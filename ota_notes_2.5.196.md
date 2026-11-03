# Vision+ 2.5.196 — Juegos solo en la app principal

> **RETIRADA (2026-10-30): APK sin motor X4K inyectado; la reproducción fallaba con ENGINE_LOAD_FAIL. Instalar 2.5.197.**

**Release:** https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.196

**Fecha:** 2026-10-30
**versionCode:** 436
**Launcher:** 1.3.13 / vc36

## Cambios

- **Los juegos viven únicamente en la app principal Vision+.** Se retiró la
  pestaña «Juegos» de Vision+ Home para no duplicar el acceso.
- El **modo TV** crea la sala desde `Inicio → Juegos`; los teléfonos solo se
  unen con QR o código. El teléfono ya no puede crear salas remotas.
- El selector de la tele muestra **las 7 tarjetas a la vez** en una rejilla
  4×2: Trivia, Charadas, Apuesta a la pista, El Impostor, Duelo de Dibujo Ciego,
  Subasta Ciega y Falso Conocimiento.
- La app incluye **Vision+ Home 1.3.13** como respaldo de instalación en TV.

## Backend

Desplegado y validado en el VPS:

- catálogo con los 7 juegos;
- creación/inicio de salas;
- `draw_partial`, `auction_bid` y `fibbage_lie` remotos;
- 542 pruebas backend en verde.

## Pruebas

- 1,136 pruebas Android en verde.
- 170 pruebas del launcher en verde.
- Release R8 firmado con la llave histórica `9593cb0d…`.
- Instalado en Chromecast HD; rejilla de 7 juegos y lobby de El Impostor
  verificados.

## Assets

- 15 assets de salón + 4 portadas nuevas.
- 48 voces Qwen3-TTS nuevas, 248 audios totales.
- 6 SFX con ducking.
- Manifiesto en `/media/chronos/Disco Duro/vision-assets/games/MANIFEST.sha256`.

## Artefactos

- `VisionPlus.apk`: 74,166,709 B, SHA-256 `130d4d5556a528e4c6b83f95fa321b2bfd33e3c66ab60ab330a27fc773d168a2`
- `VisionPlusHome.apk` (1.3.13): 1,525,204 B, SHA-256 `04f87ef49619a426e5671ab5936c5b21fe4407c2fc0835cd2c9cb62d64af6362`
- Release: `v2.5.196` (estable)

## Notas

- La app crea la sala desde la tele y el primer teléfono que entra es el
  anfitrión del ritmo.
- Pendiente: prueba física completa con 2–3 teléfonos.
