#!/usr/bin/env bash
# Vision+ PC — construye UN solo ejecutable portable para Windows.
#
# Resultado:
#   pc/build/VisionPlusPC-Portable.exe
#
# Incluye Chromium/Electron, la app de Smart TV, el motor webOS, server.js y
# FFmpeg Windows. No requiere Edge/Chrome, Node ni FFmpeg en la PC destino.
#
# Requisitos de build: Node.js 18+, npm, curl, 7z y ~1.5 GB libres.
#
#   ./pc/tools/build-single-exe.sh
#
# Variables:
#   VISION_FFMPEG_WIN=/ruta/ffmpeg.exe   usa un ffmpeg.exe local
#   VISION_FFMPEG_LICENSE=/ruta/LICENSE  licencia del build de FFmpeg

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PC="$REPO/pc"
WORK="$(mktemp -d /tmp/visionpc-electron.XXXXXX)"
NODE_VERSION="${NODE_VERSION:-v22.14.0}"
FFMPEG_URL="${FFMPEG_URL:-https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip}"
OUT="$PC/build"
FFMPEG_WIN="${VISION_FFMPEG_WIN:-}"

cleanup() {
  rm -rf "$WORK"
}
trap cleanup EXIT

echo "==> Preparando árbol de Electron"
mkdir -p "$WORK/runtime"
cp "$PC/electron/main.js" "$WORK/main.js"
cp "$PC/electron/preload.js" "$WORK/preload.js"
cp "$PC/electron/updater.js" "$WORK/updater.js"
cp "$PC/electron/package.json" "$WORK/package.json"
cp "$PC/electron/FFMPEG-LICENSE.txt" "$WORK/runtime/FFMPEG-LICENSE.txt"
cp "$PC/server.js" "$WORK/server.js"
cp "$REPO/vision_icon.ico" "$WORK/icon.ico"
cp "$PC/electron/icon.png" "$WORK/icon.png"
cp "$PC/electron/splash.html" "$WORK/splash.html"
cp "$PC/electron/vision_logo_horizontal.png" "$WORK/vision_logo_horizontal.png"
cp "$PC/electron/splash.bmp" "$WORK/splash.bmp"
cp "$PC/LEEME-PC.txt" "$WORK/LEEME.txt"

cp -a "$REPO/webos/app" "$WORK/app"
cp "$REPO/vision_icon.ico" "$WORK/app/images/icon.ico"
mkdir -p "$WORK/service"
cp "$REPO/webos/service/"*.js "$WORK/service/"
cp "$REPO/webos/service/package.json" "$WORK/service/package.json"

if [ -z "$FFMPEG_WIN" ] && [ -f "$PC/runtime/ffmpeg.exe" ]; then
  FFMPEG_WIN="$PC/runtime/ffmpeg.exe"
fi

if [ -n "$FFMPEG_WIN" ]; then
  echo "==> FFmpeg local: $FFMPEG_WIN"
  cp "$FFMPEG_WIN" "$WORK/runtime/ffmpeg.exe"
  if [ -n "${VISION_FFMPEG_LICENSE:-}" ] && [ -f "$VISION_FFMPEG_LICENSE" ]; then
    cp "$VISION_FFMPEG_LICENSE" "$WORK/runtime/FFMPEG-LICENSE.txt"
  fi
else
  echo "==> Descargando FFmpeg Windows"
  curl -sL --max-time 600 -o "$WORK/ffmpeg.zip" "$FFMPEG_URL"
  7z x -y -o"$WORK/ffmpeg-win" "$WORK/ffmpeg.zip" >/dev/null
  FOUND="$(find "$WORK/ffmpeg-win" -name ffmpeg.exe -type f | head -1)"
  if [ -z "$FOUND" ]; then
    echo "FALLO: el paquete de FFmpeg no contiene ffmpeg.exe" >&2
    exit 1
  fi
  cp "$FOUND" "$WORK/runtime/ffmpeg.exe"
  LICENSE="$(find "$WORK/ffmpeg-win" -iname 'LICENSE*' -type f | head -1)"
  if [ -n "$LICENSE" ]; then cp "$LICENSE" "$WORK/runtime/FFMPEG-LICENSE.txt"; fi
fi

echo "==> Instalando electron/electron-builder (si hace falta)"
if [ ! -d "$WORK/node_modules/electron-builder" ]; then
  (
    cd "$WORK"
    ELECTRON_SKIP_BINARY_DOWNLOAD=1 npm install --no-audit --no-fund >/dev/null
  )
fi

echo "==> Empaquetando .exe portable"
(
  cd "$WORK"
  ELECTRON_SKIP_BINARY_DOWNLOAD=1 npx electron-builder --win portable --x64
)

mkdir -p "$OUT"
cp "$WORK/dist/VisionPlusPC-Portable.exe" "$OUT/VisionPlusPC-Portable.exe"
echo
echo "Listo:"
ls -lh "$OUT/VisionPlusPC-Portable.exe"
sha256sum "$OUT/VisionPlusPC-Portable.exe"
