# OTA v2.5.93 — Atrás confiable: el D-pad ya no se pierde al volver de una serie + cierre con confirmación

## Qué trae la app
- **El D-pad ya no se pierde al volver de una serie**: al regresar al Inicio, la app
  revelaba la fila equivocada cuando la fila «Populares de la semana» estaba presente
  (siempre, en equipos con internet): el índice del regreso no la contaba, el scroll caía
  una fila arriba y el foco quedaba lejos o el mando muerto. Corregido y con guardián
  (`returnRowIndexCountsTheWeeklyTrendingRow`).
- **Atrás en la pantalla principal ahora pide confirmación**: el primer Atrás muestra el
  aviso «Presiona atrás de nuevo para cerrar la aplicación»; el segundo, dentro de la
  ventana corta, cierra la app de verdad. Si pasa el tiempo, el aviso se oculta solo y no
  se cierra (jamás un cierre por accidente).
- **Se acabó el bucle «Atrás → reproductor»**: al cerrar desde el menú principal la app
  también cierra al reproductor residente (que vive en su propia tarea): antes el sistema
  lo traía al frente al soltar la actividad principal y quedabas atrapado dentro del
  reproductor sin poder salir por más que presionaras Atrás.
- **Rescate del mando**: si al volver la tarjeta exacta ya no existe (contenido cambiado),
  el Inicio vuelve al principio de la columna para que el héroe tome el foco — nunca más
  un mando muerto.
- El regreso pendiente se invalida al salir del Inicio por un hub (En vivo, Categorías,
  Continuar): no se re-dispara en la siguiente vuelta.

## Verificación
- 447 tests Android en verde (nuevos: `ExitFlowTest` — la política del doble Atrás — y
  `TvRescueTest.returnRowIndexCountsTheWeeklyTrendingRow`; los 18 guardianes de foco de TV
  siguen verdes, incluido el regreso con contenido que llega tarde).
- Probado en emulador: primer Atrás → aviso visible (y NO cierra); el aviso se oculta solo
  pasada la ventana; doble Atrás → la app se cierra limpia (tarea fuera, lanzador al
  frente) y vuelve a abrir con normalidad.
- Firma histórica `9593cb0d…`, versionCode 329.

— ChronosBVRX
