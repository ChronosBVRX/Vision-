# Vision+ Fiesta — guardián anti-regresiones

**Última actualización:** 2026-11-02  
**Alcance:** los seis juegos de Fiesta 3.0 y su arte/voz/economía.  
**Regla:** si tocas Fiesta, corre los tests de la sección 4 y revisa la sección 5.

---

## 1. Inventario congelado (lo que ya existe)

### Juegos y hosts

| id | Juego | Host | Moneda | Tienda |
|---|---|---|---|---|
| `ultima_palabra` | La Última Palabra | Nora Vela | Risas | La Trastienda de Nora |
| `vox_populi` | Vox Populi | Don Chisme | Rating | La Mesa de Control |
| `fuera_de_epoca` | Fuera de Época | Profesor Tic Tac | Cronones | Chatarra Temporal |
| `texto_roto` | Texto Roto | La Máquina | Datos | Modo Dios |
| `franela_fest` | Franela Fest | El Crítico | Hilos | La Boutique del Mal Gusto |
| `el_tasador` | El Tasador | El Tasador | Fichas | La Bodega de Objetos Malditos |

### Bucles 4.0 (servidor)

| Juego | Secuencia | Resolución |
|---|---|---|
| La Última Palabra | write → duel → reveal | duelos A/B, 150/voto, +250 ganador, Nora-KO +300 |
| Vox Populi | guess → vote → reveal | chips + ranking + HILO (+250) |
| Fuera de Época | guess (impar) / sort (par) → reveal | años dinámicos o línea temporal |
| Texto Roto | chat (3×15 s) → vote → reveal | writers/reactors, secret goals, reacciones |
| Franela Fest | create → remix → vote → reveal | 2 eslóganes, pool ajeno, reparto 200/100/100 |
| El Tasador | pitch → invest → reveal | 2 lotes, vendedores/compradores, pujas |

### Identidad visual

Cada juego tiene su propio plató, control de teléfono y presentador. **No
vuelvas a un `FiestaTvContent` o `FiestaPhoneContent` genéricos.**

| Juego | Plató | Control de teléfono | Acento |
|---|---|---|---|
| La Última Palabra | estudio de concurso | libreta de Nora | naranja |
| Vox Populi | noticiero | boleta de encuesta | cian |
| Fuera de Época | cámara temporal | consola de años | violeta |
| Texto Roto | chat físico | chat/compositor | esmeralda |
| Franela Fest | pasarela | taller de camiseta | rosa |
| El Tasador | casa de subastas | paleta de subasta | oro |

Archivos:

```text
app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaDesign.kt
app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaTvWorlds.kt
app/src/main/java/com/chronosbvrx/visionplus/ui/games/FiestaPhoneWorlds.kt
```

`FiestaTvScreen.kt` solo despacha al plató; `GameRoomScreen.kt` despacha al
control. Los archivos viejos `FiestaTvScreen.kt` y `FiestaPhoneScreen.kt`
conservan helpers históricos, pero **la ruta activa es `FiestaWorldTv` /
`FiestaWorldPhone`**.

### Economía y reglas

- Apuesta 100/250/500, una por jugador y ronda; paga x2 con puntos y x3 si es
  líder; si no suma, pierde.
- Soplo 120, uno por jugador y ronda; en Fuera de Época apaga dos años; en El
  Tasador da rango de valor.
- Burla 60, una por jugador y ronda; solo acepta frases de `TAUNTS` del juego.
- Tienda: doble, robo y seguro; se compra en el reveal y se aplica a la
  siguiente ronda.
- Giros por juego en `TWISTS_BY_GAME`; no vuelvas a usar los giros genéricos.
- Todo se valida en el servidor. El cliente nunca decide puntos, monedas ni
  ganadores.

### Audio

- Motor: **Qwen3-TTS VoiceDesign + Base (clonación)**. Piper está retirado.
- 5 tomas por momento × 34 momentos = **170 clips**.
- Nombres: `fiesta_<juego>_<momento>_<0-4>.m4a`.
- El servidor manda `hostCue.audio` y `hostCue.variant`; el texto visible es
  exactamente el que suena.
- `FiestaVoice` resuelve por nombre con `getIdentifier`; **no hay constantes
  `R.raw` por clip**.
- `res/raw/keep.xml` conserva `@raw/fiesta_*`.
- Loudness: −16 LUFS, techo −2.5 dB, AAC 64 kbps, 48 kHz mono, 0.18 s de
  silencio inicial.
- `tools/qwen_voice_qc.py` exige f0 dentro de ±3 st del presentador.

### Arte

