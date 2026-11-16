"""Broker FHD aislado: solo firma licencias, sin catálogo ni base de datos.

Se despliega en un VPS pequeño aparte del catálogo. La app sigue llamando a
`/v1/play/vip` en el dominio de siempre; Caddy del VPS viejo reenvía solo esas
rutas aquí. Así la cuenta y el broker quedan fuera del servidor que sirve
metadatos/juegos.
"""

from __future__ import annotations

import asyncio
import logging
import os

from fastapi import FastAPI, Header, HTTPException, Request

import vip

log = logging.getLogger("visionplus.broker")

API_KEY = os.environ.get("API_KEY", "").strip()
CONFIG_ADMIN_KEY = os.environ.get("CONFIG_ADMIN_KEY", "").strip()

app = FastAPI(
    title="Vision+ Broker",
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)


def _api_ok(key: str | None) -> bool:
    return bool(API_KEY) and key == API_KEY


def _admin_ok(key: str | None) -> bool:
    return bool(CONFIG_ADMIN_KEY) and key == CONFIG_ADMIN_KEY


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok"}


@app.post("/v1/play/vip")
async def play_vip(request: Request, x_api_key: str | None = Header(default=None)):
    """Firma la licencia FHD de un título con la cuenta del broker."""
    if not _api_ok(x_api_key):
        raise HTTPException(status_code=401, detail="API key inválida")
    try:
        body = await request.json()
    except Exception:  # noqa: BLE001 - JSON ajeno
        raise HTTPException(status_code=400, detail="JSON inválido")

    content_id = str(body.get("contentId") or "").strip()
    if not content_id or len(content_id) > 64:
        raise HTTPException(status_code=400, detail="contentId inválido")
    series_id = str(body.get("seriesContentId") or "").strip()[:64]
    try:
        column = int(body.get("columnId") or 0)
    except (TypeError, ValueError):
        column = 0
    kind = str(body.get("type") or "0")
    episodes = body.get("episodeNumberList")
    if not isinstance(episodes, list):
        episodes = [1]

    try:
        payload = await asyncio.wait_for(
            asyncio.to_thread(vip.play, content_id, series_id, column, kind, episodes),
            timeout=25,
        )
    except vip.AccountNoDays as exc:
        log.warning("broker sin días: %s", exc)
        raise HTTPException(status_code=409, detail="cuenta sin días")
    except vip.AccountMismatch as exc:
        log.warning("broker cuenta: %s", exc)
        raise HTTPException(status_code=409, detail="cuenta mal configurada")
    except Exception as exc:  # noqa: BLE001 - la app cae a su flujo local
        log.warning("broker %s: %s", content_id, exc)
        raise HTTPException(status_code=502, detail="sin licencia")
    return payload


@app.get("/v1/vip/status")
async def vip_status(x_admin_key: str | None = Header(default=None)):
    """Semáforo de la cuenta (solo operador)."""
    if not _admin_ok(x_admin_key):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    data = await asyncio.to_thread(vip.status)
    days = data.get("daysLeft")
    data["alarm"] = (
        bool(data.get("mismatch"))
        or data.get("entitled") is False
        or (isinstance(days, int) and days < 5)
    )
    return data


@app.post("/v1/vip/probe")
async def vip_probe(x_admin_key: str | None = Header(default=None)):
    """Valida la cuenta de verdad: acuña y firma el título de sonda."""
    if not _admin_ok(x_admin_key):
        raise HTTPException(status_code=403, detail="llave de administración inválida")
    try:
        return await asyncio.wait_for(asyncio.to_thread(vip.probe), timeout=45)
    except vip.AccountNoDays as exc:
        raise HTTPException(status_code=409, detail=f"cuenta sin días: {exc}")
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=502, detail=f"sin licencia: {exc}")
