# NotchedMusic Phone – Umsetzungsplan

> **Status: Planung, noch kein Code.**  
> Erstellt aus `anforderungen1.txt`, den vier GUI-Mockups und dem vorhandenen
> Referenzprojekt `Z:\android_projekte\NotchedMusic\NotchedMusic-Watch` (Wear-App).
>
> **Projektgrenze: Das bestehende Uhr-Projekt wird in diesem Vorhaben nicht verändert.**
> Die Uhr-App wird später – in einem separaten Schritt außerhalb dieses Projekts – an
> drei Presets und das neue Synchronisationsprotokoll angepasst. Bis dahin bleibt die
> Phone-App bei einer **Mock-Synchronisation**; es werden keine Dateien im Wear-Projekt
> editiert, gebaut oder umstrukturiert.

| | |
|---|---|
| Projektordner | `Z:\android_projekte\NotchedMusic\NotchedMusic-Phone` |
| Mockups | `Z:\android_projekte\NotchedMusic\eq_mediaplayer\Screenshot *.png` |
| Zielgerät | Google Pixel 10, Android 17 (API 37) |
| App-Paket | `de.notchedmusic.phone` |
| Projektname | `NotchedMusicPhone` |
| Sprache | Kotlin, Compose, Material 3 |
| Wiedergabe | Media3/ExoPlayer + eigener DSP-`AudioProcessor` |
| Bibliothek | Storage Access Framework (SAF), kein `MANAGE_EXTERNAL_STORAGE` |
| Synchronisation | Wear Data Layer, jetzt hinter einem Mock-Transport |

---

## 0. Umgebungscheck und Einrichtung des virtuellen Geräts

### 0.1 Umgebung (Stand nach dem Setup)

| Prüfung | Ergebnis | Bewertung |
|---|---|---|
| Android Studio | `AI-262.10968.63` (2026.2.2), JBR 25 | vorhanden |
| Android SDK | `C:\Users\super\AppData\Local\Android\Sdk` | vorhanden |
| SDK Platform | `android-37.0` (Android 17, API 37) | **vorhanden** |
| Build-Tools | `36.0.0`, `36.1.0` | vorhanden |
| Platform-Tools / adb | `37.0.1` | vorhanden |
| Emulator | `37.2.13.0` | vorhanden |
| Hardwarebeschleunigung | WHPX erkannt und nutzbar | vorhanden |
| SDK Command-line Tools | `cmdline-tools/latest` (23.0 / Android CLI 1.1.16717390) | **installiert** |
| System-Image API 37 | `system-images;android-37.0;google_apis;x86_64` (ca. 5,3 GB) | **installiert** |
| AVD Pixel 10 | `Pixel_10_API_37`, 1080×2424, 420 dpi (422 ppi), Pixel-10-Skin, 4 GB RAM | **angelegt und gebootet** |
| Weitere AVDs | `Medium_Phone_API_36.1`, `Wear_OS_Large_Round` | unverändert vorhanden |
| Gradle | kein globales `gradle`, aber Wrapper-Distribution 9.6.0 im Cache | Wrapper reicht |
| JDK | `JAVA_HOME` = Zulu 21; Studio-JBR 25 | Gradle 9.6 läuft, bereits verifiziert |
| Gradle-Cache | AGP 9.5.1, Kotlin 2.3.20, Compose BOM 2026.09.00, Media3 1.11.1 | gute Ausgangslage |
| Internet | erreichbar | Downloads möglich |
| Freier Speicher | ca. 535 GB auf C: | ausreichend |

### 0.2 Setup-Ergebnis

Erledigt:

1. **Android SDK Command-line Tools** installiert:
   `C:\Users\super\AppData\Local\Android\Sdk\cmdline-tools\latest`.
   Das moderne CLI heißt `android.exe`; `sdkmanager.bat` und
   `avdmanager.bat` sind ebenfalls vorhanden.
2. **System-Image API 37** installiert:
   `system-images;android-37.0;google_apis;x86_64`.
3. **AVD „Pixel 10 / Android 17“** angelegt:
   `Pixel_10_API_37`, 1080×2424, 420 dpi (422 ppi), Pixel-10-Skin,
   4 GB RAM, 2 GB SD-Karte,
   10 GB Datenpartition. Der Emulator bootet erfolgreich; `adb` meldet
   `Android 17 / SDK 37`.

### 0.3 Brauche ich deine Hilfe?

**Das SDK-Setup ist erledigt; für die Entwicklung ist keine GUI-Hilfe mehr
nötig.** Offen bleibt nur der spätere Test auf echter Hardware:
**Bluetooth-Kopfhörer lassen sich im Emulator nicht real testen.** A2DP und die
Stabilität mit echten Headsets müssen später auf dem Pixel 10 / einem echten
Gerät geprüft werden; im Emulator testen wir Wiedergabe, UI, SAF, DSP und
Zustandswechsel.

### 0.4 Befehle für das Setup (Referenz)

```bat
set SDK=C:\Users\super\AppData\Local\Android\Sdk

%SDK%\cmdline-tools\latest\bin\android.exe sdk install ^
  system-images/android-37.0/google_apis/x86_64

%SDK%\cmdline-tools\latest\bin\avdmanager.bat list device
%SDK%\cmdline-tools\latest\bin\avdmanager.bat create avd ^
  -n "Pixel_10_API_37" ^
  -k "system-images;android-37.0;google_apis;x86_64" ^
  -d "pixel_10"

%SDK%\emulator\emulator.exe -avd Pixel_10_API_37 -no-snapshot -gpu auto
%SDK%\platform-tools\adb.exe devices
```

Hinweis: `avdmanager.bat` mit dem Paketpfad funktioniert zuverlässig aus einer
Batch-Datei; die verschachtelte Quoting-Form direkt aus der Shell kann den
Paketpfad zerschneiden.

AVD-Werte:

| Einstellung | Wert |
|---|---|
| Gerät | Pixel 10 |
| System | Android 17 / API 37, x86_64 |
| Auflösung / Dichte | 1080×2424 / 420 dpi (422 ppi) |
| Geräterahmen / Skin | `pixel_10` |
| RAM | 4096 MB |
| Heap | 512 MB |
| Datenpartition | 10 GB |
| SD-Karte | 2 GB |
| GPU | automatisch (WHPX ist aktiv) |
| Netzwerk | an (für Cover-Abfrage) |

---

## 1. Ziel und Abgrenzung

Die App ist ein **Tinnitus-Retraining-Player**: Musik wird über Bluetooth-
Kopfhörer wiedergegeben und mit einem engmaschigen Notch-Filter bearbeitet.
Die Tinnitus-Frequenz und die Filter müssen präzise einstellbar sein; die
Bedienung im Alltag muss aber auch für technisch unaffine Nutzer funktionieren.

**Im Umfang:**

- Musik aus einem vom Nutzer gewählten Ordner abspielen.
- Ordner-/Titel-Navigation mit Vor/Zurück.
- Drei lokale EQ-Presets mit je sechs Filtern.
- Filtertypen: aus, Notch, Bell, Highshelf, Lowshelf, Lowcut, Highcut.
- Preamp/Trim, Lautstärke, Softlimiter, Clipping-Anzeige.
- Album-Cover aus Datei-Metadaten, sonst Online-Fallback.
- Sinuston zur Tinnitus-Frequenzbestimmung.
- Setup-Seite mit Ordnereinstellung und „Send to Watch“.
- Synchronisationsseite mit Musik- und EQ-Sync als **Mockup**.
- Deutsche Oberfläche, dunkles Design entsprechend den Mockups.

**Nicht im Umfang dieses Projekts:**

- Implementierung der Uhr-App.
- **Jede Änderung am bestehenden Uhr-Projekt** `AndroidStudioProjects/NotchedMusic`
  (Paket, DSP, Empfänger, Manifest, Build-Dateien). Die Anpassung der Uhr an drei
  Presets und das neue Protokoll erfolgt später separat.
