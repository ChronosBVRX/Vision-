"""Marcadores de salto (intro / recap / créditos) de las bases globales comunitarias.

La app no adivina los minutos: pregunta por (fila de temporada, episodio) y estas
bases —las mismas que alimentan los plugins de Jellyfin / Emby / Kodi / Infuse—
devuelven los milisegundos donde empieza y termina cada segmento.

Aquí vive lo puro y testeable: normalización de referencias, parseo de las tres
fuentes, saneo (los datos comunitarios traen entradas rotas: se vieron «intros» a
las 19:51 en capítulos de 24 min) y fusión. El acceso a red es `fetch_global()`;
el API lo corre en un thread aparte y cachea el resultado en Postgres.

Fuentes:
- TheIntroDB (theintrodb.org): por `tmdb_id`; listas de segmentos por tipo.
- IntroDB.app: por `imdb_id`; un objeto por segmento (`outro` = créditos).
- AniSkip: por `mal_id` (MyAnimeList); openings/endings/recaps de anime.
  El `mal_id` se resuelve con la tabla `anime_mal_map` (dataset Fribb anime-lists:
  `themoviedb_id.tv` + `season.tmdb` → `mal_id`), que el worker refresca semanal.
"""

import json
import re
import urllib.parse
import urllib.request

TIDB_URL = "https://api.theintrodb.org/v2/media?tmdb_id={tmdb}&season={season}&episode={episode}"
IDB_URL = "https://api.introdb.app/segments?imdb_id={imdb}&season={season}&episode={episode}"
# AniSkip: tiempos de opening/ending/recap por anime, con el MyAnimeList id y el número de
# capítulo dentro de ESA entrada de MAL (cada temporada es su propia entrada).
# OJO: la v2 exige `episodeLength` (segundos, aproximado basta): sin él responde 400.
ANISKIP_URL = "https://api.aniskip.com/v2/skip-times/{mal}/{episode}?types=op&types=ed&types=recap&episodeLength={length}"
# Duración supuesta cuando la ficha no trae (anime estándar de 24 min).
ANISKIP_DEFAULT_LENGTH_S = 1440
# Dataset Fribb anime-lists (mini basta: mal_id + themoviedb_id.tv + season.tmdb).
ANIME_LIST_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-mini.json"

KINDS = ("intro", "recap", "credits")

# La escena post-créditos no es un salto (es un aviso): viaja aparte para no mezclarse
# con la tarjeta de «siguiente capítulo». La traen IntroDB.app (`post-credits`) y
# TheIntroDB (`post_credits`).
POST_KIND = "postCredits"

# Cotas de cordura: un intro legítimo vive en los primeros minutos (hasta las novelas
# con cold open largo); un recap jamás pasa de 5 min; los créditos nunca empiezan en
# la primera mitad del capítulo.
INTRO_FALLBACK_MAX_MS = 12 * 60_000
INTRO_FRACTION = 0.5
RECAP_MAX_MS = 5 * 60_000
CREDITS_FALLBACK_MIN_MS = 5 * 60_000
CREDITS_FRACTION = 0.5
SEGMENT_MAX_MS = 6 * 3600_000

SOURCE_TIDB = "theintrodb"
SOURCE_IDB = "introdb"
SOURCE_ANISKIP = "aniskip"

# AniSkip no expone votos: sus tiempos viven votados en el sitio, así que entran con la
# prioridad más alta para anime (por encima de las bases generalistas en cualquier empate).
ANISKIP_VOTES = 5

_USER_AGENT = "VisionPlusIndex/1.0"


# ---------------------------------------------------------------- referencias

def normalize_ref(raw) -> str:
    """La referencia de la app a la clave canónica de la tabla (`xtv:<cid>`)."""
    ref = str(raw or "").strip()[:80]
    if not ref:
        return ""
    if ref.startswith(("xtv:", "magis:", "magisvod:")):
        return ref
    return f"xtv:{ref}"


