#!/usr/bin/env python3
"""Crea las poses del presentador girando SUS brazos, no otro personaje.

Parte del maestro original (`docs/assets/games/narrator_master.png`) para conservar
exactamente la cara, el cuerpo y el emblema de Vision+. Separa los brazos con una
máscara poligonal, los gira alrededor del hombro y reconstruye la pose. Así las
siluetas cambian de verdad sin perder al personaje.

Salida: `app/src/main/res/drawable-nodpi/narrator_<mood>.png`, los 4 frames de
idle, el avatar y una hoja de contacto en `docs/assets/games/narrator_poses/`.
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
MASTER = ROOT / "docs/assets/games/narrator_master.png"
LOGO = ROOT / "app/src/main/res/drawable-nodpi/ic_vision_mark.png"
ASSETS = ROOT / "app/src/main/res/drawable-nodpi"
DOCS = ROOT / "docs/assets/games/narrator_poses"

# Polígonos de cada brazo sobre el maestro de 739x885. Se excluye el torso.
LEFT_ARM = [
    (250, 360), (220, 378), (190, 400), (165, 430), (145, 465),
    (120, 500), (95, 535), (60, 565), (30, 600), (28, 625),
    (70, 622), (115, 595), (145, 550), (175, 505), (205, 455),
    (235, 418), (258, 392),
]
RIGHT_ARM = [
    (489, 360), (519, 378), (549, 400), (574, 430), (594, 465),
    (619, 500), (644, 535), (679, 565), (709, 600), (711, 625),
    (669, 622), (624, 595), (594, 550), (564, 505), (534, 455),
    (504, 418), (481, 392),
]
LEFT_PIVOT = (247, 388)
RIGHT_PIVOT = (492, 388)
JOINT_RADIUS = 26

# Ángulos en grados (Pillow: positivo = antihorario visual).
POSES = {
    "welcome": (LEFT_PIVOT, -105, RIGHT_PIVOT, 0),
    "thinking": (LEFT_PIVOT, 0, RIGHT_PIVOT, 118),
    "happy": (LEFT_PIVOT, -78, RIGHT_PIVOT, 78),
    "oops": (LEFT_PIVOT, -38, RIGHT_PIVOT, 38),
    "celebrate": (LEFT_PIVOT, -125, RIGHT_PIVOT, 125),
}


def arm_layer(master: Image.Image, polygon: list[tuple[int, int]]) -> Image.Image:
    layer = Image.new("RGBA", master.size, (0, 0, 0, 0))
    layer.paste(master, (0, 0))
    mask = Image.new("L", master.size, 0)
    ImageDraw.Draw(mask).polygon(polygon, fill=255)
    layer.putalpha(ImageChops.multiply(layer.getchannel("A"), mask))
    return layer


def erase_arm(master: Image.Image, polygon: list[tuple[int, int]]) -> Image.Image:
    base = master.copy()
    mask = Image.new("L", master.size, 0)
    ImageDraw.Draw(mask).polygon(polygon, fill=255)
    alpha = base.getchannel("A")
    base.putalpha(ImageChops.multiply(alpha, ImageChops.invert(mask)))
    return base


def rotate_arm(layer: Image.Image, angle: float, pivot: tuple[int, int]) -> Image.Image:
    if abs(angle) < 0.1:
        return layer
    return layer.rotate(angle, center=pivot, resample=Image.BICUBIC, expand=False)


def build_pose(base: Image.Image, left: Image.Image, right: Image.Image,
               angle_left: float, angle_right: float) -> Image.Image:
    pose = base.copy()
    # Los dos brazos se vuelven a pegar SIEMPRE: el brazo que no cambia se pega
    # sin girar. Si se omite, queda el hueco del brazo borrado.
    pose.alpha_composite(rotate_arm(left, angle_left, LEFT_PIVOT))
    pose.alpha_composite(rotate_arm(right, angle_right, RIGHT_PIVOT))
    if abs(angle_left) >= 0.1:
        draw_joint(pose, LEFT_PIVOT)
    if abs(angle_right) >= 0.1:
        draw_joint(pose, RIGHT_PIVOT)
    return pose


def draw_joint(image: Image.Image, pivot: tuple[int, int]) -> None:
    x, y = pivot
    radius = JOINT_RADIUS
    draw = ImageDraw.Draw(image)
    draw.ellipse(
        (x - radius, y - radius, x + radius, y + radius),
        fill=(18, 28, 58, 255), outline=(10, 16, 30, 255), width=4,
    )
    draw.arc(
        (x - radius + 4, y - radius + 4, x + radius - 4, y + radius - 4),
        start=205, end=335, fill=(80, 130, 230, 180), width=3,
    )


def save_frame(master: Image.Image, logo: Image.Image, name: str,
               shift_y: int = 0, scale: float = 1.0) -> None:
    height = 470
    width = int(master.width * height / master.height)
    char = master.resize((width, height), Image.LANCZOS)
    if scale != 1.0:
        width, height = int(width * scale), int(height * scale)
        char = char.resize((width, height), Image.LANCZOS)
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    x = (512 - width) // 2
    y = 512 - height - 18 + shift_y
    canvas.alpha_composite(char, (x, y))
    # El maestro trae el aro de luz del pecho; el emblema oficial se compone aquí.
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
    canvas.save(ASSETS / f"narrator_{name}.png", optimize=True)


def contact_sheet(paths: dict[str, Path]) -> None:
    DOCS.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGB", (1100, 320), (7, 11, 19))
    draw = ImageDraw.Draw(sheet)
    for index, (name, path) in enumerate(paths.items()):
        image = Image.open(path).convert("RGBA")
        image.thumbnail((190, 250), Image.LANCZOS)
        x, y = 12 + index * 215, 20
        sheet.paste(image, (x, y), image)
        draw.text((x, y + 260), name, fill=(220, 230, 245))
    sheet.save(DOCS / "contact_sheet.png", optimize=True)


def silhouette_iou(images: dict[str, Path]) -> None:
    import numpy as np
    arrays = {
        name: np.array(Image.open(path).convert("RGBA"))[:, :, 3] > 16
        for name, path in images.items()
    }
    names = list(arrays)
    for index, first in enumerate(names):
        for second in names[index + 1:]:
            a, b = arrays[first], arrays[second]
            iou = np.logical_and(a, b).sum() / max(1, np.logical_or(a, b).sum())
            print(f"{first:9} vs {second:9} IoU={iou:.3f}")


def main() -> int:
    master = Image.open(MASTER).convert("RGBA")
    logo = Image.open(LOGO).convert("RGBA")
    ASSETS.mkdir(parents=True, exist_ok=True)
    DOCS.mkdir(parents=True, exist_ok=True)
    left = arm_layer(master, LEFT_ARM)
    right = arm_layer(master, RIGHT_ARM)
    base = erase_arm(erase_arm(master, LEFT_ARM), RIGHT_ARM)

    paths: dict[str, Path] = {}
    for name, (lp, la, rp, ra) in POSES.items():
        pose = build_pose(base, left, right, la, ra)
        save_frame(pose, logo, name)
        pose.save(DOCS / f"{name}.png", optimize=True)
        paths[name] = ASSETS / f"narrator_{name}.png"
        print(f"-> narrator_{name}.png")
    # Idle y avatar salen del maestro intacto (mismo personaje).
    save_frame(master, logo, "idle_0", 0, 1.0)
    save_frame(master, logo, "idle_1", -3, 1.004)
    save_frame(master, logo, "idle_2", -5, 1.008)
    save_frame(master, logo, "idle_3", -3, 1.004)
    save_frame(master, logo, "static", 0, 1.0)
    avatar = Image.open(ASSETS / "narrator_idle_0.png").convert("RGBA")
    avatar.crop((112, 10, 400, 300)).resize((256, 256), Image.LANCZOS).save(
        ASSETS / "narrator_avatar.png", optimize=True
    )
    contact_sheet(paths)
    silhouette_iou(paths)
    print("listo")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
