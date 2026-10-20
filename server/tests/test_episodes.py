"""Tests del espejo de capítulos (`/v1/episodes` + worker `episodes_mirror`).

Solo lógica pura: la DB nunca se toca (rows sintéticos) y el portal se simula
con un cliente falso que responde fichas enlatadas. La promesa que se fija:
el espejo SOLO PUEDE MEJORAR — sin copia fresca, todo cae al portal como hoy.
"""

import json
import sys
import time
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).parent.parent / "api"))
sys.path.insert(0, str(Path(__file__).parent.parent / "sync"))

import episodes as api_episodes  # noqa: E402
import episodes_mirror  # noqa: E402


NOW = 1760000000.0


class _Stamp:
    """Un objeto con .timestamp() como datetime, para no depender de la zona."""

    def __init__(self, ts):
        self._ts = ts

    def timestamp(self):
        return self._ts

    def isoformat(self):
        return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(self._ts))


def _real_row(status="ok", age_s=0.0, attempts=0, requested_age_s=0.0, payload=None):
    return {
        "ref": "xtv:111",
        "status": status,
        "payload": payload if payload is not None else [],
        "episodes": len(payload or []),
        "fetched_at": _Stamp(NOW - age_s) if status == "ok" else None,
        "requested_at": _Stamp(NOW - requested_age_s),
        "updated_at": _Stamp(NOW - age_s),
        "attempts": attempts,
    }


# ----- refs válidas -----


def test_ref_accepts_prefixed_and_bare_cid():
    assert api_episodes.valid_ref("xtv:6950367441337182593") == "xtv:6950367441337182593"
    assert api_episodes.valid_ref("6950367441337182593") == "xtv:6950367441337182593"


def test_ref_rejects_other_sources_and_garbage():
    assert api_episodes.valid_ref("magis:123") is None
    assert api_episodes.valid_ref("ia:night") is None
    assert api_episodes.valid_ref("xtv:") is None
    assert api_episodes.valid_ref("") is None
    assert api_episodes.valid_ref(None) is None
    assert api_episodes.valid_ref("xtv:abc:def") is None  # dos puntos de más


# ----- frescura y encolado (la promesa: solo puede mejorar) -----


def test_fresh_ok_row_is_served_and_not_enequeued():
    row = _real_row(status="ok", age_s=3600.0, payload=[{"contentId": "1"}, {"contentId": "2"}])
    ts = NOW
    assert api_episodes.is_fresh(row, ts) is True
    assert api_episodes.needs_enqueue(row, ts) is False
    body = api_episodes.payload("xtv:111", row, ts)
    assert body["status"] == "ok" and len(body["episodes"]) == 2


def test_stale_ok_row_serves_nothing_and_enequeues_refresh():
    row = _real_row(status="ok", age_s=72 * 3600.0, payload=[{"contentId": "1"}, {"contentId": "2"}])
    ts = NOW
    assert api_episodes.is_fresh(row, ts) is False
    assert api_episodes.needs_enqueue(row, ts) is True
    body = api_episodes.payload("xtv:111", row, ts)
    # vencida → vacía honesta (la app cae al portal, fresca de verdad)
    assert body["status"] == "miss" and body["episodes"] == []


def test_missing_row_enequeues_and_serves_empty():
    assert api_episodes.needs_enqueue(None, NOW) is True
    body = api_episodes.payload("xtv:111", None, NOW)
    assert body["status"] == "miss" and body["episodes"] == []


def test_payload_decodes_the_jsonb_string_asyncpg_delivers():
    # asyncpg entrega `jsonb` como str: sin decode, `episodes` viajaba como una
    # lista de CARACTERES (medido en vivo: 1935 «episodios» = el largo del JSON).
    row = _real_row(status="ok", age_s=60.0, payload=json.dumps(
        [{"contentId": "1", "name": "C1", "seriesNumber": 1, "episodeNumber": 1},
         {"contentId": "2", "name": "C2", "seriesNumber": 1, "episodeNumber": 2}],
        separators=(",", ":"), ensure_ascii=False))
    body = api_episodes.payload("xtv:111", row, NOW)
    assert body["status"] == "ok"
    assert [e["contentId"] for e in body["episodes"]] == ["1", "2"]
    # payload corrupto → miss honesto, jamás basura
    row_bad = _real_row(status="ok", age_s=60.0, payload="{roto")
    assert api_episodes.payload("xtv:111", row_bad, NOW)["status"] == "miss"
    assert api_episodes.needs_enqueue(None, NOW) is True
    body = api_episodes.payload("xtv:111", None, NOW)
    assert body["status"] == "miss" and body["episodes"] == []


