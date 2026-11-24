# Projektstatus – NotchedMusic Phone

**Stand:** 24.11.2026 (Bugfix: Hintergrundwiedergabe nach Anruf/Sperre)
**Gesamtstatus:** Funktionsfähig; echter Wear-Data-Layer-Sync auf Pixel 10 ↔ TicWatch Pro 5
abgenommen. Die Watch-App wurde auf denselben Stand gebracht (drei Presets, sieben Filtertypen,
Musik-Spiegel).

---

## 1. Kurzfassung

Die Phone-App ist vollständig umgesetzt:

- Bibliothek über SAF ohne `MANAGE_EXTERNAL_STORAGE`
- Media3/ExoPlayer mit eigener DSP-Kette (Biquad-EQ + stereo-verkoppelter Limiter)
- Drei Presets mit je sechs Bändern, sieben Filtertypen, Tastatureingabe und Bypass
- Sinuston-Tester mit 1-Hz-Schritten und Auto-Advance
- Hintergrundwiedergabe mit Vordergrund-Medienmeldung samt Cover und Transportsteuerung
- **Echter Sync** für Presets und Musik über die Wear Data Layer (Bluetooth/WLAN), mit
  Fortschritt, Abbruch, Timeout, Retry, Manifest + SHA-256 und Commit
- Persistenz in DataStore, versioniertes JSON, Fallback auf Defaults
- **Wiederaufnahme:** Ordner und zuletzt gespielter Titel werden gespeichert; nach einem
  App-Neustart stehen sie wieder im Player (Titel über SAF-Dokument-ID, nicht über den Pfad)
- **EQ-Statistik:** vierter Tab `STATS`; Phone zählt nur laufende Musik mit aktivem EQ, die Uhr
  zählt nur laufende Musik mit aktivem Preset. Journal + Tagesdatei, Verdichtung beim Öffnen der
  Statistik-Seite und beim Sync; Übergabe an das Phone per DataItem mit idempotenter Report-ID,
  Uhr löscht ihre Statistik erst nach bestätigtem ACK
- **Hintergrundwiedergabe:** Media3 verwaltet Medien-Meldung und Vordergrunddienst; Anruf,
  Auflegen und Gerätesperre beenden die Wiedergabe nicht mehr vorzeitig

Die Watch-App unter `Z:\android_projekte\NotchedMusic\NotchedMusic-Watch` wurde auf denselben
Datenstand migriert: drei Presets, sieben Filtertypen, Auswahl nur auf der Uhr, Musik-Spiegel mit
Löschung erst nach bestätigtem Commit.

---

## 2. Umgebung und Build

| Komponente | Wert |
|---|---|
| Android Studio | 2026.2.2 |
| SDK-Plattform | android-37 (Android 17) |
| Build-Tools | 36.0.0 / 36.1.0 |
| Android-Tools | platform-tools 37.0.1, Emulator 37.2.13 |
| JDK | Java 21 (Zulu), Gradle-Toolchain 17 |
| Emulator | `Pixel_10_API_37`, 1080×2424 |
| Reales Phone | Pixel 10, Android 17 / API 37, 1080×2424 |
| Reale Uhr | TicWatch Pro 5, Wear OS 4 / **API 33**, 466×466 |
| AGP / Kotlin | 9.5.1 / 2.3.20 |
| Compose BOM / Material 3 | 2026.09.00 / 1.4.0 |
| Media3 | 1.11.1 |
| Play Services Wearable | 20.0.1 |
| minSdk / compileSdk / targetSdk | 33 / 37 / 37 |
| applicationId | `de.notchedmusic.phone` (bewusst auch für die Watch-App) |

Bau und Prüfung (alle grün):

```bat
gradlew.bat :core:test :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
gradlew.bat :app:lintDebug
```

