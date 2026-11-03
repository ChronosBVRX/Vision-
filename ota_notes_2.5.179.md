# Vision+ 2.5.179 (pruebas) — Vision+ Play en la tele + «Ahora en la tele»

**Canal beta. No es la OTA estable.** Esta versión convierte la tele en la
pantalla de Vision+ Play: crea la sala, enseña un QR grande y los teléfonos
llevan el ritmo de la partida. Además, el control remoto del teléfono ya muestra
lo que la tele reproduce, con avance y controles reales.

## Qué incluye

- **La tele es la anfitriona visual**: el riel de Smart TV suma **Juegos** y
  `visionplus://open/games` abre la pantalla. La tele crea una sala `display`,
  muestra el QR (`visionplus://play/join?code=ABC-234`), el código de 6
  caracteres, la lista de jugadores, las preguntas, el reveal y el podio.
- **El primer teléfono que entra es el anfitrión**. El host inicia la partida y
  avanza las rondas desde su teléfono; la tele no tiene botón de inicio.
- **Los teléfonos marcan el ritmo**: la pregunta no cambia por reloj; el
  servidor abre el reveal cuando **todos los jugadores conectados** han
  respondido. En el reveal cada teléfono pulsa **Continuar** y el host solo
  puede pasar a la siguiente ronda cuando **todos** están listos. Si alguien se
  desconecta, deja de bloquear tras el margen de desconexión del servidor.
- **Escáner QR en el teléfono** con ZXing; el código de 6 caracteres sigue
  disponible como respaldo. La cámara es opcional en el manifest.
- La pantalla de TV no reproduce video ni arranca el motor: solo pinta el
  juego y la narradora.
- **Narradora** y sonidos existentes se conservan en el teléfono.
- Kill-switch `games_enabled` (default true) sin cambios.

## Cómo probar

1. Abrir **Juegos** en el riel de la tele. Debe aparecer el QR y el código.
2. En el primer teléfono: **Juegos → Escanear QR de la tele** o escribir el
   código. Ese teléfono queda como **Anfitrión**.
3. Unir más teléfonos escaneando el mismo QR.
4. El anfitrión pulsa **Iniciar partida** en su teléfono.
5. Responder la pregunta en todos los teléfonos. La tele muestra cuántos han
   respondido. Al responder el último, aparece el resultado.
6. Cada teléfono pulsa **Continuar**; el anfitrión ve **Siguiente** habilitado
   solo cuando todos están listos. Al pulsarlo empieza la siguiente ronda.
7. Al final, la tele muestra el podio; **Nueva partida** crea otra sala.

## Control remoto: «Ahora en la tele»

- El teléfono muestra **póster, título, temporada/capítulo, minuto y duración**
  de lo que se reproduce en la tele.
- **Barra de progreso arrastrable**, play/pausa, ±10 s y capítulo
  anterior/siguiente.
- En vivo: **EN VIVO**, programa actual de la EPG y cambio de canal; sin barra
  de búsqueda.
- **Audio** y **Subtítulos** abren el selector nativo de la tele y se navegan
  con el D-pad del teléfono.
- **Barra flotante** en el resto de la app del móvil: tocar abre el control y el
  botón central pausa/reanuda.
- El estado viaja por **Messenger** entre `:x4k` y el proceso principal (no por
  broadcast), así que la barra no se congela en HyperOS/POCO.
- Funciona tanto con el motor nativo XTV como con ExoPlayer (deportes, M3U y
  descargas offline).

## Backend

- El backend ya está desplegado y verificado en el VPS: `playersPace` en salas
  `display`, host = primer teléfono, `POST /v1/game/rooms/{code}/ready`,
  `answeredCount`, `readyCount` y `allReady` en el snapshot.
- Humo público del flujo nuevo: **2 teléfonos, 1 ronda, espera de todos,
  bloqueo del «Siguiente» sin todos listos y podio**.
- 200 preguntas en el banco; `/v1/game/health`, `/v1/index` y
  `/v1/section/movies` responden 200.

## Evidencia

- Backend: **508 tests** (`pytest server/tests -q`), 0 fallos.
- Android: **1061 tests** (`:app:testNormalDebugUnitTest`), 0 fallos.
- Launcher: **153 tests**, 0 fallos (sin cambios).
- APK release R8 compila y conserva los DEX propios + los 3 del motor
  (`classes.dex`, `classes2.dex` + `classes3/4/5.dex`), sin colisiones de R8.

## Límites de esta fase

- Sin charadas ni apuestas (Fase 3).
- Sin packs de temporada ni ranking acumulado (Fase 4).
- La sala vive en memoria: si la API se reinicia, la app dice «la sala se
  cerró».
- Sin chat, cuentas, monedas ni premios; solo puntos.

## Firma

APK y launcher firmados con la llave histórica de producción
(`9593cb0d…`, `androiddebugkey`). No usar otra llave.

— ChronosBVRX
