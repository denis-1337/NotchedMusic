package de.notchedmusic.core.protocol

import de.notchedmusic.core.model.DefaultPresets
import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterBand
import de.notchedmusic.core.model.FilterType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncProtocolTest {

  @Test
  fun roundtripErhaeltDreiPresets() {
    val payload =
      PresetSyncPayload(
        version = SyncProtocol.VERSION,
        presets = DefaultPresets.three(),
        activePresetId = "p2",
      )
    val text = SyncProtocol.encode(payload)
    val decoded = SyncProtocol.decode(text)
    assertEquals(payload, decoded)
  }

  @Test
  fun unbekannteFelderWerdenIgnoriert() {
    val text =
      """
      {"version":3,"presets":[],"activePresetId":"p1","zukunft":{"neu":true},"extra":42}
      """.trimIndent()
    val decoded = SyncProtocol.decode(text)
    assertTrue(decoded != null)
  }

  @Test
  fun muellWirdZuNull() {
    assertNull(SyncProtocol.decode("kein json"))
    assertNull(SyncProtocol.decode(""))
    assertNull(SyncProtocol.decode("{\"version\":\"drei\"}"))
  }

  @Test
  fun zuGrossesPaketWirdAbgelehnt() {
    val text = "{\"presets\":[]}" + " ".repeat(SyncProtocol.MAX_JSON_BYTES)
    assertNull(SyncProtocol.decode(text))
  }

  @Test
  fun validateFuelltAufDreiPresetsAuf() {
    val payload = PresetSyncPayload(presets = listOf(DefaultPresets.three().first()), activePresetId = "unbekannt")
    val validated = SyncProtocol.validate(payload)
    assertEquals(EqPreset.MAX_PRESETS, validated.presets.size)
    assertEquals("p1", validated.activePresetId)
  }

  @Test
  fun validateKlemmtFrequenzen() {
    val preset =
      EqPreset(
        "p1",
        "Test",
        bands = listOf(FilterBand(FilterType.PEAKING, 99_999.0, 99.0, 99.0)),
      )
    val validated = SyncProtocol.validate(PresetSyncPayload(presets = listOf(preset)), sampleRateHz = 48_000.0).presets.first()
    assertEquals(EqPreset.MAX_FREQUENCY_HZ, validated.bands[0].frequencyHz, 1e-9)
    assertEquals(EqPreset.MAX_Q, validated.bands[0].q)
  }

  @Test
  fun versionWirdAufDreiGesetzt() {
    val decoded = SyncProtocol.decode("""{"version":2,"presets":[]}""")
    val validated = SyncProtocol.validate(decoded!!)
    assertEquals(SyncProtocol.VERSION, validated.version)
  }

  @Test
  fun alteDatenOhneFelderBleibenLesbar() {
    val decoded = SyncProtocol.decode("""{"presets":[{"id":"p1","name":"Alt"}]}""")
    assertTrue(decoded != null)
    val validated = SyncProtocol.validate(decoded)
    assertEquals(3, validated.presets.size)
    assertEquals(6, validated.presets[0].bands.size)
  }
}
