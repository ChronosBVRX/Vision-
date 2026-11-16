#!/bin/bash
# Despliega el broker aislado en el VPS nuevo.
# Requisitos: Docker + Docker Compose plugin + Caddy instalados, y ejecutarse como root.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Ejecuta como root: sudo bash deploy.sh"
  exit 1
fi

for cmd in docker caddy; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Falta $cmd. Instálalo antes de continuar."
    exit 1
  fi
done
docker compose version >/dev/null 2>&1 || {
  echo "Falta el plugin docker compose."
  exit 1
}

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="/opt/vision-broker"
install -d -m 755 "$DEST"
cp -a "$DIR/api" "$DIR/broker" "$DEST/"
chmod 600 "$DEST/broker/.env"

cd "$DEST/broker"
docker compose up -d --build

CADDYFILE="/etc/caddy/Caddyfile"
if [ ! -f "$CADDYFILE" ]; then
  touch "$CADDYFILE"
fi
if ! grep -q '^broker\.visionplusv2\.duckdns\.org' "$CADDYFILE"; then
  cp "$CADDYFILE" "$CADDYFILE.bak-$(date +%Y%m%d-%H%M%S)"
  cat "$DIR/broker/Caddyfile" >> "$CADDYFILE"
fi
caddy validate --config "$CADDYFILE"
systemctl enable --now caddy
systemctl reload caddy

sleep 2
curl -fsS http://127.0.0.1:8200/healthz
echo
echo "Broker arriba en 127.0.0.1:8200."
echo "1) Apunta broker.visionplusv2.duckdns.org al IP de este VPS."
echo "2) En el VPS viejo agrega el snippet broker/old-caddy-snippet.caddy"
echo "   dentro del bloque visionplusv2.duckdns.org y recarga Caddy."
