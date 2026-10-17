#!/usr/bin/env bash
# fetch-fonts.sh — download the two OFL fonts Newz bundles and copy the six static
# weights plus their licence texts into the Android app.
#
#   Big Shoulders Display 700/800  — github.com/xotypeco/big_shoulders, release v1.100
#   Schibsted Grotesk 400/500/600/700 — github.com/schibsted/schibsted-grotesk, release 1.100
#
# Only GitHub release assets and the repositories' OFL.txt are fetched; no font CDN is
# contacted. The fonts are committed, so the app never downloads them at run time.
#
# Usage: bash scripts/fetch-fonts.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FONT_DIR="$REPO_ROOT/androidApp/src/main/res/font"
LICENSE_DIR="$REPO_ROOT/androidApp/src/main/assets/licenses"

BS_ZIP_URL="https://github.com/xotypeco/big_shoulders/releases/download/v1.100/v1.1_Fonts.zip"
BS_OFL_URL="https://raw.githubusercontent.com/xotypeco/big_shoulders/master/OFL.txt"
SG_ZIP_URL="https://github.com/schibsted/schibsted-grotesk/releases/download/1.100/schibsted-grotesk-fonts.zip"
SG_OFL_URL="https://raw.githubusercontent.com/schibsted/schibsted-grotesk/main/OFL.txt"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fetch() { curl -fsSL --retry 3 -o "$2" "$1"; }

echo "[fonts] downloading Big Shoulders"
fetch "$BS_ZIP_URL" "$work/bs.zip"
fetch "$BS_OFL_URL" "$work/OFL-BigShoulders.txt"
echo "[fonts] downloading Schibsted Grotesk"
fetch "$SG_ZIP_URL" "$work/sg.zip"
fetch "$SG_OFL_URL" "$work/OFL-SchibstedGrotesk.txt"

unzip -q -o "$work/bs.zip" -d "$work/bs"
unzip -q -o "$work/sg.zip" -d "$work/sg"

mkdir -p "$FONT_DIR" "$LICENSE_DIR"
bs_static="$work/bs/Big Shoulders/static"
sg_static="$work/sg/schibsted-grotesk-fonts/fonts/ttf"

cp "$bs_static/BigShouldersDisplay-Bold.ttf" "$FONT_DIR/big_shoulders_display_bold.ttf"
cp "$bs_static/BigShouldersDisplay-ExtraBold.ttf" "$FONT_DIR/big_shoulders_display_extrabold.ttf"
cp "$sg_static/SchibstedGrotesk-Regular.ttf" "$FONT_DIR/schibsted_grotesk_regular.ttf"
cp "$sg_static/SchibstedGrotesk-Medium.ttf" "$FONT_DIR/schibsted_grotesk_medium.ttf"
cp "$sg_static/SchibstedGrotesk-SemiBold.ttf" "$FONT_DIR/schibsted_grotesk_semibold.ttf"
cp "$sg_static/SchibstedGrotesk-Bold.ttf" "$FONT_DIR/schibsted_grotesk_bold.ttf"
cp "$work/OFL-BigShoulders.txt" "$LICENSE_DIR/OFL-BigShoulders.txt"
cp "$work/OFL-SchibstedGrotesk.txt" "$LICENSE_DIR/OFL-SchibstedGrotesk.txt"

echo "[fonts] installed:"
(cd "$FONT_DIR" && shasum -a 256 ./*.ttf)
(cd "$LICENSE_DIR" && shasum -a 256 ./OFL-*.txt)
