package de.notchedmusic.watch.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.playback.PhoneLink
import de.notchedmusic.watch.ui.VolumeScale
import de.notchedmusic.watch.ui.filter.EqScreenViewModel
import de.notchedmusic.watch.ui.theme.Akzent
import de.notchedmusic.watch.ui.theme.NotchedMusicTheme
import de.notchedmusic.watch.ui.theme.chipButtonColors
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Einstellungen der Uhr: Lautstärke, Anzeige, echter Phone-Status, Musik und Speicher.
 *
 * Presets werden hier **nicht** importiert oder bearbeitet – das Phone bleibt der Editor.
 */
@Composable
fun SettingsScreen(
  libraryPath: String,
  onRescan: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: EqScreenViewModel = viewModel(),
) {
  val context = LocalContext.current
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  var hinweis by remember { mutableStateOf<String?>(null) }
  var phoneReachable by remember { mutableStateOf<Boolean?>(null) }
  val storage = remember(libraryPath) { storageText(libraryPath) }

  LaunchedEffect(Unit) {
    PhoneLink.pruefeStatus(context) { erreichbar -> phoneReachable = erreichbar }
  }

  SettingsScreen(
    settings = settings,
    libraryPath = libraryPath,
    phoneReachable = phoneReachable,
    hinweis = hinweis,
    storageText = storage,
    onVolumeChange = viewModel::setVolumeDb,
    onKeepScreenOnChange = viewModel::setKeepScreenOn,
    onAmbientAnzeigeWechseln = viewModel::cycleAmbientAnzeige,
    onRescan = onRescan,
    onRequestProfiles = {
      hinweis = "Anfrage läuft…"
      PhoneLink.fordereProfileAn(context) { erfolgreich, grund ->
        hinweis = if (erfolgreich) "Anfrage an das Handy geschickt" else (grund ?: "Anfrage fehlgeschlagen")
      }
    },
    modifier = modifier,
  )
}

@Composable
internal fun SettingsScreen(
  settings: WatchSettings,
  libraryPath: String,
  phoneReachable: Boolean?,
  hinweis: String?,
  storageText: String,
  onVolumeChange: (Float) -> Unit,
  onKeepScreenOnChange: (Boolean) -> Unit,
  onAmbientAnzeigeWechseln: () -> Unit,
  onRescan: () -> Unit,
  onRequestProfiles: () -> Unit,
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
          Text(text = "Einstellungen", color = Akzent)
        }
      }

      item {
        Text(
          text = "Lautstärke · ${VolumeScale.percent(settings.volumeDb)} %",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Slider(
          value = settings.volumeDb,
          onValueChange = onVolumeChange,
          valueRange = WatchSettings.MIN_VOLUME_DB..WatchSettings.MAX_VOLUME_DB,
          steps = 59,
          segmented = false,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Text(
          text = "Anzeige",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Button(
          onClick = { onKeepScreenOnChange(!settings.keepScreenOn) },
          colors = chipButtonColors(),
          label = {
            Text(if (settings.keepScreenOn) "Bildschirm bleibt an" else "Bildschirm darf aus")
          },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }

      item {
        Button(
          onClick = onAmbientAnzeigeWechseln,
          colors = chipButtonColors(),
          label = {
            Text(settings.ambientAnzeigeStufe().label(), maxLines = 1)
          },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }

      item {
        Text(
          text = "Handy",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Text(
          text = phoneStatusText(phoneReachable, settings),
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      if (settings.lastSyncError != null) {
        item {
          Text(
            text = "Letzter Fehler: ${settings.lastSyncError}",
            style = MaterialTheme.typography.bodySmall,
            color = Akzent,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          )
        }
      }

      item {
        Button(
          onClick = onRequestProfiles,
          colors = chipButtonColors(),
          label = { Text("Profile anfordern", maxLines = 1) },
          transformation = SurfaceTransformation(transformationSpec),
          modifier =
            Modifier.fillMaxWidth()
              .transformedHeight(this, transformationSpec)
              .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding),
        )
      }

      if (hinweis != null) {
        item {
          Text(
            text = hinweis,
            style = MaterialTheme.typography.bodySmall,
            color = Akzent,
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
          )
        }
      }

      item {
        Text(
          text = "Musik",
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Text(
          text = storageText,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
        )
      }

      item {
        Button(
          onClick = onRescan,
          colors = chipButtonColors(),
          label = { Text("Neu laden") },
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

private fun phoneStatusText(reachable: Boolean?, settings: WatchSettings): String {
  val verbindung =
    when (reachable) {
      true -> "verbunden"
      false -> "nicht erreichbar"
      null -> "wird geprüft"
    }
  val presets =
    if (settings.lastPresetUpdateMs <= 0L) {
      "noch keine"
    } else {
      ZEITPUNKT_FORMAT.format(Instant.ofEpochMilli(settings.lastPresetUpdateMs))
    }
  val musik =
    if (settings.lastMusicUpdateMs <= 0L) {
      "noch keine"
    } else {
      ZEITPUNKT_FORMAT.format(Instant.ofEpochMilli(settings.lastMusicUpdateMs))
    }
  return listOf(verbindung, "Presets: $presets", "Musik: $musik")
    .joinToString(System.lineSeparator())
}

private fun storageText(path: String): String {
  val folder = File(path)
  val freiMb = folder.usableSpace / (1024L * 1024L)
  val frei =
    if (freiMb >= 1024L) {
      String.format(Locale.GERMAN, "%.1f GB", freiMb / 1024.0)
    } else {
      "$freiMb MB"
    }
  val dateien = MusicMirrorCounter.count(path)
  val anzahl = if (dateien == 1) "1 Datei" else "$dateien Dateien"
  return "$anzahl · $frei frei"
}

/** Kleine, rekursive Zählung – die Einstellungsseite soll nicht die ganze Bibliothek bauen. */
private object MusicMirrorCounter {
  fun count(path: String): Int =
    File(path).walkTopDown().count { file ->
      file.isFile && file.extension.lowercase() in
        setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav")
    }
}

private val ZEITPUNKT_FORMAT: DateTimeFormatter =
  DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMAN)
    .withZone(ZoneId.systemDefault())

@WearPreviewDevices
@Composable
private fun SettingsScreenPreview() {
  NotchedMusicTheme {
    SettingsScreen(
      settings = WatchSettings(),
      libraryPath = "Android/data/de.notchedmusic.phone/files/Music",
      phoneReachable = true,
      hinweis = "Anfrage an das Handy geschickt",
      storageText = "12 Dateien · 18,0 GB frei",
      onVolumeChange = {},
      onKeepScreenOnChange = {},
      onAmbientAnzeigeWechseln = {},
      onRescan = {},
      onRequestProfiles = {},
    )
  }
}
