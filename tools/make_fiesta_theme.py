#!/usr/bin/env python3
"""Música original de Vision+ Fiesta: tema de fiesta y sting de entrada.

Sin samples ni licencias: todo sintetizado con numpy. El tema es un loop de
128 BPM con bombo four-on-the-floor, palmas, bajo, arpegio y stabs; el sting es
un riser corto para la presentación de la sección de juegos.
"""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app/src/main/res/raw"
SR = 44100
BPM = 128.0
BEAT = 60.0 / BPM
BAR = BEAT * 4


def t(duration: float) -> np.ndarray:
    return np.arange(int(duration * SR)) / SR


def sine(freq: float, duration: float, phase: float = 0.0) -> np.ndarray:
    return np.sin(2 * np.pi * freq * t(duration) + phase)


def saw(freq: float, duration: float) -> np.ndarray:
    x = (t(duration) * freq) % 1.0
    return 2.0 * x - 1.0


def square(freq: float, duration: float) -> np.ndarray:
    return np.sign(np.sin(2 * np.pi * freq * t(duration)))


def env(n: int, attack: float, decay: float, sustain: float = 0.7, release: float = 0.08) -> np.ndarray:
    a = max(1, int(attack * SR))
    d = max(1, int(decay * SR))
    r = max(1, int(release * SR))
    e = np.ones(n) * sustain
    e[:min(a, n)] = np.linspace(0, 1, min(a, n))
    if a + d < n:
        e[a:a + d] = np.linspace(1, sustain, d)
    if r < n:
        e[-r:] *= np.linspace(1, 0, r)
    return e


def add(buf: np.ndarray, at: float, sound: np.ndarray, gain: float = 1.0, pan: float = 0.0) -> None:
    start = int(at * SR)
    if start >= len(buf):
        return
    n = min(len(sound), len(buf) - start)
    if n <= 0:
        return
    left = 1.0 + pan
    right = 1.0 - pan
    buf[start:start + n, 0] += sound[:n] * gain * left
    buf[start:start + n, 1] += sound[:n] * gain * right


def kick(duration: float = 0.20) -> np.ndarray:
    time = t(duration)
    freq = 155 * np.exp(-time * 34) + 46
    tone = np.sin(2 * np.pi * np.cumsum(freq) / SR)
    click = np.random.default_rng(7).normal(0, 1, len(tone)) * np.exp(-time * 160) * 0.18
    return (tone * np.exp(-time * 18) + click) * 0.9


def clap(duration: float = 0.16) -> np.ndarray:
    rng = np.random.default_rng(11)
    tone = rng.normal(0, 1, int(duration * SR))
    e = np.exp(-t(duration) * 34)
    # pequeños rebotes de palma
    for delay in (0.012, 0.022, 0.033):
        idx = int(delay * SR)
        if idx < len(tone):
            tone[idx:idx + 200] += rng.normal(0, 0.6, min(200, len(tone) - idx))
    return (tone * e * 0.22).astype(np.float64)


def hat(duration: float = 0.06) -> np.ndarray:
    rng = np.random.default_rng(13)
    tone = rng.normal(0, 1, int(duration * SR)) * np.exp(-t(duration) * 90)
    return tone * 0.10


def bass_note(freq: float, duration: float) -> np.ndarray:
    time = t(duration)
    tone = 0.65 * sine(freq, duration) + 0.25 * saw(freq, duration) + 0.10 * square(freq * 0.5, duration)
    e = env(len(tone), 0.008, 0.08, 0.8, 0.05)
    return tone * e * 0.33


def pluck(freq: float, duration: float) -> np.ndarray:
    time = t(duration)
    tone = 0.55 * sine(freq, duration) + 0.25 * saw(freq * 2, duration) + 0.12 * sine(freq * 3, duration)
    e = np.exp(-time * 8.5) * env(len(time), 0.004, 0.03, 0.9, 0.03)
    return tone * e * 0.16


def pad(freqs: list[float], duration: float) -> np.ndarray:
    out = np.zeros(int(duration * SR))
    for freq in freqs:
        out += 0.12 * sine(freq, duration) + 0.04 * saw(freq * 1.003, duration)
    e = env(len(out), 0.12, 0.25, 0.65, 0.15)
    return out * e / max(len(freqs), 1)


def brass(freqs: list[float], duration: float) -> np.ndarray:
    out = np.zeros(int(duration * SR))
    for freq in freqs:
        out += 0.16 * saw(freq, duration) + 0.08 * square(freq * 0.5, duration)
    e = env(len(out), 0.01, 0.10, 0.55, 0.18)
    return out * e


