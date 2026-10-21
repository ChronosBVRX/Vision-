# Vision+ 2.5.116 — el Modo Sin Conexión se abre solo y el reproductor solo ofrece lo que el video tiene

- **Sin conexión, la app abre directo «Vision+ Offline».** Al quedarse sin red (o al arrancar sin internet) ya no se intenta cargar el catálogo para terminar en un Inicio vacío: se muestra la interfaz sin conexión con tus descargas y el botón para reintentar. Al volver la red, la app regresa sola a su pantalla normal.
- **Un router sin internet también cuenta como «sin conexión».** Si el Wi-Fi está conectado pero el cable/módem está caído, el sistema no valida la red: la app hace una sonda corta de salida y, si no hay internet real, abre el Modo Sin Conexión en vez de quedarse esperando.
- **El reproductor ya no interrumpe una película o un canal en vivo** por una caída momentánea de la red: el propio reproductor maneja sus reintentos. El Modo Sin Conexión aparece al volver al menú.
- **El botón «Reintentar conexión» ahora avisa** cuando todavía no hay internet (antes parecía que no hacía nada).
- **Opciones honestas en el reproductor.** La píldora de audio solo aparece si el video trae **más de un idioma**; la de subtítulos, solo si el programa trae subtítulos; y la de calidad, solo si hay **más de una resolución**. Con una sola opción el botón se oculta: se acabaron los menús vacíos o de una sola entrada.
- **Mismo criterio en el reproductor en línea (ExoPlayer).** El menú «Audio, subtítulos y calidad» se arma con las pistas reales del video: idiomas de audio, subtítulos y las resoluciones que existen. Se retiró el menú de relleno («Servidor» falso, «Original», «este archivo no trae subtítulos»).
- **El mando sigue bien:** cuando una opción no existe, el foco del D-pad salta solo entre las píldoras visibles, sin destinos muertos.
- Guardianes nuevos: 13 pruebas del Modo Sin Conexión (cuándo se abre, reproductores que no se interrumpen, sonda de salida, cableado en móvil y TV) y 11 de las opciones honestas (audio/subtítulos/calidad, menú del reproductor en línea y cableado). Suite: **706/706 Android** en verde; release R8 compila limpio.

— ChronosBVRX
