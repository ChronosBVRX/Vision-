#!/usr/bin/env python3
"""Música y stingers originales para los seis juegos de Fiesta.

Sintetiza con la librería estándar (sin numpy) y exporta AAC con ffmpeg:

  res/raw/fiesta_music_<juego>.m4a    bucle de 24-32 s, estéreo, -18 LUFS
  res/raw/fiesta_sting_<juego>.m4a    sting de 2.5-3.5 s para el reveal

Cada juego tiene tempo, escala, instrumentación y mezcla distintos: show de
concurso, noticiero, laboratorio temporal, chat roto, pasarela y casa de
subastas. No usa samples de terceros.
"""

from __future__ import annotations

import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "raw"
SR = 44100
TAU = math.tau


def note(name: str) -> float:
    names = {"C": 0, "C#": 1, "D": 2, "D#": 3, "E": 4, "F": 5, "F#": 6,
             "G": 7, "G#": 8, "A": 9, "A#": 10, "B": 11}
    pitch, octave = name[:-1], int(name[-1])
    midi = 12 * (octave + 1) + names[pitch]
    return 440.0 * (2 ** ((midi - 69) / 12))


def env(t: float, attack: float, decay: float, sustain: float, release: float, length: float) -> float:
    if t < 0 or t > length:
        return 0.0
    if t < attack:
        return t / max(1e-6, attack)
    if t < attack + decay:
        return 1.0 - (1.0 - sustain) * (t - attack) / max(1e-6, decay)
    if t < length - release:
        return sustain
    return sustain * max(0.0, (length - t) / max(1e-6, release))


def osc(kind: str, phase: float) -> float:
    x = phase % TAU
    if kind == "sine":
        return math.sin(x)
    if kind == "tri":
        return 2.0 / math.pi * math.asin(math.sin(x))
    if kind == "saw":
        return 2.0 * (x / TAU) - 1.0
    if kind == "square":
        return 1.0 if x < math.pi else -1.0
    return math.sin(x)


class Track:
    def __init__(self, seconds: float):
        self.n = int(seconds * SR)
        self.left = [0.0] * self.n
        self.right = [0.0] * self.n

    def add(self, start: float, length: float, freq: float, amp: float,
            kind: str = "sine", pan: float = 0.0, attack=0.01, decay=0.12,
            sustain=0.6, release=0.18, detune=0.0):
        i0 = max(0, int(start * SR))
        i1 = min(self.n, int((start + length) * SR))
        lp = (1.0 - max(-1.0, min(1.0, pan))) * 0.5
        rp = 1.0 - lp
        for i in range(i0, i1):
            t = (i - i0) / SR
            e = env(t, attack, decay, sustain, release, length)
            if e <= 0:
                continue
            p = TAU * freq * t
            v = osc(kind, p) * 0.72 + osc(kind, p * (1 + detune)) * 0.20 + osc(kind, p * 0.5) * 0.08
            self.left[i] += v * amp * e * lp
            self.right[i] += v * amp * e * rp

    def noise(self, start: float, length: float, amp: float, pan: float = 0.0,
              seed: int = 1, lowpass: float = 0.35):
        i0 = max(0, int(start * SR))
        i1 = min(self.n, int((start + length) * SR))
        lp = (1.0 - pan) * 0.5
        rp = 1.0 - lp
        state = seed & 0x7FFFFFFF
        last = 0.0
        for i in range(i0, i1):
            state = (1103515245 * state + 12345) & 0x7FFFFFFF
            white = (state / 0x7FFFFFFF) * 2.0 - 1.0
            last = last * (1 - lowpass) + white * lowpass
            t = (i - i0) / SR
            e = env(t, 0.002, length * 0.6, 0.0, length * 0.3, length)
            self.left[i] += last * amp * e * lp
            self.right[i] += last * amp * e * rp

    def save(self, path: Path):
        peak = max(1e-6, max(max(abs(x) for x in self.left), max(abs(x) for x in self.right)))
        scale = 0.86 / peak
        with tempfile.TemporaryDirectory(prefix="fiesta_music_") as tmp:
            wav_path = Path(tmp) / "mix.wav"
            with wave.open(str(wav_path), "wb") as wf:
                wf.setnchannels(2)
                wf.setsampwidth(2)
                wf.setframerate(SR)
                frames = bytearray()
                for i in range(self.n):
                    l = int(max(-1, min(1, self.left[i] * scale)) * 32767)
                    r = int(max(-1, min(1, self.right[i] * scale)) * 32767)
                    frames += struct.pack("<hh", l, r)
                wf.writeframes(frames)
            path.parent.mkdir(parents=True, exist_ok=True)
            subprocess.run([
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-i", str(wav_path),
                "-af", "loudnorm=I=-18:TP=-3:LRA=9",
                "-ar", "44100", "-ac", "2", "-c:a", "aac", "-b:a", "80k",
                str(path),
            ], check=True)


