# Vision+ 2.5.138

**APK de 105 MB → ~74 MB y limpieza del motor legado**
- Fuera del APK los DEX y librerías de respaldo del motor (**31 MB**): viajan en `classes2/3/4.dex` y en `lib/<abi>` desde la 2.5.137. El respaldo queda solo en las compilaciones de depuración, para desarrollo.
- Se retira el **motor legado** completo: sus clases (`MagisClient`, `MagisTitan`, `TitanSdk`, `TitanVodAty`), su pantalla de reproducción y su entrada del manifest. Ya estaba inerte desde la 2.5.137 y todo el catálogo se reproduce por XTV.
- Un canal que llegara con referencia vieja `magis:` se intenta por el motor XTV con el mismo código, en vez de usar el motor retirado.

**Sin cambios** en el reproductor, en la preparación ni en lo que se reproduce.

**Verificado**
- Compila en debug y release, suite completa en verde, y probado en **Chromecast HD** con el APK publicado: el motor arranca desde el APK y reproduce.

— ChronosBVRX
