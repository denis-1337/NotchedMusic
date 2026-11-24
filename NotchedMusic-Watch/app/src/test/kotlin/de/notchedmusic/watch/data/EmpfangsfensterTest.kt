package de.notchedmusic.watch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Empfangsschalter: Die Data-Layer-Empfänger sollen nur an sein, wenn die App offen ist oder
 * gerade Daten eintreffen – und danach wieder ausgehen.
 */
class EmpfangsfensterTest {

  private var jetzt = 0L

  private fun fenster(nachlaufMs: Long = NACHLAUF) =
    Empfangsfenster(jetztMs = { jetzt }, nachlaufMs = nachlaufMs)

  @Test
  fun beim_prozessstart_ist_der_empfang_zu() {
    val schalter = fenster()

    assertFalse(schalter.empfangAn)
    // Nichts einzuschalten heißt auch: nichts muss umgeschrieben werden.
    assertFalse(schalter.zwingeAus())
  }

  @Test
  fun eine_sichtbare_activity_schaltet_den_empfang_an() {
    val schalter = fenster()

    assertTrue(schalter.aktivitaetSichtbar())
    assertTrue(schalter.empfangAn)

    // Solange eine Activity sichtbar ist, tut keine Frist etwas.
    assertFalse(schalter.pruefeFrist(zeitMs = 10_000_000L))
    assertTrue(schalter.empfangAn)
  }

  @Test
  fun erst_die_letzte_activity_setzt_die_frist() {
    val schalter = fenster()
    assertTrue(schalter.aktivitaetSichtbar())
    assertFalse(schalter.aktivitaetSichtbar())

    jetzt = 1_000L
    assertNull(schalter.aktivitaetVersteckt())
    assertTrue(schalter.empfangAn)

    jetzt = 2_000L
    assertEquals(2_000L + NACHLAUF, schalter.aktivitaetVersteckt())
    assertTrue(schalter.empfangAn)
  }

  @Test
  fun nach_der_frist_ohne_alles_geht_der_empfang_aus() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    val frist = schalter.aktivitaetVersteckt()!!

    assertFalse(schalter.pruefeFrist(zeitMs = frist - 1L))
    assertTrue(schalter.empfangAn)

    assertTrue(schalter.pruefeFrist(zeitMs = frist))
    assertFalse(schalter.empfangAn)

