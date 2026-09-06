# Hadith.to Android

A quiet, native Android companion for listening to, reading, and studying Hadith.

## The reading experience

Version 0.6 applies the approved Android designs across the app. Cool stone pages, matte bronze controls, Source Serif headings, and bundled Arabic fonts give the library, reader, and player one consistent visual language. The app opens in Library, with Listen and Search always within reach on browsing screens.

The app includes:

- All 12 live collections and 65,647 indexed records, with collection contents and book-level browsing.
- Reading and Study modes, selectable English/Urdu translations, word details, synchronized word highlighting, and a focused reader.
- Light, sepia, and dark appearances; Scheherazade New, Amiri, or Noto Naskh Arabic; adjustable text sizes, line spacing, and word spacing.
- Saved hadiths and words, recent reading, and audio resume positions stored privately on the device.
- Real Media3 playback, exact word playback, speed control, repeat, next-hadith playback, and sleep timers.
- Audio and text downloads for individual hadiths or a book, real byte progress, Wi-Fi-only controls, retry, cancellation, and removal.
- Search by exact hadith number within a collection, including letter suffixes; text search across the 500 most recently cached passages on this device.
- Sources, font licences, native sharing and copying, and an optional passage-specific error report form.
- Explicit empty, loading, offline, missing-translation, and audio-unavailable states.

Recordings use the timing sidecar's authoritative Arabic stream. A missing recording is shown as unavailable and cannot start silent playback. Synthetic narration in the forty-hadith datasets is disclosed in the player. Available translations and word meanings come from the existing sources; missing meanings are never invented.

## Offline and lifecycle behavior

Completed recordings, timing files, text, saved items, recent reading, and preferences survive an app restart. Catalog payloads are cached on disk. A bundled opening passage of Bukhari 1 remains readable without a connection.

Keep the app open while downloading. The active queue is retained through screen and Activity changes but does not survive process termination. Paused transfers restart from the beginning when retried; only completed recordings appear as available offline.

Playback handles audio focus and headphone disconnection. This release does not provide a background MediaSession service or notification controls; foreground listening is supported.

## Stack and build

Kotlin, Jetpack Compose, Material 3, AndroidX Lifecycle, Media3, and coroutines. JDK 17, Android SDK 36, minimum Android 8.0, Gradle 8.13.

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

For device tests with an emulator attached:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Pull requests run lint, unit tests, APK/AAB builds, and Android device checks. CI captures the 20 approved screen states plus large-type and unavailable-audio states, and checks reader actions, navigation, appearance persistence, and Activity recreation. Download `hadithaudioapp-debug-apk` from the latest successful Android workflow for an installable debug build.

The separate [signed release workflow](docs/RELEASE.md) uses protected signing secrets. No keystore material belongs in this repository.

## Data and privacy

Arabic data comes from Hadith.to, translations from Hadith.to and its versioned Hadith API snapshot on jsDelivr, and recordings/timings from the existing Hadith.to CDN. The app has no accounts, ads, or analytics. Optional error reports are sent only when the reader presses Send report. See the [privacy policy](docs/PRIVACY.md).