def ref_variants(ref: str) -> list:
    """Variantes con las que la misma ficha puede vivir en `titles` (xtv/magis)."""
    cid = ref.split(":", 1)[1] if ":" in ref else ref
    return list(dict.fromkeys(v for v in (ref, f"xtv:{cid}", f"magis:{cid}") if v))


_SEASON_TAIL = re.compile(r"(?:^|\s)t(?:em|emp|emporada)?\s*[.:\-]?\s*(\d{1,2})\s*$", re.IGNORECASE)
_SEASON_TRAILING = re.compile(r"\s(\d{1,2})\s*$")


def season_from_title(title: str, kind: str = "") -> int:
    """Número de temporada de la fila («… T32», «… Temporada 3»); 0 si no hay marca."""
    text = (title or "").strip()
    match = _SEASON_TAIL.search(text) or _SEASON_TRAILING.search(text)
    if not match:
        return 0
    number = int(match.group(1))
    return number if 1 <= number <= 60 else 0


def ms_or_none(value):
    """Milisegundos válidos (0..6 h) o None; corta números rotos o gigantes."""
    try:
        number = int(value)
    except (TypeError, ValueError):
        return None
    if number < 0 or number > SEGMENT_MAX_MS:
        return None
    return number


# ---------------------------------------------------------------- parseo

def _segment(start, end, src, votes, conf=0.0):
    seg = {
        "s": ms_or_none(start),
        "e": ms_or_none(end),
        "src": src,
        "votes": int(votes or 0),
        "conf": float(conf or 0.0),
    }
    if seg["s"] is None and seg["e"] is None:
        return None
    return seg


def parse_tidb(payload) -> dict:
    """TheIntroDB: listas de segmentos por tipo; se elige el más votado."""
    out = {}
    if not isinstance(payload, dict):
        return out
    for kind in KINDS:
        best = None
        for item in payload.get(kind) or []:
            if not isinstance(item, dict):
                continue
            seg = _segment(
                item.get("start_ms"),
                item.get("end_ms"),
                SOURCE_TIDB,
                item.get("submission_count"),
                item.get("confidence"),
            )
            if seg is None:
                continue
            if best is None or seg["votes"] > best["votes"]:
                best = seg
        if best is not None:
            out[kind] = best
    best_pc = None
    for item in payload.get("post_credits") or []:
        if not isinstance(item, dict):
            continue
        seg = _segment(
            item.get("start_ms"), item.get("end_ms"), SOURCE_TIDB,
            item.get("submission_count"), item.get("confidence"),
        )
        if seg is None:
            continue
        if best_pc is None or seg["votes"] > best_pc["votes"]:
            best_pc = seg
    if best_pc is not None:
        out[POST_KIND] = best_pc
    return out


def parse_idb(payload) -> dict:
    """IntroDB.app: un objeto por segmento; «outro» son los créditos."""
    out = {}
    if not isinstance(payload, dict):
        return out
    for key, kind in (("intro", "intro"), ("recap", "recap"), ("outro", "credits")):
        item = payload.get(key)
        if not isinstance(item, dict):
            continue
        seg = _segment(
            item.get("start_ms"),
            item.get("end_ms"),
            SOURCE_IDB,
            item.get("submission_count"),
            item.get("confidence"),
        )
        if seg is not None:
            out[kind] = seg
    for key in ("post-credits", "post_credits"):
        item = payload.get(key)
        if isinstance(item, dict):
            seg = _segment(
                item.get("start_ms"), item.get("end_ms"), SOURCE_IDB,
                item.get("submission_count"), item.get("confidence"),
            )
            if seg is not None:
                out[POST_KIND] = seg
                break
    return out


_ANISKIP_KIND = {"op": "intro", "ed": "credits", "recap": "recap"}


def _sec_to_ms(value):
    """Segundos (float, como los da AniSkip) a ms enteros; None si no es número."""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number < 0 or number * 1000 > SEGMENT_MAX_MS:
        return None
    return int(number * 1000)


