"""Antirregresiones del agrupador de temporadas (lógica pura, sin DB ni red)."""

import grouping


def _row(id, title, kind="series", score=7.0, year=2020):
    return {"id": id, "title": title, "kind": kind, "source": "xtv",
            "year": year, "score": score, "poster_url": "",
            "synopsis": "", "play_ref": f"xtv:{id}"}


class FakeTx:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False


class FakeConn:
    def __init__(self, owner):
        self.owner = owner

    def transaction(self):
        return FakeTx()

    async def execute(self, query, *args):
        self.owner.statements.append((query, args))


class FakePool:
    def __init__(self, rows):
        self.rows = rows
        self.statements = []

    async def fetch(self, *a):
        return self.rows

    def acquire(self):
        return FakeAcquire(self)


class FakeAcquire:
    def __init__(self, owner):
        self.owner = owner

    async def __aenter__(self):
        return FakeConn(self.owner)

    async def __aexit__(self, *a):
        return False


def test_marks():
    assert grouping.split_season("Show T1", "series") == ("Show", 1)
    assert grouping.split_season("Show tem1", "series") == ("Show", 1)
    assert grouping.split_season("Show Temp. 2", "anime") == ("Show", 2)
    assert grouping.split_season("Show 2ª Temporada", "series") == ("Show", 2)
    assert grouping.split_season("Show T1 (2022)", "series") == ("Show", 1)
    assert grouping.split_season("Show 2 (2024)", "series") == ("Show", 2)


def test_marks_rejected():
    assert grouping.split_season("Avatar 2", "movie") is None
    assert grouping.split_season("Show Cap 3", "series") is None
    assert grouping.split_season("Show Parte 1", "series") is None
    assert grouping.split_season("Takers (2010)", "movie") is None
    assert grouping.split_season("Gladiator (2000)", "movie") is None


def test_qualifiers():
    assert grouping.strip_qualifier("Show - Español Latino") == "Show"
    assert grouping.strip_qualifier("Show (HD)") == "Show"
    assert grouping.strip_qualifier("Show HD") == "Show"
    # Sin separador no se toca: podría ser el nombre real.
    assert grouping.strip_qualifier("Orgullo Latino") == "Orgullo Latino"


def test_canon_unifies_kind_and_dub():
    assert grouping.canon_key("Los Simpson T37", "anime") == ("series", "los simpson")
    assert grouping.canon_key("Los Simpson T36", "series") == ("series", "los simpson")
    assert grouping.canon_key("Los Simpson - Español Latino T5", "anime") == ("series", "los simpson")
    assert grouping.canon_key("Orgullo Latino T1", "series") == ("series", "orgullo latino")


async def _run_group(rows):
    import asyncio
    pool = FakePool(rows)
    await grouping.group(pool)
    return pool.statements


def test_group_unifies_cross_kind_and_plain():
    import asyncio
    rows = [
        _row("a", "Los Simpson T37", "anime"),
        _row("b", "Los Simpson T36", "series"),
        _row("c", "Los Simpson - Español Latino T5", "anime"),
        _row("d", "Los Simpson - Español Latino", "anime"),
        _row("e", "Avatar 2", "movie"),
    ]
    statements = asyncio.run(_run_group(rows))
    groups = [s for s in statements if "insert into title_groups" in s[0]]
    members = [s for s in statements if "insert into group_members" in s[0]]
    assert len(groups) == 1
    # Base corta manda en pantalla.
    assert groups[0][1][1] == "Los Simpson"
    seasons = sorted(m[1][2] for m in members)
    assert seasons == [0, 5, 36, 37]


def test_marked_never_becomes_plain():
    import asyncio
    rows = [_row("a", "Show T1"), _row("b", "Show T2")]
    statements = asyncio.run(_run_group(rows))
    members = [s for s in statements if "insert into group_members" in s[0]]
    assert sorted(m[1][2] for m in members) == [1, 2]
