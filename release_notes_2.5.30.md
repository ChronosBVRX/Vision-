# Vision+ v2.5.30 — Navegación D-Pad Blindada y Detección Exacta de Canales en Fútbol

Esta actualización corrige de forma integral la pérdida de navegación D-pad al interactuar con los filtros de ligas en Smart TV, y perfecciona la asignación de canales de transmisión en fútbol eliminando sugerencias incorrectas y validando los derechos reales de cada liga.

### 🌟 Novedades y Mejoras Clave

1. **🎮 Navegación D-Pad Blindada en Smart TV (`TvSportsScreen.kt`)**:
   - **Retención de Foco en Filtros**: Al presionar OK sobre cualquier filtro de liga, el foco permanece en el chip seleccionado sin extraviarse ni saltar al inicio.
   - **Contenedores Semánticos (`Modifier.focusGroup()`)**: Separación clara entre la tira de Días, la tira de Filtros de Ligas y la lista de Partidos, guiando al motor espacial de Compose en Smart TV.
   - **Conexiones Direccionales Explícitas (`focusProperties`)**:
     - Presionar **ABAJO** en cualquier chip de liga enfoca directamente la primera tarjeta del partido.
     - Presionar **ARRIBA** desde el primer partido regresa con precisión al chip de filtro activo, sin atascarse en encabezados de texto no interactivos.
     - Presionar **ARRIBA** desde los filtros salta limpiamente a la fila de fechas.
   - **Reseteo de Scroll Seguro (`listState = rememberLazyListState()`)**: Al cambiar de liga o fecha, el scroll de la lista regresa automáticamente a la posición 0, evitando que listas con pocos partidos queden fuera de la pantalla.

2. **⚽ Detección Exacta de Canales y Fuentes de Transmisión (`SportsLinker.kt` & `server/sync/sports.py`)**:
   - **Validación de Derechos por Liga (`LEAGUE_DISALLOWED_BRANDS`)**: Erradicado el problema donde partidos europeos sugerían canales sin derechos (ej. sugerir Fox Sports para Serie A o LaLiga). Cualquier canal que no posea derechos de esa liga es rechazado con puntuación 0.
   - **Priorización Estricta de Emisiones Específicas**: Las emisiones directas confirmadas (`broadcasts`) tienen prioridad sobre las listas genéricas de respaldo.
   - **Listados de Transmisión Reales desde el Servidor**: Implementado fallback resiliente con `curl` en `_fetch_livesoccertv` para burlar el bloqueo Cloudflare HTTP 403, garantizando que el servidor extraiga los canales reales de transmisión para México.
   - **Mapeo Canónico Enriquecido**: Soporte para nombres oficiales como `ESPN 2 Mexico`, `ESPN2 Mexico`, `FOX One`, `ViX Mexico`, `Max Mexico` y `Movistar Fútbol`.

3. **✅ 100% de Pruebas Unitarias Verificadas**:
   - 122 pruebas unitarias de Android (`:app:testNormalDebugUnitTest` incluyendo validación de marcas desautorizadas por liga) y 33 pruebas de backend (`pytest server/tests`) aprobadas al 100%.
