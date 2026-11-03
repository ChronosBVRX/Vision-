# Port de Vision+ a PC — análisis de XuperTV PC y entrega 1:1

**Fecha:** 2026-11-03
**Objetivo:** analizar el port portable de XuperTV para Windows y reutilizar sus
elementos de escritorio para publicar **Vision+ PC** con **nuestro catálogo** y
**nuestra interfaz de Smart TV**, sin reescribir el motor ni la UI.

---

## 1. Resumen ejecutivo

El paquete `xupertvpremium.rar` no es una app nativa de Windows ni un emulador:
es un **lanzador .NET + Node.js portable + FFmpeg portable + una app web** que
se abre en una ventana de Edge/Chrome en modo aplicación. El motor del portal
viaja en `server/magis.js`; el navegador solo consume el catálogo y los flujos
que el servidor local publica.

Lo valioso para Vision+ no es su catálogo, su portal ni su interfaz Netflix,
sino **la capa de escritorio**:

1. **Empaquetado portable**: `runtime/node.exe` + `runtime/ffmpeg.exe` + app +
   servidor, todo en una carpeta que se copia a USB.
2. **Lanzador de bandeja**: ventana sin marco con Edge/Chrome `--app`, ocultar
   a la bandeja al cerrar/minimizar, restaurar con doble clic y cerrar de verdad
   desde el menú.
3. **Servidor HTTP local** que expone el mismo motor que la app de TV.
4. **Relay FFmpeg para PC**: Chrome/Edge no reproducen HLS/MPEG-TS de forma
   nativa y Windows no siempre tiene el códec HEVC. El relay convierte a
   **MP4 fragmentado H.264/AAC** al vuelo.
5. **Seek por offset**: como el relay no es un archivo con Range, el reproductor
   reabre el flujo en `?start=<segundos>` y suma ese offset al tiempo mostrado.
6. **Atajos de escritorio**: `F` pantalla completa y `P` Picture-in-Picture.

Vision+ ya tenía las piezas 3 (motor `webos/service`) y 4/5 parcialmente
(`ffmpeg-relay.js`), y la interfaz de Smart TV (`webos/app`). La entrega añade
`pc/` con el adaptador de escritorio, el lanzador, el empaquetado y los ajustes
de reproducción para PC, **sin duplicar la UI ni el catálogo**.

Este port **no es el producto anterior de emulador** (aquel `Vision+ PC.exe` con
AVD y ~3.5 GB): es una app web nativa en una ventana de Edge/Chrome, con el
motor Node del port de webOS y FFmpeg para la compatibilidad de códecs.

---

## 2. Inventario del paquete XuperTV PC

```
xupertvpremium/
├── XuperTV.exe                  Lanzador .NET (WinForms + hooks Win32)
├── INICIAR-XUPERTV.bat          Arranque de respaldo sin lanzador
├── LEEME-WINDOWS.txt            Instrucciones de uso
├── runtime/
│   ├── node.exe                 Node.js portable (x64)
│   └── ffmpeg.exe               FFmpeg portable (x64, GPL)
├── app/                         App web (UI estilo Netflix)
│   ├── index.html
│   ├── css/netflix.css
│   ├── js/netflix.js, netflix.es5.js, hls.min.js,
│   │   tv-resolution.js, webos-polyfills.js, webOSTV.js
│   ├── data/portal.js, radios.json
│   └── images/
└── server/
    ├── server.js                HTTP local + API + proxy + relay
    ├── magis.js                 Cliente del portal (sesión, catálogo, vivo)
    ├── proxy.js                 Proxy de medios con cabeceras, HLS y subtítulos
    ├── ffmpeg-relay.js          Remux/transcodificación para PC
    ├── ts-indexer.js            Índice de segmentos dentro de un TS
    └── seeds.json               Semillas de sesión del portal
```

### 2.1 Lanzador `XuperTV.exe`

Ensamblado .NET Framework 4.0 (WinForms). Por los nombres del ensamblado y las
importaciones de `user32.dll`/`kernel32.dll`:

- `Mutex` de instancia única.
- `NotifyIcon` + `ContextMenuStrip` para la bandeja.
- `Process` para arrancar `runtime\node.exe server\server.js` y el navegador.
- `WaitForServer` con `HttpWebRequest` a `/ping`.
- `EnumWindows` + `GetWindowText` + `GetWindowThreadProcessId` para localizar la
  ventana del navegador y restaurarla.
