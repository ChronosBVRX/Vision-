# Deportes «Principal» + alineación visual con TV en vivo (2026-11-01)

## Qué cambió

### Una sola definición de relevancia
- `home-contract/.../SportsLeagues.kt`: lista principal exacta, alias y detección de
  categorías inferiores (`LaLiga 2`, `Serie A Women`, `2. Bundesliga`,
  `Russian Premier League`, `Championship`, `Liga MX Femenil`…).
- `server/api/app.py`: el endpoint de GeeSports calcula `tier` (1/2/3) y `featured`
  por evento, descarta `visible=false` y acepta `scope=main` para servir solo Tier 1.
  El modo por defecto sigue siendo `all` para no romper clientes viejos.
- `app/.../data/gee/SportsRelevance.kt`: usa el `tier` del servidor y, con cachés
  viejas, recalcula con la misma lista exacta.

### La pantalla
- **Deportes abre en «Principal»**: solo Tier 1; la píldora `Principal` cambia a
  «Todas las ligas» sin perder el catálogo completo. El estado se conserva al volver
  del reproductor.
- El hero de Inicio y el del launcher solo aceptan Tier 1 y ordenan también los
  «próximos» por relevancia, no solo por hora.
- El feed del launcher baja de 250 a 24 eventos y filtra a Tier 1.
- TV en vivo: una tarjeta por señal (colapsa HD/FHD) y numeración estable con
  `channelNumber` del portal. El selector de **Lista** queda como siguiente paso.

### Visual de Deportes en la tele
- Hero cinematográfico del partido principal (liga, equipos, escudos, marcador,
  píldora EN VIVO, TV/señales), con el mismo lenguaje que `TvLiveHero`.
- Píldoras de cristal para alcance, deportes, días y ligas, con foco blanco y
  selección cian; se reutiliza la gramática de `TvLiveFilterPill`/`TvLiveCategoryChip`.
- Tarjetas 16:9 con degradado, escudos, marcador, liga, estado y pie de transmisión;
  foco cian de 2 dp con escala y glow.
- Encabezados de bloque en mayúsculas con contador y punto rojo para «En vivo».

## Pruebas
- `627` backend.
- `1,199` Android (3 skipped) + `355` launcher + `26` home-contract.
- Casos nuevos: falsos positivos de liga, `visible=false`, `scope=main`, tier,
  hero sin ligas secundarias, parseo de `tier`/`featured`/`visible`.

## Publicado
- Beta **2.5.226** (`v2.5.226-fiesta-beta15` + tag `beta`).
- `server/api/app.py` desplegado al VPS con respaldo `app.py.bak-vp226-20261101-101543`.
- Verificado en el Chromecast HD: arranque ~4 s y Deportes con una sola tarjeta
  del partido (Racing Santander-Real Madrid 0-3), sin el duplicado anterior.

## Pendiente
- Decidir si el launcher también necesita un selector «Todas las ligas» (hoy su
  pestaña Deportes muestra solo Tier 1).
