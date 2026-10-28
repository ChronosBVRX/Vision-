"""Antirregresiones del agrupador de temporadas (lógica pura, sin DB ni red).

Cubre el caso real «House» + «House M.D.» (una sola ficha con 8 temporadas) y las
protecciones para no juntar obras que solo se parecen: secuelas, ediciones y spin-offs
(«9-1-1» / «9-1-1: Nashville», «Vikingos» / «Vikingos: Valhalla», «Power Rangers» /
«Power Rangers Megaforce») ni números distintos («Deutschland 83» / «Deutschland 86»).
"""

import asyncio

import grouping


def _row(id, title, kind="series", score=7.0, year=2020, program_type="teleplay",
         tmdb_id=0, conf=0.0, original="", localized="", alts=(), translated=(),
         popularity=0.0, source="xtv"):
    return {"id": id, "title": title, "kind": kind, "source": source,
            "year": year, "score": score, "poster_url": "", "synopsis": "",
            "play_ref": f"{source}:{id}", "program_type": program_type,
            "popularity": popularity, "tmdb_id": tmdb_id, "match_confidence": conf,
            "original_title": original, "localized_title": localized,
            "alt_titles": list(alts), "translated_titles": list(translated)}


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


def _run_group(rows):
    pool = FakePool(rows)
    asyncio.run(grouping.group(pool))
    return pool.statements


def _groups(statements):
    """{base_title: {season: title_id}} a partir de las sentencias del agrupador."""
    groups, members = {}, {}
    for query, args in statements:
        if "insert into title_groups" in query:
            groups[args[0]] = args[1]
        elif "insert into group_members" in query:
            members.setdefault(args[0], {})[args[2]] = args[1]
    return {groups[gid]: seasons for gid, seasons in members.items()}


def test_marks():
    assert grouping.split_season("Show T1", "series") == ("Show", 1)
    assert grouping.split_season("Show tem1", "series") == ("Show", 1)
    assert grouping.split_season("Show Temp. 2", "anime") == ("Show", 2)
    assert grouping.split_season("Show 2ª Temporada", "series") == ("Show", 2)
    assert grouping.split_season("Show T1 (2022)", "series") == ("Show", 1)
    assert grouping.split_season("Show 2 (2024)", "series") == ("Show", 2)


def test_glued_mark_is_not_part_of_the_name():
    """El portal pega la marca a la palabra: «La casa de papelT1», «Malcolm el de EnmedioT1»."""
    assert grouping.split_season("La casa de papelT1", "series") == ("La casa de papel", 1)
    assert grouping.split_season("Malcolm el de EnmedioT1", "series") == ("Malcolm el de Enmedio", 1)
    assert grouping.base_name("La casa de papelT1") == "La casa de papel"
    # Un «T1» suelto dentro de una palabra que no es marca no se toca.
    assert grouping.split_season("Beverly Hills 90210", "series") is None


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


def test_fold_name_joins_initials_and_number_words():
    assert grouping.fold_name("House M.D.") == "house md"
    assert grouping.fold_name("House MD") == "house md"
    assert grouping.fold_name("S.W.A.T.") == "swat"
    assert grouping.names_compatible("Hawaii Five-0", "Hawai 5.0") == "tokens"


def test_canon_unifies_kind_and_dub():
    assert grouping.canon_key("Los Simpson T37", "anime") == ("series", "los simpson")
    assert grouping.canon_key("Los Simpson T36", "series") == ("series", "los simpson")
    assert grouping.canon_key("Los Simpson - Español Latino T5", "anime") == ("series", "los simpson")
    assert grouping.canon_key("Orgullo Latino T1", "series") == ("series", "orgullo latino")


