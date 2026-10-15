"""Pool de slots: cada intento firma su decisión; el medidor se lee como medidor."""

import slots as device_slots


def test_normalize_device_key():
    assert device_slots.normalize_device_key("  AbC-123_xy! ") == "abc-123xy"
    assert device_slots.normalize_device_key("") == ""
    assert device_slots.normalize_device_key(None) == ""
    assert len(device_slots.normalize_device_key("x" * 200)) <= 64


def test_normalize_code():
    assert device_slots.normalize_code(" ab12 cd34 ") == "AB12CD34"
    assert device_slots.normalize_code("corto") == ""
    assert device_slots.normalize_code("con-guion1") == ""
    assert device_slots.normalize_code(None) == ""


def test_normalize_sn():
    assert device_slots.normalize_sn(" 2392fe ") == "2392fe"
    assert device_slots.normalize_sn("") == ""
    assert device_slots.normalize_sn(None) == ""


def test_generate_code_shape():
    seen = {device_slots.generate_code() for _ in range(50)}
    assert len(seen) > 40  # esencialmente únicos
    for code in seen:
        assert len(code) == 8
        assert all(ch in device_slots.CODE_ALPHABET for ch in code)


def test_redeem_free_slot_ok():
    slot = {"sn": "SN1", "user_id": "U1", "status": "free", "device_key": None}
    out = device_slots.redeem_decision(slot, "box-1")
    assert out == {"ok": True, "sn": "SN1", "user_id": "U1"}


def test_redeem_same_device_idempotent():
    slot = {"sn": "SN1", "user_id": "", "status": "assigned", "device_key": "box-1"}
    out = device_slots.redeem_decision(slot, "box-1")
    assert out["ok"] is True and out["sn"] == "SN1"


def test_redeem_taken_by_other_device():
    slot = {"sn": "SN1", "user_id": "", "status": "assigned", "device_key": "box-9"}
    out = device_slots.redeem_decision(slot, "box-1")
    assert out == {"ok": False, "error": "taken"}


def test_redeem_invalid_and_blocked_and_no_device():
    assert device_slots.redeem_decision(None, "box-1") == {"ok": False, "error": "invalid"}
    blocked = {"sn": "SN1", "user_id": "", "status": "blocked", "device_key": None}
    assert device_slots.redeem_decision(blocked, "box-1") == {"ok": False, "error": "blocked"}
    free = {"sn": "SN1", "user_id": "", "status": "free", "device_key": None}
    assert device_slots.redeem_decision(free, "") == {"ok": False, "error": "device"}


def test_claim_returns_assignment_or_none():
    row = {"sn": "SN7", "user_id": "U7"}
    assert device_slots.claim_decision(row) == {"sn": "SN7", "user_id": "U7"}
    assert device_slots.claim_decision(None) is None


def test_auto_returns_existing_without_consuming():
    existing = {"sn": "SN1", "user_id": "U1"}
    free = {"sn": "SN2", "user_id": "U2"}
    out = device_slots.auto_decision(existing, free)
    assert out == {"ok": True, "fresh": False, "sn": "SN1", "user_id": "U1"}


def test_auto_assigns_oldest_free_when_new():
    out = device_slots.auto_decision(None, {"sn": "SN2", "user_id": ""})
    assert out == {"ok": True, "fresh": True, "sn": "SN2", "user_id": ""}


def test_auto_exhausted_when_pool_empty():
    assert device_slots.auto_decision(None, None) == {"ok": False, "error": "exhausted"}


def test_pool_summary_counts():
    rows = [{"status": "free"}, {"status": "assigned"}, {"status": "assigned"},
            {"status": "worker"}, {"status": "blocked"}, {"status": "raro"}]
    assert device_slots.pool_summary(rows) == {
        "free": 1, "assigned": 2, "worker": 1, "blocked": 1,
    }


def test_worker_sn_prefers_worker_slot():
    rows = [{"status": "assigned", "sn": "A"}, {"status": "worker", "sn": "W"}]
    assert device_slots.worker_sn(rows, env_sn="E", legacy_sn="L") == "W"
    assert device_slots.worker_sn([], env_sn="E", legacy_sn="L") == "E"
    assert device_slots.worker_sn([], legacy_sn="L") == "L"
