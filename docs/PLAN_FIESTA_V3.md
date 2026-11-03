# Vision+ Fiesta 3.0 — plan de juegos originales tipo party pack

**Fecha:** 2026-10-31  
**Estado:** propuesta base. **Actualización 2026-10-31:** la beta 3.0 ya se implementó con seis juegos: La Última Palabra, Vox Populi, Fuera de Época, Texto Roto, Franela Fest y El Tasador. El plan 4+4 queda como catálogo futuro. Ver `docs/BETA_FIESTA_3.0.md`.
**Base:** `docs/ANALISIS_JACKBOX_PORTER_VISIONPLUS.md` (análisis de los 4 packs instalados).  
**Objetivo:** construir un party pack propio, con mecánicas equivalentes a las de Jackbox, pero con **contenido, chistes, voces, arte y personajes originales de Vision+**.

---

## 0. Opinión ejecutiva

**Sí se puede, y es la dirección correcta. Pero no conviene hacer “los 14 juegos con todas sus mecánicas y todos sus chistes” de una vez.**

La mecánica se puede replicar. El chiste no se copia: se escribe. La sátira, los diálogos y los personajes deben ser originales; copiar los de Jackbox sería infracción y, además, el producto se sentiría derivativo.

La forma de que salga bien:

1. **Temporada 1 con 4 juegos nuevos**, no 14.
2. **Conservar 5 juegos actuales** que ya funcionan y mapean a mecánicas de Jackbox.
3. **Archivar 2 juegos** que no aportan o son muy caros de mantener (con datos, no por corazonada).
4. **Limpiar la deuda** (v1, mecánicas sin UI, assets muertos) antes de apilar más juegos.
5. **Producir contenido como si fuera una sala de escritores**: 200+ prompts por juego, pruebas con personas reales, métricas de risa/voto, moderación.
6. **Voces nuevas por juego**, con casting y dirección; no reutilizar una sola voz para todo.
7. **Arte original y consistente**, con masters en el disco de 400 GB y assets comprimidos en el APK.

El disco no es el límite. Con 528 GB libres vistos en `df`, hay espacio de sobra para masters. El límite real es **escritura, curaduría, QA y tamaño del APK**.

---

## 1. Reglas del proyecto

### 1.1 Lo que sí

- Replicar **mecánicas** (escribir, votar, dibujar, ordenar, apostar, deducir, encuestar).
- Crear **sátira original** sobre streaming, cine, telenovelas, anime, deportes, influencers, familia, trabajo, tecnología y cultura mexicana/LATAM.
- Crear **personajes y voces originales** con TTS local.
- Crear **arte original** con ComfyUI + retoque humano.
- Usar el catálogo de Vision+ (52k títulos, canales, deportes) como materia prima.
- Reutilizar la infraestructura de Vision+ Play: salas, QR, TV/teléfono, voz, arte, tests.

### 1.2 Lo que no

- Copiar nombres, personajes, chistes, diálogos, preguntas, respuestas, arte, música o código de Jackbox.
- Usar “Jackbox”, “Quiplash”, “Fibbage”, “Tee K.O.”, “Survey Scramble”, “Trivia Murder Party” en el producto o marketing.
- Clonar la identidad visual de Jackbox.
- Prometer “las mismas voces” o “los mismos chistes”.
- Usar contenido de Jackbox para entrenar modelos o generar variantes.
- Poner texto libre de jugadores sin moderación.

### 1.3 Nombre de trabajo

**Vision+ Fiesta** (o **Vision+ Play: Temporada 1**).  
Cada juego tiene nombre propio y original. La marca Exterior no menciona a Jackbox.

---

## 2. Limpieza: qué conservar y qué descartar

### 2.1 Los 7 juegos actuales

