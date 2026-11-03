# Handoff — Vision+ Play Fase 4

## Documento para análisis por un modelo frontera

**Estado:** implementado, desplegado en el VPS y validado con smoke remoto. **OTA estable v2.5.197 publicada; launcher 1.3.13 sin juegos.**
**Fecha:** 2026-10-30.
**Objetivo de este documento:** dar a otra IA (o a una persona) todo el contexto
necesario para auditar mecánicas, assets, diálogos, balance, UX y deuda técnica,
y proponer la siguiente iteración sin tener que leer todo el repositorio.

---

## 1. Resumen ejecutivo

Vision+ Play pasó de 3 juegos a **7**:

1. Trivia Vision+ (existente)
2. Charadas (existente)
3. Apuesta a la pista (existente)
4. **El Impostor** (nuevo)
5. **Duelo de Dibujo Ciego** (nuevo)
6. **Subasta Ciega** (nuevo)
7. **Falso Conocimiento / Fibbage** (nuevo)

Los cuatro nuevos siguen el principio asimétrico de Jackbox/Spyfall:

- **La tele** muestra el estado público, el reloj, el marcador, el dibujo, el
  martillo y las revelaciones. No recibe secretos antes de tiempo.
- **El teléfono** guarda el rol de impostor, la palabra secreta, el lienzo, las
  mentiras, las pujas y los votos.
- **El servidor** es el árbitro: fases, timers, puntajes, filtrado de secretos y
  validación de acciones.

También se añadieron:

- **Público/espectador** (`spectator=true`): puede entrar con la sala llena y
  votar en Dibujo y Fibbage sin competir por puntos.
- **Momentos destacados** al final: medallas automáticas.
- **Háptica** en el teléfono.
- **SFX** en la tele.
- **48 voces nuevas** de Qwen3-TTS y **4 portadas nuevas** de ComfyUI.

---

## 1.1 Actualización post-feedback (2026-10-30)

Ya aplicado en el árbol local:

- **Letras de opciones en la tele**: `TvModeOption` recibe índice real en
  Impostor, Dibujo y Fibbage; se pintan A, B, C, D…
- **Reloj exacto**: el snapshot incluye `phaseDuration` y las barras de teléfono
  y tele usan la duración real de cada fase (45 s, 22 s, 15 s, etc.).
- **Lienzo táctil**: el `verticalScroll` del teléfono se desactiva mientras el
  artista está en fase `draw`, para que el trazo no compita con el scroll.
- **Idempotencia**: `/action` ignora repeticiones idénticas dentro de 1.5 s
  (misma fase, ronda, jugador, tipo y contenido) para evitar dobles votos,
  dobles pujas o doble envío por reintentos de red.
- **Pruebas**: 14 tests de juegos de fiesta y compilación Android en verde.
- **Impostor**: cadena de preguntas dirigidas con tiempo escalado y botón
  «Siguiente pregunta»; empate en la cima → `guess`; 8 opciones de escape;
  puntajes +800 al impostor atrapado que acierta y +300 extra al crew si falla.
- **Decoys de casa**: Dibujo y Fibbage rellenan hasta 5 opciones en salas
  pequeñas o con timeouts.
- **Subasta**: pujas en múltiplos de 50, `double_next`, `shield`, robo del 25 %
  con tope, liquidación de bóveda a 0.5 pts y reveal con «Continuar».
- **Dibujo/Fibbage**: artista +600 por voto real, −500 si nadie acierta en salas
  de 4+, bono +300 por mentira con 3+ votos y ronda final de Fibbage a 2x.
- **Dibujo en vivo**: `draw_partial` cada ~2.5 s, preview del 75 % en tele,
  elección de 1 de 2 prompts y rechazo de títulos con similitud ≥ 0.82.
- **Público**: urnas separadas; el favorito del público da +300 al autor/artista.
- **Audio**: 21 líneas nuevas + 2 reemplazos, prioridad de reveal y ducking de
  SFX mientras habla la narradora.
- **Hotfix 2.5.197**: motor X4K inyectado en `classes3/4/5.dex` (la 2.5.196 se
  retiró por `ENGINE_LOAD_FAIL`); hero recortado con `clipToBounds`, arte
  monocromo y cortina superior/inferior; juegos solo en la app y sala solo en TV.

Siguiente prioridad: kit de 15 assets, más variantes de diálogo y prueba física.

---

## 2. Catálogo actual

| id | Juego | Min–Max | Rondas | Duración aprox. | Acento |
|---|---|---:|---:|---:|---|
| `trivia` | Trivia Vision+ | 1–8 | 8 | 9 min | `#2D7DF6` |
| `charadas` | Charadas | 2–8 | 6 | 10 min | `#FF8A65` |
| `wager` | Apuesta a la pista | 1–8 | 6 | 11 min | `#B388FF` |
| `impostor` | El Impostor | 3–8 | 3 | 10 min | `#FF5C7A` |
| `dibujo` | Duelo de Dibujo Ciego | 3–8 | 3 | 12 min | `#4DD0E1` |
| `subasta` | Subasta Ciega | 2–8 | 4 | 9 min | `#F5C518` |
| `fibbage` | Falso Conocimiento | 2–8 | 4 | 10 min | `#A5D6A7` |

El catálogo vive duplicado a propósito en:

- `server/api/games.py` → `catalog()`
- `app/.../data/games/GameCatalog.kt`

El servidor decide qué juegos acepta al crear la sala. La app solo pinta la carta.

---

