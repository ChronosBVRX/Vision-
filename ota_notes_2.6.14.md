# Vision+ 2.6.14 — Entrada inmersiva y salas de un solo juego

Actualización estable sobre la 2.6.13. Mantiene catálogo, perfiles, descargas, reproducción y los 13 juegos.

## Juegos

- Al entrar a **Juegos** aparece una transición inmersiva con animación propia y audio de fiesta.
- Se incluyen **8 tomas de audio** creadas con Qwen y mezcladas con un fondo musical de transición; se evita repetir la última que escuchaste.
- El texto de la transición se adapta a la tele y la animación corre local, sin depender de la red.

## Salas

- Al elegir un juego ya **no pasas por la landing ni por el dashboard**: se abre directo la sala de ese juego.
- Mientras se prepara la sala se muestra una pantalla nativa de **“Preparando la sala”**, en lugar de pantallas intermedias del navegador.
- Cada sala queda **dedicada a un solo juego**. Desde la tele o desde el control del anfitrión en el celular ya no se puede cambiar de juego ni volver al catálogo.
- El lobby del juego se limpia de elementos de catálogo: sin botón de volver, sin barra lateral de navegador y con una cabecera más clara.
- Las tarjetas del lobby muestran jugadores y duración aproximada de la ronda.

## Estabilidad

- La máscara de carga se retira sola al terminar de montar la sala.
- El botón **Salir** se movió al centro superior para no tapar el título del lobby.
- Sin cambios en perfiles, favoritos, historial, descargas ni ajustes.

## Pruebas

- Android: **1,221 pruebas**, 0 fallos, 3 omitidas.
- Host: los **13/13** juegos inician ronda; la sala rechaza el cambio de juego.
- Verificado en Chromecast HD: transición, carga nativa, lobby sin dashboard, QR, D-pad y salida.

## Cómo actualizar

Desde **Ajustes → Buscar actualizaciones**, o instalando el APK de este release encima de la versión actual. No borra datos, perfiles, favoritos, historial, descargas ni ajustes.

— ChronosBVRX
