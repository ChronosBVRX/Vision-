# Handoff maestro — Vision+ (para el agente que toma el proyecto)

**Fecha:** 2026-10-27. **Estado:** producto en producción, con usuarios reales.
**Este documento es el punto de entrada.** Léelo antes que nada; después sigue el orden de §12.

> A partir de ahora tú llevas el proyecto completo: producto, app Android, Vision+ Home,
> backend del VPS, publicación de OTA y documentación. El dueño decide el «qué»; tú propones
> el «cómo», lo implementas con tests y lo reportas con evidencia.

---

## 1. Qué es Vision+

App Android de streaming personal (marca **Vision+**, paquete `com.chronosbvrx.visionplus`,
autoría **ChronosBVRX**). Catálogo de ~52 000 títulos (películas, series, novelas, anime, kids)
y ~1 000 canales en vivo, servido por el **portal XTV** (tenant «masnew») y espejado en un VPS
propio. Reproduce con el **motor nativo del proveedor** (Titan/Ranger, ARM) embebido en el APK.

Tres productos en un repo:

| Pieza | Paquete | Qué es |
|---|---|---|
| **Vision+** | `com.chronosbvrx.visionplus` | La app (móvil y tele, dos shells) |
| **Vision+ Home** | `com.chronosbvrx.visionplus.home` | Launcher de TV aparte (1.5 MB, sin motor ni media3) |
| **Backend** | — | FastAPI + Postgres en el VPS: índice, búsqueda, EPG, deportes, diagnóstico |

No hay cuentas ni pagos: todo el catálogo es para todos. La app se actualiza **dentro de la app**
desde GitHub Releases (nunca Play Store).

---

## 2. Estado actual (verificado)

- **App 2.5.177 (versionCode 417)** — publicada y estable. Release `/releases/latest` = `v2.5.177`.
- **Vision+ Home 1.3.1 (versionCode 24)** — 1 508 820 B, en el mismo release.
- **Suites en verde:** **1002 tests Android + 153 launcher**; backend ~**431** (`pytest server/tests`).
- **APK:** ~82.6 MB, 5 DEX (2 propios + 3 del motor), firma histórica `9593cb0d…`.
- **VPS:** 20 contenedores (comparte host con La Veinte); `postgres` + `api` (127.0.0.1:8100) + `sync`.
  Índice en ~**v143+**, 52k títulos. Caddy nativo del host publica `/catalogo/*`.
- **Árbol de trabajo:** hay cambios **sin commitear** de la publicación 2.5.177. Ver §4 regla 1.

---

## 3. Arquitectura en una pantalla

```
app/            Android (Kotlin + Compose) — cliente móvil y tele
launcher/       Vision+ Home — APK aparte (Inicio de la TV, sin motor)
home-contract/  DTOs/JSON/deep links compartidos entre los dos APK
server/api/     FastAPI + Postgres — índice, búsqueda, EPG, deportes, banners, sync, diag
server/sync/    Workers (loop propio): XTV, TMDB, EPG, ESPN, GeeSports, marcadores, diagnóstico
tools/          Publicación de OTA y utilidades (inyección del motor, empaquetado Roku parkeado)
docs/           Arquitectura, planes y anti-regresiones
```

Dentro de la app:

```
MainActivity ──► PhoneApp / TvApp   (dos shells: móvil y tele, misma lógica en data/)
                    ├── ui/*         pantallas Compose (solo pintan)
                    ├── data/*       lógica pura (internal) + clientes de red + persistencia
                    │     ├── xuper/    portal XTV, motor nativo, sesión y slots
                    │     ├── sources/  índice remoto del VPS (caché en archivo)
                    │     ├── homefeed/ feed que lee Vision+ Home
                    │     ├── offline/  bóveda cifrada y descargas
                    │     ├── sync/     continuidad entre equipos (código de 8)
                    │     ├── gee/      Deportes en vivo
                    │     ├── diag/     telemetría
                    │     ├── banners/  hero del launcher
                    │     └── profile/  perfiles por persona
                    └── ui/player/X4KPlayerAty.kt   reproductor nativo (proceso :x4k)
```

**Regla de oro de código:** la lógica testeable vive en `data/` como funciones **puras**
(`internal`), y la UI solo la pinta. Los `*WiringTest` leen el fuente para comprobar el cableado.

Documento hermano (léelo después de este): **`docs/ARQUITECTURA.md`** — invariantes, deuda y
cómo añadir una pantalla sin romper el mando.

---

## 4. Las doce reglas duras (romper una cuesta una OTA)

1. **No destruyas trabajo sin commitear.** El árbol casi siempre está sucio (el publicador deja
   el bump sin commit). **Prohibido** `git checkout`, `reset`, `stash`, `clean`, `restore` sobre
   archivos que no tocaste. Antes de cualquier operación de git: `git status` y `git diff`.
