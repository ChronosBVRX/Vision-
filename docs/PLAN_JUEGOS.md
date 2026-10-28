# Plan de implementación — «Vision+ Play» (trivia, charadas y juegos de sala)

**Estado:** listo para ejecutar. **Fecha:** 2026-10-27.
**Ejecuta:** agente/sesión de implementación. **Revisa y publica:** el dueño del proyecto.

> **Antes de este documento, lee `docs/HANDOFF_MAESTRO.md`**: ahí está todo el contexto del
> proyecto (arquitectura, reglas, VPS, trampas), porque este módulo se suma a una app en
> producción.
>
> Este documento es la fuente única de verdad del lote. Si algo de aquí choca con una
> ocurrencia del momento, manda este documento. Si algo choca con `AGENTS.md`, manda
> `AGENTS.md` y se avisa al usuario.

---

## 0. Pedido original

> «Dame una idea de cómo podemos generar un juego de trivia, charadas, y actividades
> grupales para mi app relacionada con las series y películas. Este correrá en el VPS y los
> jugadores podrán unirse desde la app móvil con un código que generará cada sala.»

Decisiones ya tomadas con el usuario (no volver a preguntarlas):

- Se llama **Vision+ Play**; una partida es una **Sala**.
- El **VPS es el árbitro** (autoridad total). El móvil y la TV son clientes.
- El **contenido se genera solo** del catálogo existente. Nadie escribe preguntas a mano.
- **Sin chat de texto**: solo reacciones predefinidas.
- **Solo puntos**: nunca dinero, premios ni sorteos.
- Fase 1 es **móvil primero**. La TV entra en Fase 2.

---

## 1. Punto de partida (baseline) — leer antes de tocar nada

- App publicada **2.5.177 (versionCode 417)**; launcher **1.3.1 (vc24)**; release `v2.5.177`.
- Suites en verde al escribir este plan: **1002 tests Android + 153 launcher**; backend ~**431**.
- **El árbol de trabajo tiene cambios sin commitear** (publicación de la 2.5.177).
  **Prohibido** `git checkout`, `git reset`, `git stash`, `git clean` o cualquier cosa que
  descarte trabajo sin commitear. **No commitear** salvo que el usuario lo pida.
- **No cambiar la firma.** La llave histórica (`9593cb0d…`) es la de producción.
- **No publicar OTA** sin instrucción explícita del usuario. Este plan entrega código probado,
  no un release.

### Zonas prohibidas (regla dura del proyecto)

Sin tests nuevos + prueba física en equipo real, no se toca:

- `data/offline/*`
- `data/xuper/X4KTitan.play / stop / queryStatusUrls / beanPlayUrl`
- `ui/player/X4KPlayerAty.onCreate / play`
- `VisionApp.onCreate`
- Nada del proceso `:x4k`

**Este lote no necesita tocar nada de eso.** Si crees que lo necesitas, es señal de que estás
saliéndote del plan: para y pregunta.

---

## 2. Producto

### 2.1 Roles

| Rol | Quién | Qué hace |
|---|---|---|
| Árbitro | FastAPI en el VPS | Guarda la respuesta correcta, cronometra, puntúa, filtra qué ve cada rol |
| Pantalla | TV con Vision+ (o un teléfono) | Muestra pregunta, arte, reloj y podio. **No responde** |
| Mando | Teléfonos | Botones de respuesta, apuestas y reacciones |

### 2.2 Modos

- **Modo Sala** (`mode = "display"`): hay pantalla compartida; los teléfonos son controles.
- **Modo Remoto** (`mode = "remote"`): sin pantalla; cada teléfono muestra la pregunta.
  Habilita jugar con familia en otra casa.
- **Modo Solo** (`mode = "solo"`): 1–2 jugadores en un teléfono. Es el modo de pruebas.

La pantalla **nunca** es obligatoria. Si no hay TV, la partida se juega igual.

### 2.3 Reglas de convivencia

- Sin chat. 6 reacciones fijas.
- Apodo: 2–14 caracteres, sin caracteres de control, filtrado por lista corta de groserías.
  Por defecto se precarga el nombre del perfil activo (`ProfileStore`) y el color del
  `ProfileAvatar` (hash del nombre, paleta de 8).
