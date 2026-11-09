# Narración en español mexicano

## Criterio

La voz de Vision+ Party no es locutora de noticiero ni doblaje neutro. Es una
narradora mexicana de fiesta: acento cerrado, ritmo rápido, ironía y complicidad.
Cada momento tiene **10 textos distintos y 10 audios distintos** para que una
sesión no repita el mismo chiste.

## Motor y referencias

- Motor: `Qwen3-TTS-12Hz-1.7B-Base` en local.
- Referencia femenina: `vision-assets/party-games/voice-refs/f1.wav` (casting F1).
- Referencia masculina: `vision-assets/party-games/voice-refs/m1.wav` (casting M1).
- Los maestros de voz viven en el HDD externo, no en el SSD del proyecto.
- Modo: clonación ICL con el texto de la referencia para conservar acento y
  prosodia, no solo el timbre.
- No se usa Edge, Piper ni ninguna API en tiempo de ejecución. El host solo
  sirve archivos `.m4a`.

## Excepción empaquetada en el APK
### Recorte seguro de la bienvenida

Las tomas de bienvenida usan un recorte conservador (`top_db=50`) y conservan 0.35 s antes
del primer intervalo, además de 0.5 s de silencio inicial. Así la TV no se come la primera
consonante cuando el audio arranca. No volver a usar `top_db=38` para estas tomas.


La **bienvenida de la sección Juegos** es la única narración que viaja dentro del APK
(`app/src/main/res/raw/party_welcome_0..9.m4a`). La transición es local para no depender de
la red. Si se cambian esas tomas, hace falta publicar OTA; el resto de la narración se
actualiza desde el host sin APK nuevo.

## Cobertura

| Bloque | Momentos | Variantes | Audios |
|---|---:|---:|---:|
| 10 juegos base | 40 | 10 | 400 |
| Banterbolt | 9 | 10 | 90 |
| El Comisionado Merk | 7 | 10 | 70 |
| ¿Le atinaste? Bien. | 6 | 10 | 60 |
| Selección de juego | 1 | 10 | 10 |
| **Total** | **63** | **10** | **630** |

## Formato

- AAC mono, 48 kHz, 64 kbps.
- Loudness cercano a −16 LUFS; techo con margen para evitar clipping.
- 0.18 s de silencio inicial para que el televisor no se coma la primera sílaba.
- Duración objetivo: 1–14 s.

## Rotación

- Los 10 juegos base usan una bolsa mezclada por juego y momento: se agotan las
  10 variantes antes de repetir.
- Banterbolt, Merk y ¿Le atinaste? usan un desplazamiento aleatorio por sesión y
  avanzan de una en una.
- El archivo se elige por nombre: `narrator_<momento>_<0-9>.m4a`.

## Generar

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
"$PY" party/tools/generate_party_voice_mx.py --force --batch 10
```

Para un solo juego:

```bash
"$PY" party/tools/generate_party_voice_mx.py --game pantomime --force
```

## Auditar

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
"$PY" party/tools/audit_party_audio.py
"$PY" party/tools/audit_party_audio.py --asr --json /tmp/opencode/audio-audit.json
# o el atajo:
./party/tools/test_party_audio.sh

# Si la auditoría marca loudness o picos:
"$PY" party/tools/normalize_party_audio.py --audit /tmp/opencode/audio-audit.json
```

La auditoría revisa:

- 10 archivos y 10 textos únicos por momento.
- AAC mono 48 kHz y duración.
- Loudness, pico real, saturación y silencios de entrada/salida.
- Duplicados exactos por hash.
- Consistencia de tono (f0) por voz y momento.
- Transcripción con Whisper y WER contra el guion.

## Resultado de la auditoría

- **630/630** archivos presentes y con formato AAC mono 48 kHz.
- **0 fallas** de duración, loudness, pico, silencio, duplicados, léxico o tono.
- Pico máximo medido: **−1.3 dBFS**; loudness entre **−18.5 y −14.9 LUFS**.
- **630/630** transcripciones dentro del umbral; WER promedio **0.024** y máximo **0.429**.
- **Cero** transcripciones con “Mínica”; las tomas mal pronunciadas se regeneraron.
- Las tomas se generaron primero en el HDD y luego se instalaron en el repo; el audio viejo se eliminó.
- Host desplegado en `https://party.visionplusv2.duckdns.org/`: 630 `.m4a`
  servidos con `audio/mp4`; smoke de juegos **13/13**.
- Evidencia: `party/docs/audio_audit_2026-11-07.json` (630 entradas con
  duración, LUFS, pico, WER y transcripción).
- Los clips que quedaron fuera de loudness se normalizaron con
  `party/tools/normalize_party_audio.py`.
- Los clips con tono fuera de registro o dicción dudosa se regeneraron y se
  eligieron por WER con Whisper.

## Pendiente

- Escucha física con 3 teléfonos y televisor para validar timbre, volumen y
  sincronía con la escena. Las pruebas automáticas no sustituyen el oído.
