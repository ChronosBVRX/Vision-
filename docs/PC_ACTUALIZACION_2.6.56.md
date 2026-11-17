# Actualización del port de PC — análisis y cambios (2.6.56)

**Fecha:** 2026-11-17
**Alcance:** `pc/` + `webos/` (el `.exe` de PC reutiliza la UI y el motor de webOS).
**Versión anterior:** PC 0.5.0 / motor webOS 0.3.0, última edición del código el
2026-11-12, equivalente a la app Android **2.6.28**.
**Versión nueva:** PC **0.7.0** / motor webOS **0.5.0**, alineada con Android
**2.6.56**.

## 1. Qué se encontró

El port de PC no es una app aparte: es un envoltorio Electron de `webos/app` +
`webos/service`. Por eso todo lo que cambió en Android desde 2.6.29 hay que
llevarlo al motor JS y a la UI web, no al Kotlin.

| Área | Estado antes | Problema real |
|---|---|---|
| VOD | `v9/startPlayVOD` con `columnId: 0` | Solo 480p; el filtro de CDN exigía `sign_type=cfl` y el portal ahora entrega `cs`/`goog`. Los `cs` devuelven **403**; sin relevo, el play moría. |
| FHD | Sin broker | No se usaba `/v1/play/vip`; se perdía el 1080p que sí tiene Android. |
| Series | Sin `episodeNumberList` | «Sig. capítulo», anterior y selector pedían siempre el primero. |
| CDN | Un solo host | Sin reintento ni comprobación previa; un host caído rompía el título. |
| TV en vivo | `v4/startPlayLive` | El portal lo retiró (`portal100001`); la licencia ahora viene en `getLiveData`. |
| Proxy | Keep-alive + `req.close` | En Node moderno mataba el upstream antes de los bytes (`ECONNRESET`); además `destroyConnection` destruía la respuesta antes de vaciarla. |
| Infantil | Sin menús | No existían «Películas Infantiles» ni «Series Infantiles» dentro de Películas/Series. |
| Fichas | Sin reparto/país | El API ya manda `cast`/`countries`, pero la UI no los pintaba. |
| Calidad | Sin aviso | No se mostraba la resolución real al empezar. |

No son portables tal cual (y siguen fuera del alcance de esta actualización):
juegos/Party, descargas USB cifradas, perfiles locales, control remoto y la guía
EPG detallada. Son módulos de la app Android, no del motor de streaming.

## 2. Cambios aplicados

### Motor (`webos/service`)

- **`xtv.js`**
  - Licencia VOD en paralelo: **broker FHD** (`/v1/play/vip`) + v9 local para
    metadatos/subtítulos y respaldo. Si el broker tarda, a los 4 s se reproduce
    el respaldo local.
  - `episodeNumberList` real en series; `authType = "1"` y `seriesContentId`
    correcto.
  - Mapa de columnas (`getNextColumns`) como respaldo v10.
  - `vodCdns`: acepta `cfl`, `goog` y `cs`; ordena `cfl`/`goog` antes que `cs`
    (los `cs` actuales devuelven 403).
  - Sondeo `Range: bytes=0-0` de hasta 4 CDN y elección del primero que responde,
    con caché de 10 min.
  - `getSlbInfo` prueba **v15** y cae a **v14**; usa `masmobile` cuando la
    licencia es del broker y `reserve1` de la familia Hydra.
  - Vivo: `getLiveData` entrega `liveAddressList`; el canal se abre con esa
    licencia (`liveStreamFor`), sin `startPlayLive`.
  - Deduplicación de pistas por calidad (H.264/H.265 de la misma resolución).
- **`api.js`**: nuevo `playVip()`.
- **`core.js`**: pasa `episodeNumber` al resolver; precarga el mapa de columnas
  al arrancar y ya no sondea con FFmpeg los MP4 grandes (descargaba ~85 MB antes
  de abrir; la duración la reporta el reproductor).
- **`proxy.js`**:
  - Sin keep-alive en los agentes (los CDN cierran el socket tras cada Range).
  - `req.close` ya no se usa para cerrar el upstream (en Node moderno se emite
    al terminar de leer la petición, no al abortar).
  - `destroyConnection(..., keepResponse)` no destruye la respuesta del cliente
    cuando el upstream termina normalmente; antes se perdía el primer byte.
  - Log de error de upstream para diagnóstico.

### UI (`webos/app`)