- `SetForegroundWindow` + `AttachThreadInput` para traerla al frente.
- Hooks de bajo nivel `WH_MOUSE_LL` y `WH_KEYBOARD_LL`: interceptan el clic en
  la zona de cerrar/minimizar y `Alt+F4` para **ocultar** la ventana en vez de
  cerrarla. Por eso la reproducción continúa en segundo plano.
- `SW_HIDE` / `SW_SHOW` / `SW_RESTORE`.
- `KillOrphanedNodes` (mata `node.exe` sueltos) y `Cleanup`.

El lanzador abre el navegador con:

```
msedge.exe --app="http://127.0.0.1:8910/?service=http://127.0.0.1:8910"
           --user-data-dir="%LOCALAPPDATA%\XuperTV\EdgeProfile"
           --window-size=1280,720
```

### 2.2 Motor del servidor

- **`magis.js`**: sesión propia del portal (`snToken`/`active`), repisas del
  catálogo, búsqueda, detalle, canales en vivo y resolución
  `playCode + license -> CDN`. Las semillas viven en `seeds.json`.
- **`proxy.js`**: inyecta `Content-Auth`/`Content-License`/`User-Agent`, sirve
  Range para VOD, reescribe manifiestos HLS (variantes, segmentos, claves y
  `EXT-X-MAP`) y proxya subtítulos.
- **`ffmpeg-relay.js`**: para PC. Convierte TS y HEVC a MP4 H.264; para vivo,
  transcodifica a H.264/AAC y publica un MP4 fragmentado.
- **`ts-indexer.js`**: calcula cortes/segmentos dentro de un TS para el modo HLS
  virtual cuando no se usa FFmpeg.
- **`server.js`**: estáticos, `/ping`, `/home`, `/search`, `/detail`,
  `/liveChannels`, `/resolve`, `/resolveLive`, `/radios`, `/proxySubtitle`,
  `/abortMedia` y precalentamiento de sesión/catálogo.

### 2.3 Interfaz

`app/index.html` + `netflix.css` + `netflix.js` implementan una UI propia
(Netflix-like) con búsqueda, filtros, filas, ficha, reproductor, radio y TV en
vivo. **No se reutiliza** porque Vision+ ya tiene una interfaz de Smart TV
propia y el pedido es conservarla.

---

## 3. Qué toma Vision+ y qué no

| Elemento de XuperTV PC | Decisión | Dónde vive en Vision+ |
|---|---|---|
| Lanzador .NET con bandeja | **Se reimplementa** con la marca Vision+ | `pc/launcher/VisionPlusPC.cs` |
| Node.js portable | **Se reutiliza la idea**; el build descarga Node oficial, no el binario del RAR | `pc/runtime/node.exe` (generado) |
| FFmpeg portable | **Se reutiliza la idea**; el build descarga un FFmpeg Windows (BtbN), no el del RAR | `pc/runtime/ffmpeg.exe` (generado) |
| Servidor HTTP local | **Se reutiliza el patrón**, con el motor Vision+ | `pc/server.js` + `webos/service/core.js` |
| `ffmpeg-relay.js` | **Se adapta**: transcode PC + live relay + `?start` | `webos/service/ffmpeg-relay.js` |
| Seek por offset en el reproductor | **Se adapta** a nuestra UI | `webos/app/js/app.js` |
| `F` pantalla completa / `P` PiP | **Se añade** a nuestra UI | `webos/app/js/app.js` |
| Ventana `--app` de Edge/Chrome | **Se reutiliza** | `pc/launcher/VisionPlusPC.cs` |
| Ocultar a bandeja al cerrar | **Se reutiliza** (hooks Win32) | `pc/launcher/VisionPlusPC.cs` |
| Catálogo/portal/seeds/`magis.js` | **No se toca** | se usa `webos/service/xtv.js` |
| UI Netflix | **No se toca** | se usa `webos/app` |
| Credenciales/portal de Xuper | **No se copian** | — |

---

## 4. Arquitectura de Vision+ PC

