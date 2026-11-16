# Newz code review — November 2026

This is the first full code review of the Newz codebase. Five reviewers each took one slice in parallel: Network & privacy; Parsing & content; Engine; Storage & backup; and Android UI & build. Each reviewer read its slice against README.md, BUGS.md and CLAUDE.md, and treated past fixes B1–B22 as intended behaviour. A second agent then tried to disprove every finding by reading the code, and in some cases by running scratch harnesses against the compiled `shared` jvm classes and the cached OkHttp/Ktor jars. No network calls were made and no device or emulator was used. The verifiers refuted one finding, which was dropped. Every finding below was either confirmed or rated plausible, and each comes with file:line evidence and a concrete failure scenario. Two findings that two slices reported independently were merged (R6, R23). No code was changed, committed or pushed.

## Summary

Unique findings by severity and primary slice (after merging the duplicates):

| Slice | High | Medium | Low | Total | Dropped by verifier |
|---|---:|---:|---:|---:|---:|
| Network & privacy | 1 | 2 | 3 | 6 | 0 |
| Parsing & content | 0 | 3 | 5 | 8 | 0 |
| Engine | 0 | 2 | 2 | 4 | 1 |
| Storage & backup | 0 | 3 | 3 | 6 | 0 |
| Android UI & build | 0 | 2 | 10 | 12 | 0 |
| **Total** | **1** | **12** | **23** | **36** | **1** |

Reviewers kept 38 findings in total. Two pairs were duplicates: the stale feedId/topics finding (Parsing Low and Storage Medium, merged as R6 at Medium) and the future-dated articles finding (Parsing Low and Storage Low, merged as R23). That leaves 36 unique findings. Confidence: 31 confirmed, 5 plausible (R3, R7, R19, R20, R27).

| ID | Sev | Conf | Slice | Title |
|---|---|---|---|---|
| R1 | High | confirmed | Network & privacy | HostGuard canonicalises hosts differently from OkHttp; xn--/UTS-46 hosts reach Google |
| R2 | Medium | confirmed | Network & privacy | Google domain list misses ad, analytics, Blogger, Firebase and AMP hosts |
| R3 | Medium | plausible | Network & privacy | Article links on Google hosts are stored and opened without a HostGuard check |
| R4 | Medium | confirmed | Storage & backup | Backup import silently deletes history and recently saved articles |
| R5 | Medium | confirmed | Storage & backup | Restoring a backup never fetches the restored sources |
| R6 | Medium | confirmed | Storage & backup, Parsing & content | Article keeps the first section's feedId/topics; unfollowing it hides the story |
| R7 | Medium | plausible | Parsing & content | Uncapped summaries collapse unrelated stories and slow grouping |
| R8 | Medium | confirmed | Parsing & content | ISO 8601 basic offset, no-seconds and space-offset dates fail to parse |
| R9 | Medium | confirmed | Parsing & content | Unknown RFC 822 zone abbreviations are read as UTC |
| R10 | Medium | confirmed | Engine | A refresh that joins a running load skips newly followed feeds |
| R11 | Medium | confirmed | Engine | FeedRanker spread fallback is quadratic when one outlet dominates |
| R12 | Medium | confirmed | Android UI & build | Feed scroll position and screen state lost on navigation |
| R13 | Medium | confirmed | Android UI & build | Landscape: app bars and settings ignore side nav-bar and cutout insets |
| R14 | Low | confirmed | Network & privacy | IP-literal hosts pass HostGuard |
| R15 | Low | confirmed | Parsing & content | First `<img>` (possibly a tracking pixel) becomes the card picture |
| R16 | Low | confirmed | Network & privacy | Image downloads have no byte cap |
| R17 | Low | confirmed | Network & privacy | Autodiscovery gives up when the first declared feed is Google's |
| R18 | Low | confirmed | Parsing & content | Items sharing one guid collapse into a single row |
| R19 | Low | plausible | Storage & backup | Corrupt preferences file crashes the app on every launch |
| R20 | Low | plausible | Storage & backup | Interrupted restore can pair the backup's prefs with the old tables |
| R21 | Low | confirmed | Android UI & build | Backup "include history" lost if the process dies during the picker |
| R22 | Low | confirmed | Android UI & build | Onboarding picks lost on process death |
| R23 | Low | confirmed | Parsing & content, Storage & backup | Future-dated articles pinned at the top and never pruned |
| R24 | Low | confirmed | Parsing & content | Keyword "es" ending over-matches (Tim/times, rat/rates) |
| R25 | Low | confirmed | Parsing & content | `<!ENTITY` inside CDATA rejects the whole feed |
| R26 | Low | confirmed | Engine | A pull during the final compose loses its re-rank, and the flag leaks |
| R27 | Low | plausible | Engine | Concurrent publishes unordered; an older snapshot can overwrite a newer one |
| R28 | Low | confirmed | Storage & backup | Reading history never pruned and fully re-read on every recompose |
| R29 | Low | confirmed | Android UI & build | Keyword-mute and zip sheets don't scroll; buttons pushed off-screen by the IME |
| R30 | Low | confirmed | Android UI & build | BottomSheet leaves the screen behind it reachable by TalkBack |
| R31 | Low | confirmed | Android UI & build | Undo snackbar ignores the accessibility timeout |
| R32 | Low | confirmed | Android UI & build | Card menu, keyword and story-group sheets close on rotation |
| R33 | Low | confirmed | Android UI & build | Saved list composes every saved article at once |
| R34 | Low | confirmed | Android UI & build | Saved offline banner only updates when the feed emits |
| R35 | Low | confirmed | Android UI & build | All screen ViewModels are activity-scoped and never cleared |
| R36 | Low | confirmed | Android UI & build | Toolchain script installs an unpinned JDK and edits global Gradle config |

## High

