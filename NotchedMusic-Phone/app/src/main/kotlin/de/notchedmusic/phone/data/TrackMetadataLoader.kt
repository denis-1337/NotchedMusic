package de.notchedmusic.phone.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lädt Titel-Metadaten **lazy** für den aktuellen und die benachbarten Titel.
 *
 * `MediaMetadataRetriever` arbeitet nicht billig; für 5 000 Dateien beim Scan wäre das ein
 * Mehrfaches an Zeit. Deshalb wird nur angefragt, was die Oberfläche gerade braucht. Ein kleiner
 * LRU-Cache hält die letzten Ergebnisse; der Retriever wird immer geschlossen.
 */
class TrackMetadataLoader(private val context: Context) {

  private val retrieverCache =
    object : LruCache<String, CachedMetadata>(64) {
      override fun sizeOf(key: String, value: CachedMetadata): Int = 1
    }

  /** Liefert den Titel angereichert um Künstler, Album und Dauer; im Fehlerfall unverändert. */
  suspend fun load(track: LibraryTrack): LibraryTrack =
    withContext(Dispatchers.IO) {
      val cached = retrieverCache.get(track.documentId)
      val metadata = cached ?: readMetadata(track)?.also { retrieverCache.put(track.documentId, it) }
      if (metadata == null) {
        return@withContext track
      }
      track.copy(
        title = metadata.title?.takeIf { it.isNotBlank() } ?: track.title,
        artist = metadata.artist,
        album = metadata.album,
        durationMs = metadata.durationMs,
      )
    }

  private fun readMetadata(track: LibraryTrack): CachedMetadata? {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, track.uri)
      CachedMetadata(
        title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
        artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
        album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
        durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
      )
    } catch (_: IllegalArgumentException) {
      null
    } catch (_: RuntimeException) {
      null
    } finally {
      retriever.release()
    }
  }

  private data class CachedMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
  )
}
