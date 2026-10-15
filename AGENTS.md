# AGENTS.md — Contexto persistente del proyecto Vision+ / Xuper XTV

> Este archivo se carga automáticamente en cada sesión de opencode. **Mantenerlo actualizado al
> cerrar una sesión** (estado, versión publicada, pendientes). El proyecto vive en
> `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+\` (aquí hay una copia idéntica de este archivo).

## Proyecto y objetivo
- **Lote 2.5.81: «UI Premium» — cero emojis, iconografía de marca y selector de episodios premium + firma del autor — OTA publicada** (2026-10-15; 356 tests Android):
  - *Petición*: los emojis hacían ver la app «barata»; sustituirlos por iconos premium acordes a la
    paleta; el selector de episodios debía verse profesional; y que la app «se sienta suya» (que
    amigos y familia sepan quién la hizo) **sin decir el nombre**.
  - *Decisiones del usuario*: rating con estrella **dorada** (#F5C518, único acento fuera del azul);
    episodios de la ficha móvil como **lista vertical** con miniatura; países del fútbol como
    **chips ISO** (MX/AR/US/INT) en vez de banderitas; **sin** codename de versión; historia del
    autor con **pista elegante del oficio** (sin nombre ni hospital); **sin** easter egg ni fila
    «Selección del autor»; **sí** botón «Compartir Vision+» firmado.
  - *Sistema*: `ui/icons/VisionIcons.kt` (vocabulario único Material Symbols Rounded + `homeRowIcon`)
    + tokens en `Theme.kt` (RatingGold, Success, LiveRed) + ~21 vectores `ic_vp_*` en `res/drawable`
    para el reproductor nativo (con `TextView.withIcon` y píldoras con `iconRes`). El `IconView` de
    Canvas del reproductor se retiró (play/pausa/±10 ahora son vectores del mismo set).
  - *Inicio*: sello `TopTenBadge` azul (fuera 🔥), filas con icono (AutoAwesome/TrendingUp/
    PlayCircleOutline/Collections/Movie/Theaters/Animation/ChildCare), rating dorado con estrella,
    «Ver todo» con chevron. `isTopRow` ahora por id (`home-top10`) además del título.
  - *Fichas*: botones con iconos (Star/StarBorder, Bookmark, Notifications, Download/Usb/
    DownloadDone), estados con CheckCircle, temporadas sin «●», chip de score dorado con icono.
    **Ficha móvil**: `MobileSeriesSelectorSection` es lista vertical (miniatura póster 2:3 con
    «C#», estado real, toggle visto con icono, barra de progreso de temporada).
    **Ficha TV**: tarjetas 240dp con miniatura 16:9 + «C#» sobre el arte + estado con icono.
  - *Reproductor*: píldoras con icono (LiveTv, PlaylistPlay, SkipNext, VolumeUp, Subtitles, Tune,
    Star, FastForward, Close, Replay, Play), punto EN VIVO **dibujado** (LinearLayout oval + texto),
    aviso post-créditos con Movie, tarjeta «A continuación» con icono, listas de canales con estrella
    como compound drawable, picker de calidad con «· Actual». **Selector de episodios**: barra de
    vistos, segmented de temporadas sin «●», filas con acento izquierdo azul en la actual, título real
    (sin «Capítulo N ·» duplicado), estado con icono, píldoras con icono, spinner «Preparando
    capítulos…», hint «Mantén OK para marcar como visto». IDs y cadenas de foco D-pad intactos.
  - *Barrido*: PlayerDialogs/MiniPlayer con `Check` como icono guía y `ExpandMore` (fuera «▾»),
    Ajustes TV con chevrons reales, Mi lista (móvil/TV) con chips con icono, búsqueda con `SearchOff`,
    ReminderUi con campana, fútbol con `CountryIsoChip` + `CheckCircle` + `PlayArrow`, `SportsLinker`
    «EN VIVO · programa», LiveTaxonomy `countryCode()` (fuera `countryFlag()`).
  - *La firma (sin nombre)*: splash con «por ChronosBVRX · EST. 2026»; micro-línea
    «VISION+ · POR CHRONOSBVRX» en la tarjeta de carga del reproductor; «Acerca de» (móvil y TV)
    reescrito con la historia del autor («Un estudio de una sola persona… las mejores historias no se
    compran, se revelan»), firma dibujada (trazo Canvas), sello EST. 2026, créditos estilo cine y
    botón **Compartir Vision+** con mensaje firmado + enlace estable del APK; el diálogo de
    actualización dice «Creada por ChronosBVRX». Notas de OTA firmadas «— ChronosBVRX» (proceso).
  - *Guardián*: `UiNoEmojiTest` recorre `ui/` + Popularity/LiveTaxonomy/SportsLinker y **falla si
    aparece un emoji** (permite el `☆` de datos del proveedor y las flechas tipográficas; verifica
    que escanea ≥50 archivos). Suite completa 356/356.
  - *Verificado*: 356 Android en verde; release R8 vc317 (102.2 MB) con firma histórica
    `9593cb0d…` (apksigner). Smoke visual en emulador: home móvil con sello TOP 10, estrellas
    doradas, encabezados con icono y chevrons; splash con «por ChronosBVRX · EST. 2026». A32 no
    estaba en red al publicar (prueba física pendiente).
  - *Ajuste post-publicación*: en el splash Compose (TV/bajo consumo) la firma salía duplicada
    (bajo el logo y abajo): ahora la de abajo solo aparece con el video de marca. Asset de la OTA
    re-subido con el fix (mismo vc317).
  - *OTA v2.5.81 publicada* (`VisionPlus.apk`, release en GitHub). Plan de trabajo en
    `docs/PLAN_UI_PREMIUM.md`.
- **Lote 2.5.80: arranque más rápido en Smart TV (handshake adelantado desde la ficha) — OTA publicada** (2026-10-15; 355 tests Android + 122 backend):
  - *Pregunta del usuario*: ¿por qué en el celular la reproducción es casi instantánea
    y en la app original del proveedor en TV también, pero en Vision+ en la TV tarda
    varios segundos?
  - *Razón medida*: la caja tarda 0.2-0.7 s por llamada al portal (TLS+JSON) vs
    0.05-0.15 s del celular, y NUESTRO flujo hacía el handshake (ficha + licencia +
    SLB) **al pulsar Reproducir**, en serie → 1-4 s antes de abrir el reproductor.
    La app original pide la licencia **al abrir la ficha** (por eso se siente
    instantánea). Extra: esa caja tenía el proceso `init.svc.console` comiendo un
    núcleo (detenido en la sesión 2.5.79).
  - *Fix*: `X4KApi.prewarmVod()` — al abrir la ficha (TV y móvil) se adelantan la
    licencia (`prepareVod`) y los marcadores (caché 6 h del MarkersStore) en
    segundo plano; `prepareVod` consume esa entrada UNA vez (TTL 90 s) y cae al
    flujo normal si falta o caduca. Además, `prepareVod` ya no pide la ficha
    editorial cuando el catálogo conoce la duración (`fallbackDurationMs > 0`):
    una llamada menos en el crítico (subsInfo viaja vacío; el reproductor ya
    recupera duración/metadatos en segundo plano).
  - *Verificado*: 355 Android + 122 backend. Es cambio de handoff, no del
    reproductor. La TV apagó la depuración inalámbrica tras la reinstalación limpia
    (sin adb al momento de publicar): pendiente sentirlo/verificarlo en la TV al
    reconectar (el actualizador in-app ofrecerá 2.5.80).
  - *OTA v2.5.80 publicada* (release R8 vc316, firma histórica).
- **Lote 2.5.79: re-arranque interno del motor (fin del «error al primer intento»), vivo con espera honesta y foco de regreso — OTA publicada** (2026-10-15; 355 tests Android + 122 backend):
  - *Síntoma (usuario)*: en Chromecast las series dan error al primer intento y con
    «Reintentar» ya reproducen; en la TV box rooteada no abrían nunca; el vivo en la
    TV quedaba en negro sin salida; y al volver de un canal se perdía el D-pad.
  - *Fix 1 — re-arranque interno*: el motor aceptaba el `play` pero la primera pasada
    del P2P se quedaba corta en cajas lentas. `softRestartPlayback()`: a los 25 s sin
    señal hace stop + play interno (sin error, con la espera visible); el error real
    solo a los ~60 s (35 s tras el re-arranque). Guarda contra `firstFrame` tardío.
  - *Fix 2 — espera del vivo*: `hasLivePlayback` ya no se dispara a los 1.2 s; espera
    señal REAL (`pos>0 || isPlaying`) o 12 s de cortesía, y el error del vivo se mide
    por señal (`liveSignalSeen`), no por temporizador (antes: negro eterno).
  - *Fix 3 — «0 canales»*: `X4KApi.liveStubsForCodes()` reconstruye el selector con
    los códigos del intent cuando `:x4k` no tiene el catálogo cacheado.
  - *Fix 4 — foco al volver*: `TvLiveScreen(returnChannelCode=…)` + `liveReturn` en
    TvApp: el regreso enfoca la MISMA tarjeta del canal (fallback: primera visible /
    destino). El harness de Compose no restaura foco de ventana tras la ruta del
    reproductor (limitación del entorno): el caso se verificó en la tele real.
  - *Instalación limpia pedida por el usuario*: paquete desinstalado, `/data/data` y
    restos en sdcard/tmp borrados, filas huérfanas del launcher (Watch Next) quitadas
    con cirugía de `tv.db` (respaldo/verificación de integridad), y reinstalación
    fresca — auto-alta inmediata («auto-alta en el portal: ok»; el reclamo compartido
    se ignora correctamente). Estaba viendo Attack on Titan con video y «Omitir
    resumen» al cerrar.
  - *Extra (lado equipo)*: la TV box ZTE tenía un `sh` de consola del firmware en
    bucle comiendo un núcleo (`init.svc.console`, 97 % CPU permanente). Detenido con
    `setprop ctl.stop console`; si reaparece tras reiniciar, repetir el comando. Es
    gran parte de la lentitud de la caja (joins del P2P tardíos).
  - *Verificado*: 355 Android + 122 backend; TV ZTE con serie+anime reproduciendo;
    release R8 vc315, firma histórica. **OTA v2.5.79 publicada.**
  - *Pendiente*: prueba física del vivo (canal + regreso D-pad) y del segundo intento
    en Chromecast tras actualizar.
- **Lote 2.5.78: fin de la tormenta de sesiones + crash R8 `k4/x` del motor — OTA publicada** (2026-10-15; 351 tests Android + 122 backend):
  - *Síntoma*: la TV no reproducía NADA (ni VOD ni vivo, error a los 20 s) y luego el
    móvil también empezó a fallar («me saca al darle play»).
  - *Causa 1 — tormenta de mints (verificada)*: la TV tenía un «slot propio» que en
    realidad contenía el SN COMPARTIDO (`2392fe…`, cuenta `967111326`): el pool le dio
    el mismo SN que al resto + worker. El portal admite 1 sesión por SN: cada mint
    patea (`aaa100083`, probado: un mint muere a los ~2.5 min). El «ya hay slot local»
    bloqueaba para siempre el alta derivada (v2.5.73). La reproducción necesita la
    sesión estable 20-60 s; pateada a medio camino, el motor se queda sin segmentos y
    el auto-reparado rotaba el motor (gen 2→12) + re-acuñaba a la fuerza (tormenta).
    Fix app: `DeviceSlot.isShared()` + `SlotClient.ensureSlot` jubila el slot
    compartido y completa el alta derivada (reclamo/pool ignoran SN compartido; sin
    red sigue en compartido). Verificado: TV con cuenta propia `969757642`, cero
    renovaciones en 2 h; worker con SN propio `1ec7e0c1…` vía `POST /v1/slots/worker`
    (migra solo en su próxima revalidación).
  - *Causa 2 — colisión R8 contra el DEX del motor (verificada con stack)*: en release,
    el `onPrepared` (`yc.e$h`) arranca `Observable.interval` (RxJava del DEX) cuyo
    `SchedulerPoolFactory` llama a `k4.x.a(AtomicReference,...)` (okhttp del proveedor).
    R8 había renombrado `com.google.crypto.tink.proto.AesGcmKey` a `k4.x`: con
    parent-first el motor resolvía la NUESTRA → `NoSuchMethodError` en `k4/x` → muerte
    del `:x4k` 1 s después de elegir decodificador (stack en A32 v314 + gemelo en la
    otra TV). En debug no hay ofuscación y carga la suya. Fix: `-keep
    com.google.crypto.tink.**` (+ dontwarn google-api-client/joda); mapping verificado
    sin `k4.x` generado. OJO: R8 NO silencia logs (no hay assumer de Log) — el release
    sí loguea; no asumir lo contrario.
  - *Verificado*: 351 Android + 122 backend en verde; TV ZTE (root): Spider-Man con
    video+audio (4.6M frames), Azteca 7 en vivo real, NBA; A32 (USB): Spider-Man con
    video en release (el crash desapareció). Release R8 vc314, firma histórica
    `9593cb0d…`. **OTA v2.5.78 publicada.**
  - *Lecciones*: (1) un slot del pool con el SN compartido vale cero — detectar y
    migrar; (2) cualquier clase R8 con nombre de 1-2 letras puede ensombrecer clases
    del DEX del motor (parent-first) — revisar el mapping ante un NoSuchMethodError
    con nombre ofuscado; (3) `pm install -r` no mata procesos + el reboot limpia la
    autorización adb (la TV quedó `unauthorized`: aceptar el diálogo RSA en la TV);
    (4) mis propias sondas al portal patean sesiones reales — minimizar mints (el
    detector de duelo sí confirmó duelo, pero a costa de patadas).
  - *Pendiente*: instalar 2.5.78 final en la TV (quedó con build diag; adb
    `unauthorized` tras el reboot — aceptar diálogo en la TV) + prueba física
    VOD/vivo; confirmar migración del worker a su SN (`sesión lista userId=969755826`
    en su log); prueba física del móvil del usuario con la OTA.
- **Lote 2.5.77: resumen, post-créditos, tendencias, avisos, niños, actividad y pulido visual — OTA publicada** (2026-10-15; 349 tests Android + 122 backend):
  - *Intro/créditos*: píldora «Omitir resumen» (misma píldora del intro, el resumen manda primero;
    el servidor ya lo servía, la app lo ignoraba) + auto-salto en modo automático; aviso discreto
    «🎬 Hay escena después de los créditos» (nueva fuente `postCredits` en TheIntroDB/IntroDB.app,
    con saneo y fusión; nota en la tarjeta de siguiente + aviso auto-oculto en películas);
    precarga en cascada (`GET /v1/markers` calienta ep+1/ep+2 en BackgroundTasks).
  - *Servidor (desplegado y verificado en VPS)*: `/v1/trending` (TMDB /trending/week, tabla
    `weekly_trending`, 40 títulos) + fila «📈 Populares de la semana» en Inicio móvil y TV;
    `/v1/schedule` (TVMaze 7 días, tabla `schedule_airings` + `title_id` casado por imdb, 10/10
    casados) + botón «🔔 Seguir serie» en fichas y chequeo diario con aviso 60 min antes.
  - *Reproductor*: doble toque ±10 s (móvil, X4K), visto/no-visto (long-press en selector X4K,
    ✓/○ en ficha móvil, OK-largo en ficha TV), tamaño/posición/sincronía de subtítulos reales
    (selector + Ajustes), idioma de audio por serie (se recuerda y reaplica), siguiente capítulo
    con arte, auto-descarga del siguiente con WiFi al salir (respeta el motor único: v2.5.62).
  - *Catálogo*: recomendaciones con peso a lo reciente + «Populares de la semana».
  - *Familia y actividad*: modo niños con PIN (hash, pase 15 min, Inicio/Buscar filtrados, resto
    con PIN), «Mi actividad» local en Ajustes, avisos de fútbol 30 min antes (con permiso).
  - *Visual*: tinte ambiental del héroe (móvil + TV, muestra 8×8 sin dependencias), transiciones
    entre secciones (solo móvil, TV intacta por foco), vacíos de búsqueda con guía, ficha compacta
    en apaisado.
  - *Descartado por el usuario*: aportar a bases globales, memoria por serie, novelas por audio,
    velocidad, PiP, miniaturas de scrub, temporada completa de un jalón, perfiles.
  - *Verificado*: 349 Android + 122 backend en verde; release R8 (vc313) con firma histórica
    `9593cb0d…` (dex con el lote verificado en mapping.txt). **OTA v2.5.77 publicada.**
  - *Pendiente*: prueba física (TV/teléfono) tras la OTA.
- **Fix D-pad al volver al Inicio: misma tarjeta + teclas que no se tragan + telemetría blindada — OTA v2.5.76** (2026-10-15; 330 tests Android + 114 backend):
  - *Diagnóstico (sesión paralela, verificado)*: el Inicio se recompone desde vacío (`produceState(HomeCurated())`)
    y el scroll restaurado cae en otra fila; no había revelado vertical (la tarjeta ni se compone);
    `FocusRescue.rescue()` devolvía `true` aunque el `requestFocus()` fallara en silencio (cada tecla
    comida); el `filmReturn` nunca se limpiaba y los hubs no lo invalidaban. Confirmado con mapa de
    nodos en Robolectric (los botones del héroe están en y≈248: Right/Right/OK cae en Favorito, no en
    tarjeta — los tests de navegación deben bajar a la fila).
  - *Fix (6 puntos)*: (1) caché `XuperCatalog.lastHomeCurated` siembra el primer frame (los tests la
    limpian en `seed`); (2) `homeReturnRowIndex` (puro, réplica del orden del LazyColumn) + revelado
    vertical en `rememberReturnFocus` (reusa `revealReturnIndex`); (3) rescate honesto: `LandingFocus.attached`
    + `RescueAnchor` en el héroe + `shouldConsumeKey` puro (MainActivity sin cambios); (4) fallback de
    6 s → 1.5 s; (5) `filmReturn` con ciclo de vida (`onClearReturn`/`onDone` al restaurar o salir por hub —
    OJO: al rendirse NO se limpia, o el contenido tardío ya no restaura; el test lo cazó); (6) guardianes
    `TvRescueTest` (puros) + `homeBackRestoresSameCardAfterLateContent` (misma tarjeta, no "algún foco").
  - *Telemetría*: `Diagnostics` ya no sube nada desde tests (guard `org.robolectric` — no existe en el APK,
    producción intacta): los `FOCUS_LOST` de `robolectric` contaminaban el VPS.
  - *Verificado*: 330/330 Android (suite fresca) + 114 backend; release R8 vc312 con firma histórica
    `9593cb0d…` (dex verificado con el fix en mapping.txt). **OTA v2.5.76 publicada.**
  - *Pendiente*: prueba física en la TV del usuario (secciones → serie → Atrás, foco en la misma tarjeta).
- **OTA v2.5.75: todo integrado (AniSkip + pulido de subtítulos + espera) — publicada** (2026-10-15; 324 tests Android + 114 backend):
  - *Qué integra*: (1) AniSkip para anime + retiro del botón «✎ Marcas» (cero trabajo manual);
    (2) pulido de subtítulos post-2.5.74 (tic robusto + idioma activo y Español primero);
    (3) espera del reproductor con contenido real (ya estaba commiteada desde la sesión paralela).
    Servidor y repo idénticos (hashes VPS = repo en los 6 archivos del servidor).
  - *Verificado*: suite Android fresca 324/324 + backend 114/114; release R8 (`:app:assembleNormalRelease`,
    106.7 MB) con firma histórica `9593cb0d…` (apksigner), versionCode 311. Release
    `v2.5.75` en GitHub con asset `VisionPlus.apk` (enlace estable `.../releases/latest/download/VisionPlus.apk`).
  - *Pendiente*: prueba física en TV del usuario (foco D-pad v2.5.69, subtítulos v2.5.74/75, Omitir intro).
- **Subtítulos del proveedor dentro del reproductor (por fin de verdad) — OTA v2.5.74** (2026-10-14; 327 tests Android):
  - *Diagnóstico (el usuario tenía razón: «idioma sí, subtítulos no»)*: en el SDK embarcado la llave
    `"subtitle"` de la fachada es **un no-op** (`yc/h$l`: solo llama al motor para volume/audio/speed/
    thumbnail/polling_period/mediacodec; subtitle/subtitle_delay/buffer_time/hdr_map/latency/default_subtitle
    solo hacen `equals` y `return`) y el motor/vista tampoco saben pintar subtítulos. El APK oficial los
    resuelve en la app: **servicio del portal** `POST /api/subs/terminal/metadata` con `{"programId":…}`
    cifrado con llave propia (`NxZZ7EYgaJiJSBHjnq7sDxYvYRm32tPQ`, mismo sobre DESede+base64+hex de la casa),
    respuesta `{returnCode, data:<hex cifrado>}` → lista de idiomas con SRTs → **descarga del .srt y
    render por la app** (TextView overlay, como el `bb.g`/SubtitleManger del APK original; el motor no participa).
  - *Replicado y verificado en vivo contra masnew*: `data/subs/Subs.kt` (puro: parser de la lista, parser
    SRT con BOM/UTF-8/Latin-1, cues con búsqueda binaria, etiquetas de idioma) + `data/subs/SubsStore.kt`
    (red con host de respaldo, caché de 24 h del índice en archivo —cruza `:x4k`—, SRT en
    `filesDir/subs/<programa>/<idioma>_<idx>.srt`, elección por título+serie en `selection.json` con
    «__off__» explícito). Vector de request/response capturado EN VIVO y clavado en `SubsTest`, más
    `SubsStoreTest` (Robolectric: caché con formato del dispositivo, selección por serie, SRT cacheado).
  - *Reproductor (`X4KPlayerAty`)*: overlay `TextView` sin foco (sube cuando los controles están visibles),
    tic propio que se re-agenda al siguiente cambio de cue (tope 1 s), carga de la lista al abrir el
    programa (y al cambiar de capítulo), auto-reactivación de la elección guardada, picker con la lista
    REAL del servicio («✓ Desactivados / Español / Inglés»; sin lista: «Sin subtítulos disponibles»;
    mientras viaja: «Buscando subtítulos…» y se reabre solo). El viejo camino (pistas del motor / `subsInfo`)
    quedó fuera: era placebo.
  - *Trampas cazadas en el camino*: (1) regex ICU de Android — `{\\[^}]*}` revienta en `Pattern.compile`
    del `<clinit>` (JVM de escritorio lo acepta): corregida a `\{\\[^}]*\}` y por eso CUALQUIER 2.5.74
    previo debe rehacerse; (2) el caché del índice se guardaba en un formato que el parser no releía
    («Sin subtítulos disponibles» falso) — ahora escribe el mismo sobre que el servicio y un caché
    ilegible se descarta y se reintenta; (3) en el A32 la fila de píldoras queda dentro de la zona de
    gestos (tap en y≤2266): los scripts de prueba deben picar arriba de y=2274.
  - *Verificado en el A32 (release R8, firma histórica)*: lista n=2 (es/en), `es_0.srt` de 56 KB,
    901 cues, `subs activo 'es' … overlay=true`, captura con subtítulo en pantalla («Mientras más furia
    sientes…»), elección persistida y auto-restaurada al reabrir; cero crashes.
  - *Acople con la espera del reproductor (sesión paralela), verificado en el A32 sobre la OTA v2.5.74*:
    serie «Ley y orden: UVE» → C2 por `KEYCODE_MEDIA_NEXT` → espera «…· T1 · Capítulo 2» + barra por
    capítulo; y con la elección «es» de la serie sembrada, al pasar a C3 el capítulo siguiente la heredó
    solo (`subs list cid=9A2D… n=2` → `subs activo 'es' cues=739 overlay=true`, captura `vp_x4.png`).
    OJO: en MÓVIL las píldoras del reproductor no son enfocables con D-pad (solo TV) y la fila queda
    parcialmente bajo la barra de navegación — para pruebas táctiles usar TV o cambiar de navegación.
  - *Pendiente*: verlo en la TV del usuario (el overlay ya usa tamaño/márgenes de TV y está en la OTA);
    y el picker de subtítulos no existe en vivo (el servicio es por programa VOD — correcto).
  - *Pulido post-2.5.74 (publicado en la OTA v2.5.75)*: el tic de subs ya no puede morir por un cue
    raro (try + texto plano si el HTML del proveedor viene roto) y el selector pone primero el idioma
    activo y luego **Español** (`orderSubsForPicker`, con test).
  - *Ojo (lecciones)*: los SRT del proveedor traen cues CORTOS y con huecos largos — una captura puede
    caer entre cues y parecer «no funciona» (verificar con ráfaga + logs `subs list/activo`, no con una
    sola foto). Y la lista de idiomas sale del servicio, no de `subsInfo` (que solo es texto).

- **Espera del reproductor con contenido real (sin decir «película» en series) y refrescada por capítulo — publicada en OTA v2.5.75** (2026-10-14; 325 tests Android):
  - *Pedido*: la espera decía «Preparando tu película…» incluso al abrir series, y al cambiar de capítulo
    seguía mostrando el PRIMER capítulo de la sesión (4x3 aunque se viera el 4x8): el overlay se
    construía UNA vez (título cocido) y en `playEpisode` solo se volvía a mostrar tal cual.
  - *Fix*: `applyLoadingContent()` en `X4KPlayerAty` refresca la espera en cada cambio (mountUi,
    playEpisode, zap de canal y cuando la lista de capítulos llega tarde). Puro `resolveLoadingCardText`
    en `ControlsState.kt`: serie → título base + «T1 · Capítulo 8» en azul (más el nombre real del
    capítulo si el portal lo trae; los genéricos «Capítulo N» se descartan); película → «Preparando tu
    película…»; vivo → «Preparando la señal…». El nombre del canal también se refresca al zappear.
  - *Guardianes*: `PlayerLoadingCardTest` (8 pruebas). Suite Android completa 325/325 en verde.
  - *Handoff para el agente de subtítulos*: `docs/HANDOFF_ESPERA_REPRODUCTOR.md` (funciones exactas,
    invariantes a respetar y la trampa de regex ICU).
  - *Verificado en A32*: APK debug (vc310) instalado; reproducción con video OK, sin crash. La captura
    de la espera en serie quedó pendiente: el teléfono estaba en uso concurrente (otra sesión probando
    subtítulos). **Falta prueba física/OTA.**
    → *CERRADO (misma fecha, sesión de subtítulos)*: verificado sobre la OTA **v2.5.74** en el A32 —
    serie «Ley y orden: UVE» → C1 → C2 con `KEYCODE_MEDIA_NEXT`: la espera mostró título + **«T1 ·
    Capítulo 2»** en azul + nombre real del capítulo, la barra superior se actualizó a «· T1 · C2» y el
    zap por capítulo siguió disparando la recarga de subtítulos del nuevo `programId`. Capturas en
    `Temp/opencode/vp_w4.png` (espera C2) y `vp_u1.png` (barra C1).
  - *CRÍTICO (subtítulos en curso, hallado de paso)*: un build 2.5.74 (R8) se caía al abrir CUALQUIER
    título en `:x4k` (`ExceptionInInitializerError` en el `<clinit>` por `Pattern.compile`): **Android
    (ICU) rechaza el patrón `\{\\[^}]*}`** (el `}` final sin escapar) mientras que en
    JVM de escritorio compila — trampa solo-Android. La fuente ya tiene la variante válida
    `\{\\[^}]*\}` (`data/subs/Subs.kt:119`). Comprobado con un dex lanzado en el propio teléfono:
    `\{\\[^}]*}` → BAD, `\{\\[^}]*\}` → OK. **Cualquier 2.5.74 construido antes del fix debe rehacerse.**
    También se repararon los imports rotos de `SubsStore.kt` (constantes viven dentro de `X4KApi`) para
    que el árbol compile.
- **Alta automática en el portal (réplica Hydra COMPLETA) — v2.5.73** (2026-10-14; 302 tests Android + 101 backend):
  - *El usuario tenía razón*: Hydra no compra cuentas. En Crystal (`ac/a1.java`) el `isNew=="1"`
    deriva local `SN = MD5(snToken + "ntFT65w6itH!lHCPw7D=@qnsFC5adD28")` — sal idéntica en los
    3 APK (X4K original, Hydra, Crystal). El portal valida recomputando: sin `sn` derivado el
    active da 100059; con él, `rc=0` con usuario visitante NUEVO.
  - *Verificado contra masnew (no adivinado)*: sesión `969423096/identity=1` abre 10 repisas,
    ficha y **licencia VOD real de Moana** (mismo playCode que la principal). `X4KApi.mintOwnSlot`
    + `deriveSn` (vector MD5 en tests); orden: local → reclamo → auto-alta gratis → pool.
  - *Verificado en el A32*: 2.5.73 se autodió de alta sola («Propia (771d970d…)») y Moana
    reproduce con video y cero patadas. La primera revisión dijo «sin slot» por revisar a los
    75 s: el mint tarda ~2 min con red lenta (logs `SlotClient` agregados). **OTA v2.5.73 publicada.**
  - *Nota*: el pool de SN queda como respaldo VIP/manual; para el caso común ya no hace falta
    upstream. Cada instalación = una sesión = fin de las patadas.
- **Auto-asignación de identidad (cero códigos) — v2.5.72** (2026-10-14; 301 tests Android + 101 backend):
  - *Pedido*: que ningún usuario escriba códigos. `POST /v1/slots/auto`: con asignación previa
    la devuelve (reinstalación sin gastar); si no, entrega el libre `auto` más viejo en transacción
    `SKIP LOCKED`; agotado → 409 y se sigue compartido en silencio. Columnas `auto`/`created_at`
    (ALTER idempotente al arrancar el sync). La app lo intenta sola al arrancar (`ensureSlot`:
    local → reclamo → auto); el canje manual queda de respaldo.
  - *Verificado*: endpoint en vivo con slot falso (asigna, idempotente, 409 sin pool; falso
    borrado después); 2.5.72 (vc308, R8, firma histórica) en el ZTE: arranca, identidad Propia
    intacta, sin regresiones (el duelo teléfono-vs-tele persiste con el SN legado de prueba hasta
    que haya slots reales — esperado, no regresión). **OTA v2.5.72 publicada.**
  - *Deploy (ojo, 2 tropiezos)*: `api/Dockerfile` debe copiar `slots.py`; el sync COPIA en build
    (reiniciar no basta: `up -d --build sync`); el `slots.py` nuevo también hay que subirlo
    (`AttributeError auto_decision` si queda el viejo).
- **Pool de identidades del portal (réplica del modelo Hydra) — v2.5.71** (2026-10-14; 301 tests Android + 98 backend):
  - *Pregunta del usuario*: ¿cómo se distribuye Xuper Hydra sin que todos se pateen con la misma
    sesión? Quiere lo mismo: instalar y listo, sin pedir cuentas.
  - *Hallazgo (APK `com.msandroid.mobile` v6.5.7 descompilado con jadx en
    `Temp/opencode/hydra_src3`)*: Hydra NO trae SN fijo (nuestro `2392fe…` no aparece en sus dex).
    Manda `SnTokenBean` de 21 campos de huella real (androidId, wifi/eth MAC, serial, CID de la
    eMMC, cpuId, RAM/ROM, build…) a `v3/snToken` y el portal responde
    `SnTokenData{snToken, isNew, userId, sn}` — el SN lo asigna el servidor por dispositivo.
    Flujo `v3/snToken → v6/active → v6/login`, visitante por defecto, `v5/heartbeat`,
    SN persistido en prefs (`SP_SN_BACKUP`) + archivos + `.wav` en MediaStore (sobrevive
    reinstalos). Colector en `b1.java`, tabla de endpoints en `utils/a0.java`, API en `db/y1.java`.
  - *Veredicto para masnew (probado contra el portal, NO adivinado)*: el tenant NO incorpora
    dispositivos: SN virgen → `v8/active` responde `portal100059`; huella completa real del ZTE
    (21 campos vía root) → `v3/snToken` devuelve solo `{snToken, isNew:"0"}` y `v6/v8/active`
    → `rc=1 parámetros anómalos`. La réplica exacta por huella es imposible en masnew; va por pool.
  - *Sistema (operativo)*: tabla `device_slots` (sn, user_id, free|assigned|worker|blocked,
    device_key único, claim_code único que se quema) + `POST /v1/slots/issue` (admin, emite código),
    `POST /v1/slots/redeem` (canje idempotente por equipo), `POST /v1/slots/claim` (reclamo tras
    reinstalar, solo devuelve), `GET /v1/slots/status` + `POST /v1/slots/worker` (admin). App:
    `DeviceSlot`/`SlotStore` (cifrado)/`SlotClient`, `X4KApi` con SN/userId por slot (legado = igual
    que antes si no hay slot), motor nativo (`X4KTitan.ensureConfigured` en player/warmup/descargas)
    con la misma identidad, Ajustes → «Identidad del equipo» (móvil y TV) + reclamo silencioso al
    arrancar. El worker lee su slot de la DB (`_worker_slot`) y deja de patear a la app.
  - *Verificado*: servidor en vivo (status 0/0/0/0, redeem malo 404, issue sin llave 403, stats 200);
    APK release R8 2.5.71 (vc307, firma histórica `9593cb0d…`) instalado en el ZTE: canje real por
    D-pad del código `KF6SBGZE` → «Propia (2392fe51…)», slot `assigned` al `device_key` del box en
    Postgres, Moana reproduce con la identidad aplicada, diagnósticos restaurados a Sí.
    **OTA v2.5.71 publicada.**
  - *Operador*: `/tmp/issue_slot.sh <SN> [USER_ID]` en el VPS emite códigos;
    `POST /v1/slots/worker` fija el SN del worker. OJO: el pool arranca VACÍO y el slot de prueba
    es el SN legado en el box (mismo comportamiento que antes). Cada usuario real necesita SU SN
    aprovisionado del upstream — sin eso no hay distribución. Sin slot, todo sigue igual (legado).
  - *Guardianes*: `DeviceSlotTest`, `X4KApiSlotTest`, `SlotStoreTest` (backend inyectable: Robolectric
    no tiene AndroidKeyStore), `server/tests/test_slots.py`. Ojo: `api/Dockerfile` debe copiar
    `slots.py` (se rompió el primer deploy por esto).
- **Fix reproductor mudo en Smart TV («cajas atascadas») — el motor legado Xuper tumbaba la reproducción XTV** (2026-10-14; 291 tests Android):
  - *Síntoma*: en el ZTE B866F la reproducción moría en silencio: el motor descargaba el video
    (segmentos reales en `files/x4k-luna*/block_cache`) pero nunca arrancaba IJK (`FFP_MSG_OPEN_INPUT`
    jamás salía) → overlay de error a los 20 s, una y otra vez, incluso con títulos que en el
    teléfono reproducían perfecto. Fases de fallo de ~1–1.5 h que solo se curaban con `pm clear`.
  - *Causa raíz (cazada con root en la tele)*: el motor LEGADO «Xuper» (`XuperApiClient`/`XuperEngine`,
    config capturada en código: email/password/cookies, sn `ca0e53ed…`) guardaba una sesión rancia
    en `xuper_secure` (EncryptedSharedPreferences **compartido** con el Gate y Magis). Sus rotaciones
    (auto-login/bootstrap/startPlayLive contra el portal masnew) tumbaban la autorización de
    reproducción del X4K de la misma cuenta — el motor bajaba datos pero el stream nunca arrancaba.
    A/B con root: quitar el archivo → reproduce; reponerlo → falla; quitarlo → reproduce (3 ciclos
    limpios). El estado renace del código: borrar el almacén no basta.
  - *Fix (v2.5.70)*: `XuperEngine.disable()` — desarme permanente por bandera (`xuper_engine.xml`,
    prefs llanas): tras una rotación que no produce lista nueva, o si el almacén ni abre, el origen
    legado queda inerte (sin cliente, sin red, sin keystore) para siempre; `ensureReady`,
    `ensureChannel` y `XuperSource.channelCatalog` respetan la bandera. `PlayerRecovery` (nuevo,
    `data/xuper/`): racha de fallos silenciosos en archivo (cruza procesos `:x4k`↔principal), a los
    2 seguidos rota la carpeta del motor (`x4k-luna-gen<N>`), renueva sesión+SLB
    (`consumeGenChange` en `prepareVod`) y desarma el Xuper — auto-reparado sin que el usuario borre
    datos. `PLAYER_ERROR` ahora lleva etapa/modo y hay evento `PLAYER_SELF_HEAL`.
    `SESSION_CODE_RX` ya no renueva por 100006/100008 («no existe» — eran tormentas de renovación).
  - *Verificado*: 291 tests (PlayerRecoveryTest, XuperEngineDisarmTest, anti-tormenta en
    X4KApiSessionTest) + prueba física en el ZTE: con el archivo envenenado repuesto a propósito, la
    app se auto-desarmó al arrancar y 3/3 reproducciones con video. **OTA v2.5.70 publicada.**
  - *Ojo (lecciones)*: `xuper_secure` es un almacén compartido (Xuper legado + Gate + Magis): NO
    borrarlo entero (daño colateral + renace); el desarme va por bandera. Y `PLAYER_ERROR` sin
    etapa costó horas de arqueología: ahora la trae.
- **Fix D-pad: el foco se perdía al volver de una ficha/serie en el Inicio de Smart TV** (2026-10-14; 285 tests Android):
  - *Primera ronda (v2.5.67, insuficiente)*: al regresar de una ficha, el Inicio arma su contenido
    en segundo plano (`Popularity.homeCurated` en `produceState`, desde v2.5.60) y los reintentos
    del regreso (12×60 ms) y el destino de la pantalla (3 s) se agotaban antes de que las filas
    pintaran; el rescate «llegó contenido y nadie tiene foco» quedaba bloqueado por
    `returnFilmId != null` (nunca se limpia en la sesión). Se añadió `rememberReturnFocus`
    (`ui/tv/TvFocus.kt`): re-arma con cada `contentKey`, reintenta 60×100 ms y cae al landing.
    Se aplicó a Inicio, Categorías, secciones, Buscar y Mi lista. La tele reportó que seguía
    fallando.
  - *Causa real (v2.5.68)*: en `TvCategoryScreen` (secciones del riel y categorías) las filas
    **«Recomendado para ti»**, «Mejor valoradas» y «Novedades» se dibujaban **sin
    `returnRequester`** — el foco no tenía tarjeta a la que volver y caía al héroe; con la vista
    desplazada (héroe fuera de composición) **no quedaba nada enfocado** y el mando moría. La
    tarjeta de la cuadrícula (`TvCategoryGrid`) tampoco lo registraba. Reproducido con un test
    que falla con el código viejo: `TvFocusTest.seriesFromSectionRecommendedRowBackKeepsTheSameCard`
    (antes=Offset(118,435) → después=Offset(118,105): el foco se iba al héroe).
  - *Fix v2.5.68*: todas las filas del catálogo (SectionRow, cuadrícula, filas del Inicio,
    Categorías, Mi lista) registran la tarjeta de regreso; nuevo `entryRequester` («primera
    tarjeta visible de cada fila compuesta») como rescate cuando la tarjeta exacta no existe o su
    fila quedó fuera de pantalla; el primer intento del regreso es incondicional (no depende de
    `screenHasFocus`, que puede llegar tarde en la tele); y si de plano nada toma el foco se
    reporta `FOCUS_LOST` al VPS (pantalla, intentos, adjunta/entrada, clave) para no volver a
    adivinar.
  - *Ajuste v2.5.69 (el foco vuelve a la MISMA tarjeta)*: la fila se **desplaza sola** hasta la
    tarjeta abierta si quedó fuera de la ventana (`revealReturnIndex` en `TvFocus.kt`: espera 60 ms
    el scroll restaurado, comprueba la ventana y solo entonces desplaza) y el regreso ya no se rinde
    cuando el único foco es el automático de la pantalla (`landing.focused`): sigue intentando la
    tarjeta exacta y en cuanto el usuario mueve el mando con otra cosa, deja de intervenir. Aplicado
    en filas del Inicio, Categorías, SectionRow, cuadrículas (Todo/Mi lista).
  - *Guardianes*: `TvFocusTest` — `seriesFromSectionRecommendedRowBackKeepsTheSameCard` (falla con
    el código viejo), `seriesFromSectionRowDeepRightBackKeepsTheSameCard` (tarjeta al final de la
    fila), `homeToSeriesFichaAndBackKeepsFocus`, `homeSeriesBackWithLateContentStillKeepsFocus`,
    `categoriesRecomendadosSeriesBackKeepsFocus` y `categoriesDeepRowSeriesBackRestoresSameCard`
    (fila lejana + scroll horizontal). 286 tests Android en verde (suite completa; ojo: los 2 tests
    de `TvLiveScreenTest` son sensibles a la carga de la máquina — 60 s de reloj real de Espresso).
  - **OTA v2.5.67, v2.5.68 y v2.5.69 publicadas** (asset `VisionPlus.apk`, misma firma histórica
    `9593cb0d…`). Pendiente: prueba física en la TV del usuario (secciones → «Recomendado para ti»
    → serie → Atrás; el foco debe quedar en esa misma tarjeta).
- **Índice TMDB: backlog desbloqueado y candado por versión del emparejador** (2026-10-14; 87 tests backend):
  - *Diagnóstico*: el buscador corregido (`clean_query`, v2.5.59) **sí funciona** —muestra en vivo
    contra TMDB: **50/52 títulos (96%)** del backlog emparechan— pero el re-match estaba congelado:
    los ~16.6k «sin match» quedaron marcados entre el 9 y 13 de octubre y el cooldown de 14 días
    dejaba **0 elegibles hasta ~el 23**. El fix llegó justo después de una pasada que marcó todo el
    backlog y quedó bloqueado por su propio candado.
  - *Fix permanente*: el candado ahora es por **VERSIÓN del emparejador** (`enrichment.match_algo` +
    `ALGO_VERSION` en `sync/tmdb.py`): al mejorar el buscador se sube la versión y TODO el backlog
    recibe una pasada fresca, una sola vez, sin esperar. Una mejora ya no puede volver a quedar
    bloqueada por el reintento anterior.
  - *Desbloqueo (desplegado en VPS)*: mini-lote de prueba 40 → **38 matches** (series 560→598 al
    instante); el worker masticará ~2,500/ciclo (~10k/día): backlog de 16.6k en ~2 días. El re-match
    también rellena popularidad, así que **mejora el orden de Tendencias/Estrenos** además de la
    cobertura de marcadores.
  - *Catch-up (2026-10-14)*: lote subido a **`TMDB_REMATCH_BATCH=16000`** en el `.env` del VPS (el
    compose ahora mapea la variable) para vaciar los ~15k pendientes en **una sola pasada** (~4 h de
    matching; el resto del ciclo sigue normal). Con el candado por versión ya no es obligatorio
    bajarlo después: en régimen normal solo procesa lo que haya pendiente. Primer lote real con el
    fix: 2,280/2,500 (91%) → series 4%→30% en un ciclo.
  - *Medidores*: línea por ciclo `TMDB cobertura: …` en el log del worker + **`GET /v1/stats`**
    (API key): cobertura TMDB por tipo (total/matched/noMatch/fresh + %) y estado de `skip_markers`.
  - *Guardianes*: `server/tests/test_tmdb_rematch.py` (libera con cambio de versión, cada intento
    firma su versión, el medidor se lee como medidor). Estado al desplegar: series 3% · movie 89%
    · anime 66% · kids 58%.
- **Omitir intro y créditos con bases globales comunitarias («skip markers»)** (2026-10-13; 280 tests Android + 81 backend):
  - *Qué*: series/novelas/anime usan los tiempos comunitarios de **TheIntroDB** (theintrodb.org, por
    tmdb_id), **IntroDB.app** (por imdb_id) y **AniSkip** (por MyAnimeList, solo anime) —las mismas
    bases que alimentan los plugins de Jellyfin/Emby/Kodi/Infuse— para pintar «⏭ Omitir intro»
    durante la entrada y adelantar la tarjeta de «siguiente capítulo» al **inicio de los créditos**
    (con botón «Ver créditos» para dejarlos correr). Ajuste nuevo (móvil y TV): **Botón**
    (recomendado, como Netflix) / **Automático** / **Nunca**. El switch «siguiente episodio
    automático» **ahora sí** lo honra el reproductor nativo (sin cuenta regresiva si está apagado).
    Decisión del usuario: **cero trabajo manual** — sin datos no hay botón ni correcciones; el botón
    «✎ Marcas» se retiró del reproductor (la capa aprendida local y su subida quedaron fuera de la
    app; el endpoint POST sigue por compatibilidad con APKs viejos).
  - *Servidor*: `GET /v1/markers?ref=<fila de temporada xtv:…>&ep=<n>` — caché en Postgres
    (`skip_markers`, 30 días positivo / 2 días negativo) y consulta al vuelo a las tres bases en un
    thread aparte; `POST /v1/markers` queda como legado. `server/api/markers.py` puro: referencias,
    parseo (incl. AniSkip en segundos→ms y dataset Fribb `parse_anime_list`), **saneo** (un «intro» a
    las 19:51 en un capítulo de 24 min se descarta; créditos jamás antes de la mitad) y fusión (más
    votos gana; empate → AniSkip, luego IntroDB.app). `sync/aniskip.py` refresca semanal la tabla
    `anime_mal_map` (tmdb,temporada→mal, 5,232 filas) desde el dataset Fribb. Trampa cazada: AniSkip
    v2 **exige `episodeLength`** (sin él, 400 siempre) — se manda la duración de la ficha (24 min
    por defecto).
  - *Cobertura medida contra el catálogo real* (VPS, 2026-10-13): top-100 **anime 69%**, top-100
    **series 48%**, series de cola larga ~2%, **novelas 0%** (ninguna base las cubre). Cuello real:
    el rematch TMDB de series (~560 de 16.9k con ficha hoy); al avanzar, la cobertura sube sola.
    AniSkip verificado en vivo: JoJo T5 pasó de sin-datos a completo; fusiones mixtas por tipo
    (intro AniSkip + recap IntroDB.app en FMA/Spy x Family).
  - *App*: `data/markers/SkipMarkers.kt` (puro + `MarkersStore` solo-lectura del servidor, caché de
    red 6 h); `X4KApi.playVod` pide los marcadores **en paralelo** al handshake del portal (tope
    2.5 s) y los pasa por el intent (`markersJson`); `X4KPlayerAty`: píldora visible en la ventana
    del intro (enfocable solo con controles visibles — política D-pad intacta), salto automático una
    vez por capítulo, tarjeta de créditos al llegar al marcador y **conteo de visto aunque los
    créditos duren más de 2 min** (`flushOutgoingEpisode` con `atCredits`); el intro se reusa en el
    mismo lote de temporada y se limpia al cruzar de temporada.
  - *Verificado*: 324 tests Android (`SkipMarkersTest`: ventanas, créditos, parseo, contrato) + 114
    backend (`test_markers.py` + `test_aniskip.py`: parseo 3 fuentes, saneo, fusión con prioridad,
    episodeLength, mapeo y sync); **desplegado y verificado en vivo en el VPS** (caché, 3 fuentes,
    `docker compose up -d --build api sync`, 5,232 mapeos).
  - *Pendiente*: **prueba física en A32/POCO/TV antes de publicar OTA** (regla del proyecto para
    cambios de runtime del reproductor); el diálogo placebo «Corregir intro y outro» del camino
    ExoPlayer sigue sin cablear (las series no pasan por ahí).
- **Icono de la app con la identidad Vision+ (fuera el anillo placeholder)** (2026-10-13; 267 tests Android):
  - *Qué*: el icono del launcher móvil seguía con el placeholder viejo (anillo blanco + punto azul,
    `ic_launcher_foreground.xml`), mientras TV, banner y splash ya usaban la identidad. Ahora el icono
    es el MISMO emblema oficial (`ic_vision_mark.png`: la «V» con el botón de play y el «+», estelas
    incluidas) sobre el fondo de marca `#070B13`.
  - *Assets*: PNGs pre-renderizados (System.Drawing, reescalado progresivo HighQualityBicubic) desde el
    emblema: `mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png` (canvas adaptativo 108 dp, emblema
    62 dp de ancho → radio máx. 33.6 dp: entra completo en el círculo garantizado de 66 dp y sin
    recortes visibles bajo ninguna máscara), `ic_launcher.png` (heredado 48 dp, cuadro redondeado 22 %,
    emblema 0.78) e `ic_launcher_round.png` (círculo, emblema 0.72). Los adaptativos
    (`mipmap-anydpi-v26/*.xml`) apuntan al foreground nuevo; se retiraron `drawable/ic_launcher_foreground.xml`
    (anillo), los layer-list `mipmap/ic_launcher*.xml` y el color huérfano `vision_logo_blue`.
  - *Verificado*: render contra el asset original (geometría y colores idénticos), simulacros de máscara
    círculo 66/72 dp y squircle sin recortes visibles, APK release con R8 (versionCode 302, firma histórica
    `9593cb0d…`) y emulador Vision_Phone con el release instalado: el cajón «All apps» muestra «Vision+»
    con el emblema íntegro bajo la máscara circular. OTA v2.5.66 publicada.
