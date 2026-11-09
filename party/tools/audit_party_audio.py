#!/usr/bin/env python3
"""Auditoría de calidad de la narración de Vision+ Party.

Comprueba lo que sí se puede medir sin oído humano:

  * que existan los 10 archivos por momento;
  * formato AAC mono 48 kHz y duración razonable;
  * loudness integrado cerca de -16 LUFS y pico real sin clipping;
  * silencios de entrada/salida y muestras saturadas;
  * que no haya dos variantes con el mismo audio ni el mismo texto;
  * consistencia de tono (f0) por voz y por momento;
  * transcripción con Whisper (opcional, `--asr`) y parecido con el guion.

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" party/tools/audit_party_audio.py
  "$PY" party/tools/audit_party_audio.py --asr
  "$PY" party/tools/audit_party_audio.py --json /tmp/opencode/audio-audit.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import subprocess
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

# CTranslate2 necesita las librerías CUDA del venv. Se reejecuta una vez con
# LD_LIBRARY_PATH para que el cargador dinámico las encuentre antes de importar
# faster-whisper.
if "--asr" in sys.argv and os.environ.get("PARTY_AUDIO_REEXEC") != "1":
    import glob
    import sysconfig

    purelib = Path(sysconfig.get_paths()["purelib"])
    libs = sorted(glob.glob(str(purelib / "nvidia" / "*" / "lib")))
    joined = ":".join(libs)
    if joined:
        os.environ["LD_LIBRARY_PATH"] = joined + ":" + os.environ.get("LD_LIBRARY_PATH", "")
        os.environ["PARTY_AUDIO_REEXEC"] = "1"
        os.execv(sys.executable, [sys.executable, *sys.argv])

ROOT = Path(__file__).resolve().parents[2]
AUDIO_ROOT: Path | None = None
STANDARD = [
    "pantomime", "zeichnen-und-erraten", "schaetzorama", "imposter", "buzzwort",
    "schattenjagd", "flatterfluff", "magic-arena", "card-table", "dungeon-party",
]
GAMES = [*STANDARD, "banterbolt", "merkissioner", "you-got-it", "selection"]
# Términos que delatan español peninsular, doblaje neutro o inglés metido a la
# fuerza. No son una lista exhaustiva: son una red para que no se cuele lo obvio.
BANNED = [
    "vosotros", "vosotras", "ordenador", "móvil", "patata", "zumo", "coche",
    "conducir", "guay", "chaval", "chavala", "tío", "tía", "hostia", "gilipollas",
    "follar", "joder", "venga", "piso", "coger",
    "show", "game", "play", "score", "party time", "late night",
    "der", "die", "das", "und", "nicht", "spiel", "wort", "witz", "schatten",
    "jagd", "zeichnen", "erraten", "schätz", "zahlen", "flatter", "stich",
    "wette", "karte",
]
GAME_VOICE = {**{game: "female" for game in STANDARD}, "banterbolt": "female", "you-got-it": "female", "merkissioner": "male", "selection": "female"}


def lines_path(game: str) -> Path:
    if game == "selection":
        return ROOT / "party" / "content" / "narrator" / "selection_welcome.json"
    for name in (f"{game}_party_lines.json", f"{game}_lines.json"):
        path = ROOT / "party" / "content" / "narrator" / name
        if path.is_file():
            return path
    raise FileNotFoundError(game)


def out_dir(game: str) -> Path:
    if AUDIO_ROOT is not None:
        return AUDIO_ROOT / game
    if game in ("banterbolt", "merkissioner", "you-got-it"):
        return ROOT / "party" / "games" / game / "public" / "host" / game / "narrator"
    return ROOT / "party" / "content" / "narrator" / "audio" / game


def load_tasks(game: str) -> list[tuple[str, list[str]]]:
    data = json.loads(lines_path(game).read_text(encoding="utf-8"))
    return [(str(item["id"]), [str(x).strip() for x in item.get("variants", []) if str(x).strip()]) for item in data]


def norm_words(text: str) -> list[str]:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^a-z0-9ñ]+", " ", text)
    return [word for word in text.split() if word]


def wer(expected: str, actual: str) -> float:
    import numpy as np

    a = norm_words(expected)
    b = norm_words(actual)
    if not a:
        return 1.0
    previous = list(range(len(b) + 1))
    for i, word_a in enumerate(a, 1):
        current = [i]
        for j, word_b in enumerate(b, 1):
            current.append(min(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + (word_a != word_b),
            ))
        previous = current
    return previous[-1] / max(1, len(a))


def ffprobe(path: Path) -> dict:
    return json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_entries",
        "format=duration,size:stream=codec_name,sample_rate,channels,bit_rate",
        "-of", "json", str(path),
    ]))


def loudness(path: Path) -> tuple[float, float]:
    """Devuelve (LUFS integrado, true peak dBFS) usando ebur128."""
    proc = subprocess.run([
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-filter_complex", "ebur128=peak=true", "-f", "null", "-",
    ], capture_output=True, text=True)
    text = proc.stderr
    integrated = -70.0
    peak = -70.0
    matches = re.findall(r"I:\s*(-?\d+(?:\.\d+)?)\s*LUFS", text)
    if matches:
        integrated = float(matches[-1])
    matches = re.findall(r"True peak:\s*Peak:\s*(-?\d+(?:\.\d+)?)\s*dBFS", text)
    if matches:
        peak = float(matches[-1])
    return integrated, peak


def silence_edges(path: Path) -> tuple[float, float]:
    """Duración de silencio inicial y final con ffmpeg silencedetect."""
    proc = subprocess.run([
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-af", "silencedetect=noise=-45dB:d=0.15", "-f", "null", "-",
    ], capture_output=True, text=True)
    text = proc.stderr
    duration = float(ffprobe(path)["format"]["duration"])
    starts = [float(x) for x in re.findall(r"silence_start:\s*(-?\d+(?:\.\d+)?)", text)]
    ends = [float(x) for x in re.findall(r"silence_end:\s*(-?\d+(?:\.\d+)?)", text)]
    lead = 0.0
    tail = 0.0
    if starts and starts[0] <= 0.05:
        lead = (ends[0] if ends else duration)
    if ends and ends[-1] >= duration - 0.1:
        tail = duration - (starts[-1] if starts else 0.0)
    return round(lead, 3), round(tail, 3)


def max_sample(path: Path) -> float:
    proc = subprocess.run([
        "ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
        "-af", "astats=metadata=1:reset=0", "-f", "null", "-",
    ], capture_output=True, text=True)
    matches = re.findall(r"Peak level dB:\s*(-?\d+(?:\.\d+)?)", proc.stderr)
    if not matches:
        return 0.0
    return float(matches[-1])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", default="")
    parser.add_argument("--audio-root", type=Path)
    parser.add_argument("--asr", action="store_true")
    parser.add_argument("--json", type=Path)
    parser.add_argument("--max-wer", type=float, default=0.45)
    parser.add_argument("--max-f0-semitones", type=float, default=4.0)
    args = parser.parse_args()
    global AUDIO_ROOT
    AUDIO_ROOT = args.audio_root

    failures: list[str] = []
    warnings: list[str] = []
    rows: list[dict] = []
    seen_hashes: dict[str, str] = {}
    expected_total = 0
    games = [args.game] if args.game else GAMES

    for game in games:
        for moment, texts in load_tasks(game):
            expected_total += len(texts)
            if len(texts) != 10:
                failures.append(f"{game}/{moment}: {len(texts)} textos (se esperaban 10)")
            if len(set(texts)) != len(texts):
                failures.append(f"{game}/{moment}: textos repetidos")
            for text in texts:
                low = text.lower()
                for word in BANNED:
                    if re.search(rf"(?<![a-záéíóúñ]){re.escape(word)}(?![a-záéíóúñ])", low):
                        failures.append(f"LÉXICO {game}/{moment}: “{word}” en “{text}”")
                        break
            for index, text in enumerate(texts):
                path = out_dir(game) / f"narrator_{moment}_{index}.m4a"
                key = f"{game}/{path.name}"
                if not path.is_file():
                    failures.append(f"FALTA {key}")
                    continue
                if path.stat().st_size < 1200:
                    failures.append(f"MUY PEQUEÑO {key} ({path.stat().st_size} B)")
                    continue
                info = ffprobe(path)
                stream = info["streams"][0]
                duration = float(info["format"]["duration"])
                if stream.get("codec_name") != "aac":
                    failures.append(f"CODEC {key}: {stream.get('codec_name')}")
                if stream.get("sample_rate") != "48000" or stream.get("channels") != 1:
                    failures.append(f"FORMATO {key}: {stream.get('sample_rate')} Hz / {stream.get('channels')} ch")
                if not (1.0 <= duration <= 14.0):
                    failures.append(f"DURACIÓN {key}: {duration:.2f}s")
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                if digest in seen_hashes:
                    failures.append(f"DUPLICADO {key} = {seen_hashes[digest]}")
                else:
                    seen_hashes[digest] = key
                lufs, peak = loudness(path)
                if not (-18.5 <= lufs <= -13.5):
                    failures.append(f"LOUDNESS {key}: {lufs:.1f} LUFS")
                if peak > -1.0:
                    failures.append(f"PICO {key}: {peak:.1f} dBFS")
                max_db = max_sample(path)
                if max_db > -0.5:
                    failures.append(f"SATURACIÓN {key}: {max_db:.1f} dBFS")
                lead, tail = silence_edges(path)
                if lead > 0.45:
                    failures.append(f"SILENCIO INICIAL {key}: {lead:.2f}s")
                if tail > 0.65:
                    failures.append(f"SILENCIO FINAL {key}: {tail:.2f}s")
                try:
                    row_file = str(path.relative_to(ROOT))
                except ValueError:
                    row_file = str(path)
                rows.append({
                    "game": game, "moment": moment, "variant": index, "file": row_file,
                    "text": text, "duration": round(duration, 2), "lufs": round(lufs, 1),
                    "peak": round(peak, 1), "lead": lead, "tail": tail,
                    "hash": digest[:16],
                })

    # Consistencia de tono por voz y momento.
    by_voice: dict[str, list[dict]] = defaultdict(list)
    for row in rows:
        by_voice[GAME_VOICE[row["game"]]].append(row)
    # La f0 real se calcula solo si hace falta revisar; aquí se usa el reporte
    # de generación cuando existe para no cargar 630 audios con librosa.
    for game in games:
        if AUDIO_ROOT is not None:
            report_file = AUDIO_ROOT / "reports" / f"{game}_report.json"
        else:
            report_file = (ROOT / "party" / "content" / "narrator" / f"{game}_report.json")
            if not report_file.is_file():
                report_file = out_dir(game) / "narrator_report.json"
        if not report_file.is_file():
            warnings.append(f"Sin reporte de f0 para {game}")
            continue
        payload = json.loads(report_file.read_text(encoding="utf-8"))
        by_moment: dict[str, list[float]] = defaultdict(list)
        for item in payload.get("items", []):
            if item.get("f0"):
                by_moment[item["moment"]].append(float(item["f0"]))
        for moment, values in by_moment.items():
            if len(values) < 3:
                continue
            target = sorted(values)[len(values) // 2]
            for value in values:
                semis = 12 * math.log2(value / target) if value > 0 and target > 0 else 99
                if abs(semis) > args.max_f0_semitones:
                    failures.append(f"TONO {game}/{moment}: {value:.1f} Hz ({semis:+.1f} st)")
                    break

    if args.asr:
        try:
            import glob
            import sysconfig

            purelib = Path(sysconfig.get_paths()["purelib"])
            libs = sorted(glob.glob(str(purelib / "nvidia" / "*" / "lib")))
            joined = ":".join(libs)
            if joined and joined not in os.environ.get("LD_LIBRARY_PATH", ""):
                os.environ["LD_LIBRARY_PATH"] = joined + ":" + os.environ.get("LD_LIBRARY_PATH", "")
            import soundfile as sf
            from faster_whisper import WhisperModel

            model_dir = Path("/media/chronos/Disco Duro/vision-tts/models/faster-whisper-small")
            model = WhisperModel(str(model_dir), device="cuda", compute_type="float16")
            import tempfile

            for index, row in enumerate(rows, 1):
                path = Path(row["file"]) if Path(row["file"]).is_absolute() else ROOT / row["file"]
                with tempfile.TemporaryDirectory(prefix="party_asr_") as tmp:
                    wav_path = Path(tmp) / "asr.wav"
                    subprocess.run([
                        "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                        "-i", str(path), "-ac", "1", "-ar", "16000", str(wav_path),
                    ], check=True)
                    y, sr = sf.read(str(wav_path), dtype="float32")
                segments, info = model.transcribe(y, language="es", beam_size=5, vad_filter=False)
                transcript = " ".join(segment.text for segment in segments).strip()
                error = wer(row["text"], transcript)
                row["transcript"] = transcript
                row["wer"] = round(error, 3)
                if error > args.max_wer:
                    failures.append(f"TRANSCRIPCIÓN {row['game']}/{row['moment']}/{row['variant']}: WER {error:.2f}")
                if index % 50 == 0:
                    print(f"ASR {index}/{len(rows)}", file=sys.stderr)
        except Exception as exc:  # noqa: BLE001
            failures.append(f"ASR no disponible: {exc}")

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps({
            "expected": expected_total, "checked": len(rows), "failures": failures,
            "warnings": warnings, "items": rows,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    for item in failures:
        print(f"FALLA {item}")
    for item in warnings:
        print(f"AVISO {item}")
    print(f"auditoría: {expected_total - len(failures)}/{expected_total} archivos esperados; {len(rows)} revisados; {len(failures)} fallas; {len(warnings)} avisos")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
