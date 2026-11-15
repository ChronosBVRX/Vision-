# Vision+ 2.6.44: Deportes con escudos/fotos y optimización de fluidez

## Deportes (trabajo complementario)

- **Se restauran los logos de los equipos** en las tarjetas de Deportes, en el Inicio y en la portada del launcher.
- **Fútbol tiene prioridad**: cada partido muestra el escudo de su equipo local y visitante. Cuando ESPN trae el escudo, ese manda; si no, se usa la imagen oficial del evento.
- **Fútbol americano, béisbol, básquetbol y hockey** muestran también el logo de cada equipo.
- **UFC y boxeo** muestran la foto de cada peleador en las peleas reales.
- **WWE, F1 y shows** conservan el logo del evento, sin repetirlo en las dos esquinas.
- **Las tarjetas en vivo ahora muestran cuándo empezaron y cuándo terminan**:
  - Píldora: `EN VIVO · 19:00–21:00 · 67'`.
  - Ficha y hero: `Empezó 19:00 · Termina ~21:00 · 67'`.
- El fin se estima según el deporte (fútbol 2 h, NFL 3 h 15, NBA/NHL 2 h 30, MLB 3 h, peleas 3 h) y se marca como aproximado.
- Se mantiene el minuto de juego y el marcador.
- Aplica en TV, celular, hero del Inicio y Vision+ Home.
- No se toca la reproducción, los enlaces, los marcadores ni el resto del catálogo.

## Optimización de fluidez (sin sacrificar funciones)

- **Recomposición más eficiente**: `derivedStateOf` en búsquedas, filtros y agenda de deportes (Home, Search, TvLive, TvSports), para no recalcular listas en cada frame.
- **Mejor reciclado de listas**: `contentType` en listas lazy de Home, Section, TvHome, TvCategory y TvSports, para que Compose recicle tarjetas por tipo.
- **Hero más ligero**: `SubcomposeAsyncImage` → `AsyncImage` en el hero de Home (menos subcomposición).
- **Caché en memoria de páginas de categoría**: `RemoteIndexSource.categoryPage` ahora cachea en RAM con TTL de 5 min, evitando refetch al volver a "Ver más".
- **Sin cambios de funcionalidad**: todos los flujos, pantallas y datos se mantienen igual; solo se reduce trabajo innecesario.

— ChronosBVRX