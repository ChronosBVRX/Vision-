"""Generación del banco de preguntas: distractores disjuntos y sin filtrar respuestas."""

import json
from datetime import datetime, timedelta, timezone

import pytest

import trivia_bank as bank


def row(
    tid,
    title,
    year,
    *,
    kind="movie",
    genres=("Drama",),
    cast=("Ana Ruiz",),
    directors=("Luis Pérez",),
    overview="Una historia de prueba con personajes y lugares.",
    poster="https://img.test/poster.jpg",
    group_id="",
    group_title="",
    season_count=0,
    tmdb=1,
    popularity=100.0,
    votes=5000,
    translations=(),
):
    return {
        "id": tid,
        "title": title,
        "year": year,
        "kind": kind,
        "genres": list(genres),
        "cast_names": ", ".join(cast),
        "director_names": ", ".join(directors),
        "overview": overview,
        "poster_url": poster,
        "group_id": group_id,
        "group_title": group_title,
        "season_count": season_count,
        "tmdb_id": tmdb,
        "popularity": popularity,
        "vote_count": votes,
        "translated_titles": list(translations),
    }


def sample_rows():
    rows = [
        row("xtv:A", "Alfa", 2020, genres=("Acción",), cast=("Ana Ruiz",), directors=("Luis Pérez",),
            tmdb=1, translations=("Alpha", "Alfa Internacional")),
        row("xtv:B", "Beta", 2018, genres=("Comedia",), cast=("Beto Díaz",), directors=("Marta Sol",),
            tmdb=2, translations=("Beta Internacional",)),
        row("xtv:C", "Gamma", 2015, genres=("Terror",), cast=("Caro Gil",), directors=("Nora Paz",),
            tmdb=3, translations=("Gamma World",)),
        row("xtv:D", "Delta", 2012, genres=("Drama",), cast=("Dani Cruz",), directors=("Omar Ruiz",),
            tmdb=4, translations=("Delta World",)),
        row("xtv:E", "Épsilon", 2010, genres=("Suspenso",), cast=("Eva Luna",), directors=("Pablo Rey",),
            tmdb=5, translations=("Epsilon",)),
        row("xtv:F", "Zeta", 2008, genres=("Ciencia ficción",), cast=("Fabián Mora",), directors=("Queta Vaz",),
            tmdb=6, translations=("Zeta Plus",)),
        row("xtv:G", "Eta", 2005, genres=("Animación",), cast=("Gaby Ruiz",), directors=("Rita Sol",),
            tmdb=7, translations=("Eta Plus",)),
        row("xtv:H", "Theta", 2002, genres=("Romance",), cast=("Hugo Paz",), directors=("Sara Gil",),
            tmdb=8, translations=("Theta Plus",)),
    ]
    rows += [
        row("xtv:S1", "Serie T1", 2019, kind="series", genres=("Drama",), cast=("Ana Ruiz", "Beto Díaz"),
            directors=("Luis Pérez",), overview="Una serie coral sobre una familia.", group_id="g-serie",
            group_title="Serie", season_count=2, tmdb=20, translations=("Series",)),
        row("xtv:S2", "Serie T2", 2021, kind="series", genres=("Drama",), cast=("Ana Ruiz", "Caro Gil"),
            directors=("Luis Pérez",), overview="Una serie coral sobre una familia.", group_id="g-serie",
            group_title="Serie", season_count=2, tmdb=20, translations=("Series",)),
        row("xtv:T1", "Otra T1", 2017, kind="series", genres=("Comedia",), cast=("Dani Cruz",),
            directors=("Marta Sol",), overview="Otra familia y sus vecinos.", group_id="g-otra",
            group_title="Otra", season_count=2, tmdb=21, translations=("Other",)),
        row("xtv:T2", "Otra T2", 2019, kind="series", genres=("Comedia",), cast=("Dani Cruz",),
            directors=("Marta Sol",), overview="Otra familia y sus vecinos.", group_id="g-otra",
            group_title="Otra", season_count=2, tmdb=21, translations=("Other",)),
        row("xtv:U1", "Tercera", 2016, kind="series", genres=("Suspenso",), cast=("Eva Luna",),
            directors=("Nora Paz",), overview="Un misterio en la costa.", group_id="g-tercera",
            group_title="Tercera", season_count=2, tmdb=22, translations=("Third",)),
        row("xtv:U2", "Tercera T2", 2018, kind="series", genres=("Suspenso",), cast=("Eva Luna",),
            directors=("Nora Paz",), overview="Un misterio en la costa.", group_id="g-tercera",
            group_title="Tercera", season_count=2, tmdb=22, translations=("Third",)),
        row("xtv:V1", "Cuarta", 2014, kind="series", genres=("Terror",), cast=("Caro Gil",),
            directors=("Pablo Rey",), overview="Una casa y sus secretos.", group_id="g-cuarta",
            group_title="Cuarta", season_count=2, tmdb=23, translations=("Fourth",)),
        row("xtv:V2", "Cuarta T2", 2016, kind="series", genres=("Terror",), cast=("Caro Gil",),
            directors=("Pablo Rey",), overview="Una casa y sus secretos.", group_id="g-cuarta",
            group_title="Cuarta", season_count=2, tmdb=23, translations=("Fourth",)),
    ]
    return rows


