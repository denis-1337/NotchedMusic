package de.notchedmusic.phone

import android.os.Build
import de.notchedmusic.phone.data.CoverRepository
import de.notchedmusic.phone.data.MusicScanner
import de.notchedmusic.phone.data.SafFolder
import de.notchedmusic.phone.data.SettingsRepository
import de.notchedmusic.phone.data.TrackMetadataLoader
import de.notchedmusic.phone.sync.ContentResolverAssetSource
import de.notchedmusic.phone.sync.PlayServicesWearGateway
import de.notchedmusic.phone.sync.SyncAckBus
import de.notchedmusic.phone.sync.SyncRepository
import de.notchedmusic.phone.sync.StatsReportBus
import de.notchedmusic.phone.sync.WearSyncTransport
import de.notchedmusic.phone.stats.StatsRepository
import de.notchedmusic.phone.playback.EqAudioProcessor
import de.notchedmusic.phone.playback.SineTonePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Einmalig erzeugte, geteilte Abhängigkeiten – Activity und Dienste nutzen dieselben Objekte. */
class AppGraph(application: android.app.Application) {

  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val settings = SettingsRepository(application, scope)
  val scanner = MusicScanner(application)
  val metadata = TrackMetadataLoader(application)
  val covers = CoverRepository(application)
  val saf = SafFolder(application)
  val sine = SineTonePlayer(scope)
  val eqProcessor = EqAudioProcessor()
  val stats = StatsRepository(application)

  val wearGateway = PlayServicesWearGateway(application)
  val syncAcks = SyncAckBus()
  val statsReports = StatsReportBus()
  private val assetSource = ContentResolverAssetSource(application)
  private val syncTransport =
    WearSyncTransport(
      gateway = wearGateway,
      acks = syncAcks,
      statsReports = statsReports,
      assets = assetSource,
      phoneName = Build.MODEL ?: "Pixel",
    )
  val sync = SyncRepository(scope, settings, syncTransport, stats)
}

/** Application-Klasse: Activity, Sync-Listener und Playback-Service teilen sich einen [AppGraph]. */
class NotchedMusicApp : android.app.Application() {
  lateinit var graph: AppGraph
    private set

  override fun onCreate() {
    super.onCreate()
    graph = AppGraph(this)
    registerWearCapability()
  }

  /**
   * Die Uhr fragt `de.notchedmusic.phone.sync` ab, um uns zu finden. Ohne diese Meldung läuft
   * ihre Anfrage ins Leere, egal wie gut der restliche Data-Layer-Kanal ist.
   */
  private fun registerWearCapability() {
    try {
      com.google.android.gms.wearable.Wearable.getCapabilityClient(this)
        .addLocalCapability(PHONE_SYNC_CAPABILITY)
        .addOnSuccessListener {
          android.util.Log.i("NotchedMusicApp", "Capability $PHONE_SYNC_CAPABILITY registriert")
        }
        .addOnFailureListener { ausnahme ->
          android.util.Log.w("NotchedMusicApp", "Capability $PHONE_SYNC_CAPABILITY nicht registriert", ausnahme)
        }
    } catch (ausnahme: Exception) {
      // Ohne Wear-Companion ist die Meldung wirkungslos; die App bleibt vollständig nutzbar.
      android.util.Log.w("NotchedMusicApp", "Wear-Capability nicht verfügbar", ausnahme)
    }
  }

  companion object {
    const val PHONE_SYNC_CAPABILITY = "de.notchedmusic.phone.sync"
  }
}
