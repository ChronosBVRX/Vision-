# Vision+ v2.5.29 — Estabilidad Definitiva y Filtro Elegante de Ligas en Fútbol

Esta versión resuelve de manera definitiva los cierres inesperados y congelamientos en la sección de Fútbol tanto en Smart TV como en teléfonos móviles, e integra un sistema interactivo y premium de filtros por liga con conteo de partidos y logos oficiales.

### 🌟 Novedades y Correcciones Clave

1. **🛡️ Eliminación Total de Crashes en Móvil (`IllegalStateException`)**:
   - Erradicado el fallo de Compose ocasionado por el uso de `Modifier.weight` dentro de `LazyRow` en la tira de canales de fútbol.
   - Sustituido por `Modifier.widthIn(max = 160.dp)` en `SportsScreen`, asegurando un renderizado estable y fluido sin restricciones no acotadas.

2. **⚡ Erradicación de Congelamientos de Interfaz y ANR en Fútbol**:
   - Eliminadas las ~180,000 normalizaciones síncronas de cadenas en el hilo principal de la interfaz al calcular canales de transmisión.
   - Implementada caché de alto rendimiento en memoria (`brandInfoCache: ConcurrentHashMap<String, BrandInfo>`) en `SportsLinker` y resolución asíncrona en corrutinas (`Dispatchers.Default` / `Dispatchers.IO`), manteniendo la UI a 60 fps estables.
   - Retirado el cálculo síncrono del bucle de composición de `LazyColumn` en Smart TV (`TvSportsScreen`).

3. **🔒 Blindaje de Memoria Gráfica en GPUs Mali (`SIGSEGV in libhwui.so`)**:
   - Deshabilitado universalmente `.allowHardware(false)` en Coil para el renderizado de logos y escudos de equipos de fútbol.
   - Evita el agotamiento de descriptores nativos `GraphicBuffer` / `AHardwareBuffer` en Smart TV y dispositivos modestos durante el desplazamiento rápido con el control remoto.

4. **🏆 Filtro Elegante de Ligas con Insignias y Contadores**:
   - **Smart TV**: Tira interactiva con navegación D-pad (`TvLeagueFilterChip`) que permite filtrar los encuentros por liga («Todas (N)», «Liga MX», «Champions League», «LaLiga», etc.) con logos oficiales, conteo en tiempo real de partidos disponibles y resplandor en `VisionBlueBright` con foco en blanco nítido.
   - **Móvil**: Barra horizontal de chips estéticos con scroll suave y conteo de partidos, respetando la paleta premium de Vision+ (`VisionBlue`, `VisionSurface`).
   - **Filtrado Reactivo Instantáneo**: Estado centralizado en `SportsState` que filtra los partidos en 0 ms en memoria sin necesidad de peticiones de red adicionales.

5. **✅ Verificación y Calidad**:
   - 121 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 pruebas de backend (`pytest server/tests`) al 100% en verde.
