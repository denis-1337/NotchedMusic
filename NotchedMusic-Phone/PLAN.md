# NotchedMusic Phone – Implementation plan

> **Status: Planning document, retained as historical design record.**  
> Created from `requirements1.txt`, the four GUI mockups and the existing
> Reference project `Z:\android_projects\NotchedMusic\NotchedMusic-Watch` (Wear app).
>
> **Project boundary: The existing watch project will not be changed in this project.**
> The watch app will be added later - in a separate step outside of this project
> three presets and the new synchronization protocol adapted. Until then it stays
> Phone app during a **mock synchronization**; there are no files in the Wear project
> edited, built or restructured.

| | |
|---|---|
| Project folder | `Z:\android_projects\NotchedMusic\NotchedMusic-Phone` |
| Mockups | `Z:\android_projects\NotchedMusic\eq_mediaplayer\Screenshot *.png` |
| Target device | Google Pixel 10, Android 17 (API 37) |
| App package | `de.notchedmusic.phone` |
| Project name | `NotchedMusicPhone` |
| language | Kotlin, Compose, Material 3 |
| Playback | Media3/ExoPlayer + own DSP `AudioProcessor` |
| Library | Storage Access Framework (SAF), no `MANAGE_EXTERNAL_STORAGE` |
| Synchronization | Wear Data Layer, now behind a mock transport |

---

## 0. Environment check and virtual device setup

### 0.1 Environment (as of after setup)

| check | Result | Review |
|---|---|---|
| Android Studio | `AI-262.10968.63` (2026.2.2), JBR 25 | available |
| Android SDK | `C:\Users\super\AppData\Local\Android\Sdk` | available |
| SDK Platform | `android-37.0` (Android 17, API 37) | **available** |
| Build tools | `36.0.0`, `36.1.0` | available |
| Platform tools / adb | `37.0.1` | available |
| Emulator | `37.2.13.0` | available |
| Hardware acceleration | WHPX recognized and usable | available |
| SDK Command-line Tools | `cmdline-tools/latest` (23.0 / Android CLI 1.1.16717390) | **installed** |
| System image API 37 | `system-images;android-37.0;google_apis;x86_64` (approx. 5.3 GB) | **installed** |
| AVD Pixel 10 | `Pixel_10_API_37`, 1080×2424, 420 dpi (422 ppi), Pixel 10 skin, 4 GB RAM | **created and booted** |
| More AVDs | `Medium_Phone_API_36.1`, `Wear_OS_Large_Round` | existing unchanged |
| Gradle | no global `gradle`, but wrapper distribution 9.6.0 in cache | Wrapper is enough |
| JDK | `JAVA_HOME` = Zulu 21; Studio JBR 25 | Gradle 9.6 is running, already verified |
| Gradle cache | AGP 9.5.1, Kotlin 2.3.20, Compose BOM 2026.09.00, Media3 1.11.1 | good starting position |
| Internet | reachable | Downloads possible |
| Free memory | about 535 GB on C: | sufficient |

### 0.2 Setup result

Done:

1. **Android SDK Command-line Tools** installed:
   `C:\Users\super\AppData\Local\Android\Sdk\cmdline-tools\latest`.
   The modern CLI is called `android.exe`; `sdkmanager.bat` and
   `avdmanager.bat` are also present.
2. **System image API 37** installed:
   `system-images;android-37.0;google_apis;x86_64`.
3. **AVD “Pixel 10 / Android 17”** created:
   `Pixel_10_API_37`, 1080×2424, 420 dpi (422 ppi), Pixel 10 skin,
   4GB RAM, 2GB SD card,
   10GB data partition. The emulator boots successfully; `adb` reports
   `Android 17 / SDK 37`.

### 0.3 Do I need your help?

**The SDK setup is done; there is no longer any GUI help for development
necessary.** Only the later test on real hardware remains open:
**Bluetooth headphones cannot be tested in real life in the emulator.** A2DP and the
Stability with real headsets will have to be on the Pixel 10 / a real one laterdevice to be checked; in the emulator we test playback, UI, SAF, DSP and
Change of state.

### 0.4 Commands for Setup (Reference)

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

Note: `avdmanager.bat` with the package path works reliably from a
batch file; the nested quoting form directly from the shell can
Cut package path.

AVD values:

| Setting | Value |
|---|---|
| Device | Pixel 10 |
| System | Android 17 / API 37, x86_64 |
| Resolution/Density | 1080×2424 / 420 dpi (422 ppi) |
| Device frame / skin | `pixel_10` |
| RAM | 4096MB |
| Heap | 512MB |
| Data partition | 10GB |
| SD card | 2GB |
| GPU | automatically (WHPX is active) |
| Network | to (for cover query) |

---

## 1. Goal and boundaries

The app is a **tinnitus retraining player**: music is played via Bluetooth
Played through headphones and processed with a close-meshed notch filter.
The tinnitus frequency and filters must be precisely adjustable; the
However, everyday operation must also work for users who are not technically savvy.

**In scope:**

- Play music from a user-selected folder.
- Folder/title navigation with forward/back.
- Three local EQ presets with six filters each.
- Filter types: off, notch, bell, highshelf, lowshelf, lowcut, highcut.
- Preamp/Trim, volume, soft limiter, clipping display.
- Album art from file metadata, otherwise online fallback.
- Sine tone for tinnitus frequency determination.
- Setup page with folder setting and “Send to Watch”.
- Synchronization page with music and EQ sync as a **mockup**.
- German interface, dark design according to the mockups.

**Not within the scope of this project:**

- Implementation of the watch app.
- **Any change to the existing watch project** `AndroidStudioProjects/NotchedMusic`
  (Package, DSP, Receiver, Manifest, Build Files). The adjustment of the watch to three
  Presets and the new protocol will be done separately later.
