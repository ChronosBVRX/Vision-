# Vision+ 2.5.100 — Deportes en vivo: respaldo en tu lista y un vigilante que no deja de mirar las señales

Dos cosas nuevas para los partidos, pensadas para que nunca te quedes sin poder verlos.

## El respaldo: si su canal está caído, se ofrece el nuestro
- Cada partido trae su parrilla oficial («Se transmite en: Telemundo · TUDN · ViX Deportes»). Si una de esas cadenas **no se puede ver** por la fuente deportiva (su canal está caído), la app ofrece **esa misma cadena del catálogo de la casa**, lista para reproducir, **pegada a la fila caída**.
- **Solo con canales 100 % confirmados**: misma marca y misma edición, jamás un parecido. «TNT Sports» nunca resuelve a «TNT Series»; «Fox Sports» no se cubre con «Fox Sports 2»; «TUDN USA» no se cubre con la señal mexicana.
- **La fuente deportiva siempre manda**: mientras su opción esté viva no aparece ningún respaldo, y **en cuanto se recupera, el respaldo desaparece solo** y ella vuelve a ser la primera opción.
- Ejemplo real (Chivas vs Tigres): «Telemundo — Caído» → debajo, **TELEMUNDO EAST HD · Respaldo de Telemundo · Confirmado**, verificado reproduciendo en el reproductor de la casa.

## El vigilante: comprueba él mismo, sin parar
- Mientras tienes abierta la pantalla de **Deportes en vivo**, la app **comprueba cada 30 segundos** las señales de los partidos que se están jugando: una petición corta al manifiesto con sus cabeceras, la misma prueba que hace el servidor pero al momento y en tu equipo.
- Por eso una **caída se ve al instante** (el canal se marca «Caído» y su respaldo aparece enseguida) y un **levantamiento también** (vuelve a «Disponible» y el respaldo se va).
- Comprueba **todas** las señales del partido —también las caídas, porque pueden volver— y solo mientras la pantalla está abierta: no gasta nada en segundo plano, y se limita para no competir con la reproducción.

## Ámbito
- Solo **Deportes en vivo** (móvil y TV). El reproductor, el catálogo y todo lo demás quedan igual.

## Pruebas
- **561 pruebas unitarias en verde**: el cruce del respaldo (con los casos que deben quedar fuera), el vigilante (qué se comprueba, el tope, quién manda cuando la app ya comprobó) y una prueba **contra un servidor HTTP de verdad** en el equipo (manifiesto que responde, error, puerto muerto y basura).

## Verificado
- En vivo, con el partido de Chivas: «Telemundo — Caído» con **TELEMUNDO EAST HD** debajo, y al tocarlo abrió el canal del catálogo (`cyx_TELEMUNDO1USA`) en el reproductor de la casa. El vigilante registró su ronda («16 señales comprobadas, vivas 15») cada 30 segundos.

— ChronosBVRX