### R1 — HostGuard canonicalises hosts differently from OkHttp: UTS-46-mapped characters and ASCII `xn--` labels reach Google
- **Slice:** Network & privacy
- **Location:** `shared/src/commonMain/kotlin/app/newz/net/HostGuard.kt:68` (`normalizeHost`, lines 68–89)
- **What's wrong:** `normalizeHost` maps only fullwidth ASCII and the three ideographic full stops. It then punycode-encodes any non-ASCII label as it is, and never decodes labels that are already `xn--`. OkHttp 5.5.0, the engine on JVM and Android, percent-decodes the host and applies full UTS-46 mapping (`idnToAscii`). That mapping folds compatibility characters (ⓖ, 𝐠, ſ, ᴹ), drops ignorable characters (U+00AD, U+200B) and decodes existing punycode (`xn--google-` becomes `google`). `guardedExecute` (NewzHttpClient.kt:113) and `HostGuardInterceptor` (NewzImageLoader.kt:75) check HostGuard's idea of the host, but OkHttp connects to its own canonical host.
- **Failure scenario:** Verified against the compiled jvm classes and the cached OkHttp 5.5.0 / Ktor 3.6.0 jars, using a recording `Dns` and no network. `isBlocked` returned false for `https://xn--google-.com/rss`, `https://feeds.xn--feedburner-.com/x`, `https://www.xn--youtube-.com/`, `https://ⓖoogle.com/`, `https://goo­gle.com/` (soft hyphen), `https://www.go​ogle.com/` (ZWSP), `https://x.blogſpot.com/`, `https://x.blog%C5%BFpot.com/a.jpg` and `https://feedburner.coᴹ/`. In each case `fetchCapped` then resolved the real Google name (google.com, feeds.feedburner.com, i.ytimg.com, x.blogspot.com). A hostile or compromised feed can do this using only ASCII. The ways in are an item image, a redirect `Location`, a discovered `<link rel=alternate>`, or an og:image when Better pictures is on. Typed addresses are not affected, because AddressInput rejects these inputs.
- **Suggested fix:** Check the host the engine will actually use. In `platformHttpEngine`, add an OkHttp application interceptor plus a network interceptor (which runs on every hop) that calls `HostGuard.isBlockedHost(chain.request().url.host)`, or wrap `Dns` so it refuses blocked names. In HostGuard itself, canonicalise the way the engine does: an expect/actual around `toHttpUrlOrNull()?.host` on JVM/Android. Failing that, decode every `xn--` label, apply full UTS-46 mapping, and block any host whose punycode round-trip differs. Add jvmTests that compare HostGuard with `HttpUrl.host` on these inputs.
- **Confidence:** confirmed
- **Evidence:** jshell with okhttp-jvm-5.5.0: `HttpUrl.parse("https://xn--google-.com").host()` = `google.com`, and likewise for every input above. HostGuard.kt:87 leaves all-ASCII labels unchanged.

## Medium

### R2 — Google-owned domain list misses ad, analytics, Blogger, Firebase and AMP-cache hosts
- **Slice:** Network & privacy
- **Location:** `shared/src/commonMain/kotlin/app/newz/net/HostGuard.kt:13` (GOOGLE_HOSTS, lines 13–42)
- **What's wrong:** GOOGLE_HOSTS and the google.<TLD> rule leave out many Google-operated domains that feed images, ad images and links do point at.
- **Failure scenario:** `isBlockedHost` returns false for s0.2mdn.net, googleadservices.com, googletagservices.com, app-measurement.com, img1.blogblog.com, ghs.googlehosted.com, gmail.com, googlemail.com, 1e100.net, redirector.gvt1.com, x.page.link, x.web.app, x.firebasestorage.app, www.recaptcha.net, www.gstatic.cn, fonts.googleapis.cn, *.cdn.ampproject.org, doubleclick.com, admob.com and youtubekids.com. A feed whose first `<img>` is a 2mdn.net ad creative makes the image loader fetch it from DoubleClick, even though the README promises that DoubleClick and Firebase are blocked.
- **Suggested fix:** Add 2mdn.net, doubleclick.com, googleadservices.com, googletagservices.com, app-measurement.com, admob.com, blogblog.com, googlehosted.com, 1e100.net, gvt1/2/3.com, page.link, web.app, firebasestorage.app, recaptcha.net, gstatic.cn, googleapis.cn, googleusercontent.cn, cdn.ampproject.org, ampproject.net, gmail.com, googlemail.com and youtubekids.com. Consider moving the list into one data file with its own test.
- **Confidence:** confirmed
- **Evidence:** `isBlockedHost` (lines 56–61) matches by suffix only, and none of these suffixes are in the list or in GOOGLE_BRAND_TLDS (lines 132–145).

### R3 — Article links on Google hosts are stored and opened in the browser without a HostGuard check
- **Slice:** Network & privacy
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt:117`; also `androidApp/src/main/kotlin/app/newz/platform/ArticleOpener.kt:34` and `ShareLauncher.kt:18`
- **What's wrong:** `toItem` keeps any link that passes `WebUrl.isWebUrl`, which checks only the scheme and that a host exists. `ArticleOpener` and `ShareLauncher` check only WebUrl too, never HostGuard.
- **Failure scenario:** A feed item's `<link>` is `https://feedproxy.google.com/~r/site/~3/abc/story.html` or `https://www.youtube.com/watch?v=…`. The card shows normally. Tapping it opens a Custom Tab on a Google host, and in the FeedBurner case that tells Google which article the user read.
- **Suggested fix:** In `FeedParser.toItem`, drop items whose link HostGuard blocks, or mark them and have ArticleOpener refuse them or warn first. Decide whether ShareLauncher should refuse them too. Add a test that a feedproxy.google.com link is dropped.
- **Confidence:** plausible. The mechanism is confirmed. Whether a browser hop that Newz starts counts as "contact" is a product judgement, since the README says the browser has its own privacy policy.
- **Evidence:** WebUrl.kt:9–12 checks only `hostOf != null`.

### R4 — Importing a backup silently deletes reading history and articles saved since the export
- **Slice:** Storage & backup
- **Location:** `shared/src/commonMain/kotlin/app/newz/store/BackupCodec.kt:157`
- **What's wrong:** Restore replaces the saved, hidden, custom-feed and history tables. `contents.history.orEmpty()` turns a backup made without history into an empty history. Export leaves history out by default (BackupScreen.kt:66–71). Import starts as soon as a file is picked, with no confirmation (BackupScreen.kt:55–58, 117). The only warning, "They replace the choices on this phone", does not mention that saved articles or reading history will be lost. Replace semantics are in the spec (S24); the defect is unconfirmed, unexplained data loss, the same class as B8.
- **Failure scenario:** A user exports with the defaults, then imports that file a month later to undo a change to their sources. All reading history is deleted, which also resets ReadingAffinity and the suggestions, and every article saved since the export is gone. Neither can be recovered.
- **Suggested fix:** Show a confirm sheet that lists what will be replaced ("Your N saved articles and reading history on this phone will be replaced"). When `history == null`, keep the current history, or offer that choice.
- **Confidence:** confirmed
- **Evidence:** `replaceUserData` deletes before inserting (ArticleStore.kt:285–289). The success note (BackupScreen.kt:130–132) omits history.

