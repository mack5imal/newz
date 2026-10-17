#!/usr/bin/env bash
# verify-release.sh — check the built release APK before it is shared.
#
#   1. apksigner verify --print-certs   (signed, CN=Newz certificate)
#   2. aapt2 dump badging               (minSdk: sdkVersion:'29')
#   3. aapt2 dump permissions           (exactly INTERNET + ACCESS_NETWORK_STATE, no AD_ID)
#   4. releaseRuntimeClasspath          (none of the Google / tracker groups, SPEC S2 + S3)
#
# Run after `./gradlew :androidApp:assembleRelease`. Exits non-zero on any failure.
# Usage: bash scripts/verify-release.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_dir="$(sed -n 's/^sdk\.dir=//p' "$REPO_ROOT/local.properties" 2> /dev/null | tail -n 1)"
ANDROID_HOME="${ANDROID_HOME:-${sdk_dir:-$(brew --prefix)/share/android-commandlinetools}}"
BUILD_TOOLS="$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d 2> /dev/null | sort -V | tail -n 1)"
APKSIGNER="$BUILD_TOOLS/apksigner"
AAPT2="$BUILD_TOOLS/aapt2"

FORBIDDEN_GROUPS=(
  com.google.firebase
  com.google.android.gms
  com.google.android.play
  com.google.ads
  com.google.android.libraries.places
  io.sentry
  com.bugsnag
  com.crashlytics
  com.mixpanel
  com.amplitude
  com.segment
  com.appsflyer
  com.adjust
)
EXPECTED_PERMISSIONS="android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET"

failures=0
log() { printf '[verify-release] %s\n' "$*"; }
pass() { log "PASS: $*"; }
fail() {
  log "FAIL: $*"
  failures=$((failures + 1))
}

if [ ! -x "$APKSIGNER" ] || [ ! -x "$AAPT2" ]; then
  log "FAIL: apksigner/aapt2 not found under $ANDROID_HOME/build-tools (run scripts/setup-android-toolchain.sh)"
  exit 1
fi

shopt -s nullglob
apks=("$REPO_ROOT"/androidApp/build/outputs/apk/release/*.apk)
shopt -u nullglob
if [ "${#apks[@]}" -eq 0 ]; then
  log "FAIL: no APK in androidApp/build/outputs/apk/release/ (run ./gradlew :androidApp:assembleRelease)"
  exit 1
fi

for apk in "${apks[@]}"; do
  rel="${apk#"$REPO_ROOT"/}"
  log "Checking $rel"

  # 1. Signature
  if certs="$("$APKSIGNER" verify --print-certs "$apk" 2>&1)"; then
    printf '%s\n' "$certs" | grep -E 'Signer #1 certificate (DN|SHA-256)' | sed 's/^/  /'
    if grep -q 'Signer #1 certificate DN: CN=Newz$' <<< "$certs"; then
      pass "signed with the CN=Newz certificate"
    else
      fail "signed, but not with the CN=Newz certificate"
    fi
  else
    printf '%s\n' "$certs" | sed 's/^/  /'
    fail "apksigner verify failed"
  fi

  # 2. Badging / minSdk
  badging="$("$AAPT2" dump badging "$apk" 2>&1)"
  # aapt2 prints the minSdk as minSdkVersion:'29' (older aapt: sdkVersion:'29').
  grep -E "^(package|minSdkVersion|sdkVersion|targetSdkVersion):" <<< "$badging" | sed 's/^/  /'
  if grep -Eq "^(minSdkVersion|sdkVersion):'29'$" <<< "$badging"; then
    pass "minSdk sdkVersion:'29'"
  else
    fail "sdkVersion:'29' not found in badging"
  fi

  # 3. Permissions
  perms="$("$AAPT2" dump permissions "$apk" 2>&1 | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p" | sort -u)"
  printf '%s\n' "$perms" | sed 's/^/  uses-permission: /'
  if grep -q 'AD_ID' <<< "$perms"; then
    fail "AD_ID permission present"
  fi
  if [ "$perms" = "$EXPECTED_PERMISSIONS" ]; then
    pass "permissions are exactly INTERNET + ACCESS_NETWORK_STATE"
  else
    fail "permissions differ from INTERNET + ACCESS_NETWORK_STATE"
  fi
done

# 4. Dependency report
log "Resolving :androidApp releaseRuntimeClasspath"
deps="$(cd "$REPO_ROOT" && ./gradlew -q :androidApp:dependencies --configuration releaseRuntimeClasspath 2>&1)"
deps_status=$?
if [ "$deps_status" -ne 0 ] || ! grep -q -- '--- ' <<< "$deps"; then
  printf '%s\n' "$deps" | tail -n 20
  fail "could not produce the releaseRuntimeClasspath dependency report"
else
  pattern="$(printf '%s:|' "${FORBIDDEN_GROUPS[@]}" | sed 's/\./\\./g; s/|$//')"
  hits="$(grep -E -- "($pattern)" <<< "$deps" || true)"
  if [ -n "$hits" ]; then
    printf '%s\n' "$hits" | sed 's/^/  /'
    fail "forbidden Google / tracker groups on releaseRuntimeClasspath"
  else
    pass "releaseRuntimeClasspath has none of the ${#FORBIDDEN_GROUPS[@]} forbidden groups"
  fi
fi

if [ "$failures" -ne 0 ]; then
  log "$failures check(s) failed"
  exit 1
fi
log "All release checks passed"
