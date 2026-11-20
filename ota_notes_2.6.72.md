# Vision+ 2.6.72 (beta)

## El 4K deja de viajar en la lista del catálogo

- **Siempre entra en 1080p y rápido.** El catálogo de Xuper ya viaja al motor
  **solo con sus pistas nativas**. El 4K se manda aparte, como una opción, y el
  reproductor lo agrega a la lista de resoluciones **después** de arrancar en la
  máxima calidad del catálogo (1080p). Se conserva el arranque rápido.
- **Verificación y cruce dentro del reproductor.** Mientras el 1080p ya se está
  viendo, el resolvedor consulta la fuente 4K en segundo plano. Cuando la
  encuentra, aparece el **chip sutil «4K» / «4K · ORIGINAL»** y la entrada
  **4K** en el selector de calidad. Nada se cambia solo.
- **El aviso de calidad ya dice 1080p.** Antes el 4K iba al frente de la lista y
  el aviso podía anunciar «Calidad: 2160p» aunque el reproductor arrancara en
  1080p. Ahora el aviso refleja la calidad nativa real.
- **El 4K sigue siendo manual:** 1080p · 720p · 480p · 4K en el mismo selector,
  con audio y subtítulos. Si el 4K no trae español, se activan los subtítulos
  españoles de la API del catálogo y el chip dice «4K · ORIGINAL».
- **Si el 4K falla, no se queda en negro:** ante un códec incompatible, red cortada
  o URL vencida, el reproductor vuelve solo al 1080p nativo y deja el 4K disponible
  para reintentar.

## Inicio de la tele (se mantiene lo de 2.6.71)

- D-pad completo: hero → primera fila → filas y de vuelta, sin quedarse abajo.
- Degradado del hero a pantalla completa, sin el rectángulo a media altura.
- El hero vuelve completo al subir y el foco inicial sigue en «Reproducir».

## Notas

- Publicado **solo** en el canal `beta`; la estable sigue en 2.6.67.
- El HEVC Main10 de algunas fuentes 4K puede no decodificar en equipos modestos;
  en ese caso el 1080p sigue disponible y el chip 4K queda como aviso.
