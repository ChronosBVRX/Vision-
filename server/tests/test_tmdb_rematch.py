"""Pruebas del re-match TMDB: el candado por versión del emparejador y el medidor.

La lección del backlog de series (2026-10): el fix del buscador llegó justo después de
una pasada que marcó ~16k «sin match», y el cooldown de 14 días dejó al fix sin poder
correr (los «elegibles hoy» eran CERO). El candado ahora es por VERSIÓN (`match_algo`):
subir ALGO_VERSION da una pasada fresca a todo el backlog, una sola vez, sin esperar.
"""

import inspect
import sys
from pathlib import Path

# Añadir server/sync al sys.path para poder importar tmdb
sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import tmdb


def test_rematch_bypasses_cooldown_when_the_matcher_changed():
    source = inspect.getsource(tmdb.rematch_batch)
    assert "e.match_algo is distinct from $3" in source
    assert "e.updated_at < now() - make_interval(days => $1)" in source
    assert "ALGO_VERSION" in source


def test_no_match_stamps_the_matcher_version():
    # Cada intento queda firmado con su versión: sin esto, una fila reintentada por el
    # emparejador nuevo seguiría pareciendo «ya intentada» y el cooldown la congelaría.
    source = inspect.getsource(tmdb._mark_no_match)
    assert "match_algo" in source
    assert "ALGO_VERSION" in source


def test_successful_matches_also_record_the_version():
    source = inspect.getsource(tmdb._store_match)
    assert "match_algo" in source


def test_algo_version_is_a_meaningful_tag():
    assert tmdb.ALGO_VERSION.strip()
    assert " " not in tmdb.ALGO_VERSION


def test_coverage_line_reads_like_a_meter():
    rows = [
        {"kind": "series", "total": 17198, "matched": 12000, "fresh": 0},
        {"kind": "movie", "total": 11288, "matched": 10012, "fresh": 3},
    ]
    line = tmdb.format_coverage(rows)
    assert "series 12000/17198 (70%)" in line
    assert "movie 10012/11288 (89%)" in line
    # Mayor primero: las series lideran el catálogo y deben leerse primero.
    assert line.index("series") < line.index("movie")


def test_coverage_line_survives_empty_and_zero_rows():
    assert tmdb.format_coverage([]) == ""
    assert tmdb.format_coverage([{"kind": "x", "total": 0, "matched": 0, "fresh": 0}]) == "x 0/0 (0%)"
