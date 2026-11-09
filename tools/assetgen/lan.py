"""Сеть: модели коммутаторов (из воксельных эскизов looks/netvox.py), текстуры кабеля и вилки, иконка мотка,
blockstates, добыча и рецепты.

Запуск из корня проекта: python3 tools/assetgen/lan.py
"""
import json, math, os, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "looks"))
import cases  # noqa: E402
from cases import ASSETS, DATA, write  # noqa: E402
import netvox  # noqa: E402
from PIL import Image  # noqa: E402

SWITCH_BODY = (52, 56, 62)
RACK_BODY = (40, 43, 50)
ROUTER_BODY = (236, 238, 242)


def facing_variants(model):
    return {f"facing={f}": ({"model": model} | ({"y": y} if y else {}))
            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}


def save_block(name, model, paint):
    mj, img, bbox, n_el, n_glow = cases.build(name, model, folder="lan", paint=paint)
    os.makedirs(os.path.join(ASSETS, "textures/block/lan"), exist_ok=True)
    img.save(os.path.join(ASSETS, "textures/block/lan", name + ".png"))
    write(os.path.join(ASSETS, "models/block", name + ".json"), mj)
    write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": facing_variants(f"minepc:block/{name}")})
    write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": f"minepc:block/{name}"})
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    print(f"{name:16s} коробок {n_el}, поверх {n_glow}, габарит {bbox}, некрашеный 0x{cases.paint_tint(paint):06X}")


