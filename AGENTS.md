# Vision+ — AGENTS.md

> Archivo de contexto compartido entre opencode y Antigravity 2.0.
> **Actualizado por última vez:** 04/06/2026 por Antigravity

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

## Modificaciones de Antigravity (03/06/2026)

### Docker y Despliegue Automatizado
- **Multi-stage Dockerfile & Compose:** Configuración de `Dockerfile` multi-stage y `docker-compose.yml` para compilar el frontend React, levantar el servidor Express y garantizar la persistencia del volumen de datos.
- **Instructivo de Despliegue (`AGENT_SETUP.md`):** Creación del instructivo con instalación desatendida mediante winget (Git, Docker, Cloudflare), verificación del demonio docker, configuración de `.env` y levantamiento de túneles públicos Cloudflare.

### Canales en Vivo & Mini-EPG
- **Buscador y Categorías en Vivo:** Organización de canales en una cuadrícula con filtrado dinámico por categorías (incluyendo Pluto TV) y barra de búsqueda funcional.
- **Optimización de Reproductor Live TV (Mini-EPG):** Controles optimizados para canales de TV en vivo que incluyen cambio rápido de canal (anterior/siguiente), selector de relación de aspecto, información de la calidad del flujo y guía de programación integrada (Mini-EPG) con navegación espacial totalmente adaptada a mandos a distancia.

### Mantenimiento y Limpieza
- **Limpieza de Archivos Temporales:** Eliminación de copias y respaldos temporales innecesarios para optimizar el almacenamiento de la base de datos local en formato JSON.

### Rediseño de Pantallas de Carga (04/06/2026)
- **Componente Unificado LoadingScreen.jsx:** Creado un componente de pantalla de carga premium con soporte para tres modos: carga de catálogo (`catalog`), sintonizador en segundo plano (`resolver`) y búfer interno del reproductor (`player`).
- **Carrusel de Confianza Dinámico:** Muestra mensajes en bucle (ej. evadiendo anuncios, conectando túnel seguro) que aumentan la confianza del usuario mientras sintoniza el flujo de video.
- **Fondo de Transición Suave:** Estilizado con un gradiente radial oscuro que incluye un brillo de marca rojo (`#e50914`) al 12%-18% de opacidad sobre fondo negro verdadero, haciendo la transición del catálogo a la cortinilla (`brand-intro`) totalmente imperceptible y suave.
- **Estilos Premium en index.css:** Añadidas clases y animaciones de spinner doble invertido, barra de carga y desvanecimiento suaves.

### Retracción de Submenús en Menú Lateral (04/06/2026)
- **Cierre inteligente al hacer click/navegar:** Actualizados los handlers de navegación y de selección de categorías en `App.jsx` para colapsar los submenús de Canales en Vivo y Anime tan pronto como el usuario selecciona una subcategoría o cambia a otra sección de la aplicación (como Películas, Deportes o Admin). Esto evita el scroll excesivo y mantiene la barra lateral en su formato compacto original.

### Evasión de Bloqueos Antibot (PoseidonHD — 04/06/2026)
- **Nuevo browserFetcher.js:** Módulo que utiliza Playwright Chromium con bloqueo de recursos estáticos/anuncios y evasión antibot (ocultar `navigator.webdriver` e inyectar configuraciones limpias) para extraer HTML de páginas protegidas.
- **Integración con fallback en Scraper y Resolvers:** Actualizadas las funciones de resolución de streams (`videoSourceResolver.js`) y de catalogación (`scraper.js`) para procesar a PoseidonHD directamente con el nuevo extractor. Para otras fuentes, si `axios` es bloqueado por bot check (403, 429, 503), se realiza un reintento/fallback automático mediante el navegador virtual.
- **Sniffer invisible:** Añadidos argumentos anti-detección en `browserResolver.js` para asegurar que el sniffer de streams no sea bloqueado por protecciones en los servidores de hosting (como Voe o Streamwish).

### Compilación de APK para Android TV (04/06/2026)
- **Estructura del Proyecto Nativo (`android/`):** Wrapper WebView nativo de Android usando Gradle 8.14.3 y AGP 8.10.0, apuntando a `compileSdk = 34` y `targetSdk = 34`.
- **Mapeo Inteligente de Controles:** El WebView recibe los eventos del D-Pad y los pasa a la aplicación web. El botón físico **ATRÁS** se intercepta para disparar eventos `Escape`/`Backspace` al DOM de la página para interactuar con modales/reproductores web de manera fluida sin salir de la app, e implementa doble pulsación en 2 segundos para salir.
- **Soporte Fullscreen:** WebChromeClient personalizado que atrapa las solicitudes de pantalla completa de HTML5 de forma nativa para reproducir películas, series y canales.
- **Gráficos Premium integrados:** Banner de Android TV de alta resolución generado por IA (`banner.png` de 320x180 px) y logotipo de launcher personalizado (`ic_launcher.png`).
- **Archivo Resultante:** `VisionPlus-TV.apk` en la raíz del proyecto.

---

## Próximos pasos / Pendientes

- [ ] Agregar autenticación básica al panel admin
- [ ] Migrar database.json a SQLite
- [ ] Refactorizar server.js en rutas modulares (separar concerns)
- [ ] Cache de catálogo con TTL configurable
- [ ] Modo offline / service worker
- [ ] Home.jsx tiene un selector de episodios antiguo (todas las temporadas expandidas) — migrar al mismo diseño de Movies.jsx (pestañas + grilla)
- [ ] Agregar soporte para `onNextEpisode`/`onPrevEpisode` callbacks desde Movies.jsx/Home.jsx al VideoPlayer (actualmente no se pasan, pero el VideoPlayer los usa internamente via `playEpisode(getNextEpisode())`)
