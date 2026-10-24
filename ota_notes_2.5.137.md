# Vision+ 2.5.137

**El motor de video dentro del APK, ahora sí (Chromecast incluido)**
- La 2.5.135 falló porque el APK llevaba **dos motores con las mismas librerías** y `libranger-jni.so` no declara SONAME: Android cargaba la copia del motor legado y su `JNI_OnLoad` tumbaba el proceso. Verificado en el log nativo del Chromecast.
- Ahora `lib/<abi>` lleva **el build bueno del motor** (`libranger-jni`, `libijkffmpeg`, `libijkplayer`, `libijksdl`, `libtnet` y su `libc++_shared`), y el motor legado queda **inerte** (ya no se usa: todo se reproduce por XTV).
- Con eso, las clases del motor viajan como **`classes2/3/4.dex`** y se cargan con el classloader de la app: **sin extraer 17 MB, sin verificar bytecode en runtime** (adiós al `VerifyError` de ART en una ZTE) y sin precargar nada a mano.
- Se retiran los DEX del motor legado de `assets/titan` (**30 MB menos**). El APK baja de 101 MB a ~70 MB.

**Verificado**
- Emulador y **Chromecast HD (Android 14, armeabi-v7a)** con el APK publicado: el motor arranca desde el APK y **reproduce**.
- Si algo se portara raro en un equipo, la bandera remota `engine_bundled` apaga el camino nuevo y vuelve a los assets.

— ChronosBVRX
