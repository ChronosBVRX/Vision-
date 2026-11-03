#!/usr/bin/env python3
"""Humo de Vision+ Play: 8 jugadores simulados contra la API real por HTTP.

No usa dependencias: solo stdlib. Sirve para comprobar de punta a punta que
crear sala -> unirse -> empezar -> responder -> reveal -> podio funciona.

Uso (con la API local en 8100 y el banco cargado):

    python server/tests/smoke_games.py --base-url http://127.0.0.1:8100 --api-key dev

Códigos de salida: 0 si la partida terminó con 8 jugadores y podio; 1 si algo
falló (imprime el detalle).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

PLAYERS = ("Beto", "Caro", "Dani", "Eva", "Fabián", "Gaby", "Hugo")


class Api:
    def __init__(self, base: str, key: str):
        self.base = base.rstrip("/")
        self.key = key

    def call(self, method: str, path: str, body: dict | None = None,
             headers: dict | None = None) -> tuple[int, dict | None]:
        data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("X-Api-Key", self.key)
        request.add_header("Accept", "application/json")
        if data is not None:
            request.add_header("Content-Type", "application/json")
        for name, value in (headers or {}).items():
            request.add_header(name, value)
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                raw = response.read()
                return response.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                payload = json.loads(raw) if raw else None
            except ValueError:
                payload = {"detail": raw.decode("utf-8", "replace")[:200]}
            return error.code, payload

    def ok(self, method: str, path: str, body: dict | None = None,
           headers: dict | None = None) -> dict:
        status, payload = self.call(method, path, body, headers)
        if status not in (200, 201, 204):
            raise RuntimeError(f"{method} {path} -> HTTP {status}: {payload}")
        return payload or {}


def wait_phase(api: Api, code: str, since: int, wanted: str, deadline: float) -> tuple[dict, int]:
    """Sondea el snapshot de pantalla hasta la fase pedida (o lanza timeout)."""
    last = since
    while time.time() < deadline:
        status, snap = api.call(
            "GET", f"/v1/game/rooms/{code}/state?role=display&since={last}"
        )
        if status == 204:
            time.sleep(0.15)
            continue
        if status != 200 or not snap:
            raise RuntimeError(f"state -> HTTP {status}: {snap}")
        last = int(snap["rev"])
        if snap["phase"] == wanted:
            return snap, last
        if snap["phase"] in ("podium", "closed"):
            return snap, last
        time.sleep(0.15)
    raise TimeoutError(f"la sala no llegó a {wanted}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8100")
    parser.add_argument("--api-key", default="dev")
    parser.add_argument("--players", type=int, default=8)
    parser.add_argument("--rounds", type=int, default=8)
    parser.add_argument("--question-seconds", type=int, default=5)
    parser.add_argument("--sync-code", default="")
    parser.add_argument("--timeout", type=float, default=180.0)
    args = parser.parse_args()

    api = Api(args.base_url, args.api_key)
    headers_sync = {"X-Sync-Code": args.sync_code} if args.sync_code else {}
    created = api.ok("POST", "/v1/game/rooms", {
        "mode": "remote",
        "hostName": "Anfitrión",
        "rounds": args.rounds,
        "questionSeconds": args.question_seconds,
        "rulesVersion": 1,
    }, headers_sync)
    code = created["code"]
    host_token = created["hostToken"]
    host_player = created["playerToken"]
    host_id = created["playerId"]
    print(f"sala {created['displayCode']} ({code}) · {created['rounds']} rondas")

    players = [("Anfitrión", host_player, host_id)]
    for index, name in enumerate(PLAYERS[: max(0, args.players - 1)]):
        joined = api.ok("POST", f"/v1/game/rooms/{code}/join", {"name": name})
        players.append((name, joined["playerToken"], joined["playerId"]))
        print(f"  unido {name} ({joined['playerId']})")
    if len(players) != args.players:
        raise RuntimeError(f"se esperaban {args.players} jugadores y hay {len(players)}")

    api.ok("POST", f"/v1/game/rooms/{code}/host/start", {}, {"X-Game-Host": host_token})
    deadline = time.time() + args.timeout
    since = 0
    answered: set[int] = set()
    saw_reveal = False
    rounds_seen: set[int] = set()
    while True:
        snap, since = wait_phase(api, code, since, "question", deadline)
        if snap["phase"] == "podium":
            break
        round_no = int(snap["round"])
        rounds_seen.add(round_no)
        if round_no not in answered:
            for index, (_name, token, _pid) in enumerate(players):
                status, answer = api.call(
                    "POST", f"/v1/game/rooms/{code}/answer",
                    {"round": round_no, "option": (index + round_no) % 4},
                    {"X-Game-Player": token},
                )
                if status != 200 or not answer or not answer.get("accepted"):
                    # Una respuesta tardía no invalida el humo: el servidor debe
                    # rechazarla. Solo se informa.
                    print(f"  aviso: {_name} ronda {round_no} -> {status} {answer}")
            answered.add(round_no)
            # El snapshot del display no puede llevar la respuesta en `question`.
            if snap.get("answer") is not None:
                raise RuntimeError("el snapshot de pregunta filtró la respuesta")
            if not snap.get("question") or len(snap["question"]["options"]) != 4:
                raise RuntimeError("la pregunta no trae 4 opciones")

        reveal, since = wait_phase(api, code, since, "reveal", deadline)
        if reveal["phase"] == "podium":
            break
        if not saw_reveal:
            # En reveal sí debe venir la opción correcta; y el player su resultado.
            player_snap = api.ok(
                "GET", f"/v1/game/rooms/{code}/state?role=player&since=0",
                headers={"X-Game-Player": players[1][1]},
            )
            if not player_snap.get("answer") or "option" not in player_snap["answer"]:
                raise RuntimeError("el reveal no trae la respuesta correcta")
            if "you" not in player_snap:
                raise RuntimeError("el snapshot del jugador no trae su estado")
            question_id = reveal.get("question", {}).get("id", "")
            if question_id:
                api.ok(
                    "POST", f"/v1/game/rooms/{code}/report",
                    {"questionId": question_id, "reason": "humo"},
                    {"X-Game-Player": players[1][1]},
                )
            saw_reveal = True
        # Cortar la espera del reveal; si el servidor ya avanzó, el 409 es normal.
        api.call("POST", f"/v1/game/rooms/{code}/host/next", {}, {"X-Game-Host": host_token})

    final = api.ok("GET", f"/v1/game/rooms/{code}/state?role=display&since=0")
    if final["phase"] != "podium":
        raise RuntimeError(f"la partida terminó en {final['phase']}, no en podio")
    if len(final["players"]) != args.players:
        raise RuntimeError(f"el podio tiene {len(final['players'])} jugadores")
    if len(rounds_seen) != args.rounds:
        raise RuntimeError(f"se jugaron {len(rounds_seen)} rondas de {args.rounds}")

    # 204 real cuando el cliente ya está al día.
    status, _ = api.call(
        "GET", f"/v1/game/rooms/{code}/state?role=display&since={final['rev']}"
    )
    if status != 204:
        raise RuntimeError(f"el state al día debía ser 204 y fue {status}")

    api.ok("POST", f"/v1/game/rooms/{code}/host/close", {}, {"X-Game-Host": host_token})
    health = api.ok("GET", "/v1/game/health")
    print("\nPodio:")
    for position, player in enumerate(final["players"], 1):
        print(f"  {position}. {player['name']:<12} {player['score']:>5} puntos")
    print(f"\nOK: {len(rounds_seen)} rondas · {len(final['players'])} jugadores · "
          f"preguntas en banco {health.get('questions')} · salas {health.get('rooms')}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:  # noqa: BLE001
        print(f"FALLO: {error}", file=sys.stderr)
        raise SystemExit(1)
