# Vision+ v2.5.46 — Menú Simplificado (6 Pilares), Brand Hubs y Corrección de Foco D-Pad al Regresar

### 🚀 Novedades y Mejoras Principales

#### 1. Corrección Definitiva de Pérdida de Foco D-Pad al Regresar ("Back Focus Orphan")
- **Diagnóstico**: Al abrir una película desde `TvHomeScreen` o `TvCategoriesScreen` y regresar con el botón "Atrás", el árbol de composición restauraba el scroll dejando el Hero Billboard fuera de pantalla (`offscreen`). Al no existir `FocusRequester` registrado en las tarjetas de catálogo para retorno, y al no registrar `onFilmPick(it.id)`, el sistema de navegación perdía el foco completamente, bloqueando el control remoto.
- **Solución Implementada**:
  - Implementado `returnFilmId` y `returnRequester: FocusRequester` en `TvHomeScreen` y `TvCategoriesScreen` con reintento activo de 12 ciclos (`repeat(12)`) y fallback a `landing.request()`.
  - Propagado a `HeroBillboard`, `HomePosterRow` (Top 10, Estrenos y filas curadas) y `ContinueRow`.
  - Registrado `onFilmPick(it.id)` en `TvApp.kt` para retorno instantáneo a la tarjeta activa.

#### 2. Arquitectura de Navegación Simplificada de Clase Mundial (Menú de 6 Pilares)
- Riel de navegación lateral en Smart TV (`TvApp.kt`) optimizado de 9 ítems saturados a **6 pilares esenciales**:
  1. `🔍 Buscar` (`Route.Search`)
  2. `🏠 Inicio` (`Route.Home` con retorno de pila `goTab`)
  3. `🎬 Películas` (`Route.Section("movies", "Películas")`)
  4. `📺 Series` (`Route.Section("series", "Series")`)
  5. `📡 En vivo` (`Route.Live`)
  6. `🔖 Mi lista` (`Route.Library`)
  Y anclado al fondo: `⚙️ Opciones` (`Route.Settings`).
- Menú lateral en Móvil y Tablets (`PhoneApp.kt`) homologado bajo los mismos 6 pilares de consumo.

#### 3. Fila de Accesos Rápidos a Colecciones y Especiales ("Brand Hubs" estilo Disney+/Max)
- En Smart TV (`TvHomeScreen.kt`), agregada fila horizontal `TvQuickHubsRow` inmediatamente después del Hero Billboard:
  - `[ ⚽ Fútbol en Vivo ]` (Acceso directo con foco D-pad a `Route.Sports`)
  - `[ 🌸 Anime ]` (Acceso directo a `Route.Section("anime", "Anime")`)
  - `[ 🎭 Novelas ]` (Acceso directo a `Route.Section("novelas", "Novelas")`)
  - `[ 📂 Categorías ]` (Acceso directo a `Route.Categories`)
- En Móvil (`HomeScreen.kt`), expandida la fila `SELECTIONS` para acceso directo en 1 toque a Películas, Series, Fútbol, TV en Vivo, Anime, Novelas y Categorías.

### 🛡️ Pruebas y Validación
- **163 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%**.
- Nuevas pruebas de anti-regresión: `homeDownToFilmAndBackKeepsFocus` y `categoriesBackKeepsFocus`.
- Binario de producción firmado y verificado: `VisionPlus.apk` (v2.5.46, build 282).
