"""Pruebas de GeeSports V3.8 (`sync/gee38.py` y endpoints `/v1/gee38/*`).

Sin red y sin la clave real: el cifrado se prueba **con ida y vuelta** (el test
cifra con la receta invertida y el módulo descifra) más vectores fijos de la
derivación de clave; el parseo usa muestras del formato real del CMS (pares
`{cat}`/`{channel}`/`{event}` con JSON embebido como string); y los endpoints se
prueban con un pool falso que captura el SQL. La clave real vive solo en el
`.env` del VPS (el repo es público).
"""

import base64
import json
from datetime import date, datetime, timezone

import pytest

import gee38

KEY = b"TestKey123456789"  # 16 bytes (nunca la real)


# ------------------------------------------------------------------ cifrado

def encrypt_for_test(plain: bytes, key: bytes) -> str:
    """Receta invertida: claro -> AES-CBC -> b64 -> reversa -> pairswap -> b64."""
    pad = 16 - (len(plain) % 16)
    ct = gee38.AES.new(key, gee38.AES.MODE_CBC, gee38.IV).encrypt(plain + bytes([pad]) * pad)
    x = base64.b64encode(ct).decode()
    l1 = gee38.pairswap(x[::-1])
    return base64.b64encode(l1.encode("latin-1")).decode()


def test_crypto_round_trip():
    payload = json.dumps([{"cat": "{\"name\": \"TNT Sports\"}"}], ensure_ascii=False).encode()
    body = encrypt_for_test(payload, KEY)
    assert gee38.decrypt_body(body, KEY) == payload


def test_crypto_tolerates_cms_whitespace():
    payload = b'{"app_version": 11}'
    body = encrypt_for_test(payload, KEY)
    misc = "  " + body[:20] + "\n" + body[20:] + "\n"
    assert gee38.decrypt_body(misc, KEY) == payload


def test_derive_key_vectors():
    # Vectores fijos: la receta (SHA-256 -> b64 -> reversa -> pairswap -> b64 -> 16)
    # quedó clavada en vivo contra el APK; si alguien la toca, esto lo delata.
    assert gee38.derive_key_from_cert(b"gee38-test-cert") == b"WT14a1dJRGY3Q1Jn"
    assert gee38.derive_key_from_cert(bytes(range(64))) == b"Zz1FUWtWMGRSOGpF"


def test_resolve_key_accepts_ascii_and_hex(monkeypatch):
    monkeypatch.delenv("GEE38_APK", raising=False)
    monkeypatch.setenv("GEE38_KEY", "TestKey123456789")
    assert gee38.resolve_key() == KEY
    monkeypatch.setenv("GEE38_KEY", KEY.hex())
    assert gee38.resolve_key() == KEY


def test_resolve_key_rejects_garbage_without_apk(monkeypatch):
    monkeypatch.delenv("GEE38_APK", raising=False)
    monkeypatch.setenv("GEE38_KEY", "corta")
    assert gee38.resolve_key() is None


# ------------------------------------------------------------------ parseo

def test_parse_groups_reads_embedded_cat_json():
    raw = [
        {"cat": json.dumps({"name": "TNT Sports", "logo": "https://i.ibb.co/x.jpg",
                            "type": "custom", "api": "channels/AbC.txt"})},
        {"cat": json.dumps({"name": "Live Events SD", "type": "m3u",
                            "api": "https://fawasp.pages.dev/playlist.txt"})},
        {"nope": 1},
    ]
    groups = gee38.parse_groups(json.dumps(raw).encode())
    assert [g["name"] for g in groups] == ["TNT Sports", "Live Events SD"]
    assert groups[0]["type"] == "custom"
    assert groups[1]["api"].startswith("https://")


def test_parse_group_bundle_reads_channels_and_link_names():
    ch = {"channel": json.dumps({
        "name": "TNT Sports", "logo": "l", "visible": "True",
        "links": "pro/AbC.txt", "link_names": "['TNT SPORTS 1', 'TNT SPORTS 2']",
    })}
    out = gee38.parse_group_bundle(json.dumps([ch]).encode())
    assert out[0]["links_ref"] == "pro/AbC.txt"
    assert out[0]["link_names"] == ["TNT SPORTS 1", "TNT SPORTS 2"]
    assert out[0]["visible"] is True
    assert out[0]["is_playlist"] is False


def test_parse_group_bundle_accepts_list_link_names():
    ch = {"channel": json.dumps({"name": "X", "link_names": ["A", "B"]})}
    out = gee38.parse_group_bundle(json.dumps([ch]).encode())
    assert out[0]["link_names"] == ["A", "B"]


def test_split_link_separates_headers():
    url, headers = gee38.split_link("https://x/y.mpd|user-agent=Mozilla%2F5.0&referer=https://a/b")
    assert url == "https://x/y.mpd"
    assert headers["user-agent"] == "Mozilla/5.0"
    assert headers["referer"] == "https://a/b"
    assert gee38.split_link("https://plain/hls.m3u8") == ("https://plain/hls.m3u8", {})


def test_parse_pro_maps_dash_clearkey_and_hls():
    items = [
        {"name": "TNT SPORTS 1",
         "link": "https://otte.cache.aiv-cdn.net/x/cenc.mpd|user-agent=M",
         "scheme": "0",
         "api": "69a5aa835a061ce64a630d1046727e40:d02feac8a999bd06bf4059bf33411749",
         "tokenApi": ""},
        {"name": "Azteca", "link": "https://cdn/hls.m3u8", "scheme": 3, "api": ""},
        {"name": "sin link", "link": ""},
    ]
    out = gee38.parse_pro(json.dumps(items).encode())
    assert len(out) == 2
    assert out[0]["type"] == "dash"
    assert out[0]["drm"].startswith("69a5aa")
    assert out[0]["headers"] == {"user-agent": "M"}
    assert out[1]["type"] == "hls" and out[1]["drm"] == ""


