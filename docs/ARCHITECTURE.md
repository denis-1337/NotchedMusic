# Architecture

NotchedMusic consists of two Android applications and one shared JVM module. The phone app owns
the library, EQ editing and master sync state. The watch app is a standalone player with its own
real-time DSP and local statistics.

## Modules

| Module | Role |
|---|---|
| `NotchedMusic-Phone/core` | Pure JVM model, DSP and sync protocol; testable without Android |
| `NotchedMusic-Phone/app` | Android UI, SAF library, playback pipeline, sync transport, statistics |
| `NotchedMusic-Watch/app` | Wear OS UI, local playback, watch DSP, statistics, Wear receivers |
| `eq_mediaplayer` | Original requirements, planning notes and GUI mockups |

## Phone playback pipeline

```
SAF library scan
      ↓
PlaybackEngine (Media3 ExoPlayer)
      ↓
EqAudioProcessor + EffectChain (biquads, limiter, preamp)
      ↓
AudioTrack
```

`PlaybackEngine` owns the player and applies settings snapshots from `SettingsRepository`. The DSP
uses fixed working buffers and reads volatile EQ coefficients from the audio thread, so parameter
changes do not allocate in the sample path. Coefficients are crossfaded when a preset changes to
avoid clicks.

The playback service is a Media3 `MediaSessionService`. Media3 owns the media notification and the
foreground-service lifecycle. The service is registered with `addSession(session)` and does not
call `startForeground`/`stopForeground` itself. This is what keeps playback alive through calls,
screen locks and the pause/resume transitions.

## Settings and persistence

Phone settings are stored in DataStore Preferences. A versioned JSON entry holds all three presets,
the active preset, volume, bypass and display preferences. Corrupt data falls back to defaults.
The watch keeps its own `WatchSettings` store and filter settings, with migration from the old A/B
profile format.

Both apps share an equally named package identity and the same signing key. The watch keeps this
quirk deliberately so the Wear Data Layer can resolve the phone package.

## Wear Data Layer sync

### Presets

The phone sends `/filter/profiles/<id>` as a DataItem containing all three presets and the active
selection. The watch validates the payload, stores it and acknowledges with a message
`/filter/ack`. The watch can request a fresh profile with `/filter/request`.

### Music mirroring

1. Phone builds a manifest with file sizes and SHA-256 hashes.
2. Watch reports which entries are missing or already present.
3. Phone sends missing/changed files as Wear assets.
4. Watch acknowledges each file.
5. Phone sends `/music/commit`; the watch commits the mirror plan and acknowledges deletion of
   unselected titles.

The phone library is the master. Files are not streamed from the phone; the watch plays local
copied files with its own Media3/DSP pipeline, so it works without the smartphone nearby.

DataItems are written urgently, because non-urgent writes can wait for a data-layer idle window.
During a long mirroring session, the phone sends `/music/keepalive` every two minutes. There is no
per-file timeout; only 30 minutes of complete silence ends the session. A watch receiver closes
after 90 seconds outside a session.

### EQ listening statistics

The statistics protocol (`/stats/request`, `/stats/report`, `/stats/ack`) uses a stable report ID
for idempotent retries:

- the phone requests a snapshot;
- the watch returns its accumulated days through a DataItem;
- the phone merges the values, deduplicates with `lastWatchReportId`, and sends an APPLIED
  acknowledgement;
- the watch deletes only the acknowledged snapshot. Segments recorded after the snapshot remain.

Phone statistics are stored as an append-only journal plus a compacted `days.json` file. The journal
is compacted when the STATS page opens or a sync runs. `journalCompactedThroughMs` prevents
double-counting after a crash.

## Watch playback

The watch uses the same model/DSP concepts as the phone but is self-contained:

- local music folder under `/sdcard/Android/data/de.notchedmusic.phone/files/Music/`;
- its own `PlaybackService` built on Media3;
- active preset `P1/P2/P3` or `Off`;
- real-time EQ/notch filtering in the watch audio sink;
- local EQ statistics journal, handed to the phone during sync.

The watch UI is optimized for 466 × 466 OLED: album/title navigation, compact player controls,
preset selection and settings. Band editing remains on the phone.

## Protocols and paths

| Direction | Path | Purpose |
|---|---|---|
| Phone → Watch | `/filter/profiles/<id>` | three presets |
| Watch → Phone | `/filter/ack` | preset ACK |
| Watch → Phone | `/filter/request` | request fresh presets |
| Phone → Watch | `/music/manifest/<id>` | file list |
| Watch → Phone | `/music/manifest/ack` | missing/present indices |
| Phone → Watch | `/music/upload/<id>` + asset | audio transfer |
| Watch → Phone | `/music/ack` | file ACK |
| Phone → Watch | `/music/commit` | commit mirror |
| Watch → Phone | `/music/commit/ack` | deletion confirmation |
| Phone → Watch | `/music/keepalive` | two-minute session heartbeat |
| Phone ↔ Watch | `/sync/cancel` | abort |
| Phone → Watch | `/stats/request` | request statistics snapshot |
| Watch → Phone | `/stats/report` | statistics report |
| Phone → Watch | `/stats/ack` | applied snapshot ACK |

## Verification

The project has no mock sync gateway in production. Real Wear Data Layer sync has been exercised on
a Pixel 10 and a TicWatch Pro 5, including a real statistics handoff (51,645 s) and APPLIED
acknowledgement.

Current test status: 102 phone tests and 150 watch tests, with lint clean on the phone and 0 errors
on the watch. Details are kept in each project's status document.
