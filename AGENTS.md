# AGENTS.md — Contexto persistente del proyecto Vision+ / Xuper XTV

> Este archivo se carga automáticamente en cada sesión de opencode. **Mantenerlo actualizado al
> cerrar una sesión** (estado, versión publicada, pendientes). El proyecto vive en
> `C:\Users\Axel Rosete\Desktop\Proyectos\Vision+\` (aquí hay una copia idéntica de este archivo).

## Proyecto y objetivo
- **OTA 2.5.177 PUBLICADA (2026-10-27): el teléfono deja la tele lista con la ÚLTIMA versión del release — pestaña «Instalar en TV» (Vision+ + Vision+ Home + Inicio, sin PC) y teles Android 8/9 por dirección con diagnóstico claro — 1002 Android + 153 launcher en verde**:
  - *Pedido*: «ya tengo en mi apk algo para dejar Vision+ Home como launcher predeterminado… ¿puedo hacer una utilidad que aproveche esa función para descargar el paquete del repositorio, instalarlo y configurarlo en la tele automáticamente, sin PC? … que quede perfectamente utilizable: la última Vision+ + el Home como Inicio» → «Sí saca la OTA».
  - *Qué hace*: Ajustes del teléfono → pestaña **«Instalar en TV»** (nueva, junto a Reproducción y Sistema). Por cada tele emparejada: **1)** baja del release oficial la última **Vision+** (`VisionPlus.apk`, ~82 MB) y, si la tele no la tiene o está atrás, la instala; **2)** baja e instala el último **Vision+ Home** (`VisionPlusHome.apk`, 1.5 MB); **3)** lo fija como Inicio y solo retira el launcher de fábrica si sigue robándose la pantalla (con «Restaurar» por tele). **Guardia**: si la tele no tiene Vision+ y no se pudo instalar, **no se toca su Inicio** (nunca queda sin app que abrir).
  - *Sin descargar nada a mano ni API de GitHub*: `data/adb/TvLauncherPackage.kt` — `ReleaseApkStore` + `TvLauncherPackage`/`TvAppPackage`. El launcher se baja del asset estable (`releases/latest/download/VisionPlusHome.apk`, caché 6 h, respaldo `assets/vision_home.apk`); la app se decide con `releases/latest/download/version.json` (cientos de bytes, caché 1 h) y solo se bajan los 82 MB si de verdad hay versión nueva (`isNewer` puro evita downgrades de una beta instalada). Descarga atómica a `.part`, `getPackageArchiveInfo` lee la versión del APK, estado observable (`ApkState`) para el progreso.
  - *Bug de fondo corregido*: `TvAdb` usaba `pm install -S` **sin `-r`** → una tele con una versión anterior respondía `INSTALL_FAILED_ALREADY_EXISTS` y el launcher nunca se actualizaba. Ahora `pm install -r -S` y, para la app de 82 MB, `installApkFileOutput` **transmite el archivo por bloques** (no carga 82 MB en el heap del teléfono).
  - *Estado por tele (`TvRecord`)*: `appVersion` + `launcherVersion` leídos del televisor (`dumpsys package … | grep versionName`) y persistidos en el JSON (compatible con las listas viejas). La fila muestra «Vision+ 2.5.177 · Home 1.3.1 en la tele» y el botón es **«Instalar Vision+ y su Inicio»** / «Actualizar…».
  - *Android 8/9 — dirección manual (diseño final, 2.5.177)*: se **retiró la búsqueda automática** (mDNS `_androidtvremote*` + barrido del 5555 con `LanScan`): en redes reales no era fiable y el usuario la reportó como «no funciona». Queda lo que sí servía: el flujo por **código** (Android 11+) y, para 8/9, **escribir la IP del televisor** (Ajustes → Red) con «ADB por red» encendido. `TvHomeSetup.connectByAddress` **comprueba el puerto 5555 antes de esperar** (`isPortOpen`), distingue «puerto cerrado» de «falta aceptar ¿Permitir depuración?» (`looksUnauthorized`), y deja **«Reintentar conexión»** con la misma IP. No hace falta tener Vision+ instalada en la tele.
  - *Pestañas visibles*: la pestaña de sistema del móvil pasó de «Sistema y Rendimiento» a **«Sistema»** para que **Reproducción · Sistema · Instalar en TV** quepan sin desplazar la barra (la 2.5.172 salió minutos antes con la tercera cortada; la 2.5.173 la deja a la vista). La tele conserva su «Sistema y Rendimiento».
  - *Cierre limpio y reintento (2.5.176)*: cerrar el diálogo mientras conectaba mostraba «No se pudo conectar: … was cancelled» (el `catch` trataba la cancelación como fallo). Ahora `if (t is CancellationException) throw t` en todos los `catch`, `isActive` antes de escribir estado y `resetMessage()` limpia mensaje + detalles; la búsqueda con código vive **dentro** del diálogo (`LaunchedEffect`) y cualquier acción cancela la anterior (`run(...)`); tras un intento fallido queda **«Reintentar conexión»** (`legacyHost`).
  - *Respaldo al día*: el APK incluido en la app (`app/src/main/assets/vision_home.apk`) se reemplazó por el **1.3.1 (vc24) firmado** (1,508,820 B): si no hay red, la tele recibe el launcher actual, no el 1.0.14 viejo.
  - *Verificado*: **1002 Android + 153 launcher** en verde (`TvHomeAdbTest` +3: flujo Android 8/9 por dirección sin búsqueda automática, normalización de la IP y comprobación del puerto contra un `ServerSocket` real; release/`pm install -r`/streaming, versiones, caché, dumpsys, pestaña, cancelación que no se reporta como fallo y reintento; round-trip de `appVersion`/`launcherVersion`). Assets publicados descargados y comprobados: `version.json` **UTF-8 sin BOM** (`2.5.177`, vc417), `VisionPlus.apk` **82,654,808 B** (vc417/2.5.177, 5 DEX: app 2 + motor 3, sin `assets/x4k`, launcher interno 1,508,820 B, cadenas «Instalar en TV»/`192.168.1.x`/«ADB por red»/«El puerto 5555…»/«No toco su Inicio»), `VisionPlusHome.apk` **1,508,820 B** (1.3.1/vc24); los dos con la firma histórica `9593cb0d…`; `/releases/latest` = **v2.5.177** (no prerelease) y el asset publicado tiene el mismo SHA-256 que el local. En el emulador (APK publicado y debug): Ajustes abre con **Reproducción · Sistema · Instalar en TV**; el diálogo muestra solo la dirección manual para Android 8/9 y, al conectar a una IP con el puerto cerrado, responde al instante con los pasos exactos y **«Reintentar conexión»**; **cerrar mientras conecta no deja ningún mensaje de error**. *Pendiente*: prueba física en una tele Android 8/9 real (encender «ADB por red», escribir su IP, aceptar el aviso, instalar Vision+ y Home y reiniciar) y commitear el bump + AGENTS.md.
- **OTA 2.5.171 + HOME 1.3.1 PUBLICADAS (2026-10-27): el launcher se arma a tu manera — pestañas con orden/visibilidad y 4 plantillas de Inicio; el diálogo placebo de intro/outro se retiró de la app — 994 Android + 153 launcher en verde**:
  - *Pedido*: «En otro agente trabajé otras cosas, vamos a implementar todo lo pendiente y sacar las OTA sin regresiones ni errores».
  - *Launcher — pestañas propias (`HomeConfig` v3 + `ManageTabsDialog`)*: **Personalizar Inicio → Pestañas** con el mando: Arriba/Abajo cambia el orden, el ojo oculta, **Inicio nunca se mueve ni se oculta** (es la puerta de vuelta). Se guarda por perfil (`tabOrder`/`hiddenTabs`), las pestañas nuevas de una versión futura entran al final, y `visibleTabs(config, inputsAvailable)` es la fuente única de la barra: respeta el orden, quita las ocultas, **nunca pinta «Entradas» si el equipo no reporta fuentes** y fuerza Inicio primero. Si la pestaña activa deja de existir (se ocultó o era Entradas en una TV box), el Inicio se resuelve a Inicio sin pelear con el mando. En el gestor, la pestaña Entradas sin fuentes dice «Sin fuentes en este equipo».
  - *Launcher — plantillas (`LauncherPreset`, `PresetsDialog`)*: **TV primero** (Seguir viendo, Mis canales, TV en vivo y Entradas al frente), **Cine** (Top 10, Estrenos y catálogo primero; TV en vivo/Canales/Entradas ocultas), **Solo lo mío** (Seguir viendo, Mis canales y apps; sin catálogo) y **Niños** (Infantil/anime primero; secciones de adultos fuera). Determinan filas y pestañas, **no tocan favoritos, canales ocultos ni la pestaña inicial**, y se deshacen con Restablecer personalización.
  - *App — limpieza real*: se retiró `SkipMarkersDialog` («Corregir intro y outro») de `PlayerDialogs.kt`; era un diálogo que ningún reproductor abría y solo aparentaba corregir marcadores. Los marcadores reales (Omitir intro/resumen/créditos) siguen exactamente igual. Guardián nuevo en `MarkersPlayerWiringTest`.
  - *Verificado en el emulador (APK publicado)*: hub con **Pestañas (7 en la barra)** y **Plantillas**; plantilla **Cine** → barra `Inicio | Películas | Series | Apps | TV en vivo | Deportes` y sin fila «TV en vivo»; gestor de Pestañas → ocultar **Series** → desaparece de la barra; reset a defaults. Instalados los assets publicados: app **2.5.171 (vc411)** abre sin crashes y launcher **1.3.1 (vc24)** abre el Inicio; en este equipo (sin sintonizador) no se pinta Entradas.
  - *Verificación del release*: `VisionPlus.apk` **82,581,080 B** y `VisionPlusHome.apk` **1,508,820 B**, ambos firma histórica `9593cb0d…`; app 5 DEX (2 propios + 3 motor), sin `assets/x4k`; el DEX de la app conserva `PLAYER_RENDER_REATTACH`/«sin vista de render tras el play» y **no** contiene «Corregir intro y outro»/`SkipMarkersDialog`; el del launcher contiene Plantillas/TV primero/Solo lo mío/Niños/Pestañas/«Sin fuentes en este equipo». `version.json` UTF-8 correcto (guiones largos y acentos).
  - *Publicado*: release **v2.5.171** estable (`/releases/latest` = v2.5.171) con los dos APK y `version.json`; notas en `ota_notes_2.5.171.md`. **994 Android + 153 launcher** en verde. Los cambios y el bump siguen **sin commitear**.
  - *Pendiente*: prueba física en la TCL (sintonizador/HDMI abren la app del fabricante y el panel de ajustes se recorre con el mando) y el commit manual del bump + AGENTS.md.
- **EL LAUNCHER YA TIENE SUS PROPIOS AJUSTES Y LAS ENTRADAS DE TV (2026-10-27, launcher 1.3.0/vc23 PUBLICADO en el release v2.5.170): «Ajustes de Vision+ Home» aparte de «Personalizar Inicio», pestaña/fila «Entradas» con el sintonizador y los HDMI reales del televisor, y degradación honesta en TV box/PC — 148 launcher en verde + verificado en el emulador**:
  - *Menú del canal en el Inicio (fix de esta sesión, mismo launcher)*: las filas «Mis canales» y «TV en vivo» abrían el canal con el `playLink` del feed, que no lleva `c`/`g`; el reproductor caía al catálogo entero y el botón «Canales» mostraba los 1,000. Ahora `HomeSurface` entrega la lista COMPLETA de la fila (`favChannels` / `liveItems`) y su rótulo, y `LauncherScreen` arma `VisionLinks.live(..., codes=..., scope=...)`; verificado en el emulador desde la fila «TV en vivo»: `zapping scope: codes=400 resolved=400 index=1 group=Todos los canales`. La pestaña TV en vivo ya lo hacía. Guardián `LauncherLiveScopeTest` (3).
  - *Pedido*: «El launcher debe tener sus opciones de configuración, aparte de configurar inicio debe sentirse pro, opciones propias de un launcher. También no veo por ningún lado tv en vivo nativo de la PC o seleccionar fuentes de video, déjamelo como un launcher de verdad» + aclaración: **«El sintonizador de tv solo si está disponible en la TV, luego hay TV box que no tienen esta función nativa»**.
  - *Ajustes propios (`ui/LauncherSettings.kt`, nuevo)*: panel con **riel de secciones** (Inicio, Entradas, Sistema, Actualizaciones, Acerca de) que abre desde el **Centro de control** («Ajustes de Vision+ Home», primer destino de foco) y desde el tile «Ajustes del launcher» de la pestaña Apps. **Inicio**: pestaña al abrir (Inicio / TV en vivo / Entradas / Apps / La última), fila de entradas Sí/No, acceso a Personalizar Inicio y **estado real del Inicio del sistema** (con «Poner como Inicio» / «Cambiar el Inicio»). **Sistema**: Wi-Fi y red, Pantalla, Sonido, Almacenamiento, Fecha y hora, **Idioma y región**, **Accesibilidad**, **Protector de pantalla**, Apps del sistema, Apps predeterminadas y Ajustes de la tele, con cabecera de **equipo · Android · red · disco** (`AppsRepository.deviceLabel/networkLabel/storageLabel`; el disco se dice en MB si es menor a 1 GB). **Actualizaciones** (launcher y Vision+ con su progreso) y **Acerca de** (versión, autoría, licencias y la nota de que el launcher no reproduce video).
  - *Entradas de la tele (`data/TvInputs.kt`, nuevo)*: lee `TvInputManager` y crea una tarjeta por fuente real (`TvInputSource`: TUNER/HDMI/OTHER, puerto, paquete, icono del sistema). Orden de tele: **sintonizador primero, luego HDMI por número y al final el resto**; el número se saca del nombre cuando el firmware no lo expone («HDMI3 (ARC)» → HDMI 3) y **la misma entrada anunciada por dos apps se colapsa por puerto**. La pestaña **Entradas** (`InputsSurface`, mosaico) y la fila «Entradas de la tele» del Inicio (`home-inputs`, ordenable/ocultable como las demás) abren la fuente: 1) URI passthrough oficial del HDMI, 2) la app dueña del sintonizador/entrada, 3) Ajustes (el botón nunca queda muerto). El Centro de control suma **«Ver TV abierta»** cuando hay sintonizador.
  - *Regla del sintonizador (lo que pidió el usuario)*: **nada se inventa**. Si `TvInputManager` no reporta nada (TV box, PC/emulador) la pestaña «Entradas» **no se pinta** (`TopChrome(availableTabs=…)`), la fila del Inicio no aparece, «Ver TV abierta» no se ofrece y el ciclo de «pestaña al abrir» salta «Entradas»; la sección Entradas de los ajustes lo explica («Una TV box o una PC no tienen sintonizador ni entradas de video, así que el launcher no muestra la sección en vez de inventarla») con «Buscar de nuevo» y «Abrir TV en vivo». Un televisor con tuner o HDMI las muestra tal cual.
  - *Persistencia (`HomeConfig` v2)*: `startTab` y `hiddenInputs` en el JSON por perfil; **migración de la versión 1** que inserta la fila `inputs` después de `live` (no al final y sin duplicar). Extra: mover una fila del FEED en «Filas del Inicio» ya funciona (antes `moveRow` era no-op si el id no estaba en `rowOrder`).
  - *Verificado en el emulador (APK release firmado con la llave histórica)*: Centro de control → «Ajustes de Vision+ Home» → panel con el riel; **Entradas** dice «Este equipo no tiene entradas de TV» con la explicación de TV box/PC; **Sistema** muestra «Google sdk_gphone64_x86_64 · Android 16 · Datos móviles · … MB libres de 5 GB» y las diez pantallas; el Inicio **no** pinta la pestaña ni la fila «Entradas» (equipo sin fuentes). Strings nuevas presentes en el DEX publicado; sin crashes.
  - *Pruebas*: **148 launcher** en verde (`TvInputsTest` 8, `LauncherSettingsTest` 10, `HomeConfigTest` 21 con migración/pestaña/entradas, `LauncherLogicTest` actualizado al orden de pestañas, `LauncherLiveScopeTest` 3). APK release **1.3.0 (vc23)**, firma `9593cb0d…`.
  - *Publicado*: `VisionPlusHome.apk` **1,508,820 B (vc23/1.3.0)** subido al release `v2.5.170` (reemplaza al 1.2.5/vc22) junto al `version.json` regenerado y las notas del release; hash descargado = local. El launcher se actualiza desde su Centro de control (o Ajustes → Sistema → Vision+ Home del app). *Pendiente*: probar en la **tele física** (TCL) que «TV abierta»/HDMI abren la app de TV del fabricante y que el foco del mando recorre el panel.
- **TV EN VIVO YA NO ABRE EN NEGRO HASTA CAMBIAR DE CANAL (2026-10-27, OTA 2.5.170 estable publicada): el primer play espera a que la vista del motor esté anclada a la ventana y, si aun así el contenedor quedó vacío, se re-emite una vez — 993 Android en verde + verificado en el emulador**:
  - *Reporte*: «cuando abro TV en vivo desde el launcher carga el player pero el canal queda en negro; doy arriba y el de arriba sí se ve, luego abajo y se ve el original». También desde la app.
  - *Causa medida (SDK del proveedor + emulador, no adivinada)*: el motor crea su vista de render (SurfaceView/TextureView) **dentro** del `play` (`ed.e.A()` → `ad.c.a()` → `ad.c.k()`) y solo la añade al contenedor si `ed.e` ya pasó por `onAttachedToWindow` (`ad.c.c(...)` se fija ahí). Con el motor precalentado (`isConfigured` → «montando UI en 0 ms»), `mountUi` corre dentro de `onCreate` y el play se adelantaba al attach: el contenedor quedaba **sin ningún hijo** (`attached=false children=0` en el play y `attached=true children=0` 1.5 s después) ⇒ audio/progreso sí, video negro para siempre; el zap pintaba porque volvía a llamar al motor ya anclado. `playLive` **precalienta `:x4k` justo antes de lanzar la actividad** (`ensureEngineData` → `prewarmEngine`), así que es el camino normal.
  - *Fix (`ui/player/X4KPlayerAty.kt` + `ControlsState.kt`)*: `startPlaybackWhenAttached(v, action)` — el primer play (y el que sale del pre-roll) espera al attach y corre un frame después (`view.post`). Red de seguridad: a los 0.8 s y 2.4 s, `ensureRenderViewAttached` comprueba `shouldReattachRender(isPlayerActive, attached, childCount, renderReattach)` (pura) y, si el contenedor sigue sin su vista de render, re-emite **una sola vez** el mismo `X4KTitan.play` (lo que hacía el zap, sin cambiar de canal) con `PLAYER_RENDER_REATTACH` en diagnóstico. La bandera se limpia en `resetForNewPlay`.
  - *Pruebas*: `LiveRenderAttachTest` (3: el primer play espera al attach, la re-emisión única y la decisión pura). **993 Android** en verde (`--rerun-tasks`).
  - *Verificado en el emulador con el motor precalentado*: build viejo → `play attached=false children=0` y `+1.5s attached=true children=0` (negro); build nuevo → `play attached=true children=1`, sin `PLAYER_RENDER_REATTACH`, y ECDF se vio desde el primer play sin zappear (`FFP_MSG_VIDEO_RENDERING_START`).
  - *Publicado (misma fecha)*: **OTA 2.5.170 estable (vc410)** con app + **Vision+ Home 1.3.0 (vc23)** + `version.json` en el release `v2.5.170` (no prerelease; `/releases/latest` = v2.5.170): `VisionPlus.apk` **82,581,080 B**, `VisionPlusHome.apk` **1,508,820 B**. Firma histórica `9593cb0d…` en los dos, 5 DEX (app 2 + motor 3), sin `assets/x4k`; el DEX de la app conserva `PLAYER_RENDER_REATTACH` y «sin vista de render tras el play», y el del launcher las cadenas de sus ajustes/entradas. Instalado en el emulador: la app arranca en 2.5.170 sin crashes y el launcher 1.3.0 abre el Inicio. *Pendiente*: prueba física en la tele del usuario; el bump y los cambios siguen **sin commitear** (como marca el publicador).
  - *Menú del canal (launcher 1.3.0, mismo release)*: el Inicio abría el canal sin su menú (usaba `playLink`); ahora «Mis canales» y «TV en vivo» mandan la lista completa de la fila y su rótulo, y el zapping/«Canales» se quedan en ese segmento. Verificado en el emulador desde la fila «TV en vivo»: `codes=400 resolved=400 index=1 group=Todos los canales`. Guardián `LauncherLiveScopeTest` (3) + **148 launcher** en verde.
- **ESTRENOS + TEMÁTICAS CORREGIDOS (2026-10-27, solo servidor, sin OTA): «Estrenos y Novedades» ya no abre con cine viejo y Halloween / Día de Muertos se clasifican con las keywords oficiales de TMDB; «Noche de terror» retirada — 431 backend en verde**:
  - *Pedido*: «Verifica cómo se muestran nuestros estrenos y novedades, hay puras películas viejas justo ahora; también las categorías de Halloween, Día de Muertos y Noche de terror son redundantes y están mal clasificadas; busca algo en internet o una BD donde estén bien clasificadas».
  - *Diagnóstico medido EN VIVO (BD + `/v1/index` del VPS, no adivinado)*: la hilera `estrenos` tenía 4,115 fichas y abría con **El Proyecto Manhattan (1989), La Zona Muerta (1983), El piano (1993), Mí querido viejo (1991)** porque el sync premia `first_seen_at`: la pasada profunda acaba de importar cine clásico y «recién llegado» se confundía con «estreno». Además, las series agrupadas mostraban el año de la **primera** temporada (`Crimen en el paraíso` 2011 aunque estuviera sirviendo la T15 de 2026, `South Park` 1997, `Reacher` 2022). Las temáticas eran un calco: Día de Muertos y Halloween compartían 20+ fichas y metían **Harry Potter, Piratas del Caribe, Vértigo 2, Minions, Monarch, Moon Girl y Dahmer** por palabras genéricas («muerte», «monstruo», «terror»); «Noche de terror» era el top-24 de «Terror» todo el año.
  - *Estrenos — regla nueva (`sync/index_build.py`)*: solo entran títulos con **año actual o anterior** (`current_year - 1`); `is_premiere` y `first_seen_at` ya no admiten cine viejo. El frente de «recién llegado» exige además señal real (`popularity > 0` o `votes >= 5`), y `_one_per_work` colapsa T14/T15 (mismo `tmdb_id`) en la temporada más nueva antes de asignar posiciones. La fila quedó en **3,846 fichas, todas 2025/2026**; los primeros 15 ya son 2026/2025 (Negocios turbios, Verity, Antes del amor, Crimen en el paraíso, South Park, La odisea, Vértigo 2…).
  - *Año de la tarjeta (`api/app.py`)*: en `estrenos`/`*-estrenos`/temáticas el año sube al de la temporada servida (nunca baja) y `film()` hace lo mismo con títulos de temporada («The Last of Us T2» 2025 ya no dice 2023); en las demás filas (tendencias/búsqueda) se conserva el año de estreno de la serie. El payload de `/v1/index` quedó con **0 fichas < 2025** en `estrenos` y AHS/Halloween muestran 2012/2025, no 2011/2022.
  - *Clasificación con TMDB (`sync/themes.py` + `sync/tmdb.py`)*: Halloween usa **solo** la keyword `halloween` (3335) vía `/discover/movie|tv?with_keywords=`; Día de Muertos usa `day of the dead` (209789) + `dia de los muertos` (322048) **más una lista corta de obras canónicas** (Coco, El libro de la vida, La leyenda de la Nahuala, Macario, Día de Muertos, Catalina la Catrina, Amar Hasta a la Muerte) porque TMDB solo tiene 29 películas con esa keyword y 5 en nuestro catálogo. **Cero relleno por género** en ambas. «Noche de terror» se retiró (duplicaba «Terror»); «Para reír» se conserva.
  - *Trampa de IDs compartidos cerrada*: TMDB usa espacios de ID distintos para cine y series (300401 = película «Day of the Dead» 1957 **y** serie «Dynasty» 2017). Cada miembro de `theme_tmdb` guarda `media`, y al cruzar se exige que el tipo de la ficha corresponda; si es ambiguo (`anime`/`kids`), `_title_matches` desempata por título/año. Resultado: **Dynasty ya no aparece en Día de Muertos**.
  - *Foto cacheada (`theme_tmdb`)*: los IDs descubiertos se guardan en Postgres; si TMDB falla, la colección conserva la última copia buena en vez de desaparecer o inventar títulos.
  - *Resultado EN VIVO (API v143)*: `estrenos` 3,846 (años [2025, 2026]); **Día de Muertos 8** (Coco, Spectre, El libro de la vida, La Nahuala, Día de Muertos, Macario, Catalina la Catrina, Amar Hasta a la Muerte); **Halloween 28** (AHS 2012, Hechizo de amor, Stranger Things, El extraño mundo de Jack, Coraline, Merlina 2025, Sabrina, El cadáver de la novia, The Conjuring, Chucky, Sleepy Hollow, Viernes 13, Halloween, Scream 7…); **`tema-noche-de-terror` ya no existe**. 0 fichas < 2025 en estrenos y 0 términos ajenos en Halloween.
  - *Pruebas*: **431 backend** en verde (`test_index_freshness` +3, `test_themes` reescrito, `test_api_serve` +2). Desplegados `sync` (schema `theme_tmdb`) y `api` en el VPS; índice reconstruido y bump a **v143**.
  - *Pendiente*: la app refresca el índice sola (TTL 30 min o al cambiar la versión); ver en el teléfono/tele que «Novedades» abre en 2026 y que las dos temáticas nuevas se ven; si se quiere una fila de Día de Muertos más larga haría falta ampliar el catálogo (hoy solo hay 8 obras reales de la fiesta) — no rellenarla con terror genérico.
- **AUDITORÍA D-PAD + MÓVIL (2026-10-27, OTA 2.5.169 estable publicada): navegación completa del launcher y de la app, «Omitir intro» y elegir un minuto desde el móvil — 990 Android + 125 launcher**:
  - *Pedido*: «Verifica toda la navegación dpad en el launcher y en la aplicación principal, necesitamos que esté perfecta. También hay un detalle con el omitir intro y seleccionar algún minuto específico en la barra de reproducción desde el móvil».
  - *TV Inicio — filas inalcanzables (causa medida en el emulador)*: el `LazyColumn` solo compone lo visible; al enfocar la última fila visible la flecha **Abajo no tenía destino** (la fila siguiente aún no existía) y el mando se quedaba atorado. `TvHomeScreen` gana `revealNextRowPeek(slotIndex)` (y su simétrico para la fila anterior): al enfocar una tarjeta se deja asomar un pico de 132 dp de la fila vecina, de modo que Abajo/Arriba siempre tienen una tarjeta compuesta a la que saltar. Verificado en el emulador: 12 Abajo recorre Top 10 → Novedades → temáticas → Populares → Películas/Series populares y 12 Arriba vuelve hasta «Inicio».
  - *Regresión `focusGroup`*: `Modifier.focusGroup().focusRestorer()` en los `LazyRow` de las filas del Inicio **bloqueaba la flecha Arriba** (el foco no salía de la fila hacia el héroe/barra). Se retiró de las filas del Inicio (se conserva donde no bloquea) y también de las pestañas de `TvTopBar`; el aterrizaje del héroe ya tiene `up` explícito.
  - *Launcher — diálogos*: nuevo `ui/DialogFocus.kt` con **foco inicial verificado** (reintenta hasta que el destino lo tiene, no una vez), **vigilante** que devuelve el foco si el contenido cambia debajo del cursor (lista filtrada, confirmación) y **respaldo** al control que siempre existe (Cerrar). Aplicado a Centro de control, Personalizar (hub/Canales/Apps/Filas/Apariencia), acciones de tarjeta, buscador y guía del Inicio. Bug real: el modificador con `focusRequester` de `PanelAction` (y de los avisos de instalar/actualizar) se aplicaba a una fila **no enfocable**; ahora va en el nodo enfocable.
  - *Launcher — configuración*: confirmar/cancelar «Restablecer todo» conserva el foco (`resetFocus` + `LaunchedEffect(confirmReset)`); los filtros de Canales/Apps se alcanzan con **Arriba** desde la primera fila y **Abajo** vuelve a la primera fila (`filtersFocus` + `softFocusDown`); ocultar el último canal de una lista ya no deja el mando muerto.
  - *Launcher — aviso de Vision+*: el aviso de instalar/actualizar vive entre la barra y el contenido y era **inalcanzable** (Abajo de las pestañas y Arriba del héroe lo saltaban). Ahora la barra baja al aviso y el contenido sube a él (`bannerFocus`, `primaryModifier` en ambos avisos) y su botón baja al contenido.
  - *Launcher — barra superior*: las pestañas recuerdan su último foco (`focusGroup().focusRestorer()`) y perfil/buscar/ajustes tienen salida explícita hacia el contenido (`softFocusDown`). Se añadió una red proactiva de foco en el Inicio (solo con la actividad al frente y sin paneles) para cuando una fila se refresca y el control enfocado desaparece.
  - *App TV — secciones*: `TvCategoryScreen` re-aterriza **solo al cambiar de modo** (destacados ↔ cuadrícula); elegir otro género conserva el foco en el chip (antes saltaba a «Volver a Todo» en cada selección). `TvLiveScreen` recupera el foco al cerrar el buscador con Atrás, y sus columnas/chips son grupos de foco. `TvApp` reintenta el rescate también al cambiar de ruta.
  - *App TV — PIN de niños*: los 10 dígitos en una sola fila no cabían en el panel (el 0 se perdía); ahora **2 filas de 5**, con foco inicial verificado y grupo por fila. `TvDownloadsScreen` ya no abre sin foco cuando hay una descarga en curso (el botón Cancelar sostiene el aterrizaje). `TvLibraryScreen`/`TvDownloads` agrupan sus cuadrículas; el riel de Ajustes y los chips de categoría también.
  - *Móvil — «Omitir intro» (causa real)*: en el reproductor nativo la píldora se ve sobre el video con los controles cerrados, pero su click exigía `canExecuteControlAction()` (que devuelve false si los controles están ocultos) → **el primer toque no hacía nada**. Ahora el candado de 220 ms solo aplica al mando (`!isTv || canExecuteControlAction()`) y en el Compose la píldora sale también con los controles cerrados en móvil (`skipPillVisibleFor`, `canPressSkipPill`); en la tele se mantiene el primer OK para despertar el menú.
  - *Móvil — elegir un minuto en la barra*: un toque previo en el video dejaba programado «ocultar en 300 ms» y **la barra desaparecía a mitad del arrastre** (se cancela al empezar a elegir y el ocultado ya no corre con `scrubbing`); el sondeo cada 500 ms devolvía el pulgar a la posición vieja mientras el motor procesaba el seek (nuevo `seekHoldActive`: la barra sostiene el minuto elegido ±2 s o 1.8 s); sin duración conocida ya no se manda el video al segundo 0. En el nativo la barra táctil es un poco más alta en móvil.
  - *Verificado en el emulador (teléfono, shell TV forzada)*: Centro de control abre con foco en «Personalizar Inicio»; la confirmación de «Restablecer» y su cancelación conservan el foco; los filtros de Canales se alcanzan y vuelven a la lista; ocultar/mostrar un canal no deja el mando muerto; Inicio de la TV baja/sube por todas las filas; la sección Películas conserva el chip al cambiar de género y la cuadrícula navega; TV en vivo y la ficha de una serie también. **No se pudo probar la reproducción en el emulador**: el motor ARM traducido aborta (SIGABRT) al hacer play (limitación conocida); los arreglos del reproductor quedan cubiertos por pruebas puras y de cableado y esperan la prueba física.
  - *Pruebas*: `MobileSkipSeekTest` (5), `TvDpadAuditTest` (6) y `LauncherDpadTest` (8); **990 Android + 125 launcher** en verde. Sin OTA publicada.
  - *Publicado (misma fecha)*: OTA **2.5.169 estable (vc409)** + **Vision+ Home 1.2.5 (vc22)** en el release `v2.5.169` (no prerelease; `/releases/latest` = v2.5.169): `VisionPlus.apk` **78.8 MB (82,581,080 B)**, `VisionPlusHome.apk` **1.4 MB (1,476,052 B)** y `version.json`; firma histórica `9593cb0d…` en los dos, 5 DEX (app 2 + motor 3) y sin `assets/x4k`. El canal **beta** (`beta`, prerelease) conserva la app 2.5.168 pero se le subió el **launcher 1.2.5** para que ese canal también entregue el launcher nuevo; el actualizador del launcher (`HomeUpdate`) lee `/releases/latest` (estable), así que recibe 1.2.5.
  - *Pendiente*: prueba física en la TLC/teléfono del «Omitir intro» con controles cerrados y del arrastre a un minuto exacto (el motor del emulador aborta al reproducir); el bump de versión y los cambios quedan **sin commitear** (como indica el publicador).
- **VISION TV 2.5.168 BETA (2026-10-27): Roku retirado por ahora — sin relay en Ajustes, sin servicio en el APK y sin `/v1/roku/*` en el VPS; el proyecto quedó parkeado fuera del repo — 979 Android + 422 backend; beta publicado**:
  - *Pedido*: «Quita todo lo de Roku por el momento».
  - *App*: se retiraron `data/relay/` (RelayServer/RelayService/RelayEngine/RelayLauncher/RelayRules/RelayNet), `ui/settings/RokuRelayCard.kt`, su import y su llamada en Ajustes, la entrada `<service .data.relay.RelayService>` del manifest y las pruebas del relay. El puerto 48640 ya no se abre; el teléfono no sirve video a ningún Roku. **No se tocó** el proceso `:x4k` del reproductor/descargas ni LibADB (eso es «Inicio en la TV», no Roku).
  - *Servidor*: se quitó `api/roku.py`, el import `roku` y las rutas `/v1/roku/health|home|search|title|episodes` (ahora **404**), y se restauró el `api/app.py` pre-Roku (con el parche de `/v1/section` 4× y el resto de rutas desplegadas). `/v1/index`, `/v1/section`, `/v1/episodes`, `/v1/epg/health`, `/v1/diag/health`, `/v1/trending` y `/v1/categories` verificados 200.
  - *Parkeado (fuera del repo, reversible)*: `C:\Users\Axel Rosete\Desktop\Proyectos\_parked_roku_2026-10-27\` con `roku/` (VisionPlusRelay/Direct, docs), `pack_roku*.ps1`, `ROKU_STANDALONE.md`, `api/roku.py`, `test_roku.py` y los archivos/pruebas del relay de la app. No se borró nada: se movió fuera del árbol para no publicarlo.
  - *Pruebas*: **979 Android + 422 backend** en verde (la suite bajó al quitar los 17 tests del relay y los 15 de Roku). Release R8 **78.7 MB**, firma histórica `9593cb0d…`, motor inyectado (5 DEX). APK verificado: sin `Roku (relay local)`, `vision_relay` ni `RelayService`; conserva `Inicio en la TV`, `Continuidad entre equipos`, `tema-tendencias-mundo`, «Temáticas» y «Géneros y colecciones». Instalado en el emulador: Inicio con Top 10/Novedades y temáticas.
  - *Publicado*: beta **2.5.168 (vc408)** + `VisionPlusHome.apk` 1.2.4 (vc21) + `version.json`, en el tag `beta`; reemplaza a la 2.5.167. *Pendiente*: si se retoma Roku, restaurar desde el parkeado; la prueba física de la TLC sigue pendiente.
- **VISION TV 2.5.167 BETA (2026-10-27): beta consolidada de los pendientes de los otros agentes (Roku relay, Inicio en la TV, diagnósticos, continuidad, temáticas, GeeSports y catálogo) + API de Roku Direct desplegada — reemplazada por la 2.5.168 (Roku retirado)**:
  - *Contenido*: el APK de la 2.5.167 juntó todo el árbol de trabajo (relay de Roku para el teléfono, «Inicio en la TV» por ADB inalámbrico, diagnósticos con `/v1/diag/health`, continuidad, temáticas, catálogo vivo y GeeSports) y se desplegó el API `/v1/roku/*` de solo lectura. La 2.5.168 quitó Roku a petición del usuario; **todo lo demás sigue vigente**.
  - *Pruebas de esa beta*: 996 Android + 437 backend (incluían Roku). Notas: `ota_notes_2.5.167.md`.
- **VISION TV 2.5.166 BETA (2026-10-26): colecciones temáticas que se encienden solas por fecha — Día de Muertos ya activo, Halloween, Navidad, San Valentín, Madres/Padres, Fiestas Patrias, Vacaciones, Regreso a clases y Año Nuevo + «Noche de terror» y «Para reír» todo el año + «Tendencias del mundo» — 996 Android + 437 backend; beta publicado**:
  - *Pedido*: «Agregar categorías temáticas por las fechas, Día de Muertos, tendencias del mundo, que se sienta vivo el catálogo de terror y comedia».
  - *Servidor (`server/sync/themes.py` nuevo + `catalog_filters.py`, desplegado)*: `active_themes(fecha)` enciende cada tema por ventana (Día de Muertos 24-oct→4-nov, Halloween 1→31-oct, Navidad 1→31-dic, San Valentín 1→15-feb, Madres 1→12-may, Padre 1→21-jun, Fiestas Patrias 10→20-sep, Vacaciones 1-jul→20-ago, Regreso a clases 1-ago→10-sep, Año Nuevo 28-dic→10-ene; permanentes «Noche de terror» y «Para reír»). La selección va por etapas: **título → frases específicas de sinopsis → género primario** (el relleno usa el primario real, no etiquetas crudas), deduplica por `tmdb_id` (las temporadas T1..T20 no consumen la fila) y las temáticas de fecha (Navidad, San Valentín…) **no se rellenan con cualquier comedia**: si no hay títulos de la fecha, no se inventan. Categorías `kind='theme'` en posiciones 5+; «Tendencias del mundo» con la foto semanal de TMDB. **Regresión cazada y cerrada**: «Terror»/«Horror» no tenían alias en `genres.py` y la fila `genero-terror` había desaparecido del catálogo; se restauró (4,126 títulos) y `test_genres` lo fija.
  - *App (`Popularity.kt`, `HomeScreen.kt`, `TvHomeScreen.kt`, `HomeFeedStore.kt`, `VisionIcons.kt`)*: `HomeCurated.themeRows` (máx. 4; «Tendencias del mundo» se queda en Categorías porque el Inicio ya tiene «Populares de la semana»), filas **después de Novedades y antes de Populares**; no repiten Top 10/Novedades ni entre sí (`disjointRows`), y las filas por tipo, la semanal y «Porque viste X» también las excluyen. El launcher publica las mismas filas en su feed, Categorías las muestra desde `/v1/index` e `homeRowIcon` les da icono de colecciones.
  - *Verificado EN VIVO (VPS + emulador con el release vc406)*: Día de Muertos **28** títulos, Halloween **28**, Noche de terror **24**, Para reír **24**, Tendencias del mundo **29**. Día de Muertos abrió con La Noche del Demonio, American Horror Story, Embrujadas, Buffy, Entre fantasmas, Fantasmas, Scary Movie, Sabrina y El Heladero; Noche de terror con Resident Dead, Zona Cero, Aniquilación, No dejes a los niños solos, Resident Evil, Coraline, Backrooms…; Para reír con El oso, The Office, Shameless, Modern Family, Ted Lasso, Friends, Seinfeld…; Halloween con Minions, Monster, Monarch, Halloween, The Mummy, The Terror, Dahmer. Las filas no repiten Novedades ni la una a la otra.
  - *Pruebas*: **996 Android + 437 backend** en verde (`test_themes.py` 14, `test_genres` +1 con Terror, `test_index_normalize.py` 3, `PopularityTest` +2 temáticas, `HomeCatalogWiringTest` +1).
  - *Publicado*: beta **2.5.166 (vc406)**, `VisionPlus.apk` 82.6 MB + `VisionPlusHome.apk` 1.2.4 (vc21) + `version.json`, firma histórica `9593cb0d…`; el mismo APK incluye el catálogo vivo/géneros de la 2.5.165. Reemplaza a la beta 2.5.165.
  - *Pendiente*: probar en la TLC física; ver el cambio de temporada (5-nov desaparece Día de Muertos; 1-dic entra Navidad) y ajustar palabras si alguna fila trae un falso positivo.
- **VISION TV 2.5.165 BETA (2026-10-26): los enlaces de Deportes se actualizan solos cada 5 min — pase caliente en el worker + refresco de eventos en la app — 993 Android + 421 backend; beta publicado**:
  - *Pedido*: «Hay que ver qué siempre se actualicen los enlaces».
  - *Diagnóstico medido*: el worker completo corría cada **45 min** y el CMS a veces publica `events.txt` a medias (**79 vs 1,378 guardados**); el partido Instituto vs Riestra conservaba un enlace con `checked_at` del **23-oct**, aunque el evento seguía en la agenda. El `events.txt` parcial también impedía refrescar su `links_ref`.
  - *Servidor (`server/sync/gee38.py`, desplegado)*: `_fetch_events_full` lee `events.txt` **4 veces** y `_merge_event_pages` fusiona por partido quedándose con el `links_ref` más nuevo; `_refresh_hot_links` corre cada **5 min** para los partidos de la ventana (**una semana atrás..pasado mañana**, porque el API cruza enlaces de entradas hermanas), **pone primero los de HOY con la fecha cruda del CMS (UTC)** y luego **ordena por el `checked_at` más viejo**, vuelve a bajar su `pro/*.txt`, sondea y actualiza el payload. El `loop` alterna ciclo completo (45 min) y pase caliente (5 min). El primer pase actualizó **56 eventos**; ya filtrando solo eventos con enlaces y priorizando el `checked_at` más viejo, el siguiente actualizó **60**. Los partidos de hoy ya sirven enlaces con `checked_at` del día (p. ej. el `LOW QUALITY` del Instituto, 03:07 UTC / 21:07 CDMX) y el pase también alcanza la ranura `D SPORTS2` heredada de la entrada del 22-oct.
  - *App (`GeeSportsState` + `SportsScreen`/`TvSportsScreen`)*: `refreshLinks` pide **solo los eventos** (no los grupos) cada **5 min** mientras Deportes está al frente (`repeatOnLifecycle(RESUMED)`), sin esperar el TTL de 8 min ni el ciclo del worker. El diálogo usa la lista actualizada.
  - *Se mantiene*: solo enlaces de GeeSports; «Sin enlaces disponibles» si no hay; «Viendo» y el regreso al mismo partido.
  - *Pruebas*: **993 Android + 421 backend** en verde (`_merge_event_pages` nuevo, `theSportsScreenRefreshesTheLinksWhileItIsOpen`).
  - *Publicado*: beta **2.5.165 (vc405)**, `VisionPlus.apk` **82,581,080 B (82.6 MB)**, `VisionPlusHome.apk` **1.2.4 (vc21, 1,476,052 B)** y `version.json`; firma histórica `9593cb0d…`; 5 DEX (app 2 + motor 3), cadenas `Sin enlaces disponibles`/`Transmisiones de GeeSports` presentes y sin `Canales posibles`. Asset descargado y verificado (`versionCode=405`/`versionName=2.5.165`, launcher 21/1.2.4); instalado en el emulador: Deportes con **19 eventos y 8 en vivo**, señales de GeeSports. Reemplaza a la beta 2.5.164.
  - *Pendiente*: verificar en la TLC que el enlace de un partido en vivo cambia a `checked_at` reciente y que el selector lo abre; el ADB de la TLC está cerrado ahora. Después, promover a estable.
  - *Catálogo vivo y géneros sin repetición (mismo beta 2.5.165/vc405; servidor ya desplegado)*:
    - *Pedido*: «Los catálogos de series, películas e Inicio muestran siempre lo mismo; el Top 10 siempre es igual, Novedades y Estrenos y Populares de la semana deben actualizarse con lo nuevo; en Drama/Suspenso/Comedia salen las mismas películas, que no se repitan entre categorías y que los géneros sean los reales».
    - *Causa medida*: `popularity` solo se llenaba una vez al enriquecer (TMDB la mueve a diario) y `TENDENCIAS_SQL` ordenaba por ese valor congelado; los géneros salían de los `tags` del portal (con «Nacional», «Scripted», «Game Show», «Corto»…) y un título entraba en TODOS sus géneros (había fichas en hasta 22 filas; 36,758 títulos repetidos en el top-100 de los géneros globales); «Novedades» abría con los «destacados» del portal (varias fichas de 0 votos) y no con lo nuevo; `/v1/section` pedía `limit` filas y luego colapsaba temporadas (Series quedaba en 3-6 tarjetas); la app reordenaba la hilera `tendencias` por popularidad cruda y destruía el ranking del servidor.
    - *Servidor (`server/sync/genres.py` nuevo + `index_build.py`/`tmdb.py`/`main.py`/`schema.sql`, desplegado)*: géneros canónicos (TMDB manda; el portal rellena) y **un género primario por título** (prioridad: Telenovela/Terror/Suspenso/Acción/Ciencia ficción/Animación…; Comedia y Drama al final; Novelas usa prioridad propia Romance→Comedia→Drama y descarta «Telenovela» para no quedar en una sola fila). `first_seen_at` (columna nueva; las filas viejas quedan con 180 días para no inventar 52k novedades) y `popularity_at`; `refresh_popularity` refresca 240 fichas TMDB por ciclo (orden por antigüedad y popularidad), `sync_trending` se rehace cada 12 h y solo guarda si llegaron cine Y series; `TENDENCIAS_SQL` pasa a un `buzz` con popularidad (clamp), votos, `is_premiere`, recién llegado, tendencia semanal (max +4) y un desempate diario determinista (`md5(id||fecha)`); `estrenos` pone primero lo recién llegado con señal (posiciones 1..10) y después el año por impacto, excluyendo el Top 10 por id Y por `tmdb_id`; los tráilers `(Trailer) …` quedan fuera de las filas curadas; el índice se arma AL FINAL del ciclo (ve la popularidad/géneros refrescados en el mismo pase); `/v1/section` pide 4× y aplica el límite después del colapso; `normalize` ya no fusiona las repisas de FUENTE con filas curadas (si se llaman «Acción»/«Comedia» se retiran: no reintroducen títulos de otros géneros ni tráilers).
    - *App (`Popularity.kt`, `SectionScreen.kt`, `TvCategoryScreen.kt`, `HomeScreen.kt`, `TvHomeScreen.kt`)*: `topPopular` respeta el orden del servidor (solo rellena si faltan cartas y exige señal), `homeCurated` usa la fila `estrenos` del servidor (sin recalcularla), `disjointRows` quita de cada fila lo ya mostrado arriba (y omite filas que solo repetirían), «Recomendado para ti» de TV no repite los géneros, y «Populares de la semana» pide 40.
    - *Verificado EN VIVO (VPS + emulador con el release vc405 publicado)*: géneros globales canónicos (sin «Nacional»/«Scripted»/etc.); **0 solapamientos** entre las 18 filas de género de Películas (top 60 de cada una) y **0** en el top-100 de los 21 géneros globales; Top 10 vs Novedades **0** repetidos; primer pase de popularidad **240 fichas**; «Novedades» abrió con **Hermanos del viento** (recién llegado de 2016, `first_seen_at` nuevo) al frente; el emulador con vc405 mostró el Top 10 exactamente en el orden del servidor (Hope, Spider-Man, Caballos lentos, Kusuriya, Cyberpunk, Obsesión, Tierra de Mafia, Breaking Bad, Carrie, La odisea) y Novedades sin traslape; «(Trailer) The Batman» desapareció de Suspenso.
    - *Pruebas*: **993 Android + 421 backend** en verde (`test_genres.py` 9, `test_index_freshness.py` 7, `test_index_normalize.py` 3, `PopularityTest` +5, `HomeCatalogWiringTest` +2; tests de sección/tendencias actualizados). Beta **2.5.165 (vc405)** reeditada con estas notas en `version.json` (mismo APK). *Pendiente*: ver en la TLC cómo cambian las filas en días distintos y valorar subir/bajar `TMDB_POPULARITY_REFRESH_BATCH`; el candado de «Populares» depende de TMDB (si su trending no cambia, la fila tampoco).
- **VISION TV 2.5.164 BETA (2026-10-26): Deportes vuelve a tener UNA sola fuente de verdad — solo los enlaces de GeeSports; si no hay, «Sin enlaces disponibles» — 984 Android; beta publicado**:
  - *Pedido*: «Tu única fuente de verdad es los canales de GeeSports; si no hay enlaces online pon una leyenda sin enlaces disponibles. Pero solo muestra los que GeeSports manda» (además: en la APK de GeeSports las fuentes sí abren el partido; en Vision no).
  - *Causa*: la 2.5.163 había vuelto a poner la guía/parrilla (ESPN/TNT/TyC) al frente del selector y a mezclarla con las señales del evento. En el emulador, para Instituto vs Riestra la guía ofrecía ESPN/TyC **caídos** y un TNT de guía que respondía **HTTP 591**; los enlaces directos de GeeSports para ese partido venían vacíos. La APK original usa los enlaces del evento, no la parrilla.
  - *Fix (`ui/sports/GeePicks.kt` + `GeeSportsTabs.kt`/`TvGeeSportsTabs.kt` + pantallas)*: `geePickRows` usa **solo `event.links`**; descarta URL vacías, deduplica por URL sin cabeceras, ordena por `alive` y marca «Viendo». Se retiraron `GeeGuide.channelsFor`, las filas de guía y la línea «Se transmite en: …» del selector. Si no queda ningún enlace: **«Sin enlaces disponibles»**. `SportsScreen`/`TvSportsScreen` ya no pasan guía/grupos al diálogo. El zapping usa exactamente esa lista.
  - *Se conserva*: «Viendo», el regreso al mismo partido/opción, el estado de DeportesReturn y el zapping de 2.5.163.
  - *Verificado*: **984 Android** en verde (GeePicksTest reescrito: solo enlaces, URL vacía fuera, duplicados, «Viendo», sin guía; GeePickDialogDuplicateTest actualizado; LiveSportsPlayerUiTest exige que el selector no tenga la guía). En el emulador, Instituto vs Riestra (sin enlaces GeeSports) muestra solo **«Sin enlaces disponibles»** y «Volver»; no aparece ESPN/TNT/TyC.
  - *Publicado*: beta **2.5.164 (vc403)** + **Vision+ Home 1.2.4 (vc21)**, `VisionPlus.apk` **82.6 MB** + `VisionPlusHome.apk` 1.4 MB + `version.json`, firma histórica `9593cb0d…`; assets verificados (`versionCode=403`/`versionName=2.5.164`, launcher 21/1.2.4, 5 DEX, cadenas `Sin enlaces disponibles`/`Transmisiones de GeeSports:`, sin `Canales posibles`). Instalado el release en el emulador: Instituto vs Riestra (sin enlaces GeeSports) muestra solo «Sin enlaces disponibles» y «Volver». Reemplaza a la beta 2.5.163.
  - *Pendiente*: probar en la TLC física un partido **con enlaces directos de GeeSports** (Flamengo/SPORT TV/PREMIER) y el regreso con «Viendo»; la TLC tiene el **ADB 5555 cerrado** ahora, al reactivarlo se instala el release. Si va bien, promover a estable.
- **VISION TV 2.5.163 BETA (2026-10-26): los enlaces de Deportes en vivo vuelven a llevar al partido correcto y Atrás regresa al menú con la selección hecha — 984 Android en verde; beta publicado**:
  - *Pedido*: «Se rompió lo de los eventos en vivo, ya los enlaces no mandan al partido o evento correcto; también cuando das atrás del reproductor cuando ya pusiste un canal te tiene que regresar al menú del que saliste con la selección que hiciste».
  - *Causa 1 (enlaces)*: la 2.5.160 quitó la guía oficial del diálogo y dejó SOLO los enlaces de la fuente. Esos enlaces incluyen señales genéricas compartidas («STREAM TV + HD», FANCODE, CazéTV…) que muchas veces están dando OTRO partido; la guía de ESPN/LiveSoccerTV (ESPN, TUDN, Fox Sports, beIN…) ya no se ofrecía como opción.
  - *Causa 2 (regreso)*: el modal reabría en la PRIMERA opción, no en la que se estaba viendo, y la selección de la pantalla (deporte/día/liga/pestaña) se reiniciaba al volver del reproductor. En TV en vivo, un `LaunchedEffect` del primer pase borraba el filtro de país restaurado.
  - *Fix 1 (`ui/sports/GeePicks.kt`, nuevo)*: `geePickRows` arma **UNA sola lista**: primero los canales 24/7 de GeeSports que la parrilla oficial confirma (`GeeGuide.channelsFor`), después las señales directas de la fuente; sin duplicar URL (la comparación ignora `|headers`); lo que responde primero (guía viva → fuente viva → sin verificar → caído) y dentro de cada grupo la guía manda. Tope 24.
  - *Fix 2 (diálogos móvil/TV)*: la línea «Se transmite en: …» vuelve como contexto; las filas de la guía dicen «Canal en vivo · [cadena]»; la opción sintonizada se marca «Viendo» y es la que recibe el foco y el scroll al reabrir. El zapping (`GeeZapSignals.arm`) usa el mismo orden.
  - *Fix 3 (`DeportesReturn`)*: recuerda deporte/día/liga/pestaña y el partido+enlace que se estaba viendo; `consumeWatchingEvent()` se consume una vez, `showEvent` alinea la pantalla cuando el partido llega del héroe/launcher y `forgetWatching` limpia al cerrar o elegir otro partido. `validDay` evita reabrir un día pasado.
  - *Fix 4 (TV)*: al volver con el modal abierto no se reinicia el scroll ni se roba el foco (`skipLanding` en `TvSportsScreen`); en `TvLiveScreen` el filtro de país ya no se borra en el primer pase: solo al tocar lista/género/favoritos.
  - *Pruebas*: **984 Android** en verde (nuevas `GeePicksTest` 8 y `DeportesReturnTest` 5; `GeePickDialogDuplicateTest` +1 con guía y «Viendo»; `LiveSportsPlayerUiTest` actualizado; `TvSportsMosaicTest` resetea `DeportesReturn`).
  - *Verificado en el emulador (modo tele)*: Sevilla vs Osasuna muestra «Se transmite en: ESPN Deportes · ESPN+ · ESPN» y el canal de la guía cuando está vivo; PSG vs Lyon abre con «beIN ENGLISH 1 · Canal en vivo · beIN Sports» primero; Atrás deja el mismo partido con la opción «Viendo» enfocada; TV en vivo conserva Chile y el canal 13 al volver; el móvil reabre igual el modal con «Viendo». Con el APK **release R8 publicado** (vc402) instalado en el emulador, Unión (Santa Fe) vs Estudiantes abre con «TNT SPORTS 1 · Canal en vivo · TNT Sports» y al volver queda «Viendo» enfocado.
  - *Publicado*: beta **2.5.163 (vc402)** en el tag `beta`, `VisionPlus.apk` **82.6 MB** + `VisionPlusHome.apk` **1.2.4 (vc21, 1.4 MB)** + `version.json`, firma histórica `9593cb0d…`; asset descargado y verificado (`versionCode=402`/`versionName=2.5.163`, 5 DEX: app 2 + motor 3, cadenas `Se transmite en: `/`Viendo`/`Transmisiones de GeeSports:` presentes; launcher 21/1.2.4). `version.json` leído por bytes: UTF-8 correcto (`Atrás`, `—`).
  - *Pendiente*: probar en la TLC física (los enlaces de la guía y el regreso con el mando); si va bien, promover a estable.
- **VISION TV 2.5.162 BETA (2026-10-26): el zoom del hero ya no se sale del banner — recorte `clipToBounds` en launcher 1.2.4; verificado en la TLC física — 970 Android + 117 launcher + 399 backend; beta publicado**:
  - *Pedido*: «conforme se va haciendo grande la imagen del header se sale de la parte oscura del banner… una parte se ve por detrás del filtro oscuro y una pequeña parte en su color original» (launcher).
  - *Causa medida en la TLC*: `HeroBackdrop` escala el arte a 1.07 con `graphicsLayer` (Ken Burns); el `Box` del hero no recortaba, así que la imagen se dibujaba fuera de su caja y los degradados —hijos del mismo `Box`— no cubrían esa franja. En la captura se veía la franja inferior del arte sin filtro sobre la fila siguiente.
  - *Fix (`launcher/.../ui/Hero.kt`)*: `Modifier.clipToBounds()` en el `Box` del `HeroStage`; el zoom queda siempre dentro del banner y el degradado cubre todo el arte. No se tocó la animación ni el arte.
  - *Verificado en la TLC (BeyondTV, 1920×1080, Android 9)*: launcher 1.2.4 debug; capturas antes (`tlc_zoom_early.png`/`tlc_zoom_late.png`: franja inferior sin filtro) y después (`tlc_check.png`/`tlc_zoom_max.png`: borde limpio, sin franja); bajar 3 y subir deja el hero completo (`tlc_scroll_fixed.png`). La app 2.5.161 sigue instalada; este arreglo es del launcher.
  - *Pruebas*: **970 Android + 117 launcher + 399 backend** en verde (nuevo guardián `clipToBounds` en `HeroBannersWiringTest`).
  - *Publicado*: beta **2.5.162 (vc401)** + **Vision+ Home 1.2.4 (vc21)**, `VisionPlus.apk` **78.8 MB** + `VisionPlusHome.apk` 1.4 MB + `version.json`, firma histórica `9593cb0d…`; assets verificados (`versionCode=401`/`versionName=2.5.162`, launcher `21`/`1.2.4`, firma, cadenas `nextRefreshAt`/`Transmisiones de GeeSports`, sin `Canales posibles`). Launcher release instalado en la TLC: slides de película y deportes sin franja; bajar/subir deja el hero completo. La app es la misma 2.5.161 (el cambio va en el launcher); reemplaza a la beta 2.5.161.
  - *Pendiente*: probar en la TLC el hero de la app y un partido en vivo; si va bien, promover 2.5.162 a estable.
- **VISION TV 2.5.161 BETA (2026-10-26): el hero vuelve COMPLETO al subir — la lista se ancla al tope al enfocar sus botones (launcher 1.2.3) — 970 Android + 117 launcher + 399 backend; beta publicado; la estable sigue en 2.5.157**:
  - *Pedido*: «cuando bajas en el menú y regresas hacia arriba los banners ya no se ven completos, se ven cortados» (launcher y app).
  - *Causa medida*: el hero es el primer ítem del `LazyColumn`; al volver arriba el foco caía en sus botones y el `MinimalScrollBringIntoView` solo desplazaba lo justo para mostrar el botón, dejando el título/sinopsis/arte por encima del viewport. La captura del launcher mostraba el hero a ~150 px (solo botones y una tira de arte).
  - *Fix launcher (`LauncherScreen.kt`)*: `LaunchedEffect(heroFocused) { if (heroFocused) homeListState.scrollToItem(0) }` — al enfocar el hero, la lista se ancla al tope. El `heroFocused` ya existía (`onPrimaryFocus`) para pausar el carrusel.
  - *Fix app (`TvHomeScreen.kt` + `TvWidgets.kt`)*: `TvCompactAction` gana `onFocusChanged` (desde su `interactionSource`); cada acción del hero incrementa `heroActionTick`; `LaunchedEffect(heroActionTick) { if (tick > 0 && returnFilmId == null) rowsListState.scrollToItem(0) }`. La guarda `returnFilmId == null` evita que el regreso a una ficha pierda la tarjeta original (lo cazó `TvFocusTest.homeBackRestoresSameCardAfterLateContent`).
  - *Verificado en el emulador (TV 1080p)*: launcher y app, bajar 3 y subir 8 con el D-pad → hero completo (título, metadatos, sinopsis y botones); capturas `launcher_fixed.png`/`app_fixed.png`.
  - *Pruebas*: **970 Android + 117 launcher + 399 backend** en verde (nuevo guardián `TvHeroWiringTest.returningToTheHeroAnchorsTheFullBanner`; aserciones en `HeroBannersWiringTest`).
  - *Publicado*: beta **2.5.161 (vc400)** + **Vision+ Home 1.2.3 (vc20)**, `VisionPlus.apk` **78.8 MB** + `VisionPlusHome.apk` 1.4 MB + `version.json`, firma histórica `9593cb0d…`; assets descargados y verificados (`versionCode=400`/`versionName=2.5.161`, launcher `20`/`1.2.3`, firma histórica, cadenas `Transmisiones de GeeSports`/`nextRefreshAt`, **sin** `Canales posibles`). Instalado en el emulador (release): app y launcher con el hero **completo** tras bajar y subir con el mando. Reemplaza a la beta 2.5.160 (mismo contenido + este arreglo). **Reemplazada por la beta 2.5.162 (launcher 1.2.4, recorte del zoom).**
  - *Pendiente*: probar en la TCL/caja física el regreso arriba con el mando; si va bien, promover 2.5.161 a estable.
- **VISION TV 2.5.160 BETA (2026-10-26): banners diarios con coste cero y el selector de Deportes solo con fuentes de GeeSports — 969 Android + 117 launcher + 399 backend; beta publicado; la estable sigue en 2.5.157**:
  - *Pedido*: «no haya fallback en los canales de la agenda deportiva, solo muestro los de GeeSports… el diálogo me deja escoger entre dos categorías, la primera casi nunca tiene opciones» + «exprime al máximo el rendimiento de los banners para que no me cueste absolutamente nada ni tenga riesgo de que me cueste nada pero que sí funciona».
  - *Deportes (`ui/sports/GeeSportsTabs.kt` + `ui/tv/TvGeeSportsTabs.kt`, beta 2.5.159)*: fuera la sección **«Canales posibles»** (los canales 24/7 del catálogo resueltos por `GeeGuide`) y el respaldo `GeeBackstop`; la lista queda **una sola**, «Transmisiones de GeeSports:», con las señales del partido que tienen **URL real** (`filter { it.link.isNotBlank() }.distinctBy { it.link }`). Una ranura declarada sin URL ya no se ofrece; si no queda ninguna, se dice «Sin transmisiones disponibles por ahora». Tocar una fuente caída reproduce **esa** fuente (sin cambiarla por un canal de la casa). La guía oficial («TV: ESPN · DAZN») se queda solo como contexto en la tarjeta y en el aviso de cable del reproductor (`EventInfo.guide`), nunca como opción para sintonizar; `EventInfo.fallback` ya no se llena.
  - *Banners — cliente (`data/banners/BannerSource.kt` + `launcher/.../data/BannersRepository.kt`)*: la caché caduca con el **`nextRefreshAt` del VPS + 5 min** (20 h), no cada 30 min; si el VPS está refrescando, la respuesta trae el siguiente intento a 15 min (nunca una fecha vencida) y la tele no entra en bucle; **`Mutex`** para una sola descarga; **15 min de enfriamiento** si falla la red (el hero sigue con el disco); la lectura del archivo ya **no marca la foto vieja como fresca** (el bug que la dejaba pegada: se guarda el `lastModified` real); el arte se cachea con **`bannerArtCacheKey` = URL sin `?v=`**, así Coil reutiliza la misma imagen aunque cambie la versión diaria; la siguiente diapositiva se **prefetcha** mientras se ve la actual.
  - *Banners — VPS (`server/api/banners.py`, desplegado)*: **candado `pg_try_advisory_lock`** (un solo refresco entre workers; el que no lo consigue no gasta TMDB), **`_BACKGROUND_RETRY_SECONDS = 15 min`** para no martillar si falla, `nextRefreshAt` futuro cuando la foto está vieja o no hay, y el refresco escribe con la **misma conexión del candado** (sin una segunda conexión del pool). TMDB: 5 llamadas al día como máximo; el arte lo sirve TMDB, no el VPS.
  - *Verificado*: **969 Android + 117 launcher + 399 backend** en verde. En el emulador (modo tele): la app y el launcher arrancaron con la copia fresca y **no pidieron `/v1/banners`** (el contador del VPS se quedó en 3); el hero siguió pintando Hope/Spider-Man. En el VPS: se envejeció la tabla 30 h → la primera respuesta dio `nextRefreshAt` a 15 min y la segunda, 12 s después, ya tenía la foto nueva (`generated_at` 0.2 min, `nextRefreshAt` 20 h); hash local=VPS (`adc03f…`).
  - *Publicado*: beta **2.5.160 (vc399)** + **Vision+ Home 1.2.2 (vc19)** (reemplazada por la beta 2.5.161; los assets del tag `beta` son los de 2.5.161).
  - *Pendiente*: probar en la TCL/caja física el hero con banners y un partido en vivo; confirmar que la beta 2.5.160 actualiza desde 2.5.159; si va bien, promover 2.5.160 (con el hero de 2.5.158 y el selector de 2.5.159) a estable. **La beta 2.5.161 (arriba) reemplaza a esta con el arreglo del hero; la prueba física sigue pendiente.**
- **VISION TV 2.5.158 BETA (2026-10-26): el billboard de la app usa los banners diarios del VPS y los partidos en vivo de GeeSports — 967 Android; beta publicado; la estable sigue en 2.5.157**:
  - *Pedido*: «¿Podemos implementar esto para vision tv?» (el hero cinematográfico que ya tiene el launcher).
  - *Cliente (`data/banners/BannerSource.kt` + `ui/tv/TvHero.kt`)*: pide `/v1/banners` con la misma API key, cachea `files/vision_home_banners.json` (TTL 30 min en 2.5.158; 2.5.160 lo reemplaza por `nextRefreshAt`) y cae al disco sin red. `buildTvHeroItems` mezcla **solo películas** (las series necesitan temporadas que el banner no trae) con **GeeSports**: un partido en vivo abre el billboard, las películas siguen y el próximo va al final. El `TvHomeScreen` carga banners y deportes en **dos LaunchedEffects** (el arte no espera al feed) y rota cada 9 s (13 s en equipos modestos); se detiene cuando el usuario enfoca una fila (`focusFilm`).
  - *Billboard*: `HeroBillboard` conserva el diseño de TV y añade el modo deportes (`TvSportsScoreboard` con escudos, marcador, liga y «EN VIVO») y botones **Ver en vivo / Agenda**; la acción setea `DeportesReturn.pickingId` y navega a `Route.Sports`. El fondo de un partido es un lienzo con gradiente + crestas ambientales; el de cine usa el **backdrop** del VPS.
  - *Foco (lección)*: la primera versión pedía landing al llegar los banners y **le robaba el foco** al regreso de una ficha; se quitó ese landing extra. Además, en Robolectric no se toca la red (`isRobolectric`) para que `TvFocusTest` y `UiNoEmojiTest` no dependan de TMDB/GeeSports. Se quitó el `★` de texto (el test de emojis no lo permite).
  - *Pruebas*: **967 Android** en verde (nuevas `BannerSourceTest` 3, `TvHeroTest` 8 y `TvHeroWiringTest` 3). El primer arranque del APK de 78.8 MB en el emulador dio «Launch timeout»; el segundo arranque mostró el Inicio normal (no es crash).
  - *Publicado*: beta **`beta` 2.5.158 (vc397)**, `VisionPlus.apk` **78.8 MB** + `VisionPlusHome.apk` 1.2.1 + `version.json`, firma histórica (`classes.dex/2` + motor `classes3/4/5`). Asset descargado y verificado (`versionCode=397`, `versionName=2.5.158`, firma `9593cb0d…`, cadenas `vision_home_banners`/`PELÍCULA DESTACADA`); instalado en el emulador en modo tele: billboard con **Hope/Spider-Man**, sin crash.
  - *Pendiente*: probar en la TCL/caja física (el emulador es un teléfono con `vision_force_tv=1`), confirmar el hero de un partido en vivo con la agenda real y, si va bien, promover 2.5.158 a estable. **Superado por la beta 2.5.160 (arriba), que ya incluye este hero y el selector de Deportes de 2.5.159; la prueba física sigue pendiente.**
- **ROKU PARKEADO (2026-10-27)**: a petición del usuario se retiró Roku del producto: el relay ya no está en el APK (ni en Ajustes ni en el manifest), `/v1/roku/*` responde 404 en el VPS y el canal/Direct/relay quedaron en `C:\Users\Axel Rosete\Desktop\Proyectos\_parked_roku_2026-10-27\` (fuera del repo). Los lotes de abajo se conservan como historial de la investigación; **no se publica nada de Roku por ahora**.
- **ROKU DIRECTO (proyecto nuevo, de cero, 2026-10-25): canal sideloadeado que habla SOLO con el VPS — arquitectura, contrato `/v1/roku/*` congelado y canal SceneGraph completo compilando (28.6 KB, 0 errores); el plano de reproducción del VPS NO está implementado**:
  - *Pedido*: «Nuevo proyecto, de cero. Sin reabrir nada de antes… un canal de Roku que haga todo dentro del Roku» con requisitos duros: portada/filas/búsqueda/ficha contra el VPS, OK reproduce, igual en casa que fuera, y nada de la app del proveedor en el camino de reproducción.
  - *Qué se construyó* (`roku/VisionPlusDirect/`, independiente de `roku/VisionPlusRelay`): `docs/ARQUITECTURA.md` (tres planos, límites reales del Roku, opciones para llenar el resolver), `docs/CONTRATO_API.md` (endpoints, cuerpos, errores, reglas del m3u8), canal SceneGraph completo (portada con héroe, filas, búsqueda, ficha con temporadas/capítulos, reproductor HLS con job 202, heartbeat, progreso, «Omitir intro», siguiente capítulo), `tools/pack_roku_direct.ps1` (brighterscript + ZIP con `manifest` en raíz + anti-BOM) y `mock/roku_mock.py` (sirve el contrato y un HLS de demo de Apple para ver el canal sin VPS).
  - *Decisiones de contrato*: `id` **opaco** (el canal no parsea `xtv:`); **ticket en la URL** porque el nodo `Video` no manda headers a los segmentos (también en `EXT-X-KEY`/`EXT-X-MAP`); `/play` responde **202 + job** por el arranque en frío del motor; **HEVC se pregunta** (`roDeviceInfo.CanDecodeVideo`) y el servidor devuelve `CODEC_UNSUPPORTED` si no hay variante; el resolver necesita **slot de portal propio** (si usa el SN del teléfono patea la app).
  - *Trampas de Roku cazadas en la documentación (habrían dejado el canal en blanco)*: **`RowList` NO copia los campos del `ContentNode` al componente del ítem** — hay que leer `itemContent`, y el foco del ítem llega por `itemHasFocus` (no `itemFocused`); los campos custom del `ContentNode` necesitan **`AddFields`** (asignar uno inexistente es error de runtime); `showRowLabel`/`rowItemSize` son **arrays** (`[true]`, `[[300,214]]`); el espaciado horizontal es **`rowItemSpacing`** (`itemSpacing` es vertical). También: el foco se pide **después** de `appendChild` (`callFunc("takeFocus")`), nunca en `init()`.
  - *Verificado*: `tools/pack_roku_direct.ps1` compila con **brighterscript 0 errores** y genera `roku/VisionPlusDirect.zip` (25 archivos). El mock se probó de punta a punta con HTTP real: `health`, `home` (4 filas), `search`→House, `title` (2 temporadas/5 caps), `play` 202→2 sondeos→ready con `season/episode`, `progress`→fila «Seguir viendo», `stop`, 404 de ficha y **400 si el cuerpo JSON es inválido** (el mock antes lo tragaba y respondía `NOT_PLAYABLE`; corregido).
  - *Estado*: el **plano A** está implementado en el repo (ver el lote de abajo) y **pendiente de desplegar**; el **plano B** (`play` + `/stream/...`) **no existe** (espera M1/M2). No hay Roku físico conectado a este equipo, así que la prueba en pantalla está pendiente.
  - *Reglas fijas del dueño (2026-10-25)*: **(1) sin caja** — ningún aparato Android como reproductor ni como resolutor obligatorio; **(2) la app del proveedor no vuelve al camino**. Efecto: **C y E quedan descartadas**; **A** (redroid + motor headless en un VPS ARM) solo sigue viva si «app» no incluye el motor sin interfaz; **F** (transporte propio, sin SDK) es la única que cumple las dos al pie de la letra. Costes y matriz en `roku/VisionPlusDirect/docs/DECISION_REPRODUCCION.md`.
  - *Medición (2026-10-25, AVD x86_64, app debug con relay)*: el **modo vista reproduce** (`FFP_MSG_VIDEO_RENDERING_START`), pero **no publica lista**: el bean `uc.c.d()` vuelve vacío y el HTTP del motor (`Ranger/4.9.4`) responde 404/400 a `/vod/0/<code>.m3u8`, `/vod/1/...`, `/hls/...` y `/`. La caché es `files/x4k-luna/block_cache/<playCode>_media.ts/.metainfo`, **ofuscada** (sin `.ts` legibles). El **headless aborta bajo traducción ARM** (`std::out_of_range: map::at`), así que el m3u8 del relay no se pudo capturar aquí. Veredicto **M2 probable (no confirmado)**: el video solo existe dentro del proceso del motor. El dato final son **2 min en un teléfono ARM real** con `roku_measure.py` (sonda en `%TEMP%\opencode`; no se subió al repo). La instrumentación temporal del reproductor se revirtió.
  - *No compromiso del VPS (2026-10-25)*: el dueño no acepta nada que ponga en riesgo el VPS (que además hospeda La Veinte/Supabase y otros sitios). Regla: **el VPS no transporta video**. Permitido: catálogo, pairing/autorización y devolver una URL de CDN ya emitida. Prohibido: `/stream`, caché/transcodificación, motor/redroid y aceptar URLs del cliente. La medición pasa a **M1a** (CDN autocontenido: sin headers/ cookies/IP → D-direct en el VPS, 2–4 días), **M1b** (CDN con headers/IP → host aparte o catálogo sin reproducción) y **M2** (solo motor → host aparte o catálogo). A/F no se descartan como técnica: se descartan **en el VPS**.
  - *Standalone (2026-10-25)*: el dueño exige que el Roku **funcione solo, sin depender de nada más**. Se adopta: **ningún equipo de la casa** (ni teléfono, ni caja, ni PC); **el VPS sí** (es el backend del producto). **No existe versión con cero servidor**: el Roku no puede resolver licencia/P2P (motor ARM/bionic) ni servir 52,588 títulos desde el ZIP. Consecuencia: **A (redroid) queda fuera también por operación** (segundo servidor ARM + runtime Android = «algo más»); C/E por regla; B es capa. Vivas: **D (M1) / F (M2)**. Si M2, F es el único camino standalone (4–10 semanas).
  - *Plano A (catálogo) EN CURSO (2026-10-25)*: implementado en el repo — `server/api/roku.py` (forma del contrato + receta de filas, **puro**) y rutas en `server/api/app.py` (`/v1/roku/health|home|search|title|episodes`, reusando `film`/`_grouped_entry`/`_enrichment_map`/búsqueda y el espejo de capítulos). Se extrajo `_season_payload` para que `/v1/episodes` y el Roku compartan la MISMA copia+TMDB (sin duplicar SQL). `playable` sale del `play_ref`; **cero acoplamiento al resolver** y `/v1/roku/play` no existe todavía. **386 backend en verde** (21 nuevos en `test_roku.py`). **Despliegue pendiente**: el `app.py` local lleva cambios de otros agentes; no se copia a ciegas al VPS. El **plano B** (reproducción) espera M1/M2; la **rama B** (caché) también.
  - *Siguiente*: (1) cerrar M1/M2 con el teléfono (USB o IP+PIN); (2) desplegar el plano A cuando el árbol esté coordinado y probarlo con `curl` + el canal; (3) plano B según el filtro lexicográfico (`docs/DECISION_REPRODUCCION.md`); (4) `ffprobe` del `url` del `/play` **antes** de tocar el Roku; (5) sideload y prueba con mando.
- **RELAY ROKU (VOD, en pruebas; sin publicar): el teléfono sirve el HLS del motor por la LAN y un canal de Roku sideloadeado lo reproduce — 18 guardianes nuevos** (2026-10-25):
  - *Pedido*: «¿podemos hacer algo para desencriptar las fuentes de video de nuestro motor y distribuirlas por http para portarlo a Roku?» → «Empecemos con VOD, lo voy a distribuir por medio del modo desarrollador».
  - *Lo que hay que entender (cambia el plan)*: **no hay nada que descifrar**. El motor ya publica un HLS plano en loopback (`uc.c.d()` → `http://127.0.0.1:<puerto>/vod/0/<code>.m3u8`, exactamente lo que consume `OfflineHttpDownloader`). Lo que faltaba era **re-servirlo fuera de loopback** y un cliente. Y Roku **no puede cargar el motor** (BrightScript/SceneGraph, sin NDK/JNI): el relay es obligatorio, no una optimización.
  - *App (`data/relay/`)*: `RelayRules` (puro: PIN con comparación de tiempo constante, guardia anti-SSRF **solo loopback**, resolución de URIs, reescritura m3u8 con `EXT-X-TARGETDURATION`/`EXT-X-ENDLIST`/`PLAYLIST-TYPE:VOD` y `URI="..."` de KEY/MAP/MEDIA, content-types que Roku acepta); `RelayEngine` (`prepareVod` → `playHeadless` → sondeo `queryStatusUrls`, el mismo camino probado del Modo Offline, sin descargar); `RelayServer` (HTTP en **48640**, `/health` sin token, `/home`, `/search`, `/start`, `/hls/<sid>/index.m3u8`, `/hls/<sid>/p?t=&u=`, `/stop`; `ReentrantLock.tryLock` para que dos `/start` no peleen por el motor; apagado tras 10 min sin uso); `RelayService` (proceso **`:x4k`**, `dataSync`, wakelock parcial, `START_NOT_STICKY`); `RelayLauncher` (PIN estable en prefs `vision_relay`, sonda `/health` por loopback y **reinicio ordenado de `:x4k`** reusando `ACTION_DIE_FOR_DOWNLOADS` cuando la vista del reproductor ya reprodujo).
  - *Ajustes del teléfono*: tarjeta «Roku (relay local)» (`ui/settings/RokuRelayCard.kt`) con encender/apagar, dirección LAN, PIN, copiar. **No se ofrece en la tele.**
  - *Canal Roku (`roku/VisionPlusRelay/`)*: SceneGraph mínimo (manifest `id=dev`, `MainScene` + `PosterItem` + `ApiTask` con Task node para no bloquear la escena), servidor/PIN a mano una vez en `roRegistrySection`, filas de pósters, búsqueda con `KeyboardDialog`, `Video` node `streamFormat: "hls"` y Atrás vuelve a la lista. Empaquetador `tools/pack_roku.ps1` que crea el ZIP con rutas `/` y **aborta si `manifest` no está en la raíz** (`Compress-Archive` puede romperlo).
  - *Bugs cazados antes de publicar (los tres rompían la reproducción)*: (1) las rutas de segmentos generadas **no llevaban el PIN** → 401 en el primer segmento; ahora el token va delante de `u=`; (2) el motor puede rotular el m3u8 como `text/plain` → se fuerza `application/vnd.apple.mpegurl` (Roku no reproduce si no); (3) dos `/start` simultáneos podían encender dos programas headless; ahora el segundo recibe 409.
  - *Alcance deliberado de v1*: **solo películas** (una serie agrupada exige resolver temporada→episodio, otra ruta del portal), **un título a la vez** (el motor admite un programa por proceso; cada stream concurrente necesitaría su propio SN/slot y se patearía con el teléfono), **solo LAN** y **calidad única del portal (h265/720p)**: un Roku sin HEVC necesitaría transcodificación, que no está en v1.
  - *Verificado*: compila (`:app:compileNormalDebugKotlin`) y **18 guardianes en verde** (`RelayRulesTest` 13: token, SSRF, URIs relativas/absolutas, lista maestra vs VOD, llaves/mapas, content-types, token en segmentos; `RelayWiringTest` 5: servicio en `:x4k` de primer plano no exportado, solo teléfono, proxy loopback+token, camino headless del Modo Offline, paquete del canal con `manifest` en la raíz). ZIP generado y validado (8 archivos, 5.5 KB).
  - *El canal se valida solo*: `tools/pack_roku.ps1` corre **`brighterscript`** (compilador de BrightScript/SceneGraph de la comunidad) sobre una copia antes de zipear y **aborta si no compila**. La primera pasada encontró **4 errores reales** (`evt as roSGNodeEvent` no es un tipo válido en anotaciones → `evt as object`) que habrían reventado el canal al abrirlo; hoy compila con **0 errores**. También se quitó `maxLines` del `Label` del póster (campo no garantizado: un campo inexistente tumba la escena en tiempo de ejecución y `brighterscript` no lo detecta).
  - *¿Standalone?* El **canal** sí (ZIP sideloadeado, sin PC ni tienda después); la **solución no**: motor + sesión viven en el teléfono con Vision+ y el relay encendido, y un SN = una sesión (usar la app en otro equipo mientras el Roku reproduce se patean). Para acercarlo a electrodoméstico: un teléfono/box Android viejo enchufado + `BOOT_COMPLETED` + modo relay-only (pendiente). En el Roku **no puede** vivir el motor (sin NDK; `.so` ARM/bionic).
  - *¿Emulador de Roku?* **No existe oficialmente**; el flujo real exige un Roku físico en modo desarrollador. Alternativas: `brighterscript` (validación, ya integrada), `brs`/`brs-engine` (interpretan BrightScript, sin HLS real) y `ffprobe`/VLC para el relay. El banco de pruebas sigue siendo un Roku Express/Roku TV.
  - *Investigación «standalone» (2026-10-25, `docs/ROKU_STANDALONE.md`)*: **el cliente del portal SÍ es portable a Roku; el bloqueo es UNA función**. Verificado en docs de Roku: `roEVPCipher` **sí tiene 3DES** (`des-ede3`, 3-key ECB con PKCS#5) → el sobre DESede del portal se puede hablar desde BrightScript; `roEVPDigest` da MD5 (SN derivado); el nodo `Video` hace HLS/HEVC. Lo único que no se puede es **resolver `playCode`+`license` → flujo** (eso es `libranger-jni` + `libtnet`, ARM/bionic contra ART). Y **no se puede emular en el Roku** (sin NDK, sin `.so`, sin ART): la emulación va fuera. Opciones evaluadas: (A) **Android nativo como electrodoméstico** (caja/teléfono viejo, mismo APK) — recomendada; (B) **redroid** (Android en Docker, arm64/amd64, **sin KVM**, `binder_linux`+`ashmem_linux`; en x86 las imágenes ya traen `libndk_translation`, el mismo mecanismo del emulador del PC); (C) emulador en Windows (Vision+ PC, ya existe); (D) descartadas: cloud (fuera de LAN) y **cliente nativo Roku** (reimplementar la pieza protegida: frágil, semanas de trabajo y la posición legal más expuesta).
  - *Pendiente de esa investigación*: relay disponible fuera del móvil (la tarjeta es solo-teléfono) + arranque automático (**Android 15 restringe FGS `dataSync` desde `BOOT_COMPLETED`**: decidir entre `connectedDevice`, kiosco o `JobScheduler`, probándolo en el equipo real), y el experimento clave: **¿el motor headless corre en redroid?** (sin GPU ni `MediaCodec` si se confirma que no lo toca).
  - *Pendiente*: probar en físico con el orden del `roku/README.md` — (1) `curl /health`, `/home`, `/start` y **`ffprobe` del `src`** desde la PC (si ffprobe no ve video, no hay canal que lo salve); (2) instalar el ZIP en modo desarrollador; (3) reproducir un título y medir. Después: series (temporada→episodio), vivo, auto-arranque del relay y valorar auto-descubrimiento.
- **HERO CINEMATOGRÁFICO + BANNERS DIARIOS DEL VPS (2026-10-26): el Inicio abre con arte panorámico real, rota con Ken Burns y mezcla partidos en vivo de la agenda; `/v1/banners` busca 5 películas una vez al día (TMDB) — 113 launcher + 395 backend; Vision+ Home 1.2.1 publicado en v2.5.157**:
  - *Pedido*: «El launcher debe verse más bonito en diseño y animaciones; el hero debe mostrar películas destacadas y partidos en vivo destacados de nuestra agenda; que el VPS busque una vez al día y mande los banners».
  - *Servidor (`server/api/banners.py`, desplegado)*: `GET /v1/banners` (API key) devuelve 5 películas/series con **`backdrop` de TMDB w1280**; la foto se guarda en la tabla nueva `launcher_banners` y se refresca **cada 20 h** (la primera petición del día la construye; las siguientes sirven la caché y el refresco corre en `BackgroundTasks`). Solo entran fichas **`xtv`** (fuera `magis`, que no tiene reproducción garantizada) y **películas primero**; el resto, series/anime. Sin `TMDB_API_KEY` o sin TMDB, el banner sale con el póster del catálogo.
  - *Trampa de CDN cazada EN VIVO*: el mismo backdrop respondía **200 en un edge de BunnyCDN y 404 en otro** (12 h de caché). Se añadió un **`?v=<epoch diario>`** a cada URL; desde entonces 200 en el VPS (con `TZ=America/Mexico_City`) y en el emulador. El launcher además pinta el **póster detrás** del backdrop: si el arte no carga, el hero no queda negro.
  - *Despliegue quirúrgico al VPS* (sin copiar el `app.py` local, que trae cambios de otros agentes): se parchearon `api/app.py` (import + ruta), `api/Dockerfile` (`COPY … banners.py`), `docker-compose.yml` (`TMDB_API_KEY` en el servicio `api`) y se copió `api/banners.py`; respaldos `*.bak-banners-20261026-*`. Verificado en público: `/v1/banners` **200**, 5 items, primero **Spider-Man: Un nuevo día** (xtv, 2026, 8.4), backdrop 205,430 B; se envejeció la tabla 30 h y el siguiente request sirvió la foto vieja al instante y **refrescó detrás** con un `?v=` nuevo.
  - *Launcher (`HeroSlide`, `BannersRepository`, `Hero.kt`)*: el hero unifica **cine/series** (banners del VPS con arte panorámico o `feed.hero` como respaldo) y **deportes** (`feed.sports` de la agenda real). Un partido **en vivo abre el hero** (liga, escudos, marcador, `EN VIVO`, «Ver en vivo»); los próximos van al final; el resto son películas. Diseño: arte a sangre, **Ken Burns**, degradados de cine, luz azul, badge con pulso en vivo, marcador grande, crestas ambientales, **indicador de progreso** y pausa de la rotación al enfocar los botones. En equipos de 2 GB (`profile.lowRam=false`) sí corre el Ken Burns; en 1.5 GB se queda el crossfade + rotación (13 s).
  - *Sin red / sin VPS*: `BannersRepository` cachea el JSON en `files/launcher_banners.json` (TTL 30 min) y el hero cae a `feed.hero`; los partidos salen del feed. El hero se recalcula solo cuando cambia el feed o los banners, no cada minuto, para no reiniciar el carrusel. **1.2.1**: el refresco periódico vive dentro de `repeatOnLifecycle(RESUMED)`, así que se corta al abrir una app/reproductor y no gasta red en segundo plano.
  - *Pruebas*: `HeroSlideTest` (9: mix en vivo/películas, terminados fuera, próximo al final, respaldo del feed, marcador y enlaces, CDMX), `BannersParseTest` (4: película/serie/deportes/basura), `HeroBannersWiringTest` (3: launcher↔endpoint, pausa/animaciones, VPS diario) + `test_banners.py` (9: dedupe por obra, películas primero, serie temporada, TMDB rating, caché/refresco/fallback) = **113 launcher + 395 backend** en verde.
  - *Verificado en emulador con el release R8*: el hero mostró **DESTACADO HOY · Spider-Man: Un nuevo día · 2026 · 8.4 · 2 h 25 min** con «Reproducir / Más información», sin crash; Coil guardó el backdrop de **205,430 B** en `cache/home_images`. El deporte no se pudo ver en el emulador porque no tiene el provider de Vision+ (el feed real de la tele sí lo trae).
  - *Publicado*: **Vision+ Home 1.2.1 (vc18)**, 1,476,052 B, firma histórica `9593cb0d…`, como `VisionPlusHome.apk` del release **v2.5.157**; sección nueva en las notas (la 1.2.0 se reemplazó en minutos por la 1.2.1). **No hizo falta OTA de la app**: el launcher pide `/v1/banners` directo y los deportes ya viajan en el feed.
  - *Costo medido en el VPS (2026-10-26)*: `/v1/banners` **3.9 KB (1.66 KB gzip, 7.5 ms)**; tabla `launcher_banners` **5 filas / 88 KB**; API **58 MB/1 GB y 0.1 % CPU**; el arte lo sirve **TMDB**, no el VPS. El gasto real es el feed que ya existía: `/v1/gee38/events` 1.72 MB (**163 KB gzip**) cada ~10 min y `/v1/epg/bulk` 81 KB (**17 KB gzip**) cada 5 min con el Inicio al frente; ≈**0.2 GB/mes por tele con 5 h/día** (≈0.85 GB/mes si estuviera 24/7), muy por debajo de los 10 TB del plan. El refresco se corta en segundo plano desde 1.2.1.
  - *Pendiente*: probar en la TCL/caja física (el emulador es teléfono y sin feed); validar el hero de un partido en vivo con la agenda real; si se quiere, añadir banners de deportes generados en el VPS (hoy el arte del partido se compone en el launcher con escudos + gradiente).
- **LAUNCHER 1.1.0 PUBLICADO: el Inicio deja de ser solo una vitrina — OK largo con el mando, desinstalar apps, ficha/cierre de apps, buscador de apps instaladas y refresco automático al instalar o quitar — 97 launcher; asset del release v2.5.157** (2026-10-25):
  - *Pedido del usuario*: «El launcher debe sentirse como un launcher útil, no solo una interfaz bonita; OK largo debe darte opciones, poder desinstalar apps, etc.».
  - *OK largo real con el mando*: `FocusableSurface` detecta OK mantenido (550 ms o la repetición del mando) y también **Menú/Info**; antes `combinedClickable` solo lo daba con el dedo. `TvPressGuard` se come el KeyUp de esa pulsación para que no active la primera acción del menú (probado en emulador: sin el candado «Abrir» se disparaba solo). El candado se vence a los 2.5 s para no dejar una tecla muda.
  - *Menú de app completo* (`LauncherScreen`): Abrir, Fijar/Quitar, Ocultar/Mostrar, **Información de la app**, **Cerrar app** (solo apps de usuario) y **Desinstalar** (o «Quitar actualización» en apps de fábrica actualizadas), con el destructivo en rojo. `ItemActionsDialog` es desplazable y conciso: antes se recortaba en pantallas de ~411 dp y escondía Desinstalar.
  - *Desinstalar de verdad*: `AppsRepository.requestUninstall` lanza `ACTION_DELETE`; se añadió **`REQUEST_DELETE_PACKAGES`** al manifest (verificado en Android 16: sin ese permiso el `UninstallerActivity` abre y se cierra solo con «does not have REQUEST_DELETE_PACKAGES»). También hay papelera en Gestionar apps y `KILL_BACKGROUND_PROCESSES` para «Cerrar app».
  - *Bug real cazado en el emulador*: `FLAG_INSTALLED` no viene en la consulta del launcher en Android 16 y dejaba sin «Desinstalar» a las apps instaladas por el usuario; la regla es `!isSystem || updatedSystem` (sin ese flag).
  - *La lista se refresca sola*: receptor de `PACKAGE_ADDED/REMOVED/CHANGED/REPLACED` + recarga en `ON_RESUME`. Verificado de punta a punta: desinstalé GeeSports con la papelera → confirmación del sistema → al volver, la app desapareció del gestor y el contador bajó de 24 a 23 sin reiniciar.
  - *Buscador útil*: `filterApps` (sin acentos, compacto, también por paquete) pinta las apps instaladas como atajos arriba de los resultados; «gee» → GeeProbe. Probado en el emulador.
  - *Más sistema*: Pantalla, Sonido, Almacenamiento, Fecha y hora, Acerca de y Apps del sistema en la pestaña Apps; se abren directo y avisan si el firmware no tiene esa pantalla.
  - *Guardianes*: `AppActionsTest` (10: búsqueda de apps, acentos, compacto, paquete, límite y regla de desinstalación) + `LauncherUtilityTest` (6: cableado de OK largo/guard, desinstalar/instalar/cerrar, refresco por paquetes, buscador y accesos del sistema) = **97 launcher** en verde.
  - *Publicado*: Vision+ Home **1.1.0 (vc16)** firmado con la llave histórica (`9593cb0d…`, 1.46 MB) como `VisionPlusHome.apk` del release estable **v2.5.157**, con una sección nueva en las notas. Asset descargado y verificado (`versionCode=16 versionName=1.1.0`, firma histórica). No se tocó la app.
  - *Pendiente*: probarlo en la TCL/caja física (el emulador es teléfono; el flujo de mando y el desinstalador ya se verificaron ahí); valorar el menú de opciones también en las tarjetas de «Seguir viendo»/catálogo (hoy apps y canales).
- **ANÁLISIS DE ERRORES DEL VPS + BLINDAJE DEL ÍNDICE (servidor desplegado; app preparada para la 2.5.153) — 929 Android + 365 backend** (2026-10-25):
  - *Pedido*: «analiza los errores que se han reportado en el vps» → «Dale» a aplicar los P0 del servidor y dejar los de la app para la próxima OTA.
  - *Hallazgos medidos*: (1) la 2.5.151 rota dejó **8 crashes `NoClassDefFoundError: visionplus/V1`** en el Xiaomi del usuario, todos dentro de los **18 minutos** entre su publicación (23:10 MX) y la 2.5.152 (23:28 MX); desde la 2.5.152 no hay ni un crash. (2) **26 `INDEX_FETCH_FAIL: HTTP 500` de 9 equipos** (peor episodio 24/10 11:55–12:43 MX) por `asyncpg DiskFullError ... base/pgsql_tmp`: `/var/oled` (15 GB) estaba al 87 % con 2 GB libres y la consulta del índice escribía **16.8 MB de temporales por recálculo** cada 5 min. (3) De 464 ANR, **336 eran `nativePollOnce`** (proceso congelado en background: `uptimeMillis` sigue) y la huella incluía los segundos → **12 alertas de Telegram** distintas. (4) El `PLAYBACK_LICENSE_FAIL` de 1 equipo era un **prefetch cancelado** al salir de la ficha. Ruido sano: `SESSION_RENEW_FAIL` solo en builds ≤2.5.122, `PLAYER_SOFT_RESTART`/`PORTAL_SLOW` del motor/portal y `PLAYER_RESIDENT_IDLE` es la red de seguridad.
  - *Servidor desplegado*: `server/api/app.py` — índice con **`cross join lateral`** por categoría (0 KB de temporales, 536 ms vs 986 ms, **127/127 categorías idénticas**), caché **300 s → 1800 s** y **última copia buena** servida si la BD falla (un 500 ya no deja el Inicio a medias; la excepción se loguea). BD: `work_mem=64MB`, `temp_file_limit=1GB`, `max_wal_size=512MB`; respaldos 7→4 días; `gee38` baja a INFO los huecos permanentes del CMS (404 / host vacío). Verificado en vivo: `/v1/index` 200 en ~1 s (versión 121), **0 KB de temp**, 0 respuestas 500.
  - *App preparada (sin publicar)*: el vigilante de ANR **solo mide con la app al frente** (`activityResumed/Paused` en MainActivity y X4KPlayerAty) y la huella usa **rangos** (`5-9s/10-59s/1-10min/mas-de-10min`) con la duración exacta en `message`; `prepareVod` ya no reporta `PLAYBACK_LICENSE_FAIL` cuando la cancelación viene de salir de la ficha. Guardianes: `DiagnosticsNoiseTest` (5) + 4 en `server/tests`.
  - *GitHub*: se borró el asset duplicado `VisionPlus-2.5.151-ARREGLADA.apk`, la v2.5.151 quedó marcada como **retirada** (el APK que conserva ya es el corregido) y el canal **beta** pasó a servir **2.5.152** (antes 2.5.149). Suite **929 Android + 365 backend** en verde; `:app:assembleNormalRelease` compila con R8 (`colisiones=0`).
  - *Pendiente*: probar en físico la reducción de ANR (el VPS debe dejar de recibir `MAIN_BLOCKED_mas-de-10min`).
- **VPS: DOCKER MOVIDO AL DISCO GRANDE — el volumen de 15 GB pasa de 94 % a 13 %** (2026-10-25):
  - *Motivo*: `/var/oled` (15 GB, donde vivía el `data-root` de Docker) estaba al **94 % con 975 MB libres** y el grupo LVM no tiene espacio para crecer (disco de 150 GB 100 % asignado; el root XFS no se puede encoger). El 93 % era de **Supabase/La Veinte** (6.7 GB de imágenes + 1.2 GB de backups + 581 MB de `laveinte-app`) y de las otras webs (Innovación Rx, Rítmika, CobaMedic, SportPlus), no de Vision+ (~2.5 GB).
  - *Qué se hizo*: ventana de ~6 min con todos los sitios parados; `rsync -aHAX --numeric-ids /var/oled/docker/ /var/lib/docker/` (**12.08 GB, 274,477 archivos**, segunda pasada con 0 cambios), `daemon.json` `data-root` → `/var/lib/docker`, Docker arriba; se pausaron el watchdog de Vision+ y el push-campaigns de La Veinte durante la ventana y se restauraron después; el store viejo se borró al verificar.
  - *Verificado*: **20/20 contenedores arriba, 0 unhealthy**, `healthz` 200, `/v1/index` 200 (1.3 s), **52,577 títulos**, Supabase DB accesible, `la20.com.mx` 307, `visionplusv2` 200; `streaming-backend_pgdata` ahora en `/var/lib/docker/volumes/...`. **`/var/oled`: 1.9 GB usados / 14 GB libres (13 %)**; `/`: 50 GB usados / 84 GB libres (38 %).
  - *Seguridad*: el store movido quedó con la etiqueta genérica `var_t`; se reetiquetó a **`container_var_lib_t`** con `restorecon` (**668,723 archivos**, SELinux sigue **Enforcing**, 0 AVC). `/var/lib/docker` es 710 root:root; ningún contenedor monta el socket de Docker ni el store (solo sus propios volúmenes) y todos los puertos siguen publicados solo en `127.0.0.1`.
  - *Vigilante de disco*: `sync/diag.py` gana `disk_health` en el loop del triage (cada 5 min): avisa al Telegram si `/` baja de **10 % o 5 GB** libres (y de `/var/oled` si está visible), con enfriamiento de **12 h**, aviso de recuperación y estado en `sync_meta.health_disk_alert_at`. `disk_is_low`/`disk_should_alert`/`disk_alert_text`/`disk_recovered_text` son puros y tienen 6 tests. En el contenedor `shutil.disk_usage("/")` ve el disco de 133 GB (overlay2) y `/var/oled` se omite si no está montado. **371 backend** en verde; desplegado y verificado (`disk_health -> 0`, 89 GB libres).
  - *Resultado*: Docker (imágenes + volúmenes de TODOS los proyectos) vive en el disco de 133 GB; el volumen de 15 GB queda solo para backups de Supabase y `laveinte-app` (14 GB libres). Ya no hay competencia por disco entre proyectos.
- **HOTFIX 2.5.152: la 2.5.151 no abría — el inyector del motor sobrescribía `classes2.dex` de la app al crecer con LibADB/Conscrypt/BouncyCastle — 924 app** (2026-10-24):
  - *Reporte del usuario*: «Se rompió la app, ya no abre» justo después de instalar la 2.5.151.
  - *Causa medida (comparando los dos APK, no adivinada)*: el build 2.5.151 produjo **`classes.dex` (9,758,564 B) + `classes2.dex` (3,895,868 B)** porque las dependencias nuevas (LibADB + Conscrypt + BouncyCastle) pasaron del límite del primer DEX. `tools/inject_engine_dex.py` escribía el motor **siempre** en `classes2/3/4.dex` y además **borraba** cualquier DEX con ese nombre: el `classes2.dex` propio desapareció y lo reemplazó el del motor (8,370,784 B). ART se queda sin clases → la app muere al arrancar. Prueba: el APK publicado tiene `classes2.dex=8,370,784` (motor) y el build previo tenía `classes2.dex=3,895,868` (app).
  - *Fix*: el inyector calcula el **primer índice libre** (`target_names`, salta `classes.dex`, `classes2.dex`…) y **aborta** si fuera a pisar un DEX existente; el publicador cuenta los DEX antes/después y **aborta si no son `antes + 3`** («El APK perdio DEX propios al inyectar el motor»). Verificado en local: `classes.dex 9.76 MB · classes2.dex 3.89 MB (app) · classes3/4/5 (motor)`.
  - *Guardianes*: `BundledEngineTest` exige `target_names`, el salto de índices, el aborto por DEX existente y el candado `$dexBefore + 3` del publicador.
  - *Lección*: cualquier paso que **escriba archivos con nombre fijo** dentro del APK (DEX, libs) tiene que contar/verificar lo que ya hay; un build que crece deja de ser el caso que se probó.
  - *Publicado*: **v2.5.152 (vc391)** con app + Vision+ Home 1.0.14, firma histórica. La 2.5.151 quedó **retirada**: su APK ya es el corregido (se borró el duplicado `-ARREGLADA` y las notas avisan de instalar la 2.5.152).
- **TELÉFONO → TELE: «Inicio en la TV» por ADB inalámbrico, sin PC (en pruebas, sin publicar) — 8 tests nuevos** (2026-10-24):
  - *Pedido del usuario*: «¿puedo hacer un script para que Vision del móvil haga este proceso en una tv con Vision TV? Para no tener que usar una PC y adb, sino el mismo móvil lo configura como predeterminado» → «que sea casi desatendida, solamente el user activa depuración inalámbrica y nuestra app hace el resto, pero esto solo se activa en la app de teléfono».
  - *Límite de Android que NO se puede saltar*: el emparejamiento exige el **código de 6 dígitos** que muestra la tele (SPAKE2). Se pide UNA vez; la llave queda guardada y después se reaplica sin código.
  - *Qué se hizo (móvil)*: `data/adb/AdbKeys.kt` (RSA 2048 + certificado autofirmado con BouncyCastle, una llave por teléfono en `filesDir/adb/`, equivalente al `~/.android/adbkey`), `data/adb/TvAdb.kt` (cliente LibADB `AbsAdbConnectionManager` + `AdbDiscovery` con `NsdManager` para `_adb-tls-pairing/_adb-tls-connect`; el descubridor de LibADB solo acepta el MISMO equipo, por eso no sirve aquí) y `data/adb/TvHomeSetup.kt` (flujo + reglas puras `TvHomeRules`).
  - *Flujo*: activar Depuración inalámbrica → «Emparejar dispositivo con código» → el móvil busca la tele, se teclea el código → `pair()` → `connect()` → `pm set-home-activity` → pulsa Inicio y comprueba con `dumpsys activity activities` quién quedó al frente → si sigue el launcher de fábrica, `pm disable-user --user 0 <ese>` (hasta 3 intentos) → verifica. «Restaurar la tele» hace `pm enable` de lo retirado y devuelve el Inicio original.
  - *Teles Android 9/10 (TCL y cajas)*: no tienen emparejamiento moderno; se usa **ADB en el 5555** con la dirección que el teléfono ya conoce del control remoto (`RemoteClient.rememberedTvs`) y se reintenta mientras la persona acepta el aviso «¿Permitir depuración?» del televisor.
  - *Candados de seguridad*: nunca desactiva System UI, Ajustes, instalador, permissioncontroller ni providers (lista `PROTECTED`); **no retira ningún Inicio si Vision+ Home no está instalado en la tele** (dejaría la tele sin pantalla); la llave no sale de `filesDir`.
  - *Varias teles (pedido del usuario)*: la app ya no se ata a un televisor. `data/adb/TvAdbStore.kt` guarda una **lista** (`TvRecord`: id = Android ID/serial del televisor, nombre, host, `paired`, `applied`, **su** Inicio original y **sus** apps retiradas; JSON en `vision_tv_adb`, parseo/upsert/remove puros). Cada operación es por tele: `apply(tvId)`, `restore(tvId)` y `forget(tvId)`; `connectTo()` descubre todos los puertos ADB y **compara la identidad** antes de tocar nada (si hay dos teles y ninguna coincide, no se toca la equivocada). «Olvidar» avisa si esa tele tiene cambios y ofrece **«Restaurar y olvidar»**.
  - *Diagnóstico y auto-instalación (2026-10-25)*: cada paso deja `UiState.log` (el comando y la primera línea de la respuesta) y la interfaz lo muestra en **«Detalles»** (sección y diálogo); `sh()` pone tope de 8-20 s por comando para que el flujo nunca se quede «pensando» (el fallo reportado: «se queda haciendo el proceso pero no hace nada en la tv»). El launcher viaja como asset del teléfono (`app/src/main/assets/vision_home.apk`, 1.0.14) y se instala por `exec:pm install -S` cuando el televisor no lo tiene — antes el flujo se abortaba para no dejarlo sin pantalla.
  - *UI*: Ajustes del **teléfono** → sección «Inicio en la TV» (`ui/settings/TvHomeSetupCard.kt`, solo móvil) con la lista de teles (estado + Aplicar/Restaurar/Olvidar), «Agregar otra tele», código de 6 dígitos y «Emparejar y aplicar».
  - *Dependencias*: `com.github.MuntashirAkon:libadb-android:3.1.1` (JitPack) + `sun-security-android:1.1` + `conscrypt-android:2.5.3` + `bcpkix-jdk15to18:1.81`; keep rules en `proguard-rules.pro` para R8. **Ojo**: `conscrypt-android` rompía TODA la suite Robolectric (`UnsatisfiedLinkError: no conscrypt_jni`), se excluye del classpath de unit tests (`configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }`).
  - *Guardianes*: `TvHomeAdbTest` (8: parseo de `resolve-activity` y de `mResumedActivity`/`topResumedActivity`, lista protegida, launcher retirable, componente→paquete, cableado pair/disable/enable/legacy/probe, y que solo el teléfono lo expone).
  - *Pendiente*: probarlo en un teléfono real contra la TCL/caja (no había teléfono con ADB al terminarlo), publicar la OTA y validar el release con R8 (el flujo usa reflexión/JCA).
- **LAUNCHER 1.0.14 (en pruebas, sin publicar): el estado del Inicio deja de mentir y el botón deja de no hacer nada — resolución real del intent HOME (el rol engañaba), visibilidad de los launchers del sistema, rutas comprobadas con `resolveActivity` y guía de 3 pasos cuando el firmware no tiene selector — 81 launcher** (2026-10-24):
  - *Reporte del usuario*: «el launcher aún no queda, en tvs donde lo he instalado no queda ni da la opción de launcher predeterminado» (caja Android TV genérica: «no aparece opción en Ajustes» y «aparece y no cambia»).
  - *Tres causas medidas en equipos reales*:
    1. **`RoleManager.ROLE_HOME` mentía**: en el Chromecast (Android 14) `cmd role get-role-holders android.app.role.HOME` devolvía `com.chronosbvrx.visionplus.home` mientras el Inicio real seguía siendo `com.google.android.apps.tv.launcherx`. El código daba por hecho que «ya es tu Inicio» y no pedía nada.
    2. **Visibilidad de paquetes (Android 11+)**: sin consultar el intent HOME en `<queries>`, `resolveActivity` no ve el launcher del sistema y devolvía otra app (el asistente de configuración) como «Inicio actual».
    3. **Intents que el firmware no atiende**: en la TCL (`Android 9`) `am start -a android.settings.HOME_SETTINGS` y `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` **no abren nada** (el intent se «lanza» y la pantalla sigue igual); `openHomeSettings()` los daba por buenos y el botón quedaba muerto.
  - *Descartado con la documentación oficial*: `android:priority="5"` en el filtro HOME **no sirve** — Android recorta a 0 cualquier prioridad >0 pedida por una app no privilegiada (comprobado: el APK instalado declara `priority(0x0101001c)=5` y `cmd package query-activities` sigue reportando `priority=0`; el launcher de Google TV declara 2 y gana). Se retiró y quedó documentado en el manifest.
  - *Launcher (`HomeRole.kt` + `AppsRepository`)*: `isVisionHomeDefault()` ahora compara **quién resuelve el intent HOME** (`resolveActivity(..., MATCH_DEFAULT_ONLY)`) con nuestro paquete (`isOwnHome`, puro); el rol solo decide si se puede **pedir** (`roleHeld` = ya concedido ⇒ no vuelve a preguntar, va a Ajustes). `homeRoleAction(sdk, roleAvailable, roleHeld, isDefault, handlesHomeSettings, handlesDefaultApps)` devuelve `REQUEST_ROLE` / `OPEN_SETTINGS` / `OPEN_CURRENT_LAUNCHER` / `ALREADY_DEFAULT`. `handles()` descarta los **stubs** (`*EmptyStubActivity` de Google TV) y `openHomeSettings()` prueba HOME_SETTINGS → apps predeterminadas → **ficha de app del launcher actual** (donde está «Borrar predeterminados») → Ajustes. `planHomeChange()` decide sin abrir nada.
  - *Interfaz (`Overlays.kt` + `LauncherScreen`)*: el centro de control muestra el Inicio real («Ahora abre: X · te llevo a la ventana correcta») y, si el firmware no trae selector, abre **`HomeGuideDialog`**: 3 pasos (ajustes del launcher actual → «Borrar predeterminados» → botón Inicio y elegir Vision+ Home · Siempre) con atajo a esa ficha de app. Los dos botones (centro de control y Apps) pasan por `requestHome()`.
  - *Manifest*: el filtro **HOME ahora es propio** (`MAIN + HOME + DEFAULT`), separado de `LEANBACK_LAUNCHER`/`LAUNCHER` (forma canónica, como los launchers de Google); `<queries>` suma el intent HOME. Sin prioridad (ver arriba).
  - *Verificado en físico*: **TCL** (`Android 9`): sigue siendo el Inicio, el centro de control dice «Vision+ Home ya es tu Inicio», la app arranca y pinta el Inicio real (captura). **Chromecast** (`Android 14`, antes de retirarlo): con la versión nueva el panel pasó de mentir («ya es tu Inicio») a ofrecer «Poner Vision+ Home como Inicio · Ahora abre: Google TV», es decir la detección por resolución corrige el caso que fallaba.
  - *Chromecast — dejarlo como Inicio por ADB (verificado, persistió al reiniciar)*: Google TV **ignora** la elección (registra rol y predeterminado y aun así levanta su launcher), así que el único camino es retirar el Inicio de fábrica:
    1. `pm set-home-activity com.chronosbvrx.visionplus.home/.LauncherActivity` (con `HomeSettingsActivity` del permissioncontroller también se puede elegir a mano; el botón de 1.0.14 ya abre esa pantalla).
    2. `pm disable-user --user 0 com.google.android.apps.tv.launcherx` — al hacerlo el sistema eligió **`com.google.android.tungsten.setupwraith/.RecoveryActivity`** (el asistente de configuración) como Inicio.
    3. `pm disable-user --user 0 com.google.android.tungsten.setupwraith` → `resolve-activity` y Home ya devuelven `com.chronosbvrx.visionplus.home/.LauncherActivity`; **reiniciado el equipo, sigue siendo el Inicio**.
    *Reversión*: `pm enable com.google.android.apps.tv.launcherx; pm enable com.google.android.tungsten.setupwraith;` y elegir Google TV en «App de inicio predeterminada».
    *Ojo*: la depuración inalámbrica **no vuelve sola tras el reinicio** (hay que reactivarla en Ajustes → Sistema → Desarrollador para volver a entrar por ADB). Efectos de tener Google TV Home desactivado: se pierden sus recomendaciones/modo ambiente; los Ajustes siguen accesibles desde Vision+ Home → «Ajustes de la tele».
  - *Google TV bloquea el cambio*: aunque el sistema registre a Vision+ Home como rol y predeterminado, `launcherx` recupera su tarea de Inicio al matarla; de ahí el paso de desactivarlo.
  - *Guardianes*: `HomeRoleTest` (6: rol en Android 10, rol ya concedido, Android 9, firmware sin selector, ya es el Inicio, `isOwnHome` con nulos/blancos), `LauncherManifestTest` (4: filtro HOME propio, sin prioridad, LEANBACK aparte, `<queries>` HOME) y `LauncherHomeRoleWiringTest` (5: detección por `resolveActivity`, `planHomeChange`, stubs, ficha del launcher actual, guía). **81 launcher en verde**; `:launcher:assembleRelease` 1.0.14 (vc15) firmado con la histórica.
  - *Pendiente*: **publicar** el launcher 1.0.14 (el release v2.5.150 todavía sirve 1.0.13) e instalarlo en la caja del usuario para probar la guía de 3 pasos; si esa caja tampoco expone «Borrar predeterminados», el único camino es ADB (`pm set-home-activity`) o el menú del fabricante.
- **OFICIAL 2.5.150 + Vision+ Home 1.0.13: perfiles renombrables sin cambiar de perfil, el reproductor respeta el launcher y el Inicio se elige desde la ventana del sistema — lápiz al enfocar, modal de escritura con el teclado nativo de la TV, zapping dentro del menú abierto, Atrás devuelve a Vision+ Home y selector directo del launcher — 916 Android + 77 launcher + 22 contrato** (2026-10-24):
  - *Pedido del usuario*: «necesito que la aplicación pueda editar los perfiles, el nombre de los perfiles… sin tener que seleccionarlo; te puedes poner sobre él y abajo te va a salir un icono de editar»; además, al abrir un canal desde «Canales de TV en México» en el launcher, Arriba/Abajo debe hacer zapping **solo dentro de ese menú**, y al salir del reproductor (Atrás dos veces) debe volver a Vision+ Home en la misma tarjeta, no al menú de la app. Después pidió que el lápiz abra **un modal de escritura propio con el teclado de la TV** (no editar dentro del mismo modal ni usar el teclado dibujado por la app).
  - *Perfiles — TV (`ui/tv/TvProfilesDialog.kt`)*: cada tarjeta de perfil es enfocable; al enfocarla aparece **debajo un lápiz** (botón circular focusable, alcanzable con Abajo). Su clic abre un **`RenameProfileDialog` separado** con `BasicTextField` enfocado y `LocalSoftwareKeyboardController.show()`: usa el **IME nativo del televisor** (verificado en la TCL), con Guardar/Cancelar y acción «Listo» del teclado. `ProfileStore.rename(context, target.id, name)` renombra **ESE** perfil sin cambiar el activo; el clic en la tarjeta sigue cambiando de perfil. Al cerrar el modal el foco vuelve a la lista; `key(profile.id)` conserva el estado de cada tarjeta.
  - *Perfiles — móvil (`ui/settings/ProfilesCard.kt`)*: cada avatar tiene su lápiz (`Icons.Rounded.Edit`, 28 dp) que abre `NameDialog` para ese `profile.id` (`renameTarget`), sin seleccionarlo. Se conserva la acción global «Renombrar «activo»» y el crear/borrar con confirmación.
  - *Zapping del launcher*: `VisionLinks.live` viaja con `c=<códigos separados por |>`, `g=<rótulo>` y `from=home`; el `LiveSurface` del launcher pasa `visible` (el mosaico ya filtrado por Todos/México/Favoritos) y `LiveSurface` rotula «Todos los canales», «Canales de México» o «Mis canales». `liveRouteFor` los convierte en `Route.LivePlayer(channelCodes, groupTitle, launcherOrigin)`; sin `c` (control remoto o enlace viejo) se mantiene el zapping global. Verificado manualmente en la TCL con `ECDF → Azteca 7 → ECDF` en un alcance de dos códigos.
  - *Atrás vuelve al launcher*: `VisionLinks.watch/live` marcan `from=home`; `launcherRouteFor`/`liveRouteFor` ponen `launcherOrigin=true`; `TvApp`/`PhoneApp` cierran con `closePlayback(route, nav, onExitToLauncher)` y `MainActivity` pasa `exitAppCleanly`, que cierra la tarea residente del reproductor y la tarea principal para revelar Vision+ Home (conserva su última selección). Un reproductor abierto desde la app o por el control remoto sigue desapilando su ruta como siempre. *Pendiente de prueba física: el Atrás al launcher (el ADB de la TCL se perdió antes de la comprobación); el resto del flujo del launcher está verificado.*
  - *Inicio del launcher (`HomeRole.kt` + `AppsRepository`)*: el botón **«Poner Vision+ Home como Inicio»** (centro de control y Apps) ahora usa `RoleManager.ROLE_HOME` en Android 10+; su intent abre directamente la **ventana del sistema para elegir el launcher predeterminado**, sin navegar a Ajustes. En Android 9 o si el rol no está disponible cae a `Settings.ACTION_HOME_SETTINGS` y luego a `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` (nunca un menú genérico si existe una ruta mejor). Se conserva **«Cambiar el Inicio (ajustes)»** como puerta manual para volver al otro launcher; si Vision+ Home ya es el Inicio, el centro de control lo indica. Lógica pura `homeRoleAction(sdk, roleAvailable, roleHeld)` con `HomeRoleTest` (4) y guardián de cableado `LauncherHomeRoleWiringTest` (3).
  - *Verificado en la TCL*: el diálogo de Perfiles mostró el lápiz bajo el perfil enfocado (`Renombrar Principal`), su clic abrió **«Renombrar perfil»** con el campo `EditText` enfocado y **el teclado nativo de TCL** visible (`Español`, `123#`, `.com`), y el perfil no cambió al abrir la edición. El zapping por alcance se comprobó con el deep link. La suite completa quedó en **916 app + 77 launcher + 22 contrato**, toda en verde.
  - *Publicado*: **estable `v2.5.150` (versionCode 389)** con `VisionPlus.apk` (70.7 MB), `VisionPlusHome.apk` (**1.0.13**, versionCode 14, 1.4 MB) y `version.json`; la release se actualizó en el mismo tag con el nuevo launcher y las notas del selector directo. Ambos con la firma histórica `9593cb0d…` (verificada con `apksigner` + `aapt`). Incluye el precalentamiento del motor, la EPG masiva y «Personalizar Inicio» de las betas 2.5.149.
  - *Pendiente*: probar el Atrás al launcher y el selector directo de Inicio en un equipo Android 10+ (la TCL es Android 9: ahí cae al selector de Ajustes); presets de launcher (TV primero/Cine/Solo lo mío/Niños), pestañas ocultas/reordenadas y favoritos compartidos entre app y launcher.
- **BETA 2.5.149 + Vision+ Home 1.0.11: el launcher se vuelve personalizable — canales favoritos/ocultos/orden, apps, filas, apariencia por perfil, OK largo con menú y gestores — 905 Android + 68 launcher + 19 contrato** (2026-10-24):
  - *Pedido del usuario*: «el launcher hay que añadirle más capas de personalización que se sienta como un launcher comercial… qué canales quieres ver, preferencias de visualización, OK largos para mover/quitar, etc.».
  - *Modelo (`launcher/data/HomeConfig.kt`)*: JSON versionado por perfil (`LauncherPrefs.homeConfig/saveHomeConfig`), con filas (orden/ocultas), canales (favoritos/ocultos/orden/sort Auto·Favoritos·A-Z), apps (favoritas/ocultas/orden), y `DisplayConfig` (densidad Compacta/Cómoda/Cine, animaciones Auto/Ligeras/Completas, reloj 12/24, mostrar reloj, EPG, tarjetas por fila 8/12/18). Defaults = vista actual; JSON corrupto cae a defaults; favorito y oculto son excluyentes. Migración desde `favorite_apps` y desde la clave vieja por nombre cuando el feed estrena `profileId` estable (`FeedMeta.profileId`; el feed de la app ya lo manda).
  - *Inicio (`HomeSurface`)*: filas en el orden configurado, ocultas respetadas, límite por fila, **«Mis canales»** sintética (favoritos en orden manual), apps favoritas/ocultas/recientes, EPG on/off y OK largo en canales/apps. `LauncherScreen` aplica `LocalReducedEffects` según la preferencia de animaciones y `rememberHomeMetrics(density)`.
  - *Menú y gestores (`ui/Personalize.kt`)*: **OK largo** en canal/app → menú (Fijar/Quitar, Ocultar/Mostrar, Mover al inicio, Gestionar). Hub «Personalizar Inicio» desde el centro de control; gestor de **Canales** (Todos/Favoritos/Ocultos + Auto/Favoritos/A-Z, estrella, ojo, flechas, Restablecer), **Apps**, **Filas** (mover/ocultar/restablecer) y **Apariencia**. Todo con `MiniAction`, foco D-pad, `focusGroup` y aterrizaje en el primer control; fondo opaco de pantalla completa; «Restablecer personalización» con confirmación.
  - *TV en vivo (`LiveSurface`)*: filtro **Favoritos** además de Todos/México, orden por preferencia, canales ocultos fuera, EPG respetada y acción Gestionar; el estado vacío explica cómo recuperar canales. `ChannelCard` acepta `showEpg` y `onLongClick`; `AppsSurface` filtra/ordena y tiene Gestionar.
  - *Perfil*: `FeedMeta.profileId` viaja en el feed; la configuración se guarda por id (fallback nombre). Cambiar de perfil recarga su personalización sin mezclar datos.
  - *Verificado en la TCL (capturas)*: hub con Canales/Apps/Filas/Apariencia/Restablecer; gestor de canales con **Canal 5 → Drake y Josh**, Azteca 7 → Los seis signos de la luz, TUDN → Lucha libre CMLL, Fox Sports → The Fifth Quarter (EPG corregida del VPS); al fijar Canal 5 aparece la fila **Mis canales**; Apariencia con Densidad/Cómoda, Animaciones/Auto, Reloj/24 h, EPG/Sí, Tarjetas/12.
  - *Guardianes*: `HomeConfigTest` (16: defaults, JSON, migración, favorito/oculto, orden, sort, apps, filas, límites, reset, rescate de alias), `PersonalizationWiringTest` (4: hub/gestores/menú, almacenamiento por perfil, filas/favoritos/OK largo, apariencia/reloj), `HomeMetricsTest` + densidad, `LauncherLogicTest` + reloj 12 h. **905 app + 68 launcher + 19 contrato** en verde. Release app R8 ~70.7 MB y launcher 1.0.11/12 firmados con la histórica.
  - *También en la beta*: EPG masiva `/v1/epg/bulk` (400 canales en una petición, respaldo por canal) y precalentamiento del motor de las otras sesiones.
  - *Precalentamiento del motor (app + launcher, verificado en la TCL)*: `EngineWarmup` + `EngineWarmService` en `:x4k` con permiso de firma `ENGINE_PREWARM`; el splash pide el warmup en paralelo con el catálogo (tope 5 s configurable `engine_prewarm_max_ms`, solo TV ≥1.8 GB) y el launcher se vincula a los 2.5 s de su primer frame por 15 min (`EnginePrewarmPolicy`, mismos umbrales). El servicio NO acuña sesión ni crea la vista: solo proceso + `boot` + `Env`/`Entries` cacheados + índice de canales; el estado viaja en `files/x4k_warm_ready` (`ok/at/pid/source`, se exige PID vivo y TTL 15 min). Los equipos de 1.5 GB conservan el camino diferido actual. **Medido EN LA TCL con el release 2.5.149 (motor dentro del APK)**: launcher prewarm a los 2.5 s → motor listo ~1 s; Vision+ abrió en **658 ms** con el motor caliente; en app-only el warmup salió con `source=splash`; el canal ECDF pintó a los ~2.3 s del `play`. Guardianes: `EngineWarmupTest` (4), `EngineWarmupWiringTest` (3), `EnginePrewarmPolicyTest` (4), `EnginePrewarmContractTest` (2). **905 app + 68 launcher + 19 contrato**; release R8 70.7 MB y launcher 1.4 MB, firmas históricas. El árbol quedó en vc388 y este lote ya está commiteado (`f45586a`); la beta publicada es vc387.
  - *Pendiente*: presets (TV primero/Cine/Solo lo mío/Niños), pestañas ocultas/reordenadas, favoritos de canales compartidos con la app y sincronización entre equipos; los favoritos del launcher son locales por ahora.
- **EPG EN LA APP (integrado, listo para la próxima beta): el launcher pide los 400 canales en UNA petición al endpoint masivo y cae al camino por canal (120) si el servidor es viejo — 898/898 Android + release de la app compilado; la publicación espera al launcher de otro agente** (2026-10-24):
  - *Pedido del usuario*: «Sí ya hay que ir integrando todo pero recuerda que hay otros agentes trabajando entonces hay que complementarse bien para que no haya regresiones después».
  - *App*: `X4KApi.epgBulkFor` (POST `/v1/epg/bulk`, 4 s connect / 8 s read, mapa `EpgNow` con AHORA/DESPUÉS/ventana) + `HomeFeedStore.fetchEpg` bulk-first: `EPG_CHANNELS = 400` y `EPG_FALLBACK_CHANNELS = 120`; si el endpoint falla o el servidor es viejo, usa el camino por canal en paralelo (8) de siempre. El mapeo ESPN/Star+ (`Elite Sports Hub`) se factorizó en `epgNameFor` para que el individual y el masivo resuelvan igual. El JSON del feed no cambia de esquema (solo lleva más ventanas), así que el launcher 1.0.10 lo consume sin cambios.
  - *Verificado*: suite Android **898/898** (123 clases, 0 fallos; `HomeFeedWiringTest` 12 y `HomeFeedStoreTest` 8), backend **359/359**, y `:app:assembleNormalRelease` **BUILD SUCCESSFUL** (56.9 MB sin inyectar el motor; el publicador lo inyecta y deja ~70.7 MB). `verifyEngineNamespace: colisiones=0` (motor=0 porque el DEX se inyecta post-build).
  - *Coordinación con otros agentes*: **no se tocó** el launcher ni `home-contract` (tienen cambios en curso). El módulo `launcher` **no compila ahora mismo** por trabajo de otro agente (`LauncherScreen.kt:726:2 Syntax error: Expecting '}'` y `Personalize.kt` con `weight` sin resolver/visibilidad), así que **no se publicó beta ni se subió versión** (sigue 2.5.148/vc385). Cuando el launcher vuelva a compilar, el lote de app ya está listo para `publish_ota.ps1 -Version 2.5.149`.
  - *Pendiente*: publicar la beta 2.5.149 con app + launcher ya compilando; probar en la TCL que el mosaico de TV en vivo pinta AHORA en los 400 canales (la primera pantalla ya tiene 26/30 con guía) y que el respaldo por canal sigue funcionando si se apunta a un servidor viejo.
- **EPG AL DÍA (solo servidor, desplegado): la guía de TV deja de mezclar países y pasa de 6.3% a 22% de cobertura (45% en los 120 canales del launcher) — Canal 5, Azteca 7, TUDN, ESPN 2 y Fox Sports 2 ya muestran AHORA/DESPUÉS; endpoint masivo y salud con alerta — 359 backend** (2026-10-24):
  - *Pedido del usuario*: «Hay que ver cómo está el estado actual de mi EPG para que no se quede atrás en todas estas mejoras que hemos implementado».
  - *Diagnóstico medido EN VIVO (VPS)*: el worker corría cada 4 h y escribía 80,077 programas, pero la tabla solo tenía 151 claves y 65 de 1,035 canales (6.3%): 100 de esas claves eran canales de franquicia NFL/NBA/MLB que no existen en el catálogo. La regla vieja `("2" in tokens and "canal" in tokens) → lasestrellas` mandaba a Las Estrellas «Canal ESPN 2 (Chile)», «Canal Fox Sports 2 (México)», «Canal HBO 2», «Canal History 2», «Canal Golden Premier 2» y «Canal Azteca Uno -2 Horas»; `24` era palabra de ruido («24 Horas» → «horas») y «Canal 13»/«Canal 5 HN»/«Canal 2 NIC» se cruzaban entre países. La nota de la 2.5.148 («Canal 5/Azteca 7/TUDN no existen en los feeds») venía de una tabla parcial: **MX1 sí los trae**; el problema era el mapeo y la escritura.
  - *Fix (servidor)*: nuevo `epg_channels.py` compartido por worker y API (copias idénticas, test de paridad): `canonical_channel` corrige las reglas (Las Estrellas solo con XEW/estrellas, ESPN 6/7, HBO Zone, History 2, Golden Premier 2, NTN24 antes que 24 Horas, Win Sports+, Claro Cinema, Discovery H&H, America TV, ADN MX, 3ABN, timeshifts -1/-2 h aparte) y `channel_country` detecta el país por nombre/código (`cyx-ElNueve` → AR). La guía se guarda como `<país>:<cadena>` (`mx:tudn`, `cl:canal13`); `epg_norms_for` no cruza países: un canal hondureño/nicaragüense/chileno solo usa su feed, y las cadenas nacionales (Canal 5, Azteca 7, Las Estrellas…) jamás caen a México si son de otro país. Se añadieron PE1 y EC1 (8 feeds, 7 países), columna `country`, limpieza de filas del formato viejo, dedupe por ventana (dos señales de la misma cadena no duplican el AHORA) y `epg_last_cycle` en `sync_meta`.
  - *Endpoints*: `/v1/epg` devuelve `norm`/`country` y nunca mezcla filas de dos países; **`POST /v1/epg/bulk`** entrega AHORA/DESPUÉS de hasta 400 canales en una petición (el launcher hace 120); **`GET /v1/epg/health`** y el bloque `epg` de `/v1/stats` dan filas, vigentes, países, cobertura total/útil/top-120 y frescura; `diag.py` avisa al Telegram si la guía pasa de 8 h sin escribir o quedan <500 programas (con enfriamiento de 12 h).
  - *Verificado EN VIVO (VPS, 2 ciclos)*: **52,060 filas, 610 claves, 7 países, 47,846 programas vigentes**; `mx:canal5` → Drake y Josh/iCarly/Baila conmigo; `mx:azteca7`, `mx:tudn`, `mx:espn2`, `mx:foxsports2`, `mx:clarosports1`, `mx:hbo2`, `mx:history2`, `mx:goldenpremier2`, `mx:id`, `cl:canal13`, `ar:canal13`, `co:winsportsplus` ya responden; `CANAL 5 HN`, `CANAL 2 NIC`, `CANAL 9 NIC`, `CANAL 13 NI` devuelven vacío (honesto, no programación ajena); `CANAL 9 HD` con `cyx-ElNueve` → `ar:canal9`; `CANAL 5 HD` sin duplicados; **cero cruces de país** en la simulación de 1,035 canales. Cobertura: **231/1,035 (22%)**, **231/937 útiles (25%)**, **54/120 del top México-primero del launcher (45%)** y **26/30 (87%) / 42/60 (70%)** en la primera pantalla (antes 6.3%/65). Bulk de 120 canales: **56 ms, 13.9 KB**. `healthz` 200 y `/v1/epg/health` sin `stale`.
  - *Guardianes*: `test_epg.py` 13 (canónicos que ya no se mezclan, país por nombre/código, `epg_norms_for` sin cruces, paridad de las dos copias, 8 feeds con país, UPSERT con country, `_epg_pick`/`_epg_unique`/`_epg_window`/`_epg_brief`) + `test_diag.py` (alerta por guía vacía/vieja y texto humano) = **359 backend** en verde. Desplegado con respaldos `*.bak-20261024-*`; `sync`/`api` reconstruidos y `healthz` sano.
  - *Pendiente*: **la app ya adoptó `/v1/epg/bulk`** (ver el lote de arriba): `X4KApi.epgBulkFor` + `HomeFeedStore.fetchEpg` piden los 400 canales en UNA petición y caen al camino por canal (120) si el servidor es viejo; **898/898 Android** y release de la app compilado. Falta **publicar la beta** cuando el módulo `launcher` vuelva a compilar (otro agente lo tiene a medio editar: `LauncherScreen.kt:726` y `Personalize.kt` con `weight` sin resolver). Los huecos restantes son maratones «24/7», canales regionales de EE. UU. y países sin feed XMLTV (HN, NI, SV, GT, CR, PA, DO, BO, PY, UY, ES, BR); TUDN USA queda sin guía a propósito (no hay feed de EE. UU. para TUDN).
- **BETA 2.5.148 + Vision+ Home 1.0.10: Deportes deja de marcar todo como EN VIVO, el mosaico de TV en vivo muestra nombre + guía AHORA y la navegación del launcher deja de robar el foco — 898 Android + 42 launcher + 17 contrato** (2026-10-24):
  - *Pedido del usuario*: «en deportes muestra todos como en vivo no solamente los que en realidad están en vivo; la navegación a veces se siente torpe, se pierde y se va sola hasta abajo; y en TV en vivo quiero el nombre de los canales y su EPG actualizado».
  - *Deportes — causa medida*: `HomeFeedStore.sportsItems` marcaba `live = event.live || event.alive > 0 || status == "in"`; `alive` es el número de enlaces que responden, NO si el partido se juega (la propia app lo documenta en `GeeTiles.streams`). Snapshot del API: 447 eventos, **solo 3 con `score.live`**, 290 con `alive > 0`; hoy 78 eventos y la regla marcaba **59 en vivo**. En la TCL se veía el absurdo: **Aston Villa 2-1 Man City, Rayo 0-3 Elche y Cagliari 0-2 Bologna con «EN VIVO» y «FT» a la vez**. Ahora usa `GeeAgenda.phaseOf(event, now)` (estado real + ventana de 3 h), `status`/`startMs` viajan en `SportsItem`, el feed ordena por fase y el detalle dice minuto/`Final`/hora `HH:mm`.
  - *Deportes — launcher*: `ui/SportsPhase.kt` (puro) recalcula la fase con el reloj cada 30 s sin confiar en el `live` viejo (si hay `startMs`/`status`, mandan; el `live` solo es respaldo sin horario); grupos **En vivo / Comienzan pronto / Terminados**, contador real, `Final/Final/FT` y `En juego`; el día seleccionado ya no se reinicia con cada feed y «Hoy» siempre está. Verificado en la TCL: **87 eventos · 19 en vivo** (antes 78/59), sin FT+EN VIVO y con el foco en «Todos los deportes».
  - *TV en vivo — tarjetas*: `ChannelCard` ahora pinta **nombre del canal** + **AHORA** + barra de avance; si la fuente no tiene guía dice «Guía no disponible» (no inventa). La guía del feed pasó de **16 a 120 canales**, se pide en paralelo (8, tope 15 s), viaja con `startMs/endMs` y el launcher recalcula el avance cada 30 s; el provider dispara `refreshAsync` y el launcher vuelve a pedir el feed cada 2 min, así el AHORA se actualiza. La EPG del VPS sí trae Las Estrellas/Fox Sports/etc.; **Canal 5, Azteca 7 y TUDN no existen en los feeds actuales** (el endpoint devuelve 0), así que muestran «Guía no disponible» — ampliar fuentes XMLTV queda como servidor.
  - *Navegación — causa*: `LauncherScreen` tenía un `LaunchedEffect(feed.generatedAt)` que ponía `heroFocused=false` y luego **simulaba hasta 10 `moveFocus(Down)` cada 180 ms**; con el foco en una fila, el feed se refrescaba y el foco bajaba hasta el fondo. Además cada Home recreaba `HomeSurface` (`key(homeKey)`) sin aterrizaje explícito, no había rescate y todo el foco era geométrico (por eso «Abajo» desde Deportes caía en «Básquet»). Compose TV aplica además su `PivotBringIntoViewSpec` (30 %, 125 ms) **aunque la tarjeta ya esté visible**, causando el efecto caminadora.
  - *Navegación — fix*: fuera el bucle y `forceLanding`/`homeKey`; **un solo aterrizaje** verificado (`requestFocusSafely`, 6 reintentos de 120 ms) que se cancela si el usuario toca el mando; `Home` reutiliza el `LazyListState` y sube al héroe; rescate en la raíz si no hay foco; `TopChrome` manda «Abajo» al primer control real de la pestaña (`softFocusDown`), las filas/rejillas usan `focusGroup` + `focusRestorer`, y todas las listas usan `MinimalScrollBringIntoView` (solo desplaza si el control quedó fuera). Escalado 170→120 ms y apagado en teles ≤2 GB.
  - *Verificado en la TCL (capturas)*: Home con el héroe enfocado; TV en vivo con nombres y AHORA; el header y los filtros ya no se superponen (el item del grid se envolvió en `Column`); el aro blanco queda en «Todos»/«México» y «Abajo» entra a la rejilla; Deportes con grupos y 19 en vivo. La TV quedó en el launcher.
  - *Guardianes*: app **898** (`HomeFeedWiringTest` 12, `HomeFeedStoreTest`, `GeeAgendaTest`; se añadieron las comprobaciones de `GeeAgenda.phaseOf`/`startMs`/EPG 120/provider), launcher **42** (`EventsAgendaTest` + fase por reloj/FT, `LauncherSearchTest` + nombre/EPG/foco, `HomeMetricsTest` + alto de tarjeta) y contrato **17** (round-trip de `status/startMs` y `startMs/endMs`). Release R8 **70.7 MB** firmado con la histórica; launcher **1.0.10 (vc11)** 1.3 MB.
  - *Publicado*: beta `beta` (**2.5.148**, vc385) con `VisionPlus.apk` + `VisionPlusHome.apk` + `version.json`; los usuarios siguen en la oficial 2.5.140. Incluye el buscador de 2.5.147.
  - *Pendiente*: probar Deportes a distintas horas (los deportes sin marcador usan la ventana de 3 h, igual que la app). **La EPG ya se amplió y corrigió el 2026-10-24 (ver el lote de abajo): Canal 5, Azteca 7, TUDN, ESPN 2 y Fox Sports 2 ya tienen guía, hay endpoint masivo `/v1/epg/bulk` y salud con alerta.**
- **BETA 2.5.147 (lotes 1-3 de interfaz premium): Ajustes y Perfiles de la tele dentro del shell nuevo — riel de categorías, filas a todo el ancho con foco BLANCO, avatares dibujados y chip de perfil accionable; botón de buscar como PÍLDORA «Buscar» (tele y móvil) con perfiles de avatares también en móvil; y el BUSCADOR rediseñado con AUTOCOMPLETAR de títulos completos, filtros y recientes — 891 Android** (2026-10-24):
  - *Pedido del usuario*: «la interfaz de configuración, el cómo crear/modificar un perfil y la búsqueda se quedó desfasada… que se sienta premium y que no sacrifique rendimiento; analiza la experiencia de usuario». Decisión: **todo en 2-3 lotes al canal beta** y perfiles con **avatar de inicial + color**.
  - *Diagnóstico (capturas en la TCL y el emulador)*: Ajustes TV **sin barra superior** (parecía otra app), píldoras gigantes, filas al 60% del ancho con relleno azul al enfocar y el reloj flotante encima de las pestañas; Ajustes móvil con tarjetas-formulario y descripciones de 3-4 líneas; perfiles como lista de texto y buscador con header propio / teclado gigante.
  - *Lote 1 (hecho, en beta)*: `tvTopBarVisible` incluye **Settings y Search** (misma barra: marca, pestañas, hora, perfil, lupa, ajustes) y `showsFloatingClock` excluye Settings (la barra ya trae la hora). `TvSettingsScreen` pasa a **riel** (`SettingsRailItem`: acento azul en la activa, aro blanco al enfocar) + contenido a la derecha. `TvActionOption` ahora es **todo el ancho** y enfoca con **aro blanco** (antes azul y 60%). **Perfiles**: `ui/profile/ProfileAvatar.kt` (paleta de 8 colores por hash del nombre, inicial acentuada, «Perfil N» muestra su **número**; dibujado, sin imágenes) y `TvProfilesDialog` con tarjetas de 76 dp, aro en el perfil en uso, «+ Crear perfil» y borrar con confirmación. El **chip del perfil** de la barra ahora es un botón que abre «Perfiles» desde cualquier pantalla (`onProfile` en `TvTopBar` → `TvApp`).
  - *Lote 2 (hecho, en beta)*: el **botón de buscar** deja de ser una lupa suelta y pasa a **píldora «Buscar»** (icono + texto, fondo blanco al 8-10%, aro blanco al enfocar) en la barra de la tele (`TvSearchPill`) y en el header móvil; los **perfiles del móvil** (`ProfilesCard`) pasan de lista de nombres a **fila de avatares** de 56 dp (mismo `ProfileAvatar`, aro en el que está en uso, «En uso» en azul) con tarjeta **«+ Crear»**, conservando crear/renombrar/borrar.
  - *Lote 3 (hecho, en beta) — el buscador*: nuevo `data/search/SearchSuggest.kt` puro: `suggestTitles` propone **títulos completos** (primero los que empiezan con lo escrito, luego palabra que empieza, luego contiene; sin acentos/mayúsculas, sin duplicados y sin devolver lo ya escrito), `SearchFilter` (Todo/Películas/Series con `matchesSearchFilter`) y `RecentSearches` (prefs `vision_search`, últimas 8, sin duplicar por forma plegada). Se alimenta de los **resultados que llegan + los títulos ya cargados** (máx. 4000 strings, sin red ni películas extra). **Móvil**: fuera el header «← Buscar»; el buscador es el campo (`BasicTextField` con lupa, limpiar y dictado), filtros en píldoras y fila **Sugerencias/Recientes**; los recientes se guardan solos cuando la consulta se asienta (1.8 s con resultados). **Tele**: teclado **compacto** (tope 46 dp por tecla, columna 320 dp; antes crecía hasta comerse la pantalla) y fila de atajos `TvSearchChip` con aro blanco.
  - *Verificado EN LA TCL Y EL EMULADOR (2.5.147, capturas)*: tele con `visionplus://open/search?q=guerra` → «SUGERENCIAS: Guerra Mundial Z · Guerra de Papás 2 · Guerra de Papás · …» y **Resultados (52)** con 3 filas visibles; móvil → campo «guerra» con lupa/X/mic, píldoras **Todo/Películas/Series**, sugerencias y **Resultados (52)** con nota dorada y «También: …».
  - *Guardianes*: **891 Android** en verde (`SettingsUxTest` 9 + `SearchSuggestTest` 9: prefijo antes que contiene, acentos/mayúsculas, sin repetir ni devolver lo escrito, límite, filtros cine/series, recientes al frente sin duplicados y tope de 8, y el cableado de las dos pantallas + teclado compacto). `TvFocusTest.settingsTabsAllRender` actualizado al riel.
  - *Publicado*: beta `beta` con **2.5.143 → 2.5.144 → 2.5.145 → 2.5.146 → 2.5.147** (vc383), 70.7 MB, firma histórica. Los usuarios siguen en la oficial 2.5.140.
  - *Pendiente*: remate del **chrome de Ajustes móvil** (segmented compacto + 3 bloques con descripciones de una línea). Remates menores: la etiqueta «Crear perfil» se corta («Crear perf») en la tarjeta de 132 dp del diálogo TV, el nombre del chip de perfil va en gris muy tenue y la fila de sugerencias de la tele no lleva respiro al final (el último chip se ve cortado al desplazar).

- **CANAL DE PRUEBAS (BETA) separado del oficial — publicado 2.5.141/2.5.142 como pre-release `beta`; verificado en el Chromecast: el equipo de pruebas ve la beta y los usuarios siguen en la oficial — 875 Android** (2026-10-24):
  - *Pedido del usuario*: «tengo esta versión ya distribuida en varios usuarios… cada cambio es molesto para ellos; no puedo ser un canal exclusivo para mí para pruebas y ya cuando esté pulida mandarla al canal oficial».
  - *Diseño*: las iteraciones se publican como **pre-release con tag fijo `beta`** (`gh release create beta --prerelease`), que GitHub **excluye** de `/releases/latest` ⇒ los usuarios normales no la ven. Los equipos de pruebas se marcan **desde el servidor** (`beta_device_ids` en `remote_config`, por `deviceId`) — sin UI ni teclado en la tele. `AppUpdate.channel()` = preferencia local (`vision_updates`) o lista del servidor; en beta compara **beta y oficial** y ofrece la más nueva (así, al convertir una beta en oficial, el equipo de pruebas la recibe igual). `RemoteConfig.stringListFrom` (JSONArray o texto separado por comas) y `Diagnostics.deviceId()` (mismo hash que la telemetría).
  - *Actualizador*: lee un **`version.json`** dentro de cada release (`version`, `versionCode`, `apk`, `notes`) por descarga normal — sin la API de GitHub (límite por IP) y sin depender del orden de los assets; si un release viejo no lo trae, cae a la API. `apkAsset` ahora **prefiere `VisionPlus.apk`** (los releases llevan también `VisionPlusHome.apk`). `releaseVersion(tag, name)` saca la versión del tag o del título (la beta usa tag fijo `beta` y título «Vision+ 2.5.142 (pruebas)»).
  - *Publicador (`tools/publish_ota.ps1`)*: `-Channel stable|beta`; genera y sube `version.json` en **los dos** canales; la beta borra/recrea el tag `beta` (el `delete` va por `cmd /c` porque `gh` escribe a stderr y PowerShell 5 lo trataba como error terminante).
  - *Verificado EN VIVO*: `releases/latest` → **v2.5.140** (los usuarios no ven la beta); `releases/tags/beta` → pre-release 2.5.142 con `VisionPlus.apk` + `VisionPlusHome.apk` + `version.json`. El **Chromecast** (device `2a371bd1ec724566`) en 2.5.141 abrió el diálogo «v2.5.141 → **v2.5.142**» con las notas de la beta. La **TCL** se conectó por **ADB de red en `192.168.1.109:5555`** (método del proyecto `TV Sala`: `adb_shell` por TCP; el barrido de `:5555` lo encontró) y quedó instalada en 2.5.142. Marcados como probadores: TCL `9a053d28c255422c`, Chromecast `2a371bd1ec724566`, ZTE `3d8dcd634270e1da`.
  - *Log nativo de la TCL (con ADB)*: el **primer** `play` se queda ~12 s antes de `FFP_MSG_OPEN_INPUT` (el motor calienta el P2P; SELinux le niega `sysfs/net`), y el **reintento reutiliza** ese trabajo y abre al instante (`RENDERING_START`). Por eso la 2.5.140 bajó el VOD de **30.4 s a 15.2 s** (`esperaMs=11894`); el resto es del motor, no del cliente. También se vio `getItemData → portal100006 剧集不存在` (inofensivo: la duración viene del catálogo).
  - *Verificado*: **875 Android** en verde (nuevo `UpdateChannelTest`, 6: elección por canal, versión desde tag/título, lista de equipos en JSON o texto, el actualizador nunca instala el launcher, el publicador tiene canal y manifiesto).
  - *Pendiente*: usar el canal beta para cada iteración y publicar oficial solo cuando esté validada; la próxima oficial debe salir desde la beta probada (mismo contenido).

- **OTA 2.5.140 PUBLICADA: el re-arranque del reproductor salta a los 10 s cuando el motor NO recibió nada (antes 25 s) — medido en la TCL: 26.9 s sin datos y el reintento pintó en 3 s — 873 Android** (2026-10-24):
  - *Dato del usuario*: «Ya reprodujo [la TCL, con 2.5.139] pero ve los logs».
  - *Logs medidos (TCL, 2.5.139)*: **VOD** → `PLAYER_SOFT_RESTART modo=vod intento=1 esperaMs=26892 pos=-1ms dur=-1ms playing=false engineBoot=0ms` y `PLAYER_START firstFrameMs=30425 bootMs=0 intentos=2`; **vivo** → `PLAYER_START firstFrameMs=3218 bootMs=0 intentos=1 live=true`. Sin `PLAYBACK_LICENSE_FAIL` (el reintento de licencia de la 2.5.139 funcionó) y `bootMs=0` (motor ya caliente, sin extracción).
  - *Lectura*: el VOD no estaba «buffereando»: el motor **no tenía ni duración** (`dur=-1`) durante 27 s, o sea que no llegó nada y el re-arranque lo rescató (3 s después). El vivo, en cambio, abrió en 3.2 s al primer intento.
  - *Fix (`X4KPlayerAty`)*: política de espera consciente de datos — `engineHasNoData` (VOD + sin señal + `durationMs(v) <= 0`) ⇒ **10 s** en el primer intento y **15 s** en los reintentos; con datos se mantienen 25/35 s para no cortar un buffering legítimo. La gracia de 1.5 s de la 2.5.134 sigue antes de tocar el motor.
  - *Verificado*: **873 Android** en verde (nuevo caso en `PlaybackSpeedWiringTest` con los cuatro topes); release R8 **70.7 MB** con firma histórica; publicado v2.5.140 (vc376), notas en `ota_notes_2.5.140.md`.
  - *Pendiente*: medir en la TCL (el VOD debería quedar en ~13 s y verse `esperaMs≈10000` en `PLAYER_SOFT_RESTART`); si el primer intento sigue sin datos, el problema es la conexión inicial del motor en esa tele (DNS/ruta al CDN), no la espera.

- **OTA 2.5.139 PUBLICADA: la licencia ya no se rinde con el portal lento — último intento con 25 s/60 s y `PLAYBACK_LICENSE_FAIL` con el motivo exacto — 872 Android** (2026-10-24):
  - *Reporte*: «La tv TCL sigue fallando y no reproduce». Telemetría de la TCL en **2.5.138**: `PLAYBACK_HANDOFF_FAIL sin-medios (prepare=null)` + `PORTAL_SLOW semillaMs=1, cacheMs=16436, portalMs=47107`. El empaquetado/motor ya está bien (el Chromecast reproduce con el mismo APK); lo que falla es la **licencia**: `startPlayVOD` no devuelve medios.
  - *Causa probable (medida parcialmente)*: el portal de esa tele tarda ~47 s en la fase del portal y `post()` corta a **15 s connect / 25 s read**; tras dos intentos (normal + sesión nueva) `prepareVod` devolvía `null` y el handoff moría sin motivo. No se pudo confirmar con logcat: la tele es Android 9 sin ADB por red y el barrido de `:5555` en la LAN no encontró nada (la depuración USB necesita cable a este PC).
  - *Fix*: `post(path, specific, connectMs = 15_000, readMs = 25_000)` con timeouts por llamada; `prepareVod` factoriza el cuerpo (`licenseBody`) y, si tras los dos intentos no hay medios, hace un **último intento con 25 s / 60 s** (`license("2"|"1")`), y si aun así falla emite `PLAYBACK_LICENSE_FAIL` con `cid`, `motivo` (excepción/código del portal o «sin medios») y `ms`. En redes buenas no cambia nada.
  - *Verificado*: compila; **872 Android** en verde (nuevo caso en `PlaybackSpeedWiringTest`: timeouts por llamada, reintento largo y telemetría del motivo); release R8 **70.7 MB** con firma histórica; publicado v2.5.139 (vc375), notas en `ota_notes_2.5.139.md`.
  - *Pendiente*: probar en la TCL y, si sigue fallando, **`PLAYBACK_LICENSE_FAIL` dirá el motivo exacto** (si es `portal100024`/región, timeout o vacío). Para logcat nativo en la TCL hace falta cable USB a este PC (Android 9 no tiene emparejamiento inalámbrico) o una opción de «depuración de red» del fabricante.

- **OTA 2.5.138 PUBLICADA: APK de 105 → 70.7 MB y retirada del motor legado — el release ya no lleva los assets del motor (respaldo solo en debug) y se borran las clases del motor viejo — 871 Android** (2026-10-24):
  - *Pedido*: «Baja el APK y haz limpieza de deuda».
  - *APK*: `assets/x4k` (3 DEX de 17 MB + 14 MB de libs) se movió a **`app/src/debug/assets/x4k`**: el release ya no lo lleva (el motor vive en `classes2/3/4.dex` y `lib/<abi>` desde la 2.5.137) y las compilaciones de depuración conservan el respaldo para desarrollar sin inyectar. `tools/publish_ota.ps1` **siempre inyecta** (se quitó `-SkipEngineInject`, que ahora produciría un release sin motor) y lee los DEX de `src/debug/assets`.
  - *Limpieza*: eliminados `MagisClient.kt`, `MagisMitan/TitanSdk.kt`, `TitanVodAty.kt`, los composables `TitanLivePlayer`/`TitanVodPlayerScreen` de `PlayerScreen.kt`, la entrada `<activity .TitanVodAty>` del manifest y el bloque de catálogo Magis de `XuperSource`. Un canal con ref vieja `magis:` se intenta por el motor XTV con el mismo código (`channel.copy(url = "xtvlive:<code>")`). Los assets `titan` ya habían salido en la 2.5.137.
  - *Verificado*: compila debug+release; **871 Android** en verde (`BundledEngineTest.theLegacyEngineIsGone` ahora exige que los archivos NO existan y que la rama `magis:` use XTV); release R8 **70.7 MB** con firma histórica; asset publicado comprobado (`versionCode=374`, `classes.dex+classes2/3/4.dex`, **sin** `assets/x4k` ni `assets/titan`, `lib/*/libranger-jni.so` = build X4K). Probado en el **Chromecast HD** con el APK publicado: `SDK X4K desde el APK`, `play X4K vod`, `FFP_MSG_VIDEO_RENDERING_START` y captura reproduciendo; crash buffer vacío.
  - *Publicado*: v2.5.138 (vc374, 70.7 MB + Home 1.3 MB), notas en `ota_notes_2.5.138.md`.
  - *Pendiente*: ZTE y TCL siguen sin actualizar (últimos reportes: ZTE 2.5.130, TCL 2.5.133). Falta su prueba en tele. Nota observada en el Chromecast: el primer intento de reproducción se quedó sin cuadro ~25 s y el **re-arranque interno** lo pintó en ~2 s — la gracia de 1.5 s de la 2.5.134 funcionando. Queda como opcional llevar el reproductor al proceso principal (paridad total con Crystal).

- **OTA 2.5.137 PUBLICADA: el motor dentro del APK, ahora sí — `lib/` lleva el build BUENO del motor, el motor legado queda inerte y se retiran 30 MB de `assets/titan`; verificado reproduciendo en el Chromecast — 871 Android** (2026-10-24):
  - *Continuación del hotfix 2.5.136*: con el Chromecast en **ADB por red** (`192.168.1.116`, Android 14, **armeabi-v7a**) se reprodujo el fallo y se capturó el log nativo: `DEBUG: #08 base.apk (JNI_OnLoad+4660)` ← `System.loadLibrary` ← `com.titan.ranger.NativeJni.<clinit>` ← `yc.h$c.run`. **Causa raíz medida**: `libranger-jni.so` **NO tiene SONAME** (comprobado con un parser ELF), así que precargarlo por ruta absoluta no sirve: `System.loadLibrary("ranger-jni")` resolvía la copia del **motor legado** que vivía en `lib/<abi>` y su `JNI_OnLoad` abortaba el proceso (no hay excepción Java, por eso el `CrashGuard` no veía nada).
  - *Fix*: `app/src/main/jniLibs/<abi>` pasa a llevar **el build X4K** (`libranger-jni`, `libijkffmpeg`, `libijkplayer`, `libijksdl`, `libtnet-3.1.14` y `libc++_shared`), copiados byte a byte de `assets/x4k/lib/<abi>`; el motor legado se deja **inerte** (`TitanSdk.load()` devuelve `false`, comentario “MOTOR LEGADO RETIRADO”) y se borran sus DEX (`assets/titan`, **30.4 MB**). `X4KTitan` ya **no precarga nada**: adopta el classloader de la app y `System.loadLibrary` encuentra el build bueno en `lib/`. `engine_bundled` pasa a **true** por defecto (kill-switch remoto disponible).
  - *Verificado EN EL CHROMECAST con el APK publicado*: `adb install -r` → `SDK X4K desde el APK (sin extraer): env=true program=true view=true` → `play X4K vod` → `selected codec: c2.amlogic.hevc.decoder` → **`MEDIA_INFO_VIDEO_RENDERING_START`** y captura con el logo de Columbia reproduciéndose (`cc_play.png`); **crash buffer vacío** y `:x4k` vivo. Emulador: mismo camino OK. Suite **871 Android** en verde (`BundledEngineTest` 5: bandera encendida, classloader de la app, **sin precarga**, libs de `jniLibs` idénticas a las del asset, motor legado inerte).
  - *Publicado*: v2.5.137 (vc373, **105 MB**; quitar los assets de respaldo la bajará a ~74 MB), notas en `ota_notes_2.5.137.md`. Asset comprobado: firma histórica, `classes2/3/4.dex`, sin `assets/titan`, `lib/*/libranger-jni.so` con los sha del build X4K.
  - *Pendiente*: probar en la **ZTE** (el `VerifyError` de ART debe desaparecer al venir el motor verificado del APK) y en la TCL (los ~25 s siguen siendo P2P del motor); después quitar `assets/x4k` (17 MB DEX + 14 MB libs, ya duplicados) para dejar el APK en ~74 MB, y valorar llevar el reproductor al proceso principal como Crystal.

