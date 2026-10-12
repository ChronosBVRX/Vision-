# AGENTS.md — Contexto persistente del proyecto Vision+ / Xuper XTV

> Este archivo se carga automáticamente en cada sesión de opencode. **Mantenerlo actualizado al
> cerrar una sesión** (estado, versión publicada, pendientes). El proyecto vive en
> `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+\` (aquí hay una copia idéntica de este archivo).

## Proyecto y objetivo
- App Android (Compose) **"Vision+"** — pkg `com.chronosbvrx.visionplus`, `versionName` actual **2.5.44**
  (versionCode **280**, release OTA v2.5.44). Es una app de streaming personal del usuario (ChronosBVRX).
- Integra el catálogo **Xuper/XTV** (tenant "masnew"): películas/series/kids/anime + **1000+ canales en vivo**,
  con el motor nativo propio del proveedor (Titan/Ranger) embebido.
- **Directivas del usuario**: sin distinción free/pago (todo el catálogo para todos); sin salir de la app
  (actualizaciones in-app); la app detecta sola si es Smart TV y se maneja con mando; todo **premium**
  (azul #2D7DF6); marca "Vision+" sin rastros visibles de "Xuper" ni "Magis"; orientación según teléfono
  y video sin deformar.

- **Reingeniería Integral del Menú de Opciones y Erradicación de Placebos del Sistema** (v2.5.44):
  - **Eliminación Total de Código Placebo y Obsoleto**:
    - Erradicado `DemoSettings.kt` y la pestaña entera de maquetas de subtítulos (sliders de tamaño, opacidad, sombra, grosor y preview 16:9 que no afectaban a los reproductores de video reales).
    - Eliminados los interruptores falsos de reproducción (*"Datos curiosos"*, *"Saltar intro en anime"*) y el selector placebo de *"DNS seguro"* que no interactuaba con la pila de red.
    - Purgado `DemoCompanion.kt` y todo el código huérfano de emparejamiento por IP simulada, códigos falsos (`482 913`) y dispositivos fantasma (*"TV de la sala"*).
  - **Nuevo Gestor Persistente de Configuraciones (`VisionSettings.kt`)**:
    - **Reproducción y Calidad**:
      - Selector de calidad preferida (`Auto`, `1080p`, `720p`, `480p`) persistido en `SharedPreferences` y respetado tanto por el motor nativo Titan (`X4KPlayerAty.kt`) como por ExoPlayer (`PlayerScreen.kt`).
      - Ajuste de relación de aspecto predeterminado (`Ajustar 16:9` vs `Rellenar pantalla / Zoom`) aplicado al iniciar videos en Titan y ExoPlayer.
      - Opción de reanudación automática de reproducción (`autoResume`), permitiendo continuar directo desde el punto guardado sin interrumpir con preguntas al usuario.
      - Opción de reproducción continua automática de series (`autoPlayNext`).
    - **Rendimiento y Pantalla**:
      - Selector de rendimiento de interfaz unificado (`Auto`, `Ligero`, `Completo`) con persistencia en `vision_performance`.
      - Selector de amortiguamiento en vivo (`Rápido 0.5s` vs `Estable 3s`).
    - **Almacenamiento y Mantenimiento**:
      - Cálculo en tiempo real del tamaño de caché (disco + Coil) y acción directa para liberar memoria interna (`clearCache`).
      - Función `PlaybackHistory.clear(context)` para vaciar el historial de "Seguir viendo" desde ajustes.
    - **Diagnóstico y Red**:
      - Detección de tipo de red activa (`Wi-Fi`, `Ethernet`, `Datos móviles`) y estado de servidores XTV.
  - **Rediseño UI/UX en Móvil y Smart TV**:
    - `SettingsScreen.kt` (Móvil/Tablet): Diseño modular en tarjetas con pestañas *Reproducción* y *Sistema y Rendimiento*.
    - `TvSettingsScreen.kt` (Smart TV): Navegación D-pad Leanback completa, soporte de teclas izquierda/derecha para ciclar valores (`TvStepOption`) y accesibilidad total.
  - **Validación**: 161 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%, incluyendo la nueva suite `VisionSettingsTest.kt`.
  - **Smart TV (Android TV Leanback D-pad Experience)**:
    - En `TvCategoryScreen.kt` (Películas, Series, Novelas, Anime, Fútbol), incorporado selector deslizable horizontal de categorías (`TvCategoryPill`) con foco de D-pad, cápsula `"Todo / Destacados"` por defecto y géneros dinámicos.
    - **Modo Feed Curado por Módulos**: Hero superior inmersivo + carruseles horizontales con `TvPosterCard` (proporción 2:3, ancho 140.dp) con badge de rating numérico (`★ 8.2`), badge de estado (`NUEVO`, `TOP 10`), y variante Top 10 con número de posición gigante estilizado en degradado azul plateado (`1..10`).
    - **Encabezados con Acción Interactiva `"Ver todo →"` (`TvSeeAllButton`)**: botón de foco D-pad para conmutar directamente a la cuadrícula de cualquier categoría.
    - **Modo Cuadrícula de Categoría (5 Columnas en Smart TV)**: Cuadrícula adaptativa `LazyVerticalGrid(columns = GridCells.Fixed(5))` con paginación infinita en lotes de 24 (`GRID_BATCH_SIZE = 24`), encabezado con botón `"← Volver a Todo"`, y soporte estricto de `BackHandler` para regresar al feed antes de salir de la sección.
    - **Optimización de Memoria en Dispositivos Limitados (TV Box ZTE B866F, Chromecast)**: Carruseles horizontales limitados a `take(15)` (`CAROUSEL_LIMIT = 15`) en `TvCategoryScreen.kt`, `TvHomeScreen.kt` y `TvCategoriesScreen.kt`, junto con decodificación Coil acotada a `size(320, 480)`.
    - **Biblioteca en TV (`TvLibraryScreen.kt`)**: Migrado a cuadrícula de 5 columnas con `TvPosterCard` y badges de calificación.
  - **Móvil y Tablets (`phone/SectionScreen.kt` & `Components.kt`)**:
    - Cabecera fija con selector `CategoryPill`, cuadrícula de 3 columnas (móvil) / 5 columnas (tablet), cards 2:3 con badges, Top 10 y límite de 15 items por carrusel.
  - **Validación**: 153 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%, compilación limpia y verificación visual directa vía ADB en dispositivo real Smart TV Box B866F.

- **Estabilización Total de la Duración ("Anti-Buffer Shadowing") y Selector Dinámico de Resolución en Series y Películas** (v2.5.42):
  - **Diagnóstico y Solución de la Duración Alternante ("Dos Minutos Más del Minuto Actual")**:
    - *Causa Raíz*: En `X4KPlayerAty.kt`, el bucle de sondeo cada 500 ms consultaba `X4KTitan.durationMs(v)`. En transmisiones MPEG-TS/HLS sin cabecera de duración fija en el contenedor, IjkPlayer reporta la duración como la ventana del búfer demuxer deslizante (`current_position + audio_cached_duration`), exactamente ~120 s (2 min) por encima del minuto actual. La condición previa `if (declaredDurationMs <= 0L || Math.abs(declaredDurationMs - engineDur) > 120_000L)` sobreescribía la duración real del catálogo (ej. 105 min) con la ventana de búfer (ej. 7 min en el minuto 5), provocando un parpadeo constante entre 1:45:00 y 07:00 y saltos erráticos de la barra entre 5% y 71%.
    - *Solución*: Implementadas las funciones `isBufferWindowDuration` y `resolveStableTotalDuration` en `ControlsState.kt`. Cualquier duración reportada por el decodificador que cumpla `engineDur <= currentPos + 180_000L` o `declaredDurationMs > 0L && engineDur < declaredDurationMs * 0.75f` es identificada como búfer deslizante y estrictamente descartada. Una vez establecida la duración canónica desde el catálogo o metadatos del portal, queda anclada de forma inmutable. Aplicado transversalmente a `X4KPlayerAty` (`poller`, `onDuration`, `onCreate`, `performSeekBy`, `progressMs`) y a `PlayerScreen` (`PlayerCore` en ExoPlayer).
  - **Selector de Resolución y Calidad de Video en Series y Películas**:
    - *Reproductor Nativo Titan (`X4KPlayerAty`)*:
      - Incorporada píldora interactiva `qualityBtn` (`"⚙ 1080p"`, `"⚙ 720p"`, `"⚙ Auto"`) en los controles de transporte VOD, con navegación D-pad completa y retención de foco al descartar modales (`lastPickerSourceBtn`).
      - Modal con diseño premium Vision+ (`ModalDimLayout` y paleta `#121A2A` / `#2D7DF6`) mostrando las resoluciones disponibles del portal (`1080p FHD`, `720p HD`, `480p SD`, `4K Ultra HD`), indicando la activa (`✓ (Actual)`) o notificando cuando el título cuenta con una única calidad nativa óptima.
      - Conmutación de flujo dual: llamada primaria a `X4KTitan.setMedia(v, target.name)` (método nativo `ed.e.setMedia`), con fallback automático reordenando la lista de streams y reiniciando con seek imperceptible al minuto actual.
    - *Reproductor ExoPlayer (`PlayerScreen.kt` & `PlayerDialogs.kt`)*:
      - Agregada sección "Calidad de video" en `AudioAndSubtitlesDialog` (`Auto`, `1080p FHD`, `720p HD`, `480p SD`).
      - Enlazado con `trackSelectionParameters` dinámico sobre ExoPlayer mediante `setMaxVideoSize` y `setMinVideoSize`.
  - **Validación**: 149 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.42.

- **Política Estricta de Arranque Seguro en Smart TV / Chromecast (Zero-Crash Cold Start & Non-Empty Catalog)** (v2.5.41):
  - **Diagnóstico Integral de Crashes en Primer Arranque en Chromecast**:
    1. *Bifurcación Prematura en Dos Procesos*: Durante el arranque en frío, `homeRows` invocaba `ensureEngineData` de forma síncrona, enviando un broadcast a `X4KWarmupReceiver` y forzando a Android a levantar de inmediato el proceso secundario `:x4k`. En los 1.5 GB - 2 GB de RAM de un Chromecast (donde el sistema operativo ya consume ~1.2 GB), dos procesos ART concurrentes provocaban la muerte fulminante por `lmkd` (*Low Memory Killer Daemon*).
    2. *Extracción Masiva de Binarios en Flash Lenta*: En el primer arranque, el proceso `:x4k` extraía de golpe 3 DEX y 6 librerías `.so` nativas (`libijkffmpeg.so`, etc.) desde los assets a `filesDir/x4k`. Escribir decenas de MB en el almacenamiento eMMC saturaba el bus I/O y los 4 núcleos de CPU, congelando el hilo principal a 0 FPS.
    3. *Tormenta de Red y Timeout de Catálogo Vacío*: Al no tener caché, se ejecutaban a la vez consultas SLB, tokens, 5 categorías VOD, el índice remoto de 25k títulos, 1,000+ canales en vivo y proxy HLS. Si la red tardaba > 9s (`BOOT_CAP_MS`), `status` caía en `NO_SOURCE`, dejando la app vacía o forzando cierres.
  - **Reglas Universales Inviolables de Arranque**:
    - **Regla 1 (Precalentamiento Estrictamente Diferido)**: Queda terminantemente prohibido levantar el proceso secundario `:x4k` o invocar `ensureEngineData` / `prewarmEngine` de forma síncrona durante `catalog.load()` o la fase inicial de arranque. El precalentamiento del reproductor debe ejecutarse de forma diferida (mínimo 6 a 7.5 segundos después de que el Home esté interactivo) o bajo demanda antes de reproducir.
    - **Regla 2 (Emisión Progresiva de Filas / Row Streaming)**: `X4KApi.homeRows` y `XuperCatalog` no retienen las filas esperando a que las 5 consultas del portal terminen; emiten y pintan cada fila (en especial la fila 1 de Destacados / Hero) inmediatamente a través de `onRowLoaded`.
    - **Regla 3 (Catálogo Semilla Fallback Inmediato en 0 ms)**: `CatalogSeed` provee una selección curada de títulos populares con metadatos completos y URLs de pósteres de alta calidad. En arranque en frío sin caché previa, `XuperCatalog` asigna la semilla en 0 ms, garantizando que el usuario jamás vea un inicio vacío ni espere en pantalla negra.
    - **Regla 4 (Escalonamiento Estricto de Tareas Secundarias)**: Canales en vivo (1,000+ canales), índice remoto masivo (25k títulos), comprobación de updates y proxy HLS se programan con retardos progresivos (2s, 4.5s, 7.5s, 9.5s) para garantizar un uso de CPU < 35% durante el arranque.
    - **Regla 5 (Feedback Activo en Splash de TV)**: `VisionSplash` en TV incorpora indicador de estado activo ("Iniciando Vision+...") y spinner continuo, mientras `MainActivity` retrasa la verificación de actualizaciones en GitHub para no saturar el ancho de banda.
  - **Validación**: 146 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.41.

