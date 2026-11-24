package de.notchedmusic.watch.model

import kotlinx.serialization.Serializable

/**
 * Alle Filterarten, die die Phone-App anbietet – die Uhr rechnet sie exakt mit denselben Grenzen.
 *
 * [OFF] ist ein vollwertiger Zustand: Das Band bleibt sichtbar und editierbar, wird aber nicht
 * gerechnet. [LOW_CUT] (Hochpass) und [HIGH_CUT] (Tiefpass) ignorieren `gainDb`, der Wert wird
 * trotzdem gespeichert, damit ein Typwechsel nichts verliert.
 */
@Serializable
enum class FilterType {
  OFF,
  NOTCH,
  PEAKING,
  LOW_SHELF,
  HIGH_SHELF,
  LOW_CUT,
  HIGH_CUT,
}

/**
 * Ein einzelnes Filterband eines Presets.
 *
 * `q` ist die Güte, nicht die Bandbreite: `bandbreite = frequencyHz / q`. Kleines Q heißt breit
 * und flach, großes Q schmal und steil. `gainDb` wirkt nur bei [FilterType.PEAKING],
 * [FilterType.LOW_SHELF] und [FilterType.HIGH_SHELF].
 *
 * `enabled = false` entspricht klanglich [FilterType.OFF]; das Band bleibt aber erhalten.
 */
@Serializable
data class FilterBand(
  val type: FilterType = FilterType.OFF,
  val frequencyHz: Double = 1_000.0,
  val q: Double = 1.0,
  val gainDb: Double = 0.0,
  val enabled: Boolean = true,
) {

  val usesGain: Boolean
    get() = type == FilterType.PEAKING || type == FilterType.LOW_SHELF || type == FilterType.HIGH_SHELF

  val effectiveType: FilterType
    get() = if (enabled) type else FilterType.OFF
}
