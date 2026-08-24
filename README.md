# Hadith.to Android

A calm, native Android companion for listening to, reading, and studying hadith.

## Product direction

The app opens directly into a hadith instead of a dashboard. The reading surface keeps the Arabic text central and reveals translation, study tools, and navigation only when they are useful.

The MVP includes:

- real Media3 playback of the opening passage of Sahih al-Bukhari 1
- synchronized Arabic word highlighting from the Hadith.to timing manifest
- English, Urdu, and bilingual reading
- quiet listening and focused study modes
- an accessible narration-chain disclosure
- local search and an honest collection roadmap
- accessible, RTL-aware Jetpack Compose UI
- a clearly labelled silent timing preview when audio is unavailable

## Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Lifecycle
- Media3 / ExoPlayer
- Kotlin coroutines
- a single, intentionally small app module

## Status

The sacred-reader MVP is implemented. It deliberately ships one complete interaction slice rather than presenting unavailable collections as finished. Sahih Muslim and Sunan Abi Dawud appear as disabled “coming next” entries.

The app resolves the production Bukhari timing sidecar and recording from the existing Hadith.to corpus. The player clips the full recording to the displayed opening passage, preserves exact word timing, handles audio focus and headphone disconnects, and labels synthetic narration whenever a timing sidecar declares it.

## Build

Open the project in a current stable Android Studio with JDK 17 and Android SDK 36 installed. CI uses Gradle 8.13.

```bash
gradle :app:assembleDebug
```

Run the full local verification with:

```bash
gradle :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Every pull request runs the same checks on GitHub Actions and uploads `hadithaudioapp-debug-apk` as an installable debug artifact. A signed Play Store bundle is intentionally outside this MVP until release signing and store credentials are configured.

## Data boundary

The reader model is local today, while audio and word timings use the existing static Hadith.to corpus contract. Expanding the catalog means adding collection adapters behind `HadithRepository`; the Compose reader and playback UI do not need to be rewritten.