- **102 Unit-Tests** (74 in `:core`, 28 in `:app`), 0 Fehler, 0 übersprungen.
- **Lint:** 0 Fehler, 0 Warnungen.
- **Release-APK (installiert):** `app/build/outputs/apk/release/app-release-signed.apk` (≈ 32 MB, lokal mit Debug-Schlüssel signiert, nicht debuggable). Debug-APK ≈ 42 MB.

---

## 3. Umsetzungsstand nach Plan

| Bereich | Stand | Nachweis |
|---|---|---|
| SAF-Ordnerauswahl, persistierbare Freigabe | fertig | Frischer Picker, Freigabe „ALLOW“, über Neustart erhalten |
| Root + direkte Unterordner | fertig | `TestMusic` + `Album A` + `Album B`; „CD 2“ erscheint nicht als Knoten |
| Rekursiver Scan, natürliche Sortierung | fertig | 2 005 Dateien in **1 389 ms** (Ziel < 1 500 ms), danach 6 Titel / 3 Knoten in 103 ms |
| Formate | fertig | Filter für MP3/M4A/AAC/OGG/Opus/FLAC/WAV inkl. MIME-Prüfung, MP3 zuerst |
| Player mit Titel-/Ordnerpfeilen | fertig | Pfeile, zyklischer Wechsel, Bottom-Sheet-Ordnerwahl |
| Wiedergabe, Auto-Advance, Repeat | fertig | AUS/TITEL/ALLE; 13-Minuten-Dauerlauf, Position läuft weiter |
| App-Lautstärke 0–100 % / −60…0 dB | fertig | Slider geprüft (50 % = −30 dB, 100 % = 0 dB) |
| Album-Cover | fertig | eingebettet → Speicher → Disk-Cache (512 px) → MusicBrainz/iTunes → Platzhalter; eingebettet offline verifiziert |
| EQ: 3 Presets × 6 Bänder | fertig | Default Preset 1: Notch 8 kHz, Q 8, Gain −20; Preset 2: 6 kHz/Q 4; Preset 3 leer |
| EQ-Typen und Eingabefelder | fertig | OFF, Notch, Bell, Low-Shelf, High-Shelf, Lowcut, Highcut; Komma und Punkt |
| Bypass | fertig | Standard an; Filter/PreGain entfallen, Lautstärke und Limiter bleiben |
| DSP-Kette | fertig | Biquad transponiert DF-II, 128-Sample-Ramp, Stereo-Limiter −1 dBFS |
| Clipping-Anzeige | fertig | OK/Limiter/Clip mit 400 ms Hysterese; „Clip“ im Emulator rot ausgelöst |
| Hintergrunddienst | fertig | `MediaSessionService`, FGS-Typ `mediaPlayback`, Medienmeldung mit Cover |
| Kopfhörer-/Medien-Tasten | fertig | `KEYCODE_MEDIA_PAUSE/PLAY/NEXT` steuern die Session korrekt |
| `setHandleAudioBecomingNoisy(true)` | fertig | im Code aktiv |
| Sinuston | fertig | `AudioTrack`, 100–15 000 Hz, 10-Hz-Slider, ±1 Hz, Langdruck ±10 Hz |
| **Preset-Sync echt** | fertig | Phone → Watch-DataItem + ACK; Watch meldete „Presets von Pixel 10 übernommen: [Preset 1, Preset 2, Preset 3]“ |
| **Musik-Sync echt (Spiegel)** | fertig | Manifest → fehlende Assets → Commit; Phone meldet „Musik gespiegelt: 2 Titel.“ |
| **Spiegel-Löschung** | fertig | Nicht ausgewählter Titel wurde nach Commit auf der Uhr gelöscht; alter Bestand überlebt Abbruch |
| Sync-Zeitstempel | fertig | nach Neustart wiederhergestellt; letzter Lauf 13.11.2026, 23:13 |
| Persistenz | fertig | DataStore, ein JSON-Eintrag, Formatversion, konflatierte Schreiber |
| Wiederaufnahme | fertig | Ordner + zuletzt gespielter Titel überleben den App-Neustart; Emulator-Abnahme mit Album B und „03 - Ton 880“ im Unterordner „CD 2“ |
| EQ-Statistik | fertig, reale Uhr-Abnahme offen | vierter Tab `STATS`; Gesamt/Ø-Tag/Heute, Woche-Monat-Jahr-Chart mit Pfeilen und sichtbarer Skala (Max, Halb, 0), Kurzzeiten unter einer Minute in Sekunden; `days.json` + Journal, Verdichtung beim Öffnen und beim Sync; Sync-Pfad `/stats/request` → `/stats/report` → `/stats/ack`, Uhr löscht erst nach APPLIED-ACK. Emulator-Abnahme der Phone-Seite und der Watch-Aufzeichnung; reale Übergabe an das Phone steht aus |
| Fehlerbilder | fertig | Scan-/SAF-Fehler mit verständlichem Text und „Erneut versuchen“; Sync-Fehler pro ACK-Grund |
| Dark Theme / Bedienbarkeit | fertig | deutsche Texte, große Ziele, Content-Descriptions, keine reine Farbcodierung |

