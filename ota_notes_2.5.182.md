# Vision+ 2.5.182 (pruebas) — capítulos con nombre y «Seguir viendo» de series

**Canal beta. No es la OTA estable.**

## Qué cambia

### Selector de capítulos
- Cada capítulo muestra su **número real** y su **nombre** cuando la base de datos lo tiene:
  `C3 · El titán bestia`.
- Si la temporada tiene miniaturas por episodio de TMDB, se usa esa imagen; si no, se conserva
  el póster de la serie.
- También aplica al selector de capítulos dentro del reproductor.

### Seguir viendo
- En **Inicio** (móvil y tele), **Historial de Mi lista** y la fila de **Vision+ Home**, una
  tarjeta de serie abre la **ficha de la serie**, no el reproductor a ciegas.
- La ficha aterriza en la temporada donde se quedó y ofrece
  **«Continuar donde te quedaste»** con el capítulo y el minuto exactos.
- El botón principal empieza el siguiente capítulo desde cero y el selector permite escoger
  cualquier capítulo.
- Si terminaste un capítulo pero quedan más, la serie **ya no desaparece** de «Seguir viendo»:
  la tarjeta muestra «Siguiente capítulo» y abre la ficha.
- La fila «Seguir viendo» queda **arriba de todas las categorías** del Inicio.
- Se reconoce la serie por su temporada activa, sus temporadas o su nombre base, no por el
  código del episodio; si el catálogo ya no tiene la ficha, se arma una mínima para abrir el
  menú de la serie igual.

### Vision+ Home
- La fila «Seguir viendo» del launcher también abre la ficha de la serie cuando es un capítulo.
- Si el feed no trae el enlace nuevo, sigue funcionando el enlace de reanudación anterior.

## Versión

- App **2.5.182 / vc422**.
- Launcher **1.3.3 / vc26**.

## Evidencia

- Android: **1090 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Contrato: **22 tests**, 0 fallos.
- Release R8 compilado y firmado con la llave histórica `9593cb0d…`.

## Cómo probar

1. Instalar la app y, si se quiere la fila nueva del launcher, `VisionPlusHome.apk`.
2. Abrir una serie y comprobar que el selector muestra `C# · nombre` y la miniatura cuando existe.
3. Ver un capítulo, salir y volver al Inicio: «Seguir viendo» debe estar arriba y abrir la ficha.
4. En la ficha, usar «Continuar donde te quedaste» y comprobar que abre el minuto exacto.
5. Terminar un capítulo con siguiente: la serie debe seguir en «Seguir viendo» como
   «Siguiente capítulo».

— ChronosBVRX
