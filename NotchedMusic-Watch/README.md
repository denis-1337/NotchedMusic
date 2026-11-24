# NotchedMusic Watch

The Wear OS companion for **NotchedMusic**, a private music player developed for tinnitus
retraining through notched music listening. It targets the **TicWatch Pro 5 / Wear OS 4 /
API 33** (466 × 466 round OLED).

Unlike a remote-control companion, this app is a **standalone player**. It plays the music mirrored
from the phone on the watch itself, applying real-time EQ and notch filtering in its local Media3
audio sink — equivalent to the phone app. It does not stream audio from the smartphone and can keep
playing when the phone is not nearby.

## Features

- Local library of mirrored audio files, with album and title navigation.
- Compact player with progress arc, time, title, preset mode, transport controls and volume bar.
- EQ selection only: `Off`, `P1`, `P2`, `P3`; band editing stays on the phone.
- Real-time DSP with biquad filters and limiter in the watch playback pipeline.
- Local EQ listening statistics, returned to the phone via the Wear Data Layer.
- OLED-friendly display, no permanent animations and power-conscious display refresh.
- Settings page with phone status, profile request, storage usage and library reload.

## Synchronisation

The watch receives presets and mirrored music from the phone over the real Wear Data Layer. The
phone remains the library master and sends a manifest with SHA-256 hashes before uploading files.
Only after the phone receives the commit acknowledgement are unselected titles deleted.

The watch app uses the phone's package identity (`de.notchedmusic.phone`) but launches the Wear
activity:

```bat
adb shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity
```

Both APKs must be signed with the same key.

The watch stores its music under:

```text
/sdcard/Android/data/de.notchedmusic.phone/files/Music/
```

Statistics are transferred only during a sync. The watch keeps a snapshot until the phone sends an
APPLIED acknowledgement; newer locally recorded segments survive the deletion.

## Build and tests

```bat
cd NotchedMusic-Watch
gradlew.bat app:testDebugUnitTest
gradlew.bat app:assembleDebug app:lintDebug
```

Current status: **150 tests, 0 failures**; lint has 0 errors with dependency/version warnings. A
locally signed release APK is installed on the real watch. See
[`../docs/BUILD.md`](../docs/BUILD.md) for signing and deployment.

## Protocol overview

| Direction | Path | Purpose |
|---|---|---|
| Phone → Watch | `/filter/profiles/<id>` | three EQ presets |
| Watch → Phone | `/filter/ack` | preset acknowledgement |
| Watch → Phone | `/filter/request` | request fresh presets |
| Phone → Watch | `/music/manifest/<id>` | target list with size and SHA-256 |
| Watch → Phone | `/music/manifest/ack` | missing/present indices |
| Phone → Watch | `/music/upload/<id>` + asset | audio file |
| Watch → Phone | `/music/ack` | file acknowledgement |
| Phone → Watch | `/music/commit` | commit mirror |
| Watch → Phone | `/music/commit/ack` | deletion acknowledgement |
| Phone → Watch | `/music/keepalive` | two-minute session heartbeat |
| Phone → Watch | `/stats/request` | request statistics |
| Watch → Phone | `/stats/report` | statistics snapshot |
| Phone → Watch | `/stats/ack` | APPLIED acknowledgement |

See [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full architecture and
[`PROJEKTSTATUS.md`](PROJEKTSTATUS.md) for detailed project history.