## 3. Mecánicas exactas

### 3.1 El Impostor

**Idea:** todos comparten un lugar secreto; uno no lo sabe y debe fingir.

#### Fases y tiempos

```
lobby
  → countdown (3 s)
  → secret (11 s)
  → questions (25 s por pareja en 3–4 jugadores; 20 s en 5–6; 15 s en 7–8)
  → vote (22 s)
  → guess (20 s, solo si atraparon al impostor)
  → reveal (5 s)
  → siguiente ronda
  → podium
```

- El impostor se elige al azar con semilla estable por sala y ronda.
- El orden rota por ronda en una **cadena dirigida**: A pregunta a B, B pregunta
  a C, etc. Cada turno tiene un `askerId`, un `targetId` y una pregunta
  sugerida.
- El hablante puede pulsar **«Siguiente pregunta»** en su teléfono para avanzar
  sin esperar el reloj; el servidor también avanza solo al vencer el turno.
- No hay chat: las preguntas se dicen en voz alta.
- La tele muestra el turno, quién interroga a quién, la pregunta y el estado de
  votos, pero **no la palabra**.
- El teléfono del crew muestra palabra, categoría y pista.
- El teléfono del impostor solo muestra «Eres el impostor» y una categoría
  clasificada.

#### Votación y escape

- Cada jugador vota a otro; no puede votarse a sí mismo.
- Si el impostor está entre los más votados, aunque sea empate → `guess`.
- Si el más votado es un crew único → el impostor escapa.
- En `guess`, el impostor elige una de **8 opciones** (la correcta + 7 señuelos).
- Si acierta, roba la ronda; si falla, gana el equipo.

#### Puntaje

| Situación | Puntos |
|---|---:|
| Crew que votó al impostor y lo atraparon | +1000, +300 extra si el impostor falla el guess |
| Impostor atrapado pero adivina el lugar | +800 |
| Impostor no atrapado | +1500 |
| Votar a otra persona / no votar | 0 |

#### Notas de diseño

- La categoría y la pista se ocultan a la tele y al impostor hasta el reveal.
- La palabra nunca aparece en la tele antes del reveal.
- El «último intento» es una elección múltiple de 8 opciones, no texto libre.
- El mazo actual son lugares genéricos de cine, no franquicias concretas.

---

### 3.2 Duelo de Dibujo Ciego

**Idea:** un artista dibuja una frase absurda en su teléfono; los demás inventan
títulos falsos y votan el título real.

#### Fases y tiempos

```
lobby
  → countdown (3 s)
  → draw (45 s)
  → title (30 s)
  → vote (22 s)
  → reveal (5 s)
  → siguiente ronda
  → podium
```

- El artista rota por ronda entre todos los jugadores conectados.
- El artista elige **1 de 2 prompts** antes de tocar el lienzo; si no elige, se
  queda el primero.
- El dibujo se envía como **vectores normalizados 0..1000**, no como imagen.
- Durante `draw`, el teléfono manda el arreglo completo de trazos (`draw_partial`)
  cada ~2.5 s. La tele muestra solo el **75 %** como vista previa; al pasar a
  `title` revela el 100 %.
- Los demás escriben un título falso.
- El servidor arma las opciones: título real + mentiras, barajadas.
- El artista no vota.
- El público vota en una urna separada. Si su opción más votada no empata, paga
  +300 al autor de la mentira o al artista si eligió la verdad.

#### Puntaje

| Situación | Puntos |
|---|---:|
| Votar el título real | +1000 |
| Autor de una mentira, por cada voto recibido | +500 |
| Autor de una mentira con 3+ votos | +300 extra |
| Artista, por cada voto al título real | +600 |
| Artista si nadie vota el título real (salas de 4+) | −500 |
| Votar una mentira | 0 para quien vota |

#### Lienzo

- Colores: blanco, rojo, amarillo, cian, verde, violeta.
- Grosores: fino, medio, grueso.
- Herramientas: deshacer y borrar (locales; no se sincronizan al servidor).
- Límites: 160 trazos y 2,400 puntos por dibujo.
- Formato: `[{color:"#RRGGBB", width:5, points:[[x,y],...]}]`.
- El render usa Canvas vectorial en teléfono y tele.

#### Notas de diseño

- El prompt está oculto para los no-artistas, pero el título real aparece como
  opción en la fase de voto; el juego consiste en identificarlo.
- Si hay menos de 5 opciones totales, el servidor rellena con **decoys de casa**
  del mazo. Esto evita el 50/50 de las salas de 3 o los timeouts.
- Si dos jugadores escriben la misma mentira, el servidor deduplica y solo uno
  conserva la autoría (el primero que llegó).
- Un título falso con similitud ≥ 0.82 o contenido dentro del real se rechaza
  con 400 y el teléfono puede pedir otro.
- No hay penalización por no enviar título; simplemente no aparece su mentira.
- El artista ya no puede sabotear impunemente: en salas de 4+ pierde 500 si
  nadie vota la verdad.

---

### 3.3 Subasta Ciega

**Idea:** cartas de botín con valor visible o misterioso; pujas secretas; martillo.

#### Fases y tiempos

```
lobby
  → countdown (3 s)
  → auction (15 s)
  → auction_reveal (9 s)
  → siguiente lote
  → podium
```

- Cada jugador empieza con **1000 monedas**.
- La carta puede mostrar su efecto o ser `mystery`.
- Todos pujan en secreto; el servidor recorta la puja a `0..coins` y la redondea
  a múltiplos de **50** (0, 50, 100, 250, 500…).
