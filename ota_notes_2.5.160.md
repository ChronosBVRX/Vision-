# Vision+ 2.5.160 (beta) — banners con coste cero y Deportes solo GeeSports

- **La tele ya no pregunta en bucle**: pide `/v1/banners` solo cuando el VPS anuncia la siguiente foto (`nextRefreshAt`, 20 h) + 5 min de margen. Antes podía consultar cada 5-30 min sin que hubiera nada nuevo.
- **Si el VPS está refrescando**, la respuesta trae el siguiente intento a 15 min (nunca una fecha vencida): la tele no entra en bucle ni se queda con una foto eternamente vieja.
- **Una sola descarga a la vez** y **15 min de enfriamiento** si falla la red; el hero sigue pintando la copia en disco.
- **En el VPS hay un solo refresco**: candado de Postgres entre workers y 5 llamadas a TMDB al día como máximo; si otra instancia ya está refrescando, la segunda no gasta nada.
- **Arte reutilizado**: Coil cachea por la ruta estable de la imagen, así que el `?v=` diario del CDN ya no obliga a bajar la misma foto otra vez. La siguiente diapositiva se deja lista mientras se ve la actual.
- **Deportes**: el selector del partido muestra SOLO las fuentes de GeeSports con URL real; sin «Canales posibles» ni respaldo automático.
- **Vision+ Home 1.2.2** con la misma caché y reutilización de arte.
- Incluye el hero de Vision TV 2.5.158: banners diarios del VPS + partidos en vivo de la agenda.

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».