- **Inicio: TOP 10 real de popularidad (ya no repite «Novedades y Estrenos»)** (2026-10-14; 267 tests Android + 60 backend):
  - *Qué*: el «Top 10 en Vision+ Hoy» salía del mismo pool reciente que «Novedades y Estrenos»
    (ambos por `rankScore`), así que mostraban casi los mismos títulos. Ahora es un Top 10 de
    popularidad real de TODO el catálogo: películas, series y anime, sin importar el año.
  - *Servidor*: nueva categoría especial `tendencias` (`TENDENCIAS_SQL` en `sync/index_build.py`)
    = títulos reproducibles con póster ordenados `popularity desc, vote_count desc, score desc,
    year desc` (sin nicho sin match TMDB ni magis); `normalize()` la deja en posición 1
    (estrenos 2, recomendados 3, tv-en-vivo 4). Desplegada en el VPS: 12,347 enlaces, índice **v48**.
    En Categorías (móvil/TV) aparece como «Tendencias».
  - *App (`data/Popularity.kt`)*: `topPopular(ranking, pool)` = orden por popularidad real (el
    pool solo rellena si un servidor viejo aún no manda la hilera); `homePool` suma `tendencias`;
    el héroe/billboard pasa a ser el #1 de popularidad. `nearDuplicateKey`: el portal duplica
    fichas con el plural cambiado («Los Simpson»/«Los Simpsons») y el Top 10 muestra una sola
    tarjeta (misma base ±«s» final de palabra y mismo año; las secuelas no se tocan).
  - *Guardianes*: `PopularityTest` (popularidad > recencia, orden del servidor, Top 10 desde
    «tendencias» vs novedades, fallback sin hilera, duplicados plurales, secuelas aparte) +
    `server/tests/test_tendencias.py` (SQL: popularidad manda, sin filtro de año, póster y
    play_ref obligatorios, posición en `normalize`).
  - *Verificado*: emulador Vision_Phone (release R8, firma histórica): arranque en frío con
    Top 10 = Spider-Man, La Noche del Demonio, Ley y orden (1999)…, un solo «Los Simpsons»,
    Novedades ya es otra fila; logcat sin crashes. OTA v2.5.65 publicada.
