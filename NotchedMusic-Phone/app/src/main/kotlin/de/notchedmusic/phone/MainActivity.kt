package de.notchedmusic.phone

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
    ensurePlaybackService()

    setContent {
      NotchedMusicTheme {
        MainScreen(viewModel)
      }
    }
  }

  override fun onStart() {
    super.onStart()
    // Nach einer Sperre/Installation kann der erste Start verweigert worden sein. Beim sichtbaren
    // Lebenszyklus wird er nachgeholt, bevor der Nutzer die Wiedergabe startet.
    ensurePlaybackService()
  }

  private fun ensurePlaybackService() {
    try {
      PlaybackService.start(this)
    } catch (_: IllegalStateException) {
      // Android verbietet Starts im Hintergrund; der nächste sichtbare Start versucht es erneut.
    }
  }
}
