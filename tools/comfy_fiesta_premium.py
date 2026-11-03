#!/usr/bin/env python3
"""Arte premium de Vision+ Fiesta con ComfyUI + SDXL local.

Genera candidatos en `docs/assets/games/v3/premium/` (PNG maestros) para que se
puedan revisar antes de instalar. No toca el APK.

  python3 tools/comfy_fiesta_premium.py --only stage --variants 2
  python3 tools/comfy_fiesta_premium.py --only host --variants 2
  python3 tools/comfy_fiesta_premium.py --only phone --variants 1
  python3 tools/comfy_fiesta_premium.py --contact

Dirección de arte: ilustración 3D cinematográfica, plató real, luz volumétrica,
materiales ricos, negro profundo y un acento por juego. Sin texto, sin logos,
sin UI. Los seis platós comparten lenguaje visual para que se sientan parte del
mismo party pack, pero cada uno tiene composición y utilería propias.
"""

from __future__ import annotations

import argparse
import io
import math
import os
import time
import urllib.parse
import urllib.request
from pathlib import Path

import requests
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("COMFY_URL", "http://127.0.0.1:8188")
CHECKPOINT = os.environ.get("FIESTA_SDXL", "juggernautXL_v9Rundiffusion.safetensors")
OUT = ROOT / "docs/assets/games/v3/premium"
SHEET = OUT / "contact_sheet.png"

STYLE = (
    "premium cinematic 3D illustration for a television party game, "
    "high-end production design, film still quality, ultra detailed set, "
    "volumetric practical lighting, glossy and matte material contrast, "
    "rich deep shadows, subtle atmospheric haze, tasteful film grain, "
    "deep color grading, confident composition, editorial art direction, "
    "no text, no letters, no words, no logo, no watermark, no signature, "
    "no UI, no HUD, no frame, no border"
)

NEG = (
    "text, letters, words, typography, logo, watermark, signature, UI, HUD, "
    "frame, border, low quality, lowres, blurry, pixelated, jpeg artifacts, "
    "deformed, extra fingers, bad hands, distorted face, duplicate, "
    "flat lighting, generic, cluttered, oversaturated, childlike, cartoon outline, "
    "cheap render, plastic toy, stock photo, amateur"
)


LOBBY_PROMPT = (
    "wide cinematic party game hub at night, a luxurious dark circular lounge with six "
    "glowing doorways arranged around a glossy central floor, each doorway showing a "
    "different world: a warm orange TV game-show stage with bulbs, a cyan television "
    "newsroom with rain glass, a violet time-machine portal with clock rings, an emerald "
    "chat world with floating glass bubbles, a hot-pink fashion runway with camera flashes, "
    "and a golden auction house with crimson curtains. Volumetric spotlights, deep shadows, "
    "subtle haze, premium production design, symmetrical composition, no people, no text, "
    "no letters, no logos, no UI"
)

