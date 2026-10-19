# Newz

A privacy-first news reader for Android. Newz builds your feed straight from publishers' own RSS
feeds, on your phone. It has no account, no server, no analytics, and it never contacts Google.

## What it does

- **One feed from hundreds of outlets.** A built-in catalog of 343 outlets and 477 feeds across 20
  topics, plus any site you add yourself by address or search.
- **Same story, one card.** Articles about the same event from different outlets are grouped
  together on the device, so a big story shows up once rather than ten times.
- **You decide what you see.** Follow topics and set how often each appears, mute keywords, hide
  stories or whole outlets, and use "Show less like this" on any card.
- **Local news without location tracking.** Type a zip code once; it is matched to a metro area
  using a table that ships with the app and never leaves the device.
- **Works offline.** Articles are cached on the phone, and saved articles stay readable.
- **Your data stays yours.** Reading history is optional and on-device only. Settings, follows,
  mutes and saved articles can be exported to a file and imported again.

Articles open in your browser's in-app tab. Newz shows headlines and pictures from the feeds; it
does not copy or host articles.

## Privacy

Newz's core rule is **zero contact with Google servers**, and the code enforces it rather than
relying on good intentions:

- Every network request goes through one gate, [`HostGuard`](shared/src/commonMain/kotlin/app/newz/net/HostGuard.kt),
  which refuses any Google-owned host (google.\*, gstatic, googleapis, YouTube, Blogger,
  FeedBurner, DoubleClick, Firebase and more). Feeds, pictures and discovered sites are all
  checked, including after redirects.
- No Google Play Services, Firebase, analytics, crash reporting or ad libraries. The release
  check script fails the build if any of them appear in the dependency tree.
- The app asks for two permissions only: internet access and network status.
- There is no Newz server. The only requests the app makes are to the publishers whose feeds
  you follow, identified by a plain User-Agent: `Newz/1.0 (+https://github.com/mack5imal/newz; RSS reader)`.

The browser you choose for opening articles has its own privacy policy; Newz uses whichever
browser is your default.

## Install

Newz is not in the Play Store. See **[INSTALL.md](INSTALL.md)** for a step-by-step guide to
building the APK and sideloading it on your phone (Android 10 or newer).

## Build

Requirements: a Mac or Linux machine with JDK 17 and the Android SDK (compileSdk 37).
`scripts/setup-android-toolchain.sh` installs both on macOS without admin rights.

```bash
bash scripts/make-release-key.sh          # once: creates your signing key in keystore/
./gradlew :androidApp:assembleRelease     # builds androidApp/build/outputs/apk/release/androidApp-release.apk
bash scripts/verify-release.sh            # checks signing, permissions and that no Google/tracker libraries are present
```

Back up the `keystore/` folder: Android only installs updates signed with the same key.

### Tests

```bash
./gradlew :shared:jvmTest                 # engine, store and parser tests
./gradlew ktlintCheck                     # formatting
bash scripts/run-instrumented-tests.sh    # UI tests: boots the newz-api34 emulator headless, runs them, shuts it down
```

The UI test run uninstalls and reinstalls the app, which wipes its data. Run it on a test
emulator, never on a phone you use.

## Project layout

| Path | What's there |
|---|---|
| `shared/` | Kotlin Multiplatform core: feed fetching and parsing, story grouping, ranking, filtering, storage (SQLDelight, DataStore), and `HostGuard` |
| `androidApp/` | The Android app: Jetpack Compose UI, screens and view models |
| `catalog/` | The outlet/feed catalog, the zip-to-metro table, and the data notices |
| `design/` | The design handoff: screen mockups, tokens and the HTML they were rendered from |
| `scripts/` | Toolchain setup, release key and checks, catalog and data builders |

Built with Kotlin 2.4, Compose Multiplatform, Ktor (OkHttp engine), SQLDelight, DataStore,
Coil 3 and [RSS-Parser](https://github.com/prof18/RSS-Parser).

## Data sources and credits

- Zip-code to metro table: US Census Bureau, public domain. See [catalog/ZIP-DATA-NOTICE.md](catalog/ZIP-DATA-NOTICE.md).
- Outlet popularity scores: derived from the Majestic Million, CC BY 3.0. See [catalog/POPULARITY-NOTICE.md](catalog/POPULARITY-NOTICE.md).
- Story-grouping test data: UCI News Aggregator Data Set, CC BY 4.0. See [shared/src/commonTest/resources/dedup/NOTICE.md](shared/src/commonTest/resources/dedup/NOTICE.md).
- Fonts: Big Shoulders Display and Schibsted Grotesk, SIL Open Font License 1.1 (texts in
  `androidApp/src/main/assets/licenses/`).

Outlet names and feeds belong to their publishers. Newz is not affiliated with any of them.

## Security

To report a security or privacy problem, see [SECURITY.md](SECURITY.md).

## License

LICENSE_TBD
