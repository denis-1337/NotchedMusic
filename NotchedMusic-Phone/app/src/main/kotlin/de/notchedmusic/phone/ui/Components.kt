package de.notchedmusic.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.MutedGrey
import java.util.Locale

/** Abschnittsüberschrift im Stil der Mockups. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    modifier = modifier,
    color = MutedGrey,
    fontSize = 12.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.2.sp,
  )
}

/** Blauer Pill-Button mit zentriertem Text. */
@Composable
fun PillButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  color: Color = Blue,
  contentColor: Color = Color.White,
) {
  Box(
    modifier =
      modifier
        .height(48.dp)
        .background(color.copy(alpha = if (enabled) 1f else 0.35f), RoundedCornerShape(24.dp))
        .clickable(enabled = enabled) { onClick() }
        .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      color = contentColor.copy(alpha = if (enabled) 1f else 0.6f),
      fontWeight = FontWeight.Bold,
      fontSize = 15.sp,
    )
  }
}

/** Kleine Zeile aus Label und Wert, wie in der Diagnose. */
@Composable
fun InfoRow(label: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, color = MutedGrey, fontSize = 13.sp)
    Spacer(Modifier.width(12.dp))
    Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
  }
}

fun formatBytes(bytes: Long): String {
  if (bytes <= 0) return "0 MB"
  val mb = bytes / (1024.0 * 1024.0)
  return if (mb < 1.0) {
    "%.0f KB".format(Locale.ENGLISH, bytes / 1024.0)
  } else {
    "%.1f MB".format(Locale.ENGLISH, mb)
  }
}

fun formatDuration(durationMs: Long): String {
  if (durationMs <= 0) return "--:--"
  val totalSeconds = durationMs / 1000
  val minutes = totalSeconds / 60
  val seconds = totalSeconds % 60
  return "%d:%02d".format(minutes, seconds)
}
