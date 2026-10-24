# Vision+ 2.5.130 — la tele con la piel de Vision+ Home

**El modo Smart TV de Vision+ ahora se ve como el launcher**, con todas las opciones de la app.

- **Barra superior global**: Vision+ TV · Inicio · TV en vivo · Deportes · Películas · Series ·
  Mi lista, con la hora del centro de México, el perfil activo, la lupa y los ajustes. El riel
  izquierdo desaparece y la navegación se siente como un sistema.
- **Inicio con el lenguaje de Vision+ Home**: hero a sangre, botón principal blanco (con el anillo
  azul del foco), tarjetas 2:3 redondeadas con la nota dorada, chips redondeados y foco con
  escalado corto. Se conservan las filas de siempre (Top 10, Estrenos, Populares de la semana,
  Seguir viendo, Porque viste X, Tus series · Esta semana, filas por tipo).
- **Pantalla completa**: en la tele se ocultan las barras del sistema, como en el launcher.
- **Todas las secciones siguen ahí** (no se traen las del launcher):
  - **TV en vivo** con los chips de país (México, EE. UU., Colombia…), categorías, favoritos y la
    vista previa; ahora con los nombres completos de los canales.
  - **Deportes** con deporte, día y liga (Fútbol y Liga MX primero), marcadores y señales.
  - **Películas/Series** con el hero y las tarjetas nuevas.
  - **Mi lista** (Favoritos, Ver más tarde, Historial, Descargas), **Buscar**, **Ajustes**
    (perfiles, continuidad, calidad, descargas, control remoto, modo niños) y las fichas.

**Vision+ Home (el launcher) se cuida solo**
- Si detecta que **Vision+ está vieja o no responde** al Inicio, muestra «Vision+ está
  desactualizado» (vX → vY) con **Actualizar Vision+** (descarga + instalador del sistema; Android
  no permite instalar en silencio) y la acción en el centro de control. Si algún día actualizas
  solo el launcher, no se queda con una app vieja sin avisar.
- Se mantiene el buscador propio del launcher, TV en vivo con México primero y la agenda de
  Deportes con filtros de deporte/día/liga (2.5.129).

**Cómo verlo**
1. En la tele, abre Vision+: ya entra directo con la barra superior.
2. Recorre Inicio, TV en vivo (chip «México»), Deportes y Mi lista con el mando.
3. En el launcher: si Vision+ está atrasada, verás la franja para actualizarla.

**Nota de QA**: en un teléfono/emulador puedes forzar el modo tele con
`adb shell settings put global vision_force_tv 1` (y apagarlo con `0`).

— ChronosBVRX
