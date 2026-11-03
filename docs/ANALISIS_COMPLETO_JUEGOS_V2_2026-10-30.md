# Vision+ Play 2.0 — Análisis completo para auditoría externa

**Fecha:** 30 de octubre de 2026  
**Build analizada:** `2.5.198 / vc438` (APK local con motor X4K inyectado)  
**OTA estable publicada:** `2.5.197 / vc437`  
**Modo por defecto:** `rulesVersion=2` para salas nuevas; `rulesVersion=1` sigue existiendo para compatibilidad.  
**Objetivo de este documento:** entregar a un experto externo todo el contexto de mecánicas, estados, puntajes, UI, transiciones, assets, voces, pruebas y superficies de trampa, sin obligarlo a leer todo el repositorio.

> **Dictamen corto:** el producto tiene una capa visual sólida y una arquitectura de servidor autoritaria en tiempos y puntajes, pero **no está listo para una OTA abierta** hasta corregir varias filtraciones de secretos y reglas que hoy solo se aplican en la UI. Los hallazgos P0 están en la sección 14 y en la tabla de la sección 0.1.
>
> **Nota de vigencia:** este documento describe el estado **previo** al lote P0 del 30 de octubre. Las correcciones aplicadas después están en `docs/REPORTE_P0_SEGURIDAD_2026-10-30.md`.

---

## 0. Resumen ejecutivo

Vision+ Play es un party game de 7 juegos para TV + teléfonos:

1. Trivia Vision+
2. Charadas
3. Apuesta a la pista
4. El Impostor
5. Duelo de Dibujo Ciego
6. Subasta Ciega
7. Falso Conocimiento / Fibbage

La TV crea la sala y muestra el estado público. Los teléfonos envían acciones y guardan secretos. El servidor decide fases, timers, puntajes y qué ve cada rol. El cliente no calcula respuestas correctas.

**Fortalezas**

- Separación clara TV / teléfono / servidor.
- Snapshot con filtrado por rol y `player_id`.
- Timers y puntajes calculados en el servidor.
- Códigos de sala sin caracteres ambiguos y tokens opacos hasheados en el servidor.
- 7 juegos con loops distintos, no solo variantes de trivia.
- Assets ligeros y adecuados para Chromecast HD.
- Buena base de pruebas unitarias y de estado.

**Riesgos principales**

- Un **espectador puede ver la palabra secreta de El Impostor**.
- La **URL completa del póster de Apuesta** se envía a todos los jugadores desde la primera pista; la UI la oculta, pero el JSON no.
- **Charadas paga dos veces al actor** cuando acierta por timbre.
- **Dibujo** permite adivinar con palabras vacías como `que`, `una` o `con`, y no aplica el límite de tinta en el servidor.
- Varias mecánicas v2 se activan también en salas v1.
- El sistema de preguntas servidas de Trivia está inactivo en la práctica: la tele no manda `X-Sync-Code` y `trivia_served` queda en 0.
- Hay mecánicas sin UI: cazador de Fibbage y doble hueco final.
- Hay 60 audios v2, 7 tableros de tutorial, 36 avatares y 5 partículas generados que no están conectados a ninguna pantalla.

### 0.1 Hallazgos P0 (corregir antes de OTA)

| ID | Hallazgo | Evidencia | Impacto |
|---|---|---|---|
| S1 | Espectador ve la palabra secreta de El Impostor | `snapshot()` calcula `can_see_word` sin excluir `me.spectator`; el rol se reporta como `crew` | El público puede delatar al impostor y arruinar la ronda |
| S2 | Póster completo de Apuesta filtrado desde la pista 1 | `wager.poster` y `question.art` se envían siempre; la UI solo los oculta visualmente | Cualquier jugador puede abrir la URL y ver el póster sin comprar pistas |
| S3 | Charadas paga dos veces al actor por timbre | `_finish_charada_buzz()` suma 1000 al actor y `_begin_reveal()` vuelve a sumar `correct*1000` | Puntaje inflado y asimetría entre “Correcto” manual y timbre |
| S4 | Adivinanza temprana de Dibujo acepta stopwords | `guess in prompt` sin límites de palabra; `que`, `una`, `con` aparecen en muchas consignas | +400 gratis con dos intentos, sin haber dibujado ni adivinado el título |
| S5 | Límite de tinta de Dibujo no se aplica en servidor | `draw_partial` y `draw_submit` guardan cualquier `_clean_strokes`; solo se registra `ink_used` | La regla “tinta limitada” es cosmética; un cliente modificado dibuja sin límite |
| S6 | Mecánicas v2 activas en salas v1 | Impostor asigna cómplice/detective sin mirar `rules_version`; Dibujo activa tinta en ronda 2; `auction_ghost` no se bloquea en v1 | Se rompe la promesa de compatibilidad total con v1 |

### 0.2 Estado de verificación

- **Android:** 1,143 pruebas; suite completa re-ejecutada en verde sobre el build final.
- **Backend:** 570 pruebas reportadas en verde; el árbol actual contiene 565 funciones `def test_` (hay parametrizaciones). No se re-ejecutó la suite completa en esta auditoría.
- **Launcher:** 176 pruebas reportadas en verde; el árbol actual contiene 181 métodos `@Test` contados. No se re-ejecutó la suite del launcher en esta auditoría.
- **Pruebas específicas de juegos:** 104 de servidor (42 + 21 + 14 + 27) y aproximadamente 74 de Android.
- **Backend consultado en vivo:** `ok: true`, 3,125 preguntas, `served: 0`, 2 salas abiertas en el momento de la consulta.
- **APK instalada:** 2.5.198/vc438, 97,637,875 bytes (~93 MiB), motor X4K en `classes3/4/5.dex`, firma histórica `9593cb0d…`, SHA-256 `0f147db0eb129601c4c8539c8ce029537912bd375da3b88c7a05916a7bc32452`.
- **No se ha hecho la prueba física completa con 2–3 teléfonos.** El selector y el lobby se verificaron por ADB en Chromecast HD, pero no una partida real con teléfonos.

---

## 1. Cómo leer este documento

- **“Servidor”** = `server/api/games.py` y `server/api/app.py`.
- **“TV”** = `TvGamesScreen.kt`, `TvGameModesScreen.kt`, `GameTransitionsV2.kt`.
- **“Teléfono”** = `GameRoomScreen.kt`, `GameV2Screens.kt`, `GameModesScreen.kt`.
- **“Snapshot”** = JSON que el servidor devuelve a `/state`; es la única fuente de verdad para la UI.
- **“Secreto”** = dato que, si se filtra, permite jugar con ventaja.
- Las rutas y funciones se citan para que el experto pueda ir directo al código.

---

## 2. Catálogo actual

| ID | Juego | Min–Max | Rondas | Duración | Acento en catálogo | Acento en selector v2 |
|---|---|---:|---:|---:|---|---|
| `trivia` | Trivia Vision+ | 1–8 | 8 | 12 min | `#2D7DF6` | `#2D7DF6` |
| `charadas` | Charadas | 2–8 | 6 | 10 min | `#FF8A65` | `#EF4444` |
| `wager` | Apuesta a la pista | 1–8 | 6 | 10 min | `#B388FF` | `#8B5CF6` |
| `impostor` | El Impostor | 3–8 | 3 | 10 min | `#FF5C7A` | `#F43F5E` |
| `dibujo` | Duelo de Dibujo Ciego | 3–8 | 3 | 12 min | `#4DD0E1` | `#06B6D4` |
| `subasta` | Subasta Ciega | 2–8 | 6 | 6 min | `#F5C518` | `#F59E0B` |
| `fibbage` | Falso Conocimiento | 2–8 | 4 | 10 min | `#A5D6A7` | `#10B981` |

**Observación visual:** el selector usa los acentos v2 saturados, pero el lobby y las pantallas de juego usan los acentos pastel de `GameCatalog`. Esto produce un cambio de color entre selector, lobby y partida. No es un bug funcional, pero sí una inconsistencia de dirección de arte.

El catálogo está duplicado a propósito:

- Servidor: `server/api/games.py` → `catalog()`.
- Android: `app/.../data/games/GameCatalog.kt`.

El servidor es la autoridad de qué juegos acepta. El cliente solo pinta la carta.

---

## 3. Arquitectura de sala y estados compartidos

### 3.1 Ciclo de vida

```
POST /v1/game/rooms
  → crea sala en memoria
  → TV en modo display no tiene jugadores todavía
  → el primer teléfono que entra es el anfitrión
  → countdown 3 s
  → fases del juego
  → reveal / auction_reveal
  → siguiente ronda
  → podium
  → close / TTL / idle
```

- Las salas viven en `_GAME_ROOMS` en memoria (`app.py`).
- TTL: 4 horas. Inactividad: 30 minutos.
- Máximo: 100 salas.
- El barrendero (`_games_sweep_loop`) corre cada 60 s, hace `tick` y persiste resultados al cerrar.
- Si la API se reinicia, las salas activas se pierden. Solo se guardan resultados al cerrar.
- El resultado se guarda en `game_results` con juego, modo, pack, jugadores, rondas y timestamps. No hay historial de acciones por ronda.

### 3.2 Roles

| Rol | Token | Qué ve |
|---|---|---|
| `display` | No requiere token de jugador; solo API key | Estado público de la TV |
| `player` | `X-Game-Player` obligatorio | Estado público + `you` + secretos que le correspondan |
| `spectator` | Token de jugador con `spectator=true` | Estado de jugador, pero no debería competir |

**Problema estructural:** `role=display` no tiene token de TV ni firma. Cualquiera con la API key estática puede pedir el snapshot de display. Hoy el snapshot de display está bien filtrado para casi todos los juegos, pero es una superficie que conviene autenticar.

### 3.3 Tokens y códigos

