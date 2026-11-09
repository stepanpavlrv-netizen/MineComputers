"""Мониторы и клавиатуры новых видов: воксельные эскизы → модели блоков (как cases.py).

Экран в модели — тёмное «выключенное» стекло; картинку поверх рисует MonitorRenderer.
Где экран лежит в блоке, печатается в конце (это числа для MonitorKind.java).

Запуск из корня проекта: python3 tools/assetgen/monitors.py
"""
import json, os, random, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "looks"))
import cases  # noqa: E402
from cases import ASSETS, DATA, write  # noqa: E402
import models, models2  # noqa: E402
from vox import Model, noise  # noqa: E402


def off_screen(m, x0, x1, y0, y1, z, base, scan=False, seed=1):
    """Заменить картинку на выключенный экран: тёмное стекло с лёгким бликом."""
    r = random.Random(seed)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            k = 1.0 + 0.06 * r.random()
            if (x - x0) + (y1 - y) < 3:
                k += 0.25                                  # блик в углу
            if scan and y % 2 == 0:
                k *= 0.85
            m.v[(x, y, z)] = tuple(min(255, int(c * k)) for c in base)


def crt():
    m = models.mon_crt()
    m.remove(4, 5, 15, 12, 12, 16)                         # выпуклое стекло — картинка рисуется плоской
    off_screen(m, 3, 12, 4, 12, 14, (26, 38, 30), scan=True, seed=2)
    return m, (3, 12, 4, 12, 14)


def tv():
    m = models2.retro_tv()
    off_screen(m, 2, 9, 3, 11, 13, (36, 38, 40), scan=True, seed=3)
    return m, (2, 9, 3, 11, 13)


def terminal():
    m = models.mon_terminal()
    off_screen(m, 3, 12, 5, 12, 10, (10, 22, 12), seed=4)
    return m, (3, 12, 5, 12, 10)


def panel():
    """Промышленная панель вешается на стену: в эскизе она у передней грани, в блоке — у задней."""
    src = models.mon_panel()
    m = Model()
    for (x, y, z), c in src.v.items():
        m.v[(x, y, z - 13)] = c
    off_screen(m, 3, 12, 4, 13, 1, (44, 26, 8), seed=5)
    return m, (3, 12, 4, 13, 1)


def hanging():
    """Навесная клавиатура: полка на кронштейнах; задняя сторона (z = 0 в эскизе) — к монитору."""
    src = models2.hanging_keyboard()
    m = Model()
    for (x, y, z), c in src.v.items():
        m.v[(x, y + 3, z)] = c
    return m


def stand():
    return models2.kb_stand()


def block_body(body, glass, scan=False, seed=1):
    """Блочный монитор без рамки: бока корпуса и стекло во всю переднюю грань (рамку дорисуют соседи)."""
    m = Model()
    m.box(0, 0, 0, 16, 16, 15, noise(body, 3, seed))
    r = random.Random(seed)
    for x in range(16):
        for y in range(16):
            k = 1.0 + 0.05 * r.random()
            if scan and y % 2 == 0:
                k *= 0.85
            m.v[(x, y, 15)] = tuple(min(255, int(c * k)) for c in glass)
    return m


BLOCKS = {   # стиль: (корпус, стекло, рамка, развёртка)
    "monitor_block": ((40, 42, 48), (14, 15, 18), (22, 23, 27), False),
    "monitor_block_white": ((226, 228, 232), (24, 26, 30), (208, 211, 216), False),
    "monitor_block_retro": ((214, 205, 178), (20, 32, 24), (196, 187, 160), True),
}

SINGLE = {"monitor_crt": crt, "monitor_tv": tv, "monitor_terminal": terminal, "monitor_panel": panel}
PAINT = {"monitor_crt": (210, 202, 176), "monitor_terminal": (60, 62, 66), "monitor_panel": (92, 96, 84)}


LCD_BODY = (30, 30, 34)