### R5 — Restoring a backup never fetches the restored sources
- **Slice:** Storage & backup
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/settings/BackupViewModel.kt:120`
- **What's wrong:** After a restore, `import()` calls only `feedRepository.invalidate()`, which recomposes from the cache without fetching (FeedRepository.kt:215–227). Every other path that adds sources calls `refresh()` (SettingsViewModel.kt:290/317/439, SearchViewModel.kt:121/128/165, SuggestionsViewModel.kt:77/84). Nothing refreshes on resume.
- **Failure scenario:** This is the "copy it to your new phone" flow the Backup screen recommends. The user onboards with a few topics, then imports. Prefs now follow 40 outlets and 3 custom feeds, but the cache holds only the onboarding feeds. The feed is sparse, or reads "Nothing to show — your filters hide everything", until the user pulls to refresh or restarts the app.
- **Suggested fix:** Call `graph.feedRepository.refresh()` after a successful restore.
- **Confidence:** confirmed
- **Evidence:** The only automatic `coordinator.refresh()` is the one at start (FeedRepository.kt:236). The prefs collector (line 238) only recomposes.

### R6 — An article in two section feeds of one outlet keeps the first feed's feedId and topics; unfollowing that section or topic hides it
- **Slice:** Storage & backup; also reported by Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/store/ArticleStore.kt:68` (refresh path, lines 67–70); `shared/src/commonMain/kotlin/app/newz/store/StoreModels.kt:117`
- **What's wrong:** `ArticleIds.of` hashes outletId + guid (or url), so the same story in two sections of one outlet becomes one row. `upsert` writes feedId and topics only when it inserts. Later sightings go through `refresh`/`refreshKeepingDate` (Article.sq:23–29), which update only url, title, summary, imageUrl and publishedAt. FeedFilter.reason (FeedFilter.kt:76–89, 126–134) judges FEED_NOT_FOLLOWED, TOPIC_NOT_FOLLOWED and TOPIC_MUTED from that one stale feedId and topic list. Fetches run 8 at a time, so which feed lands first varies.
- **Failure scenario:** The user follows nyt-world and nyt-us. A story appears in both feeds, and nyt-world lands first. The user unticks World, or unfollows or mutes the world topic. The story disappears even though nyt-us, which is still followed, carries it, and it stays hidden until it is pruned after 7 days. The US topic chip never matches it. 33 catalog outlets have more than one section feed.
- **Suggested fix:** Store the union of topics and the set of source feeds, or an (articleId, feedId) join table, and merge them on refresh. In FeedFilter, treat the article as followed when any of its feeds is followed. At minimum, rewrite feedId and topics on refresh when the stored feed is no longer followed and the current one is.
- **Confidence:** confirmed
- **Evidence:** FeedComposer.toArticle (FeedComposer.kt:80–91) passes the stored feedId and topics straight on.

### R7 — Uncapped summaries (full article bodies) collapse unrelated stories into one card and slow grouping
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt:120`
- **What's wrong:** `val body = item.description ?: item.content` keeps the whole plain-text body with no length cap. StoryGrouper (StoryGrouper.kt:188) vectorises headline + summary. Its smoothed IDF has a floor of 1 (line 193), centroids accumulate every member's terms (lines 167–172), and SAME_STORY_THRESHOLD = 0.275 (line 13) was tuned on headline-only data. The B17 performance test uses only 8–20-word summaries.
- **Failure scenario:** In a synthetic Zipf corpus of 5,000 unrelated articles, 40-word summaries gave 4,999 groups, which is correct. 300-word summaries gave 65 groups and 600-word summaries gave 55, so most of the feed vanishes behind "+N sources". Grouping time rose from 1.2 s to 7.3 s on a desktop JVM, and this runs on every recompose. Full-text WordPress feeds put the whole body in `<description>`.
- **Suggested fix:** Truncate the summary in `toItem` to roughly 300–500 characters, cut on a word boundary, and prefer a short `description` over `content`. Feed StoryGrouper the headline plus at most the first ~40 summary words. Add a long-summary grouping test that checks both the false-merge rate and the run time.
- **Confidence:** plausible. The missing cap is confirmed; the amount of false merging rests on a synthetic corpus.
- **Evidence:** ArticleStore.kt:60,68 stores the summary as is, and TextFeatures.kt:23 tokenises the full text.

### R8 — ISO 8601 dates with a basic offset (+0000), no seconds, or a space before the offset fail to parse, so old items look new
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedDates.kt:45`
- **What's wrong:** `parseIso` relies on `Instant.parse`, which accepts only the extended offset form. ISO_NO_ZONE (line 36) accepts no offset at all, and RFC822 (lines 29–33) needs a month name.
- **Failure scenario:** These all parse to null: `2026-11-16T10:00:00+0000` (PHP DATE_ISO8601 and Java `Z` format, common in feeds), `2026-11-16T10:00Z`, `2026-11-16 10:00:00 +0000`, `Mon, 16 Nov 2026 10:00:00 +0000 (UTC)` and `…10:00:00.123+0530`. Each such item gets publishedAt = now (ArticleStore.kt:62), and refreshKeepingDate keeps that date. Up to 300 weeks-old items then show as "now", bypass the 7-day window and take the top recency band.
- **Suggested fix:** Before parsing, normalise a trailing `[+-]\d{4}` to `[+-]\d{2}:\d{2}`, remove a single space before the offset, insert `:00` for HH:MM without seconds, and strip a parenthesised comment from RFC 822 dates. Add a test for each form.
- **Confidence:** confirmed
- **Evidence:** Reproduced with jshell against shared/build/classes and kotlinx-datetime 0.8.0.

