package de.notchedmusic.phone.sync

import android.content.Context
import androidx.core.net.toUri
import de.notchedmusic.core.protocol.SyncProtocol
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Geprüfte Audiodatei für Manifest und Upload. */
data class PreparedAsset(
  val sizeBytes: Long,
  val sha256: String,
)

/** Fehler beim Vorbereiten einer Datei – verständlich für die Sync-Seite. */
class AssetPreparationException(message: String) : Exception(message)

/** Liest Größe und SHA-256 einer ausgewählten Datei. */
interface AssetSource {
  suspend fun prepare(uri: String): PreparedAsset
}

/**
 * Reale Umsetzung über den ContentResolver.
 *
 * Gelesen wird genau einmal: Größe und Prüfsumme entstehen im selben Durchlauf. Zu große Dateien
 * werden vor dem Manifest abgelehnt, damit die Uhr gar nicht erst in einen halben Lauf startet.
 */
class ContentResolverAssetSource(private val context: Context) : AssetSource {

  override suspend fun prepare(uri: String): PreparedAsset =
    withContext(Dispatchers.IO) {
      val parsed = uri.toUri()
      val input =
        try {
          context.contentResolver.openInputStream(parsed)
        } catch (_: Exception) {
          null
        } ?: throw AssetPreparationException("Datei ist nicht mehr lesbar.")

      val digest = MessageDigest.getInstance("SHA-256")
      var total = 0L
      try {
        input.use { source ->
          val buffer = ByteArray(BUFFER_BYTES)
          while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            total += read
            if (total > SyncProtocol.MAX_MUSIC_FILE_BYTES) {
              throw AssetPreparationException("Datei ist größer als 40 MB.")
            }
            digest.update(buffer, 0, read)
          }
        }
      } catch (error: AssetPreparationException) {
        throw error
      } catch (_: Exception) {
        throw AssetPreparationException("Datei konnte nicht gelesen werden.")
      }
      if (total <= 0L) throw AssetPreparationException("Datei ist leer.")
      PreparedAsset(total, digest.digest().joinToString("") { "%02x".format(it) })
    }

  private companion object {
    const val BUFFER_BYTES = 64 * 1024
  }
}
