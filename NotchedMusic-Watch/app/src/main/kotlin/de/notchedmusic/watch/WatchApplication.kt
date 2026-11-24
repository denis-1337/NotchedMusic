package de.notchedmusic.watch

import android.app.Application
import de.notchedmusic.watch.data.SyncEmpfang
import de.notchedmusic.watch.stats.WatchStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Startet den Empfangsschalter der Uhr.
 *
 * Die Data-Layer-Empfänger stehen im Manifest auf `enabled="false"` und werden von
 * [SyncEmpfang] nur eingeschaltet, solange die App offen ist oder Daten eintreffen. Beim
 * Prozessstart gilt deshalb zuerst „aus" – so weckt die Uhr niemand, solange niemand sie benutzt.
 */
class WatchApplication : Application() {

  /** Lebt so lange wie der Prozess; Dienste dürfen ihre Arbeit nicht beim Unbind verlieren. */
  val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** Lokale EQ-Statistik; Dienst und Sync-Empfänger teilen dieselbe Instanz. */
  lateinit var stats: WatchStatsRepository
    private set

  override fun onCreate() {
    super.onCreate()
    stats = WatchStatsRepository(this)
    SyncEmpfang.install(this)
  }

  override fun onTerminate() {
    appScope.cancel()
    super.onTerminate()
  }
}