| Juego actual | Mapea a | Decisión propuesta | Razón |
|---|---|---|---|
| **Trivia Vision+** | Trivia Murder Party / Timejinx | **Conservar y pulir** | Es el pilar; ya tiene 3,125 preguntas y reglas v2 |
| **Falso Conocimiento (Fibbage)** | Fibbage 4 | **Conservar y ampliar** | Ya es el equivalente más directo; solo faltan mazos |
| **El Impostor** | Roomerang / Hypnotorious | **Conservar y ampliar** | Deducción social fuerte; se le pueden añadir roles |
| **Duelo de Dibujo Ciego** | Tee K.O. / Drawful | **Conservar y ampliar** | El dibujo ya funciona; base para Franela Fest |
| **Subasta Ciega** | Junktopia / subastas | **Conservar como secundario** | Es original y distinto; no es Jackbox, pero funciona |
| **Charadas** | Ninguno directo | **Archivar** | Depende de actuación en persona, es difícil de escalar y de moderar; no es el fuerte del formato TV+teléfono |
| **Apuesta a la pista** | Ninguno directo | **Archivar** | Muy complejo, nicho, solapa con Trivia/Fibbage y consume mucho mantenimiento |

**No borrar de inmediato.** Primero ocultar del catálogo con feature flag, medir una release, y luego retirar el código. Las salas activas tienen TTL de 4 h; se puede drenar sin romper nada.

### 2.2 Deuda que hay que quitar antes de sumar juegos

| Deuda | Acción | Impacto |
|---|---|---|
| `rules_version=1` y sus ramas | Retirar tras una release de compatibilidad; dejar solo v2/v3 | Menos combinaciones, menos tests, menos bugs |
| Mecánicas v2 sin UI (interrogatorio libre/radar, relevo de artistas, sindicato, cazador, doble hueco) | Terminar solo si las usa un juego nuevo; si no, borrar | Menos código muerto |
| 60 audios, 7 tableros de tutorial, 36 avatares y 5 partículas sin conectar | Integrarlos al hub/lobby/podio o archivar | Menos peso y menos confusión |
| Catálogo duplicado servidor/cliente | Una fuente JSON + test de paridad | Evita drift |
| Assets viejos inconsistentes | Auditar; conservar lo que sirva; archivar el resto | Identidad visual coherente |
| Tests v1 | Eliminar junto con el código v1 | Suite más rápida y clara |

### 2.3 Qué reutilizar de lo viejo

- **Avatares, medallas, partículas y SFX** originales de Vision+.
- **Narradora Dalia** como voz del hub o presentadora transversal.
- **Tableros de tutorial** como base de los nuevos tutoriales.
- **Pipeline de TTS Qwen3**, `tools/generate_narrator_voice.py`, `tools/make_game_sfx.py`, `tools/comfy_game_art.py`.
- **Componentes Compose** de TV/teléfono, foco D-pad, lobby, QR, podio.
- **Servidor autoritativo** y tests de secretos.

---

## 3. Catálogo propuesto

### 3.1 Temporada 1 (4 juegos nuevos)

| # | Juego | Base | Prioridad | Esfuerzo | Por qué |
|---:|---|---|---|---:|---|
| 1 | **La Última Palabra** | Quiplash 3 | **P0** | 1-2 semanas | Máximo retorno; reutiliza Fibbage; texto + voto |
| 2 | **Vox Populi** | Survey Scramble | **P0** | 2-3 semanas | Gran factor social; contenido original de encuestas |
| 3 | **Fuera de Época** | Timejinx | **P1** | 1-2 semanas | Extiende Trivia; fácil de producir |
| 4 | **Texto Roto** | FixyText | **P1** | 2-3 semanas | Original, barato en arte, texto en tiempo real |

### 3.2 Temporada 2 (4 juegos)

| # | Juego | Base | Prioridad | Esfuerzo | Nota |
|---:|---|---|---|---:|---|
| 5 | **Franela Fest** | Tee K.O. | P1 | 4-6 semanas | Dibujo + eslogan + camiseta; arte pesado |
| 6 | **En Su Lugar** | Quixort | P2 | 3-4 semanas | Requiere WebSocket/tiempo real |
| 7 | **El Tasador** | Junktopia | P2 | 2-3 semanas | Objetos raros + pitch + tasación |
| 8 | **Escala Humana** | Nonsensory | P2 | 3 semanas | Números/dibujo; reutiliza Wager/Dibujo |

