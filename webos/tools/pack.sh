#!/usr/bin/env bash
# Empaqueta Vision+ webOS como .ipk (formato webOS: ar + control.tar.gz + data.tar.gz).
# Uso: ./webos/tools/pack.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WEBOS="$ROOT/webos"
APP_ID="com.chronosbvrx.visionplus.webos"
SERVICE_ID="com.chronosbvrx.visionplus.webos.service"
VERSION="$(node -e "console.log(require('$WEBOS/app/appinfo.json').version)")"
BUILD="$WEBOS/build"
STAGE="$BUILD/stage"
OUT="$BUILD/visionplus-webos_${VERSION}_all.ipk"

rm -rf "$BUILD"
mkdir -p "$STAGE/control" "$STAGE/data"
mkdir -p "$STAGE/data/usr/palm/applications/$APP_ID"
mkdir -p "$STAGE/data/usr/palm/services/$SERVICE_ID"
mkdir -p "$STAGE/data/usr/palm/packages/$APP_ID"

cp -a "$WEBOS/app/." "$STAGE/data/usr/palm/applications/$APP_ID/"
cp -a "$WEBOS/service/." "$STAGE/data/usr/palm/services/$SERVICE_ID/"
chmod 755 "$STAGE/data/usr/palm/services/$SERVICE_ID/ffmpeg" 2>/dev/null || true

cat > "$STAGE/data/usr/palm/packages/$APP_ID/packageinfo.json" <<EOF
{
  "id": "$APP_ID",
  "version": "$VERSION",
  "app": "$APP_ID",
  "services": [
    "$SERVICE_ID"
  ]
}
EOF

INSTALLED_KB="$(du -sk "$STAGE/data" | cut -f1)"
cat > "$STAGE/control/control" <<EOF
Package: $APP_ID
Version: $VERSION
Section: misc
Priority: optional
Architecture: all
Installed-Size: $INSTALLED_KB
Maintainer: ChronosBVRX <noreply@chronosbvrx.local>
Description: Vision+ for webOS (catalog, live TV and local playback proxy)
webOS-Package-Format-Version: 2
webOS-Packager-Version: x.y.x
EOF

printf '2.0\n' > "$STAGE/debian-binary"
tar --numeric-owner --owner=0 --group=0 -czf "$STAGE/control.tar.gz" -C "$STAGE/control" control
tar --numeric-owner --owner=0 --group=0 -czf "$STAGE/data.tar.gz" -C "$STAGE/data" usr

cd "$STAGE"
ar rc "$OUT" debian-binary control.tar.gz data.tar.gz
cd "$ROOT"

echo "IPK: $OUT"
ls -lh "$OUT"
ar t "$OUT"
