#!/usr/bin/env python3
"""Genera los assets de "jugador" y tutoriales de Vision+ Play (Fase 5).

Incluye:
  - 12 avatares/mascotas con 3 estados (normal, pensando, celebrando).
  - 21 iconos de tutorial (7 juegos × 3 pasos) + 7 tarjetas 16:9.
  - Sprite sheet de partículas (confeti, moneda, chispa, fuego, corona).

Usa el ComfyUI local y el checkpoint de siempre. El fondo se genera en verde
plano y se recorta por flood-fill (sin rembg) para que los assets sean
transparentes y consistentes. Los maestros quedan en el disco grande y en
`docs/assets/games/art/`.

Uso:
    python3 tools/comfy_game_assets_fase5.py avatars
    python3 tools/comfy_game_assets_fase5.py tutorials
    python3 tools/comfy_game_assets_fase5.py cards
    python3 tools/comfy_game_assets_fase5.py particles
    python3 tools/comfy_game_assets_fase5.py all
    python3 tools/comfy_game_assets_fase5.py avatars --only popcorn,robot
    python3 tools/comfy_game_assets_fase5.py avatars --recompose
"""

from __future__ import annotations

import argparse
import colorsys
import math
import random
import time
import urllib.parse
from collections import deque
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
BASE = "http://127.0.0.1:8188"
CHECKPOINT = "juggernautXL_v9Rundiffusion.safetensors"
ASSETS = ROOT / "app/src/main/res/drawable-nodpi"
DOCS = ROOT / "docs/assets/games/art"
MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/art")
TMP = Path("/tmp/opencode/game_assets_fase5")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REGULAR = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

NEG = (
    "text, letters, words, typography, logo, watermark, signature, frame, border, "
    "multiple characters, duplicate, deformed, extra limbs, extra fingers, blurry, "
    "low quality, realistic photo, human face, scary, weapon, busy background, "
    "gradient background, shadow on the background"
)
NEG_AVATAR = (
    NEG + ", label, sticker, badge, sign, writing, brand name, product label, "
    "cluttered floor, spilled food, props, set dressing, floor, ground, base, "
    "cast shadow, drop shadow, reflection"
)
AVATAR_SUFFIX = (
    ", completely blank unbranded surfaces, no label, no sticker, no badge, "
    "no sign, no writing, no logo, one single character, short sturdy arms and "
    "legs, floating in mid-air, no floor, no ground, no base, no shadow, "
    "full body, clean silhouette"
)

