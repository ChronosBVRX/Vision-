"""Enriquecimiento por título con TMDB (elegido por el usuario).

Por cada título sin enriquecer: búsqueda multi-idioma (es-MX) por nombre y año,
selección del mejor candidato con confianza, y ficha con `imdb_id`, rating,
año y géneros en español. Se procesa por lotes (ENRICH_BATCH por ciclo) y a
ritmo lento para respetar los límites de la API. Sin TMDB_API_KEY no hace nada.

IMDb directo no tiene API libre: TMDB da `imdb_id` + rating, que es lo que se
indexa; los desacuerdos de match pueden corregirse luego en `enrichment`.
"""

import asyncio
import json
import logging
import os
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request

import grouping
import search_text

log = logging.getLogger("tmdb")

API = "https://api.themoviedb.org/3"
KEY = os.environ.get("TMDB_API_KEY", "").strip()
BATCH = int(os.environ.get("ENRICH_BATCH", "300"))
DELAY = float(os.environ.get("ENRICH_DELAY_SECONDS", "0.25"))
# Pausa por llamada HTTP (no solo por título): respeta el límite ~40 req/10 s.
REQUEST_GAP = float(os.environ.get("ENRICH_REQUEST_GAP_SECONDS", "0.3"))
# Re-match de los marcados «sin match» con la búsqueda corregida (marcas de temporada).
REMATCH_BATCH = int(os.environ.get("TMDB_REMATCH_BATCH", "2500"))
REMATCH_COOLDOWN_DAYS = int(os.environ.get("TMDB_REMATCH_COOLDOWN_DAYS", "14"))
# Versión del emparejador: al mejorar el buscador se sube esta etiqueta y TODOS los «sin
# match» reciben una pasada fresca (una vez), sin esperar el cooldown. Así una mejora
# nunca vuelve a quedar bloqueada por el reintento anterior (lección del backlog de series:
# el fix del buscador llegó justo después de una pasada que marcó 16k y quedó congelado).
ALGO_VERSION = "2"
# Backfill de popularidad/votos para matches viejos (los que llegaron antes de las columnas).
BACKFILL_BATCH = int(os.environ.get("TMDB_BACKFILL_BATCH", "3000"))
# Backfill de NOMBRES (es-MX/original/alternativos) para matches viejos: una llamada por
# título ya conocido (sin búsqueda) y con su propio ritmo, más rápido que el gota a gota
# del enriquecimiento pero lejos del límite de TMDB (una pasada de estreno puede subirse).
ALIAS_BATCH = int(os.environ.get("TMDB_ALIAS_BATCH", "1500"))
ALIAS_GAP = float(os.environ.get("TMDB_ALIAS_GAP_SECONDS", "0.08"))
# El texto de búsqueda se rehace por trozos mientras se guardan nombres: si el proceso
# muere a media pasada (reinicio del contenedor), lo ya guardado queda fresco igual.
# Antes se rehacía todo al final y una muerte a mitad dejó ~3.4k textos viejos ya
# marcados como hechos — «buscando a nemo» dejó de encontrar «Finding Nemo» (2026-10-18).
ALIAS_REBUILD_CHUNK = 500
# Traducciones por idioma de TMDB (`/translations`) para los matches ya hechos: una
# llamada por título, con su propio marcador (`translations_at`) y presupuesto. Es la
# otra mitad de los nombres («Die Simpsons», «Блич», «死神»): los alternativos por
# región no siempre las traen.
TRANSLATIONS_BATCH = int(os.environ.get("TMDB_TRANSLATIONS_BATCH", "1500"))
TRANSLATIONS_GAP = float(os.environ.get("TMDB_TRANSLATIONS_GAP", "0.12"))
# Orden de las traducciones en `search_text`: primero los idiomas que más se buscan
# aquí (es-MX/es, inglés, portugués, francés, alemán…), luego el resto.
TRANSLATION_PRIORITY = {
    "es-mx": 0, "es-es": 1, "es-419": 2, "es-ar": 3, "es": 4,
    "en-us": 5, "en-gb": 6, "en": 7,
    "pt-br": 8, "pt-pt": 9, "pt": 10,
    "fr-fr": 11, "fr": 12, "de-de": 13, "de": 14, "it-it": 15, "it": 16,
    "ja-jp": 17, "ja": 18, "ko-kr": 19, "ko": 20,
    "zh-cn": 21, "zh-tw": 22, "zh": 23, "ru-ru": 24, "ru": 25,
    "hi-in": 26, "hi": 27, "ar-sa": 28, "ar": 29, "tr-tr": 30, "tr": 31,
    "pl-pl": 32, "pl": 33, "nl-nl": 34, "nl": 35, "sv-se": 36, "da-dk": 37,
    "fi-fi": 38, "no-no": 39, "cs-cz": 40, "hu-hu": 41, "ro-ro": 42,
    "el-gr": 43, "he-il": 44, "th-th": 45, "vi-vn": 46, "id-id": 47, "uk-ua": 48,
}
TRANSLATIONS_LIMIT = 40


