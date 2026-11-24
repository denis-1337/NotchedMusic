# NotchedMusic – Joint implementation plan Phone ↔ TicWatch Pro 5

**As of:** November 13, 2026  
**Projects:**  
- Phone: `Z:\android_projekte\NotchedMusic\NotchedMusic-Phone` (`de.notchedmusic.phone`)  
- Watch: `Z:\android_projects\NotchedMusic\NotchedMusic-Watch` (`de.notchedmusic.watch`)  
**Target devices:** Google Pixel 10 (Android 17 / API 37) and Mobvoi TicWatch Pro 5 (466 × 466, round, OLED)  
**Implementation status:** November 13th, 2026 - Phases 1-5 implemented and on Pixel 10 ↔ TicWatch
TicWatch TicWatch Pro 5 accepted for preset and music sync; Phase 6 (full E2E matrix,
UI tests, OLED/battery) is partially open. Details: Section 12 and the
`PROJECT_STATUS.md` files of both projects.  
**Revision:** November 13th, 2026 - Decisions regarding music mirror, watch scope and app reset incorporated.

---

## 0. Short version

The phone app is functionally ready and approved on the emulator. The only missing
Feature block is the real communication with the watch; A mock is currently running there.
The Watch app is also built and tested, but lags in the data model and the
Sync protocol afterwards: It knows **two presets (A/B)** and **four filter types**, the phone app
manages **three presets (P1/P2/P3)** and **seven filter types** (including `OFF`, `LOW_CUT`,
`HIGH_CUT`).

This plan brings both projects to the same level, implements true sync across the
**Wear OS Data Layer** (Bluetooth/Wi-Fi, no dedicated Bluetooth stack) and optimizes the
Watch interface for the TicWatch Pro 5. Testing is carried out on both real devices
existing emulators and with JVM/UI tests.

### Verified initial status (11/13/2026)

| check | Result |
|---|---|
| Phone Build | `BUILD SUCCESSFUL` |
| Phone Tests | **68 / 68 green** (`:core` 55, `:app` 13), 0 errors |
| Watch Build | `BUILD SUCCESSFUL` |
| Watch tests | **108 / 108 green**, 0 errors |
| Pixel 10 real | Android 17 / API 37, 1080×2424, 47GB free |
| TicWatch Pro 5 real | Android 13 / **API 33** (not 34!), 466 × 466, 20 GB memory, 18 GB free |
| Wear data layer | **already works**: Capability `de.notchedmusic.watch.filter_profiles` came on November 13th. at 16:42 on the pixel on |
| Coupling | both devices with `supermuckl@gmail.com`, Wear Companion active on the Pixel, watch paired via Bluetooth |
| Watch app installed | yes, since November 13th. 16:41 (version 1.0) |
| Phone app installed | yes, including debug build |

> **Important:** The real watch reports **API 33** (Wear OS 4). The previous watch status speaks of
> Wear OS 5 / API 34. The real API 33 applies for testing and compatibility; `compileSdk/targetSdk 37`
> and `minSdk 33` remain unchanged.

---

## 1. Goal and Definition of Done

After completion:

1. **Preset Sync:** The phone app sends all three presets including names, IDs, pre-gain,
   six bands, filter types and 'enabled' states to the watch. The watch takes over completely,
   validates it and confirms receipt.
2. **Music Sync is a mirror:** The Phone library is master. The watch contains one
   successful music sync **exactly** the selected tracks; not selected or on the phone
   Titles that no longer exist will be deleted from the watch. The watch plays the files autonomously.
3. **Three presets on the watch:** P1, P2, P3 are selectable; The bypass remains “off”. It always will
   exactly one preset or bypass is effective, never a stacking. The presets are edited
   exclusively on the phone.4. **Seven filter types:** The watch calculates `OFF`, `NOTCH`, `PEAKING` (Bell), `LOW_SHELF`,
   `HIGH_SHELF`, `LOW_CUT` and `HIGH_CUT` with the same parameters and validation limits as the
   Phone app.
5. **No silent data loss:** Invalid packets are logged and rejected
   half taken over. The watch reports success or an understandable reason for the error.
6. **Reliable Sync:** Progress, Cancel, Timeout, Retry, Space Check and
   Retry after connection loss are implemented and tested.
7. **Watch GUI:** The watch interface is on 466 × 466, round shape, OLED and operation with one
   Hand optimized. It shows music without covers and only offers the EQ as “on/off” plus preset selection.
   It meets the OLED budget (≤ 15% bright pixels) and is approved on the real device.
8. **Test Acceptance:** All JVM tests, UI tests and the end-to-end test on Pixel 10 ↔ TicWatch Pro 5
   are green. The measurements and screenshots are documented.

**Not part of this project:** New phone GUI construction (the existing interface has been removed),
Audio streaming from phone to watch, own Bluetooth audio stack, release signing/store,
Cloud sync, preset editing and album art on the watch.

---

## 2. The gaps in the current status