- Se puede pujar 0.
- Puede haber una sola puja por jugador y lote.

#### Resolución

1. Se ordenan las pujas de mayor a menor.
2. Si hay empate en la cima, **todos los empatados se cancelan**.
3. Gana la siguiente puja más alta, si existe.
4. Si todos empatan o nadie puja, no hay ganador.
5. El ganador paga su puja y recibe el efecto.

#### Efectos implementados

| Efecto | Resultado |
|---|---|
| `points` | Suma puntos al marcador |
| `coins` | Suma monedas para futuras pujas |
| `steal` | Roba el 25 % de los puntos del líder, con mínimo 250 y máximo 600 |
| `double_next` | Duplica los puntos de tu próximo lote de puntos |
| `shield` | Inmunidad permanente a robos |
| `mystery` | Elige al azar entre points/coins/steal/double_next/shield |

Al cerrar el último lote, cada moneda sobrante vale **0.5 puntos**
(`liquidación de bóveda`). Eso evita que guardar 1000 monedas sin pujar sea una
estrategia gratis.

**No implementado todavía:** impuesto, préstamo, segunda vuelta, mercado entre
rondas y subasta de jugadores.

#### Notas de diseño

- El efecto `steal` castiga al líder actual, pero el `shield` y el tope 600
  evitan que un solo lote destruya la partida.
- La fase de reveal dura 12 s y acepta «Continuar» de los teléfonos; el host
  puede avanzar cuando todos están listos.
- La tele muestra todas las pujas solo después del martillo.

---

### 3.4 Falso Conocimiento / Fibbage

**Idea:** un dato insólito de cine/series; todos inventan una mentira y votan la
que creen verdadera.

#### Fases y tiempos

```
lobby
  → countdown (3 s)
  → write (30 s)
  → vote (22 s)
  → reveal (5 s)
  → siguiente ronda
  → podium
```

- Todos los jugadores escriben una mentira.
- El servidor rechaza una mentira que coincida con la respuesta real.
- Las mentiras duplicadas se deduplican.
- En voto, no puedes votar tu propia mentira.
- La respuesta real se mezcla con las mentiras y se baraja.
- El público vota en una urna separada; su opción más votada da +300 al autor
  de la mentira (sin empate en la cima).

#### Puntaje

| Situación | Puntos |
|---|---:|
| Votar la respuesta real | +1000 (x2 en la ronda final) |
| Autor de una mentira, por cada voto recibido | +500 (x2 en la ronda final) |
| Autor de una mentira con 3+ votos | +300 extra |
| Votar una mentira | 0 para quien vota |

#### Notas de diseño

- Bono +300 por engañar a 3+ personas; la ronda final vale 2x.
- Las salas pequeñas o con timeouts se rellenan con decoys de casa hasta 5
  opciones.
- No hay penalización por votar una mentira obvia.
- No hay ronda de desempate ni «última pregunta» con doble puntaje.
- El dato se revela con una explicación breve (hint), pero no con fuente.
- Algunos datos son más conocidos que insólitos; falta verificación externa.

---

### 3.5 Público / audiencia

- `POST /join` acepta `spectator: true`.
- Un espectador puede entrar con la sala llena y aunque la partida ya empezó.
- No ocupa cupo de jugador, no es anfitrión y no aparece en el marcador.
- Puede votar en Dibujo y Fibbage.
- En Dibujo su voto sí entra en el conteo que determina el bono del artista.
- En Fibbage su voto se ve, pero el autor de la mentira no recibe puntos por ese
  voto (el cálculo de puntos solo recorre jugadores).
- No puede votar en El Impostor.
- No tiene leaderboard ni puntos propios.

### 3.6 Momentos destacados

El servidor calcula medallas al cerrar:

- **El más mentiroso**: más votos recibidos en mentiras (Dibujo/Fibbage).
- **El detective**: más aciertos de la verdad (Dibujo/Fibbage/Impostor).
- **El tiburón de la subasta**: más lotes ganados.
- **El camaleón**: más veces que el impostor escapó o adivinó.
- **El más despistado**: menor puntaje.

Regla actual: cada jugador puede recibir **como máximo una medalla** (se usa un
`used` set). Por eso «El más despistado» puede desaparecer si su jugador ya ganó
otra medalla. Las medallas se muestran en el podio de la tele (máximo 2) y en el
podio del teléfono (todas).

---

## 4. Contrato HTTP y estado

### Endpoints

Todos requieren `X-Api-Key`.

| Método | Ruta | Notas |
|---|---|---|
| POST | `/v1/game/rooms` | `game` acepta los 7 ids |
| POST | `/v1/game/rooms/{code}/join` | `spectator` opcional |
| POST | `/v1/game/rooms/{code}/rejoin` | token de jugador |
| GET | `/v1/game/rooms/{code}/state` | rol `player`/`display`; 204 si no cambió |
| POST | `/v1/game/rooms/{code}/action` | puerta de los juegos de fiesta |
| POST | `/v1/game/rooms/{code}/ready` | reveal de los juegos con ritmo de jugadores |
| POST | `/v1/game/rooms/{code}/host/{action}` | start/next/close/kick |
| POST | `/v1/game/rooms/{code}/reaction` | reacciones |
| POST | `/v1/game/rooms/{code}/report` | reporte de trivia |
| GET | `/v1/game/catalog` | carta de juegos |
| GET | `/v1/game/packs` | packs de trivia |
| GET | `/v1/game/health` | salud |

