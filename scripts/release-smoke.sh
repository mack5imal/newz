#!/usr/bin/env bash
# release-smoke.sh — install the signed release APK on the headless emulator,
# launch it, and check it is still alive after 10 s with no FATAL EXCEPTION.
# Catches R8 shrinking that breaks the app at runtime (serialization, SQLDelight, Ktor).
#
# Boots and always shuts down the emulator through the same code as
# scripts/run-instrumented-tests.sh (its --release-smoke mode).
#
# Usage: bash scripts/release-smoke.sh [path/to/app.apk]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec bash "$REPO_ROOT/scripts/run-instrumented-tests.sh" --release-smoke "$@"
