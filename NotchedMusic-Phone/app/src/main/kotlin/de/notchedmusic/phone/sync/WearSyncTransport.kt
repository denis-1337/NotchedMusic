package de.notchedmusic.phone.sync

import de.notchedmusic.core.protocol.MusicManifest
import de.notchedmusic.core.protocol.MusicManifestEntry
import de.notchedmusic.core.protocol.MusicUpload
import de.notchedmusic.core.protocol.PresetSyncPayload
import de.notchedmusic.core.protocol.SyncAck
import de.notchedmusic.core.protocol.SyncCancel
import de.notchedmusic.core.protocol.SyncFristen
import de.notchedmusic.core.protocol.SyncKinds
import de.notchedmusic.core.protocol.SyncProtocol
import de.notchedmusic.core.protocol.StatsAck
import de.notchedmusic.core.protocol.StatsProtocol
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Der echte Transport über die Wear Data Layer.
 *
 * Ablauf:
 * 1. Capability suchen – ohne erreichbare Uhr wird gar nichts gesendet.
 * 2. Presets: DataItem + ACK, Frist 60 s, zwei Versuche.
 * 3. Musik: Manifest + ACK mit Ist-Liste → nur fehlende/geänderte Dateien → ACK pro Datei → erst
 *    dann Commit. Für die Wartezeit gibt es **keinen** Größen-Timeout: eine große Datei darf über
 *    die Bluetooth-Brücke Minuten brauchen. Gewartet wird, bis die Uhr antwortet oder abbricht;
 *    nur bei völliger Stille greift die Rückfall-Frist aus [SyncFristen]. Während die Sitzung
 *    sendet, hält ein Lebenszeichen ([SyncFristen.HALTEZEICHEN_PFAD]) den Empfänger der Uhr offen.
 *
 * DataItems werden als *urgent* geschrieben; Assets werden über ihren Play-Services-Digest
 * (`audio_digest`) adressiert, damit die Uhr nicht auf einen bereits abgelaufenen Asset-Handle
 * wartet.
 *
 * Vor dem Commit wird auf der Uhr nichts gelöscht. Bricht der Lauf ab, bleibt der alte Bestand.
 */
