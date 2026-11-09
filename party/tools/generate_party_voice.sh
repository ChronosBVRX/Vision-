#!/usr/bin/env bash
set -euo pipefail

# Genera la voz irreverente de un juego de Vision+ Party con Qwen3-TTS
# VoiceDesign. No usa Dalia: crea una voz original de comediante mexicana.
#
# Uso:
#   ./party/tools/generate_party_voice.sh banterbolt
#   ./party/tools/generate_party_voice.sh you-got-it --variants 10

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GAME="${1:?Uso: generate_party_voice.sh <juego> [args]}"
shift || true

PY="${QWEN_TTS_PY:-/media/chronos/Disco Duro/vision-tts/venv/bin/python}"

exec "$PY" "$ROOT/party/tools/generate_party_voice_design.py" \
  --game "$GAME" \
  --variants 10 \
  "$@"