def lcd(stand):
    """Плоский монитор (уже в координатах блока, смотрящего на север): панель, стекло, ножка и подставка."""
    m = Model()
    y0 = 2 if stand else 0
    m.box(0, y0, 11, 16, 16, 14, noise(LCD_BODY, 2, 3 if stand else 4))
    r = random.Random(5)
    for x in range(1, 15):
        for y in range(3, 15):
            k = 1.0 + 0.06 * r.random() + (0.25 if (14 - x) + (14 - y) < 3 else 0)
            m.v[(x, y, 11)] = tuple(min(255, int(c * k)) for c in (14, 15, 18))
    m.v[(2, 2 if not stand else 2, 11)] = (80, 160, 255, "glow")
    if stand:
        m.box(7, 1, 14, 9, 9, 15, (50, 50, 56))
        m.box(4, 0, 9, 12, 1, 16, (44, 44, 50))
    return m


def zoff(x):
    return int(round(((x - 15.5) ** 2) / 70))


def wide_full():
    m = models.mon_wide()
    off_screen(m, 2, 29, 5, 14, 9, (14, 15, 18), seed=6)
    return m


def curved_full():
    m = models.mon_curved()
    r = random.Random(7)
    for x in range(1, 31):
        for y in range(5, 15):
            k = 1.0 + 0.06 * r.random()
            m.v[(x, y, 8 + zoff(x))] = tuple(min(255, int(c * k)) for c in (14, 15, 18))
    return m


def half(full, right):
    """Половина двухблочного монитора: левая (x 0..15 эскиза) или правая (x 16..31), как видит зритель."""
    m = Model()
    for (x, y, z), c in full.v.items():
        if (x >= 16) == right:
            m.v[(x - 16 if right else x, y, z)] = c
    return m


WIDE = {   # имя: (эскиз, цвет основы, экран (x0, x1, y0, y1, z) в эскизе 32×16)
    "monitor_wide": (wide_full, (24, 24, 28), (2, 29, 5, 14, 9)),
    "monitor_curved": (curved_full, (22, 22, 26), (1, 30, 5, 14, 8)),
}


def facing_variants(model, extra=""):
    return {f"facing={f}{extra}": ({"model": model} | ({"y": y} if y else {}))
            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}


def loot(name, extra_pools=()):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}] + list(extra_pools)})


def recipe(name, a, b, back):
    write(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": a}, {"item": b}], "result": {"id": f"minepc:{name}", "count": 1}})
    write(os.path.join(DATA, "recipe", name + "_back.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": f"minepc:{name}"}], "result": {"id": back, "count": 1}})


RECIPES = {
    "monitor_crt": "minecraft:birch_planks", "monitor_tv": "minecraft:dark_oak_planks", "monitor_terminal": "minepc:keyboard",
    "monitor_panel": "minecraft:iron_ingot", "monitor_block": "minecraft:black_concrete",
    "monitor_block_white": "minecraft:white_concrete", "monitor_block_retro": "minecraft:smooth_sandstone",
}


TINTS = {}   # блок -> цвет-множитель некрашеного (для Java)


def save_model(name, fn_model, paint=None, rotate=True, tint_key=None):
    mj, img, bbox, n_el, n_glow = cases.build(name, fn_model, folder="monitor", rotate=rotate, paint=paint)
    if paint:
        TINTS[tint_key or name] = "0x%06X" % cases.paint_tint(paint)
    os.makedirs(os.path.join(ASSETS, "textures/block/monitor"), exist_ok=True)
    img.save(os.path.join(ASSETS, "textures/block/monitor", name + ".png"))
    write(os.path.join(ASSETS, "models/block", name + ".json"), mj)
    return bbox, n_el, n_glow


