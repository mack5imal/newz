# Handoff: Newz

This package covers every screen and state in the Newz design brief — a private news feed for Android phones (Android 10+, Jetpack/Compose) — in the approved direction ("Take 2 Blue": departure-board signage in light blue and navy), plus the approved home-screen icon. Screenshots are at **2× scale: 1 dp = 2 px**, so a 412 dp-wide phone screen is 824 px wide. Most screenshots are one phone screen tall (915 dp); `settings`, `settings-sources` and `settings-topics` are drawn taller (1120, 1180 and 1300 dp) to show the whole scrolling page, and `app-icon.png` is a 496×880 dp specimen sheet, not a phone screen. The top 28 dp of each screenshot is the status-bar inset (the app draws edge-to-edge; system bars are not shown). Hatched blocks are placeholders for the articles' own images. Outlet names and headlines are invented sample data. Every screen has a light screenshot, and for every screen except `app-icon` the last file in its row is the same screen in dark theme, which follows the phone's setting. Where a list runs past the bottom edge, the screenshot simply crops it — the list continues.

## Approved screens

| id | name | files |
|---|---|---|
| `feed` | Feed | `feed.png`, `feed--loading.png`, `feed--long-headline-xl.png`, `feed--filter-active.png`, `feed--filter-empty.png`, `feed--undo.png`, `feed--end.png`, `feed--all-filtered.png`, `feed--offline-saved.png`, `feed--offline-empty.png`, `feed--dark.png` |
| `card-menu` | Article actions | `card-menu.png`, `card-menu--saved.png`, `card-menu--much-more.png`, `card-menu--confirmation.png`, `card-menu--dark.png` |
| `keyword-mute` | Mute a keyword | `keyword-mute.png`, `keyword-mute--typed.png`, `keyword-mute--blank.png`, `keyword-mute--too-long.png`, `keyword-mute--dark.png` |
| `story-group` | Same story, other outlets | `story-group.png`, `story-group--many.png`, `story-group--dark.png` |
| `onboarding` | Pick your topics | `onboarding.png`, `onboarding--ready.png`, `onboarding--dark.png` |
| `settings` | Settings home | `settings.png`, `settings--dark.png` |
| `settings-sources` | Sources | `settings-sources.png`, `settings-sources--failing.png`, `settings-sources--none-removed.png`, `settings-sources--dark.png` |
| `settings-topics` | Topics & keywords | `settings-topics.png`, `settings-topics--no-keywords.png`, `settings-topics--much-more.png`, `settings-topics--dark.png` |
| `settings-hidden` | Hidden articles | `settings-hidden.png`, `settings-hidden--empty.png`, `settings-hidden--dark.png` |
| `saved` | Saved for later | `saved.png`, `saved--empty.png`, `saved--offline.png`, `saved--dark.png` |
| `search` | Find sources & topics | `search.png`, `search--results.png`, `search--checking.png`, `search--feed-found.png`, `search--no-feed.png`, `search--not-address.png`, `search--already-following.png`, `search--google.png`, `search--dark.png` |
| `suggestions` | Suggested for you | `suggestions.png`, `suggestions--empty.png`, `suggestions--dark.png` |
| `backup` | Backup & restore | `backup.png`, `backup--exported.png`, `backup--imported.png`, `backup--import-failed.png`, `backup--dark.png` |
| `app-icon` | Home-screen icon (approved addition) | `app-icon.png` |

State files map to the brief's states as follows:
- `feed`:
  - `feed.png` is the populated feed; it also shows the grouped card ("+3 sources") and the card with no image.
  - `--loading` is the first load.
  - `--long-headline-xl` is a very long headline at Extra large text.
  - `--filter-active` has topic chips selected.
  - `--filter-empty` is a filter with no matching articles.
  - `--undo` is the Undo bar just after hiding an article.
  - `--end` is the end of the feed.
  - `--all-filtered` is everything filtered out.
  - `--offline-saved` is offline with saved articles.
  - `--offline-empty` is offline with nothing saved.
- `card-menu`:
  - `card-menu.png` is the normal sheet.
  - `--saved` is an article that's already saved (Unsave).
  - `--much-more` is a topic already at Much more.
  - `--confirmation` is the confirmation after a choice, with Undo.
- `keyword-mute`:
  - `keyword-mute.png` shows the headline suggestions, with one picked.
  - `--typed` is a typed keyword.
  - `--blank` is an empty field (Mute disabled).
  - `--too-long` is a keyword over 60 characters.
- `story-group`: `story-group.png` is 2 outlets; `--many` is many outlets, scrolling.
- `onboarding`: `onboarding.png` is fewer than 3 selected; `--ready` is 3 or more.

## Design tokens

Colours are written `light · dark`. A token that is the same in both themes has one value. Light and dark are separate designs, not inversions:
- In light mode, the app bar and strips are navy on a light-blue page.
- In dark mode, the page itself is navy, the app bar is marked by a light-blue rule, cards are a lighter navy and strips a deeper navy.

### Colour

| name | value | usage |
|---|---|---|
| `paper` | light `#DCE5F2` · dark `#1D3A5F` | app background (dark: also the app bar) |
| `ink` | light `#1D3A5F` · dark `#F3F6FB` | headlines, primary text, row titles, button text on outline/text buttons |
| `ink-muted` | light `#4A6385` · dark `#B7C8DE` | secondary text, subtitles, helper text, field labels, intros, sheet meta |
| `navy` | `#1D3A5F` | light app bar, light `SplitRule`, light `GroupButton` fill |
| `sky` | `#DCE5F2` | wordmark; all text and icons on `navy`/`strip`; dark app-bar rule; dark `GroupButton` outline |
| `card` | light `#F5F8FC` · dark `#24456F` | article cards, panels, bottom sheets, bottom action bar |
| `field` | light `#EDF2F9` · dark `#24456F` | text-field fill; topic-chip fill (light only — dark chips are transparent) |
| `line` | light `#1D3A5F` · dark `#6F8DB5` | 2 dp borders: chips, fields, segmented controls, checkboxes, switches, outline buttons, tags; `BottomActionBar` top border; sheet grabber (dark) |
| `line-subtle` | light `#B3C3D9` · dark `#36597F` | 1 dp list and sheet dividers; sheet grabber (light) |
| `strip` | light `#1D3A5F` · dark `#142A47` | card header strip, panel header, no-image panel, offline banner, board panels, line across flap tiles |
| `tile` | light `#33557F` · dark `#2E5283` | flap-tile face (age, loader, rank) |
| `image-placeholder` | light `#C5D3E6` · dark `#2C4D78` | article image area before the image loads or if it fails |
| `image-hatch` | light `#B8C8DD` · dark `#33568A` | 1 dp lines at 135°, every 11 dp (every 9 dp on `SavedCard` thumbnails), over `image-placeholder` |
| `button` | light `#1D3A5F` · dark `#DCE5F2` | primary button fill, selected chip, selected segment, switch on, checkbox on |
| `on-button` | light `#DCE5F2` · dark `#1D3A5F` | text and icons on `button` |
| `disabled` | light `#B3C3D9` · dark `#36597F` | disabled button fill |
| `on-disabled` | light `#4A6385` · dark `#B7C8DE` | disabled button text |
| `icon` | light `#1D3A5F` · dark `#B7C8DE` | icon buttons and row icons on `card`/`paper` |
| `alert` | light `#B3261E` · dark `#FFB4A9` | error text, error field border, warning icons |
| `scrim` | light `rgba(13,27,46,0.55)` · dark `rgba(5,12,22,0.62)` | behind bottom sheets |
| `snackbar` | light `#1D3A5F` · dark `#DCE5F2` | Undo bar background |
| `on-snackbar` | light `#DCE5F2` · dark `#1D3A5F` | Undo bar text and action border |
| `shadow-snackbar` | `0 8 24 rgba(8,18,32,0.35)` | the only shadow in the app (Undo bar) |

### Fonts

Both fonts are under the SIL Open Font License 1.1 and are bundled in the app. Nothing is loaded from Google Fonts or any CDN.

