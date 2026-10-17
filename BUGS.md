# Newz bug log

Bugs and errors found while testing Newz on the emulator (`newz-api34`), started 2026-10-17.
Each entry is numbered and keeps its number for good. When the fix plan is made, entries move
from **Open** to **Fixed** with the commit that fixed them.

Severity: **High** blocks testing or loses data · **Medium** wrong behavior with a workaround ·
**Low** cosmetic.

## Open


## Fixed

### B18 — Wordmark animation only flashes now that loads are fast
- **Severity:** Low (regression from B17's speed-up)
- **Where:** `FlapWordmark` in `androidApp/src/main/kotlin/app/newz/ui/components/Flap.kt`
- **Seen:** on refresh the NEWZ tiles flicker for about a quarter of a second.
- **Cause:** the tiles animate only while `busy` is true and snap back to NEWZ the moment it turns
  false; a refresh now often finishes in well under a second, mid-cycle.
- **Requested:** always play at least one full cycle (letters flip until they spell NEWZ, ~2.2 s),
  and keep cycling for as long as the load lasts; finish the current cycle when it ends.
- **Also requested:** the "Refreshing your feed…" band should last as long as the animation.
- **Fixed in:** `0c527e5` (at least one full ~2.2 s cycle per load, keeps cycling while it runs, settles on NEWZ) and `81a6f7b` (the band shows from the pull until the tiles settle, sharing one state). With the phone's animations turned off, the tiles stay still and the band follows the refresh.

### B19 — Onboarding shows the app icon beside the wordmark
- **Severity:** Low
- **Where:** onboarding header in `androidApp/src/main/kotlin/app/newz/ui/onboarding/OnboardingScreen.kt`
- **Requested:** remove the app icon; show only the NEWZ flap tiles.
- **Fixed in:** `5c83638`.

### B20 — Article headlines a little too large
- **Severity:** Low
- **Where:** `NewzTokens.typeHeadline` (feed cards: 21 sp, 27 sp line height, bold)
- **Requested:** a little smaller; amount to be chosen by the owner.
- **Fixed in:** `05a16bc` — 18 sp on a 24 sp line (owner's choice); the story-group sheet title follows.

### B21 — Age tiles on the card strip too big next to the outlet name
- **Severity:** Low
- **Where:** `CardStrip` / `FlapAge` in `androidApp/src/main/kotlin/app/newz/ui/components/Cards.kt`
- **Seen:** age tiles are 24×32 dp (24 sp character); the outlet name is 18 sp signage.
- **Requested:** age tiles about the same size as the outlet name.
- **Fixed in:** `4df717d` — feed-card age tiles use the small 18×24 dp size.

### B17 — Wordmark flips forever and "Refreshing your feed…" never goes away
- **Severity:** High (feed never finishes loading; phone CPU at 100%, battery drain)
- **Where:** `shared/src/commonMain/kotlin/app/newz/dedup/StoryGrouper.kt` (`group`, `Building.similarity`),
  called from `FeedRepository.onProgress` → `publish` → `FeedComposer.compose`
- **Seen:** on the phone (Galaxy A71, release build, fresh install): the NEWZ tiles never stop
  flipping, and after pull to refresh "Refreshing your feed…" stays. Same with Better pictures
  on or off. Reproduced on the emulator with the release build after a fresh onboarding.
- **Cause (confirmed by thread dump):** the first load is still running. While a first load runs,
  the feed is recomposed every time a single feed lands, and each recompose re-groups *every*
  cached article for duplicates by comparing each article with every story group, recomputing
  each group's vector length on every comparison (roughly N² work for N articles). With a few
  thousand articles and ~100 feeds that is minutes of CPU per feed, so the load effectively never
  ends. The wordmark animates and the refresh band shows for as long as a load runs. Pull to
  refresh joins the load that is already running, so it never ends either. Tests use small feeds,
  so they never hit it.
- **Fix idea:** make grouping near-linear (keep each group's vector length up to date, only compare
  groups inside the 48-hour window, and only groups sharing a word with the article), and while a
  first load runs recompose at most every ~1 s instead of once per feed; add a performance test
  with thousands of articles.
- **Fixed in:** `39c479f` (story grouping ~8.5× faster on 5,000 articles, identical results and dedup scores) and `b1ada46` (at most one recompose a second during a first load). Emulator release build: fresh first load settles in ~10 s, pull to refresh ~7 s.

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
- **Resolved (2026-10-17):** on the phone, articles open in Brave's in-app tab, which works well. No in-app reader needed; no code change.

### B12 — "Change it in Settings" doesn't say where
- **Severity:** Medium
- **Where:** `FeedViewModel` show more/less message; Settings home's "Topics & keywords" row
  (`SettingsScreen.kt:102`)
- **Seen:** after Show more / Show less the message says "Change it in Settings", but nothing on
  the Settings screen mentions levels.
- **Cause:** the levels are on Settings › Topics & keywords (a Normal / More / Much more control
  under each followed topic, plus "Other topics you’ve boosted"). The message dropped the screen
  name to fit two lines, and the Settings row's subtitle only shows counts.
- **Fix idea:** message names "Settings › Topics" (fits); the Topics & keywords row subtitle
  mentions how often each topic appears.
- **Fixed in:** `79b70b7` — message now "{Topic}: {Level} (2× as often). Change it in Settings › Topics." (fits two lines for every built-in topic); the Topics & keywords row reads "{n} topics · set how often each appears".

### B13 — Clear reading history button too big
- **Severity:** Low
- **Where:** `SettingsScreen.kt:159`
- **Requested:** a smaller button, centred.
- **Fixed in:** `7ec560c` — compact button, centred, still a 48 dp touch target.

### B14 — Placeholder topic tiles are left-aligned
- **Severity:** Low
- **Where:** `NoImagePanel` / `TopicTiles` in `ui/components/Cards.kt`
- **Requested:** centre the tiles in the panel (screenshot 12:52).
- **Fixed in:** `1c65c69` — tiles centred across the panel.

### B15 — Use the flap-tile NEWZ as the top-bar wordmark
- **Severity:** Low (design change)
- **Where:** `AppBarMain` in `ui/components/AppBars.kt`; refresh indicator in `ui/feed/FeedScreen.kt`
- **Requested:** replace the plain "NEWZ" wordmark with the split-flap NEWZ tiles (matches the
  app icon), colours adjusted to suit the bar; the pull-to-refresh band shows a plain message
  such as "Refreshing feed…" instead of the tiles.
- **Fixed in:** `ade9b86` — NEWZ flap-tile wordmark (light tiles, navy letters) in the top bar and onboarding; it flips while the feed loads or refreshes; the pull band says "Refreshing your feed…".

### B16 — Theme setting (System / Light / Dark)
- **Severity:** Low (feature request)
- **Where:** `ui/theme/NewzTheme.kt` (follows the system today), Settings, `UserPrefs`
- **Requested:** a Settings choice of System, Light or Dark.
- **Fixed in:** `35e0a5f` — Settings › Reading › Theme: System / Light / Dark, applied at once (also article tabs and nav-bar icons).

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
- **Fixed in:** `3ebf8d1` (pictures under 300 px wide show the flap tiles) and `5a05e9c` (opt-in Settings › Reading › Better pictures from article pages, off by default; reads the start of the page for `og:image`). `73324fe` asks servers for only the first 64 KB (`Range`), with fallbacks: range ignored → stop reading at 64 KB; 416/400/403 → one retry without it; a cut-off compressed reply → use what decoded.

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
