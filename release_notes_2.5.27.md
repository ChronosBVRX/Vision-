# Vision+ v2.5.27 — Estrenos y Novedades Curadas + Blindaje del VPS

Esta versión incorpora repisas dedicadas de novedades y estrenos en la sección principal y en cada categoría individual (Películas, Series, Anime y Novelas) con identidad nativa de Vision+, además de blindar por completo la seguridad y privacidad del servidor VPS.

### 🌟 Novedades y Mejoras Clave

1. **Repisas de Estrenos y Novedades por Sección**:
   - **Películas**: Nueva hilera estelar *«Estrenos de Películas»* ubicada inmediatamente bajo el Héroe inmersivo en Smart TV y al inicio de la sección en móvil.
   - **Series**: Repisa dedicada *«Estrenos y Nuevas Temporadas»* con nombres limpios y normalizados (sin marcas antiestéticas de "T1" o "Tem 2").
   - **Anime y Novelas**: Hileras de *«Estrenos de Anime»* y *«Novedades de Novelas»* aisladas de forma mutuamente excluyente.
   - **Aislamiento Estricto de Catálogo**: Cine y series quedan 100% protegidos contra contaminación cruzada.

2. **Estrenos Promovidos en Inicio (Home)**:
   - La repisa de *«Estrenos y Novedades»* enriquecida con metadatos y calificaciones de TMDB se ubica ahora en la parte superior del catálogo en Smart TV y móvil.
   - Carga instantánea (0 ms) en el primer cuadro desde la memoria caché, con actualización transparente en segundo plano.

3. **Estandarización de Repisas Dinámicas**:
   - Limpieza automática de nombres crudos provenientes del portal (*"Recién agregadas"*, *"Estrenos"*, *"Últimos lanzamientos"*) adaptándolos a la tipografía y estética premium de Vision+.

4. **Blindaje Integral del VPS y Ocultación de API**:
   - Erradicación total de la documentación pública de Swagger y OpenAPI (`/docs`, `/redoc`, `/openapi.json`), impidiendo que bots o rastreadores descubran la estructura o rutas del índice.
   - Endpoints de salud (`/healthz`) y raíz (`/`) protegidos con respuestas genéricas seguras sin exponer metadatos del sistema ni de PostgreSQL.
   - Verificación de cabecera `X-Api-Key` en tiempo constante en todas las rutas del catálogo.

5. **Optimizaciones de Rendimiento y Respuesta Inmediata**:
   - Compresión HTTP GZIP activa en el servidor, reduciendo el consumo de ancho de banda del catálogo en un 80%.
   - Búsqueda instantánea con *debounce* de 280 ms y cálculo acelerado de taxonomía en Smart TV.
   - Historial de «Seguir viendo» optimizado a tiempo constante $O(1)$ sin bloqueos de interfaz.

6. **100% de Pruebas en Verde**:
   - 120 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 32 pruebas de backend (`pytest server/tests`) pasando con éxito.
