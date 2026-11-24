package de.notchedmusic.phone.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.notchedmusic.core.model.AppSettings
import de.notchedmusic.core.model.ClipLevel
import de.notchedmusic.core.model.ClipMonitor
import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterBand
import de.notchedmusic.core.model.SettingsCodec
import de.notchedmusic.core.model.FilterType
import de.notchedmusic.core.model.RepeatMode
import de.notchedmusic.phone.AppGraph
import de.notchedmusic.phone.NotchedMusicApp
import de.notchedmusic.phone.data.FolderNode
import de.notchedmusic.phone.data.LibrarySnapshot
import de.notchedmusic.phone.data.LibraryTrack
import de.notchedmusic.phone.data.ScanResult
import de.notchedmusic.phone.playback.PlaybackEngine
import de.notchedmusic.phone.playback.PlaybackService
import de.notchedmusic.phone.playback.PlaybackUiState
import de.notchedmusic.phone.stats.StatsUiState
import de.notchedmusic.phone.sync.SyncUiState
import de.notchedmusic.phone.sync.tracksForSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bibliothekszustand für die Oberfläche. */
data class LibraryUiState(
  val scanning: Boolean = false,
  val snapshot: LibrarySnapshot = LibrarySnapshot.EMPTY,
  val error: String? = null,
)

