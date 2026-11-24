package de.notchedmusic.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import de.notchedmusic.phone.R
import de.notchedmusic.phone.ui.eq.EqScreen
import de.notchedmusic.phone.ui.player.PlayerScreen
import de.notchedmusic.phone.ui.setup.SetupScreen
import de.notchedmusic.phone.ui.stats.StatsScreen
import de.notchedmusic.phone.ui.sync.SyncScreen
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.NightBlack
import de.notchedmusic.phone.ui.theme.SurfaceRaised

/**
 * Rahmen der App: Tabs oben, Synchronize als Unterziel von Setup.
 *
 * Die Edge-to-Edge-Abstände kommen ausschließlich aus `safeDrawing`; die Inhalte bekommen links
 * und rechts dieselbe großzügige Randbreite wie in den Mockups.
 */
@Composable
fun MainScreen(viewModel: AppViewModel) {
  val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
  val showSync by viewModel.showSync.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val folderPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      viewModel.onFolderPicked(uri)
    }
  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* kein Zwang */ }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(NightBlack)
        .safeDrawingPadding(),
  ) {
    if (showSync) {
      SyncScreen(
        viewModel = viewModel,
        onBack = viewModel::closeSync,
        modifier = Modifier.weight(1f),
      )
    } else {
      TabBar(selected = activeTab, onSelect = viewModel::selectTab)
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when (activeTab) {
          0 ->
            PlayerScreen(
              viewModel = viewModel,
              onPickFolder = { folderPicker.launch(null) },
              onPlay = {
                if (
                  ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                  notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.togglePlayback()
              },
            )
          1 -> EqScreen(viewModel)
          2 ->
            SetupScreen(
              viewModel = viewModel,
              onPickFolder = { folderPicker.launch(null) },
              onSendToWatch = viewModel::openSync,
            )
          else -> StatsScreen(viewModel)
        }
      }
    }
  }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
  val labels = listOf(R.string.tab_player, R.string.tab_eq, R.string.tab_setup, R.string.tab_stats)
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    labels.forEachIndexed { index, label ->
      val isSelected = index == selected
      Box(
        modifier =
          Modifier
            .weight(1f)
            .background(
              color = if (isSelected) Blue else SurfaceRaised,
              shape = RoundedCornerShape(12.dp),
            )
            .clickable { onSelect(index) }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = stringResource(label),
          color = Color.White,
          fontSize = 13.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp,
        )
      }
    }
  }
}
