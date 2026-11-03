# Vision+ Play — build para testers (Fase 2: tele + QR)

**No es una OTA estable.** Es la beta de pruebas de Vision+ Play con la tele como
pantalla. El APK y el launcher deben firmarse con la **llave histórica**
(`9593cb0d…`, `androiddebugkey`). No usar `visionplus-release.keystore`: rompería
la actualización de los equipos.

## Qué probar

1. En la **tele**: abrir **Juegos** en el riel (o `visionplus://open/games`).
   Debe crear la sala y mostrar un **QR grande** y el código `ABC-234`.
2. En el **primer teléfono**: **Juegos → Escanear QR de la tele** (o escribir el
   código). Ese teléfono queda como **Anfitrión** y es el único que puede
   iniciar y avanzar.
3. Unir más teléfonos escaneando el mismo QR. La tele va mostrando sus nombres.
4. El **anfitrión** pulsa **Iniciar partida** desde su teléfono; la tele no
   inicia nada.
5. Responder la pregunta en **todos** los teléfonos. La tele muestra
   «Respondidas X de Y»; no avanza por tiempo.
6. Al responder el último, aparece el resultado. Cada teléfono pulsa
   **Continuar**; el anfitrión ve **Siguiente** habilitado solo cuando todos
   están listos. Si falta alguien, el servidor responde «faltan jugadores por
   continuar».
7. Ver el podio en la tele y probar **Nueva partida**. El host puede reportar
   una pregunta desde su teléfono.

## Lo que NO entra todavía

- Charadas y apuestas (Fase 3).
- Packs de temporada, ranking acumulado y “¿Quién es más…?” (Fase 4).
- La sala vive en memoria: si la API se reinicia, la app dice «la sala se cerró».
- Sin chat, sin cuentas, sin premios; solo puntos.

## Backend

El backend **ya está desplegado y verificado** en el VPS: 200 preguntas,
`/v1/game/health` 200, salas `display` con `playersPace`, host = primer
teléfono, `POST /v1/game/rooms/{code}/ready`, y el humo nuevo con 2 teléfonos
(la pregunta espera a todos, el «Siguiente» se bloquea y el podio funciona).
La entrega **2.5.179 (vc419)** es la que lleva TV + QR y está preparada para
publicarse; la beta publicada sigue siendo la 2.5.178 (sin TV) y la OTA estable
sigue en 2.5.177. La publicación está en espera de la firma histórica y de los
otros cambios del usuario.

## Evidencia de esta entrega

- Backend: **508 tests** (`pytest server/tests -q`), 0 fallos.
- Android: **1048 tests** (`:app:testNormalDebugUnitTest`), 0 fallos.
- Launcher: **153 tests**, 0 fallos (sin cambios).
- Humo local y público del flujo TV: **2 teléfonos, 1 ronda, espera de todos,
  «Siguiente» bloqueado sin todos listos y podio**; 200 preguntas en el banco.
- Banco real: 200 preguntas generadas de 900 fichas del catálogo, 9 tipos.
