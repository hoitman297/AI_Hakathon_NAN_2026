from __future__ import annotations

import hashlib
import json
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


COLS = 6
ROWS = 4
TILE = 32


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_grass_atlas.py SOURCE OUTPUT_DIR")

    source_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)

    source = Image.open(source_path).convert("RGB")
    source_copy = output_dir / "grass-atlas-24-source-v1.png"
    shutil.copy2(source_path, source_copy)

    # The generated sheet is logically 6x4. Each region is reduced separately so
    # every gameplay cell lands on an exact 32x32 pixel boundary.
    tiles: list[Image.Image] = []
    for row in range(ROWS):
        y0 = round(source.height * row / ROWS)
        y1 = round(source.height * (row + 1) / ROWS)
        for col in range(COLS):
            x0 = round(source.width * col / COLS)
            x1 = round(source.width * (col + 1) / COLS)
            tile = source.crop((x0, y0, x1, y1)).resize(
                (TILE, TILE), Image.Resampling.BOX
            )
            tiles.append(tile)

    # Give every tile a shared one-pixel perimeter. This makes every possible
    # left/right and top/bottom pairing mathematically compatible in a random map.
    base = tiles[0]
    horizontal = [base.getpixel((x, 0)) for x in range(TILE)]
    vertical = [base.getpixel((0, y)) for y in range(TILE)]
    corner = tuple(sum(values) // len(values) for values in zip(*(
        horizontal[0], horizontal[-1], vertical[0], vertical[-1]
    )))
    horizontal[0] = horizontal[-1] = corner
    vertical[0] = vertical[-1] = corner

    for tile in tiles:
        px = tile.load()
        for x, color in enumerate(horizontal):
            px[x, 0] = color
            px[x, TILE - 1] = color
        for y, color in enumerate(vertical):
            px[0, y] = color
            px[TILE - 1, y] = color

    atlas = Image.new("RGB", (COLS * TILE, ROWS * TILE))
    for index, tile in enumerate(tiles):
        atlas.paste(tile, ((index % COLS) * TILE, (index // COLS) * TILE))

    atlas_path = output_dir / "grass-atlas-24-v1.png"
    atlas.save(atlas_path, optimize=True)

    preview_path = output_dir / "grass-atlas-24-v1-preview.png"
    atlas.resize((atlas.width * 4, atlas.height * 4), Image.Resampling.NEAREST).save(
        preview_path, optimize=True
    )

    hashes = [hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles]
    edge_checks = []
    for tile in tiles:
        edge_checks.append(
            ImageStat.Stat(ImageChops.difference(
                tile.crop((0, 0, 1, TILE)), tile.crop((TILE - 1, 0, TILE, TILE))
            )).sum == [0.0, 0.0, 0.0]
            and ImageStat.Stat(ImageChops.difference(
                tile.crop((0, 0, TILE, 1)), tile.crop((0, TILE - 1, TILE, TILE))
            )).sum == [0.0, 0.0, 0.0]
        )

    metadata = {
        "image": "grass-atlas-24-v1.png",
        "tileWidth": TILE,
        "tileHeight": TILE,
        "columns": COLS,
        "rows": ROWS,
        "tileCount": len(tiles),
        "margin": 0,
        "spacing": 0,
        "uniqueTileCount": len(set(hashes)),
        "arbitraryNeighborEdgesMatch": all(edge_checks),
        "tiles": [
            {
                "id": index,
                "x": (index % COLS) * TILE,
                "y": (index // COLS) * TILE,
                "width": TILE,
                "height": TILE,
            }
            for index in range(len(tiles))
        ],
    }
    (output_dir / "grass-atlas-24-v1.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(metadata, ensure_ascii=False))


if __name__ == "__main__":
    main()
