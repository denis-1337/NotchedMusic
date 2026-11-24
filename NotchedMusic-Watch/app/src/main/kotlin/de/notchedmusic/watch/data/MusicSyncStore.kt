package de.notchedmusic.watch.data

import android.content.Context
import de.notchedmusic.watch.protocol.MusicManifest
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ausstehendes Manifest; es wird gespeichert, aber **vor dem Commit nichts gelöscht**. */
@Serializable
data class PendingMusicManifest(
  val sessionId: String,
  val manifest: MusicManifest,
  val receivedAtMs: Long,
)

/** Kleiner, atomar geschriebener Zwischenspeicher zwischen zwei Dienst-Aufrufen. */
object MusicSyncStore {

  private const val FILE_NAME = "pending_music_manifest.json"

  private val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

  @Synchronized
  fun save(context: Context, pending: PendingMusicManifest) {
    val file = File(context.filesDir, FILE_NAME)
    val temporary = File(context.filesDir, "$FILE_NAME.tmp")
    temporary.writeText(json.encodeToString(PendingMusicManifest.serializer(), pending))
    if (file.exists() && !file.delete()) {
      temporary.delete()
      return
    }
    if (!temporary.renameTo(file)) {
      temporary.copyTo(file, overwrite = true)
      temporary.delete()
    }
  }

  @Synchronized
  fun load(context: Context): PendingMusicManifest? {
    val file = File(context.filesDir, FILE_NAME)
    if (!file.isFile) return null
    return try {
      json.decodeFromString(PendingMusicManifest.serializer(), file.readText())
    } catch (_: SerializationException) {
      null
    } catch (_: Exception) {
      null
    }
  }

  @Synchronized
  fun clear(context: Context) {
    File(context.filesDir, FILE_NAME).delete()
    File(context.filesDir, "$FILE_NAME.tmp").delete()
  }
}