### Bewusst so entschieden

- **Phone ist Master und Editor.** Drei Presets werden nur hier bearbeitet; die Uhr zeigt Auswahl.
- **Musik-Sync ist ein Spiegel.** Nicht ausgewählte oder verschwundene Titel werden auf der Uhr
  erst nach vollständigem Upload und bestätigtem Commit gelöscht.
- **Gleiche `applicationId`.** Watch-APK heißt ebenfalls `de.notchedmusic.phone`; der
  Watch-Namespace bleibt `de.notchedmusic.watch`. Data Layer findet Pakete sonst nicht.
- **Keine breite Speicherberechtigung.** Nur SAF.
- **Kein eigener Bluetooth-Stack.** A2DP/Codec/Latenz liegen beim System; die Data Layer wählt
  selbst Bluetooth oder WLAN.
- **Online-Cover nur mit Künstler/Album-Tags.** Ein reiner Dateiname liefert keine sinnvollen
  Treffer; es werden nur Titel/Künstler/Album gesendet.

---

## 4. Sync-Implementierung (Phone)

| Datei | Aufgabe |
|---|---|
| `sync/WearSyncTransport.kt` | Capability, Presets, Manifest, Datei-Retry, Commit, Cancel; Rückfall-Fristen aus `SyncFristen`, Lebenszeichen alle 2 min |
| `sync/WearGateway.kt` | `PlayServicesWearGateway`: `findNode`, urgent DataItems, FD-Assets mit Play-Services-Digest, Delete, Message |
| `sync/SyncAckBus.kt` | korreliert `CompletableDeferred` nach `(syncId, kind, index)` |
| `sync/AssetSource.kt` | SHA-256, MIME, Größe aus dem SAF-Dokument |
| `sync/SyncRepository.kt` | Auswahl, Fortschritt, Abbruch, UI-Zustand, `lastSyncMs` |
| `sync/WearSyncListenerService.kt` | Watch-Request `/filter/request`, ACKs, Registrierung der Phone-Capability; verarbeitet zusätzlich `/stats/report` |
| `sync/StatsReportBus.kt` | übergibt den Statistik-Report der Uhr an den wartenden Sync-Job |
| `stats/StatsStore.kt` | Tagesdatei `days.json` + Journal `journal.jsonl`; Verdichtung, idempotenter Watch-Merge |
| `stats/EqStatsTracker.kt` | misst EQ-Hörzeit nur bei laufender Wiedergabe mit aktivem EQ |
| `stats/StatsChart.kt` | Woche (Summe je Tag), Monat (Summe je ISO-KW) und Jahr (Summe je Monat) |
| `stats/StatsRepository.kt` | UI-Zustand, Refresh und Merge der Uhr-Statistik |
| `AppGraph.kt` | verdrahtet Repository, Transport, Tracker und Service |

