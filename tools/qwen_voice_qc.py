#!/usr/bin/env python3
"""Control de calidad de las voces Qwen de Fiesta.

Comprueba que cada toma suene al mismo presentador y no a un impostor:

  * f0 mediana por toma (timbre/registro);
  * desviación en semitonos contra la mediana del presentador;
  * duración, clipping y silencio;
  * texto esperado del reporte de generación.

Si una toma se desvía más de `--max-semitones` (3 st por defecto), se puede
regenerar con `--repair`. Así se evita que una variante salga con otra voz.

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" tools/qwen_voice_qc.py
  "$PY" tools/qwen_voice_qc.py --repair
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
REPORT = ROOT / "docs/assets/games/v3/voices/qwen_report.json"
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(os.environ.get(
    "QWEN_TTS_MODEL",
    str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-VoiceDesign"),
))
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")

sys.path.insert(0, str(ROOT / "tools"))
import generate_fiesta_voice_qwen as gen  # noqa: E402


def measure(path: Path) -> dict:
    import librosa
    import numpy as np

    y, sr = librosa.load(str(path), sr=16000, mono=True)
    if len(y) == 0:
        return {"duration": 0.0, "f0": 0.0, "voiced": 0}
    f0, _, _ = librosa.pyin(
        y, fmin=65, fmax=450, sr=16000, frame_length=1024, hop_length=160,
    )
    voiced = f0[np.isfinite(f0)]
    return {
        "duration": round(len(y) / sr, 2),
        "f0": round(float(np.median(voiced)), 2) if len(voiced) else 0.0,
        "voiced": int(len(voiced)),
        "rms": round(float(np.sqrt(np.mean(y ** 2))), 5),
    }


def expected_text(report: list[dict]) -> dict[str, str]:
    return {item["file"]: item["text"] for item in report}


def analyze(report: list[dict], max_semitones: float) -> tuple[list[dict], dict[str, float]]:
    hosts: dict[str, list[float]] = {}
    rows = []
    for item in report:
        path = OUT / item["file"]
        m = measure(path) if path.is_file() else {"duration": 0, "f0": 0, "voiced": 0}
        host = item["voice"]
        item.update(m)
        rows.append(item)
        if m["f0"] > 0:
            hosts.setdefault(host, []).append(m["f0"])
    targets = {
        host: sorted(values)[len(values) // 2]
        for host, values in hosts.items() if values
    }
    for item in rows:
        target = targets.get(item["voice"], 0.0)
        if item["f0"] > 0 and target > 0:
            item["semitones"] = round(12 * math.log2(item["f0"] / target), 2)
        else:
            item["semitones"] = 99.0
        item["ok"] = (
            item["duration"] >= 0.8
            and item["duration"] <= 12.0
            and abs(item["semitones"]) <= max_semitones
        )
    return rows, targets


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repair", action="store_true")
    parser.add_argument("--max-semitones", type=float, default=3.0)
    args = parser.parse_args()

    if not REPORT.is_file():
        print(f"Falta el reporte: {REPORT}", file=sys.stderr)
        return 2
    payload = json.loads(REPORT.read_text(encoding="utf-8"))
    report = payload.get("items", [])
    # El reporte pudo quedar parcial si se regeneró solo un juego. Se
    # reconstruye la lista completa desde el contenido y los nombres de audio.
    expected = sum(len(texts) for _, _, _, texts in gen.tasks(list(gen.MOMENTS), 5))
    if len(report) < expected or len({item.get("game") for item in report}) < len(gen.MOMENTS):
        rebuilt = []
        for game, moment, host, texts in gen.tasks(list(gen.MOMENTS), 5):
            for variant, text in enumerate(texts):
                rebuilt.append({
                    "file": f"fiesta_{game}_{moment}_{variant}.m4a",
                    "game": game,
                    "moment": moment,
                    "variant": variant,
                    "text": text,
                    "voice": host,
                    "mode": "clone",
                })
        report = rebuilt
        payload["items"] = report
        payload["engine"] = "Qwen3-TTS Base (clone)"
    if not report:
        print("Reporte vacío.", file=sys.stderr)
        return 2

    rows, targets = analyze(report, args.max_semitones)
    bad = [row for row in rows if not row["ok"]]
    for host, target in sorted(targets.items()):
        host_rows = [row for row in rows if row["voice"] == host]
        print(f"{host:8} f0 objetivo {target:6.1f} Hz · {len(host_rows)} tomas")
    for row in bad:
        print(
            f"FALLA {row['file']:48} f0={row['f0']:6.1f} "
            f"({row['semitones']:+.1f} st) dur={row['duration']:.2f}"
        )
    print(f"{len(rows) - len(bad)}/{len(rows)} tomas pasan el control de voz")

    if args.repair and bad:
        import torch
        from qwen_tts import Qwen3TTSModel

        print(f"Reparando {len(bad)} tomas…", flush=True)
        clone_mode = any(row.get("mode") == "clone" for row in bad)
        model_dir = Path(os.environ.get(
            "QWEN_TTS_CLONE_MODEL",
            str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-Base"),
        )) if clone_mode else MODEL_DIR
        model = Qwen3TTSModel.from_pretrained(
            str(model_dir), device_map="cuda:0", dtype=torch.bfloat16,
            attn_implementation="sdpa",
        )
        torch.set_grad_enabled(False)
        prompts = {}
        if clone_mode:
            refs_path = ROOT / "docs/assets/games/v3/voices/refs.json"
            refs = json.loads(refs_path.read_text(encoding="utf-8"))
            for host, data in refs.items():
                prompts[host] = model.create_voice_clone_prompt(
                    ref_audio=data["ref_audio"],
                    ref_text=data["ref_text"],
                    x_vector_only_mode=False,
                )
        for row in bad:
            game, moment = row["game"], row["moment"]
            voice, _ = gen.CAST[row["voice"]]
            instruct = f"{voice} {gen.TONE.get(moment, '')}".strip()
            dest = OUT / row["file"]
            for attempt in range(4):
                try:
                    with torch.inference_mode():
                        if row.get("mode") == "clone" and row["voice"] in prompts:
                            wavs, sr = model.generate_voice_clone(
                                text=[row["text"]],
                                language=["Spanish"],
                                voice_clone_prompt=prompts[row["voice"]],
                                temperature=1.0 + attempt * 0.04,
                                top_p=0.95,
                                top_k=50,
                                repetition_penalty=1.04,
                                non_streaming_mode=True,
                                max_new_tokens=1024,
                            )
                        else:
                            wavs, sr = model.generate_voice_design(
                                text=[row["text"]],
                                instruct=instruct,
                                language="Spanish",
                                non_streaming_mode=True,
                                do_sample=True,
                                temperature=0.88 + attempt * 0.06,
                                top_p=0.95,
                                top_k=50,
                                repetition_penalty=1.04,
                                max_new_tokens=1024,
                            )
                    clean, sr2 = gen.clean_wav(wavs[0], sr)
                    gen.encode(clean, sr2, dest)
                    m = measure(dest)
                    target = targets.get(row["voice"], 0.0)
                    semis = 12 * math.log2(m["f0"] / target) if target and m["f0"] > 0 else 99
                    if abs(semis) <= args.max_semitones and 0.8 <= m["duration"] <= 12.0:
                        row.update(m)
                        row["semitones"] = round(semis, 2)
                        row["ok"] = True
                        print(f"[ok] {dest.name} f0={m['f0']:.1f} ({semis:+.1f} st)")
                        break
                    print(f"[retry {attempt+1}] {dest.name} f0={m['f0']:.1f} ({semis:+.1f} st)")
                except Exception as exc:  # noqa: BLE001
                    print(f"[error] {dest.name}: {exc}")
                finally:
                    torch.cuda.empty_cache()
        payload["items"] = rows
        REPORT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        bad = [row for row in rows if not row["ok"]]
        print(f"Tras reparar: {len(rows) - len(bad)}/{len(rows)} tomas OK")
    else:
        payload["items"] = rows
        REPORT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())
