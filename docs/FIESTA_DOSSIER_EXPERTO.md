# Vision+ Fiesta — dossier completo para análisis de diseño

**Producto:** Vision+ Fiesta (party pack para Android TV + teléfonos)  
**Fecha del corte:** 2026-11-02  
**Estado:** implementado en el árbol de trabajo. **Sin OTA y sin deploy del backend de Fiesta.**  
**Uso:** entregar este documento (y `docs/fiesta_dossier_data.json`) a un experto en juegos de fiesta tipo Jackbox para auditar mecánicas, economía, variables, contenido, voz y progresión.

> **Advertencia de honestidad:** este dossier separa lo **implementado** de lo **diseñado pero no activo**. Hay fases y duraciones reservadas (`duel`, `sort`, `chat`, `remix`, `invest`) que todavía no se ejecutan. No las cuentes como mecánicas terminadas.

---

## 0. Resumen ejecutivo

- **6 juegos**: La Última Palabra, Vox Populi, Fuera de Época, Texto Roto, Franela Fest y El Tasador.
- **Cada juego tiene su plató de tele, su control de teléfono, su presentador y su paleta.**
- **170 clips de voz Qwen3-TTS**: 5 tomas × 34 momentos, con una referencia clonada por presentador.
- **6 bucles de música + 6 stingers** originales.
- **Arte IA**: 6 platós, 6 presentadores, 6 teléfonos, 6 pósters de selección y 1 fondo de lobby. **31 assets visuales** en el APK, **19 maestros aprobados**.
- **Economía**: monedas propias por juego, apuestas, soplos, burlas, tienda y una ruleta entre rondas.
- **Contenido**: 246 piezas de contenido base (prompts, encuestas, eventos, escenas, camisetas y objetos) + 170 líneas de presentador + 48 líneas de banter + 26 burlas.
- **Calidad técnica**: 661 tests backend, suite completa Android en verde, release R8 sin colisiones.
- **Problema principal para el experto**: cinco de los seis juegos siguen compartiendo el esqueleto **escribir → votar → reveal**. Solo Vox Populi y Fuera de Época se resuelven sin voto. La diferenciación hoy es sobre todo de **presentación, voz, arte y economía**, no de **bucle de juego**.

---

## 1. Arquitectura y flujo de sala

```text
TV (display)                         Servidor FastAPI                 Teléfonos
────────────                         ───────────────                 ─────────
Crea sala con código/QR   ───────►   Room (autoridad)   ◄───────────  Se unen por código
Muestra lobby             ◄───────   snapshot público  ───────────►  Lobby + host
Muestra plató             ◄───────   fase + timer      ───────────►  Control del juego
Muestra reveal/podio      ◄───────   resultados        ───────────►  Resultado + tienda
```

- **Autoridad:** el servidor decide fases, timers, puntos, monedas, apuestas, soplos, burlas y ganadores.
- **Transporte:** polling HTTP (~700 ms en fases activas, 1 s en el resto). **No hay WebSocket/SSE.**
- **Sala:** hasta 8 jugadores; 8 espectadores separados que no reciben secretos; TTL 4 h.
- **Mínimos:** 1 en modo prueba; 2–3 según el juego en modo normal.
- **El primer teléfono que entra** es el anfitrión y el único que inicia/avanza.
- **La tele no juega:** muestra, narra y ambienta.
- **Modo familiar / sin filtro:** el servidor filtra groserías y odio; el modo adulto permite lenguaje adulto, nunca acoso.

### 1.1 Ciclo macro

```text
lobby → countdown → ronda 1 → reveal → [ruleta si toca] → ronda 2 → …
      → última ronda (x2) → podio
```

- La ruleta aparece **cada 2 rondas** si el juego tiene **6 o más rondas**.
- La última ronda paga **x2** por `_multiplier()`.
- El reveal avanza solo tras 9 s (o al pulsar continuar, según flujo).
- El podio ordena por puntos; no hay desempate explícito más allá del orden de entrada.

### 1.2 Transiciones de fase reales

