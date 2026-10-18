# Vision+ 2.5.106 — El capítulo exacto desde el teléfono + los vínculos que sí se recuerdan

Dos entregas del control remoto: las series ya no se mandan «en general» (eliges temporada y capítulo y ESE capítulo es el que abre), y el emparejamiento se recuerda de verdad en los dos sentidos.

## El control remoto ya no manda la serie «en general»

- **El buscador del control remoto abre un selector para las series**: al tocar una serie, se despliegan sus temporadas (T1, T2, T3…) y sus capítulos con nombre real («Los Simpson - Español Latino») o el genérico «Capítulo N». Eliges y se abre ESE capítulo en la tele. Las películas siguen directas, sin pasos extra, como siempre.
- **El botón «Ver en la TV» de la ficha usa el mismo selector**: si el título es una serie, primero eliges temporada y capítulo; si es película, se manda al instante.
- **Sin callejón sin salida**: si los capítulos no se pueden cargar (red), el selector lo dice, deja reintentar y ofrece «Reproducir el título» con el comportamiento de siempre.
- **La fila avisa**: las series muestran «Capítulos» en lugar de «Reproducir», para que se entienda que hay algo que elegir.
- **La tele abre el capítulo con su serie alrededor**: el enlace viaja con la temporada activa y las temporadas del grupo, así el reproductor puede seguir con el siguiente capítulo sin volver al teléfono, y «En la tele: …» muestra el capítulo exacto («Los Simpson · T2 · C1»).

## La tele y el teléfono recuerdan sus vínculos

- **La tele recuerda cada dispositivo vinculado, con su nombre**: en Ajustes → «Control remoto» se ve «Dispositivos vinculados: N» con la lista de nombres; el teléfono ya emparejado entra solo, sin PIN, y hay un «Olvidar dispositivos vinculados» para empezar de cero.
- **El teléfono recuerda sus teles por una identidad estable (no por la IP)**: el vínculo sobrevive a los reinicios y a los cambios de dirección de la tele; los vínculos que ya existían se mudan solos al nuevo formato sin perder el PIN.
- **«Tus teles vinculadas» en la pantalla del control remoto**: las teles ya vinculadas aparecen aunque el descubrimiento no las vea, con su etiqueta «Vinculada» (un toque y entran sin PIN). Los teléfonos que aún no conocen nada ven «Conectar» como siempre.
- Compatibilidad total: teles viejas (sin identidad) siguen funcionando con la dirección de siempre.

## Pruebas y verificación

- **612 pruebas** de Android en verde (nuevas: el selector de capítulos y su contexto, el cable del control remoto, la baliza con identidad estable, la memoria de vínculos con su migración, los nombres recordados y el olvido, y el cableado de Ajustes).
- **En el emulador con una tele simulada**: «Los Simpson» por el buscador del control remoto → T1·C2 y T2·C1 (con su índice de temporada), «Ley y orden: UVE» desde «Ver en la TV» de la ficha → T1·C3, y una película («Moana») directa y sin contexto. El enlace resultante, disparado contra la app, **abrió y reprodujo el capítulo exacto** (video, subtítulos del capítulo correcto y «Omitir intro»).
- **Migración de vínculos verificada en vivo**: un vínculo viejo guardado por dirección se mudó solo a la identidad estable, la lista quedó con una sola entrada y la reconexión desde «Tus teles vinculadas» entró sin PIN.
- Pendiente de sentir en los equipos: la prueba física teléfono + tele en la red de casa (el flujo verificado es el mismo que corre en los equipos reales).

— ChronosBVRX
