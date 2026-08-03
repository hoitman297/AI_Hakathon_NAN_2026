from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\background-assets\objects")


def split(source_name, columns, rows, output_names):
    image = Image.open(ROOT / source_name).convert("RGBA")
    cell_w = image.width // columns
    cell_h = image.height // rows
    pieces = []
    for row in range(rows):
        for col in range(columns):
            left = col * cell_w
            top = row * cell_h
            right = image.width if col == columns - 1 else (col + 1) * cell_w
            bottom = image.height if row == rows - 1 else (row + 1) * cell_h
            cell = image.crop((left, top, right, bottom))
            bbox = cell.getchannel("A").getbbox()
            if bbox is None:
                raise RuntimeError(f"Empty cell {row},{col} in {source_name}")
            pieces.append(cell.crop(bbox))

    if len(pieces) != len(output_names):
        raise RuntimeError("Output name count does not match grid")

    for piece, name in zip(pieces, output_names):
        canvas = Image.new("RGBA", (piece.width + 64, piece.height + 64), (0, 0, 0, 0))
        canvas.alpha_composite(piece, (32, 32))
        canvas.save(ROOT / name, optimize=True)


split(
    "chicken-direction-sheet.png", 4, 1,
    ["chicken-front.png", "chicken-left.png", "chicken-back.png", "chicken-right.png"],
)
split(
    "wood-fence-state-sheet.png", 2, 1,
    ["wood-fence-intact.png", "wood-fence-broken.png"],
)
split(
    "rural-props-sheet.png", 2, 2,
    ["onggi-jars.png", "stone-well.png", "wooden-pyeongsang.png", "scarecrow.png"],
)

print("Object sheets split into 10 transparent PNG files")