def test_recent_pending_does_not_requeue_but_old_one_does():
    fresh_pending = _real_row(status="pending", requested_age_s=30.0)
    assert api_episodes.needs_enqueue(fresh_pending, NOW) is False
    old_pending = _real_row(status="pending", requested_age_s=3600.0)
    assert api_episodes.needs_enqueue(old_pending, NOW) is True


def test_failed_row_awaits_cooldown_and_respects_attempt_ceiling():
    recent_failed = _real_row(status="failed", attempts=1, age_s=60.0)
    assert api_episodes.needs_enqueue(recent_failed, NOW) is False  # enfriamiento de 24 h
    old_failed = _real_row(status="failed", attempts=1, age_s=30 * 3600.0)
    assert api_episodes.needs_enqueue(old_failed, NOW) is True  # reintento acotado
    spent = _real_row(status="failed", attempts=6, age_s=100 * 3600.0)
    assert api_episodes.needs_enqueue(spent, NOW) is False  # attempts >= techo: quieto


# ----- extracción del portal (campos crudos, sin decidir nada) -----


def test_extract_keeps_only_program_fields_and_order():
    asset = {
        "simpleProgramList": [
            {"contentId": "c2", "name": "Attack on Titan T2_2", "seriesNumber": 2, "episodeNumber": 2, "junk": 1},
            {"contentId": "c1", "name": "Attack on Titan T2_1", "seriesNumber": 1},
            {"name": "sin id", "seriesNumber": 3},
            "no-es-dict",
        ]
    }
    rows = episodes_mirror.extract_programs(asset)
    assert [r["contentId"] for r in rows] == ["c2", "c1"]  # orden del portal intacto
    assert rows[1] == {"contentId": "c1", "name": "Attack on Titan T2_1", "seriesNumber": 1, "episodeNumber": 0}
    assert "junk" not in rows[0]  # payload pelado: solo lo que el parser consume


def test_extract_accepts_full_response_and_bare_assetdata():
    # Bug cazado EN VIVO: el drain pasaba la respuesta COMPLETA (con assetData
    # dentro) y el extractor leía simpleProgramList de arriba → 0 filas → el
    # espejo marcaba `failed` series perfectas mientras la sonda directa veía 12.
    eps = [{"contentId": "a", "name": "C1", "seriesNumber": 1},
           {"contentId": "b", "name": "C2", "seriesNumber": 2}]
    full = {"returnCode": "0", "assetData": {"simpleProgramList": eps}}
    assert len(episodes_mirror.extract_programs(full)) == 2
    assert len(episodes_mirror.extract_programs(full["assetData"])) == 2
    assert episodes_mirror.extract_programs({}) == []
    assert episodes_mirror.extract_programs(None) == []


# ----- baja con cliente falso (type 0 → respaldo type 1) -----


class FakeClient:
    def __init__(self, zero=None, one=None, fail=None, fail_zero=None, fail_one=None):
        self._zero = zero or {}
        self._one = one or {}
        self.fail = fail
        self.fail_zero = fail_zero
        self.fail_one = fail_one
        self.calls = []

    @property
    def user_token(self):
        return "tok"

    @property
    def user_id(self):
        return "1"

    def _post(self, path, spec):
        self.calls.append((spec["contentId"], spec["type"]))
        if self.fail:
            raise RuntimeError(self.fail)
        if spec["type"] == "0":
            if self.fail_zero:
                raise RuntimeError(self.fail_zero)
            return self._zero
        if self.fail_one:
            raise RuntimeError(self.fail_one)
        return self._one


def test_fetch_takes_type0_when_it_has_a_real_list():
    client = FakeClient(zero={"simpleProgramList": [{"contentId": "a"}, {"contentId": "b"}]})
    mode, rows = episodes_mirror.fetch_programs(client, "123")
    assert mode == "ok" and [r["contentId"] for r in rows] == ["a", "b"]
    assert ("123", "0") in client.calls and ("123", "1") not in client.calls


def test_fetch_falls_back_to_type1_when_type0_is_empty():
    client = FakeClient(
        zero=dict(),
        one={"simpleProgramList": [{"contentId": "a"}, {"contentId": "b"}]},
    )
    mode, rows = episodes_mirror.fetch_programs(client, "123")
    assert mode == "ok" and len(rows) == 2
    assert ("123", "1") in client.calls