- Código de sala: 6 caracteres de `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (sin I, L, O, 0, 1).
- `31^6` combinaciones.
- Tokens de jugador: `secrets.token_urlsafe(24)`.
- El servidor guarda SHA-256 del token; el token en claro solo viaja al cliente que lo recibe.
- `host_ok()` acepta el host token o el player token del jugador que es host.
- En el teléfono, los tokens se guardan en `SharedPreferences` sin cifrado adicional.
- El host token de la TV se guarda en `GameStore.saveHost` y se usa para cerrar la sala.

### 3.4 Ritmo y polling

- Teléfono: polling cada 700 ms en fases activas (`question`, `clue`, `attack`, `sort`, `assault`, `punishment`) y 1 s en el resto.
- TV: mismo patrón.
- El servidor devuelve `204` si `since == rev`.
- El dibujo parcial se envía cada ~2.5 s; el servidor limita a un update cada 1.8 s.
- No hay WebSocket ni SSE.
- El reloj del cliente usa `serverNow` + tiempo transcurrido desde el snapshot; el servidor valida la hora real.

### 3.5 Rate limits y límites duros

| Límite | Valor | Nota |
|---|---:|---|
| Cuerpo HTTP | 64 KB | Por los trazos |
| Salas | 100 | Global |
| Jugadores | 8 por defecto, 12 máximo duro | `maxPlayers` |
| Create / join | 10 por IP y minuto | `_GAME_JOIN_RATE` |
| Acciones | 30 por jugador y minuto | `/action` |
| `draw_partial` | Exento del rate de acciones | Actualización cada ~2.5 s |
| Reacciones | 1 cada 2 s, máximo 6 en snapshot | |
| Reportes | **sin rate limit real** | `_GAME_REPORT_BUCKET` definido pero no usado |
| Estado | **sin rate limit** | `/state` no limita polling |
| Trazos | 160 trazos, 2,400 puntos | `_clean_strokes` |
| Strokes por dibujo | 64 KB de body | No hay compresión delta |

### 3.6 Puntaje y medallas

- El servidor calcula todo el puntaje.
- `GameScore.kt` replica la tabla clásica de Trivia para que la UI muestre lo mismo; hay test de paridad.
- Las medallas del podio son:
  - El más mentiroso (`lies_votes`)
  - El detective (`detective`)
  - El tiburón de la subasta (`auction_wins`)
  - El camaleón (`impostor_wins`)
  - El más despistado (siempre, al de menor puntaje)
- Máximo 2 medallas por jugador, salvo “El más despistado”, que se fuerza.
- No hay puntaje por equipos ni temporadas.

---

## 4. Trivia Vision+ — “El piso se quema”

### 4.1 Loop

- 8 rondas por defecto.
- Pregunta de opción múltiple con 4 opciones.
- Respuesta correcta + velocidad + racha.
- Dos aciertos seguidos desbloquean un ataque para la ronda siguiente.
- Rondas 4 y 5: clasificación antes/después.
- Rondas 3 y 6: castigo de cables si alguien falla.
- Ronda 8: asalto final de selección múltiple.

### 4.2 Fases y tiempos

| Fase | Duración | Qué pasa |
|---|---:|---|
| `countdown` | 3 s | Arranque |
| `attack` | 6 s | Los elegibles usan poder o pasan |
| `question` | 20 s por defecto | Opción múltiple |
| `punishment` | 8 s | Los que fallaron cortan cable; los salvos votan |
| `punishment_reveal` | 5 s | Se revela el cable caliente |
| `sort` | 18 s | 6 cartas antes/después |
| `assault` | 25 s | Selección múltiple final |
| `reveal` | 8 s | Resultado; en modo display espera “Continuar” del host |
| `podium` | — | Cierre |

### 4.3 Puntaje

- Base por dificultad: 1,000 / 1,500 / 2,000.
- Bono de velocidad: hasta 25% de la base.
- Bono de racha: +100 por acierto consecutivo después del primero, tope +500.
- Respuesta incorrecta o tardía: 0 y racha a 0.
- Ataque:
  - `freeze`: cuesta 200; el objetivo necesita 5 toques para ver opciones.
  - `glitch`: cuesta 200; censura letras solo en el teléfono del objetivo.
  - `parasite`: gratis; el atacante no responde. Si el objetivo acierta, el atacante cobra 75% de sus puntos; si falla, ambos pierden 300.
- Castigo: el cable caliente cuesta −500.
- Ráfaga: +250 por carta correcta.
- Asalto: +400 por acierto, −400 por error, sin bajar de 0.
- El ataque solo está disponible si `streak >= 2` y la ronda siguiente coincide.

### 4.4 Reglas anti-focus y anti-cadena

- Un solo efecto negativo por víctima y por ronda: `target in trivia_attack_target.values()` bloquea un segundo ataque.
- El parásito no puede formar cadenas A→B→A ni atacar a alguien que ya es parásito.
- El atacante parásito se marca como “respondido” con `Answer(option=-1)` para no bloquear el timer.
- El ataque se aplica al comenzar la pregunta, no al instante de elegirlo.

### 4.5 UI

**Teléfono**

- `TriviaV2Content` en `GameV2Screens.kt`.
- Tarjeta de ataque con poder y objetivo; `attack.targets.take(4)`.
- Pregunta con opciones, censura visual y botón “Golpear hielo” para el congelado.
- Castigo con 4 cables de colores.
- Ráfaga con 6 filas antes/después.
- Asalto final con selección múltiple.
- Reveal con puntos y medallas.

**TV**

- `TvTriviaV2` en `TvGamesScreen.kt`.
- Badge de fase, ronda, reloj, barra de progreso.
- En `punishment` muestra votos por cable en vivo.
- En `sort` y `assault` muestra tarjetas y respuestas solo en reveal.
- El TV no recibe el texto de la respuesta hasta `reveal`.

### 4.6 Profundidad

**Alta.** Es el juego con más variación de ronda y más decisiones tácticas. El ataque, el castigo, la ráfaga y el asalto cambian el ritmo.

**Fisuras de diseño**

1. **`take(4)` en el teléfono:** en salas de 5–8 jugadores, el atacante solo puede elegir entre los primeros 4 objetivos. El servidor soporta 7; la UI no. Es una pérdida de agencia real.
2. **El glitch es débil en una sala con TV:** la TV muestra las opciones completas; el jugador censurado puede mirar la tele. Si el diseño busca presión individual, el efecto no se sostiene.
3. **El freeze son 5 toques:** no es un reto de habilidad; se automatiza con un cliente modificado. El servidor no exige cadencia ni tiempo mínimo entre toques.
4. **Los votos del castigo se ven en vivo en la TV:** un votante puede esperar y votar al cable que va ganando. Puede ser intencional (farol público), pero favorece al último voto.
5. **El ataque cuesta 200 sobre una base de 1,000–2,000:** es barato; el líder puede atacar sin sacrificar casi nada. El parásito gratis es especialmente fuerte.
6. **La rotación de preguntas está inactiva:** la tele no envía `X-Sync-Code`; `/health` reporta `served: 0`. La consulta de 90 días no se usa en salas reales.
7. **No hay haptics en Trivia v2**, a diferencia de los juegos de fiesta.
8. **La etiqueta de cable en la TV usa `RED/BLUE/YELLOW/GREEN`**, mientras el teléfono usa “Rojo/Azul/Amarillo/Verde”. Es una inconsistencia menor de localización.

### 4.7 Veredicto Trivia

- **Diversión:** alta.
- **Profundidad:** alta.
- **Riesgo de trampa:** medio.
- **Principal deuda:** rotación de preguntas y límite de objetivos del ataque.

---

## 5. Apuesta a la pista — “Bóveda ciega y póker de pistas”

### 5.1 Loop

- Todos empiezan con banca de 1,000 puntos en v2.
- Cada ronda: todos pagan 100 al pozo.
- Se muestra un póster y una pista pública de etapa 1.
- Cada jugador puede comprar pistas privadas en su teléfono (200 cada una) o bloquear su respuesta.
- Al bloquear, el factor depende de la etapa privada: x4 (etapa 1), x2 (etapa 2), x1 (etapa 3).
- Si nadie acierta, el pozo rueda a la siguiente ronda.
- Hay apuesta lateral de 200 contra alguien que bloqueó en etapa 1.

### 5.2 Fases

- No hay fase `wager` en v2: el servidor entra directo a `clue`.
- `clue` dura `clue_seconds` (15 s por defecto).
- `reveal` dura 8 s en v2; en modo display espera “Continuar”.
- No hay una fase pública 2/3: la TV solo muestra la pista pública 1. Las pistas 2/3 son privadas en el teléfono.

### 5.3 Puntaje

- Banca inicial: `WAGER_START_BANK = 1000`.
- Ciega: `WAGER_ANTE = 100` por jugador conectado.
- Comprar pista: `WAGER_BUY_COST = 200` al pozo.
- Factores: `4, 2, 1`.
- Reparto del pozo por peso; cada share se redondea hacia abajo a múltiplos de 50.
- Rollover: si nadie acierta, el pozo completo pasa a la siguiente ronda.
- Apuesta lateral:
  - 200 contra un jugador que bloqueó en etapa 1.
  - Si el objetivo acierta: el apostador pierde 200 y el objetivo los gana.
  - Si el objetivo falla y hay ganador: el apostador gana 400.
  - Si nadie acierta y hay rollover: el apostador gana 200 (no 400).
- El TV muestra el pozo, la ciega, el costo de pista y el factor.

### 5.4 UI

**Teléfono**

- `WagerV2Content`.
- Muestra pozo, factor privado, pista privada, póster solo cuando `privateStage >= 3` o reveal.
- Botón de compra de pista, opciones de respuesta, apuesta lateral.
- Muestra “Póster pixelado · Pista privada N/3” cuando no es etapa 3.

**TV**

- `TvWagerV2`.
- Muestra “PISTA 1 DE 3”, pozo, factor, póster con alpha 0.35, opciones y apuestas laterales.
- La tele no avanza a pista 2/3; eso es privado.

### 5.5 Profundidad

**Alta.** Es el juego con más economía y farol: pago de entrada, compra de información, timing de bloqueo, apuesta lateral y rollover.

**Fisuras**

1. **Póster completo filtrado:** el snapshot envía `wager.poster` y `question.art` desde la pista 1. La UI lo oculta hasta etapa 3, pero el JSON no. Un jugador puede abrir la URL y reconocer el título. **P0.**
2. **La pista privada 2 no muestra medio póster:** el servidor define `privatePosterReveal = 0.5`, pero la UI solo muestra imagen cuando `privateStage >= 3` o reveal. La etapa 2 queda como texto, no como imagen parcial.
3. **La TV usa alpha, no pixelación:** el póster público es una imagen completa atenuada al 35%, no un póster pixelado. La pista pública 1 puede ser más fácil de lo previsto.
4. **El pago lateral no coincide en el caso rollover:** la UI promete +400 si el rival falla; si nadie acierta, el servidor paga +200.
5. **La UI solo ofrece 3 objetivos de apuesta lateral:** `take(3)`; en salas grandes puede haber más bloqueados en etapa 1.
6. **La economía puede quedar negativa en la percepción del jugador:** el pozo se reparte por peso, pero el que compró pistas puede perder más de lo que gana si falla. Es diseño, no bug, pero conviene simularlo.
7. **El `wager_stage` global se queda en 1:** es coherente con la decisión de TV solo pista pública 1, pero el texto “PISTA 1 DE 3” puede sugerir que la TV avanzará y no lo hará.

### 5.6 Veredicto Apuesta

- **Diversión:** alta.
- **Profundidad:** alta.
- **Riesgo de trampa:** alto por el póster.
- **Principal deuda:** filtrar el póster y completar la lectura visual de etapas privadas.

---

## 6. Charadas — “El set de rodaje”

### 6.1 Loop

- Por turnos, un actor ve un título en su teléfono y lo actúa.
- El resto adivina en voz alta.
- En v2 hay un director que impone una restricción y un saboteur que conoce el título y quiere que nadie acierte.
- Cualquier no-actor puede tocar el timbre; el actor decide si el grito fue correcto o fallido.
- El actor puede comprar ayudas de género (200) o año (300), que se descuentan del premio de la ronda.

### 6.2 Fases y tiempos

| Fase | Duración | Qué pasa |
|---|---:|---|
| `director` | 5 s | El director elige una restricción de 3 opciones |
| `turn` | 60 s | El actor actúa; el timbre pausa 7 s |
| `turn` con timbre | 7 s | El actor marca correcto o fallido |
| `reveal` | 5 s | Resultado del turno; espera “Continuar” en modo display |

- Si el director no elige, el servidor toma la primera opción a los 5 s.
- Si el actor se desconecta, se va a reveal.
- Si el timbre falla, el adivinador tiene 12 s de cooldown y pierde 200.
- Si el timbre acierta, el actor suma 1,000 y el adivinador 700; el reloj se reanuda con el tiempo restante.

### 6.3 Reparto de roles

- Actor: rota por orden de entrada, `round_index % connected`.
- Director: el jugador con menor puntaje entre los no-actores (desempate por entrada).
- Saboteur: elegido con RNG determinista por sala y ronda, entre los no-actores.
- Opciones de restricción: muestra aleatoria de 3 de 5.
- El saboteur ve el título; el crew no.

### 6.4 Puntaje

- Actor: `max(0, correct * 1000 - help_penalty)`.
- Director: +300 si el actor tuvo al menos un acierto y el director eligió restricción.
- **El actor también recibe +300 en ese caso** (se suma a `earned`).
- Saboteur: +600 si el turno termina con 0 aciertos.
- Timbre:
  - Acierto: adivinador +700, actor +1,000, `charada_correct += 1`.
  - Fallo: adivinador −200, cooldown 12 s.
- Ayudas: −200 género, −300 año; no se descuentan del marcador vivo, solo del premio.
- Pases: máximo 3 por turno.
- El actor puede marcar “Correcto” sin timbre; esa vía solo paga en el reveal.
- El actor puede marcar “Correcto” después de un timbre; esa vía paga dos veces (ver S3).

### 6.5 UI

**Teléfono**

- `CharadaContent` en `GameRoomScreen.kt`.
- Director ve las 3 restricciones.
- Actor ve el título, botones Correcto/Pasar y ayudas.
- Saboteur ve “TU MISIÓN: SABOTEAR” y el título.
- Crew ve el turno y el timbre.
- El timbre activo muestra “¡CORTE! [nombre]” y el actor confirma.
- No hay chat de voz; se juega hablando.

**TV**

- `TvCharada` en `TvGamesScreen.kt`.
- Muestra actor, restricción, título solo en reveal, timbre y resultado.
- El título no se muestra a la TV antes del reveal.

### 6.6 Profundidad

**Media-alta.** Es un juego social, no de sistema. La profundidad depende del grupo, del director y del saboteur.

**Fisuras**

1. **Doble pago del actor por timbre (S3).** Verificado: tras un timbre correcto el actor queda en 1,000; al `_begin_reveal` sube a 2,300 (1,000 + 1,000 + 300 de director). Si marca “Correcto” manual, queda en 1,300. Es una asimetría grande.
2. **El actor se auto-reporta.** No hay verificación independiente de que el público realmente haya dicho el título. Un actor puede marcar “Correcto” en todas las cartas y farmear 1,000 por carta. En un party game puede ser confianza social, pero para un experto es una fisura de integridad.
3. **El saboteur puede revelar el título.** Sabe la respuesta; si la dice, el crew acierta y él pierde 600, pero puede arruinar la ronda a voluntad. Es social, no hay detección.
4. **La ayuda es gratis si no hay acierto.** El costo se descuenta del premio; si el actor no acierta, no paga nada. La UI dice “se descuenta del premio”, así que es coherente, pero el riesgo de comprar ayudas es bajo.
5. **El bono del director se paga al actor y al director.** Puede ser intencional (premio al buen director), pero el actor ya recibe 1,000 por acierto; el +300 extra refuerza al que ya gana.
6. **No hay restricción de tiempo para el timbre después de un fallo más allá del cooldown.** El cooldown es 12 s; el turno puede seguir.
7. **No hay haptics en Charadas v2**, aunque el teléfono es el dispositivo de acción.
8. **La TV usa `GameStageBadge` y texto, no una transición de “telón”.** El componente `GamePhaseTransition` existe, pero no se usa.

### 6.7 Veredicto Charadas

- **Diversión:** alta si el grupo actúa.
- **Profundidad:** media-alta.
- **Riesgo de trampa:** alto por auto-reporte; alto por doble pago.
- **Principal deuda:** corregir doble pago y decidir si el auto-reporte es aceptable.

---

## 7. El Impostor — “Infiltración y coartadas”

### 7.1 Loop

- Todos comparten un lugar secreto; el impostor no lo sabe.
- En v2, con 5+ jugadores hay un detective; con 6+ hay un cómplice.
- Preguntas dirigidas por parejas; el asker avanza su turno.
- Coartada rápida: todos eligen un “vibe” 0–9.
- Votación; si el impostor está entre los más votados, pasa a adivinar el lugar.
- Si lo atrapan y acierta, roba la ronda; si falla, gana el crew.

### 7.2 Fases y tiempos

| Fase | Duración | Qué pasa |
|---|---:|---|
| `secret` | 11 s | Cada teléfono muestra rol |
| `questions` | 25 s (3–4 jugadores), 20 s (5–6), 15 s (7–8) por pareja | Preguntas dirigidas |
| `coartada` | 8 s | Todos eligen un valor 0–9 |
| `vote` | 22 s | Votan quién miente |
| `guess` | 20 s | Solo si el impostor fue atrapado |
| `reveal` | 5 s | Resultado |

- El asker puede avanzar el turno antes del timer.
- Si hay empate en la cima y el impostor está entre los líderes, se considera atrapado parcialmente y va a `guess`.
- Si el impostor no está entre los líderes, escapa.

### 7.3 Roles

| Rol | Requisito | Qué sabe | Puntos |
|---|---|---|---|
| Crew | Todos | Lugar, pista, categoría | +1,000 si vota al impostor; +300 extra si el impostor falla el guess |
| Impostor | Siempre | No sabe el lugar; ve categoría clasificada | +1,500 si escapa; +800 si lo atrapan y adivina |
| Cómplice | 6+ jugadores | Sabe quién es el impostor, no el lugar | +700 si el impostor escapa |
| Detective | 5+ jugadores | Ve dos lugares posibles: real + señuelo | +300 si el impostor es atrapado y falla el guess |

- El impostor se elige con RNG determinista por sala y ronda.
- El detective se elige fuera del impostor y del cómplice.
- El cómplice no ve la palabra.
- El impostor no ve la palabra ni la categoría.
- La TV no ve la palabra hasta reveal.
- El crew ve la palabra durante `secret` y `questions`.

### 7.4 UI

**Teléfono**

- `ImpostorPhoneContent` en `GameModesScreen.kt`.
- Tarjeta secreta con arte de rol (`game_imp_role_spy` / `game_imp_role_crew`).
- Crew: lugar, pista, advertencia de no mostrar.
- Impostor: “ERES EL IMPOSTOR”, sin lugar.
- Cómplice: “ERES EL CÓMPLICE”, ve el nombre del impostor.
- Detective: dos opciones de lugar.
- Preguntas: pregunta sugerida, asker/target y botón “Siguiente pregunta”.
- Coartada: 5 botones de vibra.
- Voto: lista de jugadores.
- Guess: 8 opciones para el impostor.
- Reveal: impostor, lugar, cómplice, detective, coartada y puntos.

**TV**

- `TvImpostor` en `TvGameModesScreen.kt`.
- Muestra reparto secreto sin palabra, pregunta actual, coartada, votos, guess y reveal.
- El TV no ve la palabra hasta reveal.
- La TV muestra “Categoría clasificada” cuando no debe ver.

### 7.5 Profundidad

**Alta.** Es el juego con más lectura social; los roles asimétricos y la coartada añaden capas.

**Fisuras**

1. **Espectador ve la palabra secreta (S1).** Confirmado: un `spectator=true` recibe `impostor.word`, `category` y `hint`; su rol se reporta como `crew`. El público puede delatar al impostor. Es el hallazgo más grave.
2. **v1 asigna cómplice y detective (S6).** `_begin_impostor` no mira `rules_version`; una sala v1 con 6 jugadores tiene cómplice y detective, y el UI los muestra. La fase `coartada` sí se salta en v1, pero los roles ya están asignados.
3. **La coartada es un número, no una frase.** Todos eligen 0–9; el impostor puede elegir un valor aleatorio. La información es débil; el valor se revela antes de votar, así que el impostor puede imitar la distribución. Es más un mini-ritual que una coartada real.
4. **El detective ve dos opciones, pero no puede verificar cuál es real.** Está bien, pero si canta las dos opciones en voz alta, el impostor también las oye. Es social.
5. **El impostor atrapado que acierta gana 800, no 1,500.** La UI lo cuenta; el crew que lo votó igual cobra 1,000. La ronda queda con recompensa mixta.
6. **El impostor que no habla puede sobrevivir.** No hay penalización por silencio; el timer avanza y el crew puede interpretar el silencio como sospecha. En grupos pasivos, el juego puede ser plano.
7. **No hay interrogatorio libre ni radar.** El handoff lo menciona como pendiente; el código solo tiene preguntas dirigidas y coartada.
8. **El espectador no tiene una vista de público dedicada.** Aun sin el leak, la UI de `ImpostorPhoneContent` no distingue claramente entre crew y audiencia.
9. **El rol de cómplice solo aparece con 6+.** Para 5 jugadores hay detective, pero no cómplice; para 3–4 no hay roles extra. La profundidad cae fuerte en salas pequeñas.

### 7.6 Veredicto Impostor

- **Diversión:** alta.
- **Profundidad:** alta.
- **Riesgo de trampa:** crítico por el espectador; medio por el resto.
- **Principal deuda:** corregir S1 y S6; decidir si la coartada numérica es suficiente.

---

## 8. Duelo de Dibujo Ciego — “Taller caótico”

### 8.1 Loop

- Un artista rota por ronda y elige una de dos consignas.
- Dibuja en el teléfono; la tele muestra el lienzo en vivo.
- Los demás pueden hacer una adivinanza temprana (2 intentos) mientras dibuja.
- Después del dibujo, los demás inventan títulos falsos.
- Todos votan el título real.
- El artista gana por votos reales; los autores de mentiras ganan por votos recibidos.
- Hay fichas de crítico: bomba (−200) y corona (+300).
- Ronda 2: tinta limitada. Ronda 3: linterna en la TV.

### 8.2 Fases y tiempos

| Fase | Duración | Qué pasa |
|---|---:|---|
| `draw` | 45 s | El artista dibuja; el resto puede adivinar temprano |
| `title` | 30 s | Los no-artistas inventan títulos |
| `vote` | 22 s | Todos votan el título real |
| `reveal` | 5 s | Resultado |

- El artista puede elegir consigna con `draw_pick_prompt` antes de dibujar.
- `draw_partial` actualiza cada ~2.5 s; el servidor limita a 1.8 s.
- `draw_submit` cierra el dibujo y pasa a `title`.
- El artista no vota.
- El público puede votar en la urna de audiencia.

### 8.3 Reglas de dibujo

- Coordenadas 0..1000.
- Máximo 160 trazos y 2,400 puntos.
- Colores hex de 6 dígitos; ancho 1–24.
- El servidor guarda vectores, no imágenes.
- Tinta: `_ink_cost` suma longitud * (ancho/10) / 20.
- Ronda 2: límite 100; bono de artista si termina con ≤50% y tiene votos reales.
- Ronda 3: `flashlight=true`; la TV dibuja una máscara radial móvil.
- El teléfono del artista ve un canvas táctil; el scroll se desactiva durante el dibujo.
- **El límite de tinta no se valida en el servidor:** solo se mide para el bono.

### 8.4 Adivinanza temprana

- Solo no-artistas y no-espectadores.
- 2 intentos.
- Texto mínimo 3 caracteres.
- Si `fold(guess) in fold(prompt)`, gana +400.
- No hay verificación de palabra completa, stopwords ni contexto del dibujo.
- La palabra acertada ya no se prohíbe en el título falso (se quitó el castigo), así que no hay downside.
- El intento se puede hacer antes de que el artista haya dibujado nada.

### 8.5 Puntaje

- Votante que elige la verdad: +1,000.
- Autor de una mentira votada: +500 por voto.
- Mentira con 3+ votos: +300 extra.
- Artista: +600 por voto real.
- Artista con 0 votos reales y 4+ jugadores: −500.
- Artista con tinta ≤50% y votos reales: duplica el bono por voto real.
- Adivinanza temprana: +400.
- Bomba de crítico: −200 al autor/artista.
- Corona de crítico: +300 al autor/artista.
- Favorito del público: +300 al autor/artista, solo si hay un ganador único de audiencia.

### 8.6 UI

**Teléfono**

- `DibujoPhoneContent` en `GameModesScreen.kt`.
- Elección de consigna, canvas, colores, grosores, deshacer, borrar, enviar.
- Barra de tinta con color verde/rojo.
- Adivinanza temprana durante `draw`.
- Título falso durante `title`.
- Voto y fichas de crítico durante `vote`.
- Reveal con opciones y puntos.

**TV**

- `TvDibujo` en `TvGameModesScreen.kt`.
- `AnimatedVectorDrawingCanvas` dibuja los trazos con interpolación de 1.6 s.
- Linterna radial infinita en ronda 3.
- Muestra título real, opciones y favorito del público en reveal.
- El TV no muestra el prompt durante draw/title/vote.

### 8.7 Profundidad

**Media.** La creación y el engaño son divertidos, pero el sistema tiene varias reglas que dependen del cliente.

**Fisuras**

1. **Adivinanza temprana con stopwords (S4).** Verificado: `que`, `una`, `con` aparecen en 13, 22 y 9 consignas. Con 2 intentos, un jugador puede probar dos stopwords y cobrar +400 sin adivinar. Es la fisura más clara de Dibujo.
2. **Tinta no aplicada en servidor (S5).** Verificado: se aceptó un dibujo con 11,979.2 de tinta contra límite 100. La regla es cosmética.
3. **El artista puede escribir letras con trazos.** El servidor no detecta texto; la regla “sin letras” es social.
4. **El artista puede dibujar ambiguo a propósito** para favorecer las mentiras. En salas de 4+, si nadie vota real, pierde 500; si logra al menos un voto real, gana 600 por voto. El incentivo no es puramente perverso, pero el artista puede preferir una obra confusa que genere votos de mentira si el grupo se ríe de ella.
5. **Las fichas de crítico se pueden usar sobre la propia mentira.** El servidor no excluye al autor; un jugador puede coronar su propia mentira (+300) y bombardear otra. La UI lo permite.
6. **El público puede inflar el favorito (+300) con varias sesiones.** No hay tope de espectadores.
7. **El prompt es estático y hay 50 consignas.** Un grupo que repite aprende el mazo; la adivinanza temprana se vuelve más fácil.
8. **La linterna es solo visual.** No afecta el tiempo, la tinta ni el puntaje. Es una capa estética.
9. **No hay relevo de artistas ni maleficio de pintura.** El handoff los menciona; el código actual no los implementa.
10. **No hay `DramaticRevealCard`:** el reveal es una lista, no un volteo 3D.

### 8.8 Veredicto Dibujo

- **Diversión:** alta en grupos creativos.
- **Profundidad:** media.
- **Riesgo de trampa:** alto por early guess y tinta.
- **Principal deuda:** validar palabra completa y aplicar tinta en servidor.

---

## 9. Subasta Ciega — “Mercado negro”

### 9.1 Loop

- 6 lotes por defecto.
- Cada lote tiene una carta pública y una carta secreta.
- 1 informante en salas de 3–4; 2 en 5–8.
- Todos pujan en secreto desde el teléfono.
- El mayor postor paga y aplica efectos.
- Empate en la cima: se cancelan los líderes y gana el siguiente.
- Ronda 4: el segundo lugar también paga (“lote maldito”).
- Falsa alarma: una vez por jugador, avisa a la TV de una puja alta falsa.
- Al final, las monedas sobrantes se convierten a puntos al 50%.

### 9.2 Fases

| Fase | Duración | Qué pasa |
|---|---:|---|
| `auction` | 15 s | Pujas secretas |
| `auction_reveal` | 12 s | Martillo, ganador, efectos, pujas |

- El `auction_reveal` puede avanzar antes si todos están listos y `players_pace`.
- El `auction` cierra cuando todos pujaron o cuando expira.

### 9.3 Efectos

| Efecto | Efecto |
|---|---|
| `points` | Suma puntos |
| `coins` | Suma monedas |
| `steal` | Roba 25% de los puntos al líder, con mínimo 250 y máximo 600 |
| `double_next` | Duplica el próximo lote de puntos/robo |
| `shield` | Bloquea un robo; se rompe y rebota −250 al ladrón |
| `bomb` | Resta puntos al ganador; si nadie puja, todos pierden 100 |
| `donate` | El ganador dona puntos al último lugar |
| `mystery` | Efecto aleatorio al revelar |

- `auction_secret_effect` es un segundo efecto secreto, visible solo para informantes y en reveal.
- El “lote maldito” es la ronda 4; el segundo paga su puja completa, o la mitad si está empatado en la cima con el ganador.
- `double_next` se consume al usarse; si el lote también tiene un efecto secreto de puntos, el flag puede consumirse en el mismo lote.
- El escudo reflector v2: si el líder tiene escudo, el ladrón pierde 250 y el líder gana 250.

### 9.4 UI

**Teléfono**

- `SubastaPhoneContent`.
- Carta, título, descripción, efecto visible.
- Si es informante: efecto secreto.
- Botones 0/50/100/250/500, +50/+100/+250, “Todo”.
- Botón de puja; una vez registrada no se cambia.
- Falsa alarma una vez.
- Reveal con ganador, precio, pujas, empate y efecto aplicado.

**TV**

- `TvSubasta`.
- Martillo animado, lote, efecto, “lote maldito”, falsa alarma, ganador y pujas en reveal.
- El TV no ve el efecto secreto antes del reveal.

### 9.5 Profundidad

**Media.** La subasta tiene farol y gestión de monedas, pero el espacio de decisiones es acotado y el mazo es estático.

**Fisuras**

1. **Puja por debajo de 50 monedas (S9).** El servidor redondea a múltiplos de 50 *después* de limitar a monedas. Si tienes 25, puedes pujar 50 y ganar. Verificado.
2. **v1 permite falsa alarma (S6).** El `auction_ghost` no comprueba `rules_version`; una sala v1 puede usar una mecánica v2.
3. **El segundo pago tiene semántica ambigua.** Si el segundo es un líder empatado, paga la mitad; si es otro, paga completo. La UI dice “el segundo lugar también paga su puja”, sin explicar el empate.
4. **El mazo de 26 cartas se memoriza.** Los valores y efectos son fijos; un grupo recurrente puede calcular el valor esperado de cada carta.
5. **No hay sindicato ni mercado entre rondas.** El handoff los menciona; no están implementados.
6. **La falsa alarma no tiene costo.** Es información falsa gratis una vez por jugador; puede usarse para manipular.
7. **El escudo reflector puede castigar al ladrón incluso si no era el líder.** Es una interacción fuerte, pero está documentada.
8. **La liquidación de bóveda solo ocurre al final de la ronda 6; si el host cierra antes, no hay cashout.**
9. **La puja no se puede cambiar**, lo que reduce el farol de última hora. Es una decisión de diseño, no un bug.

### 9.6 Veredicto Subasta

- **Diversión:** media-alta.
- **Profundidad:** media.
- **Riesgo de trampa:** medio por puja bajo presupuesto y por información secreta.
- **Principal deuda:** corregir el redondeo de puja y decidir si el segundo pago se explica mejor.

---

## 10. Falso Conocimiento / Fibbage — “Trampa mental”

### 10.1 Loop

- La TV lanza un dato insólito de cine/series.
- Cada jugador escribe una mentira.
- El servidor mezcla la verdad + mentiras + señuelos de la casa.
- Todos votan la que creen verdadera.
- El autor de una mentira votada gana; el que acierta la verdad gana.
- Extras v2: veneno, escáner, voto de certeza, cazador y ronda final a 2x.

### 10.2 Fases y tiempos

| Fase | Duración | Qué pasa |
|---|---:|---|
| `write` | 30 s | Cada jugador escribe una mentira |
| `vote` | 22 s | Todos votan la verdad |
| `reveal` | 5 s | Resultado |

- La ronda final tiene `finalShared=true` y multiplicador 2x.
- El “doble hueco final” no está implementado como mecánica de dos respuestas; solo existe el flag y el 2x.
- El cazador (`fibbage_hunter`) existe en servidor, pero no tiene UI ni test.

### 10.3 Reglas

- Mentira: texto de hasta 100 caracteres.
- Se rechaza exactamente la respuesta real (por `fold`).
- No se rechazan respuestas casi iguales (“Toy Story 2” vs “Toy Story”).
- Veneno: cuesta 150; si alguien vota la mentira, el autor roba 300 al votante.
- Escáner: una vez por partida; revela 1 mentira en salas de ≤4 y 2 en salas de 5–8; cuesta 300 si fallas (si votas una mentira).
- Voto de certeza: x1.5 si aciertas; −300 si fallas; una mentira votada con certeza paga 800 fijos al autor.
- Escáner y certeza son exclusivos: si usas escáner, no puedes votar con certeza.
- Cazador: `text=optionId`, `target=authorId`; +300 si aciertas autor; sin UI.
- Mentira con 3+ votos: +300 extra (x2 en ronda final).
- Favorito del público: +300 al autor de la mentira elegida por la audiencia; no aplica a la verdad.

### 10.4 Puntaje

- Verdad: +1,000 (x2 final).
- Mentira votada: +500 por voto (x2 final).
- Mentira con 3+ votos: +300 (x2 final).
- Certeza sobre verdad: 1.5x.
- Certeza sobre mentira: −300 al votante y +800 al autor.
- Veneno: +300 al autor y −300 al votante.
- Escáner usado y voto no real: −300.
- Cazador: +300.
- Público: +300 al autor de la mentira favorita.

### 10.5 UI

**Teléfono**

- `FibbagePhoneContent`.
- Escribe mentira, activa veneno, envía.
- En voto: opciones, voto normal o certeza, escáner.
- El escáner filtra visualmente las opciones tachadas.
- Reveal con verdad, opciones, votos, puntos y favorito del público.

**TV**

- `TvFibbage`.
- Muestra prompt, pista, categoría, mentiras enviadas, opciones y verdad.
- No muestra autores ni verdad hasta reveal.
- No muestra “doble hueco”.

### 10.6 Profundidad

**Media-baja.** Es el juego más dependiente del contenido y el que tiene más mecánicas a medio conectar.

**Fisuras**

1. **Mazo de 30 datos.** Un grupo de 4 rondas ve 30 datos en ~7–8 partidas. Después, el juego se vuelve memoria, no conocimiento. Es la mayor deuda de rejugabilidad.
2. **Datos no verificados con fuente.** El handoff lo reconoce; algunos son conocidos o ambiguos. Un dato incorrecto rompe la confianza en el juego.
3. **Cazador sin UI (S12).** Está implementado en servidor (`fibbage_hunter`), pero ninguna pantalla lo usa y no hay test específico. Es una mecánica muerta.
4. **Ronda final sin doble hueco (S12).** `finalShared` solo activa 2x; no hay dos respuestas compartidas ni mecánica de doble hueco. El test solo comprueba el flag.
5. **Escáner solo se aplica en UI (S13).** El servidor entrega `scanned` y la UI oculta esas opciones, pero un cliente modificado puede votar una opción tachada.
6. **Mentira casi verdadera.** El servidor solo rechaza coincidencia exacta; una variante cercana se acepta como mentira y puede confundir a los votantes.
7. **El público puede inflar +300** con varias sesiones de espectador; no hay tope.
8. **La certeza es una apuesta dominante si conoces el dato.** x1.5 con riesgo −300; en la ronda final x2 puede ser 3,000. Es una decisión de riesgo, pero el óptimo es apostar certeza si tienes confianza.
9. **No hay fuente ni dificultad.** Todos los datos tienen el mismo peso; no hay packs por dificultad.
10. **No hay `DramaticRevealCard`.** El reveal es una lista, no un volteo.

### 10.7 Veredicto Fibbage

- **Diversión:** alta la primera noche; cae con la repetición.
- **Profundidad:** media-baja.
- **Riesgo de trampa:** medio por memoria, público y escáner.
- **Principal deuda:** ampliar/rotar el banco, decidir cazar/doble hueco y validar hechos.

---

## 11. Sistemas transversales, balance y profundidad

### 11.1 Tabla de profundidad y riesgo

| Juego | Profundidad | Rejugabilidad | Riesgo de trampa | Principal decisión interesante |
|---|---|---|---|---|
| Trivia | Alta | Alta si se rota el banco | Medio | Atacar, congelar o guardar la racha |
| Apuesta | Alta | Alta | Alto | Comprar información o bloquear temprano |
| Charadas | Media-alta | Alta | Alto | Actuar, sabotear o timbrar |
| Impostor | Alta | Alta | Crítico | Fingir, interrogar, coartada y voto |
| Dibujo | Media | Media | Alto | Dibujar legible o ambiguo; mentir el título |
| Subasta | Media | Media | Medio | Valorar el lote y el farol |
| Fibbage | Media-baja | Baja por mazo corto | Medio | Mentir creíble o apostar certeza |

### 11.2 Economía de puntos y catch-up

- **Trivia** premia velocidad y racha; el ataque es barato (200) frente a una base de 1,000. El líder puede castigar sin perder su ventaja.
- **Apuesta** tiene banca fija, pozo y rollover; el que compra pistas puede recuperar con factor x4/x2/x1. La información privada es la ventaja principal.
- **Charadas** tiene el premio más alto por acción (1,000 por carta) y el doble pago por timbre; el actor puede dispararse.
- **Impostor** premia la supervivencia (1,500) por encima del crew (1,000+300). El cómplice añade 700. Es una recompensa alta al rol minoritario.
- **Dibujo** premia al artista por cada voto real (600) y al votante por acertar (1,000); una sala de 8 puede dar al artista miles de puntos. La penalización por cero votos es solo 500.
- **Subasta** tiene monedas y puntos; el valor de un lote puede ser positivo o negativo. La liquidación al 50% favorece acumular monedas.
- **Fibbage** tiene mentiras de 500 por voto, veneno de 300 por voto y verdad de 1,000; la ronda final duplica. La certeza puede dar 3,000 en final.

**Riesgo de runaway leader:** alto en Charadas y Dibujo; medio en Trivia; mitigado en Apuesta por rollover y en Subasta por informantes a los que van perdiendo. Fibbage no tiene catch-up específico.

**Kingmaking:** alto en Subasta (informantes, falsa alarma, segundo pago), Dibujo/Fibbage (favorito del público) y Charadas (saboteur).

### 11.3 Contenido

| Fuente | Cantidad | Riesgo |
|---|---:|---|
| Preguntas de Trivia (backend) | 3,125 | Buen volumen; rotación rota |
| Pack Cine | 1,239 | Bien |
| Pack Series | 1,489 | Bien |
| Pack Anime | 281 | Medio |
| Pack Infantil | 61 | Bajo para sesiones repetidas |
| Pack Novelas | 55 | Bajo para sesiones repetidas |
| Lugares de Impostor | 42 | 14 partidas de 3 rondas |
| Consignas de Dibujo | 50 | 16 partidas de 3 rondas |
| Cartas de Subasta | 26 | 4–6 partidas |
| Datos de Fibbage | 30 | 7–8 partidas de 4 rondas |

**Conclusión de contenido:** Trivia tiene volumen; los juegos de fiesta no. Los mazos locales se barajan por sala, pero no rotan por grupo ni por historial. Un grupo recurrente memoriza Fibbage, Subasta y Dibujo.

### 11.4 Público / audiencia

- El público puede entrar después de empezar (`spectator=true`).
- No compite por puntos ni bloquea a los jugadores.
- En Dibujo y Fibbage puede votar en la urna de audiencia y otorgar +300.
- No hay límite de espectadores ni aprobación del host.
- **Problema:** el público es un rol de confianza que recibe un snapshot de jugador. En El Impostor eso filtra la palabra secreta (S1).
- **Problema:** el público puede inflar el +300 con varias sesiones; el rate limit de join es 10/min/IP.

### 11.5 Host, lobby y abandono

- El primer teléfono que entra es host, incluida la sala de la TV.
- Si el host se desconecta 90 s, el host pasa al jugador conectado más antiguo.
- El host puede iniciar, avanzar, cerrar y expulsar por API.
- **La UI del host no expone “expulsar”.** El lobby puede llenarse con jugadores falsos y no hay forma visual de limpiarlo.
- La TV cierra la sala al salir de Juegos (`DisposableEffect`).
- Si la API se reinicia, la sala muere.

### 11.6 Reloj y tiempos

- El servidor usa `time.time()` y `phase_started`; el cliente no puede alterar el puntaje con su reloj.
- `phaseEndsAt` y `phaseDuration` viajan en el snapshot.
- En modo display (`players_pace=true`), el reveal requiere que todos los jugadores conectados marquen “Continuar”; si uno no lo hace, el host puede avanzar.
- En Trivia clásica con `players_pace=true`, la pregunta no tiene fallback por timer: si un jugador no responde, la sala puede quedarse esperando hasta que se desconecte (90 s) o el host actúe. En Trivia v2 sí hay fallback de timer.
- No hay pausa explícita; salir de la app del teléfono no pausa la partida.

### 11.7 Idempotencia y reintentos

- `/action` calcula una huella con jugador, ronda, fase, tipo, target, texto, poder, cable, bid, amount, vote, choices/selected y hash de trazos.
- Si la misma huella llega en menos de 1.5 s, devuelve `True` sin repetir.
- No hay idempotency key explícita ni nonce; un reintento después de 1.5 s puede volver a procesarse, aunque las validaciones de ronda/fase suelen bloquearlo.
- Los votos, pujas y envíos tienen flags de ronda que impiden duplicados.

---

## 12. Transiciones y lenguaje visual

### 12.1 Lo que está implementado

**Selector de TV**

- Rejilla 4×2 con 7 tarjetas full-bleed y una octava tarjeta `Ruleta Vision+`.
- Foco con escala 1.04, borde degradado por acento y fondo del plató tintado con `animateColorAsState`.
- Cabecera con píldoras de mecánicas v2 del juego enfocado.
- Presentadora reactiva al foco.
- Guía `OK` / `FLECHAS`.
- Sin blur ni sombras caras.
- Ruleta que elige un juego al azar y abre la sala; Trivia entra en `general`.

**Lobby de TV**

- Arte del juego a sangre completa, con degradado y resplandor.
- Tarjeta de unión con QR de 190 dp, código grande y texto de instrucciones.
- 8 puestos visibles en 2 filas de 4, borde punteado para libres.
- Progreso `0/8`, píldoras de mínimo, bloque “Cómo se juega”.
- Presentadora compacta.
- Botón `Cambiar juego` con foco azul y borde blanco.
- Fondo y borde con acento del juego.
- Verificado en Chromecast: crear sala, ver QR, foco y volver al selector.

**Capturas de referencia**

- Selector: `https://files.catbox.moe/fzmhdo.png`
- Lobby: `https://files.catbox.moe/zj6im3.png`
- Reporte visual previo: `https://files.catbox.moe/c7w4p5.md`

