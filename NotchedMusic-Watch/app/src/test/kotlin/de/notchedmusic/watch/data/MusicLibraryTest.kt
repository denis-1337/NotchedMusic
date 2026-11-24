package de.notchedmusic.watch.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Reine JVM-Tests – laufen auf dem PC, ohne Emulator und ohne Uhr. */
class MusicLibraryTest {

  @get:Rule val folder = TemporaryFolder()

  @Test
  fun `unterordner sind alben - nicht titel`() {
    val album = folder.newFolder("Morgen")
    File(album, "01 - Erstes.mp3").createNewFile()
    File(album, "02 - Zweites.mp3").createNewFile()

    val albums = MusicLibrary(folder.root).albums()

    assertEquals(listOf("Morgen"), albums.map { it.name })
    assertEquals(listOf("Erstes", "Zweites"), albums.single().tracks.map { it.title })
  }

  /** Die Vorgabe war: Titel **rekursiv** einlesen. Früher prüfte dieser Test das Gegenteil. */
  @Test
  fun `titel werden rekursiv aus tieferen unterordnern gelesen`() {
    val album = folder.newFolder("Boxset")
    val cd1 = File(album, "CD 1").apply { mkdirs() }
    val cd2 = File(album, "CD 2").apply { mkdirs() }
    File(cd1, "a.mp3").createNewFile()
    File(cd2, "b.mp3").createNewFile()

    val tracks = MusicLibrary(folder.root).albums().single().tracks

    assertEquals(2, tracks.size)
    assertEquals(listOf("a", "b"), tracks.map { it.title })
  }

  @Test
  fun `mehrere alben entstehen aus mehreren ordnern - alphabetisch`() {
    folder.newFolder("zuletzt").also { File(it, "x.mp3").createNewFile() }
    folder.newFolder("zuerst").also { File(it, "y.mp3").createNewFile() }

    assertEquals(listOf("zuerst", "zuletzt"), MusicLibrary(folder.root).albums().map { it.name })
  }

  @Test
  fun `dateien direkt im root landen im album ohne album`() {
    folder.newFile("loses Lied.mp3")

    val albums = MusicLibrary(folder.root).albums()

    assertEquals(listOf(MusicLibrary.LOOSE_TRACKS_NAME), albums.map { it.name })
    assertEquals(listOf("loses Lied"), albums.single().tracks.map { it.title })
  }

  @Test
  fun `leere ordner sind keine alben`() {
    folder.newFolder("leer")

    assertEquals(emptyList<Album>(), MusicLibrary(folder.root).albums())
  }

  @Test
  fun `leerer ordner fuehrt zu leerer liste`() {
    assertEquals(emptyList<Album>(), MusicLibrary(folder.newFolder("music")).albums())
  }

  @Test
  fun `fehlt der ordner, gibt es eine leere liste statt eines fehlers`() {
    assertEquals(emptyList<Album>(), MusicLibrary(File(folder.root, "gibt-es-nicht")).albums())
  }

  @Test
  fun `nicht-musik-dateien werden ignoriert - auch mit grossen endungen`() {
    val album = folder.newFolder("Mix")
    File(album, "klassik.MP3").createNewFile()
    File(album, "Notiz.txt").createNewFile()
    File(album, "cover.jpg").createNewFile()

    assertEquals(listOf("klassik"), MusicLibrary(folder.root).albums().single().tracks.map { it.title })
  }

  @Test
  fun `ordner mit musik-endung wird nicht als titel gezaehlt`() {
    folder.newFolder("Entwurf.mp3")

    assertTrue(MusicLibrary(folder.root).albums().isEmpty())
  }

  @Test
  fun `nummerierung wird sortiert und aus dem titel entfernt`() {
    val album = folder.newFolder("Konzert")
    File(album, "10 - Zehn.mp3").createNewFile()
    File(album, "02 - Zwei.mp3").createNewFile()
    File(album, "01. Eins.mp3").createNewFile()
    File(album, "03_Zwei.mp3").createNewFile()

    val tracks = MusicLibrary(folder.root).albums().single().tracks

    assertEquals(listOf("Eins", "Zwei", "Zwei", "Zehn"), tracks.map { it.title })
    assertEquals(listOf(1, 2, 3, 10), tracks.map { it.sortIndex })
  }

  @Test
  fun `eine zahl ohne trenner bleibt teil des titels`() {
    val album = folder.newFolder("Party")
    File(album, "99 Luftballons.mp3").createNewFile()
    File(album, "1 Titel.mp3").createNewFile()

    val tracks = MusicLibrary(folder.root).albums().single().tracks

    assertEquals(listOf("1 Titel", "99 Luftballons"), tracks.map { it.title }.sorted())
    assertTrue(tracks.all { it.sortIndex == null })
  }

  @Test
  fun `versteckte dateien und ordner werden ignoriert`() {
    val album = folder.newFolder("Album")
    File(album, ".versteckt.mp3").createNewFile()
    File(album, "hörbar.mp3").createNewFile()
    folder.newFolder(".verstecktes album").also { File(it, "x.mp3").createNewFile() }

    val albums = MusicLibrary(folder.root).albums()

    assertEquals(listOf("Album"), albums.map { it.name })
    assertEquals(listOf("hörbar"), albums.single().tracks.map { it.title })
  }

  @Test
  fun `zu tiefe unterordner werden nicht mehr gelesen`() {
    val album = folder.newFolder("Tief")
    var dir = album
    repeat(MusicLibrary.MAX_DEPTH + 2) { depth ->
      dir = File(dir, "ebene $depth").apply { mkdirs() }
    }
    File(dir, "zu-tief.mp3").createNewFile()

    assertTrue(MusicLibrary(folder.root).albums().isEmpty())
  }

  @Test
  fun `genau sechs ebene tiefe wird noch gelesen`() {
    val album = folder.newFolder("Sechs")
    var dir = album
    repeat(MusicLibrary.MAX_DEPTH) { depth ->
      dir = File(dir, "ebene $depth").apply { mkdirs() }
    }
    File(dir, "noch-erreichbar.mp3").createNewFile()

    assertEquals(1, MusicLibrary(folder.root).albums().size)
  }

  @Test
  fun `leere datei mit musik-endung zaehlt als titel`() {
    val album = folder.newFolder("Halbfertig")
    File(album, "leer.mp3").createNewFile()

    assertEquals(listOf("leer"), MusicLibrary(folder.root).albums().single().tracks.map { it.title })
  }
}