2. **No cambies la firma.** La llave histórica (`CN=Android Debug`, SHA-256 `9593cb0d…`) es la de
   producción: la base instalada no se arriesga. `keystore.properties` apunta a ella.
3. **No publiques OTA sin que el dueño lo pida.** «Compila y prueba» ≠ «publica». El publicador
   `tools/publish_ota.ps1` hace bump, tests, R8, firma y `gh release`: es irreversible en la
   práctica (los usuarios ya la ven).
4. **Zonas prohibidas** sin tests nuevos **y** prueba física: `data/offline/*`,
   `X4KTitan.play/stop/queryStatusUrls/beanPlayUrl`, `X4KPlayerAty.onCreate/play`,
   `VisionApp.onCreate`, y **todo** el proceso `:x4k`.
5. **Cada fix trae su test.** Si no se puede testear, se explica por qué en el reporte.
6. **Nada de emojis en la UI** (`UiNoEmojiTest` lo bloquea). Iconos siempre de `VisionIcons`.
7. **Tests de Compose:** añade casos a una clase con **sandbox existente**. Una clase nueva con
   su propio `@Config`/sandbox ya colgó la suite completa más de una vez.
8. **Cachés estáticas** con `resetMemoryForTests()` (lección de `GeeSportsSource`): Robolectric no
   reinicia estáticos entre clases.
9. **`server/api/Dockerfile` copia `.py` con lista explícita.** Si añades un módulo, añádelo a esa
   línea o el contenedor arranca con `ModuleNotFoundError`. (`sync` usa `COPY *.py`.)
10. **El `.env` del VPS debe terminar en salto de línea** antes de un append, o pega dos variables
    y tumba el worker. Operar con `scp` + `bash /tmp/x.sh`; **nunca** un `ssh` largo con comillas
    (PowerShell las destroza).
11. **R8 no puede pisar el espacio del motor:** `-repackageclasses 'visionplus'` + tarea
    `verifyEngineNamespace`. No tocar eso ni las keep rules sin correr la verificación.
12. **No reinicies `sync` a media pasada profunda** (deja el índice a medias) ni despliegues si hay
    una partida de Vision+ Play en curso (cuando exista: mata las salas).

---

## 5. Invariantes técnicas que no se negocian

1. **Un solo proceso de motor.** `:x4k` no admite dos programas distintos; el reproductor es
   residente (`singleTask`, tarea aparte) y reutiliza la vista. Descargas y reproducción compiten
   por el mismo proceso → `EngineDownloadGate` (diálogo «Pausar y ver ahora»).
2. **Sesión única por SN.** El portal patea (`aaa100083`). La app renueva proactivo cada 6 h, con
   cooldown 30 s, 10 s tras patada y **60 s de castigo** tras un mint fallido. El worker tiene su
   propio SN. Cada instalación se auto-da de alta (`mintOwnSlot`) y así deja de patear.
3. **Extracción del motor atómica y sellada:** `.part` + rename, manifiesto
   `engine-manifest.txt`, generaciones `x4k-rN` (una ruta nueva por reparación, porque ART
   reutiliza el `vdex` de la ruta vieja), rescate final con `InMemoryDexClassLoader`.
4. **Dos shells, una lógica.** Todo lo que se hace en móvil debe tener su equivalente en tele o
   una razón escrita para no tenerlo. Todo control D-pad tiene guardián.
5. **Perfiles por archivo.** `ProfileStore.prefsName`: el Principal conserva los nombres legados;
   los demás `base__p1`. El perfil activo se lee en cada acceso porque `:x4k` también escribe.
6. **La telemetría nunca desaloja un crash.** Colas separadas (`diag_queue` 40, `diag_sessions` 5).
7. **Sin emojis, sin marcas del proveedor.** El usuario final nunca ve «Xuper», «Magis» ni «Titan».
8. **La TV es el equipo modesto de referencia.** TCL Android 9, 2 GB, índice de 5.8 MB que tarda
   12.4 s en parsear: por eso la TV pide 24 títulos por fila y no 100.

---

## 6. Mapa de archivos por función

