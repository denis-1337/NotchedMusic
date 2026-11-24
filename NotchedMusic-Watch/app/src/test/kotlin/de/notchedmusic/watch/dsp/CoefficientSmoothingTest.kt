package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beweist, dass ein Preset- oder Bandwechsel **mitten im Ton** kein Knacken erzeugt.
 *
 * Ohne Überblendung würde ein Koeffizientensprung im Wellenverlauf als Sprung von bis zu einem
 * halben Vollausschlag hörbar – auf dem Kopfhörer ein Klicken. Mit der 128-Sample-Überblendung
 * muss die Änderung zwischen zwei Abtastwerten im Rahmen dessen bleiben, was das Signal selbst
 * an Steigung mitbringt.
 */
class CoefficientSmoothingTest {

  private val sampleRateHz = 48_000.0

  /** Ein 2-kHz-Sinus bei halber Aussteuerung: natürliche Steigung ≈ 0,13 pro Sample. */
  private val signalFrequencyHz = 2_000.0
  private val amplitude = 0.5f

  @Test
  fun `preset-wechsel erzeugt keinen sample-sprung`() {
    val chain = EffectChain().apply {
      configure(sampleRateHz, 1, testPreset(bands = listOf(notch(1_000.0))))
    }

    // Erst einige Buffer mit dem alten Preset einlaufen lassen.
    repeat(8) { chain.process(monoSine()) }

    chain.configure(sampleRateHz, 1, testPreset(bands = listOf(notch(12_000.0))))

    val afterSwitch = monoSine()
    chain.process(afterSwitch)

    val largestStep = largestSampleStep(afterSwitch)
    assertTrue(
      "Sprung von $largestStep pro Sample – Überblendung greift nicht",
      largestStep < MAX_STEP,
    )
  }

  @Test
  fun `gain-aenderung mitten im ton erzeugt keinen sample-sprung`() {
    val chain = EffectChain().apply { configure(sampleRateHz, 1, DefaultPresets.three().first()) }
    repeat(8) { chain.process(monoSine()) }

    // Gain über die Preset-Struktur ändern: die Kette muss weich nachziehen.
    chain.configure(
      sampleRateHz,
      1,
      testPreset(
        bands =
          listOf(
            FilterBand(
              frequencyHz = 8_000.0,
              q = 8.0,
              gainDb = 12.0,
              type = FilterType.PEAKING,
            )
          )
      ),
    )

    val afterSwitch = monoSine()
    chain.process(afterSwitch)

    assertTrue(largestSampleStep(afterSwitch) < MAX_STEP)
  }

  @Test
  fun `band-sprung von notch auf peaking bleibt weich`() {
    val chain = EffectChain().apply {
      configure(
        sampleRateHz,
        1,
        testPreset(
          bands =
            listOf(
              FilterBand(
                frequencyHz = 8_000.0,
                q = 4.0,
                gainDb = -20.0,
                type = FilterType.NOTCH,
              )
            )
        ),
      )
    }
    repeat(8) { chain.process(monoSine()) }

    // Anderer Typ, gleiche Bauform: nur Koeffizienten werden nachgezogen.
    chain.configure(
      sampleRateHz,
      1,
      testPreset(
        bands =
          listOf(
            FilterBand(
              frequencyHz = 8_000.0,
              q = 4.0,
              gainDb = -20.0,
              type = FilterType.PEAKING,
            )
          )
      ),
    )

    val afterSwitch = monoSine()
    chain.process(afterSwitch)

    assertTrue(largestSampleStep(afterSwitch) < MAX_STEP)
  }

  private fun notch(frequencyHz: Double): FilterBand =
    FilterBand(frequencyHz = frequencyHz, q = 4.0, gainDb = -20.0, type = FilterType.NOTCH)

  private fun monoSine(): FloatArray =
    FloatArray(FRAMES) { index ->
      (amplitude * sin(2 * PI * signalFrequencyHz * index / sampleRateHz)).toFloat()
    }

  private fun largestSampleStep(buffer: FloatArray): Float {
    var largest = 0f
    for (index in 1 until buffer.size) {
      val step = abs(buffer[index] - buffer[index - 1])
      if (step > largest) largest = step
    }
    return largest
  }

  private companion object {
    const val FRAMES = 1_024

    /** Natürliche Steigung des Testsignals ≈ 0,13 – alles bis 0,3 ist kein Klick. */
    const val MAX_STEP = 0.3f
  }
}
