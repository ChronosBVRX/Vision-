# Vision+ — AGENTS.md

> Archivo de contexto compartido entre opencode y Antigravity 2.0.
> **Actualizado por última vez:** 07/06/2026 por opencode

---

## Estado del proyecto

Aplicación web de streaming (películas, series, TV en vivo). Stack: Node/Express + React/Vite. Persistencia principal en SQLite (`data/database.sqlite`). El antiguo `database.json` quedó solo como respaldo/migración legacy.

## Estructura de archivos clave

```
Vision+/
├── server.js              ← API Express (~1620 líneas, monolithic)
├── scraper.js             ← Scrapers: IPTV, películas, catálogo
├── data/database.sqlite   ← BD local principal (SQLite)
├── database.json.backup   ← Respaldo legacy de la antigua BD JSON
├── server/sports/
│   ├── sportsApiClient.js    ← TheSportsDB API client
│   ├── sportsRanker.js       ← Interest scoring LATAM/Global
│   ├── sportsMatcher.js      ← Matching eventos ↔ canales IPTV
│   ├── sportsRoutes.js       ← 6 endpoints REST
│   └── sportsWorker.js       ← Refresco automático
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
│   │   ├── SportsAgenda.jsx ← Agenda deportiva inteligente
│   │   └── Admin.jsx      ← CRUD fuentes + import M3U + seed (~780 líneas)
│   └── components/
│       ├── VideoPlayer.jsx     ← Reproductor HLS/MP4/iframe (~1215 líneas)
│       ├── DetailsModal.jsx    ← Modal de detalles
│       ├── SportsEventCard.jsx ← Tarjeta de evento deportivo
│       ├── WatchOptionsModal.jsx ← Modal "Dónde verlo"
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
| **Sports Hub (Agenda Deportiva)** | ✅ | Módulo nuevo con agenda inteligente LATAM/Global + matching IPTV (07/06/2026) |
| ~~Deportes en vivo~~ | ❌ Eliminado | Sección de deportes en vivo removida (07/06/2026) |

## Estado actual para el siguiente agente

- Versión actual visible en Bootloader: `1.0.40`
- Smart TV: `useSpatialNavigation.js` ya lee `window.isSmartTV` en runtime, no en scope de módulo.
- Smart TV: `Home.jsx` y `Movies.jsx` ya calculan columnas reales del selector de episodios; no usan `4` fijo.
- Smart TV: `VideoPlayer.jsx` ya depende de `selectedSeasonIndex` en el handler de teclas para evitar closures viejas al cambiar de temporada.
- Smart TV: `index.css` ya anula `transform`/`box-shadow` de `season-tab` y `episode-card` en modo TV para evitar layout shift.
- Backend: `server/telegramClient.js` ya es idempotente con `initPromise`, tolera `AUTH_KEY_DUPLICATED` y corta reintentos cuando Telegram queda duplicado o inalcanzable.
- Backend/Admin: rutas mutantes y de mantenimiento (`/api/sync`, `/api/restart`, `/api/restart-tunnel`, `/api/publish-tunnel`, `/api/scrape`) requieren contraseña admin.
- Build: el frontend recompiló con éxito con `cmd.exe /c "npm run build --prefix frontend"`.
- Worktree: hay cambios ajenos/no relacionados en `frontend/src/components/SportsEventCard.jsx`, `frontend/src/pages/SportsAgenda.jsx`, `server/sports/*` y `server/resolvers/adapters/planetaplayAdapter.js`; no revertirlos.
- Locks: al cierre de esta sesión el lock estaba libre.
- Riesgo pendiente: si reaparecen errores de `BrowserResolver`/`browserPool`, reprobar con el runtime activo antes de tocar el resolver.

## Sistema de Logs

El servidor escribe todos los logs en `server.log` con formato `[fecha] [LEVEL] [Modulo] mensaje`.
Este archivo se sincroniza a GitHub cada 10 minutos desde el servidor de producción.

**Para leer logs desde dev:**
```bash
git pull origin main          # baja el server.log más reciente
node read-logs.js --errors    # solo errores
node read-logs.js --last 30   # últimas 30 líneas
node read-logs.js --module Resolver --errors  # errores de un módulo
node read-logs.js --since "2026-06-04 15:00"  # desde una hora
node read-logs.js --search "ENOTFOUND"        # buscar texto
node read-logs.js --json --errors             # salida JSON para agents
node read-logs.js --watch                     # tail -f (sigue el archivo)
```

**Shorthands:**
```bash
npm run logs     # node read-logs.js
npm run errors   # node read-logs.js --errors
```

## Dependencias principales

**Backend:** express, axios, cheerio, puppeteer, playwright, cors, youtube-dl-exec
**Frontend:** react 19, vite 8, hls.js, plyr, lucide-react
**Persistencia:** SQLite (`data/database.sqlite`) con tablas legacy (`sources`, `categories`, `settings`) y tablas normalizadas (`movies`, `movie_links`, `sports_events`, etc.). `server.js` todavía mantiene una capa `memDB` en memoria para compatibilidad con endpoints legacy.

## Convenciones / Reglas

### 📌 Versión visible en Bootloader — Toda modificación incrementa la versión

**REGLA OBLIGATORIA:** Cada vez que un agente haga cualquier cambio en el código (fix, feat, refactor, chore), DEBE incrementar la versión en `package.json` sumando 1 al último dígito (ej. `1.0.02` → `1.0.03`).

**¿Por qué?** El `Bootloader.jsx` muestra `v{version}` en la pantalla de carga. La versión se inyecta automáticamente desde `package.json` via `VITE_APP_VERSION` en `vite.config.js:8`. Sin este bump, no hay forma de saber qué versión del código se está ejecutando realmente en el servidor.

**Verificación:** `grep '"version"' package.json`

### 🔒 Sistema de locks — Nunca editar el mismo archivo al mismo tiempo

Para evitar conflictos entre agents, usar el sistema de lock:

```bash
# Antes de empezar: verificar que el archivo no esté bloqueado
node agent-lock.js check server.js

# Adquirir lock al empezar
node agent-lock.js acquire opencode server.js "Refactor rutas API"

# Liberar al terminar (antes del commit)
node agent-lock.js release

# Ver lock actual
node agent-lock.js status
```

Si `check` devuelve `locked: true` con otro agente, **NO EDITAR ESE ARCHIVO**. Esperar a que termine o coordinar con el usuario.

### 🆔 Identidad de cada agente

Usar el nombre exacto en comentarios y commits:
- **opencode** — agente principal de desarrollo
- **antigravity** — agente de infraestructura y despliegue

Marca bloques de código delicados con `// [opencode]` o `// [antigravity]`.

### 📋 Workflow obligatorio para cada agente

**Desactivación del Espejo en Vivo:** El desarrollo se realiza directamente en el servidor. Ya no se debe ejecutar `npm run sync` ni usar los scripts de sincronización (`direct-sync.js`, `watch-sync.js`).

**Regla de Build Obligatorio:** Cada vez que se haga **cualquier cambio en el código** (frontend, backend, config, versión), el agente DEBE:
  1. Recompilar el frontend. En Windows, si la directiva de ejecución de PowerShell bloquea los scripts de Node/npm (`npm.ps1`), se debe forzar mediante `cmd.exe /c` (ej: `cmd.exe /c "npm run build --prefix frontend"`). Esto compilará los archivos del cliente en `frontend/dist/` e inyectará la nueva versión en el bundle.

**¿Por qué?** La versión (`VITE_APP_VERSION`) se inyecta en tiempo de build desde `package.json`. Si no se recompila el frontend, el servidor sirve el bundle viejo con la versión anterior y el Bootloader mostraría una versión incorrecta.

**Antes de empezar cualquier tarea:**
0. **CORRER EL SERVIDOR SIEMPRE:** Debes ejecutar el servidor en segundo plano **antes de hacer cualquier otra cosa**. En Windows, si la directiva de ejecución bloquea los comandos, usa `cmd.exe /c` (ej: `cmd.exe /c "npm run dev"`). Es una regla estricta ordenada por el usuario.
1. `git pull origin main` — asegurar código más reciente
2. `node agent-lock.js status` — verificar que no haya locks activos
3. Si el archivo a editar tiene lock de otro agente → **detenerse y avisar al usuario**
4. `node read-logs.js --errors --json` — leer errores activos del servidor
5. Si hay errores → priorizar su diagnóstico y solución antes de nueva funcionalidad
6. `node agent-lock.js acquire <agente> <archivo> "<tarea>"` — bloquear el archivo

**Mientras se trabaja:**
- No modificar archivos fuera del alcance de la tarea
- `data/database.sqlite` NUNCA se edita manualmente, solo vía API/módulos DB. `database.json.backup` no se toca salvo restauración explícita.
- Usar `console.log("[Modulo] mensaje")` en server.js para que quede en server.log
- **Rebuild el frontend** después de cualquier cambio en código o versión: `npm run build --prefix frontend` (en Windows: `cmd.exe /c "npm run build --prefix frontend"` si es necesario).
- Si `watch-sync.js` no está corriendo, hacer commit + push manual tras cada cambio significativo

**Antes de hacer commit:**
1. `node agent-lock.js release` — liberar el lock
2. **AVANZAR VERSIÓN:** Incrementar `package.json` → `version` sumando 1 al último dígito (ej. 1.0.02 → 1.0.03). Verificar con `grep '"version"' package.json`
3. `git status` — verificar que solo están los archivos intencionados
4. `git diff --stat` — revisar que no hay cambios accidentales
5. Verificar que NO se incluye `data/database.sqlite`, `database.json.backup` ni `agent.lock` en el commit
6. Verificar que no se incluyen secretos/API keys
7. Hacer commit descriptivo incluyendo la versión y push

**Formato de commits:**
- `feat:` — nueva funcionalidad
- `fix:` — corrección de bug
- `refactor:` — refactorización
- `chore:` — tareas de mantenimiento/logs
- `docs:` — documentación
- Ejemplo: `fix: PoseidonHD no cargaba episodios — cambiar thisSeries por thisSerie (v1.0.01)`

### 🚫 Qué NO hacer
- No editar `data/database.sqlite` ni `database.json.backup` manualmente (siempre vía API/módulos DB)
- No editar archivos con lock activo de otro agente
- No hacer commits sin liberar el lock primero
- No incluir secretos, API keys, .env en commits

### ✅ Qué hacer al finalizar
- Commit y push automático con mensaje descriptivo
- Si la tarea queda incompleta, dejarlo claro en el mensaje del commit
- Las tareas completadas se marcan en la sección "Próximos pasos" abajo

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

### Optimización de Rendimiento en Smart TVs (04/06/2026)
- **Detector de Smart TV**: Implementado sniffer de User Agent en `main.jsx` para aplicar la clase `.is-smart-tv` en el root del DOM.
- **Evitar Doble Decodificación (Decoder Deferral)**: Modificado `VideoPlayer.jsx` para no montar ni inicializar el video HLS/DASH principal hasta que finalice/se omita el pre-roll de marca, previniendo cuellos de botella en Smart TVs.
- **Parche de Caja de Video Gris**: Ajustados estilos de fondo del reproductor a negro absoluto (`#000 !important`) para esconder la caja gris de renderizado nativo.
- **Perfil de Estilos Reducidos para TV**: Desactivados todos los filtros de desenfoque (`backdrop-filter`) y simplificada la navegación espacial D-pad (eliminado `scale` y `translate` al enfocar) para lograr transiciones instantáneas y fluidas a 60 FPS en hardware de Smart TV de bajo costo.
- **Capas del Reproductor y Foco (Z-Index y Visibilidad)**: Subido el `z-index` de los controles del reproductor a `1000` para evitar que queden ocultos detrás del plano de decodificación por hardware de la TV. Añadido `visibility: hidden` a las barras y cabeceras ocultas para evitar que los controles invisibles capturen el foco del control remoto por error.

### Unificación de Grids y Tarjetas en Smart TV (04/06/2026, sesión tarde)
- **Compactación del Layout Horizontal (`.catalog-rows`)**: Reducida la altura de las tarjetas de poster a `155px` (manteniendo proporción 2:3) y comprimidos los márgenes y títulos de fila para permitir **3 a 4 filas visibles simultáneamente** en una pantalla típica de Smart TV (720p/1080p).
- **Uniformidad de Grillas (`.catalog-grid`, `.sports-grid`, `.ep-grid`)**: Ajustados los `minmax` de todas las grillas en el modo `.is-smart-tv` para asegurar coherencia visual. Las tarjetas de catálogo dentro de la grilla ahora llenan el 100% de la columna manteniendo su aspect ratio.
- **Corrección Definitiva del Foco (Outline sin Layout Shift)**:
  - **Problema:** Las tarjetas escalaban al enfocarse (`transform: scale()`) y su brillo exterior desbordaba la tarjeta, provocando parpadeos y solapamientos en dispositivos de gama baja.
  - **Solución Técnica:** Se eliminó cualquier `transform` y `box-shadow` al hacer focus en modo TV. Se implementó un sólido `outline: 3px solid var(--primary-light)` junto con `outline-offset: 2px` y `overflow: hidden` en el box de la tarjeta. El outline no suma tamaño al elemento (no afecta el layout) garantizando transiciones de D-pad perfectas y sin temblores en la pantalla.

---

## Modificaciones de opencode (05/06/2026) — Tunnel auto-publish + redirect

- **`index.html` ahora usa redirect (sin iframe):** Se eliminó el iframe cross-origin que impedía el back button. Ahora hace `window.location.href` directo al túnel.
- **`publishTunnelUrl()` en server.js:** Cambiada la plantilla HTML de iframe a redirect (`TUNNEL_HTML_TEMPLATE`).
- **Auto-publish funcional:** `checkAndPublishTunnelRedirect()` cada 20s detecta cambios en `tunnel.log` y publica automáticamente a GitHub (commit+push) el `index.html` actualizado con el nuevo túnel.
- **Botón manual en status.html:** "Publicar Túnel en GitHub" llama a `/api/publish-tunnel`.

---

## Modificaciones de opencode (04/06/2026)

### Sistema de Caché Inteligente (3 capas)

**Problema:** Cada navegación a Home/Movies/Series hacía 3-4 fetch frescos al backend. Sin caché client-side de ningún tipo.

**Solución — 3 capas de caché:**

1. **useCache hook** (`frontend/src/hooks/useCache.js`):
   - Guarda respuestas API en localStorage con TTL configurable
   - Muestra datos cacheados al instante en renders iniciales
   - Refresca en background cuando el caché supera el 50% del TTL
   - Fallo silencioso si localStorage está lleno

2. **CatalogContext** (`frontend/src/context/CatalogContext.jsx`):
   - Precarga movieCatalog, seriesCatalog y sources a nivel de App
   - Provider envuelve la App en main.jsx
   - Home.jsx lee datos del context — 0 fetches en navegación
   - Movies.jsx usa useCache directo con su propio contentType

3. **Server + Service Worker**:
   - `server.js`: Cache-Control: public, max-age=300 en catalog y sources
   - `sw.js`: Cache-First para /api/catalog/* y /api/sources

**Archivos nuevos:**
- `frontend/src/hooks/useCache.js` — hook de localStorage con TTL
- `frontend/src/context/CatalogContext.jsx` — contexto de catálogo precargado

**Archivos modificados:**
- `frontend/src/main.jsx` — CatalogProvider envuelve App
- `frontend/src/App.jsx` — usa useCatalog() en vez de fetchCategories()
- `frontend/src/pages/Home.jsx` — usa useCatalog() — carga instantánea
- `frontend/src/pages/Movies.jsx` — usa useCache() — carga instantánea
- `server.js` — Cache-Control headers en catalog y sources
- `frontend/public/sw.js` — Cache-First para catalog/sources

### Cache TTL configurable desde Admin
- Nuevos sliders en Admin (`discovery` tab) para ajustar TTL de catálogo (1-60 min) y sources (1-30 min)
- Almacena preferencia en localStorage (vp_ttl_catalog, vp_ttl_sources)
- useCache.js lee estos valores automáticamente
- Botón para limpiar caché local y restablecer valores por defecto

### Sistema de Locks entre agents (04/06/2026)
- `agent-lock.js` — Script para adquirir/liberar/verificar locks sobre archivos
- Evita que opencode y antigravity editen el mismo archivo simultáneamente
- Lock se almacena en `agent.lock` (ignorado por git)
- Uso: `node agent-lock.js acquire <agente> <archivo> "<tarea>"`
- Si hay lock de otro agente, el script ABORTA la operación

### Utilidad read-logs.js (04/06/2026)
- `read-logs.js` — Visor de server.log con filtros: --errors, --module, --since, --search, --json
- `npm run logs` / `npm run errors` como shorthands
- Documentado en AGENTS.md para que agents lo usen automáticamente

### Workflow robusto multi-agente (04/06/2026)
- Reglas completas en Convenciones/Reglas:
  - 🔒 Sistema de locks obligatorio antes de editar
  - 🆔 Identidad de cada agente (opencode / antigravity)
  - 📋 Checklist pre-tarea: git pull → check locks → leer errores → acquire lock
  - 📋 Checklist pre-commit: release lock → git status → diff → sin secrets → push
  - 🚫 Qué NO hacer: no editar `data/database.sqlite`/`database.json.backup`, no archivos con lock ajeno, no commits sin liberar
  - ✅ Formato estandarizado de commits (feat/fix/refactor/chore/docs)

## Modificaciones de Antigravity (05/06/2026)

### Sincronización Inicial a Servidor Remoto
- **Script `initial-sync.js`:** Creado para realizar una transferencia masiva inicial de todos los archivos locales hacia la ruta de red remota configurada en `.env`, omitiendo node_modules, android, bases de datos y carpetas git.
- **Validación del Mirror:** Confirmado que `direct-sync.js` copia instantáneamente archivos de código y distribuciones compiladas de React (`frontend/dist`) hacia el servidor en vivo.

### Bootloader Premium (Pantalla de Carga)
- **Componente `<Bootloader />`:** Implementado en React que detiene el uso de la interfaz hasta que `CatalogContext` termina de precargar toda la data.
- **Experiencia de Usuario:** Agregado gradiente radial rojo, animación de carga, tiempo mínimo de retención (2000ms) para evitar destellos rápidos, y transición suave (fade-out) hacia la app.
- **Versión Dinámica:** Vite inyecta automáticamente la versión del `package.json` mediante `VITE_APP_VERSION` para mostrarla en el splash screen.

### Desactivación de Sincronización a Servidor Remoto (05/06/2026)
- **Eliminación del flujo Sync:** Se eliminó la regla de sincronización a otra PC (espejo en vivo) debido a que el entorno de desarrollo ahora reside directamente en el servidor.
- **Simplificación del Workflow:** Se adaptaron las reglas del workflow para evitar el uso del comando `npm run sync` y de los scripts de sincronización (`direct-sync.js`, `watch-sync.js`), manteniendo la compilación del frontend y el ciclo de desarrollo directo en el servidor.

---

## Próximos pasos / Pendientes

- [x] ~~Agregar autenticación básica al panel admin~~ ✅ Backend valida rutas admin y mantenimiento
- [x] ~~Migrar database.json a SQLite~~ ✅ Hecho (`data/database.sqlite`)
- [ ] Refactorizar server.js en rutas modulares (separar concerns)
- [ ] ~~Cache de catálogo con TTL configurable~~ ✅ Hecho
- [ ] ~~Modo offline / service worker~~ ✅ Cache-First implementado en SW
- [ ] ~~Home.jsx tiene un selector de episodios antiguo~~ ✅ Ya estaba migrado (pestañas + grilla)
- [ ] Agregar soporte para `onNextEpisode`/`onPrevEpisode` callbacks desde Movies.jsx/Home.jsx al VideoPlayer
- [ ] ~~Sistema de locks entre agents~~ ✅ `agent-lock.js` implementado
- [ ] ~~Utilidad: read-logs.js con filtros~~ ✅ Creado + npm scripts
- [ ] ~~Workflow multi-agente documentado~~ ✅ Reglas completas en AGENTS.md
- [x] ~~**Configurar Enlace en Vivo (direct-sync)**: (Para opencode) El usuario ha solicitado un espejo EN VIVO...~~ ✅ Completado por antigravity (script initial-sync y comprobación de mirror)
