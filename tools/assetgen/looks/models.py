from vox import Model, noise, glow
import random, math

R = random.Random(7)

def text_screen(bg, fg, x0, x1, y0, y1, seed=1, cursor=None, density=0.75):
    """Экран с «текстом»: строки из штрихов разной длины через строку."""
    r = random.Random(seed)
    rows = {}
    for y in range(y0, y1):
        if (y1 - 1 - y) % 2 == 1:
            rows[y] = set()
            continue
        line = set()
        x = x0 + 1
        L = r.randint(int((x1 - x0) * 0.3), int((x1 - x0) * density))
        while x < x0 + 1 + L and x < x1 - 1:
            w = r.randint(1, 4)
            for k in range(w):
                if x + k < x1 - 1: line.add(x + k)
            x += w + 1
        rows[y] = line
    def f(x, y, z):
        on = x in rows.get(y, ())
        return glow(fg) if on else glow(bg)
    return f

# ======================= СИСТЕМНЫЕ БЛОКИ =======================

def case_office():
    """Бежевый тауэр 90-х: отсеки 5,25", дисковод 3,5", кнопки Turbo и Reset, лампочки."""
    m = Model()
    beige = (214, 205, 178)
    m.box(3, 0, 1, 13, 15, 15, noise(beige, 5, 1))
    m.box(3, 15, 1, 13, 16, 15, noise((206, 197, 170), 4, 2))   # крышка
    F = 14
    dark = (176, 167, 140)
    # отсеки 5,25": две полосы с прорезью и кнопкой
    for y in (12, 10):
        m.paint(lambda x, yy, z, y=y: z == F and yy == y and 4 <= x <= 11, (196, 187, 160))
        m.paint(lambda x, yy, z, y=y: z == F and yy == y and 5 <= x <= 9, (60, 58, 52))
        m.paint(lambda x, yy, z, y=y: z == F and yy == y and x == 11, (150, 142, 118))
    # дисковод 3,5"
    m.paint(lambda x, y, z: z == F and y == 8 and 6 <= x <= 10, (40, 40, 40))
    m.paint(lambda x, y, z: z == F and y == 7 and x == 10, (120, 120, 120))
    # кнопки и лампочки
    m.paint(lambda x, y, z: z == F and y in (4, 5) and x in (5, 6), (150, 146, 132))
    m.paint(lambda x, y, z: z == F and y == 5 and x == 9, glow((90, 255, 90)))
    m.paint(lambda x, y, z: z == F and y == 5 and x == 11, glow((255, 170, 40)))
    m.paint(lambda x, y, z: z == F and y == 4 and x == 10, (180, 60, 50))   # Turbo
    # решётка внизу
    m.paint(lambda x, y, z: z == F and y in (1, 2) and 4 <= x <= 11 and x % 2 == 0, dark)
    # шов боковой крышки
    m.paint(lambda x, y, z: x == 12 and z == 2, dark)
    return m

def case_gaming():
    """Игровой: чёрный корпус, стеклянный бок, видна начинка с RGB-вентиляторами."""
    m = Model()
    black = (26, 26, 31)
    m.box(2, 0, 1, 14, 16, 15, noise(black, 3, 3))
    F = 14
    # передняя сетка и RGB-полосы по краям
    m.paint(lambda x, y, z: z == F and 3 <= x <= 12 and 1 <= y <= 14 and (x + y) % 2 == 0, (44, 44, 52))
    def rgb(y):
        t = y / 15
        return glow((int(60 + 195 * t), int(220 - 160 * t), 255))
    m.paint(lambda x, y, z: z == F and x in (3, 12) and 1 <= y <= 14, lambda x, y, z: rgb(y))
    # стекло справа (грань +x): начинка за тонированным стеклом
    GX = 13
    def inside(x, y, z):
        if not (2 <= z <= 13 and 1 <= y <= 14): return None
        c = (20, 22, 30)
        if 3 <= z <= 10 and 6 <= y <= 13: c = (28, 40, 34)                 # материнская плата
        if 3 <= z <= 12 and y in (7, 8): c = (50, 50, 58)                  # видеокарта
        if 3 <= z <= 12 and y == 6: c = glow((255, 60, 200))               # подсветка видеокарты
        if 5 <= z <= 7 and 10 <= y <= 12: c = (70, 70, 80)                 # кулер процессора
        for cz, cy in ((12, 11), (12, 4), (8, 3), (4, 3)):                  # вентиляторы
            dd = (z - cz) ** 2 + (y - cy) ** 2
            if 1 <= dd <= 2: c = glow(((z * 40) % 255, 120, 255))
            elif dd == 0: c = (90, 90, 100)
        if y == 1 or y == 14 or z == 2 or z == 13: c = (12, 12, 16)        # рамка
        return c
    m.paint(lambda x, y, z: x == GX, lambda x, y, z: inside(x, y, z) or (26, 26, 31))
    # верх — вентиляционная сетка
    m.paint(lambda x, y, z: y == 15 and 4 <= x <= 11 and 4 <= z <= 11 and (x + z) % 2 == 0, (12, 12, 16))
    return m

