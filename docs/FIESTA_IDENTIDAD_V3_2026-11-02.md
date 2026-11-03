# Vision+ Fiesta — identidad propia para cada juego

**Fecha:** 2026-11-02  
**Estado:** implementado en el árbol de trabajo (sin OTA ni deploy).  
**Alcance:** los seis juegos de Fiesta 3.0 (`ultima_palabra`, `vox_populi`, `fuera_de_epoca`, `texto_roto`, `franela_fest`, `el_tasador`).

---

## 1. El diagnóstico: seis juegos, una sola interfaz

El problema no era el concepto. Era que los seis juegos compartían **el mismo
esqueleto visual y el mismo control**:

- un único `FiestaTvContent` con el mismo header, el mismo `Surface` de prompt,
  el mismo `FiestaTvWaiting`, el mismo marcador y la misma lógica de pantalla;
- un único `FiestaPhoneContent` con el mismo `OutlinedTextField`, los mismos
  botones de año y la misma lista de voto;
- el fondo era la misma composición abstracta con otro color;
- la narración tenía **una sola toma por momento**: la misma frase en cada
  partida;
- la "economía" existía en el servidor (monedas, tienda, ruleta) pero el
  jugador casi no la veía ni la usaba: la tienda solo aparecía en el reveal y
  las monedas eran un número en una esquina;
- los giros de ronda eran los mismos para todos los juegos, con nombres
  genéricos.

Eso produce la sensación de "todo se siente igual": el jugador no cambia de
**objeto mental** al cambiar de juego, solo de texto.

## 2. Qué hace Jackbox que sí funciona

No se copió ningún asset, código, chiste ni personaje. Se estudiaron los packs
instalados y la documentación pública para extraer **patrones de producto**:

| Juego de referencia | Patrón que se llevó a Vision+ |
|---|---|
| **Quiplash 3** | La tele es un concurso: la frase es el cartel, las respuestas se enfrentan en tarjetas A/B y la sala vota. La ronda final sube la apuesta. |
| **Fibbage 4** | Mentira + voto + revelación; la gracia está en el texto y en el orden de la revelación, no en el formulario. |
| **Survey Scramble** | No es una trivia: es un noticiero de opinión. HILO, SPEED, SQUARES, BOUNCE, DARES y DASH cambian la **forma de leer la misma lista**. Para Vision+ se adoptó el lenguaje de noticiero, ranking y cintillo. |
| **Tee K.O.** | El teléfono es un taller: dibujo + eslogan + composición. La tele es una pasarela, no una lista de respuestas. |
| **Timejinx** | El año es el protagonista: dial, reloj, línea temporal y una máquina que se queja. |
| **FixyText** | El chat **es** la interfaz: burbujas, quién escribe, reacciones y un moderador que lee en voz alta. |
| **Junktopia** | El objeto raro manda: lote, procedencia absurda, tasación y martillo. |
| **Trivia Murder Party** | La tensión se construye con arte, sonido y un presentador con personalidad; la trivia es solo el vehículo. |

La lección común: **cada juego necesita un objeto físico distinto en la tele y
en el teléfono**. La tele no es un panel de resultados; es un plató. El teléfono
no es un formulario; es un control.

## 3. Principios de la identidad Vision+ Fiesta

1. **Un plató por juego.** La tele cambia de composición, tipografía, paleta,
   movimiento y utilería, no solo de color.
2. **Un control por juego.** Libreta, boleta, consola, chat, taller y paleta de
   subasta. El gesto principal es distinto en cada uno.
3. **Irreverencia con cariño.** El humor es mexicano, absurdo y reconocible,
   sin groserías gratuitas; el modo familiar sigue siendo seguro.
4. **Economía visible.** Monedas con nombre propio por juego, apuestas antes de
   la ronda, soplos y burlas que se compran y se ven en la tele.
5. **Voz con casting.** Cada juego tiene su presentador y **cinco tomas por
   momento**, sin repetir la anterior en la misma sala.
6. **Arte original y liviano.** Ilustración procedural/vectorial propia,
   comprimida para el Chromecast; nada de assets de terceros.

## 4. Los seis mundos

### 4.1 La Última Palabra — "El estudio naranja"

- **Tele:** marquesina de concurso, dos tarjetas gigantes enfrentadas (A/B),
  aplausómetro, focos, público en silueta y confeti suspendido.
