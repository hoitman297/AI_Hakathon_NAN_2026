from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\background-assets\growth\crops")
NAMES = ["carrot", "potato", "sweet-potato", "strawberry", "watermelon"]

for name in NAMES:
    source = ROOT / f"{name}-sheet.png"
    image = Image.open(source).convert("RGBA")
    alpha = image.getchannel("A")
    third = image.width // 3
    extracted = []

    for index in range(3):
        left = index * third
        right = image.width if index == 2 else (index + 1) * third
        section = image.crop((left, 0, right, image.height))
        bbox = section.getchannel("A").getbbox()
        if bbox is None:
            raise RuntimeError(f"No visible pixels: {name} stage {index + 1}")
        extracted.append(section.crop(bbox))

    canvas_width = max(piece.width for piece in extracted) + 64
    canvas_height = max(piece.height for piece in extracted) + 64

    for index, piece in enumerate(extracted, start=1):
        canvas = Image.new("RGBA", (canvas_width, canvas_height), (0, 0, 0, 0))
        x = (canvas_width - piece.width) // 2
        y = canvas_height - piece.height - 32
        canvas.alpha_composite(piece, (x, y))
        canvas.save(ROOT / f"{name}-stage-{index}.png", optimize=True)

print("Split crop sheets:", ", ".join(NAMES))
