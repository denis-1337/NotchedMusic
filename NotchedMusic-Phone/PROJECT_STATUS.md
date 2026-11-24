# Project Status – NotchedMusic Phone

**As of:** November 24th, 2026 (English phone UI, 1.0.2 release)
**Overall Status:** Working; real Wear Data Layer sync accepted on Pixel 10 ↔ TicWatch Pro 5. The Watch app has been brought up to the same level (three presets, seven filter types,
Music Mirror).

---

## 1. Short version

The phone app is fully implemented:

- Library via SAF without `MANAGE_EXTERNAL_STORAGE`
- Media3/ExoPlayer with its own DSP chain (Biquad EQ + stereo-linked limiter)
- Three presets with six bands each, seven filter types, keyboard entry and bypass
- Sine tone tester with 1 Hz steps and auto advance
- Background playback with foreground media notification including cover and transport controls
- **Real sync** for presets and music via the Wear Data Layer (Bluetooth/Wi-Fi), with
  Progress, Abort, Timeout, Retry, Manifest + SHA-256 and Commit
- Persistence in DataStore, versioned JSON, fallback to defaults
- **Resume:** The folder and last played track are saved and restored after restart (title by SAF document ID, not by path).
- **EQ statistics:** fourth tab `STATS`; Phone only counts music playing with active EQ, the watch
  Only music playing with an active preset counts. Journal + daily file, compression when opening the
  Statistics page and when syncing; Transfer to the phone via DataItem with idempotent report ID,
  Watch only deletes its statistics after a confirmed ACK
- **Background playback:** Media3 manages the media notification and foreground service; incoming calls, hang-ups and screen locks no longer stop playback early.
- **Service startup recovery:** the activity resumes and playback actions re-start `PlaybackService`; playback can no longer run from the activity without a foreground service after a denied background start.

The watch app under `NotchedMusic-Watch` was migrated to the same data model: three presets, seven filter types, selection only on the watch, and music mirroring with deletion only after a confirmed commit.

---

## 2. Environment and build

| Component | Value |
|---|---|
| Android Studio | 2026.2.2 |
| SDK platform | android-37 (Android 17) |
| Build tools | 36.0.0 / 36.1.0 |
| Android tools | platform-tools 37.0.1, Emulator 37.2.13 |
| JDK | Java 21 (Zulu), Gradle Toolchain 17 |
| Emulator | `Pixel_10_API_37`, 1080×2424 |
| Real Phone | Pixel 10, Android 17 / API 37, 1080×2424 |
| Real watch | TicWatch Pro 5, Wear OS 4 / **API 33**, 466×466 |
| AGP/Kotlin | 9.5.1 / 2.3.20 |
| Compose BOM / Material 3 | 2026.09.00/1.4.0 |
| Media3 | 1.11.1 |
| Play Services Wearable | 20.0.1 |
| minSdk / compileSdk / targetSdk | 33 / 37 / 37 |
| applicationId | `de.notchedmusic.phone` (deliberately also for the Watch app) |

Construction and testing (all green):

```bat
gradlew.bat :core:test :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
gradlew.bat :app:lintDebug
```

- **102 unit tests** (74 in `:core`, 28 in `:app`), 0 errors, 0 skipped.
- **Lint:** 0 errors, 0 warnings.
- **Release APK (installed):** `app/build/outputs/apk/release/app-release-signed.apk` (≈ 32 MB, locally signed with debug key, not debuggable). Debug APK ≈ 42 MB.

---

## 3. Implementation status according to plan

