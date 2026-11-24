package de.notchedmusic.watch.data

import java.io.File
import java.util.Locale

/** Ein Musikstück auf dem Speicher der Uhr. */
data class Track(
  /** Anzeigename, ohne Pfad und Dateiendung; führende Nummerierung ist entfernt. */
  val title: String,
  /** Absoluter Pfad zur Datei auf der Uhr. */
  val path: String,
  /** Nummer aus dem Dateinamen (`01 - Morgen.mp3` → 1); `null`, wenn keine da ist. */
  val sortIndex: Int? = null,
)

/** Ein Album = ein Unterordner des Musik-Roots. */
data class Album(
  /** Anzeigename: der Ordnername. */
  val name: String,
  /** Alle Titel daraus, **rekursiv** – auch aus tieferen Unterordnern. */
  val tracks: List<Track>,
)

/**
 * Findet Musik auf der Uhr und ordnet sie in Alben.
 *
 * Aufbau, wie vom Nutzer gewünscht: der Musik-Root ist die Album-Liste, **jeder Unterordner ist
 * ein Album**. Titel werden aus jedem Album **rekursiv** eingesammelt – ein Album darf also selbst
 * noch Unterordner haben (z. B. „CD 2"), ohne dass Titel verloren gehen.
 *
 * Dateien direkt im Root landen im Album [LOOSE_TRACKS_NAME], damit nichts verschwindet.
 *
 * **Grenzen gegen Fehlerbäume:** höchstens [MAX_DEPTH] Ebenen, [MAX_FILES] Dateien und
 * [MAX_ALBUMS] Alben pro Durchlauf. Versteckte Einträge (`.name`) werden übersprungen. Das hält
 * einen versehentlich kopierten Systemordner davon ab, die Uhr lahmzulegen.
 *
 * Nummerierte Dateinamen werden sortiert und für die Anzeige bereinigt:
 * `01 - Morgen.mp3` → Titel „Morgen", Sortierindex 1. Ein „99 Luftballons" ohne Trenner bleibt
 * unverändert – die Nummer zählt nur vor `-`, `.` oder `_`.
 *
 * **Wichtig fürs Testen:** Ordner, die von `adb shell mkdir` angelegt wurden, gehören `shell`,
 * nicht der App – die App kann sie dann nicht auflisten. Ordner müssen **von der App** angelegt
 * werden (z. B. über den Handy-Upload), sonst bleibt das Album unsichtbar.
 *
 * Bewusst ohne Android-Klassen: so lässt sich alles auf dem PC testen (MusicLibraryTest).
 */
class MusicLibrary(private val musicDir: File) {

  /** Der Ordner, in dem gesucht wird – für Diagnose und Fehlermeldungen. */
  val folderPath: String
    get() = musicDir.absolutePath

  /** Alle Alben, alphabetisch. Leer, wenn der Ordner leer oder nicht vorhanden ist. */
  fun albums(): List<Album> {
    val children = musicDir.listFiles() ?: return emptyList()

    val fromFolders = mutableListOf<Album>()
    var filesCollected = 0

    val folders =
      children
        .filter { it.isDirectory && !it.name.startsWith('.') }
        .sortedBy { it.name.lowercase(Locale.GERMAN) }
        .take(MAX_ALBUMS)

    for (folder in folders) {
      val remaining = MAX_FILES - filesCollected
      if (remaining <= 0) break
      val tracks = tracksRecursively(folder, remaining)
      if (tracks.isNotEmpty()) {
        fromFolders += Album(name = folder.name, tracks = tracks)
        filesCollected += tracks.size
      }
    }

    val loose = tracksIn(musicDir, MAX_FILES - filesCollected)
    return if (loose.isEmpty()) fromFolders else fromFolders + Album(LOOSE_TRACKS_NAME, loose)
  }

  /** Alle Titel aus [album] – bereits rekursiv eingelesen. */
  fun tracks(album: Album): List<Track> = album.tracks

  /** Titel **rekursiv** unter [dir], sortiert nach Nummerierung, sonst Name. */
  private fun tracksRecursively(dir: File, budget: Int): List<Track> {
    val files = collectAudioFiles(dir, depth = 0, budget = budget)
    val rootPath = dir.absolutePath.length
    return files
      .sortedWith(
        compareBy<Track> { it.sortIndex ?: Int.MAX_VALUE }
          .thenBy { it.path.substring(rootPath).lowercase(Locale.GERMAN) },
      )
  }

  /** Nur die Titel einer einzelnen Ebene – für das Album „lose Dateien". */
  private fun tracksIn(dir: File, budget: Int): List<Track> =
    (dir.listFiles() ?: emptyArray())
      .asSequence()
      .filter { !it.name.startsWith('.') && isAudioFile(it) }
      .take(budget.coerceAtLeast(0))
      .map(::trackOf)
      .sortedWith(compareBy<Track> { it.sortIndex ?: Int.MAX_VALUE }.thenBy { it.title.lowercase(Locale.GERMAN) })
      .toList()

  /** Sammelt alle Audiodateien unter [dir], egal wie tief – bis Tiefen- und Dateigrenze. */
  private fun collectAudioFiles(dir: File, depth: Int, budget: Int): List<Track> {
    if (depth > MAX_DEPTH || budget <= 0) return emptyList()
    val files = dir.listFiles() ?: return emptyList()
    val result = mutableListOf<Track>()
    for (file in files) {
      if (result.size >= budget) break
      if (file.name.startsWith('.')) continue
      if (file.isDirectory) {
        result +=
          collectAudioFiles(
            file,
            depth = depth + 1,
            budget = budget - result.size,
          )
      } else if (isAudioFile(file)) {
        result += trackOf(file)
      }
    }
    return result
  }

  private fun trackOf(file: File): Track {
    val baseName = file.nameWithoutExtension
    val numbered = NUMBERED_NAME.matchEntire(baseName)
    return if (numbered == null) {
      Track(title = baseName, path = file.absolutePath)
    } else {
      Track(
        title = numbered.groupValues[2].trim().ifEmpty { baseName },
        path = file.absolutePath,
        sortIndex = numbered.groupValues[1].toIntOrNull(),
      )
    }
  }

  private fun isAudioFile(file: File): Boolean =
    file.isFile && file.extension.lowercase(Locale.GERMAN) in SUPPORTED_EXTENSIONS

  companion object {
    /** Endungen, die der Player versteht. MP3 ist der Hauptfall, die anderen sind Zugabe. */
    private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")

    /** Album-Name für Titel, die direkt im Musik-Root liegen. */
    const val LOOSE_TRACKS_NAME = "(no album)"

    /** Höchste Unterordner-Ebene innerhalb eines Albums (gegen Fehlerbäume). */
    const val MAX_DEPTH = 6

    /** Höchstens so viele Audiodateien pro Durchlauf. */
    const val MAX_FILES = 2_000

    /** Höchstens so viele Alben pro Durchlauf. */
    const val MAX_ALBUMS = 200

    /** Nur Nummern vor einem Trenner gelten als Sortierindex: `01 - Morgen.mp3`. */
    private val NUMBERED_NAME = Regex("""^(\d{1,3})\s*[-._]\s*(.+)$""")
  }
}
