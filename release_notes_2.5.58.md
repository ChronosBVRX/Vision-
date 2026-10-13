# Vision+ v2.5.58 — Progreso en vivo + notificación con %

## Progreso que sí avanza (POCO)
- Causa: en POCO/HyperOS los broadcasts `:x4k`→principal no siempre se entregan
  (emisor y receptor verificados vivos por ADB, historial con envíos, cero recepciones).
  El disco crecía pero la UI quedaba en 0% hasta el COMPLETED.
- Fix: puente por archivo — el servicio escribe `<id>.progress` (bytes/total, 1/s) en
  el vault y el principal lo refleja cada 2 s (`OfflineProgressSync`, centralizado en
  `VisionApp`). Los broadcasts siguen como vía primaria (A32). Limpieza del sidecar al
  completar/fallar/cancelar.

## Notificación persistente con %
- La notificación de descarga ahora lleva el % en el título
  ("Vision+ · Descargando 42%"), categoría progreso, visibilidad pública y
  comportamiento inmediato (sin diferido de 10 s en Android 12+).

## Validación
- 207 unit tests en verde (nuevo: roundtrip del sidecar + adopción en `syncOnce`).
- Verificado en POCO por ADB: % moviéndose en Descargas + notificación con %.
- Reproducción intacta.