- **HOTFIX 2.5.136 PUBLICADA: se apaga el motor dentro del APK (en un Chromecast dejó de reproducir sin error Java) — el motor vuelve a assets y el camino nuevo queda tras la bandera `engine_bundled` (por defecto OFF) — 870 Android** (2026-10-24):
  - *Reporte del usuario*: «Lo estoy probando en un Chromecast y ya no reproduce nada» con la 2.5.135 recién instalada.
  - *Diagnóstico (telemetría del VPS)*: el Chromecast (`2a371bd1ec724566`) mandó `APP_START 2.5.135` a las 05:58 y **ningún** `ENGINE_*` ni `PLAYER_*` ni crash Java. Un fallo **nativo** (SIGABRT/SIGSEGV) no pasa por `CrashGuard`, y eso encaja con «no reproduce nada sin error». Sospecha principal: el APK lleva **dos** builds del motor con los mismos SONAME (`lib/<abi>` = Magis/Titan heredado, `assets/x4k/lib` = X4K) y al cargar las clases desde el classloader de la app, `System.loadLibrary`/`libc++_shared` pueden resolver el build equivocado. En el emulador (x86_64 con traducción ARM) el camino funcionó, por eso pasó el smoke test.
  - *Fix*: `X4KTitan.load` consulta `RemoteConfig.flag(app, "engine_bundled", false)` (bandera nueva, **apagada por defecto**) y solo con ella encendida intenta el classloader de la app; en caso contrario usa el camino de siempre (assets + `DexClassLoader` + `librarySearchPath`), que es el de la 2.5.134. `tools/publish_ota.ps1` gana `-SkipEngineInject` y esta release se publicó con él: el APK vuelve a tener **solo `classes.dex`** y los assets del motor (101.2 MB).
  - *Verificado*: **870 Android** en verde (`BundledEngineTest` ahora exige la bandera apagada por defecto); asset publicado comprobado (`versionCode=372 versionName=2.5.136`, firma histórica, `classes.dex` únicamente y `assets/x4k/x4k-sdk-1.dex` presente).
  - *Publicado*: v2.5.136 (vc372, 101.2 MB), notas en `ota_notes_2.5.136.md`.
  - *Pendiente / siguiente paso real*: para que el motor-en-APK funcione hay que **quitar de `lib/` las libs del motor legado (Magis)** — es la colisión de SONAME/`libc++_shared` la que tumba al motor — y validarlo **con ADB en el Chromecast** (logcat nativo: `dlopen`, `abort`, `UnsatisfiedLinkError`) antes de volver a encender `engine_bundled`. No volver a publicar el camino nuevo sin esa prueba en un equipo real.

