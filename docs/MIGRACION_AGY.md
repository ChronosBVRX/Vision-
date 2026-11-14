# Contexto de migración — Vision+ → Antigravity

**Generado:** 2026-11-14 · **Origen:** sesión de OpenCode en `C:\Users\Axel Rosete\Desktop\Proyectos\Vision Laptop`
**Propósito:** que un agente nuevo en Antigravity retome el proyecto exactamente donde quedó, sin re-descubrir nada.

> **Cómo usar este archivo:** abre en Antigravity la carpeta `C:\Users\Axel Rosete\Desktop\Proyectos\Vision Laptop\Vision-`
> (el repo). Antigravity leerá `AGENTS.md` automáticamente; dile además:
> *"Lee docs/MIGRACION_AGY.md completo y continúa con la Tarea Abierta #1"*.

---

## 0. Entorno (obligatorio antes de compilar)

El repo **no** funciona sin estas dos variables. `local.properties` ya existe y está en `.gitignore`.

```powershell
$env:JAVA_HOME    = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\Axel Rosete\AppData\Local\Android\Sdk"
cd "Vision-"
```

| Comando | Para qué |
|---|---|
| `.\gradlew.bat :app:compileNormalDebugKotlin --console=plain` | Verificación rápida de compilación (~1 min) |
| `.\gradlew.bat :app:testNormalDebugUnitTest --tests "<Clase>" --console=plain` | Un test puntual |
| `.\gradlew.bat :app:testNormalDebugUnitTest --console=plain` | Suite completa (~1000 tests) |
| `.\gradlew.bat :app:assembleNormalRelease --console=plain` | APK release con R8 |

**Fallo conocido y NO relacionado:** `EncryptedVaultDataSourcePortableTest > streamsAndSeeksPortableContainer`
falla por carga de runtime nativo en este equipo. Ignóralo; no es una regresión.

**Distribución:** solo GitHub Releases. Nunca Play Store. `gh` CLI ya está autenticado como `ChronosBVRX`.

---

## 1. Qué es Vision+

App Android de streaming personal (marca **Vision+**, paquete `com.chronosbvrx.visionplus`, autoría **ChronosBVRX**).
Catálogo ≈52 000 títulos (películas, series, novelas, anime, kids) y ≈1 000 canales en vivo, servido por el
portal **XTV** (tenant `masnew`) y espejado en un VPS propio (`visionplusv2.duckdns.org`).
Reproduce con el motor nativo del proveedor (Titan/Ranger, ARM) inyectado como DEX adicional en el APK.

**Dos shells de UI en el mismo APK** — este es el eje del trabajo pendiente:

| Shell | Entrada | Archivos | Peso |
|---|---|---|---|
| **TV** (D-pad, 10-foot UI) | `ui/TvApp.kt` | `ui/tv/*` (30 archivos) + `ui/sports`, `ui/title`, `ui/settings` | **649 KB** |
| **Móvil** (táctil) | `ui/PhoneApp.kt` | `ui/phone/*` (7 archivos) + `ui/home/HomeScreen.kt` | **149 KB** |

El desbalance es real y es exactamente lo que el dueño quiere cerrar.

---

## 2. Estado verificado hoy (2026-11-14)

- **Versión publicada:** `2.6.37` — `versionCode 530`
- **Release:** https://github.com/ChronosBVRX/Vision-Releases/releases/tag/v2.6.37
- **Assets:** `VisionPlus.apk` (119.4 MB), `VisionPlusHome.apk` (3.3 MB), `version.json`
- **`releases/latest/download/version.json`** resuelve a 2.6.37 ✔
- **Downloader del usuario:** `4999823` (redirige siempre al último APK)
- **Firma:** clave histórica, verificada automáticamente por el script de publicación
- **Commit local:** `c9d4738` (no se ha podido hacer push, ver §9)

---

## 3. Reglas duras del producto (no romper)

1. **Anime = solo animación japonesa.** Clasificación únicamente por `kind == "anime"` o tag "Animación Japonesa". Nunca mapear kids → anime.
2. **Kids vive DENTRO de Series y Movies**, como submenú (`kids-movies`, `kids-series`) y como chip "Infantil". No es una sección de primer nivel.
3. **Deportes muestra todos los eventos de todos los deportes.** El HUD estilo FIFA (OK sobre un stream) muestra estadísticas por deporte, no solo fútbol.
4. **La búsqueda en Vivo de TV** debe permitir bajar con D-pad desde los resultados al teclado en pantalla.
5. **"Ver más" en Películas/Anime no debe cortar la carga del catálogo.** Tope de paginación: 1200 en dispositivos de gama baja, 4000 en normales.
6. **El selector de perfiles** es modal con degradado→transparente, compacto alrededor del área útil, sin perder foco del D-pad.
7. **La UI de TV se toca con guantes blancos**: cada cambio de foco/landing se prueba con D-pad. Un cambio "bonito" que mata el foco es un bug.

