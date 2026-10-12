# Vision+ v2.5.40 — Botón Siguiente Episodio y Reproducción Limpia en Series

### ⏭️ Botón Rápido de "Siguiente Episodio" en Reproductor
- **Acceso Inmediato en la Barra de Controles**: Se agregó la píldora interactiva `⏭ Sig. C#` (o `⏭ Sig. T#`) en la barra inferior de navegación del reproductor (`X4KPlayerAty`), colocada estratégicamente entre el botón de adelantar y el selector de capítulos.
- **Transición Fluida entre Temporadas**: Si el usuario finaliza o desea pasar al siguiente episodio y se encuentra en el último capítulo de la temporada actual, el botón carga automáticamente la siguiente temporada sin salir del reproductor ni interrumpir la experiencia.
- **Soporte de Botón Físico en Mandos Smart TV**: Conectado el evento de hardware `KEYCODE_MEDIA_NEXT` para cambiar de capítulo directamente desde los mandos a distancia con teclas multimedia.
- **Atenuación Visual Inteligente**: Al alcanzar el último capítulo de la serie o temporada final, el botón se atenúa (`alpha = 0.45f`) informando que se ha llegado al final del contenido disponible.

### 🎬 Erradicación del Diálogo "¿Reanudar o desde el principio?" en Series
- **Cero Fricción al Seleccionar Capítulos**: En series, novelas y anime, seleccionar un episodio (o presionar el botón de siguiente episodio) inicia SIEMPRE de manera limpia e inmediata desde el segundo `00:00`, eliminando el molesto diálogo de "¿Reanudar o desde el principio?".
- **Eliminación de Condición de Carrera en Motor Nativo**: Se resolvió la fuga donde el motor nativo de video (`X4KTitan`) conservaba la posición del episodio anterior mientras se resolvía la URL en segundo plano; ahora la reproducción previa se detiene inmediatamente (`X4KTitan.stop()`), se actualiza la identidad del episodio en memoria y se limpia cualquier entrada residual antes del arranque.
- **Preservación de «Seguir Viendo»**: Si el usuario pulsa deliberadamente sobre un episodio en la fila de «Seguir viendo» de la pantalla de inicio, la reproducción reanuda de forma directa en el minuto guardado sin desplegar cuadros de confirmación redundantes.

### 🛡️ Blindaje y Suite Anti-Regresiones
- 142 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%.
- Protección de Safe Area en Smart TV y visualización de barra de avance íntegras sin regresiones.