- Real wear data layer protocol in the first expansion stage.
- Streaming services, podcasts, videos, cloud music.
- Multi-user management, advertising, analytics.
- Sound “improvement” at all costs: Be the focus
  Computing effort, stability, battery and clean notch/limiter behavior.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  Compose UI                                                          │
│  Player · EQ · Setup · Sync                                    │
└───────────────┬─────────────────────────────────────────────────────┘
                │ StateFlow / ViewModel
┌───────────────▼─────────────────────────────────────────────────────┐
│  Repositories / State                                               │
│  MusicRepository · SettingsRepository · CoverRepository              │
│  SyncRepository (Mock) · PermissionManager                          │
└───────┬───────────────────────┬───────────────────────┬────────────┘
        │                       │                       │
┌───────▼────────┐     ┌────────▼────────┐     ┌───────▼──────────┐
│ :core (pure)   │     │ Playback backend │     │ Persistence        │
│ DSP, models,  │     │ Media3-ExoPlayer │     │ DataStore/JSON    │
│ Protocol DTO  │     │ AudioProcessor   │     │ SAF index (RAM)   │
└───────┬────────┘     │ MediaSession     │     └───────┬──────────┘
        │              └────────┬─────────┘             │
        │                       │                       │
        └───────────────┬───────┴───────────────────────┘
                        │
                 AudioTrack → System → A2DP → Bluetooth headphones
```

### 2.1 Modules

| Module | Content | Dependencies |
|---|---|---|
| `:core` | pure Kotlin/JVM library: DSP, filter/preset models, sync DTOs, validation | no Android dependencies |
| `:app` | Android App: UI, Playback, SAF, Cover, Sync Mock | `:core`, AndroidX, Media3 |

` :core` is deliberately Android-free so that DSP tests can run on the PC and the
The same calculation logic can later be adopted by the watch app.

### 2.2 Package structure in `:app`

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

## 3. Requirements → Implementation → Acceptance

| Requirement | Implementation | Acceptance criterion |
|---|---|---|
| Define root folder | SAF folder selection in setup, persistable URI permission | After restarting, the folder remains intact, including read permission
| Root + subfolder selectable | Only root and **direct** subfolders are navigation items | Forward/Back reaches each of these levels; deeper levels do not appear as separate folders |
| Title recursively from the selected folder | Scanner collects all audio files under the selected node, no matter how deep | Root contains all titles; Subfolder contains all titles of its subtree || Title selection forward/back | title line with arrows; no search slider | Arrows change titles cyclically; Play starts the current title |
| Play/Stop | MediaSessionService + ExoPlayer | Play/Stop with hardware, headphone and UI operation |
| Album art automatically | embedded cover first; otherwise online fallback; Cache | Cover appears without UI blockage; offline remains placeholder |
| MP3 prioritized, common formats | ExoPlayer decoder + file extension filter | MP3, M4A/AAC, OGG/Opus, FLAC, WAV are recognized and played |
| Just music | File extension and MIME filter, no video | Unknown formats are skipped, not as an error dialog |
| Volume system/app | own app gain **and** system volume | App controller changes audibly, system controller also changes; no clamping |
| EQ bypass (off by default) | `BYPASS` mode: Filter and PreGain are skipped | Bypass leaves the sound unchanged; Player volume and limiter remain active |
| Preset dropdown at bottom right | Choice of three presets | Selection takes effect immediately and is saved |
| Three presets, six filters each | `EqPreset` with a maximum of six `FilterBand`s | Exactly three presets; sixth filter remains; seventh is cut off |
| Select filter (blue frame) | Selection in the band editor | Selection shows blue frame; only the selected volume will be edited |
| Filter on/off (green) | `enabled` switch in the selected band | Green = active; off = visually different; Sound changes instantly |
| Filter types including off | `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`, `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT` | Each type is selectable and results in a stable filter |
| Freq/Q/Gain/Volume via keyboard | `OutlinedTextField` with decimal point/point and units | Values ​​are validated, clamped and applied immediately |
| Volume as Trim/Preamp | `preGainDb` per preset, before the filter chain | Reduces level before filters increase it; is skipped in the bypass |
| Soft limiter against clipping | stereo-linked limiter, ceiling −1 dBFS, before 16-bit conversion | exit does not exceed the ceiling; no audible cracking |
| Clipping display on the player | Peak measurement before the limiter, badge on the volume slider | Display lights up when limiting without adjusting the controller |
| Setup: Send to Watch → Sync | Navigation to the Synchronize page | Button opens the sync page |
| Root Folder Display | readable path/URI name in Setup | If access is revoked, an understandable message will appear |
| Sine tone for frequency determination | own 'AudioTrack' generator, 10 Hz slider, ±1 Hz buttons | Frequency and volume are accurately displayed and audible |
| Sync page: root + subfolder | Checkbox list with root and direct child folders | Selection shows number of titles/estimated size |
| Sync Music | Mock transport, later data layer asset upload | Progress/Status appears; real transfer clearly marked as “later” |
| Sync EQs | Mock transport, later `profiles_json` | All three presets including filters are transferred as DTO |
| Check/request rights automatically | SAF permission + targeted runtime permissions | No start abort due to missing rights; Notice with action |
| Technically inexperienced users | big targets, clear German texts, safe defaults | A layman can select and play music without understanding the EQ side |
| Performance | pure DSP chain, no allocation per buffer, slow scan in the background | For measured values ​​see section 13.6 |

---

## 4. Data model

### 4.1 Filters```kotlin
enum class FilterType {
  OFF,          // Band is not processed
  NOTCH,        // complete cancellation at frequencyHz
  PEAKING,      // Bell
  LOW_SHELF,
  HIGH_SHELF,
  LOW_CUT,      // high-pass
  HIGH_CUT      // low-pass
}

