# HANDOFF — Espera del reproductor (loading card) + trampa de regex ICU

> Sesión del 2026-10-14 (misma fecha del trabajo de subtítulos). Este documento es para el
> agente/sesión que sigue trabajando en los **subtítulos del proveedor** (`data/subs/`), que
> toca los mismos archivos que este cambio. **No borres ni renombres nada de aquí sin leer
> esta página.** Estado al escribir: suite Android **325/325 en verde**; build debug vc310
> instalado y reproduciendo en el A32; OTA **no** publicada (falta prueba física del usuario).

## 1. Qué se hizo (y dónde)

**Pedido del usuario**: la pantalla de espera del reproductor nativo decía «Preparando tu
película…» incluso al abrir una serie, y al cambiar de capítulo seguía mostrando el PRIMER
capítulo de la sesión (4x3 aunque se viera el 4x8). Ahora la espera habla del contenido real
y se refresca en cada cambio.

### Archivos y funciones exactas

| Archivo | Cambio |
|---|---|
| `ui/player/ControlsState.kt` | **Nuevo** (puro, todo `internal`): `data class LoadingCardText(title, subtitle, episodeName, accent)`, `fun resolveLoadingCardText(isLive, isSeries, baseTitle, rawTitle, seasonLabel, episodeIndex, episodeName)` y `fun cleanEpisodeName(name, episodeIndex)`. Nada existente se tocó. |
| `ui/player/X4KPlayerAty.kt` | Campos nuevos junto a `loadingOverlay` (~línea 91): `loadingTitleView`, `loadingSubView`, `loadingEpNameView`. `buildLoading()` (~768) ahora arma wordmark + spinner + **3 textos** y llama `applyLoadingContent(title)`. **Nuevo** `applyLoadingContent(rawTitle)` (~830): decide serie/película/vivo, pinta los 3 textos y muestra/oculta la línea del nombre del capítulo. |
| `ui/player/X4KPlayerAty.kt` | Llamadas a `applyLoadingContent(...)`: (a) `buildLoading` al montar (~814); (b) `playEpisode`, justo después de `topTitle?.text = title` (~2624); (c) `switchChannel` (zap en vivo, ~3020); (d) hilo que resuelve la lista de capítulos tarde en `onCreate` (~406). |
| `test/.../ui/player/PlayerLoadingCardTest.kt` | **Nuevo**, 8 guardianes de los textos (película/serie/vivo, temporada vs capítulo solo, nombres genéricos, índice 0, fallback de título). |
| `data/subs/SubsStore.kt` | **Solo los imports reparados** (constantes viven dentro de `object X4KApi`): el árbol no compilaba (`Unresolved reference APP_ID/APK_VER/HOST_MAIN/HOST_BACKUP/SPKG_VER`). No se tocó lógica de subtítulos. |
| `AGENTS.md` | Entrada de sesión con este mismo resumen. |

### Textos que produce `resolveLoadingCardText`

- **Película** → título + `Preparando tu película…` (sin acento).
- **Serie** → título base + `T1 · Capítulo 8` (azul `blueBright`, negrita) + **nombre real del
  capítulo** si el portal trae uno (si es «Capítulo 8»/«8» genérico se descarta).
- **En vivo** → canal + `Preparando la señal…`.

## 2. Reglas de oro (no romper)

1. `resolveLoadingCardText` / `cleanEpisodeName` son la **única fuente** de los textos de la
   espera. Si quieres otro texto, cámbialo ahí y actualiza `PlayerLoadingCardTest`; **no**
   hardcodees textos en `X4KPlayerAty`.
2. `applyLoadingContent(...)` se llama en **todo** camino que cambia de capítulo/canal antes
   de volver a mostrar `loadingOverlay`. Si agregas un camino nuevo (p. ej. auto-avance por
   subtítulos u otra cosa), llama `applyLoadingContent(curTitle)` también.
3. La detección de serie usa la regla canónica del proyecto:
   `isSeriesContent(epIds.size, seasonIds.size) || curSeries.isNotBlank()`. No inventes otra.
