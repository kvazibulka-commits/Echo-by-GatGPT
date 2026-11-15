package ru.echo.radar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.echo.radar.model.Capsule

@Composable
fun CapsulesScreen(capsules: List<Capsule>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("ГОРОД ПОМНИТ МУЗЫКУ")
        Text("Оставлено здесь.", style = MaterialTheme.typography.displaySmall)
        Text("Треки, у которых есть свое место.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (capsules.isEmpty()) {
            Text("Здесь еще не оставляли музыку.", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 28.dp))
            Text("Оставь первую капсулу в выбранной точке города.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Оставить капсулу") }
        } else LazyColumn(Modifier.weight(1f)) { items(capsules, key = { it.id }) { CapsuleRow(it) } }
    }
}

@Composable
private fun CapsuleRow(capsule: Capsule) {
    Column(Modifier.fillMaxWidth().clickable {}.padding(vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Eyebrow("КАПСУЛА ГОРОДА")
        Text("${capsule.track.artist} — ${capsule.track.title}", style = MaterialTheme.typography.titleLarge)
        Text(capsule.note.ifBlank { "Музыка осталась здесь." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(capsule.track.genre.ifBlank { "Жанр пока не указан" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        HorizontalDivider()
    }
}
