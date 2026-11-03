#!/usr/bin/env python3
"""Arte original de Vision+ Fiesta (temporada 3.0, corte de identidad).

Genera, sin assets de terceros y sin texto legible:
  * 6 platós de tele 1920x1080 (drawable-nodpi/fiesta_stage_<juego>.webp)
  * 6 fondos de teléfono 1080x1920 (drawable-nodpi/fiesta_phone_<juego>.webp)
  * 6 retratos de presentador 900x900 (drawable-nodpi/fiesta_host_<juego>.webp)

Cada juego tiene composición, paleta y utilería propias. El objetivo no es
"un degradado con otro color": la tele de La Última Palabra es un estudio de
concurso, Vox Populi es un noticiero, Fuera de Época es una consola temporal,
Texto Roto es un chat roto, Franela Fest es una pasarela y El Tasador es una
casa de subastas.

Uso:
  python3 tools/make_fiesta_art_v2.py
  python3 tools/make_fiesta_art_v2.py --only ultima_palabra
"""

from __future__ import annotations

import argparse
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
MASTER = Path("/tmp/opencode/fiesta_art_masters")
TV = (1920, 1080)
PHONE = (1080, 1920)
HOST = (900, 900)
SS = 2  # supersampling para bordes limpios en la tele


def rgba(hex_value: str, alpha: int = 255) -> tuple[int, int, int, int]:
    value = hex_value.lstrip("#")
    if len(value) == 3:
        value = "".join(ch * 2 for ch in value)
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def mix(a, b, t: float):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(len(a)))