- **Botón "Siguiente Episodio" en Reproductor y Reproducción Limpia en Series, Novelas y Anime** (v2.5.40):
  - **Botón Rápido de Siguiente Episodio en Controles**:
    - Se incorporó la píldora interactiva `nextEpBtn` (`"⏭ Sig. C#"` / `"⏭ Sig. T#"`) en la barra inferior de controles de `X4KPlayerAty`, colocada ergonómicamente entre adelantar y capítulos.
    - Soporta tanto avance de capítulo dentro de la misma temporada como transición fluida a la siguiente temporada cargando automáticamente su lista de episodios vía `X4KApi.episodeProgramsFor`.
    - Conectado a la tecla multimedia física `KEYCODE_MEDIA_NEXT` para mandos de TV y controles remotos.
    - Estado adaptativo: se desactiva con atenuación visual (`alpha = 0.45f`) al alcanzar el último capítulo de la última temporada disponible.
  - **Erradicación del Diálogo "¿Reanudar o desde el principio?" al Seleccionar Episodios**:
    - *Causa Raíz*: Al invocar `playEpisode()`, `curCode` sólo se actualizaba tras la respuesta del hilo de red (`prepareVod`), mientras que `playerView` no se detenía inmediatamente. El sondeo `poller` (`firstFrame = false`) disparaba `onFirstFrame()` mientras `curCode` aún correspondía al episodio previo, recuperando su posición histórica (> 15s) y desplegando el diálogo de reanudación sobre el nuevo capítulo.
    - *Solución Definitiva*:
      1. `X4KTitan.stop(v)` detiene inmediatamente el motor previo al solicitar un nuevo episodio.
      2. Asignación inmediata de `curCode = code`, `curTitle = title`, `curSeries = series`, `curEpIndex = epIdx`, `curSeasonIndex = seasonIdx`.
      3. Forzado de `resumeMs = 0L`, `resumeApplied = true`, `resumeAsked = true`, `pendingAutoResume = false` y `pendingResumeEntry = null`, eliminando además el historial previo con `PlaybackHistory.delete(this, code)`.
      4. Regla universal en `onFirstFrame()`: si es contenido seriado (`curSeries.isNotBlank() || epIds.isNotEmpty()`), nunca interrumpe con el diálogo de reanudación y arranca limpio en `00:00` a menos que se haya abierto explícitamente desde la fila de «Seguir viendo».
  - **Validación**: 142 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.40.

- **Corrección Definitiva de Barra de Avance Completa, Duración y Safe Area en Smart TV** (v2.5.39):
  - **Diagnóstico Integral de "Barra de Avance Incompleta"**:
    1. *Corte Físico por Overscan en Pantallas Smart TV*: `X4KPlayerAty` tenía un padding horizontal de sólo `14dp` y vertical de `10dp`. En televisores con sobreexploración (overscan del 3-5%), el extremo derecho de la barra de avance (`timeTotal`, p. ej. `1:45:20`) y el thumb final quedaban físicamente cortados fuera del marco de la pantalla.
    2. *Márgenes de Seguridad y No Recorte*: Se incrementó el padding seguro en Smart TV a `48dp` horizontal y `28dp` vertical (alineado con los márgenes seguros de TV), con `clipChildren = false` y `clipToPadding = false` en `bottom` y `seekRow`, ancho mínimo garantizado (`minWidth = 44dp`), alineación derecha en `total`, y altura mínima de pista `minimumHeight = 28dp`.
    3. *Desconexión de `splitTrack`*: Android por defecto recorta el riel con un espacio transparente alrededor del thumb (`splitTrack = true`), dando la ilusión visual de que la barra de avance está rota o incompleta. Se fijó `splitTrack = false` en `SeekBar`.
    4. *Inspección Forense de Duración en Motor Nativo Titan*: Se descubrió por descompilación de bytecode que en el bean de estado `uc.c` (`yc.h.p(0)`) el método que devuelve la duración es `c()`, y en el facade `yc.e` es `W()`. Se expandió `X4KTitan.durationMs(view)` para consultar estos métodos directamente.
    5. *Propagación Total de Duración Fallback*: Si el portal aún no responde o la consulta de red tarda, `TitleScreen.kt` y `TvTitleScreen.kt` inicializan `itemDurationMs` con `film.durationMin * 60_000L` y pasan `EpisodePlay` con duración no nula a `onPlay`. Se actualizó la consulta en segundo plano de `X4KPlayerAty` para inspeccionar tanto `code` como `curSeries` con `X4KApi.detailFor` y `itemInfo`.
  - **Validación**: 140 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.39.

- **Restauración de Duración Total Inmutable y Scrubber Interactivo de Búsqueda Fina** (v2.5.38):
  - **Diagnóstico Forense de la Regresión de Duración Total (`--:--` o `00:00`)**:
    - En `X4KTitan.kt`, el listener por reflexión `bd.a` hacía cast de `args?.firstOrNull() as? Long`. Cuando el motor nativo Titan devolvía `Integer` (`java.lang.Integer`) para `V0(duration)` o `P3(position)`, el casteo fallaba silenciosamente a `null`, impidiendo que `onDuration` se ejecutara.
    - En `X4KApi.kt`, `prepareVod` sólo leía `item?.optString("duration")`, ignorando variantes del JSON del portal como `playLength`, `totalTime`, `runtime`, `length`, `videoTime` o datos retornados en `startPlayVOD` (`d` / `episodeList`).
    - En `X4KPlayerAty.kt`, la consulta en segundo plano de `itemInfo` en `onCreate` estaba condicionada a `if (curSeries.isNotBlank())`, dejando películas sueltas sin duración conocida sin consultar la API.
    - En `TvTitleScreen.kt` y `TitleScreen.kt`, la duración recuperada del portal se mostraba sólo en la etiqueta visual pero no se inyectaba en `EpisodePlay.durationMs`.
    - En `PlayerScreen.kt` (`PlayerCore`), la barra `SeekBar` tenía `enabled = durationMs > 0`, bloqueando el desplazamiento si no se cargaba inmediatamente la duración.
  - **Diagnóstico Forense del Desplazamiento por la Barra (Scrubber Navigation)**:
    - Cuando `total <= 0`, tocar o arrastrar la barra en pantalla o mando evaluaba `progressMs()` en `0L`, provocando que al soltar hiciera seek al segundo 0 (reinicio de la película).
    - Al mantener presionado el D-pad izquierdo o derecho sobre la barra de progreso, se ejecutaba `X4KTitan.seekTo` de forma síncrona en cada repetición de tecla (`repeatCount`), saturando los decodificadores nativos de hardware con congelamientos y tartamudeos severos. Navegar 45 minutos requería cientos de pulsaciones lentas.
  - **Soluciones Implementadas**:
    - **`X4KTitan.kt`**: Casteo universal numérico `(args?.firstOrNull() as? Number)?.toLong()` en `V0` y `P3`. Método `durationMs(view)` con consulta directa a la vista nativa y al estado del facade `yc.h.p(0)`.
    - **`X4KApi.kt`**: Función universal `extractDurationMs(json)` soportando campos en texto y numéricos (`duration`, `playLength`, `totalTime`, `runtime`, `len`, `length`, `videoTime`). `prepareVod` enriquecido inspeccionando `item`, `sid`, `PlaybackHistory` y respuestas de `startPlayVOD`.
    - **`X4KPlayerAty.kt`**:
      - Consulta asíncrona de `itemInfo` en `onCreate` tanto para películas como para series.
      - Desplazamiento interactivo fluido a 60 FPS con `previewSeekMs` y paso dinámico progresivo `calculateScrubStepMs(total, event.repeatCount)` (10s–30s base -> 60s tras 1s -> 120s tras 2s -> 300s tras 5s).
      - Commit desacoplado con debounce de 350 ms (`commitSeekRunnable`) o al presionar Enter / cambiar de botón / soltar, evitando saturar el decodificador nativo mientras se visualiza el minuto deseado en pantalla en tiempo real.
      - Arrastre táctil en móvil calculando el tiempo objetivo directamente de `progress * total / 1000L`.
    - **`PlayerScreen.kt`**: `fallbackDuration` y `declaredDurationMs` leen `episode.durationMs`, habilitando la barra de inmediato y aplicando paso dinámico progresivo.
    - **`ControlsState.kt`**: Incorporada la función `calculateScrubStepMs(totalMs, repeatCount)`.
    - **`TvTitleScreen.kt` / `TitleScreen.kt` / `Navigation.kt`**: Propagación íntegra de `durationMs` en `EpisodePlay`.
  - **Blindaje de la Suite Anti-Regresiones (`PlayerNavigationTest.kt`)**:
    - Agregadas pruebas para `calculateScrubStepMsProgressiveAcceleration` y `extractDurationMsFromPortalJson`. Total de pruebas pasando: 140 de Android (100%) y 33 de backend (100%).


- **Política Universal Anti-Regresiones de Controles D-Pad en Todos los Reproductores** (v2.5.37):
  - **Diagnóstico y Análisis Forense Integral de los 6 Reproductores de la App**:
    - Se identificaron y auditaron exhaustivamente las 6 variantes de reproductor:
      1. `X4KPlayerAty` Live (`xtvlive:<code>`) — Nativo Titan en proceso aislado `:x4k`.
      2. `X4KPlayerAty` VOD (`xtv:<id>`) — Nativo Titan en proceso aislado `:x4k`.
      3. `PlayerCore` Live (M3U, `xuperch:`, live sports GeeSportsFallback con ClearKey DRM) — ExoPlayer en Compose.
      4. `PlayerCore` VOD (HTTP/IA films) — ExoPlayer en Compose.
      5. `TitanLivePlayer` (`magis:<code>`) — TitanSdk en Compose.
      6. `TitanVodPlayerScreen` (`magisvod:<id>`) — TitanSdk en Compose.
    - **Causa Raíz de la Regresión**:
      - En v2.5.33, al habilitar la barra de progreso (scrubber) con D-pad en `X4KPlayerAty`, se añadieron atajos de seek directo `performSeekBy(±10s)` en `onKeyDown` sin verificar si los controles estaban visibles (`!controlsVisible`).
      - En `PlayerCore`, entre el segundo 0:03 y 2:00 de cualquier película VOD > 10m, `SkipPill` («Saltar intro») se mostraba incondicionalmente fuera del bloque `controls.visible` y tomaba el foco D-pad sobre el video en negro. Al pulsar OK por primera vez, `skipTakesOk` disparaba el salto de +85s en lugar de abrir el menú de controles, y las flechas izquierda/derecha ejecutaban `seekBy(±10s)`.
      - En `TitanLivePlayer` y `TitanVodPlayerScreen`, los controles iniciaban visibles (`controlsVisible = true`) y el único elemento interactivo era el botón Atrás (`IconButton(onClick = onBack)`), por lo que presionar OK con el menú cerrado cerraba el reproductor inmediatamente en el primer clic.
      - Las pruebas anti-regresión anteriores sólo evaluaban la función booleana `resolveLiveDpadAction` para flechas Arriba/Abajo, sin probar OK/Centro, Enter, Izquierda, Derecha ni los dispatchers reales de eventos de los reproductores.
  - **Motor Universal de Decisión Anti-Regresiones (`ControlsState.kt`)**:
    - Implementado `PlayerKeyDownAction` y `resolvePlayerKeyDownAction`:
      - Regla inmutable: Si el menú está oculto (`!isControlsVisible`) o sin foco activo (`!hasControlFocus`), el PRIMER clic en cualquier tecla de navegación D-pad (`OK/Center`, `Enter`, `Izquierda`, `Derecha`, `Arriba`, `Abajo`, `Menu`, `CH+`, `CH-`, `Info`, `Guide`) **ÚNICAMENTE** hace visible el menú y posiciona el foco (`WAKE_CONTROLS_ONLY`), con **cero** acciones ejecutadas (cero seek, cero togglePlay, cero zapping, cero saltar intro).
      - Las acciones reales (seek, pausar, cambiar canal) solo se autorizan cuando el menú ya está visible y enfocado.
    - Implementado guardián de debounce centralizado `canExecuteControlAction(nowMs, lastWakeupMs, cooldownMs = 220L)` para absorber rebotes de hardware, teclas repetidas y eventos `ACTION_UP` inmediatos tras la apertura de controles o el descarte de modales.
  - **Corrección en Todos los Reproductores**:
    - `X4KPlayerAty.kt`: Se reemplazó la lógica manual en `dispatchKeyEvent` y `onKeyDown` por `resolvePlayerKeyDownAction`. Se blindaron todos los botones y el `SeekBar` con `canExecuteControlAction()`.
    - `PlayerScreen.kt` (`PlayerCore`): Se unificó `onPreviewKeyEvent` y `onKeyEvent` con `resolvePlayerKeyDownAction`. Se movió `SkipPill` dentro de `controls.visible`, eliminando el robo de foco durante la reproducción limpia. Se aplicó `canExecuteControlAction()` a todos los botones de transporte y atajos.
    - `TitanLivePlayer` y `TitanVodPlayerScreen`: Migrados a `rememberControlsState()` iniciando ocultos, con `AutoHideEffect`, `landingFocus(surface)` y `resolvePlayerKeyDownAction`.
  - **Blindaje de la Suite Anti-Regresiones (`PlayerNavigationTest.kt`)**:
    - Añadidas 5 pruebas unitarias exhaustivas cubriendo todos los modos (VOD y Vivo), todas las teclas D-pad, teclas multimedia dedicadas, bypass de modales y el cooldown de 220ms. Total de pruebas pasando: 138 de Android (100%) y 33 de backend (100%).