- **IA, no procedural**: ComfyUI + SDXL Juggernaut XL v9.
- 6 platós 1920×1080, 6 presentadores 900×900, 6 teléfonos 1080×1920, 6
  tarjetas de selección 900×1200 y un fondo de lobby 1920×1080.
- Maestros aprobados: `docs/assets/games/v3/premium/approved/`.
- Instalador: `tools/install_fiesta_premium.py`.
- Filtro: `tools/fiesta_quality_gate.py` (213 assets).
- El generador procedural `tools/make_fiesta_art_v2.py` **no puede sobrescribir**
  el arte premium: se niega si existe `premium/approved/`.

---

## 2. Invariantes que no se pueden romper

1. **Seis ids exactos** en `FiestaCatalog.ids` y `GameCatalog.all`.
2. **Cada juego tiene su propio shell**: `FiestaShell` y `FiestaLook` deben
   seguir devolviendo un `stageRes`, `phoneRes`, `hostRes` y acento distintos.
3. **La tele no muestra secretos**: año correcto, valor real y autor de voto
   solo viajan al reveal (o al jugador que corresponde).
4. **El teléfono no recibe el estado completo del resto**; solo su `you` y lo
   público.
5. **El servidor es la autoridad** de puntos, monedas, apuestas, soplos, burlas
   y ganadores.
6. **Cinco variantes por momento**: no reduzcas a una sola toma.
7. **Texto y audio comparten variante**: no cambies `hostCue.text` sin cambiar
   `hostCue.audio`/`variant`.
8. **Nombres de audio estables**: `fiesta_<juego>_<momento>_<0-4>.m4a`.
9. **No vuelvas a Piper** ni a un solo casting para los seis juegos.
10. **No vuelvas al arte procedural** en la ruta de producción.
11. **El fondo del selector no es un degradado**: usa `fiesta_lobby_bg.webp` y
    las tarjetas `game_<juego>_poster.webp` (IA).
12. **No metas assets nuevos sin pasar el filtro**: `fiesta_quality_gate.py`.

---

## 3. Archivos clave

```text
server/api/fiesta.py                     motor, economía, fases, snapshots
server/api/fiesta_content.py             prompts, encuestas, objetos, voces
server/tests/test_fiesta.py              guardianes del servidor
app/.../data/games/FiestaCatalog.kt      ids y casting
app/.../data/games/GameModels.kt         payloads
app/.../data/games/FiestaVoice.kt        resolución de audio por nombre
app/.../ui/games/FiestaDesign.kt         identidad visual
app/.../ui/games/FiestaTvWorlds.kt       seis platós
app/.../ui/games/FiestaPhoneWorlds.kt    seis controles
app/.../ui/games/FiestaStageArt.kt       mapeo de arte
docs/assets/games/v3/premium/approved/   maestros IA aprobados
docs/assets/games/v3/voices/             referencias y reporte Qwen
tools/comfy_fiesta_premium.py            generación de arte IA
tools/install_fiesta_premium.py          instalación WebP
tools/fiesta_quality_gate.py             filtro de arte/audio
tools/generate_fiesta_voice_qwen.py      casting Qwen
tools/select_fiesta_voice_refs.py        referencia por host
tools/generate_fiesta_voice_clone.py     clonación consistente
tools/qwen_voice_qc.py                   control de f0/clipping
```

---

## 4. Tests que deben quedar verdes

### Android

```bash
./gradlew :app:testNormalDebugUnitTest --offline --max-workers=1 \
  --tests '*Fiesta*' --tests '*GameWiring*'
```

Guardianes:

- `FiestaModelsTest`: parseo de twist/perk/bonus/economía/taunts.
- `FiestaDepthTest`: arte por juego, 5 tomas por momento, música/stinger, seis
  shells, presentadores y teléfonos.
- `FiestaDialogueWiringTest`: casting y Qwen en lobby/inicio/podio, texto y voz
  en la misma toma, `FiestaVoice` dinámico.
- `GameWiringTest` / `GameCatalogTest`: catálogo y cableado.

### Backend

```bash
.venv/bin/python -m pytest server/tests -q
```

Guardianes de `test_fiesta.py`:

- cada juego tiene lobby/start/podio con voz propia;
- apuestas, soplos y burlas son autoridad del servidor;
- no se puede votar lo propio ni comprar dos veces;
- el año/valor real no se filtra antes del reveal;
- el modo solo funciona en los seis juegos;
- **cada fase activa conserva su duración real** (no cae en el fallback de 8 s al añadir fases reservadas);
- **cada momento usado tiene 5 líneas distintas** de host y sus 5 audios;
- **No hay secretos antes del reveal**: ni rank del misterio de Vox, ni `authentic`/`realValue` de El Tasador, ni año en `sortItems`, ni autor en `duelState`.
- **Los bucles nuevos no se pueden desactivar sin actualizar los tests**: `duel`, `sort`, `chat`, `remix` e `invest`.

