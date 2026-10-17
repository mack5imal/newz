# Story-grouping fixture: attribution

`uci-pairs.tsv` and the generated `shared/src/commonTest/kotlin/app/newz/dedup/UciPairsFixture.kt`
contain news headlines and publisher names sampled from:

- **Dataset:** News Aggregator Data Set, UCI Machine Learning Repository
- **Author:** Fabio Gasparetti (Roma Tre University), 2017.
  Related paper: F. Gasparetti, "Modeling user interests from web browsing activities",
  Data Mining and Knowledge Discovery 31(2), 2017.
- **Licence:** Creative Commons Attribution 4.0 International (CC BY 4.0),
  <https://creativecommons.org/licenses/by/4.0/>
- **Source:** <https://archive.ics.uci.edu/dataset/359/news+aggregator>
- The UCI repository page lists the licence as CC BY 4.0 (the zip's own `readme.txt` says
  "Public domain"); this project follows the stricter CC BY 4.0 terms and gives attribution here.

Changes: rows were sampled into labelled pairs by `scripts/build-dedup-fixture.py` (seeded),
whitespace was collapsed, and pairs were hand-checked: ambiguous same-story candidates and
different-story candidates that were really one event were dropped. Titles are otherwise
unchanged, including encoding errors present in the source data. Only titles, publisher names
and time differences are used; no URLs or article text.

The data is used only by tests. The app never contacts the UCI repository or the original
aggregator; the download happens only when a developer runs the script.
