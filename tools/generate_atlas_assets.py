#!/usr/bin/env python3
"""Rebuild original Living Atlas ornaments with Python's standard library only.

Coordinates and pixel drawings below are the editable source of ornaments.png.
folio_tile.png is a separately generated and reduced surface; see atlas-assets.md.
"""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'src/client/resources/assets/regnum-nations/textures/gui/atlas'
W, H = 256, 128
pixels = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]
GOLD = (170, 143, 94, 255)
LIGHT = (211, 188, 137, 255)
SHADOW = (50, 46, 35, 255)
STONE = (101, 102, 87, 255)
IVORY = (235, 225, 198, 255)
INK = (24, 28, 27, 255)


def dot(x, y, color):
    if 0 <= x < W and 0 <= y < H:
        pixels[y][x] = color


def rect(x, y, w, h, color):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            dot(xx, yy, color)


def line(x, y, xx, yy, color):
    dx, dy = abs(xx-x), -abs(yy-y)
    sx, sy = 1 if x < xx else -1, 1 if y < yy else -1
    error = dx + dy
    while True:
        dot(x, y, color)
        if (x, y) == (xx, yy):
            break
        twice = error * 2
        if twice >= dy:
            error += dy
            x += sx
        if twice <= dx:
            error += dx
            y += sy


def polygon(points, color):
    for y in range(min(p[1] for p in points), max(p[1] for p in points)+1):
        xs = []
        for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]):
            if y1 == y2:
                continue
            if min(y1, y2) <= y < max(y1, y2):
                xs.append(x1 + (y-y1)*(x2-x1)/(y2-y1))
        xs.sort()
        for a, b in zip(xs[::2], xs[1::2]):
            for x in range(int(a+0.999), int(b)+1):
                dot(x, y, color)


# Binding corners: a double rule, folded clasp, and a restrained inward leaf.
line(1, 1, 15, 1, LIGHT)
line(1, 1, 1, 15, LIGHT)
line(3, 3, 15, 3, GOLD)
line(3, 3, 3, 15, GOLD)
line(5, 5, 11, 5, SHADOW)
line(5, 5, 5, 11, SHADOW)
polygon([(6, 6), (12, 7), (8, 9), (7, 12)], GOLD)
line(7, 7, 10, 10, LIGHT)
rect(1, 1, 3, 3, GOLD)
dot(2, 2, IVORY)
corner = [row[:16] for row in pixels[:16]]
for index in range(1, 4):
    for y in range(16):
        for x in range(16):
            pixels[y][index*16+x] = corner[15-y if index >= 2 else y][15-x if index % 2 else x]

# Tiled edge segments. Opposite edges are symmetric around their local midpoint.
for x in range(16):
    dot(x, 17, GOLD)
    dot(x, 19, SHADOW)
    dot(x, 21, STONE)
for x in (2, 10):
    dot(x, 18, LIGHT)
for y in range(16):
    dot(17, 16+y, GOLD)
    dot(19, 16+y, SHADOW)
    dot(21, 16+y, STONE)
for y in (2, 10):
    dot(18, 16+y, LIGHT)

# Opposite border strips mirror the corners, keeping the same continuous rules.
for yy in range(8):
    for xx in range(16):
        pixels[24+yy][xx] = pixels[16+7-yy][xx]
for yy in range(16):
    for xx in range(8):
        pixels[16+yy][24+xx] = pixels[16+yy][16+7-xx]

# A lozenge divider is the atlas's repeated section marker.
line(32, 20, 63, 20, GOLD)
polygon([(48, 16), (53, 20), (48, 24), (43, 20)], SHADOW)
polygon([(48, 17), (51, 20), (48, 23), (45, 20)], GOLD)
dot(48, 20, IVORY)

# Standard's brass suspension rod, including two finials.
rect(72, 19, 41, 2, GOLD)
rect(73, 18, 39, 1, LIGHT)
for x in (72, 108):
    polygon([(x, 19), (x+2, 16), (x+4, 19), (x+2, 23)], GOLD)
    dot(x+2, 18, LIGHT)

# Grayscale cloth, suitable for multiplication by the persisted political color.
cloth = [(3, 35), (28, 35), (28, 75), (16, 87), (3, 75)]
polygon(cloth, (218, 218, 218, 255))
for y in range(36, 86):
    for x in range(4, 28):
        if pixels[y][x][3]:
            value = 238 if 7 <= x <= 11 else 181 if x >= 24 else 210
            dot(x, y, (value, value, value, 255))
