package de.notchedmusic.watch.playback

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.notchedmusic.watch.WatchApplication
import de.notchedmusic.watch.data.SyncEmpfang
import de.notchedmusic.watch.data.WearDataCleanup
import de.notchedmusic.watch.protocol.SyncAck
import de.notchedmusic.watch.protocol.SyncKinds
import de.notchedmusic.watch.protocol.SyncProtocol
import kotlinx.coroutines.launch

/**
 * Empfängt Filter-Presets von der **Phone-App** über die Wear Data Layer.
 *
 * Ungültige, zu große oder unbekannte Pakete werden geloggt, abgelehnt und bestätigt – der
 * bisherige Stand bleibt dabei unangetastet. Der Empfang ist atomar: entweder alle drei Presets
 * werden übernommen oder keine.
 */
class PhoneFilterReceiver : WearableListenerService() {

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
      val dataItem = event.dataItem
      val path = dataItem.uri.path ?: continue
      if (!path.startsWith(PATH_PROFILES)) continue

      val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
      val syncId = dataMap.getString(KEY_SYNC_ID) ?: ""
      val payload = dataMap.getString(KEY_PROFILES_JSON)
      val nodeId = dataItem.uri.host ?: ""

      if (payload == null) {
        reject(syncId, nodeId, SyncProtocol.ERROR_INVALID_PAYLOAD, "Paket ohne profiles_json")
        continue
      }
      if (payload.toByteArray(Charsets.UTF_8).size > SyncProtocol.MAX_JSON_BYTES) {
        reject(syncId, nodeId, SyncProtocol.ERROR_TOO_LARGE, "Preset-Paket zu groß")
        continue
      }
      val decoded =
        SyncProtocol.decode(payload)
          ?: run {
            reject(syncId, nodeId, SyncProtocol.ERROR_INVALID_PAYLOAD, "Ungültiges Preset-JSON")
            continue
          }
      if (decoded.version > SyncProtocol.VERSION) {
        reject(syncId, nodeId, SyncProtocol.ERROR_UNSUPPORTED_VERSION, "Version ${decoded.version}")
        continue
      }

      val validated =
        try {
          SyncProtocol.validate(decoded)
        } catch (exception: RuntimeException) {
          Log.w(TAG, "Validierung fehlgeschlagen", exception)
          reject(syncId, nodeId, SyncProtocol.ERROR_INTERNAL, "Validierung fehlgeschlagen")
          continue
        }

      val now = System.currentTimeMillis()
      FilterSettings.update { settings ->
        settings.withPresetsFrom(validated.presets, now)
      }
      ack(
        SyncAck(
          syncId = syncId,
          kind = SyncKinds.PRESETS,
          status = SyncProtocol.STATUS_APPLIED,
          appliedAtMs = now,
          presetCount = validated.presets.size,
          watchVersion = SyncProtocol.WATCH_VERSION,
        ),
        nodeId,
      )
    }
  }

  private fun reject(syncId: String, nodeId: String, error: String, logText: String) {
    Log.w(TAG, "$logText – abgelehnt.")
    FilterSettings.update { it.copy(lastSyncError = error) }
    ack(
      SyncAck(
        syncId = syncId,
        kind = SyncKinds.PRESETS,
        status = SyncProtocol.STATUS_REJECTED,
        appliedAtMs = System.currentTimeMillis(),
        watchVersion = SyncProtocol.WATCH_VERSION,
        error = error,
      ),
      nodeId,
    )
  }

  private fun ack(ack: SyncAck, nodeId: String) {
    if (nodeId.isBlank()) return
    val bytes = SyncProtocol.encodeAck(ack).toByteArray(Charsets.UTF_8)
    val client = Wearable.getMessageClient(applicationContext)
    client
      .sendMessage(nodeId, PATH_ACK, bytes)
      .addOnFailureListener { exception -> Log.w(TAG, "ACK nicht zugestellt", exception) }
  }

  companion object {
    /** Präfix, unter dem die Phone-App Presets schickt (Auto-ID am Ende). */
    const val PATH_PROFILES = "/filter/profiles"

    const val PATH_ACK = "/filter/ack"
    const val KEY_PROFILES_JSON = "profiles_json"
    const val KEY_SYNC_ID = "sync_id"

    private const val TAG = "PhoneFilterReceiver"
  }
}
