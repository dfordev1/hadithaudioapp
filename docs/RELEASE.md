# Android release checklist

The normal `Android` workflow deliberately produces an unsigned release AAB. Signing runs only through the manual `Android Signed Release` workflow and only when all four protected secrets are present.

## One-time Play setup

1. Reserve the Play application ID `to.hadith.audio` and enable Play App Signing.
2. Create a dedicated upload key. Keep both the keystore and its recovery material outside this repository.
3. In GitHub, create a protected environment named `play-release` with required reviewers.
4. Add these environment secrets:

   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`

   Encode the keystore as one base64 string locally. Never paste the keystore or passwords into source, issues, pull requests, build logs, or chat.

## Every release

1. Confirm `versionCode` increased and `versionName` is correct in `app/build.gradle.kts`.
2. Confirm the main `Android` workflow is green.
3. Run `Android Signed Release` manually from the intended commit on `main`.
4. Download `hadithaudioapp-signed-release-aab` and verify the workflow's `jarsigner` step passed.
5. Upload first to Play's internal testing track and complete install, catalog, translation, RTL, audio-focus, offline, and accessibility smoke tests.
6. Publish [the privacy policy](PRIVACY.md), complete corpus/audio attribution, and submit the Play Data Safety form before production rollout.

The workflow prepares a signed bundle but does not upload to Play automatically. Automated Play upload requires a separately authorized Google Play service account and an explicit release-track policy.
