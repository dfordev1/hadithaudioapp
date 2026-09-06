# Privacy policy

Effective: 5 September 2026

Hadith.to Android does not require an account and does not include advertising, analytics SDKs, location access, or contacts access.

## Network requests

When a reader opens online content or downloads a recording, the app requests Arabic text, translations, timing manifests, and audio from Hadith.to infrastructure, Cloudflare R2, and jsDelivr. Those providers may receive standard connection metadata such as IP address, user agent, requested path, and timestamp according to their operational policies. The app does not add an advertising identifier or account identifier.

The app checks network connectivity and whether Wi-Fi is available to honor the download preference. It does not read Wi-Fi names or device location.

## Data on the device

Saved hadiths, saved words, recently opened passages, audio positions, reading preferences, catalog caches, and downloaded recordings/timings are stored in the app's private storage. They are not synchronized to an account. Android cloud backup is disabled. Removing a download deletes its audio and timing files while preserving the saved text. Clearing app storage or uninstalling removes the local library and preferences.

## Optional error reports and sharing

The report form sends data only when the reader presses Send report. The payload contains the selected collection and hadith number, the error category, the reader's note, a passage/timing reference, and an optional selected word. It is sent to Hadith.to's error-report service at `hadith-error-reports.quran-wbw.workers.dev`. The service also receives normal connection metadata. Do not include information in a report that you do not want its maintainers to receive.

Copy and Share are reader-initiated actions using the Android clipboard and share sheet. The destination app or recipient is chosen by the reader.

## Sharing and sale

The app does not sell personal data or send it to advertisers or data brokers. Content providers receive normal network requests; maintainers receive only error reports that a reader explicitly submits.

## Changes and contact

Material changes are recorded in this repository before a new production release. Questions can be opened through the [Hadith.to Android issue tracker](https://github.com/dfordev1/hadithaudioapp/issues).
