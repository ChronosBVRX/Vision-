# Vision+ 2.5.134

**El reproductor deja de matar un cuadro que está entrando**
- En una TCL se midió que el primer cuadro llegó **31 ms después** de que saltara el re-arranque interno: el `stop` del re-arranque mataba la reproducción que ya estaba entrando (parpadeo negro y otra espera). Ahora hay una **ventana de gracia de 1.5 s**: si el cuadro llega, no se toca el motor.

**Medición honesta del arranque**
- `PLAYER_START` cuenta **desde el toque** (antes se reiniciaba con cada re-arranque y llegó a reportar 3.9 s cuando habían pasado ~25 s) e incluye `intentos`.
- `PLAYER_SOFT_RESTART` ahora reporta el estado real del motor al rendirse: `pos`, `dur`, `playing` y `engineBoot`. Eso distingue «no llegó nada» (P2P/red) de «llegó y no pintó» (decodificador) sin adivinar.

**Sin cambios** en la reproducción, en la preparación ni en el empaquetado del motor.

Verificado: suite Android en verde; release R8 con la firma histórica.

— ChronosBVRX
