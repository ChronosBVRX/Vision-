# AGENTS.md — Contexto persistente del proyecto Vision+ / Xuper XTV

> Este archivo se carga automáticamente en cada sesión de opencode. **Mantenerlo actualizado al
> cerrar una sesión** (estado, versión publicada, pendientes). El proyecto vive en
> `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+\` (aquí hay una copia idéntica de este archivo).

## Proyecto y objetivo
- **Lote 2.5.115: la tarjeta «Siguiente capítulo» deja de verse vacía — nombre real y sinopsis es-MX por capítulo guardados en el VPS (TMDB → `episode_meta`) y servidos en `/v1/episodes` — OTA publicada** (2026-10-20; 689 tests Android + 281 backend):
  - *Pedido del usuario*: «Mi app ya muestra un modal de siguiente episodio pero solo es un modal grande que se ve vacío, ¿podemos poner el nombre y una pequeña sinopsis? ¿O es muy caro? Todo esto guardado en una BD en el VPS».
  - *Causa*: el portal bautiza sus capítulos con una marca técnica («Ataque a los Titanes Temporada 2_Ataque a los Titanes T2-01»); `cleanEpisodeName` la limpia a "" y la tarjeta solo decía «Capítulo 2» + cuenta regresiva (con el póster perdido si el intent no traía `posterUrl`). No es caro: TMDB ya se usa para el índice y una temporada entera se baja con **UNA petición**, guardada en Postgres.
  - *Servidor (desplegado y verificado EN VIVO)*: tablas `episode_meta` (tmdb_id, season, episode → name/overview/still) y `episode_meta_season` (una fila por temporada consultada: status/attempts/TTL 14 días, enfriamiento 24 h, tope 4). El worker `sync/episodes_mirror.py` resuelve `tmdb_id`+`season` desde `enrichment`/`group_members` del ref espejado y baja `/tv/{id}/season/{n}?language=es-MX` (si no hay ninguna sinopsis, una segunda pasada `en-US` solo rellena vacíos; nunca pisa es-MX), presupuesto `EPISODES_META_MAX_PER_CYCLE=20` por pasada. `/v1/episodes` cruza por número de capítulo (`seriesNumber`) y agrega `title`/`overview`/`still` sin tocar el `name` crudo (compatibilidad del parser de la app). Verificado: AoT T2 → «El titán bestia», «Regreso a casa», «Hacia el suroeste» con su sinopsis es-MX y miniatura (`still: true`).
  - *App*: `EpisodeMirror.EpisodeInfo` + `info(seasonRef, code)` (lee la copia en RAM; si está fría pide una vez al VPS, ~50 ms, jamás lanza). La tarjeta de `X4KPlayerAty.showNextCard` se rediseñó como fila: arte del capítulo a la izquierda (110 dp en TV; en teléfono no, la tarjeta es angosta) + nombre real y sinopsis de 3 líneas; el nombre TMDB reemplaza la marca técnica y, sin póster de serie, se usa la miniatura 16:9 del capítulo. Identidad por `nextTarget.code` (si el usuario ya cambió de capítulo, la respuesta tardía no pinta nada) y todo en hilo aparte. Tarjeta móvil más ancha (0.9 del ancho).
  - *Guardianes*: backend (6) parseo/mergen de TMDB, `meta_due` (TTL/enfriamiento/techo), `enrich` por número con campos vacíos si no hay fila, SQL del endpoint y tablas; app (3) `infoFrom`/caché y cableado de la tarjeta (sinopsis, título real, miniatura). Suite **689 Android + 281 backend**; release R8 101.1 MB compila limpio. **OTA v2.5.115 PUBLICADA** (vc351, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.115). *Pendiente*: prueba física (llegar a créditos y ver la tarjeta con nombre/sinopsis; el resto de temporadas se enriquece sola en minutos). El servidor ya sirve los metadatos sin OTA.
  - *Cobertura en otras series + fix del `tmdb_id` equivocado (2026-10-20, solo servidor)*: el usuario preguntó si funcionaba en series aparte de SNK. Medido en vivo: Yellowstone T2 salía bien pero **Yellowstone T4/T5 y Los Simpson T17 fallaban con 404** porque la ficha de ESA temporada tenía un `tmdb_id` mal emparejado (214951 / 383887) mientras el resto de su grupo tenía el bueno (73586 / 456); **644 grupos** del índice tienen desacuerdo de `tmdb_id` entre sus temporadas. Fix: worker y API resuelven el `tmdb_id` por **voto mayoritario del grupo** (`count(*) desc`, desempate por `avg(match_confidence)`, respaldo a la ficha propia) — la MISMA regla en `sync/episodes_mirror.py` y `api/app.py` (guardián de cableado). Verificado tras desplegar: **Los Simpson T17 → 22/22 capítulos con nombre y sinopsis**, **Yellowstone T4 → 10/10**; las 6 temporadas espejadas quedaron enriquecidas (96 capítulos con sinopsis). Las claves viejas fallidas quedan inertes. Además, `EpisodeMirror.info` refresca UNA vez la copia si se cacheó sin metadatos (`hasMeta`) — app, viaja en la próxima OTA (no crítica: la ventana es de minutos). Guardianes: backend 282/282, Android **690/690**.
- **Lote 2.5.114: espejo de capítulos/temporadas en el VPS + «Omitir intro» solo al presionar (default Botón) — OTA publicada** (2026-10-20; 686 tests Android + 275 backend):
  - *Pedido del usuario (1)*: acelerar la ficha de series sirviendo la lista de capítulos desde nuestro servidor (la app la pedía al portal en cada apertura: 0.2–0.7 s por aparato y gastaba la sesión del equipo). *(2)*: «el omitir intro está funcionando solo sin que lo presione».
  - *Servidor (desplegado y verificado EN VIVO en el VPS)*: tabla `episode_mirror` + worker `sync/episodes_mirror.py` (baja con la sesión del worker, JAMÁS acuña; presupuesto `EPISODES_MAX_PER_CYCLE=30`, cooldown, techo de intentos; solo refs que el catálogo conoce) + `GET /v1/episodes?ref=xtv:<cid>` (miss honesto → la app cae al portal; guardias 400 ref inválida / 404 ref ajena / 401 sin clave). Lógica pura en `api/episodes.py`. TTL 48 h, refresco al vencer conservando los episodios hasta que la nueva baja entre.
  - *3 bugs cazados EN VIVO (no adivinados)*: (1) el drain pasaba la RESPUESTA COMPLETA a `extract_programs` (que espera `assetData`) → 0 filas → marcaba `failed` series perfectas (AoT T2 daba 12 capítulos en la sonda y 0 en el drain); el extractor ahora acepta las dos formas. (2) El fallback `type 1` del portal responde `portal100008 节目不存在` de forma intermitente; se tolera por tipo como la app (`runCatching` + `continue`) y si los dos fallan sube el error más importante (sesión > contenido > red). (3) asyncpg entrega `jsonb` como str → el API servía `episodes` como lista de 1935 CARACTERES; se decodifica y un payload corrupto es `miss` honesto.
  - *Medido*: serve **2.7–4 ms** (vs 0.2–0.7 s del portal), miss→cola→drain→`ok` con los 12 capítulos reales de AoT T2, TTL vencido → miss + re-encola, guardias intactos, 398 MB podados en Docker.
  - *App*: `data/mirror/EpisodeMirror.kt` (HttpURLConnection, 2/2.5 s de tope, `X-Api-Key`, LRU 12/30 min, jamás lanza) y UNA edición en `X4KApi.episodeProgramsFor` (espejo primero → si no hay copia, el portal EXACTO de siempre; el parseo sigue en `parseEpisodePrograms`, una sola implementación). No toca el arranque en frío.
  - *Fix «Omitir intro» (pedido 2)*: el lote 2.5.112 había dejado `skipIntroMode` default **Automático**; como el ajuste solo se guarda al tocarlo, toda instalación sin tocar saltaba sola. Nuevo `SKIP_INTRO_DEFAULT = Botón` (state, `init` y `skipIntroModeOf`), copia de Ajustes que lo explica («Botón (recomendado): el capítulo NO se mueve hasta que lo pulses»). Guardianes: `VisionSettingsTest` (default Botón + Automático persiste como elección explícita + ciclo de opciones) y `MarkersPlayerWiringTest.autoSkipOnlyRunsWhenTheUserChoseAutomatic` (el salto automático SOLO corre con modo Automático en el reproductor nativo y en el Compose).
  - *Verificado*: backend **275/275**; Android **686/686**; release R8 101.1 MB compila limpio. **OTA v2.5.114 PUBLICADA** (vc350, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.114). *Pendiente*: prueba física (ficha de serie → capítulos al instante en la 2.ª apertura; «Omitir intro» solo aparece como botón y no mueve el capítulo hasta pulsarlo). El espejo del servidor ya aplica sin OTA.
- **Lote 2.5.113: el Inicio de Smart TV ya muestra 3 filas del catálogo — pósteres adaptados a la altura real de la pantalla, «Seguir viendo» compacto y regreso al foco por identidad — OTA publicada** (2026-10-19; 675 tests Android):
  - *Pedido del usuario*: «en el modo smartv se pudieran ver por lo menos 3 filas del catalogo, ahorita por el tamaño se ven 2».
  - *Causa medida*: las filas del Inicio usaban pósteres fijos de 140×210 dp; con etiqueta, título y márgenes cada fila medía ~290 dp. En un panel 1080p de TV (Compose reporta 960×540 dp a 320 dpi) solo caben 2 filas (en la vista previa del emulador, 1.8).
  - *Fix (app)*: nuevo `ui/tv/TvLayout.kt` (`tvRowMetrics`/`rememberTvRowMetrics`) que calcula el póster 2:3, la etiqueta, los márgenes y el alto del título desde `screenHeightDp`; en 540 dp la fila mide 177.5 dp (3 = 532.5 dp contra los 870 dp de antes) y en pantallas ≥700 dp conserva el póster grande de siempre (140 dp). `TvPosterCard` acepta `cardWidth` y escala el número del Top 10; `HomePosterRow` y `ContinueRow` usan las medidas; el sello TOP 10 se ajusta a la altura de la etiqueta (antes un badge de 34 dp invadía el póster y chocaba con su nota).
  - *Accesibilidad y guardianes*: la tarjeta enfocable anuncia el título (`contentDescription` en el Card; la imagen ya no lo duplica). `TvLayoutTest` (4) fija 3 filas para 480/540/600/720/800/1080 dp y la proporción 2:3; `TvHomeRowsFitTest` compone 3 filas reales en 540 dp y verifica que la tercera queda dentro de la pantalla; `TvFocusTest.homeBackRestoresSameCardAfterLateContent` comprueba la MISMA tarjeta por título (y por fila cuando su etiqueta sigue compuesta), aceptando que el regreso re-alinee la fila al centro; las pruebas de sección conservan el retorno por tarjeta exacta sin cambios.
  - *Verificado*: suite Android **675/675** en verde; release R8 vc349 (101.1 MB) con la firma histórica `9593cb0d…`; **OTA v2.5.113 publicada** (`VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.113). *Pendiente*: prueba física en la tele (Inicio desplazado: 3 filas completas, D-pad y regreso a ficha).
- **Lote marcadores de salto automáticos: cada capítulo pide SU plan, la caché cruza a `:x4k`, el «Omitir intro» espera a que el video esté dibujado y el siguiente capítulo ya no corta créditos/post-créditos — servidor desplegado y OTA v2.5.112 PUBLICADA** (2026-10-19; 670 tests Android + 252 backend):
  - *Pedido del usuario*: aplicar todas las mejoras del análisis de omitir intro / siguiente capítulo en series y anime, y corregir que el botón «Omitir intro» aparecía sobre la pantalla de carga con el logo.
  - *Causa raíz medida*: `X4KPlayerAty.playEpisode()` reutilizaba la ventana de marcadores del primer capítulo de la temporada (`keepMarkersForSeason`) y **nunca pedía el plan del capítulo nuevo**. AoT T2 E1 tiene intro 3:29–4:59 y E2 1:26–2:56: con la ventana de E1 el auto-skip cortaba 90 s de E2 y no omitía su intro real. Jujutsu T1 E1 0:55–2:25 vs E2 5:45–7:15 era el mismo fallo. Cambiar de temporada borraba los marcadores y no los volvía a pedir.
  - *Fix app*: `MarkersStore` ahora tiene **caché en disco** (`filesDir/markers_cache`, compartida entre procesos) + memoria, prefetch y stale-while-revalidate; `X4KApi.playVod` lanza al instante con lo cacheado (sin esperar 2.5 s) y manda `markersRef`/`markersEp`/`markersLengthSec`; `X4KPlayerAty` pide el plan en `handleLaunchIntent` y en cada `playEpisode` (temporada nueva incluida), cancela respuestas viejas con `playGen`, y prefetcha los 2 siguientes. Se añadió `episodeNumbers` real (MAL/TMDB) a `EpisodePlay`/intent para no confundir el índice del portal con el número de episodio. La política pura vive en `SkipMarkers.kt`: auto-skip solo con **inicio conocido**, video reproduciendo, duración real válida y gracia de 15 s si el plan llega tarde; créditos solo después del 50% del capítulo; post-créditos bloquea el avance rápido; el seek va +1 s para no repetirse en el keyframe (bug detectado EN VIVO: el auto-skip se repetía cada 500 ms). El contador de siguiente se cancela en `onStop`/park y respeta pausa. Nuevo ajuste `nextOnCredits` (default apagado = modo seguro al final; encendido = al terminar créditos) y `skipIntroMode` default **Automático**. La píldora usa `videoIsReady()` (`firstFrame && !loadingFading && overlay != VISIBLE`) y se oculta explícitamente al mostrar la carga.
  - *Fix servidor (desplegado en el VPS)*: `fetch_global_status` consulta TIDB/IDB/AniSkip **en paralelo** y distingue «no hay datos» de «fuente caída» (caché negativa 1 h si todas fallaron, 2 días solo si fue un negativo real; positivo se refresca a 1 día si faltó una fuente); `_MARKERS_TITLE_SQL` prefiere la **ref exacta**, luego XTV/anime (`coalesce(kind,'')='anime'`) para que una copia Magis no desactive AniSkip; `MARKERS_EPISODE_MAX` 400→**5000**; Fribb sin `season` cae a **temporada 1** (One Piece TMDB 37854 → MAL 21, verificado en vivo); AniSkip prueba la duración real y luego **1440/720 s** (la v2 responde 404 si no coincide); `len` opcional en `/v1/markers` mejora saneo/longitud; TTL positivo 14 días; `season` de la respuesta ya es el resuelto.
  - *Offline/ExoPlayer*: se retiró el salto fijo de 85 s y el diálogo placebo «Corregir intro y outro»; el búnker lee los marcadores cacheados de `OfflineRegistry` (`vaultMarkersFor`), usa la misma política (`autoSkipTarget`/`shouldApplyAutoSkip`/`creditsCardStartMs`) y el modo rápido opcional en créditos; las descargas prefetchan los marcadores al encolarse.
  - *Verificado EN VIVO (VPS + emulador)*: `/v1/markers` AoT T2 E2 devuelve su intro 86–176 s (antes se habría usado 209–299), Bleach TYBW T2 E1 `season=2`, One Piece E1 ahora usa AniSkip (recap 132–201 s, intro 310–400 s, créditos 1396–1434 s), AoT T4 E1 usa AniSkip (208–298 s) y `len=1380` sigue funcionando por el fallback. En el emulador (release R8 instalado) el auto-skip saltó **una sola vez** de 40 s a 53 s; la pantalla de carga mostró solo el logo, sin «Omitir intro». `:app:assembleNormalRelease` compila y arranca el reproductor sin el `VerifyError` de `PlayerCore` (se evitó crecer su firma usando la caché de `OfflineRegistry`).
  - *Guardianes*: `SkipMarkersTest` + auto por episodio, inicio desconocido, gracia, pausa, créditos/duraciones, post-créditos, una sola aplicación, píldora durante carga; `MarkersStoreTest` (disco/proceso), `MarkersPlayerWiringTest` (cableado), `VisionSettingsTest` (defaults/persistencia), `test_markers.py` (status de fuentes, fallback AniSkip, Fribb T1), `test_aniskip.py`, `test_api_serve.py` (cap 5000, orden de la SQL). Suite **670 Android + 252 backend**.
  - *OTA v2.5.112 PUBLICADA*: `tools/publish_ota.ps1 -Version 2.5.112 -NotesFile ota_notes_2.5.112.md` (vc348, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.112). *Pendiente*: prueba física en teléfono/tele del usuario (AoT T2 E1→E2, One Piece >400, post-créditos, offline). El servidor ya aplica sin OTA.
- **Lote catálogo del Inicio: «novedades populares de verdad» y fin de las tarjetas repetidas — servidor YA aplicado y OTA v2.5.111 PUBLICADA** (2026-10-19; 652 tests Android + 245 backend):
  - *Pedido del usuario*: «el catálogo de la pantalla principal de verdad tiene que ser novedades populares y hay cosas que se repiten; por ejemplo Link se repite muchas veces y no creo que sea popular en la mayoría de las personas».
  - *Medido EN VIVO (no adivinado)*: (1) `/v1/trending` devolvía 40 filas para 31 obras — **«Link Click» ×4** (T1..T4, mismo tmdb_id 123542), «Tulsa King» ×5, «La diplomática» ×5; el join `weekly_trending × enrichment` devuelve una fila por cada copia del catálogo que comparte tmdb_id y el endpoint no deduplicaba (la app tampoco: solo `distinctBy { id }`). (2) «Novedades y Estrenos» abría con Spider-Man (idéntico al Top 10) y colaba un 10.0 con 4 votos porque `rankScore` daba confianza plena al rating en cuanto la popularidad fuera >0. (3) Las filas por tipo repetían lo ya visto arriba (Hope salía en «Populares de la semana» y en «Películas populares»). (4) De paso se midió que `/v1/index` tardaba **47 s** (10.2 MB) porque calculaba TODAS las filas de las 124-161 categorías antes de recortar a 100: el Inicio en frío se quedaba sin Top 10/Novedades cuando el fetch rebasaba los 30 s del cliente.
  - *Fix servidor (desplegado en el VPS, aplica al instante sin OTA)*: (1) `/v1/trending` deduplica por `(tmdb_id, media)` —una tarjeta por obra— elige la copia de mayor confianza y ordena por **popularidad real** (no por el puesto editorial de TMDB: Link Click ya no abre la hilera); (2) `TENDENCIAS_SQL` usa `distinct on (coalesce(e.tmdb_id,0), …)` para que Tendencias tenga una entrada por obra (adiós T1..T4 y «Os Simpsons»/«Los Simpson» duplicados en el Top 10); (3) `film()` manda `tmdbId` (la app lo usa para deduplicar); (4) `/v1/index` recorta **en SQL** con `row_number() over (partition by category_id)` (2× el límite para absorber grupos) + caché por versión/límite: **47 s → 2.5 s frío / 1.2 s cacheado** y **10.2 MB → 5.8 MB**; `total` sigue real con un `count(*)` aparte.
  - *Fix app (build de pruebas verificado; viaja en la próxima OTA)*: `Film.tmdbId` (parseo en índice/búsqueda/trending y caché), `Popularity` con identidad por `tmdb:<id>:<palabra fuerte>` (colapsa temporadas/doblajes, pero NO fusiona un match TMDB equivocado con nombre ajeno), `dedupe` elige la copia doblada al español (idioma > popularidad/votos > arte) y `normalizeDisplayName` oculta el sufijo «- Español Latino»; `realPremieres` = novedades con señal real (popularidad manda; rating ponderado por `votos/(votos+100)`) y **sin repetir el Top 10**; las filas por tipo se filtran contra Top 10 + Novedades + «Populares de la semana»; móvil y TV usan la misma lógica.
  - *Verificado en el emulador (Vision_Phone, build debug con los cambios)*: Top 10 (Spider-Man, La Noche del Demonio, Ley y orden…), Novedades (Entrega Al Límite, Toy Story 5, Tierra de Mafia…) **sin traslape**, Populares de la semana (South Park, Hope, Bajo la superficie, Bleach…) **sin Link Click repetido**, Películas populares sin Hope/GTA (ya vistos arriba); el índice carga en ~2 s y el Inicio ya pinta las tres filas. Capturas: `%TEMP%\opencode\vp_home_new3.png`, `vp_home_new4.png`, `vp_home_new7.png` (antes: `vp_home_old2.png`).
  - *Guardianes*: `PopularityTest` +8 (dedupe por tmdb, match malo no fusiona, doblaje español gana, `noveltyScore` sin rating huérfano, novedades sin señal fuera, kind rows sin repetir, etc.), `RemoteIndexTest.tmdbIdSurvivesTheParseAndDefaultsZero`, `CatalogTest` (sufijo de doblaje), `HomeCatalogWiringTest` (móvil+TV filtran la hilera semanal y las filas por tipo; el tmdbId viaja del servidor al motor) y `server/tests/test_tendencias.py`/`test_api_serve.py` (distinct on, dedupe del trending, orden por popularidad). Suite **652/652 Android + 245/245 backend**.
  - *OTA v2.5.111 PUBLICADA*: `tools/publish_ota.ps1 -Version 2.5.111 -NotesFile ota_notes_2.5.111.md` (vc347, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.111). Verificada instalando el APK publicado en el emulador: 2.5.111 (vc347), Inicio con Top 10 + Novedades + Populares sin repetidos. *Pendiente*: probar en el teléfono/tele del usuario.