data class FilterBand(
  val type: FilterType = FilterType.OFF,
  val frequencyHz: Double,
  val q: Double,
  val gainDb: Double = 0.0,
  val enabled: Boolean = true,
)
```

- `gainDb` only works on peak/shelf. For notch, lowcut and highcut the
  Value ignored, but saved so that a type change does not lose anything.
- `enabled = false` corresponds to `OFF` in terms of sound, but the band remains
  Edit received.
- All six bands are always displayed, even if some are off.

### 4.2 Preset

```kotlin
data class EqPreset(
  val id: String,             // stabil: "p1", "p2", "p3"
  val name: String,           // "Preset 1" ...
  val preGainDb: Double = 0.0, // Trim/preamp before the filters
  val bands: List<FilterBand> = List(6) { FilterBand(...) },
)
```

- Three presets are saved as an ordered list. That is the binding one
  target structure; Preset 3 is not an optional appendage.
- There is **always exactly one preset active**, never a stack.
- The watch app will later be customized to these three presets separately. The Phone Project
  always sends all three presets in the sync DTO and does not wait for a legacy format.
- `preGainDb` is part of the sound recipe and later becomes part of the watch sync
  transferred.
- The player volume control is **not** part of the preset. He belongs to that
  User and is never adjusted by a sync.

### 4.3 Limits and Validation

| Value | Area | Justification |
|---|---|---|
| Frequency | 20 Hz to `min(20 000 Hz, 0.45 × SampleRate)` | near Nyquist becomes unreliable |
| Q | 0.1 to 30 | A notch smeared above it audibly |
| Gain per band | ±24 dB | enough reserve for notch and shelves |
| Preamp(`preGainDb`) | ±12dB | Clipping prevention and level adjustment |
| Player volume | −60 to 0 dB | 0 dB = full; System also remains behind |
| Bands per preset | 6 | Requirement |
| Presets | 3 | Requirement |
| Total increase | max. +12 dB | prevents too much boost before the limiter |

Validation runs **once when changing a preset**, not per sample.
Invalid values ​​are clamped and declared in the UI; the audio chain never throws
because of a user input.

### 4.4 Settings

```kotlin
data class AppSettings(
  val rootUri: String? = null,
  val presets: List<EqPreset> = DefaultPresets.three(),
  val activePresetId: String? = "p1",
  val eqBypass: Boolean = true,          // Requirement: off by default
  val playerVolumeDb: Float = 0f,
  val repeatMode: RepeatMode = RepeatMode.ALL,
  val autoAdvance: Boolean = true,
  val sineFrequencyHz: Int = 440,
  val sineVolumeDb: Float = -20f,
  val lastSyncMs: Long = 0L,
)
```

Standard presets:

- **Preset 1:** empty, neutral initial state; Notch 8 kHz as
  helpful starting point, all other bands `OFF`.
- **Preset 2:** wider notch (e.g. 6 kHz, Q 4, −20 dB).
- **Preset 3:** empty so that the user can build their own recipe.

`eqBypass = true` is intentional: the player should be on the first start
“just play music”. Only when you need the notch do you activate the EQ.

---

## 5. DSP and audio chain

### 5.1 Signal path

```
File → ExoPlayer decoder → float PCM
   → preset preGain (only when EQ is active)
   → 6 biquads per channel (only active bands)
   → player volume
   → soft limiter (ceiling −1 dBFS)
   → 16-bit conversion
   → AudioTrack → system volume → A2DP → headphones