- Echtes Wear-Data-Layer-Protokoll in der ersten Ausbaustufe.
- Streamingdienste, Podcasts, Videos, Cloud-Musik.
- Mehrbenutzerverwaltung, Werbung, Analytics.
- Klangliche „Verbesserung“ um jeden Preis: Im Mittelpunkt stehen
  Rechenaufwand, Stabilität, Akku und ein sauberes Notch-/Limiter-Verhalten.

---

## 2. Architekturüberblick

```
┌─────────────────────────────────────────────────────────────────────┐
│  Compose-UI                                                          │
│  Player · EQ · Setup · Synchronize                                    │
└───────────────┬─────────────────────────────────────────────────────┘
                │ StateFlow / ViewModel
┌───────────────▼─────────────────────────────────────────────────────┐
│  Repositories / State                                               │
│  MusicRepository · SettingsRepository · CoverRepository              │
│  SyncRepository (Mock) · PermissionManager                          │
└───────┬───────────────────────┬───────────────────────┬────────────┘
        │                       │                       │
┌───────▼────────┐     ┌────────▼────────┐     ┌───────▼──────────┐
│ :core (pure)   │     │ Playback-Backend │     │ Persistenz        │
│ DSP, Modelle,  │     │ Media3-ExoPlayer │     │ DataStore/JSON    │
│ Protokoll-DTO  │     │ AudioProcessor   │     │ SAF-Index (RAM)   │
└───────┬────────┘     │ MediaSession     │     └───────┬──────────┘
        │              └────────┬─────────┘             │
        │                       │                       │
        └───────────────┬───────┴───────────────────────┘
                        │
                 AudioTrack → System → A2DP → Bluetooth-Kopfhörer
```

### 2.1 Module

| Modul | Inhalt | Abhängigkeiten |
|---|---|---|
| `:core` | reine Kotlin/JVM-Bibliothek: DSP, Filter-/Preset-Modelle, Sync-DTOs, Validierung | keine Android-Abhängigkeiten |
| `:app` | Android-App: UI, Wiedergabe, SAF, Cover, Sync-Mock | `:core`, AndroidX, Media3 |

` :core` ist bewusst Android-frei, damit DSP-Tests auf dem PC laufen und die
gleiche Rechenlogik später von der Uhr-App übernommen werden kann.

### 2.2 Paketstruktur in `:app`

```
de.notchedmusic.phone
├── NotchedMusicApp.kt
├── MainActivity.kt
├── data
│   ├── MusicRepository.kt
│   ├── MusicScanner.kt
│   ├── SafFolder.kt
│   ├── CoverRepository.kt
│   ├── SettingsRepository.kt
│   └── PermissionManager.kt
├── playback
│   ├── PlaybackService.kt
│   ├── PlaybackController.kt
│   ├── EqAudioProcessor.kt
│   ├── EqAudioSinkFactory.kt
│   └── SineTonePlayer.kt
├── sync
│   ├── SyncRepository.kt
│   ├── SyncTransport.kt
│   └── MockSyncTransport.kt
└── ui
    ├── MainScreen.kt
    ├── theme
    ├── player
    ├── eq
    ├── setup
    └── sync
```

---

## 3. Anforderungen → Umsetzung → Abnahme

| Anforderung | Umsetzung | Abnahmekriterium |
|---|---|---|
| Root-Ordner definieren | SAF-Ordnerauswahl in Setup, persistierbare URI-Berechtigung | Nach Neustart bleibt der Ordner samt Leseberechtigung erhalten |
| Root + Unterordner auswählbar | Nur Root und **direkte** Unterordner sind Navigationspunkte | Vor/Zurück erreicht jede dieser Ebenen; tiefere Ebenen erscheinen nicht als eigene Ordner |
| Titel rekursiv aus dem gewählten Ordner | Scanner sammelt alle Audiodateien unter dem gewählten Knoten, egal wie tief | Root enthält alle Titel; Unterordner enthält alle Titel seines Teilbaums |
| Titelauswahl vor/zurück | Titelzeile mit Pfeilen; kein Such-Slider | Pfeile wechseln Titel zyklisch; Play startet den aktuellen Titel |
| Play/Stop | MediaSessionService + ExoPlayer | Play/Stop mit Hardware-, Kopfhörer- und UI-Bedienung |
| Album-Cover automatisch | eingebettetes Cover zuerst; sonst Online-Fallback; Cache | Cover erscheint ohne UI-Blockade; offline bleibt Platzhalter |
| MP3 priorisiert, gängige Formate | ExoPlayer-Decoder + Dateiendungsfilter | MP3, M4A/AAC, OGG/Opus, FLAC, WAV werden erkannt und abgespielt |
| Nur Musik | Dateiendungs- und MIME-Filter, kein Video | Unbekannte Formate werden übersprungen, nicht als Fehlerdialog |
| Lautstärke System/App | eigener App-Gain **und** Systemlautstärke | App-Regler ändert hörbar, Systemregler zusätzlich; kein Klemmen |
| EQ-Bypass (standardmäßig aus) | Modus `BYPASS`: Filter und PreGain werden übersprungen | Bypass lässt den Klang unverändert; Player-Lautstärke und Limiter bleiben aktiv |
| Preset-Dropdown unten rechts | Auswahl aus drei Presets | Auswahl wirkt sofort und wird gespeichert |
| Drei Presets, je sechs Filter | `EqPreset` mit maximal sechs `FilterBand`s | Genau drei Presets; sechster Filter bleibt erhalten; siebter wird abgeschnitten |
| Filter auswählen (blauer Rahmen) | Selektion im Band-Editor | Auswahl zeigt blauen Rahmen; nur der gewählte Band wird editiert |
| Filter on/off (grün) | `enabled`-Schalter im gewählten Band | Grün = aktiv; aus = visuell anders; Klang ändert sich sofort |
| Filtertypen inkl. off | `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`, `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT` | Jeder Typ ist wählbar und ergibt einen stabilen Filter |
| Freq/Q/Gain/Volume per Tastatur | `OutlinedTextField` mit Dezimalkomma/-punkt und Einheiten | Werte werden validiert, geklemmt und sofort übernommen |
| Volume als Trim/Preamp | `preGainDb` je Preset, vor der Filterkette | Reduziert Pegel, bevor Filter ihn anheben; wird im Bypass übersprungen |
| Softlimiter gegen Clipping | stereo-verkoppelter Limiter, Decke −1 dBFS, vor 16-Bit-Wandlung | Ausgang überschreitet die Decke nicht; kein hörbares Knacken |
| Clipping-Anzeige am Player | Peak-Messung vor dem Limiter, Badge am Volume-Slider | Anzeige leuchtet bei Begrenzung, ohne den Regler zu verstellen |
| Setup: Send to Watch → Sync | Navigation zur Synchronize-Seite | Button öffnet die Sync-Seite |
| Root-Folder-Anzeige | lesbarer Pfad/URI-Name in Setup | Bei entzogenem Zugriff erscheint ein verständlicher Hinweis |
| Sinuston zur Frequenzbestimmung | eigener `AudioTrack`-Generator, 10-Hz-Slider, ±1-Hz-Buttons | Frequenz und Lautstärke werden exakt angezeigt und hörbar |
| Sync-Seite: Root + Unterordner | Checkbox-Liste mit Root und direkten Kindordnern | Auswahl zeigt Titelanzahl/geschätzte Größe |
| Sync Musik | Mock-Transport, später Data-Layer-Asset-Upload | Fortschritt/Status erscheinen; echte Übertragung klar als „später“ markiert |
| Sync EQs | Mock-Transport, später `profiles_json` | Alle drei Presets samt Filtern werden als DTO übertragen |
| Rechte automatisch prüfen/anfordern | SAF-Permission + gezielte Runtime-Permissions | Kein Startabbruch durch fehlende Rechte; Hinweis mit Aktion |
| Technisch unaffine Nutzer | große Ziele, klare deutsche Texte, sichere Defaults | Ein Laie kann Musik wählen und abspielen, ohne die EQ-Seite zu verstehen |
| Performance | reine DSP-Kette, keine Allokation pro Buffer, langsamer Scan im Hintergrund | Messwerte siehe Abschnitt 13.6 |

