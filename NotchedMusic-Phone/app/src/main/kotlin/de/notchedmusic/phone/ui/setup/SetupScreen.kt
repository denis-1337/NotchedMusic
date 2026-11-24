package de.notchedmusic.phone.ui.setup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notchedmusic.phone.BuildConfig
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.ui.InfoRow
import de.notchedmusic.phone.ui.PillButton
import de.notchedmusic.phone.ui.SectionLabel
import de.notchedmusic.core.model.RepeatMode
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.DangerRed
import de.notchedmusic.phone.ui.theme.MutedGrey
import de.notchedmusic.phone.ui.theme.SurfaceRaised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Setup: Ordnereinstellung, Sinuston-Bestimmung und Diagnose. */
@Composable
fun SetupScreen(
  viewModel: AppViewModel,
  onPickFolder: () -> Unit,
  onSendToWatch: () -> Unit,
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val library by viewModel.library.collectAsStateWithLifecycle()
  val playback by viewModel.playback.collectAsStateWithLifecycle()
  val sinePlaying by viewModel.sinePlaying.collectAsStateWithLifecycle()
  val sync by viewModel.sync.collectAsStateWithLifecycle()

  val frequencyPercent = (settings.sineFrequencyHz - 100).toFloat()
  val sineVolumePercent = ((settings.sineVolumeDb + 60f) / 60f * 100f).coerceIn(0f, 100f)

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
  ) {
    Text("SETUP", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    PillButton("Send to Watch", onSendToWatch, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text("Öffnet die Synchronize-Seite.", color = MutedGrey, fontSize = 11.sp)

    Spacer(Modifier.height(20.dp))
    SectionLabel("MUSIKORDNER")
    Spacer(Modifier.height(6.dp))
    PillButton(
      text = "Select Root Folder",
      onClick = onPickFolder,
      color = SurfaceRaised,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    SectionLabel("ROOT FOLDER PATH")
    Text(
      text = settings.rootUri ?: "Noch kein Musikordner gewählt.",
      color = if (library.error != null) DangerRed else Color.White,
      fontSize = 12.sp,
      maxLines = 3,
      overflow = TextOverflow.Ellipsis,
    )
    val libraryError = library.error
    if (libraryError != null) {
      Text(libraryError, color = DangerRed, fontSize = 11.sp)
      Spacer(Modifier.height(6.dp))
      PillButton("Erneut versuchen", { viewModel.scan() }, color = SurfaceRaised)
    }
    if (library.snapshot.allTracks.isNotEmpty()) {
      Text(
        "${library.snapshot.rootName}: ${library.snapshot.allTracks.size} Titel, " +
          "${library.snapshot.folders.size} Knoten",
        color = MutedGrey,
        fontSize = 11.sp,
      )
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel("SINUSTON")
    Spacer(Modifier.height(6.dp))
    PillButton(
      text = if (sinePlaying) "Stop Sine Wave" else "Play Sine Wave",
      onClick = { if (sinePlaying) viewModel.stopSine() else viewModel.toggleSine() },
      color = if (sinePlaying) DangerRed else Blue,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("FREQUENZ")
      Text("${settings.sineFrequencyHz} Hz", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      StepButton("−") { viewModel.nudgeSineFrequency(-1) }
      Slider(
        value = frequencyPercent,
        valueRange = 0f..14_900f,
        steps = 1_489,
        onValueChange = { raw -> viewModel.setSineFrequency(100 + Math.round(raw / 10f) * 10) },
        modifier = Modifier.weight(1f),
      )
      StepButton("+") { viewModel.nudgeSineFrequency(1) }
    }
    Text("10-Hz-Schritte am Regler · ±1 Hz an den Tasten · langes Drücken = ±10 Hz", color = MutedGrey, fontSize = 10.sp)

    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("TESTLAUTSTÄRKE")
      Text("${sineVolumePercent.toInt()} %", color = Color.White, fontSize = 13.sp)
    }
    Slider(
      value = sineVolumePercent,
      valueRange = 0f..100f,
      onValueChange = viewModel::setSineVolumePercent,
      modifier = Modifier.fillMaxWidth(),
    )
    Text("Vorsicht: Beginne leise und erhöhe langsam.", color = MutedGrey, fontSize = 10.sp)

    Spacer(Modifier.height(20.dp))
    SectionLabel("WIEDERGABE")
    Spacer(Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text("Auto-Advance", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("nächster Titel am Ordnerende", color = MutedGrey, fontSize = 10.sp)
      }
      Switch(
        checked = settings.autoAdvance,
        onCheckedChange = viewModel::setAutoAdvance,
        colors = SwitchDefaults.colors(checkedTrackColor = Blue),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      RepeatMode.entries.forEach { mode ->
        val active = settings.repeatMode == mode
        Box(
          modifier =
            Modifier
              .weight(1f)
              .height(40.dp)
              .clip(RoundedCornerShape(20.dp))
              .background(if (active) Blue else SurfaceRaised)
              .clickable { viewModel.setRepeatMode(mode) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = when (mode) {
              RepeatMode.OFF -> "AUS"
              RepeatMode.ONE -> "TITEL"
              RepeatMode.ALL -> "ALLE"
            },
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
      Text(
        if (settings.repeatMode == RepeatMode.ONE) "Titel wiederholt sich." else if (settings.autoAdvance) "Ordner läuft weiter." else "Stoppt am Ordnerende.",
        color = MutedGrey,
        fontSize = 10.sp,
      )
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel("BIBLIOTHEK")
    Spacer(Modifier.height(6.dp))
    PillButton(
      "Musik neu einlesen",
      onClick = { library.snapshot.let { viewModel.scan() } },
      color = SurfaceRaised,
      modifier = Modifier.fillMaxWidth(),
    )
    if (library.scanning) {
      Text("Scan läuft…", color = MutedGrey, fontSize = 11.sp)
    }

    Spacer(Modifier.height(20.dp))
    SectionLabel("DIAGNOSE")
    Spacer(Modifier.height(6.dp))
    InfoRow("Version", BuildConfig.VERSION_NAME)
    InfoRow("Titel", "${library.snapshot.allTracks.size}")
    InfoRow("Root", library.snapshot.rootName.ifBlank { "–" })
    InfoRow(
      "Letzter Scan",
      if (library.snapshot.scannedAtMs > 0) formatTime(library.snapshot.scannedAtMs) else "–",
    )
    InfoRow("Wiedergabe", if (playback.isPlaying) "läuft" else "steht")
    InfoRow("App-Lautstärke", "${settings.playerVolumeDb.toInt()} dB")
    InfoRow("EQ", if (settings.eqBypass) "Bypass" else settings.activePreset().name)
    InfoRow("Sync", if (sync.lastSyncMs > 0) formatTime(sync.lastSyncMs) else "noch nicht")
    InfoRow("Sync-Transport", "Wear Data Layer")
    Text(
      "Audio-Route: System (A2DP) · DSP: Media3-AudioProcessor",
      color = MutedGrey,
      fontSize = 10.sp,
      modifier = Modifier.padding(top = 6.dp),
    )
    Spacer(Modifier.height(24.dp))
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(SurfaceRaised)
        .combinedClickable(onClick = onClick, onLongClick = { repeat(10) { onClick() } }),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
  }
}

private fun formatTime(epochMs: Long): String =
  SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(epochMs))
