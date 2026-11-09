#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PY="${QWEN_TTS_PY:-/media/chronos/Disco Duro/vision-tts/venv/bin/python}"
exec "$PY" "$ROOT/party/tools/audit_party_audio.py" --asr "$@"