---

## 4. Datenmodell

### 4.1 Filter

```kotlin
enum class FilterType {
  OFF,          // Band wird nicht gerechnet
  NOTCH,        // vollständige Auslöschung bei frequencyHz
  PEAKING,      // Bell
  LOW_SHELF,
  HIGH_SHELF,
  LOW_CUT,      // Hochpass
  HIGH_CUT      // Tiefpass
}

data class FilterBand(
  val type: FilterType = FilterType.OFF,
  val frequencyHz: Double,
  val q: Double,
  val gainDb: Double = 0.0,
  val enabled: Boolean = true,
)
```

- `gainDb` wirkt nur bei Peak/Shelf. Bei Notch, Lowcut und Highcut wird der
  Wert ignoriert, aber gespeichert, damit ein Typwechsel nichts verliert.
- `enabled = false` entspricht klanglich `OFF`, das Band bleibt aber zum
  Bearbeiten erhalten.
- Alle sechs Bänder werden immer angezeigt, auch wenn einige aus sind.

### 4.2 Preset

```kotlin
data class EqPreset(
  val id: String,             // stabil: "p1", "p2", "p3"
  val name: String,           // "Preset 1" ...
  val preGainDb: Double = 0.0, // Trim/Preamp vor den Filtern
  val bands: List<FilterBand> = List(6) { FilterBand(...) },
)
```

- Drei Presets werden als geordnete Liste gespeichert. Das ist die verbindliche
  Zielstruktur; Preset 3 ist kein optionales Anhängsel.
- Es ist **immer genau ein Preset aktiv**, niemals eine Stapelung.
- Die Uhr-App wird später separat auf diese drei Presets angepasst. Das Phone-Projekt
  schickt im Sync-DTO immer alle drei Presets und wartet nicht auf ein Legacy-Format.
- `preGainDb` gehört zum Klangrezept und wird später mit zum Watch-Sync
  übertragen.
- Der Player-Lautstärkeregler ist **nicht** Teil des Presets. Er gehört dem
  Nutzer und wird nie durch einen Sync verstellt.

### 4.3 Grenzen und Validierung

| Wert | Bereich | Begründung |
|---|---|---|
| Frequenz | 20 Hz bis `min(20 000 Hz, 0,45 × SampleRate)` | nahe Nyquist wird unzuverlässig |
| Q | 0,1 bis 30 | darüber schmiert ein Notch hörbar |
| Gain je Band | ±24 dB | genug Reserve für Notch und Shelves |
| Preamp (`preGainDb`) | ±12 dB | Clipping-Vorsorge und Pegelangleich |
| Player-Volume | −60 bis 0 dB | 0 dB = voll; System bleibt zusätzlich dahinter |
| Bänder je Preset | 6 | Anforderung |
| Presets | 3 | Anforderung |
| Summen-Anhebung | max. +12 dB | verhindert zu viel Boost vor dem Limiter |

Die Validierung läuft **einmal beim Ändern eines Presets**, nicht pro Sample.
Ungültige Werte werden geklemmt und im UI erklärt; die Audio-Kette wirft nie
wegen einer Nutzereingabe.

### 4.4 Einstellungen

```kotlin
data class AppSettings(
  val rootUri: String? = null,
  val presets: List<EqPreset> = DefaultPresets.three(),
  val activePresetId: String? = "p1",
  val eqBypass: Boolean = true,          // Anforderung: standardmäßig aus
  val playerVolumeDb: Float = 0f,
  val repeatMode: RepeatMode = RepeatMode.ALL,
  val autoAdvance: Boolean = true,
  val sineFrequencyHz: Int = 440,
  val sineVolumeDb: Float = -20f,
  val lastSyncMs: Long = 0L,
)
```

Standard-Presets:

- **Preset 1:** leerer, neutraler Ausgangszustand; Notch 8 kHz als
  hilfreicher Startpunkt, alle anderen Bänder `OFF`.
- **Preset 2:** breiterer Notch (z. B. 6 kHz, Q 4, −20 dB).
- **Preset 3:** leer, damit der Nutzer ein eigenes Rezept aufbauen kann.

`eqBypass = true` ist Absicht: Beim ersten Start soll der Player
„einfach Musik“ spielen. Erst wer den Notch braucht, aktiviert den EQ.

---

## 5. DSP und Audiokette

### 5.1 Signalweg

```
Datei → ExoPlayer-Decoder → Float-PCM
   → Preset-PreGain (nur wenn EQ aktiv)
   → 6 Biquads pro Kanal (nur bei aktivem Band)
   → Player-Volume
   → Softlimiter (Decke −1 dBFS)
   → 16-Bit-Wandlung
   → AudioTrack → Systemlautstärke → A2DP → Kopfhörer
```

Bei `BYPASS` entfallen PreGain und Biquads; **Player-Volume und Limiter
bleiben aktiv**. So bleibt die Lautstärke beim Umschalten konstant und der
Ausgang kann nicht übersteuern.

### 5.2 Biquad

- RBJ-Cookbook, **Transposed Direct Form II**.
- Koeffizienten in `Double`, Zustand in `Float`.
- Fünf Multiplikationen und vier Additionen pro Sample und Kanal; für alle
  sechs Filtertypen gleich teuer.
- Koeffizientenwechsel werden über ca. 128 Samples überblendet, damit
  Preset-/Reglerwechsel nicht knacken.
- Sample-Rate kommt aus dem tatsächlichen Audio-Ausgang; bei 44,1/48/96 kHz
  werden die Koeffizienten neu berechnet. Ein Ratewechsel ist ein
  Neuaufbau, kein laufender Mischfall.

Formeln (RBJ):

| Typ | Koeffizienten |
|---|---|
| Notch | `b0 = 1`, `b1 = −2 cos ω₀`, `b2 = 1`, `a0 = 1 + α`, `a1 = −2 cos ω₀`, `a2 = 1 − α` |
| Peaking/Bell | mit `A = 10^(gain/40)`: `b0 = 1 + αA`, `b1 = −2 cos ω₀`, `b2 = 1 − αA`, `a0 = 1 + α/A`, `a1 = −2 cos ω₀`, `a2 = 1 − α/A` |
| LowShelf / HighShelf | Standard-RBJ-Shelf mit `α = sin ω₀ / √2` bzw. Q |
| LowCut | Hochpass: `b0 = (1+cos ω₀)/2`, `b1 = −(1+cos ω₀)`, `b2 = (1+cos ω₀)/2`, `a0 = 1+α`, `a1 = −2 cos ω₀`, `a2 = 1−α` |
| HighCut | Tiefpass: `b0 = (1−cos ω₀)/2`, `b1 = 1−cos ω₀`, `b2 = (1−cos ω₀)/2`, `a0 = 1+α`, `a1 = −2 cos ω₀`, `a2 = 1−α` |

Bei Lowcut/Highcut wird `Q` als Flankengüte verwendet; Standard 0,707.
Gain wird ignoriert.

### 5.3 Softlimiter

- Stereo-verkoppelt: **eine** Verstärkung für beide Kanäle, damit das
  Stereobild bei lauten Stellen nicht zusammenklappt.
- Decke **−1 dBFS**; darunter bleibt Luft für Codec-/Rundungsreste.
- Angriff sofort, Erholung exponentiell über ca. 50 ms.
- Zusätzlicher harter Deckel nur als letzte Rundungsabsicherung.
- Bewusst keine Vorausschau in der ersten Version: kein Latenzaufbau, keine
  komplexe Zustandsmaschine. Wenn Hörtests transienten Druck zeigen, kann
  später ein 1-ms-Lookahead nachgerüstet werden; das ist eine eigene
  Messentscheidung, keine Voraussetzung.
- Der Limiter ist **kein** Ersatz für sinnvolle Presets. Die Summen-Anhebung
  wird bereits beim Speichern begrenzt.

### 5.4 Clipping-Anzeige

