## Novedades de Vision+ v2.5.24

### 🚀 Arranque Instantáneo en Chromecast y Dispositivos Ligeros
- **Fin a la pesadez y demora al tocar el icono de la app**:
  - **Composición Diferida de Pantalla Principal (`MainActivity.kt`)**: Anteriormente, Jetpack Compose construía, medía y evaluaba todo el árbol composable de `TvApp` (carril lateral, carrusel de inicio, filas de películas/series y canales) simultáneamente con el Splash en el primer frame. En los procesadores quad-core de Chromecast con Google TV y Smart TVs, esto bloqueaba el hilo principal durante más de 2 segundos antes de poder mostrar el primer cuadro. Ahora, el primer frame solo renderiza `VisionSplash` en menos de 30 ms, mientras `TvApp` se monta suavemente en segundo plano.
  - **Carga de imágenes perezosa con `ImageLoaderFactory` (`VisionApp.kt`)**: Se implementó `ImageLoaderFactory` en la clase Application para que Coil construya el pipeline de imágenes y el caché en disco de 256 MB bajo demanda, liberando a `onCreate()` de accesos síncronos a la memoria flash eMMC durante el arranque en frío.
  - **Ventana de inicio nativa de 0 ms (`themes.xml`)**: El sistema operativo despliega de inmediato el fondo `#070B13` con el isotipo de Vision+ desde el primer milisegundo.

### ✨ Identidad Oficial de Marca y Logo Estático sin Fondo
- **Extracción del repositorio original (`ChronosBVRX/Vision-`)**:
  - Recuperado el diseño maestro oficial de Vision+.
  - Recorte de alta fidelidad que preserva el degradado cian-azul-morado, el botón *play* central y el resplandor "+".
- **Integración integral en la aplicación**:
  - **Splash Screen Compose**: Despliega `VisionFullLogo` nítido y centrado.
  - **Carril lateral Leanback (Smart TV)**: Despliega el isotipo puro `ic_vision_mark` cuando está colapsado y el logo horizontal `ic_vision_wordmark` al expandirse.
  - **Reproductor nativo y Handoff**: Sustituidos los textos planos por la identidad visual oficial en pantalla de espera y reproductor XTV.
  - **Banners oficiales de Android TV**: Banners panorámicos 16:9 renovados (`ic_banner.png` y `ic_launcher.png`) para el launcher de Google TV / Android TV.
- **Cero regresiones**: Blindaje contra crasheos en Fútbol y TV en Vivo (v2.5.23), Focus Trap en modales (v2.5.21), catálogo y TV online al 100%. 105 tests unitarios Android y 28 tests de backend en verde.
