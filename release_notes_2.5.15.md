Blindaje exhaustivo contra congelamientos y crashes en todas las secciones de Smart TV.

- Optimización en búsqueda y normalización de texto: precompilación estática de expresiones regulares en Catalog y TvCategoryScreen (eliminando miles de recompilaciones por pulsación en teclado y búsquedas).
- Prevención de desbordamientos y colisiones de layout: estandarización de columnas y listas con `weight(1f)` en Categorías, Fútbol y Ajustes, eliminando anomalías de medición en pantallas con D-pad.
- Desduplicación sistemática de claves en Lazy layouts: blindaje con `distinctBy` en películas de categorías, agenda de partidos de fútbol y listas personales («Mi lista»), evitando excepciones de claves repetidas en Compose.
- Foco seguro en categorías: selección y aterrizaje seguros contra colecciones vacías en TvCategoriesScreen sin evaluaciones repetitivas de primer elemento.
- Verificado y protegido por la suite completa de tests automatizados.