---

## 4. Mapa del repo (`Vision-\`)

```
app/                        App Android (Kotlin + Compose, 2 shells)
  src/main/java/com/chronosbvrx/visionplus/
    data/                   Capa de datos
      xuper/X4KApi.kt       Portal XTV: sesión, licencias v10, columnas, homeRows
      gee/GeeSportsSource.kt Agenda deportiva + logos de equipos/eventos
      sports/SportsStatsSource.kt  Estadísticas ESPN multi-deporte
      sources/RemoteIndexSource.kt Índice remoto, categoryPage, section
      SectionPaging.kt      Reglas de paginación/techos
    ui/
      tv/                   Shell TV (30 archivos, 649 KB)
      phone/                Shell móvil (7 archivos, 149 KB)  ← ATRASADO
      home/HomeScreen.kt    Home compartido (38 KB)
      sports/               Piezas compartidas de deportes (110 KB)
      title/ player/ settings/ components/ theme/ ... compartidos
home-contract/              Launcher Vision+ Home (APK aparte, 3.3 MB)
server/                     Backend FastAPI + Postgres (VPS)
  api/app.py                API pública
  api/vip.py                Broker de licencias (espeja los cambios de X4KApi)
  sync/sports.py            Sync ESPN → Postgres (home_logo, away_logo, league_logo)
  sync/schema.sql           Esquema
tools/publish_ota.ps1       Pipeline de release (bump + R8 + inyección DEX + firma + gh)
docs/                       Documentación histórica (ver HANDOFF_MAESTRO.md, desactualizado)
version.json                Metadatos de OTA
ota_notes_*.md              Notas por versión (una por release)
```

---

## 5. Pipeline de release (usar tal cual)

```powershell
cd "Vision-"
powershell -ExecutionPolicy Bypass -File ".\tools\publish_ota.ps1" `
  -Version "2.6.38" -NotesFile "ota_notes_2.6.38.md" -SkipTests
```

Qué hace el script, en orden:
1. Bump de `versionName` (+1 patch) y `versionCode` (+1) en `app/build.gradle.kts`.
2. Build release R8 de **Vision+** y de **Vision+ Home**.
3. Inyecta el motor (3 DEX del proveedor) DESPUÉS de los DEX propios (`classes3..5.dex`).
4. Verifica firma histórica en ambos APK.
5. Publica el release con `gh` en `ChronosBVRX/Vision-Releases` con los 3 assets.

**Flujo correcto antes de publicar:**
`compilar` → `tests puntuales` → escribir `ota_notes_X.md` → publicar → `git add`/`git commit` → verificar con `gh release view`.

---

## 6. Historia reciente (conocimiento que NO está en los docs viejos)

### 2.6.34 (vc527) — Licencias FHD
- `authType` = `""` para películas, `"1"` para series.
- `seriesContentId = contentId` en películas; **omitir** `episodeNumberList` en películas.
- Sincronizado `columnFor`, `slbFor` (tenant `masmobile`) y columnas del broker `79715`/`79716`.

### 2.6.35 (vc528) — Instalar en otra TV
- Pestaña/tutorial con el código Downloader `4999823`, QR de WhatsApp (`wa.me`), copiar al portapapeles.
- Archivos: `ui/share/ShareInstallTutorial.kt`, `ui/tv/TvInstallTab.kt`.

### 2.6.36 (vc529) — Bloque grande de UX
- Modal de perfiles con degradado + endurecimiento de foco (`ui/tv/TvProfilesDialog.kt`).
- Búsqueda de Vivo en TV a dos columnas, teclado alcanzable con Abajo (`ui/tv/TvLiveScreen.kt`).
- `GeeSportsState.scoped()`: muestra todos los eventos por categoría.
- `SportsStatsSource`: resolución de scoreboard ESPN + stats para NBA/MLB/NFL/NHL.
- Anime restringido a japonés; techos de paginación subidos; fallback de sección `xtv-$section-all`.
- Kids como entradas `kids-movies` / `kids-series` + chip "Infantil".
- **Bug pendiente detectado después:** `Paper` de "Ver más" podía detener la carga (ya mitigado con techos).

### 2.6.37 (vc530) — Logos de deportes + catálogo infantil (último release)
- **Causa real de los logos faltantes:** `safeImage()` forzaba `http://` → `https://`, y muchos CDN de deportes son HTTP puro → fallo de handshake silencioso. Ahora se respeta el protocolo (la app ya tiene `usesCleartextTraffic="true"`).
- **Segunda causa:** solo 86/251 eventos traen `home_logo`/`away_logo`, pero **251/251** traen `logo` del evento. Se añadió respaldo: `TvSportsCrest(logo, fallbackLogo = event.logo, ...)` y equivalentes en `TvSportsScreen.kt` / `TvSportsParts.kt`.
- **Kids vacío, causa 1:** `homeRows()` no guardaba `kind` en los films del portal → `kind = ""` → los filtros `it.kind == "kids"` descartaban todo. Corregido con `kindOfCode` derivado del `columnCode`.
- **Kids vacío, causa 2:** la app solo miraba los rows de Home. Ahora `kids-movies` / `kids-series` / chip "Infantil" conectan directo a **`xtv-kids-all`** (1 404 títulos reales en el backend) con paginación continua.
- Archivos tocados: `X4KApi.kt`, `GeeSportsSource.kt`, `TvCategoryScreen.kt`, `TvSportsParts.kt`, `TvSportsScreen.kt`.

