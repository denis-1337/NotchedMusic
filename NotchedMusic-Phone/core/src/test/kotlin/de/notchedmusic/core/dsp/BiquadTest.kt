package de.notchedmusic.core.dsp

import de.notchedmusic.core.model.FilterType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiquadTest {

  @Test
  fun notchLoeschtDieMittenfrequenz() {
    val (response8k, response1k) =
      notchResponse(frequencyHz = 8_000.0, q = 8.0, probeHz = 8_000.0 to 1_000.0)
    assertTrue("Notch bei 8 kHz muss 8 kHz stark absenken, war $response8k", response8k < 0.1)
    assertTrue("1 kHz bleibt weitgehend erhalten, war $response1k", response1k > 0.9)
  }

  @Test
  fun notchBreiteSinktMitQ() {
    // 100 Hz neben der Mittenfrequenz: das breite Notch senkt dort stärker als das schmale.
    val breit = notchResponseAt(8_000.0, 2.0, 8_100.0)
    val schmal = notchResponseAt(8_000.0, 20.0, 8_100.0)
    assertTrue("Schmales Notch muss bei 8,1 kHz weniger senken: $schmal vs $breit", schmal > breit)
  }

  @Test
  fun peakBellHebtUmDenGainAn() {
    val gain = 6.0
    val response = sineResponse(
      FilterType.PEAKING,
      frequencyHz = 1_000.0,
      q = 2.0,
      gainDb = gain,
      probeHz = 1_000.0,
    )
    val expected = 10.0.pow(gain / 20.0)
    assertEquals(expected, response, 0.05)
  }

  @Test
  fun highShelfHebtHoheFrequenzenAn() {
    val low = sineResponse(FilterType.HIGH_SHELF, 1_000.0, 0.7, 6.0, 100.0)
    val high = sineResponse(FilterType.HIGH_SHELF, 1_000.0, 0.7, 6.0, 12_000.0)
    assertEquals(1.0, low, 0.05)
    assertEquals(10.0.pow(6.0 / 20.0), high, 0.1)
  }

  @Test
  fun lowShelfHebtTiefeFrequenzenAn() {
    val low = sineResponse(FilterType.LOW_SHELF, 1_000.0, 0.7, 6.0, 100.0)
    val high = sineResponse(FilterType.LOW_SHELF, 1_000.0, 0.7, 6.0, 12_000.0)
    assertEquals(10.0.pow(6.0 / 20.0), low, 0.1)
    assertEquals(1.0, high, 0.05)
  }

  @Test
  fun lowCutSenktTiefeFrequenzen() {
    val low = sineResponse(FilterType.LOW_CUT, 1_000.0, 0.707, 0.0, 100.0)
    val high = sineResponse(FilterType.LOW_CUT, 1_000.0, 0.707, 0.0, 5_000.0)
    assertTrue("Hochpass muss 100 Hz absenken, war $low", low < 0.1)
    assertTrue("Hochpass lässt 5 kHz durch, war $high", high > 0.95)
  }

  @Test
  fun highCutSenktHoheFrequenzen() {
    val low = sineResponse(FilterType.HIGH_CUT, 1_000.0, 0.707, 0.0, 100.0)
    val high = sineResponse(FilterType.HIGH_CUT, 1_000.0, 0.707, 0.0, 10_000.0)
    assertTrue("Tiefpass lässt 100 Hz durch, war $low", low > 0.95)
    assertTrue("Tiefpass muss 10 kHz absenken, war $high", high < 0.1)
  }

  @Test
  fun offLaesstSignalUnveraendert() {
    val filter = Biquad()
    filter.configure(48_000.0, 1_000.0, 1.0, type = FilterType.OFF)
    val input = sine(1_000.0, 0.5, 48_000, 200)
    val output = FloatArray(input.size) { index -> filter.process(input[index]) }
    for (index in input.indices) {
      assertEquals(input[index], output[index], 1e-6f)
    }
  }

  @Test
  fun hohesQBleibtStabil() {
    val filter = Biquad()
    filter.configure(48_000.0, 19_000.0, 30.0, 0.0, FilterType.PEAKING)
    val input = sine(19_000.0, 0.9, 48_000, 2_000)
    for (value in input) {
      val output = filter.process(value)
      assertTrue("Ausgabe muss endlich bleiben, war $output", output.isFinite())
      assertTrue("Verstärkung muss begrenzt bleiben, war $output", abs(output) < 10f)
    }
  }

  @Test
  fun retargetKnacktNicht() {
    val filter = Biquad()
    filter.configure(48_000.0, 1_000.0, 1.0, 0.0, FilterType.PEAKING)
    val first = sine(1_000.0, 0.5, 48_000, 500)
    for (value in first) filter.process(value)
    filter.retarget(48_000.0, 1_000.0, 4.0, 12.0, FilterType.PEAKING)

    var previous = filter.process(0.5f)
    repeat(2_000) {
      val current = filter.process(0.5f)
      assertTrue("Sprung zwischen Samples: $previous -> $current", abs(current - previous) < 0.5f)
      previous = current
    }
  }

  private fun notchResponse(frequencyHz: Double, q: Double, probeHz: Pair<Double, Double>): Pair<Double, Double> {
    val first = sineResponse(FilterType.NOTCH, frequencyHz, q, 0.0, probeHz.first)
    val second = sineResponse(FilterType.NOTCH, frequencyHz, q, 0.0, probeHz.second)
    return first to second
  }

  private fun notchResponseAt(frequencyHz: Double, q: Double, probeHz: Double): Double =
    sineResponse(FilterType.NOTCH, frequencyHz, q, 0.0, probeHz)

  private fun sineResponse(
    type: FilterType,
    frequencyHz: Double,
    q: Double,
    gainDb: Double,
    probeHz: Double,
  ): Double {
    val filter = Biquad()
    filter.configure(48_000.0, frequencyHz, q, gainDb, type)
    val sampleCount = 24_000
    val input = sine(probeHz, 0.5, 48_000, sampleCount)
    val output = FloatArray(sampleCount) { index -> filter.process(input[index]) }
    // Einschwingen überspringen: die zweite Hälfte ist eingeschwungen.
    return rms(output, sampleCount / 2) / rms(input, sampleCount / 2)
  }

  private fun sine(frequencyHz: Double, amplitude: Double, sampleRateHz: Int, count: Int): FloatArray =
    FloatArray(count) { index ->
      (amplitude * sin(2.0 * PI * frequencyHz * index / sampleRateHz)).toFloat()
    }

  private fun rms(samples: FloatArray, from: Int): Double {
    var sum = 0.0
    for (index in from until samples.size) {
      val value = samples[index].toDouble()
      sum += value * value
    }
    return sqrt(sum / (samples.size - from))
  }
}
