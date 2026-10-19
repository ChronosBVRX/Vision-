# Vision+ 2.5.111 — el Inicio ahora sí es «novedades populares» (y sin tarjetas repetidas)

Se auditó el catálogo real del Inicio, no una maqueta: lo que mandaba el servidor y lo que pintaba la app. El resultado eran tres puntos concretos: «Populares de la semana» repetía la MISMA serie varias veces (Link Click salía 4 veces: T1, T2, T3 y T4; Tulsa King 5; La diplomática 5), «Novedades y Estrenos» abría con la misma película del Top 10 y colaba rarezas con «10.0» de 4 votos, y las filas de abajo volvían a mostrar lo mismo que las de arriba. Este lote lo corrige en el servidor y en la app.

## Qué cambió

- **Una sola tarjeta por obra.** El servidor ya no entrega una fila por cada copia del catálogo que comparte el mismo título de TMDB: Tendencias y «Populares de la semana» dejan una entrada por serie/película y eligen la mejor copia (la doblada al español antes que una variante extranjera). Link Click puede seguir apareciendo si de verdad está en tendencia, pero **una vez**, no cuatro.
- **Novedades populares de verdad.** La fila «Novedades y Estrenos» se ordena por impacto real: la popularidad manda y el rating solo pesa cuando tiene votos que lo respalden. Un 10.0 con 4 votos ya no le gana a un estreno que la gente sí está viendo, y la fila **no repite** lo que ya salió en el Top 10.
- **Cero repetidos entre filas.** «Populares de la semana» y las filas por tipo (Películas, Series, Anime, Para chicos) descartan lo que ya pintaron el Top 10, Novedades o la hilera semanal. Se acabó ver la misma carátula tres veces al bajar por el Inicio.
- **Nombres limpios.** Los títulos doblados ya no muestran el sufijo del portal («Los Simpson - Español Latino» → «Los Simpson»).
- **El Inicio abre con todo desde el primer momento.** El índice del catálogo se recortaba en el servidor después de calcularlo completo (47 s y 10.2 MB); ahora se recorta en la consulta y se cachea por versión: **2.5 s la primera vez, 1.2 s después, 5.8 MB**. Esto además arregla el caso en que el Inicio se quedaba solo con «Populares de la semana» y «TV en vivo» porque el índice no llegaba a tiempo.

## Pruebas y verificación

- **652 pruebas de Android en verde (10 nuevas) y 245 de backend**: deduplicación por tmdbId (temporadas y doblajes), el match malo de TMDB que no debe fusionar dos obras distintas, la copia en español que gana, el rating huérfano que no corona las novedades, las filas por tipo sin repetir lo de arriba y el cableado móvil+TV.
- **Verificado en el emulador con datos reales**: Top 10 (Spider-Man, La Noche del Demonio, Ley y orden…), Novedades (Entrega Al Límite, Toy Story 5, Tierra de Mafia…) sin traslape; Populares de la semana (South Park, Hope, Bajo la superficie, Bleach…) sin Link Click repetido; y Películas populares sin lo ya visto arriba.
- El servidor ya quedó aplicado (no requiere OTA); esta actualización lleva la parte de la app a teléfonos y tele.

— ChronosBVRX
