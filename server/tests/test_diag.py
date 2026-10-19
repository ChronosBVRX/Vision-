"""Antirregresiones del diagnóstico de campo (Fase 1): límites, rate limit y alertas."""

import json

import app
import diag


def _row(**over):
    """Fila sintética como la que entrega el triage (con payload ya decodificado)."""
    row = {
        "fingerprint": "6171ce10085be490",
        "kind": "crash",
        "code": "IllegalArgumentException",
        "latest_version": "2.5.93",
        "devices": 1,
        "hits": 1,
        "device": "POCO F7",
        "android": "Android 16 (SDK 36)",
        "last_seen": "17/10/2026 07:18",
        "payload": {
            "process": "com.chronosbvrx.visionplus",
            "screen": "Sports",
            "phase": "reproductor",
            "engine": "cargado",
            "ramMb": 7802,
            "freeStorageMb": 41000,
            "network": "wifi",
            "tv": False,
            "detail": "com.foo.Bar.baz(Bar.kt:123)\ncom.foo.Bar.qux(Bar.kt:456)",
        },
    }
    row.update(over)
    return row


def test_clean_diag_text():
    assert app._clean_diag_text("  hola\x00 mundo ", 10) == "hola mundo"
    assert app._clean_diag_text(None, 10) == ""
    assert len(app._clean_diag_text("x" * 100, 10)) == 10


# ----- reincidencias: el «ya se avisó» deja de tapar un bug vivo -----


def test_repeat_reason_when_already_resolved_returns_back():
    assert diag.repeat_alert_reason(
        resolved_at="2026-10-17 09:00:00-06",
        alert_version="2.5.93",
        latest_version="2.5.94",
        hours_since_alert=2.0,
    ) == "regreso"


def test_repeat_reason_version_escalation_returns_reincidencia():
    assert diag.repeat_alert_reason(
        resolved_at=None,
        alert_version="2.5.93",
        latest_version="2.5.94",
        hours_since_alert=2.0,
    ) == "reincidencia"


def test_repeat_reason_same_version_stays_quiet_until_the_daily_nag():
    assert diag.repeat_alert_reason(
        resolved_at=None,
        alert_version="2.5.95",
        latest_version="2.5.95",
        hours_since_alert=2.0,
    ) is None
    assert diag.repeat_alert_reason(
        resolved_at=None,
        alert_version="2.5.95",
        latest_version="2.5.95",
        hours_since_alert=25.0,
    ) == "sigue"


def test_repeat_reason_unknown_alerted_version_is_still_quiet_today():
    # Filas legacy sin la versión avisada: hoy no genera reincidencia (evita una
    # andanada de alertas al desplegar el worker).
    assert diag.repeat_alert_reason(
        resolved_at=None,
        alert_version=None,
        latest_version="2.5.95",
        hours_since_alert=1.0,
    ) is None


def test_repeat_text_keeps_both_layers_and_changes_the_headline():
    text = diag.repeat_text(_row(), "reincidencia")
    assert "Reincidencia" in text
    # La capa humana sigue intacta (explicación + qué revisar) y la técnica completa.
    assert "estado inconsistente" in text
    assert "Qué revisar" in text
    assert "huella: 6171ce10085be490" in text


def test_alert_text_crash_includes_the_system_message():
    row = _row(payload={
        "process": "p", "screen": "Sports", "phase": "reproductor", "engine": "cargado",
        "network": "wifi", "tv": False,
        "detail": "com.foo.Bar.baz(Bar.kt:123)",
        "message": "Key \"NBA Preseason|17/10/2026|20:00:00\" was already used",
    })
    text = diag.alert_text(row)
    # La causa ya no hay que adivinarla: el mensaje del sistema viaja en ambas capas.
    assert 'Key "NBA Preseason|17/10/2026|20:00:00" was already used' in text


def test_diag_rate_ok_sliding_window():
    bucket = {}
    now = 1000.0
    for _ in range(3):
        assert app._diag_rate_ok(bucket, "1.2.3.4", now, limit=3, window=60)
    assert not app._diag_rate_ok(bucket, "1.2.3.4", now + 1, limit=3, window=60)
    assert app._diag_rate_ok(bucket, "1.2.3.4", now + 61, limit=3, window=60)
    assert app._diag_rate_ok(bucket, "5.6.7.8", now, limit=3, window=60)


def test_alert_text_crash_explains_in_human_language_but_keeps_technical_detail():
    text = diag.alert_text(_row())
    # Lenguaje humano.
    assert "la app se cerró sola" in text
    assert "Qué revisar" in text
    assert "estado inconsistente" in text
    # Detalles técnicos intactos (lo que ya traía la alerta vieja) + contexto nuevo.
    assert "Crash" in text and "IllegalArgumentException" in text
    assert "v2.5.93" in text
    assert "6171ce10085be490" in text
    assert "1 dispositivo" in text and "1 vez" in text
    assert "pantalla «Sports»" in text
    assert "POCO F7" in text
    assert "07:18" in text
    assert "Bar.kt:123" in text


