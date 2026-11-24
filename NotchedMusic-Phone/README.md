# NotchedMusic Phone

The phone application for **NotchedMusic**, a private music player developed specifically for
tinnitus retraining through notched music listening. It targets **Google Pixel 10 / Android 17
(API 37)** and is built with Kotlin, Jetpack Compose and Material 3.

The phone is the library master and EQ editor. It scans music through Android's Storage Access
Framework, applies a real-time notch-filter DSP chain in Media3's audio sink, and syncs presets and
mirrored music to the companion Wear OS app over the Wear Data Layer.

> The Wear OS app is a standalone player with its own real-time DSP and notch filtering. The phone
> is not required for playback on the watch.

Project status and detailed measurements are in [`PROJECT_STATUS.md`](PROJECT_STATUS.md).

## Quick start

Requirements: JDK 17, Android SDK platform 37, build-tools 36+, and a connected Pixel 10.

```bat
cd NotchedMusic-Phone

gradlew.bat :core:test :app:testDebugUnitTest
gradlew.bat :app:assembleDebug :app:lintDebug
```

Release signing and installation instructions are in [`../docs/BUILD.md`](../docs/BUILD.md).

## Features

- **Library access without broad storage permissions** — SAF tree access, recursive scan, root and
  direct subfolders as navigation points, natural sorting, MP3 prioritised; MP3, M4A/AAC,
  OGG/Opus, FLAC and WAV supported.
- **Player** — folder/title arrows, folder picker, large play/stop, app volume (0–100 % /
  −60…0 dB), auto-advance, repeat OFF/TITLE/ALL, album cover (embedded → local cache → online
  lookup → placeholder) and a clip badge.
- **EQ** — three presets with six bands; filter types `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`,
  `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT`; frequency, Q, gain and preamp; bypass enabled by default;
  coefficient crossfade to prevent clicks.
- **DSP** — transposed biquad DF-II chain and stereo-coupled limiter with a −1 dBFS ceiling before
  16-bit conversion; fixed buffers and no allocation in the sample path.
- **Background playback** — Media3 `MediaSessionService` with a foreground media notification,
  headset/Bluetooth controls and `setHandleAudioBecomingNoisy(true)`.
- **Sine tone** — separate `AudioTrack` generator, 100–15,000 Hz, 10 Hz slider, ±1 Hz fine tuning
  (±10 Hz on long press), optional auto-advance.
- **Persistence** — DataStore Preferences, versioned JSON settings, conflated writes and fallback
  to defaults for corrupt data.
- **EQ statistics** — fourth tab with total time, all-time average per day, today's time and
  week/month/year charts; merges watch reports during sync.
- **Accessibility** — English UI, dark OLED-friendly palette and large touch targets.

## Sync with the watch

Both apps must be installed with the same package identity and signing key. The watch app uses the
phone's application ID but launches `de.notchedmusic.watch.MainActivity`.

1. Pair the watch with the Wear companion on the phone using the same Google account.
2. Open the watch app; it advertises its capability only while open.
3. On the phone: **SETUP → Find watch**.
4. **SYNC EQS** sends all three presets and the active selection.
5. Select a root folder or album, then **SYNC MUSIC** to mirror files with SHA-256 verification.
6. The watch reloads the local music folder and can play independently with local real-time EQ.

The protocol uses DataItems and messages for `/filter/*`, `/music/*`, `/stats/*` and `/sync/cancel`.
See [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full table.

## Tests

Current status:

- `:core:test` and `:app:testDebugUnitTest`: 102 tests total, 0 failures.
- `lintDebug`: clean.
- DSP benchmark: roughly 11–14 ns/sample for six stereo bands at 48 kHz.
- Real hardware: preset/music sync and statistics handoff verified on Pixel 10 ↔ TicWatch Pro 5.

## Design boundaries

- No `MANAGE_EXTERNAL_STORAGE`; access is limited to the SAF picker.
- No custom Bluetooth stack; A2DP/codec/latency stay with the system.
- No music streaming from phone to watch; the watch plays mirrored local files.
- No cover art on the watch; only audio files, album names and file names are transferred.
- Online covers are optional and only use title/artist/album metadata.
- No analytics, account, cloud storage or listening-history upload.
