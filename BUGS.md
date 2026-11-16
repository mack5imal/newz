# Newz bug log

Bugs and errors found while testing Newz on the emulator (`newz-api34`), started 2026-10-17.
Each entry is numbered and keeps its number for good. When the fix plan is made, entries move
from **Open** to **Fixed** with the commit that fixed them. Entries B23 onwards come from the
November 2026 code review (`reviews/2026-11-code-review.md`); each names its review ID.

Severity: **High** blocks testing or loses data · **Medium** wrong behavior with a workaround ·
**Low** cosmetic.

## Open

## Fixed

### B47 — Feed scroll position and screen state lost on every navigation (R12)
- **Severity:** Medium
- **Where:** `androidApp/.../ui/nav/Navigation.kt` (`NavHost`)
- **Seen:** in review. Scrolling 60 cards down, opening Saved and pressing Back lands at the top.
- **Fixed in:** `7927242` (each back-stack entry has its own SaveableStateHolder slot; scroll and screen state come back on Back).

### B48 — Landscape: app bars and settings ignore side insets (R13)
- **Severity:** Medium
- **Where:** `androidApp/.../ui/components/AppBars.kt`, `SettingsScreen.kt` (`ScreenFrame`), `NewzApp`
- **Seen:** in review. With 3-button navigation on the right, Settings and Remove buttons sit under
  the nav bar; the back arrow sits under the camera cutout.
- **Fixed in:** `6cd47bd` (horizontal safe-drawing insets once at the app root; the feed list keeps only its bottom inset).

