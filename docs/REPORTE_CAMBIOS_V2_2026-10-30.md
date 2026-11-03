# Reporte de cambios — Vision+ Play v2

**Fecha:** 30 de octubre de 2026  
**Alcance:** evolución de los 7 juegos a reglas v2, backend, Android TV, teléfono y assets.  
**Estado:** cierre de producción 2.5.198/vc438 compilado, firmado e instalado en Chromecast HD. Backend desplegado y smoke test OK. **OTA todavía no publicada.** Incluye los parches de la auditoría de sistemas (parásito, pistas privadas, timbre, escáner, informantes y optimización de assets).

---

## 1. Resumen ejecutivo

Se añadió una capa de reglas `rulesVersion=2` que convive con las reglas anteriores:

- Las salas nuevas se crean en v2.
- Las salas v1 siguen funcionando exactamente como antes.
- El snapshot incluye `rulesVersion` y objetos nuevos por juego.
- El servidor sigue siendo la autoridad: fases, timers, puntajes y secretos.

La intención del rediseño fue romper el bucle de “leer la tele y tocar un botón”:

- **Trivia** ahora tiene ataques, castigos, clasificación y asalto final.
- **Apuesta** pasó a póker de pistas con pozo, compra de pistas y apuestas laterales.
- **Charadas** tiene director, saboteador, timbre y ayudas.
- **Impostor** tiene cómplice, detective y coartada rápida.
- **Dibujo** tiene tinta limitada, adivinanza temprana, fichas de crítico y linterna.
- **Subasta** tiene 6 lotes, cartas secretas, informantes, falsa alarma y lote maldito.
- **Fibbage** tiene veneno, escáner, voto de certeza, cazador y ronda final compartida.

---

## 2. Cambios por juego

### 2.1 Trivia Vision+ 2.0 — “El Piso se Quema”

**Fases nuevas**

- `attack`: ventana de 6 s para usar un poder.
- `punishment`: ronda de castigo de 10 s.
- `punishment_reveal`: resultado del castigo, 5 s.
- `sort`: clasificación de 6 cartas, 18 s.
- `assault`: asalto final de selección múltiple, 25 s.

**Mecánicas**

- Con racha ≥2, el jugador puede gastar **200 puntos** en:
  - `freeze`: el rival debe pulsar 5 veces para ver sus opciones.
  - `glitch`: el servidor censura letras solo en el teléfono del rival.
  - `parasite`: el atacante no responde; si el objetivo acierta cobra el 75 %, si falla ambos pierden 300.
- También existe `pass` para no atacar.
- Castigo en rondas 3 y 6: los que fallan cortan un cable; los que acertaron votan el cable caliente. Si coincide, −500.
- Rondas 4 y 5: 6 cartas para clasificar antes/después; 250 puntos por acierto.
- Ronda 8: selección múltiple; +400 por acierto, −400 por error, sin bajar de 0.

**Acciones nuevas**

- `trivia_attack` (`power`, `target`)
- `trivia_tap`
- `trivia_punish` (`wire`, `vote`)
- `trivia_sort` (`choices`)
- `trivia_assault` (`selected`)

**Snapshot nuevo**

- `trivia.roundKind`
- `trivia.attack` (`canAttack`, `cost`, `powers`, `targets`, `used`)
- `trivia.you` (`frozen`, `frozenTaps`, `glitched`, `parasiteTarget`, etc.)
- `trivia.sort`, `trivia.assault`, `trivia.punishment`

---

### 2.2 Apuesta a la pista 2.0 — “Bóveda Ciega y Póker de Pistas”

**Mecánicas**

- Todos empiezan con **1,000 puntos** de banca.
- Cada ronda paga una **ciega de 100** al pozo.
- La pista se revela en 3 etapas:
  - Etapa 1: póster pixelado + año/género, paga **x4**.
  - Etapa 2: póster al 50 % + sinopsis censurada, paga **x2**.
  - Etapa 3: póster claro + reparto/cita, paga **x1**.
