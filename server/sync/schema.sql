-- Esquema del índice Vision+ (fase 1). Idempotente: el worker lo aplica al arrancar.
-- Solo metadatos de catálogo: la reproducción (licencias) vive en el dispositivo.

create table if not exists sync_state (
    id integer primary key default 1 check (id = 1),
    version bigint not null default 1,
    updated_at timestamptz not null default now()
);

insert into sync_state (id, version) values (1, 1)
on conflict (id) do nothing;

-- Metadatos del worker que sobreviven reinicios (p. ej. cuándo fue la última
-- pasada PROFUNDA de colecciones del portal). Clave-valor simple.
create table if not exists sync_meta (
    key text primary key,
    value text not null default '',
    updated_at timestamptz not null default now()
);

create table if not exists titles (
    id text primary key,                       -- id estable con prefijo de fuente: xtv:<cid>
    source text not null,                      -- xtv | magis | ia | m3u
    source_id text not null,                   -- contentId / identifier del portal
    kind text not null default 'movie',        -- movie | series | anime | kids
    title text not null,
    original_title text,
    year integer,
    synopsis text not null default '',
    poster_url text not null default '',
    backdrop_url text not null default '',
    duration_min integer not null default 0,
    score numeric(4,1),
    genres text[] not null default '{}',
    cast_list text not null default '',
    director text not null default '',
    audio text not null default '',
    subs text not null default '',
    play_ref text not null,                    -- ref que la app resuelve en el dispositivo
    is_premiere boolean not null default false,
    is_recommended boolean not null default false,
    updated_at timestamptz not null default now(),
    unique (source, source_id)
);

-- program_type real del portal (movie | series | teleplay | anime | ...): manda
-- sobre kind para clasificar secciones (kind se reescribe entre columnas).
alter table titles add column if not exists program_type text not null default '';
-- Popularidad TMDB para ordenar tendencias/estrenos como app profesional
-- (rating solo no distingue un 8.0 oscuro de un 7.7 con 200k votos).
alter table titles add column if not exists popularity numeric(10,3) not null default 0;
alter table titles add column if not exists vote_count integer not null default 0;

create table if not exists categories (
    id text primary key,                       -- estrenos | recomendados | genero-accion | xtv-movies-…
    title text not null,
    kind text not null default 'source',       -- special | genre | source | section
    position integer not null default 100,
    updated_at timestamptz not null default now()
);

create table if not exists title_categories (
    title_id text not null references titles(id) on delete cascade,
    category_id text not null references categories(id) on delete cascade,
    position integer not null default 0,
    primary key (title_id, category_id)
);

-- Fase 3: enriquecimiento TMDB / datasets IMDb, con confianza de match y overrides.
create table if not exists enrichment (
    title_id text primary key references titles(id) on delete cascade,
    imdb_id text,
    tmdb_id integer,
    match_confidence numeric(4,3),
    rating numeric(3,1),
    year integer,
    genres text[] not null default '{}',
    updated_at timestamptz not null default now()
);

-- Sinopsis es-MX de TMDB (fase 3): la API la prefiere sobre la descripción del portal.
alter table enrichment add column if not exists overview text not null default '';
-- Señales de impacto TMDB para ponderar el rating (evitan que un oscuro 9.0
-- tape a un blockbuster 7.7): se sirven al API y ordenan estrenos/géneros.
alter table enrichment add column if not exists popularity numeric(10,3) not null default 0;
alter table enrichment add column if not exists vote_count integer not null default 0;
-- Versión del emparejador TMDB que intentó por última vez cada fila: subir ALGO_VERSION
-- en tmdb.py da una pasada fresca a TODOS los «sin match» sin esperar el cooldown.
alter table enrichment add column if not exists match_algo text not null default '';

create index if not exists idx_titles_source on titles (source);
create index if not exists idx_title_categories_category on title_categories (category_id, position);
create index if not exists idx_titles_title_lower on titles (lower(title));

-- Nombres como la gente busca (2026-10-18): un título del portal puede llamarse
-- «Finding Nemo» (y el usuario buscar «buscando a nemo»): el es-MX de TMDB, su
-- original (inglés) y los alternativos por región («Vaiana» para «Moana») entran
-- al enriquecimiento para que el buscador los encuentre (sync/search_text.py).
alter table enrichment add column if not exists original_title text not null default '';
alter table enrichment add column if not exists localized_title text not null default '';
alter table enrichment add column if not exists alt_titles text[] not null default '{}';
-- Marca de intento del backfill de nombres (una llamada por título, para no repetir).
alter table enrichment add column if not exists aliases_at timestamptz;

-- Texto de búsqueda materializado de cada título (nombres plegados + compactos):
-- el buscador matchea aquí primero (índice GIN) y solo cae a la búsqueda profunda
-- (reparto/sinopsis) y al fuzzy cuando esta no da resultados.
alter table titles add column if not exists search_text text not null default '';
alter table titles add column if not exists search_text_at timestamptz;

