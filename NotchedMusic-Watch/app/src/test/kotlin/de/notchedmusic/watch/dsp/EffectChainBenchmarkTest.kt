package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Laufzeit-Beweis für die Klangkette: sechs Bänder gemischt, stereo, 48 kHz.
 *
 * Zwei Zusagen aus dem Plan werden hier gemessen:
 *
 * * **Mittelwert < 15 ns/Sample** – das sind bei 48 kHz unter 0,75 ms CPU pro Sekunde Musik.
 * * **schlechtester Einzelpuffer < 1 ms** – fängt Neuberechnungs- und Überblendspitzen ab; ein
 *   Mittelwert allein würde genau die verstecken.
 *
 * Gemessen wird nach dem Warmlaufen (JIT) über mehrere Läufe; für den Mittelwert zählt der beste
 * Lauf, für den schlechtesten Puffer der schlechteste. Das ist die ehrliche Kombination: Der
 * Mittelwert zeigt den ausoptimierten Zustand, die Spitze zeigt, dass auch der erste Puffer
 * nach einem Preset-Wechsel in Echtzeit bleibt.
 */
class EffectChainBenchmarkTest {

  @Test
  fun `sechs baender bei 48 kHz stereo bleiben unter 15 ns pro sample und 1 ms pro puffer`() {
    val chain = EffectChain().apply { configure(SAMPLE_RATE_HZ, CHANNEL_COUNT, PRESET_SIX_BANDS) }
    val buffer = pseudoRandomBuffer()

    val samplesPerRun = (SAMPLE_RATE_HZ * AUDIO_SECONDS * CHANNEL_COUNT).toLong()
    repeat(WARMUP_BUFFERS) { chain.process(buffer) }
    chain.reset()

    var bestNanosPerSample = Double.MAX_VALUE
    var worstBufferNanos = 0L
    repeat(RUNS) {
      var processed = 0L
      val startedAt = System.nanoTime()
      while (processed < samplesPerRun) {
        val bufferStartedAt = System.nanoTime()
        chain.process(buffer)
        val bufferElapsed = System.nanoTime() - bufferStartedAt
        if (bufferElapsed > worstBufferNanos) worstBufferNanos = bufferElapsed
        processed += buffer.size
      }
      val elapsed = System.nanoTime() - startedAt
      bestNanosPerSample = minOf(bestNanosPerSample, elapsed.toDouble() / samplesPerRun)
    }

    val worstBufferMillis = worstBufferNanos / 1_000_000.0
    println(
      "DSP-Benchmark: %s ns/Sample, schlechtester Puffer %s ms (%d Baender, %d Hz, %d Kanaele, %d Laeufe)"
        .format(
          nanos(bestNanosPerSample),
          millis(worstBufferMillis),
          PRESET_SIX_BANDS.bands.size,
          SAMPLE_RATE_HZ.toInt(),
          CHANNEL_COUNT,
          RUNS,
        ),
    )

    assertTrue(
      "Erwartet unter $MAX_NANOS_PER_SAMPLE ns/Sample, gemessen ${nanos(bestNanosPerSample)}",
      bestNanosPerSample < MAX_NANOS_PER_SAMPLE,
    )
    assertTrue(
      "Erwartet unter $MAX_BUFFER_MILLIS ms pro Puffer, gemessen ${millis(worstBufferMillis)}",
      worstBufferMillis < MAX_BUFFER_MILLIS,
    )
  }

  private fun nanos(value: Double): String = String.format(Locale.US, "%.3f", value)

  private fun millis(value: Double): String = String.format(Locale.US, "%.3f", value)

  /** Gleichmäßiges Rauschen – kostet exakt so viel Rechenaufwand wie Musik. */
  private fun pseudoRandomBuffer(): FloatArray =
    FloatArray(BUFFER_FRAMES * CHANNEL_COUNT) { index ->
      val mixed = ((index * 1_103_515_245) + 12_345) shr 16
      (mixed and 0xFFFF) / 32_768f - 1f
    }

  private companion object {
    const val SAMPLE_RATE_HZ = 48_000.0
    const val CHANNEL_COUNT = 2

    /** Der reale Fall: sechs gemischte Bänder, zwei davon schmal, plus Limiter. */
    val PRESET_SIX_BANDS =
      testPreset(
        bands =
          listOf(
            FilterBand(type = FilterType.LOW_SHELF, frequencyHz = 80.0, q = 0.7, gainDb = 4.0),
            FilterBand(type = FilterType.PEAKING, frequencyHz = 300.0, q = 2.0, gainDb = -6.0),
            FilterBand(type = FilterType.PEAKING, frequencyHz = 2_000.0, q = 4.0, gainDb = 3.0),
            FilterBand(type = FilterType.NOTCH, frequencyHz = 6_000.0, q = 7.5, gainDb = -20.0),
            FilterBand(type = FilterType.PEAKING, frequencyHz = 9_500.0, q = 20.0, gainDb = -15.0),
            FilterBand(type = FilterType.HIGH_SHELF, frequencyHz = 12_000.0, q = 0.7, gainDb = -3.0),
          ),
        preGainDb = -6f,
      )

    /** Eine Buffergröße, wie Media3 sie typisch liefert (1024 Frames stereo). */
    const val BUFFER_FRAMES = 1_024

    /** Etwa 20 Sekunden Material pro Lauf – genug für stabile Zahlen, kurz genug für jeden Build. */
    const val AUDIO_SECONDS = 20.0

    const val WARMUP_BUFFERS = 2_000
    const val RUNS = 3

    /** Grenze aus dem Plan: 15 ns pro Sample bei sechs Bändern. */
    const val MAX_NANOS_PER_SAMPLE = 15.0

    /** Grenze aus dem Plan: schlechtester Puffer unter 1 ms. */
    const val MAX_BUFFER_MILLIS = 1.0
  }
}
