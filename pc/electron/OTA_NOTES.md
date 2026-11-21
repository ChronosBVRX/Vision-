# Vision+ PC 0.8.2 — Agenda deportiva y audio

Actualización del ejecutable portable de Vision+ PC.

## Enlaces de la agenda deportiva

- Se resolvieron los enlaces que entregan su señal en el HTML de la página
  (HLS/DASH) y los que apuntan directo al manifiesto.
- Las páginas que solo publican la señal dentro de su reproductor web y que
  bloquean o cuelgan Electron ahora se rechazan con un mensaje claro
  («no está disponible en PC, prueba otra señal») en lugar de dejar la app
  esperando o congelada.
- Ya no se aceptan segmentos `.ts` ni videos `.mp4` sueltos como si fueran la
  transmisión: eran avisos, intros o anuncios que duraban dos segundos.

## Audio raro / bucle

- Se descartan los manifiestos de anuncios, intros y pre-roll.
- El relay en vivo usa timestamps limpios (`+genpts`, `avoid_negative_ts`) y el
  elemento de video ya no puede quedar en bucle.
- El proceso principal ya no escribe a un stdout cerrado. El lanzador portable
  cerraba la consola y los logs del relay podían bloquear el proceso; ahora el
  diagnóstico va solo a `VisionPlusPC-data/server.log`. Esto también evita que
  el reproductor se quede sin audio o se reinicie solo durante un partido.

## Se mantiene

- Instalador OTA con reintentos de la 0.8.1.
- 4K manual y selectores de calidad, audio, subtítulos e imagen.
- TV en vivo, Deportes, catálogo, fichas, favoritos, historial y ajustes.
