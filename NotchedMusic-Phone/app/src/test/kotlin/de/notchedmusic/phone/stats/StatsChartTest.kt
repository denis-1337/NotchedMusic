package de.notchedmusic.phone.stats

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsChartTest {

  @Test
  fun woche_hat_sieben_tagesbalken_montag_bis_sonntag() {
    val today = LocalDate.parse("2026-11-18")
    val days = mapOf("2026-11-16" to 3_600_000L, "2026-11-18" to 7_200_000L)

    val chart = statsChartFor(days, StatsPeriod.WEEK, offset = 0, today = today)

    assertEquals(7, chart.bars.size)
    assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), chart.bars.map { it.label })
    assertEquals(3_600_000L, chart.bars[0].activeMs)
    assertEquals(7_200_000L, chart.bars[2].activeMs)
    assertEquals(10_800_000L, chart.totalMs)
  }

  @Test
  fun monat_gruppiert_nur_die_tage_im_monat_in_kalenderwochen() {
    val today = LocalDate.parse("2026-11-18")
    val days =
      mapOf(
        "2026-11-01" to 1_000L,
        "2026-11-02" to 2_000L,
        "2026-11-08" to 3_000L,
      )

    val chart = statsChartFor(days, StatsPeriod.MONTH, offset = 0, today = today)

    assertTrue(chart.bars.isNotEmpty())
    assertEquals(6_000L, chart.totalMs)
    assertTrue(chart.bars.all { it.label.startsWith("KW ") })
    assertTrue(chart.bars.any { it.activeMs == 5_000L })
  }

  @Test
  fun jahr_hat_zwoelf_monatsbalken() {
    val today = LocalDate.parse("2026-11-18")
    val days = mapOf("2026-01-05" to 1_000L, "2026-02-06" to 2_000L, "2025-12-31" to 4_000L)

    val chart = statsChartFor(days, StatsPeriod.YEAR, offset = 0, today = today)

    assertEquals(12, chart.bars.size)
    assertEquals("Jan", chart.bars[0].label)
    assertEquals(1_000L, chart.bars[0].activeMs)
    assertEquals(2_000L, chart.bars[1].activeMs)
    assertEquals(3_000L, chart.totalMs)
  }

  @Test
  fun durchschnitt_zaehlt_kalendertage_ohne_eq_nutzung_mit() {
    val today = LocalDate.parse("2026-11-03")
    val days = mapOf("2026-11-01" to 3_600_000L, "2026-11-03" to 7_200_000L)

    assertEquals(3_600_000L, statsAveragePerDayMs(days, today))
  }

  @Test
  fun leere_statistik_hat_null_durchschnitt_und_leere_balken() {
    val today = LocalDate.parse("2026-11-18")
    assertEquals(0L, statsAveragePerDayMs(emptyMap(), today))
    assertEquals(0L, statsChartFor(emptyMap(), StatsPeriod.YEAR, 0, today).totalMs)
  }
}
