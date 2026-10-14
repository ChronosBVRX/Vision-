"""Antirregresiones del diagnóstico de campo (Fase 1): límites, rate limit y alertas."""

import app
import diag


def test_clean_diag_text():
    assert app._clean_diag_text("  hola\x00 mundo ", 10) == "hola mundo"
    assert app._clean_diag_text(None, 10) == ""
    assert len(app._clean_diag_text("x" * 100, 10)) == 10


def test_diag_rate_ok_sliding_window():
    bucket = {}
    now = 1000.0
    for _ in range(3):
        assert app._diag_rate_ok(bucket, "1.2.3.4", now, limit=3, window=60)
    assert not app._diag_rate_ok(bucket, "1.2.3.4", now + 1, limit=3, window=60)
    assert app._diag_rate_ok(bucket, "1.2.3.4", now + 61, limit=3, window=60)
    assert app._diag_rate_ok(bucket, "5.6.7.8", now, limit=3, window=60)


def test_alert_text_covers_kinds():
    row = {"kind": "crash", "code": "NPE_PLAYER", "fingerprint": "abc123",
           "latest_version": "2.5.63", "devices": 2, "hits": 7}
    text = diag.alert_text(row)
    assert "Crash" in text and "NPE_PLAYER" in text and "abc123" in text and "2.5.63" in text
    row["kind"] = "anr"
    assert "ANR" in diag.alert_text(row)


def test_diag_endpoints_registered():
    paths = {r.path for r in app.app.routes}
    assert "/v1/diag" in paths
    assert "/v1/diag/summary" in paths