def _norm(text: str) -> str:
    folded = unicodedata.normalize("NFD", (text or "").lower())
    folded = "".join(c for c in folded if unicodedata.category(c) != "Mn")
    return "".join(c if c.isalnum() or c == " " else " " for c in folded).strip()


def clean_query(title: str, kind: str) -> str:
    """Título base para buscar en TMDB: sin la marca de temporada pegada («Show T1»,
    «Serie Temp.3») ni calificadores de doblaje/calidad (« - Latino», «HD»).

    El catálogo del portal trae cada temporada como título propio y la búsqueda con
    el sufijo fallaba el 96% de las series («Eagleheart T1» no existe en TMDB).
    """
    raw = (title or "").strip()
    base = raw
    if kind in ("series", "anime", "kids"):
        split = grouping.split_season(base, kind)
        if split:
            base = split[0]
    base = grouping.strip_qualifier(base).strip()
    return base or raw


def _search_specs(kind: str) -> list[tuple[str, str]]:
    """Búsquedas TMDB por tipo: tv para series, movie para películas y AMBOS para
    anime/kids (el portal mezcla películas y series en esas secciones)."""
    if kind == "movie":
        return [("/search/movie", "year")]
    if kind == "series":
        return [("/search/tv", "first_air_date_year")]
    return [("/search/tv", "first_air_date_year"), ("/search/movie", "year")]


def _get(path: str, gap: float | None = None, **params) -> dict:
    params["api_key"] = KEY
    url = f"{API}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "VisionPlusIndex/1.0"})
    delay = REQUEST_GAP if gap is None else gap
    if delay:
        time.sleep(delay)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def parse_alt_titles(payload) -> list[str]:
    """Títulos alternativos de TMDB: películas usan `titles`, series `results`.

    Cada entrada trae {title|name, iso_3166_1, type}. Se limpian, deduplican sin
    distinguir caja y se cortan a 40: los nombres regionales útiles («Vaiana» para
    «Moana») suelen venir entre los primeros; el resto es cola de transliteraciones.
    """
    if not isinstance(payload, dict):
        return []
    items = payload.get("titles")
    if not isinstance(items, list):
        items = payload.get("results")
    out: list[str] = []
    seen: set[str] = set()
    for item in items if isinstance(items, list) else []:
        if not isinstance(item, dict):
            continue
        name = (item.get("title") or item.get("name") or "").strip()
        key = name.lower()
        if not name or key in seen:
            continue
        seen.add(key)
        out.append(name)
        if len(out) >= 40:
            break
    return out


