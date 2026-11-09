#!/usr/bin/env python3
"""Safely split horizontal chroma-key asset sheets without cutting artwork."""

from __future__ import annotations

import argparse
from collections import Counter
from pathlib import Path

from PIL import Image


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Split a horizontal chroma-key sheet at verified background-only "
            "columns, remove the key, trim transparent padding, and save PNGs."
        )
    )
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--names", nargs="+", required=True)
    parser.add_argument(
        "--tolerance",
        type=int,
        default=35,
        help="Maximum per-channel distance from the sampled key color.",
    )
    parser.add_argument(
        "--search-ratio",
        type=float,
        default=0.12,
        help="Half-width of the safe-cut search window as a fraction of sheet width.",
    )
    return parser.parse_args()


def sample_key_color(image: Image.Image) -> tuple[int, int, int]:
    width, height = image.size
    border_pixels = []
    for x in range(width):
        border_pixels.append(image.getpixel((x, 0))[:3])
        border_pixels.append(image.getpixel((x, height - 1))[:3])
    for y in range(height):
        border_pixels.append(image.getpixel((0, y))[:3])
        border_pixels.append(image.getpixel((width - 1, y))[:3])
    return Counter(border_pixels).most_common(1)[0][0]


def is_key_pixel(
    pixel: tuple[int, int, int, int],
    key: tuple[int, int, int],
    tolerance: int,
) -> bool:
    close_to_sample = (
        max(abs(pixel[channel] - key[channel]) for channel in range(3)) <= tolerance
    )
    if close_to_sample:
        return True

    key_is_magenta = key[0] >= 180 and key[2] >= 160 and key[1] <= 110
    if not key_is_magenta:
        return False

    red, green, blue = pixel[:3]
    return (
        red >= 175
        and blue >= 150
        and green <= 120
        and min(red, blue) - green >= 65
    )


def column_is_key_only(
    image: Image.Image,
    x: int,
    key: tuple[int, int, int],
    tolerance: int,
) -> bool:
    return all(
        is_key_pixel(image.getpixel((x, y)), key, tolerance)
        for y in range(image.height)
    )


def find_safe_cut(
    image: Image.Image,
    expected_x: int,
    search_radius: int,
    key: tuple[int, int, int],
    tolerance: int,
) -> int:
    minimum_x = max(1, expected_x - search_radius)
    maximum_x = min(image.width - 1, expected_x + search_radius)
    candidates = sorted(
        range(minimum_x, maximum_x + 1),
        key=lambda x: (abs(x - expected_x), x),
    )

    for x in candidates:
        if column_is_key_only(image, x, key, tolerance):
            return x

    best_x = min(
        candidates,
        key=lambda x: sum(
            not is_key_pixel(image.getpixel((x, y)), key, tolerance)
            for y in range(image.height)
        ),
    )
    non_key_pixels = sum(
        not is_key_pixel(image.getpixel((best_x, y)), key, tolerance)
        for y in range(image.height)
    )
    raise RuntimeError(
        f"No background-only cut line near x={expected_x}. "
        f"Best candidate x={best_x} still intersects {non_key_pixels} non-key pixels."
    )


def remove_key(image: Image.Image, key: tuple[int, int, int], tolerance: int) -> Image.Image:
    output = Image.new("RGBA", image.size)
    source = image.load()
    pixels = []
    for y in range(image.height):
        for x in range(image.width):
            pixel = source[x, y]
            if is_key_pixel(pixel, key, tolerance):
                pixels.append((pixel[0], pixel[1], pixel[2], 0))
            else:
                pixels.append((pixel[0], pixel[1], pixel[2], 255))
    output.putdata(pixels)
    return output


def trim_transparent(image: Image.Image, padding: int = 2) -> Image.Image:
    alpha_box = image.getchannel("A").getbbox()
    if alpha_box is None:
        raise RuntimeError("A sliced cell contains no visible artwork.")
    left, top, right, bottom = alpha_box
    return image.crop(
        (
            max(0, left - padding),
            max(0, top - padding),
            min(image.width, right + padding),
            min(image.height, bottom + padding),
        )
    )


def main() -> None:
    args = parse_args()
    image = Image.open(args.input).convert("RGBA")
    asset_count = len(args.names)
    if asset_count < 2:
        raise ValueError("At least two output names are required.")

    key = sample_key_color(image)
    search_radius = max(8, round(image.width * args.search_ratio))
    cuts = [0]
    for index in range(1, asset_count):
        expected_x = round(image.width * index / asset_count)
        cuts.append(
            find_safe_cut(
                image,
                expected_x,
                search_radius,
                key,
                args.tolerance,
            )
        )
    cuts.append(image.width)

    if cuts != sorted(cuts) or len(set(cuts)) != len(cuts):
        raise RuntimeError(f"Resolved cut lines are invalid: {cuts}")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    for index, name in enumerate(args.names):
        cell = image.crop((cuts[index], 0, cuts[index + 1], image.height))
        result = trim_transparent(remove_key(cell, key, args.tolerance))
        destination = args.output_dir / name
        result.save(destination)
        print(f"{name}: x={cuts[index]}..{cuts[index + 1]}, size={result.size}")

    print(f"Key color: #{key[0]:02x}{key[1]:02x}{key[2]:02x}")
    print(f"Verified background-only cuts: {cuts[1:-1]}")


if __name__ == "__main__":
    main()
