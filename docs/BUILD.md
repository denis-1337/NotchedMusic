# Build and deployment

## Requirements

- JDK 17
- Android SDK with platform 37 and build-tools 36+
- Gradle/Android Studio matching the project wrappers
- A connected Pixel 10 / Android 17 device for phone testing
- A connected TicWatch Pro 5 / Wear OS 4 device for watch testing

The project uses two Gradle builds:

| Project | Modules | Build |
|---|---|---|
| `NotchedMusic-Phone` | `:core` (JVM), `:app` (Android) | `gradlew.bat` / `./gradlew` |
| `NotchedMusic-Watch` | `:app` only | `gradlew.bat` / `./gradlew` |

## Phone build and tests

```bat
cd NotchedMusic-Phone

:: unit tests
gradlew.bat :core:test :app:testDebugUnitTest

:: debug APK and lint
gradlew.bat :app:assembleDebug :app:lintDebug
```

Expected current status: **102 phone tests** (74 in `:core`, 28 in `:app`) and a clean phone lint
run. The DSP benchmark is part of the core tests and may show timing variation on emulators.

## Watch build and tests

```bat
cd ..\NotchedMusic-Watch

gradlew.bat app:testDebugUnitTest
gradlew.bat app:assembleDebug app:lintDebug
```

Expected current status: **150 watch tests**, 0 failures; lint has 0 errors and only dependency /
version warnings.

## Release build and signing

Both apps are intended to be installed personally. The release build must not be committed; only
source and documentation belong in Git.

```bat
cd NotchedMusic-Phone
gradlew.bat :app:assembleRelease

:: Align and sign the APK with a local key. Never commit the key or its passwords.
zipalign -p -f 4 app\build\outputs\apk\release\app-release-unsigned.apk ^
  app\build\outputs\apk\release\app-release-aligned.apk

apksigner sign --ks <path-to-local-keystore> ^
  --ks-key-alias <alias> ^
  --out app\build\outputs\apk\release\app-release-signed.apk ^
  app\build\outputs\apk\release\app-release-aligned.apk
```

Repeat the release procedure for the watch project. The phone and watch APKs must be signed with
the **same key**: the Wear Data Layer identifies the package and signature, and a mismatch prevents
the sync receivers from discovering each other.

`apksigner verify` should report a valid signature before installation.

## Install on devices

```bat
:: Phone release
adb -s <phone> install -r NotchedMusic-Phone\app\build\outputs\apk\release\app-release-signed.apk
adb -s <phone> shell am start -n de.notchedmusic.phone/.MainActivity

:: Watch release
adb -s <watch> install -r NotchedMusic-Watch\app\build\outputs\apk\release\app-release-signed.apk
adb -s <watch> shell am start -n de.notchedmusic.phone/de.notchedmusic.watch.MainActivity
```

The watch app deliberately uses the phone's application ID (`de.notchedmusic.phone`) but launches
the Wear activity `de.notchedmusic.watch.MainActivity`.

If an install happens while the phone is locked, Android may block the initial playback-service
start. Restart the app once while the device is unlocked before testing background playback.

## Emulator checks

```bat
emulator.exe -avd Pixel_10_API_37 -no-window -gpu swiftshader_indirect -port 5554
emulator.exe -avd Wear_OS_Large_Round -no-window -gpu swiftshader_indirect -port 5556
```

Useful device checks:

```bat
adb shell dumpsys media_session
adb shell dumpsys activity services de.notchedmusic.phone
adb shell dumpsys audio
```

## Repository hygiene

The root `.gitignore` excludes:

- Gradle and Android Studio state (`.gradle/`, `build/`, `.idea/`, `.kotlin/`);
- local SDK paths (`local.properties`);
- generated UI hierarchy dumps;
- APK/AAB files and signing material;
- local test audio and secret configuration files.

Build outputs and signing keys are never committed. The release APK is copied to a device directly
or distributed privately by the project owner.
