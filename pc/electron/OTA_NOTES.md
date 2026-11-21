# Vision+ PC 0.8.1 — Instalador OTA corregido

Actualización del ejecutable portable de Vision+ PC.

## Qué se corrigió

- El instalador anterior esperaba solo **0.9 s** a que Windows liberara el `.exe`. El
  lanzador portable mantiene el archivo bloqueado unos segundos más, la copia fallaba
  y se volvía a abrir la versión vieja: el ciclo de «Actualizar → Instalar → reiniciar».
- El instalador nuevo **espera y reintenta** el reemplazo hasta **60 segundos**, comprueba
  que el tamaño copiado coincida y solo entonces abre la aplicación.
- Antes de reiniciar se cierra el servidor local para que el proceso termine de verdad.
- Se conserva un respaldo `VisionPlusPC-Portable.exe.bak` y la actualización descargada
  ya no se borra al instante. Cada intento queda en `VisionPlusPC-data/update/<versión>/install.log`.
- Si la carpeta no permite escribir o no se encuentra el ejecutable, la app **no se cierra**:
  muestra el motivo y un botón **Descargar manualmente**.

## Importante para esta primera transición

El instalador roto vive en la 0.8.0 y anteriores, así que esta vez puede que el aviso
automático siga fallando. Para pasar a la 0.8.1 una sola vez:

1. Descarga `Actualizar-VisionPC.bat` y ponlo junto a `VisionPlusPC-Portable.exe`.
2. Ciérralo o deja que el .bat lo cierre.
3. Ejecuta el .bat y espera. Descarga la última versión, la reemplaza con reintentos y abre Vision+.

También puedes descargar `VisionPlusPC-Portable.exe` y sustituir el archivo manualmente
(cierra Vision+ y espera unos segundos antes de copiar).

Después de esta instalación manual, las siguientes OTA ya se aplican solas.

## Se mantiene de la 0.8.0

- 4K manual con arranque en 1080p, fuentes verificadas y vuelta automática a 1080p.
- Selectores de Calidad, Audio, Subtítulos e Imagen con listas reales.
- Imagen con mejoras Cine, Nítido, Noche, Vivo, Cálido, Frío y Original, y proporciones
  Original, Rellenar, 16:9 y 4:3.
- TV en vivo, Deportes, catálogo, fichas, favoritos, historial y ajustes.
