# Vision+ Play — Fase 4: juegos de fiesta asimétricos

**Estado:** implementado, desplegado en el VPS y validado con smoke remoto. **OTA estable 2.5.197 publicada; los juegos viven solo en la app principal.**
**Fecha:** 2026-10-30.

## Qué entra

La carta de Vision+ Play pasa de 3 a 7 juegos. Los cuatro nuevos usan el diseño
asimétrico: la tele muestra el estado público y la tensión; el teléfono guarda el
secreto, el rol, la mentira o la puja.

| Juego | Mecánica | Fases |
|---|---|---|
| **El Impostor** | Todos ven un lugar secreto menos uno. Preguntas rápidas por turnos y voto. Si lo atrapan, adivina el lugar para robar la ronda. | `secret → questions → vote → guess → reveal` |
| **Duelo de Dibujo Ciego** | Un artista recibe una frase absurda y dibuja vectores en su teléfono. La tele muestra el lienzo sin título; los demás inventan títulos y votan el real. | `draw → title → vote → reveal` |
| **Subasta Ciega** | Carta de botín con valor visible o misterio. Pujas secretas con monedas; el martillo cancela empates y aplica el efecto al ganador. | `auction → auction_reveal` |
| **Falso Conocimiento** | Dato insólito de cine/series. Cada jugador inventa una mentira; todos votan la que creen real. | `write → vote → reveal` |

Además:

- **Público / audiencia**: el join acepta `spectator=true`; puede entrar con la
  sala llena y votar en dibujo y Fibbage sin bloquear a los jugadores ni competir
  por puntos. La tele muestra el contador de público.
- **Momentos destacados**: al cerrar, el servidor calcula medallas automáticas
  («El más mentiroso», «El detective», «El tiburón de la subasta», «El camaleón»,
  «El más despistado») y viajan en el snapshot del podio.
- **Háptica**: el teléfono vibra al recibir rol secreto, al tocarle el turno, al
  empezar a dibujar, al revelar el impostor y en el martillo.
- **SFX de tele**: tic, voto, secreto, reveal, martillo y whoosh, sintetizados
  localmente (`tools/make_game_sfx.py`) y servidos por `GameSfx`.
- **Voz**: 16 momentos nuevos × 3 variantes (48 audios Qwen3-TTS) para impostor,
  dibujo, subasta y Fibbage.

## Contrato

- `POST /v1/game/rooms` acepta `game = impostor|dibujo|subasta|fibbage`.
- `POST /v1/game/rooms/{code}/join` acepta `spectator` (bool).
- `POST /v1/game/rooms/{code}/action` es la puerta única:
  - `impostor_advance`, `impostor_vote` (`target`), `impostor_guess` (`text`)
  - `draw_submit` (`strokes`), `draw_title` (`text`), `draw_vote` (`target`)
  - `auction_bid` (`bid`)
  - `fibbage_lie` (`text`), `fibbage_vote` (`target`)
- El cuerpo máximo sube a **64 KB** para los trazos vectoriales. Los trazos se
  guardan como coordenadas normalizadas 0..1000 (máximo 2,400 puntos), nunca como
  imágenes.
- El snapshot añade `impostor`, `drawing`, `auction`, `fibbage`, `audience` y
  `highlights` (podio), y `you.spectator/role/submitted/voted/bid/coins/artist`.
- El secreto se filtra por rol: la tele nunca recibe la palabra del impostor
  antes del reveal, ni el título real del dibujo antes de que exista la votación.

## Contenido local

- `server/api/game_decks.py`: 42 lugares del impostor con preguntas, 50 consignas
  de dibujo, 26 cartas de subasta y 30 datos de Fibbage. Sin base de datos y con
  barajado determinista por sala.
- Assets maestros en el disco grande:
  - `/media/chronos/Disco Duro/vision-assets/games/art` (portadas 16:9)
  - `/media/chronos/Disco Duro/vision-assets/games/sfx` (WAV)
  - `/media/chronos/Disco Duro/vision-assets/games/voice` (copia de los m4a)
- En la app: `game_{impostor,dibujo,subasta,fibbage}_art.webp`,
  `game_auc_card_*.webp`, `game_imp_role_*.webp`, `game_medal_*.webp`,
  `game_bg_tv_noir_*.webp`, `game_sfx_*.wav` y `narrator_<momento>_<0..2>.m4a`.
- Kit de salón: 15 WebP (~420 KB) con cartas, roles, medallas y fondos de TV.

## Regenerar assets

```bash
# Portadas (ComfyUI local en 8188; maestros en el disco grande)
python3 tools/comfy_game_art.py impostor dibujo subasta fibbage

# Efectos secos (numpy/ffmpeg del venv de TTS)
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
"$PY" tools/make_game_sfx.py

# Las 48 voces nuevas (Qwen3-TTS, 3 variantes por momento)
"$PY" tools/generate_narrator_voice.py \
  --only "impostor_secret,impostor_vote,impostor_caught,draw_start,draw_vote,auction_start,auction_reveal,fibbage_write,fibbage_vote" \
  --variants 3
```

`tools/generate_narrator_voice.py` compila los parciales en el disco grande y
solo mueve el `.m4a` final a `res/raw`, para que Android no vea nombres con
puntos extra durante una regeneración.

## Pruebas

- Backend: **542** en verde (`pytest`), incluidos `test_game_modes.py` (flujo
  completo de los cuatro juegos, empate de subasta, público y mazos).
- Android: **1136** en verde, incluidos `GameCatalogTest`,
  `GamePhase4ModelsTest`, `GameNarratorTest` y `GameWiringTest`.
- `:app:assembleNormalDebug` compila y empaqueta los assets.

## Verificación visual

El APK release se instaló en el Chromecast HD (`192.168.1.124`) y se abrió
`visionplus://open/games` con el mando: el selector muestra las siete tarjetas
con su arte, el foco blanco y el desplazamiento horizontal. Captura:
`docs/screenshots/games_fase4_selector.png`.

## Estado operativo

- Backend desplegado en el VPS el **2026-10-30**: `game_decks.py`, `games.py`,
  parche de `app.py` y `Dockerfile`; contenedor `api` reconstruido.
- `/v1/game/catalog` devuelve los 7 juegos y el smoke remoto validó salas,
  `draw_partial`, `auction_bid` y `fibbage_lie`.
- **OTA estable 2.5.197/vc437** publicada en GitHub
  (`https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.197`) e instalada en el Chromecast HD.
- **Vision+ Home 1.3.13/vc36** retira la pestaña Juegos: el acceso queda
  exclusivamente en la app principal. Incluido en el APK de la app y en
  `/media/chronos/Disco Duro/vision-assets/dist/`.

## Pendiente

- Prueba física completa con 2–4 teléfonos: dibujo táctil, vibración, voto del
  público, voz/SFX y QR.
- Publicar OTA pública: no se tocó la firma ni se subió a GitHub.