- Un jugador puede **reconectar** y conservar asiento y puntos.

---

## 3. Los juegos

### 3.1 Trivia Vision+ (pilar)

Ocho rondas de opción múltiple (4 opciones) por partida. Tipos y regla de generación:

| Tipo | Fuente | Regla de distractores |
|---|---|---|
| `year` | `titles.year` | Años en ±2..±8, distintos, ordenados |
| `genre` | género primario canónico (`genres.py`) | 3 géneros distintos del correcto |
| `cast` | `enrichment.cast_names` (top 5 facturados) | Actores que **no** estén en el reparto de esa ficha |
| `director` | `enrichment.director_names` | Directores que no dirijan esa ficha |
| `synopsis` | `enrichment.overview` es-MX | Títulos del mismo género y banda de popularidad, **sin el título en el texto** |
| `poster` | póster TMDB `w92` servido a pantalla completa | Títulos de popularidad parecida |
| `group` | `title_groups` / `group_members` | 3 títulos que **no** pertenezcan al grupo |
| `seasons` | temporadas agrupadas | Números vecinos, distintos del real |
| `translation` | `enrichment.translated_titles` | Títulos del mismo tipo y banda |

Reglas transversales de generación:

- **Nunca** un distractor que comparta `tmdb_id`, grupo o reparto con la respuesta.
- La sinopsis no puede contener el título (ni plegado, ni compacto). Si lo contiene, se descarta.
- Nada de títulos por debajo de la señal mínima (`popularity > 0` o `votes >= 20`).
- Cada pregunta lleva `hash` estable para no duplicar.

### 3.2 Charadas / «Actúa y Adivina» (Fase 3)

- El teléfono del actor es el mazo. La pantalla compartida solo dice el turno.
- 60 s, equipos, 3 pases por ronda, botones **Correcto / Pasar**.
- **Solo títulos reconocibles** (misma señal mínima, más exigente: `vote_count >= 50`).
- Sin clips de audio ni video: solo texto y póster.

### 3.3 Actividades grupales

1. **Apuesta a la pista** (Fase 3): 3 pistas progresivas (sinopsis → reparto → póster). Se
   apuestan puntos antes de cada pista; acertar temprano paga más, fallar pierde lo apostado.
2. **¿Quién es más…?** (Fase 4): votación sin respuesta correcta, resultado en barras.
3. **Duelo de sagas** (Fase 4): ordenar 3–4 entregas por año.
4. **Bingo de clichés** (Fase 5, opcional): tarjeta 3×3 durante una película en Vision+.

---

## 4. Arquitectura

- **Servidor autoritativo.** El cliente nunca recibe la respuesta correcta antes del `reveal`,
  ni el orden real de las opciones, ni decide el cronómetro.
- **Transporte: sondeo corto.** `GET /state?since=<rev>` devuelve snapshot y `serverNow`;
  si nada cambió responde **204 sin cuerpo**. Intervalo 700 ms en pregunta, 1000 ms en lobby.
  - Motivo: sin sesiones pegajosas, sin configurar WebSocket en Caddy, sobrevive el cambio
    Wi-Fi→4G. Con 12 jugadores son ~17 req/s de payloads de cientos de bytes.
  - **Solo** se migra a WebSocket si se mide jitter > 300 ms en pruebas reales.
- **Estado de sala en memoria** (dict por código) + volcado a Postgres **solo al terminar**.
  Si la API se reinicia, la sala muere: se acepta y la app dice «la sala se cerró».
- **Sin Redis, sin Celery, sin servicios nuevos.** Un archivo nuevo en `api/` y otro en `sync/`.
- **Kill-switch:** `games_enabled` en `remote_config` (patrón de `RemoteConfig.flag`), default
  `true` al publicar. Si se apaga, la pestaña explica que el servicio no está disponible.
- **Aislamiento (escape hatch, no hacerlo ahora):** si hay latencia o se quiere desplegar la
  API sin tumbar partidas, se separa en un contenedor `streaming-game` (`mem_limit 384m`) con
  ruta Caddy `/juego/*`. En Fase 1 va dentro de `api`: el estado es de kilobytes.

