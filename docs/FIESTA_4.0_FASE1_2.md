# Vision+ Fiesta 4.0 — Fase 1 y 2 (servidor)

**Fecha:** 2026-11-02  
**Estado:** implementado en `server/api/fiesta.py` y `server/api/fiesta_content.py`.  
**Pruebas:** **667 backend en verde**, tests de Fiesta y suite Android de Fiesta/GameWiring en verde.  
**Cierre:** Fases 3, 4 y 5 completadas. Ver `docs/FIESTA_4.0_COMPLETO.md`.

---

## 1. Bucles nuevos

### 1.1 La Última Palabra — duelos A/B

- `write` (55 s) asigna `ceil(jugadores/2)` prompts distintos; cada pareja recibe el suyo.
- Parejas 1v1; si falta un jugador, entra `NoraBot` con respuesta de `BOT_ANSWERS`.
- `duel` (18 s por pareja): solo se envían las dos tarjetas del duelo activo; los autores no votan (salvo 1–2 jugadores).
- Puntos: 150 × voto × multiplicador; +250 × multiplicador al ganador; **Nora-KO** +300 × multiplicador si el perdedor recibe 0 votos y hay ≥2 votos.
- `duel_pairs` guarda `prompt`, `player_a/b`, `ans_a/b`, `votes_a/b` (listas de votantes).
- El reveal agrega todos los duelos y muestra `· NORA-KO`.

### 1.2 Vox Populi — chips + HILO

- `guess` baja a 45 s.
- `you.quickChips` trae 8 opciones: 4 respuestas medias de la encuesta + 4 distractores de otras encuestas.
- Matching: sin acentos, minúsculas, sin artículos, Levenshtein ≤2 o subcadena de ≥4 letras.
- `vote` (25 s): se revela el puesto #3 y una respuesta misteriosa; se vota `MAS_ARRIBA` (1–2) o `MAS_ABAJO` (4–6).
- +250 × multiplicador por acertar el HILO.
- El ranking del misterio **no viaja** antes del reveal.

### 1.3 Fuera de Época — distractores dinámicos + sort

- `guess` (45 s), rondas impares.
- Distractores dinámicos: `age=max(5,2026-año)`, `delta=max(2,int(age*0.08))`, offsets aleatorios en `[-3δ,-δ]` y `[δ,3δ]`, sin salir de 1..2026.
- `sort` (50 s), rondas pares: 3 eventos, sin años en el payload, orden del más antiguo al más reciente.
- Puntos: exacto `600 + 100×(racha−1)`; un par adyacente o primero/último correcto `250`; invertido `0` y racha a 0.
- El payload `sortItems` solo lleva `id` y `title`.

### 1.4 Texto Roto — chat por subturnos + reacciones

- `chat` (45 s) se divide en **3 subturnos de 15 s**.
- En cada subturno, un grupo es `WRITER` y el otro `REACTOR`; rotan.
- Cada writer recibe `you.secretGoal` de `SECRET_GOALS` (15 directivas).
- Los mensajes se agregan en vivo a `chatMessages`.
- Los reactores tienen `you.screen="react"`, 4 stickers (`🔥`, `💀`, `🤡`, `🚨`), 1 reacción por subturno.
- Reacción: +25 puntos al autor y +10 monedas al reactor.
- Después, `vote` (30 s) sobre toda la conversación; se suman los bonos de reacción.
- El `screen` público es `chat`; el rol se lee en `you.chatRole` y `you.screen`.

### 1.5 Franela Fest — create + remix + vote

- `create` (55 s): 1 dibujo + 2 eslóganes independientes.
- `remix` (40 s): el servidor baraja dibujos y eslóganes; `you.remixPool` trae 2 dibujos ajenos y 4 eslóganes ajenos. Hay 2 dibujos y 4 eslóganes de reserva.
- `vote` (30 s): se vota la camiseta ensamblada.
- Cada voto reparte: 200 × multiplicador al **Director Creativo** (remixer), 100 al **Artista** (dibujo) y 100 al **Copywriter** (eslogan).
- El payload de vote incluye `strokes`, `slogan`, `remixerName`, `authorName` y `sloganAuthorName`, pero los nombres se ocultan hasta el reveal.

### 1.6 El Tasador — vendedores vs compradores

- `pitch` (50 s): 2 vendedores (o 1 humano + El Tasador Bot en 1–2 jugadores), 2 lotes A/B.
- Cada lote tiene objeto, pista de legitimidad, condición secreta (`AUTENTICO` 600–1200 / `FALSIFICACION` 50–250) y valor real.
- `invest` (30 s): los compradores eligen lote y pujan 0/100/250/400; se descuentan monedas al pujar.
- Liquidación:
  - Vendedor: 100% de la suma de pujas; +200 si vendió una falsificación por >250.
  - Comprador: `max(0, valor_real − puja) × multiplicador` si pujó >0.
  - Si paga de más por una falsificación, pierde las Fichas y no gana puntos.
