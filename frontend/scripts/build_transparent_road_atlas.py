from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image


GRID = 8
TILE = 32


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: build_transparent_road_atlas.py SOURCE OUTPUT_DIR")

    source_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)
    source = Image.open(source_path).convert("RGBA")
    atlas = Image.new("RGBA", (GRID * TILE, GRID * TILE), (0, 0, 0, 0))

    for row in range(GRID):
        for column in range(GRID):
            x0 = round(column * source.width / GRID)
            x1 = round((column + 1) * source.width / GRID)
            y0 = round(row * source.height / GRID)
            y1 = round((row + 1) * source.height / GRID)
            # Exclude only the generated atlas separator. The road artwork and
            # its alpha edge are otherwise copied without painting new pixels.
            tile = source.crop((x0 + 2, y0 + 2, x1 - 2, y1 - 2))
            tile = tile.resize((TILE, TILE), Image.Resampling.LANCZOS)
            atlas.alpha_composite(tile, (column * TILE, row * TILE))

    output_path = output_dir / "organic-dirt-road-only-atlas-64-32px-v1.png"
    atlas.save(output_path, optimize=True)
    metadata = {
        "image": output_path.name,
        "tileWidth": TILE,
        "tileHeight": TILE,
        "columns": GRID,
        "rows": GRID,
        "tileCount": GRID * GRID,
        "margin": 0,
        "spacing": 0,
        "transparent": True,
        "source": source_path.name,
    }
    output_path.with_suffix(".json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(metadata))


if __name__ == "__main__":
    main()
