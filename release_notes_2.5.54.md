# Vision+ v2.5.54 — Películas sin botones de serie

## Lo que cambia para ti
- Las películas ya no muestran "Siguiente episodio" ni el selector de
  temporadas/capítulos en el reproductor: solo aparecen en series, novelas
  y anime reales (2+ capítulos o 2+ temporadas).
- La ficha de película vuelve a decir "Reproducir" y oculta el panel de
  temporadas cuando el portal devuelve un único capítulo residual.
- "Seguir viendo" de películas viejas contaminadas se autocorrige al abrir.

## Técnico
- Fuente única `isSeriesContent(epCount, seasonCount)` en `ControlsState.kt`;
  `hasNextEpisodeAvailable` la exige antes de ofrecer siguiente.
- `X4KApi.parseEpisodePrograms` descarta <=1 (igual que
  `parsePlayEpisodePrograms`); `playVod`/`launch` degradan a película y no
  envían extras de serie; `X4KPlayerAty` normaliza intents/historiales viejos,
  `saveHistory` solo guarda contexto en series reales y `showNextCard`/
  `showEpisodePicker`/`onFirstFrame` usan el mismo criterio.
- `TitleScreen`/`TvTitleScreen`/`resumeRoute`: `isSeriesTitle` y saneo de
  `EpisodePlay` para no contaminar películas.
- Tests: 203 unit Android + 38 backend en verde (nuevos
  `movieNeverShowsSeriesUiWithSingleEpisodeOrStraySeriesId` y
  `X4KApiSeriesTest`: 1 programa = película, 2 = serie).
