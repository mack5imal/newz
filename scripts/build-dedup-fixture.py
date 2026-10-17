#!/usr/bin/env python3
"""Builds the story-grouping test fixture from the UCI News Aggregator data set (SPEC S15).

Data: UCI Machine Learning Repository, "News Aggregator" data set, F. Gasparetti (2017),
CC BY 4.0, https://archive.ics.uci.edu/dataset/359/news+aggregator. Attribution is in
shared/src/commonTest/resources/dedup/NOTICE.md.

Run from anywhere with the standard library only:

    python3 -I scripts/build-dedup-fixture.py              # write the fixture
    python3 -I scripts/build-dedup-fixture.py --candidates # print sampled positives for hand-checking

The zip is downloaded once from archive.ics.uci.edu into build/dedup/ (never committed). The
script contacts no other host. Sampling is seeded, so a re-run writes the same fixture.

Pairs (all within 48 h, different publishers):
  same       same STORY id, both titles naming a shared proper noun; hand-checked, ambiguous
             ones listed in DROPPED_AMBIGUOUS and removed
  different  different STORY id, same category (random)
  hard       different STORY id, same category, same UTC day, sharing a proper noun
Plus DISTRACTORS unrelated titles the test mixes in so the IDF is realistic.

Output: shared/src/commonTest/resources/dedup/uci-pairs.tsv with the columns
label, title_a, title_b, publisher_a, publisher_b, hours_apart, and the same rows as a Kotlin
constant in shared/src/commonTest/kotlin/app/newz/dedup/UciPairsFixture.kt (commonTest has no
portable resource loading).
"""

import collections
import csv
import io
import os
import random
import re
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "build", "dedup")
ZIP_PATH = os.path.join(CACHE, "news-aggregator.zip")
ZIP_URL = "https://archive.ics.uci.edu/static/public/359/news+aggregator.zip"
TSV_OUT = os.path.join(ROOT, "shared/src/commonTest/resources/dedup/uci-pairs.tsv")
KT_OUT = os.path.join(ROOT, "shared/src/commonTest/kotlin/app/newz/dedup/UciPairsFixture.kt")

SEED = 20141016
CANDIDATE_POSITIVES = 80
EASY_NEGATIVES = 25
HARD_NEGATIVES = 36
DISTRACTORS = 30
WINDOW_MS = 48 * 3600 * 1000

# Candidate positives (by "idA-idB") dropped after hand-checking: the two titles do not clearly
# name the same event (UCI STORY clusters are coarser than one event).
DROPPED_AMBIGUOUS = set(
    """66974-66975 409707-409712 316727-316743 133424-133449 35910-35911 18528-18529 101419-101429
    208175-208181 124575-124585 289046-289048 151868-151878 250011-250032 221930-222076 216916-216942
    152415-152438 12896-12901 66526-66529 88410-88425 358736-358738 29894-29901 130310-130311
    215922-215965 250050-250051 338049-338080 49436-49440 341124-341129 47940-47943 314706-314710""".split()
)

# Candidate hard negatives dropped after hand-checking: UCI split one event across two STORY
# ids, so the pair is really the same event and cannot serve as a different-story pair.
DROPPED_SAME_EVENT = set(
    """89892-93691 374623-376887 76422-76755 75993-79603 327762-330712 143982-145840 258935-262828
    80138-80271 234555-235164 290001-290055 395941-396080 332945-336773""".split()
)

# Capitalised words that do not name the story's subject (dates, the country); a hard negative
# must share some other proper noun.
WEAK_ENTITIES = set(
    """january february march april may june july august september october november december
    monday tuesday wednesday thursday friday saturday sunday u.s us america american""".split()
)

WORD = re.compile(r"[A-Za-z][A-Za-z'&.-]*[A-Za-z]|[A-Za-z]")
COMMON = set(
    """a an and the of to in on for at by with from as is are was were be been it its this that
    these those but or not no new says said say after over up down out off into than more most
    how why what who when where will would can could may might has have had do does did his her
    their our your my he she they we you i us report reports video photos watch update live""".split()
)


