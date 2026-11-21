# Vision+ PC — ejecutable único (Electron)

Esta carpeta es la fuente del **portable de un solo `.exe`**. Electron aporta
Chromium y la ventana nativa; el resto es el mismo port de Smart TV:

- `app/` — UI de `webos/app` (la copia el script de build).
- `service/` — motor de `webos/service` (copia el script).
- `server.js` — adaptador HTTP de PC (copia el script).
- `runtime/ffmpeg.exe` — FFmpeg Windows (lo descarga el script).
- `main.js` — ventana, bandeja, datos portables y arranque del servidor.

## Construir

Desde Linux/macOS:

```bash
./pc/tools/build-single-exe.sh
```

Desde Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\pc\tools\build-single-exe.ps1 `
  -Ffmpeg "C:\ruta\ffmpeg.exe"
```

También se puede hacer a mano copiando `app/`, `service/` y `runtime/` a esta
carpeta y ejecutando:

```powershell
npm install
npx electron-builder --win portable --x64
```

Salida: `pc/build/VisionPlusPC-Portable.exe` (o `pc/electron/dist/` si se
ejecuta electron-builder desde esta carpeta).

## Datos

El portable de electron-builder define `PORTABLE_EXECUTABLE_DIR` con la carpeta
del `.exe`. `main.js` usa esa carpeta para:

```
VisionPlusPC-data/
├── session.json       sesión del portal
├── electron/          perfil Chromium (localStorage, caché)
├── server.log
├── launcher.log
└── port.json
```

Si la carpeta no se puede escribir (por ejemplo, Program Files), cae a
`%APPDATA%\Vision+ PC`.

## Ventana

- Cerrar con la X → oculta a la bandeja; la reproducción sigue.
- Clic/doble clic en la bandeja → restaurar.
- Menú de la bandeja: Abrir, Pantalla completa, Recargar, Carpeta de datos,
  Cerrar aplicación.
- `F` pantalla completa y `P` Picture-in-Picture dentro del reproductor.

## Pantalla de carga

El portable muestra un splash NSIS durante la extracción (`splash.bmp`) y luego
una ventana Electron con barra de progreso real (`splash.html`) que avanza
mientras arrancan el servidor, el catálogo y la interfaz. No se muestra la
ventana principal hasta llegar al 100 %.

## Deportes en vivo

- Al abrir una transmisión de Deportes, el reproductor muestra el **Match Center**
  estilo FIFA con marcador, escudos, estadísticas, goles/tarjetas y alineaciones.
- El botón **Partido** del OSD abre la tarjeta completa.
- Las estadísticas salen de ESPN (`matchStats`) y se cargan aparte, sin bloquear
  el video.
- Los enlaces `token_api` de GeeSports se resuelven con una ventana Electron
  oculta (páginas que solo publican el HLS cuando arranca su player). Los enlaces
  DASH con ClearKey pasan por FFmpeg con `-cenc_decryption_keys`.

## Pantalla completa

- La ventana abre directamente en pantalla completa.
- `Ctrl+F` entra o sale de pantalla completa; es el único atajo de salida.
- `Escape` sigue siendo «Atrás» dentro de la interfaz y no abandona la pantalla completa.
- Al iniciar aparece durante unos segundos un aviso discreto con el atajo.

## Mouse

- Mover el mouse dentro del reproductor muestra el OSD.
- El clic izquierdo sobre el video funciona como «Atrás»: oculta el OSD, o lo muestra si estaba oculto.
- La barra de progreso acepta clic y arrastre; muestra una vista previa del tiempo.
- La duración total sale del metadato o, si falta, de una sonda de FFmpeg.

## Selectores del reproductor

- **Calidad**, **Audio** y **Subtítulos** abren listas con las pistas reales de la
  transmisión; **Imagen** incluye las mejoras Cine, Nítido, Noche, Vivo, Cálido,
  Frío y Original, además de las proporciones Original, Rellenar, 16:9 y 4:3.
- La película siempre arranca en 1080p. Unos segundos después del primer cuadro se
  buscan fuentes 4K y aparecen en **Calidad** como una opción manual.
- El 4K se valida antes de ofrecerse. Si no carga o el equipo no lo decodifica, el
  reproductor vuelve solo a 1080p y oculta esa opción 5 minutos.
- En 4K se elige la pista de audio del archivo que coincida con español (o inglés)
  y se convierte a AAC para que Chromium pueda reproducirla.

## Actualizaciones OTA

- La app revisa `pc-version.json` del canal fijo `pc-latest` de GitHub Releases.
- Si hay una versión nueva, aparece un aviso discreto con **Actualizar**.
- La descarga se verifica con SHA-256 antes de instalar.
- Al instalar, un script de PowerShell espera a que Vision+ cierre y **reintenta**
  reemplazar el `.exe` hasta 60 s (Windows mantiene el portable bloqueado unos
  segundos). Verifica el tamaño, guarda un `.bak` y escribe `install.log`.
- Si no puede escribir en la carpeta o no encuentra el ejecutable, la app sigue
  abierta y ofrece **Descargar manualmente**.
- Para una transición desde una versión con el instalador viejo, usa una vez
  `pc/tools/Actualizar-VisionPC.bat` o sustituye el `.exe` a mano. Después la OTA
  funciona sola.
- Para publicar una versión nueva:

```bash
./pc/tools/build-single-exe.sh
./pc/tools/publish-pc-ota.sh
```

En Windows:

```powershell
powershell -ExecutionPolicy Bypass -File .\pc\tools\publish-pc-ota.ps1
```

El canal `pc-latest` es una pre-release, así que **no reemplaza** el canal estable de Android.
