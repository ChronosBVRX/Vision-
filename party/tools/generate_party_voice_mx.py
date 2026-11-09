#!/usr/bin/env python3
"""Genera la narración de Vision+ Party con dicción mexicana.

Usa Qwen3-TTS Base y clona una referencia de voz mexicana. Cada momento
(`intro`, `playing`, `result`, `scoreboard`, etc.) tiene diez textos distintos
y diez archivos de audio para que la fiesta no repita el mismo chiste.

  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" party/tools/generate_party_voice_mx.py --game pantomime --force
  "$PY" party/tools/generate_party_voice_mx.py --force
  "$PY" party/tools/generate_party_voice_mx.py --audit

Salida:
  party/content/narrator/audio/<juego>/narrator_<momento>_<0-9>.m4a
  party/games/banterbolt/public/host/banterbolt/narrator/...
  party/games/merkissioner/public/host/merkissioner/narrator/...
  party/games/you-got-it/public/host/you-got-it/narrator/...
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import subprocess
import sys
import tempfile
import time
import unicodedata
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
os.environ.setdefault("PYTORCH_CUDA_ALLOC_CONF", "expandable_segments:True")

ROOT = Path(__file__).resolve().parents[2]
HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(os.environ.get("QWEN_TTS_MODEL", str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-Base")))
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")

REF_FEMALE = Path("/media/chronos/Disco Duro/vision-assets/party-games/voice-refs/f1.wav")
REF_MALE = Path("/media/chronos/Disco Duro/vision-assets/party-games/voice-refs/m1.wav")
REF_FEMALE_TEXT = "¡Bienvenidos a Visión Plus Party! Aquí se viene a reír, a ganar y a quedar en ridículo."
REF_MALE_TEXT = REF_FEMALE_TEXT
OUT_ROOT: Path | None = None

# Un perfil por timbre. El texto de referencia se usa en modo ICL para conservar
# el acento y la prosodia mexicanos, no solo el tono.
PROFILES = {
    "female": {
        "ref": REF_FEMALE,
        "ref_text": REF_FEMALE_TEXT,
        "f0_min": 195.0,
        "f0_max": 390.0,
        "f0_target": 280.0,
        "label": "narradora mexicana F1",
    },
    "male": {
        "ref": REF_MALE,
        "ref_text": REF_MALE_TEXT,
        "f0_min": 140.0,
        "f0_max": 330.0,
        "f0_target": 220.0,
        "label": "narrador mexicano M1",
    },
}

STANDARD = [
    "pantomime",
    "zeichnen-und-erraten",
    "schaetzorama",
    "imposter",
    "buzzwort",
    "schattenjagd",
    "flatterfluff",
    "magic-arena",
    "card-table",
    "dungeon-party",
]

GAME_VOICE = {
    **{game: "female" for game in STANDARD},
    "banterbolt": "female",
    "you-got-it": "female",
    "merkissioner": "male",
    "selection": "female",
}


def lines_path(game: str) -> Path:
    if game == "selection":
        return ROOT / "party" / "content" / "narrator" / "selection_welcome.json"
    candidates = [
        ROOT / "party" / "content" / "narrator" / f"{game}_party_lines.json",
        ROOT / "party" / "content" / "narrator" / f"{game}_lines.json",
    ]
    for path in candidates:
        if path.is_file():
            return path
    raise FileNotFoundError(f"No hay guion para {game}")


def out_dir(game: str) -> Path:
    if OUT_ROOT is not None:
        return OUT_ROOT / game
    if game in ("banterbolt", "merkissioner", "you-got-it"):
        return ROOT / "party" / "games" / game / "public" / "host" / game / "narrator"
    return ROOT / "party" / "content" / "narrator" / "audio" / game


def report_path(game: str) -> Path:
    if OUT_ROOT is not None:
        return OUT_ROOT / "reports" / f"{game}_report.json"
    return ROOT / "party" / "content" / "narrator" / f"{game}_report.json"


def load_tasks(game: str) -> list[tuple[str, list[str]]]:
    data = json.loads(lines_path(game).read_text(encoding="utf-8"))
    tasks = []
    for item in data:
        texts = [str(text).strip() for text in item.get("variants", []) if str(text).strip()]
        if texts:
            tasks.append((str(item["id"]), texts))
    return tasks


def syllables(text: str) -> int:
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^a-zñ ]+", " ", t)
    total = 0
    for word in t.split():
        groups = re.findall(r"[aeiou]+", word)
        n = len(groups)
        n -= len(re.findall(r"qu[ei]", word))
        n -= len(re.findall(r"gu[ei]", word))
        if word.endswith("y"):
            n += 1
        total += max(1, n)
    return max(1, total)


def express(text: str) -> str:
    text = " ".join(text.split())
    text = text.replace("...", "…")
    if "?" in text and "¿" not in text:
        text = "¿" + text
    if "!" in text and "¡" not in text:
        text = "¡" + text
    if text and text[-1] not in ".!?…":
        text += "."
    return text


def clean_wav(wav, sr):
    import librosa
    import numpy as np

    y = np.asarray(wav, dtype=np.float32).squeeze()
    _, (start, end) = librosa.effects.trim(y, top_db=38)
    voice = y[start:end]
    lead = np.zeros(int(0.18 * sr), dtype=voice.dtype)
    tail = np.zeros(int(0.06 * sr), dtype=voice.dtype)
    y = np.concatenate([lead, voice, tail])
    peak = float(np.max(np.abs(y))) if len(y) else 0.0
    if peak > 0:
        y = y / peak * 0.82
    return y, sr


def encode(wav, sr, dest: Path) -> None:
    import soundfile as sf

    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="party_mx_") as tmp:
        wav_path = Path(tmp) / "voice.wav"
        sf.write(wav_path, wav, sr)
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(wav_path),
            "-af", "loudnorm=I=-16:TP=-3.0:LRA=11,alimiter=limit=0.75:level=false",
            "-ac", "1", "-ar", "48000", "-c:a", "aac", "-b:a", "64k",
            str(dest),
        ], check=True)


def measure(path: Path, text: str) -> dict:
    import librosa
    import numpy as np

    with tempfile.TemporaryDirectory(prefix="party_measure_") as tmp:
        wav_path = Path(tmp) / "measure.wav"
        subprocess.run([
            "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
            "-i", str(path), "-ac", "1", "-ar", "16000", str(wav_path),
        ], check=True)
        y, sr = librosa.load(str(wav_path), sr=16000, mono=True)
    duration = len(y) / sr
    if len(y) == 0:
        return {"duration": 0.0, "f0": 0.0, "rate": 0.0, "silence": 1.0, "clip": 1.0}
    f0, _, _ = librosa.pyin(y, fmin=60, fmax=420, sr=sr, frame_length=1024, hop_length=160)
    voiced = f0[np.isfinite(f0)]
    rms = librosa.feature.rms(y=y, frame_length=512, hop_length=160)[0]
    return {
        "duration": round(float(duration), 2),
        "f0": round(float(np.median(voiced)), 2) if len(voiced) else 0.0,
        "voiced": int(len(voiced)),
        "rate": round(syllables(text) / max(duration, 0.1), 2),
        "silence": round(float(np.mean(rms < 0.002)), 3),
        "clip": round(float(np.mean(np.abs(y) > 0.995)), 5),
    }


def score(m: dict, profile: dict) -> float:
    if m["duration"] < 0.9 or m["duration"] > 14.0:
        return 1e6
    if m["f0"] <= 0 or not (profile["f0_min"] <= m["f0"] <= profile["f0_max"]):
        return 1e6
    if m["rate"] < 2.6 or m["rate"] > 8.8:
        return 1e6
    if m["clip"] > 0.002:
        return 1e6
    return (
        abs(m["rate"] - 5.2) * 0.7
        + abs(math.log2(m["f0"] / profile["f0_target"])) * 0.8
        + m["silence"] * 2.0
        + max(0.0, m["duration"] - 11.5) * 0.8
    )


def audit() -> int:
    import subprocess

    bad = 0
    total = 0
    for game in [*STANDARD, "banterbolt", "merkissioner", "you-got-it", "selection"]:
        for moment, texts in load_tasks(game):
            for index, text in enumerate(texts):
                total += 1
                path = out_dir(game) / f"narrator_{moment}_{index}.m4a"
                if not path.is_file():
                    print(f"FALTA {game}/{path.name}")
                    bad += 1
                    continue
                info = json.loads(subprocess.check_output([
                    "ffprobe", "-v", "error", "-show_entries",
                    "format=duration:stream=sample_rate,channels", "-of", "json", str(path),
                ]))
                duration = float(info["format"]["duration"])
                stream = info["streams"][0]
                if not (0.9 <= duration <= 14.0) or stream.get("sample_rate") != "48000" or stream.get("channels") != 1:
                    print(f"FORMATO {game}/{path.name} dur={duration:.2f} sr={stream.get('sample_rate')} ch={stream.get('channels')}")
                    bad += 1
    print(f"auditoría: {total - bad}/{total} correctos")
    return 1 if bad else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", default="")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--audit", action="store_true")
    parser.add_argument("--batch", type=int, default=10)
    parser.add_argument("--out-root", type=Path)
    args = parser.parse_args()
    global OUT_ROOT
    OUT_ROOT = args.out_root

    if args.audit:
        return audit()

    games = [args.game] if args.game else [*STANDARD, "banterbolt", "merkissioner", "you-got-it", "selection"]
    for game in games:
        if game not in GAME_VOICE:
            print(f"Juego desconocido: {game}", file=sys.stderr)
            return 2
    if not MODEL_DIR.exists():
        print(f"Falta el modelo: {MODEL_DIR}", file=sys.stderr)
        return 1
    for profile in PROFILES.values():
        if not profile["ref"].is_file():
            print(f"Falta la referencia: {profile['ref']}", file=sys.stderr)
            return 1

    import torch
    from qwen_tts import Qwen3TTSModel

    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        str(MODEL_DIR), device_map="cuda:0", dtype=torch.bfloat16, attn_implementation="sdpa",
    )
    print(f"modelo cargado en {time.time() - started:.1f}s", flush=True)
    torch.set_grad_enabled(False)
    prompts = {}
    for name, profile in PROFILES.items():
        prompts[name] = model.create_voice_clone_prompt(
            ref_audio=str(profile["ref"]), ref_text=profile["ref_text"], x_vector_only_mode=False,
        )

    for game in games:
        profile = PROFILES[GAME_VOICE[game]]
        dest = out_dir(game)
        dest.mkdir(parents=True, exist_ok=True)
        tasks = load_tasks(game)
        report: list[dict] = []
        print(f"\n=== {game} · {len(tasks)} momentos · voz {profile['label']} ===", flush=True)
        for moment, texts in tasks:
            if len(texts) != 10:
                print(f"AVISO {game}/{moment}: {len(texts)} textos, se esperaban 10", file=sys.stderr)
            pending = []
            for index, text in enumerate(texts):
                path = dest / f"narrator_{moment}_{index}.m4a"
                if args.force or not path.is_file() or path.stat().st_size < 1200:
                    pending.append((index, text, path))
                else:
                    report.append({
                        "file": path.name, "moment": moment, "variant": index, "text": text,
                        "voice": GAME_VOICE[game], "mode": "clone",
                    })
            if not pending:
                print(f"[skip] {moment}", flush=True)
                continue
            begin = time.time()
            for start in range(0, len(pending), max(1, args.batch)):
                chunk = pending[start:start + max(1, args.batch)]
                chunk_texts = [express(text) for _, text, _ in chunk]
                wavs = None
                sr = None
                for attempt in range(3):
                    try:
                        with torch.inference_mode():
                            wavs, sr = model.generate_voice_clone(
                                text=chunk_texts,
                                language=["Spanish"] * len(chunk_texts),
                                voice_clone_prompt=prompts[GAME_VOICE[game]],
                                temperature=0.98 + attempt * 0.03,
                                top_p=0.95,
                                top_k=50,
                                repetition_penalty=1.04,
                                non_streaming_mode=True,
                                max_new_tokens=1024,
                            )
                        if wavs and len(wavs) == len(chunk_texts):
                            break
                    except Exception as exc:  # noqa: BLE001
                        print(f"AVISO {game}/{moment}: {exc}; reintento {attempt + 1}", file=sys.stderr)
                        torch.cuda.empty_cache()
                        wavs = None
                        sr = None
                if not wavs or sr is None:
                    print(f"ERROR {game}/{moment}: sin audio", file=sys.stderr)
                    continue
                for (index, text, path), wav in zip(chunk, wavs):
                    clean, sr2 = clean_wav(wav, sr)
                    encode(clean, sr2, path)
                    m = measure(path, text)
                    report.append({
                        "file": path.name, "moment": moment, "variant": index, "text": text,
                        "voice": GAME_VOICE[game], "mode": "clone",
                        **m, "score": round(score(m, profile), 3),
                    })
                    print(f"  {path.name} · {m['duration']:.2f}s · f0 {m['f0']:.0f} · ritmo {m['rate']:.1f}", flush=True)
                torch.cuda.empty_cache()
            print(f"[{game}] {moment}: {len(pending)} tomas en {time.time() - begin:.1f}s", flush=True)
        report.sort(key=lambda item: (item["moment"], item["variant"]))
        payload = {
            "engine": "Qwen3-TTS-12Hz-1.7B-Base (clone)",
            "voice": GAME_VOICE[game],
            "reference": str(profile["ref"]),
            "language": "es-MX",
            "variants_per_moment": 10,
            "items": report,
        }
        rp = report_path(game)
        rp.parent.mkdir(parents=True, exist_ok=True)
        rp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (dest / "narrator_report.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"reporte: {rp}", flush=True)
    print("listo", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
