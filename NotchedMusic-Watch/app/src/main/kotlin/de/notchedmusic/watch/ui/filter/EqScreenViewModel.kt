package de.notchedmusic.watch.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.playback.FilterSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Nur Auswahl und Geräte-Einstellungen.
 *
 * Presets werden ausschließlich auf dem Phone bearbeitet; die Uhr zeigt und wählt sie nur. Die
 * einzige Quelle der Wahrheit bleibt [FilterSettings].
 */
class EqScreenViewModel : ViewModel() {

  val settings: StateFlow<WatchSettings> =
    FilterSettings.settings.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
      FilterSettings.current(),
    )

  fun selectPreset(presetId: String?) {
    FilterSettings.update { it.selectPreset(presetId) }
  }

  fun cyclePreset() {
    FilterSettings.update { it.cyclePreset() }
  }

  fun setVolumeDb(volumeDb: Float) {
    FilterSettings.update { it.withVolumeDb(volumeDb) }
  }

  fun setKeepScreenOn(keepScreenOn: Boolean) {
    FilterSettings.update { it.copy(keepScreenOn = keepScreenOn) }
  }

  /** Knopf auf der Einstellungsseite: schmal → voll → aus → schmal. */
  fun cycleAmbientAnzeige() {
    FilterSettings.update { it.cycleAmbientAnzeige() }
  }

  private companion object {
    const val STOP_TIMEOUT_MILLIS = 5_000L
  }
}
