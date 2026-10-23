"""«Todo el catálogo»: ningún título queda sin una fila navegable.

El 2026-10-22 se midieron 691 títulos que se encontraban por nombre pero no vivían
en ninguna categoría (los que llegan por el buscador del portal y las repisas sin
colección). Este guardián fija la categoría que los recoge: la primera vez ordena
todo por popularidad; después solo agrega los nuevos al final.
"""

import asyncio

import index_build


class _Conn:
    def __init__(self, pool):
        self.pool = pool

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return False

    async def execute(self, sql, *args):
        self.pool.calls.append((sql, args))
        if "insert into title_categories" in sql:
            return "INSERT 0 42"
        return "INSERT 0 1"

    async def fetchval(self, sql, *args):
        self.pool.calls.append((sql, args))
        return self.pool.has_rows


class _Pool:
    def __init__(self, has_rows: bool = False):
        self.has_rows = has_rows
        self.calls = []

    def acquire(self):
        return _Conn(self)


def test_catalog_category_links_every_orphan_title():
    pool = _Pool(False)
    assert asyncio.run(index_build.ensure_catalog_category(pool)) == 42
    statements = [sql for sql, _ in pool.calls]
    args = [arg for _, values in pool.calls for arg in values]
    assert any("insert into categories" in sql for sql in statements)
    assert "Todo el catálogo" in args
    assert "catalogo-completo" in args
    insert = next(sql for sql in statements if "insert into title_categories" in sql)
    assert "not exists" in insert
    assert "title_categories" in insert
    # Primera vez: posiciones desde 0 (orden por popularidad).
    assert "1000000" not in insert


def test_catalog_category_appends_new_titles_after_the_first_fill():
    pool = _Pool(True)
    asyncio.run(index_build.ensure_catalog_category(pool))
    insert = next(sql for sql, _ in pool.calls if "insert into title_categories" in sql)
    # Ya había filas: los nuevos van al final, sin reordenar lo que el usuario ve.
    assert "1000000" in insert
