# Vision+ 2.5.139

**Tele corrige: la licencia ya no se rinde con el portal lento**
- La TCL reportó `PLAYBACK_HANDOFF_FAIL · sin-medios` con el portal a **47 s** y el tope de lectura en 25 s: la petición de licencia se cortaba y la reproducción moría sin mensaje.
- Ahora, si la licencia normal y su reintento con sesión nueva no traen medios, hay un **último intento con más paciencia** (25 s de conexión, 60 s de lectura). En redes rápidas no cambia nada.
- Si aun así no hay medios, la app reporta **`PLAYBACK_LICENSE_FAIL` con el motivo exacto** (código del portal, timeout o «sin medios») y cuánto tardó. Se acabó el «sin-medios» a ciegas.

**Sin cambios** en el reproductor ni en cómo se reproduce.

— ChronosBVRX