**TV de juego**

- `TvDibujo` usa `AnimatedVectorDrawingCanvas`, con interpolación de trazos y linterna radial.
- `TvSubasta` usa un martillo animado con `animateFloatAsState`.
- `TvTriviaV2`, `TvWagerV2`, `TvImpostor`, `TvFibbage` usan paneles y badges, sin transición de fase.
- `TvGameModesScreen` usa `TvModePanel` y `TvModeHeader`.

**Teléfono**

- Formularios Material 3 con scroll.
- Haptics en los juegos de fiesta (Impostor, Dibujo, Subasta, Fibbage).
- Sin haptics específicos en Trivia v2, Apuesta v2 ni Charadas v2.
- Canvas táctil para dibujo.
- No hay transiciones animadas entre fases; el contenido cambia por recomposición.

### 12.2 Lo que está generado pero no conectado

| Componente | Estado | Uso actual |
|---|---|---|
| `GamePhaseTransition` | Definido en `GameTransitionsV2.kt` | **No usado** |
| `DramaticRevealCard` | Definido | **No usado** |
| `LightweightParticleOverlay` | Definido | **No usado** |
| `AnimatedVectorDrawingCanvas` | Definido | Usado en `TvDibujo` |
| Partículas `game_fx_*` | 5 sprites generados | **No usados** |
| Tableros `game_tut_*` | 7 tableros + alias + iconos | **No usados** |
| Avatares `game_avatar_*` | 36 archivos (12×3) | **No usados** |
| 60 audios v2 | Generados/auditados | **No referenciados por `GameNarrator`** |

