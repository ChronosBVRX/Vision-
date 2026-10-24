# Vision+ 2.5.131

**La tele vuelve a reproducir (ZTE B866V2 / Android 12)**
- En esa caja el verificador de ART rechazaba dos clases del motor (`yc.h`, `yc.e`) y el proceso del reproductor moría: el menú y las fichas cargaban, pero **no abría nada**. La app se auto-reparaba y volvía a fallar, porque re-extraía los DEX **en la misma ruta** y ART reutilizaba su verificación dañada.
- Ahora el motor se extrae en una **generación nueva** (`x4k-rN`): cada reparación estrena ruta de DEX, así ART no puede reutilizar su `vdex`/`oat`. Al reparar también se limpian los artefactos de ART del equipo.
- El sello de la extracción guarda el **sha256** de cada DEX (antes solo el tamaño): una copia corrompida que mide lo mismo se detecta y se vuelve a copiar.
- `yc.e` y `ed.e` se verifican **antes** de arrancar el motor (antes se tragaba el error y reventaba a media reproducción).
- Última palanca: si el equipo sigue rechazando un DEX que en el APK está intacto, el motor se carga **en memoria** (sin archivos ni artefactos de ART).

**Telemetría**
- Se distingue `ENGINE_DEX_MISMATCH` (la copia del equipo está dañada) de `ENGINE_VERIFY_FAIL` (ART rechaza bytes idénticos al APK), con la generación usada, y `ENGINE_MEMORY_OK` cuando el rescate en memoria funciona.

**Debajo**
- La carpeta legada `files/x4k` se libera al actualizar (17 MB en equipos con poco espacio).

Verificado: 853 tests Android en verde; release R8 con la firma histórica. En la tele afectada, al abrir Vision+ tras la actualización el motor se re-extrae y se estrena solo.

— ChronosBVRX
