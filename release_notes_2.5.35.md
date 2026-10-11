# Vision+ v2.5.35 — Continuidad Secuencial de Series y Selector Pro de Temporadas y Capítulos

Esta actualización soluciona de raíz la reproducción continua de series para que cada episodio avance automáticamente al siguiente en estricto orden secuencial (tanto dentro de una temporada como al cambiar de temporada), e incorpora el nuevo **Selector Pro de Temporadas y Capítulos** con la identidad visual y paleta oficial de Vision+ (`VisionBlue` `#2D7DF6`, `VisionBlueBright` `#6BA6FF`, `VisionSurface` `#0F1626` y `VisionSurfaceHigh` `#1B2538`).

### 🌟 Novedades y Correcciones Clave

1. **🎬 Reproducción Continua de Series en Orden Secuencial Estricto (`X4KApi.kt`, `X4KTitan.kt`, `X4KPlayerAty.kt`, `PlayerScreen.kt`)**:
   - **Ordenamiento canónico de episodios (`parseEpisodePrograms` y `parsePlayEpisodePrograms`)**: Los capítulos recibidos del portal (`simpleProgramList` y `episodeList`) se desduplican por `contentId` y se ordenan ascendentemente (`1, 2, 3...`) según `seriesNumber` / `episodeNumber` (con extracción numérica por regex sobre el nombre como respaldo), garantizando que nunca se salten ni desordenen capítulos.
   - **Callback nativo de fin de episodio (`bd.a.z2` → `onCompletion`) y duración exacta por capítulo**: Se enlazó el evento nativo de finalización del motor Titan hacia `onEpisodeEnded()` y se eliminó la duración fallback ficticia de 45 minutos en series, permitiendo que capítulos de 20 a 58 minutos fijen su duración real de archivo y disparen puntualmente la transición al siguiente capítulo.
   - **Continuidad automática entre temporadas**: Al concluir el último capítulo de una temporada y avanzar al `Capítulo 1` de la siguiente, el reproductor actualiza la lista completa de episodios (`epIds` y `epNames`) de la nueva temporada para que la reproducción continúe `C1 → C2 → C3...` sin interrupciones, conservando siempre el título base de la serie (`Serie · T# · C#`) e iniciando desde `00:00`.

2. **💎 Selector Pro de Temporadas y Capítulos en Reproductor, Smart TV y Móvil (`X4KPlayerAty.kt`, `TvTitleScreen.kt`, `TitleScreen.kt`)**:
   - **En el Reproductor Nativo (`openEpisodePicker` y `showNextCard`)**:
     - Modal rediseñado sobre `VisionSurface` (`#0F1626`) con borde `VisionBlue` (`#2D7DF6`), insignia `SERIE · VISION+`, contador de capítulos vistos, botón `✕ Cerrar` y pestañas de temporada con indicador activo.
     - Tarjetas de capítulo enriquecidas con insignia numérica (`C1`, `C2`...), título limpio del episodio, indicador de estado (`● Reproduciendo ahora`, `✓ Capítulo visto`, `Disponible en orden secuencial`), píldora de acción (`▶ Viendo` / `↺ Repetir` / `▶ Reproducir`) y aro de foco blanco nítido para control remoto D-pad.
   - **En Ficha de Smart TV (`TvSeriesSelectorPanel`) y Móvil (`MobileSeriesSelectorSection`)**:
     - Panel contenedor Pro con selector horizontal de temporadas y tarjetas de capítulos que destacan el siguiente episodio en turno (`▶ Siguiente`) y los capítulos vistos (`✓ Visto`).
     - El botón principal de reproducción detecta el progreso en orden y ofrece directamente `▶ Continuar · T# · C#` (o `▶ Reproducir · C1`).

3. **🧪 Validación y Cero Regresiones**:
   - Nuevas pruebas unitarias de ordenamiento de episodios en `X4KApiDurationTest.kt`.
   - **131 pruebas unitarias de Android** (`:app:testNormalDebugUnitTest`) pasando al 100% en verde.
