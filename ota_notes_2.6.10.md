# Vision+ 2.6.10 — Salida limpia del reproductor y WebView

Actualización estable sobre la 2.6.9. Mantiene catálogo, perfiles, descargas, Juegos y la optimización de hardware.

## Reproductor

- Se añadió un **guardián de salida**: si el motor nativo se bloquea al detener la reproducción, el proceso del reproductor se cierra solo después de 2 segundos en lugar de dejar la pantalla congelada.
- El cierre ahora detiene el motor, guarda el progreso, limpia temporizadores y libera la vista antes de volver.
- Mejor manejo de memoria y liberación del WebView en la sección Juegos al salir.

## Juegos

- La sala Smart TV mantiene QR, código, configuración, packs, reglas y jugadores en una sola pantalla.
- D-pad funcional en tarjetas, opciones, reglas y botones.
- Las 13 portadas temáticas generadas con IA local se mantienen en el selector y en el host.

## Optimización

- Modo ligero automático en TV/equipos de 2 GB: 30 FPS, menos efectos, `prefers-reduced-motion`, DPR 1 y prioridad de render del WebView.

## Pruebas

- Android: **1,219 pruebas**, 0 fallos, 3 omitidas.
- Backend: **538 pruebas**.
- Juegos: **13/13** inician ronda.

## Cómo actualizar

Desde **Ajustes → Buscar actualizaciones**, o instalando el APK de este release encima de la versión actual. No borra datos, perfiles, favoritos, historial, descargas ni ajustes.

— ChronosBVRX