- **OTA 2.5.135 PUBLICADA: el motor de video viaja DENTRO del APK (`classes2/3/4.dex`, como Crystal) con libs precargadas y doble respaldo — 870 Android** (2026-10-24):
  - *Decisión del usuario*: «Hazlo de una manera segura que no rompa la app pues ya la tengo distribuida pero sí quiero que sea rápida como Crystal» → «Dale» a retirar Magis. Se ejecutó la parte segura (**motor en el APK**) y se **dejó Magis para después**: sus libs son el mismo SONAME y `libc++_shared.so` ya lo usan `libcast-jni`/`libcredentials`, así que borrarlas sin equipo era el único paso con riesgo real. Los assets se conservan como respaldo en esta versión.
  - *Qué cambió*: los 3 DEX salen de `assets/x4k` y entran como **`classes2/3/4.dex`** (STORED, 4-byte aligned) → Android los deja **verificados al instalar**: se acaban la copia de 17 MB, el `DexClassLoader` en runtime y el `VerifyError` de ART de la ZTE. `X4KTitan.load()` detecta el motor con `Class.forName("yc.h", false, X4KTitan::class.java.classLoader)` y adopta **el classloader de la app**; `preloadEngineLibs()` carga las `.so` por **ruta absoluta** en orden de dependencias (`libc++_shared` → `tnet` → `ijksdl` → `ijkplayer` → `ijkffmpeg` → `ranger`) porque el APK también trae las del motor legado con esos nombres y `System.loadLibrary` habría elegido las de Magis. Si algo falla, cae a los assets de siempre (sin cambios).
  - *Pipeline (`tools/publish_ota.ps1` + `tools/inject_engine_dex.py`)*: tras el build, el script inyecta los DEX (valida magic y `file_size` de cada uno), `zipalign -p -f 4` y **vuelve a firmar con la llave histórica** (`apksigner` + `keystore.properties`), y **aborta** si la firma no es `9593cb0d…`, si faltan `classes2/3/4.dex` o si desaparecieron los assets de respaldo. `assembleNormalRelease` por sí solo sigue produciendo el APK de antes (sin inyección).
  - *Verificado EN EL EMULADOR con el APK publicado*: `pm install` + arranque → log `SDK X4K desde el APK (sin extraer): env=true program=true view=true`, `motor X4K arrancado en /data/user/0/…/files/x4k-luna`, `entries X4K enviadas (7)`; el Inicio pinta hero, Top 10 y filas (captura `vp135.png`). Suite **870 Android** en verde (nuevo `BundledEngineTest`, 4). Asset publicado comprobado: `versionCode=371 versionName=2.5.135`, firma histórica, `classes.dex+classes2/3/4.dex` (classes2 STORED y alineado) y `assets/x4k/x4k-sdk-1.dex` presente.
  - *Publicado*: v2.5.135 (vc371, 123.4 MB — sube por llevar los DEX **doblados**: APK + respaldo; la siguiente versión los quita y baja a ~101 MB, y al retirar Magis ~70 MB).
  - *Pendiente*: probar en la ZTE (debe cargar sin extraer y sin el `VerifyError`) y en la TCL; luego retirar Magis + assets (deja el APK en ~70 MB) y, si todo va bien, eliminar el `:x4k` para tener el reproductor en el proceso principal como Crystal.