**Consecuencia:** la sensación de “juego terminado” depende de la UI actual, no de la capa de transiciones ya escrita. El reveal de Dibujo/Fibbage es una lista; no hay volteo 3D. El podio no tiene partículas. Los tutoriales no se ven.

### 12.3 Problemas visuales / de rendimiento

1. **No hay transición de fase:** el `when` de `TvGamesScreen` cambia de pantalla directamente. `GamePhaseTransition` no está cableado.
2. **`AnimatedVectorDrawingCanvas` no respeta `LocalReducedEffects`:** la linterna usa una animación infinita aunque el equipo tenga efectos reducidos.
3. **`LightweightParticleOverlay` no escala el sprite:** dibuja el WebP de 128×128 a tamaño nativo; si se conecta tal cual, las partículas serán enormes. No está listo para producción.
4. **`DramaticRevealCard` no respeta `LocalReducedEffects`:** el volteo 3D se anima siempre; si se conecta, conviene un modo reducido.
5. **Acentos inconsistentes:** selector con acentos v2 saturados; lobby y juego con acentos pastel del catálogo.
6. **Texto pequeño:** chips de tarjeta a 9–10 sp y algunos labels de lobby a 7–8 sp. En una TV de 3 m pueden ser límite. En la captura 1080p se leen, pero conviene medir en la TV física.
7. **La TV no tiene barra de progreso en `TvModeHeader`:** solo número de segundos; el teléfono sí tiene barra.
8. **El selector no tiene “hay más” porque ya no hay scroll:** ahora se ven 7 + Ruleta; es una decisión correcta.
9. **El lobby muestra “Libre” en los puestos vacíos:** se corrigió el truncado de “Puesto libre” en 4 columnas.

