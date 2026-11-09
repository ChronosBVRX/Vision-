#!/usr/bin/env python3
"""Genera las portadas de Vision+ Party con ComfyUI local.

Requiere ComfyUI en http://127.0.0.1:8188 y el checkpoint
juggernautXL_v9Rundiffusion.safetensors. Los prompts viven en
party/tools/party_cover_prompts.json.

Uso:
  python3 party/tools/generate_party_covers.py --out /tmp/opencode/covers
"""
from __future__ import annotations
import argparse, json, shutil, time, urllib.request
from pathlib import Path

COMFY = "http://127.0.0.1:8188"
ROOT = Path(__file__).resolve().parents[2]

def queue(prompt: str, negative: str, name: str, seed: int) -> Path:
    workflow = {
        "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "juggernautXL_v9Rundiffusion.safetensors"}},
        "2": {"class_type": "CLIPTextEncode", "inputs": {"text": prompt, "clip": ["1", 1]}},
        "3": {"class_type": "CLIPTextEncode", "inputs": {"text": negative, "clip": ["1", 1]}},
        "4": {"class_type": "EmptyLatentImage", "inputs": {"width": 832, "height": 1216, "batch_size": 1}},
        "5": {"class_type": "KSampler", "inputs": {"seed": seed, "steps": 28, "cfg": 5.5, "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1, "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0]}},
        "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
        "7": {"class_type": "SaveImage", "inputs": {"filename_prefix": f"party-cover-{name}", "images": ["6", 0]}},
    }
    req = urllib.request.Request(f"{COMFY}/prompt", data=json.dumps({"prompt": workflow}).encode(), headers={"Content-Type": "application/json"})
    prompt_id = json.load(urllib.request.urlopen(req, timeout=30))["prompt_id"]
    for _ in range(300):
        time.sleep(2)
        history = json.load(urllib.request.urlopen(f"{COMFY}/history/{prompt_id}", timeout=30))
        if prompt_id in history:
            images = history[prompt_id]["outputs"].get("7", {}).get("images", [])
            if images:
                source = Path("/home/chronos/Escritorio/Comfy/output") / images[0]["subfolder"] / images[0]["filename"]
                return source
    raise RuntimeError(f"Timeout generando {name}")

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=Path("/tmp/opencode/covers"))
    parser.add_argument("--only", default="")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    prompts = json.loads((ROOT / "party/tools/party_cover_prompts.json").read_text())
    negative = "text, letters, words, watermark, logo, signature, UI, frame, border, low quality, blurry, deformed, extra limbs, copyrighted character, real politician"
    for name, prompt in prompts.items():
        if args.only and args.only != name:
            continue
        source = queue(prompt, negative, name, 200 + len(name))
        shutil.copy2(source, args.out / f"{name}.png")
        print(f"listo: {name}")

if __name__ == "__main__":
    main()