def parse_translations(payload, limit: int = TRANSLATIONS_LIMIT) -> list[str]:
    """Nombres por IDIOMA de TMDB (`/translations`) → lista priorizada y sin repetir.

    El payload trae {iso_639_1, iso_3166_1, data:{title|name}}. Se ordena por los
    idiomas que más se buscan en Vision+ (es-MX/es, inglés, portugués, francés,
    alemán…) y se corta a [limit]: el buscador se queda con los nombres útiles y no
    con la cola de transliteraciones exóticas. La deduplicación ignora caja/acentos
    (mismo criterio que `_norm`).
    """
    if not isinstance(payload, dict):
        return []
    items = payload.get("translations")
    if not isinstance(items, list):
        return []
    ranked: list[tuple[int, int, str]] = []
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            continue
        data = item.get("data")
        if not isinstance(data, dict):
            continue
        name = (data.get("title") or data.get("name") or "").strip()
        if not name:
            continue
        language = str(item.get("iso_639_1") or "").strip().lower()
        region = str(item.get("iso_3166_1") or "").strip().lower()
        key = f"{language}-{region}" if region else language
        priority = TRANSLATION_PRIORITY.get(key, TRANSLATION_PRIORITY.get(language, 999))
        ranked.append((priority, index, name))
    ranked.sort(key=lambda entry: (entry[0], entry[1]))
    out: list[str] = []
    seen: set[str] = set()
    for _, _, name in ranked:
        key = _norm(name)
        if not key or key in seen:
            continue
        seen.add(key)
        out.append(name)
        if len(out) >= limit:
            break
    return out


def current_week() -> str:
    """Etiqueta ISO de la semana (2026-W42): la tendencia se refresca por semana."""
    import datetime

    year, week, _ = datetime.date.today().isocalendar()
    return f"{year}-W{week:02d}"


def parse_trending(payload, media: str) -> list:
    """Títulos de `/trending/{movie,tv}/week` → [(tmdb_id, puesto)] (puro, testeable)."""
    out = []
    results = (payload or {}).get("results") if isinstance(payload, dict) else None
    if not isinstance(results, list):
        return out
    for rank, item in enumerate(results, start=1):
        if not isinstance(item, dict):
            continue
        try:
            tmdb_id = int(item.get("id") or 0)
        except (TypeError, ValueError):
            continue
        if tmdb_id > 0:
            out.append((tmdb_id, rank))
    return out


async def sync_trending(pool) -> int:
    """Baja lo más visto de la semana (cine + series) y lo deja en `weekly_trending`.

    Dos llamadas por semana (una por tipo): si la semana ya está guardada no hace nada.
    """
    if not KEY:
        return 0
    week = current_week()
    async with pool.acquire() as conn:
        have = await conn.fetchval("select count(*) from weekly_trending where week = $1", week)
        if have:
            return 0
    rows = []
    try:
        for media in ("movie", "tv"):
            payload = await asyncio.to_thread(_get, f"/trending/{media}/week", language="es-MX")
            rows += [(tmdb_id, media, rank) for tmdb_id, rank in parse_trending(payload, media)]
    except Exception as exc:  # noqa: BLE001 - sin red no se tumba el ciclo
        log.warning("TMDB trending falló: %s", exc)
        return 0
    if not rows:
        return 0
    async with pool.acquire() as conn:
        await conn.executemany(
            """
            insert into weekly_trending (week, tmdb_id, media, rank, updated_at)
            values ($1, $2, $3, $4, now())
            on conflict (week, tmdb_id) do update set
                media = excluded.media, rank = excluded.rank, updated_at = now()
            """,
            [(week, tmdb_id, media, rank) for tmdb_id, media, rank in rows],
        )
        await conn.execute("delete from weekly_trending where week <> $1", week)
    log.info("TMDB trending %s: %s títulos", week, len(rows))
    return len(rows)


def _confidence(query: str, candidate: dict, kind: str, year: int | None) -> float:
    title = candidate.get("title") or candidate.get("name") or ""
    original = candidate.get("original_title") or candidate.get("original_name") or ""
    target = _norm(query)
    names = {_norm(title), _norm(original)}
    score = 0.0
    if target in names:
        score = 1.0
    elif any(target and (target in n or n in target) for n in names if n):
        score = 0.8
    else:
        score = 0.5
    date = candidate.get("release_date") or candidate.get("first_air_date") or ""
    if year and len(date) >= 4:
        try:
            if abs(int(date[:4]) - year) <= 1:
                score += 0.1
            else:
                score -= 0.15
        except ValueError:
            pass
    return max(0.0, min(1.0, score))


