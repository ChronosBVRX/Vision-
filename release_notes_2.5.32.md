# Vision+ v2.5.32 — Blindaje Definitivo de Foco D-Pad al Salir del Modal en Fútbol

Esta actualización resuelve de raíz la pérdida total del desplazamiento D-pad que ocurría al salir con la tecla Atrás (o botones de cierre) del modal de selección de canales de partidos de fútbol en Smart TV.

### 🌟 Correcciones y Mejoras Clave

1. **🛡️ Aislamiento de Ventana con `Dialog` (`TvSportsScreen.kt`)**:
   - Se migró `TvMatchChannelsModal` a un componente nativo `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))`, equiparándolo con el estándar del resto de diálogos de la aplicación (`TvDialog`, `TvOssNoticesDialog`).
   - Al ejecutarse en su propia ventana aislada de Android, el árbol de foco de la pantalla padre en la Activity principal nunca pierde su estado, sus referencias ni sus nodos interactivos.

2. **🚫 Erradicación de Bloqueo de Foco en Fondo (`Column.focusProperties`)**:
   - Eliminada la restricción `.focusProperties { canFocus = selectedMatchForPicker == null }` que desactivaba por completo la capacidad de foco de todas las tarjetas y componentes de la pantalla mientras el modal estaba abierto, previniendo la invalidación de nodos al cerrar el modal.

3. **🔄 Escalafón de Recuperación de Foco Inmediato**:
   - Al descartar el modal (con tecla Atrás o botón Cerrar), se resetea inmediatamente `FocusRescue.screenHasFocus = false`, eliminando banderas de estado congeladas o desincronizadas.
   - Retorno automático y exacto a la tarjeta de partido seleccionada (`cardFocusRequester.requestFocus()`), con escalafón de seguridad hacia el landing principal (`landing.request()`) y la fila de filtros de liga (`filterRowFocusRequester.requestFocus()`), la cual siempre permanece visible en pantalla.
   - Eliminada la condición prematura que abortaba el reintento de foco en la primera iteración.

4. **🧪 Pruebas Unitarias Automatizadas**:
   - Incorporado test unitario específico `sportsModalDismissRestoresDpadFocus` y añadida la ruta deportiva a `everyPlaceholderScreenLands` en `TvFocusTest.kt`.
   - **123 tests de Android** y **33 tests de backend** pasando al 100% en verde.