### Statistik-Sync (Phone ↔ Uhr)

* Beim Öffnen der Statistik-Seite bzw. vor dem Merge wird das aktive Segment geschlossen und das
  Journal in `days.json` verdichtet; ein Absturz davor zählt durch `journalCompactedThroughMs`
  nicht doppelt.
* Die Phone-Seite schickt `/stats/request`. Die Uhr antwortet mit einem DataItem auf
  `/stats/report` und einem stabilen `reportId`; das Phone merged nur Tage, die es noch nicht kennt.
* Nach dem erfolgreichen Schreiben von `days.json` bestätigt das Phone mit `/stats/ack`. Nur bei
  `STATUS_APPLIED` löscht die Uhr ihre bereits übernommene Momentaufnahme; schlägt das Schreiben
  fehl oder bleibt die Bestätigung aus, behält die Uhr die Daten für einen erneuten Versuch.

Feste Grenzen: Manifest ≤ 1 MiB, Datei ≤ 40 MB, JSON ≤ 8 KiB, Musik nur unterstützte
Audio-Endungen; gelöscht wird nur innerhalb des App-Musikordners der Uhr.

### Bewährte Stolperstein-Lösungen

* DataItems mit **`setUrgent()`** schreiben; sonst wartet die Data Layer auf ein Idle-/Ladefenster
  und das sieht wie ein Timeout aus.
* Assets über den **Play-Services-Digest** (`audio_digest`, Base64-SHA-1) adressieren; der
  hexadezimale SHA-256 bleibt der fachliche Integritätscheck.
* Die Asset-Metadaten erst nach dem tatsächlichen Transfer lesen; ein sofortiger
  `DataMapItem.fromDataItem` kann mit `ASSET_UNAVAILABLE` scheitern.
* Watch-Empfänger synchron im Callback und im app-weiten Scope halten; GMS löst die Bindung kurz
  nach dem Event.
* `ApiException: 4006 DUPLICATE_CAPABILITY` ist harmlos (App + Listener registrieren dieselbe
  Capability).
* **Kein Größen-Timeout, keine Display-Sperre:** `SyncFristen` kennt nur **Rückfall-Fristen für
  Stille** (Presets 60 s, Manifest und Datei 30 min, Commit 10 min). Eine Datei mit 40 MB darf über
  die Bluetooth-Brücke fünf bis sechs Minuten brauchen, ohne abgebrochen zu werden. Während einer
  Spiegelung sendet das Phone alle 2 Minuten `/music/keepalive`; danach (Erfolg, Fehler, Abbruch) ist
  der Job weg – `WearSyncTransportTest` prüft, dass währenddessen Lebenszeichen gehen und hinterher
  keine mehr. Weder Phone noch Uhr halten für den Transfer ein Wake-Lock oder `keepScreenOn`.
* **Real gemessen am Limit (14.11.2026, 23:26):** ein bis dahin nie gesendeter Titel mit
  **39,96 MiB** brauchte **≈ 4,7 min** auf die Uhr (~145 kB/s). Uhr-App 13 s nach dem Start
  geschlossen, Pixel `Dozing`, Uhr `Asleep`; der Uhr-Lauscher blieb die ganzen fünf Minuten offen und
  ging 90 s nach dem Commit wieder zu, keine `.part`-Reste. Ein Titel an der Grenze liegt damit über
  der alten 300-s-Frist – ohne Größen-Timeout läuft er durch.
* **Durchsatz nur mit neuen Inhalten messbar:** vier am selben Tag bereits gesendete Dateien
  (34 MB) waren in **79 s** wieder auf der Uhr – die Data Layer legt Asset-Inhalte inhalts­adressiert
  ab, wiederholte Sendungen sind also nahezu gratis.
