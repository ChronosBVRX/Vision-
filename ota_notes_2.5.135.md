# Vision+ 2.5.135

**El motor de video ahora viaja dentro del APK (como Crystal)**
- Los 3 DEX del reproductor pasan de `assets/x4k` a `classes2/3/4.dex` del APK. Android los deja **verificados al instalar**, así que la app ya **no los copia ni los verifica en runtime**: se acaban los 17 MB de extracción, el arranque en frío más largo y el `VerifyError` de ART que dejó muda a una tele.
- Las librerías nativas se **precargan por ruta absoluta**, porque el APK también trae las del motor legado con los mismos nombres y `System.loadLibrary` habría elegido las equivocadas.
- **Red de seguridad doble**: si el APK no trae las clases, la app sigue usando los assets de siempre; y si las libs no precargan, también. Los assets se conservan en esta versión (por eso el APK crece temporalmente a 117 MB).

**Verificado**
- En el emulador con el APK **publicado**: `SDK X4K desde el APK (sin extraer): env=true program=true view=true` y `motor X4K arrancado`.
- La inyección y la **re-firma con la llave histórica** las hace el pipeline de publicación; si algo falla, no se publica (se comprueba la firma y que existan `classes2/3/4.dex` y los assets de respaldo).

**Siguiente**
- En la próxima se puede retirar el motor legado (Magis) y con él sus libs: el APK bajaría ~31 MB. Se dejó para después de ver esta versión funcionando en los equipos.

— ChronosBVRX
