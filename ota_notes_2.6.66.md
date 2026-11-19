# Vision+ 2.6.66 — Badge 4K de marca y resolución desde el VPS

Actualización sobre 2.6.65.

## El 4K ahora sí aparece

- El **VPS de Vision+** resuelve las fuentes 4K públicas con su propia IP. El addon limita por IP y el teléfono ya no depende de esa cuota.
- Nuevo endpoint `GET /v1/4k?imdb=<tt…>` (misma `X-Api-Key` del índice): devuelve solo pistas **2160p**, ordenadas por tamaño y con las URLs firmadas de R2 listas para el reproductor.
- La app consulta primero el VPS; si no responde, intenta HdHub directo y luego Kronos. Si nada sirve, cae a Xuper.
- Caché de 10 min en el VPS y de 6 h en el equipo; sin llamadas repetidas por abrir la ficha.

## Badge de marca

- La ficha muestra un badge pequeño **«4K · ULTRA HD»** con el azul oficial, fondo oscuro y borde degradado, tanto en móvil como en TV.
- Va acompañado de «Disponible en 4K» y solo aparece cuando hay una fuente 4K sana en caché.
- No es un botón: el Play normal usa el 4K y cae solo a la máxima calidad nativa si el equipo no puede.

## Verificación

- Android: compilación correcta y **1,357 pruebas**; solo los **3 fallos ambientales conocidos**.
- Servidor: endpoint `v1/4k` probado con *La Odisea* y *Spider-Man: Un nuevo día* (4 pistas cada uno).
- Nuevas pruebas de cableado: badge en las dos fichas y consulta al VPS antes del addon.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
