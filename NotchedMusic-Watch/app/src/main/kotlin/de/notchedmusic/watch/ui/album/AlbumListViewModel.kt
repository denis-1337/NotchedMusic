package de.notchedmusic.watch.ui.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.notchedmusic.watch.data.Album
import de.notchedmusic.watch.data.MusicLibrary
import de.notchedmusic.watch.data.MusicLibraryRefresh
import de.notchedmusic.watch.model.WatchSettings
import de.notchedmusic.watch.playback.FilterSettings
import de.notchedmusic.watch.playback.PlaybackController
import de.notchedmusic.watch.playback.PlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Alben-Liste und Wiedergabe.
 *
 * Der Spieler läuft im [de.notchedmusic.watch.playback.PlaybackService]; hier steht nur die
 * Fernbedienung ([PlaybackController]). Nach einem Musik-Commit meldet der Empfänger über
 * [MusicLibraryRefresh] einen Tick – die Liste liest sich dann selbst neu ein.
 */
class AlbumListViewModel(
  private val musicLibrary: MusicLibrary,
  private val controller: PlaybackController,
) : ViewModel() {

  private val _uiState = MutableStateFlow<AlbumListUiState>(AlbumListUiState.Loading)
  val uiState: StateFlow<AlbumListUiState> = _uiState.asStateFlow()

  val playback: StateFlow<PlaybackUiState> = controller.uiState

  /** Einmalige Anforderung an die Navigation, wenn der letzte Titel wieder im Spieler steht. */
  private val _wiederaufnahme = MutableStateFlow(0L)
  val wiederaufnahme: StateFlow<Long> = _wiederaufnahme.asStateFlow()

  /** Nur ein Versuch pro App-Start – auch wenn der Dienst später erst verbindet. */
  private var wiederaufnahmeVersucht = false

  /** Zuletzt festgehaltener Pfad, damit nicht jeder Zustands-Tick erneut schreibt. */
  private var beobachteterPfad: String? = null

  /** Presets, Auswahl, Lautstärke und Anzeige-Schalter. */
  val settings: StateFlow<WatchSettings> = FilterSettings.settings

  init {
    refresh()
    viewModelScope.launch {
      MusicLibraryRefresh.ticks.collect {
        if (it > 0L) refresh()
      }
    }
    viewModelScope.launch {
      playback.collect { zustand ->
        if (!zustand.isConnected) return@collect
        val pfad = zustand.currentMediaId
        if (pfad != null && pfad != beobachteterPfad) {
          beobachteterPfad = pfad
          FilterSettings.update { it.copy(lastTrackPath = pfad) }
        }
        versucheWiederaufnahme()
      }
    }
    // Erst nach dem Lesen der Platte steht der gespeicherte Pfad bereit.
    viewModelScope.launch {
      FilterSettings.bereit.collect { geladen ->
        if (geladen) versucheWiederaufnahme()
      }
    }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.Default) {
      val albums = musicLibrary.albums()
      _uiState.value =
        if (albums.isEmpty()) AlbumListUiState.NoMusic
        else AlbumListUiState.Albums(albums)
      // Der Controller muss auf dem Main-Thread bedient werden.
      kotlinx.coroutines.withContext(Dispatchers.Main.immediate) { versucheWiederaufnahme() }
    }
  }

  /**
   * Stellt beim ersten Öffnen den letzten Titel wieder im Spieler ein.
   *
   * Läuft eine Sitzung bereits (App nur neu geöffnet), gilt deren laufender Titel. Ist der Dienst
   * frisch, wird der gespeicherte Pfad gesucht und seine Albumliste gewählt – **ohne** Play, damit
   * die Uhr nicht von selbst losspielt. Die Navigation zeigt danach die Spielerseite.
   */
  private fun versucheWiederaufnahme() {
    if (wiederaufnahmeVersucht) return
    if (!FilterSettings.bereit.value) return
    val zustand = playback.value
    if (!zustand.isConnected) return
    val albums = (_uiState.value as? AlbumListUiState.Albums)?.albums ?: return
    wiederaufnahmeVersucht = true

    val laufenderPfad = zustand.currentMediaId
    if (laufenderPfad != null) {
      laufendesAlbum = albumMitTitel(albums, laufenderPfad)?.name ?: laufendesAlbum
      _wiederaufnahme.value = _wiederaufnahme.value + 1L
      return
    }

    val gespeicherterPfad = FilterSettings.current().lastTrackPath ?: return
    val album = albumMitTitel(albums, gespeicherterPfad) ?: return
    val index = album.tracks.indexOfFirst { it.path == gespeicherterPfad }
    if (index < 0) return
    laufendesAlbum = album.name
    controller.restoreList(album.tracks, index)
    _wiederaufnahme.value = _wiederaufnahme.value + 1L
  }

  fun albumNamed(name: String): Album? =
    (_uiState.value as? AlbumListUiState.Albums)?.albums?.firstOrNull { it.name == name }

  fun playAlbum(album: Album) {
    if (album.tracks.isEmpty()) return
    laufendesAlbum = album.name
    controller.playList(album.tracks, /* atIndex = */ 0)
  }

  fun playTrack(album: Album, index: Int) {
    if (index !in album.tracks.indices) return
    laufendesAlbum = album.name
    controller.playList(album.tracks, index)
  }

  fun nextAlbum() = springeAlbum(richtung = +1)

  fun previousAlbum() = springeAlbum(richtung = -1)

  private fun springeAlbum(richtung: Int) {
    val albums = (_uiState.value as? AlbumListUiState.Albums)?.albums ?: return
    val ziel = nachbarAlbum(albums, laufendesAlbum, richtung) ?: return
    playAlbum(ziel)
  }

  private var laufendesAlbum: String? = null

  /** Die UI meldet hier Sichtbarkeit und „nur gedimmt"; der Ticker folgt daraus. */
  private var sichtbarGemeldet = true
  private var reduziertGemeldet = false

  /** Activity sichtbar oder nicht – bei „nicht sichtbar" fragt die UI gar nicht mehr. */
  fun oberflaecheSichtbar(sichtbar: Boolean) {
    sichtbarGemeldet = sichtbar
    controller.setzeAnzeigetakt(sichtbarGemeldet, reduziertGemeldet)
  }

  /** Gedimmter Bildschirm: für Titel und Minutenstellung genügt ein Takt alle 5 Sekunden. */
  fun oberflaecheReduziert(reduziert: Boolean) {
    reduziertGemeldet = reduziert
    controller.setzeAnzeigetakt(sichtbarGemeldet, reduziertGemeldet)
  }

  fun next() = controller.next()

  fun previous() = controller.previous()

  fun togglePlayPause() = controller.togglePlayPause()

  fun setVolumeDb(volumeDb: Float) {
    FilterSettings.update { it.withVolumeDb(volumeDb) }
  }

  /** Modus-Knopf: P1 → P2 → P3 → Aus → P1. */
  fun cyclePreset() {
    FilterSettings.update { it.cyclePreset() }
  }

  fun selectPreset(presetId: String?) {
    FilterSettings.update { it.selectPreset(presetId) }
  }

  override fun onCleared() {
    controller.close()
  }
}

/**
 * Ordner-Sprung: das Album neben [aktuell] in [richtung] (+1 = weiter, −1 = zurück).
 *
 * Am Rand und ohne bekanntes laufendes Album gibt es kein Ziel – die Wiedergabe bleibt stehen,
 * statt irgendwohin zu springen.
 */
internal fun nachbarAlbum(albums: List<Album>, aktuell: String?, richtung: Int): Album? {
  val index = albums.indexOfFirst { it.name == aktuell }
  if (index < 0) return null
  return albums.getOrNull(index + richtung)
}

/** Das Album, das den Titel mit [pfad] enthält – Basis der Wiederaufnahme nach einem App-Start. */
internal fun albumMitTitel(albums: List<Album>, pfad: String?): Album? =
  pfad?.let { gesuchterPfad -> albums.firstOrNull { album -> album.tracks.any { it.path == gesuchterPfad } } }

sealed interface AlbumListUiState {

  data object Loading : AlbumListUiState

  data object NoMusic : AlbumListUiState

  data class Albums(val albums: List<Album>) : AlbumListUiState
}
