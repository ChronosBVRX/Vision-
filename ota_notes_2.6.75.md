# Vision+ 2.6.75 (beta)

## Arranque del Play más rápido

- La ficha adelanta el handshake de reproducción y ese trabajo **sigue vivo aunque
  el usuario cambie de pantalla**. Al pulsar Play se espera ese mismo trabajo en vez
  de lanzar un segundo portal, que en una tele lenta duplicaba la espera.
- El cruce 4K se hace **después** del handshake, no al pulsar Play: su red ya no
  compite con el arranque del 1080p.
- La licencia adelantada dura ahora **5 minutos** (antes 90 s), para quien se queda
  leyendo la ficha antes de reproducir.
- En equipos de 2 GB o menos se salta el fundido del primer cuadro, que añadía casi
  un segundo a la espera.
- El catálogo, el 4K manual y el Inicio de la tele se mantienen igual.

## Notas

- Publicado **solo** en el canal `beta`; la estable sigue en 2.6.67.
- Si el primer cuadro tarda por red, el respaldo de calidad de 2.6.74 sigue activo:
  espera el buffer y, si no arranca, prueba 720p/480p.
