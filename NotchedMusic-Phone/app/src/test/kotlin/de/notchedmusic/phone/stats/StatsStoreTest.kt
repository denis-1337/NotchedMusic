package de.notchedmusic.phone.stats

import de.notchedmusic.core.protocol.EqStatsDay
import de.notchedmusic.core.protocol.StatsReport
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StatsStoreTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val zone = ZoneId.of("UTC")

  private fun store() = StatsStore(folder.root, zone)

  @Test
  fun journal_wird_auf_tage_verteilt_und_verdichtet() {
    val store = store()
    val start = Instant.parse("2026-11-18T23:30:00Z").toEpochMilli()
    store.appendSegment(start, start + 60L * 60L * 1000L)

    val snapshot = store.compact()

    assertEquals(30L * 60L * 1000L, snapshot.days["2026-11-18"])
    assertEquals(30L * 60L * 1000L, snapshot.days["2026-11-19"])
  }

  @Test
  fun wiederholtes_verdichten_zaehlt_nicht_doppelt() {
    val store = store()
    val start = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    store.appendSegment(start, start + 90L * 60L * 1000L)

    val first = store.compact()
    val second = store.compact()

    assertEquals(90L * 60L * 1000L, first.days["2026-11-18"])
    assertEquals(first.days, second.days)
  }

  @Test
  fun uhr_report_wird_einmal_gezaehlt_und_dann_wiedererkannt() {
    val store = store()
    val report =
      StatsReport(
        reportId = "watch-1",
        days = listOf(EqStatsDay("2026-11-18", 30L * 60L * 1000L)),
      )

    val first = store.mergeWatchReport(report)
    val second = store.mergeWatchReport(report)

    assertTrue(first.applied)
    assertFalse(first.alreadyProcessed)
    assertEquals(1, first.mergedDays)
    assertTrue(second.alreadyProcessed)
    assertEquals(30L * 60L * 1000L, store.snapshot().days["2026-11-18"])
  }

  @Test
  fun tracker_zaehlt_nur_bei_aktiver_eq_wiedergabe_und_beim_titelwechsel() {
    var now = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    val store = store()
    val tracker = EqStatsTracker(store) { now }

    tracker.updatePlayback(playing = true, trackId = "a")
    tracker.setEqOn(true)
    now += 30_000L
    tracker.updatePlayback(playing = true, trackId = "b")
    now += 30_000L
    tracker.setEqOn(false)
    tracker.flushActive()

    val days = store.compact().days
    assertEquals(60_000L, days["2026-11-18"])
  }

  @Test
  fun pause_und_bypass_werden_nicht_gezaehlt() {
    var now = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    val store = store()
    val tracker = EqStatsTracker(store) { now }

    tracker.setEqOn(false)
    tracker.updatePlayback(playing = true, trackId = "a")
    now += 10_000L
    tracker.flushActive()
    assertEquals(emptyMap<String, Long>(), store.compact().days)

    tracker.setEqOn(true)
    tracker.updatePlayback(playing = false, trackId = "a")
    now += 10_000L
    tracker.flushActive()
    assertEquals(emptyMap<String, Long>(), store.compact().days)
  }
}