### Acciones de `/action`

| `kind` | Campos | Quién |
|---|---|---|
| `impostor_advance` | — | asker del turno actual |
| `impostor_vote` | `target` | jugador, no espectador |
| `impostor_guess` | `text` | solo el impostor |
| `draw_pick_prompt` | `index` 0/1 | solo el artista, antes de dibujar |
| `draw_partial` | `strokes` | solo el artista; no consume el rate-limit de votos |
| `draw_submit` | `strokes` | solo el artista |
| `draw_title` | `text` | jugadores, no artista ni público |
| `draw_vote` | `target` | jugadores no artistas y público |
| `auction_bid` | `bid` | jugadores, no público |
| `fibbage_lie` | `text` | jugadores, no público |
| `fibbage_vote` | `target` | jugadores y público |

### Límites y anti-trampa

- Cuerpo máximo: **64 KB** (por los trazos).
- 160 trazos y 2,400 puntos por dibujo.
- 30 acciones/minuto por jugador.
- La respuesta correcta y los roles se filtran por `role` y `player_id`.
- El puntaje se calcula en el servidor.
- Las salas viven en memoria; si la API se reinicia, la sala muere.
- El estado no persiste entre procesos; el resultado se guarda en `game_results`
  al cerrar.

### Campos nuevos del snapshot

- `impostor`: categoría, pista, palabra filtrada, rol, hablante, pregunta, votos,
  impostor, `caught`, `guess`, `guessOptions`, `guessCorrect`, `points`.
- `phaseDuration`: duración nominal de la fase en segundos; el cliente la usa
  como denominador exacto del reloj.
- `drawing`: artista, prompt filtrado, trazos, opciones, conteos, `youSubmitted`,
  `youVoted`, `yourVote`, `audienceVotes`.
- `auction`: item, misterio, efecto, valor, pujas, ganador, precio, cancelados,
  empate, efecto aplicado.
- `fibbage`: prompt, pista, categoría, respuesta filtrada, opciones, conteos,
  `yourLie`, `yourVote`, `audienceVotes`.
- `audience`: número de espectadores conectados.
- `highlights`: medallas del podio.
- `you`: `spectator`, `coins`, `role`, `submitted`, `voted`, `bid`, `artist`.

---

## 5. UX por dispositivo

### Teléfono

Archivo: `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameModesScreen.kt`

- **Iniciar sala es exclusivo de la tele.** El teléfono solo se une por QR o
  código; no tiene botón «Crear sala».

- `ImpostorPhoneContent`: tarjeta de rol, palabra, pregunta sugerida, turno,
  lista de votos, opciones de adivinanza, reveal y acciones de continuar.
- `DibujoPhoneContent`: prompt, lienzo táctil, paleta, grosores, deshacer, borrar,
  envío, campo de título falso, voto, reveal y marcador.
- `SubastaPhoneContent`: carta, pista de valor, monedas, chips de puja, +100,
  «Todo», envío, reveal y lista de pujas.
- `FibbagePhoneContent`: dato, pista, campo de mentira, voto, reveal y marcador.
- `VoteButtons`: componente compartido para Dibujo/Fibbage; muestra autor y votos
  solo en reveal; no deja votar tu propia mentira.
- `GameRevealActions`: Continuar + Siguiente (host) en los juegos con reveal.
- `GameScoreboard`: marcador y público.
- `DrawingCanvas`: gestos de arrastre, coordenadas normalizadas.
- `VectorCanvas`: render de trazos del servidor.
- `CountdownBuzz`: vibración a 5, 3 y 1 segundos.
- Vibración al recibir rol secreto, al tocar turno, al dibujar, al votar y al
  revelar.

### Tele

Archivo: `app/src/main/java/com/chronosbvrx/visionplus/ui/games/TvGameModesScreen.kt`

- Los juegos viven **solo en la app principal**; Vision+ Home 1.3.13 ya no
  muestra la pestaña Juegos. La app 2.5.196 abre su selector con las 7 tarjetas
  en rejilla 4×2 y crea la sala en modo TV.
- `TvImpostor`: panel público, turno, pregunta, votos, opciones de escape,
  revelación y marcador.
- `TvDibujo`: lienzo vectorial grande, título real, opciones y votos.
- `TvSubasta`: carta con gradiente, valor/misterio, martillo, ganador, pujas y
  efecto aplicado.
- `TvFibbage`: dato grande, pista, opciones y autores/votos en reveal.
- `TvScoreStrip`: marcador + público.
- `TvModePanel`, `TvModeHeader`, `TvModeOption`: componentes visuales.
- El selector de juegos pasó a una **rejilla 4×2** con las 7 tarjetas visibles a
  la vez en la tele, sin scroll horizontal.
- SFX: `GameSfx` reproduce `game_sfx_*.wav`; se dispara al cambiar de fase y
  hace tic en los últimos 5 segundos.
- Voz: `NarratorAudio` + `GameNarrator`; la tele es la única que reproduce.

---

## 6. Inventario de assets

### 6.1 Portadas

7 portadas 16:9, generadas con ComfyUI/SDXL local.

| Juego | Archivo | Tamaño |
|---|---|---:|
| Trivia | `game_trivia_art.webp` | 18 KB |
| Charadas | `game_charadas_art.webp` | 23 KB |
| Apuesta | `game_wager_art.webp` | 36 KB |
| Impostor | `game_impostor_art.webp` | 13 KB |
| Dibujo | `game_dibujo_art.webp` | 40 KB |
| Subasta | `game_subasta_art.webp` | 35 KB |
| Fibbage | `game_fibbage_art.webp` | 35 KB |