# Paleta Vision+ con acentos por personaje. Los prompts piden el mismo lenguaje
# visual para que los 12 avatares se sientan del mismo paquete.
AVATARS = [
    {
        "id": "popcorn", "accent": (245, 197, 66), "name": "Palomitas",
        "prompt": (
            "cute anthropomorphic popcorn bucket mascot, glossy 3D toy collectible, "
            "Pixar style render, big friendly eyes, short sturdy arms and legs, overflowing "
            "buttery popcorn, warm yellow and electric blue accents, front view, "
            "full body, centered, premium party game avatar, soft studio lighting, "
            "isolated on flat chroma green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "clapper", "accent": (107, 166, 255), "name": "Claqueta",
        "prompt": (
            "cute anthropomorphic movie clapperboard mascot, glossy 3D toy collectible, "
            "Pixar style render, big friendly eyes, short sturdy arms and legs, black and white "
            "stripes, electric blue neon rim light, front view, full body, centered, "
            "premium party game avatar, soft studio lighting, isolated on flat chroma "
            "green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "alien", "accent": (124, 92, 255), "name": "Alien",
        "prompt": (
            "cute friendly alien mascot, glossy 3D toy collectible, Pixar style render, "
            "big expressive eyes, small antennae, violet and cyan skin, short sturdy "
            "arms and legs, front view, full body, centered, premium party game avatar, soft "
            "studio lighting, isolated on flat chroma green background, no text, no "
            "letters, no logo"
        ),
    },
    {
        "id": "ticket", "accent": (245, 197, 66), "name": "VIP",
        "prompt": (
            "cute anthropomorphic golden VIP ticket mascot, glossy 3D toy collectible, "
            "Pixar style render, big friendly eyes, short sturdy arms and legs, perforated "
            "edges, warm gold and electric blue accents, front view, full body, "
            "centered, premium party game avatar, soft studio lighting, isolated on "
            "flat chroma green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "robot", "accent": (45, 125, 246), "name": "Robot",
        "prompt": (
            "cute retro television robot mascot, glossy 3D toy collectible, Pixar style "
            "render, rounded dark navy body, big cyan glowing eyes on a screen face, "
            "small antenna, short sturdy arms and legs, electric blue and violet accents, front "
            "view, full body, centered, premium party game avatar, soft studio lighting, "
            "isolated on flat chroma green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "detective_cat", "accent": (255, 107, 107), "name": "Gato noir",
        "prompt": (
            "cute noir detective cat mascot, glossy 3D toy collectible, Pixar style "
            "render, black and white fur, tiny fedora hat, trench coat, big amber eyes, "
            "front view, full body, centered, premium party game avatar, soft studio "
            "lighting, isolated on flat chroma green background, no text, no letters, "
            "no logo"
        ),
    },
    {
        "id": "disco", "accent": (107, 230, 255), "name": "Disco",
        "prompt": (
            "cute anthropomorphic disco ball mascot, glossy 3D toy collectible, Pixar "
            "style render, mirrored facets, star-shaped sunglasses, short sturdy arms and legs, "
            "cyan and magenta party lights, front view, full body, centered, premium "
            "party game avatar, soft studio lighting, isolated on flat chroma green "
            "background, no text, no letters, no logo"
        ),
    },
    {
        "id": "skull", "accent": (255, 138, 76), "name": "Calavera",
        "prompt": (
            "cute friendly skull mascot, glossy 3D toy collectible, Pixar style render, "
            "big expressive eye sockets, tiny party hat, orange and electric blue "
            "accents, short sturdy arms and legs, front view, full body, centered, premium party "
            "game avatar, soft studio lighting, isolated on flat chroma green "
            "background, no text, no letters, no logo"
        ),
    },
    {
        "id": "dice", "accent": (124, 255, 178), "name": "Dado",
        "prompt": (
            "cute anthropomorphic dice mascot, glossy 3D toy collectible, Pixar style "
            "render, rounded white die with electric blue pips, big friendly eyes, short "
            "sturdy arms and legs, mint green accent glow, front view, full body, centered, "
            "premium party game avatar, soft studio lighting, isolated on flat chroma "
            "green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "mic", "accent": (255, 107, 180), "name": "Micrófono",
        "prompt": (
            "cute vintage microphone mascot, glossy 3D toy collectible, Pixar style "
            "render, chrome and rose gold body, big friendly eyes, short sturdy arms and legs, "
            "pink and electric blue accent glow, front view, full body, centered, "
            "premium party game avatar, soft studio lighting, isolated on flat chroma "
            "green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "brush", "accent": (124, 230, 255), "name": "Pincel",
        "prompt": (
            "cute anthropomorphic paintbrush mascot, glossy 3D toy collectible, Pixar "
            "style render, wooden handle, bright cyan paint tip, big friendly eyes, short "
            "sturdy arms and legs, paint splatter accents, front view, full body, centered, "
            "premium party game avatar, soft studio lighting, isolated on flat chroma "
            "green background, no text, no letters, no logo"
        ),
    },
    {
        "id": "coin", "accent": (245, 197, 66), "name": "Moneda",
        "prompt": (
            "cute anthropomorphic golden coin mascot, glossy 3D toy collectible, Pixar "
            "style render, embossed star, big friendly eyes, short sturdy arms and legs, warm "
            "gold and electric blue accent glow, front view, full body, centered, "
            "premium party game avatar, soft studio lighting, isolated on flat chroma "
            "green background, no text, no letters, no logo"
        ),
    },
]

TUTORIALS = {
    "trivia": {
        "name": "TRIVIA",
        "accent": (45, 125, 246),
        "steps": [
            ("answers", "four glowing quiz answer cards A B C D floating in a row, "
                        "minimalist 3D game icon, electric blue neon rim light"),
            ("clock", "a sleek stopwatch with a lightning bolt, minimalist 3D game icon, "
                      "electric blue and cyan neon rim light"),
            ("trophy", "a small golden trophy with a star, minimalist 3D game icon, "
                       "warm gold and electric blue neon rim light"),
        ],
    },
    "charadas": {
        "name": "CHARADAS",
        "accent": (255, 138, 76),
        "steps": [
            ("masks", "comedy and tragedy theater masks, minimalist 3D game icon, "
                      "coral orange and electric blue neon rim light"),
            ("phone", "a smartphone showing a movie poster silhouette, minimalist 3D "
                      "game icon, coral orange and electric blue neon rim light"),
            ("stopwatch", "a stopwatch with a stage spotlight, minimalist 3D game icon, "
                          "coral orange and electric blue neon rim light"),
        ],
    },
    "wager": {
        "name": "APUESTA",
        "accent": (124, 92, 255),
        "steps": [
            ("chips", "a stack of glowing poker chips, minimalist 3D game icon, violet "
                      "and electric blue neon rim light"),
            ("cards", "three glowing clue cards fanned out, minimalist 3D game icon, "
                      "violet and electric blue neon rim light"),
            ("trophy", "a golden trophy with a question mark silhouette, minimalist 3D "
                       "game icon, violet and gold neon rim light"),
        ],
    },
    "impostor": {
        "name": "IMPOSTOR",
        "accent": (255, 77, 109),
        "steps": [
            ("group", "four friendly party silhouettes in a row, minimalist 3D game "
                      "icon, crimson and electric blue neon rim light"),
            ("spy", "a mysterious spy in a trench coat and fedora, minimalist 3D game "
                    "icon, crimson and electric blue neon rim light"),
            ("magnifier", "a magnifying glass revealing an eye, minimalist 3D game icon, "
                          "crimson and electric blue neon rim light"),
        ],
    },
    "dibujo": {
        "name": "DIBUJO",
        "accent": (107, 230, 255),
        "steps": [
            ("canvas", "a glowing canvas on an easel with a pencil, minimalist 3D game "
                       "icon, cyan and electric blue neon rim light"),
            ("bubble", "a blank comic speech bubble with a scribble, minimalist 3D game "
                       "icon, cyan and electric blue neon rim light"),
            ("trophy", "a golden trophy with a paint splatter, minimalist 3D game icon, "
                       "cyan and gold neon rim light"),
        ],
    },
    "subasta": {
        "name": "SUBASTA",
        "accent": (245, 197, 66),
        "steps": [
            ("coins", "a stack of golden coins, minimalist 3D game icon, warm gold and "
                      "electric blue neon rim light"),
            ("gavel", "a golden auction gavel on a sound block, minimalist 3D game icon, "
                      "warm gold and electric blue neon rim light"),
            ("vault", "a small brass vault with a star lock, minimalist 3D game icon, "
                      "warm gold and electric blue neon rim light"),
        ],
    },
    "fibbage": {
        "name": "FIBBAGE",
        "accent": (124, 255, 178),
        "steps": [
            ("fact", "a glowing trivia fact card with a lightbulb, minimalist 3D game "
                     "icon, mint green and electric blue neon rim light"),
            ("lie", "a comic speech bubble with a forked tongue, minimalist 3D game "
                    "icon, mint green and electric blue neon rim light"),
            ("magnifier", "a magnifying glass over a fingerprint, minimalist 3D game "
                          "icon, mint green and electric blue neon rim light"),
        ],
    },
}


def post(path: str, payload: dict) -> dict:
    response = requests.post(BASE + path, json=payload, timeout=60)
    response.raise_for_status()
    return response.json()


def get(path: str) -> dict:
    response = requests.get(BASE + path, timeout=60)
    response.raise_for_status()
    return response.json()


def graph(prompt: str, seed: int, width: int, height: int, steps: int, cfg: float,
          prefix: str, negative: str = NEG) -> dict:
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": CHECKPOINT}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": negative, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {
            "width": width, "height": height, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "seed": seed, "steps": steps, "cfg": cfg, "sampler_name": "dpmpp_2m",
            "scheduler": "karras", "denoise": 1.0,
            "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0],
            "latent_image": ["4", 0],
        }},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {
            "filename_prefix": prefix, "images": ["6", 0]}},
    }


def generate(prompt: str, seed: int, prefix: str, *, width: int = 1024,
             height: int = 1024, steps: int = 30, cfg: float = 6.5,
             negative: str = NEG) -> Path:
    TMP.mkdir(parents=True, exist_ok=True)
    prompt_id = post("/prompt", {"prompt": graph(
        prompt, seed, width, height, steps, cfg, prefix, negative,
    )})["prompt_id"]
    for _ in range(420):
        time.sleep(1)
        history = get(f"/history/{prompt_id}")
        if prompt_id not in history:
            continue
        images = history[prompt_id]["outputs"].get("7", {}).get("images", [])
        if not images:
            raise RuntimeError(f"ComfyUI no devolvió imagen para {prefix}")
        image = images[0]
        query = urllib.parse.urlencode({
            "filename": image["filename"],
            "subfolder": image.get("subfolder", ""),
            "type": image.get("type", "output"),
        })
        response = requests.get(f"{BASE}/view?{query}", timeout=120)
        response.raise_for_status()
        target = TMP / f"{prefix}.png"
        target.write_bytes(response.content)
        return target
    raise TimeoutError(f"ComfyUI no terminó {prefix}")


def _distance(a: tuple[int, int, int], b: tuple[int, int, int]) -> float:
    return sum((x - y) ** 2 for x, y in zip(a, b)) ** 0.5


def key_green(path: Path, *, tolerance: float = 95.0) -> Image.Image:
    """Recorta el fondo del render.

    Primero intenta rembg (u2net) porque el modelo a veces ignora el chroma
    verde y pinta un fondo cálido; si no está instalado, cae al flood-fill verde.
    """
    image = Image.open(path).convert("RGB")
    try:
        from rembg import remove
        cut = remove(image)
        bbox = cut.getbbox()
        if bbox:
            return cut.crop(bbox)
    except Exception as exc:  # rembg es opcional
        print(f"  aviso: rembg no disponible ({exc}); uso chroma verde", flush=True)
    small = image.resize((512, 512), Image.Resampling.LANCZOS)
    px = small.load()
    corners = [px[0, 0], px[511, 0], px[0, 511], px[511, 511]]
    bg = tuple(sum(c[i] for c in corners) // 4 for i in range(3))

    def is_bg(p: tuple[int, int, int]) -> bool:
        r, g, b = p
        # Verde dominante o muy parecido al color de la esquina.
        greenish = g > 55 and g > r * 1.12 and g > b * 1.12
        return greenish or _distance(p, bg) < tolerance

    mask = Image.new("L", (512, 512), 255)
    out = mask.load()
    visited = bytearray(512 * 512)
    queue: deque[tuple[int, int]] = deque()
    for x in range(512):
        for y in (0, 511):
            if is_bg(px[x, y]) and not visited[y * 512 + x]:
                visited[y * 512 + x] = 1
                queue.append((x, y))
    for y in range(512):
        for x in (0, 511):
            if is_bg(px[x, y]) and not visited[y * 512 + x]:
                visited[y * 512 + x] = 1
                queue.append((x, y))
    while queue:
        x, y = queue.popleft()
        out[x, y] = 0
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < 512 and 0 <= ny < 512:
                index = ny * 512 + nx
                if not visited[index] and is_bg(px[nx, ny]):
                    visited[index] = 1
                    queue.append((nx, ny))

    mask = mask.filter(ImageFilter.GaussianBlur(1.4)).resize(
        image.size, Image.Resampling.LANCZOS)
    rgba = image.convert("RGBA")
    rgba.putalpha(mask)

    # Limpia el derrame verde en los píxeles semitransparentes del borde.
    data = rgba.load()
    for y in range(rgba.height):
        for x in range(rgba.width):
            r, g, b, a = data[x, y]
            if 0 < a < 245 and g > max(r, b) + 12:
                data[x, y] = (r, min(g, max(r, b) + 10), b, a)

    bbox = rgba.getbbox()
    return rgba.crop(bbox) if bbox else rgba


def fit_character(char: Image.Image, *, size: int = 512, max_h: int = 424,
                  bottom: int = 486, glow: tuple[int, int, int] | None = None,
                  shadow: bool = True) -> tuple[Image.Image, tuple[int, int, int, int]]:
    if char.height > max_h or char.width > size - 56:
        scale = min(max_h / char.height, (size - 56) / char.width)
        char = char.resize((max(1, round(char.width * scale)),
                            max(1, round(char.height * scale))),
                           Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    x = (size - char.width) // 2
    y = bottom - char.height

    if glow:
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        pad = 34
        draw.ellipse((x - pad, y + 18, x + char.width + pad, bottom + 14),
                     fill=(*glow, 45))
        canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(18)))

    if shadow:
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        draw.ellipse((x + 14, bottom - 22, x + char.width - 14, bottom + 10),
                     fill=(0, 0, 0, 95))
        canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(10)))

    canvas.alpha_composite(char, (x, y))
    return canvas, (x, y, char.width, char.height)


def _star(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float,
          color: tuple[int, int, int, int]) -> None:
    points = []
    for i in range(8):
        angle = -math.pi / 2 + i * math.pi / 4
        radius = r if i % 2 == 0 else r * 0.38
        points.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
    draw.polygon(points, fill=color)


def _thought_bubble(canvas: Image.Image, accent: tuple[int, int, int],
                    head: tuple[int, int, int, int]) -> None:
    draw = ImageDraw.Draw(canvas)
    x, y, w, h = head
    bx = min(canvas.width - 118, x + w - 40)
    by = max(8, y - 6)
    box = (bx, by, min(canvas.width - 8, bx + 108), min(canvas.height - 8, by + 68))
    draw.rounded_rectangle(box, radius=22, fill=(8, 14, 26, 238),
                           outline=(*accent, 235), width=3)
    for i, dx in enumerate((26, 54, 82)):
        draw.ellipse((bx + dx - 5, by + 27 - 5, bx + dx + 5, by + 27 + 5),
                     fill=(*accent, 245))
    # Colita de la burbuja hacia la cabeza.
    draw.ellipse((bx - 12, by + 56, bx + 4, by + 72), fill=(8, 14, 26, 225),
                 outline=(*accent, 200), width=2)
    draw.ellipse((bx - 24, by + 72, bx - 12, by + 84), fill=(8, 14, 26, 210),
                 outline=(*accent, 170), width=2)


def _confetti(canvas: Image.Image, accent: tuple[int, int, int], seed: int) -> None:
    rng = random.Random(seed)
    draw = ImageDraw.Draw(canvas)
    colors = [
        (*accent, 235), (107, 230, 255, 235), (245, 197, 66, 235),
        (255, 255, 255, 220), (255, 107, 180, 220),
    ]
    for _ in range(26):
        x = rng.randint(24, 488)
        y = rng.randint(20, 330)
        w = rng.randint(5, 12)
        h = rng.randint(10, 22)
        color = rng.choice(colors)
        # Rectángulo girado simple: se dibuja en una capa y se rota.
        piece = Image.new("RGBA", (w * 2, h * 2), (0, 0, 0, 0))
        ImageDraw.Draw(piece).rounded_rectangle(
            (w // 2, h // 2, w + w // 2, h + h // 2), radius=2, fill=color)
        piece = piece.rotate(rng.randint(0, 359), expand=False,
                             resample=Image.Resampling.BICUBIC)
        canvas.alpha_composite(piece, (x, y))
    for _ in range(9):
        _star(draw, rng.randint(30, 482), rng.randint(20, 300),
              rng.randint(7, 15), rng.choice(colors))


def build_avatar_states(avatar: dict, raw: Path) -> list[Path]:
    char = key_green(raw)
    accent = avatar["accent"]
    outputs: list[Path] = []

    normal, box = fit_character(char, glow=accent)
    outputs.append(normal)

    thinking, _ = fit_character(char, glow=(107, 166, 255))
    thinking = thinking.rotate(-3.2, resample=Image.Resampling.BICUBIC,
                               expand=False)
    _thought_bubble(thinking, accent, box)
    outputs.append(thinking)

    celebrating, box2 = fit_character(char, max_h=436, glow=(245, 197, 66))
    celebrating = celebrating.rotate(2.0, resample=Image.Resampling.BICUBIC,
                                     expand=False)
    _confetti(celebrating, accent, seed=sum(map(ord, avatar["id"])))
    draw = ImageDraw.Draw(celebrating)
    cx = box2[0] + box2[2] // 2
    top = max(6, box2[1] - 12)
    _star(draw, cx, top, 22, (245, 197, 66, 245))
    _star(draw, cx, top, 11, (255, 255, 255, 230))
    outputs.append(celebrating)

    names = ["normal", "thinking", "celebrating"]
    for state, image in zip(names, outputs):
        image = image.convert("RGBA")
        image.save(ASSETS / f"game_avatar_{avatar['id']}_{state}.webp",
                   "WEBP", quality=88, method=6)
        image.save(DOCS / "avatars" / f"{avatar['id']}_{state}.png", optimize=True)
        image.save(MASTER / "avatars" / f"{avatar['id']}_{state}.png", optimize=True)
    return outputs


def avatar_contact_sheet() -> None:
    tiles = []
    for avatar in AVATARS:
        for state in ("normal", "thinking", "celebrating"):
            path = DOCS / "avatars" / f"{avatar['id']}_{state}.png"
            if path.exists():
                tiles.append((avatar["id"], state, Image.open(path).convert("RGBA")))
    if not tiles:
        return
    cell = 220
    cols, rows = 6, 6
    sheet = Image.new("RGB", (cols * cell, rows * cell + 30), (7, 11, 19))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype(FONT_REGULAR, 13)
    for index, (aid, state, image) in enumerate(tiles[: cols * rows]):
        x = (index % cols) * cell
        y = (index // cols) * cell
        thumb = image.copy()
        thumb.thumbnail((cell - 24, cell - 38), Image.Resampling.LANCZOS)
        sheet.paste(thumb, (x + (cell - thumb.width) // 2,
                            y + 8 + (cell - 38 - thumb.height) // 2), thumb)
        draw.text((x + 8, y + cell - 24), f"{aid} · {state}",
                  fill=(180, 196, 220), font=font)
    sheet.save(DOCS / "avatar_contact_sheet.png", optimize=True)
    sheet.save(MASTER / "avatar_contact_sheet.png", optimize=True)


def build_tutorial_icon(game: dict, step: tuple[str, str], raw: Path) -> Image.Image:
    char = key_green(raw)
    accent = game["accent"]
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    # Teja oscura con borde de acento, para que el icono se lea en cualquier fondo.
    draw.rounded_rectangle((18, 18, 494, 494), radius=86, fill=(9, 15, 27, 255),
                           outline=(*accent, 120), width=2)
    draw.rounded_rectangle((28, 28, 484, 484), radius=78, fill=(13, 22, 39, 255))
    glow = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((96, 96, 416, 416), fill=(*accent, 42))
    canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(24)))
    icon, _ = fit_character(char, size=512, max_h=300, bottom=392, glow=accent)
    canvas.alpha_composite(icon)
    # Número de paso discreto.
    font = ImageFont.truetype(FONT_BOLD, 46)
    draw = ImageDraw.Draw(canvas)
    draw.ellipse((392, 392, 478, 478), fill=(*accent, 235))
    number = str(game["steps"].index(step) + 1)
    box = draw.textbbox((0, 0), number, font=font)
    draw.text((435 - (box[2] - box[0]) / 2, 435 - (box[3] - box[1]) / 2 - 4),
              number, fill=(7, 11, 19), font=font)
    return canvas


def tutorial_card(game_id: str, game: dict, icons: list[Image.Image]) -> Image.Image:
    width, height = 1280, 720
    canvas = Image.new("RGB", (width, height), (7, 11, 19))
    draw = ImageDraw.Draw(canvas)
    accent = game["accent"]
    # Gradiente radial de acento.
    glow = Image.new("RGB", (width, height), (7, 11, 19))
    gdraw = ImageDraw.Draw(glow)
    for radius in range(560, 0, -8):
        alpha = int(38 * (1 - radius / 560) ** 2)
        color = tuple(min(255, 7 + int(accent[i] * alpha / 255)) for i in range(3))
        gdraw.ellipse((width // 2 - radius, 120 - radius // 2,
                       width // 2 + radius, 120 + radius // 2), fill=color)
    canvas = Image.blend(canvas, glow, 0.72)
    draw = ImageDraw.Draw(canvas)
    # Lineas de escenario.
    for offset in (0, 1):
        draw.line((0, 590 + offset, width, 590 + offset),
                  fill=(30, 48, 78), width=1)
    draw.text((64, 44), f"VISION+ PLAY  ·  {game['name']}",
              fill=(150, 170, 200), font=ImageFont.truetype(FONT_BOLD, 24))
    draw.text((64, 82), "CÓMO SE JUEGA", fill=(245, 248, 255),
              font=ImageFont.truetype(FONT_BOLD, 56))
    tile = 300
    gap = 48
    total = tile * 3 + gap * 2
    start = (width - total) // 2
    for index, icon in enumerate(icons):
        x = start + index * (tile + gap)
        y = 220
        canvas.paste(icon.resize((tile, tile), Image.Resampling.LANCZOS),
                     (x, y), icon.resize((tile, tile), Image.Resampling.LANCZOS))
    draw = ImageDraw.Draw(canvas)
    draw.text((width - 64, height - 54), "1  ·  2  ·  3",
              anchor="ra", fill=accent,
              font=ImageFont.truetype(FONT_BOLD, 26))
    return canvas


def tutorial_contact_sheet() -> None:
    paths = sorted((DOCS / "tutorials").glob("*.png"))
    if not paths:
        return
    cell = 240
    cols = 3
    rows = math.ceil(len(paths) / cols)
    sheet = Image.new("RGB", (cols * cell, rows * cell), (7, 11, 19))
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.truetype(FONT_REGULAR, 11)
    for index, path in enumerate(paths):
        image = Image.open(path).convert("RGBA")
        image.thumbnail((cell - 12, cell - 28), Image.Resampling.LANCZOS)
        x = (index % cols) * cell
        y = (index // cols) * cell
        sheet.paste(image, (x + (cell - image.width) // 2, y + 4), image)
        draw.text((x + 6, y + cell - 18), path.stem, fill=(170, 188, 214),
                  font=font)
    sheet.save(DOCS / "tutorial_contact_sheet.png", optimize=True)
    sheet.save(MASTER / "tutorial_contact_sheet.png", optimize=True)


def build_particles() -> None:
    """Partículas simples, sin Comfy: se dibujan y quedan listas para Canvas."""
    size = 256
    transparent = (0, 0, 0, 0)

    confetti = Image.new("RGBA", (size, size), transparent)
    draw = ImageDraw.Draw(confetti)
    rng = random.Random(5013)
    colors = [(45, 125, 246, 245), (107, 230, 255, 245), (245, 197, 66, 245),
              (255, 255, 255, 235), (255, 107, 180, 235)]
    for _ in range(38):
        x, y = rng.randint(12, 244), rng.randint(12, 244)
        w, h = rng.randint(5, 13), rng.randint(9, 22)
        piece = Image.new("RGBA", (w * 2, h * 2), transparent)
        ImageDraw.Draw(piece).rounded_rectangle(
            (w // 2, h // 2, w + w // 2, h + h // 2), radius=2,
            fill=rng.choice(colors))
        piece = piece.rotate(rng.randint(0, 359), expand=False,
                             resample=Image.Resampling.BICUBIC)
        confetti.alpha_composite(piece, (x, y))
    confetti.save(ASSETS / "game_fx_confetti.webp", "WEBP", quality=90, method=6)
    confetti.save(DOCS / "particles" / "confetti.png", optimize=True)
    confetti.save(MASTER / "particles" / "confetti.png", optimize=True)

    coin = Image.new("RGBA", (size, size), transparent)
    draw = ImageDraw.Draw(coin)
    draw.ellipse((28, 20, 228, 236), fill=(120, 82, 8, 255))
    draw.ellipse((34, 14, 222, 222), fill=(245, 197, 66, 255),
                 outline=(255, 236, 160, 255), width=5)
    draw.ellipse((62, 42, 194, 198), outline=(180, 126, 18, 255), width=3)
    _star(draw, 128, 118, 44, (255, 245, 200, 245))
    _star(draw, 128, 118, 21, (180, 126, 18, 245))
    coin.save(ASSETS / "game_fx_coin.webp", "WEBP", quality=90, method=6)
    coin.save(DOCS / "particles" / "coin.png", optimize=True)
    coin.save(MASTER / "particles" / "coin.png", optimize=True)

    spark = Image.new("RGBA", (size, size), transparent)
    draw = ImageDraw.Draw(spark)
    _star(draw, 128, 128, 92, (107, 230, 255, 80))
    _star(draw, 128, 128, 64, (255, 255, 255, 235))
    _star(draw, 128, 128, 30, (255, 255, 255, 255))
    spark.save(ASSETS / "game_fx_spark.webp", "WEBP", quality=90, method=6)
    spark.save(DOCS / "particles" / "spark.png", optimize=True)
    spark.save(MASTER / "particles" / "spark.png", optimize=True)

    flame = Image.new("RGBA", (size, size), transparent)
    draw = ImageDraw.Draw(flame)
    draw.ellipse((52, 92, 204, 244), fill=(255, 77, 32, 210))
    draw.ellipse((74, 70, 182, 220), fill=(255, 150, 40, 235))
    draw.ellipse((98, 92, 158, 210), fill=(255, 230, 120, 245))
    draw.polygon([(128, 18), (166, 104), (90, 104)], fill=(255, 230, 120, 245))
    flame.save(ASSETS / "game_fx_fire.webp", "WEBP", quality=90, method=6)
    flame.save(DOCS / "particles" / "fire.png", optimize=True)
    flame.save(MASTER / "particles" / "fire.png", optimize=True)

    crown = Image.new("RGBA", (size, size), transparent)
    draw = ImageDraw.Draw(crown)
    draw.polygon([(30, 78), (70, 160), (128, 96), (186, 160), (226, 78),
                  (208, 206), (48, 206)], fill=(245, 197, 66, 255),
                 outline=(255, 240, 170, 255), width=4)
    for x, y in ((30, 78), (128, 64), (226, 78)):
        draw.ellipse((x - 13, y - 13, x + 13, y + 13),
                     fill=(107, 230, 255, 255))
    draw.rounded_rectangle((48, 170, 208, 206), radius=12,
                           fill=(180, 126, 18, 255))
    crown.save(ASSETS / "game_fx_crown.webp", "WEBP", quality=90, method=6)
    crown.save(DOCS / "particles" / "crown.png", optimize=True)
    crown.save(MASTER / "particles" / "crown.png", optimize=True)


def run_avatars(only: set[str], recompose: bool) -> None:
    (DOCS / "avatars").mkdir(parents=True, exist_ok=True)
    (MASTER / "avatars").mkdir(parents=True, exist_ok=True)
    for index, avatar in enumerate(AVATARS, 1):
        if only and avatar["id"] not in only:
            continue
        print(f"[avatar {index}/{len(AVATARS)}] {avatar['id']}", flush=True)
        raw = TMP / f"avatar_{avatar['id']}.png"
        if not recompose and not raw.exists():
            raw = generate(
                avatar["prompt"] + AVATAR_SUFFIX,
                781000 + index * 37, f"avatar_{avatar['id']}",
                width=1024, height=1024, steps=32, cfg=7.2,
                negative=NEG_AVATAR,
            )
        build_avatar_states(avatar, raw)
    avatar_contact_sheet()
    print("avatares listos", flush=True)


def run_tutorials(only: set[str], recompose: bool) -> None:
    (DOCS / "tutorials").mkdir(parents=True, exist_ok=True)
    (MASTER / "tutorials").mkdir(parents=True, exist_ok=True)
    for game_id, game in TUTORIALS.items():
        if only and game_id not in only:
            continue
        icons: list[Image.Image] = []
        for step_index, (step_id, description) in enumerate(game["steps"], 1):
            print(f"[tutorial {game_id} {step_index}/3] {step_id}", flush=True)
            raw = TMP / f"tutorial_{game_id}_{step_id}.png"
            if not recompose and not raw.exists():
                prompt = (
                    f"{description}, glossy 3D game icon, centered, front view, "
                    "premium party game asset, dark navy and electric blue palette, "
                    "soft studio lighting, isolated on flat chroma green background, "
                    "no text, no letters, no logo, no watermark"
                )
                raw = generate(
                    prompt, 782000 + step_index * 41 + sum(map(ord, game_id)),
                    f"tutorial_{game_id}_{step_id}",
                    width=768, height=768, steps=28, cfg=6.5,
                )
            icon = build_tutorial_icon(game, (step_id, description), raw)
            icons.append(icon)
            icon.save(ASSETS / f"game_tutorial_{game_id}_{step_id}.webp",
                      "WEBP", quality=90, method=6)
            icon.save(DOCS / "tutorials" / f"{game_id}_{step_id}.png",
                      optimize=True)
            icon.save(MASTER / "tutorials" / f"{game_id}_{step_id}.png",
                      optimize=True)
        card = tutorial_card(game_id, game, icons)
        card.save(ASSETS / f"game_tutorial_{game_id}.webp", "WEBP",
                  quality=88, method=6)
        card.save(DOCS / "tutorials" / f"{game_id}_card.png", optimize=True)
        card.save(MASTER / "tutorials" / f"{game_id}_card.png", optimize=True)
    tutorial_contact_sheet()
    print("tutoriales listos", flush=True)


def run_cards() -> None:
    """Recompone las tarjetas 16:9 con los iconos ya generados."""
    for game_id, game in TUTORIALS.items():
        icons = []
        for step_id, _ in game["steps"]:
            path = DOCS / "tutorials" / f"{game_id}_{step_id}.png"
            if not path.exists():
                raise SystemExit(f"Falta {path}; corre primero tutorials")
            icons.append(Image.open(path).convert("RGBA"))
        card = tutorial_card(game_id, game, icons)
        card.save(ASSETS / f"game_tutorial_{game_id}.webp", "WEBP",
                  quality=88, method=6)
        card.save(DOCS / "tutorials" / f"{game_id}_card.png", optimize=True)
        card.save(MASTER / "tutorials" / f"{game_id}_card.png", optimize=True)
    print("tarjetas listas", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=["avatars", "tutorials", "cards",
                                           "particles", "all"])
    parser.add_argument("--only", default="")
    parser.add_argument("--recompose", action="store_true",
                        help="No vuelve a llamar a ComfyUI; recompone con los raw")
    args = parser.parse_args()
    only = {x.strip() for x in args.only.split(",") if x.strip()}
    TMP.mkdir(parents=True, exist_ok=True)
    (DOCS / "particles").mkdir(parents=True, exist_ok=True)
    (MASTER / "particles").mkdir(parents=True, exist_ok=True)
    if args.target in ("avatars", "all"):
        run_avatars(only, args.recompose)
    if args.target in ("tutorials", "all"):
        run_tutorials(only, args.recompose)
    if args.target in ("cards", "all"):
        run_cards()
    if args.target in ("particles", "all"):
        build_particles()
        print("partículas listas", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