### 3.3 Lo que no se haría por ahora

- **Dodo Re Mi (ritmo)**: la latencia TV/teléfono y el polling actual lo hacen muy riesgoso. Se puede evaluar cuando exista WebSocket y audio de baja latencia.
- **Los 14 juegos de golpe**: la calidad de escritura y el QA se diluyen.
- **Copias 1:1 de UI/animación**: mejor identidad propia.

### 3.4 Resultado

- 5 juegos actuales conservados (Trivia, Fibbage, Impostor, Dibujo, Subasta).
- 4 nuevos en Temporada 1 → **9 juegos**.
- 4 más en Temporada 2 → **13 juegos**.
- Charadas y Apuesta archivados.

---

## 4. Diseño de los 4 juegos de Temporada 1

### 4.1 La Última Palabra (Quiplash-like)

**Fantasy:** un concurso de ingenio donde la respuesta más cafre/ingeniosa gana; la tele es el escenario, el teléfono es la libreta.

**Jugadores:** 3-8. **Duración:** 12-15 min.

**Fases:**

```text
lobby → countdown → prompt (45 s) → matchup (20 s) → reveal (5 s)
      → siguiente ronda → final_prompt (45 s) → final_vote (25 s) → podium
```

**Mecánica:**

- Todos reciben el mismo prompt y escriben una respuesta.
- Las respuestas se emparejan al azar; nadie vota la suya.
- La tele muestra dos respuestas anónimas; los teléfonos votan A/B.
- +100 por voto recibido; la respuesta más votada de la ronda recibe bonus.
- Ronda final: un solo prompt, todas las respuestas a la vista; +1,000 al ganador, +500 al segundo.
- Público: voto separado; +300 al favorito del público.
- Desempate: voto del público y, si persiste, respuesta más rápida.

**Contenido:**

- 250 prompts originales (cine, series, telenovelas, anime, deportes, familia, trabajo, streaming, tecnología).
- 80 líneas de host, 40 reacciones, 20 líneas de podio, 10 de timeout.
- 3 variantes por línea, seleccionadas por energía/tono.

**Voz:** Nora Vela, presentadora sarcástica y rápida; ríe, se burla con cariño, sube el ritmo en el reveal.

**Arte:** escenario neón, tarjetas de respuesta, marcador, trofeo, fondo de “estudio de TV”.

**Riesgos:** moderación de texto libre; respuestas vacías; emparejamiento con número impar; jugadores que no escriben.

**Definición de hecho:** 20 partidas simuladas, 3 teléfonos reales, 0 fugas de respuestas antes del reveal, filtro de groserías y reporte.

---

### 4.2 Vox Populi (Survey Scramble-like)

**Fantasy:** un noticiero de encuestas callejeras donde la gente dice cosas absurdas y los equipos intentan adivinar qué dijo la mayoría.

**Jugadores:** 2-8, individual o equipos. **Duración:** 10-12 min.

**Fases:**

```text
lobby → countdown → question (5 s) → guess (20 s por turno)
      → reveal (5 s) → dare (10 s, opcional) → siguiente ronda → final_survey → podium
```

**Mecánica:**

- La tele muestra una pregunta de encuesta y las respuestas ocultas (top 6).
- Los equipos alternan turnos y dicen una respuesta; si está en el top, se revela con puntos por rank.
- Tres fallos del equipo = se abre “robo” para el rival.
- **Dare:** tras un acierto, el equipo reta a un rival a adivinar “más alto o más bajo” en el ranking; acierta +300, falla −200.
- Ronda final: una encuesta grande; todos escriben la respuesta que creen número 1; +1,000.
- Público: puede votar la respuesta más divertida; +200 al autor.

**Contenido:**

