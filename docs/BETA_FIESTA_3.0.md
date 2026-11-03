# Vision+ Fiesta 3.0 — beta 2

**Fecha:** 2026-10-31  
**Estado:** beta local. **No es OTA y no se publicó en GitHub Releases.**  
**APK:** `beta/VisionPlus-Fiesta-3.0-beta3.apk` (95 MB, firma histórica `9593cb0d…`, SHA-256 `b6d7a8e98d9c6611df8bda2116f4c625649230a77a457108f6139bfae530338a`).  
**Servidor:** `server/api/fiesta_beta_server.py` (local, sin Postgres).

**Canal beta publicado:** release con tag
[`beta`](https://github.com/ChronosBVRX/Vision-/releases/tag/beta) y assets
`version.json` + `VisionPlus.apk` (**2.5.210/vc450**, 99,921,414 B, SHA-256
`367b8c5db0c01e0dd953fbe169a7bd374be9e0e109f9ac31e7ade1c7b35f57c6`). El switch
**Canal de pruebas (beta)** del celular y de la tele ya la detecta desde Ajustes →
Buscar actualizaciones. La estable `/releases/latest` sigue siendo **2.5.204**.
La beta local `beta3` queda para probar sin VPS.

**Novedades 2.5.210:** estadísticas del partido (posesión, tiros, tiros a puerta,
córners, faltas, tarjetas, atajadas y alineaciones de ESPN) en un panel a la
derecha, sobre los controles; el vivo ya no muestra botón de Pausa; la señal
seleccionada se marca con borde y etiqueta «Seleccionada»; Arriba/Abajo cambia solo
entre las señales disponibles de ese evento. En Juegos, el modo de prueba de un
solo jugador queda activado por defecto en la tele y la voz del host ya no se corta
al empezar otra frase.

**Entrega directa:** pre-release
[`v2.5.210-fiesta-beta8`](https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.210-fiesta-beta8)
con el APK `VisionPlus-2.5.210-fiesta-beta8.apk`. Incluye el backend de Fiesta
desplegado en el VPS y los cambios de Deportes.

---

## 1. Qué entra

Seis juegos nuevos, con motor propio, contenido original y casting nuevo:

| Juego | Mecánica | Host |
|---|---|---|
| **La Última Palabra** | Escribir respuesta, votar la más graciosa y final | Nora Vela |
| **Vox Populi** | Adivinar la respuesta más popular de una encuesta | Don Chisme |
| **Fuera de Época** | Elegir el año de un evento y mantener la racha | Profesor Tic Tac |
| **Texto Roto** | Escribir una línea de chat, mezclarla y votar | La Máquina |
| **Franela Fest** | Dibujar una camiseta, escribir eslogan y votar | El Crítico |
| **El Tasador** | Inventar un pitch, poner precio y votar | El Tasador |

Además:

- **Modo familiar** y **modo sin filtro** desde el selector de la tele.
- **Casting nuevo por juego**: 28 clips de voz generados con Qwen3-TTS VoiceDesign, timbres distintos, sin clonar personas reales.
- **Arte original** para las seis tarjetas (procedural, 16:9, sin texto).
- **Servidor autoritativo** para fases, timers, puntajes y filtrado de secretos.
- **Teléfono y tele**: el teléfono escribe/elige/dibuja/vota; la tele muestra el plató.
- **Contenido inicial**: 87 prompts de La Última Palabra, 28 encuestas, 46 eventos, 30 escenas, 25 camisetas y 30 objetos, con variantes adultas.

## 2. Cómo probarla

### 2.1 Levantar el servidor local

En este PC:

```bash
cd "/home/chronos/Escritorio/Vision+/repo/server/api"
../../.venv/bin/uvicorn fiesta_beta_server:app --host 0.0.0.0 --port 8100
```

La beta está compilada con:

```text
http://192.168.1.131:8100
```

Si la IP del PC cambia, hay que recompilar con:

```bash
./gradlew :app:assembleNormalRelease --offline \
  -PfiestsBaseUrl=http://NUEVA_IP:8100
```

y volver a inyectar el motor/firmar (ver §4).

Requisitos:

- PC y tele/teléfonos en la misma red.
- Puerto 8100 accesible desde la tele (firewall del PC).
- No hace falta el VPS ni Postgres para esta beta.

### 2.2 Instalar la beta

```bash
adb install -r beta/VisionPlus-Fiesta-3.0-beta3.apk
```

También se puede copiar el APK a la tele y abrirlo con un instalador. La firma es la histórica, así que actualiza sobre la app estable sin cambiar de paquete.

Para volver a la estable: instalar el APK estable/OTA vigente.

### 2.3 Jugar

1. Abrir **Vision+ → Juegos**.
2. Elegir **Familiar** o **Sin filtro** en la cabecera del selector.
3. Elegir uno de los seis juegos.
4. La tele muestra el código; los teléfonos entran con el código/QR.
5. La tele inicia la partida; los teléfonos escriben, eligen, dibujan y votan.

## 3. Qué se probó

- **Backend:** `606 passed` (`pytest -q server/tests`).
- **Android:** `1153 tests` en verde (`:app:testNormalDebugUnitTest`).
- **Smoke local HTTP:** los seis juegos crean sala, unen 3 jugadores, inician, reciben acciones y avanzan a voto/reveal.
- **Fugas:** `Fuera de Época` no expone el año correcto antes del reveal; `El Tasador` no expone el valor; `Franela Fest` oculta autor en la votación.
- **Firma:** histórica `9593cb0d…`; 5 DEX (2 propios + 3 del motor X4K).
- **APK:** `99.3 MB`, `classes3/4/5.dex` inyectados, recursos nuevos presentes.

## 4. Cómo se reconstruye la beta

```bash
# 1) Compilar release con el servidor local
./gradlew :app:assembleNormalRelease --offline \
  -PfiestsBaseUrl=http://192.168.1.131:8100

# 2) Inyectar el motor X4K (DEX del respaldo debug)
python3 tools/inject_engine_dex.py \
  app/build/outputs/apk/normal/release/app-normal-release.apk \
  beta/VisionPlus-Fiesta-3.0-beta3-unsigned.apk \
  app/src/debug/assets

# 3) Alinear
"$ANDROID_HOME/build-tools/35.0.0/zipalign" -p -f 4 \
  beta/VisionPlus-Fiesta-3.0-beta3-unsigned.apk \
  beta/VisionPlus-Fiesta-3.0-beta3-aligned.apk

# 4) Firmar con la llave histórica (keystore.properties)
#    Ver tools/publish_ota.ps1 o el bloque de firma usado en esta entrega.
```

## 5. Limitaciones de esta beta

- **No hay prueba física en Chromecast** en esta sesión: no había dispositivo ADB conectado. El smoke fue por HTTP y tests; falta jugar con 2-3 teléfonos reales.
- **La beta depende del PC**: si el servidor local no está corriendo, las salas no cargan.
- **Solo seis juegos nuevos en el selector beta**. Los juegos históricos siguen en la app estable; no se muestran aquí.
- **Contenido inicial**: hay suficientes prompts para varias partidas, pero falta ampliar a 200+ por juego y curar con audiencia real.
- **Voces**: 28 clips, una toma por momento; falta generar variantes y probar mezcla/volumen en TV.
- **Arte**: las seis tarjetas son procedurales; falta arte final con ComfyUI/retouch y tableros de tutorial por juego.
- **Purga física**: Charadas y Apuesta ya no están en el catálogo beta; se eliminaron sus artes/tutoriales únicos. Quedan audio y código histórico inerte para drenar salas viejas; la purga total de audio/código es el siguiente corte.
- **Modo sin filtro**: el filtro familiar bloquea groserías; el modo adulto permite lenguaje adulto, pero mantiene bloqueo de odio/acoso. Falta revisión editorial y legal.

## 6. Archivos principales

```text
server/api/fiesta.py                    motor de los seis juegos
server/api/fiesta_content.py            contenido original
server/api/fiesta_beta_server.py        servidor local sin Postgres
app/.../data/games/FiestaCatalog.kt     ids/casting
app/.../data/games/GameModels.kt        payload FiestaState
app/.../ui/games/FiestaPhoneScreen.kt   teléfono
app/.../ui/games/FiestaTvScreen.kt      tele
app/.../ui/games/FiestaVoice.kt         mapeo de voces
tools/generate_fiesta_voice.py          casting Qwen3-TTS
tools/make_fiesta_art.py                arte procedural
beta/VisionPlus-Fiesta-3.0-beta3.apk    APK beta
```

## 7. Siguiente corte

1. Probar en Chromecast con 2-3 teléfonos y corregir foco/timing/audio.
2. Ampliar contenido a 200+ prompts/juego y curar con personas.
3. Generar 3 variantes por línea de voz y mezclar.
4. Arte final y tutoriales por juego.
5. Purga completa de Charadas/Apuesta y assets muertos.
6. Solo después: OTA/release, con instrucción explícita.
