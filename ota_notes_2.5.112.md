# Vision+ 2.5.112 — omitir intro y siguiente capítulo, ahora sí automáticos y sin cortes

Este lote corrige el flujo completo de series y anime: cada capítulo usa sus propios tiempos, el botón «Omitir intro» ya no aparece sobre la pantalla de carga y el siguiente capítulo no se lleva por delante los créditos ni las escenas post-créditos.

## Qué cambió

- **Cada capítulo pide SU plan.** Antes la app reutilizaba los marcadores del primer capítulo de la temporada. Attack on Titan T2 E1 tiene el intro en 3:29–4:59 y E2 en 1:26–2:56: con el plan viejo se cortaban 90 segundos de E2. Ahora cada episodio (también al cambiar de temporada o elegirlo en el selector) consulta sus propios tiempos.
- **Caché en disco compartida con el reproductor.** El proceso `:x4k` ya puede leer los marcadores del capítulo actual y de los siguientes sin esperar a la red. El video abre al instante y, si el plan no estaba cacheado, se pide en segundo plano.
- **«Omitir intro» solo cuando ya se ve el video.** La píldora espera a que el motor haya dibujado el primer cuadro y a que la pantalla de carga con el logo se haya desvanecido. Nunca más sobre el logo o el spinner.
- **Auto-skip seguro.** Solo salta si el inicio del intro es conocido, el video está reproduciendo y el marcador cabe en la duración real. Si el plan llega tarde, no corta contenido. Créditos solo después de la mitad del capítulo. El salto se aplica una sola vez (se corrigió el bucle que repetía el seek cada 500 ms en el keyframe anterior).
- **Siguiente capítulo sin cortar el final.** Por defecto la tarjeta sale al empezar los créditos, pero el siguiente episodio espera al final del archivo: no se pierden créditos ni escenas post-créditos. En Ajustes → Series y Continuidad hay un modo rápido opcional («Avanzar en créditos») que empieza al terminar los créditos.
- **Se respeta la pausa y el segundo plano.** El contador de «A continuación» se congela si pausas y se cancela si sales de la app; un episodio no arranca invisible detrás del menú.
- **Anime y series largas.** Se recuperó el mapeo TMDB→MAL de Fribb para series sin bloque de temporada (One Piece ya usa AniSkip), AniSkip prueba la duración real y las longitudes estándar de 24/12 minutos, y el tope de capítulos subió de 400 a 5,000.
- **Servidor más resistente.** Las tres fuentes se consultan en paralelo; una caída temporal ya no congela una caché negativa de dos días. La ficha exacta (XTV/anime) tiene prioridad sobre una copia Magis para no desactivar AniSkip. El campo `season` de la respuesta ya es el resuelto.
- **Offline y reproductor Compose.** Se retiró el salto fijo de 85 segundos y el diálogo placebo «Corregir intro y outro». El búnker lee los marcadores guardados al descargar y usa la misma política de auto-skip/siguiente.

## Verificación

- 670 pruebas Android + 252 de backend en verde.
- En vivo contra el VPS: AoT T2 E2 devuelve su intro 86–176 s (no la ventana de E1); Bleach TYBW T2 E1 reporta temporada 2; One Piece E1 usa AniSkip (recap 132–201 s, intro 310–400 s, créditos 1396–1434 s); AoT T4 E1 usa AniSkip y `len=1380` sigue funcionando por el fallback.
- En el emulador con el APK release R8: el auto-skip saltó una sola vez de 40 s a 53 s; la pantalla de carga mostró solo el logo, sin «Omitir intro».
- `:app:assembleNormalRelease` compila y el reproductor arranca sin el `VerifyError` de R8 que apareció durante el desarrollo (se evitó crecer la firma de `PlayerCore`).

## Nota

El servidor ya está desplegado y aplica sin OTA. Esta actualización solo trae el lado de la app. Si un capítulo no tiene datos en las bases comunitarias, no se muestra botón: no se inventan tiempos.

— ChronosBVRX
