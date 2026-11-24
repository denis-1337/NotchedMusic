package de.notchedmusic.watch.ui.filter

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import de.notchedmusic.watch.model.DefaultPresets
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.ui.theme.Akzent
import de.notchedmusic.watch.ui.theme.NotchedMusicTheme
import de.notchedmusic.watch.ui.theme.chipButtonColors

/**
 * EQ-Seite: **nur** „Aus" und die drei Presets.
 *
 * Es gibt bewusst keinen Band-Editor und keinen Import: Die Presets werden am Phone bearbeitet und
 * per Sync übernommen. Auf 466 × 466 ist jede zusätzliche Bearbeitungsfläche ein verlorenes Ziel.
 */
@Composable
fun EqScreen(
  modifier: Modifier = Modifier,
  viewModel: EqScreenViewModel = viewModel(),
) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  EqScreen(
    settings = settings,
    onSelectPreset = viewModel::selectPreset,
    modifier = modifier,
  )
}

@Composable
internal fun EqScreen(
  settings: WatchSettings,
  onSelectPreset: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(modifier = modifier, scrollState = columnState) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      modifier = Modifier.fillMaxWidth(),
    ) {
      item {
        ListHeader(
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = "EQ", color = Akzent)
        }
      }

      item {
        ModeButton(
          label = "Off",
          isActive = settings.activePresetId == null,
          onClick = { onSelectPreset(null) },
          transformationSpec = transformationSpec,
        )
      }

      for (index in 0 until EqPreset.MAX_PRESETS) {
        val preset = settings.presets.getOrNull(index) ?: DefaultPresets.three()[index]
        item {
          val isActive = settings.activePresetId == preset.id
          ModeButton(
            label = presetSummary(preset),
            isActive = isActive,
            onClick = { onSelectPreset(preset.id) },
            transformationSpec = transformationSpec,
          )
        }
      }
    }
  }
}

@Composable
private fun TransformingLazyColumnItemScope.ModeButton(
  label: String,
  isActive: Boolean,
  onClick: () -> Unit,
  transformationSpec: TransformationSpec,
) {
  Button(
    onClick = onClick,
    colors = chipButtonColors(),
    label = {
      Text(
        text = if (isActive) "$label · active" else label,
        color = if (isActive) Akzent else Color.Unspecified,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    transformation = SurfaceTransformation(transformationSpec),
    modifier =
      Modifier.fillMaxWidth()
        .transformedHeight(this, transformationSpec)
        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
  )
}

private fun presetSummary(preset: EqPreset): String {
  val bands = preset.activeBands().size
  return if (bands == 0) preset.name else "${preset.name} · $bands"
}

@WearPreviewDevices
@Composable
private fun EqScreenPreview() {
  NotchedMusicTheme {
    EqScreen(settings = WatchSettings(), onSelectPreset = {})
  }
}
