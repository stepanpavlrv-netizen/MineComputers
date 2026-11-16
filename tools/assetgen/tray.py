"""Кабельный лоток (вариант А «лестничный»): текстура, части модели, multipart-blockstate, предмет, добыча, рецепт.

Лоток шириной 10 пикселей (боковины на x=3 и x=12, внутри — 8 дорожек для проводов x=4..11), высотой 3 пикселя,
перекладины через 3 пикселя. Части нарисованы для лотка на полу; на потолок и стены их поворачивает blockstate
(как у блока TrayBlock в коде: потолок — x=180, стены — x=90 и поворот по y).

Свойства блока: face (к чему прикреплён: down — пол, up — потолок, north/south/east/west — стена),
n/e/s/w — связь по сторонам лотка в его собственной раскладке (none — закрыто боковиной, flat — продолжается,
bend — у лотка на полу поднимается на стену, у лотка на потолке — опускается на стену).

Запуск из корня проекта: python tools/assetgen/tray.py
"""
import os, sys, random

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from cases import ASSETS, DATA, write  # noqa: E402
from PIL import Image  # noqa: E402

NAME = "cable_tray"
TEX = "minepc:block/" + NAME

# Поворот частей лотка: (x, y) у blockstate — как TrayBlock.ROT в коде
FACES = {"down": (0, 0), "up": (180, 0), "south": (90, 0), "west": (90, 90), "north": (90, 180), "east": (90, 270)}
SIDES = ("n", "e", "s", "w")