- 120 preguntas × 6-8 respuestas con frecuencias.
- Las encuestas deben ser **originales y reales** (formularios locales, comunidad Vision+, redes) o etiquetarse claramente como “encuestas de la comunidad”. No inventar “la gente dijo” si no se preguntó.
- 80 líneas de host, 30 reacciones, 20 de podio.

**Voz:** Don Chisme, locutor de radio de chismes, cálido y exagerado.

**Arte:** set de noticiero, pizarra de respuestas, gráficos de barras, público.

**Riesgos:** datos de encuesta; respuestas ambiguas; equipos desiguales; contenido sensible.

**Definición de hecho:** 100 preguntas aprobadas, frecuencias documentadas, 3 teléfonos, modo equipos y modo individual.

---

### 4.3 Fuera de Época (Timejinx-like)

**Fantasy:** un profesor excéntrico viaja en el tiempo y pone a prueba si sabes cuándo pasó cada cosa.

**Jugadores:** 3-8. **Duración:** 10-12 min.

**Fases:**

```text
lobby → countdown → event (5 s) → place (20 s) → bet (10 s)
      → reveal (5 s) → siguiente ronda → final_timeline → podium
```

**Mecánica:**

- La tele muestra un evento (película, serie, invento, deporte, tecnología).
- Cada jugador lo coloca en una línea temporal de 5 eventos; o elige el año.
- Apuesta opcional de puntos a que su posición es exacta.
- Puntos: posición exacta +500, adyacente +250, lejos 0; apuesta x2 o pierde.
- Ronda final: línea temporal maestra con 8 eventos; ordenar todos; +1,000.
- Público: apuesta por el evento más antiguo/nuevo.

**Contenido:**

- 200 eventos con año verificable, categoría y país.
- Evitar fechas ambiguas o polémicas; una sola respuesta correcta.
- 80 líneas de host, 30 reacciones, 20 de podio.

**Voz:** Profesor Tic Tac, pedante, entusiasta, con efectos de máquina del tiempo.

**Arte:** línea temporal retro-futurista, relojes, tarjetas de época, fondo de laboratorio.

**Riesgos:** datos incorrectos; eventos desconocidos para el público; apuestas que desbalancean.

**Definición de hecho:** 200 eventos verificados por dos fuentes, 3 teléfonos, simulación de 20 partidas.

---

### 4.4 Texto Roto (FixyText-like)

**Fantasy:** un chat grupal que se rompe en tiempo real; todos escriben a la vez y el resultado es un desastre que hay que votar.

**Jugadores:** 3-8. **Duración:** 8-10 min.

**Fases:**

```text
lobby → countdown → seed (5 s) → write (45 s, simultáneo)
      → merge (5 s) → vote (20 s) → reveal (5 s) → siguiente ronda → podium
```

**Mecánica:**

- La tele muestra una conversación/escena con roles (cliente, mesero, IA, mamá, jefe).
- Todos escriben una o dos líneas a la vez; el servidor las mezcla en orden.
- La tele lee la escena resultante; todos votan la línea más graciosa.
- +300 por voto; +100 por participar; carta de sabotaje una vez por partida (cambia una palabra o impone una restricción).
- Ronda final: una escena larga con todos; +1,000 al más votado.
- Público: vota la línea más absurda.

**Contenido:**

- 150 semillas de conversación originales (chat de fans, grupo familiar, reunión de trabajo, app de citas, soporte técnico, stream).
- 80 líneas de host, 30 reacciones, 20 de podio.
- Moderación fuerte: texto libre, límite de caracteres, filtro, reporte, kick.

**Voz:** La Máquina, IA deadpan con glitches; el “editor” exasperado.

**Arte:** interfaz de chat, glitch, oficina/casa/estudio, avatares.

**Riesgos:** concurrencia de texto; latencia; moderación; que el merge se sienta injusto.

**Definición de hecho:** 8 jugadores escribiendo a la vez, merge determinista, moderación, 3 teléfonos, 20 simulaciones.

---

## 5. Producción de contenido

### 5.1 Sala de escritores

