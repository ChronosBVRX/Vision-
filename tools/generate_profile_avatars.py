#!/usr/bin/env python3
"""Genera los avatares de personaje de Perfiles con ComfyUI + FLUX.2 Klein local.

Uso:
  .venv/bin/python tools/generate_profile_avatars.py
  .venv/bin/python tools/generate_profile_avatars.py --only cine_noir series_detective
  .venv/bin/python tools/generate_profile_avatars.py --force

Salida:
  - maestros PNG en /media/chronos/Disco Duro/vision-assets/profile_avatars
  - WebP 256x256 en app/src/main/res/drawable-nodpi/profile_avatar_<id>.webp
  - hoja de contacto en /tmp/opencode/profile_avatars_sheet.jpg

Los personajes son originales (arquetipos de cine/series/anime), no recortes de
pósters ni personajes con copyright. Cada prompt está pensado para una tele: cara
grande, centrada, fondo oscuro y sin texto.
"""

from __future__ import annotations

import argparse
import io
import os
import time
import urllib.parse
from pathlib import Path

import requests
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
BASE = os.environ.get("COMFY_URL", "http://127.0.0.1:8188")
OUT = Path("/media/chronos/Disco Duro/vision-assets/profile_avatars")
RES = ROOT / "app/src/main/res/drawable-nodpi"
SHEET = Path("/tmp/opencode/profile_avatars_sheet.jpg")

UNET = "flux-2-klein-4b-fp8.safetensors"
CLIP = "qwen_3_4b.safetensors"
VAE = "flux2-vae.safetensors"

STYLE = {
    ("cine", "adulto"): (
        "cinematic film still character portrait, photorealistic, detailed skin texture, "
        "dramatic practical lighting, shallow depth of field, premium streaming key art"
    ),
    ("cine", "familia"): (
        "premium 3D animated feature film character portrait, rounded stylized shapes, "
        "expressive eyes, soft cinematic studio lighting, Pixar-inspired original character"
    ),
    ("cine", "infantil"): (
        "premium 3D animated family movie character portrait, cute rounded shapes, big expressive eyes, "
        "bright cheerful colors, soft studio lighting, original character"
    ),
    ("series", "adulto"): (
        "premium television drama character portrait, cinematic semi-realistic, "
        "detailed wardrobe, dramatic soft key light, dark background"
    ),
    ("series", "familia"): (
        "stylized 3D animated television character portrait, friendly rounded shapes, "
        "expressive eyes, colorful family-friendly design"
    ),
    ("series", "infantil"): (
        "cute stylized 3D animated kids TV character portrait, toy-like rounded shapes, "
        "big friendly eyes, bright colors, playful family-friendly design"
    ),
    ("anime", "adulto"): (
        "2D anime key visual character portrait, cel shaded, clean line art, detailed hair, "
        "dramatic rim light, cinematic composition, studio quality"
    ),
    ("anime", "familia"): (
        "2D anime character portrait, cel shaded, clean line art, vibrant colors, "
        "expressive eyes, family-friendly adventure tone"
    ),
    ("anime", "infantil"): (
        "cute family-friendly anime character portrait, cel shaded, bright colors, "
        "big expressive eyes, playful and wholesome"
    ),
}

