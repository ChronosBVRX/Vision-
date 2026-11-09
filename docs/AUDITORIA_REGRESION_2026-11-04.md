# Auditoría de la regresión 2.5.247 y restauración 2.6.0

**Fecha:** 2026-11-04  
**Motivo:** la estable puente 2.5.247 (vc491) se publicó con un árbol reducido y perdió
mejoras que ya estaban en la beta6 (vc491). La nueva estable **2.6.0 (vc492)** se compila
desde el árbol completo y las restaura todas.

## Cómo se verificó

Se descargaron los APK públicos de `Vision-Releases` y se inspeccionaron con
`aapt2 dump resources` y `strings` sobre los DEX:

- Estable 2.5.247/vc491: `VisionPlus.apk` de 94,916,199 B.
- Beta6 2.6.0-beta6/vc491: `VisionPlus.apk` de 95,685,572 B.
- Árbol actual (base de 2.6.0/vc492).

## Regresiones encontradas en 2.5.247

| Mejora | 2.5.247 | Beta6 / árbol actual |
|---|---|---|
| Avatares de personajes | **0 recursos `profile_avatar`** | **60 recursos** (20 cine, 20 series, 20 anime) |
| Teclado en pantalla de Perfiles | Sin `Teclado del sistema` ni `Teclado en pantalla` | Presente |
| Capítulos vistos por porcentaje | Sin `watched_episodes_manual` / `watched_episodes_unwatched` | Presente (85 %, OK largo, barra de avance) |
| Idioma/Subtítulos siempre visibles | Sin `Idioma solo disponible` | Presente, con «Sin subtítulos» |
| Hero del calendario editorial (beta4) | Sin `evento-` ni `tema-para-reir` | Presente (`Popularity.kt`) |
| Descargas portátiles USB con PIN | Presente (`pvpack`, «Biblioteca portátil») | Presente |
| OTA en `Vision-Releases` | Presente | Presente |

Además, la 2.5.247 usó **versionCode 491**, el mismo que la beta6. Por eso los equipos en
beta6 no recibían la oferta de actualización y los equipos estables sí recibían un APK sin
las mejoras.

## Restauración en 2.6.0

La 2.6.0/vc492 se compila desde el árbol actual e incluye:

- Los **60 personajes originales** de Perfiles y el teclado en pantalla con foco corregido.
- Los **capítulos vistos al 85 %**, OK largo y barra de porcentaje.
- El **OSD** con título visible, degradados hasta los bordes, Idioma/Subtítulos siempre
  visibles y botones discretos.
- El **calendario editorial** y las filas de campaña del beta4.
- Las **descargas portátiles USB con PIN**.
- La **OTA nueva** en `ChronosBVRX/Vision-Releases`.

La actualización es **obligatoria**: `min_version_code = 492` y
`migration_version_code = 492`; cualquier build anterior muestra «Actualización requerida»
y no puede cerrar el aviso hasta instalar la 2.6.0. La firma histórica `9593cb0d…` se
mantiene, así que se instala encima sin perder perfiles, historial, favoritos, descargas ni
ajustes.

## Publicación

- Estable: `v2.6.0` (latest) en `ChronosBVRX/Vision-Releases`.
- APK: `VisionPlus.apk`.
- Launcher: `VisionPlusHome.apk` 1.3.17/vc40.
- `version.json` con `2.6.0` / `versionCode 492`.
- La beta6 se conserva como pre-release `beta`; la estable 2.6.0 es más nueva y también la
  reciben los equipos del canal de pruebas.
