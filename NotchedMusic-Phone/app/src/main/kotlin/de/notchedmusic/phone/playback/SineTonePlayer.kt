package de.notchedmusic.phone.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import de.notchedmusic.core.dsp.EqChain
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sinuston zur Tinnitus-Frequenzbestimmung.
 *
 * Bewusst ein eigener [AudioTrack] statt ExoPlayer: Der Ton ist ein reiner Rechenwert, braucht
 * keine Datei und muss in 1-Hz-Schritten exakt einstellbar sein. Die Ausgabe läuft in einem
 * eigenen Hintergrund-Job; Frequenz und Lautstärke sind `@Volatile` und werden ohne Neustart
 * übernommen. Der Ton spielt über dieselbe Standard-Audiospur wie Musik (Bluetooth-kompatibel).
 */
class SineTonePlayer(private val scope: CoroutineScope) {

  private val _isPlaying = MutableStateFlow(false)
  val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

  @Volatile private var frequencyHz: Double = 440.0

  /** Pegel als linearer Faktor (0 … 1). */
  @Volatile private var amplitude: Float = 0.1f

  private var toneJob: Job? = null
  private var audioTrack: AudioTrack? = null

  fun setFrequency(hz: Int) {
    frequencyHz = hz.toDouble().coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
  }

  fun setVolumeDb(volumeDb: Float) {
    val clamped = volumeDb.coerceIn(-60f, 0f)
    amplitude = if (clamped <= -60f) 0f else maxOf(0.001f, EqChain.dbToFactor(clamped.toDouble()) * SAFETY_FACTOR)
  }

  fun toggle() {
    if (_isPlaying.value) stop() else start()
  }

  fun start() {
    if (_isPlaying.value) return
    _isPlaying.value = true
    toneJob =
      scope.launch(Dispatchers.Default) {
        runTone()
      }.apply {
        invokeOnCompletion {
          _isPlaying.value = false
        }
      }
  }

  fun stop() {
    val track = audioTrack
    audioTrack = null
    toneJob?.cancel()
    toneJob = null
    try {
      track?.stop()
    } catch (_: IllegalStateException) {
      // Schon freigegeben oder nie gestartet – nichts zu tun.
    } finally {
      track?.release()
    }
    _isPlaying.value = false
  }

  private suspend fun runTone() {
    val minBuffer =
      AudioTrack.getMinBufferSize(
        SAMPLE_RATE_HZ,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )
    if (minBuffer <= 0) {
      Log.w(TAG, "AudioTrack-Puffer konnte nicht bestimmt werden ($minBuffer)")
      return
    }

    val track =
      AudioTrack.Builder()
        .setAudioAttributes(
          AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build(),
        )
        .setAudioFormat(
          AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
        )
        .setBufferSizeInBytes(minBuffer * 2)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    audioTrack = track
    try {
      track.play()
      val block = FloatArray(BLOCK_FRAMES)
      var phase = 0.0
      var currentAmplitude = 0f
      while (currentCoroutineContext().isActive) {
        val targetAmplitude = amplitude
        val frequency = frequencyHz.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)
        val increment = 2.0 * PI * frequency / SAMPLE_RATE_HZ
        for (index in block.indices) {
          currentAmplitude += (targetAmplitude - currentAmplitude) * AMPLITUDE_RAMP
          block[index] = (sin(phase) * currentAmplitude).toFloat()
          phase += increment
          if (phase >= 2.0 * PI) phase -= 2.0 * PI
        }
        val written = track.write(block, 0, block.size, AudioTrack.WRITE_BLOCKING)
        if (written < 0) {
          Log.w(TAG, "AudioTrack.write fehlgeschlagen: $written")
          break
        }
      }
    } catch (error: IllegalStateException) {
      Log.w(TAG, "Sinuston abgebrochen", error)
    } finally {
      try {
        track.stop()
      } catch (_: IllegalStateException) {
        // Bereits gestoppt.
      }
      track.release()
      if (audioTrack === track) audioTrack = null
      _isPlaying.value = false
    }
  }

  private companion object {
    const val TAG = "SineTonePlayer"
    const val SAMPLE_RATE_HZ = 48_000
    const val BLOCK_FRAMES = 960
    const val AMPLITUDE_RAMP = 0.002f
    const val SAFETY_FACTOR = 0.8f
    const val MIN_FREQUENCY_HZ = 100.0
    const val MAX_FREQUENCY_HZ = 15_000.0
  }
}
