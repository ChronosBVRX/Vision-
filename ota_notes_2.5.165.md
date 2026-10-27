# Vision+ 2.5.165 (beta) — Deportes: enlaces siempre al día + catálogo vivo y géneros sin repetición

- **Servidor**: los partidos de la ventana (ayer..pasado mañana) tienen un **pase caliente cada 5 min** que vuelve a bajar el `pro` de GeeSports, sondea las señales y actualiza la lista. Se priorizan los enlaces con el `checked_at` más viejo para que el partido en vivo no se quede fuera del lote.
- **`events.txt` a medias**: el worker lo lee **4 veces** y fusiona las copias por partido, conservando el `links_ref` más nuevo. Ya no se queda con una lectura parcial de 79 cuando la fuente tiene cientos.
- **App**: mientras Deportes está al frente, pide **solo los eventos** de GeeSports cada **5 min** (no los grupos), sin esperar el TTL de 8 min ni el ciclo de 30 min. Al abrir el partido, la lista está al día.
- **Sigue la regla**: solo enlaces de GeeSports; si no hay, «Sin enlaces disponibles»; se conserva «Viendo» y el regreso al mismo partido.

## Catálogo vivo y géneros sin repetición (mismo beta)

- **Top 10 en Vision+ Hoy**: ahora respeta el ranking del servidor, que mezcla la **popularidad refrescada de TMDB**, la **tendencia de la semana** y lo **recién llegado**. La popularidad se vuelve a pedir a TMDB cada pocos días (antes se quedó congelada desde el primer match) y los títulos de impacto parecido rotan a diario. Ya no es la misma lista para siempre.
- **Novedades y Estrenos**: manda lo que **de verdad acaba de entrar al catálogo** (los primeros días de cada título) y después lo del año por impacto real. Se acabaron las repisas «destacados» del portal con fichas de 0 votos al frente. La fila cambia sola conforme el sync descubre estrenos; en la verificación ya abrió con un título recién agregado de 2016.
- **Populares de la semana**: la foto de TMDB se refresca **cada 12 h** en vez de quedarse toda la semana, se guarda completa (cine + series) y la app pide 40 títulos para no mostrar siempre los mismos 15.
- **Géneros que sí son géneros**: las categorías se construyen con los géneros reales de **TMDB** (el portal rellena cuando no hay match) y se descartan etiquetas que no son género («Nacional», «Scripted», «Game Show», «Corto», «Talk Show», «Anime»…). También se unifican las variantes («Suspense»/«Suspenso», «Bélica»/«Guerra», «Western»/«Occidental») y los combos de TV («Sci-Fi & Fantasy», «Action & Adventure»).
- **Una película, una categoría**: cada título tiene **un género primario** y solo aparece en esa fila. Drama, Suspenso y Comedia ya no muestran la misma película; lo mismo aplica a Películas, Series, Novelas y Anime. En Novelas se reparten por Romance/Comedia/Drama/Suspenso para que la sección no se quede en una sola fila. Medido en el servidor: **0 títulos repetidos** entre las 18 filas de género de Películas (top 60 de cada una).
- **Filas más llenas**: al servir una sección se piden 4× filas y el límite se aplica **después** de colapsar temporadas de la misma serie; las filas de Series ya no se quedan en 3-6 tarjetas.
- **Sin tráilers duplicados**: los «(Trailer) …» del portal ya no compiten con la película real en el mismo género.
- **Aplica también al launcher**: Vision+ Home se alimenta del mismo Inicio, así que el Top 10, Novedades y las filas por tipo se actualizan sin tocar el launcher.
- **Pruebas**: **993 Android + 418 backend** en verde. El servidor ya está desplegado (refresco de popularidad, géneros primarios, tráilers fuera e índice reconstruido; 240 fichas TMDB refrescadas en el primer pase).

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».

— ChronosBVRX
