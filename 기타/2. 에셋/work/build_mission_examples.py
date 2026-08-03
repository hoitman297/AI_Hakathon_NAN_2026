from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(r"C:\Users\a0109\Documents\Codex\2026-08-02\npc-7-1-2-1-2\outputs")
EXAMPLES = ROOT / "ui" / "missions" / "examples"
SOURCE = EXAMPLES / "source"
TEMPLATE = ROOT / "ui" / "missions" / "mission-card-active.png"
FONT_PATH = Path(r"C:\Windows\Fonts\malgunbd.ttf")

# Coordinates of the image opening and two-line caption in the 592x813 card.
WINDOW = (86, 102, 506, 568)
CAPTION_CENTER_Y = 646
TEXT_COLOR = (58, 42, 30, 255)


def cover_crop(image, size):
    target_w, target_h = size
    source_ratio = image.width / image.height
    target_ratio = target_w / target_h
    if source_ratio > target_ratio:
        crop_w = round(image.height * target_ratio)
        left = (image.width - crop_w) // 2
        image = image.crop((left, 0, left + crop_w, image.height))
    else:
        crop_h = round(image.width / target_ratio)
        top = (image.height - crop_h) // 2
        image = image.crop((0, top, image.width, top + crop_h))
    return image.resize(size, Image.Resampling.LANCZOS)


def centered_text(draw, text, y, font):
    bounds = draw.textbbox((0, 0), text, font=font)
    width = bounds[2] - bounds[0]
    draw.text(((592 - width) // 2, y), text, font=font, fill=TEXT_COLOR)


def build(scene, lines, destination, source_crop=None):
    card = Image.open(TEMPLATE).convert("RGBA")
    scene_image = Image.open(scene).convert("RGB")
    if source_crop:
        scene_image = scene_image.crop(source_crop)
    x1, y1, x2, y2 = WINDOW
    scene_image = cover_crop(scene_image, (x2 - x1, y2 - y1))
    card.paste(scene_image, (x1, y1))

    draw = ImageDraw.Draw(card)
    font = ImageFont.truetype(str(FONT_PATH), 25)
    centered_text(draw, lines[0], CAPTION_CENTER_Y, font)
    centered_text(draw, lines[1], CAPTION_CENTER_Y + 42, font)
    card.save(destination, optimize=True)


build(
    SOURCE / "chicken-coop-generated-card.png",
    ("양계장 울타리가 부서졌다.", "흩어진 닭과 단서를 찾아보자."),
    EXAMPLES / "example-chicken-coop-investigation.png",
    source_crop=(150, 185, 935, 1085),
)
build(
    SOURCE / "watermelon-field-illustration.png",
    ("수박밭이 처참하게 망가졌다.", "밭에 남은 발자국을 조사하자."),
    EXAMPLES / "example-watermelon-field-investigation.png",
)

print("Created 2 completed mission-card examples")