| Area | Status | Proof |
|---|---|---|
| SAF folder selection, persistable sharing | finished | Fresh picker, approval “ALLOW”, obtained via restart |
| Root + direct subfolders | finished | `TestMusic` + `Album A` + `Album B`; “CD 2” does not appear as a node |
| Recursive scan, natural sorting | finished | 2,005 files in **1,389 ms** (target < 1,500 ms), then 6 titles / 3 nodes in 103 ms |
| Formats | finished | Filter for MP3/M4A/AAC/OGG/Opus/FLAC/WAV incl. MIME check, MP3 first || Player with title/folder arrows | finished | Arrows, cyclic change, bottom sheet folder selection |
| Play, Auto Advance, Repeat | finished | OFF/TITLE/ALL; 13-minute endurance run, position continues |
| App volume 0–100% / −60…0 dB | finished | Slider tested (50% = −30 dB, 100% = 0 dB) |
| Album cover | finished | embedded → Storage → Disk Cache (512 px) → MusicBrainz/iTunes → Placeholder; embedded offline verified |
| EQ: 3 presets × 6 bands | finished | Default Preset 1: Notch 8 kHz, Q 8, Gain −20; Preset 2: 6 kHz/Q 4; Preset 3 empty |
| EQ types and input fields | finished | OFF, Notch, Bell, Low Shelf, High Shelf, Lowcut, Highcut; Comma and period |
| Bypass | finished | default on; Filter/PreGain are omitted, volume and limiter remain |
| DSP chain | finished | Biquad transposes DF-II, 128-sample ramp, stereo limiter −1 dBFS |
| Clipping display | finished | OK/Limiter/Clip with 400 ms hysteresis; “Clip” triggered red in the emulator |
| Background service | finished | `MediaSessionService`, FGS type `mediaPlayback`, media notification with cover |
| Headphone/Media Buttons | finished | `KEYCODE_MEDIA_PAUSE/PLAY/NEXT` control the session correctly |
| `setHandleAudioBecomingNoisy(true)` | finished | active in code |
| sine tone | finished | `AudioTrack`, 100-15,000 Hz, 10 Hz slider, ±1 Hz, long press ±10 Hz |
| **Preset sync real** | finished | Phone → Watch DataItem + ACK; Watch reported “Presets adopted from Pixel 10: [Preset 1, Preset 2, Preset 3]” |
| **Music sync real (mirror)** | finished | manifest → missing assets → commit; Phone reports “Music mirrored: 2 tracks.” |
| **Mirror Deletion** | finished | Unselected track deleted after commit on watch; old inventory survives deletion |
| Sync timestamp | finished | restored after reboot; last run November 13, 2026, 11:13 p.m. |
| Persistence | finished | DataStore, a JSON entry, format version, conflated writers |
| Resumption | finished | Folder + last played track survive app restart; Emulator recording with album B and “03 - Ton 880” in the subfolder “CD 2” |
| EQ statistics | finished | fourth tab `STATS`; Total/average day/today, week-month-year chart with arrows and visible scale (max, half, 0), short times less than a minute in seconds; `days.json` + Journal, compression when opening and when syncing; Sync path `/stats/request` → `/stats/report` → `/stats/ack`, watch is only deleted after APPLIED-ACK. Emulator acceptance of the phone page and watch record; real handover verified on Pixel 10 ↔ TicWatch Pro 5 |
| Error images | finished | Scan/SAF error with understandable text and “Try again”; Sync errors per ACK reason |
| Dark Theme / Usability | finished | English UI texts, big targets, content descriptions, no pure color coding |

### Deliberately so decided

- **Phone is master and editor.** Three presets are only edited here; the watch shows selection.
- **Music sync is a mirror.** Unselected or missing songs will be displayed on the watch
  only deleted after a complete upload and confirmed commit.
- **Same `applicationId`.** Watch APK is also called `de.notchedmusic.phone`; the
  Watch namespace remains `de.notchedmusic.watch`. Otherwise Data Layer will not find the package.
- **No wide storage permission.** SAF only.
- **No dedicated Bluetooth stack.** A2DP/Codec/Latency are on the system; the Data Layer chooses Bluetooth or Wi-Fi.- **Online cover only with artist/album tags.** A file name alone is not a useful search term; only title/artist/album will be sent.

---

## 4. Sync implementation (Phone)