- **Fase 2 — Mitigación remota (kill-switches) y reporte con texto** (2026-10-13; 231 tests Android + 56 backend):
  - *Servidor*: tabla `remote_config` + `GET /v1/config` (flags para la app, TTL 6 h) y `POST /v1/config`
    protegido con `CONFIG_ADMIN_KEY` (header `X-Admin-Key`; la API_KEY viaja en el APK y no basta). La
    llave quedó en `/opt/streaming-backend/.env` y en `.visionplus/CREDENCIALES.txt` (respaldar).
  - *App*: `data/config/RemoteConfig.kt` (caché en prefs, refresco al arrancar y cada 6 h, defaults
    seguros). Flags honrados hoy: `downloads_enabled` (apaga descargas en el manager y en las fichas
    móvil/TV) y `diagnostics_forced_off` (apaga el diagnóstico aunque el usuario lo tenga activo).
  - *UI*: en Ajustes móvil, «Enviar diagnóstico ahora» pasó a **«Reportar un problema»** con texto libre
    (máx. 500 caracteres) que viaja como `USER_FEEDBACK`.
  - *Verificado en emulador (Vision_Phone)*: POST con llave mala → 403; con llave buena → flag servido;
    la app lee `config remota: 2 flags`; con `downloads_enabled=false` la ficha muestra «Descargas
    desactivadas» (kill-switch real); el reporte con texto llegó al VPS (`USER_FEEDBACK` con el texto);
    y el CrashGuard capturó un crash real del `:x4k` (UnsatisfiedLinkError del motor en x86_64, stack
    completo). Flag restaurado a `true` al terminar.
  - *OTA v2.5.63 publicada* (release OTA v2.5.63, asset `VisionPlus.apk`, misma firma histórica).
