package de.notchedmusic.watch.stats

import android.content.Context
import java.io.File
import java.time.ZoneId
import de.notchedmusic.watch.protocol.StatsReport

/**
 * Die lokale Statistik der Uhr.
 *
 * Sie zählt nur, solange Musik läuft und ein Preset aktiv ist. Beim Sync-Anruf des Phones wird
 * ein stabiler Report erzeugt; erst nach dem Phone-ACK werden die bestätigten Tages-Summen
 * abgezogen. Neue Segmente bleiben dabei erhalten.
 */
class WatchStatsRepository(context: Context) {

  private val store =
    WatchStatsStore(
      directory = File(context.filesDir, DIRECTORY),
      zoneId = ZoneId.systemDefault(),
    )
  private val tracker = WatchEqStatsTracker(store)

  fun updatePlayback(playing: Boolean, trackId: String?) {
    tracker.updatePlayback(playing, trackId)
  }

  fun setEqOn(enabled: Boolean) {
    tracker.setEqOn(enabled)
  }

  fun flushActive() {
    tracker.flushActive()
  }

  /** Baut den Report; ein noch offener Pending-Report bleibt unverändert. */
  fun buildReport(): StatsReport = store.buildReport()

  /** Entfernt den bestätigten Snapshot nach dem Phone-ACK. */
  fun acknowledge(reportId: String): Boolean = store.acknowledge(reportId)

  private companion object {
    const val DIRECTORY = "statistics"
  }
}
