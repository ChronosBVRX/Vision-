"""Buscador con paridad del portal + catálogo que crece con lo que la gente busca.

Brecha medida EN VIVO el 2026-10-18: buscar «guerra de papas» en Vision+ devolvía
«Juego de tronos» (el fuzzy agarraba «A Guerra dos Tronos», el nombre portugués de
TMDB) mientras la app original sí encontraba «Guerra de Papás». La causa: los
listados del portal topan en 10.000 títulos y las colecciones se bajaban 3 páginas;
la película vive en Comedia página 73. Este módulo fija el contrato del respaldo:
- el buscador del portal se consulta y sus títulos se dan de alta;
- el orden pone primero lo que el índice NO tiene, sin perder el enriquecido;
- el ruido del buscador difuso se recorta;
- la etapa difusa exige TODAS las palabras largas (fin del «Juego de tronos»).
"""

import asyncio
import inspect
from pathlib import Path

import app
import portal


# ----- crypto compartida -----


def test_wire_is_the_same_file_in_api_and_sync():
    root = Path(inspect.getfile(app)).parent
    assert (root / "wire.py").read_bytes() == (root.parent / "sync" / "wire.py").read_bytes()


def test_portal_fold_matches_the_api_fold():
    for query in ("Guerra de Papás", "¿Canción del Ángel?", "Spider-Man: Un nuevo día"):
        assert portal.fold_name(query) == app._fold(query)


# ----- un resultado del portal -> fila del índice -----

GUERRA = {
    "type": "1",
    "contentId": "A93658121D1840E78F958BAFAB9D409C",
    "name": "Guerra de Papás",
    "programType": "movie",
    "duration": "",
    "tags": "Comedy,Family",
    "description": "",
    "director": "Sean Anders",
    "releaseTime": "2016-01-01",
    "contentType": "2",
    "alias": "Daddy's Home",
    "score": 6.2,
    "actorDisplay": "",
    "posterList": [
        {"fileUrl": "https://cdn.example/icon", "fileType": "icon"},
        {"fileUrl": "https://cdn.example/poster", "fileType": "poster"},
    ],
}


def test_guerra_de_papas_maps_to_a_playable_index_row():
    row = portal.item_row(GUERRA)
    assert row is not None
    assert row[0] == "xtv:A93658121D1840E78F958BAFAB9D409C"
    assert row[1] == "xtv" and row[2] == "A93658121D1840E78F958BAFAB9D409C"
    assert row[3] == "movie"
    assert row[4] == "Guerra de Papás"
    assert row[5] == "Daddy's Home"          # alias: el nombre que usa TMDB
    assert row[6] == 2016
    assert row[8] == "https://cdn.example/poster"  # póster antes que icono
    assert row[14] == "xtv:A93658121D1840E78F958BAFAB9D409C"  # play_ref
    assert row[17] == "movie"                # program_type
    assert row[20].startswith("guerra de papas")
    assert "guerradepapas" in row[20]        # compacto: «guerradepapas» también encuentra
    assert "daddy s home" in row[20]


def test_portal_item_kinds_follow_program_type():
    assert portal.item_kind({"programType": "movie", "type": "1"}) == "movie"
    assert portal.item_kind({"programType": "teleplay", "type": "0"}) == "series"
    assert portal.item_kind({"programType": "anime", "type": "0"}) == "anime"
    assert portal.item_kind({"programType": "kids", "type": "0"}) == "kids"
    # Sin programType reconocible manda el `type` del buscador (1 = película).
    assert portal.item_kind({"type": "1"}) == "movie"
    assert portal.item_kind({"type": "0"}) == "series"


def test_portal_item_without_content_id_is_discarded():
    assert portal.item_row({"name": "Sin id"}) is None
    assert portal.item_row({"contentId": "X", "name": ""}) is None


# ----- la respuesta viene agrupada -----


def test_flatten_reads_every_group_and_dedupes_by_content_id():
    data = {"searchItemList": [
        {"programType": "others", "itemList": [
            {"contentId": "A", "name": "Uno"},
            {"contentId": "B", "name": "Dos"},
            {"contentId": "A", "name": "Uno repetido"},
        ]},
        {"programType": "others", "itemList": [
            {"contentId": "C", "name": "Tres"},
            {"name": "sin id"},
        ]},
    ]}
    assert [i["contentId"] for i in portal.flatten(data)] == ["A", "B", "C"]


def test_keep_items_protects_the_hit_and_filters_fuzzy_noise():
    # Caso real: 264 resultados para «guerra de papas», con Juan Luis Guerra incluido.
    items = [
        {"contentId": "1", "name": "Guerra de Papás"},
        {"contentId": "2", "name": "Guerra de Papás 2"},
        {"contentId": "3", "name": "Juan Luis Guerra 4.40, Entre Mar y Palmeras"},
        {"contentId": "4", "name": "Guerra de Ídolos T1"},
        {"contentId": "5", "name": "Tu madre o la mía: Guerra de suegras"},
        {"contentId": "6", "name": "Prisonero de guerra"},        # fuera por falta «papas»
        {"contentId": "7", "name": "La guerra de papás perdida"},  # contiene ambas
    ]
    kept = portal.keep_items(items, "guerra de papas", keep_first=5, cap=20)
    assert [i["contentId"] for i in kept] == ["1", "2", "3", "4", "5", "7"]


