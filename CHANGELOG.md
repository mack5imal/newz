# Changelog

What changed in each version of Newz, in plain terms. The detail behind every entry is in
[BUGS.md](BUGS.md), where each fix names its bug number and commit.

## 1.1 — November 2026

The fixes from the first full code review ([reviews/2026-11-code-review.md](reviews/2026-11-code-review.md),
bugs B23–B58). Updating upgrades the app's data the first time it opens, so export a backup
first; see [INSTALL.md](INSTALL.md#6-updating).

- **Stricter Google blocking.** Disguised Google addresses (punycode, lookalike letters,
  invisible characters) are now caught, also inside the HTTP engine itself. More Google ad,
  analytics, Firebase and AMP domains are blocked. Feed items that link to Google are dropped,
  links to bare IP addresses are refused, and a site that lists a FeedBurner feed before its own
  now gets its own.
- **Safer backup restore.** A restore shows what it will replace and asks first. A backup made
  without reading history no longer erases yours, the restored sources load straight away, and
  an interrupted restore can no longer leave settings and saved articles out of step.
- **Your place is kept.** The feed keeps its scroll position when you visit Saved or Settings
  and come back. An open menu or keyword sheet survives rotating the phone, and onboarding
  picks survive the app being closed in the background.
- **Correct dates and fewer stray cards.** More feed date formats and time zones are read
  correctly, so old stories no longer show as new, and dates far in the future are ignored.
  A feed that gives every story the same id no longer collapses into one card.
- **Better story grouping and pictures.** Full-text feeds no longer merge unrelated stories into
  one card, and tracking pixels are never used as card pictures. Pictures over 10 MB are not
  downloaded.
- **Following is reliable.** An outlet followed while the feed is loading is fetched by that
  load, and a pull to refresh near the end of a load is no longer lost. Unfollowing one section
  of an outlet keeps stories that another section you follow also carries.
- **Accessibility and landscape.** TalkBack stays inside an open sheet, the Undo bar waits as
  long as your "Time to take action" setting asks, and in landscape no button sits under the
  navigation bar or the camera.
- **Smaller fixes.** Muting "Tim" no longer hides "times". A feed quoting `<!ENTITY` in a post
  loads again. Reading history is kept for 180 days. A damaged settings file is reset instead
  of crashing the app at every launch. Large feeds rank much faster.
- **For builders.** The setup script installs a fixed, checksum-verified JDK and no longer edits
  your global Gradle settings.

## 1.0 — October 2026

The first release.
