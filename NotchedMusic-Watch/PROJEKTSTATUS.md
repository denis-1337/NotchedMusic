# Projektstatus – NotchedMusic

> **Diese Datei ist der Einstiegspunkt für jede neue Sitzung.** Zuletzt aktualisiert: 20.11.2026,
> nach Statistik-Aufzeichnung auf der Uhr, Statistik-Sync-Protokoll und Phone-Merge. Regeln: diese
> Datei bei jedem abgeschlossenen Schritt aktualisieren, `plan.md` nur nach Rücksprache ändern.

## Kurzfassung für den Einstieg

Wear-OS-Musikplayer mit Echtzeit-EQ (Notch-Filter) für Tinnitus-Retraining. Zielgerät ist die
**Mobvoi TicWatch Pro 5** (Wear OS 4 / **API 33**, 466 × 466 rund, OLED). Die Uhr spielt autark;
die Phone-App (`de.notchedmusic.phone`) ist der Editor für **drei Presets** und der Master für den
**Musik-Spiegel**.

* **Fertig und auf beiden echten Geräten geprüft:** gemeinsames `EqPreset`-Modell, sieben
  Filtertypen, drei Presets, Migration vom alten A/B-Stand, echter Sync über die Wear Data Layer
  (Presets und Musik mit SHA-256 + Commit), TicWatch-UI ohne Preset-Editor.
* **EQ-Statistik:** Die Uhr zählt lokal nur laufende Musik mit aktivem Preset (`activePresetId != null`),
  schreibt geschlossene Segmente ins Journal und übergibt beim Sync eine stabile Momentaufnahme.
  Gelöscht wird erst nach `STATUS_APPLIED`-ACK des Phones; ohne ACK bleiben die Daten für einen
  erneuten Versuch erhalten.
* **Build/Prüfung:** `assembleDebug` und `assembleRelease` grün, **150 / 150 JVM-Tests grün**,
  Lint 0 Fehler. Das Release-APK ist mit lokalem Schlüssel signiert und auf der Uhr installiert.
