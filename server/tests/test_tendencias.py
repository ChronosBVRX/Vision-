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
    return index_build.TENDENCIAS_SQL.format(not_unmatched=index_build.NOT_UNMATCHED)


def test_tendencias_ranks_by_real_popularity_not_year():
    sql = _sql()
    # La popularidad manda; los votos desempatan (confianza en el rating).
    assert "coalesce(t.popularity, 0) desc" in sql
    assert "t.vote_count desc nulls last" in sql
    assert "order by coalesce(t.popularity, 0) desc, t.vote_count desc" in sql
    # El año no filtra la hilera: un clásico popular entra igual que un estreno.
    before_order = sql.split("order by")[0]
    assert "t.year >=" not in before_order
    assert "current_year" not in sql


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


def test_tendencias_position_leads_the_special_rows():
    # normalize() deja Tendencias arriba del todo en /v1/index (1), con estrenos
    # y recomendados detrás (2/3): el orden curado de las categorías especiales.
    import inspect

    source = inspect.getsource(index_build.normalize)
    assert 'set position = 1 where id = \'tendencias\'' in source
    assert 'set position = 2 where id = \'estrenos\'' in source
    assert 'set position = 3 where id = \'recomendados\'' in source