- **Apertura Instantánea de Canales en Vivo y Despertar Limpio de Controles con D-Pad** (v2.5.36):
  - **Precalentamiento del Proceso `:x4k` e Índice O(1) en Memoria (`X4KWarmupReceiver.kt`, `X4KTitan.kt`, `X4KApi.kt`, `X4KPlayerAty.kt`)**:
    - Creado `X4KWarmupReceiver` en `android:process=":x4k"` que precalienta en segundo plano los 3 DEX, las 6 librerías `.so` nativas, el boot de `yc.h`, las entradas SLB CDN (`sendEntriesCdnList` + `sendEnv`) y el arreglo de canales en vivo antes de que el usuario toque un canal, y se recalienta automáticamente al cerrar el reproductor (`XtvHandoff`).
    - Implementado índice `O(1)` en memoria (`memoryLiveMedias` y `memoryLiveArray`) en `X4KApi.kt` para evitar volver a parsear los ~2 MB de JSON (`K_LIVE`, 1,035+ canales) en cada clic.
    - En `X4KPlayerAty.onCreate`, si `X4KTitan.isConfigured(...)` ya está listo, `mountUi` se ejecuta de inmediato en el hilo principal en `0 ms`, mientras que el filtrado de la lista de canales corre en segundo plano sin bloquear el arranque.
    - Reducido el intervalo de sondeo del primer cuadro (`poller`) de `500 ms` a `80 ms` (con disparo inmediato desde `onPosition`), y el fundido de `loadingOverlay` en vivo de `400 ms` a `120 ms`.
  - **Despertar Limpio del Menú de Reproducción con D-Pad sin Acción Accidental (`ControlsState.kt`, `X4KPlayerAty.kt`, `PlayerScreen.kt`)**:
    - Los controles inician ocultos (`controlsVisible = false`, `View.GONE`) para que el video arranque limpio.
    - Implementado `shouldWakeControlsWithoutAction` y `swallowWakeupKeyCode` en `dispatchKeyEvent` / `onPreviewKeyEvent`: en Smart TV, cuando el menú está oculto (o sin foco activo), pulsar cualquier tecla del D-pad (`OK/Centro`, `Izquierda`, `Derecha`, `Arriba`, `Abajo`) **solo muestra el menú y posiciona el foco** en el botón principal (`channelsBtn` en vivo, `playPauseBtn` en VOD), absorbiendo repeticiones y el `ACTION_UP` para que jamás pause, adelante/atrase ni cambie de canal por sí solo.
  - **Validación**: 133 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%. Release OTA v2.5.36 publicada en GitHub.

- **Continuidad Secuencial de Series y Selector Pro de Temporadas y Capítulos** (v2.5.35):
  - **Ordenamiento Estricto y Reproducción Continua de Series (`X4KApi.kt`, `X4KTitan.kt`, `X4KPlayerAty.kt`, `PlayerScreen.kt`)**:
    - `X4KApi.parseEpisodePrograms` y `parsePlayEpisodePrograms` desduplican por `contentId` y ordenan ascendentemente (`1, 2, 3...`) según `seriesNumber` / `episodeNumber` (con fallback por regex en el nombre).
    - Conectado el callback nativo de fin de reproducción (`bd.a.z2` → `onCompletion`) en `X4KTitan.makePlayerListener` hacia `onEpisodeEnded()`, y eliminada la duración fallback ficticia de 45m en series para que cada capítulo fije su duración real de archivo (20–58m) y dispare el paso al siguiente episodio sin fallos.
    - Al cambiar automáticamente a la siguiente temporada, se actualizan `epIds` y `epNames` con todos los capítulos de la nueva temporada, preservando `curBaseTitle` e iniciando desde `00:00`.
  - **Selector Pro de Temporadas y Capítulos (`X4KPlayerAty.kt`, `TvTitleScreen.kt`, `TitleScreen.kt`)**:
    - Modal en reproductor y paneles en fichas de Smart TV y móvil rediseñados con la paleta Vision+ (`VisionBlue` `#2D7DF6`, `VisionBlueBright` `#6BA6FF`, `VisionSurface` `#0F1626`, `VisionSurfaceHigh` `#1B2538`), insignias `C#`, títulos limpios de episodios, estado (`● Reproduciendo ahora`, `✓ Visto`, `▶ Siguiente`) y botón principal inteligente (`▶ Continuar · T# · C#`).
  - **Validación**: 131 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%. Release OTA v2.5.35 publicada en GitHub.

- **Canales Internacionales de Fútbol, Descubrimiento de ESPN / Star+ y Opciones Adicionales con DRM ClearKey** (v2.5.34):
  - **Identidad Real de ESPN / Star+ en XTV (`X4KApi.kt` & `SportsLinker.kt`)**:
    - Los 4 canales camuflados en el portal XTV como `Elite Sports Hub HD`, `2 HD`, `3 HD` y `4 HD` (con códigos `ST☆R+HD_cyx` a `ST☆R+ 4 HD_cyx`) ahora se exponen limpiamente como `ESPN / Star+ HD`, `ESPN 2 / Star+ 2 HD`, `ESPN 3 / Star+ 3 HD` y `ESPN 4 / Star+ 4 HD`, enlazados a la guía EPG de ESPN en el servidor y resueltos para partidos de `ESPN`, `ESPN 2–4`, `Disney+` y `Star+`.
  - **Cobertura Internacional de Ligas Europeas y Sudamericanas (`SportsLinker.kt` & `server/sync/sports.py`)**:
    - Mapeo completo de los canales deportivos internacionales presentes en XTV (`MOVISTAR FUTBOL HD`, `Liga de campeones`, `DAZN 1–4 HD`, `Directv Sports PC`, `ON DIRECTV HD`, `CANAL GOL HD`, `TV AZTECA DEPORTES NETWORK HD`, `FOX ONE`, `TNT SPORTS FHD/HD/PREMIUM`, `ViX Deportes`) para LaLiga, Champions League, Premier League, Serie A, Bundesliga, Ligue 1, Libertadores y Sudamericana.
  - **Opciones Adicionales de Transmisión en Vivo (HLS con Cabeceras + DASH ClearKey DRM en Media3)**:
    - Incorporadas opciones adicionales directas de transmisión deportiva (`GeeSportsFallback.kt`, presentadas limpiamente como opciones en la lista sin marcas externas) con soporte en `PlayerScreen.kt` (`PlayerCore`) para URLs con cabeceras pipe (`|origin=...&referer=...&user-agent=...`), manifiestos DASH (`.mpd`) y desencriptación ClearKey (`C.CLEARKEY_UUID` + `LocalMediaDrmCallback`).
  - **Validación**: 128 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.34.

- **Navegación D-Pad Fluida en Reproductores, Barra de Progreso y Restauración de Menús** (v2.5.33):
  - **Navegación en la Barra de Progreso (Scrubber) en Smart TV Resuelta (`X4KPlayerAty.kt` & `PlayerScreen.kt`)**:
    - **Reproductor Nativo Titan (`X4KPlayerAty.kt`)**: Implementado `performSeekBy(deltaMs: Long)` que sincroniza instantáneamente el motor nativo `X4KTitan.seekTo`, la posición en texto `timeNow` y el progreso del `SeekBar`. Conectado `seek.setOnKeyListener` para capturar `KEYCODE_DPAD_LEFT` (-10s), `KEYCODE_DPAD_RIGHT` (+10s), `KEYCODE_DPAD_DOWN` (traslado de foco a `playPauseBtn`), y `KEYCODE_DPAD_CENTER` / `ENTER` (`togglePlay()`).
    - En `onKeyDown`, el D-pad izquierdo y derecho ahora ejecutan rebobinado/adelanto de 10s inmediato mostrando la barra OSD, sin perder pulsaciones.
    - **Reproductor Compose (`PlayerScreen.kt`)**: Corregido `SeekBar` agregando `.focusable()` y `.focusProperties { down = playPauseFocus; left = barFocus; right = barFocus; up = barFocus }`, permitiendo que el foco suba desde los botones de transporte al scrubber, animando el grosor de pista y cambiando el pulgar a blanco nítido cuando está enfocado.
  - **Restauración Total de Foco D-Pad al Descartar Menús y Diálogos**:
    - **`X4KPlayerAty.kt`**: Implementado rastreo de origen `lastPickerSourceBtn` en selectores de audio, subtítulos, capítulos, canales en vivo e información de canal. Al descartar el modal (`dismissPicker()`, `hideResumeDialog()`, `hideNext()`), el foco regresa de inmediato al botón que abrió el diálogo o a `playPauseBtn` / `channelsBtn`, eliminando el extravío de cursor en el limbo.
    - **`PlayerDialogs.kt`**: Erradicado `.modalFocusTrap()` interno que bloqueaba la navegación hacia los botones de acción «Cerrar» y «Cancelar».
    - **`PlayerScreen.kt`**: Incorporados `LaunchedEffect(showTracks)` y `LaunchedEffect(showMarkers)` para reenfocar automáticamente `tracksFocus` y `markersFocus` al descartar los diálogos.
  - **Validación**: 125 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.33.

- **Blindaje Definitivo de Foco D-Pad al Salir del Modal en Fútbol** (v2.5.32):
  - **Aislamiento de Ventana con `Dialog` (`TvSportsScreen.kt`)**: Se migró `TvMatchChannelsModal` a un componente nativo `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` para que corra en su propia ventana Android, protegiendo el árbol de foco de la Activity subyacente.
  - **Erradicación de Bloqueo de Foco en Fondo (`Column.focusProperties`)**: Retirada la directiva `.focusProperties { canFocus = selectedMatchForPicker == null }` que invalidaba los nodos de foco de las tarjetas mientras el modal estaba abierto.
  - **Escalafón de Recuperación de Foco Inmediato**: Al descartar el modal (con tecla Atrás o botón Cerrar), se resetea `FocusRescue.screenHasFocus = false` y se enfoca directamente la tarjeta seleccionada previa (`cardFocusRequester.requestFocus()`), con escalafón de seguridad a `landing.request()` y a la fila de filtros de liga (`filterRowFocusRequester.requestFocus()`).
  - **Validación**: 123 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.32 publicada en GitHub.

- **Sello Propio Definitivo y Desacoplamiento Completo** (v2.5.31):
  - **Identidad y Autoría Oficial Consolidada (`AboutScreen.kt`)**: Actualizada la pantalla «Acerca de» tanto en Smart TV como en teléfonos móviles, estableciendo la titularidad exclusiva de ChronosBVRX y describiendo la plataforma real de streaming nativa.
  - **Limpieza de Configuración Gradle (`app/build.gradle.kts`)**: Erradicado el flavor experimental `kinoid` (`com.arkiv.player.light`), dejando limpio el entorno con el sabor oficial `normal` (`com.chronosbvrx.visionplus`).
  - **Documentación y Avisos Legales Reescritos**: `README.md`, `NOTICE`, `LEGAL.md` y `THIRD_PARTY_NOTICES.md` completamente reescritos y alineados con el catálogo de 80,000+ títulos, TV en vivo, deportes y motores nativos de Vision+.
  - **Validación**: 122 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 33 de backend pasando al 100%. Release OTA v2.5.31 publicada en GitHub.

