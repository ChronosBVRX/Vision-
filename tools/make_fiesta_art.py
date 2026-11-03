#!/usr/bin/env python3
"""Arte procedural original para Vision+ Fiesta 3.0.

No usa ComfyUI ni assets de terceros. Genera seis key arts 16:9 sin texto,
con la paleta de cada juego, y guarda master + WebP para el APK.
"""

from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
MASTER = Path("/media/chronos/Disco Duro/vision-assets/games/v3/art")

W, H = 1600, 900

GAMES = {
    "ultima_palabra": {
        "bg": ((24, 10, 4), (80, 24, 8)),
        "accent": (249, 115, 22),
        "secondary": (251, 191, 36),
        "motif": "stage",
    },
    "vox_populi": {
        "bg": ((3, 22, 32), (5, 50, 65)),
        "accent": (34, 211, 238),
        "secondary": (59, 130, 246),
        "motif": "news",
    },
    "fuera_de_epoca": {
        "bg": ((18, 10, 38), (42, 22, 90)),
        "accent": (167, 139, 250),
        "secondary": (244, 114, 182),
        "motif": "time",
    },
    "texto_roto": {
        "bg": ((3, 26, 18), (6, 60, 44)),
        "accent": (52, 211, 153),
        "secondary": (34, 211, 238),
        "motif": "chat",
    },
    "franela_fest": {
        "bg": ((38, 8, 28), (80, 18, 52)),
        "accent": (244, 114, 182),
        "secondary": (251, 191, 36),
        "motif": "shirt",
    },
    "el_tasador": {
        "bg": ((38, 24, 4), (80, 50, 8)),
        "accent": (251, 191, 36),
        "secondary": (249, 115, 22),
        "motif": "auction",
    },
}