### R9 — Unknown RFC 822 zone abbreviations (AEST, IST, BST, CET…) are read as UTC
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedDates.kt:81`
- **What's wrong:** `ZONES[zone.lowercase()]?.let { it * 3600 } ?: 0`. The regex accepts any 1–5-letter zone, and any abbreviation outside the US/UTC table (lines 13–27) silently becomes +00:00.
- **Failure scenario:** An Australian feed stamps an item `Tue, 17 Nov 2026 08:00:00 AEDT`, which is 21:00Z on the 16th. Newz reads it as 11 hours in the future, so the item is clamped to "now" and sits at the top of band 0 for 11 hours (see R23). A zone west of UTC makes items look older instead. The catalog includes abc.net.au, bbc.co.uk, cbc.ca and japantimes.co.jp.
- **Suggested fix:** Return null for an unrecognised alphabetic zone, so the item falls back to firstSeenAt. Optionally add the common non-US zones, keeping in mind that some abbreviations are ambiguous (IST, for example).
- **Confidence:** confirmed
- **Evidence:** jshell: `Mon, 16 Nov 2026 10:00:00 AEST` and `… BST` both parse as 10:00Z.

### R10 — A refresh that joins a running load does not fetch feeds followed after that load started
- **Slice:** Engine
- **Location:** `shared/src/commonMain/kotlin/app/newz/engine/RefreshCoordinator.kt:70`
- **What's wrong:** `startOrJoin` returns the active Deferred whenever a load is running. `load()` fixes its source list once, at lines 83–88, and for a retry filters it to `only`. The follow paths (SearchViewModel.kt:121,128,165; SuggestionsViewModel.kt:77,84; SettingsViewModel.kt:290,317) call `refresh()` expecting the new feed to be fetched.
- **Failure scenario:** The user follows an outlet in Search while the start-up load (~7–10 s) is still running, or taps Follow on two results a few seconds apart. The new outlet's refresh joins a load that never fetches it, so it shows no articles and no failure marker until a later manual pull. Likewise, a pull during Settings › Sources › Retry fetches only the one feed being retried.
- **Suggested fix:** When `refresh()` joins a running load, mark it dirty or compare the current followed sources with the load's list, and when the load ends, run one follow-up load for the sources it did not cover (or a full load if a full refresh joined a retry). Add a gated-load test in RefreshCoordinatorTest.
- **Confidence:** confirmed
- **Evidence:** The prefs collector (FeedRepository.kt:238) only recomposes, and nothing queues a follow-up fetch.

### R11 — FeedRanker's source-spread fallback is quadratic when one outlet dominates
- **Slice:** Engine
- **Location:** `shared/src/commonMain/kotlin/app/newz/engine/FeedRanker.kt:90` (pick loop, lines 82–102)
- **What's wrong:** For each card, when every queue head belongs to an outlet already at MAX_PER_WINDOW, `laterCardOf(current)` and then `laterCardOf(later)` scan every remaining item with `indexOfFirst(::fits)`, and each `fits` counts over the deque. Line 82 also re-filters and re-sorts the queues on every pick. Once most remaining cards come from one outlet, `rank()` is O(N²). It runs on every compose: invalidate, prefs change, growing publish, onLoaded and showNewStories.
- **Failure scenario:** In a scratch benchmark, 12,000 groups took 39 ms when outlets were evenly spread and 972 ms with 80% from one outlet. 3,000 groups took 76 ms and 6,000 took 259 ms. A user who follows many sections of one prolific outlet (sections share an outletId) gets delayed card updates after every hide or save, plus the CPU and battery cost. This is the B17 class of problem. It runs on Dispatchers.Default, so the UI thread does not freeze.
- **Suggested fix:** Keep per-outlet window counts in a map so `fits` is O(1). Skip runs of blocked-outlet cards, or index each queue by outlet. Remember that no card fits until `recent` changes. Add a performance bound test like the B17 one.
- **Confidence:** confirmed. The complexity follows directly from the code; the verifier did not re-run the benchmark.
- **Evidence:** FeedRanker.kt:97–102 falls through to `current.first()` only after scanning everything that is left.

### R12 — Feed scroll position and per-screen state are lost on every forward/back navigation
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/nav/Navigation.kt:126`
- **What's wrong:** NavHost renders only `content(backStack.current)`, with no SaveableStateHolder, so leaving a destination disposes its composition and every rememberSaveable in it. That includes FeedScreen's `rememberLazyListState()` (FeedScreen.kt:175), ScreenFrame's scroll state (SettingsScreen.kt:294) and the showAll/expanded/editZip flags on Topics and Sources. `rememberBackStack` saves only the routes (lines 107–116).
- **Failure scenario:** The user scrolls 60 cards down, taps Saved or Settings, then presses Back, and lands at index 0 of a long feed. On Settings, opening Sources and coming back resets the scroll and collapses "Show all".
- **Suggested fix:** Wrap each destination in `rememberSaveableStateHolder().SaveableStateProvider(screen.route) { … }` and call `removeState(route)` when the entry is popped. Alternatively, hoist the feed's list position into FeedViewModel.
- **Confidence:** confirmed
- **Evidence:** A grep finds no SaveableStateHolder anywhere in androidApp.

### R13 — Landscape edge-to-edge: app bars and settings screens ignore side navigation-bar and display-cutout insets
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/components/AppBars.kt:42`
- **What's wrong:** With targetSdk 36, edge-to-edge is enforced (`enableEdgeToEdge`, MainActivity.kt:50), and the activity has no orientation lock. AppBarSurface pads only the status bar. ScreenFrame (SettingsScreen.kt:289–298), AboutScreen and OnboardingScreen add only a bottom nav-bar spacer. Only the feed list's contentPadding handles the side nav bar, and it ignores the cutout.
- **Failure scenario:** In landscape with 3-button navigation, the trailing Settings button, the Sources "Add" button and ListRow switches and Remove buttons sit partly under the nav bar on the right, so taps land on Back or Home. On the other side, the back arrow, the wordmark and row titles sit under the camera cutout.
- **Suggested fix:** Apply `Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))` once at the root Box in NewzApp, or apply it per surface and add displayCutout padding to the feed list.
- **Confidence:** confirmed for the nav-bar overlap. The cutout part is plausible, because it depends on the cutout mode `enableEdgeToEdge` sets.
- **Evidence:** The only insets used in the app are statusBars, bottom-only navigationBars, sheet/snackbar navigationBarsPadding, and the feed contentPadding (FeedScreen.kt:222).

## Low

### R14 — IP-literal hosts (including Google's own addresses) pass HostGuard
- **Slice:** Network & privacy
- **Location:** `shared/src/commonMain/kotlin/app/newz/net/HostGuard.kt:56`
- **What's wrong:** `isBlockedHost` matches names only, and `isValidHostSyntax` (lines 118–123) explicitly allows bracketed IPv6. IPv4, bracketed IPv6 and IPv4-mapped literals are never blocked.
- **Failure scenario:** `isBlocked("https://8.8.8.8/")` and `isBlocked("https://[::ffff:8.8.8.8]/")` both return false, and OkHttp resolves the latter to host 8.8.8.8. A feed image at `https://142.250.80.46/x.jpg` makes the phone open TCP and send a TLS ClientHello to Google before the certificate check fails. Literals also let a feed make the phone probe LAN addresses.
- **Suggested fix:** Refuse IP-literal hosts in feed-supplied URLs (images, redirects, discovered links), and refuse private and loopback ranges in release builds.
- **Confidence:** confirmed
- **Evidence:** jshell with OkHttp 5.5.0: `HttpUrl.parse("https://[::ffff:8.8.8.8]/").host()` == `8.8.8.8`.

