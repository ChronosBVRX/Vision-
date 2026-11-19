# Vision+ 2.6.60 — Arranque más rápido y respaldo de calidad

Actualización sobre 2.6.59.

## Mejor pista desde el primer cuadro

- El orden de las pistas VOD ahora prioriza la **resolución** (4K → 1080p → 720p…) y, dentro de la misma resolución, la pista que el equipo puede reproducir mejor:
  - **Audio latino** por encima de original o castellano (`dual-lat`, `esp.latino`, «Español Latino», `419`).
  - **H.264 / WEB-DL** antes que **AV1**, que muchas cajas y Fire TV no decodifican o reproducen con tirones.
  - **HEVC / H.265** recibe un bono menor que H.264 por compatibilidad.
- El mismo criterio se aplica a las pistas que firma el broker VIP y a las **descargas offline**, no solo al catálogo libre.
- Un título con una sola pista conserva exactamente el orden del portal: sin cambios para el caso más común.

## Si una pista no arranca, se prueba la siguiente

- Si el motor rechaza la pista o no entrega el primer cuadro, Vision+ prueba **los espejos de la misma resolución** y después **baja a la siguiente calidad** (por ejemplo, 1080p → 720p → 480p) **en el mismo reproductor**.
- No se sale a la ficha, no se abre otro reproductor y el botón Atrás sigue funcionando igual.
- Se muestra un aviso discreto: «Probando otro servidor…» o «Cambiando a 720p…».
- Presupuesto máximo de **3 respaldos automáticos** por reproducción; si ninguno arranca, aparece la tarjeta de error de siempre con «Reintentar / Volver».
- El vivo no cambia: sigue usando su zapping y su failover de señales de PlayerScreen.

## Telemetría

- Nuevo evento `PLAYER_DOWNGRADE` en el diagnóstico: motivo, pista anterior y pista nueva. Permite medir en el VPS qué servidores están fallando y cuántos arranques se salvan con el respaldo.

## Verificación

- Compilación `:app:compileNormalDebugKotlin` correcta.
- **1,332 pruebas** de Android ejecutadas; solo quedan los **3 fallos ambientales conocidos** de la suite local (runtime nativo de Robolectric y dos pruebas de UI heredadas), los mismos de 2.6.59.
- `server/tests/test_vip.py`: 12 pruebas en verde.
- Nuevas pruebas: `StreamRankerTest`, `PlaybackChainTest`, `FreeQualityTest` y el guardián `PlayerStreamGuardTest`.
- No se toca el motor X4K, las licencias, los perfiles ni la firma.

— ChronosBVRX
