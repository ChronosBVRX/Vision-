# Runtime para Vision+ PC

Esta carpeta se llena al compilar en Windows:

- `node.exe` — Node.js x64 (MIT). El script `tools/build-windows.ps1` lo
  descarga desde `nodejs.org/dist/<versión>/win-x64/node.exe`.
- `ffmpeg.exe` — FFmpeg x64 (LGPL/GPL según build). Se descarga del paquete
  `ffmpeg-master-latest-win64-gpl.zip` de BtbN por defecto.
- `NODE-LICENSE.txt`, `FFMPEG-LICENSE.txt` — licencias de los binarios.

No se versionan los `.exe` (ver `../.gitignore`). Para compilar sin internet,
copia aquí `node.exe` y `ffmpeg.exe` antes de ejecutar el empaquetador.