- 2-3 escritores humanos + LLM local para borradores.
- table reads semanales; se descartan prompts que no generan risa.
- Métricas por prompt: votos recibidos, skip, reportes, veces elegido.
- Regla: si un prompt no recibe al menos un voto en 10 partidas, se revisa o se retira.

### 5.2 Sátira y chistes

**Objetivos:** streaming, algoritmos, telenovelas, cine de oro, anime doblado, deportes, influencers, WhatsApp familiar, oficina, citas, IA, premios, reality shows.

**Herramientas:** exageración, mala dirección, regla de tres, callback, running gag, comparación absurda, autodesprecio, humor de observación.

**Guardarraíles:**

- No punch down; no chistes sobre grupos protegidos.
- No difamación; figuras públicas solo con cuidado y con revisión legal.
- Nada de tragedias reales ni política partidista.
- Modo familiar por defecto; “sin filtro” opcional con confirmación y edad.
- Nombres y marcas ficticias (“una plataforma”, “una telenovela”, “un influencer”).
- Revisión de sensibilidad y legal antes de publicar.

### 5.3 Voces

**Casting inicial:**

| Personaje | Juego | Tono |
|---|---|---|
| **Nora Vela** | La Última Palabra | Sarcástica, rápida, cómplice |
| **Don Chisme** | Vox Populi | Locutor cálido, exagerado, chismoso |
| **Profesor Tic Tac** | Fuera de Época | Pedante, entusiasta, viajero del tiempo |
| **La Máquina** | Texto Roto | IA deadpan, glitch, impredecible |
| **El Crítico** | Franela Fest | Snob de moda, dramático |
| **Dalia / Vera** | Hub | Presentadora transversal de Vision+ |

- Voces sintéticas originales; no imitar celebridades.
- 3-6 variantes por línea; selección por tono, energía, ritmo y WER.
- Léxico de pronunciación: títulos, actores, marcas, slang mexicano.
- Audio: mono, 48 kHz, AAC-LC 48-64 kbps, −16 LUFS, true peak −1.5 dBTP.
- Pruebas de escucha en TV y teléfono; evitar voces fatigantes en sesiones de 15 min.

### 5.4 Arte

- TV 1920×1080; teléfono 1080×1920; WebP/AVIF; sin texto incrustado.
- 15-25 assets por juego (fondo, tarjetas, iconos, trofeo, medallas, transiciones).
- Maestros en `/media/chronos/Disco Duro/vision-assets/games/v3/<juego>/`.
- Exportaciones comprimidas al APK; masters fuera del repo.
- Estilo: fiesta editorial, neón, collage, caricatura; distinto de Jackbox.
- Reutilizar avatares/partículas/SFX existentes cuando encajen.

### 5.5 Almacenamiento

Con 528 GB libres:

| Elemento | Estimado |
|---|---:|
| Masters de arte (14 juegos) | 30-60 GB |
| WAV de voz (14 juegos) | 10-20 GB |
| M4A final | 1-3 GB |
| Videos/animaciones | 10-30 GB |
| Backups | 20-40 GB |
| **Total** | **70-150 GB** |

Sobra espacio. El límite es el APK: mantenerlo por debajo de ~150 MB; usar WebP/AVIF y AAC; evitar videos pesados.

---

## 6. Arquitectura técnica

### 6.1 Servidor

Problema actual: `server/api/games.py` tiene ~204 KB y la clase `Room` acumula decenas de campos por juego. Añadir 8 juegos así lo vuelve inmanejable.

Propuesta incremental:

```text
server/api/games/
  common.py        Room, Player, tokens, snapshot base
  catalog.py       definiciones y metadatos
  engine.py        tick, fases, dispatch
  last_word.py
  vox_populi.py
  fuera_de_epoca.py
  texto_roto.py
  legacy.py        trivia, fibbage, impostor, dibujo, subasta
```

- No hacer un big-bang: migrar primero un juego nuevo a un módulo/plugin y dejar los existentes donde están.
- Interfaz `GameModule`: `phases`, `tick`, `handle_action`, `snapshot`, `catalog`.
- Tests de contrato por juego; simulador de bots.
- Snapshot con filtrado por rol; nunca enviar respuestas antes del reveal.

