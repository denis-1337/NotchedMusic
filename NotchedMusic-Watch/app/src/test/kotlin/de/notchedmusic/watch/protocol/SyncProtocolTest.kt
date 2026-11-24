package de.notchedmusic.watch.protocol

import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Uhr muss dieselben Envelopes lesen und schreiben wie die Phone-App. */
class SyncProtocolTest {

  @Test
  fun presetPayloadRoundtripMitDreiPresets() {
    val payload =
      PresetSyncPayload(
        version = SyncProtocol.VERSION,
        presets = DefaultPresets.three(),
        activePresetId = DefaultPresets.PRESET_2_ID,
      )

    assertEquals(payload, SyncProtocol.decode(SyncProtocol.encode(payload)))
  }

  @Test
  fun validateKlemmtAlleWerteUndLiefertSechsBaender() {
    val preset =
      EqPreset(
        id = "p1",
        name = "Test",
        preGainDb = 999.0,
        bands = listOf(FilterBand(FilterType.PEAKING, 99_999.0, 99.0, 99.0)),
      )

    val validated = SyncProtocol.validate(PresetSyncPayload(presets = listOf(preset)))

    assertEquals(3, validated.presets.size)
    assertEquals(EqPreset.MAX_PRE_GAIN_DB, validated.presets.first().preGainDb, 0.0)
    assertEquals(EqPreset.MAX_BANDS, validated.presets.first().bands.size)
  }

  @Test
  fun ackRoundtripMitIndexUndFehlern() {
    val ack =
      SyncAck(
        syncId = "run",
        kind = SyncKinds.MUSIC_FILE,
        status = SyncProtocol.STATUS_REJECTED,
        index = 4,
        error = SyncProtocol.ERROR_CHECKSUM_MISMATCH,
      )

    assertEquals(ack, SyncProtocol.decodeAck(SyncProtocol.encodeAck(ack)))
  }

  @Test
  fun manifestRoundtripUndIndexSchluessel() {
    val manifest =
      MusicManifest(
        entries =
          listOf(
            MusicManifestEntry("Album A", "01.mp3", 10L, "a".repeat(64)),
            MusicManifestEntry(null, "02.ogg", 20L, null),
          ),
      )

    val decoded = SyncProtocol.decodeManifest(SyncProtocol.encodeManifest(manifest))

    assertEquals(manifest, decoded)
    assertEquals("Album A\u000001.mp3", decoded?.entries?.first()?.key)
  }

  @Test
  fun kaputtePaketeWerdenZuNull() {
    assertNull(SyncProtocol.decode("kein json"))
    assertNull(SyncProtocol.decodeAck("kein json"))
    assertNull(SyncProtocol.decodeManifest("kein json"))
    assertNull(SyncProtocol.decodeCancel("kein json"))
  }

  @Test
  fun cancelRoundtrip() {
    val cancel = SyncCancel(syncId = "run", reason = "user")
    assertEquals(cancel, SyncProtocol.decodeCancel(SyncProtocol.encodeCancel(cancel)))
    assertTrue(SyncProtocol.decodeCancel(SyncProtocol.encodeCancel(cancel))?.syncId == "run")
  }
}