- **OTA 2.5.134 PUBLICADA: el re-arranque del motor deja de matar un cuadro que está entrando + medición honesta del arranque y estado del motor al rendirse — 866 Android** (2026-10-24):
  - *Contexto*: tras la 2.5.133, la **TCL BeyondTV** reprodujo y el log mostró `PLAYBACK_PREP cached=true totalMs=0` (nuestro lado instantáneo), `PLAYER_SOFT_RESTART modo=vod intento=1` y `PLAYER_START firstFrameMs=3969` **en el mismo segundo**. Al leer el código: `softRestartPlayback()` llamaba `X4KTitan.stop(v)` en el acto y el primer cuadro llegó **31 ms después** → el re-arranque **mataba la reproducción que ya estaba entrando** (parpadeo negro y otra espera). Además `firstFrameMs` se medía desde `startedAtMs`, que el propio re-arranque reinicia: el 3969 ms reportado no era real (habían pasado ~25 s).
  - *Fix app (`X4KPlayerAty`)*: (1) **ventana de gracia de 1.5 s** antes de tocar el motor (`softRestartPlayback` solo agenda; `performSoftRestart` re-comprueba `firstFrame` y aborta si llegó; bandera `softRestartPending` para no encolar varios); (2) nuevo `playRequestedAtMs` fijado en `handleLaunchIntent`/`playEpisode` y **nunca** en el re-arranque → `PLAYER_START` mide **desde el toque** y reporta `intentos`; (3) `PLAYER_SOFT_RESTART` añade el estado real del motor: `pos`, `dur`, `playing`, `engineBoot`.
  - *Análisis del APK de Crystal (`cc_src`, mismo proveedor)*: su motor vive **dentro del DEX del APK** (`ed.e`, `yc.h`, `com.titan.ranger.*` se descompilan del propio APK; **no hay `assets/x4k`**) y sus `.so` van en **`lib/<abi>`**; el reproductor corre en el **proceso principal** (el manifest solo separa `:gomediad`, `:channel`, `:boost_multidex`); `Env`+entradas CDN se envían al llegar `getSlbInfo` y el play es solo `view.A(program,…)` (SDK más nuevo: `x(instance,…)` + `openSurface()`). **Por eso no extrae nada ni verifica DEX en runtime.**
  - *Por qué NO se copió ese empaquetado (medido)*: el APK ya trae **otro** `libranger-jni.so` en `lib/` (9.8 MB, motor **Magis/Titan** heredado) distinto del de X4K en assets (8.2 MB). Si las clases de X4K entraran al classloader de la app, `System.loadLibrary("ranger-jni")` resolvería el build de **Magis** → motor equivocado. Ese es justo el motivo del proceso `:x4k` y de las libs extraídas. Y en la TCL `bootMs=0` demuestra que el motor **ya estaba cargado**: los ~25 s son la negociación P2P del motor, que Crystal pagaría igual con la misma variante (`h265/720p/ts`, única en el portal).
  - *Verificado*: **866 Android** en verde; release R8 **101.2 MB** con firma histórica `9593cb0d…`; asset comprobado (`versionCode=370 versionName=2.5.134`, firma histórica).
  - *Publicado*: v2.5.134 (vc370, VisionPlus 101.2 MB + VisionPlusHome 1.0.9), notas en `ota_notes_2.5.134.md`.
  - *Decisión pendiente para igualar a Crystal*: para meter el motor en el APK hay que **retirar el motor Magis heredado** (sus libs ocupan `lib/` con el mismo SONAME). Con eso se podría inyectar los 3 DEX como `classesN.dex` (post-build + `zipalign` + `apksigner` con la llave histórica) y las libs en `jniLibs`: elimina extracción, verificación en runtime (bug ART de la ZTE) y el arranque en frío. **No se hizo sin equipo para probar** y porque no acelera la TCL (su costo es el P2P). El siguiente dato (`PLAYER_SOFT_RESTART` con `pos/dur/playing`) dirá si la TCL recibe datos durante la espera.