def parse_aniskip(payload) -> dict:
    """AniSkip: `results` con `skipType` (op/ed/recap) e intervalo en segundos float."""
    out = {}
    if not isinstance(payload, dict):
        return out
    results = payload.get("results")
    if not isinstance(results, list):
        return out
    for item in results:
        if not isinstance(item, dict):
            continue
        kind = _ANISKIP_KIND.get(str(item.get("skipType") or "").strip().lower())
        if kind is None or kind in out:
            continue
        interval = item.get("interval") or {}
        seg = _segment(
            _sec_to_ms(interval.get("startTime")),
            _sec_to_ms(interval.get("endTime")),
            SOURCE_ANISKIP,
            ANISKIP_VOTES,
            1.0,
        )
        if seg is not None:
            out[kind] = seg
    return out


def parse_anime_list(entries) -> dict:
    """Dataset Fribb anime-lists → {(tmdb_id, tmdb_season): mal_id}.

    Cada entrada trae `mal_id`, `themoviedb_id: {tv: …}` y `season: {tmdb: N}`.
    Solo sirven las que tienen los tres datos; el resto se ignora.
    """
    out = {}
    if not isinstance(entries, list):
        return out
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        try:
            mal_id = int(entry.get("mal_id") or 0)
        except (TypeError, ValueError):
            continue
        if mal_id <= 0:
            continue
        tmdb_block = entry.get("themoviedb_id") or {}
        season_block = entry.get("season") or {}
        try:
            tmdb_id = int(tmdb_block.get("tv") or 0)
            season = int(season_block.get("tmdb") or 0)
        except (TypeError, ValueError, AttributeError):
            continue
        if tmdb_id <= 0 or season <= 0:
            continue
        out.setdefault((tmdb_id, season), mal_id)
    return out


# ---------------------------------------------------------------- saneo y fusión

def sanitize(markers, duration_min: int = 0) -> dict:
    """Descarta segmentos imposibles antes de guardarlos o servirlos."""
    if not markers:
        return {}
    duration_ms = max(0, int(duration_min or 0)) * 60_000
    out = {}

    intro = markers.get("intro")
    if intro:
        start, end = intro.get("s"), intro.get("e")
        cap = INTRO_FALLBACK_MAX_MS
        if duration_ms > 0:
            cap = min(cap, int(duration_ms * INTRO_FRACTION))
        if end is not None and end > 0 and end <= cap and (start is None or start < end):
            out["intro"] = intro

    recap = markers.get("recap")
    if recap:
        start, end = recap.get("s"), recap.get("e")
        edge = end if end is not None else start
        if edge is not None and 0 < edge <= RECAP_MAX_MS and (end is None or start is None or start < end):
            out["recap"] = recap

    credits = markers.get("credits")
    if credits:
        start, end = credits.get("s"), credits.get("e")
        if start is not None:
            low = CREDITS_FALLBACK_MIN_MS
            if duration_ms > 0:
                low = max(low, int(duration_ms * CREDITS_FRACTION))
            if start >= low and (duration_ms <= 0 or start <= duration_ms + 60_000):
                if end is None or end > start:
                    out["credits"] = credits

    post = markers.get(POST_KIND)
    if post:
        start, end = post.get("s"), post.get("e")
        if start is not None and start > 0:
            low = CREDITS_FALLBACK_MIN_MS
            if duration_ms > 0:
                low = max(low, int(duration_ms * CREDITS_FRACTION))
            if start >= low and (end is None or end > start):
                out[POST_KIND] = post

    return out


