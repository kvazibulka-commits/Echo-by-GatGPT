package ru.echo.radar.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.echo.radar.data.EchoStateRepository
import ru.echo.radar.engine.SerendipityEngine
import ru.echo.radar.model.Track

/** Functional media foundation; the four product screens belong to the next stage. */
@Composable
fun DeckFoundation() {
    val presence by EchoStateRepository.presence.collectAsStateWithLifecycle()
    val open by EchoStateRepository.openToMeet.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var artist by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ЭХО", style = MaterialTheme.typography.headlineLarge)
            Text("Музыка, которая живет рядом", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Text(presence?.track?.let { "${it.artist} — ${it.title}" } ?: "Включите трек в своем плеере",
                style = MaterialTheme.typography.headlineSmall)
            presence?.track?.genre?.takeIf { it.isNotBlank() }?.let { Text(it) }
            Text(SerendipityEngine.archetype(listOfNotNull(presence?.track)),
                color = MaterialTheme.colorScheme.secondary)
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text("Подключить системный плеер")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Открыт для музыкальных встреч", modifier = Modifier.weight(1f))
                Switch(checked = open, onCheckedChange = EchoStateRepository::setOpenToMeet)
            }
            Text("Задать трек вручную", style = MaterialTheme.typography.titleMedium)
            listOf("Исполнитель" to artist, "Трек" to title, "Жанр · необязательно" to genre).forEachIndexed { index, field ->
                OutlinedTextField(value = field.second, onValueChange = { value ->
                    if (value.length <= 160) when (index) { 0 -> artist = value; 1 -> title = value; else -> genre = value }
                }, label = { Text(field.first) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            }
            Button(onClick = { EchoStateRepository.manual(Track(artist.trim(), title.trim(), genre.trim())) },
                enabled = artist.isNotBlank() && title.isNotBlank()) { Text("Оставить на деке") }
            if (presence?.source == "manual") {
                TextButton(onClick = EchoStateRepository::followSystem) { Text("Вернуться к системному плееру") }
            }
        }
    }
}
