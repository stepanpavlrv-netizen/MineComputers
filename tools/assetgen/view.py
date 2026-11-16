"""Посмотреть модель блока или предмета без игры: 4 ракурса + текстуры, одна картинка PNG.

    python tools/assetgen/view.py monitor                      # models/block/monitor.json
    python tools/assetgen/view.py item/flash_drive             # предмет
    python tools/assetgen/view.py monitor monitor_keyboard     # несколько моделей вместе (как multipart)
    python tools/assetgen/view.py camera_wall --y 90 -o cam.png  # повернуть, как в blockstate (y: 0/90/180/270)
    python tools/assetgen/view.py some/path/model.json         # любой файл модели
    python tools/assetgen/view.py --sheet all                  # все модели блоков — по одному ракурсу, общий лист
    python tools/assetgen/view.py --sheet case_mini case_aio   # выбранные модели рядом (сравнить стиль)

Понимает: родителей (свои и ванильные block/block, cube, cube_all, cube_column, orientable, item/generated),
повороты элементов, авто-UV, переворот и поворот UV, анимированные текстуры (первый кадр), shade: false.
Окраска (tintindex): у корпусов цвет по умолчанию берётся из CaseVariant.java, иначе — без окраски;
задать самому: --tint 0x292931 (для tintindex 0) или --tint 1=0xff0000.
Не понимает: display (ракурсы в руке), item overrides, модели, которые рисует код (кабели, детали в корпусе).
Нужны pillow и numpy. Картинка по умолчанию — build/view/<имя>.png. Перед лицом блока — север (-Z), как у моделей
с facing=north.
"""
import sys, os, json, math, argparse

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")

# ---------------------------------------------------------------- модели

VANILLA = {
    "block/block": {},
    "block/cube": {"elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
        d: {"texture": "#" + d, "cullface": d} for d in ("down", "up", "north", "south", "west", "east")}}]},
    "block/cube_all": {"parent": "block/cube", "textures": {
        "particle": "#all", "down": "#all", "up": "#all", "north": "#all", "south": "#all", "west": "#all", "east": "#all"}},
    "block/cube_column": {"parent": "block/cube", "textures": {
        "down": "#end", "up": "#end", "north": "#side", "south": "#side", "west": "#side", "east": "#side"}},
    "block/orientable": {"parent": "block/cube", "textures": {
        "down": "#top", "up": "#top", "north": "#front", "south": "#side", "west": "#side", "east": "#side"}},
    "item/generated": {"generated": True},
    "item/handheld": {"parent": "item/generated"},
}


def model_path(ref):
    """«monitor», «block/monitor», «minepc:item/x» или путь к файлу → (путь или None, ключ ванильной модели или None)."""
    if ref.endswith(".json") and os.path.exists(ref):
        return os.path.abspath(ref), None
    ns, _, path = ref.rpartition(":")
    ns = ns or "minepc"
    if "/" not in path:
        path = "block/" + path
    if ns == "minecraft":
        return None, path
    p = os.path.join(ASSETS, ns, "models", path + ".json")
    if os.path.exists(p):
        return p, None
    p2 = os.path.join(ASSETS, ns, "models", "block", path + ".json")
    if os.path.exists(p2):
        return p2, None
    if path in VANILLA:
        return None, path
    raise SystemExit("Нет модели: " + ref + " (" + p + ")")


def load_model(ref, depth=0):
    """Модель с учётом родителей: {elements, textures, generated}."""
    if depth > 20:
        raise SystemExit("Слишком длинная цепочка родителей у " + ref)
    p, vkey = model_path(ref)
    if p:
        m = json.load(open(p, encoding="utf-8"))
    else:
        if vkey not in VANILLA:
            print("  ! ванильный родитель " + vkey + " не знаю — рисую без него")
            return {"elements": [], "textures": {}, "generated": False}
        m = VANILLA[vkey]
    parent = m.get("parent")
    if parent:
        if ":" not in parent:
            own = p is not None and os.path.exists(os.path.join(ASSETS, "minepc", "models", parent + ".json"))
            parent = ("minepc:" if own else "minecraft:") + parent
        base = load_model(parent, depth + 1)
    else:
        base = {"elements": [], "textures": {}, "generated": False}
    out = {"elements": m["elements"] if "elements" in m else base["elements"],
           "textures": dict(base["textures"]), "generated": m.get("generated", base["generated"])}
    out["textures"].update(m.get("textures", {}))
    return out


def resolve(textures, ref):
    seen = 0
    while ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:], ref)
        seen += 1
    return None if ref.startswith("#") else ref


