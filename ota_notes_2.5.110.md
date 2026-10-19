# Vision+ 2.5.110 — el arreglo de los saltos de reproducción («no es pausa, es un salto de frames»)

Se reprodujo y midió la prueba en video del salto (a los ~11.7 s del clip: la imagen brinca dentro del mismo plano, sin ningún congelamiento previo, y el audio cambia en el mismo instante; los cortes de escena y los hipos del grabador quedaron descartados uno por uno). Con eso en mano, este lote ataca el problema por sus tres frentes dentro de la app, sin cambiar nada de lo que se ve.

## Qué cambió

- **La «Calidad preferida» ahora manda de verdad desde el primer cuadro.** El ajuste (Ajustes → Reproducción) solo pintaba la etiqueta de la píldora: el motor nativo siempre arrancaba con la primera calidad del portal —casi siempre la más pesada— y solo bajaba si la cambiabas a mano en cada reproducción. Ahora la elegida (1080p / 720p / 480p) va al frente de la lista con la que el motor arranca, en películas, capítulos y canales. En equipos modestos, donde las escenas de mucho movimiento son las que sufren tirones/saltos, elegir 720p ahora sí alivia desde el segundo cero.
- **El reproductor avisa cuando el contenido salta (telemetría nueva `PLAYER_FRAME_SKIP`).** El tic del reproductor compara la posición contra el reloj de pared: si la posición avanza más de lo que transcurrió —el salto que nunca se mostró—, lo reporta al diagnóstico con su tamaño en milisegundos. Los seeks legítimos (adelantar, continuar, omitir intro) reinician el detector, así que no hay falsos positivos. Si vuelve a pasar, quedará medido en los reportes del servidor: cuánto salta y cada cuánto.
- **Proceso del reproductor más liviano (sin cambios visibles).** El icono de reproducir/pausar ya no se reconstruye dos veces por segundo (solo cuando cambia de verdad), la duración del motor solo se consulta mientras el catálogo no la declaró, se hace un solo sondeo de estado por tic y los textos de tiempo y la barra solo se repintan cuando cambian. Menos trabajo del proceso `:x4k` en todo momento, incluidos los picos de las escenas de acción.

## Pruebas y verificación

- **642 pruebas de Android en verde (10 nuevas)**: orden de calidades (la preferida al frente conservando el resto; «Auto» y una calidad ausente dejan la lista del portal intacta; una sola media no se toca), detector de saltos (tic normal, estancamiento, rebobinado, hueco de sondeo, salto real con su exceso exacto, borde de tolerancia, sin línea base) y guardián de cableado (las DOS rutas de arranque —inicio y cambio de capítulo— usan la lista ordenada, la telemetría existe, cada seek de la app reinicia la base y el icono cachea su estado).
- **Pendiente de sentir** (equipos fuera de red al compilar): reproducir un título pesado y comparar «Auto» contra «720p» en Ajustes → Calidad preferida (debe arrancar ya en 720p), y en el próximo aviso del VPS comprobar si aparece `PLAYER_FRAME_SKIP`.

— ChronosBVRX