### R15 — First `<img>` in the description becomes the card picture, even when it is a third-party tracking pixel
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt:142` (imageOf, lines 132–152)
- **What's wrong:** The fallback takes the first `<img src>` in content or description, with no check of width/height attributes or of the host beyond HostGuard. The 300 px MinSourceWidth check runs only after the image has been downloaded.
- **Failure scenario:** An item with no media:content or enclosure starts its description with a 1x1 beacon on a non-Google analytics host. Coil fetches the beacon when the card renders, which tells that host which article the user scrolled past. That breaks the "publishers only" promise.
- **Suggested fix:** Skip `<img>` tags with width or height ≤ 2 and paths that look like tracking (b.gif, pixel, beacon). Consider requiring the same registrable domain as the item link for this fallback.
- **Confidence:** confirmed
- **Evidence:** The only filter applied is `HostGuard.isBlocked` at line 159.

### R16 — Image downloads have no byte cap; only the 15 s request timeout bounds them
- **Slice:** Network & privacy
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/image/NewzImageLoader.kt:54`
- **What's wrong:** Feeds and pages are capped (fetchCapped at 5 MB, fetchStart at 64 KB), but `KtorNetworkFetcherFactory(httpClient)` streams each picture's whole body. MinSourceWidthDecoder runs only after the download.
- **Failure scenario:** A hostile or misconfigured feed names a 300 MB file as an item image. Each card downloads whatever fits in 15 s, possibly hundreds of MB on fast mobile data, and several cards can load at once. This uses up the user's data and churns the 64 MB disk cache.
- **Suggested fix:** Add an interceptor on the image client that checks Content-Length and counts bytes read from the body, aborting past about 10 MB.
- **Confidence:** confirmed
- **Evidence:** The only bound is `requestTimeoutMillis = 15 s` (NewzHttpClient.kt:61, 92).

### R17 — Feed autodiscovery gives up on the first declared feed when it is a Google one
- **Slice:** Network & privacy
- **Location:** `shared/src/commonMain/kotlin/app/newz/discover/FeedDiscovery.kt:92`
- **What's wrong:** `feedLink` (lines 199–237) returns the first rel=alternate RSS/Atom candidate. `discover()` returns `DiscoveryResult.Google` if that one candidate is blocked, and never tries the later ones.
- **Failure scenario:** An older WordPress site declares feeds.feedburner.com first and its own `/feed/` second. Typing the site's address shows the Google refusal instead of offering the publisher's own feed.
- **Suggested fix:** Skip blocked candidates, and return Google only when every candidate is blocked.
- **Confidence:** confirmed
- **Evidence:** The early return at line 92.

### R18 — Items sharing one guid collapse into a single row and the other stories are lost
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/store/StoreModels.kt:117`
- **What's wrong:** `guid = item.guid ?: url` (FeedParser.kt:122), and `ArticleIds.of` ignores the url whenever a guid is present. Within one upsert, each later item with the same guid overwrites the row through `refresh`.
- **Failure scenario:** A user-added feed has `<guid>https://blog.example/</guid>` on all 20 items. Only one card ever appears, showing the last item processed, and its title and link change on every refresh.
- **Suggested fix:** In `FeedParser.parse`, detect guids repeated within one feed and fall back to the url for those items.
- **Confidence:** confirmed
- **Evidence:** ArticleStore.kt:47–72. The `distinctBy { it.url }` in FeedComposer.kt:69 cannot help, because only one row exists.

### R19 — A corrupt preferences file crashes the app on every launch
- **Slice:** Storage & backup
- **Location:** `shared/src/commonMain/kotlin/app/newz/store/PreferencesStore.kt:43`
- **What's wrong:** `PreferenceDataStoreFactory.createWithPath` is called without a `corruptionHandler`. The `runCatching` in `decode` covers only the JSON value inside a valid file. AppGraph collects with `stateIn(scope, Eagerly, …)` (AppGraph.kt:122) in a SupervisorJob scope with no CoroutineExceptionHandler.
- **Failure scenario:** A truncated or damaged `newz.preferences_pb` makes the app crash at every launch. The only recovery is clearing app data, which also deletes saved articles.
- **Suggested fix:** Pass `corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }`, and tell the user their settings were reset.
- **Confidence:** plausible. DataStore writes through an atomic rename, so a corrupt file is rare.
- **Evidence:** PreferencesStore.kt:42–43, 48–49.

### R20 — An interrupted restore can leave the backup's prefs alongside the phone's old tables
- **Slice:** Storage & backup
- **Location:** `shared/src/commonMain/kotlin/app/newz/store/BackupCodec.kt:151`
- **What's wrong:** `preferences.replace(contents.prefs)` runs before the try/rollback block, and the table replacement follows it. The two stores are not written atomically. The verifier found the reported cancellation path mostly wrong, because the ViewModel is activity-scoped (see R35). What remains is process death or the activity finishing between the DataStore commit and the SQL transaction.
- **Failure scenario:** The process is killed in that gap. The prefs are the backup's, but the saved, hidden and custom-feed tables are the phone's old ones, and no error is shown.
- **Suggested fix:** Move `preferences.replace` inside the try block, or write the tables first and the prefs last. Run the restore in an application-lifetime scope or with NonCancellable.
- **Confidence:** plausible
- **Evidence:** BackupCodec.kt:151–152. The restore runs in viewModelScope (BackupViewModel.kt:107).

### R21 — Backup "include reading history" choice is lost if the process dies while the file picker is open
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/settings/BackupViewModel.kt:49`
- **What's wrong:** `includeHistoryState` is a plain `MutableStateFlow(false)`. `export()` reads it when the CreateDocument result arrives (BackupScreen.kt:52–53), and after process death that result reaches a new ViewModel.
- **Failure scenario:** On a low-memory phone, Android kills Newz while the user browses folders in the Files app. The backup is then written without history, and nothing says so. A later restore from that file brings back no history.
- **Suggested fix:** Keep the flag in SavedStateHandle (`createSavedStateHandle()`), or hold it in rememberSaveable and pass it to `export(uri, includeHistory)`.
- **Confidence:** confirmed
- **Evidence:** Only `exportName` is in rememberSaveable (BackupScreen.kt:50).

### R22 — Onboarding picks are lost on process death
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/onboarding/OnboardingViewModel.kt:33`
- **What's wrong:** `pickedState` and `localState` live only in the ViewModel, while `zipSheetOpen` is in rememberSaveable (OnboardingScreen.kt:64). After process death the zip sheet reopens over a screen with nothing picked. Saving a zip then is harmless, because `finish()` (lines 68–77) applies it only when Local is picked.
- **Failure scenario:** The user picks five topics and Local, then switches apps to look up their zip code, and Android kills Newz. On return they see "0 picked — pick at least 3" and have to pick everything again.
- **Suggested fix:** Store the picks in SavedStateHandle, or close the restored sheet when Local isn't picked.
- **Confidence:** confirmed
- **Evidence:** OnboardingViewModel.kt:33–38.