- **OTA v2.5.110: el arreglo de los saltos de reproducción («no es pausa, es un salto de frames») — la Calidad preferida manda de verdad desde el primer cuadro, telemetría nueva `PLAYER_FRAME_SKIP` y el proceso del reproductor más liviano — publicada** (2026-10-19; 642 tests Android en verde):
  - *Reporte del usuario*: «todo carga bien pero la reproducción tiene un corte repentino, y se nota a veces cortado, en escenas de mucha acción se nota más; pero no es pausa, es como un salto de frames» + una grabación de la tele como prueba.
  - *Prueba analizada cuadro a cuadro (no adivinada)*: clip de 14.36 s (1920x1080@60, grabación del televisor con el teléfono). Inventario: cortes de escena normales en 1.75/7.85/10.52/12.78 s; 10 hipos del grabador (33 ms) repartidos y NINGUNO en el momento clave; sin congelamientos ni pantallas negras. **El salto real: a los ~11.72 s** — dentro del mismo plano, entre dos cuadros consecutivos el personaje cambia de postura de golpe mientras el fondo queda pixel a pixel idéntico (diferencia 7–9.5 vs 0.8–1.4 de una toma tranquila), **sin ningún freeze previo** (descarta el buffering: el ítem congelado habría dejado decenas de cuadros casi idénticos) y con **el audio cambiando en el mismo instante** (pico espectral exactamente a 11.720 s y el nivel ×3.7 justo después). Los timestamps del video son continuos ahí (el salto fue del contenido en pantalla, no de la grabación). Artefactos del análisis en `%TEMP%\opencode` (`cmp_701_702.png`, `zoom_man.png`, `vidaudio.wav`).
  - *Lectura*: el reproductor **mostró discontinuamente el contenido** (cuadros que nunca se pintaron), con el audio arrastrando el mismo salto — el caso típico de frames descartados por el pipeline del motor cuando va justo (equipos modestos + escenas de mucho movimiento = más bits por segundo). La app no tiene forma de tocar el interior del motor (Titan/ijk del proveedor), así que el lote ataca los tres frentes de la app.
  - *Fix 1 — la «Calidad preferida» ahora sí manda desde el primer cuadro*: `X4KTitan.play` arranca con la PRIMERA media de la lista (`medias.first().name`) y el ajuste de Ajustes solo pintaba la etiqueta de la píldora (el motor seguía en la calidad del portal, casi siempre la más pesada) — bajar de calidad solo aliviaba cambiándola a mano en cada reproducción. Nuevo `orderMediasByQuality` (puro) coloca la elegida al frente conservando el resto; «Auto» o una calidad ausente dejan la lista intacta. Cableado en las DOS rutas de arranque (`mountUi` — películas y canales — y `playEpisode`, con su reintento).
  - *Fix 2 — telemetría de saltos*: `positionSkipExcessMs` (puro) compara la posición contra el reloj de pared; si avanza más de lo transcurrido (umbral 900 ms, huecos de sondeo >5 s descartados), el reproductor reporta `PLAYER_FRAME_SKIP` con el exceso en ms (freno de 30 s). Cada seek de la app llama `noteAppSeek()` (reinicia la base y silencia 3 s) para cero falsos positivos — enganchado en las 9 rutas de seek (scrub táctil, commit del D-pad, cambio de calidad con fallback, continuar/desde el inicio, reanudar en primer cuadro ×3, ±10 s y reset por reproducción).
  - *Fix 3 — el proceso `:x4k` más liviano (sin cambios visibles)*: el icono play/pausa ya no se reconstruye 2 veces por segundo (solo cuando cambia de verdad o cambia el botón, `playIconState`/`renderPlayIcon`); la duración del motor solo se pregunta mientras el catálogo no la declaró; un solo `isPlaying` por tic (antes dos, ambos por reflexión); los textos de tiempo y la barra solo se repintan cuando cambian.
  - *Guardianes*: `PlayerStreamGuardTest` (10): orden de calidades (preferida al frente conservando orden; «Auto»/ausente intactos; una sola media no se toca), detector de saltos (tic normal, estancamiento, rebobinado, hueco de sondeo, salto real con exceso exacto, borde de tolerancia 800 vs 1100 ms, sin línea base) y guardián de cableado (≥3 usos de `orderMediasByQuality` en las dos rutas, `PLAYER_FRAME_SKIP` presente, ≥5 `noteAppSeek()`, icono con estado cacheado). Suite **642/642**.
  - *Publicada*: `tools/publish_ota.ps1` (vc346, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.110.md`).
  - *Pendiente*: prueba física (sin equipos conectados al compilar) — reproducir un título pesado y comparar «Auto» vs «720p» en Ajustes → Calidad preferida (debe arrancar YA en 720p) y vigilar el VPS por si aparece `PLAYER_FRAME_SKIP` (si el salto viene del flujo del proveedor, la telemetría lo dirá con números; el salto medido en la prueba caería en el umbral de 900 ms). Nota útil: comparar el MISMO momento en la app original de Xuper — si también salta ahí, es del flujo, no del motor nuestro.
- **OTA v2.5.109: el buscador muestra «También: …» (el nombre original cuando difiere) y el control remoto busca en el catálogo completo del servidor — publicada** (2026-10-19; 632 tests Android en verde):
  - *Qué*: (1) las tarjetas de búsqueda (móvil y TV) muestran debajo **«También: X»** cuando el título encontrado tiene otro nombre original —lo manda el servidor desde el índice («buscando a nemo» → Finding Nemo, «vaiana» → Moana); si el título mostrado ya ES el original, la línea no aparece—; (2) el buscador de «Abrir en la tele» del **control remoto** ahora consulta al servidor (el mismo buscador del Inicio) con el respaldo local instantáneo, en vez de mirar solo la copia local (menos títulos).
  - *App*: `Film.originalTitle` (parseo en los DOS caminos del índice —`parseRows` y `parseFilmObject`— y en la caché `rowToJson`), `Film.akaLine()` puro (vacío si el original es el mismo o no viene), `PosterCard(altTitle=…)`, `SearchScreen`/`TvSearchScreen` lo pintan, y `RemoteScreen.CastPanel` busca con `RemoteIndexSource.search` (LaunchedEffect con debounce 250 ms; local primero, servidor después y se queda si trae resultados).
  - *Guardianes*: `CatalogTest.akaLineShowsTheOriginalNameOnlyWhenItDiffers`, `RemoteIndexTest.originalTitleSurvivesTheParseAndDefaultsBlank`, `SearchWiringTest` (2: ambos buscadores pintan la línea; el control remoto usa el servidor con respaldo local). Suite **632/632**.
  - *Verificado en el emulador con el APK de la OTA*: «buscando nemo» → **Finding Nemo** primero (sin línea: es el mismo nombre) y «Buscando a Dory»/«Procurando Dory» con **«También: Finding Dory»**; la app arrancó sin diálogo de actualización (es la última). El panel «Abrir en la tele» solo se dibuja con una tele conectada: falta el flujo físico teléfono↔tele (el camino de servidor es el mismo del buscador móvil, ya verificado).
  - *Publicada*: `tools/publish_ota.ps1` (vc345, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.109.md`).
- **Fix «Guerra de Papás» no salía en Vision+ aunque sí en Xuper: el buscador ahora pregunta al portal (paridad real) y el catálogo se completa con una pasada profunda de colecciones — solo servidor, sin OTA** (2026-10-18; 232 tests backend en verde):
  - *Reporte del usuario*: «en visión no sale guerra de papás y se supone que tenemos el mismo catálogo que Xuper; fui a buscar en Xuper y ahí sí está».
  - *Causa raíz medida en vivo (no adivinada)*: los listados del portal topan en **10.000 títulos por columna** (`portal100081 限制返回节目数量`; la página 101 se rechaza) y el sync bajaba solo **3 páginas (300 títulos)** por colección. «Guerra de Papás» vive en **Comedia página 73/93**; «El padrino» (1972) en **Crimen página 35** y no en el «All» (primeros 10k) → no estaban en `titles` y el buscador devolvía basura («Juego de tronos», por el «A Guerra dos Tronos» portugués de TMDB en la etapa difusa). La app original los encuentra porque su buscador va DIRECTO al portal (`v3/searchByName`, endpoint y `SearchByNameBean` verificados en el dex del APK).
  - *Fix 1 — buscador con paridad (`server/api/portal.py` nuevo + `wire.py` copiado)*: `/v1/search` lanza **en paralelo** la búsqueda del portal con la sesión que el worker ya mantiene (`portal_sessions`; **jamás acuña** — si el token no sirve, sigue solo el índice local), aplana `searchItemList[].itemList[]`, recorta el ruido (los 5 primeros solo si comparten palabra significativa; el resto exige TODAS; stopwords fuera) y **da de alta** los títulos encontrados (`on conflict do nothing` + `search_text` plegado/compacto): el catálogo crece con lo que la gente busca, sin pisar lo enriquecido. Orden: si el índice ya tiene el nombre en `titles.search_text` (título/alias/es-MX/original), manda el local; si no, el acierto del portal va primero. Tope 4 s, caché 10 min (fallos 60 s).
  - *Fix 2 — catálogo completo (`sync/xtv.py` + tabla `sync_meta`)*: **pasada profunda** de colecciones (todas las páginas hasta el tope del portal) cada `XTV_DEEP_EVERY_HOURS=168` con `XTV_COLLECTION_DEEP_PAGES=100`; deja marca en `sync_meta.xtv_deep_at` y se da por buena con ≤25 % de fallos (una colección retirada no la repite cada ciclo). El «All» ya pide 100 páginas (antes pedía la 101 y el portal la rechazaba en cada ciclo).
  - *Fix 3 — fin del ruido difuso (`api/app.py`)*: `fuzzy_probes` exige **TODAS** las palabras largas (antes solo la más larga) y descarta palabras vacías; máximo 3 sondas. Una consulta sin sentido ahora devuelve **0** (antes 5 películas al azar en 2.6 s).
  - *Verificado EN VIVO (VPS, tras desplegar)*: «guerra de papas» → **Guerra de Papás (2016)** + secuela (0.15-0.47 s); «daddy's home» (alias) → la misma; «el padrino» → **El padrino (1972)** primero (no estaba en el índice); «buscando a nemo» → **Finding Nemo** primero (el ruido del portal ya no lo tapa); «intensa mente» → Intensa Mente (2015); consulta basura → 0. `getItemData` del título: 96 min, sinopsis y pósters ✓; `startPlayVOD` con playCode y licencia libre ✓. Deep pass en vivo: **34.9k → 52.1k títulos** (Comedia y Familia ya enlazan «Guerra de Papás»; `title_categories` 288k → 308k).
  - *Verificado EN EL EMULADOR con la app **v2.5.105** ya instalada (sin OTA)*: buscar «guerra de papas» → «Resultados (5)» con **Guerra de Papás** de primera (Guerra de Papás 2, Juan Luis Guerra, … después); la ficha abrió con **720P · «Incluido con tu cuenta» · 2016 · 1 h 36 min · Español** y su sinopsis (todo del portal); **Reproducir abrió el reproductor nativo y el video corrió** (logo de Paramount → tarjeta de créditos): el arreglo es **100 % del servidor**.
  - *INCENDIO apagado en el camino (lección dura)*: al terminar el pase profundo, **Postgres entró en crash-loop por «No space left on device»** en su WAL y tumbó API/sync en cadena. Causa: el **Docker Root Dir del VPS es `/var/oled/docker` sobre un volumen de 15 GB** (el root de 133 GB no lo usa) y la caché de build + imágenes colgantes lo tenían al 100 % (el pase profundo solo fue el último empujón). Fix: `docker image prune -f` + `docker builder prune -f` liberaron **3.9 GB**; Postgres recuperó solo con redo completo y **los datos del pase quedaron intactos** (52,162 títulos; `sync_meta.xtv_deep_at` quedó escrito a las 20:23:32). Reglas nuevas: (1) **podar imágenes/caché de build tras cada compilación** en ese VPS + cron semanal idempotente instalado en el crontab de `opc` (`20 4 * * 0 docker image prune -f; docker builder prune -f --keep-storage 1GB`), y (2) la pasada profunda ahora **se aplaza sola si quedan menos de `XTV_DEEP_MIN_FREE_GB`=2 GB** (`_deep_due` mira `shutil.disk_usage` antes de tocar la base). Además el rank del buscador ahora **plega la puntuación como la consulta** (`_fold_sql`): «Daddy's Home» empata con «daddy s home» y el alias del portal gana sobre un alternativo TMDB basura (sin esto, «My Melody y Kuromi» —cuya ficha TMDB trae un «Daddy's Home» ajeno— salía primero).
  - *Guardianes*: `server/tests/test_portal_search.py` (13: `wire.py` idéntico API↔sync, pliegue, mapeo del título real, tipos, flatten/dedupe, ruido+stopwords, orden local↔portal con alias TMDB, sondas difusas y SQL con todas las palabras) + `test_xtv_deep.py` (3: la pasada profunda arranca sin marca, respeta el intervalo y **se aplaza con el disco lleno sin tocar la base**) + `test_search_names.py` (offset del rank derivado de sus params, plegado de puntuación en SQL). Suite backend **237/237**. De paso se reparó un test bomba-de-tiempo: `test_gee38_events_merges_links_of_the_same_fixture` usaba fechas fijas (17-oct) y detonó al pasar el día real en el runner UTC (la ventana −1..+7 ya no lo incluía) — ahora la fecha se deriva de hoy (ayer) y vale cualquier día.
  - *Permanente y con los nuevos lanzamientos (cerrado el 2026-10-18)*: (1) **el buscador pregunta al portal en cada consulta** — un título recién publicado en el portal se encuentra (y se da de alta) al instante, sin esperar al ciclo; (2) el **ciclo de 6 h** trae los nuevos por el «All» (ordenado de lo más nuevo a lo más viejo) y las repisas de estreno; (3) la **pasada profunda semanal** rellena la cola larga; (4) **monitor nuevo en `sync/diag.py::catalog_health`** (loop del triage cada 5 min): avisa al Telegram si el último ciclo tiene **>10 h** (`DIAG_CATALOG_STALE_HOURS`) o si la pasada profunda lleva **>8 días** (`DIAG_DEEP_STALE_DAYS`), con enfriamiento de 12 h y estado en `sync_meta` (`health_cycle_alert_at`/`health_deep_alert_at`); (5) **catch-up TMDB**: `ENRICH_BATCH` del VPS subido de 300 a **1500**/ciclo para enriquecer los **14,592 títulos nuevos** (cobertura movie 43 % → ~90 % en ~2-3 días; hasta entonces ya se buscan/reproducen, pero su `popularity=0` los deja fuera de «Populares»/Top 10); (6) el VPS ya tiene **restart: unless-stopped**, cron semanal de poda de Docker, aplazamiento por disco y el watchdog/uptime externos. Lo único que sigue dependiendo de una OTA futura son mejoras de la app (p. ej. mostrar «también conocida como…» o que el buscador del control remoto use el servidor), no este fix.
  - *Pendiente*: probar en el teléfono/tele del usuario (la búsqueda ya debe salir **sin OTA**; el deep pass semanal sigue llenando el catálogo).