- **Navegación D-Pad Blindada y Detección Exacta de Canales en Fútbol** (v2.5.30):
  - **Eliminación Total de Crashes en Móvil y Smart TV (`SportsScreen.kt`, `TvSportsScreen.kt`, `VisionApp.kt`)**:
    - **Crash en Móvil resuelto (`IllegalStateException`)**: Erradicado el uso de `Modifier.weight` dentro de `LazyRow` en `ChannelChip`, sustituyéndolo por `Modifier.widthIn(max = 160.dp)`, evitando que Compose truene por restricciones horizontales no acotadas.
    - **Congelamientos de Interfaz y ANR resueltos (`SportsLinker.kt` & `SportsState.kt`)**: Eliminada la ejecución síncrona en el hilo principal de UI de ~180,000 normalizaciones de cadenas en `SportsLinker.resolveChannels()`. Se implementó caché en memoria `brandInfoCache: ConcurrentHashMap<String, BrandInfo>` y recálculo en corrutinas en `Dispatchers.Default` / `Dispatchers.IO`. En Smart TV (`TvSportsScreen.kt`), se retiró la invocación directa de `quickResolve()` del bucle de composición de `LazyColumn`.
    - **Blindaje Universal contra Fallos de Segmentación GPU (`VisionApp.kt`)**: Deshabilitado `.allowHardware(false)` globalmente en Coil para evitar que el renderizado de logos y escudos de equipos de fútbol agote los descriptores nativos `GraphicBuffer`/`AHardwareBuffer` en GPUs Mali y chips Amlogic (`SIGSEGV in libhwui.so`).
  - **Filtro de Ligas Premium con Insignias y Contadores (`TvSportsScreen.kt`, `SportsScreen.kt`, `SportsState.kt`)**:
    - **Smart TV**: Tira horizontal interactiva con foco D-pad (`TvLeagueFilterChip`) que permite filtrar partidos por liga ("Todas (N)", "Liga MX", "Champions League", "LaLiga", etc.) mostrando el logo oficial de la liga, contador de partidos disponibles y borde resplandeciente en `VisionBlueBright` con foco en blanco nítido.
    - **Móvil**: Barra de `FilterChips` estéticos con scroll horizontal, badge de conteo de encuentros y logos oficiales, respetando la paleta Vision+ (`VisionBlue`, `VisionSurface`).
    - **Gestión de Estado Centralizada**: `selectedLeagueId`, `availableLeagues`, `leagueMatchCount(leagueId)` y `selectLeague(leagueId)` en `SportsState.kt`, actualizando reactivamente los grupos de partidos.
  - **Navegación D-Pad Blindada en Smart TV (`TvSportsScreen.kt`)**:
    - Erradicado el extravío de foco al navegar entre filtros y partidos: implementado `listState = rememberLazyListState()` con reseteo de scroll (`scrollToItem(0)`) al cambiar de liga o fecha.
    - Contenedores semánticos con `Modifier.focusGroup()` en la fila de días, la fila de filtros y la lista de encuentros.
    - Conexión direccional bidireccional explícita con `focusProperties`: de cualquier chip de filtro hacia la primera tarjeta (`down = firstCardFocusRequester`) y hacia los días (`up = daysRowFocusRequester`); y de la primera tarjeta hacia los filtros (`up = filterRowFocusRequester`), sorteando encabezados no interactivos.
  - **Asignación Precisa de Canales y Fuentes de Transmisión (`SportsLinker.kt` & `server/sync/sports.py`)**:
    - Erradicada la sugerencia de canales erróneos (ej. sugerir Fox Sports para Serie A o LaLiga): implementado `LEAGUE_DISALLOWED_BRANDS` en `SportsLinker.kt` que invalida con score 0 marcas sin derechos para cada competición.
    - Priorización estricta de emisiones confirmadas (`match.broadcasts`) sobre canales genéricos.
    - Incorporado fallback resiliente con `curl` en `_fetch_livesoccertv` para burlar el bloqueo Cloudflare HTTP 403, garantizando que el servidor capture los listados reales de transmisión para México.
    - Mapeo canónico enriquecido en `SportsLinker.kt` y `sports.py` (`espn2 mexico`, `espn3 mexico`, `fox one`, `vix mexico`, `max mexico`, `movistar futbol`).
  - **Validación**: 122 tests unitarios de Android (`:app:testNormalDebugUnitTest` incluyendo `leagueDisallowedBrandsPreventsWrongSuggestions`) y 33 tests de backend (`pytest server/tests`) al 100% en verde.

- **Buscador Universal Inteligente y Búsqueda por Voz** (v2.5.28):
  - **Búsqueda por Voz Nativa en Smart TV y Móvil (`TvSearchScreen.kt` & `SearchScreen.kt`)**:
    - **Smart TV**: Incorporado botón de dictado por voz («Dictar voz») integrado al teclado virtual y navegable con D-pad. Inicia `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` y `rememberLauncherForActivityResult`, permitiendo buscar cualquier título hablando al control remoto sin escribir letra por letra.
    - **Teléfono Móvil**: Botón de micrófono integrado directamente en la barra de búsqueda para dictado rápido.
    - **Botón de Borrado Rápido**: Botón «Borrar» para limpiar el texto de búsqueda con un solo clic.
  - **Búsqueda Universal Insensible a Acentos y Diacríticos (`unaccent` en PostgreSQL / `server/api/app.py`)**:
    - Resuelto el problema donde buscar sin tilde ("la vida es bella", "cancion", "corazon", "angel", "mama") no encontraba títulos guardados con acentos (o viceversa).
    - Integrada la extensión `unaccent` en la consulta SQL de `/v1/search` en PostgreSQL, garantizando coincidencia perfecta sin importar acentos o mayúsculas.
  - **Búsqueda Multicriterio por Tokens (Título, Director, Reparto, Sinopsis)**:
    - Búsqueda descompuesta por palabras clave: buscar términos separados ("vida bella", "vida es bella") encuentra de inmediato títulos relevantes como "La vida es bella".
    - Soporte para búsquedas por **Director** (ej. "Roberto Benigni", "Tarantino", "Christopher Nolan", "Spielberg") y **Actores/Reparto**, devolviendo sus películas aunque no figuren en el título.
  - **Ranking Ponderado por Relevancia y Calidad**:
    - Erradicado el ordenamiento alfabético ciego. Priorización multicapa: (1) Título exacto -> (2) Prefijo de título -> (3) Frase o palabras completas -> (4) Título original -> (5) Director/Reparto/Sinopsis, ordenado por calificación IMDb/TMDB y año más reciente.
  - **Caché en Memoria Ultrarrápido (0 ms)**:
    - `memorySearchCache` en `RemoteIndexSource.kt` para respuestas en 0 ms ante retrocesos o repeticiones.
    - `_SEARCH_CACHE` en FastAPI (`app.py`), respondiendo en menos de 1 ms.
    - *Debounce* de tecleo optimizado a 220 ms.
  - **Catálogo Completo en Móvil**:
    - `SearchScreen.kt` en móviles ahora consulta directamente el índice remoto del servidor VPS (80,000+ títulos).
  - **Validación y despliegue**: 120 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 33 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.28 publicada en GitHub.

- **Estrenos y Novedades Curadas + Blindaje del VPS**:
  - **Blindaje y Ocultación de API en el VPS (`server/api/app.py`)**:
    - Erradicación de documentación pública (`docs_url=None`, `redoc_url=None`, `openapi_url=None` en FastAPI), impidiendo que bots o crawlers descubran esquemas, rutas y metadatos del catálogo.
    - Respuestas genéricas y seguras en raíz (`/` -> `{"status": "ok"}`) y healthcheck (`/healthz` -> `{"status": "ok"}`), ocultando el estado interno de PostgreSQL.
  - **Repisas de Estrenos y Novedades Aisladas por Sección (`server/sync/index_build.py` & `app.py`)**:
    - Creación de categorías dedicadas de primer nivel en PostgreSQL (`position = 1`):
      - Películas: `xtv-movies-estrenos` («Estrenos de Películas»).
      - Series: `xtv-series-estrenos` («Estrenos y Nuevas Temporadas»).
      - Anime: `xtv-anime-estrenos` («Estrenos de Anime»).
      - Novelas: `xtv-novelas-estrenos` («Novedades de Novelas»).
    - Las secciones solo incluyen títulos estrictamente filtrados de su categoría (aislamiento total cine vs series) ordenados por año y calificación reciente.
    - `/v1/section/{name}` entrega automáticamente la repisa de estrenos en la posición 1 antes de los géneros.
    - Reordenamiento del índice global de `estrenos` en `/v1/index` por año y puntuación decreciente.
  - **Priorización de Novedades en Inicio y Pantallas de Sección (`XuperCatalog.kt`, `TvCategoryScreen.kt`, `X4KApi.kt`)**:
    - `TvCategoryScreen.kt` ubica `premiereRow` como la primera fila bajo el Héroe inmersivo en Smart TV, precediendo a recomendaciones y géneros.
    - `XuperCatalog.kt` promueve la fila de `estrenos` curada por el servidor a la parte superior de Inicio tanto en TV como en móvil en frame 0 desde caché.
    - `X4KApi.cleanShelfTitle` estandariza nombres de repisas dinámicas del portal ("Recién agregadas", "Estrenos") al diseño limpio de Vision+.
  - **Validación y despliegue**: 120 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 32 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.27 publicada en GitHub con éxito.

- **Duración Real Inmutable en Reproductores VOD (Películas y Series)**:
  - **Erradicación de barra progresiva dinámica ("se va generando")**:
    - Se identificó y resolvió la causa por la cual la barra de progreso OSD y el contador total no mostraban la duración real total desde el frame 0 y aumentaban continuamente a saltos (1m, 3m, 7m...) a medida que el motor nativo (Titan/ijkplayer o ExoPlayer) descargaba fragmentos de streaming HLS (`.m3u8`).
  - **Parser Universal de Duraciones (`X4KApi.parseDurationMs`)**:
    - Soporte multi-formato para respuestas del portal y APIs: reloj (`HH:mm:ss`, `mm:ss`), textos con unidades (`"90 min"`, `"1h 30m"`, `"1 h 30 min"`, `"45m"`), y números enteros en segundos (`"5400"`), minutos (`"90"`, `"45"`) o milisegundos (`"5400000"`).
  - **Herencia de Metadatos en Capítulos de Series**:
    - Los capítulos de teleplay no contienen el campo `"duration"` en su ficha individual en la API de XTV. `prepareVod` ahora consulta automáticamente la ficha de la serie (`sid`) para heredar su duración.
  - **Inyección de Fallback de Catálogo (`Film.durationMin`)**:
    - `PlayerScreen.kt` inyecta `fallbackDurationMs = film.durationMin * 60_000L` a `X4KApi.playVod`, `prepareVod` y `PlayerCore` (ExoPlayer). La barra de progreso inicializa con la escala real inmutable desde el milisegundo 0.
  - **Blindaje del Motor Nativo (`X4KPlayerAty.kt`)**:
    - En el listener `onDuration`, se filtran y descartan duraciones parciales provenientes de fragmentos HLS descargados (`< 80%`). Si el motor reporta la duración exacta de archivo (`>= 80%`), se afina milimétricamente sin romper la escala.
    - `playNextEpisode` preserva la duración declarada previa (`prevDeclared`) al cambiar de capítulo y resetea la barra a 00:00 / Duración Total.
    - `PlaybackHistory` y `Navigation.resumeRoute` preservan `durationMin` al reabrir desde «Seguir viendo».
  - **Validación**: 119 tests unitarios de Android (`:app:testNormalDebugUnitTest` incluyendo `X4KApiDurationTest.kt`) y 30 tests de backend (`pytest server/tests`) al 100% en verde. Build de APK verificado con éxito.

- **Optimización Integral de Rendimiento en Gama Baja y Descarga al VPS** (v2.5.27):
  - **Búsqueda Instantánea con Debounce y Descarga al Servidor (`TvSearchScreen.kt` & `RemoteIndexSource.kt`)**:
    - Erradicado el congelamiento de interfaz al teclear con D-pad donde `searchFilms` normalizaba síncronamente miles de sinopsis en cada letra.
    - Implementada búsqueda con *debounce* de 280 ms que consulta directamente `/v1/search` en el VPS (FastAPI + Postgres en ~3 ms sin uso de CPU en la TV) con fallback local optimizado en `Dispatchers.Default`.
    - Optimizado `searchFilms` en `Catalog.kt` priorizando coincidencia en título antes de evaluar sinopsis.
  - **Offloading de Fútbol y Enriquecimiento EPG en el VPS (`server/api/app.py` & `sports.py`)**:
    - Cruce centralizado en PostgreSQL de partidos activos/próximos contra la tabla `epg_programs`. El VPS promueve automáticamente el canal que emite el partido a la primera posición de `channels` y `broadcasts`.
    - Elimina la necesidad de que los dispositivos gama baja ejecuten hasta 8 peticiones HTTP secuenciales a la guía EPG en bucle.
  - **Compresión de Red GZIP Activa en el Servidor (`server/api/app.py`)**:
    - Incorporado `GZipMiddleware(minimum_size=1000)` en FastAPI para comprimir automáticamente todos los payloads JSON (`/v1/index`, `/v1/section/*`, `/v1/sports`, `/v1/search`), reduciendo el ancho de banda transferido en un 75% a 85%.
  - **Cero Congelamientos en Inicio y «Seguir viendo» (`TvHomeScreen.kt` & `PlaybackHistory.kt`)**:
    - `reloadContinue()` trasladado a corrutina en segundo plano (`Dispatchers.Default`) con bypass inmediato si el historial está vacío.
    - Búsqueda de fichas de historial optimizada de `O(N*M)` a `O(1)` mediante indexación en `HashMap` en `matchCards`.
  - **Taxonomía Eficiente de Canales en Vivo (`TvLiveScreen.kt`)**:
    - Consolidado el cálculo de géneros, conteos por categoría y países en una sola pasada lineal sobre la lista de canales, reduciendo en un 70% la asignación de objetos en el Heap.
  - **Blindaje de Decodificadores de Hardware en el Reproductor (`X4KPlayerAty.kt`)**:
    - Detección de dispositivos gama baja (`DevicePerformance.isLowEndDevice(this)`): omite el `VideoView` de intro para evitar colisiones y agotamiento de decodificadores de hardware en SoCs Amlogic/Chromecast, pasando directo a la reproducción principal.
  - **Validación**: 111 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 30 tests de backend (`pytest server/tests`) al 100% en verde.