def gradient(c1, c2) -> Image.Image:
    image = Image.new("RGB", (W, H), c1)
    px = image.load()
    for y in range(H):
        t = y / max(H - 1, 1)
        for x in range(W):
            # Gradiente diagonal suave + profundidad radial.
            d = min(1.0, math.hypot((x - W * 0.5) / W, (y - H * 0.45) / H) * 1.6)
            mix = 0.15 + 0.85 * t
            r = int(c1[0] * (1 - mix) + c2[0] * mix)
            g = int(c1[1] * (1 - mix) + c2[1] * mix)
            b = int(c1[2] * (1 - mix) + c2[2] * mix)
            dark = int(1.0 - 0.42 * d)
            px[x, y] = (r * dark // 1, g * dark // 1, b * dark // 1)
    return image


def glow(image: Image.Image, xy, radius, color, alpha=120) -> None:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    x, y = xy
    for r in range(radius, 0, -8):
        a = int(alpha * (1 - r / radius) ** 1.3)
        draw.ellipse((x - r, y - r, x + r, y + r), fill=(*color, a))
    layer = layer.filter(ImageFilter.GaussianBlur(22))
    image.alpha_composite(layer) if image.mode == "RGBA" else image.paste(
        Image.alpha_composite(image.convert("RGBA"), layer).convert("RGB")
    )


def draw_stage(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Focos y tarjetas de respuesta.
    for i in range(3):
        x = 280 + i * 520
        draw.polygon([(x, 0), (x + 260, 0), (x + 90, H)], fill=(*accent, 22))
    for i in range(5):
        x = 160 + i * 300
        y = 420 + (i % 2) * 80
        draw.rounded_rectangle((x, y, x + 210, y + 130), radius=28, outline=(*secondary, 130), width=4)
        draw.rounded_rectangle((x + 18, y + 24, x + 160, y + 40), radius=8, fill=(*accent, 110))
        draw.rounded_rectangle((x + 18, y + 58, x + 190, y + 72), radius=8, fill=(*secondary, 80))
        draw.rounded_rectangle((x + 18, y + 92, x + 130, y + 106), radius=8, fill=(*secondary, 60))


def draw_news(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Pantalla de noticiero y barras de encuesta.
    draw.rounded_rectangle((180, 120, 1420, 780), radius=40, outline=(*accent, 130), width=6)
    for i in range(6):
        h = 60 + (i * 47) % 260
        x = 270 + i * 185
        draw.rounded_rectangle((x, 650 - h, x + 80, 650), radius=14, fill=(*accent, 90))
        draw.rounded_rectangle((x, 650, x + 80, 690), radius=10, fill=(*secondary, 55))
    draw.line((200, 600, 1400, 600), fill=(*secondary, 140), width=5)
    draw.ellipse((1120, 170, 1320, 370), outline=(*accent, 140), width=7)
    draw.arc((1140, 190, 1300, 350), 20, 320, fill=(*secondary, 180), width=8)


def draw_time(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Reloj, línea temporal y anillos.
    cx, cy = 800, 430
    for r in (330, 280, 220, 150):
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=(*accent, 55), width=3)
    draw.ellipse((cx - 210, cy - 210, cx + 210, cy + 210), outline=(*secondary, 140), width=7)
    for angle in range(0, 360, 30):
        a = math.radians(angle)
        x1 = cx + math.cos(a) * 180
        y1 = cy + math.sin(a) * 180
        x2 = cx + math.cos(a) * 205
        y2 = cy + math.sin(a) * 205
        draw.line((x1, y1, x2, y2), fill=(*accent, 120), width=4)
    draw.line((cx, cy, cx + 90, cy - 110), fill=(*secondary, 220), width=12)
    draw.line((cx, cy, cx - 120, cy + 40), fill=(*accent, 220), width=8)
    for i in range(7):
        x = 100 + i * 240
        draw.rounded_rectangle((x, 720, x + 160, 740), radius=10, fill=(*accent, 70))
        draw.ellipse((x + 60, 700, x + 100, 740), fill=(*secondary, 100))


def draw_chat(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Burbujas de chat, glitch y cursor.
    random.seed(7)
    for i in range(12):
        x = 160 + (i * 137) % 1200
        y = 150 + (i * 83) % 560
        w = 180 + (i * 37) % 280
        h = 52 + (i % 3) * 20
        color = accent if i % 3 == 0 else secondary
        draw.rounded_rectangle((x, y, x + w, y + h), radius=h // 2, fill=(*color, 35), outline=(*color, 90), width=2)
        draw.rounded_rectangle((x + 18, y + 18, x + w - 30, y + 28), radius=5, fill=(*color, 90))
        draw.rounded_rectangle((x + 18, y + 34, x + w - 70, y + 42), radius=4, fill=(*color, 55))
    for i in range(8):
        y = 120 + i * 95
        draw.rectangle((120, y, 1480, y + 3), fill=(*accent, 40))
    draw.rounded_rectangle((400, 610, 1200, 700), radius=30, outline=(*accent, 140), width=5)
    draw.rectangle((440, 645, 470, 685), fill=(*accent, 200))


def draw_shirt(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Camiseta central y pinceles.
    left, top, right, bottom = 520, 180, 1080, 770
    draw.polygon([
        (left, top + 120), (left + 150, top), (right - 150, top), (right, top + 120),
        (right - 80, top + 210), (right - 80, bottom), (left + 80, bottom),
        (left + 80, top + 210),
    ], fill=(248, 250, 252, 28), outline=(*accent, 160), width=6)
    draw.arc((left + 120, top + 40, right - 120, top + 250), 0, 180, fill=(*secondary, 180), width=8)
    for i in range(6):
        x = 220 + i * 220
        draw.line((x, 800, x + 40, 650), fill=(*secondary, 120), width=10)
        draw.ellipse((x + 24, 630, x + 56, 662), fill=(*accent, 170))
    random.seed(11)
    for _ in range(20):
        x = random.randint(600, 1000)
        y = random.randint(300, 650)
        r = random.randint(6, 22)
        draw.ellipse((x, y, x + r, y + r), fill=(*secondary, random.randint(40, 110)))


def draw_auction(draw: ImageDraw.ImageDraw, accent, secondary) -> None:
    # Pedestal, mazo y destellos de valor.
    draw.rounded_rectangle((480, 580, 1120, 760), radius=30, fill=(*accent, 28), outline=(*accent, 120), width=5)
    draw.rounded_rectangle((620, 520, 980, 590), radius=22, fill=(*secondary, 35), outline=(*secondary, 120), width=4)
    draw.line((800, 520, 800, 300), fill=(*accent, 170), width=12)
    draw.rounded_rectangle((650, 250, 950, 340), radius=30, fill=(*accent, 60), outline=(*accent, 180), width=6)
    draw.rounded_rectangle((700, 200, 900, 270), radius=25, fill=(*secondary, 55), outline=(*secondary, 160), width=4)
    for i in range(10):
        angle = i * math.pi / 5
        x = 800 + math.cos(angle) * 280
        y = 470 + math.sin(angle) * 180
        draw.line((800, 470, x, y), fill=(*secondary, 45), width=3)
        draw.ellipse((x - 8, y - 8, x + 8, y + 8), fill=(*secondary, 120))


def make(game: str, spec: dict) -> Path:
    image = gradient(spec["bg"][0], spec["bg"][1]).convert("RGBA")
    accent, secondary = spec["accent"], spec["secondary"]
    glow(image, (W * 0.5, H * 0.38), 520, accent, 90)
    glow(image, (W * 0.18, H * 0.72), 320, secondary, 55)
    glow(image, (W * 0.84, H * 0.24), 280, secondary, 45)
    draw = ImageDraw.Draw(image)
    motif = spec["motif"]
    if motif == "stage":
        draw_stage(draw, accent, secondary)
    elif motif == "news":
        draw_news(draw, accent, secondary)
    elif motif == "time":
        draw_time(draw, accent, secondary)
    elif motif == "chat":
        draw_chat(draw, accent, secondary)
    elif motif == "shirt":
        draw_shirt(draw, accent, secondary)
    else:
        draw_auction(draw, accent, secondary)
    # Viñeta radial: centro limpio, bordes oscuros.
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    md.ellipse((-W * 0.25, -H * 0.3, W * 1.25, H * 1.3), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(180))
    black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    image = Image.composite(image, black, mask).convert("RGB")
    ASSETS.mkdir(parents=True, exist_ok=True)
    MASTER.mkdir(parents=True, exist_ok=True)
    master = MASTER / f"{game}.png"
    image.save(master, optimize=True)
    out = ASSETS / f"game_{game}_art.webp"
    image.resize((1280, 720), Image.LANCZOS).save(out, "WEBP", quality=86, method=6)
    return out


def main() -> int:
    for game, spec in GAMES.items():
        target = make(game, spec)
        print(target.name, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
