# Despliegue de Vision+ Party

El host, el controlador y el servidor se compilan en `artifacts/hosted/` con:

```bash
cd platform/open-party-lab
# Copia el diccionario y los 630 audios de narradora (10 variantes por momento).
mkdir -p apps/host/public/party-i18n apps/controller/public/party-i18n
cp ../../content/i18n/es.json apps/host/public/party-i18n/es.json
cp ../../content/i18n/es.json apps/controller/public/party-i18n/es.json
# Los 10 juegos base + selección viven en party-audio.
OPL_ROOT="$PWD" ../../tools/sync_party_audio.sh
# Banterbolt, Merk y ¿Le atinaste? traen su narración dentro de local-games.
npm run games:sync-local
npm run build:hosted
node_modules/.bin/esbuild apps/server/src/main.ts \
  --bundle --platform=node --format=esm --target=node22 \
  --outfile=artifacts/hosted/party-server.mjs \
  --banner:js='import { createRequire } from "node:module"; const require = createRequire(import.meta.url);'
```

Se copian `party-server.mjs`, `web/` y este `Dockerfile` al VPS en
`/opt/vision-party/`, se construye la imagen y se arranca el contenedor:

```bash
sudo docker build -t vision-party:local /opt/vision-party
sudo docker rm -f vision-party 2>/dev/null || true
sudo docker run -d --restart unless-stopped --name vision-party \
  -p 127.0.0.1:3100:3000 \
  -e RENDER=true \
  -e PUBLIC_CONTROLLER_ORIGIN=https://party.visionplusv2.duckdns.org/controller/ \
  vision-party:local
```

Caddy publica `party.visionplusv2.duckdns.org` contra `127.0.0.1:3100`.
Los teléfonos entran por el QR al controlador; la TV abre
`/host?game=<id>&platform=android`, crea la sala automáticamente y la deja
bloqueada a ese juego.
