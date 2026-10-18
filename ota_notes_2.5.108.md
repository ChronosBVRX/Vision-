# Vision+ 2.5.108 — El control remoto ya no deja la tele a medias

Arreglo del reporte: **con algo reproduciéndose en la TV, mandar un título o canal desde el control remoto del teléfono dejaba el menú al frente con el audio de la reproducción anterior de fondo**. Ahora el contenido nuevo abre bien y nunca queda video sonando invisible.

## Qué pasaba (dos causas encadenadas)

- **El cierre viejo deshacía el play nuevo**: el reproductor vive en su propia tarea de Android, así que mientras se ve algo, el menú queda detenido con su pantalla de espera («Abriendo…») todavía dibujada. El deep link que manda el teléfono también trae el menú al frente, y esa espera vieja interpretaba «regresé al frente» como «el reproductor se cerró»: cerraba la pantalla que el teléfono acababa de abrir (y el play viejo quedaba apilado y podía re-dispararse al volver).
- **El motor seguía sonando invisible**: al quedar la actividad del reproductor detrás (menú al frente), solo se guardaba el minuto; el motor nunca se soltaba — de ahí el audio de fondo en el menú.

## Qué cambió

- **El deep link reemplaza, no apila**: cuando el teléfono (o una tarjeta de «Continuar viendo» del launcher) abre contenido mientras hay otro reproduciéndose, el nuevo reemplaza al anterior en la navegación. Al volver de la reproducción se regresa a la pantalla real de abajo (Inicio/ficha), jamás a una espera vieja que revive sola.
- **Cada pantalla cierra solo la suya**: el cierre de la espera del reproductor ahora es por identidad de pantalla; una espera vieja no puede cerrar la reproducción nueva.
- **El reproductor suelta el motor si queda detrás**: si la actividad queda en segundo plano con reproducción viva (control remoto abriendo otra cosa, botón Inicio de la tele, pantalla apagada), se guarda el minuto y se detiene el motor — la actividad queda lista para reutilizar la vista en el próximo play, como siempre. El sondeo interno también se apaga para no re-arrancar nada en segundo plano.
- **Visto al reproducir**: la próxima vez que uses el control con el catálogo mientras algo se ve, el título/canal que mandes abre al instante y en el menú ya no queda audio de nada.

## Pruebas y verificación

- **628 pruebas** de Android en verde (9 nuevas): reemplazo del reproductor en curso (película y canal en vivo), el cierre viejo que ya no toca la pantalla nueva, el cierre que nunca vacía la pila, la política de «soltar el motor al quedar detrás» y el guardián de cableado (deep link, cierres y estacionamiento).
- **Pendiente de sentir en la tele**: reproducir algo y mandar otra película/canal desde el control remoto — el contenido nuevo debe abrir solo y jamás quedar audio de fondo en el menú.

— ChronosBVRX
