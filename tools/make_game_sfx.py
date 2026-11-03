#!/usr/bin/env python3
"""Genera los efectos de sonido de Vision+ Play (WAV local, sin samples).

Se sintetizan con numpy y se guardan en `res/raw/game_sfx_*.wav` y en el
disco grande (`/media/chronos/Disco Duro/vision-assets/games/sfx`).
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/raw"
MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/sfx")
SR = 44100


def envelope(n: int, attack: float = 0.01, release: float = 0.08) -> np.ndarray:
    env = np.ones(n, dtype=np.float32)
    a = max(1, min(n // 2, int(attack * SR)))
    r = max(1, min(n // 2, int(release * SR)))
    env[:a] = np.linspace(0, 1, a, dtype=np.float32)
    env[-r:] = np.linspace(1, 0, r, dtype=np.float32)
    return env


def tone(freq: float, duration: float, wave_type: str = "sine") -> np.ndarray:
    n = int(duration * SR)
    t = np.arange(n, dtype=np.float32) / SR
    phase = 2 * math.pi * freq * t
    if wave_type == "square":
        base = np.sign(np.sin(phase))
    elif wave_type == "saw":
        base = 2 * ((freq * t) % 1) - 1
    else:
        base = np.sin(phase)
    return (base * envelope(n)).astype(np.float32)


def noise(duration: float, decay: float = 8.0) -> np.ndarray:
    n = int(duration * SR)
    rng = np.random.default_rng(7788)
    signal = rng.standard_normal(n).astype(np.float32)
    t = np.linspace(0, duration, n, dtype=np.float32)
    return signal * np.exp(-decay * t)


def sweep(start: float, end: float, duration: float) -> np.ndarray:
    n = int(duration * SR)
    t = np.linspace(0, 1, n, dtype=np.float32)
    freq = start + (end - start) * t
    phase = 2 * math.pi * np.cumsum(freq) / SR
    return (np.sin(phase) * envelope(n)).astype(np.float32)


def mix(*parts: np.ndarray) -> np.ndarray:
    size = max(len(p) for p in parts)
    out = np.zeros(size, dtype=np.float32)
    for part in parts:
        out[: len(part)] += part
    return out


def normalize(signal: np.ndarray, peak: float = 0.82) -> np.ndarray:
    maximum = float(np.max(np.abs(signal)) or 1.0)
    return (signal / maximum * peak).astype(np.float32)


def write(name: str, signal: np.ndarray) -> None:
    audio = normalize(signal)
    pcm = (audio * 32767).astype(np.int16)
    OUT.mkdir(parents=True, exist_ok=True)
    MASTER.mkdir(parents=True, exist_ok=True)
    for target in (OUT / f"{name}.wav", MASTER / f"{name}.wav"):
        with wave.open(str(target), "wb") as handle:
            handle.setnchannels(1)
            handle.setsampwidth(2)
            handle.setframerate(SR)
            handle.writeframes(pcm.tobytes())
    print(f"{name}.wav · {len(pcm) / SR:.2f}s")


def main() -> int:
    write("game_sfx_tick", mix(tone(1040, 0.09), tone(1560, 0.05) * 0.3))
    write("game_sfx_vote", mix(tone(520, 0.16), tone(780, 0.18) * 0.8))
    write("game_sfx_secret", mix(
        sweep(180, 420, 0.55),
        noise(0.5, 5.0) * 0.08,
    ))
    write("game_sfx_reveal", mix(
        sweep(280, 980, 0.48),
        tone(1320, 0.25) * 0.35,
    ))
    write("game_sfx_hammer", mix(
        tone(90, 0.42, "saw") * 0.9,
        noise(0.18, 18.0) * 0.55,
    ))
    write("game_sfx_whoosh", mix(
        noise(0.45, 3.5) * 0.55,
        sweep(900, 180, 0.45) * 0.3,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
