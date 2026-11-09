#!/usr/bin/env bash
# Vision+ LG webOS - lanza la app en el webOS TV 26 Simulator oficial.
#
# El simulador no instala IPK: ejecuta el directorio de la app y permite
# agregar el servicio Luna desde su interfaz. Para probar sin ese paso, este
# script lanza la app con launchParams que apuntan al dev-server HTTP y activan
# el relay. Así se valida la interfaz y el reproductor en el Chromium real de
# webOS; el servicio Luna se sigue probando con dev-server/smoke-test.
#
# Requisitos:
#   1. Descargar y extraer el webOS TV 26 Simulator de LG.
#   2. Definir WEBOS_SIMULATOR_BIN=/ruta/al/binario/extraído.
#   3. Tener ./webos/tools/run-dev.sh corriendo en otra terminal.
#
#   WEBOS_SIMULATOR_BIN=/tmp/webos-sim/squashfs-root/webOS_TV_26_Simulator_1.5.0 \
#     ./webos/tools/run-simulator.sh
#
# Variables:
#   WEBOS_SIMULATOR_BIN   binario extraído del simulador (obligatorio)
#   VISION_DEV_SERVICE    dev-server (por defecto http://127.0.0.1:8910)
#   VISION_SIM_RELAY      true/false; fuerza el relay en el simulador
#   VISION_SIM_DEBUG     1 para abrir el puerto CDP 9222

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SIM_BIN="${WEBOS_SIMULATOR_BIN:-}"
DEV_SERVICE="${VISION_DEV_SERVICE:-http://127.0.0.1:8910}"
RELAY="${VISION_SIM_RELAY:-true}"
VERSION="26"
WRAPPER_DIR="$REPO/webos/build/simulator"
WRAPPER="$WRAPPER_DIR/webOS_TV_${VERSION}_Simulator_1.5.0.appimage"

if [ -z "$SIM_BIN" ] || [ ! -x "$SIM_BIN" ]; then
  echo "Define WEBOS_SIMULATOR_BIN con el binario extraído del simulador." >&2
  echo "Ejemplo: WEBOS_SIMULATOR_BIN=~/webOS_TV_26_Simulator_1.5.0" >&2
  exit 1
fi

if ! curl -fsS -X POST "$DEV_SERVICE/ping" -H 'Content-Type: application/json' -d '{}' >/dev/null 2>&1; then
  echo "El dev-server no responde en $DEV_SERVICE." >&2
  echo "En otra terminal: VISION_FFMPEG=/ruta/a/ffmpeg ./webos/tools/run-dev.sh" >&2
  exit 1
fi

mkdir -p "$WRAPPER_DIR"
DEBUG_FLAG=""
if [ "${VISION_SIM_DEBUG:-0}" = "1" ]; then
  DEBUG_FLAG=" --remote-debugging-port=9222"
fi

cat > "$WRAPPER" <<EOF
#!/bin/bash
APP="\${1:-}"
PARAMS='{"devService":"$DEV_SERVICE","relay":$RELAY}'
exec "$SIM_BIN" "\$APP" "\$PARAMS" --no-sandbox --ozone-platform=x11$DEBUG_FLAG
EOF
chmod +x "$WRAPPER"

echo "Simulador: $SIM_BIN"
echo "App:       $REPO/webos/app"
echo "Servicio:  $DEV_SERVICE"
echo "Relay:     $RELAY"
echo

npx --yes -p @webos-tools/cli ares-launch -s "$VERSION" "$REPO/webos/app" -sp "$WRAPPER_DIR"