### 4.1 Límites duros (obligatorios)

| Límite | Valor |
|---|---|
| Jugadores por sala | 8 (12 máximo configurable) |
| Rondas por partida | 12 (8 trivia + 2 apuestas + 2 charadas según modo) |
| Salas simultáneas | 100 |
| TTL de sala | 4 h |
| Sala inactiva | 30 min → cierre |
| Barrendero | cada 60 s |
| `join` por IP | 10/min |
| `answer` por jugador/ronda | 1 |

### 4.2 Anti-trampa y equidad

- La respuesta correcta **solo** existe en el snapshot de fase `reveal`.
- Puntaje por **hora de recepción del servidor**; jamás por reloj del cliente.
- Bono de velocidad **topado al 25 %** de la base.
- Modo `fair = true` (sin bono de velocidad) disponible por sala para redes malas.
- Token opaco por jugador (32 caracteres, hasheado en BD) para reconectar.
- El host puede cambiar: si se cae, pasa al jugador conectado más antiguo. La sala no se cierra.

---

## 5. Contrato HTTP

Todos los endpoints llevan `X-Api-Key` (como el resto). Además:

- Host: header `X-Game-Host` con `hostToken`.
- Jugador: header `X-Game-Player` con `playerToken`.

Módulo puro nuevo: `server/api/games.py`. Rutas en `server/api/app.py`.
**Ojo:** `server/api/Dockerfile` copia los `.py` con una lista explícita — si no añades
`games.py` a esa línea, el contenedor arranca con `ModuleNotFoundError`.

| Método | Ruta | Cuerpo / notas |
|---|---|---|
| `POST` | `/v1/game/rooms` | `mode`, `pack?`, `rounds?`, `questionSeconds?`, `fair?`, `hostName?` → `code`, `hostToken`, `expiresAt` |
| `POST` | `/v1/game/rooms/{code}/join` | `name` → `playerToken`, `playerId`, snapshot del lobby |
| `POST` | `/v1/game/rooms/{code}/rejoin` | `playerToken` en el header → mismo asiento y puntos |
| `GET` | `/v1/game/rooms/{code}/state?since=` | `role=player\|display`; `200` snapshot o `204` si `rev` no cambió |
| `POST` | `/v1/game/rooms/{code}/answer` | `round`, `option` → `accepted`, `receivedAt` |
| `POST` | `/v1/game/rooms/{code}/wager` | Fase 3: `round`, `points` |
| `POST` | `/v1/game/rooms/{code}/host/{action}` | `start`, `next`, `kick`, `close` (solo host) |
| `POST` | `/v1/game/rooms/{code}/reaction` | `kind` 0..5 |
| `POST` | `/v1/game/rooms/{code}/report` | `questionId`, `reason?` |
| `GET` | `/v1/game/packs` | Packs activos por fecha (`active_themes`) |
| `GET` | `/v1/game/health` | Salas abiertas, preguntas en banco, versión del banco |

Notas de contrato:

- `serverNow` va en **cada** snapshot; el cliente calcula el desfase una vez y lo interpola.
- `display` recibe `art` (URL de póster `w342`/`w92`) y el código en grande; `player` recibe
  solo lo necesario para votar.
- `state` con `since` igual a la `rev` actual **no** serializa el cuerpo (204).
- Errores: `404` sala cerrada, `409` sala llena / fase incorrecta, `410` token inválido,
  `429` rate limit. Mensajes cortos y en español, sin jerga.

### 5.1 Máquina de estados

```
lobby → countdown(3 s) → question(20 s) → reveal(5 s) → question … → podium → closed
```

- `answer` solo en `question`; una por jugador y ronda. Tardía → rechazada con `accepted:false`.
- `next` solo en `reveal` (corta la espera). `start` solo en `lobby`.
- `close` desde cualquier fase (host). `podium` es terminal salvo «revancha», que crea **otra**
  sala con el mismo grupo y preguntas nuevas.
- El servidor avanza solo: nadie tiene que pulsar nada para que la partida siga.

---

## 6. Modelo de datos

