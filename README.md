![Mono Fix waveform logo](docs/mono-fix.svg)

# Mono Fix

Mono Fix is an Android 10+ app for videos with sound in only one ear. It converts a single stereo audio track to mono on the device and saves a new MP4 under `Movies/Mono Fix/`.

## Usage

1. Tap **Choose video** and select a clip.
2. Leave **Either side** selected when one channel is silent. Choose **Left only** or **Right only** to discard a noisy channel.
3. Tap **Save mono video**, then **Play saved video** to check the result.

The current version processes one video at a time.

## Build

Open the project in Android Studio with Android SDK 35 installed, or use Gradle 8.9 and JDK 17:

```sh
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
