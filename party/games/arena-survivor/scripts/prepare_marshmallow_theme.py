"""Prepare Marshmallow Mayhem atlases without cutting through visible artwork.

The extractor treats the magenta key as background, grows a logical atlas cell
until every edge is background-only, and only then crops the visible object.
Weapon exports are additionally normalized to the renderer's upward source axis.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from pathlib import Path
from shutil import copy2

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "public" / "art-source" / "marshmallow-mayhem"
THEME_ROOTS = [
    ROOT / "public" / "host" / "arena-survivor" / "themes" / "marshmallow-mayhem",
    ROOT / "public" / "controller" / "arena-survivor" / "themes" / "marshmallow-mayhem",
]
EDGE_ALPHA = 18
EDGE_FEATHER_RADIUS = 0.45

CHARACTERS = [
    "schrotto-scharfschuss",
    "kloppbert-keulenwucht",
    "funkenberta-flaemmchen",
    "kanni-baldrian",
    "doktor-knolle",
    "sir-pampel-panzer",
    "flitzelotte",
    "professor-paradox",
    "rundling-allround",
    "pruegler-brawler",
    "jaeger-ranger",
    "gluecksknolle-lucky",
    "ackerling-farmer",
]

ENEMIES = [
    "jam-ooze",
    "licorice-crawler",
    "ember-imp",
    "cocoa-spitter",
    "cracker-golem",
    "campfire-knight",
    "smore-titan",
]

WEAPONS = [
    "cleaver",
    "coil-rifle",
    "ember-wand",
    "frost-orb",
    "gear-launcher",
    "halberd",
    "hunter-bow",
    "lance",
    "mace",
    "prism-scepter",
    "rust-blade",
    "scrap-smg",
    "spear",
    "spark-rod",
    "stick",
    "stone",
    "survivor-pistol",
    "twin-daggers",
    "venom-siphon",
    "war-hammer",
    "pitchfork",
]

# Pillow rotates counter-clockwise. Each value places the functional tip/muzzle
# at twelve o'clock, which is the source orientation expected by the renderer.
WEAPON_ROTATIONS = [
    90, 90, 50, 50, 90, 45, 90,
    65, 45, 45, 55, 90, 60, 55,
    80, 0, 90, 55, 90, 55, 55,
]

ITEMS = [
    "mushroom-cap",
    "stone-heart",
    "herbal-bandage",
    "vampire-brooch",
    "iron-shell",
    "heavy-coat",
    "power-bracelet",
    "berserker-feather",
    "heavy-bullets",
    "drill-core",
    "glass-eye",
    "scope-lens",
    "blindfold",
    "trigger-glove",
    "runner-boots",
    "duelist-ribbon",
    "arcane-crystal",
    "magnet-core",
    "thorn-chain",
    "medal",
    "lucky-charm",
    "harvest-sprout",
]

UPGRADES = [
    "attack-speed",
    "armor",
    "crit",
    "damage",
    "range",
    "move-speed",
    "max-health",
    "life-steal",
]


@dataclass(frozen=True)
class AtlasSpec:
    source: str
    columns: int
    rows: int
    names: list[str]
    category: str
    size: int
    rotations: list[float] | None = None


def remove_chroma_key(image: Image.Image) -> Image.Image:
    rgba_image = image.convert("RGBA")
    source_alpha = np.asarray(rgba_image.getchannel("A"))
    if np.any(source_alpha == 0):
        return polish_alpha_edges(rgba_image, feather=False)
    pixels = np.asarray(rgba_image).copy()
    rgb = pixels[:, :, :3].astype(np.float32)
    border = np.concatenate(
        [rgb[:20].reshape(-1, 3), rgb[-20:].reshape(-1, 3), rgb[:, :20].reshape(-1, 3), rgb[:, -20:].reshape(-1, 3)]
    )
    key = np.median(border, axis=0)
    distance = np.linalg.norm(rgb - key, axis=2)
    coverage = np.clip((distance - 50.0) / 70.0, 0.0, 1.0)
    pixels[:, :, 3] = np.minimum(
        pixels[:, :, 3].astype(np.float32), coverage * 255.0
    ).astype(np.uint8)

    # Propagate nearby opaque foreground colors into the soft matte. This
    # removes the generated magenta fringe without recoloring opaque reds or
    # violets inside the artwork.
    edge = (coverage > 0.0) & (coverage < 0.98)
    known = coverage >= 0.98
    filled = rgb.copy()
    height, width = coverage.shape
    for _ in range(12):
        accumulator = np.zeros_like(filled)
        neighbor_count = np.zeros((height, width), dtype=np.float32)
        for offset_y, offset_x in (
            (-1, -1), (-1, 0), (-1, 1),
            (0, -1), (0, 1),
            (1, -1), (1, 0), (1, 1),
        ):
            source_y = slice(max(0, -offset_y), min(height, height - offset_y))
            source_x = slice(max(0, -offset_x), min(width, width - offset_x))
            target_y = slice(max(0, offset_y), min(height, height + offset_y))
            target_x = slice(max(0, offset_x), min(width, width + offset_x))
            source_known = known[source_y, source_x]
            accumulator[target_y, target_x] += filled[source_y, source_x] * source_known[:, :, None]
            neighbor_count[target_y, target_x] += source_known
        newly_known = edge & ~known & (neighbor_count > 0)
        if not np.any(newly_known):
            break
        filled[newly_known] = accumulator[newly_known] / neighbor_count[newly_known][:, None]
        known[newly_known] = True
    pixels[:, :, :3][edge & known] = filled[edge & known].astype(np.uint8)
    pixels[:, :, :3][edge & ~known] = 0
    pixels[pixels[:, :, 3] == 0, :3] = 0
    return polish_alpha_edges(Image.fromarray(pixels, "RGBA"), feather=False)


def polish_alpha_edges(image: Image.Image, *, feather: bool = True) -> Image.Image:
    """Replace chroma-contaminated matte colors and gently smooth the final edge.

    Generated alpha atlases can still retain the key color in semi-transparent
    pixels. Those colors become visible again when Canvas scales or rotates a
    sprite. We extend nearby opaque foreground colors into the matte before
    applying a sub-pixel alpha feather, without changing opaque artwork.
    """
    pixels = np.asarray(image.convert("RGBA")).copy()
    alpha = pixels[:, :, 3]
    rgb = pixels[:, :, :3].astype(np.float32)
    known = alpha >= 245
    filled = rgb.copy()
    height, width = alpha.shape

    for _ in range(3):
        accumulator = np.zeros_like(filled)
        neighbor_count = np.zeros((height, width), dtype=np.float32)
        for offset_y, offset_x in (
            (-1, -1), (-1, 0), (-1, 1),
            (0, -1), (0, 1),
            (1, -1), (1, 0), (1, 1),
        ):
            source_y = slice(max(0, -offset_y), min(height, height - offset_y))
            source_x = slice(max(0, -offset_x), min(width, width - offset_x))
            target_y = slice(max(0, offset_y), min(height, height + offset_y))
            target_x = slice(max(0, offset_x), min(width, width + offset_x))
            source_known = known[source_y, source_x]
            accumulator[target_y, target_x] += filled[source_y, source_x] * source_known[:, :, None]
            neighbor_count[target_y, target_x] += source_known
        newly_known = ~known & (neighbor_count > 0)
        if not np.any(newly_known):
            break
        filled[newly_known] = accumulator[newly_known] / neighbor_count[newly_known][:, None]
        known[newly_known] = True

    matte = (alpha >= 12) & (alpha < 245)
    pixels[:, :, :3][matte & known] = np.clip(filled[matte & known], 0, 255).astype(np.uint8)
    if feather:
        softened = Image.fromarray(alpha, "L").filter(ImageFilter.GaussianBlur(EDGE_FEATHER_RADIUS))
        softened_alpha = np.asarray(softened).astype(np.float32)
        # Drop barely visible key remnants, then expand the useful matte back
        # to the full range. This removes the broad dark/chroma halo while
        # retaining a narrow anti-aliased contour.
        pixels[:, :, 3] = np.clip((softened_alpha - 12) * 255 / 243, 0, 255).astype(np.uint8)
    pixels[pixels[:, :, 3] == 0, :3] = 0
    return Image.fromarray(pixels, "RGBA")


def edge_has_art(alpha: np.ndarray, side: str) -> bool:
    if side == "left":
        edge = alpha[:, :3]
    elif side == "right":
        edge = alpha[:, -3:]
    elif side == "top":
        edge = alpha[:3, :]
    else:
        edge = alpha[-3:, :]
    return bool(np.any(edge > EDGE_ALPHA))


def adaptive_cell(image: Image.Image, columns: int, rows: int, index: int) -> Image.Image:
    cell_width = image.width / columns
    cell_height = image.height / rows
    column = index % columns
    row = index // columns
    left = round(column * cell_width)
    top = round(row * cell_height)
    right = round((column + 1) * cell_width)
    bottom = round((row + 1) * cell_height)
    step = max(2, round(min(cell_width, cell_height) * 0.015))

    # If generated art touches a nominal cell edge, move that edge outward
    # until a background-only seam is found. Never silently cut foreground.
    for _ in range(64):
        crop = image.crop((left, top, right, bottom))
        alpha = np.asarray(crop.getchannel("A"))
        moved = False
        if edge_has_art(alpha, "left") and left > 0:
            left = max(0, left - step)
            moved = True
        if edge_has_art(alpha, "right") and right < image.width:
            right = min(image.width, right + step)
            moved = True
        if edge_has_art(alpha, "top") and top > 0:
            top = max(0, top - step)
            moved = True
        if edge_has_art(alpha, "bottom") and bottom < image.height:
            bottom = min(image.height, bottom + step)
            moved = True
        if not moved:
            break
    else:
        raise RuntimeError(f"No background-only cut found for atlas cell {index}")

    crop = image.crop((left, top, right, bottom))
    visible_box = crop.getchannel("A").getbbox()
    if visible_box is None:
        raise RuntimeError(f"Atlas cell {index} contains no visible asset")

    padding = max(8, round(min(cell_width, cell_height) * 0.035))
    object_left = max(0, visible_box[0] - padding)
    object_top = max(0, visible_box[1] - padding)
    object_right = min(crop.width, visible_box[2] + padding)
    object_bottom = min(crop.height, visible_box[3] + padding)
    result = crop.crop((object_left, object_top, object_right, object_bottom))
    result_alpha = np.asarray(result.getchannel("A"))
    if any(edge_has_art(result_alpha, side) for side in ("left", "right", "top", "bottom")):
        raise RuntimeError(f"Unsafe foreground cut detected for atlas cell {index}")
    return result


def component_cells(image: Image.Image, columns: int, rows: int, count: int) -> list[Image.Image]:
    """Assign complete connected components to cells before cropping.

    This deliberately does not use mathematical grid edges as cut lines. Large
    creatures, weapon tips and detached sparks may cross those lines without
    being clipped or leaking into the neighboring export.
    """
    alpha = np.asarray(image.getchannel("A"))
    foreground = alpha > EDGE_ALPHA
    visited = np.zeros(foreground.shape, dtype=bool)
    labels = np.zeros(foreground.shape, dtype=np.int32)
    components: list[tuple[int, int, float, float]] = []
    next_label = 0

    for seed_y, seed_x in np.argwhere(foreground):
        if visited[seed_y, seed_x]:
            continue
        next_label += 1
        queue: deque[tuple[int, int]] = deque([(int(seed_y), int(seed_x))])
        visited[seed_y, seed_x] = True
        size = 0
        sum_x = 0
        sum_y = 0
        while queue:
            y, x = queue.popleft()
            labels[y, x] = next_label
            size += 1
            sum_x += x
            sum_y += y
            for offset_y in (-1, 0, 1):
                for offset_x in (-1, 0, 1):
                    if offset_x == 0 and offset_y == 0:
                        continue
                    next_y = y + offset_y
                    next_x = x + offset_x
                    if (
                        0 <= next_y < foreground.shape[0]
                        and 0 <= next_x < foreground.shape[1]
                        and foreground[next_y, next_x]
                        and not visited[next_y, next_x]
                    ):
                        visited[next_y, next_x] = True
                        queue.append((next_y, next_x))
        if size >= 5:
            components.append((next_label, size, sum_x / size, sum_y / size))

    cell_width = image.width / columns
    cell_height = image.height / rows
    assignments: list[list[int]] = [[] for _ in range(columns * rows)]
    for label, _size, center_x, center_y in components:
        best_index = min(
            range(columns * rows),
            key=lambda index: (
                (center_x - ((index % columns) + 0.5) * cell_width) ** 2 / cell_width ** 2
                + (center_y - ((index // columns) + 0.5) * cell_height) ** 2 / cell_height ** 2
            ),
        )
        assignments[best_index].append(label)

    pixels = np.asarray(image).copy()
    results: list[Image.Image] = []
    for index in range(count):
        assigned = assignments[index]
        if not assigned:
            raise RuntimeError(f"Atlas cell {index} contains no connected foreground components")
        keep = np.isin(labels, assigned)
        isolated = pixels.copy()
        isolated[~keep, :] = 0
        isolated_image = Image.fromarray(isolated, "RGBA")
        visible_box = isolated_image.getchannel("A").getbbox()
        if visible_box is None:
            raise RuntimeError(f"Atlas cell {index} contains no visible asset")
        padding = max(10, round(min(cell_width, cell_height) * 0.035))
        left = max(0, visible_box[0] - padding)
        top = max(0, visible_box[1] - padding)
        right = min(image.width, visible_box[2] + padding)
        bottom = min(image.height, visible_box[3] + padding)
        result = isolated_image.crop((left, top, right, bottom))
        result_alpha = np.asarray(result.getchannel("A"))
        if any(edge_has_art(result_alpha, side) for side in ("left", "right", "top", "bottom")):
            raise RuntimeError(f"Unsafe foreground cut detected for atlas cell {index}")
        results.append(result)
    return results


def normalized_canvas(image: Image.Image, size: int, margin: int = 18) -> Image.Image:
    scale = min((size - margin * 2) / image.width, (size - margin * 2) / image.height)
    image = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((size - image.width) // 2, (size - image.height) // 2))
    return polish_alpha_edges(canvas)


def save_to_theme_roots(image: Image.Image, relative_path: Path) -> None:
    for root in THEME_ROOTS:
        destination = root / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        image.save(destination, optimize=True)


def build_portrait(torso: Image.Image, character_index: int) -> Image.Image:
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    torso_layer = torso.copy()
    torso_layer.thumbnail((390, 360), Image.Resampling.LANCZOS)
    torso_x = (512 - torso_layer.width) // 2
    torso_y = 92
    canvas.alpha_composite(torso_layer, (torso_x, torso_y))

    draw = ImageDraw.Draw(canvas)
    eye_y = 218
    for eye_x in (214, 298):
        draw.ellipse((eye_x - 24, eye_y - 29, eye_x + 24, eye_y + 29), fill="#fff9e6", outline="#532d1f", width=5)
        draw.ellipse((eye_x - 8, eye_y - 13, eye_x + 12, eye_y + 17), fill="#382119")
        draw.ellipse((eye_x - 4, eye_y - 10, eye_x + 3, eye_y - 3), fill="#ffffff")
    draw.arc((230, 235, 282, 286), start=8, end=172, fill="#633528", width=5)

    motion = SOURCE / "motion-kit"
    if CHARACTERS[character_index] == "sir-pampel-panzer":
        headgear = Image.open(motion / "helmet.png").convert("RGBA")
        headgear.thumbnail((430, 215), Image.Resampling.LANCZOS)
        canvas.alpha_composite(headgear, ((512 - headgear.width) // 2, 32))
    else:
        colors = ["blue", "red", "violet", "green", "teal", "gold"]
        color = colors[character_index % len(colors)]
        headgear = Image.open(motion / "headbands" / f"headband-{color}.png").convert("RGBA")
        headgear = headgear.resize((424, 78), Image.Resampling.LANCZOS)
        canvas.alpha_composite(headgear, (44, 122))

    hand = Image.open(motion / "hand-knob.png").convert("RGBA")
    foot = Image.open(motion / "foot-knob.png").convert("RGBA")
    hand.thumbnail((82, 72), Image.Resampling.LANCZOS)
    foot.thumbnail((98, 72), Image.Resampling.LANCZOS)
    canvas.alpha_composite(hand, (48, 322))
    canvas.alpha_composite(hand, (512 - 48 - hand.width, 322))
    canvas.alpha_composite(foot, (132, 424))
    canvas.alpha_composite(foot, (512 - 132 - foot.width, 424))
    return polish_alpha_edges(canvas)


def export_atlas(spec: AtlasSpec) -> None:
    atlas = remove_chroma_key(Image.open(SOURCE / spec.source))
    cells = component_cells(atlas, spec.columns, spec.rows, len(spec.names))
    for index, (name, asset) in enumerate(zip(spec.names, cells)):
        if spec.rotations:
            asset = asset.rotate(spec.rotations[index], expand=True, resample=Image.Resampling.BICUBIC)
            asset = polish_alpha_edges(asset, feather=False)
            visible_box = asset.getchannel("A").getbbox()
            if visible_box:
                asset = asset.crop(visible_box)
        normalized = normalized_canvas(asset, spec.size)
        save_to_theme_roots(normalized, Path(spec.category) / f"{name}.png")
        if spec.category == "characters/torsos":
            portrait = build_portrait(normalized, index)
            save_to_theme_roots(portrait, Path("characters/portraits") / f"{name}.png")


def copy_shared_assets() -> None:
    for root in THEME_ROOTS:
        (root / "backgrounds").mkdir(parents=True, exist_ok=True)
        copy2(SOURCE / "background.png", root / "backgrounds" / "cocoa-clearing.png")
        rig = root / "rig"
        rig.mkdir(parents=True, exist_ok=True)
        for name in ("hand-knob.png", "foot-knob.png", "helmet.png"):
            polished = polish_alpha_edges(Image.open(SOURCE / "motion-kit" / name))
            polished.save(rig / name, optimize=True)
        headbands = rig / "headbands"
        headbands.mkdir(parents=True, exist_ok=True)
        for source in (SOURCE / "motion-kit" / "headbands").glob("headband-*.png"):
            polished = polish_alpha_edges(Image.open(source))
            polished.save(headbands / source.name, optimize=True)


def main() -> None:
    specs = [
        AtlasSpec("character-torsos-atlas-alpha.png", 4, 4, CHARACTERS, "characters/torsos", 512),
        AtlasSpec("enemies-atlas-alpha.png", 4, 2, ENEMIES, "enemies", 512),
        AtlasSpec("weapons-atlas-alpha.png", 7, 3, WEAPONS, "weapons", 384, WEAPON_ROTATIONS),
        AtlasSpec("items-upgrades-atlas-alpha.png", 6, 5, ITEMS, "items", 320),
        AtlasSpec("items-upgrades-atlas-alpha.png", 6, 5, UPGRADES, "upgrades", 320),
        AtlasSpec("pickups-atlas-alpha.png", 2, 1, ["health", "material"], "pickups", 320),
    ]
    for spec in specs:
        if spec.category == "upgrades":
            # Upgrade icons start after the 22 item cells.
            atlas = remove_chroma_key(Image.open(SOURCE / spec.source))
            cells = component_cells(atlas, spec.columns, spec.rows, len(ITEMS) + len(UPGRADES))
            for offset, name in enumerate(spec.names, start=len(ITEMS)):
                normalized = normalized_canvas(cells[offset], spec.size)
                save_to_theme_roots(normalized, Path(spec.category) / f"{name}.png")
            continue
        export_atlas(spec)
    copy_shared_assets()
    print("Marshmallow Mayhem assets prepared with background-only crop edges.")


if __name__ == "__main__":
    main()
