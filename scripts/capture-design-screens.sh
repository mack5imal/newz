#!/usr/bin/env bash
# capture-design-screens.sh — capture the 14 approved design screens as PNGs (SPEC S34).
#
# Usage: RELAY_DESIGN_CHECK_DIR=<out-dir> bash scripts/capture-design-screens.sh
#
# Boots the emulator (scripts/run-instrumented-tests.sh --design-capture), runs only
# app.newz.design.DesignCaptureTest (each screen in its default state on deterministic
# fixtures, light theme, 412 dp wide; the app icon from its adaptive drawable at 432 px),
# pulls the PNGs into $RELAY_DESIGN_CHECK_DIR as <id>.png and kills the emulator.
# The output folder comes only from the environment: exits 2 when it is unset.
set -euo pipefail

if [ -z "${RELAY_DESIGN_CHECK_DIR:-}" ]; then
  echo "capture-design-screens: set RELAY_DESIGN_CHECK_DIR to the output folder" >&2
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$RELAY_DESIGN_CHECK_DIR"
exec bash "$REPO_ROOT/scripts/run-instrumented-tests.sh" --design-capture "$RELAY_DESIGN_CHECK_DIR"