# id, etiqueta, categoría, público, descripción del personaje
SPECS = [
    # ---------------- CINE (20) ----------------
    ("cine_noir", "Detective noir", "cine", "adulto",
     "a hardboiled 1940s detective, 45-year-old Latino man with stubble, trench coat and fedora, rain and neon city bokeh"),
    ("cine_agente", "Agente secreto", "cine", "adulto",
     "a suave secret agent, 35-year-old Black woman in a tailored tuxedo, night casino lights behind"),
    ("cine_dama", "Dama misteriosa", "cine", "adulto",
     "an elegant femme fatale, 30-year-old East Asian woman in a red evening dress, plain dark velvet studio background with soft red rim light"),
    ("cine_pistolero", "Pistolero", "cine", "adulto",
     "a weathered gunslinger, 50-year-old white man with a gray beard, duster coat and cowboy hat, desert sunset"),
    ("cine_gladiador", "Gladiador", "cine", "adulto",
     "an ancient gladiator, 35-year-old Mediterranean man, bronze armor and leather straps, dusty arena"),
    ("cine_mafia", "Jefe mafioso", "cine", "adulto",
     "a mafia boss, 60-year-old Italian man in a pinstripe suit, cigar smoke and warm tungsten light"),
    ("cine_cientifica", "Científica loca", "cine", "adulto",
     "an eccentric scientist, 40-year-old mixed-race woman with curly hair and safety goggles, white lab coat, neon laboratory"),
    ("cine_vampiro", "Vampiro elegante", "cine", "adulto",
     "an elegant ancient vampire, pale aristocratic man in a velvet cape, candlelit gothic hall"),
    ("cine_pirata", "Capitán pirata", "cine", "familia",
     "a rugged pirate captain, 40-year-old man with a tricorn hat and weathered coat, stormy sea and ship deck"),
    ("cine_astronauta", "Astronauta", "cine", "familia",
     "a brave astronaut, 35-year-old woman in a white space suit, alien planet horizon and stars"),
    ("cine_heroe", "Superhéroe escarlata", "cine", "familia",
     "an original masked superhero, 25-year-old man in a dark teal and silver armored suit with a plain glowing chest line, no emblem, no logo, city skyline at dusk"),
    ("cine_mago", "Mago anciano", "cine", "familia",
     "a wise old wizard, 70-year-old man with a long white beard, deep blue robes and a glowing wooden staff, ancient library"),
    ("cine_princesa", "Princesa guerrera", "cine", "familia",
     "a warrior princess, 25-year-old Black woman with natural hair, gold armor and a royal cape, castle hall"),
    ("cine_robot", "Robot amigo", "cine", "infantil",
     "a friendly rounded robot, pastel cream and teal metal, big warm eyes, small antenna, soft workshop background"),
    ("cine_dragon", "Dragón bebé", "cine", "infantil",
     "a cute baby dragon, turquoise scales, tiny wings and big amber eyes, magical cave with glowing crystals"),
    ("cine_pirata_nino", "Pirata niño", "cine", "infantil",
     "a cheerful young pirate boy, 10-year-old animated character, red bandana and tiny sword, treasure map, sunny ship deck"),
    ("cine_exploradora", "Exploradora", "cine", "infantil",
     "a curious young jungle explorer girl, 12-year-old animated character, safari hat and binoculars, lush green temple"),
    ("cine_hada", "Hada del bosque", "cine", "infantil",
     "a tiny forest fairy with glowing translucent wings, flower petal dress, enchanted garden at twilight"),
    ("cine_zorro", "Zorro aventurero", "cine", "infantil",
     "an anthropomorphic adventurous fox wearing a little scarf and satchel, storybook forest, warm friendly expression"),
    ("cine_ranger", "Ranger espacial", "cine", "infantil",
     "a toy-like retro space ranger, 10-year-old animated character, white and orange suit, tiny rocket backpack, colorful alien moon"),

    # ---------------- SERIES (20) ----------------
    ("series_detective", "Detective de serie", "series", "adulto",
     "a TV detective, 40-year-old woman with short dark hair, beige trench coat, crime board and rainy window"),
    ("series_doctora", "Doctora de urgencias", "series", "adulto",
     "an emergency room doctor, 35-year-old Black woman in blue scrubs, stethoscope, hospital corridor"),
    ("series_abogado", "Abogado brillante", "series", "adulto",
     "a brilliant defense lawyer, 45-year-old man in a charcoal suit, courtroom and law books behind"),
    ("series_hacker", "Hacker nocturno", "series", "adulto",
     "a hacker, 25-year-old East Asian woman in a dark hoodie, glasses, neon computer monitors and code reflections"),
    ("series_reina", "Reina de fantasía", "series", "adulto",
     "a fantasy queen, 30-year-old woman with braided hair and a silver crown, dark throne room and candlelight"),
    ("series_superviviente", "Superviviente", "series", "adulto",
     "a post-apocalyptic survivor, 35-year-old man in a worn leather jacket, ruins and dust behind"),
    ("series_agente", "Agente federal", "series", "adulto",
     "a federal agent, 40-year-old Latina woman in a dark suit and earpiece, secure government office"),
    ("series_chef", "Chef estrella", "series", "adulto",
     "a fiery celebrity chef, 40-year-old Latina woman in a black apron, flames and copper pans in a busy kitchen"),
    ("series_periodista", "Periodista", "series", "adulto",
     "an investigative journalist, 30-year-old man with a press badge and notebook, rainy city newsroom"),
    ("series_vampiro", "Vampiro moderno", "series", "adulto",
     "a stylish modern vampire, 30-year-old woman in a black coat, dark nightclub with blue and red neon"),
    ("series_bruja", "Bruja adolescente", "series", "familia",
     "a teenage witch, 16-year-old animated character with wavy auburn hair, a pointed purple witch hat, school uniform and glowing wand, magical sparkles"),
    ("series_estudiante", "Estudiante curioso", "series", "infantil",
     "a curious schoolboy, 9-year-old animated character with a backpack and big glasses, colorful classroom"),
    ("series_mascota", "Mascota divertida", "series", "infantil",
     "a cheerful fluffy cartoon mascot creature, round lavender and mint fur, tiny horns and a big smile, confetti background"),
    ("series_alien", "Alien amistoso", "series", "infantil",
     "a small friendly blue alien with three eyes, two antennae and a round body, pastel spaceship interior"),
    ("series_heroe_joven", "Héroe adolescente", "series", "familia",
     "a teen superhero, 16-year-old animated character in a teal and white suit with a simple mask, city rooftop at sunset"),
    ("series_perro", "Perro detective", "series", "infantil",
     "a clever beagle detective wearing a tiny deerstalker hat and a scarf, magnifying glass, cozy library"),
    ("series_robot_casa", "Robot de casa", "series", "infantil",
     "a helpful pastel household robot with round eyes and a little apron, warm modern living room"),
    ("series_ninera", "Niñera mágica", "series", "familia",
     "a kind magical nanny, 35-year-old woman with an elegant blue coat, carpet bag and umbrella, floating above a London rooftop"),
    ("series_grumete", "Grumete valiente", "series", "infantil",
     "a brave young cabin boy, 10-year-old animated character with a striped shirt and rope, ship deck at sunrise"),
    ("series_aventurera", "Aventurera juvenil", "series", "infantil",
     "a young temple explorer girl, 12-year-old animated character with a ponytail and lantern, ancient jungle ruins"),

    # ---------------- ANIME (20) ----------------
    ("anime_samurai", "Samurái errante", "anime", "adulto",
     "a wandering samurai, 35-year-old Japanese man with a topknot and worn haori, katana, cherry blossoms and mist"),
    ("anime_ninja", "Ninja de la luna", "anime", "familia",
     "a moonlit ninja, 18-year-old woman in a dark blue ninja suit and mask, full moon and tiled rooftops"),
    ("anime_mecha", "Piloto de mecha", "anime", "familia",
     "a mecha pilot, 20-year-old man in a sleek white and blue flight suit, futuristic cockpit and holographic screens"),
    ("anime_cazadora", "Cazadora de demonios", "anime", "familia",
     "a demon hunter, 19-year-old woman in a black uniform with a katana, glowing embers and night forest"),
    ("anime_espadachin", "Espadachín marcado", "anime", "adulto",
     "a scarred wandering swordsman, 30-year-old man with a straw hat and worn cloak, rain and bamboo forest"),
    ("anime_hechicero", "Hechicero moderno", "anime", "familia",
     "a modern sorcerer, 18-year-old man in a dark high-collar uniform, blue cursed energy swirling around his hands"),
    ("anime_capitan", "Capitán del cielo", "anime", "familia",
     "a sky pirate captain, 20-year-old man with goggles and a long coat, airship deck above the clouds"),
    ("anime_magica", "Chica mágica", "anime", "infantil",
     "a magical girl, 14-year-old anime character with silver twin tails, pink ribbons and a glowing star wand, cherry blossoms"),
    ("anime_gato", "Gato robot", "anime", "infantil",
     "a round metallic robot cat, silver and blue metal with visible joints and an antenna, glowing green eyes and a little jet pack, pastel future city"),
    ("anime_entrenador", "Entrenador de monstruos", "anime", "infantil",
     "a young monster trainer, 12-year-old anime boy in a teal hoodie and goggles, a tiny round crystalline creature friend on his shoulder"),
    ("anime_ninja_novato", "Ninja novato", "anime", "infantil",
     "a novice young ninja, 12-year-old anime boy in a purple outfit and goggles, training courtyard"),
    ("anime_nino_dragon", "Niño dragón", "anime", "infantil",
     "a young dragon-child, 10-year-old anime character with small horns and a tiny dragon companion, fantasy village"),
    ("anime_shonen", "Héroe shonen", "anime", "familia",
     "an energetic shonen hero, 17-year-old anime boy with spiky red hair and bandaged fists, dramatic speed lines"),
    ("anime_estudiante", "Estudiante de instituto", "anime", "familia",
     "a high school student, 16-year-old anime girl in a navy sailor uniform, cherry blossom school gate"),
    ("anime_idol", "Idol del escenario", "anime", "familia",
     "a pop idol, 18-year-old anime girl with pastel hair and a sparkling stage outfit, concert lights and confetti"),
    ("anime_detective", "Detective escolar", "anime", "familia",
     "a school detective, 16-year-old anime boy with a magnifying glass and a brown trench coat, library"),
    ("anime_alquimista", "Alquimista", "anime", "familia",
     "an alchemist, 20-year-old anime woman with white gloves and a glowing transmutation circle, ancient study"),
    ("anime_cadete", "Cadete espacial", "anime", "infantil",
     "a young space cadet, 12-year-old anime boy in a retro white and orange space suit, starship bridge"),
    ("anime_hada", "Hada anime", "anime", "infantil",
     "a small anime fairy, 12-year-old character with glowing pastel wings and a flower crown, enchanted forest"),
    ("anime_sumo", "Luchador sumo", "anime", "familia",
     "a friendly comedic sumo wrestler, 30-year-old anime man with a topknot and colorful mawashi, cherry blossom arena"),
]

