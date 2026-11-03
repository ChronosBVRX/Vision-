#!/usr/bin/env python3
"""Servidor local de la beta Vision+ Fiesta 3.0 (sin Postgres).

Solo expone el contrato `/v1/game/*` que usa la app para los seis juegos nuevos.
Se levanta en la LAN y la beta apunta a `http://<IP-del-PC>:8100`.

Uso:
    cd server/api
    ../../.venv/bin/uvicorn fiesta_beta_server:app --host 0.0.0.0 --port 8100
"""

from __future__ import annotations

import os
import time
from typing import Any

from fastapi import FastAPI, Header, HTTPException, Request, Response

import fiesta
import games

API_KEY = os.environ.get(
    "FIESTA_API_KEY",
    "fb2244441f690185f861470423afb43278c3b19d31b14115",
)
ROOMS: dict[str, games.Room] = {}
APP = FastAPI(title="Vision+ Fiesta beta", version="3.0.0-beta1")


def require_key(value: str | None) -> None:
    if not value or value != API_KEY:
        raise HTTPException(status_code=401, detail="API key inválida")


def _code(raw: str) -> str:
    code = games.normalize_code(raw)
    if not code:
        raise HTTPException(status_code=400, detail="código inválido")
    return code


def _room(raw: str) -> games.Room:
    code = _code(raw)
    room = ROOMS.get(code)
    if room is None:
        raise HTTPException(status_code=404, detail="sala no encontrada")
    return room


def _player(room: games.Room, token: str | None) -> games.Player:
    player = room.player_by_token(token)
    if player is None:
        raise HTTPException(status_code=401, detail="jugador no autorizado")
    return player


def _host(room: games.Room, token: str | None) -> None:
    if not room.host_ok(token):
        raise HTTPException(status_code=401, detail="host no autorizado")


def _payload(room: games.Room, role: str, since: int, player_id: str | None) -> dict[str, Any] | Response:
    now = time.time()
    room.tick(now)
    if room.phase == "closed":
        ROOMS.pop(room.code, None)
        raise HTTPException(status_code=404, detail="sala cerrada")
    snap = room.snapshot(role, since, now, player_id)
    if snap is None:
        return Response(status_code=204)
    return snap