- El jugador puede **bloquear** su respuesta o **comprar** la siguiente pista (+200 al pozo).
- Apuesta lateral: 300 puntos contra alguien que bloqueó en etapa 1.
- El pozo se reparte por peso de factor; si nadie acierta, **rueda** a la siguiente ronda.

**Acciones nuevas**

- `wager_buy`
- `wager_lock` (también acepta `/answer`)
- `wager_side_bet`

**Snapshot nuevo**

- `wager.stage`, `pot`, `ante`, `buyCost`, `posterReveal`, `clue`
- `wager.lockedStage1`
- `wager.sideBets`, `sideResults`
- `wager.you` (`locked`, `lockedStage`, `canBuy`, `canSideBet`, `factor`)

---

### 2.3 Charadas 2.0 — “El Set de Rodaje”

**Mecánicas**

- Fase `director` de 5 s: el último lugar elige una restricción para el actor.
- **Saboteador** secreto: ve el título y gana **+600** si nadie acierta.
- **Timbre “¡CORTE!”**:
  - Cualquier no-actor (menos el saboteador) puede pulsar.
  - El reloj se pausa 5 s.
  - El actor marca correcto/fallo.
  - Acierto: actor +1,000 y adivinador +700.
  - Fallo: adivinador −200.
- **Ayudas del actor**:
  - Soplar género: −200.
  - Mostrar año en TV: −300.

**Acciones nuevas**

- `charada_director`
- `charada_buzz`
- `charada_buzz_result` (`correct`/`fail`)
- `charada_help` (`genre`/`year`)

**UI**

- Teléfono: director elige restricción; actor ve título, restricción, ayudas y botones; saboteador ve título y misión; el resto ve botón de timbre.
- TV: muestra director, restricción, timbre, ayudas y saboteador al reveal.

---

### 2.4 El Impostor 2.0 — “Infiltración y Coartadas”

**Mecánicas**

- **Cómplice** (salas de 6+): sabe quién es el impostor, no el lugar; +700 si el impostor escapa.
- **Detective** (salas de 5+): recibe dos lugares posibles (real + señuelo); no ve la palabra; +300 si el crew gana.
- **Coartada rápida**: fase `coartada` de 8 s; todos eligen un “vibe” 0–9 y la tele muestra los valores antes de votar.

**Acciones nuevas**

- `impostor_coartada` (`index` 0–9)

**Snapshot nuevo**

- `impostor.role` (`impostor`/`accomplice`/`detective`/`crew`)
- `impostor.detectiveOptions`
- `impostor.coartadaValues`
- `impostor.accompliceName`, `detectiveName` al reveal

**Pendiente de esta capa**

- Interrogatorio libre con elección de siguiente víctima.
- Radar de sospecha en tiempo real.
- Sabotaje del `guess` por el crew.

---

### 2.5 Duelo de Dibujo Ciego 2.0 — “Taller Caótico”

**Mecánicas**

- **Tinta limitada** en ronda 2: límite 100; si el artista termina con ≤50 % y hay votos reales, duplica su bono.
- **Adivinanza temprana**: durante `draw`, los demás pueden escribir una palabra clave; si aciertan, +400 y esa palabra queda prohibida en su mentira.
- **Fichas de crítico** durante `vote`:
  - Basura: −200 al autor.
  - Corona: +300 al autor.
  - Una de cada una por jugador.
- **Linterna**: ronda 3, la tele oscurece el lienzo con un foco radial móvil.

**Acciones nuevas**

- `draw_early_guess`
- `draw_critic` (`text=bomb|crown`, `target`)

**Snapshot nuevo**

- `drawing.inkUsed`, `inkLimit`
- `drawing.flashlight`
- `drawing.earlyGuessSubmitted`, `earlyWinners`
- `drawing.criticBomb`, `criticCrown`

