# Vision+ 2.5.148 — BETA (Deportes honesto + TV en vivo con nombre y guía + navegación del Inicio)

**Deportes vuelve a decir la verdad**
- El estado **EN VIVO** ya no sale de las señales que responden: ahora usa el estado real de la agenda y, cuando la agenda llega tarde, el **reloj** (ventana de 3 horas desde el inicio), la misma regla que la app.
- Los eventos se agrupan en **En vivo · Comienzan pronto · Terminados**; un partido con **FT** ya no aparece como EN VIVO.
- El encabezado cuenta solo los que de verdad están en juego. En la prueba del 24-oct pasó de «59 en vivo» a 19 (y solo los que ya habían comenzado).
- La hora se muestra como `14:00`, sin segundos; los terminados dicen **Final/FT**.

**TV en vivo: se entiende qué canal es y qué está dando**
- Cada tarjeta del mosaico muestra el **nombre del canal** (antes solo el logo) y la guía **AHORA**.
- La guía cubre ahora los primeros **120 canales** (antes 16) y se pide en paralelo; se refresca con el feed cada dos minutos y el avance se recalcula con el reloj.
- Si la fuente no tiene guía para ese canal, dice **«Guía no disponible»** en lugar de inventarla. Los canales con datos (Las Estrellas, Fox Sports, etc.) muestran el programa y su barra de avance.

**Navegación del Inicio (launcher)**
- Fuera el bucle que simulaba **diez «Abajo»**: el foco aterriza una sola vez, verificado, y no se mueve solo cuando el feed se refresca.
- Pulsar **Home** vuelve al héroe sin recrear toda la pantalla; al cambiar de pestaña, el foco entra al primer control real.
- **«Abajo» desde una pestaña** ya no cae en un filtro arbitrario (antes, desde Deportes caía en «Básquet»): aterriza en «Todos los deportes» y de ahí baja a los filtros y a la rejilla.
- Scroll **mínimo**: la lista ya no se recentra al 30 % en cada movimiento; solo se desplaza si el control quedó fuera de vista. Las filas tienen grupo y memoria de foco.
- Las animaciones de foco bajan a 120 ms y se apagan en teles de 2 GB o menos. Rescate de foco si una tarjeta desaparece.

**También viene del lote 2.5.147**
- Buscador con autocompletar, recientes y filtros; teclado compacto en la tele y píldora «Buscar».

— ChronosBVRX