```
┌──────────────────────────── Windows ─────────────────────────────┐
│ VisionPlusPC.exe (lanzador .NET)                                 │
│   ├─ arranca runtime\node.exe server.js                          │
│   ├─ espera data\port.json + /ping                               │
│   ├─ abre Edge/Chrome --app=http://127.0.0.1:<puerto>/?platform=pc│
│   └─ bandeja: ocultar/restaurar/cerrar + hooks Alt+F4 y X        │
│                                                                  │
│  Navegador (ventana app)                                         │
│   └─ webos/app  (LA MISMA UI DE SMART TV)                        │
│        │ fetch POST /home, /search, /resolve...                  │
│        ▼                                                         │
│  server.js (127.0.0.1)                                           │
│   └─ webos/service/core.js                                       │
│        ├─ api.js   -> VPS /v1/* (catálogo, EPG, deportes)        │
│        ├─ xtv.js   -> portal XTV (sesión propia + CDN)           │
│        ├─ proxy.js -> Range, cabeceras, HLS, subtítulos          │
│        └─ ffmpeg-relay.js -> TS/HEVC/HLS -> MP4 H.264/AAC        │
└──────────────────────────────────────────────────────────────────┘
```

### 4.1 Modo PC del motor

`server.js` define `VISION_PC=1` antes de cargar el motor. Con esa bandera:

- `core.js` enruta automáticamente al relay:
  - **VOD** si el flujo es MPEG-TS, HLS o HEVC.
  - **Vivo** siempre (HLS no nativo en Chrome/Edge).
- `ffmpeg-relay.js`:
  - VOD HEVC -> `libx264` + AAC/`aac_adtstoasc` -> MP4 fragmentado.
  - VOD H.264 -> copia de video y audio -> MP4 fragmentado.
  - Vivo -> `libx264` `ultrafast` + AAC -> MP4 fragmentado.
- `core.ping()` reporta `platform: "windows"` y `relayAvailable`.

En webOS nada cambia: sin `VISION_PC`, el relay conserva el comportamiento
anterior (copia TS con `hvc1`, sin transcode) y el proxy HLS sigue mandando.

### 4.2 Seek en el reproductor PC

El relay entrega un flujo continuo, no un archivo con `Range`. Por eso:

1. La app guarda `relayBase` (URL sin query) y `relayOffset` (segundo inicial).
2. `playerPosition()` = `video.currentTime + relayOffset`.
3. Al retroceder/avanzar, `seekToPosition()` reabre
   `relayBase?start=<segundo>&a=<audio>` y reinicia el video.
4. `updateOsd()`, subtítulos, marcadores de intro y `saveProgress()` usan la
   posición absoluta, no `video.currentTime` a secas.

Esto replica el `currentSeekOffset` que usa XuperTV PC y evita que la barra
vuelva a 00:00 al buscar.

### 4.3 Atajos de escritorio

En `webos/app/js/app.js`, solo cuando `?platform=pc`:

- `F` -> `requestFullscreen()` / `exitFullscreen()`.
- `P` -> `requestPictureInPicture()` / `exitPictureInPicture()` (durante el
  reproductor).
- Se oculta el ajuste «Modo de reproducción» porque en PC el relay es
  automático.
- El splash muestra «PC» en lugar de «webOS».

### 4.4 Interfaz reconstruida contra la app real de Smart TV

La primera versión del port PC usaba la UI mínima del port webOS. El 2026-11-03
se capturó la app real desde el Chromecast y se reconstruyó la capa visual:

- **Barra superior global**: `Vision+ TV`, Inicio, En vivo (menú TV/Deportes),
  Juegos, Películas, Series (menú Series/Anime/Novelas), Mi lista (menú
  Mi lista/Historial), reloj CDMX, campana, recarga, perfil, Buscar y ajustes.
- **Inicio**: hero cinematográfico con badge «PELÍCULA DESTACADA · DESTACADO
  HOY», título grande, metadatos, sinopsis, botones Reproducir/Detalles/
  Favorito y fila «Seguir viendo» horizontal.
- **TV en vivo**: hero del canal con logo, programa actual, ventana EPG, barra
  de avance, «Quedan X min» y «Sigue: …»; filtros de País, Buscar, categorías
  (Todo, Deportes, 24/7 Series, Noticias, Infantil, Películas, Series,
  Favoritos), estrella y recarga; carril horizontal de canales con número,
  logo, programa y progreso. El número y el logo salen del `v6/getLiveData`
  del portal (`liveCatalog`), no del resumen del VPS.