| File | Task |
|---|---|
| `sync/WearSyncTransport.kt` | Capability, Presets, Manifest, File Retry, Commit, Cancel; Fallback deadlines from `SyncFristen`, signs of life every 2 min |
| `sync/WearGateway.kt` | `PlayServicesWearGateway`: `findNode`, urgent DataItems, FD assets with Play-Services-Digest, Delete, Message |
| `sync/SyncAckBus.kt` | correlates `CompletableDeferred` to `(syncId, kind, index)` |
| `sync/AssetSource.kt` | SHA-256, MIME, size from SAF document |
| `sync/SyncRepository.kt` | Selection, Progress, Cancel, UI State, `lastSyncMs` |
| `sync/WearSyncListenerService.kt` | Watch request `/filter/request`, ACKs, registration of phone capability; additionally processes `/stats/report` |
| `sync/StatsReportBus.kt` | transfers the statistics report of the watch to the waiting sync job |
| `stats/StatsStore.kt` | Day file `days.json` + Journal `journal.jsonl`; Compaction, idempotent watch-merge |
| `stats/EqStatsTracker.kt` | only measures EQ listening time during playback with active EQ |
| `stats/StatsChart.kt` | Week (total per day), month (total per ISO-KW) and year (total per month) |
| `stats/StatsRepository.kt` | UI state, refresh and merge of the watch statistics |
| `AppGraph.kt` | wired repository, transport, tracker and service |

### Statistics Sync (Phone ↔ Watch)

* When opening the statistics page or before the merge, the active segment is closed and that
  Journal condensed in `days.json`; a crash before that counts with `journalCompactedThroughMs`
  not double.
* The phone side sends `/stats/request`. The watch responds with a DataItem
  `/stats/report` and a stable `reportId`; the phone only merges days that it doesn't know yet.
* After successfully writing `days.json`, the phone confirms with `/stats/ack`. Only at
  `STATUS_APPLIED` causes the watch to delete its already applied snapshot; beats writing
  If the confirmation fails or is not received, the watch retains the data for another attempt.

Fixed limits: Manifest ≤ 1 MiB, File ≤ 40 MB, JSON ≤ 8 KiB, Music only supported
audio endings; only deleted within the app music folder of the watch.

### Proven stumbling block solutions

* Write DataItems with **`setUrgent()`**; otherwise the data layer waits for an idle/loading window
  and that looks like a timeout.
* Address assets via the **Play-Services-Digest** (`audio_digest`, Base64-SHA-1); the
  hexadecimal SHA-256 remains the technical integrity check.
* Read the asset metadata only after the actual transfer; an immediate one
  `DataMapItem.fromDataItem` can fail with `ASSET_UNAVAILABLE`.
* Keep watch receivers synchronous in callback and app-wide scope; GMS releases the binding briefly
  after the event.
* `ApiException: 4006 DUPLICATE_CAPABILITY` is harmless (app + listener register same
  capability).
* **No size timeout, no screen lock:** `SyncFristen` only knows **fallback deadlines for
  Silence** (presets 60 s, manifest and file 30 min, commit 10 min). A file with 40 MB can be over
  The Bluetooth bridge takes five to six minutes without being canceled. During one
  Mirroring, the phone sends `/music/keepalive` every 2 minutes; after that (success, error, abort).the job is gone - `WearSyncTransportTest` checks that there are signs of life during this and afterwards
  no more. Neither the phone nor the watch maintains a wake lock or `keepScreenOn` for the transfer.
* **Really measured against the limit (11/14/2026, 11:26 p.m.):** a title that had never been broadcast until then
  **39.96 MiB** took **≈ 4.7 min** to watch (~145 kB/s). Watch app 13 s after starting
  closed, pixel `dozing`, watch `asleep`; the watch eavesdropper remained open for the entire five minutes and
  closed again 90 s after commit, no `.part` leftovers. A title on the border is therefore over
  the old 300 s deadline - it runs through without a size timeout.
* **Throughput can only be measured with new content:** four files already sent on the same day
  (34 MB) were back on the watch in **79 s** - the data layer places asset content in a content-addressed manner
  from, so repeated broadcasts are almost free.
* **Start sync only when the watch is available:** “SYNC EQs” and “SYNC MUSIC” are deactivated while
  `sync.watchAvailable != true`. So both open at start is forced; **must stay open**
  only the phone, the watch app is allowed to be open again after starting.
