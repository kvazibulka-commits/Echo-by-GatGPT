package ru.echo.radar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ru.echo.radar.ui.DeckFoundation
import ru.echo.radar.ui.EchoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EchoTheme { DeckFoundation() } }
    }
}
