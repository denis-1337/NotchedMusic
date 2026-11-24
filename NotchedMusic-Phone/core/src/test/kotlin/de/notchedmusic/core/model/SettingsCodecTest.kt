package de.notchedmusic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsCodecTest {

  @Test
  fun roundtripErhaeltAlleWerte() {
    val settings =
      AppSettings(
        rootUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
        activePresetId = DefaultPresets.PRESET_2_ID,
        eqBypass = false,
        playerVolumeDb = -9f,
        autoAdvance = false,
        repeatMode = RepeatMode.ONE,
        sineFrequencyHz = 6_000,
        sineVolumeDb = -30f,
        lastSyncMs = 123L,
        lastFolderKey = "Album A",
        lastTrackDocumentId = "doc-42",
      )
    val decoded = SettingsCodec.decode(SettingsCodec.encode(settings))
    assertEquals(settings, decoded)
  }

  @Test
  fun kaputtesJsonFaelltAufDefaultsZurueck() {
    assertEquals(AppSettings(), SettingsCodec.decode("kaputt"))
    assertEquals(AppSettings(), SettingsCodec.decode(null))
    assertEquals(AppSettings(), SettingsCodec.decode(""))
  }

  @Test
  fun unbekannteFelderWerdenIgnoriert() {
    val text =
      """{"version":1,"rootUri":null,"zukunft":"egal","presets":[]}"""
    val decoded = SettingsCodec.decode(text)
    assertEquals(3, decoded.presets.size)
  }

  @Test
  fun werteWerdenGeklemmt() {
    val text =
      """{"version":1,"playerVolumeDb":-999,"sineFrequencyHz":99999,"sineVolumeDb":10}"""
    val decoded = SettingsCodec.decode(text)
    assertEquals(-60f, decoded.playerVolumeDb)
    assertEquals(15_000, decoded.sineFrequencyHz)
    assertEquals(0f, decoded.sineVolumeDb)
  }

  @Test
  fun dreiPresetsBleibenImmerVollstaendig() {
    val decoded = SettingsCodec.decode("""{"version":1,"presets":[]}""")
    assertEquals(3, decoded.presets.size)
    assertEquals(listOf("p1", "p2", "p3"), decoded.presets.map { it.id })
    assertEquals(DefaultPresets.PRESET_1_ID, decoded.activePresetId)
  }

  @Test
  fun unbekannteAktiveIdFaelltAufErstesPreset() {
    val decoded = SettingsCodec.decode("""{"version":1,"activePresetId":"gibtsnicht"}""")
    assertEquals("p1", decoded.activePresetId)
    assertTrue(decoded.activePreset().id == "p1")
  }

  @Test
  fun letzteWahlWirdGehalten() {
    val settings = AppSettings(lastFolderKey = "Album B", lastTrackDocumentId = "doc-7")
    val decoded = SettingsCodec.decode(SettingsCodec.encode(settings))
    assertEquals("Album B", decoded.lastFolderKey)
    assertEquals("doc-7", decoded.lastTrackDocumentId)
  }

  @Test
  fun alterStandOhnePlayerWahlBleibtLesbar() {
    val decoded = SettingsCodec.decode("""{"version":1,"rootUri":"content://musik"}""")
    assertEquals(null, decoded.lastFolderKey)
    assertEquals(null, decoded.lastTrackDocumentId)
  }

  @Test
  fun withPresetErsetztOhneAnzahlZuAendern() {
    val settings = AppSettings()
    val updated = settings.activePreset().copy(name = "Mein Preset")
    val result = settings.withPreset(updated)
    assertEquals(3, result.presets.size)
    assertEquals("Mein Preset", result.activePreset().name)
  }
}
