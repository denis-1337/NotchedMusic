package de.notchedmusic.watch.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import de.notchedmusic.watch.WatchApplication
import de.notchedmusic.watch.protocol.StatsProtocol
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Uhr-Seite des Statistik-Syncs.
 *
 * Das Phone fragt per Nachricht an. Die Uhr erzeugt einen stabilen Report und schickt ihn als
 * DataItem zurück. Erst wenn das Phone den Report verarbeitet hat und per `/stats/ack`
 * bestätigt, werden die bestätigten lokalen Tages-Summen gelöscht.
 */
class StatisticsTransferReceiver : WearableListenerService() {

  private val scope
    get() = (application as WatchApplication).appScope

  private val stats
    get() = (application as WatchApplication).stats

  override fun onCreate() {
    super.onCreate()
    WearDataCleanup.schedule(this, scope)
  }

  override fun onMessageReceived(messageEvent: MessageEvent) {
    SyncEmpfang.datenEingegangen(this)
    when (messageEvent.path) {
      StatsProtocol.PATH_REQUEST -> handleRequest(messageEvent)
      StatsProtocol.PATH_ACK -> handleAck(messageEvent)
      else -> Log.w(TAG, "Unbekannter Statistik-Pfad: ${messageEvent.path}")
    }
  }

  private fun handleRequest(messageEvent: MessageEvent) {
    val requestId = String(messageEvent.data, Charsets.UTF_8)
    val nodeId = messageEvent.sourceNodeId
    if (requestId.isBlank() || nodeId.isBlank()) {
      Log.w(TAG, "Statistik-Anfrage ohne Request-ID oder Ziel")
      return
    }
    scope.launch {
      val report = stats.buildReport()
      val dataMap =
        DataMap().apply {
          putString(StatsProtocol.KEY_REPORT_JSON, StatsProtocol.encodeReport(report))
          putString(StatsProtocol.KEY_REQUEST_ID, requestId)
          putLong(StatsProtocol.KEY_SENT_AT_MS, System.currentTimeMillis())
        }
      val request =
        PutDataRequest.createWithAutoAppendedId(StatsProtocol.PATH_REPORT).apply {
          setData(dataMap.toByteArray())
          setUrgent()
        }
      try {
        Tasks.await(
          Wearable.getDataClient(applicationContext).putDataItem(request),
          30L,
          TimeUnit.SECONDS,
        )
      } catch (exception: Exception) {
        Log.w(TAG, "Statistik-Report nicht zugestellt", exception)
      }
    }
  }

  private fun handleAck(messageEvent: MessageEvent) {
    val text = String(messageEvent.data, Charsets.UTF_8)
    val ack = StatsProtocol.decodeAck(text)
    if (ack == null || !ack.isApplied || ack.reportId.isBlank()) {
      Log.w(TAG, "Statistik-ACK abgelehnt oder ungültig")
      return
    }
    val deleted = stats.acknowledge(ack.reportId)
    Log.i(TAG, "Statistik-Report ${ack.reportId} bestätigt; lokale Daten ${if (deleted) "gelöscht" else "nicht gefunden"}.")
  }

  private companion object {
    const val TAG = "StatisticsTransfer"
  }
}
