#!/usr/bin/env python3
"""Exporta TODO el estado de Vision+ Fiesta para análisis externo.

Salida: docs/fiesta_dossier_data.json

Incluye:
  * info pública de los seis juegos (jugadores, rondas, moneda, tienda);
  * fases, duraciones y pantallas;
  * fórmulas de puntuación y economía;
  * giros, tienda, soplos, burlas y ruleta;
  * contenido completo: prompts, encuestas, eventos, escenas, camisetas y objetos;
  * líneas de voz por momento, banter y burlas;
  * inventario de arte, audio y pruebas.

No toca el APK ni el servidor. Es solo un volcado reproducible.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SERVER = ROOT / "server" / "api"
sys.path.insert(0, str(SERVER))

import fiesta  # noqa: E402
import fiesta_content as content  # noqa: E402

OUT = ROOT / "docs" / "fiesta_dossier_data.json"
GAMES = fiesta.FIESTA_GAMES

CONTENT = {
    "ultima_palabra": ("ULTIMA_PALABRA_PROMPTS", "prompts"),
    "vox_populi": ("VOX_QUESTIONS", "surveys"),
    "fuera_de_epoca": ("TIME_EVENTS", "events"),
    "texto_roto": ("TEXTO_ROTO_SCENES", "scenes"),
    "franela_fest": ("FRANELA_PROMPTS", "shirts"),
    "el_tasador": ("TASADOR_OBJECTS", "objects"),
}

USED_MOMENTS = {
    "ultima_palabra": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "vox_populi": ("lobby", "start", "guess", "reveal", "podium"),
    "fuera_de_epoca": ("lobby", "start", "guess", "reveal", "podium"),
    "texto_roto": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "franela_fest": ("lobby", "start", "create", "vote", "reveal", "podium"),
    "el_tasador": ("lobby", "start", "pitch", "vote", "reveal", "podium"),
}

PHASE_FLOW = {
    "ultima_palabra": ["lobby", "countdown", "write (55 s)", "duel (20 s por pareja)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
    "vox_populi": ["lobby", "countdown", "guess (45 s)", "vote/HILO (25 s)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
    "fuera_de_epoca": ["lobby", "countdown", "choice/guess (45 s)", "sort (50 s)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
    "texto_roto": ["lobby", "countdown", "chat (3 × 18 s = 54 s)", "vote (30 s)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
    "franela_fest": ["lobby", "countdown", "create/draw (55 s)", "remix (40 s)", "vote (30 s)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
    "el_tasador": ["lobby", "countdown", "pitch (50 s)", "invest (30 s)", "reveal (9 s)", "bonus si aplica", "siguiente ronda", "podium"],
}

SCORING = {
    "ultima_palabra": "Duelo A/B: 150 × votos recibidos × multiplicador al autor; +250 × multiplicador al ganador; +300 × multiplicador si el perdedor recibe 0 votos y hay ≥2 votos (Nora-KO).",
    "vox_populi": "Guess: 700, 600, 500, 400, 300, 200 según ranking (mínimo 100) × multiplicador. HILO: +250 × multiplicador por acertar arriba/abajo.",
    "fuera_de_epoca": "Orden exacto: 600 + 100 × (racha-1); orden adyacente: 250; orden invertido: 0; × multiplicador.",
    "texto_roto": "Autor: (100 + 300 × votos recibidos) × multiplicador; +200 × multiplicador si es la línea más votada. Reacción: +25 al autor y +10 monedas al reactor.",
    "franela_fest": "Remixer: 200 × votos; arte original: 100 × votos; eslogan original: 100 × votos; todo × multiplicador.",
    "el_tasador": "Vendedor: recibe las pujas de su lote; +200 × multiplicador si es falso y recauda >250. Comprador: max(0, valor real − puja) × multiplicador.",
}

ECONOMY = {
    "start_coins": fiesta.FIESTA_START_COINS,
    "bet_options": [100, 250, 500],
    "bet_payout": "Solo paga el Top 50% de la ronda: x2 si queda dentro; x3 si además es el #1; 0 fuera del corte. Con un solo jugador exige ≥300 puntos.",
    "hint_cost": fiesta.HINT_COST,
    "hint_scope": "1 por jugador y ronda. Pista de texto; en Fuera de Época apaga 2 años; en El Tasador da rango de valor.",
    "taunt_cost": fiesta.TAUNT_COST,
    "taunt_scope": "1 por jugador y ronda; solo frases de la lista del juego; se publica en la tele.",
    "perks": list(fiesta.PERKS),
    "bonus_every": fiesta.BONUS_EVERY,
    "bonus_min_rounds": fiesta.BONUS_ROUNDS_MIN,
    "bonus": {
        "title": "La Ruleta de Fiesta",
        "options": 3,
        "payouts": [
            {"probability": 0.12, "coins": 500, "points": 200, "label": "Jackpot"},
            {"probability": 0.30, "coins": 250, "points": 0, "label": "Buen botín"},
            {"probability": 0.36, "coins": 120, "points": 100, "label": "Cofre sorpresa"},
            {"probability": 0.22, "coins": 40, "points": 0, "label": "Cofre vacío"},
        ],
    },
    "final_round_multiplier": 2.0,
}


def content_payload(game: str) -> dict:
    attr, key = CONTENT[game]
    items = list(getattr(content, attr))
    safe = [item for item in items if not item.get("adult")]
    adult = [item for item in items if item.get("adult")]
    return {
        "source_attr": attr,
        "key": key,
        "total": len(items),
        "family": len(safe),
        "adult": len(adult),
        "items": items,
    }


def host_payload(game: str) -> dict:
    moments = {}
    for moment in USED_MOMENTS[game]:
        moments[moment] = list(content.host_lines(game, moment))
    return {
        "host": fiesta.HOST_BY_GAME[game],
        "host_name": fiesta.FIESTA_INFO[game]["title"],
        "moments": moments,
        "total_lines": sum(len(v) for v in moments.values()),
        "banter": {k: list(v) for k, v in fiesta.BANTER_BY_GAME.get(game, {}).items()},
        "taunts": list(fiesta.TAUNTS.get(game, ())),
        "hint": fiesta.HINTS.get(game, ""),
    }


def audio_inventory() -> dict:
    raw = ROOT / "app/src/main/res/raw"
    voices = sorted(p.name for p in raw.glob("fiesta_*_[0-4].m4a"))
    music = sorted(p.name for p in raw.glob("fiesta_music_*.m4a"))
    sting = sorted(p.name for p in raw.glob("fiesta_sting_*.m4a"))
    voices_dir = ROOT / "docs/assets/games/v3/voices"
    refs = {}
    refs_json = voices_dir / "refs.json"
    if refs_json.is_file():
        refs = json.loads(refs_json.read_text(encoding="utf-8"))
    return {
        "engine": "Qwen3-TTS-12Hz-1.7B-VoiceDesign + Base (clonación)",
        "voice_clips": len(voices),
        "variants_per_moment": 5,
        "moments": sum(len(v) for v in USED_MOMENTS.values()),
        "music_loops": music,
        "stingers": sting,
        "references": refs,
        "format": "AAC 64 kbps, 48 kHz, mono, loudnorm -16 LUFS, TP -2.5 dB",
    }


def art_inventory() -> dict:
    drawable = ROOT / "app/src/main/res/drawable-nodpi"
    names = {
        "stages": sorted(p.name for p in drawable.glob("fiesta_stage_*.webp")),
        "hosts": sorted(p.name for p in drawable.glob("fiesta_host_*.webp")),
        "phones": sorted(p.name for p in drawable.glob("fiesta_phone_*.webp")),
        "selectors": sorted(p.name for p in drawable.glob("game_*_poster.webp")),
        "lobby": [p.name for p in drawable.glob("fiesta_lobby_bg.webp")],
        "horizontal_cards": sorted(
            f"game_{game}_art.webp" for game in GAMES
            if (drawable / f"game_{game}_art.webp").is_file()
        ),
    }
    return {
        "pipeline": "ComfyUI + SDXL Juggernaut XL v9",
        "counts": {k: len(v) for k, v in names.items()},
        "files": names,
        "approved_masters": str(ROOT / "docs/assets/games/v3/premium/approved"),
        "quality_gate": "213/213 assets instalados, 19/19 maestros IA",
    }


def main() -> int:
    payload = {
        "product": "Vision+ Fiesta",
        "snapshot": "2026-11-02",
        "games": {},
        "economy": ECONOMY,
        "audio": audio_inventory(),
        "art": art_inventory(),
        "room": {
            "max_players": 8,
            "hard_max_players": 12,
            "audience": "espectadores separados; no reciben secretos",
            "room_ttl_hours": 4,
            "transport": "polling HTTP (~700 ms en fases activas, 1 s en el resto)",
            "server_authority": True,
        },
        "quality": {
            "backend_tests": 661,
            "android_tests": "suite completa en verde",
            "release": "assembleNormalRelease OK, verifyEngineNamespace 0 colisiones",
            "known_debt": [
                "cinco juegos siguen compartiendo el esqueleto escribir→votar→reveal",
                "no hay WebSocket/SSE; el chat no es tiempo real",
                "no hay minijuegos por juego más allá de la Ruleta de Fiesta",
                "no hay modo equipos ni audiencia interactiva",
                "faltan tutoriales propios por juego",
                "el backend de Fiesta no está desplegado en el VPS",
            ],
        },
    }
    for game in GAMES:
        info = dict(fiesta.FIESTA_INFO[game])
        payload["games"][game] = {
            "info": info,
            "phases": PHASE_FLOW[game],
            "durations": fiesta.DURATIONS.get(game, {}),
            "screen_by_phase": fiesta.SCREEN_BY_PHASE,
            "scoring": SCORING[game],
            "content": content_payload(game),
            "host": host_payload(game),
            "twists": list(fiesta.TWISTS_BY_GAME.get(game, fiesta.TWISTS)),
            "perks": list(fiesta.PERKS),
        }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"exportado: {OUT}")
    print(f"juegos: {len(payload['games'])} · voces: {payload['audio']['voice_clips']} · arte: {sum(payload['art']['counts'].values())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
