# Vision+ 2.5.195 — beta interna (2026-10-30)

Esta build **no se publicó** en GitHub. Es el paquete firmado para instalar en los
equipos de prueba (Chromecast HD `192.168.1.124` y teléfonos).

## Incluye

- Vision+ Play con 7 juegos: Trivia, Charadas, Apuesta a la pista, El Impostor,
  Duelo de Dibujo Ciego, Subasta Ciega y Falso Conocimiento.
- Impostor con preguntas dirigidas, empate → `guess`, 8 opciones y puntajes
  rebalanceados.
- Dibujo semi-vivo cada ~2.5 s, preview del 75 %, elección de 1 de 2 prompts,
  validación anti-spoiler y favorito del público.
- Subasta con pujas en múltiplos de 50, `double_next`, `shield`, robo del 25 %,
  liquidación de bóveda y reveal con «Continuar».
- Fibbage con decoys de casa, bono por mentira con 3+ votos y ronda final 2x.
- Público con urna separada y medallas (máximo 2 por jugador, «despistado»
  siempre evaluado).
- 15 assets de salón: cartas de subasta, roles, medallas y fondos de TV.
- 48 voces nuevas de Qwen3-TTS y SFX con ducking.
- `VIBRATE`, idempotencia de acciones y `phaseDuration` en el snapshot.

## Backend

Desplegado en el VPS el 2026-10-30:

- `games.py` `b8f85c22…`
- `game_decks.py` `e8d7446e…`
- `app.py` `18573e1f…`
- `Dockerfile` `e4a0dcf6…`

`/v1/game/catalog` devuelve los 7 juegos. Smoke remoto: creación de salas,
inicio, `draw_partial` visible para display, `auction_bid` y `fibbage_lie`.

## Artefacto

`/media/chronos/Disco Duro/vision-assets/dist/VisionPlus-2.5.195-beta.apk`

- `versionCode = 435`
- `versionName = 2.5.195`
- Firma histórica `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`
- SHA-256 `c6c0edf5a8dbbef30285681669962ff972f7b609bf967be4c33186e670534f9a`
- `version.json` local junto al APK.

## Pruebas

- 542 backend en verde.
- 1,136 Android en verde.
- `assembleNormalRelease` OK.
- Instalada y abierta en el Chromecast HD; selector de 7 juegos y lobby de El
  Impostor contra el VPS verificados.

## Pendiente

- Partida física completa con 2–3 teléfonos: dibujo táctil, voto del público,
  vibración, voz/SFX y QR.
- Publicar OTA pública cuando el dueño lo autorice.