def download():
    if os.path.exists(ZIP_PATH) and os.path.getsize(ZIP_PATH) > 0:
        return
    os.makedirs(CACHE, exist_ok=True)
    part = ZIP_PATH + ".part"
    with urllib.request.urlopen(ZIP_URL, timeout=600) as resp, open(part, "wb") as out:
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
    os.replace(part, ZIP_PATH)


def load_rows():
    with zipfile.ZipFile(ZIP_PATH) as z, z.open("newsCorpora.csv") as raw:
        text = io.TextIOWrapper(raw, encoding="utf-8", errors="replace", newline="")
        rows = []
        for rec in csv.reader(text, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(rec) != 8:
                continue
            rid, title, _url, publisher, category, story, _host, ts = rec
            title = " ".join(title.split())
            publisher = " ".join(publisher.split())
            if not title or not publisher or not ts.isdigit():
                continue
            rows.append((int(rid), title, publisher, category, story, int(ts)))
        return rows


def proper_nouns(rows):
    """Words written capitalised in >= 90% of their non-initial occurrences (seen >= 20 times)."""
    cap = collections.Counter()
    total = collections.Counter()
    for r in rows:
        words = WORD.findall(r[1])
        for w in words[1:]:
            lw = w.lower()
            total[lw] += 1
            if w[0].isupper() and not w.isupper():
                cap[lw] += 1
            elif w.isupper() and len(w) > 1:
                cap[lw] += 1
    return {w for w, n in total.items() if n >= 20 and w not in COMMON and len(w) > 2 and cap[w] >= 0.9 * n}


def entities(title, nouns):
    return {w.lower() for w in WORD.findall(title) if w[0].isupper() and w.lower() in nouns}


def clean(title):
    return "?" not in title and 25 <= len(title) <= 95 and "\t" not in title


def pair_key(a, b):
    return "%d-%d" % (a[0], b[0])


def hours(a, b):
    return round(abs(a[5] - b[5]) / 3_600_000, 1)


def sample(rows):
    rng = random.Random(SEED)
    nouns = proper_nouns(rows)
    rows = [r for r in rows if clean(r[1])]
    by_story = collections.defaultdict(list)
    for r in rows:
        by_story[r[4]].append(r)
    stories = sorted(by_story)
    rng.shuffle(stories)

    positives = []
    used_stories = set()
    for s in stories:
        if len(positives) >= CANDIDATE_POSITIVES:
            break
        members = sorted(by_story[s])
        rng.shuffle(members)
        found = None
        for a in members[:40]:
            ea = entities(a[1], nouns)
            if not ea:
                continue
            for b in members[:40]:
                if b[2] == a[2] or b[1].lower() == a[1].lower() or abs(a[5] - b[5]) > WINDOW_MS:
                    continue
                if ea & entities(b[1], nouns):
                    found = (a, b) if a[0] < b[0] else (b, a)
                    break
            if found:
                break
        if found:
            positives.append(found)
            used_stories.add(s)

    by_cat_day = collections.defaultdict(list)
    for r in rows:
        if r[4] not in used_stories:
            by_cat_day[(r[3], r[5] // 86_400_000)].append(r)
    keys = sorted(by_cat_day)
    rng.shuffle(keys)

    hard = []
    seen_entities = set()
    for k in keys:
        if len(hard) >= HARD_NEGATIVES:
            break
        bucket = by_cat_day[k]
        if len(bucket) < 2:
            continue
        for _ in range(200):
            a, b = rng.sample(bucket, 2)
            if a[4] == b[4] or a[2] == b[2] or abs(a[5] - b[5]) > WINDOW_MS:
                continue
            shared = (entities(a[1], nouns) & entities(b[1], nouns)) - WEAK_ENTITIES
            if shared and not shared & seen_entities and a[4] not in used_stories and b[4] not in used_stories:
                hard.append((a, b) if a[0] < b[0] else (b, a))
                seen_entities |= shared
                used_stories |= {a[4], b[4]}
                break

    easy = []
    while len(easy) < EASY_NEGATIVES:
        k = rng.choice(keys)
        bucket = by_cat_day[k]
        if len(bucket) < 2:
            continue
        a, b = rng.sample(bucket, 2)
        if a[4] == b[4] or a[2] == b[2] or a[4] in used_stories or b[4] in used_stories:
            continue
        if abs(a[5] - b[5]) > WINDOW_MS or entities(a[1], nouns) & entities(b[1], nouns):
            continue
        easy.append((a, b) if a[0] < b[0] else (b, a))
        used_stories |= {a[4], b[4]}

    distractors = []
    while len(distractors) < DISTRACTORS:
        r = rng.choice(rows)
        if r[4] in used_stories:
            continue
        distractors.append(r)
        used_stories.add(r[4])

    return positives, hard, easy, distractors


def kt_string(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def write(positives, hard, easy, distractors):
    kept = [p for p in positives if pair_key(*p) not in DROPPED_AMBIGUOUS]
    hard_kept = [p for p in hard if pair_key(*p) not in DROPPED_SAME_EVENT]
    rows = [("same",) + p for p in kept] + [("hard",) + p for p in hard_kept] + [("different",) + p for p in easy]
    os.makedirs(os.path.dirname(TSV_OUT), exist_ok=True)
    with open(TSV_OUT, "w", encoding="utf-8", newline="") as f:
        f.write("label\ttitle_a\ttitle_b\tpublisher_a\tpublisher_b\thours_apart\n")
        for label, a, b in rows:
            f.write("\t".join([label, a[1], b[1], a[2], b[2], str(hours(a, b))]) + "\n")
        for d in distractors:
            f.write("\t".join(["distractor", d[1], "", d[2], "", "0"]) + "\n")

    os.makedirs(os.path.dirname(KT_OUT), exist_ok=True)
    with open(KT_OUT, "w", encoding="utf-8", newline="") as f:
        f.write("package app.newz.dedup\n\n")
        f.write("// Generated by scripts/build-dedup-fixture.py from the UCI News Aggregator data set\n")
        f.write("// (Gasparetti 2017, CC BY 4.0, see resources/dedup/NOTICE.md). Do not edit by hand.\n")
        f.write("// Same rows as resources/dedup/uci-pairs.tsv.\n\n")
        f.write("internal data class UciPair(\n")
        f.write("    val label: String,\n    val titleA: String,\n    val titleB: String,\n")
        f.write("    val publisherA: String,\n    val publisherB: String,\n    val hoursApart: Double,\n)\n\n")
        f.write("internal object UciPairsFixture {\n")
        f.write("    const val DROPPED_AMBIGUOUS = %d\n\n" % (len(positives) - len(kept)))
        f.write("    const val DROPPED_SAME_EVENT_NEGATIVES = %d\n\n" % (len(hard) - len(hard_kept)))
        f.write("    val pairs =\n        listOf(\n")
        for label, a, b in rows:
            f.write("            UciPair(\n")
            for v in (label, a[1], b[1], a[2], b[2]):
                f.write("                %s,\n" % kt_string(v))
            f.write("                %s,\n            ),\n" % repr(float(hours(a, b))))
        f.write("        )\n\n")
        f.write("    val distractors =\n        listOf(\n")
        for d in distractors:
            f.write("            %s,\n" % kt_string(d[1]))
        f.write("        )\n}\n")
    sys.stdout.write(
        "same=%d (dropped %d of %d) hard=%d (dropped %d of %d) different=%d distractors=%d\n"
        % (len(kept), len(positives) - len(kept), len(positives), len(hard_kept), len(hard) - len(hard_kept),
           len(hard), len(easy), len(distractors))
    )


def main():
    download()
    positives, hard, easy, distractors = sample(load_rows())
    if "--candidates" in sys.argv[1:]:
        for a, b in positives:
            sys.stdout.write("%s\n  A: %s [%s]\n  B: %s [%s]\n" % (pair_key(a, b), a[1], a[2], b[1], b[2]))
        sys.stdout.write("--- hard negatives\n")
        for a, b in hard:
            sys.stdout.write("%s\n  A: %s\n  B: %s\n" % (pair_key(a, b), a[1], b[1]))
        sys.stdout.write("--- different\n")
        for a, b in easy:
            sys.stdout.write("%s\n  A: %s\n  B: %s\n" % (pair_key(a, b), a[1], b[1]))
        return
    write(positives, hard, easy, distractors)


if __name__ == "__main__":
    main()