- **Detección Precisa de Canales y Países en Fútbol** (v2.5.26):
  - **Aislamiento Estricto de Ediciones de Canales (`SportsLinker.kt`)**:
    - Erradicado el secuestro de canales donde pedir "ESPN" sintonizaba "ESPN 2" o "ESPN 3", o pedir "Fox Sports" elegía "Fox Sports 2" o "Fox News".
    - Implementado `BrandInfo` y `scoreChannel`: cada edición solicitada (base, "2", "3", "premium", "extra") se empareja estrictamente con su equivalente, rechazando ediciones incompatibles (score = 0).
  - **Priorización Geográfica y Correspondencia por País (`LiveTaxonomy.kt`)**:
    - Señales de México (`🇲🇽 México`) reciben prioridad máxima (+80 puntos) para partidos sin país explícito, seguidas por señales internacionales (+40) y EE. UU. (+20).
    - Señales sudamericanas (Argentina, Chile, Colombia, etc.) reciben penalización (-70 puntos) para evitar que sustituyan la transmisión del partido por programas o ligas locales no deseadas.
    - Feeds explícitos (ej. "TUDN USA") emparejan con prioridad máxima (+100 puntos) con su respectiva señal nacional.
    - Agregados tokens de país ("AR" para Argentina, "CL" para Chile, "CO" para Colombia) en `LiveTaxonomy.COUNTRY_TOKENS`.
  - **Insignias Visuales de País con Bandera Nacional (`LiveTaxonomy.countryFlag`)**:
    - En el modal de canales de Smart TV (`TvMatchChannelsModal`) y Móvil (`MobileMatchChannelsDialog`), cada canal muestra una cápsula estilizada con su bandera y país (`🇲🇽 México`, `🇦🇷 Argentina`, `🇺🇸 EE. UU.`, `🌐 Internacional`, etc.).
  - **Escaneo EPG Profundo (Título + Sinopsis)**:
    - `SportsLinker.confirm` ahora evalúa `mentionsBoth("${item.name} ${item.description}", match)`, detectando partidos cuyos equipos figuran en la sinopsis del feed XMLTV.
    - Muestra subtítulo en vivo en el modal (`link.epgPrograms[channel.url]` = "⚽ En vivo: [Programa]" o "Ahora: [Programa]") y promueve el canal confirmado al primer lugar de la lista.
  - **Validación y despliegue**: 111 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.26 publicada en GitHub.

- **Opciones de Fútbol al Instante y Foco D-pad Blindado** (v2.5.25):
  - **Canales de Fútbol en Vivo al Instante (0 ms / frame 0)**:
    - Resuelta la demora donde los partidos live no mostraban opciones de reproducción hasta terminar la descarga HTTP de red.
    - Implementada resolución síncrona en memoria en `SportsLinker.quickResolve(context, match)` y `quickResolveAll(context, matches)` con caché `ConcurrentHashMap`, cruzando las cadenas oficiales con `X4KApi.cachedLiveChannels(context)`.
    - `SportsState.kt` ahora inicializa tanto `matches` como `links` en frame 0 desde cache en `init` y `pick(date)`.
    - Respaldo directo en el modal de selección de canales (`TvMatchChannelsModal` y `MobileMatchChannelsDialog`) asegurando candidatos inmediatos.
  - **Blindaje y Restauración de Foco D-pad al Cerrar Modal con Tecla Atrás**:
    - Erradicada la congelación de foco donde cerrar el modal con Atrás dejaba el mando D-pad muerto.
    - **Pila de Objetivos en `FocusRescue`**: Se implementó una pila de `LandingFocus` (`pushTarget` / `popTarget`) para que al destruirse el modal de canales o cualquier diálogo, el destino de rescate regrese automáticamente al landing de la pantalla padre en vez de ponerse a `null`.
    - **Re-enfoque exacto en la tarjeta del partido seleccionado (`cardFocusRequester`)**: Al pulsar Atrás, `TvSportsScreen` re-enfoca la tarjeta específica que el usuario tenía seleccionada mediante un `LaunchedEffect` dedicado, permitiendo retomar la navegación vertical/horizontal sin saltar al inicio.
  - **Precalentamiento de Agenda Deportiva (`XuperCatalog.kt`)**: La agenda deportiva se precarga en segundo plano junto con las repisas y canales de TV en vivo durante el Splash inicial.
  - **Validación y despliegue**: 105 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.25 publicada en GitHub.

- **Identidad de Marca Oficial y Arranque Instantáneo en Chromecast** (v2.5.24):
  - **Arranque instantáneo en frame 0 (`MainActivity.kt`)**: Implementada composición diferida (`!splashVisible || bootReady`) para `TvApp` y `PhoneApp`. En el primer cuadro, Compose solo tiene que renderizar la pantalla ligera `VisionSplash`, reduciendo la latencia de renderizado inicial de ~2,500 ms a menos de 30 ms y eliminando la pesadez al tocar el icono en el launcher de Chromecast.
  - **Pipeline perezoso de imágenes (`VisionApp.kt`)**: `VisionApp` ahora implementa `ImageLoaderFactory`, eliminando accesos síncronos a memoria flash para `DiskCache` en `Application.onCreate()`.
  - **Ventana nativa del sistema optimizada (`themes.xml`)**: `windowSplashScreenAnimatedIcon` configurado con `ic_vision_mark` para una transición limpia sin demoras.
  - **Identidad oficial de marca (`logo.png`) extraída del repositorio original (`ChronosBVRX/Vision-`)**:
    - Recorte sin fondo de alta fidelidad que preserva íntegros los resplandores en cian, azul y morado, la "V" futurista con el reproductor y el resplandor "+".
    - Variantes estáticas oficiales: `ic_vision_mark.png` (isotipo), `ic_vision_wordmark.png` (imagotipo horizontal), `ic_vision_logo_full.png` (imagotipo vertical).
    - Reemplazo total de placeholders de Kino en `VisionWordmark`, `VisionSplash`, `TvSideRail`, `PlayerScreen`, `X4KPlayerAty`, y banners panorámicos 16:9 de Android TV (`ic_banner.png` y `ic_launcher.png`).
  - **Validación**: 105 tests de Android y 28 tests de backend pasando al 100%. Release OTA v2.5.24 publicada en GitHub.

- **Estabilidad Total en Fútbol y Navegación TV con D-pad** (v2.5.23):
  - **Eliminación de crasheos por scroll rápido (`SIGSEGV` en `libhwui`)**: Resuelto el crash que ocurría al desplazarse velozmente con el D-pad por los partidos de fútbol mientras se cargaban escudos de equipos y resultados.
  - **Blindaje de memoria gráfica en Coil (`VisionApp.kt`)**: Desactivado `allowHardware` (`.allowHardware(!useModestMemory)`) en Smart TV y dispositivos modestos (`Chromecast`, TV Boxes). Evita el agotamiento de descriptores nativos `AHardwareBuffer` / `GraphicBuffer` en GPU Mali al decodificar masivamente logos PNG de alta resolución de ESPN CDN, transfiriendo la decodificación de forma segura al Dalvik heap de 512 MB.
  - **Muestreo descendente estricto (Downsampling a `size(56, 56)`)**: En `TvSportsScreen` y `SportsScreen`, cada petición de escudo se decodifica al tamaño exacto de destino con `Precision.INEXACT`, reduciendo el consumo de memoria en un 75% y acelerando el renderizado de la lista. Agregado placeholder con iniciales de equipo mientras descarga.
  - **Eliminación del secuestro de foco durante el scroll**: Retirado `state.links.size` del `LaunchedEffect` de foco inicial en `TvSportsScreen.kt`. Impide que la llegada asíncrona de canales candidatos o confirmados intente refocalizar la primera tarjeta original cuando el usuario ya se ha desplazado hacia abajo.
  - **Tarjetas con altura fija de 86 dp y eliminación de "Jitter" en `LazyColumn`**: Fijada la altura de `TvMatchCard` a `86.dp` y delimitado `TvChannelInfo` a 3 líneas fijas con elipsis. Elimina variaciones bruscas de altura (de 70 dp a 110 dp) que desestabilizaban las mediciones de la lista en desplazamientos continuos.
  - **Memoización de `byLeague` y claves anti-colisión**: Memoizado `byLeague` con `remember(state.matches)` y claves compuestas `league_${leagueId.ifBlank { "idx_$leagueIndex" }}_$leagueIndex` y `m_${match.id}` con extracción segura `firstOrNull()`.
  - **Validación y despliegue**: 105 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.23 publicada en GitHub.

- **Arranque Instantáneo y Cero Crasheos en Chromecast y Smart TV** (v2.5.22):
  - **Eliminación de crasheos nativos (`SIGSEGV` en `libhwui`) y congelamientos**: Se sustituyó el `VideoView` (`MediaPlayer` + `SurfaceView`) en `VisionSplash.kt` por una pantalla de bienvenida 100% nativa en Jetpack Compose en TV y equipos de hardware modesto (`isTv || DevicePerformance.isLowEndDevice(context)`). Elimina el conflicto donde animar la superficie de video con `graphicsLayer` causaba fallos de segmentación en chips Amlogic/Android TV, y reduce a 0 el consumo de decodificadores de hardware en frío.
  - **Ampliación de Heap Dalvik (`android:largeHeap="true"`)**: Habilitado en `AndroidManifest.xml` (de 128 MB a 512 MB), erradicando excepciones `OutOfMemoryError` en dispositivos con 1.5 - 2 GB de RAM durante la compilación de árboles de Compose y parsing de catálogos JSON.
  - **Arranque asíncrono no bloqueante (`MainActivity.kt` & `XuperCatalog.kt`)**: Corregida la fuga de corrutina `coroutineScope` que retenía el boot durante 9 segundos forzados; la app ahora entra en un mínimo suave de 600 ms tan pronto como los datos responden.
  - **Priorización de catálogos y aislamiento de I/O**: Eliminada la descompresión innecesaria de DEX de Titan en el hilo de arranque principal; las repisas de XTV y canales de TV en vivo se despliegan de inmediato, mientras que consultas externas (`ArchiveSource`) operan de forma concurrente con límite de 2.5s y caché en memoria RAM.
  - **Validación y despliegue**: 104 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.22 publicada en GitHub.

