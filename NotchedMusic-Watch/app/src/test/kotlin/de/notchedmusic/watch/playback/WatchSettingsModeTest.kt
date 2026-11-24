package de.notchedmusic.watch.playback

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.WatchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Modus-Knopf und Auswahl: P1 → P2 → P3 → Aus → P1. */
class WatchSettingsModeTest {

  @Test
  fun der_knopf_laeuft_p1_p2_p3_aus_und_wieder_p1() {
    var settings = WatchSettings()
    val seen = mutableListOf<String?>()

    repeat(4) {
      seen += settings.activePresetId
      settings = settings.cyclePreset()
    }

    assertEquals(
      listOf(DefaultPresets.PRESET_1_ID, DefaultPresets.PRESET_2_ID, DefaultPresets.PRESET_3_ID, null),
      seen,
    )
    assertEquals(DefaultPresets.PRESET_1_ID, settings.activePresetId)
  }

  @Test
  fun die_auswahl_laesst_die_uebrigen_werte_stehen() {
    val start = WatchSettings(volumeDb = -24f, keepScreenOn = true)

    val gewechselt = start.selectPreset(DefaultPresets.PRESET_2_ID)

    assertEquals(-24f, gewechselt.volumeDb, 0.001f)
    assertEquals(true, gewechselt.keepScreenOn)
    assertEquals(DefaultPresets.PRESET_2_ID, gewechselt.activePresetId)
  }

  @Test
  fun unbekannte_preset_id_schaltet_auf_aus() {
    val settings = WatchSettings().selectPreset("gibt-es-nicht")

    assertNull(settings.activePresetId)
  }
}
