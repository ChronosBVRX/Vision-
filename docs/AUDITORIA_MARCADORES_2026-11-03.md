# Auditoría de «Omitir intro» y «Siguiente capítulo» — 2026-11-03

Alcance: base de datos del VPS (`catalog_db`), catálogo XTV/Magis/IA, marcadores
comunitarios de salto y espejo de temporadas/capítulos. Lectura y escritura solo
del backend; no se tocó el APK.

## 1. Estado inicial medido

| Métrica | Antes |
|---|---:|
| Filas en `skip_markers` | 1,010 |
| Referencias (temporadas) distintas | 166 |
| Filas con algún segmento útil | 733 |
| Referencias con datos positivos | 132 |
| Cobertura de series | 0.5 % (93 / 17,903 temporadas) |
| Cobertura de anime | 0.9 % (39 / 4,256 temporadas) |
| Cobertura de novelas | 0 % (0 / 100) |
| Cobertura de kids | 0 % |
| Temporadas con espejo de capítulos `ok` | 171 / 21,517 |
| Temporadas con metadatos TMDB | 3,141 |
| Capítulos con nombre/sinopsis | 71,326 |

Causa: el endpoint `/v1/markers` resolvía solo cuando un equipo abría un
capítulo; no existía un pase que recorriera el catálogo. Lo mismo pasaba con el
espejo de capítulos: solo se encolaba lo que pedía una ficha.

Muestra de novelas (12 títulos, S1E1): TheIntroDB/IntroDB.app respondieron datos
en 2/12. Las bases comunitarias no cubren la mayoría de telenovelas; inventar
tiempos haría saltar escenas equivocadas.

## 2. Qué se implementó

### Marcadores (`server/api/app.py`)

- Pase de fondo `_markers_prewarm_loop`, arrancado y cancelado con el ciclo de
  vida del API.
- Consulta `_MARKERS_PREWARM_SQL`: recorre todas las temporadas de
  `series`, `anime` y `kids` con `program_type` de serie/teleplay, ordenadas por
  popularidad.
- Indexa el episodio 1 de cada temporada. Si hay datos, indexa también E2 y E3
  (`MARKERS_PREWARM_EPISODES`, configurable). Así la mayoría de series quedan
  listas y el resto se calienta al reproducir (cascada existente).
- Respeta los TTL del endpoint: 14 días para positivos y 2 días para negativos;
  no repite lo ya indexado.
- Cada ficha va en su propio `try`: una ficha rota no detiene el barrido.
- `/v1/stats` ahora informa `markers.byKind`.

### Espejo de capítulos (`server/sync/episodes_mirror.py`)

- `prewarm()` encola temporadas del catálogo que aún no tienen copia, cuando la
  cola está por debajo de `EPISODES_PREWARM_LOW_WATER`.
- Orden popular, `on conflict (ref) do nothing` (respeta lo que ya pidió la app)
  y lotes acotados para no acumular miles de pendientes.
- El worker sigue drenando con su sesión propia y sus reintentos/backoff.
- Se subieron los presupuestos del VPS para completar el barrido en horas:
  `EPISODES_MAX_PER_CYCLE=120`, `EPISODES_META_MAX_PER_CYCLE=200`,
  prewarm TMDB `10000 + 10000`.

## 3. Verificación

- Suite backend: **673 pruebas en verde**.
- `GET /healthz`: 200; sin respuestas 5xx ni excepciones en los logs tras el
  despliegue.
- Smoke real:
  - `/v1/markers?ref=xtv:38CAC…&ep=1` → intro 42.5–72.5 s y créditos
    1,327–1,359 s (`introdb`).
  - `/v1/episodes?ref=xtv:6E40…` → 10 capítulos con nombre, sinopsis es-MX y
    miniatura de TMDB.
  - Anime: «Avatar: La Leyenda de Korra» T3/T4 indexado con E1–E3.

## 4. Progreso al cierre de la sesión

| Métrica | Después (parcial) |
|---|---:|
| Filas en `skip_markers` | 13,680 |
| Filas con datos | 10,299 |
| Series top-100 con marcadores | 30 → 89 |
| Series top-500 con marcadores | 48 → 428 |
| Anime top-100 con marcadores | 21 → 84 |
| Novelas con marcadores | 0 → 2 / 100 |
| Temporadas espejadas `ok` | 15,325 |
| Capítulos espejados | 350,075 |
| Temporadas con metadatos | 15,264 |

El barrido continúa en segundo plano. A ~60 filas de marcadores por minuto y
~120 temporadas espejadas por ciclo (drenaje y metadatos en carriles separados),
el catálogo completo tarda varias horas; si el contenedor reinicia, retoma solo
porque la consulta excluye lo ya indexado.

Las 100 novelas detectadas se priorizaron en la cola del espejo y se consultaron
sus marcadores de E1: 99 quedaron con lista de capítulos (11,527 capítulos) y 2
con algún marcador positivo.

## 5. Límites honestos

- **Películas**: no usan «Omitir intro»; su equivalente es «Más como esta» al
  terminar. El catálogo de películas ya está indexado (28,265 fichas).
- **Novelas**: el «siguiente capítulo» quedó cubierto: 99 de las 100 novelas
  detectadas ya tienen espejo (11,527 capítulos) y metadatos. El intro depende
  de que exista en las bases comunitarias (2/100); no se inventan tiempos.
- **Episodios E4+**: se resuelven on-demand y se cachean; no se precargan todos
  para no multiplicar peticiones externas ni almacenar ruido.

## 6. Operación

- Estado: `GET /v1/stats` → bloques `markers` y `episodes`.
- **TheIntroDB con cuenta**: la API key vive SOLO en `/opt/streaming-backend/.env`
  (`THEINTRODB_API_KEY=...`) y el compose del API la pasa como variable. Con cuenta
  el tope sube de 5,000 a **100,000 peticiones/día**; sin ella se usa el anónimo.
  La clave no se guarda en el repo ni en la app.
- Desactivar el pase de marcadores: `MARKERS_PREWARM=0` y recrear el API.
- Desactivar el prewarm del espejo: `EPISODES_PREWARM=0` y recrear el sync.
- Respaldos del despliegue: `api/app.py.bak-markers-20261103-222919`,
  `sync/episodes_mirror.py.bak-markers-20261103-222919`,
  `.env.bak-markers-20261103-222919`; los redeploys posteriores dejaron
  `*.bak-tidb-*` y `*.bak-tidb-priority-*`.

## 7. Re-auditoría rápida

```sql
-- Cobertura de marcadores por tipo (temporadas con al menos un positivo)
with pos as (
  select distinct ref from skip_markers where segments <> '{}'::jsonb
)
select t.kind, count(*) as temporadas,
       count(*) filter (where t.id in (select ref from pos)
                           or t.play_ref in (select ref from pos)) as con_marcadores
from titles t
where t.kind in ('movie','series','anime','kids')
group by 1 order by 2 desc;

-- Espejo de capítulos
select status, count(*), coalesce(sum(episodes),0)
from episode_mirror group by 1;

-- Metadatos de capítulos
select status, count(*) from episode_meta_season group by 1;
```
