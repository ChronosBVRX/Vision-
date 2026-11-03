# Vision+ 2.5.183 (pruebas) — duración real del capítulo y salto de créditos

**Canal beta. No es la OTA estable.**

## Qué cambia

### Duración correcta en series
- La ficha de una serie/temporada ya **no** se usa como duración del capítulo. Antes un
  capítulo de 42 min podía mostrar 1 h 40 (la duración del contenedor).
- En series, la duración la reporta el **motor con el archivo real** del capítulo. Si el
  portal no la trae, se muestra `--:--` un momento y luego la duración correcta.
- El historial viejo con una duración equivocada ya no se toma como verdad para series.
- La ficha (móvil y tele) tampoco pinta la duración de la temporada como si fuera la del
  capítulo.

### Créditos y siguiente capítulo
- Con marcador de créditos, la tarjeta «Siguiente capítulo» sigue saliendo al inicio de los
  créditos y permite **«Reproducir ahora»** o **«Ver créditos»**.
- Si la base **no tiene marcador de créditos**, en el último minuto del capítulo aparece la
  misma tarjeta con **«Seguir viendo»** / **«Reproducir ahora»**, para saltar al siguiente
  sin esperar al final.
- La duración correcta también evita que un marcador válido se descarte por no caber en la
  duración falsa.

### Servidor (sin OTA)
- `/v1/episodes` acepta CIDs que empiezan con letra (Carrie: `DC8195…`). Antes el
  regex exigía un dígito inicial y respondía «referencia inválida», así que el selector
  no recibía nombres ni miniaturas.
- El worker de episodios valida/renueva su **sesión propia** antes de drenar la cola y
  reconoce `aaa100027` («sesión caducada»). Antes la cola quedaba atascada hasta el ciclo
  XTV de 6 h y las fichas se quedaban sin metadatos.
- Verificado en vivo: `/v1/episodes?ref=xtv:DC81956806FD4F759317809F573B670D` devuelve
  8 capítulos de Carrie con nombre y `still` de TMDB.

### Se mantiene
- Selector de capítulos con `C# · nombre` y miniatura por episodio.
- «Seguir viendo» arriba del Inicio; series abren su ficha, «Continuar donde te quedaste» y
  «Siguiente capítulo» cuando terminaste un capítulo.
- Launcher 1.3.3 con la fila de series abriendo la ficha.

## Versión

- App **2.5.183 / vc423**.
- Launcher **1.3.3 / vc26** (sin cambios en esta tanda).

## Evidencia

- Android: **1092 tests**, 0 fallos.
- Launcher: **155 tests**, 0 fallos.
- Contrato: **22 tests**, 0 fallos.
- Release R8 compilado y firmado con la llave histórica `9593cb0d…`.

## Cómo probar

1. Instalar `VisionPlus.apk` de este release.
2. Abrir una serie cuyo capítulo dure menos que la temporada (el caso reportado: «Carrie»).
3. Comprobar que el total del reproductor coincide con el capítulo y no con 1 h 40.
4. Llegar al final: si hay marcador, la tarjeta sale en créditos; si no, sale en el último
   minuto. «Reproducir ahora» salta al siguiente capítulo.

— ChronosBVRX