M3U = """#EXTM3U
#EXTINF:-1 tvg-id="tnt" tvg-logo="https://l/x.png" group-title="Deportes",TNT SPORTS 1
#EXTVLCOPT:http-referrer=https://foo.bar/
#EXTVLCOPT:http-user-agent=Mozilla/5.0
#EXTVLCOPT:http-origin=https://foo.bar
https://cdn.example/live.m3u8
#EXTINF:-1 group-title="Deportes",FOX Sports
https://cdn.example/fox.m3u8
"""


def test_parse_m3u_carries_vlc_headers_into_the_link():
    out = gee38.parse_m3u(M3U)
    assert [i["name"] for i in out] == ["TNT SPORTS 1", "FOX Sports"]
    assert out[0]["headers"]["referer"] == "https://foo.bar/"
    assert out[0]["headers"]["user-agent"] == "Mozilla/5.0"
    assert out[0]["headers"]["origin"] == "https://foo.bar"
    assert out[0]["link"].startswith("https://cdn.example/live.m3u8|")
    assert out[1]["headers"] == {}


EVENT = {
    "category": "Football",
    "eventName": "Liga MX: America vs Chivas",
    "eventLogo": "https://l/e.png",
    "teamAName": "America", "teamBName": "Chivas",
    "teamAFlag": "https://l/mx.png", "teamBFlag": "https://l/mx2.png",
    "links": "pro/Ev123.txt",
    "link_names": "['Azteca 7', 'Canal 5']",
    "date": "30/06/2026", "time": "19:00:00", "visible": "false", "priority": 0,
}


def test_parse_events_reads_fields_and_dates():
    out = gee38.parse_events(json.dumps([{"event": json.dumps(EVENT)}]).encode())
    assert len(out) == 1
    ev = out[0]
    assert ev["event"].startswith("Liga MX")
    assert ev["date_iso"] == "2026-06-30"
    assert ev["priority"] == 0          # un 0 legítimo no se convierte en -1
    assert ev["visible"] is False
    assert ev["link_names"] == ["Azteca 7", "Canal 5"]


def test_parse_events_tolerates_bad_dates():
    bad = dict(EVENT, date="tbd")
    out = gee38.parse_events(json.dumps([{"event": json.dumps(bad)}]).encode())
    assert out[0]["date_iso"] is None


def test_sort_links_puts_alive_first_and_keeps_origin_order():
    links = [
        {"name": "muerto", "alive": False},
        {"name": "vivo2", "alive": True},
        {"name": "duda", "alive": None},
        {"name": "vivo1", "alive": True},
    ]
    out = gee38._sort_links(links)
    # Vivos primero (conservando su orden de origen: el CMS publica el mejor primero),
    # luego los de duda y al final los caídos.
    assert [lk["name"] for lk in out] == ["vivo2", "vivo1", "duda", "muerto"]


# ------------------------------------------- hermanos del mismo cruce (enlaces)

def _lk(name, url, alive=None):
    return {"name": name, "url": url, "link": url, "alive": alive, "type": "hls", "drm": "", "headers": {}}


def test_team_pair_key_ignores_order_accents_and_signs():
    assert gee38._team_pair_key("Atlético Madrid", "Espanyol") == gee38._team_pair_key("Espanyol", "Atl\u00e9tico  Madrid")
    assert gee38._team_pair_key("Everton", "Chelsea") == "chelsea|everton"
    assert gee38._team_pair_key("", "Chelsea") == ""
    assert gee38._team_pair_key("X", "x") == ""


def test_merge_sibling_links_completes_a_poor_list_with_the_live_sibling():
    # El caso real (Everton-Chelsea 17-oct): la entrada de hoy traía UN enlace muerto;
    # su hermana (13-oct) traía la lista completa con 6 vivos. Se fusionan y los vivos
    # quedan primero: lo que la app original ya mostraba.
    own = [_lk("STREAM TV + HD", "https://edgestream2.pro/hls/.m3u8", alive=False)]
    sibling = [
        _lk("TNT1UK SPORTS FHD", "https://otte/a", alive=True),
        _lk("FUBO SPORTS FHD", "https://fubo/b", alive=True),
    ]
    out = gee38._merge_sibling_links(own, "2026-10-17", [("2026-10-13", sibling)])
    assert [lk["url"] for lk in out] == ["https://otte/a", "https://fubo/b", "https://edgestream2.pro/hls/.m3u8"]


def test_merge_sibling_links_prefers_own_and_never_duplicates():
    own = [_lk("TV QUALITY", "https://a", alive=True)]
    sibling = [_lk("TV QUALITY (viejo)", "https://a", alive=False), _lk("HD QUALITY", "https://b", alive=True)]
    out = gee38._merge_sibling_links(own, "2026-10-17", [("2026-10-16", sibling)])
    assert len(out) == 2
    first = next(lk for lk in out if lk["url"] == "https://a")
    assert first["name"] == "TV QUALITY" and first["alive"] is True


def test_merge_sibling_links_orders_siblings_by_day_proximity():
    near = [_lk("NEAR", "https://near", alive=True)]
    far = [_lk("FAR", "https://far", alive=True)]
    out = gee38._merge_sibling_links([], "2026-10-17", [("2026-10-05", far), ("2026-10-16", near)])
    assert [lk["url"] for lk in out] == ["https://near", "https://far"]