- **Deportes**: hero del partido (próximo/en vivo), filtros Principal/Fútbol/
  Básquet/Hockey/Favoritos/Buscar/Canales, días y ligas, y rejilla de tarjetas
  con liga, hora, equipos con escudos, marcador y TV; el modal reproduce la
  lista de transmisiones y, si el partido aún no empieza, ofrece recordatorios
  de 10/30/60 min.
- **Reproductor**: barra superior negra con el título, barra de progreso,
  −10 s / play / +10 s, y píldoras Audio/Subtítulos/Imagen como la app real.
- **Pantalla de carga**: splash NSIS inmediato al abrir el `.exe` y splash
  Electron animado con barra de progreso (0–100 %) mientras arranca el motor.

Referencias visuales en `docs/screenshots/`:

| Real (Chromecast) | Port PC actual |
|---|---|
| `pc_reference_home.png` | `pc_current_home.png` |
| `pc_reference_live.png` | `pc_current_live.png` |
| `pc_reference_sports.png` | `pc_current_sports.png` |
| `pc_reference_player.png` | `pc_current_player.png` |
| Captura real de Ajustes | `pc_current_settings.png` |

---

## 5. Paridad con el port de Smart TV

| Función del port webOS | PC |
|---|---|
| Inicio, hero, filas, Top 10, Novedades | ✅ misma UI |
| Buscar con teclado y filtros | ✅ misma UI |
| Ficha, temporadas, capítulos | ✅ misma UI |
| TV en vivo + EPG | ✅ misma UI; HLS por relay |
| Deportes y agenda | ✅ misma UI |
| Reproductor VOD | ✅ directo MP4 o relay H.264 |
| Audio/subtítulos | ✅ pistas del portal; audio por relay |
| Calidades | ✅ re-resuelve conservando el minuto |
| Omitir intro | ✅ marcadores con posición absoluta |
| Seguir viendo / Mi lista | ✅ `localStorage` |
| Ajustes | ✅ mismos, sin el toggle de render en PC |
| D-pad | ✅ flechas + Enter + Escape |
| Pantalla completa | ✅ `F` |
| PiP | ✅ `P` |
| Bandeja / segundo plano | ✅ lanzador .NET |
| Portable (USB) | ✅ carpeta completa + `data/` |

Funciones que siguen fuera de alcance (igual que en webOS): descargas, perfiles,
juegos, continuidad entre equipos y DASH/DRM de deportes.

---

## 6. Archivos de la entrega

> **Aviso de git:** `webos/` existe en el árbol de trabajo pero aún no está
> trackeado por git (`?? webos/`). `pc/` depende de `webos/app` y
> `webos/service`; ambos directorios deben entrar juntos en el commit/entrega.

```
repo/pc/
├── server.js                    Adaptador HTTP de escritorio
├── LEEME-PC.txt                 Instrucciones para el usuario final
├── README.md                    Documentación técnica
├── launcher/
│   └── VisionPlusPC.cs          Lanzador .NET (bandeja, hooks, Edge/Chrome)
├── electron/
│   ├── main.js                  Ventana/bandeja del ejecutable único
│   ├── package.json             Configuración electron-builder
│   └── README.md                Guía del portable de un solo .exe
├── tools/
│   ├── build-single-exe.sh      Un solo .exe (Electron + FFmpeg)
│   ├── build-windows.ps1        Carpeta portable + ZIP (Edge/Chrome)
│   ├── run-dev.sh               Prueba en Linux/macOS
│   └── smoke-test.sh            Prueba de ping/catálogo/resolve/relay
└── build/                       Salida (ignorada por git)
```

### Ejecutable único

`build-single-exe.sh` usa Electron + electron-builder para producir
`pc/build/VisionPlusPC-Portable.exe`. Ese archivo contiene Chromium, la UI, el
motor, el servidor y FFmpeg; no depende de Edge/Chrome ni de Node en la PC
destino. Los datos se guardan en `VisionPlusPC-data/` junto al `.exe`
(`PORTABLE_EXECUTABLE_DIR`). Es la opción recomendada para distribuir un solo
archivo; la carpeta con lanzador .NET + Edge/Chrome se conserva como alternativa.

