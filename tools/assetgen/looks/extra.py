import math, random
from vox import Model, render, noise, glow
import models
from PIL import Image

def recolor(model, mapping):
    """Перекрасить воксели: близкие к исходному цвету → новый (с сохранением оттенка-шума)."""
    for k, c in list(model.v.items()):
        if len(c) > 3: continue
        for src, dst in mapping:
            if sum(abs(a - b) for a, b in zip(c, src)) < 40:
                model.v[k] = tuple(max(0, min(255, d + (a - s))) for a, s, d in zip(c, src, dst))
                break
    return model

def save(m, name, grid=(16, 16), s=12):
    img = render(m, s=s, pad=16, grid=grid)
    img.save(name + ".png")
    return img

# Цвета офисного корпуса: бежевый (90-е), чёрный (2000-е), белый
save(recolor(models.case_office(), [((214, 205, 178), (40, 40, 44)), ((206, 197, 170), (34, 34, 38)), ((196, 187, 160), (60, 60, 66)), ((176, 167, 140), (24, 24, 28))]), "office_black")
save(recolor(models.case_office(), [((214, 205, 178), (236, 238, 240)), ((206, 197, 170), (226, 228, 232)), ((196, 187, 160), (210, 212, 216)), ((176, 167, 140), (190, 192, 198))]), "office_white")
# Игровой белый
save(recolor(models.case_gaming(), [((26, 26, 31), (232, 234, 238)), ((44, 44, 52), (200, 202, 208)), ((12, 12, 16), (180, 182, 188))]), "gaming_white")
# ЖК белый и серебристый
save(recolor(models.mon_lcd43(), [((30, 30, 34), (232, 232, 236)), ((50, 50, 56), (210, 210, 216)), ((40, 40, 46), (220, 220, 226))]), "lcd_white")
save(recolor(models.mon_lcd43(), [((30, 30, 34), (170, 176, 184)), ((50, 50, 56), (150, 156, 164)), ((40, 40, 46), (160, 166, 174))]), "lcd_silver")

def wallpaper(u, v):
    """Картинка на всю стену (u, v от 0 до 1): небо, солнце, горы, окно поверх."""
    c = (int(30 + 120 * v), int(60 + 120 * v), int(150 + 90 * v))
    dx, dy = u - 0.72, v - 0.62
    if dx * dx * 4 + dy * dy * 9 < 0.02: c = (255, 230, 150)
    hill = 0.35 + 0.08 * math.sin(u * 9) + 0.05 * math.sin(u * 23)
    if v < hill: c = (40, int(90 + 160 * v), 60)
    if 0.08 < u < 0.42 and 0.45 < v < 0.9:
        c = (236, 236, 240) if v < 0.86 else (60, 110, 220)
        if 0.11 < u < 0.38 and 0.5 < v < 0.8 and int(v * 60) % 3 == 0: c = (120, 120, 130)
    if v < 0.07: c = (28, 28, 34)
    return glow(c)

def lcd_wall(cols, rows):
    """Стена из тонких панелей: рамка только по внешнему краю, картинка сплошная, без подставок."""
    m = Model()
    W, H = cols * 16, rows * 16
    m.box(0, 0, 12, W, H, 14, (26, 26, 30))
    for x in range(1, W - 1):
        for y in range(1, H - 1):
            m.v[(x, y, 13)] = wallpaper((x - 1) / (W - 3), (y - 1) / (H - 3))
    # швы между блоками — тонкая тёмная линия (как у настоящих видеостен)
    for x in range(1, W - 1):
        for y in range(1, H - 1):
            if (x % 16 == 0 or y % 16 == 0):
                c = m.v[(x, y, 13)]
                m.v[(x, y, 13)] = glow(tuple(int(ch * 0.55) for ch in c[:3]))
    return m

def crt_wall(cols, rows):
    """Видеостена из кинескопов: каждый — отдельный ящик, картинка разбита по экранам."""
    m = Model()
    for i in range(cols):
        for j in range(rows):
            ox, oy = i * 16, j * 16
            beige = (204, 196, 170) if (i + j) % 2 == 0 else (196, 188, 162)
            m.box(ox, oy, 4, ox + 16, oy + 16, 15, noise(beige, 4, i * 7 + j))
            for x in range(ox + 2, ox + 14):
                for y in range(oy + 2, oy + 14):
                    u = (i * 12 + (x - ox - 2)) / (cols * 12 - 1)
                    v = (j * 12 + (y - oy - 2)) / (rows * 12 - 1)
                    c = wallpaper(u, v)
                    # строчная развёртка кинескопа
                    if y % 2 == 0: c = glow(tuple(int(ch * 0.8) for ch in c[:3]))
                    m.v[(x, y, 14)] = c
    return m

save(lcd_wall(3, 2), "wall_lcd", grid=(48, 16), s=10)
save(crt_wall(2, 2), "wall_crt", grid=(32, 16), s=10)
print("ok")