def _match(kind: str, title: str, original: str | None, year: int | None) -> tuple[dict | None, bool]:
    """El mejor candidato TMDB (con sinopsis es-MX) o (None, hubo_error_de_red).

    El error se distingue del "no hay match": con error no se marca negativo para
    reintentarlo en el próximo ciclo; sin match se marca 0.0 y no se reintenta.
    """
    query = clean_query(title, kind)
    clean_original = clean_query(original or "", kind) if original else ""
    variants = [query]
    if clean_original and _norm(clean_original) != _norm(query):
        variants.append(clean_original)

    candidates: list[dict] = []
    had_error = False
    for path, year_key in _search_specs(kind):
        media = "tv" if path.endswith("/tv") else "movie"
        found: list[dict] = []
        for alt in variants:
            params = {"language": "es-MX", "include_adult": "false", "query": alt}
            # El año de una temporada no es el año de estreno de la serie: en tv
            # estorba (filtraría por primera emisión y no por la temporada), así
            # que solo se usa como criterio de confianza, no en la búsqueda.
            if year and media == "movie":
                params[year_key] = year
            try:
                results = _get(path, **params).get("results") or []
            except Exception:  # noqa: BLE001 - se reintenta en el próximo ciclo
                had_error = True
                results = []
            found.extend(results)
            if found:
                break
        for result in found:
            result["_media"] = media
        candidates.extend(found)
    if not candidates:
        return None, had_error

    def _rank(c: dict) -> tuple[float, int]:
        return (_confidence(query, c, kind, year), int(c.get("vote_count") or 0))

    best = max(candidates, key=_rank)
    confidence = _confidence(query, best, kind, year)
    if confidence < 0.5:
        return None, False
    tmdb_id = best.get("id")
    if not tmdb_id:
        return None, False
    media = best.get("_media") or ("tv" if kind == "series" else "movie")
    detail_path = f"/tv/{tmdb_id}" if media == "tv" else f"/movie/{tmdb_id}"
    try:
        # alternative_titles: los nombres regionales/alternativos viajan en la MISMA
        # llamada que la ficha: sin ellos, «vaiana» no encuentra «Moana».
        details = _get(
            detail_path, language="es-MX",
            append_to_response="external_ids,alternative_titles,translations",
        )
    except Exception:  # noqa: BLE001
        return None, True
    genres = [g.get("name") for g in details.get("genres") or [] if g.get("name")]
    date = details.get("release_date") or details.get("first_air_date") or ""
    detail_year = int(date[:4]) if len(date) >= 4 else year
    imdb_id = (details.get("external_ids") or {}).get("imdb_id")
    rating = details.get("vote_average")
    popularity, votes = _impact_of(details)
    return {
        "tmdb_id": int(tmdb_id),
        "imdb_id": imdb_id,
        "confidence": round(confidence, 3),
        "rating": round(float(rating), 1) if rating else None,
        "year": detail_year,
        "genres": genres,
        "overview": (details.get("overview") or "").strip(),
        "vote_count": votes,
        "popularity": popularity,
        # Nombres para buscar («buscando a nemo» → «Finding Nemo»).
        "original_title": (details.get("original_title") or details.get("original_name") or "").strip(),
        "localized_title": (details.get("title") or details.get("name") or "").strip(),
        "alt_titles": parse_alt_titles(details.get("alternative_titles")),
        "translated_titles": parse_translations(details.get("translations")),
    }, False


def _impact_of(details: dict) -> tuple[float, int]:
    """(popularidad, votos) de una ficha TMDB; 0 si no vienen (registros viejos)."""
    try:
        votes = int(details.get("vote_count") or 0)
    except (TypeError, ValueError):
        votes = 0
    try:
        popularity = round(float(details.get("popularity") or 0.0), 3)
    except (TypeError, ValueError):
        popularity = 0.0
    return popularity, votes


def _media_for(kind: str) -> str:
    return "tv" if kind == "series" else "movie"


def _details_for(tmdb_id: int, media: str) -> dict:
    path = f"/tv/{tmdb_id}" if media == "tv" else f"/movie/{tmdb_id}"
    return _get(path, language="es-MX")