Cambios compartidos, activados solo por `VISION_PC`/`?platform=pc`:

- `repo/webos/service/core.js`: ruteo automático al relay en PC.
- `repo/webos/service/ffmpeg-relay.js`: `registerLive`, transcode H.264 y
  `VISION_FFMPEG`.
- `repo/webos/app/js/app.js`: offset de relay, `F`/`P`, splash PC.

---

## 7. Cómo compilar y probar

### Windows

```powershell
cd repo\pc\tools
.\build-windows.ps1
# salida: repo\pc\build\VisionPlusPC\VisionPlusPC.exe
#         repo\pc\build\VisionPlusPC.zip
```

El script copia `webos/app` y `webos/service`, descarga `node.exe` y
`ffmpeg.exe` si no están en `pc\runtime`, compila el lanzador con `csc.exe` y
genera el ZIP. No requiere Android Studio ni permisos de administrador.

### Linux/macOS (desarrollo)

```bash
./repo/pc/tools/run-dev.sh
# abrir http://127.0.0.1:8910/?platform=pc
```

### Smoke test

```bash
PATH="/ruta/a/ffmpeg/bin:$PATH" ./repo/pc/tools/smoke-test.sh
```

Comprueba `/ping`, `/home`, `/resolve` y que el relay entregue H.264.

### Evidencia de esta sesión

- `/ping` -> `platform: windows`, `relayAvailable: true`.
- `/home` -> catálogo real (114 categorías).
- `/resolve` de «Guerra de Papás» -> `isRelay: true`.
- Relay -> MP4 `h264 1280x720 + aac`, con `?start=60` funcional.
- `/resolveLive` de «La Granja VIP 1 HD» -> `isRelay: true`, MP4 H.264/AAC.
- Chrome real: la app carga, reproduce VOD, avanza, busca por offset y
  reproduce un canal en vivo; la barra muestra la duración declarada
  (`1:57:00`) y no el fragmento del relay.

---

## 8. Límites y riesgos

1. **CPU del transcode**: HEVC 720p a H.264 en `ultrafast` consume CPU. En PCs
   modestas puede ser pesado; el modo directo solo se usa para H.264 MP4.
2. **Autoplay**: el lanzador añade `--autoplay-policy=no-user-gesture-required`.
   Sin ese flag, Chrome puede bloquear el primer play.
3. **Hooks de teclado/ratón**: el lanzador usa hooks de bajo nivel (como
   XuperTV). Algunos antivirus pueden pedir confirmación. Es código propio y
   solo actúa sobre la ventana de Vision+.
4. **Windows 7/8.1**: el objetivo probado es Windows 10/11 con .NET Framework
   4.0+ y Edge/Chrome. En 7/8.1 haría falta una versión de Node y un navegador
   compatibles con esos sistemas.
5. **Un stream por relay**: igual que Xuper, hay un `current` en el relay; al
   abrir otro título se corta el anterior. Es correcto para un solo reproductor.
6. **Dependencia del portal**: hosts, sesión y CDN pueden rotar; el motor
   `xtv.js` ya concentra esa lógica.
7. **Legal**: no se redistribuyen credenciales ni catálogo de Xuper. Node (MIT)
   y FFmpeg (LGPL/GPL según build) se incluyen como terceros con sus licencias.

---

## 9. Decisiones tomadas

- **No duplicar la UI**: PC sirve `webos/app` tal cual. Así la paridad visual y
  funcional es real y no hay dos interfaces que mantener.
- **No duplicar el motor**: PC sirve `webos/service` tal cual. El modo PC es una
  bandera, no un fork.
- **No reutilizar `netflix.js` ni `magis.js`**: son la UI y el portal de Xuper;
  Vision+ tiene los suyos.
- **Sí reutilizar la mecánica del lanzador y del relay**: son las piezas que
  resuelven los problemas reales de Windows (ventana, bandeja, HLS, HEVC, seek).
- **Lanzador propio y más seguro**: no mata todos los `node.exe` del sistema;
  solo cierra el PID que dejó `data/port.json` y el propio servidor.
