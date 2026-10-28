"""`normalize()` no debe deshacer el género primario.

Las repisas de la fuente se llaman igual que un género («Acción», «Comedia»…). Al
fusionarlas con la categoría de género se colaban títulos con OTROS géneros y hasta
tráilers: el mismo problema que se acababa de cerrar. La regla es simple: si ya hay
una fila curada (género o especial), la repisa de fuente con ese nombre se retira;
sus títulos siguen en «Todo el catálogo» y en su género primario.
"""

import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import index_build


class _Conn:
    def __init__(self, rows):
        self.rows = rows
        self.calls = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def fetch(self, sql, *args):
        return self.rows

    async def execute(self, sql, *args):
        self.calls.append((sql, args))
        if sql.lstrip().startswith("insert into title_categories"):
            return "INSERT 0 1"
        return "DELETE 2"


class _Pool:
    def __init__(self, rows):
        self.conn = _Conn(rows)

    def acquire(self):
        return self.conn


def test_source_shelf_with_the_same_name_as_a_genre_is_removed_not_merged():
    rows = [
        {"id": "genero-accion", "title": "Acción", "kind": "genre", "titles": 100},
        {"id": "xtv-movies-777", "title": "Acción", "kind": "source", "titles": 500},
        {"id": "masmobile_accion", "title": "Acción", "kind": "source", "titles": 50},
    ]
    pool = _Pool(rows)
    merged = asyncio.run(index_build.normalize(pool))
    deletes = [
        set(args[0])
        for sql, args in pool.conn.calls
        if "delete from categories where id = any" in sql
    ]
    assert {"xtv-movies-777", "masmobile_accion"} in deletes
    # No se copiaron sus enlaces al género (eso reintroducía las repeticiones).
    copies = [
        args
        for sql, args in pool.conn.calls
        if "insert into title_categories" in sql and "from title_categories" in sql
    ]
    assert not any(set(args[1]) & {"xtv-movies-777", "masmobile_accion"} for args in copies)
    assert merged >= 2


def test_source_shelves_still_merge_when_there_is_no_curated_row():
    rows = [
        {"id": "xtv-movies-netflix", "title": "Netflix", "kind": "source", "titles": 30},
        {"id": "magis-netflix", "title": "Netflix Movie", "kind": "source", "titles": 20},
    ]
    pool = _Pool(rows)
    asyncio.run(index_build.normalize(pool))
    copies = [
        set(args[1])
        for sql, args in pool.conn.calls
        if "insert into title_categories" in sql and "from title_categories" in sql
    ]
    assert {"magis-netflix"} in copies


def test_special_rows_also_fend_off_source_shelves():
    rows = [
        {"id": "estrenos", "title": "Estrenos", "kind": "special", "titles": 100},
        {"id": "xtv-movies-estrenos-shelf", "title": "Estrenos", "kind": "source", "titles": 400},
    ]
    pool = _Pool(rows)
    asyncio.run(index_build.normalize(pool))
    deletes = [
        set(args[0])
        for sql, args in pool.conn.calls
        if "delete from categories where id = any" in sql
    ]
    assert {"xtv-movies-estrenos-shelf"} in deletes
    copies = [
        args
        for sql, args in pool.conn.calls
        if "insert into title_categories" in sql and "from title_categories" in sql
    ]
    assert not any("xtv-movies-estrenos-shelf" in set(args[1]) for args in copies)
