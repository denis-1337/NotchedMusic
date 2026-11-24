package de.notchedmusic.phone.data

import android.net.Uri

/**
 * Ein Titel, wie ihn die Bibliothek kennt.
 *
 * Metadaten (Künstler, Album, Länge) werden bewusst **lazy** nachgeladen: Ein Scan von mehreren
 * tausend Dateien darf nicht für jede Datei einen `MediaMetadataRetriever` öffnen. Der Scanner
 * setzt den Dateinamen als Titel; [MusicScanner] liefert nur das, was der Verzeichnis-Cursor
 * ohnehin mitbringt.
 */
data class LibraryTrack(
  val uri: Uri,
  val documentId: String,
  val displayName: String,
  val title: String,
  val fileSizeBytes: Long,
  val mimeType: String?,
  val artist: String? = null,
  val album: String? = null,
  val durationMs: Long? = null,
) {

  /** Für die Anzeige: Künstler, sonst Dateiname ohne Endung. */
  val subtitle: String
    get() = artist?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.', displayName)

  /** Albumname auf der Uhr: Metadaten, sonst der Ordner aus dem SAF-Baum, sonst „Unsortiert“. */
  val watchAlbum: String
    get() = album?.takeIf { it.isNotBlank() } ?: WATCH_FALLBACK_ALBUM

  private companion object {
    const val WATCH_FALLBACK_ALBUM = "Unsortiert"
  }
}

/**
 * Ein auswählbarer Navigationspunkt.
 *
 * Es gibt nur den Root und dessen **direkte** Unterordner. [tracks] enthält rekursiv alle Titel
 * des Teilbaums; tiefere Unterordner sind keine eigenen Knoten.
 */
data class FolderNode(
  val key: String,
  val name: String,
  val isRoot: Boolean,
  val tracks: List<LibraryTrack>,
) {
  val trackCount: Int
    get() = tracks.size
  val sizeBytes: Long
    get() = tracks.sumOf { it.fileSizeBytes }
}

/** Unveränderlicher Stand der Bibliothek im RAM. */
data class LibrarySnapshot(
  val rootName: String,
  val folders: List<FolderNode>,
  val scannedAtMs: Long,
) {
  val root: FolderNode?
    get() = folders.firstOrNull { it.isRoot }

  val allTracks: List<LibraryTrack>
    get() = folders.flatMap { it.tracks }.distinctBy { it.documentId }

  val isEmpty: Boolean
    get() = folders.all { it.tracks.isEmpty() }

  fun folder(key: String): FolderNode? = folders.firstOrNull { it.key == key }

  fun tracksFor(folderKeys: Set<String>): List<LibraryTrack> =
    folders.filter { it.key in folderKeys }.flatMap { it.tracks }.distinctBy { it.documentId }

  companion object {
    val EMPTY = LibrarySnapshot(rootName = "", folders = emptyList(), scannedAtMs = 0L)
  }
}

/** Formatfilter: Musik ja, Videos und Systemdateien nein. */
object TrackFormats {

  private val extensions =
    setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")

  private val mimeTypes =
    setOf(
      "audio/mpeg",
      "audio/mp3",
      "audio/mp4",
      "audio/aac",
      "audio/ogg",
      "audio/opus",
      "audio/flac",
      "audio/x-flac",
      "audio/wav",
      "audio/x-wav",
      "audio/vnd.wave",
    )

  fun extensionOf(fileName: String): String =
    fileName.substringAfterLast('.', "").lowercase()

  /** MP3 wird priorisiert; alle gelisteten Formate werden aber erkannt. */
  fun isSupported(fileName: String, mimeType: String?): Boolean {
    val extension = extensionOf(fileName)
    if (extension in extensions) return true
    return mimeType != null && mimeType.lowercase() in mimeTypes
  }

  fun isMp3(fileName: String, mimeType: String?): Boolean =
    extensionOf(fileName) == "mp3" || mimeType == "audio/mpeg"

  fun titleFromFileName(fileName: String): String =
    fileName.substringBeforeLast('.', fileName)
}

/**
 * Natürliche Namenssortierung: „2 - Titel“ steht vor „10 - Titel“.
 *
 * Reine Zeichenketten-Vergleiche würden 10 vor 2 einsortieren – bei Alben mit Nummerierung ist
 * das unbrauchbar. Zahlenblöcke werden deshalb numerisch verglichen.
 */
object NaturalOrder : Comparator<String> {

  override fun compare(first: String, second: String): Int {
    var a = 0
    var b = 0
    while (a < first.length && b < second.length) {
      val charA = first[a]
      val charB = second[b]
      if (charA.isDigit() && charB.isDigit()) {
        val startA = a
        val startB = b
        while (a < first.length && first[a].isDigit()) a++
        while (b < second.length && second[b].isDigit()) b++
        val numberA = first.substring(startA, a).trimStart('0')
        val numberB = second.substring(startB, b).trimStart('0')
        if (numberA.length != numberB.length) {
          return numberA.length - numberB.length
        }
        val numeric = numberA.compareTo(numberB)
        if (numeric != 0) return numeric
      } else {
        val lowerA = charA.lowercaseChar()
        val lowerB = charB.lowercaseChar()
        if (lowerA != lowerB) return lowerA.compareTo(lowerB)
        a++
        b++
      }
    }
    return (first.length - a) - (second.length - b)
  }
}

/** Endungs- und MIME-Prüfung ist hier zentral, damit Scan und Sync dasselbe Kriterium nutzen. */
fun libraryTrack(
  uri: Uri,
  documentId: String,
  displayName: String,
  fileSizeBytes: Long,
  mimeType: String?,
): LibraryTrack =
  LibraryTrack(
    uri = uri,
    documentId = documentId,
    displayName = displayName,
    title = TrackFormats.titleFromFileName(displayName),
    fileSizeBytes = fileSizeBytes,
    mimeType = mimeType,
  )

/** Mit explizitem Album – der Scanner kennt den Ordnernamen, bevor Metadaten geladen sind. */
fun libraryTrack(
  uri: Uri,
  documentId: String,
  displayName: String,
  fileSizeBytes: Long,
  mimeType: String?,
  album: String?,
): LibraryTrack =
  libraryTrack(uri, documentId, displayName, fileSizeBytes, mimeType).copy(album = album)
