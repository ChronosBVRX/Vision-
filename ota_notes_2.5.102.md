# Vision+ 2.5.102 — Deportes en vivo: la lista solo con las fuentes de GeeSports y el respaldo, siempre automático

Ajuste pedido por ti para que nadie se confunda: **la lista de un partido enseña solo lo que da GeeSports**, y el respaldo trabaja **solo**, sin botones ni filas de más.

## La lista: solo GeeSports
- En «Canales en vivo» y «Transmisiones en vivo» sale **únicamente** lo que publica la fuente deportiva — sus canales 24/7 y sus enlaces del partido — con su estado (**Disponible** / **Caído**).
- **Ya no se listan los canales del catálogo de la casa**: nada de «Respaldo en tu lista» ni filas nuestras mezcladas. Lo que ves es lo que da GeeSports.
- La fila que está caída lo avisa en su propia línea («· respaldo automático») para que sepas que se arregla sola.

## El respaldo: automático y siempre en segundo lugar
- **Si tocas una fuente caída**, la app reproduce al instante **esa misma cadena confirmada de nuestro catálogo**, sin preguntar («Telemundo» caído → se abre TELEMUNDO, en el reproductor de TV en vivo).
- **Si estás viendo un partido y se caen todas sus señales** (tras los reintentos y el failover entre señales), el reproductor **cambia solo** al canal confirmado del catálogo en vez de dejarte la tarjeta de error. Lo deja anotado en el diagnóstico (`PLAYER_FAILOVER_XTV`).
- **GeeSports manda siempre**: con cualquier señal suya viva, se usa la suya y el respaldo no se toca (ni se calcula). Solo entra cuando la fuente deportiva no puede servir esa cadena.
- El respaldo sigue siendo de **canales 100 % confirmados** (misma marca y edición, de la parrilla oficial del partido).

## Y los partidos traen todas las fuentes de la app original
- El arreglo del espejo (del lote anterior) ya está **en el servidor**: si el CMS rota una entrada fuera de su archivo del día, la lista del partido se **vuelve a leer** en vez de quedarse congelada. Verificado con Chivas-Tigres: las **3 fuentes** (TUDN MEXICO FHD, LOW QUALITY (USE VPN) y CANAL MX FHD).

## Verificado en el emulador
- La lista del partido mostró **solo** las fuentes de GeeSports (Telemundo / TUDN MX / Vix Deportes, las tres «Caído» con su aviso de respaldo automático, y TUDN MEXICO FHD «Disponible»).
- Al tocar **Telemundo (caído)** se abrió solo **nuestro canal** (`cyx_TELEMUNDO1USA`) en el reproductor de TV en vivo, sin crash y sin preguntar.

## Pruebas
- **567 pruebas** de Android en verde: el modal ya no pinta respaldos (solo fuentes de GeeSports), tocar una caída lleva al canal de la casa, una fuente sana **gana siempre** aunque exista respaldo, y el reproductor pide el respaldo solo al agotarse.
- Nota interna: un cambio de firma en el reproductor hizo que ART rechazara la clase (`VerifyError`) en la build de pruebas; se corrigió pasando el respaldo por el puente del proceso sin tocar la firma (la versión que iba a publicarse nunca tuvo ese fallo).

— ChronosBVRX
