package de.notchedmusic.watch.dsp

import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Beweist die zwei Zusagen des Limiters auf dem PC:
 *
 * 1. **Keine Übersteuerung** – auch wenn sechs Bänder bei +24 dB und die maximale Vor-Verstärkung
 *    zusammen rechnerisch weit über 1,0 landen, verlässt kein Sample die Decke −1 dBFS.
 * 2. **Sauberer Angriff und Erholung** – nach einem lauten Block steigt der Pegel leise wieder,
 *    ohne Sprung und ohne NaN.
 */
class LimiterTest {

  private val sampleRateHz = 48_000.0
  private val ceiling = 10.0.pow(Limiter.CEILING_DB / 20.0).toFloat()

  @Test
  fun `begrenzt spitzen auf die decke - beide kanaele gemeinsam`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 2) }
    val buffer = FloatArray(256 * 2) { index -> if (index % 2 == 0) 2f else -2f }

    limiter.process(buffer)

    buffer.forEach { value ->
      assertTrue("Wert $value liegt über der Decke", abs(value) <= ceiling + 1e-6f)
    }
  }

  @Test
  fun `stereo-verkoppelt - ein lauter linker kanal daempft auch rechts`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 2) }
    val buffer = FloatArray(64 * 2)
    for (frame in 0 until 64) {
      buffer[frame * 2] = 2f
      buffer[frame * 2 + 1] = 0.5f
    }

    limiter.process(buffer)

    for (frame in 0 until 64) {
      val left = buffer[frame * 2]
      val right = buffer[frame * 2 + 1]
      assertEquals("Frame $frame: gleicher Gain für beide Kanäle", left / right, 4f, 1e-3f)
    }
  }

  @Test
  fun `erholt sich exponentiell und springt nicht`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 1) }
    val loud = FloatArray(64) { 2f }
    limiter.process(loud)

    // Danach ein leiser, konstanter Pegel: der Gain muss allmählich zurückkommen. 300 ms sind
    // sechs Zeitkonstanten – danach ist die Erholung praktisch abgeschlossen.
    val quiet = FloatArray(RELEASE_CHECK_SAMPLES) { 0.1f }
    limiter.process(quiet)

    assertTrue("Anfang gedämpft: ${quiet[0]}", quiet[0] < 0.1f)
    assertTrue("Ende erholt: ${quiet.last()}", quiet.last() > 0.099f)
    for (index in 1 until quiet.size) {
      val step = abs(quiet[index] - quiet[index - 1])
      assertTrue("Sprung bei $index: $step", step < 0.05f)
    }
  }

  @Test
  fun `null-signal bleibt exakt null - kein NaN aus dem Gedaechtnis`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 2) }
    val loud = FloatArray(256 * 2) { 2f }
    limiter.process(loud)

    val silence = FloatArray(256 * 2)
    limiter.process(silence)

    silence.forEach { assertEquals(0f, it, 0f) }
  }

  @Test
  fun `reset macht den limiter wieder ungedaempft`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 1) }
    limiter.process(FloatArray(64) { 2f })

    limiter.reset()

    val buffer = FloatArray(64) { 0.5f }
    limiter.process(buffer)

    buffer.forEach { assertEquals(0.5f, it, 1e-6f) }
  }

  @Test
  fun `signale unter der decke bleiben bitgenau`() {
    val limiter = Limiter().apply { configure(sampleRateHz, 2) }
    val buffer = FloatArray(1_024 * 2) { index -> (index % 100) / 200f - 0.25f }
    val expected = buffer.copyOf()

    limiter.process(buffer)

    assertTrue(buffer.contentEquals(expected))
  }

  @Test
  fun `konfiguration mit unsinnigen werten wirft - der audio-pfad prueft vorher`() {
    val limiter = Limiter()
    var threw = false
    try {
      limiter.configure(0.0, 2)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }

  private companion object {
    /** 300 ms bei 48 kHz – sechs Release-Zeitkonstanten à 50 ms. */
    const val RELEASE_CHECK_SAMPLES = 14_400
  }
}