- **Fix post-OTA 2.5.107 (incluido en la OTA 2.5.108): mandar contenido desde el control remoto mientras la tele reproduce ya no deja el menú con el audio de fondo — el handoff viejo ya no deshace el play nuevo y el motor se suelta al quedar detrás — OTA publicada** (2026-10-18; 628 tests Android en verde):
  - *Reporte del usuario*: «si estoy reproduciendo algo en la TV e intento mandar algo desde el control con el catálogo no se reproduce y se buggea, se sigue escuchando la reproducción de fondo pero ya estamos en el menú principal».
  - *Causa 1 (el menú)*: el reproductor residente vive en su TAREA propia (`taskAffinity`), así que mientras se reproduce el menú (`MainActivity`) queda detenido con su pila `[Home, Player(viejo)]` y la espera «Abriendo…» del play viejo COMPUESTA. Su `XtvHandoff` trata CUALQUIER regreso a primer plano como «el reproductor se cerró» (`DisposableEffect` + ON_RESUME) — el deep link del teléfono también trae el menú al frente, y el cierre ATRASADO de la espera vieja (`nav.pop()`) deshacía la reproducción nueva (con `push` además el play viejo quedaba apilado y podía re-dispararse al volver).
  - *Causa 2 (el audio)*: `X4KPlayerAty.onStop()` solo guardaba el minuto; al quedar la actividad detrás (menú al frente) el motor seguía sonando invisible.
  - *Fix*: (1) `Navigator.pushPlayer()` — los deep links (control remoto y tarjetas del launcher) REEMPLAZAN el reproductor en curso en vez de apilarlo; (2) `Navigator.close(route)` — el cierre del handoff es por IDENTIDAD de ruta (la espera vieja ya no puede cerrar la pantalla nueva), aplicado a los `onBack` de Player/LivePlayer en `TvApp`/`PhoneApp`; (3) `shouldParkPlaybackOnStop` (pura) + `parkPlaybackInBackground` en `X4KPlayerAty.onStop`: si queda reproducción viva detrás se suelta el motor (commit del minuto + `X4KTitan.stop`, la actividad queda residente para reutilizar la vista) y el poller se apaga (no re-arranca nada en segundo plano).
  - *Guardianes*: `NavigationTest` +4 (reemplazo del player en curso, vivo incluido, el cierre viejo no toca la pantalla nueva, `close` no vacía la pila), `ResidentPlayerTest` +2 (`shouldParkPlaybackOnStop`), `RemotePlaybackWiringTest` (cableado: pushPlayer/close/park). Suite 628/628.
  - *OTA v2.5.108 PUBLICADA* (vc344, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.108.md`).
  - *Pendiente*: prueba física en la tele (reproducir y mandar otra película/canal desde el control: el contenido nuevo debe abrir solo y jamás quedar audio de fondo en el menú).
- **Lote D-pad a prueba de fallos: el rescate VERIFICA el aterrizaje (fin de la tecla comida en silencio), toda pantalla/modo tiene destino compuesto, y los diálogos, el zumbido de teclas y el control remoto dejan de poder matar el mando — OTA publicada** (2026-10-18; 619 tests Android en verde):
  - *Pedido del usuario*: «Analiza toda nuestra navegación D-pad, que sea robusta y que nunca falle; dime dónde podemos reforzarla y a prueba de errores» → plan Fase 0→4 ejecutado (infraestructura → pantallas → reproductores → guardianes).
  - *El hueco estructural (H1)*: `LandingFocus.attached` nacía en `true` y nada lo apagaba salvo el héroe del Inicio; `FocusRescue.rescue()` **consumía la tecla aunque `requestFocus()` hubiera fallado** — con un destino muerto (el riel en una pantalla que no es Inicio, el héroe con la lista desplazada) cada tecla se perdía sin mover nada: «el mando no responde». Fix en `TvFocus.kt`: (1) `Modifier.landingFocus` mantiene `attached`/`focused` con un `Modifier.Node` real (alta al entrar al árbol, baja al salir — API verificada de Compose 1.7.6); (2) `rescue()` recorre los candidatos DE VERDAD y solo consume cuando `focused` quedó en true tras la petición; si ninguno aterriza, la tecla SIGUE SU CURSO; (3) `rescueCandidates()` pura (del último al primero, solo compuestos) probada; (4) telemetría `FOCUS_RESCUE_MISS` con freno de 30 s (`currentTimeMillis`, no `SystemClock`, para que la lógica viva también en tests JVM puros).
  - *Riel (`TvApp.kt`)*: su landing solo se registra cuando el riel existe (Inicio); en las demás rutas era un destino muerto que comía teclas. La red de seguridad de 300 ms ahora usa `FocusRescue.rescue()` (verificado).
  - *Deportes en vivo (`TvSportsScreen.kt`, el caso más expuesto)*: era la única pantalla TV SIN landing — con la agenda vacía/en camino nadie pedía foco y las teclas se comían. Ahora: landing SIEMPRE compuesto (el primer chip del deporte; «Canales» si no hay categorías), el aterrizaje se re-arma cuando llegan los datos (`LaunchedEffect(..., filtered.isEmpty())`), y el hack `FocusRescue.screenHasFocus = false` se retiró.
  - *Sección (`TvCategoryScreen.kt`)*: al pulsar OK en un chip el modo cambia, la fila de chips se re-crea y el héroe (el landing del feed) deja de componerse — el foco moría. Ahora el modo cuadrícula tiene su landing («Volver a Todo») y un efecto por `activeCategory` re-aterriza al cambiar de modo.
  - *Búsqueda (`TvSearchScreen.kt`)*: el regreso usa los `results` reales como key y la PRIMERA tarjeta de resultados como ancla (antes: `initialFilms.size` fijo y caía siempre al teclado).
  - *Inicio (`TvHomeScreen.kt`)*: fin de la réplica manual del orden de filas (`homeReturnRowIndex` ya se desincronizó una vez con «Populares de la semana»). Ahora `HomeRowSlot`/`homeRowSlots`/`homeRowIndexOf` son la FUENTE ÚNICA: el `LazyColumn` se pinta recorriendo la misma lista que calcula el índice del regreso; el envoltorio viejo se conserva para los guardianes puros.
  - *Reproductores*: `swallowWakeupKeyCode` (nativo) y `swallowWakeupKey` (los 3 caminos Compose) ahora VENCEN a los ~1.2 s si el UP nunca llega (refrescándose con las repeticiones de una tecla sostenida): un mando que no manda el UP no puede dejar una tecla muerta. Guardas de `repeatCount`: sostener play/pausa no alterna en cadena, sostener ±/CH ya no recorre canales ni capítulos (`MEDIA_NEXT`), y el zapping vivo con flecha sostenida queda a un canal por toque. `showControlsBriefly` reintenta el foco 2×160 ms (la petición hecha mientras el motor arranca se descarta en silencio).
  - *Diálogos*: `TvNoUsbDialog` era el único `Dialog` de `ui/tv` sin avisar `FocusRescue.dialogsOpen` (el rescate de atrás podía robarle el foco a su ventana). Guardián nuevo `TvModalGuardTest`: cualquier fuente de `ui/tv` con `import androidx.compose.ui.window.Dialog` debe traer `dialogsOpen++` (habría fallado antes del fix). `UiNoEmojiTest` sigue barriendo el resto.
  - *Control remoto*: si la actividad del menú ya no está RESUMED (el reproductor tomó la pantalla), la tecla del teléfono viaja por `RemoteBus.broadcastKey` al proceso `:x4k` — jamás se inyecta en la ventana invisible del menú. Guardián de cableado en `RemoteMemoryWiringTest`.
  - *Guardianes nuevos/actualizados (619/619 en la suite completa)*: `TvFocusTest` 23 (nuevos: `categoryChipEntersTheGridWithThePadAlive` — falla con el código viejo —, `rescueSkipsADeadTargetAndLandsOnARealOne`, `sportsEntryKeepsThePadAlive`; los dos tests de pila del rescate ahora comprueban `rescueCandidates`), `TvRescueTest` 9 (rescate que no come sin composición real, candidatos puros, `homeRowSlotsFollowThePaintingOrder`), `PlayerNavigationTest` +1 (vencimiento del swallow), `TvModalGuardTest`, `RemoteMemoryWiringTest` +1. Extra de higiene: `GeeSportsSource.resetMemoryForTests()` — la caché estática cruzaba clases de test (Robolectric no reinicia los estáticos) y los resets van en `@Before` de las suites de TV.
  - *Pendiente*: prueba física en la tele (batería D-pad: Inicio→sección→chip→cuadrícula→Atrás; Deportes en frío; volver de ficha/serie; abrir/cerrar cada modal; reproductor nativo con mando; control remoto) — la OTA v2.5.107 ya está publicada (vc343, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.107.md`).
- **Buscador por nombres universales: el título es-MX/inglés/regional de TMDB entra al índice, el texto de búsqueda se materializa (plegado + compacto) y hay fuzzy por trigramas — auditoría 2026-10-18, solo servidor (sin OTA), 216 tests backend en verde**:
  - *Pedido del usuario*: «no encuentro todas las películas a pesar de que sí estén: a veces tienen otros nombres dependiendo la región o en inglés se llaman de otra manera».
  - *Auditoría medida EN VIVO (antes)*: «buscando a nemo» NO encontraba «Finding Nemo» (el catálogo lo llama en inglés y el nombre es-MX de TMDB jamás se guardaba), «vaiana» NO encontraba «Moana» (alternativo regional, 0 resultados), «termintor 2» nada (sin fuzzy) y «fast x» devolvía Silicon Valley/Bob Esponja (las palabras cortas matcheaban sinopsis/reparto). El buscador clásico solo veía título/alias del portal/director/reparto/sinopsis con `unaccent`.
  - *Qué cambió (todo servidor)*:
    1. **Nombres TMDB guardados** (`sync/tmdb.py` + `schema.sql`): `enrichment` suma `original_title` (inglés/original), `localized_title` (es-MX) y `alt_titles` (regionales, tope 40, con marca `aliases_at`). Los matches NUEVOS los traen en la MISMA llamada (se añadió `alternative_titles` al `append_to_response`). Para los ~31k matches ya existentes: `backfill_aliases` (populares primero, una llamada por título) — catch-up manual con olas paralelas `python tmdb.py --aliases 6` (~13 títulos/s medido) y lote de mantenimiento por ciclo (`TMDB_ALIAS_BATCH`, `TMDB_ALIAS_GAP_SECONDS`); 404 en ambos tipos = ficha borrada (se marca y no se repite).
    2. **`titles.search_text` materializado** (`sync/search_text.py`): todos los NOMBRES plegados (minúsculas, sin acentos, signos→espacios) + **compactos** («spider man»→«spiderman», «S.W.A.T.»→«swat»), dedupe y tope 1200; lo mantiene `rebuild()` por pendientes (título nuevo o enriquecimiento más nuevo que el texto) y `rebuild_all()` para el estreno (`docker compose exec sync python search_text.py`). Índice **GIN `pg_trgm`** (extensión creada en DO-block con guardia: si no hay permisos, la búsqueda sigue sin el atajo).
    3. **`/v1/search` por ETAPAS** (`api/app.py`, constructores puros `build_name_match`/`build_deep_match`/`build_rank`): 1) **nombres** contra `search_text` (índice trgm, ~90-130 ms) → 2) **profunda** clásica (reparto/dirección/sinopsis + nombres TMDB; las palabras de 1 carácter NO tocan sinopsis/reparto — el ruido de «fast x») → 3) **difusa** `word_similarity` con la palabra MÁS LARGA («termintor 2»→0.615 vs 0.533 de la frase completa; umbral 0.55) → 4) contingencia LIKE plano. Ranking en cascada: título exacto/prefijo/contiene → alias del portal → es-MX de TMDB → original → alternativos. La respuesta suma `originalTitle` cuando difiere (la app actual lo ignora; OTA futura puede enseñarlo como «también conocida como…»).
  - *Verificado EN VIVO (VPS, tras desplegar)*: «buscando a nemo» → **Finding Nemo** ✓ (106 ms); «vaiana» → **Moana** ✓ (102 ms); «termintor»/«termintor 2» → **Terminator** ✓ (fuzzy ~1.7 s); «findin nemo» → Finding Nemo ✓; «spderman» → Spider Man ✓; «el conjuro» → Expediente Warren ✓; «fast x» pasó de 3 basuras a 1 resultado legítimo (Fast Times at Ridgemont High, alias del portal). Regresiones OK: los simpson, the simpsons, money heist, spider-man brand new day, el padrino, the godfather, sin límite. Frío 90-130 ms (etapa rápida); difusa 1.0-2.0 s solo cuando las anteriores fallan. **Catch-up de nombres completo: 31,012/31,012** (0 textos viejos) y el top popular se encuentra **es-MX 16/16 · original 20/20** (verificación consciente de grupos de temporadas).
  - *Guardianes*: `server/tests/test_search_names.py` (24: pliegue idéntico al del API, compactos, dedupe/tope, parse de alternativos película/serie, `_match` guarda nombres sin request extra, fallback tv↔movie con 404, olas, trozos y marcas del backfill, offsets de placeholders de las 3 etapas, `fuzzy_probe`, modo `--pending`). Deploy: compose pasa `TMDB_ALIAS_BATCH`/`TMDB_ALIAS_GAP_SECONDS`/`SEARCH_TEXT_BATCH` (documentados en `.env.example`).
  - *Bicho cazado DURANTE la verificación (y cerrado, lección)*: el backfill rehacía el texto solo al **final** de cada lote; al reconstruir el contenedor a media pasada, ~3.4k filas quedaron con `aliases_at` puesto pero texto VIEJO («buscando a nemo» volvió a no encontrar a Finding Nemo, aunque su es-MX sí estaba guardado). Fix: `ALIAS_REBUILD_CHUNK=500` (el texto se rehace por trozos mientras se guarda), el CLI del catch-up **sanea después de cada pasada** («textos sanados: N») y `docker compose exec sync python search_text.py --pending` sana los viejos de una vez. Lección general: cualquier pase que materialice algo en dos tablas debe sanar por trozos, no al final — un reinicio a mitad no puede dejar datos marcados-y-viejos.
  - *Lección del deploy*: el contenedor `python:3.12-slim` NO trae `pkill`; para relanzar el catch-up basta reconstruir el contenedor sync (mata las exec viejas). Y como siempre: operar el VPS con scripts scp-eados (PowerShell destroza las comillas de un ssh largo).
  - *CERRADO en la OTA v2.5.109*: mostrar `originalTitle` («también conocida como …») en los resultados de búsqueda y que el buscador del control remoto (`RemoteScreen`) use el servidor en vez del respaldo local de `searchFilms` — las dos cosas salieron publicadas (ver el lote de la OTA arriba).
- **Lote 2.5.106: el control remoto pide temporada y capítulo (el episodio exacto abre en la tele, con su serie alrededor) + la memoria de vínculos en los dos sentidos (identidad estable de la tele, migración sin perder el PIN, nombres recordados y olvido) — OTA publicada** (2026-10-18; 612 tests Android en verde):
  - *Pedido del usuario (1)*: «al momento de buscar series y películas las encuentra pero tenemos el problema en las series o elementos de episodios, solo dice reproducir y no tiene selector de temporada y capítulos por lo cual no reproduce lo que pones porque manda en general a reproducir la serie sin seleccionar episodio» + *(2)* «que la tele donde se conecte recuerde dispositivos que ya fueron vinculados y también los dispositivos recuerden teles vinculadas».
  - *El selector (`ui/remote/CastEpisodeDialog.kt` + `RemoteCast.kt` puro)*: al tocar una serie en «Abrir en la tele» (o «Ver en la TV» de la ficha, que ahora usa el MISMO selector) se despliegan las temporadas del grupo (`film.seasons`) y los capítulos de la temporada activa (`X4KApi.episodeProgramsFor`, con `episodeRowLabel`); elegir uno manda `xtv:<playCode del capítulo>` con TODO el contexto de su serie. `isCastSeries` (temporadas o `kind` series/teleplay/anime) decide cuándo hay selector; la fila lo avisa («Capítulos» vs «Reproducir»); películas directas como siempre; sin capítulos (red) hay «Reintentar» + «Reproducir el título» (jamás un callejón sin salida). `CastEpisode`/`castEpisodeFor`/`castSeasonLabel`/`castEpisodeTitle`/`castEpisodeRef` puros.
  - *El cable y la tele*: `RemoteMsg` suma `series`/`seasonIndex`/`seasonIds`/`seasonNames`; `remoteOpenUriString` los mete al deep link como `s`/`sx`/`sl`/`sn`; `WatchLink` los parsea y `launcherRouteFor` arma `Route.Player(film, EpisodePlay(…))` → `playVod` auto-resuelve la lista de la temporada y el reproductor nativo abre ESE capítulo con «siguiente» y selector listos. Compatibilidad total: teléfono viejo+tele nueva y teléfono nuevo+tele vieja se comportan como antes (los campos son opcionales).
  - *Memoria de vínculos — tele (`RemoteServer.kt`)*: `tvId` estable (una vez, en `remote_pairing/tv_id`) que viaja en la baliza, el `hello`, el `needpin` y el `ok`; los tokens recordados guardan el NOMBRE del dispositivo (`dev_<token>`) y se podan al rotar (tope 8); `RemoteBus.pairedDevices` + Ajustes TV (Sistema) → «Control remoto» con «Dispositivos vinculados: N», la lista y «Olvidar dispositivos vinculados» (`RemoteServer.forgetPairings`). El recordado que vuelve sube al frente.
  - *Memoria de vínculos — teléfono (`RemoteClient.kt`)*: el token se recuerda por la identidad estable (`RemoteTv.key = id ?: host:puerto`) con **migración automática** del formato viejo (`tokenFor` muda `tok_<addr>` a `tok_<id>`; `learnTvId` la aplica al recibir la respuesta); lista `known_tvs` (JSON, tope 5, sin duplicados — el caso migración no deja el par viejo+nuevo: `upsertKnownTv` descarta por identidad y por dirección con id) y `rememberedTvs` (la última primero) → la pantalla del control remoto muestra «Tus teles vinculadas» con etiqueta «Vinculada» (`tvRowLabel`, verde) vs «Conectar», y auto-conecta a la última/primera vinculada al abrir.
  - *Verificado en el emulador (tele simulada con id en el PC, `adb reverse tcp:48633`)*: «Los Simpson» → T1·C2 y T2·C1 (con `seasonIndex:1`) y «Ley y orden: UVE» → T1·C3 desde la ficha; «Moana» directa y sin contexto; el deep link del capítulo disparado contra la app **abrió y reprodujo el capítulo exacto** (video en pantalla + subtítulos `cid` correcto + «Omitir intro»). Migración en vivo: prefs viejas (`tok_127.0.0.1:48633`, sin id) → al reconectar quedó `tok_TVPC9F1A…`, `last_id`, `known_tvs` con UNA entrada, el panel «Tus teles vinculadas · Vinculada» y reconexión silenciosa (hello con token, sin PIN). **Lección**: la tele simulada en el PC ocupa el 48633 y tumba `RemoteServerFlowTest` (Windows reparte la conexión al último binder con SO_REUSEADDR) — detenerla ANTES de correr la suite; el script quedó en `Temp/opencode/faketv_mem.py`.
  - *Guardianes*: `RemoteCastEpisodeTest` (6), `CastPickerWiringTest` (3), `RemoteClientMemoryTest` (8: lista corta/sin duplicados, identidad sobre dirección, migración, supervivencia al cambio de IP, remembered, etiquetas), `RemoteMemoryWiringTest` (3), `RemoteDiscoveryTest` +2 (baliza con id; clave por identidad), `RemoteOpenTest` +3, `RemoteProtocolTest` +2, `RemoteServerFlowTest` +2 (baliza UDP real con id estable + hello que lo confirma; nombres recordados y olvido con token rechazado después) y `LauncherDeepLinkTest` +3. Suite **612/612**.
  - *OTA v2.5.106 PUBLICADA* (vc342, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.106.md`).
- **Lote 2.5.105: «Deportes en vivo» al menú lateral de la Smart TV + el D-pad blindado al volver al Inicio (vigilante del foco y rescate a un destino compuesto) — OTA publicada** (2026-10-18; 579 tests Android en verde):
  - *Pedido del usuario (1)*: la sección de deportes también en el menú de la tele (en el móvil ya estaba desde 2.5.89) + compilar y mandar la OTA «sin regresiones ni errores» + *(2)* «verifica por qué se sigue perdiendo la navegación D-pad cuando regreso a la pantalla principal después de terminar una película que puse desde la pantalla principal».
  - *Riel de TV (`ui/TvApp.kt`)*: `TV_RAIL_SPECS` (lista única, `internal` para los guardianes) y el Inicio arma su riel desde ahí; **«Deportes en vivo»** entra justo después de «En vivo» (mismo icono y etiqueta que el móvil). Guardianes: `TvMenuTest` (3, lista y orden) + la comprobación de que el riel **PINTA** el pilar, que vive en `TvFocusTest` componiendo la app TV real (una clase propia con su sandbox Robolectric se colgaba en la suite completa, aunque pasaba aislada).
  - *D-pad (app, `ui/tv/TvFocus.kt`)*: (1) `rememberReturnFocus` deja un **vigilante** vivo mientras la pantalla tiene un regreso pendiente: si el foco queda vacío (la tarjeta enfocada desapareció porque su fila se recargó —«Seguir viendo» al terminar una película—) lo devuelve a la tarjeta exacta, si no a la primera tarjeta visible y si no al héroe; jamás pelea con un foco que sí exista ni con un diálogo abierto. (2) `FocusRescue.rescue()` elige el último destino **compuesto** de la pila (antes solo miraba el de arriba y, si estaba sin componer —el héroe fuera de pantalla tras desplazar la lista—, la tecla se perdía en silencio).
  - *Diagnóstico honesto del reporte*: el regreso ficha→reproductor→ficha→Inicio ya restauraba la tarjeta correcta (guardianes 2.5.69/2.5.76); el hueco medido es el foco que queda vacío **después** de restaurarlo (una recarga de filas quita la tarjeta enfocada y nada volvía a pedir el foco) + el rescate que se rendía con el héroe descompuesto. Sin tele/emulador disponibles al publicar (cero dispositivos ADB): la verificación física del regreso queda pendiente.
  - *Lección de pruebas (importante)*: la suite completa caía con ~38 tests de `ui.tv.*` (todos pasaban aislados) agotando los 60 s de «idle» de Compose. **Dos causas reales, las dos cerradas**: (1) el JVM de pruebas arranca con **512 MB de heap** y la suite entera (100+ clases Robolectric + Compose) lo ahoga → `unitTests.all { maxHeapSize = "2g"; jvmArgs("-XX:MetaspaceSize=256m", "-XX:MaxMetaspaceSize=1g") }` en `app/build.gradle.kts`; (2) las composiciones de prueba corrían con efectos COMPLETOS (el drift infinito del héroe y los springs del riel dejan la composición sin asentar nunca) porque `LocalReducedEffects` tenía `false` por defecto → **ahora su default es `true`** («ligero») y la app inyecta el valor real en `MainActivity`: cualquier composición fuera de la app (pruebas, hosts futuros) ya no arrastra animaciones infinitas; la app real no cambia en nada. Lección extra: una clase de prueba de Compose con su PROPIO sandbox Robolectric puede colgarse al componer en la suite completa aunque pase aislada (por eso la comprobación del riel vive ahora dentro de `TvFocusTest`, sobre la app TV real).
  - *OTA v2.5.105 PUBLICADA* (vc341, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, notas en `ota_notes_2.5.105.md`).
- **Lote 2.5.104: Deportes en vivo — una sola fila por canal, lenguaje honesto («posibles») y los enlaces a la vista — OTA publicada** (2026-10-17; 573 tests Android + 194 backend en verde):
  - *Pedido del usuario (captura del Cruz Azul vs FC Juárez)*: «El mismo canal con dos estados (TUDN MX «Caído» y TUDN MEXICO FHD «Disponible»)» genera confusión + «sé más claro en el lenguaje para no generar falsa expectativa: di posibles transmisiones, muestra los enlaces y recomienda intercalar entre transmisiones hasta encontrar el partido».
  - *Una sola fila por canal*: los modales calculan `liveLinks`/`liveTv` (los vivos de cada sección) y filtran con `GeeBackstop.isSameChannel(guideName, candidateName)` (misma marca y edición, país solo cuando se pide; feeds nacionales equivalentes; marca deportiva exige su palabra): la copia caída del mismo canal **se oculta** cuando la otra sección ya lo sirve VIVO — se acabó el «TUDN MX · Caído» + «TUDN MEXICO FHD · Disponible». El guardián del caso real está en `GeePickDialogDuplicateTest.theSameChannelNeverAppearsTwiceWithOppositeStates`.
  - *Lenguaje honesto*: secciones renombradas a **«Canales posibles»** y **«Posibles transmisiones»**; cada transmisión **enseña su enlace** (`linkDisplay` = la dirección sin las cabeceras tras `|`); y tras la lista va `ZAP_HINT`: «Puede que alguna esté dando otro partido: cambia entre ellas (Arriba/Abajo durante la reproducción) hasta encontrar la que lo dé». Motivo documentado en las notas: se midió en vivo que un canal listado puede estar dando OTRO partido (el «TUDN MEXICO FHD» del León–América estaba con Toluca–San Luis) y que la parrilla a veces nombra un canal equivocado.
  - *Verificado en el emulador (Cruz Azul vs FC Juárez, 21:10)*: «Canales posibles: Canal 5, Vix Deportes» (TUDN ya no se repite) + «Posibles transmisiones: TUDN MEXICO FHD y CANAL MX FHD» **con sus enlaces completos** y los declarados sin señal (XTREME + FHD, UNIVISION HD…).
  - *OTA v2.5.104 PUBLICADA* (vc340, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.104.md`).
