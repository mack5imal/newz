#!/usr/bin/env bash
# run-instrumented-tests.sh — boot the `newz-api34` AOSP AVD headless, run
# `:androidApp:connectedDebugAndroidTest`, and always shut the emulator down.
#
# Usage: bash scripts/run-instrumented-tests.sh [extra Gradle args…]
#   e.g. bash scripts/run-instrumented-tests.sh \
#          -Pandroid.testInstrumentationRunnerArguments.class=app.newz.SmokeTest
# Exits with Gradle's exit code (1 if the emulator never finishes booting).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD_NAME="${NEWZ_AVD:-newz-api34}"
BOOT_TIMEOUT_S="${NEWZ_BOOT_TIMEOUT:-300}"

sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" 2> /dev/null | tail -n 1)"
ANDROID_HOME="${ANDROID_HOME:-${sdk_dir:-$(brew --prefix)/share/android-commandlinetools}}"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

log() { printf '[instrumented] %s\n' "$*"; }

emu_flags=(-avd "$AVD_NAME" -no-window -no-audio -no-boot-anim -no-snapshot)
if "$EMULATOR" -help 2>&1 | grep -q -- '-no-metrics'; then
  emu_flags+=(-no-metrics)
fi
if [ -n "${NEWZ_EMULATOR_GPU:-}" ]; then
  emu_flags+=(-gpu "$NEWZ_EMULATOR_GPU")
fi

emu_pid=""
serial=""
cleanup() {
  if [ -n "$serial" ]; then
    "$ADB" -s "$serial" emu kill > /dev/null 2>&1 || true
  fi
  if [ -n "$emu_pid" ]; then
    for _ in $(seq 1 30); do
      kill -0 "$emu_pid" 2> /dev/null || break
      sleep 1
    done
    kill "$emu_pid" 2> /dev/null || true
    sleep 2
    kill -9 "$emu_pid" 2> /dev/null || true
  fi
  pkill -9 -f "qemu-system.*-avd $AVD_NAME" 2> /dev/null || true
  pkill -9 -f "qemu-system.*@$AVD_NAME" 2> /dev/null || true
}
trap cleanup EXIT INT TERM

"$ADB" start-server > /dev/null 2>&1
before="$("$ADB" devices | awk '/^emulator-/{print $1}')"

log "Booting $AVD_NAME: emulator ${emu_flags[*]}"
emu_log="$REPO_ROOT/build/emulator.log"
mkdir -p "$(dirname "$emu_log")"
"$EMULATOR" "${emu_flags[@]}" > "$emu_log" 2>&1 &
emu_pid=$!

deadline=$((SECONDS + BOOT_TIMEOUT_S))
while [ -z "$serial" ]; do
  if [ "$SECONDS" -ge "$deadline" ] || ! kill -0 "$emu_pid" 2> /dev/null; then
    log "Emulator did not appear (see $emu_log)"
    exit 1
  fi
  serial="$("$ADB" devices | awk '/^emulator-/{print $1}' | grep -vxF "${before:-none}" | head -n 1 || true)"
  sleep 2
done
log "Device $serial attached, waiting for boot (timeout ${BOOT_TIMEOUT_S}s)"

until [ "$("$ADB" -s "$serial" shell getprop sys.boot_completed 2> /dev/null | tr -d '\r')" = "1" ]; do
  if [ "$SECONDS" -ge "$deadline" ] || ! kill -0 "$emu_pid" 2> /dev/null; then
    log "Emulator did not finish booting (see $emu_log)"
    exit 1
  fi
  sleep 3
done
log "Boot completed after ${SECONDS}s"

for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$ADB" -s "$serial" shell settings put global "$setting" 0
done

export ANDROID_SERIAL="$serial"
set +e
(cd "$REPO_ROOT" && ./gradlew :androidApp:connectedDebugAndroidTest "$@")
gradle_status=$?
set -e
log "Gradle exited with $gradle_status"
exit "$gradle_status"
