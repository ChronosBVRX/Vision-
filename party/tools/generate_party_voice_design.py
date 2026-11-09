#!/usr/bin/env python3
"""Voz irreverente de Vision+ Party con Qwen3-TTS VoiceDesign.

Genera varias tomas por momento para rotar frases sin repetir la misma voz
ni el mismo chiste. No usa la referencia Dalia: crea una voz original de
comediante mexicana con actitud pícara.

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" party/tools/generate_party_voice_design.py --game banterbolt --variants 10 --force
  "$PY" party/tools/generate_party_voice_design.py --game banterbolt --pilot
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

ROOT = Path(__file__).resolve().parents[2]
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(os.environ.get("QWEN_TTS_MODEL", str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-VoiceDesign")))
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")

# La voz de la fiesta: no es locutora seria ni conductora de noticiero.
VOICE = (
    "Voz femenina mexicana de 29 años, comediante de stand-up y narradora de fiesta. "
    "Acento chilango de la Ciudad de México, español mexicano cerrado, entonación "
    "cantadita y pronunciación de barrio. Timbre cálido, ligeramente ronco, sonrisa "
    "pícara y mucha calle. Habla rápido, con ironía y complicidad, como la amiga que "
    "se burla de todos pero te quiere. Nada de acento español, argentino, colombiano "
    "ni inglés. Nada de tono infantil, nada de locutora de supermercado, nada de voz "
    "seria de noticiero."
)

TONE: dict[str, str] = {
    "welcome": "Bienvenida de fiesta: burlona, con risa contenida y ganas de desmadre.",
    "write": "Con prisa y conspiración: incita a escribir algo peligroso y gracioso.",
    "vote": "Sarcástica y traicionera: disfruta que voten con el hígado.",
    "reveal": "Suspenso teatral: pausa antes del golpe y luego disfruta el caos.",
    "zap": "Grito de gol exagerado: sorpresa, burla y celebración.",
    "jinx": "Burla cariñosa: dos genios con la misma idea, cero puntos.",
    "scoreboard": "Repaso burlón de la tabla, como comentarista de barrio.",
    "finale": "Pistoletazo de salida: energía máxima, última ronda.",
    "podium": "Cierre de gala: celebración exagerada y despedida con cariño.",
    "clue": "Misteriosa y pícara, como vidente de barrio que sí sabe.",
    "guess": "Urgente y juguetona: muevan el dial, no sean cobardes.",
    "steal": "Chismosa y conspiradora: el otro equipo puede robar.",
    "huddle": "Susurro de consejo secreto: cuidado con quien confías.",
    "nominate": "Intriga política burlona: ahora sí se destapan.",
    "legislature": "Solemne pero ridícula: el destino del país en sus manos.",
    "power": "Dramática y poderosa: alguien va a pagar.",
    "game_over": "Revelación de telenovela: todos los roles al descubierto.",
}


def load_tasks(lines_path: Path, variants: int, pilot: bool) -> list[tuple[str, list[str]]]:
    data = json.loads(lines_path.read_text(encoding="utf-8"))
    tasks = []
    for item in data:
        texts = [str(text) for text in item.get("variants", []) if str(text).strip()]
        if not texts:
            continue
        if pilot:
            texts = texts[:1]
        tasks.append((str(item["id"]), texts[:variants]))
    return tasks


def encode(wav, sr, dest: Path) -> None:
    import soundfile as sf

    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="party_qwen_") as tmp:
        wav_path = Path(tmp) / "voice.wav"
        sf.write(wav_path, wav, sr)
        subprocess.run(
            [
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(wav_path),
                "-af", "loudnorm=I=-16:TP=-2.5:LRA=11,alimiter=limit=0.88",
                "-ac", "1", "-ar", "48000", "-c:a", "aac", "-b:a", "64k",
                str(dest),
            ],
            check=True,
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", required=True)
    parser.add_argument("--lines", type=Path)
    parser.add_argument("--out", type=Path)
    parser.add_argument("--variants", type=int, default=10)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--pilot", action="store_true")
    parser.add_argument("--audit", action="store_true")
    args = parser.parse_args()

    lines_path = args.lines or (ROOT / "party" / "content" / "narrator" / f"{args.game}_lines.json")
    out_dir = args.out or (ROOT / "party" / "games" / args.game / "public" / "host" / args.game / "narrator")
    if not lines_path.exists():
        print(f"Falta {lines_path}", file=sys.stderr)
        return 1

    tasks = load_tasks(lines_path, max(1, min(10, args.variants)), args.pilot)
    if args.audit:
        bad = 0
        total = 0
        for moment, texts in tasks:
            for index, _ in enumerate(texts):
                total += 1
                path = out_dir / f"narrator_{moment}_{index}.m4a"
                if not path.exists():
                    print(f"FALTA {path.name}")
                    bad += 1
                    continue
                duration = float(subprocess.check_output([
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "csv=p=0", str(path),
                ]).strip())
                if not 1.0 <= duration <= 14.0:
                    print(f"FUERA DE RANGO {path.name} {duration:.2f}s")
                    bad += 1
        print(f"auditoría: {total - bad}/{total} correctos")
        return 1 if bad else 0

    if not MODEL_DIR.exists():
        print(f"Falta el modelo en {MODEL_DIR}", file=sys.stderr)
        return 1

    import torch
    from qwen_tts import Qwen3TTSModel

    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        str(MODEL_DIR), device_map="cuda:0", dtype=torch.bfloat16, attn_implementation="sdpa",
    )
    print(f"modelo cargado en {time.time() - started:.1f}s", flush=True)
    torch.set_grad_enabled(False)
    out_dir.mkdir(parents=True, exist_ok=True)
    report = []

    for index, (moment, texts) in enumerate(tasks, 1):
        dests = [out_dir / f"narrator_{moment}_{variant}.m4a" for variant in range(len(texts))]
        if not args.force and all(path.exists() for path in dests):
            print(f"[{index}/{len(tasks)}] existe {moment}", flush=True)
            continue
        instruct = f"{VOICE} {TONE.get(moment, '')}".strip()
        begin = time.time()
        try:
            with torch.inference_mode():
                wavs, sr = model.generate_voice_design(
                    text=texts,
                    instruct=instruct,
                    language="Spanish",
                    non_streaming_mode=True,
                    do_sample=True,
                    temperature=0.94,
                    top_p=0.95,
                    top_k=50,
                    repetition_penalty=1.04,
                    max_new_tokens=1024,
                )
        except Exception as exc:  # noqa: BLE001
            print(f"AVISO {moment}: {exc}; generando una por una", flush=True)
            torch.cuda.empty_cache()
            wavs, sr = [], None
            for text in texts:
                with torch.inference_mode():
                    one, sr = model.generate_voice_design(
                        text=text,
                        instruct=instruct,
                        language="Spanish",
                        non_streaming_mode=True,
                        do_sample=True,
                        temperature=0.94,
                        top_p=0.95,
                        top_k=50,
                        repetition_penalty=1.04,
                        max_new_tokens=1024,
                    )
                wavs.append(one[0] if isinstance(one, list) else one)

        for variant, (text, wav) in enumerate(zip(texts, wavs)):
            dest = dests[variant]
            encode(wav, sr, dest)
            duration = float(subprocess.check_output([
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "csv=p=0", str(dest),
            ]).strip())
            report.append({"file": dest.name, "moment": moment, "variant": variant, "text": text, "duration": round(duration, 2)})
            print(f"[{index}/{len(tasks)}] {dest.name} · {duration:.2f}s", flush=True)

    (out_dir / "narrator_report.json").write_text(
        json.dumps({"engine": "Qwen3-TTS-12Hz-1.7B-VoiceDesign", "voice": "vision-party-irreverent", "items": report}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"listo: {len(report)} archivos en {out_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
