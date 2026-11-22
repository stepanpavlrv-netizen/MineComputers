"""Карты уровней «Разлома» без игры: проверка и вид сверху.

    python tools/razview/maps.py [номер ...]      → tools/razview/shots/map_N.png и отчёт

Проверяет: каждый враг и предмет стоит в проходимой клетке на её полу (око — в воздухе), игрок может дойти
от старта до ключ-карт и выхода (шаг — 0,45 м, прыжок — до 1 м, вниз — сколько угодно, двери — когда есть
карта, прыжковые площадки — по их толчку). Нужны lupa и pillow.
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont
import lupa.lua52 as lua52

HERE = os.path.dirname(os.path.abspath(__file__))
LEVELS = os.path.normpath(os.path.join(HERE, "../../src/main/resources/data/minepc/computer/disks/razlom/app/levels"))
BASE = {".": dict(f=0, c=4), ",": dict(f=0, c=9), "~": dict(f=-0.8, c=9, acid=True)}
KEYS = {"keyRed": "red", "keyYellow": "yellow", "keyBlue": "blue"}
ENEMY = {"guard", "gunner", "eye", "scientist", "crawler", "turret", "boss"}


def py(v):
    if lua52.lua_type(v) == "table":
        keys = list(v.keys())
        if keys and all(isinstance(k, int) for k in keys):
            return [py(v[i]) for i in sorted(keys)]
        return {(k - 1 if isinstance(k, int) else k): py(x) for k, x in v.items()}   # смешанные: номера с нуля
    return v




def load(n):
    L = lua52.LuaRuntime()
    src = open(os.path.join(LEVELS, "%d.lua" % n), encoding="utf-8").read()
    return py(L.execute(src))


def legend(D):
    lg = {k: dict(v) for k, v in BASE.items()}
    for k, v in (D.get("legend") or {}).items():
        lg[k] = v
    return lg


def check(n, D):
    lg = legend(D)
    M = D["map"]
    W, H = len(M[0]), len(M)
    errs = []
    for r, row in enumerate(M):
        if len(row) != W:
            errs.append("строка %d длиной %d, а не %d" % (r, len(row), W))

    def cell(x, z):
        if 0 <= x < W and 0 <= z < H:
            return lg.get(M[z][x])
        return None

    for th in D["things"]:
        kind, x, y, z = th[0], th[1], th[2], th[3]
        e = cell(int(x), int(z))
        if not e:
            errs.append("%s (%.1f, %.1f) — в стене" % (kind, x, z))
            continue
        if kind == "eye":
            if not (e["f"] < y < e["c"]):
                errs.append("око (%.1f, %.1f) на высоте %.1f вне клетки %.1f..%.1f" % (x, z, y, e["f"], e["c"]))
        elif abs(e["f"] - y) > 0.01 and not e.get("acid"):
            errs.append("%s (%.1f, %.1f): y=%.2f, а пол клетки %.2f" % (kind, x, z, y, e["f"]))

    # проходимость
    st = D["start"]
    sx, sz = int(st["x"]), int(st["z"])
    keys = set()
    things = D["things"]
    reach = set()
    while True:
        reach = set([(sx, sz)])
        stack = [(sx, sz)]
        while stack:
            x, z = stack.pop()
            e = cell(x, z)
            nbrs = [(x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)]
            if e.get("pad"):
                up, vx, vz = e["pad"][0], e["pad"][1], e["pad"][2]
                t = 2 * up / 18
                tx, tz = x + vx * t, z + vz * t
                peak = e["f"] + up * up / 36
                for dz in range(-2, 3):
                    for dx in range(-2, 3):
                        nx, nz = int(round(tx)) + dx, int(round(tz)) + dz
                        ne = cell(nx, nz)
                        if ne and ne["f"] <= peak - 0.3:
                            nbrs.append((nx, nz))
            for nx, nz in nbrs:
                ne = cell(nx, nz)
                if not ne or (nx, nz) in reach:
                    continue
                if ne.get("door") and ne["door"] in ("red", "yellow", "blue") and ne["door"] not in keys:
                    continue
                if ne["c"] - ne["f"] < 1.8 or min(ne["c"], e["c"]) - max(ne["f"], e["f"]) < 1.8:
                    continue
                if ne["f"] - e["f"] > 1.0 and not e.get("pad"):
                    continue
                reach.add((nx, nz))
                stack.append((nx, nz))
        new = set(keys)
        for th in things:
            if th[0] in KEYS and (int(th[1]), int(th[3])) in reach:
                new.add(KEYS[th[0]])
        if new == keys:
            break
        keys = new
    for th in things:
        if th[0] in KEYS and (int(th[1]), int(th[3])) not in reach:
            errs.append("ключ %s недостижим" % th[0])
    ex = D["exit"]
    if (int(ex["x"]), int(ex["z"])) not in reach:
        errs.append("выход недостижим")
    lost = ["%s(%g,%g)" % (th[0], th[1], th[3]) for th in things if th[0] not in ENEMY and th[0] != "console" and (int(th[1]), int(th[3])) not in reach]
    if lost:
        errs.append("недостижимые предметы: " + ", ".join(lost))
    return errs, reach


COL = {"guard": (90, 130, 230), "gunner": (230, 180, 50), "eye": (230, 80, 120), "scientist": (240, 240, 240),
       "crawler": (150, 80, 200), "turret": (255, 60, 40), "boss": (120, 255, 140)}


def draw(n, D, reach, out):
    lg = legend(D)
    M = D["map"]
    W, H = len(M[0]), len(M)
    S = 16
    im = Image.new("RGB", (W * S, H * S + 28), (16, 17, 21))
    d = ImageDraw.Draw(im)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
        big = ImageFont.truetype("arial.ttf", 16)
    except OSError:
        font = big = ImageFont.load_default()
    for z, row in enumerate(M):
        for x, ch in enumerate(row):
            e = lg.get(ch)
            if not e:
                continue
            f = e["f"]
            k = max(0, min(1, (f + 3) / 9))
            c = (int(60 + 120 * k), int(64 + 120 * k), int(74 + 110 * k))
            if e.get("acid"):
                c = (60, 190, 70)
            if e.get("pad"):
                c = (255, 170, 40)
            door = e.get("door")
            if door:
                c = {"red": (220, 50, 50), "yellow": (230, 190, 50), "blue": (60, 120, 230)}.get(door, (150, 150, 160))
            d.rectangle([x * S, 28 + z * S, x * S + S - 1, 28 + z * S + S - 1], fill=c)
    for b in D.get("brushes") or []:
        x1, z1, x2, z2 = b[0], b[2], b[3], b[5]
        col = (80, 230, 140) if b.get("glow") else (120, 90, 60)
        d.rectangle([x1 * S, 28 + z1 * S, x2 * S - 1, 28 + z2 * S - 1], outline=col, width=2)
    for th in D["things"]:
        kind, x, z = th[0], th[1], th[3]
        px, pz = x * S, 28 + z * S
        if kind in COL:
            r = 9 if kind == "boss" else 4
            d.ellipse([px - r, pz - r, px + r, pz + r], fill=COL[kind], outline=(0, 0, 0))
        elif kind in KEYS:
            c = {"red": (255, 70, 70), "yellow": (255, 220, 60), "blue": (80, 140, 255)}[KEYS[kind]]
            d.rectangle([px - 5, pz - 5, px + 5, pz + 5], fill=c, outline=(255, 255, 255))
        elif kind == "console":
            d.rectangle([px - 4, pz - 4, px + 4, pz + 4], fill=(40, 255, 120), outline=(0, 0, 0))
        else:
            d.polygon([(px, pz - 4), (px + 4, pz), (px, pz + 4), (px - 4, pz)], fill=(200, 200, 210))
    st, ex = D["start"], D["exit"]
    d.ellipse([st["x"] * S - 6, 28 + st["z"] * S - 6, st["x"] * S + 6, 28 + st["z"] * S + 6], outline=(255, 255, 255), width=3)
    d.ellipse([ex["x"] * S - 7, 28 + ex["z"] * S - 7, ex["x"] * S + 7, 28 + ex["z"] * S + 7], outline=(190, 100, 255), width=3)
    d.text((6, 5), "Уровень %d · %s" % (n, D["name"]), fill=(240, 240, 240), font=big)
    im.save(out)


if __name__ == "__main__":
    which = [int(a) for a in sys.argv[1:]] or [1, 2, 3, 4, 5]
    os.makedirs(os.path.join(HERE, "shots"), exist_ok=True)
    bad = 0
    for n in which:
        D = load(n)
        errs, reach = check(n, D)
        out = os.path.join(HERE, "shots", "map_%d.png" % n)
        draw(n, D, reach, out)
        print("Уровень %d «%s»: %s" % (n, D["name"], "ок" if not errs else "%d замечаний" % len(errs)))
        for e in errs:
            print("   ", e)
        bad += len(errs)
    sys.exit(1 if bad else 0)
