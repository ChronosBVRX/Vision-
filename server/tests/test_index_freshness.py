"""«Novedades y Estrenos» se mueve con lo que de verdad llega al catálogo.

La fila se quedaba pegada a las repisas «destacados» del portal (varias con 0 votos)
y no cambiaba aunque el sync estrenara títulos. Aquí se fija la regla: los recién
llegados con señal van al frente (posiciones 1..FRESH_TOP) y el resto se ordena por
impacto real; lo viejo sin señal queda fuera.
"""

import asyncio
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import index_build

NOW = datetime(2026, 10, 26, 12, 0, tzinfo=timezone.utc)


def _row(
    title_id: str,
    *,
    year: int,
    popularity: float = 0.0,
    votes: int = 0,
    premiere: bool = False,
    seen_days_ago: int = 200,
    score: float = 7.0,
):
    return {
        "id": title_id,
        "title": title_id,
        "year": year,
        "popularity": popularity,
        "vote_count": votes,
        "score": score,
        "is_premiere": premiere,
        "first_seen_at": NOW - timedelta(days=seen_days_ago),
        "source": "xtv",
        "tmdb_id": None,
    }


def test_new_arrivals_lead_only_when_they_are_recent_releases():
    rows = [
        _row("fresh-old", year=2010, popularity=5.0, votes=10, seen_days_ago=1),
        _row("fresh-hit", year=2026, popularity=100.0, seen_days_ago=10),
        _row("recent-hit", year=2025, popularity=200.0),
        _row("portal-premiere", year=1990, popularity=1.0, premiere=True),
        _row("old-classic", year=1990, popularity=300.0),
        _row("fresh-without-signal", year=1990, seen_days_ago=2),
    ]
    pairs = dict(index_build._estrenos_pairs(rows, NOW, 2026))
    # Lo recién llegado Y reciente va al frente.
    assert pairs["fresh-hit"] == 1
    # El resto reciente empieza en REST_OFFSET, por popularidad real.
    assert pairs["recent-hit"] == 1000
    # Un clásico viejo no es «novedad», ni aunque acabe de entrar al catálogo.
    assert "fresh-old" not in pairs
    assert "old-classic" not in pairs
    assert "portal-premiere" not in pairs
    assert "fresh-without-signal" not in pairs


def test_estrenos_never_opens_with_old_catalog_imports():
    """La pasada profunda importa cine de 1983/1993: no puede abrir la fila."""
    rows = [
        _row("El piano", year=1993, popularity=8.1, votes=1715, seen_days_ago=1),
        _row("La Zona Muerta", year=1983, popularity=6.0, votes=1942, seen_days_ago=1),
        _row("Verity", year=2026, popularity=67.0, votes=251, seen_days_ago=1),
        _row("Crimen en el paraíso T15", year=2026, popularity=1626.0),
    ]
    pairs = dict(index_build._estrenos_pairs(rows, NOW, 2026))
    head = [name for name, pos in pairs.items() if pos <= index_build.FRESH_TOP]
    assert head == ["Verity"]
    assert "El piano" not in pairs and "La Zona Muerta" not in pairs


def test_one_card_per_work_picks_the_newest_season():
    rows = [
        _row("Crimen T14", year=2025, popularity=1626.0),
        _row("Crimen T15", year=2026, popularity=1626.0),
        _row("Otra", year=2026, popularity=10.0),
    ]
    for row in rows:
        row["tmdb_id"] = 41956 if row["id"].startswith("Crimen") else 1
    pairs = dict(index_build._estrenos_pairs(rows, NOW, 2026))
    assert "Crimen T15" in pairs
    assert "Crimen T14" not in pairs
    assert len([name for name in pairs if name.startswith("Crimen")]) == 1


def test_fresh_head_is_bounded_but_the_rest_still_appears():
    rows = [
        _row(f"fresh-{i}", year=2026, popularity=float(i), seen_days_ago=i)
        for i in range(1, 16)
    ]
    pairs = dict(index_build._estrenos_pairs(rows, NOW, 2026))
    head = [name for name, pos in pairs.items() if pos <= index_build.FRESH_TOP]
    assert len(head) == index_build.FRESH_TOP
    # Los que no caben en el frente no desaparecen: entran detrás por impacto.
    assert all(name in pairs for name in [f"fresh-{i}" for i in range(1, 16)])
    tail = [pos for name, pos in pairs.items() if name not in head]
    assert all(pos >= index_build.REST_OFFSET for pos in tail)


def test_duplicates_between_fresh_and_recent_are_inserted_once():
    row = _row("same", year=2026, popularity=50.0, seen_days_ago=2)
    pairs = index_build._estrenos_pairs([row], NOW, 2026)
    assert [title_id for title_id, _ in pairs] == ["same"]


class _Conn:
    def __init__(self):
        self.calls = []

    async def execute(self, sql, *args):
        self.calls.append((sql, args))
        return f"INSERT 0 {len(args[0])}"


def test_insert_links_chunks_and_returns_inserted_rows():
    conn = _Conn()
    links = [(f"t{i}", "genero-x", i) for i in range(5001)]
    inserted = asyncio.run(index_build._insert_links(conn, links))
    assert inserted == 5001
    assert len(conn.calls) == 2
    sql = conn.calls[0][0]
    assert "unnest($1::text[], $2::text[], $3::int[])" in sql


def test_meta_query_carries_freshness_and_identity():
    import inspect

    source = inspect.getsource(index_build._title_meta)
    assert "first_seen_at" in source
    assert "t.source" in source
    assert "e.tmdb_id" in source


def test_estrenos_does_not_repeat_the_top10_by_id_or_work():
    import inspect

    source = inspect.getsource(index_build.build)
    # Se excluyen las 10 primeras de tendencias por título Y por tmdb_id (una
    # temporada distinta de la misma obra también sería la misma tarjeta).
    assert "position <= 10" in source
    assert "top_tmdb" in source
    assert 'category_id = "estrenos"' in source or "category_id = 'estrenos'" in source


def test_popularity_refresh_cycles_by_age_instead_of_freezing():
    import inspect

    import tmdb

    # La ficha se refresca por antigüedad y se deja marcada: sin `popularity_at`
    # se volvería a pedir la misma (o nunca se refrescaría el resto).
    source = inspect.getsource(tmdb.refresh_popularity)
    assert "popularity_at" in source
    assert "make_interval(days => $2)" in source
    assert "update enrichment" in source
    assert "genres = $4" in source


def test_weekly_trending_refreshes_and_never_stores_half_a_photo():
    import inspect

    import tmdb

    source = inspect.getsource(tmdb.sync_trending)
    assert "TRENDING_REFRESH_HOURS" in source
    assert 'for media in ("movie", "tv")' in source
    assert 'raise RuntimeError(f"trending {media} vino vacío")' in source
    assert "delete from weekly_trending where week = $1" in source
    # Ya no se salta el refresco solo porque haya filas: la foto se rehace.
    assert "if have:" not in source

