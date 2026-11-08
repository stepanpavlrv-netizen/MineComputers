"""Превью иконок в инвентаре: рендер витринных моделей с gui-трансформацией, как в Minecraft."""
import sys, os, json, math
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
from models3d import rotate

root, out = sys.argv[1], sys.argv[2]
S = int(sys.argv[3]) if len(sys.argv) > 3 else 64
A = f"{root}/src/main/resources/assets/minepc"
STOCK = {  # (цвет, акцент) — как в HardwarePresets
    "motherboard": [(0xFFFFFF, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0x5A5E66, 0xFFFFFF), (0x3C3F47, 0xFF4060), (0xFFFFFF, 0x80D0FF)],
    "cpu": [(0xFFFFFF, 0xFFFFFF)] * 4 + [(0x6B7080, 0xF0C050)],
    "cooler": [(0x4A4E58, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x4A4E58, 0x40C8FF)],
    "ram": [(0xFFFFFF, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x4A4E58, 0x3AB0FF), (0xFFFFFF, 0x9A6BFF)],
    "gpu": [(0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x5A5E66, 0xFFFFFF), (0x3C3F47, 0xFFFFFF), (0xFFFFFF, 0x40FF9A)],
    "psu": [(0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x3C3F47, 0xE0C060), (0x3C3F47, 0xFF60D0)],
    "hdd": [(0xFFFFFF, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0xFFFFFF, 0xFFFFFF), (0xFFFFFF, 0x40A0FF)],
    "ssd": [(0x4A4E58, 0xFFFFFF), (0xFFFFFF, 0xFF4040), (0xFFFFFF, 0xFFFFFF), (0x4A4E58, 0xFFFFFF), (0x4A4E58, 0xFF4040)],
}
NORM = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}
L0 = np.array([-0.35, 1.0, 0.75]); L0 /= np.linalg.norm(L0)
L1 = np.array([0.35, 0.6, 0.9]); L1 /= np.linalg.norm(L1)
_cache = {}


def tex(ref):
    p = f"{A}/textures/{ref.split(':')[1]}.png"
    if p not in _cache:
        _cache[p] = np.asarray(Image.open(p).convert("RGBA"), dtype=np.float32)
    return _cache[p]


def corners(d, f, t):
    fx, fy, fz = f; tx, ty, tz = t
    return {"north": ((tx, ty, fz), (fx, ty, fz), (tx, fy, fz)), "south": ((fx, ty, tz), (tx, ty, tz), (fx, fy, tz)),
            "east": ((tx, ty, tz), (tx, ty, fz), (tx, fy, tz)), "west": ((fx, ty, fz), (fx, ty, tz), (fx, fy, fz)),
            "up": ((fx, ty, fz), (tx, ty, fz), (fx, ty, tz)), "down": ((fx, fy, tz), (tx, fy, tz), (fx, fy, fz))}[d]


def render_icon3(model, tints, size):
    return render_icon(model, tints, size)


def render_icon(model, tints, size):
    disp = model["display"]["gui"]
    rot, sc = disp["rotation"], disp["scale"][0]
    color = np.zeros((size, size, 4), dtype=np.float32)
    zbuf = np.full((size, size), -1e9, dtype=np.float32)
    def P(p):
        v = rotate([(p[i] - 8) * sc for i in range(3)], rot)
        return ((v[0] + 8) / 16 * size, (8 - v[1]) / 16 * size, v[2])
    for e in model["elements"]:
        for d, fd in e["faces"].items():
            n = np.array(rotate(NORM[d], rot))
            if n[2] <= 1e-6:
                continue
            ref = model["textures"][fd["texture"].lstrip("#")]
            img = tex(ref)
            H, W = img.shape[:2]
            u0, v0, u1, v1 = fd.get("uv", [0, 0, 16, 16])
            p0, p1, p2 = corners(d, e["from"], e["to"])
            a, b, c = P(p0), P(p1), P(p2)
            ex = (b[0] - a[0], b[1] - a[1]); ey = (c[0] - a[0], c[1] - a[1])
            det = ex[0] * ey[1] - ex[1] * ey[0]
            if abs(det) < 1e-9:
                continue
            xs = [a[0], b[0], c[0], b[0] + ey[0]]; ys = [a[1], b[1], c[1], b[1] + ey[1]]
            x0, y0 = max(0, int(min(xs))), max(0, int(min(ys)))
            x1, y1 = min(size, int(max(xs)) + 2), min(size, int(max(ys)) + 2)
            if x1 <= x0 or y1 <= y0:
                continue
            X, Y = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
            dx, dy = X - a[0], Y - a[1]
            sp = (ey[1] * dx - ey[0] * dy) / det
            tp = (-ex[1] * dx + ex[0] * dy) / det
            inside = (sp >= 0) & (sp <= 1) & (tp >= 0) & (tp <= 1)
            uu = np.clip(((u0 + sp * (u1 - u0)) / 16 * W).astype(int), 0, W - 1)
            vv = np.clip(((v0 + tp * (v1 - v0)) / 16 * H).astype(int), 0, H - 1)
            samp = img[vv, uu].copy()
            ti = fd.get("tintindex")
            if ti in (1, 2, 3) and ti - 1 < len(tints):
                rgb = tints[ti - 1]
                samp[..., 0] *= ((rgb >> 16) & 255) / 255; samp[..., 1] *= ((rgb >> 8) & 255) / 255; samp[..., 2] *= (rgb & 255) / 255
            light = min(1.0, 0.45 + 0.55 * (max(0, n @ L0) + 0.6 * max(0, n @ L1)))
            samp[..., :3] *= light
            dep = a[2] + sp * (b[2] - a[2]) + tp * (c[2] - a[2])
            sub_z = zbuf[y0:y1, x0:x1]
            msk = inside & (samp[..., 3] > 8) & (dep > sub_z)
            color[y0:y1, x0:x1][msk] = samp[msk]
            color[y0:y1, x0:x1, 3][msk] = 255
            sub_z[msk] = dep[msk]
    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8))


types = list(STOCK)
G = max(4, S // 8)
sheet = Image.new("RGBA", (5 * (S + G) + G, len(types) * (S + G) + G), (139, 139, 139, 255))
for r, t in enumerate(types):
    for s in range(5):
        model = json.load(open(f"{A}/models/item/showcase/{t}_s{s}.json"))
        icon = render_icon(model, STOCK[t][s], S)
        x, y = G + s * (S + G), G + r * (S + G)
        slot = Image.new("RGBA", (S, S), (139, 139, 139, 255))
        sheet.paste(slot, (x, y))
        sheet.alpha_composite(icon, (x, y))
sheet.save(out)
print("ok")
