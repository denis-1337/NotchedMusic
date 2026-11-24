package de.notchedmusic.watch.legacy

import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.FilterBand
import de.notchedmusic.watch.model.FilterType
import kotlinx.serialization.Serializable

/** Alter Speicherstand: zwei Plätze A/B und genau ein aktiver Platz oder Bypass. */
@Serializable
data class LegacyFilterProfiles(
  val slotA: LegacyFilterPreset = LegacyFilterPreset.standard(8_000.0, 8.0),
  val slotB: LegacyFilterPreset = LegacyFilterPreset.standard(6_000.0, 4.0),
  val active: LegacySlot? = null,
  val volumeDb: Float = 0f,
  val keepScreenOn: Boolean = false,
  val ambientDimmed: Boolean = true,
  val lastPhoneUpdateMs: Long = 0L,
)

@Serializable
enum class LegacySlot {
  A,
  B,
}

/** Altes Preset-Format: nur Bänder und Vor-Verstärkung, ohne ID und Namen. */
@Serializable
data class LegacyFilterPreset(
  val bands: List<LegacyFilterBand> = emptyList(),
  val preGainDb: Float = 0f,
) {

  fun toEqPreset(id: String, name: String): EqPreset =
    EqPreset(
      id = id,
      name = name,
      preGainDb = preGainDb.toDouble(),
      bands =
        bands
          .take(EqPreset.MAX_BANDS)
          .map { band ->
            FilterBand(
              type = band.type.toFilterType(),
              frequencyHz = band.frequencyHz,
              q = band.q,
              gainDb = band.gainDb,
              enabled = band.enabled,
            )
          },
    )

  companion object {
    /** Typischer alter Startpunkt: ein Notch bei [frequencyHz] Hz. */
    fun standard(frequencyHz: Double, q: Double): LegacyFilterPreset =
      LegacyFilterPreset(
        bands = listOf(LegacyFilterBand(frequencyHz = frequencyHz, q = q, gainDb = -20.0)),
      )
  }
}

@Serializable
data class LegacyFilterBand(
  val frequencyHz: Double,
  val q: Double,
  val gainDb: Double = 0.0,
  val type: LegacyFilterType = LegacyFilterType.NOTCH,
  val enabled: Boolean = true,
)

@Serializable
enum class LegacyFilterType {
  LOW_SHELF,
  HIGH_SHELF,
  PEAKING,
  NOTCH,
}

private fun LegacyFilterType.toFilterType(): FilterType =
  when (this) {
    LegacyFilterType.LOW_SHELF -> FilterType.LOW_SHELF
    LegacyFilterType.HIGH_SHELF -> FilterType.HIGH_SHELF
    LegacyFilterType.PEAKING -> FilterType.PEAKING
    LegacyFilterType.NOTCH -> FilterType.NOTCH
  }