- Vor dem Limiter wird der Sample-Peak gemessen.
- Zustände:
  - **OK:** Peak ≤ −6 dBFS.
  - **Gelb „Limiter“:** Limiter greift > 0,5 dB ein.
  - **Rot „Clip“:** Eingangspeak vor dem Limiter > 0 dBFS oder
    Limiter-Gain > 3 dB.
- Anzeige am Volume-Slider der Player-Seite; Hysterese gegen Flackern.
- Die App verstellt die Lautstärke **nicht** automatisch. Optionaler
  Ein-Tap-Vorschlag „−2 dB“ wie in der Watch-Referenz.

### 5.5 Media3-Anbindung

- `MediaSessionService` besitzt den `ExoPlayer`.
- `DefaultAudioSink.Builder(context).setAudioProcessorChain(...)` mit einem
  `BaseAudioProcessor` für den EQ.
- `@OptIn(UnstableApi::class)` nur an der Media3-Grenze.
- Der Audio-Thread liest einen **unveränderlichen Snapshot** aus
  `@Volatile`-Feldern; keine DataStore-, Mutex- oder UI-Zugriffe im
  Audio-Callback.
- Ein fester Arbeitspuffer; keine Objekt-Allokation pro Puffer.
- Bei fehlender DSP-Unterstützung oder exotischem Decoder fällt die App auf
  unveränderte Wiedergabe zurück, nicht auf einen Absturz.

### 5.6 Formate

Priorität: **MP3**. Unterstützt werden darüber hinaus:

- `.mp3`
- `.m4a`, `.aac`
- `.ogg`, `.oga`, `.opus`
- `.flac`
- `.wav`

Die Auswahl erfolgt über Dateiendung **und** MIME-Typ, um Systemdateien und
Videos zu vermeiden. Codec-Fehler einzelner Dateien werden pro Titel gemeldet
und übersprungen; die Wiedergabe bleibt stehen.

---

## 6. Bibliothek, Ordner und Titel

### 6.1 SAF statt breiter Medienberechtigung

Der Nutzer wählt den Musik-Root über `ACTION_OPEN_DOCUMENT_TREE`. Die App
speichert die URI und ruft `takePersistableUriPermission(..., READ)` auf.
Damit gilt:

- Kein `READ_MEDIA_AUDIO`, kein `MANAGE_EXTERNAL_STORAGE`.
- Der Nutzer entscheidet selbst, welcher Ordner freigegeben wird.
- Die Freigabe überlebt Neustarts, solange der Nutzer sie nicht entzieht.
- Android 17-konform; keine Sonderbehandlung für Android 13+ nötig.

Beim App-Start und bei jeder Nutzung wird geprüft:

1. Ist eine Root-URI gespeichert?
2. Ist dafür noch eine persistierbare Leseberechtigung vorhanden?
3. Existiert das Verzeichnis noch?

Wenn nicht: unaufdringlicher Hinweis mit „Ordner wählen“, kein Absturz und
kein leerer Player ohne Erklärung.

### 6.2 Das geforderte flache Ordner-Modell

Auswahlpunkte im Player und auf der Sync-Seite:

```
Musik-Root/                     ← auswählbar, enthält rekursiv alles
├── Album A/                    ← auswählbar, enthält rekursiv alles
│   ├── 01 - Titel.mp3
│   └── CD 2/                   ← NICHT auswählbar
│       └── 02 - Titel.mp3      ← zählt zu Album A
├── Album B/                    ← auswählbar
└── locker.mp3                  ← zählt zum Musik-Root
```

- **Root** und **jeder direkte Unterordner** sind Navigationspunkte.
- Tiefere Unterordner sind keine eigenen Punkte.
- Die Dateien eines Punktes werden **rekursiv** eingesammelt.
- Dateien direkt im Root gehören zum Root-Punkt.
- Leere Ordner erscheinen nicht als eigener Eintrag, wenn sie keine Titel im
  Teilbaum haben; der Punkt bleibt aber erreichbar, solange er direkt unter
  dem Root liegt.

### 6.3 Scanner

- `DocumentsContract.buildChildDocumentsUriUsingTree(...)` mit
  `ContentResolver.query`, nicht `DocumentFile` pro Datei: weniger
  Objekt-Allokation und weniger Roundtrips.
- Traversierung im Hintergrund (`Dispatchers.IO`, cancellable).
- Ein Scan erzeugt einen unveränderlichen `LibrarySnapshot` im RAM.
- Kein Neu-Scannen bei jeder UI-Animation; nur bei Start, nach Ordnerwechsel,
  nach Sync und auf „Neu laden“.
- Limits gegen Fehlerbäume: maximale Tiefe 8, maximal 5 000 Dateien,
  500 Root-Knoten. Überschreitung wird geloggt und in der UI erklärt.
- Versteckte Ordner (`.name`) werden übersprungen.
- Sortierung: natürliche Namensreihenfolge; führende Nummern
  (`01 - ...`) werden als Sortierindex erkannt.
- Anzeige: ID3/MediaStore-Titel, sonst Dateiname ohne Endung.

### 6.4 Titel-Daten

```kotlin
data class Track(
  val uri: Uri,
  val documentId: String,
  val displayName: String,   // Dateiname ohne Endung
  val title: String,         // Metadaten oder Dateiname
  val artist: String?,
  val album: String?,
  val durationMs: Long?,
  val fileSizeBytes: Long?,
  val mimeType: String?,
)
```

- Metadaten werden **lazy** beim aktuellen/benachbarten Titel geladen
  (`MediaMetadataRetriever`), nicht für alle 5 000 Dateien beim Scan.
- Ein kleiner LRU-Cache hält die letzten Titel.
- `MediaMetadataRetriever` wird immer geschlossen; Fehler führen zu
  Dateiname als Fallback.

### 6.5 Navigation im Player

- Eine Zeile mit „Ordner“ und Pfeilen links/rechts.
- Eine Zeile mit „Titel“ und Pfeilen links/rechts.
- Ein Tipp auf den Ordnernamen öffnet ein Bottom-Sheet mit allen
  auswählbaren Knoten; das ist die barrierefreie Alternative zu vielen
  Pfeiltipps.
- Titel springen zyklisch; der aktuelle Titel wird beim Wechsel gestoppt und
  neu gestartet (oder bleibt gestoppt, wenn vorher gestoppt).
- Bei „Play“ startet der aktuelle Titel; bei „Stop“ wird angehalten.
- Auto-Advance: standardmäßig an, damit ein Retraining nicht nach drei
  Minuten endet; in den Einstellungen abschaltbar.
- Kein Such-Slider, kein Vorspulen — wie in der Watch-Referenz und den
  Mockups.

---

## 7. Wiedergabe, Bluetooth und Hintergrund

### 7.1 Dienst

- `MediaSessionService` mit `MediaSession` und `ExoPlayer`.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Notification mit Cover, Titel, Künstler und Transportaktionen.
- `setHandleAudioBecomingNoisy(true)`: Stecker ziehen/Headset trennen pausiert.
- Audio-Fokus wird von Media3 verwaltet; kein eigener zweiter Fokus.
- `WAKE_LOCK` nur, wenn Media3 ihn tatsächlich für die Wiedergabe benötigt.
- Bei Kopfhörer-Disconnect bleibt die App pausiert; kein automatisches
  Weiterdudeln über den Lautsprecher (Tinnitus-Kontext).

### 7.2 Bluetooth

- Die App nutzt die Standard-Audiospur; A2DP, Codec und Latenz liegen beim
  System. Das ist Absicht: kein eigenes SCO/HFP, keine Codec-Manipulation.
- `AudioAttributes.USAGE_MEDIA` + `CONTENT_TYPE_MUSIC`.
- Das System merkt sich die Lautstärke pro Bluetooth-Gerät; die App-Regel
  wirkt davor im DSP-Pfad.
- Empfohlene Bedienung in der UI: Systemlautstärke eher hoch lassen und
  mit dem App-Regler fein regeln. Ein Hinweis in Setup, kein Zwang.
