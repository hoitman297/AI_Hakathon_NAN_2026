from __future__ import annotations

import hashlib
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance


TILE = 32
COLS = 6
ROWS = 4


def main() -> None:
    if len(sys.argv) not in (3, 4):
        raise SystemExit("usage: build_wildflower_overlay_atlas.py TRANSPARENT_SOURCE OUTPUT_DIR [PREFIX]")
    source_path = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    prefix = sys.argv[3] if len(sys.argv) == 4 else "wildflowers-overlay"
    output_dir.mkdir(parents=True, exist_ok=True)

    source = Image.open(source_path).convert("RGBA")
    tiles: list[Image.Image] = []
    entries = []
    for row in range(ROWS):
        y0 = round(source.height * row / ROWS)
        y1 = round(source.height * (row + 1) / ROWS)
        for col in range(COLS):
            x0 = round(source.width * col / COLS)
            x1 = round(source.width * (col + 1) / COLS)
            tile = source.crop((x0, y0, x1, y1)).resize((TILE, TILE), Image.Resampling.LANCZOS)
            if prefix == "weeds-overlay":
                alpha_before = tile.getchannel("A")
                tile = ImageEnhance.Color(tile).enhance(1.25)
                tile = ImageEnhance.Brightness(tile).enhance(1.35)
                tile.putalpha(alpha_before.point(lambda value: min(255, int(value * 1.45))))
            alpha = tile.getchannel("A")
            # Keep a guaranteed transparent perimeter for arbitrary overlay placement.
            alpha_draw = ImageDraw.Draw(alpha)
            alpha_draw.rectangle((0, 0, TILE - 1, TILE - 1), outline=0, width=2)
            tile.putalpha(alpha)
            index = len(tiles)
            tiles.append(tile)
            bbox = alpha.getbbox()
            entries.append({
                "id": index,
                "x": col * TILE,
                "y": row * TILE,
                "width": TILE,
                "height": TILE,
                "empty": bbox is None,
                "contentBounds": list(bbox) if bbox else None,
            })

    atlas = Image.new("RGBA", (COLS * TILE, ROWS * TILE), (0, 0, 0, 0))
    for index, tile in enumerate(tiles):
        atlas.paste(tile, ((index % COLS) * TILE, (index // COLS) * TILE), tile)
    atlas_path = output_dir / f"{prefix}-atlas-24-v1.png"
    atlas.save(atlas_path, optimize=True)

    # A checkerboard preview keeps the shipped atlas fully transparent while making
    # tiny overlay details visible during review.
    checker = Image.new("RGB", atlas.size, (73, 95, 48))
    draw = ImageDraw.Draw(checker)
    for y in range(0, atlas.height, 8):
        for x in range(0, atlas.width, 8):
            if (x // 8 + y // 8) % 2:
                draw.rectangle((x, y, x + 7, y + 7), fill=(84, 108, 55))
    checker.paste(atlas, (0, 0), atlas)
    checker.resize((atlas.width * 4, atlas.height * 4), Image.Resampling.NEAREST).save(
        output_dir / f"{prefix}-atlas-24-v1-preview.png", optimize=True
    )

    hashes = [hashlib.sha256(tile.tobytes()).hexdigest() for tile in tiles]
    edge_clear = all(
        tile.getchannel("A").crop((0, 0, TILE, 2)).getbbox() is None
        and tile.getchannel("A").crop((0, TILE - 2, TILE, TILE)).getbbox() is None
        and tile.getchannel("A").crop((0, 0, 2, TILE)).getbbox() is None
        and tile.getchannel("A").crop((TILE - 2, 0, TILE, TILE)).getbbox() is None
        for tile in tiles
    )
    metadata = {
        "image": atlas_path.name,
        "tileWidth": TILE,
        "tileHeight": TILE,
        "columns": COLS,
        "rows": ROWS,
        "tileCount": len(tiles),
        "uniqueTileCount": len(set(hashes)),
        "transparent": True,
        "transparentPerimeterPixels": 2,
        "allTileEdgesTransparent": edge_clear,
        "margin": 0,
        "spacing": 0,
        "tiles": entries,
    }
    (output_dir / f"{prefix}-atlas-24-v1.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: value for key, value in metadata.items() if key != "tiles"}))


if __name__ == "__main__":
    main()
