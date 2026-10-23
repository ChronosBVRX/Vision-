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


# ----- canario de builds nuevos (frena la oferta solo si un build sale roto) -----


def test_only_player_breakage_counts_for_the_canary():
    # Crash en la pantalla del reproductor: cuenta.
    assert diag.is_player_breakage("crash", "NoSuchMethodError", {"screen": "Player"}) is True
    assert diag.is_player_breakage("crash", "X", {"phase": "reproductor-vod"}) is True
    # Codigos que ya sabemos letales para el motor: cuentan aunque el screen venga vacio.
    assert diag.is_player_breakage("anomaly", "PLAYER_ERROR", {}) is True
    assert diag.is_player_breakage("anomaly", "PLAYER_RECONNECT_FAIL", {}) is True
    assert diag.is_player_breakage("anomaly", "ENGINE_NAMESPACE_LEAK", {}) is True
    assert diag.is_player_breakage("anomaly", "ENGINE_LOAD_FAIL", {}) is True
    # Ruido que NO debe frenar una version: crash en el menu, ANR de la tele suspendida y
    # avisos de recuperacion que tambien salen en equipos sanos.
    assert diag.is_player_breakage("crash", "IllegalArgumentException", {"screen": "Home"}) is False
    assert diag.is_player_breakage("anr", "MAIN_BLOCKED_64858s", {"screen": "Player"}) is False
    assert diag.is_player_breakage("anomaly", "PORTAL_SLOW", {"screen": "Player"}) is False
    assert diag.is_player_breakage("anomaly", "PLAYER_FRAME_SKIP", {"detail": "exceso 1200 ms"}) is False
    assert diag.is_player_breakage("anomaly", "PLAYER_SOFT_RESTART", {"phase": "reproductor-vod"}) is False
    assert diag.is_player_breakage("anomaly", "PLAYER_FAILOVER", {}) is False


def test_canary_pauses_a_fresh_build_with_repeated_player_failures():
    assert diag.canary_decision(
        fresh_hours=6, player_reports=3, player_devices=2,
        latest_code=354, paused_code=0, paused_hours=None,
    ) == "pause"
    # Un solo equipo no basta (puede ser su equipo, no el build).
    assert diag.canary_decision(
        fresh_hours=6, player_reports=9, player_devices=1,
        latest_code=354, paused_code=0, paused_hours=None,
    ) == "none"
    # Pocos reportes tampoco.
    assert diag.canary_decision(
        fresh_hours=6, player_reports=2, player_devices=2,
        latest_code=354, paused_code=0, paused_hours=None,
    ) == "none"
    # Un build viejo ya no se frena solo (seria ruido de cola larga).
    assert diag.canary_decision(
        fresh_hours=72, player_reports=20, player_devices=5,
        latest_code=340, paused_code=0, paused_hours=None,
    ) == "none"


def test_canary_rearms_itself_and_never_leaves_the_offer_off_forever():
    # Mientras siga frenado y no caduque, no hace nada mas.
    assert diag.canary_decision(
        fresh_hours=2, player_reports=10, player_devices=4,
        latest_code=354, paused_code=354, paused_hours=1,
    ) == "none"
    # Una version mas nueva en la telemetria rearma el freno.
    assert diag.canary_decision(
        fresh_hours=1, player_reports=0, player_devices=0,
        latest_code=355, paused_code=354, paused_hours=1,
    ) == "clear"
    # Y si nadie publica nada, el freno caduca solo.
    assert diag.canary_decision(
        fresh_hours=30, player_reports=10, player_devices=4,
        latest_code=354, paused_code=354, paused_hours=13,
    ) == "clear"


def test_canary_texts_say_the_verdict_and_how_to_get_out():
    pause = diag.canary_alert_text(
        "2.5.117", 353, 9, 2, "22/10/2026 08:04", ["NoSuchMethodError"], ["Chromecast HD", "Xiaomi"]
    )
    assert "2.5.117" in pause and "vc353" in pause
    assert "ota_paused=true" in pause
    assert "min_version_code" in pause
    assert "Chromecast HD" in pause
    clear = diag.canary_clear_text(353, "newer")
    assert "reactivada" in clear and "vc353" in clear


