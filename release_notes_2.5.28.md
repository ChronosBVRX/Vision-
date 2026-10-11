# Vision+ v2.5.28 — Buscador Universal Inteligente y Búsqueda por Voz

Esta versión revoluciona el sistema de búsqueda en Vision+, incorporando dictado por voz nativo en Smart TV y móvil, búsqueda difusa universal en el servidor VPS con tolerancia total a acentos y tildes, búsqueda multicriterio (título, director, reparto, sinopsis) y ordenamiento ponderado por relevancia.

### 🌟 Novedades y Mejoras Clave

1. **🎤 Búsqueda por Voz Nativa (Smart TV y Teléfono Móvil)**:
   - **Smart TV**: Incorporado botón de dictado por voz («Dictar voz») integrado al teclado virtual y navegable con D-pad. Al pulsar OK con el control remoto, activa el asistente de voz nativo de Android TV para buscar cualquier título al instante sin necesidad de escribir letra por letra.
   - **Teléfono Móvil**: Botón de micrófono integrado directamente en la barra de búsqueda para dictar títulos rápidamente en cualquier momento.
   - **Botón de Borrado Rápido**: Botón «Borrar» para limpiar el texto de búsqueda con un solo clic.

2. **⚡ Búsqueda Universal Tolerante a Acentos y Diacríticos (`unaccent`)**:
   - Resuelto el problema donde buscar sin tilde ("la vida es bella", "cancion", "corazon", "angel", "mama") no encontraba títulos guardados con acentos (o viceversa).
   - Integración de la extensión `unaccent` en PostgreSQL tanto en la consulta como en los campos de datos, garantizando coincidencias 100% exactas sin importar la ortografía del usuario.

3. **🎯 Búsqueda Multicriterio y por Palabras Separadas (Tokens)**:
   - Búsqueda descompuesta por términos independientes: buscar palabras desordenadas ("vida bella", "vida es bella") encuentra de inmediato títulos relevantes como "La vida es bella".
   - Soporte para búsquedas por **Director** (ej. "Roberto Benigni", "Tarantino", "Christopher Nolan", "Spielberg") y **Actores/Reparto**, devolviendo sus películas aunque su nombre no figure en el título.
   - Búsqueda en sinopsis completa como criterio complementario.

4. **🏆 Ranking Ponderado por Relevancia y Calificación**:
   - Erradicado el ordenamiento alfabético ciego que relegaba los clásicos y títulos importantes.
   - Sistema de puntuación multicapa:
     1. Coincidencia exacta de título completo.
     2. Títulos que comienzan con la consulta.
     3. Títulos que contienen la frase completa o todas las palabras.
     4. Título original en idioma fuente.
     5. Coincidencias en director, reparto y sinopsis.
   - Desempate ordenado por calificación (IMDb/TMDB score) y año más reciente.

5. **⚡ Caché Ultrarrápido en Memoria (0 ms)**:
   - Caché en memoria viva en la app Android (`memorySearchCache` en `RemoteIndexSource`) para respuestas instantáneas en retrocesos de texto o consultas repetidas.
   - Caché en memoria en el servidor VPS (`_SEARCH_CACHE` en FastAPI), respondiendo en menos de 1 ms.
   - Reducción del *debounce* de tecleo a 220 ms para una respuesta hiperfluida a 60 fps en Smart TV.

6. **📱 Búsqueda del Catálogo Completo en Móvil**:
   - `SearchScreen` en teléfonos ahora consulta directamente el índice remoto del servidor VPS (80,000+ títulos), en lugar de limitarse a las pocas películas cargadas en la pantalla de inicio.

7. **✅ 100% de Pruebas Unitarias Verificadas**:
   - 120 pruebas de Android (`:app:testNormalDebugUnitTest`) y 33 pruebas de backend (`pytest server/tests`) al 100% en verde.
