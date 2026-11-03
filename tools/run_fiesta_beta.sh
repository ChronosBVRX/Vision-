#!/usr/bin/env bash
# Levanta el servidor local de la beta Vision+ Fiesta 3.0.
# La tele y los teléfonos deben estar en la misma red que este PC.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/server/api"
exec "$ROOT/.venv/bin/uvicorn" fiesta_beta_server:app --host 0.0.0.0 --port 8100
