"""Модели, текстуры, рецепты и таблицы добычи для периферии: монитор, клавиатура, дисковод,
дискета, флешка. Запуск из корня репозитория:
    python tools/assetgen/build_devices.py
"""
import json, os, random
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
A = os.path.join(ROOT, "src/main/resources/assets/minepc")
D = os.path.join(ROOT, "src/main/resources/data/minepc")


def save_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)


def img(w=16, h=16, color=(0, 0, 0, 0)):
    return Image.new("RGBA", (w, h), color)


def noise(im, amount, seed):
    rnd = random.Random(seed)
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            d = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)
    return im


def rect(im, x0, y0, x1, y1, c):
    px = im.load()
    for x in range(x0, x1):
        for y in range(y0, y1):
            if 0 <= x < im.width and 0 <= y < im.height:
                px[x, y] = c


def save_png(im, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)


PLASTIC = (46, 49, 55, 255)
PLASTIC_DARK = (32, 34, 39, 255)
PLASTIC_LIGHT = (62, 66, 74, 255)

# ---------------- текстуры ----------------
# Корпус монитора (задняя стенка, бока, подставка)
t = noise(img(16, 16, PLASTIC), 4, 1)
rect(t, 0, 0, 16, 1, PLASTIC_LIGHT)
for y in (5, 8, 11):
    rect(t, 4, y, 12, y + 1, PLASTIC_DARK)   # вентиляционные прорези
save_png(t, "block/monitor_back")

# Лицевая сторона: рамка 1 px и тёмное стекло (используется область 16×14)
t = img(16, 16, PLASTIC_DARK)
rect(t, 1, 1, 15, 13, (10, 12, 15, 255))
px = t.load()
for i in range(5):                    # лёгкий блик в углу
    if 2 + i < 15 and 1 + i < 13:
        px[2 + i, 1 + i] = (24, 28, 34, 255)
        px[3 + i, 1 + i] = (18, 21, 26, 255)
px[14, 14] = (90, 200, 120, 255)      # светодиод питания на нижней рамке
save_png(t, "block/monitor_front")

# Клавиатура: верх с клавишами
t = img(16, 16, PLASTIC_DARK)
key, key_dark = (78, 82, 90, 255), (58, 61, 68, 255)
for row in range(4):
    y = 1 + row * 2
    for col in range(7):
        rect(t, 1 + col * 2, y, 2 + col * 2, y + 1, key)
rect(t, 3, 9, 13, 10, key)             # пробел
for x in range(16):
    for y in range(16):
        if px is not None:
            pass
save_png(noise(t, 2, 3), "block/keyboard_top")
save_png(noise(img(16, 16, PLASTIC_DARK), 3, 4), "block/keyboard_side")

# Дисковод: корпус и передняя панель (без дискеты и с дискетой)
save_png(noise(img(16, 16, (176, 172, 160, 255)), 5, 5), "block/disk_drive_side")
for has_disk in (False, True):
    t = noise(img(16, 16, (186, 182, 170, 255)), 4, 6)
    rect(t, 0, 0, 16, 1, (206, 202, 190, 255))
    rect(t, 2, 3, 14, 5, (40, 40, 44, 255))                  # щель дисковода
    if has_disk:
        rect(t, 3, 3, 13, 4, (52, 70, 120, 255))             # торчащий край дискеты
    rect(t, 11, 6, 13, 7, (90, 220, 110, 255) if has_disk else (70, 80, 72, 255))  # светодиод
    rect(t, 3, 6, 6, 7, (150, 146, 136, 255))                # кнопка извлечения
    save_png(t, "block/disk_drive_front_disk" if has_disk else "block/disk_drive_front")

# Дискета 3.5"
t = img()
rect(t, 2, 2, 14, 14, (44, 72, 140, 255))
rect(t, 2, 2, 14, 3, (64, 96, 170, 255))
rect(t, 5, 2, 11, 6, (190, 192, 198, 255))                   # металлическая шторка
rect(t, 8, 3, 10, 5, (120, 122, 128, 255))
rect(t, 4, 8, 12, 14, (232, 232, 224, 255))                  # наклейка
for y in (10, 12):
    rect(t, 5, y, 11, y + 1, (150, 150, 160, 255))
rect(t, 13, 13, 14, 14, (30, 30, 30, 0))                     # срезанный угол
save_png(t, "item/floppy_disk")

# Флешка
t = img()
rect(t, 6, 1, 10, 5, (200, 200, 205, 255))                   # разъём USB
rect(t, 7, 2, 8, 3, (60, 60, 64, 255))
rect(t, 8, 2, 9, 3, (60, 60, 64, 255))
rect(t, 5, 5, 11, 15, (40, 120, 200, 255))                   # корпус
rect(t, 5, 5, 11, 6, (70, 150, 225, 255))
rect(t, 7, 12, 9, 13, (240, 240, 120, 255))                  # светодиод
save_png(t, "item/flash_drive")

