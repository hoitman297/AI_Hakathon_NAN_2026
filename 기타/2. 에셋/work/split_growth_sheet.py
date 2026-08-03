from pathlib import Path

from PIL import Image


ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs\background-assets\growth\fruit")
NAMES = ("apple-tree", "cherry-tree", "persimmon-tree", "raspberry-bush")


for name in NAMES:
    source = ROOT / f"{name}-sheet.png"
    image = Image.open(source).convert("RGBA")
    width, height = image.size
    canvas_width = (width + 2) // 3

    for index in range(3):
        left = round(width * index / 3)
        right = round(width * (index + 1) / 3)
        crop = image.crop((left, 0, right, height))
        stage = Image.new("RGBA", (canvas_width, height), (0, 0, 0, 0))
        stage.alpha_composite(crop, ((canvas_width - crop.width) // 2, 0))
        target = ROOT / f"{name}-stage-{index + 1}.png"
        stage.save(target)
        print(f"{target.name}: {stage.mode} {stage.size} alpha_bbox={stage.getchannel('A').getbbox()}")
