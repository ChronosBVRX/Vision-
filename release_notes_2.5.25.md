## Novedades de Vision+ v2.5.25

### ⚡ Canales de Fútbol en Vivo al Instante (0 ms)
- **Opciones de reproducción inmediatas al entrar a Fútbol**:
  - Resuelto el problema donde al abrir la agenda de fútbol, los partidos que estaban en vivo o por comenzar no desplegaban canales de transmisión de inmediato hasta que terminaba la descarga HTTP por red.
  - **Resolución síncrona en memoria (`SportsLinker.quickResolve` & `quickResolveAll`)**: Cruce instantáneo de las cadenas oficiales (`TUDN`, `Canal 5`, `Fox Sports`, `ESPN`, `Azteca 7`, `ViX`, etc.) contra los canales de TV en vivo cacheados en memoria RAM y disco (`X4KApi.cachedLiveChannels`).
  - **Carga de estado inmediata (`SportsState.kt`)**: Tanto en el inicio de la pantalla como al cambiar de fecha con los chips superiores, los partidos cacheados y sus canales candidatos se inicializan de forma inmediata (0 ms / frame 0), mientras la red actualiza marcadores y confirmación por EPG en segundo plano.
  - **Respaldo directo en el selector modal (`TvMatchChannelsModal` & `MobileMatchChannelsDialog`)**: Si por alguna razón el enlace no estuviera listo, el diálogo modal ejecuta `quickResolve` de respaldo directamente al abrirse, garantizando que la lista de canales nunca aparezca vacía ni tarde en cargar.

### 🎮 Restauración Impecable de Foco D-pad al Cerrar el Modal
- **Fin a la pérdida de navegación con el mando al presionar Atrás**:
  - Resuelto el fallo donde presionar la tecla Atrás para cerrar el modal de selección de canales sin sintonizar dejaba el D-pad completamente inerte y sin foco.
  - **Pila de objetivos en el rescate global de foco (`FocusRescue.targets`)**: Anteriormente, al cerrar el modal, el `onDispose` limpiaba `FocusRescue.target = null`, dejando a la pantalla huérfana de destino de rescate. Ahora `FocusRescue` utiliza una pila segura (`pushTarget` / `popTarget`) que restaura automáticamente el landing de la pantalla padre al cerrarse cualquier diálogo o modal.
  - **Retorno exacto a la tarjeta seleccionada (`cardFocusRequester`)**: Al presionar Atrás o pulsar "Volver"/"Cerrar", la pantalla TV reenfoca exactamente la tarjeta del partido que el usuario tenía seleccionada, permitiendo continuar navegando hacia arriba, abajo o a los lados fluidamente sin saltar al inicio de la lista.

### 🔄 Precalentamiento de la Agenda en Segundo Plano
- **Arranque asíncrono no bloqueante (`XuperCatalog.kt`)**: La agenda deportiva del día se precarga discretamente en segundo plano junto con las repisas y canales en vivo durante el inicio de la app, permitiendo que al abrir Fútbol todo esté disponible de inmediato.

---
**Calidad y Estabilidad**:
- 105 tests unitarios de Android (`:app:testNormalDebugUnitTest`) pasando al 100%.
- 28 tests de backend (`pytest server/tests`) pasando al 100%.
- Cero regresiones en TV en vivo, VOD, reproductor, ni arranque en Chromecast.
