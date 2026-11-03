import json
from pathlib import Path

"""Mapa propio de canales deportivos XTV y cruce con la agenda ESPN."""

import pytest

import app as api_app
import sports_map

MAP_PATH = Path(__file__).resolve().parent.parent / "api" / "sports_channels.json"


def test_broadcast_resolution_prefers_the_most_specific_name():
    assert sports_map.resolve_broadcast("ESPN 2")[0] == "ESPN 2"
    assert sports_map.resolve_broadcast("ESPN")[0] == "ESPN"
    assert sports_map.resolve_broadcast("Fox Sports Premium")[0] == "Fox Sports"
    assert sports_map.resolve_broadcast("Paramount+")[2] == "ott"
    assert sports_map.resolve_broadcast("Disney+ Premium Mexico")[2] == "ott"
    assert sports_map.resolve_broadcast("Prime Video")[2] == "ott"


def test_tudn_offers_the_us_sibling():
    channels, _ = sports_map.channels_for("TUDN")
    names = [c["name"] for c in channels]
    countries = {c["country"] for c in channels}
    assert any("TUDN MX" in name for name in names)
    assert any("TUDN USA" in name for name in names)
    assert "México" in countries and "EE. UU." in countries


def test_fox_sports_keeps_its_feeds_and_countries():
    channels, _ = sports_map.channels_for("Fox Sports")
    names = [c["name"] for c in channels]
    assert any(name.startswith("Fox Sports 1 MX") for name in names)
    assert any(name.startswith("FOX SPORTS 1 USA") for name in names)
    assert any(c["country"] == "México" for c in channels)
    # Fox Deportes es OTRA marca (Disney/US), no debe colarse en Fox Sports MX.
    assert not any(name.startswith("Fox Deportes") for name in names)


def test_fox_deportes_is_us():
    channels, resolved = sports_map.channels_for("Fox Deportes")
    assert resolved[3] == "EE. UU."
    assert channels and all(c["country"] == "EE. UU." for c in channels)


def test_general_broadcasters_are_included():
    channels, _ = sports_map.channels_for("Azteca 7")
    assert channels
    assert channels[0]["general"] is True
    assert channels[0]["country"] == "México"


def test_ott_without_an_xtv_channel_is_honest():
    channels, resolved = sports_map.channels_for("Prime Video")
    assert resolved[2] == "ott"
    assert channels == []


def test_vix_and_disney_map_to_their_xtv_siblings():
    vix, _ = sports_map.channels_for("ViX")
    assert any(c["name"] == "ViX Deportes" for c in vix)
    disney, resolved = sports_map.channels_for("Disney+")
    assert resolved[2] == "ott"
    # Elite Sports Hub se comprobó en la tele: es una pantalla de Disney+,
    # no un ESPN en vivo. El mapa no lo ofrece como canal del partido.
    assert disney == []


def test_options_for_an_espn_match():
    match = {
        "id": "m1",
        "league": {"name": "Liga MX"},
        "home": {"name": "América"},
        "away": {"name": "Guadalajara"},
        "kickoff": "2026-11-01T23:00:00+00:00",
        "channels": ["TUDN", "Prime Video"],
        "broadcasts": ["TUDN USA"],
    }
    out = sports_map.options_for_event(match)
    names = [c["name"] for c in out["available"]]
    assert any("TUDN" in name for name in names)
    assert out["noXtv"] is False
    prime = next(item for item in out["broadcasts"] if item["name"] == "Prime Video")
    assert prime["kind"] == "ott" and prime["xtv"] == []


def test_map_payload_has_the_full_parrilla():
    payload = sports_map.map_payload()
    assert payload["sportsChannels"] >= 180
    assert payload["total"] == payload["sportsChannels"]
    names = {c["name"] for c in payload["channels"]}
    assert "Fox Sports Central HD" in names
    assert "Elite Sports Hub HD" not in names
    assert "TUDN MX HD" in names


@pytest.mark.anyio
async def test_sports_channels_endpoint_serves_the_map():
    out = await api_app.sports_channels(
        country="México", brand=None, q=None, general=1, limit=0, x_api_key="test-key"
    )
    assert out["total"] > 0
    assert out["byCountry"]
    assert all(c["country"] == "México" for c in out["channels"])


