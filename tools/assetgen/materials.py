"""Материалы для 3D-моделей деталей и текстуры лицевой стороны материнских плат."""
import math, random
from px import Canvas, fan, noise, hx
from items import M_BASE, M_HI, M_LO, M_ENGR


def solid(color, amount=3, seed=0, size=16):
    c = Canvas(size, size)
    noise(c, 0, 0, size - 1, size - 1, color, amount, seed)
    return c


def brushed(color, seed=0):
    """Шлифованный металл: лёгкие продольные полосы."""
    c = Canvas()
    rnd = random.Random(seed)
    b = hx(color)
    for y in range(16):
        d = rnd.randint(-5, 5)
        for x in range(16):
            e = d + rnd.randint(-2, 2)
            c.px(x, y, (max(0, min(255, b[0] + e)), max(0, min(255, b[1] + e)), max(0, min(255, b[2] + e)), 255))
    return c


def stripes(vertical, a, b):
    c = Canvas()
    for i in range(16):
        for j in range(16):
            k = i if vertical else j
            c.px(i, j, a if k % 2 == 0 else b)
    return c


def materials():
    m = {}
    m["metal"] = brushed(M_BASE, 1)
    m["metal_dark"] = brushed("3a3d44", 2)
    m["silver"] = brushed("b9bec5", 3)
    m["fins_v"] = stripes(True, M_BASE, M_LO)
    m["fins_h"] = stripes(False, M_BASE, M_LO)
    m["plastic"] = solid("2a2b30", 2, 4)
    m["plastic_light"] = solid("e2e3e5", 2, 5)
    m["led"] = solid("f4f4f4", 1, 6)
    m["copper"] = brushed("c4834f", 7)
    m["chip"] = solid("1d1e22", 2, 8)
    m["die"] = solid("2b3048", 3, 9)
    m["pcb_green"] = solid("2c5e3f", 3, 10)
    m["pcb_black"] = solid("23262b", 3, 11)
    m["pcb_blue"] = solid("1f2d4a", 3, 12)
    m["slot"] = solid("121315", 2, 13)
    m["slot_gray"] = solid("3a3d44", 2, 14)

    g = Canvas()
    for x in range(16):
        for y in range(16):
            g.px(x, y, "e2bb55" if x % 2 == 0 else "a8862f")
    m["gold"] = g

    lab = Canvas()
    noise(lab, 0, 0, 15, 15, "f0f0ee", 2, 15)
    for (y, x1) in [(3, 12), (6, 9), (9, 13), (12, 7)]:
        lab.hline(3, x1, y, "9a9a96")
    m["label"] = lab

    f = Canvas()
    noise(f, 0, 0, 15, 15, "222327", 2, 16)
    fan(f, 8, 8, 7.4, frame="2a2b30", blade="4f5158", hub="70727a", dark="17181b")
    for (x, y) in [(0, 0), (15, 0), (0, 15), (15, 15)]:
        f.px(x, y, "3a3c42")
    m["fan"] = f

    mesh = Canvas()
    noise(mesh, 0, 0, 15, 15, "2a2b30", 2, 17)
    for x in range(16):
        for y in range(16):
            if (x % 3 == 1 and y % 2 == 0) or (x % 3 == 2 and y % 2 == 1):
                mesh.px(x, y, "131416")
    m["mesh"] = mesh

    hb = Canvas()
    noise(hb, 0, 0, 15, 15, "4a4e56", 3, 18)
    for x in (2, 7, 12):
        hb.px(x, 5, "202226"); hb.px(x, 10, "202226")
    hb.hline(0, 15, 0, "5c6068"); hb.hline(0, 15, 15, "33363c")
    m["hdd_body"] = hb

    ht = Canvas()
    noise(ht, 0, 0, 15, 15, M_BASE, 3, 19)
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14), (8, 1)]:
        ht.px(x, y, M_ENGR)
    for x in range(16):
        for y in range(16):
            if abs(math.hypot(x - 7.5, y - 9.5) - 3.2) < 0.5:
                ht.px(x, y, "c2c6cc")
    m["hdd_top"] = ht

    con = Canvas()
    noise(con, 0, 0, 15, 15, "17181b", 2, 20)
    for x in range(2, 15, 4):
        for y in range(3, 14, 5):
            con.rect(x, y, x + 2, y + 2, "2a2b30")
            con.px(x + 1, y + 1, "6a6c72")
    m["connectors"] = con

    m["gpu_fans_1"] = gpu_bottom(1)
    m["gpu_fans_2"] = gpu_bottom(2)
    m["gpu_fans_3"] = gpu_bottom(3)
    return m