| Juego | Secuencia real | Duración |
|---|---|---|
| La Última Palabra | lobby → countdown → `write` → `vote` → `reveal` | 55 s / 30 s / 9 s |
| Vox Populi | lobby → countdown → `guess` → `reveal` | 95 s / 9 s |
| Fuera de Época | lobby → countdown → `choice`/`guess` → `reveal` | 55 s / 9 s |
| Texto Roto | lobby → countdown → `write` → `vote` → `reveal` | 30 s / 30 s / 9 s |
| Franela Fest | lobby → countdown → `create`/`draw` → `vote` → `reveal` | 70 s / 35 s / 9 s |
| El Tasador | lobby → countdown → `pitch` → `vote` → `reveal` | 60 s / 30 s / 9 s |

**Pantallas que recibe el cliente (`fiesta.screen`):**

| Fase servidor | Pantalla | Juego |
|---|---|---|
| `lobby` | `lobby` | todos |
| `countdown` | `countdown` | todos |
| `write` / `guess` / `pitch` | `text_input` | La Última Palabra, Vox, Texto, Tasador |
| `guess` | `choice` | Fuera de Época |
| `create` | `draw` | Franela Fest |
| `vote` | `vote` | La Última Palabra, Texto, Franela, Tasador |
| `reveal` | `reveal` | todos |
| `bonus` | `bonus` | todos |
| `bonus_reveal` | `bonus_reveal` | todos |
| `podium` | `podium` | todos |
| otro | `wait` | todos |

**Avance automático:**

- `write`/`guess`/`create`/`pitch`: cuando **todos** enviaron o se acaba el timer.
- `vote`: cuando **todos** votaron o se acaba el timer.
- `reveal`: al acabar el timer.
- `bonus`: cuando todos eligen cofre o se acaba el timer.
- `bonus_reveal`: al acabar el timer.
- `podium`: no avanza solo.

**Fases reservadas no activas** (están en `DURATIONS` pero el motor no las usa):

```text
ultima_palabra: duel (18 s)
fuera_de_epoca: sort (55 s)
texto_roto: chat (30 s) y react (30 s)
franela_fest: remix (40 s)
el_tasador: invest (30 s)
```

Son el diseño de una segunda capa de mecánicas (duelos, ordenar, chat por turnos, remix, inversión). **No están implementadas.** El experto debe saberlo para no evaluarlas como si existieran.

### 1.3 Transiciones visuales / animaciones

Implementado:

- Fondo animado por juego: partículas, scanlines en tiempo/chat y deriva suave.
- Cinta de banter con desplazamiento.
- Timer circular con arco y cambio a rojo en los últimos 5 s.
- Aplausómetro animado en La Última Palabra.
- “Escribiendo…” con puntos animados en Texto Roto.
- Burbujas de burlas flotando sobre el plató.
- Tarjetas del selector con `AnimatedVisibility` (fade + scale, escalonado).
- Línea del presentador con `AnimatedContent`.
- `LocalReducedEffects` apaga animaciones en equipos modestos.

**No implementado en Fiesta:**

- `GamePhaseTransition` maestro entre fases (existe en los juegos legacy, pero no envuelve los seis mundos de Fiesta).
- Transición cinematográfica de ronda a ronda; el contenido cambia con un `when`.
- Transición de teléfono entre control y reveal; también es un `when`.

Esto es una deuda clara de “sensación de producción”.

---

## 2. Los seis juegos

### 2.1 La Última Palabra

**Fantasy:** concurso de ingenio nocturno; la tele es un estudio, el teléfono es una libreta.

- **Jugadores:** 3–8. **Rondas:** 12. **Duración declarada:** 24 min.
- **TV:** estudio naranja, tarjetas A/B, aplausómetro, focos, público en silueta.
- **Teléfono:** libreta de Nora, hoja de papel, contador y medidor de “promesa”.
- **Fase activa:** todos escriben una respuesta a la misma frase.
- **Voto:** se muestran todas las respuestas; nadie puede votar la suya.
- **Puntuación:** 100 × votos recibidos × multiplicador; +300 × multiplicador si es la más votada.
- **Contenido:** 87 prompts (10 adultos) = 77 familiares.
- **Host:** Nora Vela. **Moneda:** Risas. **Tienda:** La Trastienda de Nora.
- **Fortaleza:** el más directo y el que mejor funciona con 8; la risa social es inmediata.
- **Riesgo:** no hay enfrentamiento A/B real; todas las respuestas se ven en una lista. La ronda final es solo x2, no un formato nuevo. El voto se dispersa con muchas respuestas.

