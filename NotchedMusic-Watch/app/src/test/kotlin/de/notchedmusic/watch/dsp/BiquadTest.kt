package de.notchedmusic.watch.dsp

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Misst die Filterwirkung auf dem Sinus: Ein Sinus geht hinein, verglichen wird die Energie
 * dahinter – das ist die Dämpfung in dB, so wie sie später auf dem Kopfhörer ankommt.
 *
 * Geprüft werden alle vier RBJ-Typen: Peaking und Notch um die Mittenfrequenz herum sowie die
 * beiden Shelves an ihren Plateaus.
 */
class BiquadTest {

  @Test
  fun `peaking setzt bei der mittenfrequenz um die eingestellte tiefe ab`() {
    for (sampleRateHz in SAMPLE_RATES) {
      val biquad = configuredBiquad(sampleRateHz, FilterType.PEAKING)

      assertEquals(
        "Sample-Rate $sampleRateHz Hz",
        DEPTH_DB,
        attenuationDb(biquad, sampleRateHz, FREQUENCY_HZ),
        TOLERANCE_DB,
      )
    }
  }

  @Test
  fun `peaking laesst eine oktave darunter und darueber nahezu unveraendert`() {
    for (sampleRateHz in SAMPLE_RATES) {
      val biquad = configuredBiquad(sampleRateHz, FilterType.PEAKING)

      assertEquals(0.0, attenuationDb(biquad, sampleRateHz, FREQUENCY_HZ / 2), TOLERANCE_DB)
      assertEquals(0.0, attenuationDb(biquad, sampleRateHz, FREQUENCY_HZ * 2), TOLERANCE_DB)
    }
  }

  @Test
  fun `der notch sitzt bei 44100 und bei 48000 Hz auf derselben frequenz`() {
    val deepestPerSampleRate =
      SAMPLE_RATES.map { sampleRateHz -> deepestDipHz(sampleRateHz, FilterType.PEAKING) }

    for ((index, sampleRateHz) in SAMPLE_RATES.withIndex()) {
      assertEquals(
        "Tiefster Punkt bei $sampleRateHz Hz",
        FREQUENCY_HZ,
        deepestPerSampleRate[index],
        BANDWIDTH_HZ / 2,
      )
    }
    assertEquals(deepestPerSampleRate[0], deepestPerSampleRate[1], BANDWIDTH_HZ / 4)
  }

  @Test
  fun `gainDb wirkt ohne neukonfiguration - und blendet weich nach`() {
    val biquad = configuredBiquad(SAMPLE_RATES[1], FilterType.PEAKING)
    assertEquals(DEPTH_DB, attenuationDb(biquad, SAMPLE_RATES[1], FREQUENCY_HZ), TOLERANCE_DB)

    biquad.retarget(SAMPLE_RATES[1], FREQUENCY_HZ, Q, -6.0, FilterType.PEAKING)

    assertEquals(-6.0, attenuationDb(biquad, SAMPLE_RATES[1], FREQUENCY_HZ), TOLERANCE_DB)
  }

  @Test
  fun `NOTCH-Typ loescht bei der mittenfrequenz fast vollstaendig`() {
    val biquad =
      Biquad(FilterType.NOTCH).apply {
        configure(SAMPLE_RATES[1], FREQUENCY_HZ, Q, GAIN_DB_UNUSED)
      }

    assertTrue(attenuationDb(biquad, SAMPLE_RATES[1], FREQUENCY_HZ) < -40.0)
    assertEquals(0.0, attenuationDb(biquad, SAMPLE_RATES[1], FREQUENCY_HZ / 2), TOLERANCE_DB)
  }

  @Test
  fun `low-shelf hebt tiefen an und laesst hoehen unberuehrt`() {
    val biquad = Biquad(FilterType.LOW_SHELF)
    biquad.configure(SAMPLE_RATES[1], SHELF_FREQUENCY_HZ, SHELF_Q, SHELF_GAIN_DB)

    assertEquals(
      SHELF_GAIN_DB,
      attenuationDb(biquad, SAMPLE_RATES[1], SHELF_FREQUENCY_HZ / 4),
      SHELF_TOLERANCE_DB,
    )
    assertEquals(
      0.0,
      attenuationDb(biquad, SAMPLE_RATES[1], SHELF_FREQUENCY_HZ * 16),
      SHELF_TOLERANCE_DB,
    )
  }

  @Test
  fun `high-shelf hebt hoehen an und laesst tiefen unberuehrt`() {
    val biquad = Biquad(FilterType.HIGH_SHELF)
    biquad.configure(SAMPLE_RATES[1], HIGH_SHELF_FREQUENCY_HZ, SHELF_Q, SHELF_GAIN_DB)

    assertEquals(
      SHELF_GAIN_DB,
      attenuationDb(biquad, SAMPLE_RATES[1], HIGH_SHELF_FREQUENCY_HZ * 2.5),
      SHELF_TOLERANCE_DB,
    )
    assertEquals(
      0.0,
      attenuationDb(biquad, SAMPLE_RATES[1], HIGH_SHELF_FREQUENCY_HZ / 10),
      SHELF_TOLERANCE_DB,
    )
  }

