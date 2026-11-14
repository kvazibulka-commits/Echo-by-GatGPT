package ru.echo.radar.media

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.echo.radar.data.EchoStateRepository
import ru.echo.radar.model.MusicPresence
import ru.echo.radar.model.Track

class EchoMediaListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val controllers = mutableMapOf<MediaController, MediaController.Callback>()
    private val manager by lazy { getSystemService(MediaSessionManager::class.java) }
    private var connected = false
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }
    private val poll = object : Runnable {
        override fun run() {
            if (!connected) return
            refresh()
            handler.postDelayed(this, 2500)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, javaClass), handler)
        } catch (_: SecurityException) {
            connected = false
        }
        handler.removeCallbacks(poll)
        if (connected) handler.post(poll)
    }

    private fun refresh() {
        val sessions = try {
            manager.getActiveSessions(ComponentName(this, javaClass)).filter { it.packageName in MUSIC_PACKAGES }
        } catch (_: SecurityException) {
            disconnect()
            return
        }
        controllers.keys.filter { old -> sessions.none { it.sessionToken == old.sessionToken } }.toList().forEach {
            controllers.remove(it)?.let { callback -> it.unregisterCallback(callback) }
        }
        sessions.forEach { controller ->
            if (controllers.keys.none { it.sessionToken == controller.sessionToken }) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) { publish() }
                    override fun onPlaybackStateChanged(state: PlaybackState?) { publish() }
                    override fun onSessionDestroyed() { refresh() }
                }
                controllers[controller] = callback
                controller.registerCallback(callback, handler)
            }
        }
        publish()
    }

    private fun publish() {
        val presence = controllers.keys.sortedByDescending {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }.firstNotNullOfOrNull { controller ->
            val meta = controller.metadata ?: return@firstNotNullOfOrNull null
            val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            if (artist.isBlank() || title.isBlank()) return@firstNotNullOfOrNull null
            MusicPresence(Track(artist, title, meta.getString(MediaMetadata.METADATA_KEY_GENRE).orEmpty()),
                controller.packageName, controller.playbackState?.state == PlaybackState.STATE_PLAYING)
        }
        EchoStateRepository.detected(presence)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in MUSIC_PACKAGES) return
        refresh()
        if (controllers.isEmpty()) {
            val extras = sbn.notification.extras
            val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            if (artist.isNotBlank() && title.isNotBlank()) {
                EchoStateRepository.detected(MusicPresence(Track(artist, title), sbn.packageName, true))
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) { if (sbn.packageName in MUSIC_PACKAGES) refresh() }
    override fun onListenerDisconnected() { disconnect(); super.onListenerDisconnected() }
    override fun onDestroy() { disconnect(); super.onDestroy() }
    private fun disconnect() {
        connected = false
        handler.removeCallbacks(poll)
        manager.removeOnActiveSessionsChangedListener(sessionsListener)
        controllers.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        controllers.clear()
        EchoStateRepository.detected(null)
    }

    companion object {
        val MUSIC_PACKAGES = setOf("ru.yandex.music", "com.vkontakte.android", "com.vk.music",
            "com.zvooq.openplay", "com.spotify.music", "com.google.android.apps.youtube.music")
    }
}