SUFFIX = (
    " Character portrait avatar, head and shoulders, face centered, looking at the viewer, "
    "square composition, elegant dark cinematic background with depth, highly detailed, "
    "no text, no letters, no words, no logo, no watermark, no signature, no frame, no border, "
    "no multiple people, no cropped head, no deformed face."
)


def post(path: str, payload: dict) -> dict:
    r = requests.post(BASE + path, json=payload, timeout=120)
    r.raise_for_status()
    return r.json()


def graph(seed: int, prompt: str, width: int = 1024, height: int = 1024) -> dict:
    return {
        "1": {"class_type": "UNETLoader", "inputs": {"unet_name": UNET, "weight_dtype": "default"}},
        "2": {"class_type": "CLIPLoader", "inputs": {"clip_name": CLIP, "type": "flux2", "device": "default"}},
        "3": {"class_type": "VAELoader", "inputs": {"vae_name": VAE}},
        "4": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["2", 0]}},
        "5": {"class_type": "CLIPTextEncode", "inputs": {"text": "", "clip": ["2", 0]}},
        "6": {"class_type": "EmptyFlux2LatentImage", "inputs": {"width": width, "height": height, "batch_size": 1}},
        "7": {"class_type": "Flux2Scheduler", "inputs": {"steps": 4, "width": width, "height": height}},
        "8": {"class_type": "KSamplerSelect", "inputs": {"sampler_name": "euler"}},
        "9": {"class_type": "CFGGuider", "inputs": {
            "model": ["1", 0], "positive": ["4", 0], "negative": ["5", 0], "cfg": 1.0,
        }},
        "10": {"class_type": "RandomNoise", "inputs": {"noise_seed": seed}},
        "11": {"class_type": "SamplerCustomAdvanced", "inputs": {
            "noise": ["10", 0], "guider": ["9", 0], "sampler": ["8", 0],
            "sigmas": ["7", 0], "latent_image": ["6", 0],
        }},
        "12": {"class_type": "VAEDecode", "inputs": {"samples": ["11", 0], "vae": ["3", 0]}},
        "13": {"class_type": "SaveImage", "inputs": {"filename_prefix": "profile_avatar", "images": ["12", 0]}},
    }


