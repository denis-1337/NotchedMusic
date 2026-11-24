package de.notchedmusic.phone.ui.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notchedmusic.phone.R
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.sync.tracksForSelection
import de.notchedmusic.phone.ui.PillButton
import de.notchedmusic.phone.ui.SectionLabel
import de.notchedmusic.phone.ui.formatBytes
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.DangerRed
import de.notchedmusic.phone.ui.theme.MutedGrey
import de.notchedmusic.phone.ui.theme.SurfaceDark
import de.notchedmusic.phone.ui.theme.SurfaceRaised
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Synchronize-Seite: Auswahl des Roots und der direkten Unterordner, zwei Sync-Läufe,
 * Fortschritt und Abbruch.
 *
 * Die Übertragung läuft über die Wear Data Layer; Presets in v3, Musik als SHA-256-geprüfter
 * Spiegel mit Commit. Ein Abbruch löscht auf der Uhr nichts.
 */
@Composable
fun SyncScreen(
  viewModel: AppViewModel,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val sync by viewModel.sync.collectAsStateWithLifecycle()
  val library by viewModel.library.collectAsStateWithLifecycle()
  val folders = library.snapshot.folders
  val selectedTracks = library.snapshot.tracksForSelection(sync.selection)
  val selectedBytes = selectedTracks.sumOf { it.fileSizeBytes }

  LaunchedEffect(Unit) { viewModel.checkWatchConnection() }

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 8.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(SurfaceRaised).clickable { onBack() },
        contentAlignment = Alignment.Center,
      ) {
        Text("‹", color = Blue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
      }
      Text("SYNCHRONIZE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Spacer(Modifier.size(44.dp))
    }

    Text(
      text = stringResource(R.string.sync_title),
      color = Color.White,
      fontSize = 20.sp,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    )
    Text(
      text = "Choose which music the watch should receive. All three presets are always sent.",
      color = MutedGrey,
      fontSize = 11.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    )

    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text =
          when (sync.watchAvailable) {
            true -> "Watch connected – Bluetooth/Wi-Fi active."
            false -> "No watch found – open the app on the watch."
            null -> "Looking for watch…"
          },
        color = if (sync.watchAvailable == false) DangerRed else MutedGrey,
        fontSize = 10.sp,
        textAlign = TextAlign.Center,
      )
      Text(
        text = "Find watch",
        color = Blue,
        fontSize = 10.sp,
        modifier =
          Modifier
            .padding(start = 8.dp)
            .clickable(enabled = !sync.isRunning) { viewModel.checkWatchConnection() }
            .padding(4.dp),
      )
    }

    Spacer(Modifier.height(8.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("SELECTION")
      Row {
        Text(
          stringResource(R.string.select_all),
          color = Blue,
          fontSize = 11.sp,
          modifier = Modifier.clickable { viewModel.selectAllForSync() }.padding(6.dp),
        )
        Text(
          stringResource(R.string.deselect_all),
          color = MutedGrey,
          fontSize = 11.sp,
          modifier = Modifier.clickable { viewModel.clearSyncSelection() }.padding(6.dp),
        )
      }
    }

    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
      if (folders.isEmpty()) {
        item {
          Text(
            "No library yet. Choose a music folder in Setup.",
            color = MutedGrey,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 16.dp),
          )
        }
      }
      items(folders, key = { it.key }) { folder ->
        val checked = folder.key in sync.selection
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(SurfaceDark)
              .clickable { viewModel.toggleSyncSelection(folder.key) }
              .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = checked,
            onCheckedChange = { viewModel.toggleSyncSelection(folder.key) },
            colors = CheckboxDefaults.colors(checkedColor = Blue),
          )
          Column(Modifier.weight(1f)) {
            Text(
              text = folder.name + if (folder.isRoot) " (all)" else "",
              color = Color.White,
              fontSize = 14.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              "${folder.trackCount} tracks · ${formatBytes(folder.sizeBytes)}",
              color = MutedGrey,
              fontSize = 10.sp,
            )
          }
        }
        Spacer(Modifier.height(4.dp))
      }
    }

    Text(
      text = when {
        sync.selection.isEmpty() -> "No folders selected."
        else -> "${sync.selection.size} folders · ${selectedTracks.size} tracks · ${formatBytes(selectedBytes)}"
      },
      color = MutedGrey,
      fontSize = 11.sp,
      modifier = Modifier.padding(vertical = 4.dp),
    )

    if (sync.isRunning) {
      val progress = sync.progress
      Text(
        text =
          if (progress != null) {
            "${progress.done} / ${progress.total} · ${progress.label} · ${(progress.fraction * 100).toInt()} %"
          } else {
            "${sync.operationLabel} · waiting for watch…"
          },
        color = Color.White,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth(),
      )
      LinearProgressIndicator(
        progress = { progress?.fraction ?: 0f },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = Blue,
      )
      Text(
        stringResource(R.string.cancel),
        color = DangerRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clickable { viewModel.cancelSync() }.padding(6.dp),
      )
    }

    sync.message?.let { message ->
      Text(
        text = message,
        color = if (sync.messageIsError) DangerRed else Color.White,
        fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
      PillButton(
        text = stringResource(R.string.sync_eqs),
        onClick = viewModel::syncEqs,
        enabled = !sync.isRunning && sync.watchAvailable == true,
        modifier = Modifier.weight(1f),
      )
      PillButton(
        text = stringResource(R.string.sync_music),
        onClick = viewModel::syncMusic,
        enabled = !sync.isRunning && sync.selection.isNotEmpty() && sync.watchAvailable == true,
        modifier = Modifier.weight(1f),
      )
    }

    Text(
      text =
        if (sync.lastSyncMs > 0) {
          "${stringResource(R.string.last_sync)}: ${formatTime(sync.lastSyncMs)}"
        } else {
          stringResource(R.string.last_sync) + ": never"
        },
      color = MutedGrey,
      fontSize = 10.sp,
      modifier = Modifier.padding(bottom = 8.dp),
    )
  }
}

private fun formatTime(epochMs: Long): String =
  SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH).format(Date(epochMs))