- **Teléfono:** libreta de Nora con hoja color papel, contador de caracteres y
  un medidor de "promesa" de la respuesta.
- **Mecánica:** escribir → la sala vota → la respuesta más votada se lleva el
  aplausómetro. Apuesta, soplo y burla disponibles.
- **Voz:** Nora Vela, locutora nocturna, rápida y con sonrisa pícara.
- **Paleta:** naranja #F97316, ámbar #FBBF24, fondo #1A0704.

### 4.2 Vox Populi — "El noticiero de la gente"

- **Tele:** noticiero en vivo con cintillo, gráfica de barras, mesa de anchors,
  "● EN VIVO", índice de vergüenza y ranking de la sala.
- **Teléfono:** boleta de encuesta con sello de "ciudadano anónimo", campo de
  una línea y urna.
- **Mecánica:** adivinar la respuesta más popular; el servidor revela el top y
  reparte puntos por ranking. Los giros se llaman "Rating doble", "Última hora",
  "Patrocinado"…
- **Voz:** Don Chisme, locutor de radio de chismes, grave y exagerado.
- **Paleta:** cian #22D3EE, amarillo #FACC15, fondo #02131C.

### 4.3 Fuera de Época — "La consola temporal"

- **Tele:** consola CRT, anillos de tiempo, paneles laterales, dial de ronda y
  cuatro años gigantes en fichas tipo teclado.
- **Teléfono:** consola con cuatro teclas de año, años apagados cuando se usa el
  soplo 50/50 y una palanca "TIRAR DE LA PALANCA".
- **Mecánica:** elegir el año correcto; racha con bonus; el soplo apaga dos
  años imposibles.
- **Voz:** Profesor Tic Tac, documental antiguo, pausado y pedante.
- **Paleta:** violeta #A78BFA, rosa #F472B6, fondo #0D061F.

### 4.4 Texto Roto — "El chat que nadie pidió"

- **Tele:** una ventana de chat a pantalla completa con burbujas, roles,
  "escribiendo…", miembros en línea, stickers y glitches.
- **Teléfono:** chat real con tu papel, campo de mensaje, respuestas rápidas
  ("jajaja", "no manches", "órale") y envío.
- **Mecánica:** escribir tu línea → se arma el chat → votar la más graciosa.
- **Voz:** La Máquina, IA deadpan con glitches y chorus.
- **Paleta:** verde #34D399, cian #22D3EE, fondo #03140F.

### 4.5 Franela Fest — "La pasarela del mal gusto"

- **Tele:** revista de moda ("FRANELA"), pasarela en perspectiva, maniquíes con
  camisetas, flashes, banco del jurado y tarjeta de crítica.
- **Teléfono:** taller con lienzo en forma de camiseta, paleta de seis colores,
  campo de eslogan y botón "MANDAR A LA PASARELA".
- **Mecánica:** dibujar + eslogan → votación anónima → podio.
- **Voz:** El Crítico, snob teatral, articulado y dramático.
- **Paleta:** rosa #F472B6, oro #FBBF24, fondo #1B0614.

### 4.6 El Tasador — "La casa de subastas"

- **Tele:** cortinas, arco de escenario, lote con etiqueta, martillo gigante,
  etiquetas de precio colgando y público con paletas.
- **Teléfono:** paleta de subasta con lote, pitch, teclado de precio y botón
  "LEVANTAR LA PALETA".
- **Mecánica:** pitch + valor → votar el mejor pitch → revelar valor real y
  premiar al más cercano.
- **Voz:** El Tasador, anticuario teatral, grave y lento.
- **Paleta:** oro #FBBF24, naranja #FB923C, fondo #1A1003.

## 5. Economía y mecánicas transversales

Cada juego tiene **su propia moneda** (Risas, Rating, Cronones, Datos, Hilos,
Fichas), su **tienda con nombre** y su **giro de ronda con sabor propio**, pero
el motor comparte las reglas para que el servidor siga siendo la autoridad:

