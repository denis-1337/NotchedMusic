package de.notchedmusic.watch.model

import kotlinx.serialization.Serializable

/**
 * Ein komplettes Klangrezept: Vor-Verstärkung und bis zu sechs Bänder.
 *
 * Das Format ist eine Kopie der Phone-Core-Klasse und wird durch gemeinsame JSON-Fixtures
 * abgesichert. Der Hörpegel gehört bewusst **nicht** hierher; er ist Eigentum der Uhr.
 */
@Serializable
data class EqPreset(
  val id: String,
  val name: String,
  val preGainDb: Double = 0.0,
  val bands: List<FilterBand> = emptyList(),
) {

  /** Liefert immer [MAX_BANDS] Bänder in UI-Reihenfolge; fehlende werden neutral aufgefüllt. */
  fun normalizedBands(): List<FilterBand> =
    List(MAX_BANDS) { index ->
      bands.getOrNull(index)
        ?: FilterBand(
          type = FilterType.OFF,
          frequencyHz = DEFAULT_FREQUENCIES_HZ[index],
          q = 1.0,
          gainDb = 0.0,
        )
    }

  /**
   * Klemmt das Preset auf einen bei [sampleRateHz] gefahrlos rechenbaren Zustand.
   *
   * Überzählige Bänder fallen weg, die positive Summen-Anhebung wird anteilig begrenzt.
   */
  fun validate(sampleRateHz: Double): EqPreset {
    require(sampleRateHz > 0.0) { "Sample-Rate muss größer als 0 Hz sein, war $sampleRateHz Hz" }
    val maxFrequency = minOf(MAX_FREQUENCY_HZ, sampleRateHz * MAX_SAMPLE_RATE_FRACTION)

    var usable =
      normalizedBands().map { band ->
        band.copy(
          frequencyHz = band.frequencyHz.coerceIn(MIN_FREQUENCY_HZ, maxFrequency),
          q = band.q.coerceIn(MIN_Q, MAX_Q),
          gainDb = band.gainDb.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB),
        )
      }

    val positiveSumDb = usable.filter { it.enabled }.sumOf { maxOf(0.0, it.gainDb) }
    if (positiveSumDb > MAX_TOTAL_BOOST_DB) {
      val scale = MAX_TOTAL_BOOST_DB / positiveSumDb
      usable =
        usable.map { band ->
          if (band.gainDb > 0.0) band.copy(gainDb = band.gainDb * scale) else band
        }
    }

    return copy(
      preGainDb = preGainDb.coerceIn(-MAX_PRE_GAIN_DB, MAX_PRE_GAIN_DB),
      bands = usable,
    )
  }

  fun activeBands(): List<FilterBand> =
    normalizedBands().filter { it.enabled && it.type != FilterType.OFF }

  companion object {
    const val MAX_BANDS = 6
    const val MAX_PRESETS = 3
    const val MIN_FREQUENCY_HZ = 20.0
    const val MAX_SAMPLE_RATE_FRACTION = 0.45
    const val MAX_FREQUENCY_HZ = 20_000.0
    const val MIN_Q = 0.1
    const val MAX_Q = 30.0
    const val MAX_BAND_GAIN_DB = 24.0
    const val MAX_PRE_GAIN_DB = 12.0
    const val MAX_TOTAL_BOOST_DB = 12.0
    val DEFAULT_FREQUENCIES_HZ: List<Double> = listOf(60.0, 250.0, 1_000.0, 4_000.0, 8_000.0, 12_000.0)
  }
}
