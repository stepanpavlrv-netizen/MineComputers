"""Текстуры комплектующих.

Каждая деталь состоит из слоёв:
  base    — не перекрашивается (платы, контакты, вентиляторы)
  primary — светлый нейтральный, перекрашивается основным цветом (крышки, радиаторы, кожухи)
  accent  — почти белый, перекрашивается цветом подсветки / наклейки
"""
from px import Canvas, fan, hx

# нейтральный "алюминий" для перекрашиваемых слоёв
M_BASE, M_HI, M_LO, M_EDGE, M_ENGR = "cfd3d8", "eef0f2", "8e939a", "b3b8be", "a5aab1"
LED, LED_LO = "f4f4f4", "d4d4d4"

GOLD, GOLD_D = "e2bb55", "a8862f"
CAP, CAP_D = "c79a5b", "8a6a3d"


def layers(base, primary=None, accent=None):
    return {"base": base, "primary": primary, "accent": accent}


# ======================= ПРОЦЕССОРЫ =======================
def substrate(c, a, b, base, edge, hi):
    c.rect(a, a, b, b, base)
    c.hline(a, b, b, edge); c.vline(b, a, b, edge)
    c.hline(a, b - 1, a, hi); c.vline(a, a, b - 1, hi)


def lid(c, x0, y0, x1, y1):
    c.bevel(x0, y0, x1, y1, M_BASE, M_HI, M_LO)
    c.px(x0, y1, M_EDGE); c.px(x1, y0, M_EDGE)


def cpu(style):
    b = Canvas()
    if style == 0:  # открытый кристалл, маленькая плата
        substrate(b, 2, 13, "2c5e3f", "1b3d29", "3f7a55")
        b.rect(6, 6, 9, 9, "2b3048")
        b.hline(6, 9, 6, "4d5a85"); b.vline(6, 6, 9, "4d5a85")
        b.hline(7, 9, 9, "1a1d2c"); b.vline(9, 7, 9, "1a1d2c")
        b.px(7, 7, "6c7bb0")
        for (x, y) in [(4, 4), (10, 4), (4, 10), (10, 10)]:
            b.px(x, y, CAP); b.px(x + 1, y, CAP_D)
        b.px(3, 12, "d9b45a")
        return layers(b)

    if style == 1:  # маленькая крышка
        substrate(b, 2, 13, "2a5a40", "19392a", "3c7556")
        for y in (5, 9):
            b.px(3, y, CAP); b.px(3, y + 1, CAP_D)
            b.px(12, y, CAP); b.px(12, y + 1, CAP_D)
        b.px(3, 12, "d9b45a")
        p = Canvas(); lid(p, 5, 5, 10, 10)
        p.hline(6, 8, 7, M_ENGR)
        return layers(b, p)

    if style == 2:  # стандартная крышка
        substrate(b, 1, 14, "245a43", "163a2b", "377a5c")
        for y in (5, 9):
            b.px(2, y, CAP); b.px(2, y + 1, CAP_D)
            b.px(13, y, CAP); b.px(13, y + 1, CAP_D)
        b.px(2, 13, "d9b45a")
        p = Canvas(); lid(p, 4, 4, 11, 11)
        p.hline(5, 9, 6, M_ENGR); p.hline(5, 8, 8, M_ENGR)
        return layers(b, p)

    if style == 3:  # крупная крышка с вырезами
        substrate(b, 0, 15, "203f36", "132822", "2f5a4d")
        for y in (6, 9):
            b.px(1, y, CAP); b.px(14, y, CAP)
        b.px(1, 14, "d9b45a"); b.px(2, 14, "b8963f"); b.px(1, 13, "b8963f")
        p = Canvas(); lid(p, 3, 2, 12, 13)
        for (x, y) in [(3, 2), (12, 2), (3, 13), (12, 13)]:
            p.clear(x, y)
        p.px(4, 2, M_HI); p.px(3, 3, M_HI); p.px(12, 12, M_LO); p.px(11, 13, M_LO)
        p.rect(5, 5, 8, 6, "bfc3c9"); p.px(5, 5, M_ENGR)
        p.hline(5, 10, 8, M_ENGR); p.hline(5, 9, 10, M_ENGR)
        return layers(b, p)

    # style 4: флагман — большая крышка с фаской и вставкой-логотипом
    substrate(b, 0, 15, "1c2230", "10141c", "2a3346")
    for x in (3, 6, 9, 12):
        b.px(x, 1, CAP_D); b.px(x, 14, CAP_D)
    b.px(1, 14, "d9b45a"); b.px(2, 14, "b8963f"); b.px(1, 13, "b8963f")
    p = Canvas()
    p.rect(2, 2, 13, 13, M_BASE)
    p.hline(3, 12, 2, M_HI); p.vline(2, 3, 12, M_HI)
    p.hline(3, 12, 13, M_LO); p.vline(13, 3, 12, M_LO)
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        p.clear(x, y)
    p.rect(4, 4, 11, 11, "c4c8ce")          # фаска
    p.hline(4, 11, 4, M_HI); p.vline(4, 4, 11, M_HI)
    p.hline(5, 11, 11, M_EDGE); p.vline(11, 5, 11, M_EDGE)
    p.hline(5, 10, 9, M_ENGR); p.hline(5, 8, 10, M_ENGR)
    a = Canvas()                            # вставка-логотип (цвет акцента)
    a.rect(6, 5, 9, 7, LED); a.hline(6, 9, 7, LED_LO)
    return layers(b, p, a)


