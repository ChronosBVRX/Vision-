"""La tupla de títulos debe tener tantas columnas como TITLE_SQL (el 17-vs-18
tumbó el índice de canales en vivo un ciclo entero)."""

import xtv


def _placeholders():
    return xtv.TITLE_SQL.count("$")


def _asset(**over):
    base = {"contentId": "ABC123", "name": "Show", "alias": "",
            "releaseTime": "2024-01-01", "description": "d",
            "posterList": [], "duration": "00:45:00", "score": "7.5",
            "tags": "Drama", "actorDisplay": "", "director": "",
            "programType": "series"}
    base.update(over)
    return base


def test_title_row_matches_sql_arity():
    assert len(xtv.title_row(_asset(), "series", False)) == _placeholders()


def test_live_row_matches_sql_arity():
    row = xtv.live_title_row({"channelCode": "C1", "name": "Canal",
                              "posterList": []})
    assert len(row) == _placeholders()
    assert row[0] == "xtv-live-C1"
    assert row[14] == "xtvlive:C1"


def test_series_kind_rewrite():
    row = xtv.title_row(_asset(programType="teleplay"), "movie", False)
    assert row[3] == "series"


def test_client_sn_defaults_to_device():
    assert xtv.XtvClient("t", "u").sn == xtv.SN


def test_client_sn_override_for_server_identity():
    assert xtv.XtvClient("t", "u", sn="abc123").sn == "abc123"