### 2.2 Vox Populi

**Fantasy:** noticiero de encuestas donde la mayoría siempre tiene la razón.

- **Jugadores:** 2–8. **Rondas:** 12. **Duración declarada:** 22 min.
- **TV:** noticiero cian, gráfica de barras, cintillo, índice de vergüenza, ranking.
- **Teléfono:** boleta de encuesta, sello de ciudadano anónimo, urna.
- **Fase activa:** todos escriben la respuesta que creen más popular.
- **Resolución:** el servidor compara con el top de la encuesta; no hay voto.
- **Puntuación:** 700, 600, 500, 400, 300, 200 según ranking (mínimo 100) × multiplicador.
- **Contenido:** 28 encuestas × 6 respuestas = 168 respuestas.
- **Host:** Don Chisme. **Moneda:** Rating. **Tienda:** La Mesa de Control.
- **Fortaleza:** es el juego más distinto del paquete: no hay voto y se resuelve por ranking.
- **Riesgo:** 28 encuestas es poco para 12 rondas; el matching es generoso por subcadena y puede aceptar respuestas imprecisas; no hay turnos ni “robo” ni HILO/DARES reales.

### 2.3 Fuera de Época

**Fantasy:** el profesor Tic Tac pone a prueba la memoria histórica.

- **Jugadores:** 2–8. **Rondas:** 12. **Duración declarada:** 22 min.
- **TV:** consola temporal violeta, dial, anillos, portal, fichas de año.
- **Teléfono:** consola con cuatro teclas de año y palanca.
- **Fase activa:** elegir uno de cuatro años para un evento.
- **Resolución:** el servidor revela el año correcto y calcula racha.
- **Puntuación:** 500 + 100 × (racha−1) si es correcto; 200 si está a ±3 años; 0 si falla; × multiplicador.
- **Contenido:** 46 eventos (6 adultos) = 40 familiares.
- **Host:** Profesor Tic Tac. **Moneda:** Cronones. **Tienda:** Chatarra Temporal.
- **Fortaleza:** el más rápido de entender; la racha da tensión.
- **Riesgo:** es un multiple choice clásico, no una línea temporal; 46 eventos se agotan; los distractores son offsets fijos (−12, −6, +7, +13) y pueden hacer la respuesta obvia.

### 2.4 Texto Roto

**Fantasy:** un chat que empieza bien y todos arruinan.

- **Jugadores:** 3–8. **Rondas:** 8. **Duración declarada:** 26 min.
- **TV:** ventana de chat a pantalla completa, burbujas, roles, “escribiendo…”, miembros en línea.
- **Teléfono:** chat con tu papel, campo de mensaje, respuestas rápidas.
- **Fase activa:** cada jugador escribe una línea para su personaje.
- **Voto:** se arma el chat y se vota la línea más graciosa.
- **Puntuación:** 100 + 300 × votos recibidos; +200 si es la más votada; × multiplicador.
- **Contenido:** 30 escenas (5 adultos) = 25 familiares.
- **Host:** La Máquina. **Moneda:** Datos. **Tienda:** Modo Dios.
- **Fortaleza:** el tema del chat y los roles dan mucho juego; el arte de burbujas ayuda.
- **Riesgo:** no es un chat en vivo: se escribe en paralelo 30 s, se ordena por orden de envío y luego se vota. No hay reacciones, interrupciones ni objetivos secretos. El orden de las líneas puede arruinar el chiste.

### 2.5 Franela Fest

**Fantasy:** pasarela de moda donde el mal gusto compite con el arte.

- **Jugadores:** 3–8. **Rondas:** 6. **Duración declarada:** 28 min.
- **TV:** pasarela rosa/dorada, maniquíes, flashes, jurado, tarjeta de crítica.
- **Teléfono:** taller con camiseta-lienzo, paleta de seis colores, eslogan.
- **Fase activa:** dibujar una camiseta y escribir un eslogan.
- **Voto:** anónimo; se vota el diseño.
- **Puntuación:** 200 + 300 × votos recibidos; +300 si es el más votado; × multiplicador.
- **Contenido:** 25 prompts de camiseta (5 adultos) = 20 familiares.
- **Host:** El Crítico. **Moneda:** Hilos. **Tienda:** La Boutique del Mal Gusto.
- **Fortaleza:** el dibujo es el gesto más expresivo del paquete; el arte de pasarela vende la fantasía.
- **Riesgo:** no hay remix ni colaboración (Tee K.O. usa dos fases); 25 prompts es poco para 6 rondas; dibujar en 70 s en un teléfono pequeño puede ser frustrante.

