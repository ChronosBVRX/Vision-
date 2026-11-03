#!/usr/bin/env python3
"""Voces premium de Vision+ Fiesta con Qwen3-TTS VoiceDesign.

Reemplaza por completo el casting anterior (Piper). Cada juego tiene un
presentador original y cada momento tiene **cinco tomas** con dirección
actoral propia: el mismo texto puede sonar a bienvenida, a burla o a sentencia.

  res/raw/fiesta_<juego>_<momento>_<0-4>.m4a

Motor:
  /media/chronos/Disco Duro/vision-tts/models/Qwen3-TTS-12Hz-1.7B-VoiceDesign

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" tools/generate_fiesta_voice_qwen.py --only ultima_palabra --force
  "$PY" tools/generate_fiesta_voice_qwen.py --force
  "$PY" tools/generate_fiesta_voice_qwen.py --audit
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

ROOT = Path(__file__).resolve().parent.parent
SERVER = ROOT / "server" / "api"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(os.environ.get(
    "QWEN_TTS_MODEL",
    str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-VoiceDesign"),
))
REPORT = ROOT / "docs/assets/games/v3/voices/qwen_report.json"
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")

sys.path.insert(0, str(SERVER))
import fiesta_content as content  # noqa: E402

MOMENTS: dict[str, tuple[str, ...]] = {
    "ultima_palabra": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "vox_populi": ("lobby", "start", "guess", "reveal", "podium"),
    "fuera_de_epoca": ("lobby", "start", "guess", "reveal", "podium"),
    "texto_roto": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "franela_fest": ("lobby", "start", "create", "vote", "reveal", "podium"),
    "el_tasador": ("lobby", "start", "pitch", "vote", "reveal", "podium"),
}

# Voz base: timbre, edad, acento, actitud. Nada de caricatura barata: cada
# presentador suena a alguien que lleva años en televisión y disfruta el caos.
CAST: dict[str, tuple[str, str]] = {
    "nora": (
        "Voz femenina mexicana de 31 años, presentadora de un late-night game show. "
        "Timbre cálido y ligeramente ronco, sonrisa pícara, dicción rápida y elegante. "
        "Suena carismática y un poco cruel, como alguien que disfruta ver a la gente "
        "equivocarse en vivo. Nada de tono infantil ni de locutora de supermercado.",
        "nora",
    ),
    "chisme": (
        "Voz masculina mexicana de 46 años, locutor de radio de chismes y noticiero "
        "popular. Grave, cálida, exagerada, con sonrisa permanente y ritmo de noticiero "
        "de barrio. Suena como el vecino que se sabe todos los secretos y los cuenta con "
        "gusto, sin pedir permiso.",
        "chisme",
    ),
    "tictac": (
        "Voz masculina mexicana de 58 años, profesor de historia y presentador de "
        "documental antiguo. Nasal, pausada, pedante y entusiasta; se emociona con fechas "
        "y datos. Suena a maestro que ama el pasado y no soporta que nadie se equivoque, "
        "pero se divierte corrigiendo.",
        "tictac",
    ),
    "maquina": (
        "Voz andrógina sintética, deadpan, con pequeños glitches y ritmo entrecortado. "
        "Suena como una IA que intenta ser graciosa y no sabe cómo; ligeramente "
        "inquietante, nunca infantil ni tierna. Puede alargar una sílaba como si se "
        "reiniciara.",
        "maquina",
    ),
    "critico": (
        "Voz masculina mexicana de 36 años, crítico de moda snob y dramático. Articulada, "
        "afeminada, con desdén elegante y entonación teatral. Todo le parece una tragedia "
        "estética; habla como si estuviera en una pasarela de París y el resto del mundo "
        "vestiera de tianguis.",
        "critico",
    ),
    "tasador": (
        "Voz masculina mexicana de 62 años, anticuario y subastador teatral. Grave, "
        "misteriosa, ritmo lento, como narrador de subastas de objetos malditos. Le da "
        "importancia a cosas que no la tienen; suena a dinero viejo, polvo y secretos.",
        "tasador",
    ),
}

GAME_HOST = {
    "ultima_palabra": "nora",
    "vox_populi": "chisme",
    "fuera_de_epoca": "tictac",
    "texto_roto": "maquina",
    "franela_fest": "critico",
    "el_tasador": "tasador",
}

# Dirección actoral por momento. Se suma a la voz base, no la reemplaza.
TONE = {
    "lobby": "Tono de bienvenida de show en vivo: sonríe, mira al público, ganas de que empiece.",
    "start": "Arranque energético, como pistoletazo de salida; sube el ritmo y la intensidad.",
    "write": "Rápido, conspirativo, con prisa; incita a escribir algo peligroso y gracioso.",
    "guess": "Urgente, locutor de noticiero, ligeramente chismoso; disfruta el escándalo.",
    "sort": "Profesor emocionado y pedante; saborea los datos y corrige con gusto.",
    "chat": "IA deadpan, pausas raras, pequeños glitches; diversión seca y un poco inquietante.",
    "create": "Crítico de moda dramático; exige arte, se divierte juzgando y sufre por el buen gusto.",
    "remix": "Snob decepcionado pero intrigado; habla como si oliera una tragedia textil.",
    "pitch": "Anticuario teatral y misterioso; alarga las palabras y vende humo con autoridad.",
    "invest": "Subastero acelerando la puja: apremiante, con malicia y gusto por el dinero.",
    "vote": "Sarcástico, incita a la traición y a votar con el hígado; se relame.",
    "reveal": "Suspenso teatral: pausa antes del golpe y luego disfruta el caos.",
    "podium": "Cierre de gala: celebración exagerada, burla cariñosa y despedida de show.",
}


def tasks(games: list[str], variants: int) -> list[tuple[str, str, str, list[str]]]:
    out = []
    for game in games:
        host = GAME_HOST[game]
        for moment in MOMENTS[game]:
            lines = list(content.host_lines(game, moment))
            if not lines:
                continue
            selected = [lines[i % len(lines)] for i in range(variants)]
            out.append((game, moment, host, selected))
    return out


def clean_wav(wav, sr):
    import librosa
    import numpy as np

    y = np.asarray(wav, dtype=np.float32).squeeze()
    y = librosa.effects.trim(y, top_db=38)[0]
    lead = np.zeros(int(0.18 * sr), dtype=y.dtype)
    tail = np.zeros(int(0.06 * sr), dtype=y.dtype)
    y = np.concatenate([lead, y, tail])
    peak = float(np.max(np.abs(y))) if len(y) else 0.0
    if peak > 0:
        y = y / peak * 0.82
    return y, sr


def encode(wav, sr, dest: Path) -> None:
    import soundfile as sf

    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="fiesta_qwen_") as tmp:
        wav_path = Path(tmp) / "voice.wav"
        sf.write(wav_path, wav, sr)
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(wav_path),
            "-af", "loudnorm=I=-16:TP=-2.5:LRA=11,alimiter=limit=0.88",
            "-ac", "1", "-ar", "48000", "-c:a", "aac", "-b:a", "64k",
            str(dest),
        ], check=True)


def audit() -> int:
    bad = 0
    total = 0
    for game, moments in MOMENTS.items():
        for moment in moments:
            for variant in range(5):
                total += 1
                path = OUT / f"fiesta_{game}_{moment}_{variant}.m4a"
                if not path.is_file():
                    print(f"FALTA {path.name}")
                    bad += 1
                    continue
                dur = float(subprocess.check_output([
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "csv=p=0", str(path),
                ]).strip())
                if not 0.8 <= dur <= 12.0:
                    print(f"FUERA DE RANGO {path.name} {dur:.2f}s")
                    bad += 1
    print(f"auditoría: {total - bad}/{total} correctos")
    return 1 if bad else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--variants", type=int, default=5)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--audit", action="store_true")
    parser.add_argument("--candidates", type=int, default=1)
    args = parser.parse_args()
    if args.audit:
        return audit()
    games = [args.only] if args.only else list(MOMENTS)
    jobs = tasks(games, max(1, min(8, args.variants)))
    print(f"Qwen3-TTS VoiceDesign · {len(jobs)} momentos")
    if not MODEL_DIR.exists():
        print(f"Falta el modelo: {MODEL_DIR}", file=sys.stderr)
        return 1

    import torch
    from qwen_tts import Qwen3TTSModel

    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        str(MODEL_DIR), device_map="cuda:0", dtype=torch.bfloat16,
        attn_implementation="sdpa",
    )
    print(f"modelo cargado en {time.time() - started:.1f}s", flush=True)
    torch.set_grad_enabled(False)
    report: list[dict] = []

    for index, (game, moment, host, texts) in enumerate(jobs, 1):
        dests = [OUT / f"fiesta_{game}_{moment}_{variant}.m4a" for variant in range(len(texts))]
        if not args.force and all(path.is_file() for path in dests):
            print(f"[{index}/{len(jobs)}] existe {game}_{moment}", flush=True)
            continue
        voice, _ = CAST[host]
        instruct = f"{voice} {TONE.get(moment, '')}".strip()
        begin = time.time()
        wavs = sr = None
        try:
            with torch.inference_mode():
                wavs, sr = model.generate_voice_design(
                    text=texts,
                    instruct=instruct,
                    language="Spanish",
                    non_streaming_mode=True,
                    do_sample=True,
                    temperature=0.92,
                    top_p=0.95,
                    top_k=50,
                    repetition_penalty=1.04,
                    max_new_tokens=1024,
                )
        except Exception as exc:  # noqa: BLE001
            print(f"AVISO lote {game}_{moment}: {exc}; se genera una por una", flush=True)
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
                        temperature=0.92,
                        top_p=0.95,
                        top_k=50,
                        repetition_penalty=1.04,
                        max_new_tokens=1024,
                    )
                wavs.append(one[0])
        if not wavs or sr is None:
            print(f"ERROR {game}_{moment}: sin audio", file=sys.stderr)
            continue
        for variant, (text, wav) in enumerate(zip(texts, wavs)):
            clean, sr2 = clean_wav(wav, sr)
            dest = dests[variant]
            encode(clean, sr2, dest)
            duration = len(clean) / sr2
            report.append({
                "file": dest.name,
                "game": game,
                "moment": moment,
                "variant": variant,
                "text": text,
                "voice": host,
                "duration": round(duration, 2),
                "bytes": dest.stat().st_size,
            })
        torch.cuda.empty_cache()
        # Elimina las tomas viejas sin variante (Piper) para no dejar basura.
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
        json.dumps({"engine": "Qwen3-TTS-12Hz-1.7B-VoiceDesign", "items": report},
                   ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"reporte: {REPORT}")
    return audit()


if __name__ == "__main__":
    raise SystemExit(main())
