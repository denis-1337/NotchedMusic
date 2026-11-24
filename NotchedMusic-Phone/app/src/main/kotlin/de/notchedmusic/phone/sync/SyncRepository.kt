package de.notchedmusic.phone.sync

import de.notchedmusic.core.protocol.MusicUpload
import de.notchedmusic.core.protocol.PresetSyncPayload
import de.notchedmusic.core.protocol.SyncProtocol
import de.notchedmusic.phone.data.FolderNode
import de.notchedmusic.phone.data.LibrarySnapshot
import de.notchedmusic.phone.data.LibraryTrack
import de.notchedmusic.phone.data.SettingsSource
import de.notchedmusic.phone.data.TrackFormats
import de.notchedmusic.phone.stats.StatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Alles, was die Synchronize-Seite anzeigt. */
data class SyncUiState(
  val isRunning: Boolean = false,
  val operationLabel: String = "",
  val progress: SyncProgress? = null,
  val message: String? = null,
  val messageIsError: Boolean = false,
  val lastSyncMs: Long = 0L,
  val selection: Set<String> = emptySet(),
  val watchAvailable: Boolean? = null,
) {

  val rootSelected: Boolean
    get() = "root" in selection
}

/**
 * Steuert Auswahl und beide Sync-Läufe.
 *
 * Die Auswahl folgt dem Haken auf dem Root: Ist er gesetzt, gilt **alles**, und Unterordner sind
 * nicht mehr einzeln wählbar. Die effektive Menge ist die Vereinigung der übrigen Haken.
 */
