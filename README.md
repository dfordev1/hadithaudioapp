# Hadith.to Android

A calm, native Android companion for listening to, reading, and studying hadith.

## Product direction

The app opens directly into a hadith instead of a dashboard. The reading surface keeps the Arabic text central and reveals translation, study tools, and navigation only when they are useful.

The app includes:

- lazy browsing of all 12 live Hadith.to collections and 65,647 indexed records
- the major books, Riyad as-Salihin, Musnad Ahmad, and three forty-hadith collections
- collection-scoped exact-number lookup, including alphanumeric report IDs
- real Media3 playback wherever a Hadith.to timing sidecar is published
- synchronized highlighting from the timing sidecar's authoritative Arabic stream
- English, Urdu, and bilingual reading
- quiet listening and focused study modes
- an accessible narration-chain disclosure
- local preview text search plus exact-number lookup across every collection
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

The sacred reader and the complete live Hadith.to shelf are implemented: Sahih al-Bukhari, Sahih Muslim, Sunan Abi Dawud, Jami at-Tirmidhi, Sunan an-Nasa'i, Sunan Ibn Majah, Muwatta Malik, Riyad as-Salihin, Musnad Ahmad, Nawawi's Forty, Forty Hadith Qudsi, and Shah Waliullah's Forty. Collection indexes and book payloads load only when opened; translations load only for the selected narration and are labelled unavailable when the upstream source does not publish them.

The opening offline passage remains available immediately. Catalog records use the production Hadith.to Arabic corpora, translation endpoints, timing manifests, and recordings. Records without a published sidecar stay readable and are labelled audio-unavailable rather than borrowing another narration's recording. Synthetic recitations in the forty-hadith datasets are disclosed in the player.

Playback is foreground-only in this release. It handles audio focus and headphone disconnects, but a MediaSession service and notification controls are still required before background playback can be promised.

## Build

Open the project in a current stable Android Studio with JDK 17 and Android SDK 36 installed. CI uses Gradle 8.13.

```bash
./gradlew :app:assembleDebug
```

Run the full local verification with:

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease
```

Every pull request runs the same checks on GitHub Actions and uploads an installable debug APK plus an unsigned, minified release AAB. Play Store signing and upload remain secret-backed release steps; no keystore material belongs in this repository.

The manual `Android Signed Release` workflow can produce a verified signed AAB once the protected GitHub environment and four signing secrets in [docs/RELEASE.md](docs/RELEASE.md) are configured.

Release documentation includes the current [privacy policy](docs/PRIVACY.md).

## Data boundary

Arabic catalog data comes from `www.hadith.to`, translations from Hadith.to and the versioned Hadith API snapshot on jsDelivr, and timing/audio from the Hadith.to CDN. The app has no accounts, ads, or analytics. Normal CDN/server request logs may still receive network metadata such as an IP address when online content is opened.

Catalog payloads are cached in memory for the current app session. Full-catalog text search and durable offline downloads are intentionally not claimed yet.
