# Vision+ PC (portable para Windows)

Port de escritorio de Vision+ que reutiliza **la misma interfaz de Smart TV** y
**el mismo motor** del port de webOS. Añade lo necesario para que funcione en un
PC Windows sin instalar nada y sin emulador.

La UI del `.exe` se reconstruyó contra la app real de Smart TV (capturas del
Chromecast): barra superior global con menús, Inicio, TV en vivo con EPG,
Deportes con agenda y selector de transmisiones, y OSD del reproductor.
Comparativas en `docs/screenshots/pc_reference_*.png` y `pc_current_*.png`.

> Análisis del port de XuperTV PC y decisiones: `docs/PC_PORT_XUPER_ANALISIS.md`.
>
> **Importante para git:** este port reutiliza `webos/`. En el árbol actual
> `webos/` todavía no está trackeado (`?? webos/`), así que hay que incluirlo
> junto con `pc/` en el mismo commit/entrega; si solo se copia `pc/`, no hay
> interfaz ni motor.

## Arranque rápido (Windows)

### Opción A — un solo ejecutable portable (recomendado)

```bash
./pc/tools/build-single-exe.sh
# salida: pc/build/VisionPlusPC-Portable.exe
```

Ese archivo **contiene todo**: Chromium/Electron, la UI de Smart TV, el motor,
`server.js` y FFmpeg. No requiere Edge/Chrome, Node ni FFmpeg en la PC destino.
Los datos (sesión, caché y perfil) se guardan junto al `.exe` en
`VisionPlusPC-data/`.

Salida: `pc/build/VisionPlusPC-Portable.exe` (~150 MB).

En Windows también se puede construir el mismo `.exe` con:

```powershell
cd pc\electron
npm install
npx electron-builder --win portable --x64
```

### Opción B — carpeta portable con Edge/Chrome

```powershell
cd pc\tools
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1
```

Salida:

```
pc\build\VisionPlusPC\
├── VisionPlusPC.exe       lanzador con bandeja
├── INICIAR-VISIONPLUS.bat respaldo sin lanzador
├── server.js
├── app\                   UI de Smart TV (webos/app)
├── service\               motor (webos/service)
├── runtime\               node.exe + ffmpeg.exe
└── data\                  sesión, caché, perfil, logs
```

Ejecuta `VisionPlusPC.exe` o copia la carpeta a una USB.

> `pc/launcher/VisionPlusPC.exe` ya viene precompilado (Mono 6.12, .NET
> Framework 4.0). El empaquetador lo recompila con `csc` si está disponible.

## Pantalla de carga

Al abrir el `.exe` aparece primero un splash NSIS (mientras se extrae el
portable) y después un splash Electron con barra de progreso real (0–100 %):
inicia motor, prepara catálogo/reproductor y abre la interfaz. Así el usuario
sabe que la app está trabajando incluso si el primer arranque tarda.

## Qué hace el lanzador

> El `.exe` único usa Electron y hace lo mismo de forma nativa; no necesita el
> lanzador .NET ni Edge/Chrome. La explicación siguiente es para la opción B.

- Arranca `runtime\node.exe server.js` en segundo plano.
- Espera `data\port.json` y `/ping`.
- Abre Edge/Chrome con `--app` en una ventana limpia de 1280×720.
- Al pulsar la `X` o `Alt+F4`, **oculta la ventana a la bandeja**; la
  reproducción continúa.
- Doble clic en la bandeja restaura; clic derecho → **Cerrar aplicación**
  detiene navegador y servidor.
- Si el puerto 8910 está ocupado, el servidor prueba 8911, 8912… y el lanzador
  lee el puerto real.

## Reproducción en PC

Chrome/Edge no reproducen HLS ni MPEG-TS de forma nativa, y Windows no siempre
tiene el códec HEVC. Por eso `pc/server.js` activa `VISION_PC=1` y el motor:

- manda **TS/HEVC/HLS** al relay FFmpeg;
- el relay entrega **MP4 fragmentado H.264/AAC**;
- el reproductor reabre el relay en `?start=<segundos>` al buscar y suma el
  offset al tiempo mostrado.

Los MP4 H.264 que el portal ya entregue siguen yendo por el proxy normal con
Range y seek nativo.

## Atajos

| Tecla | Acción |
|---|---|
| Flechas | navegar (D-pad) |
| Enter | seleccionar / reproducir |
| Escape / Retroceso | volver / cerrar reproductor |
| `F` | pantalla completa |
| `P` | Picture-in-Picture |
| Espacio | pausa / reproducir (en el reproductor) |
| ← / → | −10 s / +10 s (en el reproductor) |
| Clic en la barra | saltar al minuto seleccionado |

## Desarrollo en Linux/macOS

```bash
./pc/tools/run-dev.sh
# http://127.0.0.1:8910/?platform=pc
```

`run-dev.sh` usa `webos/app` y `webos/service` directamente; no necesita el
empaquetado.

## Pruebas

```bash
# requiere ffmpeg en el PATH e internet (VPS + portal)
./pc/tools/smoke-test.sh
```

Comprueba `/ping`, `/home`, `/resolve` y que el relay entregue H.264.

## Estructura

```
pc/
├── server.js              HTTP local + modo PC
├── LEEME-PC.txt           instrucciones para el usuario
├── launcher/
│   └── VisionPlusPC.cs    lanzador .NET (bandeja + hooks Win32)
├── electron/
│   ├── main.js            ventana/bandeja del ejecutable único
│   └── package.json       build electron-builder -> portable .exe
├── tools/
│   ├── build-windows.ps1  carpeta portable + ZIP (Edge/Chrome)
│   ├── build-single-exe.sh un solo .exe (Electron + FFmpeg)
│   ├── run-dev.sh         servidor de desarrollo
│   └── smoke-test.sh      prueba de humo
└── build/                 salida (ignorada por git)
```

## Notas

- No se copia nada del catálogo ni de la UI de XuperTV.
- El lanzador no mata todos los `node.exe` del sistema: solo el PID registrado
  en `data/port.json`.
- Node.js se distribuye bajo MIT y FFmpeg bajo LGPL/GPL según el build; el
  empaquetador copia sus licencias a `runtime/`.