* **Sync nur bei erreichbarer Uhr starten:** „SYNC EQs“ und „SYNC MUSIC“ sind deaktiviert, solange
  `sync.watchAvailable != true`. Beides offen beim Start ist also erzwungen; **offen bleiben** muss
  nur das Phone, die Uhr-App darf nach dem Start wieder zu sein.
* **Die Uhr empfängt nur, wenn ihre App offen ist** (uhrseitiger `SyncEmpfang`-Schalter seit
  14.11.2026). Wenn „Keine Uhr gefunden – App auf der Uhr öffnen.“ erscheint, ist die Uhr-App zu –
  kein Kopplungsfehler. Ein laufender Sync überlebt das Schließen der Uhr-App.
* **Der Data-Layer-Lauscher der Phone-App ist bewusst NICHT an Sichtbarkeit gebunden:** `WearSyncListenerService`
  braucht die ACKs der Uhr auch dann, wenn das Phone gesperrt ist – ein langer Spiegel läuft
  sonst ab. Stromsparend wirkt hier stattdessen `AppViewModel.clipUeberwachung(an)`: die 10-Hz-
  Abfrage der Pegel-Spitze läuft nur noch, solange die Player-Seite sichtbar ist
  (`LifecycleStartEffect` in `PlayerScreen`) – bei anderem Tab, Bildschirm aus oder im Hintergrund
  fragt der ViewModel nicht mehr.
* **Durchsatz der Data Layer über die Bluetooth-Brücke:** real gemessen **≈ 2 Titel/Minute**
  (≈ 0,12 MB/s). Vollspiegel `punk_rock` mit 101 Titeln / 479 MB am 14.11.2026 von 21:04 bis
  21:31 Uhr: Uhr danach 101 Dateien, Album zeigt „101 Titel“, Einstellungen „Musik: 14.11. 21:31“;
  die Uhr-App war ab 21:04 geschlossen, der Sync lief trotzdem durch.

---

## 5. Messungen

| Messung | Ergebnis | Bewertung |
|---|---|---|
| DSP, 6 Bänder stereo @48 kHz, warm | **11–14 ns/Sample** (letzter Einzellauf: 11,463; schwankt unter Host-Last) | Ziel < 15 ns erreicht* |
| Bibliothek, 2 005 Dateien | **1 389 ms** | Ziel < 1 500 ms |
| Bibliothek, 6 Dateien | **103 ms** | – |
| App-Speicher nach Dauerlauf | ~104 MB PSS | unauffällig |
| Underruns / Crashes im 13-Minuten-Lauf | **0 / 0** | Ziel 0 Underruns |
| StrictMode im Debug-Build | keine Verstöße | kein Main-Thread-I/O festgestellt |

\* Ein kalter erster JVM-Lauf lag bei ca. 26 ns; der Test wärmt deshalb 20 Blöcke vor und bewertet
den eingeschwungenen Zustand. Unter Last schwanken einzelne Läufe.

---

## 6. Abnahme auf echten Geräten (13.11.2026)

| Prüfung | Ergebnis | Beleg |
|---|---|---|
| Phone auf Pixel 10 installiert/gestartet | ohne Absturz, Sync-Seite bedienbar | 22:18–22:46 |
| Watch als `de.notchedmusic.phone` installiert | App läuft, Activity `de.notchedmusic.watch.MainActivity` | mehrfach |
| Capability-Suche | Phone findet Watch, Watch findet Phone | GMS-/WearSync-Logs |
| Preset-Sync | drei Presets angenommen und bestätigt; Watch-UI zeigt P1/P2/P3 | 22:44 |
| Musik-Sync 2 Titel | 264 695 B + 141 289 B übertragen, SHA-256 geprüft, Commit bestätigt | 22:32 / 22:46 |
| Musik-Sync finaler Build | Manifest mit 2 Titeln, 0 fehlend, Commit bestätigt, Phone „Musik gespiegelt: 2 Titel.“ | 23:13 |
| Musik-Spiegel-Löschung | 1 nicht ausgewählter Titel nach Commit gelöscht | 22:38 |
| Abbruch | 101-Titel-Lauf nach Manifest abgebrochen; Watch meldet „0 halbe Dateien“, alter Bestand bleibt | 23:07 |
| Neustart | Bibliothek neu gescannt, Sync-Auswahl/Zustand nach App-Neustart bedienbar | 22:37 / 22:46 |
| App-Neustart Watch | Dateien und Presets bleiben erhalten | mehrfach |
| Lint/Build | 0 Fehler, BUILD SUCCESSFUL | 22:47 |

