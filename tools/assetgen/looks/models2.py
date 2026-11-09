from vox import Model, noise, glow
import models, pics
import math, random

def place(scene, m, dx=0, dy=0, dz=0):
    for (x, y, z), c in m.v.items():
        scene.v[(x + dx, y + dy, z + dz)] = c
    return scene

def screen(m, x0, x1, y0, y1, z, pic, dim_rows=False, gl=True):
    """Нарисовать картинку на передней грани (z) в прямоугольнике [x0,x1)×[y0,y1)."""
    W, H = x1 - x0, y1 - y0
    for x in range(x0, x1):
        for y in range(y0, y1):
            c = pic(x - x0, y - y0, W, H)
            if dim_rows and y % 2 == 0: c = tuple(int(ch * 0.82) for ch in c)
            m.v[(x, y, z)] = glow(c) if gl else c

def keyboard(m, x0, x1, z0, z1, body, key, y=0, accent=None, mouse=True):
    """Плоская клавиатура толщиной 1 пиксель (клавиши — узор на верхней грани) и мышь справа."""
    for x in range(x0, x1):
        for z in range(z0, z1):
            c = body
            if x0 < x < x1 - 1 and z0 < z < z1 - 1 and (x + z) % 2 == 0:
                c = key if accent is None else accent(x, z)
            if z == z1 - 2 and x0 + 3 <= x < x1 - 3: c = key if accent is None else accent(x, z)   # пробел
            m.v[(x, y, z)] = c
    if mouse:
        mx = x1 + 1
        if mx + 2 <= 16 or x1 > 16:
            for x in range(mx, mx + 2):
                for z in range(z0 + 1, z0 + 4):
                    m.v[(x, y, z)] = body

# ============================== РЕТРО ==============================
BEIGE = (214, 205, 178)

def retro_desktop():
    """Горизонтальный настольный корпус: на него ставят монитор."""
    m = Model()
    m.box(1, 0, 2, 15, 5, 15, noise(BEIGE, 4, 51))
    F = 14
    for y in (3, 1):     # два дисковода 5,25"
        m.paint(lambda x, yy, z, y=y: z == F and yy == y and 8 <= x <= 13, (196, 187, 160))
        m.paint(lambda x, yy, z, y=y: z == F and yy == y and 9 <= x <= 12, (52, 50, 46))
    m.paint(lambda x, y, z: z == F and y == 2 and x in (10, 11), (150, 142, 118))
    m.paint(lambda x, y, z: z == F and y == 3 and 2 <= x <= 3, glow((90, 255, 90)))
    m.paint(lambda x, y, z: z == F and y == 3 and x == 5, glow((255, 170, 40)))
    m.paint(lambda x, y, z: z == F and y == 1 and x in (2, 3), (150, 146, 132))     # кнопка питания
    m.paint(lambda x, y, z: z == F and y == 2 and x == 6, (90, 86, 76))             # замок с ключом
    m.paint(lambda x, y, z: y == 4 and 3 <= x <= 12 and z in (4, 6), (180, 171, 144))   # вентиляция сверху
    return m

