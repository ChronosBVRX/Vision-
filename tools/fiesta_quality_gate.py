#!/usr/bin/env python3
"""Filtro de calidad de los assets de Vision+ Fiesta.

No intenta "adivinar" si un arte es bonito: mide señales objetivas que separan
un key art de producción de un degradado procedural:

  * resolución mínima por tipo de asset;
  * peso mínimo (detalle real comprimido, no un fondo plano);
  * entropía de luminancia (riqueza de tonos);
  * densidad de bordes (estructura/utilería, no solo humo);
  * colorfulness (paleta viva, no gris genérico);
  * rango tonal (no todo negro ni todo quemado).

Audio:
  * duración esperada por tipo;
  * no clipping (max_volume <= -0.5 dB);
  * nivel medio razonable;
  * música/stingers con duración de bucle/sting.

Uso:
  python3 tools/fiesta_quality_gate.py
  python3 tools/fiesta_quality_gate.py --premium
  python3 tools/fiesta_quality_gate.py --json
"""

from __future__ import annotations

import argparse
import json
import math
import os
import subprocess
import sys
from dataclasses import dataclass, asdict
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter, ImageStat

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
DRAWABLE = RES / "drawable-nodpi"
RAW = RES / "raw"
PREMIUM = ROOT / "docs/assets/games/v3/premium"
REPORT = ROOT / "docs/assets/games/v3/QUALITY_REPORT.md"

GAMES = ("ultima_palabra", "vox_populi", "fuera_de_epoca",
         "texto_roto", "franela_fest", "el_tasador")

# (ancho, alto, bytes mínimos, entropía mínima, borde mínimo, color mínimo)
IMAGE_RULES = {
    "stage": (1920, 1080, 120_000, 6.3, 3.2, 28.0),
    "phone": (1080, 1920, 70_000, 6.0, 2.6, 24.0),
    "host": (900, 900, 45_000, 5.8, 2.2, 22.0),
    "card": (1280, 720, 35_000, 5.6, 2.2, 22.0),
    "poster": (900, 1200, 80_000, 5.8, 2.2, 22.0),
    "lobby": (1920, 1080, 150_000, 6.2, 2.8, 24.0),
}

VOICE_MOMENTS = {
    "ultima_palabra": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "vox_populi": ("lobby", "start", "guess", "reveal", "podium"),
    "fuera_de_epoca": ("lobby", "start", "guess", "reveal", "podium"),
    "texto_roto": ("lobby", "start", "write", "vote", "reveal", "podium"),
    "franela_fest": ("lobby", "start", "create", "vote", "reveal", "podium"),
    "el_tasador": ("lobby", "start", "pitch", "vote", "reveal", "podium"),
}


@dataclass
class Result:
    path: str
    kind: str
    ok: bool
    detail: str
    metrics: dict


def image_metrics(path: Path) -> dict:
    image = Image.open(path).convert("RGB")
    gray = image.convert("L")
    edges = gray.filter(ImageFilter.FIND_EDGES)
    edge_stat = ImageStat.Stat(edges)
    lum_stat = ImageStat.Stat(gray)
    histogram = gray.histogram()
    total = sum(histogram) or 1
    entropy = -sum((v / total) * math.log2(v / total) for v in histogram if v)
    red, green, blue = image.split()
    rg = ImageChops.difference(red, green)
    yb = ImageChops.difference(Image.blend(red, green, 0.5), blue)
    rs, ys = ImageStat.Stat(rg), ImageStat.Stat(yb)
    colorfulness = (
        math.sqrt(rs.stddev[0] ** 2 + ys.stddev[0] ** 2)
        + 0.3 * math.sqrt(rs.mean[0] ** 2 + ys.mean[0] ** 2)
    )
    return {
        "width": image.width,
        "height": image.height,
        "bytes": path.stat().st_size,
        "entropy": round(entropy, 2),
        "edge_mean": round(edge_stat.mean[0], 2),
        "edge_std": round(edge_stat.stddev[0], 2),
        "lum_mean": round(lum_stat.mean[0], 2),
        "lum_std": round(lum_stat.stddev[0], 2),
        "colorfulness": round(colorfulness, 2),
    }


def check_image(path: Path, kind: str) -> Result:
    if not path.is_file():
        return Result(str(path), kind, False, "no existe", {})
    try:
        m = image_metrics(path)
    except OSError as exc:
        return Result(str(path), kind, False, f"imagen ilegible/truncada: {exc}", {})
    min_w, min_h, min_bytes, min_entropy, min_edge, min_color = IMAGE_RULES[kind]
    failures = []
    if m["width"] < min_w or m["height"] < min_h:
        failures.append(f"resolución {m['width']}x{m['height']} < {min_w}x{min_h}")
    if m["bytes"] < min_bytes:
        failures.append(f"peso {m['bytes']} B < {min_bytes} B")
    if m["entropy"] < min_entropy:
        failures.append(f"entropía {m['entropy']} < {min_entropy}")
    if m["edge_mean"] < min_edge:
        failures.append(f"bordes {m['edge_mean']} < {min_edge}")
    if m["colorfulness"] < min_color:
        failures.append(f"color {m['colorfulness']} < {min_color}")
    if not 18 <= m["lum_mean"] <= 190:
        failures.append(f"luminancia media {m['lum_mean']} fuera de 18..190")
    detail = "ok" if not failures else "; ".join(failures)
    return Result(str(path.relative_to(ROOT)), kind, not failures, detail, m)