* **The watch only receives when its app is open** (watch-side `SyncReceive` switch since
  November 14, 2026). If “No watch found – open app on the watch.” appears, the watch app is too –
  no pairing error. A running sync survives closing the watch app.
* **The Phone app's data layer listener is intentionally NOT bound to visibility:** `WearSyncListenerService`
  needs the ACKs of the watch even when the phone is locked - a long mirror runs
  otherwise off. What saves energy here instead is `AppViewModel.clipUeberwachung(an)`: the 10 Hz
  The peak level query only runs as long as the player page is visible
  (`LifecycleStartEffect` in `PlayerScreen`) – with another tab, screen off or in the background
  the ViewModel no longer asks.
* **Throughput of the data layer via the Bluetooth bridge:** measured in real terms **≈ 2 tracks/minute**
  (≈ 0.12 MB/s). Full mirror `punk_rock` with 101 titles / 479 MB on November 14th, 2026 from 9:04 p.m. to
  9:31 p.m.: Watch after 101 files, album shows "101 tracks", settings "Music: 11/14 9:31 p.m.";
  The watch app was closed at 9:04 p.m., but the sync still worked.

---

## 5. Measurements

| Measurement | Result | Review |
|---|---|---|
| DSP, 6 bands stereo @48 kHz, warm | **11-14 ns/sample** (last single run: 11,463; fluctuates under host load) | Target < 15 ns achieved* |
| Library, 2 005 files | **1 389 ms** | Target < 1 500 ms |
| Library, 6 files | **103 ms** | – |
| App storage after endurance run | ~104MB PSS | inconspicuous |
| Underruns / Crashes in the 13-minute run | **0 / 0** | Target 0 Underruns |
| StrictMode in debug build | no violations | no main thread I/O detected |

\* A cold first JVM run was around 26 ns; The test therefore preheats and evaluates 20 blocks
the steady state. Individual barrels fluctuate under load.

---

## 6. Acceptance on real devices (11/13/2026)

| check | Result | receipt |
|---|---|---|
| Phone installed/launched on Pixel 10 | without crash, sync page operable | 22:18-22:46 |
| Watch installed as `de.notchedmusic.phone` | App is running, Activity `de.notchedmusic.watch.MainActivity` | multiple |
| Capability Search | Phone finds Watch, Watch finds Phone | GMS/WearSync Logs |
| Preset Sync | three presets accepted and confirmed; Watch UI shows P1/P2/P3 | 22:44 || Music sync 2 tracks | 264 695 B + 141 289 B transferred, SHA-256 checked, commit confirmed | 22:32 / 22:46 |
| Music sync final build | Manifest with 2 tracks, 0 missing, commit confirmed, phone "Music mirrored: 2 tracks." | 23:13 |
| Music Mirror Deletion | 1 unselected title deleted after commit | 22:38 |
| Abort | 101-title run canceled after manifesto; Watch reports “0 half files”, old inventory remains | 23:07 |
| Reboot | Library rescanned, sync selection/state operable after app restart | 22:37 / 22:46 |
| App restart Watch | Files and presets are retained | multiple |
| Lint/Build | 0 errors, BUILD SUCCESSFUL | 22:47 |

The emulator continues to be the reference for complex playback/cover/endurance tests.
On the real phone, the sync in particular was validated in this run, not the entire thing
A2DP chain.

---

## 7. Emulator Decrease (Pixel 10, API 37)

