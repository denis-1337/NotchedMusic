package de.notchedmusic.phone.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.notchedmusic.phone.MainActivity
import de.notchedmusic.phone.NotchedMusicApp
import de.notchedmusic.phone.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Besitzer der MediaSession und der Hintergrundwiedergabe.
 *
 * Die `MediaSessionService`-Infrastruktur von Media3 übernimmt Meldung und Vordergrunddienst
 * vollständig selbst: Sie zeigt die Medien-Meldung, hebt den Dienst beim Spielen in den
 * Vordergrund und hält ihn nach einer Pause (z. B. während eines Anrufs) noch für die
 * Rückkehrphase im Vordergrund. Deshalb darf der Dienst die Meldung **nicht** zusätzlich selbst
 * bauen oder `stopForeground` aufrufen – ein eigener Vordergrundzustand würde die automatische
 * Verwaltung aushebeln und die Wiedergabe beim Sperren des Geräts nach wenigen Sekunden beenden.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

  private var mediaSession: MediaSession? = null
  private var settingsScope: CoroutineScope? = null

  override fun onCreate() {
    super.onCreate()

    val graph = (application as NotchedMusicApp).graph
    PlaybackEngine.initialize(this, graph.eqProcessor)

    // Die Meldung und der Vordergrunddienst kommen von Media3; nur das Symbol bleibt unser eigenes.
    setMediaNotificationProvider(
      DefaultMediaNotificationProvider.Builder(this).build().apply {
        setSmallIcon(R.drawable.ic_notification)
      },
    )

    val openApp =
      PendingIntent.getActivity(
        this,
        REQUEST_CODE_OPEN_APP,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )
    val session =
      MediaSession.Builder(this, PlaybackEngine.player())
        .setId(SESSION_ID)
        .setSessionActivity(openApp)
        .build()
    mediaSession = session
    // Ohne verbundenen MediaController kennt die MediaSessionService die Session sonst nicht
    // und würde weder Meldung noch Vordergrunddienst verwalten.
    addSession(session)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    settingsScope = scope
    scope.launch {
      graph.settings.state.collect { settings ->
        graph.eqProcessor.preset = settings.activePreset()
        graph.eqProcessor.bypass = settings.eqBypass
        graph.eqProcessor.volumeDb = settings.playerVolumeDb
        PlaybackEngine.applySettings(settings.repeatMode, settings.autoAdvance)
        graph.stats.setEqOn(!settings.eqBypass)
      }
    }
    scope.launch {
      PlaybackEngine.state.collect { state ->
        graph.stats.updatePlayback(state.isPlaying, state.currentMediaId)
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
    mediaSession

  override fun onDestroy() {
    // Das offene EQ-Segment gehört noch zum Bestand, bevor der Dienst den Prozess verlässt.
    (application as NotchedMusicApp).graph.stats.flushActive()
    settingsScope?.cancel()
    settingsScope = null
    mediaSession?.release()
    mediaSession = null
    super.onDestroy()
  }

  companion object {
    private const val SESSION_ID = "notched-phone"
    private const val REQUEST_CODE_OPEN_APP = 0

    /**
     * Startet den Dienst aus einer sichtbaren Activity oder während einer Nutzeraktion.
     *
     * Der Aufruf ist idempotent. Android kann den Start im Hintergrund verweigern; dann muss der
     * nächste sichtbare Lebenszyklus den Dienst erneut anfordern.
     */
    fun start(context: Context) {
      context.startService(Intent(context, PlaybackService::class.java))
    }
  }
}
