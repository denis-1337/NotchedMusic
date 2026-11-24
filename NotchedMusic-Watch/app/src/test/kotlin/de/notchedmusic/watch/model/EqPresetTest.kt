package de.notchedmusic.watch.model

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Uhr liest exakt das JSON, das die Phone-App schreibt. Die Grenzen sind mit der Phone-Core-
 * Klasse identisch – ein Rezept darf den Audio-Pfad nie in einen gefährlichen Zustand bringen.
 */
class EqPresetTest {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  @Test
  fun `preset ueberlebt den JSON-Roundtrip`() {
    val preset =
      EqPreset(
        id = "p1",
        name = "Preset 1",
        preGainDb = -6.0,
        bands =
          listOf(
            FilterBand(FilterType.NOTCH, 8_000.0, 8.0, -20.0, true),
            FilterBand(FilterType.LOW_SHELF, 90.0, 0.707, -3.0, false),
          ),
      )

    val encoded = json.encodeToString(EqPreset.serializer(), preset)

    assertEquals(preset, json.decodeFromString(EqPreset.serializer(), encoded))
  }

  @Test
  fun `alle sieben filtertypen sind lesbar`() {
    val types = FilterType.entries

    for (type in types) {
      val band = FilterBand(type = type, frequencyHz = 1_000.0, q = 1.0, gainDb = 2.0)
      val encoded = json.encodeToString(FilterBand.serializer(), band)
      assertEquals(band, json.decodeFromString(FilterBand.serializer(), encoded))
    }
    assertEquals(7, types.size)
  }

  @Test
  fun `band ueber Nyquist wird geklemmt statt zu fliegen`() {
    val preset = EqPreset(id = "p1", name = "Test", bands = listOf(FilterBand(frequencyHz = 30_000.0)))

    val band = preset.validate(sampleRateHz = 48_000.0).bands.first()

    assertEquals(EqPreset.MAX_FREQUENCY_HZ, band.frequencyHz, 1e-6)
  }

  @Test
  fun `zu tiefe frequenz wird auf die untere Grenze gezogen`() {
    val preset = EqPreset(id = "p1", name = "Test", bands = listOf(FilterBand(frequencyHz = 10.0)))

    assertEquals(EqPreset.MIN_FREQUENCY_HZ, preset.validate(48_000.0).bands.first().frequencyHz, 1e-6)
  }

  @Test
  fun `unsauberes Q und Gain werden beschnitten`() {
    val band =
      EqPreset(
          id = "p1",
          name = "Test",
          bands = listOf(FilterBand(FilterType.PEAKING, 5_000.0, 0.0001, -80.0)),
        )
        .validate(48_000.0)
        .bands
        .first()

    assertEquals(EqPreset.MIN_Q, band.q, 1e-6)
    assertEquals(-EqPreset.MAX_BAND_GAIN_DB, band.gainDb, 1e-6)
  }

  @Test
  fun `zu grosses Q wird auf dreissig gezogen`() {
    val band =
      EqPreset(
          id = "p1",
          name = "Test",
          bands = listOf(FilterBand(FilterType.PEAKING, 5_000.0, 99_999.0, -6.0)),
        )
        .validate(48_000.0)
        .bands
        .first()

    assertEquals(EqPreset.MAX_Q, band.q, 0.0)
  }

  @Test
  fun `sechs baender plus neutrale auffuellung - der Audio-pfad wirft nie`() {
    val bands =
      (0 until 10).map {
        FilterBand(type = FilterType.PEAKING, frequencyHz = 1_000.0 + it * 500.0, q = 4.0, gainDb = -3.0)
      }

    val validated = EqPreset(id = "p1", name = "Test", bands = bands).validate(48_000.0)

    assertEquals(EqPreset.MAX_BANDS, validated.normalizedBands().size)
    assertEquals(EqPreset.MAX_BANDS, validated.bands.size)
  }

  @Test
  fun `summen-anhebung wird anteilig auf zwoelf db begrenzt`() {
    val bands =
      (0 until 4).map {
        FilterBand(type = FilterType.PEAKING, frequencyHz = 1_000.0 + it * 1_000.0, q = 4.0, gainDb = 12.0)
      }

    val validated = EqPreset(id = "p1", name = "Test", bands = bands).validate(48_000.0)

    val positive = validated.bands.sumOf { if (it.gainDb > 0.0) it.gainDb else 0.0 }
    assertEquals(EqPreset.MAX_TOTAL_BOOST_DB, positive, 1e-6)
    assertEquals(4, validated.bands.count { it.gainDb > 0.0 })
  }

  @Test
  fun `vor-verstaerkung wird auf zwoelf db begrenzt`() {
    val high = EqPreset(id = "p1", name = "Test", preGainDb = 999.0).validate(48_000.0)
    val low = EqPreset(id = "p1", name = "Test", preGainDb = -999.0).validate(48_000.0)

    assertEquals(EqPreset.MAX_PRE_GAIN_DB, high.preGainDb, 0.0)
    assertEquals(-EqPreset.MAX_PRE_GAIN_DB, low.preGainDb, 0.0)
  }

  @Test
  fun `ausgeschaltetes band zaehlt trotzdem als vorhanden - aber nicht als aktiv`() {
    val preset =
      EqPreset(
        id = "p1",
        name = "Test",
        bands = listOf(FilterBand(type = FilterType.NOTCH, frequencyHz = 5_000.0, enabled = false)),
      )

    assertTrue(preset.activeBands().isEmpty())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `sample-rate null ist kein Zielwert`() {
    EqPreset(id = "p1", name = "Test").validate(sampleRateHz = 0.0)
  }

  @Test(expected = SerializationException::class)
  fun `kaputtes JSON wird erkannt`() {
    json.decodeFromString(EqPreset.serializer(), """{"id":"p1","name":"Test","bands":"kaputt"}""")
  }
}
