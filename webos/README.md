# Vision+ para webOS (experimental)

Port de Vision+ a LG webOS usando la misma arquitectura que el port de XuperTV:
una app web (HTML/CSS/JS) + un servicio Luna en Node.js que habla con el API
del VPS y con el portal XTV, y publica el video por un proxy HTTP local.

> Estado: **port funcional v0.2.0**. Catálogo, búsqueda, ficha con
> temporadas/capítulos, TV en vivo con EPG, deportes/agenda, VOD/live, audio y
> subtítulos por idioma, calidades, omitir intro, «Seguir viendo» y «Mi lista».
> Faltan descargas, perfiles, juegos y la prueba en una TV física.

## Por qué funciona sin el motor nativo

La app Android resuelve la reproducción con el motor X4K (ARM/bionic). En webOS
no se puede cargar ese motor. El port de XuperTV demuestra que el portal `masnew`
también entrega un CDN libre:

```
v10/startPlayVOD  -> episodeList[].totalMovieList[].movieList[]
                     (contentId = playCode, licenseList[].license)
v14/getSlbInfo    -> cdn_list[tag=vod|live] + url_list[sign_type=cfl]
                     -> https://<cdn>/vod/<playCode>_media.ts
                        headers: Content-Auth + Content-License
```

Se probaron IDs reales del catálogo de Vision+ (`A936…` = Guerra de Papás,
`D6FE…` = Tierra de Mafia, episodio `07C7…`) y resolvieron con el mismo cliente
que usa el port de XuperTV. El stream es un MPEG-TS H.265 720p con audio AAC;
`gst-discoverer-1.0` lo valida a través del proxy local. Los canales en vivo
entregan un HLS H.264 720p con segmentos públicos.

## Arquitectura

```
┌────────────────────────────── LG webOS ──────────────────────────────┐
│  app/ (HTML/CSS/JS)                                                  │
│    Inicio · Buscar · Ficha · TV en vivo · Reproductor · Ajustes       │
│         │ luna://com.chronosbvrx.visionplus.webos.service            │
│         ▼                                                            │
│  service/ (Node.js, webos-service)                                   │
│    api.js      -> https://visionplusv2.duckdns.org/v1/* (X-Api-Key)  │
│    xtv.js      -> portal masnew: sesión propia + startPlayVOD/Live    │
│    proxy.js    -> http://127.0.0.1:<puerto>/media/<token>/stream.ts  │
│    ffmpeg-relay.js -> remux TS -> MP4 (modo suave, si el equipo puede)│
└──────────────────────────────────────────────────────────────────────┘
```

La app **no** habla con el portal: el servicio acuña su propia sesión
(`v3/snToken -> MD5(snToken + sal) -> v8/active`), la guarda en
`/tmp/visionplus-webos-session.json` y la renueva sola. Así no comparte SN con
el teléfono ni con la app Android.

## Probar en el navegador (sin TV)

```bash
node webos/service/dev-server.js
# abrir http://127.0.0.1:8910/
```

El servidor de desarrollo expone los mismos métodos del servicio por HTTP y
sirve la app. El video usa el proxy local; en un PC x86 el binario `ffmpeg` ARM
no se puede ejecutar, así que `relayAvailable` será `false` y el modo suave no
estará disponible. La reproducción directa tampoco se puede validar en Chrome
si no soporta H.265; en la TV sí.

## Empaquetar

```bash
# Si no está el binario ARM (está gitignored), se extrae del .ipk de XuperTV:
./webos/tools/prepare-ffmpeg.sh "/ruta/com.webosflux.xuptertv.fidelity_1.5.8_all.ipk"

./webos/tools/pack.sh
# webos/build/visionplus-webos_0.2.0_all.ipk
```

El `.ipk` se arma como un paquete webOS (`ar` + `control.tar.gz` + `data.tar.gz`)
con:

```
usr/palm/applications/com.chronosbvrx.visionplus.webos/
usr/palm/services/com.chronosbvrx.visionplus.webos.service/
usr/palm/packages/com.chronosbvrx.visionplus.webos/packageinfo.json
```

Instalación con webOS CLI en modo desarrollador:

```bash
ares-setup-device            # una vez
ares-install -d <device> webos/build/visionplus-webos_0.2.0_all.ipk
ares-launch  -d <device> com.chronosbvrx.visionplus.webos
```

