from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image


TILE = 32
OUTPUT_TILE = 16
WIDTH = 128
HEIGHT = 96
SEED = 20260804


def atlas_tiles(path: Path, columns: int, count: int) -> list[Image.Image]:
    atlas = Image.open(path).convert("RGBA")
    return [
        atlas.crop(((i % columns) * TILE, (i // columns) * TILE, (i % columns + 1) * TILE, (i // columns + 1) * TILE))
        for i in range(count)
    ]


def supplied_grid_tiles(path: Path, columns: int, rows: int) -> list[Image.Image]:
    """Slice supplied artwork without repainting, masking, or texture synthesis."""
    atlas = Image.open(path).convert("RGBA")
    tiles: list[Image.Image] = []
    for row in range(rows):
        for column in range(columns):
            x0 = round(column * atlas.width / columns)
            x1 = round((column + 1) * atlas.width / columns)
            y0 = round(row * atlas.height / rows)
            y1 = round((row + 1) * atlas.height / rows)
            tile = atlas.crop((x0, y0, x1, y1))
            tiles.append(tile.resize((TILE, TILE), Image.Resampling.LANCZOS))
    return tiles


def supplied_tile_connections(tile: Image.Image) -> int:
    """Read which edges contain supplied dirt pixels; never modify the tile."""
    px = tile.load()

    def is_dirt(pixel: tuple[int, ...]) -> bool:
        red, green, blue = pixel[:3]
        return red - green > 10 and red - blue > 35 and red > 105

    strips = (
        [px[x, y] for y in range(3) for x in range(TILE)],
        [px[x, y] for x in range(TILE - 3, TILE) for y in range(TILE)],
        [px[x, y] for y in range(TILE - 3, TILE) for x in range(TILE)],
        [px[x, y] for x in range(3) for y in range(TILE)],
    )
    bits = 0
    for bit, strip in zip((1, 2, 4, 8), strips):
        if sum(is_dirt(pixel) for pixel in strip) >= 5:
            bits |= bit
    return bits


def organic_path(waypoints: list[tuple[int, int]], seed: int) -> set[tuple[int, int]]:
    rng = random.Random(seed)
    cells: set[tuple[int, int]] = set()
    x, y = waypoints[0]
    cells.add((x, y))
    for tx, ty in waypoints[1:]:
        while (x, y) != (tx, ty):
            can_x = x != tx
            can_y = y != ty
            if can_x and can_y:
                move_x = rng.random() < abs(tx - x) / (abs(tx - x) + abs(ty - y))
            else:
                move_x = can_x
            if move_x:
                x += 1 if tx > x else -1
            else:
                y += 1 if ty > y else -1
            cells.add((x, y))
    return cells


def connection_bits(cells: set[tuple[int, int]], x: int, y: int) -> int:
    return (
        (1 if (x, y - 1) in cells else 0)
        | (2 if (x + 1, y) in cells else 0)
        | (4 if (x, y + 1) in cells else 0)
        | (8 if (x - 1, y) in cells else 0)
    )


def widen_road(centerline: set[tuple[int, int]]) -> set[tuple[int, int]]:
    """Expand a one-cell route into an organic two-to-three-cell road."""
    widened = set(centerline)
    for x, y in centerline:
        horizontal = (x - 1, y) in centerline or (x + 1, y) in centerline
        vertical = (x, y - 1) in centerline or (x, y + 1) in centerline
        broad_section = ((x // 7) + (y // 6) + SEED) % 4 in (0, 1)

        if horizontal and not vertical:
            widened.add((x, y + 1))
            if broad_section:
                widened.add((x, y - 1))
        elif vertical and not horizontal:
            widened.add((x + 1, y))
            if broad_section:
                widened.add((x - 1, y))
        else:
            # Corners and junctions receive a rounded 2x2 shoulder; selected
            # junctions get one extra cell so their width changes gradually.
            widened.update(((x + 1, y), (x, y + 1), (x + 1, y + 1)))
            if broad_section:
                widened.update(((x - 1, y), (x, y - 1)))

    return {(x, y) for x, y in widened if 0 <= x < WIDTH and 0 <= y < HEIGHT}


def widen_river(centerline: set[tuple[int, int]]) -> set[tuple[int, int]]:
    """Expand the stream to a variable seven-to-eleven-tile river channel."""
    widened: set[tuple[int, int]] = set()
    for x, y in centerline:
        horizontal = (x - 1, y) in centerline or (x + 1, y) in centerline
        vertical = (x, y - 1) in centerline or (x, y + 1) in centerline
        radius = 3 + (((x // 15) + (y // 11) + SEED) % 3)

        if horizontal and not vertical:
            for offset in range(-radius, radius + 1):
                widened.add((x, y + offset))
        elif vertical and not horizontal:
            for offset in range(-radius, radius + 1):
                widened.add((x + offset, y))
        else:
            # Round bends with a small disk so banks do not form square elbows.
            for dx in range(-radius, radius + 1):
                for dy in range(-radius, radius + 1):
                    if dx * dx + dy * dy <= radius * radius + radius:
                        widened.add((x + dx, y + dy))

    return {(x, y) for x, y in widened if 0 <= x < WIDTH and 0 <= y < HEIGHT}


def fill_enclosed_holes(cells: set[tuple[int, int]]) -> set[tuple[int, int]]:
    """Fill empty tile pockets completely enclosed by a terrain region."""
    outside: set[tuple[int, int]] = set()
    pending = [
        (x, y)
        for x in range(WIDTH)
        for y in range(HEIGHT)
        if (x in (0, WIDTH - 1) or y in (0, HEIGHT - 1)) and (x, y) not in cells
    ]
    while pending:
        x, y = pending.pop()
        if (x, y) in outside or (x, y) in cells:
            continue
        outside.add((x, y))
        for nx, ny in ((x, y - 1), (x + 1, y), (x, y + 1), (x - 1, y)):
            if 0 <= nx < WIDTH and 0 <= ny < HEIGHT and (nx, ny) not in outside:
                pending.append((nx, ny))
    holes = {
        (x, y)
        for x in range(WIDTH)
        for y in range(HEIGHT)
        if (x, y) not in cells and (x, y) not in outside
    }
    return cells | holes


def choose_different(rng: random.Random, count: int, left: int | None, up: int | None) -> int:
    candidates = [i for i in range(count) if i != left and i != up]
    return rng.choice(candidates)


def ellipse_cells(cx: int, cy: int, rx: int, ry: int, wobble_seed: int) -> set[tuple[int, int]]:
    rng = random.Random(wobble_seed)
    cells = set()
    for y in range(cy - ry - 1, cy + ry + 2):
        for x in range(cx - rx - 1, cx + rx + 2):
            wobble = rng.uniform(-0.09, 0.09)
            if ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1 + wobble:
                if 0 <= x < WIDTH and 0 <= y < HEIGHT:
                    cells.add((x, y))
    return cells


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: assemble_terrain_map.py TILESET_DIR OUTPUT_DIR")
    tileset_dir = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)
    rng = random.Random(SEED)

    grass = atlas_tiles(tileset_dir / "grass-atlas-24-v1.png", 6, 24)
    roads = atlas_tiles(tileset_dir / "organic-dirt-road-only-atlas-64-32px-v1.png", 8, 64)
    rivers = atlas_tiles(tileset_dir / "river-atlas-80-v1.png", 10, 80)
    river_center_source = Image.open(tileset_dir / "river-pebble-continuous-source-v3-small.png").convert("RGBA")
    transitions = atlas_tiles(tileset_dir / "terrain-transitions-256-v1.png", 16, 256)
    overlays = {
        "wildflowers": atlas_tiles(tileset_dir / "wildflowers-overlay-atlas-24-v1.png", 6, 24),
        "weeds": atlas_tiles(tileset_dir / "weeds-overlay-atlas-24-v1.png", 6, 24),
        "pebbles": atlas_tiles(tileset_dir / "pebbles-overlay-atlas-24-v1.png", 6, 24),
        "forest-details": atlas_tiles(tileset_dir / "forest-details-overlay-atlas-24-v1.png", 6, 24),
    }

    # Roads are intentionally disabled for the current compact village layout.
    road_cells: set[tuple[int, int]] = set()
    secondary_path_count = 0

    # A single stream now crosses the middle of the chunk with broad, readable
    # bends instead of occupying isolated corners or following a straight channel.
    river_centerline = organic_path(
        [(0, 58), (14, 55), (27, 60), (41, 54), (55, 59), (69, 53),
         (83, 58), (98, 52), (112, 56), (127, 54)],
        27,
    )
    river_cells = river_centerline
    river_cells = fill_enclosed_holes(widen_river(river_cells))
    bridge_y_candidates = [y for x, y in river_centerline if 59 <= x <= 63]
    bridge_y = round(sum(bridge_y_candidates) / len(bridge_y_candidates)) if bridge_y_candidates else 48
    bridge_cells = {
        (x, y)
        for x in range(59, 64)
        for y in range(bridge_y - 7, bridge_y + 8)
        if (x, y) in river_cells
    }

    # A larger eastern field remains separated from the river by a two-tile grass
    # buffer, so the riverbank never appears glued directly to cultivated soil.
    river_buffer = {
        (x + dx, y + dy)
        for x, y in river_cells
        for dx in range(-2, 3)
        for dy in range(-2, 3)
        if 0 <= x + dx < WIDTH and 0 <= y + dy < HEIGHT
    }
    road_buffer = {
        (x + dx, y + dy)
        for x, y in road_cells
        for dx in range(-1, 2)
        for dy in range(-1, 2)
        if 0 <= x + dx < WIDTH and 0 <= y + dy < HEIGHT
    }
    farm_cells = ellipse_cells(96, 34, 17, 12, 31)
    farm_cells -= road_buffer | river_buffer

    map_image = Image.new("RGBA", (WIDTH * TILE, HEIGHT * TILE))
    grass_ids: list[int] = []
    farmland_sparse = []
    road_sparse = []
    river_sparse = []
    overlay_sparse = []

    previous_row: list[int] = [-1] * WIDTH
    for y in range(HEIGHT):
        current_row: list[int] = []
        for x in range(WIDTH):
            left = current_row[-1] if current_row else None
            up = previous_row[x] if y else None
            grass_id = choose_different(rng, 24, left, up)
            current_row.append(grass_id)
            grass_ids.append(grass_id)
            map_image.paste(grass[grass_id], (x * TILE, y * TILE))
        previous_row = current_row

    # Farmland uses the supplied grass-to-farmland transition group (offset 64).
    for x, y in sorted(farm_cells, key=lambda p: (p[1], p[0])):
        bits = connection_bits(farm_cells, x, y)
        variation = (x * 13 + y * 7 + SEED) % 4
        tile_id = 64 + bits * 4 + variation
        map_image.paste(transitions[tile_id], (x * TILE, y * TILE))
        farmland_sparse.append({"x": x, "y": y, "tile": tile_id, "connections": bits})

    # Build a connection lookup by reading the actual supplied tile edges. The
    # artwork remains unchanged; this only decides where each piece is placed.
    supplied_road_by_bits: dict[int, list[int]] = {}
    for tile_id, tile in enumerate(roads):
        bits = supplied_tile_connections(tile)
        if bits:
            supplied_road_by_bits.setdefault(bits, []).append(tile_id)

    # Exact connection groups from the existing 80-tile atlas. Corner/shoreline
    # masks mix both supplied families; open water uses only IDs 76-79 so no bank
    # fragment can appear as a hole in the river center.
    river_by_bits: dict[int, list[int]] = {
        5: list(range(0, 4)),
        10: list(range(4, 8)),
        3: list(range(8, 12)) + list(range(24, 28)),
        6: list(range(12, 16)) + list(range(28, 32)),
        12: list(range(16, 20)) + list(range(32, 36)),
        9: list(range(20, 24)) + list(range(36, 40)),
        14: list(range(40, 44)) + list(range(60, 64)),
        13: list(range(44, 48)) + list(range(64, 68)),
        11: list(range(48, 52)) + list(range(68, 72)),
        7: list(range(52, 60)),
        15: list(range(76, 80)),
    }
    chosen_river_tiles: dict[tuple[int, int], int] = {}

    for x, y in sorted(river_cells, key=lambda p: (p[1], p[0])):
        bits = connection_bits(river_cells, x, y)
        if bits == 15:
            # Sample by world position from one continuous generated surface.
            # Adjacent 32px cells therefore share their source edges and read as
            # a single broad riverbed rather than repeated individual tiles.
            source_x = (x * TILE) % river_center_source.width
            source_y = (y * TILE) % river_center_source.height
            center_tile = river_center_source.crop((source_x, source_y, source_x + TILE, source_y + TILE))
            map_image.paste(center_tile, (x * TILE, y * TILE))
            river_sparse.append({"x": x, "y": y, "tile": -1, "atlas": "continuous-center", "connections": bits})
            continue

        candidates = river_by_bits.get(bits)
        if not candidates:
            # Open edge endpoints use the closest straight/bend family while the
            # actual exit still reaches the chunk boundary.
            fallback = 5 if bits in (1, 4) else 10
            candidates = river_by_bits[fallback]
        # A nonlinear position hash breaks the former diagonal cycle. Avoid the
        # immediate left/up choice as well, reducing visible checker repetition.
        index = ((x * 73856093) ^ (y * 19349663) ^ SEED) % len(candidates)
        tile_id = candidates[index]
        forbidden = {
            chosen_river_tiles.get((x - 1, y)),
            chosen_river_tiles.get((x, y - 1)),
        }
        for offset in range(1, len(candidates)):
            if tile_id not in forbidden:
                break
            tile_id = candidates[(index + offset) % len(candidates)]
        chosen_river_tiles[(x, y)] = tile_id
        map_image.paste(rivers[tile_id], (x * TILE, y * TILE))
        river_sparse.append({"x": x, "y": y, "tile": tile_id, "connections": bits})

    for x, y in sorted(road_cells, key=lambda p: (p[1], p[0])):
        bits = connection_bits(road_cells, x, y)
        candidates = supplied_road_by_bits.get(bits)
        if not candidates:
            # If the supplied sheet lacks one exact topology, choose the piece
            # with the fewest mismatched edge connections. No rotation/redraw.
            closest_bits = min(
                supplied_road_by_bits,
                key=lambda candidate: (bits ^ candidate).bit_count(),
            )
            candidates = supplied_road_by_bits[closest_bits]
        tile_id = candidates[(x * 19 + y * 5 + SEED) % len(candidates)]
        # The supplied road artwork occupies only about one third of a 32px tile,
        # which is too narrow beside the 48px player. Scale the existing pixels
        # 2x with nearest-neighbor and overlap adjacent pieces at their centers.
        # No new road texture or painted terrain is introduced.
        road_scale = 2
        road_size = TILE * road_scale
        road_tile = roads[tile_id].resize((road_size, road_size), Image.Resampling.NEAREST)
        road_offset = TILE // 2 - road_size // 2
        map_image.alpha_composite(road_tile, dest=(x * TILE + road_offset, y * TILE + road_offset))
        road_sparse.append({"x": x, "y": y, "tile": tile_id, "atlas": "supplied-road", "connections": bits})

    # Sparse environmental overlays only occupy undecorated grass cells.
    blocked = road_cells | river_cells | farm_cells
    overlay_rates = (("weeds", 0.055), ("wildflowers", 0.022), ("pebbles", 0.018), ("forest-details", 0.015))
    for y in range(HEIGHT):
        for x in range(WIDTH):
            if (x, y) in blocked:
                continue
            roll = rng.random()
            running = 0.0
            for name, rate in overlay_rates:
                running += rate
                if roll < running:
                    tile_id = rng.randrange(24)
                    tile = overlays[name][tile_id]
                    map_image.alpha_composite(tile, (x * TILE, y * TILE))
                    overlay_sparse.append({"x": x, "y": y, "atlas": name, "tile": tile_id})
                    break

    image_path = output_dir / "korean-countryside-chunk-01.png"
    output_image = map_image.resize(
        (WIDTH * OUTPUT_TILE, HEIGHT * OUTPUT_TILE), Image.Resampling.NEAREST
    )
    output_image.convert("RGB").save(image_path, optimize=True)
    output_image.resize((1024, 768), Image.Resampling.NEAREST).convert("RGB").save(
        output_dir / "korean-countryside-chunk-01-preview.png", optimize=True
    )

    data = {
        "name": "korean-countryside-chunk-01",
        "version": 1,
        "seed": SEED,
        "tileWidth": OUTPUT_TILE,
        "tileHeight": OUTPUT_TILE,
        "width": WIDTH,
        "height": HEIGHT,
        "pixelWidth": WIDTH * OUTPUT_TILE,
        "pixelHeight": HEIGHT * OUTPUT_TILE,
        "renderedImage": image_path.name,
        "spawn": {"x": 7, "y": 20},
        "camera": {"followPlayer": True, "viewport": {"width": 1280, "height": 720}},
        "zones": [
            {
                "id": row * 3 + column + 1,
                "column": column,
                "row": row,
                "x": [0, 43, 86][column],
                "y": [0, 32, 64][row],
                "width": [43, 43, 42][column],
                "height": 32,
                "name": "마을회관 구역" if row == 0 and column == 0 else f"구역 {row * 3 + column + 1}",
            }
            for row in range(3)
            for column in range(3)
        ],
        "bridge": {"tileX": 61, "tileY": bridge_y, "crossingTiles": len(bridge_cells)},
        "roadGeneration": {"mainPathCount": 0, "secondaryPathCount": secondary_path_count},
        "atlases": {
            "grass": "../tilesets/grass-atlas-24-v1.png",
            "farmlandTransitions": "../tilesets/terrain-transitions-256-v1.png",
            "road": "../tilesets/organic-dirt-road-only-atlas-64-32px-v1.png",
            "river": "../tilesets/river-atlas-80-v1.png",
            "riverCenter": "../tilesets/river-pebble-continuous-source-v3-small.png",
            "wildflowers": "../tilesets/wildflowers-overlay-atlas-24-v1.png",
            "weeds": "../tilesets/weeds-overlay-atlas-24-v1.png",
            "pebbles": "../tilesets/pebbles-overlay-atlas-24-v1.png",
            "forest-details": "../tilesets/forest-details-overlay-atlas-24-v1.png",
        },
        "layers": {
            "grass": grass_ids,
            "farmland": farmland_sparse,
            "river": river_sparse,
            "road": road_sparse,
            "overlays": overlay_sparse,
        },
        "exits": {
            "road": {"west": None, "east": None, "north": None},
            "river": {"west": [0, 58], "east": [127, 54]},
        },
        "reservedOpenAreas": [
            {"name": "northwest-residential", "x": 7, "y": 5, "width": 20, "height": 15},
            {"name": "central-village", "x": 42, "y": 30, "width": 24, "height": 15},
            {"name": "southwest-residential", "x": 10, "y": 65, "width": 26, "height": 18},
            {"name": "southeast-residential", "x": 78, "y": 66, "width": 28, "height": 17},
        ],
        "collision": {
            "blockedTiles": [
                {"x": x, "y": y} for x, y in sorted(river_cells - bridge_cells)
            ]
        },
    }
    (output_dir / "korean-countryside-chunk-01.json").write_text(
        json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8"
    )
    print(json.dumps({
        "image": image_path.name,
        "size": [WIDTH, HEIGHT],
        "pixels": [WIDTH * OUTPUT_TILE, HEIGHT * OUTPUT_TILE],
        "roadTiles": len(road_cells),
        "riverTiles": len(river_cells),
        "farmlandTiles": len(farm_cells),
        "overlayTiles": len(overlay_sparse),
    }))


if __name__ == "__main__":
    main()
