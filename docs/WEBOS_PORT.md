# Port de Vision+ a webOS — análisis y MVP

**Fecha:** 2026-10-30
**Pregunta:** ¿se puede usar la lógica del port de XuperTV a webOS para portar Vision+?

## Respuesta corta

**Sí.** El bloqueo histórico de Vision+ en plataformas sin Android era el motor
X4K. El port de XuperTV demuestra que el mismo portal (`masnew`) entrega un CDN
libre que se puede reproducir sin el motor: se piden las pistas con
`v10/startPlayVOD`, la licencia y el CDN con `v14/getSlbInfo`, y se construye
`http(s)://<cdn>/vod/<playCode>_media.ts` con `Content-Auth` y `Content-License`.
Un proxy local pone esas cabeceras y el `<video>` de webOS reproduce el TS.

Se verificó contra IDs reales del catálogo de Vision+, no solo contra el catálogo
de muestra de XuperTV:

| Título | Ref | Resultado |
|---|---|---|
| Guerra de Papás | `xtv:A93658121D1840E78F958BAFAB9D409C` | TS H.265 720p + AAC, 1.1 GB, `206` por Range |
| Tierra de Mafia T1 E1 | `07C7904968D449C2B4B08117904B4474` | TS H.265 720p resuelto |
| La Granja VIP 1 HD | `cyx_5D7BA85AE3F8A2A0921E_720p` | HLS H.264 720p, segmentos públicos |

`gst-discoverer-1.0` sobre el proxy:

```
container #0: MPEG-2 Transport Stream
  video #1: H.265 (Main Profile), 1280x720, 25 fps
  audio #2: MPEG-4 AAC, español, 2 canales, 44100 Hz
```

## Arquitectura elegida

```
App webOS (HTML/CSS/JS)
   │ luna://…visionplus.webos.service
   ▼
Servicio Node.js
   ├── API VPS /v1/* (índice, búsqueda, episodios, vivo)
   ├── Portal XTV masnew (startPlayVOD/Live + getSlbInfo)
   └── Proxy local (cabeceras + Range + HLS) + ffmpeg opcional
```

La app **no** mete la lógica del portal en el navegador: el servicio acuña una
sesión propia, la persiste y renueva. Así no se comparte SN con el teléfono y se
evitan las patadas `aaa100083`.

## Por qué no se reutiliza el motor

- El motor X4K es ARM/bionic + JNI; webOS no tiene ART ni el runtime de Android.
- Reimplementar `playCode + license -> flujo` es la pieza frágil y legalmente
  expuesta que ya se descartó para Roku.
- El camino directo del CDN no necesita el motor y es el que usa el port de
  XuperTV en producción.

## Alcance entregado (v0.2.0)

Carpeta `webos/`:

- `app/` — Inicio, búsqueda con teclado D-pad, ficha, temporadas/capítulos,
  TV en vivo con EPG, deportes/agenda, reproductor, ajustes, «Seguir viendo» y
  «Mi lista».
- `service/` — API VPS, cliente XTV con sesión propia, proxy de medios con
  reescritura HLS, índice de TS, relay ffmpeg y servidor de desarrollo.
- `tools/pack.sh` — empaqueta el `.ipk` con la estructura de webOS.

Segunda fase:

- **Audio/subtítulos:** pistas de audio del portal (vía relay ffmpeg) y ciclo
  off/es/en de subtítulos SRT.
- **Calidad:** re-resuelve la pista elegida conservando el minuto.
- **Intro:** `/v1/markers` con auto-skip y ajuste para desactivarlo.
- **EPG:** `/v1/epg/bulk` pinta «Ahora: …» en las tarjetas de TV en vivo.
- **Deportes:** GeeSports del día + agenda `/v1/sports`; enlaces HLS con
  selector; DASH/DRM se marcan como no compatibles.
- **HLS:** el proxy reescribe manifiestos y segmentos para propagar
  `Referer`/`User-Agent`/licencias a todo el flujo.

Verificado:

- `node webos/service/dev-server.js` sirve la app y el API.
- `/home`, `/search`, `/episodes`, `/resolve`, `/resolveLive` responden.
- El proxy devuelve `206 Partial Content` para VOD y el m3u8 para vivo.
- `gst-discoverer` reconoce ambos streams.
- La UI se probó en Chromium headless con capturas de Inicio, ficha, búsqueda,
  catálogo, TV en vivo y reproductor.

## Riesgos y límites

1. **Portal de terceros**: hosts, versiones y CDN pueden cambiar. La sesión y
   los endpoints están aislados en `xtv.js` para actualizar en un solo sitio.
2. **HEVC**: muchas fichas solo ofrecen H.265 720p. TVs LG 2018+ lo soportan;
   un equipo sin HEVC necesitaría transcodificación (TV o VPS).
3. **Node del servicio**: el MVP usa `async/await`; asume webOS 5+/6 (Node
   8.12). webOS 4.x y 3.x corren Node 0.12 y necesitarían transpilación a ES5
   (y la UI, un repaso de CSS Grid/flex gap).
4. **Un stream por motor**: el portal puede limitar sesiones; el servicio usa
   una sesión propia y un proxy, pero no está probado con varios televisores a
   la vez.
5. **Funciones que no son portables tal cual**: bóveda cifrada de descargas,
   perfiles con archivos locales, juegos y continuidad entre equipos requieren
   rehacer su almacenamiento para webOS.
6. **Legal**: catálogo/licencias de terceros; no se redistribuyen credenciales.

## Siguientes hitos

1. Prueba en TV LG física: HEVC, seek, Atrás, audio, subtítulos, reanudación.
2. Guía EPG completa por canal (la tarjeta ya muestra «Ahora»).
3. Perfiles y «Mi lista» en el VPS (o `localStorage` por perfil).
4. Reproducción DASH/ClearKey vía MSE/dash.js para los enlaces de GeeSports.
5. Transpilación ES5 opcional para webOS 4/3 (Node 0.12).
6. Empaquetado firmado con `ares-package` y prueba de instalación real.