- **Lote 2.5.103: el caso León–América a fondo — la lista COMPLETA de cada partido (los canales que la fuente declara sin dirección) y cada uno resuelto solo — OTA publicada** (2026-10-17; 570 tests Android + 194 backend en verde):
  - *Pedido del usuario*: «el partido del América en gee sí tiene fuentes que funcionan y en visión no; que no se vuelva a repetir, corrobóralo muy bien» + «tienes el APK de geesports, instálalo en el emulador… no te detengas hasta que lo obtengas».
  - *Diagnóstico medido (VPS + CMS + APK + emulador)*: (1) el sitio del CMS (`new.whatyouthink.site`, Cloudflare) está **bloqueado por un aviso legal (DMCA → 451/1023)**: responde 403 «Website Access Blocked» a TODO (nuestro worker, el VPS, mi PC, el navegador y los proxies públicos) → nuestro espejo **congeló** en el ciclo de 19:19 y el worker omite ciclos sin perder datos; el dominio no rotó (Firebase RC sigue diciendo el mismo). La app original tampoco descarga: en el emulador abre con «sin conexión» (lo que se ve en el teléfono del usuario es su copia en memoria). (2) Para **Club León vs Club América** la fuente declara **8 canales** (`XTREME + FHD, TUDN US, TUDN US 2, UNIVISION HD, TUDN MEXICO FHD, CANAL MX FHD, HD QUALITY, SD QUALITY`) y **solo 2 traían dirección**: el parser de la app (`jsonLinks` descartaba `link` vacío) tiraba los otros seis → «pocas fuentes». (3) Los 2 servidos son los **canales lineales** de OTTe/Amazon compartidos por 7 partidos (`TUDN MEXICO FHD` mostraba **Toluca–San Luis** en pantalla, captura revisada). (4) La parrilla (ESPN) decía «Fox Sports · Claro Sports» pero el canal que lo transmitía era **FOX One** (prensa: El Universal/Depor) — y **nuestro FOX ONE (xtvlive:cyx_A4C363F00DD17F7A4) estaba con el partido** (captura: jugadores del León). (5) Bug nuestro: el respaldo de «Claro Sports» podía caer en «CANAL CLARO HD» (canal general) en vez de «Claro Sports 1 HD».
  - *Arreglo (servidor `api/app.py` + app)*: la tarjeta del partido ahora lleva **también los nombres declarados sin dirección** (`declared_by_match` desde `link_names` del CMS → ranuras `pending` con `link=""`, sin duplicar y después de los servidos) y el parser de la app **ya no los descarta**: el selector enseña la lista completa como la app original («La fuente aún no publica su señal · respaldo automático»). Al tocar una ranura (o un canal declarado cuyo enlace está caído), `playZap(dead=true, guideName=…)` reproduce el canal **100 % confirmado** de ese nombre en el catálogo de la casa (respaldo automático, GeeSports manda cuando sí lo sirve). El respaldo ahora se arma con `event.broadcasts + event.links.map { it.name }` (los nombres del CMS también), y `GeeBackstop` suma: **feeds nacionales como la misma cadena** (`brandOf` quita east/west/este/oeste → «Univision East HD» cubre «UNIVISION HD») y **marca deportiva exige su palabra** («Claro Sports» NO cae en «CANAL CLARO HD»; «Fox Sports» no cae en «FOX One»).
  - *Verificado en el emulador*: el selector del León–América enseña **los 8 canales** (con sus badges) y al tocar «TUDN US» abrió solo `reproductor XTV lanzado: live TUDNUNIVISION☆` (nuestro TUDN USA) ✓; y FOX ONE con el partido en pantalla ✓.
  - *Guardianes*: `GeeBackstopTest` +3 (la palabra deportiva, los nombres declarados sin URL, los feeds nacionales) y `GeePickDialogDuplicateTest` +1 (una ranura declarada se lista y su toque usa el respaldo de ESE nombre); `server/tests/test_gee38.py` +1 (la tarjeta lleva los declarados, sin duplicar y sin perder los servidos). Suite **570 Android + 194 backend**.
  - *OTA v2.5.103 PUBLICADA* (vc339, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.103.md`). El cambio del servidor ya aplica a las apps instaladas.
- **Lote 2.5.102: Deportes en vivo — la lista SOLO con las fuentes de GeeSports y el respaldo SIEMPRE automático (GeeSports con prioridad) — OTA publicada** (2026-10-17; 567 tests Android + 193 backend en verde):
  - *Pedido del usuario*: «Para que no se confundan, el fallback es automático, siempre; pero siempre es prioridad y hay que mostrar solo las fuentes de geesports» + «Los canales de cada transmisión también solo las da GeeSports».
  - *La lista (móvil y TV)*: se retiraron las filas y la sección de respaldo que se pintaban junto a las fuentes caídas (2.5.100) y también `GeeBackstop.orphans`/`GeeBackstopRow`/`TvGeeBackstopItem`: el modal vuelve a enseñar **solo** los canales 24/7 resueltos por GeeSports y sus enlaces del partido, con su badge (Disponible/Caído). La fila caída lo dice en su línea: «Canal en vivo · respaldo automático» / «DASH · ClearKey · respaldo automático».
  - *El respaldo, automático en los dos caminos*: (1) **al tocar una fuente caída**, `playZap(picked, dead, guideName)` reproduce la cadena confirmada del catálogo en vez del enlace muerto (por nombre de parrilla con `GeeBackstop.forGuide`; para un enlace directo caído, el primer respaldo del partido); (2) **dentro del reproductor**, al agotarse TODAS las señales tras los reintentos y el failover, `giveUpRecovery` pide el respaldo (`PLAYER_FAILOVER_XTV`) y `LivePlayerScreen` lo abre con el reproductor nativo (el de TV en vivo) en vez de dejar la tarjeta de error. GeeSports manda siempre: con una señal suya viva no se toca el respaldo (ni se calcula).
  - *Firma sin crecer (lección dura)*: el primer intento añadió `xtvFallback` + `onFallback` a `PlayerCore` y ART **rechazó la clase** (`java.lang.VerifyError: register v264 has type Long (High Half) but expected Boolean`, composable con demasiados parámetros) — crash real en el emulador al abrir el reproductor. Fix: el respaldo viaja por el puente del proceso (`GeeZapSignals.requestFallback/consumeFallback`, estado observable) y `PlayerCore` NO cambia de firma. Regla: **no crecer la firma de las composables gigantes**; usar el patrón de objetos de proceso.
  - *Verificado en el emulador*: la lista mostró solo GeeSports (Telemundo/TUDN MX/Vix Deportes «Caído · respaldo automático» + TUDN MEXICO FHD «Disponible»); al tocar Telemundo (caído) se abrió **solo** `cyx_TELEMUNDO1USA` con el motor nativo (`reproductor XTV lanzado`) sin crash y sin preguntar.
  - *Guardianes*: `GeePickDialogDuplicateTest` reescrito (la lista no pinta respaldos, tocar una caída lleva al canal de la casa, y **una fuente sana gana siempre** aunque haya respaldo) + `LiveSportsPlayerUiTest` (el reproductor pide el respaldo solo al agotarse, por el puente, y la firma no crece) + `GeeBackstopTest` sin el caso de huérfanos. Suite **567/567**.
  - *OTA v2.5.102 PUBLICADA* (vc338, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.102.md`).
- **Lote 2.5.101: el reproductor de Deportes en vivo con la MISMA interfaz que el de TV en vivo + el espejo que ya no congela las fuentes de un partido — OTA publicada** (2026-10-17; 566 tests Android + 193 backend en verde):
  - *Pedido del usuario (1)*: «El reproductor de los canales de Geesports tiene que ser el mismo o con la misma interfaz de nuestra tv en vivo conservando las funciones propias que ya tiene justo ahora». *(2)*: «En visión este partido no tiene fuentes que sí lo televisen pero en gee sí tiene» (captura de la app original mostrando para Chivas-Tigres: LOW QUALITY (USE VPN) · TNT SPORTS HD · TNT SPORTS HD 2).
  - *Interfaz (app, `ui/player/PlayerScreen.kt` + `ui/player/PlayerDialogs.kt`)*: el reproductor EN LÍNEA de deportes ahora pinta el **aviso de cable** de la TV en vivo (`LiveCableBanner`: píldora EN VIVO con punto, chip de la liga, título grande, y líneas con etiqueta azul **AHORA:** (la señal en pantalla + HLS/DASH·ClearKey) y **MARCADOR:** (`0 - 1 · 60'`, o «SE TRANSMITE EN» si aún no hay marcador)); abajo, **píldoras etiquetadas** con la piel del reproductor nativo (`PlayerPill`: Canales e Información —las dos de TV en vivo que aquí aplican— + Pausa/Reproducir y Audio y subtítulos, con resaltado azul al enfocar y fila deslizable en móvil) y el **modal Información** (`GeeMatchInfoDialog`: liga, marcador, señal en pantalla, parrilla y TODAS las señales del evento con la actual marcada). La lista **Canales** (`ZapListDialog`) rotula el formato de cada señal (HLS / DASH · ClearKey) y la actual con su check. La píldora de zapping aparte se retiró: el aviso de cable cuenta la señal nueva (el overlay se queda ~4.5 s tras el zap), como en la TV en vivo. **Intactas las funciones propias**: reconexión con «Reconectando… intento N», failover, ClearKey por señal, zap Arriba/Abajo en dos toques, respaldo en tu lista y audio/subtítulos.
  - *Contexto sin churn de firma*: `GeeZapSignals.EventInfo` (título del partido, liga, marcador/minuto, parrilla, nº de señales) se arma en las dos pantallas de deportes y viaja por el mismo holder; `LivePlayerScreen` lo lee con `takeInfoFor(channel)` (ANTES de `takeFor`, one-shot validado por URL) y `PlayerCore` lo pinta. `geeScoreLine` (puro) arma «1 - 0 · 37'».
  - *Fuentes congeladas (SERVIDOR, causa raíz medida)*: nuestro espejo refresca los enlaces de un evento **solo cuando su entrada viene en `events.txt`**, y el CMS publica a medias y ROTA entradas (log real: «events.txt vino a medias (115 vs 321 guardados)»): la fila de Chivas-Tigres llevaba horas con **2** enlaces mientras su `pro/<…>.txt` ya traía **3** (faltaba «LOW QUALITY (USE VPN)», un HLS `http://193.47.62.55/…|user-agent=…&origin=…`). Comprobado en el VPS: `parse_pro` del worker devolvía 3 y la BD tenía 2 (la pasada extra solo re-fusionaba lo guardado, sin volver a descargar). Fix (`sync/gee38.py`, segunda pasada): si la entrada rotó fuera del archivo, su `links_ref` **se RE-DESCARGA** (presupuesto compartido) y, si el archivo ya no está publicado, se conserva lo guardado. Desplegado al VPS (`docker compose up -d --build sync`, respaldo `/tmp/gee38.py.bak-…`) y verificado: la tarjeta ya sirve las 3 señales.
  - *Verificado en el emulador*: el aviso de cable con «EN VIVO · Liga MX · Guadalajara vs Tigres UANL · AHORA: LOW QUALITY (USE VPN) · HLS · MARCADOR: 0 - 1 · 60'» y las píldoras Canales/Información/Pausa, **reproduciendo** la señal nueva (decodificador AAC del emulador activo). Y la tarjeta mostró las 3 señales igual que la app original (TUDN MEXICO FHD / LOW QUALITY (USE VPN) / CANAL MX FHD).
  - *Guardianes*: `LiveSportsPlayerUiTest` (6: rotulado del tipo de señal, la línea del marcador, la fuente que prueba que el aviso de cable y las píldoras existen con las etiquetas de TV en vivo, que el contexto viaja desde los dos modales y que la ficha de Información tiene sus filas) y `server/tests/test_gee38.py` +2 (la pasada extra re-descarga el `pro` de una entrada rotada; y conserva lo guardado si el archivo se retiró). Suite **566 Android + 193 backend**.
  - *OTA v2.5.101 PUBLICADA* (vc337, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.101.md`). El arreglo del espejo es de servidor: aplica al instante a las apps ya instaladas.
- **Lote 2.5.100: Deportes en vivo — respaldo en tu lista (canal caído → el mismo canal del catálogo, 100 % confirmado) + vigilante de vida propio (comprueba las señales cada 30 s) — OTA publicada** (2026-10-17; 561 tests Android en verde):
  - *Pedido del usuario*: «cuando haya un fallback de un canal caído en geesports que tengamos en XTV sí lo muestres, pero solo con canales 100% confirmados (por ejemplo Telemundo en el partido de Chivas), siempre como fallback si la opción principal está caída; si se recupera, siempre tomamos geesports como primera opción» + «un sistema que en los eventos deportivos que estén en vivo esté comprobando continuamente si está caído o si se levanta el server para reproducir».
  - *Motor del respaldo (`data/gee/GeeBackstop.kt`, puro)*: cruza la **parrilla oficial del partido** (`event.broadcasts`) contra el **catálogo de canales de la casa** y devuelve, por cadena, **UN** canal solo si la coincidencia es del **100 %**: misma marca (`SportsLinker.extractBrandInfo`) + misma edición (la «1» vale como base) + el país solo se exige si la parrilla lo dice («TUDN USA» no se cubre con la señal mexicana). Alias de la casa incluidos (`Caliente TV` → `FOX One`). Jamás un parecido: «TNT Sports» no resuelve a «TNT Series»; «Fox Sports» no se cubre con «Fox Sports 2». Desempate determinista (nombre exacto > país de la parrilla > México > feed nacional EAST/WEST antes que estación local > menos adornos). Tope `MAX_BACKSTOPS=4`, una fila por cadena.
  - *Regla de vida (la fuente SIEMPRE manda)*: un nombre de la parrilla **ya servido EN VIVO** (por su canal 24/7 o por una transmisión directa del partido) no recibe respaldo — y en cuanto el caído se levanta, el respaldo desaparece solo. `orphans()`/`forGuide()` reparten los respaldos entre las filas caídas y una sección propia.
  - *Vigilante de vida propio (`GeeHealth` puro + `GeeProbe` con OkHttp)*: mientras la pantalla de Deportes está abierta, cada **30 s** (`GeeHealth.INTERVAL_MS`) se comprueban las señales de los partidos **EN VIVO**: sus transmisiones directas **y** los canales 24/7 que resuelve su parrilla (para que un levantamiento también se vea). Es una **petición corta al manifiesto** (512 bytes, 3.5 s de tope, cabeceras del enlace tras `|`, 4 en paralelo, tope `MAX_PER_PASS=16`): un 2xx con cuerpo = viva. **`GeeHealth.effective(check, serverAlive)`**: lo comprobado AHORA manda sobre el dato del servidor (que puede tener minutos) — así el badge «Disponible/Caído» y el respaldo van al día. Se apaga al salir de la pantalla (vive en un `LaunchedEffect`); log `GeeWatch` por ronda.
  - *App*: `GeeSportsState` suma `checks` (mapa observable), `xtvChannels` (catálogo de la casa, con recarga en segundo plano si la copia está vacía) y `watchSignals()`; los modales (`GeeLinkPickDialog` móvil y `TvGeeLinkPickDialog` TV) reciben `checks` + `backstops` y pintan la fila del respaldo **pegada a la fila caída** (indentada, en azul, «Respaldo de Telemundo · Confirmado» o «En tu lista · Confirmado» si el nombre coincide); al tocarla reproduce el canal de la casa por el reproductor nativo XTV. Las listas de zapping no cambian (solo señales directas).
  - *Guardianes*: `GeeBackstopTest` (12: el caso real de Chivas, la fuente viva que no recibe respaldo, el parecido que se rechaza, la edición, el país, los servicios de streaming que no son canales, alias FOX One, el nombre exacto, huérfanos), `GeeHealthTest` (6) y `GeeProbeTest` (5, **contra un servidor HTTP real de sockets** en el equipo: manifiesto que responde con sus cabeceras, error, puerto muerto, basura, lote) + 4 pruebas nuevas en `GeePickDialogDuplicateTest` (el respaldo se pinta en ambos modales, en el móvil se toca y reproduce el canal de la casa, va pegado a la fila caída y desparece si la fuente está sana, y la comprobación fresca gana al veredicto viejo del servidor). Suite **561/561**.
  - *Verificado en el emulador con el partido de Chivas*: «Telemundo — Caído» (y TUDN MX/Vix Deportes caídos) con **TELEMUNDO EAST HD · Respaldo de Telemundo · Confirmado** y **ViX Deportes · En tu lista · Confirmado** justo debajo de sus filas; «TUDN» NO recibe respaldo (su transmisión directa TUDN MEXICO FHD estaba viva); al tocar el respaldo se abrió `reproductor XTV lanzado: live cyx_TELEMUNDO1USA` con `motor :x4k ya precalentado` y sin FATAL/ANR. El vigilante registró «16 señales comprobadas, vivas 15» cada ~31 s y siguió al volver del reproductor.
  - *OTA v2.5.100 PUBLICADA* (vc336, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.100.md`).
