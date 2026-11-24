package de.notchedmusic.watch.protocol

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Synchronisationspaket **Version 3** für Filter-Presets – dieselbe Struktur wie auf dem Phone. */
@Serializable
data class PresetSyncPayload(
  val version: Int = SyncProtocol.VERSION,
  val presets: List<EqPreset> = emptyList(),
  val activePresetId: String? = null,
) {

  fun completePresets(): List<EqPreset> {
    val defaults = DefaultPresets.three()
    return List(EqPreset.MAX_PRESETS) { index ->
      presets.getOrNull(index) ?: defaults[index]
    }
  }
}

/** Ergebnis der Musik-Auswahl; auf der Uhr nur zur Anzeige von Fehlern nötig. */
@Serializable
data class MusicUpload(
  val uri: String,
  val fileName: String,
  val album: String? = null,
  val sizeBytes: Long = 0L,
  val mimeType: String? = null,
  val sha256: String? = null,
)

/** Eine Soll-Datei im Musik-Spiegel der Uhr. */
@Serializable
data class MusicManifestEntry(
  val album: String? = null,
  val fileName: String = "",
  val sizeBytes: Long = 0L,
  val sha256: String? = null,
) {

  val key: String
    get() = "${album.orEmpty()}\u0000$fileName"
}

/** Soll-Liste eines Musik-Syncs – genau der anschließend gewünschte Bestand. */
@Serializable
data class MusicManifest(
  val version: Int = VERSION,
  val entries: List<MusicManifestEntry> = emptyList(),
) {

  companion object {
    const val VERSION = 1
  }
}

/** Antwort der Uhr; der Phone-Transport korreliert über `syncId`, `kind` und `index`. */
@Serializable
data class SyncAck(
  val protocol: Int = SyncProtocol.VERSION,
  val syncId: String = "",
  val kind: String = "",
  val status: String = SyncProtocol.STATUS_REJECTED,
  val appliedAtMs: Long = 0L,
  val presetCount: Int? = null,
  val watchVersion: String? = null,
  val error: String? = null,
  val index: Int? = null,
  val missingCount: Int? = null,
  val missingFiles: List<String>? = null,
  val presentFiles: List<String>? = null,
  val missingIndexes: List<Int>? = null,
  val presentIndexes: List<Int>? = null,
  val deletedCount: Int? = null,
  val freeBytes: Long? = null,
) {

  val isApplied: Boolean
    get() = status == SyncProtocol.STATUS_APPLIED
}

@Serializable
data class SyncCancel(
  val syncId: String = "",
  val reason: String? = null,
)

object SyncKinds {
  const val PRESETS = "presets"
  const val MUSIC_MANIFEST = "music_manifest"
  const val MUSIC_FILE = "music_file"
  const val MUSIC_COMMIT = "music_commit"
  const val CANCEL = "cancel"
}

/**
 * Lesen und Schreiben aller Data-Layer-Pakete.
 *
 * Fehlerhafte Pakete werden nie als Exception nach oben gereicht: Audio- und Sync-Pfade dürfen
 * an fremden Daten nicht scheitern.
 */
object SyncProtocol {

  const val VERSION = 3
  const val MAX_JSON_BYTES = 8 * 1024
  const val MAX_MANIFEST_BYTES = 1024 * 1024
  const val MAX_MUSIC_FILE_BYTES = 40L * 1024L * 1024L
  const val WATCH_VERSION = "2.0"

  const val STATUS_APPLIED = "APPLIED"
  const val STATUS_REJECTED = "REJECTED"
  const val STATUS_CANCELLED = "CANCELLED"

  const val ERROR_INVALID_PAYLOAD = "invalid_payload"
  const val ERROR_TOO_LARGE = "too_large"
  const val ERROR_UNSUPPORTED_VERSION = "unsupported_version"
  const val ERROR_INTERNAL = "internal_error"
  const val ERROR_INSUFFICIENT_SPACE = "insufficient_space"
  const val ERROR_SESSION_MISMATCH = "session_mismatch"
  const val ERROR_CHECKSUM_MISMATCH = "checksum_mismatch"
  const val ERROR_MISSING_FILE = "missing_file"

  private val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      isLenient = false
    }

  fun encode(payload: PresetSyncPayload): String = json.encodeToString(PresetSyncPayload.serializer(), payload)

  fun decode(text: String): PresetSyncPayload? {
    if (text.isBlank() || text.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES) return null
    return try {
      json.decodeFromString(PresetSyncPayload.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  fun encodeAck(ack: SyncAck): String = json.encodeToString(SyncAck.serializer(), ack)

  fun decodeAck(text: String): SyncAck? =
    try {
      if (text.isBlank() || text.length > MAX_ACK_BYTES) null
      else json.decodeFromString(SyncAck.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  fun encodeCancel(cancel: SyncCancel): String = json.encodeToString(SyncCancel.serializer(), cancel)

  fun decodeCancel(text: String): SyncCancel? =
    try {
      if (text.isBlank() || text.length > MAX_ACK_BYTES) null
      else json.decodeFromString(SyncCancel.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  fun encodeManifest(manifest: MusicManifest): String =
    json.encodeToString(MusicManifest.serializer(), manifest)

  fun decodeManifest(text: String): MusicManifest? {
    if (text.isBlank()) return null
    if (text.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) return null
    return try {
      json.decodeFromString(MusicManifest.serializer(), text)
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  fun validate(payload: PresetSyncPayload, sampleRateHz: Double = 48_000.0): PresetSyncPayload {
    val presets = payload.completePresets().take(EqPreset.MAX_PRESETS)
    val cleaned =
      presets.mapIndexed { index, preset ->
        val withId =
          preset.copy(
            id = preset.id.ifBlank { DefaultPresets.three()[index].id },
            name = preset.name.ifBlank { "Preset ${index + 1}" },
          )
        withId.validate(sampleRateHz)
      }
    val activeId =
      payload.activePresetId?.takeIf { id -> cleaned.any { it.id == id } }
        ?: cleaned.first().id
    return PresetSyncPayload(version = VERSION, presets = cleaned, activePresetId = activeId)
  }

  private const val MAX_ACK_BYTES = 64 * 1024
}
