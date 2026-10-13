# Vision+ v2.5.55 — Descargas offline verificadas en dispositivo real

## Verificado en A32 (root, USB) el 2026-10-12
- **Descarga completa real:** "El sorprendente Hombre Araña 2" → 1,166,536,428 bytes
  cifrados en `.vpack`, estado `COMPLETED`, ~1.9 MB/s estables, cero crashes.
- **Reproducción offline verificada:** el `.vpack` se ve y se escucha (descifrado
  al vuelo + ExoPlayer).

## Causa raíz del "sigue sin descargar" (v2.5.51/52)
1. `GetStatus {"instance":0,"name":…}` vía `NativeJni.a` devuelve vacío siempre
   (probado hasta con el video reproduciéndose): esa vía de sondeo no existe en el
   proveedor, fue una suposición.
2. La dirección real vive en el bean de estado `uc.c` (`p(0)` → `d()` = play_url):
   proxy local `http://127.0.0.1:<puerto>/vod/0/<code>.m3u8` (puerto dinámico).
3. La vía `NativeJni.o/g` directa en instance=1 (v2.5.50) era el SIGSEGV de
   `star_file_cache.cpp` (slot 1 sin inicializar): eliminada en v2.5.52.

## Fix (reproducción intacta)
- `X4KTitan.playHeadless`: arranca vía facade `x(0, code, program, "vod")`, la misma
  llamada que `ed.e.A()` hace al abrir (probado: sin crash, sin vista, sin audio).
- `X4KTitan.beanPlayUrl` + `queryStatusUrls`: leen `d()` del bean (primario) con
  fallback al `Call` nativo.
- `stopHeadless` espeja `B()` (`H(0, code)`); el servicio conserva el guard
  "reproducción en curso" (un programa por proceso).
- Sondas `DlProbe` retiradas del reproductor (cero restos en su camino).

## Validación
- 204 unit tests Android en verde, 0 fallos (incluye guardianes que prohíben
  reintroducir `resolveVodStream`/`stopBackgroundVod`/vistas en el servicio).
- Flujo probado en A32 con logcat: delegación → boot `:x4k` → `playHeadless` →
  bytes cifrados → `COMPLETED` → play offline OK.