def case_mini():
    """Мини-ПК (неттоп): маленький алюминиевый брусок на ножках."""
    m = Model()
    def alu(x, y, z):
        v = 186 + (y * 3) - (z % 3)
        return (v, v + 4, v + 10)
    m.box(4, 1, 4, 12, 5, 12, alu)
    for x, z in ((4, 4), (11, 4), (4, 11), (11, 11)):
        m.box(x, 0, z, x + 1, 1, z + 1, (40, 40, 40))
    F = 11
    m.paint(lambda x, y, z: z == F and y == 2 and 5 <= x <= 10, glow((60, 170, 255)))   # светодиодная полоса
    m.paint(lambda x, y, z: z == F and y == 3 and x == 10, (90, 95, 100))              # кнопка
    m.paint(lambda x, y, z: z == F and y == 3 and x in (5, 7), (30, 30, 34))           # USB
    m.paint(lambda x, y, z: y == 4 and 6 <= x <= 9 and 6 <= z <= 9, (150, 154, 162))    # логотип-вставка сверху
    return m

def case_server():
    """Серверная стойка: в раме стоят серверы по 1U с дисками и мигающими лампочками."""
    m = Model()
    frame = (40, 43, 50)
    m.box(0, 0, 0, 16, 16, 16, noise(frame, 3, 5))
    r = random.Random(11)
    F = 15
    def unit(x, y, z):
        if not (2 <= x <= 13 and 1 <= y <= 14): return None
        if (y - 1) % 3 == 2: return (22, 24, 28)             # щель между серверами
        c = (120, 126, 134) if (y - 1) % 3 == 1 else (104, 110, 118)
        if (y - 1) % 3 == 0 and 3 <= x <= 9 and x % 2 == 1: c = (56, 60, 66)      # диски
        if x == 12 and (y - 1) % 3 == 1:
            c = glow((60, 255, 120)) if r.random() < 0.7 else glow((255, 170, 0))
        if x == 11 and (y - 1) % 3 == 1 and r.random() < 0.5: c = glow((80, 160, 255))
        return c
    m.paint(lambda x, y, z: z == F, lambda x, y, z: unit(x, y, z) or frame)
    # перфорированный бок
    m.paint(lambda x, y, z: x == 15 and 2 <= y <= 13 and 2 <= z <= 13 and (y + z) % 3 == 0, (24, 26, 30))
    m.paint(lambda x, y, z: y == 15 and 3 <= x <= 12 and 3 <= z <= 12 and (x + z) % 3 == 0, (24, 26, 30))
    return m

def case_workstation():
    """Рабочая станция: алюминиевый корпус с решёткой-«тёркой», ручки сверху."""
    m = Model()
    def alu(x, y, z):
        v = 200 - y
        return (v, v + 3, v + 8)
    m.box(3, 1, 1, 13, 15, 15, alu)
    for x in (3, 12):                              # ножки-рама
        m.box(x, 0, 1, x + 1, 1, 15, (170, 174, 182))
        m.box(x, 15, 1, x + 1, 16, 2, (170, 174, 182))
        m.box(x, 15, 14, x + 1, 16, 15, (170, 174, 182))
        m.box(x, 16, 1, x + 1, 17, 15, (190, 194, 202))   # ручка
    F = 14
    m.paint(lambda x, y, z: z == F and 4 <= x <= 11 and 2 <= y <= 13 and (x + y) % 2 == 0, (110, 114, 122))
    m.paint(lambda x, y, z: x == 12 and 2 <= y <= 13 and 2 <= z <= 13 and (y + z) % 2 == 0, (120, 124, 132))
    m.paint(lambda x, y, z: z == F and y == 14 and x == 11, glow((255, 255, 255)))
    return m

