# Vision+ 2.6.57 — Interfaz TV responsiva, Deportes priorizado y marcadores ESPN

Actualización sobre 2.6.56.

## Inicio y barra superior (TV)

- La barra superior ahora tiene variantes **Full / Compact / Narrow** según el ancho real y el escalado de fuente:
  - **Full:** conserva reloj, nombre del perfil y etiquetas.
  - **Compact/Narrow:** prioriza las 6 pestañas, deja el perfil como avatar, Buscar como icono y oculta el reloj y el sufijo «TV».
- Las 6 pestañas se conservan en una tira desplazable con degradado de borde y auto-scroll a la pestaña activa.
- Objetivos de foco de 40/44 dp, anillo de 2 dp e interlineado explícito para que el D-pad siga legible en 720p y con fuente grande.
- Se corrigió la restauración de foco al cerrar los menús de Películas, Series, Mi lista y En vivo con Atrás.

## Hero Banner

- El bloque de texto usa una sola columna: **46 % / 440 dp** en Compact y **50 % / 560 dp** en Full.
- Título de 28 sp en Compact y 36 sp en Full, sinopsis de 14 sp a 2 líneas.
- «Favorito» pasa a icono de 44 dp en Compact, conservando la acción y el aviso de foco.
- Velo local detrás del texto para mantener contraste sobre banners claros, sin cambiar el degradado global del arte.
- El marcador deportivo usa anchos flexibles, sin nombres fijos que se encimen.

## Deportes en vivo

- Los eventos se ordenan por relevancia real en **todos los deportes**: primero los que tienen canal reproducible, luego el tier del servidor, después la disponibilidad de canales y al final la prioridad de liga.
- Los partidos con más señales/canales quedan arriba; los que no tienen canal quedan al final de su bloque, sin desaparecer (para poder ver dónde se transmiten o programar un aviso).
- El pie de la tarjeta ahora cuenta enlaces vivos, enlaces resolubles y canales XTV de la casa.

## Marcadores ESPN

- Se corrigió el cruce de marcadores de las ligas que ESPN sincroniza pero que antes no entraban en la agenda: **Concacaf Nations League, Copa Oro, UEFA Nations League, amistosos internacionales y Liga BetPlay**.
- También se normaliza «Saint» / «St.» para cruzar equipos como Saint Kitts and Nevis con St. Kitts and Nevis.
- El marcador en vivo vuelve a llegar a la tarjeta desde el servidor; no se inventan cifras si ESPN no publica el partido.

## Catálogo y secciones

- Películas, Series, Anime, Novelas e Infantiles estrenan feed clasificado: **Top 10**, Estrenos, Tendencias, En emisión, Mejor valoradas y géneros.
- «Mejor valoradas» pondera el rating por la confianza de los votos.
- El catálogo infantil abre su feed completo y conserva el lote curado al paginar.
- Se evitan títulos repetidos entre las filas de una misma sección.

## Verificación

- Android: compilación correcta y 1,305 pruebas ejecutadas; solo quedan los 3 fallos ambientales ya conocidos de la suite local (runtime nativo de Robolectric y dos pruebas de UI heredadas).
- Backend: 578 pruebas en verde.
- VPS actualizado y verificado con partidos reales de Concacaf: los marcadores ya llegan a `/v1/gee38/events`.

No se toca el motor X4K, las licencias, los perfiles ni las descargas.

— ChronosBVRX