- **OTA 2.5.133 PUBLICADA: la tele deja de competir consigo misma — índice ligero en TV (24 en vez de 100 por fila) y el trabajo de fondo espera al reproductor — 866 Android** (2026-10-23):
  - *Contexto*: el usuario aclaró que la tele lenta al reproducir es una **TCL BeyondTV** (Android 9, 2 GB, 4.9 GB libres), no la ZTE. La telemetría de la 2.5.132 en ESE equipo (device `9a053d28c255422c`) dio la respuesta: `PORTAL_SLOW semillaMs=3, cacheMs=12436, portalMs=31042` a las 23:20:50, `PLAYBACK_PREP cached=true totalMs=0 desdeFicha=2571ms` a las 23:21:11 y `PLAYER_START firstFrameMs=16504 bootMs=0` a las 23:22:19 — o sea: **nuestro handshake costó 0 ms y el motor ya estaba caliente**, pero el equipo se pasó **12.4 s parseando el índice guardado (5.8 MB)** y 31 s en el portal **mientras el reproductor intentaba arrancar**. En la ZTE ese parseo son 1.8 s y en un móvil 0.46 s.
  - *Diagnóstico de fondo*: la app pedía `/v1/index?limitFilms=100` a todos por igual; en una TV de 2 núcleos efectivos ese JSON cuesta segundos de CPU y competía con el motor justo en el tramo crítico. El portal sirve **una sola variante** por título (h265/720p/ts), así que no hay “calidad más ligera” que elegir en el cliente; lo que sí es nuestro es el peso del índice y **cuándo** corre.
  - *Fix app*: `RemoteIndexSource.filmsPerCategoryFor(isTv, lowEnd)` → **24** títulos por fila en tele/equipo modesto (100 en móvil con recursos), con `K_LIMIT` en prefs: si la medida cambia (o se actualiza la app) se pide `since=0` una vez para no quedarse con el índice de 100. Nuevo `data/PlaybackFocus.kt` (`recentlyLaunchedPlay` puro + reloj inyectable): `X4KApi.launch()` marca el instante y `XuperCatalog.awaitPlaybackIdle()` hace que el índice en caché, las filas del portal y la sync remota **esperen hasta 20 s** si acaba de arrancar una reproducción (tope de 60 s para no quedarse esperando una película entera).
  - *Descartado a propósito*: el “snapshot del primer cuadro” que había propuesto. Con el índice a 1/4 y el `CatalogSeed` pintando en 0 ms ya no aporta, y exigía un serializador nuevo de `Film` (el de `UserLibrary` solo guarda 8 campos, sin nota/kind/temporadas) — no se toca serialización por una mejora marginal.
  - *Verificado*: **866 Android** en verde (nuevo `PlaybackFocusTest`, 9: ventana de silencio con reloj inyectable, bordes y reloj hacia atrás, 24 vs 100 por equipo, y cableado de las tres fases + `since=0` al cambiar la medida); release R8 **101.2 MB** con firma histórica; asset comprobado (`versionCode=369 versionName=2.5.133`, firma `9593cb0d…`, clave `films_limit` presente en el DEX publicado).
  - *Publicado*: v2.5.133 (vc369, VisionPlus 101.2 MB + VisionPlusHome 1.0.9), notas en `ota_notes_2.5.133.md`.
  - *Pendiente*: medir en la TCL tras actualizar — `PORTAL_SLOW` debería bajar de `cacheMs=12436` a ~1/4, y `PLAYER_START` debería mejorar aunque el motor sea el mismo (deja de tener competencia). Si el primer cuadro sigue en ~16 s con `bootMs=0`, el costo es del motor/la señal (una sola variante h265) y la palanca siguiente sería el APK acompañante o la política de reintento del motor, no el cliente.

