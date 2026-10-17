# Newz bug log

Bugs and errors found while testing Newz on the emulator (`newz-api34`), started 2026-10-17.
Each entry is numbered and keeps its number for good. When the fix plan is made, entries move
from **Open** to **Fixed** with the commit that fixed them.

Severity: **High** blocks testing or loses data · **Medium** wrong behavior with a workaround ·
**Low** cosmetic.

## Open

### B9 — Opened article has no easy way back to Newz
- **Severity:** Medium (feature request)
- **Where:** `androidApp/src/main/kotlin/app/newz/platform/ArticleOpener.kt`
- **Seen:** tapping a card opened the article in a separate browser ("WebView Browser Tester");
  the only way back was the phone's Back button (screenshot 9:35).
- **Cause:** Newz opens articles in a Custom Tab when the default browser supports it, which
  shows a ✕ close button and the Newz colours; otherwise it hands the link to whatever browser
  is installed. The emulator has no Custom Tabs browser, so it fell back to the bare test browser.
  A real phone with Firefox or another Custom Tabs browser would show a Custom Tab, but only
  if one is installed.
- **Requested:** show the article in a frame inside Newz, with a clear way back.
- **Fix idea:** an in-app reader screen (Android WebView) with a Newz top bar (back, title,
  share, open in browser). Privacy check needed: switch off WebView's Safe Browsing, which
  contacts Google; no third-party cookies; same blocked-host rules as the feed.
- **Status:** deferred (2026-10-17). Kept Custom Tabs; to be tried on a real phone and then decide on an in-app reader.

## Fixed

### B2 — Running the UI tests overwrites the device's real settings
- **Severity:** Medium (test tooling; the app itself is fine)
- **Where:** `androidApp/src/androidTest/kotlin/app/newz/testing/TestApp.kt` (`TestApp.install`)
- **Seen:** running the full instrumented suite on the emulator replaced the onboarding choices
  with test values, so the feed showed "Nothing to show — your filters hide everything".
- **Cause:** the tests use a throwaway in-memory database but write preferences to the app's
  real `files/newz.preferences_pb`.
- **Fix idea:** give tests their own preferences file (or in-memory store), like the database.
- **Recovery used:** `adb shell am force-stop app.newz` then
  `adb shell run-as app.newz rm files/newz.preferences_pb`, and onboard again.
- **Fixed in:** `d3fc1c7` — UI tests use their own `newz-test.preferences_pb`; the real file's hash is unchanged after full suite runs.

### B3 — Topic chip filter only searches stories already loaded (to confirm)
- **Severity:** Medium if confirmed
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/feed/FeedViewModel.kt` (`FeedState.filtered`);
  the unused shared path is `FeedRepository.setTopicFilter`
- **Seen:** not yet; carried over from the Relay run's deferred cleanup item (FINDINGS.md).
- **Suspected:** a narrow topic shows only the few matching stories in pages already loaded,
  and only matches a group's lead article. The shared engine has a second, unused filter that
  covers the whole cache, and the topic filter tests test that one.
- **To confirm:** tap a narrow chip before scrolling, then again after scrolling far down;
  more results the second time means the bug is real.
- **Resolved:** not a user bug — the feed keeps paging until the chip's topic turns up or the feed ends (tested). The unused duplicate filter in the shared engine was removed in `3e06168`. Still true: the chip matches a grouped story by its lead article only.

### B4 — No-image placeholder word is cut off; some articles show no image
- **Severity:** Low (looks broken, though it is by design)
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/components/Cards.kt` (`NoImagePanel` /
  `CroppedWord`); image choice in `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt` (`imageOf`)