_tex = {}
_warned = {}
VANILLA_COLORS = {"iron_block": (220, 220, 220), "black_concrete": (8, 10, 15), "gray_concrete": (54, 57, 61),
                  "white_concrete": (207, 213, 214), "light_gray_concrete": (125, 125, 115),
                  "smooth_stone": (158, 158, 158), "red_concrete": (142, 32, 32), "redstone_block": (175, 24, 5),
                  "calcite": (223, 224, 220), "stone": (125, 125, 125), "oak_planks": (162, 130, 78)}


def load_tex(ref):
    """Текстура по «minepc:block/x»; у анимированной — первый кадр. Нет файла — фиолетово-чёрная клетка, как в игре."""
    if ref in _tex:
        return _tex[ref]
    ns, _, path = ref.rpartition(":")
    p = os.path.join(ASSETS, ns or "minecraft", "textures", path + ".png")
    jar = os.environ.get("MC_JAR")    # клиентский jar Minecraft — тогда ванильные текстуры настоящие
    if not os.path.exists(p) and ns == "minecraft" and jar and os.path.exists(jar):
        import zipfile, io
        try:
            with zipfile.ZipFile(jar) as z:
                p = io.BytesIO(z.read("assets/minecraft/textures/" + path + ".png"))
        except KeyError:
            pass
    if not isinstance(p, str) or os.path.exists(p):
        im = Image.open(p).convert("RGBA")
        if im.height > im.width and im.height % im.width == 0:
            im = im.crop((0, 0, im.width, im.width))
    elif ns == "minecraft" and path.split("/")[-1] in VANILLA_COLORS:
        # ванильной текстуры нет под рукой — похожий цвет с лёгким шумом
        import random
        rnd = random.Random(path)
        base = VANILLA_COLORS[path.split("/")[-1]]
        im = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                k = 1 + rnd.uniform(-0.05, 0.05)
                im.putpixel((x, y), tuple(min(255, int(c * k)) for c in base) + (255,))
        if not _warned.get("vanilla"):
            _warned["vanilla"] = True
            print("  ванильные текстуры — примерным цветом (настоящие: переменная MC_JAR = путь к jar клиента Minecraft)")
    else:
        print("  ! нет текстуры " + ref)
        im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, 7, 7), fill=(248, 0, 248, 255))
        d.rectangle((8, 8, 15, 15), fill=(248, 0, 248, 255))
    _tex[ref] = im
    return im


# ---------------------------------------------------------------- геометрия

def corners(d, f, t):
    """Углы грани: точки для (u0,v0), (u1,v0), (u0,v1) — раскладка Minecraft."""
    fx, fy, fz = f
    tx, ty, tz = t
    return {
        "north": ((tx, ty, fz), (fx, ty, fz), (tx, fy, fz)),
        "south": ((fx, ty, tz), (tx, ty, tz), (fx, fy, tz)),
        "east": ((tx, ty, tz), (tx, ty, fz), (tx, fy, tz)),
        "west": ((fx, ty, fz), (fx, ty, tz), (fx, fy, fz)),
        "up": ((fx, ty, fz), (tx, ty, fz), (fx, ty, tz)),
        "down": ((fx, fy, tz), (tx, fy, tz), (fx, fy, fz)),
    }[d]


NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
          "east": (1, 0, 0), "west": (-1, 0, 0)}


def auto_uv(d, f, t):
    fx, fy, fz = f
    tx, ty, tz = t
    return {
        "north": (16 - tx, 16 - ty, 16 - fx, 16 - fy),
        "south": (fx, 16 - ty, tx, 16 - fy),
        "east": (16 - tz, 16 - ty, 16 - fz, 16 - fy),
        "west": (fz, 16 - ty, tz, 16 - fy),
        "up": (fx, fz, tx, tz),
        "down": (fx, 16 - tz, tx, 16 - fz),
    }[d]


def rot_point(p, axis, ang, origin):
    a = math.radians(ang)
    c, s = math.cos(a), math.sin(a)
    x, y, z = (p[i] - origin[i] for i in range(3))
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def face_crop(img, uv, rotation):
    W, H = img.size
    u0, v0, u1, v1 = uv
    x0, x1 = sorted((u0 * W / 16, u1 * W / 16))
    y0, y1 = sorted((v0 * H / 16, v1 * H / 16))
    box = (int(math.floor(x0 + 1e-6)), int(math.floor(y0 + 1e-6)),
           max(int(math.floor(x0 + 1e-6)) + 1, int(math.ceil(x1 - 1e-6))),
           max(int(math.floor(y0 + 1e-6)) + 1, int(math.ceil(y1 - 1e-6))))
    crop = img.crop(box)
    if u0 > u1:
        crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
    if v0 > v1:
        crop = crop.transpose(Image.FLIP_TOP_BOTTOM)
    if rotation:
        crop = crop.rotate(-rotation, expand=True)
    return crop


