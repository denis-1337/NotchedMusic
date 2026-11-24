package de.notchedmusic.watch.playback

import de.notchedmusic.watch.model.AmbientAnzeige
import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.model.WatchSettingsCodec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Speicherpfad ist hinter [FilterSettings.attachStore] versteckt – die Klasse bleibt auf dem
 * PC ohne Android testbar. Geprüft wird der Codec-Roundtrip und das Verhalten ohne Store.
 */
class FilterSettingsStoreTest {

  @After
  fun restoreDefaults() {
    FilterSettings.update { WatchSettings() }
  }

  @Test
  fun `profilstand ueberlebt den speicher-roundtrip`() {
    val customPreset =
      EqPreset(
        id = "p1",
        name = "Mein Preset",
        preGainDb = -3.0,
        bands =
          listOf(
            FilterBand(FilterType.PEAKING, 6_000.0, 8.0, -18.0),
            FilterBand(FilterType.LOW_SHELF, 90.0, 0.707, -3.0),
          ),
      )
    val settings =
      WatchSettings(
        presets = listOf(customPreset, DefaultPresets.three()[1], DefaultPresets.three()[2]),
        activePresetId = "p1",
        volumeDb = -17f,
        keepScreenOn = true,
        ambientDimmed = false,
        lastPresetUpdateMs = 1_700_000_000_000L,
        lastMusicUpdateMs = 1_700_000_100_000L,
        lastTrackPath = "/sdcard/Music/Morgen/01 - Aufwachen.mp3",
      )

    val stored = WatchSettingsCodec.encode(settings)
    val decoded = WatchSettingsCodec.decode(stored)

    assertEquals(settings.normalized(), decoded)
  }

  @Test
  fun `letzter titel ueberlebt den speicher-roundtrip`() {
    val settings = WatchSettings(lastTrackPath = "/sdcard/Music/Abend/07.mp3")
    val decoded = WatchSettingsCodec.decode(WatchSettingsCodec.encode(settings))
    assertEquals("/sdcard/Music/Abend/07.mp3", decoded?.lastTrackPath)
  }

  @Test
  fun `leerer titelpfad wird nicht gespeichert`() {
    val decoded = WatchSettingsCodec.decode("""{"version":2,"lastTrackPath":"  "}""")
    assertNull(decoded?.lastTrackPath)
  }

  @Test
  fun `aus ist kein eigenes preset - sondern aktiv gleich null`() {
    FilterSettings.update { it.selectPreset(null) }

    assertNull(FilterSettings.current().activePresetId)
    assertNull(FilterSettings.activePreset())
  }

  @Test
  fun `sechs baender bleiben erhalten`() {
    val bands =
      (1..EqPreset.MAX_BANDS).map {
        FilterBand(type = FilterType.PEAKING, frequencyHz = it * 1_000.0, q = 8.0, gainDb = -10.0)
      }

    FilterSettings.update {
      it.copy(presets = it.presets.mapIndexed { index, preset -> if (index == 0) preset.copy(bands = bands) else preset })
    }

    assertEquals(bands, FilterSettings.current().presets[0].bands)
  }

  @Test
  fun `p1 p2 und p3 teilen sich nichts`() {
    val onlyP1 = EqPreset(id = "p1", name = "Nur P1", bands = listOf(FilterBand(FilterType.NOTCH, 5_000.0)))

    FilterSettings.update {
      it.copy(presets = listOf(onlyP1, it.presets[1], it.presets[2]))
    }

    assertEquals(onlyP1, FilterSettings.current().presets[0])
    assertEquals(DefaultPresets.three()[1], FilterSettings.current().presets[1])
    assertEquals(DefaultPresets.three()[2], FilterSettings.current().presets[2])
  }

  @Test
  fun `lautstaerke wird auf geraete-grenzen geklemmt`() {
    FilterSettings.update { it.withVolumeDb(50f) }
    assertEquals(WatchSettings.MAX_VOLUME_DB, FilterSettings.current().volumeDb, 0f)

    FilterSettings.update { it.withVolumeDb(-500f) }
    assertEquals(WatchSettings.MIN_VOLUME_DB, FilterSettings.current().volumeDb, 0f)
  }

