from __future__ import annotations

import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw


TILE = 32
WIDTH = 96
HEIGHT = 72
SEED = 712026


def load_tiles(path: Path, columns: int, count: int) -> list[Image.Image]:
    atlas = Image.open(path).convert("RGBA")
    return [
        atlas.crop(((i % columns) * TILE, (i // columns) * TILE, (i % columns + 1) * TILE, (i // columns + 1) * TILE))
        for i in range(count)
    ]


def path(waypoints: list[tuple[int, int]], seed: int) -> set[tuple[int, int]]:
    rng = random.Random(seed)
    cells = {waypoints[0]}
    x, y = waypoints[0]
    for tx, ty in waypoints[1:]:
        last_axis = ""
        run = 0
        while (x, y) != (tx, ty):
            choices = []
            if x != tx:
                choices.extend(["x"] * max(1, abs(tx - x)))
            if y != ty:
                choices.extend(["y"] * max(1, abs(ty - y)))
            axis = rng.choice(choices)
            if axis == last_axis and run >= rng.choice((2, 3, 4)) and len(set(choices)) > 1:
                axis = "y" if axis == "x" else "x"
            if axis == "x" and x != tx:
                x += 1 if tx > x else -1
            elif y != ty:
                y += 1 if ty > y else -1
                axis = "y"
            if axis == last_axis:
                run += 1
            else:
                last_axis, run = axis, 1
            cells.add((x, y))
    return cells


def polygon_cells(points: list[tuple[int, int]]) -> set[tuple[int, int]]:
    mask = Image.new("1", (WIDTH, HEIGHT), 0)
    ImageDraw.Draw(mask).polygon(points, fill=1)
    pixels = mask.load()
    return {(x, y) for y in range(HEIGHT) for x in range(WIDTH) if pixels[x, y]}


def bits(cells: set[tuple[int, int]], x: int, y: int) -> int:
    return (
        (1 if (x, y - 1) in cells else 0)
        | (2 if (x + 1, y) in cells else 0)
        | (4 if (x, y + 1) in cells else 0)
        | (8 if (x - 1, y) in cells else 0)
    )


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: assemble_first_playable_chunk.py TILESETS OUTPUT")
    tilesets = Path(sys.argv[1])
    output = Path(sys.argv[2])
    output.parent.mkdir(parents=True, exist_ok=True)

    grass = load_tiles(tilesets / "grass-atlas-24-v1.png", 6, 24)
    roads = load_tiles(tilesets / "dirt-road-atlas-90-v1.png", 10, 90)
    rivers = load_tiles(tilesets / "river-atlas-80-v1.png", 10, 80)
    transitions = load_tiles(tilesets / "terrain-transitions-256-v1.png", 16, 256)
    rng = random.Random(SEED)

    # Hand-authored major route: a relaxed west-to-south curve with three short
    # branches serving future residential and agricultural spaces.
    road = path([(0, 39), (10, 38), (20, 35), (31, 35), (41, 39), (49, 43), (58, 49), (66, 57), (72, 71)], 101)
    road |= path([(27, 35), (27, 29), (24, 24), (22, 22)], 102)
    road |= path([(47, 42), (55, 39), (63, 37), (68, 37)], 103)
    road |= path([(59, 50), (67, 49), (76, 50), (84, 53)], 104)

    # A small stream occupies the north-east corner and continues into neighboring
    # chunks through the north and east borders without crossing the road.
    river = path([(82, 0), (81, 7), (82, 13), (86, 18), (91, 20), (95, 21)], 201)

    # Irregular, hand-shaped fields with distinct silhouettes.
    field_one = polygon_cells([(7, 13), (13, 10), (22, 10), (29, 15), (28, 22), (24, 27), (16, 29), (9, 25), (6, 20)])
    field_two = polygon_cells([(66, 28), (73, 26), (82, 27), (88, 32), (88, 39), (83, 46), (75, 48), (68, 44), (65, 37)])
    farmland = (field_one | field_two) - road - river

    canvas = Image.new("RGBA", (WIDTH * TILE, HEIGHT * TILE))
    previous = [-1] * WIDTH
    for y in range(HEIGHT):
        current = []
        for x in range(WIDTH):
            forbidden = {previous[x]}
            if current:
                forbidden.add(current[-1])
            choices = [index for index in range(24) if index not in forbidden]
            tile_id = rng.choice(choices)
            current.append(tile_id)
            canvas.paste(grass[tile_id], (x * TILE, y * TILE))
        previous = current

    # Grass-to-farmland atlas group begins at tile 64.
    for x, y in sorted(farmland, key=lambda p: (p[1], p[0])):
        mask = bits(farmland, x, y)
        variation = (x * 7 + y * 11 + SEED) % 4
        tile_id = 64 + mask * 4 + variation
        canvas.paste(transitions[tile_id], (x * TILE, y * TILE))

    road_masks = [1, 2, 4, 8, 5, 10, 3, 6, 12, 9, 7, 14, 13, 11, 15]
    road_lookup = {mask: list(range(index * 6, index * 6 + 6)) for index, mask in enumerate(road_masks)}
    for x, y in sorted(road, key=lambda p: (p[1], p[0])):
        mask = bits(road, x, y)
        candidates = road_lookup[mask]
        tile_id = candidates[(x * 13 + y * 17 + SEED) % len(candidates)]
        canvas.paste(roads[tile_id], (x * TILE, y * TILE))

    river_families = [
        (5, "straight"), (10, "straight"), (3, "inner"), (6, "inner"),
        (12, "inner"), (9, "inner"), (3, "bend"), (6, "bend"),
        (12, "bend"), (9, "bend"), (14, "outer"), (13, "outer"),
        (11, "outer"), (7, "outer"), (7, "t"), (14, "t"),
        (13, "t"), (11, "t"), (15, "cross"), (15, "open"),
    ]
    river_lookup: dict[int, list[int]] = {}
    for index, (mask, family) in enumerate(river_families):
        if family in ("straight", "bend"):
            river_lookup.setdefault(mask, []).extend(range(index * 4, index * 4 + 4))
    for x, y in sorted(river, key=lambda p: (p[1], p[0])):
        mask = bits(river, x, y)
        if mask in (1, 4):
            candidates = river_lookup[5]
        elif mask in (2, 8):
            candidates = river_lookup[10]
        else:
            candidates = river_lookup[mask]
        tile_id = candidates[(x * 19 + y * 5 + SEED) % len(candidates)]
        canvas.paste(rivers[tile_id], (x * TILE, y * TILE))

    canvas.convert("RGB").save(output, optimize=True)
    print(f"{output} {canvas.width}x{canvas.height}")


if __name__ == "__main__":
    main()
