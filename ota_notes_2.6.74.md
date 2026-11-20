# Vision+ 2.6.74 (beta)

## VOD: el primer cuadro ya no se corta a los 25 s

- En teles lentas o redes lentas el motor ya tenía la duración del video, pero el
  primer cuadro tardaba más de 25 s. El reproductor se reiniciaba a los 25 s y
  cortaba el buffer justo cuando estaba por entrar.
- Ahora, si el motor ya tiene duración (está buffereando), la ventana es de **45 s**
  y de **60 s** en equipos de 2 GB o menos. Si de verdad no llegó nada (sin
  duración), se mantiene el reintento rápido de 10 s.
- Si aun así no arranca, el respaldo baja a la **siguiente resolución**
  (720p/480p) en vez de probar otro 1080p del mismo origen. Así se evita el
  «Probando otro servidor…» que tampoco pintaba.
- El 4K manual, el chip sutil y el Inicio de la tele se mantienen igual.

## Notas

- Publicado **solo** en el canal `beta`; la estable sigue en 2.6.67.
- En redes muy lentas el primer cuadro puede tardar; el Inicio abre con lo
  guardado y se completa solo.