async def _store_match(pool, title_id: str, match: dict) -> None:
    async with pool.acquire() as conn:
        await conn.execute(
            """
            insert into enrichment (title_id, imdb_id, tmdb_id, match_confidence, rating, year,
                                    genres, overview, popularity, vote_count, match_algo,
                                    original_title, localized_title, alt_titles,
                                    translated_titles, translations_at, aliases_at, updated_at)
            values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15, now(), now(), now())
            on conflict (title_id) do update set
                imdb_id = excluded.imdb_id, tmdb_id = excluded.tmdb_id,
                match_confidence = excluded.match_confidence, rating = excluded.rating,
                year = excluded.year, genres = excluded.genres,
                overview = excluded.overview, popularity = excluded.popularity,
                vote_count = excluded.vote_count, match_algo = excluded.match_algo,
                original_title = excluded.original_title,
                localized_title = excluded.localized_title,
                alt_titles = excluded.alt_titles,
                translated_titles = excluded.translated_titles,
                translations_at = now(),
                aliases_at = now(),
                updated_at = now()
            """,
            title_id, match["imdb_id"], match["tmdb_id"], match["confidence"],
            match["rating"], match["year"], match["genres"], match["overview"],
            match.get("popularity", 0.0), match.get("vote_count", 0), ALGO_VERSION,
            match.get("original_title", ""), match.get("localized_title", ""),
            list(match.get("alt_titles") or []),
            list(match.get("translated_titles") or []),
        )
        # Propaga el impacto a titles para que el índice ordene por popularidad
        # sin joins por fila (estrenos/géneros profesionales).
        await conn.execute(
            "update titles set popularity = $2, vote_count = $3, updated_at = now() where id = $1",
            title_id, match.get("popularity", 0.0), match.get("vote_count", 0),
        )
    try:
        # El texto de búsqueda se rehace al momento (si fallara, el pase de
        # mantenimiento del ciclo lo detecta por updated_at y lo rehace igual).
        await search_text.rebuild(pool, ids=[title_id])
    except Exception:  # noqa: BLE001
        log.warning("TMDB: texto de búsqueda de %s no se pudo rehacer", title_id)


async def _mark_no_match(pool, title_id: str) -> None:
    async with pool.acquire() as conn:
        await conn.execute(
            """
            insert into enrichment (title_id, match_confidence, match_algo, updated_at)
            values ($1, 0.0, $2, now())
            on conflict (title_id) do update set
                match_confidence = 0.0, match_algo = $2, updated_at = now()
            """,
            title_id, ALGO_VERSION,
        )


async def _run_matches(pool, pending, label: str) -> int:
    done = 0
    for row in pending:
        try:
            match, had_error = await asyncio.to_thread(
                _match, row["kind"], row["title"], row["original_title"], row["year"]
            )
        except Exception as exc:  # noqa: BLE001
            log.warning("TMDB: %s falló: %s", row["title"], exc)
            match, had_error = None, True
        if match:
            await _store_match(pool, row["id"], match)
            done += 1
        elif not had_error:
            await _mark_no_match(pool, row["id"])
        # Con error de red no se marca nada: el título sigue pendiente al próximo ciclo.
        if DELAY:
            await asyncio.sleep(DELAY)
    log.info("TMDB: %s %s/%s títulos", label, done, len(pending))
    return done


async def enrich_batch(pool) -> int:
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select t.id, t.title, t.original_title, t.year, t.kind
            from titles t
            left join enrichment e on e.title_id = t.id
            where e.title_id is null and t.kind in ('movie', 'series', 'anime', 'kids')
            order by (t.source = 'xtv') desc, t.is_premiere desc, t.score desc nulls last, t.updated_at desc
            limit $1
            """,
            BATCH,
        )
    if not pending:
        return 0
    return await _run_matches(pool, pending, "enriquecidos")


async def rematch_batch(pool) -> int:
    """Reintenta los marcados «sin match» con la búsqueda corregida (sin marca de
    temporada). Sin esto, 16k series quedaron fuera de TMDB para siempre.

    El candado es por VERSIÓN del emparejador (`match_algo`): subir [ALGO_VERSION] da una
    pasada fresca a todo el backlog, una sola vez; el cooldown de días solo aplica a los
    intentos hechos por la versión actual.
    """
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select t.id, t.title, t.original_title, t.year, t.kind
            from enrichment e
            join titles t on t.id = e.title_id
            where coalesce(e.match_confidence, 0) < 0.5
              and e.tmdb_id is null
              and (e.match_algo is distinct from $3
                   or e.updated_at < now() - make_interval(days => $1))
            order by case t.kind
                         when 'series' then 0
                         when 'anime' then 1
                         when 'kids' then 2
                         else 3
                     end,
                     t.is_premiere desc, t.score desc nulls last, t.year desc nulls last
            limit $2
            """,
            REMATCH_COOLDOWN_DAYS,
            REMATCH_BATCH,
            ALGO_VERSION,
        )
    if not pending:
        return 0
    return await _run_matches(pool, pending, "re-matcheados")


