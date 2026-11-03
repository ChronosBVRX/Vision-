#!/usr/bin/env python3
"""Instala los maestros aprobados de Fiesta en el APK.

Lee `docs/assets/games/v3/premium/approved/<juego>_<tipo>.png` y escribe:

  app/src/main/res/drawable-nodpi/fiesta_stage_<juego>.webp   (1920x1080)
  app/src/main/res/drawable-nodpi/fiesta_phone_<juego>.webp   (1080x1920)
  app/src/main/res/drawable-nodpi/fiesta_host_<juego>.webp    (900x900)
  app/src/main/res/drawable-nodpi/game_<juego>_art.webp       (1280x720)

No inventa nada: si falta un maestro aprobado, se salta y lo dice.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
APPROVED = ROOT / "docs/assets/games/v3/premium/approved"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
GAMES = ("ultima_palabra", "vox_populi", "fuera_de_epoca",
         "texto_roto", "franela_fest", "el_tasador")

TARGETS = {
    "stage": ((1920, 1080), "fiesta_stage_{game}.webp", 93),
    "phone": ((1080, 1920), "fiesta_phone_{game}.webp", 91),
    "host": ((900, 900), "fiesta_host_{game}.webp", 92),
    "selector": ((900, 1200), "game_{game}_poster.webp", 92),
}


def fit(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    """Recorte central + resize de alta calidad (nunca deforma)."""
    target_ratio = size[0] / size[1]
    ratio = image.width / image.height
    if ratio > target_ratio:
        new_w = int(image.height * target_ratio)
        left = (image.width - new_w) // 2
        image = image.crop((left, 0, left + new_w, image.height))
    elif ratio < target_ratio:
        new_h = int(image.width / target_ratio)
        top = (image.height - new_h) // 2
        image = image.crop((0, top, image.width, top + new_h))
    return image.resize(size, Image.Resampling.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=1.3, percent=80, threshold=3)
    )


def phone_finish(image: Image.Image) -> Image.Image:
    """Oscurece la mitad inferior para que los controles del teléfono se lean."""
    w, h = image.size
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    for y in range(h):
        t = y / max(1, h - 1)
        # Casi limpio arriba; 58 % negro al pie.
        alpha = int(max(0.0, (t - 0.35) / 0.65) ** 1.5 * 150)
        draw.rectangle([0, y, w, y + 1], fill=(0, 0, 0, alpha))
    return Image.alpha_composite(image.convert("RGBA"), overlay).convert("RGB")


def install(game: str, kind: str, src: Path) -> None:
    size, pattern, quality = TARGETS[kind]
    image = Image.open(src).convert("RGB")
    image = fit(image, size)
    if kind == "phone":
        image = phone_finish(image)
    target = OUT / pattern.format(game=game)
    image.save(target, "WEBP", quality=quality, method=6)
    print(f"[ok] {target.name}  {target.stat().st_size / 1024:.0f} KB")


def main() -> int:
    if not APPROVED.is_dir():
        print(f"Falta la carpeta de aprobados: {APPROVED}")
        return 2
    for game in GAMES:
        for kind in ("stage", "phone", "host", "selector"):
            src = APPROVED / f"{game}_{kind}.png"
            if not src.is_file() and kind == "phone":
                # El teléfono hereda el plató: misma identidad visual, recorte
                # vertical y degradado inferior para los controles.
                src = APPROVED / f"{game}_stage.png"
            if not src.is_file():
                print(f"[skip] falta {src.name}")
                continue
            install(game, kind, src)
        stage = APPROVED / f"{game}_stage.png"
        if stage.is_file():
            selector = fit(Image.open(stage).convert("RGB"), (1280, 720))
            target = OUT / f"game_{game}_art.webp"
            selector.save(target, "WEBP", quality=91, method=6)
            print(f"[ok] {target.name}  {target.stat().st_size / 1024:.0f} KB")
    lobby = APPROVED / "lobby.png"
    if lobby.is_file():
        image = fit(Image.open(lobby).convert("RGB"), (1920, 1080))
        target = OUT / "fiesta_lobby_bg.webp"
        image.save(target, "WEBP", quality=93, method=6)
        print(f"[ok] {target.name}  {target.stat().st_size / 1024:.0f} KB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