**Pendiente de esta capa**

- Relevo de dos artistas en la misma ronda.
- Maleficio de pintura (mancha/espejo).

---

### 2.6 Subasta Ciega 2.0 — “Mercado Negro”

**Mecánicas**

- **6 lotes** por defecto.
- **Lote compuesto**:
  - Carta pública visible.
  - Carta secreta oculta.
- **Informantes**: 2 jugadores (prioridad a los que van perdiendo) ven la carta secreta en su teléfono.
- **Falsa alarma**: una vez por jugador, la tele muestra “alguien pujó alto” aunque sea mentira.
- **Lote maldito** (ronda 4): el segundo lugar también pierde su puja.
- **Escudo reflector**: bloquea un robo, se rompe y rebota −250 al ladrón.
- La carta secreta puede ser puntos, monedas, robo, doble, escudo, bomba o donación.

**Acciones nuevas**

- `auction_ghost`

**Snapshot nuevo**

- `auction.secretEffect`, `secretValue`
- `auction.informant`
- `auction.ghostBidderName`, `ghostUsed`
- `auction.secondPays`

**Pendiente de esta capa**

- Sindicato de puja (unir bóvedas).

---

### 2.7 Fibbage 2.0 — “Trampa Mental”

**Mecánicas**

- **Mentira envenenada**: pagar 200 al escribir; si alguien la vota, el autor roba 250.
- **Escáner**: una vez por partida; tacha 2 mentiras y cuesta 300 si fallas.
- **Voto de certeza**: x1.5 si aciertas la verdad, −300 si caes en una mentira.
- **Cazador**: tras votar, puedes elegir una opción y apostar quién la escribió; +300 si aciertas.
- **Ronda final compartida**: la última ronda se marca como “Verdad compartida” y vale doble.

**Acciones nuevas**

- `fibbage_lie` con `amount=200` para envenenar.
- `fibbage_scan`
- `fibbage_hunter` (`text=optionId`, `target=authorId`)
- `fibbage_vote` con `vote=true` para certeza.

**Snapshot nuevo**

- `fibbage.scanUsed`, `scanned`
- `fibbage.certain`
- `fibbage.poisoned`
- `fibbage.finalShared`
- `fibbage.hunterGuess`

**Pendiente de esta capa**

- UI del cazador en el teléfono.
- Ronda final de doble hueco con dos espacios.

---

### 2.8 Parches de la auditoría de sistemas

- **Trivia**:
  - El parásito ahora es **gratis** de activar y exige que el objetivo no haya sido atacado.
  - Se prohíben cadenas A→B→A y el focus fire: un solo efecto negativo por víctima.
  - El atacante parásito se marca como respondido para no bloquear el reloj.
- **Apuesta**:
  - Las pistas compradas ahora son **privadas** en el teléfono; la TV no revela la pista 2/3.
  - El comprador baja su multiplicador personal; el resto no ve la pista gratis.
- **Charadas**:
  - Timbre ampliado a **7 s**.
  - Fallar el timbre impone **cooldown de 12 s**.
  - El saboteador también ve el botón de timbre (camuflaje).
  - Las ayudas se descuentan del **premio de la ronda**, no del marcador vivo.
- **Dibujo**:
  - La adivinanza temprana tiene **2 intentos** por ronda.
  - Ya no se prohíbe la palabra acertada en la mentira.
- **Subasta**:
  - **1 informante** en salas de 3–4 jugadores; **2** en 5–8.
  - Si nadie puja y la carta secreta es bomba, todos pierden 100.
  - En el lote maldito, el que empató arriba y no ganó paga la **mitad** de su puja.
- **Fibbage**:
  - Escáner tacha **1** mentira en salas de 3–4 y **2** en 5+.
  - Usar el escáner desactiva el voto de certeza en esa ronda.
  - Veneno: cuesta **150** y roba **300**.