| Acción | Coste | Efecto | Dónde se ve |
|---|---:|---|---|
| **Apuesta** | 100 / 250 / 500 | Si sumas puntos en la ronda, cobras x2; si además eres el líder, x3. Si no sumas, pierdes. | Teléfono (chips) y líneas del reveal. |
| **Soplo** | 120 | Pista del presentador; en Fuera de Época apaga dos años; en El Tasador da un rango de valor. | Tarjeta "SOPLO" en el teléfono. |
| **Burla** | 60 | Sticker de texto predefinido que vuela sobre la tele. | Nube de burlas en el plató. |
| **Tienda** | 150–300 | Doble puntos, robo de bolsillo, seguro de fiesta para la siguiente ronda. | Carrusel en el reveal. |
| **Ruleta** | — | Cofres entre rondas con monedas/puntos. | Plató y teléfono. |
| **Giros** | — | Multiplicador, reloj corto, lluvia de monedas, primero en responder, remontada. | Cinta de giro y nombres propios por juego. |

El servidor valida todo: no se puede apostar más de lo que hay, no se puede
comprar dos veces el mismo soplo, las burlas solo aceptan frases de la lista y
el reveal liquida las apuestas una sola vez.

## 6. Audio: Qwen3-TTS, un narrador por juego y cinco tomas por momento

Se retiró por completo el casting anterior de Piper. Las 170 tomas actuales se
generan con **Qwen3-TTS-12Hz-1.7B-VoiceDesign** (Apache 2.0) en la RTX 3060
local:

- **Casting original:** Nora Vela, Don Chisme, Profesor Tic Tac, La Máquina,
  El Crítico y El Tasador. Cada uno tiene timbre, edad, acento, actitud y
  dirección actoral propia; no se clona a ninguna persona real.
- **Dirección por momento:** la descripción base se combina con una instrucción
  de actuación para lobby, inicio, escritura/adivinanza/pitch, voto, reveal y
  podio. La misma frase puede sonar a bienvenida, a burla o a sentencia.
- **Variables:** 5–6 momentos por juego × **5 tomas** = **170 clips**
  `fiesta_<juego>_<momento>_<0-4>.m4a`, AAC 64 kbps, loudnorm con techo −2.5 dB.
- **Selección:** el servidor elige variante por sala/ronda/fase, evita repetir
  la anterior y manda `hostCue.audio`; el cliente reproduce exactamente el
  texto que muestra.
- **Control de calidad:** `tools/qwen_voice_qc.py` mide f0 por toma y rechaza
  las que se salen del registro del presentador (más de 3 semitonos), además de
  duración, clipping y silencio.
- **Música:** seis bucles originales de 24–32 s y seis stingers de reveal,
  sintetizados localmente (concurso, noticiero, tiempo, chat, pasarela,
  subasta). Suenan solo en la tele y bajan cuando habla el presentador.

Regenerar:

```bash
PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
export PYTORCH_CUDA_ALLOC_CONF=expandable_segments:True

# 1. Casting con VoiceDesign (5 tomas por momento)
"$PY" tools/generate_fiesta_voice_qwen.py --force
# 2. Elegir una referencia por presentador
"$PY" tools/select_fiesta_voice_refs.py
# 3. Clonar la referencia en las 170 líneas (una sola voz por juego)
"$PY" tools/generate_fiesta_voice_clone.py --force
# 4. Control de f0/clipping y reparación
"$PY" tools/qwen_voice_qc.py --repair
python3 tools/make_fiesta_music.py
```

## 7. Arte premium y filtro de calidad

El arte de producción ya no es procedural. Se genera con **ComfyUI + SDXL
(Juggernaut XL v9)** en la RTX 3060 local y pasa por un filtro objetivo antes de
entrar al APK.

- 6 platós 1920×1080 (`fiesta_stage_<juego>.webp`).
- 6 fondos verticales 1080×1920 (`fiesta_phone_<juego>.webp`), recortados del
  plató para que el teléfono y la tele compartan el mismo mundo.
- 6 retratos de presentador 900×900 (`fiesta_host_<juego>.webp`).
- 6 tarjetas de selector verticales 900×1200 (`game_<juego>_poster.webp`) con el presentador y la utilería del juego.
- 1 fondo de lobby 1920×1080 (`fiesta_lobby_bg.webp`) con el salón de fiesta completo.
- 6 versiones 1280×720 (`game_<juego>_art.webp`) para las tarjetas horizontales del teléfono.

Dirección de arte común: ilustración 3D cinematográfica, luz volumétrica,
materiales ricos, negro profundo y un acento por juego. Cada plató tiene
composición y utilería propias: estudio de concurso, noticiero, cámara del
tiempo, muro de chat, pasarela y casa de subastas.

