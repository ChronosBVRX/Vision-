# Vision+ v2.5.53 — Tendencias y estrenos de verdad

## Lo que cambia para ti
- El inicio ahora abre con lo más popular (héroe + Top 10 por impacto real),
  no con la primera repisa del portal ni con títulos oscuros bien puntuados.
- "Novedades y Estrenos" trae estrenos de verdad: solo año actual y anterior,
  populares primero (un éxito 2025 supera a un oscuro 2026).
- Películas, Series, Novelas y Anime ordenan cada hilera por impacto
  (rating con confianza + popularidad TMDB + recencia), en móvil y TV.
- Las repisas del portal ahora hidratan score y año: compiten en el ranking
  en vez de quedar en cero.

## Técnico
- Nuevo motor `data/Popularity.kt` (puro, testeable): `rankScore`,
  `topTrending`, `realPremieres`, `reorderRow`, `pickHero`, `dedupe`.
- `Film` suma `popularity`/`voteCount`; `RemoteIndexSource` los parsea y cachea.
- `X4KApi.homeRows` hidrata `score`/`year` del portal y los persiste en caché.
- `HomeScreen`/`TvHomeScreen`: héroe = #1 de tendencias con póster.
- `TvCategoryScreen`/`SectionScreen`: reordenado cliente (robusto ante caché
  vieja) + cuadrículas rankeadas; `UserLibrary.recommendations` rankeada.
- Servidor: columnas `titles/popularity,vote_count` + `enrichment/popularity,
  vote_count` (`schema.sql` idempotente); TMDB los captura y propaga a `titles`;
  `index_build` ordena estrenos y géneros por `popularity DESC, score, year`;
  `/v1/search` desempatando por popularidad; `film()` sirve `popularity`/
  `voteCount` (tolerante a registros viejos).
- Tests: 199 unit Android + 38 backend en verde (nuevos `PopularityTest`,
  `portalScore/portalYear`, `popularitySurvivesTheParse`, `film` popularidad,
  `title_row` impacto, `tmdb confidence`).
