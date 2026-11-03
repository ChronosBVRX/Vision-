# Vision+ 2.5.178 (pruebas) — Vision+ Play

**Canal beta. No es la OTA estable.** Esta versión entrega la primera fase de
Vision+ Play: trivia con árbitro en el VPS, salas por código de 6 caracteres y
narradora animada en el teléfono.

## Qué incluye

- Backend `/v1/game/*`: crear sala, unirse, reconectar, estado con sondeo corto
  (204 cuando no cambia), respuestas, reveal, podio, reportes y salud.
- Banco generado del catálogo: **200 preguntas** de 9 tipos (año, género,
  reparto, dirección, sinopsis, póster, saga, temporadas y traducción), con
  distractores disjuntos y rotación por grupo de continuidad.
- App móvil: pestaña **Juegos**, crear/unirse, lobby con código, 8 rondas,
  puntaje, podio, reporte de pregunta y reconexión.
- Narradora: robot flotante con el emblema oficial en el pecho, animación de
  4 fotogramas, avatar y 10 voces en español (Qwen3-TTS local). Se puede apagar y
  respeta el modo silencio.
- Deep link: `visionplus://play/join?code=ABC-234`.
- Kill-switch `games_enabled` (default true).

## Cómo probar

1. Abrir **Juegos** en el menú del teléfono.
2. Escribir un apodo y **Crear sala** (el anfitrión también juega).
3. En otros teléfonos: **Unirse** con el código `ABC-234`.
4. El anfitrión pulsa **Iniciar partida**.
5. Responder las 8 rondas; el servidor avanza solo. En el reveal se ve la
   respuesta correcta y los puntos; el anfitrión puede cortar la espera con
   **Siguiente**.
6. Ver el podio, probar **Reportar pregunta** y el interruptor de voz.

## Evidencia

- Backend: **506 tests** en verde.
- Android: **1038 tests**, 0 fallos.
- Launcher: **153 tests**, 0 fallos.
- Humo local y público: **8 jugadores · 8 rondas · podio**; 200 preguntas en el
  banco del VPS.
- `/healthz`, `/v1/game/health`, `/v1/index` y `/v1/section/movies` verificados
  en público.

## Límites de esta fase

- Sin pantalla/TV, QR ni tile del launcher (Fase 2).
- Sin charadas ni apuestas (Fase 3).
- Sin packs de temporada ni ranking (Fase 4).
- La sala vive en memoria: si la API se reinicia, la app dice «la sala se
  cerró».
- Sin chat, cuentas, monedas ni premios.

## Firma

APK y launcher firmados con la llave histórica de producción
(`9593cb0d…`, `androiddebugkey`). No usar otra llave.

— ChronosBVRX
