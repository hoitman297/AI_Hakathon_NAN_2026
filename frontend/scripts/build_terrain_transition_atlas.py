from __future__ import annotations

import hashlib
import json
import random
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


TILE = 32
SAMPLE = 128
VARIATIONS = 4
COLS = 16
TRANSITIONS = ("dirt", "farmland", "riverbank", "dry-grass")


def wrapped_sample(source: Image.Image, x: int, y: int) -> Image.Image:
    large = Image.new("RGB", (SAMPLE, SAMPLE))
    src = source.load()
    dst = large.load()
    for oy in range(SAMPLE):
        for ox in range(SAMPLE):
            dst[ox, oy] = src[(x + ox) % source.width, (y + oy) % source.height]
    return large.resize((TILE, TILE), Image.Resampling.BOX)


def organic_mask(bits: int, variation: int, terrain_index: int) -> Image.Image:
    if bits == 0:
        return Image.new("L", (TILE, TILE), 0)
    if bits == 15:
        return Image.new("L", (TILE, TILE), 255)

    scale = 4
    size = TILE * scale
    rng = random.Random(bits * 1009 + variation * 9176 + terrain_index * 65537)
    cx = size // 2 + rng.randint(-2, 2) * scale
    cy = size // 2 + rng.randint(-2, 2) * scale
    radius = rng.randint(5, 8) * scale
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=255)

    if bits & 1:
        d.polygon(((0, 0), (size, 0), (cx + radius, cy), (cx - radius, cy)), fill=255)
    if bits & 2:
        d.polygon(((size, 0), (size, size), (cx, cy + radius), (cx, cy - radius)), fill=255)
    if bits & 4:
        d.polygon(((0, size), (size, size), (cx + radius, cy), (cx - radius, cy)), fill=255)
    if bits & 8:
        d.polygon(((0, 0), (0, size), (cx, cy + radius), (cx, cy - radius)), fill=255)

    # Fine shoreline wobble is interior-only, keeping Wang edges deterministic.
    for _ in range(8 + variation * 2):
        x = rng.randrange(4 * scale, 28 * scale)
        y = rng.randrange(4 * scale, 28 * scale)
        r = rng.randrange(scale, 3 * scale + 1)
        d.ellipse((x - r, y - r, x + r, y + r), fill=rng.choice((0, 255)))

    return mask.filter(ImageFilter.GaussianBlur(2.2 * scale)).resize(
        (TILE, TILE), Image.Resampling.LANCZOS
    )


def blend_color(a: tuple[int, int, int], b: tuple[int, int, int]) -> tuple[int, int, int]:
    return tuple((x + y) // 2 for x, y in zip(a, b))


def enforce_wang_edges(
    tile: Image.Image,
    bits: int,
    grass_h: list[tuple[int, int, int]], grass_v: list[tuple[int, int, int]],
    target_h: list[tuple[int, int, int]], target_v: list[tuple[int, int, int]],
) -> None:
    px = tile.load()
    for i in range(1, TILE - 1):
        px[i, 0] = target_h[i] if bits & 1 else grass_h[i]
        px[TILE - 1, i] = target_v[i] if bits & 2 else grass_v[i]
        px[i, TILE - 1] = target_h[i] if bits & 4 else grass_h[i]
        px[0, i] = target_v[i] if bits & 8 else grass_v[i]
    neutral = blend_color(grass_h[0], target_h[0])
    px[0, 0] = px[TILE - 1, 0] = px[0, TILE - 1] = px[TILE - 1, TILE - 1] = neutral


def main() -> None:
    if len(sys.argv) != 8:
        raise SystemExit(
            "usage: build_terrain_transition_atlas.py GRASS_ATLAS DIRT FARMLAND RIVERBANK DRY_GRASS OUTPUT_DIR SOURCE_COPY_NAME"
        )
    grass_path, dirt_path, farm_path, bank_path, dry_path, output_dir = map(Path, sys.argv[1:7])
    source_copy_name = sys.argv[7]
    output_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(dry_path, output_dir / source_copy_name)

    grass_atlas = Image.open(grass_path).convert("RGB")
    grass_tiles = [
        grass_atlas.crop(((i % 6) * TILE, (i // 6) * TILE, (i % 6 + 1) * TILE, (i // 6 + 1) * TILE))
        for i in range(24)
    ]
    sources = {
        "dirt": Image.open(dirt_path).convert("RGB"),
        "farmland": Image.open(farm_path).convert("RGB"),
        "riverbank": Image.open(bank_path).convert("RGB"),
        "dry-grass": Image.open(dry_path).convert("RGB"),
    }
    grass_h = [grass_tiles[0].getpixel((i, 0)) for i in range(TILE)]
    grass_v = [grass_tiles[0].getpixel((0, i)) for i in range(TILE)]

    tiles: list[Image.Image] = []
    entries = []
    for terrain_index, transition in enumerate(TRANSITIONS):
        source = sources[transition]
        boundary = wrapped_sample(source, 0, 0)
        target_h = [boundary.getpixel((i, TILE // 2)) for i in range(TILE)]
        target_v = [boundary.getpixel((TILE // 2, i)) for i in range(TILE)]
        for bits in range(16):
            for variation in range(VARIATIONS):
                seed = terrain_index * 1000003 + bits * 8191 + variation * 131071
                rng = random.Random(seed)
                grass = grass_tiles[(terrain_index * 5 + bits * 3 + variation * 7) % 24].copy()
                target = wrapped_sample(source, rng.randrange(source.width), rng.randrange(source.height))
                mask = organic_mask(bits, variation, terrain_index)
                tile = Image.composite(target, grass, mask)
                enforce_wang_edges(tile, bits, grass_h, grass_v, target_h, target_v)
                index = len(tiles)
                tiles.append(tile)
                entries.append({
                    "id": index,
                    "transition": f"grass-to-{transition}",
                    "connections": bits,
                    "variation": variation,
                    "north": bool(bits & 1), "east": bool(bits & 2),
                    "south": bool(bits & 4), "west": bool(bits & 8),
                    "x": (index % COLS) * TILE, "y": (index // COLS) * TILE,
                    "width": TILE, "height": TILE,
                })

    rows = len(tiles) // COLS
    atlas = Image.new("RGB", (COLS * TILE, rows * TILE))
    for index, tile in enumerate(tiles):
        atlas.paste(tile, ((index % COLS) * TILE, (index // COLS) * TILE))
    atlas_path = output_dir / "terrain-transitions-256-v1.png"
    atlas.save(atlas_path, optimize=True)
    atlas.resize((atlas.width * 2, atlas.height * 2), Image.Resampling.NEAREST).save(
        output_dir / "terrain-transitions-256-v1-preview.png", optimize=True
    )

    hashes = [hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles]
    metadata = {
        "image": atlas_path.name,
        "tileWidth": TILE, "tileHeight": TILE,
        "columns": COLS, "rows": rows,
        "tileCount": len(tiles), "uniqueTileCount": len(set(hashes)),
        "transitionCount": len(TRANSITIONS), "variationsPerMask": VARIATIONS,
        "masksPerTransition": 16, "margin": 0, "spacing": 0,
        "connectionBits": {"north": 1, "east": 2, "south": 4, "west": 8},
        "transitions": list(TRANSITIONS), "tiles": entries,
    }
    (output_dir / "terrain-transitions-256-v1.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: value for key, value in metadata.items() if key != "tiles"}))


if __name__ == "__main__":
    main()
