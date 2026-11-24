package de.notchedmusic.watch.stats

/** Zählt die EQ-Hörzeit der Uhr als geschlossene Segmente (gleiche Regeln wie auf dem Phone). */
internal class WatchEqStatsTracker(
  private val store: WatchStatsStore,
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
