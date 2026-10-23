# Sistema anti-regresiones — Vision+

> Objetivo: poder avanzar sin romper lo que ya funciona. Tres capas:
> **tests automáticos** (rápidos, bloquean el merge) + **checklist manual**
> (TV + móvil antes de publicar OTA) + **regla de oro** (cada fix trae su test).

## 1. Tests automáticos (corren en CI y en local)

| Qué | Dónde | Qué cubre |
|---|---|---|
| Catálogo local | `app/src/test/.../CatalogTest.kt`, `data/CatalogRegressionTest.kt` | `metaLine`, `searchFilms` (acentos), `allFilms` (dedup), `xuperRows`, índice remoto básico |
| Índice remoto | `data/sources/RemoteIndexTest.kt` | `parseIndex`/`parseRows`/`parseSeasons`: entradas sin id, `upToDate`, temporadas (dedup+orden), `groupId` |
| Agenda fútbol (parseo) | `data/sports/SportsSourceTest.kt` | orden por kickoff, scores número/texto, formatos de fecha, round-trip JSON, `shift` de días |
| Agenda fútbol (canales) | `data/sports/SportsLinkerTest.kt` | `norm`/`tokens`, feed MX primero, secuencia de tokens, tope 4 + dedup, alias (Chivas/Xolos/Rayados/PSG…), `mentionsBoth`, ventana en vivo |
| Espacio de nombres del motor (R8) | tarea Gradle `verifyEngineNamespace` (`app/build.gradle.kts`) | corre tras `minifyNormalReleaseWithR8`: parsea los 3 DEX de `assets/x4k` y el `mapping.txt` y **rompe el build** si R8 le da a una clase nuestra el nombre de una del motor (ensombrecimiento parent-first). Nació del crash `k4.x` de la OTA 2.5.117, que dejó la app sin reproducir nada; el blindaje por clase de 2.5.78 fue insuficiente porque R8 reasigna nombres en cada build |
| Aislamiento en ejecución del motor | `data/xuper/EngineLoader.kt` + `data/xuper/EngineLoaderTest.kt` + `EngineLoaderWiringTest.kt` | `EngineClassLoader` pregunta primero al DEX del motor por sus nombres ofuscados (raíz de 1–2 letras) y delega al padre si no están; `leakedEngineNames` comprueba en cada arranque que `k4.x`, `ed.e`, `yc.h`, `bd.a`, `uc.c` los sirva el motor y reporta `ENGINE_NAMESPACE_LEAK` si no. Kill-switch remoto `engine_child_first` |
| Logos de canales (TV en vivo) | `ui/live/ChannelLogoTest.kt` | `logoDisplaySize` no agranda más de `maxUpscale`× ni más que la caja, conserva la proporción y cae a la caja si el tamaño aún es desconocido; y las dos pantallas (móvil y tele) usan la pieza compartida en vez de pedir 480×270 / 640×360. Nació de los logos de 90×60 px estirados a toda la tarjeta |
| Canario de builds (servidor) | `server/tests/test_diag.py` (umbrales, decisión y cableado con un pool falso) | un build recién publicado (<48 h) con ≥3 fallos de reproductor en ≥2 equipos frena solo la oferta (`ota_paused=true`) y avisa por Telegram; se rearma al ver una versión más nueva o a las 12 h. NO cuenta ruido de equipos sanos (`PLAYER_FRAME_SKIP`, `PLAYER_SOFT_RESTART`, `PLAYER_FAILOVER*`, `PLAYER_SELF_HEAL`, `PLAYER_RESIDENT_IDLE`) |
| Navegación | `ui/NavigationTest.kt` | `push` sin duplicados, `pop` con Home base, `goTab`, payload de `Title`/`Player`/`LivePlayer` |
| M3U | `data/M3uTest.kt` | nombres/logos/grupos/VLC-opts, duplicados, URLs no-http |
| Foco TV (D-pad) | `ui/tv/TvFocusTest.kt` (Robolectric) | cada pantalla deja foco, mover el D-pad no lo pierde, Home→Ficha→Player→atrás, rail, tabs de En vivo |
| Launcher TV (Watch Next) | `data/launcher/WatchNextPlanTest.kt`, `WatchNextValuesTest.kt`, `LauncherWatchNextSyncTest.kt` (Robolectric), `ui/LauncherDeepLinkTest.kt` | specs del historial (una por serie, tope), reconciliación (una escritura atrasada no retrocede tarjetas), XML del historial entre procesos, values del canal (tipo/minuto/intent), deep link → ruta (reanudar o respaldo), ciclo completo contra un TV provider falso (publicar/actualizar/retirar/descartes) |
| Vision+ Home (launcher propio) | `home-contract/src/test` (14), `launcher/src/test` (17), `data/homefeed/HomeFeedStoreTest.kt`, `ui/HomeFeedWiringTest.kt` | JSON v1 del feed (round-trip, versión desconocida, basura, ítems rotos), deep links (`watch/live/title/open`), métricas por altura de pantalla (3 filas en 540 dp), orden de apps (favoritas/recientes), pestañas sin mezclar cine y series, feed nunca vacío en equipo nuevo, canales HD/FHD colapsados, permiso `signature` + provider de solo lectura, manifiesto con los 4 hosts y los dos APK compartiendo `:home-contract` |
| Servidor (sync/api) | `server/tests/` (pytest, sin red ni DB) | marcas de temporada, unión por kind/doblaje, aridad de tuplas vs TITLE_SQL, limpieza al servir, preferencia XTV/TMDB |

