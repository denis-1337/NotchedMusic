package de.notchedmusic.core.protocol

import de.notchedmusic.core.model.DefaultPresets
import de.notchedmusic.core.model.EqPreset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Synchronisationspaket **Version 3** für Filter-Presets.
 *
 * Es enthält immer alle drei Presets samt Vor-Verstärkung und Bändern. Der Player-Lautstärkeregler
 * und die Systemlautstärke werden bewusst nicht mitgesendet: Ein Sync darf den Hörpegel nicht
 * verstellen. Die Uhr-App nutzt dasselbe Modell und bestätigt den Empfang. Der Transport läuft
 * über die Wear Data Layer.
 */
@Serializable
data class PresetSyncPayload(
  val version: Int = SyncProtocol.VERSION,
  val presets: List<EqPreset> = emptyList(),
  val activePresetId: String? = null,
) {

  /** Die drei Presets, bei weniger Einträgen mit Standardrezepten aufgefüllt. */
  fun completePresets(): List<EqPreset> {
    val defaults = DefaultPresets.three()
    return List(EqPreset.MAX_PRESETS) { index ->
      presets.getOrNull(index) ?: defaults[index]
    }
  }
}

/** Ergebnis der Musik-Auswahl beim Synchronisieren (nur Daten, keine Dateizugriffe). */
@Serializable
data class MusicUpload(
  val uri: String,
  val fileName: String,
  val album: String? = null,
  val sizeBytes: Long = 0L,
  val mimeType: String? = null,
  val sha256: String? = null,
)

/**
 * Lesen und Schreiben des Preset-Pakets.
 *
 * Fehlerhafte Pakete werden **nie** als Exception nach oben gereicht: Die Audio- und Sync-Pfade
 * dürfen an fremden Daten nicht scheitern. Unbekannte Felder werden ignoriert, überzählige
 * Presets und Bänder abgeschnitten, alle Werte geklemmt.
 */
object SyncProtocol {

  const val VERSION = 3

  /** Größenlimit für eingehende Presets. */
  const val MAX_JSON_BYTES = 8 * 1024

  /** Größenlimit für ein Musik-Manifest (etwa 2.000 Titel). */
  const val MAX_MANIFEST_BYTES = 1024 * 1024

  /** Größte zulässige Audiodatei im ersten Wurf (Chunking ist späterer Ausbau). */
  const val MAX_MUSIC_FILE_BYTES = 40L * 1024L * 1024L

  /** Kennung der aktuellen Uhr-App im ACK. */
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

  /** Liest ein Paket; `null` bei leerem, zu großem oder ungültigem Text. */
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

  /** Liest ein Manifest; `null`, wenn es leer, zu groß oder inhaltlich kaputt ist. */
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

  /**
   * Bringt ein gelesenes Paket in die verbindliche Form: genau drei Presets, je sechs Bänder,
   * gültige Frequenzen für [sampleRateHz].
   */
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