-- Trigramas: búsqueda tolerante a errores de escritura («termintor 2» encuentra
-- «Terminator 2») y aceleración de LIKE '%…%' sobre search_text. Si la extensión
-- no estuviera disponible el buscador sigue funcionando (a lo sumo, sin ese atajo).
do $$
begin
    create extension if not exists pg_trgm;
exception when others then
    raise notice 'pg_trgm no disponible: la busqueda difusa queda desactivada';
end $$;

do $$
begin
    create index if not exists idx_titles_search_trgm
        on titles using gin (search_text gin_trgm_ops);
exception when others then
    raise notice 'indice trgm no creado: se buscara sin aceleracion';
end $$;

-- Agenda de fútbol (GET /v1/sports): partidos que baja el conector sports.py.
-- Fuera del índice de catálogo y de su versión: tiene su propio ciclo rápido.
create table if not exists sports_matches (
    id text primary key,                       -- id del evento en ESPN
    league_id text not null,
    league_name text not null default '',
    league_logo text not null default '',
    home_id text not null default '',
    home_name text not null default '',
    home_abbr text not null default '',
    home_logo text not null default '',
    home_score integer,
    away_id text not null default '',
    away_name text not null default '',
    away_abbr text not null default '',
    away_logo text not null default '',
    away_score integer,
    kickoff timestamptz not null,
    status text not null default 'pre',        -- pre | in | post
    status_detail text not null default '',
    venue text not null default '',
    broadcasts text[] not null default '{}',   -- cadenas que reporta ESPN (mercado US)
    channels text[] not null default '{}',     -- cadenas candidatas en México (por liga)
    updated_at timestamptz not null default now()
);

create index if not exists idx_sports_kickoff on sports_matches (kickoff);

-- Agrupador de temporadas: una serie repetida como «Título T1/T2/…» vive como un grupo.
create table if not exists title_groups (
    id text primary key,
    base_title text not null,
    kind text not null,
    primary_title_id text not null references titles(id) on delete cascade,
    updated_at timestamptz not null default now()
);

create table if not exists group_members (
    group_id text not null references title_groups(id) on delete cascade,
    title_id text not null references titles(id) on delete cascade,
    season integer not null default 0,
    primary key (group_id, title_id)
);

create index if not exists idx_group_members_title on group_members (title_id);

-- Las sesiones de portal que mantiene el worker (sobreviven reinicios).
create table if not exists portal_sessions (
    portal text primary key,
    user_token text not null,
    sn_token text not null default '',
    updated_at timestamptz not null default now()
);

-- La versión del índice la bumpea el worker una vez por ciclo si algo cambió
-- (no triggers por fila: un sync masivo dispararía miles de updates).
-- Limpieza de triggers de versiones anteriores:
do $$
declare
    t text;
begin
    foreach t in array array['titles', 'categories', 'title_categories', 'enrichment'] loop
        execute format('drop trigger if exists %I on %I', 'trg_' || t || '_version', t);
    end loop;
end $$;
drop function if exists bump_sync_version();

-- Guía electrónica de programación (EPG) para TV en vivo (GET /v1/epg).
create table if not exists epg_programs (
    id text primary key,
    channel_id text not null,
    channel_name text not null,
    norm_name text not null,
    title text not null,
    description text not null default '',
    start_time timestamptz not null,
    end_time timestamptz not null,
    updated_at timestamptz not null default now()
);

create index if not exists idx_epg_norm_time on epg_programs (norm_name, start_time, end_time);
create index if not exists idx_epg_end_time on epg_programs (end_time);

