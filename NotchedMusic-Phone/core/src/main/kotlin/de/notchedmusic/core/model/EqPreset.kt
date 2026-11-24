package de.notchedmusic.core.model

import kotlinx.serialization.Serializable

/**
 * Ein komplettes Klangrezept: Vor-Verstärkung und bis zu sechs Bänder.
 *
 * Die Phone-App verwaltet verbindlich **drei** Presets; das aktive Preset ist immer genau eines,
 * niemals eine Stapelung. Der Player-Lautstärkeregler gehört bewusst **nicht** hierher: Er ist
 * eine Hör-Einstellung des Nutzers und wird durch einen Sync nie verstellt.
 */
@Serializable
data class EqPreset(
  val id: String,
  val name: String,
  val preGainDb: Double = 0.0,
  val bands: List<FilterBand> = emptyList(),
) {

  /**
   * Liefert immer genau [MAX_BANDS] Bänder in UI-Reihenfolge.
   *
   * Fehlende Einträge werden mit den neutralen Standardfrequenzen aufgefüllt, damit ein
   * abgeschnittenes oder altes Preset nie zu einer springenden Oberfläche führt.
   */
  fun normalizedBands(): List<FilterBand> =
    List(MAX_BANDS) { index ->
      bands.getOrNull(index) ?: FilterBand(
        type = FilterType.OFF,
        frequencyHz = DEFAULT_FREQUENCIES_HZ[index],
        q = 1.0,
        gainDb = 0.0,
      )
    }

  /**
   * Bringt das Preset in einen Zustand, der bei [sampleRateHz] gefahrlos zu rechnen ist.
   *
   * Es wird **geklemmt statt verworfen**: Frequenz, Q, Band-Gain und Vor-Verstärkung landen in
   * ihren zulässigen Bereichen, überzählige Bänder fallen weg und die positive Summen-Anhebung
   * wird anteilig begrenzt. Der Audio-Pfad darf nie an einer Fehlbedienung scheitern.
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

  /** Nur die Klanglich wirksamen Bänder in Schaltreihenfolge. */
  fun activeBands(): List<FilterBand> =
    normalizedBands().filter { it.enabled && it.type != FilterType.OFF }

  companion object {
    /** Höchstzahl der Bänder pro Preset – Anforderung, nicht verhandelbar. */
    const val MAX_BANDS = 6

    /** Genau drei Presets gehören zur Phone-App. */
    const val MAX_PRESETS = 3

    const val MIN_FREQUENCY_HZ = 20.0

    /** Oberhalb von 45 % der Sample-Rate arbeitet ein Biquad unzuverlässig. */
    const val MAX_SAMPLE_RATE_FRACTION = 0.45

    /** Höchste sinnvolle Einstellfrequenz; die Sample-Rate kann enger begrenzen. */
    const val MAX_FREQUENCY_HZ = 20_000.0

    /** Kleinstes Q (breiteste Stellung). */
    const val MIN_Q = 0.1

    /** Größtes Q (schmalste Stellung); darüber schmiert ein Notch hörbar. */
    const val MAX_Q = 30.0

    /** Größter Gain eines einzelnen Bandes. */
    const val MAX_BAND_GAIN_DB = 24.0

    /** Größte Vor-Verstärkung. */
    const val MAX_PRE_GAIN_DB = 12.0

    /** Summen-Anhebung aller wirksamen Bänder gegen Übersteuerung. */
    const val MAX_TOTAL_BOOST_DB = 12.0

    /** Neutrale Frequenzen der sechs Bänder in UI-Reihenfolge. */
    val DEFAULT_FREQUENCIES_HZ: List<Double> = listOf(60.0, 250.0, 1_000.0, 4_000.0, 8_000.0, 12_000.0)
  }
}