### Pipeline

```bash
# 1. Generar candidatos (no toca el APK)
python3 tools/comfy_fiesta_premium.py --only stage --variants 2
python3 tools/comfy_fiesta_premium.py --only host --variants 1
python3 tools/comfy_fiesta_premium.py --only phone --variants 1
python3 tools/comfy_fiesta_premium.py --only selector --variants 1
python3 tools/comfy_fiesta_premium.py --only lobby --variants 1
python3 tools/comfy_fiesta_premium.py --contact

# 2. Elegir los mejores en docs/assets/games/v3/premium/approved/
#    (stage/host; el teléfono se recorta del plató)

# 3. Instalar WebP en el APK
python3 tools/install_fiesta_premium.py

# 4. Filtro de calidad (213 assets)
python3 tools/fiesta_quality_gate.py
python3 tools/fiesta_quality_gate.py --premium
```

### Filtro de calidad

`tools/fiesta_quality_gate.py` rechaza:

- resolución insuficiente;
- peso demasiado bajo (señal de fondo plano);
- entropía de luminancia baja;
- densidad de bordes baja (sin estructura/utilería);
- colorfulness bajo (paleta gris/genérica);
- imágenes truncadas o ilegibles;
- voz con duración fuera de rango, nivel medio anómalo o clipping;
- música/stingers con duración o pico incorrectos.

El reporte vive en `docs/assets/games/v3/QUALITY_REPORT.md`. El candado es
objetivo, no sustituye la revisión humana de composición, legibilidad y
coherencia de personaje.

`tools/make_fiesta_art_v2.py` queda solo como respaldo de emergencia y se niega
a sobrescribir el arte premium si existe `premium/approved/`.

## 8. Archivos principales

```text
server/api/fiesta.py                       economía, apuestas, soplos, burlas, giros por juego
server/api/fiesta_content.py               contenido + variantes de voz
server/tests/test_fiesta.py                guardianes de las nuevas reglas
app/.../data/games/GameModels.kt            payloads nuevos (bet/hint/taunt/currency)
app/.../data/games/NarratorAudio.kt         música por juego + stingers
app/.../ui/games/FiestaDesign.kt            sistema de identidad y piezas compartidas
app/.../ui/games/FiestaTvWorlds.kt          los seis platós de la tele
app/.../ui/games/FiestaPhoneWorlds.kt       los seis controles de teléfono
app/.../ui/games/FiestaTvScreen.kt          despacho al plató correcto
app/.../ui/games/GameRoomScreen.kt          despacho al control correcto
tools/make_fiesta_art_v2.py                 arte original
tools/generate_fiesta_voice_qwen.py        casting Qwen3-TTS con 5 tomas
tools/make_fiesta_music.py                  música y stingers originales
```

## 9. Pruebas

- Backend: **661 tests** en verde (`pytest server/tests`), incluidos los nuevos
  de apuestas/soplos/burlas y variantes de voz.
- Android: suite de Fiesta/GameWiring/GameCatalog/GameV2Models en verde.
- Arte: los seis platós superan los 20 KB y existen los seis fondos/retratos.
- Audio: 170 clips con nombre `fiesta_<juego>_<momento>_<variante>.m4a`.

## 10. Pendientes honestos

1. **Deploy del backend** al VPS (el árbol local ya tiene las reglas; sin deploy,
   la app instalada no recibe apuestas/soplos/burlas).
2. **Prueba física** en Chromecast con 2–3 teléfonos: foco, tamaños a 720p,
   latencia del chat y mezcla de voz/música.
3. **Más contenido**: 5 tomas por momento ya evita la repetición inmediata, pero
   conviene ampliar a 8–10 y escribir 200+ prompts por juego.
4. **Tutoriales por juego** con el nuevo lenguaje visual (hoy el lobby conserva
   el tablero genérico).
5. **Moderación** de texto libre y de burlas en partidas públicas.

## 11. Nota legal

Este trabajo **no copia** código, arte, audio, preguntas, personajes ni marcas de
Jackbox. Los patrones estudiados son de producto (concurso, noticiero, pasarela,
subasta, chat) y todo el contenido, voces, arte y música de Vision+ es original
o generado localmente.
