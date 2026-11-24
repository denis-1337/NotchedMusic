package de.notchedmusic.core.model

/**
 * Start-Presets der App.
 *
 * * **Preset 1**: neutraler Ausgang mit einem Notch bei 8 kHz – ein hilfreicher Startpunkt.
 * * **Preset 2**: breiterer Notch bei 6 kHz, Q 4 – Vergleichsrezept.
 * * **Preset 3**: leer, damit der Nutzer ein eigenes Rezept aufbauen kann.
 */
object DefaultPresets {

  /** Drei feste IDs; sie bleiben über Sync und Neustarts stabil. */
  const val PRESET_1_ID = "p1"
  const val PRESET_2_ID = "p2"
  const val PRESET_3_ID = "p3"

  fun three(): List<EqPreset> =
    listOf(
      EqPreset(
        id = PRESET_1_ID,
        name = "Preset 1",
        preGainDb = 0.0,
        bands = neutralBands().mapIndexed { index, band ->
          if (index == 4) {
            band.copy(type = FilterType.NOTCH, frequencyHz = 8_000.0, q = 8.0, gainDb = -20.0)
          } else {
            band
          }
        },
      ),
      EqPreset(
        id = PRESET_2_ID,
        name = "Preset 2",
        preGainDb = 0.0,
        bands = neutralBands().mapIndexed { index, band ->
          if (index == 4) {
            band.copy(type = FilterType.NOTCH, frequencyHz = 6_000.0, q = 4.0, gainDb = -20.0)
          } else {
            band
          }
        },
      ),
      EqPreset(id = PRESET_3_ID, name = "Preset 3", preGainDb = 0.0, bands = neutralBands()),
    )

  /** Sechs neutrale Bänder in der Reihenfolge der Oberfläche. */
  fun neutralBands(): List<FilterBand> =
    EqPreset.DEFAULT_FREQUENCIES_HZ.map { frequency ->
      FilterBand(type = FilterType.OFF, frequencyHz = frequency, q = 1.0, gainDb = 0.0)
    }

  /** Setzt ein Preset auf die neutralen Standardwerte zurück, ohne die Kennung zu verlieren. */
  fun reset(preset: EqPreset): EqPreset =
    preset.copy(preGainDb = 0.0, bands = neutralBands())
}