def test_merge_sibling_links_caps_the_selector():
    many = [_lk(f"L{i}", f"https://x/{i}", alive=True) for i in range(30)]
    out = gee38._merge_sibling_links([], "2026-10-17", [("2026-10-16", many)], cap=18)
    assert len(out) == 18


def test_parse_app_keeps_known_fields():
    raw = json.dumps([{"app_version": 11, "web_url": "https://w", "otro": 5,
                       "download_url": "https://d"}])
    out = gee38.parse_app(raw.encode())
    assert out["app_version"] == 11 and out["web_url"] == "https://w"
    assert "otro" not in out and out["download_url"] == "https://d"


def test_busted_url_protects_cms_files_but_not_external_ones():
    url = gee38._busted_url("events.txt")
    assert url.startswith("https://new.whatyouthink.site/events.txt?t=")
    external = "https://fawasp.pages.dev/playlist.txt"
    assert gee38._busted_url(external) == external
    assert gee38._busted_url("channels/xyz.txt").startswith("https://new.whatyouthink.site/channels/xyz.txt?t=")


def test_rc_api_url_extracts_the_domain():
    assert gee38._rc_api_url({"entries": {"api_url": "https://nuevo.dominio/"}}) == "https://nuevo.dominio/"
    assert gee38._rc_api_url({"entries": {"api_url": "no-url"}}) == ""
    assert gee38._rc_api_url({}) == ""


def test_key_from_env_forms(monkeypatch):
    monkeypatch.setenv("GEE38_KEY", "TestKey123456789")
    assert gee38._key_from_env() == KEY
    monkeypatch.setenv("GEE38_KEY", KEY.hex())
    assert gee38._key_from_env() == KEY
    monkeypatch.setenv("GEE38_KEY", "corta")
    assert gee38._key_from_env() is None


def test_decrypt_body_rejects_a_retired_page():
    # Los archivos retirados devuelven la página HTML del hosting: no es payload.
    with pytest.raises(ValueError):
        gee38.decrypt_body("<!DOCTYPE html>\n<html lang=\"en\">404</html>", KEY)


def test_event_id_is_stable_and_content_based():
    base = gee38.parse_events(json.dumps([{"event": json.dumps(EVENT)}]).encode())[0]
    other = gee38.parse_events(
        json.dumps([{"event": json.dumps(dict(EVENT, eventName="Otro"))}]).encode()
    )[0]
    assert gee38._event_id(base) == gee38._event_id(dict(base))
    assert gee38._event_id(base) != gee38._event_id(other)


def test_event_id_separates_matches_of_the_same_league_slot():
    # El CMS rotula todos los partidos de la jornada igual («Liga MX, Apertura»
    # + misma fecha/hora): sin los equipos en el hash, se pisaban (214 → 149).
    def build(team_a: str, team_b: str) -> dict:
        ev = dict(EVENT, eventName="Liga MX, Apertura", date="17/10/2026", time="03:00:00")
        ev["teamAName"] = team_a
        ev["teamBName"] = team_b
        return gee38.parse_events(json.dumps([{"event": json.dumps(ev)}]).encode())[0]

    atlante = build("Atlante FC", "Pumas UNAM")
    tijuana = build("Club Tijuana", "Club Puebla")
    assert gee38._event_id(atlante) != gee38._event_id(tijuana)


def test_api_timestamp_reads_the_embedded_epoch():
    enc = base64.b64encode(b"Sky Sports1779198888933").decode()
    assert gee38._api_timestamp(f"channels/{enc}.txt") == 1779198888933
    assert gee38._api_timestamp("") == 0
    assert gee38._api_timestamp("https://x/y.m3u") == 0


# ------------------------------------------------------------------ sync

class _SyncConn:
    def __init__(self, pool):
        self.pool = pool

    async def execute(self, sql, *args):
        self.pool.executed.append((sql, args))
        return "OK"

    async def fetch(self, sql, *args):
        return self.pool.select_rows

    async def fetchval(self, sql, *args):
        return 0


class _SyncAcquire:
    def __init__(self, pool):
        self.pool = pool

    async def __aenter__(self):
        return _SyncConn(self.pool)

    async def __aexit__(self, *exc):
        return False


class SyncPool:
    def __init__(self, select_rows=()):
        self.executed = []
        self.select_rows = list(select_rows)

    def acquire(self):
        return _SyncAcquire(self)