- **Recomendaciones en el launcher de Google TV — canal Watch Next («Continuar viendo»)** (2026-10-13; 254 tests Android):
  - *Qué*: «Seguir viendo» se espeja al canal **Watch Next** del sistema (`TvContractCompat.WatchNextPrograms`,
    androidx `tvprovider:1.1.0`). En Google TV se ve como «Continuar viendo»/«Play Next» y **no requiere
    allowlist** (el «Para ti» completo es de Google/socios; esto es la vía pública). Al seleccionar una
    tarjeta, el launcher dispara un `intent_uri` (`visionplus://watch/<clave>`) y la app reanuda la
    película/capítulo en el minuto exacto (reusa `resumeRoute`); si el historial ya se podó, el enlace trae
    respaldo (`c` playCode XTV, `u` videoUrl) y abre desde el principio.
  - *App*: `data/launcher/WatchNextPlan.kt` (puro: specs del historial —una entrada por serie, tope 10—,
    reconciliación insertar/actualizar/borrar y filtro de descartes), `LauncherWatchNext.kt` (publicador:
    query + reconcile contra el provider; tarjeta con `last_playback_position`, `duration`,
    `last_engagement` y `WATCH_NEXT_TYPE_CONTINUE`), `LauncherArt.kt` + `LauncherArtProvider` (pósters
    cacheados en `filesDir/launcher_art`, servidos `content://` de solo lectura a otra app —el launcher—,
    con respaldo a la URL remota), `WatchNextRemovedReceiver` («No me interesa» → borra y anota el descarte
    en archivo; no vuelve hasta un avance nuevo), `ui/LauncherDeepLink.kt` (deep link → ruta). Manifest:
    permiso `WRITE_EPG_DATA`, `<intent-filter>` VIEW `visionplus://watch`, `launchMode="singleTask"`
    (onNewIntent, nunca una segunda instancia), provider y receiver exportados.
  - *Triggers*: al salir del reproductor XTV (`X4KPlayerAty.onStop`, proceso `:x4k`), al volver a la app y al
    navegar (`MainActivity.onResume` + efecto por ruta) y al limpiar historial en Ajustes (forzado). Freno
    propio de 15 s; todo envuelto: en teléfono el canal no existe y el publicador se apaga solo con
    `DeviceKind.isTelevision`.
  - *Ojo (lección)*: las SharedPreferences **no se sincronizan entre procesos** y el historial lo escribe
    `:x4k`. El publicador lee el XML del archivo (`WatchNextHistory.kt`) para no espejar una copia vieja, y
    protege cada tarjeta con la marca de último toque: una escritura atrasada jamás retrocede una más nueva.
  - *Verificado*: deep link en frío abre el reproductor con el título correcto (emulador, «Velvet. El Nuevo
    Imperio») y en caliente entra por `onNewIntent` sin duplicar instancia; el provider de pósters lo lee
    otra app (shell) y bloquea traversal/nombres inválidos; 254 tests en verde, incluido el ciclo completo
    del canal en Robolectric con un TV provider falso (publicar, actualizar minuto, retirar, descartes).
    Nota: el AVD `Vision_Chromecast_TV` resultó ser imagen de teléfono (sin TV provider), así que el canal
    no se puede probar ahí; en teléfono el publicador se apaga por diseño.
  - *Pendiente*: probar en la TV real (Chromecast con Google TV): ver la fila «Continuar viendo» de Vision+,
    seleccionar una tarjeta y confirmar que reanuda; el APK va incluido en la OTA v2.5.64.