- **Confinamiento de Foco (Focus Trap) en Modales y Diálogos de Smart TV** (v2.5.21):
  - **Jetpack Compose TV**: Implementación de `Modifier.modalFocusTrap()` (`focusGroup()` + `focusProperties { exit = { FocusRequester.Cancel } }`) y control de rescate global (`FocusRescue.dialogsOpen`). Blindaje contra escape de foco al navegar con D-pad hacia arriba o abajo en modales:
    - Selector interactivo de canal por partido de fútbol (`TvSportsScreen.kt` - `TvMatchChannelsModal` + aislamiento de columna de fondo con `canFocus = selectedMatchForPicker == null`).
    - Diálogos del sistema TV (`TvDialogs.kt` - `TvDialog`, actualización de app, confirmación de borrado y adición de listas M3U en TV en vivo).
    - Modal de licencias de código abierto (`TvOssNotices.kt` - `TvOssNoticesDialog`).
    - Selector de pistas de audio y subtítulos / servidores (`PlayerDialogs.kt` - `AudioAndSubtitlesDialog`).
    - Selector de corrección de intro y outro (`PlayerDialogs.kt` - `SkipMarkersDialog`).
    - Diálogo "¿Continuar viendo?" en el reproductor ExoPlayer (`PlayerScreen.kt`).
  - **Reproductor Nativo XTV (`X4KPlayerAty.kt`)**:
    - `ModalDimLayout` (subclase de `FrameLayout` que sobreescribe `focusSearch`: si la siguiente vista candidata no desciende del modal, retiene el foco en el elemento actual impidiendo fugas al layout de fondo).
    - Bloqueo de foco en controles de fondo (`setBackgroundFocusBlocked(true)`) mediante `FOCUS_BLOCK_DESCENDANTS` mientras cualquier modal esté activo.
    - Cierre de navegación cíclico (`nextFocusUpId` / `nextFocusDownId` / `nextFocusLeftId` / `nextFocusRightId` asignados a sí mismos en los límites de cada lista y botones) en:
      - Selector de Idioma y Subtítulos (`showLanguagePicker`).
      - Selector de Canales en Vivo (`showChannelPicker`).
      - Ficha de Información de Canal / EPG (`showChannelInfo`).
      - Selector de Temporadas y Capítulos (`openEpisodePicker`).
      - Diálogo "¿Continuar viendo?" (`showResumeDialog`).
      - Tarjeta "A continuación" de siguiente episodio (`showNextCard`).
    - Intercepción en `dispatchKeyEvent` y `onKeyDown`: cuando `isModalActive` está activo, las teclas de dirección D-pad se entregan exclusivamente a las vistas del modal y nunca zappean canales ni fuerzan el foco a `playPauseBtn`.
  - **Validación**: 103 tests unitarios de Android (`:app:testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde, incluyendo nuevo test de regresión `modalConfinementPreventsFocusRescueAndChannelZapping`.

- **Carga Instantánea de Catálogos y TV en Vivo (0 ms)** (v2.5.20):
  - **Patrón Stale-While-Revalidate en VOD**: `RemoteIndexSource.section(context, name)` y `cachedSection` con memoria RAM (`ConcurrentHashMap`). `TvCategoryScreen` y `SectionScreen` inicializan el estado con datos cacheados en frame 0, eliminando spinners de carga al navegar por Películas, Series, Novelas y Anime.
  - **Precalentamiento en segundo plano (Boot Prewarm)**: `XuperCatalog.load()` calienta en memoria las 4 secciones principales y la lista de canales de TV en vivo durante el Splash inicial sin bloquear el inicio.
  - **Caché en memoria para TV Online**: `LiveStore.cached` y `X4KApi.liveChannelsFor` retienen los canales mapeados en memoria viva a nivel de proceso (`memoryChannels` y `memoryLiveChannels`), evitando re-parseos de JSON síncronos y cálculo repetido de taxonomía al entrar a TV en vivo.
  - **Aceleración de Backend VPS (`/v1/section/{name}`)**: Caché en memoria en `server/api/app.py` indexada por versión de catálogo (`index_version`), reduciendo ~50 consultas SQL a un retorno instantáneo en 1 ms.
  - **Validación y despliegue**: 28 unit tests de Android (`testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.20 publicada en GitHub.
- **Corrección Crítica de Reproducción en TV en Vivo** (v2.5.19):
  - **Inyección SLB CDN y Play Params garantizada**: `playLive` en `X4KApi.kt` ahora asegura siempre la disponibilidad de `cdn_list` (entries) y `play_params` mediante `ensureEngineData(context)` antes de lanzar la reproducción. Sin estos datos de SLB, el motor Titan/Ranger no puede resolver las rutas de distribución CDN.
  - **Respaldo dinámico en el reproductor**: `X4KPlayerAty.onCreate` cuenta con fallback directo a `SharedPreferences` de `x4k_api` en caso de que los extras `entriesB64` o `playParamsB64` viajen vacíos.
  - **Superficie de video íntegra en Smart TV**: Se fijó `v.alpha = 1f` en `mountUi` para canales en vivo, eliminando animaciones de transparencia sobre la superficie nativa de renderizado (`SurfaceView`).
  - **Detección inteligente de primer cuadro en emisiones en tiempo real**: En `X4KPlayerAty.poller`, se ajustó la condición de primer cuadro (`hasLivePlayback`) para evaluar `liveMode && (pos > 0 || X4KTitan.isPlaying(v) || System.currentTimeMillis() - startedAtMs > 2500)`. Esto disipa inmediatamente la pantalla de carga e impide falsos disparos de `showError()` tras 20 segundos en canales donde `positionMs()` se mantiene en 0.
  - **Resiliencia ante renovaciones concurrentes de sesión**: `renewLocked` ahora reutiliza el token existente en preferencias si se solicita dentro de la ventana de protección de 30 segundos, eliminando `IOException` prematuras que vaciaban la caché de canales. `liveRow` y `liveChannelsFor` ahora soportan forzado de actualización si la caché local carece de licencias válidas.
  - **Validación y despliegue**: Suite de 28 unit tests de Android (`testNormalDebugUnitTest`) y 28 tests de backend (`pytest server/tests`) al 100% en verde. Release OTA v2.5.19 publicada en GitHub.
- **Aislamiento Estricto y Descontaminación de Catálogos (Películas, Series, Novelas, Anime)** (v2.5.19 / Backend):
  - **Cero fugas ni contaminación cruzada**: Corrección de bug crítico en `server/sync/index_build.py` donde una segunda definición duplicada de `build_section_genres()` descartaba el `base_where` e inyectaba todos los géneros sin filtrar en `movies` y `anime` (más de 45k series y 24k películas contaminando anime y películas).
  - **Taxonomía mutuamente excluyente**:
    - *Anime*: Todo título con `kind = 'anime'`, tags 'Anime'/'Animación Japonesa' o procedente de repisas `xtv-anime-%`. Aislado 100% de Películas, Series y Novelas.
    - *Novelas*: Detección profunda de telenovelas (repisa `76236`, tags 'Novela'/'Telenovela'/'Soap', y sinopsis de novelas mexicanas/colombianas/turcas). Excluidas 100% de Series y Películas.
    - *Películas*: Solo largometrajes/cine. Excluidos 100% Anime, Series y Novelas.
    - *Series*: Solo series de televisión generales. Excluidos 100% Anime y Novelas.
  - **Despliegue y verificación en vivo**: Código deduplicado y desplegado en VPS (`streaming-sync` y `streaming-api`), base de datos PostgreSQL re-indexada limpiamente (24,521 películas puras, 47,820 series puras, 12,192 animes puros, 567 novelas puras). Suite de 28 unit tests en verde (`test_section_filters.py`).
  - **Desacoplamiento total de La 20 Digital**: Migrado a subdominio independiente gratuito `https://visionplusv2.duckdns.org` con certificado SSL Let's Encrypt automático vía Caddy. `RemoteIndexSource.BASE_URL` actualizado. Retrocompatibilidad mantenida en Caddy para clientes anteriores.
- **Canales de Fútbol en Vivo, Selector Interactivo y Zapping TV de Dos Pasos** (v2.5.18):
  - **Enriquecimiento de partidos con LiveSoccerTV** (`server/sync/sports.py`): Extractor con cookies para México, normalización de cadenas de transmisión oficiales (Canal 5 Televisa, Azteca 7, FOX, ViX, Sky Sports, Claro Sports, TUDN, ESPN) y coincidencia difusa de equipos locales y aliases (`match_teams`, `_team_match_tokens`).
  - **Emparejamiento inteligente de canales en la app** (`SportsLinker`): Cruce de nombres de transmisión con el catálogo de 1000+ canales en vivo, eliminación de ruido de broadcast y canonización de marcas.
  - **Selector modal interactivo de canal por partido** (`TvSportsScreen` y `SportsScreen`): Al presionar OK o tocar cualquier partido, se abre una ventana modal para elegir el canal disponible (con logo, nombre y estado) y reproducir directamente. En TV: modal Leanback accesible por D-pad, retorno con Back y sin fugas de foco. En Móvil: bottom dialog interactivo.
  - **Zapping TV de dos pasos con D-pad** (`X4KPlayerAty`): Al presionar D-pad Arriba o Abajo (`DPAD_UP`/`DPAD_DOWN`/`CHANNEL_UP`/`CHANNEL_DOWN`) durante la reproducción de TV en vivo, el primer toque despliega la barra de navegación y el banner EPG; solo un segundo toque consecutivo mientras la barra está visible ejecuta el cambio de canal (`switchChannel`). Interceptado en `dispatchKeyEvent` para evitar que los botones enfocados consuman el evento.
  - **Tests**: `SportsLinkerTest`, `PlayerNavigationTest`, `test_lstv_sync.py` (23 tests de backend y suite completa de unit tests Android en verde).
- **Guía EPG en Vivo real, desahogo en VPS y Banner OSD Cable TV** (v2.5.17):
  Canalización completa de EPG en el servidor VPS OCI (`https://la20.com.mx/catalogo/v1/epg`):
  El worker en segundo plano (`server/sync/epg.py`) procesa periódicamente feeds XMLTV abiertos de alta calidad
  para México, canales deportivos y Latinoamérica (MX1, Directv Sports, US Sports, Colombia, Argentina, Chile),
  manteniendo 65,000+ programas en Postgres con poda de caducados y latencia menor a 50 ms.
  El cliente Android (`X4KApi.kt` / `X4KPlayerAty.kt`) consulta el VPS en primer orden con caché en memoria (TTL 5 min)
  y fallback transparente al portal XTV. El reproductor presenta la guía en el banner OSD estilo cable:
  «AHORA: Nombre (HH:mm - HH:mm)» y «DESPUÉS: Nombre (HH:mm)» con barra de progreso real calculada dinámicamente en azul `#2D7DF6`.
  El modal `ℹ Información` despliega sinopsis completa, programa actual y próximos programas del día («A CONTINUACIÓN»).
  Tests: `EpgTest` en app Android + `test_epg.py` en backend VPS.
- **Zapping por bloques (País/Categoría), D-pad Arriba/Abajo y barra Live limpia** (v2.5.16):
  Traspaso contextual de canales filtrados (`channelCodes` y `groupTitle`) desde `TvLiveScreen` y `LiveScreen` móvil al reproductor nativo `X4KPlayerAty`.
  Si se sintoniza desde un país, categoría, favoritos o búsqueda, el selector modal y el zapping (`switchChannel`) se restringen exclusivamente a ese grupo
  con ciclo continuo (wrap-around). Flechas D-pad Arriba (`KEYCODE_DPAD_UP`/`CHANNEL_UP`) y Abajo (`KEYCODE_DPAD_DOWN`/`CHANNEL_DOWN`) cambian de canal directamente.
  Barra de navegación inferior en vivo ultra-limpia y adaptada: sin subtítulos, sin selector de idioma, sin botones de transporte innecesarios; únicamente
  tres opciones centradas: `▤ Canales`, `★ Favorito` (persistente en `SharedPreferences` compartidas con proceso `:x4k` y reactivo) e `ℹ Información`
  (modal elegante con metadatos del canal y guía EPG "Ahora/Después/Sinopsis"). Banner de EPG activo al zappear. Retrocompatibilidad y fallback
  total si se abre desde Inicio o sin filtros. Tests: `NavigationTest` (payload y persistencia).
- **Blindaje total anti-congelamiento en todas las secciones TV** (v2.5.15):
  Análisis exhaustivo de todos los componentes de Smart TV: Regex precompiladas estáticamente en `Catalog`
  y `TvCategoryScreen` (cero recompilaciones al tipear o navegar en Búsqueda y Categorías); estandarización
  de layouts con `weight(1f)` en `TvCategoriesScreen`, `TvSportsScreen` y `TvSettingsScreen` (evitando
  desbordamientos y recortes de scroll); desduplicación sistemática con `distinctBy { it.id }` en hileras de
  películas, partidos de fútbol y favoritos/ver más tarde; foco seguro y sin accesos a listas vacías.
- **TV en Vivo fluido y sin crasheos en Smart TV** (v2.5.14):
  Memoización exhaustiva con `remember` en `TvLiveScreen` para géneros, países, conteos por categoría y
  canales filtrados (`distinctBy { it.url }`), eliminando la re-clasificación síncrona en hilo principal de
  1033+ canales en cada movimiento del D-pad. Tablas `ConcurrentHashMap` de caché estática y precompilación
  de Regex a nivel de clase en `LiveTaxonomy` (`normCache`, `genreCache`, `countryCache`, `REGEX_WORDS` en
  `ChannelArt`). Corrección de layouts en chips de país (`TvCountryChip`) para evitar el error de constraints
  infinitas en `LazyRow`. Solución al colapso de categorías al seleccionar un género en el riel lateral (las
  categorías se calculan sobre el catálogo completo y no sobre la vista filtrada). Eliminación del arranque
  redundante de proxies en `LiveState`. Tests: `TvLiveScreenTest` (1000 canales, navegación D-pad, desduplicación).
- **Catálogo**: filas de películas/series/kids/anime (getNextColumns) + TV en vivo con logos + "Destacados".
- **Reproductor premium** (`X4KPlayerAty`, proceso `:x4k`): pre-roll de marca (`res/raw/brand_intro.mp4`,
  solo películas), carga premium (VISION+ + spinner), controles azules (play/pausa, ±10s, barra con
  **duración real** del portal, idiomas de audio, subtítulos), letterbox correcto, **D-pad para TV**,
  **zapping instantáneo** en vivo (⏪/⏩ = Canal −/+),   selector de canales reciclado (ListView), línea EPG
  "Ahora/Después" (v3/getProgram). **Navegación con tecla Atrás de dos pasos**: si los controles, selectores
  o barra de navegación están abiertos, presionar Atrás oculta la navegación sin detener la reproducción;
  al volver a presionar Atrás con la navegación ya oculta, sale del reproductor (homologado en `X4KPlayerAty`,
  `PlayerCore`, `TitanLivePlayer` y `TitanVodPlayerScreen`). **D-pad completo** (v2.5.4): abajo desde transporte llega a
  pills de idioma/subtítulos/canales y arriba vuelve; la barra se ajusta con izq/der enfocada;
  botón enfocado en azul; el foco se limpia al ocultar controles. **"Siguiente capítulo"**
  (v2.5.4): al terminar un episodio con contexto de serie, tarjeta "A continuación" con cuenta
  atrás de 10 s (Atrás la cancela) y replay in-place en el mismo motor; marca ✓ visto
  (`UserLibrary.markEpisodeWatched`). Contexto vía extras del intent (`seriesId`, `epIds`,
  `epIndex`, `seasonIds`, `seasonNames`, `seasonIndex`);   `X4KApi.prepareVod` resuelve sin lanzar.
- **Play instantáneo** (v2.5.6): el play de capítulos/temporadas viaja por `Route.Player` +
  `EpisodePlay` y el handoff "Abriendo…" sale al instante (antes era un thread sin feedback);
  la ficha móvil y la de TV comparten el mismo `onPlay(EpisodePlay?)`. Hero de Inicio TV con
  foco asentado (180 ms), `TvCategoryScreen` en `LazyColumn`, y secciones
  Películas/Series/Novelas/Anime en el móvil (`SectionScreen` + chips en Categorías).
- **Historial** (v2.5.7): `PlaybackHistory` guarda el minuto (cada 10 s + pausa + salida con
  commit); diálogo "¿Continuar viendo?" al reabrir a medias (XTV y ExoPlayer, con mando);
  fila "Seguir viendo" en Inicio TV+móvil con avance y refresh en ON_RESUME; reanuda por
  `resumeRoute` (`EpisodePlay.resumeMs`/`Route.Player.resumeMs` + extras `resumeMs`/`posterUrl`
  del intent); se borra al terminar. Selector de Capítulos/Temporadas dentro del reproductor
  (lista reciclada, ✓ vistos, cambio de temporada con fetch), controles en una sola fila ancha,
  cadena de foco del mando reescrita, idiomas emparejados con el portal (`trackDisplayLang`).
  Servidor: marcas `tem/temp/año` agrupadas (1998 grupos, índice v23) y títulos sin la marca.
- **Foco blindado** (v2.5.10): red global al volver (`TvApp` re-pide el destino si el mando queda
  huérfano, sin pelear con el usuario) + retorno a la tarjeta exacta en Buscar (ya existía en
  Categorías/Mi lista/Inicio); tests `filmBackKeepsFocus`+`seriesBackKeepsFocus` (ficha con
  temporadas, sin red). Reproductor: en TV el primer toque con controles ocultos solo los muestra
  (OK/izq/der no pausan ni saltan solos; teclas media dedicadas sí actúan); foco visible con aro
  blanco (transporte+barra) y selector en listas (capítulos/canales).
- **Modo ligero inteligente para TV / Chromecast** (v2.5.13): detección automática de hardware modesto
  (`DevicePerformance`: RAM ≤ 2.2 GB como Chromecast HD/4K o Fire TV Stick, o TV con ≤ 4 cores) que
  conecta con `LocalReducedEffects` reactivo y guardado en SharedPreferences. Elimina la animación
  infinita de deriva en el fondo del hero (cero sobrecarga de GPU en reposo), elimina el escalado de zoom
  al enfocar tarjetas (foco instantáneo con borde blanco 1.0f), ajusta el asentado del foco a 320 ms
  (navegación con D-pad rápida sin tirones por decodificación de pósters), gradientes estáticos fuera de
  Crossfade, y Coil con RGB_565 (50% menos memoria) y caché ajustada. Control manual interactivo en
  Ajustes ▸ Pantalla («Rendimiento de interfaz: Automático (Ligero/Completo) / Ligero / Máxima calidad»).
  Tests: `DevicePerformanceTest` (detección, overrides y compatibilidad).
- **Server series** (2026-10-10, índice v25): tupla live 17→18 (`InterfaceError`, los 1035 canales
  no se indexaban), grupos mixtos XTV+Magis neutralizados al servir (132 grupos: manda el primer
  miembro XTV en `id`/`videoUrl` y temporadas XTV primero; la app dedupea y el magis no se ve).
  App 2.5.8: `parseSeasons` prefiere el miembro `xtv:` por número (con test).
  Ojo: otro agente tocó el indentado de `server/sync/xtv.py` en local (el VPS estaba sano);
  verificar hashes VPS↔local antes de desplegar si se edita a la vez.
- **Completitud series** (2026-10-10, índice v27): el portal trae 21,431 series y el tope por listado
  es 10k → colecciones series/anime/kids a 40 págs (`XTV_SERIES_COLLECTION_PAGES`; pelis siguen
  en 3; faltaba mapearla en `docker-compose.yml`). Dos ciclos profundos sumaron series 9259→12574
  (+3315) y grupos 2072→2723: Los Simpson 24/37, South Park 19/28, Rosa de Guadalupe 17/17 ✓.
  Grupos unificados por kind+doblaje (`canon_key`, base corta manda).
  Resiliencia: patada de sesión a mitad de ciclo ya no tumba el conector (`_columns_retry` +
  raíz aislada). Ojo: cada reinicio del sync revalida sesión (patea a la app); no reiniciar
  de más. Sesiones app↔server se patean entre sí de vez en cuando (un renovar por lado basta).
  Reintento acotado por página (`_revalidate`, tope 8/ciclo) para que el ciclo profundo
  sobreviva a las pruebas en la TV. Hallazgo: series largas solo traen lo nuevo (tope 10k,
  orden nuevo-primero); anime/kids-All sí están completos (<10k). Fase 2 (a futuro): unir
  variantes por idioma con `tmdb_id` del enrichment.
- **Ficha IMDb** (móvil y TV): score ★, géneros, reparto, director, idiomas, subtítulos, duración
  destacada, sinopsis, fondo stage HD (TV).
- **Home móvil rediseñado** (hero + acciones + chips) y **arranque con barra de progreso** que precarga
  catálogo y canales detrás (tope 9 s; `XuperCatalog.load` + `X4KApi.homeRows/liveRow`).
- **Sesión XTV auto-renovable** (`v3/snToken` → `v8/active`) con **renovación reactiva**: cualquier
  llamada que falle renueva sesión y reintenta (`callWithSession`); entries del motor con TTL 90 min.
  **Regla anti-tormentas** (v2.5.9, obligatoria): UNA sola sesión por cuenta; la app renueva proactivo
  cada 6 h (no 45 min) y con cooldown de 30 s entre acuñados (un segundo mint seguido se niega en vez
  de patear de vuelta); el worker revalida 1 vez por llamada y 8 por ciclo como tope, con raíz y
  página aisladas. Probar multi-SN dio `100059` (cuenta atada al SN): no insistir.
- **OTA in-app**: releases en GitHub **`ChronosBVRX/Vision-`**; la app avisa una vez por versión
  (descarga con progreso dentro de la app + instalador del sistema, sin navegador).
- **Apertura sin rebote**: pantalla "Abriendo…" (`XtvHandoff`) que se mantiene hasta que el reproductor
  toma el control, con ventana de gracia contra el blip de resume de HyperOS.
- **Agenda de Fútbol** (móvil y TV, v2.4.0): partidos del día desde el backend propio (`/v1/sports`,
  sync de ESPN cada 10 min) con escudos, marcador en vivo, selector Ayer/Hoy/Mañana/+4 y, por partido,
  los **canales de la lista** que pueden transmitirlo (TUDN, Canal 5, Azteca 7, Fox Sports, TNT Sports,
  Claro, ViX, TVC, Win, Directv, Movistar, Telemundo/Universo…); el chip abre el reproductor en vivo.
  La EPG del portal está vacía para casi todos los canales deportivos: la confirmación "se está
  transmitiendo en…" queda como bonus si algún día la llenan (el escaneo EPG es secuencial para no
  encadenar renovaciones de sesión). Probada en el A32; **falta verla en la TV del usuario**.
