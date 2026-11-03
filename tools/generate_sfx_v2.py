#!/usr/bin/env python3
"""Generador procedural de SFX por capas para Vision+ Play v2.

Produce: game_sfx_buzzer.wav, game_sfx_glass.wav, game_sfx_heartbeat.wav,
         game_sfx_safe.wav, game_sfx_fanfare.wav
"""

import math
import os
import random
import struct
import wave
from pathlib import Path

SAMPLE_RATE = 44100
ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "app/src/main/res/raw"
MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/sfx")
OUT_DIR.mkdir(parents=True, exist_ok=True)
MASTER.mkdir(parents=True, exist_ok=True)


def save_wav(filename: str, samples: list[float]):
    max_val = max(max(abs(s) for s in samples), 1e-6)
    norm = min(1.0, 0.92 / max_val)
    pcm = bytearray()
    for s in samples:
        val = int(max(-1.0, min(1.0, s * norm)) * 32767)
        pcm.extend(struct.pack("<h", val))
    for path in (OUT_DIR / filename, MASTER / filename):
        with wave.open(str(path), "w") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(2)
            wf.setframerate(SAMPLE_RATE)
            wf.writeframes(bytes(pcm))
    print(f"[OK] Generado: {OUT_DIR / filename} ({len(samples)/SAMPLE_RATE:.2f}s)")


def gen_buzzer() -> list[float]:
    """Timbre de estudio 'CORTE': ataque brillante + cuerpo de campana FM."""
    dur = 0.65
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 5.5) * (1.0 - math.exp(-t * 120.0))
        mod = math.sin(2 * math.pi * 880 * t) * 2.5 * math.exp(-t * 8.0)
        carrier = math.sin(2 * math.pi * 587.33 * t + mod)
        sub = 0.4 * math.sin(2 * math.pi * 293.66 * t)
        click = 0.6 * math.exp(-t * 90.0) * (random.random() * 2 - 1)
        out.append((carrier + sub + click) * env)
    return out


def gen_glass() -> list[float]:
    """Cristal roto (Empate en Subasta): impacto + esquirlas de alta frecuencia."""
    dur = 0.85
    n = int(SAMPLE_RATE * dur)
    out = [0.0] * n
    for i in range(int(SAMPLE_RATE * 0.12)):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 35.0)
        thump = math.sin(2 * math.pi * (180 * math.exp(-t * 25)) * t)
        noise = (random.random() * 2 - 1) * 0.9
        out[i] += (thump + noise) * env

    random.seed(42)
    for _ in range(18):
        start = int(random.uniform(0.01, 0.38) * SAMPLE_RATE)
        freq1 = random.uniform(2200, 6800)
        freq2 = freq1 * random.uniform(1.31, 1.73)
        shard_len = int(random.uniform(0.06, 0.25) * SAMPLE_RATE)
        amp = random.uniform(0.25, 0.7)
        for j in range(shard_len):
            if start + j >= n:
                break
            t = j / SAMPLE_RATE
            env = math.exp(-t * random.uniform(18.0, 35.0))
            s = (math.sin(2 * math.pi * freq1 * t) + 0.6 * math.sin(2 * math.pi * freq2 * t)) * env * amp
            out[start + j] += s
    return out


def gen_heartbeat() -> list[float]:
    """Doble latido profundo (Rol Impostor / Tensión final)."""
    dur = 0.95
    n = int(SAMPLE_RATE * dur)
    out = [0.0] * n
    pulses = [(0.04, 1.0, 58.0), (0.28, 0.75, 48.0)]
    for start_s, amp, f_base in pulses:
        start_i = int(start_s * SAMPLE_RATE)
        length_i = int(0.22 * SAMPLE_RATE)
        for j in range(length_i):
            if start_i + j >= n:
                break
            t = j / SAMPLE_RATE
            env = math.sin(math.pi * min(1.0, t / 0.22)) * math.exp(-t * 12.0)
            freq = f_base + 35.0 * math.exp(-t * 28.0)
            out[start_i + j] += amp * env * math.sin(2 * math.pi * freq * t)
    return out


def gen_safe_tick() -> list[float]:
    """Caja fuerte / Dial mecánico (Subir puja o comprar pista)."""
    dur = 0.14
    n = int(SAMPLE_RATE * dur)
    out = []
    for i in range(n):
        t = i / SAMPLE_RATE
        env = math.exp(-t * 55.0)
        metallic = math.sin(2 * math.pi * 1450 * t) + 0.5 * math.sin(2 * math.pi * 2980 * t)
        click = (random.random() * 2 - 1) * math.exp(-t * 140.0)
        out.append((0.7 * metallic + 0.6 * click) * env)
    return out


def gen_fanfare() -> list[float]:
    """Fanfarria de Podio / Ronda Final (Acorde mayor arpegiado con brillo)."""
    dur = 1.65
    n = int(SAMPLE_RATE * dur)
    out = [0.0] * n
    notes = [
        (0.00, 523.25, 0.35),
        (0.11, 659.25, 0.35),
        (0.22, 783.99, 0.35),
        (0.34, 1046.50, 1.25),
        (0.34, 523.25, 1.25),
        (0.34, 783.99, 1.25),
    ]
    for start_s, freq, length_s in notes:
        start_i = int(start_s * SAMPLE_RATE)
        len_i = int(length_s * SAMPLE_RATE)
        for j in range(len_i):
            if start_i + j >= n:
                break
            t = j / SAMPLE_RATE
            attack = 1.0 - math.exp(-t * 85.0)
            decay = math.exp(-t * 2.8)
            tone = (
                0.60 * math.sin(2 * math.pi * freq * t)
                + 0.25 * math.sin(2 * math.pi * freq * 2 * t)
                + 0.15 * math.sin(2 * math.pi * freq * 3 * t)
            )
            out[start_i + j] += tone * attack * decay
    return out


if __name__ == "__main__":
    save_wav("game_sfx_buzzer.wav", gen_buzzer())
    save_wav("game_sfx_glass.wav", gen_glass())
    save_wav("game_sfx_heartbeat.wav", gen_heartbeat())
    save_wav("game_sfx_safe.wav", gen_safe_tick())
    save_wav("game_sfx_fanfare.wav", gen_fanfare())
