package de.notchedmusic.phone.sync

import de.notchedmusic.core.protocol.StatsReport
import kotlinx.coroutines.CompletableDeferred

/** Ein vom Phone empfangener Statistik-Report samt Ziel-Knoten und DataItem-URI. */
data class StatsIncoming(
  val report: StatsReport,
  val requestId: String,
  val nodeId: String,
  val itemUri: String,
)

/**
 * Korreliert die asynchron eintreffenden Statistik-Reports mit dem wartenden Sync-Lauf.
 *
 * Die Uhr schickt den Report als DataItem; der Phone-Listener reicht ihn über [deliver] hierher.
 * Ein Report ohne passende Anfrage wird nicht mehr verarbeitet, damit alte Pakete keinen neuen
 * Lauf verfälschen.
 */
class StatsReportBus {

  private val waiters = mutableMapOf<String, CompletableDeferred<StatsIncoming>>()

  @Synchronized
  fun register(requestId: String): CompletableDeferred<StatsIncoming> {
    val waiter = CompletableDeferred<StatsIncoming>()
    waiters[requestId] = waiter
    return waiter
  }

  @Synchronized
  fun unregister(requestId: String) {
    waiters.remove(requestId)
  }

  @Synchronized
  fun deliver(incoming: StatsIncoming): Boolean {
    val waiter = waiters.remove(incoming.requestId) ?: return false
    waiter.complete(incoming)
    return true
  }

  @Synchronized
  fun cancelAll() {
    waiters.values.forEach { it.cancel() }
    waiters.clear()
  }
}
