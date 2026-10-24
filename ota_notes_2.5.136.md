# Vision+ 2.5.136

**Hotfix: vuelve el motor de siempre**
- La 2.5.135 metió el motor dentro del APK (`classes2/3/4.dex`) y en un **Chromecast dejó de reproducir sin dar error**: el motor moría de forma nativa antes de pintar. Se apaga ese camino.
- El motor vuelve a cargarse desde `assets/x4k` como hasta la 2.5.134, que es lo que funciona en todos los equipos.
- El código del motor en el APK queda detrás de la bandera remota `engine_bundled`, **apagada por defecto**, hasta probarlo en un equipo real con ADB.

**Sin cambios** en el reproductor, en la preparación ni en el catálogo.

— ChronosBVRX
