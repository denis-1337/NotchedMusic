package de.notchedmusic.phone.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.notchedmusic.core.protocol.StatsProtocol
import de.notchedmusic.phone.NotchedMusicApp
import kotlinx.coroutines.launch

/**
 * Phone-Seite des Data-Layer-Kanals.
 *
 * Nimmt ACKs für Presets und Musik entgegen, beantwortet die Preset-Anfrage der Uhr und
 * bearbeitet Abbrüche. Die Arbeit läuft im Prozess des Anwendungsobjekts; der Dienst hält keine
 * eigene Verbindung.
 */
class WearSyncListenerService : WearableListenerService() {

  private val graph get() = (application as NotchedMusicApp).graph

  override fun onDataChanged(dataEvents: DataEventBuffer) {
    for (event in dataEvents) {
      if (event.type != DataEvent.TYPE_CHANGED) continue
      val item = event.dataItem
      val path = item.uri.path ?: continue
      if (!path.startsWith(StatsProtocol.PATH_REPORT)) continue
      val dataMap = DataMapItem.fromDataItem(item).dataMap
      val requestId = dataMap.getString(StatsProtocol.KEY_REQUEST_ID) ?: ""
      val reportText = dataMap.getString(StatsProtocol.KEY_REPORT_JSON)
      val report = reportText?.let { StatsProtocol.decodeReport(it) }
      if (requestId.isBlank() || report == null) {
        Log.w(TAG, "Ungültiger Statistik-Report unter $path")
        continue
      }
      val nodeId = item.uri.host ?: ""
      val accepted =
        graph.statsReports.deliver(
          StatsIncoming(
            report = report,
            requestId = requestId,
            nodeId = nodeId,
            itemUri = item.uri.toString(),
          ),
        )
      Log.i(TAG, "Statistik-Report (${report.days.size} Tage) ${if (accepted) "zugeordnet" else "verwaist"}.")
    }
  }

  override fun onMessageReceived(messageEvent: MessageEvent) {
    val path = messageEvent.path
    when (path) {
      PATH_FILTER_ACK,
      PATH_MUSIC_ACK,
      PATH_MANIFEST_ACK,
      PATH_COMMIT_ACK -> {
        val accepted = graph.syncAcks.deliver(String(messageEvent.data, Charsets.UTF_8))
        Log.i(TAG, "$path empfangen (${if (accepted) "zugeordnet" else "verwaist"}).")
      }

      PATH_FILTER_REQUEST -> {
        // Die Uhr bittet um den aktuellen Stand; der Transport antwortet auf demselben Weg.
        graph.scope.launch { graph.sync.syncEqs() }
      }

      PATH_CANCEL -> graph.sync.onRemoteCancel()

      else -> Log.w(TAG, "Unbekannter Nachrichtenpfad: $path")
    }
  }

  override fun onCreate() {
    super.onCreate()
    // Der Dienst kann auch dann starten, wenn die Activity im Hintergrund liegt. Die Fähigkeit
    // muss deshalb hier ebenfalls gemeldet werden – sonst findet die Uhr das Phone nicht.
    Wearable.getCapabilityClient(this)
      .addLocalCapability(NotchedMusicApp.PHONE_SYNC_CAPABILITY)
      .addOnSuccessListener { Log.i(TAG, "Phone-Capability registriert") }
      .addOnFailureListener { ausnahme -> Log.w(TAG, "Phone-Capability nicht registriert", ausnahme) }
    graph.scope.launch {
      graph.wearGateway.deleteStaleDataItems("/music", STALE_ITEM_MS)
      graph.wearGateway.deleteStaleDataItems("/filter", STALE_ITEM_MS)
      graph.wearGateway.deleteStaleDataItems("/stats", STALE_ITEM_MS)
    }
  }

  private companion object {
    const val TAG = "WearSync"
    const val PATH_FILTER_ACK = "/filter/ack"
    const val PATH_MUSIC_ACK = "/music/ack"
    const val PATH_MANIFEST_ACK = "/music/manifest/ack"
    const val PATH_COMMIT_ACK = "/music/commit/ack"
    const val PATH_FILTER_REQUEST = "/filter/request"
    const val PATH_CANCEL = "/sync/cancel"

    /** DataItems älter als eine Stunde sind sicher verwaist. */
    const val STALE_ITEM_MS = 60L * 60L * 1000L
  }
}
