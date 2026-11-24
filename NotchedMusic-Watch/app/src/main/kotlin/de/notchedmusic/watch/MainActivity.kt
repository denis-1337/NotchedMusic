package de.notchedmusic.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation3.rememberSwipeDismissableSceneStrategy
import de.notchedmusic.watch.data.MusicLibrary
import de.notchedmusic.watch.data.musicFolder
import de.notchedmusic.watch.model.AmbientAnzeige
import de.notchedmusic.watch.playback.FilterSettings
import de.notchedmusic.watch.playback.PlaybackController
import de.notchedmusic.watch.ui.album.AlbumListViewModel
import de.notchedmusic.watch.ui.album.AlbumScreen
import de.notchedmusic.watch.ui.album.NowPlayingScreen
import de.notchedmusic.watch.ui.album.TrackScreen
import de.notchedmusic.watch.ui.filter.EqScreen
import de.notchedmusic.watch.ui.settings.SettingsScreen
import de.notchedmusic.watch.ui.theme.NotchedMusicTheme
import kotlinx.serialization.Serializable

/** Ziele der Uhr-Navigation. Preset-Bearbeitung gibt es auf der Uhr bewusst nicht. */
@Serializable
sealed interface Scene : NavKey {
  @Serializable data object Albums : Scene

  @Serializable data class Tracks(val albumName: String) : Scene

  @Serializable data object NowPlaying : Scene

  /** EQ: nur Aus und P1/P2/P3. */
  @Serializable data object Eq : Scene

  @Serializable data object Settings : Scene
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      NotchedMusicTheme {
        val gerat by FilterSettings.settings.collectAsState()
        val stufe = gerat.ambientAnzeigeStufe()
        // Stufe „aus": es wird gar kein Ambient-Manager eingesetzt – beim Dimmen übernimmt
        // sofort das Zifferblatt, und die App zeichnet nichts Statisches aufs gedimmte OLED.
        val ambientModeManager =
          if (stufe == AmbientAnzeige.AUS) null else rememberAmbientModeManager()
        CompositionLocalProvider(LocalAmbientModeManager provides ambientModeManager) {
          AppScaffold { AppNavigation() }
        }
      }
    }
  }
}

@Composable
private fun AppNavigation() {
  val context = LocalContext.current
  val musicFolder = remember { context.musicFolder() }

  val albumViewModel: AlbumListViewModel =
    viewModel {
      AlbumListViewModel(
        musicLibrary = MusicLibrary(musicFolder),
        controller = PlaybackController(context.applicationContext),
      )
    }

  val settings by FilterSettings.settings.collectAsState()
  val view = LocalView.current
  DisposableEffect(settings.keepScreenOn) {
    view.keepScreenOn = settings.keepScreenOn
    onDispose { view.keepScreenOn = false }
  }

  // Nur wenn etwas sichtbar ist, darf die Wiedergabestelle abgefragt werden.
  val lebenszyklus = LocalLifecycleOwner.current
  DisposableEffect(lebenszyklus, albumViewModel) {
    val beobachter =
      LifecycleEventObserver { _, ereignis ->
        when (ereignis) {
          Lifecycle.Event.ON_START -> albumViewModel.oberflaecheSichtbar(true)
          Lifecycle.Event.ON_STOP -> albumViewModel.oberflaecheSichtbar(false)
          else -> Unit
        }
      }
    lebenszyklus.lifecycle.addObserver(beobachter)
    onDispose {
      lebenszyklus.lifecycle.removeObserver(beobachter)
      albumViewModel.oberflaecheSichtbar(false)
    }
  }

  val backStack = rememberNavBackStack(Scene.Albums)

  // Nach einem App-Start mit gespeichertem Titel direkt zum Spieler statt zur Album-Suche.
  val wiederaufnahme by albumViewModel.wiederaufnahme.collectAsState()
  LaunchedEffect(wiederaufnahme) {
    if (wiederaufnahme > 0L && backStack.lastOrNull() == Scene.Albums) {
      backStack.add(Scene.NowPlaying)
    }
  }

  NavDisplay(
    backStack = backStack,
    sceneStrategy = rememberSwipeDismissableSceneStrategy(),
    entryProvider =
      entryProvider {
        entry<Scene.Albums> {
          AlbumScreen(
            onOpenAlbum = { album -> backStack.add(Scene.Tracks(album.name)) },
            onOpenEq = { backStack.add(Scene.Eq) },
            onOpenSettings = { backStack.add(Scene.Settings) },
            viewModel = albumViewModel,
          )
        }

        entry<Scene.Tracks> { key ->
          val album = albumViewModel.albumNamed(key.albumName)
          if (album == null) {
            Text("Album nicht mehr vorhanden")
          } else {
            TrackScreen(
              album = album,
              onTrackSelected = { index ->
                albumViewModel.playTrack(album, index)
                backStack.add(Scene.NowPlaying)
              },
            )
          }
        }

        entry<Scene.NowPlaying> {
          NowPlayingScreen(viewModel = albumViewModel)
        }

        entry<Scene.Eq> {
          EqScreen()
        }

        entry<Scene.Settings> {
          SettingsScreen(
            libraryPath = musicFolder.absolutePath,
            onRescan = albumViewModel::refresh,
          )
        }
      },
  )
}