    // Ein zweiter Rückruf derselben Frist findet nichts mehr vor.
    assertFalse(schalter.pruefeFrist(zeitMs = frist + 1L))
  }

  @Test
  fun eingehende_daten_ohne_offene_app_schieben_die_frist_nach_vorn() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    val ersteFrist = schalter.aktivitaetVersteckt()!!

    jetzt = ersteFrist - 10_000L
    val zweiteFrist = schalter.datenEingegangen()!!
    assertEquals(jetzt + NACHLAUF, zweiteFrist)

    // Der alte Rückruf darf nichts abschalten.
    assertFalse(schalter.pruefeFrist(zeitMs = ersteFrist))
    assertTrue(schalter.empfangAn)

    assertTrue(schalter.pruefeFrist(zeitMs = zweiteFrist))
    assertFalse(schalter.empfangAn)
  }

  @Test
  fun daten_nach_der_pause_schalten_den_empfang_wieder_an() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    val frist = schalter.aktivitaetVersteckt()!!
    assertTrue(schalter.pruefeFrist(zeitMs = frist))
    assertFalse(schalter.empfangAn)

    jetzt = 200_000L
    assertEquals(200_000L + NACHLAUF, schalter.datenEingegangen())
    assertTrue(schalter.empfangAn)
  }

  @Test
  fun daten_waehrend_die_app_offen_ist_brauchen_keine_frist() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()

    assertNull(schalter.datenEingegangen())
    assertTrue(schalter.empfangAn)
    assertFalse(schalter.pruefeFrist(zeitMs = 999_999_999L))
  }

  @Test
  fun eine_neue_activity_vor_der_frist_haelt_den_empfang_an() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    val frist = schalter.aktivitaetVersteckt()!!

    jetzt = frist - 1_000L
    // Die Empfaenger laufen schon - es ist nichts umzuschalten, aber die Frist ist verworfen.
    assertFalse(schalter.aktivitaetSichtbar())
    assertTrue(schalter.empfangAn)

    assertFalse(schalter.pruefeFrist(zeitMs = frist + 10_000L))
    assertTrue(schalter.empfangAn)
  }

  @Test
  fun eine_grosse_datei_ueberlebt_mit_der_sync_frist() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    schalter.aktivitaetVersteckt()

    jetzt = 10_000L
    val frist = schalter.datenEingegangen(Empfangsfenster.NACHLAUF_SYNC_MS)!!
    assertEquals(10_000L + Empfangsfenster.NACHLAUF_SYNC_MS, frist)

    // Nach 9 Minuten ist nichts Neues angekommen – der Lauscher bleibt trotzdem offen.
    assertFalse(schalter.pruefeFrist(zeitMs = 10_000L + 9 * 60_000L))
    assertTrue(schalter.empfangAn)

    assertTrue(schalter.pruefeFrist(zeitMs = frist))
    assertFalse(schalter.empfangAn)
  }

  @Test
  fun das_ende_der_sitzung_kuerzt_eine_lange_frist() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    schalter.aktivitaetVersteckt()
    jetzt = 0L
    schalter.datenEingegangen(Empfangsfenster.NACHLAUF_SYNC_MS)

    // Commit: die kurze Frist gilt ab jetzt.
    val frist = schalter.datenEingegangen(Empfangsfenster.NACHLAUF_MS)!!
    assertEquals(Empfangsfenster.NACHLAUF_MS, frist)
    assertTrue(schalter.empfangAn)
    assertTrue(schalter.pruefeFrist(zeitMs = frist))
    assertFalse(schalter.empfangAn)
  }

  @Test
  fun ein_Lebenszeichen_schiebt_die_Sitzungs_Frist_wieder_hoch() {
    val schalter = fenster()
    schalter.aktivitaetSichtbar()
    schalter.aktivitaetVersteckt()

    jetzt = 0L
    schalter.datenEingegangen(Empfangsfenster.NACHLAUF_SYNC_MS)

    // Das Phone meldet sich alle zwei Minuten – die Frist wandert jedes Mal weiter.
    for (minute in 1..20) {
      jetzt = minute * 60_000L
      assertFalse(schalter.pruefeFrist(jetzt))
      assertTrue(schalter.empfangAn)
      schalter.datenEingegangen(Empfangsfenster.NACHLAUF_SYNC_MS)
    }

    // Erst als das Phone schweigt, laeuft die Frist aus.
    jetzt = 20 * 60_000L + Empfangsfenster.NACHLAUF_SYNC_MS - 1L
    assertFalse(schalter.pruefeFrist(jetzt))
    assertTrue(schalter.pruefeFrist(zeitMs = jetzt + 1L))
    assertFalse(schalter.empfangAn)
  }

  @Test
  fun die_Sitzungs_Frist_laesst_genug_Platz_fuer_eine_grosse_Datei() {
    // Die Frist muss viel groesser sein als der Abstand der Lebenszeichen – und groesser als die
    // langsamste denkbare Datei (40 MB bei rund 120 kB/s = knapp sechs Minuten).
    assertTrue(Empfangsfenster.NACHLAUF_SYNC_MS >= 15 * Empfangsfenster.HALTEZEIT_MS)
    assertTrue(Empfangsfenster.NACHLAUF_SYNC_MS > 10 * 60_000L)
  }

  @Test
  fun das_Lebenszeichen_liegt_im_anmelde_Praefix_des_Musik_Empfaengers() {
    // Die Uhr-App meldet in ihrem Manifest nur "/music" und "/sync" als Nachrichtenpraefixe.
    // Das Lebenszeichen muss unter einem davon liegen, sonst weckt es den Empfaenger nicht.
    assertTrue(MusicTransferReceiver.PATH_KEEPALIVE.startsWith("/music"))
  }

  private companion object {
    const val NACHLAUF = 90_000L
  }
}