Todo en `server/sync/schema.sql` (idempotente, `create table if not exists` + `alter` con guardia).
El sync aplica el esquema al arrancar (`apply_schema`), como ya hace.

| Tabla | Columnas |
|---|---|
| `trivia_questions` | `id`, `kind`, `payload jsonb` (título, opciones, arte), `answer_key` (separado), `difficulty smallint`, `pack text`, `source_ref text`, `lang`, `hash unique`, `created_at` |
| `trivia_served` | `question_id`, `group_id`, `served_at` (rotación por grupo familiar) |
| `trivia_reports` | `question_id`, `reason`, `created_at` |
| `game_results` | `id`, `mode`, `pack`, `players jsonb`, `rounds`, `started_at`, `ended_at` |

Las salas **no** se persisten en vivo. Solo `game_results` al cerrar.

`group_id` = el `sync_id` (hash del código de continuidad) que ya existe. Así la misma familia
no repite preguntas y puede tener ranking acumulado sin cuentas nuevas.

---

## 7. Banco de preguntas (el corazón)

Worker nuevo `server/sync/trivia_bank.py`, invocado desde `main.py` con la misma lógica de
`*_due` + `sync_meta` que usan las pasadas profundas.

Proceso:

1. Elige títulos con señal real, rotando por popularidad y por antigüedad de uso.
2. Genera preguntas de la tabla de §3.1, con distractores disjuntos.
3. Calcula dificultad: `1` fácil (muy popular), `2` media, `3` difícil (cola larga).
4. Guarda `hash` para no duplicar; descarta la pregunta si ya existe.
5. Presupuesto por pasada (`TRIVIA_MAX_PER_CYCLE`, default 400) y tope del banco (`TRIVIA_BANK_CAP`,
   default 8000, poda las nunca servidas más viejas).
6. Packs de temporada reusando `themes.active_themes()`: Día de Muertos, Halloween, Navidad,
   «Solo anime», «Novelas». Cero contenido extra.
7. Rotación: al armar partida se excluyen las `trivia_served` del `group_id` de los últimos
   90 días; si el banco del pack se agota, se permite repetir lo más viejo.
8. Botón **«Reportar pregunta»** en el reveal → `trivia_reports`; 3 reportes = fuera del banco.

Volumen objetivo: 3 000–6 000 preguntas (~5 MB). Con 12 por partida sobra para años.

**Riesgo número uno del producto:** un distractor que también es correcto (repartos
corales, actores que aparecen en dos sagas). Se cierra en la generación (conjuntos disjuntos) y
con reportes. Cualquier pregunta que no se pueda garantizar, no se genera.

---

## 8. Puntuación (exacta, igual en servidor y cliente)

| Concepto | Valor |
|---|---|
| Base por dificultad | 1000 / 1500 / 2000 |
| Bono de velocidad | `base * 0.25 * fracción de tiempo restante`, redondeado |
| Racha | +100 por acierto consecutivo, tope +500 |
| Respuesta incorrecta o tardía | 0 puntos, racha a 0 |
| Apuesta a la pista | 200–1000; fallar resta lo apostado (nunca deja negativo) |
| Charadas | +1 por acierto, −1 por pase (nunca negativo) |
| Modo `fair` | Bono de velocidad = 0 |

La fórmula vive **duplicada a propósito** (servidor en Python, cliente en Kotlin) y hay un test
de paridad con la misma tabla de casos en los dos lados. Si cambia un número, cambian los dos.

---

## 9. Presupuesto de rendimiento (la TV lenta)

El tablero es la pantalla **más ligera** del producto. Reglas obligatorias:

1. **Cero dependencia del catálogo.** La pantalla de Juegos no lee `remote_index.json`, no
   espera a `XuperCatalog` y no llama `/v1/index`. Recibe la pregunta ya armada.
2. **Nunca arranca `:x4k`.** Ni precalentamiento ni servicio. En un equipo de 1.5 GB eso decide
   si se juega o si el LMK mata la tele.
3. **Imágenes al tamaño, en variante baja.** Póster `w342` (~40 KB) con `size()` explícito;
   RGB_565 en modo ligero.
