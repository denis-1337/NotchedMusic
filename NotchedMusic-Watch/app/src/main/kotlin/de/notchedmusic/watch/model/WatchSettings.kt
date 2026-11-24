package de.notchedmusic.watch.model

import de.notchedmusic.watch.legacy.LegacyFilterProfiles
import de.notchedmusic.watch.legacy.LegacySlot
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Der vollständige Zustand der Uhr.
 *
 * * genau drei Presets (`p1`, `p2`, `p3`) – die Phone-App ist der Editor,
 * * `activePresetId = null` bedeutet **Bypass**; es gibt keinen separaten Schalter,
 * * `volumeDb` gehört ausschließlich der Uhr und wird durch einen Preset-Sync nie verstellt.
 */
@Serializable
data class WatchSettings(
  val version: Int = SETTINGS_VERSION,
  val presets: List<EqPreset> = DefaultPresets.three(),
  val activePresetId: String? = DefaultPresets.PRESET_1_ID,
  val volumeDb: Float = 0f,
  val keepScreenOn: Boolean = false,
  val ambientDimmed: Boolean = true,
  /** `null` = Wert aus dem Altbestand: folgt dann [ambientDimmed]. */
  val ambientAnzeige: AmbientAnzeige? = null,
  val lastPresetUpdateMs: Long = 0L,
  val lastMusicUpdateMs: Long = 0L,
  val lastSyncError: String? = null,
  /** Pfad des zuletzt gewählten Titels; `null`, solange noch nichts gespielt wurde. */
  val lastTrackPath: String? = null,
) {

  /** Das aktive Preset; `null` im Bypass. */
  fun activePreset(): EqPreset? = presets.firstOrNull { it.id == activePresetId }

  /** Genau drei Presets in stabiler Reihenfolge; fehlende werden mit Standards aufgefüllt. */
  fun normalized(): WatchSettings {
    val defaults = DefaultPresets.three()
    val complete =
      List(EqPreset.MAX_PRESETS) { index ->
        val candidate = presets.getOrNull(index) ?: defaults[index]
        candidate.copy(
          id = candidate.id.ifBlank { defaults[index].id },
          name = candidate.name.ifBlank { defaults[index].name },
        )
      }
    val validActive =
      activePresetId?.takeIf { id -> complete.any { it.id == id } }
    return copy(
      version = SETTINGS_VERSION,
      presets = complete,
      activePresetId = validActive,
      volumeDb = volumeDb.coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB),
      ambientAnzeige = ambientAnzeigeStufe(),
      ambientDimmed = ambientAnzeigeStufe() != AmbientAnzeige.VOLL,
      lastPresetUpdateMs = lastPresetUpdateMs.coerceAtLeast(0L),
      lastMusicUpdateMs = lastMusicUpdateMs.coerceAtLeast(0L),
      lastTrackPath = lastTrackPath?.takeIf { it.isNotBlank() },
    )
  }

  /** Die wirksame Ambient-Stufe – auch für Zustände, die noch ohne den neuen Wert gespeichert sind. */
  fun ambientAnzeigeStufe(): AmbientAnzeige =
    ambientAnzeige ?: AmbientAnzeige.ausAltbestand(ambientDimmed)

  /** Setzt Ambient-Stufe und hält den alten Schalter dabei widerspruchsfrei. */
  fun withAmbientAnzeige(modus: AmbientAnzeige): WatchSettings =
    copy(ambientAnzeige = modus, ambientDimmed = modus != AmbientAnzeige.VOLL)

  /** Einstellungs-Knopf: schmal → voll → aus → schmal. */
  fun cycleAmbientAnzeige(): WatchSettings = withAmbientAnzeige(ambientAnzeigeStufe().naechste())

  /** Wählt ein Preset oder schaltet mit `null` auf Bypass. */
  fun selectPreset(presetId: String?): WatchSettings {
    val valid = presetId?.takeIf { id -> presets.any { it.id == id } }
    return copy(activePresetId = valid)
  }

  /** Modus-Knopf: P1 → P2 → P3 → Aus → P1. Immer genau ein Zustand. */
  fun cyclePreset(): WatchSettings {
    val order = presets.map { it.id }
    val currentIndex = order.indexOf(activePresetId)
    val next =
      when {
        currentIndex < 0 -> order.firstOrNull()
        currentIndex >= order.lastIndex -> null
        else -> order[currentIndex + 1]
      }
    return copy(activePresetId = next)
  }

  /** Übernimmt Klangrezepte von der Phone-App; Hörpegel und Anzeige bleiben. */
  fun withPresetsFrom(payloadPresets: List<EqPreset>, nowMs: Long): WatchSettings {
    val defaults = DefaultPresets.three()
    val complete =
      List(EqPreset.MAX_PRESETS) { index ->
        val candidate = payloadPresets.getOrNull(index) ?: defaults[index]
        candidate.copy(
          id = candidate.id.ifBlank { defaults[index].id },
          name = candidate.name.ifBlank { defaults[index].name },
        )
      }
    return copy(
      presets = complete,
      activePresetId = activePresetId?.takeIf { id -> complete.any { it.id == id } },
      lastPresetUpdateMs = nowMs.coerceAtLeast(0L),
      lastSyncError = null,
    )
  }

  fun withVolumeDb(value: Float): WatchSettings =
    copy(volumeDb = value.coerceIn(MIN_VOLUME_DB, MAX_VOLUME_DB))

  companion object {
    const val SETTINGS_VERSION = 3
    const val MIN_VOLUME_DB = -60f
    const val MAX_VOLUME_DB = 0f
  }
}

/** Persistenz-Codec samt Migration des alten A/B-Stands. */
object WatchSettingsCodec {

  val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      isLenient = false
    }

  fun encode(settings: WatchSettings): String =
    json.encodeToString(WatchSettings.serializer(), settings)

  /** Liest den neuen Stand; `null` bei leerem oder kaputtem Text. */
  fun decode(text: String?): WatchSettings? {
    if (text.isNullOrBlank()) return null
    return try {
      json.decodeFromString(WatchSettings.serializer(), text).normalized()
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  /**
   * Migriert den alten `FilterProfiles`-Stand (A/B) auf drei Presets.
   *
   * A → p1, B → p2, p3 = Standard. Lautstärke, Anzeige-Schalter und Ambient-Darstellung bleiben.
   * Die alte Auswahl bleibt: A → p1, B → p2, aus → aus.
   */
  fun migrateLegacy(text: String?): WatchSettings? {
    if (text.isNullOrBlank()) return null
    val legacy =
      try {
        json.decodeFromString(LegacyFilterProfiles.serializer(), text)
      } catch (_: SerializationException) {
        return null
      } catch (_: IllegalArgumentException) {
        return null
      }
    val defaults = DefaultPresets.three()
    val mapped =
      listOf(
        legacy.slotA.toEqPreset(DefaultPresets.PRESET_1_ID, "Preset 1"),
        legacy.slotB.toEqPreset(DefaultPresets.PRESET_2_ID, "Preset 2"),
        defaults[2],
      )
    val activeId =
      when (legacy.active) {
        LegacySlot.A -> DefaultPresets.PRESET_1_ID
        LegacySlot.B -> DefaultPresets.PRESET_2_ID
        null -> null
      }
    return WatchSettings(
      version = WatchSettings.SETTINGS_VERSION,
      presets = mapped,
      activePresetId = activeId,
      volumeDb = legacy.volumeDb,
      keepScreenOn = legacy.keepScreenOn,
      ambientDimmed = legacy.ambientDimmed,
      lastPresetUpdateMs = legacy.lastPhoneUpdateMs,
    ).normalized()
  }
}