### 2.6 El Tasador

**Fantasy:** casa de subastas de objetos malditos; todo tiene un precio.

- **Jugadores:** 2–8. **Rondas:** 10. **Duración declarada:** 26 min.
- **TV:** cortinas, lote, martillo, etiquetas de precio, público con paletas.
- **Teléfono:** paleta de subasta, pitch, teclado de precio.
- **Fase activa:** todos escriben un pitch y un valor para el mismo objeto.
- **Voto:** se vota el pitch más convincente.
- **Puntuación:** 100 + 300 × votos; +200 si es el más votado; +300 si el valor es el más cercano al real; × multiplicador.
- **Contenido:** 30 objetos (5 adultos) = 25 familiares.
- **Host:** El Tasador. **Moneda:** Fichas. **Tienda:** La Bodega de Objetos Malditos.
- **Fortaleza:** el reveal del valor real y el “más cercano” dan un segundo eje de puntuación.
- **Riesgo:** no es una subasta: todos pitchan el mismo objeto y votan; no hay pujas, presupuesto, lotes distintos ni riesgo económico. La fantasía promete más de lo que el bucle entrega.

---

## 3. Economía y variables

### 3.1 Monedas

- **Inicio:** 400 monedas por jugador.
- **Fuente principal:** 1 moneda por cada 100 puntos ganados (mínimo 1 si hubo puntos), multiplicada por el giro “Lluvia de monedas”.
- **Otras fuentes:** apuestas ganadas, cofres de la ruleta.
- **Gasto:** apuestas, soplos, burlas y tienda.
- **Las monedas no dan victoria directa:** compran ventajas para la siguiente ronda.

### 3.2 Acciones económicas

| Acción | Coste | Timing | Efecto | Límite |
|---|---:|---|---|---|
| Apuesta 100/250/500 | 100/250/500 | Fase activa | Si sumas puntos: x2; si eres líder: x3; si no sumas: pierdes | 1 por ronda; no puedes apostar más de lo que tienes |
| Soplo | 120 | Fase activa | Pista; Fuera de Época apaga 2 años; El Tasador da rango de valor | 1 por ronda |
| Burla | 60 | Cualquier fase activa/reveal | Sticker validado que vuela sobre la tele | 1 por ronda; solo frases de la lista |
| Doble puntos | 300 | Reveal | Tus puntos de la siguiente ronda valen x2 | 1 compra por ronda |
| Robo de bolsillo | 250 | Reveal | Le quitas 200 al líder al cerrar la siguiente ronda | 1 compra por ronda |
| Seguro de fiesta | 150 | Reveal | Si no sumas nada, cobras 300 | 1 compra por ronda |

### 3.3 Ruleta de Fiesta

- Aparece cada 2 rondas si el juego tiene 6+ rondas.
- Tres cofres, pago oculto:

| Probabilidad | Monedas | Puntos | Etiqueta |
|---:|---:|---:|---|
| 12 % | 500 | 200 | Jackpot |
| 30 % | 250 | 0 | Buen botín |
| 36 % | 120 | 100 | Cofre sorpresa |
| 22 % | 40 | 0 | Cofre vacío |

- **Valor esperado:** ~187 monedas y ~60 puntos por jugador y ruleta.
- Con 12 rondas hay 5–6 ruletas: ~935–1,122 monedas extra por jugador. Esto puede inflar la economía y hacer que la tienda sea trivial en la segunda mitad.

### 3.4 Giros por ronda

Cada juego tiene 6 giros con nombres y descripciones propias. Los efectos son compartidos:

| Efecto | Qué hace |
|---|---|
| `clean` | Ronda normal |
| `double` | Multiplicador x2 y monedas x2 |
| `rush` | Reloj ×0.68 y puntos ×1.5 |
| `golden` | Monedas x2 |
| `sudden` | +300 al primero que envía |
| `comeback` | +300 al último al empezar la ronda |

