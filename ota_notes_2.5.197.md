# Vision+ 2.5.197 — hotfix de reproducción y hero

**Fecha:** 2026-10-30
**versionCode:** 437
**Launcher:** 1.3.13 / vc36

Esta release **reemplaza a la 2.5.196**, que se retira por un fallo grave: su APK se compiló sin inyectar el motor X4K y la reproducción fallaba con `ENGINE_LOAD_FAIL` («no se pudo extraer x4k/x4k-sdk-1.dex»).

## Correcciones

- **Motor X4K inyectado** como `classes3.dex`, `classes4.dex` y `classes5.dex`; el APK vuelve a reproducir VOD y TV en vivo. Verificado en el Chromecast HD.
- **Hero de la app principal**:
  - el arte queda recortado dentro de su contenedor (`clipToBounds`);
  - el zoom y el drift ya no invaden la barra superior ni las filas;
  - el arte ahora es monocromo y tiene una cortina horizontal/vertical más firme, sin colores crudos.
- **Juegos solo en la app principal** y **sala solo en modo TV**:
  - Vision+ Home 1.3.13 ya no muestra la pestaña Juegos;
  - el teléfono solo se une por QR/código;
  - el selector de la tele muestra las 7 tarjetas a la vez en rejilla 4×2.

## Backend

Sin cambios respecto a la 2.5.196: backend desplegado, catálogo de 7 juegos y smoke remoto correcto.

## Pruebas

- 1,136 pruebas Android en verde.
- 170 pruebas del launcher en verde.
- 542 pruebas backend en verde.
- Reproducción verificada en el Chromecast HD con el APK inyectado.

## Artefactos

- `VisionPlus.apk`: 91,465,246 B
  SHA-256 `a1fac994b31edbf928adee3de7b6f7c6dee037c3c17dbb15389fbd887dbce8e7`
- `VisionPlusHome.apk` (1.3.13): 1,525,204 B
  SHA-256 `04f87ef49619a426e5671ab5936c5b21fe4407c2fc0835cd2c9cb62d64af6362`
- Firma histórica `9593cb0d…` en ambos.