4. La etiqueta de temporada usa `seasonLabel(curSeasonIndex)` y **solo** se muestra cuando
   `seasonIds.size > 1` (una sola temporada → «Capítulo N» a secas).
5. `loadingEpNameView` queda `GONE` cuando no hay nombre real de capítulo.

## 3. Convivencia con el trabajo de subtítulos

El trabajo de subtítulos vive **en el mismo archivo** (`X4KPlayerAty.kt`). Al momento de este
handoff ya convive bien: `playEpisode` trae, justo debajo de `applyLoadingContent(title)`,
la parte de `disableSubs(...)` / `subsEntries = emptyList()` / `subsLoaded = false`, y el
overlay de subtítulos se pinta sobre el video (no toca la tarjeta de espera). Reglas:

- La espera (`loadingOverlay`) se muestra en `mountUi`/`playEpisode`/`switchChannel`; si el
  feature de subs añade su **propio** estado de espera, que use estas mismas funciones en vez
  de crear otra tarjeta.
- La línea de nombre de capítulo de la espera usa `epNames` (la misma lista que usa el
  selector de capítulos). Si cambias la semántica de `epNames`, revisa `cleanEpisodeName`.
- No renombres `applyLoadingContent`, `resolveLoadingCardText`, `cleanEpisodeName`,
  `LoadingCardText` ni `PlayerLoadingCardTest` sin actualizar TODOS los llamados y los tests.

## 4. ⚠️ Trampa de regex de Android (ICU) — hallazgo crítico

Un build **2.5.74 (R8)** se caía al abrir CUALQUIER título en `:x4k`
(`ExceptionInInitializerError` en el `<clinit>`, `Pattern.compile`). Causa: el patrón de
etiquetas karaoke del parser SRT.

- **Ilegal en Android (ICU)**: `` `\{\\[^}]*}` `` — el `}` final sin escapar. En JVM de
  escritorio compila; en Android **no** (verificado ejecutando un dex en el propio teléfono
  con `dalvikvm`: ese patrón → BAD).
- **Válido (ya en `data/subs/Subs.kt`, `KARAOKE_RX`)**: `` `\{\\[^}]*\}` `` — con el `}` final
  escapado (→ OK en el mismo dex).

Reglas para el agente de subs:

1. **No** reverting ese patrón nunca. Cualquier `Regex(...)` nuevo se valida **en Android**,
   no solo con `:app:testNormalDebugUnitTest` (Robolectric corre en JVM y no reproduce ICU).
2. **Cualquier 2.5.74 construido antes de ese fix debe rehacerse** antes de instalarse o
   publicarse: se cae al reproducir cualquier título para todos.
3. Truco de validación rápida en el teléfono (A32 con adb):
   `javac` el patrón → `d8` a dex → `adb push` → `adb shell dalvikvm -cp /data/local/tmp/x.dex Clase`.

## 5. Cómo verificar (sin pisar al otro)

```powershell
# Solo los guardianes de la espera (rápido)
.\gradlew.bat :app:testNormalDebugUnitTest --tests "com.chronosbvrx.visionplus.ui.player.PlayerLoadingCardTest"

# Suite completa (antes de cualquier OTA)
.\gradlew.bat :app:testNormalDebugUnitTest
```

Estado al escribir: **325 tests / 0 fallos** (incluye `SubsTest` y `SubsStoreTest`).
Prueba física pendiente del usuario: abrir una serie, cambiar de capítulo un par de veces
(«⏭ Sig. episodio», selector, auto-avance) y ver la espera («Serie» + «T1 · Capítulo N») y
la barra superior del reproductor actualizarse con cada capítulo.

## 6. Ojo con el entorno compartido

Al momento de este handoff había **dos procesos trabajando en el mismo repo y el mismo A32
a la vez**: se vieron `gradlew --stop` cruzados, bloqueos de `test-results/bin/output.bin`
por builds simultáneos, e instalaciones encima (vc309 debug ↔ vc310 release) mientras el
teléfono también se navegaba desde otra sesión. Si algo truena raro en Gradle/adb, revisa si
hay otro `gradlew`/adb en curso antes de culpar al código.
