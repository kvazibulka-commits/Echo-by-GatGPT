package ru.echo.radar.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.echo.radar.data.EchoStateRepository
import ru.echo.radar.engine.SerendipityEngine
import ru.echo.radar.model.MusicPresence
import ru.echo.radar.model.Track

@Composable
fun DeckScreen(presence: MusicPresence?, onSave: (Track) -> Unit) {
    val context = LocalContext.current
    val open by EchoStateRepository.openToMeet.collectAsStateWithLifecycleCompat()
    var artist by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Eyebrow("ТВОЙ СЛЕД В ЭФИРЕ")
        Text("Сторона А.", style = MaterialTheme.typography.displaySmall)
        Text("Музыка играет в твоем плеере.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Vinyl()
        Eyebrow("СЕЙЧАС ЗВУЧИТ")
        Text(presence?.track?.let { "${it.artist} — ${it.title}" } ?: "Трек не определен", style = MaterialTheme.typography.headlineSmall)
        Text(presence?.track?.genre?.ifBlank { "Жанр пока не указан" } ?: "Включите трек в плеере", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Открыт для музыкальных встреч")
                Text("Тихое приглашение быть рядом", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = open, onCheckedChange = EchoStateRepository::setOpenToMeet)
        }
        Text("Указать другой трек", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(artist, { if (it.length <= 160) artist = it }, label = { Text("Исполнитель") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        OutlinedTextField(title, { if (it.length <= 160) title = it }, label = { Text("Трек") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        OutlinedTextField(genre, { if (it.length <= 160) genre = it }, label = { Text("Жанр · необязательно") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Button(enabled = artist.isNotBlank() && title.isNotBlank(), onClick = {
            val track = Track(artist.trim(), title.trim(), genre.trim()); EchoStateRepository.manual(track); onSave(track)
        }) { Text("Оставить на деке") }
        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Подключить системный плеер") }
        presence?.track?.let { Text(SerendipityEngine.archetype(listOf(it)), color = MaterialTheme.colorScheme.secondary) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Vinyl() {
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "vinylRotation")
    Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
        Box(Modifier.size(250.dp).rotate(rotation).background(Brush.radialGradient(listOf(Color(0xFF29292B), Color(0xFF0D0D0F))), CircleShape)
            .border(1.dp, Color(0xFF4A4A4D), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(86.dp).background(Color(0xFFD6D1C6), CircleShape), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ЭХО", color = Color(0xFF19191B), style = MaterialTheme.typography.titleMedium)
                    Text("СТОРОНА А", color = Color(0xFF19191B), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun Eyebrow(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
