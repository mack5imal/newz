# Newz design source

This folder is for people. The builder works from `HANDOFF.md` and `screens/` alone.

- `html/<screen-id>[--<state>].html`: one static page per screenshot. Each one links `fonts.css` and `newz.css`. Open it in Chrome with the window set to the screen's width (412 dp; `app-icon` is 496) to inspect the CSS. The screenshots were made from these pages with headless Chromium at 2× device pixel ratio.
- `html/newz.css`: the shared tokens and component styles. Light tokens are on `.nz` and dark overrides are on `.nz.dk`.
- `html/fonts/`: Big Shoulders Display (700, 800) and Schibsted Grotesk (400–700) as Latin-subset WOFF2 files from Fontsource, with their SIL Open Font License 1.1 texts. The app should bundle the full font files from the upstream projects instead.