@pytest.mark.anyio
async def test_sync_groups_keeps_the_freshest_duplicate(monkeypatch):
    old_api = "channels/" + base64.b64encode(b"Sky Sports1759911098003").decode() + ".txt"
    new_api = "channels/" + base64.b64encode(b"Sky Sports1779198888933").decode() + ".txt"
    sports = json.dumps([
        {"cat": json.dumps({"name": "Sky Sports", "type": "custom", "api": old_api, "logo": ""})},
        {"cat": json.dumps({"name": "Sky Sports", "type": "custom", "api": new_api, "logo": ""})},
    ]).encode()
    bundle_new = json.dumps([{"channel": json.dumps({
        "name": "Sky Sports", "links": "pro/S1.txt", "link_names": "['Sky Sports 1']",
    })}]).encode()
    pro_new = json.dumps([{"name": "Sky Sports 1", "link": "https://cdn/sky.m3u8",
                           "scheme": 0, "api": ""}]).encode()

    async def fake_fetch(path, timeout=25):
        if path == "sports.txt":
            return encrypt_for_test(sports, KEY)
        if path == old_api:
            raise ValueError("archivo retirado")
        if path == new_api:
            return encrypt_for_test(bundle_new, KEY)
        if path == "pro/S1.txt":
            return encrypt_for_test(pro_new, KEY)
        raise AssertionError(path)

    async def fake_probe_many(links):
        for link in links:
            link["alive"] = True

    monkeypatch.setattr(gee38, "_fetch_async", fake_fetch)
    monkeypatch.setattr(gee38, "_probe_many", fake_probe_many)

    pool = SyncPool()
    n = await gee38._sync_groups(pool, KEY, {})
    assert n == 1  # la entrada vieja con archivo retirado no cuenta
    inserts = [args for sql, args in pool.executed if "insert into gee38_catalog" in sql]
    assert len(inserts) == 1
    gid, name, sort, raw_payload = inserts[0]
    assert gid == "group:sky-sports" and name == "Sky Sports"
    payload = json.loads(raw_payload)
    assert payload["channels"][0]["name"] == "Sky Sports"
    assert payload["channels"][0]["links"][0]["url"] == "https://cdn/sky.m3u8"
    assert payload["alive"] == 1
    prunes = [sql for sql, _ in pool.executed if "delete from gee38_catalog" in sql]
    assert prunes and "kind = 'group'" in prunes[0]


@pytest.mark.anyio
async def test_sync_events_writes_stable_ids_with_links(monkeypatch):
    ev = dict(EVENT, visible="true")
    events_body = encrypt_for_test(json.dumps([{"event": json.dumps(ev)}]).encode(), KEY)
    pro_body = encrypt_for_test(json.dumps([
        {"name": "Azteca 7", "link": "https://cdn/azteca.m3u8", "scheme": 0, "api": ""},
    ]).encode(), KEY)

    async def fake_fetch(path, timeout=25):
        if path == "events.txt":
            return events_body
        if path == "pro/Ev123.txt":
            return pro_body
        raise AssertionError(path)

    async def fake_probe_many(links):
        for link in links:
            link["alive"] = None

    monkeypatch.setattr(gee38, "_fetch_async", fake_fetch)
    monkeypatch.setattr(gee38, "_probe_many", fake_probe_many)

    pool = SyncPool()
    n = await gee38._sync_events(pool, KEY, {})
    assert n == 1
    inserts = [args for sql, args in pool.executed if "insert into gee38_catalog" in sql]
    assert len(inserts) == 1
    eid, name, category, sort, raw_payload = inserts[0]
    expected = "event:" + __import__("hashlib").sha1(
        f"Football|{EVENT['eventName']}|{EVENT['teamAName']}|{EVENT['teamBName']}|30/06/2026|19:00:00".encode()
    ).hexdigest()[:14]
    assert eid == expected and category == "Football"
    payload = json.loads(raw_payload)
    assert payload["links"][0]["url"] == "https://cdn/azteca.m3u8"
    # El id estable viaja en el payload: es la clave de lista de la app (LazyColumn).
    assert payload["id"] == eid
    prunes = [sql for sql, _ in pool.executed if "delete from gee38_catalog" in sql]
    assert prunes and "kind = 'event'" in prunes[0]


@pytest.mark.anyio
async def test_sync_events_second_pass_probes_window_rows_missing_from_the_file(monkeypatch):
    # El CMS rota events.txt: una entrada puede desaparecer un rato. La fila guardada de
    # un partido de la ventana se sondea igual (badges frescos) y se re-fusiona con sus
    # hermanos del mismo cruce (la lista rica) — el caso real de Everton-Chelsea.
    async def fake_fetch(path, timeout=25):
        if path == "events.txt":
            return encrypt_for_test(json.dumps([]).encode(), KEY)
        raise AssertionError(path)

    async def fake_probe_many(links):
        for link in links:
            link["alive"] = True

    stale = {
        "id": "event:stale", "category": "Football", "event": "Premier League",
        "team_a": "Everton", "team_b": "Chelsea",
        "date_iso": date.today().isoformat(), "time": "11:30:00",
        "links": [{"name": "STREAM TV + HD", "url": "https://muerto", "link": "https://muerto", "alive": False}],
    }
    sibling = {
        "id": "event:sib", "category": "Football", "event": "Premier League",
        "team_a": "Chelsea", "team_b": "Everton", "date_iso": "2026-10-13", "time": "11:30:00",
        "links": [{"name": "TNT1UK SPORTS FHD", "url": "https://vivo", "link": "https://vivo", "alive": None}],
    }
    pool = SyncPool(select_rows=[
        {"id": "event:stale", "payload": stale},
        {"id": "event:sib", "payload": sibling},
    ])
    monkeypatch.setattr(gee38, "_fetch_async", fake_fetch)
    monkeypatch.setattr(gee38, "_probe_many", fake_probe_many)

    await gee38._sync_events(pool, KEY, {})
    updates = [args for sql, args in pool.executed if "update gee38_catalog" in sql]
    assert updates, "la pasada extra debe actualizar la fila de la ventana"
    payload = json.loads(updates[0][1])
    urls = [lk["url"] for lk in payload["links"]]
    assert "https://vivo" in urls and "https://muerto" in urls
    assert payload["alive"] == 2 and payload["total"] == 2


# ------------------------------------------------------------------ API

import app as api_app  # noqa: E402  (conftest ya ajustó sys.path)


