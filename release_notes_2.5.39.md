# Vision+ v2.5.39 — Corrección Definitiva de Barra de Avance y Safe Area en Smart TV

### 📺 Barra de Avance Completa y Márgenes de Seguridad en Pantallas Smart TV
- **Erradicación del Corte Físico por Overscan**: Se ajustaron los márgenes seguros en televisores (`48dp` horizontal y `28dp` vertical), garantizando que ni el extremo derecho de la barra de avance, ni el indicador de tiempo total (`timeTotal`), ni el thumb final queden recortados fuera del marco de la pantalla en televisores con sobreexploración.
- **Riel Continuo y Sin Espacios (`splitTrack = false`)**: Desactivado el recorte automático de Android (`splitTrack = false`) en el `SeekBar`, eliminando el corte transparente que provocaba que la barra se viera rota o incompleta alrededor del cursor.
- **Contenedores Sin Recorte (`clipChildren / clipToPadding = false`)**: Blindados los contenedores de controles inferiores para evitar que el renderizado del thumb o de las pistas se trunque en los bordes de la vista.

### ⏱️ Auditoría Forense de Duración en Motor Nativo Titan
- **Inspección Profunda de Bytecode**: Descubiertos y conectados los métodos reales del motor nativo Titan/Ranger (`uc.c.c()` y `yc.e.W()`) en `X4KTitan.durationMs(view)` para extraer la duración directamente desde el decodificador IJK embebido.
- **Propagación Fallback Inmediata**: `TvTitleScreen` y `TitleScreen` inicializan la duración de inmediato con los metadatos disponibles (`film.durationMin * 60_000L`) y transfieren un `EpisodePlay` con duración no nula a `onPlay`, asegurando que la barra de tiempo nunca arranque en `--:--`.
- **Resolución Asíncrona Enriquecida**: `X4KPlayerAty` ahora audita tanto el código individual como la serie en segundo plano utilizando `X4KApi.detailFor` y `itemInfo`.

### 🛡️ Blindaje Anti-Regresiones
- Mantenida al 100% la política inmutable de controles D-pad: primer clic solo despierta la interfaz sin acciones accidentales.
- 140 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%.
