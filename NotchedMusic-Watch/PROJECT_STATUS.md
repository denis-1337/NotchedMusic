# Project Status – NotchedMusic

> **This file is the entry point for each new session.** Last updated: 11/20/2026,
> after statistics recording on the watch, statistics sync protocol and phone merge. Rules: update
> this file after every completed step; change `plan.md` only after consultation.

## Short version to get you started

Wear OS music player with real-time EQ (notch filter) for tinnitus retraining. The target device is the
**Mobvoi TicWatch Pro 5** (Wear OS 4 / **API 33**, 466 × 466 round, OLED). The watch plays autonomously;
the Phone app (`de.notchedmusic.phone`) is the editor for **three presets** and the master for the
**Music Mirror**.

* **Done and tested on both real devices:** common `EqPreset` model, seven
  Filter types, three presets, migration from the old A/B status, real sync via the Wear Data Layer
  (Presets and music with SHA-256 + Commit), TicWatch UI without preset editor.
* **EQ statistics:** The watch locally counts only music playing with an active preset (`activePresetId != null`),
  writes closed segments to the journal and transfers a stable snapshot during sync. The watch
  deletes its snapshot only after the phone sends a `STATUS_APPLIED` ACK; without ACK the data
  remains available for another attempt.
* **Build/Test:** `assembleDebug` and `assembleRelease` green, **150 / 150 JVM tests green**,
  Lint 0 errors. The release APK is signed with local key and installed on the watch.
* **Power saving:** The two Data Layer receivers are disabled in the manifest (`enabled="false"`)
  and are opened by `SyncReception` only when the app is open or data arrives (90 s receiver
  window). During a music mirroring session, the phone refreshes a **30-minute fallback window**
  every 2 minutes with `/music/keepalive`; there is no wake lock and no screen hold. The display
  rate follows the screen:
  4 Hz bright, dimmed for 5 s, not at all if the app is invisible. The display button has three levels:
  narrow / full / **off**; in the dimmed state with "off", the watch face takes over.
* **Open:** instrumented Compose UI tests, large file at limit, abort/storage-shortage tests,
  30-minute A2DP/OLED measurement and app icon. Release signing for personal installation is done.
* **Resuming:** `lastTrackPath` in `WatchSettings` (format version 3); after the app restarts, the
  watch preselects the saved album and title without starting playback. The path is recorded on
  every title change, including auto-advance.
* **EQ statistics:** `WatchStatsStore` with journal + `watch_stats.json`, local tracker in
  `PlaybackService`; sync only when the watch is reachable and the receiver is open. The snapshot
  is deleted only after an ACK.
* **Next step:** document the open device tests and screenshots of the sync pages.

## Frame for new sessions

| | |
|---|---|
| Language/Style | Kotlin, 2 spaces indentation, KDoc comments **in German**, no TODO remainders |
| Namespace | `de.notchedmusic.watch` (Source: `app/src/main/kotlin/de/notchedmusic/watch/`) |
| **applicationId** | `de.notchedmusic.phone` – deliberately identical to the phone app (see sync note below) |
| Start | `adb ​​shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity` |
| Project folder | `Z:\android_projects\NotchedMusic\NotchedMusic-Watch` |
| Module | just `:app`, a Wear OS app module || Check build | `./gradlew.bat app:assembleDebug app:assembleRelease app:testDebugUnitTest app:lintDebug --console=plain` |
| Release APK | `app/build/outputs/apk/release/app-release-signed.apk` (≈ 32 MB, locally signed with debug key, not debuggable) |
| Last status | BUILD SUCCESSFUL, **150/150 tests**, benchmark **11-14 ns/sample**, worst buffer **< 0.1 ms** (last run 11.054 ns / 0.097 ms; limit 15 ns / 1 ms) |
| Real watch | TicWatch Pro 5, Android 13 / API 33, 466 × 466, `adb-C102X37220190-fKNFqi._adb-tls-connect._tcp` |
| Real Phone | Pixel 10, Android 17 / API 37, `adb-59061FDCR000HC-DasVPx._adb-tls-connect._tcp` |
| Screen timeout | **do not set again:** `screen_off_timeout` was set to 2,147,483,647 in the tests and was turned back to normal on November 14, 2026 (watch 15,000 ms, pixels 60,000 ms; `stay_on_while_plugged_in = 0`). With the maximum value, the OLED never turns off and Doze never starts. |
| Music folder of the watch | `/sdcard/Android/data/de.notchedmusic.phone/files/Music/` (applicationId!) |
| Reference book | Wear OS basics, measurement methods, adb guide: `wearos_leitfaden.artifact.md` in this session's Artifacts folder |

