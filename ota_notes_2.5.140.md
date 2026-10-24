# Vision+ 2.5.140

**El reintento del reproductor ya no espera 25 s cuando no llegó nada**
- En la TCL el primer intento pasó **26.9 s con el motor sin datos** (`pos=-1 dur=-1 playing=false`) y el reintento pintó el video en **3 s**. No era «va lento»: estaba atascado.
- Ahora, si el motor no tiene ni duración ni posición, el re‑arranque interno salta a los **10 s** (15 s en los reintentos) en vez de 25. Con datos reales se mantiene la ventana larga, para no cortar un buffering legítimo.
- Medido en la TCL tras el cambio: el video debería abrir en ~13 s en vez de ~30, y el vivo ya iba en 3.2 s al primer intento.

**Sin cambios** en el motor ni en la preparación de la reproducción.

— ChronosBVRX