def cable_textures():
    """Провод: серые косые полосы (красится цветом провода). Вилка: прозрачный пластик с золотыми контактами."""
    r = random.Random(3)
    wire = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 236 if ((x + y) // 2) % 2 == 0 else 196
            v = int(v * (1 + 0.03 * r.random()))
            wire.putpixel((x, y), (min(255, v),) * 3 + (255,))
    plug = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            c = (196, 214, 222) if (x + y) % 3 else (176, 196, 206)
            if y % 4 == 0 and x % 2 == 0:
                c = (232, 186, 70)                       # контакты
            plug.putpixel((x, y), c + (255,))
    os.makedirs(os.path.join(ASSETS, "textures/block"), exist_ok=True)
    wire.save(os.path.join(ASSETS, "textures/block/network_cable.png"))
    plug.save(os.path.join(ASSETS, "textures/block/network_plug.png"))


def coil_icon(color=(52, 104, 200)):
    """Моток кабеля с вилкой RJ-45 (иконка предмета)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), tuple(c) + (255,))
    def sh(c, k):
        return tuple(max(0, min(255, int(v * k))) for v in c)
    for a in range(0, 360, 4):
        for rad, k in ((5.5, 1.0), (4.0, 0.85)):
            x = 7 + rad * math.cos(math.radians(a)); y = 8 + rad * 0.8 * math.sin(math.radians(a))
            px(int(round(x)), int(round(y)), sh(color, k))
    for x in range(11, 14):
        for y in range(3, 6):
            px(x, y, (196, 214, 222))
    for x in range(11, 14):
        px(x, 3, (232, 186, 70))
    px(12, 6, color); px(11, 7, color)
    src = im.copy()
    for x in range(16):                                   # тёмная обводка, как у предметов Minecraft
        for y in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), (20, 20, 24, 255))
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    im.save(os.path.join(ASSETS, "textures/item/network_cable.png"))


def wifi_icon(name, stripe, led):
    """USB Wi-Fi адаптер: тёмный корпус с антенной, серебряный разъём, цветная полоска поколения и лампочка."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def px(x, y, c):
        im.putpixel((x, y), tuple(c) + (255,))
    for x in range(5, 11):                      # корпус
        for y in range(5, 12):
            px(x, y, (48, 50, 58) if (x + y) % 5 else (40, 42, 48))
    for x in range(5, 11):
        px(x, 5, (70, 74, 84))
    for x in range(6, 10):                      # разъём USB
        for y in range(12, 16):
            px(x, y, (196, 200, 206) if x in (6, 9) or y == 15 else (150, 154, 160))
    px(7, 13, (40, 40, 44)); px(8, 13, (40, 40, 44))
    for y in range(0, 5):                       # антенна
        px(9, y, (30, 30, 34))
    px(9, 0, (70, 74, 84))
    for x in range(5, 11):                      # полоска поколения
        px(x, 9, stripe)
    px(6, 7, led)                               # лампочка
    src = im.copy()
    for x in range(16):
        for y in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), (16, 16, 20, 255))
    im.save(os.path.join(ASSETS, "textures/item", name + ".png"))
    write(os.path.join(ASSETS, "models/item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"minepc:item/{name}"}})


def main():
    save_block("network_switch", netvox.switch(SWITCH_BODY), SWITCH_BODY)
    save_block("rack_switch", netvox.rack_switch(), RACK_BODY)
    save_block("wifi_router", netvox.router(ROUTER_BODY), ROUTER_BODY)
    wifi_icon("wifi_adapter_g", (120, 128, 140), (80, 255, 120))
    wifi_icon("wifi_adapter_n", (52, 120, 230), (70, 150, 255))
    wifi_icon("wifi_adapter_ac", (230, 180, 60), (255, 220, 90))
    write(os.path.join(DATA, "recipe/wifi_router.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["L L", "QSQ"],
        "key": {"L": {"item": "minecraft:lightning_rod"}, "Q": {"item": "minecraft:quartz"}, "S": {"item": "minepc:network_switch"}},
        "result": {"id": "minepc:wifi_router", "count": 1}})
    write(os.path.join(DATA, "recipe/wifi_adapter_g.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minecraft:redstone"}, {"item": "minecraft:copper_ingot"}, {"item": "minecraft:iron_ingot"},
                        {"item": "minecraft:gold_nugget"}],
        "result": {"id": "minepc:wifi_adapter_g", "count": 1}})
    write(os.path.join(DATA, "recipe/wifi_adapter_n.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:wifi_adapter_g"}, {"item": "minecraft:gold_ingot"}, {"item": "minecraft:quartz"}],
        "result": {"id": "minepc:wifi_adapter_n", "count": 1}})
    write(os.path.join(DATA, "recipe/wifi_adapter_ac.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:wifi_adapter_n"}, {"item": "minecraft:diamond"}, {"item": "minecraft:amethyst_shard"}],
        "result": {"id": "minepc:wifi_adapter_ac", "count": 1}})
    cable_textures()
    coil_icon()
    # Кабель: модель собирает игра из проводов в блоке; здесь — только текстуры для атласа
    write(os.path.join(ASSETS, "models/block/network_cable.json"), {
        "textures": {"particle": "minepc:block/network_cable", "wire": "minepc:block/network_cable", "plug": "minepc:block/network_plug"},
        "elements": [{"from": [7, 0, 7], "to": [9, 2, 9], "faces": {"up": {"texture": "#wire"}, "north": {"texture": "#plug"}}}]})
    write(os.path.join(ASSETS, "blockstates/network_cable.json"), {"variants": {"": {"model": "minepc:block/network_cable"}}})
    write(os.path.join(ASSETS, "models/item/network_cable.json"), {"parent": "minecraft:item/generated",
                                                                   "textures": {"layer0": "minepc:item/network_cable"}})
    write(os.path.join(DATA, "loot_table/blocks/network_cable.json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minepc:network_cable"}]}]})
    # Рецепты: медь и нитки → 8 кабеля; коммутаторы
    write(os.path.join(DATA, "recipe/network_cable.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["SSS", "CCC", "SSS"],
        "key": {"S": {"item": "minecraft:string"}, "C": {"item": "minecraft:copper_ingot"}},
        "result": {"id": "minepc:network_cable", "count": 8}})
    write(os.path.join(DATA, "recipe/network_switch.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": [" R ", "CGC", "III"],
        "key": {"R": {"item": "minecraft:redstone"}, "C": {"item": "minepc:network_cable"},
                "G": {"item": "minecraft:gold_ingot"}, "I": {"item": "minecraft:iron_ingot"}},
        "result": {"id": "minepc:network_switch", "count": 1}})
    write(os.path.join(DATA, "recipe/rack_switch.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:network_switch"}, {"item": "minepc:network_switch"},
                        {"item": "minecraft:iron_block"}, {"item": "minecraft:quartz"}],
        "result": {"id": "minepc:rack_switch", "count": 1}})


if __name__ == "__main__":
    main()
