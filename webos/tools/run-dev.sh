#!/usr/bin/env bash
# Vision+ LG webOS - servidor de desarrollo para probar la app en un navegador.
#
# Mantiene la interfaz de LG (sin ?platform=pc) y activa el relay de FFmpeg
# solo para el navegador, porque Chrome/Edge no reproducen MPEG-TS/HEVC.
# En la TV real no se usa este script: la app reproduce de forma nativa.
#
#   ./webos/tools/run-dev.sh
#   abrir http://127.0.0.1:8910/?relay=1
#
# Variables:
#   PORT=8910              puerto local
#   VISION_FFMPEG=...      FFmpeg que sí ejecute en este equipo (recomendado
#                          si el `ffmpeg` del PATH falla con el CDN)
#   VISION_DATA_DIR=...    carpeta para sesión, caché y relay.log
#
# El FFmpeg ARM incluido en el IPK no se puede ejecutar en x86; el motor
# intenta primero el binario configurado y, si falla, el del PATH.

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DATA="${VISION_DATA_DIR:-$REPO/webos/build/dev-data}"
PORT="${VISION_PORT:-${PORT:-8910}}"

mkdir -p "$DATA"

export VISION_RELAY=1
export VISION_APP_DIR="${VISION_APP_DIR:-$REPO/webos/app}"
export VISION_SERVICE_DIR="${VISION_SERVICE_DIR:-$REPO/webos/service}"
export VISION_DATA_DIR="$DATA"
export VISION_PORT="$PORT"
export PORT="$PORT"

if [ -z "${VISION_FFMPEG:-}" ]; then
  if command -v ffmpeg >/dev/null 2>&1; then
    export VISION_FFMPEG="$(command -v ffmpeg)"
  fi
fi

echo "Vision+ LG webOS (dev)"
echo "  app:     $VISION_APP_DIR"
echo "  service: $VISION_SERVICE_DIR"
echo "  ffmpeg:  ${VISION_FFMPEG:-el incluido / PATH}"
echo "  datos:   $VISION_DATA_DIR"
echo "  url:     http://127.0.0.1:$PORT/?relay=1"
echo
echo "Si el video no arranca, exporta un FFmpeg x86 que funcione:"
echo "  VISION_FFMPEG=/ruta/a/ffmpeg ./webos/tools/run-dev.sh"
echo

exec node "$REPO/webos/service/dev-server.js"