@APP.get("/v1/game/health")
def health(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    return {"ok": True, "rooms": len(ROOMS), "games": list(fiesta.FIESTA_GAMES)}


@APP.get("/v1/game/catalog")
def catalog(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    return {"games": fiesta.catalog()}


@APP.get("/v1/game/packs")
def packs(x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    return {"packs": []}


@APP.post("/v1/game/rooms")
async def create(request: Request, x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    body = await request.json()
    game = str(body.get("game") or "").strip().lower()
    if game not in fiesta.FIESTA_GAMES:
        raise HTTPException(status_code=400, detail="juego inválido")
    mode = str(body.get("mode") or "display").strip().lower()
    if mode not in games.MODES:
        raise HTTPException(status_code=400, detail="modo inválido")
    rounds = int(body.get("rounds") or fiesta.FIESTA_INFO[game]["rounds"])
    family = bool(body.get("family", True))
    code = ""
    for _ in range(12):
        candidate = games.generate_code()
        if candidate not in ROOMS:
            code = candidate
            break
    if not code:
        raise HTTPException(status_code=503, detail="sin códigos")
    host_token = games.new_token()
    player_token = games.new_token() if mode != "display" else ""
    now = time.time()
    room = games.create_room(
        code, str(body.get("hostName") or "Pantalla"), host_token, player_token, [],
        game=game, mode=mode, rounds=rounds, family_mode=family, now=now,
    )
    ROOMS[code] = room
    role = "display" if mode == "display" else "player"
    return {
        "code": code,
        "displayCode": games.format_code(code),
        "hostToken": host_token,
        "playerToken": player_token,
        "playerId": room.host_id,
        "hostId": room.host_id,
        "mode": mode,
        "game": game,
        "rounds": room.rounds,
        "expiresAt": int((now + games.ROOM_TTL_SECONDS) * 1000),
        "snapshot": room.snapshot(role, 0, now, room.host_id, display_authorized=True),
    }


@APP.post("/v1/game/rooms/{code}/join")
async def join(code: str, request: Request, x_api_key: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    body = await request.json()
    token = games.new_token()
    player = room.add_player(str(body.get("name") or "Jugador"), token, time.time(),
                             spectator=bool(body.get("spectator")))
    if player is None:
        raise HTTPException(status_code=409, detail="no se pudo unir")
    return {
        "playerToken": token,
        "playerId": player.id,
        "snapshot": room.snapshot("player", 0, time.time(), player.id),
    }


@APP.post("/v1/game/rooms/{code}/rejoin")
async def rejoin(code: str, request: Request, x_api_key: str | None = Header(default=None),
                 x_game_player: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    player = _player(room, x_game_player)
    room.rejoin(x_game_player or "", time.time())
    return {"playerToken": x_game_player, "playerId": player.id,
            "snapshot": room.snapshot("player", 0, time.time(), player.id)}


@APP.get("/v1/game/rooms/{code}/state")
def state(code: str, since: int = 0, role: str = "player",
          x_api_key: str | None = Header(default=None),
          x_game_player: str | None = Header(default=None),
          x_game_host: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    if role not in games.ROLES:
        raise HTTPException(status_code=400, detail="rol inválido")
    player = None
    now = time.time()
    if role == "player":
        player = _player(room, x_game_player)
        player.connected = True
        player.last_seen = now
    room.touch(now)
    display_authorized = bool(role == "display" and room.host_ok(x_game_host))
    return _payload(room, role, since, player.id if player else None)


@APP.post("/v1/game/rooms/{code}/action")
async def action(code: str, request: Request,
                 x_api_key: str | None = Header(default=None),
                 x_game_player: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    player = _player(room, x_game_player)
    now = time.time()
    player.connected = True
    player.last_seen = now
    room.touch(now)
    body = await request.json()
    round_no = int(body.get("round") or 0)
    kind = str(body.get("kind") or "").strip().lower()
    now = time.time()
    room.tick(now)
    accepted, reason = room.act(
        player.id, round_no, kind, now,
        text=str(body.get("text") or ""),
        target=str(body.get("target") or ""),
        strokes=body.get("strokes"),
        index=body.get("index", 0),
        amount=body.get("amount", 0),
        bid=body.get("bid", 0),
    )
    ignored = bool(
        not accepted and reason in {"duplicada", "fase", "ronda", "turno", "duelo"}
    )
    return {"accepted": accepted, "ignored": ignored, "reason": reason,
            "phase": room.phase, "receivedAt": int(now * 1000),
            "points": int(player.score)}


@APP.post("/v1/game/rooms/{code}/ready")
async def ready(code: str, request: Request,
                x_api_key: str | None = Header(default=None),
                x_game_player: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    player = _player(room, x_game_player)
    body = await request.json()
    ok = room.ready(player.id, int(body.get("round") or 0), time.time())
    return {"ok": ok, "ready": ok}


@APP.post("/v1/game/rooms/{code}/host/{action_name}")
async def host_action(code: str, action_name: str, request: Request,
                      x_api_key: str | None = Header(default=None),
                      x_game_host: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    _host(room, x_game_host)
    now = time.time()
    room.tick(now)
    if action_name == "start":
        if not room.start(now):
            raise HTTPException(status_code=409, detail="no se pudo iniciar")
    elif action_name == "next":
        if not room.next_round(now):
            raise HTTPException(status_code=409, detail="no se puede avanzar")
    elif action_name == "close":
        room.close(now, "host")
        ROOMS.pop(room.code, None)
        return {"ok": True, "closed": True}
    elif action_name == "kick":
        body = await request.json()
        if not room.kick(str(body.get("playerId") or ""), now):
            raise HTTPException(status_code=404, detail="jugador no encontrado")
    else:
        raise HTTPException(status_code=404, detail="acción desconocida")
    return {"ok": True, "phase": room.phase, "rev": room.rev}


@APP.post("/v1/game/rooms/{code}/reaction")
async def reaction(code: str, request: Request,
                   x_api_key: str | None = Header(default=None),
                   x_game_player: str | None = Header(default=None)):
    require_key(x_api_key)
    room = _room(code)
    player = _player(room, x_game_player)
    body = await request.json()
    ok = room.react(player.id, int(body.get("kind") or 0), time.time())
    return {"ok": ok}


@APP.post("/v1/game/rooms/{code}/report")
async def report(code: str, request: Request,
                 x_api_key: str | None = Header(default=None),
                 x_game_player: str | None = Header(default=None)):
    require_key(x_api_key)
    _room(code)
    _player(_room(code), x_game_player)
    return {"ok": True, "flagged": True}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(APP, host="0.0.0.0", port=8100)

app = APP