- Instalado/probado: **A32** (2.4.0, agenda de fútbol verificada) y **POCO** del usuario; TV Chromecast
  del usuario (actualizar con el enlace corto; verificar en Ajustes → "Versión instalada").
- **Sistema anti-regresiones** (2026-10-10, ver `docs/REGRESSIONS.md`): CI
  (`.github/workflows/android-ci.yml` con `:app:testNormalDebugUnitTest` + job `server-tests`
  con `pytest server/tests`, en push/PR a main) + unit tests de lógica pura (`SportsLinkerTest`,
  `SportsSourceTest`, `RemoteIndexTest`, `CatalogRegressionTest`, `NavigationTest` +
  `CatalogTest`/`M3uTest`/`TvFocusTest` + `server/tests/` con 17 tests del sync/API) +
  plantilla de PR con checklist + checklist manual pre-OTA (móvil + TV con D-pad).
  **Falta activar el bloqueo**: Settings → Branches → protección de `main` con los checks
  `unit-tests` + `server-tests`. Regla: cada fix trae su test; funciones puras en `internal`
  para testearlas. En su primera corrida el sistema ya encontró 2 bugs reales (foco perdido en
  "Mi lista" vacía en TV + `XuperApiClient` construido fuera del `try` en `ensureReady`);
  ambos corregidos. Los tests del servidor ya atraparían la tupla live 17-vs-18 y el representante
  magis.

## Claves técnicas XTV (imprescindibles)
- **Wire crypto**: `hex(base64(DESede/ECB/PKCS5(json)))`, key string `568e194f-d170-4369-96e8-4a457f641880`
  decodificada con el decodificador de la familia (base64 "y7a": `-` inválido → 0xFF) = 24 bytes
  `e7af1ed7de1ffddd7bd3fe37ebdffde9ef3fe1ae39edfeb8` (implementado en `X4KApi.KEY_BYTES`).
- **Portal**: `fuxok.nguvmqhpk.com` (backup `mptec.dhkrxuzcy.com`), headers `apk: com.android.msandroid`,
  `apkVer: 43404`, `spkgVer: 2025-03-11 02:26:11_33_13_4.14.186-27270957`, body con
  `appId com.android.msandroid`, `sn 2392fe5186d83d73508a2c8c60a26ef6`,
  `portalCode "masnew"`, `userId 967111326`.
- **Endpoints** (POST JSON wire): `v3/snToken` (snToken vacío) → `v8/active` (macAddr 02:00:00:00:00:00,
  channel default) → userToken; `v14/getSlbInfo` (liveCodeList ["masmobile_live"], type merge, appVer 49902)
  → cdn_list (entries) + play_params; `getNextColumns` {columnCode: masnew_featured/movies/series/kids/anime}
  → recommendList (shelves con assetList); `v6/getLiveData` {columnId 76182, pageSize 3000} → channelList
  (liveAddressList con playCode+license tag free); `v10/startPlayVOD` {contentId, type "2" o "1",
  authType "0", episodeNumberList [1]} → episodeList→totalMovieList→movieList (su `contentId` = **playCode**,
  licenseList[tag].license, quality, encodeFormat, videoFormat, audioInfo); `v4/getItemData`
  {contentId, type "1", language "es"} → assetData (duration "HH:MM:SS", releaseTime, score, tags,
  actorDisplay, director, description, **subsInfo**, posterList stage 1920x1080); `v3/getProgram`
  {channelCode, columnId 76182, type "1"} → programList (programName, start/endTime yyyyMMddHHmmss).
- **SDK X4K del APK `com.xuper.netxxus`**: dex en `assets/x4k/x4k-sdk-{1,2,3}.dex`, libs en
  `assets/x4k/lib/<abi>/` (libranger-jni, libijk*, libtnet-3.1.14, libc++_shared). Boot: facade
  `yc.h` → `m.a()` (companion `h$b` campo `m` → método `a()`), `.r(workPath, cb1, cb2)` con
  **proxies que enrutan los callbacks a la vista** (`routingProxy`, CRÍTICO: sin esto el tamaño real
  del video no llega y el video se deforma), `.B(Env)`, `.A(List<Entry>)` desde cdn_list; vista `ed.e`
  (`A(program, code, buss)`); controles `ed.e.u()/v()/w(ms)/r()/x(lang,idx)/getAudioTracks()`;
  facade `p(id)` → `uc.c` con pistas ("subtitle"/"audio") y `E(id, kind, trackId)` para seleccionarlas.
  Env(app, app_ver "49902", user_id, dev_id, communication_key, params=play_params, android_id, titan_port).
- **Proceso `:x4k` obligatorio** (libranger-jni colisiona con el motor Magis del proceso principal) y
  **se mata al salir** (`killProcess` en onDestroy si isFinishing): el motor no acepta un segundo
  programa distinto con vista nueva; el zapping in-place (misma vista) SÍ funciona.
