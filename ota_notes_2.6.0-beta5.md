# Vision+ 2.6.0-beta5 — Migración del canal de actualizaciones

Esta beta conserva la base de **2.6.0-beta4** y solo cambia el canal de actualizaciones: la OTA
ahora apunta al repositorio público **Vision-Releases**. No hay cambios de interfaz ni de
reproducción.

## Qué cambia

- La app busca actualizaciones en `ChronosBVRX/Vision-Releases`.
- El launcher **Vision+ Home 1.3.17** también busca sus actualizaciones ahí.
- Se instala **encima de beta4 (vc489)** sin borrar datos, perfiles, favoritos, historial ni
  descargas.
- Firma histórica `9593cb0d…`, la misma de todas las versiones anteriores.

## Cómo actualizar

1. Abre Vision+ y espera el aviso de actualización.
2. Si no aparece, descarga e instala manualmente desde
   `https://github.com/ChronosBVRX/Vision-Releases/releases/latest`.
3. En la tele, instala también `VisionPlusHome.apk` del mismo release si quieres el launcher 1.3.17.

## Notas

- Versión: **2.6.0-beta5** (versionCode **490**).
- Launcher: **1.3.17** (versionCode **40**).
- Esta es una pre-release de pruebas; la estable sigue siendo **2.5.246** (vc486).

— ChronosBVRX