- Generación: `tools/comfy_game_art.py`.
- Modelo: Juggernaut XL v9 (`juggernautXL_v9Rundiffusion.safetensors`).
- 1024×576, 32 pasos, CFG 7, `dpmpp_2m` + `karras`.
- Semillas: 778000 + índice × 41.
- Maestros: `docs/assets/games/art/*.png` (7.4 MB).
- Copia maestra en el disco grande:
  `/media/chronos/Disco Duro/vision-assets/games/art`.
- Contact sheet: `docs/assets/games/assets_contact_sheet.png`.

**Dirección visual:** cine noir, neón azul/cian, rojo impostor, oro subasta,
verde archivo para Fibbage. Sin texto ni logos dentro del arte.

**Lo que falta:** arte de cartas de subasta (24), reversos, role cards, medallas,
iconos de herramientas de dibujo, transiciones, fondos de lobby, arte 1:1 para
teléfono, lower thirds y animaciones de “martillo”.

### 6.2 SFX

6 WAV sintetizados localmente:

| Archivo | Uso | Tamaño |
|---|---|---:|
| `game_sfx_tick.wav` | últimos segundos | 7.8 KB |
| `game_sfx_vote.wav` | empieza votación | 16 KB |
| `game_sfx_secret.wav` | rol secreto / suspense | 48 KB |
| `game_sfx_reveal.wav` | reveal | 42 KB |
| `game_sfx_hammer.wav` | martillo de subasta | 37 KB |
| `game_sfx_whoosh.wav` | transición | 39 KB |

- Generación: `tools/make_game_sfx.py`.
- 44.1 kHz, mono, 16-bit WAV.
- Síntesis con numpy: senos, sierras, ruido y envelopes.
- Reproducción: `GameSfx` en la tele; volumen 0.7; se libera al terminar.
- No hay mezcla con la voz ni control de volumen propio; el interruptor de
  narradora también apaga los SFX.

### 6.3 Voces

- Motor: **Qwen3-TTS-12Hz-1.7B-Base** (Apache 2.0), clonando `dalia.wav`.
- 6 tomas por línea; se elige la mejor por rango tonal, energía, ritmo, WER y
  duración.
- AAC-LC mono 44.1 kHz, 48 kbps, normalizado a −16 LUFS.
- 48 audios nuevos: 16 momentos × 3 variantes.
- Peso total de los 48: ~1.2 MB.
- 200 audios clásicos existentes: 20 momentos × 10 variantes.
- Maestros/copia: `/media/chronos/Disco Duro/vision-assets/games/voice` (248
  archivos, ~10 MB con arte/SFX).
- Reporte: `/media/chronos/Disco Duro/vision-assets/games/narrator_report.json`.
- `MANIFEST.sha256` con 242 archivos.

**Nota:** el generador ahora compila los `.part.m4a` en el disco grande y solo
mueve el archivo final a `res/raw`; antes un `.part.m4a` dentro de `res/raw`
rompía el build de Android.

---

## 7. Diálogos nuevos (48 líneas)

### `impostor_secret`

0. Teléfonos arriba. Uno de ustedes no sabe dónde está.
1. Reparto secreto. El impostor ya está temblando.
2. Miren su pantalla y disimulen. Aquí empieza la mentira.

### `impostor_vote`

0. ¿Quién miente? Señalen sin piedad.
1. Hora de votar. Las caras no mienten… o sí.
2. Voten al impostor. Si le atinan, se pone bueno.

### `impostor_caught`

0. ¡Lo atraparon! Pero todavía puede robar la ronda.
1. Descubierto. Ahora tiene una última oportunidad.
2. ¡Cayó el impostor! A ver si adivina el lugar.

### `draw_start`

0. A dibujar. Sin letras, sin trampas y sin pena.
1. El lienzo está listo. Que empiece la obra maestra.
2. Dibuja rápido, que la tele ya está mirando.

### `draw_vote`

0. ¿Cuál es el título real? Voten, mentirosos.
1. Hora de votar. Busquen el título real entre tanta mentira.
2. Elijan el título verdadero. Sin trampas.

### `auction_start`

0. Se abre la subasta. Saquen las carteras y los faroles.
1. Lote en vitrina. Pujen por debajo del agua, sin que los vean.
2. El mercado negro abre. Que gane el más valiente.

### `auction_reveal`

0. ¡Martillo! Veamos quién se quedó con el botín.
1. Se cierra la puja. Los empates se cancelan.
2. El martillo habló. A pagar o a llorar.

### `fibbage_write`

0. Inventen una mentira que suene a verdad. Aquí nadie sabe nada.
1. Si no sabes, invéntalo con cara de experto.
2. La verdad está escondida. Ustedes inventen el resto.

### `fibbage_vote`

0. Voten la que crean verdadera. Alguien va a morder.
1. Hora de votar. La mentira más creíble se lleva los puntos.
2. Señalen la verdad. Si caen, se ríen y suman.

### Segunda ola (21 líneas)

**`impostor_questions`**
0. Empieza el interrogatorio. Hagan sus preguntas en voz alta y fíjense quién suda.
1. A interrogar. Respondan rápido… el que duda, se delata solo.
2. Micrófono abierto en la sala. Cuidado con dar demasiadas pistas al impostor.