def test_names_compatible_joins_the_same_show():
    """El caso del usuario y las variantes de escritura/idioma más comunes."""
    assert grouping.names_compatible("House", "House M.D.") == "initials"
    assert grouping.names_compatible("Los Simpson", "Los Simpsons") == "tokens"
    assert grouping.names_compatible("Anatomía de Grey", "Anatomía según Grey") == "tokens"
    assert grouping.names_compatible("Buffy, cazavampiros", "Buffy, la cazavampiros") == "tokens"
    assert grouping.names_compatible("Hawai 5.0", "Hawaii Five-0") == "tokens"
    assert grouping.names_compatible("La Casa de Papel", "Casa de Papel") == "tokens"
    assert grouping.names_compatible("Phineas y Ferb", "Phineas e Ferb") == "tokens"
    assert grouping.names_compatible("Los Simpson", "Los Simpson") == "same"


def test_names_compatible_never_joins_siblings():
    """Secuelas, ediciones y spin-offs se quedan aparte aunque compartan el inicio."""
    assert grouping.names_compatible("9-1-1", "9-1-1: Nashville") == ""
    assert grouping.names_compatible("Below Deck", "Below Deck Mediterranean") == ""
    assert grouping.names_compatible("Vikingos", "Vikingos: Valhalla") == ""
    assert grouping.names_compatible("Power Rangers", "Power Rangers Megaforce") == ""
    assert grouping.names_compatible("Power Rangers", "Power Rangers S.P.D") == ""
    assert grouping.names_compatible("Tokyo Ghoul", "Tokyo Ghoul: Re") == ""
    assert grouping.names_compatible("Deutschland 83", "Deutschland 86") == ""
    assert grouping.names_compatible("Deutschland 89", "Deutschland 86") == ""
    assert grouping.names_compatible("Love Is Blind", "Love Is Blind: UK") == ""
    assert grouping.names_compatible("El juego del calamar", "El juego del calamar: El desafío") == ""
    # El remake coreano NO es la Casa de Papel original.
    assert grouping.names_compatible("Casa de Papel", "La casa de papel: Corea") == ""


def test_is_groupable_only_real_series():
    assert grouping.is_groupable("series", "teleplay")
    assert grouping.is_groupable("kids", "teleplay")
    assert grouping.is_groupable("anime", "")
    assert not grouping.is_groupable("series", "movie")
    assert not grouping.is_groupable("kids", "movie")
    assert not grouping.is_groupable("movie", "movie")


def test_group_unifies_cross_kind_and_plain():
    rows = [
        _row("a", "Los Simpson T37", "anime"),
        _row("b", "Los Simpson T36", "series"),
        _row("c", "Los Simpson - Español Latino T5", "anime"),
        _row("d", "Los Simpson - Español Latino", "anime"),
        _row("e", "Avatar 2", "movie"),
    ]
    groups = _groups(_run_group(rows))
    assert list(groups) == ["Los Simpson"]
    assert sorted(groups["Los Simpson"]) == [0, 5, 36, 37]


def test_marked_never_becomes_plain():
    groups = _groups(_run_group([_row("a", "Show T1"), _row("b", "Show T2")]))
    assert sorted(groups["Show"]) == [1, 2]


def test_movie_never_joins_a_season_group():
    """«Zootopia» (película kids) + «Zootopia+» (serie): jamás un grupo de temporadas."""
    rows = [
        _row("m", "Zootopia", "kids", program_type="movie"),
        _row("s", "Zootopia+ Temp.1", "kids", program_type="teleplay"),
    ]
    assert _groups(_run_group(rows)) == {}


def test_movie_sequels_never_group_as_seasons():
    """«Intensa mente» + «Intensa mente 2» son dos películas, no T1/T2 de una serie."""
    rows = [
        _row("a", "Intensa mente", "anime", program_type="movie"),
        _row("b", "Intensa mente 2", "anime", program_type="movie"),
    ]
    assert _groups(_run_group(rows)) == {}


