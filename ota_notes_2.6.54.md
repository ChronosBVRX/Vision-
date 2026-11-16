# Vision+ 2.6.54 — Sugerencias al empezar los créditos de las películas

Actualización sobre 2.6.53.

## Películas

- Al empezar los **títulos finales** de una película aparece «Más como esta», con títulos del mismo género o estilo.
- Solo se muestra cuando la base trae el **inicio real de los créditos**. Si no hay dato, no se adivina, no se tapa la última escena y no se le pide nada al usuario.
- Si la película tiene **escena después de los créditos**, el modal lo avisa y se retira solo cuando empieza la escena, para no taparla.
- Las películas que no tienen dato siguen mostrando las sugerencias al terminar, como antes, pero ya no se adelantan al último minuto.

## Series

- Sin cambios: al empezar los créditos sale la tarjeta de «Siguiente capítulo»; el final de temporada y el selector siguen igual.
- El aviso de escena post-créditos ahora también existe en películas, sin botones ni configuración.

## Servidor

- Los marcadores de película se consultan en TheIntroDB (v4) e IntroDB (outro + post-créditos), con la duración real para acertar la versión teatral o extendida.
- La caché de marcadores guarda la duración usada y se invalida si se reproduce otra copia de la película.
- Sin pasos manuales: no hay botón para «marcar créditos» ni correcciones por parte del usuario.

## Verificación

- 566 pruebas del backend en verde.
- 46 pruebas de marcadores en verde (incluye películas, bloques de créditos y post-créditos).
- Compilación `:app:compileNormalDebugKotlin` correcta.
- Smoke real del endpoint: Deadpool & Wolverine devuelve créditos 2:02:43 y escena post-créditos 2:06:49; Oppenheimer devuelve créditos 2:54:12.

No se tocan catálogo, vivo, deportes, perfiles, descargas ni el motor X4K.

— ChronosBVRX
