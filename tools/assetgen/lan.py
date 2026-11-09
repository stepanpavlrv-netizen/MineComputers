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
    tint = f", некрашеный 0x{cases.paint_tint(paint):06X}" if paint else ""
    print(f"{name:16s} коробок {n_el}, поверх {n_glow}, габарит {bbox}{tint}")


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
    # Оптоволокно: гладкая оболочка с блестящей полоской посередине (красится цветом), разъём LC — бежевый с синим
    fiber = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 250 if (x + y) % 8 in (3, 4) else 218
            fiber.putpixel((x, y), (v, v, v, 255))
    fplug = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            c = (226, 214, 186) if (x // 4 + y // 4) % 2 else (210, 198, 170)
            if y % 8 in (0, 1):
                c = (60, 110, 200)
            fplug.putpixel((x, y), c + (255,))
    fiber.save(os.path.join(ASSETS, "textures/block/network_fiber.png"))
    fplug.save(os.path.join(ASSETS, "textures/block/network_fiber_plug.png"))


def coil_icon(color=(52, 104, 200), name="network_cable", plug=(196, 214, 222)):
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
            px(x, y, plug)
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
    im.save(os.path.join(ASSETS, "textures/item", name + ".png"))


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


from vox import Model, noise, glow  # noqa: E402

RADIO_BODY = (200, 202, 196)


def patch_panel():
    """Патч-панель для стойки: тёмная рама, спереди и сзади по 4 гнезда (по дорожкам), над гнёздами цветные метки."""
    m = Model()
    m.box(0, 0, 0, 16, 16, 16, noise((44, 47, 54), 3, 251))
    labels = [(230, 230, 236), (120, 200, 255), (255, 200, 90), (140, 230, 140)]
    for z in (15, 0):
        for k, x0 in enumerate((1, 5, 9, 13)):
            for x in (x0, x0 + 1):
                for y in (6, 7):
                    m.v[(x, y, z)] = (16, 16, 18)                    # гнездо
                m.v[(x, 9, z)] = labels[k]                           # метка
            m.v[(x0, 5, z)] = (90, 94, 100)
            m.v[(x0 + 1, 5, z)] = (90, 94, 100)
        for x in range(0, 16):                                       # уши стойки
            m.v[(x, 13, z)] = (70, 74, 82) if x in (0, 15) else m.v[(x, 13, z)]
        for x in (0, 15):
            for y in (2, 13):
                m.v[(x, y, z)] = (150, 154, 160)                     # винты
    return m


def radio_modem():
    """Радиомодем: светлый шкаф, окошко с лампочками, решётка, короткий штырь антенны сверху."""
    m = Model()
    m.box(2, 0, 3, 14, 12, 13, noise(RADIO_BODY, 3, 231))
    F = 12
    m.paint(lambda x, y, z: z == F and 4 <= x <= 11 and 6 <= y <= 9, (40, 44, 40))
    for i, x in enumerate(range(5, 11)):
        m.v[(x, 8, F)] = glow((80, 255, 120)) if i % 2 == 0 else glow((255, 170, 40))
    m.v[(5, 7, F)] = glow((255, 80, 60))
    m.paint(lambda x, y, z: z == F and y in (2, 3) and 4 <= x <= 11 and x % 2 == 0, (150, 152, 146))
    for y in range(12, 16):
        for (x, z) in ((7, 7), (8, 7), (7, 8), (8, 8)):
            m.v[(x, y, z)] = (170, 174, 180)
    return m


def mast(top):
    """Секция решётчатой мачты; у верхней — штырь антенны и красный огонь."""
    m = Model()
    steel, dark = (170, 174, 180), (145, 148, 154)
    h = 10 if top else 16
    for y in range(h):
        for (x, z) in ((6, 6), (9, 6), (6, 9), (9, 9)):
            m.v[(x, y, z)] = steel
        k = y % 6
        if k in (0, 3):
            for t in range(6, 10):
                m.v[(t, y, 6)] = steel; m.v[(t, y, 9)] = steel
                m.v[(6, y, t)] = steel; m.v[(9, y, t)] = steel
        else:
            d = 6 + (k if k < 3 else 6 - k)
            m.v[(d, y, 9)] = dark; m.v[(9, y, d)] = dark; m.v[(d, y, 6)] = dark; m.v[(6, y, d)] = dark
    if top:
        for y in range(10, 15):
            m.v[(7, y, 7)] = (60, 62, 66)
        for y in (4, 5, 6, 7):                                       # панель-антенна
            for x in (7, 8):
                m.v[(x, y, 10)] = (226, 228, 232)
        m.v[(7, 15, 7)] = glow((255, 40, 40))
    return m


def save_plain(name, model, variants):
    mj, img, bbox, n_el, n_glow = cases.build(name, model, folder="lan", rotate=False)
    img.save(os.path.join(ASSETS, "textures/block/lan", name + ".png"))
    write(os.path.join(ASSETS, "models/block", name + ".json"), mj)
    print(f"{name:16s} коробок {n_el}, поверх {n_glow}, габарит {bbox}")


def cord_icon():
    """Патч-корд: петля провода и две вилки."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def px(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            im.putpixel((x, y), tuple(c) + (255,))
    blue = (52, 104, 200)
    for a in range(0, 360, 3):
        x = 8 + 4.5 * math.cos(math.radians(a)); y = 9 + 3.5 * math.sin(math.radians(a))
        if not (200 < a < 340):
            px(int(round(x)), int(round(y)), blue if a % 12 else (40, 80, 170))
    for x0, y0 in ((2, 3), (11, 3)):
        for x in range(x0, x0 + 3):
            for y in range(y0, y0 + 3):
                px(x, y, (196, 214, 222))
        px(x0 + 1, y0, (232, 186, 70))
    px(4, 6, blue); px(4, 7, blue); px(11, 6, blue); px(11, 7, blue)
    src = im.copy()
    for x in range(16):
        for y in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), (16, 16, 20, 255))
    im.save(os.path.join(ASSETS, "textures/item/patch_cord.png"))
    write(os.path.join(ASSETS, "models/item/patch_cord.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": "minepc:item/patch_cord"}})


def tray_assets():
    """Кабельный лоток: сетчатая сталь (дырки прозрачные), модель для инвентаря — прямой кусок."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            grid = x % 4 == 0 or y % 4 == 0
            if edge or grid:
                v = 196 if edge else 168
                if (x + y) % 5 == 0:
                    v -= 18
                im.putpixel((x, y), (v, v + 2, v + 6, 255))
    im.save(os.path.join(ASSETS, "textures/block/cable_tray.png"))
    faces = {d: {"texture": "#mesh"} for d in ("north", "south", "east", "west", "up", "down")}
    write(os.path.join(ASSETS, "models/block/cable_tray.json"), {
        "render_type": "minecraft:cutout",
        "textures": {"particle": "minepc:block/cable_tray", "mesh": "minepc:block/cable_tray", "wire": "minepc:block/network_cable"},
        "elements": [
            {"from": [0, 4, 4], "to": [16, 12, 12], "faces": faces},
            {"from": [0, 4.2, 5], "to": [16, 4.9, 6], "faces": {d: {"texture": "#wire", "tintindex": 11} for d in ("up", "north", "south")}},
            {"from": [0, 4.2, 7], "to": [16, 4.9, 8], "faces": {d: {"texture": "#wire", "tintindex": 1} for d in ("up", "north", "south")}},
            {"from": [0, 4.2, 9], "to": [16, 4.9, 10], "faces": {d: {"texture": "#wire", "tintindex": 5} for d in ("up", "north", "south")}},
        ]})
    write(os.path.join(ASSETS, "blockstates/cable_tray.json"), {"variants": {"": {"model": "minepc:block/cable_tray"}}})
    write(os.path.join(ASSETS, "models/item/cable_tray.json"), {"parent": "minepc:block/cable_tray"})
    loot("cable_tray")
    write(os.path.join(DATA, "recipe/cable_tray.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["I I", "BBB"],
        "key": {"I": {"item": "minecraft:iron_nugget"}, "B": {"item": "minecraft:iron_bars"}},
        "result": {"id": "minepc:cable_tray", "count": 8}})


def loot(name):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def main():
    save_block("network_switch", netvox.switch(SWITCH_BODY), SWITCH_BODY)
    save_block("rack_switch", netvox.rack_switch(), RACK_BODY)
    save_block("wifi_router", netvox.router(ROUTER_BODY), ROUTER_BODY)
    save_block("patch_panel", patch_panel(), None)
    save_block("radio_modem", radio_modem(), RADIO_BODY)
    save_plain("radio_mast", mast(False), None)
    save_plain("radio_mast_top", mast(True), None)
    write(os.path.join(ASSETS, "blockstates/radio_mast.json"), {"variants": {
        "top=false": {"model": "minepc:block/radio_mast"}, "top=true": {"model": "minepc:block/radio_mast_top"}}})
    write(os.path.join(ASSETS, "models/item/radio_mast.json"), {"parent": "minepc:block/radio_mast_top"})
    loot("radio_mast")
    cord_icon()
    tray_assets()
    write(os.path.join(DATA, "recipe/patch_cord.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:network_cable"}, {"item": "minepc:network_cable"}, {"item": "minecraft:iron_nugget"}],
        "result": {"id": "minepc:patch_cord", "count": 2}})
    write(os.path.join(DATA, "recipe/patch_panel.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["III", "CCC", "III"],
        "key": {"I": {"item": "minecraft:iron_ingot"}, "C": {"item": "minepc:network_cable"}},
        "result": {"id": "minepc:patch_panel", "count": 1}})
    write(os.path.join(DATA, "recipe/radio_modem.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": [" L ", "IRI", "ISI"],
        "key": {"L": {"item": "minecraft:lightning_rod"}, "I": {"item": "minecraft:iron_ingot"},
                "R": {"item": "minecraft:redstone_block"}, "S": {"item": "minepc:network_switch"}},
        "result": {"id": "minepc:radio_modem", "count": 1}})
    write(os.path.join(DATA, "recipe/radio_mast.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["I I", "III", "I I"],
        "key": {"I": {"item": "minecraft:iron_bars"}},
        "result": {"id": "minepc:radio_mast", "count": 2}})
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
    coil_icon((236, 200, 50), "fiber_cable", (226, 214, 186))
    write(os.path.join(ASSETS, "models/item/fiber_cable.json"), {"parent": "minecraft:item/generated",
                                                                 "textures": {"layer0": "minepc:item/fiber_cable"}})
    write(os.path.join(DATA, "recipe/fiber_cable.json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["SSS", "GQG", "SSS"],
        "key": {"S": {"item": "minecraft:string"}, "G": {"item": "minecraft:glass_pane"}, "Q": {"item": "minecraft:quartz"}},
        "result": {"id": "minepc:fiber_cable", "count": 8}})
    # Кабель: модель собирает игра из проводов в блоке; здесь — только текстуры для атласа
    write(os.path.join(ASSETS, "models/block/network_cable.json"), {
        "textures": {"particle": "minepc:block/network_cable", "wire": "minepc:block/network_cable", "plug": "minepc:block/network_plug"},
        "elements": [{"from": [7, 0, 7], "to": [9, 2, 9], "faces": {"up": {"texture": "#wire"}, "north": {"texture": "#plug"}}}]})
    write(os.path.join(ASSETS, "blockstates/network_cable.json"), {"variants": {"": {"model": "minepc:block/network_cable"}}})
    write(os.path.join(ASSETS, "models/item/network_cable.json"), {"parent": "minecraft:item/generated",
                                                                   "textures": {"layer0": "minepc:item/network_cable"}})
    # Добычу (провода каждого вида и блок-маскировку) выдаёт сам блок кабеля
    write(os.path.join(DATA, "loot_table/blocks/network_cable.json"), {"type": "minecraft:block", "pools": []})
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
