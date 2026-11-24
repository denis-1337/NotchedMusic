package de.notchedmusic.phone.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class LibraryModelTest {

  @Test
  fun mp3WirdErkannt() {
    assertTrue(TrackFormats.isSupported("01 - Lied.mp3", null))
    assertTrue(TrackFormats.isSupported("Lied.MP3", null))
    assertTrue(TrackFormats.isMp3("01 - Lied.mp3", null))
  }

  @Test
  fun alleGelistetenFormateWerdenErkannt() {
    listOf("m4a", "aac", "ogg", "oga", "opus", "flac", "wav").forEach { extension ->
      assertTrue(
        "Format $extension muss erkannt werden",
        TrackFormats.isSupported("datei.$extension", null),
      )
    }
  }

  @Test
  fun mimeTypAlleinReicht() {
    assertTrue(TrackFormats.isSupported("ohne_endung", "audio/flac"))
    assertTrue(TrackFormats.isSupported("ohne_endung", "audio/mpeg"))
  }

  @Test
  fun videosUndSystemdateienWerdenAbgelehnt() {
    assertFalse(TrackFormats.isSupported("film.mp4", "video/mp4"))
    assertFalse(TrackFormats.isSupported("cover.jpg", "image/jpeg"))
    assertFalse(TrackFormats.isSupported(".DS_Store", null))
  }

  @Test
  fun titelOhneEndung() {
    assertEquals("01 - Lied", TrackFormats.titleFromFileName("01 - Lied.mp3"))
    assertEquals("ohne", TrackFormats.titleFromFileName("ohne"))
  }

  @Test
  fun natuerlicheSortierungStelltZehnNachZwei() {
    val names = listOf("10 - zehn.mp3", "2 - zwei.mp3", "1 - eins.mp3", "20 - zwanzig.mp3")
    val sorted = names.sortedWith(NaturalOrder)
    assertEquals(listOf("1 - eins.mp3", "2 - zwei.mp3", "10 - zehn.mp3", "20 - zwanzig.mp3"), sorted)
  }

  @Test
  fun natuerlicheSortierungIgnoriertGrossKlein() {
    val names = listOf("b.mp3", "A.mp3", "c.mp3")
    assertEquals(listOf("A.mp3", "b.mp3", "c.mp3"), names.sortedWith(NaturalOrder))
  }

  @Test
  fun fuehrendeNullenZaehlenNicht() {
    val names = listOf("010 - zehn.mp3", "9 - neun.mp3")
    assertEquals(listOf("9 - neun.mp3", "010 - zehn.mp3"), names.sortedWith(NaturalOrder))
  }

  @Test
  fun gleicherNameBleibtGleich() {
    assertEquals(0, NaturalOrder.compare("Album 1", "Album 1"))
  }
}
