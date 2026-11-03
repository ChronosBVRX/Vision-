#!/usr/bin/env python3
"""Genera el arte de las tres tarjetas de Vision+ Play con ComfyUI local.

Salida: `app/src/main/res/drawable-nodpi/game_<id>_art.webp` (16:9, sin texto).
"""

from __future__ import annotations

import io
import os
import time
import urllib.parse
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("COMFY_URL", "http://127.0.0.1:8188")
ASSETS = ROOT / "app/src/main/res/drawable-nodpi"
DOCS = ROOT / "docs/assets/games/art"
TMP = Path("/tmp/opencode/game_art")
CHECKPOINT = "juggernautXL_v9Rundiffusion.safetensors"

NEG = (
    "text, letters, words, typography, logo, watermark, human, people, hands, faces, "
    "clutter, messy, low quality, blurry, deformed, duplicate, frame, border"
)

GAMES = {
    "trivia": (
        "cinematic game show stage, deep navy and electric blue neon, a glowing quiz podium, "
        "floating geometric question mark shapes made of light, volumetric spotlights, "
        "dark premium streaming key art, symmetrical composition, no text, no letters"
    ),
    "charadas": (
        "theatrical stage, warm orange and coral spotlights, red velvet curtains, "
        "a vintage microphone in the center, playful mysterious party game, "
        "dark premium streaming key art, cinematic composition, no text, no letters"
    ),
    "wager": (
        "mysterious game table, violet and electric blue neon, three glowing clue cards, "
        "poker chips and playing cards, smoke and rim light, dark premium streaming key art, "
        "cinematic composition, no text, no letters"
    ),
    "impostor": (
        "noir interrogation room turned party stage, one hard overhead lamp, long dramatic shadows, "
        "crimson neon rim light, silhouette of a mysterious guest behind frosted glass, "
        "hidden identity and social deduction, cinematic film still, premium streaming key art, "
        "deep blacks, no text, no letters, no logos"
    ),
    "dibujo": (
        "surreal dark art gallery at night, an empty glowing canvas on an easel, "
        "electric cyan neon outlines of absurd doodles floating in the air, "
        "cinematic haze and spotlights, playful mystery, premium streaming key art, "
        "no text, no letters, no logos"
    ),
    "subasta": (
        "secret underground auction house, golden gavel under a single spotlight, black velvet, "
        "mysterious locked crates and art deco gold details, cigarette smoke, "
        "dark cinematic noir film still, premium streaming key art, no text, no letters, no logos"
    ),
    "fibbage": (
        "vintage cinema archive at night, floating paper cards with illegible scribbles, "
        "an old film projector casting green and teal light, shelves of mysterious books, "
        "cinematic haze, premium streaming key art, dark moody, no text, no letters, no logos"
    ),
}

MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/art")
SALON_MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/art/salon")
SALON_DOCS = ROOT / "docs/assets/games/art/salon"
SALON_NEG = (
    "text, letters, typography, watermark, signature, logo, numbers, HUD, UI, "
    "blurry, lowres, oversaturated, cartoon, anime, 3d render plastic, bright daylight, "
    "white background, cluttered, deformed hands, extra fingers, frame, border"
)
SALON_ASSETS = [
    {
        "id": "game_auc_card_points", "gen_width": 768, "gen_height": 1152,
        "out_width": 512, "out_height": 768, "webp_quality": 82, "seed": 778500,
        "prompt": (
            "noir cinema prop illustration, a gleaming stack of golden 35mm film reels "
            "and a vintage cinema clapperboard resting on a dark velvet pedestal, "
            "warm golden neon rim lighting, deep shadows, dark slate background, "
            "centered composition, high contrast, sleek, no text"
        ),
    },
    {
        "id": "game_auc_card_coins", "gen_width": 768, "gen_height": 1152,
        "out_width": 512, "out_height": 768, "webp_quality": 82, "seed": 778541,
        "prompt": (
            "noir cinema prop illustration, an open vintage brass bank vault box overflowing "
            "with glowing golden tokens and casino chips, dramatic amber and cyan rim lighting, "
            "dark moody studio background, centered subject, cinematic texture, no text"
        ),
    },
    {
        "id": "game_auc_card_steal", "gen_width": 768, "gen_height": 1152,
        "out_width": 512, "out_height": 768, "webp_quality": 82, "seed": 778582,
        "prompt": (
            "noir heist illustration, a sleek black leather gloved hand emerging from shadows "
            "snatching a glowing golden trophy, crimson and magenta neon rim light, "
            "dramatic Venetian blind shadows, dark background, centered composition, no text"
        ),
    },
    {
        "id": "game_auc_card_mystery", "gen_width": 768, "gen_height": 1152,
        "out_width": 512, "out_height": 768, "webp_quality": 82, "seed": 778623,
        "prompt": (
            "noir mystery prop illustration, a locked dark mahogany and brass crate wrapped "
            "in heavy chains, emitting a mysterious purple and gold volumetric glow from the cracks, "
            "atmospheric fog, dark background, centered object, cinematic lighting, no text"
        ),
    },
    {
        "id": "game_auc_card_special", "gen_width": 768, "gen_height": 1152,
        "out_width": 512, "out_height": 768, "webp_quality": 82, "seed": 778664,
        "prompt": (
            "noir sci-fi prop illustration, an ornate metallic shield and an electric glowing "
            "hourglass fused together on a dark pedestal, intense cyan and gold neon rim lighting, "
            "sparks, dark moody background, centered composition, high contrast, no text"
        ),
    },
    {
        "id": "game_imp_role_crew", "gen_width": 1024, "gen_height": 576,
        "out_width": 640, "out_height": 360, "webp_quality": 80, "seed": 778705,
        "prompt": (
            "noir detective desk closeup, a metallic silver investigator badge, a vintage magnifying "
            "glass and a manila folder under cool cyan neon rim light, Venetian blind shadows, "
            "cinematic atmosphere, dark slate background, no text, no letters"
        ),
    },
    {
        "id": "game_imp_role_spy", "gen_width": 1024, "gen_height": 576,
        "out_width": 640, "out_height": 360, "webp_quality": 80, "seed": 778746,
        "prompt": (
            "noir thriller silhouette of a mysterious spy in a trenchcoat and fedora standing "
            "in a misty alleyway, dramatic crimson red neon rim lighting, deep black shadows, "
            "cinematic suspense, high contrast, no text"
        ),
    },
    {
        "id": "game_medal_liar", "gen_width": 1024, "gen_height": 1024,
        "out_width": 256, "out_height": 256, "webp_quality": 85, "seed": 778787,
        "prompt": (
            "stylized 3d metallic enamel pin badge of a theatrical comedy mask split with "
            "a neon green serpent tongue, gold rim, isolated on solid pitch black background, "
            "centered game achievement icon, dramatic studio lighting, no text"
        ),
    },
    {
        "id": "game_medal_detective", "gen_width": 1024, "gen_height": 1024,
        "out_width": 256, "out_height": 256, "webp_quality": 85, "seed": 778828,
        "prompt": (
            "stylized 3d metallic enamel pin badge of a brass magnifying glass with a glowing "
            "cyan neon eye inside the lens, isolated on solid pitch black background, "
            "centered game achievement icon, high contrast, no text"
        ),
    },
    {
        "id": "game_medal_shark", "gen_width": 1024, "gen_height": 1024,
        "out_width": 256, "out_height": 256, "webp_quality": 85, "seed": 778869,
        "prompt": (
            "stylized 3d metallic enamel pin badge of a golden mechanical shark fin cutting "
            "through dark waves of coins, amber neon rim light, isolated on solid pitch black "
            "background, centered game achievement icon, no text"
        ),
    },
    {
        "id": "game_medal_chameleon", "gen_width": 1024, "gen_height": 1024,
        "out_width": 256, "out_height": 256, "webp_quality": 85, "seed": 778910,
        "prompt": (
            "stylized 3d metallic enamel pin badge of a sleek chameleon wearing a noir fedora, "
            "shifting from crimson red to cyan neon, isolated on solid pitch black background, "
            "centered game achievement icon, high contrast, no text"
        ),
    },
    {
        "id": "game_medal_clueless", "gen_width": 1024, "gen_height": 1024,
        "out_width": 256, "out_height": 256, "webp_quality": 85, "seed": 778951,
        "prompt": (
            "stylized 3d metallic enamel pin badge of an upside-down spilled cinema popcorn bucket "
            "with dizzy neon purple stars spinning around it, isolated on solid pitch black "
            "background, centered game achievement icon, no text"
        ),
    },
    {
        "id": "game_bg_tv_noir_red", "gen_width": 1024, "gen_height": 576,
        "out_width": 1280, "out_height": 720, "webp_quality": 75, "seed": 778992,
        "prompt": (
            "dark abstract noir cinema lounge background, very dark charcoal empty center for UI "
            "text legibility, soft crimson red neon bokeh lights and subtle Venetian blind shadows "
            "only on the far edges, heavy dark vignette, minimalist, no objects in center, no text"
        ),
    },
    {
        "id": "game_bg_tv_noir_gold", "gen_width": 1024, "gen_height": 576,
        "out_width": 1280, "out_height": 720, "webp_quality": 75, "seed": 779033,
        "prompt": (
            "dark abstract vintage vault lounge background, pitch dark empty center for UI "
            "legibility, soft warm golden amber bokeh lights and subtle art deco geometric shadows "
            "on the outer borders, heavy dark vignette, minimalist, no objects in center, no text"
        ),
    },
    {
        "id": "game_bg_tv_noir_cyan", "gen_width": 1024, "gen_height": 576,
        "out_width": 1280, "out_height": 720, "webp_quality": 75, "seed": 779074,
        "prompt": (
            "dark abstract cinema archive room background, pitch dark empty center for UI "
            "legibility, soft teal and cyan neon rim glow along the corners, subtle film grain, "
            "heavy dark vignette, minimalist, no objects in center, no text"
        ),
    },
]