| Area | Current status | Target status | Affected |
|---|---|---|---|
| Sync Transport Phone | `MockSyncTransport`; sends nothing | real wear data layer transport | `sync/*`, `AppGraph`, Manifest |
| Capability Phone | Phone reports no capability | `de.notchedmusic.phone.sync` for the watch request | `NotchedMusicApp`, `PhoneLink` |
| Preset data model Watch | 2 slots A/B, own `FilterPreset` | 3 presets p1/p2/p3, common `EqPreset` format | `dsp/*`, `playback/*` |
| Filter Types Watch | 4 types | 7 types including OFF/Lowcut/Highcut | `Biquad`, `EffectChain`, UI |
| Preset Receiver Watch | reads `FilterProfiles` (2 slots) | reads `PresetSyncPayload` v3 (3 presets) | `PhoneFilterReceiver` |
| Music Sync Phone | only simulates | Asset upload from SAF, progress, cancellation | `sync/*` |
| Music sync semantics | planned additively/overwriting | Mirror: selected titles present, delete everything else on the watch | `SyncRepository`, `MusicTransferReceiver` |
| Music Receiver Watch | writes files, but without ACK/reserve check | atomic, checking, ACK, memory space checking, refresh signal | `MusicTransferReceiver` |
| Album mapping | `track.album` is often the ID3 album name or `null` | Watch folder structure: Root = loose, direct subfolder = Album | `LibraryTrack`, `MusicScanner`, `SyncRepository` |
| Watch status | shows “last” from old slot model | real connection/sync status | `SettingsScreen`, `PhoneLink` |
| Watch GUI | functional, but A/B layout and unnecessary deep editing | EQ on/off, 3 presets, music without cover, clear game page | `ui/*` |
| Documentation | Watch status says 86 tests and 2 presets | As of 108 tests, 3 presets, sync | `PROJECT_STATUS.md`, `plan.md` |

---

## 3. Architecture

### 3.1 Data flow

```mermaid
flowchart LR
  subgraph Phone["Pixel 10"]
    PUI["Phone-UI\nPlayer · EQ · Setup · Sync"]
    PCORE[":core\nEqPreset · FilterBand · SyncProtocol"]
    PST["SettingsRepository\nDataStore"]
    PTR["WearSyncTransport\nDataClient / MessageClient"]
    PSVC["WearableListenerService\nACK · Request"]
    PUI --> PCORE
    PUI --> PST
    PUI --> PTR
    PTR --> PCORE
    PSVC --> PTR
  end

  subgraph Wear["TicWatch Pro 5"]
    WUI["Watch UI\nAlbums · Player · Presets · Settings"]
    WSET["FilterSettings\nDataStore"]
    WRCV["PhoneFilterReceiver\nMusicTransferReceiver"]
    WDSP["EffectChain\nBiquad · Limiter"]
    WPLAY["PlaybackService\nExoPlayer + MediaSession"]
    WUI --> WSET
    WRCV --> WSET
    WSET --> WDSP
    WUI --> WPLAY
    WDSP --> WPLAY
  end

  PTR -- "/filter/profiles/<id>\nDataItem + JSON" --> WRCV
  WRCV -- "/filter/ack\nMessage + JSON" --> PSVC
  PTR -- "/music/upload/<id>\nDataItem + Asset" --> WRCV
  WRCV -- "/music/ack\nMessage + JSON" --> PSVC
  WRCV -- "/filter/request\nMessage" --> PSVC
  PSVC -- "/filter/profiles/<id>\nDataItem + JSON" --> WRCV
  WRCV -- "/sync/cancel\nMessage" --> PTR
  PTR -- "/sync/cancel\nMessage" --> WRCV
```

The data layer chooses Bluetooth or Wi-Fi itself; the app does not build its own Bluetooth stack.
This is the path intended by Wear OS and is already proven to work between the two
real devices.

### 3.2 A common preset format

**Decision D1 (made): Copy.** This is purely a code organization question, not one
Functional question. The Watch app gets a copy of the Phone Core classes in its own project:- `EqPreset`, `FilterBand`, `FilterType`, `DefaultPresets`, `SyncProtocol`, `PresetSyncPayload`,
  `MusicUpload`, `SyncAck`, `MusicManifest`
- `Biquad`, `Limiter`, `Pcm`, `EqChain`

This means that both projects remain separate and can be built independently (no cross-project path, no
`includeBuild` dependency). The copy is made by **common JSON fixtures and
Compatibility tests** secured in both projects. When the phone core classes change,
The watch copy must be consciously updated and the fixture test adjusted.

**Not selected:** A common Gradle module `notchedmusic-core` outside of both apps. This
would be structurally cleaner, but would require a rebuild of both builds and a new one
Project structure. That's not needed here.

### 3.3 Watch state model

Instead of `FilterProfiles` with `slotA`/`slotB`, the watch will save in the future:

```kotlin
@Serializable
data class WatchSettings(
  val version: Int = 2,
  val presets: List<EqPreset> = DefaultPresets.three(), // genau p1, p2, p3
  val activePresetId: String? = DefaultPresets.PRESET_1_ID, // null = Bypass
  val volumeDb: Float = 0f,                              // Hörpegel, nie vom Phone gesetzt
  val keepScreenOn: Boolean = false,
  val ambientDimmed: Boolean = true,
  val lastPresetUpdateMs: Long = 0L,
  val lastMusicUpdateMs: Long = 0L,
  val lastSyncError: String? = null,
)
```

- Exactly three presets; Surplus ones are cut off, missing ones with the standard recipes
  filled up.