```

With `BYPASS`, PreGain and Biquads are omitted; **Player volume and limiter
remain active**. So the volume remains constant when switching and the
Output cannot override.

### 5.2 Biquad

- RBJ Cookbook, **Transposed Direct Form II**.
- Coefficients in `Double`, state in `Float`.
- Five multiplications and four additions per sample and channel; for everyone
  Six types of filters cost the same.
- Coefficient changes are faded over approx. 128 samples, so that
  No cracking when changing presets/controllers.
- Sample rate comes from actual audio output; at 44.1/48/96 kHz
  the coefficients are recalculated. A rate change is a
  New construction, not an ongoing mixed case.

Formulas (RBJ):

| Type | Coefficients |
|---|---|
| Notch | `b0 = 1`, `b1 = −2 cos ω₀`, `b2 = 1`, `a0 = 1 + α`, `a1 = −2 cos ω₀`, `a2 = 1 − α` |
| Peaking/Belling | with `A = 10^(gain/40)`: `b0 = 1 + αA`, `b1 = −2 cos ω₀`, `b2 = 1 − αA`, `a0 = 1 + α/A`, `a1 = −2 cos ω₀`, `a2 = 1 − α/A` |
| LowShelf / HighShelf | Standard RBJ shelf with `α = sin ω₀ / √2` or Q |
| LowCut | High pass: `b0 = (1+cos ω₀)/2`, `b1 = −(1+cos ω₀)`, `b2 = (1+cos ω₀)/2`, `a0 = 1+α`, `a1 = −2 cos ω₀`, `a2 = 1−α` || HighCut | Low pass: `b0 = (1−cos ω₀)/2`, `b1 = 1−cos ω₀`, `b2 = (1−cos ω₀)/2`, `a0 = 1+α`, `a1 = −2 cos ω₀`, `a2 = 1−α` |

For lowcut/highcut, `Q` is used as edge quality; Standard 0.707.
Gain is ignored.

### 5.3 Soft limiter

- Stereo-linked: **one** gain for both channels, so that
  Stereo image does not collapse in loud places.
- Ceiling **−1 dBFS**; There is room underneath for codec/rounding residues.
- Attack immediately, recovery exponentially over about 50 ms.
- Additional hard lid only as a final rounding protection.
- Deliberately no look-ahead in the first version: no latency build-up, none
  complex state machine. If hearing tests show transient pressure, may
  a 1 ms lookahead can be added later; this is its own
  Measurement decision, not a requirement.
- The limiter is **not** a replacement for useful presets. The total increase
  is already limited when saving.

### 5.4 Clipping display

- The sample peak is measured before the limiter.
- Conditions:
  - **OK:** Peak ≤ −6 dBFS.
  - **Yellow “Limiter”:** Limiter intervenes > 0.5 dB.
  - **Red “Clip”:** Input peak before the limiter > 0 dBFS or
    Limiter gain > 3 dB.
- Display on the volume slider of the player page; Anti-flicker hysteresis.
- The app **does not** adjust the volume automatically. More optional
  One-tap suggestion “−2 dB” as in the Watch reference.

### 5.5 Media3 connection

- `MediaSessionService` owns `ExoPlayer`.
- `DefaultAudioSink.Builder(context).setAudioProcessorChain(...)` with a
  `BaseAudioProcessor` for the EQ.
- `@OptIn(UnstableApi::class)` only on the Media3 boundary.
- The audio thread reads an **immutable snapshot**
  `@Volatile` fields; no DataStore, mutex or UI accesses in
  Audio callback.
- A fixed working buffer; no object allocation per buffer.
- If there is no DSP support or an exotic decoder, the app continues unchanged playback
  unchanged playback, not a crash.

### 5.6 Formats

Priority: **MP3**. The following are also supported:

- `.mp3`
- `.m4a`, `.aac`
- `.ogg`, `.oga`, `.opus`
- `.flac`
- `.wav`

The selection is made via file extension **and** MIME type to system files and
Videos to avoid. Individual file codec errors are reported per title
and skipped over; playback stops.

---

## 6. Library, folders and titles

### 6.1 SAF instead of broad media authorization

The user chooses the music root via `ACTION_OPEN_DOCUMENT_TREE`. The app
saves the URI and calls `takePersistableUriPermission(..., READ)`.
This means:

- No `READ_MEDIA_AUDIO`, no `MANAGE_EXTERNAL_STORAGE`.
- The user decides which folder is shared.
- The permission survives restarts as long as the user does not revoke it.
- Android 17 compliant; no special treatment necessary for Android 13+.

When the app starts and every time it is used, the following is checked:

1. Is a root URI stored?
2. Is there still a persistable read permission for this?
3. Does the directory still exist?

If not: unobtrusive message with “Select folder”, no crash and
no empty player without explanation.

### 6.2 The required flat folder model

Selection points in the player and on the sync page:

```
Music root/                     ← selectable, contains everything recursively
├── Album A/                    ← selectable, contains everything recursively
│   ├── 01 - Title.mp3
│   └── CD 2/                   ← NOT selectable
│       └── 02 - Title.mp3      ← belongs to Album A
├── Album B/                    ← selectable
└── locker.mp3                  ← belongs to the music root
```

- **Root** and **any direct subfolder** are navigation points.
- Deeper subfolders are not separate points.
- The files of a point are collected **recursively**.
- Files directly in the root belong to the root point.- Empty folders do not appear as a separate entry if there are no titles in them
  have subtree; However, the point remains accessible as long as it is directly below
  the root lies.

### 6.3 Scanners

- `DocumentsContract.buildChildDocumentsUriUsingTree(...)` with
  `ContentResolver.query`, not `DocumentFile` per file: less
  Object allocation and fewer round trips.
- Traversal in the background (`Dispatchers.IO`, cancelable).
- A scan creates an immutable `LibrarySnapshot` in RAM.
- No rescanning on every UI animation; only at startup, after changing folders,
  after Sync and click “Reload”.
- Limits against fault trees: maximum depth 8, maximum 5 000 files,
  500 root nodes. Exceedance is logged and explained in the UI.
- Hidden folders (`.name`) are skipped.
- Sorting: natural name order; leading numbers
  (`01 - ...`) are recognized as a sorting index.
- Display: ID3/MediaStore title, otherwise file name without extension.

### 6.4 Title data

```kotlin
data class Track(
  val uri: Uri,
  val documentId: String,
  val displayName: String,   // filename without extension
  val title: String,         // metadata or filename
  val artist: String?,
  val album: String?,
  val durationMs: Long?,
  val fileSizeBytes: Long?,
  val mimeType: String?,
)
```

- Metadata is loaded **lazy** for the current/adjacent title
  (`MediaMetadataRetriever`), not for all 5 000 files during scanning.
- A small LRU cache holds the last tracks.
- `MediaMetadataRetriever` is always closed; Errors lead to
  Filename as a fallback.

### 6.5 Navigation in the player

- A line with “folder” and arrows left/right.
- A line with “title” and arrows left/right.
- A tap on the folder name opens a bottom sheet with all of them
  selectable nodes; this is the barrier-free alternative to many
  Arrow tips.
- Titles jump cyclically; the current title is stopped when changing and
  restarted (or remains stopped if previously stopped).
- With “Play” the current title starts; “Stop” stops.
- Auto-Advance: on by default to prevent retraining after three
  minutes ends; Can be switched off in the settings.
- No search slider, no fast forward — like in the Watch Reference and the
  Mockups.

---

## 7. Playback, Bluetooth and background

### 7.1 Service

- `MediaSessionService` with `MediaSession` and `ExoPlayer`.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Notification with cover, title, artist and transport actions.
- `setHandleAudioBecomingNoisy(true)`: Unplug/disconnect headset paused.
- Audio Focus is managed by Media3; no separate second focus.
- `WAKE_LOCK` only if Media3 actually needs it for playback.
- If the headphones are disconnected, the app remains paused; no automatic
  Continue playing through the loudspeaker (tinnitus context).

### 7.2 Bluetooth

- The app uses the default audio track; A2DP, codec and latency are at
  system. This is intentional: no own SCO/HFP, no codec manipulation.
- `AudioAttributes.USAGE_MEDIA` + `CONTENT_TYPE_MUSIC`.
- The system remembers the volume per Bluetooth device; the app rule
  acts beforehand in the DSP path.
- Recommended operation in the UI: Leave the system volume rather high and
  fine-tune with the app controller. A note in setup, not a requirement.
- Headset buttons (Play/Pause, Next/Previous) run via the MediaSession.
- The emulator cannot check a real A2DP route; that's for later
  real Pixel 10 with headphones provided.

### 7.3 Stability

- Local files: `LoadControl` set to allow short I/O peaks
  can be buffered without reporting the battery.
- For `Player.Listener.onPlayerError`: understandable message, next trackonly by user decision or with auto-advance with skipping.
- No network in playback path; Load covers asynchronously and can be canceled.
- A 10-minute endurance run in the emulator is part of the acceptance test.

---

## 8. Interface and user guidance

Design from the mockups: black background, strong blue, large
Pill buttons, white typography. This is at the same time OLED friendly and for
Easy to read for laypeople.

### 8.1 Navigation

At the top there is a three-part tab: **PLAYER · EQ · SETUP**.
The Synchronize page is a sub-goal of Setup and has one at the top
Back arrow. The tabs remain visible; this is consistent with the mockups.

### 8.2 Players

| Element | behavior |
|---|---|
| Tabs | Player/EQ/Setup |
| Folder line | name of the selected point; change arrows; Tip opens selection |
| Cover | square, embedded or online cover, otherwise placeholder |
| Play/Stop | large round target, feel, content description |
| Title line | Title, Artist/Filename; Arrows change title |
| Volume | Slider 0–100%, display in %, additional dB in the EQ context |
| Clip badge | “Limiter” yellow / “Clip” red, only if relevant |
| EQ BYPASS | switch; Default active (filter off) |
| Presets | Dropdown at the bottom right: Preset 1/2/3, takes effect immediately |

The slider controls the app gain. The system volume remains unaffected and
is also available via the system keys.

### 8.3 EQ

- Three preset pills: **PRESET 1 / 2 / 3**.
- Six band chips:
  - **blue frame** = currently selected
  - **green** = active (`enabled = true`)
  - **gray/orange** = off
- A tap on a chip selects it and shows the fields.
- A “Filter active” switch in the editor sounds like “enabled”;
  the chips reflect the condition.
- Fields of the selected chip:
  - **Type/OFF:** Dropdown with all seven types.
  - **Freq:** Hz, keyboard, range display.
  - **Q:** dimensionless, explanation “wider/narrower”.
  - **Filter Gain:** dB, only effective for peak/shelf.
  - **Volume:** dB, preamp of the **preset**; in bypass without effect.
- Bottom: “Reset Preset”, “Copy from Preset” and Live Notice
  “this band is out/out of the audible range”.
- The fields accept comma and period. After leaving the field
  formatted and validated; The text remains as you type.
- The six bands are always visible, even when `OFF`.

### 8.4 Setup

| Element | behavior |
|---|---|
| Send to Watch | opens Synchronize |
| Select Root Folder | SAF picker |
| Root Folder Path | readable path; If access is missing, red with action |
| Play Sine Wave | Start/Stop the test tone |
| Freq | Slider in 10 Hz steps, display in Hz |
| + / − | 1 Hz steps, with a long press ten steps |
| Volume | Test volume in %, with warning |
| Reread music | optional rescan |
| Diagnosis | Version, last scan, sync status, audio route |

### 8.5 Synchronize

- Header “Synchronize EQ Presets and Music to Watch”.
- List:
  - Root with count/estimated size
  - any direct sublevel
- checkboxes; “Select/Deselect All”; selected titles will be
  deduplicated.
- Two large buttons: **SYNC EQs** and **SYNC MUSIC**.
- Progress display with file/preset, percent, cancel.
- Clear banner: **“Mock – Watch app will be customized separately later”**.
- After completion: timestamp and “last synced”.
- Error states: aborted, watch not accessible, file not readable;
  each with understandable text and repetition.### 8.6 Usability for technically uninformed users

- German labels, no English technical terms in the main flow.
- Large touch targets (min. 48 dp), sufficient contrast.
- Frequency/Q/Gain with units and plain text help; EQ remains an “expert site”.
- Default: EQ off, volume 0 dB, auto advance on.
- No blocking dialogs at startup; Rights are assigned at the place of use
  explained.
- Error always with an action: “Select folder”, “Try again”,
  “Check system volume”.
- Screen reader: content descriptions, roles, status announcements.
- No pure color information: off/selected/active additionally via icon
  and text.

---

## 9. Permissions (Android 17 / API 37)

### 9.1 Manifesto

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- Only needed for watch/headset APIs; not for Data Layer alone -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

- **No** `READ_MEDIA_AUDIO`, **no** `MANAGE_EXTERNAL_STORAGE`,
  **no** wide memory access.
- `.mp3` etc. are not claimed as intent filters; the app is not
  general file app.
- `MediaSessionService` with `foregroundServiceType="mediaPlayback"`.

### 9.2 Runtime check

| Permission | When | Behavior in case of rejection |
|---|---|---|
| SAF Folder Access | Start + every time you use the library | Banner, app remains usable, no empty player without explanation |
| `POST_NOTIFICATIONS` | On the first play | Playback is running, but there is no media notification |
| `BLUETOOTH_CONNECT` | Only when a watch/headset API really needs it | Sync/Status with clear error message; Core app remains usable |
| Network | Cover query | Wildcard, no error message in main flow |

Rules:

1. **First check, then request, then demote.**
2. Never ask for all rights in one go when starting the app.
3. Every rejection explains what is missing without the right.
4. If the user revokes folder access, the library will be saved as
   Marked as “not available”, not empty.
5. Android 17 peculiarities: Edge-to-edge with `WindowInsets`, Predictive Back
   Activate, create notification channel for API 33+.
6. An ongoing service may not be permitted due to a late rights decision
   crash; If in doubt, pause and bring the user back.

---

## 10. Synchronization and API

### 10.1 Now: Transport interface + mock

```kotlin
interface SyncTransport {
  suspend fun isWatchAvailable(): Boolean
  suspend fun sendPresets(payload: PresetSyncPayload): SyncResult
  fun sendMusic(items: List<MusicUpload>, onProgress: (Float) -> Unit): SyncResult
  suspend fun cancel()
}
```

- `MockSyncTransport` simulates 50-300 ms per file/preset, reports
  Progress and can cancel. No real data is written.
- UI clearly displays mock status. This is how it works now
  fully testable without inventing the later API.
- The real implementation will be added later behind the same interface,
  as soon as the watch app is separately adapted to the v3 protocol. The Watch Project
  will not be changed in this project.

### 10.2 Reference log of the existing Wear app

The Wear app `de.notchedmusic.watch` already uses:

| Purpose | Path/Key |
|---|---|
| ability | `de.notchedmusic.watch.filter_profiles` |
| Send profiles | `/filter/profiles`, `profiles_json` |
| Request profiles | `/filter/request` (empty) |
| Send music | `/music/upload`, `audio` (asset), `album`, `file_name` |

This log is **just a reference** from the existing watch project and
is not changed here. It has two presets (A/B), while the phone app
Manages three binding presets. The adjustment is a later,
separate order on the watch project.

### 10.3 New DTO “v3” (proposal, not yet committed)

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

- `version` allows later extensions.- Unknown fields are ignored, never thrown.
- Maximum of three presets, six bands each; the counterpart revalidates.
- `playerVolumeDb` and system volume are **not** sent.
- A preset is always a complete sound recipe; the watch shouldn't
  have to guess which bands are missing.

**Compatibility decision (determined):** The phone app always works
three presets and in real sync only sends the v3 package with all of them
three presets. The existing watch app can't read v3 yet; it will for it
later adapted **outside of this project**. Until then:

- There is **no** silent downsampling to `slotA`/`slotB` and none
  Data loss from Preset 3.
- The real watch sync remains deactivated or reports clearly
  “Watch app still needs to be adjusted”.
- The mock transport still shows and simulates all three presets, so
  the phone interface and the data model can be fully checked.
- The existing watch project is neither changed nor made a build dependency
  integrated. It is intended only as a reference for DSP and path conventions.

The adjustment of the watch (three slots instead of A/B, v3 receiver, tests) is a
separate follow-up order and not part of this phone project.

### 10.4 Music Sync

- One data layer item per file with asset, album name and file name.
- The existing watch receiver stores atomically via `.part`. This behavior
  is only referenced; the watch project is not changed.
- Before sending: Check existence, readability, size and ending.
- Progress: “3 / 12 files · 4.2 MB from 9.1 MB”.
- Duplicate selection is deduplicated via track URI.
- Cancel leaves files that have already been transferred; ignored the watch
  half `.part` files.
- Large files: Chunking/Resume is a later development; for the first
  One asset per file is enough. The boundaries of the data layer are shown in front of the
  real implementation measured.

### 10.5 API robustness

- Each incoming message is limited in size (presets < 8 KiB text).
- Invalid JSON is logged and discarded, never raised as an exception.
- Paths are defused (`..`, `/`, `\`), as in the existing receiver.
- The Phone app requests profiles (`/filter/request`) so that the watch can follow
  a sync immediately has the new status.
- Timestamp `lastSyncMs` and a short status line in the setup.
- Idempotence: resending the same preset does nothing other than change
  the timestamp.

---

## 11. Album cover

Order:

1. **Embedded Cover** from the file metadata
   (`MediaMetadataRetriever.embeddedPicture`). This is fast, offline
   available and covers most MP3s with ID3v2 APIC.
2. **Local Cache** (coil disk cache, key from title/album/artist
   normalized).
3. **Online fallback** only if there is no embedded cover.
   Primary MusicBrainz + Cover Art Archive (no API key, but user agent and
   Rate limit necessary), secondarily iTunes Search API as an alternative option.
4. **Wildcard** for timeout, offline or no hit.

Rules:

- No network call in audio or UI thread.
- Request timeout 5 s; maximum 1 request/s; Result is cached.
- Only track/artist/album will be sent, no file paths, none
  Listening history.
- Cover loading can be canceled when the title changes.
- Only for supported music files, not for arbitrary files.
- When the network is deactivated, the app remains fully usable.- Limit cover size to display (e.g. 512 px), not original images
  keep in memory.

---

## 12. Persistence and state

- **DataStore Preferences** for the small settings:
  - Root URI
  - three presets as JSON
  - active preset
  - EQ bypass
  - Player volume
  - Repeat/Auto Advance
  - Sine frequency/volume
  - last sync
- **One** JSON entry for all presets, so no half-written sentence
  comes from three different states.
- Writing via a **conflated channel**: fast controller movements
  only generate the latest version; an older writer never overwrites
  a newer one.
- A `StateFlow<AppSettings>` is the source of truth for the UI.
- The audio thread only reads an immutable snapshot; DataStore I/O
  never takes place there.
- Incorrect or unknown JSON versions fall back to defaults and
  are logged instead of blocking the app.
- Format version in JSON so that later migrations are possible.
- Optional: Export/import of all presets as a file is a later option
  Nice-to-have, no starting scope.

---

## 13. Test and acceptance concept

### 13.1 Pure JVM tests (`:core`)

- Biquad impulse response and notch depth at 1kHz/8kHz.
- Stability at Q = 30 and frequencies close to Nyquist.
- Lowcut/highcut edges, shelf gain, bell gain.
- Coefficient blending: no jump, no DC drift.
- Limiter: ceiling is never exceeded; stereo coupling; Release time.
- Preset validation: six bands, sum boost, clamping.
- Clip detection: thresholds and hysteresis.
- Protocol JSON: v3 read/write, unknown fields, old v2 data.
- Benchmark: 6 bands stereo 48 kHz in loop; Target < 15 ns/sample,
  no allocation in the path.

### 13.2 Android Tests (`:app`)

- SAF scanner with test tree (Mock-ContentResolver or documented
  Instrumentation): Root + direct subfolders, recursive titles,
  deeper folders not as nodes.
- Permission revocation: saved root, revoked URI → error condition.
- Settings repository: DataStore round trip, conflated writer,
  Corruption fallback.
- Cover repository: embedded in front of online, cache, offline.
- Sync mock: progress, abort, error, deduplication.
- Compose UI tests: player buttons, EQ selection/colors, sync selection.

### 13.3 Emulator test (Pixel 10, API 37)

1. Music folder via `adb push` to `/sdcard/Download/TestMusic/`.
2. Select via SAF in the app.
3. Folder/track navigation, play/stop, auto advance.
4. EQ: Change preset, activate band, change frequency while music is playing.
5. Bypass on/off; Volume; Clip display with an intentionally loud
   preset.
6. Sine tone: 10 Hz slider, ±1 Hz, stop.
7. Sync Mock: Select, Progress, Cancel.
8. App restart: Root, presets and volume are preserved.
9. Revoke permission (Files app/`adb shell appops`), check error image.
10. 10-minute endurance run: no underruns, no memory growth.

### 13.4 Real device (later)

- Pixel 10 with Android 17.
- Bluetooth headphones: pairing, A2DP, headphone buttons, disconnect/connect.
- Background playback when screen is off.
- Real library > 2 000 files, mixed formats.
- Battery consumption during 1 hour of playback.

### 13.5 Acceptance criteria per page

| Page | Acceptance |
|---|---|
| Players | A layperson can play music after the first start; all five core buttons work without explanation || EQ | Three presets, six bands, all seven types; Values ​​via keyboard; Bypass works immediately |
| Setup | Root folder remains after reboot; Sine tone can be set in 1 Hz steps |
| Sync | Choice is clear; Progress visible; Mock is clearly marked |
| Stability | 10-minute endurance run without crash/underrun; Revoked rights lead to a clean error pattern |
| Performance | DSP measurements and memory budget adhered to (13.6) |

### 13.6 Measurement budgets

| Area | Target | Measurement |
|---|---|---|
| DSP | 6 bands stereo 48 kHz < 15 ns/sample | JVM benchmark, 10 000 blocks, warmup |
| Audio | 0 underruns in the 10-minute test | ExoPlayer/AudioTrack Statistics, Log |
| Allocation | no new objects per audio buffer | Code review + benchmark |
| Library | 2,000 files < 1.5 s scan | Time measurement with test tree |
| UI | no main thread I/O > 16 ms | StrictMode in debug build |
| Cover | no UI block; Timeout 5 s | Trace |
| App launch | < 1 s to operable with cached index | Cold/warm start measurement |

---

## 14. Roadmap

| Milestone | Content | Done when |
|---|---|---|
| **M0 – Tools** | SDK CLI, API 37 system image, AVD Pixel 10, project framework, Gradle build | `assembleDebug` + `testDebugUnitTest` green; Emulator starts |
| **M1 – Core** | `:core`: Models, Validation, Biquad, Limiter, Clip Detection, DTOs | JVM tests and benchmark green |
| **M2 – Library** | SAF selection, scanner, flat folder model, settings repository, permissions | Root remains intact; recursive titles correct |
| **M3 – Playback** | ExoPlayer, MediaSessionService, DSP Processor, Volume, Limiter, Notification | Play/Stop with filter audible; background running |
| **M4 – Player UI** | Tabs, Folder/Track Navigation, Cover, Volume, Bypass, Preset | Mockup player fully operable |
| **M5 – EQ UI** | Three presets, six bands, editor, persistence, bypass | All filter types editable and audible |
| **M6 – Setup + Sine** | Root display, send-to-watch navigation, sine tone | Frequency/level precise, 10 Hz slider, ±1 Hz |
| **M7 – Sync Mock** | Checkbox list, two sync buttons, progress, cancel | Mock complete, clearly marked |
| **M8 – fine tuning** | Cover online, accessibility, error images, lint, dark theme, tests | Acceptance criteria from 13.3 met |
| **M9 – real test** | Pixel 10, Bluetooth, Real Library, Battery, Release Build | Release criteria from 13.4 met |

Order conscious: **first playable, then finely adjustable, then sync.**
The sync buttons remain visible until M7, but are marked as mock.

---

## 15. Project structure and build

### 15.1 Files (target)

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

### 15.2 versions

Based on the existing, functioning watch base (only as
Version reference, no change there) and present in cache:

| building block | Version | Remark |
|---|---|---|
| Gradle wrappers | 9.6.0 | existing distribution in cache |
| Android Gradle Plugin | 9.5.1 | available |
| Kotlin | 2.3.20 | available |
| Compose BOM | 2026.09.00 | available |
| Kotlinx Serialization | 1.12.0 | available |
| Media3 | 1.11.1 | available |
| DataStore | 1.3.0 | available |
| Play Services Wearable | 20.0.1 | for later real sync connection |
| Navigation Compose | 2.10.2 | stable; Download required |
| Coil 3 | 3.6.3 | cover cache; Download required |
| WorkManager | 2.12.0 | optional for background cover sync |
| core-ktx/Activity/Lifecycle | 1.18.0 / 1.13.0 / 2.10.0 | available |`minSdk = 33` (Android 13) as starting value: This avoids the legacy
Storage permissions and matches the watch base (minSdk 33). If the app
is intended to run on older phones, this is a deliberate later expansion.

### 15.3 Build configuration

- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 33`.
- Java/Kotlin toolchain 17.
- Compose compiler plugin over Kotlin version.
- `buildFeatures { compose = true; buildConfig = true }` (BuildConfig for
  Debug diagnostics).