async def backfill_popularity(pool) -> int:
    """Rellena popularidad/votos de matches que llegaron antes de esas columnas.

    Sin esto, el orden por popularidad era inerte (0 para 10.5k títulos) y los
    «score» inflados del portal (película de nicho 9.3) dominaban las filas.
    """
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select e.title_id, e.tmdb_id, t.kind
            from enrichment e
            join titles t on t.id = e.title_id
            where e.match_confidence >= 0.5 and e.tmdb_id is not null
              and coalesce(t.popularity, 0) = 0
            order by t.is_premiere desc, t.score desc nulls last, t.year desc nulls last
            limit $1
            """,
            BACKFILL_BATCH,
        )
    if not pending:
        return 0
    done = 0
    for row in pending:
        details = None
        for media in (("tv", "movie") if _media_for(row["kind"]) == "tv" else ("movie", "tv")):
            try:
                details = await asyncio.to_thread(_details_for, row["tmdb_id"], media)
                break
            except Exception:  # noqa: BLE001 - el id puede ser del otro tipo
                details = None
        if details:
            popularity, votes = _impact_of(details)
            async with pool.acquire() as conn:
                await conn.execute(
                    "update enrichment set popularity = $2, vote_count = $3, updated_at = now() where title_id = $1",
                    row["title_id"], popularity, votes,
                )
                await conn.execute(
                    "update titles set popularity = $2, vote_count = $3, updated_at = now() where id = $1",
                    row["title_id"], popularity, votes,
                )
            done += 1
        if DELAY:
            await asyncio.sleep(DELAY)
    log.info("TMDB: %s/%s popularidad rellenada", done, len(pending))
    return done


def _fetch_alias_bundle(tmdb_id: int, kind: str) -> dict | None:
    """Nombres de un título ya casado en TMDB: (es-MX, original, alternativos).

    Prueba el tipo probable primero (películas: movie; series/anime/kids: tv) y cae al
    otro: el `kind` de la repisa no siempre coincide con el tipo real de TMDB.
    Devuelve None si TMDB ya no tiene el id (404 en ambos tipos: ficha borrada/fusionada).
    Los errores de red se propagan: la fila queda sin marca y se reintenta otro ciclo.
    """
    order = ("tv", "movie") if _media_for(kind) == "tv" else ("movie", "tv")
    for media in order:
        try:
            details = _get(
                f"/{media}/{tmdb_id}", language="es-MX",
                append_to_response="alternative_titles", gap=ALIAS_GAP,
            )
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                continue
            if exc.code == 429:  # límite de la API: se le da aire y se reintenta otro ciclo
                time.sleep(3.0)
            raise
        return {
            "original_title": (details.get("original_title") or details.get("original_name") or "").strip(),
            "localized_title": (details.get("title") or details.get("name") or "").strip(),
            "alt_titles": parse_alt_titles(details.get("alternative_titles")),
        }
    return None


async def _rebuild_ids(pool, ids) -> None:
    """Rehace el texto de búsqueda de los títulos indicados (a prueba de fallos)."""
    try:
        await search_text.rebuild(pool, ids=ids)
    except Exception:  # noqa: BLE001 - el pase de mantenimiento lo rehará
        log.warning("TMDB alias: textos de búsqueda pendientes (los rehará el pase)")


async def backfill_aliases(pool, workers: int = 1) -> int:
    """Nombres para buscar en los matches YA existentes (sin ellos, «buscando a nemo»
    no encontraba «Finding Nemo» ni «vaiana» encontraba «Moana»; brecha medida 2026-10-18).

    Una llamada por título conocido (sin búsqueda), con ritmo propio (`ALIAS_GAP`) y
    marca `aliases_at` para no repetir: los populares primero. Tras guardar, rehace el
    texto de búsqueda de esos títulos. `workers > 1` (solo el catch-up manual con
    `python tmdb.py --aliases N`): las descargas viajan en olas paralelas, las
    escrituras siguen en el hilo principal.
    """
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select e.title_id, e.tmdb_id, t.kind, t.title
            from enrichment e
            join titles t on t.id = e.title_id
            where e.tmdb_id is not null and coalesce(e.match_confidence, 0) >= 0.5
              and e.aliases_at is null
            order by t.popularity desc nulls last, t.score desc nulls last
            limit $1
            """,
            ALIAS_BATCH,
        )
    if not pending:
        return 0
    done, ids, gone = 0, [], 0
    wave_size = max(1, workers)
    for start in range(0, len(pending), wave_size):
        wave = pending[start:start + wave_size]
        results = await asyncio.gather(
            *(asyncio.to_thread(_fetch_alias_bundle, row["tmdb_id"], row["kind"]) for row in wave),
            return_exceptions=True,
        )
        for row, result in zip(wave, results):
            if isinstance(result, BaseException):  # noqa: BLE001 - se reintenta otro ciclo
                log.warning("TMDB alias: %s no se pudo consultar (%s)", row["title"], result)
                continue
            async with pool.acquire() as conn:
                if result is None:
                    # TMDB ya no tiene ese id: se marca para no reintentarlo para siempre.
                    await conn.execute(
                        "update enrichment set aliases_at = now() where title_id = $1",
                        row["title_id"],
                    )
                    gone += 1
                    continue
                await conn.execute(
                    """
                    update enrichment set original_title = $2, localized_title = $3,
                           alt_titles = $4, aliases_at = now(), updated_at = now()
                    where title_id = $1
                    """,
                    row["title_id"], result["original_title"], result["localized_title"],
                    result["alt_titles"],
                )
            ids.append(row["title_id"])
            done += 1
        if len(ids) >= ALIAS_REBUILD_CHUNK:
            await _rebuild_ids(pool, ids)
            ids = []
    if ids:
        await _rebuild_ids(pool, ids)
    log.info("TMDB alias: %s/%s nombres (ids perdidos en TMDB: %s)", done, len(pending), gone)
    return done


