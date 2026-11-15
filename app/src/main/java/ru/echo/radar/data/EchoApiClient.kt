package ru.echo.radar.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.echo.radar.model.Capsule
import ru.echo.radar.model.EchoState
import ru.echo.radar.model.Encounter
import ru.echo.radar.model.GeoPoint
import ru.echo.radar.model.Impulse
import ru.echo.radar.model.Signal
import ru.echo.radar.model.Track

class EchoApiException(val statusCode: Int, message: String) : IOException(message)

/** Small standard-library client for the LAN node. Token never enters a URL. */
class EchoApiClient(
    baseUrl: String,
    private val token: String = "",
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 8_000,
) {
    private val origin = baseUrl.trim().trimEnd('/').also {
        require(it.startsWith("http://") || it.startsWith("https://")) { "Echo URL must use HTTP(S)" }
    }

    suspend fun publish(sessionId: String, presence: EchoPresence, point: GeoPoint?) {
        request("/api/signal", JSONObject().apply {
            put("session_id", sessionId); put("artist", presence.track.artist)
            put("title", presence.track.title); put("genre", presence.track.genre)
            put("source", presence.source); put("open_to_meet", presence.openToMeet)
            point?.let { put("latitude", it.latitude); put("longitude", it.longitude) }
        })
    }

    suspend fun sync(sessionId: String): EchoState = parseState(
        request("/api/sync", JSONObject().put("session_id", sessionId))
    )

    suspend fun capsules(point: GeoPoint): List<Capsule> {
        val response = request("/api/capsules?latitude=${point.latitude}&longitude=${point.longitude}", null)
        return parseCapsules(response.optJSONArray("capsules"))
    }

    suspend fun sendImpulse(id: String, senderId: String, targetId: String) {
        request("/api/impulses", JSONObject().apply {
            put("id", id); put("sender_id", senderId); put("target_id", targetId)
        })
    }

    private suspend fun request(path: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(origin + path).openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = connectTimeoutMs; readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (token.isNotBlank()) setRequestProperty("X-Echo-Token", token)
            if (body != null) {
                doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) throw EchoApiException(status, json.optString("error", "Echo node error"))
            json
        } catch (error: EchoApiException) { throw error }
        catch (error: IOException) { throw IOException("Echo node is unreachable", error) }
        finally { connection.disconnect() }
    }

    companion object {
        private fun parseState(json: JSONObject): EchoState = EchoState(
            signals = parseSignals(json.optJSONArray("signals")),
            encounters = parseEncounters(json.optJSONArray("encounters")),
            impulses = parseImpulses(json.optJSONArray("impulses")),
            serverTime = json.optDoubleOrNull("server_time"),
        )
        private fun parseSignals(array: JSONArray?): List<Signal> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) array.optJSONObject(index)?.let { json ->
                val artist = json.optString("artist"); val title = json.optString("title")
                if (json.optString("session_id").isBlank() || artist.isBlank() || title.isBlank()) return@let
                add(Signal(json.optString("session_id"), Track(artist, title, json.optString("genre")),
                    json.pointOrNull(), json.optBoolean("open_to_meet"), json.optDouble("updated_at")))
            }
        }
        private fun parseCapsules(array: JSONArray?): List<Capsule> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) array.optJSONObject(index)?.let { json ->
                val point = json.pointOrNull() ?: return@let
                val artist = json.optString("artist"); val title = json.optString("title")
                if (json.optString("id").isBlank() || artist.isBlank() || title.isBlank()) return@let
                add(Capsule(json.optString("id"), Track(artist, title, json.optString("genre")), point,
                    json.optString("note"), json.optDouble("created_at")))
            }
        }
        private fun parseEncounters(array: JSONArray?): List<Encounter> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) array.optJSONObject(index)?.let {
                add(Encounter(it.optString("session_a"), it.optString("session_b"),
                    it.optInt("encounters_count"), it.optDouble("first_seen_at"), it.optDouble("last_seen_at")))
            }
        }
        private fun parseImpulses(array: JSONArray?): List<Impulse> = buildList {
            if (array == null) return@buildList
            for (index in 0 until array.length()) array.optJSONObject(index)?.let {
                add(Impulse(it.optString("id"), it.optString("sender_id"),
                    it.optString("target_id"), it.optDouble("created_at")))
            }
        }
        private fun JSONObject.pointOrNull(): GeoPoint? {
            val lat = optDoubleOrNull("latitude") ?: return null
            val lon = optDoubleOrNull("longitude") ?: return null
            return runCatching { GeoPoint(lat, lon) }.getOrNull()
        }
        private fun JSONObject.optDoubleOrNull(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            val value = optDouble(key, Double.NaN)
            return value.takeIf { it.isFinite() }
        }
    }
}

data class EchoPresence(
    val track: Track,
    val source: String,
    val openToMeet: Boolean,
)
