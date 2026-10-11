Solución definitiva a congelamientos, ANR y cierres forzados en TV en Vivo (Modo Smart TV).

- Rendimiento y memoización en UI TV: memoización completa con `remember` de los 1000+ canales, conteos de categorías y lista filtrada en `TvLiveScreen`, eliminando la re-clasificación masiva por frame al mover el D-pad.
- Caché concurrente estática en LiveTaxonomy: constantes de Regex precompiladas a nivel de clase y tablas `ConcurrentHashMap` para normalización y clasificación de canales O(1) instantánea sin allocations ni pausas de Garbage Collector.
- Corrección de restricción de layout en chips de país: eliminación del conflicto de ancho infinito en `TvCountryChip` dentro de `LazyRow`, previniendo crashes de medición de Compose.
- Riel de categorías blindado: selección de género sin colapso de la lista de categorías, manteniendo todas las categorías visibles y la navegación con control remoto intacta.
- Desduplicación y foco seguro: canales protegidos contra claves duplicadas con `distinctBy { it.url }` y selector de primer canal seguro contra listas vacías.
- Limpieza de servicios en segundo plano: remoción del inicio redundante de proxies en `LiveState`.
- Verificado y protegido con tests unitarios en suite de regresión (`TvLiveScreenTest`).