def build_theme(bars: int = 16) -> np.ndarray:
    total = bars * BAR + 0.5
    buf = np.zeros((int(total * SR), 2))
    # Am - F - C - G, con melodía pentatónica.
    roots = [110.00, 87.31, 130.81, 98.00]
    chords = [
        [220.00, 261.63, 329.63, 493.88],
        [174.61, 220.00, 329.63, 392.00],
        [196.00, 261.63, 329.63, 523.25],
        [196.00, 246.94, 293.66, 440.00],
    ]
    arp = [
        [440.00, 523.25, 659.25, 523.25],
        [349.23, 440.00, 523.25, 440.00],
        [523.25, 659.25, 783.99, 659.25],
        [392.00, 493.88, 587.33, 493.88],
    ]
    for bar in range(bars):
        start = bar * BAR
        root = roots[bar % 4]
        chord = chords[bar % 4]
        arpeggio = arp[bar % 4]
        # cuatro bombos por compás
        for beat in range(4):
            add(buf, start + beat * BEAT, kick(), 0.72)
        # palmas en 2 y 4
        for beat in (1, 3):
            add(buf, start + beat * BEAT, clap(), 0.52)
        # hats en corcheas con acentos
        for eighth in range(8):
            at = start + eighth * BEAT / 2
            add(buf, at, hat(), 0.75 if eighth % 2 == 0 else 0.45, pan=0.18 if eighth % 2 else -0.18)
        # bajo en corcheas
        for eighth in range(8):
            note = root if eighth not in (3, 7) else root * 1.5
            add(buf, start + eighth * BEAT / 2, bass_note(note, BEAT * 0.42), 0.95)
        # pad y stab por compás
        add(buf, start, pad(chord, BAR * 0.94), 0.95, pan=0.0)
        add(buf, start, brass(chord, BEAT * 0.30), 0.75, pan=0.0)
        # arpegio de 16avos
        for step in range(16):
            note = arpeggio[step % 4] * (2 if step >= 8 and bar % 4 == 3 else 1)
            add(buf, start + step * BEAT / 4, pluck(note, BEAT * 0.21), 0.68, pan=0.25 if step % 2 else -0.25)
        # brillo en el último compás de cada 4
        if bar % 4 == 3:
            for step in (2, 6, 10, 14):
                add(buf, start + step * BEAT / 4, pluck(1046.50, BEAT * 0.30), 0.30, pan=0.35)
    # fundido de bucle: 0.3 s
    fade = int(0.3 * SR)
    ramp = np.linspace(0, 1, fade)
    buf[:fade] = buf[:fade] * ramp[:, None] + buf[-fade:] * (1 - ramp[:, None])
    buf = buf[:-fade]
    peak = float(np.max(np.abs(buf)))
    if peak > 0:
        buf = buf / peak * 0.32
    return buf.astype(np.float32)


def build_intro() -> np.ndarray:
    duration = 4.2
    buf = np.zeros((int(duration * SR), 2), dtype=np.float32)
    rng = np.random.default_rng(23)
    # riser de ruido filtrado
    n = int(3.4 * SR)
    noise = rng.normal(0, 1, n)
    # filtro paso alto simple
    filtered = np.diff(np.concatenate([[0.0], noise]))
    e = np.linspace(0.0, 1.0, n) ** 2.2
    riser = filtered * e * 0.12
    buf[:n, 0] += riser
    buf[:n, 1] += riser * 0.92
    # golpe final y acorde
    at = 3.4
    add(buf, at, kick(0.35), 1.0)
    add(buf, at, brass([220.0, 261.63, 329.63, 440.0], 1.2), 0.9)
    add(buf, at, pad([220.0, 329.63, 440.0], 1.3), 0.5)
    add(buf, at, clap(0.3), 0.5)
    peak = float(np.max(np.abs(buf)))
    if peak > 0:
        buf = buf / peak * 0.45
    return buf


def write_mp3(stereo: np.ndarray, out: Path, bitrate: str = "128k") -> None:
    import wave

    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    pcm = (np.clip(stereo, -1.0, 1.0) * 32767).astype(np.int16)
    with wave.open(wav_path, "wb") as wav:
        wav.setnchannels(2)
        wav.setsampwidth(2)
        wav.setframerate(SR)
        wav.writeframes(pcm.tobytes())
    partial = str(out) + ".part.mp3"
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", wav_path, "-ac", "2", "-ar", str(SR),
         "-codec:a", "libmp3lame", "-b:a", bitrate, partial],
        check=True,
    )
    Path(partial).replace(out)
    Path(wav_path).unlink(missing_ok=True)


def main() -> int:
    theme = build_theme()
    intro = build_intro()
    write_mp3(theme, RAW / "game_theme.mp3")
    write_mp3(intro, RAW / "game_intro.mp3")
    old = RAW / "game_ambient.mp3"
    if old.exists():
        old.unlink()
    print(f"tema: {len(theme) / SR:.1f}s · intro: {len(intro) / SR:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
