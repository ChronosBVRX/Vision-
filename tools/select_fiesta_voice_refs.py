#!/usr/bin/env python3
"""Elige una referencia de voz por presentador a partir del casting Qwen.

El VoiceDesign produce 5 tomas por momento y, de vez en cuando, cambia el
registro. Para que cada juego tenga UNA voz consistente se elige la toma más
cercana a la mediana del presentador (duración, f0 y energía) y se guarda como
referencia WAV + transcripción. Después, `generate_fiesta_voice_clone.py` clona
esa referencia para las 170 líneas.

  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" tools/select_fiesta_voice_refs.py
"""

from __future__ import annotations

import json
import math
import os
import subprocess
import sys
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
REPORT = ROOT / "docs/assets/games/v3/voices/qwen_report.json"
VOICES = ROOT / "docs/assets/games/v3/voices"
REFS = VOICES / "refs"
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")


def measure(path: Path) -> dict:
    import librosa
    import numpy as np

    y, sr = librosa.load(str(path), sr=16000, mono=True)
    f0, _, _ = librosa.pyin(
        y, fmin=65, fmax=450, sr=16000, frame_length=1024, hop_length=160,
    )
    voiced = f0[np.isfinite(f0)]
    return {
        "duration": round(len(y) / sr, 2),
        "f0": round(float(np.median(voiced)), 2) if len(voiced) else 0.0,
        "rms": round(float(np.sqrt(np.mean(y ** 2))), 5),
    }


TARGET_F0 = {
    "nora": 220.0,
    "chisme": 120.0,
    "tictac": 132.0,
    "maquina": 200.0,
    "critico": 170.0,
    "tasador": 106.0,
}


def main() -> int:
    if not REPORT.is_file():
        print(f"Falta el reporte: {REPORT}", file=sys.stderr)
        return 2
    payload = json.loads(REPORT.read_text(encoding="utf-8"))
    items = payload.get("items", [])
    by_host: dict[str, list[dict]] = {}
    for item in items:
        # Para elegir referencia basta con las tomas de presentación; medir las
        # 170 con pyin tarda minutos y no aporta precisión.
        if item.get("moment") not in ("lobby", "start"):
            continue
        path = OUT / item["file"]
        if not path.is_file():
            continue
        item.update(measure(path))
        by_host.setdefault(item["voice"], []).append(item)
    REFS.mkdir(parents=True, exist_ok=True)
    mapping = {}
    for host, rows in sorted(by_host.items()):
        f0s = sorted(row["f0"] for row in rows if row["f0"] > 0)
        rmss = sorted(row["rms"] for row in rows if row["rms"] > 0)
        if not f0s or not rmss:
            print(f"[skip] {host}: sin voz medible")
            continue
        f0_target = TARGET_F0.get(host, f0s[len(f0s) // 2])
        rms_target = rmss[len(rmss) // 2]
        print(
            f"{host:8} f0 lobby/start: "
            f"min={f0s[0]:.0f} med={f0s[len(f0s)//2]:.0f} max={f0s[-1]:.0f} "
            f"→ objetivo {f0_target:.0f}"
        )
        candidates = [row for row in rows if row["moment"] in ("lobby", "start")]
        if not candidates:
            candidates = rows
        # Preferimos una toma de presentación, de 4 a 8 s, cerca de la mediana.
        def score(row: dict) -> float:
            duration_penalty = abs(row["duration"] - 5.8) * 0.7
            if row["duration"] < 3.5 or row["duration"] > 9.0:
                duration_penalty += 8
            f0_penalty = abs(12 * math.log2(row["f0"] / f0_target)) if row["f0"] > 0 else 20
            rms_penalty = abs(row["rms"] - rms_target) / max(rms_target, 1e-5) * 2
            return duration_penalty + f0_penalty + rms_penalty

        best = min(candidates, key=score)
        src = OUT / best["file"]
        wav = REFS / f"{host}.wav"
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(src), "-ac", "1", "-ar", "24000", str(wav),
        ], check=True)
        (REFS / f"{host}.txt").write_text(best["text"], encoding="utf-8")
        mapping[host] = {
            "ref_audio": str(wav),
            "ref_text": best["text"],
            "source": best["file"],
            "f0": best["f0"],
            "duration": best["duration"],
        }
        print(
            f"{host:8} ← {best['file']:48} "
            f"f0={best['f0']:.1f} dur={best['duration']:.2f}s"
        )
    (VOICES / "refs.json").write_text(
        json.dumps(mapping, ensure_ascii=False, indent=2), encoding="utf-8",
    )
    print(f"referencias: {VOICES / 'refs.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
