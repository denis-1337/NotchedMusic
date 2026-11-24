package de.notchedmusic.phone.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.notchedmusic.core.model.AppSettings
import de.notchedmusic.core.model.EqPreset
import de.notchedmusic.core.model.SettingsCodec
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Eine Quelle der Wahrheit für alle Einstellungen.
 *
 * Die Oberfläche und der Audio-Pfad lesen [state] (im RAM, synchron). Schreibvorgänge werden
 * **konflatiert**: Schnelle Reglerbewegungen erzeugen nur den letzten Stand, ältere Stände
 * überschreiben nie einen neueren. Alle drei Presets liegen in **einem** JSON-Eintrag, damit kein
 * halb geschriebener Satz aus verschiedenen Ständen entstehen kann.
 */
/** Minimale Schnittstelle, damit der Sync auf der JVM ohne Android testbar bleibt. */
interface SettingsSource {
  val state: StateFlow<AppSettings>
  fun setLastSync(epochMs: Long)
}

class SettingsRepository(
  context: Context,
  private val scope: CoroutineScope,
) : SettingsSource {

  private val dataStore = context.settingsDataStore
  private val key = stringPreferencesKey("app_settings_json")

  private val _state = MutableStateFlow(AppSettings())
  override val state: StateFlow<AppSettings> = _state.asStateFlow()

  private val writeSignal = Channel<Unit>(Channel.CONFLATED)

  init {
    scope.launch {
      dataStore.data.collect { preferences ->
          val decoded = SettingsCodec.decode(preferences[key])
          // Erst der gespeicherte Stand, danach nur noch Änderungen aus dem RAM.
          if (!hasLoaded) {
            hasLoaded = true
            _state.value = decoded
          }
        }
    }
    scope.launch { writeLoop() }
  }

  @Volatile private var hasLoaded = false

  /**
   * Ändert die Einstellungen sofort im RAM und stößt das konflatierte Schreiben an.
   *
   * Audio und UI sehen die Änderung ohne Umweg; die Festplatte folgt mit dem letzten Stand.
   */
  fun update(transform: (AppSettings) -> AppSettings) {
    _state.update(transform)
    writeSignal.trySend(Unit)
  }

  fun setRootUri(uri: String?) {
    update { it.copy(rootUri = uri) }
  }

  fun setActivePreset(presetId: String) {
    update { it.copy(activePresetId = presetId) }
  }

  fun setEqBypass(bypass: Boolean) {
    update { it.copy(eqBypass = bypass) }
  }

  fun setPlayerVolumeDb(volumeDb: Float) {
    update { it.copy(playerVolumeDb = volumeDb.coerceIn(-60f, 0f)) }
  }

  fun updatePreset(preset: EqPreset) {
    update { settings -> settings.withPreset(preset) }
  }

  override fun setLastSync(epochMs: Long) {
    update { it.copy(lastSyncMs = epochMs) }
  }

  /**
   * Merkt sich die Player-Auswahl (Ordner und Titel).
   *
   * Der Titel wird über die SAF-Dokument-ID festgehalten, damit ein umbenannter oder verschobener
   * Ordner nicht die Auswahl verlieren kann.
   */
  fun setLastPlayed(folderKey: String?, documentId: String?) {
    update { it.copy(lastFolderKey = folderKey, lastTrackDocumentId = documentId) }
  }

  private suspend fun writeLoop() {
    while (true) {
      writeSignal.receive()
      delay(WRITE_DEBOUNCE_MS)
      val snapshot = _state.value
      try {
        dataStore.edit { preferences -> preferences[key] = SettingsCodec.encode(snapshot) }
      } catch (error: IOException) {
        Log.w(TAG, "Einstellungen konnten nicht geschrieben werden", error)
      }
    }
  }

  private companion object {
    const val TAG = "SettingsRepository"
    const val WRITE_DEBOUNCE_MS = 40L
  }
}
