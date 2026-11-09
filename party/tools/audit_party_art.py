#!/usr/bin/env python3
"""Auditoría del arte visual de Vision+ Party.

Comprueba portadas, copias de Android y fondos ambientales de host. No modifica
nada: solo reporta dimensiones, formato, peso y duplicados.

Uso:
  PY="/media/chronos/Disco Duro/vision-tts/venv/bin/python"
  "$PY" party/tools/audit_party_art.py --json /tmp/opencode/art-audit.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
COVERS = ROOT / "party" / "assets" / "covers"
ANDROID = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
AMBIENT_GAMES = [
    "pantomime", "schaetzorama", "imposter", "banterbolt",
    "merkissioner", "you-got-it", "dungeon-party",
]
COVER_IDS = [
    "banterbolt", "buzzwort", "card-table", "default", "dungeon-party",
    "flatterfluff", "imposter", "magic-arena", "merkissioner", "pantomime",
    "schaetzorama", "schattenjagd", "you-got-it", "zeichnen-und-erraten",
]
ANDROID_NAMES = {
    "banterbolt": "party_banterbolt.webp",
    "buzzwort": "party_buzzwort.webp",
    "card-table": "party_card_table.webp",
    "default": "party_default.webp",
    "dungeon-party": "party_dungeon_party.webp",
    "flatterfluff": "party_flatterfluff.webp",
    "imposter": "party_imposter.webp",
    "magic-arena": "party_magic_arena.webp",
    "merkissioner": "party_merkissioner.webp",
    "pantomime": "party_pantomime.webp",
    "schaetzorama": "party_schaetzorama.webp",
    "schattenjagd": "party_schattenjagd.webp",
    "you-got-it": "party_you_got_it.webp",
    "zeichnen-und-erraten": "party_zeichnen_und_erraten.webp",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", type=Path)
    args = parser.parse_args()
    failures: list[str] = []
    rows: list[dict] = []

    def check(path: Path, size: tuple[int, int], max_bytes: int, label: str) -> None:
        if not path.is_file():
            failures.append(f"FALTA {label}: {path}")
            return
        try:
            im = Image.open(path)
        except Exception as exc:  # noqa: BLE001
            failures.append(f"ILEGIBLE {label}: {exc}")
            return
        if im.size != size:
            failures.append(f"TAMAÑO {label}: {im.size} (esperado {size})")
        if im.mode not in ("RGB", "RGBA"):
            failures.append(f"MODO {label}: {im.mode}")
        if path.stat().st_size > max_bytes:
            failures.append(f"PESO {label}: {path.stat().st_size} B")
        rows.append({
            "kind": label, "file": str(path.relative_to(ROOT)),
            "width": im.width, "height": im.height, "mode": im.mode,
            "bytes": path.stat().st_size, "sha256": digest(path),
        })

    seen: dict[str, str] = {}
    for game in COVER_IDS:
        cover = COVERS / f"{game}.webp"
        check(cover, (600, 800), 260_000, f"cover/{game}")
        if cover.is_file():
            h = digest(cover)
            if h in seen:
                failures.append(f"DUPLICADA cover/{game} = {seen[h]}")
            seen[h] = f"cover/{game}"
        check(ANDROID / ANDROID_NAMES[game], (600, 800), 260_000, f"android/{game}")
        if cover.is_file() and (ANDROID / ANDROID_NAMES[game]).is_file():
            if digest(cover) != digest(ANDROID / ANDROID_NAMES[game]):
                failures.append(f"ANDROID DESINCRONIZADA {game}")

    for game in AMBIENT_GAMES:
        check(
            ROOT / "party" / "games" / game / "public" / "host" / game / "ambient.webp",
            (1344, 768), 180_000, f"ambient/{game}",
        )

    # Arte de juego existente: solo verifica que abre y tiene tamaño razonable.
    extra = [
        ("flatterfluff", "sugar-prairie.png"),
        ("flatterfluff", "characters.png"),
        ("schattenjagd", "city-day.webp"),
        ("schattenjagd", "city-night.webp"),
        ("magic-arena", "action-art-atlas.png"),
        ("card-table", "symboljagd-atlas.png"),
    ]
    for game, name in extra:
        base = ROOT / "party" / "games" / game / "public"
        matches = list(base.rglob(name))
        if not matches:
            failures.append(f"FALTA extra/{game}/{name}")
            continue
        try:
            im = Image.open(matches[0])
            if im.width < 600 or im.height < 600:
                failures.append(f"PEQUEÑO extra/{game}/{name}: {im.size}")
            rows.append({
                "kind": f"extra/{game}", "file": str(matches[0].relative_to(ROOT)),
                "width": im.width, "height": im.height, "mode": im.mode,
                "bytes": matches[0].stat().st_size, "sha256": digest(matches[0]),
            })
        except Exception as exc:  # noqa: BLE001
            failures.append(f"ILEGIBLE extra/{game}/{name}: {exc}")

    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps({
            "covers": len(COVER_IDS), "ambient": len(AMBIENT_GAMES),
            "checked": len(rows), "failures": failures, "items": rows,
        }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    for failure in failures:
        print(f"FALLA {failure}")
    print(f"arte: {len(rows)} archivos revisados; {len(failures)} fallas")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
