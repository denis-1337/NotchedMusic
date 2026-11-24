package de.notchedmusic.core.model

import kotlinx.serialization.Serializable

/** Wiederhol-Verhalten des Players. */
@Serializable
enum class RepeatMode {
  OFF,
  ONE,
  ALL,
}

/**
 * Alle kleinen Einstellungen der Phone-App.
 *
 * Bewusst getrennt vom Preset: `playerVolumeDb` ist eine Hör-Einstellung des Nutzers und wird
 * durch einen Sync nie verstellt. Der EQ-Bypass steht bei einem frischen Start auf `true`, damit
 * die App zunächst „einfach Musik“ spielt; wer den Notch braucht, aktiviert ihn.
 */
@Serializable
data class AppSettings(
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
  /** Zuletzt gewählter Ordner im Player; `null` heißt „noch nichts gespeichert“. */
  val lastFolderKey: String? = null,
  /** Zuletzt gewählter Titel; Identität über die SAF-Dokument-ID, nicht über den Pfad. */
  val lastTrackDocumentId: String? = null,
) {

  /** Liefert das aktive Preset; fällt auf das erste zurück, wenn die ID veraltet ist. */
  fun activePreset(): EqPreset =
    presets.firstOrNull { it.id == activePresetId } ?: presets.firstOrNull() ?: DefaultPresets.three().first()

  /** Ersetzt ein Preset mit derselben Kennung; unbekannte IDs bleiben unverändert. */
  fun withPreset(updated: EqPreset): AppSettings {
    val replaced = presets.map { if (it.id == updated.id) updated else it }
    return copy(presets = replaced)
  }
}