class Canvas:
    def __init__(self, size: tuple[int, int], scale: int = 1):
        self.size = size
        self.scale = scale
        self.w, self.h = size[0] * scale, size[1] * scale
        self.image = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 255))
        self.draw = ImageDraw.Draw(self.image, "RGBA")

    def xy(self, x: float, y: float) -> tuple[float, float]:
        return x * self.scale, y * self.scale

    def rect(self, x, y, w, h, fill=None, outline=None, width=1, radius=0):
        box = [x * self.scale, y * self.scale, (x + w) * self.scale, (y + h) * self.scale]
        if radius:
            self.draw.rounded_rectangle(box, radius=radius * self.scale, fill=fill, outline=outline,
                                        width=max(1, int(width * self.scale)))
        else:
            self.draw.rectangle(box, fill=fill, outline=outline, width=max(1, int(width * self.scale)))

    def ellipse(self, x, y, w, h, fill=None, outline=None, width=1):
        self.draw.ellipse([x * self.scale, y * self.scale, (x + w) * self.scale, (y + h) * self.scale],
                          fill=fill, outline=outline, width=max(1, int(width * self.scale)))

    def polygon(self, points, fill=None, outline=None, width=1):
        pts = [(x * self.scale, y * self.scale) for x, y in points]
        self.draw.polygon(pts, fill=fill)
        if outline:
            pts2 = pts + [pts[0]]
            self.draw.line(pts2, fill=outline, width=max(1, int(width * self.scale)))

    def line(self, points, fill=None, width=1):
        pts = [(x * self.scale, y * self.scale) for x, y in points]
        self.draw.line(pts, fill=fill, width=max(1, int(width * self.scale)))

    def arc(self, box, start, end, fill=None, width=1):
        self.draw.arc([v * self.scale for v in box], start, end, fill=fill,
                      width=max(1, int(width * self.scale)))

    def glow(self, x, y, radius, color, alpha=90, blur=40):
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        cx, cy, r = x * self.scale, y * self.scale, radius * self.scale
        for step in range(10, 0, -1):
            rr = r * step / 10
            a = int(alpha * (1 - step / 11) ** 1.4)
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(*color[:3], a))
        layer = layer.filter(ImageFilter.GaussianBlur(blur * self.scale / 2))
        self.image = Image.alpha_composite(self.image, layer)
        self.draw = ImageDraw.Draw(self.image, "RGBA")

    def vignette(self, strength=190):
        layer = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        px = layer.load()
        cx, cy = self.w / 2, self.h / 2
        maxd = math.hypot(cx, cy)
        step = max(1, self.w // 220)
        for yy in range(0, self.h, step):
            for xx in range(0, self.w, step):
                d = math.hypot(xx - cx, yy - cy) / maxd
                a = int(max(0.0, d - 0.35) ** 1.7 * strength)
                if a <= 0:
                    continue
                for dy in range(step):
                    for dx in range(step):
                        if xx + dx < self.w and yy + dy < self.h:
                            px[xx + dx, yy + dy] = (0, 0, 0, a)
        layer = layer.filter(ImageFilter.GaussianBlur(18 * self.scale))
        self.image = Image.alpha_composite(self.image, layer)
        self.draw = ImageDraw.Draw(self.image, "RGBA")

    def grain(self, amount=13, seed=7):
        rnd = random.Random(seed)
        small = Image.new("L", (max(1, self.w // 2), max(1, self.h // 2)))
        small.putdata([128 + rnd.randint(-amount, amount) for _ in range(small.width * small.height)])
        noise = small.resize((self.w, self.h), Image.Resampling.BILINEAR).convert("RGBA")
        noise.putalpha(20)
        self.image = Image.alpha_composite(self.image, noise)
        self.draw = ImageDraw.Draw(self.image, "RGBA")

    def save(self, path: Path, quality=88):
        path.parent.mkdir(parents=True, exist_ok=True)
        image = self.image.convert("RGB")
        if self.scale != 1:
            image = image.resize(self.size, Image.Resampling.LANCZOS)
        image.save(path, "WEBP", quality=quality, method=6)
        MASTER.mkdir(parents=True, exist_ok=True)
        image.save(MASTER / (path.stem + ".png"))


def background(c: Canvas, top: str, bottom: str, glow_color: str, glow_xy=(0.5, 0.4)):
    w, h = c.w, c.h
    top_rgb, bottom_rgb = rgba(top)[:3], rgba(bottom)[:3]
    # Degradado vertical rápido con bandas de 4 px.
    for y in range(0, h, 4):
        t = y / max(1, h - 1)
        color = mix(top_rgb, bottom_rgb, t ** 0.9)
        c.draw.rectangle([0, y, w, min(h, y + 4)], fill=(*color, 255))
    c.glow(w * glow_xy[0], h * glow_xy[1], max(w, h) * 0.52, rgba(glow_color)[:3], 110, 60)
    # Piso/atmósfera inferior.
    c.draw.rectangle([0, h * 0.74, w, h], fill=(0, 0, 0, 55))


def floor_grid(c: Canvas, horizon=0.66, color=(255, 255, 255, 28), columns=18):
    w, h = c.w, c.h
    hy = h * horizon
    for i in range(columns + 1):
        x = w * i / columns
        c.line([(x, h), (w * 0.5 + (x - w * 0.5) * 0.22, hy)], fill=color, width=2)
    y = hy
    step = 12
    while y < h:
        c.line([(0, y), (w, y)], fill=color, width=2)
        step *= 1.24
        y += step


def spotlight(c: Canvas, x, y, width, color, alpha=36):
    c.polygon([(x - width * 0.12, y), (x + width * 0.12, y), (x + width, c.h), (x - width, c.h)],
              fill=(*rgba(color)[:3], alpha))
    c.glow(x, y, width * 0.42, rgba(color)[:3], 45, 34)


def bulbs(c: Canvas, y, color, count=24, radius=6):
    for i in range(count):
        x = c.w * (i + 0.5) / count
        c.glow(x, y, radius * 3, rgba(color)[:3], 34, 12)
        c.ellipse(x - radius, y - radius, radius * 2, radius * 2, fill=(*rgba(color)[:3], 230))


def silhouette(c: Canvas, x, y, scale, color=(0, 0, 0, 150), pose="sit"):
    """Público en silueta; solo cabeza y hombros."""
    c.ellipse(x - 20 * scale, y - 24 * scale, 40 * scale, 44 * scale, fill=color)
    c.ellipse(x - 42 * scale, y + 6 * scale, 84 * scale, 70 * scale, fill=color)
    if pose == "up":
        c.ellipse(x + 26 * scale, y - 44 * scale, 22 * scale, 22 * scale, fill=color)


def film_burn(c: Canvas, x, y, r, color):
    c.glow(x, y, r, rgba(color)[:3], 110, 26)
    c.ellipse(x - r * 0.12, y - r * 0.12, r * 0.24, r * 0.24, fill=(255, 255, 255, 180))


# ---------------------------------------------------------------------------
# La Última Palabra: estudio de concurso nocturno, dos tarjetas enfrentadas.
# ---------------------------------------------------------------------------
def stage_ultima_palabra():
    c = Canvas(TV, SS)
    background(c, "#1a0704", "#4a1206", "#ff7a18", (0.5, 0.28))
    floor_grid(c, 0.7, (255, 140, 60, 24), 16)
    for x, col in ((0.24, "#ff4d1a"), (0.76, "#ffb020")):
        spotlight(c, c.w * x, 0, 420, col, 30)
        bulbs(c, c.h * 0.11, col, 16, 5)
    # Marco de escenario.
    c.rect(c.w * 0.06, c.h * 0.12, c.w * 0.88, c.h * 0.7, outline=(255, 160, 80, 90), width=5, radius=36)
    # Tarjetas gigantes inclinadas (A / B) sin texto.
    for side, tilt, color in ((-1, -7, "#ff7a18"), (1, 7, "#ffd166")):
        cx = c.w * (0.33 if side < 0 else 0.67)
        cy = c.h * 0.48
        card = Image.new("RGBA", (int(520 * SS), int(360 * SS)), (0, 0, 0, 0))
        d = ImageDraw.Draw(card, "RGBA")
        d.rounded_rectangle([0, 0, card.width - 1, card.height - 1], radius=36 * SS,
                            fill=(*rgba(color)[:3], 225), outline=(255, 255, 255, 90), width=5 * SS)
        # Líneas de "respuesta" en la tarjeta.
        for i in range(3):
            d.rounded_rectangle([44 * SS, (60 + i * 78) * SS, (card.width - 44 - i * 70) * SS, (96 + i * 78) * SS],
                                radius=12 * SS, fill=(30, 9, 4, 120))
        card = card.rotate(tilt, expand=True, resample=Image.Resampling.BICUBIC)
        c.image.alpha_composite(card, (int(cx - card.width / 2), int(cy - card.height / 2)))
    c.draw = ImageDraw.Draw(c.image, "RGBA")
    # "VS" central: dos triángulos.
    c.polygon([(c.w * 0.5 - 80, c.h * 0.43), (c.w * 0.5 - 10, c.h * 0.5), (c.w * 0.5 - 80, c.h * 0.57)],
              fill=(255, 245, 220, 225))
    c.polygon([(c.w * 0.5 + 80, c.h * 0.43), (c.w * 0.5 + 10, c.h * 0.5), (c.w * 0.5 + 80, c.h * 0.57)],
              fill=(255, 245, 220, 225))
    # Aplausómetro a la derecha.
    c.rect(c.w * 0.9, c.h * 0.26, 34, c.h * 0.44, fill=(10, 4, 2, 150), radius=17)
    for i in range(9):
        h = 20 + i * 20
        c.rect(c.w * 0.9 + 8, c.h * 0.68 - h, 18, h, fill=(*rgba("#ffb020")[:3], 190 - i * 12), radius=8)
    # Público.
    for i in range(14):
        x = c.w * (0.04 + i * 0.072)
        silhouette(c, x, c.h * 0.93, 1.35, (0, 0, 0, 165), "up" if i % 3 == 0 else "sit")
    # Confeti suspendido.
    rnd = random.Random(31)
    for _ in range(70):
        x, y = rnd.uniform(0, c.w), rnd.uniform(c.h * 0.15, c.h * 0.8)
        c.rect(x, y, 5, 10, fill=(*rgba(rnd.choice(["#ff7a18", "#ffd166", "#ff4d6d"]))[:3], rnd.randint(60, 150)),
               radius=2)
    c.vignette(215)
    c.grain(12, 3)
    c.save(OUT / "fiesta_stage_ultima_palabra.webp", quality=95)


# ---------------------------------------------------------------------------
# Vox Populi: noticiero, escritorio, gráfica y cintillo.
# ---------------------------------------------------------------------------
def stage_vox_populi():
    c = Canvas(TV, SS)
    background(c, "#021018", "#073b4c", "#22d3ee", (0.5, 0.3))
    # Pared de pantallas y mapa abstracto.
    for row in range(3):
        for col in range(5):
            x = c.w * (0.08 + col * 0.17)
            y = c.h * (0.12 + row * 0.14)
            c.rect(x, y, c.w * 0.14, c.h * 0.105, fill=(10, 50, 62, 90), outline=(34, 211, 238, 60), width=2, radius=8)
            for line in range(3):
                c.rect(x + 16, y + 20 + line * 18, c.w * 0.14 - 40 - line * 20, 4, fill=(34, 211, 238, 70), radius=2)
    c.glow(c.w * 0.2, c.h * 0.35, 300, rgba("#22d3ee")[:3], 70, 50)
    # Escritorio del noticiero.
    c.polygon([(c.w * 0.08, c.h * 0.82), (c.w * 0.92, c.h * 0.82), (c.w * 0.86, c.h * 0.95), (c.w * 0.14, c.h * 0.95)],
              fill=(6, 26, 34, 235))
    c.line([(c.w * 0.1, c.h * 0.82), (c.w * 0.9, c.h * 0.82)], fill=(34, 211, 238, 160), width=5)
    # Gráfica de barras grande a la izquierda.
    for i, h in enumerate((0.18, 0.31, 0.25, 0.44, 0.36, 0.52)):
        x = c.w * (0.1 + i * 0.055)
        c.rect(x, c.h * 0.78 - c.h * h, c.w * 0.035, c.h * h, fill=(*rgba("#22d3ee")[:3], 120), radius=8)
        c.rect(x, c.h * 0.78 - 10, c.w * 0.035, 10, fill=(255, 210, 80, 170), radius=4)
    # Cintillo inferior.
    c.rect(0, c.h * 0.88, c.w, c.h * 0.06, fill=(2, 16, 22, 225))
    c.rect(0, c.h * 0.88, c.w * 0.18, c.h * 0.06, fill=(*rgba("#ff3b3b")[:3], 235))
    for i in range(12):
        c.rect(c.w * (0.2 + i * 0.065), c.h * 0.9, c.w * 0.045, 7, fill=(255, 255, 255, 55), radius=3)
    # Cámara y luces de estudio.
    c.ellipse(c.w * 0.56, c.h * 0.18, 120, 120, fill=(3, 16, 22, 220), outline=(34, 211, 238, 90), width=5)
    c.ellipse(c.w * 0.56 + 28, c.h * 0.18 + 28, 64, 64, fill=(34, 211, 238, 80))
    c.polygon([(c.w * 0.58, c.h * 0.24), (c.w * 0.7, c.h * 0.33), (c.w * 0.58, c.h * 0.42)], fill=(3, 16, 22, 150))
    # Público/periodistas.
    for i in range(10):
        x = c.w * (0.06 + i * 0.1)
        silhouette(c, x, c.h * 0.84, 1.1, (0, 8, 12, 170))
        c.rect(x - 14, c.h * 0.76, 28, 34, fill=(230, 240, 245, 60), radius=4)
    c.vignette(200)
    c.grain(10, 9)
    c.save(OUT / "fiesta_stage_vox_populi.webp", quality=95)


# ---------------------------------------------------------------------------
# Fuera de Época: consola temporal, anillos, relojes y CRT.
# ---------------------------------------------------------------------------
def stage_fuera_de_epoca():
    c = Canvas(TV, SS)
    background(c, "#0d061f", "#2a1458", "#a78bfa", (0.5, 0.36))
    # Anillos concéntricos del tiempo.
    for i, radius in enumerate((420, 330, 245, 165)):
        c.ellipse(c.w * 0.5 - radius, c.h * 0.43 - radius, radius * 2, radius * 2,
                  outline=(*rgba("#a78bfa" if i % 2 == 0 else "#f472b6")[:3], 70 - i * 10), width=4)
    for i in range(12):
        ang = math.radians(i * 30)
        x1 = c.w * 0.5 + math.cos(ang) * 420
        y1 = c.h * 0.43 + math.sin(ang) * 420
        x2 = c.w * 0.5 + math.cos(ang) * 452
        y2 = c.h * 0.43 + math.sin(ang) * 452
        c.line([(x1, y1), (x2, y2)], fill=(244, 114, 182, 160), width=6)
    c.glow(c.w * 0.5, c.h * 0.43, 260, rgba("#c4b5fd")[:3], 110, 50)
    # Núcleo/portal.
    c.ellipse(c.w * 0.5 - 130, c.h * 0.43 - 130, 260, 260, fill=(12, 6, 30, 235),
              outline=(230, 220, 255, 170), width=6)
    c.ellipse(c.w * 0.5 - 80, c.h * 0.43 - 80, 160, 160, fill=(*rgba("#7c3aed")[:3], 100))
    # Paneles CRT laterales.
    for side in (0.07, 0.79):
        c.rect(c.w * side, c.h * 0.2, c.w * 0.14, c.h * 0.34, fill=(6, 4, 18, 210),
               outline=(167, 139, 250, 110), width=4, radius=12)
        for i in range(7):
            c.rect(c.w * side + 18, c.h * 0.24 + i * 33, c.w * 0.14 - 36, 5,
                   fill=(*rgba("#a78bfa")[:3], 110 - i * 10), radius=2)
    # Engranajes.
    for cx, cy, r in ((0.17, 0.76, 70), (0.83, 0.79, 90), (0.72, 0.17, 46)):
        c.glow(c.w * cx, c.h * cy, r * 2.4, rgba("#f472b6")[:3], 40, 26)
        c.ellipse(c.w * cx - r, c.h * cy - r, r * 2, r * 2, outline=(244, 114, 182, 120), width=5)
        for k in range(10):
            ang = math.radians(k * 36)
            c.line([(c.w * cx + math.cos(ang) * r * 0.7, c.h * cy + math.sin(ang) * r * 0.7),
                    (c.w * cx + math.cos(ang) * r * 1.05, c.h * cy + math.sin(ang) * r * 1.05)],
                   fill=(244, 114, 182, 120), width=4)
    # Línea temporal inferior.
    c.line([(c.w * 0.08, c.h * 0.88), (c.w * 0.92, c.h * 0.88)], fill=(255, 255, 255, 70), width=4)
    for i in range(9):
        x = c.w * (0.1 + i * 0.1)
        c.line([(x, c.h * 0.86), (x, c.h * 0.9)], fill=(255, 255, 255, 100), width=3)
        c.ellipse(x - 8, c.h * 0.88 - 8, 16, 16, fill=(*rgba("#f472b6")[:3], 180))
    # Chispas/estática.
    rnd = random.Random(12)
    for _ in range(120):
        x, y = rnd.uniform(0, c.w), rnd.uniform(0, c.h)
        c.rect(x, y, rnd.randint(2, 6), rnd.randint(2, 5),
               fill=(*rgba(rnd.choice(["#a78bfa", "#f472b6", "#22d3ee"]))[:3], rnd.randint(40, 140)))
    c.vignette(220)
    c.grain(14, 11)
    c.save(OUT / "fiesta_stage_fuera_de_epoca.webp", quality=95)


# ---------------------------------------------------------------------------
# Texto Roto: chat a pantalla completa, burbujas flotantes y glitch.
# ---------------------------------------------------------------------------
def stage_texto_roto():
    c = Canvas(TV, SS)
    background(c, "#03140f", "#073b2c", "#34d399", (0.5, 0.35))
    # Ventana de chat.
    c.rect(c.w * 0.08, c.h * 0.1, c.w * 0.84, c.h * 0.8, fill=(4, 20, 16, 205),
           outline=(52, 211, 153, 90), width=4, radius=32)
    c.rect(c.w * 0.08, c.h * 0.1, c.w * 0.84, c.h * 0.09, fill=(8, 38, 29, 240), radius=32)
    c.rect(c.w * 0.08, c.h * 0.17, c.w * 0.84, c.h * 0.02, fill=(8, 38, 29, 240))
    c.ellipse(c.w * 0.11, c.h * 0.13, 30, 30, fill=(52, 211, 153, 220))
    c.ellipse(c.w * 0.125, c.h * 0.13, 30, 30, fill=(250, 204, 21, 180))
    c.ellipse(c.w * 0.14, c.h * 0.13, 30, 30, fill=(248, 113, 113, 160))
    # Burbujas de chat alternadas.
    rnd = random.Random(44)
    for i in range(7):
        left = i % 3 != 1
        bw = rnd.uniform(0.24, 0.42) * c.w
        bh = rnd.uniform(0.07, 0.12) * c.h
        x = c.w * (0.12 if left else 0.54)
        y = c.h * (0.24 + i * 0.088)
        col = "#34d399" if left else "#0ea5e9"
        c.rect(x, y, bw, bh, fill=(*rgba(col)[:3], 70 if left else 55),
               outline=(*rgba(col)[:3], 100), width=2, radius=18)
        for line in range(2):
            c.rect(x + 22, y + 20 + line * 20, bw * (0.66 - line * 0.18), 7,
                   fill=(235, 255, 248, 80), radius=3)
    # Indicador "escribiendo".
    c.rect(c.w * 0.12, c.h * 0.8, c.w * 0.18, c.h * 0.055, fill=(8, 38, 29, 230), radius=20)
    for i in range(3):
        c.ellipse(c.w * 0.135 + i * 34, c.h * 0.815, 14, 14, fill=(52, 211, 153, 200))
    # Glitch bars.
    for i in range(8):
        y = c.h * (0.13 + i * 0.1)
        c.rect(c.w * rnd.uniform(0.02, 0.2), y, c.w * rnd.uniform(0.1, 0.35), rnd.randint(4, 10),
               fill=(*rgba(rnd.choice(["#34d399", "#22d3ee", "#f472b6"]))[:3], rnd.randint(25, 70)))
    # Reacciones flotantes.
    for i in range(14):
        x, y = rnd.uniform(0.1, 0.86) * c.w, rnd.uniform(0.15, 0.85) * c.h
        c.glow(x, y, 34, rgba("#34d399")[:3], 45, 18)
        c.ellipse(x - 12, y - 12, 24, 24, fill=(*rgba("#a7f3d0")[:3], 180))
    c.vignette(205)
    c.grain(12, 17)
    c.save(OUT / "fiesta_stage_texto_roto.webp", quality=95)


# ---------------------------------------------------------------------------
# Franela Fest: pasarela, maniquíes, flashes y jurado.
# ---------------------------------------------------------------------------
def stage_franela_fest():
    c = Canvas(TV, SS)
    background(c, "#1b0614", "#5a1238", "#f472b6", (0.5, 0.3))
    floor_grid(c, 0.62, (255, 190, 225, 26), 14)
    # Pasarela en perspectiva.
    c.polygon([(c.w * 0.42, c.h * 0.52), (c.w * 0.58, c.h * 0.52), (c.w * 0.86, c.h), (c.w * 0.14, c.h)],
              fill=(40, 10, 26, 150), outline=(244, 114, 182, 90), width=5)
    for x, color in ((0.24, "#f472b6"), (0.76, "#fbbf24")):
        spotlight(c, c.w * x, 0, 420, color, 34)
    # Marco de pasarela con bombillas.
    bulbs(c, c.h * 0.2, "#fbbf24", 20, 6)
    # Maniquíes con camisetas.
    for i, cx in enumerate((0.3, 0.5, 0.7)):
        x = c.w * cx
        base = c.h * 0.82
        c.ellipse(x - 30, base - 250, 60, 70, fill=(20, 6, 14, 220))
        c.polygon([(x - 55, base - 190), (x + 55, base - 190), (x + 80, base - 40), (x - 80, base - 40)],
                  fill=(20, 6, 14, 220))
        # Camiseta.
        col = ("#f472b6", "#fbbf24", "#22d3ee")[i]
        c.polygon([(x - 62, base - 210), (x - 20, base - 235), (x + 20, base - 235), (x + 62, base - 210),
                   (x + 92, base - 150), (x + 58, base - 135), (x + 58, base - 55),
                   (x - 58, base - 55), (x - 58, base - 135), (x - 92, base - 150)],
                  fill=(*rgba(col)[:3], 200), outline=(255, 255, 255, 80), width=3)
        # Estampado abstracto.
        c.ellipse(x - 24, base - 175, 48, 48, fill=(30, 8, 20, 120))
        c.line([(x - 40, base - 110), (x + 40, base - 110)], fill=(30, 8, 20, 110), width=5)
    # Banco del jurado.
    c.polygon([(c.w * 0.12, c.h * 0.73), (c.w * 0.45, c.h * 0.73), (c.w * 0.43, c.h * 0.85), (c.w * 0.14, c.h * 0.85)],
              fill=(30, 6, 20, 220), outline=(251, 191, 36, 80), width=4)
    for i in range(3):
        c.ellipse(c.w * (0.16 + i * 0.09), c.h * 0.69, 54, 54, fill=(30, 8, 18, 235))
        c.rect(c.w * (0.15 + i * 0.09), c.h * 0.765, 46, 60, fill=(30, 8, 18, 235), radius=12)
    # Flashes de cámara.
    rnd = random.Random(77)
    for _ in range(18):
        x, y = rnd.uniform(0.05, 0.95) * c.w, rnd.uniform(0.15, 0.75) * c.h
        film_burn(c, x, y, rnd.uniform(10, 34), "#fff2c4")
    # Confeti rosa.
    for _ in range(80):
        x, y = rnd.uniform(0, c.w), rnd.uniform(0, c.h)
        c.rect(x, y, rnd.randint(3, 7), rnd.randint(8, 16),
               fill=(*rgba(rnd.choice(["#f472b6", "#fbbf24", "#fda4af"]))[:3], rnd.randint(60, 160)), radius=2)
    c.vignette(210)
    c.grain(11, 23)
    c.save(OUT / "fiesta_stage_franela_fest.webp", quality=95)


# ---------------------------------------------------------------------------
# El Tasador: casa de subastas, cortinas, podio y martillo.
# ---------------------------------------------------------------------------
def stage_el_tasador():
    c = Canvas(TV, SS)
    background(c, "#1a1003", "#4a2c05", "#fbbf24", (0.5, 0.3))
    # Cortinas laterales.
    for side in (0, 1):
        for i in range(8):
            x = (0.06 + i * 0.035) if side == 0 else (0.7 + i * 0.035)
            c.rect(c.w * x, 0, c.w * 0.04, c.h, fill=(*rgba("#3b0a0a")[:3], 150 - i * 8), radius=20)
    # Arco de escenario.
    c.rect(c.w * 0.18, c.h * 0.08, c.w * 0.64, c.h * 0.76, fill=(12, 7, 2, 160),
           outline=(251, 191, 36, 110), width=7, radius=48)
    c.rect(c.w * 0.205, c.h * 0.105, c.w * 0.59, c.h * 0.71, fill=(20, 12, 3, 120),
           outline=(251, 191, 36, 45), width=2, radius=40)
    # Foco central y podio.
    c.glow(c.w * 0.5, c.h * 0.38, 330, rgba("#fbbf24")[:3], 90, 55)
    c.polygon([(c.w * 0.4, c.h * 0.58), (c.w * 0.6, c.h * 0.58), (c.w * 0.66, c.h * 0.86), (c.w * 0.34, c.h * 0.86)],
              fill=(28, 16, 4, 235), outline=(251, 191, 36, 120), width=5)
    c.rect(c.w * 0.38, c.h * 0.63, c.w * 0.24, c.h * 0.025, fill=(251, 191, 36, 80), radius=8)
    c.rect(c.w * 0.42, c.h * 0.67, c.w * 0.16, c.h * 0.11, fill=(8, 5, 1, 220),
           outline=(251, 191, 36, 70), width=3, radius=12)
    # Cuadros y lotes en las paredes.
    for i in range(3):
        x = c.w * (0.23 + i * 0.025)
        y = c.h * (0.2 + (i % 2) * 0.12)
        c.rect(x, y, c.w * 0.06, c.h * 0.1, fill=(30, 20, 6, 180),
               outline=(251, 191, 36, 80), width=3, radius=4)
    # Martillo gigante a la derecha.
    c.line([(c.w * 0.83, c.h * 0.28), (c.w * 0.8, c.h * 0.52)], fill=(90, 55, 12, 240), width=22)
    c.rect(c.w * 0.76, c.h * 0.23, c.w * 0.11, c.h * 0.11, fill=(120, 72, 16, 245),
           outline=(251, 191, 36, 130), width=4, radius=14)
    # Etiquetas de precio colgando.
    rnd = random.Random(5)
    for i in range(6):
        x = rnd.uniform(0.15, 0.85) * c.w
        y = rnd.uniform(0.12, 0.35) * c.h
        c.line([(x, 0), (x, y)], fill=(251, 191, 36, 50), width=2)
        c.rect(x - 34, y, 68, 42, fill=(*rgba("#f5e6c8")[:3], 160), radius=6)
        c.rect(x - 22, y + 12, 44, 5, fill=(60, 35, 8, 150), radius=2)
        c.rect(x - 22, y + 24, 30, 5, fill=(60, 35, 8, 110), radius=2)
    # Público con paletas de puja.
    for i in range(9):
        x = c.w * (0.08 + i * 0.1)
        silhouette(c, x, c.h * 0.9, 1.15, (0, 0, 0, 180))
        c.rect(x + 24, c.h * 0.72, 30, 52, fill=(*rgba("#f5e6c8")[:3], 90), radius=4)
        c.rect(x + 31, c.h * 0.735, 16, 6, fill=(60, 35, 8, 100), radius=2)
    c.vignette(225)
    c.grain(13, 31)
    c.save(OUT / "fiesta_stage_el_tasador.webp", quality=95)


# ---------------------------------------------------------------------------
# Fondos de teléfono: misma identidad, mucho menos ruido para no competir
# con los controles. Vertical 1080x1920.
# ---------------------------------------------------------------------------
def phone_bg(name: str, top: str, bottom: str, accent: str, motif: str, seed: int):
    c = Canvas(PHONE, 1)
    background(c, top, bottom, accent, (0.5, 0.25))
    rnd = random.Random(seed)
    if motif == "stage":
        for x in (0.18, 0.82):
            spotlight(c, c.w * x, 0, 260, accent, 26)
        bulbs(c, c.h * 0.08, accent, 10, 5)
        for i in range(24):
            x, y = rnd.uniform(0, c.w), rnd.uniform(0, c.h)
            c.rect(x, y, rnd.randint(3, 6), rnd.randint(8, 16), fill=(*rgba(accent)[:3], 50), radius=2)
    elif motif == "news":
        for i in range(7):
            c.rect(0, c.h * (0.1 + i * 0.11), c.w, 3, fill=(*rgba(accent)[:3], 28))
        for i in range(5):
            h = rnd.uniform(0.08, 0.3) * c.h
            c.rect(c.w * (0.1 + i * 0.17), c.h * 0.8 - h, c.w * 0.08, h, fill=(*rgba(accent)[:3], 36), radius=8)
    elif motif == "time":
        for r in (c.w * 0.62, c.w * 0.46, c.w * 0.3):
            c.ellipse(c.w * 0.5 - r, c.h * 0.3 - r, r * 2, r * 2, outline=(*rgba(accent)[:3], 40), width=4)
        for i in range(10):
            x, y = rnd.uniform(0, c.w), rnd.uniform(0, c.h)
            c.rect(x, y, 3, 14, fill=(*rgba(accent)[:3], 45))
    elif motif == "chat":
        for i in range(6):
            left = i % 2 == 0
            x = c.w * (0.08 if left else 0.46)
            y = c.h * (0.14 + i * 0.12)
            c.rect(x, y, c.w * rnd.uniform(0.3, 0.44), c.h * 0.06,
                   fill=(*rgba(accent)[:3], 26), outline=(*rgba(accent)[:3], 34), width=2, radius=22)
    elif motif == "runway":
        floor_grid(c, 0.5, (*rgba(accent)[:3], 28), 8)
        for x in (0.25, 0.75):
            spotlight(c, c.w * x, 0, 300, accent, 28)
        for _ in range(40):
            x, y = rnd.uniform(0, c.w), rnd.uniform(0, c.h)
            c.rect(x, y, 4, 10, fill=(*rgba(accent)[:3], 50), radius=2)
    elif motif == "auction":
        c.rect(0, 0, c.w * 0.12, c.h, fill=(*rgba("#3b0a0a")[:3], 80))
        c.rect(c.w * 0.88, 0, c.w * 0.12, c.h, fill=(*rgba("#3b0a0a")[:3], 80))
        for i in range(4):
            x = c.w * (0.15 + i * 0.22)
            c.rect(x, c.h * 0.1, c.w * 0.1, c.h * 0.06, fill=(*rgba("#f5e6c8")[:3], 26), radius=6)
    c.vignette(170)
    c.grain(9, seed)
    c.save(OUT / f"fiesta_phone_{name}.webp", quality=84)


# ---------------------------------------------------------------------------
# Retratos de presentador: personajes planos, originales, con accesorios.
# ---------------------------------------------------------------------------
def host_base(accent: str, bg1: str, bg2: str, seed: int):
    c = Canvas(HOST, 2)
    background(c, bg1, bg2, accent, (0.5, 0.42))
    rnd = random.Random(seed)
    # Patrón radial.
    for i in range(9):
        r = 90 + i * 44
        c.ellipse(c.w * 0.5 - r, c.h * 0.46 - r, r * 2, r * 2, outline=(*rgba(accent)[:3], 26), width=3)
    c.glow(c.w * 0.5, c.h * 0.44, 300, rgba(accent)[:3], 80, 50)
    return c


def host_shirt(c: Canvas, color: str, width=0.52):
    w, h = c.w, c.h
    c.polygon([(w * (0.5 - width / 2), h * 0.66), (w * 0.5, h * 0.6), (w * (0.5 + width / 2), h * 0.66),
               (w * 0.82, h * 0.86), (w * 0.18, h * 0.86), (w * 0.5 - width * 0.42, h * 0.7)],
              fill=(*rgba(color)[:3], 235))


def host_head(c: Canvas, skin="#f2c39b", hair="#2a1208", style="bob"):
    w, h = c.w, c.h
    # Cuello y hombros.
    c.rect(w * 0.455, h * 0.58, w * 0.09, h * 0.1, fill=(*rgba(skin)[:3], 255), radius=18)
    # Cabeza.
    c.ellipse(w * 0.36, h * 0.27, w * 0.28, h * 0.35, fill=(*rgba(skin)[:3], 255),
              outline=(60, 25, 15, 40), width=3)
    # Orejas.
    c.ellipse(w * 0.345, h * 0.4, w * 0.035, h * 0.06, fill=(*rgba(skin)[:3], 255))
    c.ellipse(w * 0.62, h * 0.4, w * 0.035, h * 0.06, fill=(*rgba(skin)[:3], 255))
    if style == "bob":
        c.ellipse(w * 0.33, h * 0.21, w * 0.34, h * 0.32, fill=(*rgba(hair)[:3], 255))
        c.rect(w * 0.33, h * 0.36, w * 0.07, h * 0.22, fill=(*rgba(hair)[:3], 255), radius=24)
        c.rect(w * 0.6, h * 0.36, w * 0.07, h * 0.22, fill=(*rgba(hair)[:3], 255), radius=24)
        c.ellipse(w * 0.36, h * 0.24, w * 0.28, h * 0.2, fill=(*rgba(skin)[:3], 255))
    elif style == "slick":
        c.ellipse(w * 0.345, h * 0.2, w * 0.31, h * 0.18, fill=(*rgba(hair)[:3], 255))
        c.polygon([(w * 0.34, h * 0.29), (w * 0.5, h * 0.18), (w * 0.66, h * 0.29), (w * 0.6, h * 0.33),
                   (w * 0.4, h * 0.33)], fill=(*rgba(hair)[:3], 255))
    elif style == "white":
        c.ellipse(w * 0.335, h * 0.19, w * 0.33, h * 0.2, fill=(*rgba(hair)[:3], 255))
        c.rect(w * 0.33, h * 0.3, w * 0.08, h * 0.16, fill=(*rgba(hair)[:3], 255), radius=20)
        c.rect(w * 0.6, h * 0.3, w * 0.08, h * 0.16, fill=(*rgba(hair)[:3], 255), radius=20)
    elif style == "pomp":
        c.ellipse(w * 0.33, h * 0.17, w * 0.34, h * 0.23, fill=(*rgba(hair)[:3], 255))
        c.ellipse(w * 0.3, h * 0.2, w * 0.2, h * 0.12, fill=(*rgba(hair)[:3], 255))
    elif style == "hat":
        c.ellipse(w * 0.3, h * 0.2, w * 0.4, h * 0.1, fill=(*rgba(hair)[:3], 255))
        c.rect(w * 0.34, h * 0.1, w * 0.32, h * 0.14, fill=(*rgba(hair)[:3], 255), radius=18)
        c.rect(w * 0.29, h * 0.21, w * 0.42, h * 0.03, fill=(*rgba(hair)[:3], 255), radius=10)
    # Ojos.
    for ex in (0.43, 0.55):
        c.ellipse(w * ex, h * 0.39, w * 0.045, h * 0.035, fill=(255, 255, 255, 255))
        c.ellipse(w * (ex + 0.012), h * 0.397, w * 0.018, h * 0.018, fill=(35, 20, 15, 255))
    # Cejas.
    c.line([(w * 0.42, h * 0.365), (w * 0.465, h * 0.36)], fill=(*rgba(hair)[:3], 255), width=8)
    c.line([(w * 0.535, h * 0.36), (w * 0.58, h * 0.365)], fill=(*rgba(hair)[:3], 255), width=8)
    # Nariz y boca.
    c.line([(w * 0.5, h * 0.41), (w * 0.49, h * 0.47), (w * 0.515, h * 0.475)], fill=(180, 110, 80, 180), width=5)
    c.arc([w * 0.45, h * 0.47, w * 0.55, h * 0.54], 15, 165, fill=(110, 40, 40, 220), width=6)


def host_ultima():
    c = host_base("#ff7a18", "#1a0704", "#4a1206", 1)
    host_shirt(c, "#1f2937")
    # Saco naranja.
    c.polygon([(c.w * 0.3, c.h * 0.74), (c.w * 0.41, c.h * 0.66), (c.w * 0.5, c.h * 0.78),
               (c.w * 0.59, c.h * 0.66), (c.w * 0.7, c.h * 0.74), (c.w * 0.78, c.h * 0.95),
               (c.w * 0.22, c.h * 0.95)], fill=(*rgba("#c2410c")[:3], 240))
    host_head(c, skin="#e8b48d", hair="#3b1608", style="bob")
    # Micrófono.
    c.line([(c.w * 0.72, c.h * 0.7), (c.w * 0.78, c.h * 0.52)], fill=(40, 40, 40, 255), width=12)
    c.ellipse(c.w * 0.76, c.h * 0.46, c.w * 0.09, c.h * 0.09, fill=(50, 50, 50, 255),
              outline=(255, 200, 120, 200), width=4)
    # Aretes.
    for ex in (0.35, 0.645):
        c.ellipse(c.w * ex, c.h * 0.5, c.w * 0.02, c.h * 0.02, fill=(*rgba("#fbbf24")[:3], 255))
    c.vignette(130)
    c.grain(8, 4)
    c.save(OUT / "fiesta_host_ultima_palabra.webp")


def host_vox():
    c = host_base("#22d3ee", "#021018", "#073b4c", 2)
    host_shirt(c, "#0f4c5c")
    c.polygon([(c.w * 0.28, c.h * 0.78), (c.w * 0.42, c.h * 0.66), (c.w * 0.5, c.h * 0.72),
               (c.w * 0.58, c.h * 0.66), (c.w * 0.72, c.h * 0.78), (c.w * 0.78, c.h * 0.95),
               (c.w * 0.22, c.h * 0.95)], fill=(*rgba("#083344")[:3], 245))
    host_head(c, skin="#c98b5e", hair="#1b0d06", style="slick")
    # Bigote y audífonos.
    c.arc([c.w * 0.43, c.h * 0.455, c.w * 0.57, c.h * 0.5], 200, 340, fill=(30, 15, 8, 230), width=8)
    c.arc([c.w * 0.33, c.h * 0.22, c.w * 0.67, c.h * 0.55], 180, 360, fill=(20, 20, 22, 255), width=12)
    c.rect(c.w * 0.315, c.h * 0.36, c.w * 0.05, c.h * 0.12, fill=(20, 20, 22, 255), radius=12)
    c.rect(c.w * 0.635, c.h * 0.36, c.w * 0.05, c.h * 0.12, fill=(20, 20, 22, 255), radius=12)
    # Corbata.
    c.polygon([(c.w * 0.48, c.h * 0.72), (c.w * 0.52, c.h * 0.72), (c.w * 0.545, c.h * 0.95),
               (c.w * 0.5, c.h * 0.99), (c.w * 0.455, c.h * 0.95)], fill=(*rgba("#22d3ee")[:3], 220))
    c.vignette(120)
    c.grain(8, 5)
    c.save(OUT / "fiesta_host_vox_populi.webp")


def host_tiempo():
    c = host_base("#a78bfa", "#0d061f", "#2a1458", 3)
    host_shirt(c, "#2e1065")
    c.polygon([(c.w * 0.3, c.h * 0.76), (c.w * 0.42, c.h * 0.66), (c.w * 0.5, c.h * 0.74),
               (c.w * 0.58, c.h * 0.66), (c.w * 0.7, c.h * 0.76), (c.w * 0.76, c.h * 0.95),
               (c.w * 0.24, c.h * 0.95)], fill=(*rgba("#ede9fe")[:3], 235))
    host_head(c, skin="#e0b08a", hair="#e5e7eb", style="white")
    # Lentes redondos.
    for ex in (0.405, 0.545):
        c.ellipse(c.w * ex, c.h * 0.37, c.w * 0.075, c.h * 0.075, outline=(60, 50, 80, 255), width=6)
    c.line([(c.w * 0.48, c.h * 0.405), (c.w * 0.545, c.h * 0.405)], fill=(60, 50, 80, 255), width=5)
    # Moño.
    c.polygon([(c.w * 0.47, c.h * 0.69), (c.w * 0.5, c.h * 0.72), (c.w * 0.47, c.h * 0.75)],
              fill=(*rgba("#7c3aed")[:3], 255))
    c.polygon([(c.w * 0.53, c.h * 0.69), (c.w * 0.5, c.h * 0.72), (c.w * 0.53, c.h * 0.75)],
              fill=(*rgba("#7c3aed")[:3], 255))
    # Reloj de bolsillo.
    c.ellipse(c.w * 0.64, c.h * 0.76, c.w * 0.09, c.h * 0.09, fill=(*rgba("#fbbf24")[:3], 210),
              outline=(80, 50, 10, 200), width=4)
    c.vignette(130)
    c.grain(9, 6)
    c.save(OUT / "fiesta_host_fuera_de_epoca.webp")


def host_maquina():
    c = host_base("#34d399", "#03140f", "#073b2c", 4)
    # Cuerpo robot.
    c.rect(c.w * 0.28, c.h * 0.68, c.w * 0.44, c.h * 0.28, fill=(18, 30, 28, 245),
           outline=(*rgba("#34d399")[:3], 180), width=5, radius=28)
    for i in range(3):
        c.rect(c.w * 0.34, c.h * (0.74 + i * 0.06), c.w * 0.32, c.h * 0.02,
               fill=(*rgba("#34d399")[:3], 90 - i * 20), radius=6)
    # Cabeza.
    c.rect(c.w * 0.34, c.h * 0.25, c.w * 0.32, c.h * 0.32, fill=(12, 24, 22, 255),
           outline=(*rgba("#34d399")[:3], 210), width=6, radius=36)
    c.rect(c.w * 0.38, c.h * 0.33, c.w * 0.24, c.h * 0.12, fill=(2, 10, 8, 255), radius=16)
    # Ojos LED.
    for ex in (0.425, 0.525):
        c.rect(c.w * ex, c.h * 0.365, c.w * 0.045, c.h * 0.035, fill=(*rgba("#a7f3d0")[:3], 255), radius=6)
    # Antena.
    c.line([(c.w * 0.5, c.h * 0.25), (c.w * 0.5, c.h * 0.16)], fill=(*rgba("#34d399")[:3], 230), width=6)
    c.ellipse(c.w * 0.48, c.h * 0.14, c.w * 0.04, c.h * 0.04, fill=(*rgba("#f472b6")[:3], 255))
    # Bocina.
    for i in range(4):
        c.rect(c.w * (0.4 + i * 0.055), c.h * 0.5, c.w * 0.03, c.h * 0.025,
               fill=(*rgba("#34d399")[:3], 140), radius=3)
    c.vignette(120)
    c.grain(10, 7)
    c.save(OUT / "fiesta_host_texto_roto.webp")


def host_critico():
    c = host_base("#f472b6", "#1b0614", "#5a1238", 5)
    host_shirt(c, "#831843")
    c.polygon([(c.w * 0.28, c.h * 0.76), (c.w * 0.42, c.h * 0.66), (c.w * 0.5, c.h * 0.74),
               (c.w * 0.58, c.h * 0.66), (c.w * 0.72, c.h * 0.76), (c.w * 0.78, c.h * 0.95),
               (c.w * 0.22, c.h * 0.95)], fill=(*rgba("#4c0519")[:3], 240))
    host_head(c, skin="#d99a72", hair="#1c0a04", style="pomp")
    # Lentes y bufanda.
    for ex in (0.41, 0.55):
        c.rect(c.w * ex, c.h * 0.37, c.w * 0.075, c.h * 0.055, fill=(20, 10, 8, 40),
               outline=(240, 220, 200, 230), width=4, radius=6)
    c.line([(c.w * 0.485, c.h * 0.39), (c.w * 0.545, c.h * 0.39)], fill=(240, 220, 200, 230), width=4)
    c.polygon([(c.w * 0.38, c.h * 0.7), (c.w * 0.62, c.h * 0.7), (c.w * 0.58, c.h * 0.8),
               (c.w * 0.42, c.h * 0.8)], fill=(*rgba("#f472b6")[:3], 230))
    # Tarjeta de jurado.
    c.rect(c.w * 0.72, c.h * 0.66, c.w * 0.2, c.h * 0.13, fill=(250, 245, 240, 245),
           outline=(60, 20, 40, 200), width=4, radius=8)
    c.rect(c.w * 0.75, c.h * 0.7, c.w * 0.13, c.h * 0.025, fill=(*rgba("#f472b6")[:3], 200), radius=4)
    c.vignette(125)
    c.grain(8, 8)
    c.save(OUT / "fiesta_host_franela_fest.webp")


def host_tasador():
    c = host_base("#fbbf24", "#1a1003", "#4a2c05", 6)
    host_shirt(c, "#3f2a08")
    c.polygon([(c.w * 0.28, c.h * 0.76), (c.w * 0.42, c.h * 0.66), (c.w * 0.5, c.h * 0.74),
               (c.w * 0.58, c.h * 0.66), (c.w * 0.72, c.h * 0.76), (c.w * 0.78, c.h * 0.95),
               (c.w * 0.22, c.h * 0.95)], fill=(*rgba("#292010")[:3], 245))
    host_head(c, skin="#c98a5c", hair="#2a1a0a", style="hat")
    # Barba.
    c.arc([c.w * 0.42, c.h * 0.47, c.w * 0.58, c.h * 0.6], 10, 170, fill=(235, 230, 220, 230), width=14)
    # Monóculo.
    c.ellipse(c.w * 0.535, c.h * 0.365, c.w * 0.09, c.h * 0.09, outline=(*rgba("#fbbf24")[:3], 255), width=5)
    c.line([(c.w * 0.62, c.h * 0.405), (c.w * 0.66, c.h * 0.58)], fill=(*rgba("#fbbf24")[:3], 180), width=3)
    # Martillo pequeño.
    c.line([(c.w * 0.18, c.h * 0.72), (c.w * 0.24, c.h * 0.58)], fill=(90, 55, 12, 240), width=12)
    c.rect(c.w * 0.19, c.h * 0.54, c.w * 0.1, c.h * 0.06, fill=(120, 72, 16, 245),
           outline=(*rgba("#fbbf24")[:3], 200), width=3, radius=8)
    c.vignette(140)
    c.grain(10, 9)
    c.save(OUT / "fiesta_host_el_tasador.webp")


BUILDERS = {
    "ultima_palabra": stage_ultima_palabra,
    "vox_populi": stage_vox_populi,
    "fuera_de_epoca": stage_fuera_de_epoca,
    "texto_roto": stage_texto_roto,
    "franela_fest": stage_franela_fest,
    "el_tasador": stage_el_tasador,
}

PHONES = {
    "ultima_palabra": ("#1a0704", "#4a1206", "#ff7a18", "stage", 101),
    "vox_populi": ("#021018", "#073b4c", "#22d3ee", "news", 102),
    "fuera_de_epoca": ("#0d061f", "#2a1458", "#a78bfa", "time", 103),
    "texto_roto": ("#03140f", "#073b2c", "#34d399", "chat", 104),
    "franela_fest": ("#1b0614", "#5a1238", "#f472b6", "runway", 105),
    "el_tasador": ("#1a1003", "#4a2c05", "#fbbf24", "auction", 106),
}

HOSTS = {
    "ultima_palabra": host_ultima,
    "vox_populi": host_vox,
    "fuera_de_epoca": host_tiempo,
    "texto_roto": host_maquina,
    "franela_fest": host_critico,
    "el_tasador": host_tasador,
}


def main():
    # Candado de calidad: el arte de producción es el de ComfyUI/SDXL. Este
    # generador procedural queda solo como respaldo de emergencia.
    if (ROOT / "docs/assets/games/v3/premium/approved").is_dir() and "--force-procedural" not in sys.argv:
        print("Hay arte premium aprobado. No se sobrescribe con arte procedural.")
        print("Usa tools/install_fiesta_premium.py o pasa --force-procedural para emergencias.")
        return
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", default="")
    parser.add_argument("--force-procedural", action="store_true")
    args = parser.parse_args()
    names = [args.only] if args.only else list(BUILDERS)
    for name in names:
        print(f"[art] {name}: plató")
        BUILDERS[name]()
        top, bottom, accent, motif, seed = PHONES[name]
        print(f"[art] {name}: teléfono")
        phone_bg(name, top, bottom, accent, motif, seed)
        print(f"[art] {name}: presentador")
        HOSTS[name]()
        # La tarjeta del selector reutiliza el plató: una sola identidad visual.
        stage = Image.open(OUT / f"fiesta_stage_{name}.webp").convert("RGB")
        stage.resize((1280, 720), Image.Resampling.LANCZOS).save(
            OUT / f"game_{name}_art.webp", "WEBP", quality=90, method=6
        )
    print(f"[art] listo en {OUT}")


if __name__ == "__main__":
    main()