---

## 13. Imágenes, assets y voces

### 13.1 Inventario de imágenes de juego

Total: **101 archivos WebP, 2.15 MB**. Todos en `app/src/main/res/drawable-nodpi`.

| Grupo | Archivos | Tamaño | Uso real |
|---|---:|---:|---|
| Portadas 16:9 | 7 | 197 KB | Selector, lobby, GamesHome |
| Fondos TV noir | 3 | 89.5 KB | `GameStageBackground` para modos |
| Cartas de subasta | 5 | 192 KB | Teléfono y TV |
| Roles Impostor | 2 | 40 KB | Teléfono |
| Medallas | 5 | 33 KB | Podio |
| Avatares 12×3 | 36 | 715 KB | **Sin uso** |
| Tutoriales (7 tableros + alias + 24 iconos) | 38 | 910 KB | **Sin uso** |
| Partículas | 5 | 21.4 KB | **Sin uso** |
| Total | 101 | 2.15 MB | |

**Portadas:** 1024×576, 13–39 KB. Excelente peso para TV.
**Fondos TV:** 1280×720, 15–50 KB.
**Cartas de subasta:** 512×768, 25–48 KB.
**Roles:** 640×360, 11–29 KB.
**Avatares:** 256×256, 13–27 KB.
**Tutoriales:** 1280×720 (20–44 KB) y 512×512 (10–36 KB).
**Partículas:** 128×128, 3–7 KB.

**Narradora:** 11 PNG en `drawable-nodpi` (idle 4 frames, static, welcome, thinking, happy, oops, celebrate, avatar), ~1.8 MB.
**No se encontraron imágenes de:** cartas de rol de Charadas, fondo de subasta por ronda, tutoriales de TV conectados, avatares de jugador, iconos de herramientas de dibujo.

### 13.2 Uso por pantalla

- **Selector:** 7 portadas + arte de Ruleta; fondo ambiental.
- **Lobby:** portada del juego a sangre; QR; acentos.
- **Trivia:** arte de pregunta si `art` existe; avatares de narradora.
- **Apuesta:** póster en TV/telefono (con el leak de URL).
- **Charadas:** póster del título en el teléfono del actor/saboteur; TV no.
- **Impostor:** roles `game_imp_role_*`; sin arte de lugar.
- **Dibujo:** canvas vectorial; sin arte de fondo por ronda.
- **Subasta:** cartas `game_auc_card_*`.
- **Fibbage:** sin arte de dato; solo tipografía.

### 13.3 Voces

- Motor: **Qwen3-TTS-12Hz-1.7B-Base** (Apache 2.0), clonando una voz femenina mexicana de referencia.
- Salida: AAC-LC mono 44.1 kHz, 48 kbps, normalizada a −16 LUFS.
- 6 tomas por línea; selección por rango tonal, energía, ritmo, duración y WER.
- La app reproduce a velocidad natural (`VOICE_SPEED = 1.0`).
- **308 archivos `.m4a`** en `res/raw`, 7.6 MB.
- **56 momentos** en total:
  - 20 momentos clásicos × 10 variantes = **200 clips**.
  - 16 momentos v2 × 3 variantes = **48 clips**.
  - 20 momentos v2/tutorial × 3 variantes = **60 clips**.
- **248 clips están referenciados por `GameNarrator`**.
- **60 clips v2 no están referenciados por `GameNarrator`:** 13 mecánicas v2 + 7 tutoriales. Incluyen:
  - `trivia_attack_window`, `trivia_punishment_wires`
  - `wager_side_bet_alert`
  - `charada_director_pick`, `charada_buzz_pause`, `charada_saboteur_win`
  - `impostor_coartada_start`
  - `drawing_ink_limit`, `drawing_flashlight_round`
  - `auction_cursed_lot`, `auction_ghost_alarm`, `auction_shield_reflect`
  - `fibbage_poison_hit`
  - 7 `tutorial_*_v2`
- **Problema:** la voz de la presentadora no explica las mecánicas nuevas; la TV usa eventos genéricos (`Started`, `Wrong`, `Reveal`, `FinalRoundDouble`) para ataque, castigo, ráfaga y asalto.
- **Problema:** no hay voz para el cazador de Fibbage ni para el doble hueco final.
- **Problema:** los bocadillos dinámicos (nombres, números, pistas privadas, lobby) no siempre tienen audio. La voz es por evento, no por línea exacta.
- **Problema:** solo hay 3 variantes por momento v2; a la tercera partida se nota la repetición.
- **Problema:** una sola voz para todos los personajes; no hay subastador, detective ni narrador alternativo.
- **Problema:** no hay subtítulos ni selector de voz separado. El switch “narrador” también apaga SFX y música ambiental, porque `GameSfx` y `GameAmbient` comparten el mismo `narratorEnabled`.

### 13.4 SFX y música

- `GameAmbient`: pad de 30 s en loop, 360 KB, volumen 0.14; baja a 0.05 cuando habla la narradora.
- 11 SFX `.wav`: tick, vote, secret, reveal, hammer, whoosh, buzzer, glass, heartbeat, safe, fanfare.
- Los 5 nuevos: buzzer, glass, heartbeat, safe, fanfare.
- **Heartbeat no se usa.** Buzzer se usa en Charadas; glass en empate/castigo; safe en coartada; fanfare en podio.
- Los SFX suenan solo en la TV, con ducking. No hay mezcla por escena ni espacialización.
- La música ambiental se inicia al entrar a Juegos y se detiene al salir.

### 13.5 Tutoriales

- 7 tableros finales 1280×720 con 3 iconos compuestos:
  - `game_tut_trivia`, `game_tut_charada`, `game_tut_wager`, `game_tut_impostor`, `game_tut_drawing`, `game_tut_auction`, `game_tut_fibbage`.
