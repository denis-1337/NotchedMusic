package de.notchedmusic.core.dsp

import de.notchedmusic.core.model.DefaultPresets
import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterBand
import de.notchedmusic.core.model.FilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class EqChainTest {

  @Test
  fun bypassLaesstSignalUnveraendert() {
    val preset = DefaultPresets.three().first()
    val chain = EqChain()
    chain.configure(48_000.0, 2, preset, bypass = true)
    val input = sine(1_000.0, 0.4, 48_000, 4_800)
    val buffer = input.copyOf()
    chain.process(buffer)
    assertCloseArrays(input, buffer, tolerance = 1e-4f)
  }

  @Test
  fun notchImPresetSenktMittenfrequenz() {
    val notch = EqPreset(
      id = "p1",
      name = "Test",
      bands = listOf(FilterBand(FilterType.NOTCH, 8_000.0, 8.0, -20.0, true)),
    )
    assertTrue("Notch muss 8 kHz stark senken", attenuationAt(notch, 8_000.0) < 0.15)
    assertTrue("1 kHz bleibt erhalten", attenuationAt(notch, 1_000.0) > 0.9)
  }

  @Test
  fun vorVerstaerkungWirdImBypassUebersprungen() {
    val preset = EqPreset("p1", "Test", preGainDb = -6.0, bands = emptyList())
    val activeChain = EqChain()
    activeChain.configure(48_000.0, 1, preset, bypass = false)
    val bypassChain = EqChain()
    bypassChain.configure(48_000.0, 1, preset, bypass = true)

    val input = sine(1_000.0, 0.25, 48_000, 4_800)
    val activeBuffer = input.copyOf()
    val bypassBuffer = input.copyOf()
    activeChain.process(activeBuffer)
    bypassChain.process(bypassBuffer)

    val activeRms = rms(activeBuffer)
    val bypassRms = rms(bypassBuffer)
    assertEquals(0.5, activeRms / bypassRms, 0.02)
  }

  @Test
  fun lautstaerkeWirktAuchImBypass() {
    val chain = EqChain()
    chain.configure(48_000.0, 1, EqPreset("p1", "Test"), bypass = true)
    chain.setVolumeDb(-6f)
    repeat(4) {
      val buffer = sine(1_000.0, 0.5, 48_000, 4_800)
      chain.process(buffer)
    }
    val buffer = sine(1_000.0, 0.5, 48_000, 4_800)
    chain.process(buffer)
    assertEquals(0.5 / kotlin.math.sqrt(2.0) * 10.0.pow(-6.0 / 20.0), rms(buffer), 0.01)
  }

  @Test
  fun limiterIstAuchImBypassAktiv() {
    val chain = EqChain()
    chain.configure(48_000.0, 2, EqPreset("p1", "Test"), bypass = true)
    val buffer = FloatArray(2_000) { 2.0f }
    chain.process(buffer)
    val ceiling = 10.0.pow(Limiter.CEILING_DB / 20.0).toFloat()
    for (value in buffer) {
      assertTrue("Bypass darf die Decke nicht überschreiten, war $value", abs(value) <= ceiling + 1e-6f)
    }
  }

  @Test
  fun pegelMessungLiegtVorDemLimiter() {
    val chain = EqChain()
    chain.configure(48_000.0, 1, EqPreset("p1", "Test"), bypass = true)
    chain.process(FloatArray(480) { 2.0f })
    assertTrue("Spitze vor dem Limiter muss 2.0 sein, war ${chain.lastPeakLinear}", chain.lastPeakLinear > 1.9f)
    assertTrue("Limiter muss eingegriffen haben, Gain = ${chain.lastLimiterGain}", chain.lastLimiterGain < 0.6f)
  }

  @Test
  fun kanalUndBandAnzahlBleibenGueltig() {
    val chain = EqChain()
    val mono = EqPreset("p1", "Test", bands = List(6) { FilterBand(FilterType.PEAKING, 1_000.0, 1.0, 3.0, true) })
    chain.configure(48_000.0, 1, mono, bypass = false)
    chain.process(FloatArray(480) { 0.1f })
    chain.configure(48_000.0, 2, EqPreset("p1", "Leer"), bypass = true)
    val stereo = FloatArray(960) { 0.1f }
    chain.process(stereo)
    for (value in stereo) assertTrue(value.isFinite())
  }

  private fun attenuationAt(preset: EqPreset, probeHz: Double): Double {
    val chain = EqChain()
    chain.configure(48_000.0, 1, preset, bypass = false)
    val input = sine(probeHz, 0.5, 48_000, 24_000)
    val output = input.copyOf()
    chain.process(output)
    return rms(output, 12_000) / rms(input, 12_000)
  }

  private fun sine(frequencyHz: Double, amplitude: Double, sampleRateHz: Int, count: Int): FloatArray =
    FloatArray(count) { index ->
      (amplitude * sin(2.0 * PI * frequencyHz * index / sampleRateHz)).toFloat()
    }

  private fun rms(samples: FloatArray, from: Int = 0): Double {
    var sum = 0.0
    for (index in from until samples.size) {
      val value = samples[index].toDouble()
      sum += value * value
    }
    return sqrt(sum / (samples.size - from))
  }

  private fun assertCloseArrays(expected: FloatArray, actual: FloatArray, tolerance: Float) {
    for (index in expected.indices) {
      assertEquals(expected[index], actual[index], tolerance)
    }
  }
}
