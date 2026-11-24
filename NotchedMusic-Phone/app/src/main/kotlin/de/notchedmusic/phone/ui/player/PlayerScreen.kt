package de.notchedmusic.phone.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notchedmusic.core.model.ClipLevel
import de.notchedmusic.phone.R
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.ui.PillButton
import de.notchedmusic.phone.ui.SectionLabel
import de.notchedmusic.phone.ui.formatDuration
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.DangerRed
import de.notchedmusic.phone.ui.theme.MutedGrey
import de.notchedmusic.phone.ui.theme.NightBlack
import de.notchedmusic.phone.ui.theme.SurfaceDark
import de.notchedmusic.phone.ui.theme.SurfaceRaised
import de.notchedmusic.phone.ui.theme.WarnYellow

/**
 * Player-Seite: Ordner-/Titelpfeile, Cover, Play, App-Lautstärke, Bypass und Preset.
 *
 * Der Slider steuert **nur** die App-Lautstärke. Die Systemlautstärke bleibt unberührt und ist
 * zusätzlich über die Seitentasten verfügbar – die beiden Regler addieren sich hörbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
  viewModel: AppViewModel,
  onPickFolder: () -> Unit,
  onPlay: () -> Unit,
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val playback by viewModel.playback.collectAsStateWithLifecycle()
  val library by viewModel.library.collectAsStateWithLifecycle()
  val selectedFolderKey by viewModel.selectedFolderKey.collectAsStateWithLifecycle()
  val cover by viewModel.cover.collectAsStateWithLifecycle()
  val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
  val clipLevel by viewModel.clipLevel.collectAsStateWithLifecycle()

  // Die Clip-Anzeige wird nur abgeholt, solange diese Seite sichtbar ist (auch bei Bildschirm aus
  // oder anderem Tab hort der ViewModel auf zu fragen).
  LifecycleStartEffect(Unit) {
    viewModel.clipUeberwachung(true)
    onStopOrDispose { viewModel.clipUeberwachung(false) }
  }

  val snapshot = library.snapshot
  val folder = snapshot.folder(selectedFolderKey) ?: snapshot.root
  val hasFolder = settings.rootUri != null

  var showFolders by remember { mutableStateOf(false) }
  var showPresets by remember { mutableStateOf(false) }
  val volumePercent = ((settings.playerVolumeDb + 60f) / 60f * 100f).coerceIn(0f, 100f)

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    NavigationRow(
      label = "ORDNER",
      value = folder?.name ?: "Kein Ordner",
      onPrevious = { viewModel.nextFolder(-1) },
      onNext = { viewModel.nextFolder(1) },
      onValueClick = { showFolders = true },
    )
    Spacer(Modifier.height(12.dp))

    NavigationRow(
      label = "TITEL",
      value = if (playback.hasTracks) (currentTrack?.title ?: playback.title) else "Keine Titel",
      onPrevious = viewModel::previousTrack,
      onNext = viewModel::nextTrack,
      onValueClick = {},
    )

    Spacer(Modifier.height(12.dp))
    Box(
      modifier =
        Modifier
          .fillMaxWidth(0.86f)
          .aspectRatio(1f)
          .clip(RoundedCornerShape(18.dp))
          .background(SurfaceRaised),
      contentAlignment = Alignment.Center,
    ) {
      val bitmap = cover
      if (bitmap != null) {
        Image(
          bitmap = bitmap.asImageBitmap(),
          contentDescription = stringResource(R.string.content_desc_cover),
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize(),
        )
      } else {
        Text("♪", color = MutedGrey, fontSize = 64.sp)
      }
    }

    Spacer(Modifier.height(16.dp))
    Box(
      modifier =
        Modifier
          .size(84.dp)
          .clip(CircleShape)
          .background(Blue)
          .clickable { onPlay() },
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = if (playback.isPlaying) "❚❚" else "▶",
        color = Color.White,
        fontSize = if (playback.isPlaying) 24.sp else 34.sp,
        fontWeight = FontWeight.Bold,
      )
    }
    Spacer(Modifier.height(12.dp))
    Text(
      text = currentTrack?.title ?: "Noch kein Titel",
      color = Color.White,
      fontSize = 20.sp,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = currentTrack?.subtitle ?: if (hasFolder) "Ordner wählen und Play drücken" else "Kein Musikordner gewählt",
      color = MutedGrey,
      fontSize = 13.sp,
      textAlign = TextAlign.Center,
    )
    Text(
      text =
        when {
          playback.hasTracks -> "${playback.currentIndex + 1} / ${playback.trackCount} · ${formatDuration(playback.durationMs)}"
          else -> "0 / 0"
        },
      color = MutedGrey,
      fontSize = 12.sp,
    )

    val playbackError = playback.errorMessage
    if (playbackError != null) {
      Spacer(Modifier.height(6.dp))
      Text(playbackError, color = DangerRed, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
    val libraryError = library.error
    if (libraryError != null) {
      Spacer(Modifier.height(6.dp))
      Text(libraryError, color = DangerRed, fontSize = 12.sp, textAlign = TextAlign.Center)
      Spacer(Modifier.height(6.dp))
      PillButton("Ordner wählen", onPickFolder)
    }

    Spacer(Modifier.height(16.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      SectionLabel("LAUTSTÄRKE")
      ClipBadge(clipLevel)
    }
    Slider(
      value = volumePercent,
      valueRange = 0f..100f,
      onValueChange = viewModel::setPlayerVolumePercent,
      modifier = Modifier.fillMaxWidth(),
    )
    Text("${volumePercent.toInt()} % · ${settings.playerVolumeDb.toInt()} dB", color = MutedGrey, fontSize = 12.sp)

    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
          Text("EQ BYPASS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
          Text(
            text = if (settings.eqBypass) "Filter aus – Originalklang" else "Filter aktiv",
            color = MutedGrey,
            fontSize = 11.sp,
          )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
          checked = settings.eqBypass,
          onCheckedChange = viewModel::setEqBypass,
          colors = SwitchDefaults.colors(checkedTrackColor = Blue, uncheckedTrackColor = MutedGrey),
        )
      }

      Box {
        Box(
          modifier =
            Modifier
              .background(Blue, RoundedCornerShape(20.dp))
              .clickable { showPresets = true }
              .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
          Text("${settings.activePreset().name} ▾", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        DropdownMenu(expanded = showPresets, onDismissRequest = { showPresets = false }) {
          settings.presets.forEach { preset ->
            DropdownMenuItem(
              text = { Text(preset.name) },
              onClick = {
                viewModel.selectPreset(preset.id)
                showPresets = false
              },
            )
          }
        }
      }
    }
    Spacer(Modifier.height(24.dp))
  }

  if (showFolders) {
    ModalBottomSheet(
      onDismissRequest = { showFolders = false },
      containerColor = SurfaceDark,
    ) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        SectionLabel("ORDNER WÄHLEN")
        snapshot.folders.forEach { node ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable {
                  viewModel.selectFolder(node.key)
                  showFolders = false
                }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = node.name + if (node.isRoot) " (alles)" else "",
              color = if (node.key == selectedFolderKey) Blue else Color.White,
              fontSize = 16.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            Text("${node.trackCount} Titel", color = MutedGrey, fontSize = 12.sp)
          }
        }
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun NavigationRow(
  label: String,
  value: String,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onValueClick: () -> Unit,
) {
  Column(Modifier.fillMaxWidth()) {
    SectionLabel(label)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArrowButton("‹", onClick = onPrevious)
      Text(
        text = value,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier
            .weight(1f)
            .clickable { onValueClick() }
            .padding(horizontal = 8.dp),
      )
      ArrowButton("›", onClick = onNext)
    }
  }
}

@Composable
private fun ArrowButton(text: String, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(SurfaceRaised)
        .clickable { onClick() },
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = Blue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
  }
}

@Composable
private fun ClipBadge(level: ClipLevel) {
  val (text, color) =
    when (level) {
      ClipLevel.OK -> "OK" to MutedGrey
      ClipLevel.LIMITING -> stringResource(R.string.clip_limiter) to WarnYellow
      ClipLevel.CLIPPING -> stringResource(R.string.clip_clip) to DangerRed
    }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(6.dp))
    Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
  }
}
