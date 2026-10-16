#!/usr/bin/env python3
# Generates Hibiscus Quests' GUI sprites under src/main/resources/assets/hibiscusquests/textures/gui/sprites/.
# Style: Hibiscus Lib's default theme (flat fills, 1 px bevels: light top-left, dark bottom-right, no gradients or
# noise), in its colours (hibiscuslib's assets/hibiscuslib/hibiscus_theme/default.json).
#
# Node shapes are white masks (shape/<name>) and white outlines (shape/<name>_outline), tinted by the book for each
# quest state, so a resource pack restyles every state by changing the theme, and adds a shape by adding a pair of
# sprites (a quest with shape: "mypack:star" uses mypack:shape/star and mypack:shape/star_outline).
# Usage: python tools/gui_sprite_gen.py   (needs Pillow: pip install pillow)
import math
import os

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "hibiscusquests", "textures", "gui", "sprites")

SHADOW = "#18191C"
TEXT = "#E6E6E6"
TEXT_MUTED = "#9A9DA3"
TEXT_DISABLED = "#5C5F66"
ACCENT = "#F2557A"
ACCENT_HOVER = "#FF7898"
ACCENT_DARK = "#B23A5A"
GOLD = "#E8B04B"
GOLD_LIGHT = "#F7D38A"
GOLD_DARK = "#A87A28"
POSITIVE = "#4FC78A"
POSITIVE_DARK = "#2E8A5C"

SHAPE_SIZE = 32
OUTLINE = 2


def rgba(hex_color, alpha=255):
    h = hex_color.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), alpha)


def paint(px, shape, fill, light, dark):
    """Flat fill with a 1 px light edge where the shape faces up or left and a dark edge where it faces down or right."""
    for (x, y) in shape:
        if (x, y - 1) not in shape or (x - 1, y) not in shape:
            px[x, y] = rgba(light)
        elif (x, y + 1) not in shape or (x + 1, y) not in shape:
            px[x, y] = rgba(dark)
        else:
            px[x, y] = rgba(fill)


def mask(size, inside):
    """The pixels whose centre is inside, in a size x size grid, with coordinates from -1 to 1."""
    pixels = set()
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            if inside(u, v):
                pixels.add((x, y))
    return pixels


def polygon(sides, rotation, radius=0.97):
    corners = [(radius * math.cos(rotation + 2 * math.pi * i / sides), radius * math.sin(rotation + 2 * math.pi * i / sides))
               for i in range(sides)]

    def inside(u, v):
        for i in range(sides):
            (ax, ay), (bx, by) = corners[i], corners[(i + 1) % sides]
            if (bx - ax) * (v - ay) - (by - ay) * (u - ax) < 0:
                return False
        return True
    return inside


def rounded_square(u, v, r=0.38, half=0.94):
    dx = max(abs(u) - (half - r), 0)
    dy = max(abs(v) - (half - r), 0)
    return abs(u) <= half and abs(v) <= half and dx * dx + dy * dy <= r * r


def heart(u, v):
    x, y = u * 1.18, -v * 1.18 + 0.12
    return (x * x + y * y - 1) ** 3 - x * x * y ** 3 <= 0


def gear(u, v):
    r = math.hypot(u, v)
    if r < 0.30:
        return False  # the hole
    a = math.atan2(v, u)
    teeth = 8
    tooth = (math.cos(a * teeth) + 1) / 2  # 1 at a tooth's middle
    edge = 0.74 + 0.22 * (1 if tooth > 0.5 else 0)
    return r <= edge


SHAPES = {
    "circle": lambda u, v: u * u + v * v <= 0.97 ** 2,
    "square": lambda u, v: abs(u) <= 0.94 and abs(v) <= 0.94,
    "rsquare": rounded_square,
    "diamond": lambda u, v: abs(u) + abs(v) <= 0.98,
    "pentagon": polygon(5, -math.pi / 2),
    "hexagon": polygon(6, 0),
    "octagon": polygon(8, math.pi / 8),
    "heart": heart,
    "gear": gear,
}


def shape_images(inside):
    pixels = mask(SHAPE_SIZE, inside)
    fill = Image.new("RGBA", (SHAPE_SIZE, SHAPE_SIZE), (0, 0, 0, 0))
    outline = Image.new("RGBA", (SHAPE_SIZE, SHAPE_SIZE), (0, 0, 0, 0))
    fp, op = fill.load(), outline.load()
    for (x, y) in pixels:
        fp[x, y] = (255, 255, 255, 255)
        near_edge = any((x + dx, y + dy) not in pixels for dx in range(-OUTLINE, OUTLINE + 1)
                        for dy in range(-OUTLINE, OUTLINE + 1) if abs(dx) + abs(dy) <= OUTLINE)
        if near_edge:
            op[x, y] = (255, 255, 255, 255)
    return fill, outline


