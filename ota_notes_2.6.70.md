# Vision+ 2.6.70 (pruebas) — Audio/subtítulos del 4K y badge «4K disponible»

Actualización sobre 2.6.69, **solo para el canal de pruebas**.

## El 4K usa los mismos selectores del catálogo

- En el 4K ya se pueden elegir **idioma de audio** y **subtítulos** con las píldoras
  nativas del catálogo, igual que en 1080p. Se alimentan de las pistas reales del
  archivo (ExoPlayer), no de una lista fija.
- El audio sigue prefiriendo español latino/castellano; si el 4K no lo trae, entra en
  inglés/original y **activa los subtítulos en español** cuando existen. La elección
  manual del usuario manda y no se pisa.
- El selector de calidad etiqueta el 4K como **«4K · original»** cuando el archivo no
  tiene audio español.

## Resolver el 4K aunque el 1080p ya haya arrancado

- Al pulsar Play, el 4K se sigue resolviendo en **segundo plano** con WorkManager aunque
  el reproductor ya esté en 1080p.
- El reproductor nativo consulta la caché; cuando el 4K aparece, la píldora de calidad se
  convierte en el badge azul **«4K disponible»** (o **«4K disponible · original»** si solo
  hay audio original) y se muestra un aviso.
- El badge es interactivo: al tocarlo se abre el selector y se puede saltar al 4K sin
  salir del reproductor. El 1080p sigue reproduciéndose mientras tanto.

## Verificación

- Compila y las pruebas de cableado cubren: píldoras externas, badge, resolución en
  segundo plano y etiqueta de idioma original.
- Android: **1,369 pruebas**; solo los **3 fallos ambientales conocidos**.
- Solo se publica en el tag `beta`; la estable sigue en 2.6.67.

— ChronosBVRX
