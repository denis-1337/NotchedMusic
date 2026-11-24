package de.notchedmusic.core.protocol

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Fristen dürfen nicht wieder zu kurzen Größen-Timeouts werden: eine Datei mit der erlaubten
 * Obergrenze muss in der Rückfall-Frist bequem Platz haben, und das Lebenszeichen muss die Uhr
 * deutlich öfter erreichen als ihre Frist abläuft.
 */
class SyncFristenTest {

  @Test
  fun die_Rueckfall_Frist_laesst_eine_grosse_Datei_in_Ruhe_durch() {
    // Durchsatz, den eine Maximal-Datei in der Frist braechen wuerde (Kilobyte pro Sekunde).
    val noetigeRate =
      SyncProtocol.MAX_MUSIC_FILE_BYTES.toDouble() * 1_000.0 / SyncFristen.DATEI_MS / 1_024.0

    // Real gemessen auf der Uhr: rund 120–160 kB/s. Die noetige Rate liegt weit darunter.
    assertTrue("noetige Rate: $noetigeRate kB/s", noetigeRate < 40.0)
  }

  @Test
  fun das_Lebenszeichen_kommt_deutlich_vor_der_Uhr_Frist() {
    assertTrue(SyncFristen.DATEI_MS >= 10 * SyncFristen.HALTEZEIT_MS)
    assertTrue(SyncFristen.HALTEZEIT_MS in 30_000L..300_000L)
  }

  @Test
  fun das_Lebenszeichen_liegt_unter_dem_Musik_Praefix_der_Uhr() =
    assertTrue(SyncFristen.HALTEZEICHEN_PFAD.startsWith("/music/"))

  @Test
  fun Presets_bleiben_bei_kurzer_Frist() {
    assertTrue(SyncFristen.PRESET_MS <= 60_000L)
    assertTrue(SyncFristen.MANIFEST_MS >= SyncFristen.PRESET_MS)
    assertTrue(SyncFristen.COMMIT_MS >= SyncFristen.PRESET_MS)
  }
}