def texture():
    """Оцинковка: светло-серая сталь с пятнами «блёсток» (в оттенках серого — красится через tintindex)."""
    rnd = random.Random(7)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    for x in range(16):
        for y in range(16):
            v = 190 + rnd.randint(-6, 6)
            if (x // 3 + y // 2 * 3 + x * y) % 7 == 0:
                v += 14                      # светлое пятнышко оцинковки
            im.putpixel((x, y), (v, v, min(255, v + 4), 255))
    # кромка (верх боковины, торцы перекладин) — чуть темнее
    for x in range(16):
        im.putpixel((x, 15), (150, 152, 156, 255))
    im.save(os.path.join(ASSETS, "textures/block", NAME + ".png"))


def box(f, t):
    faces = {d: {"texture": "#t", "tintindex": 0} for d in ("north", "south", "east", "west", "up", "down")}
    return {"from": list(f), "to": list(t), "faces": faces}


def rot_y(e, n):
    """Повернуть коробку на n×90° по часовой (вид сверху): север → восток → юг → запад."""
    (x0, y0, z0), (x1, y1, z1) = e
    for _ in range(n):
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return (x0, y0, z0), (x1, y1, z1)


# Части для стороны «север» (−Z); остальные стороны — повороты
ARM = [((3, 0, 0), (4, 3, 3)), ((12, 0, 0), (13, 3, 3)), ((4, 0, 1), (12, 1, 2))]          # боковины до края и перекладина
RAIL = [((3, 0, 3), (13, 3, 4))]                                                            # закрытая сторона — поперечная боковина
BEND = [((3, 0, 0), (4, 16, 3)), ((12, 0, 0), (13, 16, 3))] + \
       [((4, y, 0), (12, y + 1, 1)) for y in (4, 7, 10, 13)]                               # подъём на стену: боковины вверх
BARS_X = [((4, 0, z), (12, 1, z + 1)) for z in (4, 7, 10)]                                 # перекладины поперёк хода север–юг
BARS_Z = [((x, 0, 4), (x + 1, 1, 12)) for x in (4, 7, 10)]                                  # поперёк хода запад–восток
POSTS = [((x, 0, z), (x + 1, 3, z + 1)) for x in (3, 12) for z in (3, 12)]                  # стойки по углам (стыки боковин)
SIDE_RAILS_NS = [((3, 0, 3), (4, 3, 13)), ((12, 0, 3), (13, 3, 13))]                        # боковины вдоль хода север–юг


def part(name, boxes):
    write(os.path.join(ASSETS, "models/block/tray", name + ".json"), {
        "render_type": "minecraft:cutout",
        "textures": {"t": TEX, "particle": TEX},
        "elements": [box(f, t) for f, t in boxes]})
    return "minepc:block/tray/" + name


def models():
    names = {}
    for i, s in enumerate(SIDES):
        names["arm_" + s] = part("arm_" + s, [rot_y(e, i) for e in ARM])
        names["rail_" + s] = part("rail_" + s, [rot_y(e, i) for e in RAIL])
        names["bend_" + s] = part("bend_" + s, [rot_y(e, i) for e in BEND])
    names["bars_x"] = part("bars_x", BARS_X)
    names["bars_z"] = part("bars_z", BARS_Z)
    names["posts"] = part("posts", POSTS)
    # Предмет и образец для view.py: прямой кусок север–юг
    write(os.path.join(ASSETS, "models/block", NAME + ".json"), {
        "parent": "block/block", "render_type": "minecraft:cutout",
        "textures": {"t": TEX, "particle": TEX},
        "elements": [box(f, t) for f, t in ARM + [rot_y(e, 2) for e in ARM] + SIDE_RAILS_NS + BARS_X]})
    write(os.path.join(ASSETS, "models/item", NAME + ".json"), {"parent": "minepc:block/" + NAME})
    return names


def blockstate(names):
    parts = []
    for face, (rx, ry) in FACES.items():
        def apply(model):
            a = {"model": model}
            if rx:
                a["x"] = rx
            if ry:
                a["y"] = ry
            return a
        for s in SIDES:
            parts.append({"when": {"face": face, s: "none"}, "apply": apply(names["rail_" + s])})
            parts.append({"when": {"face": face, s: "flat|bend"}, "apply": apply(names["arm_" + s])})
            if face in ("down", "up"):
                parts.append({"when": {"face": face, s: "bend"}, "apply": apply(names["bend_" + s])})
        parts.append({"when": {"face": face}, "apply": apply(names["posts"])})
        # перекладины: поперёк хода; на развилке и у одиночного кусочка — сеткой в обе стороны
        parts.append({"when": {"OR": [{"face": face, "n": "flat|bend"}, {"face": face, "s": "flat|bend"},
                                      {"face": face, "e": "none", "w": "none"}]}, "apply": apply(names["bars_x"])})
        parts.append({"when": {"OR": [{"face": face, "e": "flat|bend"}, {"face": face, "w": "flat|bend"}]},
                      "apply": apply(names["bars_z"])})
    write(os.path.join(ASSETS, "blockstates", NAME + ".json"), {"multipart": parts})


def data():
    write(os.path.join(DATA, "loot_table/blocks", NAME + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "minepc:" + NAME}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write(os.path.join(DATA, "recipe", NAME + ".json"), {
        "type": "minecraft:crafting_shaped", "category": "redstone", "pattern": ["I I", "BBB"],
        "key": {"I": {"item": "minecraft:iron_nugget"}, "B": {"item": "minecraft:iron_bars"}},
        "result": {"id": "minepc:" + NAME, "count": 8}})


def previews(names, out):
    """Образцы для view.py (не для игры): прямой, угол, тройник, крестовина, подъём — на полу."""
    os.makedirs(out, exist_ok=True)
    def m(name, parts):
        elements = []
        for p in parts:
            import json
            with open(os.path.join(ASSETS, "models/block/tray", p.split("/")[-1] + ".json"), encoding="utf-8") as f:
                elements += json.load(f)["elements"]
        write(os.path.join(out, name + ".json"), {"textures": {"t": TEX, "particle": TEX}, "elements": elements})
    m("straight", ["posts", "arm_n", "arm_s", "rail_e", "rail_w", "bars_x"])
    m("corner", ["posts", "arm_n", "arm_e", "rail_s", "rail_w", "bars_x", "bars_z"])
    m("tee", ["posts", "arm_n", "arm_e", "arm_s", "rail_w", "bars_x", "bars_z"])
    m("cross", ["posts", "arm_n", "arm_e", "arm_s", "arm_w", "bars_x", "bars_z"])
    m("rise", ["posts", "arm_n", "bend_n", "arm_s", "rail_e", "rail_w", "bars_x"])


def main():
    texture()
    names = models()
    blockstate(names)
    data()
    if "--preview" in sys.argv:
        previews(names, sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