- `activePresetId = null` means **Bypass**. There is no separate `eqBypass` switch on the
  watch; selecting “Off” is the bypass.
- `volumeDb` remains the exclusive property of the watch. A preset sync never changes the
  Listening level.
- The Phone app sends its three presets and `activePresetId`; the watch takes over by default
  **the presets, but not the active selection**. The watch user's active selection remains.
- Migration: The previous status with A/B is read at the first start and mapped to p1/p2/p3
  (A → p1, B → p2, p3 = default); Volume and display switches remain.

### 3.4 Capabilities and Directions

| Device | Capability | Purpose |
|---|---|---|
| Watch | `de.notchedmusic.watch.filter_profiles` | Phone finds the watch |
| Phone | `de.notchedmusic.phone.sync` | Watch finds the phone |
| Watch | Request `/filter/request` | “Give me the current preset status” |
| Phone | DataItem `/filter/profiles/<id>` | Send presets |
| Watch | Message `/filter/ack` | Confirm receipt / report error |
| Phone | DataItem `/music/manifest/<id>` | Send target list for the music mirror |
| Watch | Message `/music/manifest/ack` | Confirm Manifest / Report Missing Files |
| Phone | DataItem `/music/upload/<id>` | Send music |
| Watch | Message `/music/ack` | Confirm file / Report error |
| Phone | Message `/music/commit` | Share Mirror: Delete unselected songs |
| Watch | Message `/music/commit/ack` | Confirm deletion |
| Phone ↔ Watch | Message `/sync/cancel` | Cancel ongoing sync |

The previous watch logic `PhoneLink.demandProfileAn` queries the **own** capability and can
therefore come to nothing. This will be corrected: the watch will ask in the future
`de.notchedmusic.phone.sync`, the phone registers this capability in `Application.onCreate`.

### 3.5 Music and album mapping

The watch has a flat album structure:

- Files directly in the music root → Album `(without album)`
- Files in a direct subfolder → Album = folder name
- Files in deeper subfolders → belong to the direct subfolder (album)

The Phone app delivers SAF titles recursively. So that the sync creates the same structure, gets
`LibraryTrack` a new field `watchAlbum: String?`:

- `null` for files directly in the root,
- Folder name of the direct root subfolder for all files under it.

`MusicUpload.album` will be populated from this. The ID3 album metadata (`track.album`) is **not**used as a storage folder; they are only there for cover/display – and on the watch there will be in the future
no covers.

File extensions on both sides are standardized:
`mp3, m4a, aac, ogg, oga, opus, flac, wav`.

**Mirror rule:** After a successful commit, the watch's music folder contains exactly the files of the
manifestos. Anything that is not in the manifesto will be deleted – only within the manifesto
App music folder and only music files. The tick on the root means “everything”; individual
Subfolders can also be selected. The effective selection is the union of the
Hook. If root is selected, all subfolders are considered included; the phone UI then deactivates
their hooks so that there is no ambiguity.

---

## 4. Sync protocol (v3 payload + sync envelope)

### 4.1 Presets

DataItem path (unique by `createWithAutoAppendedId`):

```text
/filter/profiles/<auto-id>
```

DataMap key:

| Key | Type | Content |
|---|---|---|
| `profiles_json` | String | `PresetSyncPayload` v3, max. 8 KiB |
| `sync_id` | String | UUID of run |
| `sent_at_ms` | Long | Timestamp Phone |
| `phone_name` | String | e.g. B. “Pixel 10” |
| `protocol` | Int | `3` = preset payload version |