| name | value | usage |
|---|---|---|
| `font-signage` | **Big Shoulders Display**, weights 700 and 800 (Patric King / XO Type Co. — github.com/xotypeco/big_shoulders, also on Fontsource) | all text shown in capitals: wordmark, nav buttons, screen, sheet and state titles, panel headers, chips, outlet names, flap tiles, buttons, snackbar actions, segmented controls, field and section labels, sheet meta lines, tags |
| `font-text` | **Schibsted Grotesk**, weights 400, 500, 600 and 700 (Schibsted — github.com/schibsted/schibsted-grotesk, also on Fontsource) | headlines, body, list rows, fields, messages |

### Type styles

Sizes and line heights are in sp, tracking is in em, and "caps" means uppercase text transform (see Deviations). Sizes with a line height are written size/line height.

| name | value | usage |
|---|---|---|
| `type-wordmark` | signage 800 · 40/40 · +0.04 · caps | "Newz" in the feed app bar (46/46 on onboarding) |
| `type-screen-title` | signage 800 · 28/30 · +0.04 · caps | sub-screen app-bar titles |
| `type-sheet-title` | signage 800 · 28/30 · +0.03 · caps | sheet titles ("Mute a keyword") |
| `type-state-title` | signage 800 · 34/36 · +0.02 · caps | empty, error and info panels; 30/33 for the three longer titles on `feed--all-filtered`, `search--no-feed` and `search--not-address` |
| `type-chip` | signage 700 · 19 · +0.06 · caps | topic and suggestion chips |
| `type-nav` | signage 700 · 18 · +0.08 · caps | app-bar text buttons (Saved, Settings, Add) |
| `type-outlet` | signage 700 · 18/22 · +0.08 · caps | outlet name in card strips and story rows (16 in the Saved list) |
| `type-button` | signage 800 · 19 · +0.08 · caps | all buttons and the group button; 18 for snackbar and banner actions |
| `type-panel-header` | signage 700 · 17 · +0.10 · caps | panel header strips |
| `type-meta` | signage 700 · 15 · +0.06 · caps, `ink-muted` | sheet header meta line |
| `type-label` | signage 700 · 15 · +0.10 · caps, `ink-muted` | field labels and section labels |
| `type-segment` | signage 700 · 16/16 · +0.05 · caps | segmented controls (a segment may wrap to 2 lines) |
| `type-tag` | signage 700 · 14 · +0.08 · caps | tags ("Paywall", "Following") |
| `type-tile` | signage 800 · 24 (18 on small tiles) · caps | flap-tile characters |
| `type-tile-display` | signage 800 · 128 · +0.02 · caps | topic name in the no-image panel |
| `type-headline` | text 700 · 21/27 · −0.01 | card headlines; 18/24 in the Saved list; 20/26 as the story-sheet title |
| `type-heading` | text 700 · 24/30 | onboarding heading |
| `type-body` | text 400 · 16/24 | empty-state body, onboarding promise |
| `type-row-title` | text 600 · 16/22 | list-row titles and the sheet header headline; 500 weight for sheet action rows and story-row headlines |
| `type-row-sub` | text 400 · 14/20 | row subtitles, helper text, counters |
| `type-intro` | text 400 · 15/22, `ink-muted` | screen intro lines |
| `type-field` | text 400 · 17 | text-field input |
| `type-message` | text 600 · 16/22 | snackbar message; banner message is 600 · 15/20 |

### Text size

| name | value | usage |
|---|---|---|
| `text-scale` | Small 0.875 · Default 1.0 · Large 1.15 · Extra large 1.33 | multiplies every `font-text` style in content |
| `text-scale-signage` | Small 0.9 · Default 1.0 · Large 1.12 · Extra large 1.25 | multiplies signage styles in content: outlet, chips, tiles, buttons, panel headers |

How text scaling behaves:
- The app-bar wordmark and app-bar nav buttons don't scale.
- All sizes are in sp, so the in-app setting stacks with the system font scale.
- At any scale, containers grow and text wraps; nothing is truncated or ellipsised, up to 200% system font size.
- At Extra large (`feed--long-headline-xl.png`):
  - Headline is 28/35.
  - Outlet is 23/28.
  - Tiles are 30×40 dp with 30 sp characters, and the line across sits at 19 dp.
  - Chips are 56 dp tall with 24 sp text.
  - The strip gets 10 dp of top and bottom padding and grows; the outlet name wraps over 2 lines, with the tiles top-aligned.
  - Icons, `CardActions` and the 48 dp touch targets stay the same size.

### Space and size (dp)

| name | value | usage |
|---|---|---|
| `space-2xs` | 2 | gap between app bar and `SplitRule` |
| `space-xs` | 4 | small internal gaps |
| `space-s` | 8 | gap between chips, between buttons, chip-row and wrap gaps |
| `space-m` | 12 | screen side margin for cards, panels, banners and the snackbar; chip-row side padding |
| `space-card-gap` | 14 | vertical gap between cards and between panels; top padding under app bar on sub-screens |
| `space-l` | 16 | text inset inside cards, rows and notes |
| `space-xl` | 20 | sheet side padding; empty-panel side padding; sheet action-row side padding |
| `space-2xl` | 28 | empty-panel top padding |
| `touch-target` | 48 | minimum size of every tappable element |
| `status-inset` | 28 | system status bar (drawn edge to edge; use the real inset) |
| `app-bar-height` | 64 | app-bar content row, below the status inset |
| `strip-height` | 48 min | card header strip (40 in the Saved list) |
| `chip-height` | 48 | topic chips |
| `button-height` | 48 | all buttons |
| `field-height` | 56 | text fields (52 for the search field in the app bar) |
| `row-height` | 64 min | list rows |
| `sheet-row-height` | 56 min | sheet action rows |
| `panel-header-height` | 40 min | panel header strip |
| `tile-size` | 24×32 (small 18×24), gap 3 | flap tiles |
| `image-ratio` | 16:9, full card width | article image (218 dp at a 388 dp card) |
| `no-image-height` | 104 | no-image panel |
| `thumb-size` | 72 | Saved-list thumbnail |
| `switch-size` | 56×32, thumb 20 | switch |
| `checkbox-size` | 24 | checkbox |
| `icon-size` | 22 (20 inside buttons and chips, 40 in state panels), 2 dp stroke, round caps and joins | all icons |
| `snackbar-min-height` | 60, 24 from bottom | Undo bar |

### Radius and border (dp)

| name | value | usage |
|---|---|---|
| `radius-control` | 3 | chips, buttons, fields, flap tiles, tags, segmented control, checkbox, snackbar |
| `radius-switch` | 4 (thumb 2) | switch |
| `radius-sheet` | 8, top corners | bottom sheets |
| `radius-grabber` | 2 | sheet grabber (40×4) |
| `radius-none` | 0 | cards, panels, strips, banners, image placeholders |
| `radius-app-icon` | 5 | tile inside the app icon (108 dp canvas) |
| `border-control` | 2 | chips, fields, buttons, segmented control, checkbox, switch |
| `border-tag` | 1.5 | tags |
| `divider` | 1 | list and sheet dividers (`line-subtle`) |
| `rule` | 2 | `SplitRule` (light) and dark app-bar bottom rule |

### Motion

| name | value | usage |
|---|---|---|
| `flap-flip` | 180 ms per character, ease-in; top half drops past the line | loading board, pull to refresh, age tiles updating after a refresh |
| `sheet` | 250 ms, standard decelerate | bottom sheets in and out |
| `card-collapse` | 200 ms height collapse | card removed after Hide / Don't show |
| `snackbar-timeout` | 6 s | Undo bar stays this long, then dismisses |

### Icons

All icons are original line drawings made for Newz, so they carry no licence terms. Bundle each as a vector drawable; nothing is fetched at run time.

How to draw them:
- 24×24 viewBox, no fill, stroke in the current colour.
- Stroke 2 (1.8 at 40 dp, 3 for the checkbox check), round caps and joins.
- Display size is set by `icon-size`.

`SVG` is the inner markup of `<svg viewBox="0 0 24 24">`.

