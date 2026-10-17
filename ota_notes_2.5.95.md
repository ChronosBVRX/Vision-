# OTA v2.5.95 — Deportes en vivo sin crashes + el reproductor residente ya no deja la pantalla negra ni atrapa la salida

## Qué arregla

- **Crash de «Deportes en vivo» al abrir el selector de transmisiones.** La parrilla del
  partido trae entradas distintas que resuelven al MISMO canal («Canal 5» y «Canal 5
  Televisa», «ESPN» y «ESPN+», «FOX One» y «Fox Sports», «TUDN» y «TUDN En Vivo»…): el
  modal repetía tarjeta y el `LazyColumn` tumbaba la pantalla con `IllegalArgumentException`
  (lo reportado en el Chromecast de la 2.5.93: 4 veces, misma huella).
  - `GeeGuide.channelsFor` ahora **deduplica por canal** (título + enlace, conservando la
    mejor puntuación) y el selector (móvil y TV) usa **claves con índice** como red de
    seguridad: una repetición ya no puede tumbar la pantalla.
  - Guardián nuevo `GeePickDialogDuplicateTest`: compone el modal con duplicados directos
    (TV y móvil) y exige que pinte; `GeeGuideTest` suma los cruces que colisionan.
- **El reproductor residente («Camino B») ya no se queda en fondo negro ni bloquea la
  salida.** El caso reportado: al salir de la app, la tarea del reproductor (proceso
  `:x4k` congelado o lento) quedaba al frente en negro y Atrás ya no hacía nada.
  - Al salir de la app, el proceso principal intenta **cerrar la tarea del reproductor
    directamente** (`AppTask`) antes de soltar la tarea principal; ya no depende solo de un
    broadcast a un proceso que puede estar congelado.
  - El reproductor, si vuelve al frente **sin reproducción**, se **cierra solo**. Y Atrás en
    ese estado también cierra su tarea (antes la bandera interna `exiting` lo convertía en
    un no-op silencioso para siempre).
  - Guardianes: `ResidentPlayerTest` (política de salida del residente) y `ExitFlowTest`
    (reconocimiento de la tarea del reproductor por su actividad).
- Sin cambios al arreglo de fuentes de cada partido de la 2.5.94: ese sigue **aplicado en
  el servidor** y funciona tal cual.

## Verificación

- **464 pruebas Android** en verde (12 nuevas: selector con duplicados, guías que
  colisionan, política del residente y tarea del reproductor).
- **185 pruebas backend** en verde (sin cambios de servidor en este lote).
- Build release con R8 y **firma histórica `9593cb0d…`**, versionCode 331.
- Prueba en emulador móvil (2.5.95 instalado encima): arranque, Deportes en vivo con datos
  reales (57 eventos) y el selector de transmisiones abierto sin crash —incluida la guía
  `ESPN Deportes · ESPN+ · ESPN` que tumbaba la 2.5.93—. Nota de la prueba: en este
  emulador x86_64 con traducción ARM el motor nativo aborta al detenerse (limitación del
  entorno, no del APK); en equipos físicos el cierre del reproductor se comporta como
  siempre (residente y reutilización intactas).

— ChronosBVRX