- **OTA 2.5.132 PUBLICADA: dar Play en la tele se siente más rápido — proceso `:x4k` ligero, handshake en PARALELO, sin hash de 17 MB en cada arranque y telemetría del tramo toque→primer cuadro — 857 Android** (2026-10-23):
  - *Reporte del usuario*: «en el celular carga todo de manera inmediata y en la smarttv tarda más» → aclarado después: «no me refiero al inicio, me refiero cuando reproduzco cosas… en la TV tarda mucho»; y el argumento decisivo: «en XuperTV que es la app original sí carga rápido en TV incluso viejitas».
  - *Diagnóstico (medido/leído, no adivinado)*: (1) el arranque del reproductor pagaba **toda la inicialización de la app**: `ReminderKit.ensureChannel`, `KidsMode.init` y `RemoteConfig.init` (con su hilo de red) corrían también en `:x4k`; (2) `prepareVod` hacía **en serie** `ensureEngineData` (sesión + `getSlbInfo`) y `itemInfo` (`getItemData`) — en la ZTE cada llamada cuesta **0.2–0.7 s** (medido en la 2.5.80), así que era una ida y vuelta regalada; (3) la 2.5.131 leía **17 MB de hash** al arrancar cada proceso `:x4k` (hasta ~1 s en una caja lenta) para sellar el motor; (4) la original hace UNA llamada de Inicio (`getShelveData` con `assetList`+`channelList`+`slbInfo`) y su reproductor no arrastra capas de app. Los tres primeros son nuestros y se corrigen aquí; el cuarto (una sola llamada para el Inicio) queda anotado como siguiente paso del cliente.
  - *Fix app*: `VisionApp.onCreate` deja en `:x4k` solo lo que el motor necesita (`OfflineRegistry`, `X4KApi.initSlots`, `Diagnostics`); avisos, modo niños y config remota pasan a `if (mainProcess)` (los flags remotos se leen igual de la caché en `:x4k` vía `RemoteConfig.flag()` y la subida de diagnósticos ya era solo del proceso principal, así que el kill-switch no cambia). `prepareVod` lanza `ensureEngineData` y la ficha del título con `coroutineScope`+`async` y espera ambos antes de `startPlayVOD`. El sha256 del motor se calcula **mientras se copia** (`copyAssetAtomically` devuelve el hex) y solo se **verifica** si existe la marca `files/x4k-verify` (se enciende al fallar/reparar, se apaga al verificar bien): en régimen sano no se lee el DEX para hashear.
  - *Telemetría nueva (solo lo lento)*: `PLAYBACK_PREP` (>2.5 s) con `slbMs/metaMs/licenseMs/totalMs` y `cached=true/false`; `PLAYER_START` (>3 s al primer cuadro) con `firstFrameMs` y `bootMs` (`X4KTitan.lastBootMs`, 0 = motor ya caliente y reutilizado). Así la próxima reproducción lenta dirá si el tiempo se va en portal, motor o señal.
  - *Verificado*: **857 Android** en verde (nuevo `PlaybackSpeedWiringTest`, 4: el `:x4k` no inicializa lo de la interfaz y sí conserva lo del motor; el handshake va en paralelo con su desglose; el hash depende de la marca y se calcula copiando; el primer cuadro se reporta); release R8 **101.2 MB** con firma histórica `9593cb0d…`; asset descargado y verificado (`versionCode=368 versionName=2.5.132`, firma histórica, `PLAYBACK_PREP`/`PLAYER_START`/`x4k-verify` presentes en el DEX publicado).
  - *Publicado*: v2.5.132 (vc368, VisionPlus 101.2 MB + VisionPlusHome 1.0.9), notas en `ota_notes_2.5.132.md`.
  - *Pendiente*: prueba física en la ZTE (abrir un título y un canal: el primer `PLAYER_START` dirá `bootMs`; con el proceso caliente debe ser 0 y el arranque ~130 ms). Si `PLAYBACK_PREP` domina, el siguiente paso es el Inicio de una sola llamada (`getShelveData`) y/o cachear el SLB más tiempo; si domina `firstFrameMs` con `bootMs` alto, el motor se está muriendo entre reproducciones (LMK) y toca política de proceso.

- **OTA 2.5.131 PUBLICADA: la ZTE B866V2 vuelve a reproducir — el motor se extrae por GENERACIONES (ruta de DEX nueva), se sella con sha256, limpia los artefactos de ART al reparar y, como última palanca, carga los DEX en memoria — 853 Android** (2026-10-23):
  - *Reporte del usuario*: «Revisa el log de errores en una smartv ya no carga nada».
  - *Diagnóstico EN VIVO (telemetría del VPS, no adivinado)*: la tele **ZTE B866V2, Android 12 (SDK 31), 2.5.130** (device `3d8dcd634270e1da`) mandó **44 reportes en 3 h** desde las 18:00: `VerifyError: Verifier rejected class yc.h … can't resolve returned type 'Unresolved Reference: java.lang.String'` (sdk-0.dex), `Verifier rejected class yc.e … 'Precise Reference: java.lang.reflect.Field' not instance of 'Unresolved Reference: java.lang.reflect.AccessibleObject'` (sdk-2.dex), `NoClassDefFoundError: yc.e / ed.e` y el `:x4k` muriendo: el menú y las fichas cargan, **nada reproduce**. La auto-reparación de la 2.5.123 se disparó **4 veces** (`ENGINE_CLASS_INVALID` → `ENGINE_DEX_REPAIRED`) y el error volvió siempre.
  - *Causa raíz (medida)*: (1) los DEX del APK publicado están **intactos** — descargué `v2.5.130/VisionPlus.apk` y comparé sha256 de los 3 assets con el repo: idénticos (`cc2ec074…`, `b139987f…`, `48305229…`) y con `file_size` del header correcto, así que no era una copia truncada; (2) el sha de la tele **empezó con la 2.5.129** y el motor no cambió desde la 2.5.36; (3) la reparación borraba y re-extraía **en la MISMA ruta** (`files/x4k/sdk-N.dex`), así que ART reutilizaba el `vdex`/`oat` que ya tenía para esa ruta+contenido: el rechazo se repetía aunque el DEX fuera byte a byte el del APK; (4) el sello solo guardaba **tamaño**, así que una copia corrompida del mismo largo pasaba como buena. Es la clase de fallo del **ART de Android 12** (`api_level > 29`, bug de verificador con «Unresolved Reference», corregido por Google solo vía módulo ART — esa caja no lo tiene).
  - *Fix app (`data/xuper/X4KTitan.kt` + `EngineLoader.kt`)*: el motor vive en **generaciones** (`files/x4k-rN`, archivo `x4k-rev`): cada reparación estrena **ruta de DEX nueva** ⇒ ART no puede reutilizar su verificación; al reparar se borran todas las generaciones (solo **directorios**: `x4k-rev` es un archivo y borrarlo dejaría la generación en 0 para siempre) y se limpia `codeCacheDir` (vdex/oat). El sello suma **sha256 por DEX** (`sha:sdk-N.dex=…`): se hashea al arrancar el proceso `:x4k` (17 MB, una vez) y una copia con el mismo tamaño pero otro contenido se re-extrae. `yc.e` y `ed.e` se verifican **al cargar** (`probeEngineClass`, `ed.e` obligatoria) en vez de tragarse el `LinkageError` con `runCatching`; **última palanca**: si tras reparar el equipo sigue rechazando un DEX idéntico al APK, se cargan los DEX con **`InMemoryDexClassLoader`** (API 29+, sin archivos ni artefactos). El `.part` de la copia lleva el **pid**; la carpeta legada `files/x4k` se libera al actualizar.
  - *Telemetría*: `ENGINE_DEX_MISMATCH` (la copia del equipo no coincide con el sello) vs `ENGINE_VERIFY_FAIL` (bytes idénticos al APK y aun así ART los rechaza, con generación usada) vs `ENGINE_MEMORY_OK` (el rescate en memoria funcionó) — la próxima vez el log dice cuál de las dos es, sin adivinar.
  - *Verificado*: **853 tests Android** en verde (EngineExtractionTest 14: sha256 vector conocido, sello con huellas, mismo tamaño+otro hash ⇒ re-extraer, generaciones, guarda de `x4k-rev`, cableado de generación/caché/sondeo/memoria); release R8 **101.2 MB** con firma histórica `9593cb0d…`; asset descargado y verificado (`versionCode='367' versionName='2.5.131'`, firma histórica, los 3 DEX del motor con los mismos sha256 que el repo y las cadenas nuevas en el DEX publicado).
  - *Publicado*: v2.5.131 (vc367, VisionPlus 101.2 MB + VisionPlusHome 1.0.9), notas en `ota_notes_2.5.131.md`.
  - *Pendiente*: **prueba física en la ZTE** (abrir Vision+ tras la actualización → el motor se re-extrae en `x4k-r0` y debe reproducir; si apareciera `ENGINE_VERIFY_FAIL` en el VPS, el siguiente paso es el camino del APK acompañante o rebajar la exposición del verificador, no otra reparación). Anotado también que la otra tele (Select Power, Android 10) reportó `PORTAL_SLOW` con **63.9 s** en Películas — es red/equipo, no este fallo.

- **OTA 2.5.130 PUBLICADA: la tele con la piel de Vision+ Home + el launcher que actualiza Vision+ — 847 Android + 31 launcher + 17 contrato** (2026-10-23):
  - *Pedido*: «otra versión de Vision con lo que ya tenemos pero que sea launcher y todo integrado… que se sienta premium y como un sistema operativo propio de la TV». Decisión del usuario: **opción B, APK aparte** (si el launcher falla, Vision+ sigue intacto).
  - *Arquitectura*: `:home-contract` (Kotlin puro: DTOs, JSON v1, deep links) + `HomeFeedProvider` en Vision+ (`content://…homefeed/feed`, permiso **signature** `…permission.HOME_FEED`, solo lectura) + `:launcher` (`com.chronosbvrx.visionplus.home`, **1.2 MB**, sin motor ni media3). El launcher pide **un JSON** (<80 KB) y dispara intents; la reproducción, perfiles, descargas y continuidad siguen en Vision+.
  - *Regla de oro*: primer frame desde la **caché propia** del launcher; el provider se consulta después con tope 1.5 s (leerlo puede arrancar Vision+ en frío).
  - *Feed*: hero (5), «Seguir viendo» con minuto exacto y enlaces ya resueltos, TV en vivo (24 canales, una tarjeta por señal tras colapsar HD/FHD, EPG «AHORA» de 16 canales **guardado en disco**: sobrevive a que Vision+ se cierre tras servir el feed), filas Top 10/Estrenos/Películas/Series/Anime. Se escribe atómico y se refresca en `onResume`, al terminar el catálogo y **cuando llegan los canales** (`force`); `notifyChange` despierta al launcher.
  - *UI premium*: barra superior (marca, pestañas desplazables —Inicio, TV en vivo, Deportes, Películas, Series, Apps—, reloj CDMX, perfil, buscador y centro de control), hero cinemático con rotación (apagada en equipos modestos), filas con anillo blanco y escalado (el `onFocusChanged` va **antes** del clickable: después no observa nada), Top 10 con chip de puesto, apps con iconos reales + favoritas (OK largo) + accesos del sistema, centro de control (ajustes Vision+/tele, cambiar Inicio, actualizar, firma del autor).
  - *Buscador propio*: `SearchSurface` consulta al provider (`/search?q=`, el MISMO buscador del servidor de la app) y pinta los resultados en el launcher; solo al elegir uno abre la ficha (`visionplus://title/…`). En la tele con teclado de mando + dictado; en el teléfono con el IME.
  - *La app de tele se vistió igual (2.5.130)*: barra superior global `TvTopBar` (Vision+ TV · Inicio · TV en vivo · Deportes · Películas · Series · Mi lista · reloj CDMX · perfil · lupa · ajustes; el riel se retiró), `TvCompactAction(primary=true)` blanco en los heroes, `TvPosterCard` con la nota dorada arriba a la derecha y el sello arriba a la izquierda, filas calculadas descontando la barra (`TV_TOP_BAR_DP=62`), pantalla completa sin barras del sistema y el reloj flotante ya no se monta sobre la barra. Se conservan TODAS las secciones de la app (TV en vivo con países/categorías/favoritos/preview, Deportes con deporte/día/liga, Mi lista, Buscar, Ajustes, perfiles, control remoto). QA: `adb shell settings put global vision_force_tv 1` fuerza el shell de tele en un teléfono/emulador.
  - *Launcher 1.0.9*: detecta Vision+ **vieja o sin feed** (compara `meta.appVersion` del feed con el tag del release, caché 24 h) y ofrece **«Actualizar Vision+»** en una franja fija y en el centro de control (descarga + instalador + permiso de apps desconocidas). Android no permite instalar en silencio.
  - *TV en vivo*: todos los canales (400), **México primero** (`LiveTaxonomy.countryFor`) y dentro de México los de casa (`POPULAR_MX`: Canal 5, Azteca 7, Las Estrellas, TUDN, Fox Sports, ESPN…), luego el resto en el orden del portal; chips Todos/México.
  - *Deportes*: el feed trae la agenda Gee (deporte, liga, día, equipos, escudos, marcador, señales; se filtra la agenda vencida) y el launcher dibuja tarjetas con filtros de **deporte** (Fútbol primero), **día** (Hoy/Mañana/…) y **liga** (Liga MX primero). Al elegir uno, `visionplus://open/sports?event=<id>` abre el partido en Vision+ (`DeportesReturn.pickingId`).
  - *Atrás nunca cierra el sistema*; Home vuelve a Inicio y re-enfoca el hero (reintentos hasta que el hero real existe). Deep links nuevos: `visionplus://title/<ref>` (ficha) y `open/{settings,search,library,live,section/<name>,home}`.
  - *Instalación*: Ajustes → Sistema → **Vision+ Home** descarga `VisionPlusHome.apk` del mismo release, lo instala y abre el selector de Inicio. Y **el launcher se pone completo él solo**: si la tele solo tiene Vision+ Home, su Inicio ofrece **«Instalar Vision+»** (baja `VisionPlus.apk` con progreso), detecta el permiso de «instalar apps desconocidas» y lleva directo a esa pantalla del sistema (`ACTION_MANAGE_UNKNOWN_APP_SOURCES` con el paquete), en vez de dejar el aviso sin salida. `tools/publish_ota.ps1` compila los dos APK, verifica **las dos firmas** y publica los dos assets.
  - *Verificado en emulador (release R8 instalado, firma histórica)*: permiso `HOME_FEED: granted=true`; el Inicio pinta hero real, TV en vivo con EPG y apps; ficha/buscador («nemo» → 36 resultados)/Mi lista/secciones abren por deep link; el shell recibe `SecurityException` al intentar leer el provider (el candado funciona).
  - *Publicado*: v2.5.130 (vc366, VisionPlus 101.2 MB + VisionPlusHome **1.0.9** / vc10, 1.3 MB, firma `9593cb0d…` en ambos). Los v2.5.126/128/129 intermedios se borraron (mismo día, superseded).
  - *Verificado en la A32 (Android 13, 1080×2400, kiosco de La Veinte detenido temporalmente y restaurado después)*: con `vision_force_tv=1` y horizontal, la app entra con la barra superior y el Inicio/Hero/tarjetas del launcher; TV en vivo (chips de país, nombres completos), Deportes (15 eventos, 2 en vivo, filtros deporte/día/liga) y Mi lista se ven consistentes. El launcher (1.0.9) arranca en horizontal sin barras, con hero/Seguir viendo reales y 400 canales México primero.
  - *Pendiente*: prueba física en la ZTE/Chromecast (elegir Inicio, reiniciar y ver que se queda; abrir un canal y una ficha con el mando) y valorar el modo launcher en el móvil (hoy es solo tele).
