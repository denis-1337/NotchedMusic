package de.notchedmusic.watch.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Der Takt der Wiedergabestelle: hell voll, gedimmt langsam, unsichtbar gar nicht. */
class TaktPlanTest {

  @Test
  fun eine_sichtbare_helle_App_holt_voll() {
    assertEquals(Anzeigetakt.VOLL, TaktPlan.takt(sichtbar = true, reduziert = false))
  }

  @Test
  fun ein_gedimmter_Bildschirm_holt_reduziert() {
    assertEquals(Anzeigetakt.REDUZIERT, TaktPlan.takt(sichtbar = true, reduziert = true))
  }

  @Test
  fun eine_unsichtbare_App_holt_gar_nicht() {
    assertEquals(Anzeigetakt.PAUSIERT, TaktPlan.takt(sichtbar = false, reduziert = false))
    assertEquals(Anzeigetakt.PAUSIERT, TaktPlan.takt(sichtbar = false, reduziert = true))
  }

  @Test
  fun die_Abstaende_sind_gesetzt_und_reduziert_ist_langsamer() {
    assertEquals(TaktPlan.VOLL_MS, TaktPlan.intervallMs(Anzeigetakt.VOLL))
    assertEquals(TaktPlan.REDUZIERT_MS, TaktPlan.intervallMs(Anzeigetakt.REDUZIERT))
    assertNull(TaktPlan.intervallMs(Anzeigetakt.PAUSIERT))
    assertTrue(TaktPlan.REDUZIERT_MS > TaktPlan.VOLL_MS)
  }
}