**`impostor_escaped`**
0. ¡Se les escapó en la cara! El impostor se lleva el botín.
1. Les vieron la cara a todos. Victoria limpia para el impostor.
2. Ni Sherlock los salvaba hoy. El impostor salió caminando por la puerta grande.

**`party_timeout`**
0. ¡Tiempo! El que no mandó nada, se quedó mirando.
1. Reloj en cero. A jugar con lo que alcanzaron a mandar.
2. Se acabó el tiempo. Más rápidos los dedos para la próxima.

**`auction_tie`**
0. ¡Choque de ambición! Empate en la cima, esas pujas quedan anuladas.
1. Pensaron igualito y se quedaron sin nada. Las pujas empatadas se cancelan.
2. Doble codicia, cero premio. El empate arriba le regala el lote al que sigue.

**`lie_sweep`**
0. ¡Cayeron redonditos! Esa mentira se acaba de llevar a media sala.
1. Maestro del engaño en la sala. Todos mordieron el mismo anzuelo.
2. Qué manera de vender humo… y qué manera de comprarlo todos juntos.

**`nobody_guessed`**
0. Nadie le atinó a la verdad. O el dibujo era arte abstracto, o mienten demasiado bien.
1. Cero aciertos a la respuesta real. La verdad quedó completamente enterrada.
2. Ni uno solo dio en el blanco. Qué desastre más hermoso.

**`final_round_double`**
0. Última ronda y puntos al doble. Aquí se define quién manda y quién lava los platos.
1. Ronda final. Todo vale el doble, así que todavía pueden arruinarle la noche al líder.
2. Última llamada. Con el doble de puntos en juego, nadie tiene la corona segura.

Además se reemplazaron:
- `draw_vote_1`: «Hora de votar. Busquen el título real entre tanta mentira.»
- `auction_start_1`: «Lote en vitrina. Pujen por debajo del agua, sin que los vean.»

### Lo que falta en diálogo

- No hay voz para `questions` (la tele se queda en silencio durante los turnos
  de preguntas del impostor).
- Ya hay líneas para timeout, empate de subasta, nadie adivina, mentira perfecta
  (lie_sweep), última ronda y escape del impostor.
- Faltan líneas para: remontada, podio específico por juego y tutorial.
- Solo 3 variantes por momento nuevo frente a 10 de los clásicos; a la tercera
  partida se nota la repetición.
- No hay personas distintas (narradora, subastador, detective noir). Todo lo dice
  la misma voz.
- No hay textos dinámicos con nombres/números en las líneas de voz; los
  bocadillos dinámicos no siempre tienen audio.
- No hay subtítulos/captions ni configuración de voz aparte.

---

## 8. Contenido de los mazos

Archivo: `server/api/game_decks.py`.

| Mazo | Cantidad | Notas |
|---|---:|---|
| Lugares del impostor | 42 | categoría, pista, 3 preguntas, 8 opciones de escape |
| Consignas de dibujo | 50 | frases absurdas + 3 decoys de casa por carta |
| Cartas de subasta | 26 | points/coins/steal/double_next/shield/mystery |
| Datos de Fibbage | 30 | cine y series, con pista, categoría y 3 decoys de casa |

Es contenido local, sin base de datos, barajado determinista por sala.

**Problemas potenciales:**

- 30 datos de Fibbage se agotan en 7–8 partidas de 4 rondas.
- 42 lugares del impostor se agotan en 14 partidas de 3 rondas.
- 50 consignas de dibujo se agotan en 16 partidas de 3 rondas.
- Hay respuestas de Fibbage fáciles o ambiguas; falta verificación con fuentes.
- No hay niveles de dificultad ni packs temáticos.
- No hay sinergia con el catálogo real de Vision+ (películas/series propias).
- No hay contenido en otros idiomas.

---

## 9. Pruebas y verificación

- **Backend:** 542 tests en verde.
- **Android:** 1,136 tests en verde.
- **Release:** `assembleNormalRelease` compila; APK ~74 MB.
- **Debug:** `assembleNormalDebug` compila; APK ~129 MB.
- **Físico:** estable 2.5.197 instalada en Chromecast HD (`192.168.1.124`):
  - abre `visionplus://open/games`;
  - selector con 7 tarjetas y arte;
  - D-pad y desplazamiento horizontal;
  - foco blanco;
  - crea la sala de El Impostor contra el VPS y muestra QR/código `GNB-KTD`
    con el fondo noir.
- **Smoke remoto:** `/v1/game/catalog` con 7 juegos; salas creadas, inicio,
  `draw_partial` visible en display, `auction_bid` y `fibbage_lie` aceptados.
- Captura: `docs/screenshots/games_fase4_selector.png`.
- **Kit de salón:** 15 WebP (5 cartas de subasta, 2 roles, 5 medallas, 3 fondos)
  integrados en TV/teléfono. Contact sheet: `docs/assets/games/salon_contact_sheet.png`.

**No probado:**

- Partidas reales con 2–4 teléfonos.
- Dibujo táctil en un teléfono real.
- Haptica real.
- SFX y voz en la tele durante una partida.
- Carga de 8–12 jugadores.
- Estrés de red.
- End-to-end HTTP de los nuevos endpoints contra Postgres/VPS.
- Regresión visual de las pantallas nuevas.

---

## 10. Deuda técnica y limitaciones conocidas

### Bloqueantes

