## Novedades de Vision+ v2.5.21

### 🛡️ Confinamiento de Foco (Focus Trap) en Modales y Diálogos de Smart TV
- **Blindaje total contra escape de foco con D-pad**: Al abrir cualquier ventana modal o diálogo en Smart TV, la navegación con el control remoto hacia arriba o hacia abajo queda estrictamente confinada al modal. Se eliminó la fuga de foco que hacía saltar la selección al contenido inferior de la pantalla o dejaba el mando bloqueado.
- **Ventanas y diálogos de Compose protegidos (`Modifier.modalFocusTrap()`)**:
  - Selector interactivo de canal por partido de fútbol en la Agenda Deportiva (`TvMatchChannelsModal`).
  - Diálogos del sistema TV: actualizaciones de app, confirmación de borrado y agregado de listas M3U en TV en vivo.
  - Modal de licencias de software libre (`TvOssNoticesDialog`).
  - Selectores de pistas de audio y subtítulos / servidor y marcadores de intro/outro en el reproductor.
  - Diálogo "¿Continuar viendo?" al reanudar contenidos a medias.
- **Modales del reproductor nativo XTV blindados (`ModalDimLayout`)**:
  - `ModalDimLayout` y bloqueo de descendientes en controles de fondo (`FOCUS_BLOCK_DESCENDANTS`).
  - Navegación cerrada cíclica en los límites de cada lista y botones en: selector de canales en vivo, selector de capítulos y temporadas, selector de audio/subtítulos, información EPG del canal, diálogo "¿Continuar viendo?" y tarjeta "A continuación".
  - Las flechas del D-pad no cambian de canal ni interfieren con los controles de fondo mientras cualquier modal esté en pantalla.
- **Cero regresiones**: Conserva íntegra la carga instantánea en 0 ms de catálogos VOD y TV en vivo (v2.5.20) y la suite completa de 103 pruebas unitarias al 100% en verde.