- Headset-Tasten (Play/Pause, Next/Previous) laufen über die MediaSession.
- Der Emulator kann keine echte A2DP-Strecke prüfen; dafür ist später ein
  echtes Pixel 10 mit Kopfhörern vorgesehen.

### 7.3 Stabilität

- Lokale Dateien: `LoadControl` so eingestellt, dass kurze I/O-Spitzen
  gepuffert werden, ohne den Akku zu melden.
- Bei `Player.Listener.onPlayerError`: verständliche Meldung, nächster Titel
  nur nach Nutzerentscheidung oder bei Auto-Advance mit Überspringen.
- Kein Netzwerk im Wiedergabepfad; Cover laden asynchron und abbrechbar.
- Ein 10-Minuten-Dauerlauf im Emulator ist Teil der Abnahme.

---

## 8. Oberfläche und Bedienführung

Gestaltung aus den Mockups: schwarzer Hintergrund, kräftiges Blau, große
Pill-Buttons, weiße Typografie. Das ist gleichzeitig OLED-freundlich und für
Laien gut lesbar.

### 8.1 Navigation

Oben ein dreiteiliger Tab: **PLAYER · EQ · SETUP**.
Die Synchronize-Seite ist ein Unterziel von Setup und trägt oben einen
Zurück-Pfeil. Die Tabs bleiben sichtbar; das ist konsistent mit den Mockups.

### 8.2 Player

| Element | Verhalten |
|---|---|
| Tabs | Player / EQ / Setup |
| Ordnerzeile | Name des gewählten Punktes; Pfeile wechseln; Tipp öffnet Auswahl |
| Cover | quadratisch, eingebettetes oder Online-Cover, sonst Platzhalter |
| Play/Stop | großes rundes Ziel, Haptik, Content-Description |
| Titelzeile | Titel, Künstler/Dateiname; Pfeile wechseln Titel |
| Volume | Slider 0–100 %, Anzeige in %, zusätzlich dB im EQ-Kontext |
| Clip-Badge | „Limiter“ gelb / „Clip“ rot, nur wenn relevant |
| EQ BYPASS | Umschalter; Default aktiv (Filter aus) |
| Preset | Dropdown unten rechts: Preset 1/2/3, wirkt sofort |

Der Slider steuert den App-Gain. Die Systemlautstärke bleibt unberührt und
ist über die Systemtasten zusätzlich verfügbar.

### 8.3 EQ

- Drei Preset-Pills: **PRESET 1 / 2 / 3**.
- Sechs Band-Chips:
  - **blauer Rahmen** = aktuell ausgewählt
  - **grün** = aktiv (`enabled = true`)
  - **grau/orange** = aus
- Ein Tipp auf einen Chip selektiert ihn und zeigt die Felder.
- Ein Schalter „Filter aktiv“ im Editor entspricht klanglich `enabled`;
  die Chips reflektieren den Zustand.
- Felder des gewählten Chips:
  - **Type/OFF:** Dropdown mit allen sieben Typen.
  - **Freq:** Hz, Tastatur, Bereichsanzeige.
  - **Q:** dimensionslos, Erklärung „breiter/ schmaler“.
  - **Filter Gain:** dB, nur bei Peak/Shelf wirksam.
  - **Volume:** dB, Preamp des **Presets**; im Bypass ohne Wirkung.
- Unten: „Preset zurücksetzen“, „Von Preset kopieren“ und Live-Hinweis
  „dieses Band ist aus / außerhalb des hörbaren Bereichs“.
- Die Felder akzeptieren Komma und Punkt. Nach Verlassen des Feldes wird
  formatiert und validiert; während der Eingabe bleibt der Text erhalten.
- Die sechs Bänder sind immer sichtbar, auch bei `OFF`.

### 8.4 Setup

| Element | Verhalten |
|---|---|
| Send to Watch | öffnet Synchronize |
| Select Root Folder | SAF-Picker |
| Root Folder Path | lesbarer Pfad; bei fehlendem Zugriff rot mit Aktion |
| Play Sine Wave | Start/Stop des Testtons |
| Freq | Slider in 10-Hz-Schritten, Anzeige in Hz |
| + / − | 1-Hz-Schritte, mit langem Druck zehn Schritte |
| Volume | Testlautstärke in %, mit Warnhinweis |
| Musik neu einlesen | optionaler Rescan |
| Diagnose | Version, letzter Scan, Sync-Status, Audio-Route |

### 8.5 Synchronize

- Kopfzeile „Synchronize EQ Presets and Music to Watch“.
- Liste:
  - Root mit Anzahl/geschätzter Größe
  - jede direkte Unterebene
- Checkboxen; „Alle auswählen/abwählen“; ausgewählte Titel werden
  dedupliziert.
- Zwei große Buttons: **SYNC EQs** und **SYNC MUSIC**.
- Fortschrittsanzeige mit Datei/Preset, Prozent, Abbrechen.
- Deutliches Banner: **„Mock – Uhr-App wird später separat angepasst“**.
- Nach Abschluss: Zeitstempel und „zuletzt synchronisiert“.
- Fehlerzustände: abgebrochen, Watch nicht erreichbar, Datei nicht lesbar;
  jeweils mit verständlichem Text und Wiederholen.

### 8.6 Bedienbarkeit für technisch unaffine Nutzer

- Deutsche Beschriftungen, keine englischen Fachbegriffe im Hauptfluss.
- Große Touch-Ziele (min. 48 dp), ausreichender Kontrast.
- Frequenz/Q/Gain mit Einheiten und Klartext-Hilfe; EQ bleibt „Expertenseite“.
- Default: EQ aus, Lautstärke 0 dB, Auto-Advance an.
- Keine blockierenden Dialoge beim Start; Rechte werden am Ort der Nutzung
  erklärt.
- Fehler immer mit einer Aktion: „Ordner wählen“, „Erneut versuchen“,
  „Systemlautstärke prüfen“.
- Screenreader: Content-Descriptions, Rollen, Statusansagen.
- Keine reine Farbinformation: aus/ausgewählt/aktiv zusätzlich durch Icon
  und Text.

---

## 9. Berechtigungen (Android 17 / API 37)

### 9.1 Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Falls für Watch/Headset-APIs nötig; nicht für Data Layer allein -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- **Kein** `READ_MEDIA_AUDIO`, **kein** `MANAGE_EXTERNAL_STORAGE`,
  **kein** breiter Speicherzugriff.
- `.mp3` etc. werden nicht als Intent-Filter beansprucht; die App ist keine
  allgemeine Datei-App.
- `MediaSessionService` mit `foregroundServiceType="mediaPlayback"`.

### 9.2 Laufzeitprüfung

| Berechtigung | Wann | Verhalten bei Ablehnung |
|---|---|---|
| SAF-Ordnerzugriff | Start + bei jeder Bibliotheksnutzung | Banner, App bleibt bedienbar, kein leerer Player ohne Erklärung |
| `POST_NOTIFICATIONS` | Beim ersten Play | Wiedergabe läuft, aber Hinweis auf fehlende Medien-Notification |
| `BLUETOOTH_CONNECT` | Nur wenn eine Watch-/Headset-API sie wirklich braucht | Sync/Status mit klarer Fehlermeldung; Kern-App bleibt nutzbar |
| Netzwerk | Cover-Abfrage | Platzhalter, keine Fehlermeldung im Hauptfluss |

Regeln:

1. **Erst prüfen, dann anfordern, dann erst degradieren.**
2. Nie beim App-Start alle Rechte in einem Schwall erfragen.
3. Jede Ablehnung erklärt, was ohne das Recht fehlt.
4. Wenn der Nutzer den Ordnerzugriff entzieht, wird die Bibliothek als
   „nicht verfügbar“ markiert, nicht als leer.
5. Android-17-Eigenheiten: Edge-to-Edge mit `WindowInsets`, Predictive Back
   aktivieren, Notification-Kanal bei API 33+ anlegen.
6. Ein laufender Service darf durch eine späte Rechteentscheidung nicht
   abstürzen; im Zweifel pausieren und den Nutzer zurückholen.

---

## 10. Synchronisation und API