def test_house_and_house_md_become_one_show():
    """Caso del usuario: «House» y «House M.D.» son la MISMA serie (TMDB 1408)."""
    rows = [
        _row("h1", "House T1", tmdb_id=1408, conf=1.0, original="House", localized="Dr. House"),
        _row("h2", "House T2", tmdb_id=1408, conf=1.0, original="House", localized="Dr. House"),
        _row("h6", "House T6", tmdb_id=1408, conf=1.0, original="House", localized="Dr. House"),
        _row("h8", "House T8", tmdb_id=1408, conf=1.0, original="House", localized="Dr. House"),
        _row("m3", "House M.D. T3", tmdb_id=1408, conf=0.65, original="House", localized="Dr. House"),
        _row("m4", "House M.D. T4", tmdb_id=1408, conf=0.65, original="House", localized="Dr. House"),
        _row("m5", "House M.D. T5", tmdb_id=1408, conf=0.65, original="House", localized="Dr. House"),
        _row("m7", "House M.D. T7", tmdb_id=1408, conf=0.65, original="House", localized="Dr. House"),
    ]
    groups = _groups(_run_group(rows))
    assert list(groups) == ["House"]
    assert sorted(groups["House"]) == [1, 2, 3, 4, 5, 6, 7, 8]


def test_similar_names_with_different_tmdb_ids_stay_apart():
    """«The Office» (US) y «The Office» (UK) son obras distintas: distinto tmdb_id."""
    rows = [
        _row("us1", "The Office T1", tmdb_id=2316, conf=1.0, original="The Office"),
        _row("us2", "The Office T2", tmdb_id=2316, conf=1.0, original="The Office"),
        _row("uk1", "The Office (UK) T1", tmdb_id=78107, conf=1.0, original="The Office"),
        _row("uk2", "The Office (UK) T2", tmdb_id=78107, conf=1.0, original="The Office"),
    ]
    groups = _groups(_run_group(rows))
    assert sorted(groups) == ["The Office", "The Office (UK)"]


def test_power_rangers_sagas_are_not_one_show():
    """Todos comparten tmdb_id (match malo de TMDB) pero son series distintas."""
    rows = [
        _row("a", "Power Rangers T1", tmdb_id=2328, conf=1.0, original="Power Rangers"),
        _row("b", "Power Rangers T2", tmdb_id=2328, conf=1.0, original="Power Rangers"),
        _row("c", "Power Rangers Megaforce T1", tmdb_id=2328, conf=0.65, original="Power Rangers"),
        _row("d", "Power Rangers Megaforce T2", tmdb_id=2328, conf=0.65, original="Power Rangers"),
        _row("e", "Power Rangers S.P.D T1", tmdb_id=2328, conf=0.65, original="Power Rangers"),
        _row("f", "Power Rangers S.P.D T2", tmdb_id=2328, conf=0.65, original="Power Rangers"),
    ]
    groups = _groups(_run_group(rows))
    assert sorted(groups) == ["Power Rangers", "Power Rangers Megaforce", "Power Rangers S.P.D"]


