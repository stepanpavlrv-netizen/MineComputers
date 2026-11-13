"""Радиовышка (0.30): модели секций (сужение кверху, красно-белые полосы, оцинкованная), опоры нижней секции,
штырь, секторная панель, тарелка; blockstates, иконки, добыча и рецепты.

Ширина секции, над которой ещё k секций: min(48, 16 + 2k) пикселей — внизу 3×3, наверху 1×1 (как в
TowerSectionBlock.width). Ноги — по углам, пояс внизу, раскосы крестом на каждой грани, по центру — чёрный фидер.

Запуск из корня проекта: python3 tools/assetgen/tower.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from cases import ASSETS, DATA, write  # noqa: E402
from PIL import Image  # noqa: E402

UV = [0, 0, 4, 4]
COLORS = {"red": "minecraft:block/red_concrete", "white": "minecraft:block/white_concrete",
          "grey": "minecraft:block/light_gray_concrete"}


def width(k):
    return min(48, 16 + 2 * k)


def faces(tex, uv=UV):
    return {d: {"texture": tex, "uv": uv} for d in ("north", "south", "east", "west", "up", "down")}


def box(a, b, tex, rot=None):
    # Игра не принимает элементы модели за пределами -16..32
    assert all(-16 <= v <= 32 for v in (*a, *b)), (a, b)
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": faces(tex)}
    if rot:
        e["rotation"] = rot
    return e


def section(k, color):
    wb = width(k)
    wt = 16 if k == 0 else width(k - 1)
    els = []
    # Ноги: две половины по высоте (вышка сужается на полпикселя с каждой стороны за секцию)
    for y0, y1, f in ((0, 8, 0.25), (8, 16, 0.75)):
        h = (wb + (wt - wb) * f) / 2
        for sx in (-1, 1):
            for sz in (-1, 1):
                x0 = 8 + h - 2 if sx > 0 else 8 - h
                z0 = 8 + h - 2 if sz > 0 else 8 - h
                els.append(box((x0, y0, z0), (x0 + 2, y1, z0 + 2), "#leg"))
    # Пояс внизу
    hb = wb / 2
    els.append(box((8 - hb, 0, 8 - hb), (8 + hb, 1.5, 8 - hb + 1.2), "#leg"))
    els.append(box((8 - hb, 0, 8 + hb - 1.2), (8 + hb, 1.5, 8 + hb), "#leg"))
    els.append(box((8 - hb, 0, 8 - hb + 1.2), (8 - hb + 1.2, 1.5, 8 + hb - 1.2), "#leg"))
    els.append(box((8 + hb - 1.2, 0, 8 - hb + 1.2), (8 + hb, 1.5, 8 + hb - 1.2), "#leg"))
    # Раскосы крестом на каждой грани
    wm = (wb + wt) / 2
    hm = wm / 2
    if wm < 36:
        ang, length = 45, 16 * math.sqrt(2)
    else:
        ang = 22.5
        length = min(16 / math.sin(math.radians(22.5)), (wm - 2) / math.cos(math.radians(22.5)))
    for zf in (8 - hm + 0.9, 8 + hm - 0.9):           # северная и южная грани — поворот вокруг Z
        for a in (ang, -ang):
            els.append(box((8 - length / 2, 7.5, zf - 0.4), (8 + length / 2, 8.5, zf + 0.4), "#brace",
                           {"angle": a, "axis": "z", "origin": [8, 8, round(zf, 3)]}))
    for xf in (8 - hm + 0.9, 8 + hm - 0.9):           # западная и восточная — вокруг X
        for a in (ang, -ang):
            els.append(box((xf - 0.4, 7.5, 8 - length / 2), (xf + 0.4, 8.5, 8 + length / 2), "#brace",
                           {"angle": a, "axis": "x", "origin": [round(xf, 3), 8, 8]}))
    # Фидер по центру
    els.append(box((7.4, 0, 7.4), (8.6, 16, 8.6), "#feeder"))
    if k == 0:
        # Макушка: верхний пояс и площадка под антенну
        ht = wt / 2
        els.append(box((8 - ht, 14.5, 8 - ht), (8 + ht, 16, 8 + ht), "#leg"))
        els.append(box((-2, 16, -2), (18, 16.8, 18), "#deck"))
    tex = {"particle": COLORS[color], "leg": COLORS[color], "brace": "minecraft:block/iron_block",
           "feeder": "minecraft:block/black_concrete", "deck": "minecraft:block/gray_concrete"}
    return {"ambientocclusion": False, "textures": tex, "elements": els}


def footing(k):
    h = width(k) / 2
    els = []
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx = 8 + sx * (h - 1)
            cz = 8 + sz * (h - 1)
            lo = lambda v: max(-16, v - 2)
            hi = lambda v: min(32, v + 2)
            els.append(box((lo(cx), 0, lo(cz)), (hi(cx), 2, hi(cz)), "#pad"))
    return {"ambientocclusion": False, "textures": {"particle": "minecraft:block/smooth_stone", "pad": "minecraft:block/smooth_stone"},
            "elements": els}


def pin():
    return {"ambientocclusion": False,
            "textures": {"particle": "minecraft:block/black_concrete", "pole": "minecraft:block/black_concrete",
                         "iron": "minecraft:block/iron_block", "lamp": "minecraft:block/redstone_block"},
            "elements": [box((6.5, 0, 6.5), (9.5, 1.5, 9.5), "#iron"), box((7.4, 1.5, 7.4), (8.6, 28, 8.6), "#pole"),
                         box((7, 28, 7), (9, 30, 9), "#lamp")]}


def sector():
    return {"ambientocclusion": False,
            "textures": {"particle": "minecraft:block/white_concrete", "panel": "minecraft:block/white_concrete",
                         "iron": "minecraft:block/iron_block", "feeder": "minecraft:block/black_concrete"},
            "elements": [box((4.5, 0.5, 10), (11.5, 15.5, 12), "#panel"),
                         box((7, 2, 12), (9, 4, 16), "#iron"), box((7, 12, 12), (9, 14, 16), "#iron"),
                         box((7.6, 0, 12), (8.4, 2, 16), "#feeder")]}


def dish():
    return {"ambientocclusion": False,
            "textures": {"particle": "minecraft:block/white_concrete", "dish": "minecraft:block/white_concrete",
                         "grey": "minecraft:block/light_gray_concrete", "iron": "minecraft:block/iron_block"},
            "elements": [box((1, 3, 9), (15, 13, 10), "#dish"), box((3, 1, 9), (13, 15, 10), "#dish"),
                         box((2, 2, 9.4), (14, 14, 10.2), "#dish"),
                         box((4, 4, 10), (12, 12, 11.5), "#grey"), box((6, 6, 11.5), (10, 10, 12.5), "#grey"),
                         box((7, 7, 12.5), (9, 9, 16), "#iron"),
                         box((7.6, 7.6, 4), (8.4, 8.4, 9), "#iron"), box((7, 7, 3), (9, 9, 4), "#grey")]}


def facing(model):
    return {"variants": {f"facing={f}": ({"model": model} | ({"y": y} if y else {}))
                         for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}


def icon(name, draw):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw(lambda x, y, c: im.putpixel((x, y), tuple(c) + (255,)) if 0 <= x < 16 and 0 <= y < 16 else None)
    src = im.copy()
    for x in range(16):
        for y in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), (16, 16, 20, 255))
    path = os.path.join(ASSETS, "textures/item", name + ".png")
    im.save(path)
    write(os.path.join(ASSETS, "models/item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"minepc:item/{name}"}})


def draw_section(px):
    red, white, grey = (196, 40, 36), (236, 238, 240), (150, 154, 160)
    for y in range(1, 15):
        c = red if (y // 4) % 2 == 0 else white
        px(3, y, c); px(12, y, c)
    for x in range(3, 13):
        px(x, 1, red); px(x, 14, white)
    for i in range(10):
        px(3 + i, 2 + i * 12 // 10, grey); px(12 - i, 2 + i * 12 // 10, grey)


def draw_pin(px):
    for y in range(3, 15):
        px(7, y, (30, 30, 34)); px(8, y, (44, 44, 50))
    px(7, 1, (255, 60, 50)); px(8, 1, (255, 60, 50)); px(7, 2, (200, 40, 36)); px(8, 2, (200, 40, 36))
    for x in range(5, 11):
        px(x, 15, (170, 174, 180))


def draw_sector(px):
    for x in range(5, 11):
        for y in range(1, 15):
            px(x, y, (236, 238, 240) if x != 10 else (200, 204, 210))
    px(7, 7, (170, 174, 180)); px(8, 7, (170, 174, 180))


def draw_dish(px):
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 6.6:
                px(x, y, (236, 238, 240) if d > 1.6 else (150, 154, 160))
            if 5.6 < d <= 6.6:
                px(x, y, (206, 210, 216))


def loot(name):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def main():
    parts = []
    for k in range(17):
        for color in ("red", "white", "grey"):
            write(os.path.join(ASSETS, f"models/block/tower/section_{k}_{color}.json"), section(k, color))
        write(os.path.join(ASSETS, f"models/block/tower/footing_{k}.json"), footing(k))
        parts.append({"when": {"above": str(k), "galvanized": "false", "band": "true"}, "apply": {"model": f"minepc:block/tower/section_{k}_red"}})
        parts.append({"when": {"above": str(k), "galvanized": "false", "band": "false"}, "apply": {"model": f"minepc:block/tower/section_{k}_white"}})
        parts.append({"when": {"above": str(k), "galvanized": "true"}, "apply": {"model": f"minepc:block/tower/section_{k}_grey"}})
        parts.append({"when": {"above": str(k), "height": "0"}, "apply": {"model": f"minepc:block/tower/footing_{k}"}})
    write(os.path.join(ASSETS, "blockstates/tower_section.json"), {"multipart": parts})
    write(os.path.join(ASSETS, "models/block/tower/pin.json"), pin())
    write(os.path.join(ASSETS, "models/block/tower/sector.json"), sector())
    write(os.path.join(ASSETS, "models/block/tower/dish.json"), dish())
    write(os.path.join(ASSETS, "blockstates/tower_pin.json"), {"variants": {"": {"model": "minepc:block/tower/pin"}}})
    write(os.path.join(ASSETS, "blockstates/tower_sector.json"), facing("minepc:block/tower/sector"))
    write(os.path.join(ASSETS, "blockstates/tower_dish.json"), facing("minepc:block/tower/dish"))
    icon("tower_section", draw_section)
    icon("tower_pin", draw_pin)
    icon("tower_sector", draw_sector)
    icon("tower_dish", draw_dish)
    for n in ("tower_section", "tower_pin", "tower_sector", "tower_dish"):
        loot(n)
    write(os.path.join(DATA, "recipe/tower_section.json"), {
        "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["I I", "BRB", "I I"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "B": {"item": "minecraft:iron_bars"}, "R": {"item": "minecraft:red_dye"}},
        "result": {"id": "minepc:tower_section", "count": 4}})
    write(os.path.join(DATA, "recipe/tower_pin.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minecraft:lightning_rod"}, {"item": "minecraft:iron_ingot"}, {"item": "minecraft:black_dye"},
                        {"item": "minecraft:redstone_torch"}],
        "result": {"id": "minepc:tower_pin", "count": 1}})
    write(os.path.join(DATA, "recipe/tower_sector.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["WQW", "WIW", "WRW"],
        "key": {"W": {"item": "minecraft:white_concrete"}, "Q": {"item": "minecraft:quartz"}, "I": {"item": "minecraft:iron_ingot"},
                "R": {"item": "minecraft:redstone"}},
        "result": {"id": "minepc:tower_sector", "count": 2}})
    write(os.path.join(DATA, "recipe/tower_dish.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["III", "IQI", " R "],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "Q": {"item": "minecraft:quartz"}, "R": {"item": "minecraft:redstone"}},
        "result": {"id": "minepc:tower_dish", "count": 1}})
    print("вышка: 17 ширин × 3 окраски, опоры, штырь, сектор, тарелка")


if __name__ == "__main__":
    main()