4. **Sin animaciones infinitas.** Nada de Ken Burns ni springs; manda `LocalReducedEffects`.
5. **Modo silencio.** Con sala abierta se pausan feed (2 min), EPG (5 min), banners y trending.
6. **Reloj barato.** El texto se actualiza **una vez por segundo**; la barra/aro va en una capa
   aislada. Nunca repintar el árbol entero a 60 fps.
7. **QR una sola vez,** cacheado como bitmap, jamás dentro de la composición.

### Criterio de aceptación medido en la TCL (Android 9, 2 GB)

- Abrir la sala desde el menú ya abierto: **< 1.5 s**.
- Estado servido: **< 2 KB** (204 cuando no cambia).
- Memoria extra del proceso: **≤ 15 MB**.
- Reloj sin tirones visibles.
- Partida completa con la app en **modo ligero forzado** y `:x4k` **ausente** de procesos.
- La misma partida con **3 teléfonos reales**.

Si algo falla, se ajusta el cliente (subir el sondeo a 1 s, quitar el póster), no el servidor.

---

## 10. Integración Android

### 10.1 Archivos nuevos

| Archivo | Contenido |
|---|---|
| `data/games/GameModels.kt` | DTOs (`@Serializable`): sala, ronda, jugador, snapshot |
| `data/games/GameCodes.kt` | **Puro:** alfabeto, normalización, formato `ABC-123` |
| `data/games/GameScore.kt` | **Puro:** fórmula de puntos (paridad con el servidor) |
| `data/games/GameClient.kt` | HTTP con `X-Api-Key` + token, timeouts 2/3 s, **jamás lanza** |
| `data/games/GameStore.kt` | Prefs: token por sala, apodo, último código |
| `data/games/GameDeepLink.kt` | `visionplus://play/join?code=` → ruta |
| `ui/games/GamesHomeScreen.kt` | Crear / unirse / últimas salas |
| `ui/games/GameRoomScreen.kt` | Lobby, pregunta, reveal, podio (móvil) |
| `ui/games/GameDisplayScreen.kt` | Tablero TV: código grande, QR, reloj, marcador |
| `ui/games/GameQr.kt` | QR cacheado (una generación por sala) |

### 10.2 Archivos modificados

| Archivo | Cambio |
|---|---|
| `ui/Navigation.kt` | `Route.Games`, `Route.GameRoom(code, role)` |
| `ui/PhoneApp.kt` | Pestaña «Juegos» en `TABS` (después de «Deportes en vivo») + pantalla |
| `ui/TvApp.kt` | `TV_RAIL_SPECS` + pantalla (Fase 2) |
| `AndroidManifest.xml` | host `play` en el `intent-filter` de `visionplus` |
| `MainActivity.kt` | ruta del deep link (mismo camino que `visionplus://watch`) |
| `data/config/RemoteConfig.kt` | flag `games_enabled` (default `true`) |
| `ui/title/TitleScreen.kt`, `ui/tv/TvTitleScreen.kt` | «Juega la trivia de esta serie» (Fase 4) |
| `launcher/.../ui/Surfaces.kt` | tile «Juegos» en Apps (Fase 2) |

### 10.3 Reglas de integración

- El cliente de juegos **no** importa nada de `data/xuper`, `data/offline` ni `Popularity`.
- El sondeo va dentro de `repeatOnLifecycle(RESUMED)`; al salir se corta.
- Si `games_enabled` es `false`, la pestaña se muestra deshabilitada con explicación.
- **Sin emojis** en cadenas de UI (`UiNoEmojiTest`).
- Tests de Compose: **añadir casos a una clase con sandbox ya existente** (patrón
  `GeePickDialogDuplicateTest`). Nunca una clase nueva con su propio `@Config`: ya tumbó la
  suite completa más de una vez.
- Cachés estáticas del cliente con `resetMemoryForTests()` (lección de `GeeSportsSource`).

---

## 11. Fases, entregables y puertas

### Fase 0 — Contrato y banco (sin tocar la app)

Entrega: este documento, `games.py` con la lógica pura y sus tests, `trivia_bank.py` generando
**200 preguntas reales** en local, `test_games.py` + `test_trivia_bank.py` verdes.