# ======================= ПАМЯТЬ =======================
def ram_pcb(c, top, pcb, pcb_d, pcb_h):
    c.rect(0, top, 15, 11, pcb)
    c.hline(0, 15, top, pcb_h)
    c.vline(15, top, 11, pcb_d)
    for x in range(1, 15):
        c.px(x, 11, GOLD if x % 2 else GOLD_D)
        c.px(x, 12, "c9a243" if x % 2 else "8c6f24")
    c.clear(9, 11, 9, 12)
    c.clear(0, 11); c.clear(15, 11)
    c.clear(0, 8); c.clear(15, 8)


def ram_heatsink(top, bottom=9, fins=False, pattern=None):
    p = Canvas()
    p.rect(0, top, 15, bottom, M_BASE)
    p.hline(0, 15, top, M_HI)
    p.hline(0, 15, bottom, M_LO)
    p.vline(0, top, bottom, M_EDGE); p.vline(15, top, bottom, "9a9fa6")
    if fins:
        for x in range(1, 15, 2):
            p.px(x, top, M_ENGR)
    if pattern == "plate":
        p.rect(3, top + 2, 12, top + 3, "bec2c8")
        p.hline(3, 12, top + 2, "a9aeb5"); p.hline(3, 12, top + 4, "e2e5e8")
    if pattern == "diag":
        for i in range(0, 16, 4):
            for k in range(bottom - top - 1):
                p.px(i + k, top + 1 + k, "bfc3c9")
        p.rect(4, bottom - 2, 11, bottom - 1, "a9aeb5")
    return p


def ram(style):
    b = Canvas()
    if style == 0:  # зелёная плата, голые чипы
        ram_pcb(b, 4, "2f6b3a", "1d4526", "45874f")
        for x in (1, 4, 10, 13):
            b.rect(x, 6, x + 1, 9, "1d1e22"); b.hline(x, x + 1, 6, "3a3c44")
        b.rect(6, 6, 8, 8, "e6e4dc"); b.px(7, 7, "8f8d86"); b.px(6, 8, "bdbbb3")
        return layers(b)
    ram_pcb(b, 4, "2a2b30", "1a1b1f", "3a3c43")
    if style == 1:
        return layers(b, ram_heatsink(4, pattern="plate"))
    if style == 2:
        return layers(b, ram_heatsink(3, fins=True, pattern="plate"))
    if style == 3:
        a = Canvas(); a.rect(1, 1, 14, 2, LED); a.hline(1, 14, 2, LED_LO)
        return layers(b, ram_heatsink(3, fins=True, pattern="plate"), a)
    # style 4: высокий радиатор с диагональным рисунком и широкой подсветкой
    p = ram_heatsink(3, pattern="diag")
    a = Canvas(); a.rect(0, 0, 15, 2, LED); a.hline(0, 15, 2, LED_LO)
    a.px(0, 0, LED_LO); a.px(15, 0, LED_LO)
    return layers(b, p, a)


# ======================= ВИДЕОКАРТЫ (вид на вентиляторы) =======================
def gpu_base(b, y0, y1, x1=15):
    # планка-кронштейн слева
    b.rect(0, y0 - 1, 0, y1 + 1, "9ea3aa"); b.px(0, y0 - 1, "c9ccd1")
    b.rect(1, y0, 1, y1, "6f747c")
    # PCB, выглядывающая снизу, и разъём PCIe
    b.rect(1, y1 + 1, x1, y1 + 1, "1f2a22")
    for x in range(3, 10):
        b.px(x, y1 + 2, GOLD if x % 2 else GOLD_D)


def shroud(y0, y1, x0=2, x1=15, holes=()):
    p = Canvas()
    p.bevel(x0, y0, x1, y1, M_BASE, M_HI, M_LO)
    for (cx, cy, r) in holes:
        for x in range(16):
            for y in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= (r + 0.15) ** 2:
                    p.clear(x, y)
    return p


