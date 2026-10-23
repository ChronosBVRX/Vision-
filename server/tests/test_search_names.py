"""Buscador por nombres universales (auditoría 2026-10-18).

Brecha medida EN VIVO antes del arreglo:
- «buscando a nemo» NO encontraba «Finding Nemo» (el catálogo lo llama en inglés y el
  nombre es-MX de TMDB no se guardaba);
- «vaiana» NO encontraba «Moana» (nombre regional alternativo de TMDB);
- «termintor 2» (typo) no daba nada (sin trigramas);
- «fast x» devolvía Silicon Valley / Bob Esponja (las palabras cortas matcheaban
  sinopsis y reparto: la coincidencia profunda debe ser solo de respaldo).

Estos guardianes fijan el contrato: nombres plegados + compactos en `search_text`,
nombres TMDB guardados y buscados en todas las etapas, y la etapa rápida usando la
columna materializada (índice GIN de trigramas).
"""

import asyncio
import inspect
import urllib.error

import app
import search_text
import tmdb


# ----- texto de búsqueda materializado -----


def test_fold_is_the_same_criterion_everywhere():
    for q in ("La vida es bella!", "¿Canción del Ángel?", "Spider-Man: Un nuevo día"):
        assert search_text.fold(q) == app._fold(q)


def test_search_text_has_folded_and_compact_names():
    text = search_text.build_search_text("Spider-Man: Un nuevo día")
    folded = "spider man un nuevo dia"
    assert text.startswith(folded)
    # El compacto es lo que hace que «spiderman» (un solo token) lo encuentre.
    assert folded.replace(" ", "") in text
    # Los puntos también se compactan: «S.W.A.T.» → «swat».
    assert "swat" in search_text.build_search_text("S.W.A.T.")


def test_search_text_includes_localized_original_and_regional_names():
    text = search_text.build_search_text(
        "Moana",
        localized="Moana",
        original="Moana",
        alt_titles=["Vaiana", "Vaiana: La Légende du bout du monde"],
    )
    assert "vaiana" in text
    assert "vaiana la legende du bout du monde" in text


def test_search_text_deduplicates_and_keeps_the_title_first():
    text = search_text.build_search_text("Moana", localized="Moana", original="Moana")
    assert text.count("moana") == 1
    assert text.startswith("moana")


def test_search_text_is_capped_without_losing_the_title():
    alts = [f"Nombre regional alternativo numero {i}" for i in range(80)]
    text = search_text.build_search_text("Titulo", alt_titles=alts)
    assert len(text) <= search_text.MAX_SEARCH_TEXT
    assert text.startswith("titulo")


class _FakeConn:
    def __init__(self, pool):
        self.pool = pool

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def executemany(self, sql, updates):
        self.pool.updates.extend(updates)


class _FakePool:
    def __init__(self, rows):
        self.rows = rows
        self.updates = []

    async def fetch(self, sql, *args):
        return self.rows

    def acquire(self):
        return _FakeConn(self)


def test_rebuild_composes_the_text_from_the_tmdb_names():
    pool = _FakePool([{
        "id": "xtv:12",
        "title": "Finding Nemo",
        "portal_original": "Procurando Nemo",
        "localized": "Buscando a Nemo",
        "original": "Finding Nemo",
        "alt_titles": ["Finding Nemo 3D"],
        "translated_titles": ["Buscando a Nemo", "Die Suche nach Nemo"],
    }])
    assert asyncio.run(search_text.rebuild(pool, ids=["xtv:12"])) == 1
    _, text = pool.updates[0]
    assert "buscando a nemo" in text
    assert "finding nemo" in text
    assert "die suche nach nemo" in text


def test_maintenance_pass_rebuilds_only_pending_texts():
    source = inspect.getsource(search_text.rebuild)
    assert "t.search_text = ''" in source
    assert "e.updated_at > coalesce(t.search_text_at" in source


# ----- nombres TMDB -----


