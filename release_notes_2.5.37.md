# Vision+ v2.5.37 — Política Universal Anti-Regresiones de Controles D-Pad

## Novedades y Correcciones

### 1. Política Universal Anti-Regresiones para Controles D-Pad en Todos los Reproductores
- **Cero Acciones Accidentales al Primer Clic**:
  - En Smart TV y dispositivos controlados con control remoto (incluyendo TV Boxes AOSP), cuando el menú de navegación está oculto o ningún control tiene foco activo, presionar cualquier tecla del D-pad (`OK/Centro`, `Enter`, `Izquierda`, `Derecha`, `Arriba`, `Abajo`, `Menu`, `Canal +/-`, `Info`, `Guía`) **únicamente hace visible el menú de navegación y posiciona el cursor** en el botón principal.
  - Erradicado cualquier comportamiento previo donde el primer clic pausaba, adelantaba/retrasaba 10 segundos, cambiaba de canal, saltaba intro o salía del reproductor.
- **Motor Centralizado de Decisión (`ControlsState.resolvePlayerKeyDownAction`)**:
  - Unificación de la lógica para todas las variantes de reproductor de la app:
    1. `X4KPlayerAty` Live (Canales en vivo nativo Titan en proceso `:x4k`).
    2. `X4KPlayerAty` VOD (Películas y Series nativo Titan en proceso `:x4k`).
    3. `PlayerCore` Live (Canales M3U, deportes en vivo y opciones ClearKey DRM en Compose).
    4. `PlayerCore` VOD (Películas HTTP y archivo en Compose).
    5. `TitanLivePlayer` (Canales Magis en Compose).
    6. `TitanVodPlayerScreen` (VOD Magis en Compose).
- **Guardián de Debounce / Cooldown de 220 ms**:
  - Implementado `canExecuteControlAction()` centralizado: tras despertar el menú o descartar cualquier modal/selector, se aplica un intervalo de protección de 220 ms que absorbe rebotes mecánicos de mandos a distancia, teclas repetidas por hardware y eventos `ACTION_UP` residuales.
- **Corrección de Robo de Foco en `SkipPill` ("Saltar Intro")**:
  - En `PlayerCore`, la píldora de introducción ahora se mantiene confinada dentro de la visibilidad de los controles, evitando que capture el foco sobre la reproducción limpia en negro.

### 2. Blindaje de la Suite de Pruebas Anti-Regresiones
- **Cobertura Exhaustiva de Casos**:
  - Se incorporaron pruebas unitarias en `PlayerNavigationTest.kt` que verifican que todas las teclas de navegación retornen `WAKE_CONTROLS_ONLY` con controles ocultos en todos los modos (VOD y Vivo).
  - Pruebas dedicadas para teclas multimedia físicas (`PLAY`, `PAUSE`, `FAST_FORWARD`, etc.) y bypass limpio en presencia de modales de error o pistas.
  - Suite de 138 pruebas unitarias de Android y 33 de backend pasando al 100%.
