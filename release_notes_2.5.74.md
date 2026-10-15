# Vision+ v2.5.74 — Subtítulos del proveedor + espera del reproductor

## Subtítulos (por fin de verdad)
- El reproductor ya pinta **subtítulos reales** de los títulos XTV: el botón «□ Subtítulos» lista los
  idiomas que el proveedor sí tiene (p. ej. Español · Inglés) y los muestra sincronizados sobre el video.
- La elección se recuerda **por título y por serie**: al reabrir una película (o al pasar al siguiente
  capítulo) los subtítulos se reactivan solos. «Desactivados» también se recuerda.
- El motor nativo no participa: la app pide la lista al **servicio del portal** (el mismo que usa el APK
  oficial), descarga el archivo SRT y lo pinta encima del video. (En el motor, el idioma de audio sí
  estaba cableado, pero la llave de subtítulos del SDK es un no-op: por eso antes «no funcionaban».)
- Los títulos sin subtítulos en el catálogo del proveedor lo dicen honestamente: «Sin subtítulos disponibles».

## Espera del reproductor
- La pantalla de espera ya no dice «Preparando tu película…» en series: muestra el título y
  «T1 · Capítulo 8» (con el nombre real del capítulo cuando el portal lo trae) y se refresca al cambiar
  de capítulo. En vivo: «Preparando la señal…».

## Verificación
- 327 pruebas unitarias de Android en verde.
- Prueba física en el A32 (release con R8 y la firma histórica): lista del servicio (2 idiomas),
  SRT de 56 KB descargado, 901 diálogos cargados y **captura con subtítulo en pantalla**; sin crashes.
- El APK conserva la misma firma: se actualiza encima sin perder datos (índice, bóvedas, historial).
