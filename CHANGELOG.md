# Changelog

## 2.5.35 — Continuidad Secuencial de Series y Selector Pro de Temporadas y Capítulos
- Ordenamiento estricto de capítulos (`X4KApi.parseEpisodePrograms` y `parsePlayEpisodePrograms`) por `seriesNumber` / `episodeNumber` con desduplicación y respaldo por regex.
- Detección nativa de fin de episodio (`bd.a.z2` en `X4KTitan.makePlayerListener` → `onEpisodeEnded`) y duración real por capítulo sin heredar escalas mayores del episodio previo.
- Continuidad completa entre temporadas: al pasar al `C1` de la siguiente temporada se actualizan `epIds` y `epNames` de la nueva temporada y se conserva el título base de la serie (`Serie · T# · C#`).
- Rediseño Pro y Premium del selector de temporadas y capítulos en el reproductor (`X4KPlayerAty`), Smart TV (`TvTitleScreen`) y móvil (`TitleScreen`) con la paleta oficial de Vision+ (`VisionBlue`, `VisionBlueBright`, `VisionSurface`, `VisionSurfaceHigh`) y botón inteligente `▶ Continuar · C#`.
- 131 pruebas unitarias de Android pasando al 100%.

## 2.5.33 — Navegación D-Pad Fluida en Reproductores, Barra de Progreso y Restauración de Menús
- Navegación en barra de progreso (Scrubber) en Smart TV: implementado performSeekBy en X4KPlayerAty con OnKeyListener en SeekBar para D-pad Izquierda/Derecha (±10s), Abajo (foco a Play/Pause) y OK (togglePlay).
- Adelanto y retroceso inmediato: DPAD_LEFT y DPAD_RIGHT en X4KPlayerAty ejecutan el salto de 10s al instante y despiertan los controles OSD.
- Foco en barra de progreso Compose (PlayerScreen.kt): agregados .focusable() y focusProperties al SeekBar, permitiendo subir desde los botones de transporte, animar el grosor de pista y resaltar el pulgar en blanco nítido.
- Restauración de foco al descartar menús y selectores (X4KPlayerAty.kt): rastreo de lastPickerSourceBtn en selectores de audio, subtítulos, episodios, canales e info, reenfocando el botón de origen tras cerrar con Atrás o descartar.
- Confinamiento desbloqueado en diálogos (PlayerDialogs.kt): erradicado modalFocusTrap del contenedor interno que impedía descender a los botones de acción «Cerrar» y «Cancelar».
- 125 pruebas unitarias de Android y 33 de backend pasando al 100%.

## 2.5.32 — Blindaje Definitivo de Foco D-Pad al Salir del Modal en Fútbol
- Aislamiento de ventana con Dialog: TvMatchChannelsModal migrado a Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)), preservando el árbol de foco de la Activity subyacente.
- Erradicación de bloqueo de foco en fondo: retirada la directiva focusProperties { canFocus = selectedMatchForPicker == null } que invalidaba los nodos de LazyColumn al cerrar el diálogo.
- Escalafón de recuperación de foco inmediato al salir del modal (Atrás o Cerrar): reseteo forzoso de FocusRescue.screenHasFocus y re-enfoque prioritario a cardFocusRequester, con fallback seguro a landing y a la fila de filtros de liga.
- Tests unitarios actualizados: incorporado sportsModalDismissRestoresDpadFocus y añadida la ruta Route.Sports a everyPlaceholderScreenLands en TvFocusTest.kt (123 tests de Android y 33 de backend al 100%).

## 2.5.31 — Sello Propio Definitivo y Desacoplamiento Completo
- Consolidación total de la identidad y autoría de Vision+ bajo el sello oficial de ChronosBVRX.
- Desacoplamiento de residuos históricos: eliminación de flavors de laboratorio experimentales (`kinoid` / `com.arkiv.player.light`) en la configuración de compilación de Gradle.
- Pantalla «Acerca de» actualizada con la descripción oficial y titularidad exclusiva de ChronosBVRX tanto en Smart TV como en dispositivos móviles.
- Documentación técnica y avisos legales (`README.md`, `LEGAL.md`, `NOTICE`, `THIRD_PARTY_NOTICES.md`) completamente reescritos y alineados con la arquitectura y características reales de la plataforma.
- 122 tests unitarios de Android pasando al 100%.