class SyncRepository(
  private val scope: CoroutineScope,
  private val settingsRepository: SettingsSource,
  private val transport: SyncTransport,
  private val statsRepository: StatsRepository,
) {

  private val _state = MutableStateFlow(SyncUiState())
  val state: StateFlow<SyncUiState> = _state.asStateFlow()

  private var activeJob: Job? = null

  init {
    scope.launch {
      settingsRepository.state.collect { settings ->
        _state.update { state -> state.copy(lastSyncMs = settings.lastSyncMs) }
      }
    }
  }

  /** Schaltet einen Knoten um; Root und Unterordner schließen einander aus. */
  fun toggleSelection(folderKey: String, folders: List<FolderNode>) {
    _state.update { state ->
      val rootKey = folders.firstOrNull { it.isRoot }?.key ?: "root"
      val selection =
        when {
          folderKey == rootKey -> if (folderKey in state.selection) emptySet() else setOf(rootKey)
          folderKey in state.selection -> state.selection - folderKey
          else -> (state.selection - rootKey) + folderKey
        }
      state.copy(selection = selection)
    }
  }

  fun selectAll(folders: List<FolderNode>) {
    val rootKey = folders.firstOrNull { it.isRoot }?.key ?: "root"
    _state.update { it.copy(selection = setOf(rootKey)) }
  }

  fun clearSelection() {
    _state.update { it.copy(selection = emptySet()) }
  }

  fun checkWatch() {
    if (activeJob?.isActive == true) return
    scope.launch {
      val available = transport.isWatchAvailable()
      _state.update {
        it.copy(
          watchAvailable = available,
          message = if (available) "Watch found." else "No reachable watch found.",
          messageIsError = !available,
        )
      }
    }
  }

  /** Sendet alle drei Presets im v3-Format an die Uhr. */
  /** Holt die Statistik der Uhr, verdichtet sie und bestätigt erst nach dem erfolgreichen Schreiben. */
  fun syncStatistics() {
    if (activeJob?.isActive == true) return
    activeJob =
      scope.launch {
        _state.update {
          it.copy(
            isRunning = true,
            operationLabel = "Statistics",
            progress = null,
            message = null,
            messageIsError = false,
          )
        }
        try {
          if (!transport.isWatchAvailable()) {
            _state.update {
              it.copy(
                isRunning = false,
                message = "Watch not reachable. Check pairing and Bluetooth.",
                messageIsError = true,
                watchAvailable = false,
              )
            }
            return@launch
          }
          val request =
            transport.requestStatistics { progress ->
              _state.update { it.copy(progress = progress) }
            }
          when (request) {
            is StatsRequestResult.Failure -> finish(SyncResult.Failure(request.message), "")
            is StatsRequestResult.Cancelled -> finish(SyncResult.Cancelled, "")
            is StatsRequestResult.Success -> {
              val incoming = request.incoming
              val merge = statsRepository.mergeWatchReport(incoming.report)
              if (!merge.applied) {
                // Das Phone konnte die Daten nicht speichern: die Uhr behält sie.
                transport.acknowledgeStatistics(
                  incoming.nodeId,
                  incoming.report.reportId,
                  accepted = false,
                  itemUri = incoming.itemUri,
                )
                finish(
                  SyncResult.Failure(merge.error ?: "Watch statistics could not be saved."),
                  "",
                )
              } else {
                val acknowledged =
                  transport.acknowledgeStatistics(
                    incoming.nodeId,
                    incoming.report.reportId,
                    accepted = true,
                    itemUri = incoming.itemUri,
                  )
                val text =
                  if (merge.alreadyProcessed) {
                    "Watch statistics were already imported."
                  } else {
                    "Watch statistics imported: ${merge.mergedDays} days."
                  }
                finish(
                  SyncResult.Success,
                  if (acknowledged) text else "$text The watch has not received the confirmation yet.",
                )
              }
            }
          }
        } catch (error: CancellationException) {
          throw error
        } catch (error: Exception) {
          _state.update {
            it.copy(
              message = "Statistics sync failed: ${error.message}",
              messageIsError = true,
            )
          }
        } finally {
          _state.update { it.copy(isRunning = false) }
        }
      }
  }

  fun syncEqs() {
    if (activeJob?.isActive == true) return
    val settings = settingsRepository.state.value
    val payload =
      SyncProtocol.validate(
        PresetSyncPayload(
          version = SyncProtocol.VERSION,
          presets = settings.presets,
          activePresetId = settings.activePresetId,
        ),
      )
    activeJob =
      scope.launch {
        _state.update {
          it.copy(
            isRunning = true,
            operationLabel = "Presets",
            progress = null,
            message = null,
            messageIsError = false,
          )
        }
        try {
          if (!transport.isWatchAvailable()) {
            _state.update {
              it.copy(
                isRunning = false,
                message = "Watch not reachable. Check pairing and Bluetooth.",
                messageIsError = true,
                watchAvailable = false,
              )
            }
            return@launch
          }
          val result =
            transport.sendPresets(payload) { progress ->
              _state.update { it.copy(progress = progress) }
            }
          finish(result, "3 presets sent to the watch.")
        } finally {
          _state.update { it.copy(isRunning = false) }
        }
      }
  }

  /**
   * Sendet die ausgewählten Titel als Spiegel.
   *
   * Nicht ausgewählte oder auf dem Phone verschwundene Titel werden auf der Uhr **erst nach**
   * vollständigem Upload und bestätigtem Commit gelöscht.
   */
  fun syncMusic(tracks: List<LibraryTrack>) {
    if (activeJob?.isActive == true) return
    val unique = tracks.distinctBy { it.documentId }
    activeJob =
      scope.launch {
        _state.update {
          it.copy(
            isRunning = true,
            operationLabel = "Music",
            progress = null,
            message = null,
            messageIsError = false,
          )
        }
        try {
          if (unique.isEmpty()) {
            _state.update {
              it.copy(isRunning = false, message = "No tracks selected.", messageIsError = true)
            }
            return@launch
          }
          val invalid =
            unique.firstOrNull { track ->
              !TrackFormats.isSupported(track.displayName, track.mimeType) ||
                track.fileSizeBytes > SyncProtocol.MAX_MUSIC_FILE_BYTES
            }
          if (invalid != null) {
            val reason =
              if (invalid.fileSizeBytes > SyncProtocol.MAX_MUSIC_FILE_BYTES) {
                "larger than 40 MB"
              } else {
                "unsupported audio format"
              }
            _state.update {
              it.copy(
                isRunning = false,
                message = "${invalid.displayName}: $reason.",
                messageIsError = true,
              )
            }
            return@launch
          }
          if (!transport.isWatchAvailable()) {
            _state.update {
              it.copy(
                isRunning = false,
                message = "Watch not reachable. Check pairing and Bluetooth.",
                messageIsError = true,
                watchAvailable = false,
              )
            }
            return@launch
          }
          val uploads =
            unique.map { track ->
              MusicUpload(
                uri = track.uri.toString(),
                fileName = track.displayName,
                album = track.watchAlbum,
                sizeBytes = track.fileSizeBytes,
                mimeType = track.mimeType,
              )
            }
          val result =
            transport.sendMusic(uploads) { progress ->
              _state.update { it.copy(progress = progress) }
            }
          finish(result, "Music mirrored: ${unique.size} tracks.")
        } finally {
          _state.update { it.copy(isRunning = false) }
        }
      }
  }

  fun cancel() {
    activeJob?.cancel()
    scope.launch { transport.cancel() }
    _state.update {
      it.copy(
        isRunning = false,
        message = "Cancelled. The watch deletes nothing; files already transferred remain.",
        messageIsError = false,
      )
    }
  }

  /** Von der Uhr gemeldeter Abbruch (z. B. auf der anderen Seite). */
  fun onRemoteCancel() {
    activeJob?.cancel()
    _state.update {
      it.copy(
        isRunning = false,
        message = "The sync was cancelled by the watch.",
        messageIsError = true,
      )
    }
  }

  private fun finish(result: SyncResult, successMessage: String) {
    when (result) {
      is SyncResult.Success -> {
        val now = System.currentTimeMillis()
        settingsRepository.setLastSync(now)
        _state.update {
          it.copy(
            isRunning = false,
            message = successMessage,
            messageIsError = false,
            lastSyncMs = now,
          )
        }
      }
      is SyncResult.Failure -> {
        _state.update {
          it.copy(isRunning = false, message = result.message, messageIsError = true)
        }
      }
      is SyncResult.Cancelled -> {
        _state.update {
          it.copy(
            isRunning = false,
            message = "Cancelled. The watch deletes nothing.",
            messageIsError = false,
          )
        }
      }
    }
  }

}

/** Effektive Auswahl: Root = alles, sonst Vereinigung der Unterordner. */
fun LibrarySnapshot.tracksForSelection(selection: Set<String>): List<LibraryTrack> {
  val rootKey = root?.key ?: "root"
  if (rootKey in selection) return allTracks
  return tracksFor(selection)
}
