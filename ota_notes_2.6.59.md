# Vision+ 2.6.59 — Recomendaciones del mismo género

Actualización sobre 2.6.58.

## Sugerencias al terminar una película

- «Más como esta» ahora usa el **género real de la ficha** y ya no rellena con lo más popular de todo el catálogo.
- Una película infantil o de animación solo propone animación, familia o infantil: **nunca terror, suspenso, crimen ni guerra**.
- Una película de terror propone terror, suspenso o misterio.
- Si no hay suficientes títulos del mismo género, la hilera no se muestra antes que mezclar géneros que no corresponden.
- El género, el tipo y el idioma original viajan de la ficha al reproductor (`:x4k`), que antes no compartía el catálogo en memoria.
- Si la obra no está en la primera página de su género, se consultan sus géneros en el servidor.
- La animación occidental mal etiquetada como «Anime» (Shrek, Monsters Inc…) busca sus relacionadas en Películas.

## Relacionadas antes de reproducir

- La hilera «Relacionadas» de la ficha, tanto en móvil como en TV, usa el mismo criterio estricto de género.
- Se unifican los tags del portal en español e inglés y sus variantes (`Animation`, `Family`, `Sci-Fi & Fantasy`, `Thriller`, `Bélica`…), aceptando separadores como coma, barra, tubería o punto medio.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- Pruebas en verde de `RelatedTest`, `HomePersonalTest` y `TitleRelatedWiringTest`, incluyendo el caso *Monsters Inc → terror*.

— ChronosBVRX
