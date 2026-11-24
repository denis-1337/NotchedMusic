# Releases and APK downloads

Compiled, signed APKs are published as GitHub release assets so that users without Android Studio
or Gradle experience can install NotchedMusic directly.

## Current release

[**NotchedMusic v1.0.3**](https://github.com/denis-1337/NotchedMusic/releases/tag/v1.0.3)

| Asset | Device |
|---|---|
| `NotchedMusic-Phone-1.0.2.apk` | Pixel 10 / Android 17 |
| `NotchedMusic-Watch-1.0.1.apk` | TicWatch Pro 5 / Wear OS 4 |
| `SHA256SUMS` | SHA-256 checksums |

The release is private together with the repository.

## Installing without development tools

1. Open the release page and download the APK for the phone.
2. Open the downloaded file on the phone and allow installation from the file manager/browser if
   prompted.
3. On the watch, download `NotchedMusic-Watch-1.0.1.apk` and install it with the Wear OS package installer.
4. Open the phone app first and complete the music-folder setup.

Both APKs are signed with the same project key. The watch APK deliberately uses the phone's package
identity (`de.notchedmusic.phone`) but launches the Wear activity
`de.notchedmusic.watch.MainActivity`.

## adb installation (development)

```bat
adb -s <phone> install -r NotchedMusic-Phone-1.0.2.apk
adb -s <phone> shell am start -n de.notchedmusic.phone/.MainActivity

adb -s <watch> install -r NotchedMusic-Watch-1.0.1.apk
adb -s <watch> shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity
```

## Latest changes

- **v1.0.3:** complete English watch UI: album, player, EQ, settings, error and status messages,
  date formats and track counts. The phone APK stays at 1.0.2, which already has the English UI
  and the playback-service startup fix.
- **v1.0.2:** complete English phone UI: labels, status messages, chart labels, dates and the
  statistics view. Includes the playback-service startup fix from 1.0.1.
- **v1.0.1:** playback service is re-started from the activity and before playback, fixing the
  screen-lock case where playback previously ran without a foreground service. The full project
  documentation is now English.

## Release checklist

1. Build and test both projects.
2. Sign both release APKs with the same key.
3. Verify with `apksigner verify`.
4. Record SHA-256 checksums.
5. Create a GitHub release with both APKs and `SHA256SUMS`.
6. Keep signing keys, keystores, build outputs and local test audio out of Git.

Only signed release APKs are attached. Unsigned APKs, signing keys and passwords are never
published.
