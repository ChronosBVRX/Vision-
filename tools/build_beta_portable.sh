#!/usr/bin/env bash
# Construye una beta de Vision+ con el motor X4K dentro y la firma histórica.
# Uso: tools/build_beta_portable.sh <versionCode> <versionName>
# Ejemplo: tools/build_beta_portable.sh 486 2.6.0-beta1
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION_CODE="${1:?Falta versionCode, p. ej. 486}"
VERSION_NAME="${2:?Falta versionName, p. ej. 2.6.0-beta1}"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/sdk}}"
BUILD_TOOLS="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
[ -x "$ZIPALIGN" ] || { echo "No encontré zipalign en $BUILD_TOOLS"; exit 1; }
[ -x "$APKSIGNER" ] || { echo "No encontré apksigner en $BUILD_TOOLS"; exit 1; }

./gradlew :app:assembleNormalRelease \
  -PfiestsVersionCode="$VERSION_CODE" \
  -PfiestsVersionName="$VERSION_NAME" \
  --console=plain

SRC="app/build/outputs/apk/normal/release/app-normal-release.apk"
WORK="$(mktemp -d /tmp/opencode/visionplus-beta.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT
INJECTED="$WORK/engine.apk"
ALIGNED="$WORK/aligned.apk"
OUT="$WORK/VisionPlus.apk"

python3 tools/inject_engine_dex.py "$SRC" "$INJECTED" app/src/debug/assets
"$ZIPALIGN" -p -f 4 "$INJECTED" "$ALIGNED"

# keystore.properties puede venir con CRLF de Windows; limpiamos en una copia temporal.
PROPS="$WORK/keystore.properties"
tr -d '\r' < keystore.properties > "$PROPS"
chmod 600 "$PROPS"
STORE=$(awk -F= '/^storeFile=/{sub(/^storeFile=/,""); print; exit}' "$PROPS")
STOREPASS=$(awk -F= '/^storePassword=/{sub(/^storePassword=/,""); print; exit}' "$PROPS")
ALIAS=$(awk -F= '/^keyAlias=/{sub(/^keyAlias=/,""); print; exit}' "$PROPS")
KEYPASS=$(awk -F= '/^keyPassword=/{sub(/^keyPassword=/,""); print; exit}' "$PROPS")

"$APKSIGNER" sign \
  --ks "$STORE" \
  --ks-key-alias "$ALIAS" \
  --ks-pass "pass:$STOREPASS" \
  --key-pass "pass:$KEYPASS" \
  --out "$OUT" "$ALIGNED"

"$APKSIGNER" verify --print-certs "$OUT" | grep -m1 "SHA-256 digest"

mkdir -p beta
cp "$OUT" beta/VisionPlus.apk
cp VisionPlusHome.apk beta/VisionPlusHome.apk
sha256sum beta/VisionPlus.apk beta/VisionPlusHome.apk
echo "Beta lista en beta/VisionPlus.apk ($VERSION_NAME / vc$VERSION_CODE)"