- **Seen:** Seeking Alpha card shows "MARKETS" in huge letters with the bottom sliced off (screenshot 9:25).
- **Cause:** the crop is deliberate in the approved design (the word's baseline sits 26 dp below
  the panel's bottom edge), but in use it reads as a broken image. The articles showing it most
  likely come from feeds with no image at all; not yet confirmed that no image failed to load.
- **Fix idea:** redesign the placeholder (e.g. the whole word shown smaller, or an outlet/topic
  tile) and check whether any no-image cards actually had an image that failed to load.
- **Fixed in:** `6c3d108` — the topic is spelled in flap tiles, for cards with no picture and for pictures that fail to load.

### B5 — Some images are pixelated
- **Severity:** Medium
- **Where:** `FeedParser.imageOf` (`shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt`)
- **Seen:** Phys.org vulture image is blocky (screenshot 9:25).
- **Cause (confirmed):** Phys.org's feed only offers a 90×90 `media:thumbnail`
  (`…/csz/news/tmb/…jpg`), which is stretched to full card width. The parser takes the first
  image it finds and ignores its declared size.
- **Fix idea:** prefer the largest declared image; treat images under ~300 px wide as too small
  (fall back to the placeholder, or to the article page's `og:image` from the same publisher);
  possibly a per-outlet URL rewrite for known thumbnail paths.
- **Fixed in:** `3ebf8d1` (pictures under 300 px wide show the flap tiles) and `5a05e9c` (opt-in Settings › Reading › Better pictures from article pages, off by default; reads the start of the page for `og:image`).

### B6 — Photos are cropped badly (heads cut off)
- **Severity:** Medium
- **Where:** `ArticleImage` in `androidApp/src/main/kotlin/app/newz/ui/components/Cards.kt`
- **Seen:** New York Post portrait photo shows only the torso (screenshot 9:26).
- **Cause:** every image is cropped to fill a fixed 16:9 frame from its centre
  (`ContentScale.Crop`, centred). Tall photos lose the top, which is usually where faces are.
- **Fix idea:** crop from the top third for portrait images, or show tall images letterboxed or
  at a taller ratio.
- **Fixed in:** `c59ecae` — photos crop from their upper part (feed cards and Saved thumbnails).

### B7 — Pull-to-refresh "NEWZ" tiles sit on top of the topic chips
- **Severity:** Low
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/feed/FeedScreen.kt` (the `PullToRefreshBox`
  `indicator`, ~line 145)
- **Seen:** after pulling down, the four NEWZ tiles draw over the WORLD / US / SCIENCE chips
  (screenshot 9:28).
- **Cause:** the indicator is pinned to the top of the refresh area with a fixed 12 dp gap and
  no background, and the topic chip row is inside that same area, so the tiles overlap the chips.
- **Fix idea:** put the indicator below the chip row (or give it a backing panel), and move it
  with the pull.
- **Fixed in:** `a0d3d48` — the tiles slide down in their own band and push the chips and feed down.

### B8 — "Clear reading history" clears without asking
- **Severity:** Medium (one tap loses data)
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/settings/SettingsScreen.kt:154`
  → `SettingsViewModel.clearHistory`
- **Requested:** a confirmation step before clearing, since the button is large and easy to hit.
- **Fix idea:** a confirm sheet ("Clear reading history?" / Clear / Cancel), matching the app's
  other sheets; keep the "Reading history cleared" snackbar afterwards.
- **Fixed in:** `74e9795` — a confirm sheet (Cancel / Clear history) comes first.

### B10 — "Show more like this" can't be undone or lowered, and gives no feedback
- **Severity:** Medium (design change; discuss behaviour before fixing)
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/feed/CardMenuSheet.kt`,
  `FeedViewModel.showMore`; levels in `shared/.../store/UserPrefs.kt` (`Level`: Normal, More, Much more)
- **Seen:** tapped Show more like this on a US story and nothing visible happened; reopening the
  menu showed it disabled: "Already showing much more of US" (screenshot 9:53).
- **Notes:** each tap raises the topic one level, and the action does post "Showing more like
  this" with Undo, but that message didn't appear (see B11). It is unclear whether US was
  already at More before the tap, or one tap went straight to Much more; check. The menu has
  no "Show less"; Settings › Topics & keywords already has a Normal / More / Much more control,
  but only for followed topics.
- **Requested:** a way to undo or step back down from the menu; a short confirmation; show more
  or less controls in Settings. Behaviour to be agreed when we get to it.
- **Fixed in:** `561a293` — menu shows the current level and Show more / Show less; levels explained as "about 2× / 3× as often"; Settings › Topics & keywords lists other boosted topics.

### B11 — Card menu actions give no visible message
- **Severity:** Medium
- **Where:** `Snackbar` in `androidApp/src/main/kotlin/app/newz/ui/components/Feedback.kt` and
  its host in `androidApp/src/main/kotlin/app/newz/ui/feed/FeedScreen.kt` (~line 189)
- **Seen:** after Show more like this, no message appeared.
- **Likely cause:** every menu action already posts a message with Undo ("Article hidden",
  "Showing more like this", "New York Post won’t appear in your feed", "Muted “…”", …), but
  the app draws edge to edge and the message bar has no space reserved for the phone's
  navigation bar, so with 3-button navigation it is hidden behind the buttons. To confirm.
- **Requested:** every menu action shows a short message saying what happened and where to undo
  it later, e.g. "Article hidden. Unhide it in Settings › Hidden." (wording to be written).
- **Fixed in:** `b34b116` — messages sit above the navigation bar and say where to undo; Show more/less wording in `561a293`.

### B1 — Card header strip as tall as a photo; outlet name stacked one letter per line
- **Severity:** High (every feed card; also the loading board covered the topic chips)
- **Cause:** `FlapTile`'s split rule filled the row's width, so each age tile stretched across
  the strip and squeezed the outlet name to one letter wide.
- **Fixed in:** `e2c8a05` (tiles sized to their character; regression test in
  `ComponentGalleryTest`).
