# Fiesta — voces Qwen3-TTS

**Motor:** Qwen3-TTS-12Hz-1.7B-VoiceDesign (casting) + Qwen3-TTS-12Hz-1.7B-Base
(clonación de referencia).  
**Veneno:** `/media/chronos/Disco Duro/vision-tts/venv/bin/python`  
**Salida:** `app/src/main/res/raw/fiesta_<juego>_<momento>_<0-4>.m4a`

## Pipeline

1. `generate_fiesta_voice_qwen.py` genera 5 tomas por momento con VoiceDesign,
   usando una descripción de personaje + una dirección actoral por momento
   (lobby, inicio, escritura, voto, reveal, podio).
2. `select_fiesta_voice_refs.py` elige una toma de presentación por host y la
   guarda como referencia WAV + transcripción.
3. `generate_fiesta_voice_clone.py` clona esa referencia en las 170 líneas con
   el modelo Base. Así cada juego tiene **una sola voz consistente**, no cinco
   impostores.
4. `qwen_voice_qc.py` mide f0, duración y clipping; si una toma se sale del
   registro del host (más de 3 semitonos), la regenera con la misma referencia.

## Casting

| Host | Juego | Referencia | Timbre |
|---|---|---|---|
| Nora Vela | La Última Palabra | `refs/nora.wav` | late-night, 31, cálida y pícara |
| Don Chisme | Vox Populi | `refs/chisme.wav` | radio de chismes, 46, tenor cercano |
| Profesor Tic Tac | Fuera de Época | `refs/tictac.wav` | documental, 58, pedante y pausado |
| La Máquina | Texto Roto | `refs/maquina.wav` | IA deadpan, glitches, inquietante |
| El Crítico | Franela Fest | `refs/critico.wav` | moda snob, teatral, afeminado |
| El Tasador | El Tasador | `refs/tasador.wav` | anticuario, 62, grave y misterioso |

## Comandos

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
export PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True

"$PY" tools/generate_fiesta_voice_qwen.py --force
"$PY" tools/select_fiesta_voice_refs.py
"$PY" tools/generate_fiesta_voice_clone.py --force
"$PY" tools/qwen_voice_qc.py
"$PY" tools/qwen_voice_qc.py --repair
python3 tools/fiesta_quality_gate.py
```

## Reglas

- No se clona a ninguna persona real: las referencias las genera el propio
  VoiceDesign a partir de una descripción original.
- Nada de Piper: el casting anterior se retiró.
- Cada toma lleva 0.18 s de silencio inicial para que el MediaPlayer de la tele
  no se coma la primera sílaba.
- Loudness: −16 LUFS, techo −2.5 dB, AAC 64 kbps, 48 kHz mono.
- Las 170 tomas deben pasar `fiesta_quality_gate.py` (duración, nivel y pico).
