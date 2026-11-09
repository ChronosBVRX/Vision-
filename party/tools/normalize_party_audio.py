#!/usr/bin/env python3
"""Normaliza loudness/picos de los audios de narración que fallan la auditoría.

No regenera texto ni voz. Aplica ganancia y un limitador a los archivos que
quedaron fuera de rango. Es necesario porque `loudnorm` no puede subir un clip
corto si su pico ya está cerca del techo: bajaría el volumen en vez de
comprimirlo.

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" party/tools/normalize_party_audio.py --audit /tmp/opencode/audio-audit.json
  "$PY" party/tools/normalize_party_audio.py --game banterbolt
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET_I = -16.0
TARGET_TP = -2.5
LOW = -16.9
HIGH = -15.1
PEAK_LIMIT = -1.1


def measure(path: Path) -> tuple[float, float]:
    proc = subprocess.run([
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-filter_complex", "ebur128=peak=true", "-f", "null", "-",
    ], capture_output=True, text=True)
    text = proc.stderr
    lufs = -70.0
    peak = -70.0
    matches = re.findall(r"I:\s*(-?\d+(?:\.\d+)?)\s*LUFS", text)
    if matches:
        lufs = float(matches[-1])
    matches = re.findall(r"True peak:\s*Peak:\s*(-?\d+(?:\.\d+)?)\s*dBFS", text)
    if matches:
        peak = float(matches[-1])
    return lufs, peak


def apply_gain(path: Path, gain_db: float) -> None:
    # El temporal vive junto al destino para que os.replace funcione también
    # cuando la biblioteca está en otro disco (SSD -> HDD).
    fd, tmp_name = tempfile.mkstemp(prefix=".party_norm_", suffix=".m4a", dir=path.parent)
    os.close(fd)
    dest = Path(tmp_name)
    try:
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(path),
            "-af", f"volume={gain_db:.2f}dB,alimiter=limit=0.75:level=false",
            "-ac", "1", "-ar", "48000", "-c:a", "aac", "-b:a", "64k", str(dest),
        ], check=True)
        os.replace(dest, path)
    finally:
        dest.unlink(missing_ok=True)


def normalize(path: Path) -> tuple[float, float]:
    lufs, peak = measure(path)
    gain = max(-12.0, min(12.0, TARGET_I - lufs))
    best = (lufs, peak)
    for _ in range(3):
        apply_gain(path, gain)
        lufs, peak = measure(path)
        best = (lufs, peak)
        if LOW <= lufs <= HIGH and peak <= PEAK_LIMIT:
            return best
        gain += TARGET_I - lufs
        gain = max(-12.0, min(12.0, gain))
    return best


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path)
    parser.add_argument("--game", default="")
    parser.add_argument("--all", action="store_true")
    args = parser.parse_args()

    paths: list[Path] = []
    if args.audit:
        payload = json.loads(args.audit.read_text(encoding="utf-8"))
        for item in payload.get("items", []):
            if item.get("lufs", 0) < -18.5 or item.get("lufs", 0) > -13.5 or item.get("peak", 0) > -1.0:
                paths.append(ROOT / item["file"])
    elif args.game:
        base = ROOT / "party" / "content" / "narrator" / "audio" / args.game
        if not base.is_dir():
            base = ROOT / "party" / "games" / args.game / "public" / "host" / args.game / "narrator"
        paths = sorted(base.glob("*.m4a"))
    elif args.all:
        paths = sorted((ROOT / "party" / "content" / "narrator" / "audio").rglob("*.m4a"))
        for game in ("banterbolt", "merkissioner", "you-got-it"):
            paths += sorted((ROOT / "party" / "games" / game / "public" / "host" / game / "narrator").glob("*.m4a"))
    else:
        parser.error("Usa --audit, --game o --all")

    print(f"normalizando {len(paths)} archivos", flush=True)
    for index, path in enumerate(paths, 1):
        try:
            lufs, peak = normalize(path)
            label = str(path.relative_to(ROOT)) if str(path).startswith(str(ROOT)) else str(path)
            print(f"[{index}/{len(paths)}] {label} · {lufs:.1f} LUFS · {peak:.1f} dBFS", flush=True)
        except Exception as exc:  # noqa: BLE001
            print(f"ERROR {path}: {exc}", file=sys.stderr, flush=True)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
