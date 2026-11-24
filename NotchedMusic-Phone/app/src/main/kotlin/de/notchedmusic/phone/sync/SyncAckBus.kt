package de.notchedmusic.phone.sync

import de.notchedmusic.core.protocol.SyncAck
import de.notchedmusic.core.protocol.SyncKinds
import de.notchedmusic.core.protocol.SyncProtocol
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred

/** Korrelationsschlüssel eines ACK: Lauf, Art und optional die Datei-Nummer. */
data class AckKey(
  val syncId: String,
  val kind: String,
  val index: Int? = null,
)

/**
 * Wartet auf die Bestätigung der Uhr.
 *
 * Der Data-Layer-Listener schreibt jede empfangene ACK-Nachricht hier ein; der Transport wartet
 * auf genau seinen Schlüssel. Damit gibt es weder Polling noch eine Chance, dass zwei gleichzeitige
 * Läufe die Antworten des anderen sehen.
 */
class SyncAckBus {

  private val waiters = ConcurrentHashMap<AckKey, CompletableDeferred<SyncAck>>()

  fun register(key: AckKey): CompletableDeferred<SyncAck> {
    val waiter = CompletableDeferred<SyncAck>()
    waiters[key] = waiter
    return waiter
  }

  fun unregister(key: AckKey) {
    waiters.remove(key)
  }

  /** Liefert die ACK als JSON; `true`, wenn ein Wartender sie angenommen hat. */
  fun deliver(json: String): Boolean {
    val ack = SyncProtocol.decodeAck(json) ?: return false
    val index =
      when (ack.kind) {
        SyncKinds.MUSIC_FILE -> ack.index
        else -> null
      }
    val key = AckKey(ack.syncId, ack.kind, index)
    val waiter = waiters.remove(key) ?: waiters.remove(AckKey(ack.syncId, ack.kind, null)) ?: return false
    waiter.complete(ack)
    return true
  }

  /** Bricht alle Wartenden ab; der Transport wertet das als Abbruch. */
  fun cancelAll() {
    val entries = waiters.entries.toList()
    waiters.clear()
    for ((key, waiter) in entries) {
      waiter.complete(
        SyncAck(
          syncId = key.syncId,
          kind = key.kind,
          status = SyncProtocol.STATUS_CANCELLED,
          index = key.index,
          watchVersion = SyncProtocol.WATCH_VERSION,
        ),
      )
    }
  }
}