def test_fetch_single_is_a_movie_not_a_series():
    client = FakeClient(zero={"simpleProgramList": [{"contentId": "solo"}]})
    mode, _ = episodes_mirror.fetch_programs(client, "123")
    assert mode == "single"


def test_fetch_empty_when_portal_brings_nothing():
    client = FakeClient(zero=dict(), one=dict())
    mode, _ = episodes_mirror.fetch_programs(client, "123")
    assert mode == "empty"


def test_fetch_raises_when_both_types_fail():
    # Caso medido EN VIVO: el portal a veces sirve el type 0 sin lista y el
    # type 1 responde 100008. La app tolera (runCatching + continue) y acaba
    # con ficha vacía; el espejo sube el error del portal para que la cola
    # lo clasifique (contenido → terminal; sesión → re-agenda; red → reintento).
    client = FakeClient(zero=dict(), fail_one="portal portal100008 节目不存在")
    try:
        episodes_mirror.fetch_programs(client, "123")
        raise AssertionError("debía subir el error del portal")
    except RuntimeError as exc:
        assert episodes_mirror.classify_error(str(exc)) == "content"


def test_fetch_uses_type1_rows_when_type0_fails():
    client = FakeClient(
        fail_zero="portal portal100008 节目不存在",
        one={"simpleProgramList": [{"contentId": "a"}, {"contentId": "b"}]},
    )
    mode, rows = episodes_mirror.fetch_programs(client, "123")
    assert mode == "ok" and len(rows) == 2


def test_fetch_raises_the_worst_error_when_both_types_fail():
    client = FakeClient(fail_zero="portal 100002 sesion", fail_one="portal portal100008 no existe")
    try:
        episodes_mirror.fetch_programs(client, "123")
        raise AssertionError("debía subir el error")
    except RuntimeError as exc:
        # La sesión manda: si los dos tipos fallaron y uno fue de sesión, ese sube
        # para que la cola se re-agende en vez de marcar fallo terminal.
        assert episodes_mirror.classify_error(str(exc)) == "session"


# ----- clasificación de errores (jamás acuñar: sesión = re-agenda, no golpe) -----


def test_session_errors_are_detected():
    assert episodes_mirror.classify_error("portal 100002 whatever") == "session"
    assert episodes_mirror.classify_error("portal aaa100083 账号已经在其他设备登录") == "session"
    assert episodes_mirror.classify_error("portal 100009 raro") == "net"
    assert episodes_mirror.classify_error("XTV sin hosts disponibles") == "net"


def test_missing_content_is_terminal_not_a_retry():
    assert episodes_mirror.classify_error("portal portal100006 剧集不存在") == "content"
    assert episodes_mirror.classify_error("portal portal100008 节目不存在") == "content"


# ----- presupuesto / cola -----


def test_claims_respects_budget_and_order():
    rows = [{"ref": f"xtv:{i}"} for i in range(50)]
    assert episodes_mirror.claims(rows, 30) == [f"xtv:{i}" for i in range(30)]
    assert episodes_mirror.claims(rows, 0) == []
    assert episodes_mirror.claims([], 30) == []


# ----- SQL del worker: la cola se agota sola y una sesión muerta no martilla -----


def test_drain_sql_only_takes_pending_within_budget():
    src = open(Path(__file__).parent.parent / "sync" / "episodes_mirror.py", encoding="utf-8").read()
    assert "status = 'pending' and (next_try_at is null or next_try_at <= now())" in src
    assert "order by requested_at" in src
    assert "insert into episode_mirror" in src
    # una sesión rechazada re-agenda la cola en bloque (no golpea ref por ref)
    assert "ref = any($3::text[])" in src
    # los errores jamás reinician el contador de intentos
    assert "attempts = episode_mirror.attempts + 1" in src


def test_endpoint_sql_guards_catalog_refs_and_cooldown():
    src = open(Path(__file__).parent.parent / "api" / "app.py", encoding="utf-8").read()
    # solo refs del catálogo pueden encolarse (guardia dura anti-scraping)
    assert "select exists(select 1 from titles where id = $1)" in src
    # la cola no se reescribe si ya está fresca de pedir (needs_enqueue)
    assert "on conflict (ref) do update set" in src


def test_schema_has_mirror_table():
    sql = open(Path(__file__).parent.parent / "sync" / "schema.sql", encoding="utf-8").read()
    assert "create table if not exists episode_mirror" in sql
    assert "episode_mirror_pending_idx" in sql