class _FakeConn:
    """Conexion falsa: devuelve filas preparadas y anota lo que el canario escribe."""

    def __init__(self, latest, rows, state):
        self.latest = latest
        self.rows = rows
        self.state = state
        self.executed = []

    async def fetchrow(self, sql, *args):
        return self.latest

    async def fetch(self, sql, *args):
        return self.state if "sync_meta" in sql else self.rows

    async def execute(self, sql, *args):
        self.executed.append((sql, args))


class _FakeCtx:
    def __init__(self, conn):
        self.conn = conn

    async def __aenter__(self):
        return self.conn

    async def __aexit__(self, *exc):
        return False


class _FakePool:
    def __init__(self, conn):
        self.conn = conn

    def acquire(self):
        return _FakeCtx(self.conn)


def test_canary_wiring_pauses_a_broken_fresh_build(monkeypatch):
    import asyncio
    import datetime as dt

    latest = {
        "version_code": 354,
        "app_version": "2.5.118",
        "first_seen": dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=3),
    }
    rows = [
        {"kind": "crash", "code": "NoSuchMethodError", "device": "Chromecast HD", "payload": {"screen": "Player"}},
        {"kind": "crash", "code": "NoSuchMethodError", "device": "Xiaomi", "payload": {"screen": "Player"}},
        {"kind": "crash", "code": "NoSuchMethodError", "device": "Xiaomi", "payload": {"screen": "Player"}},
        {"kind": "anr", "code": "MAIN_BLOCKED_64858s", "device": "ZTE", "payload": {"screen": "Home"}},
    ]
    conn = _FakeConn(latest, rows, [])
    alerts = []
    monkeypatch.setattr(diag, "send_alert", lambda text: alerts.append(text))

    assert asyncio.run(diag.build_canary(_FakePool(conn))) == 1
    paused = [sql for sql, _ in conn.executed if "ota_paused" in sql and "'true'" in sql]
    assert paused, "tiene que escribir ota_paused=true"
    stamped = [args for sql, args in conn.executed if "sync_meta" in sql]
    assert any(a and str(a[0]) == diag.CANARY_VERSION_KEY for a in stamped)
    assert alerts and "Build roto" in alerts[0]
    assert "2.5.118" in alerts[0]


def test_canary_wiring_rearms_when_a_newer_version_appears(monkeypatch):
    import asyncio
    import datetime as dt

    latest = {
        "version_code": 355,
        "app_version": "2.5.119",
        "first_seen": dt.datetime.now(dt.timezone.utc) - dt.timedelta(minutes=20),
    }
    refused_at = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=1)
    state = [
        {"key": diag.CANARY_VERSION_KEY, "value": "354", "updated_at": refused_at},
        {"key": diag.CANARY_AT_KEY, "value": "", "updated_at": refused_at},
    ]
    conn = _FakeConn(latest, [], state)
    alerts = []
    monkeypatch.setattr(diag, "send_alert", lambda text: alerts.append(text))

    assert asyncio.run(diag.build_canary(_FakePool(conn))) == 1
    assert any("ota_paused" in sql and "'false'" in sql for sql, _ in conn.executed)
    assert any("delete from sync_meta" in sql for sql, _ in conn.executed)
    assert alerts and "reactivada" in alerts[0]


# ----- SLO de calidad: % de aperturas sin crash -----


def test_crash_free_pct_math():
    assert diag.crash_free_pct(0, 0) == 100.0
    assert diag.crash_free_pct(100, 0) == 100.0
    assert diag.crash_free_pct(100, 1) == 99.0
    assert diag.crash_free_pct(100, 2) == 98.0
    assert diag.crash_free_pct(3, 1) == 66.67
    # Datos imposibles (más crashes que sesiones) no rompen el cálculo.
    assert diag.crash_free_pct(2, 5) == 0.0


