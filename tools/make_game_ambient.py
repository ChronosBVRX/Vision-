#!/usr/bin/env python3
"""Música ambiental tenue para Vision+ Play, 100 % local y sin samples externos.

Un pad cálido en bucle (Am9 – Fmaj9 – Cmaj9 – Gadd9) con bajo suave y un arpegio
esparso tipo calimba. Todo sintetizado con numpy: nada que licenciar, nada que
descargar. Diseñada para sonar bajito debajo de la presentadora en la tele.

Salida: `app/src/main/res/drawable-nodpi/` no; va a `app/src/main/res/raw/game_ambient.mp3`
(estéreo, 44.1 kHz, 96 kbps). Dura 36 s y el final empalma con el inicio.
"""

from __future__ import annotations

import subprocess
import tempfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/raw/game_ambient.mp3"
SR = 44100

# Progresión cálida en La menor (frecuencias en Hz).
CHORDS = [
    # (bajo, [notas del pad])
    (110.00, [220.00, 261.63, 329.63, 493.88]),   # Am9
    (87.31, [174.61, 220.00, 329.63, 392.00]),    # Fmaj9
    (130.81, [196.00, 261.63, 329.63, 587.33]),   # Cmaj9
    (98.00, [196.00, 246.94, 293.66, 440.00]),    # Gadd9
]
CHORD_SECONDS = 9.0
# Melodía esparsa (pentatónica de La menor), (segundo, nota, duración).
MELODY = [
    (2.0, 440.00, 1.6), (6.0, 523.25, 2.0),
    (11.0, 392.00, 1.8), (15.0, 329.63, 2.2),
    (20.0, 523.25, 1.6), (24.0, 587.33, 2.4),
    (29.0, 440.00, 1.8), (33.0, 329.63, 2.4),
]


def sine(freq: float, n: int) -> np.ndarray:
    phase = 2.0 * np.pi * freq * np.arange(n) / SR
    return np.sin(phase).astype(np.float64)


def adsr(n: int, attack: float, release: float) -> np.ndarray:
    env = np.ones(n)
    attack_n = min(n, int(attack * SR))
    release_n = min(n, int(release * SR))
    if attack_n > 0:
        env[:attack_n] = np.linspace(0.0, 1.0, attack_n) ** 1.5
    if release_n > 0:
        env[-release_n:] *= np.linspace(1.0, 0.0, release_n) ** 1.5
    return env


def main() -> int:
    total = int(len(CHORDS) * CHORD_SECONDS * SR)
    left = np.zeros(total)
    right = np.zeros(total)

    chord_n = int(CHORD_SECONDS * SR)
    for index, (bass, notes) in enumerate(CHORDS):
        start = index * chord_n
        # Pad: las notas con ataque y salida lentos, paneo alterno y un pelín
        # de desafinación entre canales para que suene ancho sin moverse.
        for voice, freq in enumerate(notes):
            tone_l = sine(freq * 0.998, chord_n)
            tone_r = sine(freq * 1.002, chord_n)
            env = adsr(chord_n, 3.0, 3.5)
            gain = 0.05 / len(notes)
            pan = 0.35 if voice % 2 == 0 else -0.35
            left[start:start + chord_n] += tone_l * env * gain * (1.0 + pan)
            right[start:start + chord_n] += tone_r * env * gain * (1.0 - pan)
        # Bajo: seno puro, apenas marcado, solo la primera mitad del acorde.
        bass_n = chord_n // 2
        bass_env = adsr(bass_n, 0.4, 2.5)
        bass_tone = sine(bass, bass_n) * bass_env * 0.055
        left[start:start + bass_n] += bass_tone
        right[start:start + bass_n] += bass_tone

    # Calimba: seno + armónico que decae rápido, muy bajita y con eco propio
    # (una repetición a los 0.45 s al 30 %).
    for at, freq, dur in MELODY:
        start = int(at * SR)
        n = min(int(dur * SR), total - start)
        if n <= 0:
            continue
        env = np.exp(-np.arange(n) / (SR * 0.9))
        pluck = (sine(freq, n) + 0.35 * sine(freq * 2.0, n)) * env * 0.045
        echo_at = start + int(0.45 * SR)
        echo_n = min(n, total - echo_at)
        left[start:start + n] += pluck * 1.1
        right[start:start + n] += pluck * 0.9
        if echo_n > 0:
            left[echo_at:echo_at + echo_n] += pluck[:echo_n] * 0.3
            right[echo_at:echo_at + echo_n] += pluck[:echo_n] * 0.3

    # Bucle sin clic: los últimos 6 s se funden con los primeros 6 s.
    fade = int(6.0 * SR)
    ramp = np.linspace(0.0, 1.0, fade)
    left[:fade] = left[:fade] * ramp + left[-fade:] * (1.0 - ramp)
    right[:fade] = right[:fade] * ramp + right[-fade:] * (1.0 - ramp)
    left = left[:-fade]
    right = right[:-fade]

    stereo = np.stack([left, right], axis=1)
    peak = np.abs(stereo).max()
    stereo = (stereo / max(peak, 1e-6) * 0.22).astype(np.float32)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    try:
        import soundfile as sf  # noqa: F401
        have_sf = True
    except ImportError:
        have_sf = False
    if have_sf:
        import soundfile as sf
        sf.write(wav_path, stereo, SR)
    else:
        import wave
        pcm = (np.clip(stereo, -1.0, 1.0) * 32767).astype(np.int16)
        with wave.open(wav_path, "wb") as wav:
            wav.setnchannels(2)
            wav.setsampwidth(2)
            wav.setframerate(SR)
            wav.writeframes(pcm.tobytes())
    partial = str(OUT) + ".part.mp3"
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", wav_path,
         "-ac", "2", "-ar", "44100", "-codec:a", "libmp3lame", "-b:a", "96k",
         partial],
        check=True,
    )
    Path(partial).replace(OUT)
    Path(wav_path).unlink(missing_ok=True)
    print(f"ambiente listo: {OUT.name} · {OUT.stat().st_size / 1024:.0f} KB · "
          f"{stereo.shape[0] / SR:.0f} s en bucle")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