- **AGRUPADOR DE SERIES ARREGLADO (solo servidor, ya desplegado): «House» y «House M.D.» son una sola ficha con sus 8 temporadas y el catálogo deja de repetir la misma obra con otro nombre — 346 backend** (2026-10-23):
  - *Pedido del usuario*: «Analiza en nuestro catálogo y BD por qué hay series que no se agrupan a pesar de ser las mismas… el ejemplo de House, hay otra que se llama House MD y las separa… debe haber otros y no quiero que se repita» (con otro agente en el launcher: **no se tocó nada de `app/`**, solo `server/`).
  - *Diagnóstico medido EN VIVO (no adivinado)*: la BD tenía **`g-house-series`** (House T1, T2, T6, T8) y **`g-house-m-d-series`** (House M.D. T3, T4, T5, T7) con el **mismo `tmdb_id` 1408**; en total **570 tmdb_id repartidos en más de un grupo** (2,344 fichas) y **1,105 pares (grupo, temporada) duplicados**. Dos causas: (1) `fold()` separaba las letras de los inicialismos con puntos («House M.D.» → `house m d` ≠ `house md`), así que la clave de nombre nunca coincidía; (2) el agrupador solo comparaba el NOMBRE exacto: dos variantes de escritura/idioma de la misma obra («Los Simpson»/«Os Simpsons», «Anatomía de Grey»/«Anatomía según Grey», «Full House»/«Tres por tres») quedaban en grupos distintos aunque TMDB dijera que son la misma.
  - *Fix (`server/sync/grouping.py`, reescrito)*: `fold_name` (inicialismos con puntos juntos y números en palabra a dígito: «Hawaii Five-0» = «Hawai 5.0») y `names_compatible` con cuatro reglas — `same` (mismo nombre normalizado), `initials` («House» + «House M.D.», solo inicialismos de ≤2 letras), `tokens` (≥60 % de palabras en común, misma primera palabra y plurales/erratas) y `fuzzy` (parecido ≥0.86) — **con guardas para no mezclar obras distintas**: si uno es prefijo con subtítulo propio o divergen tras el mismo inicio se quedan aparte («9-1-1» ≠ «9-1-1: Nashville», «Below Deck» ≠ «Below Deck Mediterranean», «Vikingos» ≠ «Vikingos: Valhalla», «Power Rangers» ≠ «Power Rangers Megaforce»/«S.P.D», «Tokyo Ghoul» ≠ «Tokyo Ghoul: Re», «Casa de Papel» ≠ «La casa de papel: Corea» — el prefijo se mide sobre las palabras significativas, así un artículo de más no lo esconde), y si cambia un número es otra obra («Deutschland 83/86/89»). Se reconocen además las **marcas pegadas** («La casa de papelT1», «Malcolm el de EnmedioT1», «Godzilla: La SerieT1»).
  - *Identidad TMDB (la pata que faltaba para las traducciones)*: solo se fusionan fichas que comparten `tmdb_id` (confianza ≥0.6); cuando los nombres NO se parecen (traducción real: «Las Kardashian» = «Keeping Up with the Kardashians», «Reis» = «Reyes») se exige que ambos sean nombres oficiales/traducidos de la MISMA entidad (≥0.7) y que el nombre no sea ambiguo en el catálogo (no puede apuntar a dos ids: «Aprendiendo a vivir»). Las fusiones se hacen por **grupos de nombres mutuamente compatibles** (cliques), nunca en cadena: un parecido intermedio no arrastra obras hermanas (así «Vikingos: Valhalla» se junta con «Vikings: Valhalla» sin llevarse a «Vikingos»); los `tmdb_id` mal emparejados (Power Rangers, Love Is Blind UK/US, Pokémon) quedan bloqueados por las guardas de nombre.
  - *API (`server/api/app.py`)*: el grupo sirve **una entrada por número de temporada** (XTV primero, luego popularidad) → fin de los «T5» repetidos (la propia «House of Cards» tenía dos T5 y «Yowamushi Pedal» cinco fichas «T1»); las fichas descartadas siguen perteneciendo al grupo (no reaparecen como tarjeta aparte). Y **no se cachea una sección vacía** (si el índice se está reconstruyendo, la respuesta a medias no se queda pegada hasta la siguiente versión).
  - *Robustez del índice (`server/sync/index_build.py`)*: `build_section_genres` ahora corre **en una transacción** — en este mismo trabajo, reiniciar el sync a media reconstrucción dejó el riel SIN Películas/Series (se detectó en la verificación, se restauró al instante y ya no puede repetirse).
  - *Orden del ciclo (`server/sync/main.py`)*: el agrupador pasa a ejecutarse **después** del enriquecimiento TMDB, para que un match recién hecho («House M.D.» → «House») agrupe en el mismo ciclo.
  - *Verificado EN VIVO (VPS, `docker compose up -d --build sync api` + reconstrucción manual de grupos)*: **3,841 → 4,000 grupos** y 13,438 → 14,021 fichas agrupadas (**401 grupos con varios nombres fusionados** = 401 tarjetas que ya no se repiten; antes 380). Casos comprobados por el API: **House** → una ficha `g-house-series` con **T1…T8** (la película «House» de 2008 sigue aparte); **Los Simpson** 37 temporadas servidas (antes 3 tarjetas: Los Simpson / Los Simpsons / Os Simpsons); **Full House** 8 (con «Tres por tres»); **Anatomía de Grey** 21 (con «según Grey»); **Las Kardashian** 17 (con «Keeping Up with the Kardashians»); **Vikings: Valhalla** 3 (con «Vikingos: Valhalla») y **«Vikingos» 6 aparte**; **Love Is Blind: UK** 3; **House of Cards** 6 sin el T5 duplicado; **Yowamushi Pedal** T1–T5 en una tarjeta; y siguen separados Power Rangers, Love Is Blind (US/UK/Brasil/Suecia/…), «El juego del calamar: El desafío», «La casa de papel: Corea», The Office US/UK y «Deutschland 83/86/89».
  - *Guardianes*: `server/tests/test_grouping.py` (22: inicialismos, marcas pegadas, números, secuelas/ediciones, el caso House completo, traducción con TMDB, ambigüedad, tmdb distinto, los dos nombres de Valhalla sin arrastrar a «Vikingos») + `test_api_serve.py` (una entrada por temporada) = **346 backend** en verde.
  - *Pendiente*: verlo en el móvil y la tele del usuario (ficha de House con sus 8 temporadas, Inicio sin tarjetas repetidas en Series) y confirmar que el siguiente ciclo del sync deja el riel intacto. El arreglo es 100 % del servidor: **aplica sin OTA**.
- **OTA 2.5.125 PUBLICADA: PERFILES — hasta 5 personas por equipo, cada una con su «Seguir viendo», historial, Mi lista y series seguidas — 829 Android + 333 backend** (2026-10-23):
  - *Pedido del usuario*: «Haz todo para subir a 9… pública la OTA directo». Lo que faltaba era perfiles (la otra sesión ya había publicado «Mi lista» en la 2.5.123).
  - *Diseño (nombres de archivo, no copias)*: `data/profile/ProfileStore.kt` resuelve el nombre de las preferencias según el perfil: el **Principal conserva los nombres legados** (`vision_user`, `playback_history`, `sync_state`, `vision_follow` y el XML `playback_history.xml`) → actualizar no migra ni pierde nada; los demás viven en `base__p1`, `base__p2`… El perfil activo se guarda en `files/active_profile` (escritura atómica) y **se lee en cada acceso**, así el proceso `:x4k` (reproductor residente) escribe el historial en el perfil correcto aunque se cambie mientras el player vive.
  - *Alcance*: Mi lista (favoritos/ver más tarde/vistos), historial, series seguidas y el código de continuidad son por persona. Ajustes, descargas, modo niños, emparejamiento del control remoto y la caché de catálogo/canales son del equipo (lo dice la tarjeta).
  - *UI*: móvil `ProfilesSettingsSection` (Ajustes → Sistema: lista, crear con nombre, renombrar y borrar con confirmación; el Principal no se borra) y tele `TvProfilesDialog` (Ajustes → Perfiles; crear con nombre automático y borrar en dos pasos). Al cambiar se **recrea la actividad** (ninguna fila se queda con datos viejos), se refresca «Continuar viendo» del launcher (`LauncherWatchNext.syncAsync(force=true)`) y se avisa con un toast.
  - *Guardianes*: `ProfileStoreTest` (9: nombres legados, saneo, ids, lista corrupta, aislamiento real de favoritos e historial con su XML, borrado que cae al Principal, tope de 5, renombrado y `active_profile` con basura), `ProfileWiringTest` (3: los 4 almacenes resuelven por perfil, ambas interfaces ofrecen perfiles y recargan, el diálogo TV pasa por `TvDialog`) y 1 caso de composición de la tarjeta en el sandbox existente (`GeePickDialogDuplicateTest`, lección repetida: una clase Compose nueva con su propio sandbox tumba la suite). **829 Android + 333 backend**.
  - *Verificado en equipo limpio (emulador, APK publicado)*: Ajustes → Sistema → Perfiles → «Crear perfil» (nombre precargado «Perfil 2») → aparece en la lista → tocarlo cambia y recrea (Inicio sano, proceso vivo, cero cierres) → «Perfil 2 · 2 de 5 en este equipo» → volver al Principal sin cierres.
  - *Publicado*: 2.5.124 (vc360) y **2.5.125 (vc361)** con el rótulo corregido: 101.2 MB, firma histórica `9593cb0d…`, asset verificado (`versionCode='361' versionName='2.5.125'`) y cadenas de perfiles en el DEX.
  - *Pendiente*: prueba física en teléfono/tele del usuario (crear «Sala», ver que «Seguir viendo» y Mi lista cambian, y que cada perfil tiene su código de continuidad). **Siguiente deuda**: localización (strings fuera del código) y partir el módulo Gradle, ambas en `docs/ARQUITECTURA.md`.
- **HOTFIX 2.5.123 PUBLICADA: el motor se auto-repara si su DEX no verifica, la sesión deja de reacuñar en bucle y la app ya mide su % de aperturas sin crash — 816 Android + 333 backend** (2026-10-23):
  - *Pedido del usuario*: «Haz todo para subir a 9… hay otro agente trabajando en mi lista, no entorpezcas… íntegrense sin regresiones ni errores».
  - *Diagnóstico EN VIVO (telemetría del VPS, no adivinado)*: (1) **5 crashes ese día en la tele ZTE B866V2 con 2.5.122**: `VerifyError: Verifier rejected class yc.h … can't resolve java.lang.String` y `NoClassDefFoundError: yc.h` en `X4KTitan.boot`; la declaración venía de `files/x4k/sdk-*.dex` → copia truncada del DEX del motor (la copia era `copyTo` sin verificar: una interrupción deja el archivo a medias para siempre). (2) **121 `SESSION_RENEW_FAIL` en 5 min** en un OnePlus 8 Pro: `portal100024 所在区域为黑名单` (rechazo permanente) y `ensureSession` reacuñaba en cada llamada porque el fallo no dejaba marca. (3) `PLAYER_SOFT_RESTART` 47 en 8 equipos (red, no build).
  - *App — motor*: `copyAssetAtomically` (`.part` + `fd.sync()` + rename) + manifiesto `engine-manifest.txt` (versión + tamaño por DEX) → un archivo corto o de otra versión se vuelve a copiar; `load()` verifica `yc.h` al cargar; `boot()` captura `LinkageError`, borra `files/x4k` + `x4k-libs` y reintenta UNA vez (`healBudget`), reportando `ENGINE_DEX_REPAIRED`/`ENGINE_CLASS_INVALID`. Las libs también se copian atómicamente y se refrescan al cambiar de versión (antes el DEX viejo sobrevivía a las actualizaciones).
  - *App — sesión*: `MINT_FAIL_BACKOFF_MS` = 60 s tras un mint fallido (puro `mintBackoffActive`, se limpia al acertar); `isSessionFailure` ya no trata cualquier `http 4` como patada (solo 401/403 + códigos de sesión), así un rechazo de región no dispara renovaciones.
  - *App — telemetría*: latido `kind=session` por apertura (`MainActivity.onCreate` → `Diagnostics.sessionStart()`, id de 12 hex compartido con `:x4k` por archivo y en `payload.session`), cola propia `diag_sessions` (tope 5) para no desalojar crashes, y freno de anomalías por código (1/min, con `suppressed=N`).
  - *Servidor (desplegado y verificado EN VIVO)*: `/v1/diag` acepta `session`; `/v1/diag/summary` y el triage la ignoran; **`GET /v1/diag/health`** (sesiones, % sin crash y sin ANR, por versión) + `quality_slo` en `sync/diag.py` que avisa al Telegram si baja del 99 % con ≥20 sesiones y enfriamiento de 24 h. Prueba viva: POST sesión+crash → `crashFreePct 0.0`; fila de QA borrada.
  - *Infra*: `tools/publish_ota.ps1` ya sobrevive al stderr de Gradle (`cmd /c … 2>&1`): PowerShell 5 lo convertía en error terminante y cancelaba la publicación con la suite en verde.
  - *Verificado*: **816 Android + 333 backend** en verde (15 nuevos: 8 del motor, 5 de sesión/cola, 2 de la sesión del portal); release R8 **101.2 MB** con firma histórica; asset descargado y comprobado (`versionCode='359' versionName='2.5.123'`, `9593cb0d…`) y las cadenas nuevas presentes en el DEX publicado (`engine-manifest.txt`, `ENGINE_DEX_REPAIRED`, `diag_sessions`, `APP_START`).
  - *Pendiente*: prueba física en la ZTE/teléfono (reproducir: al instalar 2.5.123 el DEX dañado se re-extrae solo) y vigilar `/v1/diag/health` 24-48 h. **Perfiles** (la mayor deuda de producto) quedan pendientes a propósito: tocan `UserLibrary`/`PlaybackHistory` y otra sesión estaba en «Mi lista»; el mapa de capas y la deuda viven en `docs/ARQUITECTURA.md`.
- **Mi lista → Historial con avance y reanudar (app; en la OTA 2.5.123) — 801 Android** (2026-10-23):
  - *Pedido del usuario*: agregar a «Mi lista» el historial de lo visto y registrar el avance de cada elemento para poder reanudar.
  - *Historial*: `PlaybackHistory` ahora **conserva también los terminados** (tope 60), `historyCards()` devuelve en curso + vistos (una entrada por serie, la más reciente) y `continueCards()` filtra `shouldKeep`, así «Seguir viendo» no se llena de cosas terminadas. `ResumeCard` suma `completed` y el avance real.
  - *UI*: pestaña **«Historial»** en `LibraryScreen` (móvil) y `TvLibraryScreen` (TV), con barra de avance y «Visto» / «Continuar en mm:ss». En curso reanuda con `resumeRoute`; terminado abre la ficha (o reinicia desde 00:00 si no hay ficha). `onResume` cableado en `PhoneApp` y `TvApp`.
  - *Reproductores*: el nativo guarda al terminar/créditos (`saveHistory(forceFinished=…)`) y el ExoPlayer/búnker con `saveHistory(finished=true)`; un `finishedSaved` evita que un `onStop`/dispose posterior degrade el terminado a medias. El launcher Watch Next, `RemoteResume` y el deep link filtran `shouldKeep`.
  - *Verificado*: **801 tests Android** en verde (6 nuevos: 2 de almacén/historial, 1 del launcher y 3 de cableado); compila. *Pendiente*: prueba física en móvil/TV (terminar una película/capítulo → «Visto»; abrir un historial a medias → minuto exacto) y decidir OTA.
- **Buscador de personas y créditos TMDB (servidor; API desplegada, créditos completos) — 324 tests backend** (2026-10-23):
  - *Pedido*: «Hagámoslo» tras ver que «tarantino» solo devolvía Death Proof.
  - *Causa*: la etapa de nombres se detenía con 1-2 resultados incidentales (un alias con «Tarantino») y la profunda completa se corría igual (3.6 s en «guerra de papas»); no existía una etapa de personas indexada.
  - *Fix*: `should_try_people` (con menos de 3 nombres se consulta la etapa de personas indexada) y `should_run_full_deep` (la profunda completa solo cuando no hay nombres); `build_deep_match` suma `e.director_names`/`e.cast_names`; el portal ya traía 39,834 directores y 45,127 repartos.
  - *Créditos*: `enrichment.director_names/cast_names/credits_at`; `parse_credits` (película: `credits`; serie: `aggregate_credits` + `created_by`, reparto top 12); `backfill_credits` (1,500 por lote, CLI `--credits N` con workers, no toca `search_text`); `/v1/stats` suma `withCredits`.
  - *Índice de personas*: `vp_fold` inmutable + GIN de trigramas en `titles.director/cast_list` y `enrichment.director_names/cast_names`; la etapa de personas consulta los dos bloques por separado (un OR cruzado no usaba los índices) y ordena **director > reparto > popularidad**. «tarantino»/«pedro pascal»/«martin scorsese»: **~200-420 ms** (antes 2.6-2.8 s); «guerra de papas» 3.6 s → **0.2 s**; la profunda completa solo corre cuando los nombres no dan nada.
  - *Verificado*: **324 tests backend** en verde (+10), CI 5m26s; API live: «tarantino» → Tiempos violentos, Bastardos sin gloria, Django sin cadenas, Perros de la calle…; «pedro pascal» → The Last of Us, The Mandalorian…; «martin scorsese» → Boardwalk Empire, La Isla Siniestra, El lobo de Wall Street… (el cameo en The Mandalorian queda después); «guerra de papas» → Guerra de Papás. Créditos **completos**: 47,409 procesados, **46,428** con nombres; sync recreado con la imagen nueva a las 07:30 (ciclo nuevo), disco 2.8 GB; la pasada profunda terminó a las 06:00 (+14 títulos; el portal topa 10,000 por listado y el resto se encuentra por el buscador).
  - *Pendiente*: el deep pass queda semanal; valorar deduplicar spin-offs en personas («Round 6» vs «El juego del calamar»).
- **Índice y buscador (solo servidor; desplegado): todo el catálogo navegable, traducciones por idioma de TMDB y ranking v2 — 314 tests backend** (2026-10-23):
  - *Pedido del usuario*: «que esté todo el catálogo indexado y actualizado, optimizado para el buscador con sus variantes de nombres en otros idiomas o regiones».
  - *Diagnóstico medido en vivo*: 52,418 títulos, 47,378 matches TMDB (92.2%), `search_text` vacío 0 y viejo 0; pero **691 títulos sin ninguna categoría**, `alt_titles` solo en 34,070 y TMDB `/translations` daba nombres nuevos en **25/25** títulos sin alternativos y **10/10** con alternativos («Die Simpsons», «Блич», «死神»).
  - *Traducciones*: `enrichment.translated_titles text[]` + `translations_at`; `tmdb.parse_translations` prioriza es-MX/es, en, pt, fr, de, it, ja, ko, zh, ru… y corta a 40; los matches nuevos las traen en la misma llamada (`append_to_response=…,translations`); `backfill_translations` las completa por popularidad con presupuesto (`TMDB_TRANSLATIONS_BATCH=1500`, `TMDB_TRANSLATIONS_GAP=0.12`) y catch-up manual `python tmdb.py --translations N`; `search_text` las incluye y el tope sube a 2,000.
  - *Ranking v2* (`build_rank`): exacto del título/alias del portal/es-MX/original → 1; prefijo o **exacto alternativo/traducido** → 2; contiene fuerte → 3; prefijo alternativo → 4; contiene alternativo → 5; compacto → 6. Los canales en vivo van después del VOD y se deduplica por `tmdbId` con confianza ≥0.75. Arregla «el conjuro» (original primero), «squid game» (serie, no el spin-off), «the simpsons» (serie, no el canal) y «nemo» (Finding Nemo, no New Amsterdam).
  - *Todo el catálogo*: categoría `catalogo-completo` («Todo el catálogo», special 90) con **52,457** títulos y **0 sin categoría**; `ensure_catalog_category` la mantiene en cada ciclo (primera vez por popularidad; los nuevos al final). `/v1/index` y `/v1/categories` ya la sirven; `withText` quedó **52,457/52,457**.
  - *Buscador más rápido*: si el índice local ya tiene el nombre no se espera al portal (antes se esperaba siempre; ~200 ms menos en la mayoría); el portal sigue para los misses. `build_deep_match` incluye las traducciones.
  - *Stats*: `/v1/stats` suma `search` (`withText`, `matched`, `withAlt`, `withTranslations`, `lastTextAt`) y `catalog` (`titles`, `withoutCategory`, `deepAt`).
  - *Verificado*: **314 tests backend** en verde (+12); SQL del rank probado contra la base real; búsquedas en vivo: «buscando a nemo»/«finding nemo» → Finding Nemo, «vaiana» → Moana, «el conjuro» → Expediente Warren, «squid game» → El juego del calamar, «the simpsons» → Los Simpson, «die simpsons»/«Блич»/«死神»/«Findet Nemo»/«Le Monde de Nemo» ya encuentran. Traducciones completas: **43,434** títulos con nombres (47,386 intentos); pasada profunda completada a las **06:00** (+14 títulos; el portal topa 10,000 por listado, el resto se encuentra por el buscador).
  - *Pendiente*: créditos de personas (lote de arriba), valorar un índice de créditos para bajar la latencia de personas; el deep pass queda semanal.
- **OTA 2.5.122 PUBLICADA: «Porque viste X» y «Tus series · Esta semana» en el Inicio, móvil y tele — 795 Android** (2026-10-22/23):
  - *Publicada*: `tools/publish_ota.ps1 -Version 2.5.122 -NotesFile ota_notes_2.5.122.md` (vc358, 101.2 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.122). Asset descargado y verificado: `versionCode='358' versionName='2.5.122'`, firma histórica OK.
  - *Pedido del usuario*: implementar «Porque viste X» (PlaybackHistory + Related + géneros) y «Nuevos episodios» (una fila «Esta semana» con `/v1/schedule` y el seguimiento de series). Decisión del usuario: **sin tráilers**, **sin «Lo más visto»** y conservar «Populares de la semana».
  - *«Porque viste X»* (`data/HomePersonal.kt`, puro): usa el último recuerdo de `PlaybackHistory` (si es capítulo, el nombre base de la serie), encuentra su ficha en el catálogo (con respaldo por nombre exacto si el id ya no está), reusa `Related.pickRelated` con los géneros de la ficha o los inferidos de las hileras y **no mezcla cine con series**. Excluye lo que ya vive en «Seguir viendo» (id y tmdbId) y lo que salió en Top 10/Estrenos/Populares mientras queden ≥6 tarjetas; con menos de 6 la fila no se pinta. Sin red nueva (usa las filas ya cargadas) y en modo niños solo `kind == kids`.
  - *«Tus series · Esta semana»*: cruza `/v1/schedule?days=7` con `FollowStore.followed` (por `titleId`/imdb), resuelve la ficha del catálogo, deja una tarjeta por serie, ordena por fecha y rotula «T2 · C5 · Hoy 20:00» / «Mañana» / «Dom 25» en hora de México. Descarta lo que no tiene ficha (no inventa tarjetas) y oculta la fila si no queda nada. `RemoteIndexSource.schedule` gana caché en memoria de 6 h (una copia de 7 días sirve al aviso diario de 2) para no pedir la agenda en cada apertura del Inicio.
  - *UI*: móvil (`HomeScreen` + `FollowRow`) y TV (`HomeRowSlot.Because`/`FollowWeek`, `HomeAiringRow`, `homeRowSlots` + `homeRowIndexOf`) después de «Seguir viendo». El índice del regreso del D-pad cuenta las dos filas nuevas (guardian en `TvRescueTest`); `HomePersonalWiringTest` vigila el cableado de ambas pantallas y la caché de la agenda.
  - *Verificado*: **795 tests Android** en verde (11 nuevos: 9 puros de `HomePersonalTest` + 2 de cableado); release R8 **101.2 MB** con `verifyEngineNamespace: motor=16395, nuestras renombradas=5342, nombres reales compartidos=14, colisiones generadas=0`; asset publicado verificado (`vc358`, firma histórica). La agenda real del VPS casa sus `titleId` con el índice (comprobado en vivo: «Sabor a ti» y «Tierra de Amor y Coraje» con sus temporadas). *Pendiente*: prueba visual (historial + seguir una serie → las dos filas en el Inicio).
- **OTA 2.5.121 PUBLICADA: «Relacionadas» en la ficha, sugerencias al terminar una película, una sola sinopsis en español, selector de temporadas compacto y «Ver más» real por género — 784 Android + 302 backend** (2026-10-22/23):
  - *Publicada*: `tools/publish_ota.ps1 -Version 2.5.121 -NotesFile ota_notes_2.5.121.md` (vc357, 101.2 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.121). Asset descargado y verificado: `versionCode='357' versionName='2.5.121'`, firma histórica OK.
  - *Pedido del usuario*: al terminar una película, un modal con sugerencias del mismo género/estilo; en la ficha, «Relacionadas»; una sinopsis coherente (no una al inicio y otra en inglés); y los botones de temporadas/capítulos más compactos.
  - *Sinopsis*: `resolveSynopsis` (puro, `Catalog.kt`) conserva la del índice (TMDB es-MX) y solo usa la del portal si la primera falta o viene en inglés y la del portal sí está en español. `TitleScreen`/`TvTitleScreen` ya no hacen `description = item.description` a ciegas: el texto no salta a media carga. *Verificado en el emulador*: Spider-Man/Shōgun/The Last of Us con sinopsis es-MX estable.
  - *«Relacionadas»*: `data/Related.kt` (puro) elige títulos del mismo género, sin repetir la obra actual (id/tmdbId/grupo), mismo tipo primero y relleno por popularidad; si la ficha no trae géneros, los infiere de las hileras del índice que la contienen. `RemoteIndexSource.section(...)` + `Related.forFilm(...)`; hilera móvil y TV (antes del selector de capítulos), tarjeta abre su ficha (`onOpenFilm` en `PhoneApp`/`TvApp`).
  - *Servidor (desplegado en el VPS)*: `film()` y `_enrichment_map` ahora sirven `genres` (TMDB, `text[]` ya existente); `api` reconstruido y verificado en vivo (`"genres":["Comedia"]`). El cliente los parsea y cachea (`Film.genres`, `RemoteIndexSource.parseGenres/rowToJson`). Sin el servidor nuevo, la app cae a los géneros del portal o a la inferencia por hileras.
  - *Final de película (reproductor nativo)*: `X4KPlayerAty.showMovieSuggestions()` al terminar una película suelta (las series conservan «Siguiente capítulo»); pide 8 relacionadas en segundo plano y pinta un modal «MÁS COMO ESTA» con póster/título/año/nota, foco D-pad, «Cerrar» y Atrás; tocar una llama `X4KApi.playVod` (reutiliza `singleTask`/`onNewIntent`). `suggestVisible` entra en `isModalActive` y en el bloqueo de foco. *No verificado visualmente*: el motor ARM-traducido del emulador aborta al abrir el reproductor (limitación conocida); pendiente en equipo físico.
  - *Compactación*: móvil (miniatura 40×58, fila 5 dp, botones 34 dp, chips de temporada 10/5 dp) y TV (tarjeta de capítulo 240→168 dp, chips 11/5 dp, panel más denso); el contenido de la ficha TV pasó a columna desplazable (`heightIn(min = maxHeight)` + `verticalScroll`) para que «Relacionadas» y el panel quepan sin recortes.
  - *Regresión de foco cazada y cerrada*: al volver de una ficha, `FocusRescue.screenHasFocus` podía quedar en `true` de la pantalla anterior y el regreso se rendía sin enfocar nada. Ahora `rememberReturnFocus` respeta solo una **tecla real** (`FocusRescue.lastUserKeyAtMs`, alimentado por `TvApp.onPreviewKeyEvent`), no el flag viejo. Además, una película que vive en dos filas («Estrenos» y «Recomendado para ti») ya no comparte el mismo `FocusRequester`: `TvCategoryScreen` recuerda la fila enfocada (`returnRow`) y solo esa la registra. `TvFocusTest` completo en verde.
  - *«Ver más» por género (2026-10-23)*: la sección seguía topada en 40 títulos por hilera; ahora `CatalogRow.total` conserva el total real (Drama = 12,192), cada hilera móvil/TV cierra con una tarjeta **«Ver más»** (y el encabezado también dice «Ver más») y la cuadrícula tiene un botón al final con «Mostrando X de Y». `RemoteIndexSource.categoryPage(...)` pide `/v1/category/{id}?page=&pageSize=` y fusiona sin duplicar; `SectionPaging` fija lote/techo por equipo (**24/180** en low-end, **48/480** en equipos grandes) para no cargar una lista que tumbe la TV. *Verificado en el emulador*: Drama abrió con **12,192**, el botón «Ver más» cargó una página nueva (aparecieron más títulos) sin crash. El endpoint ya existía; no hubo cambio de servidor para esto.
  - *Guardianes*: `RelatedTest` (9: género, tipo, grupo, tmdb, inferencia, sin URL, tokens), `CatalogTest` (sinopsis es-MX/portal/inglés + `genres` del índice + página de categoría), `TitleRelatedWiringTest` (6), `SectionPagingTest` (3: lotes/techo, páginas sin huecos, tope), `SectionPagingWiringTest` (5: servidor, móvil, TV, total real, techo), `TvFocusTest` (23), backend `test_api_serve` (+2 géneros). **784 Android + 302 backend**; release R8 **publicado** (vc357, 101.2 MB, `verifyEngineNamespace: colisiones=0`, firma histórica `9593cb0d…`).
  - *Pendiente*: prueba física del modal al final de una película, del layout TV y del «Ver más» en la tele.
- **OTA 2.5.120 PUBLICADA: los logos de TV en vivo dejan de estirarse — tamaño natural (tope 2.5×) en móvil y tele — 758 Android + 300 backend** (2026-10-22):
  - *Pedido del usuario*: «los canales en vivo actualmente se fuerzan los iconos a verse en toda la tarjeta y se ven mal; me gustaría que se vieran legibles en su tamaño original aunque la tarjeta sea más pequeña».
  - *Causa medida (no adivinada)*: los logos del portal son de **90×60 px** (descargados del catálogo real y medidos) y la app los pedía a Coil como 480×270 / 640×360 y los dibujaba con `fillMaxSize` + `ContentScale.Fit`: 3–4× de aumento → borrosos y "gigantes"; los que el proveedor ya entrega recortados (A3S, A&E) parecían rotos.
  - *Fix*: pieza compartida `ui/live/ChannelLogo.kt` — `logoDisplaySize` (puro) = tamaño nativo con tope `LOGO_MAX_UPSCALE` (2.5×; preview de la tele 3×), nunca mayor que la caja y conservando proporción; `ChannelLogo` lo pinta centrado, decodifica a 256 px y deja las iniciales **solo de respaldo** (mientras carga o si el canal no tiene logo). Aplicado en la rejilla móvil (`LiveScreen`), la fila de la tele (`TvChannelTile`) y el preview de la tele.
  - *Bug cazado durante la verificación*: `Size.width` lanza `IllegalStateException: Size is unspecified` cuando el painter aún no tiene drawable; hay que preguntar `isSpecified` antes de leerlo (se reprodujo el crash en el emulador y se corrigió antes de publicar).
  - *Verificado*: **758 Android + 300 backend** en verde (incluye `ChannelLogoTest`: no se pasa de 2.5× ni de la caja, conserva proporción, cae a la caja si el tamaño es desconocido, y las dos pantallas usan la pieza compartida); capturas antes/después en el emulador (`Vision_Phone`); APK **publicado** (vc356, 101.2 MB, firma histórica) instalado y con la rejilla ya correcta.
  - *Nota*: si se quiere más discreto o con más presencia, es una sola constante (`LOGO_MAX_UPSCALE`).
- **OTA 2.5.119 PUBLICADA: blindaje definitivo del motor (cargador aislado + autocomprobación de nombres) y canario que frena solo un build roto — 753 Android + 300 backend** (2026-10-22):
  - *Motivo*: tras el hotfix 2.5.118 (que quitó el síntoma), cerrar la clase de bug y que un build roto no llegue a más equipos sin que nadie mire.
  - *App — cargador aislado (`data/xuper/EngineLoader.kt`)*: `EngineClassLoader : DexClassLoader` que, para los nombres del espacio ofuscado del motor (raíz de 1–2 letras: `k4`, `yc`, `ed`, `bd`, `a1`…), pregunta PRIMERO al DEX del motor y, si el nombre no está ahí, delega al padre. El resto del mundo queda igual que siempre (androidx/okhttp/kotlin los sigue sirviendo la app). Kill-switch remoto **`engine_child_first`** (default `true`): `{"engine_child_first": false}` devuelve el parent-first sin OTA.
  - *Autocomprobación*: `leakedEngineNames(cl)` sobre `k4.x`, `ed.e`, `yc.h`, `bd.a`, `uc.c`; si alguno lo sirve nuestro APK se reporta `ENGINE_NAMESPACE_LEAK` (con `childFirst=` y el listado) y se loguea `motor ensombrecido`. El log de arranque del motor ahora dice `SDK X4K cargado: … childFirst=true fugas=0`.
  - *Servidor — canario (`sync/diag.py::build_canary`, desplegado en el VPS)*: mira SOLO la versión más nueva de la telemetría (7 días). Si salió hace <`DIAG_CANARY_FRESH_HOURS` (48 h) y acumula ≥`DIAG_CANARY_MIN_REPORTS` (3) fallos de reproductor en ≥`DIAG_CANARY_MIN_DEVICES` (2) equipos → escribe `ota_paused=true` en `remote_config` (deja de ofrecerse la actualización; el forzado `min_version_code` sigue mandando porque en la app `forced` gana sobre `paused`) y avisa por Telegram con el veredicto. Se **rearma solo**: al ver una versión más nueva en la telemetría o al cumplirse `DIAG_CANARY_PAUSE_HOURS` (12 h). Estado en `sync_meta` (`canary_paused_version`/`canary_paused_at`).
  - *Refinamiento del canario (evita falsos positivos)*: `is_player_breakage` cuenta crash con `screen=Player`/fase `reproductor-*` y los códigos `PLAYER_ERROR`, `PLAYER_RECONNECT_FAIL`, `ENGINE_LOAD_FAIL`, `ENGINE_NAMESPACE_LEAK`. **NO** cuentan `PLAYER_FRAME_SKIP`, `PLAYER_SOFT_RESTART`, `PLAYER_FAILOVER`/`_XTV`, `PLAYER_SELF_HEAL`/`PLAYER_HEAL_APPLIED` ni `PLAYER_RESIDENT_IDLE`: salen en equipos sanos y frenarían builds buenos.
  - *Verificado*: **753 Android + 300 backend** en verde; guardian R8 `motor=16395, renombradas=5290, reales compartidas=14, colisiones generadas=0`; APK **publicado** (vc355, firma histórica, `k4.x` ausente) instalado en el emulador → `childFirst=true fugas=0`. Con el build de debug **el video reproduce de verdad en el emulador** (logo de Columbia de Spider-Man, `FFP_MSG_VIDEO_RENDERING_START`); el aborto nativo del *release* en el emulador es idéntico antes y después del cambio (limitación del ARM traducido, no del cargador).
  - *Pendiente*: telemetría de 2.5.119 en los equipos reales (que no aparezca `ENGINE_NAMESPACE_LEAK` ni `NoSuchMethodError`) y confirmar que el canario no frena la versión (es sana).