1. **CORREGIDO 2026-10-30.** Backend desplegado en el VPS: `game_decks.py`,
   `games.py`, parche quirúrgico de `app.py` y `Dockerfile`; contenedor `api`
   reconstruido. `/v1/game/catalog` devuelve los 7 juegos y el smoke remoto creó,
   inició y ejecutó acciones de Impostor, Dibujo, Subasta y Fibbage.
2. **Pendiente:** prueba física completa con 2–3 teléfonos reales; el selector y
   el lobby de Impostor sí se verificaron en el Chromecast HD.
3. **OTA estable 2.5.197/vc437** publicada en GitHub; launcher 1.3.13/vc36. Se retiró la 2.5.196 por falta del motor X4K.
   Paquetes locales en `/media/chronos/Disco Duro/vision-assets/dist/`.

### Bugs / detalles visuales

4. **CORREGIDO 2026-10-30.** En la tele, las opciones de voto de Dibujo/Fibbage
   y las opciones de escape del impostor se pintaban todas con la letra **A**;
   ahora se pasa el índice real y se pintan A, B, C, D…
5. **CORREGIDO 2026-10-30.** La barra de progreso del teléfono usaba
   `seconds / 30f`; ahora usa `phaseDuration` real (45 s, 22 s, 15 s, etc.).
6. **CORREGIDO 2026-10-30 (parcial).** El dibujo se transmite cada ~2.5 s y la
   tele muestra el 75 % como vista previa. No es WebSocket, sino polling HTTP.
7. **CORREGIDO 2026-10-30 (pendiente prueba táctil).** El `verticalScroll` se
   desactiva mientras el artista está en `draw`, para que el trazo no compita
   con el scroll. Falta validarlo en un teléfono físico.
8. **CORREGIDO 2026-10-30.** Urnas separadas; el favorito del público da +300 al
   autor de la mentira o al artista si elige la verdad.
9. **CORREGIDO 2026-10-30.** Hasta 2 medallas por jugador; «El más despistado» se
   evalúa siempre.
10. **CORREGIDO 2026-10-30.** `questions` tiene audio propio y prioridad de reveal.
11. El selector de la tele muestra 3 tarjetas a la vez; está bien, pero no hay
    indicador de «hay más juegos a la derecha» más allá del corte visual.
12. `TvModeHeader` muestra un número de segundos, pero no una barra de progreso.

### Mecánicas / balance

13. **CORREGIDO 2026-10-30.** Impostor usa cadena dirigida con tiempo escalado
    (25/20/15 s por pareja) y botón para avanzar; el empate en la cima ya manda
    a `guess`.
14. **CORREGIDO 2026-10-30.** El servidor expone `askerId`/`targetId` y la tele
    muestra «A interroga a B».
15. **CORREGIDO 2026-10-30.** El escape tiene 8 opciones; acertar atrapado vale
    +800 y el crew recibe +300 extra si falla.
16. **CORREGIDO 2026-10-30.** Artista +600 por voto real, −500 si nadie acierta
    en salas de 4+, bono por mentira con 3+ votos, trazos parciales, elección de
    prompt y validación de títulos.
17. **CORREGIDO 2026-10-30.** Público separado y «Favorito del público (+300)».
18. **CORREGIDO 2026-10-30.** `double_next`, `shield`, robo del 25 % (250–600),
    pujas en múltiplos de 50 y liquidación de bóveda a 0.5 pts. Faltan impuesto,
    préstamo, segunda vuelta y mercado entre rondas.
19. **CORREGIDO 2026-10-30.** El reveal dura 12 s y acepta «Continuar»; el host
    puede avanzar cuando todos están listos.
20. **CORREGIDO 2026-10-30.** Fibbage tiene bono +300 por 3+ votos y ronda final
    a 2x.
21. **Fibbage:** los datos no están verificados con fuente; algunos son
    demasiado conocidos.
22. **Contenido:** mazos pequeños para sesiones repetidas; falta dificultad,
    packs y sinergia con el catálogo.

### Assets / audio / diálogo

23. **CORREGIDO 2026-10-30.** Se añadieron 15 assets: 5 cartas de subasta, 2
    roles, 5 medallas y 3 fondos de tele. Faltan iconos de herramientas y arte
    1:1 de teléfono.
24. **PARCIAL 2026-10-30.** Hay ducking de SFX bajo la voz; los efectos siguen
    siendo sintéticos y sin capas.
25. La voz nueva tiene 3 variantes por momento; la repetición se nota.
26. Todo lo narra la misma voz; no hay subastador, detective ni presentador
    alternativo.
27. Ya existen líneas de timeout/empate/remontada; faltan tutoriales hablados y más variantes.
28. No hay localización ni subtítulos.

### Técnica

29. Las salas viven en memoria; un reinicio de la API mata la partida (heredado).
30. No hay WebSocket/SSE; el dibujo en vivo requeriría otro canal.
31. El snapshot puede llevar hasta 64 KB de trazos; no hay compresión específica
    ni delta.
32. No hay tests de integración HTTP de los endpoints nuevos.
33. No hay load test de 8–12 jugadores.
34. La rate limit es global por jugador (30/min), no por tipo de acción.
35. No hay idempotency keys; un reintento de red puede enviar dos veces.
36. No hay telemetría específica de los juegos nuevos.

---

## 11. Qué debería evaluar un modelo frontera

### A. Diversión y tensión social