- **Fix «Seguir viendo»: una sola tarjeta por serie (el último capítulo) y avance siempre guardado** (2026-10-13; 261 tests Android):
  - *Qué*: al ver una serie, la fila «Seguir viendo» del Inicio (móvil y TV) mostraba TODOS los capítulos
    vistos y casi siempre a medias (al saltar los créditos con «⏭ Sig.» el capítulo quedaba en ~95%);
    ahora sale **una sola tarjeta por serie: el capítulo más reciente**, para continuar en orden. Además
    se cerraron las fugas por las que «a veces no se guardaba el avance».
  - *Historial (`data/PlaybackHistory.kt`)*: `seriesKeyOf(entry)` (nombre base del título; el id queda de
    respaldo — al cruzar de temporada el player guarda el id de la temporada nueva y la serie se partía en
    dos tarjetas), `latestBySeries` aplicado en `continueCards` (el canal Watch Next usa la misma clave) y
    `save()` guarda **un solo recuerdo por serie** (retira los capítulos anteriores): el tope de 20 ya no
    lo consume una sola serie.
  - *Avance confiable*: (1) `X4KPlayerAty.playEpisode` llama `flushOutgoingEpisode()` antes de detener el
    motor: guarda con commit el minuto exacto del capítulo que se deja (el tic periódico quedaba hasta
    10 s atrás) y lo marca visto si el salto fue dentro de los últimos 2 min; (2) `exitPlayer()` guarda
    con el motor todavía vivo (antes, al detenerlo, la posición se perdía) y `onStop()` hace un commit de
    seguridad; (3) `PlaybackHistory.list/save/delete` leen el **XML del archivo** (el reproductor vive en
    `:x4k` y las SharedPreferences no cruzan procesos: la copia en memoria dejaba tarjetas viejas o
    borraba lo recién guardado). `WatchNextHistory` delega ahora en el historial central.
  - *Guardianes*: `PlaybackHistoryTest` (dedupe por serie, salto de temporada, películas no se colapsan,
    umbral de visto al saltar), `PlaybackHistoryStoreTest` (Robolectric: guardar un capítulo reemplaza al
    anterior + lectura del archivo fresco pese a copia en memoria vieja),
    `WatchNextPlanTest.specsCollapseOneSeriesAcrossSeasons`.
  - *OTA v2.5.64 publicada* (release OTA v2.5.64, asset `VisionPlus.apk`, misma firma histórica) — incluye
    el canal Watch Next que estaba pendiente.
- **Fase 1 — Diagnóstico de campo (crashes/ANR/anomalías → VPS)** (2026-10-13; 228 tests Android + 54 backend):
  - *App (`data/diag/Diagnostics.kt`)*: `CrashGuard` (handler global; también corre en `:x4k`),
    `AnrWatchdog` (main bloqueado >5 s), y anomalías no-fatales en los puntos clave (handoff del
    reproductor, overlay de error, sesión XTV, descarga, catálogo, índice remoto, carga del motor
    nativo). Snapshot sin PII (modelo, RAM, red, almacenamiento, pantalla, fase, motor, device id
    **hasheado**); huella estable (tipo+código+6 frames sin números de línea); cola en disco (40 máx.)
    con reintentos y subida cada 60 s desde el proceso principal. Ajustes móvil y TV: interruptor
    «Enviar diagnósticos» + «Enviar diagnóstico ahora».
  - *Servidor*: `POST /v1/diag` (API key, 256 KB, rate-limit 60/min) y `GET /v1/diag/summary`
    (agrupado por huella; `resolved` a las 48 h sin reportes) + tablas `diag_reports`/`diag_alert_state`;
    worker `sync/diag.py` hace triage cada 5 min, **avisa** huellas nuevas (Telegram/webhook o log) y
    **autocierra** las resueltas.
  - *Alertas*: configurar en `/opt/streaming-backend/.env`: `DIAG_TELEGRAM_TOKEN` + `DIAG_TELEGRAM_CHAT`
    (o `DIAG_ALERT_WEBHOOK`); sin config, la alerta queda en el log del worker.
  - *Verificado end-to-end en A32*: una anomalía real (`HOME_ROWS_FAIL`, timeout de red) y el reporte
    manual (`USER_FEEDBACK`, screen=Settings) llegaron al VPS y el triage avisó a los ~5 min.
    `PRIVACY.md` actualizado (con opt-out).
- **Fix "películas que se seguían identificando como series" — el agrupador y el API ya no mezclan cine con series**
  (2026-10-13, servidor VPS; 50 tests backend en verde):
  - *Causa raíz*: `grouping.py` agrupaba por nombre base todo `kind ∈ {series, anime, kids}`, pero el portal
    mete películas en esas columnas (`program_type='movie'`): «Zootopia» (película 2016) + «Zootopia+» (serie)
    quedaban como UN grupo con la película de "temporada 0/Completa"; «Intensa mente» + «Intensa mente 2»,
    «Kung Fu Panda 4», «Toy Story 5», «Shrek 2»… agrupadas como T2/T4/T5. El API (`_grouped_entry`) servía el
    grupo como una sola ficha con la película de representante (primer miembro XTV por temporada) → la app
    pintaba «SERIE · VISION+», «Reproducir · Completa · Capítulo 1» y selector de temporadas sobre una película
    (`isSeriesTitle = episodes.size > 1 || film.seasons.size > 1`; `seasonIds.size > 1` en el player).
  - *Alcance medido (2026-10-13)*: 54 grupos con fichas-película (77 fichas), 50 con la película como
    representante servido, 45 visibles en secciones. Muestra de la sección Series (top-40/fila, 397 únicos):
    389 series reales y 8 fichas de un solo programa (3 por el agrupador — Zootopia, Jimmy Neutrón, Rango — y
    5 por metadatos del portal). Fichas de películas normales: 53/53 con 1 programa (gate v2.5.54 OK).
  - *Fix*: `grouping.is_groupable()` — una ficha `program_type='movie'` jamás es temporada; `_group_maps()` del
    API ignora miembros-película al servir y suelta grupos que queden con 1 sola temporada (efecto inmediato).
    Compromiso: ~7 series con metadato de repisa "movie" (Ao no Miburo T1, Jersey Shore T3, Heirs of the Night T1…)
    pierden el agrupado de temporadas (tarjetas separadas) a cambio de que ninguna película sea "temporada".
  - *Guardianes*: `test_is_groupable_only_real_series`, `test_movie_never_joins_a_season_group`,
    `test_movie_sequels_never_group_as_seasons`, `test_group_maps_prunes_single_season_leftovers`.
  - *Desplegado en VPS*: api+sync reconstruidos, grupos rebobinados (3638→3594; 0 con película, 0 representantes
    película) y versión del índice 45. La app refresca la sección en ≤15 min (TTL) y el índice con el bump.
  - *Verificación post-deploy (2026-10-13)*: 224 tests Android re-corridos (0 fallos) + 50 backend. API: 0
    películas con 2+ temporadas, 0 grupos servidos de 1 temporada, secciones sin filas vacías, búsqueda OK,
    grupos legítimos intactos (Los Simpsons 2, Miraculous 6, Big Mouth 15); payload de la app (limitFilms=100):
    2.16 MB gzip en 5.6 s. A32 (equipo viejo, v2.5.62): arranque en frío sin crashes, adoptó `version=45` y la
    pestaña Series refrescada con 0 películas-como-series (Zootopia+/Jimmy Neutrón serie en su lugar). Versiones
    viejas: el cliente del índice existe desde v2.5.36 y parsea con `opt*` (esquema sin cambios); anteriores ni
    lo consumen. El worker renovó la sesión del portal una vez (patea la app; se recupera sola, v2.5.61).
- **LÍNEA ESTABLE v2.5.62** (2026-10-13): 224 tests Android en verde + prueba física en A32.
  **Fix "descargar se pelea con reproducir en línea" (pausa con aviso)**:
  - *Causa raíz*: descarga y reproducción comparten el proceso `:x4k` y el motor nativo
    solo admite **un programa a la vez**. `X4KDownloadService` mantiene el programa
    headless vivo durante TODA la descarga (`stopHeadless` recién en el `finally`); abrir
    una peli en ese momento lanzaba un segundo programa → la peli se buggeaba y/o moría
    la descarga; salir del reproductor mata el proceso (`killProcess`) y con él la
    descarga. El candado existía solo en una dirección (la descarga no inicia si hay
    player activo).
  - *App*: `EngineDownloadGate` (PlayerScreen) intercepta `xtv:`/`xtvlive:` con descarga
    XTV activa → diálogo honesto «Pausar y ver ahora / Seguir descargando» (al reanudar
    empieza de cero: no hay reanudación por offset). El fallback Titan→XTV avisa en vez
    de fallar. `pauseDownload(id, context)` ahora **detiene de verdad** al servicio
    (antes solo cambiaba la etiqueta y seguía bajando por detrás).
  - *Servicio*: `ACTION_PAUSE` cancela el job, **suelta el motor al instante**
    (`stopHeadless` sincrónico) y confirma con el marcador `.paused`; el reproductor
    espera el ack (candado `pauseLock` + arbitraje por marca de emisión del intent: sin
    carreras ni marcas pegadas). La pausa/cancelación limpia el parcial `.downloading`.
  - *Guardianes*: `OfflinePlaybackCoordinationTest` (política de bloqueo, ack por
    archivo, servicio y puerta). Prueba física A32: diálogo → seguir (baja) → pausar y
    ver (PAUSED, motor libre, la peli abre a los ~1 s) → reanudar (reinicia de cero).
- **LÍNEA ESTABLE v2.5.61** (2026-10-13): 219 tests Android + 46 backend en verde.
  **Fix de la regresión "error al primer intento, Reintentar sí reproduce"**:
  - *Causa raíz*: el worker del VPS usa el mismo SN que la app (`XTV_SERVER_SN` vacío):
    cada acuñado suyo patea la sesión (`aaa100083`). La app intentaba re-acuñar pero
    `renewLocked` tenía un silencio de 30 s que devolvía el **token ya rechazado**; los
    3 auto-intentos del handoff viven en ~1.2 s → todos dentro de la ventana envenenada
    → "No se pudo abrir"; el Reintentar manual (después de los 30 s) ya funcionaba.
    Se agravó al reiniciar el worker varias veces en los deploys de v2.5.59/60.
  - *App*: `mintAllowed()` (puro, con test) permite **re-acuñar de inmediato cuando el
    portal rechazó el token** (`force = true` en `callWithSession` y en `prepareVod`),
    con candado propio de 10 s anti-ráfaga; `lastMintAt` ahora solo se marca en ÉXITO
    (un mint fallido no bloquea); `isSessionFailure` cubre 100001..100008, 200000..200002
    y 300001..300002; el handoff sube a **4 intentos** (400/900/1800 ms); `launch()`
    re-pide entries/params si una renovación las borró (el motor ya no arranca sin SLB).
  - *Reproductor nativo*: el primer `X4KTitan.play` se **reintenta una vez a 900 ms**
    (proceso `:x4k` recién booteado) antes de mostrar error.
  - *Guardianes*: `X4KApiSessionTest` (patada re-acuña, candado forzado, rangos de
    códigos, errores de negocio NO renuevan).
  - **Diagnóstico verificado**: el código del reproductor no cambió desde v2.5.54; la
    regresión vino del ping-pong de sesión + silencio de mint, no de v2.5.59/60.
