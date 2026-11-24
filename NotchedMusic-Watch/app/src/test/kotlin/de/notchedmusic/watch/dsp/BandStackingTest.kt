package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft das Verhalten mehrerer Bänder zusammen:
 *
 * * Bänder wirken in Reihe multiplikativ – bei gleicher Mittenfrequenz addieren sich ihre
 *   dB-Werte.
 * * `enabled` schaltet ein Band einzeln heraus.
 * * Die Reihenfolge ist für die Summenwirkung egal (LTI-Systeme sind vertauschbar).
 * * Die Summen-Anhebung wird auf +12 dB begrenzt, damit kein Preset den Limiter zur Regel macht.
 */
class BandStackingTest {

  private val sampleRateHz = 48_000.0
  private val frequencyHz = 1_000.0

  @Test
  fun `gleichfrequente glocken addieren ihre db`() {
    val preset =
      testPreset(
        bands =
          listOf(
            peaking(gainDb = 6.0),
            peaking(gainDb = 6.0),
          ),
      )

    assertEquals(12.0, measuredGainDb(preset), 1.0)
  }

  @Test
  fun `ein stummes band zaehlt nicht mit`() {
    val preset =
      testPreset(
        bands =
          listOf(
            peaking(gainDb = 6.0),
            peaking(gainDb = 6.0).copy(enabled = false),
          ),
      )

    assertEquals(6.0, measuredGainDb(preset), 1.0)
  }

  @Test
  fun `die reihenfolge aendert die summenwirkung nicht`() {
    val first = peaking(frequencyHz = 1_000.0, gainDb = 6.0)
    val second = peaking(frequencyHz = 3_000.0, gainDb = -4.0)

    val chainA =
      EffectChain().apply {
        configure(sampleRateHz, 1, testPreset(bands = listOf(first, second)))
      }
    val chainB =
      EffectChain().apply {
        configure(sampleRateHz, 1, testPreset(bands = listOf(second, first)))
      }
    val signal = sine(amplitude = 0.01f)
    val a = signal.copyOf().also { chainA.process(it) }
    val b = signal.copyOf().also { chainB.process(it) }

    assertArrayEquals(a, b, 1e-4f)
  }

  @Test
  fun `sechs maximale anheber werden auf zusammen 12 db beschnitten`() {
    val preset =
      testPreset(
        bands = (0 until EqPreset.MAX_BANDS).map { peaking(gainDb = EqPreset.MAX_BAND_GAIN_DB) },
      )

    val validated = preset.validate(sampleRateHz)
    val positiveSum = validated.bands.sumOf { it.gainDb }

    assertEquals(EqPreset.MAX_BANDS, validated.bands.size)
    assertEquals(EqPreset.MAX_TOTAL_BOOST_DB, positiveSum, 1e-6)
    assertEquals(EqPreset.MAX_TOTAL_BOOST_DB, measuredGainDb(preset), 1.5)
  }

  @Test
  fun `absenkungen helfen nicht beim anhebe-budget mit`() {
    val preset =
      testPreset(
        bands =
          listOf(
            peaking(frequencyHz = 1_000.0, gainDb = EqPreset.MAX_BAND_GAIN_DB),
            peaking(frequencyHz = 5_000.0, gainDb = EqPreset.MAX_BAND_GAIN_DB),
            peaking(frequencyHz = 8_000.0, gainDb = -EqPreset.MAX_BAND_GAIN_DB),
          ),
      )

    val validated = preset.validate(sampleRateHz)
    val positiveSum = validated.bands.filter { it.gainDb > 0.0 }.sumOf { it.gainDb }

    assertEquals(EqPreset.MAX_TOTAL_BOOST_DB, positiveSum, 1e-6)
    assertTrue(validated.bands.any { it.gainDb < 0.0 })
  }

  private fun peaking(
    frequencyHz: Double = this.frequencyHz,
    gainDb: Double,
  ): FilterBand =
    FilterBand(
      frequencyHz = frequencyHz,
      q = 4.0,
      gainDb = gainDb,
      type = FilterType.PEAKING,
    )

  /** Verstärkung bei [frequencyHz] in dB, gemessen über die Energie eines ganzen Buffers. */
  private fun measuredGainDb(preset: EqPreset): Double {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, preset) }
    val input = sine(amplitude = 0.01f)
    val output = input.copyOf().also { chain.process(it) }

    var inEnergy = 0.0
    var outEnergy = 0.0
    for (index in 0 until input.size) {
      inEnergy += input[index].toDouble() * input[index]
      outEnergy += output[index].toDouble() * output[index]
    }
    return 10 * log10(outEnergy / inEnergy)
  }

  private fun sine(amplitude: Float): FloatArray =
    FloatArray(FRAMES) { index ->
      (amplitude * sin(2 * PI * frequencyHz * index / sampleRateHz)).toFloat()
    }

  private companion object {
    const val FRAMES = 8_192
  }
}
