package de.notchedmusic.watch.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import de.notchedmusic.watch.data.Track
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Das bisschen Wiedergabe-Zustand, das die Oberfläche braucht. */
data class PlaybackUiState(
  /** Steht die Verbindung zum [PlaybackService]? */
  val isConnected: Boolean = false,
  val isPlaying: Boolean = false,
  /** Titel des aktuellen Stücks, `null` wenn nichts gewählt ist. */
  val currentTitle: String? = null,
  /** Pfad (MediaItem-ID) des aktuellen Stücks – Basis für die Wiederaufnahme. */
  val currentMediaId: String? = null,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
)

/**
 * Fernbedienung für den [PlaybackService] – die Schicht, über die die UI spielt.
 *
 * Die UI hält damit *keinen* Spieler mehr: ExoPlayer lebt im Service, hier sitzt nur ein
 * `MediaController`, der asynchron verbunden wird (Verbinden dauert und darf den Main-Thread nicht
 * blockieren). Alles, was die Oberfläche sieht, kommt aus [uiState].
 *
 * Alle Methoden müssen vom Main-Thread aufgerufen werden – Media3-Controller prüfen das ab und
 * würden sonst werfen.
 */
@OptIn(UnstableApi::class)
class PlaybackController(context: Context) : AutoCloseable {

  private val appContext = context.applicationContext
  private val mainHandler = Handler(Looper.getMainLooper())

  private val _uiState = MutableStateFlow(PlaybackUiState())

  /** Stand der Wiedergabe, aktualisiert von [playerListener] und [positionTicker]. */
  val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

  private var controller: MediaController? = null

  /** Wie oft die Oberfläche die Wiedergabestelle abholt (siehe [TaktPlan]). */
  private var takt = Anzeigetakt.VOLL

  /** `true`, sobald [close] einmal gelaufen ist – gegen Doppel-Freigabe. */
  private var released = false

  /** Reicht nur die Verbindungs-Informationen durch. */
  private val connectionListener =
    object : MediaController.Listener {
      override fun onDisconnected(mediaController: MediaController) {
        controller = null
        stopTicker()
        _uiState.value = PlaybackUiState()
      }
    }

  /** Der Player meldet gebündelt, was sich geändert hat. */
  private val playerListener =
    object : Player.Listener {
      override fun onEvents(player: Player, events: Player.Events) {
        if (
          events.containsAny(
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_PLAYBACK_STATE_CHANGED,
            Player.EVENT_IS_PLAYING_CHANGED,
          )
        ) {
          publishState()
        }
      }
    }