- Check `org.gradle.parallel=true` first, don't turn it on blindly.
- `org.gradle.configuration-cache=true` as in the reference.
- No dynamic versions, no `latest.release`.

### 15.4 Build commands

```bat
cd Z:\android_projekte\NotchedMusic\NotchedMusic-Phone
gradlew.bat :assembleDebug
gradlew.bat :testDebugUnitTest
gradlew.bat :connectedDebugAndroidTest
```

Expectation: `BUILD SUCCESSFUL`; Warnings need to be explained, not new ones
Deprecation swamps.

---

## 16. Risks, open points and decisions

### 16.1 Risks

| Risk | Impact | Countermeasure |
|---|---|---|
| 3 presets vs. 2 presets in the existing watch app | Real sync only works after watch adjustment | v3 protocol with all three presets; up to the separate watch adjustment only mock; no change to the watch project |
| SAF scan of large trees | long waiting time, UI stuttering | Background cursor scan, limits, snapshot in RAM |
| Online cover | Data protection, rate limits, offline | embedded cover first, cache, timeout, clear opt-out option |
| True Bluetooth range | cannot be tested in the emulator | Test on Pixel 10 with headphones; Standard audio track instead of special path |
| Limiters vs. Transients | audible distortion at extreme presets | conservative sum increase, measurement, optional lookahead |
| Android 17 behavior | new permission/service rules | Build early against API 37, request permissions declaratively and lazy |
| Volume expectation | User confused system and app volume | explain both visibly; System remains untouched |
| Withdrawal of rights | App shows empty library | explicit “unavailable” state with action |
| Emulator performance | incorrect DSP readings | DSP benchmark on JVM; Emulator for integration only |