def test_alert_text_anr_translates_blocked_seconds_to_readable_time():
    text = diag.alert_text(_row(kind="anr", code="MAIN_BLOCKED_23317s"))
    assert "ANR" in text
    assert "se quedó sin responder ≈ 6 h 28 min" in text
    assert "MAIN_BLOCKED_23317s" in text  # el código técnico no se pierde
    assert "pantalla apagada" in text


def test_alert_text_feedback_shows_the_person_words_and_decodes_json_payload():
    payload = {
        "detail": "Al abrir una serie se queda en negro y no pasa nada.",
        "screen": "Settings",
        "phase": "",
        "process": "com.chronosbvrx.visionplus",
    }
    text = diag.alert_text(
        _row(kind="feedback", code="USER_FEEDBACK", payload=json.dumps(payload))
    )
    assert "Mensaje nuevo" in text
    assert "Al abrir una serie se queda en negro" in text
    assert "USER_FEEDBACK" in text
    assert "pantalla «Settings»" in text


def test_alert_text_anomaly_known_code_uses_its_human_explanation():
    text = diag.alert_text(
        _row(kind="anomaly", code="FOCUS_LOST",
             payload={"detail": "id=abc intentos=12 adjunta=false entrada=true clave=home"})
    )
    assert "el mando se quedó sin foco" in text
    assert "FOCUS_LOST" in text
    assert "detalle: id=abc" in text


def test_alert_text_unknown_and_missing_fields_still_produce_a_safe_message():
    text = diag.alert_text({"kind": "anomaly", "code": ""})
    assert "Anomalía nueva" in text
    assert "Qué revisar" in text
    assert "sin código" in text
    assert "sin-huella" in text


def test_resolved_text_is_human_and_keeps_the_fingerprint():
    text = diag.resolved_text(
        _row(kind="anr", code="MAIN_BLOCKED_184s", devices=2, hits=3)
    )
    assert "Dejó de repetirse" in text
    assert "se congelaba" in text
    assert "ANR de 3 min 4 s" in text
    assert "6171ce10085be490" in text
    assert "48 h" in text


def test_feedback_quote_is_clipped_to_keep_the_message_short():
    text = diag.alert_text(
        _row(kind="feedback", code="USER_FEEDBACK",
             payload={"detail": "x" * 9000})
    )
    assert len(text) <= diag.MAX_ALERT_CHARS
    assert "x" * 800 not in text


def test_clip_marks_truncation_for_telegram():
    clipped = diag._clip("x" * 5000, diag.MAX_ALERT_CHARS)
    assert len(clipped) == diag.MAX_ALERT_CHARS
    assert clipped.endswith("…")


def test_diag_endpoints_registered():
    paths = {r.path for r in app.app.routes}
    assert "/v1/diag" in paths
    assert "/v1/diag/summary" in paths


def test_config_admin_ok():
    assert app._config_admin_ok("secreta", "secreta")
    assert not app._config_admin_ok("otra", "secreta")
    assert not app._config_admin_ok(None, "secreta")
    assert not app._config_admin_ok("secreta", "")


def test_config_endpoints_registered():
    paths = {r.path for r in app.app.routes}
    assert "/v1/config" in paths


# ----- salud del catálogo (lo que mantiene el buscador al día) -----


def test_is_stale_only_alerts_with_a_known_heartbeat():
    import datetime as dt

    now = dt.datetime(2026, 10, 19, 12, tzinfo=dt.timezone.utc)
    assert diag.is_stale(None, now, dt.timedelta(hours=10)) is False
    assert diag.is_stale(now - dt.timedelta(hours=9), now, dt.timedelta(hours=10)) is False
    assert diag.is_stale(now - dt.timedelta(hours=10), now, dt.timedelta(hours=10)) is True
    assert diag.is_stale(now - dt.timedelta(days=9), now, dt.timedelta(days=8)) is True


def test_health_alert_text_says_what_happened_and_what_to_check():
    import datetime as dt

    now = dt.datetime(2026, 10, 19, 12, tzinfo=dt.timezone.utc)
    cycle = diag.health_alert_text("cycle", now - dt.timedelta(hours=14), now)
    assert "dejó de actualizarse" in cycle
    assert "hace 14 h" in cycle
    assert "docker compose logs" in cycle
    deep = diag.health_alert_text("deep", now - dt.timedelta(days=9), now)
    assert "pasada profunda" in deep
    assert "hace 9 días" in deep
    assert "df -h /var/oled" in deep