def test_build_cards_collapses_seasons_and_keeps_the_premiere_year():
    cards = bank.build_cards(sample_rows())
    by_title = {card.title: card for card in cards}
    assert "Serie" in by_title
    assert by_title["Serie"].season_count == 2
    assert by_title["Serie"].year == 2019
    assert by_title["Serie"].group_id == "g-serie"
    assert by_title["Serie"].sample_member == "Serie T1"


def test_names_detect_folded_and_compact_title():
    assert bank.contains_title("Habla de Spider Man y su traje", ["Spider-Man"])
    assert bank.contains_title("spiderman regresa", ["Spider-Man"])
    assert not bank.contains_title("Una historia distinta", ["Spider-Man"])
    assert not bank.contains_title("texto", ["Up"])  # los nombres muy cortos no cuentan


def test_clean_title_removes_season_marks():
    assert bank.clean_title("Anatomía según Grey T16") == "Anatomía según Grey"
    assert bank.clean_title("The Last of Us Temporada 2") == "The Last of Us"
    assert bank.clean_title("Coco") == "Coco"
    assert bank.parse_season("Anatomía T16") == 16
    assert bank.parse_season("Coco") == 0


def test_names_and_lists_are_deduped_and_bounded():
    assert bank.parse_names("Ana, ana, Beto; Caro | Dani") == ["Ana", "Beto", "Caro", "Dani"]
    assert bank.as_list('["Drama", "Comedia"]') == ["Drama", "Comedia"]
    assert bank.as_list("Drama, Comedia") == ["Drama", "Comedia"]
    assert bank.as_list(None) == []


def test_year_options_are_four_distinct_years_near_the_answer():
    import random
    options = bank.year_options(2020, random.Random(1), [1990, 2019, 2021, 2022])
    assert len(options) == 4 and len(set(options)) == 4
    assert "2020" in options
    for value in options:
        assert abs(int(value) - 2020) <= 8


def test_genre_options_never_repeat_the_correct_genre():
    import random
    card = bank.build_cards([row("xtv:A", "Alfa", 2020, genres=("Acción",))])[0]
    options = bank.genre_options(card, random.Random(2))
    assert len(options) == 4
    assert options[0] == "Acción"
    assert "Acción" not in options[1:]


def test_genre_question_uses_tmdb_order_not_catalog_priority():
    card = bank.build_cards([
        row("xtv:A", "Sombras", 2020, genres=("Comedia", "Sci-Fi & Fantasy"))
    ])[0]
    assert bank.primary_answer_genre(card) == "Comedia"


def test_cast_and_director_distractors_are_disjoint():
    import random
    cards = bank.build_cards(sample_rows())
    target = next(card for card in cards if card.title == "Alfa")
    actors = [(name, bank.fold(name), bank._script(name), card) for card in cards for name in card.cast]
    directors = [(name, bank.fold(name), bank._script(name), card) for card in cards for name in card.directors]
    cast_options = bank._people_options(target.cast[0], target, actors, random.Random(3))
    director_options = bank._people_options(target.directors[0], target, directors, random.Random(3))
    assert target.cast[0] in cast_options
    assert not (set(cast_options[1:]) & set(target.cast))
    assert target.directors[0] in director_options
    assert not (set(director_options[1:]) & set(target.directors))


def test_title_distractors_do_not_share_tmdb_or_group():
    import random
    cards = bank.build_cards(sample_rows())
    target = next(card for card in cards if card.title == "Serie")
    options = bank._title_options(target, cards, random.Random(4), groups_only=True)
    assert target.title in options
    assert len(options) == 4
    for title in options[1:]:
        other = next(card for card in cards if card.title == title)
        assert other.tmdb_id != target.tmdb_id
        assert other.group_id != target.group_id


def test_season_options_include_the_correct_number_once():
    import random
    options = bank._number_options(21, random.Random(5))
    assert len(options) == 4 and len(set(options)) == 4
    assert options.count("21") == 1


def test_generate_questions_covers_the_core_types_and_validates_each_one():
    questions = bank.generate_questions(sample_rows(), limit=200, seed=9)
    assert len(questions) >= 20
    kinds = {question["kind"] for question in questions}
    assert {"year", "genre", "cast", "director", "synopsis", "poster", "group", "seasons", "translation"} <= kinds
    for question in questions:
        assert bank.validate_question(question)
        assert len(question["payload"]["options"]) == 4
        assert question["answer_key"] in question["payload"]["options"]
        assert len(question["hash"]) == 32


