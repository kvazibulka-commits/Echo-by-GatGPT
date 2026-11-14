package ru.echo.radar.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.echo.radar.model.MusicPresence
import ru.echo.radar.model.Track

object EchoStateRepository {
    private val mutablePresence = MutableStateFlow<MusicPresence?>(null)
    val presence = mutablePresence.asStateFlow()
    private val mutableOpenToMeet = MutableStateFlow(false)
    val openToMeet = mutableOpenToMeet.asStateFlow()

    fun detected(presence: MusicPresence?) {
        if (mutablePresence.value?.source != "manual") mutablePresence.value = presence
    }
    fun manual(track: Track) { mutablePresence.value = MusicPresence(track, "manual", true) }
    fun followSystem() { mutablePresence.value = null }
    fun setOpenToMeet(value: Boolean) { mutableOpenToMeet.value = value }
}