def merge(tidb: dict, idb: dict, aniskip: dict | None = None) -> dict:
    """Fusión por tipo: gana la fuente más votada; en empate manda AniSkip,
    luego IntroDB.app (la de mejor cobertura general) y al final TheIntroDB.

    AniSkip entra con votos fijos (tiempos ya votados en su comunidad): así un
    opening de anime le gana a un dato flojo generalista, pero un dato muy votado
    de IntroDB.app (p. ej. 5+ votos) sigue mandando.
    """
    sources = [tidb or {}, idb or {}, aniskip or {}]
    # Prioridad de desempate por fuente (mayor = gana el empate).
    priority = {SOURCE_TIDB: 0, SOURCE_IDB: 1, SOURCE_ANISKIP: 2}
    out = {}
    for kind in list(KINDS) + [POST_KIND]:
        best = None
        best_key = (-1, -1)
        for src in sources:
            seg = src.get(kind)
            if seg is None:
                continue
            key = (int(seg.get("votes") or 0), priority.get(seg.get("src"), 0))
            if best is None or key > best_key:
                best, best_key = seg, key
        if best is not None:
            out[kind] = best
    return out


def overlay(base, extra) -> dict:
    """`extra` (marcas del usuario) pisa a `base` (global) por tipo; None borra."""
    out = dict(base or {})
    for kind, seg in (extra or {}).items():
        if kind not in KINDS and kind != POST_KIND:
            continue
        if seg is None:
            out.pop(kind, None)
        else:
            out[kind] = seg
    return out


def payload(ref: str, episode: int, season: int, segments: dict) -> dict:
    """Forma que consume la app: segmentos con `s`/`e` y la fuente que los dio."""
    out = {"ref": ref, "episode": episode, "season": season, "found": bool(segments)}
    for kind in list(KINDS) + [POST_KIND]:
        seg = (segments or {}).get(kind)
        if seg:
            out[kind] = {"s": seg.get("s"), "e": seg.get("e"), "src": seg.get("src", "")}
    return out


def segments_of(value) -> dict:
    """Los `segments` tal como salen de Postgres: asyncpg entrega el jsonb como texto.

    Tolera dict (llamadas internas), str JSON (lecturas de la base) y basura.
    """
    if isinstance(value, dict):
        return dict(value)
    if isinstance(value, str) and value.strip():
        try:
            data = json.loads(value)
        except Exception:  # noqa: BLE001
            return {}
        return dict(data) if isinstance(data, dict) else {}
    return {}


# ---------------------------------------------------------------- red

def _get_json(url: str, timeout: float):
    request = urllib.request.Request(url, headers={"User-Agent": _USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8", "replace"))


def fetch_global(tmdb_id=None, imdb_id: str = "", season: int = 1, episode: int = 1, mal_id=None, episode_length: int = 0, timeout: float = 8.0) -> dict:
    """Consulta las tres bases y devuelve los marcadores fusionados ({} si no hay nada).

    Cada base es independiente: si una falla o no conoce el episodio, las demás mandan.
    `mal_id` solo se usa para anime (AniSkip); sin él no se toca esa red.
    `episode_length` son segundos aproximados (de la ficha; 1440 si no se sabe):
    AniSkip lo exige y lo usa para validar el capítulo.
    """
    season = max(1, int(season or 1))
    episode = max(1, int(episode or 1))
    try:
        length = int(episode_length or 0)
    except (TypeError, ValueError):
        length = 0
    if length <= 0:
        length = ANISKIP_DEFAULT_LENGTH_S
    tidb, idb, askip = {}, {}, {}
    if tmdb_id:
        try:
            url = TIDB_URL.format(tmdb=int(tmdb_id), season=season, episode=episode)
            tidb = parse_tidb(_get_json(url, timeout))
        except Exception:  # noqa: BLE001 — una fuente caída no tumba a las demás
            tidb = {}
    if imdb_id:
        try:
            imdb = urllib.parse.quote(str(imdb_id).strip(), safe="")
            url = IDB_URL.format(imdb=imdb, season=season, episode=episode)
            idb = parse_idb(_get_json(url, timeout))
        except Exception:  # noqa: BLE001
            idb = {}
    try:
        mal = int(mal_id or 0)
    except (TypeError, ValueError):
        mal = 0
    if mal > 0:
        try:
            url = ANISKIP_URL.format(mal=mal, episode=episode, length=length)
            askip = parse_aniskip(_get_json(url, timeout))
        except Exception:  # noqa: BLE001
            askip = {}
    return merge(tidb, idb, askip)
