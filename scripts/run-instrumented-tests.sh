#!/usr/bin/env bash
# run-instrumented-tests.sh — boot the `newz-api34` AOSP AVD headless, run
# `:androidApp:connectedDebugAndroidTest`, and always shut the emulator down.
#
# Usage: bash scripts/run-instrumented-tests.sh [extra Gradle args…]
#   e.g. bash scripts/run-instrumented-tests.sh \
#          -Pandroid.testInstrumentationRunnerArguments.class=app.newz.SmokeTest
# Exits with Gradle's exit code (1 if the emulator never finishes booting).
#
#        bash scripts/run-instrumented-tests.sh --release-smoke [path/to/app.apk]
# Instead of the test suite, installs the release APK (default:
# androidApp/build/outputs/apk/release/androidApp-release.apk), launches
# app.newz/.MainActivity, waits 10 s, and checks the process is alive with no
# FATAL EXCEPTION for app.newz in logcat. Exits 0 on a pass. Used by
# scripts/release-smoke.sh.
#
#        bash scripts/run-instrumented-tests.sh --design-capture <out-dir>
# Instead of the test suite, installs the debug app and test APKs, runs only
# app.newz.design.DesignCaptureTest with `am instrument` (no Gradle connected
# task, so the app is not uninstalled before its PNGs are pulled), pulls the
# app's external files `design-check/*.png` into <out-dir>, then uninstalls
# both APKs. Exits 0 when every capture test passed and was pulled. Used by
# scripts/capture-design-screens.sh.
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

release_smoke=0
design_capture_dir=""
if [ "${1:-}" = "--design-capture" ]; then
  if [ -z "${2:-}" ]; then
    log "--design-capture needs an output directory"
    exit 2
  fi
  design_capture_dir="$2"
  shift 2
fi
release_apk="$REPO_ROOT/androidApp/build/outputs/apk/release/androidApp-release.apk"
if [ "${1:-}" = "--release-smoke" ]; then
  release_smoke=1
  shift
  if [ -n "${1:-}" ]; then release_apk="$1"; fi
  if [ ! -f "$release_apk" ]; then
    log "Release APK not found: $release_apk (run ./gradlew :androidApp:assembleRelease)"
    exit 1
  fi
fi

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

run_release_smoke() {
  local package="app.newz" pid crashes
  # The release key differs from the debug key: remove any earlier install first,
  # and again afterwards so the debug test suite can install cleanly next time.
  "$ADB" uninstall "$package" > /dev/null 2>&1 || true
  log "Installing ${release_apk#"$REPO_ROOT"/}"
  "$ADB" install -r "$release_apk"
  "$ADB" logcat -c
  log "Launching $package/.MainActivity"
  "$ADB" shell am start -W -n "$package/.MainActivity"
  sleep 10
  pid="$("$ADB" shell pidof "$package" 2> /dev/null | tr -d '\r' || true)"
  crashes="$("$ADB" logcat -d -b crash,main 2> /dev/null | grep -A 12 'FATAL EXCEPTION' | grep -E "Process: $package([^.]|$)" || true)"
  "$ADB" uninstall "$package" > /dev/null 2>&1 || true
  if [ -n "$crashes" ]; then
    log "RELEASE SMOKE FAIL: FATAL EXCEPTION for $package"
    printf '%s\n' "$crashes"
    return 1
  fi
  if [ -z "$pid" ]; then
    log "RELEASE SMOKE FAIL: $package is not running after 10 s"
    return 1
  fi
  log "RELEASE SMOKE PASS: $package alive after 10 s (pid $pid), no FATAL EXCEPTION for $package"
}

run_design_capture() {
  local package="app.newz" test_package="app.newz.test" device_dir output pulled
  device_dir="/sdcard/Android/data/$package/files/design-check"
  "$ADB" uninstall "$package" > /dev/null 2>&1 || true
  "$ADB" uninstall "$test_package" > /dev/null 2>&1 || true
  (cd "$REPO_ROOT" && ./gradlew :androidApp:installDebug :androidApp:installDebugAndroidTest) || return 1
  log "Running app.newz.design.DesignCaptureTest"
  output="$("$ADB" shell am instrument -w -e class app.newz.design.DesignCaptureTest \
    "$test_package/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tr -d '\r')"
  printf '%s\n' "$output"
  mkdir -p "$design_capture_dir"
  if ! "$ADB" pull "$device_dir/." "$design_capture_dir" > /dev/null 2>&1; then
    # Fallback: read each file as the (debuggable) app itself.
    for f in $("$ADB" shell run-as "$package" ls "$device_dir" 2> /dev/null | tr -d '\r'); do
      "$ADB" exec-out run-as "$package" cat "$device_dir/$f" > "$design_capture_dir/$f"
    done
  fi
  "$ADB" uninstall "$test_package" > /dev/null 2>&1 || true
  "$ADB" uninstall "$package" > /dev/null 2>&1 || true
  pulled="$(find "$design_capture_dir" -maxdepth 1 -name '*.png' -newer "$emu_log" | wc -l | tr -d ' ')"
  log "Pulled $pulled PNG(s) into $design_capture_dir"
  if ! grep -q '^OK (' <<< "$output"; then
    log "DESIGN CAPTURE FAIL: not every capture test passed"
    return 1
  fi
  [ "$pulled" -gt 0 ]
}

if [ -n "$design_capture_dir" ]; then
  set +e
  run_design_capture
  capture_status=$?
  set -e
  exit "$capture_status"
fi

if [ "$release_smoke" -eq 1 ]; then
  set +e
  run_release_smoke
  smoke_status=$?
  set -e
  exit "$smoke_status"
fi

set +e
(cd "$REPO_ROOT" && ./gradlew :androidApp:connectedDebugAndroidTest "$@")
gradle_status=$?
set -e
log "Gradle exited with $gradle_status"
exit "$gradle_status"
