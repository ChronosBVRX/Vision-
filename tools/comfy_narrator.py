#!/usr/bin/env python3
"""Regenera al narrador de Vision+ Play con el ComfyUI local.

Requisitos:
  - ComfyUI escuchando en 127.0.0.1:8188 (o COMFY_URL).
  - Modelo `juggernautXL_v9Rundiffusion.safetensors` en `models/checkpoints`.
  - Pillow y rembg (u2net) para recortar el fondo:
        python3 -m venv ~/.venvs/visionplus-assets
        ~/.venvs/visionplus-assets/bin/pip install rembg onnxruntime pillow

Uso:
    ~/.venvs/visionplus-assets/bin/python tools/comfy_narrator.py

El resultado va a `app/src/main/res/drawable-nodpi/` (4 frames + avatar + estático)
y el maestro transparente a `docs/assets/games/narrator_master.png`.
"""

from __future__ import annotations

import json
import os
import time
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter
from rembg import remove

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("COMFY_URL", "http://127.0.0.1:8188")
SEED = int(os.environ.get("NARRATOR_SEED", "777002"))
ASSETS = ROOT / "app/src/main/res/drawable-nodpi"
DOCS = ROOT / "docs/assets/games"
TMP = Path(os.environ.get("NARRATOR_TMP", "/tmp/opencode/narrator"))
POS = (
    "cute friendly floating robot mascot, Pixar 3D render, front view, symmetrical, "
    "dark navy blue glossy rounded body, a wide blank dark glass panel across the chest, "
    "small rounded head with two big friendly glowing cyan eyes and a happy smile, "
    "a glowing plus sign antenna on top, short stubby arms, small hover thrusters, "
    "cyan and violet neon accents, soft studio lighting, centered, full body, "
    "plain flat green studio background, high detail, family friendly video game mascot, "
    "no text, no letters, no logo, no watermark"
)
NEG = (
    "text, letters, logo, watermark, human, realistic photo, scary, weapon, "
    "extra limbs, deformed, blurry, cropped, busy background, multiple characters, "
    "green spill on the robot, green reflections, chest text, chest letters"
)


def graph(seed: int = SEED) -> dict:
    return {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "juggernautXL_v9Rundiffusion.safetensors"}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": POS, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": NEG, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": 1024, "height": 1024, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {
            "seed": seed, "steps": 30, "cfg": 6.5, "sampler_name": "dpmpp_2m",
            "scheduler": "karras", "denoise": 1.0,
            "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0],
        }},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"filename_prefix": "narrator", "images": ["6", 0]}},
    }


def _post(path: str, payload: dict) -> dict:
    request = urllib.request.Request(
        BASE + path, data=json.dumps(payload).encode(), method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.loads(response.read())


def _get(path: str) -> dict:
    with urllib.request.urlopen(BASE + path, timeout=30) as response:
        return json.loads(response.read())


def generate_base() -> Path:
    TMP.mkdir(parents=True, exist_ok=True)
    prompt_id = _post("/prompt", {"prompt": graph()})["prompt_id"]
    for _ in range(300):
        time.sleep(1)
        history = _get(f"/history/{prompt_id}")
        if prompt_id in history:
            images = history[prompt_id]["outputs"].get("7", {}).get("images", [])
            if not images:
                raise RuntimeError("ComfyUI no devolvió imagen")
            image = images[0]
            query = urllib.parse.urlencode({
                "filename": image["filename"],
                "subfolder": image.get("subfolder", ""),
                "type": image.get("type", "output"),
            })
            with urllib.request.urlopen(f"{BASE}/view?{query}", timeout=60) as response:
                target = TMP / f"base_{SEED}.png"
                target.write_bytes(response.read())
                return target
    raise TimeoutError("ComfyUI no terminó la imagen")


def build_frames(cut: Image.Image, logo: Image.Image) -> None:
    ASSETS.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    cut.save(DOCS / "narrator_master.png", optimize=True)

    def frame(shift_y: int, scale: float, name: str) -> None:
        height = 470
        width = int(cut.width * height / cut.height)
        char = cut.resize((width, height), Image.LANCZOS)
        if scale != 1.0:
            width, height = int(width * scale), int(height * scale)
            char = char.resize((width, height), Image.LANCZOS)
        canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        x = (512 - width) // 2
        y = 512 - height - 18 + shift_y
        canvas.alpha_composite(char, (x, y))
        cx = round(x + 0.501 * width)
        cy = round(y + 0.503 * height)
        radius = 34
        glow = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse(
            (cx - radius - 9, cy - radius - 9, cx + radius + 9, cy + radius + 9),
            fill=(45, 125, 246, 150),
        )
        canvas.alpha_composite(glow.filter(ImageFilter.GaussianBlur(10)))
        draw = ImageDraw.Draw(canvas)
        draw.ellipse(
            (cx - radius, cy - radius, cx + radius, cy + radius),
            fill=(8, 14, 26, 255), outline=(107, 166, 255, 235), width=2,
        )
        logo_width = 56
        logo_height = int(logo.height * logo_width / logo.width)
        canvas.alpha_composite(
            logo.resize((logo_width, logo_height), Image.LANCZOS),
            (cx - logo_width // 2, cy - logo_height // 2),
        )
        canvas.save(ASSETS / name, optimize=True)

    frame(0, 1.0, "narrator_idle_0.png")
    frame(-3, 1.004, "narrator_idle_1.png")
    frame(-5, 1.008, "narrator_idle_2.png")
    frame(-3, 1.004, "narrator_idle_3.png")
    frame(0, 1.0, "narrator_static.png")
    base = Image.open(ASSETS / "narrator_idle_0.png").convert("RGBA")
    base.crop((112, 10, 400, 300)).resize((256, 256), Image.LANCZOS).save(
        ASSETS / "narrator_avatar.png", optimize=True
    )


def main() -> int:
    logo = Image.open(ASSETS / "ic_vision_mark.png").convert("RGBA")
    base = Image.open(generate_base()).convert("RGBA")
    cut = remove(base)
    bbox = cut.getbbox()
    if not bbox:
        raise RuntimeError("el recorte quedó vacío")
    cut = cut.crop(bbox)
    build_frames(cut, logo)
    print("narrador regenerado:", ", ".join(sorted(p.name for p in ASSETS.glob("narrator_*.png"))))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
