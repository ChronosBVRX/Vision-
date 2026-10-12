# Vision+ v2.5.48 — Depuración Integral de Fútbol en Vivo y Erradicación de Canales Falsos

### 🚀 Novedades y Mejoras Principales

#### 1. Erradicación de "TNT Series" en Partidos de Fútbol y Filtro Estricto de Elegibilidad
- **Diagnóstico**: En `SportsLinker.kt`, la regla de tokenización asignaba incondicionalmente la marca `"tnt sports"` a cualquier canal que contuviera `"tnt"` (como `TNT SERIES HD` o `TNT NOVELAS HD`). Al buscar partidos emitidos por TNT Sports o la plataforma Max, `SportsLinker` asignaba puntuación alta (+260) a `TNT SERIES HD` y lo seleccionaba como la opción principal para sintonizar, llevando al usuario a series en lugar de fútbol.
- **Solución Implementada**:
  - `cleanToks.contains("tnt")` ahora exige rigurosamente la presencia explícita de `"sports"`, `"sport"`, `"deportes"` o `"tnt sport"` para catalogar la marca como deportiva (`TNT SPORTS FHD`, `TNT SPORTS CHILE HD`, `TNT SPORTS PREMIUM`, `ESTADIO TNT`). Si contiene `"series"` o `"novelas"`, se aísla como `"tnt series"` o `"tnt novelas"` y queda descartado.
  - Implementado `isEligibleSportsChannel`: ningún canal perteneciente a géneros no deportivos (`Series`, `Novelas`, `Películas`, `Infantiles`, `Música`, `Religión`, `Documentales`) ni con palabras clave no deportivas (`series`, `serie`, `novelas`, `novela`, `cinema`, `movies`, `kids`) puede puntuar (`score = 0`).
  - Únicamente canales de la categoría `Deportes` y señales de televisión abierta nacional en `Entretenimiento` (Canal 5, Azteca 7, Telemundo, Universo) son elegibles.

#### 2. Catálogo Real Exclusivo ("Solo si de verdad tenemos el canal")
- **Eliminación Total de Fuentes Externas Fantasma**:
  - Se eliminó la inyección de `GeeSportsFallback.fallbackFor()`, la cual inundaba los partidos con hasta 12 enlaces de servidores externos caídos o no funcionales (`redima.pro`, `170.82.37.21`, etc.).
  - Ahora las opciones mostradas provienen única y exclusivamente de la lista de canales en vivo verificados de Vision+ (`xtvlive:`).
  - Si Vision+ no cuenta con el canal del partido, el sistema no inventa enlaces falsos y muestra honestamente `"Sin canal en tu lista · por [Cadena]"` / `"No está en tu lista"`, evitando pantallas negras y errores de reproducción.
- **Puntuación Positiva de Feeds Sudamericanos**:
  - Se eliminó la penalización de -70 puntos a feeds de Sudamérica (Chile, Argentina, Colombia, Perú), garantizando que señales legítimas presentes en Vision+ como `TNT SPORTS CHILE HD`, `TNT SPORTS PREMIUM`, `WIN SPORTS` o `TYC SPORTS` puntúen alto y sean sintonizadas de forma inmediata.

#### 3. Backend Limpio y sin Dilución de Canales
- En `server/sync/sports.py`, cuando LiveSoccerTV o ESPN ya determinaron las señales oficiales de un encuentro, no se contamina el registro anexando todos los canales de la liga.

### 🛡️ Pruebas y Validación
- **167 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%**.
- Nuevas pruebas de anti-regresión: `tntSeriesAndNonSportsChannelsNeverResolveForSports` y `quickResolveReturnsOnlyRealCatalogChannels`.
- **34 pruebas de backend (`pytest server/tests/`) pasando al 100%**.
- Binario de producción: `VisionPlus.apk` (v2.5.48, versionCode 284).