> Nota: `ui/phone/SectionScreen.kt` también tiene filtros de kids, pero **la paridad móvil de estas
> dos correcciones no se auditó** — es parte de la Tarea #1.

---

## 7. Backend / portal / broker (datos que cuestan horas redescubrir)

- **Portal:** `masnew`; tenant móvil `masmobile`.
- **Columnas del portal:** películas `79715`, series `79716`. Columnas free: `87887`, `87888`, `87889`.
- **Categorías maestras útiles:** `xtv-kids-all` (1 404), `xtv-movies-all`, `xtv-series-all`; shelves de Home: `masnew_featured`, `masnew_movies`, `masnew_series`, `masnew_kids`, `masnew_anime`.
- **API del VPS:** `https://visionplusv2.duckdns.org`, header `X-Api-Key: fb2244441f690185f861470423afb43278c3b19d31b14115`.
  - `/v1/category/{id}?page=&pageSize=` → `{id,page,pageSize,total,films}`
  - `/v1/gee38/events` → agenda deportiva (campos `home_logo`, `away_logo`, `logo`, `league_logo`)
- **Cuenta de cortesía para probar licencias:** sesión `VIP_SN` por defecto, `user_id=967111326`.
  Las cuentas auto-fabricadas devuelven 0 medias; hay que usar la de cortesía.
- **Broker:** `server/api/vip.py` debe espejar cualquier cambio de `X4KApi.kt`. `server/tests/test_vip.py` (7 tests) pasa.

---

## 8. TAREA ABIERTA #1 — Paridad de UI móvil con TV  ✅ *(objetivo principal)*

**Pedido textual del dueño:** *"Analiza toda mi apk, necesito que mi interfaz de celular esté a la par de la versión de TV, creo que UI/UX nos hemos quedado muy atrás."*

### Diagnóstico cuantitativo
`ui/tv` = 649 KB / 30 archivos · `ui/phone` = 149 KB / 7 archivos (+ Home compartido 38 KB).
El móvil usa el `HomeScreen` compartido y pantallas funcionales, pero **no tiene** las capas de
presentación que se construyeron para TV.

### Lo que existe SOLO en TV (brecha a cerrar)
| Capacidad | Archivo TV | Estado en móvil |
|---|---|---|
| Héroe cinematográfico + ambiente/crestas | `TvHero.kt`, `TvHomeScreen.kt` | Parcial, sin ambiente |
| Sistema de foco/landing/restore | `TvFocus.kt`, `TvLayout.kt` | N/A (táctil) pero falta jerarquía visual equivalente |
| Riel lateral con secciones | `TvSideRail.kt`, `TvTopBar.kt`, `MoviesHubPanel`/`SeriesHubPanel` | Drawer plano, sin hubs |
| Deportes: mosaicos, HUD y stats por deporte | `TvSportsScreen.kt`, `TvSportsParts.kt`, `TvGeeSportsTabs.kt` | `LiveScreen` + piezas compartidas, sin HUD |
| Selector de perfiles modal degradado | `TvProfilesDialog.kt` | Sin equivalente |
| Búsqueda de Vivo con teclado | `TvLiveScreen.kt` (`TvLiveSearchDialog`) | `LiveScreen` básico |
| Pestaña "Instalar en otra TV" | `TvInstallTab.kt`, `ShareInstallTutorial.kt` | Sin pestaña |
| Pantallas de título/settings premium | `TvTitleScreen.kt`, `TvSettingsScreen.kt` (129 KB en `ui/settings`) | `SectionScreen`, `SettingsScreen` (28 KB) |
| Party / juegos / remoto | `ui/games`, `ui/remote` | Rutas existen, UI móvil incompleta |

