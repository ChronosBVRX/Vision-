# Narradora de Vision+ Play

Personaje-animación del módulo de juegos. Es un robot flotante, amable y
familiar; lleva **el emblema oficial de Vision+** en el pecho (el mismo
`ic_vision_mark.png` de la app, no una versión redibujada). No habla con texto
en pantalla: usa un bocadillo corto y una voz grabada.

## Assets

| Asset | Uso |
|---|---|
| `res/drawable-nodpi/narrator_idle_0..3.png` | 4 fotogramas del flotado (bucle lento) |
| `res/drawable-nodpi/narrator_static.png` | Pose fija para efectos reducidos o la sala |
| `res/drawable-nodpi/narrator_avatar.png` | Cabeza recortada para la esquina de la sala |
| `res/drawable-nodpi/narrator_welcome.png` | Pose de bienvenida (mano levantada) |
| `res/drawable-nodpi/narrator_thinking.png` | Pose pensativa (pista / turno) |
| `res/drawable-nodpi/narrator_happy.png` | Pose de acierto |
| `res/drawable-nodpi/narrator_oops.png` | Pose de fallo / pase |
| `res/drawable-nodpi/narrator_celebrate.png` | Pose de podio / apuesta ganada |
| `res/drawable-nodpi/game_*_art.webp` | Arte 16:9 de las siete tarjetas (trivia, charadas, wager, impostor, dibujo, subasta, fibbage) |
| `res/raw/narrator_<id>_<0-9>.m4a` | 10 variantes por momento clásico y 3 por los dieciséis momentos de Fase 4 |
| `res/raw/game_sfx_*.wav` | Tic, voto, secreto, reveal, martillo y whoosh |
| `res/raw/game_ambient.mp3` | Música ambiental en bucle (30 s) |

El maestro transparente está en `docs/assets/games/narrator_master.png` y el
grafo exacto de ComfyUI en `docs/assets/games/narrator_workflow.json`.

## Cómo se generó (ComfyUI local)

- ComfyUI 0.36, SDXL **Juggernaut XL v9** (`juggernautXL_v9Rundiffusion.safetensors`).
- 1024×1024, 30 pasos, CFG 6.5, `dpmpp_2m` + `karras`, semilla **777002**.
- Fondo de estudio plano para recortar; recorte con `rembg` (u2net).
- El emblema oficial se compone en el pecho con Pillow (círculo de vidrio,
  borde cian y halo azul) y de ahí salen los 4 fotogramas de 512×512.
- Regeneración: `tools/comfy_narrator.py` (ver cabecera del script). Requiere el
  ComfyUI local y el venv `~/.venvs/visionplus-assets` con `rembg`, `onnxruntime`
  y `pillow`.
- Poses por momento: `tools/make_narrator_poses.py` parte del maestro original,
  separa los brazos con una máscara poligonal y los gira alrededor del hombro. Así
  la cara, el cuerpo y el emblema quedan idénticos y la silueta cambia de verdad.
  La prueba de img2img y ControlNet quedó descartada porque cambiaba al personaje
  (el emblema terminaba en la cara); los maestros finales están en
  `docs/assets/games/narrator_poses/`.
- Arte de las tarjetas: `tools/comfy_game_art.py` genera los tres fondos 16:9 y los
  convierte a WebP (`game_*_art.webp`).

## Voz (Qwen3-TTS)

- `Qwen3-TTS-12Hz-1.7B-Base` (Apache 2.0), GPU local (RTX 3060 de 12 GB),
  clonando `dalia.wav` (voz femenina mexicana, 8.5 s) como timbre. La
  referencia **no** se versiona; se pasa por `NARRATOR_REF`.
- Instalación aislada en el disco grande, fuera de `/`:
  `/media/chronos/Disco Duro/vision-tts/` (`venv/`, `models/`, `ref/`, `out/`).
  El peso del modelo y la caché no viven en el disco del sistema.
- Por cada línea se generan **6 tomas** en un solo lote y se elige la mejor por
  rango tonal (semitonos), energía y ritmo; se descartan las truncadas, las que
  no respetan la duración esperada y las que Whisper transcribe mal. No hay
  `exaggeration` ni velocidad artificial: la expresividad sale del modelo y de
  la selección.
- La app reproduce a **velocidad natural** (`NarratorAudio.VOICE_SPEED = 1.0`);
  el modelo ya entrega el ritmo y la dinámica de la toma elegida.
