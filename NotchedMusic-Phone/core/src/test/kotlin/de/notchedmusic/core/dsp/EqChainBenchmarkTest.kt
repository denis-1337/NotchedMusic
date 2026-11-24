package de.notchedmusic.core.dsp

import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterBand
import de.notchedmusic.core.model.FilterType
import kotlin.test.Test
import org.junit.Assert.assertTrue

/**
 * Ressourcen-Messung der Kette: sechs Bänder, Stereo, 48 kHz.
 *
 * Das Ziel aus dem Plan ist **< 15 ns pro Sample** (ein Sample = ein Kanal). Gemessen wird der
 * eingeschwungene Zustand nach 20 Aufwärmblöcken. Weil einzelne JVM-Läufe unter Systemlast
 * schwanken, wird der schnellste von fünf Durchläufen gewertet – so dokumentiert der Test die
 * tatsächliche Dauerleistung und schlägt trotzdem bei echten Regressionen an.
 */
class EqChainBenchmarkTest {

  @Test
  fun sechsBaenderStereoUnterDemBudget() {
    val preset =
      EqPreset(
        id = "bench",
        name = "Bench",
        preGainDb = -3.0,
        bands = List(6) { index ->
          FilterBand(
            type = FilterType.PEAKING,
            frequencyHz = 125.0 * (index + 1) * 3,
            q = 1.2,
            gainDb = 1.5,
          )
        },
      )
    val chain = EqChain()
    chain.configure(48_000.0, 2, preset, bypass = false)
    chain.setVolumeDb(-3f)

    val frames = 48_000
    val buffer = FloatArray(frames * 2) { (it % 200 - 100) / 500f }

    // Aufwärmen: erst danach ist der JIT-Lauf repräsentativ.
    repeat(20) { chain.process(buffer) }

    val measurements = mutableListOf<Double>()
    repeat(5) {
      val started = System.nanoTime()
      repeat(30) { chain.process(buffer) }
      val elapsedNs = System.nanoTime() - started
      measurements += elapsedNs.toDouble() / (frames * 2 * 30)
    }
    val best = measurements.min()

    println(
      "DSP-Benchmark: %.3f ns/Sample (Budget %.1f; Messungen: %s)".format(
        best,
        BUDGET_NS_PER_SAMPLE,
        measurements.joinToString(", ") { "%.2f".format(it) },
      ),
    )
    assertTrue(
      "DSP zu langsam: %.3f ns/Sample, Budget %.1f ns".format(best, BUDGET_NS_PER_SAMPLE),
      best < BUDGET_NS_PER_SAMPLE,
    )
  }

  private companion object {
    const val BUDGET_NS_PER_SAMPLE = 15.0
  }
}