### 16.2 Open decisions (default in brackets)

1. **Watch Sync (decided):** The watch will be adjusted separately later. The phone app sends
   always three presets in v3 format; Until it is adjusted, the real one remains
   Sync deactivated and the mock active. (No more open decision.)
2. **Auto-Advance/Repeat:** on by default or only Play/Stop
   like in the mockup?
3. **Sine tone range:** 100-15,000 Hz (default) or 20-20,000 Hz?
4. **Online cover:** MusicBrainz + Cover Art Archive (default) or
   iTunes Search API?
5. **App name:** `NotchedMusic`/`NotchedMusicPhone` (default) or your own
   Product name?
6. **minSdk:** 33 (default) or lower for older devices?
7. **EQ volume:** Preamp per preset (default, as in the mockup) or additionally
   per band? The request names Volume among the filter fields; technical
   A preset preamp is the clean solution.
8. **Real Watch Sync in the first release:** Mock (default as required)
   or already data layer?
9. **System volume display:** only an indication (default) or additionally
   visible in the app?
10. **Preset export:** Starting scope or later?

### 16.3 Architectural decisions that status

- SAF instead of broad media authorization.
- Media3/ExoPlayer for music, separate 'AudioTrack' only for the sine tone.
- IIR biquads instead of FFT/linear phase.
- Exactly one active preset, no stacking.- Preamp and player volume separated; Sync never changes the listening level.
- Limiter before 16-bit conversion; Ceiling −1 dBFS.
- Three presets with six bands each; `OFF` is a filter type. This is final.
- The existing watch project remains untouched and will be separated later
  customized; As long as Phone-Sync only sends mock or clearly marked ones
  “not yet supported” status.
