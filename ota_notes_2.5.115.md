# Vision+ 2.5.115 — la tarjeta «Siguiente capítulo» ya dice qué sigue

- **Nombre real del capítulo y una sinopsis corta.** Antes la tarjeta solo mostraba «Capítulo 2» y una cuenta regresiva: el portal nombra sus capítulos con una marca técnica («Serie T2_05») que no dice nada. Ahora ves, por ejemplo, «Capítulo 1 · El titán bestia» con dos o tres líneas de lo que va a pasar.
- **El arte también aparece:** si la serie no traía póster, la tarjeta usa la miniatura real del capítulo (formato 16:9). En la tele la imagen va a la izquierda del texto; en el teléfono la tarjeta es más ancha y el texto ocupa todo el ancho.
- **Todo sale de nuestro servidor, no de TMDB en cada reproducción.** El VPS baja la temporada completa una sola vez (una petición trae todos los capítulos), la guarda en su base de datos y la sirve en milisegundos junto con la lista de capítulos. Se refresca sola cada 14 días y las temporadas nuevas se completan en minutos.
- Si una temporada aún no tiene esos datos (o TMDB no la tiene), la tarjeta se ve exactamente como antes: esto solo puede mejorar lo que ya había.
- Guardianes nuevos: 6 pruebas del lado del servidor (nombres/sinopsis, mezcla es-MX con inglés sin pisar el español, vencimiento y presupuesto) y 3 de la app (lectura de metadatos y cableado de la tarjeta). Suite: 689/689 Android + 281/281 servidor.

— ChronosBVRX
