package de.notchedmusic.watch.data

import de.notchedmusic.watch.protocol.MusicManifestEntry
import java.io.File
import java.security.MessageDigest

/**
 * Reine Datei- und Pfadlogik für den Musik-Spiegel.
 *
 * Die Uhr hat eine flache Alben-Struktur: Dateien direkt im Musik-Root gehören zu
 * [(ohne Album)], Dateien im direkten Unterordner zu diesem Ordner. Beim Commit wird ausschließlich
 * innerhalb des App-Musikordners und ausschließlich Audiodateien gelöscht.
 */
object MusicMirror {

  const val LOOSE_ALBUM = "(no album)"
  const val MAX_NAME_LENGTH = 120
  const val MIN_FREE_BYTES = 100L * 1024L * 1024L

  private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")

  /** Entschärft einen Album- oder Dateinamen: keine Trenner, kein `..`, kein führender Punkt. */
  fun sanitizeSegment(raw: String): String {
    var name =
      raw.trim()
        .replace('/', '_')
        .replace('\\', '_')
        .replace('\u0000', '_')
        .replace("..", "_")
    while (name.startsWith('.')) name = "_" + name.removePrefix(".")
    name = name.trim().ifEmpty { "unnamed" }
    return name.take(MAX_NAME_LENGTH)
  }

  fun normalizeAlbum(album: String?): String? =
    album?.trim()?.takeIf { it.isNotEmpty() && it != LOOSE_ALBUM }

  fun targetFile(root: File, album: String?, fileName: String): File {
    val folder = normalizeAlbum(album)?.let { File(root, sanitizeSegment(it)) } ?: root
    return File(folder, sanitizeSegment(fileName))
  }

  /** Kanonischer Schlüssel aus Rohdaten; entspricht dem Schlüssel der späteren Datei. */
  fun keyFor(album: String?, fileName: String): String =
    "${normalizeAlbum(album)?.let(::sanitizeSegment).orEmpty()}\u0000${sanitizeSegment(fileName)}"

  /** Entschärft einen Manifest-Eintrag, damit Schlüssel und Zielpfad zusammenpassen. */
  fun canonicalize(entry: MusicManifestEntry): MusicManifestEntry =
    entry.copy(album = normalizeAlbum(entry.album)?.let(::sanitizeSegment), fileName = sanitizeSegment(entry.fileName))

  fun displayPath(entry: MusicManifestEntry): String =
    normalizeAlbum(entry.album)?.let { "$it/${entry.fileName}" } ?: entry.fileName

  fun isAudioFile(file: File): Boolean =
    file.isFile && !file.name.endsWith(".part", ignoreCase = true) &&
      file.extension.lowercase() in AUDIO_EXTENSIONS

  /** Alle Musikdateien unter [root], rekursiv und begrenzt. */
  fun existingAudioFiles(root: File, maxFiles: Int = 2_000): List<File> {
    val result = mutableListOf<File>()
    fun collect(directory: File, depth: Int) {
      if (depth > 8 || result.size >= maxFiles) return
      val children = directory.listFiles() ?: return
      for (child in children) {
        if (result.size >= maxFiles) return
        if (child.name.startsWith('.')) continue
        if (child.isDirectory) {
          collect(child, depth + 1)
        } else if (isAudioFile(child)) {
          result += child
        }
      }
    }
    collect(root, 0)
    return result
  }

  /** Schlüssel eines Ziels relativ zum Musik-Root. */
  fun keyOf(root: File, file: File): String {
    val parent = file.parentFile
    val album = if (parent == null || parent.absolutePath == root.absolutePath) null else parent.name
    return "${album.orEmpty()}\u0000${file.name}"
  }

  /** Ist-Datei passend zu einem Manifest-Eintrag: Größe und, wenn vorhanden, SHA-256. */
  fun matches(file: File, entry: MusicManifestEntry): Boolean {
    if (!file.isFile || file.length() != entry.sizeBytes) return false
    val expected = entry.sha256 ?: return true
    return sha256(file).equals(expected, ignoreCase = true)
  }

  data class Check(
    val present: List<MusicManifestEntry>,
    val missing: List<MusicManifestEntry>,
  )

  /** Prüft den Ist-Bestand gegen das Manifest, ohne etwas zu verändern. */
  fun check(root: File, entries: List<MusicManifestEntry>): Check {
    val present = mutableListOf<MusicManifestEntry>()
    val missing = mutableListOf<MusicManifestEntry>()
    for (entry in entries) {
      val file = targetFile(root, entry.album, entry.fileName)
      if (matches(file, entry)) present += entry else missing += entry
    }
    return Check(present = present, missing = missing)
  }

  /** Löschplan: alles, was Musikdatei ist und nicht im Manifest steht. */
  fun deletionPlan(root: File, entries: List<MusicManifestEntry>): List<File> {
    val wanted = entries.map { it.key }.toSet()
    return existingAudioFiles(root).filter { keyOf(root, it) !in wanted }
  }

  /** Entfernt leer gewordene Album-Ordner; der Root selbst bleibt. */
  fun removeEmptyFolders(root: File) {
    val children = root.listFiles().orEmpty()
    for (child in children) {
      if (!child.isDirectory || child.name.startsWith('.')) continue
      removeEmptyFolders(child)
      if ((child.listFiles()?.isEmpty() ?: false) && child.delete()) {
        // Der leere Ordner ist jetzt weg.
      }
    }
  }

  fun hasEnoughSpace(root: File, sizeBytes: Long): Boolean {
    val usable = root.usableSpace
    return usable <= 0L || usable >= sizeBytes + MIN_FREE_BYTES
  }

  fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(64 * 1024)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** Räumt verwaiste `.part`-Dateien innerhalb des Musikordners auf. */
  fun removePartFiles(root: File): Int {
    var removed = 0
    fun collect(directory: File, depth: Int) {
      if (depth > 8) return
      for (child in directory.listFiles().orEmpty()) {
        if (child.isDirectory) {
          collect(child, depth + 1)
        } else if (child.name.endsWith(".part", ignoreCase = true) && child.delete()) {
          removed++
        }
      }
    }
    collect(root, 0)
    return removed
  }
}