| check | Result | Time/Receipt |
|---|---|---|
| App start after installation | without crash, operable | multiple |
| SAF picker + persistable release | works, root remains after reboot | 21:02 / 21:26 |
| scan | 6 titles, 3 knots; nested folder no own node | 21:02 |
| Playback | AudioTrack/ExoPlayer starts, title changes, repeat takes effect | multiple |
| Background | 13 minutes with `isForeground=true`, `foregroundId=1001`, Session PLAYING | 21:17:43-21:30:43 |
| Media release | Title, Artist, Cover, Play/Pause, Forward/Back, Seek | [notification-cover.png](docs/screenshots/notification-cover.png) |
| Media buttons | Pause/Play/Next change session status | 7:30 p.m. |
| App restart | Get Root, Presets, Volumes, Sync Timestamps | 21:26 |
| Player Resume | After force-stop/restart, “Album B · 01 - Tone 330” or “Album A/CD 2 · 03 - Tone 880” was selected again | Nov 19, 2026, Emulator |
| Statistics page | four tabs PLAYER/EQ/SETUP/STATS; Total/average day/today, switch week/month/year, arrows; Bars for day, ISO-KW and month correctly grouped | Nov 20, 2026, Emulator |
| Statistics Persistence | Playback with active filter starts recording; after pause + `force-stop`/restart still “6 min” in the statistics tab | Nov 20, 2026, Emulator |
| Watch record | Watch Journal (`watch_stats_journal.jsonl`) contains a closed 5 segment after app launch/playback; Real handover to the phone was recorded with 51,645 s and APPLIED-ACK | November 20, 2026, Watch Emulator and Real Devices |
| Call/screen lock (bug fix) | Playback pauses for an incoming call and resumes after hang-up; the foreground service remains active when the screen locks. Call/hang-up tested on the emulator; real screen-lock case verified on Pixel 10 (`AudioTrack` continued `started` after 32 s, `isForeground=true`, no new AudioHardening messages). | November 24, 2026, emulator and release APK on Pixel 10 |
| EQ defaults | Preset 1: Notch 8000 Hz, Q 8.00, −20.0 dB | [eq-preset1.png](docs/screenshots/eq-preset1.png) |
| Clip display | Bell 220 Hz +12 dB and preamp +12 dB → red “clip” indicator | 19:39 |
| sine tone | 440→441 Hz via “+”, Start/Stop | earlier |
| Sync real | Music mirrored status, timestamp, watch files checked | 22:32-22:46 |
| Cover online | tagged MP3 (Pink Floyd/Time) → cover loaded and cached | [player-time.png](docs/screenshots/player-time.png) |
| Cover offline | Airplane mode + cleared cache → wildcard, no file in cache, playing | 9:20 p.m. || Cover embedded | APIC cover in `Embedded.mp3`; displayed in airplane mode without online access | [embedded-cover-offline.png](docs/screenshots/embedded-cover-offline.png) |
| Volume Slider | Player and test tone slider correct after fix (50% = −30 dB; 66%) | 22:40 |
| Revocation of rights | Code path exists, not simulated end-to-end | open |

More screenshots: [docs/screenshots](docs/screenshots).

---

## 8. Open points/risks

1. **Not all E2E cases were actually tested:** 10-file run, abort in the middle of the upload (partial `.part`),
   Memory shortage, invalid preset. The code and ACK paths are present.
   Done on November 14th, 2026: new title at the 40 MB limit (41,900,000 bytes ≈ 4.7 minutes on the watch,
   Lauscher remained open, no `.part` leftovers) and the mirror deletion of exactly this title.
2. **Full A2DP/headphone acceptance on the Pixel 10 is pending.** So far, emulator audio plus
   Sync on the real device.
3. **No end-to-end test for revoked SAF rights.** Error path implemented, not per
   System intervention simulated.
4. **Release APK is ready, no need for store.** Build and installation are running; is signed
   locally with the debug key. Proguard/R8 is deliberately switched off, there is no store preparation required.
5. **Instrumented tests are missing.** The 83 tests run on the JVM; the device was accepted
   manually via adb/uiautomator.
6. **Online covers** are optional and dependent on tags and network. The app remains without it
   Network fully usable.
7. **Watch UI testing:** Compose/instrumentation testing for the watch is still open; the
   Device acceptance there was done manually.

---

## 9. Artifacts

| Artifact | path |
|---|---|
| App APK (release, installed) | `app/build/outputs/apk/release/app-release-signed.apk` |
| App APK (Debug, Development) | `app/build/outputs/apk/debug/app-debug.apk` |
| Test music (`Embedded.mp3`, `Online.mp3`) | `testdata/` |
| Screenshots | `docs/screenshots/` |
| Quick Guide | `README.md` |
| Plan | `PLAN.md` |
| counterpart | `Z:\android_projects\NotchedMusic\NotchedMusic-Watch` |