### Estrategia recomendada (por fases, cada fase = 1 release)
1. **Fase A — Sistema visual móvil.** Tokens de `ui/theme` compartidos, tipografía/escala, radios, elevaciones, skeletons de carga y transiciones. Sin esto, todo lo demás se ve inconsistente.
2. **Fase B — Home móvil.** Héroe con gradiente + rail de "continuar viendo", hubs de Películas/Series con chips (incluido "Infantil"), carruseles con snap y paginación viva.
3. **Fase C — Deportes móvil.** Mosaicos con escudos (ya hay respaldo de `event.logo`), tabs por deporte, hoja de detalle con HUD de stats.
4. **Fase D — Vivo y perfiles.** Buscador de canales con teclado accesible, EPG, selector de perfiles modal compartido con TV.
5. **Fase E — Ajustes/Instalar/Compartir.** Reutilizar `ShareInstallTutorial` en móvil, código Downloader, QR de WhatsApp.

**Regla de reutilización:** extraer a `ui/components` / `ui/sports` / `ui/profile` lo que pueda ser
compartido por ambos shells en vez de duplicar. Nunca romper la UI de TV al extraer.

**Criterio de aceptación:** cada pantalla móvil debe verse intencional (no "Material por defecto"),
tener estados de carga/error/vacío, y respetar las reglas §3 (anime, kids, deportes).

### Auditoría ya hecha (hallazgos verificados, pendientes de corregir)

**A1 — Kids en móvil sigue limitado (bug real, prioritario).**
El chip "Infantil" **sí existe** en `ui/phone/SectionScreen.kt` (líneas 201-202), pero:
- filtra solo en memoria: `rowsFinal.flatMap { it.films }.filter { it.kind == "kids" }` (líneas 263-264);
- la paginación usa `"xtv-$section-all"` (línea 495-497), es decir `xtv-movies-all` / `xtv-series-all`, **no** `xtv-kids-all`.

Resultado: en el teléfono el catálogo infantil queda acotado a lo que traiga el Home, no a los 1 404 títulos.
**Fix esperado:** replicar en `SectionScreen.kt` la lógica que ya tiene `TvCategoryScreen.kt`
(estado `kidsServerFilms` + `LaunchedEffect` que llama `RemoteIndexSource.categoryPage(context, "xtv-kids-all", 1, 60)`,
y `targetCatId` que resuelve a `xtv-kids-all` cuando la categoría es "Infantil").

**A2 — Deportes en móvil sin respaldo de logo.** La pieza compartida `ui/sports` aún no aplica
`event.logo` como fallback cuando `homeLogo`/`awayLogo` vienen vacíos (el equivalente a
`TvSportsCrest(logo, fallbackLogo = event.logo, ...)`). Solo 86/251 eventos traen escudos propios.

**A3 — `live/` y `profile/` son mínimos** (13 KB y 11 KB) frente a sus equivalentes TV: el modal
de perfiles y el buscador de canales no están portados al shell táctil.

---

## 9. Bloqueos conocidos

1. **`git push origin main` falla con 403.** El repo `ChronosBVRX/Vision-` está **archivado** (read-only). Los commits `73cdb4c`, `fd90f25`, `8bc197e`, `c9d4738` existen solo en local. Opciones: desarchivar el repo, o crear un remoto nuevo. **Preguntar al dueño antes de tocar remotos.**
2. `EncryptedVaultDataSourcePortableTest > streamsAndSeeksPortableContainer` falla por entorno (no es regresión).
3. `docs/HANDOFF_MAESTRO.md` está congelado en 2.5.177: **no confiar en sus números**, solo en su arquitectura.
4. **`AGENTS.md` está en `.gitignore`** (por eso nunca apareció en el repo aunque el script de OTA lo menciona). Vive solo en el árbol de trabajo; no intentes commitearlo sin `-f`.

---

## 10. Verificación antes de publicar

- Compila: `:app:compileNormalDebugKotlin` sin warnings nuevos.
- Tests puntuales que deben pasar: `ShareTutorialTest`, `FreeQualityTest`, `server/tests/test_vip.py`.
- Smoke en dispositivo/emulador: Home TV (foco), Deportes (logos visibles), Kids (≥100 títulos y "Ver más" no se detiene), Vivo (búsqueda con D-pad), Perfiles (foco).
- Post-release: `gh release view vX.Y.Z -R ChronosBVRX/Vision-Releases` y comprobar `version.json` de `latest`.

---

## 11. Primeros tres pasos al abrir en Antigravity

1. Leer este archivo completo + `app/src/main/java/com/chronosbvrx/visionplus/ui/phone/` y `ui/HomeScreen.kt`.
2. Ejecutar `:app:compileNormalDebugKotlin` para confirmar que el entorno quedó bien.
3. Empezar por la **Fase A** de la Tarea #1 (sistema visual móvil) y proponer el alcance de la primera release antes de escribir código.