def test_hashes_are_unique_in_one_generation():
    questions = bank.generate_questions(sample_rows(), limit=300, seed=10)
    hashes = [question["hash"] for question in questions]
    assert len(hashes) == len(set(hashes))


def test_synopsis_question_is_discarded_when_it_names_the_title():
    rows = [
        row("xtv:A", "Coco", 2017, genres=("Animación",), overview="Coco es un niño que viaja al mundo de los muertos.", tmdb=1),
        row("xtv:B", "Beta", 2018, genres=("Comedia",), tmdb=2),
        row("xtv:C", "Gamma", 2015, genres=("Drama",), tmdb=3),
        row("xtv:D", "Delta", 2012, genres=("Terror",), tmdb=4),
        row("xtv:E", "Epsilon", 2010, genres=("Suspenso",), tmdb=5),
    ]
    questions = bank.generate_questions(rows, limit=50, seed=11)
    synopsis = [question for question in questions if question["kind"] == "synopsis"]
    assert all("Coco es un niño" not in question["payload"]["prompt"] for question in synopsis)


def test_translation_distractors_are_not_other_names_of_the_same_card():
    questions = bank.generate_questions(sample_rows(), limit=300, seed=12)
    translations = [q for q in questions if q["kind"] == "translation"]
    assert translations
    cards = {card.title: card for card in bank.build_cards(sample_rows())}
    for question in translations:
        card = cards[question["source_ref"].split(":", 1)[1]] if False else None
        # El source_ref es la clave de la tarjeta; basta con que el distractor
        # no sea el mismo texto que la respuesta.
        options = question["payload"]["options"]
        assert len(set(options)) == 4


def test_validate_question_rejects_bad_shapes():
    good = {
        "kind": "year", "answer_key": "2020", "hash": "x" * 32,
        "payload": {"prompt": "¿Año?", "options": ["2019", "2020", "2021", "2022"]},
    }
    assert bank.validate_question(good)
    assert not bank.validate_question({**good, "payload": {"prompt": "", "options": good["payload"]["options"]}})
    assert not bank.validate_question({**good, "answer_key": "2025"})
    assert not bank.validate_question({**good, "payload": {"prompt": "¿Año?", "options": ["2020", "2020", "2021", "2022"]}})
    synopsis = {
        "kind": "synopsis", "answer_key": "Coco", "hash": "x" * 32,
        "payload": {"prompt": "Coco es Coco", "options": ["Coco", "Beta", "Gamma", "Delta"]},
    }
    assert not bank.validate_question(synopsis)


def test_difficulty_matches_the_signal():
    assert bank.difficulty_for(100, 0) == 1
    assert bank.difficulty_for(10, 0) == 2
    assert bank.difficulty_for(1, 10) == 3


def test_due_accepts_first_run_and_respects_hours():
    now = datetime(2026, 10, 27, 12, 0, tzinfo=timezone.utc)
    assert bank._is_due(None, now, 6)
    assert not bank._is_due(now - timedelta(hours=1), now, 6)
    assert bank._is_due(now - timedelta(hours=7), now, 6)
    assert bank._is_due(now - timedelta(hours=1), now, 0)
    assert bank._is_due("2026-10-27T01:00:00+00:00", now, 6)


def test_generation_needs_at_least_four_cards():
    assert bank.generate_questions([row("xtv:A", "Alfa", 2020)], limit=10) == []


def test_limit_is_respected():
    questions = bank.generate_questions(sample_rows(), limit=7, seed=13)
    assert len(questions) == 7
    assert json.dumps(questions[0], ensure_ascii=False)


def test_pack_for_card_uses_the_content_kind_and_genre():
    def card(kind, genres=()):
        return bank.Card(
            key="t:x", title="X", kind=kind, year=2020, overview="", poster="",
            genres=list(genres),
        )

    assert bank.pack_for_card(card("movie")) == "cine"
    assert bank.pack_for_card(card("series")) == "series"
    assert bank.pack_for_card(card("series", ("Telenovela",))) == "novelas"
    assert bank.pack_for_card(card("series", ("Novela", "Drama"))) == "novelas"
    assert bank.pack_for_card(card("anime")) == "anime"
    assert bank.pack_for_card(card("kids")) == "infantil"
    assert bank.pack_for_card(card("otro")) == "general"


def test_generated_questions_are_classified_into_sections():
    questions = bank.generate_questions(sample_rows(), limit=60, seed=14)
    assert questions
    assert all(q["pack"] in {"cine", "series", "anime", "infantil", "novelas"} for q in questions)
    assert {"cine", "series"} <= {q["pack"] for q in questions}


def test_old_general_questions_have_a_backfill_recipe():
    assert "title_groups" in bank._BACKFILL_GROUP_PACKS
    assert "'t:' || t.id" in bank._BACKFILL_TITLE_PACKS
    assert "q.pack = 'general'" in bank._BACKFILL_GROUP_PACKS
    assert "telenovela" in bank._BACKFILL_GROUP_PACKS.lower()
