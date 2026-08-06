from __future__ import annotations

import hashlib
import json
import random
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter


TILE = 32
VARIATIONS = 4
COLS = 10

FAMILIES = [
    ("straight-ns", 5, "straight"), ("straight-ew", 10, "straight"),
    ("inner-ne", 3, "inner"), ("inner-es", 6, "inner"),
    ("inner-sw", 12, "inner"), ("inner-wn", 9, "inner"),
    ("bend-ne", 3, "bend"), ("bend-es", 6, "bend"),
    ("bend-sw", 12, "bend"), ("bend-wn", 9, "bend"),
    ("outer-n", 14, "outer"), ("outer-e", 13, "outer"),
    ("outer-s", 11, "outer"), ("outer-w", 7, "outer"),
    ("t-nes", 7, "t"), ("t-esw", 14, "t"),
    ("t-swn", 13, "t"), ("t-wne", 11, "t"),
    ("cross", 15, "cross"),
    ("open-water", 15, "open"),
]


def wrapped_crop(source: Image.Image, x: int, y: int) -> Image.Image:
    out = Image.new("RGB", (TILE, TILE))
    src = source.load()
    dst = out.load()
    for oy in range(TILE):
        for ox in range(TILE):
            dst[ox, oy] = src[(x + ox) % source.width, (y + oy) % source.height]
    return out


def make_mask(bits: int, family: str, variation: int) -> Image.Image:
    s = 4
    size = TILE * s
    c = size // 2
    rng = random.Random(bits * 10007 + variation * 1009 + sum(map(ord, family)))
    if family == "open":
        return Image.new("L", (TILE, TILE), 255)
    width = {"inner": 14, "straight": 16, "bend": 19, "outer": 19, "t": 17, "cross": 17}[family] * s
    half = width // 2
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((c - half, c - half, c + half, c + half), fill=255)
    arms = {
        1: (c - half, 0, c + half, c),
        2: (c, c - half, size, c + half),
        4: (c - half, c, c + half, size),
        8: (0, c - half, c, c + half),
    }
    for bit, rect in arms.items():
        if bits & bit:
            d.rectangle(rect, fill=255)

    if family == "bend":
        # A broader rounded pool on bends makes these distinct from tight inner corners.
        dx = (4 if bits & 2 else -4) * s
        dy = (4 if bits & 4 else -4) * s
        d.ellipse((c + dx - half, c + dy - half, c + dx + half, c + dy + half), fill=255)
    elif family == "outer":
        # Concave shoreline indentation on the closed side, without altering the
        # standardized open edge profiles.
        missing = 15 ^ bits
        dx = 0 if missing in (1, 4) else (1 if missing == 2 else -1)
        dy = 0 if missing in (2, 8) else (1 if missing == 4 else -1)
        r = 7 * s
        x, y = c + dx * 8 * s, c + dy * 8 * s
        d.ellipse((x - r, y - r, x + r, y + r), fill=0)

    # Organic shoreline variation stays away from the outer connection zone.
    for _ in range(7 + variation):
        x, y = rng.randrange(5 * s, 27 * s), rng.randrange(5 * s, 27 * s)
        r = rng.randrange(1 * s, 3 * s + 1)
        d.ellipse((x - r, y - r, x + r, y + r), fill=rng.choice((0, 255)))

    return mask.filter(ImageFilter.GaussianBlur(1.8 * s)).resize((TILE, TILE), Image.Resampling.LANCZOS)


def enforce_edges(
    tile: Image.Image,
    bits: int,
    grass_h: list[tuple[int, int, int]], grass_v: list[tuple[int, int, int]],
    water_h: list[tuple[int, int, int]], water_v: list[tuple[int, int, int]],
) -> None:
    px = tile.load()
    for i in range(TILE):
        water = 7 <= i < 25
        px[i, 0] = water_h[i] if bits & 1 and water else grass_h[i]
        px[TILE - 1, i] = water_v[i] if bits & 2 and water else grass_v[i]
        px[i, TILE - 1] = water_h[i] if bits & 4 and water else grass_h[i]
        px[0, i] = water_v[i] if bits & 8 and water else grass_v[i]


