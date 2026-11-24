package de.notchedmusic.watch.model

import de.notchedmusic.watch.ui.zeigtReduziert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ambient-Anzeige: drei Stufen, Migration aus dem alten boolean-Schalter, Zuordnung zur UI. */
class AmbientAnzeigeTest {

  @Test
  fun der_knopf_laeuft_schmal_voll_aus_und_wieder_schmal() {
    assertEquals(AmbientAnzeige.VOLL, AmbientAnzeige.SCHMAL.naechste())
    assertEquals(AmbientAnzeige.AUS, AmbientAnzeige.VOLL.naechste())
    assertEquals(AmbientAnzeige.SCHMAL, AmbientAnzeige.AUS.naechste())
  }

  @Test
  fun der_altbestand_ohne_neuen_wert_entspricht_dem_schalter() {
    assertEquals(AmbientAnzeige.SCHMAL, WatchSettings(ambientDimmed = true).ambientAnzeigeStufe())
    assertEquals(AmbientAnzeige.VOLL, WatchSettings(ambientDimmed = false).ambientAnzeigeStufe())
  }

  @Test
  fun der_wechsel_laesst_die_uebrigen_werte_stehen() {
    val start = WatchSettings(volumeDb = -24f, keepScreenOn = true)

    val gewechselt = start.cycleAmbientAnzeige()

    assertEquals(AmbientAnzeige.VOLL, gewechselt.ambientAnzeigeStufe())
    assertEquals(-24f, gewechselt.volumeDb, 0.001f)
    assertTrue(gewechselt.keepScreenOn)
    assertEquals(start.presets, gewechselt.presets)
  }

  @Test
  fun die_stufe_ueberlebt_Speicherung_und_Normierung() {
    val gewuenscht = WatchSettings().withAmbientAnzeige(AmbientAnzeige.AUS)

    val gelesen = WatchSettingsCodec.decode(WatchSettingsCodec.encode(gewuenscht))

    assertNotNull(gelesen)
    assertEquals(AmbientAnzeige.AUS, gelesen!!.ambientAnzeigeStufe())
    assertEquals(AmbientAnzeige.AUS, gelesen.normalized().ambientAnzeigeStufe())
  }

  @Test
  fun altes_JSON_ohne_neues_Feld_wandelt_richtig() {
    val gelesen = WatchSettingsCodec.decode("""{"version":2,"ambientDimmed":false}""")

    assertNotNull(gelesen)
    assertEquals(AmbientAnzeige.VOLL, gelesen!!.ambientAnzeigeStufe())
    // Die Normierung schreibt den neuen Wert gleich mit hinein.
    assertEquals(AmbientAnzeige.VOLL, gelesen.ambientAnzeige)
  }

  @Test
  fun die_stufe_bestimmt_was_im_Ambient_gezeichnet_wird() {
    assertTrue(zeigtReduziert(AmbientAnzeige.SCHMAL, imAmbient = true))
    assertFalse(zeigtReduziert(AmbientAnzeige.VOLL, imAmbient = true))
    assertFalse(zeigtReduziert(AmbientAnzeige.AUS, imAmbient = true))
    assertFalse(zeigtReduziert(AmbientAnzeige.SCHMAL, imAmbient = false))
  }
}
