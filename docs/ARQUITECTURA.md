# Arquitectura de Vision+ (mapa para no perderse)

> Documento de bus factor: qué vive dónde, qué reglas no se rompen y cómo se verifica un cambio.
> Si algo de aquí deja de ser cierto, actualízalo en el mismo commit.

## 1. Las tres piezas

```
app/            Android (Kotlin + Compose) — cliente
launcher/       Vision+ Home — APK aparte de pantalla de inicio (sin motor ni media3)
home-contract/  contrato compartido entre los dos APK (DTOs, JSON del feed, deep links)
server/api/     FastAPI + Postgres — índice, búsqueda, deportes, sync, diagnóstico
server/sync/    Workers — XTV/ESPN/TMDB/EPG/diagnóstico (loop propio, sin tocar la API)
tools/          Publicación de OTA, utilidades
```

**Vision+ Home** (decisión 2026-10-23): la tele puede tener un Inicio propio sin tocar el
streaming. `app` expone `content://…homefeed/feed` (permiso `signature`, solo lectura) con un
JSON v1 de hero/filas/«Seguir viendo»/TV en vivo; `launcher` lo pinta y **dispara deep links**
(`visionplus://watch|title|live|open/...`). El launcher nunca reproduce: si Vision+ falla o no
está, el Inicio sigue mostrando apps y ajustes. Regla: el launcher pinta **su caché primero** y
consulta el provider después (tope 1.5 s), porque leerlo puede arrancar Vision+ en frío.

No hay monorepo compartido entre `app` y `server`: el contrato es el JSON de `/v1/*`.
La app **nunca** habla con el portal XTV para el catálogo: habla con nuestro VPS
(`RemoteIndexSource`), que a su vez espeja el portal. La única excepción es la
reproducción (handshake de `X4KApi`) y la búsqueda de paridad (`/v1/search` → portal).

## 2. Capas dentro de la app

```
MainActivity ──► PhoneApp / TvApp (navegación, dos shells distintos)
                    │
                    ├── ui/*            pantallas Compose (sin lógica de negocio)
                    ├── data/*          lógica pura + clientes de red + persistencia
                    │     ├── xuper/    portal XTV, motor nativo (DEX en assets), sesión/slots
                    │     ├── sources/  índice remoto (VPS), caché en archivo
                    │     ├── homefeed/ feed que lee Vision+ Home + instalador del launcher
                    │     ├── offline/  bóveda cifrada, descargas, progreso
                    │     ├── sync/     continuidad entre equipos (código de 8 caracteres)
                    │     ├── gee/      deportes en vivo (CMS externo espejado en el VPS)
                    │     ├── diag/     telemetría (crash/ANR/anomalía/sesión)
                    │     └── ...
                    └── ui/player/X4KPlayerAty.kt   reproductor nativo (proceso :x4k)

LauncherActivity ──► LauncherScreen (otro APK: barra superior, hero, filas, apps)
                    │
                    ├── ui/*            tarjetas, foco, centro de control, buscador
                    └── data/*          FeedRepository (caché + provider), apps, update

TvApp (tele) ──► TvTopBar (barra superior global: Inicio/TV/Deportes/Películas/Series/Mi lista)
                    │                 + reloj CDMX, perfil, lupa y ajustes
                    └── la pantalla de la ruta (TvHomeScreen, TvLiveScreen, TvSportsScreen…)
```

La tele de Vision+ comparte el **lenguaje visual** con Vision+ Home: `ui/tv/TvTopBar.kt` (barra
superior global, el riel se retiró) y `TvComponents.kt`/`TvWidgets.kt` (hero, tarjetas 2:3 con la
nota dorada, chips redondeados, botón principal blanco, anillo de foco). `tvTopBarVisible(route)`
decide en qué rutas se pinta (no en fichas, reproductores, buscador ni ajustes) y
`TV_TOP_BAR_DP = 62` se descuenta al calcular las filas del Inicio.

Regla de oro: **la lógica testeable vive en `data/` como funciones puras** (`internal`),
y la UI solo la pinta. Los guardianes de la suite (`*Test.kt`) prueban esas funciones y,
cuando importa, comprueban el **cableado** leyendo el fuente (patrón `WiringTest`).

## 3. Invariantes que no se negocian

1. **Un solo proceso de motor**: `:x4k` no acepta dos programas distintos; el reproductor
   es residente (`singleTask`) y reusa la vista. Descargas y reproducción compiten por el
   mismo proceso → `EngineDownloadGate`.
2. **R8 no puede pisar el espacio del motor**: `-repackageclasses 'visionplus'` +
   `verifyEngineNamespace` (falla el build si una clase renombrada cae en un nombre del
   DEX del motor). El motor se carga con `EngineClassLoader` (child-first solo para sus
   nombres, con kill-switch `engine_child_first`).
