package ru.echo.radar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.echo.radar.data.EchoStateRepository
import ru.echo.radar.model.EchoState
import ru.echo.radar.model.Signal
import ru.echo.radar.model.Track

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirScreen(state: EchoState, currentTrack: Track?) {
    var selected by remember { mutableStateOf<Signal?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("СЕЙЧАС В ТВОЕМ РАЙОНЕ")
        Text("Ночь звучит.", style = MaterialTheme.typography.displaySmall)
        Text("Коснись сигнала. Услышь совпадение.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Radar(state.signals.size)
        if (state.signals.isEmpty()) {
            Text("Эфир пока тих.", style = MaterialTheme.typography.headlineSmall)
            Text("Включи трек в своем плеере. Возможно, кто-то услышит совпадение.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Eyebrow("БЛИЖЕ ВСЕГО ПО ЗВУЧАНИЮ")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(state.signals, key = { it.sessionId }) { signal -> SignalRow(signal) { selected = signal } }
            }
        }
        currentTrack?.let { Text("Твой сигнал: ${it.artist} — ${it.title}", color = MaterialTheme.colorScheme.secondary) }
    }
    selected?.let { signal -> ModalBottomSheet(onDismissRequest = { selected = null }) {
        SignalSheet(signal, onSave = { EchoStateRepository.saveTrack(signal.track); selected = null },
            onImpulse = { EchoStateRepository.sendImpulse(signal.sessionId); selected = null })
    } }
}

@Composable
private fun Radar(signalCount: Int) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f); val radius = size.minDimension * .42f
            listOf(.3f, .55f, .78f, 1f).forEach { drawCircle(Color(0xFF303033), radius * it, center, 1f) }
            drawLine(Color(0xFF303033), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1f)
            drawLine(Color(0xFF303033), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1f)
            drawLine(Color(0x55EEEAE3), center, Offset(center.x + radius * .65f, center.y - radius * .68f), 2f)
            drawCircle(Color(0xFFEEEAE3), radius = 4f, center = center)
            if (signalCount > 0) listOf(.2f to -.5f, -.55f to .25f, .58f to .4f).take(signalCount.coerceAtMost(3)).forEachIndexed { index, pair ->
                drawCircle(if (index == 1) Color(0x99EEEAE3) else Color(0xFFEEEAE3), radius = 7f,
                    center = Offset(center.x + radius * pair.first, center.y + radius * pair.second))
            }
        }
        Text("ТЫ ЗДЕСЬ\n$signalCount трека рядом", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SignalRow(signal: Signal, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AbstractForm()
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("${signal.track.artist} — ${signal.track.title}", style = MaterialTheme.typography.titleMedium)
            Text(signal.track.genre.ifBlank { "Жанр пока не указан" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("↗", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun SignalSheet(signal: Signal, onSave: () -> Unit, onImpulse: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AbstractForm(); Eyebrow("СИГНАЛ ИЗ ЭФИРА")
        Text("${signal.track.artist} — ${signal.track.title}", style = MaterialTheme.typography.headlineSmall)
        Text(signal.track.genre.ifBlank { "Жанр пока не указан" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Eyebrow("В ТВОЕМ РАЙОНЕ")
        Text("Открыт для музыкальных встреч", color = MaterialTheme.colorScheme.secondary)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Сохранить трек") }
        TextButton(onClick = onImpulse, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Тихий импульс") }
    }
}

@Composable
private fun AbstractForm() {
    Canvas(Modifier.size(42.dp)) { drawCircle(Color(0xFFAAA69D), size.minDimension * .42f, Offset(size.width * .42f, size.height * .42f), 1f) }
}
