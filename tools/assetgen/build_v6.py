"""Сборка ассетов v6: материалы, модели всех вариантов (по списку из Java-теста), сборочный стол."""
import sys, os, json, math, shutil, random
sys.path.insert(0, os.path.dirname(__file__))
from models6 import BUILDERS
from models3d import rotate
from materials5 import materials, mobo_face_v5
from materials import case_tray
from px import Canvas, noise, hx

root, dump_path = sys.argv[1], sys.argv[2]
A = f"{root}/src/main/resources/assets/minepc"
D = f"{root}/src/main/resources/data"
T = f"{A}/textures"
dump = json.load(open(dump_path))
COMBOS = {s["type"]: [tuple(v) for v in s["visuals"]] for s in dump["specs"]}


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, separators=(",", ":"), ensure_ascii=False)


# ================= текстуры =================
for d in ("item/icon", "item/mat", "item/mobo"):
    shutil.rmtree(f"{T}/{d}", ignore_errors=True)
os.makedirs(f"{T}/item/mat", exist_ok=True); os.makedirs(f"{T}/item/mobo", exist_ok=True)
mats = materials()

pcb = Canvas()
noise(pcb, 0, 0, 15, 15, "d6d8db", 3, 200)
rnd = random.Random(201)
for _ in range(7):
    y = rnd.randint(1, 14); x0 = rnd.randint(0, 9); pcb.hline(x0, min(15, x0 + rnd.randint(3, 7)), y, "c3c6ca")
for _ in range(5):
    pcb.px(rnd.randint(1, 14), rnd.randint(1, 14), "eceef0")
mats["pcb"] = pcb
for name, im in mats.items():
    im.save(f"{T}/item/mat/{name}.png")

# нейтральная лицевая сторона платы (красится цветом текстолита)
face = mobo_face_v5(4)
fp = face.im.load()
silk = hx("8d9196")[:3]
for x in range(32):
    for y in range(32):
        if fp[x, y][:3] == silk:
            fp[x, y] = (246, 247, 248, 255)
face.save(f"{T}/item/mobo/face.png")
os.makedirs(f"{T}/block", exist_ok=True)
case_tray().save(f"{T}/block/case_tray.png")

# --- сборочный стол ---
esd = Canvas()
noise(esd, 0, 0, 15, 15, "2f6f86", 3, 210)
for i in range(0, 16, 4):
    esd.hline(0, 15, i, "2a6478"); esd.vline(i, 0, 15, "2a6478")
esd.px(14, 14, "c9a243"); esd.px(13, 14, "8a8f97")             # кнопка заземления
esd.save(f"{T}/block/esd_mat.png")
top = Canvas()
noise(top, 0, 0, 15, 15, "3a3d44", 2, 211)
top.hline(0, 15, 0, "50545c"); top.hline(0, 15, 15, "2a2c31")
top.save(f"{T}/block/table_top.png")
leg = Canvas()
noise(leg, 0, 0, 15, 15, "2a2c31", 2, 212)
leg.vline(0, 0, 15, "3a3d44")
leg.save(f"{T}/block/table_leg.png")
bp = Canvas()
noise(bp, 0, 0, 15, 15, "2b4f86", 2, 213)
for x in range(2, 14):
    bp.px(x, 2, "c8d8f0"); bp.px(x, 13, "c8d8f0")
for y in range(2, 14):
    bp.px(2, y, "c8d8f0"); bp.px(13, y, "c8d8f0")
bp.rect(6, 6, 9, 9, "9fb8e0"); bp.hline(4, 11, 4, "9fb8e0"); bp.vline(4, 6, 11, "9fb8e0")
bp.save(f"{T}/block/blueprint.png")
tray = Canvas()
noise(tray, 0, 0, 15, 15, "1e1f23", 2, 214)
for x in (5, 10):
    tray.vline(x, 0, 15, "4a4e56")
tray.hline(0, 15, 8, "4a4e56")
for (x, y, c) in ((2, 3, "c9a243"), (7, 2, "b8bcc4"), (12, 4, "c8784a"), (3, 11, "40a0e0"), (8, 12, "b8bcc4")):
    tray.px(x, y, c)
tray.save(f"{T}/block/parts_tray.png")