Der Emulator bleibt weiterhin die Referenz für aufwendige Wiedergabe-/Cover-/Dauerlauf-Tests.
Auf dem echten Phone wurde in diesem Lauf insbesondere der Sync validiert, nicht die komplette
A2DP-Kette.

---

## 7. Emulator-Abnahme (Pixel 10, API 37)

| Prüfung | Ergebnis | Zeit/Beleg |
|---|---|---|
| App-Start nach Installation | ohne Absturz, bedienbar | mehrfach |
| SAF-Picker + persistierbare Freigabe | funktioniert, Root bleibt nach Neustart | 21:02 / 21:26 |
| Scan | 6 Titel, 3 Knoten; verschachtelter Ordner kein eigener Knoten | 21:02 |
| Wiedergabe | AudioTrack/ExoPlayer startet, Titel wechselt, Repeat greift | mehrfach |
| Hintergrund | 13 Minuten mit `isForeground=true`, `foregroundId=1001`, Session PLAYING | 21:17:43–21:30:43 |
| Medienmeldung | Titel, Künstler, Cover, Play/Pause, Vor/Zurück, Seek | [notification-cover.png](docs/screenshots/notification-cover.png) |
| Medien-Tasten | Pause/Play/Next ändern den Session-Status | 19:30 |
| App-Neustart | Root, Presets, Lautstärken, Sync-Zeitstempel erhalten | 21:26 |
| Player-Wiederaufnahme | nach `force-stop`/Neustart wieder „Album B · 01 - Ton 330“ bzw. „Album A/CD 2 · 03 - Ton 880“ ausgewählt | 19.11.2026, Emulator |
| Statistik-Seite | vier Tabs PLAYER/EQ/SETUP/STATS; Gesamt/Ø-Tag/Heute, Umschalter Woche/Monat/Jahr, Pfeile; Bars für Tag, ISO-KW und Monat korrekt gruppiert | 20.11.2026, Emulator |
| Statistik-Persistenz | Wiedergabe mit aktivem Filter startet Aufzeichnung; nach Pause + `force-stop`/Neustart weiterhin „6 min“ im Statistik-Tab | 20.11.2026, Emulator |
| Watch-Aufzeichnung | Uhr-Journal (`watch_stats_journal.jsonl`) enthält ein geschlossenes 5-Segment nach App-Start/Wiedergabe; reale Übergabe an das Phone wurde mit 51,645 s und APPLIED-ACK abgenommen | 20.11.2026, Watch-Emulator und echte Geräte |
| Anruf/Sperre (Bugfix) | Wiedergabe pausiert bei eingehendem Anruf, läuft nach Auflegen weiter und bleibt bei ausgeschaltetem Display im Vordergrund; Anruf/Auflegen auf dem Emulator, Gerätesperre real auf dem Pixel 10 (AudioTrack nach 32 s weiter `started`, `isForeground=true`, keine neuen AudioHardening-Meldungen) | 24.11.2026, Emulator und Release-APK auf Pixel 10 |
| EQ-Defaults | Preset 1: Notch 8000 Hz, Q 8,00, −20,0 dB | [eq-preset1.png](docs/screenshots/eq-preset1.png) |
| Clip-Anzeige | Bell 220 Hz +12 dB und Preamp +12 dB → rote „Clip“-Anzeige | 19:39 |
| Sinuston | 440→441 Hz über „+“, Start/Stop | früher |
| Sync real | Status „Musik gespiegelt“, Zeitstempel, Watch-Dateien geprüft | 22:32–22:46 |
| Cover online | getaggte MP3 (Pink Floyd/Time) → Cover geladen und gecacht | [player-time.png](docs/screenshots/player-time.png) |
| Cover offline | Flugmodus + gelöschter Cache → Platzhalter, keine Datei im Cache, Wiedergabe läuft | 21:20 |
| Cover eingebettet | APIC-Cover in `Embedded.mp3`; im Flugmodus ohne Online-Zugriff angezeigt | [embedded-cover-offline.png](docs/screenshots/embedded-cover-offline.png) |
| Lautstärke-Slider | Spieler- und Testton-Slider nach Fix korrekt (50 % = −30 dB; 66 %) | 22:40 |
| Rechte-Entzug | Code-Pfad vorhanden, nicht end-to-end simuliert | offen |

