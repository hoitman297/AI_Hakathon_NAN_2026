from pathlib import Path
from PIL import Image

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs")

required = []
required += [f"background-assets/terrain-{name}-source.png" for name in ("grass", "dirt-path", "tilled-soil", "water")]
required += [
    "background-assets/edge-grass-dirt.png",
    "background-assets/edge-riverbank.png",
    "background-assets/bridge-concrete.png",
]
for crop in ("potato", "sweet-potato", "carrot", "strawberry", "watermelon"):
    required += [f"background-assets/growth/crops/{crop}-stage-{stage}.png" for stage in range(1, 4)]
for forage in ("apple-tree", "cherry-tree", "persimmon-tree", "raspberry-bush"):
    required += [f"background-assets/growth/fruit/{forage}-stage-{stage}.png" for stage in range(1, 4)]
required += [f"background-assets/buildings/houses/house-{number}.png" for number in range(1, 8)]
required += [f"background-assets/buildings/public/{name}.png" for name in ("produce-shop", "item-shop", "village-hall")]
required += [f"background-assets/facilities/{name}.png" for name in (
    "village-park", "chicken-coop-normal", "chicken-coop-broken",
    "watermelon-field-normal", "watermelon-field-damaged",
)]
required += [f"background-assets/objects/{name}.png" for name in (
    "ordinary-tree", "chicken-front", "chicken-back", "chicken-left", "chicken-right",
    "wood-fence-intact", "wood-fence-broken", "onggi-jars", "stone-well",
    "wooden-pyeongsang", "scarecrow",
)]
required += [f"background-assets/furniture/{name}.png" for name in (
    "single-bed", "wardrobe", "low-table", "wooden-chair", "bookshelf",
    "storage-cabinet", "floor-lamp", "storage-chest",
)]
required += [f"items/{name}.png" for name in (
    "sneakers", "lie-detector", "bag-level-1", "bag-level-2", "bag-level-3",
)]

opaque_terrain = {f"background-assets/terrain-{name}-source.png" for name in ("grass", "dirt-path", "tilled-soil", "water")}
errors = []

for relative in required:
    path = ROOT / relative
    if not path.is_file():
        errors.append(f"MISSING {relative}")
        continue
    try:
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            if image.width <= 0 or image.height <= 0:
                errors.append(f"INVALID_SIZE {relative}")
            if relative not in opaque_terrain:
                if image.mode != "RGBA":
                    errors.append(f"NO_RGBA {relative} ({image.mode})")
                else:
                    alpha = image.getchannel("A")
                    if alpha.getbbox() is None:
                        errors.append(f"EMPTY_ALPHA {relative}")
                    corners = [alpha.getpixel((0, 0)), alpha.getpixel((image.width - 1, 0)),
                               alpha.getpixel((0, image.height - 1)), alpha.getpixel((image.width - 1, image.height - 1))]
                    if any(corners):
                        errors.append(f"OPAQUE_CORNER {relative}: {corners}")
    except Exception as exc:
        errors.append(f"BROKEN_PNG {relative}: {exc}")

print(f"required={len(required)}")
print(f"passed={len(required) - len(errors)}")
print(f"errors={len(errors)}")
for error in errors:
    print(error)

raise SystemExit(1 if errors else 0)