# ================= модели деталей =================
shutil.rmtree(f"{A}/models/item", ignore_errors=True)
PARTICLE = "minepc:item/mat/metal"


def euler_from_view(d, u):
    def norm(v):
        l = math.sqrt(sum(x * x for x in v)); return [x / l for x in v]
    def cross(a, b):
        return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
    d = norm(d)
    u = norm([u[i] - d[i] * sum(u[j] * d[j] for j in range(3)) for i in range(3)])
    r = cross(u, d)
    R = [r, u, d]
    b = math.asin(max(-1, min(1, R[0][2])))
    a = math.atan2(-R[1][2], R[2][2])
    c = math.atan2(-R[0][1], R[0][0])
    return tuple(round(math.degrees(x), 2) for x in (a, b, c))


V = euler_from_view
UP = (0, 1, 0)


def hero(t, v):
    if t == "MOTHERBOARD": return V((1, .35, -.45), UP)
    if t == "CPU": return V((1, .7, -.5), UP)
    if t == "COOLER":
        return [V((1, .6, -.6), UP), V((1, .5, -.6), UP), V((.45, .3, -1), (1, 0, 0)),
                V((.55, .35, -1), (1, 0, 0)), V((.75, -.55, -1), UP)][v[0]]
    if t == "RAM": return V((.3, .25, -1), (1, 0, 0))
    if t == "GPU": return V((.2, -1, -.45), (1, 0, 0))
    if t == "PSU": return V((.8, -.65, -.6), UP)
    if t == "HDD": return V((.45, 1, -.35), (0, 0, 1))
    if t == "SSD": return V((1, .4, -.6), UP) if v[0] == 0 else V((1, .5, -.35), UP)


PERSPECTIVES = ["gui", "ground", "fixed", "head", "thirdperson_righthand", "thirdperson_lefthand",
                "firstperson_righthand", "firstperson_lefthand"]


