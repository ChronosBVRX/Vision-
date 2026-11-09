#!/usr/bin/env bash
# Vision+ LG webOS - smoke test del servidor de desarrollo y del relay.
#
# Verifica:
#   1. /ping responde como webOS y reporta relayAvailable=true;
#   2. /home devuelve catálogo;
#   3. /resolve de un título real se enruta al relay;
#   4. el relay entrega un MP4 H.264 reproducible.
#
# Requiere internet (VPS + portal XTV), curl, python3 y un FFmpeg x86 que
# funcione en este equipo. El FFmpeg ARM del IPK no sirve para el PC.
#
#   VISION_FFMPEG=/ruta/a/ffmpeg ./webos/tools/smoke-test.sh

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PORT="${VISION_PORT:-8930}"
DATA="$(mktemp -d /tmp/vision-lg-smoke.XXXXXX)"
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

if ! command -v curl >/dev/null 2>&1; then
  echo "curl es necesario para el smoke test." >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 es necesario para leer el JSON." >&2
  exit 1
fi

if [ -z "${VISION_FFMPEG:-}" ]; then
  if command -v ffmpeg >/dev/null 2>&1; then
    VISION_FFMPEG="$(command -v ffmpeg)"
  fi
fi
if [ -z "${VISION_FFMPEG:-}" ]; then
  echo "No hay FFmpeg. Define VISION_FFMPEG=/ruta/a/ffmpeg." >&2
  exit 1
fi
if ! "$VISION_FFMPEG" -version >/dev/null 2>&1; then
  echo "VISION_FFMPEG no se puede ejecutar: $VISION_FFMPEG" >&2
  exit 1
fi

FFPROBE="${VISION_FFPROBE:-}"
if [ -z "$FFPROBE" ]; then
  FFPROBE="$(dirname "$VISION_FFMPEG")/ffprobe"
  [ -x "$FFPROBE" ] || FFPROBE="$(command -v ffprobe || true)"
fi

echo "==> Servidor LG webOS en 127.0.0.1:$PORT"
(
  cd "$REPO"
  VISION_RELAY=1 \
  VISION_APP_DIR="$REPO/webos/app" \
  VISION_SERVICE_DIR="$REPO/webos/service" \
  VISION_DATA_DIR="$DATA" \
  VISION_FFMPEG="$VISION_FFMPEG" \
  VISION_PORT="$PORT" \
  PORT="$PORT" \
  exec node webos/service/dev-server.js
) >"$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 50); do
  if curl -fsS -X POST "http://127.0.0.1:$PORT/ping" -H 'Content-Type: application/json' -d '{}' >/dev/null 2>&1; then
    break
  fi
  sleep 0.25
done

PING="$(curl -fsS -X POST "http://127.0.0.1:$PORT/ping" -H 'Content-Type: application/json' -d '{}')"
echo "    $PING"
echo "$PING" | grep -q '"platform":"webos"' || { echo "FALLO: no se reportó webOS"; exit 1; }
echo "$PING" | grep -q '"relayAvailable":true' || { echo "FALLO: relayAvailable != true"; tail -40 "$LOG"; exit 1; }

echo "==> Catálogo"
HOME_JSON="$(curl -fsS -X POST "http://127.0.0.1:$PORT/home" -H 'Content-Type: application/json' -d '{"limit":2}')"
echo "$HOME_JSON" | grep -q '"categories"' || { echo "FALLO: /home sin categorías"; exit 1; }

REF="$(printf '%s' "$HOME_JSON" | python3 -c '
import json, sys
d = json.load(sys.stdin)
for category in d.get("categories", []):
    for film in category.get("films", []):
        ref = film.get("videoUrl") or film.get("id") or ""
        if str(ref).startswith("xtv:") and film.get("kind") == "movie":
            print(ref)
            raise SystemExit
for category in d.get("categories", []):
    for film in category.get("films", []):
        ref = film.get("videoUrl") or film.get("id") or ""
        if str(ref).startswith("xtv:"):
            print(ref)
            raise SystemExit
')"
[ -n "$REF" ] || REF="xtv:A93658121D1840E78F958BAFAB9D409C"
echo "    ref: $REF"

echo "==> Resolver un título real (relay)"
RESOLVE="$(curl -fsS --max-time 90 -X POST "http://127.0.0.1:$PORT/resolve" \
  -H 'Content-Type: application/json' \
  -d "{\"ref\":\"$REF\",\"forceRelay\":true}")"
echo "$RESOLVE" | grep -q '"isRelay":true' || { echo "FALLO: el título no se enrutó al relay"; echo "$RESOLVE" | head -c 800; exit 1; }
RELAY_URL="$(printf '%s' "$RESOLVE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["proxyPath"])')"
echo "    $RELAY_URL"

echo "==> Relay (primeros segundos)"
# Se corta a 3 s con el mismo FFmpeg: el relay transcodifica a toda velocidad
# y una descarga completa del sample crecería cientos de MB.
"$VISION_FFMPEG" -y -hide_banner -loglevel error -i "$RELAY_URL" -t 3 -c copy "$DATA/sample.mp4" || true
SIZE="$(stat -c%s "$DATA/sample.mp4" 2>/dev/null || echo 0)"
echo "    $SIZE bytes"
[ "$SIZE" -gt 100000 ] || { echo "FALLO: el relay no entregó video"; tail -60 "$LOG"; exit 1; }

if [ -n "$FFPROBE" ] && [ -x "$FFPROBE" ]; then
  echo "==> ffprobe"
  "$FFPROBE" -v error -show_entries stream=codec_name,codec_type,width,height -of default=noprint_wrappers=1 "$DATA/sample.mp4"
  "$FFPROBE" -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "$DATA/sample.mp4" | grep -q '^h264$' \
    || { echo "FALLO: el relay no entregó H.264"; exit 1; }
else
  echo "    (sin ffprobe; se omite la comprobación de códec)"
fi

echo
echo "OK: Vision+ LG webOS responde, resuelve y transcodifica a H.264."