/**
 * Ein ViewModel für den ganzen Verlauf.
 *
 * Es hält Bibliothek und Auswahl und reicht Einstellungen direkt an das [SettingsRepository]
 * weiter – die Oberfläche rendert aus den Flows, nicht aus einer zweiten Kopie des Zustands.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

  private val graph: AppGraph = (application as NotchedMusicApp).graph

  val settings: StateFlow<AppSettings> = graph.settings.state

  private val _library = MutableStateFlow(LibraryUiState())
  val library: StateFlow<LibraryUiState> = _library.asStateFlow()

  private val _selectedFolderKey = MutableStateFlow(ROOT_KEY)
  val selectedFolderKey: StateFlow<String> = _selectedFolderKey.asStateFlow()

  private val _activeTab = MutableStateFlow(0)
  val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

  private val _showSync = MutableStateFlow(false)
  val showSync: StateFlow<Boolean> = _showSync.asStateFlow()

  val playback: StateFlow<PlaybackUiState> = PlaybackEngine.state

  private val _currentTrack = MutableStateFlow<LibraryTrack?>(null)
  val currentTrack: StateFlow<LibraryTrack?> = _currentTrack.asStateFlow()

  private val _cover = MutableStateFlow<android.graphics.Bitmap?>(null)
  val cover: StateFlow<android.graphics.Bitmap?> = _cover.asStateFlow()

  private val _clipLevel = MutableStateFlow(ClipLevel.OK)
  val clipLevel: StateFlow<ClipLevel> = _clipLevel.asStateFlow()

  val sinePlaying: StateFlow<Boolean> = graph.sine.isPlaying

  val sync: StateFlow<SyncUiState> = graph.sync.state

  /** Verdichtete EQ-Statistik; wird beim Öffnen der Statistik-Seite aktualisiert. */
  val stats: StateFlow<StatsUiState> = graph.stats.state

  private val clipMonitor = ClipMonitor()
  private var scanJob: Job? = null
  private var trackJob: Job? = null

  private var observedRootUri: String? = null

  /** Läuft nur, solange die Player-Seite tatsächlich sichtbar ist. */
  private var clipJob: Job? = null

  init {
    PlaybackEngine.initialize(getApplication(), graph.eqProcessor)
    viewModelScope.launch { observeSettings() }
    viewModelScope.launch { observePlayback() }
  }

  // ── Bibliothek ────────────────────────────────────────────────────────────────

  private suspend fun observeSettings() {
    graph.settings.state.collect { settings ->
      val rootUri = settings.rootUri ?: return@collect
      if (rootUri == observedRootUri) return@collect
      observedRootUri = rootUri
      val uri = rootUri.toUri()
      if (!graph.saf.hasPersistedReadPermission(uri)) {
        _library.value = LibraryUiState(
          error = "Zugriff auf den Musikordner fehlt. Bitte im Setup erneut wählen.",
        )
      } else {
        scan()
      }
    }
  }

  fun scan() {
    val rootUri = settings.value.rootUri ?: run {
      _library.value = LibraryUiState(error = "Noch kein Musikordner gewählt.")
      return
    }
    scanJob?.cancel()
    scanJob =
      viewModelScope.launch {
        _library.value = _library.value.copy(scanning = true, error = null)
        when (val result = graph.scanner.scan(rootUri.toUri())) {
          is ScanResult.Success -> {
            _library.value = LibraryUiState(scanning = false, snapshot = result.snapshot)
            restoreSelection(result.snapshot)
          }
          is ScanResult.Failure -> {
            _library.value = LibraryUiState(scanning = false, error = result.message)
          }
        }
      }
  }

  /**
   * Übernimmt die SAF-Auswahl.
   *
   * Die persistierbare Leseberechtigung wird sofort gespeichert; die alte Freigabe wird
   * freigegeben, wenn der Root gewechselt hat.
   */
  fun onFolderPicked(uri: Uri?) {
    if (uri == null) return
    val previous = settings.value.rootUri?.let(Uri::parse)
    if (!graph.saf.takePersistableReadPermission(uri, previous)) {
      _library.value = _library.value.copy(error = "Die Ordnerfreigabe wurde nicht erteilt.")
      return
    }
    observedRootUri = uri.toString()
    graph.settings.setRootUri(uri.toString())
    // Neuer Musikordner: die alte Ordner-/Titelwahl gehört zu einem anderen Baum und wird verworfen.
    graph.settings.setLastPlayed(ROOT_KEY, null)
    _selectedFolderKey.value = ROOT_KEY
    scan()
  }

  /** Ordnerwechsel (Pfeile oder Auswahl-Sheet). */
  fun selectFolder(key: String) {
    val folder = _library.value.snapshot.folder(key) ?: return
    _selectedFolderKey.value = folder.key
    refreshQueueForSelectedFolder()
  }

  /** Nächster Ordner in der Liste; springt zyklisch. */
  fun nextFolder(direction: Int) {
    val folders = _library.value.snapshot.folders
    if (folders.isEmpty()) return
    val current = folders.indexOfFirst { it.key == _selectedFolderKey.value }.coerceAtLeast(0)
    val next = (current + direction).mod(folders.size)
    _selectedFolderKey.value = folders[next].key
    refreshQueueForSelectedFolder()
  }

  fun currentFolder(): FolderNode? {
    val snapshot = _library.value.snapshot
    return snapshot.folder(_selectedFolderKey.value) ?: snapshot.root
  }

  /**
   * Stellt nach dem Scan die zuletzt gespeicherte Ordner-/Titelwahl wieder her.
   *
   * Fehlt der Ordner (oder wurde der Musikordner neu gewählt), bleibt nur der Root übrig.
   * Fehlt der Titel, beginnt die Warteschlange wie bisher beim ersten Eintrag.
   */
  private fun restoreSelection(snapshot: LibrarySnapshot) {
    val savedFolderKey = settings.value.lastFolderKey?.takeIf { snapshot.folder(it) != null }
    _selectedFolderKey.value = savedFolderKey ?: ROOT_KEY
    refreshQueueForSelectedFolder(restoreDocumentId = settings.value.lastTrackDocumentId)
  }

  /**
   * Baut die Warteschlange des gewählten Ordners neu.
   *
   * [restoreDocumentId] wählt nach einem Neustart den gespeicherten Titel; beim normalen
   * Ordnerwechsel bleibt es beim ersten Eintrag. Die Auswahl wird anschließend festgehalten.
   */
  private fun refreshQueueForSelectedFolder(restoreDocumentId: String? = null) {
    val folder = currentFolder()
    val tracks = folder?.tracks.orEmpty()
    val startIndex =
      restoreDocumentId
        ?.let { documentId -> tracks.indexOfFirst { it.documentId == documentId }.takeIf { it >= 0 } }
        ?: 0
    val wasPlaying = playback.value.isPlaying
    val autoPlay = wasPlaying && tracks.isNotEmpty()
    if (autoPlay) ensurePlaybackService()
    PlaybackEngine.setQueue(tracks, startIndex = startIndex, autoPlay = autoPlay)
    val track = tracks.getOrNull(startIndex)
    _currentTrack.value = track
    refreshCoverFor(track)
    persistSelection(track)
  }

  /**
   * Hält Ordner und Titel für den nächsten App-Start fest.
   *
   * Schreibt nur bei echter Änderung, damit der laufende Titelwechsel nicht unnötig den
   * konflatierten DataStore-Schreibpfad anstößt.
   */
  private fun persistSelection(track: LibraryTrack?) {
    val folderKey = _selectedFolderKey.value
    val documentId = track?.documentId
    val current = settings.value
    if (current.lastFolderKey == folderKey && current.lastTrackDocumentId == documentId) return
    graph.settings.setLastPlayed(folderKey, documentId)
  }

  // ── Wiedergabe ────────────────────────────────────────────────────────────────

  fun togglePlayback() {
    // Der Dienst muss vor dem Start laufen, sonst greift beim Sperren kein Vordergrundschutz.
    ensurePlaybackService()
    PlaybackEngine.toggle()
    if (playback.value.errorMessage != null) PlaybackEngine.clearError()
  }

  private fun ensurePlaybackService() {
    try {
      PlaybackService.start(getApplication())
    } catch (_: IllegalStateException) {
      // Hintergrundstart verboten; die Activity holt den Dienst beim nächsten sichtbaren Start nach.
    }
  }

  fun nextTrack() {
    val folder = currentFolder() ?: return
    if (folder.tracks.isEmpty()) return
    val next = (playback.value.currentIndex + 1).mod(folder.tracks.size)
    PlaybackEngine.selectIndex(next)
    showTrack(folder.tracks[next])
  }

  fun previousTrack() {
    val folder = currentFolder() ?: return
    if (folder.tracks.isEmpty()) return
    val previous = (playback.value.currentIndex - 1).mod(folder.tracks.size)
    PlaybackEngine.selectIndex(previous)
    showTrack(folder.tracks[previous])
  }

  private suspend fun observePlayback() {
    PlaybackEngine.state.collect { state ->
      if (!state.isPlaying) clipMonitor.reset()
      val folder = currentFolder() ?: return@collect
      val track = folder.tracks.getOrNull(state.currentIndex) ?: return@collect
      if (track.documentId != _currentTrack.value?.documentId) {
        showTrack(track)
      }
    }
  }

  private fun showTrack(track: LibraryTrack) {
    persistSelection(track)
    _currentTrack.value = track
    refreshCoverFor(track)
    trackJob?.cancel()
    trackJob =
      viewModelScope.launch {
        val enriched = graph.metadata.load(track)
        if (_currentTrack.value?.documentId == track.documentId) {
          _currentTrack.value = enriched
          refreshCoverFor(enriched)
        }
      }
  }

  private var coverJob: Job? = null

  private fun refreshCoverFor(track: LibraryTrack?) {
    coverJob?.cancel()
    coverJob = null
    if (track == null) {
      _cover.value = null
      return
    }
    _cover.value = null
    coverJob =
      viewModelScope.launch {
        val bitmap = graph.covers.load(track)
        if (_currentTrack.value?.documentId == track.documentId) {
          _cover.value = bitmap
          PlaybackEngine.updateCurrentItem(track, bitmap)
        }
      }
  }

  private suspend fun pollClipLevel() {
    while (true) {
      delay(CLIP_POLL_MS)
      if (!playback.value.isPlaying) {
        _clipLevel.value = ClipLevel.OK
        continue
      }
      _clipLevel.value =
        clipMonitor.update(
          peakLinear = graph.eqProcessor.lastPeakLinear,
          limiterGain = graph.eqProcessor.lastLimiterGain,
          nowMs = SystemClock.uptimeMillis(),
        )
    }
  }

  /**
   * Clip-Anzeige an- bzw. ausstellen.
   *
   * Die Pegel-Spitze wird mit 10 Hz abgeholt – unnötig, wenn die Player-Seite nicht zu sehen ist
   * (anderer Tab, Bildschirm aus, App im Hintergrund). Der Sync im Hintergrund bleibt unberührt.
   */
  fun clipUeberwachung(an: Boolean) {
    if (an) {
      if (clipJob?.isActive == true) return
      clipJob = viewModelScope.launch { pollClipLevel() }
    } else {
      clipJob?.cancel()
      clipJob = null
      _clipLevel.value = ClipLevel.OK
    }
  }

  // ── Einstellungen ─────────────────────────────────────────────────────────────

  fun selectTab(index: Int) {
    _activeTab.value = index.coerceIn(0, 3)
  }

  fun openSync() {
    _showSync.value = true
  }

  fun closeSync() {
    _showSync.value = false
  }

  fun setEqBypass(bypass: Boolean) = graph.settings.setEqBypass(bypass)

  fun selectPreset(presetId: String) = graph.settings.setActivePreset(presetId)

  fun setPlayerVolumePercent(percent: Float) {
    val db = -60f * (1f - percent.coerceIn(0f, 100f) / 100f)
    graph.settings.setPlayerVolumeDb(db)
  }

  /** Ändert ein Band des aktiven Presets und schreibt alle drei Presets als einen Stand. */
  fun updateActiveBand(index: Int, transform: (FilterBand) -> FilterBand) {
    val settingsValue = settings.value
    val preset = settingsValue.activePreset()
    val bands = preset.normalizedBands().toMutableList()
    if (index !in bands.indices) return
    bands[index] = transform(bands[index])
    graph.settings.updatePreset(preset.copy(bands = bands))
  }

  fun updateActivePresetPreGain(preGainDb: Double) {
    val preset = settings.value.activePreset()
    graph.settings.updatePreset(preset.copy(preGainDb = preGainDb))
  }

  fun resetActivePreset() {
    val preset = settings.value.activePreset()
    graph.settings.updatePreset(de.notchedmusic.core.model.DefaultPresets.reset(preset))
  }

  fun copyPresetFrom(sourceId: String) {
    val settingsValue = settings.value
    val source = settingsValue.presets.firstOrNull { it.id == sourceId } ?: return
    val target = settingsValue.activePreset()
    graph.settings.updatePreset(
      target.copy(preGainDb = source.preGainDb, bands = source.normalizedBands()),
    )
  }

  fun setSineFrequency(hz: Int) {
    val clamped = hz.coerceIn(SINE_MIN_HZ, SINE_MAX_HZ)
    graph.settings.update { it.copy(sineFrequencyHz = clamped) }
    graph.sine.setFrequency(clamped)
  }

  fun nudgeSineFrequency(delta: Int) {
    setSineFrequency(settings.value.sineFrequencyHz + delta)
  }

  fun setSineVolumePercent(percent: Float) {
    val db = -60f * (1f - percent.coerceIn(0f, 100f) / 100f)
    graph.settings.update { it.copy(sineVolumeDb = db) }
    graph.sine.setVolumeDb(db)
  }

  fun toggleSine() {
    graph.sine.setFrequency(settings.value.sineFrequencyHz)
    graph.sine.setVolumeDb(settings.value.sineVolumeDb)
    graph.sine.toggle()
  }

  fun stopSine() = graph.sine.stop()

  fun setRepeatMode(mode: RepeatMode) = graph.settings.update { it.copy(repeatMode = mode) }

  fun setAutoAdvance(enabled: Boolean) = graph.settings.update { it.copy(autoAdvance = enabled) }

  // ── Synchronisation ───────────────────────────────────────────────────────────

  fun toggleSyncSelection(folderKey: String) =
    graph.sync.toggleSelection(folderKey, library.value.snapshot.folders)

  fun checkWatchConnection() = graph.sync.checkWatch()

  fun selectAllForSync() = graph.sync.selectAll(library.value.snapshot.folders)

  fun clearSyncSelection() = graph.sync.clearSelection()

  fun syncEqs() = graph.sync.syncEqs()

  /** Verdichtet Journal und Tages-Summen; die Statistik-Seite ruft das beim Öffnen auf. */
  fun refreshStatistics() {
    viewModelScope.launch { graph.stats.refresh() }
  }

  /** Holt die noch nicht übernommenen Tages-Summen von der Uhr. */
  fun syncStatistics() = graph.sync.syncStatistics()

  fun syncMusic() {
    val tracks = library.value.snapshot.tracksForSelection(sync.value.selection)
    graph.sync.syncMusic(tracks)
  }

  fun cancelSync() = graph.sync.cancel()

  /** Nur für Diagnose: kurzer, lesbarer Stand der Einstellungen. */
  fun settingsSummary(): String = SettingsCodec.encode(settings.value)

  fun filterTypeLabel(type: FilterType): String =
    when (type) {
      FilterType.OFF -> "Aus"
      FilterType.NOTCH -> "Notch"
      FilterType.PEAKING -> "Bell"
      FilterType.LOW_SHELF -> "Low-Shelf"
      FilterType.HIGH_SHELF -> "High-Shelf"
      FilterType.LOW_CUT -> "Lowcut"
      FilterType.HIGH_CUT -> "Highcut"
    }

  fun formatVolumePercent(db: Float): Int = ((db + 60f) / 60f * 100f).toInt().coerceIn(0, 100)

  fun activePreset(): EqPreset = settings.value.activePreset()

  fun foldersForSync(): List<FolderNode> = library.value.snapshot.folders

  private companion object {
    const val ROOT_KEY = "root"
    const val CLIP_POLL_MS = 100L
    const val SINE_MIN_HZ = 100
    const val SINE_MAX_HZ = 15_000
  }
}
