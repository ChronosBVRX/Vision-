# OTA v2.5.90 — Fin del crash de «Deportes en vivo» (id estable por evento) y del D-pad en TV

## Qué corrige
1. **Crash real en «Deportes en vivo» (visto en vivo)**: la lista de eventos usaba
   `liga|fecha|hora` como clave cuando el evento no traía id, y dos partidos distintos
   del mismo bloque a la misma hora («NBA Preseason» 20:00) compartían clave → la lista
   moría. Ahora **cada evento viaja con un id estable propio** (el servidor lo calcula
   con los equipos incluidos y además lo rellena para eventos viejos) y la app
   deduplica/fusiona duplicados reales antes de pintar. La protección es doble: el
   servidor ya protege incluso a versiones anteriores instaladas, y esta versión suma
   la red de seguridad en la app.
2. **D-pad de Smart TV — la clase completa de crash por `FocusRequester`**: cuando el
   destino de una tecla «Abajo» (o un chip de descarga) se desmontaba — tarjeta
   reciclada al desplazarse, chip ausente por configuración — la navegación del mando
   intentaba enfocar un elemento inexistente y tumbaba la pantalla. Ahora, si el
   destino no está disponible, **la tecla sigue su curso normal**: nunca más un crash,
   y el atajo de foco se conserva cuando el destino sí existe.

## Automatización (sin cambio visible en la app)
- CI reparado (dos roturas encadenadas: paquete `tools` retirado por Google y una ruta
  de Java local de Windows dentro del repo) — **cada push ahora se valida solo** y
  `main` quedó protegida con los 3 checks.
- En el servidor: respaldo diario del catálogo con retención y vigilante de la API
  cada 5 minutos (reinicio automático si deja de responder).

## Verificación
- 430 tests Android + 170 backend en verde.
- Release R8 instalado en el POCO: Deportes → Básquet → tarjeta → modal, sin crashes;
  el servidor ya sirve ids únicos (141 eventos, 0 duplicados).
- Firma histórica `9593cb0d…`, versionCode 326.

— ChronosBVRX