### B49 — Backup "include history" choice lost if the process dies in the file picker (R21)
- **Severity:** Low
- **Where:** `androidApp/.../settings/BackupViewModel.kt`
- **Seen:** in review. The backup is then written without history, silently.
- **Fixed in:** `c7604b8` (the choice lives in the screen's saved state and is passed to export).

### B50 — Onboarding picks lost on process death (R22)
- **Severity:** Low
- **Where:** `androidApp/.../onboarding/OnboardingViewModel.kt`
- **Seen:** in review. Switching apps to look up a zip code can lose every pick.
- **Fixed in:** `eca274a` (the screen saves the picks and hands them to a new view model after process death).

### B51 — Keyword-mute and zip sheets don't scroll; the keyboard hides their buttons (R29)
- **Severity:** Low
- **Where:** `androidApp/.../feed/KeywordMuteSheet.kt`, `settings/ZipCodeSheet.kt`
- **Seen:** in review. In landscape or at large text the Mute/Save row is cut off.
- **Fixed in:** `1396a65` (both sheets cap their height, scroll their body and keep the buttons above the keyboard).

### B52 — TalkBack can reach the screen behind an open sheet (R30)
- **Severity:** Low
- **Where:** `androidApp/.../components/Sheets.kt` (`BottomSheet`), `FeedScreen.kt`
- **Seen:** in review. Swiping past the last sheet action moves focus to the feed behind it.
- **Fixed in:** `1282209` (content behind an open sheet is cleared from semantics; the sheet is a traversal group) and `2155178` (test update).

### B53 — The Undo snackbar ignores the accessibility timeout (R31)
- **Severity:** Low
- **Where:** `androidApp/.../components/Feedback.kt`
- **Seen:** in review. It always closes after 6 s.
- **Fixed in:** `4153c0f` (LocalAccessibilityManager's recommended timeout).

### B54 — Card menu, keyword and story-group sheets close on rotation (R32)
- **Severity:** Low
- **Where:** `androidApp/.../feed/FeedScreen.kt`
- **Seen:** in review. Typed keyword text is lost on rotation, dark-mode change or text size change.
- **Fixed in:** `ad48186` (sheet state in rememberSaveable; the story group kept by its lead id).

### B55 — Saved composes every saved article at once (R33)
- **Severity:** Low
- **Where:** `androidApp/.../saved/SavedScreen.kt`
- **Seen:** in review. A few hundred saved articles all compose and load thumbnails together.
- **Fixed in:** `e3a8a80` (LazyColumn keyed by article id, through LazyScreenFrame).

### B56 — Saved's offline banner only updates when the feed changes (R34)
- **Severity:** Low
- **Where:** `androidApp/.../saved/SavedViewModel.kt`
- **Seen:** in review. The banner stays after Wi-Fi returns, and never appears on going offline.
- **Fixed in:** `1f82705` (a ConnectivityManager callback flow re-checks the network).

### B57 — Screen view models live as long as the activity (R35)
- **Severity:** Low
- **Where:** `androidApp/.../MainActivity.kt`, `ui/nav/Navigation.kt`
- **Seen:** in review. A Sources snackbar reappears on Settings; Saved keeps collecting when closed.
- **Fixed in:** `7927242` (a ViewModelStore per back-stack entry, cleared when the entry is popped).

### B58 — Toolchain script installs an unpinned JDK and edits the global Gradle config (R36)
- **Severity:** Low
- **Where:** `scripts/setup-android-toolchain.sh`
- **Seen:** in review. Downloads Adoptium's moving `latest/17` without a checksum and writes
  `org.gradle.java.home` into `~/.gradle/gradle.properties`.
- **Fixed in:** `89a954e` (pinned Temurin release; daemon JDK from gradle/gradle-daemon-jvm.properties, nothing written to ~/.gradle) and `6d2f423` (SHA-256 check against Adoptium's published checksum).

### B35 — Full-text summaries merge unrelated stories into one card and slow grouping (R7)
- **Severity:** Medium
- **Where:** `shared/.../feed/FeedParser.kt` (`toItem`), `dedup/StoryGrouper.kt`
- **Seen:** in review. Summaries are stored uncapped; 300-word bodies collapse a 5,000-article feed
  into 65 groups and grouping takes six times longer.
- **Fixed in:** `bbbca8a` (summaries capped at 400 characters; grouping reads the headline and the first 40 summary words).

### B36 — ISO dates with +0000, no seconds or a space before the offset fail to parse (R8)
- **Severity:** Medium
- **Where:** `shared/.../feed/FeedDates.kt` (`parseIso`, `parseRfc822`)
- **Seen:** in review. Such items get "now" as their date, so weeks-old stories show as new.
- **Fixed in:** `e6f3b1f` (one ISO pattern for +0000, +05, no seconds, a spaced offset and a trailing comment).

### B37 — Unknown RFC 822 zone abbreviations are read as UTC (R9)
- **Severity:** Medium
- **Where:** `FeedDates.kt` (`zoneOffsetSeconds`)
- **Seen:** in review. AEDT, BST, CET and others parse as +00:00, so items land hours off.
- **Fixed in:** `e6f3b1f` (unknown zone abbreviations give no date; common non-US zones added, IST left out as ambiguous).

### B38 — A refresh that joins a running load skips feeds followed after it started (R10)
- **Severity:** Medium
- **Where:** `shared/.../engine/RefreshCoordinator.kt` (`startOrJoin`, `load`)
- **Seen:** in review. Following an outlet during the start-up load shows nothing from it until a
  manual pull.
- **Fixed in:** `5f5fc31` (a joined load widens to every source followed by the time it finishes fetching).

### B39 — Ranking is quadratic when one outlet dominates the feed (R11)
- **Severity:** Medium
- **Where:** `shared/.../engine/FeedRanker.kt` (`rank`)
- **Seen:** in review. 12,000 groups with 80% from one outlet take about a second per compose.
- **Fixed in:** `3975bc1` (per-outlet window counts and an outlet-indexed queue; same order, checked against the original; ~65 ms for 12,000 groups).

### B40 — A tracking pixel can become the card picture (R15)
- **Severity:** Low
- **Where:** `FeedParser.kt` (`imageOf`)
- **Seen:** in review. The first `<img>` is used even when it is a 1x1 beacon.
- **Fixed in:** `897a088` (images declared at 2 px or less, or with beacon-like addresses, are skipped, also as RSS-Parser's item image).

### B41 — Items sharing one guid collapse into a single card (R18)
- **Severity:** Low
- **Where:** `FeedParser.kt` (`parse`), `store/StoreModels.kt` (`ArticleIds`)
- **Seen:** in review. A feed that gives every item the same guid shows one card.
- **Fixed in:** `bdffe27` (a guid shared by items with different links falls back to each link).

### B42 — Future-dated articles sit at the top and are never pruned (R23)
- **Severity:** Low
- **Where:** `store/ArticleStore.kt` (`upsert`), `filter/FeedFilter.kt`, `Article.sq`
- **Seen:** in review. An item dated next year ranks as "now" until a week after that date.
- **Fixed in:** `5f39873` (dates more than 15 min ahead are stored as undated; prune uses the earlier of the two dates).

### B43 — Muting "Tim" hides "times" (R24)
- **Severity:** Low
- **Where:** `shared/.../filter/KeywordMatcher.kt` (`ENDINGS`)
- **Seen:** in review. The "es" ending is allowed after any letter.
- **Fixed in:** `a5e3251` ("es" only after s, x, z, ch, sh or o, and not after a two-letter word in s).

### B44 — `<!ENTITY` quoted inside CDATA rejects the whole feed (R25)
- **Severity:** Low
- **Where:** `FeedParser.kt` (`DECLARATION`)
- **Seen:** in review. The declaration check scans the whole document, CDATA included.
- **Fixed in:** `d6c3161` (declarations are looked for only as markup, outside comments and CDATA) and `7700670` (the scan compares strings instead of matching a regex at each tag, which was slow on Android).

### B45 — A pull during a finishing load's last compose loses its re-rank (R26)
- **Severity:** Low
- **Where:** `shared/.../engine/FeedRepository.kt` (`onLoaded`, `refresh`)
- **Seen:** in review. The pull joins a load that has already read the re-rank flag; the flag
  leaks into the next load.
- **Fixed in:** `5f5fc31` (a request after a load's fetching is over queues a new load instead of joining).

### B46 — An older feed composition can overwrite a newer one (R27)
- **Severity:** Low
- **Where:** `FeedRepository.kt` (`publish`)
- **Seen:** in review. Composes finish in any order, so a hidden card can reappear.
- **Fixed in:** `1ce20fe` (snapshots carry their read order; an older publish keeps the shown cards but still updates the state).

### B29 — Importing a backup silently deletes reading history and recently saved articles (R4)
- **Severity:** High (data loss)
- **Where:** `shared/.../store/BackupCodec.kt` (`BackupRestorer.restore`),
  `androidApp/.../settings/BackupScreen.kt`
- **Seen:** in review. Export leaves history out by default, and import of such a file empties the
  history. Import starts as soon as the file is picked, with no confirmation, and the warning does
  not mention saved articles or history.
- **Fixed in:** `c2112e8` (a confirm sheet lists what the restore replaces; a backup without history keeps the phone's history) and the UI-test update after it.

### B30 — Restoring a backup never fetches the restored sources (R5)
- **Severity:** Medium
- **Where:** `androidApp/.../settings/BackupViewModel.kt` (`import`)
- **Seen:** in review. After a restore on a new phone, the feed shows only the onboarding sources
  until a manual pull.
- **Fixed in:** `fbb9400` (a restore refreshes and re-ranks the feed).

### B31 — A story in two sections of one outlet keeps the first section's feed and topics (R6)
- **Severity:** Medium
- **Where:** `shared/.../store/ArticleStore.kt` (`upsert`), `Article.sq`, `filter/FeedFilter.kt`
- **Seen:** in review. Following nyt-world and nyt-us, then unfollowing World hides a story that
  nyt-us still carries.
- **Fixed in:** `013e0ba` (article_feed table, migration 2 -> 3; an article shows through the first of its feeds the filter lets through).

### B32 — A damaged preferences file crashes the app on every launch (R19)
- **Severity:** Low
- **Where:** `shared/.../store/PreferencesStore.kt`
- **Seen:** in review. No DataStore corruption handler; the only way out is clearing app data.
- **Fixed in:** `30a14d6` (DataStore corruption handler resets to defaults; onboarding says the settings could not be read).

### B33 — An interrupted restore can pair the backup's preferences with the old tables (R20)
- **Severity:** Low
- **Where:** `BackupCodec.kt` (`BackupRestorer.restore`), `BackupViewModel.kt`
- **Seen:** in review. Preferences are replaced before the tables, and the restore runs in the
  screen's scope.
- **Fixed in:** `048cd55` (tables first, prefs last with the tables put back if the prefs write fails; the restore runs to the end in the app's scope).

### B34 — Reading history is never pruned and is re-read in full on every recompose (R28)
- **Severity:** Low
- **Where:** `History.sq`, `engine/RefreshCoordinator.kt`
- **Seen:** in review. History grows without bound and every feed compose reads all of it.
- **Fixed in:** `cdb7866` (history older than 180 days is pruned at each load; openedAt is indexed).

### B23 — HostGuard and OkHttp read hosts differently, so disguised Google hosts get through (R1)
- **Severity:** High (breaks the no-Google promise)
- **Where:** `shared/src/commonMain/kotlin/app/newz/net/HostGuard.kt` (`normalizeHost`),
  `Engine.android.kt`, `Engine.jvm.kt`
- **Seen:** in review. `xn--google-.com`, `ⓖoogle.com`, a soft hyphen or zero-width space inside
  `google`, and `blogſpot.com` all pass `isBlocked`, but OkHttp's UTS-46 mapping turns them into
  the real Google host and connects.
- **Cause:** HostGuard maps only fullwidth ASCII and never decodes existing `xn--` labels; the
  engine applies full IDNA mapping after the check.
- **Fixed in:** `8cecf94` (HostGuard decodes punycode and also judges OkHttp's own canonical host; the OkHttp engine, now one file for jvm and android, checks every hop in interceptors and DNS).

### B24 — Blocked-host list misses Google ad, analytics, Blogger, Firebase and AMP domains (R2)
- **Severity:** Medium
- **Where:** `HostGuard.kt` (`GOOGLE_HOSTS`)
- **Seen:** in review. 2mdn.net, googleadservices.com, app-measurement.com, blogblog.com,
  web.app, page.link, cdn.ampproject.org and others are not blocked.
- **Fixed in:** `795a1de` (22 more Google domains blocked).

### B25 — Feed items that link to Google hosts are kept and open in the browser (R3)
- **Severity:** Medium
- **Where:** `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt` (`toItem`),
  `androidApp/.../platform/ArticleOpener.kt`, `ShareLauncher.kt`
- **Seen:** in review. A `feedproxy.google.com` item link shows as a normal card and tapping it
  opens Google.
- **Fixed in:** `f0a5928` (the parser drops items whose link is blocked; ArticleOpener and ShareLauncher refuse blocked hosts too).

### B26 — IP-literal hosts pass HostGuard (R14)
- **Severity:** Low
- **Where:** `HostGuard.kt` (`isBlockedHost`)
- **Seen:** in review. `https://142.250.80.46/x.jpg` and `https://[::ffff:8.8.8.8]/` are fetched.
- **Fixed in:** `3890527` (IP-address hosts are refused, loopback aside; a typed IP is "not an address").

### B27 — Image downloads have no size cap (R16)
- **Severity:** Low
- **Where:** `androidApp/src/main/kotlin/app/newz/ui/image/NewzImageLoader.kt`
- **Seen:** in review. A feed naming a 300 MB image makes each card download for up to 15 s.
- **Fixed in:** `e2ec90d` (the image client's engine fails a body past 10 MB, declared or not).

### B28 — Autodiscovery stops at a Google feed even when the site declares its own (R17)
- **Severity:** Low
- **Where:** `shared/src/commonMain/kotlin/app/newz/discover/FeedDiscovery.kt` (`feedLink`)
- **Seen:** in review. A site that lists feeds.feedburner.com first and `/feed/` second shows the
  Google refusal.
- **Fixed in:** `3143d07` (every declared feed is tried; Google is refused only when all are Google's).

### B22 — After a refresh, old articles sit at the top; new ones are scattered down the feed
- **Severity:** Medium
- **Where:** `shared/src/commonMain/kotlin/app/newz/engine/FeedPager.kt` (`submit`),
  `FeedRanker.kt` (`rank`), `FeedRepository.kt` (`onLoaded` → `publish(reset = …)`)
- **Seen:** on the phone after a few weeks of use: after refreshing, many cards near the top are
  old, and the newer articles are spread through the whole list.
- **Requested:** new articles at the top. Keep mixing sources, but order mainly by recency.
- **Likely cause (two parts):**
  1. A refresh over cards already on screen never re-ranks: `FeedPager.submit` keeps every
     served card (the first 20) in place and queues new articles behind them, so fresh stories
     start at card 21 or later. The app's cache-first start does the same: it shows the cached
     (older) cards, then merges the load's new articles behind them.
  2. The ranker interleaves topic queues by stride (Normal 1, More 2, Much more 3), so every topic
     gets its turn near the top however old its newest article is: a quiet topic's day-old story
     sits between minutes-old ones.
- **Fixed in:** `e5ef8c0` (recency bands stretched by topic level), `d5387bf` (pull to refresh
  re-ranks; loads over shown cards count new stories), `1e31e54` + `51e074f` (New stories button
  below the chips; tap re-ranks and scrolls up), `4cf2029` (count only stories that reach the
  first page).

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
