# OTA v2.5.80 — Arranque más rápido en Smart TV (handshake adelantado)

## Por qué tardaba más en la TV que en el celular (y que en la app original)
1. **La caja es lenta**: cada llamada al portal (TLS + JSON) tarda 0.2-0.7 s
   contra 0.05-0.15 s del celular, y el motor tarda más en unirse al P2P.
2. **El handshake ocurría al pulsar Reproducir**: licencia (`startPlayVOD`) +
   ficha (`getItemData`) + SLB si había expirado — en serie. En la TV eso son
   1-4 s antes de que el reproductor siquiera abriera. La app original del
   proveedor pide esa licencia **al abrir la ficha**, por eso se siente instantánea.
3. Además, en esa caja el firmware tenía un proceso de consola en bucle comiendo
   un núcleo (detenido en la sesión anterior).

## Qué hace esta versión
- **Handshake adelantado**: al abrir la ficha (película o el capítulo que toca de
  una serie) se piden la licencia y los marcadores en segundo plano y se guardan
  (90 s, una sola reproducción). Al pulsar Reproducir el reproductor sale ya con
  el payload listo — el salto es inmediato. Si algo falla o caduca, el flujo
  normal hace las llamadas como siempre (cero riesgo).
- **Una llamada menos en el crítico**: la ficha editorial ya no se pide al
  reproducir cuando el catálogo ya conoce la duración (el reproductor la
  recupera en segundo plano para el rótulo).

## Verificación
- 355 Android + 122 backend en verde.
- La mejora es de arranque/handoff (el reproductor no cambió); se sentirá sobre
  todo en el celular/Chromecast al actualizar y en la TV al volver a habilitar la
  depuración (queda pendiente la prueba física ahí).
- Firma histórica `9593cb0d…`, versionCode 316.