### Versions (chosen deliberately - don't change without reason)

| | Value | Why |
|---|---|---|
| AGP | 9.5.1 | Wear Compose M3 requires AGP ≥ 9.1.0 **and** compileSdk ≥ 37 |
| Gradle wrapper | 9.6.0 | AGP 9.4+ requires Gradle ≥ 9.6.0; Follow along with `distributionSha256Sum`! |
| compileSdk/targetSdk | 37 / 37 | API 37 is the maximum for this AGP line |
| **minSdk** | **33** | real TicWatch reports API 33 |
| Kotlin | 2.3.20 | Compose compiler runs over the same `version.ref` |
| Media3 | 1.11.1 | AudioProcessor API is `@UnstableApi` → `@OptIn(UnstableApi::class)` |
| Wear Compose M3 / Foundation | 1.7.1 | `ScreenScaffold`, `TransformingLazyColumn`, `Modifier.rotaryScrollable` |
| Navigation 3 | 1.0.1 | including `wear.compose.navigation3` (swipe-to-dismiss) |
| Play Services Wearable | 20.0.1 | Data Layer, CapabilityClient, MessageClient |

## What is built

### Model and protocol

| File | Content |
|---|---|
| `model/FilterBand.kt` | `FilterType` with **seven** types: `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`, `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT`; Band with frequency/Q/Gain/`enabled` |
| `model/EqPreset.kt` | `@Serializable` preset format, six bands, validation, pre-amplification |
| `model/DefaultPresets.kt` | P1/P2/P3 with stable IDs; P3 as reserve |
| `model/WatchSettings.kt` | Format version 2: three presets, active selection, `volumeDb`, display switch, sync timestamp; Migrating from A/B (legacy) |
| `protocol/SyncProtocol.kt` | v3 payload, ACK/Cancel/Manifest encode/decode, size and index check |
| `protocol/StatsProtocol.kt` | v1 report/ACK for EQ listening time, daily limits and stable `reportId` |
| `stats/WatchEqStatsTracker.kt` | only measures music playing with an active preset; closes segments at pause/title change |
| `stats/WatchStatsStore.kt` | Journal + `watch_stats.json`, compaction, idempotent report snapshot and ACK handling |
| `stats/WatchStatsRepository.kt` | Watch UI state and connection to the data layer sync |
| `data/StatisticsTransferReceiver.kt` | `/stats/request` and `/stats/ack`; sends `/stats/report` as DataItem |
| `legacy/LegacyFilterProfiles.kt` | reads the old A/B DataStore and maps A→P1, B→P2, P3→Default |

### DSP

| File | Content |
|---|---|
| `dsp/Biquad.kt` | RBJ coefficients for all seven types, pairwise blending || `dsp/EffectChain.kt` | Biquads in series per channel, pre-amplification, volume, fixed working buffer |
| `dsp/Limiter.kt` | Ceiling −1 dBFS, 1 ms attack / 50 ms release |
| `dsp/Pcm.kt` | 16 Bit Little Endian ↔ FloatArray, rounding |
| `dsp/EffectAudioProcessor.kt` | Media3-`BaseAudioProcessor`, coefficients from the real sample rate |
| `dsp/EffectAudioProcessorChain.kt` | Chain for tempo/pitch unchanged, silence skipping off |

### Data, sync and background

| File | Content |
|---|---|
| `data/MusicLibrary.kt` | recursive scan; a direct subfolder = an album, deeper subfolders are included |
| `data/MusicFolders.kt` | private app music folder |
| `data/MusicMirror.kt` | Manifest check, target paths, deletion plan, `.part` backup |
| `data/MusicSyncStore.kt` | pending manifest state between manifest and commit |
| `data/MusicTransferReceiver.kt` | `WearableListenerService`: manifest, upload, commit, cancel; ACK via message |
| `data/MusicLibraryRefresh.kt` | Signal to re-read the library after commit |
| `data/WearDataCleanup.kt` | cleans up orphaned DataItems |
| `playback/FilterSettings.kt` | a source of truth: RAM-`StateFlow` + DataStore, Migration |
| `playback/PhoneFilterReceiver.kt` | accepts `/filter/profiles` DataItems, validates and confirms via `/filter/ack` |
| `playback/PhoneLink.kt` | queries the phone capability `de.notchedmusic.phone.sync` and sends `/filter/request` |
| `WatchApplication.kt` | registers the watch capability `de.notchedmusic.watch.filter_profiles`; provides an app-wide `CoroutineScope` |
| `MainActivity.kt` | Navigation 3: Albums · Titles · Players · EQ · Settings |

