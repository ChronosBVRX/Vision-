# Vision+ 2.6.77 (beta)

## Aviso 4K y reproducción manual

- El chip 4K de la esquina ahora es un aviso temporal: **«Esta película está
  disponible en 4K en su idioma original»**. Aparece después de unos segundos de
  reproducción, dura unos segundos y desaparece. Ya no se queda fijo durante la carga.
- El 4K sigue disponible como opción manual en la lista de calidad. Al elegirlo se
  abre el reproductor externo y, si el audio es original, entran automáticamente
  nuestros subtítulos en español.
- El cruce 4K ya no se pierde por un IMDb viejo: si el VPS no encuentra la ficha con
  el dato guardado, reintenta por título/TMDB y limpia el IMDb incorrecto.
- Se prefiere el CDN y se da más tiempo al primer cuadro del 4K (45 s, 60 s en equipos
  lentos) antes de volver al 1080p.
- En televisores cuyo decodificador no soporta HEVC Main10 4K, el reproductor vuelve
  solo al 1080p; el arranque normal nunca se queda en negro.
- El arranque sigue en 1080p y el 4K nunca se selecciona solo.

## Notas

- Publicado solo en el canal beta; la estable sigue en 2.6.67.
- El respaldo a 720p/480p y los subtítulos del catálogo se mantienen.