def _fetch_translation_bundle(tmdb_id: int, kind: str) -> list[str] | None:
    """Traducciones por idioma de un id ya casado en TMDB.

    Prueba el tipo probable primero (películas: movie; series/anime/kids: tv) y cae al
    otro, igual que el backfill de nombres. Devuelve None solo si TMDB ya no tiene el
    id en ninguno de los dos tipos (404): así la fila se marca y no se reintenta para
    siempre. Los errores de red se propagan para que la fila se reintente otro ciclo.
    """
    order = ("tv", "movie") if _media_for(kind) == "tv" else ("movie", "tv")
    for media in order:
        try:
            payload = _get(f"/{media}/{tmdb_id}/translations", gap=TRANSLATIONS_GAP)
        except urllib.error.HTTPError as exc:
            if exc.code == 404:
                continue
            if exc.code == 429:  # límite de la API: se le da aire y se reintenta otro ciclo
                time.sleep(3.0)
            raise
        return parse_translations(payload)
    return None


async def backfill_translations(pool, workers: int = 1) -> int:
    """Traducciones por idioma para los matches ya existentes.

    Los nombres alternativos por región (`alternative_titles`) no siempre traen los
    nombres por idioma: la muestra real del 2026-10-22 dio nombres nuevos en 25/25
    títulos sin alternativos y en 10/10 con alternativos («Die Simpsons», «Блич»,
    «死神»). Aquí se completa esa mitad y se rehace `search_text` por trozos.
    """
    if not KEY:
        return 0
    async with pool.acquire() as conn:
        pending = await conn.fetch(
            """
            select e.title_id, e.tmdb_id, t.kind, t.title
            from enrichment e
            join titles t on t.id = e.title_id
            where e.tmdb_id is not null and coalesce(e.match_confidence, 0) >= 0.5
              and e.translations_at is null
            order by t.popularity desc nulls last, t.score desc nulls last
            limit $1
            """,
            TRANSLATIONS_BATCH,
        )
    if not pending:
        return 0
    done, ids, gone = 0, [], 0
    wave_size = max(1, workers)
    for start in range(0, len(pending), wave_size):
        wave = pending[start:start + wave_size]
        results = await asyncio.gather(
            *(asyncio.to_thread(_fetch_translation_bundle, row["tmdb_id"], row["kind"]) for row in wave),
            return_exceptions=True,
        )
        for row, result in zip(wave, results):
            if isinstance(result, BaseException):  # noqa: BLE001 - se reintenta otro ciclo
                log.warning("TMDB traducciones: %s no se pudo consultar (%s)", row["title"], result)
                continue
            async with pool.acquire() as conn:
                if result is None:
                    # TMDB ya no tiene ese id: se marca para no reintentarlo para siempre.
                    await conn.execute(
                        "update enrichment set translations_at = now() where title_id = $1",
                        row["title_id"],
                    )
                    gone += 1
                    continue
                await conn.execute(
                    """
                    update enrichment set translated_titles = $2,
                           translations_at = now(), updated_at = now()
                    where title_id = $1
                    """,
                    row["title_id"], result,
                )
            ids.append(row["title_id"])
            done += 1
        if len(ids) >= ALIAS_REBUILD_CHUNK:
            await _rebuild_ids(pool, ids)
            ids = []
    if ids:
        await _rebuild_ids(pool, ids)
    log.info("TMDB traducciones: %s/%s nombres (ids perdidos en TMDB: %s)", done, len(pending), gone)
    return done