### Watch UI

* **Albums** and **Titles** without covers; "Reload"; current title highlighted.
* **Player (466 × 466 verified):** a fixed, compact page - progress sheet with time,
  Title, preset mode, three compact transport buttons and a narrow volume bar. The
  Bar runs from silent to right to full on the left; the display shows the level in percent.
  All elements fit without scrolling; Back is done via the Wear gesture/system.
* **EQ**: only **Off · P1 · P2 · P3**; no band editors, no cogs – editing only
  on the phone.
* **Settings**: Phone status as three lines, “Request profiles”, memory usage (none
  technical path more), “Reload”;
  understandable conditions even without a phone.
* OLED friendly night scheme, no persistent animations.

## Verified facts (do not re-assert, re-measure if changed)

* **Build + Tests:** `app:assembleDebug` green, `app:testDebugUnitTest` **139/139 green**,
  `app:lintDebug` **0 errors**.
* **Test distribution:** `MusicLibraryTest` 15, `Reception WindowTest` 13, `EqPresetTest` 12,
  `EffectChainTest` 11, `FilterSettingsStoreTest` 11, `BiquadTest` 10, `VolumeStepsTest` 9,
  `MusicMirrorTest` 9, `LimiterTest` 7, `SyncProtocolTest` 6, `AmbientDisplayTest` 6,
  `AlbumJumpTest` 6, `BandStackingTest` 5, `VolumeScaleTest` 4, `TaktPlanTest` 4,
  `WatchSettingsModeTest` 3, `CoefficientSmoothingTest` 3, `WatchStatsProtocolTest` 3,
  `NoiseSpectrumTest` 2, `RingingTest` 2, `WatchStatsStoreTest` 4, `EffectChainBenchmarkTest` 1.
  `NoiseWavTest`
  omitted with the deleted debug noise tools.
* **DSP benchmark:** **11-14 ns/sample**, worst buffer **< 0.1 ms** (6 bands, 48 kHz,
  Stereo, 3 barrels; last run 11.054 ns / 0.097 ms) → below promises (15 ns / 1 ms).The benchmark is host load dependent; It can with LLM/Gradle processes running in parallel
  tear out and then be measured again individually.
* **Preset sync real (11/13/2026, 10:44 p.m.):** Watch “Request profiles” → Phone sends
  `/filter/profiles/PN-…` (urgent) → Watch `PhoneFilterReceiver` reports
  “Presets adopted from Pixel 10: [Preset 1, Preset 2, Preset 3]” → Phone gets `/filter/ack`.
* **Music sync real (11/13/2026, 10:32 p.m., 10:46 p.m. and 11:13 p.m.):**
  - Manifest + ACK within seconds; uploads per asset; `Embedded.mp3` (264 695 B) and
    `Online.mp3` (141 289 B) will then be in the Watch music folder; Phone reports
    “Music mirrored: 2 tracks.”
  - Commit confirmed; `MusicTransfer` logs 0 or 1 deleted songs.
  - Final build (23:13): Manifest with 2 titles, 0 missing, commit 0 deleted.
  - The test files were removed from the phone and the watch after the device was removed.
* **Music mirror deletion real (11/13/2026, 10:38 p.m.):** Only `Embedded.mp3` selected → Manifest with
  1 track, 0 missing → Commit deletes `Online.mp3` on the watch (`1 unselected track deleted`).
* **Abort real (11/13/2026, 11:07 p.m.):** 101 title selection started, after the manifest ACK
  "Cancel" → Watch gets `/sync/cancel`, reports "Sync canceled; 0 half files
  cleaned up”; the existing SyncTest files remain unchanged. The deletion in the middle
  File upload (`.part` cleanup) is not yet logged.
* **UI completion (11/14/2026):** Settings lines were initially only as the last element
  a `TransformingLazyColumn` item; each line is now its own item. players and
  Settings were tested at 466 × 466 with no content trimming; the technical path and the
  Production `Log.i` outputs are removed.