- Salida AAC-LC mono 44.1 kHz, 48 kbps (`.m4a`), normalizada a −16 LUFS. Pesa
  la mitad que el MP3 de 96 kbps y Android la reproduce con `MediaPlayer`. Cada regeneración
  se verifica por duración (una línea de ~60 caracteres ≈ 3–6 s; si sale >12 s
  es repetición y se regenera esa línea).
- Regeneración: `tools/generate_narrator_voice.py --force` con
  `/media/chronos/Disco Duro/vision-tts/venv/bin/python` (ver cabecera del
  script; `--pilot` hace 5 momentos de prueba y `--audit` revisa los MP3).

### Instalación (una vez)

```bash
BASE="/media/chronos/Disco Duro/vision-tts"
python3 -m venv --copies "$BASE/venv"
"$BASE/venv/bin/pip" install -U pip
"$BASE/venv/bin/pip" install qwen-tts faster-whisper   # fija transformers 4.57.3
HF_HOME="$BASE/hf" "$BASE/venv/bin/hf" download \
  Qwen/Qwen3-TTS-12Hz-1.7B-Base --local-dir "$BASE/models/Qwen3-TTS-12Hz-1.7B-Base"
```

El transcript de la referencia se cachea en `$BASE/ref/dalia.txt` (se obtiene
con `faster-whisper-base` si falta). El modelo vive fuera de `/` a propósito:
los pesos ocupan ~4.3 GB y el disco del sistema está justo de espacio.

## Música ambiental

- `res/raw/game_ambient.mp3`: pad cálido en bucle de 30 s (Am9–Fmaj9–Cmaj9–Gadd9)
  con bajo suave y arpegio esparso, 100 % sintetizado con numpy
  (`tools/make_game_ambient.py`, sin samples ni licencias), pico −14 dB para
  quedar debajo de la voz.
- `GameAmbient` (en `data/games/NarratorAudio.kt`): bucle en la tele con
  `isLooping`, volumen 0.14 que baja a 0.05 mientras habla la presentadora y
  vuelve al terminar. Mismo destino e interruptor que la voz. Arranca al entrar
  a Juegos y se detiene al salir.

Diálogos (`tools/narrator_lines.json`): 20 momentos × 10 variantes = 200 líneas
con humor mexicano ligero, sin groserías. Cada momento elige una variante
distinta a la anterior (`GameNarrator.cue`), y el bocadillo en pantalla usa la
misma variante que la voz (`rememberNarratorVariant` / `rememberNarratorText`):
nunca dicen cosas distintas. Los bocadillos con datos vivos (aciertos, números
de pista, nombres) se quedan dinámicos porque ya varían solos.

Fase 4 suma dieciséis momentos × 3 variantes (48 audios): `impostor_secret`,
`impostor_vote`, `impostor_caught`, `draw_start`, `draw_vote`, `auction_start`,
`auction_reveal`, `fibbage_write`, `fibbage_vote`, `impostor_questions`,
`impostor_escaped`, `party_timeout`, `auction_tie`, `lie_sweep`,
`nobody_guessed` y `final_round_double`. Se generaron con
`--only "…" --variants 3` y el mismo control de calidad (6 tomas, selección por
rango tonal/energía/ritmo, WER y duración). La copia maestra vive en
`/media/chronos/Disco Duro/vision-assets/games/voice`.

Los efectos secos (`game_sfx_*.wav`) se sintetizan con numpy en
`tools/make_game_sfx.py` y se reproducen solo en la tele con `GameSfx`, con el
mismo interruptor de voz. No usan samples ni licencias externas.

## Comportamiento en la app

- En **Juegos** se anima el flotado de 4 fotogramas (~5 fps) con
  `LocalReducedEffects` como interruptor: en equipos modestos o con efectos
  reducidos se queda la pose fija.
- En la **sala** se usa la pose del momento (`welcome`, `thinking`, `happy`,
  `oops`, `celebrate`) con un flotado suave, luz de estudio y el bocadillo
  escrito en tiempo real; no hay bucle de fotogramas para no competir con el
  reloj ni con el motor.
- La voz suena **en la tele**, que es la pantalla compartida; los teléfonos quedan
  en silencio y solo muestran el bocadillo. La tele usa su propio volumen y no
  depende del modo silencio de un teléfono.
- El tono es mexicano y ligero («¡Chido!», «ni modo», «invita los tacos»), sin
  groserías ni albures.
- Sin emojis y sin chat: solo reacciones predefinidas.

## Nombre

La UI y las frases la llaman «la narradora» para no cerrar una decisión de
producto. Si el dueño quiere un nombre propio, la propuesta es **Vivi** (de
Vision+); cambiarlo es solo regenerar las frases y el copy, no toca el motor.