### 10.1 Jetzt: Transport-Schnittstelle + Mock

```kotlin
interface SyncTransport {
  suspend fun isWatchAvailable(): Boolean
  suspend fun sendPresets(payload: PresetSyncPayload): SyncResult
  fun sendMusic(items: List<MusicUpload>, onProgress: (Float) -> Unit): SyncResult
  suspend fun cancel()
}
```

- `MockSyncTransport` simuliert pro Datei/Preset 50–300 ms, meldet
  Fortschritt und kann abbrechen. Es werden keine echten Daten geschrieben.
- Die UI zeigt den Mock-Status klar an. So ist die Bedienung schon jetzt
  vollständig testbar, ohne die spätere API zu erfinden.
- Die echte Implementierung wird später hinter dieselbe Schnittstelle gehängt,
  sobald die Uhr-App separat auf das v3-Protokoll angepasst ist. Das Uhr-Projekt
  wird in diesem Vorhaben nicht verändert.

### 10.2 Referenzprotokoll der vorhandenen Wear-App

Die Wear-App `de.notchedmusic.watch` verwendet bereits:

| Zweck | Pfad / Schlüssel |
|---|---|
| Fähigkeit | `de.notchedmusic.watch.filter_profiles` |
| Profile senden | `/filter/profiles`, `profiles_json` |
| Profile anfordern | `/filter/request` (leer) |
| Musik senden | `/music/upload`, `audio` (Asset), `album`, `file_name` |

Dieses Protokoll ist **nur eine Referenz** aus dem bestehenden Uhr-Projekt und
wird hier nicht verändert. Es hat zwei Presets (A/B), während die Phone-App
verbindlich drei Presets verwaltet. Die Angleichung ist ein späterer,
separater Auftrag am Uhr-Projekt.

### 10.3 Neues DTO „v3“ (Vorschlag, noch nicht festgeschrieben)

```json
{
  "version": 3,
  "presets": [
    {
      "id": "p1",
      "name": "Preset 1",
      "preGainDb": 0.0,
      "bands": [
        {
          "type": "NOTCH",
          "frequencyHz": 1960.0,
          "q": 2.8,
          "gainDb": -20.0,
          "enabled": true
        }
      ]
    }
  ],
  "activePresetId": "p1"
}
```

- `version` erlaubt spätere Erweiterungen.
- Unbekannte Felder werden ignoriert, nie geworfen.
- Maximal drei Presets, je sechs Bänder; das Gegenstück validiert erneut.
- `playerVolumeDb` und Systemlautstärke werden **nicht** mitgesendet.
- Ein Preset ist immer ein vollständiges Klangrezept; die Uhr soll nicht
  raten müssen, welche Bänder fehlen.

**Kompatibilitätsentscheidung (festgelegt):** Die Phone-App arbeitet immer mit
drei Presets und sendet im echten Sync ausschließlich das v3-Paket mit allen
drei Presets. Die vorhandene Uhr-App kann v3 noch nicht lesen; sie wird dafür
später **außerhalb dieses Projekts** angepasst. Bis dahin:

- Es gibt **kein** stilles Herunterrechnen auf `slotA`/`slotB` und keinen
  Datenverlust von Preset 3.
- Der echte Watch-Sync bleibt deaktiviert bzw. meldet klar
  „Uhr-App muss noch angepasst werden“.
- Der Mock-Transport zeigt und simuliert trotzdem alle drei Presets, damit
  die Phone-Oberfläche und das Datenmodell vollständig geprüft werden können.
- Das bestehende Uhr-Projekt wird weder verändert noch als Build-Abhängigkeit
  eingebunden. Es dient nur als Nachschlagewerk für DSP- und Pfadkonventionen.

Die Anpassung der Uhr (drei Slots statt A/B, v3-Empfänger, Tests) ist ein
separater Folgeauftrag und nicht Teil dieses Phone-Projekts.

### 10.4 Musik-Sync

- Pro Datei ein Data-Layer-Item mit Asset, Albumname und Dateiname.
- Der bestehende Uhr-Empfänger legt atomar über `.part` ab. Dieses Verhalten
  wird nur referenziert; das Uhr-Projekt wird nicht verändert.
- Vor dem Senden: Existenz, Lesbarkeit, Größe und Endung prüfen.
- Fortschritt: „3 / 12 Dateien · 4,2 MB von 9,1 MB“.
- Doppelte Auswahl wird über die Track-URI dedupliziert.
- Abbrechen lässt bereits übertragene Dateien liegen; die Uhr ignoriert
  halbe `.part`-Dateien.
- Große Dateien: Chunking/Resume ist ein späterer Ausbau; für den ersten
  Wurf reicht ein Asset pro Datei. Die Grenzen des Data Layer werden vor der
  echten Implementierung gemessen.

### 10.5 Robustheit der API

