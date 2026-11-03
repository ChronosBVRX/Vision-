# Vision+ 2.5.185 (pruebas) — «Seguir viendo» del launcher al instante y sin carreras

**Canal beta. No es la OTA estable.**

## Qué cambia

### Launcher y app sincronizados
- El launcher ya no espera los 10 minutos de caché ni el freno de 20 s del feed: cuando el
  historial cambia (ver, terminar, borrar o vaciar), el provider detecta la escritura del
  XML y reemplaza **solo la fila «Seguir viendo»** al instante.
- El launcher escucha el aviso del provider y hace un segundo refresco corto al volver del
  reproductor (1.2 s) para recoger el minuto que `:x4k` confirma al salir.
- **Sin carreras:** si el historial cambia mientras el feed completo se está reconstruyendo,
  al terminar se vuelve a parchear «Seguir viendo» para que el rebuild viejo no pise el dato
  nuevo.
- Si terminaste un capítulo y quedan más, el launcher muestra **«Siguiente capítulo»** (barra
  en 0) y abre la ficha de la serie, igual que el Inicio de la app.
- Las series abren su ficha desde el launcher; las películas reanudan directo. Se conserva el
  enlace de reanudación como respaldo.

### Se mantiene
- Selector de capítulos con `C# · nombre` y miniatura por episodio.
- Duración real del capítulo en series (no la de la temporada).
- Tarjeta de créditos/siguiente capítulo, con fallback en el último minuto si no hay marcador.
- Servidor de episodios corregido (CIDs con letra y sesión del worker).

## Versión

- App **2.5.185 / vc425**.
- Launcher **1.3.4 / vc27**.

## Evidencia

- Android: **1095 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Contrato: **22 tests**, 0 fallos.
- Release R8 compilado y firmado con la llave histórica `9593cb0d…`.

## Cómo probar

1. Instalar `VisionPlus.apk` y `VisionPlusHome.apk` de este release.
2. Abrir una serie, ver un capítulo y salir al launcher: «Seguir viendo» debe mostrar el
   capítulo/minuto actual sin esperar.
3. Terminar un capítulo con siguiente: la tarjeta debe decir «Siguiente capítulo».
4. Vaciar el historial desde Ajustes y volver al launcher: la fila debe desaparecer.
5. Repetir la salida varias veces seguidas para comprobar que no se queda con una tarjeta
   anterior por una reconstrucción en curso.

— ChronosBVRX
