# Vision+ 2.6.69 (pruebas) — Interfaz única para el 4K

Actualización sobre 2.6.67, **solo para el canal de pruebas**.

## El 4K dentro del reproductor de siempre

- El 4K ya no abre otro reproductor: se reproduce **dentro de `X4KPlayerAty`**, el mismo
  Activity del VOD de Xuper, con su barra, su mando, su PIP y su selector de calidad.
- El 4K entra por **ExoPlayer** dentro del Activity nativo; las calidades del portal
  siguen entrando por el motor Titan. El usuario no cambia de pantalla al alternar.
- La píldora de calidad muestra **«4K»** y el selector conserva las calidades del portal.
  Al elegir una calidad nativa, el mismo Activity cambia al motor Titan sin salir.

## Audio y subtítulos

- En el 4K, ExoPlayer prefiere **español latino/castellano**; si el archivo no lo trae,
  usa inglés/original y **activa los subtítulos en español** cuando existen.
- En esta beta de la interfaz única, las píldoras de audio/subtítulos del 4K quedan
  ocultas: la selección es automática. En las calidades nativas se usan las píldoras
  del portal de siempre.

## Estado de la prueba

- Verificado en emulador: el clip 4K H.264 se reproduce **dentro de `X4KPlayerAty`** con la
  barra nativa, el tiempo y la píldora de calidad **«4K»**. El video sale por ExoPlayer y
  las calidades del portal siguen en el mismo selector.
- Es un **POC de beta**: la interfaz ya es la misma, pero faltan pulir los selectores
  manuales de audio/subtítulos del 4K y probar el selector en TV.
- El decodificador del emulador no soporta HEVC Main10; el 4K de HdHub se prueba con un
  clip H.264 y en equipos 4K reales con la fuente directa.
- Android: **1,368 pruebas**; solo los **3 fallos ambientales conocidos**.
- Solo se publica en el tag `beta`; la estable sigue en 2.6.67.

— ChronosBVRX
