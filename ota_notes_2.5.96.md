# Vision+ 2.5.96 — arranque en frío al instante + alertas que ya no se callan

Esta tanda no toca la reproducción ni la navegación: es una tanda de **arranque y diagnóstico** — el primer arranque en frío deja de esperar al portal (a veces varios segundos, incluso en móviles potentes) y los avisos del bot ahora saltan también cuando un problema conocido reaparece.

## Arranque en frío — Inicio sin espera
- El Inicio se pinta con contenido real en milisegundos: la semilla va al primer cuadro y el índice en caché **ya no bloquea el arranque** (su parseo pesa varios MB y era de los 4-9 s medidos).
- Las llamadas del portal (metadatos de fichas + fila de **TV en vivo**) pasaron a segundo plano: el Inicio abre con lo suyo y las filas se refrescan solas en cuanto el portal responde.
- Con esto desaparece el `HOME_ROWS_FAIL` crónico (era el tope de 9 s del arranque, no un fallo real) y el aviso "carga incompleta" deja de aparecer.
- Nueva telemetría honesta: si un arranque supera los 6 s la app reporta `BOOT_SLOW` con el desglose de ms por fase (semilla/caché/portal), para que podamos ver dónde va lento de verdad y no volver a adivinar.

## Alertas de diagnóstico (bot de Telegram)
- **Reincidencias**: si una huella que ya se avisó vuelve a reportarse — otra versión, un equipo nuevo o tras el autocierre — el bot avisa de nuevo («Reincidencia», «Regresó», «Sigue ocurriendo»). Antes avisaba una sola vez y ya: un bug vivo en una versión vieja se quedaba sin aviso.
- **Crashes con mensaje**: los reportes de crash ahora traen el mensaje del sistema («Key … was already used»), que en la pantalla de Deportes habría identificado la causa al instante (la huella no cambia, así que las alertas viejas no se duplican).
- **Reproductor con contexto**: los ANR del proceso del reproductor ya reportan pantalla y fase (vivo/vOD/arranque/error), y la red de seguridad del reproductor residente deja un aviso cuando se autocorrige.

## Físico
Sin cambios de motor ni de réplicas; la prueba de campo es el arranque del teléfono que tengas a mano tras actualizar.

— ChronosBVRX
