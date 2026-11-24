package de.notchedmusic.phone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import de.notchedmusic.phone.playback.PlaybackService
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.ui.MainScreen
import de.notchedmusic.phone.ui.theme.NotchedMusicTheme

/** Einzige Activity: reines Compose, Edge-to-Edge, Wiedergabe lebt im Dienst. */
class MainActivity : ComponentActivity() {

  private val viewModel: AppViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Der Dienst hält MediaSession und ExoPlayer; die UI steuert denselben Spieler.
    try {
      startService(Intent(this, PlaybackService::class.java))
    } catch (_: IllegalStateException) {
      // Im Hintergrund verboten – die Wiedergabe startet dann beim ersten Play den Dienst.
    }

    setContent {
      NotchedMusicTheme {
        MainScreen(viewModel)
      }
    }
  }
}
