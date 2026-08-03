from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs")


def split_grid(source, output_dir, columns, rows, names):
    image = Image.open(source).convert("RGBA")
    cell_w, cell_h = image.width // columns, image.height // rows
    output_dir.mkdir(parents=True, exist_ok=True)
    pieces = []
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
                raise RuntimeError(f"Empty cell {row},{column}: {source}")
            pieces.append(cell.crop(bbox))
    if len(pieces) != len(names):
        raise RuntimeError("Name count mismatch")
    for piece, name in zip(pieces, names):
        canvas = Image.new("RGBA", (piece.width + 48, piece.height + 48), (0, 0, 0, 0))
        canvas.alpha_composite(piece, (24, 24))
        canvas.save(output_dir / f"{name}.png", optimize=True)


split_grid(
    ROOT / "items" / "item-icons-sheet.png",
    ROOT / "items",
    5, 1,
    ["sneakers", "lie-detector", "bag-level-1", "bag-level-2", "bag-level-3"],
)
split_grid(
    ROOT / "background-assets" / "furniture" / "furniture-sheet.png",
    ROOT / "background-assets" / "furniture",
    4, 2,
    ["single-bed", "wardrobe", "low-table", "wooden-chair", "bookshelf", "storage-cabinet", "floor-lamp", "storage-chest"],
)
print("Created 5 item icons and 8 furniture sprites")