class _FakePool:
    def __init__(self, rows=None, row=None):
        self.rows = rows or []
        self.row = row
        self.calls = []

    async def fetch(self, sql, *args):
        self.calls.append((sql, args))
        return self.rows

    async def fetchrow(self, sql, *args):
        self.calls.append((sql, args))
        return self.row

    async def execute(self, sql, *args):
        self.calls.append((sql, args))
        return "UPDATE 1"


class _FakeRequest:
    def __init__(self, body: bytes):
        self._body = body

    async def body(self):
        return self._body


@pytest.mark.anyio
async def test_gee38_sports_serves_groups_and_meta(monkeypatch):
    now = datetime.now(timezone.utc)
    fake = _FakePool(
        rows=[{"payload": '{"name": "TNT Sports", "channels": []}', "updated_at": now}],
        row={"v": '{"app_version": 11}', "updated_at": now},
    )
    monkeypatch.setattr(api_app, "pool", fake)
    out = await api_app.gee38_sports(x_api_key="test-key")
    assert out["groups"][0]["name"] == "TNT Sports"
    assert out["app"]["app_version"] == 11
    assert out["updatedAt"] == now.isoformat()


@pytest.mark.anyio
async def test_gee38_events_filters_day_window(monkeypatch):
    payload = json.dumps({
        "id": "event:a", "category": "Football", "event": "Liga MX, Apertura",
        "team_a": "Alfa", "team_b": "Beta",
        "date_iso": "2026-10-16", "time": "19:00:00", "links": [],
    })
    fake = _FakePool(rows=[{"id": "event:a", "payload": payload}])
    monkeypatch.setattr(api_app, "pool", fake)
    out = await api_app.gee38_events(category="Football", day="2026-10-16", days=3,
                                     x_api_key="test-key")
    catalog_calls = [call for call in fake.calls if "from gee38_catalog" in call[0]]
    sql, args = catalog_calls[-1]
    assert "category ilike $1" in sql
    assert "payload->>'date_iso' >= $2" in sql
    assert "payload->>'date_iso' < $3" in sql
    assert args == ("Football", "2026-10-16", "2026-10-19")
    assert out["events"][0]["event"] == "Liga MX, Apertura"
    assert out["events"][0]["id"] == "event:a"


@pytest.mark.anyio
async def test_gee38_events_backfills_id_and_dedupes(monkeypatch):
    # Payload viejo sin id (guardado antes del fix): se rellena con el id de la fila —
    # sin él la app caía al respaldo nombre|fecha|hora y dos partidos del mismo bloque
    # a la misma hora compartían clave de lista (crash del POCO en vivo).
    old = json.dumps({
        "category": "Basketball", "event": "NBA Preseason",
        "team_a": "Golden State Warriors", "team_b": "Portland Trail Blazers",
        "date_iso": "2026-10-16", "time": "20:00:00", "links": [],
    })
    fake = _FakePool(rows=[
        {"id": "event:aaa", "payload": old},
        {"id": "event:aaa", "payload": old},  # duplicado literal: una sola tarjeta
        {"id": "event:bbb", "payload": json.dumps({
            "category": "Basketball", "event": "NBA Preseason",
            "team_a": "Phoenix Suns", "team_b": "Utah Jazz",
            "date_iso": "2026-10-16", "time": "20:00:00", "links": [],
        })},
    ])
    monkeypatch.setattr(api_app, "pool", fake)
    out = await api_app.gee38_events(category=None, day="2026-10-16", days=0,
                                     x_api_key="test-key")
    ids = [e["id"] for e in out["events"]]
    assert ids == ["event:aaa", "event:bbb"]
    assert len(set(ids)) == len(ids)


@pytest.mark.anyio
async def test_gee38_events_merges_links_of_the_same_fixture(monkeypatch):
    # El caso real (Everton-Chelsea): la entrada del día traía 1 enlace muerto y su
    # hermana —mismo cruce, otro día— la lista completa con los vivos. La tarjeta del
    # calendario debe ofrecer TODAS las señales del cruce (lo que enseña su app),
    # con los vivos primero.
    day_event = json.dumps({
        "id": "event:day", "category": "Football", "event": "Premier League",
        "team_a": "Everton", "team_b": "Chelsea",
        "date_iso": "2026-10-17", "time": "11:30:00",  # UTC → 05:30 MX
        "links": [{"name": "STREAM TV + HD", "url": "https://muerto",
                   "link": "https://muerto", "alive": False}],
    })
    sib_event = json.dumps({
        "id": "event:sib", "category": "Football", "event": "Premier League",
        "team_a": "Chelsea", "team_b": "Everton",  # mismo cruce, orden invertido
        "date_iso": "2026-10-13", "time": "11:30:00",
        "links": [
            {"name": "TNT1UK SPORTS FHD", "url": "https://vivo1", "link": "https://vivo1", "alive": True},
            {"name": "FUBO SPORTS FHD", "url": "https://vivo2", "link": "https://vivo2", "alive": True},
        ],
    })
    fake = _FakePool(rows=[
        {"id": "event:day", "payload": day_event},
        {"id": "event:sib", "payload": sib_event},
    ])
    monkeypatch.setattr(api_app, "pool", fake)

    async def fake_window(events):  # la agenda ESPN de la ventana
        return [(
            {
                "id": "401878771", "league": {"id": "eng.1", "name": "Premier League", "logo": ""},
                "home": {"name": "Everton", "logo": "", "score": None},
                "away": {"name": "Chelsea", "logo": "", "score": None},
                "kickoff": "2026-10-17T11:30:00+00:00", "status": "pre", "detail": "",
                "broadcasts": ["TNT Sports"], "channels": [],
            },
            {"everton"}, {"chelsea"},
            datetime(2026, 10, 17, 11, 30, tzinfo=timezone.utc),
        )]

    monkeypatch.setattr(api_app, "_gee_window_matches", fake_window)
    out = await api_app.gee38_events(category=None, day="2026-10-17", days=0, x_api_key="test-key")
    cards = [e for e in out["events"] if str(e.get("id")) == "espn:401878771"]
    assert cards, "la tarjeta del calendario debe servirse"
    urls = [lk["url"] for lk in cards[0]["links"]]
    assert "https://vivo1" in urls and "https://vivo2" in urls and "https://muerto" in urls
    assert urls.index("https://muerto") > urls.index("https://vivo1"), "los vivos van primero"
    assert cards[0]["total"] == 3


