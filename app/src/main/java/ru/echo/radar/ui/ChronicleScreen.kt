package ru.echo.radar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.echo.radar.engine.SerendipityEngine
import ru.echo.radar.model.Encounter
import ru.echo.radar.model.Track

@Composable
fun ChronicleScreen(encounters: List<Encounter>, savedTracks: List<Track>) {
    val archetype = SerendipityEngine.archetype(savedTracks)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("СЕГОДНЯ · ВЕЧЕР")
        Text("Этот вечер.", style = MaterialTheme.typography.displaySmall)
        Text("То, что осталось после музыки.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(); Eyebrow("ТВОЙ МУЗЫКАЛЬНЫЙ ОБРАЗ")
        Text(archetype, style = MaterialTheme.typography.headlineSmall)
        Text("Ты сохраняешь то, мимо чего другие проходят.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Eyebrow("ПЕРЕСЕЧЕНИЯ")
        if (encounters.isEmpty()) Text("Первая встреча еще впереди.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else encounters.take(5).forEach { encounter ->
            Text("Вы уже ${encounter.encountersCount}-й раз оказались рядом.", style = MaterialTheme.typography.titleMedium)
            Text("Музыка пересеклась с другой сессией эфира.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Eyebrow("СОХРАНЕННЫЕ ТРЕКИ")
        if (savedTracks.isEmpty()) Text("Сохрани сигнал — он появится здесь.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else savedTracks.take(10).forEach { track ->
            Text("${track.artist} — ${track.title}", style = MaterialTheme.typography.titleMedium)
            Text(track.genre.ifBlank { "Жанр пока не указан" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
