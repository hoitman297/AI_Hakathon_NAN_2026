from pathlib import Path
from PIL import Image


def split_row(source: Path, output_dir: Path, names):
    image = Image.open(source).convert("RGBA")
    cell_width = image.width // len(names)
    output_dir.mkdir(parents=True, exist_ok=True)
    for index, name in enumerate(names):
        left = index * cell_width
        right = image.width if index == len(names) - 1 else (index + 1) * cell_width
        cell = image.crop((left, 0, right, image.height))
        bbox = cell.getchannel("A").getbbox()
        if bbox is None:
            raise RuntimeError(f"Empty section: {source.name} / {name}")
        piece = cell.crop(bbox)
        canvas = Image.new("RGBA", (piece.width + 64, piece.height + 64), (0, 0, 0, 0))
        canvas.alpha_composite(piece, (32, 32))
        canvas.save(output_dir / f"{name}.png", optimize=True)


ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\background-assets")
split_row(
    ROOT / "buildings" / "public" / "public-buildings-sheet.png",
    ROOT / "buildings" / "public",
    ["produce-shop", "item-shop", "village-hall"],
)
split_row(
    ROOT / "facilities" / "chicken-coop-state-sheet.png",
    ROOT / "facilities",
    ["chicken-coop-normal", "chicken-coop-broken"],
)
split_row(
    ROOT / "facilities" / "watermelon-field-state-sheet.png",
    ROOT / "facilities",
    ["watermelon-field-normal", "watermelon-field-damaged"],
)
print("Created 7 public-building and facility PNG files")
