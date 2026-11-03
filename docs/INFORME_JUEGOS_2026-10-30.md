# Informe de juegos — Vision+ Play

**Para:** experto en diseño de videojuegos / análisis de viabilidad
**Producto:** Vision+ (Android TV + teléfonos)
**Versión analizada:** 2.5.197 / vc437 · launcher 1.3.13 / vc36
**Fecha:** 2026-10-30
**Estado:** 7 juegos implementados, backend desplegado en VPS, OTA estable publicada.

---

## 1. Resumen ejecutivo

Vision+ Play es un módulo de fiesta para TV con QR. La TV crea la sala y actúa
como plató; los teléfonos son mandos con información secreta. Hay siete juegos:

1. Trivia Vision+
2. Charadas
3. Apuesta a la pista
4. El Impostor
5. Duelo de Dibujo Ciego
6. Subasta Ciega
7. Falso Conocimiento / Fibbage

La arquitectura es deliberadamente asimétrica:

- **TV:** estado público, reloj, marcador, QR, dibujo, martillo y reveal.
- **Teléfono:** rol, palabra, lienzo, mentira, puja y voto.
- **Servidor:** árbitro de fases, timers, puntaje y filtrado de secretos.

**Decisión de producto actual:** los juegos viven **solo en la app principal**;
Vision+ Home ya no tiene pestaña Juegos. **La sala solo se crea en modo TV**; el
teléfono se une por QR/código y no puede crear salas remotas.

---

## 2. Cómo se juega a nivel de sala

### Flujo general

```
TV: Inicio → Juegos → elegir uno de los 7 modos
  → se crea sala display + QR/código
  → los teléfonos entran con apodo (y opcionalmente como público)
  → el primer teléfono es el anfitrión del ritmo
  → rondas → reveal → podio
```

- La tele **no responde**: solo crea la sala y muestra el estado.
- El primer teléfono que entra es el anfitrión y controla `start/next`.
- El teléfono puede entrar como **público/espectador** aunque la sala esté llena.
- La sala vive en memoria del servidor: si la API se reinicia, la partida se
  pierde; el resultado final se guarda en Postgres.
- La app y la tele usan sondeo HTTP corto (700 ms en fases rápidas, 1 s en el
  resto), no WebSockets.

### Límites de sala

| Límite | Valor |
|---|---:|
| Jugadores activos | 8 por defecto, 12 máximo duro |
| Público | Sin tope explícito en el código actual |
| Salas simultáneas | 100 |
| TTL de sala | 4 h |
| Sala inactiva | 30 min |
| Desconexión que deja de bloquear | 90 s |
| Cuerpo máximo de acción | 64 KB |
| Rate limit de acciones | 30/min por jugador (excepto `draw_partial`) |
| Idempotencia de acción | 1.5 s |
| Trazos por dibujo | 160 |
| Puntos de dibujo | 2,400 |

---

## 3. Los siete juegos

### 3.1 Trivia Vision+

- 8 rondas de 4 opciones.
- 20 s por pregunta; reveal 5 s.
- Puntaje por dificultad, velocidad (tope 25 %) y racha (tope 500).
- Packs: general, cine, series, anime, infantil, novelas.
- En modo display el ritmo depende de todos los teléfonos: la pregunta avanza
  cuando todos responden y el reveal cuando todos pulsan Continuar.
- **Fortaleza:** contenido generado del catálogo, no preguntas escritas a mano.
- **Riesgo:** en una mesa lenta, la espera de “todos listos” puede alargar la
  partida; el anfitrión no puede avanzar solo.

### 3.2 Charadas

- 6 turnos.
- El actor ve un título y un póster en su teléfono.
- 60 s, 3 pases, 1,000 puntos por acierto.
- El resto adivina en voz alta.
- **Fortaleza:** no exige escribir ni dibujar; funciona con cualquier edad.
- **Riesgo:** el turno es de una sola persona; el resto es público puro. Con
  actores tímidos, el silencio se siente.

### 3.3 Apuesta a la pista

- 6 rondas, 3 pistas progresivas.
- Apuesta 200/500/1,000 antes de la primera pista.
- Factores x3, x2, x1; fallar descuenta la apuesta (nunca deja puntos negativos).
- **Fortaleza:** decisión económica real y tensión de riesgo.
- **Riesgo:** el que no conoce el catálogo puede quedar fuera de la conversación.

### 3.4 El Impostor

**Idea:** todos comparten un lugar secreto; uno no lo sabe y debe fingir.

#### Fases

