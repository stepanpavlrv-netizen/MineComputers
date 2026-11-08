"""Мини-рендерер: рисует корпус с деталями из JSON-моделей (ортогональная проекция, текстуры)."""
import sys, os, json, math
from PIL import Image

root = sys.argv[1]
out = sys.argv[2]
config = sys.argv[3]
A = f"{root}/src/main/resources/assets/minepc"

_tex_cache = {}


def load_tex(ref):
    ns, path = ref.split(":")
    p = f"{A}/textures/{path}.png"
    if p not in _tex_cache:
        _tex_cache[p] = Image.open(p).convert("RGBA")
    return _tex_cache[p]


def tint_img(im, rgb):
    if rgb is None or rgb == 0xFFFFFF:
        return im
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    px = im.load(); im = im.copy(); px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
    return im


def shade_img(im, k):
    px = im.load()
    for x in range(im.width):
        for y in range(im.height):
            pr, pg, pb, pa = px[x, y]
            px[x, y] = (int(pr * k), int(pg * k), int(pb * k), pa)
    return im


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
          "east": (1, 0, 0), "west": (-1, 0, 0)}


def corners(d, f, t):
    """Углы грани: (u0,v0), (u1,v0), (u0,v1) — в той же раскладке, что у Minecraft."""
    fx, fy, fz = f; tx, ty, tz = t
    return {
        "north": ((tx, ty, fz), (fx, ty, fz), (tx, fy, fz)),
        "south": ((fx, ty, tz), (tx, ty, tz), (fx, fy, tz)),
        "east": ((tx, ty, tz), (tx, ty, fz), (tx, fy, tz)),
        "west": ((fx, ty, fz), (fx, ty, tz), (fx, fy, fz)),
        "up": ((fx, ty, fz), (tx, ty, fz), (fx, ty, tz)),
        "down": ((fx, fy, tz), (tx, fy, tz), (fx, fy, fz)),
    }[d]


faces = []   # (depth, quad3d, texture image crop)


def add_model(path, offset=(0, 0, 0), tints=(None, None, None), case_coords_dx=-4.0):
    model = json.load(open(path))
    texmap = model["textures"]
    for el in model["elements"]:
        f = [el["from"][0] + case_coords_dx + offset[0], el["from"][1] + offset[1], el["from"][2] + offset[2]]
        t = [el["to"][0] + case_coords_dx + offset[0], el["to"][1] + offset[1], el["to"][2] + offset[2]]
        for d, fd in el["faces"].items():
            ref = fd["texture"].lstrip("#")
            ref = texmap.get(ref, ref)
            uv = fd.get("uv", [0, 0, 16, 16])
            img = load_tex(ref)
            W, H = img.size
            u0, v0, u1, v1 = uv
            box = (int(round(min(u0, u1) * W / 16)), int(round(min(v0, v1) * H / 16)),
                   max(int(round(min(u0, u1) * W / 16)) + 1, int(round(max(u0, u1) * W / 16))),
                   max(int(round(min(v0, v1) * H / 16)) + 1, int(round(max(v0, v1) * H / 16))))
            crop = img.crop(box)
            ti = fd.get("tintindex")
            if ti is not None and 0 < ti < len(tints):
                crop = tint_img(crop, tints[ti])
            crop = shade_img(crop.copy(), SHADE[d])
            faces.append((d, corners(d, f, t), crop))


# ---------- камера ----------
V = tuple(float(v) for v in os.environ.get("PVIEW", "1.0,0.45,-0.55").split(","))
n = math.sqrt(sum(c * c for c in V)); V = tuple(c / n for c in V)
UP = (0, 1, 0)
def cross(a, b): return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
def dot(a, b): return sum(x*y for x, y in zip(a, b))
def norm(a): l = math.sqrt(dot(a, a)); return tuple(x / l for x in a)
RIGHT = norm(cross(UP, V)); RIGHT = tuple(-x for x in RIGHT)
SUP = norm(cross(V, RIGHT)); SUP = tuple(-x for x in SUP)
if SUP[1] < 0: SUP = tuple(-x for x in SUP)
SCALE = float(os.environ.get("PSCALE", 52))
CX, CY = 550, 560
FOCUS = tuple(float(v) for v in os.environ.get("PFOCUS", "8,8,8").split(","))


def proj(p):
    q = tuple(p[i] - FOCUS[i] for i in range(3))
    return (CX + SCALE * dot(q, RIGHT), CY - SCALE * dot(q, SUP))


def render():
    import numpy as np
    Wc, Hc = 1100, 1100
    color = np.zeros((Hc, Wc, 3), dtype=np.float32); color[:] = (34, 36, 40)
    zbuf = np.full((Hc, Wc), -1e9, dtype=np.float32)
    for d, (p0, p1, p2), img in faces:
        if dot(NORMAL[d], V) <= 1e-6:
            continue
        a, b, c = proj(p0), proj(p1), proj(p2)
        ex = (b[0] - a[0], b[1] - a[1]); ey = (c[0] - a[0], c[1] - a[1])
        det = ex[0] * ey[1] - ex[1] * ey[0]
        if abs(det) < 1e-9:
            continue
        xs = [a[0], b[0], c[0], b[0] + ey[0]]; ys = [a[1], b[1], c[1], b[1] + ey[1]]
        x0, y0 = max(0, int(min(xs))), max(0, int(min(ys)))
        x1, y1 = min(Wc, int(max(xs)) + 2), min(Hc, int(max(ys)) + 2)
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
        ui = np.clip((sp * tw).astype(int), 0, tw - 1)
        vi = np.clip((tp * th).astype(int), 0, th - 1)
        samp = tex[vi, ui]
        ex3 = [p1[i] - p0[i] for i in range(3)]; ey3 = [p2[i] - p0[i] for i in range(3)]
        dep = dot(p0, V) + sp * dot(ex3, V) + tp * dot(ey3, V)
        sub_z = zbuf[y0:y1, x0:x1]
        m = inside & (samp[..., 3] > 8) & (dep > sub_z)
        sub_c = color[y0:y1, x0:x1]
        al = (samp[..., 3:4] / 255.0)
        sub_c[m] = (samp[..., :3] * al + sub_c * (1 - al))[m]
        sub_z[m] = dep[m]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8))


# ---------- корпус (без стекла) ----------
add_model(f"{A}/models/block/pc_case.json", case_coords_dx=0.0)
faces[:] = [f for f in faces if not (f[2].size == (16, 16) and False)]
# убрать стекло и внешние грани, закрывающие вид
case = json.load(open(f"{A}/models/block/pc_case.json"))
faces.clear()
tm = case["textures"]
for el in case["elements"]:
    if el.get("name") == "glass":
        continue
    for d, fd in el["faces"].items():
        ref = tm[fd["texture"].lstrip("#")]
        img = load_tex(ref)
        f, t = el["from"], el["to"]
        sizes = {"north": (f[0], f[1], t[0], t[1]), "south": (f[0], f[1], t[0], t[1])}
        crop = shade_img(img.copy(), SHADE[d])
        faces.append((d, corners(d, f, t), crop))

parts = json.load(open(config))
for p in parts:
    add_model(f"{A}/models/item/{p['model']}.json", tuple(p.get("offset", (0, 0, 0))),
              (None, p.get("color"), p.get("accent"), p.get("pcb")))
render().save(out)
print("faces:", len(faces))