Puerta: 200 preguntas revisadas a ojo (10 al azar) sin distractores ambiguos; `pytest` verde;
**cero cambios** en `app/` y `launcher/`.

### Fase 1 — MVP trivia (solo móvil)

Entrega: rutas en `app.py`, esquema, sala con código, 1–8 jugadores, 12 rondas, puntaje,
podio, reconexión. Pantalla móvil completa. Sin TV.

Puerta: partida completa de 8 jugadores simulados contra el servidor local; **≥ 30 tests
backend + ≥ 15 Android**; sin OTA.

### Fase 2 — Pantalla TV + QR + deep link

Entrega: `GameDisplayScreen`, rail de TV, tile del launcher, QR, `visionplus://play/join`.

Puerta: partida real en la TCL + 3 teléfonos; se cumplen los números de §9.

### Fase 3 — Charadas y Apuesta a la pista

Entrega: equipos, mazos, tarjeta privada, apuestas.

Puerta: una noche de juego completa sin reiniciar la app.

### Fase 4 — Packs de temporada, «¿Quién es más…?», reportes, ranking

Entrega: packs por fecha, votaciones, reportes, ranking por `group_id`.

Puerta: pack de temporada encendido y apagado por fecha real.

### Fase 5 (opcional) — Bingo de clichés

Solo si las fases 1–4 se sienten sólidas. Requiere tocar el reproductor: vuelve a aplicar la
regla de las zonas prohibidas.

**Regla de oro:** cada fase termina con la suite completa en verde y un reporte de evidencia.
No se empieza la siguiente sin pasar la puerta.

---

## 12. Pruebas exigidas

### Backend (`server/tests/`)

- `test_games.py`
  - Código: 6 caracteres del alfabeto, sin `I/L/O/0/1`, normalización, colisión, formato.
  - Apodo: recorte, largo, control chars, vacío, lista de groserías.
  - Puntaje: base por dificultad, bono ≤ 25 %, racha con tope, tardía = 0, duplicada rechazada.
  - Máquina de estados: transiciones válidas e inválidas; `next` solo en reveal; `answer` solo
    en question.
  - Snapshot: `question` **no** trae respuesta; `reveal` sí; `display` vs `player`; `since` → 204.
  - Límites: sala llena, TTL, barrendero, rate limit, host equivocado.
  - Reconexión: mismo asiento y puntos; token inválido → 410.
- `test_trivia_bank.py`
  - Distractores disjuntos por `tmdb_id`, grupo y reparto.
  - Sinopsis sin el título; descarte si lo contiene.
  - Dificultad y `hash` dedupe; tope del banco; rotación por grupo.
  - Packs por fecha con `active_themes`.
- Contrato: los endpoints existen, piden `X-Api-Key` y respetan errores (patrón `test_api_serve`).

### Android (`app/src/test/`)

- `GameCodesTest` — alfabeto, normalización, formato, basura.
- `GameScoreTest` — **misma tabla de casos que Python** (paridad).
- `GameClientTest` — parseo, 204, errores HTTP, timeout que no lanza (servidor de sockets real,
  patrón `GeeProbeTest`).
- `GameWiringTest` — pestaña en `TABS`, rail TV, host del deep link en el manifest, flag
  `games_enabled`, **no** importa `data/xuper`/`data/offline`, **no** arranca `:x4k`.
- UI: casos añadidos a un sandbox existente (no clase nueva).

Mínimos: Fase 0 → 15 backend; Fase 1 → 30 backend + 15 Android; Fase 2 → +8 Android;
Fase 3 → +10 backend + 8 Android.

---

## 13. Comandos

Desde `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+\`:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testNormalDebugUnitTest
.\gradlew.bat :launcher:testDebugUnitTest
python -m pytest server/tests -q
```

Build de release (solo si el usuario lo pide):

```powershell
.\gradlew.bat :app:assembleNormalRelease
```

Publicar OTA (**solo con autorización explícita**):

```powershell
.\tools\publish_ota.ps1 -Version X.Y.Z -NotesFile ota_notes_X.Y.Z.md
```

---

## 14. Despliegue al VPS