GAMES = {
    "ultima_palabra": {
        "accent": "burnt orange and warm gold",
        "stage": (
            "luxurious 1970s-inspired TV game show studio at night, a curved modular stage "
            "with two tall sleek contestant podiums facing each other in the foreground, each "
            "podium holding an oversized blank answer card glowing warm amber, a giant "
            "semi-circular applause meter with warm bulbs on the right, deep orange velvet "
            "seating, studio audience silhouettes at the edges, tungsten spotlights and soft "
            "lens flares, confetti suspended in the air, polished wood and brass details, "
            "dramatic symmetrical composition, no office furniture, "
            "burnt orange and warm gold palette, deep cocoa shadows"
        ),
        "host": (
            "character portrait of Nora Vela, a sharp 32-year-old Mexican female game-show host, "
            "confident smirk, dark bob haircut, tailored burnt-orange blazer over black top, "
            "vintage chrome microphone in one hand, warm studio key light with orange rim light, "
            "dark chocolate studio background, waist-up, looking at camera, "
            "premium stylized 3D character design, expressive eyes, detailed fabric"
        ),
        "selector": (
            "vertical character key art of Nora Vela, a sharp 32-year-old Mexican female game-show host in a tailored burnt-orange blazer, holding a vintage chrome microphone, standing confidently on a luxurious warm-orange TV game-show stage with glowing answer cards and a giant applause meter behind her, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium TV game-show app, warm orange "
            "stage bokeh, soft glowing bulbs, suspended confetti, glossy floor reflections, "
            "curved set architecture dissolving into darkness, deep cocoa and burnt orange "
            "gradient, cinematic haze, dark lower half for mobile controls, no focal object, "
            "no people, no text, no letters, no UI"
        ),
    },
    "vox_populi": {
        "accent": "cyan and sodium yellow",
        "stage": (
            "high-tech television studio set at night transformed into a survey game show, "
            "an empty curved glass anchor desk in the foreground with no chairs and no people, "
            "a giant floating holographic survey chart with six tall glowing vertical bars "
            "above the desk, a massive wall of screens with abstract maps, cyan and sodium-yellow "
            "lighting, rain-streaked city skyline through floor-to-ceiling windows, a "
            "breaking-news light rig above, camera silhouettes at the edges, reflective polished "
            "floor, dramatic wide composition, no people, no chairs, cyan and amber palette, deep teal shadows"
        ),
        "host": (
            "character portrait of Don Chisme, a charismatic 46-year-old Mexican male radio and "
            "TV host, warm exaggerated smile, slicked-back dark hair, thick mustache, teal suit "
            "with a loosened tie, chunky studio headphones around his neck, holding a broadcast "
            "microphone, cyan and warm tungsten rim light, dark newsroom background, waist-up, "
            "premium stylized 3D character design, detailed skin and fabric"
        ),
        "selector": (
            "vertical character key art of Don Chisme, a charismatic 46-year-old Mexican male news host in a teal suit with studio headphones, holding a broadcast microphone, standing in a high-tech cyan newsroom at night with a wall of glowing survey screens and rain-streaked city windows behind him, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium TV news app, cyan newsroom "
            "bokeh, rain-streaked glass, glowing screen bars far in the distance, deep teal "
            "gradient, volumetric haze, dark lower half for mobile controls, no desk, no focal "
            "object, no people, no text, no letters, no UI"
        ),
    },
    "fuera_de_epoca": {
        "accent": "violet and electric magenta",
        "stage": (
            "retro-futurist time machine laboratory inside a television studio, a giant brass and "
            "glass clock dial as the centerpiece, concentric rings and floating holographic "
            "calendar panels, vintage CRT monitors stacked on the sides, Tesla coils with violet "
            "arcs, dust motes in volumetric light, a polished dark floor reflecting the dial, "
            "violet and electric magenta palette, deep indigo shadows, dramatic symmetrical composition"
        ),
        "host": (
            "character portrait of Professor Tic Tac, a distracted 58-year-old Mexican history "
            "professor and TV host, wild white hair, round brass glasses, tweed jacket with a "
            "violet bow tie, a glowing pocket watch in one hand, excited pedantic expression, "
            "violet key light with magenta rim light, dark laboratory background, waist-up, "
            "premium stylized 3D character design, detailed wrinkles and fabric"
        ),
        "selector": (
            "vertical character key art of Professor Tic Tac, a distracted 58-year-old Mexican history professor with wild white hair, round brass glasses and a violet bow tie, holding a glowing pocket watch, standing in a retro-futurist time machine laboratory with a giant violet clock portal behind him, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium time-travel game app, violet "
            "and magenta glowing rings and brass gear bokeh in the far distance, floating dust, "
            "soft CRT glow, deep indigo gradient, volumetric haze, dark lower half for mobile "
            "controls, no clock face, no numerals, no focal object, no people, no text, no letters"
        ),
    },
    "texto_roto": {
        "accent": "emerald and ice cyan",
        "stage": (
            "abstract dark digital void, giant translucent glass chat bubbles with soft emerald "
            "glow floating in mid-air at different depths, broken UI panels and glitch shards "
            "suspended around them, a glowing emerald keyboard floor dissolving into darkness, "
            "a central circular light portal, ice-cyan and emerald neon, deep black-green "
            "shadows, cinematic depth of field, dramatic asymmetric composition, no room, no "
            "furniture, no chairs, no people, no office"
        ),
        "host": (
            "character portrait of La Maquina, an androgynous sleek robot TV host, matte black "
            "ceramic shell, expressive emerald LED screen face with a subtle deadpan smile, thin "
            "antenna, visible glowing circuitry seams, one hand raised as if typing in the air, "
            "emerald key light with ice-cyan rim light, dark server room background, waist-up, "
            "premium stylized 3D character design, immaculate materials"
        ),
        "selector": (
            "vertical character key art of La Maquina, an androgynous sleek black robot host with an expressive emerald screen face and thin antenna, raising one hand as if typing in the air, standing in a surreal dark chat world with giant translucent emerald message bubbles and glitch shards behind, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium chat game app, emerald and "
            "ice-cyan glass bubble bokeh and glitch shards dissolving in the far distance, deep "
            "black-green gradient, volumetric haze, subtle scanlines, dark lower half for mobile "
            "controls, no UI, no focal object, no people, no text, no letters"
        ),
    },
    "franela_fest": {
        "accent": "hot pink and champagne gold",
        "stage": (
            "high-fashion runway show at night inside a grand theater, a glossy black runway in "
            "perspective, three tall golden featureless mannequins wearing absurd oversized "
            "t-shirts under spotlights, a judges table with brass scorecards on the left, camera "
            "flashes in the dark audience, heavy magenta velvet curtains, champagne-gold light "
            "rig, confetti and haze, no human models, hot pink and gold palette, deep burgundy "
            "shadows, dramatic editorial composition"
        ),
        "host": (
            "character portrait of El Critico, a flamboyant 36-year-old Mexican male fashion critic "
            "and TV host, dramatic pompadour, thin gold glasses, pink silk scarf over a black "
            "tailored suit, holding a blank brass scorecard, theatrical disdainful expression, "
            "hot-pink key light with champagne rim light, dark runway background, waist-up, "
            "premium stylized 3D character design, detailed fabric and jewelry"
        ),
        "selector": (
            "vertical character key art of El Critico, a flamboyant 36-year-old Mexican male fashion critic with a dramatic pompadour, gold glasses and a pink silk scarf, holding a blank brass scorecard, standing on a glossy black runway at night with magenta curtains, golden mannequins and camera flashes behind him, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium fashion game app, glossy black "
            "runway edge and magenta curtain bokeh far behind, champagne-gold light flares and "
            "camera flashes, hot-pink gradient, cinematic haze, dark lower half for mobile "
            "controls, no people, no models, no focal object, no text, no letters, no UI"
        ),
    },
    "el_tasador": {
        "accent": "antique gold and deep amber",
        "stage": (
            "opulent secret auction stage at night, heavy crimson velvet curtains framing a "
            "spotlit empty central pedestal with a mysterious gilded artifact under a glass "
            "cloche, a giant antique brass gavel on the right, hanging blank price tags and "
            "art-deco gold frames, candlelight and warm amber spotlights, smoke curling in the "
            "air, dark mahogany and gold palette, deep espresso shadows, dramatic theatrical "
            "composition, no people, no text, no numbers"
        ),
        "host": (
            "character portrait of El Tasador, a theatrical 62-year-old Mexican auctioneer, "
            "neat white beard, gold monocle, black top hat, deep burgundy velvet coat, holding a "
            "small brass gavel, mysterious knowing expression, warm amber key light with gold rim "
            "light, dark auction house background, waist-up, premium stylized 3D character design, "
            "detailed velvet and metal"
        ),
        "selector": (
            "vertical character key art of El Tasador, a theatrical 62-year-old Mexican auctioneer with a neat white beard, gold monocle, black top hat and burgundy velvet coat, holding a small brass gavel, standing in an opulent auction house with crimson velvet curtains, a spotlit gilded artifact and candlelight behind him, cinematic rim light, dark lower third for UI, premium stylized 3D, no text"
        ),
        "phone": (
            "vertical atmospheric background plate for a premium auction game app, crimson "
            "velvet curtain bokeh, antique gold frame glints and candlelight far behind, deep "
            "amber gradient, curling smoke, volumetric haze, dark lower half for mobile "
            "controls, no objects, no focal point, no people, no text, no letters, no UI"
        ),
    },
}