- Alias con IDs largos (`game_tut_charadas`, `game_tut_dibujo`, `game_tut_subasta`).
- 24 iconos 512×512 de apoyo.
- `tutorialBoardRes()` existe en `GameArt.kt`.
- **No hay ninguna pantalla que use `tutorialBoardRes()`**: solo hay un test de existencia de assets. Los tutoriales no se ven en el juego.
- **Los 7 tutoriales de voz** están en los 60 clips no referenciados.

### 13.6 Evaluación de assets

**Fortalezas**

- Peso total bajo para Chromecast HD.
- Portadas 16:9 consistentes.
- Cartas de subasta y roles con identidad.
- Paleta oscura con acentos.
- Sin blur caro en selector/lobby.
- Assets de narradora con poses.

**Debilidades**

- 36 avatares de mascota sin uso.
- 38 archivos de tutorial sin uso.
- 5 partículas sin uso.
- 60 audios v2 sin uso.
- 1 SFX sin uso.
- 3 de 4 componentes de transición sin uso.
- No hay arte de lugar para Impostor; el juego depende de texto.
- No hay cartas de rol de Charadas; el rol se explica con texto.
- No hay iconos de herramientas de dibujo; se usan colores y textos.
- No hay arte de “póster pixelado” real; se usa alpha.
- No hay arte de “doble hueco” de Fibbage.

---

## 14. Seguridad, secretos y trampas

### 14.1 Modelo de confianza

| Actor | Confianza | Puede |
|---|---|---|
| TV | Media | Mostrar estado público; no debe recibir secretos |
| Teléfono jugador | Baja | Enviar acciones; puede inspeccionar JSON y modificar el cliente |
| Público | Baja | Ver y votar en urnas de audiencia; no debería ver secretos |
| Host | Media | Iniciar, avanzar, cerrar, expulsar |
| Servidor | Alta | Autoridad de fases, timers, puntajes y filtrado |
| API key | Baja | Estática, embebida en el APK; no identifica usuario |

**Principio correcto:** el servidor no confía en el reloj ni en el puntaje del cliente.
**Principio incompleto:** varias reglas de juego se aplican solo en la UI (póster oculto, tinta, escáner, opciones tachadas, roles de v1). Un cliente modificado las salta.

### 14.2 Matriz de secretos

| Secreto | Quién debería verlo | Quién lo ve hoy | Estado |
|---|---|---|---|
| Respuesta de Trivia | TV y teléfonos en reveal | Correcto | OK |
| Respuestas de sort/assault | En reveal | Correcto | OK |
| Opciones de Trivia glitch | Solo el objetivo ve opciones censuradas | El objetivo ve censura; la TV ve opciones completas | Débil por TV |
| Póster de Apuesta | Nadie hasta la etapa privada 3/reveal | **Todos desde la pista 1** | **P0** |
| Pista privada de Apuesta | Solo el comprador | Correcto en `wager.you.privateClue` | OK |
| Título de Charadas | Actor, saboteur, reveal | Correcto | OK |
| Identidad del saboteur | Reveal | Correcto | OK |
| Palabra de Impostor | Crew y reveal | Crew, reveal y **espectadores** | **P0** |
| Rol de Impostor | Cada jugador | Correcto para jugadores; espectador aparece como crew | **P0** |
| Opciones del detective | Solo detective | Correcto para jugador; no para TV | OK |
| Prompt de Dibujo | Artista y reveal | Correcto | OK |
| Trazos en vivo | TV y artista | Correcto; otros no los reciben durante draw | OK |
| Títulos/autores de Dibujo | Reveal | Correcto | OK |
| Efecto secreto de Subasta | Informantes y reveal | Correcto | OK |
| Pujas de Subasta | Reveal | Correcto | OK |
| Respuesta de Fibbage | Reveal | Correcto | OK |
| Autores de mentiras | Reveal | Correcto | OK |
| Opciones escaneadas | Solo el jugador que escaneó | El servidor las entrega; la UI las oculta | **Bypass UI** |
| Token de jugador | Solo el dispositivo | Correcto en snapshot (nunca viaja) | OK |
| Host token | Solo la TV | Correcto en snapshot | OK |

### 14.3 Hallazgos detallados

#### S1 — El espectador ve la palabra del Impostor (P0, crítico)

**Código:** `server/api/games.py`, `Room.snapshot()`, líneas 3702–3710:

```python
is_impostor = bool(me and me.id == self.impostor_id)
is_accomplice = bool(me and me.id == self.impostor_accomplice_id)
is_detective = bool(me and me.id == self.impostor_detective_id)
can_see_word = reveal or (
    role == "player" and me is not None
    and not is_impostor and not is_accomplice and not is_detective
)
```

No se comprueba `me.spectator`. Un espectador no es impostor, cómplice ni detective, así que entra en `can_see_word=True`.

Además, `role_name` se calcula como `"crew"` para cualquier jugador que no sea especial, incluido el espectador. La UI de `ImpostorPhoneContent` usa `data.role` y `data.word`, así que el público ve la tarjeta de crew con el lugar.

**Reproducción verificada:**

```text
phase = secret
palabra = Metro
snapshot del espectador → impostor.word = "Metro"
impostor.role = "crew"
you.role = ""
```

**Impacto:** el público puede decir el lugar en voz alta, delatar al impostor o usarlo para su propio grupo. Rompe el juego.

**Fix recomendado:** excluir `me.spectator` de `can_see_word`, devolver `role="audience"` y ocultar `word/category/hint`; en la UI, si `me.spectator`, mostrar una tarjeta de público sin rol ni palabra. Añadir test de snapshot para espectador en `secret`, `questions` y `reveal`.

#### S2 — Póster completo de Apuesta filtrado (P0, alto)

**Código:** `server/api/games.py`, `Room.snapshot()` v2:

```python
"poster": data.get("poster", ""),
...
"posterReveal": 1.0 if self.phase == "reveal" else (0.06, 0.5, 1.0)[stage - 1],
```

Y en la pregunta:

```python
"art": data.get("poster", ""),
```

El snapshot de cada jugador lleva la URL completa del póster desde la pista 1. La UI de `WagerV2Content` solo pinta la imagen si `privateStage >= 3 || reveal`, pero el JSON ya tiene la URL.

**Reproducción verificada:** en `phase=clue`, `privateStage=1`, `privatePosterReveal=0.06`, el snapshot contiene `wager.poster` y `question.art` con la URL real.

**Impacto:** un jugador puede abrir la URL o inspeccionar el JSON y resolver el título sin comprar pistas. La economía de información de Apuesta queda anulada.

**Fix recomendado:** no enviar `poster` ni `art` al jugador hasta que la etapa privada lo permita; para TV, enviar solo una versión derivada (crop/pixelado) o una URL firmada de baja resolución. No usar la URL original como “oculta”. Añadir test: en `clue` etapa 1, `snapshot["wager"]["poster"] == ""` y `snapshot["question"]["art"] == ""` para no-compradores.

#### S3 — Charadas paga dos veces al actor (P0, alto)

**Código:**

`_finish_charada_buzz()` (líneas 935–965):

```python
if correct:
    buzzer.score += CHARADA_BUZZ_POINTS
    ...
    if actor is not None:
        actor.score += CHARADA_POINTS
        self.deltas[actor.id] += CHARADA_POINTS
    self.charada_correct += 1
```

`_begin_reveal()` (líneas 2031–2050):

```python
earned = max(0, self.charada_correct * CHARADA_POINTS - self.charada_help_penalty)
if v2 and director_chosen and correct > 0:
    earned += CHARADA_DIRECTOR_BONUS
    director.score += CHARADA_DIRECTOR_BONUS
actor.score += earned
```

**Reproducción verificada:**

- Actor empieza en 0.
- Timbre correcto: actor 1,000.
- `_begin_reveal`: actor 2,300 (1,000 + 1,000 + 300 de director).
- Si el actor marca “Correcto” manual sin timbre: actor 1,300 (1,000 + 300).
- El test `test_charadas_v2_buzzer_and_helpers` solo exige `>= 800 + 1000`, así que no detecta el exceso.

**Impacto:** el timbre es la vía óptima para el actor; el puntaje no es comparable con la vía manual. En una partida de 6 rondas, un actor puede inflar miles de puntos.

**Fix recomendado:** en `_finish_charada_buzz`, no sumar `CHARADA_POINTS` al actor; solo marcar el acierto, avanzar carta y dejar que `_begin_reveal` pague una vez. O, si se quiere pagar al instante, marcar un flag y no volver a pagar en reveal. Añadir test exacto de puntaje después de timbre y después del reveal.

#### S4 — Adivinanza temprana de Dibujo acepta stopwords (P0, alto)

**Código:** `draw_early_guess()` (líneas 2172–2200):

```python
guess = fold(clean)
if len(guess) >= 3 and guess in prompt:
    self.drawing_early_winners[player_id] = 400
```

No hay `word boundary`, ni lista de stopwords, ni requisito de que el dibujo tenga trazos. `que` aparece en 13 consignas, `una` en 22, `con` en 9. Con 2 intentos, un jugador puede probar dos stopwords y cobrar +400 sin adivinar el título.

**Impacto:** el bono de clarividente deja de ser una lectura del dibujo; es una lotería de palabras vacías. Además, el intento se puede hacer antes de que el artista dibuje.

**Fix recomendado:** normalizar por palabras completas (`re.findall`), excluir stopwords y exigir que la coincidencia sea una palabra de contenido; o mejor, exigir una coincidencia semántica del título completo (con tolerancia) y no solo una subcadena. Añadir test con `que`, `una`, `con` y con una palabra de contenido real.

#### S5 — Límite de tinta no aplicado en servidor (P0, alto)

**Código:** `draw_partial` y `draw_submit` (líneas 2845–2873):

```python
self.drawing_strokes = _clean_strokes(strokes)
self.drawing_ink_used = _ink_cost(self.drawing_strokes)
```

No hay comparación contra `self.drawing_ink_limit`. La UI muestra la tinta y el bono, pero el servidor acepta cualquier dibujo.

**Reproducción verificada:** ronda 2, `ink_limit=100`, se enviaron 2,400 puntos y se aceptó `ink_used=11,979.2`.

**Impacto:** la regla “tinta limitada” solo afecta al bono de artista; un cliente modificado dibuja con detalle infinito. La penalización es solo no obtener el bono, no perder la ronda.

**Fix recomendado:** calcular el costo antes de aceptar; si `cost > limit`, devolver `False, "tinta"` o truncar los trazos al límite. Aplicarlo tanto en `draw_partial` como en `draw_submit`. Añadir test de rechazo y de bono exacto en ≤50%.

#### S6 — Mecánicas v2 activas en salas v1 (P0, compatibilidad)

**Evidencia verificada:**

- `rules_version=1`, Impostor con 6 jugadores: `accomplice=True`, `detective=True`, fase `secret`. El código `_begin_impostor` no consulta `rules_version`.
- `rules_version=1`, Dibujo ronda 2: `drawing_ink_limit=100`. `_begin_drawing` no consulta `rules_version`.
- `rules_version=1`, Subasta: `auction_ghost` aceptado. `act()` no consulta `rules_version`.
- `rules_version=1`, Fibbage: la UI muestra veneno/escáner/certeza; el servidor ignora el veneno, rechaza el escáner y no activa la certeza, pero la UI no se adapta.
- `rules_version=1`, Apuesta: el flujo v1 se conserva; Trivia v1 se conserva.

**Impacto:** la promesa “v1 sigue intacto” no se cumple en los juegos de fiesta. Un cliente que cree estar en v1 recibe roles y reglas v2.

**Fix recomendado:** centralizar un helper `v2 = rules_version >= 2` y gatear roles, tinta, linterna, falsa alarma, veneno, escáner, certeza, cazador y coartada en servidor y UI. Añadir una suite v1 por juego de fiesta.

---

### 14.4 Hallazgos P1

#### S7 — Rotación de preguntas de Trivia inactiva

- `TvGamesScreen` llama a `GameClient.create` sin `syncCode` (líneas 166–172).
- `GameClient.create` acepta `syncCode=""`; el header `X-Sync-Code` no se envía.
- `app.py` calcula `group_id = _game_group_id(x_sync_code)`; sin header, `group_id=""`.
- `_game_questions` filtra `s.group_id = $2` y solo inserta en `trivia_served` si `group_id` no está vacío.
- `/v1/game/health` devuelve `served: 0` con 3,125 preguntas.

**Impacto:** el sistema de 90 días no se usa; las preguntas pueden repetirse. No es un leak, pero reduce profundidad y confianza.

