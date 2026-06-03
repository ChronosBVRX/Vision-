# Vision+ — AGENTS.md

> Archivo de contexto compartido entre opencode y Antigravity 2.0.
> **Actualizado por última vez:** 02/06/2026 por opencode

---

## Estado del proyecto

Aplicación web de streaming (películas, series, TV en vivo, deportes). Stack: Node/Express + React/Vite. Persistencia en `database.json` (JSON plano).

## Estructura de archivos clave

```
Vision+/
├── server.js              ← API Express (~1620 líneas, monolithic)
├── scraper.js             ← Scrapers (~1430 líneas): Rojadirecta, IPTV, películas
├── database.json          ← BD local (~8425 líneas)
├── server/resolvers/
│   ├── videoSourceResolver.js
│   ├── browserResolver.js
│   ├── customSearch.js
│   ├── languageNormalizer.js
│   ├── sourceValidator.js
│   └── adapters/
│       ├── authorizedEmbedAdapter.js
│       ├── directFileAdapter.js
│       └── fallbackAdapter.js
├── frontend/src/
│   ├── App.jsx            ← Router + sidebar + submenú Pluto TV expandible
│   ├── pages/
│   │   ├── Home.jsx       ← Catálogo + TV en vivo (~366 líneas)
│   │   ├── Movies.jsx     ← Películas/series con catálogo, selector episodios TV (~710 líneas)
│   │   ├── Series.jsx     ← Wrapper de Movies.jsx con contentType='series' (7 líneas)
│   │   ├── Sports.jsx     ← Deportes en vivo (~438 líneas)
│   │   └── Admin.jsx      ← CRUD fuentes + import M3U + seed (~780 líneas)
│   └── components/
│       ├── VideoPlayer.jsx     ← Reproductor HLS/MP4/iframe (~1215 líneas)
│       ├── DetailsModal.jsx    ← Modal de detalles
│       ├── CatalogComponents.jsx ← HeroBanner + CatalogRow (~231 líneas)
│       └── TrailerPlayer.jsx
└── Dockerfile             ← Chromium incluido para Puppeteer
```

## Funcionalidades implementadas

| Funcionalidad | Status | Notas |
|---|---|---|
| Catálogo películas | ✅ | Scrapea `scrapeMovieCatalog()`, seed desde jsonfakery.com (TMDB) |
| Series con episodios | ✅ | Scrapea temporadas, resuelve episodios individuales |
| TV en vivo (IPTV) | ✅ | Brave Search + M3U fallbacks, parseo automático |
| Pluto TV (Live & VOD) | ✅ | Scraper integrado con boot dinámico para México |
| Deportes en vivo | ✅ | Scrapea Rojadirecta + mirrors |
| Resolución de video | ✅ | iframe → sniff → directo (.m3u8/.mp4), con proxy HLS |
| Proxy HLS | ✅ | `/api/proxy` reescribe playlists y sirve segmentos |
| Panel Admin | ✅ | CRUD, import M3U, seed TMDB, scraper URL |
| Navegación espacial | ✅ | `useSpatialNavigation.js` (para smart TV/controles remotos) |
| Reproductor con controles TV | ✅ | HLS.js + Plyr, barra custom TV, volumen, velocidad, fullscreen |
| Imagen proxy | ✅ | `/api/img-proxy` con cache en memoria (TTL 1h) |
| Buscador y categorías | ✅ | Búsqueda en tiempo real + chips de categorías dinámicas |
| Submenú Pluto TV (sidebar) | ✅ | Expandible con "TV en Vivo" y "Películas Bajo Demanda" |
| Selector episodios TV (Movies.jsx) | ✅ | Pestañas temporada + grilla episodios, navegable con mando |
| Navegación episodios en reproductor | ✅ | Botones Anterior/Siguiente + drawer lateral con lista completa |

## Dependencias principales

**Backend:** express, axios, cheerio, puppeteer, playwright, cors, youtube-dl-exec
**Frontend:** react 19, vite 8, hls.js, plyr, lucide-react
**Sin BD:** usa JSON plano (`database.json`) — ~8425 líneas actualmente

## Convenciones / Reglas

1. **No pisar código del otro agente** — usar comentarios `// [agente]` si es necesario delimitar
2. **Commits y pushes automáticos** — Al finalizar con éxito cualquier tarea, al corregir un bug o antes de finalizar su turno, el agente **debe** preparar los archivos, realizar un commit descriptivo y hacer `git push` a GitHub automáticamente:
   ```bash
   git add -A
   git commit -m "avance: [Descripción corta]"
   git push origin main
   ```
3. **database.json** — archivo compartido, NO editar manualmente, siempre via API
4. **Nuevas funcionalidades** — registrar aquí abajo antes de empezar
5. **Fuentes de contenido** — No agregar fuentes de películas o series (estas solo las puede agregar el usuario). Sí está permitido agregar fuentes de Deportes online.

---

## Modificaciones de opencode (02/06/2026)

### Fix Pluto TV no reproducía
1. `videoSourceResolver.js:33` — Cambiado orden de adapters: `plutoAdapter` ahora es el primero
2. `directFileAdapter.js:13` — Guard `if (!source.url.startsWith('http'))` para no procesar URLs no-HTTP
3. `VideoPlayer.jsx:428` — Eliminada exclusión de Pluto del proxy HLS (`isPlutoUrl` check)

