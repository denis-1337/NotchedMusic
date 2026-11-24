package de.notchedmusic.watch.ui

import de.notchedmusic.watch.model.WatchSettings
import kotlin.math.roundToInt

/**
 * Bildet den App-Hörpegel auf Reglerposition und Prozentanzeige ab.
 *
 * Die Richtung ist wie bei einem normalen Lautstärkeregler: links = stumm, rechts = voll.
 * Grundlage bleibt der negative dB-Wert von [WatchSettings] ( −60 dB … 0 dB ).
 */
internal object VolumeScale {

  /** Der gesamte Regelbereich in Dezibel, positiv ausgedrückt. */
  const val RANGE_DB: Float = WatchSettings.MAX_VOLUME_DB - WatchSettings.MIN_VOLUME_DB

  /**
   * Reglerposition für einen Hörpegel: `0` = stumm, `1` = voll.
   *
   * @param volumeDb App-Hörpegel in dB; außerhalb des Bereichs wird geklemmt
   */
  fun fraction(volumeDb: Float): Float =
    ((volumeDb - WatchSettings.MIN_VOLUME_DB) / RANGE_DB).coerceIn(0f, 1f)

  /**
   * Rechnet eine Reglerposition zurück in den App-Hörpegel.
   *
   * @param fraction Position von `0` (stumm) bis `1` (voll)
   * @return Pegel in dB innerhalb von [WatchSettings.MIN_VOLUME_DB] … [WatchSettings.MAX_VOLUME_DB]
   */
  fun volumeDb(fraction: Float): Float =
    WatchSettings.MIN_VOLUME_DB + fraction.coerceIn(0f, 1f) * RANGE_DB

  /**
   * Anzeigewert für die aktuelle Lautstärke: `0` = stumm, `100` = voll.
   *
   * @param volumeDb App-Hörpegel in dB
   */
  fun percent(volumeDb: Float): Int = (fraction(volumeDb) * 100f).roundToInt()
}
