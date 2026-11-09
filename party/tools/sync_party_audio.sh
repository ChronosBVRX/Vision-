#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OPL_ROOT="${OPL_ROOT:-$ROOT/party/platform/open-party-lab}"
DEST="$OPL_ROOT/apps/host/public/party-audio"
# Adjust DEST if your Open Party Lab checkout lives elsewhere.
for game in pantomime zeichnen-und-erraten schaetzorama imposter buzzwort schattenjagd flatterfluff magic-arena card-table dungeon-party selection; do
  mkdir -p "$DEST/$game"
  cp "$ROOT/party/content/narrator/audio/$game/"*.m4a "$DEST/$game/"
done
echo "Audio copiado a $DEST"