**Fix:** generar un `sync_id` persistente para la TV (o aceptar el `syncCode` del dispositivo) y pasarlo en create. Test de integración que verifique que una segunda sala del mismo grupo no repite.

#### S8 — Reporte de pregunta permite autoborrado

- `game_report` inserta un registro por llamada, sin deduplicar por jugador/pregunta.
- No usa `_GAME_REPORT_BUCKET`, aunque está definido.
- Si `count >= 3`, borra la pregunta de `trivia_questions`.

**Impacto:** un solo jugador puede reportar la misma pregunta 3 veces y borrarla. Es sabotaje de contenido y pérdida de banco.

**Fix:** una fila por `(question_id, player_id)` con `ON CONFLICT DO NOTHING`; rate limit; revisión humana antes de borrar; no borrar en caliente.

#### S9 — Puja de Subasta por debajo del mínimo

- `act("auction_bid")` limita `amount` a `player.coins`.
- Luego redondea a múltiplos de 50: `max(AUCTION_STEP, (amount // 50) * 50)`.
- Si `coins=25`, el resultado es 50, por encima del saldo.
- Verificado: puja 25 → almacenada 50 → gana el lote y queda con 0 monedas.

**Impacto:** se puede comprar con dinero que no se tiene; economía y farol se rompen en el borde.

**Fix:** redondear y luego volver a limitar: `amount = min(player.coins, max(50, (amount // 50) * 50))`; si `coins < 50`, permitir 0 o el saldo exacto. Test con 25, 49 y 50.

#### S10 — Público sin límite y con +300

- `add_player(... spectator=True)` no aplica el máximo de jugadores.
- `game_join` no limita el número de espectadores.
- En Dibujo y Fibbage, la urna de audiencia puede dar +300.
- Rate limit de join: 10/min/IP.

**Impacto:** un jugador puede abrir varias sesiones de público y inflar el favorito, además de espiar como en S1. No rompe el marcador principal, pero sí la recompensa de audiencia.

**Fix:** tope de espectadores, aprobación del host o un solo voto de audiencia por dispositivo; separar la recompensa de audiencia de la economía principal o eliminarla.

#### S11 — Límites de UI en salas grandes

- Trivia: `attack.targets.take(4)` en `GameV2Screens.kt` línea 252. Con 8 jugadores hay 7 objetivos; solo se pueden elegir 4.
- Apuesta: `wager.lockedStage1.filter { ... }.take(3)` línea 859. Con 8 jugadores puede haber más de 3 bloqueados en etapa 1.

**Impacto:** los jugadores no pueden usar todas las opciones que el servidor permite. Es una pérdida de agencia y una ventaja para los primeros de la lista.

**Fix:** rejilla con scroll o “ver todos” en el teléfono; test con 8 jugadores.

#### S12 — Fibbage: cazador y doble hueco incompletos

- `fibbage_hunter` existe en `act()` y en el scoring, pero no tiene UI ni test.
- `fibbage_final_shared` solo activa el multiplicador 2x; no hay doble hueco ni dos respuestas compartidas.
- El test `test_fibbage_v2_final_round_is_shared_truth` solo verifica el flag, no la mecánica.

**Impacto:** dos promesas de profundidad no están en el producto. El experto debe decidir si se implementan o se retiran de la documentación.

**Fix:** implementar UI/flujo del cazador y del doble hueco, o marcar ambas como “fuera de alcance” y eliminar el flag/acción del servidor para no confundir.

#### S13 — Escáner de Fibbage solo se aplica en UI

- `fibbage_scan` devuelve `scanned` y la UI filtra esas opciones.
- `fibbage_vote` no comprueba que el target no esté en `scanned`.

**Impacto:** un cliente modificado puede votar una opción que el escáner debería haber tachado. Es una ventaja menor, pero es una regla de UI.

**Fix:** en `fibbage_vote`, rechazar targets escaneados por el jugador.

#### S14 — Espectador puede enviar respuestas de Trivia

- `answer()` no comprueba `player.spectator`.
- Un espectador puede guardar una respuesta; no entra en el marcador porque `players_payload` y `result` excluyen espectadores, y no bloquea `_all_answered`, pero es una acción no prevista.

**Impacto:** bajo; no permite ganar ni bloquear. Es una inconsistencia de permisos.

**Fix:** rechazar `player.spectator` en `answer` y en el flujo legacy de wager.

#### S15 — `/state?role=display` sin autenticación de TV

- Cualquiera con la API key estática puede pedir el estado de display.
- No hay token de TV, firma ni límite de polling.

**Impacto:** permite observar el estado público y hacer timing; no debería filtrar secretos (salvo el problema de opciones completas en Trivia). También es una superficie de coste.

**Fix:** token de display o firma de TV; rate limit de estado.

#### S16 — Lobby squatting y falta de expulsión en UI

- Cualquier teléfono con el código puede entrar antes de empezar.
- El primero es host; no hay aprobación.
- El servidor tiene `kick`, pero la UI del host no lo expone.
- Se pueden llenar 8 puestos con sesiones falsas (10 joins/min/IP).

**Impacto:** denegación de sala y host no deseado. En un entorno doméstico es social; en un evento público es un problema.

**Fix:** aprobación de ingreso, botón de expulsar en el host, límite por dispositivo y sala.

#### S17 — Salas en memoria

- Si la API se reinicia, las salas activas se pierden.
- No hay multi-instancia; un balanceador con varios workers rompería la continuidad.
- Solo se persiste el resultado al cerrar.

**Impacto:** una partida en curso se pierde por un deploy o un crash. Para OTA y soporte, conviene persistir el estado mínimo o aceptar la pérdida con aviso.

---

### 14.5 Hallazgos P2 / deuda de producto

1. **Contenido estático** de fiesta: 42/50/26/30; repetición a corto plazo.
2. **Datos de Fibbage sin fuente** y algunos ambiguos.
3. **60 audios v2 sin integrar.**
4. **7 tutoriales sin integrar.**
5. **36 avatares sin uso.**
6. **5 partículas sin uso.**
7. **3 de 4 componentes de transición sin uso.**
8. **Heartbeat sin uso.**
9. **Acentos inconsistentes** selector vs catálogo.
10. **Texto pequeño** en chips/labels.
11. **Sin subtítulos ni localización.**
12. **Una sola voz** para todos los personajes.
13. **Sin prueba E2E HTTP** de los endpoints nuevos.
14. **Sin load test** de 8–12 jugadores.
15. **Sin prueba física** con teléfonos.
16. **Sin telemetría** de acciones sospechosas o abandonos.
17. **Sin cifrado de tokens en el teléfono.**
18. **API key estática** en el APK.
19. **Nombres con emoji** permitidos; pueden salir como cuadros en TV.
20. **La TV puede mostrar `RED/BLUE/...`** mientras el teléfono muestra “Rojo/Azul/...”.

---

## 15. Pruebas y verificación

### 15.1 Conteo actual

| Suite | Total | Notas |
|---|---:|---|
| Backend | 570 reportadas | 565 funciones `def test_`; 104 específicas de juegos: 42 + 21 + 14 + 27 |
| Android | 1,143 | ~74 específicas de juegos; suite completa re-ejecutada en verde |
| Launcher | 176 reportadas | 181 métodos `@Test` contados; sin juegos; no re-ejecutada |
| Física | 0 | Pendiente con 2–3 teléfonos |

### 15.2 Cobertura de servidor

**Bien cubierto**

- Códigos, tokens, nombres y colores.
- Puntaje clásico y paridad Kotlin/Python.
- Respuesta correcta oculta hasta reveal.
- Una respuesta por jugador/ronda; tardía rechazada.
- Host failover, kick, TTL, idle, cierre.
- Snapshot 204 por `rev`.
- Salas display con ritmo de teléfonos.
- Deck de Charadas y Apuesta; redacción de título.
- Trivia v2: ataque, glitch, parásito, castigo, sort, assault.
- Apuesta v2: banca, pozo, compra, lock, rollover, side bet.
- Charadas v2: director, saboteur, timbre, ayudas.
- Impostor v2: cómplice, detective, coartada, empate/guess.
- Dibujo v2: tinta, early guess, críticos.
- Subasta v2: compuesto, ghost, segundo pago.
- Fibbage v2: veneno, escáner, certeza, final flag.

**No cubierto / insuficiente**

- Espectador en Impostor (S1).
- Póster de Apuesta (S2).
- Doble pago de Charadas (S3).
- Stopwords de early guess (S4).
- Tinta sobre límite (S5).
- v1 en juegos de fiesta (S6).
- Rotación de preguntas con `X-Sync-Code` (S7).
- Reportes duplicados (S8).
- Puja bajo saldo (S9).
- Tope/límites de audiencia (S10).
- UI de 8 jugadores para ataques y side bets (S11).
- Cazador y doble hueco reales (S12).
- Escáner aplicado en servidor (S13).
- E2E HTTP, carga, latencia, reconexión real.
- Prueba física de dibujo, QR, audio y foco.

### 15.3 Cobertura de Android

**Bien cubierto**

- Catálogo, códigos, deep link, QR.
- Modelos v2/v3/v4 y parseo.
- Cliente HTTP, headers, timeouts, errores.
- Puntaje y narrador.
- Wiring de TV/phone.
- Lobby de TV.
- `UiNoEmojiTest`.

**No cubierto**

- Interacción real multi-teléfono.
- Foco D-pad en partida real.
- Audio de la narradora en TV.
- Rendimiento de dibujo y partículas.
- Accesibilidad / tamaño de texto / contraste.
- Rotación de pantalla del teléfono.
- Lectura de QR desde un teléfono real.

### 15.4 Verificación de backend en vivo

Consulta real del 30 de octubre de 2026:

```json
{"ok": true, "rooms": 2, "questions": 3125, "served": 0, "version": 1793397675, "maxRooms": 100}
```

`served: 0` es la evidencia de que la rotación de preguntas no se está usando en salas reales.

### 15.5 Verificación de APK

- Release R8 compilado.
- Motor X4K inyectado en `classes3/4/5.dex`.
- Firma histórica `9593cb0d…`.
- Instalado en Chromecast HD por ADB.
- Selector y lobby verificados; creación de sala y QR verificados.
- No se publicó OTA.

### 15.6 Discrepancias entre documentación y código

| Documento | Dice | Código actual |
|---|---|---|
| `ENTREGA_GAMES_V2.md` | “Pistas privadas en teléfono; la TV no las filtra” | La UI las oculta, pero `wager.poster` y `question.art` viajan en el JSON desde la pista 1 |
| Reporte de producción | “60 voces TTS v2 auditadas” | 60 clips v2 están generados, pero no referenciados por `GameNarrator`; 248 clips sí están cableados |
| Reporte de producción | “Transiciones listas” | Solo `AnimatedVectorDrawingCanvas` se usa; 3 componentes están sin cablear |
| Handoff Fase 4 | “v1 sigue intacto” | Impostor, Dibujo, Subasta y Fibbage tienen regresiones v2 |
| Handoff Fase 4 | “selector 4×2 con 7 tarjetas” | Cierto, ahora con Ruleta en la octava celda |
| Documentación de assets | Tutoriales y avatares generados | 38 archivos de tutorial y 36 avatares sin uso |
| Handoff Fase 4 | “14 tests de juegos de fiesta” | Hoy hay más, pero faltan los casos P0 de esta auditoría |

---

## 16. Backlog priorizado

### P0 — antes de cualquier OTA o partida competitiva

1. **S1** Espectador no debe ver palabra/rol de Impostor.
2. **S2** No enviar póster de Apuesta hasta la etapa permitida.
3. **S3** Corregir doble pago de Charadas y decidir verificación de “Correcto”.
4. **S4** Endurecer early guess de Dibujo.
5. **S5** Aplicar límite de tinta en servidor.
6. **S6** Gatear todas las mecánicas v2 en v1 (servidor + UI) y tests v1.
7. **S7** Hacer que la TV envíe un `X-Sync-Code` persistente.
8. **S8** Un reporte por jugador/pregunta y rate limit; no autoborrar sin revisión.
9. **S9** Corregir puja mínima vs saldo.
10. **S10** Tope de público y una sola urna por dispositivo; o retirar +300 de audiencia.

### P1 — antes de promocionar la OTA

