package de.notchedmusic.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Envelope-Test für ACK, Cancel und Musik-Manifest – die Gegenstücke auf beiden Geräten. */
class SyncEnvelopeTest {

  @Test
  fun ackRoundtripMitDiskretenFeldern() {
    val ack =
      SyncAck(
        syncId = "run-1",
        kind = SyncKinds.MUSIC_FILE,
        status = SyncProtocol.STATUS_APPLIED,
        appliedAtMs = 1_763_050_000_000L,
        index = 7,
        watchVersion = SyncProtocol.WATCH_VERSION,
        freeBytes = 512L * 1024L * 1024L,
      )

    val decoded = SyncProtocol.decodeAck(SyncProtocol.encodeAck(ack))

    assertEquals(ack, decoded)
    assertTrue(decoded?.isApplied == true)
  }

  @Test
  fun manifestAckTraegtIstUndSollListen() {
    val ack =
      SyncAck(
        syncId = "run-2",
        kind = SyncKinds.MUSIC_MANIFEST,
        status = SyncProtocol.STATUS_REJECTED,
        error = SyncProtocol.ERROR_INSUFFICIENT_SPACE,
        missingCount = 2,
        missingFiles = listOf("Album A/01.mp3", "Album B/02.flac"),
        missingIndexes = listOf(0, 1),
        presentFiles = listOf("Album A/03.ogg"),
        presentIndexes = listOf(2),
      )

    val decoded = SyncProtocol.decodeAck(SyncProtocol.encodeAck(ack))

    assertEquals(ack, decoded)
    assertFalse(decoded?.isApplied == true)
  }

  @Test
  fun kaputtesAckWirdZuNull() {
    assertNull(SyncProtocol.decodeAck(""))
    assertNull(SyncProtocol.decodeAck("kein json"))
    assertNull(SyncProtocol.decodeAck("""{"status":42}"""))
  }

  @Test
  fun cancelRoundtrip() {
    val cancel = SyncCancel(syncId = "run-3", reason = "user")
    assertEquals(cancel, SyncProtocol.decodeCancel(SyncProtocol.encodeCancel(cancel)))
  }

  @Test
  fun manifestRoundtripBewahrtReihenfolgeUndSchluessel() {
    val manifest =
      MusicManifest(
        entries =
          listOf(
            MusicManifestEntry("Album A", "01 - Morgen.mp3", 5_000_000L, "a".repeat(64)),
            MusicManifestEntry(null, "02 - Abend.ogg", 2_000_000L, null),
          ),
      )

    val decoded = SyncProtocol.decodeManifest(SyncProtocol.encodeManifest(manifest))

    assertEquals(manifest, decoded)
    assertEquals("Album A\u000001 - Morgen.mp3", decoded?.entries?.first()?.key)
  }

  @Test
  fun zuGrossesManifestWirdAbgelehnt() {
    val text = SyncProtocol.encodeManifest(MusicManifest()) + " ".repeat(SyncProtocol.MAX_MANIFEST_BYTES)
    assertNull(SyncProtocol.decodeManifest(text))
  }

  @Test
  fun leeresManifestBleibtGueltig() {
    assertEquals(MusicManifest(), SyncProtocol.decodeManifest(SyncProtocol.encodeManifest(MusicManifest())))
  }
}
