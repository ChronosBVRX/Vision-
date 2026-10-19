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
        "post-credits": {"start_ms": 2600000, "end_ms": 2655000, "submission_count": 1},
    }
    out = markers.parse_idb(payload)
    assert out["intro"]["e"] == 82000
    assert out["credits"]["s"] == 2527000 and out["credits"]["src"] == "introdb"
    assert out["postCredits"]["s"] == 2600000 and out["postCredits"]["e"] == 2655000


def test_parse_tidb_reads_post_credits_list():
    payload = {
        "intro": [],
        "post_credits": [
            {"start_ms": 2600000, "end_ms": 2650000, "submission_count": 1},
            {"start_ms": 2610000, "end_ms": 2660000, "submission_count": 3},
        ],
    }
    out = markers.parse_tidb(payload)
    assert out["postCredits"]["s"] == 2610000 and out["postCredits"]["votes"] == 3
    assert "intro" not in out


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


def test_sanitize_post_credits_must_be_late():
    good = {"s": 2600000, "e": 2650000, "src": "introdb", "votes": 1, "conf": 1.0}
    early = {"s": 60000, "e": 120000, "src": "introdb", "votes": 1, "conf": 1.0}
    assert markers.sanitize({"postCredits": good}, duration_min=45)["postCredits"]["s"] == 2600000
    assert markers.sanitize({"postCredits": early}, duration_min=45) == {}
    assert markers.sanitize({"postCredits": {"s": 60000, "e": 60000}}, duration_min=0) == {}


def test_merge_includes_post_credits():
    a = {"postCredits": {"s": 1, "e": 2, "src": "theintrodb", "votes": 1}}
    b = {"postCredits": {"s": 3, "e": 4, "src": "introdb", "votes": 1}}
    assert markers.merge(a, b)["postCredits"]["src"] == "introdb"
    assert markers.merge(a, {})["postCredits"]["s"] == 1


def test_payload_carries_post_credits():
    segments = {"postCredits": {"s": 2600000, "e": None, "src": "introdb", "votes": 1}}
    out = markers.payload("xtv:ABC", 1, 1, segments)
    assert out["found"] is True
    assert out["postCredits"] == {"s": 2600000, "e": None, "src": "introdb"}


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
    # Las fuentes van EN PARALELO: el orden de llegada no es contrato.
    tmdb_call = next(url for url in calls if "theintrodb" in url)
    imdb_call = next(url for url in calls if "introdb.app" in url)
    assert "tmdb_id=2560140" in tmdb_call and "season=1" in tmdb_call and "episode=1" in tmdb_call
    assert "imdb_id=tt2560140" in imdb_call
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


def test_fetch_global_status_flags_a_source_failure(monkeypatch):
    def fake_get(url, timeout):
        if "aniskip" in url:
            raise RuntimeError("caída")
        return {"intro": [{"start_ms": 90000, "end_ms": 150000, "submission_count": 2}]}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    segments, all_ok = markers.fetch_global_status(2560140, "", season=1, episode=1, mal_id=290)
    assert segments["intro"]["src"] == "theintrodb"
    assert all_ok is False, "una fuente caída no puede pasar por «no hay datos»"


def test_fetch_global_status_true_negative_is_not_a_failure(monkeypatch):
    def fake_get(url, timeout):
        return {"found": False, "results": []}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    segments, all_ok = markers.fetch_global_status(None, "", season=1, episode=1, mal_id=290)
    assert segments == {}
    assert all_ok is True


def test_aniskip_falls_back_to_720_after_404(monkeypatch):
    calls = []

    def fake_get(url, timeout):
        calls.append(url)
        if "episodeLength=1440" in url:
            raise RuntimeError("HTTP 404")
        return {"found": True, "results": [
            {"skipType": "op", "interval": {"startTime": 5.0, "endTime": 65.0}},
        ]}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    out = markers.fetch_global(None, "", season=1, episode=1, mal_id=290)
    assert out["intro"]["s"] == 5000
    assert any("episodeLength=720" in url for url in calls)


# ---------------------------------------------------------------- AniSkip

def test_parse_aniskip_maps_op_ed_recap_to_ms():
    payload = {
        "found": True,
        "results": [
            {"skipType": "op", "interval": {"startTime": 128.406, "endTime": 218.406}},
            {"skipType": "ed", "interval": {"startTime": 1342.795, "endTime": 1430.616}},
            {"skipType": "recap", "interval": {"startTime": 1.0, "endTime": 64.0}},
        ],
    }
    out = markers.parse_aniskip(payload)
    assert out["intro"]["s"] == 128406 and out["intro"]["e"] == 218406
    assert out["intro"]["src"] == "aniskip"
    assert out["credits"]["s"] == 1342795 and out["credits"]["e"] == 1430616
    assert out["recap"]["e"] == 64000
    # Votos fijos: los tiempos de AniSkip ya vienen votados por su comunidad.
    assert out["intro"]["votes"] == markers.ANISKIP_VOTES