### Assets

```bash
python3 tools/fiesta_quality_gate.py          # 206 instalados
python3 tools/fiesta_quality_gate.py --premium # 19 maestros IA
```

### Release

```bash
./gradlew :app:assembleNormalRelease --offline
```

Debe terminar con `verifyEngineNamespace: colisiones generadas=0`.

---

## 5. Checklist manual de Fiesta (TV + 2 teléfonos)

- [ ] La tele abre el selector con **fondo IA** y tarjetas IA (no degradados).
- [ ] Cada tarjeta abre su juego y su plató cambia por completo.
- [ ] El teléfono muestra el control correcto (libreta, boleta, consola, chat,
      taller, paleta), no un formulario genérico.
- [ ] La voz del host es la misma durante toda la partida y cambia de tono por
      momento; no se corta al empezar otra frase.
- [ ] Se puede apostar, comprar soplo, comprar burla y comprar tienda; el saldo
      nunca queda negativo.
- [ ] El reveal muestra ganador, apuesta y burlas sin tapar el marcador.
- [ ] Fuera de Época no filtra el año correcto antes del reveal.
- [ ] El Tasador no filtra el valor real antes del reveal.
- [ ] La burla inventada se rechaza y no cuesta monedas.
- [ ] La partida llega al podio sin crash y vuelve al selector.
- [ ] A 720p/1080p no se corta texto ni se sale de pantalla.
- [ ] Sin red, el lobby muestra un error claro (no crash).

---

## 5.1 Tests específicos de Fiesta 4.0

`server/tests/test_fiesta.py` cubre:

- 2 prompts y bot en los duelos de La Última Palabra;
- chips, matching y HILO de Vox;
- años dinámicos y sort de 3 eventos;
- 3 subturnos, roles, secret goals y reacciones de Texto Roto;
- pool de remix y reparto de créditos de Franela;
- vendedores/compradores y lotes ocultos de El Tasador;
- apuesta solo Top 50%;
- burla con objetivo e `incomingTaunt`;
- ruleta máximo 2 veces y Mimic;
- `all_in_boost`;
- voto de audiencia;
- no filtrado de secretos antes del reveal.

## 6. Cómo regenerar sin romper

### Arte IA

```bash
python3 tools/comfy_fiesta_premium.py --only selector --variants 1
python3 tools/comfy_fiesta_premium.py --only lobby --variants 1
# revisar y copiar a premium/approved/
python3 tools/install_fiesta_premium.py
python3 tools/fiesta_quality_gate.py
```

