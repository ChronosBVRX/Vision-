# Vision+ 2.6.55 — Fichas en español y Películas Infantiles completas

Actualización sobre 2.6.54.

## Fichas

- Las **sinopsis** de películas y series ya no muestran texto en inglés: se usa la sinopsis es-MX de TMDB y, si viene vacía, su traducción oficial al español.
- Si TMDB todavía no tiene una sinopsis en español, la ficha prefiere no mostrar texto antes que enseñar el inglés del portal.
- El **reparto** y el **país** vienen de TMDB: el país se resuelve por código ISO y aparece siempre en español («Estados Unidos», «Reino Unido», «México»…).
- La ficha de TV y móvil muestra el reparto y el país; el reproductor usa los mismos datos en su tarjeta de información.

## Infantil

- **Películas Infantiles** y **Series Infantiles** abren directamente en la cuadrícula completa, con paginación real: ya no se quedan en las primeras 60 ni repiten las mismas dos filas.
- Se recupera la animación occidental que el portal etiquetaba como «Anime» (Shrek, Barbie, LEGO, Muppets…). Solo se descarta la animación realmente japonesa.
- El contador de la sección ahora refleja los títulos disponibles después de esa clasificación.

## Servidor

- Nueva pasada de sinopsis/país/idioma original de TMDB para fichas viejas, priorizando las colecciones infantiles.
- Las categorías infantiles filtran el anime japonés real por `original_language` de TMDB.
- Los endpoints del índice entregan `cast`, `countries` y `originalLanguage` sin romper clientes anteriores.

## Verificación

- Backend: 573 pruebas en verde.
- Android: compilación correcta y pruebas de catálogo, infantil, fichas y reproductor en verde.
- Smoke real en el VPS: las fichas ya entregan reparto/país/idioma, y la colección infantil pasa de 640 a 592 títulos tras excluir solo el anime japonés, recuperando la animación occidental.

No se tocan el motor X4K, el vivo, los deportes, los perfiles ni las descargas.

— ChronosBVRX
