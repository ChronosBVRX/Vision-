# Vision+ 2.6.56 — Hotfix: abrir una serie ya no cierra la app

Actualización sobre 2.6.55.

## El fallo

- Al abrir la ficha de una serie con **varias temporadas**, la app podía cerrarse sola mientras
  terminaban de llegar los capítulos de la temporada.
- Causa: la ficha pedía adelantar el capítulo con `nextEpisodeIndex.coerceIn(0, episodes.lastIndex)`.
  Sin capítulos cargados, `lastIndex` vale `-1` y el rango queda vacío: Android lanza
  `IllegalArgumentException: Cannot coerce value to an empty range` y el proceso muere.
- Se reprodujo con los reportes de campo del VPS: `kind=crash`, `screen=Title(...)`, mismo
  mensaje en móvil y en tele.

## La corrección

- Nueva guarda compartida `episodeIndexOrZero(index, size)`: con la lista vacía devuelve `0`
  y nunca construye un rango vacío; con capítulos, acota como siempre.
- Se usa en la ficha **móvil** (`TitleScreen`) y de **tele** (`TvTitleScreen`): precalentado del
  portal, número de capítulo y botón de descarga.
- El selector de capítulos del reproductor nativo también acota con `coerceAtLeast(0)` para no
  repetir el mismo cierre si una temporada llega sin capítulos.
- Las películas no estaban afectadas: el camino de serie exige temporadas o 2+ capítulos.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` en verde.
- Nueva prueba `EpisodeIndexTest`: lista vacía, índice dentro de rango, fuera de rango y
  comprobación de que ninguna de las dos fichas vuelve a acotar con `lastIndex`.
- `TitleRelatedWiringTest` sigue en verde.

No se tocan el motor X4K, el vivo, los deportes, los perfiles, las descargas ni el catálogo.

— ChronosBVRX