### Voces Qwen

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
export PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True
"$PY" tools/generate_fiesta_voice_qwen.py --force
"$PY" tools/select_fiesta_voice_refs.py
"$PY" tools/generate_fiesta_voice_clone.py --force
"$PY" tools/qwen_voice_qc.py --repair
python3 tools/fiesta_quality_gate.py
```

**No** borres `docs/assets/games/v3/voices/refs/`: son la referencia para
reparar una toma sin cambiar de voz.

---

## 7. Errores que ya pasaron (no repetir)

| Síntoma | Causa | Fix |
|---|---|---|
| La tele repetía la misma frase | una sola toma por momento | 5 variantes y `hostCue.audio` |
| Cinco voces distintas por juego | VoiceDesign estocástico | referencia + clonación Base |
| Una toma sonaba a otra persona | f0 fuera de registro | `qwen_voice_qc.py --repair` |
| El APK no encontraba la voz | recursos eliminados por shrink | `res/raw/keep.xml` |
| El selector se veía procedural | arte viejo o degradado | `fiesta_lobby_bg` + `game_*_poster` IA |
| El servidor mandaba texto sin audio | variante no generada | regenerar y pasar QC |
| El cliente rompía al cambiar nombres | constantes `R.raw` | `FiestaVoice` por `getIdentifier` |
| La tienda no se veía | solo estaba en el servidor | carrusel en el reveal del teléfono |
| La apuesta dejaba saldo negativo | no se validaba | validación en `_place_bet` |

---

## 8. Deuda conocida

1. Deploy del backend de Fiesta al VPS (las reglas nuevas no llegan solas).
2. Prueba física con 2–3 teléfonos en la tele.
3. Más contenido: 5 tomas evitan repetición inmediata, pero conviene 8–10.
4. Tutoriales por juego con el arte nuevo.
5. Moderación de texto libre en partidas públicas.

---

## 9. Candado Fiesta 4.0 · Fases 3 y 4

### Contrato servidor → Android

- [ ] El teléfono calcula `activePhoneScreen = you.screen.takeIf { it.isNotBlank() } ?: fiesta.screen`.
- [ ] La TV sigue usando `fiesta.screen`, nunca `you.screen`.
- [ ] `duelState.optionA/B` no lleva autor antes del reveal.
- [ ] `voxState.mysteryAnswer` no lleva `rank` antes del reveal.
- [ ] `sortItems` no lleva `year` antes del reveal.
- [ ] `lots` no lleva `authentic` ni `realValue` antes del reveal.
- [ ] `chatMessages.authorName` es `ANÓNIMO` para terceros antes del reveal; el autor ve el suyo.
- [ ] `you.incomingTaunt` solo llega al jugador objetivo.
- [ ] `you.screen` distingue `text_input`/`react` en Texto Roto y `invest`/`text_input` en El Tasador.
- [ ] `you.waitBonus` impide pagar dos veces el minijuego de espera.
- [ ] `all_in_boost` aparece solo en el penúltimo reveal y cuesta todo el saldo.

### Controles de teléfono

- [ ] La Última Palabra muestra duelo, autoría propia y burla.
- [ ] Vox muestra chips de sondeo y botones HILO `MAS_ARRIBA` / `MAS_ABAJO`.
- [ ] Fuera de Época ordena 3 tarjetas con ▲/▼ y envía `fiesta_sort`.
- [ ] Texto Roto separa escritores y reactores; el reactor manda `fiesta_react`.
- [ ] Franela crea con 2 eslóganes y remezcla con 2 dibujos + 4 eslóganes ajenos.
- [ ] El Tasador separa vendedor (pitch + pista) y comprador (lote + puja 0/100/250/400).
- [ ] La burla recibida se limpia con 3 toques y no reaparece.
- [ ] El minijuego de espera paga +25 una sola vez.
- [ ] La tienda del reveal pinta `all_in_boost` cuando el servidor lo manda.

### Televisión

- [ ] `FiestaPhaseTransition` respeta `LocalReducedEffects`.
- [ ] El retrato del host se mueve solo mientras `FiestaVoice.isHostSpeaking`.
- [ ] La música sube a 1.12x en los últimos 10 s y vuelve a 1.0x en reveal.
- [ ] La Última Palabra muestra “Duelo N de M” y “¡NORA-KO!”.
- [ ] Vox muestra el puesto #3 y la respuesta misteriosa.
- [ ] Fuera de Época muestra el vórtice sin años y la línea restaurada con años en reveal.
- [ ] Texto Roto muestra burbujas entrantes y reacciones.
- [ ] Franela muestra el taller de remix y los 3 créditos en reveal.
- [ ] El Tasador muestra Lote A/B, autenticidad, valor real y recaudación.
- [ ] `reactionBanner` y `★ FAVORITO DEL PÚBLICO (+150)` se pintan cuando corresponde.

### Contenido mínimo de Fase 4

- [ ] 48 encuestas Vox con 6 respuestas.
- [ ] 66 eventos de Fuera de Época.
- [ ] 45 escenas de Texto Roto.
- [ ] 40 prompts de Franela.
- [ ] 45 objetos de El Tasador.
- [ ] 21 directivas secretas únicas.
- [ ] 20 eslóganes de reserva.
- [ ] 9 burlas válidas por juego.

### Blindaje de red y audio

- [ ] `DURATIONS["texto_roto"]["chat"] == 18` y `CHAT_SUBTURN_SECONDS == 18.0`.
- [ ] `DURATIONS["ultima_palabra"]["duel"] == 20`.
- [ ] Duplicados/fase avanzada responden 200 con `ignored: true`.
- [ ] `GameAmbient` no toca `playbackParams` si el player no está sonando.
- [ ] `pitch` se fuerza a 1.0 y el speed vuelve a 1.0 en duck/stop.
- [ ] El cambio de rol del teléfono usa `Crossfade(220 ms)`.

### Comandos de cierre

```bash
.venv/bin/python -m pytest server/tests -q
./gradlew :app:testNormalDebugUnitTest --offline --max-workers=1 \
  --tests '*Fiesta*' --tests '*GameWiring*'
python3 tools/fiesta_quality_gate.py
python3 tools/fiesta_quality_gate.py --premium
./gradlew :app:assembleNormalRelease --offline
```
