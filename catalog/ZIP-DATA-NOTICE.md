# Zip-code data notice

`catalog/zip-table.csv` and the `metros` in `catalog/catalog.json` are built by
`scripts/build-zip-table.sh` from US Census Bureau files. Census Bureau data are works of the
US federal government and are in the public domain (17 U.S.C. 105); no license applies.
This product uses Census Bureau data but is not endorsed or certified by the Census Bureau.

ZIP Code Tabulation Areas (ZCTAs) approximate USPS zip codes. A zip maps to a metro when the
county holding most of its land lies in that metro's CBSA. The app ships the table offline;
a typed zip code never leaves the device (SPEC S29).

| File | URL | Downloaded |
|---|---|---|
| 2026 Gazetteer ZCTA file: each ZCTA5 code and its internal point (INTPTLAT, INTPTLONG). | https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2026_Gazetteer/2026_Gaz_zcta_national.zip | 2026-10-17 |
| 2020 ZCTA5-to-county relationship file: the county holding most of each ZCTA's land area. | https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_county20_natl.txt | 2026-10-17 |
| 2020 ZCTA5-to-county-subdivision relationship file: the same for Connecticut, by town. | https://www2.census.gov/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_cousub20_natl.txt | 2026-10-17 |
| Connecticut 2022 county-to-planning-region crosswalk: Connecticut towns to the planning regions the 2023 delineation uses as counties. | https://www2.census.gov/geo/docs/reference/ct_change/ct_cou_to_cousub_crosswalk.xlsx | 2026-10-17 |
| July 2023 CBSA delineation file (OMB Bulletin 23-01): county to metropolitan statistical area. | https://www2.census.gov/programs-surveys/metro-micro/geographies/reference-files/2023/delineation-files/list1_2023.xlsx | 2026-10-17 |
| 2026 Gazetteer CBSA file: each metro's internal point (the `lat`/`lon` of catalog metros). | https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2026_Gazetteer/2026_Gaz_cbsa_national.zip | 2026-10-17 |
| Vintage 2025 CBSA population estimates: metro ranking and `population` (POPESTIMATE2025). | https://www2.census.gov/programs-surveys/popest/datasets/2020-2025/metro/totals/cbsa-est2025-alldata.csv | 2026-10-17 |

Rows: 33791 zips, 8812 in a covered metro (50 metros).
