package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Impulsantwort auf die zwei klassischen IIR-Krankheiten:
 *
 * * **anwachsendes Nachklingen** (instabile Pole) – die Energie muss monoton fallen,
 * * **NaN/Inf** – auch ein Q von 30 bei 20 Hz darf den Zustand nicht vergiften.
 *
 * Die theoretische Hüllkurve klingt mit `Q/(π·f₀)` ab. Bei 8 kHz und Q 30 sind das etwa 1,2 ms;
 * nach 50 ms ist praktisch nichts mehr übrig. Der Test nutzt bewusst einen großzügigen Rahmen,
 * damit er die Physik prüft und nicht die letzte Stelle hinter dem Komma.
 */
class RingingTest {

  private val sampleRateHz = 48_000.0

  @Test
  fun `impulsantwort klingt ab und waechst nie`() {
    val chain =
      EffectChain().apply {
        configure(
          sampleRateHz,
          1,
          testPreset(
            bands =
              listOf(
                FilterBand(
                  frequencyHz = 8_000.0,
                  q = EqPreset.MAX_Q,
                  gainDb = -20.0,
                  type = FilterType.NOTCH,
                )
              )
          ),
        )
      }

    val response = FloatArray(48_000)
    response[0] = 0.5f
    chain.process(response)

    val firstWindow = windowEnergy(response, 0, 2_400)
    val lastWindow = windowEnergy(response, 45_600, 48_000)

    assertTrue("Erste Hüllkurve ist null", firstWindow > 0.0)
    assertTrue("Letzte Hüllkurve $lastWindow klingt nicht ab", lastWindow < firstWindow * 1e-3)

    // Jedes Fenster darf höchstens so energiereich sein wie das davor.
    var previous = Double.MAX_VALUE
    var start = 0
    while (start + WINDOW_SAMPLES <= response.size) {
      val energy = windowEnergy(response, start, start + WINDOW_SAMPLES)
      assertTrue("Fenster ab $start wächst: $energy > $previous", energy <= previous + 1e-9)
      previous = energy
      start += WINDOW_SAMPLES
    }

    response.forEach { assertTrue("Wert $it ist nicht endlich", it.isFinite()) }
  }

  @Test
  fun `tiefes q bei niedriger frequenz bleibt stabil`() {
    val chain =
      EffectChain().apply {
        configure(
          sampleRateHz,
          2,
          testPreset(
            bands =
              listOf(
                FilterBand(
                  frequencyHz = EqPreset.MIN_FREQUENCY_HZ,
                  q = 1.0,
                  gainDb = EqPreset.MAX_BAND_GAIN_DB,
                  type = FilterType.PEAKING,
                )
              )
          ),
        )
      }

    val buffer = FloatArray(4_800 * 2) { index -> if ((index / 2) % 2 == 0) 0.8f else -0.8f }
    chain.process(buffer)

    buffer.forEach { value ->
      assertTrue("Wert $value ist nicht endlich", value.isFinite())
      assertTrue("Wert $value liegt über der Decke", abs(value) <= 1f)
    }

    val tail = FloatArray(4_800)
    chain.process(tail)
    tail.forEach { assertTrue("Nachklingen endet nicht: $it", abs(it) < 0.1f) }
  }

  private fun windowEnergy(buffer: FloatArray, from: Int, to: Int): Double {
    var sum = 0.0
    for (index in from until to) {
      sum += buffer[index].toDouble() * buffer[index]
    }
    return sum
  }

  private companion object {
    /** 50-ms-Fenster bei 48 kHz. */
    const val WINDOW_SAMPLES = 2_400
  }
}