# ---------------- модели ----------------
def face(tex, uv=None, cull=None):
    f = {"texture": tex}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(tex, front=None, front_uv=None):
    out = {d: face(tex) for d in ("north", "south", "east", "west", "up", "down")}
    if front:
        out["north"] = face(front, front_uv)
    return out


monitor = {
    "parent": "block/block",
    "textures": {"particle": "minepc:block/monitor_back", "back": "minepc:block/monitor_back",
                 "front": "minepc:block/monitor_front"},
    "elements": [
        box([0, 2, 11], [16, 16, 14], all_faces("#back", "#front", [0, 0, 16, 14])),
        box([7, 1, 14], [9, 9, 15], all_faces("#back")),
        box([4, 0, 9], [12, 1, 16], all_faces("#back")),
    ],
}
save_json(os.path.join(A, "models/block/monitor.json"), monitor)

keyboard = {
    "parent": "block/block",
    "textures": {"particle": "minepc:block/keyboard_side", "top": "minepc:block/keyboard_top",
                 "side": "minepc:block/keyboard_side"},
    "elements": [
        box([1, 0, 4], [15, 1.5, 12], {
            "up": face("#top", [1, 0, 15, 11]),
            "down": face("#side"), "north": face("#side"), "south": face("#side"),
            "east": face("#side"), "west": face("#side")}),
    ],
}
save_json(os.path.join(A, "models/block/keyboard.json"), keyboard)

for suffix, front in (("", "disk_drive_front"), ("_disk", "disk_drive_front_disk")):
    model = {
        "parent": "block/block",
        "textures": {"particle": "minepc:block/disk_drive_side", "side": "minepc:block/disk_drive_side",
                     "front": "minepc:block/" + front},
        "elements": [box([1, 0, 1], [15, 8, 15], {
            "north": face("#front", [1, 0, 15, 8]), "south": face("#side"), "east": face("#side"),
            "west": face("#side"), "up": face("#side"), "down": face("#side", cull="down")})],
    }
    save_json(os.path.join(A, f"models/block/disk_drive{suffix}.json"), model)

ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def variant(model, f):
    v = {"model": model}
    if ROT[f]:
        v["y"] = ROT[f]
    return v


save_json(os.path.join(A, "blockstates/monitor.json"),
          {"variants": {f"facing={f}": variant("minepc:block/monitor", f) for f in ROT}})
save_json(os.path.join(A, "blockstates/keyboard.json"),
          {"variants": {f"facing={f}": variant("minepc:block/keyboard", f) for f in ROT}})
save_json(os.path.join(A, "blockstates/disk_drive.json"), {"variants": {
    f"disk={d},facing={f}": variant("minepc:block/disk_drive" + ("_disk" if d == "true" else ""), f)
    for f in ROT for d in ("false", "true")}})

for name in ("monitor", "keyboard", "disk_drive"):
    save_json(os.path.join(A, f"models/item/{name}.json"), {"parent": f"minepc:block/{name}"})
for name in ("floppy_disk", "flash_drive"):
    save_json(os.path.join(A, f"models/item/{name}.json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"minepc:item/{name}"}})

# ---------------- рецепты и добыча ----------------
def shaped(result, pattern, key, count=1):
    return {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
            "key": {k: {"item": v} for k, v in key.items()}, "result": {"id": result, "count": count}}


save_json(os.path.join(D, "recipe/monitor.json"), shaped("minepc:monitor", ["III", "IGI", " R "],
          {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone"}))
save_json(os.path.join(D, "recipe/keyboard.json"), shaped("minepc:keyboard", ["BBB", "SRS"],
          {"B": "minecraft:stone_button", "S": "minecraft:smooth_stone_slab", "R": "minecraft:redstone"}))
save_json(os.path.join(D, "recipe/disk_drive.json"), shaped("minepc:disk_drive", ["III", "R  ", "III"],
          {"I": "minecraft:iron_ingot", "R": "minecraft:redstone"}))
save_json(os.path.join(D, "recipe/floppy_disk.json"), shaped("minepc:floppy_disk", ["P", "R"],
          {"P": "minecraft:paper", "R": "minecraft:redstone"}))
save_json(os.path.join(D, "recipe/flash_drive.json"), shaped("minepc:flash_drive", [" I ", "GRG", " G "],
          {"I": "minecraft:iron_ingot", "G": "minecraft:gold_nugget", "R": "minecraft:redstone"}))

for name in ("monitor", "keyboard", "disk_drive"):
    save_json(os.path.join(D, f"loot_table/blocks/{name}.json"), {"type": "minecraft:block", "pools": [
        {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
         "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

tag = os.path.join(ROOT, "src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json")
values = json.load(open(tag))["values"]
for name in ("monitor", "keyboard", "disk_drive"):
    if f"minepc:{name}" not in values:
        values.append(f"minepc:{name}")
save_json(tag, {"values": values})
print("ok")