def test_parse_alt_titles_understands_movies_and_tv():
    movie = {"titles": [
        {"iso_3166_1": "ES", "title": "Vaiana"},
        {"iso_3166_1": "FR", "title": "Vaiana"},
        {"iso_3166_1": "US", "title": "   "},
    ]}
    assert tmdb.parse_alt_titles(movie) == ["Vaiana"]
    tv = {"results": [
        {"iso_3166_1": "MX", "title": "La casa de papel"},
        {"iso_3166_1": "US", "title": "Money Heist"},
    ]}
    assert tmdb.parse_alt_titles(tv) == ["La casa de papel", "Money Heist"]
    assert tmdb.parse_alt_titles(None) == []
    assert tmdb.parse_alt_titles({"titles": "basura"}) == []


def test_parse_alt_titles_caps_the_tail():
    payload = {"titles": [{"title": f"Nombre {i}"} for i in range(80)]}
    assert len(tmdb.parse_alt_titles(payload)) == 40


def test_parse_translations_prioritizes_spanish_and_dedupes():
    payload = {"translations": [
        {"iso_639_1": "ko", "iso_3166_1": "KR", "data": {"name": "심슨 가족"}},
        {"iso_639_1": "es", "iso_3166_1": "MX", "data": {"name": "Los Simpson"}},
        {"iso_639_1": "de", "iso_3166_1": "DE", "data": {"name": "Die Simpsons"}},
        {"iso_639_1": "es", "iso_3166_1": "ES", "data": {"name": "Los Simpson"}},
        {"iso_639_1": "ja", "iso_3166_1": "JP", "data": {"name": "ザ・シンプソンズ"}},
        {"iso_639_1": "en", "iso_3166_1": "US", "data": {"name": "   "}},
    ]}
    names = tmdb.parse_translations(payload)
    assert names[0] == "Los Simpson"
    assert names[1] == "Die Simpsons"
    assert names.count("Los Simpson") == 1
    assert "심슨 가족" in names
    assert tmdb.parse_translations(None) == []
    assert tmdb.parse_translations({"translations": "basura"}) == []


def test_parse_translations_caps_the_tail():
    payload = {"translations": [
        {"iso_639_1": f"x{i}", "data": {"title": f"Nombre {i}"}} for i in range(80)
    ]}
    assert len(tmdb.parse_translations(payload)) == 40


def test_match_keeps_the_names_tmdb_knows(monkeypatch):
    calls = []

    def fake_get(path, gap=None, **params):
        calls.append((path, params))
        if path == "/search/movie":
            return {"results": [{
                "id": 12, "title": "Buscando a Nemo", "original_title": "Finding Nemo",
                "release_date": "2003-05-30", "vote_count": 20000,
            }]}
        return {
            "title": "Buscando a Nemo", "original_title": "Finding Nemo",
            "release_date": "2003-05-30", "vote_average": 7.8, "vote_count": 20000,
            "popularity": 40.0, "genres": [{"name": "Animación"}],
            "overview": "Un pez payaso...", "external_ids": {"imdb_id": "tt0266543"},
            "alternative_titles": {"titles": [{"title": "Procurando Nemo"}]},
            "translations": {"translations": [
                {"iso_639_1": "de", "iso_3166_1": "DE", "data": {"title": "Findet Nemo"}},
            ]},
            "credits": {
                "crew": [{"job": "Director", "name": "Andrew Stanton"}],
                "cast": [
                    {"name": "Albert Brooks", "order": 0},
                    {"name": "Ellen DeGeneres", "order": 1},
                ],
            },
        }

    monkeypatch.setattr(tmdb, "_get", fake_get)
    match, had_error = tmdb._match("movie", "Finding Nemo", None, 2003)
    assert had_error is False
    assert match["localized_title"] == "Buscando a Nemo"
    assert match["original_title"] == "Finding Nemo"
    assert match["alt_titles"] == ["Procurando Nemo"]
    assert match["translated_titles"] == ["Findet Nemo"]
    assert match["director_names"] == "Andrew Stanton"
    assert match["cast_names"] == "Albert Brooks, Ellen DeGeneres"
    # Los nombres viajan en la MISMA llamada de la ficha (sin request extra).
    detail_calls = [c for c in calls if c[0].startswith("/movie/")]
    assert detail_calls and "alternative_titles" in detail_calls[0][1]["append_to_response"]
    assert "translations" in detail_calls[0][1]["append_to_response"]
    assert "credits" in detail_calls[0][1]["append_to_response"]


