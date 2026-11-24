package de.notchedmusic.core.protocol

import kotlinx.serialization.Serializable

/**
 * Bestätigung der Uhr für Presets, Manifest, einzelne Dateien oder den Musik-Commit.
 *
 * Der Phone-Transport korreliert die Antwort über `syncId` und `kind`; bei Datei-Uploads
 * zusätzlich über `index`. Alle übrigen Felder sind optional und werden nur dort gesetzt, wo
 * sie eine Bedeutung haben.
 */
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

/** Abbruch eines laufenden Syncs; wird von beiden Seiten verstanden. */
@Serializable
data class SyncCancel(
  val syncId: String = "",
  val reason: String? = null,
)

/** Feste Kennungen für die ACK-Korrelation. */
object SyncKinds {
  const val PRESETS = "presets"
  const val MUSIC_MANIFEST = "music_manifest"
  const val MUSIC_FILE = "music_file"
  const val MUSIC_COMMIT = "music_commit"
  const val CANCEL = "cancel"
}
