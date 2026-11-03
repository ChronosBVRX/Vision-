#!/usr/bin/env bash
# Sirve los APK de beta/ a la tele en la red local (http://IP:8101/v.apk).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/beta"
exec python3 -m http.server 8101 --bind 0.0.0.0