| name | SVG | usage |
|---|---|---|
| `icon-back` | `<path d="M20 12H5M11 6l-6 6 6 6"/>` | app-bar Back |
| `icon-settings` | `<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>` | Settings nav button, "Review in Settings" |
| `icon-bookmark` | `<path d="M6.5 3.5h11v17l-5.5-4-5.5 4z"/>` | Saved nav button, Save for later, Saved row; drawn **filled** (fill current colour, no stroke) for Unsave |
| `icon-share` | `<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="M8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1"/>` | Share |
| `icon-more` | `<circle cx="12" cy="5" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="12" cy="19" r="2"/>` — **filled**, no stroke | More actions (⋮) |
| `icon-arrow-right` | `<path d="M4 12h15M13 6l6 6-6 6"/>` | `GroupButton`, onboarding Continue |
| `icon-chevron` | `<path d="M9 6l6 6-6 6"/>` | trailing chevron on settings rows |
| `icon-eye-off` | `<path d="M3 3l18 18M10.6 6.1A9 9 0 0 1 12 6c5 0 9 6 9 6a15.5 15.5 0 0 1-2.6 3.3M6.3 7.6C4.2 9.2 3 12 3 12s4 6 9 6c1.5 0 2.9-.4 4.1-1.1M9.9 9.9a3 3 0 0 0 4.2 4.2"/>` | Hide this article, Hidden articles row and empty state |
| `icon-arrow-up` | `<path d="M12 19V6M6 12l6-6 6 6"/>` | Show more like this |
| `icon-ban` | `<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>` | Don't show content from {outlet} |
| `icon-minus-circle` | `<circle cx="12" cy="12" r="9"/><path d="M8 12h8"/>` | Don't show {topic} |
| `icon-mute` | `<path d="M4 9h4l5-4v14l-5-4H4zM17 9.5l5 5M22 9.5l-5 5"/>` | Mute a keyword… |
| `icon-lock` | `<rect x="5" y="10.5" width="14" height="10" rx="1"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>` | Mark as paywalled, Paywall filter row |
| `icon-check` | `<path d="M5 12.5l4.5 4.5L19 7.5"/>` | selected chip, checkbox, success notes, "Already following" |
| `icon-close` | `<path d="M6 6l12 12M18 6L6 18"/>` | clear a field, unfollow/remove/unmute × |
| `icon-plus` | `<path d="M12 5v14M5 12h14"/>` | Add (app bar, feed found), custom-topic row |
| `icon-restore` | `<path d="M9 14L4 9l5-5M4 9h11a5 5 0 0 1 0 10h-4"/>` | Restore |
| `icon-retry` | `<path d="M20 12a8 8 0 1 1-2.4-5.7M20 4v5h-5"/>` | Retry buttons |
| `icon-warning` | `<path d="M12 3.5l9.5 17h-19zM12 10v4.5M12 17.5v.01"/>` | errors, in `alert` |
| `icon-wifi-off` | `<path d="M3 3l18 18M8.5 16.5a5 5 0 0 1 7 0M5 13a10 10 0 0 1 4.6-2.6M14.4 10.4A10 10 0 0 1 19 13M2 9.5a15 15 0 0 1 4.3-2.7M10 5.1A15 15 0 0 1 22 9.5"/><circle cx="12" cy="20" r="1"/>` | offline banner and states |
| `icon-rss` | `<path d="M5 4.5a14.5 14.5 0 0 1 14.5 14.5M5 10.5a8.5 8.5 0 0 1 8.5 8.5"/><circle cx="6" cy="18" r="1.5"/>` | Sources row, feed results, search hint |
| `icon-rss-off` | `icon-rss` plus `<path d="M15 4l5 5M20 4l-5 5"/>` | No feed found |
| `icon-search` | `<circle cx="10.5" cy="10.5" r="6.5"/><path d="M15.5 15.5L21 21"/>` | Find sources, search hint |
| `icon-shield` | `<path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z"/>` | privacy notes, About row |
| `icon-shield-check` | `icon-shield` plus `<path d="M9 12l2 2 4-4"/>` | Google refusal |
| `icon-tag` | `<path d="M3.5 12.5V4h8.5l8.5 8.5-8.5 8.5z"/><circle cx="8" cy="8.5" r="1.5"/>` | Topics & keywords row |
| `icon-compass` | `<circle cx="12" cy="12" r="9"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>` | Suggestions row and empty state |
| `icon-clock` | `<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>` | Reading history row |
| `icon-text-size` | `<path d="M4 18L9 6l5 12M5.8 14h6.4M15 18l3-7 3 7M15.9 16h4.2"/>` | Text size row |
| `icon-download` | `<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>` | Export, Backup & restore row |
| `icon-upload` | `<path d="M12 15V4M7 9l5-5 5 5M5 20h14"/>` | Choose a backup file |
| `icon-open` | `<path d="M14 4h6v6M20 4l-9 9M19 14v6H4V5h6"/>` | trailing on `StoryRow` (opens in browser) |
| `icon-spinner` | `<circle cx="12" cy="12" r="9" opacity="0.25"/><path d="M21 12a9 9 0 0 0-9-9"/>` — the arc rotates, 1 turn per 0.9 s | Looking for a feed… |

## Components

Each entry gives what the component is, where it appears, and its exact measurements. Sizes are in dp.

