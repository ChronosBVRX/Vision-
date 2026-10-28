"""Pruebas de la hilera «Tendencias» (el Top de popularidad del catálogo completo).

El «Top 10» del Inicio no debe repetir «Novedades y Estrenos»: se rankea por
popularidad real (TMDB) de TODO el catálogo, sin importar el año, y solo entra
lo que tiene ficha visible (póster) y reproducción.
"""

import sys
from pathlib import Path

# Añadir server/sync al sys.path para poder importar index_build
sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import index_build


def _sql() -> str:
    return index_build.TENDENCIAS_SQL.format(
        not_unmatched=index_build.NOT_UNMATCHED,
        not_trailer=index_build.NOT_TRAILER,
    )


def test_tendencias_ranks_by_real_popularity_not_year():
    sql = _sql()
    # La popularidad manda; los votos desempatan (confianza en el rating).
    assert "t.popularity desc nulls last" in sql
    assert "t.vote_count desc nulls last" in sql
    assert "row_number() over (order by buzz desc" in sql
    assert "least(10.0, ln(coalesce(t.popularity, 0) + 1.0)" in sql
    # El año no filtra la hilera: un clásico popular entra igual que un estreno.
    before_order = sql.split("distinct on")[0]
    assert "t.year >=" not in before_order
    assert "current_year" not in sql


def test_tendencias_feels_alive_without_inventing_taste():
    """La fila se mueve por señales reales: tendencia de la semana, recién llegado
    y refresco de popularidad; el desempate diario solo rota lo de impacto parecido."""
    sql = _sql()
    assert "weekly_trending" in sql
    assert "greatest(0, 21 - rank)" in sql
    assert "t.first_seen_at >= now() - make_interval(days => $2)" in sql
    assert "to_char(current_date, 'YYYY-MM-DD')" in sql
    assert "md5(t.id" in sql
    # El empujón por «recién llegado» no puede coronar basura sin reproducción: la
    # fila ya exige póster, play_ref y popularidad > 0.
    assert "coalesce(t.poster_url, '') <> ''" in sql
    assert "coalesce(t.play_ref, '') <> ''" in sql


def test_tendencias_leaves_one_entry_per_work():
    # El portal reparte la misma serie en temporadas/doblajes (Link Click T1..T4,
    # Os Simpsons/Los Simpson) y todas comparten tmdb_id: la hilera del Top 10 no
    # puede pintar la misma tarjeta varias veces.
    sql = _sql()
    assert "distinct on (coalesce(e.tmdb_id, 0)" in sql
    assert "case when e.tmdb_id is null then t.id else '' end" in sql
    assert "left join enrichment e" in sql
    # La copia que se queda es la mejor: popular, votada y estrenada primero.
    assert "t.year asc nulls last" in sql


def test_tendencias_only_watchable_cards_with_art():
    sql = _sql()
    assert "coalesce(t.poster_url, '') <> ''" in sql
    assert "coalesce(t.play_ref, '') <> ''" in sql
    assert "coalesce(t.popularity, 0) > 0" in sql


def test_tendencias_excludes_niche_and_magis():
    sql = _sql()
    # Mismo criterio curado que estrenos: sin nicho sin match TMDB ni portal magis.
    assert "match_confidence < 0.5" in sql
    assert "t.source != 'magis'" in sql
    # Los tráilers del portal no son la película: no compiten en el Top 10.
    assert "trailers?" in sql


def test_trailer_shelves_do_not_duplicate_the_real_title():
    assert "trailers?" in index_build.NOT_TRAILER
    import inspect

    for fn in (index_build._genres_by_title, index_build._title_meta):
        assert "NOT_TRAILER" in inspect.getsource(fn)


def test_tendencias_position_leads_the_special_rows():
    # normalize() deja Tendencias arriba del todo en /v1/index (1), con estrenos
    # y recomendados detrás (2/3): el orden curado de las categorías especiales.
    import inspect

    source = inspect.getsource(index_build.normalize)
    assert 'set position = 1 where id = \'tendencias\'' in source
    assert 'set position = 2 where id = \'estrenos\'' in source
    assert 'set position = 3 where id = \'recomendados\'' in source