- Menú **Películas** desplegable con «Películas» y «Películas Infantiles»;
  «Series Infantiles» añadido al menú de Series.
- `openKids()` usa las categorías reproducibles `xtv-kids-76805` (películas) y
  `xtv-kids-76806` (series), con scroll infinito y filtro `originalLanguage != ja`.
- Ficha: reparto y país (`detail-extra`), con respaldo al reparto del portal.
- Reproductor: aviso «Calidad: 1080p/720p/480p» al iniciar.
- `resolveParams`: envía el número real de capítulo.
- **Deportes en vivo (reescritura con paridad TV)**:
  - fuente única GeeSports (`/v1/gee38/events` y `/v1/gee38/sports`), sin el
    cruce difuso con la agenda `/v1/sports` que armaba tarjetas distintas;
  - scope «Principal / Todas las ligas», chips dinámicos por deporte,
    Hoy/Mañana, ligas con logo (todas, no solo 12), favoritos y búsqueda local;
  - bloques **En vivo / Comienzan pronto / Terminados** con el mismo orden y
    contadores, hero del partido, mosaico de escudos/marcador, estado con
    minuto/hora y pie de TV/señales;
  - selector de transmisiones con filas GeeSports, badge
    Disponible/Caído/Sin verificar, `token_api` que se resuelve al abrir,
    respaldo XTV, marca de la señal sintonizada y recordatorios;
  - pestaña **Canales** con los grupos 24/7 de GeeSports, disponibilidad y su
    propio selector;
  - al volver del reproductor se reabre el partido con la señal marcada;
  - refresco de la agenda cada 2 min y reloj cada 30 s.
- **Servicio**: `geeSports` en `api.js`, `core.js`, `dev-server.js` y
  `pc/server.js`.

### Versiones y documentación

- `webos/app/appinfo.json`, `webos/app/config.js`, `webos/service/package.json`:
  **0.5.0**.
- `pc/electron/package.json`: **0.7.0**.
- `pc/electron/OTA_NOTES.md`, `pc/README.md`, `webos/README.md`: actualizados.

## 3. Verificación realizada

- `node --check` en `xtv.js`, `core.js`, `api.js`, `proxy.js`, `app.js` y
  `pc/server.js`: OK.
- **Película** (`La Odisea`): `resolve` devuelve pistas 1080p/720p/480p;
  `proxyPath` responde **206** con cabecera MP4 (`ftypisom`).
- **Serie** (`Tierra de Mafia`): C1 y C2 devuelven `media_code` distintos; el
  stream del C2 responde **206**.
- **Vivo**: `liveCatalog` devuelve 1,035 canales; `resolveLive` entrega HLS;
  el relay MP4 responde 200 y transmite (`curl`, 16 MB).
- **PC real** (`pc/server.js` en el puerto 8920): `/ping` OK, `/resolve` 1080p,
  stream 206 y la UI sirve los nuevos menús.
- **UI en Chromium**: menú Películas → Películas Infantiles carga la cuadrícula;
  la ficha muestra sinopsis, reparto y país; «Reproducir» abre el video en
  **1080p** (`readyState 4`, sin errores de consola) y el OSD muestra
  «Calidad: 1080p».
- **Deportes**: 99 eventos de hoy + 23 de mañana; «Principal» y «Todas las
  ligas»; búsqueda «mexico» deja `Mexico vs Jamaica`; la pestaña Canales carga
  33 grupos / 86 canales; un partido en vivo abre el selector con badges
  Disponible/Caído, reproduce (`readyState 4`) y al volver reabre el mismo
  selector con la señal «Seleccionada».

## 4. Entrega

- **PC 0.7.0 publicado** en el canal `pc-latest`:
  `VisionPlusPC-Portable.exe` (137,150,396 bytes, SHA-256
  `2fa2b42d1a996abf3047cd875934358299f93c86a66e26250753624e3808b747`).
- Manifiesto `pc-version.json` verificado con `version: 0.7.0` y el mismo SHA.

## 5. Lo que queda para otra entrega

1. **Sugerencias al empezar los créditos** (2.6.54): el motor ya recibe los
   marcadores, falta el modal de «Más como esta» en la UI web.
2. **Overlay premium del reproductor** (2.6.47–49): póster y sinopsis en el OSD.
3. **EPG detallada por canal** y **deportes DASH/DRM** (roadmap original).
4. **Party, perfiles, descargas y control remoto**: módulos Android que exigirían
   rediseñar su almacenamiento para la web.
