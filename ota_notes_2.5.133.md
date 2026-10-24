# Vision+ 2.5.133

**La tele deja de pelearse consigo misma al reproducir (TCL BeyondTV, 2 GB)**
- **Índice ligero en Smart TV**: en tele o equipo modesto se piden **24 títulos por fila** en vez de 100. En una TCL se midió **12.4 s** solo para leer el índice guardado (`cacheMs=12436`); con un cuarto del JSON el parseo baja en la misma proporción. El catálogo completo sigue disponible: las secciones paginan con «Ver más».
- **El trabajo de fondo espera al reproductor**: si acabas de dar Play, la carga del índice y de las filas del portal **no arrancan** durante los primeros 20 s, que es cuando el motor está negociando la señal. Antes corrían a la vez y le quitaban la CPU justo en el momento crítico.
- Al cambiar la medida del índice (o tras una actualización) se pide **una vez** completo, para no quedarse con el de 100 por fila guardado.

**Sin cambios** en el reproductor nativo, en la preparación de la reproducción ni en la extracción del motor.

Verificado: suite Android en verde; release R8 con la firma histórica.

— ChronosBVRX
