#!/usr/bin/env bash
# Builds catalog/zip-table.csv, the offline zip-code -> metro table the app bundles (SPEC S29).
#
# Every input is public-domain US Census Bureau data, downloaded from www2.census.gov only
# (cached in build/zipdata/, never committed):
#   - 2026 Gazetteer ZCTA file: each ZCTA5's internal point (INTPTLAT/INTPTLONG);
#   - 2020 ZCTA-to-county relationship file: which county holds most of each ZCTA's land;
#   - 2020 ZCTA-to-county-subdivision relationship file + the Connecticut crosswalk: the same
#     for Connecticut, whose counties became planning regions in 2022 (the delineation uses them);
#   - July 2023 CBSA delineation file (OMB Bulletin 23-01): county -> CBSA;
#   - 2026 Gazetteer CBSA file and Vintage 2025 CBSA population estimates: written to
#     build/zipdata/top-metros.json, the source for the `metros` array in catalog/catalog.json.
#
# Output rows are `zip,lat,lon,metroId|-` sorted by zip; metroId is set when the ZCTA's main
# county belongs to the CBSA of a metro in catalog/catalog.json. Re-run after editing metros.
# Also rewrites catalog/ZIP-DATA-NOTICE.md with the files, URLs and download dates.
#
#   scripts/build-zip-table.sh
set -euo pipefail
cd "$(dirname "$0")/.."

base=https://www2.census.gov
dir=build/zipdata
mkdir -p "$dir"

urls=(
    "$base/geo/docs/maps-data/data/gazetteer/2026_Gazetteer/2026_Gaz_zcta_national.zip"
    "$base/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_county20_natl.txt"
    "$base/geo/docs/maps-data/data/rel2020/zcta520/tab20_zcta520_cousub20_natl.txt"
    "$base/geo/docs/reference/ct_change/ct_cou_to_cousub_crosswalk.xlsx"
    "$base/programs-surveys/metro-micro/geographies/reference-files/2023/delineation-files/list1_2023.xlsx"
    "$base/geo/docs/maps-data/data/gazetteer/2026_Gazetteer/2026_Gaz_cbsa_national.zip"
    "$base/programs-surveys/popest/datasets/2020-2025/metro/totals/cbsa-est2025-alldata.csv"
)

for url in "${urls[@]}"; do
    file="$dir/${url##*/}"
    if [[ ! -s "$file" ]]; then
        curl -fsSL --retry 2 --max-time 600 -o "$file.part" "$url"
        mv "$file.part" "$file"
        date -u +%Y-%m-%d > "$file.date"
    fi
    [[ -s "$file.date" ]] || date -u +%Y-%m-%d > "$file.date"
done

python3 -I - "$dir" "${urls[@]}" <<'PY'
import csv, io, json, math, re, sys, zipfile
import xml.etree.ElementTree as ET

d, urls = sys.argv[1], sys.argv[2:]
path = lambda url: f"{d}/{url.rsplit('/', 1)[1]}"
zcta_gaz, rel_county, rel_cousub, ct_xwalk, delineation, cbsa_gaz, popest = map(path, urls)
NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"


def xlsx_rows(p):
    z = zipfile.ZipFile(p)
    strings = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).iter(NS + "si"):
            strings.append("".join(t.text or "" for t in si.iter(NS + "t")))
    sheet = sorted(n for n in z.namelist() if re.match(r"xl/worksheets/sheet\d+\.xml$", n))[0]
    for row in ET.fromstring(z.read(sheet)).iter(NS + "row"):
        out = {}
        for c in row.iter(NS + "c"):
            col = re.match(r"[A-Z]+", c.get("r")).group()
            v, t = c.find(NS + "v"), c.get("t")
            if t == "inlineStr":
                out[col] = "".join(x.text or "" for x in c.iter(NS + "t"))
            elif v is not None:
                out[col] = strings[int(v.text)] if t == "s" else v.text
        yield out


def pipe_rows(p, opener=open):
    with opener(p) as f:
        yield from csv.DictReader(f, delimiter="|")


def zipped(p):
    def opener(_):
        z = zipfile.ZipFile(p)
        return io.TextIOWrapper(z.open(z.namelist()[0]), encoding="utf-8-sig")
    return opener


# County (5-digit FIPS) -> CBSA code, metropolitan areas only.
county_cbsa, cbsa_titles = {}, {}
for r in xlsx_rows(delineation):
    if r.get("E") == "Metropolitan Statistical Area" and re.fullmatch(r"\d{5}", r.get("A", "")):
        county_cbsa[r["J"].zfill(2) + r["K"].zfill(3)] = r["A"]
        cbsa_titles[r["A"]] = r["D"]

# Connecticut: 2020 county subdivision -> 2022 planning region (county equivalent).
ct_region = {}
for r in xlsx_rows(ct_xwalk):
    if re.fullmatch(r"\d{10}", r.get("G", "")):
        ct_region[r["G"]] = r["A"].zfill(2) + r["D"].zfill(3)

# ZCTA -> {county: land area}; Connecticut via its county subdivisions.
land = {}
for r in pipe_rows(rel_county, lambda p: open(p, encoding="utf-8-sig")):
    z, county = r["GEOID_ZCTA5_20"], r["GEOID_COUNTY_20"]
    if z and not county.startswith("09"):
        parts = land.setdefault(z, {})
        parts[county] = parts.get(county, 0) + int(r["AREALAND_PART"] or 0)
