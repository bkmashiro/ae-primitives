#!/usr/bin/env python3
"""Generate the small, shared machine textures without image dependencies."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/aeprimitives/textures/block"
Color = tuple[int, int, int, int]


def write_png(name, pixels):
    width = height = 16
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    (ROOT / name).write_bytes(png)


def canvas(color: Color) -> list[list[Color]]:
    return [[color for _ in range(16)] for _ in range(16)]


def rect(image: list[list[Color]], x0: int, y0: int, x1: int, y1: int, color: Color) -> None:
    for y in range(y0, y1):
        for x in range(x0, x1):
            image[y][x] = color


# Recessed interior wall: quiet enough to let actual geometry read first.
back = canvas((29, 38, 45, 255))
rect(back, 1, 1, 15, 15, (38, 51, 59, 255))
rect(back, 2, 2, 14, 14, (27, 38, 46, 255))
for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
    rect(back, x, y, x + 1, y + 1, (90, 119, 126, 255))
for i in range(4, 12):
    back[8][i] = (46, 93, 101, 255)
    back[i][8] = (46, 93, 101, 255)
write_png("chamber_back.png", back)

ore = canvas((61, 69, 73, 255))
for x, y, color in (
    (2, 3, (92, 107, 108, 255)), (11, 2, (43, 50, 54, 255)),
    (5, 6, (73, 223, 194, 255)), (9, 5, (39, 151, 146, 255)),
    (7, 10, (103, 236, 208, 255)), (12, 11, (34, 112, 115, 255)),
    (3, 12, (47, 54, 58, 255)),
):
    rect(ore, x, y, x + 2, y + 2, color)
write_png("fortune_ore.png", ore)

beam = canvas((83, 221, 232, 255))
rect(beam, 0, 0, 16, 2, (202, 255, 255, 255))
rect(beam, 0, 14, 16, 16, (29, 114, 143, 255))
write_png("fortune_beam.png", beam)
