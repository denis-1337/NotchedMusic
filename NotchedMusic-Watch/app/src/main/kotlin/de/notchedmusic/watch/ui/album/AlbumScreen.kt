package de.notchedmusic.watch.ui.album

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import de.notchedmusic.watch.data.Album
import de.notchedmusic.watch.data.Track
import de.notchedmusic.watch.ui.theme.Akzent
import de.notchedmusic.watch.ui.theme.NotchedMusicTheme
import de.notchedmusic.watch.ui.theme.chipButtonColors

/**
 * Stufe 1: die **Alben**.
 *
 * Der Musik-Root ist die Liste, jeder Unterordner ein Album. Hoch/Runter wischen steppt durch,
 * ein Klick spielt ab dem ersten Titel. Genau das, was auf einem 454-Pixel-Schirm noch zu treffen
 * ist – vier Pfeil-Buttons wären zu klein.
 */
@Composable
fun AlbumScreen(
  onOpenAlbum: (Album) -> Unit,
  onOpenEq: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AlbumListViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  AlbumScreen(
    state = state,
    onOpenAlbum = onOpenAlbum,
    onRefresh = viewModel::refresh,
    onOpenEq = onOpenEq,
    onOpenSettings = onOpenSettings,
    modifier = modifier,
  )
}

@Composable
internal fun AlbumScreen(
  state: AlbumListUiState,
  onOpenAlbum: (Album) -> Unit,
  onRefresh: () -> Unit,
  onOpenEq: () -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(modifier = modifier, scrollState = columnState) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        ListHeader(
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(
            text = when (state) {
              AlbumListUiState.Loading -> "Suche Musik…"
              AlbumListUiState.NoMusic -> "Keine Musik"
              is AlbumListUiState.Albums -> "${state.albums.size} Alben"
            },
            color = Akzent,
          )
        }
      }

      when (state) {
        AlbumListUiState.Loading -> Unit

        AlbumListUiState.NoMusic -> {
          item {
            Text(
              text = "Musik per Sync vom Handy schicken – die Titel erscheinen danach hier.",
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            )
          }
        }

        is AlbumListUiState.Albums -> {
          items(state.albums, key = { it.name }) { album ->
            Button(
              onClick = { onOpenAlbum(album) },
              colors = chipButtonColors(),
              label = {
                Text(
                  text = album.name,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              secondaryLabel = { Text("${album.tracks.size} Titel") },
              transformation = SurfaceTransformation(transformationSpec),
              modifier =
                Modifier.fillMaxWidth()
                  .transformedHeight(this, transformationSpec)
                  .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
            )
          }
        }
      }

      item {
        Button(
          onClick = onRefresh,
          colors = chipButtonColors(),
          label = { Text("Neu laden") },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }

      item {
        Button(
          onClick = onOpenEq,
          colors = chipButtonColors(),
          label = { Text("EQ") },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }

      item {
        Button(
          onClick = onOpenSettings,
          colors = chipButtonColors(),
          label = { Text("Einstellungen") },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }
    }
  }
}

/** Titel-Liste eines Albums – Stufe 2. */
@Composable
fun TrackScreen(
  album: Album,
  onTrackSelected: (index: Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columnState = rememberTransformingLazyColumnState()
  val transformationSpec = rememberTransformationSpec()

  ScreenScaffold(modifier = modifier, scrollState = columnState) { contentPadding ->
    TransformingLazyColumn(
      state = columnState,
      contentPadding = contentPadding,
      modifier = Modifier.fillMaxSize(),
    ) {
      item {
        ListHeader(
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
          transformation = SurfaceTransformation(transformationSpec),
        ) {
          Text(text = album.name, color = Akzent)
        }
      }

      itemsIndexed(album.tracks, key = { _, track -> track.path }) { index, track ->
        Button(
          onClick = { onTrackSelected(index) },
          colors = chipButtonColors(),
          label = {
            Text(
              text = track.title,
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
    }
  }
}

private val PREVIEW_TRACKS = listOf(Track("Morgen", "/a"), Track("Regen", "/b"))

@WearPreviewDevices
@Composable
private fun AlbumScreenPreview() {
  NotchedMusicTheme {
    AlbumScreen(
      state = AlbumListUiState.Albums(listOf(Album("Morgen", PREVIEW_TRACKS), Album("Regen", PREVIEW_TRACKS))),
      onOpenAlbum = {},
      onRefresh = {},
      onOpenEq = {},
      onOpenSettings = {},
    )
  }
}