def test_parse_aniskip_tolerates_garbage():
    assert markers.parse_aniskip(None) == {}
    assert markers.parse_aniskip("nope") == {}
    assert markers.parse_aniskip({"found": False}) == {}
    assert markers.parse_aniskip({"results": [{"skipType": "xx", "interval": {}}]}) == {}
    assert markers.parse_aniskip({"results": [{"skipType": "op"}]}) == {}
    assert markers.parse_aniskip({"results": "no-lista"}) == {}


def test_parse_anime_list_maps_tmdb_season_to_mal():
    entries = [
        {"mal_id": 290, "themoviedb_id": {"tv": 26209}, "season": {"tmdb": 1}},
        {"mal_id": 300, "themoviedb_id": {"tv": 62913}, "season": {"tmdb": 1}},
        {"mal_id": 1225, "themoviedb_id": {"tv": 62913}, "season": {"tmdb": 2}},
        {"mal_id": 0, "themoviedb_id": {"tv": 1}, "season": {"tmdb": 1}},       # sin mal: fuera
        {"mal_id": 5, "themoviedb_id": {}, "season": {"tmdb": 1}},              # sin tmdb: fuera
        {"mal_id": 6, "themoviedb_id": {"tv": 9}, "season": {}},                # sin temporada: T1
        {"mal_id": 7, "themoviedb_id": {"tv": 9}, "season": {"tmdb": 1}},       # explícito gana
        "basura",
    ]
    out = markers.parse_anime_list(entries)
    assert out[(26209, 1)] == 290
    assert out[(62913, 1)] == 300
    assert out[(62913, 2)] == 1225
    assert out[(9, 1)] == 7, "un mapeo explícito no se pisa con el fallback"
    assert len(out) == 4


def test_parse_anime_list_falls_back_to_season_one_for_long_running_shows():
    # One Piece en Fribb no trae bloque `season`; antes se descartaba y AniSkip
    # nunca se usaba para la serie. El fallback la recupera.
    entries = [{"mal_id": 21, "themoviedb_id": {"tv": 37854}}]
    assert markers.parse_anime_list(entries) == {(37854, 1): 21}


def test_merge_aniskip_wins_ties_but_loses_to_heavily_voted():
    weak_tidb = {"intro": {"s": 1, "e": 2, "src": "theintrodb", "votes": 1}}
    weak_idb = {"intro": {"s": 1, "e": 2, "src": "introdb", "votes": 1}}
    askip = {"intro": {"s": 3, "e": 4, "src": "aniskip", "votes": markers.ANISKIP_VOTES}}
    # Empate/ventaja leve de las generalistas: el opening de anime manda.
    assert markers.merge(weak_tidb, weak_idb, askip)["intro"]["src"] == "aniskip"
    # Un dato muy votado de la base generalista sigue mandando (no es anime o es mejor).
    strong = {"intro": {"s": 1, "e": 2, "src": "introdb", "votes": 99}}
    assert markers.merge(weak_tidb, strong, askip)["intro"]["src"] == "introdb"


def test_merge_without_aniskip_keeps_old_behavior():
    tidb = {"intro": {"s": 1, "e": 2, "src": "theintrodb", "votes": 2}}
    idb = {"intro": {"s": 3, "e": 4, "src": "introdb", "votes": 2}}
    assert markers.merge(tidb, idb)["intro"]["src"] == "introdb"


def test_fetch_global_with_mal_queries_aniskip(monkeypatch):
    calls = []

    def fake_get(url, timeout):
        calls.append(url)
        if "aniskip" in url:
            return {
                "found": True,
                "results": [
                    {"skipType": "op", "interval": {"startTime": 10.0, "endTime": 100.0}},
                ],
            }
        return {}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    out = markers.fetch_global(None, "", season=1, episode=3, mal_id=290)
    assert len(calls) == 1
    assert "/290/3" in calls[0]
    assert "episodeLength=" in calls[0]  # la v2 lo exige: sin él responde 400
    assert out["intro"]["src"] == "aniskip"
    assert out["intro"]["e"] == 100000


def test_fetch_global_sends_episode_length_from_the_card(monkeypatch):
    calls = []

    def fake_get(url, timeout):
        calls.append(url)
        return {"found": False, "results": []}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    markers.fetch_global(None, "", season=1, episode=1, mal_id=290, episode_length=24 * 60)
    assert "episodeLength=1440" in calls[0]
    # Sin duración conocida: el estándar de anime (24 min) para que no falle.
    calls.clear()
    markers.fetch_global(None, "", season=1, episode=1, mal_id=290, episode_length=0)
    assert "episodeLength=1440" in calls[0]


def test_fetch_global_without_mal_never_calls_aniskip(monkeypatch):
    calls = []

    def fake_get(url, timeout):
        calls.append(url)
        return {}

    monkeypatch.setattr(markers, "_get_json", fake_get)
    markers.fetch_global(1396, "tt0903747", season=1, episode=1)
    assert calls and all("aniskip" not in url for url in calls)
