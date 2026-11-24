package de.notchedmusic.phone.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.ui.PillButton
import de.notchedmusic.phone.ui.SectionLabel
import de.notchedmusic.phone.stats.StatsPeriod
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.DangerRed
import de.notchedmusic.phone.ui.theme.MutedGrey
import de.notchedmusic.phone.ui.theme.NightBlack
import de.notchedmusic.phone.ui.theme.SurfaceDark
import de.notchedmusic.phone.ui.theme.SurfaceRaised
import de.notchedmusic.phone.stats.StatsBar
import de.notchedmusic.phone.stats.statsAveragePerDayMs
import de.notchedmusic.phone.stats.statsChartFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Statistik-Seite: Gesamt-/Tageszeit, kleines Wochen-, Monats- und Jahres-Chart und der
 * Uhr-Sync. Beim Öffnen wird das Journal verdichtet; die Anzeige selbst liest nur den Stand.
 */
@Composable
fun StatsScreen(
  viewModel: AppViewModel,
  modifier: Modifier = Modifier,
) {
  val stats by viewModel.stats.collectAsStateWithLifecycle()
  val sync by viewModel.sync.collectAsStateWithLifecycle()
  var period by remember { mutableStateOf(StatsPeriod.WEEK) }
  var offset by remember { mutableIntStateOf(0) }
  val today = LocalDate.now()
  val chart = remember(stats.days, period, offset, today) { statsChartFor(stats.days, period, offset, today) }
  val averageMs = remember(stats.days, today) { statsAveragePerDayMs(stats.days, today) }
  val todayMs = stats.days[today.toString()] ?: 0L

  LaunchedEffect(Unit) {
    viewModel.refreshStatistics()
    viewModel.checkWatchConnection()
  }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .background(NightBlack)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
  ) {
    Text("STATISTICS", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(
      "EQ listening time on this phone – watch data is added during sync.",
      color = MutedGrey,
      fontSize = 11.sp,
      modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      StatCard("TOTAL", formatStatDuration(stats.totalMs), Modifier.weight(1f))
      StatCard("AVG / DAY", formatStatDuration(averageMs), Modifier.weight(1f))
      StatCard("TODAY", formatStatDuration(todayMs), Modifier.weight(1f))
    }

    Spacer(Modifier.height(18.dp))
    StatsPeriodSelector(
      period = period,
      onPeriod = { period = it; offset = 0 },
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArrowButton("‹") { offset = (offset - 1) }
      Text(
        periodLabel(period, offset, today),
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
      )
      ArrowButton("›") { offset = (offset + 1).coerceAtMost(0) }
    }

    Text(
      text = "${formatStatDuration(chart.totalMs)} in this period",
      color = MutedGrey,
      fontSize = 11.sp,
      modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
    )

    StatsChart(chart.bars)

    if (stats.loading) {
      Text("Compacting statistics…", color = MutedGrey, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }
    stats.error?.let { error ->
      Text(error, color = DangerRed, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel("WATCH SYNC")
    Spacer(Modifier.height(6.dp))
    Text(
      text =
        when (sync.watchAvailable) {
          true -> "Watch reachable. Watch statistics are deleted on the watch after import."
          false -> "Watch not reachable – please open the watch app."
          null -> "Looking for watch…"
        },
      color = if (sync.watchAvailable == false) DangerRed else MutedGrey,
      fontSize = 11.sp,
    )
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Find watch", color = Blue, fontSize = 11.sp, modifier = Modifier.clickable { viewModel.checkWatchConnection() })
      if (sync.isRunning && sync.operationLabel == "Statistics") {
        Text("Sync running…", color = MutedGrey, fontSize = 11.sp)
      }
    }

    if (sync.isRunning && sync.operationLabel == "Statistics") {
      LinearProgressIndicator(
        progress = { sync.progress?.fraction ?: 0f },
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        color = Blue,
      )
    }

    if (sync.watchAvailable == true) {
      PillButton(
        text = "SYNC WATCH STATISTICS",
        onClick = viewModel::syncStatistics,
        enabled = !sync.isRunning,
        modifier = Modifier.fillMaxWidth(),
      )
    }

    sync.message?.let { message ->
      if (sync.operationLabel == "Statistics") {
        Text(
          text = message,
          color = if (sync.messageIsError) DangerRed else Color.White,
          fontSize = 11.sp,
          modifier = Modifier.padding(top = 6.dp),
        )
      }
    }

    Text(
      "The watch only counts playing music with an active preset; pause and bypass do not count.",
      color = MutedGrey,
      fontSize = 10.sp,
      modifier = Modifier.padding(top = 10.dp, bottom = 20.dp),
    )
  }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(12.dp))
        .background(SurfaceDark)
        .padding(horizontal = 10.dp, vertical = 12.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(label, color = MutedGrey, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Text(
      value,
      color = Color.White,
      fontSize = 22.sp,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun StatsPeriodSelector(period: StatsPeriod, onPeriod: (StatsPeriod) -> Unit) {
  val labels = mapOf(StatsPeriod.WEEK to "WEEK", StatsPeriod.MONTH to "MONTH", StatsPeriod.YEAR to "YEAR")
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    StatsPeriod.entries.forEach { entry ->
      val selected = period == entry
      Box(
        modifier =
          Modifier
            .weight(1f)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Blue else SurfaceRaised)
            .clickable { onPeriod(entry) },
        contentAlignment = Alignment.Center,
      ) {
        Text(labels[entry] ?: entry.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun ArrowButton(label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier.size(38.dp).clip(CircleShape).background(SurfaceRaised).clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = Blue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun StatsChart(bars: List<StatsBar>) {
  val maxMs = bars.maxOfOrNull { it.activeMs } ?: 0L
  val barArea = 128.dp
  Row(
    modifier = Modifier.fillMaxWidth().height(barArea + 24.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      modifier = Modifier.width(48.dp).height(barArea).padding(end = 4.dp),
      horizontalAlignment = Alignment.End,
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(formatStatDuration(maxMs), color = MutedGrey, fontSize = 8.sp, maxLines = 1)
      Text(formatStatDuration(maxMs / 2L), color = MutedGrey, fontSize = 8.sp, maxLines = 1)
      Text("0", color = MutedGrey, fontSize = 8.sp, maxLines = 1)
    }
    bars.forEach { bar ->
      val fraction = if (maxMs <= 0L) 0f else (bar.activeMs.toFloat() / maxMs.toFloat()).coerceIn(0f, 1f)
      val height = if (bar.activeMs > 0L) maxOf(4.dp, (barArea * fraction)) else 2.dp
      Column(
        modifier = Modifier.weight(1f).fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(height)
              .background(if (bar.activeMs > 0L) Blue else SurfaceRaised, RoundedCornerShape(4.dp))
              .semantics { contentDescription = "${bar.label}: ${formatStatDuration(bar.activeMs)}" },
        )
        Text(
          bar.label,
          color = MutedGrey,
          fontSize = 9.sp,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}

private fun periodLabel(period: StatsPeriod, offset: Int, today: LocalDate): String =
  when (period) {
    StatsPeriod.WEEK -> {
      val monday = today.minusDays((today.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()).plusWeeks(offset.toLong())
      val week = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
      "Wk $week"
    }
    StatsPeriod.MONTH -> {
      val month = YearMonth.from(today).plusMonths(offset.toLong())
      "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}"
    }
    StatsPeriod.YEAR -> "${today.year + offset}"
  }

private fun formatStatDuration(activeMs: Long): String {
  if (activeMs <= 0L) return "0 min"
  val seconds = activeMs / 1_000L
  if (seconds < 60L) return "$seconds s"
  val minutes = activeMs / 60_000L
  if (minutes < 60L) return "$minutes min"
  val hours = minutes / 60.0
  return String.format(Locale.ENGLISH, "%.1f h", hours)
}
