package de.notchedmusic.watch.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import de.notchedmusic.watch.model.EqPreset
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.model.WatchSettingsCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Die eine, pro Prozess gültige Quelle für den Uhren-Zustand: drei Presets, Auswahl, Lautstärke
 * und Anzeige-Schalter.
 *
 * Zwei Schichten, bewusst getrennt:
 *
 * 1. **[settings] (RAM)** – ein [StateFlow], den der Audio-Faden synchron liest.
 * 2. **[store] (Platte)** – ein DataStore, in den derselbe Stand über einen konflatierten Kanal
 *    geschrieben wird. Bei schnellen Reglerbewegungen zählt nur der letzte Stand.
 *
 * Beim ersten Start nach dem Update wird der alte A/B-Stand migriert (A → p1, B → p2,
 * p3 = Standard). Fehlt der Store (Unit-Tests, Vorschau), verhält sich die Klasse wie ein reines
 * In-Memory-Objekt.
 */
object FilterSettings {

  private val _settings = MutableStateFlow(WatchSettings())

  val settings: StateFlow<WatchSettings> = _settings.asStateFlow()

  /**
   * `true`, sobald der erste gespeicherte Stand gelesen wurde.
   *
   * Vor diesem Signal kann [settings] noch die Defaultwerte zeigen – wer daraus eine Wiederaufnahme
   * ableitet, muss darauf warten.
   */
  private val _bereit = MutableStateFlow(false)
  val bereit: StateFlow<Boolean> = _bereit.asStateFlow()

  fun current(): WatchSettings = _settings.value

  /** Das aktive Preset; `null` bedeutet Bypass. */
  fun activePreset(): EqPreset? = _settings.value.activePreset()

  private val SETTINGS_KEY = stringPreferencesKey("watch_settings_json")
  private val LEGACY_KEY = stringPreferencesKey("filter_profiles_json")

  private var store: DataStore<Preferences>? = null
  private var storeScope: CoroutineScope? = null
  private var writeChannel: Channel<WatchSettings>? = null

  private val updateVersion = AtomicLong(0L)

  /** Bindet den dauerhaften Speicher an und lädt den Stand nach. Idempotent. */
  @Synchronized
  fun attachStore(context: Context) {
    if (store != null) return

    val appContext = context.applicationContext
    val dataStore =
      PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { appContext.preferencesDataStoreFile(STORE_NAME) },
      )
    store = dataStore

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    storeScope = scope
    val channel = Channel<WatchSettings>(Channel.CONFLATED)
    writeChannel = channel

    scope.launch {
      for (settings in channel) {
        val encoded = WatchSettingsCodec.encode(settings)
        try {
          dataStore.edit { preferences -> preferences[SETTINGS_KEY] = encoded }
        } catch (_: IOException) {
          // Schreiben fehlgeschlagen ist nicht fatal: dieser Prozess behält seinen RAM-Stand.
        }
      }
    }

    val versionBeforeLoad = updateVersion.get()
    scope.launch {
      val preferences =
        try {
          dataStore.data
            .catch { exception ->
              if (exception is IOException) emit(emptyPreferences()) else throw exception
            }
            .first()
        } catch (_: IOException) {
          null
        }

      val fromNewKey = WatchSettingsCodec.decode(preferences?.get(SETTINGS_KEY))
      val migrated =
        if (fromNewKey == null) {
          WatchSettingsCodec.migrateLegacy(preferences?.get(LEGACY_KEY))
        } else {
          null
        }
      val loaded = fromNewKey ?: migrated
      if (loaded == null) {
        // Nichts Gespeichertes: den RAM-Stand einmal auf die Platte legen.
        channel.trySend(_settings.value)
        _bereit.value = true
        return@launch
      }

      if (updateVersion.get() == versionBeforeLoad) {
        _settings.value = loaded
        // Migration sofort festhalten, damit sie nicht bei jedem Start erneut läuft.
        if (migrated != null) channel.trySend(loaded)
      } else {
        // Während des Ladens hat jemand bedient: der neuere RAM-Stand gewinnt.
        channel.trySend(_settings.value)
      }
      _bereit.value = true
    }
  }

  /** Ändert den Stand atomar; die Oberfläche sieht die Änderung sofort. */
  fun update(change: (WatchSettings) -> WatchSettings) {
    val updated = _settings.updateAndGet { current -> change(current).normalized() }
    updateVersion.incrementAndGet()
    writeChannel?.trySend(updated)
  }

  private const val STORE_NAME = "filter_settings"
}