During testing on the Pixel 10, test music was under `/sdcard/Music/SyncTest` (2 tracks, 396 KB)
and was reflected on the watch. Once completed, the test files were removed from the phone and watch.

---

## 10. Change history (excerpt)

- **17:44-18:00** Scaffold, versions from local Gradle cache, `:core` model/DSP/protocol.
- **18:00-19:00** `:app` data layer, scanner, playback, media3, UI, sync mock.
- **19:00 - 19:45** Emulator setup, SAF test, test music, player/EQ/sync test.
- **19:45 - 20:30** Bugs: manifest application class, clip thresholds, test assertions,
  formats, cover cache; Fixed foreground service with its own media notification.
- **20:30-21:00** Background/Media Key Test, Scan Performance 2,005 files.
- **21:00 - 21:30** 13 minute endurance run, online/offline cover test.
- **21:30 - 22:30** Media release with cover and enriched metadata, slider bug fixed,
  Sync timestamp loaded on startup, resources/lint cleaned up.
- **11/13, 4:00 p.m. - 10:50 p.m.** Phone/Watch unified: SyncProtocol v3, Watch data model,
  Data layer transport, manifest + assets + commit, real device acceptance, `applicationId` alignment
  on `de.notchedmusic.phone`, documentation updated.
- **11/19** Player resume: `lastFolderKey` and `lastTrackDocumentId` in `AppSettings`
  (Format version 2), Restore after SAF scan, selection is made with every title/folder changedetained; on the API 37 emulator and actually on the Pixel 10 via force stop/restart
  checked (2 / 101 · “You Can’t Stop Me” selected again after restart).
- **11/20** EQ statistics: fourth tab, journal/day persistence, chart for week/month/year
  with Y scale (max/half/0) and seconds display for short times, `StatsProtocol` v1, synchronous
  Watch merge with stable report ID and deletion only after APPLIED-ACK; Watch counts locally
  active preset. Phone tests 102/102, watch tests 150/150, lint and release signing green.
  Real handover on Pixel 10 ↔ TicWatch Pro 5: watch report with 51,645 s was adopted,
  `lastWatchReportId` set, watch file then emptied; the real devices are now running again
  on the release APKs.
- **November 24th** Bug fix after call/screen lock: the custom foreground service was replaced by
  Media3 `MediaSessionService`, and the custom media notification was removed. `addSession`
  registers the session for automatic notification and foreground management. Emulator: incoming
  call, hang-up, screen off; playback remains in the foreground after 15 s and continues. Signed
  release APK on the real Pixel 10: after screen lock, the AudioTrack continued after 32 s, the
  service stayed active with `isForeground=true` and a media notification; no new AudioHardening
  messages appeared.
- **November 24th, UI translation** The complete phone UI was translated to English: tabs, player/EQ/setup/sync/statistics labels, validation and sync messages, chart labels, month/day names and date formats. The tests were updated for the English chart labels; release 1.0.2 contains the translated APK.
- **November 24th, second fix** Root cause of the remaining screen-lock stop: the playback service
  could be absent while the activity continued to own the player, for example after an install or
  launch while the phone was locked. Playback then ran without a foreground service and Android
  muted it at screen lock. `MainActivity.onStart()` and playback actions now restart
  `PlaybackService` before playback. Verified on the real Pixel 10 with the 1.0.1 release APK:
  service stopped, playback started -> service foreground; after 30 s locked, the AudioTrack
  remained `started` with `mutedState:none` and no new AudioHardening messages.

---

## 11. Next sensible steps

1. Process the open E2E cases on real devices (large upload, abortion, storage, invalid
   preset).
2. Run full A2DP/headphone test on Pixel 10.
3. Add Compose instrumentation tests for sync page, player and EQ fields.
4. Optional: R8/Proguard and its own release key store; not necessary for personal installation.
5. Optional: Cover the call/hang-up case on real devices as well; the emulator path and the real screen-lock case on the Pixel 10 are already verified.
6. Optional: Preset export/import, cover disk LRU with size limit.