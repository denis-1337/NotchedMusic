package de.notchedmusic.core.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsProtocolTest {

  @Test
  fun report_roundtrip_behaelt_stabile_report_id_und_tage() {
    val report =
      StatsReport(
        reportId = "run-1",
        generatedAtMs = 1_700_000_000_000L,
        days = listOf(EqStatsDay("2026-11-18", 3_600_000L), EqStatsDay("2026-11-19", 2_400_000L)),
      )

    val decoded = StatsProtocol.decodeReport(StatsProtocol.encodeReport(report))

    assertEquals("run-1", decoded?.reportId)
    assertEquals(2, decoded?.days?.size)
    assertEquals(3_600_000L, decoded?.days?.first()?.activeMs)
  }

  @Test
  fun doppelte_tage_werden_zusammengefasst() {
    val report =
      StatsReport(
        reportId = "run-2",
        days =
          listOf(
            EqStatsDay("2026-11-18", 1_000L),
            EqStatsDay("2026-11-18", 2_000L),
          ),
      )

    val decoded = StatsProtocol.decodeReport(StatsProtocol.encodeReport(report))

    assertEquals(1, decoded?.days?.size)
    assertEquals(3_000L, decoded?.days?.single()?.activeMs)
  }

  @Test
  fun leerer_report_ist_gueltig_aber_ungueltige_tage_nicht() {
    assertTrue(StatsProtocol.decodeReport(StatsProtocol.encodeReport(StatsReport(reportId = "leer"))) != null)
    assertNull(
      StatsProtocol.decodeReport(
        StatsProtocol.encodeReport(
          StatsReport(reportId = "kaputt", days = listOf(EqStatsDay("2026-99-99", 1L))),
        ),
      ),
    )
  }

  @Test
  fun negative_tageswerte_werden_abgelehnt_zu_grosse_geklemmt() {
    assertNull(
      StatsProtocol.decodeReport(
        StatsProtocol.encodeReport(
          StatsReport(reportId = "neg", days = listOf(EqStatsDay("2026-11-18", -1L))),
        ),
      ),
    )
    val clamped =
      StatsProtocol.decodeReport(
        StatsProtocol.encodeReport(
          StatsReport(reportId = "zu-gross", days = listOf(EqStatsDay("2026-11-18", StatsProtocol.MAX_DAY_MS + 1L))),
        ),
      )
    assertEquals(StatsProtocol.MAX_DAY_MS, clamped?.days?.single()?.activeMs)
  }

  @Test
  fun leere_report_id_und_zu_grosse_pakete_sind_ungueltig() {
    assertNull(StatsProtocol.decodeReport(""))
    assertNull(StatsProtocol.decodeReport("kein json"))
    assertNull(StatsProtocol.decodeReport("{\"reportId\":\"\"}"))
    assertNull(StatsProtocol.decodeReport("{\"reportId\":\"zu-gross\",\"days\":[{\"date\":\"2026-11-18\",\"activeMs\":1}]}${" ".repeat(StatsProtocol.MAX_REPORT_BYTES)}"))
  }

  @Test
  fun ack_roundtrip() {
    val ack = StatsAck(reportId = "run-1", status = StatsProtocol.STATUS_APPLIED, mergedDays = 2, mergedMs = 6_000L)
    val decoded = StatsProtocol.decodeAck(StatsProtocol.encodeAck(ack))
    assertEquals(ack, decoded)
  }
}