def test_gee_pair_key_normalizes_order_and_accents():
    assert api_app._gee_pair_key("Everton", "Chelsea") == "chelsea|everton"
    assert api_app._gee_pair_key("Atlético Madrid", "Espanyol") == api_app._gee_pair_key("Espanyol", "atletico  madrid")
    assert api_app._gee_pair_key("Everton", "everton") == ""
    assert api_app._gee_pair_key("", "Chelsea") == ""


@pytest.mark.anyio
async def test_gee38_feedback_marks_link_and_persists(monkeypatch):
    payload = {
        "name": "TNT Sports",
        "channels": [{"name": "TNT Sports", "links": [
            {"name": "TNT SPORTS 1", "url": "https://x/y.mpd", "link": "https://x/y.mpd",
             "alive": True}]}],
        "alive": 1, "total": 1,
    }
    fake = _FakePool(rows=[{"id": "group:tnt-sports", "payload": json.dumps(payload)}])
    monkeypatch.setattr(api_app, "pool", fake)
    req = _FakeRequest(json.dumps({"url": "https://x/y.mpd", "alive": False}).encode())
    out = await api_app.gee38_feedback(request=req, x_api_key="test-key")
    assert out == {"ok": True, "updated": 1}
    sql, args = fake.calls[-1]
    assert "update gee38_catalog" in sql
    assert args[0] == "group:tnt-sports"
    stored = json.loads(args[1])
    link = stored["channels"][0]["links"][0]
    assert link["alive"] is False
    assert "reported_at" in link


@pytest.mark.anyio
async def test_gee38_feedback_unknown_url_is_noop(monkeypatch):
    fake = _FakePool(rows=[{"id": "group:x", "payload": '{"channels": []}'}])
    monkeypatch.setattr(api_app, "pool", fake)
    req = _FakeRequest(json.dumps({"url": "https://nadie", "alive": False}).encode())
    out = await api_app.gee38_feedback(request=req, x_api_key="test-key")
    assert out == {"ok": True, "updated": 0}


# ------------------------------------------------------------------ cruce ESPN

def _espn_match(home, away, home_score=None, away_score=None, status="pre",
                detail="", kickoff="2026-10-17T19:00:00+00:00", league="Liga MX"):
    return {
        "id": "m1",
        "league": {"id": "mex.1", "name": league, "logo": "https://l/lmx.png"},
        "home": {"id": "h", "name": home, "short": "", "logo": f"https://l/{home}.png", "score": home_score},
        "away": {"id": "a", "name": away, "short": "", "logo": f"https://l/{away}.png", "score": away_score},
        "kickoff": kickoff,
        "status": status,
        "detail": detail,
        "venue": "",
        "channels": [],
        "broadcasts": [],
    }


def _gee_event(**over):
    base = {
        "category": "Football", "event": "Liga MX, Apertura",
        "team_a": "Chivas", "team_b": "America",
        "date_iso": "2026-10-17", "time": "13:00:00",
        "flag_a": "https://sofascore/team/1", "flag_b": "https://sofascore/team/2",
        "links": [],
    }
    base.update(over)
    return base


def test_gee_find_match_matches_by_alias_and_time():
    event = _gee_event()
    entry = api_app._gee_match_entry(_espn_match("Guadalajara", "Club América",
                                                 kickoff="2026-10-17T19:00:00+00:00"))
    found = api_app._gee_find_match(event, [entry])
    assert found is not None
    assert found["home"]["name"] == "Guadalajara"


def test_gee_find_match_skips_junk_shared_names():
    # El CMS repite el nombre de la liga cuando no hay equipos reales.
    event = _gee_event(event="Liga Profesional de Fútbol",
                       team_a="Liga Profesional de Fútbol",
                       team_b="Liga Profesional de Fútbol")
    entry = api_app._gee_match_entry(_espn_match("Tigres", "Rayados"))
    assert api_app._gee_find_match(event, [entry]) is None


def test_gee_find_match_respects_time_window():
    event = _gee_event(date_iso="2026-10-17", time="13:00:00")
    # El partido es 3 días después: no es el mismo juego.
    entry = api_app._gee_match_entry(_espn_match("Chivas", "America",
                                                 kickoff="2026-10-20T19:00:00+00:00"))
    assert api_app._gee_find_match(event, [entry]) is None


def test_gee_enrich_event_adds_scores_logos_and_league():
    event = _gee_event()
    entry = api_app._gee_match_entry(_espn_match(
        "Guadalajara", "Club América", home_score=2, away_score=1,
        status="in", detail="67'", kickoff="2026-10-17T19:00:00+00:00", league="Liga MX"))
    out = api_app._gee_enrich_event(event, [entry])
    assert out["score"]["home"] == 2 and out["score"]["away"] == 1
    assert out["score"]["live"] is True and out["score"]["detail"] == "67'"
    assert out["league_label"] == "Liga MX"
    assert out["home_name"] == "Guadalajara"
    assert out["home_logo"].endswith("Guadalajara.png")


