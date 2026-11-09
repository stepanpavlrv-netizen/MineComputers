"""Корпуса-варианты: воксельные эскизы (tools/assetgen/looks) → модели блоков Minecraft.

Каждый эскиз — воксели 16×16×16 (1 воксель = 1 пиксель блока). Модель собирается из небольшого
числа коробок (жадное разбиение по заполненности), а цвета вокселей рисуются в текстуру граней —
так сохраняются и мелкие детали, и шум материала. Светящиеся пиксели (лампочки, подсветка)
получают отдельные тонкие плашки с полной яркостью (neoforge_data.block_light).

Запуск из корня проекта: python3 tools/assetgen/cases.py
Пишет: blockstates, models/block, models/item, textures/block/case, loot_table, recipe, cases.json (для Java).
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "looks"))
from PIL import Image
import models, models2  # noqa: E402

ROOT = os.path.join(HERE, "..", "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/minepc")
DATA = os.path.join(ROOT, "src/main/resources/data/minepc")


def recolor(model, mapping):
    """Перекрасить воксели: близкие к исходному цвету → новый (с сохранением оттенка-шума)."""
    for k, c in list(model.v.items()):
        if len(c) > 3:
            continue
        for src, dst in mapping:
            if sum(abs(a - b) for a, b in zip(c, src)) < 40:
                model.v[k] = tuple(max(0, min(255, d + (a - s))) for a, s, d in zip(c, src, dst))
                break
    return model


def gaming_white():
    return recolor(models.case_gaming(), [((26, 26, 31), (232, 234, 238)), ((44, 44, 52), (200, 202, 208)), ((12, 12, 16), (180, 182, 188))])


# id: (эскиз, тема окна, рецепт: что добавить к обычному корпусу)
CASES = {
    "case_retro_tower": (models.case_office, "RETRO", "minecraft:birch_planks"),
    "case_retro_desktop": (models2.retro_desktop, "RETRO", "minecraft:birch_slab"),
    "case_home80": (models.case_home80, "RETRO", "minepc:keyboard"),
    "case_office_tower": (models2.office_tower, "OFFICE", "minecraft:black_dye"),
    "case_mini": (models.case_mini, "OFFICE", "minecraft:iron_ingot"),
    "case_workstation": (models.case_workstation, "OFFICE", "minecraft:iron_bars"),
    "case_gaming": (models.case_gaming, "GAMING", "minecraft:glass_pane"),
    "case_gaming_white": (gaming_white, "GAMING", "minecraft:white_stained_glass_pane"),
    "case_gaming_cube": (models2.gaming_cube, "GAMING", "minecraft:amethyst_shard"),
    "case_industrial": (models2.ind_cabinet, "INDUSTRIAL", "minecraft:iron_block"),
    "case_server": (models.case_server, "SERVER", "minecraft:chain"),
}


def rotated(m):
    """Эскиз смотрит передом на +z, блок Minecraft (facing=north) — на −z: поворот на 180°."""
    return {(15 - x, y, 15 - z): c for (x, y, z), c in m.v.items()}


def boxes(vox):
    """Жадно разбить заполненные воксели на коробки (x0, y0, z0, x1, y1, z1), по возможности крупные."""
    left = set(vox)
    out = []
    while left:
        x0, y0, z0 = min(left, key=lambda k: (k[1], k[2], k[0]))
        best = None
        for order in ("xzy", "zxy", "xyz", "yxz", "zyx", "yzx"):
            lo, hi = [x0, y0, z0], [x0 + 1, y0 + 1, z0 + 1]
            for ax in order:
                a = "xyz".index(ax)
                while True:
                    hi2 = hi[:]
                    hi2[a] += 1
                    ok = True
                    for x in range(lo[0], hi2[0]):
                        for y in range(lo[1], hi2[1]):
                            for z in range(lo[2], hi2[2]):
                                if (x, y, z) not in left:
                                    ok = False
                                    break
                            if not ok:
                                break
                        if not ok:
                            break
                    if not ok:
                        break
                    hi = hi2
            vol = (hi[0] - lo[0]) * (hi[1] - lo[1]) * (hi[2] - lo[2])
            if best is None or vol > best[0]:
                best = (vol, tuple(lo + hi))
        b = best[1]
        out.append(b)
        for x in range(b[0], b[3]):
            for y in range(b[1], b[4]):
                for z in range(b[2], b[5]):
                    left.discard((x, y, z))
    return out


DIRS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def face_pixels(b, face):
    """Пиксели грани коробки: [(col, row, voxel)], ширина, высота — в раскладке UV Minecraft."""
    x0, y0, z0, x1, y1, z1 = b
    px = []
    if face == "north":
        w, h = x1 - x0, y1 - y0
        for x in range(x0, x1):
            for y in range(y0, y1):
                px.append((x1 - 1 - x, y1 - 1 - y, (x, y, z0)))
    elif face == "south":
        w, h = x1 - x0, y1 - y0
        for x in range(x0, x1):
            for y in range(y0, y1):
                px.append((x - x0, y1 - 1 - y, (x, y, z1 - 1)))
    elif face == "west":
        w, h = z1 - z0, y1 - y0
        for z in range(z0, z1):
            for y in range(y0, y1):
                px.append((z - z0, y1 - 1 - y, (x0, y, z)))
    elif face == "east":
        w, h = z1 - z0, y1 - y0
        for z in range(z0, z1):
            for y in range(y0, y1):
                px.append((z1 - 1 - z, y1 - 1 - y, (x1 - 1, y, z)))
    elif face == "up":
        w, h = x1 - x0, z1 - z0
        for x in range(x0, x1):
            for z in range(z0, z1):
                px.append((x - x0, z - z0, (x, y1 - 1, z)))
    else:
        w, h = x1 - x0, z1 - z0
        for x in range(x0, x1):
            for z in range(z0, z1):
                px.append((x - x0, z1 - 1 - z, (x, y0, z)))
    return px, w, h


def face_uv_rect(b, face):
    """Где грань коробки лежит в координатах блока (для плашек подсветки)."""
    x0, y0, z0, x1, y1, z1 = b
    return {"north": z0, "south": z1, "west": x0, "east": x1, "down": y0, "up": y1}[face]


class Atlas:
    """Полочная упаковка прямоугольников граней в одну текстуру."""

    def __init__(self, size):
        self.size = size
        self.x = self.y = self.row = 0
        self.img = Image.new("RGBA", (size, size), (0, 0, 0, 0))

    def place(self, w, h):
        if self.x + w > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        if self.y + h > self.size:
            return None
        p = (self.x, self.y)
        self.x += w
        self.row = max(self.row, h)
        return p


def r4(v):
    return round(v, 4)


def build(name, model, folder="case", rotate=True):
    vox = rotated(model) if rotate else dict(model.v)
    bxs = boxes(set(vox))
    faces = []
    for b in bxs:
        for face, (dx, dy, dz) in DIRS.items():
            px, w, h = face_pixels(b, face)
            vis = [(c, r, v) for c, r, v in px if (v[0] + dx, v[1] + dy, v[2] + dz) not in vox]
            if vis:
                faces.append((b, face, px, w, h, {(c, r) for c, r, _ in vis}))
    # Атлас: самые высокие грани первыми
    order = sorted(range(len(faces)), key=lambda i: (-faces[i][4], -faces[i][3]))
    for size in (32, 64, 128, 256):
        atlas = Atlas(size)
        pos = {}
        for i in order:
            p = atlas.place(faces[i][3], faces[i][4])
            if p is None:
                break
            pos[i] = p
        if len(pos) == len(faces):
            break
    k = 16 / atlas.size
    elements = {}
    glow_elems = []
    for i, (b, face, px, w, h, vis) in enumerate(faces):
        ax, ay = pos[i]
        for c, r, v in px:
            col = vox[v]
            atlas.img.putpixel((ax + c, ay + r), tuple(col[:3]) + (255,))
        el = elements.setdefault(b, {"from": [b[0], b[1], b[2]], "to": [b[3], b[4], b[5]], "faces": {}})
        f = {"uv": [r4(ax * k), r4(ay * k), r4((ax + w) * k), r4((ay + h) * k)], "texture": "#all"}
        edge = face_uv_rect(b, face)
        if edge in (0, 16) and ((face in ("north", "west", "down") and edge == 0) or (face in ("south", "east", "up") and edge == 16)):
            f["cullface"] = face
        el["faces"][face] = f
        # Светящиеся пиксели: отрезки по строкам грани
        rows = {}
        for c, r, v in px:
            if (c, r) in vis and len(vox[v]) > 3:
                rows.setdefault(r, []).append(c)
        for r, cols in rows.items():
            cols.sort()
            start = prev = cols[0]
            for c in cols[1:] + [None]:
                if c is not None and c == prev + 1:
                    prev = c
                    continue
                glow_elems.append(glow_quad(b, face, start, prev, r, ax, ay, k, w, h))
                if c is not None:
                    start = prev = c
    els = list(elements.values()) + glow_elems
    tex = f"minepc:block/{folder}/{name}"
    model_json = {"parent": "minecraft:block/block", "textures": {"particle": tex, "all": tex}, "elements": els}
    lo = [min(b[i] for b in bxs) for i in range(3)]
    hi = [max(b[i + 3] for b in bxs) for i in range(3)]
    return model_json, atlas.img, lo + hi, len(elements), len(glow_elems)


def glow_quad(b, face, c0, c1, r, ax, ay, k, w, h):
    """Плашка толщиной 0 чуть перед гранью: пиксели c0..c1 строки r светятся в темноте."""
    x0, y0, z0, x1, y1, z1 = b
    e = 0.01
    if face == "north":
        frm, to = [x1 - 1 - c1, y1 - 1 - r, z0 - e], [x1 - c0, y1 - r, z0 - e]
    elif face == "south":
        frm, to = [x0 + c0, y1 - 1 - r, z1 + e], [x0 + c1 + 1, y1 - r, z1 + e]
    elif face == "west":
        frm, to = [x0 - e, y1 - 1 - r, z0 + c0], [x0 - e, y1 - r, z0 + c1 + 1]
    elif face == "east":
        frm, to = [x1 + e, y1 - 1 - r, z1 - 1 - c1], [x1 + e, y1 - r, z1 - c0]
    elif face == "up":
        frm, to = [x0 + c0, y1 + e, z0 + r], [x0 + c1 + 1, y1 + e, z0 + r + 1]
    else:
        frm, to = [x0 + c0, y0 - e, z1 - 1 - r], [x0 + c1 + 1, y0 - e, z1 - r]
    uv = [r4((ax + c0) * k), r4((ay + r) * k), r4((ax + c1 + 1) * k), r4((ay + r + 1) * k)]
    return {"from": [r4(v) for v in frm], "to": [r4(v) for v in to], "shade": False,
            "neoforge_data": {"block_light": 15, "sky_light": 15},
            "faces": {face: {"uv": uv, "texture": "#all"}}}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, indent=1)
        f.write("\n")


def main():
    table = []
    for name, (fn, style, extra) in CASES.items():
        model_json, img, bbox, n_el, n_glow = build(name, fn())
        os.makedirs(os.path.join(ASSETS, "textures/block/case"), exist_ok=True)
        img.save(os.path.join(ASSETS, "textures/block/case", name + ".png"))
        write(os.path.join(ASSETS, "models/block", name + ".json"), model_json)
        write(os.path.join(ASSETS, "models/item", name + ".json"), {"parent": f"minepc:block/{name}"})
        write(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            f"facing={f}": ({"model": f"minepc:block/{name}"} | ({"y": y} if y else {}))
            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{name}"}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        write(os.path.join(DATA, "recipe", name + ".json"), {
            "type": "minecraft:crafting_shapeless", "category": "redstone",
            "ingredients": [{"item": "minepc:pc_case"}, {"item": extra}],
            "result": {"id": f"minepc:{name}", "count": 1}})
        write(os.path.join(DATA, "recipe", name + "_back.json"), {
            "type": "minecraft:crafting_shapeless", "category": "redstone",
            "ingredients": [{"item": f"minepc:{name}"}],
            "result": {"id": "minepc:pc_case", "count": 1}})
        table.append({"id": name, "style": style, "box": bbox})
        print(f"{name:20s} коробок {n_el:3d}, подсветка {n_glow:3d}, текстура {img.size[0]}, габарит {bbox}")
    write(os.path.join(HERE, "cases.json"), table)


if __name__ == "__main__":
    main()
