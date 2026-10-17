#!/usr/bin/env bash
# make-release-key.sh — create the project-local release signing key for Newz.
#
# Idempotent: if keystore/newz-release.jks already exists it is left untouched.
# Writes keystore/keystore.properties (storeFile, storePassword, keyAlias,
# keyPassword), which androidApp/build.gradle.kts reads for the release build.
# keystore/ is gitignored; the passwords are never printed.
#
# Usage: bash scripts/make-release-key.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEY_DIR="$REPO_ROOT/keystore"
KEYSTORE="$KEY_DIR/newz-release.jks"
PROPS="$KEY_DIR/keystore.properties"
ALIAS="newz"

log() { printf '[release-key] %s\n' "$*"; }

find_keytool() {
  local home
  if home="$(/usr/libexec/java_home -v 17 2> /dev/null)" && [ -x "$home/bin/keytool" ]; then
    printf '%s\n' "$home/bin/keytool"
  elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/keytool" ]; then
    printf '%s\n' "$JAVA_HOME/bin/keytool"
  else
    command -v keytool
  fi
}

backup_reminder() {
  log "IMPORTANT: back up the whole keystore/ folder (newz-release.jks + keystore.properties)"
  log "somewhere safe. Updates must be signed with this same key; if it is lost, the app"
  log "has to be uninstalled before a build signed with a new key can be installed."
}

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

if [ -f "$KEYSTORE" ]; then
  log "Keystore already exists: keystore/newz-release.jks (left unchanged)"
  if [ ! -f "$PROPS" ]; then
    log "keystore/keystore.properties is missing, but the passwords cannot be recovered."
    log "Restore keystore.properties from your backup."
    exit 1
  fi
  backup_reminder
  exit 0
fi

KEYTOOL="$(find_keytool)"
# A PKCS12 keystore (keytool's default) uses one password for the store and the key,
# so the same random password is written as both storePassword and keyPassword.
NEWZ_KEY_PASSWORD="$(openssl rand -base64 24)"
export NEWZ_KEY_PASSWORD

log "Generating 4096-bit RSA key 'newz' (CN=Newz, valid 10000 days) — this can take a moment"
"$KEYTOOL" -genkeypair -v \
  -keystore "$KEYSTORE" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Newz" \
  -storepass:env NEWZ_KEY_PASSWORD \
  -keypass:env NEWZ_KEY_PASSWORD > /dev/null 2>&1

umask 077
{
  printf 'storeFile=newz-release.jks\n'
  printf 'storePassword=%s\n' "$NEWZ_KEY_PASSWORD"
  printf 'keyAlias=%s\n' "$ALIAS"
  printf 'keyPassword=%s\n' "$NEWZ_KEY_PASSWORD"
} > "$PROPS"
chmod 600 "$KEYSTORE" "$PROPS"
unset NEWZ_KEY_PASSWORD

log "Created keystore/newz-release.jks and keystore/keystore.properties"
backup_reminder