async def coverage(pool) -> list[dict]:
    """Cobertura de fichas por tipo (total, con match y sin intentar) — el medidor del ciclo."""
    if not KEY:
        return []
    rows = await pool.fetch(
        """
        select t.kind,
               count(*) as total,
               count(*) filter (where e.tmdb_id is not null) as matched,
               count(*) filter (where e.title_id is null) as fresh
        from titles t
        left join enrichment e on e.title_id = t.id
        where t.kind in ('movie', 'series', 'anime', 'kids')
        group by 1
        """
    )
    return [dict(r) for r in rows]


def format_coverage(rows) -> str:
    """Una línea de log: «series 12000/17198 (70%) · anime …» (mayor primero)."""
    parts = []
    for row in sorted(rows, key=lambda r: -int(r["total"] or 0)):
        total = int(row["total"] or 0)
        matched = int(row["matched"] or 0)
        pct = round(100.0 * matched / total) if total else 0
        parts.append(f"{row['kind']} {matched}/{total} ({pct}%)")
    return " · ".join(parts)


if __name__ == "__main__":
    import sys

    import asyncpg

    async def _cli() -> None:
        """Catch-up manual: `--aliases [workers]` o `--translations [workers]`."""
        mode = next((m for m in ("--translations", "--aliases") if m in sys.argv), "")
        if not mode:
            print("uso: python tmdb.py --aliases [workers] | --translations [workers]")
            return
        workers = 1
        idx = sys.argv.index(mode) + 1
        if idx < len(sys.argv) and sys.argv[idx].isdigit():
            workers = max(1, int(sys.argv[idx]))
        label = "traducciones" if mode == "--translations" else "alias"
        pool = await asyncpg.create_pool(os.environ["DATABASE_URL"], min_size=1, max_size=2)
        try:
            total = 0
            while True:
                if mode == "--translations":
                    n = await backfill_translations(pool, workers=workers)
                else:
                    n = await backfill_aliases(pool, workers=workers)
                total += n
                # Barrido de sanación: cualquier texto viejo (p. ej. de una pasada
                # interrumpida por un reinicio) se rehace aquí, no en horas.
                healed = await search_text.rebuild(pool)
                print(f"{label}: {n} (total {total}); textos sanados: {healed}", flush=True)
                if n == 0 and healed == 0:
                    break
            print(f"{label} completo: {total}")
        finally:
            await pool.close()

    asyncio.run(_cli())