def tint_img(im, rgb):
    a = np.asarray(im, dtype=np.float32).copy()
    a[..., 0] *= ((rgb >> 16) & 255) / 255.0
    a[..., 1] *= ((rgb >> 8) & 255) / 255.0
    a[..., 2] *= (rgb & 255) / 255.0
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


def case_tints():
    """Цвета корпусов по умолчанию (paintBase) из CaseVariant.java: {"case_gaming": 0x292931, ...}."""
    import re
    p = os.path.join(ROOT, "src", "main", "java", "com", "strikerr212", "minepc", "block", "CaseVariant.java")
    if not os.path.exists(p):
        return {}
    return {m.group(1): int(m.group(2), 16) for m in re.finditer(r'\("(\w+)",\s*0x([0-9A-Fa-f]{6})', open(p, encoding="utf-8").read())}


def collect(model, yrot=0, xrot=0, used=None, tints=None, stats=None):
    """Грани модели: (точки p0,p1,p2, нормаль, картинка, тень вкл.)."""
    faces = []
    tm = model["textures"]
    elements = model["elements"]
    if model.get("generated"):
        # плоский предмет: слои layer0, layer1… — пластинка толщиной 1 пиксель
        elements = []
        for i in range(5):
            if "layer%d" % i in tm:
                elements.append({"from": [0, 0, 7.5 - i * 0.01], "to": [16, 16, 8.5 + i * 0.01], "shade": False,
                                 "faces": {"north": {"texture": "#layer%d" % i, "uv": [16, 0, 0, 16]},
                                           "south": {"texture": "#layer%d" % i, "uv": [0, 0, 16, 16]}}})
    for el in elements:
        f, t = el["from"], el["to"]
        r = el.get("rotation")
        for d, fd in el.get("faces", {}).items():
            ref = resolve(tm, fd["texture"])
            if ref is None:
                print("  ! грань без текстуры: " + fd["texture"])
                continue
            if ":" not in ref:
                ref = "minecraft:" + ref
            if used is not None and ref not in used:
                used.append(ref)
            uv = fd.get("uv") or auto_uv(d, f, t)
            img = face_crop(load_tex(ref), uv, fd.get("rotation", 0))
            ti = fd.get("tintindex")
            if ti is not None:
                if tints and ti in tints:
                    img = tint_img(img, tints[ti])
                elif stats is not None:
                    stats["untinted"] = stats.get("untinted", 0) + 1
            pts = list(corners(d, f, t))
            nrm = NORMAL[d]
            if r and r.get("angle"):
                pts = [rot_point(p, r["axis"], r["angle"], r.get("origin", (8, 8, 8))) for p in pts]
                nrm = rot_point(nrm, r["axis"], r["angle"], (0, 0, 0))
            # поворот блока, как в blockstate: сначала x, потом y (в Minecraft y — по часовой, если смотреть сверху)
            if xrot:
                pts = [rot_point(p, "x", -xrot, (8, 8, 8)) for p in pts]
                nrm = rot_point(nrm, "x", -xrot, (0, 0, 0))
            if yrot:
                pts = [rot_point(p, "y", -yrot, (8, 8, 8)) for p in pts]
                nrm = rot_point(nrm, "y", -yrot, (0, 0, 0))
            faces.append((pts, nrm, img, el.get("shade", True)))
    return faces


def shade_of(n):
    nx, ny, nz = n
    return nx * nx * 0.6 + nz * nz * 0.8 + ny * ny * (1.0 if ny > 0 else 0.5)


# ---------------------------------------------------------------- рисование

def norm(a):
    l = math.sqrt(sum(x * x for x in a)) or 1
    return tuple(x / l for x in a)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(x * y for x, y in zip(a, b))


