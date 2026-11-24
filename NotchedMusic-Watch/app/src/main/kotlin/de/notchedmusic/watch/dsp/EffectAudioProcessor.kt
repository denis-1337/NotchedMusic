package de.notchedmusic.watch.dsp

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.StreamMetadata
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import de.notchedmusic.watch.model.EqPreset
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Media3-Brücke: schiebt jedes 16-Bit-PCM-Paket durch [EffectChain].
 *
 * Der Prozessor bleibt nach erfolgreichem `onConfigure` immer in der Pipeline – auch im Bypass.
 * Ein Umbau mitten in der Wiedergabe würde Puffer, Positionen und Flush-Zustand gefährden.
 */
@OptIn(UnstableApi::class)
class EffectAudioProcessor : BaseAudioProcessor() {

  private val chain = EffectChain()

  private val presetVersion = AtomicLong(0L)

  private var appliedPresetVersion = NOT_CONFIGURED

  private var samples = FloatArray(0)

  /** Aktuelles Klangrezept; ein leeres Preset ist der Bypass-Zustand. */
  @Volatile var preset: EqPreset = EqPreset(id = "off", name = "Aus")
    set(value) {
      field = value
      presetVersion.incrementAndGet()
    }

  /** Schaltet Filter und Vor-Verstärkung aus; Lautstärke und Limiter bleiben. */
  @Volatile var bypass: Boolean = true
    set(value) {
      field = value
      presetVersion.incrementAndGet()
    }

  /** App-eigene Hör-Lautstärke in dB (0 = voll, −60 = fast stumm). */
  @Volatile var volumeDb: Float = 0f

  @Volatile var processedFrames: Long = 0L
    private set

  override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
    if (
      (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) ||
        (inputAudioFormat.sampleRate <= 0) ||
        (inputAudioFormat.channelCount <= 0)
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
    appliedPresetVersion = presetVersion.get()
    return inputAudioFormat
  }

  override fun queueInput(input: ByteBuffer) {
    val inputBytes = input.remaining()
    if (inputBytes == 0) return

    val channelCount = inputAudioFormat.channelCount
    val sampleRateHz = inputAudioFormat.sampleRate
    val output = replaceOutputBuffer(inputBytes)

    if ((channelCount <= 0) || (sampleRateHz <= 0)) {
      output.put(input)
      output.flip()
      return
    }

    val sampleCount = inputBytes / Pcm.BYTES_PER_SAMPLE
    if (samples.size < sampleCount) samples = FloatArray(sampleCount)
    val buffer = samples

    Pcm.shortsToFloats(input, buffer, sampleCount)
    applyPendingPreset(sampleRateHz.toDouble(), channelCount)
    chain.setVolumeDb(volumeDb)
    chain.process(buffer)

    Pcm.floatsToShorts(buffer, output, sampleCount)
    output.flip()
    processedFrames += sampleCount / channelCount.toLong()
  }

  override fun onFlush(streamMetadata: StreamMetadata) {
    chain.reset()
  }

  override fun onReset() {
    chain.reset()
    samples = FloatArray(0)
    appliedPresetVersion = NOT_CONFIGURED
  }

  private fun applyPendingPreset(sampleRateHz: Double, channelCount: Int) {
    val version = presetVersion.get()
    if (version == appliedPresetVersion) return
    chain.configure(sampleRateHz, channelCount, preset, bypass)
    appliedPresetVersion = version
  }

  private companion object {
    const val NOT_CONFIGURED = -1L
  }
}
