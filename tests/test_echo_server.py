from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import sqlite3
import socket
import tempfile
import threading
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from server.echo_server import ApiError, ENCOUNTER_GAP, EchoHTTPServer, Store, udp_discovery


class StoreTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.path = str(Path(self.directory.name) / "echo.db")
        self.store = Store(self.path)

    def tearDown(self):
        self.directory.cleanup()

    def signal(self, session, **extra):
        return self.store.publish(dict(session_id=session, artist="Артист", title="Трек",
                                       latitude=55.75463, longitude=37.62414, **extra))

    def test_schema_and_privacy(self):
        self.signal("a")
        with self.store.connection() as db:
            self.assertEqual(db.execute("PRAGMA journal_mode").fetchone()[0], "wal")
            self.assertIn("encounters_count", [row[1] for row in db.execute("PRAGMA table_info(encounters)")])
            self.assertEqual(db.execute("SELECT latitude FROM signals").fetchone()[0], 55.75)

    def test_sync_is_atomic_and_polling_does_not_count_as_new_encounter(self):
        self.signal("a")
        self.signal("b")
        with ThreadPoolExecutor(max_workers=12) as pool:
            list(pool.map(lambda _: self.store.state("a", sync=True), range(40)))
        encounters = self.store.state("a")["encounters"]
        self.assertEqual(len(encounters), 1)
        self.assertEqual(encounters[0]["encounters_count"], 1)
        self.assertEqual(self.store.state("b", True)["encounters"][0]["encounters_count"], 1)

    def test_return_after_gap_counts_once(self):
        self.signal("a")
        self.signal("b")
        self.store.state("a", True)
        with self.store.connection(True) as db:
            db.execute("UPDATE encounters SET last_seen_at=?", (time.time() - ENCOUNTER_GAP - 1,))
        self.assertEqual(self.store.state("a", True)["encounters"][0]["encounters_count"], 2)
        self.assertEqual(self.store.state("a", True)["encounters"][0]["encounters_count"], 2)

    def test_expired_signal_is_not_visible(self):
        self.signal("a")
        self.signal("b")
        with self.store.connection(True) as db:
            db.execute("UPDATE signals SET updated_at=0 WHERE session_id='b'")
        self.assertEqual(self.store.state("a", True)["signals"], [])
        self.assertEqual(self.store.state("a")["encounters"], [])

    def test_missing_location_never_implies_nearby(self):
        self.store.publish(dict(session_id="a", artist="A", title="T"))
        self.signal("b")
        self.assertEqual(self.store.state("a", True)["signals"], [])

    def test_impulse_idempotency_and_rate_limit(self):
        self.signal("a")
        self.signal("b")
        event = dict(id="event", sender_id="a", target_id="b")
        self.assertEqual(self.store.impulse(event), self.store.impulse(event))
        self.assertEqual(len(self.store.state("b")["impulses"]), 1)
        with self.assertRaises(ApiError) as caught:
            self.store.impulse(dict(event, id="second"))
        self.assertEqual(caught.exception.status, 429)

    def test_capsules_are_filtered_by_distance_and_sql_is_bound(self):
        self.store.create_capsule(dict(creator_id="a", artist="'; DROP TABLE signals;--", title="T",
                                       latitude=55.75, longitude=37.62))
        self.assertEqual(len(self.store.capsules(dict(latitude=55.75, longitude=37.62))["capsules"]), 1)
        self.assertEqual(self.store.capsules(dict(latitude=0, longitude=0))["capsules"], [])
        self.signal("a")

    def test_validation(self):
        for invalid in (float("nan"), float("inf"), True, 91, "55.7"):
            with self.assertRaises(ApiError):
                self.store.publish(dict(session_id="a", artist="A", title="T", latitude=invalid, longitude=1))
        with self.assertRaises(ApiError):
            self.store.publish(dict(session_id="a", artist="A", title="T", open_to_meet="true"))

    def test_rollback(self):
        with self.assertRaises(sqlite3.IntegrityError):
            with self.store.connection(True) as db:
                db.execute("INSERT INTO impulses VALUES ('x','a','b',0)")
                db.execute("INSERT INTO impulses VALUES ('y','a','a',0)")
        with self.store.connection() as db:
            self.assertEqual(db.execute("SELECT COUNT(*) FROM impulses").fetchone()[0], 0)


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        store = Store(str(Path(self.directory.name) / "api.db"))
        self.server = EchoHTTPServer(("127.0.0.1", 0), store, "test-token")
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.directory.cleanup()

    def request(self, path, data=None, headers=None):
        request = Request(self.url + path, data=json.dumps(data).encode() if data is not None else None,
                          headers={"Content-Type": "application/json", **(headers or {})})
        try:
            response = urlopen(request, timeout=5)
        except HTTPError as error:
            response = error
        with response:
            return response.status, response.headers, response.read()

    def test_health_and_protected_routes(self):
        self.assertEqual(self.request("/api/health")[0], 200)
        self.assertEqual(self.request("/api/state?session_id=a")[0], 401)
        headers = {"X-Echo-Token": "test-token"}
        status, response_headers, _ = self.request("/api/signal", dict(session_id="a", artist="A", title="T"), headers)
        self.assertEqual(status, 200)
        self.assertEqual(response_headers["Cache-Control"], "no-store")
        self.assertEqual(self.request("/api/sync", dict(session_id="a"), headers)[0], 200)
        self.assertEqual(self.request("/missing", headers=headers)[0], 404)

    def test_proxy_does_not_bypass_authorization(self):
        self.server.token = ""
        self.assertEqual(self.request("/api/state?session_id=a")[0], 200)
        self.assertEqual(self.request("/api/state?session_id=a", headers={"CF-Connecting-IP":"1.2.3.4"})[0], 403)

    def test_cross_origin_and_limits(self):
        headers = {"X-Echo-Token": "test-token"}
        self.assertEqual(self.request("/api/signal", {"x":"a" * 17000}, headers)[0], 413)
        self.assertEqual(self.request("/api/signal", {}, dict(headers, Origin="https://other.example"))[0], 403)

    def test_pwa_resources(self):
        for path in ("/app", "/app.js", "/style.css", "/manifest.webmanifest", "/sw.js", "/icon.svg"):
            self.assertEqual(self.request(path)[0], 200, path)


class DiscoveryTests(unittest.TestCase):
    def test_udp_node_discovery(self):
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as reserve:
            reserve.bind(("127.0.0.1", 0))
            port = reserve.getsockname()[1]
        stop = threading.Event()
        thread = threading.Thread(target=udp_discovery, args=(stop, "127.0.0.1", port, 8080), daemon=True)
        thread.start()
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
                client.settimeout(0.2)
                for attempt in range(10):
                    client.sendto(b"ECHO_DISCOVER_V1", ("127.0.0.1", port))
                    try:
                        reply, _ = client.recvfrom(128)
                        break
                    except socket.timeout:
                        if attempt == 9:
                            raise
                self.assertEqual(reply, b"ECHO_V1:8080")
                client.sendto(b"arbitrary mutation", ("127.0.0.1", port))
                with self.assertRaises(socket.timeout):
                    client.recvfrom(128)
        finally:
            stop.set()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