def test_translated_name_joins_when_tmdb_confirms_it():
    """«Las Kardashian» = «Keeping Up with the Kardashians»: mismo nombre oficial de TMDB."""
    rows = [
        _row("a", "Keeping Up with the Kardashians T19", tmdb_id=14814, conf=1.0,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("b", "Keeping Up with the Kardashians T20", tmdb_id=14814, conf=0.85,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("c", "Las Kardashian T1", tmdb_id=14814, conf=0.85,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("d", "Las Kardashian T3", tmdb_id=14814, conf=0.85,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
    ]
    groups = _groups(_run_group(rows))
    assert list(groups) == ["Las Kardashian"]
    assert sorted(groups["Las Kardashian"]) == [1, 3, 19, 20]


def test_two_name_variants_merge_without_dragging_the_parent_show():
    """«Vikingos: Valhalla» + «Vikings: Valhalla» se juntan; «Vikingos» (la original) no."""
    rows = [
        _row("v1", "Vikingos T1", tmdb_id=116135, conf=0.85,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
        _row("v2", "Vikingos T2", tmdb_id=116135, conf=0.85,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
        _row("s1", "Vikingos: Valhalla T1", tmdb_id=116135, conf=1.0,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
        _row("s3", "Vikingos: Valhalla T3", tmdb_id=116135, conf=0.85,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
        _row("e1", "Vikings: Valhalla Temp.1", tmdb_id=116135, conf=1.0,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
        _row("e2", "Vikings: Valhalla Temp.2", tmdb_id=116135, conf=1.0,
             original="Vikings: Valhalla", localized="Vikingos: Valhalla"),
    ]
    groups = _groups(_run_group(rows))
    assert "Vikingos" in groups
    assert sorted(groups["Vikingos"]) == [1, 2]  # la serie original queda sola
    valhalla = [g for name, g in groups.items() if name.lower().endswith("valhalla")]
    assert len(valhalla) == 1
    assert sorted(valhalla[0]) == [1, 2, 3]  # las dos variantes, una temporada por número


def test_translated_name_needs_a_firm_match():
    """Sin confianza suficiente no se fusiona por traducción (mejor repetido que mezclado)."""
    rows = [
        _row("a", "Keeping Up with the Kardashians T1", tmdb_id=14814, conf=0.6,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("b", "Keeping Up with the Kardashians T2", tmdb_id=14814, conf=0.6,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("c", "Las Kardashian T1", tmdb_id=14814, conf=0.6,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
        _row("d", "Las Kardashian T2", tmdb_id=14814, conf=0.6,
             original="Keeping Up with the Kardashians", localized="Las Kardashian"),
    ]
    groups = _groups(_run_group(rows))
    assert sorted(groups) == ["Keeping Up with the Kardashians", "Las Kardashian"]


def test_ambiguous_name_never_bridges_translations():
    """Un nombre que apunta a dos entidades («Aprendiendo a vivir») no sirve de puente."""
    rows = [
        _row("a", "Heartbreak High T1", tmdb_id=158154, conf=1.0,
             original="Heartbreak High", localized="Aprendiendo a vivir"),
        _row("b", "Heartbreak High T2", tmdb_id=158154, conf=1.0,
             original="Heartbreak High", localized="Aprendiendo a vivir"),
        # El mismo nombre localizado bajo dos entidades distintas: ambiguo.
        _row("d", "Aprendiendo a vivir T8", tmdb_id=1777, conf=1.0,
             original="Yo y el mundo", localized="Aprendiendo a vivir"),
        _row("e", "Yo y el mundo T1", tmdb_id=1777, conf=1.0,
             original="Yo y el mundo", localized="Aprendiendo a vivir"),
    ]
    groups = _groups(_run_group(rows))
    # El nombre idéntico manda (regla de siempre), pero la traducción NO tiende el puente.
    in_heartbreak = groups.get("Heartbreak High", {})
    assert "d" not in in_heartbreak.values() and "e" not in in_heartbreak.values()


def test_duplicate_season_stays_in_the_group():
    """Al fusionar dos nombres puede haber dos «T5»: el grupo las guarda y el API sirve una."""
    rows = [
        _row("a", "Los Simpson T5", tmdb_id=456, conf=1.0, original="The Simpsons"),
        _row("b", "Los Simpson T6", tmdb_id=456, conf=1.0, original="The Simpsons"),
        _row("c", "Los Simpsons T5", tmdb_id=456, conf=0.9, original="The Simpsons", source="magis"),
        _row("d", "Los Simpsons T7", tmdb_id=456, conf=0.9, original="The Simpsons", source="magis"),
    ]
    statements = _run_group(rows)
    groups = [args for query, args in statements if "insert into title_groups" in query]
    assert [g[1] for g in groups] == ["Los Simpson"]
    members = [args for query, args in statements if "insert into group_members" in query]
    assert sorted(m[2] for m in members) == [5, 5, 6, 7]