- `AppBarMain` — the feed's app bar (`feed`): `type-wordmark` "Newz" at the left and `NavButton`s Saved and Settings at the right, in a 64 dp row with padding 0 4 0 16 below `status-inset`; light uses a `navy` background, dark uses `paper` with a 2 dp `sky` bottom rule.
- `AppBarSub` — the app bar on every other full screen (`settings*`, `saved`, `search`, `suggestions`, `backup`): a back `IconButton` and `type-screen-title` in a 64 dp row with padding 0 8 0 4, an optional trailing `NavButton` ("Add" on `settings-sources`) or a `SearchField` in place of the title (`search`); same colours as `AppBarMain`.
- `SplitRule` — under every app bar in light theme only: a 2 dp `paper` gap, then a 2 dp `navy` line, echoing the line across the flap tiles; dark has no rule here, because its app bar carries the `sky` bottom rule.
- `NavButton` — the text buttons in app bars (Saved, Settings, Add): 48 dp tall, padding 0 10, a 20 dp icon, gap 6, and `type-nav`, all in `sky`.
- `IconButton` — every icon-only control (Back, Share, ⋮, Unsave, clear ×, unfollow ×): a 48×48 target with a 22 dp icon in `icon`, or in `sky` when it sits directly on an app bar; the clear × inside a field always uses `icon`; it always has a spoken label.
- `TopicChipRow` — the horizontally scrolling chip row at the top of `feed`: padding 10 12 12 (12 12 12 in dark), gap 8; it scrolls away with the list.
- `TopicChip` — topic chips in `feed` and `onboarding`, headline suggestions in `keyword-mute`, and muted keywords in `settings-topics`: 48 tall, padding 0 16, a 2 dp `line` border, `radius-control` and `type-chip`; the fill is `field` in light and transparent with `sky` text in dark; selected chips get a `button` fill, `on-button` text and a leading 20 dp `icon-check` with gap 6; muted-keyword chips have a trailing `icon-close`.
- `ArticleCard` — each article in `feed` (and behind the sheets): a `card` surface holding a `CardStrip`, the headline in `type-headline` with padding 14 16, then an `ArticleImage` or `NoImagePanel`, then `CardActions`; tapping anywhere outside its buttons opens the article.
- `CardStrip` — the header band of `ArticleCard`: `strip`, min height 48, padding 0 8 0 14, gap 12; the outlet name is in `type-outlet` `sky` (it wraps and is never truncated) with `FlapAge` at the right; at Extra large it gets 10 dp of top and bottom padding and the tiles top-align.
- `FlapAge` — the article age in `CardStrip`, `StoryRow` and `SavedCard`: one `FlapTile` per character of the age string ("now", "5m", "3h", "2d"), gap 3; it is spoken as a single label, e.g. "2 hours ago".
- `FlapTile` — the split-flap character used by `FlapAge`, `LoadingBoard`, `RankTile` and the END board: a `tile` rectangle, 24×32 (18×24 small), `radius-control`, with a centred `type-tile` character in `sky` and a 2 dp `strip` line across it 15 dp from the top (11 on small tiles); build it from live text, not images.
- `ArticleImage` — the article photo in `ArticleCard`: full card width at 16:9 (218 dp on a 388 dp card), cropped to fill; it shows `image-placeholder` with `image-hatch` lines every 11 dp until it loads, and keeps that if loading fails; it is the only thing fetched from an article's site.
- `NoImagePanel` — replaces `ArticleImage` when an article has no image (`feed.png`, second card): a 104 dp `strip` panel with the article's first topic in `type-tile-display` `sky`, 12 from the left, its baseline set 26 dp below the panel's bottom edge so the word is cropped; decorative and hidden from screen readers.
- `CardActions` — the bottom row of `ArticleCard`: 64 tall, padding 0 4 0 12, with a `GroupButton` at the left on grouped cards only and Share and ⋮ `IconButton`s at the right.
- `GroupButton` — "+n sources" on grouped cards: 48 tall, padding 0 14, a trailing 20 dp `icon-arrow-right` with gap 10, `type-button`, `radius-control`; light uses a `navy` fill with `sky` text, dark is transparent with a 2 dp `sky` border, padding 0 12.
- `Button` — every text button: 48 tall, padding 0 20, an optional leading 20 dp icon with gap 8, `type-button`, `radius-control` and a 2 dp border; Primary is `button`/`on-button`, Outline has a `line` border with `ink` text, Text has no visible border with `ink` text and padding 0 12, Disabled is `disabled`/`on-disabled`, and Wide is full width.
- `Snackbar` — the Undo bar (`feed--undo`, `card-menu--confirmation` and every "(not drawn)" confirmation in Copy): it floats 12 from the sides and 24 above the navigation bar, min height 60, padding 6 6 6 16, gap 12, with a `snackbar` background, `type-message` in `on-snackbar`, `radius-control`, `shadow-snackbar` and a `SnackbarAction`; it lasts for `snackbar-timeout`.
- `SnackbarAction` — "Undo" or "Retry" inside `Snackbar` and `OfflineBanner`: 48 tall, padding 0 14, a 2 dp border in the text colour, `type-button` at 18.
- `OfflineBanner` — the offline notice on `feed--offline-saved` and `saved--offline`: a band with 12 side margins, margin-bottom 14, padding 6 6 6 14 and gap 12; it has a `strip` background, a 22 dp `icon-wifi-off`, the message in 600 15/20 and a `SnackbarAction` "Retry", all in `sky`.
- `StatePanel` — every empty, error and info panel (feed filter-empty, all-filtered and offline-empty, the empty states, search no-feed and not-address): a `card` block with margin 4 12 14 (so it sits 18 below the app bar) and padding 28 20 24; it holds an optional 40 dp icon in `ink` (in `alert` for errors) with 16 below, a `type-state-title`, `type-body` in `ink-muted` with 10 above, and an optional `Button` with 20 above.
- `BoardPanel` — a `StatePanel` on a `strip` background with title, body, icon and outline-button borders in `sky`: used for the end of `feed` (three `FlapTile`s spelling "END", 18 above the title) and for the Google refusal in `search`.
- `LoadingBoard` — the loading indicator on `feed--loading`: a row with padding 4 4 2 and gap 14, holding four `FlapTile`s that flip through letters until they spell "NEWZ" and then repeat, beside `type-row-title` "Loading today’s news…"; the tiles alone are the pull-to-refresh indicator.
- `SkeletonCard` — the placeholder cards on `feed--loading`, hidden from screen readers: an `ArticleCard` shape whose strip holds a 12 dp bar in `tile` and blank `FlapTile`s, whose headline area (padding 18 16, gap 10) holds 16 dp bars in `image-placeholder`, followed by a plain `image-placeholder` image block and an empty 64 dp actions area.
- `BottomSheet` — the container for `card-menu`, `keyword-mute` and `story-group`: a `card` surface with `radius-sheet` over `scrim`, with a 40×4 grabber (`line-subtle` in light, `line` in dark) 10 from the top and 4 above the content; it is dismissed by a swipe down, a tap on the scrim, or Back.
- `SheetActionRow` — each action in `card-menu`: min height 56, padding 0 20, a 22 dp icon in `icon`, gap 16, and the label in `type-row-title` at weight 500; the disabled variant turns label and icon `ink-muted`, adds an explanation line in `type-row-sub` 600 `ink`, and can't be tapped.
- `Panel` — the grouped lists on every settings-style screen: a `card` block with 12 side margins and 14 below, made of a `PanelHeader` followed by `ListRow`s or other content.
- `PanelHeader` — the title band of a `Panel`: min height 40, padding 0 14, a `strip` background, and the title in `type-panel-header` at the left with an optional count in the same style at the right, both `sky`.
- `ListRow` — the rows in `Panel`s: min height 64, padding 8 8 8 16, a 1 dp `line-subtle` divider above (none directly under a `PanelHeader`), an optional 22 dp leading icon in `icon` with gap 14, a title in `type-row-title` and a subtitle in `type-row-sub`, and an optional trailing `icon-chevron`, `Switch` or `Button`; when the row navigates, the whole row is the touch target.
- `Switch` — the Paywall filter and Reading history toggles in `settings`: a 56×32 track with a 2 dp `line` border and `radius-switch`, and a 20 dp thumb with radius 2; off is a transparent track with a `line` thumb 4 from the left, on is a `button` track and border with an `on-button` thumb 4 from the right.
- `SegmentedControl` — Text size in `settings` and the topic levels in `settings-topics`: 48 tall with a 2 dp `line` border and `radius-control`; its equal segments are divided by 2 dp `line` and set in `type-segment`; the selected segment is `button`/`on-button`.
- `Checkbox` — section feeds in `settings-sources` and "Include reading history" in `backup`: 24 square with a 2 dp `line` border and `radius-control`; when checked it has a `button` fill and a 16 dp `icon-check` (stroke 3) in `on-button`; its 48 dp row is the touch target.
- `Tag` — small outline labels ("Paywall", "Following") in `settings-sources`, `story-group`, `search` and `suggestions`: 24 tall, padding 0 7, a 1.5 dp `line` border, `radius-control` and `type-tag`.
- `TextField` — the keyword field in `keyword-mute`: a `type-label` above with 6 below, then a 56 dp field with padding 0 4 0 14, a `field` fill, a 2 dp `line` border and `radius-control`, holding the `type-field` input and a trailing clear `IconButton` once there is text; below it, 6 down, helper text at the left and a counter at the right in `type-row-sub`; in the error state the border, message (with `icon-warning`) and counter turn `alert`.
- `SearchField` — the field in the `search` app bar: a `TextField` with no label, 52 tall, padding 0 0 0 14, a `field` fill, and a border in `field` (light) or `line` (dark).
- `StoryRow` — each outlet in `story-group`: the whole row is tappable, with padding 14 12 14 20, gap 12 and a divider above; the outlet in `type-outlet` `ink` with an optional `Tag` (gap 8) sits at the left with a small `FlapAge` at the right, the headline sits 6 below in `type-row-title` at weight 500, and a trailing 22 dp `icon-open` is in `icon`.
- `SavedCard` — each article in `saved`: a 40 dp strip with the outlet at 16 sp and a small `FlapAge`; then a row with padding 12 12 0 16 and gap 12, holding an 18/24 headline and a 72 dp thumbnail (hatch every 9 dp; with no image, a `strip` square with the topic in signage 800 20 `sky`); then a 52 dp action row with Share and Unsave (`icon-bookmark` filled) at the right.
- `RankTile` — the rank number at the start of each row in `suggestions`: one full-size `FlapTile`, spoken as "Rank {n}".
- `NoteRow` — the search hints, the suggestions privacy note, and the result and confirmation notes in `search` and `backup`: padding 14 16, a 22 dp icon in the text colour, gap 12, top-aligned, with text at 15/22 in `ink`, or a `type-row-title` line over a `type-row-sub` line.
- `BottomActionBar` — the bar pinned to the bottom of `onboarding`: a `card` surface with a 2 dp `line` top border, padding 12 12 24 16 and gap 12; its status is at the left in `type-row-title` (with an optional `type-row-sub` line below) and a Primary `Button` is at the right.
- `AppIcon` — the adaptive launcher icon, also shown at 56 dp on `onboarding`; see `app-icon`.

## Screens

### feed — Feed