  @Test
  fun `anzeige-schalter aendern die presets nicht`() {
    val before = FilterSettings.current()

    FilterSettings.update { it.copy(volumeDb = -9f, keepScreenOn = true).withAmbientAnzeige(AmbientAnzeige.VOLL) }

    val after = FilterSettings.current()
    assertEquals(before.presets, after.presets)
    assertEquals(before.activePresetId, after.activePresetId)
    assertTrue(after.keepScreenOn)
    assertEquals(AmbientAnzeige.VOLL, after.ambientAnzeigeStufe())
    // Der alte Schalter bleibt als Spiegel widerpruchsfrei mitgezogen.
    assertFalse(after.ambientDimmed)
  }

  @Test
  fun `die schmale ambient-darstellung ist standardmaessig an`() {
    assertEquals(AmbientAnzeige.SCHMAL, WatchSettings().ambientAnzeigeStufe())
    assertTrue(WatchSettings().ambientDimmed)
  }

  @Test
  fun `presets von der phone-app lassen pegelschalter und auswahl stehen`() {
    FilterSettings.update { it.copy(volumeDb = -24f, keepScreenOn = true).withAmbientAnzeige(AmbientAnzeige.VOLL) }
    val incoming =
      EqPreset(id = "p1", name = "Neu", bands = listOf(FilterBand(FilterType.NOTCH, 7_300.0, 6.0, -18.0)))

    FilterSettings.update { it.withPresetsFrom(listOf(incoming), nowMs = 123L) }

    val after = FilterSettings.current()
    assertEquals("Neu", after.presets[0].name)
    assertEquals(-24f, after.volumeDb, 0f)
    assertTrue(after.keepScreenOn)
    assertFalse(after.ambientDimmed)
    assertEquals(123L, after.lastPresetUpdateMs)
  }

  @Test
  fun `unbekannte felder im JSON werden ignoriert`() {
    val decoded =
      WatchSettingsCodec.decode(
        """{"volumeDb":-6.0,"zukunftsfeld":true,"presets":[{"id":"p1","name":"P1","preGainDb":-3.0,"gainStaging":"neu"}]}"""
      )

    assertEquals(-6f, decoded?.volumeDb ?: 0f, 0f)
    assertEquals(-3.0, decoded?.presets?.first()?.preGainDb ?: 0.0, 0.0)
  }

  @Test
  fun `kaputtes JSON wird als null erkannt`() {
    assertNull(WatchSettingsCodec.decode("""{"presets":"kaputt"}"""))
    assertNull(WatchSettingsCodec.decode(null))
    assertNull(WatchSettingsCodec.decode(""))
  }

  @Test
  fun `alter a-b-stand migriert auf p1 p2 und p3`() {
    val legacy =
      """
      {
        "slotA": {"bands": [{"frequencyHz": 7300.0, "q": 8.0, "gainDb": -24.0, "type": "NOTCH"}]},
        "slotB": {"bands": []},
        "active": "B",
        "volumeDb": -12.0,
        "keepScreenOn": true,
        "ambientDimmed": false,
        "lastPhoneUpdateMs": 12345
      }
      """.trimIndent()

    val migrated = WatchSettingsCodec.migrateLegacy(legacy)

    assertEquals("p2", migrated?.activePresetId)
    assertEquals(-12f, migrated?.volumeDb ?: 0f, 0f)
    assertTrue(migrated?.keepScreenOn == true)
    assertFalse(migrated?.ambientDimmed == true)
    assertEquals(12_345L, migrated?.lastPresetUpdateMs)
    assertEquals("p1", migrated?.presets?.get(0)?.id)
    assertEquals(7_300.0, migrated?.presets?.get(0)?.bands?.single()?.frequencyHz ?: 0.0, 0.0)
  }
}
