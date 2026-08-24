# Hadith.to for Android

A calm, native Android companion for listening to, reading, and studying hadith.

## Product direction

The app opens directly into a hadith instead of a dashboard. The reading surface keeps the Arabic text central and reveals translation, study tools, and navigation only when they are useful.

The first release focuses on:

- synchronized Arabic word highlighting
- English, Urdu, and bilingual reading
- quiet listening and focused study modes
- collection browsing and search
- accessible, RTL-aware Jetpack Compose UI
- streamed audio through Android Media3

## Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX Lifecycle
- Media3 / ExoPlayer
- single app module with feature-focused packages

## Status

Active development. The initial Compose scaffold and sacred-reader MVP are being built now.

## Build

Open the project in a current stable Android Studio with JDK 17 and Android SDK 36 installed. CI uses Gradle 8.13.

```bash
gradle :app:assembleDebug
```

Until the production Hadith.to API contract is connected, the app uses a small, clearly isolated sample repository for UI development.
