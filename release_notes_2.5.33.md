# Vision+ v2.5.33 — Navegación D-Pad Fluida en Reproductores, Barra de Progreso y Restauración de Menús

Esta actualización analiza y corrige a profundidad todos los problemas de navegación con control remoto (D-pad) en los reproductores de Vision+ (tanto en el reproductor nativo Titan/ijkplayer para VOD y TV en vivo como en el reproductor Compose/ExoPlayer), solucionando de raíz el funcionamiento de la barra de progreso (scrubber) y erradicando la pérdida de foco al descartar menús y selectores.

### 🌟 Correcciones y Mejoras Clave

1. **⏱️ Navegación Interactiva en la Barra de Progreso (Scrubber) en Smart TV (`X4KPlayerAty.kt`)**:
   - Implementada la función `performSeekBy(deltaMs: Long)`, que actualiza en tiempo real el motor de reproducción nativo `X4KTitan.seekTo`, sincroniza el contador textual `timeNow` y ajusta el progreso de la barra `SeekBar`.
   - Conectado `seek.setOnKeyListener` en el `SeekBar` para capturar el control remoto en Smart TV:
     - **Flechas Izquierda / Derecha**: Rebobina y adelanta 10 segundos al instante (`performSeekBy(±10_000L)`) con respuesta inmediata y reinicio del temporizador de auto-ocultación.
     - **Flecha Abajo**: Traslada limpiamente el foco al botón central de reproducción (`playPauseBtn.requestFocus()`).
     - **Botón Centro / OK**: Conmuta reproducción y pausa (`togglePlay()`).
   - Se erradicó el problema donde el poller periódico de progreso sobreescribía la barra en 500 ms y anulaba los saltos de tiempo del usuario.
   - En `onKeyDown`, presionar Flecha Izquierda o Flecha Derecha con los controles ocultos ahora ejecuta el salto de 10 segundos de inmediato y despierta el OSD informativo.

2. **🎯 Barra de Progreso Accesible en Reproductor Compose (`PlayerScreen.kt`)**:
   - Incorporados modificadores `.focusable()` y `.focusProperties { down = playPauseFocus; left = barFocus; right = barFocus; up = barFocus }` al componente `SeekBar`, permitiendo que la navegación hacia arriba desde los botones de transporte aterrice directamente en el scrubber.
   - Respuesta visual refinada: la pista se ensancha dinámicamente a 8 dp y el thumb cambia a blanco brillante al recibir el foco del D-pad.

3. **🔄 Restauración Automática de Foco al Cerrar Menús y Diálogos**:
   - **`X4KPlayerAty.kt`**: Implementado el seguimiento `lastPickerSourceBtn` en selectores de pistas de audio, subtítulos, episodios de series, lista de canales en vivo y diálogo de información. Al cerrar el selector (pulsando Atrás, seleccionando una opción o pulsando Cerrar), el foco se restaura automáticamente en el botón que lo originó (`audioBtn`, `subsBtn`, `episodesBtn`, `channelsBtn`, `infoBtn`), o en su defecto a `playPauseBtn`.
   - Lo mismo se garantizó al descartar la tarjeta de siguiente episodio (`hideNext()`) y el diálogo de reanudación (`hideResumeDialog()`).
   - **`PlayerDialogs.kt`**: Erradicado el confinamiento interno erróneo (`modalFocusTrap`) en el cuerpo de los diálogos de audio/subtítulos y marcadores que impedía que el cursor del control remoto descendiera hasta los botones de acción «Cerrar» y «Cancelar».
   - **`PlayerScreen.kt`**: Añadidos `LaunchedEffect(showTracks)` y `LaunchedEffect(showMarkers)` para restaurar el foco a `tracksFocus` y `markersFocus` tras cerrar los paneles.

4. **🧪 Pruebas Unitarias Automatizadas**:
   - Incorporadas pruebas unitarias `calculateSeekTargetClamping` y `resolveDismissFocusTargetPreservesSourceButton` en `PlayerNavigationTest.kt`.
   - **125 pruebas unitarias de Android** (`:app:testNormalDebugUnitTest`) y **33 pruebas de backend** (`pytest server/tests`) pasando al 100% en verde.
