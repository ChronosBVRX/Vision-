# Vision+ v2.5.51 — Offline sin crashes: descargas en `:x4k` + episodios reales

## Qué se arregló
- **Crash al descargar en móvil (el que reportaste):** el gestor intentaba bootear el motor
  Ranger en el proceso principal, donde ya vive Magis → crash nativo, 0% al volver y crash
  al reintentar. El principal ahora JAMÁS toca Ranger: solo descarga http directo o delega.
- **Episodios que no descargaban:** las fichas llamaban `enqueueDownload(film)` sin capítulo;
  las series intentaban el `seriesId` y fallaban. Ahora descargan el mismo C# objetivo que
  el botón Reproducir (móvil y TV).

## Cómo quedó
- Nuevo `X4KDownloadService` (`android:process=":x4k"`, foreground `dataSync`): resuelve el
  stream real (`prepareVod` + `resolveVodStream` instance=1/exo), descarga MP4/TS/HLS y cifra
  al búnker, reportando progreso al principal vía broadcasts.
- `OfflineHttpDownloader` común (sin nativo, seguro en cualquier proceso).
- Reproducción intacta: no se tocó `X4KPlayerAty`, `PlayerScreen`, `TitanSdk` ni `MagisTitan`.

## Prueba en tu móvil
1. Instala la OTA, abre una película → Descargar → debe avanzar % real sin crash.
2. Abre una serie → Descargar → debe bajar el capítulo objetivo (T#/C# en Descargas).
3. Mata la app a mitad de descarga → al volver queda en Pausa (reanuda, no 0% fantasma).

## Validación
- 184 unit tests Android en verde (178 previos + 6 guardianes `OfflineDownloadDispatchTest`).
- Guardianes: el principal no importa `X4KTitan` ni llama `prepareVod`; series usan el code del C#.