def test_gee_enrich_event_leaves_other_sports_untouched():
    event = _gee_event(category="Basketball", event="NBA", team_a="Lakers", team_b="Celtics")
    out = api_app._gee_enrich_event(event, [])
    assert "score" not in out and "league_label" not in out


def test_gee_enrich_event_keeps_sofascore_flags_when_unmatched():
    event = _gee_event(date_iso="2026-10-12", team_a="CD Tenerife", team_b="Girona FC")
    out = api_app._gee_enrich_event(event, [])
    assert out["flag_a"] == "https://sofascore/team/1"
    assert out.get("home_logo") is None


def test_gee_fix_event_times_moves_utc_to_mexico():
    event = _gee_event(date_iso="2026-10-17", time="03:00:00")  # 03:00 UTC
    api_app._gee_fix_event_times(event)
    assert event["date_iso"] == "2026-10-16"
    assert event["time"] == "21:00:00"


def test_gee_logo_normalizes_sofascore_and_cleartext():
    assert api_app._gee_logo("http://img.sofascore.com/api/v1/team/1/image") == \
        "https://img.sofascore.com/api/v1/team/1/image"
    assert api_app._gee_logo("http://api.sofascore.com/api/v1/unique-tournament/155/image/dark") == \
        "https://img.sofascore.com/api/v1/unique-tournament/155/image/dark"
    assert api_app._gee_logo("https://a.espncdn.com/x.png") == "https://a.espncdn.com/x.png"
    assert api_app._gee_logo("") == ""


def test_gee_apply_match_overrides_time_with_kickoff():
    event = _gee_event(date_iso="2026-10-17", time="01:00:00")
    match = _espn_match("Chivas", "Club América", kickoff="2026-10-17T01:00:00+00:00")
    api_app._gee_apply_match(event, match)
    # 01:00 UTC del 17 == 19:00 del 16 en México: manda el calendario.
    assert event["date_iso"] == "2026-10-16"
    assert event["time"] == "19:00:00"


def test_gee_match_card_shape():
    match = _espn_match("Tijuana", "Puebla", status="pre",
                        kickoff="2026-10-17T03:05:00+00:00", league="Liga MX")
    links = [{"name": "CANAL MX FHD", "url": "https://x/y.mpd", "link": "https://x/y.mpd", "alive": True}]
    card = api_app._gee_match_card(match, links)
    assert card["id"].startswith("espn:")
    assert card["category"] == "Football"
    assert card["event"] == "Liga MX" and card["league_label"] == "Liga MX"
    assert card["home_name"] == "Tijuana" and card["away_name"] == "Puebla"
    assert card["score"]["live"] is False
    assert card["alive"] == 1 and card["total"] == 1
    assert card["date_iso"] == "2026-10-16" and card["time"] == "21:05:00"


def test_gee_match_card_carries_the_guide():
    # La parrilla oficial viaja en la tarjeta: la app la usa como guía «se transmite en…».
    match = _espn_match("Necaxa", "Atlas")
    match["broadcasts"] = ["TUDN", "ViX Deportes", "TUDN"]
    match["channels"] = ["TUDN", "ViX Deportes"]
    card = api_app._gee_match_card(match, [])
    assert card["guide"] == ["TUDN", "ViX Deportes"]
    assert card["broadcasts"] == ["TUDN", "ViX Deportes"]
    assert card["league_id"] == "mex.1"


def _link(name: str) -> dict:
    return {"name": name, "url": "https://x/" + name, "link": "https://x/" + name, "alive": True}


def test_gee_broadcast_filter_orders_official_channels_first():
    # Necaxa-Atlas: la parrilla (TUDN/ViX) manda; CANAL MX queda al final.
    match = _espn_match("Necaxa", "Atlas")
    match["broadcasts"] = []
    match["channels"] = ["TUDN", "ViX Deportes"]
    links = [_link("CANAL MX FHD"), _link("FOX DEPORTES FHD"), _link("TUDN MEXICO FHD")]
    kept = api_app._gee_broadcast_filter(links, match)
    assert [link["name"] for link in kept] == ["TUDN MEXICO FHD", "CANAL MX FHD", "FOX DEPORTES FHD"]


def test_gee_broadcast_filter_accepts_sibling_feeds():
    match = _espn_match("Tijuana", "Puebla")
    match["broadcasts"] = ["FOX One", "TUDN USA"]
    match["channels"] = ["Fox Sports"]
    links = [_link("CANAL MX FHD"), _link("FOX DEPORTES FHD"), _link("TUDN MEXICO FHD")]
    kept = api_app._gee_broadcast_filter(links, match)
    # Las señales hermanas van arriba; la marca ajena queda al final (nunca se tira).
    assert [link["name"] for link in kept] == ["FOX DEPORTES FHD", "TUDN MEXICO FHD", "CANAL MX FHD"]


def test_gee_broadcast_filter_orders_neutrals_before_other_brands():
    match = _espn_match("Eintracht Frankfurt", "FC Cologne", league="Bundesliga")
    match["broadcasts"] = ["Fandango"]
    match["channels"] = []
    links = [_link("DAZN SPORTS FHD"), _link("HD QUALITY"), _link("ONE SOCCER HD")]
    kept = api_app._gee_broadcast_filter(links, match)
    # Sin coincidencia de parrilla: primero el agregador neutral, luego las marcas.
    assert [link["name"] for link in kept] == ["HD QUALITY", "DAZN SPORTS FHD", "ONE SOCCER HD"]


