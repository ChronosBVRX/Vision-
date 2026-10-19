# Vision+ 2.5.113 — el Inicio de Smart TV muestra 3 filas del catálogo

- El Inicio de la tele ahora calcula el tamaño de las tarjetas desde la altura real de la pantalla: en un panel 1080p (540 dp) entran **3 filas completas** del catálogo. Antes, con pósteres fijos de 140×210 dp, solo se veían 2.
- En pantallas altas se conserva el póster grande de siempre (140 dp); en pantallas cortas el póster se achica (≈81 dp en 540 dp) y los márgenes se aprietan lo justo, sin recortar la proporción 2:3 ni la navegación con D-pad.
- La fila «Seguir viendo» usa los mismos márgenes compactos. El sello TOP 10 y el número de posición acompañan el tamaño de la tarjeta para no invadir el póster.
- Cada tarjeta se anuncia con el nombre del título (lector de pantalla) y el nodo enfocado conserva su identidad al volver de una ficha.
- Guardianes nuevos: `TvLayoutTest` (4 pruebas puras de las medidas) y `TvHomeRowsFitTest` (compone 3 filas reales en 540 dp y verifica que la tercera queda dentro de la pantalla). `TvFocusTest` ahora comprueba que el regreso cae en la MISMA tarjeta por título/fila, no por un píxel exacto. Suite completa: 675/675.
- Sin cambios en móvil, en el reproductor ni en la navegación: solo el Inicio de Smart TV se ve más denso.

— ChronosBVRX