# A few woven stitches; no full-screen texture noise.
for y in (41, 57, 72):
    for x in (5, 23):
        if pixels[y][x][3]:
            dot(x, y, (160, 160, 160, 255))
# Independent trim overlays the tinted cloth.
points = [(35, 35), (60, 35), (60, 75), (48, 87), (35, 75), (35, 35)]
for a, b in zip(points, points[1:]):
    line(*a, *b, GOLD)
line(37, 37, 58, 37, LIGHT)
line(36, 38, 36, 74, LIGHT)
line(37, 76, 47, 85, LIGHT)
for x in (40, 54):
    rect(x, 32, 3, 4, GOLD)

# Four division masks: pale, chevron, bend, quartering. Alpha keeps the field dominant.
for index in range(4):
    for y in range(56):
        for x in range(32):
            if not pixels[32+y][x][3]:
                continue
            selected = (14 <= x <= 17) if index == 0 else (abs(y-(27+abs(x-16)//2)) <= 1) if index == 1 else (abs(x-(28-y//2)) <= 2) if index == 2 else (x in (15, 16) or y in (24, 25))
            if selected:
                dot(64+index*32+x, 64+y, (255, 255, 255, 45))

# Dark heater shield, separating the ivory charge from light political colors.
polygon([(208, 33), (231, 33), (231, 50), (220, 60), (208, 50)], GOLD)
polygon([(210, 35), (229, 35), (229, 49), (220, 57), (210, 49)], INK)

# Four original 16px heraldic charges: tower, north star, oak, crossed keys.
def charge(index, draw):
    draw(64+index*16, 32)
    # No external artwork or Unicode glyphs are used for heraldry.


def tower(x, y):
    rect(x+4, y+5, 8, 9, IVORY)
    rect(x+3, y+3, 10, 3, IVORY)
    for xx in (3, 7, 11):
        rect(x+xx, y+1, 2, 3, IVORY)
    rect(x+7, y+10, 2, 4, INK)
    rect(x+6, y+6, 1, 2, INK)
    rect(x+9, y+6, 1, 2, INK)


def star(x, y):
    polygon([(x+8, y), (x+10, y+6), (x+15, y+8), (x+10, y+10), (x+8, y+15), (x+6, y+10), (x, y+8), (x+6, y+6)], IVORY)
    for dx, dy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        dot(x+dx, y+dy, GOLD)
    rect(x+7, y+7, 2, 2, GOLD)


def oak(x, y):
    rect(x+7, y+6, 2, 9, IVORY)
    for dx, dy in ((3, 5), (10, 5), (5, 1), (1, 8), (11, 8)):
        polygon([(x+dx, y+dy+1), (x+dx+2, y+dy-1), (x+dx+4, y+dy+1), (x+dx+3, y+dy+4), (x+dx, y+dy+3)], IVORY)
    rect(x+5, y+14, 6, 1, IVORY)


def keys(x, y):
    for dx in (2, 10):
        rect(x+dx, y+1, 4, 4, IVORY)
        rect(x+dx+1, y+2, 2, 2, INK)
    line(x+4, y+5, x+12, y+13, IVORY)
    line(x+12, y+5, x+4, y+13, IVORY)
    rect(x+10, y+12, 4, 2, IVORY)
    rect(x+2, y+12, 4, 2, IVORY)
    dot(x+13, y+11, IVORY)
    dot(x+2, y+11, IVORY)

for i, draw in enumerate((tower, star, oak, keys)):
    charge(i, draw)

# Meaningful small marks: a territory grid and a capital gateway (12px each).
for yy in range(3):
    for xx in range(3):
        rect(64+xx*4, 48+yy*4, 3, 3, GOLD if (xx, yy) != (1, 1) else IVORY)
rect(80, 53, 12, 7, GOLD)
rect(80, 50, 3, 4, IVORY)
rect(89, 50, 3, 4, IVORY)
rect(83, 51, 6, 3, GOLD)
rect(85, 56, 2, 4, INK)
rect(81, 48, 1, 2, GOLD)
rect(90, 48, 1, 2, GOLD)


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind+data))


DEST.mkdir(parents=True, exist_ok=True)
raw = b''.join(b'\0' + bytes(v for rgba in row for v in rgba) for row in pixels)
png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', W, H, 8, 6, 0, 0, 0))
png += chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
(DEST / 'ornaments.png').write_bytes(png)
for name in ['ornaments', 'folio_tile']:
    (DEST / (name+'.png.mcmeta')).write_text('{"texture":{"blur":false,"clamp":false}}\n')
print('Wrote original ornaments.png (256×128) and crisp texture metadata')
