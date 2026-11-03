#!/usr/bin/env python3
"""Genera la voz de la narradora de Vision+ Play con Qwen3-TTS local.

MOTOR
  Qwen3-TTS-12Hz-1.7B-Base (Apache 2.0), clonando `dalia.wav` como timbre.
  El modelo, el venv y la caché viven en el disco grande:
      /media/chronos/Disco Duro/vision-tts/{venv,hf,models,out}
  Sin dependencias del motor TTS anterior.

EXPRESIVIDAD
  Por cada línea se generan varias tomas y se elige la mejor medida:
  variación de tono (semitonos), energía y ritmo, descartando tomas
  truncadas, repetidas o con mala transcripción (Whisper). Así no se
  fuerza la voz con `exaggeration` ni con velocidad artificial.

USO
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" tools/generate_narrator_voice.py --pilot        # 5 líneas de prueba
  "$PY" tools/generate_narrator_voice.py --force        # las 200
  "$PY" tools/generate_narrator_voice.py --audit        # solo revisa los MP3

  Variables: NARRATOR_REF, NARRATOR_REF_TEXT, QWEN_TTS_HOME, QWEN_TTS_MODEL,
  NARRATOR_VARIANTS, NARRATOR_CANDIDATES, NARRATOR_BITRATE, NARRATOR_EXT.
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
from dataclasses import dataclass
from pathlib import Path

os.environ.setdefault("HF_HUB_DISABLE_PROGRESS_BARS", "1")
os.environ.setdefault("TRANSFORMERS_VERBOSITY", "error")
os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")

# CTranslate2 (faster-whisper) necesita las libs CUDA del venv; el cargador
# dinámico lee LD_LIBRARY_PATH al arrancar, así que nos relanzamos una vez.
if os.environ.get("VISION_TTS_REEXEC") != "1":
    import glob
    import sysconfig

    purelib = Path(sysconfig.get_paths()["purelib"])
    nvidia = sorted(glob.glob(str(purelib / "nvidia" / "*" / "lib")))
    cusparselt = purelib / "cusparselt" / "lib"
    if cusparselt.is_dir():
        nvidia.append(str(cusparselt))
    joined = ":".join(nvidia)
    if joined and joined not in os.environ.get("LD_LIBRARY_PATH", ""):
        os.environ["LD_LIBRARY_PATH"] = (
            joined + ":" + os.environ.get("LD_LIBRARY_PATH", "")
        ).rstrip(":")
        os.environ["VISION_TTS_REEXEC"] = "1"
        os.execv(sys.executable, [sys.executable, *sys.argv])

import numpy as np  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
LINES = ROOT / "tools" / "narrator_lines.json"
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"

HOME = Path(os.environ.get("QWEN_TTS_HOME", "/media/chronos/Disco Duro/vision-tts"))
MODEL_DIR = Path(
    os.environ.get(
        "QWEN_TTS_MODEL",
        str(HOME / "models" / "Qwen3-TTS-12Hz-1.7B-Base"),
    )
)
REF = Path(
    os.environ.get("NARRATOR_REF", str(HOME / "ref" / "dalia.wav"))
)
LANG = "spanish"
os.environ["PATH"] = str(HOME / "bin") + ":" + os.environ.get("PATH", "")
BITRATE = os.environ.get("NARRATOR_BITRATE", "48k")
AUDIO_EXT = os.environ.get("NARRATOR_EXT", "m4a")
VARIANTS = int(os.environ.get("NARRATOR_VARIANTS", "10"))
CANDIDATES = int(os.environ.get("NARRATOR_CANDIDATES", "6"))
ASR_MODEL = os.environ.get(
    "NARRATOR_ASR", str(HOME / "models" / "faster-whisper-base")
)

# Tono objetivo (desviación típica en semitonos) por momento. La media de
# El motor anterior daba 3.31 st; aquí pedimos un poco más de vida sin sobreactuar.
TARGET_F0 = {
    "welcome": 4.4,
    "selector": 3.8,
    "create": 3.9,
    "join": 4.1,
    "start": 4.5,
    "correct": 4.8,
    "wrong": 4.2,
    "time": 4.3,
    "reveal": 3.6,
    "podium": 4.4,
    "solo": 3.6,
    "charada_turn": 4.5,
    "charada_correct": 4.6,
    "charada_pass": 4.0,
    "charada_reveal": 4.0,
    "wager_start": 4.0,
    "wager_clue": 3.8,
    "wager_reveal": 4.2,
    "wager_win": 4.6,
    "wager_lose": 3.9,
}


@dataclass
class Take:
    wav: np.ndarray
    sr: int
    dur: float
    f0_std: float
    rms_std: float
    rate: float
    clip: float
    silence: float
    score: float = 0.0
    wer: float = 1.0
    text: str = ""

    @property
    def valid(self) -> bool:
        return self.dur > 0.4 and self.clip < 0.02 and self.silence < 0.6


def _norm_words(text: str) -> list[str]:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^a-z0-9ñ ]+", " ", text)
    return [w for w in text.split() if w]


def wer(reference: str, hypothesis: str) -> float:
    ref, hyp = _norm_words(reference), _norm_words(hypothesis)
    if not ref:
        return 1.0
    prev = list(range(len(hyp) + 1))
    for i, rw in enumerate(ref, 1):
        cur = [i]
        for j, hw in enumerate(hyp, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1,
                           prev[j - 1] + (0 if rw == hw else 1)))
        prev = cur
    return prev[-1] / len(ref)


def syllables(text: str) -> int:
    """Aproximación suficiente para estimar el ritmo esperado."""
    t = unicodedata.normalize("NFD", text.lower())
    t = "".join(c for c in t if unicodedata.category(c) != "Mn")
    t = re.sub(r"[^a-zñ ]+", " ", t)
    total = 0
    for word in t.split():
        groups = re.findall(r"[aeiouáéíóúü]+", word)
        n = len(groups)
        n -= len(re.findall(r"qu[ei]", word))
        n -= len(re.findall(r"gu[ei]", word))
        if word.endswith("y"):
            n += 1
        total += max(1, n)
    return max(1, total)


def express(text: str) -> str:
    """Limpieza prosódica sin cambiar ni una palabra."""
    text = " ".join(str(text).split())
    text = text.replace("...", "…")
    # iOS/Android suelen mandar comillas rectas; las curvas ayudan al modelo.
    text = re.sub(r'"([^"]+)"', r"«\1»", text)
    # Signos de apertura si faltan (el modelo respeta mejor la entonación).
    if "?" in text and "¿" not in text:
        text = "¿" + text
    if "!" in text and "¡" not in text:
        text = "¡" + text
    if text and text[-1] not in ".!?…":
        text += "."
    return text


def trim(y: np.ndarray, sr: int) -> np.ndarray:
    import librosa
    import numpy as np

    _, (start, end) = librosa.effects.trim(y, top_db=38)
    # Las TVs (Chromecast, Android TV) tardan unos 100-150 ms en abrir la pista
    # de audio de un MediaPlayer: si la toma empieza en el primer fonema, el
    # arranque se come la primera sílaba. Se deja un pequeño colchón de silencio.
    lead = int(0.18 * sr)
    tail = int(0.06 * sr)
    voice = y[start:end]
    return np.concatenate([np.zeros(lead, dtype=y.dtype), voice, np.zeros(tail, dtype=y.dtype)])


def analyze(wav: np.ndarray, sr: int, text: str) -> Take:
    import librosa

    y = np.asarray(wav, dtype=np.float32).squeeze()
    y = trim(y, sr)
    dur = len(y) / sr
    y16 = librosa.resample(y, orig_sr=sr, target_sr=16000)
    f0, _, _ = librosa.pyin(
        y16, fmin=70, fmax=420, sr=16000,
        frame_length=1024, hop_length=160,
    )
    voiced = f0[np.isfinite(f0)]
    if len(voiced) > 5:
        semis = 12 * np.log2(voiced / np.median(voiced))
        f0_std = float(np.std(semis))
    else:
        f0_std = 0.0
    rms = librosa.feature.rms(y=y16, frame_length=512, hop_length=160)[0]
    gate = np.percentile(rms, 25)
    voiced_rms = rms[rms > gate]
    rms_std = (
        float(np.std(20 * np.log10(np.maximum(voiced_rms, 1e-6))))
        if len(voiced_rms) > 4 else 0.0
    )
    clip = float(np.mean(np.abs(y) > 0.995))
    silence = float(np.mean(np.abs(y) < 0.004))
    rate = syllables(text) / max(dur, 0.1)
    return Take(y, sr, dur, f0_std, rms_std, rate, clip, silence)


def score_take(take: Take, moment: str) -> float:
    target = TARGET_F0.get(moment, 4.0)
    expected = syllables(take.text) / 5.0
    penalty = 0.0
    if take.rate < 3.2 or take.rate > 8.5:
        penalty += 4.0
    if take.dur < expected * 0.55 or take.dur > expected * 1.9:
        penalty += 3.0
    return (
        -abs(take.f0_std - target)
        + 0.070 * min(take.rms_std, 28.0)
        - 1.1 * abs(math.log(max(take.dur, 0.1) / max(expected, 0.1)))
        - 2.0 * take.clip
        - penalty
    )


def ref_text_for(ref: Path) -> str:
    env = os.environ.get("NARRATOR_REF_TEXT", "").strip()
    if env:
        return env
    cache = HOME / "ref" / f"{ref.stem}.txt"
    if cache.exists():
        return cache.read_text(encoding="utf-8").strip()
    print("Transcribiendo la referencia con faster-whisper…", flush=True)
    from faster_whisper import WhisperModel

    model = WhisperModel(ASR_MODEL, device="cuda", compute_type="float16")
    import librosa

    audio, _ = librosa.load(str(ref), sr=16000, mono=True)
    segments, _ = model.transcribe(
        audio, language="es", beam_size=5, vad_filter=True,
    )
    text = " ".join(s.text.strip() for s in segments).strip()
    if not text:
        raise SystemExit("No se pudo transcribir la referencia de voz.")
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(text + "\n", encoding="utf-8")
    print(f"referencia: {text}", flush=True)
    return text


def to_audio(wav: np.ndarray, sr: int, destination: Path) -> None:
    import shutil
    import soundfile as sf

    handle, wav_path = tempfile.mkstemp(suffix=".wav")
    os.close(handle)
    # El parcial NO puede vivir en res/raw: Android rechaza nombres con puntos
    # extra. Se compila en el disco grande y se mueve al final.
    HOME.joinpath("tmp").mkdir(parents=True, exist_ok=True)
    handle, partial_name = tempfile.mkstemp(suffix=".m4a", dir=str(HOME / "tmp"))
    os.close(handle)
    partial = Path(partial_name)
    try:
        sf.write(wav_path, wav, sr, subtype="PCM_16")
        subprocess.run(
            [
                "ffmpeg", "-v", "error", "-y", "-i", wav_path,
                "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
                "-ac", "1", "-ar", "44100",
                "-codec:a", "aac", "-profile:a", "aac_low",
                "-b:a", BITRATE, "-movflags", "+faststart",
                "-f", "mp4", str(partial),
            ],
            check=True,
        )
        shutil.move(str(partial), str(destination))
    finally:
        for path in (wav_path, partial):
            try:
                os.unlink(path)
            except OSError:
                pass


def load_tasks(
    only: str | None, pilot: bool, count: int, lines_path: Path = LINES,
) -> list[tuple[str, str, Path]]:
    jobs = json.loads(lines_path.read_text(encoding="utf-8"))
    if only:
        wanted = {part.strip() for part in str(only).split(",") if part.strip()}
        jobs = [j for j in jobs if j["id"] in wanted]
        if not jobs:
            raise SystemExit(f"No existe el momento {only!r}")
    if pilot:
        keep = {"welcome", "correct", "wrong", "wager_start", "podium"}
        jobs = [j for j in jobs if j["id"] in keep]
        count = min(count, 2)
    tasks = []
    for job in jobs:
        variants = job.get("variants") or ([job["text"]] if job.get("text") else [])
        for number, text in enumerate(variants[:count]):
            dest = OUT / f"narrator_{job['id']}_{number}.{AUDIO_EXT}"
            tasks.append((job["id"], express(text), dest))
    return tasks


def audit(paths: list[Path]) -> int:
    bad = 0
    for path in paths:
        if not path.exists():
            print(f"FALTA {path.name}")
            bad += 1
            continue
        dur = float(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "csv=p=0", str(path),
        ]).strip())
        if dur < 1.0 or dur > 13.0:
            print(f"FUERA DE RANGO {path.name} {dur:.2f}s")
            bad += 1
    print(f"auditoría: {len(paths) - bad}/{len(paths)} correctos")
    return 1 if bad else 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--pilot", action="store_true")
    parser.add_argument("--only")
    parser.add_argument("--audit", action="store_true")
    parser.add_argument("--candidates", type=int, default=CANDIDATES)
    parser.add_argument("--variants", type=int, default=VARIANTS)
    parser.add_argument("--no-verify", action="store_true")
    parser.add_argument("--x-vector", action="store_true",
                        help="Clonar solo el timbre (sin el estilo de la referencia)")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--file", action="append", default=[],
                        help="Regenerar solo estos nombres (repetible)")
    parser.add_argument("--lines", type=Path, default=LINES,
                        help="JSON de momentos alternativo")
    args = parser.parse_args()

    if not REF.exists():
        print(f"Falta la referencia de voz: {REF}", file=sys.stderr)
        return 1

    tasks = load_tasks(args.only, args.pilot, args.variants, args.lines)
    out_dir = args.out or HOME / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    if args.out:
        tasks = [(m, t, args.out / d.name) for m, t, d in tasks]
        OUT.mkdir(parents=True, exist_ok=True)
        args.out.mkdir(parents=True, exist_ok=True)

    if args.file:
        wanted = set(args.file)
        tasks = [t for t in tasks if t[2].name in wanted]
        if not tasks:
            raise SystemExit("Ningún archivo coincide con --file")

    all_tasks = list(tasks)
    report_path = out_dir / "narrator_report.json"
    previous: dict[str, dict] = {}
    if report_path.exists():
        try:
            payload = json.loads(report_path.read_text(encoding="utf-8"))
            previous = {item["file"]: item for item in payload.get("items", [])}
        except (OSError, ValueError, KeyError):
            previous = {}

    if args.audit:
        return audit([d for _, _, d in all_tasks])

    if not args.force:
        if previous:
            tasks = [
                t for t in tasks
                if t[2].name not in previous or not t[2].exists()
            ]
        else:
            tasks = [t for t in tasks if not t[2].exists()]
    if not tasks:
        print("Nada que generar (usa --force para rehacer).")
        return audit([d for _, _, d in all_tasks])

    import soundfile as sf
    import torch
    from qwen_tts import Qwen3TTSModel

    if not MODEL_DIR.exists():
        print(f"Falta el modelo en {MODEL_DIR}", file=sys.stderr)
        return 1
    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        str(MODEL_DIR),
        device_map="cuda:0",
        dtype=torch.bfloat16,
        attn_implementation="sdpa",
    )
    langs = model.get_supported_languages()
    if LANG not in langs:
        print(f"Idiomas del modelo: {langs}", file=sys.stderr)
        return 1
    print(f"modelo cargado en {time.time() - started:.1f} s", flush=True)
    prompt = model.create_voice_clone_prompt(
        ref_audio=str(REF),
        ref_text=None if args.x_vector else ref_text_for(REF),
        x_vector_only_mode=args.x_vector,
    )

    asr = None
    if not args.no_verify:
        from faster_whisper import WhisperModel

        asr = WhisperModel(ASR_MODEL, device="cuda", compute_type="float16")

    report: list[dict] = []
    torch.set_grad_enabled(False)
    for index, (moment, text, dest) in enumerate(tasks, 1):
        begin = time.time()
        takes: list[Take] = []
        count = max(1, args.candidates)

        def generate(n: int, tokens: int):
            with torch.inference_mode():
                return model.generate_voice_clone(
                    text=[text] * n,
                    language=[LANG] * n,
                    voice_clone_prompt=prompt,
                    temperature=1.05,
                    top_p=0.95,
                    top_k=50,
                    repetition_penalty=1.05,
                    non_streaming_mode=True,
                    max_new_tokens=tokens,
                )

        wavs = sr = None
        for n, tokens in ((count, 768), (max(2, count // 2), 512), (1, 384)):
            try:
                wavs, sr = generate(n, tokens)
                break
            except RuntimeError as exc:
                if "out of memory" not in str(exc).lower():
                    raise
                torch.cuda.empty_cache()
                print(
                    f"AVISO {moment} · GPU sin memoria (lote {n}); "
                    f"reintento más pequeño",
                    flush=True,
                )
        if wavs is None or sr is None:
            raise RuntimeError(f"{moment}: sin memoria incluso con lote 1")

        for wav in wavs:
            take = analyze(wav, sr, text)
            take.text = text
            if take.valid:
                take.score = score_take(take, moment)
                takes.append(take)
        if not takes:
            print(f"ERROR {moment} · todas las tomas inválidas", flush=True)
            return 1
        takes.sort(key=lambda t: t.score, reverse=True)
        best = takes[0]
        torch.cuda.empty_cache()
        if asr is not None:
            import librosa

            for candidate in takes[:3]:
                try:
                    segments, _ = asr.transcribe(
                        librosa.resample(
                            candidate.wav, orig_sr=candidate.sr,
                            target_sr=16000,
                        ),
                        language="es", beam_size=1, vad_filter=False,
                    )
                except RuntimeError as exc:
                    if "out of memory" not in str(exc).lower():
                        raise
                    torch.cuda.empty_cache()
                    print(
                        f"AVISO {moment} · Whisper sin memoria; "
                        f"se usa la mejor toma sin verificar",
                        flush=True,
                    )
                    break
                candidate.wer = wer(
                    text, " ".join(s.text for s in segments)
                )
                if candidate.wer <= 0.40:
                    best = candidate
                    break
            else:
                print(
                    f"AVISO {moment} · ninguna toma con WER bajo "
                    f"(mejor {best.wer:.2f})",
                    flush=True,
                )
        else:
            best.wer = 0.0
        to_audio(best.wav, best.sr, dest)
        report.append({
            "moment": moment, "file": dest.name, "text": text,
            "dur": round(best.dur, 3), "f0_std": round(best.f0_std, 3),
            "rms_std": round(best.rms_std, 3), "rate": round(best.rate, 3),
            "wer": round(best.wer, 3), "candidates": len(takes),
        })
        merged = {**previous, **{item["file"]: item for item in report}}
        report_path.write_text(
            json.dumps({
                "model": "Qwen3-TTS-12Hz-1.7B-Base",
                "reference": str(REF), "bitrate": BITRATE,
                "candidates": args.candidates,
                "items": list(merged.values()),
            }, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        print(
            f"[{index}/{len(tasks)}] {dest.name} · {best.dur:.2f}s · "
            f"f0±{best.f0_std:.2f}st · {best.rate:.1f}sil/s · "
            f"wer {best.wer:.2f} · {len(takes)} tomas · "
            f"{time.time() - begin:.1f}s",
            flush=True,
        )

    items = list({**previous, **{i["file"]: i for i in report}}.values())
    means = {
        key: sum(i[key] for i in items) / len(items)
        for key in ("dur", "f0_std", "rms_std", "rate", "wer")
    }
    print(
        "listo en {:.1f} min · media {:.2f}s · f0±{:.2f}st · "
        "{:.1f}sil/s · wer {:.2f} · {} archivos".format(
            (time.time() - started) / 60, means["dur"], means["f0_std"],
            means["rate"], means["wer"], len(items),
        )
    )
    return audit([d for _, _, d in all_tasks])


if __name__ == "__main__":
    raise SystemExit(main())
