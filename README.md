# Mono Fix — Fix one-sided video audio on Android

![Mono Fix waveform logo](docs/mono-fix.svg)

Hear your video in only one ear? Mono Fix converts stereo audio to mono so the recording plays through both earbuds. It’s useful when a microphone recorded a voice on just the left or right channel. The app works offline and saves a new MP4 on your phone.

**[Download the Android APK](https://github.com/Rohitms/mono-fix/releases/latest/download/Mono-Fix.apk)** · Android 10 or newer · [Release notes](https://github.com/Rohitms/mono-fix/releases/latest)

## How to convert a video's stereo audio to mono

1. Open Mono Fix, tap **Choose video**, and select your recording.
2. If one side is silent, leave **Either side** selected. If one side contains noise, choose **Left only** or **Right only** to keep the clean channel.
3. Tap **Save mono video**, then **Play saved video** to listen to the result.

Look in `Movies/Mono Fix/` for the copy ending in `-mono.mp4`. You can share this file with someone else and they’ll hear the centered audio on their device.

## Supported recordings

Version 1.0 processes one video at a time and expects one stereo audio track. If two people were recorded on separate channels, choosing **Left only** or **Right only** keeps just the person on that side.

## Build the Android app

Mono Fix uses Java and AndroidX Media3 Transformer. Open the project in Android Studio with Android SDK 35 installed, or build with Gradle 8.9 and JDK 17.

```sh
gradle :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