def gpu(style):
    b = Canvas()
    if style == 0:  # маленькая карта с радиатором без вентилятора
        gpu_base(b, 6, 11, x1=12)
        b.rect(2, 6, 12, 11, "2d6a3d")
        b.rect(10, 7, 11, 8, "1d1e22"); b.rect(10, 9, 11, 10, "1d1e22")
        p = Canvas()
        p.rect(3, 6, 8, 11, M_BASE)
        for x in range(3, 9, 2):
            p.vline(x, 6, 11, M_LO)
        p.hline(3, 8, 6, M_HI)
        return layers(b, p)
    if style == 1:  # турбина
        gpu_base(b, 4, 11)
        b.rect(2, 4, 15, 11, "202125")
        fan(b, 11.5, 7.5, 3.4)
        p = shroud(4, 11, holes=[(11.5, 7.5, 3.4)])
        p.rect(3, 6, 6, 9, M_ENGR); p.hline(3, 6, 6, M_LO)   # решётка турбины
        return layers(b, p)
    if style == 2:  # два вентилятора
        gpu_base(b, 4, 11)
        b.rect(2, 4, 15, 11, "202125")
        fan(b, 5.5, 7.5, 3.2); fan(b, 11.5, 7.5, 3.2)
        return layers(b, shroud(4, 11, holes=[(5.5, 7.5, 3.2), (11.5, 7.5, 3.2)]))
    if style == 3:  # три вентилятора
        gpu_base(b, 3, 12)
        b.rect(2, 3, 15, 12, "202125")
        for cx in (4.5, 9.0, 13.5):
            fan(b, cx, 7.5, 2.4)
        return layers(b, shroud(3, 12, holes=[(4.5, 7.5, 2.4), (9.0, 7.5, 2.4), (13.5, 7.5, 2.4)]))
    # style 4: три вентилятора + RGB-полоса и угловатый кожух
    gpu_base(b, 3, 12)
    b.rect(2, 3, 15, 12, "202125")
    for cx in (4.5, 9.0, 13.5):
        fan(b, cx, 7.5, 2.4)
    p = shroud(3, 12, holes=[(4.5, 7.5, 2.4), (9.0, 7.5, 2.4), (13.5, 7.5, 2.4)])
    p.clear(15, 3); p.clear(15, 12)
    a = Canvas()
    a.hline(3, 14, 3, LED); a.hline(3, 14, 12, LED_LO)
    return layers(b, p, a)


# ======================= НАКОПИТЕЛИ =======================
def storage(style):
    b = Canvas()
    if style in (0, 1):  # HDD 3.5" / 2.5"
        x0, y0, x1, y1 = (2, 1, 13, 14) if style == 0 else (3, 3, 12, 13)
        b.rect(x0, y0, x1, y1, "3a3d44")
        p = Canvas()
        p.bevel(x0, y0, x1, y1 - 1, M_BASE, M_HI, M_LO)
        for (x, y) in [(x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 2), (x1 - 1, y1 - 2)]:
            p.px(x, y, "7f848c")                     # винты
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 - 1
        p.px(int(cx), int(cy), M_ENGR)               # выпуклость шпинделя
        a = Canvas()
        a.rect(x0 + 2, y0 + 2, x1 - 2, y0 + 5 + (2 if style == 0 else 0), LED)
        a.hline(x0 + 3, x1 - 4, y0 + 3, "bcbcbc"); a.hline(x0 + 3, x1 - 6, y0 + 4, "cfcfcf")
        b.hline(x0 + 2, x0 + 7, y1, "2b2c30")        # разъёмы SATA снизу
        return layers(b, p, a)
    if style == 2:  # SATA SSD 2.5"
        b.rect(2, 3, 13, 12, "2b2c30")
        b.hline(4, 7, 12, GOLD_D); b.hline(9, 11, 12, GOLD_D)
        p = Canvas(); p.bevel(2, 3, 13, 11, M_BASE, M_HI, M_LO)
        a = Canvas(); a.rect(4, 5, 11, 9, LED); a.hline(5, 9, 6, "bcbcbc"); a.hline(5, 8, 8, "cfcfcf")
        return layers(b, p, a)
    # NVMe M.2
    b.rect(0, 6, 15, 10, "1f2a22")
    b.hline(0, 15, 6, "2e3d31")
    for y in range(6, 11):
        b.px(0, y, GOLD if y % 2 else GOLD_D)
    b.clear(0, 8)
    b.px(15, 8, "0f1410")                            # вырез под винт
    b.rect(2, 7, 4, 9, "1d1e22"); b.rect(6, 7, 9, 9, "1d1e22"); b.rect(11, 7, 13, 9, "1d1e22")
    if style == 3:
        a = Canvas(); a.rect(5, 7, 12, 9, LED); a.hline(6, 10, 8, "bcbcbc")
        return layers(b, None, a)
    # style 4: NVMe с радиатором
    p = Canvas()
    p.rect(1, 5, 14, 11, M_BASE)
    p.hline(1, 14, 5, M_HI); p.hline(1, 14, 11, M_LO)
    for x in range(2, 14, 2):
        p.vline(x, 6, 10, M_ENGR)
    a = Canvas(); a.hline(1, 14, 8, LED)
    return layers(b, p, a)


