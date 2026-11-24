package de.notchedmusic.watch.data

import de.notchedmusic.watch.protocol.MusicManifestEntry
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Dateisicherheit des Musik-Spiegels: Pfade entschärfen, nur Audio löschen, nie außerhalb. */
class MusicMirrorTest {

  private lateinit var root: java.io.File

  @Before
  fun setUp() {
    root = Files.createTempDirectory("watch-mirror").toFile()
  }

  @After
  fun tearDown() {
    root.deleteRecursively()
  }

  @Test
  fun `trenner und ausbruchsversuche landen im dateinamen`() {
    val safe = MusicMirror.sanitizeSegment("../../etc/passwd")

    assertFalse(safe.contains('/'))
    assertFalse(safe.contains(".."))
  }

  @Test
  fun `fuehrende punkte werden entschaerft`() {
    assertFalse(MusicMirror.sanitizeSegment(".versteckt").startsWith('.'))
  }

  @Test
  fun `album und datei landen im direkten unterordner`() {
    val target = MusicMirror.targetFile(root, "AC/DC", "../01.mp3")

    assertEquals(root.absolutePath, target.parentFile?.parentFile?.absolutePath)
    assertEquals("AC_DC", target.parentFile?.name)
    assertFalse(target.name.contains(".."))
  }

  @Test
  fun `null-album liegt direkt im root`() {
    val target = MusicMirror.targetFile(root, null, "01.mp3")

    assertEquals(root.absolutePath, target.parentFile?.absolutePath)
  }

  @Test
  fun `kanonische schluessel passen zu vorhandenen dateien`() {
    val entry = MusicMirror.canonicalize(MusicManifestEntry("AC/DC", "01: intro.mp3", 10L, null))
    val file = MusicMirror.targetFile(root, entry.album, entry.fileName)
    file.parentFile?.mkdirs()
    file.writeText("x")

    assertEquals(entry.key, MusicMirror.keyOf(root, file))
  }

  @Test
  fun `deletionplan trifft nur audio und nur innerhalb des roots`() {
    val keep = root.resolve("Album A/keep.mp3").apply { parentFile.mkdirs(); writeText("x") }
    val stale = root.resolve("Album A/stale.flac").apply { writeText("x") }
    root.resolve("Album A/notizen.txt").writeText("x")
    root.resolve("Album A/halbe.part").writeText("x")

    val plan = MusicMirror.deletionPlan(root, listOf(MusicManifestEntry("Album A", "keep.mp3")))

    assertFalse(plan.contains(keep))
    assertTrue(plan.contains(stale))
    assertEquals(1, plan.size)
  }

  @Test
  fun `check erkennt gleiche datei und fehlende datei`() {
    val present = MusicManifestEntry("Album A", "same.mp3", 1L, null)
    val missing = MusicManifestEntry("Album A", "weg.mp3", 1L, null)
    root.resolve("Album A").mkdirs()
    root.resolve("Album A/same.mp3").writeText("x")

    val check = MusicMirror.check(root, listOf(present, missing))

    assertEquals(listOf(present), check.present)
    assertEquals(listOf(missing), check.missing)
  }

  @Test
  fun `part-dateien werden aufgeraeumt`() {
    root.resolve("Album A").mkdirs()
    root.resolve("Album A/01.mp3.part").writeText("x")
    root.resolve("Album A/02.mp3").writeText("x")

    val removed = MusicMirror.removePartFiles(root)

    assertEquals(1, removed)
    assertTrue(root.resolve("Album A/02.mp3").exists())
  }

  @Test
  fun `leere alben verschwinden - der root bleibt`() {
    root.resolve("Album A").mkdirs()
    root.resolve("Album B").mkdirs()
    root.resolve("Album B/02.mp3").writeText("x")

    MusicMirror.removeEmptyFolders(root)

    assertFalse(root.resolve("Album A").exists())
    assertTrue(root.resolve("Album B").exists())
    assertTrue(root.exists())
  }
}
