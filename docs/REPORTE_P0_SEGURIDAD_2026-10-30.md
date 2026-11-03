# Vision+ Play — Reporte P0 de seguridad e integridad

**Fecha:** 30 de octubre de 2026  
**Alcance:** Etapa A de la instrucción Vision+ Play 3.0: auditoría, protección de secretos, liquidación de puntos, reglas v1/v2 y primeras correcciones P1.  
**Estado:** cambios implementados y probados. **OTA 2.5.201/vc441 publicada** y **backend desplegado** en el VPS con respaldos. La app no se pudo instalar en el Chromecast HD porque el ADB estaba cerrado; la actualización queda disponible por OTA para la tele.

---

## 1. Resumen

Se corrigieron los hallazgos P0 de la auditoría externa:

1. El espectador de El Impostor ya no recibe la palabra secreta.
2. La URL del póster de Apuesta ya no viaja a los jugadores antes de la etapa 3 o el reveal.
3. Charadas liquida al actor una sola vez.
4. La adivinanza temprana de Dibujo ya no gana con stopwords.
5. El límite de tinta de Dibujo se aplica en el servidor.
6. Las mecánicas v2 ya no se activan en salas v1.

También se corrigieron:

- La rotación de preguntas de Trivia: la TV manda un `X-Sync-Code` persistente.
- El autoborrado de preguntas por reportes.
- La puja de Subasta por debajo del saldo/mínimo.
- El voto de opciones tachadas por el escáner de Fibbage.
- La respuesta de espectadores en Trivia.
- El límite de espectadores.
- La autenticación del snapshot de display de la TV.
- Los objetivos de ataque y apuestas laterales en salas de 8.
- La idempotencia de acciones rechazadas.
- La UI de v1 en Charadas, Dibujo, Subasta y Fibbage.

---

## 2. Cambios por archivo

### Servidor

- `server/api/games.py`
  - `MAX_SPECTATORS = 8`.
  - `EARLY_GUESS_STOPWORDS`.
  - `reports_by_player` por sala.
  - `_begin_impostor()`: cómplice/detective solo en v2.
  - `_begin_drawing()`: tinta/linterna/opciones de consigna solo en v2.
  - `_finish_charada_buzz()`: dejó de pagar al actor; el pago ocurre una vez en `_begin_reveal()`.
  - `draw_early_guess()`: exige dibujo iniciado, palabras completas y al menos una palabra de contenido; rechaza stopwords.
  - `draw_partial()` y `draw_submit()`: rechazan `"tinta"` si se supera el límite.
  - `act()`: nueva envoltura de idempotencia que solo memoriza acciones aceptadas; un rechazo ya no se convierte en `True` al reintentar.
  - `auction_bid`: la puja se limita al saldo después de redondear; con menos de 50 monedas no hay puja positiva.
  - `auction_ghost`, `draw_pick_prompt`, ayudas/timbre de Charadas, escáner/caza/certeza de Fibbage: bloqueados en v1.
  - `fibbage_vote`: rechaza opciones tachadas por el escáner y `vote=true` en v1.
  - `answer()` y wager legacy: rechazan espectadores.
  - `report()`: deduplicado por jugador.
  - Snapshot de Impostor: `can_see_word` excluye espectadores, rol `audience`.
  - Snapshot de Apuesta: póster visible para display, etapa 3 o reveal; vacío para jugadores en etapas 1–2.
  - Snapshot de Dibujo: `promptOptions` solo v2.
  - `MAX_SPECTATORS` aplicado en `add_player`.

- `server/api/app.py`
  - `GET /state?role=display` usa `X-Game-Host` (token de la TV) para autorizar el póster; una TV vieja sigue leyendo el estado público, pero sin ese póster.
  - `game_report` usa rate limit, deduplica por jugador y **ya no borra** preguntas; devuelve `flagged` para revisión editorial.

### Android

- `GameClient.kt`
  - `state(..., role, hostToken)` envía `X-Game-Host` cuando el rol es `display`.

- `TvGamesScreen.kt`
  - El polling de display pasa el host token.
  - La creación de sala manda `syncCode` persistente de `SyncStore`.
  - El `ensureCode` se ejecuta en el hilo principal antes del `withContext(IO)`.

- `GameModesScreen.kt`
  - Espectador de Impostor ve tarjeta de público, no la palabra.
  - v1 oculta falsa alarma de Subasta, veneno/escáner/certeza de Fibbage, adivinanza temprana y críticos de Dibujo.
  - El envío de dibujo sobre tinta muestra error.
  - La corazonada antes del dibujo muestra error.

- `GameRoomScreen.kt`
  - v1 oculta ayudas y timbre de Charadas.

- `GameV2Screens.kt`
  - Todos los objetivos de ataque de Trivia se muestran en filas de 4.
  - Todos los objetivos de apuesta lateral se muestran en filas de 3.

### Pruebas

- Nuevo `server/tests/test_games_security.py` con 15 pruebas de:
  - espectador de Impostor,
  - póster de Apuesta,
  - pago único de Charadas,
  - stopwords de Dibujo,
  - tinta en servidor,
  - v1 de Impostor, Dibujo, Charadas, Subasta y Fibbage,
  - puja bajo saldo,
  - escáner de Fibbage,
  - espectador en Trivia,
  - tope de espectadores,
  - deduplicación de reportes.
- Ajustes a `test_games_v2.py`, `test_game_modes.py` y `test_games_api.py` para separar v1/v2 y cubrir las nuevas reglas.

---

## 3. Evidencia de corrección