* **Stromsparen:** Die beiden Data-Layer-Empfänger stehen im Manifest auf `enabled="false"` und
  werden von `SyncEmpfang` nur geöffnet, wenn die App offen ist oder Daten eintreffen (90 s
  Nachlauf; während eines Musik-Spiegels eine **Rückfall-Frist von 30 Minuten**, die das Phone alle
  2 Minuten mit einem Lebenszeichen `/music/keepalive` neu stellt – kein Wake-Lock, kein
  Bildschirm-Halten). Der Anzeigetakt folgt dem Bildschirm:
  4 Hz hell, 5 s gedimmt, gar nicht wenn die App unsichtbar ist. Der Anzeige-Knopf hat drei Stufen:
  schmal / voll / **aus** (bei „aus" übernimmt im Gedimmt-Zustand das Zifferblatt).
* **Offen:** instrumentierte Compose-UI-Tests, große Datei am Limit, Abort-/Speicherknappheits-Test,
  30-Minuten-A2DP-/OLED-Messung und App-Icon. Release-Signing für die persönliche Installation ist erledigt.
* **Wiederaufnahme:** `lastTrackPath` in `WatchSettings` (Formatversion 3); nach einem
  App-Neustart wählt die Uhr das gespeicherte Album samt Titel im Spieler vor, ohne selbst
  loszuspielen. Der Pfad wird bei jedem Titelwechsel (auch Auto-Advance) festgehalten.
* **EQ-Statistik:** `WatchStatsStore` mit Journal + `watch_stats.json`, lokaler Tracker im
  `PlaybackService`; Sync nur bei erreichbarer Uhr und offenem Empfänger, danach ACK-gesteuertes
  Löschen der übernommenen Momentaufnahme.
* **Nächster Schritt:** die offenen Gerätetests und Screenshots der Sync-Seiten dokumentieren.

## Frame für neue Sitzungen

| | |
|---|---|
| Sprache / Stil | Kotlin, 2 Leerzeichen Einrückung, KDoc-Kommentare **auf Deutsch**, keine TODO-Reste |
| Namespace | `de.notchedmusic.watch` (Quelle: `app/src/main/kotlin/de/notchedmusic/watch/`) |
| **applicationId** | `de.notchedmusic.phone` – bewusst identisch mit der Phone-App (siehe Sync-Hinweis unten) |
| Start | `adb shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity` |
| Projektordner | `Z:\android_projekte\NotchedMusic\NotchedMusic-Watch` |
| Modul | nur `:app`, ein Wear-OS-App-Modul |
| Build prüfen | `./gradlew.bat app:assembleDebug app:assembleRelease app:testDebugUnitTest app:lintDebug --console=plain` |
| Release-APK | `app/build/outputs/apk/release/app-release-signed.apk` (≈ 32 MB, lokal mit Debug-Schlüssel signiert, nicht debuggable) |
| Letzter Stand | BUILD SUCCESSFUL, **150/150 Tests**, Benchmark **11–14 ns/Sample**, schlechtester Puffer **< 0,1 ms** (letzter Lauf 11,054 ns / 0,097 ms; Grenze 15 ns / 1 ms) |
| Reale Uhr | TicWatch Pro 5, Android 13 / API 33, 466 × 466, `adb-C102X37220190-fKNFqi._adb-tls-connect._tcp` |
| Reales Phone | Pixel 10, Android 17 / API 37, `adb-59061FDCR000HC-DasVPx._adb-tls-connect._tcp` |
| Bildschirm-Timeout | **nicht erneut setzen:** `screen_off_timeout` war in den Tests auf 2 147 483 647 und ist am 14.11.2026 auf Normalwert zurückgedreht worden (Uhr 15 000 ms, Pixel 60 000 ms; `stay_on_while_plugged_in = 0`). Mit dem Maximalwert geht das OLED nie aus und Doze startet nie. |
| Musik-Ordner der Uhr | `/sdcard/Android/data/de.notchedmusic.phone/files/Music/` (applicationId!) |
| Nachschlagewerk | Wear-OS-Grundlagen, Messmethoden, adb-Anleitung: `wearos_leitfaden.artifact.md` im Artifacts-Ordner dieser Sitzung |

### Versionen (bewusst gewählt – nicht ohne Grund ändern)

| | Wert | Warum |
|---|---|---|
| AGP | 9.5.1 | Wear Compose M3 braucht AGP ≥ 9.1.0 **und** compileSdk ≥ 37 |
| Gradle-Wrapper | 9.6.0 | AGP 9.4+ verlangt Gradle ≥ 9.6.0; `distributionSha256Sum` mitziehen! |
| compileSdk / targetSdk | 37 / 37 | API 37 ist das Maximum für diese AGP-Linie |
| **minSdk** | **33** | reale TicWatch meldet API 33 |
| Kotlin | 2.3.20 | Compose-Compiler läuft über dasselbe `version.ref` |
| Media3 | 1.11.1 | AudioProcessor-API ist `@UnstableApi` → `@OptIn(UnstableApi::class)` |
| Wear Compose M3 / Foundation | 1.7.1 | `ScreenScaffold`, `TransformingLazyColumn`, `Modifier.rotaryScrollable` |
| Navigation 3 | 1.0.1 | samt `wear.compose.navigation3` (Swipe-to-Dismiss) |
| Play Services Wearable | 20.0.1 | Data Layer, CapabilityClient, MessageClient |

## Was gebaut ist

### Modell und Protokoll

| Datei | Inhalt |
|---|---|
| `model/FilterBand.kt` | `FilterType` mit **sieben** Typen: `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`, `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT`; Band mit Frequenz/Q/Gain/`enabled` |
| `model/EqPreset.kt` | `@Serializable` Preset-Format, sechs Bänder, Validierung, Vor-Verstärkung |
| `model/DefaultPresets.kt` | P1/P2/P3 mit stabilen IDs; P3 als Reserve |
| `model/WatchSettings.kt` | Format version 2: drei Presets, aktive Auswahl, `volumeDb`, Anzeige-Schalter, Sync-Zeitstempel; Migration von A/B (legacy) |
| `protocol/SyncProtocol.kt` | v3-Payload, ACK/Cancel/Manifest-Encode/-Decode, Größen- und Indexprüfung |
| `protocol/StatsProtocol.kt` | v1-Report/ACK für EQ-Hörzeit, Tagesgrenzen und stabile `reportId` |
| `stats/WatchEqStatsTracker.kt` | misst nur laufende Musik mit aktivem Preset; schließt Segmente bei Pause/Titelwechsel |
| `stats/WatchStatsStore.kt` | Journal + `watch_stats.json`, Verdichtung, idempotente Report-Snapshot- und ACK-Behandlung |
| `stats/WatchStatsRepository.kt` | Uhr-UI-Zustand und Anbindung an den Data-Layer-Sync |
| `data/StatisticsTransferReceiver.kt` | `/stats/request` und `/stats/ack`; sendet `/stats/report` als DataItem |
| `legacy/LegacyFilterProfiles.kt` | liest den alten A/B-DataStore und bildet A→P1, B→P2, P3→Default ab |

### DSP

| Datei | Inhalt |
|---|---|
| `dsp/Biquad.kt` | RBJ-Koeffizienten für alle sieben Typen, paarweise Überblendung |
| `dsp/EffectChain.kt` | Biquads in Serie pro Kanal, Vor-Verstärkung, Lautstärke, fester Arbeitspuffer |
| `dsp/Limiter.kt` | Decke −1 dBFS, 1 ms Attack / 50 ms Release |
| `dsp/Pcm.kt` | 16 Bit Little Endian ↔ FloatArray, rundend |
| `dsp/EffectAudioProcessor.kt` | Media3-`BaseAudioProcessor`, Koeffizienten aus der echten Sample-Rate |
| `dsp/EffectAudioProcessorChain.kt` | Kette für Tempo/Pitch unverändert, Silence-Skipping aus |

### Daten, Sync und Hintergrund

| Datei | Inhalt |
|---|---|
| `data/MusicLibrary.kt` | rekursiver Scan; ein direkter Unterordner = ein Album, tiefere Unterordner zählen dazu |
| `data/MusicFolders.kt` | privater App-Musikordner |
| `data/MusicMirror.kt` | Manifest-Prüfung, Zielpfade, Löschplan, `.part`-Sicherung |
| `data/MusicSyncStore.kt` | ausstehender Manifest-Stand zwischen Manifest und Commit |
| `data/MusicTransferReceiver.kt` | `WearableListenerService`: Manifest, Upload, Commit, Cancel; ACK per Message |
| `data/MusicLibraryRefresh.kt` | Signal zum Neu-Einlesen der Bibliothek nach Commit |
| `data/WearDataCleanup.kt` | räumt verwaiste DataItems auf |
| `playback/FilterSettings.kt` | eine Quelle der Wahrheit: RAM-`StateFlow` + DataStore, Migration |
| `playback/PhoneFilterReceiver.kt` | nimmt `/filter/profiles`-DataItems an, validiert und bestätigt per `/filter/ack` |
| `playback/PhoneLink.kt` | fragt die Phone-Capability `de.notchedmusic.phone.sync` ab und sendet `/filter/request` |
| `WatchApplication.kt` | registriert die Watch-Capability `de.notchedmusic.watch.filter_profiles`; stellt einen app-weiten `CoroutineScope` bereit |
| `MainActivity.kt` | Navigation 3: Alben · Titel · Spieler · EQ · Einstellungen |

### Watch-UI

* **Alben** und **Titel** ohne Cover; „Neu laden“; laufender Titel markiert.
* **Spieler (466 × 466 abgenommen):** eine feste, kompakte Seite – Fortschrittsbogen mit Zeit,
  Titel, Preset-Modus, drei kompakte Transportknöpfe und ein schmaler Lautstärkebalken. Der
  Balken läuft links von stumm nach rechts bis voll; die Anzeige nennt den Pegel in Prozent.
  Alle Elemente passen ohne Scrollen; Zurück läuft über die Wear-Geste/das System.
* **EQ**: nur **Aus · P1 · P2 · P3**; keine Band-Editoren, kein Zahnrad – Bearbeitung ausschließlich
  am Phone.
* **Einstellungen**: Phone-Status als drei Zeilen, „Profile anfordern“, Speicherbelegung (kein
  technischer Pfad mehr), „Neu laden“;
  verständliche Zustände auch ohne Phone.
* OLED-freundliches Nachtschema, keine dauerhaften Animationen.

## Verifizierte Fakten (nicht erneut behaupten, nachmessen falls geändert)

* **Build + Tests:** `app:assembleDebug` grün, `app:testDebugUnitTest` **139/139 grün**,
  `app:lintDebug` **0 Fehler**.
* **Testverteilung:** `MusicLibraryTest` 15, `EmpfangsfensterTest` 13, `EqPresetTest` 12,
  `EffectChainTest` 11, `FilterSettingsStoreTest` 11, `BiquadTest` 10, `VolumeStepsTest` 9,
  `MusicMirrorTest` 9, `LimiterTest` 7, `SyncProtocolTest` 6, `AmbientAnzeigeTest` 6,
  `AlbumJumpTest` 6, `BandStackingTest` 5, `VolumeScaleTest` 4, `TaktPlanTest` 4,
  `WatchSettingsModeTest` 3, `CoefficientSmoothingTest` 3, `WatchStatsProtocolTest` 3,
  `NoiseSpectrumTest` 2, `RingingTest` 2, `WatchStatsStoreTest` 4, `EffectChainBenchmarkTest` 1.
  `NoiseWavTest`
  entfällt mit den gestrichenen Debug-Rauschwerkzeugen.
* **DSP-Benchmark:** **11–14 ns/Sample**, schlechtester Puffer **< 0,1 ms** (6 Bänder, 48 kHz,
  Stereo, 3 Läufe; letzter Lauf 11,054 ns / 0,097 ms) → unter den Zusagen (15 ns / 1 ms).
  Der Benchmark ist host-lastabhängig; bei parallel laufenden LLM-/Gradle-Prozessen kann er
  ausreißen und wird dann einzeln erneut gemessen.
* **Preset-Sync real (13.11.2026, 22:44):** Watch „Profile anfordern“ → Phone sendet
  `/filter/profiles/PN-…` (urgent) → Watch `PhoneFilterReceiver` meldet
  „Presets von Pixel 10 übernommen: [Preset 1, Preset 2, Preset 3]“ → Phone erhält `/filter/ack`.
* **Musik-Sync real (13.11.2026, 22:32, 22:46 und 23:13):**
  - Manifest + ACK innerhalb von Sekunden; Uploads per Asset; `Embedded.mp3` (264 695 B) und
    `Online.mp3` (141 289 B) liegen danach im Watch-Musikordner; Phone meldet
    „Musik gespiegelt: 2 Titel.“
  - Commit bestätigt; `MusicTransfer` protokolliert 0 bzw. 1 gelöschte Titel.
  - Finaler Build (23:13): Manifest mit 2 Titeln, 0 fehlend, Commit 0 gelöscht.
  - Die Testdateien wurden nach der Geräteabnahme vom Phone und von der Uhr entfernt.
* **Musik-Spiegel-Löschung real (13.11.2026, 22:38):** Nur `Embedded.mp3` ausgewählt → Manifest mit
  1 Titel, 0 fehlend → Commit löscht `Online.mp3` auf der Uhr (`1 nicht ausgewählte Titel gelöscht`).
* **Abbruch real (13.11.2026, 23:07):** 101-Titel-Auswahl gestartet, nach dem Manifest-ACK
  „Abbrechen“ → Watch erhält `/sync/cancel`, meldet „Sync abgebrochen; 0 halbe Dateien
  aufgeräumt“; die vorhandenen SyncTest-Dateien bleiben unverändert. Der Abbruch mitten im
  Datei-Upload (`.part`-Aufräumen) ist noch nicht protokolliert.
* **UI-Abschluss (14.11.2026):** Settings-Zeilen lagen anfangs nur als jeweils letztes Element
  eines `TransformingLazyColumn`-Items; jede Zeile ist jetzt ein eigenes Item. Spieler und
  Einstellungen wurden auf 466 × 466 ohne Kürzung der Inhalte geprüft; der technische Pfad und die
  Produktions-`Log.i`-Ausgaben sind entfernt.
* **API 33:** die reale TicWatch läuft Wear OS 4 / API 33 – nicht API 34; alle Gerätetests gelten
  für API 33.
* **Lint:** 0 Fehler; bewusst gesetzte `tools:ignore`-Stellen bleiben dokumentiert.
* **App läuft autark:** lokale Wiedergabe, EQ und Bibliothek funktionieren ohne Phone; der Sync ist
  optional und bricht ohne Kopplung mit verständlichem Status ab.

## Entscheidungen, die nicht mehr zur Debatte stehen

1. **Phone ist Master und Editor.** Drei Presets werden nur auf dem Phone bearbeitet; die Uhr zeigt
   Auswahl und aktiven Zustand.
2. **Musik ist ein Spiegel mit Commit.** Erst nach bestätigtem Commit löscht die Uhr nicht
   ausgewählte Titel; ein Abbruch löscht nichts.
3. **Gemeinsames Modell als Kopie.** Watch und Phone teilen die Klasse `EqPreset`/`SyncProtocol`
   als Quelltext-Kopie, abgesichert durch gemeinsame JSON-Fixtures.
4. **Keine stillen Verluste.** Ungültige Daten werden mit Fehler-ACK abgelehnt; alte Presets bleiben.
5. **Data Layer statt eigener Bluetooth-Stack.** Die Wahl Bluetooth/WLAN liegt bei Wear OS.
6. **DataItems sind `urgent`.** Normale Writes warten auf ein Idle-/Ladefenster; das sieht wie ein
   Timeout aus. `setUrgent()` ist für Sync zwingend.
7. **Assets werden über den Play-Services-Digest adressiert.** Die Uhr liest `audio_digest`
   (Base64-SHA-1) und ruft `getFdForAsset` damit auf; der fachliche Integritätscheck bleibt SHA-256.
8. **Gleiche `applicationId`.** Data Layer adressiert Pakete über Namen + Signatur; beide APKs
   heißen deshalb `de.notchedmusic.phone`, der Watch-Namespace bleibt `de.notchedmusic.watch`.
9. **Empfänger arbeiten im app-weiten Scope und synchron im Callback.** Das hält den Prozess
   während Manifest/Upload/Commit am Leben, auch wenn GMS die Bindung kurz danach löst.
10. **Empfang nur bei offener App.** `MusicTransferReceiver` und `PhoneFilterReceiver` sind im
    Manifest `enabled="false"`; `SyncEmpfang` (Zustandsautomat `Empfangsfenster`) schaltet sie ein,
    wenn eine Activity sichtbar wird, und 90 s nachdem die letzte Activity weg *und* keine Daten
    mehr eingetroffen sind wieder aus. Beim Prozessstart gilt immer „aus“. Die Fähigkeit
    `de.notchedmusic.watch.filter_profiles` wird in denselben Momenten an- bzw. abgemeldet, das
    Phone sieht die Uhr also nur dann als erreichbar, wenn sie wirklich zuhören kann.
    **Bewusste Folge:** Preset-Schub und Musik-Spiegel vom Phone brauchen die geöffnete Uhr-App;
    ein laufender Upload darf die Uhr-App trotzdem verlassen, die ankommenden Dateien halten den
    Empfang offen. „Profile anfordern“ und der Status auf der Uhr funktionieren unverändert.
11. **Krone** bleibt auf Listen Scrollen und auf der Spielseite Lautstärke; Drücken ist
    Systemverhalten.

## Sync-Protokoll (v3)

| Richtung | Pfad | Zweck |
|---|---|---|
| Phone → Watch | DataItem `/filter/profiles/<id>` | drei Presets + Metadaten |
| Watch → Phone | Message `/filter/ack` | Empfang/Fehler bestätigen |
| Watch → Phone | Message `/filter/request` | aktuelle Presets anfordern |
| Phone → Watch | DataItem `/music/manifest/<id>` | Soll-Liste mit Größe + SHA-256 |
| Watch → Phone | Message `/music/manifest/ack` | fehlende/presente Indizes |
| Phone → Watch | DataItem `/music/upload/<id>` + Asset | eine Audiodatei |
| Phone → Watch | Message `/music/keepalive` | hält den Lauscher während einer Spiegelung offen |
| Watch → Phone | Message `/music/ack` | Datei-ACK/Fehler |
| Phone → Watch | Message `/music/commit` | Spiegel freigeben |
| Watch → Phone | Message `/music/commit/ack` | Löschung bestätigen |
| Phone ↔ Watch | Message `/sync/cancel` | Lauf abbrechen |

### Empfang nur, wenn die App offen ist (14.11.2026)

Beide Lauscher sind im Manifest deaktiviert (`android:enabled="false"`), damit keine Data-Layer-
Sendung den Uhr-Prozess weckt, wenn niemand die App benutzt. `SyncEmpfang` meldet zusätzlich die
Fähigkeit der Uhr an bzw. ab.

Geräteprüfung (TicWatch Pro 5, Release-APK, 14.11.2026 20:56–21:11):

| Situation | `cmd package query-services` auf `wear://…/music/upload` |
|---|---|
| App geschlossen (nach Installation und nach Neustart der App) | kein Treffer – Empfänger zu |
| App offen (Library/Einstellungen) | Treffer – beide Empfänger an |
| App geschlossen, aber Sync läuft | bleibt an, weil jeder Eingang die Frist nach hinten schiebt |
| 100 s nach `HOME` ohne Daten | wieder zu, `Wake Locks: size=0` |
| Neues Release (14.11., 22:47): App zu | kein Treffer – auch nicht für `wear://…/music/keepalive` |
| Neues Release (14.11., 22:47): App offen | 11 Treffer, auch für `wear://…/music/keepalive` – das Lebenszeichen fällt in den angemeldeten Präfix-Bereich |

Dazu: „Profile anfordern“ auf der Uhr → Phone antwortet → Einstellungen zeigen
„Presets: 14.11. 21:03“, und der Musik-Spiegel lief im selben Fenster bei geschlossener Uhr-App
weiter (52 → 63 Titel).

### Ablauf einer Übertragung (Stand 14.11.2026)

1. **Uhr-App öffnen** (Library, Spieler oder Einstellungen – egal welche Seite). Damit meldet die Uhr
   ihre Fähigkeit an, und das Phone zeigt „Uhr verbunden“.
2. Auf dem Phone „SYNC EQs“ oder „SYNC MUSIC“ starten.
3. **Danach darf die Uhr-App zu und das Display aus sein.** Jeder ankommende Paket-Eingang hält den
   Lauscher offen: 90 s normal; während ein Spiegel läuft eine Rückfall-Frist von 30 Minuten, die
   das Phone alle 2 Minuten mit `/music/keepalive` neu stellt. Nach Commit oder Abbruch gilt wieder
   die kurze Frist – die Geräte dürfen also direkt nach der Übertragung wieder schlafen.
4. Ist die Uhr-App zu, meldet das Phone „Keine Uhr gefunden – App auf der Uhr öffnen.“ Preset-Schub
   und Musik-Spiegel brauchen dann das Öffnen der Uhr-App; die Sync-Knöpfe auf dem Phone sind in
   diesem Zustand deaktiviert. „Profile anfordern“ von der Uhr aus geht immer, solange ihre App
   offen ist.

### Anzeige dem Bildschirm anpassen (14.11.2026)

* **Anzeigetakt** (`TaktPlan`): hell und sichtbar 250 ms, gedimmt 5 000 ms, unsichtbar gar nicht.
  Gemeldet wird das von der Activity-Lifecycle-Beobachtung (ON_START/ON_STOP) und der Ambient-
  Meldung der Spielseite. Vorher fragte die App immer mit 4 Hz – auch bei gedimmtem oder aus-
  geschaltetem Display.
* **Anzeige-Knopf mit drei Stufen:** `AmbientAnzeige` SCHMAL (Standard) → VOLL → AUS. Der alte
  boolean-Wert `ambientDimmed` bleibt gespeichert und wird als Spiegel mitgezogen; altes JSON ohne
  neues Feld wird migriert (an → SCHMAL, aus → VOLL). Gerätestest: Knopf zeigt nacheinander
  „Gedimmt: schmal / voll / schmal“ (zyklisch).
* **Stufe „aus" auf der echten Uhr:** nach dem Timeout ist `mLastPausedActivity` unsere Activity und
  `visibleRequested=false` → die App wurde pausiert, das Zifferblatt übernimmt das gedimmte Display.
  Bei Stufe „schmal" bleibt die Activity im Ambient **resumed** (`ResumedActivity: …MainActivity`)
  und zeichnet die reduzierte Ansicht alle 5 Sekunden.
* **Frist während eines Spiegels:** `Empfangsfenster.datenEingegangen(nachlauf)` nutzt
  `NACHLAUF_SYNC_MS = 30 min`, solange ein ausstehendes Manifest existiert (`SyncEmpfang.sitzungStart`
  / `sitzungEnde`). Das ist eine **Rückfall-Frist für Stille**, kein Größen-Timeout: das Phone schickt
  alle 2 Minuten (`SyncFristen.HALTEZEIT_MS`) ein Lebenszeichen auf `/music/keepalive`, und der
  Message-Intent-Filter der Uhr deckt genau dieses Präfix `/music` ab. Hört das Phone auf (fertig,
  abgebrochen, tot), bleibt die Rückfall-Frist übrig – nach Commit/Abbruch stellt `sitzungEnde`
  sofort wieder auf 90 s. Auf der Phone-Seite gibt es deshalb **keinen Datei-Timeout mehr**, der eine
  große Datei abschneiden könnte; dort wartet der Transport bis zur Antwort und läuft nur nach
  30 Minuten völliger Stille auf die Rückfall-Frist.
* **Kein Sperren von Display oder Schlaf:** für den Transfer hält weder die Uhr noch das Phone ein
  Wake-Lock oder `keepScreenOn`. Die Data Layer überträgt auch im Doze; gemessen wurde ein voller
  Spiegel (101 Titel, 479 MB) bei geschlossener Uhr-App und ausgeschaltetem Display.

### Großer Titel an der 40-MB-Grenze (14.11.2026, 23:26)

Neuer, bis dahin nie übertragener Titel `SyncTest - 40 MB Grenze.mp3` mit **41 900 000 Bytes
(39,96 MiB)** – direkt unter der erlaubten Grenze. Uhr-App 13 s nach dem Start geschlossen, beide
Geräte währenddes sen schlafend.

| Zeitpunkt | Beobachtung |
|---|---|
| 23:26:13 | „SYNC MUSIC“ gestartet, Lauscher offen (11 Treffer) |
| 23:26:26 | Uhr-App geschlossen (HOME) |
| 23:27:58 | Pixel `Dozing`, Uhr `Asleep`, Display aus – Lauf läuft weiter |
| 23:28 – 23:30 | Lauscher bleibt die ganzen ~5 Minuten offen (Sitzungsfrist + Lebenszeichen) |
| ≈ 23:30:30 | Datei auf der Uhr angekommen: **41 900 000 Bytes**, also ≈ 4,7 min für eine Datei (~145 kB/s) |
| 23:32:37 | Lauscher wieder **0** – 90-s-Frist nach Commit, keine `.part`-Reste |
| danach | `mWakefulness=Asleep`, `UID u0a34: INACTIVE count=0`, Uhr zeigt „punk_rock / 102 Titel“ |

Ein einzelner Titel an der Grenze braucht also **deutlich länger als die alte 300-s-Datei-Frist** –
mit dem alten Timeout wäre dieser Lauf an der Grenze der Misserfolgsrandzone gelandet; ohne
Größen-Timeout und mit 30-min-Rückfall läuft er durch.

**Spiegel-Löschung mit großer Datei (23:43:22):** nach dem Löschen des Testtitels auf dem Phone ein
weiterer Spiegel – 63 s später zeigte die Uhr **101 Dateien**, die 39,96-MiB-Datei war weg, keine
`.part`- oder `SyncTest`-Reste im Musikordner; ~90 s nach dem Commit wieder Lauscher 0.

**Nebenbefund zum Durchsatz:** dieselben vier Dateien, die am selben Tag schon einmal übertragen
wurden, waren in **79 s** wieder da (34 MB). Die Data Layer legt Asset-Inhalte inhaltsadressiert ab,
wiederholte Sendungen sind also fast gratis. Für Zeitmessungen immer neue Datei-Inhalte benutzen.

### Wiederaufnahme des letzten Titels (19.11.2026, Emulator 454 × 454)

* `WatchSettings` Formatversion 3 speichert `lastTrackPath`; der Pfad wird über die
  Playback-Beobachtung bei jedem Titelwechsel (Bedienung und Auto-Advance) festgehalten.
* `FilterSettings.bereit` signalisiert, dass der gespeicherte Stand von der Platte gelesen ist –
  die Wiederaufnahme wartet darauf und läuft nur einmal pro App-Start.
* Nach dem Neustart wird das Album zum Pfad gesucht, die Albumliste samt Titelindex ohne Play
  gesetzt und die Now-Playing-Seite direkt geöffnet. Läuft eine Sitzung noch, gilt deren
  laufender Titel statt des gespeicherten.
* Abnahme Emulator: „Album A / Ton 220“ ausgewählt, App per `force-stop` beendet und neu
  gestartet – Now-Playing zeigte wieder „Ton 220“, **pausiert**; drei Neustarts hintereinander
  ergaben dasselbe Ergebnis.
* Abnahme TicWatch Pro 5: `punk_rock / you can't stop me` gewählt, per `force-stop` beendet und
  neu gestartet – Now-Playing öffnete wieder „you can't stop me“, **pausiert**.

### EQ-Statistik auf der Uhr (20.11.2026, Watch-Emulator und echte Geräte)

* **Zählregel:** nur `isPlaying == true` **und** `activePresetId != null`; Bypass oder Pause zählen
  nicht. Bei Titelwechsel, Pause/Stop, Preset-Auswahl und Dienst-Ende wird das aktive Segment
  geschlossen und ins `watch_stats_journal.jsonl` geschrieben (kein 60-s-Intervall).
* **Persistenz:** Beim Öffnen der Statistik-Seite bzw. vor dem Sync wird das Journal in
  `watch_stats.json` verdichtet; `journalCompactedThroughMs` verhindert doppelte Tage nach einem
  Absturz. Report und ACK laufen über `/stats/request` und `/stats/ack`; das Phone fordert den
  Report an, die Uhr antwortet mit `/stats/report`.
* **Löschung:** Die Uhr entfernt nur die Werte ihrer Momentaufnahme nach `STATUS_APPLIED` und
  behält neuere Segmente. Ein fehlendes ACK lässt den stabilen Report für einen erneuten Versuch
  stehen; das Phone dedupliziert über `lastWatchReportId`.
* **Prüfung:** Im Watch-Emulator wurde nach App-Start/Wiedergabe ein geschlossenes 5-Sekunden-Segment
  im Journal gefunden. Auf den echten Geräten (TicWatch Pro 5 → Pixel 10) wurde ein Segment von
  51,645 s im Journal aufgezeichnet, vom Phone übernommen und mit
  `lastWatchReportId` gespeichert; nach dem APPLIED-ACK war `watch_stats.json` auf der Uhr leer
  (`days={}`, `pending=null`), das Journal entfernt. Ein danach gestartetes Segment (7,4 s) blieb
  als neues Journal erhalten – die Löschung des bestätigten Snapshots hat neuere Daten nicht
  verneint.

Capabilities:

* Watch: `de.notchedmusic.watch.filter_profiles`
* Phone: `de.notchedmusic.phone.sync`

Grenzen: Manifest ≤ 1 MiB, Datei ≤ 40 MB, JSON ≤ 8 KiB, Musik nur unter dem App-Musikordner und
nur unterstützte Audio-Endungen.

## Offene Punkte / noch nicht gebaut

* **Instrumentierte Compose-Tests** (EQ-Auswahl, Spieler, Einstellungen) fehlen; die
  Device-Prüfung erfolgte manuell über adb/uiautomator.
* **10-Dateien-Lauf**, **Abort mitten im Upload**, **Speicherknappheit** und **ungültiges Preset**
  sind im Code vorgesehen, aber noch nicht als reale E2E-Fälle protokolliert. Erledigt sind dagegen:
  ein Titel an der 40-MB-Grenze (≈ 4,7 min, siehe oben) und die Spiegel-Löschung genau dieses Titels.
* **30-Minuten-A2DP-Wiedergabe** und die **OLED-Pixelmessung auf der echten Uhr** stehen aus.
* **Store-Signing und Proguard** sind nicht nötig; das persönliche Release ist mit dem lokalen
  Debug-Schlüssel signiert. Das runde **App-Icon** ist noch offen.
* **Fortschrittsbogen und Modusknopf** sind auf der kompakten Spielseite umgesetzt; die Presetwahl
  liegt zusätzlich auf der EQ-Seite. Ein eigener „Zum Album“-Knopf entfällt zugunsten der
  Wear-Zurück-Geste.

## Nächster Schritt

1. Die offenen E2E-Fälle aus `PLAN.md` 7.3 abarbeiten, zuerst großer Upload, Abort und
   ungültiges Preset.
2. Screenshots/Logauszüge der Sync-Seiten und der drei EQ-Zustände archivieren.
3. Instrumentierte UI-Tests für die Watch-Seiten ergänzen.
4. Danach die verbleibenden Gerätemessungen (OLED, Akku, A2DP) und das App-Icon.