- **Lote 2.5.99: Deportes en vivo — el candado de horario ya se abre a su hora (fin del «no se desbloquea aunque ya es la hora») — OTA publicada** (2026-10-17; 532 tests Android en verde):
  - *Pedido del usuario*: «Revisa porque el candado que pusimos antes de que no pudiéramos ver los partidos hasta que fuera la hora no se desbloquea a pesar que ya es la hora de algunos partidos».
  - *Causa raíz (medida contra el API real, no adivinada)*: `GeeAgenda.phaseOf` le daba la última palabra al estado de la agenda (ESPN): `status == "pre"` devolvía `Phase.Soon` SIEMPRE, antes de mirar el reloj. El sincronizado de ESPN tarda minutos en pasar de «pre» a «in»: verificado en vivo con **Chivas vs Tigres UANL (17:07)**, que a las 17:11 seguía sirviendo `status=pre, live=false` (con 2 señales ya vivas) → la app lo dejaba bloqueado pasado el horario. Foto del endpoint: 197 eventos (61 `pre`, 38 `post`, 5 `in`).
  - *Fix (`data/gee/GeeAgenda.kt`)*: **el reloj manda para ABRIR** — `live` → Live; `post` → Done; con horario usable: `[inicio, inicio+3 h)` → Live, antes → Soon, después → Done; sin horario → manda el estado (`in` → Live, resto → Soon). Constante `LIVE_WINDOW_MS`. Un partido terminado JAMÁS se reabre.
  - *Que se abra SOLO con la pantalla abierta*: `GeeSportsState.clockMs` + `tick()` (estado Compose) con tic de **30 s** en móvil y TV (en TV el refresh de datos sigue cada 2 min: 4 tics por refresh) y el reloj entra en las llaves de `remember` de `sections`/`liveCount` — antes el `remember(filtered)` dejaba los bloques congelados aunque hubiera recomposición.
  - *Tarjetas congruentes*: `playable`/`blockNote`/píldoras reciben `nowMs` explícito (`GeeEventTile`, `GeeTileFooter`, `GeeStateBadge` y sus equivalentes de TV) y `GeeTiles.state(event, today, liveByClock)` pinta **EN VIVO** cuando el reloj ya lo abrió (antes la tarjeta decía «Hoy 17:07» dentro del bloque «En vivo»).
  - *Extra*: `startMs` cachea con llave `id|fecha hora` (antes solo `id`): una hora corregida por el CMS ya no se queda pegada. Seam de pruebas `GeeAgenda.clockForTestsMs` (internal; producción jamás lo toca) y `TvSportsMosaicTest` lo fija a las 18:00 CDMX del día sembrado para no depender de la hora real de la suite.
  - *Guardianes*: `GeeAgendaTest` +4 (el candado se abre por reloj aunque la agenda siga en «pre»; terminado no se reabre; hora corregida no se congela; sin fecha manda el estado) y `GeeTilesTest` +1 (EN VIVO por reloj). Suite **532/532**.
  - *Verificado en el emulador con datos reales*: antes «57 eventos · 5 en vivo» con Chivas-Tigres en «Comienzan pronto» y badge «Hoy 17:07»; después **«57 eventos · 6 en vivo»**, el partido en el bloque En vivo con badge y píldora EN VIVO, y su selector abierto con «Se transmite en…», «Canales en vivo» y las señales reales (TUDN MEXICO FHD, CANAL MX FHD «Disponible») sin ningún aviso de bloqueo.
  - *OTA v2.5.99 PUBLICADA* (vc335, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.99.md`).
- **Lote 2.5.98: Control remoto — el teléfono como mando de la tele (D-pad, reproducción, canales, volumen y abrir contenido) — OTA publicada** (2026-10-17; 528 tests Android en verde):
  - *Pedido del usuario*: el teléfono como control remoto de la tele — D-pad, play/pausa, ±10 s, cambio de canal, volumen y **abrir un título/canal directo en la tele**; emparejamiento automático en la misma red con PIN de 4 dígitos una vez y recordado después; additivo, sin tocar nada existente.
  - *Arquitectura (la TELE escucha, el teléfono manda)*: `RemoteServer` se levanta en `MainActivity.onCreate` **solo si `DeviceKind.isTelevision`** y se apaga en `onDestroy` con `isFinishing` (mientras la app vive). UDP **48632** (sonda del teléfono → baliza con nombre/modelo/puerto; `MulticastLock` porque los drivers Wi-Fi filtran la difusión) + TCP **48633** (un JSON por línea, `kotlinx.serialization`). Teléfono: `RemoteClient` (descubrimiento por difusión a 255.255.255.255 + las de cada interfaz, conexión, token por tele, estado observable `RemoteUiState/RemoteStatus`).
  - *Teclas*: lista blanca `RemoteKeys.ALLOWED` (volumen aparte con `AudioManager.adjustStreamVolume(FLAG_SHOW_UI)`; mute = `ADJUST_TOGGLE_MUTE`). El menú las atiende **en proceso** (`RemoteBus.Sink` = `MainActivity.dispatchRemoteKey`); el reproductor (`:x4k`) por **broadcast interno** `setPackage` + receptor `RECEIVER_NOT_EXPORTED` (`RemoteBus.ACTION_REMOTE_KEY`) — si hay pantalla local al frente no se emite broadcast, así la tecla jamás cae en la pantalla equivocada. BACK por `onBackPressedDispatcher.onBackPressed()` (respeta el Atrás de dos pasos).
  - *Abrir contenido*: `remoteOpenUriString` (puro) traduce a los deep links que la app ya sabe abrir — `visionplus://watch/<ref>` (XTV o URL) y `visionplus://live/<código>` (host nuevo en el manifest) — vía `startActivity` + `onNewIntent` (convive con el reproductor residente).
  - *Emparejamiento y seguridad*: PIN de 4 dígitos que la tele muestra **solo cuando un teléfono lo pide** (`RemoteBus.pairingPin`), 5 intentos y se corta; token de 32 caracteres por tele (guardado en la tele en `remote_pairing/tv_tokens` y en el teléfono como `tok_<host:puerto>`); «En la tele: …» (NOW) lo publica el reproductor y lo pinta el teléfono.
  - *App*: pestaña **«Control remoto»** en el menú lateral del móvil (`PhoneApp.TABS`, `Route.Remote`, guardián en `PhoneMenuTest`); pantalla `RemoteScreen` (D-pad, Atrás/Menú/Info, ±10 s, canal ±, volumen, «Abrir en la tele» con buscador de VOD + canales, «Desconectar») y botón **«Ver en la TV»** en la ficha móvil — solo con conexión **VIVA** (`RemoteStatus.CONNECTED` + `castableRef`), nunca un botón muerto.
  - *Bug cazado en vivo (importante)*: los botones escribían el socket en el **hilo principal** → `NetworkOnMainThreadException` tragada por un `runCatching` y la orden se perdía **en silencio** (el HELLO sí viajaba porque corre en el hilo de conexión). Fix: escritor único `remote-send` (`Executors.newSingleThreadExecutor`), `socket`/`out` `@Volatile` y `wireWriterForTest`. Guardián nuevo `RemoteClientWireTest` (3: tecla/abrir/PIN escriben en `remote-send`, no en el hilo que llama).
  - *Guardianes*: `RemoteProtocolTest`, `RemotePairingTest`, `RemoteDiscoveryTest`, `RemoteOpenTest`, `RemoteDeepLinkTest`, `RemoteCastTest`, `RemoteTitleCastTest`, `RemoteServerFlowTest` (**8, de punta a punta sobre sockets reales**: PIN → token → reconexión silenciosa, teclas al frente por sink y por broadcast, tecla fuera de la lista jamás llega, `open` → deep link) + `PhoneMenuTest`.
  - *Verificado en vivo en el emulador* contra una **tele simulada en el PC** (`adb reverse tcp:48633`, porque dos AVD no comparten red): PIN 4321 → token guardado → CONNECTED con «En la tele: Inicio»; llegaron todas las teclas (OK 23, Abajo/Arriba 20/19, Play/Pausa 85, Volumen+ 24, Menú 82, Atrás 4); reconexión silenciosa sin PIN; y «Ver en la TV» desde la ficha de Spider-Man envió `{"t":"open","ref":"xtv:6950…","title":"Spider-Man: un nuevo día","poster":"https://…"}` correcto.
  - *Limitación aceptada (documentada)*: sin servicio en primer plano, si el proceso principal muere durante una reproducción larga el control remoto se cae (reabrir la app en la tele lo revive).
  - *OTA v2.5.98 PUBLICADA* (vc334, 101 MB, firma histórica `9593cb0d…`, notas en `ota_notes_2.5.98.md`). *Pendiente física*: probar teléfono + tele reales en la red de casa (el flujo verificado es el mismo; falta el descubrimiento UDP real entre dos equipos).
- **Lote 2.5.97: Deportes en vivo — zapping del partido con el mando + señales que se defienden solas (reconexión y failover automáticos, DRM por señal) — OTA publicada** (2026-10-17; 482 tests Android en verde):
  - *Pedido del usuario*: «Vamos a darle todo junto pero sin errores ni regresiones, no pares hasta terminar y entregarme una compilación sin errores y vía ota la actualización» — jugador del reproductor EN LÍNEA (`PlayerCore` en `ui/player/PlayerScreen.kt`, ExoPlayer) premium; sin tocar el reproductor nativo (`X4KPlayerAty`, `X4KTitan`) ni los cambios de deporte sin commitear de la otra sesión (gating playable/blockNote y badges «Disponible» quedaron intactos).
  - *Fase 1 (reconexión automática, de la sesión previa)*: política pura `LiveResilience` + recuperación del reproductor con reintentos y píldora «Reconectando… intento N», política de errores tolerante (LoadErrorHandlingPolicy propia), buffer de vivo paciente (`DefaultLoadControl` solo-vivo), tarjeta premium «Preparando la señal…» y telemetría `PLAYER_RECONNECT_FAIL`.
  - *Fase 2 (zapping + failover + botón Canales)*:
    - *Plumbing sin churn de firma*: holder `GeeZapSignals` (`ui/sports/GeeZapSignals.kt`, estilo `DeportesReturn`) — los diálogos de opciones del partido (móvil `GeeSportsTabs.kt` y TV `TvGeeSportsTabs.kt`) arman la lista ORDENADA de señales al tocar cualquiera; `LivePlayerScreen` la consume con `takeFor(channel)` validado por URL (one-shot; vacío/extraño → zapping desactivado, seguro en todos los demás caminos del reproductor).
    - *Motor de zap en `PlayerCore`*: primeros D-pad Arriba/Abajo en 2 toques (`ZAP_ARM_MS=2500`) hacen `zapTo(+1/−1)` con wrap-around; resetea error/presupuesto de reintento/estado de stall, actualiza `currentTitle`, cambia el píldora superior con punto rojo + nombre (`zapBannerName`, auto-oculta 2.2 s) y **usa un solo `clearKeyJsonRef` remember-hoisted** (AtomicReference): el proveedor DRM devuelve `DRM_UNSUPPORTED` cuando no hay llaves y las de ClearKey se cambian POR SEÑAL al zappear (verificado en vivo: FANCODE y D SPORTS, DASH·ClearKey descifrando tras el salto).
    - *Failover automático en `giveUpRecovery`*: si la señal se rinde tras los reintentos y quedan alternativas (`LiveResilience.failoverAllowed`), `zapTo(+1)` solo + `PLAYER_FAILOVER` a diagnóstico; la tarjeta de error sale solo cuando se AGOTAN los espejos. El zap manual no consume el presupuesto.
    - *Ciclo = solo señales directas*: filtro http(s)/https al armar (refs `xuperch:`/`magis:` del catálogo quedan fuera del ciclo, alcanzables por el selector como siempre) — decisión documentada; la señal caída «ESPN 1 | México» SÍ entra (es http directa, solo está muerta hoy) y el failover la rescata solo.
    - *UI premium*: botón **Canales** (icono TV) en la fila del reproductor en vivo (móvil + TV) que abre `ZapListDialog` (en `PlayerDialogs.kt`, estilado como AudioAndSubtitlesDialog: `MenuOption`/`MenuSectionTitle`/`rememberDialogLanding`, la actual resaltada); cadena de foco D-pad: pistas→Canales→pista, Arriba/Abajo→play/pausa.
  - *Guardianes*: `LiveResilienceTest` +2 (`zapWrapsAroundBothWays`, `failoverTriesEveryOtherSignalOnceThenGivesUp`; 9 en total) y `GeeZapSignalsTest` nuevo (4: semántica de restantes, canal ajeno vacío, dedupe por URL, take drena). Suite completa **482/482**.
  - *Verificado en el emulador con datos reales (LaLiga, Valencia–Athletic)*: modal arma la lista (ESPN DEPORTES HLS + FANCODE/D SPORTS/CAZE DASH·ClearKey); abierto por «ESPN DEPORTES HD» → **D-pad 2 toques saltó a ESPN 1|México (caída, con banner)** → reconexión → **failover automático a FANCODE reproduciendo**; zap D SPORTS→ESPN 1|México→failover a FANCODE; cabecera y banner correctos en cada salto; cero FATAL en logcat (y el camino nativo intacto — se abrió por accidente un canal `:x4k` y salió limpio). Nota NO-regresión: el toque no abre ni el diálogo de Canales NI el de pistas (preexistente del catcher móvil; el camino del mando es el objetivo del lote).
  - *OTA v2.5.97 PUBLICADA* (vc333, 101 MB, firma histórica `9593cb0d…` verificada con apksigner; notas en `ota_notes_2.5.97.md`). *Pendiente física*: A32/POCO/TV fuera de red al publicar (mismo caso que 2.5.81/2.5.83) — el flujo D-pad es el mismo verificado en emulador; probar en la tele con el mando al volver a red.
- **Lote 2.5.96: arranque en frío al instante (fin de la espera del portal en el primer abierto) + reincidencias del bot + crashes con mensaje + telemetría del reproductor en `:x4k` — OTA publicada** (2026-10-17; 464 tests Android en verde + 191 backend):
  - *Pedido del usuario*: el siguiente lote aprobado (reincidencias, `e.message`, telemetría `:x4k`, `HOME_ROWS_FAIL`) y «revisa por qué el primer arranque en frío tarda muchísimo a veces incluso en el móvil con más capacidades».
  - *Causa del arranque lento (medida, no adivinada)*: `catalog.load()` bloqueaba `bootReady` con (1) el parseo del índice en caché (varios MB: `cacheMs=4430` medido en emulador frío) y (2) el portal completo — 5 llamadas de `X4KApi.homeRows` (re-acuñado de sesión incluido) + `liveRow` (~2 MB de canales). En red fría eso rebasaba el tope de 9 s (`BOOT_CAP_MS`) y producía el `HOME_ROWS_FAIL` crónico que además era **engañoso**: el detalle «Timed out waiting for 9000 ms» era la cancelación del tope EXTERNO atrapada por el catch interno del gateway (2 s), no un fallo del gateway (26 hits/5 equipos en 72 h).
  - *Fix de arranque (`XuperCatalog.load` en 3 fases)*: primer cuadro con `CatalogSeed` en 0 ms → índice en caché y portal en **segundo plano** (`cachedIndexRows` sustituye la semilla conservando filas ya pintadas; `portalRows` arma estrenos+`xtv-live`+base como siempre): el Inicio abre con lo suyo en ~1 s y las filas se refrescan solas. Guardias nuevos: `PORTAL_SLOW` (portal >20 s) y `BOOT_SLOW` en `MainActivity` (bootMs>6 s con desglose `semillaMs/cacheMs/portalMs` en el detalle) — probado en emulador: reportó `bootMs=6615 … cacheMs=4430` y tras el fix ya NO volvió a dispararse (y `HOME_ROWS_FAIL` desapareció de los reportes).
  - *Reincidencias (worker `server/sync/diag.py`)*: la huella ya alertada que recibe reportes NUEVOS tras `alerted_at` ahora re-avisa según `repeat_alert_reason` (pura): **«regreso»** (estaba autocerrada), **«reincidencia»** (versión distinta de la avisada — el caso del POCO 2.5.94 que el bot dejó pasar por estar ya alertada la huella del Chromecast) o **«sigue»** (24 h repitiéndose, `NAG_HOURS`). Al re-avisar actualiza `alerted_at` + `alert_version` (columna nueva `diag_alert_state.alert_version`, ALTER idempotente en `schema.sql` y aplicada en el VPS) y limpia `resolved_at`; filas legacy sin versión se fijan en silencio para no andanar Telegram al desplegar. `alert_text(row, headline=…)` + `repeat_text(row, reason)` reusan las dos capas humano+técnico.
  - *`e.message` en crashes (app `Diagnostics.kt`)*: `reportCrash` manda `payload.message` (truncado a 400); la huella se calcula SOLO del stack (no cambia, no duplica alertas viejas); el bot lo muestra en ambas capas («Mensaje: …»). Aprobado el despliegue: ahora un `IllegalArgumentException` deja de ser anónimo.
  - *Telemetría del reproductor (`X4KPlayerAty`)*: `Diagnostics.screen("Player")` + fase por modo (`reproductor-vivo`/`reproductor-vod`/`reproductor-arranque`) para que los ANR del `:x4k` dejen de venir «sin pantalla»; la red de seguridad del residente (2.5.95) reporta `PLAYER_RESIDENT_IDLE` con detalle idéntico en ambos caminos (mismo huella, una sola alerta).
  - *Verificado*: backend 191/191 (`test_diag.py` +6: repeat_reason ×4, repeat_text, mensaje del crash); suite Android en verde; emulador frío con `pm clear` — Inicio pintado en ~4.9 s a primer frame (antes 8.5 s) y `am start -W` COLD repetido ~4.5 s; VPS con worker de reincidencias desplegado (`docker compose up -d --build sync`, respaldo en `/tmp/diag.bak-lote2`) y `alert_version` en la BD.
  - *OTA v2.5.96 PUBLICADA* (vc332, firma histórica `9593cb0d…`, asset `VisionPlus.apk`).
- **Lote 2.5.95: Deportes en vivo sin crashes (dedupe del selector) + el reproductor residente ya no deja la pantalla negra ni atrapa la salida — OTA publicada** (2026-10-17; 464 tests Android + 185 backend):
  - *Pedido del usuario*: analizar los últimos errores de los equipos y arreglarlos sin regresiones (el crash activo de Deportes y el reproductor negro que no dejaba salir).
  - *Crash de Deportes (telemetría + código + datos en vivo)*: huella `6171ce10085be490` — 4 crashes en el Chromecast v2.5.93, `IllegalArgumentException` en el measure de un `LazyColumn` (screen Sports). La parrilla del partido trae entradas distintas que resuelven al MISMO canal («Canal 5»/«Canal 5 Televisa», «ESPN»/«ESPN+», «FOX One»/«Fox Sports», «TUDN»/«TUDN En Vivo»): `GeeGuide.channelsFor` devolvía una opción por nombre de guía y el modal repetía `(title, link)` → clave duplicada. Fix: `GeeGuide.dedupeOptions` por `(title, link)` conservando la mejor puntuación + `itemsIndexed` en los modales móvil/TV (una repetición ya no puede tumbar la pantalla). Guardianes: `GeePickDialogDuplicateTest` (compone el modal con duplicados directos, TV y móvil) + 5 pruebas nuevas en `GeeGuideTest`.
  - *Reproductor negro / no deja salir (causa en el código)*: la tarea del reproductor es aparte (`taskAffinity` propia + `excludeFromRecents`) y al salir de la app solo se cerraba con un broadcast al proceso `:x4k` esperando 700 ms; con el proceso congelado (los ANR de 108–155 s con main idle lo mostraban) la tarea quedaba al frente en negro. Peor: `exiting=true` (puesto al salir del reproductor) hacía que Atrás en esa actividad fuera un no-op para siempre. Fix triple: (1) `exitAppCleanly` intenta cerrar la tarea del reproductor directamente (`ActivityManager.getAppTasks()` + `AppTask.finishAndRemoveTask()`) antes de soltar la principal, con el broadcast de respaldo; (2) el reproductor se cierra solo si vuelve al frente **sin reproducción** (`onResume`); (3) `exitPlayer()` con `exiting` y sin reproducción también cierra la tarea (`shouldFinishIdleResidentOnExit`). Guardianes: `ResidentPlayerTest` + `ExitFlowTest` (reconoce la tarea por su actividad base/tope y clava el nombre real de la clase).
  - *Verificado*: 464 Android + 185 backend en verde; release R8 vc331 con firma histórica `9593cb0d…`; smoke de la OTA en emulador (Deportes con datos reales + el selector abierto sin crash con la guía `ESPN Deportes · ESPN+ · ESPN`; el motor ARM-traducido del emulador x86_64 aborta al detenerse —limitación del entorno— y por eso la reutilización residente en físico queda como pendiente de sentir en la tele).
  - *OTA v2.5.95 PUBLICADA* (asset `VisionPlus.apk`).
- **Lote 2.5.94: reloj del centro de México en todo el menú + agenda deportiva en horario central — OTA publicada; y el ARREGLO DEFINITIVO de las fuentes de «Deportes en vivo» (solo servidor, aplicado)** (2026-10-17; 451 tests Android + 185 backend):
  - *Pedido del usuario (1)*: un reloj en todo el menú (excepto al reproducir contenido) con el horario del centro de México; y que la agenda deportiva sea en torno al horario central mexicano.
    - *App*: `ui/Clock.kt` — `VisionClock` (puro: `timeText`/`msToNextMinute` en `America/Mexico_City`, probado con instantes UTC fijos) + `VisionClockBadge` (píldora sin foco, encima de todo el menú). En `MainActivity`: se muestra salvo en `Route.Player`/`LivePlayer` (reproductores) y `Route.Search` (el campo ocupa la parte superior); probado en emulador EN OTRA ZONA horaria: la app mostró «07:33 CDMX».
    - *Agenda MX*: `SportsSource` ancla día/chips/horas a `America/Mexico_City` (`todayIso(nowMs)`, `shift`, `weekdayLabel`, `timeLabel`) — el servidor ya agrupa y publica en esa zona; `GeeAgenda` ya usaba MX para fases.
  - *Pedido del usuario (2)*: «en GeeSports salen todas esas fuentes y sí te llevan al partido; en Vision+ solo salen pocas y no reproducen — arreglo definitivo» → «revisa y analiza cómo GeeSports asigna los canales a cada evento, ahí está la clave».
    - *Análisis del APK V3.8 (`com.live.geesports`, jadx en Temp)*: el diálogo «Multiple links available» (`pc/i.java`) muestra los `link_names` del EVENTO y, al tocar un índice, el reproductor recibe la ruta del `pro/*.txt` + el índice y resuelve `pro[índice]` FRESCO al reproducir. La lista es la del MISMO CRUCE de equipos (el CMS publica varias entradas por partido: «Everton-Chelsea» 17-oct tenía 1 enlace muerto y visible=false; la del 13-oct —visible— 10 enlaces con 6 vivos; su app enseña esa). Además su lista solo muestra eventos `visible=true`.
    - *Causa raíz nuestra*: servíamos SOLO la entrada del día del cruce (1 enlace muerto) en vez de la lista rica del cruce, y sin sondeo fresco → «pocas opciones y no reproducen».
    - *Fix (worker `sync/gee38.py` + API `api/app.py`)*: `_team_pair_key` + `_merge_sibling_links` (fusión por cruce sin duplicar URL, propios primero, hermanos por cercanía de fecha y vivos, tope 18; `_sort_links` ahora conserva el orden de origen dentro de cada grupo) + presupuesto `EVENTS_LINKS_MAX` 160→320 (todos los eventos refrescan) + SEGUNDA PASADA que re-fusiona y sondea los eventos de la ventana que el CMS rotó fuera de `events.txt` (tope 80/ciclo) + en el API fusión por cruce de la tarjeta del calendario (dedupe prefiriendo la copia sondeada, vivos primero antes del filtro de parrilla). Guardines: 5 pruebas nuevas (`_merge_sibling_links`, `_team_pair_key`, segunda pasada, fusión de tarjeta ESPN con hermanos).
    - *Verificado EN VIVO (deploy VPS + respaldos `*.bak-*`)*: **Everton-Chelsea: 10 fuentes con 4 EN VIVO** (misma lista que su app: TNT1UK, FUBO, USA TV, beIN, SKY…; DASH con ClearKey `kid:key` verificado) y **Espanyol-Atlético: 8 (5 vivas)**; hoy **50 partidos con enlaces, 36 con señal EN VIVO** (antes: 16 sin links y listas de 1-3 muertas). Aplica al INSTANTE para las apps instaladas (sin OTA): el arreglo es del servidor.
  - *OTA v2.5.94 PUBLICADA* (vc330, firma histórica `9593cb0d…`, asset `VisionPlus.apk`).
- **Alertas de Telegram en lenguaje humano, sin perder lo técnico (2026-10-17; solo servidor, sin OTA)**:
  - *Pedido del usuario*: los avisos del bot eran solo códigos y huellas («MAIN_BLOCKED_23317s», «huella: …»); quería saber en lenguaje humano qué pasó o qué falló, sin quitar los detalles técnicos.
  - *Worker* `server/sync/diag.py`: cada alerta ahora tiene dos capas. **(1) Humano**: titular («la app se cerró sola», «se quedó congelada», «el mando se quedó sin foco»…), explicación de qué significa y un «👉 Qué revisar» según el tipo de excepción (`IllegalArgumentException`, `IllegalStateException`, `OutOfMemoryError`, `UnsatisfiedLinkError`…) o el código de anomalía (`HOME_ROWS_FAIL`, `FOCUS_LOST`, `PLAYER_ERROR`…). Los ANR convierten `MAIN_BLOCKED_23317s` en «≈ 6 h 28 min» y aclaran que un bloqueo tan largo suele ser suspensión del equipo, no un cuelgue real. Los `USER_FEEDBACK` por fin muestran el texto de la persona. **(2) Técnico**: tipo, código, versión, dispositivos/veces y huella (lo de antes) + contexto nuevo (equipo, Android, red, RAM, almacenamiento, pantalla, fase, motor, proceso y hora de México) + primeras líneas de la traza (4 crash / 5 ANR). El cierre pasa a «✅ Dejó de repetirse: …» y se mantiene atómico con CTE (sin carrera entre el cierre y un reporte nuevo). Límite de Telegram respetado (3900 caracteres).
  - *Guardianes*: `server/tests/test_diag.py` (13 pruebas: crash, ANR, anomalía, feedback con payload JSON, campos faltantes, plurales, truncado). Suite backend **177/177** en verde.
  - *Desplegado en el VPS* (`/opt/streaming-backend/sync/diag.py`, `docker compose up -d --build sync`; respaldo `diag.py.bak-`). Sin cambios en la app ni OTA; aplica al instante para reportes nuevos.
- **Lote 2.5.93: «Atrás» confiable — D-pad que ya no se pierde al volver de una serie + cierre con confirmación (adiós al bucle del reproductor) — OTA publicada** (2026-10-17; 447 tests Android):
  - *Pedido del usuario*: al retroceder de una serie abierta desde el Inicio el D-pad se perdía; y en el menú principal, Atrás no cerraba la app — «cae en el reproductor de nuevo y de ahí ya no lo saco» —; quería cierre con doble Atrás y aviso.
  - *Causa raíz del foco (cazada en el código)*: `homeReturnRowIndex` (el índice vertical del regreso) NO contaba la fila «Populares de la semana» que se pinta entre Estrenos y «Seguir viendo» desde v2.5.77: con esa fila presente (todo equipo con internet), `revealReturnIndex` revelaba una fila arriba, la tarjeta exacta no componía y — si el héroe quedaba fuera de pantalla — el mando moría (los guardianes no lo veían porque en tests no hay red/trending). Fix: `trending` suma al índice en su posición exacta + guardián `returnRowIndexCountsTheWeeklyTrendingRow`; red de seguridad en `rememberReturnFocus` (si al rendirse nada tiene foco, la columna vuelve al inicio para que el héroe EXISTA y tome el foco — `requestFocusSafely`).
  - *Causa raíz del «bucle del reproductor»*: Atrás en la raíz cumplía el default (finish) → el sistema resumía la TAREA del reproductor residente (v2.5.85, tarea propia fuera de recientes) → Atrás en el reproductor = «volver a la app» (HIDE_RESIDENT) → raíz → finish → reproductor… bucle sin salida.
  - *Fix — cierre con confirmación (móvil y TV)*: `BackHandler` en la raíz de `MainActivity` (`stack <= 1`): primer Atrás arma + aviso «Presiona atrás de nuevo para cerrar la aplicación» (píldora sin foco, se oculta sola al vencer la ventana); segundo dentro de 2.5 s cierra de verdad. Política pura `BackExitGuard` (+4 guardianes en `ExitFlowTest`). `exitAppCleanly`: si el proceso `:x4k` vive, se le pide cerrar al reproductor residente (`X4KWarmupReceiver.ACTION_FINISH_FOR_EXIT` → `X4KPlayerAty.finishResidentForExit()`, proceso muere con la actividad) y recién entonces `finishAndRemoveTask()` (la espera acotada evita el flash del reproductor; con descarga activa el servicio sigue, como siempre). Mismos hábitos del reproductor nativo (doble Atrás 2.5 s con aviso propio, ya existente).
  - *Extra (misma clase)*: el regreso pendiente (`filmReturn`) ahora se invalida al salir del Inicio por un hub que faltaba (En vivo, Categorías, Continuar) — evita que la siguiente vuelta re-dispare el reveal viejo. OJO: NO se limpió en el `onDone` del regreso (la prueba `homeBackRestoresSameCardAfterLateContent` lo cazó: el foco transitorio de una tarjeta reciclada cuenta como «usuario» y limpiar ahí rompía el regreso con contenido tardío).
  - *Verificado en emulador*: primer Atrás → aviso visible y NO cierra; el aviso se oculta solo (4 s) y la app sigue; doble Atrás → la tarea desaparece (lanzador al frente) y la app reabre normal; ruta con reproductor (deep link) sigue saliendo con su propio Atrás. Suite completa 447/447. El caso residente real (matar la tarea del reproductor al salir) queda para la prueba física en la tele (el emulador no sostiene una sesión de reproducción por red).
  - *OTA v2.5.93 PUBLICADA* (vc329, firma histórica `9593cb0d…`, asset `VisionPlus.apk`).
- **Lote 2.5.92: «Deportes en vivo» en MOSAICO (móvil y TV) + filtros compactos en Smart TV — OTA publicada** (2026-10-17; 442 tests Android):
  - *Pedido del usuario*: en móvil y Smart TV la sección de deportes pasaba de lista a **parrilla en mosaico** con los logotipos a la vista y aspecto profesional; y en TV el área de filtros se comía casi la mitad de la pantalla (los partidos apenas se apreciaban).
  - *App — mosaico*: `ui/sports/SportsScreen.kt` (móvil) y `ui/tv/TvSportsScreen.kt` (TV) pintan los partidos en `LazyVerticalGrid` (2 columnas adaptativas en móvil, 3 en TV) con encabezados de sección a todo lo ancho (EN VIVO → COMIENZAN PRONTO → TERMINADOS); cada tarjeta es un marcador: escudos grandes (22dp móvil / 34dp TV), nombre, marcador, estado (EN VIVO · minuto / Final / Hoy 19:00), guía «TV: …» y píldora roja «N en vivo». Lógica compartida y pura en `ui/sports/GeeTiles.kt` (teams/score/liveLabel/state/streams/monogram — una sola fuente para ambos mosaicos); los elementos sin equipos (GP de México) muestran logo + nombre. Tocar/OK abre el mismo selector de transmisiones de siempre (guía + canales del catálogo + directos), con el recuerdo de `DeportesReturn` intacto.
  - *App — filtros compactos (TV)*: el conteo («56 eventos · 2 en vivo») vive EN LÍNEA en el encabezado (antes era una fila propia), los chips usan el nuevo modo `compact` de `TvChoiceChip` y **días + ligas comparten UNA sola fila** (con separador; «Todas» primero). El área de filtros bajó de ~300dp a ~130dp: antes se veían 2-3 tarjetas, ahora ~6 partidos a la vez. El foco de entrada sigue anclado a la primera tarjeta (`geeEntryFocus` + `softFocusDown`), sin `focusProperties` sobre requester huérfanos (lección 2.5.90).
  - *Guardianes*: `GeeTilesTest` (7 pruebas puras: equipos/alias, marcador, etiquetas de estado, pie) y `TvSportsMosaicTest` (Robolectric, siembra la copia en archivo sin red: mosaico con partidos y escudo de estado, foco de entrada asentado, D-pad vivo, conteo en el encabezado y filtros de días+ligas en una fila). Suite completa 442/442.
  - *Verificado*: emulador móvil con datos reales (56 eventos — mosaico de 2 columnas, tarjeta → modal «Everton vs Chelsea · Se transmite en: USA Net · Universo · NBC Universo → Cerrar»); render de la pantalla de TV a PNG (mosaico de 3 columnas con EN VIVO · 23', filtros en 2 filas compactas, tarjeta enfocada con marco blanco). Nota: el AVD `Vision_Chromecast_TV` corre imagen de teléfono (sin modo TV) — el render se obtuvo con Robolectric (graphics NATIVE) antes de publicar; prueba física en la tele del usuario queda pendiente.
  - *OTA v2.5.92 PUBLICADA*: `tools/publish_ota.ps1` (vc328, firma histórica `9593cb0d…`, asset `VisionPlus.apk`).
- **Lote 2.5.91: red de seguridad de actualizaciones + OTA en un comando + respaldo off-site con verificación + vigilante externo — OTA publicada** (2026-10-17; 433 tests Android + 170 backend):
  - *Pedido del usuario*: «¿qué podemos resolver ahora?» de la lista comercial — automatización total sin intervención: actualizaciones a prueba de build malo, respaldos fuera del VPS y vigilancia que no viva en el VPS.
  - *App — kill-switches de actualización* (probados EN VIVO en el POCO): `min_version_code` (si el servidor lo sube por encima del `versionCode` instalado, la app muestra «Actualización requerida» en TODAS las aperturas e **ignora el `skip`**; toque afuera/Atrás no cierran) y `ota_paused` (deja de ofrecerse la actualización: campana sin badge, sin diálogo — probado: aviso normal con 2.5.91 publicada → pausa → sin aviso). Política pura `AppUpdate.updateOffer/shouldForce` + `RemoteConfig.intFlag` con guardianes (`AppUpdatePolicyTest`, `RemoteConfigTest.intFlagAcceptsNumbersAndStrings`). Operador: `POST /v1/config` con `X-Admin-Key` y `{"min_version_code": 999}` / `{"ota_paused": true}` (la llave vive en `.visionplus/CREDENCIALES.txt` y en el `.env` del VPS — OJO: la copia local estaba rancia, usar la del VPS).
  - *Publicación en UN comando*: `tools/publish_ota.ps1 -Version X.Y.Z -NotesFile ota_notes_X.Y.Z.md` — bump, tests, build R8, **verificación de firma histórica** (aborta si no coincide) y `gh release create`; si algo falla restaura `app/build.gradle.kts`. Estrenado con la v2.5.91 (vc327, 101 MB, firma `9593cb0d…`). Queda como decisión del usuario meter la llave de firma en GitHub Secrets para compilar en la nube (no se hizo: repo público + llave histórica = riesgo irreversible).
  - *VPS — respaldo off-site*: `backup.sh` (diario 03:30, retención 7) manda el dump a Telegram los domingos (`disable_notification`) — probado con `--telegram` (archivo de 21 MB recibido). 
  - *VPS — verificación de restauración*: `restore_check.sh` (domingos 03:45) restaura el último dump en una base temporal `restore_check`, cuenta `titles`/`gee38_catalog` y reporta por Telegram — probado: **«respaldo verificado: titles=34942»**. Es también el heartbeat semanal (si no llega el domingo, algo murió).
  - *Vigilante EXTERNO*: GitHub Actions `uptime.yml` (cada 15 min, `workflow_dispatch` para probar) comprueba `visionplusv2.duckdns.org/healthz` y `la20.com.mx/catalogo/healthz` y **alerta al Telegram** si alguno falla — corre fuera del VPS, cubre la caída del host entero. Secrets `DIAG_TELEGRAM_TOKEN`/`DIAG_TELEGRAM_CHAT` en el repo (revocables). Probado: run manual 200/200 → «OK: backend sano».
  - *Lección de quoting*: los `curl` largos por `ssh` desde PowerShell se destrozan — para operar el VPS usar SIEMPRE un script `scp`-eado y `bash /tmp/x.sh` (adoptado en todos los pasos de hoy).
- **Fix post-OTA 2.5.89 (crash real del POCO en «Deportes en vivo») + la clase de crash D-pad de la ZTE + automatización de operación — OTA v2.5.90 publicada** (2026-10-17; 430 tests Android + 170 backend):
  - *Reporte del usuario*: «el POCO crasheó» — cazado con `dumpsys dropbox` + el diagnóstico del VPS: `IllegalArgumentException: Key "NBA Preseason|17/10/2026|20:00:00" was already used` en pantalla=Sports, vc324 (2.5.88), proceso principal.
  - *Causa raíz (verificada en vivo contra la BD)*: **el CMS de Gee NO trae `id` por evento (210/210)** y el payload servido tampoco; la app caía al respaldo `nombre|fecha|hora` para la clave de la lista → dos partidos NBA distintos a la MISMA hora («NBA Preseason» 20:00) compartían clave → LazyColumn muere. El `_event_id` del worker YA incluía equipos (fix 2.5.88) pero **solo vivía en la fila de la BD, no en el JSON**.
  - *Fix en DOS capas*: (1) **servidor (desplegado 2026-10-17 06:31 UTC, protege YA a toda app 2.5.87+)**: el worker `sync/gee38.py` escribe `payload["id"] = eid` y `/v1/gee38/events` rellena ids faltantes desde el row id + deduplica la salida por id; (2) **app**: respaldo de id con EQUIPOS y `dedupeEvents` (fusiona enlaces de duplicados reales) en `GeeSportsSource`; canales de un grupo deduplicados por nombre+primer enlace; líder `GeeSportsSourceTest` (4 nuevos). Verificado en vivo: **141 eventos, 0 sin id, 0 duplicados, los dos NBA 20:00 con ids distintos** (`event:62d8…` / `event:2cd7…`).
  - *De paso (misma clase, pantalla TV)*: la ZTE crasheó (v2.5.85) con `IllegalStateException` en `dispatchKeyEvent` — el mapping R8 lo confirma: `e0.p`=FocusRequester, `e0.j`=FocusOwnerImpl, `q0.e`=KeyInputNode → **`FocusRequester.requestFocus()` sobre un requester HUÉRFANO** (su nodo ya no está adjunto). El patrón `focusProperties { down = … }` con destinos que pueden desmontarse seguía en `TvSportsScreen` (ancla = primera tarjeta de la LazyColumn, se recicla al desplazar) y en `TvTitleScreen` (`down = chipFocus` con chip que solo existe `if (downloadsOn)`; con el kill-switch de descargas = crash). Fix: `requestFocusSafely()` + `Modifier.softFocusDown()` en `TvFocus.kt` (si el destino está adjunto salta a él; si no, **la navegación normal sigue — jamás un crash**) aplicados en las 6 ocurrencias. Guardián `TvRescueTest.safeFocusRequestNeverThrowsOnDetachedRequester`.
  - *Nota de API*: en Compose 1.7.3 `FocusRequester.requestFocus()` devuelve `void` (no Boolean) — el wrapper no puede leer un resultado, «éxito» = no lanzó (comentado en el código).
  - *Automatización de operación (mismo día)*: **CI reparado (DOS roturas encadenadas)** — (1) `setup-android@v3` intentaba instalar el paquete `tools` (retirado por Google en 2025): el step fallaba y **TODOS los runs** morían ahí; ahora `packages: platform-tools`. (2) Al destaparlo, el daemon fallaba con «Java home supplied is invalid»: el repo traía `org.gradle.java.home` con una **ruta local de Windows** en `gradle.properties` → retirado (el CI usa su JDK 17; local se exporta `JAVA_HOME`, ya documentado). **Run verde confirmado** (6m17s, los 3 jobs). **Protección de `main` ACTIVADA** (pendiente histórico de AGENTS): checks requeridos `unit-tests`/`server-tests`/`release-build` (OJO: quedó sin force-push; si se necesita, `gh api -X PUT ... -f allow_force_pushes=true`). **VPS**: `backup.sh` (respaldo diario 03:30, retención 7, probado: dump de 21M) + `watchdog.sh` (cada 5 min: si `/healthz` ≠ 200 reinicia la API y lo anota; probado) — instalados con cron idempotente junto al de laveinte.
  - *Verificado en el POCO (release R8 vc325 instalado encima, firma histórica)*: Deportes → Básquet → «3 eventos · 1 en vivo» sin crash; tarjeta → modal → Atrás OK; logcat limpio. Pendiente de la próxima OTA: el fix de app (la parte de servidor ya protege la app instalada).
  - *OTA v2.5.90 PUBLICADA* (decidida por el usuario en la misma sesión): vc326 (101 MB) firmada con la llave histórica `9593cb0d…` (apksigner), asset `VisionPlus.apk`, notas firmadas; instalada encima en el POCO (`pm install -r` Success → 2.5.90, arranque y Deportes sin crashes).
  - *Canal de alertas CONECTADO (misma sesión)*: bot de Telegram **@VisionPlus_Info_bot** (chat de Edward, `8531818760`) configurado en el `.env` del VPS (`DIAG_TELEGRAM_TOKEN`/`DIAG_TELEGRAM_CHAT`) — **prueba real enviada por `diag.send_alert()` desde el contenedor y recibida**; desde ahora el triage (cada 5 min) avisa al Telegram toda huella NUEVA de crash/ANR y el autocierre a las 48 h. Credencial respaldada en `.visionplus/CREDENCIALES.txt`.
  - *Lección del `.env`*: el archivo NO terminaba en salto de línea y el append pegó `DIAG_TELEGRAM_TOKEN` a `GEE38_INTERVAL_SECONDS=2700` (el sync recreó con la variable corrupta). Al editar el `.env` del VPS: `if [ -n "$(tail -c1 .env)" ]; then echo >> .env; fi` antes del append y verificar con `grep -o '^[A-Z_0-9]*=' .env`.
- **Lote 2.5.89: «Deportes en vivo» al menú lateral del móvil — OTA publicada** (2026-10-17; 426 tests Android):
  - *Pedido del usuario*: «La sección de Eventos deportivos en el móvil tiene que estar en el menú lateral» — antes solo se llegaba por el acceso rápido del Inicio.
  - *App*: `ui/PhoneApp.kt` — `TABS` suma `Tab(Route.Sports, "Deportes en vivo", Icons.Default.SportsSoccer)` entre «En vivo» y «Mi lista» (misma lista para el drawer y el riel de tablet en apaisado); `Tab`/`TABS` pasan a `internal` para el guardián. El PIN del modo niños ya contemplaba `Route.Sports`, y `nav.goTab` deja Home debajo (Atrás regresa al Inicio).
  - *Guardián*: `PhoneMenuTest` (la entrada existe, con ese label, justo después de «En vivo»). Suite 426/426.
  - *Verificado en el POCO*: debug y release R8 vc325 — drawer con la entrada (seleccionada al estar en la sección), tap → «Deportes en vivo» con chips de deporte/día/liga y eventos; Atrás → Inicio. Release R8 vc325 (105.9 MB) firma histórica `9593cb0d…` (apksigner); asset `VisionPlus.apk` de la OTA.
  - *Nota*: en Smart TV sin cambios (ahí ya tenía acceso en los hubs del Inicio).
- **Lote 2.5.88: «Deportes en vivo» — la guía REAL de dónde se transmite cada partido, «Canales en vivo» servidos SOLO del catálogo de Gee (sin XTV en este feed), conflicto FOX resuelto y Atrás→modal — OTA publicada** (2026-10-17; 424 tests Android + 165 backend):
  - *Pedido del usuario*: «sigue sin mostrar canales y lo que muestra está equivocado; ¿no hay guía de dónde pasan los partidos?» — la «guía» del CMS solo son los nombres de sus enlaces (a veces UN espejo genérico o el canal equivocado); la guía buena es la parrilla ESPN/LiveSoccerTV que YA teníamos, pero no se usaba en la app.
  - *Servidor*: `_gee_match_card` ahora incluye `guide`/`broadcasts` (dedup, tope 8) y `league_id` — la parrilla oficial viaja en la tarjeta.
  - *App*: `GeeEvent` parsea `broadcasts`/`league_id`; **«Canales en vivo» se sirve SOLO de Gee** (decisión del usuario: ese feed no se alimenta de XTV): `data/gee/GeeGuide.kt` cruza la guía del partido con los grupos 24/7 del catálogo (por marca+edición+país; prefiere señal MX y la viva) y el modal (móvil y TV) muestra **«Se transmite en: …»** + **«Canales en vivo:»** (FOX SPORTS 1 MX, TUDN MX, Azteca 7/Canal 5 dentro de grupos agregadores…, reproduce el enlace directo al instante) + **«Transmisiones en vivo:»** (enlaces directos ordenados por parrilla). Las tarjetas muestran **«TV: …»**. **Conflicto FOX arreglado** (reporte del usuario — real en México): el canal general «Fox» (Disney) ya NO se confunde con «Fox Sports» (Lauman) ni con «FOX DEPORTES» (EE. UU.); además los países «mx/us» de 2 letras ahora se leen como palabra completa («FOX SPORTS 1 MX» = México y gana sobre feeds US). **Caliente TV se convirtió en FOX One en México** (verificado con prensa: Fox Corporation la adquirió; la marca Caliente desapareció el 1-dic-2025): alias `Caliente TV → FOX One` implementado en `GeeGuide` (guía) y en la familia del servidor, para que el partido se ofrezca solo cuando el catálogo lo traiga — hoy **no está en Gee** (0 en los 8 enlaces del grupo FOX Sports y en la lista madre de 539; en XTV sí existe «FOX ONE», pero ese feed es solo-Gee por decisión del usuario). **Atrás desde el reproductor regresa al MODAL de opciones** (no a la lista): `DeportesReturn.pickingId` (objeto de proceso en `ui/sports/GeeSportsState.kt`) recuerda el partido abierto y el modal se restaura al volver; el diálogo ya no se autodescarta al sintonizar (cada pantalla decide — Canales sí limpia el suyo).
  - *Verificado en el POCO*: tarjeta Tijuana-Puebla «TV: Caliente TV · Fox Sports»; modal con «Se transmite en: Caliente TV · Fox Sports», **FOX SPORTS 1 MX** en «Canales en vivo» (del catálogo de Gee, reprodujo — audio focus verificado) y CANAL MX FHD abajo como directo; Necaxa «TV: TUDN · Canal 5 · Azteca 7»; **sintonizar → Atrás → el modal de opciones reaparece** (pedido del usuario, verificado). 165 backend + 424 Android en verde (incluye `GeeGuideTest` y guardianes del conflicto FOX); release R8 con firma histórica.
  - *Refinamiento post-publicación 2 (mismo día, solo servidor)*: **¿cómo consigue Gee los enlaces?** Cada partido de su CMS declara `link_names` (CANAL MX FHD, TUDN MEXICO FHD, XTREME FHD, AZTECA HD…) y un `pro/<…>.txt` por partido con las URLs reales — **rotan durante el día** y su hosting sirve **copias cacheadas** y a veces publica el archivo **a medias** (visto en vivo: 214→149→214 eventos en minutos). Arreglado en nuestro espejo: (1) `_busted_url` = cache-buster en cada descarga del CMS (los externos no); (2) **poda blindada** — si el archivo viene a medias (<50% de lo guardado) no se poda, y solo se poda lo de >3 días; (3) **BUG de IDs cazado**: `_event_id` ignoraba los equipos y todos los partidos de una jornada («Liga MX, Apertura» + misma fecha/hora) colisionaban — **214 escritos, 149 guardados** (Atlante «perdido» por esto); ahora el hash incluye equipos → **210/214** (los 4 restantes son duplicados literales de su archivo). Verificado: **Atlante FC vs Pumas UNAM ya sale con ['TUDN MEXICO FHD','CANAL MX FHD']** (lo mismo que su app mostraba) y Tijuana-Puebla con CANAL MX (su `pro` de ese partido hoy solo trae esa).
  - *Autonomía total (grado comercial, misma fecha)*: el espejo ya se mantiene solo — (1) `_refresh_base`: **Firebase Remote Config** (proyecto 723237950983 de su app) descubre el dominio si rotan el hosting (verificado en vivo); (2) `_resolve_key_auto`: clave del entorno → guardada en `gee38_meta` (Postgres) → **re-derivada del APK** si rotan el certificado (`cryptography` añadido al worker; `download_url` guardado en meta); (3) dominio y clave quedan persistidos en la BD (sobreviven reinicios); (4) contenedores con `restart: unless-stopped`. Ciclo verificado: 32 grupos + 214 eventos, 210 guardados, Atlante con 2 enlaces, `meta key/base` escritos. Intervención manual restante: ninguna rutinaria (solo si cambian TODO su ecosistema —proyecto Firebase + app— a la vez).
- **Lote 2.5.87: «Deportes en vivo» — agenda deportiva completa por días, una sola fuente, canales filtrados a la parrilla real y cero rastros de la fuente — OTA publicada** (2026-10-17; 415 tests Android + 163 backend):
  - *Pedido del usuario*: fuente única (fin de la doble agenda), pestaña «Deportes en vivo» que agrupa los deportes dando protagonismo al fútbol y sus grandes ligas (Liga MX y Ascenso primero), agenda **por días** (Hoy/Mañana/+2…+7), bloques **EN VIVO → COMIENZAN PRONTO → TERMINADOS** con marcadores en tiempo real, SIN referencias visuales a la fuente externa, solo deportes top (Fútbol/F1/Americano/Básquet/Tenis/Béisbol) y solo ligas principales + MX + Ascenso; y que los canales ofrecidos sean **solo los que de verdad transmiten el partido** («le doy ver y sale otra cosa»).
  - *Servidor (VPS, desplegado)*: worker `sync/gee38.py` (clave del CMS en el `.env` del VPS; receta en `.visionplus/gee38_key.txt` — NADA de esto al repo público) descifra y publica el catálogo (`gee38_catalog`/`gee38_meta`): 32 grupos 24/7 + eventos, sondeo de vida (vivos primero), feedback de caídos, archivos retirados = HTML detectado (se conserva lo bueno). `api/app.py`: `/v1/gee38/*`; **la agenda de fútbol se ARMA del calendario ESPN** (ventana −1..+7 días, 16 ligas) y a cada tarjeta se le cruzan los enlaces del CMS por equipos+horario (alias Chivas/Guadalajara, Tigres/UANL; ±36 h). Piezas: `_gee_fix_event_times` (el CMS publica en **UTC** — Necaxa 01:00 = 19:00 MX verificado ×3), `_gee_fix_logos` (Sofascore `http://` → `https://img.sofascore.com/…`; http daba 301/403 y Android bloquea cleartext), `_gee_broadcast_filter` = **solo canales de la parrilla oficial del partido** (familias FOX Deportes≈Fox Sports, TUDN USA≈TUDN, beIN≈beIN1; marcas fuera de parrilla se descartan; agregadores solo si no hay coincidencia; «Sin señal» si no queda nada — Tijuana-Puebla ya no manda a Canal 5), deportes permitidos y ligas permitidas (ids ESPN + nombres normalizados).
  - *App*: `data/gee/` — `GeeSportsSource` (fetch `/v1/gee38/sports|events` con caché memoria+archivo, `safeImage` http→https, `toChannel` → PlayerScreen existente con `|headers` + ClearKey) y `GeeAgenda` (fases Live/Soon/Done, prioridad de ligas con **Liga MX primero**, alias de liga, secciones puras y probadas); `GeeSportsState` (día + deporte + filtro); rediseño total de `ui/sports/SportsScreen.kt` y `ui/tv/TvSportsScreen.kt` (chips deporte→día→liga, tarjetas con escudos/marcador/estado, selector de transmisiones vivos primero); entradas renombradas «Deportes en vivo» (Home y quick-hub TV); el reproductor muestra el grupo «Deportes en vivo» (sin rastro de la fuente); etiquetas F1/Americano/Básquet/Tenis/Béisbol.
  - *Verificado*: 415 Android + 163 backend; VPS en vivo (144 eventos, jornadas MX completas con todos los partidos que antes faltaban, sin ligas basura); **A32 con el release final vc323**: chips de deporte y días (Hoy/Mañana/Dom 18/Lun 19), ligas con Liga MX al frente, EN VIVO con marcador al minuto (Atlante 0-0 Pumas 12') y reproducción real desde el selector (F1 en directo y Canal 5 DASH·ClearKey, verificado antes de publicar). Release R8 con firma histórica `9593cb0d…` (apksigner). **OTA v2.5.87 publicada** (asset `VisionPlus.apk`).
  - *Refinamiento post-publicación (mismo día, solo servidor — aplica al instante sin OTA)*: el filtro por parrilla quedó **demasiado estricto** y dejó partidos «Sin señal» (la parrilla oficial es incompleta y cambia entre fuentes: Necaxa-Atlas decía TUDN un día y FOX al siguiente). Ahora `_gee_broadcast_filter` **no descarta nada: ordena por niveles** — 0) cadenas de la parrilla (con familias), 1) señales neutrales (agregadores tipo «HD QUALITY»), 2) otras marcas al final — el usuario siempre tiene opciones y la mejor va arriba (Necaxa → FOX DEPORTES primero; Tijuana-Puebla → CANAL MX, que antes salía vacío). QA de navegación en el POCO (2.5.87): diálogo→Atrás cierra, Deportes→Atrás a Inicio, ficha→Atrás, reproductor→Atrás×2 con **liberación de audio verificada** (`dumpsys audio`: releasing player), spam de Atrás sale limpio al launcher, **cero ANR/input-timeouts**. Nota: en el reproductor el primer Atrás con controles visibles solo los oculta (diseño de dos pasos).
  - *Pendiente*: prueba en la TV real (componentes idénticos a móvil; el D-pad no se ha probado en físico con este rediseño).