def generate(seed: int, prompt: str) -> Path:
    pid = post("/prompt", {"prompt": graph(seed, prompt)})["prompt_id"]
    for _ in range(300):
        time.sleep(1)
        h = requests.get(BASE + f"/history/{pid}", timeout=60).json()
        if pid not in h:
            continue
        imgs = h[pid]["outputs"].get("13", {}).get("images", [])
        if not imgs:
            raise RuntimeError("ComfyUI no devolvió imagen")
        img = imgs[0]
        q = urllib.parse.urlencode({
            "filename": img["filename"],
            "subfolder": img.get("subfolder", ""),
            "type": img.get("type", "output"),
        })
        data = requests.get(BASE + "/view?" + q, timeout=120).content
        target = Path("/tmp/opencode") / f"profile_avatar_{seed}.png"
        target.write_bytes(data)
        return target
    raise TimeoutError("ComfyUI tardó demasiado")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", nargs="*", default=[])
    ap.add_argument("--force", action="store_true")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    RES.mkdir(parents=True, exist_ok=True)
    wanted = set(args.only)
    todo = [s for s in SPECS if not wanted or s[0] in wanted or s[0].replace("_", " ") in wanted]
    if not todo:
        print("No hay avatares que generar.")
        return 1

    made = []
    for i, (id_, label, cat, aud, desc) in enumerate(todo, 1):
        webp = RES / f"profile_avatar_{id_}.webp"
        master = OUT / f"{id_}.png"
        if webp.exists() and not args.force and not wanted:
            made.append((id_, label, cat, aud, Image.open(webp).convert("RGB")))
            print(f"[{i}/{len(todo)}] {id_} (ya existe)", flush=True)
            continue
        prompt = f"{STYLE[(cat, aud)]}, {desc}.{SUFFIX}"
        seed = 880000 + SPECS.index((id_, label, cat, aud, desc)) * 97 + 17
        print(f"[{i}/{len(todo)}] {id_} · {label} · {cat}/{aud}", flush=True)
        t = time.time()
        try:
            raw = generate(seed, prompt)
        except Exception as exc:
            print(f"  ERROR: {exc}", flush=True)
            continue
        img = Image.open(raw).convert("RGB")
        img.save(master, "PNG", optimize=True)
        avatar = img.resize((256, 256), Image.Resampling.LANCZOS)
        avatar.save(webp, "WEBP", quality=86, method=6)
        made.append((id_, label, cat, aud, avatar))
        print(f"  ok {time.time() - t:.1f}s -> {webp.name}", flush=True)

    if made:
        cols = 10
        rows = (len(made) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * 140, rows * 168), (18, 22, 30))
        draw = ImageDraw.Draw(sheet)
        for i, (id_, label, cat, aud, img) in enumerate(made):
            x = (i % cols) * 140 + 6
            y = (i // cols) * 168 + 6
            sheet.paste(img.resize((128, 128), Image.Resampling.LANCZOS), (x, y))
            draw.text((x, y + 132), f"{label[:20]}", fill=(235, 235, 235))
        sheet.save(SHEET, quality=92)
        print(f"hoja de contacto: {SHEET}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
