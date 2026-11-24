package de.notchedmusic.core.model

import kotlin.test.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PresetValidationTest {

  @Test
  fun immerSechsBaenderNachNormalisierung() {
    val preset = EqPreset("p3", "Leer")
    assertEquals(EqPreset.MAX_BANDS, preset.normalizedBands().size)
    assertEquals(
      EqPreset.MAX_BANDS,
      EqPreset("x", "x", bands = List(2) { FilterBand() }).validate(48_000.0).bands.size,
    )
  }

  @Test
  fun ueberzaehligeBaenderFallenWeg() {
    val preset = EqPreset("x", "x", bands = List(10) { FilterBand(FilterType.PEAKING, 1_000.0, 1.0) })
    assertEquals(EqPreset.MAX_BANDS, preset.validate(48_000.0).bands.size)
  }

  @Test
  fun frequenzWirdGeklemmt() {
    val preset = EqPreset(
      "x",
      "x",
      bands = listOf(FilterBand(FilterType.PEAKING, 1.0, 1.0), FilterBand(FilterType.PEAKING, 50_000.0, 1.0)),
    )
    val bands = preset.validate(48_000.0).bands
    assertEquals(EqPreset.MIN_FREQUENCY_HZ, bands[0].frequencyHz, 1e-9)
    assertEquals(EqPreset.MAX_FREQUENCY_HZ, bands[1].frequencyHz, 1e-9)
  }

  @Test
  fun qUndGainWerdenGeklemmt() {
    val preset = EqPreset("x", "x", preGainDb = -99.0, bands = listOf(FilterBand(FilterType.PEAKING, 1_000.0, 99.0, 6.0)))
    val validated = preset.validate(48_000.0)
    assertEquals(EqPreset.MAX_Q, validated.bands[0].q, 1e-9)
    assertEquals(6.0, validated.bands[0].gainDb, 1e-9)
    assertEquals(-EqPreset.MAX_PRE_GAIN_DB, validated.preGainDb, 1e-9)
  }

  @Test
  fun summenAnhebungWirdBegrenzt() {
    val preset = EqPreset(
      "x",
      "x",
      bands = List(6) { FilterBand(FilterType.PEAKING, 1_000.0, 1.0, 20.0) },
    )
    val positive = preset.validate(48_000.0).bands.filter { it.enabled }.sumOf { maxOf(0.0, it.gainDb) }
    assertTrue("Summen-Anhebung $positive muss ≤ ${EqPreset.MAX_TOTAL_BOOST_DB} sein", positive <= EqPreset.MAX_TOTAL_BOOST_DB + 1e-9)
  }

  @Test
  fun abgesenkteBaenderHelfenNichtMit() {
    val preset = EqPreset(
      "x",
      "x",
      bands = listOf(FilterBand(FilterType.PEAKING, 1_000.0, 1.0, -24.0), FilterBand(FilterType.PEAKING, 2_000.0, 1.0, 12.0)),
    )
    val boost = preset.validate(48_000.0).bands.sumOf { maxOf(0.0, it.gainDb) }
    assertEquals(12.0, boost, 1e-9)
  }

  @Test
  fun activeBandsIgnoriertOffUndDisabled() {
    val preset = EqPreset(
      "x",
      "x",
      bands = listOf(
        FilterBand(FilterType.NOTCH, 1_000.0, 1.0),
        FilterBand(FilterType.PEAKING, 2_000.0, 1.0, 3.0, enabled = false),
        FilterBand(FilterType.OFF, 3_000.0, 1.0),
      ),
    )
    assertEquals(1, preset.activeBands().size)
  }

  @Test
  fun dreiStandardPresetsMitFestenIds() {
    val presets = DefaultPresets.three()
    assertEquals(3, presets.size)
    assertEquals(listOf("p1", "p2", "p3"), presets.map { it.id })
    assertEquals(FilterType.NOTCH, presets[0].normalizedBands()[4].type)
    assertEquals(0, presets[2].activeBands().size)
  }

  @Test
  fun resetLiestNeutraleWerteZurueck() {
    val preset = DefaultPresets.three().first()
    val reset = DefaultPresets.reset(preset)
    assertEquals(preset.id, reset.id)
    assertEquals(0.0, reset.preGainDb, 1e-9)
    assertTrue(reset.activeBands().isEmpty())
  }
}
