#!/usr/bin/env bash
# Vision+ PC - smoke test manual del servidor y del relay.
#
# Verifica sin abrir navegador:
#   1. /ping responde y reporta relayAvailable=true;
#   2. /home devuelve catálogo;
#   3. /resolve de un título real devuelve isRelay=true;
#   4. el relay entrega un MP4 H.264 reproducible (si ffprobe existe).
#
# Requiere internet (VPS + portal XTV) y ffmpeg en el PATH.
#
#   ./pc/tools/smoke-test.sh

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORT="${VISION_PORT:-8929}"
DATA="$(mktemp -d /tmp/visionpc-smoke.XXXXXX)"
LOG="$DATA/server.log"
PID=""

cleanup() {
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -rf "$DATA"
}
trap cleanup EXIT

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg no está en el PATH; el relay no estará disponible." >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "curl es necesario para el smoke test." >&2
  exit 1
fi

echo "==> Servidor en 127.0.0.1:$PORT"
(
  cd "$REPO"
  VISION_PC=1 \
  VISION_APP_DIR="$REPO/webos/app" \
  VISION_SERVICE_DIR="$REPO/webos/service" \
  VISION_DATA_DIR="$DATA" \
  VISION_FFMPEG="$(command -v ffmpeg)" \
  VISION_PORT="$PORT" \
  exec node pc/server.js
) >"$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 40); do
  if curl -fsS -X POST "http://127.0.0.1:$PORT/ping" -H 'Content-Type: application/json' -d '{}' >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

PING="$(curl -fsS -X POST "http://127.0.0.1:$PORT/ping" -H 'Content-Type: application/json' -d '{}')"
echo "    $PING"
echo "$PING" | grep -q '"relayAvailable":true' || { echo "FALLO: relayAvailable != true"; exit 1; }
echo "$PING" | grep -q '"platform":"windows"' || { echo "FALLO: platform != windows"; exit 1; }

echo "==> Catálogo"
HOME_JSON="$(curl -fsS -X POST "http://127.0.0.1:$PORT/home" -H 'Content-Type: application/json' -d '{"limit":1}')"
echo "$HOME_JSON" | grep -q '"categories"' || { echo "FALLO: /home sin categorías"; exit 1; }

echo "==> Resolver un título real (relay HEVC)"
RESOLVE="$(curl -fsS --max-time 60 -X POST "http://127.0.0.1:$PORT/resolve" \
  -H 'Content-Type: application/json' \
  -d '{"ref":"xtv:A93658121D1840E78F958BAFAB9D409C","contentId":"A93658121D1840E78F958BAFAB9D409C"}')"
echo "$RESOLVE" | grep -q '"isRelay":true' || { echo "FALLO: el título no se enrutó al relay"; exit 1; }
RELAY_URL="$(printf '%s' "$RESOLVE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["proxyPath"])')"
echo "    $RELAY_URL"

echo "==> Relay (primeros segundos)"
curl -fsS --max-time 15 "$RELAY_URL" -o "$DATA/sample.mp4" || true
SIZE="$(stat -c%s "$DATA/sample.mp4" 2>/dev/null || echo 0)"
echo "    $SIZE bytes"
[ "$SIZE" -gt 100000 ] || { echo "FALLO: el relay no entregó video"; tail -40 "$LOG"; exit 1; }

if command -v ffprobe >/dev/null 2>&1; then
  echo "==> ffprobe"
  ffprobe -v error -show_entries stream=codec_name,codec_type,width,height -of default=noprint_wrappers=1 "$DATA/sample.mp4"
  ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "$DATA/sample.mp4" | grep -q '^h264$' \
    || { echo "FALLO: el relay no entregó H.264"; exit 1; }
fi

echo
echo "OK: Vision+ PC responde, resuelve y transcodifica a H.264."