def gpu_bottom(n):
    """Нижняя сторона видеокарты (32x32). Используется область u 0..15 текстуры
    (её доля совпадает с пропорциями днища карты), вентиляторы вдоль длинной стороны."""
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, "202125", 2, 30 + n)
    w = 15
    if n == 1:      # турбина в конце + рёбра
        fan(c, 7, 25, 6.2, frame="2a2b30", blade="4f5158", hub="70727a", dark="17181b")
        for y in range(2, 17, 2):
            c.hline(2, 12, y, "15161a")
    else:
        step = 32 / n
        r = min(6.6, step / 2 - 0.6)
        for i in range(n):
            fan(c, 7.5, step * i + step / 2, r, frame="2a2b30", blade="4f5158", hub="70727a", dark="17181b")
    return c


def mobo_face(style):
    """Лицевая сторона платы 32x32. Пиксель (tu, tv) ↔ z = 14.9 - tu*8.7/32, y = 14.8 - tv*10.3/32."""
    pcb = ["2c5e3f", "26352e", "23262b", "1e2126", "d8dadc"][style]
    trace = ["357049", "30433a", "2c3036", "262a30", "c8cbcf"][style]
    silk = ["8fb59a", "77867e", "6f747c", "6a7078", "8d9196"][style]
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, pcb, 3, 40 + style)

    def T(z, y):
        return int(round((14.9 - z) * 32 / 8.7)), int(round((14.8 - y) * 32 / 10.3))

    rnd = random.Random(50 + style)
    for _ in range(14):                          # дорожки
        y = rnd.randint(2, 29); x0 = rnd.randint(1, 20); x1 = x0 + rnd.randint(4, 10)
        c.hline(x0, min(30, x1), y, trace)
    for _ in range(10):
        x = rnd.randint(2, 29); y0 = rnd.randint(1, 20)
        c.vline(x, y0, min(30, y0 + rnd.randint(3, 8)), trace)
    for (z, y) in [(14.6, 14.5), (6.5, 14.5), (14.6, 4.8), (6.5, 4.8), (10.7, 8.0), (6.5, 9.5)]:
        tu, tv = T(z, y)                          # монтажные отверстия
        c.rect(tu - 1, tv - 1, tu, tv, "b9bec5"); c.px(tu, tv, "6a6e74")
    for _ in range(40):                          # мелкие SMD-компоненты
        x, y = rnd.randint(1, 30), rnd.randint(1, 30)
        c.px(x, y, rnd.choice(["8a6a3d", "c79a5b", "3a3c42", silk]))
    tu, tv = T(12.0, 10.6)
    c.hline(tu, tu + 5, tv, silk)                # подписи-шелкография
    tu, tv = T(13.4, 7.6)
    c.hline(tu, tu + 3, tv, silk)
    return c


def case_tray():
    """Поддон корпуса под материнскую плату (u = 16 - z, v = 16 - y)."""
    c = Canvas()
    noise(c, 0, 0, 15, 15, "24262b", 2, 60)
    for v in (3, 6, 9):                          # резиновые заглушки для кабелей у переднего края платы
        c.rect(10, v, 10, v + 1, "101113"); c.px(10, v, "34363c")
    for u in (3, 6):                             # снизу
        c.rect(u, 11, u + 1, 11, "101113")
    c.rect(3, 3, 5, 6, "1a1b1f")                 # вырез под бэкплейт кулера
    return c
