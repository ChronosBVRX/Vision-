# Vision+ v2.5.56 — Descargas estables + no más re-descargas fantasma

## 1. Estabilidad bajo carga (POCO, verificado por ADB)
- Se probó HLS en paralelo (4 conexiones): el proxy local de Ranger sirve UN cliente;
  con 4 conexiones responde `HTTP 404` / cortes y mata la descarga. Revertido a
  secuencial (el que completó 1.09 GB en A32).
- El cuello real es el upstream P2P del proveedor (~2 MB/s): 1 GB ≈ 9 min. Acelerar
  de verdad exige handshake directo con el CDN (línea futura, no este release).
- Blindajes que sí quedan: directorio de partes único por corrida, guard anti-doble
  corrida por id, `mkdirs` defensivo antes de escribir.

## 2. Fin del "0% atorado" / re-descarga de lo ya descargado (POCO)
- Causa: si la app moría al completar, el broadcast de fin se perdía y el registro
  quedaba colgado; al reintentar re-descargaba desde cero.
- `OfflineRegistry.reconcileWithDisk` al iniciar: `.vpack` válido en disco → se adopta
  como COMPLETED (con bytes reales); fantasma COMPLETED sin archivo → se descarta;
  DOWNLOADING/QUEUED → PAUSED. Constante única `MIN_VALID_VAULT_BYTES`.
- Reproducción intacta (solo offline + registro).

## Validación
- 207 unit tests Android en verde (nuevos: reconcile adopta/descarta + estimado HLS).
- Medición real en POCO F7 por ADB antes/después.