def render(faces, V, size, bg, box_lines=True):
    """Ортогональный вид с направления V (от модели к зрителю)."""
    V = norm(V)
    F = tuple(-x for x in V)
    up = (0, 1, 0) if abs(V[1]) < 0.99 else (0, 0, -1)
    R = norm(cross(F, up))
    U = cross(R, F)
    pts = [p for fc in faces for p in fc[0]]
    # углы блока 16×16×16 — тоже в кадр, чтобы видеть размер относительно блока
    cube = [(x, y, z) for x in (0, 16) for y in (0, 16) for z in (0, 16)]
    allp = pts + (cube if box_lines else [])
    if not allp:
        allp = cube
    xs = [dot(p, R) for p in allp]
    ys = [dot(p, U) for p in allp]
    span = max(max(xs) - min(xs), max(ys) - min(ys), 1)
    scale = size * 0.86 / span
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2

    def proj(p):
        return (size / 2 + scale * (dot(p, R) - cx), size / 2 - scale * (dot(p, U) - cy))

    color = np.zeros((size, size, 3), dtype=np.float32)
    color[:] = bg
    if box_lines:
        im = Image.fromarray(color.astype(np.uint8))
        d = ImageDraw.Draw(im)
        for a in cube:
            for b in cube:
                if sum(1 for i in range(3) if a[i] != b[i]) == 1 and a < b:
                    d.line([proj(a), proj(b)], fill=tuple(int(c * 1.6 + 12) for c in bg), width=1)
        color = np.asarray(im, dtype=np.float32).copy()
    zbuf = np.full((size, size), -1e9, dtype=np.float32)
    for (p0, p1, p2), nrm, img, shade in faces:
        if dot(nrm, V) <= 1e-6:
            continue
        a, b, c = proj(p0), proj(p1), proj(p2)
        ex = (b[0] - a[0], b[1] - a[1])
        ey = (c[0] - a[0], c[1] - a[1])
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-9:
            continue
        qx = [a[0], b[0], c[0], b[0] + ey[0]]
        qy = [a[1], b[1], c[1], b[1] + ey[1]]
        x0, y0 = max(0, int(min(qx))), max(0, int(min(qy)))
        x1, y1 = min(size, int(max(qx)) + 2), min(size, int(max(qy)) + 2)
        if x1 <= x0 or y1 <= y0:
            continue
        X, Y = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = X - a[0], Y - a[1]
        sp = (ey[1] * dx - ey[0] * dy) / det
        tp = (-ex[1] * dx + ex[0] * dy) / det
        inside = (sp >= 0) & (sp <= 1) & (tp >= 0) & (tp <= 1)
        if not inside.any():
            continue
        tex = np.asarray(img, dtype=np.float32)
        th, tw = tex.shape[:2]
        samp = tex[np.clip((tp * th).astype(int), 0, th - 1), np.clip((sp * tw).astype(int), 0, tw - 1)]
        k = shade_of(nrm) if shade else 1.0
        e1 = [p1[i] - p0[i] for i in range(3)]
        e2 = [p2[i] - p0[i] for i in range(3)]
        dep = dot(p0, V) + sp * dot(e1, V) + tp * dot(e2, V)
        sub_z = zbuf[y0:y1, x0:x1]
        m = inside & (samp[..., 3] > 8) & (dep > sub_z - 1e-4)
        sub_c = color[y0:y1, x0:x1]
        al = samp[..., 3:4] / 255.0
        sub_c[m] = (samp[..., :3] * k * al + sub_c * (1 - al))[m]
        sub_z[m] = dep[m]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8))


def font(sz):
    for name in ("DejaVuSans.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "arial.ttf", "segoeui.ttf",
                 "C:/Windows/Fonts/arial.ttf"):
        try:
            return ImageFont.truetype(name, sz)
        except OSError:
            pass
    return ImageFont.load_default()


# Перед блока — север (-Z). «Слева/справа» — как видит игрок, который стоит перед блоком лицом к нему.
VIEWS = [("спереди слева", (0.75, 0.6, -1)), ("спереди справа", (-0.75, 0.6, -1)),
         ("сзади", (0.7, 0.5, 1)), ("прямо спереди", (0, 0.001, -1))]