### 6.2 Android

- `GameCatalog.kt` añade las cartas nuevas.
- `GameModels.kt` (36 KB) no debe crecer sin control: crear paquetes por juego.
- Componentes compartidos: `GameShell`, `TvGameStage`, `PhoneGamePanel`, `RevealCard`, `Podium`, `Timer`.
- TV: foco D-pad, textos grandes, alto contraste, animaciones reducidas.
- Teléfono: teclado, dibujo, vibración, una mano, reconexión.
- Accesibilidad: subtítulos, timers extendidos/sin timers, movimiento reducido.

### 6.3 Transporte

- Turn-based (La Última Palabra, Vox Populi, Fuera de Época): polling actual de 700 ms es suficiente; bajar a 500 ms si hace falta.
- Texto Roto: escritura simultánea; polling 500 ms con UI optimista o WebSocket.
- En Su Lugar: WebSocket/SSE obligatorio.
- Dodo Re Mi: no hacerlo hasta tener WebSocket y audio de baja latencia.

### 6.4 Contenido y assets

- Mazos en JSON versionado, con schema, tags, safety, locale, autor, estado.
- Servidor puede actualizar mazos sin OTA; arte/voz requieren APK o pack descargable.
- Manifiesto de assets con hash, tamaño y versión.
- No meter masters en git.

### 6.5 Telemetría

Añadir a `game_results` o eventos:

- juego, jugadores, rondas completadas, duración, abandono.
- respuestas por prompt, votos, skip, reportes.
- rematch, podio visto, tutorial saltado.
- crash/ANR por juego.

Sin datos no se puede decidir qué retirar. Con datos, sí.

---

## 7. Roadmap

### Sprint 0 — Limpieza y diseño (1-2 semanas)

- Aprobar tono, catálogo y retiros.
- Escribir GDD de los 4 juegos.
- Schema de contenido, voz y arte.
- Auditar assets viejos; decidir qué se reutiliza.
- Prueba física de los 5 juegos que se conservan con 2-3 teléfonos.
- Feature flag para ocultar Charadas/Apuesta.

**Salida:** documentos aprobados + baseline estable.

### Sprint 1 — La Última Palabra (2-3 semanas)

- Servidor: fases, emparejamiento, votos, puntaje, final.
- Android TV/teléfono.
- 250 prompts, 80 líneas de voz, arte base.
- Moderación y reportes.
- 20 simulaciones + 3 teléfonos.

**Salida:** juego jugable de punta a punta.

### Sprint 2 — Vox Populi (2-3 semanas)

- Equipos, ranking, dare, final.
- 120 preguntas con datos originales.
- Voz Don Chisme, arte de noticiero.
- Pruebas de equipo y empates.

### Sprint 3 — Fuera de Época (2 semanas)

- Línea temporal, apuestas, final.
- 200 eventos verificados.
- Voz Profesor Tic Tac, arte retro-futurista.

### Sprint 4 — Texto Roto (2-3 semanas)

- Escritura simultánea, merge, voto.
- 150 semillas, voz La Máquina.
- Moderación reforzada; prueba de 8 jugadores.

### Sprint 5 — Temporada 1 completa (1-2 semanas)

- Hub “Vision+ Fiesta”.
- Tutoriales, medallas, podio, rematch.
- Telemetría y métricas.
- Prueba física con 4-8 teléfonos.
- OTA solo con instrucción explícita.

### Temporada 2

- Franela Fest, En Su Lugar, El Tasador, Escala Humana.
- WebSocket para En Su Lugar.
- Evaluar Dodo Re Mi solo con audio de baja latencia.

---

## 8. Definición de hecho por juego

Un juego no se considera terminado hasta que cumple:

- [ ] GDD aprobado y contenido firmado.
- [ ] 3-8 jugadores, reconexión, expulsión, espectador.
- [ ] Fases y timers en servidor; snapshot filtrado por rol.
- [ ] Sin fugas de respuestas antes del reveal (tests).
- [ ] Moderación de texto libre y reportes.
- [ ] Tutorial de 3 pasos y ayuda en el lobby.
- [ ] Voz del host, SFX, música y mezcla.
- [ ] Arte TV/teléfono y estados de foco.
- [ ] 20 simulaciones de bots y 3 teléfonos reales.
- [ ] Telemetría y métricas por prompt.
- [ ] Accesibilidad: subtítulos, timers, movimiento reducido.
- [ ] Sin crashes/ANR en 10 partidas.
- [ ] Documentación y tests en verde.

---

## 9. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Copiar sin querer chistes/contenido de Jackbox | Legal y reputacional | Sala de escritores, originalidad, revisión, no usar sus decks como fuente |
| Contenido libre ofensivo | Moderación | Filtros, reportes, kick, modo familiar |
| Sátira sobre personas reales | Legal | Ficción, arquetipos, revisión legal |
| Calidad de chistes | Producto | Pruebas con audiencia, métricas, cortar lo débil |
| Latencia en Texto Roto | UX | Polling 500 ms/WebSocket, UI optimista |
| APK demasiado grande | OTA | WebP/AVIF, AAC, sin videos, packs descargables |
| Complejidad del servidor | Mantenimiento | Plugins por juego, tests de contrato |
| Falta de prueba física | Bugs | Sprint 0 y definition of done |
| Voces repetitivas | Cansancio | 3-6 variantes, casting por juego, mezcla |
| Derechos de música/SFX | Legal | Original o licenciado; no Jackbox |

---

## 10. Decisiones que necesito

1. **Tono:** ¿PG-13 familiar por defecto, adulto sin filtro, o ambos con toggle?
2. **Retiros:** ¿archivamos Charadas y Apuesta, o los mantenemos como “Clásicos”?
3. **Primer juego:** ¿arrancamos con **La Última Palabra** (más rápido) o con **Vox Populi** (más diferenciador)?
4. **Voces:** ¿casting nuevo por juego (recomendado) o reutilizar Dalia para todo?
5. **Alcance:** ¿Temporada 1 de 4 juegos o intentamos 6 desde el inicio?

Mi recomendación: **PG-13 con modo sin filtro**, archivar Charadas/Apuesta, empezar con **La Última Palabra**, casting nuevo por juego y **4 juegos** en Temporada 1.

---

## Apéndice A — Schema de contenido

```json
{
  "id": "lw_cine_001",
  "game": "last_word",
  "locale": "es-MX",
  "kind": "prompt",
  "text": "El nuevo reality de Vision+ se llama...",
  "tags": ["cine", "reality", "absurdo"],
  "tone": "pg13",
  "status": "approved",
  "author": "sala",
  "version": 1
}
```

## Apéndice B — Schema de voz

```json
{
  "id": "lw_host_intro_01",
  "game": "last_word",
  "voice": "nora",
  "text": "Bienvenidos a La Última Palabra.",
  "emotion": "sarcastic",
  "variants": 3,
  "duration_ms": 2400
}
```

## Apéndice C — Layout de assets

```text
/media/chronos/Disco Duro/vision-assets/games/v3/
  last_word/
    art/masters/
    art/export/
    voice/wav/
    voice/m4a/
    content/
    manifests/
```

## Apéndice D — Ejemplos de tono (originales)

- “La nueva telenovela de Vision+ se llama ‘Amor en Modo Avión’ y trata de…”
- “El peor consejo que te puede dar un influencer de streaming es…”
- “Si tu smart TV pudiera hablar, lo primero que diría es…”
- “La película que cancelaron por ser demasiado mexicana se llamaba…”
- “El grupo de WhatsApp de la familia se volvió peligroso cuando…”
- “La IA de soporte técnico se enamoró del cliente cuando…”

Estos ejemplos son solo para fijar tono; el contenido final se escribe, se prueba y se aprueba.

---

**Fin del plan.**
