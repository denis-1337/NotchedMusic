package de.notchedmusic.watch.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.playback.PlaybackUiState
import de.notchedmusic.watch.ui.VolumeScale
import de.notchedmusic.watch.ui.imAmbientModus
import de.notchedmusic.watch.ui.zeigtReduziert
import de.notchedmusic.watch.ui.theme.Akzent
import de.notchedmusic.watch.ui.theme.AmbientSchrift
import de.notchedmusic.watch.ui.theme.NotchedMusicTheme
import de.notchedmusic.watch.ui.theme.chipButtonColors
import kotlinx.coroutines.delay

private val VOLUME_STEP_DP = 24.dp
private const val ROTARY_DIRECTION = 1f
private const val VOLUME_HINT_MS = 1_500L

/**
 * Das laufende Stück: Titel, Fortschritt, Transport und Preset-Modus.
 *
 * Die Seite ist für den runden 466-px-Schirm gebaut: feste Höhe statt scrollender Liste, damit
 * Transport und Modus immer gleichzeitig erreichbar sind. Die Uhr-Krone regelt die Lautstärke;
 * der schmale Balken darunter zeigt den Stand und lässt sich zusätzlich antippen. „Zurück" läuft
 * über die Wear-Geste bzw. den Systemknopf und braucht keinen eigenen Platz.
 *
 * P1 → P2 → P3 → Aus → P1; es ist immer genau ein Zustand wirksam. Halten auf ⏮/⏭ springt einen
 * ganzen Ordner zurück bzw. weiter.
 */
@Composable
fun NowPlayingScreen(
  viewModel: AlbumListViewModel,
  modifier: Modifier = Modifier,
) {
  val playback by viewModel.playback.collectAsStateWithLifecycle()
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val imAmbient = imAmbientModus()

  // Gedimmter Bildschirm: Titel und Minutenstellung reichen alle 5 Sekunden – kein 4-Hz-Grund.
  DisposableEffect(imAmbient) {
    viewModel.oberflaecheReduziert(imAmbient)
    onDispose { viewModel.oberflaecheReduziert(false) }
  }

  NowPlayingScreen(
    playback = playback,
    settings = settings,
    reducedInAmbient = zeigtReduziert(settings.ambientAnzeigeStufe(), imAmbient),
    onVolumeChange = viewModel::setVolumeDb,
    onCycleMode = viewModel::cyclePreset,
    onPrevious = viewModel::previous,
    onPreviousAlbum = viewModel::previousAlbum,
    onTogglePlayPause = viewModel::togglePlayPause,
    onNext = viewModel::next,
    onNextAlbum = viewModel::nextAlbum,
    modifier = modifier,
  )
}

@Composable
internal fun NowPlayingScreen(
  playback: PlaybackUiState,
  settings: WatchSettings,
  reducedInAmbient: Boolean,
  onVolumeChange: (Float) -> Unit,
  onCycleMode: () -> Unit,
  onPrevious: () -> Unit,
  onPreviousAlbum: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  onNextAlbum: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val stepPx = remember(density) { with(density) { VOLUME_STEP_DP.toPx() } }
  val volumeDb = settings.volumeDb

  val volumeSteps = remember { VolumeSteps() }
  val currentVolume = rememberUpdatedState(volumeDb)
  var hintTick by remember { mutableIntStateOf(0) }

  val rotaryState =
    rememberScrollableState { delta ->
      val before = currentVolume.value
      val after =
        volumeSteps.advance(volumeDb = before, deltaPx = delta * ROTARY_DIRECTION, stepPx = stepPx)
      if (after != before) {
        onVolumeChange(after)
        hintTick++
      }
      delta
    }
  val rotaryBehavior =
    RotaryScrollableDefaults.behavior(scrollableState = rotaryState, flingBehavior = null)
  val focusRequester = remember { FocusRequester() }

  LaunchedEffect(reducedInAmbient) {
    if (!reducedInAmbient) focusRequester.requestFocus()
  }

  LaunchedEffect(hintTick) {
    if (hintTick == 0) return@LaunchedEffect
    delay(VOLUME_HINT_MS)
    hintTick = 0
  }

  ScreenScaffold(modifier = modifier) { contentPadding ->
    if (reducedInAmbient) {
      AmbientPlayer(
        playback = playback,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
      )
      return@ScreenScaffold
    }

    Box(
      modifier =
        Modifier.fillMaxSize()
          .rotaryScrollable(behavior = rotaryBehavior, focusRequester = focusRequester),
    ) {
      Column(
        modifier =
          Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          ProgressArc(
            progress = playback.progressFraction(),
            modifier = Modifier.fillMaxWidth(0.7f).height(18.dp),
          )
          Text(
            text = playback.progressText(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
          )
        }

        Text(
          text = playback.currentTitle ?: "No track",
          style = MaterialTheme.typography.titleMedium,
          color = Akzent,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth(),
        )

        CompactButton(
          onClick = onCycleMode,
          colors = chipButtonColors(),
          label = { Text(modusText(settings), style = MaterialTheme.typography.titleSmall, maxLines = 1) },
          modifier = Modifier.fillMaxWidth(0.6f),
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CompactButton(
            onClick = onPrevious,
            onLongClick = onPreviousAlbum,
            onLongClickLabel = "Previous album",
            colors = chipButtonColors(),
            label = { Text("◀", style = MaterialTheme.typography.titleSmall) },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Previous track" },
          )
          CompactButton(
            onClick = onTogglePlayPause,
            colors = chipButtonColors(),
            label = {
              Text(if (playback.isPlaying) "❚❚" else "▶", style = MaterialTheme.typography.titleSmall)
            },
            modifier =
              Modifier.weight(1f).semantics {
                contentDescription = if (playback.isPlaying) "Pause" else "Play"
              },
          )
          CompactButton(
            onClick = onNext,
            onLongClick = onNextAlbum,
            onLongClickLabel = "Next album",
            colors = chipButtonColors(),
            label = { Text("▶", style = MaterialTheme.typography.titleSmall) },
            modifier = Modifier.weight(1f).semantics { contentDescription = "Next track" },
          )
        }

        Column(modifier = Modifier.fillMaxWidth(0.75f)) {
          Text(
            text = "Volume · ${VolumeScale.percent(volumeDb)} %",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
          )
          VolumeBar(
            volumeDb = volumeDb,
            onValueChange = {
              onVolumeChange(it)
              hintTick++
            },
          )
        }
      }

      AnimatedVisibility(
        visible = hintTick > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.Center),
      ) {
        Text(
          text = "Volume · ${VolumeScale.percent(volumeDb)} %",
          style = MaterialTheme.typography.titleMedium,
          color = Akzent,
        )
      }
    }
  }
}

