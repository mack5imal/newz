# Outlet popularity data

Outlet popularity scores in `catalog.json` are derived from the **Majestic Million**
by Majestic (https://majestic.com), licensed under the
**Creative Commons Attribution 3.0 Unported licence (CC BY 3.0)**,
https://creativecommons.org/licenses/by/3.0/.

- Source file: https://downloads.majestic.com/majestic_million.csv
- Downloaded: 2026-10-17 (UTC; the file was last modified 2026-10-16)
- Changes made: each outlet's registrable domain was looked up in the list and its global rank
  was converted to a score, `popularity = round(1 - log10(rank) / 6, 3)`, clamped to 0–1.
  Domains not in the list get 0.05. Only these scores ship with the app; the list itself does not.
- Regenerate with `scripts/build-popularity.sh`.

Suggested About-screen credit: "Outlet popularity: Majestic Million, © Majestic, CC BY 3.0."
