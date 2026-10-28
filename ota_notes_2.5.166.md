# Vision+ 2.5.166 (beta) — Colecciones temáticas: Día de Muertos, Halloween, Navidad y más

- **Día de Muertos, ya encendido**: la colección aparece sola del **24 de octubre al 4 de noviembre** con títulos de terror, fantasmas, brujas, vampiros, almas y cementerios. En el emulador abrió con *La Noche del Demonio*, *American Horror Story*, *Embrujadas*, *Buffy*, *Entre fantasmas*, *Fantasmas*, *Scary Movie*, *Sabrina* y *El Heladero*.
- **Halloween** (todo octubre), **Navidad** (diciembre), **San Valentín** (1–15 de febrero), **Día de las Madres**, **Día del Padre**, **Fiestas Patrias**, **Vacaciones**, **Regreso a clases** y **Año Nuevo**. Cada una se enciende y se apaga por fecha; no hay que tocar nada.
- **Todo el año**: «Noche de terror» y «Para reír» con lo más popular de terror y comedia, para que esos catálogos no dependan de la temporada. También «Tendencias del mundo» con la foto semanal de TMDB.
- **«Terror» vuelve al catálogo**: la fila se había quedado sin alias («Terror»/«Horror») y no existía; ahora tiene **4,126 títulos** y alimenta Día de Muertos, Halloween y Noche de terror.
- **Cómo se eligen los títulos**: primero las palabras del **título** («Día de Muertos», «Navidad», «Halloween»), después frases específicas de la sinopsis y al final el **género primario** como relleno popular. Una obra entra una sola vez (se deduplica por `tmdb_id`, así las temporadas T1…T20 de una serie no llenan la fila). Las colecciones de fecha (Navidad, San Valentín, etc.) no se rellenan con cualquier comedia: si no hay títulos de la fecha, la fila no se inventa.
- **Dónde se ven**: en el **Inicio** (después de Novedades y antes de Populares de la semana), en **Categorías** y en el **launcher**. Las filas temáticas no repiten el Top 10, Novedades ni entre sí.
- **Se mantiene todo lo de la 2.5.165**: Top 10 con popularidad refrescada y rotación, Novedades con recién llegados, géneros canónicos sin repetición entre Drama/Suspenso/Comedia, filas más llenas y sin tráilers duplicados.
- **Pruebas**: 996 Android + 437 backend en verde. El servidor ya está desplegado con las colecciones construidas (Día de Muertos 28 títulos, Halloween 28, Noche de terror 24, Para reír 24, Tendencias del mundo 29).
- **Pendiente**: comprobar en la TLC física y ver el cambio de temporada (el 5 de noviembre desaparece Día de Muertos; el 1 de diciembre entra Navidad).

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».

— ChronosBVRX