- Sync now mock, interface clean from the start.
- `:core` remains Android-free and reusable.

---

## 17. Immediately next steps after approval

0. **Before beginning: confirm that the watch project remains unchanged.** All
   Work takes place exclusively in `NotchedMusic-Phone`.
1. Set up SDK packages and AVD (Section 0.4).
2. Derive the project framework from the watch base:
   `settings.gradle.kts`, `libs.versions.toml`, wrapper, two modules.
3. Build `:core` with data models, biquad, limiters and tests.
4. Implement SAF selection and scanner; Check with test tree in the emulator.
5. Set up playback service with DSP and MediaSession.
6. Build player UI against the mockups.
7. EQ-UI and persistence.
8. Setup/Sine.
9. Sync Mock.
10. Cover, finishing touches, measurements, real Bluetooth test.

---

## 18. Appendix: Mockup Reference

The four screenshots show:

1. **Synchronize:** List of root/subfolders, checkboxes, two
   Sync buttons.
2. **Player:** Tabs, Folder/Title Arrows, Cover, Play, Volume, Bypass,
   preset.
3. **EQ:** Three presets, six filter chips (selected/blue, active/green),
   Type/Freq/Q/Gain/Volume fields.
4. **Setup:** Send to Watch, Select Root Folder, Root Path, Play Sine Wave,
   Frequency slider with ± buttons, volume.

The plan follows these mockups. Additional elements (clip badge,
Folder bottom sheet, “Filter active” switch, sync mock banner).
Additions that improve usability without compromising the mockup structure
change.