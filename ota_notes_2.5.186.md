# Vision+ 2.5.186 — OTA oficial: hero completo + launcher sincronizado

**Versión estable. Ya disponible por OTA.**

## Qué cambia

### Hero
- El velo oscuro del hero cubre ahora **todo el banner, también el borde superior**.
  Antes el arte entraba limpio arriba y el gris solo se veía hacia abajo.
- Aplica al Inicio de la tele y al hero móvil; el launcher conserva su velo superior.

### Launcher sincronizado con la app
- «Seguir viendo» se actualiza al instante cuando ves, terminas, borras o vacías el
  historial: el provider detecta la escritura del XML y reemplaza solo esa fila.
- El launcher escucha el aviso y hace un refresco corto al volver del reproductor.
- Si el historial cambia durante un rebuild completo, se vuelve a parchear al terminar
  para que el rebuild viejo no pise el dato nuevo.
- Si terminaste un capítulo y quedan más, muestra «Siguiente capítulo» y abre la ficha
  de la serie; las películas siguen reanudando directo.

### Capítulos, duración y créditos
- Selector de capítulos con `C# · nombre` y miniatura por episodio.
- Duración real del capítulo en series (no la de la temporada).
- Tarjeta de créditos/siguiente capítulo; si no hay marcador, aparece en el último minuto
  con «Seguir viendo» / «Reproducir ahora».
- Servidor de episodios corregido: acepta CIDs con letra y renueva la sesión del worker.

## Versión

- App **2.5.186 / vc426**.
- Launcher **1.3.4 / vc27**.

## Evidencia

- Android: **1096 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Contrato: **22 tests**, 0 fallos.
- Release R8 compilado y firmado con la llave histórica `9593cb0d…`.

## Cómo probar

1. Actualizar desde la app (OTA) o instalar `VisionPlus.apk` y `VisionPlusHome.apk`.
2. Abrir el Inicio de la tele: el hero debe verse cubierto arriba y abajo.
3. Ver un capítulo y salir al launcher: «Seguir viendo» debe mostrar el minuto actual.
4. Terminar un capítulo: la tarjeta debe ofrecer el siguiente.
5. Vaciar el historial: la fila debe desaparecer del launcher.

— ChronosBVRX