Credenciales en `AGENTS.md` (sección «Servidor de índice»). **No** las copies a archivos nuevos.

Protocolo obligatorio (ya aprendido a golpes):

1. Respaldar cada archivo que se toca con `*.bak-AAAAMMDD-HHMM`.
2. Subir con `scp` y ejecutar con `bash /tmp/x.sh`. **Nunca** un `ssh` largo con comillas: PowerShell
   las destroza.
3. `docker compose up -d --build api sync`.
4. **`sync` usa `COPY *.py`** (no hay que tocarlo). **`api` usa lista explícita**: añadir
   `games.py` a `server/api/Dockerfile` o el contenedor no arranca.
5. Antes de añadir variables al `.env`: comprobar que termina en salto de línea
   (`tail -c1 .env`), o el append pega dos variables.

### Verificación posterior (todas obligatorias)

- `/healthz` → 200.
- `/v1/game/health` → 200 con salas y tamaño del banco.
- `/v1/index?limitFilms=100` → 200 y tamaño similar al previo (**no rompiste el índice**).
- Tablas nuevas creadas (`trivia_questions`, `trivia_served`, `trivia_reports`, `game_results`).
- Banco con ≥ 200 filas.
- Humo con `curl`: crear sala, unir 2 jugadores, `start`, responder, `reveal`, podio.
- `docker stats` del contenedor `api` < 300 MB tras el humo.

### Rollback

Restaurar los `*.bak-*`, `docker compose up -d --build api sync`, verificar `/healthz` y
`/v1/index`. Las tablas nuevas pueden quedarse (son aditivas).

**Regla operativa:** si hay una partida en curso, no se despliega. Un `up --build api` reinicia
el contenedor y mata las salas.

---

## 15. Reglas del proyecto que aplican

- Cada fix trae su test. Funciones puras en `internal` para poder testearlas.
- Nada de emojis en UI (guardian `UiNoEmojiTest`).
- Una clase Compose nueva con su propio sandbox Robolectric puede colgar la suite completa:
  reutilizar sandbox existente.
- `Diagnostics` no reporta nada bajo Robolectric (ya está resuelto; no lo rompas).
- Textos en español, tono del producto, sin jerga técnica en la UI.
- Si un cambio puede afectar al arranque, al reproductor o a las descargas: **no se hace en este
  lote**.
- Al cerrar: actualizar `AGENTS.md` con el lote, la versión publicada y los pendientes.

---

## 16. Decisiones ya tomadas (no preguntar)

| Tema | Decisión |
|---|---|
| Nombre | Vision+ Play / «Sala» |
| Autoridad | VPS |
| Transporte | Sondeo 700 ms + 204 |
| Jugadores | 8 (12 máx) |
| Rondas | 12 |
| Equipos | Solo desde Fase 3 |
| Puntaje | Competitivo por defecto; `fair` opcional |
| Persistencia | Solo resultado final |
| Pantalla TV | Fase 2, nunca obligatoria |
| Contenido | Generado; sin editor manual en v1 |

## 17. Fuera de alcance (no lo hagas)

- Chat de texto, voz o video.
- Cuentas nuevas, login, monedas, premios, sorteos.
- WebSocket, Redis, colas, microservicios.
- Clips de audio/video o frames de las películas.
- Trivia dentro del reproductor (eso es Fase 5).
- Cambios en la firma, en el launcher más allá del tile, o en el motor.

## 18. Primer paso concreto

1. Leer `AGENTS.md` completo (secciones «Pitfalls», «Build / OTA», «Servidor de índice»).
2. Abrir `server/api/sync.py` como modelo de módulo puro + `server/tests/test_sync.py` como
   modelo de tests.
3. Crear `server/api/games.py` con lo puro (códigos, apodos, puntaje, máquina de estados,
   filtrado de snapshot) y su test. **Sin rutas todavía.**
4. Crear `server/sync/trivia_bank.py` y generar 200 preguntas en local.
5. Reportar: tabla de las 200 preguntas (tipo, pregunta, respuesta, distractores) y el verde de
   `pytest`.

Si algo de este documento no cuadra con el código real, **para y repórtalo**; no improvises.