### R23 — Future-dated articles are pinned at the top as "now" and never pruned until their date passes
- **Slice:** Parsing & content; also reported by Storage & backup
- **Location:** `shared/src/commonMain/kotlin/app/newz/filter/FeedFilter.kt:71`; `shared/src/commonMain/sqldelight/app/newz/db/Article.sq:38`
- **What's wrong:** `minOf(a.date, ctx.now)` means TOO_OLD can never fire for a future date, and FeedRanker (FeedRanker.kt:61,66,75) clamps the same way and puts the item in band 0. ArticleStore stores publishedAt without clamping it (ArticleStore.kt:62/68), and `pruneBefore` deletes only rows with `publishedAt < cutoff`. If the publisher later drops or corrects the item, the stored date never changes.
- **Failure scenario:** A feed dates an item `Fri, 01 Jan 2027 09:00:00 GMT`, because of a year typo, a schedule feed or a wrong zone (see R9). The card ranks with the newest stories, shows "now" and stays in the cache until 7 days after that date.
- **Suggested fix:** When storing, clamp publishedAt to firstSeenAt plus a small tolerance (about 15 min), treating anything further ahead as undated. Or prune on `min(publishedAt, firstSeenAt)`. Add a test in which a far-future item expires.
- **Confidence:** confirmed
- **Evidence:** A grep finds no future clamp in FeedDates or FeedParser.

### R24 — The "es" keyword ending is allowed after any letter: muting "Tim" hides "times", "rat" hides "rates", "US" hides "uses"
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/filter/KeywordMatcher.kt:29`
- **What's wrong:** `ENDINGS = listOf("", "'s", "s", "es", "'")`, and `lastWordMatches` (lines 79–84) accepts keyword + "es" for any keyword. Mutes always win (FeedFilter.kt:90), and the same matcher assigns custom-topic tags (FeedFilter.kt:105–112, 145).
- **Failure scenario:** Muting "Tim" hides every "Times Square" or "three times" headline with no explanation. A custom topic "cop" tags stories containing "copes".
- **Suggested fix:** Allow "es" only after s, x, z, ch, sh or o. Add negative tests.
- **Confidence:** confirmed
- **Evidence:** KeywordMatcher.kt:29, 79–84.

### R25 — Any `<!ENTITY` text inside CDATA makes the whole feed fail to parse
- **Slice:** Parsing & content
- **Location:** `shared/src/commonMain/kotlin/app/newz/feed/FeedParser.kt:59`
- **What's wrong:** `DECLARATION.containsMatchIn(xml)` scans the whole document, CDATA bodies included. The DOCTYPE regex (line 42) also silently strips an embedded `<!DOCTYPE html>` from CDATA.
- **Failure scenario:** A security blog's post quotes an XXE payload inside CDATA. Every fetch fails with "Feed declares a DTD or entities" until the post drops out of the feed.
- **Suggested fix:** Test for declarations only in the prolog, before the root element match, or strip CDATA before the check.
- **Confidence:** confirmed
- **Evidence:** FeedParser.kt:43, 59.

### R26 — A pull to refresh during a finishing load's final compose loses its re-rank, and the flag leaks into the next load
- **Slice:** Engine
- **Location:** `shared/src/commonMain/kotlin/app/newz/engine/FeedRepository.kt:287`
- **What's wrong:** `onLoaded` reads and clears `rerankRequested` in its first lock block (lines 284–288), then composes outside the lock (line 299) while the coordinator's Deferred is still active. A `refresh(rerank = true)` in that window sets the flag (line 183) and joins the finishing load (line 184). The pull gets no fetch and no re-rank, and the flag stays true.
- **Failure scenario:** On a large cache, the user pulls just as the load's last feeds land. The pull ends with the old cards in place, which is the B22 symptom. Later, an unrelated load (a follow, a Retry) sees the stale flag and calls `pager.reset`, so the cards jump while the user is reading.
- **Suggested fix:** Tie the flag to a load, or start a new load when the joined one has already passed its read of the flag. Add a test that gates onLoaded's compose and pulls during it.
- **Confidence:** confirmed. It is timing-dependent but reachable.
- **Evidence:** FeedRepository.kt:183–184, 284–304; RefreshCoordinator.kt:113.

### R27 — Concurrent publishes are not ordered: a composition from an older snapshot can overwrite a newer one
- **Slice:** Engine
- **Location:** `shared/src/commonMain/kotlin/app/newz/engine/FeedRepository.kt:299`
- **What's wrong:** `publish()` composes outside the lock and applies the result under the lock in whatever order the composes finish, on Dispatchers.Default (AppGraph.kt:111). No snapshot sequence number is compared, and `composedPrefs` (line 315) is set by whichever compose finishes last.
- **Failure scenario:** A load's snapshot is read, then the user hides a card. The cheaper recompose that follows finishes first, and the load's older compose is applied after it. The hidden card reappears, because FeedViewModel.hide relies on `invalidate()` alone, and composedPrefs reverts until prefs emit again.
- **Suggested fix:** Give each snapshot read a sequence number and apply a publish only when its number is newer than the last one applied. Alternatively, route all recomposes through one conflated actor.
- **Confidence:** plausible. The code allows this ordering, but it was not demonstrated.
- **Evidence:** FeedRepository.kt:226, 238, 299–324; FeedViewModel.kt:238–252.

### R28 — Reading history is never pruned, and all of it is read and JSON-decoded on every recompose
- **Slice:** Storage & backup
- **Location:** `shared/src/commonMain/sqldelight/app/newz/db/History.sq:17`
- **What's wrong:** History.sq has only insert, deleteAll and selectAll, with no retention, unlike the article, hidden and page_image tables. `FeedSnapshot.read` calls `listHistory()` on every snapshot (FeedComposer.kt:38).
- **Failure scenario:** After a year of heavy reading, every hide, save, mute and landed feed re-reads and decodes tens of thousands of rows on the store dispatcher, and the cost keeps growing.
- **Suggested fix:** Prune history older than 90–180 days in the RefreshCoordinator prune step and index `openedAt`, or keep aggregate counts instead of raw rows.
- **Confidence:** confirmed
- **Evidence:** FeedRepository.kt:140/191/226/242–245 all read a snapshot.

### R29 — Keyword-mute and zip-code sheets don't scroll, so Mute/Save can be pushed off-screen by the keyboard
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/feed/KeywordMuteSheet.kt:206`; `androidApp/src/main/kotlin/app/newz/ui/settings/ZipCodeSheet.kt:146`
- **What's wrong:** Both sheets put all their content in a plain Column with imePadding, and neither caps its height or scrolls, unlike CardMenuSheet and StoryGroupSheet. Both open the keyboard as soon as they appear (lines 201–204).
- **Failure scenario:** In landscape (about 360 dp tall, keyboard about 200 dp), or at the largest text sizes in portrait, the Cancel/Mute row is clipped. The user has to dismiss the keyboard before they can confirm.
- **Suggested fix:** Use `heightIn(max = …)` and `verticalScroll` as CardMenuSheet does, or pin the button row outside the scrolling area.
- **Confidence:** confirmed
- **Evidence:** Sheets.kt:107–136 doesn't scroll either.

