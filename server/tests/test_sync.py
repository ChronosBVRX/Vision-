"""Continuidad entre equipos: código hasheado, saneo de elementos y LWW del SQL."""

import json
from datetime import datetime, timezone

import pytest
from fastapi import HTTPException

import app
import sync as device_sync


def test_normalize_code_accepts_dashes_and_spaces():
    assert device_sync.normalize_code(" abcd-efgh ") == "ABCDEFGH"
    assert device_sync.normalize_code("ABCD EFGH") == "ABCDEFGH"
    assert device_sync.normalize_code("") == ""
    assert device_sync.normalize_code(None) == ""
    assert device_sync.normalize_code("corto") == ""
    assert device_sync.normalize_code("con!simbolo") == ""


def test_code_is_never_stored_in_clear():
    code = "ABCDEFGH"
    hashed = device_sync.code_id(code)
    assert hashed and hashed != code
    assert len(hashed) == 64
    assert device_sync.code_id("abcd-efgh") == hashed
    assert device_sync.code_id("OTROCOD1") != hashed


def test_clean_at_clamps_the_future_and_the_past():
    now = 1_700_000_000_000
    assert device_sync.clean_at(0, now) == now
    assert device_sync.clean_at("no-numero", now) == now
    assert device_sync.clean_at(now + 10 * 24 * 60 * 60 * 1000, now) == now + 60 * 60 * 1000
    assert device_sync.clean_at(now - 365 * 24 * 60 * 60 * 1000, now) == now - 180 * 24 * 60 * 60 * 1000


def test_clean_item_drops_unknown_kinds_and_empty_keys():
    now = 1_700_000_000_000
    assert device_sync.clean_item({"k": "raro", "key": "x"}, now) is None
    assert device_sync.clean_item({"k": "resume", "key": ""}, now) is None
    assert device_sync.clean_item("no-dict", now) is None
    item = device_sync.clean_item({"k": "RESUME", "key": "  xtv:A  ", "at": now, "data": {"a": 1}}, now)
    assert item == {"k": "resume", "key": "xtv:A", "at": now, "d": False, "data": {"a": 1}}


def test_clean_item_rejects_a_giant_payload():
    now = 1_700_000_000_000
    huge = {"s": "x" * (device_sync.MAX_DATA + 1)}
    assert device_sync.clean_item({"k": "resume", "key": "x", "at": now, "data": huge}, now) is None


def test_clean_items_caps_the_list_and_skips_bad_rows():
    now = 1_700_000_000_000
    raw = [{"k": "watched", "key": f"serie|{i}", "at": now} for i in range(device_sync.MAX_ITEMS + 20)]
    raw.insert(0, {"k": "malo", "key": "x"})
    out = device_sync.clean_items(raw, now)
    assert len(out) == device_sync.MAX_ITEMS
    assert out[0]["k"] == "watched"


def test_row_item_decodes_jsonb_and_honours_tombstones():
    at = datetime.fromtimestamp(1_700_000_000, tz=timezone.utc)
    row = {
        "kind": "fav", "item_key": "xtv:A", "payload": json.dumps({"id": "xtv:A"}),
        "deleted": False, "client_at": at, "server_seq": 7,
    }
    item = device_sync.row_item(row)
    assert item["k"] == "fav" and item["key"] == "xtv:A"
    assert item["data"] == {"id": "xtv:A"} and item["d"] is False
    assert item["at"] == 1_700_000_000_000

    row["deleted"] = True
    assert device_sync.row_item(row)["data"] is None
    assert device_sync.row_item(row)["d"] is True


def test_api_sync_is_last_write_wins_and_incremental():
    source = open(app.__file__, encoding="utf-8").read()
    assert "x_sync_code" in source
    assert "case when excluded.client_at > sync_items.client_at" in source
    assert "client_at = greatest(sync_items.client_at, excluded.client_at)" in source
    assert "server_seq = nextval('sync_items_seq')" in source
    assert "server_seq > $2" in source
    assert "offset 500" in source


def test_sync_table_is_capped_per_code():
    source = open(app.__file__, encoding="utf-8").read()
    assert "delete from sync_items" in source
    assert "offset 500" in source


def test_sync_id_rejects_a_bad_code():
    with pytest.raises(HTTPException) as error:
        app._sync_id_or_401("")
    assert error.value.status_code == 401
    assert app._sync_id_or_401("ABCD-EFGH") == device_sync.code_id("ABCDEFGH")