- **Assets**:
  - Avatares reducidos a **256×256 WebP**.
  - Partículas reducidas a **128×128 WebP**.
  - El APK firmado bajó de ~90 MB a **89 MB**; el peso restante es motor y librerías, no los avatares.

---

## 3. Cambios de cliente Android

### Archivos nuevos

- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameV2Screens.kt`
  - `TriviaV2Content`
  - `WagerV2Content`
- `app/src/test/java/com/chronosbvrx/visionplus/GameV2ModelsTest.kt`

### Archivos modificados

- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameModels.kt`
  - `rulesVersion`
  - `GameTrivia`, `GameTriviaAttack`, `GameTriviaYou`, `GameTriviaSort`, `GameTriviaAssault`, `GameTriviaPunishment`
  - `GameWager` ampliado: `stage`, `pot`, `ante`, `posterReveal`, `lockedStage1`, `sideBets`, `sideResults`, `you`
  - `GameCharada`: rol, director, restricción, timbre, ayudas, género
  - `GameImpostor`: cómplice, detective, coartada
  - `GameDrawing`: tinta, linterna, adivinanza temprana, críticos
  - `GameAuction`: carta secreta, informante, falsa alarma, segundo paga
  - `GameFibbage`: escáner, certeza, veneno, ronda final, cazador
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameClient.kt`
  - `action()` acepta `power`, `wire`, `choices`, `selected`, `amount`, `vote`.
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameRoomScreen.kt`
  - Rama de Trivia v2 y Wager v2.
  - Charadas: director, saboteador, timbre, ayudas.
  - Narrativa y polling para fases nuevas.
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameModesScreen.kt`
  - Impostor: coartada y roles.
  - Dibujo: tinta, adivinanza temprana, fichas de crítico.
  - Subasta: informante, falsa alarma, lote maldito.
  - Fibbage: veneno, escáner, certeza.
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt`
  - `TvTriviaV2`, `TvWagerV2`; ramas de Charadas v2.
  - Selector inmersivo: rejilla 4x2 con siete tarjetas full-bleed y una octava celda
    `Ruleta Vision+`; sin huecos. Foco con escala, borde degradado por acento y
    resplandor ambiental animado en todo el plató.
  - La cabecera muestra las tres mecánicas v2 del juego apuntado; la presentadora
    cambia de frase con el foco y el pie recuerda `OK` / `FLECHAS`.
  - La Ruleta elige uno de los siete juegos al azar y abre la sala directamente
    (Trivia entra en `general`).
  - Lobby inmersivo a juego: arte full-bleed, QR en tarjeta premium con código de
    sala, ocho puestos visibles (dos filas de cuatro), progreso de entrada,
    presentadora compacta y `Cambiar juego` con foco directo. Mantiene el QR
    blanco de alto contraste y el código legible a distancia.
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/TvGameModesScreen.kt`
  - Impostor: coartada y roles.
  - Dibujo: linterna radial.
  - Subasta: carta secreta, informante, lote maldito.
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameCatalog.kt`
  - Trivia: 12 min, 8 rondas, nuevos pasos.
  - Apuesta: 10 min, póker de pistas.
  - Subasta: 6 rondas, 6 min, mercado negro.

---

## 4. Cambios de servidor

### Archivos

- `server/api/games.py`
- `server/api/app.py`
- `server/tests/test_games_v2.py`
- `server/tests/test_games_api.py`

### Compatibilidad

- `create_room(..., rules_version=1)` conserva las reglas v1.
- `POST /v1/game/rooms` acepta `"rulesVersion": 1|2`; por defecto crea **2**.
- Los tests históricos de v1 siguen pasando.

### Fases nuevas totales

`attack`, `punishment`, `punishment_reveal`, `sort`, `assault`, `director`, `coartada`, `ante` (la apuesta usa `clue` con etapas).

### Acciones nuevas totales