Comando local (Windows, PowerShell, desde la raíz del repo):

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testNormalDebugUnitTest
python -m pytest server/tests -q
```

Reportes: `app/build/reports/tests/testNormalDebugUnitTest/index.html`.

### Regla de oro

Si corriges un bug que ningún test cubría:

1. Escribe primero el test que falla (reproduce el bug en JVM si es lógica pura).
2. Corrige el código.
3. El test queda como guardián para siempre.

La lógica pura vive en objetos/funciones sin `Context` (`SportsLinker`, `SportsSource.parse`,
`RemoteIndexSource.parseRows`, `Catalog.kt`, `Navigator`) para que sea testeable en JVM.
Lo que necesita red/portal/dispositivo **no** se testea en unit: se verifica con el checklist manual.

Para que los tests puedan llamar la lógica pura, esas funciones son `internal`
(no `private`): mismo comportamiento, pero visibles para el source-set de test.

## 2. CI que bloquea (GitHub Actions)

Workflow: `.github/workflows/android-ci.yml` — corre `:app:testNormalDebugUnitTest`
en cada push/PR a `main`. Si falla, sube los reportes como artefacto.

Activar el bloqueo (una sola vez, en GitHub):

1. Repo → **Settings → Branches → Add classic branch protection rule**.
2. Branch name pattern: `main`.
3. Activar **Require status checks to pass before merging** y marcar `unit-tests`.
4. Activar **Require branches to be up to date before merging**.
5. (Recomendado) **Do not allow bypassing the above settings**.

Sin esto, el CI avisa pero no bloquea.

## 3. Checklist manual pre-OTA (5–10 min)

Los unit tests no ven video, foco real ni red del portal. Antes de cada release:

- [ ] **Móvil**: Home (hero+filas) → Ficha (score/reparto/duración) → Reproductor VOD → atrás sin rebote.
- [ ] **Móvil**: En vivo → selector de canales → zapping ⏪/⏩ → EPG "Ahora/Después".
- [ ] **Móvil**: Fútbol (Hoy/Ayer/Mañana), partido con canal → "Ver en vivo".
- [ ] **Móvil**: Categorías + Búsqueda con red y en modo avión (caché).
- [ ] **TV con mando**: Home → Ficha → Reproductor; foco siempre visible; OK y Atrás.
- [ ] **TV (launcher)**: abrir algo, salir y ver la tarjeta de Vision+ en «Continuar viendo» → seleccionarla y confirmar que reanuda donde quedó.
- [ ] **TV**: riel (Categorías/Fútbol/En vivo/Ajustes), En vivo (categoría + tabs).
- [ ] **TV**: Ajustes → "Versión instalada" = la que se va a publicar.
- [ ] Salir del reproductor no rompe el siguiente programa (proceso `:x4k` se mata).
- [ ] OTA: la app avisa una sola vez por versión y descarga/instala in-app.

## 4. Dónde mirar cuando algo se rompe

- Test rojo de `SportsLinkerTest` → cambió el matching cadena↔canal o los alias.
- Test rojo de `SportsSourceTest` → cambió el JSON de `/v1/sports` o el orden.
- Test rojo de `RemoteIndexTest` → cambió `/v1/index` o `/v1/section`.
- Test rojo de `TvFocusTest` → una pantalla TV perdió el foco inicial o el D-pad.
- Test rojo de `WatchNextPlanTest`/`LauncherWatchNextSyncTest` → cambió el espejo del historial al canal
  Watch Next del launcher (tipos, posición, reconciliación) o el `intent_uri` del deep link.
- Fallo solo en dispositivo (sesión `aaa100083`, EPG vacía, sin internet) → ver
  `AGENTS.md` → "Pitfalls"; no es regresión de código hasta probar con red/sesión sana.

### Hallazgos de la primera corrida (2026-10-10)

El sistema ya pagó su costo al estrenarse:

1. `TvFocusTest.everyPlaceholderScreenLands` encontró que **"Mi lista" vacía dejaba el
   mando sin foco** en TV (la rama vacía de `TvLibraryScreen` no componía ningún destino
   de `landingFocus`). Fix: el chip seleccionado es el destino cuando no hay películas.
2. `TvFocusTest.liveRailListAndTabsKeepFocus` encontró que **`XuperEngine.ensureReady`
   construía `XuperApiClient` fuera del `try`**: si el almacén cifrado fallaba, la excepción
   tumbaba el scope sin atrapar (en JVM por falta de AndroidKeyStore; en un aparato, ante
   un keystore dañado). Fix: la construcción fallida es `Result.failure` con backoff.

## 5. Costo de mantenimiento

- Agregar un test cuesta minutos; depurar una regresión en la TV cuesta horas.
- No agregar frameworks nuevos sin necesidad: JUnit4 + Robolectric ya están.
- Si un test se vuelve flaky (tiempo/fecha/red), hacerlo determinista con datos
  fijos en vez de borrarlo.
