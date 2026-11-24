package de.notchedmusic.core.protocol

import kotlinx.serialization.Serializable

/** Eine Soll-Datei im Musik-Spiegel der Uhr. `album = null` heißt: direkt im Musik-Root. */
@Serializable
data class MusicManifestEntry(
  val album: String? = null,
  val fileName: String = "",
  val sizeBytes: Long = 0L,
  val sha256: String? = null,
) {

  /** Eindeutiger Schlüssel des Zielorts; die Uhr überschreibt bei Gleichheit atomar. */
  val key: String
    get() = "${album.orEmpty()}\u0000$fileName"
}

/**
 * Soll-Liste eines Musik-Syncs.
 *
 * Ein Manifest ist höchstens [SyncProtocol.MAX_MANIFEST_BYTES] groß. Es beschreibt **genau** den
 * anschließend gewünschten Bestand; gelöscht wird erst nach erfolgreichem Commit.
 */
@Serializable
data class MusicManifest(
  val version: Int = VERSION,
  val entries: List<MusicManifestEntry> = emptyList(),
) {

  companion object {
    const val VERSION = 1
  }
}
