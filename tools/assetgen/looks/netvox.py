"""Воксельные эскизы сети: кабель, коммутаторы, роутер, радиомачта, задняя панель корпуса. И сцена."""
import sys, math, random
import os as _os; sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__)))
from vox import Model, noise, glow, shade
import models, models2, pics
from models2 import place, keyboard, flat_monitor
from PIL import Image, ImageDraw

CABLE = {"blue": (52, 104, 200), "orange": (230, 126, 34), "green": (60, 160, 80), "gray": (150, 152, 158),
         "red": (200, 50, 50), "yellow": (225, 190, 40), "white": (226, 228, 232)}
PLUG = (196, 214, 222)
GOLD = (232, 186, 70)

def sh(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])

def cable(m, ox, oz, dirs, color, plugs=(), y=0, pulse=None):
    """Кабель на полу блока (ox, oz): 2×2 пикселя, как редстоун — соединяется с соседями.
    dirs — набор 'n','s','e','w' (n = −z, s = +z, w = −x, e = +x); plugs — с какой стороны вилка RJ-45 (в устройство)."""
    c = CABLE[color] if isinstance(color, str) else color
    def put(x, z, yy, k):
        m.v[(ox + x, y + yy, oz + z)] = sh(c, k)
    # узел
    for x in range(6, 10):
        for z in range(6, 10):
            put(x, z, 0, 0.92)
    for x in range(7, 9):
        for z in range(7, 9):
            put(x, z, 1, 1.0)
    arms = {"n": [(x, z) for z in range(0, 7) for x in (7, 8)], "s": [(x, z) for z in range(9, 16) for x in (7, 8)],
            "w": [(x, z) for x in range(0, 7) for z in (7, 8)], "e": [(x, z) for x in range(9, 16) for z in (7, 8)]}
    for d in dirs:
        for (x, z) in arms[d]:
            along = z if d in "ns" else x
            k = 1.0 if (along // 2) % 2 == 0 else 0.84          # витая пара: полоски вдоль кабеля
            put(x, z, 0, k * 0.95)
            put(x, z, 1, k)
    for d in plugs:                                             # вилка у самого края: прозрачный пластик и золотые контакты
        if d == "n": cells = [(x, z) for x in range(6, 10) for z in range(0, 3)]
        elif d == "s": cells = [(x, z) for x in range(6, 10) for z in range(13, 16)]
        elif d == "w": cells = [(x, z) for x in range(0, 3) for z in range(6, 10)]
        else: cells = [(x, z) for x in range(13, 16) for z in range(6, 10)]
        for (x, z) in cells:
            for yy in range(0, 3):
                m.v[(ox + x, y + yy, oz + z)] = PLUG
        edge = {"n": 0, "s": 15, "w": 0, "e": 15}[d]
        for k in range(6, 10):
            p = (ox + k, y + 2, oz + (2 if d == "n" else 13)) if d in "ns" else (ox + (2 if d == "w" else 13), y + 2, oz + k)
            m.v[p] = GOLD
    if pulse:                                                   # «сообщение» бежит по кабелю — светящаяся точка
        px, pz = pulse
        m.v[(ox + px, y + 2, oz + pz)] = glow((255, 240, 150))
        m.v[(ox + px, y + 3, oz + pz)] = glow((255, 255, 220))

def cable_up(m, ox, oz, side, color, y=0, h=16):
    """Подъём кабеля по стороне соседнего блока (как редстоун на ступеньку)."""
    c = CABLE[color]
    for yy in range(0, h):
        k = 1.0 if (yy // 2) % 2 == 0 else 0.84
        for x in (7, 8):
            m.v[(ox + x, y + yy, oz + (15 if side == "s" else 0))] = sh(c, k)

def switch(body=(52, 56, 62), ports=6):
    """Настольный коммутатор: 6 портов спереди, над каждым лампочка, сзади — питание."""
    m = Model()
    m.box(1, 0, 5, 15, 4, 12, noise(body, 3, 201))
    r = random.Random(4)
    F = 11
    for i in range(ports):
        x = 2 + i * 2
        m.v[(x, 1, F)] = (14, 14, 16)                         # гнездо RJ-45
        pass
        on = i not in (3,)
        m.v[(x, 2, F)] = glow((80, 255, 120) if i % 3 else (255, 170, 40)) if on else (40, 44, 48)
    m.paint(lambda x, y, z: y == 3 and z == 6 and 3 <= x <= 12, sh(body, 1.3))   # шов крышки
    m.v[(14, 2, F)] = glow((90, 160, 255))
    return m

def rack_switch():
    """Стоечный коммутатор: два ряда по 12 портов, лампочки, патч-корды свисают разноцветными петлями."""
    m = Model()
    frame = (40, 43, 50)
    m.box(0, 0, 0, 16, 16, 16, noise(frame, 3, 211))
    F = 15
    r = random.Random(8)
    cols = [CABLE["blue"], CABLE["orange"], CABLE["green"], CABLE["yellow"], CABLE["red"], CABLE["white"]]
    for unit_y in (12, 7):
        for x in range(2, 14):
            m.v[(x, unit_y + 1, F)] = (96, 100, 108)
            m.v[(x, unit_y, F)] = (18, 18, 20) if x % 2 == 0 else (96, 100, 108)
            m.v[(x, unit_y + 2, F)] = glow((80, 255, 120)) if x % 2 == 0 and r.random() < 0.8 else (60, 64, 70)
        # патч-корды: из гнезда вниз петлёй
        for k, x in enumerate(range(2, 14, 2)):
            if r.random() < 0.25: continue
            c = cols[(k + unit_y) % len(cols)]
            drop = 2 + (k % 3)
            m.v[(x, unit_y, F + 1)] = PLUG
            for d in range(1, drop + 1):
                m.v[(x, unit_y - d, F + 1)] = c
    for y in range(1, 5):                                       # блок питания снизу
        for x in range(2, 14):
            m.v[(x, y, F)] = (30, 32, 36) if (x + y) % 2 else (54, 58, 64)
    m.v[(13, 3, F)] = glow((90, 160, 255))
    return m

def router(body=(236, 238, 242)):
    """Wi-Fi роутер: плоский корпус, три антенны сзади, ряд синих лампочек спереди."""
    m = Model()
    m.box(3, 0, 5, 13, 2, 12, noise(body, 3, 221))
    F = 11
    for i, x in enumerate(range(5, 11)):
        m.v[(x, 1, F)] = glow((70, 150, 255)) if i != 4 else glow((80, 255, 120))
    for x in (4, 8, 11):
        for y in range(2, 9):
            m.v[(x, y, 5)] = (40, 42, 48) if y > 2 else (90, 92, 98)
    m.paint(lambda x, y, z: y == 1 and 6 <= x <= 9 and 7 <= z <= 9 and (x + z) % 2 == 0, sh(body, 0.85))
    return m

def router_dark():
    m = router((40, 42, 48))
    return m

def radio_mast():
    """Радиомодем и мачта: шкаф с модемом внизу, решётчатая мачта на 3 блока, антенна и красный огонь."""
    m = Model()
    cab = (200, 202, 196)
    m.box(2, 0, 3, 14, 10, 13, noise(cab, 3, 231))
    F = 12
    m.paint(lambda x, y, z: z == F and 4 <= x <= 11 and 5 <= y <= 8, (40, 44, 40))      # окошко модема
    for i, x in enumerate(range(5, 11)):
        m.v[(x, 7, F)] = glow((80, 255, 120)) if i % 2 == 0 else glow((255, 170, 40))
    m.v[(5, 6, F)] = glow((255, 80, 60))
    m.paint(lambda x, y, z: z == F and y in (2, 3) and 4 <= x <= 11 and x % 2 == 0, (150, 152, 146))
    steel = (170, 174, 180)
    # мачта: четыре стойки и раскосы
    for y in range(10, 46):
        for (x, z) in ((6, 6), (9, 6), (6, 9), (9, 9)):
            m.v[(x, y, z)] = steel
        k = (y - 10) % 6
        if k in (0, 3):
            for t in range(6, 10):
                m.v[(t, y, 6)] = steel; m.v[(t, y, 9)] = steel
                m.v[(6, y, t)] = steel; m.v[(9, y, t)] = steel
        else:
            d = 6 + (k if k < 3 else 6 - k)
            m.v[(d, y, 9)] = sh(steel, 0.85); m.v[(9, y, d)] = sh(steel, 0.85)
    for y in range(46, 54):                                     # штырь
        m.v[(7, y, 7)] = (60, 62, 66)
    m.v[(7, 54, 7)] = glow((255, 40, 40))
    for y in (20, 34):                                          # секторные антенны-панели
        for yy in range(y, y + 4):
            for x in range(7, 9):
                m.v[(x, yy, 10)] = (226, 228, 232)
    return m

def case_back():
    """Задняя панель офисного корпуса: разъёмы платы, сетевой порт с лампочками, вентилятор блока питания."""
    m = Model()
    body = (43, 45, 50)
    m.box(3, 0, 1, 13, 15, 15, noise(body, 2, 241))
    F = 14
    m.paint(lambda x, y, z: z == F and 4 <= x <= 7 and 6 <= y <= 13, (150, 154, 160))  # заглушка I/O
    m.v[(5, 12, F)] = (20, 20, 24); m.v[(6, 12, F)] = (20, 20, 24)                   # USB
    m.v[(5, 11, F)] = (20, 20, 24); m.v[(6, 11, F)] = (20, 20, 24)
    for y in (8, 9):                                                                  # RJ-45
        m.v[(5, y, F)] = (18, 18, 20); m.v[(6, y, F)] = (18, 18, 20)
    m.v[(5, 10, F)] = glow((80, 255, 120)); m.v[(6, 10, F)] = glow((255, 170, 40))
    m.paint(lambda x, y, z: z == F and 9 <= x <= 12 and 1 <= y <= 4, lambda x, y, z: (24, 24, 28) if (x + y) % 2 else (90, 94, 100))  # БП
    m.paint(lambda x, y, z: z == F and 9 <= x <= 11 and 7 <= y <= 13 and y % 2 == 0, (30, 31, 35))   # прорези
    # вилка в сетевом порту и кабель вниз-назад
    for y in (8, 9):
        for x in (5, 6):
            m.v[(x, y, F + 1)] = PLUG
    for y in range(1, 8):
        m.v[(5, y, F + 1)] = CABLE["blue"] if y % 2 else sh(CABLE["blue"], 0.85)
        m.v[(6, y, F + 1)] = CABLE["blue"] if y % 2 else sh(CABLE["blue"], 0.85)
    for z in range(F + 1, F + 2):
        for x in (5, 6):
            m.v[(x, 0, z)] = CABLE["blue"]
    return m

def wifi_usb_stick():
    m = Model()
    m.box(6, 0, 6, 10, 1, 13, (40, 42, 48))
    m.box(7, 0, 4, 9, 1, 6, (190, 194, 200))
    for y in range(1, 6):
        m.v[(9, y, 12)] = (40, 42, 48)
    m.v[(7, 1, 10)] = glow((70, 150, 255))
    return m
