package de.notchedmusic.phone.sync

import de.notchedmusic.core.protocol.MusicUpload
import de.notchedmusic.core.protocol.PresetSyncPayload

/** Fortschritt eines Sync-Laufs; Bytes sind Schätzungen aus den Dateigrößen. */
data class SyncProgress(
  val label: String,
  val done: Int,
  val total: Int,
  val bytesDone: Long = 0L,
  val bytesTotal: Long = 0L,
) {
  val fraction: Float
    get() = if (total <= 0) 0f else done.toFloat() / total
}

sealed interface SyncResult {
  data object Success : SyncResult
  data class Failure(val message: String) : SyncResult
  data object Cancelled : SyncResult
}

/** Ergebnis einer Statistik-Anfrage; der Report wird erst nach dem Phone-Commit bestätigt. */
sealed interface StatsRequestResult {
  data class Success(val incoming: StatsIncoming) : StatsRequestResult
  data class Failure(val message: String) : StatsRequestResult
  data object Cancelled : StatsRequestResult
}

/**
 * Die eine Schnittstelle, hinter der der Wear-Data-Layer-Transport hängt.
 *
 * [WearSyncTransport] ist die reale Umsetzung; für die JVM-Tests existiert dort ein Fake-Gateway.
 */
interface SyncTransport {
  suspend fun isWatchAvailable(): Boolean
  suspend fun sendPresets(
    payload: PresetSyncPayload,
    onProgress: (SyncProgress) -> Unit,
  ): SyncResult

  suspend fun sendMusic(
    items: List<MusicUpload>,
    onProgress: (SyncProgress) -> Unit,
  ): SyncResult

  /** Fordert die noch nicht übernommenen Statistik-Tages-Summen von der Uhr an. */
  suspend fun requestStatistics(onProgress: (SyncProgress) -> Unit): StatsRequestResult

  /** Bestätigt einen Report nach dem Speichern auf dem Phone; Grundlage für die Uhr-Löschung. */
  suspend fun acknowledgeStatistics(
    nodeId: String,
    reportId: String,
    accepted: Boolean,
    itemUri: String,
  ): Boolean

  suspend fun cancel()
}
