"""Pruebas de los marcadores de salto (`/v1/markers`): referencias, parseo, saneo y fusión.

Las bases globales comunitarias (TheIntroDB / IntroDB.app) traen entradas rotas de vez
en cuando —se vieron «intros» a las 19:51 en capítulos de 24 min—: el saneo las tira
antes de guardarlas. La fusión prefiere la fuente más votada y, en empate, IntroDB.app
(la de mejor cobertura medida contra el catálogo real).
"""

import sys
from pathlib import Path

# Añadir server/api al sys.path para poder importar markers
sys.path.insert(0, str(Path(__file__).parent.parent / "api"))

import markers


# ---------------------------------------------------------------- referencias

def test_normalize_ref_prefixes_xtv_and_keeps_known_portals():
    assert markers.normalize_ref("C8EF2ACC") == "xtv:C8EF2ACC"
    assert markers.normalize_ref(" xtv:C8EF ") == "xtv:C8EF"
    assert markers.normalize_ref("magis:ABC") == "magis:ABC"
    assert markers.normalize_ref("") == ""


def test_ref_variants_cover_the_same_card_in_both_portals():
    assert markers.ref_variants("xtv:ABC") == ["xtv:ABC", "magis:ABC"]
    assert markers.ref_variants("magis:ABC") == ["magis:ABC", "xtv:ABC"]


def test_season_from_title_reads_the_real_season_marker():
    assert markers.season_from_title("Navy: Investigación criminal T7") == 7
    assert markers.season_from_title("Los Simpson - Español Latino T3") == 3
    assert markers.season_from_title("Bleach: Thousand-Year Blood War T1") == 1
    assert markers.season_from_title("Serie Temporada 12") == 12
    assert markers.season_from_title("Los Simpson 32") == 32
    # Sin marca (o fuera de rango): no se inventa temporada.
    assert markers.season_from_title("Pokemon XY") == 0
    assert markers.season_from_title("Sakura Card Captors") == 0
    assert markers.season_from_title("Titulo T99") == 0


def test_ms_or_none_bounds():
    assert markers.ms_or_none("90000") == 90000
    assert markers.ms_or_none(0) == 0
    assert markers.ms_or_none(-5) is None
    assert markers.ms_or_none(7 * 3600_000) is None
    assert markers.ms_or_none("x") is None


# ---------------------------------------------------------------- parseo

def test_parse_tidb_picks_the_most_voted_segment():
    payload = {
        "intro": [
            {"start_ms": 90000, "end_ms": 150000, "submission_count": 1, "confidence": 1.0},
            {"start_ms": 92000, "end_ms": 151000, "submission_count": 4, "confidence": 0.9},
        ],
        "credits": [{"start_ms": 1434000, "end_ms": 1524000, "submission_count": 3}],
        "recap": [],
    }
    out = markers.parse_tidb(payload)
    assert out["intro"]["s"] == 92000 and out["intro"]["votes"] == 4
    assert out["credits"]["s"] == 1434000 and out["credits"]["src"] == "theintrodb"
    assert "recap" not in out


def test_parse_idb_maps_outro_to_credits():
    payload = {
        "intro": {"start_ms": 44200, "end_ms": 82000, "submission_count": 2, "confidence": 1.0},
        "outro": {"start_ms": 2527000, "end_ms": None, "submission_count": 1},
        "recap": None,
        "post_credits": {"start_ms": 2600000},
    }
    out = markers.parse_idb(payload)
    assert out["intro"]["e"] == 82000
    assert out["credits"]["s"] == 2527000 and out["credits"]["src"] == "introdb"
    # post-credits no es un segmento de salto de la app: se ignora.
    assert "post_credits" not in out


def test_parse_tolerates_garbage():
    assert markers.parse_tidb(None) == {}
    assert markers.parse_idb("nope") == {}
    assert markers.parse_tidb({"intro": [{"foo": 1}]}) == {}
    assert markers.parse_idb({"intro": {"start_ms": "x"}}) == {}


# ---------------------------------------------------------------- saneo

def test_sanitize_keeps_a_normal_cold_open_intro():
    intro = {"s": 44000, "e": 82000, "src": "introdb", "votes": 2, "conf": 1.0}
    out = markers.sanitize({"intro": intro}, duration_min=43)
    assert out["intro"]["e"] == 82000


def test_sanitize_drops_the_late_intro_of_a_short_episode():
    # Caso real: «intro» de 19:51 en un capítulo de 24 min (era el ending mal etiquetado).
    bad = {"s": 1191000, "e": 1291000, "src": "introdb", "votes": 1, "conf": 1.0}
    assert markers.sanitize({"intro": bad}, duration_min=24) == {}
    # Sin duración conocida también se corta (tope duro de 12 min).
    assert markers.sanitize({"intro": bad}, duration_min=0) == {}


def test_sanitize_intro_without_start_is_kept():
    seg = {"s": None, "e": 24000, "src": "theintrodb", "votes": 1, "conf": 1.0}
    assert markers.sanitize({"intro": seg}, duration_min=34)["intro"]["e"] == 24000


def test_sanitize_drops_invalid_intro_ranges():
    assert markers.sanitize({"intro": {"s": 90000, "e": 90000}}) == {}
    assert markers.sanitize({"intro": {"s": 95000, "e": 90000}}) == {}
    assert markers.sanitize({"intro": {"s": 90000, "e": None}}) == {}