def show(time: float = 32.0) -> Track:
    """La Última Palabra: concurso nocturno, metales sintéticos y aplausos."""
    t = Track(time)
    beat = 60 / 122
    chords = [("C4", "E4", "G4"), ("A3", "C4", "E4"), ("F3", "A3", "C4"), ("G3", "B3", "D4")]
    for bar in range(int(time / (beat * 4)) + 1):
        chord = chords[bar % 4]
        for index, name in enumerate(chord):
            t.add(bar * beat * 4, beat * 1.8, note(name), 0.10, "saw", (index - 1) * 0.35, detune=0.004)
        for step in range(8):
            if step % 2 == 0:
                t.add(bar * beat * 4 + step * beat / 2, beat * 0.32, note(chord[step % 3]) * 2,
                      0.055, "square", -0.2 if step % 4 else 0.2)
        t.add(bar * beat * 4, 0.22, 62, 0.30, "sine", 0.0, attack=0.002, decay=0.18, sustain=0.0, release=0.04)
        t.add(bar * beat * 4 + beat * 2, 0.18, 92, 0.22, "sine", 0.0, attack=0.002, decay=0.14, sustain=0.0)
        for off in (0.0, beat):
            t.noise(bar * beat * 4 + off, 0.07, 0.12, -0.4, seed=bar * 7 + int(off * 10), lowpass=0.8)
    # Aplausos lejanos.
    for i in range(120):
        t.noise(i * 0.23 + (i % 5) * 0.01, 0.045, 0.035, math.sin(i) * 0.6, seed=i + 3, lowpass=0.9)
    return t


def news(time: float = 28.0) -> Track:
    """Vox Populi: noticiero, bajo pulsante, teletipo y campanilla de aviso."""
    t = Track(time)
    beat = 60 / 108
    root = ["D3", "D3", "F3", "G3", "D3", "A3", "G3", "F3"]
    for bar in range(int(time / (beat * 2)) + 1):
        base = note(root[bar % len(root)])
        t.add(bar * beat * 2, beat * 1.1, base, 0.22, "tri", 0.0, attack=0.01, sustain=0.7)
        t.add(bar * beat * 2 + beat, 0.10, base * 2, 0.08, "square", 0.25)
        t.add(bar * beat * 2 + beat * 0.5, 0.06, 1400, 0.05, "sine", -0.3,
              attack=0.001, decay=0.04, sustain=0.0, release=0.02)
        if bar % 4 == 0:
            t.add(bar * beat * 2, 0.55, note("A5"), 0.09, "sine", 0.1, attack=0.005, decay=0.4)
    # Cinta de teletipo.
    for i in range(160):
        t.noise(i * 0.17, 0.022, 0.03, 0.5, seed=i + 20, lowpass=0.95)
    return t


def timelab(time: float = 30.0) -> Track:
    """Fuera de Época: relojes, pad etéreo y pulsos de máquina."""
    t = Track(time)
    beat = 60 / 88
    for i in range(int(time / beat) + 1):
        t.add(i * beat, 0.035, 2200, 0.05, "sine", -0.5, attack=0.001, decay=0.02, sustain=0.0, release=0.01)
        if i % 4 == 0:
            t.add(i * beat, 0.04, 1600, 0.07, "sine", 0.5, attack=0.001, decay=0.03, sustain=0.0)
    for bar in range(int(time / (beat * 4)) + 1):
        for name in ("D3", "A3", "F3", "C4"):
            t.add(bar * beat * 4, beat * 3.7, note(name), 0.055, "sine", 0.0,
                  attack=0.9, decay=0.5, sustain=0.8, release=1.0, detune=0.0035)
        t.add(bar * beat * 4, 0.35, 48, 0.18, "sine", 0.0, attack=0.01, decay=0.3, sustain=0.2)
    for i in range(40):
        t.noise(i * 0.7 + 0.2, 0.05, 0.025, math.sin(i * 2.1) * 0.7, seed=i + 90, lowpass=0.5)
    return t


def chat(time: float = 26.0) -> Track:
    """Texto Roto: notificaciones, bajo lo-fi y burbujas."""
    t = Track(time)
    beat = 60 / 96
    for bar in range(int(time / (beat * 4)) + 1):
        for step, name in enumerate(("E3", "G3", "B3", "D4")):
            t.add(bar * beat * 4 + step * beat, beat * 0.7, note(name), 0.09, "tri", -0.25,
                  attack=0.01, decay=0.16, sustain=0.35)
        t.add(bar * beat * 4, beat * 2, note("E2"), 0.16, "sine", 0.0, attack=0.02, sustain=0.6)
    for i in range(30):
        start = i * 0.82 + 0.12
        if i % 3 == 0:
            t.add(start, 0.12, 880, 0.10, "sine", 0.4, attack=0.003, decay=0.08, sustain=0.1)
            t.add(start + 0.14, 0.12, 1320, 0.08, "sine", 0.4, attack=0.003, decay=0.08, sustain=0.1)
        else:
            t.noise(start, 0.04, 0.03, -0.5, seed=i + 200, lowpass=0.75)
    return t


