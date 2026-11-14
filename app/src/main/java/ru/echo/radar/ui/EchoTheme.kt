package ru.echo.radar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFE6E3DF), onPrimary = Color(0xFF101012),
        background = Color(0xFF09090B), surface = Color(0xFF141416),
        onBackground = Color(0xFFE6E3DF), onSurface = Color(0xFFE6E3DF),
        secondary = Color(0xFFA3A1A0),
    ), content = content)
}