SIZES = {
    "stage": (1344, 768),
    "host": (1024, 1024),
    "phone": (768, 1344),
    "selector": (1024, 1280),
    "lobby": (1344, 768),
}


def post(path: str, payload: dict) -> dict:
    response = requests.post(BASE + path, json=payload, timeout=60)
    response.raise_for_status()
    return response.json()


def get(path: str) -> dict:
    response = requests.get(BASE + path, timeout=60)
    response.raise_for_status()
    return response.json()


def graph(seed: int, prompt: str, width: int, height: int, prefix: str) -> dict:
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CHECKPOINT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": width, "height": height, "batch_size": 1}},
        "5": {
            "class_type": "KSampler",
            "inputs": {
                "seed": seed,
                "steps": 34,
                "cfg": 6.5,
                "sampler_name": "dpmpp_2m",
                "scheduler": "karras",
                "denoise": 1.0,
                "model": ["1", 0],
                "positive": ["2", 0],
                "negative": ["3", 0],
                "latent_image": ["4", 0],
            },
        },
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"filename_prefix": prefix, "images": ["6", 0]}},
    }


def generate(seed: int, prompt: str, width: int, height: int, prefix: str) -> Image.Image:
    prompt_id = post("/prompt", {"prompt": graph(seed, prompt, width, height, prefix)})["prompt_id"]
    for _ in range(900):
        time.sleep(1)
        history = get(f"/history/{prompt_id}")
        if prompt_id not in history:
            continue
        images = history[prompt_id]["outputs"].get("7", {}).get("images", [])
        if not images:
            raise RuntimeError(f"ComfyUI no devolvió imagen para {prefix}")
        item = images[0]
        query = urllib.parse.urlencode({
            "filename": item["filename"],
            "subfolder": item.get("subfolder", ""),
            "type": item.get("type", "output"),
        })
        response = requests.get(f"{BASE}/view?{query}", timeout=180)
        response.raise_for_status()
        return Image.open(io.BytesIO(response.content)).convert("RGB")
    raise TimeoutError(f"ComfyUI no terminó {prefix}")