**Ejemplos de naming:**

- La Última Palabra: “Doble risa”, “Sin respiro”, “Lluvia de propinas”, “El primero que escribe”, “El último se levanta”.
- Vox Populi: “Rating doble”, “Última hora”, “Patrocinado”, “Primicia exclusiva”, “Reportaje de rescate”.
- Fuera de Época: “Paradoja doble”, “Salto inminente”, “Relámpago temporal”, “El primer viajero”, “Abuelo paradoja”.
- Texto Roto: “Doble mensaje”, “Modo turbo”, “Viral”, “Primer mensaje”, “Último en línea”.
- Franela Fest: “Temporada alta”, “Cierre de pasarela”, “Edición limitada”, “Primer boceto”, “Colección de rescate”.
- El Tasador: “Puja doble”, “Martillo rápido”, “Comisión oculta”, “Primera puja”, “Lote de rescate”.

### 3.5 Variables que cambian una partida

- Modo familiar vs sin filtro.
- Modo prueba de 1 jugador.
- Número de rondas.
- Giro de cada ronda.
- Apuesta de cada jugador.
- Soplo de cada jugador.
- Burla de cada jugador.
- Tienda comprada.
- Ruleta cada 2 rondas.
- Ronda final x2.
- Racha en Fuera de Época.
- Primer/último en enviar en giros `sudden`/`comeback`.
- Puntos y monedas previos (remontada, robo).

### 3.6 Análisis económico preliminar (para el experto)

- **Apuesta:** en juegos de voto casi todos suman algo, así que apostar puede ser casi gratis; en Vox/Fuera es más arriesgado. No hay datos de win rate por juego.
- **Soplo:** 120 monedas es caro para una pista textual en La Última Palabra/Texto/Franela, pero barato para eliminar dos años o dar un rango de valor. El coste no está calibrado por valor real.
- **Burla:** 60 monedas por algo cosmético; es una decisión de “irreverencia” más que estratégica. Puede ser una trampa económica.
- **Tienda:** “Doble puntos” a 300 puede ser dominante si se compra al final; “Seguro” a 150 es débil si el jugador suele sumar; “Robo” castiga al líder y puede ser contraproducente si el líder es aliado.
- **Ruleta:** ~187 monedas esperadas cada 2 rondas es mucho frente a 400 iniciales. La economía puede desbordarse y perder tensión.
- **Sin sumidero final:** las monedas no se convierten en victoria ni en apuesta final; sobran al final.

---

## 4. Voz, diálogos y variedad

### 4.1 Casting

| Host | Juego | Descripción |
|---|---|---|
| Nora Vela | La Última Palabra | 31, late-night, cálida, pícara, ligeramente ronca |
| Don Chisme | Vox Populi | 46, radio de chismes, cercano, exagerado, metiche |
| Profesor Tic Tac | Fuera de Época | 58, documental, nasal, pausado, pedante |
| La Máquina | Texto Roto | IA andrógina, deadpan, glitches, inquietante |
| El Crítico | Franela Fest | 36, moda snob, teatral, afeminado, dramático |
| El Tasador | El Tasador | 62, anticuario, grave, lento, misterioso |

### 4.2 Pipeline Qwen3-TTS

1. **VoiceDesign** genera 5 tomas por momento con una descripción de personaje + dirección actoral.
2. **Selección de referencia** por host (`tools/select_fiesta_voice_refs.py`).
3. **Base clone** de las 170 líneas (`tools/generate_fiesta_voice_clone.py`) para que cada juego tenga una sola voz consistente.
4. **QC de f0** (`tools/qwen_voice_qc.py`): ±3 semitonos respecto al presentador; repara outliers.
5. **Loudness:** −16 LUFS, techo −2.5 dB, AAC 64 kbps, 48 kHz mono, 0.18 s de silencio inicial.

**Resultado:** 170/170 tomas pasan QC.

### 4.3 Inventario de diálogo

