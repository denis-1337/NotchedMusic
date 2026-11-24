package de.notchedmusic.phone.sync

import de.notchedmusic.core.model.DefaultPresets
import de.notchedmusic.core.protocol.MusicUpload
import de.notchedmusic.core.protocol.EqStatsDay
import de.notchedmusic.core.protocol.PresetSyncPayload
import de.notchedmusic.core.protocol.StatsProtocol
import de.notchedmusic.core.protocol.StatsReport
import de.notchedmusic.core.protocol.SyncAck
import de.notchedmusic.core.protocol.SyncCancel
import de.notchedmusic.core.protocol.SyncFristen
import de.notchedmusic.core.protocol.SyncKinds
import de.notchedmusic.core.protocol.SyncProtocol
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der echte Transport läuft hier gegen ein Fake-Gateway: gleiche ACK-Korrelation, gleiche
 * Retry-Grenzen, aber ohne Play Services und ohne Warten auf echte Timeouts.
 */
class WearSyncTransportTest {

  @Test
  fun `presets gehen als v3-paket raus und werden bestaetigt`() = runTest {
    val bus = SyncAckBus()
    val gateway = FakeWearGateway(bus) { path, values ->
      if (path.startsWith(WearSyncTransport.PATH_PRESETS)) {
        val syncId = values[WearSyncTransport.KEY_SYNC_ID] as String
        bus.deliver(
          SyncProtocol.encodeAck(
            SyncAck(syncId = syncId, kind = SyncKinds.PRESETS, status = SyncProtocol.STATUS_APPLIED),
          ),
        )
      }
    }
    val transport = transport(bus, gateway)

    val result =
      transport.sendPresets(
        PresetSyncPayload(version = SyncProtocol.VERSION, presets = DefaultPresets.three(), activePresetId = "p1"),
      ) {}

    assertTrue(result is SyncResult.Success)
    val put = gateway.puts.single { it.path.startsWith(WearSyncTransport.PATH_PRESETS) }
    val decoded = SyncProtocol.decode(put.values[WearSyncTransport.KEY_PROFILES_JSON] as String)
    assertEquals(3, decoded?.presets?.size)
    assertEquals("p1", decoded?.activePresetId)
    assertTrue(gateway.deleted.any { it.contains(WearSyncTransport.PATH_PRESETS) })
  }

  @Test
  fun `abgelehnte presets werden nicht blind wiederholt`() = runTest {
    val bus = SyncAckBus()
    var presetPuts = 0
    val gateway = FakeWearGateway(bus) { path, values ->
      if (path.startsWith(WearSyncTransport.PATH_PRESETS)) {
        presetPuts++
        bus.deliver(
          SyncProtocol.encodeAck(
            SyncAck(
              syncId = values[WearSyncTransport.KEY_SYNC_ID] as String,
              kind = SyncKinds.PRESETS,
              status = SyncProtocol.STATUS_REJECTED,
              error = SyncProtocol.ERROR_UNSUPPORTED_VERSION,
            ),
          ),
        )
      }
    }
    val transport = transport(bus, gateway)

    val result = transport.sendPresets(PresetSyncPayload(presets = DefaultPresets.three())) {}

    assertTrue(result is SyncResult.Failure)
    assertEquals(1, presetPuts)
  }

  @Test
  fun `nur fehlende dateien werden hochgeladen - dann commit`() = runTest {
    val bus = SyncAckBus()
    val gateway =
      FakeWearGateway(bus) { path, values ->
        when {
          path.startsWith(WearSyncTransport.PATH_MANIFEST) -> {
            bus.deliver(
              SyncProtocol.encodeAck(
                SyncAck(
                  syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                  kind = SyncKinds.MUSIC_MANIFEST,
                  status = SyncProtocol.STATUS_APPLIED,
                  missingCount = 1,
                  presentIndexes = listOf(0),
                  missingIndexes = listOf(1),
                ),
              ),
            )
          }
          path.startsWith(WearSyncTransport.PATH_UPLOAD) -> {
            bus.deliver(
              SyncProtocol.encodeAck(
                SyncAck(
                  syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                  kind = SyncKinds.MUSIC_FILE,
                  status = SyncProtocol.STATUS_APPLIED,
                  index = values[WearSyncTransport.KEY_INDEX] as Int,
                ),
              ),
            )
          }
        }
      }
    val transport = transport(bus, gateway)

    val result =
      transport.sendMusic(
        listOf(
          upload("uri://one", "01.mp3", "Album A", 100L),
          upload("uri://two", "02.mp3", "Album A", 200L),
        ),
      ) {}

    assertTrue(result is SyncResult.Success)
    assertEquals(1, gateway.puts.count { it.path.startsWith(WearSyncTransport.PATH_UPLOAD) })
    assertEquals(1, gateway.messages.count { it.path == WearSyncTransport.PATH_COMMIT })
  }