- Trivia: `trivia_attack`, `trivia_tap`, `trivia_punish`, `trivia_sort`, `trivia_assault`
- Apuesta: `wager_buy`, `wager_lock`, `wager_side_bet`
- Charadas: `charada_director`, `charada_buzz`, `charada_buzz_result`, `charada_help`
- Impostor: `impostor_coartada`
- Dibujo: `draw_early_guess`, `draw_critic`
- Subasta: `auction_ghost`
- Fibbage: `fibbage_scan`, `fibbage_hunter`

---

## 5. Pruebas

| Suite | Resultado |
|---|---:|
| Backend | **570 tests, 0 fallos** |
| Android app | **1,141 tests, 0 fallos** |
| Launcher | **176 tests, 0 fallos** |

Archivos nuevos de prueba:

- `server/tests/test_games_v2.py` — 21 pruebas de reglas v2.
- `app/src/test/java/com/chronosbvrx/visionplus/GameV2ModelsTest.kt` — 4 pruebas de contrato/parseo.

También se corrigió una regresión de UI: se quitaron emojis de los textos nuevos para respetar `UiNoEmojiTest`.

---

## 6. Despliegue y verificación

### Backend

Desplegado en el VPS:

- `/opt/streaming-backend/api/games.py`
- `/opt/streaming-backend/api/app.py`

Contenedor `streaming-api` reconstruido y healthy.

Verificación:

- `/v1/game/health` → `ok: true`
- `/v1/game/catalog` → 7 juegos, Subasta con 6 rondas y 6 min.
- Smoke HTTP v2:
  - Trivia: sala creada, respuestas, reveal, `rulesVersion=2`.
  - Charadas: fase `director`, opciones del director, restricción visible en TV.
  - Apuesta: pozo 200, compra de pistas, etapa 3, pozo 600.

### Android

- Release R8 compilado.
- Motor X4K inyectado como `classes3/4/5.dex`.
- Firmado con la llave histórica `9593cb0d…`.
- APK de prueba de ~92 MB instalado en el Chromecast HD.
- Verificado: Inicio abre, selector de 7 juegos abre, Charadas crea sala y muestra QR sin crash.
- Selector inmersivo verificado en el Chromecast: 7 tarjetas full-bleed + Ruleta,
  foco con escala y borde degradado, ambiente que cambia de azul a rojo/violeta,
  presentadora reactiva y `Ruleta Vision+` creando una sala real de Fibbage.
- Lobby inmersivo verificado en el Chromecast: arte a sangre, QR de alto contraste,
  código grande, ocho puestos con borde punteado, progreso 0/8 y botón
  `Cambiar juego` con foco azul visible. La sala creada desde la Ruleta mostró
  su QR y quedó esperando teléfonos sin crash.
- **APK 2.5.198/vc438**: 92 MB, SHA-256 `5e645845cd129997683b8105f9b6741dfafc6e2d3d711ddac641052166d038f5`.
  Archivo local: `app/build/outputs/apk/normal/release/app-normal-release-engine-signed.apk`.
- Capturas del selector:
  - `https://files.catbox.moe/fzmhdo.png` (Trivia enfocada)
  - `https://files.catbox.moe/5gs3qt.png` (Charadas enfocada)
  - `https://files.catbox.moe/xbbu8e.png` (Ruleta enfocada)
- Captura del lobby inmersivo:
  - `https://files.catbox.moe/zj6im3.png` (Charadas, sala esperando jugadores)
- **No se publicó OTA todavía.**

---

## 7. Assets generados

Se generaron con ComfyUI local:

- **12 avatares/mascotas**, cada uno con 3 estados (256×256 WebP en la app):
  - normal
  - pensando
  - celebrando
- **5 sprites de partículas** (128×128 WebP en la app):
  - confeti
  - moneda
  - chispa
  - fuego
  - corona
