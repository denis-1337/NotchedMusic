package de.notchedmusic.core.dsp

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class LimiterTest {

  @Test
  fun deckeWirdNichtUeberschritten() {
    val limiter = Limiter()
    limiter.configure(48_000.0, 2)
    val buffer = FloatArray(2_048) { if (it % 2 == 0) 2.0f else -2.0f }
    limiter.process(buffer)
    val ceiling = 10.0.pow(Limiter.CEILING_DB / 20.0).toFloat()
    for (value in buffer) {
      assertTrue("Wert $value über der Decke $ceiling", abs(value) <= ceiling + 1e-6f)
    }
  }

  @Test
  fun stereoWirdGekoppelt() {
    val limiter = Limiter()
    limiter.configure(48_000.0, 2)
    val buffer = FloatArray(100) { index -> if (index % 2 == 0) 0.1f else 1.8f }
    limiter.process(buffer)
    // Beide Kanäle werden mit demselben Gain behandelt: Verhältnis bleibt erhalten.
    for (frame in 1 until 50) {
      val left = abs(buffer[frame * 2])
      val right = abs(buffer[frame * 2 + 1])
      if (right > 0f) {
        assertEquals(0.1f / 1.8f, left / right, 1e-4f)
      }
    }
  }

  @Test
  fun erholungLaeuftZurueck() {
    val limiter = Limiter()
    limiter.configure(48_000.0, 1)
    val loud = FloatArray(1_000) { 2.0f }
    limiter.process(loud)
    val quiet = FloatArray(48_000) { 0.1f }
    limiter.process(quiet)
    assertTrue("Limiter muss nach 1 s wieder ungedämpft sein, Gain = ${limiter.currentGain()}", limiter.currentGain() > 0.99f)
  }

  @Test
  fun leiserPegelBleibtUnveraendert() {
    val limiter = Limiter()
    limiter.configure(48_000.0, 2)
    val buffer = FloatArray(1_000) { 0.25f }
    limiter.process(buffer)
    for (value in buffer) {
      assertEquals(0.25f, value, 1e-6f)
    }
  }

  @Test
  fun resetMachtLimiterUngedaempft() {
    val limiter = Limiter()
    limiter.configure(48_000.0, 1)
    limiter.process(FloatArray(100) { 2.0f })
    limiter.reset()
    assertEquals(1f, limiter.currentGain(), 1e-6f)
  }

  @Test
  fun monoWirftBeiNullKanaelen() {
    val limiter = Limiter()
    var threw = false
    try {
      limiter.configure(48_000.0, 0)
    } catch (_: IllegalArgumentException) {
      threw = true
    }
    assertTrue(threw)
  }
}