def icon_book():
    """The Quests button: a closed book, cover in the accent colour, a gold bookmark and a check on the cover."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    pages = {(x, y) for x in range(4, 14) for y in range(3, 15)}
    paint(px, pages, TEXT, TEXT, TEXT_MUTED)
    cover = {(x, y) for x in range(2, 12) for y in range(1, 13)}
    paint(px, cover, ACCENT, ACCENT_HOVER, ACCENT_DARK)
    for y in range(1, 13):
        px[3, y] = rgba(ACCENT_DARK)
    # The bookmark, hanging below the book.
    for y in range(11, 16):
        px[9, y] = rgba(GOLD_LIGHT)
        px[10, y] = rgba(GOLD)
    px[9, 15] = (0, 0, 0, 0)
    # A check on the cover.
    for (x, y) in ((5, 7), (6, 8), (7, 9), (8, 8), (9, 7), (10, 6), (10, 5)):
        px[x, y] = rgba(TEXT)
    return img


def icon_check():
    """A bold check mark in the positive colour, for completed quests."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    stroke = set()
    for (x, y) in ((2, 8), (3, 9), (4, 10), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4), (13, 3)):
        for dx in (0, 1):
            for dy in (0, 1, 2):
                stroke.add((x + dx, y + dy))
    for (x, y) in stroke:
        for dx, dy in ((1, 1), (0, 1), (1, 0)):
            n = (x + dx, y + dy)
            if n not in stroke and 0 <= n[0] < 16 and 0 <= n[1] < 16:
                px[n[0], n[1]] = rgba(SHADOW)
    paint(px, stroke, POSITIVE, "#7FE0AE", POSITIVE_DARK)
    return img


def icon_lock():
    """A padlock, for locked chapters."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    shackle = set()
    for y in range(2, 8):
        for x in range(4, 12):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 7)
            if 2.5 <= r <= 4.0 and y <= 7:
                shackle.add((x, y))
    paint(px, shackle, TEXT_MUTED, TEXT, TEXT_DISABLED)
    body = {(x, y) for x in range(3, 13) for y in range(7, 15)}
    paint(px, body, GOLD, GOLD_LIGHT, GOLD_DARK)
    for (x, y) in ((7, 9), (8, 9), (7, 10), (8, 10), (7, 11), (8, 11)):
        px[x, y] = rgba(SHADOW)
    return img


def icon_claim_all():
    """A gift box, for Claim all."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    box = {(x, y) for x in range(2, 14) for y in range(7, 15)}
    lid = {(x, y) for x in range(1, 15) for y in range(4, 7)}
    paint(px, box, GOLD, GOLD_LIGHT, GOLD_DARK)
    paint(px, lid, GOLD, GOLD_LIGHT, GOLD_DARK)
    for y in range(4, 15):
        px[7, y] = rgba(ACCENT)
        px[8, y] = rgba(ACCENT_DARK)
    for (x, y) in ((5, 1), (6, 2), (6, 3), (10, 1), (9, 2), (9, 3), (4, 2), (11, 2)):
        px[x, y] = rgba(ACCENT)
    return img


def icon_link():
    """Two chain links, marking a node that shows a quest from another chapter."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for (cx, cy) in ((5.5, 10.5), (10.5, 5.5)):
        ring = set()
        for y in range(16):
            for x in range(16):
                r = math.hypot((x + 0.5 - cx) * 1.0, (y + 0.5 - cy) * 1.0)
                if 2.2 <= r <= 3.8:
                    ring.add((x, y))
        paint(px, ring, TEXT_MUTED, TEXT, TEXT_DISABLED)
    return img


def icon_panel(collapse):
    """Two bars and an arrow: hide the chapter list (pointing left) or show it (pointing right)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(3, 13):
        px[2, y] = rgba(TEXT_MUTED)
        px[3, y] = rgba(TEXT_MUTED)
    arrow = set()
    for i in range(5):
        for y in range(8 - i, 8 + i):
            x = 6 + i if collapse else 12 - i
            arrow.add((x, y))
    for (x, y) in arrow:
        px[x, y] = rgba(TEXT)
    return img


def save(name, img):
    path = os.path.join(OUT, name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", os.path.relpath(path))


def main():
    save("button/quests", icon_book())
    for name, inside in SHAPES.items():
        fill, outline = shape_images(inside)
        save("shape/" + name, fill)
        save("shape/" + name + "_outline", outline)
    save("icon/check", icon_check())
    save("icon/lock", icon_lock())
    save("icon/claim_all", icon_claim_all())
    save("icon/link", icon_link())
    save("icon/panel_collapse", icon_panel(True))
    save("icon/panel_expand", icon_panel(False))


if __name__ == "__main__":
    main()