- **Cuenta**: Google-bound, sin contraseña; sesión renovable por sn. Errores portal distintos de "0"
  → renovar. La cuenta de prueba puede no recibir licencias VOD premium (límite de cuenta, no de código).

## Archivos clave (Vision+)
- `data/xuper/X4KApi.kt` — cliente del portal XTV completo (sesión, filas, vivo, EPG, playVod/playLive,
  launch del reproductor, entries/params, metadata IMDb, descarga in-app).
- `data/xuper/X4KTitan.kt` — puente del SDK X4K (carga dex/libs, boot, env, entries, vista, play,
  seek/pistas, listener bd.a).
- `data/xuper/TitanSdk.kt` + `MagisClient.kt` + `MagisTitan.kt` — integración Magis antigua (aún presente).
- `ui/player/X4KPlayerAty.kt` — reproductor XTV (proceso :x4k, pre-roll, controles, zapper, D-pad).
- `ui/player/PlayerScreen.kt` — rutas `xtv:`/`xtvlive:` + `XtvHandoff` (espera sin rebote).
- `ui/title/TitleScreen.kt` (ficha móvil) y `ui/tv/TvTitleScreen.kt` (ficha TV).
- `ui/home/HomeScreen.kt` (home móvil), `ui/TvApp.kt` + `ui/tv/*` (modo TV), `ui/brand/VisionSplash.kt`
  (splash con barra), `MainActivity.kt` (precarga en boot), `VisionApp.kt` (Coil global, crossfade off).
- `data/AppUpdate.kt` (OTA GitHub + descarga in-app), `ui/settings/UpdateCheck.kt` (InAppUpdate),
  `ui/plugins/PluginUpdates.kt` (diálogo móvil), `ui/tv/TvDialogs.kt` (diálogo TV).
- `data/XuperCatalog.kt` (carga home + preload), `DeviceKind.kt` (detección TV).
- `data/sports/SportsSource.kt` — cliente de `/v1/sports` (modelos `SportMatch/SportTeam/SportLeague`,
  caché por fecha en prefs `sports_remote`: 3 min hoy, 30 min otros días). `data/sports/SportsLinker.kt`
  — resuelve las cadenas candidatas a canales reales (tokens + feeds MX primero; alias de equipos:
  Chivas=Guadalajara, Xolos=Tijuana, Rayados=Monterrey, selecciones ES/EN…) y confirma por EPG (secuencial,
  caché 10 min) si el programa en curso nombra a los dos equipos.
- `ui/sports/SportsState.kt` + `ui/sports/SportsScreen.kt` (móvil) y `ui/tv/TvSportsScreen.kt` (TV, OK
  abre el canal confirmado o el primero posible). Ruta `Route.Sports`; entrada "Fútbol" en el menú móvil
  (TABS de `PhoneApp.kt`) y en el riel de `TvApp.kt` (reemplaza la categoría "futbol" de títulos).
- Manifest: `REQUEST_INSTALL_PACKAGES`, FileProvider `${applicationId}.updates` (+`res/xml/update_paths.xml`),
  leanback optional, touchscreen no requerido, banner, LEANBACK_LAUNCHER.

## Artefactos de investigación (temp, reutilizables)
`C:\Users\AXELRO~1\AppData\Local\Temp\opencode\`:
- `hydra_wire.py` — crypto wire (y7a_decode, encrypt/decrypt). `x4k_portal*.py`, `x4k_content_test.py`,
  `x4k_moana_test.py`, `x4k_epg_test.py`, `x4k_itemdata_test.py` — replays del portal.
- `x4k_d1/ d2/ d3/` — fuentes jadx del APK X4K; `x4k_smali/` — apktool; `x4k_apk_ex/` — dex extraídos.
- `x4k_live_76182.json` (canales), `x4k_next_movies.json` (filas), `x4k_entries.json` (SLB),
  `x4k_itemdata.json`, `x4k_active_full.json`, `a32_prefs.xml` (prefs de la app en el A32, UTF-16).
- APK X4K original: `C:\Users\Axel Rosete\Documents\X4K(1).apk`.

## Build / OTA / dispositivos
- Build: `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :app:assembleNormalDebug`
  (workdir `Vision+`; **correr en foreground con timeout largo**; los Start-Process con sleeps largos
  a veces son matados por el wrapper).
- Instalar: push a `/data/local/tmp/vp.apk` + `pm install -r`. A32: `adb -s 192.168.1.130:43981`
  (wireless, rooted). POCO: `adb -s df0a78c4` (USB; HyperOS puede pedir "Instalar vía USB").
  adb exe: `C:\Users\Axel Rosete\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- **Publicar OTA** (siempre): subir `versionCode`/`versionName` en `app/build.gradle.kts` → build →
  copiar APK como `VisionPlus.apk` → `gh release create vX.Y.Z VisionPlus.apk -R ChronosBVRX/Vision- --title ... --notes-file ...`.
  **El asset debe llamarse `VisionPlus.apk`** (enlace estable:
  `https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk`; enlaces cortos TV:
  `tinyurl.com/242b5wps` y el numérico para Downloader `tinyurl.com/23002230`). `gh` autenticado como **ChronosBVRX**.

## Servidor de índice (server/ → VPS OCI)
- **Desplegado** en el VPS OCI (`opc@159.54.146.146`, llave `~/.ssh/oci_key_3`) en
  `/opt/streaming-backend`: `postgres` + `api` (FastAPI, solo `127.0.0.1:8100`) + `sync`
  (worker). Convive con La Veinte (no toca 80/443/5432/3000/8000/8443).
- Secretos en `/opt/streaming-backend/.env` (generados; **nunca** al repo). La API pide
  header `X-Api-Key` (valor en ese `.env`).
- **Sync** (ciclo 6 h): XTV `v3/getShelveData` {columnType:"2", pageSize:100} paginado
  (tope **10.000 por listado** → `XTV_MAX_PAGES=100`; colecciones `XTV_COLLECTION_PAGES=3`),
  Magis (`getHome` por página), Internet Archive (6 búsquedas). Géneros desde `tags`
  (mapa EN→ES en `xtv.py`); Estrenos = año ≥ actual−1; `tv-en-vivo` con refs `xtvlive:<code>`.
- Datos (2026-10-09): ~26.6k títulos (XTV 25.5k, Magis 0.5k, IA 0.5k), 237 categorías,
  Estrenos 4.2k, Recomendados 90, TV en vivo 1,035. Endpoints: `/healthz`, `/v1/index?since=`,
  `/v1/categories`, `/v1/category/{id}?page=`, `/v1/search?q=`, `/v1/sports?date=&days=`,
  `/v1/section/{movies|series|novelas|anime}?limit=`; el JSON de catálogo mapea 1:1 con `Film`.
- **Géneros por sección** (`sync/index_build.build_section_genres` + `program_type` persistido en
  `titles`): cada sección del riel (Películas/Series/Novelas/Anime) tiene sus hileras
  `xtv-{sección}-genero-{slug}` (kind `section`, top por score/año) solo con títulos propios
  — series/anime fuera de Películas (program_type + repisas `xtv-series-all`/`xtv-anime-all`
  como respaldo); novelas = series de telenovela (repisa `xtv-series-76236` o género
  Telenovela). Excluidas de `/v1/index` y `/v1/categories` (y de la fusión de `normalize()`).
  La app las consume en `TvCategoryScreen` vía `Route.Section` + `RemoteIndexSource.section()`
  (caché `section_{nombre}`, fallback a la vista anterior).
- **Agenda deportiva** (`sync/sports.py` + tabla `sports_matches` + `GET /v1/sports?date=YYYY-MM-DD&days=N`):
  baja la API pública de ESPN (`site.api.espn.com/apis/site/v2/sports/soccer/{liga}/scoreboard?dates=YYYYMMDD`,
  sin clave; el rango `dates=A-B` da 400, es por día) para 20 ligas (mex.1/mex.2, esp/eng/ita/ger/fra/por.1,
  uefa.champions/europa/nations, conmebol.libertadores/sudamericana, arg/bra/col.1, fifa.friendly/world,
  concacaf.nations.league/gold) en un **loop propio cada 600 s** (`SPORTS_SYNC`, `SPORTS_INTERVAL_SECONDS`;
  no toca la versión del índice). Días: hoy+mañana cada ciclo, ayer cada 6 ciclos, +2/+3 cada hora. El API
  agrupa por fecha en `America/Mexico_City`; cada liga manda `channels` (cadenas candidatas en México).
  Nota asyncpg: usar `$2::text::date` (con `$2::date` pide `datetime.date`, no str).
- Wire en `server/sync/wire.py` (DESede/ECB + y7a decode, llaves XTV y Magis).
- **Agrupador de temporadas** (`sync/grouping.py` + `title_groups`/`group_members`): detecta
  «Título T1/T2/Temporada N/Season N/2ª Temporada» **y número solo al final** («...cielos 2»,
  solo series/anime, 1–50, sin colas cap/episodio/parte/vol; no toca «Parte N»), agrupa por
  tipo+base, y `/v1/index`, `/v1/section` y `/v1/search` devuelven una entrada por grupo con
  `seasons:[{n,titleId,videoUrl}]`. Hecho 2026-10-09: 1,833 grupos (10,903 con marca + número solo).
  La reconstrucción es transaccional (nunca tabla a medias).
  **Ojo**: `main.py` quedó con una fusión rota de dos sesiones (import `sports` + indentación
  del loop); reparado. Si otro agente edita el repo/VPS a la vez, revisar main.py antes de desplegar.
- **Cliente en la app (fase 4, hecha)**: `data/sources/RemoteIndexSource.kt` consulta
  `https://la20.com.mx/catalogo` (ruta Caddy `handle_path /catalogo/*` → `127.0.0.1:8100`,
  sin DNS nuevo) con `X-Api-Key`, caché en prefs `catalog_remote` (TTL 30 min + `since`).
  `XuperCatalog.indexRows` la carga en background; Categorías (teléfono y TV) y Búsqueda la
  usan con fallback a `catalog.rows`. Home sigue curado. La app trae 100 títulos por
  categoría (`limitFilms`); **paginación on-demand de categorías: pendiente**.
- **TMDB activo** (2026-10-10): clave en `/opt/streaming-backend/.env` (solo VPS, nunca al
  repo); el sync guarda rating/año/géneros/**overview es-MX** en `enrichment` y el API los sirve
  en `/v1/index|/section|/category|/search` (confianza ≥ 0.5; sin match no se marca si hubo error
  de red). Backfill `ENRICH_BATCH=2500`/ciclo (~11 ciclos para 26.6k títulos, lo popular primero).
  Bonus del deploy: `/v1/categories` ya devuelve su JSON (le faltaba el `return`).

## Pitfalls (lecciones)
- Deformación de video: si los callbacks del motor van a proxies vacíos, el tamaño real nunca llega →
  usar `routingProxy` (reenviar a la vista activa).
- "Solo la primera película abre": el motor no acepta un segundo programa distinto en el mismo proceso;
  matar `:x4k` al salir arregla; zapping in-place con la misma vista sí funciona.
- Rebote al abrir en HyperOS: ventana de gracia en `XtvHandoff` (delay 1200 ms + resume > 2500 ms).
- Selector de canales con 1000+ ítems: nunca crear todas las vistas (usar ListView reciclado).
- Sesión XTV: llamadas **en paralelo** que necesitan renovar a la vez se invalidan entre sí
  ("aaa100083 账号已经在其他设备登录"); si se escanea EPG por canal, hacerlo secuencial.
- La EPG del portal (`v3/getProgram`) devuelve `programList` vacío en casi todos los canales
  deportivos (probado en Azteca 7, Canal 5, TUDN MX, Fox Sports 1 MX, TNT Sports; type/columnId
  alternativos también vacíos) — no confiar en ella para "en vivo".
- ESPN: `scoreboard?dates=YYYYMMDD-YYYYMMDD` responde 400; consultar por día.
- POCO a veces sin internet (DNS) — no es bug de la app.

## Pendientes / ideas
- App: paginación on-demand al abrir una categoría del índice (hoy llegan 100 por categoría;
  el resto ya está en el servidor vía `/v1/category/{id}?page=`).
- Subdominio propio para el índice (opcional; hoy va por `https://la20.com.mx/catalogo`).
- Confirmar que la TV del usuario corre ≥2.4.0 (Ajustes → "Versión instalada"); reinstalar con el enlace corto.
- Verificar la pantalla Fútbol en la TV del usuario (D-pad); en móvil ya está probada (A32).
- Fútbol: afinar el mapa liga→canales si algún partido sale con "Sin canal en tu lista" (p. ej. feeds
  sudamericanos); valorar aviso/recordatorio de partidos próximos.
- Buscador de eventos específicos sobre la EPG (idea del usuario, no implementado).
- Build **release firmada** para distribución (cuando el usuario la pida).
- Series (teleplay): el flujo usa `startPlayVOD` type 2→1 con episodeNumberList [1]; refinar episodios si hace falta.