### Fix doble salto en navegación (teclado + espacial)
- **VideoPlayer:** keydown cambiado a **fase de captura** (`addEventListener(..., true)`) + `e.stopPropagation()` en cada bloque manejado. Enter/Space explicit: `activeEl.click()`
- **CatalogRow:** keydown cambiado a fase de captura + `e.stopPropagation()`. En borde izquierdo del row NO hay `stopPropagation` para permitir salto al sidebar
- **useSpatialNavigation.js:24** — Retorna early si existe `.watch-overlay` (el VideoPlayer maneja todo)

### Controles inteligentes VideoPlayer
- **Controles por tipo:** Películas (play/pause, seek, progreso, volumen, velocidad, fullscreen), Series (todo lo anterior + navegación episodios + drawer), TV (canales anterior/siguiente, guía, sin seek/progreso)
- **Atajos teclado:** Space=Play/Pause, F=fullscreen, M=mute, ←/→=seek 10s (no TV), ↑/↓=volumen (no TV), Escape/Backspace=retrocede/cierra (doble presión rápida)
- **Fin reproducción:** Películas → overlay Replay/Cerrar. Series → countdown 10s al siguiente episodio con Cancelar/Reproducir Ya. TV → continúa
- **Auto-focus guía:** No sobreescribe navegación manual del usuario

### Fix barra de progreso no funcionaba (02/06/2026)
- **Causa:** El `<video>` no tenía event listeners `timeupdate`/`play`/`pause`/`loadedmetadata`. `currentTime` nunca se actualizaba → barra siempre en 0%, seek no se reflejaba visualmente
- **Solución:** Agregado `useEffect` en VideoPlayer.jsx que escucha `timeupdate`, `play`, `pause`, `loadedmetadata`, `ended` y actualiza estado de React
- **Flechas en progreso/volumen:** Cuando el foco está en la barra de progreso o slider de volumen, `←`/`→` delegan al handler nativo (seek / cambiar volumen) en lugar de navegar entre botones

### Selector de episodios para TV (Movies.jsx — 02/06/2026)
- **Antes:** Todas las temporadas expandidas verticalmente, sin navegación por teclado
- **Ahora:**
  - Pestañas de temporada (`.season-tab`) horizontales, navegables con `←`/`→`
  - Grilla de episodios (`.episode-card`) para la temporada seleccionada, navegable con `←` `→` `↑` `↓`
  - `Enter` reproduce, `Escape`/`Backspace` cierra
  - Eventos keydown en **fase de captura** para evitar interferencia con `useSpatialNavigation`
  - Auto-foco al primer episodio/pestaña cuando se abre el selector
  - Handler de flechas del catálogo (`Movies.jsx:308`) ahora también retorna early si `activeSeries` está activo
- **CSS nuevo** en `index.css`: `.ep-selector-*`, `.season-tab`, `.episode-card`, `.ep-grid`

### Navegación de episodios DENTRO del reproductor (VideoPlayer.jsx — 02/06/2026)
- **Botones nuevos en barra de controles (series):** `[T1:E3] [< Anterior] [≡ Lista] [Siguiente >]`
  - Anterior/Siguiente: `getPrevEpisode()`/`getNextEpisode()` — cruza entre temporadas (wrapping)
  - `List` abre drawer lateral (`.player-series-drawer`) con:
    - Selector de temporada (`<select>`)
    - Lista de episodios, episodio actual marcado (`.active`)
- **Navegación con mando dentro del drawer:**
  - `↑`/`↓` = navega episodios (wrapping)
  - `←`/`→` = cambia temporada
  - `Enter`/`Space` = reproduce enfocado
  - `Escape`/`Backspace` = cierra drawer y vuelve a controles
- Auto-foco al primer episodio al abrir el drawer
- El CSS ya existía (`.player-series-drawer`, `.player-episode-item`, etc.), solo se activó el JSX

### Fix PoseidonHD no cargaba episodios (03/06/2026)
- **Causa:** El sitio web de PoseidonHD cambió su estructura de `__NEXT_DATA__` para las series. Pasaron de usar `thisSeries` a `thisSerie`, y cambiaron la forma de estructurar las URLs de los episodios. Además, la página de episodios ahora usa la propiedad `episode` en lugar de `thisMovie` o `thisEpisode` para almacenar los videos.
- **Solución:**
  - `scraper.js:1507`: Actualizado `scrapeEpisodesFromSeriesPage` para extraer `thisSerie`, usar los nuevos nombres de propiedades (`number` en lugar de `episode_number`), y construir las URLs usando el `slug` del episodio si está presente, o construyéndolo dinámicamente con la ruta correcta (`/serie/.../temporada/.../episodio/...`).
  - `videoSourceResolver.js:237`: Añadida la compatibilidad con `episode` en `pageProps` y soporte para extraer el `cyberlocker` como nombre del servidor en caso de que sea un reproductor genérico.

---

## Próximos pasos / Pendientes

- [ ] Agregar autenticación básica al panel admin
- [ ] Migrar database.json a SQLite
- [ ] Refactorizar server.js en rutas modulares (separar concerns)
- [ ] Cache de catálogo con TTL configurable
- [ ] Modo offline / service worker
- [ ] Home.jsx tiene un selector de episodios antiguo (todas las temporadas expandidas) — migrar al mismo diseño de Movies.jsx (pestañas + grilla)
- [ ] Agregar soporte para `onNextEpisode`/`onPrevEpisode` callbacks desde Movies.jsx/Home.jsx al VideoPlayer (actualmente no se pasan, pero el VideoPlayer los usa internamente via `playEpisode(getNextEpisode())`)