| Juego | Momentos | Líneas de host | Banter | Burlas |
|---|---:|---:|---:|---:|
| La Última Palabra | 6 | 30 | 9 | 5 |
| Vox Populi | 5 | 25 | 6 | 4 |
| Fuera de Época | 5 | 25 | 6 | 4 |
| Texto Roto | 6 | 30 | 9 | 5 |
| Franela Fest | 6 | 30 | 9 | 4 |
| El Tasador | 6 | 30 | 9 | 4 |
| **Total** | **34** | **170** | **48** | **26** |

- Cada momento tiene **5 variantes de texto y 5 tomas de audio**.
- El servidor elige variante por sala/ronda/fase, evita repetir la anterior y manda `hostCue.audio`; el cliente reproduce exactamente el texto visible.
- **Banter:** 3 frases por fase activa/reveal, mostradas en el cintillo.
- **Burlas:** frases cortas predefinidas, validadas en servidor.
- **Contenido base:** 246 piezas (87 + 28 + 46 + 30 + 25 + 30).

### 4.4 Variedad real vs percibida

- **A favor:** 5 tomas por momento evitan la repetición inmediata; 6 voces distintas; 48 líneas de banter; 26 burlas; 6 giros por juego.
- **En contra:** 170 líneas es poco para sesiones largas; las mismas 5 tomas vuelven tras 5 partidas del mismo juego. El banter se repite cada 3 frases por fase. Las burlas son solo 4–5 por juego.
- **Riesgo de TTS:** puede haber errores de pronunciación, énfasis o ritmo; el QC mide f0/duración/clipping, no naturalidad percibida.

### 4.5 Música y SFX

- 6 bucles originales de 24–32 s, uno por juego.
- 6 stingers de reveal de 2–3 s.
- SFX compartidos: tick, voto, secreto, reveal, martillo, whoosh, buzzer, glass, heartbeat, safe, fanfare.
- La música baja cuando habla el presentador (`ducking`).

---

## 5. Arte y UI

### 5.1 Inventario

| Tipo | Cantidad | Tamaño | Archivo |
|---|---:|---|---|
| Plató de tele | 6 | 1920×1080 | `fiesta_stage_<juego>.webp` |
| Presentador | 6 | 900×900 | `fiesta_host_<juego>.webp` |
| Fondo de teléfono | 6 | 1080×1920 | `fiesta_phone_<juego>.webp` |
| Póster de selección | 6 | 900×1200 | `game_<juego>_poster.webp` |
| Fondo de lobby | 1 | 1920×1080 | `fiesta_lobby_bg.webp` |
| Tarjeta horizontal | 6 | 1280×720 | `game_<juego>_art.webp` |

- **Pipeline:** ComfyUI + SDXL Juggernaut XL v9 local.
- **Maestros aprobados:** 19 en `docs/assets/games/v3/premium/approved/`.
- **Filtro:** `tools/fiesta_quality_gate.py` (resolución, peso, entropía, bordes, colorfulness, audio).
- **Resultado:** 213/213 assets instalados; 19/19 maestros IA.

### 5.2 Selector / lobby

- Fondo de salón de fiesta IA.
- Tarjetas verticales con el presentador y la utilería.
- En tele: tarjetas full-bleed, foco con borde, datos de jugadores/duración, línea del host.
- En teléfono: tarjetas horizontales con arte de plató, descripción, pasos y botón de unirse/crear.

### 5.3 UI de tele por juego

| Juego | Composición |
|---|---|
| La Última Palabra | marquesina, tarjetas A/B, aplausómetro, marcador, host |
| Vox Populi | noticiero, gráfica de barras, cintillo, índice, host |
| Fuera de Época | consola, dial, cuatro fichas de año, línea temporal, host |
| Texto Roto | ventana de chat, burbujas, miembros, host |
| Franela Fest | pasarela, camisetas, jurado, marcador, host |
| El Tasador | lote, pitchs, martillo, valor real, host |

### 5.4 UI de teléfono por juego

| Juego | Control |
|---|---|
| La Última Palabra | libreta, campo de texto, medidor, voto A/B |
| Vox Populi | boleta, campo de una línea, urna |
| Fuera de Época | cuatro teclas de año, palanca |
| Texto Roto | chat, rol, compositor, respuestas rápidas |
| Franela Fest | lienzo de camiseta, paleta, eslogan |
| El Tasador | lote, pitch, teclado de precio, paleta |

