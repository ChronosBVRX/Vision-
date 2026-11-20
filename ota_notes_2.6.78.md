# Vision+ 2.6.78 (beta)

## Blindaje del 4K

- Si el 4K falla (códec, red, URL vencida o un fallo del decodificador), la app vuelve
  sola al 1080p y la película sigue. No hay que cerrar y abrir la aplicación.
- Se muestra un aviso grande: «El 4K falló, pero puedes verlo en alta definición
  (1080p)». Dura unos segundos y se va solo.
- Si el proceso del reproductor muere durante el 4K, al volver la app recupera la
  misma película en 1080p automáticamente.
- El 4K de esa ficha se oculta 5 minutos después de un fallo para no repetir el ciclo;
  luego se puede reintentar desde la lista de calidad.
- El 4K manual, los subtítulos del catálogo y el arranque en 1080p se mantienen.

## Notas

- Publicado solo en el canal beta; la estable sigue en 2.6.67.
- El respaldo a 720p/480p sigue activo para la reproducción nativa.
