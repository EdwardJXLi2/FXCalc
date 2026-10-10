# FXCalc

Android app for converting one amount into several currencies.

## Features

- Add, remove, swap, and reorder currencies. Choices are saved.
- Choose from five exchange-rate sources.
- Use saved rates offline.
- Pull down to refresh rates.

## Requirements

- Android Studio or the Android Gradle plugin toolchain.
- Android SDK 36.1.
- Android 14 or newer device or emulator.

## Build

```sh
./gradlew :app:assembleDebug
```

## Test

```sh
./gradlew :app:testDebugUnitTest
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
