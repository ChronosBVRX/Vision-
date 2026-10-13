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


def test_magis_row_matches_sql_arity():
    # Magis quedó desalineado (18 vs 20) y tumbaba su conector cada ciclo.
    import magis
    row = magis.title_row({"contentId": "X1", "name": "T", "tags": "Drama 2024"}, "movies")
    assert row is not None
    assert len(row) == _placeholders()
    assert row[0] == "magis:X1"
    assert row[14] == "magisvod:X1"


def test_ia_row_matches_sql_arity():
    import archive_ia
    row = archive_ia.title_row({"identifier": "id1", "title": "T (1920)", "year": 1920})
    assert row is not None
    assert len(row) == _placeholders()
    assert row[0] == "ia-id1"
    assert row[4] == "T"
