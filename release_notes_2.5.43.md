# Vision+ v2.5.43 — Rediseño Integral UI/UX en Smart TV y Móvil / Optimización Low-End

### 📺 Rediseño Integral de UI/UX en Smart TV (Android TV Leanback & D-pad)
- **Selector Deslizable de Categorías con Foco D-pad (`TvCategoryPill`)**:
  - En todas las vistas principales de catálogo (**Películas**, **Series**, **Novelas**, **Anime**, **Fútbol**), incorporado selector de cápsulas deslizable horizontal con foco nativo de control remoto.
  - Píldora predeterminada canónica *"Todo / Destacados"* en degradado `VisionBlue`, acompañada por la lista completa de géneros dinámicos.
- **Feed Curado por Módulos y Cards Verticales 2:3 (`TvPosterCard`)**:
  - Sustitución de las tarjetas horizontales por pósteres verticales estilo streaming con relación de aspecto 2:3 (ancho 140.dp) y esquinas redondeadas.
  - **Badge de Calificación**: Cápsula translúcida (`#0B1220`) con borde neón/azul brillante (`VisionBlue`) en la esquina superior izquierda con rating numérico (`★ 8.2`, `★ 10`).
  - **Badge de Estado**: Cápsula de gradiente azul neón en la esquina superior derecha (`NUEVO`, `TOP 10`).
  - **Variante Top 10 con Número (Rank)**: Tipografía estilizada de gran tamaño (stroke outline con degradado azul plateado) superpuesta en la esquina inferior izquierda (`1..10`).
  - Encabezados de módulo con botón interactivo de foco D-pad **"Ver todo →"** (`TvSeeAllButton`) para conmutar directamente a la cuadrícula de cualquier categoría.
- **Vista de Catálogo Completo en Cuadrícula (Grid de 5 Columnas en Smart TV)**:
  - Al presionar una categoría o *"Ver todo →"*, se activa una cuadrícula adaptativa de 5 columnas (`GridCells.Fixed(5)`).
  - Encabezado secundario con botón **"← Volver a Todo"** y contador de títulos.
  - **Soporte Estricto de Tecla Atrás**: `BackHandler` regresa al feed curado antes de salir de la sección.
- **Biblioteca en TV (`TvLibraryScreen`)**:
  - Renovada a cuadrícula de 5 columnas con `TvPosterCard`, badges de rating numérico y estado.

### 📱 Experiencia Optimizada en Teléfonos y Tablets
- Cabecera fija con selector `CategoryPill`, cuadrícula de 3 columnas (móvil) y 5 columnas (tablets).
- Cards 2:3 con badges, Top 10 rank estilizado y botón *"Ver todo →"*.

### ⚡ Reglas Estrictas de Rendimiento y Memoria en Dispositivos Limitados (TV Box ZTE B866F, Chromecast)
- **Carga Limitada en Carruseles**: Máximo estricto de **15 elementos** por carrusel horizontal (`CAROUSEL_LIMIT = 15`) en `TvCategoryScreen`, `TvHomeScreen` y `TvCategoriesScreen`.
- **Paginación Infinita en Cuadrícula**: Carga por lotes de **24 elementos** (`GRID_BATCH_SIZE = 24`), evitando instanciación masiva de nodos y memory leaks.
- **Decodificación Coil Acotada**: Muestreo restringido a `size(320, 480)` con `crossfade(150)`, protegiendo el heap de RAM.

### 🛡️ Pruebas y Validación
- 153 pruebas unitarias de Android (`:app:testNormalDebugUnitTest`) pasando al 100%.
- Verificado y probado visualmente vía ADB en hardware real Smart TV Box (ZTE B866F V1.1).