11. **S11** Mostrar todos los objetivos de ataque y side bets en salas de 8.
12. **S12** Implementar o retirar cazador/doble hueco de Fibbage.
13. **S13** Rechazar en servidor opciones escaneadas.
14. **S14** Rechazar espectador en `answer` y wager legacy.
15. **S15** Token/firma de display y rate limit de `/state`.
16. **S16** Botón de expulsar y aprobación de ingreso.
17. **S17** Persistencia mínima o aviso de pérdida de sala.
18. **Tutoriales** Integrar los 7 tableros y las 7 voces en el flujo de entrada.
19. **Voces** Integrar los 60 clips v2 o marcar el set como no usado.
20. **Transiciones** Cablear `GamePhaseTransition`, `DramaticRevealCard` y `LightweightParticleOverlay` con modo reducido.

### P2 — profundidad y pulido

21. Ampliar/rotar mazos de fiesta: Fibbage a 100+ datos, Subasta a 50+ lotes, Dibujo a 100+ prompts, Impostor a 80+ lugares.
22. Verificar datos de Fibbage con fuente.
23. Añadir fuente/dificultad a Fibbage.
24. Añadir equipos/torneo/revancha.
25. Añadir subtítulos y localización.
26. Añadir telemetría de juego y embudos.
27. Añadir E2E HTTP y load test.
28. Añadir prueba física con 2–3 teléfonos.
29. Unificar acentos selector/catálogo.
30. Revisar tamaño de texto a 3 m.

---

## 17. Preguntas concretas para el experto

1. ¿El leak del espectador en Impostor es bloqueante absoluto o se puede resolver con una vista de público?
2. ¿Aceptamos el auto-reporte del actor en Charadas como confianza social o exigimos verificación por timbre/host?
3. ¿El póster de Apuesta debe desaparecer del JSON o basta una imagen derivada/pixelada?
4. ¿La adivinanza temprana de Dibujo debe exigir título completo, palabra de contenido o solo ser una corazonada con coste?
5. ¿La tinta limitada debe ser una regla dura del servidor o un bono opcional?
6. ¿La audiencia debe existir como rol de primera clase o es un riesgo de Sybil que conviene eliminar?
7. ¿Vale la pena mantener v1 en la UI o solo como compatibilidad de API?
8. ¿La rotación de preguntas de Trivia es suficiente con 3,125, o hay que forzar el grupo?
9. ¿El reparto de puntos de Charadas (1,000/700/600/300) es justo después de corregir el doble pago?
10. ¿El escape del impostor a 1,500 frente a 1,000+300 del crew está balanceado?
11. ¿La subasta con empate que cancela y segundo que paga es suficientemente clara?
12. ¿Fibbage debe tener cazador y doble hueco, o es mejor simplificarlo?
13. ¿Los 42/50/26/30 mazos aguantan una noche de 8 jugadores? ¿Y un mes?
14. ¿La voz única y 3 variantes v2 son suficientes para la presentadora?
15. ¿Hay que cablear las transiciones ya escritas o el cambio directo es aceptable?
16. ¿El tamaño de texto de las tarjetas y chips se lee a 3 m en TV de 55–65”?
17. ¿El modelo de host “primer teléfono” es seguro para eventos públicos?
18. ¿El servidor en memoria es aceptable para una OTA doméstica o necesitamos persistencia?
19. ¿La API key estática y el `role=display` sin token son aceptables?
20. ¿Qué pruebas E2E y de carga mínimas exige antes de publicar?

---

## Apéndice A — Fases y duraciones

| Juego | Fases | Timers por defecto |
|---|---|---|
| Trivia v2 | countdown, attack, question, punishment, punishment_reveal, sort, assault, reveal, podium | 3 / 6 / 20 / 8 / 5 / 18 / 25 / 8 |
| Trivia v1 | countdown, question, reveal, podium | 3 / 20 / 5 |
| Charadas | countdown, director, turn, reveal, podium | 3 / 5 / 60 / 5; timbre 7 |
| Apuesta v2 | countdown, clue, reveal, podium | 3 / 15 / 8 |
| Apuesta v1 | countdown, wager, clue, reveal, podium | 3 / 10 / 15 / 5 |
| Impostor | countdown, secret, questions, coartada, vote, guess, reveal, podium | 3 / 11 / 25-15 / 8 / 22 / 20 / 5 |
| Dibujo | countdown, draw, title, vote, reveal, podium | 3 / 45 / 30 / 22 / 5 |
| Subasta | countdown, auction, auction_reveal, podium | 3 / 15 / 12 |
| Fibbage | countdown, write, vote, reveal, podium | 3 / 30 / 22 / 5 |

## Apéndice B — Acciones de `/action`

| `kind` | Campos | Quién | Observaciones |
|---|---|---|---|
| `trivia_attack` | `power`, `target` | Elegible | `pass` para no atacar |
| `trivia_tap` | — | Congelado | 5 toques |
| `trivia_punish` | `wire`, `vote` | Perdedor o salvo | `vote=true` para salvos |
| `trivia_sort` | `choices` | Jugador | 6 posiciones |
| `trivia_assault` | `selected` | Jugador | Selección múltiple |
| `wager_buy` | — | Jugador | +200 al pozo |
| `wager_lock` | `index` | Jugador | También vía `/answer` |
| `wager_side_bet` | `target`, `amount` | Jugador | 200 contra stage1 |
| `charada_director` | `index` | Director | 0–2 |
| `charada_buzz` | — | No actor | Timbre 7 s |
| `charada_buzz_result` | `text` | Actor | `correct`/`fail` |
| `charada_help` | `text` | Actor | `genre`/`year` |
| `impostor_coartada` | `index` | Jugador | 0–9 |
| `impostor_advance` | — | Asker | Siguiente pregunta |
| `impostor_vote` | `target` | Jugador | No espectador |
| `impostor_guess` | `text` | Impostor | 8 opciones |
| `draw_pick_prompt` | `index` | Artista | 0/1 |
| `draw_partial` | `strokes` | Artista | Cada ~2.5 s |
| `draw_submit` | `strokes` | Artista | Cierra dibujo |
| `draw_title` | `text` | No artista | Rechaza título cercano |
| `draw_vote` | `target` | No artista/público | Urna separada |
| `draw_early_guess` | `text` | No artista | 2 intentos |
| `draw_critic` | `target`, `text` | Jugador | `bomb`/`crown` |
| `auction_bid` | `bid` | Jugador | Múltiplos de 50 |
| `auction_ghost` | — | Jugador | Una vez |
| `fibbage_lie` | `text`, `amount` | Jugador | Amount 200 para veneno |
| `fibbage_scan` | — | Jugador | 1 vez |
| `fibbage_hunter` | `text`, `target` | Jugador | **Sin UI** |
| `fibbage_vote` | `target`, `vote` | Jugador/público | `vote=true` certeza |

## Apéndice C — Snapshot: campos y filtros

- `code`, `displayCode`, `mode`, `game`, `rulesVersion`, `pack`.
- `phase`, `round`, `rounds`, `rev`, `serverNow`, `phaseEndsAt`, `phaseDuration`.
- `hostId`, `players`, `playersPace`, `answeredCount`, `readyCount`, `allReady`, `audience`.
- `question`, `answer` (solo reveal).
- `trivia` (v2): ataque, `you`, sort, assault, punishment.
- `wager`: pot, stage, poster, clues, `you.privateClue`, side bets.
- `charada`: actor, title filtrado, director, saboteur, timbre, ayudas.
- `impostor`: category/word/hint filtrados, role, speaker, votes, guess.
- `drawing`: prompt filtrado, strokes, options, ink, flashlight, early guess.
- `auction`: item, effect, secret informante, bids, winner, applied.
- `fibbage`: prompt, answer filtrada, options, scan, poison, certainty, final flag.
- `you`: id, score, streak, answered, selected, points, correct, ready, actor, wager, spectator, coins, role, submitted, voted, bid, artist.
- `podium`: players + highlights + auctionCashout.

## Apéndice D — Archivos fuente

**Servidor**

- `server/api/games.py` — motor, estados, puntajes, snapshots.
- `server/api/app.py` — rutas, rate limits, persistencia, salud.
- `server/api/game_decks.py` — mazos locales de fiesta.
- `server/sync/trivia_bank.py` — banco de preguntas.
- `server/tests/test_games.py`, `test_games_api.py`, `test_game_modes.py`, `test_games_v2.py`, `test_trivia_bank.py`.

**Android**

- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameModels.kt`
- `GameClient.kt`, `GameStore.kt`, `GameCatalog.kt`, `GameScore.kt`, `GameCodes.kt`, `GameDeepLink.kt`
- `ui/games/GameRoomScreen.kt`, `GameV2Screens.kt`, `GameModesScreen.kt`
- `ui/games/TvGamesScreen.kt`, `TvGameModesScreen.kt`, `GameTransitionsV2.kt`
- `ui/games/GameNarratorView.kt`, `GameQr.kt`, `GameArt.kt`, `SalonArt.kt`
- `data/games/GameNarrator.kt`, `NarratorAudio.kt`
- Tests: `GameV2ModelsTest.kt`, `GameWiringTest.kt`, `GameCatalogTest.kt`, `GameClientTest.kt`, `GameNarratorTest.kt`, `GameScoreTest.kt`, `GameStoreTest.kt`, `GameQrTest.kt`, `GamePhase3ModelsTest.kt`, `GamePhase4ModelsTest.kt`, `GeePickDialogDuplicateTest.kt`.

**Assets**

- `app/src/main/res/drawable-nodpi/game_*`
- `app/src/main/res/drawable-nodpi/narrator_*`
- `app/src/main/res/raw/narrator_*.m4a`
- `app/src/main/res/raw/game_sfx_*.wav`
- `app/src/main/res/raw/game_ambient.mp3`
- `docs/assets/games/art/tutorial_contact_sheet.png`
- `docs/assets/games/art/avatar_contact_sheet.png`
- `/media/chronos/Disco Duro/vision-assets/games/voice` (maestros TTS)

## Apéndice E — Evidencia de los hallazgos

Estas pruebas se ejecutaron contra el árbol actual, no contra una versión antigua:

1. **Espectador Impostor:** sala `HMP234`, 3 jugadores + 1 espectador; tras `secret`, el snapshot del espectador devolvió `impostor.word = "Metro"` y `role = "crew"`.
2. **Póster Apuesta:** sala `WGR234`, `phase=clue`, `privateStage=1`; `wager.poster` y `question.art` contenían la URL real.
3. **Charadas doble pago:** sala `CHX235`, timbre correcto; actor 1,000 después del timbre, 2,300 después de `_begin_reveal`; sin timbre, 1,300.
4. **Early guess:** la lista de 50 prompts contiene `que` en 13, `una` en 22 y `con` en 9; `que`/`una`/`con` cumplen el `guess in prompt`.
5. **Tinta:** sala `DBB237`, ronda 2, límite 100; se aceptó `draw_submit` con `ink_used=11979.2`.
6. **v1:** sala v1 de Impostor con 6 jugadores asignó cómplice y detective; sala v1 de Dibujo ronda 2 tuvo `ink_limit=100`; sala v1 de Subasta aceptó `auction_ghost`.
7. **Puja bajo saldo:** sala `AUC236`, 25 monedas, puja 25; el servidor guardó 50 y ganó el lote.
8. **Rotación:** `/v1/game/health` devolvió `questions=3125`, `served=0`.

## Apéndice F — Cómo reproducir la auditoría

```bash
# Servidor
cd server
pytest -q

# Android
./gradlew :app:testNormalDebugUnitTest --no-daemon --console=plain

# Launcher
./gradlew :launcher:testReleaseUnitTest --no-daemon --console=plain

# Build de la app
./gradlew :app:assembleNormalRelease --no-daemon --console=plain

# Inyectar motor X4K, alinear y firmar
python3 tools/inject_engine_dex.py \
  app/build/outputs/apk/normal/release/app-normal-release.apk \
  app/build/outputs/apk/normal/release/app-normal-release-engine.apk \
  app/src/debug/assets
$ANDROID_HOME/build-tools/36.0.0/zipalign -p -f 4 \
  app/build/outputs/apk/normal/release/app-normal-release-engine.apk \
  app/build/outputs/apk/normal/release/app-normal-release-engine-aligned.apk
$ANDROID_HOME/build-tools/36.0.0/apksigner sign \
  --ks /ruta/visionplus-historic.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:... --key-pass pass:... \
  --out app/build/outputs/apk/normal/release/app-normal-release-engine-signed.apk \
  app/build/outputs/apk/normal/release/app-normal-release-engine-aligned.apk
```

No incluir contraseñas ni API keys en documentos compartidos.

---

**Nota final:** este documento es un análisis, no una modificación del código. Los hallazgos P0 deben resolverse antes de publicar una OTA abierta o de jugar una partida competitiva con personas que no estén informadas de las reglas sociales.



