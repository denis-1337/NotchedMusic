package de.notchedmusic.phone.ui.eq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.FilterBand
import de.notchedmusic.core.model.FilterType
import de.notchedmusic.phone.ui.AppViewModel
import de.notchedmusic.phone.ui.PillButton
import de.notchedmusic.phone.ui.SectionLabel
import de.notchedmusic.phone.ui.theme.ActiveGreen
import de.notchedmusic.phone.ui.theme.Blue
import de.notchedmusic.phone.ui.theme.MutedGrey
import de.notchedmusic.phone.ui.theme.SurfaceDark
import de.notchedmusic.phone.ui.theme.SurfaceRaised

/**
 * EQ-Seite: drei Presets, sechs Bänder, ein Editor für das ausgewählte Band.
 *
 * Farbcode wie in den Mockups: **blauer Rahmen** = ausgewählt, **grün** = aktiv, grau = aus.
 * Zusätzlich steht der Zustand im Chip als Text, damit die Anzeige nicht allein von Farben
 * abhängt.
 */
@Composable
fun EqScreen(viewModel: AppViewModel) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val preset = settings.activePreset()
  val bands = preset.normalizedBands()
  var selectedBand by rememberSaveable { mutableIntStateOf(0) }
  val selected = bands.getOrNull(selectedBand) ?: bands.first()

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
  ) {
    Text("SOUND", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      settings.presets.forEach { item ->
        val active = item.id == settings.activePresetId
        Box(
          modifier =
            Modifier
              .weight(1f)
              .height(48.dp)
              .clip(RoundedCornerShape(24.dp))
              .background(if (active) Blue else SurfaceRaised)
              .clickable { viewModel.selectPreset(item.id) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            item.name.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          )
        }
      }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel("FILTER BANDS · ${Math.round(preset.preGainDb * 10) / 10.0} dB PRE-GAIN")
    Spacer(Modifier.height(8.dp))
    repeat(2) { rowIndex ->
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(3) { column ->
          val index = rowIndex * 3 + column
          BandChip(
            index = index,
            band = bands[index],
            selected = index == selectedBand,
            modifier = Modifier.weight(1f),
            onClick = { selectedBand = index },
          )
        }
      }
      Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(8.dp))
    BandEditor(
      index = selectedBand,
      band = selected,
      preset = preset,
      typeLabel = viewModel::filterTypeLabel,
      onBandChange = { transform -> viewModel.updateActiveBand(selectedBand, transform) },
      onPreGainChange = viewModel::updateActivePresetPreGain,
    )

    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      PillButton(
        text = "Reset preset",
        onClick = viewModel::resetActivePreset,
        color = SurfaceRaised,
        modifier = Modifier.weight(1f),
      )
      CopyPresetButton(viewModel, Modifier.weight(1f))
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun BandChip(
  index: Int,
  band: FilterBand,
  selected: Boolean,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val borderColor =
    when {
      selected -> Blue
      band.enabled && band.type != FilterType.OFF -> ActiveGreen
      else -> Color(0xFF555555)
    }
  val stateText =
    when {
      band.type == FilterType.OFF -> "off"
      !band.enabled -> "off"
      else -> "active"
    }
  Column(
    modifier =
      modifier
        .height(72.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(SurfaceDark)
        .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
        .clickable { onClick() }
        .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("${index + 1}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Text(
      text = shortType(band.type),
      color = if (band.enabled && band.type != FilterType.OFF) ActiveGreen else MutedGrey,
      fontSize = 10.sp,
      maxLines = 1,
    )
    Text("${band.frequencyHz.toInt()} Hz · $stateText", color = MutedGrey, fontSize = 9.sp, maxLines = 1)
  }
}

private fun shortType(type: FilterType): String =
  when (type) {
    FilterType.OFF -> "OFF"
    FilterType.NOTCH -> "NOTCH"
    FilterType.PEAKING -> "BELL"
    FilterType.LOW_SHELF -> "LO-SHLF"
    FilterType.HIGH_SHELF -> "HI-SHLF"
    FilterType.LOW_CUT -> "LOWCUT"
    FilterType.HIGH_CUT -> "HIGHCUT"
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BandEditor(
  index: Int,
  band: FilterBand,
  preset: EqPreset,
  typeLabel: (FilterType) -> String,
  onBandChange: ((FilterBand) -> FilterBand) -> Unit,
  onPreGainChange: (Double) -> Unit,
) {
  var showTypes by remember { mutableStateOf(false) }

  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(SurfaceDark)
        .padding(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("FILTER ${index + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Filter active", color = MutedGrey, fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Switch(
          checked = band.enabled,
          onCheckedChange = { enabled -> onBandChange { it.copy(enabled = enabled) } },
          colors = SwitchDefaults.colors(checkedTrackColor = ActiveGreen),
        )
      }
    }

    Spacer(Modifier.height(8.dp))
    Text("TYPE", color = MutedGrey, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    Box(Modifier.fillMaxWidth()) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceRaised)
            .clickable { showTypes = true }
            .padding(horizontal = 12.dp, vertical = 12.dp),
      ) {
        Text(typeLabel(band.type), color = Color.White, fontSize = 14.sp)
      }
      DropdownMenu(expanded = showTypes, onDismissRequest = { showTypes = false }) {
        FilterType.entries.forEach { type ->
          DropdownMenuItem(
            text = { Text(typeLabel(type)) },
            onClick = {
              onBandChange { it.copy(type = type) }
              showTypes = false
            },
          )
        }
      }
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      NumberField(
        label = "FREQ",
        value = band.frequencyHz,
        unit = "Hz",
        decimals = 0,
        modifier = Modifier.weight(1f),
        onValueChange = { value ->
          onBandChange { it.copy(frequencyHz = value.coerceIn(MIN_FREQUENCY_HZ, MAX_FREQUENCY_HZ)) }
        },
      )
      NumberField(
        label = "Q",
        value = band.q,
        unit = "",
        decimals = 2,
        modifier = Modifier.weight(1f),
        onValueChange = { value -> onBandChange { it.copy(q = value.coerceIn(MIN_Q, MAX_Q)) } },
      )
      NumberField(
        label = "GAIN",
        value = band.gainDb,
        unit = "dB",
        decimals = 1,
        modifier = Modifier.weight(1f),
        onValueChange = { value ->
          onBandChange { it.copy(gainDb = value.coerceIn(-MAX_GAIN_DB, MAX_GAIN_DB)) }
        },
      )
    }

    Spacer(Modifier.height(8.dp))
    Text("Q: lower value = wider, higher value = narrower.", color = MutedGrey, fontSize = 10.sp)
    Text(
      if (band.usesGain) "Gain applies to Bell and Shelves." else "Gain is ignored for this type.",
      color = MutedGrey,
      fontSize = 10.sp,
    )

    Spacer(Modifier.height(12.dp))
    NumberField(
      label = "VOLUME · PRESET PRE-GAIN",
      value = preset.preGainDb,
      unit = "dB",
      decimals = 1,
      modifier = Modifier.fillMaxWidth(),
      onValueChange = { value -> onPreGainChange(value.coerceIn(-MAX_PRE_GAIN_DB, MAX_PRE_GAIN_DB)) },
    )

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        when {
          band.type == FilterType.OFF || !band.enabled -> "This band is off."
          band.frequencyHz > 15_000.0 -> "Frequency is very high – at 44.1 kHz the Nyquist limit applies."
          else -> "Changes apply immediately to current playback."
        },
      color = MutedGrey,
      fontSize = 11.sp,
    )
  }
}