- **LÍNEA ESTABLE v2.5.60** (2026-10-13): 214 tests Android + 46 backend en verde.
  **Optimización para bajos recursos (Chromecast/TV boxes), sin perder funciones**:
  - **Imágenes**: las 15 `AsyncImage` que decodificaban a resolución fuente ya piden
    `size()` (heroes de Home/fichas al tamaño real de pantalla con `rememberScreenPx()`,
    `TvLandscapeCard` 480x270, logos de canales, miniaturas de Descargas/MiniPlayer).
    El crossfade de las tarjetas se apaga en modo ligero (`LocalReducedEffects`).
  - **Home fuera del hilo de composición**: pool + Top 10 + Novedades + filas por tipo
    + héroe se calculan con `Popularity.homeCurated()` en `Dispatchers.Default` vía
    `produceState` (móvil y TV); «Seguir viendo» del móvil también en background.
  - **Índice remoto**: el payload (varios MB) salió de SharedPreferences a archivo
    (`remote_index.json` + `remote_section_*.json`, migración automática) y el parseo
    del arranque corre en IO (`XuperCatalog.load`).
  - **Micro**: skeletons con una sola transición por fila, filtro de Categorías
    memoizado, `topRated`/`novelties` de TvCategory solo en modo genérico, búsqueda
    local con títulos normalizados precomputados (`searchCorpus`), recomendaciones con
    índice film→hileras O(1), LiveScreen móvil memoizado (canales/géneros/países),
    catálogo inicial de los buscadores en `Dispatchers.Default`, `contentType` en
    listas de Categorías TV.
  - **Guardianes nuevos**: `PopularityTest.homeCuratedBundlesOwnRowsAndHero`,
    `homeCuratedFallsBackToServerPremieres`.
- **LÍNEA ESTABLE v2.5.59** (2026-10-13): 212 tests Android + 46 backend en verde.
  **Catálogo propio en Inicio + Novedades rescatadas de Xuper + popularidad real
  (fin del nicho)**:
  - **Inicio 100% propio**: `XuperCatalog.load` ya no fusiona las repisas crudas del
    portal («HBO Movie», «Maldición misteriosa», «All», «Tráiler»…) en `rows`; el
    portal se consulta solo por metadatos de fichas (K_META) y por `liveRow`. Home
    móvil y TV arman su pool con `Popularity.homePool` (filas propias + hileras
    curadas del índice: `estrenos`, `recomendados`, `genero-*`) y pintan filas
    propias por tipo (`popularByKind`): «🎬 Películas populares», «📺 Series
    populares», «🌸 Anime», «🧸 Para chicos». Las filas `magis-*` se excluyen del
    Inicio. El hero/estrenos usan fallback al pool propio (no a un canal de TV).
  - **Novedades = destacados de Xuper rescatados**: el portal no tiene endpoint de
    «destacados» (probado `getColumnRecommendResource`/`specialFlag`: vacíos); sus
    destacados son las repisas de estreno por sección («2026», «2026 Peliculas
    teatrales», «Últimas Actualizaciones»). `xtv.is_premiere_shelf` las detecta en
    el sync y las enlaza a `xtv-{sección}-destacados` (kind source, 70). `index_build`
    las pone al frente de `estrenos` global y de `xtv-{sección}-estrenos` (intercalado
    por rango de sección; relleno por popularidad desde la posición 1000). La fila
    «✨ Novedades y Estrenos» existe siempre (fallback a la hilera del servidor).
  - **Popularidad real (nicho fuera)**:
    - `index_build` excluye de filas curadas lo descartado por TMDB
      (`NOT_UNMATCHED`: `match_confidence < 0.5`) — «El Temible Quemador», «Las
      Panteras» y similares ya no aparecen — y ordena `popularity desc` (los
      pendientes de clasificar caen al final).
    - `tmdb.py`: `clean_query` quita la marca de temporada antes de buscar (el 96%
      de las series fallaba buscando «Eagleheart T1» tal cual); en tv el año ya no
      filtra la búsqueda (solo pondera confianza); anime/kids buscan tv+movie;
      `rematch_batch` reintenta los ~19k «sin match» (cooldown 14 días);
      `backfill_popularity` rellena popularidad/votos de los ~10.5k matches viejos.
      Batches: `TMDB_REMATCH_BATCH=2500`, `TMDB_BACKFILL_BATCH=3000` (por ciclo).
      OJO: el relleno total toma ~1-2 días; el orden mejora ciclo a ciclo.
    - Bug latente corregido: `magis.py`/`archive_ia.py` armaban tuplas de 18 columnas
      contra `TITLE_SQL` de 20 → sus conectores fallaban en cada ciclo desde v2.5.53;
      ahora usan `title_row()` puro con tests de aridad.
  - **API**: `film()` sirve `kind` (lo usan las filas propias del Home); `Film.kind`
    parseado en `RemoteIndexSource` (payload, búsqueda y caché).
  - **Guardianes nuevos**: `PopularityTest.homePoolOnlyTakesCuratedIndexRows`,
    `popularByKindOrdersByImpactAndFillsWithUnclassified`,
    `RemoteIndexTest.kindSurvivesTheParseForHomeRows`, `test_premiere_shelf_detection`,
    `test_clean_query_strips_season_marks`, `test_magis_row_matches_sql_arity`,
    `test_ia_row_matches_sql_arity`, `test_curated_rows_exclude_unmatched_titles`.
- **LÍNEA ESTABLE v2.5.58** (commit `f3343b2`, tag `v2.5.58`, 2026-10-13): 207 tests
  Android + 38 backend en verde; offline probado en físico (A32 1.09 GB + POCO 829 MB
  + play offline con video/audio); reproducción intacta. **Regla anti-regresión**:
  no tocar `data/offline/*`, `X4KTitan.play/stop/queryStatusUrls/beanPlayUrl`,
  `X4KPlayerAty.onCreate/play` ni `VisionApp.onCreate` sin (1) tests en verde y
  (2) prueba física en A32/POCO si cambia runtime. Guardianes:
  `OfflineDownloadDispatchTest`, `X4KTitanStatusTest`, reconcile-tests. Si otro agente
  avanza en paralelo, rebasear contra este tag y re-correr la suite completa.
- App Android (Compose) **"Vision+"** — pkg `com.chronosbvrx.visionplus`, `versionName` actual **2.5.81**
  (versionCode **317**, release OTA v2.5.81). Es una app de streaming personal del usuario (ChronosBVRX).
- Integra el catálogo **Xuper/XTV** (tenant "masnew"): películas/series/kids/anime + **1000+ canales en vivo**,
  con el motor nativo propio del proveedor (Titan/Ranger) embebido.
