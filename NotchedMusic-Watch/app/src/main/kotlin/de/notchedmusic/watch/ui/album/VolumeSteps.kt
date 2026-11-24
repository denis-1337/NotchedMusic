package de.notchedmusic.watch.ui.album

/**
 * Bildet die Dreh-Bewegung der Krone auf Lautstärke-Stufen ab: **ein Raster = ein Dezibel**.
 *
 * Die Drehung kommt als Bruchteil eines Rasters herein; was noch keinen vollen Schritt ergibt,
 * bleibt als Rest erhalten und geht beim nächsten Dreh nicht verloren. Am Anschlag wird der Rest
 * dagegen verworfen: Dort muss die Gegenrichtung sofort wirken und nicht erst ins Leere drehen.
 *
 * Reine Rechnung ohne Compose und ohne Android – genau dieselbe Klasse prüft der JVM-Test.
 */
internal class VolumeSteps(
  private val minDb: Float = MIN_VOLUME_DB,
  private val maxDb: Float = MAX_VOLUME_DB,
) {

  /** Noch nicht verbrauchte Dreh-Bewegung, in Pixeln. */
  private var restPx = 0f

  /**
   * Verarbeitet eine Drehung und liefert den neuen Pegel.
   *
   * @param volumeDb bisheriger Pegel in dB
   * @param deltaPx Dreh-Bewegung in Pixeln; positiv ist lauter
   * @param stepPx ein Kronen-Raster in Pixeln
   * @return der neue, bereits auf [minDb] … [maxDb] geklemmte Pegel
   */
  fun advance(volumeDb: Float, deltaPx: Float, stepPx: Float): Float {
    // Ohne Raster gäbe es keine Stufen – dann bleibt der Pegel, wie er ist.
    if (stepPx <= 0f) return volumeDb.coerceIn(minDb, maxDb)

    restPx += deltaPx
    val steps = (restPx / stepPx).toInt()
    if (steps == 0) return volumeDb.coerceIn(minDb, maxDb)

    restPx -= steps * stepPx
    val target = volumeDb + steps
    val clamped = target.coerceIn(minDb, maxDb)

    // Am Anschlag bleibt nichts auf Vorrat, sonst käme der erste Schritt der Gegenrichtung
    // erst nach einem Raster an.
    if (clamped != target) restPx = 0f

    return clamped
  }

  private companion object {
    /** Dieselben Geräte-Grenzen wie [de.notchedmusic.watch.model.WatchSettings]. */
    const val MIN_VOLUME_DB = -60f
    const val MAX_VOLUME_DB = 0f
  }
}
