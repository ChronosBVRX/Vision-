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
    }])
    assert asyncio.run(search_text.rebuild(pool, ids=["xtv:12"])) == 1
    _, text = pool.updates[0]
    assert "buscando a nemo" in text
    assert "finding nemo" in text


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
        }

    monkeypatch.setattr(tmdb, "_get", fake_get)
    match, had_error = tmdb._match("movie", "Finding Nemo", None, 2003)
    assert had_error is False
    assert match["localized_title"] == "Buscando a Nemo"
    assert match["original_title"] == "Finding Nemo"
    assert match["alt_titles"] == ["Procurando Nemo"]
    # Los nombres viajan en la MISMA llamada de la ficha (sin request extra).
    detail_calls = [c for c in calls if c[0].startswith("/movie/")]
    assert detail_calls and "alternative_titles" in detail_calls[0][1]["append_to_response"]


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


def test_rank_prefers_exact_and_covers_every_name():
    sql, params = app.build_rank("buscando a nemo")
    assert params == ["buscando a nemo", "buscando a nemo%", "%buscando a nemo%", "buscandoanemo"]
    for field in (
        "t.title", "t.original_title", "e.localized_title", "e.original_title", "e.alt_titles",
    ):
        assert field in sql


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