## 2.5.30 — Navegación D-Pad Blindada y Detección Exacta de Canales en Fútbol
- Navegación D-pad blindada en Smart TV: implementado rememberLazyListState con reseteo de scroll automático al cambiar de fecha o liga, y focusGroup en filas y lista.
- Conexiones direccionales explícitas con focusProperties: navegación fluida arriba/abajo entre días, filtros de liga y partidos sin extravíos de foco en encabezados.
- Validación estricta de derechos de transmisión por liga (LEAGUE_DISALLOWED_BRANDS): erradicadas sugerencias inválidas como Fox Sports para Serie A o LaLiga.
- Fallback resiliente con curl en servidor para burlar el bloqueo Cloudflare HTTP 403 y capturar la guía de transmisión real de México.
- Mapeo canónico enriquecido para señales de transmisión oficiales en SportsLinker.kt y sync de backend.
- 122 tests unitarios de Android y 33 de backend pasando al 100%.

## 2.5.29 — Estabilidad Definitiva y Filtro Elegante de Ligas en Fútbol
- Eliminación de crashes en móvil: reemplazado Modifier.weight en ChannelChip dentro de LazyRow por Modifier.widthIn(max = 160.dp).
- Erradicación de congelamientos y ANR: caché de marcas en memoria viva (brandInfoCache) en SportsLinker y cálculo diferido en corrutinas de segundo plano.
- Blindaje universal GPU contra SIGSEGV en libhwui.so en decodificación de logos deportivos vía Coil.
- Filtro interactivo de ligas premium con navegación D-pad en Smart TV (TvLeagueFilterChip) y barra táctil en móvil con badges de conteo y logos oficiales.
- 121 tests unitarios de Android y 33 de backend pasando al 100%.

## 2.5.28 — Buscador Universal Inteligente y Búsqueda por Voz
- Búsqueda por voz nativa con botón en D-pad de Smart TV y micrófono en móvil.
- Búsqueda difusa insensible a acentos y diacríticos (unaccent) en PostgreSQL / VPS.
- Soporte multicriterio por tokens (título, director, reparto, sinopsis) con ranking ponderado por relevancia y calidad.

## 2.5.27 — Estrenos y Novedades Curadas + Blindaje del VPS
- Repisas de estrenos aisladas por sección en base de datos PostgreSQL.
- Blindaje de seguridad en endpoints públicos y compresión de red GZIP activa.
- Duración real inmutable en reproductores VOD erradicando barra progresiva fragmentaria.

## 2.5.26 — Detección Precisa de Canales y Países en Fútbol
- Aislamiento estricto de ediciones numéricas de canales deportivos (BrandInfo y scoreChannel): "ESPN" ya no secuestra "ESPN 2" o "ESPN 3", ni "Fox Sports" confunde con "Fox Sports 2" o "Fox News".
- Priorización regional y correspondencia por país mediante LiveTaxonomy: señales de México (🇲🇽) priorizadas al máximo para audiencia nacional, evitando feeds sudamericanos (Argentina, Chile, Colombia) que emitían otra programación deportiva. Feeds específicos (ej. TUDN USA) respetados.
- Insignia con bandera y país (🇲🇽 México, 🇦🇷 Argentina, 🇺🇸 EE. UU., etc.) en el selector de canales de Smart TV (TvMatchChannelsModal) y Móvil (MobileMatchChannelsDialog).
- Escaneo EPG profundo de título y sinopsis en SportsLinker.confirm, detectando equipos en la descripción y mostrando el programa actual en vivo en la interfaz.
- 111 tests unitarios de Android y 28 de backend pasando al 100% sin regresiones.

