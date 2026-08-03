from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\ui")


def alpha_crop(image, padding=24):
    bbox = image.getchannel("A").getbbox()
    if bbox is None:
        raise RuntimeError("Image has no visible pixels")
    piece = image.crop(bbox)
    canvas = Image.new("RGBA", (piece.width + padding * 2, piece.height + padding * 2), (0, 0, 0, 0))
    canvas.alpha_composite(piece, (padding, padding))
    return canvas


def horizontal_components(image, minimum_gap=8):
    alpha = image.getchannel("A")
    occupied = []
    for x in range(image.width):
        occupied.append(alpha.crop((x, 0, x + 1, image.height)).getbbox() is not None)

    runs = []
    start = None
    empty_count = 0
    for x, visible in enumerate(occupied + [False] * minimum_gap):
        if visible:
            if start is None:
                start = x
            empty_count = 0
        elif start is not None:
            empty_count += 1
            if empty_count >= minimum_gap:
                runs.append((start, x - empty_count + 1))
                start = None
                empty_count = 0
    return [image.crop((left, 0, right, image.height)) for left, right in runs]


def split_horizontal(source, destination, names):
    image = Image.open(source).convert("RGBA")
    pieces = horizontal_components(image)
    if len(pieces) != len(names):
        raise RuntimeError(f"Expected {len(names)} components, found {len(pieces)} in {source.name}")
    destination.mkdir(parents=True, exist_ok=True)
    for piece, name in zip(pieces, names):
        alpha_crop(piece).save(destination / f"{name}.png", optimize=True)


split_horizontal(
    ROOT / "hud" / "stamina-components-sheet.png",
    ROOT / "hud",
    ["stamina-frame", "stamina-fill", "stamina-sneaker-icon"],
)
split_horizontal(
    ROOT / "missions" / "mission-card-state-sheet.png",
    ROOT / "missions",
    ["mission-card-active", "mission-card-completed", "mission-card-failed"],
)

inventory = Image.open(ROOT / "panels" / "inventory-window.png").convert("RGBA")
alpha_crop(inventory, padding=16).save(ROOT / "panels" / "inventory-window-trimmed.png", optimize=True)
print("Created 3 HUD components, 3 mission cards, and 1 trimmed inventory panel")