```
countdown 3 s
→ secret 11 s
→ questions: 25 s por pareja (3–4 jug.), 20 s (5–6), 15 s (7–8)
→ vote 22 s
→ guess 20 s (si lo atraparon)
→ reveal 5 s
```

- El impostor se elige al azar con semilla estable por sala/ronda.
- Cadena dirigida: A pregunta a B, B a C, etc.
- El hablante tiene botón **«Siguiente pregunta»** para avanzar antes del reloj.
- La tele muestra quién interroga a quién y la pregunta sugerida; no ve la
  palabra.
- El crew ve palabra, categoría y pista en su teléfono.
- El impostor ve solo «Eres el impostor» y una categoría clasificada.

#### Votación y escape

- Cada jugador vota a otro; no puede votarse a sí mismo.
- Si el impostor está entre los más votados, incluso en empate → `guess`.
- Si el top es un crew único → el impostor escapa.
- `guess`: 8 opciones (correcta + 7 señuelos).
- Si acierta, roba la ronda; si falla, gana el equipo.

#### Puntaje

| Situación | Puntos |
|---|---:|
| Crew que votó al impostor | +1000 |
| Crew extra si el impostor falla el guess | +300 |
| Impostor atrapado que adivina | +800 |
| Impostor no atrapado | +1500 |

#### Notas para el experto

- El empate ya no salva gratis al impostor.
- 8 opciones reducen el “guess” aleatorio a 12.5 %.
- La ronda base máxima con 8 jugadores es ~2:41; 3 rondas ~8:06 sin contar
  lobby ni reveals esperando listos.
- El último hablante escucha más información que el primero; la rotación ayuda,
  pero no elimina la ventaja de información.
- En 3 jugadores, el impostor puede votar al crew que lo acusa y buscar un
  empate; el arreglo del empate ya lo obliga a `guess`, pero el desequilibrio de
  información sigue siendo alto.

### 3.5 Duelo de Dibujo Ciego

**Idea:** un artista dibuja una frase absurda; los demás inventan el título.

#### Fases

```
countdown 3 s
→ draw 45 s
→ title 30 s
→ vote 22 s
→ reveal 5 s
```

- El artista rota por ronda.
- El artista elige **1 de 2 prompts** antes de dibujar; si no elige, se usa el
  primero.
- Dibuja en un lienzo táctil con 6 colores, 3 grosores, deshacer y borrar.
- El teléfono envía **trazos vectoriales normalizados 0..1000**, no imágenes.
- **Trazos parciales**: envía el arreglo completo cada ~2.5 s y al levantar el
  dedo; la tele muestra el 75 % como vista previa y el 100 % al pasar a `title`.
- Los demás escriben un título falso.
- El servidor arma **mínimo 5 opciones** con título real + mentiras + decoys de
  casa; las salas pequeñas no quedan en un 50/50.
- Un título demasiado parecido al real se rechaza:
  - similitud `SequenceMatcher ≥ 0.82`, o
  - texto contenido dentro del real.
- El artista no vota; el público sí.

#### Puntaje

| Situación | Puntos |
|---|---:|
| Votar el título real | +1000 |
| Autor de mentira, por voto | +500 |
| Mentira con 3+ votos | +300 extra |
| Artista, por voto real | +600 |
| Artista si 0 votan la verdad (salas de 4+) | −500 |
| Favorito del público | +300 al autor/artista |

#### Notas para el experto

- La penalización al artista desactiva el sabotaje en salas de 4+, pero **no en
  3 jugadores**.
- El 75 % de preview es una decisión deliberada: mantiene intriga, pero también
  puede hacer que el artista sienta que la tele “no le hace caso” hasta el final.
- En 3 jugadores hay 5 opciones; cada votante no puede votar su mentira, así que
  ve 4 opciones (25 % al azar), mejor que el 50/50 anterior.
- El dibujo se ve en la tele; no hay streaming WebSocket ni edición colaborativa.
- El título real está entre las opciones en `vote`; eso es parte del juego, no
  una filtración.

### 3.6 Subasta Ciega

**Idea:** cartas de botín, pujas secretas y martillo.

#### Fases

```
countdown 3 s
→ auction 15 s
→ auction_reveal 12 s (o antes si todos pulsan Continuar)
→ siguiente lote
```

- 4 lotes.
- Cada jugador empieza con **1,000 monedas**.
- Pujas en **múltiplos de 50**; el servidor redondea hacia abajo.
- El ganador paga su puja y recibe el efecto.
- Empate en la cima: los empatados se cancelan; gana la siguiente puja más alta.
- Si todos empatan o nadie puja, no hay ganador.
- El reveal acepta «Continuar» y el host puede avanzar cuando todos están listos;
  si no, avanza a los 12 s.

