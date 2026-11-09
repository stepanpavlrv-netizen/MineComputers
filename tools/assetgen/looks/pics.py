"""Картинки для экранов: f(i, j, W, H) -> цвет, i — столбец слева, j — строка снизу."""
import math, random

def desktop(i, j, W, H):
    u, v = i / max(1, W - 1), j / max(1, H - 1)
    c = (int(30 + 120 * v), int(60 + 120 * v), int(150 + 90 * v))
    dx, dy = u - 0.75, v - 0.65
    if dx * dx * 4 + dy * dy * 9 < 0.02: c = (255, 230, 150)
    hill = 0.35 + 0.08 * math.sin(u * 9) + 0.05 * math.sin(u * 23)
    if v < hill: c = (40, int(90 + 160 * v), 60)
    if 0.08 < u < 0.45 and 0.42 < v < 0.9:
        c = (236, 236, 240) if v < 0.84 else (60, 110, 220)
        if 0.12 < u < 0.4 and 0.48 < v < 0.78 and j % 2 == 0: c = (120, 120, 130)
    if j == 0: c = (28, 28, 34)
    return c

def sheet(i, j, W, H):
    """Таблица: зелёная шапка, сетка ячеек, столбик диаграммы."""
    if j == H - 1: return (40, 130, 70)
    if j == H - 2: return (230, 236, 232)
    c = (250, 250, 250)
    if i % 4 == 0 or j % 2 == 0: c = (205, 210, 214)
    if i > W * 0.62 and j < H * 0.6:
        bars = [0.3, 0.55, 0.4, 0.75, 0.5]
        k = int((i - W * 0.62) / max(1, (W * 0.38) / len(bars)))
        if k < len(bars) and j < 1 + bars[k] * H * 0.55 and (i % 2 == 0): c = (60, 120, 220)
    return c

def code(i, j, W, H, seed=4):
    r = random.Random(seed * 1000 + j)
    if j % 2 == 1 or j == H - 1: return (30, 32, 40)
    ind = r.randint(0, 3) * 2
    L = r.randint(4, max(5, W - 4))
    if 1 + ind <= i < 1 + ind + L:
        return [(255, 120, 160), (120, 200, 255), (240, 220, 120), (160, 230, 140), (220, 220, 230)][(i // 3 + j) % 5]
    return (30, 32, 40)

def game(i, j, W, H):
    t = j / max(1, H - 1)
    c = (int(255 - 60 * t), int(120 + 40 * t), int(80 + 150 * t))
    hill = H * 0.25 + 2 * math.sin(i / 3) + (1 if i > W * 0.6 else 0)
    if j <= hill: c = (40, 60 + j * 6, 50)
    cx, cy = W // 2, H // 2
    if (i, j) in ((cx, cy), (cx - 1, cy), (cx + 1, cy), (cx, cy - 1), (cx, cy + 1)): c = (255, 255, 255)
    if j == H - 1 and i < W // 3: c = (220, 40, 40)
    return c

def text(fg, bg, seed=1, density=0.8):
    def f(i, j, W, H):
        r = random.Random(seed * 1000 + (H - 1 - j))
        if (H - 1 - j) % 2 == 1: return bg
        L = r.randint(int(W * 0.3), max(int(W * 0.3) + 1, int(W * density)))
        if i == 0 or i > L: return bg
        x = 1
        while x <= L:
            w = r.randint(1, 4)
            if x <= i < x + w: return fg
            x += w + 1
        return bg
    return f

def dashboard(i, j, W, H):
    """Пульт: янтарные графики и шкалы."""
    bg, fg, dim = (36, 20, 6), (255, 176, 40), (120, 70, 20)
    if i == W // 2: return dim
    if i < W // 2:
        y = H * 0.5 + H * 0.3 * math.sin(i / 1.6)
        return fg if abs(j - y) < 0.8 else (dim if j % 4 == 0 else bg)
    k = (i - W // 2 - 1)
    if k % 3 == 2: return bg
    h = [0.4, 0.8, 0.6, 0.9, 0.3][(k // 3) % 5]
    return fg if j < h * (H - 1) else bg

def cctv(i, j, W, H):
    """Наблюдение: 4 камеры, серые картинки, метка записи."""
    qx, qy = (i * 2) // W, (j * 2) // H
    li, lj = i - qx * W // 2, j - qy * H // 2
    w, h = W // 2, H // 2
    if li == 0 or lj == 0: return (10, 10, 10)
    base = 60 + ((qx + qy * 2) * 23) % 50
    g = base + int(40 * math.sin(li / 2.5 + qx) * math.cos(lj / 3 + qy))
    if lj < h * 0.3: g -= 25
    if (qx + qy) % 2 == 0 and abs(li - w * 0.6) < 2 and lj < h * 0.6: g += 60
    if lj == h - 1 and li in (2, 3): return (230, 40, 40)
    g = max(0, min(255, g))
    return (g, g + 6 if g < 249 else g, g)

def map_view(i, j, W, H):
    """Карта местности: вода, суша, дороги, точки."""
    n = math.sin(i / 4.3) + math.cos(j / 3.1) + math.sin((i + j) / 6.7)
    c = (40, 90, 170) if n < -0.6 else ((70, 140, 70) if n < 0.9 else (150, 140, 90))
    if i % 9 == 4 or j % 7 == 3: c = (210, 200, 170) if n >= -0.6 else c
    for px, py in ((W * 0.3, H * 0.6), (W * 0.7, H * 0.3), (W * 0.55, H * 0.75)):
        if abs(i - px) < 1 and abs(j - py) < 1: c = (255, 60, 60)
    return c