* **API 33:** the real TicWatch runs Wear OS 4 / API 33 - not API 34; all device tests apply
  for API 33.
* **Lint:** 0 errors; Deliberately set `tools:ignore` positions remain documented.
* **App runs autonomously:** local playback, EQ and library work without a phone; the sync is
  optional and aborts without pairing with understandable status.

## Decisions that are no longer up for debate

1. **Phone is master and editor.** Three presets are edited only on the phone; the watch shows
   Selection and active state.
2. **Music is a mirror with a commit.** The watch will not delete until the commit has been confirmed
   selected titles; canceling doesn't delete anything.
3. **Shared model as a copy.** Watch and Phone share the `EqPreset`/`SyncProtocol` class
   as a source code copy, secured by common JSON fixtures.
4. **No silent losses.** Invalid data is rejected with error ACK; old presets remain.
5. **Data layer instead of your own Bluetooth stack.** The choice of Bluetooth/WLAN lies with Wear OS.
6. **DataItems are `urgent`.** Normal writes wait for an idle/load window; that looks like one
   Time out. `setUrgent()` is mandatory for sync.
7. **Assets are addressed via the Play Services digest.** The watch reads `audio_digest`
   (Base64-SHA-1) and calls `getFdForAsset` with it; the technical integrity check remains SHA-256.
8. **Same `applicationId`.** Data Layer addresses packets via name + signature; both APKs
   are therefore called `de.notchedmusic.phone`, the watch namespace remains `de.notchedmusic.watch`.
9. **Receivers work in the app-wide scope and synchronously in the callback.** This keeps the process runningalive during manifest/upload/commit, even if GMS unbinds shortly afterwards.
10. **Reception only when the app is open.** `MusicTransferReceiver` and `PhoneFilterReceiver` are in
    manifest `enabled="false"`; `SyncReception` (state machine `Receive Window`) switches it on,
    when an activity becomes visible, and 90 s after the last activity gone *and* no data
    more arrived and went out again. When the process starts, “off” always applies. The ability
    `de.notchedmusic.watch.filter_profiles` is logged in or logged out at the same moments, that
    Phone only sees the watch as accessible if it can really listen.
    **Deliberate consequence:** Preset boost and music mirroring from the phone require the watch app to be open;
    An ongoing upload can still leave the watch app, the incoming files keep it
    Reception open. “Request profiles” and the status on the watch work unchanged.
11. **Crown** stays on list scrolling and on game page volume; Pressing is
    System behavior.

## Sync Protocol (v3)

| Direction | path | Purpose |
|---|---|---|
| Phone → Watch | DataItem `/filter/profiles/<id>` | three presets + metadata |
| Watch → Phone | Message `/filter/ack` | Confirm receipt/error |
| Watch → Phone | Message `/filter/request` | request current presets |
| Phone → Watch | DataItem `/music/manifest/<id>` | Target list with size + SHA-256 |
| Watch → Phone | Message `/music/manifest/ack` | missing/present indices |
| Phone → Watch | DataItem `/music/upload/<id>` + Asset | an audio file |
| Phone → Watch | Message `/music/keepalive` | keeps the eavesdropper open during a mirroring |
| Watch → Phone | Message `/music/ack` | File ACK/Error |
| Phone → Watch | Message `/music/commit` | Share mirror |
| Watch → Phone | Message `/music/commit/ack` | Confirm deletion |
| Phone ↔ Watch | Message `/sync/cancel` | Cancel run |

### Reception only when the app is open (11/14/2026)

Both eavesdroppers are deactivated in the manifest (`android:enabled="false"`) so that no data layer
Broadcast wakes up the watch process when no one is using the app. `SyncReception` also reports the
Ability of the watch on or off.

Device check (TicWatch Pro 5, release APK, November 14, 2026 8:56 p.m. - 9:11 p.m.):

| situation | `cmd package query-services` on `wear://…/music/upload` |
|---|---|
| App closed (after installation and after restarting the app) | no hit – receiver to |
| App open (Library/Settings) | Hit – both receivers on |
| App closed but sync running | remains on because every input pushes the deadline back |
| 100 s after `HOME` without data | closed again, `Wake Locks: size=0` |
| New release (11/14, 10:47 p.m.): App for | no hit – not even for `wear://…/music/keepalive` |
| New release (11/14, 10:47 p.m.): App open | 11 hits, also for `wear://…/music/keepalive` – the sign of life falls into the registered prefix range |