/** Schmaler Lautstärkebalken; die Krone bleibt der Hauptregler, Tippen setzt direkt. */
@Composable
private fun VolumeBar(
  volumeDb: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  val fraction = VolumeScale.fraction(volumeDb)
  val kontur = MaterialTheme.colorScheme.outlineVariant
  Canvas(
    modifier =
      modifier
        .fillMaxWidth()
        .height(14.dp)
        .semantics {
          progressBarRangeInfo =
            ProgressBarRangeInfo(current = fraction * 100f, range = 0f..100f)
        }
        .pointerInput(onValueChange) {
          detectTapGestures { offset ->
            val width = size.width.toFloat()
            if (width > 0f) {
              onValueChange(VolumeScale.volumeDb((offset.x / width).coerceIn(0f, 1f)))
            }
          }
        },
  ) {
    val strich = 6.dp.toPx()
    val mitte = size.height / 2f
    drawLine(
      color = kontur,
      start = Offset(0f, mitte),
      end = Offset(size.width, mitte),
      strokeWidth = strich,
      cap = StrokeCap.Round,
    )
    if (fraction > 0f) {
      drawLine(
        color = Akzent,
        start = Offset(0f, mitte),
        end = Offset(size.width * fraction, mitte),
        strokeWidth = strich,
        cap = StrokeCap.Round,
      )
    }
  }
}

private fun PlaybackUiState.progressText(): String =
  if (durationMs <= 0L) "" else "${formatZeit(positionMs)} / ${formatZeit(durationMs)}"

private fun formatZeit(milliseconds: Long): String {
  val sekunden = (milliseconds.coerceAtLeast(0L) / 1000L)
  val minuten = sekunden / 60L
  return "$minuten:${(sekunden % 60L).toString().padStart(2, '0')}"
}

private fun PlaybackUiState.progressFraction(): Float =
  if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

/** Beschriftung des Modus-Knopfs: Name des aktiven Presets oder „Aus". */
private fun modusText(settings: WatchSettings): String =
  settings.activePreset()?.name ?: "EQ · Off"

@Composable
private fun AmbientPlayer(playback: PlaybackUiState, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.padding(horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    ProgressArc(
      progress = playback.progressFraction(),
      modifier = Modifier.fillMaxWidth(0.7f).height(24.dp),
      farbe = AmbientSchrift,
    )

    Text(
      text = playback.currentTitle ?: "No track",
      style = MaterialTheme.typography.titleMedium,
      color = AmbientSchrift,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.fillMaxWidth(),
    )

    val laufzeit = playback.progressText()
    if (laufzeit.isNotEmpty()) {
      Text(
        text = laufzeit,
        style = MaterialTheme.typography.bodySmall,
        color = AmbientSchrift,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ProgressArc(progress: Float, modifier: Modifier = Modifier, farbe: Color = Akzent) {
  val kontur = MaterialTheme.colorScheme.outlineVariant
  Canvas(modifier = modifier) {
    val strich = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
    drawArc(
      color = kontur,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = false,
      style = strich,
    )
    if (progress > 0f) {
      drawArc(
        color = farbe,
        startAngle = 180f,
        sweepAngle = 180f * progress,
        useCenter = false,
        style = strich,
      )
    }
  }
}

@WearPreviewDevices
@Composable
private fun NowPlayingPreview() {
  NotchedMusicTheme {
    NowPlayingScreen(
      playback = NowPlayingPreviewState,
      settings = WatchSettings(),
      reducedInAmbient = false,
      onVolumeChange = {},
      onCycleMode = {},
      onPrevious = {},
      onPreviousAlbum = {},
      onTogglePlayPause = {},
      onNext = {},
      onNextAlbum = {},
    )
  }
}

private val NowPlayingPreviewState =
  PlaybackUiState(
    isConnected = true,
    isPlaying = true,
    currentTitle = "Morning",
    positionMs = 12_000,
    durationMs = 240_000,
  )
