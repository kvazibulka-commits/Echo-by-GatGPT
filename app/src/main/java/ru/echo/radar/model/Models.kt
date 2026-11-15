package ru.echo.radar.model

data class Track(val artist: String, val title: String, val genre: String = "") {
    init { require(artist.isNotBlank() && title.isNotBlank()) }
}

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class Signal(
    val sessionId: String, val track: Track, val point: GeoPoint?,
    val openToMeet: Boolean, val updatedAt: Double,
)
data class Capsule(
    val id: String, val track: Track, val point: GeoPoint,
    val note: String, val createdAt: Double,
)
data class Encounter(
    val sessionA: String, val sessionB: String, val encountersCount: Int,
    val firstSeenAt: Double, val lastSeenAt: Double,
)
data class Impulse(val id: String, val senderId: String, val targetId: String, val createdAt: Double)
data class MusicPresence(val track: Track, val source: String, val playing: Boolean)
data class Resonance(val score: Int, val sharedGenres: Set<String>, val sharedArtist: Boolean)
data class EchoState(
    val signals: List<Signal> = emptyList(),
    val capsules: List<Capsule> = emptyList(),
    val encounters: List<Encounter> = emptyList(),
    val impulses: List<Impulse> = emptyList(),
    val serverTime: Double? = null,
)
enum class SyncStatus { IDLE, LOADING, ONLINE, OFFLINE, ERROR }
