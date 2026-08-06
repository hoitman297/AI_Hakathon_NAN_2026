from __future__ import annotations

import hashlib
import json
import random
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


TILE = 32
VARIATIONS = 6
COLS = 10
MASKS = [1, 2, 4, 8, 5, 10, 3, 6, 12, 9, 7, 14, 13, 11, 15]
NAMES = {
    1: "end-n", 2: "end-e", 4: "end-s", 8: "end-w",
    5: "straight-ns", 10: "straight-ew",
    3: "corner-ne", 6: "corner-es", 12: "corner-sw", 9: "corner-wn",
    7: "t-nes", 14: "t-esw", 13: "t-swn", 11: "t-wne",
    15: "cross",
}


def crop_wrapped(source: Image.Image, x: int, y: int, size: int) -> Image.Image:
    out = Image.new("RGB", (size, size))
    source_px = source.load()
    out_px = out.load()
    for oy in range(size):
        for ox in range(size):
            out_px[ox, oy] = source_px[(x + ox) % source.width, (y + oy) % source.height]
    return out


def road_mask(bits: int, variation: int) -> Image.Image:
    scale = 4
    size = TILE * scale
    rng = random.Random(bits * 1009 + variation * 9176)
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    center = size // 2
    half = 8 * scale

    # A shared centered opening reaches every connected edge. Irregularity begins
    # inward from the boundary so every matching road endpoint aligns exactly.
    draw.ellipse((center - half, center - half, center + half, center + half), fill=255)
    arms = {
        1: (center - half, 0, center + half, center),
        2: (center, center - half, size, center + half),
        4: (center - half, center, center + half, size),
        8: (0, center - half, center, center + half),
    }
    for bit, rect in arms.items():
        if bits & bit:
            draw.rectangle(rect, fill=255)

    # Small edge wobble, kept away from the outer four gameplay pixels.
    for _ in range(9 + variation):
        angle_side = rng.choice([1, 2, 4, 8])
        if not (bits & angle_side):
            continue
        radius = rng.randint(1, 3) * scale
        if angle_side in (1, 4):
            x = center + rng.choice([-1, 1]) * rng.randint(half - scale, half + scale * 2)
            y = rng.randint(5 * scale, (TILE - 5) * scale)
        else:
            x = rng.randint(5 * scale, (TILE - 5) * scale)
            y = center + rng.choice([-1, 1]) * rng.randint(half - scale, half + scale * 2)
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=255)

    return mask.filter(ImageFilter.GaussianBlur(scale * 0.65)).resize(
        (TILE, TILE), Image.Resampling.LANCZOS
    )


def add_micro_details(tile: Image.Image, mask: Image.Image, seed: int) -> None:
    rng = random.Random(seed)
    draw = ImageDraw.Draw(tile)
    m = mask.load()

    # Footprints, tiny stones, and occasional paired wheel-track flecks.
    for _ in range(3 + seed % 4):
        x, y = rng.randrange(4, 28), rng.randrange(4, 28)
        if m[x, y] > 210:
            color = rng.choice([(112, 79, 42), (133, 96, 53), (175, 145, 91)])
            draw.point((x, y), fill=color)
            if rng.random() < 0.35 and x + 1 < TILE:
                draw.point((x + 1, y), fill=color)
    if seed % 3 == 0:
        for y in range(7, 27, 5):
            for x in (12, 20):
                if m[x, y] > 210:
                    draw.point((x, y), fill=(126, 88, 47))

    # Sparse edge grass encroachment.
    for _ in range(5):
        x, y = rng.randrange(2, 30), rng.randrange(2, 30)
        if 55 < m[x, y] < 205:
            draw.point((x, y), fill=rng.choice([(81, 103, 44), (101, 119, 50)]))


def enforce_boundary(
    tile: Image.Image,
    bits: int,
    grass_h: list[tuple[int, int, int]],
    grass_v: list[tuple[int, int, int]],
    dirt_h: list[tuple[int, int, int]],
    dirt_v: list[tuple[int, int, int]],
) -> None:
    px = tile.load()
    for i in range(TILE):
        in_road = 8 <= i < 24
        px[i, 0] = dirt_h[i] if bits & 1 and in_road else grass_h[i]
        px[TILE - 1, i] = dirt_v[i] if bits & 2 and in_road else grass_v[i]
        px[i, TILE - 1] = dirt_h[i] if bits & 4 and in_road else grass_h[i]
        px[0, i] = dirt_v[i] if bits & 8 and in_road else grass_v[i]


