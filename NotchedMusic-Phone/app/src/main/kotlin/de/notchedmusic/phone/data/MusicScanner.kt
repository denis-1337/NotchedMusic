package de.notchedmusic.phone.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Ergebnis eines Bibliotheks-Scans. */
sealed interface ScanResult {
  data class Success(val snapshot: LibrarySnapshot) : ScanResult
  data class Failure(val message: String) : ScanResult
}

/**
 * Scannt den vom Nutzer gewählten SAF-Baum.
 *
 * Es gibt genau **einen** Verzeichnisdurchlauf, im Hintergrund und abbrechbar. Jeder Knoten
 * entspricht dem Root oder einem direkten Unterordner; die Titel eines Knotens werden rekursiv
 * eingesammelt. Verzeichnis-Cursor statt `DocumentFile`: weniger Objekte und weniger Roundtrips.
 */
class MusicScanner(private val context: Context) {

  private val resolver = context.contentResolver

  /**
   * Liest die Bibliothek unter [treeUri].
   *
   * Es wird nie in den UI-Thread zurückgekehrt, bis der komplette Snapshot steht.
   */
  suspend fun scan(treeUri: Uri): ScanResult =
    withContext(Dispatchers.IO) {
      val started = System.currentTimeMillis()
      try {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)
        val rootName = queryDisplayName(rootDocumentUri) ?: FALLBACK_ROOT_NAME

        val budget = ScanBudget()
        val entries = queryChildren(treeUri, treeDocumentId)
        val rootDirectTracks = mutableListOf<LibraryTrack>()
        val rootFiles = mutableListOf<LibraryTrack>()
        val directories = mutableListOf<FolderNode>()

        for (entry in entries) {
          if (entry.isDirectory) {
            if (entry.name.startsWith('.')) continue
            if (directories.size >= MAX_ROOT_NODES) {
              Log.w(TAG, "Mehr als $MAX_ROOT_NODES Unterordner – Rest wird übersprungen.")
              continue
            }
            val tracks = mutableListOf<LibraryTrack>()
            collectDirectory(
              treeUri = treeUri,
              documentId = entry.documentId,
              depth = 2,
              budget = budget,
              albumName = entry.name,
              into = tracks,
            )
            directories += FolderNode(
              key = entry.documentId,
              name = entry.name,
              isRoot = false,
              tracks = tracks.sortedWith(trackComparator),
            )
          } else {
            entry.toTrack(treeUri, rootName)?.let { rootFiles += it }
          }
        }

        // Root = direkt im Root liegende Titel + alle Titel der Unterordner (rekursiv).
        rootDirectTracks += rootFiles
        rootDirectTracks += directories.flatMap { it.tracks }
        val rootNode =
          FolderNode(
            key = ROOT_KEY,
            name = rootName,
            isRoot = true,
            tracks = rootDirectTracks.distinctBy { it.documentId }.sortedWith(trackComparator),
          )

        val snapshot =
          LibrarySnapshot(
            rootName = rootName,
            folders = listOf(rootNode) + directories.sortedWith { first, second -> NaturalOrder.compare(first.name, second.name) },
            scannedAtMs = System.currentTimeMillis(),
          )
        Log.i(TAG, "Scan fertig: ${snapshot.allTracks.size} Titel in ${System.currentTimeMillis() - started} ms, ${snapshot.folders.size} Knoten.")
        ScanResult.Success(snapshot)
      } catch (error: FileNotFoundException) {
        ScanResult.Failure("Der Ordner ist nicht mehr lesbar. Bitte den Musikordner erneut wählen.")
      } catch (error: SecurityException) {
        ScanResult.Failure("Der Zugriff auf den Musikordner fehlt. Bitte den Ordner erneut freigeben.")
      } catch (error: Exception) {
        Log.w(TAG, "Scan fehlgeschlagen", error)
        ScanResult.Failure("Die Bibliothek konnte nicht gelesen werden.")
      }
    }

  private suspend fun collectDirectory(
    treeUri: Uri,
    documentId: String,
    depth: Int,
    budget: ScanBudget,
    albumName: String,
    into: MutableList<LibraryTrack>,
  ) {
    if (depth > MAX_DEPTH) return
    if (budget.collected >= MAX_FILES) return
    val entries = queryChildren(treeUri, documentId)
    for (entry in entries) {
      if (budget.collected >= MAX_FILES) return
      if (entry.isDirectory) {
        if (entry.name.startsWith('.')) continue
        collectDirectory(
          treeUri = treeUri,
          documentId = entry.documentId,
          depth = depth + 1,
          budget = budget,
          albumName = albumName,
          into = into,
        )
      } else {
        val track = entry.toTrack(treeUri, albumName) ?: continue
        into += track
        budget.collected++
      }
    }
  }

  private fun queryChildren(treeUri: Uri, documentId: String): List<RawEntry> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
    val projection =
      arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE,
      )
    val result = mutableListOf<RawEntry>()
    try {
      resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
        while (cursor.moveToNext()) {
          val mime = cursor.getStringOrNull(mimeIndex)
          result +=
            RawEntry(
              documentId = cursor.getString(idIndex),
              name = cursor.getString(nameIndex) ?: "",
              mimeType = mime,
              sizeBytes = cursor.getLongOrZero(sizeIndex),
              isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
            )
        }
      }
    } catch (error: SecurityException) {
      throw error
    }
    return result
  }

  private fun queryDisplayName(documentUri: Uri): String? {
    resolver.query(
      documentUri,
      arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
      null,
      null,
      null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) return cursor.getString(0)
    }
    return null
  }

  private fun RawEntry.toTrack(treeUri: Uri, albumName: String): LibraryTrack? {
    if (isDirectory || name.isBlank() || name.startsWith('.')) return null
    if (!TrackFormats.isSupported(name, mimeType)) return null
    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
    return libraryTrack(
      uri = uri,
      documentId = documentId,
      displayName = name,
      fileSizeBytes = sizeBytes,
      mimeType = mimeType,
      album = albumName.takeIf { it.isNotBlank() },
    )
  }

  private class ScanBudget(var collected: Int = 0)

  private fun Cursor.getStringOrNull(index: Int): String? =
    if (isNull(index)) null else getString(index)

  private fun Cursor.getLongOrZero(index: Int): Long =
    if (isNull(index)) 0L else getLong(index)

  private companion object {
    const val TAG = "MusicScanner"
    const val FALLBACK_ROOT_NAME = "Musik"
    const val ROOT_KEY = "root"
    const val MAX_DEPTH = 8
    const val MAX_FILES = 5_000
    const val MAX_ROOT_NODES = 500

    val trackComparator: Comparator<LibraryTrack> =
      Comparator { first, second ->
        val pathCompare = NaturalOrder.compare(first.displayName, second.displayName)
        if (pathCompare != 0) pathCompare else first.documentId.compareTo(second.documentId)
      }
  }
}

/** Rohzeile einer Verzeichnisabfrage – noch ohne Android-URI. */
private data class RawEntry(
  val documentId: String,
  val name: String,
  val mimeType: String?,
  val sizeBytes: Long,
  val isDirectory: Boolean,
)