for r in pipe_rows(rel_cousub, lambda p: open(p, encoding="utf-8-sig")):
    z, cousub = r["GEOID_ZCTA5_20"], r["GEOID_COUSUB_20"]
    if z and cousub.startswith("09"):
        county = ct_region.get(cousub)
        if county is None:
            raise SystemExit(f"Connecticut subdivision {cousub} is not in the crosswalk")
        parts = land.setdefault(z, {})
        parts[county] = parts.get(county, 0) + int(r["AREALAND_PART"] or 0)

catalog = json.load(open("catalog/catalog.json", encoding="utf-8"))
metro_by_cbsa = {m["cbsa"]: m["id"] for m in catalog.get("metros", [])}
for cbsa in metro_by_cbsa:
    if cbsa not in cbsa_titles:
        raise SystemExit(f"catalog metro CBSA {cbsa} is not a metropolitan area in the delineation")

rows, unmatched = [], 0
for r in pipe_rows(zcta_gaz, zipped(zcta_gaz)):
    r = {k.strip(): v.strip() for k, v in r.items()}
    z = r["GEOID"]
    parts = land.get(z)
    if parts:
        county = max(sorted(parts), key=lambda c: parts[c])
        metro = metro_by_cbsa.get(county_cbsa.get(county), "-")
    else:
        unmatched += 1
        metro = "-"
    rows.append((z, round(float(r["INTPTLAT"]), 4), round(float(r["INTPTLONG"]), 4), metro))
rows.sort()

with open("catalog/zip-table.csv", "w", encoding="utf-8", newline="\n") as f:
    f.write("zip,lat,lon,metroId\n")
    for z, lat, lon, metro in rows:
        f.write(f"{z},{lat:.4f},{lon:.4f},{metro}\n")

# Top metropolitan areas by the latest population estimate, with their internal points.
points = {}
for r in pipe_rows(cbsa_gaz, zipped(cbsa_gaz)):
    r = {k.strip(): v.strip() for k, v in r.items()}
    points[r["GEOID"]] = (float(r["INTPTLAT"]), float(r["INTPTLONG"]))
with open(popest, encoding="latin-1") as f:
    est = [r for r in csv.DictReader(f) if r["LSAD"] == "Metropolitan Statistical Area"]
pop_col = max(k for k in est[0] if re.fullmatch(r"POPESTIMATE\d{4}", k))
est.sort(key=lambda r: -int(r[pop_col]))
top = [
    {"rank": i + 1, "cbsa": r["CBSA"], "title": r["NAME"], "population": int(r[pop_col]),
     "lat": points[r["CBSA"]][0], "lon": points[r["CBSA"]][1]}
    for i, r in enumerate(est[:60])
]
json.dump({"populationColumn": pop_col, "metros": top}, open(f"{d}/top-metros.json", "w"), indent=1)

counts = {}
for *_, metro in rows:
    counts[metro] = counts.get(metro, 0) + 1
missing = sorted(set(metro_by_cbsa.values()) - set(counts))
if missing:
    raise SystemExit(f"metros with no zip: {missing}")

dates = {u: open(path(u) + ".date").read().strip() for u in urls}
notes = {
    zcta_gaz: "2026 Gazetteer ZCTA file: each ZCTA5 code and its internal point (INTPTLAT, INTPTLONG).",
    rel_county: "2020 ZCTA5-to-county relationship file: the county holding most of each ZCTA's land area.",
    rel_cousub: "2020 ZCTA5-to-county-subdivision relationship file: the same for Connecticut, by town.",
    ct_xwalk: "Connecticut 2022 county-to-planning-region crosswalk: Connecticut towns to the planning "
              "regions the 2023 delineation uses as counties.",
    delineation: "July 2023 CBSA delineation file (OMB Bulletin 23-01): county to metropolitan statistical area.",
    cbsa_gaz: "2026 Gazetteer CBSA file: each metro's internal point (the `lat`/`lon` of catalog metros).",
    popest: f"Vintage 2025 CBSA population estimates: metro ranking and `population` ({pop_col}).",
}
with open("catalog/ZIP-DATA-NOTICE.md", "w", encoding="utf-8") as f:
    f.write("# Zip-code data notice\n\n")
    f.write("`catalog/zip-table.csv` and the `metros` in `catalog/catalog.json` are built by\n")
    f.write("`scripts/build-zip-table.sh` from US Census Bureau files. Census Bureau data are works of the\n")
    f.write("US federal government and are in the public domain (17 U.S.C. 105); no license applies.\n")
    f.write("This product uses Census Bureau data but is not endorsed or certified by the Census Bureau.\n\n")
    f.write("ZIP Code Tabulation Areas (ZCTAs) approximate USPS zip codes. A zip maps to a metro when the\n")
    f.write("county holding most of its land lies in that metro's CBSA. The app ships the table offline;\n")
    f.write("a typed zip code never leaves the device (SPEC S29).\n\n")
    f.write("| File | URL | Downloaded |\n|---|---|---|\n")
    for u in urls:
        f.write(f"| {notes[path(u)]} | {u} | {dates[u]} |\n")
    f.write(f"\nRows: {len(rows)} zips, {len(rows) - counts.get('-', 0)} in a covered metro"
            f" ({len(metro_by_cbsa)} metros).\n")

print(f"zip-table.csv: {len(rows)} zips, {len(rows) - counts.get('-', 0)} in {len(metro_by_cbsa)} metros"
      f" ({unmatched} without a county)")
PY
