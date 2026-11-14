"""Tests unitarios del conector de señales alternativas Wiwy/JrFlix."""

import wiwy_streams


def test_match_alt_signals_with_mocked_channels(monkeypatch) -> None:
    fake_channels = {
        "wx-jrflix-espn-hd-chile": "http://live.btv.mx:9000/hls/espn.m3u8",
        "wx-jrflix-sky-sport-la-liga": "http://live.btv.mx:9000/hls/sky.m3u8",
        "wx-jrflix-fox-centroamerica-cr": "http://live.btv.mx:9000/hls/fox.m3u8",
    }
    monkeypatch.setattr(wiwy_streams, "get_jrflix_channels", lambda force=False: fake_channels)

    # 1. Match con ESPN y Star+
    signals = wiwy_streams.match_alt_signals(["ESPN", "Star+"])
    assert len(signals) == 1
    assert signals[0]["name"] == "ESPN HD (Señal Alternativa HD)"
    assert signals[0]["url"] == "http://live.btv.mx:9000/hls/espn.m3u8"
    assert signals[0]["stream_type"] == "hls"

    # 2. Match con LaLiga / Sky
    signals_sky = wiwy_streams.match_alt_signals(["Sky Sports", "LaLiga TV"])
    assert len(signals_sky) == 1
    assert signals_sky[0]["name"] == "Sky Sports LaLiga (Señal Alternativa HD)"
    assert signals_sky[0]["url"] == "http://live.btv.mx:9000/hls/sky.m3u8"

    # 3. Sin match (ej. Canal 5)
    signals_none = wiwy_streams.match_alt_signals(["Canal 5", "Azteca 7"])
    assert len(signals_none) == 0


def test_fold_normalization() -> None:
    assert wiwy_streams._fold("ESPN 2 HD") == "espn 2 hd"
    assert wiwy_streams._fold("Sky Sports LaLiga!") == "sky sports laliga"
    assert wiwy_streams._fold("") == ""
