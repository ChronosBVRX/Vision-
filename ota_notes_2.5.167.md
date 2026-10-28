# Vision+ 2.5.167 (beta) — Beta consolidada: todos los pendientes de los otros agentes en un solo APK

Esta beta junta todo lo que estaba pendiente en el árbol (Roku, «Inicio en la TV», diagnósticos, continuidad, temáticas, GeeSports y catálogo) y además deja desplegado el **API de catálogo de Roku Direct**.

## Lo que ya trae el APK

- **Roku (relay local, solo teléfono)**: Ajustes → «Roku (relay local)» enciende el relay por la LAN con PIN, muestra la dirección y el código, y permite copiarlos. El teléfono sirve el HLS del motor y el canal de Roku lo reproduce. Sigue siendo **solo LAN**, **un título a la vez** y **solo películas** en esta versión; el teléfono debe quedarse con Vision+ y el relay encendidos.
- **Inicio en la TV por ADB inalámbrico (solo teléfono)**: empareja una o varias teles con el código de 6 dígitos una vez, pone Vision+ Home como Inicio, verifica quién quedó al frente, retira el launcher de fábrica si hace falta y permite **restaurar** cada tele. Nunca desactiva System UI, Ajustes, instalador ni permissioncontroller; si Vision+ Home no está instalado, no toca el Inicio.
- **Diagnósticos**: el vigilante de ANR solo mide con la app al frente, la huella usa rangos de duración (no los segundos exactos) y `PLAYBACK_LICENSE_FAIL` explica el motivo (excepción, código del portal o «sin medios»). Se añadió el latido de sesión y `/v1/diag/health`.
- **Continuidad entre equipos**: «Ver en la TV» reanuda en el minuto exacto y «Seguir viendo», favoritos, ver más tarde y vistos se sincronizan con un código compartido por el VPS.
- **Colecciones temáticas**: Día de Muertos (activo), Halloween, Navidad, San Valentín, Día de las Madres, Día del Padre, Fiestas Patrias, Vacaciones, Regreso a clases, Año Nuevo, «Noche de terror», «Para reír» y «Tendencias del mundo». En **Categorías** ahora hay una sección **«Temáticas»** separada de «Géneros y colecciones».
- **Catálogo vivo y géneros**: Top 10 con popularidad refrescada y rotación diaria, Novedades con recién llegados, géneros canónicos sin repetir entre Drama/Suspenso/Comedia, filas llenas y sin tráilers duplicados. De paso se restauró la fila **Terror** (4,126 títulos), que se había quedado sin alias.
- **Deportes**: solo enlaces de GeeSports, «Sin enlaces disponibles» cuando no hay, y el pase caliente del worker + refresco de la app cada 5 minutos.

## Servidor desplegado en esta beta

- **Roku Direct (API de catálogo)**: `/v1/roku/health`, `/v1/roku/home`, `/v1/roku/search`, `/v1/roku/title` y `/v1/roku/episodes` ya responden en el VPS con la misma API key. Son de **solo lectura** y no tocan el resolver. La reproducción de Roku Direct sigue pendiente de M1/M2; el canal sideloadeado existe, pero el plano de playback no.
- **EPG, agrupador de series y diagnósticos**: el VPS quedó con las versiones del árbol (EPG multi-país, una entrada por temporada, salud de catálogo y alertas).
- **Temáticas y catálogo**: el índice ya construye Día de Muertos (28), Halloween (28), Noche de terror (24), Para reír (24) y Tendencias del mundo (24 en la respuesta de TV).

## Pruebas

- **996 Android + 437 backend** en verde, release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3).
- Instalado en el emulador: Inicio con Top 10/Novedades, Día de Muertos, Halloween, Noche de terror, Para reír y Categorías con «Temáticas».
- API verificada: `/v1/index`, `/v1/section`, `/v1/roku/*`, `/v1/epg/health`, `/v1/diag/health` y `/v1/trending` responden 200.

## Pendiente (para la prueba física)

- **TLC/caja**: probar el regreso al Inicio, los enlaces de Deportes con el mando, el Atrás al launcher y las colecciones temáticas.
- **Roku físico**: instalar el ZIP sideloadeado, `/v1/roku/health`, portada/búsqueda/ficha y el relay del teléfono. El canal Roku Direct todavía no reproduce (falta el plano B).
- **Relay standalone**: arranque automático del relay fuera del móvil (kiosco/JobScheduler) y valorar redroid; sin novedad en esta beta.

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».

— ChronosBVRX
