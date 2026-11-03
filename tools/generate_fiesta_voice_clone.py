#!/usr/bin/env python3
"""Consolida las voces de Fiesta clonando UNA referencia por presentador.

`generate_fiesta_voice_qwen.py` genera el casting con VoiceDesign, pero cada
toma puede cambiar ligeramente de registro. Este pase toma la referencia
elegida por `select_fiesta_voice_refs.py` y usa Qwen3-TTS Base para clonar el
mismo timbre en las 170 líneas. Así cada juego tiene una voz consistente, no
cinco impostores.

  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" tools/generate_fiesta_voice_clone.py --force
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
VOICES = ROOT / "docs/assets/games/v3/voices"
REFS = VOICES / "refs.json"
REPORT = VOICES / "qwen_report.json"
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(os.environ.get(
    "QWEN_TTS_MODEL",
    str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-Base"),
))
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")

sys.path.insert(0, str(ROOT / "tools"))
import generate_fiesta_voice_qwen as gen  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    if not REFS.is_file():
        print(f"Faltan las referencias: {REFS}", file=sys.stderr)
        return 2
    refs = json.loads(REFS.read_text(encoding="utf-8"))
    games = [args.only] if args.only else list(gen.MOMENTS)
    jobs = gen.tasks(games, 5)
    print(f"Qwen3-TTS Base · clonación · {len(jobs)} momentos")

    import torch
    from qwen_tts import Qwen3TTSModel

    model = Qwen3TTSModel.from_pretrained(
        str(MODEL_DIR), device_map="cuda:0", dtype=torch.bfloat16,
        attn_implementation="sdpa",
    )
    torch.set_grad_enabled(False)
    prompts = {}
    for host, data in refs.items():
        prompts[host] = model.create_voice_clone_prompt(
            ref_audio=data["ref_audio"],
            ref_text=data["ref_text"],
            x_vector_only_mode=False,
        )
    report = []
    for index, (game, moment, host, texts) in enumerate(jobs, 1):
        dests = [OUT / f"fiesta_{game}_{moment}_{v}.m4a" for v in range(len(texts))]
        if not args.force and all(path.is_file() for path in dests):
            print(f"[{index}/{len(jobs)}] existe {game}_{moment}")
            continue
        begin = time.time()
        temp = 1.02
        if moment in ("reveal", "podium"):
            temp = 1.10
        elif moment in ("write", "guess", "vote", "pitch"):
            temp = 0.98
        try:
            with torch.inference_mode():
                wavs, sr = model.generate_voice_clone(
                    text=texts,
                    language=["Spanish"] * len(texts),
                    voice_clone_prompt=prompts[host],
                    temperature=temp,
                    top_p=0.95,
                    top_k=50,
                    repetition_penalty=1.04,
                    non_streaming_mode=True,
                    max_new_tokens=1024,
                )
        except Exception as exc:  # noqa: BLE001
            print(f"AVISO {game}_{moment}: {exc}; una por una", flush=True)
            torch.cuda.empty_cache()
            wavs, sr = [], None
            for text in texts:
                with torch.inference_mode():
                    one, sr = model.generate_voice_clone(
                        text=[text],
                        language=["Spanish"],
                        voice_clone_prompt=prompts[host],
                        temperature=temp,
                        top_p=0.95,
                        top_k=50,
                        repetition_penalty=1.04,
                        non_streaming_mode=True,
                        max_new_tokens=1024,
                    )
                wavs.append(one[0])
        if not wavs or sr is None:
            print(f"ERROR {game}_{moment}: sin audio", file=sys.stderr)
            continue
        for variant, (text, wav) in enumerate(zip(texts, wavs)):
            clean, sr2 = gen.clean_wav(wav, sr)
            dest = dests[variant]
            gen.encode(clean, sr2, dest)
            report.append({
                "file": dest.name,
                "game": game,
                "moment": moment,
                "variant": variant,
                "text": text,
                "voice": host,
                "mode": "clone",
                "ref": refs[host]["source"],
                "duration": round(len(clean) / sr2, 2),
                "bytes": dest.stat().st_size,
            })
        torch.cuda.empty_cache()
        old = OUT / f"fiesta_{game}_{moment}.m4a"
        if old.exists():
            old.unlink()
        print(
            f"[{index}/{len(jobs)}] {game}_{moment} · {len(wavs)} tomas · "
            f"{time.time() - begin:.1f}s",
            flush=True,
        )
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(
        json.dumps({"engine": "Qwen3-TTS-12Hz-1.7B-Base (clone)", "items": report},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"reporte: {REPORT}")
    return gen.audit()


if __name__ == "__main__":
    raise SystemExit(main())