| Hallazgo | Antes | Después |
|---|---|---|
| Espectador de Impostor | `word="Metro"`, rol `crew` | `word=""`, rol `audience` |
| Póster de Apuesta | URL completa en `wager.poster` y `question.art` desde pista 1 | vacío para jugador hasta etapa 3/reveal; display con host token |
| Charadas timbre | actor 1,000 al timbre y 2,300 al reveal | actor 0 al timbre y 1,300 al reveal; una sola liquidación |
| Early guess | `que`, `una`, `con` daban +400 | intento aceptado sin bono; solo palabra de contenido |
| Tinta | 11,979.2 aceptados con límite 100 | rechazo `"tinta"`, no se guarda el dibujo |
| v1 | cómplice/detective, tinta, ghost y ayudas activos | roles y mecánicas v2 bloqueados |
| Puja | 25 monedas → puja 50 | 25 monedas → puja 0 |
| Escáner | opción tachada podía votarse por API | rechazo `"escaneada"` |
| Reportes | un jugador podía borrar una pregunta con 3 reportes | deduplicado, rate limit y solo bandera editorial |
| Display | `/state?role=display` sin token de TV | lee estado público sin póster; con `X-Game-Host` recibe el póster autorizado |

---

## 4. Pruebas ejecutadas

- **Backend:** `590 passed` (`pytest -q tests`).
- **Android:** `1,148 tests`, suite completa `:app:testNormalDebugUnitTest` en verde.
- **Launcher:** no se re-ejecutó en este lote; última referencia `170` pruebas en los resultados locales.
- **Compilación release:** `:app:assembleNormalRelease` en verde.
- **Motor X4K:** inyectado en `classes3/4/5.dex`.
- **Firma:** histórica `9593cb0d…`.
- **Backend desplegado:** respaldos `*.bak-p0-20261030220819`.
- **Smoke del backend:** `/health` OK; display legacy → **200** sin póster; display con host → **200** con póster; `served` pasó de **0 a 5** al crear una sala con `X-Sync-Code`.

**APK publicada (OTA 2.5.201):**

- Archivo: `VisionPlus.apk`
- Versión: `2.5.201 / vc441`
- Tamaño: `97,641,971` bytes.
- SHA-256: `bd5217da285a34593eae6f7020da96bc2b9bc63fe89bf5982a5d836956eda5d5`.
- Release: `https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.201`
- **No instalada por ADB:** el Chromecast HD `192.168.1.124:35361` no respondió al cierre; la actualización está disponible por OTA.

---

## 5. Pendientes

### Inmediatamente después de la OTA

- Instalar/actualizar la tele a 2.5.201 y verificar:
  - creación de sala,
  - polling de display con host token,
  - lobby,
  - QR,
  - foco,
  - inicio de partida con teléfonos.
- Prueba física con 2–3 teléfonos.
- Verificar que `trivia_served` crece con el nuevo `X-Sync-Code` (el smoke del VPS ya lo confirmó: 5).
- Comprobar en el backend desplegado las correcciones de espectador, póster y reportes.

### Etapa B — mecánicas

- Interrogatorio libre/radar de Impostor.
- Relevo de artistas y maleficio de pintura.
- Sindicato de Subasta.
- Cazador y doble hueco real de Fibbage.
- Decidir si el auto-reporte del actor de Charadas se mantiene como confianza social o se verifica con timbre/host.

### Etapa C — contenido

- Exportar y auditar los 3,465 registros actuales de Trivia por lotes (el banco creció desde la auditoría inicial).
- Ampliar Fibbage a 100+ datos verificados.
- Ampliar Subasta a 50+ lotes.
- Ampliar Dibujo a 100+ consignas.
- Ampliar Impostor a 80+ lugares.
- Implementar rotación por grupo para los mazos locales.

### Etapa D — audiovisual

- Integrar `GamePhaseTransition`, `DramaticRevealCard` y `LightweightParticleOverlay`.
- Integrar los 60 audios v2 y los 7 tutoriales.
- Integrar avatares/partículas o retirarlos.
- Unificar acentos selector/catálogo.

### Etapa E — rendimiento

- Medir FPS, memoria y latencia en Chromecast HD.
- Probar 2, 4 y 8 jugadores.
- Probar reconexión, abandono y cambios de fase durante animaciones.
- Prueba de carga del backend.

---

## 6. Riesgos residuales

- La OTA 2.5.201 está publicada y el backend desplegado. Una tele 2.5.200 sigue funcionando, pero no verá el póster público de Apuesta; la app nueva sí lo ve con su host token.
- La APK no se pudo instalar por ADB porque el Chromecast no aceptó conexión; la actualización queda por OTA.
- El auto-reporte del actor en Charadas sigue siendo una confianza social.
- El público puede seguir creando varias sesiones; el tope de 8 reduce el riesgo, no lo elimina.
- Los mazos de fiesta siguen siendo estáticos y memorizables.
- No se ha ejecutado la prueba física.

---

## 7. Próximo paso recomendado

1. Actualizar la tele a 2.5.201 y verificar la creación de sala/QR/foco con 2–3 teléfonos.
2. Repetir la prueba de espectador, póster, timbre, tinta y escáner contra el backend nuevo.
3. Avanzar a Etapa B: completar UI de cazador/doble hueco, interrogatorio/radar, relevo y sindicato.
4. Avanzar a Etapa C: auditoría editorial por lotes y ampliación de mazos.
5. Integrar tutoriales, transiciones y los 60 audios v2.

**OTA publicada. La prueba física sigue pendiente.**
