# Vision+ 2.5.176 — Android 8/9 con errores claros y reintento

## Lo que se corrige

- **Cerrar el diálogo ya no inventa un fallo**: antes, si cerrabas «Agregar una tele» mientras conectaba, aparecía **«No se pudo conectar: … was cancelled»**. Era el trabajo cancelado, no la tele. Ahora la cancelación se trata como cancelación y no deja mensajes de error.
- **La búsqueda con código ya no pisa la lista Android 8/9**: las dos búsquedas corrían a la vez y la de Android 11 (20 s) podía terminar después y tapar las teles encontradas. Ahora la búsqueda vive **dentro del diálogo** y cualquier acción cancela la anterior.
- **Botón «Reintentar conexión»**: si un intento falla, el diálogo ofrece reintentar con la **misma dirección**, sin volver a escribirla ni buscarla. Es lo que hace falta después de aceptar «¿Permitir depuración?» en el televisor.
- Al cerrar se **limpian el mensaje y los detalles**, para que no queden intentos viejos en la pantalla.

## Lo que ya traía (2.5.175 y 2.5.174)

- **«Buscar teles Android 8/9»**: el teléfono busca en tu Wi-Fi los anuncios de Android TV y las direcciones con el puerto **5555** abierto, y te las lista con su nombre. Tocas la tuya y listo.
- **Dirección manual**: si la tele no se anuncia, escribes `192.168.1.x` y pulsas **Conectar**.
- En la tele solo hay que encender **«ADB por red»**; el televisor pedirá **«¿Permitir depuración?»** y se acepta con el mando (la app espera hasta 90 s).
- Después instala la **última Vision+** y el **último Vision+ Home** y deja Home como **Inicio**, con **Restaurar** por tele.
- **No hace falta tener Vision+ instalada** en la tele para encontrarla. Se excluyen todas las direcciones del propio teléfono.

## Requisitos en la tele

- Misma Wi-Fi que el teléfono y **«ADB por red»** encendido: Ajustes → Sistema → Información → 7 toques a «Versión» → Desarrollador → «ADB por red».
- La primera instalación baja ~82 MB de Vision+, puede tardar unos minutos.

## Pruebas

**1006 pruebas Android + 153 del launcher** en verde. Guardianes nuevos: la cancelación no se reporta como fallo, un trabajo cancelado no escribe estado, la búsqueda vive dentro del diálogo, se recuerda la última dirección para reintentar y la interfaz ofrece «Reintentar conexión». Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3).

Actualiza desde el aviso de la app (o Ajustes → «Buscar actualizaciones»).

— ChronosBVRX