def finish(image: Image.Image, target_size: tuple[int, int], out: Path) -> None:
    """Sube a resolución final conservando detalle y añade grano fino."""
    image = image.resize(target_size, Image.Resampling.LANCZOS)
    image = image.filter(ImageFilter.UnsharpMask(radius=1.4, percent=85, threshold=3))
    # Grano muy sutil para que no se vea un render plano.
    noise = Image.effect_noise(target_size, 5).convert("L")
    noise = noise.filter(ImageFilter.GaussianBlur(0.4))
    image = Image.composite(image, Image.new("RGB", target_size, (0, 0, 0)), noise.point(lambda v: 245 + v // 50))
    out.parent.mkdir(parents=True, exist_ok=True)
    image.save(out, "PNG", optimize=True)


def build_tasks(only: str, variants: int) -> list[tuple[str, str, int, str, int, int]]:
    if only == "lobby":
        return [("lobby", "lobby", 819000, f"{STYLE}, {LOBBY_PROMPT}", 1344, 768)]
    tasks = []
    for game, data in GAMES.items():
        kinds = ["stage", "host", "phone", "selector"] if only == "all" else [only]
        for kind in kinds:
            base_prompt = data[kind]
            prompt = f"{STYLE}, {data['accent']} palette, {base_prompt}"
            width, height = SIZES[kind]
            for variant in range(variants):
                seed = 810000 + (list(GAMES).index(game) + 1) * 1000 + kind_index(kind) * 100 + variant * 7
                tasks.append((game, kind, seed, prompt, width, height))
    return tasks


def kind_index(kind: str) -> int:
    return {"stage": 0, "host": 1, "phone": 2, "selector": 3, "lobby": 4}.get(kind, 0)


def install_contact_sheet() -> None:
    images = sorted(OUT.glob("*.png"))
    if not images:
        print("No hay imágenes para la hoja de contacto.")
        return
    thumbs = []
    for path in images:
        im = Image.open(path).convert("RGB")
        im.thumbnail((420, 420), Image.Resampling.LANCZOS)
        thumbs.append((path.stem, im.copy()))
    cols = 4
    rows = math.ceil(len(thumbs) / cols)
    cell_w, cell_h = 440, 470
    sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), (10, 12, 18))
    for index, (label, im) in enumerate(thumbs):
        x = (index % cols) * cell_w + 10
        y = (index // cols) * cell_h + 10
        sheet.paste(im, (x, y))
    sheet.save(SHEET, "PNG", optimize=True)
    print(f"Hoja de contacto: {SHEET}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="all", choices=["all", "stage", "host", "phone", "selector", "lobby"])
    parser.add_argument("--variants", type=int, default=1)
    parser.add_argument("--game", default="")
    parser.add_argument("--contact", action="store_true")
    args = parser.parse_args()
    if args.contact:
        install_contact_sheet()
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    tasks = build_tasks(args.only, max(1, args.variants))
    if args.game:
        tasks = [task for task in tasks if task[0] == args.game]
    print(f"ComfyUI: {BASE} · checkpoint: {CHECKPOINT}")
    print(f"Tareas: {len(tasks)}")
    for game, kind, seed, prompt, width, height in tasks:
        name = f"{game}_{kind}_{seed}"
        target = OUT / f"{name}.png"
        if target.exists():
            print(f"[skip] {target.name}")
            continue
        print(f"[gen]  {name} ({width}x{height})")
        started = time.time()
        try:
            image = generate(seed, prompt, width, height, name)
        except Exception as exc:  # noqa: BLE001
            print(f"[err]  {name}: {exc}")
            continue
        final = {
            "stage": (1920, 1080),
            "host": (900, 900),
            "phone": (1080, 1920),
            "selector": (900, 1200),
            "lobby": (1920, 1080),
        }[kind]
        finish(image, final, target)
        print(f"[ok]   {target.name} en {time.time() - started:.1f}s")
    install_contact_sheet()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
