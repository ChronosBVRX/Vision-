# Vision+ Fiesta — temporada larga y estadísticas legibles (2026-10-31)

## Qué cambió

### 1. Deportes en vivo: el resumen ya no se amontona

- El panel sobre los controles se limita a **5 cifras** y a una altura máxima
  (264 dp en tele), así que ya no empuja los controles fuera de la pantalla.
- Las alineaciones y el resto de cifras viven en una **tarjeta completa**
  (`MatchStatsDialog.kt`):
  - pestañas **Resumen** y **Alineaciones**;
  - el mando recorre las cifras con Arriba/Abajo (la lista se desplaza sola);
  - Izquierda/Derecha cambia de tarjeta;
  - Atrás cierra; el foco vuelve a la píldora **Estadísticas**.
- Se añadió la píldora **Estadísticas** al reproductor de deportes. El panel
  compacto también abre la tarjeta al tocarlo en el móvil.

### 2. Selector de juegos: tarjetas alineadas

- La fila de juegos usa `clipToBounds` y un `contentPadding` de 56 dp: ninguna
  tarjeta se sale de su carril ni se dibuja encima del encabezado.
- Se quitó el escalado agresivo de la tarjeta enfocada; el foco se marca con
  borde y color, sin desfasar a las vecinas.
- La fila conserva el desplazamiento al último juego jugado.

### 3. Temporada larga (20–40 min)

- Partidas por defecto:
  - La Última Palabra: 12 rondas.
  - Vox Populi: 12 rondas.
  - Fuera de Época: 12 rondas.
  - Texto Roto: 8 rondas.
  - Franela Fest: 6 rondas.
  - El Tasador: 10 rondas.
- Se alargaron las fases de escritura/voto y se añadió el **minijuego de la
  Ruleta de Fiesta** después de las rondas pares (desde salas de 6 rondas):
  cada jugador abre un cofre y gana monedas o puntos.
- **Giros de ronda** (`TWISTS`) con efecto real en el servidor:
  - `double`: puntos y monedas x2.
  - `rush`: reloj al 68 % y puntos x1.5.
  - `golden`: monedas x2.
  - `sudden`: el primero en enviar gana +300.
  - `comeback`: el último al empezar la ronda recibe +300.
  - `clean`: ronda normal.
- **Economía**: cada 100 puntos generan monedas. En el reveal se abre una
  **tienda** en el teléfono con tres ventajas para la ronda siguiente:
  - Doble puntos (300).
  - Robo de bolsillo (250): quita 200 al líder al cerrar la ronda.
  - Seguro de fiesta (150): si no sumas, cobras 300.
- El marcador de tele y teléfono muestra **puntos · monedas**.

### 4. Arte de plató

Seis fondos originales en `drawable-nodpi`:

- `fiesta_stage_ultima_palabra.webp`
- `fiesta_stage_vox_populi.webp`
- `fiesta_stage_fuera_de_epoca.webp`
- `fiesta_stage_texto_roto.webp`
- `fiesta_stage_franela_fest.webp`
- `fiesta_stage_el_tasador.webp`

Se usan como fondo del plató y del selector, con un velo oscuro para que no
compitan con el texto. Cada juego tiene su propio lenguaje visual (focos,
noticiero, túnel temporal, chat glitcheado, pasarela, casa de subastas).

### 5. Más variación de diálogo

- `BANTER_BY_GAME` añade tres frases escritas por fase y juego (no sonoras):
  aparecen como “crawl” del plató y cambian por ronda. La voz principal sigue
  sincronizada con su toma; no se inventan subtítulos que no correspondan al
  audio.
- El teléfono muestra el giro de la ronda, el crawl y las monedas.

### 6. Audio de juegos

- Se añadieron **180 ms de silencio al inicio** de todas las tomas `.m4a`
  (narradora y Fiesta) para que el `MediaPlayer` de la tele no se coma la
  primera sílaba.
- Se retiró `setPlaybackParams` del arranque de `NarratorAudio`: en algunos
  televisores recreaba la pista de audio y cortaba el inicio.
- Los generadores (`tools/generate_narrator_voice.py` y
  `tools/generate_fiesta_voice.py`) ya incluyen el colchón para futuras tomas.

## Pruebas

- **611 backend** en verde (`server/.venv/bin/pytest server/tests -q`).
- Suite completa de Android en verde (`:app:testNormalDebugUnitTest`).
- Pruebas nuevas:
  - `test_fiesta.py`: giros, monedas, tienda y minijuego.
  - `FiestaDepthTest.kt`: duración/rondas, payload, UI de tele/teléfono,
    assets de plató y audio sin `playbackParams`.
  - `LiveSportsPlayerUiTest.kt`: tarjeta completa de estadísticas.

## Verificación en Chromecast HD (ADB)

- Panel compacto de estadísticas visible con los controles debajo, sin
  amontonar ni tapar la barra.
- La fila de juegos ya no dibuja tarjetas fuera de su carril.
- El selector muestra **22–28 min / 6–12 rondas**.
- El plató de Fiesta pinta el fondo nuevo y el marcador con monedas.
- El minijuego aparece en el servidor local y la tele lo pinta.

## Pendiente

- **Desplegar `server/api/fiesta.py` y `games.py` en el VPS** antes de que la
  beta instalada use estas mecánicas (la prueba se hizo con
  `fiesta_beta_server` local en `:8100`). No se publicó OTA.
- Probar la tarjeta completa de estadísticas con el mando físico y una partida
  real de Fiesta con 2–3 teléfonos.
- Las variantes **sonoras** de los diálogos siguen pendientes: el modelo Qwen3
  TTS vive en el disco externo, que no está montado. Las variantes escritas ya
  están activas.
