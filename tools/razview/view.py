"""Просмотрщик моделей «Разлома» без игры: запускает art.lua (тот же код, что в игре) с заглушкой видеокарты,
собирает сетки и текстуры и рисует их в PNG с нескольких ракурсов.

    python tools/razview/view.py [art.lua] [вывод.png] [фигура ...]

Фигуры (что рисовать) — словарь FIGURES ниже: тело + ноги на своих местах. Нужны lupa и pillow.
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFont
import lupa.lua52 as lua52

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))
ART = os.path.join(REPO, "src/main/resources/data/minepc/computer/disks/razlom/app/art.lua")

# фигура: подпись, [(сетка, x, y, z, поворот°)], высота кадра в метрах
FIGURES = {
    "guard": ("Страж (сейчас)", [("guardBody", 0, 0, 0, 0), ("guardLeg", 0, 0.72, 0.15, 0), ("guardLeg", 0, 0.72, -0.15, 0)], 2.0),
    "gunner": ("Стрелок (сейчас)", [("gunnerBody", 0, 0, 0, 0), ("gunnerLeg", 0, 0.86, 0.18, 0), ("gunnerLeg", 0, 0.86, -0.18, 0)], 2.2),
    "eye": ("Око (сейчас)", [("eye", 0, 0.9, 0, 0), ("tentacle", 0, 0.62, 0.12, 0), ("tentacle", 0, 0.62, -0.12, 0)], 2.0),
    "shotgun": ("Дробовик (сейчас)", [("shotgun", -0.15, 0.4, 0, 0)], 0.9),
    # новые (черновик — файл с --extra)
    "guard2": ("Охранник (новый)", [("guard2Body", 0, 0, 0, 0), ("guard2Leg", 0, 0.72, 0.15, 0), ("guard2Leg", 0, 0.72, -0.15, 0)], 2.0),
    "gunner2": ("Боевой робот (новый)", [("gunner2Body", 0, 0, 0, 0), ("gunner2Leg", 0, 0.86, 0.2, 0), ("gunner2Leg", 0, 0.86, -0.2, 0)], 2.2),
    "eye2": ("Око (новое)", [("eye2", 0, 0.95, 0, 0), ("tentacle2", -0.08, 0.66, 0.14, 0), ("tentacle2", -0.08, 0.66, -0.14, 40),
                             ("tentacle2", 0.1, 0.64, 0.0, 90)], 2.0),
    "shotgun2": ("Дробовик (новый)", [("shotgun2", -0.12, 0.4, 0, 0)], 0.9),
}
BG = (34, 37, 44)


def load(art_path, extra=None):
    """extra — ещё один файл Lua, который получает A (таблицу art.lua) и дописывает свои текстуры и сетки."""
    L = lua52.LuaRuntime(unpack_returned_tuples=True)
    textures, meshes = {}, {}

    def new_texture(w, h, t):
        tid = len(textures) + 1
        w, h = int(w), int(h)
        textures[tid] = (w, h, [int(t[i + 1]) for i in range(w * h)])
        return tid

    def new_mesh(V, T, cull=True):
        mid = len(meshes) + 1
        v = [float(V[i + 1]) for i in range(len(V))]
        t = [int(T[i + 1]) for i in range(len(T))]
        meshes[mid] = ([v[i:i + 6] for i in range(0, len(v), 6)], [t[i:i + 4] for i in range(0, len(t), 4)], bool(cull))
        return mid

    gpu = L.table_from({"newTexture": new_texture, "newMesh": new_mesh})
    run = L.execute("return function(src, name, ...) local f = assert(load(src, '=' .. name)) return f(...) end")
    A = run(open(art_path, encoding="utf-8").read(), os.path.basename(art_path), gpu, False)
    if extra:
        run(open(extra, encoding="utf-8").read(), os.path.basename(extra), gpu, False, A)
    names = {str(k): int(v) for k, v in A["mesh"].items()}
    return textures, meshes, names


def render(textures, meshes, names, parts, yaw_deg, pitch_deg, size, height_m):
    W, H = size
    img = [[BG] * W for _ in range(H)]
    zbuf = [[-1e9] * W for _ in range(H)]
    yaw, pitch = math.radians(yaw_deg), math.radians(pitch_deg)
    scale = H / height_m * 0.82
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    base = H * 0.9
    for (mname, ox, oy, oz, ryaw) in parts:
        if mname not in names:
            continue
        verts, tris, cull = meshes[names[mname]]
        a = math.radians(ryaw)
        ca, sa = math.cos(a), math.sin(a)
        P = []
        for (x, y, z, u, v, l) in verts:
            X, Z, Y = x * ca - z * sa + ox, x * sa + z * ca + oz, y + oy
            ex = X * cy + Z * sy               # к зрителю (при yaw=0 модель смотрит на нас: вперёд — +x)
            sx = -X * sy + Z * cy              # вправо по экрану
            up = Y * cp - ex * sp
            dz = Y * sp + ex * cp
            P.append((W / 2 + sx * scale, base - up * scale, dz, u, v, l))
        for (i1, i2, i3, tex) in tris:
            p1, p2, p3 = P[i1 - 1], P[i2 - 1], P[i3 - 1]
            area = (p2[0] - p1[0]) * (p3[1] - p1[1]) - (p3[0] - p1[0]) * (p2[1] - p1[1])
            if abs(area) < 1e-9 or (cull and area > 0):
                continue
            tp = textures.get(tex)
            if tp is None:
                continue
            tw, th, tpx = tp
            xmin = max(0, int(math.floor(min(p1[0], p2[0], p3[0]))))
            xmax = min(W - 1, int(math.ceil(max(p1[0], p2[0], p3[0]))))
            ymin = max(0, int(math.floor(min(p1[1], p2[1], p3[1]))))
            ymax = min(H - 1, int(math.ceil(max(p1[1], p2[1], p3[1]))))
            for py in range(ymin, ymax + 1):
                yc = py + 0.5
                row, zrow = img[py], zbuf[py]
                for px in range(xmin, xmax + 1):
                    xc = px + 0.5
                    w1 = ((p2[0] - xc) * (p3[1] - yc) - (p3[0] - xc) * (p2[1] - yc)) / area
                    if w1 < -1e-6:
                        continue
                    w2 = ((p3[0] - xc) * (p1[1] - yc) - (p1[0] - xc) * (p3[1] - yc)) / area
                    if w2 < -1e-6:
                        continue
                    w3 = 1 - w1 - w2
                    if w3 < -1e-6:
                        continue
                    z = w1 * p1[2] + w2 * p2[2] + w3 * p3[2]
                    if z <= zrow[px]:
                        continue
                    u = w1 * p1[3] + w2 * p2[3] + w3 * p3[3]
                    v = w1 * p1[4] + w2 * p2[4] + w3 * p3[4]
                    c = tpx[min(th - 1, int((v % 1.0) * th)) * tw + min(tw - 1, int((u % 1.0) * tw))]
                    if c < 0:
                        continue
                    lt = w1 * p1[5] + w2 * p2[5] + w3 * p3[5]
                    k = 1.0 + (lt - 1) * 0.25 if lt > 1.05 else lt
                    zrow[px] = z
                    row[px] = (min(255, int((c >> 16 & 255) * k)), min(255, int((c >> 8 & 255) * k)), min(255, int((c & 255) * k)))
    im = Image.new("RGB", (W, H))
    im.putdata([c for r in img for c in r])
    d = ImageDraw.Draw(im)
    d.line([(10, int(base)), (W - 10, int(base))], fill=(64, 68, 78))
    return im


def sheet(textures, meshes, names, figs, out, views=((30, 10), (-90, 6), (150, 12))):
    tile = (300, 380)
    try:
        font = ImageFont.truetype("arial.ttf", 17)
    except OSError:
        font = ImageFont.load_default()
    rows = []
    for title, parts, hm in figs:
        ims = [render(textures, meshes, names, parts, y, p, tile, hm) for (y, p) in views]
        row = Image.new("RGB", (tile[0] * len(ims), tile[1] + 30), (24, 26, 31))
        for i, im in enumerate(ims):
            row.paste(im, (i * tile[0], 30))
        boxes = sum(len(meshes[names[p[0]]][1]) // 12 for p in parts if p[0] in names)
        ImageDraw.Draw(row).text((10, 6), "%s — коробок: %d" % (title, boxes), fill=(230, 232, 236), font=font)
        rows.append(row)
    sheet_im = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)), (24, 26, 31))
    y = 0
    for r in rows:
        sheet_im.paste(r, (0, y))
        y += r.height
    sheet_im.save(out)
    print("сохранено", out)


if __name__ == "__main__":
    args = sys.argv[1:]
    extra = None
    if "--extra" in args:
        i = args.index("--extra")
        extra = args[i + 1]
        del args[i:i + 2]
    art = args[0] if args else ART
    out = args[1] if len(args) > 1 else os.path.join(HERE, "razlom_models.png")
    which = args[2:] or list(FIGURES)
    tx, ms, nm = load(art, extra)
    sheet(tx, ms, nm, [FIGURES[w] for w in which if w in FIGURES], out)
