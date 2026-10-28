# Vision+ 2.5.169 — el mando deja de perderse, el Inicio se siente vivo y el reproductor del móvil queda fino

## Lo nuevo de esta versión

- **Navegación con el mando (D-pad) a prueba de fallos**:
  - En el **Inicio de la TV**, Abajo ya recorre **todas** las filas (Top 10 → Novedades → temáticas → Populares → Películas/Series populares…) y Arriba vuelve hasta la barra superior. Antes el foco se quedaba atorado en la última fila visible.
  - En la tele, elegir otro **género** en Películas/Series ya no te saca del chip que acabas de pulsar.
  - En **TV en vivo**, al cerrar el buscador con Atrás el mando vuelve a un control real; el teclado del PIN de niños cabe completo (2 filas de 5).
  - En **Vision+ Home** (launcher): todos los paneles (Centro de control, Personalizar, acciones de tarjeta, buscador) **abren con foco**, lo conservan cuando cambia el contenido y ya no se quedan «sin mando». Los filtros de Canales/Apps se alcanzan con Arriba y bajan a la lista, y el aviso de **Instalar/Actualizar Vision+** ya se puede seleccionar con el control.
  - El launcher actualizado viaja como **Vision+ Home 1.2.5** en este mismo release: se instala desde Ajustes → Sistema → Vision+ Home (o desde el Centro de control del launcher).

- **Móvil — «Omitir intro» arreglado**: la píldora se ve sobre el video aunque los controles estén cerrados, pero antes el primer toque no hacía nada. Ahora se toca **una sola vez** y salta el intro/resumen, tanto en el reproductor nativo como en el de ExoPlayer.

- **Móvil — elegir un minuto en la barra**: la barra ya no desaparece a mitad del arrastre (un toque previo en el video programaba ocultarla) y el pulgar **se queda en el minuto elegido** mientras el motor procesa el salto, en vez de volver a la posición vieja. Sin duración conocida ya no manda el video al segundo 0.

## Todo lo que llega desde la estable 2.5.157

- **Inicio cinematográfico**: hero con **arte panorámico real** y banners que el VPS renueva cada día, con **Ken Burns**, partidos en vivo de la agenda y el banner completo al volver arriba con el mando.
- **Catálogo vivo**: Top 10 con popularidad refrescada y rotación, Novedades con **recién llegados**, géneros canónicos que **no se repiten** entre Drama/Suspenso/Comedia, filas llenas, sin tráilers duplicados y la fila **Terror** restaurada (4,126 títulos).
- **Colecciones temáticas**: Día de Muertos, Halloween, Navidad, San Valentín, Madres/Padres, Fiestas Patrias, Vacaciones, Regreso a clases, Año Nuevo, «Noche de terror», «Para reír» y «Tendencias del mundo», con una sección **Temáticas** en Categorías.
- **Deportes**: solo las transmisiones de **GeeSports**, «Sin enlaces disponibles» cuando no hay, enlaces que se **refrescan cada 5 minutos** mientras ves la agenda y regreso al mismo partido con «Viendo».
- **Roku retirado por ahora**: se quitó la tarjeta del relay en Ajustes y el teléfono ya no abre el puerto de Roku. El proyecto quedó guardado fuera del repositorio por si se retoma.
- **Lo demás que ya estaba**: **perfiles** por persona, **continuidad entre equipos** con minuto exacto, **«Inicio en la TV»** por ADB inalámbrico, **diagnósticos** con motivo de licencia/ANR y **subtítulos del proveedor**.

## Pruebas

**990 pruebas Android + 125 del launcher** en verde. Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3). El APK y el launcher salen en este mismo release.

Actualiza desde el aviso de la app (o desde Ajustes → «Buscar actualizaciones»). Si usas Vision+ Home, instala también **1.2.5** desde Ajustes → Sistema → Vision+ Home.

— ChronosBVRX