# ======================= ОХЛАЖДЕНИЕ =======================
def cooler(style):
    b = Canvas()
    if style == 0:  # боксовый кулер, вид сверху
        b.rect(2, 2, 13, 13, "26272b")
        fan(b, 8, 8, 5.2)
        p = Canvas()
        p.rect(1, 1, 14, 14, M_BASE)
        p.hline(1, 14, 1, M_HI); p.vline(1, 1, 14, M_HI)
        p.hline(2, 14, 14, M_LO); p.vline(14, 2, 14, M_LO)
        for x in range(16):
            for y in range(16):
                if (x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2 <= 5.4 ** 2:
                    p.clear(x, y)
        return layers(b, p)

    if style == 1:  # низкопрофильный: вентилятор сверху, широкие рёбра, вид сбоку
        b.rect(4, 13, 11, 14, "b07a4c"); b.hline(4, 11, 13, "d39a63")
        for x in (3, 12):
            b.vline(x, 7, 13, "c4834f")
        b.rect(1, 3, 14, 5, "2a2b30"); b.hline(2, 13, 4, "55575f")
        b.px(1, 3, "3a3c42"); b.px(14, 3, "3a3c42")
        p = Canvas()
        for y in range(6, 13):
            p.hline(1, 14, y, M_BASE if y % 2 == 0 else M_LO)
        p.hline(1, 14, 6, M_HI)
        return layers(b, p)

    if style in (2, 3):  # башни: вид сбоку — рёбра, тепловые трубки, вентилятор
        towers = {2: [(4, 11)], 3: [(1, 6), (9, 14)]}[style]
        top = 1
        b.rect(4, 14, 11, 15, "b07a4c"); b.hline(4, 11, 14, "d39a63")   # медное основание
        for x in (5, 7, 8, 10):
            b.vline(x, top, 13, "c4834f")                               # тепловые трубки
        p = Canvas()
        for (x0, x1) in towers:
            for y in range(top, 13):
                p.hline(x0, x1, y, M_BASE if (y - top) % 2 == 0 else M_LO)
            p.hline(x0, x1, top, M_HI)
        a = None
        if style == 2:  # вентилятор сбоку башни (тонкий профиль)
            b.rect(12, 2, 14, 12, "2a2b30"); b.vline(13, 3, 11, "55575f")
        if style == 3:  # вентилятор между башнями
            b.rect(7, 2, 8, 12, "2a2b30"); b.vline(7, 3, 11, "55575f")
            a = Canvas(); a.vline(8, 3, 11, LED)
        return layers(b, p, a)

    # style 4: СЖО — радиатор с тремя вентиляторами, шланги, помпа
    b.rect(0, 1, 15, 6, "2a2b30")
    for cx in (2.5, 8.0, 13.5):
        fan(b, cx, 3.5, 2.2)
    b.vline(5, 7, 10, "18191c"); b.vline(10, 7, 10, "18191c")     # шланги
    b.vline(6, 7, 9, "2c2d32"); b.vline(11, 7, 9, "2c2d32")
    b.rect(4, 10, 11, 15, "1e1f23")
    p = Canvas()
    p.hline(0, 15, 0, M_HI); p.hline(0, 15, 6, M_LO)               # рамка радиатора
    p.vline(0, 0, 6, M_EDGE); p.vline(15, 0, 6, M_EDGE)
    p.bevel(4, 10, 11, 15, M_BASE, M_HI, M_LO)                     # корпус помпы
    a = Canvas()
    for x in range(5, 11):
        for y in range(11, 15):
            d = ((x + 0.5 - 8) ** 2 + (y + 0.5 - 13) ** 2) ** 0.5
            if 1.2 <= d <= 2.3:
                a.px(x, y, LED)                                        # кольцо подсветки
    return layers(b, p, a)


TYPES = {"cpu": cpu, "ram": ram, "gpu": gpu, "storage": storage, "cooler": cooler}
