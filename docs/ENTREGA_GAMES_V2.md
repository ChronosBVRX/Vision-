# Vision+ Play — Reglas v2 (Trivia, Apuesta, Charadas, Impostor, Dibujo, Subasta y Fibbage)

**Estado:** implementación local, tests backend y Android en verde. Backend **desplegado en el VPS** y smoke v2 OK. OTA **no publicada** todavía.

## Cómo se activa

- `create_room(..., rules_version=2)`.
- `POST /v1/game/rooms` acepta `"rulesVersion": 2`; por defecto crea v2.
- Las salas v1 siguen funcionando igual (tests históricos intactos).
- El snapshot incluye `rulesVersion` y objetos nuevos (`trivia`, `wager`, `charada`, `impostor`, `drawing`, `auction`, `fibbage`).

## Trivia 2.0 — “El Piso se Quema”

Fases nuevas: `attack`, `punishment`, `punishment_reveal`, `sort`, `assault`.

- **Ataques:** con racha ≥2, el jugador puede gastar 200 puntos en `freeze`, `glitch` o `parasite` contra otro jugador.
  - `freeze`: el objetivo debe pulsar 5 veces para ver las opciones.
  - `glitch`: el servidor censura letras solo en el teléfono del objetivo.
  - `parasite`: el atacante no responde; si el objetivo acierta cobra 75 %, si falla ambos pierden 300.
- **Castigo:** rondas 3 y 6. Los que fallan cortan un cable; los que acertaron votan el cable caliente. Si coinciden, −500.
- **Ráfaga (rondas 4–5):** 6 cartas, clasificar antes/después; 250 puntos por acierto.
- **Asalto final (ronda 8):** selección múltiple; +400 por acierto, −400 por error, sin bajar de 0.
- Acciones: `trivia_attack` (power `pass` para no atacar), `trivia_tap`, `trivia_punish` (`vote=true` para los salvos), `trivia_sort` (`choices`), `trivia_assault` (`selected`).

## Apuesta 2.0 — “Bóveda Ciega y Póker de Pistas”

- Todos empiezan con **1,000 puntos** de banca.
- Cada ronda: ciega de 100 al pozo; pistas en 3 etapas x4/x2/x1.
- **Bloquear** respuesta: `wager_lock` (o `/answer`).
- **Comprar pista:** `wager_buy` (+200 al pozo, avanza la etapa global).
- **Apuesta lateral:** `wager_side_bet` (300 contra alguien que bloqueó en etapa 1).
- El pozo se reparte por peso de factor; si nadie acierta, rueda a la siguiente ronda.
- Snapshot `wager.you`: `locked`, `lockedStage`, `canBuy`, `canSideBet`, `factor`, etc.

## Charadas 2.0 — “El Set de Rodaje”

- **Director:** fase `director` de 5 s; el último lugar elige una restricción.
- **Saboteador:** en v2 uno de los otros ve el título y gana 600 si nadie acierta.
- **Timbre:** cualquier no-actor (menos el saboteador) puede pulsar `charada_buzz`; el reloj se pausa 5 s y el actor marca `charada_buzz_result` (`correct`/`fail`). Acierto: actor +1000 y adivinador +700; fallo: adivinador −200.
- **Ayudas:** el actor puede gastar 200 en `charada_help` (`genre`) o 300 (`year`).
- Snapshot `charada`: `role`, `directorOptions`, `restriction`, `buzzerName`, `helpGenre`, `helpYear`, `genre`, `saboteurName` al reveal.

## El Impostor 2.0 — “Infiltración y Coartadas”

- **Cómplice** (6+): sabe quién es el impostor, no el lugar; +700 si escapa.
- **Detective** (5+): recibe dos lugares posibles (real + señuelo); no ve la palabra; +300 si el crew gana.
- **Coartada rápida:** fase `coartada` de 8 s; todos eligen un “vibe” 0–9. La tele muestra los valores antes de votar.
- Acción: `impostor_coartada` (`index` 0–9).
- Snapshot `impostor`: `role` (`impostor`/`accomplice`/`detective`/`crew`), `detectiveOptions`, `coartadaValues`.