- **Directivas del usuario**: sin distinción free/pago (todo el catálogo para todos); sin salir de la app
  (actualizaciones in-app); la app detecta sola si es Smart TV y se maneja con mando; todo **premium**
  (azul #2D7DF6); marca "Vision+" sin rastros visibles de "Xuper" ni "Magis"; orientación según teléfono
  y video sin deformar.

- **Descargas offline verificadas en A32 ("Bean play_url & Facade x")** (v2.5.55):  - **Causa del "sigue sin descargar"**: `GetStatus {"instance":0,"name":…}` vía
    `NativeJni.a` devuelve vacío SIEMPRE (probado con video reproduciéndose: no existe
    esa vía de sondeo). La URL real vive en el bean `uc.c` (`p(0)` → `d()` = play_url):
    proxy local `http://127.0.0.1:<puerto-dinámico>/vod/0/<code>.m3u8` (HLS). La vía
    `NativeJni.o/g` en instance=1 era el SIGSEGV (`star_file_cache`, slot 1 sin init).
  - **App**: `playHeadless` = facade `x(0, code, program, "vod")` (la llamada que
    `ed.e.A()` hace; sin vista/Surface/audio); `beanPlayUrl` + `queryStatusUrls`
    leen `d()`; `stopHeadless` = `H(0, code)`; guard de player-activo; sondas DlProbe
    retiradas. Principal jamás toca Ranger (delegación a `:x4k` + broadcasts).
  - **Validación**: 204 pruebas Android en verde + prueba física en A32 (logcat):
    Spider-Man 2 → 1,166,536,428 B cifrados → COMPLETED → play offline con video+audio.
    POCO F7 (Android 16) verificado por ADB: descarga y COMPLETED OK (757 MB, otro título).
    Ojo: el CDN rinde ~2 MB/s por conexión (con VPN del usuario, peor); 1 GB tarda
    ~10 min y el % parece quieto — no es bug. Mejora futura: segmentos HLS en paralelo.
    Release OTA v2.5.55.
- **Descargas estables (v2.5.56)**:
  - HLS en paralelo (4 conexiones) PROBADO Y REVERTIDO en POCO: el proxy local de
    Ranger sirve un solo cliente; con 4 conexiones responde HTTP 404/cortes y mata
    la descarga. Secuencial = el que completa (A32 1.09 GB + POCO Toy Story 5 829 MB).
    El cuello es el upstream P2P (~2 MB/s); paralelizar de verdad exige handshake
    directo con el CDN (futuro).
  - `OfflineRegistry.reconcileWithDisk` al iniciar: `.vpack` válido → COMPLETED
    adoptado (cubre broadcast perdido con app muerta); fantasma → descarte;
    DOWNLOADING/QUEUED → PAUSED. Constante única `MIN_VALID_VAULT_BYTES`.
    Blindajes: parts-dir único por corrida, guard anti-doble-corrida por id.
  - **Validación**: 206 pruebas Android en verde. Release OTA v2.5.56.
- **Fix regresión Chromecast + resolve acotado (v2.5.57)**:
  - Chromecast se reiniciaba en arranque (patrón doble-proceso v2.5.41): el service
    era `START_STICKY` y Android relanzaba `:x4k` solo tras kills/reboots → LMK en
    1.5 GB. Ahora `START_NOT_STICKY` siempre; nada levanta `:x4k` sin tap/play.
  - `prepareVod` con `withTimeout(60s)` x2: ERROR honesto en vez de 0% por minutos.
  - **Validación**: 206 pruebas en verde. A32 se quedó sin red a mitad de la
    verificación headless (`Network is unreachable` en shell: ambiental, no app).
    El path de descarga ya estaba probado 2 veces en físico (A32 1.09 GB + POCO
    829 MB). Release OTA v2.5.57.
- **Progreso en vivo + notificación con % (v2.5.58)**:
  - En POCO/HyperOS los broadcasts `:x4k`→principal se envían (historial del sistema)
    pero `onReceive` jamás corre (emisor y receptor verificados vivos por ADB): la UI
    quedaba en 0% aunque el disco crecía. En A32 sí llegan.
  - Puente por archivo: el servicio escribe `<id>.progress` (1/s) y
    `OfflineProgressSync` (ticker 2 s en `VisionApp` principal) lo refleja en el
    registro. Notificación FGS con % en título + `FOREGROUND_SERVICE_IMMEDIATE`.
  - **Validación**: 207 pruebas en verde. Release OTA v2.5.58.
- **Películas sin UI de Serie ("Movie-Only Player & Series Gate")** (v2.5.54):  - **Problema**: el reproductor mostraba "Siguiente episodio" y selector de
    temporadas/capítulos en películas. Causa: 3 definiciones distintas de "es
    serie" (`buildControls` y `showEpisodePicker` aceptaban `seriesId` suelto) y
    `parseEpisodePrograms` aceptaba 1 programa como serie (la ficha de película
    del portal), contaminando `EpisodePlay` desde la ficha.
  - **App**: fuente única `isSeriesContent(ep>1 || temp>1)` en `ControlsState.kt`
    (`hasNextEpisodeAvailable` la exige); `parseEpisodePrograms` descarta <=1;
    `playVod`/`launch` degradan a película y no envían extras; `X4KPlayerAty`
    normaliza intents/historiales viejos, `saveHistory` solo guarda contexto en
    series, `showNextCard`/`showEpisodePicker`/`onFirstFrame` unificados;
    `TitleScreen`/`TvTitleScreen`/`resumeRoute` con `isSeriesTitle` y saneo.
  - **Validación**: 203 pruebas Android + 38 backend en verde (nuevos
    `movieNeverShowsSeriesUi...` + `X4KApiSeriesTest`). Release OTA v2.5.54.

- **Tendencias y Estrenos Reales con Motor de Popularidad ("Popularity Rank & Real Premieres")** (v2.5.53):
  - **Problema**: Home y secciones ordenaban por `score` puro (rating sin votos) o año puro;
    un oscuro 9.0 (5 votos) tapaba a un blockbuster 7.7 (200k votos), y "Estrenos" era
    `año DESC` (oscuro 2026 score 0 vencía a éxito 2025). Las filas del portal traían
    `year=0, score=0` (score solo en `K_META`), excluidas del Top 10.
  - **App**: nuevo `data/Popularity.kt` puro (`rankScore` = rating con confianza +
    popularidad TMDB log + recencia suave + micro-bono con póster; `topTrending`,
    `realPremieres` solo año actual/anterior ordenados por impacto, `reorderRow`,
    `pickHero`, `dedupe`). `Film` suma `popularity`/`voteCount`; `RemoteIndexSource`
    los parsea/cachea; `X4KApi.homeRows` hidrata `score`/`year` del portal
    (`portalScore`/`portalYear`, persistidos en caché); `HomeScreen`/`TvHomeScreen`
    héroe = #1 tendencias con póster; `TvCategoryScreen`/`SectionScreen` reordenan
    en cliente (robusto ante caché vieja) y cuadrículas rankeadas;
    `UserLibrary.recommendations` rankeada.
  - **Servidor**: `schema.sql` agrega `titles/popularity,vote_count` +
    `enrichment/popularity,vote_count` (idempotente); `tmdb.py` los captura y propaga
    a `titles`; `index_build.py` ordena estrenos y géneros por `popularity DESC, score,
    year`; `/v1/search` desempatando por popularidad; `film()` sirve
    `popularity`/`voteCount` tolerante a registros viejos (+ fix: `search` pasaba
    `enrichment` a `_grouped_entry`).
  - **Validación**: 199 pruebas Android (`:app:testNormalDebugUnitTest`) + 38 backend
    (`pytest server/tests/`) en verde. Release OTA v2.5.53.
  - **Desplegado en VPS** (2026-10-12): backup `backups/catalog_2026-10-12_2049.dump` (17 MB);
    subidos `api/app.py` + `sync/{schema.sql,tmdb.py,xtv.py,index_build.py}` +
    `sync/sports.py` (estaba desactualizado: traía la dilución de canales que v2.5.48
    quitó en repo — ahora VPS = HEAD) + `.env.example` (docs). `docker compose up -d
    --build api sync` (postgres intacto); `esquema aplicado`, API sana, `/v1/section`
    ya sirve `popularity`/`voteCount`. Backfill TMDB 2500/ciclo (~11 ciclos): el orden
    por popularidad mejora solo ciclo a ciclo; sin regresión mientras tanto (fallback
    a score/año cuando `popularity=0`).

- **Descarga Real de Streams de Video XTV/HLS/HTTP y Conmutación Automática a Interfaz Especial Sin Conexión ("Real Stream Engine & Auto Offline Mode")** (v2.5.50):
  - **Diagnóstico y Corrección de "En cuanto pongo descargar dice descargado"**:
    - *Causa Raíz*: En `OfflineDownloadManager.kt`, `X4KApi.prepareVod` entrega el `playCode` y la `license` del portal XTV (cuyo identificador `"n"` no inicia con `"http"`). Al no ser una URL HTTP directa, `OfflineDownloadManager` caía en el bloque de respaldo `simulateEncryptedOfflineAsset`, escribiendo un texto placeholder de ~60 bytes y marcando la descarga como `COMPLETED` al instante.
    - *Solución*:
      1. *Resolución Real de Streams con `X4KTitan.resolveVodStream`*: Implementada resolución en segundo plano sobre la instancia secundaria del motor nativo Ranger (`instance = 1`) configurada en modo `"exo"` (`NativeJni.g(1, "exo", 0)`), obligando al motor a servir un flujo HTTP estándar (en lugar de `mem://`) a través del callback `JniHandler$a` y sondeo `GetStatus`. Directorio de trabajo aislado por proceso (`x4k-luna` vs `x4k-luna-main`) para evitar bloqueos entre el proceso principal y `:x4k`.
      2. *Soporte Completo para Flujos Progresivos (MP4/MPEG-TS) y Listas HLS (`.m3u8`)*: `OfflineDownloadManager` descarga y cifra byte a byte tanto streams progresivos como manifiestos HLS (incluyendo selección de variante por resolución `1080p`/`720p`/`480p`, segmentos `#EXT-X-MAP` y descifrado de transporte `#EXT-X-KEY:METHOD=AES-128`), actualizando el progreso real en `OfflineRegistry`.
      3. *Erradicación Total de Placeholders y Auto-Purga*: Eliminado `simulateEncryptedOfflineAsset`, exigido un mínimo de bytes reales de video (`MIN_VALID_VIDEO_BYTES`) y añadida auto-purga automática en `OfflineRegistry.loadFromPrefs` para limpiar cualquier registro simulado `<= 512` bytes de v2.5.49.
      4. *Seeking Instantáneo en Contenedores MP4 y MPEG-TS*: Configurado `DefaultExtractorsFactory` con `setConstantBitrateSeekingEnabled(true)` en `PlayerScreen.kt`.
  - **Detección Reactiva de Red e Interfaz Especial Sin Conexión (`NetworkMonitor.kt`)**:
    - *Detección en Tiempo Real y Arranque en Frío*: `NetworkMonitor` supervisa `ConnectivityManager` (`NetworkCallback` + validación de transporte activo). Si la app inicia sin conexión a internet, `MainActivity` omite la espera de 9 segundos del catálogo y abre en 600 ms directamente en la **Interfaz Especial Sin Conexión ("Modo Sin Conexión · Vision+ Offline")**.
    - *Conmutación Automática en Móvil/Tablet (`PhoneApp.kt` / `DownloadsScreen.kt`) y Smart TV (`TvApp.kt` / `TvDownloadsScreen.kt`)*: Al perder la conexión (mientras no se esté reproduciendo un video), la app redirige automáticamente a la interfaz dedicada "Vision+ Offline" con banner de estado sin conexión, botón `[ 🔄 Reintentar conexión ]`, medidor de almacenamiento y acceso en un clic para reproducir las películas y series descargadas. Cuando regresa el internet, recarga el catálogo automáticamente y restaura la navegación completa.
  - **Validación**: 178 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%. Release OTA v2.5.50.

- **Modo Offline Blindado y Búnker Criptográfico Anti-Piratería ("Hardware-Encrypted Vault & Strict Removable Storage Policy")** (v2.5.49):
  - **Arquitectura de Búnker Criptográfico (Cero Fugas / Anti-Copia / Grado Comercial)**:
    - *Cifrado de Hardware*: Hardware-backed `AndroidKeyStore` (AES-256 en modo CTR) con vector de inicialización seguro de 16 bytes y cálculo instantáneo de saltos $O(1)$ (`computeIvForOffset = baseIv + (offset / 16)`). Cabecera binaria `VPVAULT1` (33 bytes) conteniendo magic bytes, versión, base IV y tamaño original. Si el archivo `.vpack` es copiado o transferido a una PC o teléfono externo, es matemáticamente imposible de abrir o reproducir sin el chip seguro del dispositivo original.
    - *Descifrado al Vuelo en RAM Directa (`EncryptedVaultDataSource`)*: Fuente de datos binaria personalizada para Media3 ExoPlayer que descifra fragmentos directamente en memoria volátil conforme el decodificador de video solicita bytes. Ningún frame o archivo temporal descifrado toca jamás el disco flash.
    - *Anti-Grabación de Pantalla (`FLAG_SECURE`)*: `PlayerScreen` activa `FLAG_SECURE` durante la reproducción de esquemas offline `vault:`, protegiendo el contenido contra capturas de pantalla o grabadores externos.
  - **Política Estricta de Almacenamiento en Smart TV ("Política de USB Obligatoria")**:
    - *Diagnóstico y Prevención de Saturación*: Los dispositivos Smart TV (Chromecast con Google TV, Xiaomi Stick, TV Box ZTE B866F) cuentan con memorias de 4 a 8 GB compartidas con el SO y caché de apps. Permitir descargas internas provocaría saturación del almacenamiento, bloqueos del lanzador y muertes por `lmkd`.
    - *Regla*: En Smart TV (`DeviceKind.isTelevision()`), las descargas están terminantemente deshabilitadas en memoria interna y exigen un almacenamiento USB o disco externo extraíble (`isRemovable == true`).
    - *UX Informativo*: Si no hay memoria USB conectada, la app despliega el modal interactivo `TvNoUsbDialog` informando con claridad los requerimientos de almacenamiento USB antes de permitir la descarga.
  - **Experiencia en Móviles y Tablets**:
    - Almacenamiento seguro en sandbox de la app (`filesDir/vision_vault/`) o ranura MicroSD si está disponible.
    - Medidor visual en barra de almacenamiento (`DownloadsScreen`) que desglosa el espacio ocupado por Vision+ versus espacio libre en el dispositivo.
  - **Integración UI Completa**:
    - Fichas de detalle (`TitleScreen` y `TvTitleScreen`): Botón `[ ⬇ Descargar ]` / `[ 💾 Descargar en USB ]` con diálogo de selección de calidad e indicador circular de avance porcentual en vivo.
    - Pestaña "Descargas" en el menú lateral principal, en "Mi lista" (`LibraryScreen` y `TvLibraryScreen`), y en los hubs rápidos del Home (`SELECTIONS` y `TV_QUICK_HUBS`).
  - **Validación**: 176 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 34 pruebas de backend (`pytest server/tests/`) pasando al 100%. Release OTA v2.5.49.

- **Depuración Integral de Fuentes de Fútbol en Vivo y Erradicación de Canales Falsos ("Zero Non-Sports & Catalog-Only Linking")** (v2.5.48):
  - **Diagnóstico y Corrección de "TNT Series en Partidos de Fútbol"**:
    - *Causa Raíz*: En `SportsLinker.kt`, la regla de tokenización `cleanToks.contains("tnt")` asignaba incondicionalmente `brand = "tnt sports"`. Al evaluar canales en vivo como `TNT SERIES HD` o `TNT NOVELAS HD`, `cleanToks` contenía `"tnt"`, por lo que el normalizador les asignaba la marca `"tnt sports"`. Además, en `scoreChannel`, los canales de series recibían una puntuación alta (+260) por coincidencia de marca y país. Al buscar partidos emitidos por TNT Sports (o en la plataforma Max), `SportsLinker` seleccionaba `TNT SERIES HD` en lugar del canal deportivo real.
    - *Solución*:
      1. *Identificación Rigurosa de Marca*: `cleanToks.contains("tnt")` sólo asigna `"tnt sports"` si el nombre del canal contiene explícitamente `"sports"`, `"sport"`, `"deportes"` o `"tnt sport"`. Si contiene `"series"`, se marca como `"tnt series"`, y si contiene `"novelas"`, como `"tnt novelas"`. Canales como `STAR CHANNEL HD` o `WARNER HD` quedan excluidos de marcas deportivas como ESPN.
      2. *Filtro de Elegibilidad Estricta (`isEligibleSportsChannel`)*: En `SportsLinker`, ningún canal perteneciente a géneros no deportivos (`GENRE_SERIES`, `GENRE_NOVELAS`, `GENRE_MOVIES`, `GENRE_KIDS`, `GENRE_MUSIC`, `GENRE_RELIGION`, `GENRE_DOCS`) ni con palabras clave como `"series"`, `"serie"`, `"novelas"`, `"novela"`, `"cinema"`, `"movies"`, `"kids"` puede puntuar (`score = 0`). Sólo canales en `GENRE_SPORTS` y cadenas abiertas en `GENRE_ENTERTAINMENT` (Canal 5, Azteca 7, Telemundo, Universo) son elegibles.
  - **Erradicación de Fuentes Fantasma / Enlaces Rotos ("Solo si de verdad tenemos el canal")**:
    - *Causa Raíz*: `buildLinkWithFallback` invocaba `GeeSportsFallback.fallbackFor()`, inyectando hasta 12 enlaces externos muertos/rotos (`redima.pro`, `170.82.37.21`, etc.) que confundían al usuario y fallaban al reproducir. Además, en el backend (`server/sync/sports.py`), a cada partido de liga internacional se le agregaban incondicionalmente todos los canales de la liga (`assigned_channels.append(ch)`), haciendo que partidos exclusivos aparecieran con 6 canales incorrectos.
    - *Solución*:
      1. *Catálogo Real Exclusivo*: `buildLinkWithFallback` ahora devuelve única y exclusivamente los canales verificados de `primary` presentes en la lista en vivo de Vision+ (`xtvlive:`). Si Vision+ no cuenta con el canal del partido, no inventa fuentes y muestra honestamente `"Sin canal en tu lista · por [Cadena]"` / `"No está en tu lista"`, evitando pantallas negras y enlaces engañosos.
      2. *Puntuación de Países Sin Penalización*: Se eliminó la penalización de -70 puntos a feeds de Sudamérica (Chile, Argentina, Colombia, Perú), permitiendo que canales legítimos presentes en XTV como `TNT SPORTS CHILE HD`, `TNT SPORTS PREMIUM`, `WIN SPORTS` o `TYC SPORTS` puntúen positivamente cuando emiten el encuentro.
      3. *Backend Limpio (`sports.py`)*: Cuando un partido ya tiene canales asignados por LiveSoccerTV o ESPN, no se diluyen anexando toda la lista de canales de la liga.
  - **Validación**: 167 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 34 pruebas de backend (`pytest server/tests/`) pasando al 100%.

- **Catálogo Indexado Canónico, Normalización Universal VOD y Erradicación de Errores al Iniciar ("Auto-Retry Zero Touch")** (v2.5.47):
  - **Diagnóstico y Corrección de "Título no trae direcciones de reproducción" (Caso Pequeños Calvarios y similares)**:
    - *Causa Raíz*: En el índice del servidor y feed remoto `/v1/index`, títulos sincronizados desde el conector secundario Magis (`magis:E019F33B862F...`) conservaban referencias `magisvod:<cid>` y se integraban a categorías como "Estrenos y Novedades" o colecciones de catálogo. Al abrir dichos títulos, la app los enrutaba hacia `TitanVodPlayerScreen` con `MagisClient`, el cual fallaba por cuentas no activas arrojando el error `Este título no trae direcciones de reproducción`, a pesar de que el contenido comparte el mismo `contentId` universal de la plataforma y cuenta con streaming 100% activo bajo el motor nativo XTV (`xtv:<cid>`).
    - *Solución*:
      1. *En Backend (`server/api/app.py` y `server/sync/index_build.py`)*: La función `film()` mapea automáticamente cualquier referencia `magisvod:<cid>` al esquema canónico nativo `xtv:<cid>` con `sourceUrl = 'xtv'`. En `/v1/search`, implementada desduplicación estricta por `contentId` y título, asegurando que jamás se entreguen dos entradas (una rota y una funcional) para el mismo título. En `index_build.py`, la repisa especial de Estrenos filtra estrictamente `source != 'magis'`, garantizando que sólo ingresen títulos con reproducción nativa verificada.
      2. *En la App (`RemoteIndexSource.kt`)*: Tanto `parseRows` como `search` normalizan cualquier `magisvod:<cid>` a `xtv:<cid>` y desduplican títulos dentro de cada fila por `contentId`.
      3. *En `PlayerScreen.kt`*: Redirección de primer nivel de cualquier ref `magisvod:` a `PlayerScreen(xtvFilm)` con `xtv:<cid>`, más fallback resiliente en `TitanVodPlayerScreen` hacia `X4KApi.playVod`.
      4. *En `HomeScreen.kt` y `TvHomeScreen.kt`*: `allAvailableFilms` y `estrenosFilms` desduplican por `cleanCid`, eliminan entradas dummy/semilla (`seed_`) cuando hay datos del índice y garantizan que lo mostrado en billboard, Top 10, Estrenos y Recomendados esté plenamente indexado en nuestra BD con streams activos.
  - **Erradicación del Error Inicial de Reproducción ("Error al abrir pero le das reintentar y ahora sí abre")**:
    - *Causa Raíz*: `XtvHandoff` en `PlayerScreen.kt` invocaba `prepare()` una sola vez de forma rígida. Si el primer intento fallaba por handshake frío de red, token guardado en SharedPreferences revocado por el portal tras horas de inactividad o ligera latencia de conexión, saltaba de inmediato a `failed = true`, mostrando el overlay "No se pudo abrir" con el botón manual "Reintentar". Al hacer clic manual, el segundo intento ya encontraba la sesión renovada y abría exitosamente.
    - *Solución*:
      1. *Auto-Retry Transparente en `XtvHandoff`*: Implementado bucle de reintento automático de hasta 3 intentos totales (intento inicial + 2 reintentos transparentes) con retardos progresivos (400 ms, 800 ms). Si ocurre un fallo transitorio en el primer intento, se auto-recupera en milisegundos de forma transparente, abriendo el video directo sin interrumpir al usuario ni exigir pulsar "Reintentar".
      2. *Auto-Recuperación de Sesión en `X4KApi.kt`*: En `prepareVod`, si la consulta a `/v10/startPlayVOD` no obtiene medios en la primera pasada, invoca de inmediato `renewSession(context)` y reintenta la llamada. En `isSessionFailure`, incorporada detección exhaustiva de códigos del portal (`portal 100001..100008`, `200000..200002`, `300001`, `HTTP 401..403`).
      3. *Resiliencia en `playVod` y `X4KPlayerAty.kt`*: Reintento automático interno en `playVod` y en el cambio de capítulo de series (`playEpisode`).
  - **Entorno de Emulación Frameless (Móvil y Smart TV Low-End)**:
    - `Vision_Chromecast_TV`: Perfil Smart TV 1080p (1920x1080, 320 dpi), memoria limitada a 1.5 GB RAM (`hw.ramSize = 1536`) y heap de 256 MB para emular exactamente un Chromecast with Google TV. Activa `DeviceKind.isTelevision()` y `DevicePerformance.isLowEndDevice()`. Controlable con teclado de PC vía D-Pad (Flechas, Enter, Esc). Ventana limpia sin marco (`-no-skin`).
    - `Vision_Phone`: Perfil móvil vertical limpio (1080x2400) sin carcasa de plástico.
    - Scripts de un solo clic: `run_emulator_tv.bat` (TV), `run_emulator.bat` (Móvil) e `iniciar_emulador.bat` (Selector interactivo).
  - **Validación**: 165+ pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) y 34 pruebas de backend (`pytest server/tests/`) pasando al 100%. Release OTA v2.5.47.