def case_home80():
    """Домашний компьютер 80-х: всё в корпусе клавиатуры, сбоку разъём для кассет/картриджей."""
    m = Model()
    body = (120, 98, 76)
    m.box(1, 0, 4, 15, 2, 13, noise(body, 4, 9))
    m.box(1, 2, 4, 15, 3, 7, noise(body, 4, 10))     # приподнятая задняя часть
    keycol = [(52, 46, 40), (52, 46, 40), (52, 46, 40), (200, 120, 60)]
    def keys(x, y, z):
        if 2 <= x <= 13 and 7 <= z <= 11 and (x + z) % 2 == 0:
            return keycol[R.randint(0, 3)] if (x, z) not in ((13, 11),) else (200, 90, 60)
        return None
    m.paint(lambda x, y, z: y == 1 and keys(x, y, z) is not None, keys)
    m.paint(lambda x, y, z: y == 1 and 4 <= x <= 11 and z == 12, (52, 46, 40))           # пробел
    m.paint(lambda x, y, z: y == 2 and z == 6 and x in (11, 12, 13), lambda x, y, z: glow([(255, 70, 60), (255, 200, 60), (90, 220, 120)][x - 11]))  # цветные полоски
    m.paint(lambda x, y, z: y == 2 and z == 6 and x == 2, glow((255, 60, 40)))
    return m

# ======================= МОНИТОРЫ =======================

def mon_crt():
    """Кинескоп (ЭЛТ): глубокий бежевый корпус, выпуклое стекло, зелёный текст."""
    m = Model()
    beige = (210, 202, 176)
    m.box(1, 2, 9, 15, 15, 15, noise(beige, 4, 21))
    m.box(3, 4, 4, 13, 13, 9, noise((198, 190, 164), 4, 22))
    m.box(5, 6, 2, 11, 11, 4, noise((190, 182, 156), 4, 23))
    m.box(5, 0, 7, 11, 2, 13, noise((190, 182, 156), 4, 24))
    m.box(4, 0, 9, 12, 1, 14, noise((180, 172, 146), 4, 25))
    F = 14
    scr = text_screen((24, 40, 30), (110, 255, 140), 3, 13, 4, 13, seed=3)
    m.paint(lambda x, y, z: z == F and 3 <= x <= 12 and 4 <= y <= 12, scr)
    # стекло выпуклое: экран чуть выступает
    m.box(4, 5, 15, 12, 12, 16, lambda x, y, z: scr(x, y, z))
    m.paint(lambda x, y, z: z == F and y == 3 and x in (11, 12), (140, 134, 112))
    m.paint(lambda x, y, z: z == F and y == 3 and x == 4, glow((90, 255, 90)))
    # вентиляция сзади
    m.paint(lambda x, y, z: y == 12 and 4 <= x <= 11 and z in (5, 7), (150, 142, 118))
    return m

def mon_lcd43():
    """Офисный ЖК 4:3: тонкая чёрная рамка, ножка и подставка."""
    m = Model()
    black = (30, 30, 34)
    m.box(2, 4, 9, 14, 15, 11, black)
    m.box(7, 1, 8, 9, 5, 9, (50, 50, 56))
    m.box(4, 0, 6, 12, 1, 12, (40, 40, 46))
    F = 10
    scr = text_screen((20, 24, 60), (230, 230, 240), 3, 13, 5, 14, seed=5)
    m.paint(lambda x, y, z: z == F and 3 <= x <= 12 and 5 <= y <= 13, scr)
    m.paint(lambda x, y, z: z == F and y == 4 and x == 12, glow((80, 160, 255)))
    return m

def mon_wide():
    """Широкоформатный (2 блока): тонкая рамка, рабочий стол с окнами."""
    m = Model()
    black = (24, 24, 28)
    m.box(1, 4, 9, 31, 16, 10, black)
    m.box(15, 1, 8, 17, 6, 9, (60, 60, 66))
    m.box(11, 0, 6, 21, 1, 11, (46, 46, 52))
    def desk(x, y, z):
        if y == 5: return glow((30, 30, 36))             # панель задач
        c = (40 + x * 3, 90 + y * 6, 170)                 # обои
        if 4 <= x <= 15 and 8 <= y <= 14:                 # окно
            c = (235, 235, 240) if y < 14 else (60, 120, 220)
            if y < 13 and 6 <= x <= 13 and y % 2 == 0: c = (120, 120, 130)
        if 18 <= x <= 28 and 7 <= y <= 12:
            c = (36, 36, 40) if y < 12 else (60, 120, 220)
            if y < 11 and 19 <= x <= 25 and y % 2 == 1: c = (110, 230, 140)
        return glow(c)
    m.paint(lambda x, y, z: z == 9 and 2 <= x <= 29 and 5 <= y <= 14, desk)
    return m