def test_quality_only_alerts_below_target_with_enough_sample_and_no_cooldown():
    # Muestra suficiente, por debajo del objetivo, sin aviso previo: avisa.
    assert diag.quality_should_alert(sessions=100, crashed=3, hours_since_alert=None) is True
    # Igual o por encima del objetivo: no.
    assert diag.quality_should_alert(sessions=100, crashed=1, hours_since_alert=None) is False
    assert diag.quality_should_alert(sessions=100, crashed=0, hours_since_alert=None) is False
    # Pocas aperturas no son una regresión (2 de 3 no justifica despertar a nadie).
    assert diag.quality_should_alert(sessions=3, crashed=2, hours_since_alert=None) is False
    # Enfriamiento: el aviso no se repite cada 5 minutos.
    assert diag.quality_should_alert(sessions=100, crashed=3, hours_since_alert=2.0) is False
    assert diag.quality_should_alert(sessions=100, crashed=3, hours_since_alert=25.0) is True


def test_quality_alert_text_says_the_number_and_what_to_check():
    text = diag.quality_alert_text(100, 4, "2.5.122", 96.0)
    assert "96.00% sin crash" in text
    assert "objetivo 99%" in text
    assert "v2.5.122" in text
    assert "/v1/diag/summary" in text
    assert "sesiones=100" in text and "con_crash=4" in text


def test_health_payload_summarises_per_version_and_overall():
    rows = [
        {"app_version": "2.5.122", "version_code": 358, "sessions": 100, "crashed": 1, "anr": 2},
        {"app_version": "2.5.121", "version_code": 357, "sessions": 50, "crashed": 1, "anr": 0},
    ]
    payload = app.diag_health_payload(rows)
    assert payload["sessions"] == 150
    assert payload["crashedSessions"] == 2
    assert payload["crashFreePct"] == 98.67
    assert payload["meetsTarget"] is False
    assert [v["version"] for v in payload["versions"]] == ["2.5.122", "2.5.121"]
    assert payload["versions"][0]["crashFreePct"] == 99.0
    assert payload["versions"][1]["anrFreePct"] == 100.0


def test_health_payload_without_sessions_does_not_alarm():
    payload = app.diag_health_payload([])
    assert payload["sessions"] == 0
    assert payload["crashFreePct"] == 100.0
    assert payload["meetsTarget"] is True


def test_health_endpoint_is_registered_and_session_is_a_valid_kind():
    paths = {r.path for r in app.app.routes}
    assert "/v1/diag/health" in paths
    assert "session" in app.DIAG_KINDS


def test_triage_ignores_session_heartbeats():
    # Un latido por apertura NUNCA puede generar una alerta ni mantener viva una huella.
    import inspect

    source = inspect.getsource(diag.triage)
    assert source.count("kind <> 'session'") >= 3
    # Tampoco debe ocupar los primeros puestos del resumen (taparía crashes reales).
    assert "kind <> 'session'" in inspect.getsource(app.diag_summary)


class _QualityConn:
    """Conexión falsa: sirve las estadísticas y el estado del enfriamiento."""

    def __init__(self, stats, state):
        self.stats = stats
        self.state = state
        self.executed = []

    async def fetchrow(self, sql, *args):
        return self.state if "sync_meta" in sql else self.stats

    async def execute(self, sql, *args):
        self.executed.append((sql, args))


def test_quality_slo_alerts_a_real_regression(monkeypatch):
    import asyncio

    conn = _QualityConn(
        {"sessions": 100, "crashed": 4, "app_version": "2.5.122", "version_code": 358},
        None,
    )
    alerts = []
    monkeypatch.setattr(diag, "send_alert", lambda text: alerts.append(text))

    assert asyncio.run(diag.quality_slo(_FakePool(conn))) == 1
    assert alerts and "96.00% sin crash" in alerts[0]
    assert any(a and str(a[0]) == diag.QUALITY_ALERT_KEY for _, a in conn.executed)


def test_quality_slo_respects_the_cooldown(monkeypatch):
    import asyncio
    import datetime as dt

    recent = dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=1)
    conn = _QualityConn(
        {"sessions": 100, "crashed": 4, "app_version": "2.5.122", "version_code": 358},
        {"updated_at": recent},
    )
    alerts = []
    monkeypatch.setattr(diag, "send_alert", lambda text: alerts.append(text))

    assert asyncio.run(diag.quality_slo(_FakePool(conn))) == 0
    assert alerts == []
    assert conn.executed == []

