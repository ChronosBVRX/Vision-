# OTA v2.5.79 — Re-arranque interno (adeu al «error al primer intento») + vivo y foco

## Qué arregla (reportes de hoy)
1. **Series que fallan al primer intento** (Chromecast: «error y al reintentar ya
   reproduce»; TV box: nunca abría): el motor aceptaba el `play` pero la primera
   pasada del P2P se quedaba corta o tardaba >20 s en la caja lenta. Ahora el
   reproductor **re-arranca el motor solo** (stop + play interno, sin pintar error)
   a los 25 s sin señal; el error real solo llega a los ~60 s (35 s tras el
   re-arranque). Si el cuadro llega después, el tic lo recupera y esconde el error.
2. **TV en vivo en negro sin salida**: el overlay de espera se ocultaba a los 1.2 s
   con solo estar "en vivo" (nunca mostraba error ni recuperaba). Ahora espera la
   señal **real** del motor (posición o decodificador) o 12 s de cortesía, y el
   error se mide por señal real (no por temporizador).
3. **«0 canales» en el selector/zapping del vivo**: si el proceso `:x4k` no tiene el
   catálogo cacheado (prefs cruzadas de proceso), el selector se reconstruye con los
   códigos que viajan en el intent (nunca queda vacío).
4. **D-pad perdido al volver de un canal en vivo**: `TvLiveScreen` registra la
   tarjeta del canal abierto (`returnChannelCode` por TvApp) y el regreso enfoca
   **esa misma tarjeta**; si no está compuesta, cae a la primera visible o al
   destino de la pantalla (mismo patrón que las fichas).

## Extra (lado dispositivo, no APK)
- TV box ZTE: el firmware tenía un `sh` de consola en bucle comiendo un núcleo
  (`init.svc.console`, PID 1 → 100 % CPU). Detenido con `setprop ctl.stop console`.
  Si vuelve tras reiniciar: `adb shell su -c 'setprop ctl.stop console'`.

## Verificación
- 355 Android + 122 backend en verde.
- TV ZTE: instalación **limpia** (desinstalado + datos, restos y filas del launcher
  borradas), alta de sesión propia automática (`auto-alta en el portal: ok`), serie
  reproduciendo con video y «Omitir resumen», foco D-pad vivo.
- Firma histórica `9593cb0d…`, versionCode 315.