  @Test
  fun `fehlerhafter upload verhindert den commit`() = runTest {
    val bus = SyncAckBus()
    val gateway =
      FakeWearGateway(bus) { path, values ->
        when {
          path.startsWith(WearSyncTransport.PATH_MANIFEST) -> {
            bus.deliver(
              SyncProtocol.encodeAck(
                SyncAck(
                  syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                  kind = SyncKinds.MUSIC_MANIFEST,
                  status = SyncProtocol.STATUS_APPLIED,
                  missingCount = 1,
                ),
              ),
            )
          }
          path.startsWith(WearSyncTransport.PATH_UPLOAD) -> {
            bus.deliver(
              SyncProtocol.encodeAck(
                SyncAck(
                  syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                  kind = SyncKinds.MUSIC_FILE,
                  status = SyncProtocol.STATUS_REJECTED,
                  index = values[WearSyncTransport.KEY_INDEX] as Int,
                  error = SyncProtocol.ERROR_CHECKSUM_MISMATCH,
                ),
              ),
            )
          }
        }
      }
    val transport = transport(bus, gateway)

    val result = transport.sendMusic(listOf(upload("uri://one", "01.mp3", "Album A", 100L))) {}

    assertTrue(result is SyncResult.Failure)
    assertTrue((result as SyncResult.Failure).message.contains("SHA-256"))
    assertTrue(gateway.messages.none { it.path == WearSyncTransport.PATH_COMMIT })
  }

  @Test
  fun `abgelehntes manifest verhindert uploads und commit`() = runTest {
    val bus = SyncAckBus()
    val gateway =
      FakeWearGateway(bus) { path, values ->
        if (path.startsWith(WearSyncTransport.PATH_MANIFEST)) {
          bus.deliver(
            SyncProtocol.encodeAck(
              SyncAck(
                syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                kind = SyncKinds.MUSIC_MANIFEST,
                status = SyncProtocol.STATUS_REJECTED,
                error = SyncProtocol.ERROR_INSUFFICIENT_SPACE,
              ),
            ),
          )
        }
      }
    val transport = transport(bus, gateway)

    val result = transport.sendMusic(listOf(upload("uri://one", "01.mp3", "Album A", 100L))) {}

    assertTrue(result is SyncResult.Failure)
    assertTrue(gateway.puts.none { it.path.startsWith(WearSyncTransport.PATH_UPLOAD) })
    assertTrue(gateway.messages.none { it.path == WearSyncTransport.PATH_COMMIT })
  }

  @Test
  fun `ohne uhr wird vor dem ersten paket abgebrochen`() = runTest {
    val bus = SyncAckBus()
    val gateway = FakeWearGateway(bus, reachable = false) { _, _ -> }
    val transport = transport(bus, gateway)

    val result = transport.sendMusic(listOf(upload("uri://one", "01.mp3", "Album A", 100L))) {}

    assertTrue(result is SyncResult.Failure)
    assertFalse(transport.isWatchAvailable())
    assertTrue(gateway.puts.isEmpty())
    assertTrue(gateway.messages.isEmpty())
  }

  @Test
  fun `die ack-bus-korrelation braucht lauf art und index`() {
    val bus = SyncAckBus()
    val waiter = bus.register(AckKey("run", SyncKinds.MUSIC_FILE, 3))

    assertFalse(
      bus.deliver(
        SyncProtocol.encodeAck(
          SyncAck(syncId = "run", kind = SyncKinds.MUSIC_FILE, status = SyncProtocol.STATUS_APPLIED, index = 2),
        ),
      ),
    )
    assertTrue(
      bus.deliver(
        SyncProtocol.encodeAck(
          SyncAck(syncId = "run", kind = SyncKinds.MUSIC_FILE, status = SyncProtocol.STATUS_APPLIED, index = 3),
        ),
      ),
    )
    assertTrue(waiter.isCompleted)
  }

  @Test
  fun `waehrend einer grossen Datei kommen Lebenszeichen, danach keine mehr`() = runBlocking {
    val bus = SyncAckBus()
    val gateway =
      FakeWearGateway(bus) { path, values ->
        when {
          path.startsWith(WearSyncTransport.PATH_MANIFEST) ->
            bus.deliver(
              SyncProtocol.encodeAck(
                SyncAck(
                  syncId = values[WearSyncTransport.KEY_SESSION_ID] as String,
                  kind = SyncKinds.MUSIC_MANIFEST,
                  status = SyncProtocol.STATUS_APPLIED,
                  missingCount = 1,
                  presentIndexes = emptyList(),
                  missingIndexes = listOf(0),
                ),
              ),
            )
          path.startsWith(WearSyncTransport.PATH_UPLOAD) -> {
            // Die Bruecke braucht fuer diese Datei lange: die Bestaetigung kommt erst spaeter.
            val syncId = values[WearSyncTransport.KEY_SESSION_ID] as String
            val index = values[WearSyncTransport.KEY_INDEX] as Int
            Thread {
              Thread.sleep(400)
              bus.deliver(
                SyncProtocol.encodeAck(
                  SyncAck(
                    syncId = syncId,
                    kind = SyncKinds.MUSIC_FILE,
                    status = SyncProtocol.STATUS_APPLIED,
                    index = index,
                  ),
                ),
              )
            }.start()
          }
        }
      }
    val transport = transport(bus, gateway, haltezeitMs = 50)

    val result = transport.sendMusic(listOf(upload("uri://one", "01.mp3", "Album A", 100L))) {}

    assertTrue(result is SyncResult.Success)
    val halte = gateway.messages.filter { it.path == SyncFristen.HALTEZEICHEN_PFAD }
    assertTrue("Lebenszeichen beim Transfer: ${halte.size}", halte.size >= 2)
    assertTrue(halte.all { it.nodeId == "watch-node" })

    // Nach dem Transfer darf nichts mehr weitergesendet werden - die Geraete duerfen schlafen.
    val nachTransfer = gateway.messages.count { it.path == SyncFristen.HALTEZEICHEN_PFAD }
    delay(250)
    assertEquals(nachTransfer, gateway.messages.count { it.path == SyncFristen.HALTEZEICHEN_PFAD })
  }