To do this: “Request profiles” on the watch → Phone responds → Show settings
“Presets: 11/14 9:03 p.m.,” and the music mirror ran in the same window with the watch app closed
continue (52 → 63 titles).

### Process of a transfer (as of November 14, 2026)

1. **Open Watch app** (Library, Players or Settings – whichever page). This is what the watch reports
   its capability and the phone will display “watch connected”.
2. Start “SYNC EQs” or “SYNC MUSIC” on the phone.3. **Then the watch app can be closed and the display off.** Every incoming package receipt holds the
   Eavesdropper open: 90 s normal; While a mirror is running there is a fallback period of 30 minutes
   the phone resets every 2 minutes with `/music/keepalive`. After commit or cancellation it applies again
   the short period of time - the devices are allowed to sleep again immediately after the transmission.
4. If the watch app is closed, the phone reports “No watch found – open the app on the watch.” Preset boost
   and music mirrors then need to open the watch app; the sync buttons on the phone are in
   deactivated in this state. “Request profiles” from the watch always works as long as your app
   is open.

### Adapt display to the screen (11/14/2026)

* **Display watch** (`WatchPlan`): bright and visible 250 ms, dimmed 5,000 ms, not invisible at all.
  This is reported by the activity lifecycle observation (ON_START/ON_STOP) and the ambient
  Game site message. Previously, the app always asked at 4 Hz - even when dimmed or off.
  switched display.
* **Display button with three levels:** `AmbientDisplay` NARROW (default) → FULL → OFF. The old one
  boolean value `ambientDimmed` remains saved and is included as a mirror; old JSON without
  new field is migrated (on → NARROW, off → FULL). Device test: button shows one after the other
  “Dimmed: narrow / full / narrow” (cyclic).
* **Level "off" on the real watch:** after the timeout, `mLastPausedActivity` is our Activity and
  `visibleRequested=false` → the app has been paused, the dial takes over the dimmed display.
  At level "narrow" the activity remains in the ambient **resumed** (`ResumedActivity: …MainActivity`)
  and draws the collapsed view every 5 seconds.
* **Deadline during a mirror:** `Receivewindow.dataIncoming(trailing)` uses
  `NACHLAUF_SYNC_MS = 30 min` as long as there is a pending manifest (`SyncReceipt.sessionStart`
  / `sessionEnd`). This is a **silence fallback period**, not a size timeout: the phone sends
  every 2 minutes (`SyncFristen.HALTEZEIT_MS`) a sign of life on `/music/keepalive`, and the
  The watch's message intent filter covers exactly this `/music` prefix. The phone stops (finished,
  aborted, dead), the fallback period remains - after commit/abort sets `sessionEnd`
  immediately back to 90 s. On the phone side there is therefore **no more file timeout**, which is a
  could truncate large file; The transport waits there until there is a response and just runs afterward
  30 minutes of complete silence before the relapse period.
* **No locking of the display or sleep:** neither the watch nor the phone stops for the transfer
  Wake lock or `keepScreenOn`. The data layer also transmits in Doze; a full one was measured
  Mirror (101 tracks, 479 MB) with the watch app closed and the display turned off.

### Large title at the 40 MB limit (11/14/2026, 11:26 p.m.)

New, previously never transmitted title `SyncTest - 40 MB Limit.mp3` with **41,900,000 bytes
(39.96 MiB)** – just below the allowed limit. Watch app closed 13 s after starting, both
Devices are asleep during this time.

| Timing | observation |
|---|---|
| 23:26:13 | “SYNC MUSIC” started, eavesdropper open (11 hits) |
| 23:26:26 | Watch app closed (HOME) |
| 23:27:58 | Pixel `dozing`, watch `asleep`, display off – running continues |
| 23:28 – 23:30 | Eavesdropper remains open for the entire ~5 minutes (session deadline + signs of life) || ≈ 23:30:30 | File arrived on the watch: **41,900,000 bytes**, i.e. ≈ 4.7 min for a file (~145 kB/s) |
| 23:32:37 | Eavesdropper again **0** – 90 s deadline after commit, no `.part` leftovers |
| afterwards | `mWakefulness=Asleep`, `UID u0a34: INACTIVE count=0`, watch shows “punk_rock / 102 tracks” |

