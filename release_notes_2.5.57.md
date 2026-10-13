# Vision+ v2.5.57 — Fix regresión Chromecast + resolve acotado

## Regresión Chromecast (reinicios en arranque)
- Causa: `X4KDownloadService` era `START_STICKY`: tras cualquier muerte del proceso
  `:x4k`, Android lo relanzaba solo = doble proceso en boot y muerte por LMK en
  Chromecast de 1.5 GB (el patrón v2.5.41).
- Fix: `START_NOT_STICKY` en todos los caminos. Si el sistema mata una descarga, el
  registro la deja en PAUSED (reconcile) y se reanuda con un tap. Nada arranca `:x4k`
  salvo tap explícito o reproducción.

## Resolve acotado (fin del 0% por minutos)
- `prepareVod` envuelto en `withTimeout(60s)` x2 intentos: con red mala, ERROR honesto
  ("Sin fuentes…") y reintento, en vez de 0% colgado varios minutos.

## Validación
- 206 unit tests en verde.
- En A32 por ADB: delegación headless al servicio OK; con red caída el fallo es
  honesto y rápido (antes colgaba minutos). Descarga completa probada en v2.5.55
  (A32 1.09 GB) y v2.5.56 (POCO 829 MB): este release no cambia ese camino.
- Reproducción intacta.