- `lots` no revela `authentic` ni `realValue` antes del reveal.

---

## 2. Economía

### Apuesta

- Costes 100/250/500.
- Para cobrar x2: sumar puntos y quedar en el **Top 50%** de la ronda (o empatado con el corte).
- #1 de la ronda: x3.
- Si suma pero queda en la mitad inferior: pierde.
- Solo 1 jugador: x2 con ≥300 puntos.

### Burla

- Coste 60.
- `target` opcional; si falta, se elige al líder actual distinto del emisor.
- El snapshot de la víctima recibe `you.incomingTaunt` con `{from, text, id}`.
- La TV/public conserva `targetPlayerId` y `targetName`.

### Ruleta

- Solo en ronda `total/2` y `total−1` (máximo 2 veces).
- Pagos:
  - 15 % Jackpot: +400 monedas, +200 puntos.
  - 35 % Buen Botín: +200 monedas, +50 puntos.
  - 30 % Cofre Sorpresa: +100 monedas, +100 puntos.
  - 20 % Cofre Mimic: −100 monedas (nunca <0) y +100 puntos al último lugar.
- `reactionBanner` muestra `MIMIC HIT` si alguien abre el Mimic.

### Mercado Negro

- En el reveal previo a la última ronda aparece `all_in_boost`.
- Cuesta todo el saldo (mínimo 200) y da `+0.5x` por cada 250 monedas, tope `+1.5x`, aplicado a la ronda final.

### Audiencia

- Los espectadores pueden votar en `vote` y `duel`.
- La opción con mayoría de votos de audiencia recibe `audienceFavorite=True` y +150 puntos al autor/remixer.
- Los votos de audiencia no sustituyen los de jugadores ni bloquean el avance.

---

## 3. Snapshot nuevo (aditivo)

Campos nuevos en `fiesta`:

```text
duelState: {duelIndex, duelTotal, prompt, optionA, optionB, isFinal, results}
voxState: {rank3, mysteryAnswer, quickChips, answers?, guesses?}
sortItems: [{id,title}]
chatMessages: [{id,text,role,authorName,reactions,subturn,isYours}]
lots: [{id,object,seller,sellerName,pitch,invested,suspicion,authentic?,realValue?}]
reactionBanner: str
audienceVotes: int
```

Campos nuevos en `you`:

```text
screen, quickChips, chatRole, secretGoal, remixPool, remixChoice,
incomingTaunt, finalBoost, lotRole, lotId, lotHint, waitBonus
```

Los campos son aditivos; un cliente viejo los ignora.

---

## 4. Tests añadidos

`server/tests/test_fiesta.py` (18):

- emparejamiento A/B y bot en La Última Palabra;
- chips y HILO en Vox;
- matching con acentos/erratas;
- años dinámicos y sort de 3 eventos;
- 3 subturnos, secret goals y reacciones de Texto Roto;
- pool de remix y reparto de créditos de Franela;
- vendedores/compradores y lotes ocultos de El Tasador;
- 2 jugadores con bot vendedor;
- apuesta solo Top 50%;
- burla con objetivo e `incomingTaunt`;
- ruleta máximo 2 veces y Mimic;
- all-in boost;
- audiencia;
- duraciones reales;
- 5 tomas distintas por momento;
- solo test en los seis juegos;
- no filtrado de secretos antes del reveal.

---

## 5. Comandos de verificación

```bash
.venv/bin/python -m pytest server/tests -q
./gradlew :app:testNormalDebugUnitTest --offline --max-workers=1 \
  --tests '*Fiesta*' --tests '*GameWiring*'
```

Resultado actual:

```text
667 passed backend
Android Fiesta/GameWiring en verde
```

---

## 6. Checklist original de Fase 3 (ya implementado)

- Modelos Kotlin: `duelState`, `voxState`, `sortItems`, `chatMessages`, `lots`, `remixPool`, `incomingTaunt`, `secretGoal`, `quickChips`.
- Control `sort` con ▲/▼ y palanca.
- Control `react` con stickers.
- Control `remix` con carrusel de dibujos y chips de eslogan.
- Control `invest` con Lote A/B y pujas.
- Overlay `TauntSplatOverlay`.
- `WaitingMiniGameCard`.
- `FiestaPhaseTransition` con cortina/scanline/diagonal.
- `AnimatedHostAvatar` conectado a `isSpeaking`.
- Aceleración musical de últimos 10 s y tick por segundo.