class WearSyncTransport(
  private val gateway: WearGateway,
  private val acks: SyncAckBus,
  private val statsReports: StatsReportBus,
  private val assets: AssetSource,
  private val phoneName: String,
  /** Abstand der Lebenszeichen; nur in Tests verkuerzt. */
  private val haltezeitMs: Long = SyncFristen.HALTEZEIT_MS,
) : SyncTransport {

  @Volatile private var currentSessionId: String? = null
  @Volatile private var watchNodeId: String? = null

  override suspend fun isWatchAvailable(): Boolean =
    gateway.findNode(WATCH_CAPABILITY) != null

  override suspend fun sendPresets(
    payload: PresetSyncPayload,
    onProgress: (SyncProgress) -> Unit,
  ): SyncResult {
    val validated = SyncProtocol.validate(payload)
    val syncId = UUID.randomUUID().toString()
    repeat(PRESET_ATTEMPTS) { attempt ->
      val key = AckKey(syncId, SyncKinds.PRESETS)
      val waiter = acks.register(key)
      var itemUri: String? = null
      try {
        itemUri =
          gateway.putDataItem(
            PATH_PRESETS,
            mapOf(
              KEY_PROFILES_JSON to SyncProtocol.encode(validated),
              KEY_SYNC_ID to syncId,
              KEY_SENT_AT_MS to System.currentTimeMillis(),
              KEY_PHONE_NAME to phoneName,
              KEY_PROTOCOL to SyncProtocol.VERSION,
            ),
          )
        onProgress(
          SyncProgress(
            label = "Presets",
            done = 0,
            total = validated.presets.size,
          ),
        )
        val ack = awaitAck(waiter, PRESET_ACK_TIMEOUT_MS)
        if (ack != null && ack.isApplied) {
          onProgress(
            SyncProgress(
              label = "Presets",
              done = validated.presets.size,
              total = validated.presets.size,
            ),
          )
          return SyncResult.Success
        }
        if (ack != null && ack.status == SyncProtocol.STATUS_CANCELLED) {
          return SyncResult.Cancelled
        }
        if (ack != null && !ack.isApplied) {
          return SyncResult.Failure(errorText(ack.error))
        }
      } finally {
        acks.unregister(key)
        itemUri?.let { gateway.deleteDataItem(it) }
      }
    }
    return SyncResult.Failure("The watch did not respond (timeout).")
  }

  override suspend fun sendMusic(
    items: List<MusicUpload>,
    onProgress: (SyncProgress) -> Unit,
  ): SyncResult {
    if (items.isEmpty()) return SyncResult.Failure("No tracks selected.")

    val node =
      gateway.findNode(WATCH_CAPABILITY)
        ?: return SyncResult.Failure("Watch not reachable. Check pairing and Bluetooth.")
    watchNodeId = node

    val prepared = mutableListOf<PreparedMusic>()
    val seen = mutableSetOf<String>()
    for (item in items) {
      val key = "${item.album.orEmpty()}\u0000${item.fileName}"
      if (!seen.add(key)) continue
      if (item.sizeBytes > SyncProtocol.MAX_MUSIC_FILE_BYTES) {
        return SyncResult.Failure("${item.fileName}: file is larger than 40 MB.")
      }
      val meta =
        try {
          assets.prepare(item.uri)
        } catch (error: AssetPreparationException) {
          return SyncResult.Failure("${item.fileName}: ${error.message}")
        }
      prepared += PreparedMusic(item, meta)
    }
    if (prepared.isEmpty()) return SyncResult.Failure("No transferable tracks.")

    val sessionId = UUID.randomUUID().toString()
    currentSessionId = sessionId
    val manifest =
      MusicManifest(
        entries =
          prepared.map { music ->
            MusicManifestEntry(
              album = music.upload.album,
              fileName = music.upload.fileName,
              sizeBytes = music.meta.sizeBytes,
              sha256 = music.meta.sha256,
            )
          },
      )
    val manifestJson = SyncProtocol.encodeManifest(manifest)
    if (manifestJson.toByteArray(Charsets.UTF_8).size > SyncProtocol.MAX_MANIFEST_BYTES) {
      return SyncResult.Failure("Selection too large: manifest exceeds 1 MiB.")
    }

    val bytesTotal = prepared.sumOf { it.meta.sizeBytes }
    var bytesDone = 0L

    val presentIndexes = mutableSetOf<Int>()
    var manifestItemUri: String? = null
    var manifestAccepted = false
    repeat(MANIFEST_ATTEMPTS) {
      if (manifestAccepted) return@repeat
      val key = AckKey(sessionId, SyncKinds.MUSIC_MANIFEST)
      val waiter = acks.register(key)
      try {
        manifestItemUri =
          gateway.putDataItem(
            PATH_MANIFEST,
            mapOf(
              KEY_MANIFEST_JSON to manifestJson,
              KEY_SESSION_ID to sessionId,
              KEY_SENT_AT_MS to System.currentTimeMillis(),
            ),
          )
        val ack = awaitAck(waiter, MANIFEST_ACK_TIMEOUT_MS)
        if (ack != null && ack.isApplied) {
          manifestAccepted = true
          presentIndexes.clear()
          val indexes = ack.presentIndexes
          if (indexes != null) {
            presentIndexes += indexes.filter { it in prepared.indices }
          } else if (ack.missingCount == 0) {
            presentIndexes += prepared.indices.toList()
          }
          // presentIndexes fehlt bei einem alten Watch-ACK: dann wird alles hochgeladen.
          return@repeat
        }
        if (ack != null && ack.status == SyncProtocol.STATUS_CANCELLED) {
          return SyncResult.Cancelled
        }
        if (ack != null && !ack.isApplied) {
          return SyncResult.Failure(errorText(ack.error))
        }
      } finally {
        acks.unregister(key)
        manifestItemUri?.let { gateway.deleteDataItem(it) }
      }
    }
    if (!manifestAccepted) return SyncResult.Failure("The watch did not confirm the manifest (fallback deadline).")

    // Ab jetzt sendet die Sitzung lange: das Lebenszeichen hält den Lauscher der Uhr offen, damit
    // ein großer Titel mitten im Transfer nicht zugehält. Bei Erfolg, Fehler oder Abbruch ist es weg.
    val halteJob = CoroutineScope(coroutineContext).launch { haltezeichenSenden(node, sessionId) }
    try {
      for (index in prepared.indices) {
        if (index in presentIndexes) {
          bytesDone += prepared[index].meta.sizeBytes
          onProgress(progress(prepared, index, bytesDone, bytesTotal))
          continue
        }
        val music = prepared[index]
        var uploaded = false
        var lastError = "Transfer cancelled."
        repeat(FILE_ATTEMPTS) { attempt ->
          if (uploaded) return@repeat
          val key = AckKey(sessionId, SyncKinds.MUSIC_FILE, index)
          val waiter = acks.register(key)
          var itemUri: String? = null
          try {
            val values = mutableMapOf<String, Any>(
              KEY_SESSION_ID to sessionId,
              KEY_INDEX to index,
              KEY_TOTAL to prepared.size,
              KEY_FILE_NAME to music.upload.fileName,
              KEY_SIZE_BYTES to music.meta.sizeBytes,
              KEY_SHA256 to music.meta.sha256,
              KEY_MIME_TYPE to (music.upload.mimeType ?: "application/octet-stream"),
            )
            music.upload.album?.let { values[KEY_ALBUM] = it }
            itemUri =
              gateway.putDataItem(
                PATH_UPLOAD,
                values,
                WearAttachment(KEY_AUDIO, music.upload.uri),
              )
            val ack = awaitAck(waiter, FILE_ACK_TIMEOUT_MS)
            if (ack != null && ack.isApplied) {
              uploaded = true
              bytesDone += music.meta.sizeBytes
              onProgress(progress(prepared, index, bytesDone, bytesTotal))
              return@repeat
            }
            if (ack != null && ack.status == SyncProtocol.STATUS_CANCELLED) {
              return SyncResult.Cancelled
            }
            if (ack != null && !ack.isApplied) {
              lastError = errorText(ack.error)
              return SyncResult.Failure(lastError)
            }
          } finally {
            acks.unregister(key)
            itemUri?.let { gateway.deleteDataItem(it) }
          }
        }
        if (!uploaded) {
          return SyncResult.Failure("${music.upload.fileName}: $lastError")
        }
      }

      val commitAccepted = sendCommit(sessionId)
      return when (commitAccepted) {
        true -> SyncResult.Success
        false -> SyncResult.Failure("The watch did not confirm the music commit. Nothing was deleted.")
      }
    } finally {
      halteJob.cancel()
    }
  }

  /**
   * Schickt alle [SyncFristen.HALTEZEIT_MS] ein Lebenszeichen, solange die Sitzung läuft.
   *
   * Die Uhr verlängert damit ihre Empfangszeit. Hört das Phone auf (fertig, abgebrochen, Prozess
   * weg), bleibt nur die Rückfall-Frist der Uhr übrig – danach geht ihr Lauscher wieder zu.
   */
  private suspend fun haltezeichenSenden(node: String, sessionId: String) {
    try {
      while (true) {
        delay(haltezeitMs)
        withContext(Dispatchers.IO) {
          gateway.sendMessage(node, SyncFristen.HALTEZEICHEN_PFAD, sessionId.toByteArray(Charsets.UTF_8))
        }
      }
    } catch (_: CancellationException) {
      // Normalfall: die Sitzung ist zu Ende.
    } catch (fehler: Exception) {
      // Die Uhr ist nicht mehr erreichbar; die wartende ACK-Stelle läuft auf ihre Rückfall-Frist.
    }
  }

  override suspend fun requestStatistics(
    onProgress: (SyncProgress) -> Unit,
  ): StatsRequestResult {
    val node =
      gateway.findNode(WATCH_CAPABILITY)
        ?: return StatsRequestResult.Failure("Watch not reachable. Check pairing and Bluetooth.")
    val requestId = UUID.randomUUID().toString()
    val waiter = statsReports.register(requestId)
    try {
      onProgress(SyncProgress(label = "Statistics", done = 0, total = 1))
      val sent =
        withContext(Dispatchers.IO) {
          gateway.sendMessage(node, StatsProtocol.PATH_REQUEST, requestId.toByteArray(Charsets.UTF_8))
        }
      if (!sent) return StatsRequestResult.Failure("The watch did not accept the statistics request.")
      val incoming =
        withTimeoutOrNull(STATS_REPORT_TIMEOUT_MS) { waiter.await() }
          ?: return StatsRequestResult.Failure("The watch did not send statistics (timeout).")
      onProgress(SyncProgress(label = "Statistics", done = 1, total = 1))
      return StatsRequestResult.Success(incoming)
    } finally {
      statsReports.unregister(requestId)
    }
  }

  override suspend fun acknowledgeStatistics(
    nodeId: String,
    reportId: String,
    accepted: Boolean,
    itemUri: String,
  ): Boolean {
    val ack =
      StatsAck(
        reportId = reportId,
        status = if (accepted) StatsProtocol.STATUS_APPLIED else StatsProtocol.STATUS_REJECTED,
        appliedAtMs = System.currentTimeMillis(),
        error = if (accepted) null else StatsProtocol.STATUS_REJECTED,
      )
    val body = StatsProtocol.encodeAck(ack).toByteArray(Charsets.UTF_8)
    val sent =
      try {
        withContext(Dispatchers.IO) { gateway.sendMessage(nodeId, StatsProtocol.PATH_ACK, body) }
      } catch (_: Exception) {
        false
      }
    if (sent) {
      // Das Report-DataItem hat seinen Zweck erfüllt; die Uhr behält ihre Daten bis zum ACK.
      gateway.deleteDataItem(itemUri)
    }
    return sent
  }

  override suspend fun cancel() {
    acks.cancelAll()
    statsReports.cancelAll()
    val sessionId = currentSessionId
    val node = watchNodeId
    if (sessionId != null && node != null) {
      val body = SyncProtocol.encodeCancel(SyncCancel(syncId = sessionId, reason = "user")).toByteArray()
      withContext(Dispatchers.IO) { gateway.sendMessage(node, PATH_CANCEL, body) }
    }
    currentSessionId = null
  }

  private suspend fun sendCommit(sessionId: String): Boolean {
    val node = watchNodeId ?: return false
    repeat(COMMIT_ATTEMPTS) {
      val key = AckKey(sessionId, SyncKinds.MUSIC_COMMIT)
      val waiter = acks.register(key)
      try {
        val body =
          SyncProtocol.encodeCancel(SyncCancel(syncId = sessionId, reason = "commit")).toByteArray()
        gateway.sendMessage(node, PATH_COMMIT, body)
        val ack = awaitAck(waiter, COMMIT_ACK_TIMEOUT_MS)
        if (ack != null && ack.isApplied) return true
        if (ack != null && ack.status == SyncProtocol.STATUS_CANCELLED) return false
        if (ack != null && !ack.isApplied) return false
      } finally {
        acks.unregister(key)
      }
    }
    return false
  }

  private suspend fun awaitAck(waiter: CompletableDeferred<SyncAck>, timeoutMs: Long): SyncAck? =
    withTimeoutOrNull(timeoutMs) { waiter.await() }

  private fun progress(
    prepared: List<PreparedMusic>,
    index: Int,
    bytesDone: Long,
    bytesTotal: Long,
  ): SyncProgress =
    SyncProgress(
      label = prepared[index].upload.fileName,
      done = index + 1,
      total = prepared.size,
      bytesDone = bytesDone,
      bytesTotal = bytesTotal,
    )

  private fun errorText(error: String?): String =
    when (error) {
      SyncProtocol.ERROR_INVALID_PAYLOAD -> "The watch rejected the package (invalid data)."
      SyncProtocol.ERROR_TOO_LARGE -> "File or package is too large for the watch."
      SyncProtocol.ERROR_UNSUPPORTED_VERSION -> "The watch app is too old for this protocol."
      SyncProtocol.ERROR_INSUFFICIENT_SPACE -> "The watch has insufficient free storage."
      SyncProtocol.ERROR_SESSION_MISMATCH -> "The sync run does not match the watch. Please restart."
      SyncProtocol.ERROR_CHECKSUM_MISMATCH -> "The transfer was corrupted (SHA-256 mismatch)."
      SyncProtocol.ERROR_MISSING_FILE -> "Files are missing on the watch; nothing was deleted."
      SyncProtocol.ERROR_INTERNAL -> "Internal error on the watch."
      null -> "The watch rejected the sync."
      else -> "The watch reports: $error"
    }

  private data class PreparedMusic(val upload: MusicUpload, val meta: PreparedAsset)

  companion object {
    const val WATCH_CAPABILITY = "de.notchedmusic.watch.filter_profiles"

    const val PATH_PRESETS = "/filter/profiles"
    const val PATH_MANIFEST = "/music/manifest"
    const val PATH_UPLOAD = "/music/upload"
    const val PATH_COMMIT = "/music/commit"
    const val PATH_CANCEL = "/sync/cancel"

    const val KEY_PROFILES_JSON = "profiles_json"
    const val KEY_SYNC_ID = "sync_id"
    const val KEY_SENT_AT_MS = "sent_at_ms"
    const val KEY_PHONE_NAME = "phone_name"
    const val KEY_PROTOCOL = "protocol"

    const val KEY_MANIFEST_JSON = "manifest_json"
    const val KEY_SESSION_ID = "session_id"
    const val KEY_INDEX = "index"
    const val KEY_TOTAL = "total"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_SIZE_BYTES = "size_bytes"
    const val KEY_SHA256 = "sha256"
    const val KEY_MIME_TYPE = "mime_type"
    const val KEY_ALBUM = "album"
    const val KEY_AUDIO = "audio"

    // Rückfall-Fristen für Stille, keine Größen-Timeouts – Begründung siehe [SyncFristen].
    private val PRESET_ACK_TIMEOUT_MS = SyncFristen.PRESET_MS
    private val MANIFEST_ACK_TIMEOUT_MS = SyncFristen.MANIFEST_MS
    private val FILE_ACK_TIMEOUT_MS = SyncFristen.DATEI_MS
    private val COMMIT_ACK_TIMEOUT_MS = SyncFristen.COMMIT_MS

    private const val PRESET_ATTEMPTS = 2
    private const val MANIFEST_ATTEMPTS = 2
    private const val FILE_ATTEMPTS = 3
    private const val COMMIT_ATTEMPTS = 2
    private const val STATS_REPORT_TIMEOUT_MS = 60_000L
  }
}