### R30 — BottomSheet leaves the screen behind it reachable by TalkBack
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/components/Sheets.kt:83`
- **What's wrong:** BottomSheet is an in-tree Box, not a Dialog or Popup. The scrim (lines 88–93) has no semantics, and nothing hides the underlying feed from accessibility.
- **Failure scenario:** With TalkBack on, swiping past the last action in the ⋮ menu moves focus to the feed cards and app-bar buttons behind the scrim. A double-tap opens an article or Settings underneath the open sheet.
- **Suggested fix:** Host sheets in a Dialog or Popup (or use ModalBottomSheet), or hide the underlying content from accessibility while a sheet is open and make the sheet a traversal group.
- **Confidence:** confirmed
- **Evidence:** FeedScreen.kt:280–311 draws the sheets over the live feed tree.

### R31 — Undo snackbar disappears after a fixed 6 s, ignoring the accessibility timeout setting
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/components/Feedback.kt:62`
- **What's wrong:** `delay(NewzTokens.snackbarTimeout)` is fixed at 6,000 ms, and the "Time to take action" setting is never consulted. All Undo actions go through this bar.
- **Failure scenario:** A TalkBack or switch-access user hears the long "Article hidden…" message and can't reach Undo within 6 s, so the chance to undo is gone.
- **Suggested fix:** Use `LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(…)`, and consider not dismissing while the action has accessibility focus.
- **Confidence:** confirmed
- **Evidence:** A grep finds no AccessibilityManager use in the app.

### R32 — Card menu, keyword sheet and story-group sheet close on rotation
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/feed/FeedScreen.kt:172` (lines 172–174)
- **What's wrong:** `openGroup`, `menuCardId` and `muteHeadline` use plain `remember`, so the rememberSaveable text inside KeywordMuteSheet (lines 195–196) is never shown again. Other screens use rememberSaveable for their sheets.
- **Failure scenario:** The user types a keyword to mute, then rotates the phone, switches dark mode or changes the font size. The sheet closes and the text is lost.
- **Suggested fix:** Use rememberSaveable for `menuCardId` and `muteHeadline`. For the story group, save the lead article id and look the group up again.
- **Confidence:** confirmed
- **Evidence:** The manifest declares no configChanges.

### R33 — Saved list composes every saved article at once
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/saved/SavedScreen.kt:56`
- **What's wrong:** `items.forEach { SavedCard(...) }` runs inside a verticalScroll Column. Saved rows are never purged (Article.sq:38), so the list has no upper bound.
- **Failure scenario:** With a few hundred saved articles, opening Saved composes them all and starts every thumbnail load at once, and each Unsave remeasures the whole list. The cost grows with use.
- **Suggested fix:** Use a LazyColumn keyed by article id, with a lazy variant of ScreenFrame.
- **Confidence:** confirmed
- **Evidence:** SettingsScreen.kt:291–299 (ScreenFrame).

