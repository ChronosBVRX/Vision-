# Vision+ v2.5.38 — Restauración de Duración Total y Scrubber Interactivo de Búsqueda Fina

### ⏱️ Visualización Inmutable de Duración Total en Películas y Series
- **Cálculo Robusto Multiorigen**: Se corrigió el casteo de reflexión en el listener nativo Titan (`bd.a`), permitiendo que eventos emitidos como enteros por el decodificador nativo actualicen la duración y el progreso correctamente.
- **Resolución Enriquecida del Portal**: Nuevo extractor universal de metadatos de duración que audita campos variantes del portal (`duration`, `playLength`, `totalTime`, `runtime`, `length`, `videoTime`), así como la respuesta de `startPlayVOD` y el historial local.
- **Propagación a Reproductores**: Duración propagada íntegramente desde la ficha editorial (`TitleScreen` y `TvTitleScreen`) a los reproductores Titan y Compose, eliminando valores en cero (`--:--` o `00:00`) y habilitando la barra de navegación de forma inmediata.

### 🎯 Scrubber Interactivo de Búsqueda Fina en Barra de Navegación
- **Aceleración Dinámica Progresiva (D-Pad)**: Navegar sobre la barra de progreso ahora acelera inteligentemente según el tiempo que se mantenga presionada la tecla (10s–30s base ➔ 60s tras 1s ➔ 120s tras 2s ➔ 300s tras 5s).
- **Vista Previa Fluida a 60 FPS sin Tartamudeos**: La posición y el timestamp visual se actualizan en tiempo real mientras se recorre la barra, desacoplando el salto real (`seekTo`) con debounce de 350 ms para no saturar los búferes del decodificador de hardware.
- **Arrastre Táctil Preciso en Móvil**: La barra calcula con exactitud el minuto objetivo al deslizar el dedo sobre la línea de tiempo.

### 🛡️ Política Anti-Regresiones y Calidad
- Preservada al 100% la directiva de despertar limpio de controles con D-pad sin acciones accidentales (v2.5.37).
- 140 pruebas unitarias de Android y 33 pruebas de backend pasando al 100%.