def rim_model(name, color, body):
    """Рамка блочного монитора с одной стороны (для корпуса, смотрящего на север; «лево» — как видит зритель)."""
    from PIL import Image
    r = random.Random(len(name))
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            k = 1.0 + 0.05 * r.random()
            g = min(255, int(cases.paint_scale(body) * sum(color) / sum(body) * k))   # серый: красится, как корпус
            img.putpixel((x, y), (g, g, g, 255))
    img.save(os.path.join(ASSETS, "textures/block/monitor", name + "_rim.png"))
    tex = f"minepc:block/monitor/{name}_rim"
    lip = 0.25
    parts = {"left": ([15, 0, -lip], [16, 16, 0]), "right": ([0, 0, -lip], [1, 16, 0]),
             "up": ([0, 15, -lip], [16, 16, 0]), "down": ([0, 0, -lip], [16, 1, 0])}
    for side, (frm, to) in parts.items():
        faces = {}
        for f in ("north", "east", "west", "up", "down"):
            faces[f] = {"uv": [0, 0, 1, 1] if f != "north" else [frm[0], 16 - to[1], to[0], 16 - frm[1]], "texture": "#rim", "tintindex": 0}
        write(os.path.join(ASSETS, "models/block", f"{name}_rim_{side}.json"), {
            "textures": {"rim": tex, "particle": tex},
            "elements": [{"from": frm, "to": to, "faces": faces}]})


