# Vision+ PC 0.8.4 — Audio en vivo estabilizado

Actualización del ejecutable portable de Vision+ PC.

## Qué cambió

- El relay en vivo normaliza el audio antes de entregarlo al reproductor:
  - Fuerza una salida constante de **AAC estéreo a 48 kHz**.
  - Recalcula los timestamps del audio (`aresample` + `asetpts`) para que no
    queden tramos superpuestos o repetidos.
  - Elimina la generación automática de timestamps que podía duplicar tramos
    cuando la fuente cambiaba de segmento.
  - Ajusta el mezclado para que el audio no se reinicie al cambiar de fragmento.
- Se mantiene una sola pista de audio por transmisión.

## Si una transmisión sigue repitiendo la narración

Eso ya sería del propio flujo de origen: algunos eventos entregan un tramo de
audio duplicado en su playlist. En ese caso, prueba otra señal del mismo evento
desde el selector. Si me pasas el nombre del evento y de la señal, puedo revisar
ese flujo en concreto.

## Se mantiene

- Instalador OTA con reintentos.
- Agenda deportiva estable y rechazo limpio de páginas que no se pueden resolver.
- 4K manual, selectores de calidad/audio/subtítulos/imagen y reproducción 1080p.
- Interfaz sin nombres de fuentes.
