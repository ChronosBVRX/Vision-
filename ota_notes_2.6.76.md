# Vision+ 2.6.76 (beta)

## Películas que sí arrancan en la tele

- El reproductor nativo ya no reinicia el buffer a los 11 s cuando el motor todavía
  está cargando: si el título trae duración conocida, espera la ventana larga
  (45 s/60 s en equipos lentos) y solo reintenta cuando de verdad no hay datos.
- El 4K ya no se resuelve al abrir la ficha ni durante la carga: el cruce empieza
  varios segundos después del primer cuadro, cuando el 1080p ya está en pantalla.
- El chip 4K aparece después de unos segundos de reproducción, no en la pantalla
  de carga. La píldora de calidad sigue mostrando 1080p.
- El 4K sigue disponible como opción manual en la lista de calidad.
- La ficha conserva su aviso de 4K disponible cuando ya está en caché.

## Notas

- Publicado solo en el canal beta; la estable sigue en 2.6.67.
- Si el primer cuadro tarda por red, el respaldo a 720p/480p sigue activo.
