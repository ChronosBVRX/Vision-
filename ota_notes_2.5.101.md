# Vision+ 2.5.101 — El reproductor de Deportes en vivo ahora se ve como el de TV en vivo

Cuando abres un canal o una señal de **Deportes en vivo**, el reproductor ya tiene **la misma cara que el de TV en vivo**, con todo lo suyo de antes intacto.

## Lo que ves ahora (igual que en TV en vivo)
- **Aviso de cable** arriba, con la píldora **EN VIVO**, la etiqueta de la liga, el **título del partido** en grande y las líneas con etiqueta azul:
  - **AHORA:** la señal que estás viendo (y si es HLS o DASH · ClearKey).
  - **MARCADOR:** el resultado y el minuto en vivo (o **se transmite en**, si aún no hay marcador).
- **Píldoras etiquetadas** abajo, como las del otro reproductor: **Canales** (el selector de señales del evento) e **Información**, más las de este reproductor — **Pausa/Reproducir** y **Audio y subtítulos** — con la misma piel y el mismo resaltado al enfocar con el mando.
- **Información** abre la ficha del partido: liga, marcador, la señal en pantalla, dónde se transmite y **todas las señales del evento** con la actual marcada.
- La lista de **Canales** ahora dice también el formato de cada señal (HLS / DASH · ClearKey) y cuál está sonando.
- El **zapping** ya no muestra una píldora aparte: el aviso de cable es el que cuenta la señal nueva (se queda unos segundos a la vista y se va solo), igual que en TV en vivo.

## Lo que NO se tocó (sus funciones propias)
- Reconexión automática con «Reconectando… intento N» y **failover** entre señales.
- **ClearKey por señal** al zappear (cada DASH con sus llaves).
- Zapping con **Arriba/Abajo en dos toques** y el ciclo completo del evento.
- El respaldo en tu lista, los subtítulos/audio y el resto del reproductor en línea.

## Y los partidos ya traen TODAS las fuentes de la app original
- Había un motivo real por el que un partido podía salir con menos fuentes que en la app original: cuando el CMS rota una entrada fuera de su archivo del día, nuestro espejo se quedaba con la lista **vieja** (visto en vivo con **Chivas vs Tigres**: llevaba horas con 2 fuentes mientras el CMS ya publicaba 3).
- Arreglado **en el servidor** (aplica al instante, sin esperar a esta actualización): si la entrada de un partido no viene en el archivo del ciclo, su lista **se vuelve a leer** del CMS en vez de quedarse congelada.
- Verificado con Chivas-Tigres: ahora salen las **3 señales** — TUDN MEXICO FHD, **LOW QUALITY (USE VPN)** y CANAL MX FHD — y la nueva **reproduce** (probada en el emulador).

## Pruebas
- **566 pruebas** de Android en verde (nuevas: el aviso de cable y las píldoras con las etiquetas de TV en vivo, el contexto del partido viajando al reproductor, la ficha de Información y el rotulado del tipo de señal).
- **193 pruebas** del servidor en verde (nuevas: el espejo que re-lee el archivo del partido cuando el CMS lo rota, y que conserva lo guardado si el archivo ya no está).

— ChronosBVRX
