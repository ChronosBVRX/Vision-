#!/usr/bin/env bash
# Publica el ejecutable portable de PC y su manifiesto en el canal fijo
# `pc-latest` de GitHub Releases. No toca el canal estable de Android.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PC="$REPO_ROOT/pc"
EXE="$PC/build/VisionPlusPC-Portable.exe"
VERSION="$(node -p "require('$PC/electron/package.json').version")"
NOTES_FILE="${1:-$PC/electron/OTA_NOTES.md}"
OUT_MANIFEST="$PC/build/pc-version.json"
REPO="${VISION_PC_RELEASE_REPO:-ChronosBVRX/Vision-Releases}"
TAG="${VISION_PC_RELEASE_TAG:-pc-latest}"

if [ ! -f "$EXE" ]; then
  echo "FALTA: $EXE" >&2
  exit 1
fi
if [ ! -f "$NOTES_FILE" ]; then
  echo "FALTA el archivo de notas: $NOTES_FILE" >&2
  exit 1
fi

SHA="$(sha256sum "$EXE" | awk '{print $1}')"
SIZE="$(stat -c '%s' "$EXE")"
NOTES="$(python3 -c 'import json,sys; print(json.dumps(open(sys.argv[1],encoding="utf-8").read()))' "$NOTES_FILE")"

python3 - "$OUT_MANIFEST" "$VERSION" "$SHA" "$SIZE" "$NOTES" <<'PY'
import json, sys
path, version, sha, size, notes = sys.argv[1:6]
data = {
    "version": version,
    "exe": "VisionPlusPC-Portable.exe",
    "sha256": sha,
    "size": int(size),
    "notes": json.loads(notes),
}
with open(path, "w", encoding="utf-8") as handle:
    json.dump(data, handle, ensure_ascii=False, separators=(",", ":"))
PY

echo "==> PC $VERSION · $SHA · $SIZE bytes"
if ! gh release view "$TAG" -R "$REPO" >/dev/null 2>&1; then
  gh release create "$TAG" -R "$REPO" \
    --prerelease \
    --title "Vision+ PC — canal OTA" \
    --notes "Canal fijo de actualizaciones del ejecutable portable de Vision+ PC."
fi
gh release edit "$TAG" -R "$REPO" \
  --prerelease \
  --title "Vision+ PC — canal OTA" \
  --notes "Canal fijo de actualizaciones del ejecutable portable de Vision+ PC."

gh release upload "$TAG" "$EXE" "$OUT_MANIFEST" -R "$REPO" --clobber
echo "==> Publicado: https://github.com/$REPO/releases/download/$TAG/pc-version.json"
