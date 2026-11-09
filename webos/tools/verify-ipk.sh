#!/usr/bin/env bash
# Vision+ LG webOS - verifica la estructura y la versión del .ipk generado.
#
#   ./webos/tools/verify-ipk.sh [ruta/al/ipk]
#
# Sin argumento usa el .ipk más reciente de webos/build.

set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IPK="${1:-}"
if [ -z "$IPK" ]; then
  IPK="$(ls -1t "$REPO"/webos/build/visionplus-webos_*_all.ipk 2>/dev/null | head -1 || true)"
fi
if [ -z "$IPK" ] || [ ! -f "$IPK" ]; then
  echo "No encontré el .ipk. Ejecuta primero ./webos/tools/pack.sh" >&2
  exit 1
fi

TMP="$(mktemp -d /tmp/vision-lg-ipk.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT

cd "$TMP"
ar x "$IPK"

[ -f debian-binary ] || { echo "FALLO: falta debian-binary"; exit 1; }
[ "$(cat debian-binary)" = "2.0" ] || { echo "FALLO: debian-binary != 2.0"; exit 1; }
[ -f control.tar.gz ] || { echo "FALLO: falta control.tar.gz"; exit 1; }
[ -f data.tar.gz ] || { echo "FALLO: falta data.tar.gz"; exit 1; }

mkdir control data
tar xzf control.tar.gz -C control
tar xzf data.tar.gz -C data

APP_ID="com.chronosbvrx.visionplus.webos"
SERVICE_ID="com.chronosbvrx.visionplus.webos.service"
APP_DIR="data/usr/palm/applications/$APP_ID"
SERVICE_DIR="data/usr/palm/services/$SERVICE_ID"
PKG_DIR="data/usr/palm/packages/$APP_ID"

for path in "$APP_DIR/appinfo.json" "$SERVICE_DIR/package.json" "$SERVICE_DIR/services.json" "$PKG_DIR/packageinfo.json" "$SERVICE_DIR/ffmpeg"; do
  [ -e "$path" ] || { echo "FALLO: falta $path"; exit 1; }
done
[ -x "$SERVICE_DIR/ffmpeg" ] || { echo "FALLO: ffmpeg no es ejecutable"; exit 1; }

python3 - "$APP_DIR/appinfo.json" "$SERVICE_DIR/package.json" "$PKG_DIR/packageinfo.json" "$SERVICE_DIR/services.json" control/control <<'PY'
import json, re, sys

app_path, service_path, package_path, services_path, control_path = sys.argv[1:]
app = json.load(open(app_path, encoding="utf-8"))
service = json.load(open(service_path, encoding="utf-8"))
package = json.load(open(package_path, encoding="utf-8"))
services = json.load(open(services_path, encoding="utf-8"))
control = open(control_path, encoding="utf-8", errors="replace").read()

app_id = "com.chronosbvrx.visionplus.webos"
service_id = "com.chronosbvrx.visionplus.webos.service"

def fail(message):
    print("FALLO:", message)
    raise SystemExit(1)

if app.get("id") != app_id:
    fail(f"appinfo.id={app.get('id')!r}, esperado {app_id!r}")
if service.get("name") != service_id:
    fail(f"service.name={service.get('name')!r}, esperado {service_id!r}")
if services.get("id") != service_id:
    fail(f"services.json id={services.get('id')!r}, esperado {service_id!r}")
if package.get("id") != app_id:
    fail(f"packageinfo.id={package.get('id')!r}, esperado {app_id!r}")
if package.get("app") != app_id:
    fail(f"packageinfo.app={package.get('app')!r}, esperado {app_id!r}")
if service_id not in (package.get("services") or []):
    fail("packageinfo.services no incluye el servicio")

app_version = str(app.get("version") or "")
service_version = str(service.get("version") or "")
if not app_version:
    fail("appinfo.version vacío")
if app_version != service_version:
    fail(f"versiones distintas: appinfo={app_version} service={service_version}")

match = re.search(r"^Version:\s*(\S+)\s*$", control, re.M)
if not match:
    fail("control sin Version")
if match.group(1) != app_version:
    fail(f"control.Version={match.group(1)} != appinfo.version={app_version}")

if "Architecture: all" not in control:
    fail("control sin 'Architecture: all'")

print(f"OK  {app_id} {app_version}")
print(f"OK  {service_id} {service_version}")
print("OK  app, servicio, packageinfo, control y ffmpeg ejecutable")
PY

echo "IPK: $IPK"
ls -lh "$IPK"
