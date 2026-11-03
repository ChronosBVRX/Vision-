# Third-party notices — webOS port

Este port reutiliza, con adaptaciones, piezas del paquete de XuperTV para webOS
aportado por el usuario (`com.webosflux.xuptertv.fidelity_1.5.8_all.ipk`):

- `service/proxy.js` — proxy HTTP local con cabeceras, Range y HLS virtual.
- `service/ts-indexer.js` — índice de segmentos MPEG-TS por byte range.
- `service/ffmpeg-relay.js` — relay de ffmpeg para remux TS -> MP4.
- `service/ffmpeg` — binario estático ARM de FFmpeg (GPL; ver
  `ffmpeg-LICENSE.txt` en el paquete original o la licencia de FFmpeg).
- `app/js/webOSTV.js` — librería oficial webOSTV.js del SDK de LG (Apache-2.0).

El resto del port (UI, cliente XTV, API VPS, empaquetado) es código nuevo de
Vision+ bajo Apache-2.0.

FFmpeg se distribuye bajo GPLv2/GPLv3 según su build; si se redistribuye el
`.ipk`, hay que conservar la licencia y la oferta de fuentes correspondiente.
Si no se desea distribuir el binario, borrar `webos/service/ffmpeg`: el modo
suave quedará desactivado y la reproducción directa seguirá funcionando.
