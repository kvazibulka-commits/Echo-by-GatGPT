package ru.echo.radar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import ru.echo.radar.model.EchoState
import ru.echo.radar.model.MusicPresence
import ru.echo.radar.model.SyncStatus
import ru.echo.radar.model.Track

object EchoStateRepository {
    private val mutablePresence = MutableStateFlow<MusicPresence?>(null)
    val presence = mutablePresence.asStateFlow()
    private val mutableOpenToMeet = MutableStateFlow(false)
    val openToMeet = mutableOpenToMeet.asStateFlow()
    private val mutableEchoState = MutableStateFlow(EchoState())
    val echoState = mutableEchoState.asStateFlow()
    private val mutableSyncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus = mutableSyncStatus.asStateFlow()
    private val mutableSavedTracks = MutableStateFlow<List<Track>>(emptyList())
    val savedTracks = mutableSavedTracks.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var apiClient: EchoApiClient? = null
    private var sessionId: String = ""

    fun configure(connection: EchoConnection) {
        apiClient = EchoApiClient(connection.baseUrl, connection.token)
        sessionId = connection.sessionId
    }

    fun sync(point: ru.echo.radar.model.GeoPoint? = null) {
        val api = apiClient ?: return
        val current = mutablePresence.value ?: return
        scope.launch {
            mutableSyncStatus.value = SyncStatus.LOADING
            try {
                api.publish(sessionId, EchoPresence(current.track, current.source, mutableOpenToMeet.value), point)
                mutableEchoState.value = api.sync(sessionId)
                mutableSyncStatus.value = SyncStatus.ONLINE
            } catch (error: java.net.SocketTimeoutException) {
                mutableSyncStatus.value = SyncStatus.OFFLINE
            } catch (_: java.io.IOException) {
                mutableSyncStatus.value = SyncStatus.ERROR
            }
        }
    }

    fun detected(presence: MusicPresence?) {
        if (mutablePresence.value?.source != "manual") mutablePresence.value = presence
    }
    fun manual(track: Track) { mutablePresence.value = MusicPresence(track, "manual", true) }
    fun followSystem() { mutablePresence.value = null }
    fun setOpenToMeet(value: Boolean) { mutableOpenToMeet.value = value }
    fun saveTrack(track: Track) {
        if (mutableSavedTracks.value.none { it.artist == track.artist && it.title == track.title }) {
            mutableSavedTracks.value = mutableSavedTracks.value + track
        }
    }
    fun sendImpulse(targetId: String) {
        val api = apiClient ?: return
        scope.launch {
            try { api.sendImpulse(UUID.randomUUID().toString().replace("-", ""), sessionId, targetId) }
            catch (_: java.io.IOException) { mutableSyncStatus.value = SyncStatus.ERROR }
        }
    }
}

data class EchoConnection(val baseUrl: String, val token: String, val sessionId: String)
