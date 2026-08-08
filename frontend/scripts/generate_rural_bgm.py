from pathlib import Path
import wave

import numpy as np


RATE = 22050
OUT = Path(__file__).resolve().parents[1] / "public" / "assets" / "audio" / "bgm-samples"


def midi(note: int) -> float:
    return 440.0 * 2 ** ((note - 69) / 12)


def pan(signal: np.ndarray, position: float) -> tuple[np.ndarray, np.ndarray]:
    angle = (position + 1) * np.pi / 4
    return signal * np.cos(angle), signal * np.sin(angle)


def add_tone(track, start, length, note, volume, kind="pluck", position=0.0):
    begin = int(start * RATE)
    count = min(int(length * RATE), len(track) - begin)
    if count <= 0:
        return
    t = np.arange(count) / RATE
    frequency = midi(note)
    if kind == "flute":
        attack = np.minimum(1, t / 0.08)
        release = np.minimum(1, (length - t) / 0.25)
        envelope = attack * np.maximum(0, release)
        signal = (np.sin(2 * np.pi * frequency * t) + 0.16 * np.sin(4 * np.pi * frequency * t)) * envelope
    elif kind == "bell":
        envelope = np.exp(-3.8 * t / max(length, 0.1))
        signal = (np.sin(2 * np.pi * frequency * t) + 0.3 * np.sin(2 * np.pi * frequency * 2.01 * t)) * envelope
    else:
        envelope = np.exp(-5.4 * t / max(length, 0.1))
        signal = (np.sin(2 * np.pi * frequency * t) + 0.22 * np.sin(4 * np.pi * frequency * t)) * envelope
    left, right = pan(signal * volume, position)
    track[begin:begin + count, 0] += left
    track[begin:begin + count, 1] += right


def ambience(seed: int, style: str, duration: float) -> np.ndarray:
    rng = np.random.default_rng(seed)
    size = int(duration * RATE)
    noise = rng.normal(0, 1, size)
    smooth = np.convolve(noise, np.ones(500) / 500, mode="same")
    bed = np.column_stack((smooth, np.roll(smooth, 170))) * (0.055 if style == "stream" else 0.025)
    if style == "morning":
        for start in np.arange(4.2, duration - 1, 18.0):
            for offset, note in ((0, 88), (0.12, 91), (0.25, 86)):
                add_tone(bed, start + offset, 0.22, note, 0.025, "flute", -0.65)
    return bed


def compose(filename: str, bpm: int, chords, melody, style: str, seed: int, duration=36.0, loopable=False):
    track = ambience(seed, style, duration)
    beat = 60 / bpm
    bars = int(duration / (beat * 4))
    for bar in range(bars):
        chord = chords[bar % len(chords)]
        start = bar * beat * 4
        for step, note in enumerate(chord):
            add_tone(track, start + step * beat * 0.5, beat * 2.4, note, 0.075, "pluck", -0.3 + step * 0.3)
        for step in range(8):
            note = melody[(bar * 8 + step) % len(melody)]
            if note is not None:
                instrument = "flute" if style != "afternoon" else "bell"
                add_tone(track, start + step * beat * 0.5, beat * 0.8, note, 0.06, instrument, 0.25)
    if not loopable:
        fade = int(RATE * 1.8)
        track[:fade] *= np.linspace(0, 1, fade)[:, None]
        track[-fade:] *= np.linspace(1, 0, fade)[:, None]
    peak = max(0.001, np.max(np.abs(track)))
    pcm = np.int16(np.clip(track / peak * 0.72, -1, 1) * 32767)
    OUT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUT / filename), "wb") as output:
        output.setnchannels(2)
        output.setsampwidth(2)
        output.setframerate(RATE)
        output.writeframes(pcm.tobytes())


compose(
    "01-morning-fields.wav", 72,
    [(55, 62, 67), (57, 64, 69), (52, 59, 64), (55, 62, 69)],
    [67, 69, 71, None, 74, 71, 69, None, 67, 64, 62, None, 64, 67, 69, None],
    "morning", 17, duration=180.0, loopable=True,
)
compose(
    "02-quiet-village-afternoon.wav", 66,
    [(50, 57, 62), (55, 62, 67), (52, 59, 64), (50, 57, 64)],
    [62, None, 64, 66, 69, None, 66, 64, 62, 59, None, 62, 64, None, 59, None],
    "afternoon", 29,
)
compose(
    "03-stream-at-dusk.wav", 60,
    [(45, 52, 57), (48, 55, 60), (43, 50, 55), (45, 52, 59)],
    [69, None, 67, 64, None, 62, 64, None, 67, 69, 72, None, 69, 67, None, 64],
    "stream", 43,
)