def test_elite_sports_hub_is_not_a_live_espn_feed():
    """Prueba en Chromecast 2026-11-02: Elite Sports Hub muestra Disney+."""
    channels, _ = sports_map.channels_for("ESPN")
    assert not any("Elite Sports Hub" in c["name"] for c in channels)
    channels2, _ = sports_map.channels_for("ESPN 2")
    assert not any("Elite Sports Hub" in c["name"] for c in channels2)
    payload = sports_map.map_payload(q="Elite Sports Hub")
    assert payload["total"] == 0


def test_fox_one_is_verified_and_aym_is_not_an_event_feed():
    """Prueba de campo 2026-11-01: FOX ONE = Xolos vs Atlas; AYM = Cuadro Titular."""
    payload = sports_map.map_payload(q="FOX ONE")
    fox = next(c for c in payload["channels"] if c["name"] == "FOX ONE")
    assert fox["owner"] == "Fox Corporation"
    assert fox["country"] == "México"
    assert fox["verified"] is True
    assert "Fox One" in fox["rights"]

    aym_payload = sports_map.map_payload(q="AYM Sports")
    aym = next(c for c in aym_payload["channels"] if c["name"] == "AYM SPORTS MEXICO")
    assert aym["notEventFeed"] is True
    assert "Cuadro Titular" in aym["note"]

    premium = next(
        c for c in sports_map.map_payload(q="Fox Sports Premium")["channels"]
        if c["name"] == "Fox Sports Premium MX HD"
    )
    assert premium["owner"] == "Fox Sports (Lauman)"
    assert premium["notFoxPlus"] is True
    assert premium["legacy"] is True


def test_sony_central_does_not_become_espn():
    assert sports_map.resolve_broadcast("Sony Central") is None
    channels, _ = sports_map.channels_for("Sony Central")
    assert channels == []


def test_fox_linear_channels_map_to_the_verified_xtv_mirrors():
    """FOX basic → AYM; FOX+ → FOX ONE; FOX | Tubi sin espejo confirmado."""
    fox, _ = sports_map.channels_for("FOX")
    assert [c["name"] for c in fox] == ["AYM SPORTS MEXICO"]
    assert fox[0]["mirrorOf"] == "FOX"
    assert "Cuadro Titular" in fox[0]["note"]

    plus, _ = sports_map.channels_for("FOX+")
    assert [c["name"] for c in plus] == ["FOX ONE"]
    assert plus[0]["mirrorOf"] == "FOX+"
    assert plus[0]["verified"] is True

    tubi, _ = sports_map.channels_for("FOX | Tubi")
    assert tubi == []

    one, _ = sports_map.channels_for("FOX One")
    assert [c["name"] for c in one] == ["FOX ONE"]
    assert one[0]["mirrorOf"] == "FOX One"


def test_a_future_fox_event_gets_the_right_mirror():
    basic = sports_map.options_for_event({"channels": ["FOX"], "home": {}, "away": {}})
    assert [c["name"] for c in basic["available"]] == ["AYM SPORTS MEXICO"]
    premium = sports_map.options_for_event({"channels": ["FOX+"], "home": {}, "away": {}})
    assert [c["name"] for c in premium["available"]] == ["FOX ONE"]
    tubi = sports_map.options_for_event({"channels": ["FOX | Tubi"], "home": {}, "away": {}})
    assert tubi["available"] == []
    assert tubi["broadcasts"][0]["note"].startswith("Señal gratuita de Tubi")


