# Migración del broker FHD a un VPS aislado

**Fecha:** 2026-11-16
**Objetivo:** sacar del VPS de Oracle la cuenta y el broker que firman licencias
FHD, sin mover el catálogo, la base de datos, el sync, los juegos ni los demás
proyectos. La app no cambia y no hace falta OTA.

## 1. Arquitectura

```
App Vision+  ──►  https://visionplusv2.duckdns.org/v1/play/vip
                         │
                         │ Caddy del VPS viejo (regla nueva)
                         ▼
              https://broker.visionplusv2.duckdns.org
                         │
                         ▼
              VPS nuevo: vision-broker (FastAPI + vip.py)
                         │
                         ▼
                    Portal / CDN
```

- **VPS viejo (Oracle):** catálogo, `/v1/index`, `/v1/search`, Postgres,
  `streaming-sync`, juegos (`party.visionplusv2.duckdns.org`), estáticos y demás
  proyectos. No toca nada de eso.
- **VPS nuevo:** solo el broker. Sin base de datos, sin catálogo, sin metadatos.
  Guarda únicamente `API_KEY`, `CONFIG_ADMIN_KEY` y la cuenta (`VIP_*`).
- La app sigue usando el mismo dominio; Caddy del viejo reenvía **solo**
  `/v1/play/vip` y `/v1/vip/*` al nuevo.
- Si Oracle suspende el VPS viejo, el broker y la cuenta sobreviven en el nuevo,
  pero el catálogo/metadatos dejan de responder hasta migrarlos. Este paquete es
  intencionalmente quirúrgico: primero protege la cuenta y el broker.

## 2. Requisitos del VPS nuevo

- 1 vCPU, 512 MB–1 GB RAM, 10 GB disco. El broker casi no consume.
- Docker + plugin `docker compose`.
- Caddy con los puertos 80/443 libres.
- IP pública fija.
- Acceso SSH por llave.

## 3. Paquete de migración

En el equipo local ya está listo:

```
migration/vision-broker/
├── api/                 # vip.py, portal.py, wire.py
├── broker/              # app.py, Dockerfile, compose, Caddyfile, .env
└── README.md
```

`broker/.env` contiene los secretos reales. **No lo subas a Git** y bórralo del
equipo cuando termines. Guarda una copia cifrada fuera de los dos VPS.

## 4. Desplegar el VPS nuevo

1. Copia el paquete:

   ```bash
   scp -r migration/vision-broker root@IP_NUEVO:/root/
   ssh root@IP_NUEVO
   ```

2. Ejecuta:

   ```bash
   cd /root/vision-broker
   bash broker/deploy.sh
   ```

   El script:
   - copia `api/` y `broker/` a `/opt/vision-broker/`;
   - deja `broker/.env` en `600`;
   - levanta `vision-broker` en `127.0.0.1:8200`;
   - agrega el bloque de Caddy si no existe y recarga;
   - prueba `/healthz`.

3. Si el VPS nuevo usa `firewalld`/`ufw`, abre 80/443/22 y deja 8200 solo local:

   ```bash
   # Oracle Linux / firewalld
   sudo firewall-cmd --permanent --add-service=http --add-service=https --add-service=ssh
   sudo firewall-cmd --reload
   # Ubuntu / ufw
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   ```

4. En DuckDNS crea el subdominio `broker.visionplusv2` con el IP del VPS nuevo.
   Si DuckDNS no permite ese nombre, usa otro (por ejemplo
   `visionbroker.duckdns.org`) y cambia el dominio en `broker/Caddyfile` y en el
   snippet del VPS viejo.

5. Verifica el certificado y la salud:

   ```bash
   curl -fsS https://broker.visionplusv2.duckdns.org/healthz
   # {"status":"ok"}
   ```

## 5. Verificar el broker nuevo antes de cortar

En el VPS nuevo:

```bash
cd /opt/vision-broker/broker
API_KEY=$(grep '^API_KEY=' .env | cut -d= -f2-)

curl -sS -X POST https://broker.visionplusv2.duckdns.org/v1/play/vip \
  -H "X-Api-Key: $API_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"contentId":"34A87B9B119DBBF7CC7A991F826FF386","seriesContentId":"34A87B9B119DBBF7CC7A991F826FF386","columnId":79715,"type":"0","episodeNumberList":[1]}' \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print([(m["q"],m["e"]) for m in d["medias"]])'
```

Debe imprimir `1080p` primero. También puedes consultar el semáforo con la
`CONFIG_ADMIN_KEY`:

```bash
ADMIN=$(grep '^CONFIG_ADMIN_KEY=' .env | cut -d= -f2-)
curl -sS https://broker.visionplusv2.duckdns.org/v1/vip/status \
  -H "X-Admin-Key: $ADMIN" | python3 -m json.tool
```

## 6. Corte en el VPS viejo

1. Respalda Caddy y `.env`:

   ```bash
   sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak-$(date +%F-%H%M)
   cp /opt/streaming-backend/.env /opt/streaming-backend/.env.bak-broker-$(date +%F-%H%M)
   ```

2. Agrega el snippet **dentro** del bloque `visionplusv2.duckdns.org { ... }`,
   antes del `handle { reverse_proxy 127.0.0.1:8100 }` final:

   ```caddyfile
   handle /v1/play/vip {
       reverse_proxy https://broker.visionplusv2.duckdns.org
   }
   handle /v1/vip/* {
       reverse_proxy https://broker.visionplusv2.duckdns.org
   }
   ```

3. Valida y recarga:

   ```bash
   sudo caddy validate --config /etc/caddy/Caddyfile
   sudo systemctl reload caddy
   ```

4. Prueba desde el dominio de siempre:

   ```bash
   API_KEY=$(grep '^API_KEY=' /opt/streaming-backend/.env | cut -d= -f2-)
   curl -sS -X POST https://visionplusv2.duckdns.org/v1/play/vip \
     -H "X-Api-Key: $API_KEY" -H 'Content-Type: application/json' \
     -d '{"contentId":"34A87B9B119DBBF7CC7A991F826FF386","seriesContentId":"34A87B9B119DBBF7CC7A991F826FF386","columnId":79715,"type":"0","episodeNumberList":[1]}' \
     | python3 -c 'import json,sys; print([(m["q"],m["e"]) for m in json.load(sys.stdin)["medias"]])'
   ```

5. En el VPS nuevo confirma que el request llegó:

   ```bash
   docker logs -f vision-broker
   ```

6. Prueba un título VIP desde la app/TV. Debe entrar en 1080p.

## 7. Quitar la cuenta del VPS viejo

Cuando el corte esté validado, deja el VPS viejo sin la cuenta:

```bash
cd /opt/streaming-backend
# Guarda una copia cifrada del .env actual antes de tocarlo.
python3 - <<'PY'
from pathlib import Path
p = Path('/opt/streaming-backend/.env')
lines = []
for raw in p.read_text().splitlines():
    k = raw.split('=', 1)[0].strip() if '=' in raw and not raw.strip().startswith('#') else ''
    if k in ('VIP_EMAIL', 'VIP_PASSWORD', 'VIP_PASSWORD_HASH'):
        lines.append(f'{k}=')
    else:
        lines.append(raw)
p.write_text('\n'.join(lines).rstrip() + '\n')
p.chmod(0o600)
PY
docker compose up -d api
```

Con eso, el VPS viejo ya no puede firmar con la cuenta. El broker nuevo es el
único que la tiene. El catálogo y los juegos siguen funcionando.

## 8. Rollback

Si algo falla antes de borrar la cuenta del viejo:

1. En el VPS viejo, quita el snippet de Caddy o comenta las rutas
   `/v1/play/vip` y `/v1/vip/*`.
2. Valida y recarga Caddy. La app vuelve al endpoint local del viejo (que aún
   tiene la cuenta en `.env` y `vip.py` con login por correo).
3. Si ya borraste la cuenta del viejo, restaura el `.env` desde el respaldo
   cifrado y reinicia `api`.
4. Si el DNS del subdominio del broker está mal, corrige DuckDNS o cambia el
   `reverse_proxy` al IP/puerto correcto.

## 9. Seguridad

- `broker/.env` en `600`; nunca en Git ni en el APK.
- El puerto 8200 solo en `127.0.0.1`; el público es Caddy (80/443).
- Si puedes, limita el 443 del VPS nuevo al IP del VPS viejo. El 80 debe quedar
  abierto para el certificado.
- Rota `API_KEY`/`CONFIG_ADMIN_KEY` si alguna vez se filtran; la app tendría que
  actualizarse si cambias la `API_KEY`.
- Mantén el VPS nuevo con actualizaciones automáticas y backups del `.env`.
- El broker no guarda catálogo ni base de datos: si se compromete, el daño se
  limita a la cuenta y a las licencias que pueda firmar.

## 10. Qué NO migra este paquete

- Catálogo, Postgres, `streaming-sync`, EPG, marcadores.
- `party.visionplusv2.duckdns.org` y los juegos.
- Los demás proyectos del VPS viejo.

Eso se queda en Oracle. Si más adelante quieres mover también el catálogo, se
hace aparte con un dump de `catalog_db` y el bloque de Caddy completo.
