# Vision+ 2.6.71 (beta)

## Inicio de la tele: el mando vuelve a recorrer todo

- **Abajo/Arriba ya no se queda clavado en una fila.** El Inicio vuelve a bajar del
  hero a la primera tarjeta y de ahí a la fila siguiente, y al subir regresa al banner
  y a la barra superior. El desplazamiento solo asoma la fila siguiente cuando vas
  hacia abajo; al subir ya no empuja la pantalla en sentido contrario.
- **El hero vuelve completo al subir.** Si el banner se había reciclado, la lista
  regresa al tope y el foco aterriza en «Reproducir» (o en la acción del partido).
- **El degradado cubre toda la pantalla.** Se quitó el velo rectangular que vivía
  dentro del hero y el degradado cinematográfico ahora ocupa todo el Inicio, sin
  bordes duros a media pantalla.
- El foco inicial sigue en la acción principal del hero y el D-pad conserva el
  regreso a la misma tarjeta al volver de una ficha.

## 4K dentro del reproductor nativo

- **Siempre entra en la máxima calidad del catálogo (1080p).** El 4K ya no se
  selecciona solo ni se reproduce al abrir: se resuelve en segundo plano y queda
  como una opción manual.
- **Chip «4K» sutil** arriba a la derecha, con el mismo lenguaje visual que los
  distintivos de HDR/Dolby. Si la fuente no trae español, dice «4K · ORIGINAL».
- **Selector de calidad unificado:** 1080p, 720p, 480p y 4K en el mismo menú. El
  cambio 1080p → 4K y 4K → 1080p funciona en ambos sentidos.
- **Audio y subtítulos del 4K:** se reutiliza la API de subtítulos del catálogo.
  Si el 4K no trae audio español, se activan los subtítulos españoles de la nube y
  el selector permite elegir cualquier pista disponible. Las pistas incrustadas del
  archivo siguen como respaldo.
- **Reparado el arranque nativo:** el 4K ya no viaja como primera pista al motor de
  Xuper; el reproductor vuelve a abrir el 1080p del catálogo y el 4K se elige aparte.

## Notas

- Publicado **solo** en el canal `beta`; la estable sigue en 2.6.67.
- El HEVC Main10 de algunas fuentes 4K puede no decodificar en equipos modestos;
  en ese caso el reproductor conserva el 1080p y el chip 4K queda como aviso.
