#!/usr/bin/env bash
# Extrae el ffmpeg ARM del .ipk de XuperTV al servicio de Vision+.
# Uso: ./webos/tools/prepare-ffmpeg.sh /ruta/com.webosflux.xuptertv.fidelity_*.ipk
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
IPK="${1:-}"
if [ -z "$IPK" ] || [ ! -f "$IPK" ]; then
  echo "Uso: $0 /ruta/com.webosflux.xuptertv.fidelity_*.ipk" >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
ar x "$IPK"
tar xzf data.tar.gz

FFMPEG="$(find usr/palm/services -type f -name ffmpeg | head -n 1)"
LICENSE="$(find usr/palm/services -type f -name 'ffmpeg-LICENSE.txt' | head -n 1)"
if [ -z "$FFMPEG" ]; then
  echo "No se encontró ffmpeg dentro del ipk." >&2
  exit 1
fi

cp "$FFMPEG" "$ROOT/webos/service/ffmpeg"
chmod 755 "$ROOT/webos/service/ffmpeg"
if [ -n "$LICENSE" ]; then
  cp "$LICENSE" "$ROOT/webos/service/ffmpeg-LICENSE.txt"
fi
echo "ffmpeg listo en webos/service/ffmpeg"
