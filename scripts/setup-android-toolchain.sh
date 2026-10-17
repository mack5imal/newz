#!/usr/bin/env bash
# setup-android-toolchain.sh — install JDK 17 and the Android command-line SDK, accept
# licences, install the pinned SDK packages, create the `newz-api34` AOSP AVD and wire
# Gradle to them. Idempotent: a second run installs nothing new.
#
# Usage: bash scripts/setup-android-toolchain.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AVD_NAME="newz-api34"
SYSTEM_IMAGE="system-images;android-34;default;arm64-v8a"
LOCAL_JDK_DIR="$HOME/.local/jdk-17"

log() { printf '[setup] %s\n' "$*"; }

if ! command -v brew > /dev/null 2>&1; then
  echo "[setup] Homebrew is required (https://brew.sh)." >&2
  exit 1
fi
BREW_PREFIX="$(brew --prefix)"

# --- JDK 17 ----------------------------------------------------------------------------
# A JDK bundle under ~/Library/Java/JavaVirtualMachines is found by /usr/libexec/java_home
# and the /usr/bin/java stub, so `./gradlew` can start in a fresh shell without JAVA_HOME.
USER_JVM_BUNDLE="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk"
find_jdk17() {
  /usr/libexec/java_home -v 17 2> /dev/null || return 1
}

if JAVA_HOME="$(find_jdk17)"; then
  log "JDK 17 already present: $JAVA_HOME"
else
  if sudo -n true 2> /dev/null; then
    log "Installing temurin@17 with Homebrew"
    brew install --cask temurin@17
  fi
  if ! JAVA_HOME="$(find_jdk17)"; then
    # The temurin cask is a .pkg that needs an admin password; without one, install the
    # Temurin 17 tarball into $HOME instead (user JVM dir, plus a ~/.local/jdk-17 link).
    log "Installing Temurin 17 tarball into $USER_JVM_BUNDLE"
    tmp="$(mktemp -d)"
    curl -fsSL -o "$tmp/jdk.tar.gz" \
      "https://api.adoptium.net/v3/binary/latest/17/ga/mac/aarch64/jdk/hotspot/normal/eclipse"
    tar -xzf "$tmp/jdk.tar.gz" -C "$tmp"
    bundle="$(find "$tmp" -maxdepth 1 -mindepth 1 -type d -name 'jdk-17*' | head -n 1)"
    mkdir -p "$(dirname "$USER_JVM_BUNDLE")"
    rm -rf "$USER_JVM_BUNDLE"
    mv "$bundle" "$USER_JVM_BUNDLE"
    rm -rf "$tmp"
    JAVA_HOME="$(find_jdk17)"
  fi
fi
if [ ! -e "$LOCAL_JDK_DIR" ] && [ -d "$USER_JVM_BUNDLE/Contents/Home" ]; then
  mkdir -p "$(dirname "$LOCAL_JDK_DIR")"
  ln -s "$USER_JVM_BUNDLE/Contents/Home" "$LOCAL_JDK_DIR"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# --- Android command-line tools --------------------------------------------------------
if brew list --cask android-commandlinetools > /dev/null 2>&1; then
  log "android-commandlinetools already installed"
else
  log "Installing android-commandlinetools with Homebrew"
  brew install --cask android-commandlinetools
fi
ANDROID_HOME="$BREW_PREFIX/share/android-commandlinetools"
export ANDROID_HOME ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"

# Licences: `yes` dies of SIGPIPE when sdkmanager exits, which pipefail would report.
{ yes || true; } | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses > /dev/null

installed="$("$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list_installed 2> /dev/null | awk '{print $1}')"

# Newest stable build-tools 36.x: prefer an installed one, else ask sdkmanager.
build_tools="$(printf '%s\n' "$installed" | grep -E '^build-tools;36\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
if [ -z "$build_tools" ]; then
  build_tools="$("$SDKMANAGER" --sdk_root="$ANDROID_HOME" --list 2> /dev/null |
    awk '{print $1}' | grep -E '^build-tools;36\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1)"
fi
log "build-tools package: $build_tools"

wanted=("platform-tools" "platforms;android-37.0" "platforms;android-36" "platforms;android-34" "$build_tools" "emulator" "$SYSTEM_IMAGE")
missing=()
for pkg in "${wanted[@]}"; do
  if ! printf '%s\n' "$installed" | grep -qxF "$pkg"; then
    missing+=("$pkg")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  log "Installing SDK packages: ${missing[*]}"
  { yes || true; } | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" "${missing[@]}" > /dev/null
else
  log "All SDK packages already installed"
fi

# --- AVD -------------------------------------------------------------------------------
AVD_DIR="$HOME/.android/avd/$AVD_NAME.avd"
if [ -f "$AVD_DIR/config.ini" ]; then
  log "AVD $AVD_NAME already exists"
else
  log "Creating AVD $AVD_NAME"
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d pixel_6 > /dev/null
fi
if grep -q '^hw.ramSize=' "$AVD_DIR/config.ini"; then
  sed -i '' 's/^hw\.ramSize=.*/hw.ramSize=2048/' "$AVD_DIR/config.ini"
else
  echo 'hw.ramSize=2048' >> "$AVD_DIR/config.ini"
fi

# --- Analytics opt-out -----------------------------------------------------------------
mkdir -p "$HOME/.android"
printf '%s\n' '{"userId":"","hasOptedIn":false}' > "$HOME/.android/analytics.settings"

# --- Gradle wiring ---------------------------------------------------------------------
set_property() { # file key value — add or replace one key, leave other lines alone
  local file="$1" key="$2" value="$3" tmp
  mkdir -p "$(dirname "$file")"
  touch "$file"
  tmp="$(mktemp)"
  grep -v "^${key//./\\.}=" "$file" > "$tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  mv "$tmp" "$file"
}
set_property "$HOME/.gradle/gradle.properties" "org.gradle.java.home" "$JAVA_HOME"
set_property "$REPO_ROOT/local.properties" "sdk.dir" "$ANDROID_HOME"

log "JAVA_HOME=$JAVA_HOME"
log "ANDROID_HOME=$ANDROID_HOME"
log "Done."