def test_keep_items_drops_portal_noise_for_a_nonsense_query():
    # El portal devuelve «Lassie…» para una consulta sin sentido: no puede entrar.
    # «Una jirafa…» comparte «una», pero es una palabra vacía y tampoco cuenta.
    items = [
        {"contentId": "1", "name": "Lassie. Una nueva aventura"},
        {"contentId": "2", "name": "Una jirafa en el balcón"},
    ]
    assert portal.keep_items(items, "una pelicula que no existe 12345") == []
    assert portal.keep_items(items, "zzzz qqqq xxxx") == []


# ----- orden local + portal -----


def _film_rows(*pairs):
    rows = []
    for pair in pairs:
        rows.append({
            "id": pair[0],
            "title": pair[1],
            "search_text": pair[2] if len(pair) > 2 else "",
        })
    return rows


def test_merge_puts_the_portal_hit_first_when_the_index_lacks_the_exact_title():
    local = _film_rows(("xtv:1", "Juego de tronos"), ("xtv:2", "Vingadores: Guerra Infinita"))
    remote = _film_rows(("xtv:9", "Guerra de Papás"), ("xtv:10", "Guerra de Papás 2"))
    merged = app.merge_search_rows(local, remote, "guerra de papas", 50)
    assert [r["title"] for r in merged] == [
        "Guerra de Papás", "Guerra de Papás 2", "Juego de tronos", "Vingadores: Guerra Infinita",
    ]


def test_merge_keeps_the_local_order_when_the_name_already_exists():
    local = _film_rows(
        ("xtv:1", "El Padrino", "el padrino elpadrino the godfather"),
        ("xtv:2", "El Padrino Parte III"),
    )
    remote = _film_rows(("xtv:1", "El Padrino"), ("xtv:10", "El Padrino II"))
    merged = app.merge_search_rows(local, remote, "el padrino", 50)
    # El local manda; el id repetido se dedupe y lo que solo trae el portal se suma.
    assert [r["id"] for r in merged] == ["xtv:1", "xtv:2", "xtv:10"]


def test_merge_recognizes_the_name_through_tmdb_aliases_not_just_the_title():
    # «buscando a nemo»: el título visible es «Finding Nemo», pero su search_text
    # trae el nombre es-MX. Sin esto, el ruido del portal lo tapaba (regresión real).
    local = _film_rows(("xtv:5", "Finding Nemo", "finding nemo buscando a nemo procurando nemo"))
    remote = _film_rows(
        ("xtv:6", "Buscando a Wanda T1"),
        ("xtv:7", "Punto Nemo T1"),
        ("xtv:5", "Finding Nemo"),
    )
    merged = app.merge_search_rows(local, remote, "buscando a nemo", 50)
    assert [r["id"] for r in merged] == ["xtv:5", "xtv:6", "xtv:7"]


def test_merge_caps_and_survives_without_portal():
    local = _film_rows(("xtv:1", "A"), ("xtv:2", "B"), ("xtv:3", "C"))
    remote = _film_rows(("xtv:8", "D"), ("xtv:9", "E"))
    assert len(app.merge_search_rows(local, remote, "a", 4)) == 4
    assert app.merge_search_rows(local, [], "a", 4) == local


# ----- etapa difusa: todas las palabras largas -----


def test_fuzzy_probes_skip_short_words():
    assert app.fuzzy_probes("guerra de papas") == ["guerra", "papas"]
    assert app.fuzzy_probes("termintor 2") == ["termintor"]
    assert app.fuzzy_probes("x y z") == []
    # Las palabras vacías no son señal: una consulta sin sentido no debe «encontrar».
    assert app.fuzzy_probes("para este the") == []


def test_fuzzy_requires_every_long_word_against_the_title(monkeypatch):
    class _Capture:
        def __init__(self):
            self.sql = ""
            self.params = ()

        async def fetch(self, sql, *params):
            self.sql, self.params = sql, params
            return []

    capture = _Capture()
    monkeypatch.setattr(app, "db", lambda: capture)
    assert asyncio.run(app._search_fuzzy("guerra de papas", 10)) == []
    # «guerra» y «papas» viajan como $1 y $2 y AMBAS deben superar el umbral.
    assert capture.params[:2] == ("guerra", "papas")
    assert "$1" in capture.sql and "$2" in capture.sql
    assert capture.sql.count("word_similarity") >= 2
    assert " and " in capture.sql
    assert "limit $3" in capture.sql