def runway(time: float = 30.0) -> Track:
    """Franela Fest: pasarela, cuatro por cuatro, flashes de cámara."""
    t = Track(time)
    beat = 60 / 126
    bass = ["F2", "F2", "C3", "G2", "F2", "A2", "C3", "G2"]
    for bar in range(int(time / beat) + 1):
        t.add(bar * beat, 0.16, 55, 0.30, "sine", 0.0, attack=0.002, decay=0.14, sustain=0.0)
        t.add(bar * beat, 0.05, 1800, 0.06, "square", -0.2, attack=0.001, decay=0.04, sustain=0.0)
        if bar % 2 == 0:
            t.add(bar * beat, beat * 0.9, note(bass[(bar // 2) % len(bass)]), 0.16, "saw", 0.0,
                  attack=0.01, sustain=0.7, detune=0.002)
        if bar % 4 == 2:
            for name in ("F4", "A4", "C5"):
                t.add(bar * beat, beat * 1.6, note(name), 0.055, "square", 0.3, attack=0.02, sustain=0.4)
    for i in range(24):
        t.noise(i * 0.9 + 0.3, 0.08, 0.09, math.sin(i) * 0.8, seed=i + 300, lowpass=0.95)
    return t


def auction(time: float = 30.0) -> Track:
    """El Tasador: vals lento, martillo, terciopelo y misterio."""
    t = Track(time)
    beat = 60 / 72
    for bar in range(int(time / (beat * 3)) + 1):
        for index, name in enumerate(("D3", "F3", "A3")):
            t.add(bar * beat * 3 + index * beat, beat * 1.0, note(name), 0.12, "tri", (index - 1) * 0.3,
                  attack=0.02, decay=0.2, sustain=0.55)
        t.add(bar * beat * 3, beat * 2.6, note("D2"), 0.18, "sine", 0.0, attack=0.03, sustain=0.7)
        if bar % 2 == 0:
            t.add(bar * beat * 3 + beat * 2, 0.10, 220, 0.20, "sine", 0.0,
                  attack=0.001, decay=0.09, sustain=0.0, release=0.02)
            t.noise(bar * beat * 3 + beat * 2, 0.10, 0.10, 0.0, seed=bar + 400, lowpass=0.35)
    for i in range(28):
        t.add(4 + i * 0.83, 0.012, 3200, 0.025, "sine", math.sin(i) * 0.5,
              attack=0.001, decay=0.008, sustain=0.0, release=0.004)
    return t


def sting(kind: str) -> Track:
    t = Track(3.2)
    if kind == "ultima_palabra":
        for i, name in enumerate(("C5", "E5", "G5", "C6")):
            t.add(i * 0.09, 0.7, note(name), 0.18, "saw", (i - 1.5) * 0.2, decay=0.3, sustain=0.5)
        t.noise(0.0, 0.3, 0.12, 0.0, seed=1, lowpass=0.7)
    elif kind == "vox_populi":
        for i, name in enumerate(("A4", "E5")):
            t.add(i * 0.12, 0.5, note(name), 0.20, "square", 0.0, decay=0.2)
        t.add(0.28, 0.7, note("A5"), 0.14, "sine", 0.2, decay=0.4)
    elif kind == "fuera_de_epoca":
        t.add(0.0, 1.4, note("D4"), 0.20, "sine", -0.2, attack=0.02, decay=1.2, sustain=0.1)
        t.add(0.15, 1.2, note("A4"), 0.16, "sine", 0.2, attack=0.02, decay=1.0, sustain=0.1)
        t.noise(0.0, 0.4, 0.10, 0.0, seed=2, lowpass=0.4)
    elif kind == "texto_roto":
        for i, freq in enumerate((880, 1174, 1568)):
            t.add(i * 0.11, 0.16, freq, 0.16, "sine", (i - 1) * 0.3, decay=0.1)
        t.noise(0.0, 0.12, 0.05, 0.0, seed=3, lowpass=0.9)
    elif kind == "franela_fest":
        t.noise(0.0, 0.12, 0.16, 0.0, seed=4, lowpass=0.95)
        for i, name in enumerate(("F5", "A5", "C6")):
            t.add(0.1 + i * 0.07, 0.55, note(name), 0.15, "square", 0.3, decay=0.25)
    else:
        t.add(0.0, 0.18, 180, 0.35, "sine", 0.0, attack=0.001, decay=0.16, sustain=0.0)
        t.noise(0.0, 0.16, 0.18, 0.0, seed=5, lowpass=0.3)
        t.add(0.22, 1.2, note("D3"), 0.18, "tri", 0.0, attack=0.02, decay=0.9, sustain=0.2)
    return t


BUILDERS = {
    "ultima_palabra": show,
    "vox_populi": news,
    "fuera_de_epoca": timelab,
    "texto_roto": chat,
    "franela_fest": runway,
    "el_tasador": auction,
}


def main():
    for game, builder in BUILDERS.items():
        music = OUT / f"fiesta_music_{game}.m4a"
        builder().save(music)
        print(f"[música] {music.name}")
        st = OUT / f"fiesta_sting_{game}.m4a"
        sting(game).save(st)
        print(f"[sting]  {st.name}")


if __name__ == "__main__":
    main()
