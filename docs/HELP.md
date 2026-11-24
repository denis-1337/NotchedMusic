# Help — using NotchedMusic

The NotchedMusic phone app uses an English user interface.

NotchedMusic is a private Android music player built for **tinnitus retraining through notched
music listening**. It is not a medical device. If sound is uncomfortable, painful or causes
headaches, stop listening and seek medical advice.

The phone app is the library master and EQ editor. The Wear OS app is a standalone player: it
plays mirrored music on the watch with its own real-time notch filtering, equivalent to the phone
app, and can return EQ listening statistics during sync.

## 1. Installation

The easiest route for normal users is the [latest GitHub release](https://github.com/denis-1337/NotchedMusic/releases/tag/v1.0.3): download the phone APK and the watch APK from the release assets and install them with Android's package installer. No Android Studio or Gradle setup is required.

The phone and watch apps must use the **same application package identity and signing key** so
that the Wear Data Layer can find them. The watch app is installed as `de.notchedmusic.phone` but
launches with the activity `de.notchedmusic.watch.MainActivity`.

```bat
:: Phone
adb -s <phone> install -r NotchedMusic-Phone\app\build\outputs\apk\release\app-release-signed.apk
adb -s <phone> shell am start -n de.notchedmusic.phone/.MainActivity

:: Watch
adb -s <watch> install -r NotchedMusic-Watch\app\build\outputs\apk\release\app-release-signed.apk
adb -s <watch> shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity
```

Use the same locally signed release APKs described in [`BUILD.md`](BUILD.md). A debug build may
work for development, but the watch and phone must share the same signature.

## 2. First setup on the phone

1. Open the **SETUP** tab.
2. Choose a music folder with Android's Storage Access Framework picker.
3. NotchedMusic scans the folder recursively. A direct subfolder becomes an album; files in
   deeper folders still belong to that album.
4. Supported formats are MP3, M4A/AAC, OGG/Opus, FLAC and WAV. MP3 is prioritised when sorting.
5. The folder permission persists across app restarts.
6. No broad storage permission is requested. Access stays limited to the selected SAF tree.

The library is the phone's source of truth. The phone does not stream music from watch to phone.

## 3. Playing music

The **PLAYER** tab contains:

- folder and title arrows for stepping through the library;
- a folder picker for direct album/root selection;
- large play/stop control;
- app volume from 0–100 % (`−60…0 dB`);
- repeat mode `OFF / TITLE / ALL`;
- automatic advance to the next track;
- album cover (embedded art, local cache, online lookup or placeholder);
- a clipping badge when the signal reaches the configured ceiling.

Playback uses Media3/ExoPlayer. The EQ DSP runs in the audio sink, so filtering is applied in
real time rather than by rewriting files.

### Sine tone

The tone generator is available for testing filters and checking frequency response:

- range 100–15,000 Hz;
- 10 Hz slider steps;
- fine tuning of ±1 Hz, and ±10 Hz on a long press;
- auto-advance can be disabled;
- the sine tone is not counted as EQ listening time.

## 4. EQ and notched music

The **EQ** tab edits three presets. Each preset has six bands with:

| Parameter | Meaning |
|---|---|
| Filter type | `OFF`, `NOTCH`, `PEAKING`, `LOW_SHELF`, `HIGH_SHELF`, `LOW_CUT`, `HIGH_CUT` |
| Frequency | Centre/corner frequency in Hz |
| Q | Filter bandwidth/shape |
| Gain | Boost/cut in dB |
| Preamp | Preset-wide gain trim |

The notch filter is the central tool for notched music listening: it attenuates a narrow band
while the rest of the spectrum remains. Build the preset slowly and at comfortable volume. Bypass
is enabled by default; switch it off to hear the processed signal.

The clip indicator appears when the output reaches the limiter ceiling. Reduce preamp or gain if
it stays active. Changing presets while audio is playing uses coefficient crossfading to avoid
clicks.

## 5. Sync with the watch

### Requirements

- Wear companion app installed on the phone;
- phone and watch signed into the same Google account;
- Bluetooth enabled and both devices unlocked;
- the watch app open while searching or starting a sync.

The watch advertises its capability only while its app is open (to save power). If the watch app
is closed, the phone reports that no watch was found.

### Preset sync

1. On the phone, open **SETUP → watch section**.
2. Tap **Find watch**.
3. Tap **SYNC EQS**.
4. The phone sends all three presets with names, bands and the active selection.
5. The watch validates the payload and acknowledges it.

The watch exposes its own EQ selector as `Off / P1 / P2 / P3`; band editing stays on the phone.

### Music mirroring

1. Select the root folder or an album on the phone.
2. Tap **SYNC MUSIC**.
3. The phone sends a manifest with file sizes and SHA-256 hashes.
4. Missing or changed files are transferred as Wear assets. Existing identical files are skipped.
5. Only after the watch acknowledges the commit are unselected titles deleted from the watch.
6. On the watch, use **Reload** to read the mirrored folder again.

The phone library is the master. A sync can take a long time over Bluetooth: a large library may
transfer at roughly two tracks per minute, and a single 40 MB file may need five to six minutes.
There is no per-file timeout; the session only fails after 30 minutes of complete silence. During
mirroring, a keepalive message refreshes the watch receiver every two minutes, and the watch may
close its UI while the transfer continues.

The watch's music folder is:

```text
/sdcard/Android/data/de.notchedmusic.phone/files/Music/
```

The application ID is intentionally shared with the phone app. Both apps must use the same
signature for the Wear Data Layer to work.

The watch does **not** stream audio from the phone. It applies EQ/notch filtering in its own
Media3 audio sink and can keep playing mirrored music when the smartphone is not nearby.

### Sync protocol paths

| Direction | Path | Purpose |
|---|---|---|
| Phone → Watch | `/filter/profiles/<id>` | three EQ presets |
| Watch → Phone | `/filter/ack` | preset acknowledgement |
| Watch → Phone | `/filter/request` | request fresh presets |
| Phone → Watch | `/music/manifest/<id>` | target file list with size and SHA-256 |
| Watch → Phone | `/music/manifest/ack` | present/missing indices |
| Phone → Watch | `/music/upload/<id>` + asset | audio file transfer |
| Watch → Phone | `/music/ack` | per-file acknowledgement |
| Phone → Watch | `/music/commit` | release the mirror plan |
| Watch → Phone | `/music/commit/ack` | confirm mirror deletion |
| Phone → Watch | `/music/keepalive` | session heartbeat during mirroring |
| Phone ↔ Watch | `/sync/cancel` | abort the current run |
| Phone → Watch | `/stats/request` | ask for watch statistics |
| Watch → Phone | `/stats/report` | statistics snapshot |
| Phone → Watch | `/stats/ack` | confirm applied statistics (APPLIED) |

## 6. EQ listening statistics

The fourth phone tab is **STATS**. It shows:

- total EQ listening time;
- all-time average per calendar day (zero days included);
- today's listening time;
- period totals for the selected week, month or year.

The chart has a Week / Month / Year switcher and `‹`/`›` arrows for older periods. Scale labels
show the period maximum, half-maximum and zero. Durations under one minute are shown in seconds
(for example `51 s`), then minutes, then hours.

Rules for counting:

- **Phone:** only while music is actively playing with EQ bypass **off**.
- **Watch:** only while music is actively playing with an active preset (`P1/P2/P3`).
- A sine tone is never counted.
- Month charts use ISO calendar weeks, Monday–Sunday; weeks at month edges only count days inside
  the month.

The phone stores statistics as a journal plus a compacted `days.json`. The phone merges watch
reports during sync. The watch keeps its snapshot until the phone sends an APPLIED acknowledgement;
only then does it delete the transferred snapshot. Newer watch segments survive the deletion.

## 7. Background playback and screen lock

The playback service is a Media3 `MediaSessionService`. Media3 owns the foreground notification
and the foreground-service state.

Expected behaviour:

- incoming calls pause playback through audio focus;
- hanging up resumes playback;
- locking the screen during playback does not stop the foreground service or the audio track;
- the media notification persists while playback is active;
- unplugging headphones/Bluetooth is handled by `setHandleAudioBecomingNoisy(true)`.

If playback stops after a call or screen lock, use the current release build (1.0.1 or newer).
The app now re-starts the playback service when it becomes visible and before playback. Starting an
older build while the phone is locked could block the initial service start and leave playback
running without a foreground service.

## 8. Troubleshooting

### No watch found

- Open the watch app and keep both devices unlocked.
- Check Wear Companion pairing, Bluetooth and the same Google account.
- On the phone, tap **Find watch** again.
- The watch only advertises its capability while the app is open.

### Transfer does not start

- The phone disables `SYNC EQS` / `SYNC MUSIC` until the watch reports itself.
- DataItems are written urgently; a missing urgent flag can look like a timeout.
- Once the transfer starts, the watch UI may close. Incoming data and keepalives keep the receiver
  alive.
- The watch receiver closes after 90 seconds without a session; during music mirroring the
  fallback window is 30 minutes.

### `ASSET_UNAVAILABLE`

Watch assets are addressed by the Play Services digest, not by the hexadecimal file hash. The
integrity check itself remains SHA-256. Retry the transfer after updating both apps.

### `DUPLICATE_CAPABILITY` / `4006`

This is harmless. The app and listener register the same phone capability; the duplicate is
ignored.

### Music lands in the wrong folder

The watch turns direct subfolders into albums. "Music (all)" preserves the root structure instead
of flattening filenames.

### Cover missing

Online covers require artist and album metadata. Without a match or network access, NotchedMusic
shows a placeholder. Cover lookups are rate-limited and time-limited.

### Statistics not updating

- Make sure EQ is active and music is playing.
- On the phone, EQ bypass must be off.
- On the watch, an active preset must be selected.
- Open the STATS page or run a watch sync to compact the local journal.
- Watch reports are deleted only after the phone acknowledges them.

## 9. Privacy

NotchedMusic does not use analytics, accounts or cloud storage. Network access is limited to album
cover lookup (MusicBrainz/Cover Art Archive and iTunes Search API), with a short timeout and a
rate limit. Only track title, artist and album are sent; no paths, playback history or personal
data leave the device.

## 10. Screenshots

The repository overview shows current phone and watch screenshots in
[`README.md`](../README.md#screenshots). Phone images live in
`NotchedMusic-Phone/docs/screenshots/`, watch images in `NotchedMusic-Watch/docs/screenshots/`.