def add_details(tile: Image.Image, mask: Image.Image, seed: int) -> None:
    rng = random.Random(seed)
    d = ImageDraw.Draw(tile)
    m = mask.load()
    for _ in range(2 + seed % 3):
        x, y = rng.randrange(3, 29), rng.randrange(3, 29)
        if m[x, y] > 220:
            color = rng.choice(((116, 160, 163), (139, 180, 181), (74, 118, 126)))
            d.line((x, y, min(x + rng.randrange(1, 4), 30), y), fill=color)
    for _ in range(3):
        x, y = rng.randrange(2, 30), rng.randrange(2, 30)
        if 45 < m[x, y] < 205:
            d.point((x, y), fill=rng.choice(((104, 96, 68), (127, 116, 82), (73, 91, 52))))


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit("usage: build_river_atlas.py WATER BANK GRASS_ATLAS OUTPUT_DIR")
    water_path, bank_path, grass_path, out_dir = map(Path, sys.argv[1:])
    out_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(water_path, out_dir / "river-water-material-source-v1.png")

    water_source = Image.open(water_path).convert("RGB")
    bank_source = Image.open(bank_path).convert("RGB")
    grass_atlas = Image.open(grass_path).convert("RGB")
    grass_tiles = [grass_atlas.crop(((i % 6) * TILE, (i // 6) * TILE, (i % 6 + 1) * TILE, (i // 6 + 1) * TILE)) for i in range(24)]
    grass_h = [grass_tiles[0].getpixel((i, 0)) for i in range(TILE)]
    grass_v = [grass_tiles[0].getpixel((0, i)) for i in range(TILE)]
    boundary_water = wrapped_crop(water_source, 0, 0)
    water_h = [boundary_water.getpixel((i, TILE // 2)) for i in range(TILE)]
    water_v = [boundary_water.getpixel((TILE // 2, i)) for i in range(TILE)]

    tiles = []
    entries = []
    for family_index, (name, bits, family) in enumerate(FAMILIES):
        for variation in range(VARIATIONS):
            seed = family_index * 65537 + variation * 8191
            rng = random.Random(seed)
            grass = grass_tiles[(family_index * 5 + variation * 7) % 24].copy()
            water = wrapped_crop(water_source, rng.randrange(water_source.width), rng.randrange(water_source.height))
            bank = wrapped_crop(bank_source, rng.randrange(bank_source.width), rng.randrange(bank_source.height))
            mask = make_mask(bits, family, variation)
            dilated = mask.filter(ImageFilter.MaxFilter(5))
            bank_ring = ImageChops.subtract(dilated, mask.filter(ImageFilter.MinFilter(3)))
            tile = Image.composite(bank, grass, bank_ring)
            tile = Image.composite(water, tile, mask)
            add_details(tile, mask, seed)
            enforce_edges(tile, bits, grass_h, grass_v, water_h, water_v)
            index = len(tiles)
            tiles.append(tile)
            entries.append({
                "id": index, "name": name, "family": family, "variation": variation,
                "connections": bits,
                "north": bool(bits & 1), "east": bool(bits & 2),
                "south": bool(bits & 4), "west": bool(bits & 8),
                "x": (index % COLS) * TILE, "y": (index // COLS) * TILE,
                "width": TILE, "height": TILE,
            })

    rows = (len(tiles) + COLS - 1) // COLS
    atlas = Image.new("RGB", (COLS * TILE, rows * TILE), grass_h[0])
    for i, tile in enumerate(tiles):
        atlas.paste(tile, ((i % COLS) * TILE, (i // COLS) * TILE))
    atlas_path = out_dir / "river-atlas-80-v1.png"
    atlas.save(atlas_path, optimize=True)
    atlas.resize((atlas.width * 3, atlas.height * 3), Image.Resampling.NEAREST).save(out_dir / "river-atlas-80-v1-preview.png", optimize=True)

    hashes = [hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles]
    metadata = {
        "image": atlas_path.name, "tileWidth": TILE, "tileHeight": TILE,
        "columns": COLS, "rows": rows, "tileCount": len(tiles),
        "variationsPerShape": VARIATIONS, "uniqueTileCount": len(set(hashes)),
        "margin": 0, "spacing": 0,
        "connectionBits": {"north": 1, "east": 2, "south": 4, "west": 8},
        "tiles": entries,
    }
    (out_dir / "river-atlas-80-v1.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in metadata.items() if k != "tiles"}))


if __name__ == "__main__":
    main()