Si el instalador rechaza el paquete sin firmar, empaquétalo con
`ares-package` (webOS CLI) en lugar del script; la estructura de `webos/app` y
`webos/service` es la misma.

## Configuración

- `webos/app/config.js` — ID del servicio, URL del API, límite de títulos.
- `webos/service/api.js` — `BASE_URL` y `X-Api-Key` del VPS (los mismos de la
  app Android; también acepta `VISION_API_BASE` / `VISION_API_KEY`).
- `webos/service/xtv.js` — hosts y credenciales de portal de la familia XTV
  (los mismos que usa el port de XuperTV). Si el portal rota hosts, se cambia
  ahí; también acepta `VISION_XTV_HOSTS` separado por comas.

## Qué funciona

- Inicio con hero, filas curadas (tendencias, estrenos, recomendados, TV en vivo,
  temáticas, géneros y algunas colecciones).
- Búsqueda contra `/v1/search` (incluye el fallback del portal que ya tiene el
  VPS) con teclado en pantalla para el mando.
- Ficha de película/serie, sinopsis es-MX, nota, géneros, temporadas y capítulos
  con still de TMDB.
- Reproducción VOD: `resolve` -> proxy local -> `<video>`; seek por Range.
- TV en vivo: 1,035 canales del VPS, HLS a través del proxy, con «Ahora: …»
  desde el EPG (`/v1/epg/bulk`).
- Deportes: eventos de GeeSports del día y agenda de `/v1/sports`; las señales
  HLS compatibles se reproducen con selector de enlace. Los DASH/DRM se marcan
  como «Sin señal» para webOS.
- Audio y subtítulos por idioma: el botón Audio cambia de pista con ffmpeg; el
  botón Subtítulos recorre off/es/en.
- Calidades: si el portal ofrece más de una pista, el botón Calidad re-resuelve
  conservando el minuto.
- «Omitir intro» automático con los marcadores de `/v1/markers` (se puede
  desactivar en Ajustes).
- «Seguir viendo» y «Mi lista» en `localStorage`.
- Modo suave: ffmpeg remuxea TS -> MP4 cuando el equipo lo permite.
- El proxy reescribe manifiestos HLS (variantes, segmentos, claves y `EXT-X-MAP`)
  para que las cabeceras del CDN se apliquen también a los segmentos.
- Ajustes: modo de reproducción, calidad, subtítulos, omitir intro, renovar
  sesión y limpiar caché.

## Qué falta (roadmap)

1. **Perfiles, continuidad entre equipos y descargas** (la bóveda cifrada de la
   app Android no es portable tal cual).
2. **Juegos y control remoto** (módulos grandes, fuera del alcance de streaming).
3. **Guía EPG completa por canal** (hoy la parrilla muestra «Ahora: …» en las
   tarjetas; falta la vista de programación detallada).
4. **Deportes DASH/DRM**: GeeSports publica muchos enlaces DASH con ClearKey;
   webOS no reproduce DASH nativo sin MSE/dash.js y el DRM no está integrado.
5. **HEVC**: el portal suele ofrecer H.265 720p. Las TVs LG modernas lo
   soportan; para equipos sin HEVC haría falta transcodificar a H.264 (costoso).
6. **Transpilar el servicio a ES5** si se quiere soportar webOS 4 o 3
   (Node 0.12). El port asume **webOS 5.0+** (Node 8.12); para la UI conviene
   webOS 6+ (Chromium 87), aunque el layout ya evita `aspect-ratio` y `inset`.
7. **Prueba en TV física** con mando: foco, botón Atrás, reanudación, audio y HEVC.

## Port de PC

El mismo motor y la misma interfaz de este port se publican para Windows en
`pc/` (servidor local, lanzador de bandeja, Node/FFmpeg portables y relay
H.264). Ver `pc/README.md` y `docs/PC_PORT_XUPER_ANALISIS.md`.

## Notas legales

El código de este port es Apache-2.0, igual que Vision+. El catálogo, las
licencias y el motor son de terceros; no se redistribuyen credenciales ni se
promete disponibilidad. El binario `ffmpeg` incluido viene del paquete de
XuperTV (GPL) y se usa solo para remux local; si no se desea distribuir, se
puede borrar `webos/service/ffmpeg` y el modo suave quedará desactivado.