- Jede eingehende Nachricht wird größenbegrenzt (Presets < 8 KiB Text).
- Ungültiges JSON wird geloggt und verworfen, nie als Exception nach oben.
- Pfade werden entschärft (`..`, `/`, `\`), wie im bestehenden Empfänger.
- Die Phone-App fordert Profile an (`/filter/request`), damit die Uhr nach
  einem Sync sofort den neuen Stand hat.
- Zeitstempel `lastSyncMs` und eine kurze Statuszeile im Setup.
- Idempotenz: erneutes Senden desselben Presets ändert nichts anderes als
  den Zeitstempel.

---

## 11. Album-Cover

Reihenfolge:

1. **Eingebettetes Cover** aus den Datei-Metadaten
   (`MediaMetadataRetriever.embeddedPicture`). Das ist schnell, offline
   verfügbar und deckt die meisten MP3s mit ID3v2-APIC ab.
2. **Lokaler Cache** (Coil-Disk-Cache, Schlüssel aus Titel/Album/Künstler
   normalisiert).
3. **Online-Fallback** nur, wenn kein eingebettetes Cover vorhanden ist.
   Primär MusicBrainz + Cover Art Archive (kein API-Key, aber User-Agent und
   Rate-Limit nötig), sekundär iTunes Search API als Ausweichweg.
4. **Platzhalter** bei Timeout, Offline oder keinem Treffer.

Regeln:

- Kein Netzwerkaufruf im Audio- oder UI-Thread.
- Anfrage-Timeout 5 s; maximal 1 Anfrage/s; Ergebnis wird gecacht.
- Nur Titel/Künstler/Album werden gesendet, keine Dateipfade, keine
  Hörhistorie.
- Cover-Laden ist abbrechbar, wenn der Titel wechselt.
- Nur für unterstützte Musikdateien, nicht für beliebige Dateien.
- Bei deaktiviertem Netzwerk bleibt die App vollständig nutzbar.
- Cover-Größe auf die Anzeige begrenzen (z. B. 512 px), nicht Originalbilder
  im Speicher halten.

---

## 12. Persistenz und Zustand

- **DataStore Preferences** für die kleinen Einstellungen:
  - Root-URI
  - drei Presets als JSON
  - aktives Preset
  - EQ-Bypass
  - Player-Volume
  - Repeat/Auto-Advance
  - Sinusfrequenz/-lautstärke
  - letzter Sync
- **Ein** JSON-Eintrag für alle Presets, damit kein halb geschriebener Satz
  aus drei verschiedenen Ständen entsteht.
- Schreiben über einen **konflatierten Kanal**: schnelle Reglerbewegungen
  erzeugen nur den letzten Stand; ein älterer Schreiber überschreibt nie
  einen neueren.
- Ein `StateFlow<AppSettings>` ist die Quelle der Wahrheit für die UI.
- Der Audio-Thread liest nur einen unveränderlichen Snapshot; DataStore-I/O
  findet nie dort statt.
- Fehlerhafte oder unbekannte JSON-Stände fallen auf Defaults zurück und
  werden geloggt, statt die App zu blockieren.
- Formatversion im JSON, damit spätere Migrationen möglich sind.
- Optional: Export/Import aller Presets als Datei ist ein späteres
  Nice-to-have, kein Startumfang.

---

## 13. Test- und Abnahmekonzept

### 13.1 Reine JVM-Tests (`:core`)

- Biquad-Impulsantwort und Notch-Tiefe bei 1 kHz/8 kHz.
- Stabilität bei Q = 30 und Frequenzen nahe Nyquist.
- Lowcut/Highcut-Flanken, Shelf-Verstärkung, Bell-Gain.
- Koeffizienten-Überblendung: kein Sprung, kein DC-Drift.
- Limiter: Decke wird nie überschritten; Stereo-Kopplung; Release-Zeit.
- Preset-Validierung: sechs Bänder, Summen-Anhebung, Klemmung.
- Clip-Erkennung: Schwellen und Hysterese.
- Protokoll-JSON: v3 lesen/schreiben, unbekannte Felder, alte v2-Daten.
- Benchmark: 6 Bänder stereo 48 kHz im Loop; Ziel < 15 ns/Sample,
  keine Allokation im Pfad.

### 13.2 Android-Tests (`:app`)

- SAF-Scanner mit Testbaum (Mock-ContentResolver oder dokumentierte
  Instrumentierung): Root + direkte Unterordner, rekursive Titel,
  tiefere Ordner nicht als Knoten.
- Permission-Entzug: gespeicherter Root, entzogene URI → Fehlerzustand.
- Settings-Repository: DataStore-Roundtrip, konflatierter Schreiber,
  Korruptions-Fallback.
- Cover-Repository: eingebettet vor Online, Cache, Offline.
- Sync-Mock: Fortschritt, Abbruch, Fehler, Deduplizierung.
- Compose-UI-Tests: Player-Buttons, EQ-Auswahl/Farben, Sync-Auswahl.

### 13.3 Emulator-Test (Pixel 10, API 37)

1. Musikordner über `adb push` nach `/sdcard/Download/TestMusic/`.
2. In der App per SAF auswählen.
3. Ordner-/Titel-Navigation, Play/Stop, Auto-Advance.
4. EQ: Preset wechseln, Band aktivieren, Frequenz bei laufender Musik ändern.
5. Bypass ein/aus; Lautstärke; Clip-Anzeige mit einem absichtlich lauten
   Preset.
6. Sinuston: 10-Hz-Slider, ±1 Hz, Stop.
7. Sync-Mock: Auswahl, Fortschritt, Abbruch.
8. App-Neustart: Root, Presets und Volume sind erhalten.
9. Berechtigung entziehen (Files-App/`adb shell appops`), Fehlerbild prüfen.
10. 10-Minuten-Dauerlauf: keine Underruns, kein Speicherwachstum.

### 13.4 Echtes Gerät (später)

- Pixel 10 mit Android 17.
- Bluetooth-Kopfhörer: Pairing, A2DP, Kopfhörer-Tasten, Trennen/Verbinden.
- Hintergrundwiedergabe bei ausgeschaltetem Bildschirm.
- Reale Bibliothek > 2 000 Dateien, gemischte Formate.
- Akkuverbrauch bei 1 h Wiedergabe.

### 13.5 Abnahmekriterien je Seite

| Seite | Abnahme |
|---|---|
| Player | Ein Laie kann nach dem ersten Start Musik abspielen; alle fünf Kernbuttons funktionieren ohne Erklärung |
| EQ | Drei Presets, sechs Bänder, alle sieben Typen; Werte per Tastatur; Bypass wirkt sofort |
| Setup | Root-Ordner bleibt nach Neustart; Sinuston ist in 1-Hz-Schritten einstellbar |
| Sync | Auswahl ist eindeutig; Fortschritt sichtbar; Mock ist klar gekennzeichnet |
| Stabilität | 10-Minuten-Dauerlauf ohne Absturz/Underrun; entzogene Rechte führen zu sauberem Fehlerbild |
| Performance | DSP-Messwerte und Speicherbudget eingehalten (13.6) |

### 13.6 Messbudgets

| Bereich | Ziel | Messung |
|---|---|---|
| DSP | 6 Bänder stereo 48 kHz < 15 ns/Sample | JVM-Benchmark, 10 000 Blöcke, warmup |
| Audio | 0 Underruns im 10-Minuten-Test | ExoPlayer-/AudioTrack-Statistik, Log |
| Allokation | keine neuen Objekte pro Audio-Puffer | Code-Review + Benchmark |
| Bibliothek | 2 000 Dateien < 1,5 s Scan | Zeitmessung mit Testbaum |
| UI | kein Main-Thread-I/O > 16 ms | StrictMode im Debug-Build |
| Cover | kein UI-Block; Timeout 5 s | Trace |
| App-Start | < 1 s bis bedienbar bei gecachtem Index | Cold-/Warm-Start-Messung |

---

## 14. Roadmap

| Meilenstein | Inhalt | Fertig, wenn |
|---|---|---|
| **M0 – Werkzeuge** | SDK-CLI, API-37-Systemimage, AVD Pixel 10, Projektgerüst, Gradle-Build | `assembleDebug` + `testDebugUnitTest` grün; Emulator startet |
| **M1 – Kern** | `:core`: Modelle, Validierung, Biquad, Limiter, Clip-Erkennung, DTOs | JVM-Tests und Benchmark grün |
| **M2 – Bibliothek** | SAF-Auswahl, Scanner, flaches Ordner-Modell, Settings-Repository, Permissions | Root bleibt erhalten; rekursive Titel korrekt |
| **M3 – Wiedergabe** | ExoPlayer, MediaSessionService, DSP-Processor, Volume, Limiter, Notification | Play/Stop mit Filter hörbar; Hintergrund läuft |
| **M4 – Player-UI** | Tabs, Ordner/Titel-Navigation, Cover, Volume, Bypass, Preset | Mockup-Player vollständig bedienbar |
| **M5 – EQ-UI** | Drei Presets, sechs Bänder, Editor, Persistenz, Bypass | Alle Filtertypen editierbar und hörbar |
| **M6 – Setup + Sinus** | Root-Anzeige, Send-to-Watch-Navigation, Sinuston | Frequenz/Pegel präzise, 10-Hz-Slider, ±1 Hz |
| **M7 – Sync-Mock** | Checkbox-Liste, zwei Sync-Buttons, Fortschritt, Abbruch | Mock vollständig, klar gekennzeichnet |
| **M8 – Feinschliff** | Cover online, Accessibility, Fehlerbilder, Lint, Dark Theme, Tests | Abnahmekriterien aus 13.3 erfüllt |
| **M9 – Echttest** | Pixel 10, Bluetooth, reale Bibliothek, Akku, Release-Build | Freigabekriterien aus 13.4 erfüllt |

Reihenfolge bewusst: **erst spielbar, dann fein einstellbar, dann Sync.**
Die Sync-Buttons bleiben bis M7 sichtbar, aber als Mock gekennzeichnet.

---

## 15. Projektstruktur und Build

### 15.1 Dateien (Soll)

```
NotchedMusic-Phone/
├── PLAN.md                       ← dieses Dokument
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── core/
│   ├── build.gradle.kts
│   ├── src/main/kotlin/de/notchedmusic/core/
│   │   ├── dsp/Biquad.kt
│   │   ├── dsp/Limiter.kt
│   │   ├── dsp/EqChain.kt
│   │   ├── dsp/LevelMeter.kt
│   │   ├── model/FilterBand.kt
│   │   ├── model/EqPreset.kt
│   │   ├── model/AppSettings.kt
│   │   └── protocol/SyncPayload.kt
│   └── src/test/kotlin/...
├── app/
│   ├── build.gradle.kts
│   ├── src/main/AndroidManifest.xml
│   ├── src/main/kotlin/de/notchedmusic/phone/...
│   ├── src/main/res/values/strings.xml
│   ├── src/main/res/values/colors.xml
│   ├── src/main/res/drawable/...
│   ├── src/test/kotlin/...
│   └── src/androidTest/kotlin/...
└── docs/                         ← optional: Messprotokolle, Testdaten
```

### 15.2 Versionen

An die vorhandene, funktionierende Watch-Basis angelehnt (nur als
Versionsreferenz, keine Änderung dort) und im Cache vorhanden:

| Baustein | Version | Bemerkung |
|---|---|---|
| Gradle Wrapper | 9.6.0 | bestehende Distribution im Cache |
| Android Gradle Plugin | 9.5.1 | vorhanden |
| Kotlin | 2.3.20 | vorhanden |
| Compose BOM | 2026.09.00 | vorhanden |
| Kotlinx Serialization | 1.12.0 | vorhanden |
| Media3 | 1.11.1 | vorhanden |
| DataStore | 1.3.0 | vorhanden |
| Play Services Wearable | 20.0.1 | für spätere echte Sync-Anbindung |
| Navigation Compose | 2.10.2 | stabil; Download nötig |
| Coil 3 | 3.6.3 | Cover-Cache; Download nötig |
| WorkManager | 2.12.0 | optional für Hintergrund-Cover-Sync |
| core-ktx / Activity / Lifecycle | 1.18.0 / 1.13.0 / 2.10.0 | vorhanden |

`minSdk = 33` (Android 13) als Startwert: Das vermeidet die Legacy-
Speicherberechtigungen und passt zur Watch-Basis (minSdk 33). Wenn die App
auf älteren Telefonen laufen soll, ist das eine bewusste spätere Erweiterung.

### 15.3 Build-Konfiguration

- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 33`.
- Java/Kotlin-Toolchain 17.
- Compose-Compiler-Plugin über Kotlin-Version.
- `buildFeatures { compose = true; buildConfig = true }` (BuildConfig für
  Debug-Diagnose).
- `org.gradle.parallel=true` erst nachprüfen, nicht blind einschalten.
- `org.gradle.configuration-cache=true` wie in der Referenz.
- Keine dynamischen Versionen, keine `latest.release`.

### 15.4 Build-Kommandos

```bat
cd Z:\android_projekte\NotchedMusic\NotchedMusic-Phone
gradlew.bat :assembleDebug
gradlew.bat :testDebugUnitTest
gradlew.bat :connectedDebugAndroidTest
```

Erwartung: `BUILD SUCCESSFUL`; Warnungen sind zu erklären, keine neuen
Deprecation-Sümpfe.

---

## 16. Risiken, offene Punkte und Entscheidungen

### 16.1 Risiken

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| 3 Presets vs. 2 Presets in der bestehenden Uhr-App | Echter Sync funktioniert erst nach Uhr-Anpassung | v3-Protokoll mit allen drei Presets; bis zur separaten Uhr-Anpassung nur Mock; keine Änderung am Uhr-Projekt |
| SAF-Scan großer Bäume | lange Wartezeit, UI-Ruckeln | Cursor-Scan im Hintergrund, Limits, Snapshot im RAM |
| Online-Cover | Datenschutz, Rate-Limits, Offline | eingebettetes Cover zuerst, Cache, Timeout, klare Opt-out-Möglichkeit |
| Echte Bluetooth-Strecke | im Emulator nicht prüfbar | Test auf Pixel 10 mit Kopfhörern; Standard-Audiospur statt Sonderweg |
| Limiter vs. Transienten | hörbare Verzerrung bei extremen Presets | konservative Summen-Anhebung, Messung, optionales Lookahead |
| Android-17-Verhalten | neue Permission-/Service-Regeln | früh gegen API 37 bauen, Permissions deklarativ und lazy anfordern |
| Lautstärke-Erwartung | Nutzer verwechselt System- und App-Volume | beide sichtbar erklären; System bleibt unangetastet |
| Rechteentzug | App zeigt leere Bibliothek | expliziter „nicht verfügbar“-Zustand mit Aktion |
| Emulator-Performance | falsche DSP-Messwerte | DSP-Benchmark auf JVM; Emulator nur für Integration |

### 16.2 Offene Entscheidungen (Default in Klammern)

1. **Watch-Sync (entschieden):** Die Uhr wird später separat angepasst. Die Phone-App sendet
   immer drei Presets im v3-Format; bis zur Anpassung bleibt der echte
   Sync deaktiviert und der Mock aktiv. (Keine offene Entscheidung mehr.)
2. **Auto-Advance/Repeat:** standardmäßig an (Default) oder nur Play/Stop
   wie im Mockup?
3. **Sinuston-Bereich:** 100–15 000 Hz (Default) oder 20–20 000 Hz?
4. **Online-Cover:** MusicBrainz + Cover Art Archive (Default) oder
   iTunes Search API?
5. **App-Name:** `NotchedMusic`/`NotchedMusicPhone` (Default) oder eigener
   Produktname?
6. **minSdk:** 33 (Default) oder tiefer für ältere Geräte?
7. **EQ-Volume:** Preamp je Preset (Default, wie im Mockup) oder zusätzlich
   je Band? Die Anforderung nennt Volume unter den Filterfeldern; technisch
   ist ein Preset-Preamp die saubere Lösung.
8. **Echtes Watch-Sync im ersten Release:** Mock (Default laut Anforderung)
   oder schon Data Layer?
9. **Systemlautstärke-Anzeige:** nur Hinweis (Default) oder zusätzlich
   in der App sichtbar?
10. **Preset-Export:** Startumfang oder später?

### 16.3 Architektur-Entscheidungen, die stehen

- SAF statt breiter Medienberechtigung.
- Media3/ExoPlayer für Musik, eigener `AudioTrack` nur für den Sinuston.
- IIR-Biquads statt FFT/Linear-Phase.
- Genau ein aktives Preset, keine Stapelung.
- Preamp und Player-Volume getrennt; Sync verstellt nie den Hörpegel.
- Limiter vor der 16-Bit-Wandlung; Decke −1 dBFS.
- Drei Presets mit je sechs Bändern; `OFF` ist ein Filtertyp. Das ist final.
- Das bestehende Uhr-Projekt bleibt unangetastet und wird später separat
  angepasst; Phone-Sync sendet solange nur Mock bzw. klar gekennzeichneten
  „noch nicht unterstützt“-Status.
- Sync jetzt Mock, Schnittstelle von Anfang an sauber.
- `:core` bleibt Android-frei und wiederverwendbar.

---

## 17. Sofort nächste Schritte nach Freigabe

0. **Vor Beginn: bestätigen, dass das Uhr-Projekt unverändert bleibt.** Alle
   Arbeiten finden ausschließlich in `NotchedMusic-Phone` statt.
1. SDK-Pakete und AVD einrichten (Abschnitt 0.4).
2. Projektgerüst aus der Watch-Basis ableiten:
   `settings.gradle.kts`, `libs.versions.toml`, Wrapper, zwei Module.
3. `:core` mit Datenmodellen, Biquad, Limiter und Tests bauen.
4. SAF-Auswahl und Scanner implementieren; mit Testbaum im Emulator prüfen.
5. Playback-Service mit DSP und MediaSession aufsetzen.
6. Player-UI gegen die Mockups bauen.
7. EQ-UI und Persistenz.
8. Setup/Sinus.
9. Sync-Mock.
10. Cover, Feinschliff, Messungen, echter Bluetooth-Test.

---

## 18. Anhang: Mockup-Referenz

Die vier Screenshots zeigen:

1. **Synchronize:** Liste mit Root/Unterordnern, Checkboxen, zwei
   Sync-Buttons.
2. **Player:** Tabs, Ordner-/Titelpfeile, Cover, Play, Volume, Bypass,
   Preset.
3. **EQ:** Drei Presets, sechs Filterchips (gewählt/blau, aktiv/grün),
   Type/Freq/Q/Gain/Volume-Felder.
4. **Setup:** Send to Watch, Select Root Folder, Root-Pfad, Play Sine Wave,
   Frequenz-Slider mit ±-Buttons, Volume.

Der Plan folgt diesen Mockups. Zusätzliche Elemente (Clip-Badge,
Ordner-Bottom-Sheet, „Filter aktiv“-Schalter, Sync-Mock-Banner) sind
Ergänzungen, die die Bedienbarkeit verbessern, ohne die Mockup-Struktur zu
verändern.
