# FXCalc

Android app for converting one amount into several currencies.

## Features

- Add, remove, swap, and reorder currencies. Choices are saved.
- Choose from five exchange-rate sources.
- Use saved rates offline.
- Pull down to refresh rates.
- Home-screen widget for saved currencies.
- Quick-convert notification (experimental): enter an amount in the notification shade to convert it.

## Requirements

- Android Studio or the Android Gradle plugin toolchain.
- JDK 21.
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
