# Vision+ 2.5.99 — Deportes en vivo: el candado de horario ya se abre a su hora

Corrección del candado que bloqueaba los partidos hasta su hora: **ya no se queda pegado** cuando el partido empieza.

## Qué pasaba
- La app decidía si un partido se podía ver con el estado que publica la agenda deportiva (el mismo proveedor de horarios que usa el resto del mundo). Ese estado **tarda varios minutos** en pasar de «aún no comienza» a «jugándose».
- Mientras el proveedor llegaba tarde, el partido quedaba **bloqueado aunque la hora ya hubiera pasado**: aparecía en «Comienzan pronto», la tarjeta no decía EN VIVO y al abrirlo solo se veía dónde se transmitía. Ese era el candado que no se desbloqueaba.

## Qué se corrigió
- **El reloj manda para abrir**: pasada la hora anunciada del partido, se abre al instante — pasa al bloque **En vivo**, la tarjeta se pinta **EN VIVO** y el selector ofrece todas sus señales. Ya no hay que esperar a que el proveedor se ponga al día.
- **Se abre solo, sin tocar nada**: las pantallas de Deportes en vivo (móvil y TV) llevan un tic de reloj cada 30 segundos, así que si dejas la pantalla abierta justo a la hora del partido, el candado se levanta por sí mismo.
- Lo que **sigue igual de seguro**: un partido terminado jamás se reabre, y los partidos que aún no empiezan siguen bloqueados con su aviso. La ventana de «en vivo» sigue siendo de tres horas.
- De paso: si una hora se corrige, el horario viejo no se queda pegado; y una tarjeta que ya está en «En vivo» nunca vuelve a mostrar la hora en su lugar.

## Verificado
- En vivo y con datos reales: **Chivas vs Tigres UANL (17:07)** tenía el estado del proveedor todavía en «no comenzado»; con el arreglo pasó a **EN VIVO** con su píldora y su selector de señales completo (TUDN MEXICO FHD y CANAL MX FHD, listas para reproducir).
- 532 pruebas unitarias en verde (nuevas: el candado que se abre por reloj aunque la agenda llegue tarde, el partido terminado que no se reabre, la hora corregida que no se queda pegada y la tarjeta que dice EN VIVO).

## Ámbito
- Solo toca **Deportes en vivo** (agenda y mosaico, móvil y TV). El reproductor, el catálogo y todo lo demás quedan igual.

— ChronosBVRX