- ¿Cada juego tiene una decisión interesante cada 20–30 segundos?
- ¿El impostor tiene suficiente información para fingir sin ser obvio?
- ¿El dibujo genera risas o frustración? ¿El artista se siente responsable?
- ¿La subasta tiene farol real o solo “pujo lo que puedo”?
- ¿Fibbage castiga el conocimiento o premia la creatividad?
- ¿El público se siente parte o decorado?

### B. Balance y puntaje

- Simular 4, 6 y 8 jugadores.
- Buscar estrategias dominantes:
  - impostor que no habla,
  - artista que dibuja mal para que nadie acierte,
  - subasta que siempre puja 0,
  - Fibbage que copia la respuesta más obvia.
- Evaluar catch-up, kingmaking, runaway leader y recompensa al riesgo.
- Comparar puntajes por ronda: ¿1000/500/800/1500 son los adecuados?
- ¿La medalla única por jugador es justa?

### C. Retención y rejugabilidad

- ¿Cuántas partidas aguantan los 42/50/24/30 mazos?
- ¿Hay suficiente variedad de frases y datos?
- ¿Funciona con 3 jugadores? ¿Y con 8?
- ¿Qué modo falta: equipos, torneo, campaña, revancha?

### D. Assets

- Auditar coherencia visual de las 7 portadas.
- Proponer un kit de arte mínimo por juego: cartas, roles, medallas, iconos,
  fondos, transiciones.
- Definir prompts, semillas, aspect ratios y workflow de ComfyUI.
- Evaluar contraste, legibilidad a 1080p y peso para TV de 1.5–2 GB.
- ¿Qué se puede generar y qué conviene dibujar a mano?

### E. Diálogo

- Auditar las 48 líneas: tono, duración, gracia, repetición, claridad.
- Proponer 8–10 variantes por momento y plantillas dinámicas.
- Separar personas (narradora, subastador, detective) o justificar una sola.
- Escribir líneas para timeout, empate, remontada, última ronda y tutorial.
- Evaluar si el humor mexicano funciona para público general.

### F. Técnica

- ¿Vale la pena WebSocket/SSE para dibujo en vivo?
- ¿Cómo reducir el payload de trazos?
- ¿Cómo hacer el estado idempotente y reanudable?
- ¿Qué pruebas E2E y de carga faltan?
- ¿Cómo medir el rendimiento en Chromecast/TV box?

---

## 12. Backlog sugerido (impacto / esfuerzo)

### P0 — antes de jugar con gente real

1. Desplegar backend y probar una partida completa con 3–4 teléfonos.
2. Corregir letras A en opciones de tele.
3. Corregir la barra de progreso con la duración real de cada fase.
4. Añadir audio para `questions` y para timeout/empate.
5. Verificar que el gesto de dibujo no pelee con el scroll.
6. Añadir una prueba E2E HTTP mínima de `/action` y `/state`.

### P1 — mejoras de diversión

7. Impostor: preguntas dirigidas, desempate de votos, 2 impostores para 7–8,
   y una “misión secreta” opcional.
8. Dibujo: streaming de trazos cada 1–2 s; elegir 1 de 3 prompts; validar
   títulos; bono por engañar a 2+; galería final.
9. Subasta: implementar `double_next`, escudo, impuesto al líder, segunda vuelta
   y mercado entre rondas.
10. Fibbage: datos con fuente, niveles, bono por engañar a varios, ronda final
    de doble puntaje y voto del público con efecto.
11. Público: leaderboard propio, voto favorito y reacciones.
12. Medallas: permitir 2–3 por jugador, animaciones y medallas por juego.

### P2 — producción

13. Kit de assets: cartas base de subasta, role cards, medallas, iconos, fondos,
    transiciones y arte 1:1.
14. 8–10 variantes por momento nuevo y personas de voz alternativas.
15. WebSocket/SSE para dibujo en vivo.
16. Persistencia/reconexión de salas.
17. Tests de carga y telemetría.
18. Localización es-MX / en-US y subtítulos.

---

## 13. Restricciones que debe respetar la siguiente iteración

- **No cambiar la firma** de producción (`9593cb0d…`).
- **No publicar OTA** sin instrucción explícita.
- El servidor sigue siendo la autoridad; los secretos no viajan al rol
  equivocado ni antes de tiempo.
- La tele debe seguir siendo liviana (Chromecast HD, 1.5–2 GB RAM).
- El contenido debe ser local y generado con ComfyUI/TTS locales; no depender de
  servicios externos.
- Español mexicano, humor ligero, sin groserías.
- No usar imágenes con derechos de terceros; el arte generado es local.
- Los juegos deben funcionar con 2–8 jugadores y con público.
- La sala debe sobrevivir a desconexiones de 90 s (hereda la regla actual).

---

## 14. Preguntas concretas para el modelo frontera

1. ¿Qué tres cambios de mecánica darían el mayor aumento de diversión por
   minuto sin añadir complejidad excesiva?
2. ¿Cómo rediseñarías el Impostor para 3, 5 y 8 jugadores sin que la ronda dure
   más de 8 minutos?
3. ¿Qué fórmula de puntaje evita el runaway leader en Subasta y premia el farol?
4. ¿Cómo hacer que el público se sienta parte sin romper el juego?
5. ¿Qué kit de assets mínimo (por juego) genera el mayor salto percibido de
   “web” a “juego de salón”?
6. ¿Qué 20 líneas de diálogo faltan para que la presentadora se sienta viva?
7. ¿Qué partes del dibujo conviene transmitir en vivo y cuáles no?
8. ¿Qué pruebas y métricas demostrarían que los juegos están listos para una
   fiesta real?