| Función | Archivos clave |
|---|---|
| Arranque / splash | `MainActivity.kt`, `ui/brand/VisionSplash.kt`, `data/XuperCatalog.kt` |
| Navegación | `ui/Navigation.kt`, `ui/PhoneApp.kt`, `ui/TvApp.kt` |
| Inicio | `ui/home/HomeScreen.kt`, `ui/tv/TvHomeScreen.kt`, `data/Popularity.kt` |
| Ficha | `ui/title/TitleScreen.kt`, `ui/tv/TvTitleScreen.kt`, `data/Related.kt` |
| Reproducción nativa | `ui/player/X4KPlayerAty.kt`, `data/xuper/X4KApi.kt`, `data/xuper/X4KTitan.kt` |
| Reproductor ExoPlayer | `ui/player/PlayerScreen.kt`, `ui/player/PlayerDialogs.kt` |
| TV en vivo | `ui/phone/LiveScreen.kt`, `ui/tv/TvLiveScreen.kt`, `LiveTaxonomy.kt` |
| Deportes | `ui/sports/*`, `ui/tv/TvSportsScreen.kt`, `data/gee/*` |
| Descargas | `data/offline/*`, `ui/DownloadsScreen.kt`, `ui/tv/TvDownloadsScreen.kt` |
| Perfiles | `data/profile/ProfileStore.kt`, `ui/profile/*`, `ui/settings/ProfilesCard.kt` |
| Continuidad | `data/sync/*`, `ui/settings/SyncSettingsCard.kt`, `ui/tv/TvSyncDialog.kt` |
| Control remoto | `data/remote/*`, `ui/remote/*` |
| Vision+ Home | `launcher/**`, `data/homefeed/*`, `home-contract/**` |
| Instalar en TV (ADB) | `data/adb/*`, `ui/settings/TvHomeSetupCard.kt` |
| Índice remoto | `data/sources/RemoteIndexSource.kt` |
| Diagnóstico | `data/diag/Diagnostics.kt`, `sync/diag.py`, `/v1/diag/*` |
| OTA | `data/AppUpdate.kt`, `ui/plugins/PluginUpdates.kt`, `tools/publish_ota.ps1` |

---

## 7. El backend del VPS

- Host y credenciales: **`AGENTS.md` → «Servidor de índice» / «Build / OTA / dispositivos»**.
  **No** las copies a archivos nuevos ni al repo.
- `/opt/streaming-backend`: `docker-compose.yml` (postgres, api, sync), `.env` (secretos),
  `api/`, `sync/`, `backups/`, `backup.sh`, `restore_check.sh`, `watchdog.sh`.
- La API **solo** escucha en `127.0.0.1:8100`; Caddy publica `https://…/catalogo/*`.
- Auth: header `X-Api-Key` en todo `/v1/*`; `POST /v1/config` pide además `X-Admin-Key`.
- Sin Redis, sin colas, sin microservicios. Un contenedor de API y uno de sync.
- Índice: `/v1/index` materializa el catálogo (52k) y se cachea por versión. **Cuidado con la
  memoria** (`mem_limit: 1g`): un `limitFilms` sin tope ya tumbó el contenedor por OOM.
- Workers: XTV, TMDB (enriquecido y traducciones), agrupador de temporadas, EPG (7 países),
  ESPN/deportes, GeeSports (enlaces), espejo de capítulos, marcadores de intro, banners, diagnóstico.
- Salud: `/healthz`, `/v1/diag/health`, `/v1/epg/health`, `/v1/game/health` (cuando exista),
  más el vigilante externo de GitHub Actions y el watchdog local.

Endpoints que ya existen (no los reinventes): `/v1/index`, `/v1/categories`, `/v1/category/{id}`,
`/v1/section/{name}`, `/v1/trending`, `/v1/schedule`, `/v1/search`, `/v1/banners`, `/v1/sports`,
`/v1/gee38/*`, `/v1/epg`, `/v1/epg/bulk`, `/v1/epg/health`, `/v1/markers`, `/v1/episodes`,
`/v1/sync`, `/v1/slots/*`, `/v1/diag`, `/v1/diag/summary`, `/v1/config`.

---

## 8. Cómo se trabaja aquí (estilo del dueño)

- **Un «lote» por sesión**, con alcance claro, tests y notas. No entregas a medias.
- El dueño escribe en español y espera **español**, directo, sin jerga innecesaria.
- **Evidencia, no adjetivos:** conteos de tests, tamaños, hashes, «verificado en el emulador /
  en la TCL», capturas cuando aplique. Si algo no se pudo probar, se dice.
- Al publicar: `ota_notes_X.Y.Z.md` con secciones y **firma «— ChronosBVRX»**.
- Al cerrar un lote: **actualizar `AGENTS.md`** (estado, versión, pendientes). Es el diario del
  proyecto y la memoria entre sesiones.
- El dueño decide producto. Si una idea tuya mejora el producto, se propone **después** de
  terminar lo pedido, no en medio.
- Si algo no cuadra con el código real: **para y pregunta**. Improvisar en producción es caro.

---

## 9. Trampas que ya costaron horas (no las repitas)