def main() -> None:
    if len(sys.argv) not in (4, 5):
        raise SystemExit("usage: build_dirt_road_atlas.py DIRT_SOURCE GRASS_ATLAS OUTPUT_DIR [VERSION]")
    dirt_path, grass_path, output_dir = map(Path, sys.argv[1:4])
    version = sys.argv[4] if len(sys.argv) == 5 else "v1"
    output_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(dirt_path, output_dir / f"dirt-road-material-source-{version}.png")

    dirt = Image.open(dirt_path).convert("RGB")
    grass_atlas = Image.open(grass_path).convert("RGB")
    grass_tiles = [
        grass_atlas.crop(((i % 6) * TILE, (i // 6) * TILE, (i % 6 + 1) * TILE, (i // 6 + 1) * TILE))
        for i in range(24)
    ]
    grass_h = [grass_tiles[0].getpixel((i, 0)) for i in range(TILE)]
    grass_v = [grass_tiles[0].getpixel((0, i)) for i in range(TILE)]
    dirt_boundary = dirt.crop((0, 0, TILE, TILE))
    dirt_h = [dirt_boundary.getpixel((i, TILE // 2)) for i in range(TILE)]
    dirt_v = [dirt_boundary.getpixel((TILE // 2, i)) for i in range(TILE)]
    grass_edge = grass_h[0]

    entries = []
    tiles = []
    for bits in MASKS:
        for variation in range(VARIATIONS):
            seed = bits * 7919 + variation * 104729
            rng = random.Random(seed)
            base = grass_tiles[(bits + variation * 7) % len(grass_tiles)].copy()
            texture = crop_wrapped(dirt, rng.randrange(dirt.width), rng.randrange(dirt.height), TILE)
            mask = road_mask(bits, variation)
            tile = Image.composite(texture, base, mask)
            add_micro_details(tile, mask, seed)
            enforce_boundary(tile, bits, grass_h, grass_v, dirt_h, dirt_v)
            index = len(tiles)
            tiles.append(tile)
            entries.append({
                "id": index,
                "name": NAMES[bits],
                "variation": variation,
                "connections": bits,
                "north": bool(bits & 1),
                "east": bool(bits & 2),
                "south": bool(bits & 4),
                "west": bool(bits & 8),
                "x": (index % COLS) * TILE,
                "y": (index // COLS) * TILE,
                "width": TILE,
                "height": TILE,
            })

    rows = (len(tiles) + COLS - 1) // COLS
    atlas = Image.new("RGB", (COLS * TILE, rows * TILE), grass_edge)
    for i, tile in enumerate(tiles):
        atlas.paste(tile, ((i % COLS) * TILE, (i // COLS) * TILE))
    atlas_path = output_dir / f"dirt-road-atlas-90-{version}.png"
    atlas.save(atlas_path, optimize=True)
    atlas.resize((atlas.width * 3, atlas.height * 3), Image.Resampling.NEAREST).save(
        output_dir / f"dirt-road-atlas-90-{version}-preview.png", optimize=True
    )

    # Full dirt variations are used for the middle of roads wider than one tile.
    # Keeping them beside the edge atlas guarantees identical material/color and
    # avoids mixing in unrelated transition tiles.
    fill_atlas = Image.new("RGB", (8 * TILE, TILE))
    for variation in range(8):
        fill = crop_wrapped(dirt, variation * 47, variation * 31, TILE)
        add_micro_details(fill, Image.new("L", (TILE, TILE), 255), 9001 + variation)
        fill_atlas.paste(fill, (variation * TILE, 0))
    fill_atlas.save(output_dir / f"dirt-road-fill-8-{version}.png", optimize=True)

    hashes = [hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles]
    metadata = {
        "image": atlas_path.name,
        "tileWidth": TILE,
        "tileHeight": TILE,
        "columns": COLS,
        "rows": rows,
        "tileCount": len(tiles),
        "variationsPerShape": VARIATIONS,
        "uniqueTileCount": len(set(hashes)),
        "margin": 0,
        "spacing": 0,
        "connectionBits": {"north": 1, "east": 2, "south": 4, "west": 8},
        "tiles": entries,
    }
    (output_dir / f"dirt-road-atlas-90-{version}.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({k: metadata[k] for k in metadata if k != "tiles"}))


if __name__ == "__main__":
    main()
