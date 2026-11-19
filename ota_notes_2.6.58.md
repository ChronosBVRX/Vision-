# Vision+ 2.6.58 — Versión unificada de OTA

Esta versión se publica en **todos los canales de actualización** para dejar una sola versión instalada en todos los equipos: `ChronosBVRX/Vision-` (canal anterior) y `ChronosBVRX/Vision-Releases` (canal oficial, estable y beta).

## Qué incluye

- Todo el árbol de la **2.6.57**: barra y Hero TV responsivos, Deportes priorizado y marcadores ESPN corregidos.
- La app queda apuntando exclusivamente a **`ChronosBVRX/Vision-Releases`** para futuras actualizaciones (`/releases/latest` y `version.json`).
- Se instala encima sin borrar perfiles, historial, favoritos, "Ver más tarde", descargas ni ajustes.

## Por qué

Existían equipos en versiones distintas según el canal por el que recibían la OTA. Unificarlos en 2.6.58 (versionCode 554) evita mezclar manifiestos y deja una sola ruta de actualización antes del cierre del canal anterior.

## Verificación

- Firma histórica `9593cb0d…` en `VisionPlus.apk` y `VisionPlusHome.apk`.
- `version.json` con `versionCode=554` en todos los canales.

— ChronosBVRX