1. **lmkd en equipos de 1.5 GB.** No levantar `:x4k` en el arranque ni en segundo plano. El
   precalentamiento es diferido y solo en TV ≥ 1.8 GB.
2. **R8 vs motor.** Renombrar una clase a un nombre de 1–2 letras del motor lo ensombrece
   (`NoSuchMethodError` en `k4/x`). Ya está blindado; no lo deshagas.
3. **ART Android 12 (ZTE).** Rechaza un DEX idéntico y reutiliza el `vdex` de la ruta: por eso
   las generaciones `x4k-rN`.
4. **SharedPreferences no cruzan procesos.** Lo que escribe `:x4k` hay que leerlo del **archivo**
   (así lo hacen `PlaybackHistory` y `WatchNextHistory`).
5. **Los broadcasts `:x4k`→principal no llegan en POCO/HyperOS.** El puente es un **archivo**
   (`.done`/`.error`/`.progress`), no el broadcast.
6. **El portal topa 10 000 títulos por listado.** El resto se completa por colecciones y por el
   buscador (que pregunta al portal).
7. **TMDB comparte espacio de IDs entre cine y series.** Al cruzar, exige el tipo.
8. **CDN con caché por edge.** Un mismo póster puede dar 200 en un edge y 404 en otro: se usa
   `?v=<epoch>`.
9. **Dos `POST /v1/sync` o dos mints simultáneos se patean.** Locks y cooldowns ya resueltos.
10. **Compose TV:** `focusGroup` bloquea la flecha Arriba si se pone donde no toca; las listas
    perezosas no componen lo que no se ve (por eso el «pico» de 132 dp en el Inicio).
11. **El diálogo que nada abría.** «Corregir intro y outro» era placebo y se retiró. Si algo no
    está cableado a nada, se borra; no se deja aparentando.
12. **`.env` sin salto de línea final** pega la siguiente variable y tumba el worker.

---

## 10. Deuda conocida (no la escondas)

| Tema | Estado |
|---|---|
| Localización (es/en) | Pendiente: todo el texto está en el código. |
| Módulos Gradle | `app` sigue siendo grande; `:home-contract` y `:launcher` ya están aparte. |
| APK de ~82 MB | Aceptado (libs ARM + DEX del motor). Publicar por ABI rompería el enlace estable. |
| Firma de debug | Aceptado y respaldado; es la identidad de producción. |
| Prueba física pendiente | TCL/teléfono real para varios lotes (intro con controles cerrados, instalar en TV por IP, etc.). |
| Catálogo/motor de terceros | Riesgo estructural fuera del alcance del repo. |

---

## 11. El módulo nuevo: **Vision+ Play**

Plan completo, contrato, esquema, puntuación, presupuesto de rendimiento y fases:
**`docs/PLAN_JUEGOS.md`**. Resumen de una línea: trivia, charadas y actividades de grupo con
árbitro en el VPS, salas por código de 6 caracteres, contenido generado del catálogo.

No cambia ninguna pieza existente: añade `server/api/games.py`, `server/sync/trivia_bank.py`,
4 tablas y un paquete `data/games` + `ui/games` en la app. La TV entra en Fase 2 y nunca es
obligatoria.

---

## 12. Orden de lectura recomendado

1. **Este documento** (ya lo estás leyendo).
2. `AGENTS.md` — es enorme (~555 KB, el diario completo). **No lo leas de una sentada:** lee el
   bloque superior (lotes recientes, que es el estado real), y al final «Claves técnicas XTV»,
   «Archivos clave», «Build / OTA / dispositivos», «Servidor de índice» y «Pitfalls». El resto
   consúltalo por búsqueda cuando toques un tema.
3. `docs/ARQUITECTURA.md` — invariantes, deuda, cómo añadir pantalla.
4. `docs/REGRESSIONS.md` — qué se rompió antes y por qué.
5. `docs/PLAN_JUEGOS.md` — tu primer encargo.
6. `docs/PLAN_LAUNCHER.md`, `docs/PLAN_CONTROL_REMOTO.md`, `docs/PLAN_UI_PREMIUM.md` — historia
   de diseño de las piezas grandes.
7. `ota_notes_2.5.177.md` (y las dos anteriores) — cómo se redacta una entrega.

---

## 13. Tu primer encargo

Implementar **Fase 0 y Fase 1** de `docs/PLAN_JUEGOS.md` (backend puro + banco de 200 preguntas
+ MVP de trivia en móvil), con las suites en verde, **sin publicar OTA** y **sin commitear**.
Al terminar, reporta con la evidencia que pide el plan y espera instrucciones para Fase 2 (TV).

Si algo de este handoff no coincide con el repo, **el repo manda**: repórtalo y actualiza el
documento en el mismo lote.
