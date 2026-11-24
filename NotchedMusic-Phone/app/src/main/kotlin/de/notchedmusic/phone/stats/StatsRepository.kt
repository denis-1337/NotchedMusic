package de.notchedmusic.phone.stats

import android.content.Context
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import de.notchedmusic.core.protocol.StatsReport

/** Alles, was die Statistik-Seite anzeigt. */
data class StatsUiState(
  val loading: Boolean = false,
  val days: Map<String, Long> = emptyMap(),
  val lastWatchReportId: String? = null,
  val error: String? = null,
) {

  val totalMs: Long
    get() = days.values.sum()
}

/**
 * Verbindet den Hörzeit-Tracker mit der atomaren Tages-Datei.
 *
 * Das Journal wird bei jedem geschlossenen Segment synchron angehängt. Beim Öffnen der
 * Statistik-Seite wird das offene Segment geschlossen, das Journal verdichtet und erst danach
 * gekürzt. Die eigentliche Dateiarbeit läuft im IO-Kontext, der Tracker bleibt davon getrennt.
 */
class StatsRepository(context: Context) {

  private val zoneId = ZoneId.systemDefault()
  private val store = StatsStore(File(context.filesDir, DIRECTORY), zoneId)
  private val tracker = EqStatsTracker(store)

  private val _state = MutableStateFlow(StatsUiState())
  val state: StateFlow<StatsUiState> = _state.asStateFlow()

  private val mutex = Mutex()

  fun updatePlayback(playing: Boolean, trackId: String?) {
    tracker.updatePlayback(playing, trackId)
  }

  fun setEqOn(enabled: Boolean) {
    tracker.setEqOn(enabled)
  }

  /** Schließt das offene Segment; bei laufender EQ-Wiedergabe beginnt sofort ein neues. */
  fun flushActive() {
    tracker.flushActive()
  }

  /** Verdichtet Journal und Tages-Datei und lädt den Stand für die UI. */
  suspend fun refresh() {
    mutex.withLock {
      tracker.flushActive()
      _state.update { it.copy(loading = true, error = null) }
      try {
        val snapshot = withContext(Dispatchers.IO) { store.compact() }
        _state.update {
          it.copy(
            loading = false,
            days = snapshot.days,
            lastWatchReportId = snapshot.lastWatchReportId,
            error = null,
          )
        }
      } catch (error: Exception) {
        _state.update {
          it.copy(loading = false, error = "Statistics could not be read: ${error.message}")
        }
      }
    }
  }

  /**
   * Übernimmt die Tages-Summen der Uhr.
   *
   * Die Methode schreibt zuerst die Datei und meldet erst danach Erfolg. Nur dann darf der
   * Sync-Transport der Uhr das ACK schicken und die Uhr ihre Daten löschen.
   */
  suspend fun mergeWatchReport(report: StatsReport): StatsMergeResult {
    mutex.withLock {
      tracker.flushActive()
      val result =
        try {
          withContext(Dispatchers.IO) { store.mergeWatchReport(report) }
        } catch (error: Exception) {
          return StatsMergeResult(
            applied = false,
            error = "Watch statistics could not be saved: ${error.message}",
          )
        }
      try {
        val snapshot = withContext(Dispatchers.IO) { store.compact() }
        _state.update {
          it.copy(
            loading = false,
            days = snapshot.days,
            lastWatchReportId = snapshot.lastWatchReportId,
            error = null,
          )
        }
      } catch (_: Exception) {
        // Der Report ist bereits sicher gespeichert; nur die Anzeige konnte nicht nachziehen.
      }
      return result
    }
  }

  private companion object {
    const val DIRECTORY = "statistics"
  }
}