#### Efectos

| Efecto | Resultado |
|---|---|
| `points` | Suma puntos (300–1,000 según carta) |
| `coins` | Suma monedas (250–700) |
| `steal` | Roba 25 % de los puntos del líder, mínimo 250 y máximo 600 |
| `double_next` | Duplica el próximo lote de puntos |
| `shield` | Inmunidad permanente a robos |
| `mystery` | Elige al azar points/coins/steal/double_next/shield |

Al cerrar el último lote, **cada moneda sobrante vale 0.5 puntos**.

#### Notas para el experto

- La duración base es ~1:51 para 4 lotes; el catálogo dice ~9 min. Es una
  discrepancia grande: la subasta puede sentirse **demasiado corta** si no hay
  pausas humanas.
- La liquidación de bóveda castiga guardar monedas, pero 0.5 punto/moneda puede
  ser poco frente a un lote de 1,000 puntos.
- `shield` permanente puede volver a un jugador intocable para el resto.
- `double_next` no se acumula: si ganas dos, sigue siendo un booleano.
- El robo del 25 % con tope 600 evita destruir al líder, pero sigue siendo un
  mecanismo de catch-up/kingmaking.
- Pujas en múltiplos de 50 hacen los empates más frecuentes y tácticos.

### 3.7 Falso Conocimiento / Fibbage

**Idea:** un dato insólito; todos inventan una mentira y votan la verdad.

#### Fases

```
countdown 3 s
→ write 30 s
→ vote 22 s
→ reveal 5 s
```

- 4 rondas.
- Todos escriben una mentira; el servidor rechaza una que coincida con la verdad.
- Las mentiras duplicadas se deduplican.
- La verdad se mezcla con mentiras y **decoys de casa** hasta un mínimo de 5.
- No puedes votar tu propia mentira.
- Ronda final: **todos los puntos valen 2x**.
- Mentira con 3+ votos: **+300 extra**.
- Público separado; su favorito da +300 al autor.

#### Puntaje

| Situación | Puntos |
|---|---:|
| Votar la verdad | +1000 (x2 en ronda final) |
| Autor de mentira, por voto | +500 (x2 en ronda final) |
| Mentira con 3+ votos | +300 extra |
| Favorito del público | +300 |

#### Notas para el experto

- Es el juego con más “lectura” por ronda: 30 s de escritura y 22 s de voto.
- Con 8 jugadores hay hasta 8 mentiras + verdad + decoys; la pantalla puede
  saturarse y el voto puede volverse aleatorio.
- Los 30 datos son finitos: ~7–8 partidas de 4 rondas y se repiten.
- Algunos datos son conocidos o ambiguos; falta verificación con fuente.
- No hay ronda de desempate ni “apuesta de confianza”.

---

## 4. Tiempos reales y tiempos muertos

### Duración base por partida

Cálculo con timers del servidor; no incluye lobby, espera de “listos” ni
transiciones humanas.

| Juego | 3 jugadores | 5 jugadores | 8 jugadores | Catálogo dice |
|---|---:|---:|---:|---:|
| El Impostor (3 rondas) | 5:48 | 7:03 | 8:03 | 10 min |
| Impostor con guess en todas | 6:48 | 8:03 | 9:03 | 10 min |
| Dibujo (3 rondas) | 5:15 | 5:15 | 5:15 | 12 min |
| Subasta (4 lotes) | 1:51 | 1:51 | 1:51 | 9 min |
| Fibbage (4 rondas) | 3:51 | 3:51 | 3:51 | 10 min |
| Trivia (8 preguntas) | 3:23 | 3:23 | 3:23 | 9 min |
| Charadas (6 turnos) | 6:33 | 6:33 | 6:33 | 10 min |
| Apuesta (6 rondas, máximo) | 6:03 | 6:03 | 6:03 | 11 min |

**Hallazgo importante:** las duraciones del catálogo están muy por encima de la
suma de timers. Puede ser intencional (lobby, charla, reveals), pero un experto
debería decidir si son estimaciones honestas o si conviene ajustarlas.

### Tiempos muertos detectados

1. **Lobby sin autoarranque.** La tele espera a que el anfitrión pulse Iniciar;
   si el grupo se distrae, la pantalla queda estática.