@Composable
private fun CopyPresetButton(viewModel: AppViewModel, modifier: Modifier) {
  val settings by viewModel.settings.collectAsStateWithLifecycle()
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    PillButton(
      text = "Copy from preset",
      onClick = { expanded = true },
      color = SurfaceRaised,
      modifier = Modifier.fillMaxWidth(),
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      settings.presets.forEach { preset ->
        DropdownMenuItem(
          text = { Text("Preset ${preset.name.filter { it.isDigit() }}") },
          onClick = {
            viewModel.copyPresetFrom(preset.id)
            expanded = false
          },
        )
      }
    }
  }
}

/**
 * Zahlenfeld mit deutschem Dezimalkomma.
 *
 * Während der Eingabe bleibt der Text unangetastet („8,“ bleibt stehen), damit kein Zeichen
 * verloren geht. Erst beim Verlassen des Feldes wird auf die geklemmte Zahl zurückformatiert.
 */
@Composable
private fun NumberField(
  label: String,
  value: Double,
  unit: String,
  decimals: Int,
  modifier: Modifier,
  onValueChange: (Double) -> Unit,
) {
  var focused by remember { mutableStateOf(false) }
  var text by remember { mutableStateOf(formatNumber(value, decimals)) }

  LaunchedEffect(value, focused) {
    if (!focused) text = formatNumber(value, decimals)
  }

  Column(modifier) {
    Text(label, color = MutedGrey, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    OutlinedTextField(
      value = text,
      onValueChange = { raw ->
        text = raw
        val normalized = raw.replace(',', '.').trim()
        val parsed = normalized.toDoubleOrNull()
        if (parsed != null) onValueChange(parsed)
      },
      textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Color.White),
      suffix = if (unit.isBlank()) null else ({ Text(unit, color = MutedGrey, fontSize = 11.sp) }),
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      modifier =
        Modifier
          .fillMaxWidth()
          .onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) text = formatNumber(value, decimals)
          },
    )
  }
}

private fun formatNumber(value: Double, decimals: Int): String =
  if (decimals == 0) value.toInt().toString() else String.format(java.util.Locale.ENGLISH, "%.${decimals}f", value)

private const val MIN_FREQUENCY_HZ = 20.0
private const val MAX_FREQUENCY_HZ = 20_000.0
private const val MIN_Q = 0.1
private const val MAX_Q = 30.0
private const val MAX_GAIN_DB = 24.0
private const val MAX_PRE_GAIN_DB = 12.0
