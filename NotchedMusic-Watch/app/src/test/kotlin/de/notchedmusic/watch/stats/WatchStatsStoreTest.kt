package de.notchedmusic.watch.stats

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchStatsStoreTest {

  @get:Rule
  val folder = TemporaryFolder()

  private val zone = ZoneId.of("UTC")

  private fun store(now: () -> Long = { Instant.parse("2026-11-18T10:00:00Z").toEpochMilli() }) =
    WatchStatsStore(folder.root, zone, now)

  @Test
  fun report_bleibt_fuer_wiederholung_stabil() {
    val store = store()
    val start = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    store.appendSegment(start, start + 60L * 60L * 1000L)

    val first = store.buildReport()
    val second = store.buildReport()

    assertEquals(first.reportId, second.reportId)
    assertEquals(60L * 60L * 1000L, first.days.single().activeMs)
  }

  @Test
  fun ack_zieht_nur_den_bestaetigten_snapshot_ab() {
    val store = store()
    val start = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    store.appendSegment(start, start + 60L * 60L * 1000L)
    val report = store.buildReport()

    // Während des Syncs kommt ein weiteres Segment hinzu; es darf nicht mit gelöscht werden.
    store.appendSegment(start + 60L * 60L * 1000L, start + 180L * 60L * 1000L)
    assertTrue(store.acknowledge(report.reportId))

    val next = store.buildReport()
    assertNotEquals(report.reportId, next.reportId)
    assertEquals(120L * 60L * 1000L, next.days.single().activeMs)
  }

  @Test
  fun fremdes_ack_loescht_nichts() {
    val store = store()
    val start = Instant.parse("2026-11-18T10:00:00Z").toEpochMilli()
    store.appendSegment(start, start + 60L * 60L * 1000L)
    val report = store.buildReport()

    assertFalse(store.acknowledge("anderer-report"))
    assertEquals(report.reportId, store.buildReport().reportId)
  }

  @Test
  fun tag_ueber_mitternacht_wird_geteilt() {
    val store = store()
    val start = Instant.parse("2026-11-18T23:30:00Z").toEpochMilli()
    store.appendSegment(start, start + 60L * 60L * 1000L)

    val report = store.buildReport()

    assertEquals(2, report.days.size)
    assertEquals(30L * 60L * 1000L, report.days.first { it.date == "2026-11-18" }.activeMs)
    assertEquals(30L * 60L * 1000L, report.days.first { it.date == "2026-11-19" }.activeMs)
  }
}
