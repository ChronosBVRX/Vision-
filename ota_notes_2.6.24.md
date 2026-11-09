# Vision+ 2.6.24: fin de la pantalla blanca al iniciar un juego en televisores/teléfonos viejos

Se corrigió el bucle por el que la pantalla de carga ("GAME LOADING") podía quedarse pegada después de empezar un juego en televisores con navegador antiguo (como tu TCL y el Chromecast): la máscara de carga ahora se retira por temporizador en CADA navegación de la página, no solo en la primera. Así el lobby y la escena del juego se ven siempre.

Se mantiene la carga completa de los juegos y su audio sin cortes de la 2.6.22/2.6.23.

Sin cambios para tus datos: perfiles, historial y descargas se conservan.
