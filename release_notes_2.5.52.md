# Vision+ v2.5.52 — Offline por la vía del reproductor (fin del SIGSEGV)

## Qué mostraba tu prueba (gracias por las capturas)
- El crash ya NO es en el principal: es en `:x4k` (`Cmdline: ...:x4k`, `signal 11
  SIGSEGV` en `libranger-jni.so`, `fault addr 0x18`, `star_file_cache.cpp:295`).
- La descarga se quedaba en `Descargando 0% · Conectando stream…` (Dune 2) y luego el
  diálogo de crash del sistema.

## Causa
- La resolución en segundo plano llamaba `NativeJni.o/g` directo (instance=1, modo
  `exo`, `programJson` manual). Esa secuencia salta la inicialización que la vista
  del reproductor sí hace → `resume block cache` con 0 archivos → null deref → SIGSEGV.
  Nativo: inatrapable desde Kotlin, mata el proceso `:x4k`.

## Fix (reproducción intacta, cero cambios en su camino)
- **Eliminada** la vía `NativeJni.o/g/u` de `X4KTitan` (`resolveVodStream`,
  `stopBackgroundVod` borrados).
- El servicio `:x4k` ahora resuelve por la **vía del reproductor**: vista headless
  `ed.e` + el mismo `X4KTitan.play(view, "vod", …)` que usas al ver, y sondeo
  **read-only** de `GetStatus` (`queryStatusUrls`, no modifica nada) hasta ver la
  `play_url` http. Sin URL en 25 s → error honesto, jamás crash.
- Si hay reproducción en curso, la descarga lo dice (`Reproducción en curso: pausa
  el video e inténtalo de nuevo`) en vez de pelear por el motor (una sola vista
  activa por proceso).
- El principal sigue sin tocar Ranger (blindaje v2.5.51 intacto).

## Prueba
1. Sin nada reproduciendo: película → Descargar → % real hasta ✓.
2. Serie → Descargar → baja el C# objetivo.
3. Con video en play: Descargar → mensaje de reproducción en curso (sin crash).

## Validación
- 188 unit tests Android en verde (0 fallos), incluyendo guardianes que prohíben
  reintroducir la vía nativa directa.