def main():
    specs = {}
    for name, fn in SINGLE.items():
        model, (x0, x1, y0, y1, F) = fn()
        bbox, n_el, n_glow = save_model(name, model, paint=PAINT.get(name))
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": facing_variants(f"minepc:block/{name}")})
        write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": f"minepc:block/{name}"})
        loot(name)
        recipe(name, "minepc:monitor", RECIPES[name], "minepc:monitor")
        # экран: u слева, v сверху (как видит зритель), z — передняя грань у монитора, смотрящего на север
        specs[name] = {"uL": x0, "uR": x1 + 1, "vT": 15 - y1, "vB": 16 - y0, "z": 15 - F, "box": bbox}
        print(f"{name:22s} коробок {n_el:3d}, подсветка {n_glow:2d}, экран {specs[name]}")

    for name, (body, glass, rim, scan) in BLOCKS.items():
        save_model(name, block_body(body, glass, scan, seed=len(name)), paint=body)
        rim_model(name, rim, body)
        mp = []
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            rot = {"y": y} if y else {}
            mp.append({"when": {"facing": f}, "apply": {"model": f"minepc:block/{name}"} | rot})
            for side in ("left", "right", "up", "down"):
                mp.append({"when": {"facing": f, side: "false"}, "apply": {"model": f"minepc:block/{name}_rim_{side}"} | rot})
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"multipart": mp})
        write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": f"minepc:block/{name}"})
        loot(name)
        recipe(name, "minepc:monitor", RECIPES[name], "minepc:monitor")
        print(f"{name:22s} блочный")

    # Клавиатура: на полу или навесная (на грани монитора); клавиатурная стойка
    bbox, n_el, _ = save_model("keyboard_wall", hanging())
    print(f"keyboard_wall          коробок {n_el}, габарит {bbox}")
    ks = json.load(open(os.path.join(ASSETS, "blockstates/keyboard.json")))
    variants = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        rot = {"y": y} if y else {}
        variants[f"facing={f},mount=floor"] = {"model": "minepc:block/keyboard"} | rot
        variants[f"facing={f},mount=wall"] = {"model": "minepc:block/keyboard_wall"} | rot
    write(os.path.join(ASSETS, "blockstates/keyboard.json"), {"variants": variants})

    bbox, n_el, _ = save_model("keyboard_stand", stand())
    print(f"keyboard_stand         коробок {n_el}, габарит {bbox}")
    write(os.path.join(ASSETS, "blockstates/keyboard_stand.json"), {"variants": {
        k: v for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        for k, v in ((f"facing={f},mount=floor", {"model": "minepc:block/keyboard_stand"} | ({"y": y} if y else {})),
                     (f"facing={f},mount=wall", {"model": "minepc:block/keyboard_stand"} | ({"y": y} if y else {})))}})
    write(os.path.join(ASSETS, "models/item/keyboard_stand.json"), {"parent": "minepc:block/keyboard_stand"})
    loot("keyboard_stand")
    recipe("keyboard_stand", "minepc:keyboard", "minecraft:iron_bars", "minepc:keyboard")

    # Плоский монитор из вокселей (красится)
    save_model("monitor", lcd(True), paint=LCD_BODY, rotate=False)
    save_model("monitor_top", lcd(False), paint=LCD_BODY, rotate=False, tint_key="monitor")

    # Мониторы в два блока: левая и правая половины, у предмета — обе
    for name, (fn, body, (x0, x1, y0, y1, F)) in WIDE.items():
        full = fn()
        boxes = []
        for side in ("left", "right"):
            bbox, n_el, _ = save_model(f"{name}_{side}", half(full, side == "right"), paint=body, tint_key=name)
            boxes.append(bbox)
        variants = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for side in ("left", "right"):
                variants[f"facing={f},half={side}"] = {"model": f"minepc:block/{name}_{side}"} | ({"y": y} if y else {})
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": variants})
        # Предмет: обе половины рядом (правая — к −x у смотрящего на север), по центру и поменьше
        els, tex = [], {}
        for side, dx in (("left", 8), ("right", -8)):
            mj = json.load(open(os.path.join(ASSETS, "models/block", f"{name}_{side}.json")))
            tex[side] = mj["textures"]["all"]
            for el in mj["elements"]:
                el = json.loads(json.dumps(el))
                el["from"][0] = round(el["from"][0] + dx, 4)
                el["to"][0] = round(el["to"][0] + dx, 4)
                for fc in el["faces"].values():
                    fc["texture"] = "#" + side
                    fc.pop("cullface", None)
                els.append(el)
        write(os.path.join(ASSETS, "models/item", name + ".json"), {
            "parent": "minecraft:block/block", "textures": tex | {"particle": tex["left"]}, "elements": els,
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.42, 0.42, 0.42]},
                        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
                        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
                        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}})
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"},
                                      {"condition": "minecraft:block_state_property", "block": f"minepc:{name}",
                                       "properties": {"half": "left"}}]}]})
        specs[name] = {"uL": x0, "uR": x1 + 1 - 16, "vT": 15 - y1, "vB": 16 - y0, "z": 15 - F,
                       "left": boxes[0], "right": boxes[1]}
        print(f"{name:22s} экран {specs[name]}")
    write(os.path.join(DATA, "recipe", "monitor_wide.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:monitor"}, {"item": "minepc:monitor"}], "result": {"id": "minepc:monitor_wide", "count": 1}})
    write(os.path.join(DATA, "recipe", "monitor_curved.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:monitor_wide"}, {"item": "minecraft:red_dye"}], "result": {"id": "minepc:monitor_curved", "count": 1}})
    write(os.path.join(DATA, "recipe", "monitor_wide_back.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:monitor_wide"}], "result": {"id": "minepc:monitor", "count": 2}})
    write(os.path.join(DATA, "recipe", "monitor_curved_back.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:monitor_curved"}], "result": {"id": "minepc:monitor_wide", "count": 1}})

    # Клавиатура внутри блока плоского монитора — перед подставкой
    kb = json.load(open(os.path.join(ASSETS, "models/block/keyboard.json")))
    el = json.loads(json.dumps(kb["elements"][0]))
    el["from"], el["to"] = [1, 0, 1], [15, 1.5, 9]
    for f in el["faces"].values():
        f.pop("cullface", None)
    write(os.path.join(ASSETS, "models/block/monitor_keyboard.json"), {"textures": kb["textures"], "elements": [el]})
    ms = json.load(open(os.path.join(ASSETS, "blockstates/monitor.json")))
    mp = []
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        rot = {"y": y} if y else {}
        mp.append({"when": {"facing": f, "stand": "true"}, "apply": {"model": "minepc:block/monitor"} | rot})
        mp.append({"when": {"facing": f, "stand": "false"}, "apply": {"model": "minepc:block/monitor_top"} | rot})
        mp.append({"when": {"facing": f, "keyboard": "true"}, "apply": {"model": "minepc:block/monitor_keyboard"} | rot})
    write(os.path.join(ASSETS, "blockstates/monitor.json"), {"multipart": mp})
    # Монитор с клавиатурой при поломке отдаёт и её
    loot("monitor", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minepc:keyboard"}],
                      "conditions": [{"condition": "minecraft:block_state_property", "block": "minepc:monitor",
                                      "properties": {"keyboard": "true"}}]}])
    write(os.path.join(HERE, "monitors.json"), specs | {"tints": TINTS})


if __name__ == "__main__":
    main()