  private val connectionFuture =
    MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
      )
      .setListener(connectionListener)
      .buildAsync()

  init {
    connectionFuture.addListener(
      {
        val connected =
          try {
            connectionFuture.get()
          } catch (exception: Exception) {
            Log.w(TAG, "Verbindung zum PlaybackService fehlgeschlagen", exception)
            null
          }
        controller = connected
        connected?.addListener(playerListener)
        publishState()
      },
      appContext.mainExecutor,
    )
  }

  /**
   * Stellt eine Liste samt Titel ein, ohne sie zu starten.
   *
   * Das ist der Weg der Wiederaufnahme nach einem App-Start: Der letzte Titel steht im Spieler
   * gewählt, die Uhr beginnt aber erst auf Play zu spielen.
   */
  fun restoreList(tracks: List<Track>, atIndex: Int) {
    val player = controller ?: return
    if (tracks.isEmpty()) return
    player.setMediaItems(
      tracks.map { track -> track.toMediaItem() },
      /* startIndex = */ atIndex.coerceIn(0, tracks.lastIndex),
      /* startPositionMs = */ 0L,
    )
    player.prepare()
  }

  /**
   * Spielt die ganze Liste ab [atIndex] – der Service hängt die übrigen Stücke als Nächstes an.
   *
   * Der Pfad steckt doppelt in `MediaItem`: als `mediaId` (Wiedererkennen nach reconnect) und als
   * `uri` (davon öffnet der DataSource die Datei).
   */
  fun playList(tracks: List<Track>, atIndex: Int) {
    val player = controller ?: return
    if (tracks.isEmpty()) {
      // Nichts abzuspielen heißt auch: eine laufende Wiedergabe sauber beenden.
      stop()
      return
    }

    player.setMediaItems(
      tracks.map { track -> track.toMediaItem() },
      /* startIndex = */ atIndex.coerceIn(0, tracks.lastIndex),
      /* startPositionMs = */ 0L,
    )
    player.prepare()
    player.play()
  }

  /** Aktuelle Wiedergabe anhalten bzw. fortsetzen. */
  fun togglePlayPause() {
    val player = controller ?: return
    if (player.isPlaying) player.pause() else player.play()
  }

  /**
   * Nächster Titel.
   *
   * `seekToNextMediaItem()` springt den **Index** weiter, spielt aber von sich aus **nicht** weiter –
   * die erste Version hier tat genau das und die Wiedergabe blieb stehen. Deshalb folgt `play()`
   * immer dann, wenn der Sprung geklappt hat (`seekToDefaultPosition` liefert `true`).
   */
  fun next() {
    val player = controller ?: return
    if (!player.hasNextMediaItem()) return
    player.seekToNextMediaItem()
    player.play()
  }

  /**
   * Vorheriger Titel; am Anfang der Liste beginnt das Stück neu.
   *
   * Absichtlich **kein** Sprung an den Anfang innerhalb des laufenden Titels, wenn es einen
   * vorigen gibt: Auf der Uhr ist „zurück" fast immer „das davor", nicht „nochmal von vorn".
   */
  fun previous() {
    val player = controller ?: return
    if (!player.hasPreviousMediaItem()) {
      player.seekToDefaultPosition()
      player.play()
      return
    }
    player.seekToPreviousMediaItem()
    player.play()
  }

  /** Vollständig stoppen – auch die Liste wird geleert. */
  fun stop() {
    val player = controller ?: return
    player.stop()
    player.clearMediaItems()
  }

  /**
   * Gibt Verbindung und Controller frei. Muss vom Main-Thread, z. B. aus `ViewModel.onCleared()`.
   *
   * Mehrfach aufrufbar: `MediaController.releaseFuture` wirft, wenn die Verbindung vorher schon
   * gescheitert war – deshalb der Sperrschalter. Ohne ihn stürzt das Aufräumen ab, und zwar genau
   * dann, wenn ohnehin schon etwas schieflief.
   */
  override fun close() {
    if (released) return
    released = true
    stopTicker()
    controller = null
    MediaController.releaseFuture(connectionFuture)
    _uiState.value = PlaybackUiState()
  }

  private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
      .setMediaId(path)
      .setUri(Uri.fromFile(File(path)))
      .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
      .build()

  /** Zustand aus dem Controller übernehmen und den Ticker nachziehen. */
  private fun publishState() {
    val player = controller
    if (player == null) {
      stopTicker()
      _uiState.update { it.copy(isConnected = false) }
      return
    }

    val mediaItem = player.currentMediaItem
    val duration = player.duration
    _uiState.update {
      it.copy(
        isConnected = true,
        isPlaying = player.isPlaying,
        currentTitle = mediaItem?.mediaMetadata?.title?.toString() ?: mediaItem?.mediaId,
        currentMediaId = mediaItem?.mediaId,
        positionMs = player.currentPosition.coerceAtLeast(0L),
        durationMs = if (duration == C.TIME_UNSET) 0L else duration,
      )
    }

    // Der Ticker läuft nur während der Wiedergabe – alles andere wäre Akkuverschleiß auf der Uhr.
    if (player.isPlaying) startTicker() else stopTicker()
  }

  /** Schreibt die Wiedergabestelle; endet von selbst, sobald nichts mehr gespielt wird. */
  private val positionTicker =
    object : Runnable {
      override fun run() {
        if (released) return
        val player = controller ?: return
        val (isConnected, isPlaying) = _uiState.value
        if (!isConnected || !isPlaying) return
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        _uiState.update { it.copy(positionMs = positionMs) }
        val intervall = TaktPlan.intervallMs(takt) ?: return
        mainHandler.postDelayed(this, intervall)
      }
    }

  private fun startTicker() {
    mainHandler.removeCallbacks(positionTicker)
    val intervall = TaktPlan.intervallMs(takt) ?: return
    mainHandler.postDelayed(positionTicker, intervall)
  }

  private fun stopTicker() {
    mainHandler.removeCallbacks(positionTicker)
  }

  /**
   * Die Oberfläche meldet, ob sie sichtbar ist und ob der Bildschirm nur gedimmt ist.
   *
   * Ohne diese Meldung würde die Uhr 4× pro Sekunde zeichnen, obwohl niemand hinsieht – bei
   * gedimmtem OLED genauso unnötig wie bei ausgeschaltetem Display.
   */
  fun setzeAnzeigetakt(sichtbar: Boolean, reduziert: Boolean) {
    val neu = TaktPlan.takt(sichtbar, reduziert)
    if (neu == takt) return
    takt = neu
    when {
      neu == Anzeigetakt.PAUSIERT -> stopTicker()
      _uiState.value.isPlaying -> startTicker()
    }
  }

  private companion object {
    const val TAG = "PlaybackController"
  }
}