def post(path: str, payload: dict) -> dict:
    response = requests.post(BASE + path, json=payload, timeout=60)
    response.raise_for_status()
    return response.json()


def get(path: str) -> dict:
    response = requests.get(BASE + path, timeout=60)
    response.raise_for_status()
    return response.json()


def graph(
    seed: int,
    prompt: str,
    *,
    negative: str = NEG,
    width: int = 1024,
    height: int = 576,
    prefix: str = "game_art",
) -> dict:
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CHECKPOINT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": negative, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": width, "height": height, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "seed": seed, "steps": 32, "cfg": 7.0, "sampler_name": "dpmpp_2m",
            "scheduler": "karras", "denoise": 1.0,
            "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0],
        }},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"filename_prefix": prefix, "images": ["6", 0]}},
    }


def generate(
    game: str,
    prompt: str,
    seed: int,
    *,
    negative: str = NEG,
    width: int = 1024,
    height: int = 576,
    prefix: str = "game_art",
) -> Path:
    prompt_id = post("/prompt", {"prompt": graph(
        seed, prompt, negative=negative, width=width, height=height, prefix=prefix,
    )})["prompt_id"]
    for _ in range(360):
        time.sleep(1)
        history = get(f"/history/{prompt_id}")
        if prompt_id not in history:
            continue
        images = history[prompt_id]["outputs"].get("7", {}).get("images", [])
        if not images:
            raise RuntimeError(f"ComfyUI no devolvió imagen para {game}")
        image = images[0]
        query = urllib.parse.urlencode({
            "filename": image["filename"],
            "subfolder": image.get("subfolder", ""),
            "type": image.get("type", "output"),
        })
        response = requests.get(f"{BASE}/view?{query}", timeout=120)
        response.raise_for_status()
        target = TMP / f"{game}.png"
        target.write_bytes(response.content)
        return target
    raise TimeoutError(f"ComfyUI no terminó {game}")


def generate_salon(asset: dict) -> Path:
    raw = generate(
        asset["id"], asset["prompt"], int(asset["seed"]),
        negative=SALON_NEG,
        width=int(asset["gen_width"]), height=int(asset["gen_height"]),
        prefix="salon_art",
    )
    image = Image.open(raw).convert("RGB")
    image = image.resize(
        (int(asset["out_width"]), int(asset["out_height"])),
        Image.Resampling.LANCZOS,
    )
    image.save(SALON_DOCS / f"{asset['id']}.png", optimize=True)
    image.save(SALON_MASTER / f"{asset['id']}.png", optimize=True)
    target = ASSETS / f"{asset['id']}.webp"
    image.save(target, "WEBP", quality=int(asset["webp_quality"]), method=6)
    return target


def main() -> int:
    import sys

    TMP.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    MASTER.mkdir(parents=True, exist_ok=True)
    if "--salon" in sys.argv:
        SALON_DOCS.mkdir(parents=True, exist_ok=True)
        SALON_MASTER.mkdir(parents=True, exist_ok=True)
        wanted = {name.strip() for name in sys.argv[1:] if name.strip() and not name.startswith("--")}
        selected = [
            asset for asset in SALON_ASSETS
            if not wanted or asset["id"] in wanted or asset["id"].replace("game_", "") in wanted
        ]
        for index, asset in enumerate(selected):
            print(f"[{index + 1}/{len(selected)}] {asset['id']}", flush=True)
            target = generate_salon(asset)
            print(f"  -> {target.name}", flush=True)
        print("salón listo")
        return 0

    wanted = {name.strip() for name in sys.argv[1:] if name.strip()}
    selected = {k: v for k, v in GAMES.items() if not wanted or k in wanted}
    for index, (game, prompt) in enumerate(selected.items()):
        print(f"[{index + 1}/{len(selected)}] {game}", flush=True)
        raw = generate(game, prompt, 778000 + list(GAMES).index(game) * 41)
        image = Image.open(raw).convert("RGB")
        image.save(DOCS / f"{game}.png", optimize=True)
        image.save(MASTER / f"{game}.png", optimize=True)
        image.save(ASSETS / f"game_{game}_art.webp", "WEBP", quality=86, method=6)
        print(f"  -> game_{game}_art.webp", flush=True)
    print("arte listo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
