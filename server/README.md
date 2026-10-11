# Índice Vision+ (server) — fase 1

Mini backend para el índice de películas/series y categorías del catálogo. La app
(Android) más adelante consultará este índice con una fuente remota nueva
(`RemoteIndexSource`), manteniendo la reproducción **en el dispositivo**: las
licencias (`playCode`/`license`) son por sesión/dispositivo y aquí no se tocan.

```
VPS (docker compose)                       App Android (fase 4)
├─ postgres  → índice (títulos, categorías, enriquecimiento, versión)   RemoteIndexSource → GET /v1/index?since=
├─ api       → GET /healthz, /v1/index, /v1/categories, …               fallback: fuentes locales de siempre
└─ sync      → aplica esquema + ciclo periódico (conectores en fase 2)
Caddy nativo del host (80/443) → reverse_proxy 127.0.0.1:8100
```

## Estructura

```
server/
├─ docker-compose.yml     # postgres + api + sync (sin Caddy; el del host hace HTTPS)
├─ .env.example           # copiar a .env y completar (nunca se sube)
├─ api/                   # FastAPI: app.py, requirements.txt, Dockerfile
├─ sync/                  # worker: main.py, schema.sql, requirements.txt, Dockerfile
└─ scripts/backup.sh      # pg_dump con retención de 7 días
```

## Probar en local

```bash
cd server
cp .env.example .env      # edita POSTGRES_PASSWORD y API_KEY
# para probar de punta a punta, agrega SEED_DEMO=1 al .env
docker compose up -d --build
docker compose logs -f sync        # "esquema aplicado" y "seed demo insertado"
curl -s http://127.0.0.1:8100/healthz
curl -s -H "X-Api-Key: TU_API_KEY" http://127.0.0.1:8100/v1/index
docker compose down                # agrega -v para borrar también la base local
```

## Despliegue en el VPS OCI (159.54.146.146, usuario `opc`)

El stack convive con La Veinte: no usa 80/443 (Caddy nativo), no publica
5432/6543/3000/8000/8443, y la API solo escucha en `127.0.0.1:8100`.

```bash
# 1. Copiar el proyecto (desde tu máquina Windows; en Git Bash/WSL)
scp -i ~/.ssh/oci_key_3 -r server/ opc@159.54.146.146:/tmp/streaming-backend
ssh -i ~/.ssh/oci_key_3 opc@159.54.146.146
sudo mkdir -p /opt/streaming-backend
sudo mv /tmp/streaming-backend/* /opt/streaming-backend/
sudo chown -R opc:opc /opt/streaming-backend
cd /opt/streaming-backend

# 2. Secretos reales
cp .env.example .env
# genera valores nuevos:
echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)" # y API_KEY=$(openssl rand -hex 24)
chmod 600 .env
nano .env

# 3. Levantar (no toca los puertos de La Veinte)
docker compose up -d --build
docker compose ps
curl -s http://127.0.0.1:8100/healthz

# 4. Exponer con HTTPS en el Caddy existente (subdominio propio)
#    DNS: registro A del subdominio → 159.54.146.146
sudo nano /etc/caddy/Caddyfile
```

```caddyfile
catalogo.TUDOMINIO.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8100
}
```

```bash
sudo systemctl reload caddy     # sin interrumpir la20.com.mx
curl -s https://catalogo.TUDOMINIO.com/healthz
curl -s -H "X-Api-Key: TU_API_KEY" "https://catalogo.TUDOMINIO.com/v1/index"
```

La VCN de OCI ya permite 80/443/22: no hay que abrir puertos nuevos ni tocar
`firewalld`.

## Backups

```bash
crontab -e
# backup diario 04:15 con retención de 7 días:
15 4 * * * /opt/streaming-backend/scripts/backup.sh >> /opt/streaming-backend/backups/backup.log 2>&1
```

Restaurar: `docker compose exec -T postgres pg_restore -U catalog_user -d catalog_db --clean /ruta/catalog_*.dump`
(el dump debe copiarse antes dentro del contenedor o usar `docker cp`).

## Endpoints (todos con header `X-Api-Key`, salvo `/healthz`)

| Endpoint | Uso |
| --- | --- |
| `GET /healthz` | estado + conexión a la DB |
| `GET /v1/index?since=<v>` | índice completo de categorías con sus títulos; si la versión no cambió responde `upToDate: true` |
| `GET /v1/categories` | categorías con conteo, sin títulos |
| `GET /v1/category/{id}?page=&pageSize=` | títulos de una categoría, paginados |
| `GET /v1/section/{name}?limit=` | hileras de género de una sección (`movies`/`series`/`novelas`/`anime`), solo con títulos de esa sección |
| `GET /v1/section/{name}?limit=` | hileras de género de una sección (`movies`/`series`/`novelas`/`anime`), solo con títulos de esa sección |
| `GET /v1/search?q=&limit=` | búsqueda por título (más adelante: título original/géneros) |
| `GET /v1/sports?date=&days=` | agenda de fútbol (ESPN) por día con cadenas candidatas |

La forma de cada título (`id`, `title`, `year`, `durationMin`, `synopsis`,
`posterUrl`, `videoUrl`, `sourceUrl`) mapea 1:1 con `Film` en la app.

## Fases

- **Fase 1**: esquema + API + worker + seed local + README. ✅
- **Fase 2**: conectores propios (`sync/`): XTV (árbol de columnas + `getShelveData`
  paginado + canales en vivo), Magis (`getHome`) e Internet Archive. ✅
- **Fase 3**: enriquecimiento TMDB (imdb_id, rating, géneros) + Estrenos/Recomendados/
  Géneros calculados en el servidor. ✅ (TMDB solo corre con `TMDB_API_KEY`).
- **Fase 4**: `RemoteIndexSource` en la app (consulta con caché + fallback local). ⏳

## Operación

- El portal XTV limita cada listado "All" a **10.000 títulos** (`portal100081`); el
  worker usa `XTV_MAX_PAGES=100` (100/página) y las colecciones propias van a 3
  páginas cada una (`XTV_COLLECTION_PAGES`). Un ciclo completo tarda ~10-15 min.
- Para activar el enriquecimiento: poner `TMDB_API_KEY` en `.env` y
  `docker compose up -d sync` (procesa `ENRICH_BATCH` títulos por ciclo).
- La versión del índice (`/v1/categories`) sube una vez por ciclo con cambios.
- La agenda de fútbol (`/v1/sports`) corre en una tarea aparte del worker cada
  `SPORTS_INTERVAL_SECONDS` (10 min): baja las ligas de ESPN para ayer, hoy,
  mañana y los dos días siguientes. No toca la versión del índice.
- Los pools de géneros salen de `tags` del portal (traducidos a español) — sin
  llamadas extra por género.

## Notas de convivencia (VPS compartido)

- No levantar Caddy en Docker ni mapear `5432:5432`.
- Límites de memoria por contenedor ya definidos (2 GB db, 512 MB api/sync).
- Logs con rotación (`max-size 10m`, 3 archivos).
- El worker `sync` nunca muere por un error: registra y reintenta en el próximo ciclo.