**Riesgo visual:** las animaciones son de Compose, no videos; no hay transición maestra entre fases; el arte IA puede tener pequeños textos/artefactos en pantallas o etiquetas (se revisó a ojo, el filtro no detecta texto).

---

## 6. Contenido completo

El volcado **íntegro** está en:

```text
docs/fiesta_dossier_data.json
```

Incluye, para cada juego:

- información pública;
- fases y duraciones;
- fórmula de puntuación;
- contenido completo (prompts, encuestas, eventos, escenas, camisetas, objetos);
- líneas de host por momento;
- banter;
- burlas;
- giros;
- tienda.

### 6.1 Conteos

| Juego | Contenido | Familiar | Adulto |
|---|---:|---:|---:|
| La Última Palabra | 87 prompts | 77 | 10 |
| Vox Populi | 28 encuestas / 168 respuestas | 28 | 0 |
| Fuera de Época | 46 eventos | 40 | 6 |
| Texto Roto | 30 escenas | 25 | 5 |
| Franela Fest | 25 camisetas | 20 | 5 |
| El Tasador | 30 objetos | 25 | 5 |

### 6.2 Muestras de tono

- **La Última Palabra:** “El nuevo servicio de streaming para mascotas incluye…”
- **Vox Populi:** “¿Cuál es la mejor excusa para no salir el viernes?”
- **Fuera de Época:** eventos históricos/culturales con año oculto.
- **Texto Roto:** escenas con roles y una semilla de conversación.
- **Franela Fest:** prompts de camiseta absurda.
- **El Tasador:** objetos raros con descripción y valor oculto.

El experto puede analizar el JSON para detectar repetición, sesgo, calidad de remate, adecuación familiar y variedad temática.

---

## 7. Minijuegos y capas meta

### 7.1 Minijuego real

**La Ruleta de Fiesta** es el único minijuego transversal:

- 3 cofres.
- 4 resultados.
- Aparece cada 2 rondas en partidas de 6+ rondas.
- Pago en monedas/puntos.
- No hay minijuego por juego.

### 7.2 Capas meta

- Monedas persistentes dentro de la partida.
- Tienda entre rondas.
- Apuestas.
- Soplos.
- Burlas.
- Giros.
- Podio.
- No hay progresión entre partidas, desbloqueos, logros, medallas ni perfil persistente en Fiesta.

---

## 8. Diagnóstico de calidad para el experto

### 8.1 Fortalezas

1. **Identidad visual y sonora fuerte:** cada juego tiene plató, control y voz propios.
2. **Servidor autoritativo:** reglas, secretos y economía no dependen del cliente.
3. **Economía visible:** apuestas, soplos y burlas dan decisiones durante la partida.
4. **Contenido original:** no se copió Jackbox; hay 246 piezas y 170 líneas de host.
5. **Pipeline reproducible:** arte IA, voz Qwen y filtro de calidad documentados.
6. **Accesibilidad:** modo familiar, modo prueba, timers visibles, foco TV.

### 8.2 Debilidades críticas

1. **Cinco juegos comparten el mismo bucle:** escribir → votar → reveal. La diferencia es de piel, no de dinámica.
2. **No hay turnos ni equipos:** todos juegan en paralelo; no hay tensión de “a quién le toca”.
3. **No hay audiencia interactiva:** los espectadores existen pero no votan ni afectan.
4. **No hay minijuegos por juego:** la ruleta es la única variación.
5. **La economía puede romperse:** ruleta muy generosa, tienda no calibrada, burla dominada.
6. **Poco contenido para sesiones largas:** 25–87 piezas por juego; 12 rondas agotan Vox/Fuera/Franela.
7. **Sin ronda final distinta:** solo x2; no hay “Thriplash”, “final Fibbage” ni evento culminante.
8. **Transiciones de fase planas:** no hay `GamePhaseTransition` en Fiesta.
9. **El chat no es en vivo:** polling y escritura paralela; el orden de líneas es arbitrario.
10. **El Tasador no es una subasta:** es un voto con precio; la fantasía está por delante de la mecánica.
11. **La voz puede repetirse:** 5 tomas por momento se agotan en una sesión larga.
12. **Sin localización:** todo en español; sin textos alternativos.