- Contact sheet en `docs/assets/games/art/avatar_contact_sheet.png`.
- Recursos Android en:
  - `app/src/main/res/drawable-nodpi/game_avatar_*`
  - `app/src/main/res/drawable-nodpi/game_fx_*`

También generados:

- **7 tableros de tutorial** 1280×720 (`game_tut_*.webp`).
- **5 SFX nuevos**: timbre, cristal, latido, caja fuerte y fanfarria.
- **60 voces TTS v2**: 20 momentos × 3 variantes, auditadas 60/60.
- **Transiciones Compose**: `GameTransitionsV2.kt` con fase, lienzo interpolado, reveal 3D y partículas.
- **Avatares 256×256** y **partículas 128×128** para reducir VRAM.

---

## 8. Riesgos y cosas que el experto debería revisar

1. **Trivia 2.0**:
   - ¿El ataque de 200 puntos es suficientemente caro?
   - ¿El parásito (75 % / −300) es claro y justo?
   - ¿La ronda de castigo de 10 s corta el ritmo?
   - ¿La ráfaga de 6 cartas es legible en TV?

2. **Apuesta 2.0**:
   - ¿La banca inicial de 1,000 y la ciega de 100 son correctas?
   - ¿El pozo debe repartirse por peso x4/x2/x1 o por partes iguales?
   - ¿La apuesta lateral de 300 es demasiado castigo?
   - ¿La etapa global de pistas se entiende si un jugador compra y otro no?

3. **Charadas 2.0**:
   - ¿El timbre de 5 s es suficiente?
   - ¿+700 / −200 son los puntos correctos?
   - ¿El saboteador debe poder tocar el timbre?
   - ¿Las restricciones deben multiplicar x1.5/x2 en vez de dar +300?

4. **Impostor 2.0**:
   - ¿La coartada de 8 s da evidencia útil o delata demasiado?
   - ¿El detective con dos opciones es demasiado poderoso?
   - ¿El cómplice debe saber quién es el impostor desde el inicio?

5. **Dibujo 2.0**:
   - ¿La tinta limitada de 100 es justa?
   - ¿El bono de clarividente de +400 es suficiente?
   - ¿Las fichas de crítico deben poder usarse en la verdad?
   - ¿La linterna de la ronda 3 ayuda o solo molesta?

6. **Subasta 2.0**:
   - ¿6 lotes son suficientes?
   - ¿Los informantes deben ser 2 o 1?
   - ¿El lote maldito debe castigar al segundo o al último?
   - ¿El escudo reflector es demasiado castigo para el ladrón?

7. **Fibbage 2.0**:
   - ¿El veneno de 250 es demasiado?
   - ¿El escáner debe costar 300 solo si fallas?
   - ¿El voto de certeza x1.5 / −300 es rentable?
   - ¿El cazador de +300 aporta o distrae?
   - ¿La ronda final de doble hueco es necesaria?

---

## 9. Pendientes

- Prueba física con 2–3 teléfonos.
- Cerrar UI de:
  - interrogatorio libre y radar del Impostor.
  - relevo de artistas y maleficio de pintura.
  - sindicato de subasta.
  - cazador de Fibbage.
  - doble hueco final.
- Generar tutoriales y TTS de las reglas nuevas.
- Añadir SFX nuevos.
- Publicar OTA.
- Escuchar y validar la narradora en la tele.

---

## 10. Archivos clave

- `docs/ENTREGA_GAMES_V2.md`
- `server/api/games.py`
- `server/api/app.py`
- `server/tests/test_games_v2.py`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameV2Screens.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/data/games/GameModels.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameRoomScreen.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/GameModesScreen.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/TvGamesScreen.kt`
- `app/src/main/java/com/chronosbvrx/visionplus/ui/games/TvGameModesScreen.kt`
- `tools/comfy_game_assets_fase5.py`

---

**Nota:** este reporte describe el estado del árbol de trabajo y del backend desplegado al 30 de octubre de 2026. La OTA sigue pendiente de la prueba física.
