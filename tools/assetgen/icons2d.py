"""2D-иконки деталей 16×16 по 3D-моделям (models6.py): те же варианты (vis) и раскладка.

Рисунок не зависит от конкретных цветов: всё, что окрашивается, умножается на цвет слоя
(1 — основной, 2 — подсветка/наклейка, 3 — текстолит). layers() раскладывает иконку на 4 текстуры
для item/generated (layer0 — неокрашиваемые пиксели), а игра красит их через ItemColor.
"""
from PIL import Image
import math

def rgb(t):
    return ((t >> 16) & 255, (t >> 8) & 255, t & 255)

def mul(c, t):
    return tuple(min(255, int(a * b / 255)) for a, b in zip(c, rgb(t) if isinstance(t, int) else t))

def sh(c, k):
    return tuple(max(0, min(255, int(ch * k))) for ch in c[:3])

def lerp(a, b, t):
    return tuple(int(x + (y - x) * t) for x, y in zip(a, b))

METAL = (214, 218, 224)
DARK = (34, 36, 40)
GOLD = (232, 186, 70)
COPPER = (214, 128, 72)
NICKEL = (190, 194, 200)
CHIP = (38, 40, 44)

class Icon:
    def __init__(self):
        self.im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        self.p = self.im.load()
    def px(self, x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            self.p[x, y] = tuple(c[:3]) + (255,)
    def rect(self, x0, y0, x1, y1, c):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                self.px(x, y, c)
    def panel(self, x0, y0, x1, y1, c, outline=None, bevel=True):
        """Заливка с обводкой: сверху-слева светлее, снизу-справа темнее (как предметы Minecraft)."""
        self.rect(x0, y0, x1, y1, c)
        if bevel:
            for x in range(x0, x1 + 1):
                self.px(x, y0, sh(c, 1.18)); self.px(x, y1, sh(c, 0.72))
            for y in range(y0, y1 + 1):
                self.px(x0, y, sh(c, 1.1)); self.px(x1, y, sh(c, 0.78))
        if outline:
            for x in range(x0, x1 + 1):
                self.px(x, y0, outline); self.px(x, y1, outline)
            for y in range(y0, y1 + 1):
                self.px(x0, y, outline); self.px(x1, y, outline)
    def circle(self, cx, cy, r, c, fill=None):
        for x in range(16):
            for y in range(16):
                d = math.hypot(x + .5 - cx, y + .5 - cy)
                if fill is not None and d <= r - .5: self.px(x, y, fill)
                if r - 1.1 < d <= r: self.px(x, y, c)
    def fan(self, cx, cy, r, frame, blade=None, hub=None, glow=None):
        """Вентилятор: обод, лопасти-спирали (контрастные к корпусу), ступица; glow — светящееся кольцо."""
        if blade is None:
            blade = (108, 112, 120)
        if hub is None:
            hub = (156, 160, 168)
        ring = sh(frame, .5)
        for x in range(16):
            for y in range(16):
                dx, dy = x + .5 - cx, y + .5 - cy
                d = math.hypot(dx, dy)
                if d <= r:
                    a = math.atan2(dy, dx) + d * 0.55
                    c = blade if (int((a / (2 * math.pi)) * 7 * 2) % 2 == 0) else sh(blade, .62)
                    if d > r - 1: c = glow or ring
                    if d < max(1.2, r * .32): c = hub
                    self.px(x, y, c)

def outline_all(icon, color=None):
    """Тёмный контур вокруг непрозрачных пикселей — иконка читается на любом фоне слота."""
    src = icon.im.copy(); p = src.load()
    for x in range(16):
        for y in range(16):
            if p[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and p[nx, ny][3]:
                        n = p[nx, ny]
                        icon.p[x, y] = (color or sh(n, .35)) + (255,) if False else tuple(sh(n, .35)) + (255,)
                        break

# ================================================================ материнская плата
def server_board(slots, m2, tints):
    """Серверная плата сверху: сзади (вверху) SFP+ и порты, два сокета рядом, память рядами вдоль потока воздуха."""
    main, pcb = mul(METAL, tints[0]), mul((230, 232, 228), tints[2])
    i = Icon()
    i.panel(1, 1, 14, 14, pcb)
    for x, y in ((7, 12), (8, 12), (4, 10), (11, 10)):                   # дорожки
        i.px(x, y, sh(pcb, 1.2))
    # сзади: две клетки SFP+, порт BMC, VGA
    i.rect(2, 1, 3, 2, (196, 200, 206)); i.rect(4, 1, 5, 2, (176, 180, 186)); i.px(2, 1, GOLD); i.px(4, 1, GOLD)
    i.rect(6, 1, 6, 2, (40, 42, 46))
    i.rect(10, 1, 11, 1, (60, 90, 150))
    i.rect(12, 2, 13, 3, (40, 42, 46))                                   # питание
    # радиаторы питания за сокетами
    for x0 in (4, 9):
        i.rect(x0, 3, x0 + 2, 3, main)
    # сокеты MS-S
    for x0 in (4, 9):
        i.panel(x0, 5, x0 + 2, 8, (200, 202, 206))
        i.rect(x0 + 1, 6, x0 + 1, 7, (120, 124, 130)); i.px(x0 + 1, 6, GOLD)
    # память: ряды по бокам от каждого сокета (8 слотов — по одному, 12–16 — двойные крайние)
    cols = (3, 7, 8, 12) if slots == 0 else (2, 3, 7, 8, 12, 13)
    for x in cols:
        i.rect(x, 4, x, 10, (52, 54, 60) if x in (2, 7, 12) or (slots == 0 and x == 3) else (160, 164, 170))
        i.px(x, 4, (240, 240, 240)); i.px(x, 10, (240, 240, 240))
    # чип BMC и чипсет
    i.rect(5, 10, 5, 10, CHIP)
    i.panel(9, 11, 12, 12, main)
    # спереди: M.2 и SATA
    for k in range(m2):
        y = 12 + k
        i.rect(3, y, 6, y, (70, 72, 78)); i.px(7 if k == 0 else 2, y, GOLD)
    for x in (9, 11):
        i.rect(x, 13, x + 1, 13, (40, 42, 46))
    return i.im


def motherboard(vis, tints):
    style, slots, m2 = vis
    if style == 5:
        return server_board(slots, m2, tints)
    main, acc, pcb = mul(METAL, tints[0]), rgb(tints[1]), mul((230, 232, 228), tints[2])
    i = Icon()
    i.panel(1, 1, 14, 14, pcb)
    for x, y in ((4, 9), (5, 9), (6, 9), (8, 3), (8, 4), (8, 5), (11, 10), (12, 10)):   # дорожки
        i.px(x, y, sh(pcb, 1.2))
    # сокет процессора
    i.panel(3, 3, 7, 7, (200, 202, 206))
    i.rect(4, 4, 6, 6, (120, 124, 130)); i.px(5, 5, GOLD)
    # слоты памяти
    xs = [10, 12] if slots == 0 else [10, 11, 12, 13]
    for k, x in enumerate(xs):
        i.rect(x, 2, x, 7, (60, 62, 68) if k % 2 == 0 else (150, 154, 160))
        i.px(x, 2, (235, 235, 235))
    # радиаторы питания (VRM)
    if style >= 1:
        i.rect(3, 1, 8, 2, main); i.rect(3, 1, 8, 1, sh(main, 1.15))
        i.rect(1, 3, 2, 8, main); i.rect(1, 3, 1, 8, sh(main, 1.15))
    else:
        for x in (3, 5, 7): i.px(x, 2, (90, 92, 98))
    # кожух разъёмов
    if style >= 2:
        i.rect(1, 1, 2, 2, sh(main, .85))
    if style >= 3:
        i.rect(1, 3, 1, 8, acc)
    # слоты PCIe и M.2
    i.rect(2, 10, 12, 10, (205, 208, 214) if style >= 2 else (50, 52, 58))
    i.rect(2, 13, 8, 13, (50, 52, 58))
    for k in range(m2):
        y = 9 - 3 * k if k else 12
        i.rect(3, 12 if k == 0 else 8, 6, 12 if k == 0 else 8, (70, 72, 78)); i.px(7, 12 if k == 0 else 8, GOLD)
    # чипсет
    if style == 0:
        i.rect(10, 12, 11, 13, CHIP)
    else:
        i.panel(10, 11, 13, 13, main)
        if style in (3, 4): i.px(11, 12, acc); i.px(12, 12, acc)
    # 24-pin
    i.rect(14, 4, 14, 7, (230, 230, 230) if style == 4 else (40, 42, 46))
    return i.im

# ================================================================ процессор
def cpu(vis, tints):
    lid, size, _ = vis
    main, acc, pcb = mul(METAL, tints[0]), rgb(tints[1]), mul((230, 232, 228), tints[2])
    i = Icon()
    i.panel(1, 1, 14, 14, pcb)
    i.px(2, 13, GOLD); i.px(2, 12, GOLD); i.px(3, 13, GOLD)
    if lid == 0:
        d = [2, 3, 3][size]
        i.panel(8 - d, 8 - d, 7 + d, 7 + d, (52, 54, 64))
        i.px(8 - d + 1, 8 - d + 1, (90, 96, 120))
        for x in (4, 11):
            for y in (5, 7, 9): i.px(x, y, (190, 170, 120))
        return i.im
    r = [0, 4, 5, 5, 5][lid]
    i.panel(8 - r - 1, 8 - r - 1, 7 + r + 1, 7 + r + 1, sh(main, .8))
    i.panel(8 - r, 8 - r, 7 + r, 7 + r, main)
    for k, y in enumerate((6, 8, 10)):                   # гравировка на крышке
        i.rect(5, y, 5 + [5, 3, 4][k], y, sh(main, .78))
    if lid == 3:
        i.rect(7, 1, 8, 1, sh(main, .9)); i.rect(7, 14, 8, 14, sh(main, .9))
    if lid == 4:
        i.rect(9, 4, 11, 4, acc); i.px(10, 4, sh(acc, 1.2))
    return i.im

# ================================================================ охлаждение
def cooler(vis, tints):
    kind, fans, rad = vis
    main, acc = mul(METAL, tints[0]), rgb(tints[1])
    i = Icon()
    if kind == 0:                                         # штатный: круглый радиатор под вентилятором
        i.panel(1, 1, 14, 14, main)
        i.fan(8, 8, 6.4, main)
        return i.im
    if kind == 1:                                         # низкий: вентилятор над рёбрами
        for y in range(2, 14, 2): i.rect(1, y, 14, y, NICKEL)
        for x, y in ((3, 14), (6, 14), (9, 14), (12, 14)): i.rect(x, 13, x, 15, COPPER)
        i.panel(2, 1, 13, 12, main)
        i.fan(8, 6.5, 5.3, main)
        return i.im
    if kind in (2, 3):                                    # башня (одна или две) с рёбрами и трубками
        towers = [(4, 13)] if kind == 2 else [(2, 6), (9, 13)]
        for (a, b) in towers:
            for y in range(2, 13):
                i.rect(a, y, b, y, NICKEL if y % 2 == 0 else (150, 154, 160))
            i.rect(a, 1, b, 1, main)                      # крышка башни
        for x in ((5, 7, 9, 11) if kind == 2 else (3, 5, 10, 12)):
            i.rect(x, 13, x, 15, COPPER)
        if kind == 2:
            i.panel(1, 2, 7, 12, sh(main, .55)); i.fan(4.5, 7.5, 3.4, main)
            if fans: i.rect(14, 3, 15, 11, sh(main, .55))
        else:
            i.panel(6, 3, 9, 12, sh(main, .55)); i.fan(8, 7.5, 2.2, main)
        i.rect(4 if kind == 2 else 2, 1, 13, 1, sh(main, 1.1))
        if kind == 2: i.rect(8, 1, 13, 1, acc)
        return i.im
    # kind 4: водяное охлаждение — радиатор с вентиляторами, трубки, помпа с подсветкой
    n = rad + 1
    i.panel(0, 1, 15, 6, main)
    w = 16 / n
    for k in range(n):
        i.fan(w * (k + .5), 3.6, min(2.6, w / 2 - .2), main, glow=None)
    for x in (4, 6):
        i.rect(x, 7, x, 9, (30, 30, 34))
    i.circle(5, 12, 3.6, sh(acc, 1.0), fill=(60, 64, 72))
    i.rect(4, 11, 5, 12, (150, 154, 162))
    return i.im

# ================================================================ память
def ram(vis, tints):
    hs, led, chips = vis
    main, acc, pcb = mul(METAL, tints[0]), rgb(tints[1]), mul((230, 232, 228), tints[2])
    i = Icon()
    i.panel(0, 6, 15, 11, pcb)
    for x in range(1, 15):
        if x != 9: i.px(x, 12, GOLD)
    if hs == 0:
        n = [2, 4, 8][chips]
        w = 14 // n
        for k in range(n):
            x0 = 1 + k * w
            i.rect(x0, 7, x0 + max(1, w - 2), 9, CHIP)
        return i.im
    top = {1: 5, 2: 4, 3: 3}[hs]
    i.panel(0, top, 15, 10, main)
    i.rect(1, top + 2, 14, top + 2, sh(main, .82))       # штамповка
    if hs == 3:
        i.rect(3, top + 4, 12, top + 4, sh(main, 1.15))
    if hs == 2 and not led:
        for x in range(1, 15, 2): i.px(x, top - 1, main)
    if led:
        i.rect(1, top - 2, 14, top - 1, acc)
        i.rect(1, top - 2, 14, top - 2, sh(acc, 1.25))
        i.rect(0, top - 2, 0, top - 1, main); i.rect(15, top - 2, 15, top - 1, main)
    return i.im

# ================================================================ видеокарта
def gpu(vis, tints):
    ck, length, v2 = vis
    main, acc, pcb = mul(METAL, tints[0]), rgb(tints[1]), mul((230, 232, 228), tints[2])
    i = Icon()
    x0 = [4, 2, 1][length]
    i.panel(x0, 4, 15, 12, pcb)
    for x in range(x0 + 3, x0 + 8): i.px(x, 13, GOLD)
    i.rect(0 if x0 > 0 else 0, 2, 0, 13, (190, 194, 200))  # планка
    i.rect(1, 2, 1, 3, (190, 194, 200)); i.rect(1, 12, 1, 13, (190, 194, 200))
    if ck == 0:                                           # голая плата
        cx = (x0 + 15) // 2
        i.panel(cx - 2, 6, cx + 1, 9, (60, 140, 80)); i.rect(cx - 1, 7, cx, 8, (52, 54, 64))
        for k, (dx, dy) in enumerate([(-4, 0), (3, 0), (-4, 3), (3, 3), (0, -2), (-2, -2), (0, 4), (-2, 4)][:[2, 4, 6, 8, 8][v2]]):
            i.rect(cx + dx, 6 + dy, cx + dx, 6 + dy + 1, CHIP)
        return i.im
    if ck == 1:                                           # пассивный радиатор
        for x in range(x0 + 1, 15, 2): i.rect(x, 4, x, 10, main)
        i.rect(x0 + 1, 10, 14, 10, sh(main, .8))
        return i.im
    i.panel(x0, 3, 15, 11, main)
    if ck == 2:                                           # турбина
        i.fan(12.5, 7, 3.2, main)
        i.rect(x0 + 1, 5, 9, 5, sh(main, .8)); i.rect(x0 + 1, 8, 9, 8, sh(main, .8))
        i.rect(x0 + 1, 12, 14, 12, acc)
        return i.im
    n = 2 if ck == 3 else 3
    span = 15 - x0
    r = min(3.4, span / n / 2 - .1)
    for k in range(n):
        i.fan(x0 + .5 + span / n * (k + .5), 7.2, r, main)
    i.rect(x0 + 1, 3, 14, 3, acc)
    return i.im

# ================================================================ блок питания
def psu(vis, tints):
    side, modular, _ = vis
    main, acc = mul(METAL, tints[0]), rgb(tints[1])
    i = Icon()
    i.panel(1, 3, 14, 14, main)
    i.rect(11, 4, 13, 13, sh(main, .82))                  # боковая стенка
    if side == 1:
        for x in (11, 13):
            for y in range(5, 13, 2): i.px(x, y, sh(main, .5))
    i.fan(6.5, 8.5, 4.6, main, blade=(60, 62, 68))
    for r in (2.4, 3.6):
        i.circle(6.5, 8.5, r, sh(main, .9))
    if side == 2:
        i.rect(1, 3, 14, 3, acc); i.rect(1, 14, 14, 14, acc)
    i.rect(12, 6, 12, 8, (210, 210, 210))                 # наклейка
    if modular == 2:
        i.rect(3, 1, 10, 2, (40, 42, 46)); [i.px(x, 1, (120, 120, 120)) for x in (4, 6, 8)]
    elif modular == 1:
        i.rect(3, 2, 6, 2, (40, 42, 46)); i.rect(8, 0, 10, 2, (24, 24, 26))
    else:
        i.rect(8, 0, 11, 2, (24, 24, 26)); i.px(9, 0, (60, 60, 60))
    return i.im

# ================================================================ жёсткий диск
def hdd(vis, tints):
    form, lid, _ = vis
    main, acc = mul(METAL, tints[0]), rgb(tints[1])
    label = mul((240, 240, 240), tints[1])
    i = Icon()
    x0, y0, x1, y1 = (3, 2, 12, 13) if form == 0 else (2, 1, 13, 14)
    i.panel(x0, y0, x1, y1, main)
    for x, y in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        i.px(x, y, sh(main, .6))
    if lid == 1:
        i.panel(x0 + 2, y0 + 2, x1 - 2, y0 + 4, label)
        for k in range(3 if form else 2):
            i.rect(x0 + 3 + k * 3, y0 + 6, x0 + 3 + k * 3, y1 - 2, sh(main, .8))
    else:
        i.panel(x0 + 2, y0 + 2, x1 - 2, y0 + 6 + form, label)
        for k, x in enumerate(range(x0 + 3, x1 - 2)):     # штрихкод: вертикальные полоски
            if k % 2 == 0 or k % 5 == 3:
                i.px(x, y0 + 3, (40, 40, 40)); i.px(x, y0 + 4, (40, 40, 40))
        i.rect(x0 + 3, y0 + 5 + form, x1 - 4, y0 + 5 + form, sh(label, .55))
        cy = y1 - 3
        i.circle((x0 + x1 + 1) / 2, cy + .5, 2.2, sh(main, .75), fill=NICKEL)
    if lid == 2:
        i.rect(x1, y0 + 3, x1, y1 - 3, acc)
    i.rect(x0 + 1, y1, x0 + 5, y1, (40, 42, 46))
    return i.im

# ================================================================ SSD
def ssd(vis, tints):
    kind, cool, _ = vis
    main, acc, pcb = mul(METAL, tints[0]), rgb(tints[1]), mul((230, 232, 228), tints[2])
    label = mul((240, 240, 240), tints[1])
    i = Icon()
    if kind == 0:                                         # 2,5" SATA
        i.panel(2, 2, 13, 13, main)
        if cool < 2:
            i.panel(4, 4, 11, 10, label)
            i.rect(5, 5, 10, 5, sh(label, .5))                     # название
            for x in range(5, 11):
                if x % 2 == 1 or x == 8: i.px(x, 7, (40, 40, 40)); i.px(x, 8, (40, 40, 40))
        else:
            i.rect(2, 7, 13, 7, acc); i.rect(2, 7, 13, 7, acc)
            i.panel(8, 9, 11, 11, sh(main, 1.2))
        i.rect(3, 13, 7, 13, (40, 42, 46))
        return i.im
    # M.2
    i.panel(0, 6, 15, 9, pcb)
    i.rect(14, 6, 15, 9, GOLD); i.px(14, 7, pcb)
    i.px(0, 7, (0, 0, 0, 0)) if False else None
    if cool == 0:
        for x0 in (3, 7): i.rect(x0, 7, x0 + 2, 8, CHIP)
        i.rect(11, 7, 12, 8, CHIP)
        i.rect(2, 6, 10, 6, label)
    elif cool == 1:
        i.panel(1, 5, 13, 10, main); i.rect(3, 7, 10, 7, label)
    else:
        i.panel(1, 4, 13, 10, main)
        for x in range(2, 13, 2): i.rect(x, 3, x, 4, main)
        i.rect(2, 7, 12, 7, acc)
    return i.im

BUILD = {"MOTHERBOARD": motherboard, "CPU": cpu, "COOLER": cooler, "RAM": ram, "GPU": gpu, "PSU": psu, "HDD": hdd, "SSD": ssd}

def make(p):
    return BUILD[p["type"]](p["vis"], p["tints"])


def layers(t, vis):
    """Четыре слоя иконки: [неокрашиваемый, основной цвет, подсветка, текстолит] (RGBA 16×16)."""
    W, K = 0xFFFFFF, 0x000000
    base = BUILD[t](vis, [W, W, W])
    probes = [BUILD[t](vis, [K, W, W]), BUILD[t](vis, [W, K, W]), BUILD[t](vis, [W, W, K])]
    out = [Image.new("RGBA", (16, 16), (0, 0, 0, 0)) for _ in range(4)]
    bp = base.load()
    pp = [p.load() for p in probes]
    op = [o.load() for o in out]
    for x in range(16):
        for y in range(16):
            c = bp[x, y]
            if c[3] == 0:
                continue
            layer = 0
            for k in range(3):
                if pp[k][x, y] != c:
                    layer = k + 1
                    break
            op[layer][x, y] = c
    return out
