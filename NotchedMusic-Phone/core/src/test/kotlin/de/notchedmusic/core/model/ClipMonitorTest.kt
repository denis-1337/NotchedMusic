package de.notchedmusic.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClipMonitorTest {

  @Test
  fun leiserPegelIstOk() {
    val monitor = ClipMonitor()
    assertEquals(ClipLevel.OK, monitor.update(peakLinear = 0.2f, limiterGain = 1f, nowMs = 0L))
  }

  @Test
  fun limiterEingriffZeigtLimiting() {
    val monitor = ClipMonitor()
    assertEquals(ClipLevel.LIMITING, monitor.update(peakLinear = 1f, limiterGain = 0.8f, nowMs = 0L))
  }

  @Test
  fun starkerEingriffZeigtClipping() {
    val monitor = ClipMonitor()
    assertEquals(ClipLevel.CLIPPING, monitor.update(peakLinear = 1f, limiterGain = 0.4f, nowMs = 0L))
  }

  @Test
  fun spitzeUeberNullDBFSZeigtClipping() {
    val monitor = ClipMonitor()
    assertEquals(ClipLevel.CLIPPING, monitor.update(peakLinear = 1.4f, limiterGain = 1f, nowMs = 0L))
  }

  @Test
  fun anzeigeHaeltKurzNach() {
    val monitor = ClipMonitor(holdMs = 400L)
    monitor.update(peakLinear = 1f, limiterGain = 0.4f, nowMs = 1_000L)
    assertEquals(ClipLevel.CLIPPING, monitor.update(peakLinear = 0.1f, limiterGain = 1f, nowMs = 1_300L))
    assertEquals(ClipLevel.OK, monitor.update(peakLinear = 0.1f, limiterGain = 1f, nowMs = 1_500L))
  }

  @Test
  fun resetLoeschtHalt() {
    val monitor = ClipMonitor()
    monitor.update(peakLinear = 2f, limiterGain = 1f, nowMs = 0L)
    monitor.reset()
    assertEquals(ClipLevel.OK, monitor.update(peakLinear = 0.1f, limiterGain = 1f, nowMs = 1L))
  }

  @Test
  fun clippingBleibtVorLimiting() {
    val monitor = ClipMonitor()
    val first = monitor.update(peakLinear = 1f, limiterGain = 0.8f, nowMs = 0L)
    val second = monitor.update(peakLinear = 1f, limiterGain = 0.3f, nowMs = 100L)
    assertTrue(first < second)
  }
}
