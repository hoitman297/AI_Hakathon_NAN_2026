from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\background-assets\buildings\houses")


def split_grid(filename, columns, rows, first_number):
    image = Image.open(ROOT / filename).convert("RGBA")
    cell_w, cell_h = image.width // columns, image.height // rows
    house_number = first_number
    for row in range(rows):
        for column in range(columns):
            bounds = (
                column * cell_w,
                row * cell_h,
                image.width if column == columns - 1 else (column + 1) * cell_w,
                image.height if row == rows - 1 else (row + 1) * cell_h,
            )
            cell = image.crop(bounds)
            bbox = cell.getchannel("A").getbbox()
            if bbox is None:
                raise RuntimeError(f"Empty house cell {house_number}")
            piece = cell.crop(bbox)
            canvas = Image.new("RGBA", (piece.width + 64, piece.height + 64), (0, 0, 0, 0))
            canvas.alpha_composite(piece, (32, 32))
            canvas.save(ROOT / f"house-{house_number}.png", optimize=True)
            house_number += 1


split_grid("houses-1-4-sheet.png", 2, 2, 1)
split_grid("houses-5-7-sheet.png", 3, 1, 5)
print("Seven house sprites created")