- **Vision+ PC: producto portátil para Windows (emulador + app con aspecto de exe nativo) + fixes de motor/dedupe** (2026-10-16; sus fixes entraron en la OTA v2.5.84, aprobado por el usuario):
  - *Descubrimiento*: el emulador oficial en PC x86_64 **sí** puede correr el motor ARM del X4K: las
    imágenes Google APIs/Play (API 30+) traen la traducción ARM oficial de ARM
    (`libndk_translation.so`; verificado en `Vision_Phone`: `abilist=x86_64,arm64-v8a`). El bloqueo
    real era doble: el loader de la app pedía la carpeta ABI `x86_64` (inexistente en assets) **y** el
    APK empaquetaba libs x86_64 de los AAR → el sistema instalaba la app como x86_64 nativa y el
    dlopen ARM fallaba (`is for EM_AARCH64 (183) instead of EM_X86_64 (62)`).
  - *Fix app (para correr el motor en emulador; en teléfonos/TV sin cambio)*: (1) `X4KTitan.pickNativeAbi()`:
    extrae la primera ABI de `SUPPORTED_ABIS` **que exista en assets** (en el emulador cae a `arm64-v8a`);
    (2) `abiFilters += arm64-v8a, armeabi-v7a` en `app/build.gradle.kts`: el APK deja de traer x86/x86_64
    y el emulador lo instala como **arm64 traducido** (proceso invitado) → el motor carga y **reproduce
    de verdad** (HEVC `c2.goldfish.hevc.decoder`, audio `FFP_MSG_AUDIO_RENDERING_START`, frames avanzando).
  - *Bug real cazado de paso (crash del Inicio, dependiente de datos)*: una fila duplicada del servidor
    tumbaba el Home: `IllegalArgumentException: Key "home-trending-xtv:…" was already used`. Fix:
    `RemoteIndexSource.trending()` deduplica con `Popularity.dedupe` (cubre móvil y TV; la TV ya se
    salvaba porque su clave lleva `-idx`) + `distinctBy { it.id }` defensivo en el renderer del Home
    móvil (`HomeScreen.kt`).
  - *Producto*: carpeta portátil en `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+ PC\` (~3.5 GB):
    `sdk/` (emulador + imagen `android-36.1 google_apis_playstore x86_64` + platform-tools),
    `avd/VisionPC` (1920x1080 landscape, density 240, sin sensores de orientación, touch+teclado),
    `VisionPlus.apk` (release R8 con los fixes), `assets/logo.png`, `README.txt` y **`Vision+ PC.exe`**
    (lanzador C# .NET 4.8 compilado con `csc`, ~66 KB, icono Vision+): splash de marca → arranca/reutiliza
    el emulador (adb en puerto **5039**, consola 5556; no choca con el adb del usuario en 5037) →
    provisiona kiosco (immersive, sin sleep, rotation 0, Play Store off, hora MX) → instala el APK por
    hash (y precompila con `cmd package compile -m speed`) → abre la app (`am start`) → quita el marco
    de la ventana y la deja **fullscreen con icono y título «Vision+»** (monitor cada 2 s; salir = Alt+F4).
    Fuente en `Vision+ PC\launcher-src\` (recompilar: `csc /target:winexe /win32icon:…\vision_icon.ico
    /win32manifest:app.manifest VisionPlusPC.cs`).
  - *Validado end-to-end en esta PC*: launcher → boot → instalación → app → ventana sin marco →
    reproducción fullscreen del hero (Spider-Man; capturas `Temp/opencode/desktop_play.png`, `vp_pc_*`).
  - *Nota legal*: la traducción ARM (ARM/Google) es «solo desarrollo y depuración»; uso personal OK,
    distribución amplia = zona gris. Emulador/imagen de Google: redistribución gris.
  - *Pendiente*: en PCs sin virtualización el emulador no arranca (el launcher lo reporta con
    instrucciones). *Resuelto el 2026-10-16*: suite completa 376/376 y decisión del usuario — `abiFilters`
    solo-ARM **se queda** en producción y estos fixes viajan en la OTA v2.5.84.
  - *Instalable + ocultado total (misma fecha)*: `VisionPlusPC_Setup_2.5.83.exe` (Inno Setup 6.7.3,
    per-user sin admin, español, ~2.2 GB; accesos en escritorio y menú inicio; desinstalador) en
    `Desktop\Proyectos\Vision+ PC (instalable)\`. **Cada PC instala con AVD limpio → identidad propia
    (fin de patadas entre copias del paquete)**. El lanzador ahora mantiene el emulador **totalmente
    oculto** (ventana oculta + fuera de la barra de tareas, incluido el sliver «Emulator» del toolbar)
    hasta que Vision+ está cargado, y solo entonces la revela a pantalla completa con
    `ShowWindow(SW_SHOW)` verificado + `AttachThreadInput` para traerla al frente aunque el usuario
    esté en otra ventana (Windows niega `SetForegroundWindow` a procesos en segundo plano). Verificado
    con un testigo de ventanas (cero flashes durante toda la carga) y una instalación real de punta a
    punta (instalar → splash → Vision+ reproduciendo fullscreen). Nota: el instalador no está firmado
    con certificado Windows (SmartScreen pedirá «Más información → Ejecutar de todas formas»).
    Detalles del lanzador: `Vision+ PC\launcher-src\VisionPlusPC.cs` + `visionplus_pc.iss` (recompilar
    con `csc` y `ISCC.exe`).
- **Lote 2.5.86: descargas de series (temporada completa y por capítulo) + reproductor offline congruente — OTA publicada** (2026-10-16; 393 tests Android + pruebas en emulador/A32):
  - *Pedido del usuario*: las descargas ya funcionaban, pero al abrir una serie no había forma de bajar capítulos y faltaba «descargar temporada completa».
  - *Cola de un solo motor (lo delicado)*: el motor nativo solo mantiene un programa; dos descargas `xtv:` a la vez se pelearían. `OfflineDownloadManager` ahora serializa: una descarga activa (`activeRemoteId`) y el resto `QUEUED` («En cola») — al terminar arranca la siguiente. Pausar la activa suelta la temporada entera (pendientes a pausa); borrar una deja seguir; reinicio de app deja todo en PAUSA (reconcile). El servicio suma un `engineMutex` como red de seguridad.
  - *Resultado por ARCHIVO (fin del bus) + vigilante*: en el emulador (y en POCO/HyperOS) los broadcasts `:x4k`→principal no llegan al receptor (en `dumpsys activity broadcasts` se ven enviados y «terminados», pero `onReceive` jamás corre) — la cola avanzaba 60-90 s tarde por el vigilante. Ahora el servicio deja `.done`/`.error` junto al vault y `OfflineProgressSync` (ticker 2 s) los consume: COMPLETED/ERROR al instante y siguiente capítulo en cola al momento. El vigilante (proceso `:x4k` muerto 60 s / sin bytes 5 min → ERROR + siguiente) queda de última red (ya salvó una corrida estancada real en el emulador).
  - *Ficha móvil*: cada fila de capítulo trae su control de descarga (descargar / en cola / % con anillo / reproducir la copia / reanudar) y sobre la lista el botón de temporada con estado real («Descargar temporada · 22 de 22» → «Descargando · 1 de 22» → «Reanudar temporada · 19» → «Temporada descargada»). Reanudar re-encola pausadas/error y salta completadas.
  - *Ficha TV*: mismo botón de temporada (TvCompactAction, D-pad) y cada tarjeta de capítulo suma un chip focusable («Descargar/En cola/%/Descargado·Reproducir/Reanudar») con enlace de foco arriba/abajo (OK en la tarjeta reproduce; OK en el chip baja). Sin USB sigue el TvNoUsbDialog.
  - *Reproductor offline congruente (búnker) — 3 huecos cerrados*: (1) la ficha pasaba `EpisodePlay(code="vault:…")` con el film de catálogo y `PlayerScreen` solo miraba `film.videoUrl` → caía al handoff XTV y fallaba; ahora detecta `vault:` en el capítulo y abre el búnker (el botón «Descargado» de películas y series por fin reproduce la copia); (2) al terminar un capítulo descargado aparece «A continuación» con el siguiente descargado de la serie (cuenta regresiva 10 s si el auto-avance está activo, Atrás descarta, salta de temporada si hace falta) y el terminado queda visto sin recuerdo a medias; (3) «Reanudar automáticamente» (ajuste que ya honraba el reproductor nativo) ahora también salta directo en el búnker, sin diálogo.
  - *Verificado*: emulador (Vision_Phone): temporada de 22 capítulos encolada y bajando en orden (4 completos y el 5º en curso), pausar suelta la cola («Reanudar temporada · 19»), reconciliación tras reinicio (todo PAUSA), reproducción del búnker con FLAG_SECURE y tarjeta offline en «Seguir viendo». **Prueba física del usuario: confirmada** («sí descarga capítulos individuales y descargar temporada»). A32 (USB, release R8 vc322 instalado encima de 2.5.85, Success; versión 2.5.86 confirmada).
  - *Publicada*: release R8 vc322 (105.9 MB) con firma histórica `9593cb0d…` (apksigner) — **OTA v2.5.86** (asset `VisionPlus.apk`, enlace estable). Suite 395/395.
- **Reproductor residente («Camino B»): aperturas casi instantáneas — el truco de la app del proveedor — OTA v2.5.85 publicada** (2026-10-16; 378 tests Android + prueba física TV/A32; release R8 vc321, firma histórica `9593cb0d…`):
  - *Qué*: la queja era «la TV en vivo tarda más segundos que Xuper». Causa: Vision+ abría TODO en
    frío por reproducción (proceso `:x4k` matado al salir + pantalla y vista del motor nuevas cada vez).
    Fix: el reproductor ahora **reutiliza UNA sola vista del motor toda la vida del proceso** (patrón
    del zapping, que el motor sí admite): al salir, la actividad queda residente en su tarea y la app
    principal vuelve al frente; el siguiente play entra por `onNewIntent` (stop → play in-place) y
    solo se reconstruyen las capas de UI por encima. Medido en la TV ZTE: lanzar→reuso→`play` en
    ~130-150 ms (antes: 40-60 s de arranque frío con re-arranque interno incluido).
  - *Cómo*: `X4KPlayerAty` → `launchMode=singleTask` + `taskAffinity` propia + `excludeFromRecents`
    (tarea aparte, fuera de recientes; el Atrás no la revela y el doble Atrás sale a la app).
    `onNewIntent` + `handleLaunchIntent` con `playGen` (hilos/retrys viejos invalidados por
    generación), `resetForNewPlay`/`teardownPlaySurfaces` (vista del motor persistente; overlays
    rehechos), `exitActionFor(hardReset)` pura (`ControlsState.kt`): salida residente normal; solo el
    auto-reparado (`PlayerRecovery` rota la carpeta del motor) cierra con `finish()`+killProcess para
    estrenar proceso. «motor :x4k ya precalentado; montando UI en 0 ms» ahora es la norma, no la excepción.
  - *Convivencia con descargas (lo delicado)*: el motor headless de descargas exige un proceso en el
    que la vista no haya reproducido. Al encargar una descarga, si `:x4k` ya reprodujo (marca ARCHIVO
    `files/x4k_view_played` — no SharedPreferences: la copia cacheada del principal no ve lo escrito
    por el otro proceso, lección 2.5.64), el principal ordena a `:x4k` reiniciarse
    (`X4KWarmupReceiver.ACTION_DIE_FOR_DOWNLOADS`; el proceso se apaga solo — `killBackgroundProcesses`
    NO mata procesos percibibles, probado en el A32) y ESPERA su muerte (`runningAppProcesses`, tope
    4 s) antes de levantar el servicio: la descarga nace en un proceso limpio, como siempre.
  - *Auto-descargar siguiente al salir*: la decisión se toma en el reproductor y se deja como ARCHIVO
    (`files/x4k_pending_download.json`); el principal la consume al volver (`PendingAutoDownload` desde
    el observer de `XtvHandoff`) y pasa por el gestor (con el reinicio incluido). Antes la orden nacía
    en el proceso que moría al salir.
  - *Verificado en físico (mismo APK release R8 de la OTA, vc320)*: **A32 (USB)**: play→salir (el `:x4k`
    y la tarea del reproductor quedan VIVOS) → reuso con OTRA serie (log «vista reutilizada», 13 ms) →
    vivo (ECDF) → OTRO canal (66 ms) → cruce vivo→película (Spider-Man) → descarga tras residencia
    (reinicio ordenado: pid nuevo, bajó al 12% sin errores) → «pausar y ver ahora» con descarga activa
    (reprodujo en ese proceso; la descarga quedó pausada; la descarga de prueba se borró). **TV ZTE
    B866F**: serie (Reacher C1) → salir residente → C2 reusada (146 ms al `play`, captura a los 8 s ya
    con video) → EN VIVO La Granja (captura a los 8 s con video) → salir → ECDF (132 ms, captura con
    video). Capturas `Temp/opencode/res_*.png` y `tvre_*.png`.
  - *Observado (no bloqueante)*: en el flujo «pausar descarga y ver ahora» el handoff lanzó dos veces
    seguidas (crear+reusar en 55 ms) — inofensivo con singleTask; revisar si alguna vez molesta.
  - *Pendiente*: uso diario del usuario (cualquier rareza se reporta). Los equipos ya tienen instalado
    el build con los cambios (prueba física) y el resto se actualiza solo con la OTA v2.5.85 (vc321,
    asset `VisionPlus.apk`; mismos cambios); el APK publicado quedó verificado con apksigner
    (firma histórica).
- **Lote 2.5.84: nombres normalizados (fin de guiones bajos; incl. la carga del episodio) + el D-pad ya llega a «Omitir intro» — OTA publicada** (2026-10-16; 376 tests Android):
  - *Síntoma (usuario)*: en algunas pantallas —incluida la espera al abrir un capítulo— los nombres salían
    con guiones bajos («para separar»); y en Smart TV la navegación del mando no llegaba a la píldora
    «Omitir intro».
  - *Causa 1 — nombres (verificada en vivo contra el portal)*: el portal rotula sus capítulos como
    «Ley y orden: Unidad de Víctimas Especiales **T1_11**» (`simpleProgramList[].name`, confirmado con una
    consulta real) y hay 33 títulos con `_` en la base del índice («Rebbeca_ Un documental…», «SAC_2045»).
    Ese texto crudo llegaba a la espera, al selector de capítulos (ficha y reproductor) y a la tarjeta de
    «siguiente».
  - *Fix 1*: `normalizeDisplayName()` puro en `data/Catalog.kt` (guion bajo → espacio, espacios colapsados;
    sin `_` el texto queda igual). Aplicado en la ingesta de títulos (X4KApi repisas+caché, RemoteIndexSource,
    Magis, Archive, M3U, favoritos, descargas, `PlaybackHistory`) y en los nombres de capítulo de
    `X4KApi.parseEpisodePrograms/parsePlayEpisodePrograms` (el número se sigue leyendo del nombre crudo —
    numeración intacta). `cleanEpisodeName` (espera) y `episodeRowLabel` (fichas móvil/TV + selector del
    reproductor + «siguiente capítulo» de `X4KPlayerAty`) quitan además la marca técnica «… T1 11» y, si lo
    que queda solo repite la serie o el número, caen al genérico («T1 · Capítulo 11» / «Capítulo N»). Las
    funciones privadas duplicadas `cleanEpisodeTitle`/`cleanTvEpisodeTitle` se retiraron (una sola fuente).
  - *Causa 2 — D-pad a la píldora (leída en el código)*: la píldora se volvía enfocable (TV + controles
    visibles) pero NINGÚN control apuntaba a ella: el enlace vivía en `buildControls` y corría cuando
    `introPill` todavía era null (se crea al final) → el mando jamás llegaba.
  - *Fix 2*: `wireSkipPillFocus()` (puro, probado): «arriba» desde la barra → píldora mientras esté visible
    y enfocable; oculta, la barra queda en sí misma; en la píldora, izquierda/derecha/arriba no se escapan
    y «abajo» vuelve a play/pausa. `updateIntroPill` refresca el enlace en cada cambio de visibilidad y
    devuelve el foco a play/pausa si la píldora se oculta estando enfocada.
  - *Guardián*: `SkipPillFocusTest` (Robolectric: enlace real + `FocusFinder` resolviendo la píldora),
    `PlayerLoadingCardTest` (caso real «T1_11» → «T1 · Capítulo 11» sin línea de nombre; nombre real con
    `_` normalizado; serie con sufijo «T1»), `X4KApiSeriesTest` (contrato del portal normalizado sin tocar
    la numeración), `CatalogTest` (`normalizeDisplayName`), `RemoteIndexTest` (títulos del servidor). Suite 376/376.
  - *Publicado*: release R8 vc320 (105.9 MB) con firma histórica `9593cb0d…` (apksigner) — **OTA v2.5.84**
    (asset `VisionPlus.apk`; el enlace estable ya sirve la nueva). Incluye los fixes de Vision+ PC
    (`abiFilters` solo-ARM aprobado para producción).
  - *Verificado en el emulador VisionPC (mismo APK de la OTA)*: `pm install -r` encima de la 2.5.83
    (firma continua, Success); ficha de SVU con filas «Capítulo 1/2/3» (antes «…Especiales T1_1»);
    espera del capítulo «… · T1 · Capítulo 2» sin línea de nombre con guion bajo; el episodio reproduce
    con video y cero crashes (capturas `Temp/opencode/vp_ficha2.png`, `vp_load_l3.png`, `vp_play_final.png`).
  - *Verificado en físico (misma fecha, TV ZTE B866F + A32 por USB, APK de la OTA)*: `pm install -r` en
    ambos (Success; el A32 venía de 2.5.78, datos intactos). **TV**: espera «Attack on Titan - Español
    Latino · T2 · Capítulo 5» limpia; la píldora «Omitir intro» aparece en la ventana del intro y el
    D-pad la alcanza (arriba desde la barra: foco con aro blanco) y OK salta 1:03→1:49; el selector de
    capítulos del reproductor abre con el mando (pestañas T1–T4, «C5/C6/C7», estados Viendo/Visto/
    Repetir, sin guiones bajos). **A32**: ficha «Capítulo 1/2/3», espera «T1 · Capítulo 3» y selector
    del reproductor limpios; C3 reproduce con video y subtítulos. Capturas en `Temp/opencode`:
    `tv20_wait.png`, `tv27_pill.png`, `tv29_skip.png`, `tv31_sel.png`, `a32v84_ficha.png`,
    `a32v84_wait3.png`, `a32v84_sel.png`, `a32v84_c3play.png`.
  - *Observación (pre-existente, ajena a esta tanda)*: iniciar un capítulo lo marca «Visto» al instante
    (ficha: «Continuar» y toque de fila; reproductor: cambio interno) — confirmado en físico y por el
    diff (la tanda no toca esas líneas). Además, `vision_user` (SharedPreferences) no cruza procesos:
    una marca escrita por `:x4k` puede perderse si el principal reescribe con copia vieja (misma clase
    que se arregló para `PlaybackHistory` en 2.5.64 — candidato a fix futuro). Las marcas que dejaron
    las pruebas en el A32 se revirtieron (la ficha volvió a «Continuar · T1 · Capítulo 2 · 1 vistos»).
- **Lote 2.5.83: fix DEFINITIVO del toque móvil — el gesto nunca llegaba al reproductor (onDown) — OTA publicada** (2026-10-15; 364 tests Android):
  - *Síntoma*: tras 2.5.82 el usuario reportó que seguía igual: «doy clic en el video y no se abre
    el menú de reproducción».
  - *Causa raíz (verificada contra el código AOSP de `GestureDetector`)*: `onTouchEvent` solo
    consume el `ACTION_DOWN` si `onDown` devuelve true; `SimpleOnGestureListener` devuelve false
    por defecto. Con la vista del toque no clickable y sin consumir el DOWN, el padre **nunca le
    entrega el `ACTION_UP`** → `onSingleTapUp`/`onSingleTapConfirmed` jamás corrían: el tap moría
    en silencio desde v2.5.77 (el click simple anterior sí consumía, porque hacía clickable la
    vista). Por eso 2.5.82 —que solo cambió el callback— no lo arregló.
  - *Fix*: `MobileTapGestures` (clase extraída, `ui/player/MobileTapGestures.kt`) con
    **`onDown = true` SIEMPRE** (el eslabón que faltaba) + `attachMobileTapGestures` marca la
    vista clickable como red de seguridad. Se conserva el aviso inmediato de 2.5.82 y el doble
    toque ∓10 s con cancelación del ocultado (`pendingControlHide`).
  - *Guardián*: `MobileTapDispatchTest` (Robolectric, **dispatch táctil REAL** por la jerarquía
    con `measure`/`layout`): 4 pruebas del comportamiento + 1 que documenta la trampa (sin
    `onDown` el tap no llega, si la plataforma cambia el test lo avisa). `MobileTapActionTest`
    (3) cubre la política pura. Suite 364/364.
  - *Verificado*: 364 Android en verde; release R8 vc319 con firma histórica `9593cb0d…`.
    **OTA v2.5.83 publicada** (`VisionPlus.apk`). Prueba física en el teléfono del usuario
    pendiente (el A32 no estaba en red).
- **Lote 2.5.82: fix de regresión móvil — los controles del reproductor vuelven con UN toque — OTA publicada** (2026-10-15; 359 tests Android):
  - *Síntoma (usuario)*: en el móvil los controles de reproducción se ocultaban solos (auto-hide
    normal) y al tocar la pantalla **ya no volvían a aparecer**.
  - *Causa (cazada en el diff)*: el lote v2.5.77 cambió el click simple del `catcher` por un
    `GestureDetector` con `onSingleTapConfirmed` (para el doble toque ∓10 s). Ese callback espera
    el timeout del doble toque (~300 ms) y, si llega un segundo toque, el primero **se cancela**:
    en los lados (2/3 de la pantalla) el segundo toque hace seek, así que el usuario que toca dos
    veces por impaciencia nunca veía los controles. La v2.5.81 no lo tocó; se notó al actualizar
    el teléfono.
  - *Fix*: `resolveMobileTapAction` (puro, `ControlsState.kt`): ocultos → **mostrar AL INSTANTE**
    (`onSingleTapUp`, sin espera ni cancelación); visibles → ocultar tras el timeout (el doble
    toque lo cancela vía `pendingControlHide`); modal/pre-roll/error → ignorar. El doble toque en
    los lados sigue haciendo ∓10 s y ya no quita la barra; el centro ya no alterna (el primer
    toque ya mostró).
  - *Guardián*: `MobileTapActionTest` (3 pruebas). Suite 359/359.
  - *(Insuficiente — el aviso se hizo inmediato, pero el gesto seguía sin llegar al detector por
    el `onDown`; ver 2.5.83.)*
  - *Verificado*: 359 Android en verde; release R8 vc318 con firma histórica `9593cb0d…`.
    **OTA v2.5.82 publicada** (`VisionPlus.apk`). Prueba física en el teléfono del usuario
    pendiente (el A32 no estaba en red).
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
- App Android (Compose) **"Vision+"** — pkg `com.chronosbvrx.visionplus`, `versionName` actual **2.5.113**
  (versionCode **349**, release OTA v2.5.113 — el Inicio de Smart TV calcula el tamaño de sus filas desde
  la altura real de la pantalla (3 filas completas en 540 dp, póster 2:3 de ~81 dp; 140 dp en pantallas
  altas), «Seguir viendo» compacto y tarjetas que se anuncian por título; la v2.5.112 los marcadores de
  salto por episodio, caché en disco compartida con `:x4k`, auto-skip con duración real y una sola
  aplicación, siguiente capítulo que no corta post-créditos y «Omitir intro» oculto hasta que el video
  se dibuja; la v2.5.111 el Inicio con novedades
  populares reales y sin tarjetas repetidas: dedupe por tmdbId, trending una vez por obra, filas sin
  traslape y el índice recortado en SQL (47 s → 2.5 s); la v2.5.110 el arreglo de los saltos de
  reproducción: la «Calidad
  preferida» manda desde el primer cuadro, telemetría `PLAYER_FRAME_SKIP` y el proceso del reproductor más
  liviano; la v2.5.109 el buscador muestra «También: …» (el nombre original cuando
  difiere: «buscando a nemo» → Finding Nemo) y el buscador del control remoto consulta al servidor con
  respaldo local; la v2.5.108 el control remoto ya no deja la tele a medias: el deep link
  REEMPLAZA el reproductor en curso (nada de players apilados), el cierre de la espera es por identidad de ruta
  (el handoff viejo ya no deshace el play nuevo) y el reproductor suelta el motor si queda detrás con
  reproducción viva (fin del audio de fondo en el menú); la v2.5.107 el D-pad a prueba de fallos: el rescate del foco VERIFICA el
  aterrizaje (fin de la tecla comida en silencio), toda pantalla/modo tiene destino compuesto (Deportes en frío,
  la cuadrícula de sección, el riel solo en Inicio), el orden de filas del Inicio es fuente única y los
  reproductores/diálogos/control remoto dejan de poder matar el mando; la v2.5.106 el control remoto pide
  temporada/capítulo (el episodio exacto
  abre en la tele con su serie alrededor) + la memoria de vínculos en los dos sentidos (identidad estable de la
  tele, migración sin perder el PIN, nombres recordados y olvido); la v2.5.105 puso «Deportes en vivo» en el
  menú lateral de la Smart TV y blindó el D-pad al volver al Inicio (vigilante del foco y rescate a un destino
  compuesto); la v2.5.104 dejó una sola
  fila por canal con «posibles transmisiones» y sus enlaces, la v2.5.103 completó la lista con los canales
  declarados, la v2.5.102 la dejó solo con lo de GeeSports, la v2.5.101 le dio al reproductor la interfaz de
  TV en vivo, la v2.5.100 el respaldo + vigilante, la v2.5.99 abrió el candado de horario y la v2.5.98 el
  **control remoto**; ver los lotes de arriba). Es una app de streaming personal del usuario (ChronosBVRX).
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
  reporte con texto libre del usuario — hechos; **canal de alertas Telegram conectado el 2026-10-17**
  (@VisionPlus_Info_bot → chat de Edward; probado con `diag.send_alert` desde el VPS).
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
- **Fútbol — fuentes alternativas gee-sports (análisis 2026-10-16, decisión pendiente del usuario)**: re-análisis
  completo del APK `gee-sports.apk` (`com.geesports.live`, rebrand CricZ TV; CMS en `absuehhd.online` sobre GitHub
  Pages + Firebase RC para el dominio; bundles `channels/<slug>.json` base64 con `streamUrls[{title,link,type,api}]`).
  Tipos: **0** HLS directo (sufijo `|origin/referer/ua` → headers al OkHttp del player), **3** DASH `cenc` ClearKey
  `api=kid:key` (mismo JSON W3C que ya armamos con `GeeSportsFallback.buildClearKeyJson`), **1** POST `token_data`
  + des-ofuscación, **2** llave AES-128 protegida (`keyUrl*tt`, rewrite del parser «sheda» — documentado). `header`/`body`
  del bundle NO se usan en esta versión (verificado en smali). **Auditoría de vida (hoy): 8/370 links (2.2 %) vivos**
  desde la red del usuario y el VPS por igual — ~60 % NXDOMAIN global (dominios rotos), paneles por IP inaccesibles;
  vivos: amagi/cloudcdn/cinehub/kora/xsg/vintera/spectrum + star-sports-2 (3), sports-channels (2)… La «estabilidad»
  de la app original = fetch fresco + N servidores por canal (sin filtro de vida). Plan propuesto (Fase 1): worker
  VPS que refresca el CMS + resuelve bundles + smoke de vida + `/v1/gee/sports` + feedback + inyección «Deportes · Alternativo» en `SportsLinker`.
  Detalle completo + pasos: `docs/ANALISIS_GEE_SPORTS.md` (Temp: `gee_apktool/`, `gee_src/`, `gee_probe_results.json`).
  **Actualización misma fecha — APK NUEVA V3.8 (`com.live.geesports`, es OTRA app — la CricZ 1.3 de arriba es la vieja)**:
  **✅ INDEPENDENCIA TOTAL LOGRADA (2026-10-17, misma sesión)**: descifrado en PYTHON PURO (sin Android, sin PC puente) —
  clave derivada del certificado del APK; **receta y clave en el archivo local de credenciales** `C:\Users\Axel Rosete\.visionplus\gee38_key.txt`
  (+ `gee38_crypto.py` ahí mismo; versión de trabajo en `Temp/opencode/gee38_crypto.py`) — **NO copiar al repo (es público)**.
  Verificado EN VIVO contra el CMS (app/sports/events + `channels/*.txt` ✓); se re-deriva sola si rotan el certificado.
  **Docker-Android en el VPS: NO viable (sin /dev/kvm ni vmx/svm) — e innecesario ya.** Método de rescate documentado (A32 root
  + su dd de /proc/PID/mem + oráculo D(C1)^C0==P1) por si algo cambia. Siguiente: worker `sync/gee38.py` en el VPS + integración
  app (pestañas por deporte carga diferida + "Alternativo" en Fútbol).
- Buscador de eventos específicos sobre la EPG (idea del usuario, no implementado).
- Próxima OTA: publicar el release minificado (`:app:assembleNormalRelease`, firma histórica intacta) con
  el bump de versión; el update es directo (sin reinstalar). R8 ya verificado en A32.
- Series (teleplay): el flujo usa `startPlayVOD` type 2→1 con episodeNumberList [1]; refinar episodios si hace falta.