def test_fetch_alias_bundle_falls_back_to_the_other_media(monkeypatch):
    def fake_get(path, gap=None, **params):
        if path.startswith("/tv/"):
            raise urllib.error.HTTPError(path, 404, "not found", None, None)
        return {
            "title": "La casa de papel", "original_title": "Money Heist",
            "alternative_titles": {"titles": [{"title": "La casa de papel"}]},
        }

    monkeypatch.setattr(tmdb, "_get", fake_get)
    bundle = tmdb._fetch_alias_bundle(71446, "series")
    assert bundle["localized_title"] == "La casa de papel"
    assert bundle["original_title"] == "Money Heist"


def test_alias_backfill_marks_rows_so_it_never_loops():
    source = inspect.getsource(tmdb.backfill_aliases)
    assert "aliases_at is null" in source
    assert "aliases_at = now()" in source
    assert "_rebuild_ids" in source
    assert "search_text.rebuild" in inspect.getsource(tmdb._rebuild_ids)
    # El texto se rehace por trozos: una muerte a media pasada no deja textos viejos.
    assert "ALIAS_REBUILD_CHUNK" in source
    # El catch-up del CLI sanea los textos viejos de pasadas interrumpidas.
    assert "textos sanados" in inspect.getsource(tmdb)


def test_search_text_has_a_pending_heal_mode():
    source = inspect.getsource(search_text)
    assert '"--pending"' in source


class _WritesConn:
    def __init__(self, pool):
        self.pool = pool

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def fetch(self, sql, *args):
        return self.pool.rows

    async def execute(self, sql, *args):
        self.pool.writes.append(args)


class _WritesPool:
    def __init__(self, rows):
        self.rows = rows
        self.writes = []

    async def fetch(self, sql, *args):
        return self.rows

    def acquire(self):
        return _WritesConn(self)


def test_alias_backfill_runs_waves_and_rebuilds_each_saved_title(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [
        {"title_id": "xtv:1", "tmdb_id": 11, "kind": "movie", "title": "A"},
        {"title_id": "xtv:2", "tmdb_id": 22, "kind": "movie", "title": "B"},
        {"title_id": "xtv:3", "tmdb_id": 33, "kind": "movie", "title": "C"},
    ]
    seen = []

    def fake_bundle(tmdb_id, kind):
        seen.append(tmdb_id)
        return {"original_title": "X", "localized_title": "Y", "alt_titles": []}

    monkeypatch.setattr(tmdb, "_fetch_alias_bundle", fake_bundle)
    rebuilt = []

    async def fake_rebuild(pool, ids=None, limit=None):
        rebuilt.extend(ids or [])
        return len(ids or [])

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_aliases(pool, workers=3)) == 3
    assert sorted(seen) == [11, 22, 33]
    assert sorted(rebuilt) == ["xtv:1", "xtv:2", "xtv:3"]
    assert len(pool.writes) == 3  # una escritura de nombres por fila


