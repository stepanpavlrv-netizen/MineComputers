"""Удобства сети (0.29): бирка на кабеле, сетевая розетка, потолочная точка доступа, кабельный тестер.
Текстуры, модели, blockstates, иконки, добыча и рецепты.

Запуск из корня проекта: python3 tools/assetgen/lan2.py
"""
import math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from cases import ASSETS, DATA, write  # noqa: E402
from PIL import Image  # noqa: E402

WHITE = (238, 240, 243)
EDGE = (206, 210, 216)
HOLE = (36, 38, 44)
BLUE = (70, 160, 255)


def save(im, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def outline(im, color=(16, 16, 20, 255)):
    src = im.copy()
    for x in range(16):
        for y in range(16):
            if src.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and src.getpixel((x + dx, y + dy))[3]
                                                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                im.putpixel((x, y), color)


def tag_texture():
    """Бирка: белый пластик с серой «строчкой» надписи."""
    im = Image.new("RGBA", (16, 16), WHITE + (255,))
    for x in range(16):
        for y in range(16):
            if y % 4 == 2 and 2 <= x <= 13 and (x + y) % 3:
                im.putpixel((x, y), (150, 154, 162, 255))
    save(im, "block/cable_tag")


def loot(name):
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})


def item_model(name):
    write(os.path.join(ASSETS, "models/item", name + ".json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"minepc:item/{name}"}})


def face_variants(model):
    """Как у кнопки: пол, стена, потолок × 4 стороны (модель — лёжа на полу, лицом вверх)."""
    out = {}
    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        out[f"face=floor,facing={f}"] = {"model": model} | ({"y": y} if y else {})
        out[f"face=wall,facing={f}"] = {"model": model, "x": 90} | ({"y": y} if y else {})
        out[f"face=ceiling,facing={f}"] = {"model": model, "x": 180, "y": (y + 180) % 360}
    return {"variants": out}


def outlet():
    """Розетка: белая пластина 8×8, два гнезда (3×3, тёмные, с золотыми контактами) — линии A и B."""
    im = Image.new("RGBA", (16, 16), WHITE + (255,))
    for x in range(4, 12):
        for y in range(4, 12):
            if x in (4, 11) or y in (4, 11):
                im.putpixel((x, y), EDGE + (255,))
    for x0 in (4, 9):                      # гнёзда: x 4..6 и 9..11, z 7..9 (середины 5.5 и 10.5, как гнёзда в коде)
        for x in range(x0, x0 + 3):
            for y in range(7, 10):
                im.putpixel((x, y), HOLE + (255,))
        im.putpixel((x0 + 1, 7), (220, 180, 70, 255))
    save(im, "block/network_outlet")
    side = {"texture": "#face", "uv": [4, 0, 12, 2]}
    write(os.path.join(ASSETS, "models/block/network_outlet.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minepc:block/network_outlet", "face": "minepc:block/network_outlet"},
        "elements": [{"from": [4, 0, 4], "to": [12, 2, 12], "faces": {
            "up": {"texture": "#face"}, "down": {"texture": "#face", "uv": [0, 0, 2, 2]},
            "north": side, "south": side, "east": side, "west": side}}]})
    write(os.path.join(ASSETS, "blockstates/network_outlet.json"), face_variants("minepc:block/network_outlet"))
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(2, 14):
        for y in range(2, 14):
            icon.putpixel((x, y), (EDGE if x in (2, 13) or y in (2, 13) else WHITE) + (255,))
    for x0 in (4, 9):
        for x in range(x0, x0 + 3):
            for y in range(6, 10):
                icon.putpixel((x, y), HOLE + (255,))
        icon.putpixel((x0 + 1, 6), (220, 180, 70, 255))
    outline(icon)
    save(icon, "item/network_outlet")
    item_model("network_outlet")
    loot("network_outlet")
    write(os.path.join(DATA, "recipe/network_outlet.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minecraft:quartz"}, {"item": "minepc:network_cable"}, {"item": "minecraft:iron_nugget"}],
        "result": {"id": "minepc:network_outlet", "count": 2}})