2. **Reveal con “todos listos”.** En modo display, el reveal de Impostor,
   Dibujo, Fibbage y Trivia espera a todos; un jugador que no pulse bloquea al
   anfitrión hasta que se le marca desconectado a los 90 s.
3. **Impostor `questions`.** Si el hablante no habla, el turno se queda hasta 25 s
   (o 20/15). El botón de avanzar solo lo tiene el hablante.
4. **Dibujo `draw`.** Los no-artistas esperan 45 s con la tele en preview; el
   público no tiene una acción propia durante ese tramo.
5. **Fibbage `write`.** 30 s escribiendo una mentira; los jugadores rápidos
   esperan sin minijuego.
6. **Subasta `auction_reveal`.** 12 s de lectura; si nadie pulsa Continuar, se
   avanza solo. Es el juego con menos espera.
7. **Charadas.** El turno completo es de un actor; el resto solo mira.
8. **Sin tutorial.** La primera vez, las reglas dependen de la tele y la
   presentadora; no hay “cómo se juega” interactivo.

---

## 5. Economía y balance

### Subasta

- Monedas iniciales: **1,000**.
- Pujas: múltiplos de 50.
- 4 lotes.
- Efectos de puntos: 250–1,000.
- Robo: 25 % del líder, 250–600.
- Monedas: 250–700.
- Liquidación final: 0.5 pts por moneda.
- Escudo: inmunidad permanente.
- Doble: solo un próximo lote, no acumulable.

Preguntas de balance:

- ¿Un lote de 1,000 puntos vale más que guardar 1,000 monedas (500 pts)?
- ¿El escudo permanente es demasiado fuerte en 4 rondas?
- ¿El robo al líder castiga siempre al mismo y desincentiva ir primero?
- ¿Los empates en múltiplos de 50 son la interacción deseada o una lotería?
- ¿La liquidación de bóveda debería ser 0.5 o 0.25/0.75?
- ¿4 lotes son suficientes? La partida base dura menos de 2 minutos.

### Impostor

- Crew: 1,000 + 300 si falla el guess.
- Impostor atrapado que acierta: 800.
- Impostor que escapa: 1,500.
- 8 opciones de guess.
- Empate manda a guess.

Preguntas:

- ¿800 es suficiente castigo por ser atrapado?
- ¿El crew debe recibir 1,300 si el impostor falla, o es doble premio?
- ¿El impostor debe poder apostar puntos en su guess?
- ¿La ventaja de información del último hablante se compensa con algo?

### Dibujo

- Voto real: 1,000.
- Mentira: 500 por voto.
- Artista: 600 por voto real.
- Penalización: −500 si 0 aciertos en 4+.
- Sweep: +300 si 3+ votos.
- Público: +300 al favorito.

Preguntas:

- ¿El artista debe cobrar 600 por cada voto o un fijo por ronda?
- ¿La penalización de −500 es suficiente para evitar sabotaje en 4+?
- En 3 jugadores no hay penalización; ¿se puede explotar?
- ¿El público debe poder votar al artista o solo al título?

### Fibbage

- Verdad: 1,000.
- Mentira: 500 por voto.
- Sweep: +300.
- Ronda final 2x.
- Público: +300.

Preguntas:

- ¿La ronda final 2x es suficiente para remontar?
- ¿La mentira que engaña a 3+ debería pagar más que +300?
- ¿Votar la verdad debería dar menos si es demasiado obvia?
- ¿Falta una penalización por votar una mentira absurda?

---

## 6. Transiciones, arte y audio

### Transiciones de fase

- Countdown: 3 s.
- Reveal: 5 s.
- Subasta reveal: 12 s o todos listos.
- Cambio de ronda: directo, sin pantalla intermedia.
- Cambio de juego: vuelve al selector.
- Podio: medallas + tabla.

### Lenguaje visual

- Paleta Vision+: azul `#2D7DF6`, negro profundo, acentos por juego.
- 7 portadas 16:9.
- 15 assets de salón:
  - 5 cartas de subasta 512×768.
  - 2 roles 640×360.
  - 5 medallas 256×256.
  - 3 fondos TV 1280×720.
- Fondos de TV a `alpha = 0.28`.
- Martillo vectorial con animación spring en el reveal de subasta.
- Selector de TV: rejilla 4×2 con las 7 tarjetas visibles.
- Dibujo: el arte se recorta (`clipToBounds`), preview del 75 %, sin invadir
  la barra superior.

### Audio

