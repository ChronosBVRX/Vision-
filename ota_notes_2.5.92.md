# OTA v2.5.92 — Deportes en vivo en MOSAICO (móvil y Smart TV) y filtros compactos

## Qué trae la app
- **Parrilla en mosaico**: los partidos ya no van en lista — ahora es una cuadrícula
  de tarjetas profesionales, con los **escudos de los equipos al frente**, marcador
  real, minuto en vivo (EN VIVO · 23'), Final u hora, la guía de TV («TV: TUDN · Canal 5»)
  y el contador de señales vivas. Los bloques EN VIVO → COMIENZAN PRONTO → TERMINADOS
  se conservan, con encabezados a todo lo ancho de la cuadrícula.
- **Móvil**: mosaico de 2 columnas (se adapta solo en tablets/pantallas anchas) con el
  conteo de eventos junto al título.
- **Smart TV**: mosaico de 3 columnas pensado para el mando (bastón blanco en la tarjeta
  enfocada, OK abre el selector de transmisiones).
- **Filtros COMPACTOS en TV**: el encabezado lleva el conteo en línea (ya no es una fila
  aparte), los chips bajaron de altura y **días + ligas viven en una sola fila**. El área
  de filtros pasó de ocupar más de la mitad de la pantalla a ~una cuarta parte: ahora se
  ven 6 partidos a la vez en vez de 2-3.
- Misma lógica pura para el mosaico en móvil y TV (una sola fuente, probada): equipos,
  marcador, estado y letras de respaldo cuando un escudo no carga.

## Verificación
- 442 tests Android en verde (incluye los guardianes nuevos del mosaico:
  `GeeTilesTest` y `TvSportsMosaicTest` — parrilla, textos y foco de D-pad sin red).
- Prueba visual en emulador: mosaico móvil con 56 eventos, tarjeta → modal de
  transmisiones con la guía («Se transmite en…», canales en vivo, Cerrar).
- Render de la pantalla de TV verificado (mosaico de 3 columnas, filtros compactos,
  secciones y tarjeta enfocada con marco blanco).
- Firma histórica `9593cb0d…`, versionCode 328.

— ChronosBVRX