def test_sanitize_credits_must_start_late():
    good = {"s": 2527000, "e": None, "src": "introdb", "votes": 1, "conf": 1.0}
    early = {"s": 300000, "e": None, "src": "introdb", "votes": 1, "conf": 1.0}
    assert markers.sanitize({"credits": good}, duration_min=43)["credits"]["s"] == 2527000
    assert markers.sanitize({"credits": early}, duration_min=43) == {}
    # Sin duración: aplica el piso absoluto de 5 min (5:00 exacto pasa; 4:00 no).
    assert markers.sanitize({"credits": early}, duration_min=0)["credits"]["s"] == 300000
    just_below = {"s": 240000, "e": None, "src": "introdb", "votes": 1, "conf": 1.0}
    assert markers.sanitize({"credits": just_below}, duration_min=0) == {}


def test_sanitize_recap_never_exceeds_five_minutes():
    ok = {"s": 30000, "e": 135000, "src": "introdb", "votes": 1, "conf": 1.0}
    long = {"s": 30000, "e": 600000, "src": "introdb", "votes": 1, "conf": 1.0}
    assert "recap" in markers.sanitize({"recap": ok})
    assert markers.sanitize({"recap": long}) == {}


# ---------------------------------------------------------------- fusión

def test_merge_prefers_the_most_voted_and_idb_on_tie():
    tidb = {"intro": {"s": 1000, "e": 2000, "src": "theintrodb", "votes": 1}}
    idb = {"intro": {"s": 1100, "e": 2100, "src": "introdb", "votes": 3}}
    assert markers.merge(tidb, idb)["intro"]["src"] == "introdb"

    tidb2 = {"intro": {"s": 1000, "e": 2000, "src": "theintrodb", "votes": 2}}
    idb2 = {"intro": {"s": 1100, "e": 2100, "src": "introdb", "votes": 2}}
    assert markers.merge(tidb2, idb2)["intro"]["src"] == "introdb"

    tidb3 = {"intro": {"s": 1000, "e": 2000, "src": "theintrodb", "votes": 5}}
    assert markers.merge(tidb3, idb2)["intro"]["src"] == "theintrodb"


def test_merge_survives_single_sources():
    credits = {"s": 2527000, "e": None, "src": "theintrodb", "votes": 1}
    out = markers.merge({"credits": credits}, {})
    assert out["credits"]["s"] == 2527000
    out2 = markers.merge({}, {"credits": credits})
    assert out2["credits"]["s"] == 2527000
    assert markers.merge({}, {}) == {}


def test_overlay_user_marks_win_and_can_delete():
    base = {
        "intro": {"s": 1000, "e": 2000, "src": "theintrodb", "votes": 9},
        "credits": {"s": 3000000, "e": None, "src": "introdb", "votes": 1},
    }
    user = {"intro": {"s": 1500, "e": 2500, "src": "user", "votes": 1}, "credits": None}
    out = markers.overlay(base, user)
    assert out["intro"]["src"] == "user" and out["intro"]["s"] == 1500
    assert "credits" not in out


def test_segments_of_decodes_postgres_jsonb_text():
    # asyncpg entrega el jsonb como texto: la ruta de caché DEBE decodificarlo.
    stored = '{"intro": {"s": 90000, "e": 150000, "src": "introdb"}}'
    assert markers.segments_of(stored)["intro"]["e"] == 150000
    assert markers.segments_of({"credits": {"s": 1}}) == {"credits": {"s": 1}}
    assert markers.segments_of(None) == {}
    assert markers.segments_of("") == {}
    assert markers.segments_of("nope") == {}
    assert markers.segments_of('["lista"]') == {}


def test_payload_shape_matches_the_app_contract():
    segments = {"intro": {"s": 90000, "e": 150000, "src": "introdb", "votes": 1},
                "credits": {"s": 2527000, "e": None, "src": "introdb", "votes": 1}}
    out = markers.payload("xtv:ABC", 3, 2, segments)
    assert out["found"] is True
    assert out["intro"] == {"s": 90000, "e": 150000, "src": "introdb"}
    assert out["credits"] == {"s": 2527000, "e": None, "src": "introdb"}
    assert "recap" not in out
    assert markers.payload("xtv:ABC", 3, 2, {})["found"] is False


# ---------------------------------------------------------------- red (con fake)

def test_fetch_global_queries_both_sources_and_merges(monkeypatch):
    calls = []

    def fake_get(url, timeout):
        calls.append(url)
        if "theintrodb" in url:
            return {"intro": [{"start_ms": 90000, "end_ms": 150000, "submission_count": 1, "confidence": 1.0}]}
        return {
            "intro": {"start_ms": 92000, "end_ms": 151000, "submission_count": 5},
            "outro": {"start_ms": 1434000, "end_ms": None, "submission_count": 3},
        }

    monkeypatch.setattr(markers, "_get_json", fake_get)
    out = markers.fetch_global(2560140, "tt2560140", season=1, episode=1)
    assert len(calls) == 2
    assert "tmdb_id=2560140" in calls[0] and "season=1" in calls[0] and "episode=1" in calls[0]
    assert "imdb_id=tt2560140" in calls[1]
    assert out["intro"]["src"] == "introdb"          # 5 votos le gana a 1
    assert out["credits"]["s"] == 1434000


def test_fetch_global_survives_a_source_being_down(monkeypatch):
    def fake_get(url, timeout):
        if "introdb.app" in url:
            raise RuntimeError("caída")
        return {"intro": [{"start_ms": 90000, "end_ms": 150000, "submission_count": 2}]}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    out = markers.fetch_global(2560140, "tt2560140", season=2, episode=4)
    assert out["intro"]["src"] == "theintrodb"
    assert "credits" not in out


def test_fetch_global_without_ids_never_calls_network(monkeypatch):
    def fake_get(url, timeout):
        raise AssertionError("no debe tocar la red")

    monkeypatch.setattr(markers, "_get_json", fake_get)
    assert markers.fetch_global(None, "", season=1, episode=1) == {}
