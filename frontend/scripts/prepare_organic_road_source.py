from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image


GRID = 8
TILE = 32


def bounds(length: int, index: int) -> tuple[int, int]:
    """Return rounded cell bounds for an AI atlas whose size is not grid-even."""
    return round(index * length / GRID), round((index + 1) * length / GRID)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: prepare_organic_road_source.py SOURCE OUTPUT_DIR")

    source_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)

    source = Image.open(source_path).convert("RGB")
    tiles: list[Image.Image] = []
    entries = []

    for row in range(GRID):
        for column in range(GRID):
            x0, x1 = bounds(source.width, column)
            y0, y1 = bounds(source.height, row)
            # The generated atlas contains a thin decorative grid. Removing two
            # source pixels on every side keeps it out of the gameplay tiles.
            cell = source.crop((x0 + 2, y0 + 2, x1 - 2, y1 - 2))
            tile = cell.resize((TILE, TILE), Image.Resampling.LANCZOS)
            tiles.append(tile)
            entries.append({
                "id": len(tiles) - 1,
                "sourceRow": row,
                "sourceColumn": column,
                "x": column * TILE,
                "y": row * TILE,
                "width": TILE,
                "height": TILE,
            })

    atlas = Image.new("RGB", (GRID * TILE, GRID * TILE))
    for index, tile in enumerate(tiles):
        atlas.paste(tile, ((index % GRID) * TILE, (index // GRID) * TILE))

    atlas_path = output_dir / "organic-dirt-road-edge-atlas-64-32px-v2.png"
    atlas.save(atlas_path, optimize=True)
    atlas.resize((atlas.width * 3, atlas.height * 3), Image.Resampling.NEAREST).save(
        output_dir / "organic-dirt-road-edge-atlas-64-32px-v2-preview.png", optimize=True
    )

    # Extract only the central worn-dirt band from the eight horizontal samples.
    # This becomes the visual material used by the connection-safe road builder.
    material = Image.new("RGB", (4 * 64, 2 * 64))
    fill_tiles: list[Image.Image] = []
    for slot in range(GRID):
        # The first six cells are clean horizontal-road samples. Repeat them to
        # fill the material sheet and avoid the upward bend in the final cells.
        column = slot % 6
        x0, x1 = bounds(source.width, column)
        y0, y1 = bounds(source.height, 0)
        cell = source.crop((x0 + 3, y0 + 3, x1 - 3, y1 - 3))
        # Preserve a square patch from the path center. Stretching a thin strip
        # produced vertical wood-grain artifacts in the rendered game map.
        patch_size = min(40, cell.width, cell.height)
        left = (cell.width - patch_size) // 2
        # In the first-row source tiles, the pure dirt band sits slightly below
        # vertical center. This range excludes both grassy borders.
        top = min(int(cell.height * 0.46), cell.height - patch_size)
        dirt_patch = cell.crop((left, top, left + patch_size, top + patch_size))
        patch = dirt_patch.resize((64, 64), Image.Resampling.LANCZOS)
        material.paste(patch, ((slot % 4) * 64, (slot // 4) * 64))
        fill_tiles.append(dirt_patch.resize((TILE, TILE), Image.Resampling.LANCZOS))
    material_path = output_dir / "organic-dirt-road-material-v2.png"
    material.save(material_path, optimize=True)

    fill_atlas = Image.new("RGB", (GRID * TILE, TILE))
    for index, tile in enumerate(fill_tiles):
        fill_atlas.paste(tile, (index * TILE, 0))
    fill_atlas.save(output_dir / "organic-dirt-road-fill-8-v2.png", optimize=True)

    metadata = {
        "image": atlas_path.name,
        "tileWidth": TILE,
        "tileHeight": TILE,
        "columns": GRID,
        "rows": GRID,
        "tileCount": len(tiles),
        "margin": 0,
        "spacing": 0,
        "sourceImage": source_path.name,
        "tiles": entries,
    }
    (output_dir / "organic-dirt-road-edge-atlas-64-32px-v2.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: value for key, value in metadata.items() if key != "tiles"}))


if __name__ == "__main__":
    main()
