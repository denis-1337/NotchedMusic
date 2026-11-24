package de.notchedmusic.phone.playback

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import de.notchedmusic.core.model.RepeatMode
import de.notchedmusic.phone.data.LibraryTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Was die Player-Seite über die Wiedergabe wissen muss. */
data class PlaybackUiState(
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val currentIndex: Int = 0,
  val trackCount: Int = 0,
  val title: String = "",
  val subtitle: String = "",
  val durationMs: Long = 0L,
  /** Stabile Media-ID des laufenden Titels; Basis für die EQ-Statistik. */
  val currentMediaId: String? = null,
  val errorMessage: String? = null,
) {
  val hasTracks: Boolean
    get() = trackCount > 0
}

/**
 * Der eine ExoPlayer der App samt DSP-Kette.
 *
 * Activity und `MediaSessionService` greifen auf dieselbe Instanz zu. Alle Steueraufrufe kommen
 * vom Main-Thread (UI oder Dienst), nur der Audio-Faden liest den Prozessor. Der Zustand wird
 * als [StateFlow] veröffentlicht; die Oberfläche rendert ausschließlich daraus.
 */
@OptIn(UnstableApi::class)
object PlaybackEngine {

  private var playerInstance: ExoPlayer? = null

  @Volatile
  private var queue: List<LibraryTrack> = emptyList()

  private val _state = MutableStateFlow(PlaybackUiState())
  val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

  /**
   * Baut den Spieler mit dem eigenen AudioSink.
   *
   * Idempotent: Ein zweiter Aufruf (Activity und Dienst) liefert dieselbe Instanz.
   */
  fun initialize(context: Context, processor: EqAudioProcessor) {
    if (playerInstance != null) return
    val audioSink =
      DefaultAudioSink.Builder(context)
        .setAudioProcessorChain(EqAudioProcessorChain(processor))
        .build()
    val player =
      ExoPlayer.Builder(context, EqRenderersFactory(context, audioSink)).build().apply {
        setAudioAttributes(mediaAudioAttributes(), true)
        setHandleAudioBecomingNoisy(true)
        repeatMode = Player.REPEAT_MODE_ALL
        addListener(stateListener)
      }
    playerInstance = player
    updateState()
  }

  fun playerOrNull(): ExoPlayer? = playerInstance

  fun player(): ExoPlayer =
    checkNotNull(playerInstance) { "PlaybackEngine wurde noch nicht initialisiert" }

  /** Ersetzt die Warteschlange; ein laufender Titel wird gestoppt, sofern nicht anders verlangt. */
  fun setQueue(tracks: List<LibraryTrack>, startIndex: Int = 0, autoPlay: Boolean = false) {
    val player = playerInstance ?: return
    queue = tracks
    player.stop()
    player.clearMediaItems()
    if (tracks.isEmpty()) {
      updateState()
      return
    }
    val items = tracks.map { it.toMediaItem() }
    player.setMediaItems(items, startIndex.coerceIn(0, tracks.lastIndex), 0L)
    player.prepare()
    player.playWhenReady = autoPlay
    updateState()
  }

  fun applySettings(repeatMode: RepeatMode, autoAdvance: Boolean) {
    val player = playerInstance ?: return
    player.repeatMode =
      when {
        !autoAdvance -> Player.REPEAT_MODE_OFF
        repeatMode == RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        repeatMode == RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        else -> Player.REPEAT_MODE_OFF
      }
  }

  /** Play/Pause; ohne Warteschlange passiert nichts. */
  fun toggle() {
    val player = playerInstance ?: return
    if (queue.isEmpty()) return
    if (player.isPlaying || player.playWhenReady) {
      player.pause()
    } else {
      player.play()
    }
  }

  fun play() {
    val player = playerInstance ?: return
    if (queue.isNotEmpty()) player.play()
  }

  fun pause() {
    playerInstance?.pause()
  }

  fun next() {
    val player = playerInstance ?: return
    if (queue.isEmpty()) return
    player.seekToNext()
  }

  fun previous() {
    val player = playerInstance ?: return
    if (queue.isEmpty()) return
    player.seekToPrevious()
  }

  /** Springt im Titel- oder Ordnerwechsel auf einen bestimmten Eintrag. */
  fun selectIndex(index: Int) {
    val player = playerInstance ?: return
    if (queue.isEmpty()) return
    player.seekTo(index.coerceIn(0, queue.lastIndex), 0L)
    updateState()
  }

  fun stop() {
    val player = playerInstance ?: return
    player.stop()
    updateState()
  }

  fun clearError() {
    _state.update { it.copy(errorMessage = null) }
  }

  internal fun currentQueue(): List<LibraryTrack> = queue

  /**
   * Hängt das geladene Cover an den laufenden Titel, damit auch die Medienmeldung ein Bild zeigt.
   *
   * Die Kompression läuft im Hintergrund; der Austausch des MediaItems bleibt auf dem Main-Thread,
   * weil ExoPlayer dort seinen Looper hat. Ein Austausch des aktuellen Eintrags lässt die
   * Wiedergabeposition unangetastet.
   */
  suspend fun updateCurrentItem(track: LibraryTrack, artwork: Bitmap?) {
    val player = playerInstance ?: return
    val item = player.currentMediaItem ?: return
    if (item.localConfiguration?.uri != track.uri) return

    val artworkData =
      artwork?.let { bitmap ->
        withContext(Dispatchers.Default) {
          ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) return@withContext null
            output.toByteArray()
          }
        }
      }

    val metadata =
      item.mediaMetadata
        .buildUpon()
        .setTitle(track.title)
        .setArtist(track.subtitle)
        .setAlbumTitle(track.album)
        .apply {
          if (artworkData != null) {
            setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
          }
        }
        .build()
    player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build())
  }

  private val stateListener =
    object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) = updateState()

      override fun onIsPlayingChanged(isPlaying: Boolean) = updateState()

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateState()

      override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updateState()

      override fun onPlayerError(error: PlaybackException) {
        _state.update {
          it.copy(
            errorMessage =
              "This track cannot be played (${error.errorCodeName}). " +
                "Playback remains stopped.",
          )
        }
      }
    }

  private fun updateState() {
    val player = playerInstance ?: return
    val index = player.currentMediaItemIndex.coerceAtLeast(0)
    val track = queue.getOrNull(index)
    _state.value =
      PlaybackUiState(
        isPlaying = player.isPlaying,
        isBuffering = player.playbackState == Player.STATE_BUFFERING,
        currentIndex = index,
        trackCount = queue.size,
        title = player.mediaMetadata.title?.toString() ?: track?.title.orEmpty(),
        subtitle = player.mediaMetadata.artist?.toString() ?: track?.subtitle.orEmpty(),
        durationMs = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: track?.durationMs ?: 0L,
        currentMediaId = player.currentMediaItem?.mediaId ?: track?.documentId,
        errorMessage = _state.value.errorMessage,
      )
  }

  private fun LibraryTrack.toMediaItem(): MediaItem =
    MediaItem.Builder()
      .setUri(uri)
      .setMediaId(documentId)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(title)
          .setArtist(subtitle)
          .build(),
      )
      .build()

  @Suppress("DEPRECATION")
  private fun mediaAudioAttributes(): AudioAttributes =
    AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()

  /** Media3 1.11 baut den AudioSink im RenderersFactory – dort sitzt die DSP-Kette. */
  private class EqRenderersFactory(
    context: Context,
    private val audioSink: AudioSink,
  ) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
      context: Context,
      enableFloatOutput: Boolean,
      enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = audioSink
  }
}
