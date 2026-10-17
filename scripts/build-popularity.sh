#!/usr/bin/env bash
# Bakes an outlet popularity score into catalog JSON files (SPEC S26).
#
# Downloads the Majestic Million (CC BY 3.0, https://majestic.com) once into
# build/popularity/, maps each outlet's registrable domain to its global rank and writes
#   popularity = round(1 - log10(rank) / 6, 3), clamped to [0, 1]; 0.05 when not listed
# into every outlet of each JSON file given (default: catalog/catalog.json).
#
# If Majestic is unreachable, set TRANCO_LIST_ID to a custom Tranco list built without
# Cloudflare Radar (tranco-list.eu) and the script uses that instead. No Google host is used.
# The CSV is never committed; only the scores in the JSON are.
#
#   scripts/build-popularity.sh [catalog/catalog.json catalog/candidates.json ...]
set -euo pipefail
cd "$(dirname "$0")/.."

dir=build/popularity
mkdir -p "$dir"
majestic="$dir/majestic_million.csv"
tranco="$dir/tranco.csv"

source_csv=""
if [[ -s "$majestic" ]]; then
    source_csv="$majestic"
elif curl -fsSL --retry 2 --max-time 600 -o "$majestic.part" https://downloads.majestic.com/majestic_million.csv; then
    mv "$majestic.part" "$majestic"
    date -u +%Y-%m-%d > "$majestic.date"
    source_csv="$majestic"
elif [[ -n "${TRANCO_LIST_ID:-}" ]]; then
    rm -f "$majestic.part"
    curl -fsSL --retry 2 --max-time 600 -o "$dir/tranco.zip" "https://tranco-list.eu/download_daily/${TRANCO_LIST_ID}"
    unzip -p "$dir/tranco.zip" > "$tranco"
    date -u +%Y-%m-%d > "$tranco.date"
    source_csv="$tranco"
else
    rm -f "$majestic.part"
    echo "Majestic Million is unreachable; set TRANCO_LIST_ID to a Radar-free Tranco list to fall back." >&2
    exit 1
fi

if [[ $# -eq 0 ]]; then
    set -- catalog/catalog.json
fi

python3 - "$source_csv" "$@" <<'PY'
import csv, json, math, sys

source, files = sys.argv[1], sys.argv[2:]
ranks = {}
with open(source, newline="", encoding="utf-8") as f:
    reader = csv.reader(f)
    first = next(reader)
    if first and first[0].isdigit():
        # Tranco: rank,domain with no header.
        ranks[first[1].lower()] = int(first[0])
        for row in reader:
            ranks.setdefault(row[1].lower(), int(row[0]))
    else:
        # Majestic: GlobalRank,TldRank,Domain,...
        rank_col, domain_col = first.index("GlobalRank"), first.index("Domain")
        for row in reader:
            ranks.setdefault(row[domain_col].lower(), int(row[rank_col]))

def rank_of(domain):
    labels = domain.lower().strip(".").removeprefix("www.").split(".")
    # Try the domain itself, then each parent down to two labels.
    for i in range(0, max(1, len(labels) - 1)):
        candidate = ".".join(labels[i:])
        if candidate in ranks:
            return ranks[candidate]
    return None

def score(rank):
    if rank is None:
        return 0.05
    return round(min(1.0, max(0.0, 1 - math.log10(rank) / 6)), 3)

def dump(doc):
    # One array entry per line keeps catalog diffs reviewable.
    out = ["{"]
    keys = list(doc.keys())
    for k_i, key in enumerate(keys):
        value = doc[key]
        comma = "," if k_i < len(keys) - 1 else ""
        if isinstance(value, list):
            out.append(f'  "{key}": [')
            for i, entry in enumerate(value):
                sep = "," if i < len(value) - 1 else ""
                out.append("    " + json.dumps(entry, ensure_ascii=False) + sep)
            out.append("  ]" + comma)
        else:
            out.append(f'  "{key}": ' + json.dumps(value, ensure_ascii=False) + comma)
    out.append("}")
    return "\n".join(out) + "\n"

for path in files:
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    unlisted = []
    for outlet in doc.get("outlets", []):
        rank = rank_of(outlet["domain"])
        outlet["popularity"] = score(rank)
        if rank is None:
            unlisted.append(outlet["domain"])
    with open(path, "w", encoding="utf-8") as f:
        f.write(dump(doc))
    print(f"{path}: {len(doc.get('outlets', []))} outlets scored from {source}; not listed: {', '.join(unlisted) or 'none'}")
PY
