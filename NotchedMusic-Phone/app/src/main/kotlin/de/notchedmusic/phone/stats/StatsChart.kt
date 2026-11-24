package de.notchedmusic.phone.stats

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** Zeiträume des kleinen Statistik-Charts. */
enum class StatsPeriod {
  WEEK,
  MONTH,
  YEAR,
}

data class StatsBar(
  val label: String,
  val activeMs: Long,
)

data class StatsChartData(
  val bars: List<StatsBar>,
  val totalMs: Long,
)

/**
 * Fachliche Aufteilung der Tages-Summen für die drei Chart-Modi.
 *
 * * Woche: Montag bis Sonntag, ein Balken pro Tag.
 * * Monat: Kalenderwochen des Monats, nur die Tage innerhalb des Monats zählen.
 * * Jahr: Januar bis Dezember, Summe je Monat.
 */
fun statsChartFor(
  days: Map<String, Long>,
  period: StatsPeriod,
  offset: Int,
  today: LocalDate,
): StatsChartData {
  val parsed = parsedDays(days)
  val bars =
    when (period) {
      StatsPeriod.WEEK -> weekBars(parsed, offset, today)
      StatsPeriod.MONTH -> monthBars(parsed, offset, today)
      StatsPeriod.YEAR -> yearBars(parsed, offset, today)
    }
  return StatsChartData(bars = bars, totalMs = bars.sumOf { it.activeMs })
}

/** Gesamtzeit je Kalendertag; Null-Tage zählen beim Durchschnitt mit. */
fun statsAveragePerDayMs(days: Map<String, Long>, today: LocalDate): Long {
  val entries =
    days.entries
      .mapNotNull { (date, activeMs) ->
        runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to activeMs.coerceAtLeast(0L) }
      }
      .filter { it.second > 0L }
  if (entries.isEmpty()) return 0L
  val totalMs = entries.sumOf { it.second }
  val first = entries.minOf { it.first }
  val calendarDays =
    if (today < first) 1L else ChronoUnit.DAYS.between(first, today) + 1L
  return totalMs / calendarDays
}

private fun parsedDays(days: Map<String, Long>): Map<LocalDate, Long> =
  days.entries.mapNotNull { (date, activeMs) ->
    if (activeMs <= 0L) return@mapNotNull null
    runCatching { LocalDate.parse(date) }.getOrNull()?.let { it to activeMs }
  }.toMap()

private fun weekBars(
  days: Map<LocalDate, Long>,
  offset: Int,
  today: LocalDate,
): List<StatsBar> {
  val monday = today.minusDays((today.dayOfWeek.value - 1).toLong()).plusWeeks(offset.toLong())
  val labels = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
  return List(7) { index ->
    val date = monday.plusDays(index.toLong())
    StatsBar(labels[index], days[date] ?: 0L)
  }
}

private fun monthBars(
  days: Map<LocalDate, Long>,
  offset: Int,
  today: LocalDate,
): List<StatsBar> {
  val month = YearMonth.from(today).plusMonths(offset.toLong())
  val grouped = LinkedHashMap<LocalDate, Long>()
  for (day in 1..month.lengthOfMonth()) {
    val date = month.atDay(day)
    val weekStart = date.minusDays((date.dayOfWeek.value - 1).toLong())
    grouped[weekStart] = (grouped[weekStart] ?: 0L) + (days[date] ?: 0L)
  }
  return grouped.entries.map { (weekStart, activeMs) ->
    val week = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
    StatsBar("KW $week", activeMs)
  }
}

private fun yearBars(
  days: Map<LocalDate, Long>,
  offset: Int,
  today: LocalDate,
): List<StatsBar> {
  val year = today.year + offset
  val labels =
    listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
  val sums = LongArray(12)
  for ((date, activeMs) in days) {
    if (date.year == year) sums[date.monthValue - 1] += activeMs
  }
  return List(12) { index -> StatsBar(labels[index], sums[index]) }
}
