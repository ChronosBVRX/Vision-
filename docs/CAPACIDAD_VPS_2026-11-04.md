# Capacidad del VPS y caché de respuestas (2026-11-04)

## Resumen

Se mantuvo la instancia **gratuita** (4 OCPU Ampere A1 / 24 GB RAM, 133 GB de disco,
10 TB/mes de salida). No se redimensionó, no se agregaron volúmenes y no se creó
ningún servicio de pago.

El cambio fue **software**: cachear en memoria las respuestas que todos los equipos
piden igual y servir el índice ya serializado/comprimido. El objetivo era dejar de
repetir el mismo cálculo de ~3.6 s por cada usuario.

## Qué se desplegó

Archivo `server/api/app.py`:

- **Caché TTL de `/v1/gee38/events`** (`GEE_EVENTS_CACHE_SECONDS`, 45 s por defecto).
- **Caché TTL de `/v1/gee38/sports`** (`GEE_SPORTS_CACHE_SECONDS`, 120 s por defecto).
- **Single-flight**: si muchas peticiones llegan con la caché vacía, solo una calcula
  y las demás esperan la misma copia (evita la estampida).
- **`/v1/index` pre-serializado y precomprimido** por versión y límite: el JSON y su
  gzip se arman una vez, no una vez por usuario. Se respeta `Accept-Encoding`.
- **Pool de PostgreSQL de 4 a 10 conexiones** (`DB_POOL_MAX`, 10 por defecto).
- Se conserva la última copia buena del índice si la base falla.

Archivos de apoyo: `server/docker-compose.yml` y `server/.env.example` documentan
las tres variables. No se agregó ningún contenedor ni dependencia nueva.

## Antes / después (medido en el VPS)

| Prueba | Antes | Después |
|---|---:|---:|
| `/v1/gee38/events` (frío) | 3.6 s | ~2.9 s (una vez por ventana) |
| `/v1/gee38/events` (caché) | 3.6 s cada uno | **0.027 s** |
| `/v1/gee38/events` en caliente, 100 peticiones, par 20 | 0.27 req/s (1 worker) | **36 req/s** |
| `/v1/index` completo (caché, gzip) | 0.39 s / 2.5 req/s | **0.027 s / ~99 req/s** |
| `/v1/index?since=<versión>` | 423 req/s | **~330 req/s** (con límite por IP) |
| 30 peticiones simultáneas a `events` con caché vacía | ~30 cálculos de 3.6 s | **3.5 s en total, un solo cálculo** |
| 30 peticiones simultáneas a `/v1/index` con caché vacía | ~30 cálculos de 0.4 s | **1.4 s en total, un solo cálculo** |

Uso durante las pruebas: API **~110 MB / 1 GB**, PostgreSQL **~390 MB / 2 GB**,
host con **~15 GB de RAM disponibles**, load < 1.0. Sin 5xx.

## Capacidad estimada

- Antes: **~100–150 usuarios simultáneos** en el peor caso (todos en Inicio/Deportes).
- Ahora: objetivo seguro de **1,000–2,000 usuarios simultáneos**; el endpoint de
  Deportes en caliente da margen para más, pero el número real depende de la mezcla
  de uso.
- El video **no pasa por el VPS**; reproducir no consume este backend.
- Los 10 TB/mes de salida siguen muy por encima del uso esperado para ese rango.

## Por qué no se agregaron más workers

`uvicorn --workers 2` duplicaría los bucles de fondo (`_markers_prewarm_loop` y
`_games_sweep_loop`) y las cachés por proceso. Antes de escalar horizontalmente hay
que separar esos bucles a un solo worker o a un proceso aparte. Con la caché actual,
un worker es suficiente para el objetivo.

## Rollback

Respaldos en el VPS:

- `/opt/streaming-backend/api/app.py.bak-cache-20261104-154050`
- `/opt/streaming-backend/api/app.py.bak-cache2-20261104-154455`
- `/opt/streaming-backend/docker-compose.yml.bak-cache-20261104-154050`

Restaurar la versión anterior de la API:

```bash
cd /opt/streaming-backend
cp api/app.py.bak-cache-20261104-154050 api/app.py
docker compose up -d --build api
```

Para volver a medir:

```bash
curl -s -o /dev/null -w '%{http_code} %{time_total}\n' \
  -H "X-Api-Key: $API_KEY" \
  "http://127.0.0.1:8100/v1/gee38/events?day=$(TZ=America/Mexico_City date +%F)&days=2"
```
