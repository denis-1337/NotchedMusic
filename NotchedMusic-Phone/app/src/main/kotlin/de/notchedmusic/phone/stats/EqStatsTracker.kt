package de.notchedmusic.phone.stats

/**
 * Zählt die EQ-Hörzeit als geschlossene Segmente.
 *
 * Das Segment beginnt nur, wenn Musik läuft **und** der EQ nicht im Bypass ist. Bei Titelwechsel,
 * Pause, EQ-Umschaltung oder Service-Ende wird es geschlossen und ein neues begonnen. Die
 * Tageszerlegung passiert erst beim Verdichten; ein Segment über Mitternacht wird dort geteilt.
 */
internal class EqStatsTracker(
  private val store: StatsStore,
  private val clockMs: () -> Long = { System.currentTimeMillis() },
) {

  private var playing = false
  private var eqOn = false
  private var trackId: String? = null
  private var segmentStartMs: Long? = null

  @Synchronized
  fun updatePlayback(playing: Boolean, trackId: String?) {
    if (this.playing == playing && this.trackId == trackId) return
    val wasActive = active()
    if (wasActive) closeSegment()
    this.playing = playing
    this.trackId = trackId
    ensureActive()
  }

  @Synchronized
  fun setEqOn(enabled: Boolean) {
    if (eqOn == enabled) return
    val wasActive = active()
    if (wasActive) closeSegment()
    eqOn = enabled
    ensureActive()
  }

  /** Schließt das offene Segment und startet bei laufender EQ-Wiedergabe sofort ein neues. */
  @Synchronized
  fun flushActive() {
    closeSegment()
    ensureActive()
  }

  private fun active(): Boolean = playing && eqOn

  private fun closeSegment() {
    val start = segmentStartMs ?: return
    val end = clockMs()
    if (end > start) store.appendSegment(start, end)
    segmentStartMs = null
  }

  private fun ensureActive() {
    if (active() && segmentStartMs == null) segmentStartMs = clockMs()
  }
}