def retro_tv():
    """Ламповый телевизор в деревянном корпусе с антенной — к нему подключали домашние компьютеры."""
    m = Model()
    def wood(x, y, z):
        v = 18 * math.sin(x * 0.9 + y * 0.15) 
        return (int(120 + v), int(78 + v * 0.6), int(44 + v * 0.3))
    m.box(1, 2, 4, 15, 13, 14, wood)
    for x, z in ((2, 5), (13, 5), (2, 12), (13, 12)):
        m.box(x, 0, z, x + 1, 2, z + 1, (50, 36, 24))
    F = 13
    def tv_pic(i, j, W, H):
        bars = [(230, 230, 230), (230, 230, 40), (40, 230, 230), (40, 230, 40), (230, 40, 230), (230, 40, 40), (40, 40, 230)]
        if j < 2: return (20, 20, 20) if i % 2 else (200, 200, 200)
        return bars[min(len(bars) - 1, i * len(bars) // W)]
    screen(m, 2, 10, 3, 12, F, tv_pic, dim_rows=True)
    for x, y in ((2, 3), (9, 3), (2, 11), (9, 11)):            # скруглённые углы кинескопа
        m.v[(x, y, F)] = (60, 56, 50)
    m.paint(lambda x, y, z: z == F and 11 <= x <= 13 and 3 <= y <= 7 and y % 2 == 1, (60, 44, 30))   # динамик
    m.paint(lambda x, y, z: z == F and x in (12,) and y in (9, 11), (200, 190, 160))                  # ручки
    for k in range(5):                                         # антенна-«рога»
        m.v[(7 - k, 13 + k, 8)] = (180, 180, 186)
        m.v[(8 + k, 13 + k, 8)] = (180, 180, 186)
    m.v[(7, 13, 8)] = m.v[(8, 13, 8)] = (60, 60, 64)
    return m

def retro_keyboard():
    m = Model()
    m.box(1, 0, 4, 15, 1, 12, noise((206, 198, 172), 3, 61))
    m.box(1, 1, 4, 15, 2, 6, noise((200, 192, 166), 3, 62))
    m.paint(lambda x, y, z: y == 0 and 2 <= x <= 13 and 6 <= z <= 10 and (x + z) % 2 == 0, (170, 162, 138))
    m.paint(lambda x, y, z: y == 0 and 4 <= x <= 11 and z == 11, (170, 162, 138))
    return m

def retro_scene():
    s = Model()
    place(s, retro_desktop(), 0, 0, 0)
    crt = models.mon_crt()
    place(s, crt, 0, 5, -1)               # монитор стоит на корпусе
    place(s, retro_keyboard(), 0, 0, 16)
    place(s, models.case_home80(), 16, 0, 16)
    place(s, retro_tv(), 16, 0, 0)
    return s

# ============================== ОФИС ==============================
def office_tower():
    """Чёрный мини-тауэр: строгий, привод, синяя лампочка, USB спереди."""
    m = Model()
    body = (43, 45, 50)
    m.box(4, 0, 1, 12, 14, 15, noise(body, 2, 71))
    F = 14
    m.paint(lambda x, y, z: z == F and 5 <= x <= 10 and 1 <= y <= 13, (58, 61, 67))
    m.paint(lambda x, y, z: z == F and y == 12 and 5 <= x <= 10, (30, 31, 35))        # привод
    m.paint(lambda x, y, z: z == F and y == 11 and x == 10, (90, 92, 98))
    m.paint(lambda x, y, z: z == F and y in (9, 10) and x in (7, 8), (120, 124, 130))  # кнопка
    m.paint(lambda x, y, z: z == F and y == 8 and x in (7, 8), glow((60, 150, 255)))
    m.paint(lambda x, y, z: z == F and y == 6 and x in (6, 9), (22, 22, 26))           # USB
    m.paint(lambda x, y, z: z == F and y in (1, 2, 3) and 5 <= x <= 10 and y % 2 == 1, (36, 38, 42))
    m.paint(lambda x, y, z: x == 11 and 4 <= y <= 9 and 4 <= z <= 9 and (y + z) % 2 == 0, (30, 31, 35))
    return m

def flat_monitor(body, stand, pic, kb=None, wide=False, accent=None, legs=None, chin=False):
    """Плоский монитор. kb=(корпус, клавиши): клавиатура стоит в том же блоке перед монитором."""
    m = Model()
    W = 32 if wide else 16
    zp = 4 if kb else 8                       # с клавиатурой панель отодвинута назад
    top = 16 if wide else 15
    m.box(1, 5, zp, W - 1, top, zp + 2, body)
    if chin:
        m.box(1, 4, zp, W - 1, 5, zp + 3, body)
    c = W // 2
    if legs:
        m.box(c - 1, 1, zp - 1, c + 1, 5, zp, stand)
        for k in range(0, 5):
            m.v[(c - 2 - k, 0, zp - 1 + k)] = legs
            m.v[(c + 1 + k, 0, zp - 1 + k)] = legs
    else:
        m.box(c - 1, 1, zp - 1, c + 1, 5, zp, stand)
        m.box(c - 3 - (2 if wide else 0), 0, zp - 3, c + 3 + (2 if wide else 0), 1, zp + 2, stand)
    screen(m, 2, W - 2, 6, top - 1, zp + 1, pic)
    m.v[(W - 3, 5, zp + 1)] = glow(accent or (80, 160, 255))
    if kb:
        kbody, kkey, kacc = kb if len(kb) == 3 else (kb[0], kb[1], None)
        if wide:
            keyboard(m, 9, 22, 9, 14, kbody, kkey, accent=kacc)
        else:
            keyboard(m, 1, 12, 9, 14, kbody, kkey, accent=kacc)
    return m

def office_allinone(kb=True):
    """Моноблок: компьютер внутри монитора, белая клавиатура в том же блоке."""
    m = Model()
    white, silver = (232, 234, 238), (196, 200, 206)
    m.box(1, 4, 4, 15, 15, 6, white)
    m.box(3, 6, 2, 13, 13, 4, silver)                    # «горб» с компьютером сзади
    m.box(7, 0, 2, 9, 4, 4, silver)                      # ножка
    m.box(5, 0, 1, 11, 1, 5, silver)
    screen(m, 2, 14, 6, 14, 5, pics.sheet)
    m.paint(lambda x, y, z: z == 5 and y in (4, 5) and 2 <= x <= 13, (222, 224, 228))   # нижняя полоса
    m.v[(8, 4, 5)] = glow((255, 255, 255))
    if kb:
        keyboard(m, 1, 12, 9, 14, (238, 240, 243), (205, 208, 214))
    return m

def office_scene():
    s = Model()
    place(s, office_tower(), 0, 0, 0)
    place(s, flat_monitor((30, 31, 35), (54, 56, 62), pics.sheet, kb=((46, 48, 54), (30, 31, 35)), wide=True), 16, 0, 0)
    place(s, models.case_mini(), 48, 0, 0)
    return s

# ============================== ИГРОВАЯ ==============================
def rainbow(x, z):
    h = (x * 0.5 + z * 0.3) % 6
    cols = [(255, 60, 60), (255, 170, 40), (240, 240, 60), (60, 230, 120), (60, 140, 255), (200, 80, 255)]
    return glow(cols[int(h)])

def gaming_cube():
    """Компактный игровой куб: большой вентилятор с RGB-кольцом спереди."""
    m = Model()
    m.box(2, 0, 2, 14, 12, 14, noise((28, 28, 34), 3, 81))
    F = 13
    for x in range(3, 13):
        for y in range(1, 11):
            d = (x - 7.5) ** 2 + (y - 5.5) ** 2
            if 12 <= d <= 22: m.v[(x, y, F)] = rainbow(x * 2, y * 2)
            elif d < 12: m.v[(x, y, F)] = (50, 50, 58) if (x + y) % 2 else (20, 20, 24)
    m.paint(lambda x, y, z: x == 13 and 2 <= y <= 9 and 3 <= z <= 12 and (y + z) % 2 == 0, (16, 16, 20))
    m.paint(lambda x, y, z: y == 11 and 4 <= x <= 11 and 4 <= z <= 11 and (x + z) % 2 == 0, (16, 16, 20))
    return m

def gaming_scene():
    s = Model()
    place(s, models.case_gaming(), 0, 0, 0)
    cur = curved_with_kb()
    place(s, cur, 16, 0, 0)
    return s

def curved_with_kb():
    m = models.mon_curved()
    # отодвигаем монитор назад на 3 пикселя, спереди ставим RGB-клавиатуру
    shifted = Model()
    for (x, y, z), c in m.v.items():
        shifted.v[(x, y, z - 3)] = c
    keyboard(shifted, 9, 22, 10, 15, (24, 24, 28), (40, 40, 46), accent=rainbow)
    return shifted

def gaming_flat_kb():
    pic = pics.game
    return flat_monitor((20, 20, 24), (210, 30, 40), pic, kb=((24, 24, 28), (40, 40, 46), rainbow),
                        accent=(255, 60, 200), legs=(210, 30, 40))

# ============================== ПРОМЫШЛЕННАЯ ==============================
STEEL = (104, 118, 124)

def ind_cabinet():
    """Защищённый шкаф-компьютер: стальная дверца с окошком, ручка, полосы опасности."""
    m = Model()
    m.box(1, 1, 1, 15, 16, 15, noise(STEEL, 4, 91))
    m.box(1, 0, 1, 15, 1, 15, lambda x, y, z: (230, 190, 40) if (x + z) % 4 < 2 else (30, 30, 30))
    F = 14
    m.paint(lambda x, y, z: z == F and (x in (2, 13) or y in (2, 15)) and 2 <= x <= 13 and 2 <= y <= 15, (80, 92, 98))   # рамка двери
    r = random.Random(5)
    def window(x, y, z):
        c = (26, 34, 38)
        if y in (10, 12) and 5 <= x <= 10: c = (60, 70, 76)
        if y == 11 and x in (6, 8, 10): c = glow((60, 255, 120)) if r.random() < 0.7 else glow((255, 80, 60))
        return c
    m.paint(lambda x, y, z: z == F and 4 <= x <= 11 and 9 <= y <= 13, window)
    m.paint(lambda x, y, z: z == F and x == 12 and 5 <= y <= 8, (200, 204, 206))      # ручка
    for (x, y) in ((5, 4), (6, 4), (7, 4), (6, 5)):                                   # знак «внимание»
        m.paint(lambda xx, yy, z, x=x, y=y: z == F and xx == x and yy == y, (240, 200, 30))
    m.paint(lambda x, y, z: z == F and y in (3, 5, 7) and 9 <= x <= 11, (70, 80, 86))   # решётка
    return m

def ind_console():
    """Пульт управления: наклонный экран, ряд кнопок и большая красная кнопка."""
    m = Model()
    m.box(0, 0, 2, 16, 8, 16, noise(STEEL, 4, 92))
    for y in range(8, 15):
        zf = 15 - int((y - 8) * 1.6)
        m.box(0, y, 2, 16, y + 1, zf + 1, noise(STEEL, 4, 93 + y))
    # экран на наклонной части
    pic = pics.dashboard
    rows = list(range(9, 14))
    for x in range(2, 14):
        for k, y in enumerate(rows):
            zf = 15 - int((y - 8) * 1.6)
            c = pic(x - 2, k, 12, len(rows))
            m.v[(x, y, zf)] = glow(c)
    # кнопки на передней грани
    for i, c in enumerate([(60, 170, 70), (230, 200, 40), (60, 120, 200), (200, 200, 200)]):
        m.v[(2 + i * 2, 6, 15)] = c
    m.box(11, 5, 15, 14, 7, 16, (200, 30, 30))
    m.box(12, 7, 15, 13, 8, 16, (230, 50, 50))
    m.paint(lambda x, y, z: z == 15 and y == 1 and (x % 4) < 2, (230, 190, 40))
    return m

def ind_panel():
    """Панель на стену: тыльной стороной к стене (z=0), экран смотрит вперёд."""
    m = Model()
    m.box(0, 0, 0, 16, 16, 3, noise((92, 96, 84), 4, 31))
    m.remove(3, 4, 2, 13, 14, 3)
    screen(m, 3, 13, 4, 14, 1, pics.text((255, 176, 40), (40, 22, 6), seed=8))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        m.v[(x, y, 2)] = (170, 170, 160)
    for i, c in enumerate([(200, 40, 40), (230, 200, 40), (60, 170, 70), (60, 120, 200)]):
        m.v[(4 + i * 2, 2, 2)] = c
    for x in range(2, 14):
        m.v[(x, 15, 2)] = (230, 190, 40) if x % 4 < 2 else (30, 30, 30)
    return m

def ind_scene():
    s = Model()
    wall = (128, 130, 132)
    s.box(0, 0, -6, 48, 32, 0, noise(wall, 5, 99))                  # бетонная стена
    place(s, ind_cabinet(), 0, 0, 0)
    place(s, ind_console(), 16, 0, 0)
    place(s, ind_console(), 32, 0, 0)
    place(s, ind_panel(), 24, 16, 0)
    return s

# ============================== СЕРВЕРНАЯ ==============================
def rack_kvm():
    """Стойка с выдвинутой консолью: откидной экран и клавиатура на полке."""
    m = Model()
    frame = (40, 43, 50)
    m.box(0, 0, 0, 16, 16, 10, noise(frame, 3, 101))
    m.box(0, 0, 10, 1, 16, 16, frame)
    m.box(15, 0, 10, 16, 16, 16, frame)
    m.box(0, 15, 10, 16, 16, 16, frame)
    m.box(0, 0, 10, 16, 1, 16, frame)
    r = random.Random(12)
    for y in range(1, 15):
        for x in range(2, 14):
            if (y - 1) % 3 == 2: c = (22, 24, 28)
            else:
                c = (112, 118, 126)
                if x == 12 and (y - 1) % 3 == 1: c = glow((60, 255, 120)) if r.random() < 0.7 else glow((255, 170, 0))
            m.v[(x, y, 9)] = c
    # консоль: полка с клавиатурой и откинутый экран
    m.box(2, 6, 10, 14, 7, 15, (70, 74, 82))
    keyboard(m, 3, 13, 10, 15, (70, 74, 82), (40, 42, 48), y=7, mouse=False)
    m.box(2, 7, 10, 14, 14, 11, (50, 53, 60))
    screen(m, 3, 13, 8, 13, 10, pics.text((120, 255, 150), (8, 20, 12), seed=21))
    for x in range(3, 13):                 # экран смотрит вперёд: закрасим его переднюю грань
        for y in range(8, 13):
            m.v[(x, y, 11)] = m.v[(x, y, 10)]
            m.v.pop((x, y, 10), None)
            m.v[(x, y, 10)] = (50, 53, 60)
    return m

def server_scene():
    s = Model()
    place(s, models.case_server(), 0, 0, 0)
    place(s, rack_kvm(), 16, 0, 0)
    place(s, models.case_server(), 32, 0, 0)
    place(s, block_wall(3, 1, pics.cctv, body=(26, 28, 32)), 0, 16, 0)
    return s

# ============================== БЛОЧНЫЕ МОНИТОРЫ ==============================
def block_wall(cols, rows, pic, body=(28, 30, 34), rim=(16, 17, 20), side=None, dim_rows=False, deep=0):
    """Стена из блочных мониторов: картинка сплошная, рамка только по краю всей стены — швов нет."""
    m = Model()
    W, H = cols * 16, rows * 16
    side = side or body
    m.box(0, 0, 0 + deep, W, H, 16, lambda x, y, z: side if z < 15 else rim)
    screen(m, 1, W - 1, 1, H - 1, 15, pic, dim_rows=dim_rows)
    return m

def block_white(pic):
    return block_wall(1, 1, pic, body=(232, 234, 238), rim=(214, 217, 222))

def block_retro(pic):
    """Ретро-блок: бежевый, экран с развёрткой, кнопки снизу."""
    m = Model()
    m.box(0, 0, 0, 16, 16, 16, noise(BEIGE, 4, 111))
    screen(m, 2, 14, 3, 14, 15, pic, dim_rows=True)
    m.v[(12, 1, 15)] = (150, 142, 118); m.v[(13, 1, 15)] = (150, 142, 118)
    m.v[(3, 1, 15)] = glow((90, 255, 90))
    return m

def hanging_keyboard(body=(46, 48, 54), key=(30, 31, 35)):
    """Навесная клавиатура: полка на кронштейнах, вешается на переднюю грань блока (стоит в соседнем блоке)."""
    m = Model()
    keyboard(m, 1, 15, 0, 6, body, key, y=4, mouse=False)
    for x in (2, 13):
        for k in range(4):
            m.v[(x, k, k)] = (70, 72, 78)
    m.box(1, 3, 0, 15, 4, 1, (70, 72, 78))
    return m

def kb_stand(body=(46, 48, 54), key=(30, 31, 35)):
    """Клавиатурная стойка: ставится рядом с блочным монитором, наклонная клавиатура сверху."""
    m = Model()
    m.box(6, 0, 6, 10, 9, 10, (60, 62, 68))
    m.box(4, 0, 4, 12, 1, 12, (60, 62, 68))
    for k, z in enumerate(range(3, 13)):
        y = 9 + (k // 4)
        for x in range(2, 14):
            c = key if (x + z) % 2 == 0 and 3 <= x <= 12 and 4 <= z <= 11 else body
            m.v[(x, 12 - (z - 3) // 4 - 3 + 3, z)] = c
    return m

def blocks_singles():
    s = Model()
    place(s, block_wall(1, 1, pics.code), 0, 0, 0)
    place(s, block_white(pics.map_view), 20, 0, 0)
    place(s, block_retro(pics.text((110, 255, 140), (20, 36, 26), seed=33)), 40, 0, 0)
    return s

def blocks_terminal():
    s = Model()
    place(s, block_wall(1, 2, pics.text((120, 230, 255), (10, 18, 30), seed=40)), 0, 0, 0)
    place(s, hanging_keyboard(), 0, 12, 16)
    return s

def blocks_wall_kb():
    s = Model()
    place(s, block_wall(3, 2, pics.desktop), 0, 0, 0)
    place(s, kb_stand(), 16, 0, 22)
    return s

# ---------- исправленные версии ----------
def flat_monitor_feet(body, feet, pic, kb=None, accent=None):
    """Плоский монитор на двух лапах (игровой): лапы стоят по бокам, не мешают клавиатуре."""
    m = Model()
    zp = 4 if kb else 8
    m.box(1, 5, zp, 15, 15, zp + 2, body)
    m.box(7, 1, zp - 1, 9, 5, zp, feet)
    m.box(3, 0, zp - 2, 13, 1, zp, feet)
    m.box(3, 0, zp, 5, 1, zp + 3, feet)
    m.box(11, 0, zp, 13, 1, zp + 3, feet)
    screen(m, 2, 14, 6, 14, zp + 1, pic)
    m.v[(13, 5, zp + 1)] = glow(accent or (80, 160, 255))
    if kb:
        kbody, kkey, kacc = kb
        keyboard(m, 1, 12, 9, 14, kbody, kkey, accent=kacc)
    return m

def gaming_flat_kb():
    return flat_monitor_feet((20, 20, 24), (210, 30, 40), pics.game,
                             kb=((24, 24, 28), (40, 40, 46), rainbow), accent=(255, 60, 200))

def ind_console():
    """Пульт управления: ровный наклон 45°, экран в стальной рамке, кнопки и большая красная кнопка."""
    m = Model()
    m.box(0, 0, 2, 16, 8, 16, noise(STEEL, 4, 92))
    for y in range(8, 15):
        zf = 15 - (y - 8)
        m.box(0, y, 2, 16, y + 1, zf + 1, noise(STEEL, 4, 93 + y))
    pic = pics.text((255, 176, 40), (40, 22, 6), seed=9, density=0.9)
    rows = list(range(9, 14))
    for x in range(3, 13):
        for k, y in enumerate(rows):
            zf = 15 - (y - 8)
            m.v[(x, y, zf)] = glow(pic(x - 3, k, 10, len(rows)))
    for i, c in enumerate([(60, 170, 70), (230, 200, 40), (60, 120, 200), (200, 200, 200)]):
        m.v[(2 + i * 2, 6, 15)] = c
    m.box(11, 5, 15, 14, 7, 16, (200, 30, 30))
    m.box(12, 7, 15, 13, 8, 16, (230, 50, 50))
    m.paint(lambda x, y, z: z == 15 and y == 1 and (x % 4) < 2, (230, 190, 40))
    return m

def rack_kvm():
    """Стойка с выдвинутой консолью: на полке клавиатура, откинутый экран смотрит вперёд."""
    m = Model()
    frame = (40, 43, 50)
    m.box(0, 0, 0, 16, 16, 12, noise(frame, 3, 101))
    m.box(0, 0, 12, 1, 16, 16, frame)                 # стойки рамы спереди
    m.box(15, 0, 12, 16, 16, 16, frame)
    m.box(0, 15, 12, 16, 16, 16, frame)
    r = random.Random(12)
    for y in range(1, 15):
        for x in range(2, 14):
            if 5 <= y <= 11: c = (24, 26, 30)          # ниша консоли
            elif (y - 1) % 3 == 2: c = (22, 24, 28)
            else:
                c = (112, 118, 126)
                if x == 12 and (y - 1) % 3 == 1: c = glow((60, 255, 120)) if r.random() < 0.7 else glow((255, 170, 0))
                if x in (3, 5, 7) and (y - 1) % 3 == 0: c = (60, 64, 70)
            m.v[(x, y, 11)] = c
    m.box(2, 4, 12, 14, 5, 16, (70, 74, 82))           # полка
    keyboard(m, 3, 13, 12, 16, (70, 74, 82), (40, 42, 48), y=5, mouse=False)
    m.box(2, 6, 12, 14, 12, 13, (50, 53, 60))          # откинутый экран
    screen(m, 3, 13, 7, 11, 12, pics.text((120, 255, 150), (8, 20, 12), seed=21))
    m.v[(12, 6, 12)] = glow((60, 255, 120))
    return m

def rack_kvm():
    """Стойка с выдвинутой консолью: серверы сверху и снизу, в нише — откинутый экран и клавиатура."""
    m = Model()
    frame = (40, 43, 50)
    m.box(0, 0, 0, 16, 16, 16, noise(frame, 3, 101))
    r = random.Random(12)
    for y in range(1, 15):
        for x in range(2, 14):
            if (y - 1) % 3 == 2: c = (22, 24, 28)
            else:
                c = (112, 118, 126)
                if x == 12 and (y - 1) % 3 == 1: c = glow((60, 255, 120)) if r.random() < 0.7 else glow((255, 170, 0))
                if x in (3, 5, 7) and (y - 1) % 3 == 0: c = (60, 64, 70)
            m.v[(x, y, 15)] = c
    m.remove(2, 4, 9, 14, 15, 16)                   # ниша консоли
    m.box(2, 4, 8, 14, 15, 9, (24, 26, 30))
    m.box(2, 4, 9, 14, 5, 16, (70, 74, 82))          # полка
    keyboard(m, 3, 13, 13, 16, (70, 74, 82), (40, 42, 48), y=5, mouse=False)
    m.box(2, 5, 11, 14, 14, 12, (50, 53, 60))        # откинутый экран
    screen(m, 3, 13, 6, 13, 11, pics.text((120, 255, 150), (8, 20, 12), seed=21))
    m.v[(12, 5, 11)] = glow((60, 255, 120))
    return m