def test_alias_backfill_rebuilds_text_in_chunks(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    monkeypatch.setattr(tmdb, "ALIAS_REBUILD_CHUNK", 1)
    rows = [
        {"title_id": "xtv:1", "tmdb_id": 11, "kind": "movie", "title": "A"},
        {"title_id": "xtv:2", "tmdb_id": 22, "kind": "movie", "title": "B"},
    ]
    monkeypatch.setattr(
        tmdb, "_fetch_alias_bundle",
        lambda tmdb_id, kind: {"original_title": "X", "localized_title": "Y", "alt_titles": []},
    )
    calls = []

    async def fake_rebuild(pool, ids=None, limit=None):
        calls.append(sorted(ids or []))
        return len(ids or [])

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    assert asyncio.run(tmdb.backfill_aliases(_WritesPool(rows), workers=1)) == 2
    assert calls == [["xtv:1"], ["xtv:2"]]


def test_alias_backfill_marks_tmdb_deleted_ids_without_counting_them(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [{"title_id": "xtv:9", "tmdb_id": 99, "kind": "movie", "title": "Z"}]
    monkeypatch.setattr(tmdb, "_fetch_alias_bundle", lambda tmdb_id, kind: None)

    async def fake_rebuild(pool, ids=None, limit=None):
        raise AssertionError("sin nombres guardados no hay nada que rehacer")

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_aliases(pool)) == 0
    assert len(pool.writes) == 1  # solo la marca de aliases_at


# ----- traducciones por idioma -----


def test_translation_backfill_marks_rows_so_it_never_loops():
    source = inspect.getsource(tmdb.backfill_translations)
    assert "translations_at is null" in source
    assert "translations_at = now()" in source
    assert "_rebuild_ids" in source
    assert "ALIAS_REBUILD_CHUNK" in source


def test_translation_backfill_saves_names_and_rebuilds_each_title(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [
        {"title_id": "xtv:1", "tmdb_id": 11, "kind": "series", "title": "A"},
        {"title_id": "xtv:2", "tmdb_id": 22, "kind": "movie", "title": "B"},
        {"title_id": "xtv:3", "tmdb_id": 33, "kind": "anime", "title": "C"},
    ]
    seen = []

    def fake_bundle(tmdb_id, kind):
        seen.append(tmdb_id)
        return ["Die Simpsons", "Блич"]

    monkeypatch.setattr(tmdb, "_fetch_translation_bundle", fake_bundle)
    rebuilt = []

    async def fake_rebuild(pool, ids=None, limit=None):
        rebuilt.extend(ids or [])
        return len(ids or [])

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_translations(pool, workers=2)) == 3
    assert sorted(seen) == [11, 22, 33]
    assert sorted(rebuilt) == ["xtv:1", "xtv:2", "xtv:3"]
    assert len(pool.writes) == 3  # una escritura de traducciones por fila


def test_translation_backfill_rebuilds_text_in_chunks(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    monkeypatch.setattr(tmdb, "ALIAS_REBUILD_CHUNK", 1)
    rows = [
        {"title_id": "xtv:1", "tmdb_id": 11, "kind": "movie", "title": "A"},
        {"title_id": "xtv:2", "tmdb_id": 22, "kind": "movie", "title": "B"},
    ]
    monkeypatch.setattr(
        tmdb, "_fetch_translation_bundle", lambda tmdb_id, kind: ["Die Simpsons"]
    )
    calls = []

    async def fake_rebuild(pool, ids=None, limit=None):
        calls.append(sorted(ids or []))
        return len(ids or [])

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    assert asyncio.run(tmdb.backfill_translations(_WritesPool(rows), workers=1)) == 2
    assert calls == [["xtv:1"], ["xtv:2"]]


def test_translation_backfill_marks_tmdb_deleted_ids_without_rebuild(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [{"title_id": "xtv:9", "tmdb_id": 99, "kind": "movie", "title": "Z"}]
    monkeypatch.setattr(tmdb, "_fetch_translation_bundle", lambda tmdb_id, kind: None)

    async def fake_rebuild(pool, ids=None, limit=None):
        raise AssertionError("sin nombres guardados no hay nada que rehacer")

    monkeypatch.setattr(tmdb.search_text, "rebuild", fake_rebuild)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_translations(pool)) == 0
    assert len(pool.writes) == 1  # solo la marca de translations_at


# ----- créditos (director/reparto) -----


def test_parse_credits_movie_and_tv():
    movie = {"credits": {
        "crew": [
            {"job": "Director", "name": "Quentin Tarantino"},
            {"job": "Producer", "name": "Lawrence Bender"},
        ],
        "cast": [
            {"name": "John Travolta", "order": 0},
            {"name": "Uma Thurman", "order": 1},
        ],
    }}
    out = tmdb.parse_credits(movie, "movie")
    assert out["director_names"] == "Quentin Tarantino"
    assert out["cast_names"] == "John Travolta, Uma Thurman"

    tv = {
        "created_by": [{"name": "Vince Gilligan"}],
        "aggregate_credits": {
            "crew": [{"jobs": [{"job": "Director"}], "name": "Michelle MacLaren"}],
            "cast": [{"name": "Bryan Cranston", "order": 0}],
        },
    }
    out = tmdb.parse_credits(tv, "tv")
    assert "Vince Gilligan" in out["director_names"]
    assert "Michelle MacLaren" in out["director_names"]
    assert out["cast_names"] == "Bryan Cranston"
    assert tmdb.parse_credits(None, "movie") == {"director_names": "", "cast_names": ""}


def test_credits_backfill_marks_rows_and_does_not_rebuild_search_text():
    source = inspect.getsource(tmdb.backfill_credits)
    assert "credits_at is null" in source
    assert "credits_at = now()" in source
    assert "director_names = $2" in source
    assert "search_text.rebuild" not in source
    assert "_rebuild_ids" not in source


def test_credits_backfill_writes_director_and_cast(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [
        {"title_id": "xtv:1", "tmdb_id": 11, "kind": "movie", "title": "A"},
        {"title_id": "xtv:2", "tmdb_id": 22, "kind": "series", "title": "B"},
    ]
    seen = []

    def fake_bundle(tmdb_id, kind):
        seen.append(tmdb_id)
        return {"director_names": "D", "cast_names": "C"}

    monkeypatch.setattr(tmdb, "_fetch_credits_bundle", fake_bundle)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_credits(pool, workers=2)) == 2
    assert sorted(seen) == [11, 22]
    assert len(pool.writes) == 2
    assert all(write[1] == "D" and write[2] == "C" for write in pool.writes)


def test_credits_backfill_marks_tmdb_deleted_ids_without_writing_names(monkeypatch):
    monkeypatch.setattr(tmdb, "KEY", "test-key")
    rows = [{"title_id": "xtv:9", "tmdb_id": 99, "kind": "movie", "title": "Z"}]
    monkeypatch.setattr(tmdb, "_fetch_credits_bundle", lambda tmdb_id, kind: None)
    pool = _WritesPool(rows)
    assert asyncio.run(tmdb.backfill_credits(pool)) == 0
    assert len(pool.writes) == 1  # solo la marca de credits_at


def test_should_run_deep_when_names_are_few():
    assert app.should_run_deep([]) is True
    assert app.should_run_deep([{}]) is True
    assert app.should_run_deep([{}, {}]) is True
    assert app.should_run_deep([{}, {}, {}]) is False
    assert app.should_run_deep([{}, {}], limit=2) is False


def test_search_endpoint_uses_the_deep_stage_for_few_names():
    source = inspect.getsource(app.search)
    assert "should_run_deep" in source
    assert "rows = deep_rows" in source


# ----- etapas del endpoint -----


def test_name_match_uses_the_materialized_text_with_offsets():
    sql, params = app.build_name_match(["spider", "man"], offset=3)
    assert params == ["%spider%", "%man%"]
    assert "$4" in sql and "$5" in sql and "$3" not in sql


def test_deep_match_covers_cast_synopsis_and_tmdb_names():
    sql, params = app.build_deep_match(["nemo"], offset=3)
    assert params == ["%nemo%"]
    assert "$4" in sql
    for field in (
        "cast_list", "synopsis", "localized_title", "original_title", "alt_titles",
        "translated_titles", "director_names", "cast_names",
    ):
        assert field in sql


def test_deep_match_keeps_one_char_words_away_from_cast_and_synopsis():
    # «fast x»: la «x» solo prueba contra nombres; en reparto/sinopsis matchearía
    # cualquier texto (el ruido medido en la auditoría).
    sql, params = app.build_deep_match(["fast", "x"], offset=3)
    assert params == ["%fast%", "%x%"]
    assert "$5" in sql
    assert sql.count("cast_list") == 1
    assert sql.count("synopsis") == 1


def test_fuzzy_probe_uses_the_longest_word():
    assert app.fuzzy_probe("termintor 2") == "termintor"
    assert app.fuzzy_probe("spderman") == "spderman"
    assert app.fuzzy_probe("  ") == "  "


def test_fuzzy_skips_short_probes_without_touching_the_db(monkeypatch):
    class _Boom:
        async def fetch(self, *a):
            raise AssertionError("no debía consultar la base")

    monkeypatch.setattr(app, "db", lambda: _Boom())
    assert asyncio.run(app._search_fuzzy("abc", 10)) == []


def test_search_text_includes_translations():
    text = search_text.build_search_text(
        "Moana",
        translated_titles=["Vaiana", "Oceania", "Moana"],
    )
    assert "vaiana" in text
    assert "oceania" in text
    assert text.count("moana") == 1


def test_rank_prefers_exact_and_covers_every_name():
    sql, params = app.build_rank("buscando a nemo")
    assert params == ["buscando a nemo", "buscando a nemo%", "%buscando a nemo%", "buscandoanemo"]
    for field in (
        "t.title", "t.original_title", "e.localized_title", "e.original_title", "e.alt_titles",
        "e.translated_titles",
    ):
        assert field in sql
    exact_branch = sql.split("then 1")[0]
    assert "alt_titles" not in exact_branch
    assert "translated_titles" not in exact_branch
    second_branch = sql.split("then 1")[1].split("then 2")[0]
    assert "alt_titles" in second_branch
    assert "translated_titles" in second_branch
    assert "then 3" in sql and "then 4" in sql and "then 5" in sql and "then 6" in sql


def test_search_page_puts_live_channels_after_on_demand(monkeypatch):
    fake = _CaptureDb()
    monkeypatch.setattr(app, "db", lambda: fake)
    asyncio.run(app._search_page(
        "t.search_text like $5", ["%nemo%"], "nemo", 10))
    sql, _ = fake.calls[0]
    assert "case when t.kind = 'live' then 1 else 0 end" in sql
    assert "t.year asc nulls last" in sql


def test_portal_wait_only_when_the_local_index_lacks_the_name():
    assert app.should_wait_for_portal([], "nemo") is True
    assert app.should_wait_for_portal([{"search_text": "finding nemo"}], "nemo") is False
    assert app.should_wait_for_portal([{"search_text": "otra cosa"}], "nemo") is True


def test_tmdb_dedupe_only_with_high_confidence():
    entry = {"tmdbId": 93405}
    assert app._tmdb_dedupe_key(entry, {"match_confidence": 0.9}) == 93405
    assert app._tmdb_dedupe_key(entry, {"match_confidence": 0.6}) == 0
    assert app._tmdb_dedupe_key({"tmdbId": 0}, {"match_confidence": 1.0}) == 0


def test_rank_folds_punctuation_like_the_query():
    # «Daddy's Home» (alias del portal) debe empatar con «daddy s home» (consulta
    # plegada): sin el plegado en SQL, el rank 4 no aplicaba y ganaba «My Melody y
    # Kuromi» (TMDB le regaló un alternativo «Daddy's Home») por popularidad.
    expr = app._fold_sql("t.original_title")
    assert "regexp_replace" in expr
    assert "unaccent(lower(t.original_title))" in expr
    sql, _ = app.build_rank("daddy s home")
    assert sql.count("regexp_replace") >= 8


class _CaptureDb:
    def __init__(self):
        self.calls = []

    async def fetch(self, sql, *params):
        self.calls.append((sql, params))
        return []


def test_search_page_placeholder_count_matches_params(monkeypatch):
    fake = _CaptureDb()
    monkeypatch.setattr(app, "db", lambda: fake)
    asyncio.run(app._search_page(
        "t.search_text like $5 and t.search_text like $6", ["%a%", "%b%"], "a b", 10))
    sql, params = fake.calls[0]
    assert len(params) == 7  # 4 del orden + 2 del match + el límite
    assert "limit $7" in sql


def test_search_offsets_follow_the_rank_params():
    _, rank_params = app.build_rank("daddy s home")
    assert len(rank_params) == 4  # exacto/prefijo/contiene + compacto
    name_sql, _ = app.build_name_match(["daddy", "home"], offset=len(rank_params))
    assert "$5" in name_sql and "$6" in name_sql and "$4" not in name_sql


def test_search_entry_carries_the_original_name_when_it_differs():
    entry = {"id": "xtv:1", "title": "Finding Nemo"}
    app._attach_original_title(entry, {"id": "xtv:1"}, {"original_title": "Finding Nemo"})
    assert "originalTitle" not in entry
    app._attach_original_title(entry, {"id": "xtv:1"}, {"original_title": "Finding Nemo 3D"})
    assert entry["originalTitle"] == "Finding Nemo 3D"
    # Una fila agrupada (representante distinto) no hereda un nombre ajeno y nada truena.
    app._attach_original_title(entry, {"id": "xtv:2"}, {"original_title": "Otro"})
    assert entry["originalTitle"] == "Finding Nemo 3D"
    app._attach_original_title(entry, {"id": "xtv:1"}, None)