def test_gee_broadcast_filter_keeps_options_when_only_off_brand_links_exist():
    # Tijuana-Puebla real: el CMS solo trae CANAL MX — mejor una opción que nada.
    match = _espn_match("Tijuana", "Puebla")
    match["broadcasts"] = ["FOX One", "TUDN USA"]
    match["channels"] = ["Fox Sports"]
    links = [_link("CANAL MX FHD")]
    kept = api_app._gee_broadcast_filter(links, match)
    assert [link["name"] for link in kept] == ["CANAL MX FHD"]


def test_gee_broadcast_filter_links_caliente_with_fox_one():
    # Caliente TV es FOX One en México: cuando el enlace exista, va arriba.
    match = _espn_match("Tijuana", "Puebla")
    match["broadcasts"] = ["Caliente TV", "Fox Sports"]
    match["channels"] = []
    links = [_link("CANAL MX FHD"), _link("FOX ONE"), _link("FOX SPORTS 1 MX")]
    kept = api_app._gee_broadcast_filter(links, match)
    assert [link["name"] for link in kept[:2]] == ["FOX ONE", "FOX SPORTS 1 MX"]
    assert kept[-1]["name"] == "CANAL MX FHD"


def test_gee_league_filters_by_id_and_name():
    assert api_app._gee_league_allowed_match({"league": {"id": "mex.2"}})
    assert api_app._gee_league_allowed_match({"league": {"id": "uefa.champions"}})
    assert not api_app._gee_league_allowed_match({"league": {"id": "ita.2"}})
    assert api_app._gee_norm_league("Liga MX, Apertura") == "liga mx"
    assert api_app._gee_league_allowed_event({"event": "Liga MX, Apertura"})
    assert api_app._gee_league_allowed_event({"event": "Campeonato Brasileiro Série A"})
    assert api_app._gee_league_allowed_event({"event": "Liga Profesional de Fútbol"})
    assert not api_app._gee_league_allowed_event({"event": "LaLiga 2"})
    assert not api_app._gee_league_allowed_event({"event": "Ekstraklasa"})
    assert not api_app._gee_league_allowed_event({"event": "2. Bundesliga"})


@pytest.mark.anyio
async def test_gee38_events_limits_sports_and_leagues(monkeypatch):
    today = date.today().isoformat()

    def payload(event_id: str, category: str, name: str) -> str:
        return json.dumps({
            "id": event_id, "category": category, "event": name,
            "team_a": "Alfa", "team_b": "Beta",
            "date_iso": today, "time": "18:00:00", "links": [],
        })

    fake = _FakePool(rows=[
        {"payload": payload("event:rugby", "Rugby", "Six Nations")},
        {"payload": payload("event:laliga2", "Football", "LaLiga 2")},
        {"payload": payload("event:mx", "Football", "Liga MX, Apertura")},
        {"payload": payload("event:nba", "Basketball", "NBA")},
    ])
    monkeypatch.setattr(api_app, "pool", fake)

    async def fake_window(events):
        return []

    monkeypatch.setattr(api_app, "_gee_window_matches", fake_window)
    out = await api_app.gee38_events(category=None, day=None, days=0, x_api_key="test-key")
    ids = [event["id"] for event in out["events"]]
    assert "event:mx" in ids and "event:nba" in ids
    assert "event:rugby" not in ids and "event:laliga2" not in ids


@pytest.mark.anyio
async def test_gee38_events_merges_espn_agenda_with_gee_links(monkeypatch):
    today = date.today()
    football = json.dumps({
        "id": "event:mx1", "category": "Football", "event": "Liga MX, Apertura",
        "team_a": "Chivas", "team_b": "America",
        "date_iso": today.isoformat(), "time": "04:00:00",
        "links": [{"name": "CANAL MX FHD", "url": "https://x/mx.mpd",
                   "link": "https://x/mx.mpd", "alive": True}],
    })
    basket = json.dumps({
        "id": "event:nba", "category": "Basketball", "event": "NBA",
        "team_a": "Lakers", "team_b": "Celtics",
        "date_iso": today.isoformat(), "time": "20:00:00", "links": [],
    })
    fake = _FakePool(rows=[{"payload": football}, {"payload": basket}])
    monkeypatch.setattr(api_app, "pool", fake)
    espn = _espn_match("Guadalajara", "Club América", home_score=1, away_score=0,
                       status="in", detail="55'",
                       kickoff=f"{today.isoformat()}T10:00:00+00:00", league="Liga MX")
    entries = [api_app._gee_match_entry(espn)]

    async def fake_window(events):
        return entries

    monkeypatch.setattr(api_app, "_gee_window_matches", fake_window)
    out = await api_app.gee38_events(category=None, day=None, days=0, x_api_key="test-key")
    events = out["events"]
    ids = [event["id"] for event in events]
    # El partido del calendario sale como tarjeta única y con los enlaces cruzados.
    card = next(event for event in events if event["id"] == "espn:m1")
    assert card["home_name"] == "Guadalajara" and card["score"]["live"] is True
    assert card["alive"] == 1 and card["links"][0]["url"] == "https://x/mx.mpd"
    # El evento de fútbol quedó absorbido por la tarjeta; el básquet pasa tal cual.
    assert "event:mx1" not in ids
    assert "event:nba" in ids
