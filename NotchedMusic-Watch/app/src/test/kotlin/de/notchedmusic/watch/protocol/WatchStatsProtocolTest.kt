package de.notchedmusic.watch.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchStatsProtocolTest {

  @Test
  fun report_und_ack_roundtrip() {
    val report =
      StatsReport(
        reportId = "uhr-1",
        days = listOf(EqStatsDay("2026-11-18", 60_000L)),
      )

    assertEquals(report.reportId, StatsProtocol.decodeReport(StatsProtocol.encodeReport(report))?.reportId)
    assertEquals(60_000L, StatsProtocol.decodeReport(StatsProtocol.encodeReport(report))?.days?.single()?.activeMs)
  }

  @Test
  fun ungueltiger_report_und_ack_werden_abgelehnt() {
    assertNull(StatsProtocol.decodeReport(""))
    assertNull(StatsProtocol.decodeReport("{\"reportId\":\"\"}"))
    assertNull(StatsProtocol.decodeAck("kein json"))
  }

  @Test
  fun ack_roundtrip() {
    val ack = StatsAck(reportId = "uhr-1", status = StatsProtocol.STATUS_APPLIED, mergedDays = 1, mergedMs = 60_000L)
    assertEquals(ack, StatsProtocol.decodeAck(StatsProtocol.encodeAck(ack)))
  }
}
