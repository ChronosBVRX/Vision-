# Vision+ 2.6.73 (beta)

## Catálogo de vuelta

- Se revierte el cambio de 2.6.72 que sacaba el 4K de la lista del catálogo.
  Volvemos a la ruta de **2.6.71**, la última beta que abría el catálogo en la tele.
  El reproductor sigue arrancando en **1080p** y el 4K se mantiene como opción manual.
- **Arranque rápido en 1080p:** el motor recibe el catálogo nativo y empieza a
  reproducir de inmediato; el 4K no se selecciona solo.
- **El aviso de calidad ya dice 1080p.** El 4K puede venir en la lista interna, pero
  el reproductor lo filtra y el aviso muestra la calidad nativa real.
- **Si el 4K falla** (códec incompatible, red o URL vencida), vuelve solo al 1080p
  nativo y deja el 4K disponible para reintentar.

## 4K manual

- Chip sutil **«4K» / «4K · ORIGINAL»** cuando hay una fuente disponible.
- El 4K aparece solo como opción en **Calidad**: 1080p · 720p · 480p · 4K.
- Audio y subtítulos del 4K con la misma API de subtítulos del catálogo; si no hay
  audio español, se activan los subtítulos españoles y el chip lo indica.

## Inicio de la tele

- D-pad completo: hero → primera fila → filas y de vuelta, sin quedarse abajo.
- Degradado del hero a pantalla completa, sin el rectángulo a media altura.
- El hero vuelve completo al subir y el foco inicial sigue en «Reproducir».

## Notas

- Publicado **solo** en el canal `beta`; la estable sigue en 2.6.67.
- Si el catálogo no abre después de esta actualización, el problema está en la
  sesión o en la red de la tele; reinicia la app y la sesión, y si sigue, envía el
  diagnóstico para revisar la conexión.