3. **La extracción del motor es atómica y sellada**: `assets/x4k/*.dex` → `files/x4k/`
   con `.part` + rename y manifiesto `engine-manifest.txt` (versión + tamaños). Un DEX
   corto se detecta, se vuelve a copiar y, si una clase no verifica (`LinkageError`), se
   borra el motor extraído y se estrena una vez (`X4KTitan.boot`).
4. **Una sesión por cuenta**: la app renueva proactivo cada 6 h, con silencio de 30 s
   (`MINT_COOLDOWN_MS`), 10 s para patadas (`FORCED_MINT_COOLDOWN_MS`) y **60 s de
   castigo tras un mint fallido** (`MINT_FAIL_BACKOFF_MS`). Sin el castigo, un rechazo
   permanente del portal (región en lista negra) reacuñaba en cada llamada.
5. **La telemetría nunca desaloja un crash**: los latidos de sesión van a su cola
   (`diag_sessions`, tope 5); los reportes reales a `diag_queue` (tope 40). Una anomalía
   del mismo código calla 60 s y cuenta las omitidas.
6. **Dos shells, una lógica**: móvil (`PhoneApp`) y tele (`TvApp`) comparten `data/`.
   Toda función con D-pad tiene su guardián en `ui/tv/*Test`.
7. **Nada de emojis en la UI** (`UiNoEmojiTest`) y los iconos salen de `VisionIcons`.
8. **Los datos personales se resuelven por perfil**: Mi lista, historial, series seguidas y
   el código de continuidad piden su archivo a `ProfileStore.prefsName` (el perfil
   `principal` conserva los nombres legados; los demás usan `base__p1`). El perfil activo
   vive en `files/active_profile` y se lee en cada acceso, porque `:x4k` también escribe.
   Ajustes, descargas, modo niños y emparejamiento son del equipo.

## 4. Calidad medible

- **Suite**: `:app:testNormalDebugUnitTest` (Android, Robolectric/Compose) +
  `python -m pytest server/tests` (backend, sin red ni DB).
- **Release**: `tools/publish_ota.ps1` (bump, tests, R8, firma histórica, `gh release`).
- **Campo**: `POST /v1/diag` (la app) → `diag_reports`. El worker `sync/diag.py` hace
  triage con dos capas (humano + técnico), reincidencias, canario de builds y **SLO de
  calidad** (`/v1/diag/health`): % de aperturas sin crash, con objetivo 99 % y muestra
  mínima de 20 sesiones.
- **Kill-switches** (sin OTA): `/v1/config` con `X-Admin-Key` → `downloads_enabled`,
  `diagnostics_forced_off`, `ota_paused`, `min_version_code`, `engine_child_first`.

## 5. Deuda conocida (por orden de impacto)

| Tema | Estado | Nota |
|---|---|---|
| Perfiles por persona | **hecho (2.5.125)** | Hasta 5, aislados por archivo; el Principal conserva los datos heredados. Falta sinergia fina con el modo niños (perfil infantil). |
| Localización (es/en) | pendiente | Hoy todo el texto está en español dentro del código; extraer a `strings.xml` es un barrido grande. |
| Módulos Gradle | en curso | `app` sigue siendo el módulo grande, pero `:home-contract` y `:launcher` ya viven separados (launcher de 1.2 MB sin motor). Partir `:core:data`, `:feature:player`, `:feature:catalog` sin mover modelos compartidos de golpe. |
| APK de 101 MB | aceptado | 45 MB son libs ARM (dos ABI), 34 MB el DEX del motor, 11 MB el motor Magis. Publicar por ABI rompería el enlace estable de la OTA. |
| Firma histórica de debug | aceptado | La base instalada manda: `keystore.properties` apunta a la llave histórica respaldada. |
| Catálogo/motor de terceros | riesgo estructural | Fuera del alcance técnico del repo. |

## 6. Cómo añadir una pantalla (sin romper el mando)

1. Ruta en `ui/Navigation.kt`; entrada en `PhoneApp.TABS` y/o `TV_RAIL_SPECS`.
2. `landingFocus()` en el primer elemento enfocable; si la pantalla puede abrirse en frío
   (sin datos), el landing debe existir igual (`FocusRescue` no come teclas si no aterriza).
3. `rememberReturnFocus(...)` para volver a la tarjeta exacta; `close(route)` para cerrar
   por identidad, nunca `pop()` a ciegas.
4. Guardián: compón la pantalla real en Robolectric y mueve el D-pad (`TvFocusTest`).
