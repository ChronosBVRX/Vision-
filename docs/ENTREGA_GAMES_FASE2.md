# Entrega Vision+ Play — Fase 2 (tele + QR + ritmo de los teléfonos)

Estado: **código listo, backend desplegado, beta 2.5.179 publicada y firmada**
con la llave histórica. No se commiteó nada.

## Qué cambió

- **La tele crea la sala** (`mode=display`) en `ui/games/TvGamesScreen.kt` y
  enseña QR (`GameQr.kt`), código, jugadores, pregunta, reveal y podio. La tele
  no inicia, no avanza y no arranca el motor nativo.
- **El primer teléfono que entra es el anfitrión**. El servidor asigna
  `host_id` al primer jugador también en `display`; su token de jugador sirve
  para `start`/`next`. La tele conserva su `hostToken` solo para crear/cerrar.
- **Los móviles marcan el ritmo**:
  - La pregunta no caduca por reloj. El servidor pasa a `reveal` cuando todos
    los jugadores **conectados** han respondido (`_all_answered`).
  - En el reveal cada teléfono pulsa **Continuar** (`POST .../ready`). El
    anfitrión solo puede avanzar cuando todos los conectados están listos
    (`allReady`); si falta alguien, `host/next` responde 409
    «faltan jugadores por continuar».
  - `playersPace=true` desactiva los temporizadores de pregunta/reveal de las
    salas `display`. Las salas `remote` conservan el ritmo automático.
  - Los desconectados dejan de bloquear tras `DISCONNECT_SECONDS` (90 s) y el
    anfitrión pasa al más antiguo conectado si el host se cae.
- **Escáner QR móvil** con `com.journeyapps:zxing-android-embedded:4.3.0` +
  `com.google.zxing:core:3.5.3`; el código de 6 caracteres sigue como respaldo.
- **TV**: nueva entrada «Juegos» en el riel (`TvApp.kt`) y
  `visionplus://open/games` (`MainActivity.kt`). Sin botón de inicio en la tele.
- **Snapshot** añade `playersPace`, `answeredCount`, `readyCount`, `allReady`,
  `players[].ready` y `you.ready`.

## Backend desplegado

- Archivos: `server/api/games.py` y `server/api/app.py`.
- VPS `159.54.146.146:/opt/streaming-backend`, contenedor `streaming-api`
  reconstruido el 2026-10-27. Respaldos `api/*.bak-fase2-*`.
- Hashes desplegados: `games.py` `ebdecca4…cd6f0`, `app.py` `3e38e41c…14498`.
- Verificado en público:
  - `GET /v1/game/health` → 200, 200 preguntas, 0 salas.
  - Humo del flujo nuevo en el VPS: sala `display`, 2 teléfonos, host = Ana,
    `start` con el token de Ana, la pregunta espera a Beto, el reveal no avanza
    solo, `next` bloqueado sin los dos `ready`, y podio al final.
- No hubo cambio de esquema (el `ready` vive en memoria como el resto de la
  sala).

## Pruebas

- Backend: **pytest completo** (ver cifra final en las notas de la beta).
- Android: `:app:testNormalDebugUnitTest` **1048 tests, 0 fallos**.
- Launcher: `:launcher:testReleaseUnitTest` **153 tests, 0 fallos**.
- Release R8: `:app:assembleNormalRelease` compila; `verifyEngineNamespace`
  sin colisiones propias. Inyección del motor con
  `tools/inject_engine_dex.py`: `classes.dex`/`classes2.dex` propios y
  `classes3/4/5.dex` del motor; `zipalign -p -f 4` correcto.
- APK alineado sin firmar:
  `/tmp/opencode/VisionPlus-2.5.179-aligned.apk` (84,266,249 B).

## Publicación realizada (beta 2.5.179)

- Release: https://github.com/ChronosBVRX/Vision-/releases/tag/beta
- `VisionPlus.apk`: **84,449,920 B**, SHA-256 `b6c95720397f637343a73ddb7b48f2fc40af1037ef634c1a0fd6cb77e0839c86`
- `VisionPlusHome.apk` (1.3.1/vc24): **1,516,015 B**, SHA-256 `3456af7f8fc1b3edabfa036795c666d1f6e0fc2b0170e53e95c6d2590cb0b51e`
- `version.json`: 2.5.179/vc419, 4,496 B, SHA-256 `4bd7a5fe0a9eb39b6554e6d4acd25a1a5893680b83f3e948a131df685ee5d6e5`
- Los dos APK verificados con `apksigner`: certificado histórico
  `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`.
- `/releases/latest` sigue en **v2.5.177**; la beta es prerelease y no se
  ofrece a los usuarios normales.
- La llave temporal se borró del VPS y de Linux con `shred -u`; no se tocó la
  copia local original.

No usar `visionplus-release.keystore` (rompería las actualizaciones).

## Límites / pendientes

- Falta la prueba física con una tele y dos teléfonos reales (QR, cámara,
  espera de todos y avance del host). El emulador no estaba disponible.
- La sala vive en memoria: un reinicio de la API la cierra.
- Sin charadas/apuestas (Fase 3) ni packs/ranking (Fase 4).
