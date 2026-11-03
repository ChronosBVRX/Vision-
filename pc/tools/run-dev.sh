#!/usr/bin/env bash
# Vision+ PC - servidor de desarrollo en Linux/macOS.
#
# Usa la interfaz y el motor del port webOS, pero con el modo PC activado
# (HLS/TS/HEVC por FFmpeg -> MP4 H.264). No requiere empaquetar nada.
#
#   ./pc/tools/run-dev.sh
#   abrir http://127.0.0.1:8910/?platform=pc
#
# Variables:
#   PORT=8910          puerto local
#   VISION_FFMPEG=...  binario de ffmpeg (por defecto `ffmpeg` del PATH)
#   VISION_PORT=...    alias de PORT

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DATA="${VISION_DATA_DIR:-$REPO/pc/build/dev-data}"

mkdir -p "$DATA"

export VISION_PC=1
export VISION_APP_DIR="${VISION_APP_DIR:-$REPO/webos/app}"
export VISION_SERVICE_DIR="${VISION_SERVICE_DIR:-$REPO/webos/service}"
export VISION_DATA_DIR="$DATA"
export VISION_PORT="${VISION_PORT:-${PORT:-8910}}"
export PORT="$VISION_PORT"

if [ -z "${VISION_FFMPEG:-}" ]; then
  if command -v ffmpeg >/dev/null 2>&1; then
    export VISION_FFMPEG="$(command -v ffmpeg)"
  fi
fi

echo "Vision+ PC (dev)"
echo "  app:     $VISION_APP_DIR"
echo "  service: $VISION_SERVICE_DIR"
echo "  ffmpeg:  ${VISION_FFMPEG:-no encontrado}"
echo "  datos:   $VISION_DATA_DIR"
echo "  url:     http://127.0.0.1:$VISION_PORT/?platform=pc"
echo

exec node "$REPO/pc/server.js"