  @Test
  fun `shelf mit 0 db ist ein durchlass`() {
    val biquad = Biquad(FilterType.LOW_SHELF)
    biquad.configure(SAMPLE_RATES[1], SHELF_FREQUENCY_HZ, SHELF_Q, 0.0)

    for (frequencyHz in listOf(30.0, 300.0, 3_000.0, 10_000.0)) {
      assertEquals("Frequenz $frequencyHz Hz", 0.0, attenuationDb(biquad, SAMPLE_RATES[1], frequencyHz), 0.1)
    }
  }

  @Test
  fun `reset beginnt wieder bei null`() {
    val biquad = configuredBiquad(SAMPLE_RATES[1], FilterType.PEAKING)
    repeat(64) { biquad.process(0.25f) }

    biquad.reset()

    assertEquals(0f, biquad.process(0f), 0f)
  }

  @Test
  fun `liefert endliche werte - auch am rand des zulaessigen bereichs`() {
    val biquad = Biquad()

    for (sampleRateHz in SAMPLE_RATES) {
      biquad.configure(
        sampleRateHz,
        EqPreset.MIN_FREQUENCY_HZ,
        EqPreset.MIN_Q,
        EqPreset.MAX_BAND_GAIN_DB,
        FilterType.LOW_SHELF,
      )
      repeat(4096) { index ->
        val value = biquad.process(if ((index % 2) == 0) 1f else -1f)
        assertTrue("index $index: $value", value.isFinite())
      }
    }
  }

  private fun configuredBiquad(sampleRateHz: Double, type: FilterType): Biquad =
    Biquad(type).apply { configure(sampleRateHz, FREQUENCY_HZ, Q, DEPTH_DB) }

  /**
   * Dämpfung in dB, die [biquad] bei [frequencyHz] bringt.
   *
   * Die erste Hälfte verwirft den Einschwingvorgang, gemessen wird erst danach.
   */
  private fun attenuationDb(biquad: Biquad, sampleRateHz: Double, frequencyHz: Double): Double {
    biquad.reset()
    val samples = ((sampleRateHz / frequencyHz) * CYCLES_MEASURED).toInt() + 1
    val settled = samples / 2
    var energyIn = 0.0
    var energyOut = 0.0
    for (index in 0 until samples) {
      val value = sin(2 * PI * frequencyHz * index / sampleRateHz)
      val filtered = biquad.process(value.toFloat()).toDouble()
      if (index >= settled) {
        energyIn += value * value
        energyOut += filtered * filtered
      }
    }
    return 10 * log10(energyOut / energyIn)
  }

  /** Frequenz mit der stärksten Absenkung im Bereich um [FREQUENCY_HZ]. */
  private fun deepestDipHz(sampleRateHz: Double, type: FilterType): Double {
    val biquad = configuredBiquad(sampleRateHz, type)
    var bestHz = FREQUENCY_HZ
    var bestDb = 0.0
    var frequencyHz = FREQUENCY_HZ - BANDWIDTH_HZ
    while (frequencyHz <= FREQUENCY_HZ + BANDWIDTH_HZ) {
      val attenuation = attenuationDb(biquad, sampleRateHz, frequencyHz)
      if (attenuation < bestDb) {
        bestDb = attenuation
        bestHz = frequencyHz
      }
      frequencyHz += SWEEP_STEP_HZ
    }
    return bestHz
  }

  private companion object {
    const val FREQUENCY_HZ = 8_000.0

    /** 8 kHz / Q 8 = 1 kHz breit – dieselbe Wirkung wie vorher mit Bandbreite. */
    const val Q = 8.0

    /** Erwartete −3-dB-Breite in Hz für den Frequenz-Abgleich: f/Q. */
    val BANDWIDTH_HZ = FREQUENCY_HZ / Q

    const val DEPTH_DB = -20.0
    const val GAIN_DB_UNUSED = 0.0

    /** 2 dB Toleranz: Die Messkette (endliche Sinuslänge, Float-Zustand) streut in dieser Größe. */
    const val TOLERANCE_DB = 2.0

    val SAMPLE_RATES = listOf(44_100.0, 48_000.0)

    // Shelf-Messungen: Plateau statt −3-dB-Punkt, deshalb ein eigener, großzügiger Rahmen.
    const val SHELF_FREQUENCY_HZ = 100.0
    const val HIGH_SHELF_FREQUENCY_HZ = 4_000.0
    const val SHELF_Q = 0.707
    const val SHELF_GAIN_DB = 12.0
    const val SHELF_TOLERANCE_DB = 2.5

    const val CYCLES_MEASURED = 240
    const val SWEEP_STEP_HZ = 25.0
  }
}