def test_gee_event_fox_corp_gets_fox_one_and_never_lauman():
    event = {
        "team_a": "Necaxa", "team_b": "FC Juárez",
        "home_name": "Necaxa", "away_name": "FC Juárez",
        "event": "Liga MX, Apertura", "league_id": "mex.1",
        "link_names": ["Fox Sports Premium MX HD", "TUDN US HD"],
        "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    names = [item["name"] for item in out]
    assert "FOX One" in names or "FOX ONE" in names
    assert not any("Fox Sports Premium" in name for name in names)
    # Sin EPG, la regla Fox no marca una recomendación: evita el mal cruce.
    assert not any(item["recommended"] for item in out)


def test_gee_event_fox_basic_goes_to_aym():
    event = {
        "team_a": "Pachuca", "team_b": "Tigres UANL",
        "home_name": "Pachuca", "away_name": "Tigres UANL",
        "event": "Liga MX, Apertura", "league_id": "mex.1",
        "link_names": ["FOX"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    assert any("AYM" in item["name"] for item in out)
    aym = next(item for item in out if "AYM" in item["name"])
    assert aym["recommended"] is False


def test_gee_event_fight_keeps_fox_sports_premium():
    event = {
        "team_a": "UFC", "team_b": "Main Event",
        "home_name": "UFC", "away_name": "Main Event",
        "event": "UFC", "league_id": "ufc",
        "link_names": ["Fox Sports Premium MX HD"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    names = [item["name"] for item in out]
    assert any("Fox Sports Premium" in name for name in names)
    assert not any("FOX One" in name for name in names)


def test_fox_basic_does_not_match_fox_cricket_or_news():
    for name in ("FOX CRICKET HD", "FOX NEWS", "FOX SOCCER PLUS", "FOX SPORTS 1"):
        channels, _res = sports_map.channels_for(name)
        assert not any(ch.get("brand") == "AYM Sports" for ch in channels), name


def test_fox_one_is_not_invented_for_argentina_or_brazil():
    for league in ("arg.1", "bra.1", "por.1", "col.1", "uefa.champions"):
        event = {
            "team_a": "River Plate", "team_b": "Boca Juniors",
            "home_name": "River Plate", "away_name": "Boca Juniors",
            "event": "Liga", "league_id": league,
            "link_names": ["ESPN", "TNT Sports"], "links": [],
        }
        out = sports_map.options_for_gee_event(event)
        assert not any(item["name"] == "FOX One" for item in out), (league, out)


def test_shared_fox_team_is_not_forced_as_recommended():
    event = {
        "team_a": "Tigres UANL", "team_b": "América",
        "home_name": "Tigres UANL", "away_name": "América",
        "event": "Liga MX", "league_id": "mex.1",
        "link_names": ["TUDN"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    fox = next((item for item in out if item["name"] == "FOX One"), None)
    assert fox is not None
    assert fox["recommended"] is False


def test_fox_basic_epg_overrides_gee_fox_one_link_for_pachuca_tigres():
    event = {
        "team_a": "Pachuca", "team_b": "Tigres UANL",
        "home_name": "Pachuca", "away_name": "Tigres UANL",
        "event": "Liga MX, Apertura", "league_id": "mex.1",
        "date_iso": "2026-11-01", "time": "19:00:00",
        "link_names": ["FOX ONE"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    names = [item["name"] for item in out]
    assert any("AYM" in name for name in names), out
    assert not any(item["name"] == "FOX One" for item in out), out
    aym = next(item for item in out if "AYM" in item["name"])
    assert aym["recommended"] is True


def test_verified_xolos_atlas_field_test_is_fox_one():
    event = {
        "team_a": "Tijuana", "team_b": "Atlas",
        "home_name": "Tijuana", "away_name": "Atlas",
        "event": "Liga MX", "league_id": "mex.1",
        "date_iso": "2026-11-01", "time": "19:00:00",
        "link_names": ["FOX ONE"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    fox = next((item for item in out if item["name"] == "FOX One"), None)
    assert fox is not None and fox["recommended"] is True
    assert not any("AYM" in item["name"] for item in out)


def test_nfl_on_tsn_without_fox_epg_does_not_invent_aym():
    event = {
        "team_a": "Seattle Seahawks", "team_b": "Chicago Bears",
        "home_name": "Seattle Seahawks", "away_name": "Chicago Bears",
        "event": "NFL", "league_id": "nfl",
        "date_iso": "2026-11-02", "time": "19:15:00",
        "link_names": ["TSN1 SPORTS FHD", "TSN3 SPORTS FHD"], "links": [],
    }
    out = sports_map.options_for_gee_event(event)
    assert not any("AYM" in item["name"] for item in out)
    assert not any(item["name"] == "FOX One" for item in out)


def test_map_excludes_tnt_entertainment_and_labels_chile():
    data = json.loads((MAP_PATH).read_text(encoding="utf-8"))
    names = {ch.get("name"): ch for ch in data.get("channels", [])}
    assert "TNT SPORTS HD" not in names
    assert not any(ch.get("code", "").endswith("cyx_TNTSports3") for ch in data["channels"])
    for name in ("TNT SPORTS FHD", "TNT SPORTS HD+", "TNT SPORTS PREMIUM HD", "ESTADIO TNT SPORTS"):
        assert names[name]["country"] == "Chile", name


def test_map_excludes_disney_plus_elite_placeholder():
    data = json.loads(MAP_PATH.read_text(encoding="utf-8"))
    assert not any("elite sports hub" in ch.get("name", "").lower() for ch in data["channels"])
    assert not any(str(ch.get("code", "")).upper().startswith("XTXLIVE:ST☆R+") for ch in data["channels"])