def probe(path: Path) -> dict:
    proc = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration",
         "-of", "default=nw=1:nk=1", str(path)],
        capture_output=True, text=True,
    )
    try:
        duration = float(proc.stdout.strip())
    except ValueError:
        duration = 0.0
    vol = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-i", str(path),
         "-af", "volumedetect", "-f", "null", "-"],
        capture_output=True, text=True,
    )
    mean = maxv = None
    for line in vol.stderr.splitlines():
        if "mean_volume:" in line:
            try:
                mean = float(line.split("mean_volume:")[1].split("dB")[0])
            except ValueError:
                pass
        if "max_volume:" in line:
            try:
                maxv = float(line.split("max_volume:")[1].split("dB")[0])
            except ValueError:
                pass
    return {"duration": round(duration, 2), "mean_db": mean, "max_db": maxv}


def check_audio(path: Path, kind: str) -> Result:
    if not path.is_file():
        return Result(str(path), kind, False, "no existe", {})
    m = probe(path)
    failures = []
    if kind == "voice":
        if not 1.0 <= m["duration"] <= 12.0:
            failures.append(f"duración {m['duration']}s fuera de 1..12")
        if m["mean_db"] is None or not -34 <= m["mean_db"] <= -10:
            failures.append(f"nivel medio {m['mean_db']} dB fuera de -34..-10")
        if m["max_db"] is None or m["max_db"] > -0.5:
            failures.append(f"pico {m['max_db']} dB (clipping)")
    elif kind == "music":
        if not 20 <= m["duration"] <= 42:
            failures.append(f"música de {m['duration']}s fuera de 20..42")
        if m["max_db"] is None or m["max_db"] > -0.2:
            failures.append(f"pico {m['max_db']} dB")
    else:  # sting
        if not 1.5 <= m["duration"] <= 6.0:
            failures.append(f"sting de {m['duration']}s fuera de 1.5..6")
        if m["max_db"] is None or m["max_db"] > -0.2:
            failures.append(f"pico {m['max_db']} dB")
    detail = "ok" if not failures else "; ".join(failures)
    return Result(str(path.relative_to(ROOT)), kind, not failures, detail, m)


def premium_results() -> list[Result]:
    results = []
    folder = PREMIUM / "approved" if (PREMIUM / "approved").is_dir() else PREMIUM
    if not folder.is_dir():
        return results
    for path in sorted(folder.glob("*.png")):
        name = path.name
        if name == "contact_sheet.png":
            continue
        if "_stage_" in name or name.endswith("_stage.png"):
            kind = "stage"
        elif "_host_" in name or name.endswith("_host.png"):
            kind = "host"
        elif "_phone_" in name or name.endswith("_phone.png"):
            kind = "phone"
        elif "_selector_" in name or name.endswith("_selector.png"):
            kind = "poster"
        elif name.startswith("lobby"):
            kind = "lobby"
        else:
            continue
        results.append(check_image(path, kind))
    return results


def installed_results() -> list[Result]:
    results = []
    for game in GAMES:
        results.append(check_image(DRAWABLE / f"fiesta_stage_{game}.webp", "stage"))
        results.append(check_image(DRAWABLE / f"fiesta_phone_{game}.webp", "phone"))
        results.append(check_image(DRAWABLE / f"fiesta_host_{game}.webp", "host"))
        results.append(check_image(DRAWABLE / f"game_{game}_art.webp", "card"))
        results.append(check_image(DRAWABLE / f"game_{game}_poster.webp", "poster"))
        for moment in VOICE_MOMENTS[game]:
            for variant in range(5):
                results.append(check_audio(
                    RAW / f"fiesta_{game}_{moment}_{variant}.m4a", "voice",
                ))
        results.append(check_audio(RAW / f"fiesta_music_{game}.m4a", "music"))
        results.append(check_audio(RAW / f"fiesta_sting_{game}.m4a", "sting"))
    results.append(check_image(DRAWABLE / "fiesta_lobby_bg.webp", "lobby"))
    return results


def write_report(results: list[Result], title: str) -> None:
    ok = sum(1 for item in results if item.ok)
    lines = [
        f"# {title}",
        "",
        f"**Resultado:** {ok}/{len(results)} assets pasan el filtro.",
        "",
        "| Asset | Tipo | Resultado | Detalle |",
        "|---|---|---|---|",
    ]
    for item in results:
        status = "PASA" if item.ok else "FALLA"
        detail = item.detail.replace("|", "\\|")
        lines.append(f"| `{item.path}` | {item.kind} | {status} | {detail} |")
    lines += [
        "",
        "## Criterios",
        "",
        "Los umbrales están en `tools/fiesta_quality_gate.py`. No sustituyen la",
        "revisión humana: son un candado contra fondos planos, arte de relleno y",
        "audio con clipping. El arte de producción debe pasar además la revisión",
        "visual de composición, legibilidad y coherencia de personaje.",
        "",
    ]
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--premium", action="store_true", help="evalúa los PNG maestros de ComfyUI")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    results = premium_results() if args.premium else installed_results()
    if not results:
        print("No hay assets que evaluar.", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps([asdict(item) for item in results], ensure_ascii=False, indent=2))
    else:
        failed = 0
        for item in results:
            if not item.ok:
                failed += 1
                print(f"FALLA  {item.kind:8} {item.path}  →  {item.detail}")
        print(f"\n{len(results) - failed}/{len(results)} assets pasan el filtro.")
        write_report(results, "Fiesta — calidad de assets premium" if args.premium else "Fiesta — calidad de assets instalados")
        print(f"Reporte: {REPORT}")
        if failed:
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
