package de.notchedmusic.watch.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.notchedmusic.watch.WatchApplication
import de.notchedmusic.watch.playback.FilterSettings
import de.notchedmusic.watch.protocol.MusicManifest
import de.notchedmusic.watch.protocol.MusicManifestEntry
import de.notchedmusic.watch.protocol.SyncAck
import de.notchedmusic.watch.protocol.SyncCancel
import de.notchedmusic.watch.protocol.SyncKinds
import de.notchedmusic.watch.protocol.SyncProtocol
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/**
 * Empfängt den Musik-**Spiegel** von der Phone-App.
 *
 * Drei Schritte, bewusst voneinander getrennt:
 *
 * 1. **Manifest** wird geprüft und als ausstehend gespeichert – noch wird nichts gelöscht.
 * 2. **Uploads** werden atomar über `.part` + Rename geschrieben, mit Größen-, SHA- und
 *    Speicherplatzprüfung.
 * 3. **Commit** löscht erst dann alles, was nicht im Manifest steht; Dateien außerhalb des
 *    App-Musikordners und Nicht-Musikdateien werden nie angefasst.
 */
class MusicTransferReceiver : WearableListenerService() {

  private val scope
    get() = (application as WatchApplication).appScope

  override fun onCreate() {
    super.onCreate()
    FilterSettings.attachStore(this)
    WearDataCleanup.schedule(this, scope)
  }

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    SyncEmpfang.datenEingegangen(this)
    for (event in dataEvents) {
      if (event.type != DataEvent.TYPE_CHANGED) continue
      val item = event.dataItem
      val path = item.uri.path ?: continue
      when {
        path.startsWith(PATH_MANIFEST) -> handleManifest(item, path)
        path.startsWith(PATH_UPLOAD) -> handleUpload(item)
      }
    }
  }

  override fun onMessageReceived(messageEvent: MessageEvent) {
    SyncEmpfang.datenEingegangen(this)
    val path = messageEvent.path ?: return
    when (path) {
      PATH_COMMIT -> handleCommit(messageEvent)
      PATH_CANCEL -> handleCancel(messageEvent)
      // Lebenszeichen des Phones während einer Spiegelung: oben wurde die Empfangszeit schon
      // verlängert, weiter braucht die Uhr nichts damit zu tun.
      PATH_KEEPALIVE -> Unit
    }
  }

  private fun handleManifest(item: com.google.android.gms.wearable.DataItem, path: String) {
    val dataMap = DataMapItem.fromDataItem(item).dataMap
    val nodeId = item.uri.host ?: ""
    val sessionId = dataMap.getString(KEY_SESSION_ID) ?: ""
    val json = dataMap.getString(KEY_MANIFEST_JSON)
    if (json == null) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_INVALID_PAYLOAD)
      return
    }
    if (json.toByteArray(Charsets.UTF_8).size > SyncProtocol.MAX_MANIFEST_BYTES) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_TOO_LARGE)
      return
    }
    val decoded =
      SyncProtocol.decodeManifest(json)
        ?: run {
          rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_INVALID_PAYLOAD)
          return
        }
    if (decoded.version > MusicManifest.VERSION) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_UNSUPPORTED_VERSION)
      return
    }
    if (sessionId.isBlank()) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_INVALID_PAYLOAD)
      return
    }

    val entries = mutableListOf<MusicManifestEntry>()
    val seen = mutableSetOf<String>()
    for (entry in decoded.entries) {
      if (entry.fileName.isBlank()) {
        rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_INVALID_PAYLOAD)
        return
      }
      if (entry.sizeBytes < 0L || entry.sizeBytes > SyncProtocol.MAX_MUSIC_FILE_BYTES) {
        rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_TOO_LARGE)
        return
      }
      val canonical = MusicMirror.canonicalize(entry)
      if (!seen.add(canonical.key)) {
        rejectAck(nodeId, sessionId, SyncKinds.MUSIC_MANIFEST, SyncProtocol.ERROR_INVALID_PAYLOAD)
        return
      }
      entries += canonical
    }

    val root = musicFolder()
    root.mkdirs()
    runBlocking {
      val check = MusicMirror.check(root, entries)
      val pending =
        PendingMusicManifest(
          sessionId = sessionId,
          manifest = MusicManifest(version = MusicManifest.VERSION, entries = entries),
          receivedAtMs = System.currentTimeMillis(),
        )
      MusicSyncStore.save(this@MusicTransferReceiver, pending)
      // Ab jetzt gilt die lange Frist: ein einzelner großer Titel darf Minuten brauchen.
      SyncEmpfang.sitzungStart(this@MusicTransferReceiver)
      val now = System.currentTimeMillis()
      val ack =
        SyncAck(
          syncId = sessionId,
          kind = SyncKinds.MUSIC_MANIFEST,
          status = SyncProtocol.STATUS_APPLIED,
          appliedAtMs = now,
          watchVersion = SyncProtocol.WATCH_VERSION,
          missingCount = check.missing.size,
          missingFiles = check.missing.map(MusicMirror::displayPath),
          presentFiles = check.present.map(MusicMirror::displayPath),
          missingIndexes =
            entries.mapIndexedNotNull { index, entry -> if (check.missing.any { it.key == entry.key }) index else null },
          presentIndexes =
            entries.mapIndexedNotNull { index, entry -> if (check.present.any { it.key == entry.key }) index else null },
          freeBytes = root.usableSpace,
        )
      sendAck(nodeId, ack)
    }
  }

  private fun handleUpload(item: com.google.android.gms.wearable.DataItem) {
    val nodeId = item.uri.host ?: ""
    // Nur Metadaten lesen; das Asset liegt separat im DataItem und wird über `audio_digest` adressiert.
    val dataMap = DataMap.fromByteArray(item.data ?: ByteArray(0))
    val sessionId = dataMap.getString(KEY_SESSION_ID) ?: ""
    val index = dataMap.getInt(KEY_INDEX, -1)
    val fileName = dataMap.getString(KEY_FILE_NAME) ?: ""
    val album = dataMap.getString(KEY_ALBUM)
    val sizeBytes = dataMap.getLong(KEY_SIZE_BYTES, -1L)
    val expectedSha = dataMap.getString(KEY_SHA256)
    val assetDigest = dataMap.getString(KEY_ASSET_DIGEST)
    val asset = assetDigest?.takeIf { it.isNotBlank() }?.let { Asset.createFromRef(it) }

    if (asset == null) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, SyncProtocol.ERROR_INVALID_PAYLOAD, index)
      return
    }
    val pending = MusicSyncStore.load(this)
    if (pending == null || pending.sessionId != sessionId) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, SyncProtocol.ERROR_SESSION_MISMATCH, index)
      return
    }
    val key = MusicMirror.keyFor(album, fileName)
    val entry = pending.manifest.entries.firstOrNull { it.key == key }
    if (entry == null) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, SyncProtocol.ERROR_INVALID_PAYLOAD, index)
      return
    }
    if (sizeBytes < 0L || sizeBytes > SyncProtocol.MAX_MUSIC_FILE_BYTES) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, SyncProtocol.ERROR_TOO_LARGE, index)
      return
    }

    runBlocking {
      val root = musicFolder()
      if (!MusicMirror.hasEnoughSpace(root, sizeBytes)) {
        rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, SyncProtocol.ERROR_INSUFFICIENT_SPACE, index)
        return@runBlocking
      }
      val target = MusicMirror.targetFile(root, entry.album, entry.fileName)
      val temporary = File(target.parentFile, "${target.name}.part")
      target.parentFile?.mkdirs()

      val result =
        try {
          val response =
            Tasks.await(
              Wearable.getDataClient(applicationContext).getFdForAsset(asset),
              ASSET_TIMEOUT_SECONDS,
              TimeUnit.SECONDS,
            )
          val input = response?.inputStream
          if (input == null) {
            UploadResult(false, SyncProtocol.ERROR_INVALID_PAYLOAD)
          } else {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
              FileOutputStream(temporary).use { sink ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                while (true) {
                  val read = source.read(buffer)
                  if (read < 0) break
                  digest.update(buffer, 0, read)
                  sink.write(buffer, 0, read)
                }
                sink.fd.sync()
              }
            }
            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            val sizeOk = temporary.length() == sizeBytes
            val shaOk = expectedSha == null || actualSha.equals(expectedSha, ignoreCase = true)
            if (!sizeOk || !shaOk) {
              UploadResult(false, SyncProtocol.ERROR_CHECKSUM_MISMATCH)
            } else {
              if (target.exists()) target.delete()
              val renamed =
                temporary.renameTo(target) ||
                  run {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                    true
                  }
              if (renamed) UploadResult(true, null)
              else UploadResult(false, SyncProtocol.ERROR_INTERNAL)
            }
          }
        } catch (exception: Exception) {
          Log.w(TAG, "Upload $fileName fehlgeschlagen", exception)
          UploadResult(false, SyncProtocol.ERROR_INTERNAL)
        } finally {
          temporary.delete()
        }

      if (result.ok) {
        sendAck(
          nodeId,
          SyncAck(
            syncId = sessionId,
            kind = SyncKinds.MUSIC_FILE,
            status = SyncProtocol.STATUS_APPLIED,
            appliedAtMs = System.currentTimeMillis(),
            watchVersion = SyncProtocol.WATCH_VERSION,
            index = index,
            freeBytes = root.usableSpace,
          ),
        )
      } else {
        rejectAck(nodeId, sessionId, SyncKinds.MUSIC_FILE, result.error ?: SyncProtocol.ERROR_INTERNAL, index)
      }
    }
  }

  private fun handleCommit(messageEvent: MessageEvent) {
    val nodeId = messageEvent.sourceNodeId ?: ""
    val body = String(messageEvent.data, Charsets.UTF_8)
    val cancel = SyncProtocol.decodeCancel(body)
    val sessionId = cancel?.syncId ?: ""
    val pending = MusicSyncStore.load(this)
    if (pending == null || pending.sessionId != sessionId) {
      rejectAck(nodeId, sessionId, SyncKinds.MUSIC_COMMIT, SyncProtocol.ERROR_SESSION_MISMATCH)
      return
    }

    runBlocking {
      val root = musicFolder()
      val check = MusicMirror.check(root, pending.manifest.entries)
      if (check.missing.isNotEmpty()) {
        val ack =
          SyncAck(
            syncId = sessionId,
            kind = SyncKinds.MUSIC_COMMIT,
            status = SyncProtocol.STATUS_REJECTED,
            appliedAtMs = System.currentTimeMillis(),
            watchVersion = SyncProtocol.WATCH_VERSION,
            error = SyncProtocol.ERROR_MISSING_FILE,
            missingCount = check.missing.size,
            missingFiles = check.missing.map(MusicMirror::displayPath),
            missingIndexes =
              pending.manifest.entries.mapIndexedNotNull { index, entry ->
                if (check.missing.any { it.key == entry.key }) index else null
              },
          )
        sendAck(nodeId, ack)
        return@runBlocking
      }

      val deletions = MusicMirror.deletionPlan(root, pending.manifest.entries)
      var deleted = 0
      for (file in deletions) {
        if (file.delete()) deleted++
      }
      MusicMirror.removeEmptyFolders(root)
      MusicMirror.removePartFiles(root)
      MusicSyncStore.clear(this@MusicTransferReceiver)
      SyncEmpfang.sitzungEnde(this@MusicTransferReceiver)

      val now = System.currentTimeMillis()
      FilterSettings.update { settings ->
        settings.copy(
          lastMusicUpdateMs = now,
          lastSyncError = null,
        )
      }
      MusicLibraryRefresh.trigger()

      sendAck(
        nodeId,
        SyncAck(
          syncId = sessionId,
          kind = SyncKinds.MUSIC_COMMIT,
          status = SyncProtocol.STATUS_APPLIED,
          appliedAtMs = now,
          watchVersion = SyncProtocol.WATCH_VERSION,
          deletedCount = deleted,
          freeBytes = root.usableSpace,
        ),
      )
    }
  }

  private fun handleCancel(messageEvent: MessageEvent) {
    val cancel = SyncProtocol.decodeCancel(String(messageEvent.data, Charsets.UTF_8))
    val pending = MusicSyncStore.load(this)
    if (pending != null && (cancel == null || cancel.syncId == pending.sessionId)) {
      MusicSyncStore.clear(this)
    }
    SyncEmpfang.sitzungEnde(this)
    MusicMirror.removePartFiles(musicFolder())
  }

  private fun rejectAck(
    nodeId: String,
    sessionId: String,
    kind: String,
    error: String,
    index: Int? = null,
  ) {
    Log.w(TAG, "Lehne $kind ab: $error (session=$sessionId, index=$index)")
    FilterSettings.update { it.copy(lastSyncError = error) }
    sendAck(
      nodeId,
      SyncAck(
        syncId = sessionId,
        kind = kind,
        status = SyncProtocol.STATUS_REJECTED,
        appliedAtMs = System.currentTimeMillis(),
        watchVersion = SyncProtocol.WATCH_VERSION,
        error = error,
        index = index,
      ),
    )
  }

  private fun sendAck(nodeId: String, ack: SyncAck) {
    if (nodeId.isBlank()) return
    val bytes = SyncProtocol.encodeAck(ack).toByteArray(Charsets.UTF_8)
    Wearable.getMessageClient(applicationContext)
      .sendMessage(nodeId, ackPath(ack.kind), bytes)
      .addOnFailureListener { exception -> Log.w(TAG, "ACK ${ack.kind} nicht zugestellt", exception) }
  }

  private fun ackPath(kind: String): String =
    when (kind) {
      SyncKinds.MUSIC_MANIFEST -> PATH_MANIFEST_ACK
      SyncKinds.MUSIC_COMMIT -> PATH_COMMIT_ACK
      else -> PATH_MUSIC_ACK
    }

  private data class UploadResult(val ok: Boolean, val error: String?)

  companion object {
    const val PATH_MANIFEST = "/music/manifest"
    const val PATH_UPLOAD = "/music/upload"
    const val PATH_COMMIT = "/music/commit"
    const val PATH_CANCEL = "/sync/cancel"

    /** Hält den Lauscher offen; das Phone schickt es alle zwei Minuten während einer Spiegelung. */
    const val PATH_KEEPALIVE = "/music/keepalive"

    const val PATH_MUSIC_ACK = "/music/ack"
    const val PATH_MANIFEST_ACK = "/music/manifest/ack"
    const val PATH_COMMIT_ACK = "/music/commit/ack"

    const val KEY_AUDIO = "audio"
    const val KEY_SESSION_ID = "session_id"
    const val KEY_INDEX = "index"
    const val KEY_TOTAL = "total"
    const val KEY_FILE_NAME = "file_name"
    const val KEY_ALBUM = "album"
    const val KEY_SIZE_BYTES = "size_bytes"
    const val KEY_SHA256 = "sha256"
    const val KEY_ASSET_DIGEST = "audio_digest"
    const val KEY_MANIFEST_JSON = "manifest_json"

    private const val TAG = "MusicTransfer"
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val ASSET_TIMEOUT_SECONDS = 60L

  }
}