- 48 voces nuevas Qwen3-TTS (16 momentos × 3).
- 248 audios totales.
- 6 SFX: tick, vote, secret, reveal, hammer, whoosh.
- Ducking de SFX al 22 % mientras habla la narradora.
- Voz solo en la tele; el teléfono muestra bocadillo.
- Momentos nuevos: preguntas del impostor, escape, timeout, empate de subasta,
  barrida de mentiras, nadie acertó, ronda final.
- Pendiente: tutorial, remontada, podio por juego y más variantes.

---

## 7. Arquitectura técnica

### Clientes

- **Android TV:** Jetpack Compose for TV; selector, lobby, fases, podio.
- **Teléfono:** Compose; lienzo táctil, campos de texto, votos, pujas.
- **Launcher:** Vision+ Home 1.3.13, **sin Juegos**; los juegos solo viven en la
  app.
- **Entrada a sala:** QR (`visionplus://play/join?code=`) o código de 6
  caracteres.
- **Teléfono:** solo se une; no crea salas.

### Servidor

- FastAPI + estado en memoria.
- Rutas `/v1/game/*`.
- `POST /action` con:
  - `impostor_vote`, `impostor_advance`, `impostor_guess`
  - `draw_pick_prompt`, `draw_partial`, `draw_submit`, `draw_title`, `draw_vote`
  - `auction_bid`
  - `fibbage_lie`, `fibbage_vote`
- `phaseDuration` en el snapshot.
- Idempotencia 1.5 s.
- Rate limit 30/min; `draw_partial` exento y limitado a 1.8 s en servidor.
- Trazos como vectores, máximo 160/2,400.
- Secretos filtrados por rol.
- Resultados a Postgres al cerrar; la sala en memoria no sobrevive reinicios.

### Producción

- VPS: `streaming-api` reconstruido, catálogo de 7 juegos.
- Smoke remoto: salas, `draw_partial`, `auction_bid` y `fibbage_lie`.
- OTA **2.5.197** con motor X4K inyectado; la 2.5.196 se retiró por
  `ENGINE_LOAD_FAIL`.
- 542 backend, 1,136 Android, 170 launcher en verde.
- Reproducción verificada en Chromecast HD.

---

## 8. Riesgos y deuda

### Jugabilidad

- La subasta base es demasiado corta para su ficha de 9 minutos.
- El reveal con “todos listos” puede bloquear la partida.
- El Dibujo en 3 jugadores aún permite sabotaje sin penalización.
- El público no tiene una acción durante `draw`.
- El Impostor depende de que la mesa hable; no hay minijuego de apoyo.
- Fibbage tiene solo 30 datos y algunos son fáciles/ambiguos.

### Técnica

- Sin persistencia/reconexión de salas.
- Sin WebSocket: dibujo cada 2.5 s, no 60 fps.
- Sin tope de espectadores en el código; posible carga si entra mucha gente.
- Sin prueba física completa con 2–3 teléfonos.
- Sin tutorial interactivo.
- Sin localización ni subtítulos.

### Visual/audio

- 3 variantes por momento nuevo; se nota la repetición.
- Una sola voz para todos los juegos.
- SFX sintéticos simples, sin capas.
- Faltan iconos de herramientas y arte 1:1 de teléfono.
- El arte de subasta tiene microtexto generado; a tamaño de uso no se lee, pero
  no es ideal.

---

## 9. Preguntas para el experto

1. ¿Qué juego tiene mejor relación diversión/minuto y cuál se siente más largo?
2. ¿La subasta necesita más lotes, más monedas o una economía con intereses?
3. ¿El escudo permanente y el robo del 25 % crean un líder imparable?
4. ¿El Dibujo debe castigar al artista en 3 jugadores?
5. ¿El público debe tener una acción durante `draw`?
6. ¿El Impostor necesita preguntas dirigidas más cortas o un “pase”?
7. ¿Qué tres cambios de ritmo eliminan los tiempos muertos sin añadir reglas?
8. ¿Qué parte del catálogo de duración es honesta y qué parte es marketing?
9. ¿Qué assets faltan para que se sienta “juego de salón” y no “web”?
10. ¿Qué 20 líneas de diálogo y qué tutorial hacen falta para una primera partida?

---

## 10. Archivos de referencia

- `docs/ENTREGA_GAMES_FASE4.md`
- `docs/HANDOFF_JUEGOS_FASE4_ANALISIS.md`
- `docs/RUN_FASE4_2026-10-30.md`
- `ota_notes_2.5.197.md`
- `docs/assets/games/salon_contact_sheet.png`
- `docs/assets/games/assets_contact_sheet.png`