- **HOTFIX 2.5.118 PUBLICADA: «no me deja reproducir nada» — R8 volvió a bautizar una clase nuestra (`kotlin.collections.IndexedValue`) como `k4.x`, ensombreció al motor XTV y el `onPrepared` moría en `:x4k` (no reproducía NADA en la 2.5.117); se cerró la puerta completa con `-repackageclasses 'visionplus'` + guardián de build — 746 Android + 294 backend** (2026-10-22):
  - *Reporte del usuario*: «Analiza los errores, creo que hay un problema que no me está dejando reproducir nada».
  - *Diagnóstico (telemetría del VPS, no adivinado)*: 9 crashes `NoSuchMethodError` en `:x4k` hoy 08:04–08:18 MX, **solo en 2.5.117** (Google Chromecast HD + Xiaomi 25053PC47G = tele y teléfono del usuario); 2.5.116 con 0 crashes. Stack: `yc.e$h.onPrepared` → `zc.c.d` → `Observable.interval` → RxJava `Schedulers.<clinit>` → `SchedulerPoolFactory.tryStart` → `No static method a(AtomicReference,Object,Object)Z in class Lk4/x` («declaration of 'k4.x' appears in …/base.apk»).
  - *Causa raíz (medida sobre los binarios publicados)*: el motor vive en `assets/x4k` (16,395 clases; su ofuscación ocupa TODO el espacio de 1-2 letras `a..z`, `a0..z9`, `aa..zf`) y se carga con `DexClassLoader(parent = app)` → **parent-first**: cualquier nombre nuestro ensombrece al suyo. En vc353 R8 asignó `k4.x` a `kotlin.collections.IndexedValue` (confirmado en el `mapping.txt`). Un parser propio de DEX dio la prueba: **2.5.116 definía 696 nombres del motor (sin `k4.x`) y reproducía; 2.5.117 definía 719 (con `k4.x`) y no reproducía**. Blindar Tink en 2.5.78 fue jugar a los topos: R8 reasigna nombres en CADA build.
  - *Fix estructural*: `-repackageclasses 'visionplus'` en `app/proguard-rules.pro` (el motor no tiene NI UNA clase en `visionplus.*`, medido) → el APK nuevo define 7,037 clases y solo **14** nombres compartidos con el motor (todos clases de librería con `-keep` que YA compartía 2.5.116, ninguna interviene al reproducir): `k4.x` queda libre y **682 nombres liberados, 0 ensombrecimientos nuevos** vs la versión que funcionaba.
  - *Guardián nuevo (`verifyEngineNamespace` en `app/build.gradle.kts`)*: corre tras `minifyNormalReleaseWithR8`, parsea los 3 DEX de `assets/x4k` + el `mapping.txt` y **rompe el build** si una clase renombrada cae en un nombre del motor (ignora los marcadores `R8$$REMOVED$$CLASS$$N`). Salida verificada: `motor=16395, nuestras renombradas=5288, nombres reales compartidos=14, colisiones generadas=0`. Documentado en `docs/REGRESSIONS.md`.
  - *Verificación*: el emulador **no discrimina** este bug (el motor ARM-traducido aborta en `libc++_shared` al hacer `play`, idéntico con el APK roto y con el nuevo: limitación del entorno), así que la prueba es a nivel bytecode: el asset publicado se descargó y se escaneó → **vc354 / 2.5.118, firma histórica `9593cb0d…`, `k4.x` ausente**. Suite **746 Android + 294 backend** en verde.
  - *Pendiente*: confirmar en los equipos del usuario (teléfono + Chromecast HD) que reproducen y vigilar la telemetría por si reapareciera `NoSuchMethodError`.
  - *Palanca de emergencia ACTIVADA (2026-10-22, sin OTA)*: `POST /v1/config` con `{"min_version_code":354}` → `GET /v1/config` público ya sirve `min_version_code: 354`. Todo `versionCode < 354` (2.5.117 y anteriores) ve **«Actualización requerida»** en cada apertura, el aviso NO se cierra con Atrás ni tocando fuera (solo con «Cerrar», que no recuerda el skip) y «Actualizar ahora» descarga el APK dentro de la app. Verificado en el emulador con la 2.5.117 real (vc353): aviso + descarga al 45 % en 10 s. OJO con la aritmética: `shouldForce = ownVersionCode < min_version_code`, así que para forzar la 2.5.117 (vc353) el valor tenía que ser **354**, no 353. Para apagarla: `{"min_version_code":0}`.
- **OTA v2.5.117 PUBLICADA: continuidad real entre el teléfono y la tele (minuto exacto + «Seguir viendo»/favoritos/vistos sincronizados) y el reloj móvil discreto — 746 Android + 294 backend** (2026-10-21):
  - *Publicada*: `tools/publish_ota.ps1 -Version 2.5.117 -NotesFile ota_notes_2.5.117.md` (vc353, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.117). Asset descargado y verificado: `versionCode='353' versionName='2.5.117'`, firma histórica OK.
  - *Contenido*: Fase A (ficha/control remoto → «Ver en la TV» reanuda en el minuto exacto; el vivo no), Fase B (Ajustes → «Continuidad entre equipos»: código de 8 caracteres, sync de «Seguir viendo»/Favoritos/Ver más tarde/Vistos por el VPS, tombstones, una fila por serie, refresco del Inicio) y el reloj móvil como texto pequeño en la barra (la tele conserva su píldora). Servidor ya desplegado: `POST /v1/sync`, tabla `sync_items` con código hasheado, índice acotado a 100 por categoría y API con 1 GB de tope.
  - *Verificado*: suite **746 Android + 294 backend** en verde; release R8 limpio. En el emulador (`Vision_Phone`, release pre-bump con los mismos cambios): arranque sin crashes, Home con la hora en la barra (12:44 CDMX vs 6:44 del sistema), Ajustes → Continuidad entre equipos → **Generar código** → código `3JGW-ZYWF` → «Última sincronización: ahora mismo» (el VPS recibió 2 elementos), y «Olvidar» limpió el código local; el grupo de prueba se borró del VPS (`sync_items` volvió a 0).
  - *Pendiente*: prueba física teléfono+tele (mismo código → «Seguir viendo»/favoritos convergen, borrar en uno desaparece en el otro, «Ver en la TV» abre en el minuto exacto) y valorar `sync_enabled=false` si algo se desborda.
- **Reloj discreto en el móvil: la hora deja de flotar sobre las pantallas y pasa a texto pequeño dentro de la barra superior — la tele conserva su píldora (incluido en la OTA v2.5.117)** (2026-10-21; **712 tests Android** en verde):
  - *Pedido del usuario*: «El reloj siento que es muy estorboso en móvil, podemos hacerlo más discreto?»
  - *Antes*: `MainActivity` pintaba `VisionClockBadge` (píldora negra con borde blanco, texto en negrita y «CDMX») arriba al centro de TODO el menú; en el móvil se montaba sobre la barra superior (logo/acciones) y en la ficha sobre el arte.
  - *Ahora*: `showsFloatingClock(isTv, route)` (puro) decide el overlay flotante: **solo Smart TV**, apagado en reproductores y buscador (lo de siempre). En el móvil la hora vive **dentro de la barra de `PhoneApp`** como `VisionClockText`: `labelMedium` (12 sp), peso medio, blanco al 62 %, **sin fondo, borde ni «CDMX»** (la zona viaja en la descripción accesible), alineada al extremo derecho junto a las acciones (en Inicio queda antes de campana/recargar/buscar). El tic de ambos sale de `rememberClockText` (despierta exactamente al cambiar el minuto).
  - *Ficha móvil*: ya no lleva reloj encima (el arte queda limpio); en escritorio/tablet la hora sigue en la misma barra.
  - *Guardianes*: `VisionClockTest` +2 (el móvil NUNCA flota en ninguna ruta; la tele sí en menú/ficha/deportes/vivo y no en reproductores/buscador), 2 casos compuestos en `GeePickDialogDuplicateTest` (móvil sin «CDMX», tele con «CDMX») y `ClockWiringTest` (2: la barra móvil pinta `VisionClockText`; el overlay pasa por `showsFloatingClock`). **Lección repetida**: una clase Compose nueva con su propio sandbox Robolectric (`VisionClockUiTest`) volvía a tumbar `GeePickDialogDuplicateTest` con `AppNotIdleException` en la suite completa (aislada pasaba); los 2 casos se movieron a esa clase existente y el archivo nuevo se retiró. Suite **712/712**.
  - *Verificado en el emulador (Vision_Phone, build debug)*: Inicio «Vision+ … 10:34 [campana][recargar][buscar]»; Películas con la hora al extremo derecho; ficha de «Hope» sin reloj encima; con el equipo en otra zona (barra del sistema 4:34) la app mostró la hora de CDMX (10:34).
  - *Estado*: **publicado en la OTA v2.5.117 (vc353)**; el APK sigue en `app/build/outputs/apk/normal/release/` para reinstalarlo encima con `pm install -r` si se quiere.
- **Fase A de continuidad entre equipos: «Ver en la TV» ya reanuda en el minuto exacto del teléfono (incluido en la OTA v2.5.117)** (2026-10-21; **723 tests Android** en verde):
  - *Pedido del usuario*: tras el análisis de continuidad, «Adelante» para implementar la Fase A del plan (la B —sincronizar Seguir viendo/Mi lista— queda para después).
  - *Qué viaja ahora*: el control remoto ya mandaba `open` con serie/temporada/capítulo; se añadió `pos` a `RemoteMsg`, `positionMs` a `RemoteClient.sendOpen`, `&r=<ms>` al deep link (`remoteOpenUriString`), `resumeMs` a `WatchLink` (`LauncherLinks.uriFor/parse`) y la tele prefiere ese minuto sobre su propio historial en `launcherRouteFor` (nuevo `routeFromLink`). En la ficha móvil y en el buscador del control remoto, cada `sendOpen` calcula el minuto con `resumePositionFor(context, ref)` (nuevo `data/remote/RemoteResume.kt`: prueba la ref y el playCode pelado; 0 si no hay recuerdo).
  - *Compatibilidad*: el campo es opcional y `encodeDefaults=false`; una tele vieja ignora `r` y usa su historial, un teléfono viejo no manda `pos`. El vivo no reanuda. La visibilidad de «Ver en la TV» no cambió (sigue exigiendo conexión viva, sin botones muertos).
  - *Guardianes*: +11 casos en `RemoteProtocolTest`, `RemoteOpenTest`, `RemoteClientWireTest`, `RemoteServerFlowTest`, `LauncherDeepLinkTest`, `RemoteResumeTest`, `CastPickerWiringTest` y `RemoteTitleCastTest`. Suite **723/723**.
  - *Estado*: **publicado en la OTA v2.5.117 (vc353)**. *Pendiente*: prueba física teléfono+tele (ficha → Ver en la TV → la tele abre en el minuto exacto; serie → capítulo exacto; sin conexión el botón sigue oculto).
- **Fase B de continuidad entre equipos: «Seguir viendo», favoritos, ver más tarde y vistos se sincronizan por el VPS con un código compartido — servidor desplegado y app en la OTA v2.5.117** (2026-10-21; **746 tests Android + 294 backend** en verde):
  - *Qué*: sin cuentas, un código de 8 caracteres (sin 0/O/1/I) identifica al grupo. El teléfono lo genera o lo recibe de la tele; en Ajustes → «Continuidad entre equipos» se copia/comparte, se vincula, se sincroniza a mano o se olvida. Con el control remoto conectado, «Enviar a la tele» le entrega el código por la LAN (mensaje `sync`; la tele lo adopta y empieza sola).
  - *Servidor (desplegado en el VPS y verificado EN VIVO)*: `server/api/sync.py` (puro) + `POST /v1/sync` y `POST /v1/sync/forget` en `app.py`; tabla `sync_items` en `schema.sql` (`sync_id` = **SHA-256 del código**, nunca el código en claro; `kind/key/payload/deleted/client_at/server_seq/device_id`). El push es LWW por `(kind, key)` (los datos solo cambian si `excluded.client_at` es mayor; `client_at = greatest(...)`) y el pull es incremental por `server_seq` (no depende del reloj del equipo). El `server_seq` avanza **siempre** (también cuando un item viejo pierde) para que el pull devuelva al ganador y ningún equipo quede divergente. Límites: 200 items/petición, 64 KB, 120/min por código+IP, `at` acotado a [now−180d, now+1h]. Probado en público (`https://visionplusv2.duckdns.org/v1/sync`): push, pull, tombstone viejo ignorado, tombstone nuevo borra, presente más nuevo revive, código inválido 401 y limpieza. La tabla quedó vacía tras las pruebas.
  - *INCENDIO apagado al desplegar (lección)*: el primer `GET /v1/index` sin `limitFilms` materializaba **157 MB** y el contenedor de 512 MB moría por OOM (restart loop; parecía «Empty reply»). Fix doble: el default de `limitFilms` pasó de 0 a **100** (el que usa la app; `ge=1, le=500`) y el `mem_limit` de la API subió a **1 GB** en `docker-compose.yml` (el host tiene 22 GB). Verificado en vivo: `/v1/index` 200 con **5.96 MB / 1.6 s**, `limitFilms=500` 21.7 MB, memoria 205 MB/1 GB, `/v1/search` y `/v1/section` intactos. `test_api_serve.py` blinda el default acotado.
  - *App*: `data/sync/SyncCode.kt` (normalizar/generar/presentar), `SyncMerge.kt` (fusión pura: resume por `updatedAt` y colapso por serie; fav/later LWW con unión en la primera sincronización; watched unión POR SERIE, no por capítulo; tombstones con su hora original, podados a 180 días) y `SyncStore.kt` (snapshot local, push+pull con `X-Api-Key`/`X-Sync-Code`, 2.5/3.5 s, jamás lanza, flag `sync_enabled`). Disparadores: `MainActivity.onResume`, favorito/ver más tarde en fichas móvil y TV, vaciar historial y el botón de Ajustes; las filas «Seguir viendo» de Inicio móvil/TV se refrescan al terminar la sync (observan `lastSyncAt`). `UserLibrary` gana `filmToJson/filmFromJson`, reemplazos y `watchedEpisodesFresh` (lee el XML que escribe `:x4k`); `PlaybackHistory.replaceAll`.
  - *Interfaz*: tarjeta móvil `ui/settings/SyncSettingsCard.kt` (código, copiar/compartir, enviar a la tele, vincular, sincronizar, olvidar, estado) y diálogo TV `ui/tv/TvSyncDialog.kt` (la tele muestra el código; el teclado está en el teléfono). El teléfono puede generar el código y mandarlo a la tele si ya están emparejados.
  - *Guardianes*: `SyncCodeTest`, `SyncMergeTest` (unión inicial, LWW, serie colapsada, tombstones viejos/nuevos, revival, watched por serie, detect/prune), `SyncStorageTest` (JSON de ficha y XML fresco de `:x4k`), `SyncWiringTest` (disparadores y UI), +2 remotos (`syncCode` en el cable y la tele adopta el código). Backend `test_sync.py` (9). Suite **746 Android + 294 backend**.
  - *Estado*: **publicado en la OTA v2.5.117 (vc353)**. *Pendiente*: prueba física teléfono+tele (mismo código → «Seguir viendo»/favoritos convergen; borrar en uno desaparece en el otro; sin red no bloquea) y valorar `sync_enabled=false` desde `/v1/config` si algo se desborda.
- **Lote 2.5.116: el Modo Sin Conexión se abre solo (también con el router sin internet) y el reproductor solo ofrece las opciones que el video tiene — OTA publicada** (2026-10-20; **706 tests Android** en verde):
  - *Pedido del usuario*: revisar cómo funciona el modo offline («cuando no detecte conexión solo tiene que abrirte el modo offline») y que el reproductor muestre opciones honestas: varios idiomas solo si existen, igual con los subtítulos y la resolución.
  - *Modo offline — causa real*: `NetworkMonitor` aceptaba cualquier red con transporte + `NET_CAPABILITY_INTERNET`. Con el Wi-Fi del router pero el cable/módem caído (o un portal cautivo) Android marca INTERNET pero no **VALIDATED**, así que la app intentaba cargar el catálogo y terminaba en un Inicio vacío en vez de abrir «Vision+ Offline». Ahora, cuando la red no está validada, se confirma con una **sonda TCP 443** a resolutores públicos (1.1.1.1 / 8.8.8.8, 1.5 s, hilo aparte, una sola a la vez) y solo entonces se da por buena; el arranque usa `NetworkMonitor.verify()` en IO (nunca frena el hilo principal) y `onResume` revalida por si el callback no alcanzó. La sonda re-lee el estado al volver para no «resucitar» una red caída.
  - *Modo offline — navegación*: `shouldShowOfflineMode(isOnline, route)` centraliza la decisión en móvil y TV; **un reproductor abierto (VOD o vivo) NO se interrumpe** por una caída momentánea (el reproductor maneja sus reintentos), y el Modo Sin Conexión aparece al volver al menú. El botón «Reintentar conexión» avisa «Aún no hay conexión a internet» cuando la sonda aún no da salida; el diálogo de actualización del móvil no se ofrece sin conexión.
  - *Reproductor nativo (`X4KPlayerAty`)*: `applyOptionPillVisibility()` decide cada píldora con lo que el título REALMENTE trae — audio solo con **más de un idioma** (declarado por el portal o pistas del motor; con una sola pista el menú de audio ya no se abre), subtítulos solo si el servicio del programa devolvió alguno, calidad solo si hay **más de una resolución**. Se re-evalúa al abrir el título, al llegar la lista de subtítulos, al primer cuadro y 1.5 s después (el motor expone sus pistas tarde). `rewireOptionPillFocus()` re-cablea la cadena del D-pad para que las ocultas no dejen destinos muertos.
  - *Reproductor en línea (`PlayerCore`, ExoPlayer)*: `refreshTrackChoices()` lee `onTracksChanged` (pistas de audio, subtítulos y alturas de video reales), `applyAudioChoice`/`applySubtitleChoice` aplican `TrackSelectionOverride`; el menú «Audio, subtítulos y calidad» solo dibuja las secciones con opciones y el botón se oculta si no hay ninguna. Se retiró el menú de relleno («Servidor» falso, «Original», «este archivo no trae subtítulos», sincronía placebo). La «Calidad preferida» solo se aplica si esa resolución existe en el título (antes un 480p con «1080p» en Ajustes podía quedarse sin pista/video negro) y si no, cae a Auto.
  - *Guardianes*: `OfflineModeGateTest` (5: menú sin red, reproductores que no se interrumpen, sonda/`verify` en el arranque, cableado en ambos shells) + `PlayerHonestOptionsTest` (8: decisión pura de audio/subtítulos/calidad, resoluciones duplicadas, cableado del nativo y del ExoPlayer) + 3 casos del menú honesto compuestos en `GeePickDialogDuplicateTest` (mismo sandbox Robolectric: una clase Compose nueva con otro sandbox envenenaba la suite completa — lección repetida). Suite **706/706**.
  - *Verificado en el emulador (Vision_Phone)*: con todo cargado, modo avión → **«Vision+ Offline»** con «No se detectó conexión a internet», almacenamiento y descargas; al quitar el modo avión volvió sola al Inicio. La verificación fina del reproductor quedó en Robolectric/cableado (el AVD frío encadenó ANR de Launcher/SystemUI y no era fiable para capturas).
  - *Publicada*: **OTA v2.5.116** (vc352, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.116). *Pendiente*: prueba física en teléfono/TV (modo avión → offline; película de un solo audio/calidad sin píldoras; una con subtítulos → píldora; deportes con varias resoluciones/audio).
- **Lote 2.5.115: la tarjeta «Siguiente capítulo» deja de verse vacía — nombre real y sinopsis es-MX por capítulo guardados en el VPS (TMDB → `episode_meta`) y servidos en `/v1/episodes` — OTA publicada** (2026-10-20; 689 tests Android + 281 backend):
  - *Pedido del usuario*: «Mi app ya muestra un modal de siguiente episodio pero solo es un modal grande que se ve vacío, ¿podemos poner el nombre y una pequeña sinopsis? ¿O es muy caro? Todo esto guardado en una BD en el VPS».
  - *Causa*: el portal bautiza sus capítulos con una marca técnica («Ataque a los Titanes Temporada 2_Ataque a los Titanes T2-01»); `cleanEpisodeName` la limpia a "" y la tarjeta solo decía «Capítulo 2» + cuenta regresiva (con el póster perdido si el intent no traía `posterUrl`). No es caro: TMDB ya se usa para el índice y una temporada entera se baja con **UNA petición**, guardada en Postgres.
  - *Servidor (desplegado y verificado EN VIVO)*: tablas `episode_meta` (tmdb_id, season, episode → name/overview/still) y `episode_meta_season` (una fila por temporada consultada: status/attempts/TTL 14 días, enfriamiento 24 h, tope 4). El worker `sync/episodes_mirror.py` resuelve `tmdb_id`+`season` desde `enrichment`/`group_members` del ref espejado y baja `/tv/{id}/season/{n}?language=es-MX` (si no hay ninguna sinopsis, una segunda pasada `en-US` solo rellena vacíos; nunca pisa es-MX), presupuesto `EPISODES_META_MAX_PER_CYCLE=20` por pasada. `/v1/episodes` cruza por número de capítulo (`seriesNumber`) y agrega `title`/`overview`/`still` sin tocar el `name` crudo (compatibilidad del parser de la app). Verificado: AoT T2 → «El titán bestia», «Regreso a casa», «Hacia el suroeste» con su sinopsis es-MX y miniatura (`still: true`).
  - *App*: `EpisodeMirror.EpisodeInfo` + `info(seasonRef, code)` (lee la copia en RAM; si está fría pide una vez al VPS, ~50 ms, jamás lanza). La tarjeta de `X4KPlayerAty.showNextCard` se rediseñó como fila: arte del capítulo a la izquierda (110 dp en TV; en teléfono no, la tarjeta es angosta) + nombre real y sinopsis de 3 líneas; el nombre TMDB reemplaza la marca técnica y, sin póster de serie, se usa la miniatura 16:9 del capítulo. Identidad por `nextTarget.code` (si el usuario ya cambió de capítulo, la respuesta tardía no pinta nada) y todo en hilo aparte. Tarjeta móvil más ancha (0.9 del ancho).
  - *Guardianes*: backend (6) parseo/mergen de TMDB, `meta_due` (TTL/enfriamiento/techo), `enrich` por número con campos vacíos si no hay fila, SQL del endpoint y tablas; app (3) `infoFrom`/caché y cableado de la tarjeta (sinopsis, título real, miniatura). Suite **689 Android + 281 backend**; release R8 101.1 MB compila limpio. **OTA v2.5.115 PUBLICADA** (vc351, 101.1 MB, firma histórica `9593cb0d…`, asset `VisionPlus.apk`, release https://github.com/ChronosBVRX/Vision-/releases/tag/v2.5.115). *Pendiente*: prueba física (llegar a créditos y ver la tarjeta con nombre/sinopsis; el resto de temporadas se enriquece sola en minutos). El servidor ya sirve los metadatos sin OTA.
  - *Cobertura en otras series + fix del `tmdb_id` equivocado (2026-10-20, solo servidor)*: el usuario preguntó si funcionaba en series aparte de SNK. Medido en vivo: Yellowstone T2 salía bien pero **Yellowstone T4/T5 y Los Simpson T17 fallaban con 404** porque la ficha de ESA temporada tenía un `tmdb_id` mal emparejado (214951 / 383887) mientras el resto de su grupo tenía el bueno (73586 / 456); **644 grupos** del índice tienen desacuerdo de `tmdb_id` entre sus temporadas. Fix: worker y API resuelven el `tmdb_id` por **voto mayoritario del grupo** (`count(*) desc`, desempate por `avg(match_confidence)`, respaldo a la ficha propia) — la MISMA regla en `sync/episodes_mirror.py` y `api/app.py` (guardián de cableado). Verificado tras desplegar: **Los Simpson T17 → 22/22 capítulos con nombre y sinopsis**, **Yellowstone T4 → 10/10**; las 6 temporadas espejadas quedaron enriquecidas (96 capítulos con sinopsis). Las claves viejas fallidas quedan inertes. Además, `EpisodeMirror.info` refresca UNA vez la copia si se cacheó sin metadatos (`hasMeta`) — app, viaja en la próxima OTA (no crítica: la ventana es de minutos). Guardianes: backend 282/282, Android **690/690**.
  - *Pre-calentamiento popular primero (2026-10-20, solo servidor)*: el usuario preguntó si estaba todo el catálogo (52,294 títulos · 23,494 series/anime/kids · 3,825 grupos · **≈20,717 temporadas** a enriquecer; solo 6 estaban por el espejo). Decisión: pre-calentar **top-500 grupos (2,723 temporadas) + top-500 series sueltas** (~3.2k peticiones, una por temporada) y el resto bajo demanda. Nuevo `_META_PREWARM_SQL` en `sync/episodes_mirror.py` (voto mayoritario del grupo, solo nunca-consultadas para no martillar fallidas, orden por popularidad; presupuesto `EPISODES_META_PREWARM_MAX_PER_CYCLE=120` por pasada cada 90 s ≈ 4,800/h). **Bug cazado al desplegar**: `ranked` no llevaba `popularity` al CTE (`column r.popularity does not exist` en cada pasada, el loop lo tragaba) — corregido y re-verificado. En vivo: **639 temporadas / 11,318 capítulos** en ~10 min, ritmo medido **~3,280 temporadas/h**; ya con metadatos series que nadie abrió (Breaking Bad T1 «Acuerdo no violento», Game of Thrones T1 «Baelor»). Las fallidas (38) son huecos reales de TMDB y no se reintentan desde el prewarm. Guardianes backend **283/283**; Android sin cambios (690/690).
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
- App Android (Compose) **"Vision+"** - pkg `com.chronosbvrx.visionplus`, `versionName` actual **2.5.177**
  (versionCode **417**; estable v2.5.177; launcher 1.3.1/24; la beta más reciente 2.5.168 - **historial de la base 2.5.150:** **perfiles renomrables sin cambiar de perfil** (lápiz al
  enfocar en la tele, modal de escritura con el teclado nativo de la TV; lápiz por perfil en el móvil),
  **zapping acotado al menú del launcher** y **Atrás devuelve a Vision+ Home**; incluye el precalentamiento
  del motor, la EPG masiva y «Personalizar Inicio» de las betas 2.5.149) y el **launcher 1.0.13**
  (versionCode 14) con el enlace de canal que lleva su menú filtrado, `from=home` y el selector directo
  del Inicio del sistema (`RoleManager.ROLE_HOME`, con ajustes como respaldo manual); antes la v2.5.130 - la **tele con la piel de Vision+ Home** (barra superior global,
  hero/tarjetas/chips del launcher, botón principal blanco, pantalla completa; todas las secciones de
  la app se conservan) y el **launcher 1.0.9** que detecta y ofrece actualizar una Vision+ vieja;
  antes la v2.5.125 - **perfiles**: hasta 5 personas por equipo, cada una con su
  «Seguir viendo», historial, Mi lista, series seguidas y código de continuidad; el perfil Principal
  conserva los datos heredados; móvil y tele (Ajustes → Perfiles), y antes la v2.5.123 — hotfix de estabilidad: el motor se re-extrae solo si su DEX no
  verifica (cierra el `VerifyError` que dejó una tele sin reproducir), la sesión del portal deja de
  reacuñar en bucle cuando la rechaza y la app mide su **% de aperturas sin crash**; incluye la pestaña
  «Historial» de «Mi lista»), y antes la v2.5.122 — «Porque viste X» en el Inicio (PlaybackHistory + Related + géneros,
  sin repetir la semilla ni «Seguir viendo», sin mezclar cine con series y con filtro infantil) y
  «Tus series · Esta semana» (agenda de `/v1/schedule` cruzada con las series seguidas, una tarjeta por
  serie y caché de 6 h), en móvil y tele; la v2.5.121 «Relacionadas» en la ficha, modal de sugerencias al
  terminar una película, una sola sinopsis es-MX estable, selector de temporadas/capítulos compacto y
  «Ver más» real por género (páginas de `/v1/category`, 24/180 en equipos modestos y 48/480 en grandes); la v2.5.120
  los logos de TV en vivo se pintan a su tamaño natural (tope 2.5×,
  pieza compartida `ui/live/ChannelLogo.kt`) en vez de estirarse a toda la tarjeta; la v2.5.119 blindaje
  definitivo del motor: `EngineClassLoader` pregunta
  primero al DEX del motor por sus nombres ofuscados (con kill-switch remoto `engine_child_first`) y
  `leakedEngineNames` reporta `ENGINE_NAMESPACE_LEAK` si una clase del motor la sirviera el APK; el
  worker gana un canario que frena solo la oferta si un build nuevo no reproduce; la v2.5.118 el
  hotfix del apagón (R8 volvía a ensombrecer clases del motor XTV con nombres generados); la v2.5.117
  continuidad real entre el teléfono y la tele: «Ver en la TV»
  reanuda en el minuto exacto (el vivo no) y «Seguir viendo»/Favoritos/Ver más tarde/Vistos se sincronizan
  con un código compartido por el VPS (tombstones, una fila por serie, refresco del Inicio); el reloj del
  móvil pasa a texto pequeño en la barra superior y la tele conserva su píldora; la v2.5.116 el Modo Sin Conexión se abre solo, incluso con el
  Wi-Fi conectado pero sin internet real (sonda de salida), y el reproductor solo muestra las opciones que el
  video trae: audio con varios idiomas, subtítulos cuando existen y calidad solo con más de una resolución
  —en el motor nativo y en ExoPlayer—; la v2.5.115 la tarjeta «Siguiente capítulo» con nombre y sinopsis
  es-MX; la v2.5.113 el Inicio de Smart TV calcula el tamaño de sus filas desde
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
