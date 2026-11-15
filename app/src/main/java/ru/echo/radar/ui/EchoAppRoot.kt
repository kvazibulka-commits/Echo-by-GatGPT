package ru.echo.radar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.echo.radar.data.EchoStateRepository

enum class EchoTab(val label: String) { AIR("Эфир"), DECK("Дека"), CAPSULES("Капсулы"), CHRONICLE("Хроника") }

@Composable
fun EchoAppRoot() {
    var selectedName by rememberSaveable { mutableStateOf(EchoTab.AIR.name) }
    val selected = EchoTab.valueOf(selectedName)
    val state by EchoStateRepository.echoState.collectAsStateWithLifecycleCompat()
    val presence by EchoStateRepository.presence.collectAsStateWithLifecycleCompat()
    val savedTracks by EchoStateRepository.savedTracks.collectAsStateWithLifecycleCompat()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selected) {
                EchoTab.AIR -> AirScreen(state = state, currentTrack = presence?.track)
                EchoTab.DECK -> DeckScreen(presence = presence, onSave = EchoStateRepository::saveTrack)
                EchoTab.CAPSULES -> CapsulesScreen(capsules = state.capsules)
                EchoTab.CHRONICLE -> ChronicleScreen(encounters = state.encounters, savedTracks = savedTracks)
            }
        }
        EchoBottomNav(selected = selected, onSelect = { selectedName = it.name })
    }
}

@Composable
private fun EchoBottomNav(selected: EchoTab, onSelect: (EchoTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        EchoTab.entries.forEach { tab ->
            Column(Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tab.label, color = if (tab == selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                if (tab == selected) Text("·", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
