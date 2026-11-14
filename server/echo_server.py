"""Echo Radar LAN node. Python 3.12+, no third-party runtime dependencies."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
import logging
import math
import os
from pathlib import Path
import socket
import sqlite3
import threading
import time
from urllib.parse import parse_qs, urlsplit
import uuid

LOG = logging.getLogger("echo")
SIGNAL_TTL = 90
ENCOUNTER_GAP = 1800
MAX_BODY = 16_384
WEB = Path(__file__).with_name("web")

SCHEMA = """
CREATE TABLE IF NOT EXISTS signals (
    session_id TEXT PRIMARY KEY, artist TEXT NOT NULL, title TEXT NOT NULL,
    genre TEXT NOT NULL DEFAULT '', source TEXT NOT NULL DEFAULT 'manual',
    open_to_meet INTEGER NOT NULL CHECK(open_to_meet IN (0,1)),
    latitude REAL, longitude REAL, updated_at REAL NOT NULL,
    CHECK ((latitude IS NULL) = (longitude IS NULL))
);
CREATE INDEX IF NOT EXISTS signals_updated ON signals(updated_at);
CREATE TABLE IF NOT EXISTS capsules (
    id TEXT PRIMARY KEY, creator_id TEXT NOT NULL, artist TEXT NOT NULL,
    title TEXT NOT NULL, genre TEXT NOT NULL DEFAULT '', note TEXT NOT NULL,
    latitude REAL NOT NULL, longitude REAL NOT NULL, created_at REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS capsule_location ON capsules(latitude,longitude);
CREATE TABLE IF NOT EXISTS encounters (
    session_a TEXT NOT NULL, session_b TEXT NOT NULL,
    encounters_count INTEGER NOT NULL DEFAULT 1 CHECK(encounters_count > 0),
    first_seen_at REAL NOT NULL, last_seen_at REAL NOT NULL,
    last_counted_at REAL NOT NULL,
    PRIMARY KEY(session_a,session_b), CHECK(session_a < session_b)
);
CREATE TABLE IF NOT EXISTS impulses (
    id TEXT PRIMARY KEY, sender_id TEXT NOT NULL, target_id TEXT NOT NULL,
    created_at REAL NOT NULL, CHECK(sender_id != target_id)
);
CREATE INDEX IF NOT EXISTS impulses_target ON impulses(target_id,created_at);
PRAGMA user_version=1;
"""


class ApiError(Exception):
    def __init__(self, status: int, message: str):
        self.status, self.message = status, message


def text_field(data: dict, name: str, maximum: int = 160, required: bool = True) -> str:
    value = data.get(name, "")
    if not isinstance(value, str) or len(value) > maximum:
        raise ApiError(400, f"Invalid {name}")
    value = " ".join(value.split())
    if required and not value:
        raise ApiError(400, f"Missing {name}")
    return value


def identifier(data: dict, name: str) -> str:
    value = text_field(data, name, 64)
    if not all(c.isascii() and (c.isalnum() or c in "_-") for c in value):
        raise ApiError(400, f"Invalid {name}")
    return value


def coordinates(data: dict, required: bool = False) -> tuple[float | None, float | None]:
    lat, lon = data.get("latitude"), data.get("longitude")
    if lat is None and lon is None and not required:
        return None, None
    if (isinstance(lat, bool) or isinstance(lon, bool)
            or not isinstance(lat, (int, float)) or not isinstance(lon, (int, float))
            or not math.isfinite(lat) or not math.isfinite(lon)
            or not -90 <= lat <= 90 or not -180 <= lon <= 180):
        raise ApiError(400, "Invalid coordinates")
    return float(lat), float(lon)


def distance(a: dict, b: dict) -> float:
    lat1, lat2 = math.radians(a["latitude"]), math.radians(b["latitude"])
    dlat = lat2 - lat1
    dlon = math.radians(b["longitude"] - a["longitude"])
    value = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6_371_000 * 2 * math.asin(math.sqrt(min(1.0, max(0.0, value))))


class Store:
    def __init__(self, path: str):
        self.path = path
        # journal_mode must change outside a transaction.
        db = sqlite3.connect(path, timeout=10, isolation_level=None)
        try:
            db.execute("PRAGMA journal_mode=WAL")
            db.executescript(SCHEMA)
        finally:
            db.close()

    @contextmanager
    def connection(self, write: bool = False):
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA busy_timeout=10000")
        db.execute("PRAGMA synchronous=NORMAL")
        try:
            db.execute("BEGIN IMMEDIATE" if write else "BEGIN")
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def publish(self, data: dict) -> dict:
        session = identifier(data, "session_id")
        artist, title = text_field(data, "artist"), text_field(data, "title")
        genre = text_field(data, "genre", required=False)
        source = text_field(data, "source", 100, required=False) or "manual"
        opened = data.get("open_to_meet", False)
        if not isinstance(opened, bool):
            raise ApiError(400, "open_to_meet must be boolean")
        lat, lon = coordinates(data)
        # Never persist a listener's precise location.
        lat, lon = (round(lat, 2), round(lon, 2)) if lat is not None else (None, None)
        now = time.time()
        with self.connection(True) as db:
            db.execute("DELETE FROM signals WHERE updated_at < ?", (now - SIGNAL_TTL,))
            db.execute("DELETE FROM impulses WHERE created_at < ?", (now - 86400,))
            db.execute("""INSERT INTO signals VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(session_id) DO UPDATE SET artist=excluded.artist,
                title=excluded.title,genre=excluded.genre,source=excluded.source,
                open_to_meet=excluded.open_to_meet,latitude=excluded.latitude,
                longitude=excluded.longitude,updated_at=excluded.updated_at""",
                       (session, artist, title, genre, source, int(opened), lat, lon, now))
        return {"session_id": session, "expires_at": now + SIGNAL_TTL}

    @staticmethod
    def encounter(db: sqlite3.Connection, first: str, second: str, now: float) -> None:
        a, b = sorted((first, second))
        db.execute("""INSERT INTO encounters VALUES (?,?,1,?,?,?)
            ON CONFLICT(session_a,session_b) DO UPDATE SET
            encounters_count=encounters_count + CASE WHEN
                excluded.last_seen_at-last_seen_at >= ? THEN 1 ELSE 0 END,
            last_counted_at=CASE WHEN excluded.last_seen_at-last_seen_at >= ?
                THEN excluded.last_seen_at ELSE last_counted_at END,
            last_seen_at=excluded.last_seen_at""", (a, b, now, now, now, ENCOUNTER_GAP, ENCOUNTER_GAP))

    def state(self, session: str, sync: bool = False) -> dict:
        now = time.time()
        with self.connection(sync) as db:
            own = db.execute("SELECT * FROM signals WHERE session_id=? AND updated_at>=?",
                             (session, now - SIGNAL_TTL)).fetchone()
            rows = [dict(row) for row in db.execute(
                "SELECT * FROM signals WHERE updated_at>=? AND session_id!=? ORDER BY updated_at DESC LIMIT 500",
                (now - SIGNAL_TTL, session))]
            # No location means no claim of geographical proximity.
            rows = [row for row in rows if own and own["latitude"] is not None
                    and row["latitude"] is not None and distance(dict(own), row) <= 1500]
            if sync and own:
                for row in rows:
                    self.encounter(db, session, row["session_id"], now)
            encounters = [dict(row) for row in db.execute(
                "SELECT * FROM encounters WHERE session_a=? OR session_b=? ORDER BY last_seen_at DESC LIMIT 100",
                (session, session))]
            impulses = [dict(row) for row in db.execute(
                "SELECT * FROM impulses WHERE target_id=? AND created_at>=? ORDER BY created_at DESC LIMIT 50",
                (session, now - 86400))]
        for row in rows:
            row["open_to_meet"] = bool(row["open_to_meet"])
        return {"signals": rows, "encounters": encounters, "impulses": impulses, "server_time": now}

    def capsules(self, query: dict) -> dict:
        lat, lon = coordinates(query, required=True)
        with self.connection() as db:
            # Bounding box first, then exact great-circle filtering.
            span = min(180, 0.05 / max(0.001, math.cos(math.radians(lat))))
            rows = [dict(row) for row in db.execute(
                "SELECT * FROM capsules WHERE latitude BETWEEN ? AND ? ORDER BY created_at DESC",
                (lat - 0.05, lat + 0.05))
                if min(abs(row["longitude"] - lon), 360 - abs(row["longitude"] - lon)) <= span]
        return {"capsules": [row for row in rows if distance(query, row) <= 5000][:200]}

    def create_capsule(self, data: dict) -> dict:
        creator = identifier(data, "creator_id")
        artist, title = text_field(data, "artist"), text_field(data, "title")
        genre = text_field(data, "genre", required=False)
        note = text_field(data, "note", 400, required=False)
        lat, lon = coordinates(data, required=True)
        capsule_id = uuid.uuid4().hex
        with self.connection(True) as db:
            db.execute("INSERT INTO capsules VALUES (?,?,?,?,?,?,?,?,?)",
                       (capsule_id, creator, artist, title, genre, note, lat, lon, time.time()))
        return {"id": capsule_id}

    def impulse(self, data: dict) -> dict:
        event_id = identifier(data, "id")
        sender, target = identifier(data, "sender_id"), identifier(data, "target_id")
        if sender == target:
            raise ApiError(400, "An impulse needs another signal")
        now = time.time()
        with self.connection(True) as db:
            previous = db.execute("SELECT * FROM impulses WHERE id=?", (event_id,)).fetchone()
            if previous:
                if previous["sender_id"] != sender or previous["target_id"] != target:
                    raise ApiError(409, "Event id already used")
                return {"id": event_id}
            active = db.execute("SELECT COUNT(*) FROM signals WHERE session_id IN (?,?) AND updated_at>=?",
                                (sender, target, now - SIGNAL_TTL)).fetchone()[0]
            if active != 2:
                raise ApiError(404, "Signal has faded")
            recent = db.execute("SELECT 1 FROM impulses WHERE sender_id=? AND created_at>?",
                                (sender, now - 5)).fetchone()
            if recent:
                raise ApiError(429, "Let the impulse settle")
            db.execute("INSERT INTO impulses VALUES (?,?,?,?)", (event_id, sender, target, now))
        return {"id": event_id}


class EchoHTTPServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address: tuple[str, int], store: Store, token: str = ""):
        self.store, self.token = store, token
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    server: EchoHTTPServer

    def setup(self) -> None:
        super().setup()
        self.connection.settimeout(10)

    def log_message(self, fmt: str, *args) -> None:
        # Do not record query strings, tokens, or music/location data.
        LOG.debug("HTTP request completed")

    def send_payload(self, status: int, body: bytes, content_type: str) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)

    def authorize(self) -> None:
        origin = self.headers.get("Origin")
        if origin and urlsplit(origin).netloc != self.headers.get("Host"):
            raise ApiError(403, "Cross-origin request denied")
        token = self.server.token
        if token:
            if not hmac.compare_digest(self.headers.get("X-Echo-Token", ""), token):
                raise ApiError(401, "Token required")
        else:
            address = ipaddress.ip_address(self.client_address[0])
            proxied = any(key.lower().startswith(("x-forwarded-", "cf-")) or key.lower() in
                          ("forwarded", "x-real-ip") for key in self.headers)
            if proxied or not (address.is_loopback or address.is_private):
                raise ApiError(403, "Configure ECHO_TOKEN for remote access")

    def body(self) -> dict:
        if self.headers.get("Transfer-Encoding"):
            raise ApiError(400, "Transfer encoding unsupported")
        if self.headers.get_content_type() != "application/json":
            raise ApiError(415, "Use application/json")
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            raise ApiError(400, "Invalid Content-Length") from None
        if not 0 < length <= MAX_BODY:
            raise ApiError(413, "Body must be 1..16384 bytes")
        try:
            value = json.loads(self.rfile.read(length))
        except (ValueError, UnicodeError):
            raise ApiError(400, "Invalid JSON") from None
        if not isinstance(value, dict):
            raise ApiError(400, "Expected JSON object")
        return value

    def route(self) -> None:
        path = urlsplit(self.path).path
        status = 200
        if self.command == "GET" and path in ("/", "/app", "/app/", "/app.js", "/style.css", "/sw.js", "/manifest.webmanifest", "/icon.svg"):
            files = {"/app.js": ("app.js", "text/javascript"), "/sw.js": ("sw.js", "text/javascript"),
                     "/style.css": ("style.css", "text/css"), "/icon.svg": ("icon.svg", "image/svg+xml"),
                     "/manifest.webmanifest": ("manifest.webmanifest", "application/manifest+json")}
            name, kind = files.get(path, ("index.html", "text/html"))
            self.send_payload(200, (WEB / name).read_bytes(), kind + "; charset=utf-8")
            return
        if path == "/api/health" and self.command == "GET":
            result = {"status": "ok", "version": "0.1.0"}
        else:
            self.authorize()
            store = self.server.store
            if self.command == "GET":
                query = {k: v[0] for k, v in parse_qs(urlsplit(self.path).query).items()}
                if path in ("/api/state", "/api/encounters", "/api/impulses"):
                    state = store.state(identifier(query, "session_id"))
                    key = path.rsplit("/", 1)[1]
                    result = state if key == "state" else {key: state[key]}
                elif path == "/api/capsules":
                    try:
                        coords = {k: float(query[k]) for k in ("latitude", "longitude")}
                    except (ValueError, KeyError):
                        raise ApiError(400, "Coordinates required") from None
                    result = store.capsules(coords)
                else:
                    raise ApiError(404, "Unknown endpoint")
            else:
                data = self.body()
                if path == "/api/signal":
                    result = store.publish(data)
                elif path == "/api/sync":
                    result = store.state(identifier(data, "session_id"), sync=True)
                elif path == "/api/capsules":
                    result, status = store.create_capsule(data), 201
                elif path == "/api/impulses":
                    result, status = store.impulse(data), 201
                else:
                    raise ApiError(404, "Unknown endpoint")
        self.send_payload(status, json.dumps(result, ensure_ascii=False, allow_nan=False).encode(),
                          "application/json; charset=utf-8")

    def handle_api(self) -> None:
        try:
            self.route()
        except ApiError as error:
            self.send_payload(error.status, json.dumps({"error": error.message}).encode(), "application/json")
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            LOG.debug("Client disconnected")
        except Exception:
            LOG.exception("Request failed")
            self.send_payload(500, b'{"error":"Internal server error"}', "application/json")

    do_GET = handle_api
    do_POST = handle_api


def udp_discovery(stop: threading.Event, host: str, port: int, http_port: int) -> None:
    """Only service discovery; never accepts music, coordinates or mutations."""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as udp:
        udp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        udp.bind((host, port))
        udp.settimeout(0.5)
        while not stop.is_set():
            try:
                payload, peer = udp.recvfrom(256)
                if payload == b"ECHO_DISCOVER_V1" and ipaddress.ip_address(peer[0]).is_private:
                    udp.sendto(f"ECHO_V1:{http_port}".encode("ascii"), peer)
            except socket.timeout:
                continue
            except OSError:
                if not stop.is_set():
                    LOG.exception("UDP discovery failed")
                break


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--udp-port", type=int, default=45454)
    parser.add_argument("--db", default=os.environ.get("ECHO_DB_PATH", str(Path(__file__).with_name("echo.db"))))
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    server = EchoHTTPServer((args.host, args.port), Store(args.db), os.environ.get("ECHO_TOKEN", ""))
    stop = threading.Event()
    thread = threading.Thread(target=udp_discovery, args=(stop, args.host, args.udp_port, args.port), daemon=True)
    thread.start()
    LOG.info("Echo HTTP :%s, UDP :%s", args.port, args.udp_port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        LOG.info("Stopping Echo")
    finally:
        stop.set()
        server.server_close()
        thread.join(timeout=2)


if __name__ == "__main__":
    main()
