package de.notchedmusic.watch.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import de.notchedmusic.watch.MainActivity
import de.notchedmusic.watch.WatchApplication
import de.notchedmusic.watch.dsp.EffectAudioProcessor
import de.notchedmusic.watch.dsp.EffectAudioProcessorChain
import de.notchedmusic.watch.model.EqPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Der eigentliche Spieler: Er besitzt ExoPlayer, AudioSink und MediaSession – die Activity ist
 * nur Fernbedienung (siehe [PlaybackController]).
 *
 * Als `MediaSessionService` läuft die Wiedergabe weiter, wenn die Activity längst zu ist, und
 * andere Apps (Mediensteuerung, Kopfhörer-Knöpfe) finden die Sitzung über den Intent aus dem
 * Manifest. Der Notch-Filter sitzt im AudioSink *zwischen* Decoder und Lautsprecher – der Rest
 * der Wiedergabe-Kette merkt davon nichts.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

  private var player: ExoPlayer? = null
  private var mediaSession: MediaSession? = null

  /** Sammelt die Filter-Vorgaben, solange der Service lebt. */
  private var settingsScope: CoroutineScope? = null

  /** Hält die EQ-Statistik an denselben Playback-Ereignissen wie den Klang. */
  private var statsListener: Player.Listener? = null

  private val stats: de.notchedmusic.watch.stats.WatchStatsRepository
    get() = (application as WatchApplication).stats

  override fun onCreate() {
    super.onCreate()

    // Gespeicherte Vorgabe laden, bevor der erste Audio-Block konfiguriert wird – sonst spielte
    // der erste Sekundenbruchteil mit dem Standard statt mit dem eingestellten Notch.
    // Idempotent: ein zweiter Start des Dienstes lädt nichts doppelt.
    FilterSettings.attachStore(this)

    val processor =
      EffectAudioProcessor().apply {
        preset = FilterSettings.activePreset() ?: EqPreset(id = "off", name = "Off")
        bypass = FilterSettings.current().activePresetId == null
        volumeDb = FilterSettings.current().volumeDb
      }
    val audioSink =
      DefaultAudioSink.Builder(this)
        .setAudioProcessorChain(EffectAudioProcessorChain(processor))
        .build()

    val exoPlayer =
      ExoPlayer.Builder(this).setRenderersFactory(EffectRenderersFactory(this, audioSink)).build()
    exoPlayer.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus = */ true)

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    scope.launch {
      FilterSettings.settings.collect { settings ->
        processor.preset = settings.activePreset() ?: EqPreset(id = "off", name = "Off")
        processor.bypass = settings.activePresetId == null
        processor.volumeDb = settings.volumeDb
        stats.setEqOn(settings.activePresetId != null)
      }
    }
    settingsScope = scope

    val listener =
      object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishStats()
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = publishStats()
        override fun onPlaybackStateChanged(playbackState: Int) = publishStats()
      }
    statsListener = listener
    exoPlayer.addListener(listener)
    stats.setEqOn(FilterSettings.current().activePresetId != null)

    val openApp =
      PendingIntent.getActivity(
        this,
        REQUEST_CODE_OPEN_APP,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
      )

    player = exoPlayer
    mediaSession =
      MediaSession.Builder(this, exoPlayer).setId(SESSION_ID).setSessionActivity(openApp).build()
  }

  /**
   * Liefert die Sitzung an jeden, der sich mit unserem [androidx.media3.session.SessionToken]
   * verbindet. Gibt es sie (noch) nicht, wird der Service sofort abgebaut statt leer weiter
   * zu laufen.
   */
  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
    val session = mediaSession
    if (session == null) stopSelf()
    return session
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    val current = player
    if ((current == null) || !current.playWhenReady || (current.mediaItemCount == 0)) {
      stopSelf()
    }
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    stats.flushActive()
    settingsScope?.cancel()
    settingsScope = null

    val session = mediaSession
    mediaSession = null
    val exoPlayer = player
    player = null
    statsListener?.let { listener -> exoPlayer?.removeListener(listener) }
    statsListener = null

    session?.release()
    exoPlayer?.release()
    super.onDestroy()
  }

  /** Meldet den aktuellen Wiedergabe-/Titelzustand an die EQ-Statistik. */
  private fun publishStats() {
    val current = player ?: return
    stats.updatePlayback(current.isPlaying, current.currentMediaItem?.mediaId)
  }

  /**
   * MEDIEN-Nutzung mit Musik-Inhalt.
   *
   * Achtung auf die Namensähnlichkeit: `setContentType` will die `AUDIO_CONTENT_TYPE_*`-Familie
   * (`C.AUDIO_CONTENT_TYPE_MUSIC`), nicht das sehr ähnlich aussehende `C.CONTENT_TYPE_MUSIC`.
   * Beide stehen in Media3 und haben unterschiedliche Nummern – der falsche Wert landet sonst
   * als SILLENT im System oder gar nicht.
   *
   * `setContentType` ist bei Media3 veraltet, weil Android die Angabe neuerdings selbst wählt.
   * Ohne sie steht jedoch CONTENT_TYPE_UNKNOWN im Logcat und das Routing läuft über den
   * Standard-Stream – für Kopfhörer-Wiedergabe mit EQ genau das, was man nicht will.
   */
  @Suppress("DEPRECATION")
  private fun mediaAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()

  /**
   * Media3 1.11 baut den AudioSink im `DefaultRenderersFactory` (es gibt kein
   * `ExoPlayer.Builder.setAudioSink` mehr) – deshalb wird er hier eingesetzt.
   */
  private class EffectRenderersFactory(context: Context, private val audioSink: AudioSink) :
    DefaultRenderersFactory(context) {

    override fun buildAudioSink(
      context: Context,
      enableFloatOutput: Boolean,
      enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = audioSink
  }

  private companion object {
    /** Feste ID, damit Controllers und spätere Einstellungen sich immer auf dieselbe Sitzung beziehen. */
    const val SESSION_ID = "notched"

    const val REQUEST_CODE_OPEN_APP = 0
  }
}