def mon_curved():
    """Изогнутый игровой (2 блока): края загнуты вперёд, подсветка сзади, ножки буквой V."""
    m = Model()
    def zoff(x):
        return int(round(((x - 15.5) ** 2) / 70))
    for x in range(0, 32):
        dz = zoff(x)
        m.box(x, 4, 8 + dz, x + 1, 15, 9 + dz, (22, 22, 26))
    def game(x, y):
        # закат, горы и прицел
        t = (y - 5) / 9
        c = (int(255 - 60 * t), int(120 + 40 * t), int(80 + 150 * t))
        hill = 7 + int(2 * math.sin(x / 3)) + (1 if x > 18 else 0)
        if y <= hill: c = (40, 60 + y * 4, 50)
        if (x, y) in ((16, 10), (15, 10), (17, 10), (16, 9), (16, 11)): c = (255, 255, 255)
        if y == 14 and x < 9: c = (220, 40, 40)
        return glow(c)
    for x in range(1, 31):
        dz = zoff(x)
        for y in range(5, 15):
            m.v[(x, y, 8 + dz)] = game(x, y)
    # подсветка сзади
    for x in range(4, 28):
        m.v[(x, 9, 7 + zoff(x))] = glow((255, 40, 200) if x % 6 < 3 else (60, 120, 255))
    m.box(15, 1, 6, 17, 5, 7, (50, 50, 56))
    m.box(9, 0, 3, 23, 1, 4, (210, 30, 40))
    m.box(9, 0, 3, 10, 1, 9, (210, 30, 40))
    m.box(22, 0, 3, 23, 1, 9, (210, 30, 40))
    return m

def mon_panel():
    """Промышленная панель: вешается на стену, толстая рама с болтами, янтарный экран и кнопки."""
    m = Model()
    frame = (92, 96, 84)
    m.box(0, 0, 13, 16, 16, 16, noise(frame, 4, 31))
    F = 15
    m.remove(3, 4, 15, 13, 14, 16)                  # экран утоплен
    scr = text_screen((40, 22, 6), (255, 176, 40), 3, 13, 4, 14, seed=8)
    m.paint(lambda x, y, z: z == 14 and 3 <= x <= 12 and 4 <= y <= 13, scr)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        m.paint(lambda xx, yy, z, x=x, y=y: z == F and xx == x and yy == y, (170, 170, 160))
    for i, c in enumerate([(200, 40, 40), (230, 200, 40), (60, 170, 70), (60, 120, 200)]):
        m.paint(lambda x, y, z, i=i, c=c: z == F and y == 2 and x == 4 + i * 2, c)
    # жёлто-чёрные полосы сверху
    m.paint(lambda x, y, z: z == F and y == 15 and 2 <= x <= 13, lambda x, y, z: (230, 190, 40) if x % 4 < 2 else (30, 30, 30))
    return m

def mon_terminal():
    """Терминал: экран и клавиатура одним корпусом, монохромный зелёный текст."""
    m = Model()
    body = (60, 62, 66)
    m.box(2, 3, 3, 14, 14, 11, noise(body, 3, 41))
    m.box(3, 0, 4, 13, 3, 10, noise((54, 56, 60), 3, 42))
    m.box(1, 0, 11, 15, 2, 16, noise((70, 72, 76), 3, 43))   # клавиатура
    m.paint(lambda x, y, z: y == 1 and 2 <= x <= 13 and 12 <= z <= 14 and (x + z) % 2 == 0, (36, 36, 40))
    m.paint(lambda x, y, z: y == 1 and 4 <= x <= 11 and z == 15, (36, 36, 40))
    F = 10
    scr = text_screen((8, 20, 10), (80, 255, 110), 3, 13, 5, 13, seed=12, density=0.9)
    m.paint(lambda x, y, z: z == F and 3 <= x <= 12 and 5 <= y <= 12, scr)
    m.paint(lambda x, y, z: z == F and y == 4 and x == 12, glow((255, 160, 40)))
    return m
