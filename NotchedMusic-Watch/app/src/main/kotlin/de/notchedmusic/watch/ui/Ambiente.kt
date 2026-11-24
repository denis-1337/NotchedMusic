package de.notchedmusic.watch.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import de.notchedmusic.watch.model.AmbientAnzeige

/**
 * Sagt, ob die Uhr gerade im **Ambient-Zustand** ist: Bildschirm gedimmt, Bedienung angehalten.
 *
 * Den Zustand liefert der `AmbientModeManager`, den die Activity in [de.notchedmusic.watch.MainActivity]
 * einmal bereitstellt. Fehlt er – Vorschau, Test, frühe Phase – gilt „nicht ambient", und alle
 * Seiten sehen aus wie immer. Das ist die sichere Richtung: Nie eine gedimmte Seite zeigen,
 * wo sie niemand erwartet.
 *
 * Die Eigenschaft hängt am Snapshot-System, deshalb genügt ein gewöhnlicher Aufruf im Composable:
 * Wechselt die Uhr zwischen hell und gedimmt, gilt dieser Teil der Oberfläche als geändert und
 * zeichnet sich neu.
 */
@Composable
fun imAmbientModus(): Boolean {
  val manager = LocalAmbientModeManager.current ?: return false
  return manager.currentAmbientMode is AmbientMode.Ambient
}

/**
 * Reine Entscheidung, damit sie testbar bleibt: Wann zeichnet die Spielseite die reduzierte
 * Ansicht?
 *
 * Nur bei Stufe [AmbientAnzeige.SCHMAL] **und** tatsächlich gedimmtem Bildschirm. Ohne
 * [LocalAmbientModeManager] (Stufe „aus", Vorschau, Test) gilt „nicht ambient".
 */
fun zeigtReduziert(anzeige: AmbientAnzeige, imAmbient: Boolean): Boolean =
  imAmbient && anzeige == AmbientAnzeige.SCHMAL
