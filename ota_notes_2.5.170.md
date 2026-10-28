# Vision+ 2.5.170 + Home 1.3.0 — TV en vivo abre a la primera y respeta el menú desde el que entraste

## Lo nuevo de esta versión

- **TV en vivo — el canal ya se ve al primer intento**:
  - Al abrir un canal desde **Vision+ Home** (o desde la propia app), el reproductor cargaba pero el canal se quedaba **en negro**; al subir de canal el de arriba sí se veía y, al volver, también el original. Ahora **se ve desde el primer play**, sin tocar el mando.
  - La causa era fina: el motor del proveedor crea su vista de video **dentro** del `play` y solo la añade a la pantalla si la vista ya está anclada a la ventana. Con el motor **precalentado** (el camino normal: la app lo calienta justo antes de abrir el canal), ese primer `play` ocurría antes de que la pantalla existiera y el video se quedaba sin superficie: audio y progreso sí, imagen negra hasta cambiar de canal.
  - El reproductor ahora **espera a que la vista esté en pantalla** para el primer play y, por si acaso, comprueba dos veces que la superficie de video exista; si faltara, la re-emite **una sola vez** (lo mismo que hacía el cambio de canal, sin cambiarte el canal).

- **TV en vivo — el canal conserva el menú desde el que lo abriste**:
  - Abrir un canal desde una fila del **Inicio de Vision+ Home** (por ejemplo «TV en vivo», que empieza por los canales de México, o «Mis canales») perdía el menú: al dar Arriba/Abajo cambiabas de canal **sobre los 1,000 canales** y la lista de **Canales** salía completa. Ahora el canal viaja con su fila: el zapping y el botón **Canales** se quedan **dentro de ese menú** (en «Mis canales», solo tus favoritos en tu orden).
  - La pestaña **TV en vivo** ya lo hacía (Todos / Canales de México / Mis canales); el Inicio es el que se quedaba atrás. **Requiere Vision+ Home 1.3.0**, que se publica en este mismo release: actualízalo desde el Centro de control del launcher o desde Ajustes → Sistema → Vision+ Home.

## Todo lo que llega desde la estable anterior

- Se mantiene **todo lo de la 2.5.169**: navegación completa con el mando en la tele y en **Vision+ Home**, «Omitir intro» y elegir un minuto con un solo toque en el móvil, y el resto del catálogo, hero diario, Deportes con enlaces frescos, perfiles, continuidad entre equipos y subtítulos del proveedor.
- **Vision+ Home 1.3.0** (launcher, se instala/actualiza aparte) trae además **sus propios Ajustes** (Inicio, Entradas, Sistema, Actualizaciones, Acerca de), la pestaña y la fila **Entradas** con el sintonizador y los HDMI reales de la tele (solo si el equipo los tiene; en una TV box o PC no se inventan), y la nota honesta cuando el equipo no tiene entradas.

## Pruebas

**993 pruebas Android + 148 del launcher** en verde. Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3); launcher 1.3.0 firmado con la misma llave. Verificado en el emulador con el motor precalentado: antes el `play` ocurría sin vista de render y el contenedor quedaba vacío; ahora arranca con la superficie lista y el canal se ve **sin cambiar de canal**. Y desde la fila «TV en vivo» del Inicio del launcher el reproductor recibe su menú (`codes=400 resolved=400 group=Todos los canales`).

Actualiza desde el aviso de la app (o desde Ajustes → «Buscar actualizaciones»). El launcher se actualiza desde su Centro de control o desde Ajustes → Sistema → Vision+ Home.

— ChronosBVRX
