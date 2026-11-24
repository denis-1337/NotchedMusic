package de.notchedmusic.watch.ui.album

import de.notchedmusic.watch.data.Album
import de.notchedmusic.watch.data.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Der Ordner-Sprung per Halten: das Nachbaralbum oder nichts – nie ein Sprung ins Blaue. */
class AlbumJumpTest {

  private val alben =
    listOf(
      Album(name = "Morgen", tracks = listOf(Track("Aufwachen", "/musik/Morgen/01.mp3"))),
      Album(name = "Mittag", tracks = listOf(Track("Sonnenlicht", "/musik/Mittag/01.mp3"))),
      Album(name = "Abend", tracks = emptyList()),
    )

  @Test
  fun einmal_weiter_ist_das_naechste_album() {
    assertEquals("Mittag", nachbarAlbum(alben, aktuell = "Morgen", richtung = +1)?.name)
  }

  @Test
  fun einmal_zurueck_ist_das_vorige_album() {
    assertEquals("Morgen", nachbarAlbum(alben, aktuell = "Mittag", richtung = -1)?.name)
  }

  @Test
  fun vor_dem_ersten_album_gibt_es_kein_zurueck() {
    assertNull(nachbarAlbum(alben, aktuell = "Morgen", richtung = -1))
  }

  @Test
  fun hinter_dem_letzten_album_gibt_es_kein_weiter() {
    assertNull(nachbarAlbum(alben, aktuell = "Abend", richtung = +1))
  }

  @Test
  fun ohne_bekanntes_album_wird_nicht_gesprungen() {
    assertNull(nachbarAlbum(alben, aktuell = null, richtung = +1))
    assertNull(nachbarAlbum(alben, aktuell = "Unbekannt", richtung = -1))
  }

  @Test
  fun eine_leere_sammlung_hat_kein_nachbargebiet() {
    assertNull(nachbarAlbum(emptyList(), aktuell = "Morgen", richtung = +1))
  }

  @Test
  fun letzter_titel_findet_sein_album() {
    assertEquals("Mittag", albumMitTitel(alben, "/musik/Mittag/01.mp3")?.name)
  }

  @Test
  fun unbekannter_titel_hat_kein_album() {
    assertNull(albumMitTitel(alben, "/musik/Unbekannt/01.mp3"))
    assertNull(albumMitTitel(alben, null))
  }
}