-- Diagnóstico de campo (Fase 1): crashes, ANRs y anomalías que la app reporta.
-- El worker hace triage (huella nueva → alerta; sin reportes 48 h → resuelta).
create table if not exists diag_reports (
    id bigserial primary key,
    fingerprint text not null,
    kind text not null default 'anomaly',      -- crash | anr | anomaly | feedback
    code text not null default '',
    app_version text not null default '',
    version_code integer not null default 0,
    device text not null default '',
    android text not null default '',
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
create index if not exists idx_diag_fingerprint on diag_reports (fingerprint, created_at desc);
create index if not exists idx_diag_created on diag_reports (created_at desc);

-- Estado de alertas por huella (evita repetir el aviso y permite autocerrar).
create table if not exists diag_alert_state (
    fingerprint text primary key,
    alerted_at timestamptz not null default now(),
    resolved_at timestamptz
);
alter table diag_alert_state add column if not exists alert_version text;

-- Configuración remota (Fase 2): flags para mitigar un problema sin publicar OTA
-- (p. ej. downloads_enabled=false apaga las descargas al instante).
create table if not exists remote_config (
    key text primary key,
    value jsonb not null default 'true'::jsonb,
    updated_at timestamptz not null default now()
);

-- Marcadores de salto (/v1/markers): caché de las bases globales comunitarias
-- (TheIntroDB / IntroDB.app) + las marcas aprendidas del usuario, por episodio.
-- `segments` guarda un objeto por tipo (intro/recap/credits) con src/votos; {} es
-- caché negativa («se consultó y no había dato») y se revalida a los 2 días.
create table if not exists skip_markers (
    ref text not null,                         -- fila de temporada (xtv:…) tal como la app la conoce
    episode integer not null,                  -- índice 1-based dentro de la temporada
    season integer not null default 1,
    segments jsonb not null default '{}'::jsonb,
    fetched_at timestamptz not null default now(),
    primary key (ref, episode)
);

create index if not exists idx_skip_markers_fetched on skip_markers (fetched_at);

-- Mapeo TMDB → MyAnimeList para AniSkip (openings/endings de anime).
-- El dataset Fribb anime-lists da (themoviedb_id.tv, season.tmdb) → mal_id;
-- el worker lo refresca semanal en `sync/aniskip.py` (idempotente).
create table if not exists anime_mal_map (
    tmdb_id integer not null,
    season integer not null,
    mal_id integer not null,
    updated_at timestamptz not null default now(),
    primary key (tmdb_id, season)
);

-- Lo más visto de la semana (TMDB /trending): una foto semanal para la fila
-- «Populares de la semana». Se reescribe cada semana (`sync/tmdb.sync_trending`).
create table if not exists weekly_trending (
    week text not null,
    tmdb_id integer not null,
    media text not null default '',          -- movie | tv
    rank integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (week, tmdb_id)
);

-- Agenda de estrenos (TVMaze, gratis y sin llave): capítulos que salen al aire por
-- día en México, con el imdb para casarlos con las series que sigue el usuario.
-- `sync/schedule.py` la baja a diario (hoy + 6 días) y poda lo viejo.
create table if not exists schedule_airings (
    tvmaze_id integer not null,
    airdate date not null,
    show_name text not null default '',
    season integer not null default 0,
    episode integer not null default 0,
    ep_name text not null default '',
    airtime timestamptz,
    imdb text not null default '',
    updated_at timestamptz not null default now(),
    primary key (tvmaze_id, airdate)
);
create index if not exists idx_schedule_airdate on schedule_airings (airdate);
-- La ficha propia que corresponde al estreno (casada por imdb al sincronizar):
-- la app sigue por `title_id` sin adivinar nombres.
alter table schedule_airings add column if not exists title_id text not null default '';

-- Pool de identidades del portal (réplica del modelo Hydra): el tenant no incorpora
-- dispositivos nuevos, así que el operador carga los SN aprovisionados por el upstream
-- y cada dispositivo canjea UN código por SU slot (sn + user_id). Un slot = una sesión.
create table if not exists device_slots (
    sn text primary key,                          -- identidad del portal (una por usuario)
    user_id text not null default '',             -- cuenta atada al SN ('' = la de defecto)
    status text not null default 'free',          -- free|assigned|worker|blocked
    device_key text unique,                       -- huella de la instalación (una sola slot)
    claim_code text unique,                       -- código de un solo uso (se quema al canjear)
    note text not null default '',
    assigned_at timestamptz,
    last_seen_at timestamptz
);
-- Auto-asignación (sin códigos para el usuario): solo los slots `auto` se entregan
-- solos al arrancar; el resto se reserva para códigos manuales o el worker.
alter table device_slots add column if not exists auto boolean not null default true;
alter table device_slots add column if not exists created_at timestamptz not null default now();

-- GeeSports V3.8 (deportes en vivo: fuente principal de Fútbol y eventos). El worker
-- `sync/gee38.py` baja, descifra y sondea su CMS (grupos de canales 24/7 con enlaces
-- HLS/DASH-ClearKey + eventos deportivos por categoría) y deja todo ya resuelto en
-- JSONB; la API lo sirve tal cual (`/v1/gee38/*`). Nada de esto toca el índice de
-- catálogo ni su versión.
create table if not exists gee38_catalog (
    id text primary key,                 -- group:<slug> | event:<idx>
    kind text not null,                  -- group | event
    name text not null default '',
    category text,                       -- categoría del evento (Football, Basketball…)
    sort integer not null default 0,
    payload jsonb not null,
    updated_at timestamptz not null default now()
);
create index if not exists idx_gee38_kind on gee38_catalog (kind, sort);
create index if not exists idx_gee38_category on gee38_catalog (category);

-- Metadatos de su app (versión del CMS, aviso, telegram, enlaces oficiales…).
create table if not exists gee38_meta (
    k text primary key,
    v jsonb not null,
    updated_at timestamptz not null default now()
);