## 2.5.25 — Opciones de Fútbol al Instante y Foco D-pad
- Canales de transmisión de partidos en vivo disponibles al instante (0 ms): resolución síncrona en memoria (SportsLinker.quickResolve y quickResolveAll) cruzando con los canales en vivo de X4KApi.cachedLiveChannels.
- Inicialización en frame 0 en SportsState para partidos y canales candidatos cacheados tanto al abrir Fútbol como al cambiar de fecha.
- Respaldo directo en el modal de selección de canales (TvMatchChannelsModal y MobileMatchChannelsDialog) para garantizar candidatos sin esperas de red.
- Eliminada la pérdida de navegación D-pad al cerrar el modal de canales con la tecla Atrás: implementación de pila de objetivos en FocusRescue (pushTarget/popTarget) para nunca perder el landing de la pantalla padre.
- Re-enfoque automático exacto en la tarjeta del partido seleccionado (cardFocusRequester) tras cerrar el modal de canales.
- Precalentamiento de la agenda de partidos en segundo plano durante el arranque en XuperCatalog.

## 2.5.24 — Identidad Oficial y Arranque Instantáneo en Chromecast
- Arranque instantáneo al hacer clic en la app: composición diferida de TvApp y PhoneApp en MainActivity (frame 0 solo dibuja VisionSplash en <30 ms).
- Inicialización perezosa de Coil con ImageLoaderFactory en VisionApp (elimina bloqueos síncronos de disco en Application.onCreate).
- Integración de la identidad visual oficial rescatada del repositorio original: isotipo, imagotipo y logos estáticos sin fondo de alta fidelidad.
- Banners 16:9 oficiales de Android TV (ic_banner.png y ic_launcher.png) actualizados para Google TV.

## 2.5.23 — Estabilidad en Fútbol y Navegación TV con D-pad
- Eliminación de crasheos por scroll rápido en menú de Fútbol al desactivar hardware bitmaps en Coil para TV y dispositivos modestos (evita agotamiento de buffers GPU Mali y SIGSEGV en libhwui).
- Muestreo descendente estricto (downsampling a 56x56 dp) en escudos de equipos de ESPN CDN e iniciales de respaldo mientras cargan.
- Eliminación de secuestro de foco durante el scroll (removido state.links.size de LaunchedEffect en TvSportsScreen).
- Altura fija de 86 dp en TvMatchCard y layout de 3 líneas con elipsis en TvChannelInfo para eliminar variaciones de altura (jitter) en LazyColumn.
- Memoización de byLeague y claves anti-colisión seguras en TvSportsScreen y SportsScreen.

## 2.5.22 — Arranque Ultrarrápido y Cero Crasheos en Chromecast y TV
- Pantalla de bienvenida 100% nativa en Compose para Smart TV y dispositivos modestos, eliminando VideoView/SurfaceView y crasheos por SIGSEGV en libhwui.
- Dalvik Heap ampliado a 512 MB (largeHeap) en el manifiesto, erradicando OutOfMemoryError en Chromecast y hardware modesto.
- Optimización de corrutinas en MainActivity y XuperCatalog, eliminando esperas forzadas de 9 segundos y acelerando la entrada a 600 ms.
- Cero regresiones en VOD, TV en vivo y focus trap.

## 2.0.0 — La versión nativa

Reescritura completa como app Android nativa (Kotlin + Jetpack Compose).

- Interfaz tipo Netflix con la paleta azul de Vision+, en celular y Android TV con la misma app.
- Catálogo de películas de dominio público del Internet Archive, ampliado a 6 filas y 22 títulos
  (nuevas: Terror y suspenso, Cine negro, y «El general» de Buster Keaton).
- TV en vivo real: parser M3U/M3U8, reproducción HLS, listas gratis de iptv-org y listas propias
  (agregar por URL, recargar, quitar), con búsqueda, grupos y favoritos.
- Pre-roll de marca: el video de introducción de Vision+ se reproduce antes de la primera película,
  con botón «Omitir» a los 2 segundos.
- Splash animado propio (anillo + wordmark VISION+) y banner/iconos nuevos para Android TV.
- Pantallas de Plugins, Descargas, Biblioteca y Ajustes se mantienen como muestra, con avisos de
  «próximamente».
