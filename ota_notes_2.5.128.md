# Vision+ 2.5.128 — el launcher busca, ordena México y abre el deporte

**Vision+ Home** (el launcher) gana lo que faltaba para sentirse sistema:

- **Buscador propio**: el botón de la lupa abre un buscador **dentro del launcher**. Consulta el
  mismo buscador del servidor que usa Vision+ (por nombre, nombre original, reparto o director) y
  pinta los resultados ahí mismo. Solo al elegir uno se abre su ficha en Vision+.
  - En la tele se escribe con un **teclado de mando** (y se puede **dictar**); en el teléfono sale
    el teclado normal.
  - Busca mientras escribes (con un respiro de 260 ms) y muestra «Buscando…», «Sin resultados» o
    la cuadrícula de pósters.
- **TV en vivo con México primero**: la pestaña trae **todos los canales de México** (con los de
  casa al frente: Canal 5, Azteca 7, Las Estrellas, TUDN, Fox Sports, ESPN…) y después los
  populares del resto del catálogo. Nuevos chips **Todos / México** para filtrar, y la guía
  «AHORA» sigue guardada en disco.
- **Pestaña Deportes**: los eventos de la agenda (en vivo primero) dibujados como tarjetas con
  liga, equipos, escudos, marcador, minuto y número de señales. Al elegir uno, **Vision+ abre ese
  partido** con su selector de transmisiones.
  - **Filtros como la app**: fila de **deportes** (Fútbol primero), fila de **días** (Hoy, Mañana,
    Vie 24…) y fila de **ligas** del día y deporte elegidos. «Todos los deportes» y «Todas las
    ligas» quitan el filtro.

**Vision+ (la app) suma:**
- El buscador del launcher servido por el canal de datos (mismo `/v1/search`; nada duplicado).
- El feed incluye la agenda deportiva y el orden de canales con México primero.
- `visionplus://open/sports?event=…` abre el partido exacto en la sección de Deportes.

**Cómo verlo**
1. Ajustes → Sistema → Vision+ Home → actualizar el launcher si lo tenías instalado.
2. Lupa: escribe «nemo» o dicta «tarantino» y elige un resultado (abre la ficha en Vision+).
3. TV en vivo → chip «México»; Deportes → toca un partido.

— ChronosBVRX
