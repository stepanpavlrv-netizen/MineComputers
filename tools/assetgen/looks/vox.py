"""Простой изометрический рендер вокселей (1 воксель = 1 пиксель модели блока 16×16×16)."""
from PIL import Image, ImageDraw
import math, random

class Model:
    def __init__(self):
        self.v = {}          # (x,y,z) -> (r,g,b) или (r,g,b,'glow')
    def box(self, x0, y0, z0, x1, y1, z1, color):
        """Заполнить [x0,x1)×[y0,y1)×[z0,z1). color: кортеж или функция (x,y,z)->цвет/None."""
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    c = color(x, y, z) if callable(color) else color
                    if c is None: continue
                    self.v[(x, y, z)] = c
    def remove(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1):
            for y in range(y0, y1):
                for z in range(z0, z1):
                    self.v.pop((x, y, z), None)
    def paint(self, cond, color):
        """Перекрасить существующие воксели, для которых cond(x,y,z) истинно."""
        for k in list(self.v):
            if cond(*k):
                c = color(*k) if callable(color) else color
                if c is not None: self.v[k] = c

def shade(c, f):
    glow = len(c) > 3
    if glow: f = max(f, 0.95)
    return tuple(max(0, min(255, int(ch * f))) for ch in c[:3])

def render(model, s=10, pad=20, bg=None, grid=None):
    """Вид сверху-спереди-справа: перед модели — грань +z (слева внизу), правый бок — +x."""
    cs, sn = math.cos(math.radians(30)) * s, math.sin(math.radians(30)) * s
    def P(x, y, z):
        return ((x - z) * cs, (x + z) * sn - y * s)
    vs = model.v
    pts = [P(x + dx, y + dy, z + dz) for (x, y, z) in vs for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    if grid:
        gx, gz = grid
        pts += [P(0, 0, 0), P(gx, 0, 0), P(0, 0, gz), P(gx, 0, gz)]
    minx = min(p[0] for p in pts); maxx = max(p[0] for p in pts)
    miny = min(p[1] for p in pts); maxy = max(p[1] for p in pts)
    W, H = int(maxx - minx) + pad * 2, int(maxy - miny) + pad * 2
    img = Image.new("RGBA", (W, H), bg or (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    ox, oy = pad - minx, pad - miny
    def Q(x, y, z):
        px, py = P(x, y, z)
        return (px + ox, py + oy)
    if grid:   # контур клеток-блоков на полу
        gx, gz = grid
        for i in range(0, gx + 1, 16):
            d.line([Q(i, 0, 0), Q(i, 0, gz)], fill=(0, 0, 0, 60), width=1)
        for k in range(0, gz + 1, 16):
            d.line([Q(0, 0, k), Q(gx, 0, k)], fill=(0, 0, 0, 60), width=1)
    order = sorted(vs, key=lambda k: (k[0] + k[1] + k[2], k[1]))
    for (x, y, z) in order:
        c = vs[(x, y, z)]
        if (x, y + 1, z) not in vs:
            d.polygon([Q(x, y + 1, z), Q(x + 1, y + 1, z), Q(x + 1, y + 1, z + 1), Q(x, y + 1, z + 1)], fill=shade(c, 1.0))
        if (x, y, z + 1) not in vs:
            d.polygon([Q(x, y, z + 1), Q(x + 1, y, z + 1), Q(x + 1, y + 1, z + 1), Q(x, y + 1, z + 1)], fill=shade(c, 0.82))
        if (x + 1, y, z) not in vs:
            d.polygon([Q(x + 1, y, z), Q(x + 1, y, z + 1), Q(x + 1, y + 1, z + 1), Q(x + 1, y + 1, z)], fill=shade(c, 0.64))
    return img

def noise(base, amt=6, seed=None):
    r = random.Random(seed)
    def f(x, y, z):
        n = r.randint(-amt, amt)
        return tuple(max(0, min(255, ch + n)) for ch in base)
    return f

def glow(c):
    return tuple(c) + ("glow",)