Payload content remains the existing v3 format:

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
          "frequencyHz": 8000.0,
          "q": 8.0,
          "gainDb": -20.0,
          "enabled": true
        }
      ]
    }
  ],
  "activePresetId": "p1"
}
```

The watch validates with `SyncProtocol.validate(payload)` and replaces all three presets. It keeps
`volumeDb`, the display switch and its active selection. Excess ribbons are cut off,
invalid values clamped to the boundaries.

### 4.2 ACK

The watch responds via `MessageClient.sendMessage` to the node from `dataItem.uri.host`:

```json
{
  "protocol": 3,
  "syncId": "…",
  "kind": "presets",
  "status": "APPLIED",
  "appliedAtMs": 1763050000000,
  "presetCount": 3,
  "watchVersion": "2.0",
  "error": null
}
```

Error status: `REJECTED` with `error` = `invalid_payload`, `too_large`, `unsupported_version`,
`internal_error`. The phone app displays the error text clearly and does not repeat the run
blind.

### 4.3 Music (Mirror Sync)

The music sync runs in three steps: **Plan → Transfer → Commit**.

#### Step 1: Manifest

DataItem path:

```text
/music/manifest/<auto-id>
```

DataMap:

| Key | Type | Content |
|---|---|---|
| `manifest_json` | String | Target list, max. 1 MiB |
| `session_id` | String | Sync Run |
| `sent_at_ms` | Long | Timestamp Phone |

Manifest entry:

```json
{
  "album": "Album A",
  "fileName": "01 - Morgen.mp3",
  "sizeBytes": 5242880,
  "sha256": "…"
}
```

The watch checks the manifest, saves it as **pending** (no deletion yet!) and responds
`/music/manifest/ack` with `status`, `missingCount`, optional actual list and error reason. Contains
the ACK is the actual list with size/SHA, the phone skips identical files; missing it, will
all selected files uploaded. The commit is only allowed if every manifest file
either uploaded or confirmed as identical.

#### Step 2: File upload

DataItem path:

```text
/music/upload/<auto-id>
```

DataMap:

| Key | Type | Content |
|---|---|---|
| `audio` | Asset | Audio file, `Asset.createFromFd(...)` |
| `session_id` | String | Sync Run |
| `index` | Int | 0-based |
| 'totally' | Int | Number of files |
| `file_name` | String | File name including extension |
| `album` | String? | Watch album; `null` = `(without album)` |
| `mime_type` | String? | actual MIME type |
| `size_bytes` | Long | File size |
| `sha256` | String? | Checksum |

The watch writes each file atomically (`.part` → rename), checks size/SHA and confirms with
`/music/ack`. The Phone transport sends the files one after the other and waits for the ACK.

#### Step 3: Commit and Delete

Only when **all** manifest files are successfully confirmed will the phone send
`/music/commit` with `session_id`. The watch:1. checks that all manifest files exist,
2. deletes all music files in the music folder that are **not** in the manifest,
3. removes album folders that have become empty,
4. sends `/music/commit/ack` with `deletedCount` and status,
5. Reloads the library via the refresh signal.

**Important:** Nothing is deleted before the commit. If the transfer breaks off, the old one remains
Inventory completely preserved. This is the safe implementation of the mirror.

#### Deletion rules

- Deleted **only** within the app music folder and **only** music files.
- Files outside the music folder, `.part` files and app data will not be touched.
- If Phone and Watch have the same album/file name, the file will be uploaded
  replaced; the SHA check decides whether a new upload is necessary.
- Maximum manifest size: 1 MiB (approx. 2,000 titles). Larger selections come with clear messaging
  rejected; the watch library is limited to 2,000 files anyway.

### 4.4 Abort, timeout, retry

- Preset ACK deadline: **60 s**, 2 attempts (small packet, short deadline is right here).
- Music: **no size timeout per file.** A file up to 40 MB needs to be connected via the Bluetooth bridge
  five to six minutes; a timeout measured by this would cut off healthy transmissions.
  We wait until the watch responds; **Silence fallback deadlines** are manifest/file
  **30 min** and commit **10 min** (max. 3 file attempts). From the manifesto to the commit – that is
  “during a mirroring” – the phone sends `/music/keepalive` every **2 min** so that the
  watch-side eavesdropper remains open; after commit or abort, the watch immediately sets to the short one
  90 s deadline back.
- **No display and no wake lock hold** on both sides for the duration of the transfer - the
  Data Layer also transmits in the Doze, and a statusing OLED would be the only real price.
- `cancel()` sends `/sync/cancel` with `session_id`; **nothing is deleted before the commit**,
  Uploads that have already been completed are left behind and `.part` files are cleaned up on the watch.
- After connection loss: visible error, “Try again”; no endless loop.
- The phone transport checks the available watch via the capability before each run; the
  Sync buttons in the UI are only active when the watch is accessible (i.e. only start when both apps
  are open). Afterwards, only the phone has to remain open.

### 4.5 Robustness and Limits

- Incoming presets: maximum 8 KiB JSON, ignore unknown fields, never upward exception.
- Incoming music: maximum 40 MB per file (first roll); larger files become clearer
  Report rejected. Chunking/Resume is a later expansion.
- Storage space on the watch: min. `file size + 100 MB` free before writing; otherwise
  `REJECTED: insufficient_space`.
- Filenames and album names are defused (`..`, `/`, `\`, leading periods).
- Duplicates: same album/file name overwrites atomically. After the commit, all will not be in the
  Manifest music files deleted (mirror).
- Manifest: max. 1 MiB; the watch validates album/file names and free space before
  Commit.
- DataItems are deleted from the phone after a successful ACK; orphaned items are on the watch
  removed after a TTL/cleanup run.

---

## 5. Implementation phases

### Phase 0 – Baseline, Reset, Test Framework

**Goal:** reproducible starting point, no data loss.- [ ] Backup current debug APKs of both projects; Document `adb` serials and device information.
- [ ] Watch app can be uninstalled, overwritten and reset at any time; none
  User data backup required (confirmed by user).
- [ ] Reset test: delete watch app data, restart, check clean first boot.
- [ ] Set test music: `testdata/` on PC, 5 WAV + 2 MP3; on the watch initially empty.
- [ ] Create test script structure under `eq_mediaplayer/tools/` (Install, Logcat, Screenshot,
  pixel budget).
- [ ] Build both projects fresh and record the test numbers from XML.

**Acceptance:** both builds green, devices connected, no open ADB errors.

### Phase 1 – Align preset model and protocol

**Goal:** Watch and Phone speak exactly the same JSON.

- [ ] Copy Phone `:core` models and DSP classes to Watch:
  - `EqPreset`, `FilterBand`, `FilterType`, `DefaultPresets`, `SyncProtocol`,
    `PresetSyncPayload`, `SyncAck`, `MusicUpload`
  - `Biquad`, `Limiter`, `Pcm`, `EqChain`
- [ ] Adjust package names/imports in the watch project; no phone dependency in the watch build.
- [ ] Replace Watch-`FilterPreset`/`FilterProfiles` with `EqPreset`/`WatchSettings`.
- [ ] Write migration code for the old A/B version (`legacy/LegacyFilterProfiles.kt`).
- [ ] Store common JSON fixtures and test them in both projects:
  - 3 presets × 6 bands, all 7 types, `enabled=false`, limits.
  - invalid JSON, too large, unknown fields, missing bands.
- [ ] Add `SyncAck`/`SyncCancel` as `@Serializable` DTOs.

**Acceptance:** Both sides encode and decode the same fixtures identically; Watch build green;
Migration round trip tested.

### Phase 2 – Watch: DSP, status and receiver on 3 presets

**Goal:** The watch calculates and saves the phone status completely, even without a phone.

- [ ] Expand `FilterType` with `OFF`, `LOW_CUT`, `HIGH_CUT`.
- [ ] Add the three missing RBJ formulas to `Biquad`; `OFF` allows the signal to pass.
- [ ] change `EffectChain` to `EqPreset` + `bypass`; Bypass skips filters and
  Pre-gain, but not volume and limiter.
- [ ] `EffectAudioProcessor`: `@Volatile preset: EqPreset`, `@Volatile bypass: Boolean`.
- [ ] `PlaybackService` reads preset, bypass and volume from `WatchSettings`.
- [ ] `FilterSettings` stores `WatchSettings`; JSON version 2 with migration.
- [ ] `PhoneFilterReceiver`:
  - reads `PresetSyncPayload` instead of `FilterProfiles`,
  - validated, adopts all three presets,
  - retains volume/display/active selection,
  - sets `lastPresetUpdateMs`,
  - sends `/filter/ack`.
- [ ] `PhoneLink`:
  - new phone capability,
  - `/filter/request` to the phone,
  - Status/Error condition for settings.
- [ ] Add message listener and `POST_NOTIFICATIONS`/`WAKE_LOCK` to watch manifest (check,
  what API 33 really needs).

**Acceptance:** Watch plays with 3 presets and 7 types; a Phone JSON is sent via a phone app without a phone app
Test Intent/DataItem adopted and displayed correctly; DSP benchmark still below 15 ns/sample.

### Phase 3 – Phone: true sync transport and service

**Goal:** The mock is replaced by real data layer communication.

- [ ] `play-services-wearable` in phone build and version catalog.
- [ ] `WearSyncTransport` behind `SyncTransport`:
  - Capability search,
  - `PutDataRequest.createWithAutoAppendedId`,
  - `Asset.createFromFd` for music,
  - ACK correlation via `SyncAckBus`,
  - Timeouts and retry,- `cancel`/`/sync/cancel`.
- [ ] `WearSyncListenerService` in Phone manifest:
  - `/filter/ack`, `/music/ack`,
  - `/filter/request` (provides the current preset status),
  - `/sync/cancel`.
- [ ] `AppGraph`: Wire `SyncAckBus`, `WearSyncTransport`, `WearSyncListenerService`.
- [ ] `NotchedMusicApp`: Register phone capability `de.notchedmusic.phone.sync`.
- [ ] `SyncRepository`: real error/status messages, `mockBannerVisible` only in the debug mock.
- [ ] `SyncScreen`: “Search watch”, “3 presets”, “Waiting for confirmation”, real progress,
  Error text, “Try again”.
- [ ] `MusicUpload` creation from `watchAlbum` instead of ID3 album.

**Decrease:** Preset sync from Pixel to TicWatch appears on the watch, ACK comes back, the
Phone UI reports success; without a watch an understandable error appears.

### Phase 4 – Music Mirror End-to-End

**Goal:** After syncing, the watch will contain exactly the music selected on the phone
(no more, no less), without cover.

- [ ] Set `LibraryTrack.watchAlbum` in the scanner.
- [ ] selection rule: root = everything; subfolder = single; effective quantity = union.
- [ ] `SyncRepository.syncMusic` builds the manifest and upload list; deduplicated over
  `documentId`; uses album mapping.
- [ ] New manifest DTO `MusicManifest`/`MusicManifestEntry` in core; Size limit 1 MiB.
- [ ] Before sending: file exists, readable, size ≤ 40 MB, extension/MIME supported.
- [ ] `WearSyncTransport`: Send manifest, wait for ACKs, only missing/changed files
  upload (otherwise all), send commit only after complete confirmation.
- [ ] `MusicTransferReceiver`:
  - Check manifest and save pending (do not delete yet),
  - path prefix `/music/upload`,
  - `.part` writing, `fsync`, atomic renaming,
  - disk space check,
  - SHA check,
  - ACK per file,
  - Commit: delete music files not in the manifest, remove empty albums,
  - Cleaning up orphaned `.part` files,
  - Refresh signal for the album list.
- [ ] Expand Watch-`MusicLibrary` with `oga`/`opus`.
- [ ] Phone UI: Progress per file and bytes, cancellation, deletion warning and confirmation
  (“Unselected tracks will be deleted from the watch”), per file error.
- [ ] Watch UI: Music list without cover.
- [ ] DataItem cleanup after ACK.

**Acceptance:** 1 file, 10 files and one large file are transferred; Albums appear on the
watch; Titles that are not selected will only be deleted after the upload has been completed; Abort leaves the
old inventory untouched; Repetition is idempotent; no covers are visible on the watch.

### Phase 5 – Watch GUI for the TicWatch Pro 5

**Goal:** The rough watch GUI will be optimized for 466×466, round shape and OLED; the watch is
intentionally easier than the phone.

- [ ] Navigation:
  - Albums (Start),
  - title,
  - player,
  - EQ (only on/off and P1/P2/P3),
  - Settings.
- [ ] **No preset editor, no band detail, no preset import on the watch.** The presets are
  edited exclusively on the phone and adopted via sync.
- [ ] Music without covers: album/track lists only with name and number/track; no cover areas,
  no cover loading.
- [ ] Player: Crown = Volume, Mode Button P1 → P2 → P3 → Off, Progress Sheet,
  Track/folder jump by holding; no misleading search interaction.
- [ ] Settings: real phone status, last sync, “Request profiles”, library path,
  “Reload”, save info. No preset import.- [ ] Design: dark at night, large targets (≥ 48 dp), no continuous animations, ambient version
  narrow, app icon round.
- [ ] Screenshot regression and OLED pixel budget per screen.

**Removal:** Can be operated with one hand on the real 466 × 466 device; all touch targets hit;
≤ 15% bright pixels; no cut-off text or scroll traps; no covers; no editor.

### Phase 6 – test and acceptance run

**Goal:** prove everything, nothing is certain.

- [ ] All JVM tests of both projects.
- [ ] New Watch UI tests (Compose/Instrumentation).
- [ ] End-to-End on Pixel 10 ↔ TicWatch Pro 5.
- [ ] End-to-end on emulators (Pixel_10_API_37, Wear_OS_Large_Round).
- [ ] Error and borderline cases (see test matrix).
- [ ] Measurements: DSP, memory, battery, OLED, transmission time.
- [ ] Document screenshots and log excerpts.

**Acceptance:** Acceptance matrix green; open points documented.

### Phase 7 – Documentation, Status, Artifacts

- [ ] `PROJECT_STATUS.md` of the watch to update (108 tests are already reality; 3 presets,
  Sync, API 33).
- [ ] `PROJECT_STATUS.md` of the phone to add real sync and the watch test.
- [ ] `README.md` of both projects: sync instructions, protocol paths, troubleshooting.
- [ ] Archive debug APKs and screenshots.
- [ ] No TODO leftovers, KDoc in German, Lint check.

---

## 6. Watch UI concept (specific)

### 6.1 Pages and States

| Page | Content | Crown |
|---|---|---|
| **Albums** | List of albums, number of tracks, active album highlighted; empty state with sync message; **no covers** | scrolls |
| **Title** | Title of the album, current title marked; **no covers** | scrolls |
| **Player** | Title, album, progress sheet, large transport buttons, volume, preset mode | Volume |
| **EQ** | only “Off” and P1/P2/P3; active state; **no editing** | scrolls |
| **Settings** | Phone status, last sync, request, library, display, storage | scrolls |

### 6.2 Interactions

- **Turn crown:** scroll on lists, on the game page volume in 1 dB increments.
- **Press crown:** Do not reassign system behavior (back).
- **Swipe from edge:** back, same as Wear standard.
- **Tap:** large areas only; no grids with small targets.
- **Hold on ⏮/⏭:** entire folder back/forward.
- **Mode Button:** P1 → P2 → P3 → Off → P1; always exactly one state.
- **No gears, no editors:** The watch only displays presets; Processing takes place on the phone.

### 6.3 Visual guardrails

- 466 × 466, round; Content in a safe circle, generous margins.
- Night scheme, no large bright areas; Accent for active state only.
- OLED: ≤ 15% bright pixels; Measurement via screenshot on the real device.
- No animation that runs continuously; Progress sheet only on playback.
- Text at least 11-12 sp, targets at least 48 dp; German texts.
- Status never just about color: active entry also has text/icon.

---

## 7. Test concept

### 7.1 JVM testing

**Phone (`:core` / `:app`)**

| test | Checks |
|---|---|
| `SyncProtocolTest` | v3 round trip, 3 presets, 6 bands, unknown fields, too large, invalid |
| `SyncAckTest` | ACK/Cancel encode/decode, error status |
| `MusicManifestTest` | Build manifest, size limit, deletion schedule, album allocation |
| `WearSyncTransportTest` | Fake `WearGateway`: Capability, Timeout, Retry, Cancel, Manifest, File Stream |
| `SyncRepositoryTest` | Progress, success, error, abort, `lastSyncMs` || `LibraryModelTest` | `watchAlbum` mapping root/subfolder/deep |
| `SettingsCodecTest` | existing settings remain unchanged |

**Watch (JVM)**

| test | Checks |
|---|---|
| `PresetCompatibilityTest` | Phone-JSON → Watch Model → Phone-JSON |
| `WatchSettingsMigrationTest` | A/B legacy → 3 presets, volume/display preserved |
| `BiquadTypeTest` | all 7 types: frequency response, OFF, lowcut/highcut |
| `EffectChainTest` | 6 bands, bypass, no bang, limiter |
| `EffectChainBenchmarkTest` | < 15 ns/sample, worst buffer < 1 ms |
| `MusicTransferLogicTest` | Sanitizing, `.part`, size, SHA, space, ACK |
| `MusicMirrorTest` | manifest → pending → commit; Delete only after complete upload |
| `MusicDeletionTest` | only delete music files in the music folder; empty albums; no app data |
| `AlbumMappingTest` | Root = loose, subfolder = album, deep = parent album |
| `PhoneLinkTest` | Capability selection, request, error |
| `WatchUiStateTest` | P1→P2→P3→Off, exactly one active preset |

### 7.2 UI/Instrumentation Tests (Watch)

- EQ page: Tapping P1/P2/P3/Off changes state visibly; there are **none**
  Editor navigation.
- Player: Mode button toggles, volume responds to rotary events.
- Settings: Phone status without phone remains understandable; “Request profiles” triggers a request.
- Albums/tracks: empty state, selection, playback state; **no covers** are shown or
  loaded.

### 7.3 End-to-end matrix (real devices)

| ID | test | expectation |
|---|---|---|
| E2E-01 | Capability | Pixel finds TicWatch, TicWatch finds Phone |
| E2E-02 | Preset sync P1/P2/P3 | all three values, names, bands, types arrive |
| E2E-03 | Invalid preset | Watch refuses, phone shows errors, old presets remain |
| E2E-04 | Music 1 file | File appears in the correct album, playable, without cover |
| E2E-05 | Music 10 files | Progress correct, all files complete |
| E2E-06 | Large file/border | clear error above limit; below successful |
| E2E-07 | Abort | no half file, **old inventory remains complete**, UI reports cancellation |
| E2E-08 | Watch not reachable | understandable note, no blind retry |
| E2E-09 | App restarts | Presets, music, timestamps are retained |
| E2E-10 | Overwrite | same album/file name is cleanly replaced |
| E2E-11 | Memory low | `insufficient_space`, not a broken file |
| E2E-12 | Watch playback | 30 min with A2DP headphones, background, screen off |
| E2E-13 | Watch GUI | all pages on 466 × 466, crown, touch, no truncated text |
| E2E-14 | OLED/battery | Pixel budget ≤ 15%, battery consumption within |
| E2E-15 | Music Mirror | Titles that are not selected are deleted after a successful commit, while those that are selected remain |
| E2E-16 | No covers | Watch shows no covers; Phone-Sync does not transfer Cover |

### 7.4 Emulator Tests

- `Pixel_10_API_37`: Phone UI, mock/transport fake, preset generation.
- `Wear_OS_Large_Round` (API 33): Watch UI, local playback, receiver logic, migration.
- The data layer between two emulators is not reliable; real communication is on the
  tested on real devices.

### 7.5 Measurement budgets

| Area | Budget | Measurement |
|---|---|---|
| Watch DSP | 6 bands stereo 48 kHz < 15 ns/sample, worst buffer < 1 ms | Benchmark |
| Watch memory | Heap stable at 30 min playback | `dumpsys meminfo` || Watch OLED | ≤ 15% bright pixels per screen | Screenshot/Python |
| Watch transmission | 10MB file in reasonable time, no timeout | Sync Log |
| Phone Sync | each run ends with ACK or clear error | E2E |
| CPU | Audio path remains under 1% of a core | Benchmark/`cpuinfo` |

### 7.6 Commands (Reference)

```bat
:: Phone
cd Z:\android_projekte\NotchedMusic\NotchedMusic-Phone
gradlew.bat :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug

:: Watch
cd Z:\android_projekte\NotchedMusic\NotchedMusic-Watch
gradlew.bat app:testDebugUnitTest app:assembleDebug app:lintDebug

:: Geräte
adb devices -l
adb -s <phone> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <watch> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <watch> shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity
adb -s <phone> shell am start -n de.notchedmusic.phone/.MainActivity

:: Logs
adb -s <phone> logcat -s WearableService WearSync
adb -s <watch> logcat -s WearableService MusicTransfer PhoneFilterReceiver
```

---

## 8. Risks and countermeasures

| Risk | Impact | Countermeasure |
|---|---|---|
| Data layer needs coupling/play services | Sync doesn't work | check capability; UI with pairing notice; Test on real devices |
| Wear OS API 33 instead of 34 | Features that require API 34 are missing | API 33 testing; minSdk 33; no Wear OS 5 special cases |
| Large music files | Data layer boundaries, memory pressure | 40MB limit, FD asset, space check, chunking later |
| Transmission aborts | half files, hanging UI | `.part` + atomic rename, ACK/Timeout/Retry, cleanup run |
| Incorrect album assignment | Tracks end up in the wrong album | `watchAlbum` in the scanner, own album mapping test |
| Two data states (UI/Receiver) | Presets jump or are lost | a source `FilterSettings`, atomic updates, migration |
| Data loss during watch update | User data gone | Migration code, test with old JSON; App reset is allowed according to the user |
| Accidental deletion on mirror | Music gone | Deletion only after complete upload/commit; manifesto review; Warning/confirmation in phone; only music folder |
| Watch playback and phone session | competing media sessions | separate sessions, test on both devices |
| OLED/UI too bright | Battery, acceptance missed | dark scheme, pixel budget, screenshot regression |
| Emulator ≠ real device | UI/Performance not representative | real devices must be accepted |

---

## 9. Confirmed decisions (11/13/2026)

1. **Core organization: copy (hit).** This is just a code question, not a feature:
   The watch gets a copy of the phone core classes in its own project. Both apps remain
   can be built separately; common JSON fixtures ensure compatibility. Not a common one
   Gradle module.
2. **Smartphone is master; Music is mirrored (hit).** Selected tracks are
   transferred/overwritten. Songs that are not selected or no longer exist on the phone
   are deleted from the watch - only after a completely successful upload and commit.
3. **Active preset (hit).** The watch retains its active selection after a preset sync
   their bypass state. The three presets are transferred, not the selection.
4. **Watch scope (hit).** EQ on/off and preset selection only; music without covers; Folder/track selection,
   Play/Pause and Volume. Preset editing, band editor and preset import are available on the
   watch not; the phone remains the editor.
5. **App reset (hit).** The watch app can be deleted, overwritten and recreated at any time
   be installed. No backup/restore concept necessary; Tests start cleanly.
6. **Phone GUI (hit).** Only the sync page and necessary status indicators are adjusted;
   the rest of the existing phone GUI remains unchanged.

---

## 10. Acceptance checklist

- [ ] Phone sends 3 presets, watch accepts them completely and confirms.
- [ ] Watch shows P1/P2/P3/Off; exactly one state is effective; no preset editing.
- [ ] All 7 filter types are mathematically and audibly correct on the watch.- [ ] Music sync mirrors: selected tracks available, unselected ones deleted after commit,
  Playback autonomously, no covers.
- [ ] Abort, retry, memory shortage and invalid packages are tested.
  - [x] Title at the 40 MB limit was actually mirrored and deleted again (≈ 4.7 min for
    a file, November 14, 2026). By design, there is **no size timeout**, but rather fallback periods for
    Silence (30 min) plus signs of life every 2 min; Transfer only starts when both apps are open.
- [ ] Migration from the old A/B version works.
- [ ] Watch UI decreased to 466 × 466; OLED ≤ 15%; Crown/touch works; no editor,
  no covers.
- [ ] All JVM/UI tests green; Builds and Lint without errors.
- [ ] Real E2E matrix green; Logs/screenshots documented.
- [ ] `PROJECT_STATUS.md`, `PROJECT_STATUS.md`, READMEs updated.

---

## 11. Next step

After release:

1. Start **Phase 0**: device check, reset test, test fixtures.
2. Implement **Phase 1**: mirror the phone core status into the watch project and the
   Compatibility tests get green on both sides.
3. Then continue working strictly in the order of the phases; each phase ends with build and
   Test proof, only then the next one.

The existing phone app will remain unchanged until phase 3; the mock is not removed prematurely,
so that the removed surface remains usable.


---

## 12. Implementation status (11/13/2026)

### Implemented and verified

* **Model/Protocol:** Watch uses `EqPreset`, seven filter types, three presets and that
  v3-`SyncProtocol`; Migration from the old A/B DataStore.
* **Phone transport:** `WearSyncTransport` + `WearGateway` replace the mock; capability search,
  urgent DataItems, FD assets, ACK correlation, manifest, commit, cancel.
* **Watch receivers:** `PhoneFilterReceiver`, `MusicTransferReceiver`, `PhoneLink`,
  `WatchApplication` with Capability; Music mirror with `.part`, SHA-256, memory check and
  Deletion only after confirmed commit. Both eavesdroppers are `enabled="false"` and in the manifest
  are used by `SyncReception` (state machine `reception window`) only when the app is open or when
  incoming data open - 90 s delay, then closed; The capability logs in/out.
* **UI:** Navigation 3 with Albums/Tracks/Players/EQ/Settings; EQ only Off/P1/P2/P3; Music without
  covers; Status and “Request Profiles”.
* **Package identity:** Watch-`applicationId` is `de.notchedmusic.phone` (namespace remains
  `de.notchedmusic.watch`); without this alignment, the data layer cannot find the counterpart.
* **Real device acceptance:** Presets P1/P2/P3 transferred and confirmed; two MP3s (396 KB)
  mirrored; unselected title deleted after commit.
* **Builds/Tests:** Phone 83 unit tests (66 `:core`, 17 `:app`), Watch 139 unit tests, both builds
  and Lints green.

### Proven technical details

* DataItems **`setUrgent()`** - otherwise the data layer will wait for an idle/loading window.
* Address assets via the Play Services digest (`audio_digest`, Base64-SHA-1); more professional
  Integrity check remains SHA-256.
* Read asset metadata only after transfer; an immediate `DataMapItem.fromDataItem` can
  Provide `ASSET_UNAVAILABLE`.
* Run watch receiver synchronously in callback and app-wide scope.
* Disable data layer eavesdropper on the watch by default (`enabled="false"`) and only at
  Switch on the app or data input when it is open - otherwise every broadcast wakes up the watch process.Register/deregister the capability at the same moments so that the phone sees the watch realistically.

### Still open (Phase 6/7)

* Complete E2E matrix: 10 files, large file, abort mid-upload, memory shortage,
  invalid preset.
* Instrumented Compose/UI testing on the watch.
* 30 minute A2DP test and OLED/battery measurement on the TicWatch.
* Store preparation not necessary; personal release signed and installed; round app icon open.