Weitere Screenshots: [docs/screenshots](docs/screenshots).

---

## 8. Offene Punkte / Risiken

1. **Nicht alle E2E-Fälle real geprüft:** 10-Dateien-Lauf, Abort mitten im Upload (Teil-`.part`),
   Speicherknappheit, ungültiges Preset. Der Code und die ACK-Pfade sind vorhanden.
   Erledigt am 14.11.2026: neuer Titel an der 40-MB-Grenze (41 900 000 Bytes ≈ 4,7 min auf die Uhr,
   Lauscher blieb offen, keine `.part`-Reste) und die Spiegel-Löschung genau dieses Titels.
2. **Komplette A2DP-/Kopfhörer-Abnahme auf dem Pixel 10 steht aus.** Bisher Emulator-Audio plus
   Sync auf dem echten Gerät.
3. **Kein End-to-End-Test für entzogene SAF-Rechte.** Fehlerpfad implementiert, nicht per
   Systemeingriff simuliert.
4. **Release-APK ist fertig, Store nicht nötig.** Build und Installation laufen; signiert wird
   lokal mit dem Debug-Schlüssel. Proguard/R8 ist bewusst aus, eine Store-Vorbereitung steht nicht an.
5. **Instrumentierte Tests fehlen.** Die 83 Tests laufen auf der JVM; die Geräteabnahme erfolgte
   manuell über adb/uiautomator.
6. **Online-Cover** sind optional und abhängig von Tags und Netzwerk. Die App bleibt ohne
   Netzwerk vollständig nutzbar.
7. **Watch-UI-Tests:** Compose-/Instrumentationstests für die Uhr sind noch offen; die
   Geräteabnahme dort erfolgte manuell.

---

## 9. Artefakte

| Artefakt | Pfad |
|---|---|
| App-APK (Release, installiert) | `app/build/outputs/apk/release/app-release-signed.apk` |
| App-APK (Debug, Entwicklung) | `app/build/outputs/apk/debug/app-debug.apk` |
| Testmusik (`Embedded.mp3`, `Online.mp3`) | `testdata/` |
| Screenshots | `docs/screenshots/` |
| Kurzanleitung | `README.md` |
| Plan | `PLAN.md` |
| Gegenstück | `Z:\android_projekte\NotchedMusic\NotchedMusic-Watch` |

Testmusik lag während der Abnahme auf dem Pixel 10 unter `/sdcard/Music/SyncTest` (2 Titel, 396 KB)
und wurde auf die Uhr gespiegelt. Nach Abschluss wurden die Testdateien von Phone und Uhr entfernt.

---

## 10. Änderungshistorie (Auszug)

