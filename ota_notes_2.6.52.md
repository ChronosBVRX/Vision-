# Vision+ 2.6.52

## Series: siguiente capítulo y selector

- Corregido: «Sig. capítulo», el capítulo anterior y la selección de un capítulo de la temporada reproducían siempre el **primer capítulo**.
- Causa: el portal XTV elige el episodio por `episodeNumberList` y ese campo viajaba fijo en `[1]`; el `contentId` del capítulo se ignoraba. Ahora la licencia pide el número real del capítulo.
- Aplica a la licencia local (v9/v10), al broker FHD del VPS y al precalentamiento desde la ficha.
- Las **descargas** de capítulos también pedían siempre el C1; ahora bajan el capítulo elegido.

## Verificación

- Prueba directa contra el broker: `contentId=ep2` con `[2]` devuelve pistas distintas de `ep1` con `[1]`.
- Compilación `:app:compileNormalDebugKotlin` y tests de calidad/episodios/descargas en verde.

No se tocan catálogo, vivo, perfiles ni marcadores de salto.

— ChronosBVRX
