package de.notchedmusic.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Gespeicherter Stand mit eigener Formatversion für spätere Migrationen. */
@Serializable
data class StoredSettings(
  val version: Int = SETTINGS_VERSION,
  val rootUri: String? = null,
  val presets: List<EqPreset> = DefaultPresets.three(),
  val activePresetId: String = DefaultPresets.PRESET_1_ID,
  val eqBypass: Boolean = true,
  val playerVolumeDb: Float = 0f,
  val repeatMode: RepeatMode = RepeatMode.ALL,
  val autoAdvance: Boolean = true,
  val sineFrequencyHz: Int = 440,
  val sineVolumeDb: Float = -20f,
  val lastSyncMs: Long = 0L,
  val lastFolderKey: String? = null,
  val lastTrackDocumentId: String? = null,
) {
  companion object {
    const val SETTINGS_VERSION = 2
  }
}

/**
 * JSON-Codec für [AppSettings].
 *
 * Kaputte oder unbekannte Stände führen niemals zu einer blockierten App: Es wird geloggt und
 * auf die Standardwerte zurückgefallen. Werte werden beim Lesen geklemmt, damit ein manipulierter
 * DataStore-Eintrag keinen Audio-Pfad gefährdet.
 */
object SettingsCodec {

  val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      isLenient = false
    }

  fun encode(settings: AppSettings): String =
    json.encodeToString(
      StoredSettings.serializer(),
      StoredSettings(
        version = StoredSettings.SETTINGS_VERSION,
        rootUri = settings.rootUri,
        presets = settings.presets.take(EqPreset.MAX_PRESETS),
        activePresetId = settings.activePresetId,
        eqBypass = settings.eqBypass,
        playerVolumeDb = settings.playerVolumeDb,
        repeatMode = settings.repeatMode,
        autoAdvance = settings.autoAdvance,
        sineFrequencyHz = settings.sineFrequencyHz,
        sineVolumeDb = settings.sineVolumeDb,
        lastSyncMs = settings.lastSyncMs,
        lastFolderKey = settings.lastFolderKey,
        lastTrackDocumentId = settings.lastTrackDocumentId,
      ),
    )

  /** Liest einen Stand; bei jedem Fehler kommen die Standardwerte zurück. */
  fun decode(text: String?): AppSettings {
    if (text.isNullOrBlank()) return AppSettings()
    val stored =
      try {
        json.decodeFromString(StoredSettings.serializer(), text)
      } catch (_: SerializationException) {
        return AppSettings()
      } catch (_: IllegalArgumentException) {
        return AppSettings()
      }
    return stored.toSettings()
  }

  private fun StoredSettings.toSettings(): AppSettings {
    val defaults = DefaultPresets.three()
    val completePresets =
      List(EqPreset.MAX_PRESETS) { index ->
        presets.getOrNull(index)?.copy(
          id = presets.getOrNull(index)?.id?.ifBlank { defaults[index].id } ?: defaults[index].id,
          name = presets.getOrNull(index)?.name?.ifBlank { "Preset ${index + 1}" } ?: defaults[index].name,
        ) ?: defaults[index]
      }
    val validActiveId =
      activePresetId.takeIf { id -> completePresets.any { it.id == id } } ?: completePresets.first().id
    return AppSettings(
      rootUri = rootUri,
      presets = completePresets,
      activePresetId = validActiveId,
      eqBypass = eqBypass,
      playerVolumeDb = playerVolumeDb.coerceIn(-60f, 0f),
      repeatMode = repeatMode,
      autoAdvance = autoAdvance,
      sineFrequencyHz = sineFrequencyHz.coerceIn(100, 15_000),
      sineVolumeDb = sineVolumeDb.coerceIn(-60f, 0f),
      lastSyncMs = lastSyncMs,
      lastFolderKey = lastFolderKey?.takeIf { it.isNotBlank() },
      lastTrackDocumentId = lastTrackDocumentId?.takeIf { it.isNotBlank() },
    )
  }
}