# ----- metadatos por episodio (nombre real + sinopsis, TMDB → Postgres) -----


def test_parse_tmdb_season_takes_name_overview_and_still():
    payload = {
        "episodes": [
            {"episode_number": 1, "name": "El comienzo", "overview": "Un inicio.",
             "still_path": "/abc.jpg"},
            {"episode_number": 2, "name": "La duda", "overview": "", "still_path": None},
            {"episode_number": 0, "name": "sin número"},
            {"episode_number": 3, "name": None, "overview": None, "still_path": "https://x/y.jpg"},
        ]
    }
    rows = episodes_mirror.parse_tmdb_season(payload)
    assert [r["episode"] for r in rows] == [1, 2, 3]
    assert rows[0]["still"].endswith("/w780/abc.jpg")
    assert rows[1]["still"] == ""
    # una URL completa NO se vuelve a prefijar; solo la ruta relativa de TMDB
    assert rows[2]["still"] == ""


def test_merge_season_meta_uses_english_only_to_fill_gaps():
    primary = [
        {"episode": 1, "name": "El comienzo", "overview": "", "still": ""},
        {"episode": 2, "name": "", "overview": "Algo", "still": ""},
    ]
    fallback = [
        {"episode": 1, "name": "The Beginning", "overview": "A start.", "still": "s1"},
        {"episode": 2, "name": "The Doubt", "overview": "B", "still": "s2"},
    ]
    rows = episodes_mirror.merge_season_meta(primary, fallback)
    # el es-MX nunca se pisa; el inglés solo rellena lo vacío
    assert rows[0] == {"episode": 1, "name": "El comienzo", "overview": "A start.", "still": "s1"}
    assert rows[1]["name"] == "The Doubt" and rows[1]["overview"] == "Algo"


def test_meta_due_respects_ttl_retry_and_attempt_ceiling():
    now = 1_000_000_000.0
    # sin fila → toca
    assert episodes_mirror.meta_due(None, 0, None, now) is True
    # ok dentro del TTL → no; vencida → sí
    ttl = episodes_mirror.META_TTL_HOURS * 3600.0
    assert episodes_mirror.meta_due("ok", 0, now - ttl + 60, now) is False
    assert episodes_mirror.meta_due("ok", 0, now - ttl - 60, now) is True
    # failed: respeta el enfriamiento y el techo de intentos
    retry = episodes_mirror.META_RETRY_HOURS * 3600.0
    assert episodes_mirror.meta_due("failed", 1, now - retry + 60, now) is False
    assert episodes_mirror.meta_due("failed", 1, now - retry - 60, now) is True
    assert episodes_mirror.meta_due(
        "failed", episodes_mirror.META_MAX_ATTEMPTS, now - retry * 10, now,
    ) is False


def test_api_enrich_glues_meta_by_episode_number():
    episodes = [
        {"contentId": "a", "name": "Serie T2_01", "seriesNumber": 1, "episodeNumber": 0},
        {"contentId": "b", "name": "Serie T2_02", "seriesNumber": 2, "episodeNumber": 0},
    ]
    meta = [{"episode": 1, "name": "El comienzo", "overview": "Un inicio.", "still": "s1"}]
    out = api_episodes.enrich(episodes, meta)
    assert out[0]["title"] == "El comienzo" and out[0]["overview"] == "Un inicio."
    # sin fila para ese capítulo: campos vacíos, jamás basura
    assert out[1]["title"] == "" and out[1]["overview"] == "" and out[1]["still"] == ""
    # el nombre crudo del portal NO se toca (compatibilidad del parser de la app)
    assert out[0]["name"] == "Serie T2_01"


def test_endpoint_serves_episode_meta_only_for_known_seasons():
    src = open(Path(__file__).parent.parent / "api" / "app.py", encoding="utf-8").read()
    assert "from episode_meta m" in src
    assert "episodes_mirror.enrich(" in src
    # la temporada del ref sale de group_members (misma fuente que el índice)
    assert "select gm.season from group_members gm" in src


def test_schema_has_episode_meta_tables():
    sql = open(Path(__file__).parent.parent / "sync" / "schema.sql", encoding="utf-8").read()
    assert "create table if not exists episode_meta (" in sql
    assert "create table if not exists episode_meta_season" in sql
    # una temporada se pide UNA vez: la llave es (tmdb_id, season)
    assert "primary key (tmdb_id, season)" in sql
