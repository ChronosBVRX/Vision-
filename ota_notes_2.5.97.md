# Vision+ 2.5.97 — Deportes en vivo: zapping con el mando y señales que se defienden solas

Esta tanda es para el reproductor en línea de **Deportes en vivo** (el de las señales de los partidos): cuando una señal tropieza, la app se repone sola; y con el mando ya cambias de señal del partido en dos toques, como el zapping de toda la vida.

## Zapping del partido con el mando
- En un partido con varias señales, **Arriba/Abajo en dos toques** cambian a la señal siguiente/anterior (ciclo completo, wrap incluido), con aviso en pantalla del nombre durante unos segundos.
- Solo entran al ciclo señales **directas** del evento (HLS/DASH); los canales del catálogo siguen alcanzables por el selector de siempre.
- Nuevo botón **Canales** (icono de TV) en la fila del reproductor en vivo, con el selector de señales del partido: la actual marcada y el resto a un toque/OK.
- El armado del ciclo ocurre al abrir las opciones del partido (diálogo móvil y TV): ya no hay que salir del reproductor para probar otra señal.

## Señales que se defienden solas
- **Reconexión automática** con fines de reintento y píldora «Reconectando… intento N» antes de mostrar error: una microcaída del vivo ya no mata la pantalla.
- **Failover automático**: si una señal se rinde tras los reintentos y el evento trae más señales disponibles, la app sola pasa a la siguiente — verificado en vivo: señal caída → el video regresó solo por la siguiente señal.
- **DRM correcto por señal**: cada señal DASH con ClearKey recibe sus llaves propias al zappear (antes bastaba una llave equivocada para quedar en negro).
- Reproducción más paciente con el buffer del vivo y política de errores tolerante a blips de red.

## Ámbito
- Solo el reproductor en línea (Compose/ExoPlayer) de deportes y señales directas: el reproductor nativo de películas/series/canales del catálogo **no cambia en nada**.
- 482 pruebas unitarias en verde (nuevas: semántica del ciclo de señales, wrap-around, presupuesto de failover y del armado).

## Físico
- Verificado en emulador con partido real (LaLiga): zap con banner, failover automático de una señal caída a una viva y DASH·ClearKey descifrando por señal.
- Prueba física en TV con mando pendiente de confirmar en campo (el flujo D-pad es el mismo probado en emulador).

— ChronosBVRX
