#!/usr/bin/env bash
# Deja Vision+ Party listo desde un clon limpio (Linux/macOS/WSL).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OPL_URL="https://github.com/Hartwich/Open-Party-Lab.git"
PIN="9d94b502a334afdd1a6bcb532b7cda7595d2bca9"
OPL="$ROOT/party/platform/open-party-lab"
PATCHES="$ROOT/party/platform/patches"
GAMES="$ROOT/party/games"
CONTENT="$ROOT/party/content"

FORCE=0
SKIP_INSTALL=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --force) FORCE=1 ;;
    --skip-install) SKIP_INSTALL=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help)
      echo "Uso: $0 [--force] [--skip-install] [--skip-build]"
      exit 0
      ;;
    *) echo "Argumento desconocido: $arg" >&2; exit 2 ;;
  esac
done

echo "== Vision+ Party: bootstrap =="
echo "Repo: $ROOT"

if [ ! -d "$OPL/.git" ]; then
  if [ -d "$OPL" ] && [ -n "$(ls -A "$OPL")" ]; then
    echo "La carpeta $OPL existe y no está vacía. Muévela o bórrala antes de continuar." >&2
    exit 1
  fi
  echo "== Clonando Open Party Lab =="
  git clone "$OPL_URL" "$OPL"
fi

if [ -n "$(git -C "$OPL" status --porcelain)" ]; then
  if [ "$FORCE" != "1" ]; then
    echo "El submódulo tiene cambios locales. Usa --force solo si quieres descartarlos." >&2
    exit 1
  fi
  echo "== Descartando cambios locales del submódulo (--force) =="
  git -C "$OPL" checkout --force "$PIN"
  git -C "$OPL" clean -fdx
else
  echo "== Fijando Open Party Lab en $PIN =="
  git -C "$OPL" checkout "$PIN"
fi

echo "== Aplicando parches de Vision+ =="
cp -R "$PATCHES/." "$OPL/"

echo "== Portadas e i18n =="
HOST_PUBLIC="$OPL/apps/host/public"
CONTROLLER_PUBLIC="$OPL/apps/controller/public"
mkdir -p "$HOST_PUBLIC/party-covers" "$HOST_PUBLIC/party-i18n" "$CONTROLLER_PUBLIC/party-i18n"
cp "$ROOT/party/assets/covers/"*.webp "$HOST_PUBLIC/party-covers/"
cp "$CONTENT/i18n/es.json" "$HOST_PUBLIC/party-i18n/es.json"
cp "$CONTENT/i18n/es.json" "$CONTROLLER_PUBLIC/party-i18n/es.json"

echo "== Copiando los 18 juegos a local-games =="
mkdir -p "$OPL/local-games"
count=0
while IFS= read -r id; do
  [ -z "$id" ] && continue
  if [ ! -d "$GAMES/$id" ]; then
    echo "Falta el juego $id en party/games" >&2
    exit 1
  fi
  rm -rf "$OPL/local-games/$id"
  cp -R "$GAMES/$id" "$OPL/local-games/$id"
  count=$((count + 1))
done < <(node -e "console.log(require(process.argv[1]).map(g => g.id).join('\n'))" "$PATCHES/config/known-games.json")
echo "   juegos copiados: $count"

echo "== Audio de la narradora =="
audio_count=0
for dir in "$CONTENT/narrator/audio"/*/; do
  [ -d "$dir" ] || continue
  game="$(basename "$dir")"
  mkdir -p "$HOST_PUBLIC/party-audio/$game"
  cp "$dir"*.m4a "$HOST_PUBLIC/party-audio/$game/"
  audio_count=$((audio_count + 1))
done
echo "   paquetes de audio: $audio_count"

if [ "$SKIP_INSTALL" != "1" ]; then
  echo "== npm install =="
  (cd "$OPL" && npm install --no-audit --no-fund)
fi

if [ "$SKIP_BUILD" != "1" ]; then
  echo "== Build web (host, controlador y servidor) =="
  (cd "$OPL" && npm run build:hosted)

  echo "== Empaquetando el servidor =="
  BUNDLER="$OPL/.vision-bundle-server.cjs"
  cat > "$BUNDLER" <<'EOF'
const esbuild = require("esbuild");
esbuild.buildSync({
  entryPoints: ["apps/server/src/main.ts"],
  bundle: true,
  platform: "node",
  format: "esm",
  target: "node22",
  outfile: "artifacts/hosted/party-server.mjs",
  banner: {
    js: 'import { createRequire } from "node:module"; const require = createRequire(import.meta.url);'
  }
});
EOF
  (cd "$OPL" && node .vision-bundle-server.cjs)
  rm -f "$BUNDLER"
fi

echo ""
echo "Listo. Artefactos en party/platform/open-party-lab/artifacts/hosted"
echo "Para desarrollo local: npm run dev:server / dev:host / dev:controller"