So a single title at the limit takes **significantly longer than the old 300s file deadline** –
with the old timeout, this run would have ended up on the verge of failure; without
He runs through the size timeout and with a 30-minute fallback.

**Mirror deletion with large file (23:43:22):** after deleting the test title on the phone
another mirror - 63 s later the watch showed **101 files**, the 39.96 MiB file was gone, none
`.part` or `SyncTest` leftovers in music folder; ~90 s after the commit eavesdropper 0 again.

**Side finding regarding throughput:** the same four files transferred once on the same day
were back in **79 s** (34 MB). The data layer stores asset content in a content-addressed manner,
Repeated broadcasts are therefore almost free. Always use new file contents for time measurements.

### Last track resume (11/19/2026, Emulator 454 × 454)

* `WatchSettings` format version 3 stores `lastTrackPath`; the path is via the
  Playback observation recorded with every title change (operation and auto-advance).
* `FilterSettings.ready` signals that the saved status has been read from the disk –
  the resumption waits for this and only runs once per app start.
* After the restart, the album is searched for the path, the album list including the title index without play
  set and the Now Playing page opens directly. If a session is still running, it applies
  current title instead of the saved one.
* Acceptance of emulator: “Album A / Tone 220” selected, app terminated via force-stop and restarted
  started – Now-Playing showed “tone 220” again, **paused**; three restarts in a row
  gave the same result.
* Acceptance of TicWatch Pro 5: `punk_rock / you can't stop me` selected, ended with `force-stop` and
  restarted - Now-Playing reopened "you can't stop me", **paused**.

### EQ statistics on the watch (11/20/2026, watch emulator and real devices)

* **Counting rule:** only `isPlaying == true` **and** `activePresetId != null`; Count bypass or pause
  not. The active segment becomes active during title changes, pause/stop, preset selection and end of service
  closed and written to `watch_stats_journal.jsonl` (no 60 s interval).
* **Persistence:** When opening the statistics page or before the sync, the journal is saved
  `watch_stats.json` compacted; `journalCompactedThroughMs` prevents duplicate days after one
  Crash. Report and ACK run via `/stats/request` and `/stats/ack`; the phone demands it
  Report on, the watch responds with `/stats/report`.
* **Deletion:** The watch only removes the values of its snapshot after `STATUS_APPLIED` and
  retains newer segments. A missing ACK leaves the stable report up for retry
  status; the phone deduplicates via `lastWatchReportId`.
* **Check:** There was a closed 5 second segment in the watch emulator after app launch/playback
  found in the journal. On the real devices (TicWatch Pro 5 → Pixel 10) a segment of
  51,645 s recorded in the journal, taken over by the phone and included
  `lastWatchReportId` saved; after the APPLIED ACK, `watch_stats.json` on the watch was empty(`days={}`, `pending=null`), the journal remains. A segment that started afterwards (7.4 s) remained recorded as a new journal entry; deleting the confirmed snapshot does not discard newer data.

Capabilities:

* Watch: `de.notchedmusic.watch.filter_profiles`
* Phone: `de.notchedmusic.phone.sync`

Limits: Manifest ≤ 1 MiB, File ≤ 40 MB, JSON ≤ 8 KiB, music only under the app music folder and
only supported audio endings.

## Open points / not yet built

* **Instrumented compose tests** (EQ selection, players, settings) missing; the
  Device checking was done manually via adb/uiautomator.
* **10-file run**, **Abort mid-upload**, **Storage shortage** and **Invalid preset**
  are intended in the code but not yet logged as real E2E cases. However, the following have been completed:
  a title at the 40 MB limit (≈ 4.7 min, see above) and the mirror deletion of exactly this title.
* **30 minute A2DP playback** and **OLED pixel measurement on real watch** are pending.
* **Store signing and Proguard** are not necessary; the personal release is with the local one
  Debug key signed. The round **app icon** is still open.
* **Progress sheet and mode button** are implemented on the compact game page; the preset selection
  is also on the EQ side. There is no separate “Go to album” button in favor of it
  Wear back gesture.

## Next step

1. Process the open E2E cases from `PLAN.md` 7.3, first large upload, abortion and
   invalid preset.
2. Archive screenshots/log excerpts of the sync pages and the three EQ states.
3. Add instrumented UI tests for the watch pages.
4. Then the remaining device measurements (OLED, battery, A2DP) and the app icon.