### 8.3 Preguntas para el experto

1. ¿Qué juego debería convertirse primero en un bucle realmente distinto?
2. ¿La economía debe dar ventaja competitiva o solo ser cosmética/social?
3. ¿Cuántas monedas debería dar la ruleta para no trivializar la tienda?
4. ¿La apuesta debe ser secreta y simultánea? ¿Debe haber contraapuesta?
5. ¿Conviene un modo por equipos? ¿En cuáles?
6. ¿La audiencia debe votar? ¿Cómo evitar que opaque a los jugadores?
7. ¿Qué ronda final distinta necesita cada juego?
8. ¿Cuánto contenido es el mínimo para 20 partidas sin repetir?
9. ¿El tono “irreverente” debe subir o está bien así?
10. ¿Qué transición visual merece la pena producir en video y cuál en Compose?

---

## 9. Backlog priorizado (propuesta)

### P0 — Que los juegos se sientan distintos

1. **La Última Palabra:** enfrentamientos A/B reales (bracket), no lista única.
2. **Vox Populi:** cadena por turnos o HILO/robo, no todos escriben a la vez.
3. **Fuera de Época:** ordenar 3–4 eventos en una línea temporal, no un solo año.
4. **Texto Roto:** chat por turnos y reacciones, no escribir en paralelo.
5. **Franela Fest:** remix (dibujo de uno + eslogan de otro).
6. **El Tasador:** subasta con presupuesto y pujas, no voto.

### P1 — Economía

- Subir el coste de la ruleta o bajar su pago.
- Hacer que la burla dé algo (puntos sociales, reducción de voto, etc.).
- Calibrar apuestas por juego.
- Añadir un sumidero final (apuesta de podio, mejora de última ronda).

### P2 — Contenido y voz

- 200+ prompts por juego.
- 8–10 tomas por momento.
- 10–15 burlas por juego.
- Rondas finales con contenido propio.

### P3 — Producción

- `GamePhaseTransition` en Fiesta.
- Video intro de 2–3 s por juego.
- Tutorial por juego con arte propio.
- Más variedad de música/SFX.

---

## 10. Archivos y comandos

### Documentos

```text
docs/FIESTA_DOSSIER_EXPERTO.md      este documento
docs/fiesta_dossier_data.json       volcado completo para análisis
docs/FIESTA_IDENTIDAD_V3_2026-11-02.md
docs/FIESTA_REGRESSION_GUARD.md
docs/REGRESSIONS.md
```

### Código

```text
server/api/fiesta.py
server/api/fiesta_content.py
server/tests/test_fiesta.py
app/.../data/games/FiestaCatalog.kt
app/.../data/games/GameModels.kt
app/.../data/games/FiestaVoice.kt
app/.../ui/games/FiestaDesign.kt
app/.../ui/games/FiestaTvWorlds.kt
app/.../ui/games/FiestaPhoneWorlds.kt
```

### Assets

```text
app/src/main/res/drawable-nodpi/fiesta_*     31 visuales
app/src/main/res/raw/fiesta_*                170 voces + 6 música + 6 stingers
docs/assets/games/v3/premium/approved/       19 maestros IA
docs/assets/games/v3/voices/refs/            6 referencias Qwen
```

### Comandos

```bash
# tests
./gradlew :app:testNormalDebugUnitTest --offline --max-workers=1
.venv/bin/python -m pytest server/tests -q

# assets
python3 tools/fiesta_quality_gate.py
python3 tools/fiesta_quality_gate.py --premium

# exportar este dossier
.venv/bin/python tools/export_fiesta_dossier.py
```

---

## 11. Lo que el experto NO debe asumir

- No hay OTA publicada con estos cambios.
- No hay backend de Fiesta desplegado en el VPS.
- No hay prueba física con 2–3 teléfonos.
- No hay WebSocket.
- No hay modo equipos.
- No hay audiencia que vote.
- No hay minijuegos por juego.
- No hay progresión entre partidas.
- No hay tutorial propio por juego.
- Las fases `duel`, `sort`, `chat`, `remix` e `invest` **no están activas**.

Con este contexto, el análisis del experto puede separar “presentación de producción” de “diseño de bucle” y decirnos exactamente qué merece la pena construir después.