def main():
    ap = argparse.ArgumentParser(description="Модель блока или предмета → PNG (4 ракурса и текстуры)")
    ap.add_argument("models", nargs="+")
    ap.add_argument("-o", "--out")
    ap.add_argument("--y", type=int, default=0, help="поворот как в blockstate: 0/90/180/270")
    ap.add_argument("--x", type=int, default=0)
    ap.add_argument("--size", type=int, default=420, help="размер одного ракурса в точках")
    ap.add_argument("--nobox", action="store_true", help="не рисовать рамку блока 16×16×16")
    ap.add_argument("--tint", action="append", default=[], help="цвет окраски: 0x292931 или 1=0xff0000")
    ap.add_argument("--sheet", action="store_true", help="лист: каждая модель отдельно, один ракурс (all — все блоки)")
    a = ap.parse_args()
    if a.sheet:
        return contact_sheet(a)

    tints = {}
    for t in a.tint:
        k, _, v = t.rpartition("=")
        tints[int(k or 0)] = int(v, 16)
    faces, used, stats = [], [], {}
    defaults = case_tints()
    for ref in a.models:
        base = os.path.basename(ref).replace(".json", "").split(":")[-1].split("/")[-1]
        tt = dict(tints)
        if 0 not in tt and base in defaults:
            tt[0] = defaults[base]
            print("  окраска по умолчанию для %s: %06X (CaseVariant)" % (base, defaults[base]))
        faces += collect(load_model(ref), a.y, a.x, used, tt, stats)
    if stats.get("untinted"):
        print("  граней с окраской (tintindex) без цвета: %d — в игре они окрашены; цвет задаётся --tint" % stats["untinted"])
    name = "+".join(os.path.basename(m).replace(".json", "").replace(":", "_").replace("/", "_") for m in a.models)
    out = a.out or os.path.join(ROOT, "build", "view", name + ".png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)

    S = a.size
    bg = (38, 40, 46)
    pad = 26
    tex_h = 0
    tiles = []
    for ref in used:
        im = load_tex(ref)
        k = max(1, min(8, 128 // max(im.width, 1)))
        tiles.append((ref, im.resize((im.width * k, im.height * k), Image.NEAREST)))
    if tiles:
        tex_h = max(t.height for _, t in tiles) + pad + 10
    W = 2 * S
    sheet = Image.new("RGB", (W, pad + 2 * (S + pad) + tex_h), (28, 29, 34))
    d = ImageDraw.Draw(sheet)
    f = font(15)
    d.text((8, 5), name + "   граней: %d   рамка — блок 16×16×16" % len(faces), fill=(220, 220, 228), font=f)
    for i, (label, V) in enumerate(VIEWS):
        x, y = (i % 2) * S, pad + (i // 2) * (S + pad)
        sheet.paste(render(faces, V, S, bg, not a.nobox), (x, y + pad))
        d.text((x + 8, y + 5), label, fill=(170, 172, 186), font=f)
    x, y = 8, pad + 2 * (S + pad) + 4
    for ref, t in tiles:
        if x + t.width > W:
            break
        # шахматка под прозрачным
        ch = Image.new("RGB", t.size, (60, 60, 66))
        cd = ImageDraw.Draw(ch)
        for yy in range(0, t.height, 8):
            for xx in range(0, t.width, 8):
                if (xx // 8 + yy // 8) % 2:
                    cd.rectangle((xx, yy, xx + 7, yy + 7), fill=(80, 80, 88))
        ch.paste(t, (0, 0), t)
        sheet.paste(ch, (x, y + 20))
        d.text((x, y), ref.split(":")[-1].split("/")[-1][:22], fill=(150, 152, 166), font=font(12))
        x += max(t.width, 120) + 12
    sheet.save(out)
    print(out)


def contact_sheet(a):
    refs = a.models
    if refs == ["all"]:
        d = os.path.join(ASSETS, "minepc", "models", "block")
        refs = sorted(f[:-5] for f in os.listdir(d) if f.endswith(".json"))
    defaults = case_tints()
    S = min(a.size, 220)
    cols = max(1, min(8, len(refs)))
    rows = (len(refs) + cols - 1) // cols
    out = a.out or os.path.join(ROOT, "build", "view", "sheet.png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    im = Image.new("RGB", (cols * S, rows * (S + 18)), (28, 29, 34))
    d = ImageDraw.Draw(im)
    f = font(12)
    for i, ref in enumerate(refs):
        base = ref.split(":")[-1].split("/")[-1]
        tt = {0: defaults[base]} if base in defaults else {}
        try:
            faces = collect(load_model(ref), a.y, a.x, [], tt, {})
            tile = render(faces, VIEWS[0][1], S, (38, 40, 46), False)
        except SystemExit as e:
            tile = Image.new("RGB", (S, S), (60, 20, 20))
            print("  ! " + ref + ": " + str(e))
        x, y = (i % cols) * S, (i // cols) * (S + 18)
        im.paste(tile, (x, y + 18))
        d.text((x + 4, y + 2), base[:30], fill=(170, 172, 186), font=f)
    im.save(out)
    print(out)


if __name__ == "__main__":
    main()