Layout, top to bottom:
- `AppBarMain`, pinned.
- `SplitRule` (light only).
- `TopicChipRow`, showing the user's active topics in the order they follow them.
- A list of `ArticleCard`s with 12 dp side margins and 14 dp gaps. The chip row scrolls away with the list.

Cards:
- The newest and most relevant come first. The feed holds articles up to 7 days old.
- A grouped card shows the story once, with a `GroupButton` "+{n} sources", where n is the number of other outlets covering it. When n is 1 it reads "+1 source".

Interactions:
- Tapping a card opens the article in an in-app browser tab (Custom Tab).
- Share opens the Android share sheet with the article URL.
- ⋮ (or a long-press on the card, as a shortcut) opens `card-menu`.
- "+n sources" opens `story-group`.
- Saved opens `saved`; Settings opens `settings`.
- Tapping a chip toggles it, and several can be selected. With any chip selected, the feed shows only articles in those topics.
- Pull to refresh slides a `paper` panel holding a `LoadingBoard` (4 tiles, no text, 12 above and below) down from under the app bar with the pull, and moves the list (chip row first) down by as much, so the tiles never cover the chips; it stays until new articles are merged in. Age tiles that change flip.

States:
- `feed.png` (populated) — a grouped card ("+3 sources") and a card with no image (`NoImagePanel` showing the topic); a third card starts below the bottom edge.
- `--loading` (first load):
  - Chips show immediately.
  - Below them, a `LoadingBoard` with "Loading today’s news…" and two `SkeletonCard`s.
  - Real cards replace the skeletons as they arrive; the first should arrive within 5 s.
- `--long-headline-xl` — Extra large text size. The outlet name wraps over 2 lines, the tiles grow, and the headline runs to 6 lines, while the card keeps its image and `CardActions`; the next card follows. See Text size.
- `--filter-active`:
  - Selected chips get a check and fill.
  - Under the chip row is a line "Showing {topics}" in `type-row-sub` 600, padding 0 4 6 16, with a Text `Button` "Clear filter" at the right.
- `--filter-empty`:
  - The selected chip stays scrolled into view.
  - A `StatePanel` shows "No {topic} articles right now", body text, and a Primary "Clear filter".
- `--undo`:
  - After Hide this article, the card collapses (`card-collapse`).
  - A `Snackbar` "Article hidden. Bring it back in Settings › Hidden articles." with "Undo" appears for 6 s. Undo puts the card back in place.
- `--end`:
  - After the last article, a `BoardPanel` shows tiles spelling "END", "You're all caught up", and body text.
  - The app bar stays pinned.
- `--all-filtered` — a `StatePanel` titled "Nothing to show — your filters hide everything" (30/33), with body text and a Primary `Button` with `icon-settings`, "Review in Settings", which opens `settings`.
- `--offline-saved`:
  - An `OfflineBanner` "Offline — showing saved articles" with "Retry" sits above the list.
  - The list shows only saved articles as full `ArticleCard`s (Share and ⋮ still work), newest saved first.
  - Retry tries to load; the banner disappears on success.
- `--offline-empty` — a `StatePanel` with a 40 dp wifi-off icon, "You’re offline", body text, and a Primary "Retry" with a retry icon.
- `--dark`:
  - The app bar is `paper` with a 2 dp `sky` rule instead of the navy band, and there's no `SplitRule`.
  - Chips are transparent with `sky` text.
  - The `GroupButton` is an outline.

### card-menu — Article actions

- A `BottomSheet` over the dimmed feed, opened by ⋮ or a long-press.
- Header (padding 8 20 14, divider below): a meta line "{outlet} · {age}" (`type-meta`) above the full headline (`type-row-title`, wraps).

Rows (`SheetActionRow`), in order:
1. Save for later — `icon-bookmark`. Shows "Unsave" with the filled bookmark when the article is already saved.
2. Hide this article — `icon-eye-off`.
3. Show more like this — `icon-arrow-up`. Raises the article's main topic one level (Normal, then More, then Much more).

A 1 dp `line-subtle` divider, inset 20 dp at each side with 4 dp above and below, then:

4. Don't show content from {outlet} — `icon-ban`.
5. Don't show {topic} — `icon-minus-circle`. One row per topic the article has.
6. Mute a keyword… — `icon-mute`. Opens `keyword-mute` and closes this sheet.
7. Mark {outlet} as paywalled — `icon-lock`.

The sheet has 20 dp of padding below the last row.

Behaviour:
- Choosing any row except Mute a keyword… closes the sheet and shows the confirmation `Snackbar` with "Undo"; see Copy for each message.
- Removed content collapses out of the feed straight away.

States:
- `card-menu.png` — normal.
- `--saved` — row 1 reads "Unsave", with a filled icon.
- `--much-more` — the article's topic is already at Much more. Row 3 becomes the disabled `SheetActionRow`, with "Already showing much more of {topic}" under the label.
- `--confirmation` — after "Don't show content from The Harbor Ledger": the sheet has closed, that outlet's cards are gone, and the snackbar reads "The Harbor Ledger won’t appear. Bring it back in Settings › Sources." with "Undo".
- `--dark`.

### keyword-mute — Mute a keyword

A `BottomSheet` with side padding 20 and bottom padding 24, containing:
- `type-sheet-title` "Mute a keyword", then an intro line (`type-intro`).
- The label "From this headline", with a wrap of up to 5 `TopicChip`s holding words and names taken from the headline.
- The `TextField` "Keyword or name", with helper text and a counter "{n}/60".
- Right-aligned buttons: Text "Cancel" and Primary "Mute".

