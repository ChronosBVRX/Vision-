# Vision+ 2.5.225 — Beta Deportes + TV en vivo

**Beta de pruebas.** La estable sigue en 2.5.204.

## TV en vivo más limpio
- Se retiró de la TV el selector **Lista** y la opción de **agregar una fuente**: la barra de filtros queda solo con País, categorías, Favoritos y Recargar, con más espacio para las categorías.
- Las píldoras son **borderless glass**: fondo translúcido y texto gris en reposo; la categoría activa usa un tinte sky con punto indicador; el foco del mando se vuelve blanco con texto oscuro, escala y sombra suave.
- Las tarjetas ya no muestran el logo duplicado; el logo queda arriba (máx. 42 dp) y el texto abajo, separado por un degradado.
- Foco de tarjeta: anillo cian de 2 dp, escala 1.04 y glow; sin borde azul en reposo.
- **Un solo OK abre el canal.** Ya no hay un primer clic que solo actualiza el hero.
- El badge **EN VIVO** es más compacto: fondo carmesí translúcido, punto de 6 dp y texto de 11 sp con tracking.

## EPG integrado
- El hero muestra el programa actual en 18 sp, una píldora con horario y categoría, barra de avance de 4 dp, “Quedan X min” y la fila **Sigue: …**.
- Cada tarjeta muestra el programa actual del EPG; si no hay guía, cae a la categoría. La barra de progreso de 2 dp vive en el borde inferior.
- La petición de EPG usa los canales populares primero y reintenta si el VPS tarda; la parrilla no se bloquea mientras llega.

## Deportes
- Partido / Estadísticas / Alineaciones dentro de Información, con D-pad.
- Tarjeta estilo FIFA con degradado, escudos, marcador y goleadores.

— ChronosBVRX