## Dibujo 2.0 — “Taller Caótico”

- **Tinta limitada:** ronda 2, límite 100; si el artista termina con ≤50 % y hay votos reales, duplica su bono.
- **Adivinanza temprana:** durante `draw`, los demás pueden mandar `draw_early_guess`; si aciertan una palabra del prompt, +400 y esa palabra queda prohibida en su mentira.
- **Fichas de crítico:** durante `vote`, `draw_critic` con `text="bomb"` (−200 al autor) o `"crown"` (+300 al autor). Una de cada una por jugador.
- **Linterna:** ronda 3, la tele oscurece el lienzo con un foco radial móvil.
- Snapshot `drawing`: `inkUsed`, `inkLimit`, `flashlight`, `earlyGuessSubmitted`, `criticBomb`, `criticCrown`.

## Subasta 2.0 — “Mercado Negro”

- 6 lotes por defecto.
- **Lote compuesto:** efecto público + efecto secreto.
- **Informantes:** 2 jugadores (prioridad a los que van perdiendo) ven el efecto secreto en su teléfono.
- **Falsa alarma:** `auction_ghost` una vez por jugador; la tele muestra el aviso.
- **Lote maldito (ronda 4):** el segundo lugar también pierde su puja.
- **Escudo reflector (v2):** bloquea un robo, se rompe y rebota −250 al ladrón.
- Snapshot `auction`: `secretEffect`, `secretValue`, `informant`, `ghostBidderName`, `secondPays`.

## Fibbage 2.0 — “Trampa Mental”

- **Mentira envenenada:** `fibbage_lie` con `amount=200`; si alguien la vota, el autor roba 250.
- **Escáner:** `fibbage_scan` una vez por partida; tacha 2 mentiras y cuesta 300 si fallas.
- **Voto de certeza:** `fibbage_vote` con `vote=true`; x1.5 si aciertas, −300 si fallas.
- **Cazador:** `fibbage_hunter` con `text=optionId` y `target=authorId`; +300 si aciertas quién la escribió.
- **Ronda final:** `fibbage_final_shared=true`; la UI puede mostrarla como “Verdad compartida”.
- Snapshot `fibbage`: `scanUsed`, `scanned`, `certain`, `poisoned`, `finalShared`, `hunterGuess`.

## Tests

- Backend: **570** en verde.
- Android: **1,141** en verde.
- Launcher: **176** en verde.
- Archivos nuevos: `server/tests/test_games_v2.py` (21 pruebas), `app/src/test/.../GameV2ModelsTest.kt` (4 pruebas), `GameV2Screens.kt`.

## Parches de auditoría aplicados

- Parásito gratis, sin cadenas y sin focus fire; atacante marcado como respondido.
- Pistas de Apuesta privadas en el teléfono; la TV no las filtra.
- Timbre de Charadas a 7 s, cooldown de 12 s y saboteador con botón.
- Ayudas de Charadas contra el premio de la ronda, no contra el marcador.
- Adivinanza temprana limitada a 2 intentos y sin castigo por palabra.
- 1 informante en Subasta para 3–4 jugadores; 2 para 5–8.
- Escáner de Fibbage 1/2 según tamaño y certeza desactivada.
- Veneno de Fibbage 150/300.
- Avatares 256×256 y partículas 128×128 WebP.

## Pendientes para cerrar el producto

1. Probar en Chromecast con 2–3 teléfonos (el APK de prueba ya se instaló).
2. Publicar la OTA cuando la prueba física esté cerrada.
3. Completar las capas que aún no tienen UI: interrogatorio libre/radar del Impostor, relevo de artistas, sindicato de subasta, UI del cazador de Fibbage y doble hueco final.
4. Tutoriales, TTS y SFX ya generados; falta integrarlos en más pantallas.
5. Publicar OTA cuando la prueba física esté cerrada.