def access_point():
    """Точка доступа: белая «шайба» под потолком, снизу синее кольцо и серая лампочка посередине."""
    im = Image.new("RGBA", (16, 16), WHITE + (255,))
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if 3.6 <= d <= 4.7:
                im.putpixel((x, y), BLUE + (255,))
            elif d > 6.2:
                im.putpixel((x, y), EDGE + (255,))
    for x in (7, 8):
        for y in (7, 8):
            im.putpixel((x, y), (200, 204, 210, 255))
    save(im, "block/wifi_access_point")
    plain = {"texture": "#body"}
    write(os.path.join(ASSETS, "models/block/wifi_access_point.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": "minepc:block/wifi_access_point", "face": "minepc:block/wifi_access_point",
                     "body": "minecraft:block/white_concrete"},
        "elements": [
            {"from": [4, 14, 3], "to": [12, 16, 13], "faces": {
                "down": {"texture": "#face"}, "north": plain, "south": plain, "east": plain, "west": plain, "up": plain}},
            {"from": [3, 14.2, 4], "to": [13, 16, 12], "faces": {
                "down": {"texture": "#face"}, "north": plain, "south": plain, "east": plain, "west": plain, "up": plain}},
            {"from": [5.5, 13.6, 5.5], "to": [10.5, 14, 10.5], "faces": {
                "down": {"texture": "#face", "uv": [6, 6, 10, 10]}, "north": plain, "south": plain, "east": plain, "west": plain}},
        ]})
    write(os.path.join(ASSETS, "blockstates/wifi_access_point.json"), {"variants": {"": {"model": "minepc:block/wifi_access_point"}}})
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= 6.6:
                c = BLUE if 3.4 <= d <= 4.6 else (EDGE if d > 5.6 else WHITE)
                icon.putpixel((x, y), c + (255,))
    outline(icon)
    save(icon, "item/wifi_access_point")
    item_model("wifi_access_point")
    loot("wifi_access_point")
    write(os.path.join(DATA, "recipe/wifi_access_point.json"), {
        "type": "minecraft:crafting_shapeless", "category": "redstone",
        "ingredients": [{"item": "minepc:wifi_router"}, {"item": "minecraft:quartz"}, {"item": "minecraft:glowstone_dust"}],
        "result": {"id": "minepc:wifi_access_point", "count": 1}})


def tester():
    """Кабельный тестер: жёлтый резиновый корпус, тёмный экранчик с зелёными цифрами, кнопка и щуп с вилкой сверху."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    def px(x, y, c):
        im.putpixel((x, y), tuple(c) + (255,))
    for x in range(4, 12):
        for y in range(4, 15):
            px(x, y, (232, 168, 30) if (x + y) % 6 else (214, 150, 24))
    for x in range(5, 11):
        for y in range(5, 9):
            px(x, y, (22, 38, 26))
    for x in (6, 7, 9):
        px(x, 6, (130, 240, 130))
    px(6, 7, (130, 240, 130)); px(8, 7, (130, 240, 130))
    px(7, 11, (60, 64, 70)); px(8, 11, (60, 64, 70))           # кнопка
    px(6, 13, (200, 60, 50)); px(9, 13, (80, 200, 90))          # лампочки
    for y in range(1, 4):                                       # провод и вилка
        px(7, y, (52, 104, 200))
    for x in (6, 7, 8):
        px(x, 0, (196, 214, 222))
    outline(im)
    save(im, "item/cable_tester")
    item_model("cable_tester")
    write(os.path.join(DATA, "recipe/cable_tester.json"), {
        "type": "minecraft:crafting_shaped", "category": "tools", "pattern": [" C ", "RGR", " Y "],
        "key": {"C": {"item": "minepc:network_cable"}, "R": {"item": "minecraft:redstone"},
                "G": {"item": "minecraft:glass_pane"}, "Y": {"item": "minecraft:yellow_dye"}},
        "result": {"id": "minepc:cable_tester", "count": 1}})


def main():
    tag_texture()
    outlet()
    access_point()
    tester()
    print("готово: бирка, розетка, точка доступа, тестер")


if __name__ == "__main__":
    main()