  @Test
  fun statistikanfrage_wird_mit_report_gemeldet_und_bestaetigt() = runTest {
    val reports = StatsReportBus()
    val gateway = FakeWearGateway(SyncAckBus(), statsReports = reports) { _, _ -> }
    val transport = transport(SyncAckBus(), gateway, statsReports = reports)

    val result = transport.requestStatistics { }

    assertTrue(result is StatsRequestResult.Success)
    val incoming = (result as StatsRequestResult.Success).incoming
    assertEquals("report-1", incoming.report.reportId)
    assertEquals(60_000L, incoming.report.days.single().activeMs)
    assertTrue(
      transport.acknowledgeStatistics(
        incoming.nodeId,
        incoming.report.reportId,
        accepted = true,
        itemUri = incoming.itemUri,
      ),
    )
    assertTrue(gateway.messages.any { it.path == StatsProtocol.PATH_ACK })
    assertTrue(gateway.deleted.any { it.contains(StatsProtocol.PATH_REPORT) })
  }

  private fun transport(
    bus: SyncAckBus,
    gateway: WearGateway,
    statsReports: StatsReportBus = StatsReportBus(),
    haltezeitMs: Long = SyncFristen.HALTEZEIT_MS,
  ): WearSyncTransport =
    WearSyncTransport(
      gateway = gateway,
      acks = bus,
      statsReports = statsReports,
      assets =
        object : AssetSource {
          override suspend fun prepare(uri: String): PreparedAsset =
            PreparedAsset(sizeBytes = 100L, sha256 = "a".repeat(64))
        },
      phoneName = "Pixel 10",
      haltezeitMs = haltezeitMs,
    )

  private fun upload(uri: String, name: String, album: String, size: Long): MusicUpload =
    MusicUpload(uri = uri, fileName = name, album = album, sizeBytes = size, mimeType = "audio/mpeg")

  private class FakeWearGateway(
    private val bus: SyncAckBus,
    private val reachable: Boolean = true,
    private val statsReports: StatsReportBus = StatsReportBus(),
    private val onPut: (String, Map<String, Any>) -> Unit,
  ) : WearGateway {

    data class Put(val path: String, val values: Map<String, Any>, val attachment: WearAttachment?)
    data class Message(val nodeId: String, val path: String, val data: ByteArray)

    val puts = mutableListOf<Put>()
    val messages = mutableListOf<Message>()
    val deleted = mutableListOf<String>()

    override suspend fun findNode(capability: String): String? = if (reachable) "watch-node" else null

    override suspend fun putDataItem(
      path: String,
      values: Map<String, Any>,
      attachment: WearAttachment?,
    ): String {
      val uri = "wear://watch-node$path/${puts.size}"
      puts += Put(path, values, attachment)
      onPut(path, values)
      return uri
    }

    override suspend fun deleteDataItem(uri: String) {
      deleted += uri
    }

    override suspend fun sendMessage(nodeId: String, path: String, data: ByteArray): Boolean {
      messages += Message(nodeId, path, data)
      if (path == StatsProtocol.PATH_REQUEST) {
        val requestId = String(data, Charsets.UTF_8)
        statsReports.deliver(
          StatsIncoming(
            report =
              StatsReport(
                reportId = "report-1",
                days = listOf(EqStatsDay("2026-11-18", 60_000L)),
              ),
            requestId = requestId,
            nodeId = "watch-node",
            itemUri = "wear://watch-node${StatsProtocol.PATH_REPORT}/1",
          ),
        )
      }
      if (path == WearSyncTransport.PATH_COMMIT) {
        val cancel = SyncProtocol.decodeCancel(String(data))
        bus.deliver(
          SyncProtocol.encodeAck(
            SyncAck(
              syncId = cancel?.syncId.orEmpty(),
              kind = SyncKinds.MUSIC_COMMIT,
              status = SyncProtocol.STATUS_APPLIED,
            ),
          ),
        )
      }
      return true
    }

    override suspend fun deleteStaleDataItems(pathPrefix: String, olderThanMs: Long) = Unit
  }
}