def showcase(part, rot):
    els = part["elements"]
    lo = [min(e["from"][i] for e in els) for i in range(3)]
    hi = [max(e["to"][i] for e in els) for i in range(3)]
    c = [(lo[i] + hi[i]) / 2 for i in range(3)]
    H = 0.0
    for e in els:
        for cx in (e["from"][0], e["to"][0]):
            for cy in (e["from"][1], e["to"][1]):
                for cz in (e["from"][2], e["to"][2]):
                    v = rotate([cx - c[0], cy - c[1], cz - c[2]], rot)
                    H = max(H, abs(v[0]), abs(v[1]))
    half = max((hi[i] - lo[i]) / 2 for i in range(3))
    k = min(7.3 / H, 23.5 / half)
    new = [{"from": [round(8 + k * (e["from"][i] - c[i]), 4) for i in range(3)],
            "to": [round(8 + k * (e["to"][i] - c[i]), 4) for i in range(3)], "faces": e["faces"]} for e in els]
    return {"textures": part["textures"], "elements": new, "display": {
        "gui": {"rotation": list(rot), "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.3, .3, .3]},
        "fixed": {"rotation": list(rot), "translation": [0, 0, 0], "scale": [.6, .6, .6]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 12, 0], "scale": [.6, .6, .6]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375] * 3},
        "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [.375] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [.4] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [.4] * 3}}}


count = 0
for t, combos in COMBOS.items():
    name, build = BUILDERS[t]
    combos = sorted(combos)
    for v in combos:
        tag = f"{v[0]}_{v[1]}_{v[2]}"
        m = build(*v)
        part = {"textures": dict(m.textures, particle=PARTICLE), "elements": m.elements}
        write(f"{A}/models/item/{name}/part_{tag}.json", part)
        write(f"{A}/models/item/{name}/show_{tag}.json", showcase(part, hero(t, v)))
        wrapper = {"loader": "neoforge:separate_transforms", "textures": {"particle": PARTICLE},
                   "base": {"parent": f"minepc:item/{name}/part_{tag}"},
                   "perspectives": {p: {"parent": f"minepc:item/{name}/show_{tag}"} for p in PERSPECTIVES}}
        write(f"{A}/models/item/{name}/{tag}.json", wrapper)
        count += 1
    main = {"loader": "neoforge:separate_transforms", "textures": {"particle": PARTICLE},
            "base": {"parent": f"minepc:item/{name}/part_{combos[0][0]}_{combos[0][1]}_{combos[0][2]}"},
            "perspectives": {p: {"parent": f"minepc:item/{name}/show_{combos[0][0]}_{combos[0][1]}_{combos[0][2]}"}
                             for p in PERSPECTIVES},
            # Свойства v0..v2 задаёт Java (ClientEvents). Списки отсортированы: побеждает последнее подходящее.
            "overrides": [{"predicate": {"minepc:v0": round(v[0] * .1, 1), "minepc:v1": round(v[1] * .1, 1),
                                         "minepc:v2": round(v[2] * .1, 1)},
                           "model": f"minepc:item/{name}/{v[0]}_{v[1]}_{v[2]}"} for v in combos[1:]]}
    write(f"{A}/models/item/{name}.json", main)
write(f"{A}/models/item/pc_case.json", {"parent": "minepc:block/pc_case"})
write(f"{A}/models/item/assembly_table.json", {"parent": "minepc:block/assembly_table"})


# ================= сборочный стол: модель, состояния, лут, рецепты =================
def f(tex, **extra):
    d = {"texture": f"#{tex}"}; d.update(extra); return d


table = {"parent": "minecraft:block/block", "textures": {
    "particle": "minepc:block/table_top", "top": "minepc:block/table_top", "leg": "minepc:block/table_leg",
    "mat": "minepc:block/esd_mat", "tray": "minepc:block/parts_tray", "paper": "minepc:block/blueprint",
    "red": "minepc:item/mat/audio_cap", "steel": "minepc:item/mat/silver"},
    "elements": []}
E = table["elements"]


def el(frm, to, tex, faces=("north", "south", "east", "west", "up", "down"), uv_up=None):
    fs = {d: f(tex) for d in faces}
    if uv_up and "up" in fs:
        fs["up"]["uv"] = uv_up
    E.append({"from": frm, "to": to, "faces": fs})


for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
    el([x, 0, z], [x + 2, 12, z + 2], "leg")
el([1, 10, 3], [3, 11, 13], "leg"); el([13, 10, 3], [15, 11, 13], "leg")
el([0, 12, 0], [16, 14, 16], "top")
el([1, 14, 1], [15, 14.2, 12], "mat")
el([10, 14.2, 2], [14, 14.25, 7], "paper")
el([2, 14, 12.5], [8, 14.8, 15.5], "tray")
el([9, 14.2, 9], [12, 14.6, 9.4], "red")       # ручка отвёртки
el([12, 14.3, 9.1], [14.5, 14.45, 9.3], "steel")  # жало
write(f"{A}/models/block/assembly_table.json", table)
write(f"{A}/blockstates/assembly_table.json", {"variants": {
    "facing=north": {"model": "minepc:block/assembly_table"},
    "facing=east": {"model": "minepc:block/assembly_table", "y": 90},
    "facing=south": {"model": "minepc:block/assembly_table", "y": 180},
    "facing=west": {"model": "minepc:block/assembly_table", "y": 270}}})
for blk in ("pc_case", "assembly_table"):
    write(f"{D}/minepc/loot_table/blocks/{blk}.json", {"type": "minecraft:block", "pools": [{
        "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minepc:{blk}"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
write(f"{D}/minecraft/tags/block/mineable/pickaxe.json", {"values": ["minepc:pc_case", "minepc:assembly_table"]})
write(f"{D}/minepc/recipe/assembly_table.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["CCC", "ITI", "I I"],
    "key": {"C": {"item": "minecraft:light_blue_carpet"}, "I": {"item": "minecraft:iron_ingot"},
            "T": {"item": "minecraft:crafting_table"}},
    "result": {"id": "minepc:assembly_table", "count": 1}})
write(f"{D}/minepc/recipe/pc_case.json", {
    "type": "minecraft:crafting_shaped", "category": "misc",
    "pattern": ["III", "G R", "III"],
    "key": {"I": {"item": "minecraft:iron_ingot"}, "G": {"item": "minecraft:glass_pane"},
            "R": {"item": "minecraft:redstone"}},
    "result": {"id": "minepc:pc_case", "count": 1}})
print("models:", count, "textures:", len(os.listdir(f"{T}/item/mat")))