- **17:44–18:00** Scaffold, Versionen aus dem lokalen Gradle-Cache, `:core`-Modell/DSP/Protokoll.
- **18:00–19:00** `:app`-Datenlayer, Scanner, Playback, Media3, UI, Sync-Mock.
- **19:00–19:45** Emulator-Setup, SAF-Test, Testmusik, Player-/EQ-/Sync-Abnahme.
- **19:45–20:30** Bugs: Manifest-Application-Klasse, Clip-Schwellen, Test-Assertions,
  Formate, Cover-Cache; Vordergrunddienst mit eigener Medienmeldung repariert.
- **20:30–21:00** Hintergrund-/Medien-Tasten-Test, Scan-Performance 2 005 Dateien.
- **21:00–21:30** 13-Minuten-Dauerlauf, Online-/Offline-Cover-Test.
- **21:30–22:30** Medienmeldung mit Cover und angereicherten Metadaten, Slider-Bug behoben,
  Sync-Zeitstempel beim Start geladen, Ressourcen/Lint bereinigt.
- **13.11., 16:00–22:50** Phone/Watch vereinheitlicht: SyncProtocol v3, Watch-Datenmodell,
  Data-Layer-Transport, Manifest + Assets + Commit, reale Geräteabnahme, `applicationId`-Angleichung
  auf `de.notchedmusic.phone`, Dokumentation aktualisiert.
- **19.11.** Player-Wiederaufnahme: `lastFolderKey` und `lastTrackDocumentId` in `AppSettings`
  (Formatversion 2), Restore nach dem SAF-Scan, Auswahl wird bei jedem Titel-/Ordnerwechsel
  festgehalten; auf dem API-37-Emulator und real auf dem Pixel 10 per `force-stop`/Neustart
  geprüft (2 / 101 · „You Can't Stop Me“ nach Neustart wieder ausgewählt).
- **20.11.** EQ-Statistik: vierter Tab, Journal-/Tage-Persistenz, Chart für Woche/Monat/Jahr
  mit Y-Skala (Max/Halb/0) und Sekundenanzeige bei Kurzzeiten, `StatsProtocol` v1, synchroner
  Uhren-Merge mit stabiler Report-ID und Löschung erst nach APPLIED-ACK; Watch zählt lokal mit
  aktivem Preset. Phone-Tests 102/102, Watch-Tests 150/150, Lint und Release-Signierung grün.
  Reale Übergabe auf Pixel 10 ↔ TicWatch Pro 5: Uhr-Report mit 51,645 s wurde übernommen,
  `lastWatchReportId` gesetzt, Uhr-Datei danach geleert; die echten Geräte laufen jetzt wieder
  auf den Release-APKs.
- **24.11.** Bugfix nach Anruf/Sperre: eigener Vordergrunddienst wurde durch Media3-
  `MediaSessionService` ersetzt und die eigene Medien-Meldung entfernt. `addSession` registriert
  die Session für die automatische Meldungs-/Vordergrundverwaltung. Emulator: Anruf annehmen,
  auflegen, Display aus; Wiedergabe bleibt nach 15 s im Vordergrund und läuft weiter. Release-APK
  auf dem realen Pixel 10: nach dem Sperren lief der AudioTrack nach 32 s weiter, der Dienst
  blieb mit `isForeground=true` und Medienmeldung aktiv; keine neuen AudioHardening-Meldungen.

---

## 11. Nächste sinnvolle Schritte

1. Die offenen E2E-Fälle auf realen Geräten abarbeiten (großer Upload, Abort, Speicher, ungültiges
   Preset).
2. Kompletten A2DP-/Kopfhörer-Test auf dem Pixel 10 durchführen.
3. Compose-Instrumentierungstests für Sync-Seite, Player und EQ-Felder ergänzen.
4. Optional: R8/Proguard und ein eigenes Release-Key-Store; für die persönliche Installation nicht nötig.
5. Optional: Anruf-/Auflegen-Fall auf realen Geräten nachholen; der Emulator und die reale
   Sperre auf dem Pixel 10 sind bereits abgenommen.
6. Optional: Preset-Export/Import, Cover-Disk-LRU mit Größenlimit.
