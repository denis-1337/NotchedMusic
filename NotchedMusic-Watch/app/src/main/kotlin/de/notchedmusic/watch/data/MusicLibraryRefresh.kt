package de.notchedmusic.watch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Prozessweites Refresh-Signal.
 *
 * Der Musik-Empfänger läuft auch ohne sichtbare UI. Statt eines Broadcasts oder eines zweiten
 * ViewModels meldet er hier einen Tick; die Albenliste hat diesen Strom abonniert und liest die
 * Bibliothek neu ein.
 */
object MusicLibraryRefresh {

  private val _ticks = MutableStateFlow(0L)
  val ticks: StateFlow<Long> = _ticks.asStateFlow()

  fun trigger() {
    _ticks.update { it + 1L }
  }
}