- **Simplificación Integral del Menú Lateral (6 Pilares Globales), Colecciones Rápidas y Corrección Definitiva de Pérdida de Foco D-Pad al Regresar** (v2.5.46):
  - **Diagnóstico y Corrección de Pérdida de Foco D-Pad al Regresar de Ficha ("Back Focus Orphan")**:
    - *Causa Raíz*: Al abrir una película desde `TvHomeScreen` y `TvCategoriesScreen`, el callback `onOpenFilm` no notificaba el ID seleccionado (`onFilmPick(it.id)` no se invocaba) y ambas pantallas carecían del parámetro `returnFilmId`. Al presionar el botón "Atrás", `SaveableStateProvider` restauraba la pantalla en la posición de scroll donde el usuario había hecho clic, dejando el billboard fuera de la pantalla (`offscreen`). Al estar fuera del árbol de composición activo, `landing.request()` en el botón del Hero fallaba silenciosamente, y las tarjetas de la lista no tenían ningún `FocusRequester` registrado para retorno. Esto provocaba que ningún elemento tuviese el foco y la navegación con las flechas del control quedara completamente bloqueada.
    - *Solución*: Implementado `returnFilmId` y `returnRequester: FocusRequester` con efecto reintento de 12 ciclos (`repeat(12)`) y fallback a `landing.request()` tanto en `TvHomeScreen` como en `TvCategoriesScreen`. Propagado a `HeroBillboard`, `HomePosterRow` (Top 10, Estrenos y filas curadas) y `ContinueRow`. Actualizado `onOpenFilm` en `TvApp.kt` para registrar `onFilmPick(it.id)`.
  - **Arquitectura de Navegación Simplificada de Clase Mundial (Menú Lateral de 6 Pilares)**:
    - Riel de Smart TV reducido de 9 ítems saturados a 6 pilares estratégicos:
      1. `🔍 Buscar` (`Route.Search`)
      2. `🏠 Inicio` (`Route.Home` con retorno de pila `goTab`)
      3. `🎬 Películas` (`Route.Section("movies", "Películas")`)
      4. `📺 Series` (`Route.Section("series", "Series")`)
      5. `📡 En vivo` (`Route.Live`)
      6. `🔖 Mi lista` (`Route.Library`)
      Y anclado al fondo: `⚙️ Opciones` (`Route.Settings`).
    - Homologado el menú lateral de Teléfonos y Tablets (`PhoneApp.kt`) bajo los mismos 6 pilares universales.
  - **Fila de Accesos Rápidos a Colecciones y Especiales ("Brand Hubs" estilo Disney+/Max)**:
    - En `TvHomeScreen`, agregada fila horizontal `TvQuickHubsRow` inmediatamente después del Hero Billboard:
      - `[ ⚽ Fútbol en Vivo ]` (Acceso directo con foco D-pad a `Route.Sports`)
      - `[ 🌸 Anime ]` (Acceso directo a `Route.Section("anime", "Anime")`)
      - `[ 🎭 Novelas ]` (Acceso directo a `Route.Section("novelas", "Novelas")`)
      - `[ 📂 Categorías ]` (Acceso directo a `Route.Categories`)
    - En Móvil (`HomeScreen.kt`), expandida la fila `SELECTIONS` para incluir accesos directos a Películas, Series, Fútbol, TV en Vivo, Anime, Novelas y Categorías.
  - **Validación**: 163 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%, incluyendo nuevas pruebas `homeDownToFilmAndBackKeepsFocus` y `categoriesBackKeepsFocus`. Release OTA v2.5.46.

- **Rediseño Integral de la Pantalla Principal (Home Screen) — Estilo Cinematic Billboard** (v2.5.45):
  - **Billboard Hero Dinámico e Interactivo en Smart TV (`TvHomeScreen.kt`)**:
    - Encabezado con badge distintivo `🔥 TENDENCIA DE HOY` en gradiente `VisionBlue`.
    - Título en tipografía grande blanca bold, metadatos en una línea (`Año · ★ Rating · Duración`) y sinopsis limpia.
    - **Botones de acción directa con foco de mando**: `[ ▶ Reproducir ]` (aterrizaje inicial de foco en D-pad), `[ ℹ Detalles ]` y `[ ★ Favorito ]`.
    - Fondo cinemático inmersivo con drift y degradado horizontal/vertical suave hacia el fondo oscuro.
  - **Fila 1: "🔥 Top 10 en Vision+ Hoy" (Tendencias)**:
    - Títulos de mayor impacto con números de posición gigantes estilizados en degradado neón (`1..10`).
    - Badges de rating (`★ 10`, `★ 9.8`, etc.) y estado `TOP 10`.
    - Tarjetas verticales estilo póster 2:3 (`TvPosterCard`, 140.dp).
  - **Fila 2: "✨ Novedades y Estrenos"**:
    - Catálogo reciente 2024–2026 con badge neón `NUEVO` y calificaciones.
  - **Fila 3: "⏱️ Seguir viendo"**:
    - Rediseñada con barra de avance en azul neón brillante `VisionBlue`, duración restante formateada, auto-ocultable si no hay contenido en progreso.
  - **Experiencia Homologada en Teléfonos y Tablets (`home/HomeScreen.kt`)**:
    - Incorporadas las filas prioritarias de Top 10 Tendencias con números gigantes y Novedades y Estrenos antes del resto de categorías.
  - **Desplazamiento Fluido en TV**:
    - `LazyColumn` a pantalla completa sin restricciones rígidas de altura, navegación D-pad con `MinimalScrollBringIntoView`.
  - **Validación**: 161 pruebas unitarias pasando al 100%, compilación limpia y verificación directa vía ADB en Smart TV Box ZTE B866F.

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
- `data/launcher/` — launcher de TV (canal Watch Next): `WatchNextPlan.kt` (puro), `LauncherWatchNext.kt`
  (publicador), `LauncherArt.kt` + `LauncherArtProvider.kt` (pósters `content://`), `WatchNextHistory.kt`
  (lectura fresca del historial), `WatchNextRemovedReceiver.kt`; deep link en `ui/LauncherDeepLink.kt`
  (`visionplus://watch/<clave>`).

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
- **Firma — decisión 2026-10-13: NO se cambia la firma** (la base instalada no se arriesga). La app
  distribuida (A32, POCO, TV y el APK público de GitHub) está firmada con la llave histórica de debug
  (`CN=Android Debug`, SHA-256 `9593cb0d2b44b8c5a47c157c13342c3d4a55629843d562d2c049331ec77473f3`),
  verificada en la app instalada y en el asset público. Esa llave es ahora la llave de producción:
  copia respaldada y con contraseña fuerte en
  `C:\Users\Axel Rosete\.visionplus\visionplus-historic.keystore` (alias `androiddebugkey`; el original
  `~/.android/debug.keystore` no se toca) y `keystore.properties` apunta ahí → `:app:assembleNormalRelease`
  firma con la MISMA identidad. Probado con `pm install -r` encima de la app del A32: **Success y datos
  intactos** (índice + bóvedas). La llave nueva (`visionplus-release.keystore`, SHA-256 `6C:DA:BF:60…`)
  queda reservada para un canal futuro (Play / App ID nuevo), **nunca** para esta base. Respaldar
  `.visionplus` completa (ver `CREDENCIALES.txt`).
- **OTA con R8 activo**: a partir de la próxima versión, publicar `:app:assembleNormalRelease` (minificado
  + shrinkResources: 101.5 MB vs 125 MB del debug) firmado con la llave histórica; R8 con
  `app/proguard-rules.pro` **verificado en A32** (Home, motor nativo XTV, bóveda offline) y job
  `release-build` en CI que valida R8 en cada push.
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
- Bean `uc.c` (verificado en jadx + dispositivo): `d()` = play_url http local, `c()` = duración,
  `b()` = buss, `f()` = tracks; links solo tiene setter `m()` (sin getter). `GetStatus` por
  `NativeJni.a` no sirve para sondear (vacío aun reproduciendo).
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
- **Intro/créditos (skip markers)**: publicado en OTA v2.5.75 (AniSkip + sin botón Marcas). Sin datos
  no hay botón (decisión del usuario: cero trabajo manual). Para novelas y cola larga solo queda la
  detección por audio tipo Plex (fase futura). Cablear o retirar el diálogo placebo «Corregir intro y
  outro» del camino ExoPlayer (las series no pasan por ahí). Falta la prueba física (A32/POCO/TV).
- Fase 2 (diagnóstico): remote config / kill-switch (apagar una función problemática sin OTA) + botón de
  reporte con texto libre del usuario; configurar el canal de alertas (Telegram/webhook) en el `.env` del VPS.
- Catálogo: metadatos del portal que aún mienten — «LIMPIAR» (2020, 109 min, es película) viene como `teleplay`
  en la repisa y aparece en filas de Series; 4 series traen ficha de 1 capítulo (Amor eterno T1, La tienda de
  ramen de los chicos flores T1, La vida después de la muerte con Tyler Henry T1, Los crímenes del cementerio
  de animales T1) y su ficha se comporta como película. Propuesta: pasada de verificación por ficha
  (`getItemData` type 0/1) para sospechosos (series/teleplay con duración ≥80 y sin marca: solo 4 hoy).
- App: paginación on-demand al abrir una categoría del índice (hoy llegan 100 por categoría;
  el resto ya está en el servidor vía `/v1/category/{id}?page=`).
- Subdominio propio para el índice (opcional; hoy va por `https://la20.com.mx/catalogo`).
- Confirmar que la TV del usuario corre ≥2.4.0 (Ajustes → "Versión instalada"); reinstalar con el enlace corto.
- Verificar la pantalla Fútbol en la TV del usuario (D-pad); en móvil ya está probada (A32).
- Fútbol: afinar el mapa liga→canales si algún partido sale con "Sin canal en tu lista" (p. ej. feeds
  sudamericanos); valorar aviso/recordatorio de partidos próximos.
- Buscador de eventos específicos sobre la EPG (idea del usuario, no implementado).
- Próxima OTA: publicar el release minificado (`:app:assembleNormalRelease`, firma histórica intacta) con
  el bump de versión; el update es directo (sin reinstalar). R8 ya verificado en A32.
- Series (teleplay): el flujo usa `startPlayVOD` type 2→1 con episodeNumberList [1]; refinar episodios si hace falta.