Behaviour:
- The field takes focus and the keyboard opens; the sheet sits above the keyboard (the keyboard isn't drawn).
- Tapping a suggestion fills the field and selects that chip; only one can be selected. Typing deselects it.
- Matching is case-insensitive and on whole words.
- Mute closes the sheet, removes matching articles from the feed and shows a `Snackbar` with "Undo".

States:
- `keyword-mute.png` — "Antarctica" picked from the suggestions; Mute enabled.
- `--typed` — "Elon Musk" typed, no chip selected.
- `--blank` — empty field showing the placeholder, counter "0/60", Mute disabled.
- `--too-long` — 65 characters typed: error border, the message "Keep it to 60 characters or fewer" with a warning icon, counter "65/60" in `alert`, Mute disabled. Input isn't cut off at 60; the user edits it down.
- `--dark`.

### story-group — Same story, other outlets

A `BottomSheet` opened by "+n sources", containing:
- Header (padding 8 20 16): meta line "Same story · {n} outlets", then the story headline (`type-headline` at 20/26).
- One `StoryRow` per outlet, including the one shown in the feed, sorted newest first.
- Catalog-paywalled outlets show a "Paywall" `Tag`.

Tapping a row opens that outlet's article in the browser tab.

States:
- `story-group.png` (2 outlets) — the sheet hugs its content and ends with the note "Each article opens in your browser."
- `--many` — the sheet expands to just below the app bar (top edge at 92 dp). The list scrolls inside the sheet with a 4 dp `line-subtle` scroll indicator. The feed behind stays dimmed.
- `--dark`.

### onboarding — Pick your topics

Shown on first launch only. Layout:
- A header band in `navy`, with 20 dp side padding and 22 dp bottom padding:
  - 18 dp below the status inset, the app icon at 56 dp beside the wordmark at 46/46 sp, gap 14. The icon is its 72 dp visible area scaled to 56 dp, with corners rounded at 16/72 of its size (about 12 dp).
  - 18 dp below that, `type-heading` "News that stays on your phone."; 8 dp below, the privacy sentence in `type-body`. Both are in `sky`.
- Then `SplitRule`, then 18 16 padding, the label "Pick at least 3 topics", and 20 `TopicChip`s in a wrap with gap 8.
- `BottomActionBar` pinned at the bottom.

Behaviour:
- Tapping a chip toggles it.
- Continue saves the topics and opens `feed` in its loading state.
- Topics can be changed later in `settings-topics`.

States:
- `onboarding.png` (fewer than 3 picked) — the bar reads "{n} picked — pick at least 3", and Continue is disabled.
- `--ready` (3 or more) — the bar reads "{n} picked" over "Change them any time in Settings", and Continue is enabled with an arrow.
- `--dark` — the header is `paper` with a `sky` rule.

### settings — Settings home

Layout: `AppBarSub` "Settings" with back, then three `Panel`s, 14 apart.

Your feed — `ListRow`s with a leading icon, title, a live subtitle and a chevron:
- Sources (rss icon)
- Topics & keywords (tag icon)
- Hidden articles (eye-off icon)
- Saved (bookmark icon)
- Find sources (search icon)
- Suggestions (compass icon)

Each opens its screen: `settings-sources`, `settings-topics`, `settings-hidden`, `saved`, `search`, `suggestions`.

Reading:
- Paywall filter — `Switch`. When on, hides outlets that are paywalled in the catalog or marked by the user.
- Reading history — `Switch`. Turning it off stops recording but doesn't clear what's stored.
- "Clear reading history" — Outline `Button`, indented to line up with the text (52 dp). Opens a `ConfirmSheet` (a `BottomSheet` with the title, the message in `type-intro` `ink-muted`, and Cancel (Text) / "Clear history" (Primary) at the right, as on the zip code sheet); "Clear history" clears and shows a `Snackbar` "Reading history cleared" with no Undo.
- Text size — row title with an icon, then a full-width `SegmentedControl` (Small / Default / Large / Extra large). Applies immediately; see Text size.

Your data:
- Backup & restore — opens `backup`.
- About — opens a plain text page with the privacy statement and font credits, using `type-body` on `paper`. Not drawn.

States: default only (`settings.png`, `--dark`). The screenshot shows the whole scroll.

### settings-sources — Sources

Layout: `AppBarSub` "Sources" with a trailing `NavButton` "Add" (plus icon), which opens `search`.

Panel "Following" (with count):
- One row per outlet: name, subtitle ("{n} of {m} section feeds" or "All {m} section feeds"), and a Text `Button` "Remove".
- Tapping a row expands its section feeds as 48 dp `Checkbox` rows; the first outlet is shown expanded.
- Outlets the catalog marks as paywalled show a "Paywall" `Tag` after the name, plus "· hidden while the paywall filter is on".
- After 4 outlets, a centred Text `Button` "Show all {n}".

Panel "Marked paywalled by you" (with count): rows with the subtitle "Hidden while the paywall filter is on" and "Unflag".

Panel "Removed" (with count): rows with "Removed {date}" and a Text `Button` "Restore" with a restore icon.

Behaviour:
- Remove moves the outlet to Removed and shows a `Snackbar` "{outlet} removed" with "Undo".
- Restore and Unflag act immediately.

States:
- `settings-sources.png` — populated.
- `--failing` — a failing feed is pinned at the top of Following, with "Not loading since {date}" in `alert` with a warning icon, and buttons Outline "Retry" and Text "Remove".
- `--none-removed` — the Removed panel shows count 0 and a hint line.
- `--dark`.

### settings-topics — Topics & keywords

Layout: `AppBarSub` "Topics & keywords".

Panel "Followed topics" (with count):
- Each row: topic name, an × `IconButton` (spoken "Unfollow {topic}"), and below it a `SegmentedControl` Normal / More / Much more.
- After 3 topics, "Show all {n}".

Panel "Your keyword topics" — the same row layout for custom keyword topics, with × spoken "Remove {topic}".

Panel "Muted topics" — rows with "Restore".

Panel "Muted keywords" — a wrap of `TopicChip`s, each with a trailing × (spoken "Unmute {keyword}"). Tapping one unmutes it.

Behaviour:
- Changing a level applies immediately.
- Unfollowing removes the topic's chip from the feed and shows a `Snackbar` with "Undo".

States:
- `settings-topics.png` — populated.
- `--no-keywords` — the Muted keywords panel shows count 0 and a hint line.
- `--much-more` — Science is at Much more, with the note under its control: "Science is at the top level, so Show more like this is turned off for it."
- `--dark`.

### settings-hidden — Hidden articles

Layout: `AppBarSub` "Hidden articles", an intro line (`type-intro`, 0 16 12), then Panel "Hidden" (with count).

Rows:
- Headline (`type-row-title`, wraps), with "{outlet} · Hidden {when}" below.
- Text `Button` "Restore" at the right, top-aligned.
- Newest hidden first.

Behaviour: Restore removes the row and puts the article back in the feed if it's still under 7 days old.

States:
- `settings-hidden.png` — populated.
- `--empty` — a `StatePanel` with a 40 dp eye-off icon, "No hidden articles" and a hint.
- `--dark`.

### saved — Saved for later

Layout: `AppBarSub` "Saved", then a list of `SavedCard`s (12 margins, 14 gaps), newest saved first.

Behaviour:
- Tapping a card opens the article.
- Share opens the share sheet.
- Unsave (filled bookmark) removes the card and shows a `Snackbar` "Removed from Saved." with "Undo".
- Saved articles stay saved past the 7-day feed window until they're unsaved.

States:
- `saved.png` — populated.
- `--empty` — a `StatePanel` with a bookmark icon, "Nothing saved yet" and a hint.
- `--offline` — an `OfflineBanner` with the saved-list message and "Retry" sits above the still-complete list.
- `--dark`.

### search — Find sources & topics

Layout: `AppBarSub` with a `SearchField` in place of the title; it takes focus on open, and shows a × to clear when it has text.

As the user types a word, results appear grouped in `Panel`s, searched on the phone from the built-in catalog:
- Outlets: name, "{topic} · {n} section feeds" (or "· 1 feed"), an optional Paywall `Tag`, and an Outline "Follow".
- Topics: name and description with "Follow", or "In your topics" with a "Following" `Tag`.
- A final row "Add '{word}' as a custom topic" (plus icon), shown when the word isn't an existing topic.

When the text looks like a web address (it contains a dot or a scheme), the app checks that site alone:
- A "Web address" panel shows a spinner row "Looking for a feed…" with the note "Only {address} is contacted to check."
- Then it shows one result:
  - Feed found
  - No feed found on this site
  - That doesn't look like a web address
  - Already following
  - Google refusal

Google-owned domains are refused before any request is made. This includes google.*, news.google.com, feedburner.com, blogspot.com, youtube.com and other Google domains; keep the list in the app.

Behaviour:
- Follow turns into the "Following" tag.
- Add follows the feed, and the outlet appears in `settings-sources`.

States:
- `search.png` (empty field) — hint panel with three `NoteRow`s: search, rss, and privacy (shield).
- `--results` — query "solar".
- `--checking` — spinner row.
- `--feed-found` — rss icon, site name, "{feed URL} · RSS" (or Atom), the latest item, and a right-aligned Primary "Add".
- `--no-feed` — `StatePanel` with a crossed rss icon.
- `--not-address` — `StatePanel` with a warning icon in `alert`.
- `--already-following` — the found feed, with a disabled `Button` "Already following" with a check.
- `--google` — `BoardPanel` with a shield-check icon, "Newz never contacts Google" and an explanation.
- `--dark` — results.

### suggestions — Suggested for you

Layout:
- `AppBarSub` "Suggested for you".
- A `NoteRow` with a shield icon and the on-device statement.
- Panel "Outlets", then Panel "Topics".

Each row: `RankTile`, name, "{topic} · {reason}" (topic rows show the reason only), and an Outline "Follow".

How suggestions work:
- Ranks are worked out on the phone from follows, co-coverage and, if Reading history is on, reading.
- No reading counts or statistics are ever shown.
- Follow turns into a "Following" `Tag`.

States:
- `suggestions.png` — populated.
- `--empty` — a `StatePanel` with a compass icon, "No suggestions right now", a hint, and an Outline "Find sources" that opens `search`.
- `--dark`.

### backup — Backup & restore

Layout: `AppBarSub` "Backup & restore", then an intro line.

Panel "Export":
- A `Checkbox` row "Include reading history", off by default.
- A full-width Primary "Export to a file" with a download icon. It opens the Android system file picker (Storage Access Framework, create document) with the name `newz-backup-{yyyy-mm-dd}.json`.

Panel "Import":
- An intro line and a full-width Outline "Choose a backup file" with an upload icon. It opens the system picker.
- A valid file replaces all choices on the phone.

Behaviour: no cloud and no network — the file goes wherever the user saves it.

States:
- `backup.png` — default.
- `--exported` — a check `NoteRow` "Backup saved" with the file name and folder, under the export button.
- `--imported` — a check `NoteRow` "Backup imported" with counts.
- `--import-failed` — an `alert` `NoteRow` with a warning icon. Nothing is changed.
- `--dark`.

### app-icon — Home-screen icon (approved addition)

An Android adaptive icon on a 108×108 dp canvas. All artwork sits inside the 66 dp safe zone, so it survives every launcher mask: circle, squircle and rounded square. `app-icon.png` shows the full canvas with guides, the masks, the themed variant, and the icon at real size on dark and light wallpapers.

Layers:
- Background layer: solid `#DCE5F2`.
- Foreground layer: navy tile, light-blue N, and a light-blue split line through the tile.
- Monochrome layer (Android 13+ themed icons): the tile shape with the N and the split line knocked out. The system tints it; the themed example in the PNG uses a sample wallpaper palette.

Other sizes:
- Legacy icon and Play Store icon (512×512 px): render the full square (background plus foreground) with no mask.
- Launcher label: "Newz".

```svg
<!-- foreground (108 dp) -->
<svg viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
  <rect x="33" y="29" width="42" height="50" rx="5" fill="#1D3A5F"/>
  <path d="M42 71V37h7l10 21V37h7v34h-7L49 50v21z" fill="#DCE5F2"/>
  <rect x="33" y="53" width="42" height="2" fill="#DCE5F2"/>
</svg>
<!-- background: <rect width="108" height="108" fill="#DCE5F2"/> -->
<!-- monochrome: the tile with the N and the split rect as holes (even-odd), single colour -->
```

## Deviations from the brief

- The brief lists no app icon among the screens to design → the design adds an adaptive home-screen icon (`app-icon`): "Flap tile", a light-blue N on a navy split-flap tile on light blue, with a monochrome layer for themed icons — the installed app needs an icon; Kyle asked for one, chose Logo 1 from four options, and approved it in the blue palette.
- The brief's decided copy is `+<n> sources` for grouped cards → when n is 1 the card reads `+1 source`, and n of 2 or more is unchanged — so the one-outlet case reads correctly; Kyle approved.
- The brief's decided copy is written in sentence case → all text set in `font-signage` displays in capitals through text transform (wordmark, nav buttons, titles, panel headers, chips, outlet names, age tiles — "2h" shows as "2H" — buttons, segmented controls such as Normal / More / Much more and Small … Extra large, labels, sheet meta lines and tags), while the underlying strings and what screen readers announce stay exactly as in Copy — the capitals are part of the departure-board signage look; Kyle approved it with the chosen direction.
- The brief gives followed topics and keyword topics only a level control, and says outlets can be added on `settings-sources` without saying how → `settings-topics` has an × on each followed topic (Unfollow) and each keyword topic (Remove), and `settings-sources` has an "Add" button in its app bar that opens `search` — so every choice can be undone and sources added from the screen that manages them; Kyle approved both.

## Copy

Strings are exact. Signage-styled text (see `font-signage`) displays in capitals, but screen readers get the strings in the case written here. Variable parts are in `{braces}`. Lines marked (spoken) are accessibility labels only. Apostrophes are deliberate: the brief's decided strings use a straight ' (`You're all caught up`, `Don't show…`, `That doesn't look like a web address`, `Add '{word}'…`), and everything else uses the typographic ’ — copy each string exactly and don't normalise either one. Lines marked (not drawn) appear in no screenshot — mostly the Undo-bar messages for actions whose result isn't pictured — and use the same `Snackbar` as `feed--undo.png`.

### feed
- `Newz`
- `Saved`
- `Settings`
- `Topics` (spoken, chip row)
- `{topic}` (e.g. `World`, `Science`, `Climate`, `Tech`, `Business`, `Health`, `Seattle`)
- `{outlet}` (e.g. `The Harbor Ledger`)
- `{age}` (e.g. `now`, `5m`, `3h`, `2d`)
- `just now` (spoken, age tiles for "now"; not drawn)
- `1 minute ago` (spoken; not drawn)
- `{n} minutes ago` (spoken, e.g. `45`)
- `1 hour ago` (spoken)
- `{n} hours ago` (spoken, e.g. `2`)
- `1 day ago` (spoken)
- `{n} days ago` (spoken, e.g. `6`)
- `{headline}` (e.g. `Deep-sea survey maps the currents carrying warm water toward Antarctica’s ice shelves`)
- `Article image` (spoken)
- `+{n} sources` (e.g. `+3 sources`)
- `+1 source` (when n is 1; not drawn)
- `Share` (spoken)
- `More actions` (spoken)
- `Loading today’s news…`
- `Showing {topics}` (e.g. `Showing Science and Climate`; three or more: `Science, Climate and Tech`)
- `Clear filter`
- `No {topic} articles right now` (e.g. `Health`; several selected: `Health or Space`)
- `New {topic} stories from your sources will show up here as soon as they’re published.`
- `Article hidden. Bring it back in Settings › Hidden articles.`
- `Undo`
- `END`
- `You're all caught up`
- `That’s everything from the past 7 days. Pull down any time to check for something new.`
- `Nothing to show — your filters hide everything`
- `The topics, sources and keywords you’ve turned off cover every article from the past 7 days. You can review and undo any of them.`
- `Review in Settings`
- `Offline — showing saved articles`
- `Retry`
- `You’re offline`
- `New articles load as soon as you’re connected again. Anything you save for later will be listed here even when you’re offline.`

### card-menu
- `Article actions` (spoken, sheet)
- `{outlet} · {age}` (e.g. `The Harbor Ledger · 2h`)
- `{headline}`
- `Save for later`
- `Unsave`
- `Hide this article`
- `Show more like this`
- `Already showing much more of {topic}` (e.g. `Science`)
- `Don't show content from {outlet}` (e.g. `The Harbor Ledger`)
- `Don't show {topic}` (e.g. `Science`)
- `Mute a keyword…`
- `Mark {outlet} as paywalled`
- `Saved for later. Find it in Saved.` (snackbar after Save for later; not drawn)
- `Removed from Saved.` (snackbar after Unsave; not drawn)
- `Article hidden. Bring it back in Settings › Hidden articles.`
- `Showing more like this` (snackbar; not drawn)
- `{outlet} won’t appear. Bring it back in Settings › Sources.` (e.g. `The Harbor Ledger`)
- `{topic} won’t appear. Bring it back in Settings › Topics & keywords.` (e.g. `Science`; not drawn)
- `{outlet} marked as paywalled. Change it in Settings › Sources.` (snackbar; not drawn)
- `Undo`

### keyword-mute
- `Mute a keyword`
- `Articles that mention it are hidden everywhere in your feed. You can undo this in Settings › Topics & keywords.`
- `From this headline`
- `{suggestion}` (e.g. `Deep-sea`, `Currents`, `Antarctica`, `Ice shelves`, `Warm water`)
- `Keyword or name`
- `Type a word or name`
- `{keyword}` (e.g. `Antarctica`, `Elon Musk`)
- `Clear` (spoken)
- `Matches the whole word, in any capitalization`
- `{n}/60` (e.g. `10/60`)
- `Keep it to 60 characters or fewer`
- `Cancel`
- `Mute`
- `Muted “{keyword}”. Unmute it in Settings › Topics & keywords.` (e.g. `Antarctica`; snackbar; not drawn)
- `Undo`

### story-group
- `Same story · {n} outlets` (e.g. `7`)
- `{headline}` (e.g. `Solar storm could push auroras farther south this weekend`)
- `{outlet}`
- `{age}`
- `Paywall`
- `Each article opens in your browser.`

### onboarding
- `Newz`
- `News that stays on your phone.`
- `No accounts and no trackers — what you read and choose never leaves this phone.`
- `Pick at least 3 topics`
- `World`
- `US`
- `Politics`
- `Business`
- `Tech`
- `Science`
- `Health`
- `Sports`
- `Entertainment`
- `Climate`
- `Culture`
- `Travel`
- `Food`
- `Gaming`
- `Space`
- `Education`
- `Markets`
- `Books`
- `Local`
- `Opinion`
- `{n} picked — pick at least 3` (e.g. `2`)
- `{n} picked` (e.g. `5`)
- `Change them any time in Settings`
- `Continue`

### settings
- `Back` (spoken)
- `Settings`
- `Your feed`
- `Sources`
- `{n} followed · {m} not loading` (e.g. `24 followed · 1 not loading`)
- `{n} followed` (e.g. `24`; when no feed is failing; not drawn)
- `Topics & keywords`
- `{n} topics · {m} muted keywords` (e.g. `8 topics · 3 muted keywords`)
- `Hidden articles`
- `{n} hidden` (e.g. `6 hidden`)
- `Saved`
- `{n} saved for later` (e.g. `4 saved for later`)
- `Find sources`
- `Search outlets and topics, or add any site`
- `Suggestions`
- `Worked out on this phone from what you follow`
- `Reading`
- `Paywall filter`
- `Hide outlets that need a subscription`
- `Reading history`
- `Kept only on this phone, to tune your feed and suggestions`
- `Clear reading history`
- `Clear reading history?` (confirm sheet; not drawn)
- `This removes what you’ve opened from this phone. It can’t be undone.`
- `Clear history`
- `Cancel`
- `Reading history cleared` (snackbar, no Undo; not drawn)
- `Text size`
- `Small`
- `Default`
- `Large`
- `Extra large`
- `Your data`
- `Backup & restore`
- `Export or import your choices as a file`
- `About`
- `Privacy statement and credits`

### settings-sources
- `Back` (spoken)
- `Sources`
- `Add`
- `Following`
- `{n}` (e.g. `24`)
- `{outlet}` (e.g. `The Harbor Ledger`)
- `{n} of {m} section feeds` (e.g. `2 of 3 section feeds`)
- `All {m} section feeds` (e.g. `All 2 section feeds`)
- `{outlet} section feeds` (spoken, e.g. `The Harbor Ledger section feeds`)
- `{section}` (e.g. `Science`, `World`, `Opinion`)
- `Remove`
- `Paywall`
- `{n} of {m} section feeds · hidden while the paywall filter is on`
- `Show all {n}` (e.g. `Show all 24`)
- `Not loading since {date}` (e.g. `Oct 12`)
- `Retry`
- `Marked paywalled by you`
- `Hidden while the paywall filter is on`
- `Unflag`
- `Removed`
- `Removed {date}` (e.g. `Oct 3`)
- `Restore`
- `Outlets you remove are listed here, so you can bring them back any time.`
- `{outlet} removed` (e.g. `Kestrel`; snackbar; not drawn)
- `Undo`

### settings-topics
- `Back` (spoken)
- `Topics & keywords`
- `Followed topics`
- `{n}` (e.g. `6`)
- `{topic}` (e.g. `World`, `Science`, `Climate`)
- `Unfollow {topic}` (spoken)
- `Normal`
- `More`
- `Much more`
- `{topic} is at the top level, so Show more like this is turned off for it.` (e.g. `Science`)
- `Show all {n}`
- `Your keyword topics`
- `{keyword topic}` (e.g. `Seattle`, `Heat pumps`)
- `Remove {keyword topic}` (spoken)
- `Muted topics`
- `{muted topic}` (e.g. `Celebrity`, `Crypto`)
- `Restore`
- `Muted keywords`
- `{keyword}` (e.g. `Elon Musk`, `Spoilers`, `Bitcoin`)
- `Unmute {keyword}` (spoken)
- `No muted keywords. Mute a word or name from any article’s ⋮ menu.`
- `{topic} unfollowed` (e.g. `World`; snackbar; not drawn)
- `Undo`

### settings-hidden
- `Back` (spoken)
- `Hidden articles`
- `Hidden articles stay out of your feed. Restore one to see it again.`
- `Hidden`
- `{n}` (e.g. `6`)
- `{headline}` (e.g. `Five budget earbuds that survived a month of commuting`)
- `{outlet} · Hidden {when}` (e.g. `Open Circuit · Hidden today`; `{when}`: `today`, `yesterday`, weekday `Tue`, or date `Oct 10`)
- `Restore`
- `No hidden articles`
- `When you choose Hide this article from an article’s ⋮ menu, it’s listed here so you can bring it back.`

### saved
- `Back` (spoken)
- `Saved`
- `{outlet}`
- `{age}`
- `{headline}`
- `{topic}` (e.g. `Tech`, shown when an article has no image)
- `Share` (spoken)
- `Unsave` (spoken)
- `Removed from Saved.` (snackbar; not drawn)
- `Undo`
- `Nothing saved yet`
- `Tap ⋮ on any article and choose Save for later. Your saved list stays on this phone.`
- `You’re offline. Your list is here; articles open once you’re back online.`
- `Retry`

### search
- `Back` (spoken)
- `Outlets, topics or a web address`
- `Search outlets, topics or a web address` (spoken, field)
- `Clear search` (spoken)
- `Find an outlet or topic by name, or paste any site’s address — Newz looks for its RSS or Atom feed.`
- `Already have a feed address? Paste it here and add it directly.`
- `Searching the built-in list happens on this phone. Checking an address only contacts that site.`
- `Outlets`
- `{n}` (e.g. `2`)
- `{outlet}` (e.g. `Sun & Grid`, `Solar Weekly`)
- `{topic} · {n} section feeds` (e.g. `Energy · 3 section feeds`)
- `{topic} · 1 feed` (e.g. `Climate · 1 feed`)
- `Paywall`
- `Follow`
- `Topics`
- `{topic}` (e.g. `Energy`, `Climate`)
- `{topic description}` (e.g. `Solar, wind, batteries and the grid`)
- `In your topics`
- `Following`
- `Add '{word}' as a custom topic` (e.g. `solar`)
- `Shows articles from your sources that mention it`
- `Web address`
- `Looking for a feed…`
- `{address}` (e.g. `tidepool.example`)
- `Only {address} is contacted to check.`
- `Feed found`
- `{site name}` (e.g. `Tidepool Notes`)
- `{feed address} · RSS` (e.g. `tidepool.example/rss · RSS`; Atom feeds: `· Atom`)
- `Latest: “{headline}” · {age}` (e.g. `Latest: “Volunteers count a record number of sea stars” · 3h`)
- `Add`
- `No feed found on this site`
- `Some sites don’t publish one. If you know the feed’s own address, paste that instead.`
- `That doesn't look like a web address`
- `Check the spelling — for example tidepool.example or https://tidepool.example/rss.`
- `Already following`
- `Newz never contacts Google`
- `Addresses on Google-owned sites can’t be added, so nothing you do in Newz ever reaches Google. Try the outlet’s own website instead.`

### suggestions
- `Back` (spoken)
- `Suggested for you`
- `Worked out on this phone from what you follow and read. Nothing is sent anywhere.`
- `Outlets`
- `Topics`
- `{rank}` (e.g. `1`)
- `Rank {n}` (spoken)
- `{outlet}` (e.g. `Orbit Daily`)
- `{topic} · {reason}` (e.g. `Space · Similar to The Harbor Ledger, which you follow`)
- `Similar to {outlet}, which you follow`
- `Because you follow {topic}`
- `Because you follow {topic} and {topic}`
- `Covers many of the same stories as your sources`
- `Often in the {topic} articles you open`
- `Follow`
- `Following`
- `No suggestions right now`
- `Suggestions come from the sources and topics you follow. Check back after you’ve followed a few more.`
- `Find sources`

### backup
- `Back` (spoken)
- `Backup & restore`
- `Save all your choices — sources, topics, muted words, hidden and saved articles — to a file you keep. Nothing goes to a cloud.`
- `Export`
- `Include reading history`
- `Left out unless you tick it`
- `Export to a file`
- `Backup saved`
- `{file name} is in {folder}. Copy it to your new phone and import it there.` (e.g. `newz-backup-2026-10-16.json`, `Downloads`)
- `Import`
- `Restore choices from a Newz backup file. They replace the choices on this phone.`
- `Choose a backup file`
- `Backup imported`
- `Restored {n} sources, {n} topics, {n} muted keywords, {n} hidden and {n} saved articles.` (e.g. `24`, `8`, `3`, `6`, `4`)
- `That file isn’t a valid Newz backup`
- `Nothing was changed. Choose a file exported from Newz — its name ends in .json.`

### app-icon
- `Newz` (launcher label)

The other text in `app-icon.png` labels the specimen sheet and isn't app copy.
