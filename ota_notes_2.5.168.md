# Vision+ 2.5.168 (beta) — Roku retirado por ahora; se queda todo lo demás

- **Roku fuera del APK**: se quitó la tarjeta **«Roku (relay local)»** de Ajustes, el servicio del relay y su entrada del manifest. El teléfono ya no abre el puerto 48640 ni sirve video a un Roku. El proyecto de Roku (canal, relay, docs y empaquetadores) quedó **guardado fuera del repositorio** en `_parked_roku_2026-10-27` por si se retoma después.
- **Roku fuera del VPS**: los endpoints `/v1/roku/*` se retiraron del API; ahora responden **404**. El resto de endpoints (`/v1/index`, `/v1/section`, `/v1/episodes`, `/v1/epg`, `/v1/diag`, `/v1/trending`, etc.) siguen respondiendo 200.
- **Se mantiene todo lo demás de la 2.5.167**:
  - **Colecciones temáticas**: Día de Muertos (activo), Halloween, Navidad, San Valentín, Madres/Padres, Fiestas Patrias, Vacaciones, Regreso a clases, Año Nuevo, «Noche de terror», «Para reír» y «Tendencias del mundo». En **Categorías** hay una sección **«Temáticas»** separada de «Géneros y colecciones».
  - **Catálogo vivo**: Top 10 con popularidad refrescada y rotación, Novedades con recién llegados, géneros canónicos sin repetir entre Drama/Suspenso/Comedia, filas llenas y sin tráilers duplicados; la fila **Terror** restaurada con 4,126 títulos.
  - **Deportes**: solo enlaces de GeeSports, «Sin enlaces disponibles» cuando no hay, y refresco del pase caliente + app cada 5 minutos.
  - **«Inicio en la TV» por ADB inalámbrico**, **continuidad entre equipos** (código compartido, minuto exacto) y **diagnósticos** (ANR con rangos, sesión, motivo de licencia).
- **Pruebas**: **979 Android + 422 backend** en verde. Release R8 firmado con la llave histórica `9593cb0d…` y el motor inyectado (5 DEX: app 2 + motor 3).
- **Pendiente**: prueba física en la TLC; el proyecto de Roku queda parkeado y no se publica hasta que se retome.

Actualízalo desde el aviso de la app si estás en el canal de pruebas, o desde Vision+ → Ajustes → «Canal de pruebas (beta)».

— ChronosBVRX
