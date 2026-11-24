package de.notchedmusic.phone.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import de.notchedmusic.core.dsp.EqChain
import de.notchedmusic.core.dsp.Pcm
import de.notchedmusic.core.model.EqPreset
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Media3-Brücke: schiebt jedes 16-Bit-PCM-Paket durch die [EqChain].
 *
 * Der Prozessor ist nach dem ersten `onConfigure` **immer** aktiv – auch im Bypass. Ein Ab- und
 * Anmelden mitten in der Wiedergabe würde Media3 zwingen, die Pipeline neu zu bauen. Im Bypass
 * rechnet die Kette nur mit Lautstärke und Limiter.
 *
 * Der Audio-Faden liest ausschließlich unveränderliche Snapshots aus `@Volatile`-Feldern; es gibt
 * kein DataStore-I/O, keinen Mutex und keine UI-Zugriffe im Callback.
 */
@OptIn(UnstableApi::class)
class EqAudioProcessor : BaseAudioProcessor() {

  private val chain = EqChain()

  /** Zähler für Preset-/Bypass-Wechsel; der Audio-Faden vergleicht mit [appliedVersion]. */
  private val version = AtomicLong(0L)
  private var appliedVersion = NOT_CONFIGURED

  private var samples = FloatArray(0)

  @Volatile
  var preset: EqPreset = EqPreset(id = "p1", name = "Preset 1")
    set(value) {
      field = value
      version.incrementAndGet()
    }

  @Volatile
  var bypass: Boolean = true
    set(value) {
      field = value
      version.incrementAndGet()
    }

  /** App-eigene Hör-Lautstärke in dB (0 = voll, −60 = praktisch stumm). */
  @Volatile
  var volumeDb: Float = 0f

  /** Größte Spitze vor dem Limiter im letzten Puffer – Grundlage der Clip-Anzeige. */
  @Volatile
  var lastPeakLinear: Float = 0f
    private set

  /** Limiter-Gain am Ende des letzten Puffers (1.0 = kein Eingriff). */
  @Volatile
  var lastLimiterGain: Float = 1f
    private set

  @Volatile
  var processedFrames: Long = 0L
    private set

  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (
      inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
      inputAudioFormat.sampleRate <= 0 ||
      inputAudioFormat.channelCount <= 0
    ) {
      throw UnhandledAudioFormatException(inputAudioFormat)
    }
    chain.configure(
      sampleRateHz = inputAudioFormat.sampleRate.toDouble(),
      channelCount = inputAudioFormat.channelCount,
      preset = preset,
      bypass = bypass,
    )
    chain.setVolumeDb(volumeDb)
    appliedVersion = version.get()
    return inputAudioFormat
  }

  override fun queueInput(input: ByteBuffer) {
    val inputBytes = input.remaining()
    if (inputBytes == 0) return

    val channels = inputAudioFormat.channelCount
    val sampleRate = inputAudioFormat.sampleRate
    val output = replaceOutputBuffer(inputBytes)

    if (channels <= 0 || sampleRate <= 0) {
      output.put(input)
      output.flip()
      return
    }

    val sampleCount = inputBytes / Pcm.BYTES_PER_SAMPLE
    if (samples.size < sampleCount) samples = FloatArray(sampleCount)
    val buffer = samples

    Pcm.shortsToFloats(input, buffer, sampleCount)
    applyPendingConfiguration(sampleRate.toDouble(), channels)
    chain.setVolumeDb(volumeDb)
    chain.process(buffer)
    lastPeakLinear = chain.lastPeakLinear
    lastLimiterGain = chain.lastLimiterGain

    Pcm.floatsToShorts(buffer, output, sampleCount)
    output.flip()
    processedFrames += sampleCount / channels.toLong()
  }

  override fun onFlush(streamMetadata: StreamMetadata) {
    chain.reset()
  }

  override fun onReset() {
    chain.reset()
    samples = FloatArray(0)
    lastPeakLinear = 0f
    lastLimiterGain = 1f
    appliedVersion = NOT_CONFIGURED
  }

  private fun applyPendingConfiguration(sampleRateHz: Double, channelCount: Int) {
    val current = version.get()
    if (current == appliedVersion) return
    chain.configure(sampleRateHz, channelCount, preset, bypass)
    appliedVersion = current
  }

  private companion object {
    const val NOT_CONFIGURED = -1L
  }
}