### R34 — Saved screen's offline banner only updates when the feed emits
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/ui/saved/SavedViewModel.kt:40`
- **What's wrong:** `offline = feed.map { !isOnline() || … }` checks connectivity only when FeedState emits.
- **Failure scenario:** When Wi-Fi comes back, the "You're offline" banner stays until the user taps Retry. When the phone goes offline, no banner appears.
- **Suggested fix:** Combine the feed state with a ConnectivityManager callbackFlow.
- **Confidence:** confirmed
- **Evidence:** SavedViewModel.kt:38–45.

### R35 — All screen ViewModels are activity-scoped and never cleared
- **Slice:** Android UI & build
- **Location:** `androidApp/src/main/kotlin/app/newz/MainActivity.kt:103`
- **What's wrong:** The hand-rolled NavHost gives no ViewModelStoreOwner per entry, so one SettingsViewModel backs Settings, Sources, Topics, Hidden and Backup. Search, Saved, Suggestions and Onboarding ViewModels, with their eagerly started collectors, live as long as the activity.
- **Failure scenario:** The user removes an outlet on Sources and presses Back. Settings shows the same "X removed" snackbar again with a fresh 6 s timer. Suggestions first shows the previous visit's ranking. SavedViewModel keeps collecting the feed even when Saved isn't open.
- **Suggested fix:** Give each back-stack entry its own ViewModelStoreOwner and clear it when the entry is popped (or adopt Navigation-Compose or Navigation 3). At minimum, clear the shared message when a settings screen is left.
- **Confidence:** confirmed
- **Evidence:** Navigation.kt:120–127; SettingsScreen.kt:266–267.

### R36 — Toolchain setup script installs an unpinned, unverified JDK and rewrites the user's global Gradle config
- **Slice:** Android UI & build
- **Location:** `scripts/setup-android-toolchain.sh:43`
- **What's wrong:** The script downloads Adoptium's moving `latest/17` build over HTTPS with no checksum check, then unpacks it into ~/Library/Java/JavaVirtualMachines. Line 127 writes `org.gradle.java.home` into `~/.gradle/gradle.properties`, which affects every Gradle project on the machine. The Gradle wrapper, by contrast, is pinned with a SHA-256. (The analytics.settings overwrite on line 115 matches the privacy goal, so it is not counted as a defect.)
- **Failure scenario:** An unexpected or tampered JDK build is used for every build, including release signing. The developer's other Gradle projects silently switch to JDK 17.
- **Suggested fix:** Pin a specific Temurin release and verify its SHA-256. Write the JDK path to the repo's `gradle.properties` or `local.properties`, or export JAVA_HOME, instead of the global file.
- **Confidence:** confirmed. This is a developer-tooling risk, not an app defect.
- **Evidence:** setup-android-toolchain.sh:41–48, 127.

## Test gaps

### Network & privacy
- Every HostGuard and NewzHttpClient test runs on Ktor's MockEngine. No jvmTest compares the guard with OkHttp's `HttpUrl.host` for UTS-46-mapped characters (ⓖ, 𝐠, ſ, U+00AD, U+200B) or for ASCII `xn--` labels (R1).
- No test sends a redirect whose Location hides a Google host (percent-encoded or punycode) through the real OkHttp engine.
- The image loader's guard is tested only in androidTest (ImageGuardTest). `AppGraph.imageLoader` builds its own `newzHttpClient()`, so the `Config.httpClient` override (`TestApp.recordingClient`) never sees image requests.
- No test covers item links on Google hosts or ArticleOpener refusing them (R3).
- No test covers FeedDiscovery when the first declared feed is blocked and a later one is not (R17).
- HtmlSanitizerTest tests code that nothing in production calls, since item HTML is never rendered. SPEC S2's "stripped before display" is therefore met only by dead code.
- No test of image size limits (R16), or of fetchCapped/fetchStart against a slow-drip body on the real engine.

### Parsing & content
- FeedDates has no tests for basic offsets, HH:MM without seconds, `(UTC)` comments, non-US zone abbreviations, RFC 850 dates or far-future dates (R8, R9, R23).
- The StoryGrouper performance and equivalence tests use only 8–20-word summaries (R7).
- No end-to-end test that a future-dated article leaves the feed and is pruned.
- KeywordMatcherTest has no negative "es" cases (R24).
- No test for duplicate guids within one feed, or for the same guid across two sections followed by unfollowing one (R6, R18).
- No test for UTF-16 feeds, or for a UTF-8 BOM combined with a Latin-1 charset label (the BOM survives as `ï»¿` and breaks prolog stripping).
- No test that `<!ENTITY` inside CDATA does not reject the feed (R25).
- `imageOf` has no test for a tracking pixel that comes before the real images, or for relative `<img src>` values (currently dropped).

### Engine
- No test for following a feed during a running load, or for a full refresh joining a retry (R10). The existing tests assert only that the call joins.
- No ranking performance test with thousands of groups dominated by one outlet (R11). The B17 test bounds the number of composes, not their cost.
- `aPullJoiningARunningLoadStillReranks` covers only the fetch phase, not a pull during onLoaded's compose or the leaked flag (R26).
- No test of concurrent publishes finishing out of order (R27).
- No test that a per-feed child in `FeedFetcher.fetch` ending without sending cannot hang `repeat(unique.size) { done.receive() }`. A compose or store failure inside onLoaded/onProgress, which could leave the state stuck at Loading, is also untested.
- PageImagesTest has no concurrent test of the logo rule.
- No FeedPager test of a stale `old` group whose non-lead members have since been hidden or muted.

### Storage & backup
- No ArticleStore test of one article reached through two feeds of one outlet (R6).
- No upsert or prune test with a future publishedAt (R23).
- No test that a backup import fetches the newly followed sources (R5).
- No test that importing a backup without history clears the existing history, or of any warning about it (R4).
- No test of a corrupt `newz.preferences_pb`, or of decode falling back to defaults and the next `update()` then overwriting the stored prefs with them (R19).
- MigrationTest covers 1→2 on JdbcSqliteDriver only. Nothing exercises AndroidSqliteDriver's onUpgrade or onDowngrade, and nothing checks the data (not just the schema) after a migration.
- BackupDocuments (SAF read/write, the 5 MB cap, `wt` mode, deleting after a failed write) has no JVM test.
- No hostile-backup tests for an invalid zip or localMetroId, overlong muted keywords, or far-future hiddenAt timestamps.
- Restore under cancellation is untested (R20).

### Android UI & build
- No test leaves the feed and comes back while checking the scroll position or other rememberSaveable state (R12).
- No process-death test covering onboarding picks, the backup picker round trip, or the back stack restored from routes (R21, R22); only recreate() is tested.
- No landscape or orientation tests: side insets, cutout, or sheet heights with the IME open (R13, R29).
- AccessibilityTest never opens a sheet at large text with the IME up, never checks TalkBack confinement inside a sheet (R30), and never checks the snackbar timeout (R31).
- androidApp/src/test holds only ContrastTest. The ViewModels' Undo logic (changePrefs, changeLevel restore, toggleSaved re-save, unfollow/restoreOrder) is covered only indirectly by UI tests.
- No performance test of Saved with hundreds of rows (R33).
- `release-smoke.sh` only launches the minified APK for 10 s. Backup export/import, Custom Tabs and image loading are never run against the R8 build.

## Not assessed

No slice reviewer or verifier failed; all five slices returned results.

### Network & privacy
- The iOS engine (`Engine.ios.kt`) sets no redirect policy. If Darwin/NSURLSession follows redirects itself, hops would skip `guardedExecute`. Not checked, because iOS is not shipped and no Darwin runtime was available.
- Coil 3.6.3 KtorNetworkFetcher internals (conditional headers, streaming to disk) were assessed from the API only.
- DNS-level or hosting-level Google contact (CNAMEs to ghs.googlehosted.com, Google Cloud hosting, the user's Private DNS) cannot be caught by a hostname guard, so it was not treated as a defect.
- On Android 12+, `allowBackup=false` does not stop device-to-device transfer without dataExtractionRules. Not reported, because the data goes only to the user's own device.
- What Custom Tabs browsers send (Referer, EXTRA_REFERRER) was not tested.
- Release cleartext is off, so http:// feeds and images fail in release. This looks intentional and was not reported.
- R1 was verified on the JVM build only. OkHttp on Android is assumed to share the same `idnToAscii` code (it is common code in OkHttp 5).

### Parsing & content
- How RSS-Parser 6.1.8 maps Atom/RSS summary and content fields on Android versus JVM, and how it handles undefined HTML entities once the DOCTYPE is stripped. The library source was not read.
- The date formats and summary lengths that the 477 catalog feeds actually serve (no network was allowed). The severity of R7–R9 is therefore estimated, not measured.
- HtmlSanitizer's regex robustness (for example a possible StackOverflowError in MEDIA_TAG on long tags) was not assessed, since nothing in production calls it.
- Catalog JSON is compiled into Kotlin at build time. Only string escaping and the metro-index guard were checked; data values were out of scope.
- Performance numbers come from a desktop JVM (Temurin 17); no device timings were taken.

### Engine
- Device timings for R11; the Mac benchmark was a scratch harness outside the repo.
- Whether Ktor 3.6.0 can ever let a CancellationException escape `fetchCapped` on timeout, which could hang the fan-in in `FeedFetcher.fetch` (lines 88–94). Not reported as a defect, but it remains a robustness risk.
- StoryGrouper internals and the cost of FeedFilter on large caches were left to other slices.
- Whether AndroidSqliteDriver is thread-safe across the several "single-thread" store dispatchers and the view models that use Dispatchers.IO directly.
- The engine reviewer did not run `./gradlew :shared:jvmTest`.

### Storage & backup
- How document providers (Drive, Samsung My Files, Downloads) handle `wt` mode and `deleteDocument`. This needs a device.
- androidTest suites, including BackupScreenTest, were not run. `./gradlew --offline :shared:verifySqlDelightMigration :shared:jvmTest --tests 'app.newz.store.*'` passed.
- iOS storage was not reviewed in depth.
- How safe concurrent ArticleStore access is across dispatchers; the reviewer found no concrete failure.

### Android UI & build
- No runtime observation was possible, because the emulator and installs were not allowed. R12, R13, R21, R22 and R29–R32 come from reading the code, not from reproduction.
- The release variant was not built and the R8 output (mapping, usage.txt) was not inspected.
- ktlint and the jvm tests were not run for this slice.
- Visual and dark-mode colour correctness against `design/` was out of scope.
- The data and device scripts (build-popularity.sh, build-zip-table.sh, build-dedup-fixture.py, check-catalog-feeds, capture-design-screens.sh, run-instrumented-tests.sh) were only skimmed.
