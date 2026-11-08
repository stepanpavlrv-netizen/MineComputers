"""Детализированные 3D-модели комплектующих (v5), по мотивам рендеров реального железа.

Координаты — "координаты корпуса" (см. models3d.py), 1 пиксель ≈ 2.8 см.
Слои цвета: tintindex 1 = основной цвет (кожухи, радиаторы, крышки), 2 = подсветка/наклейка.
"""
from models3d import Model, BX, SY, SZ, RAM_Z0, PCB_Y0, PCB_Y1, M2_Y0, M2_Y1, M2_Z0, M2_Z1


# ---------------------------------------------------------------- помощники
def plates(m, axis, a0, a1, pitch, thick, lo, hi, mat="fin", tint=1, end_mat=None):
    """Стопка тонких пластин вдоль оси axis ('x','y','z') от a0 до a1.
    lo/hi — углы пластины по двум другим осям (в порядке x,y,z без оси axis)."""
    n = max(2, int(round((a1 - a0 - thick) / pitch)) + 1)
    step = (a1 - a0 - thick) / (n - 1)
    for i in range(n):
        p0 = a0 + i * step
        f, t = list(lo), list(hi)
        idx = "xyz".index(axis)
        f.insert(idx, p0); t.insert(idx, p0 + thick)
        mat_i = end_mat if end_mat and i in (0, n - 1) else mat
        m.box(tuple(f), tuple(t), mat_i, tint)
    return n


def fan_unit(m, axis, side, center, size, depth, tint=None, corners=True, hub=True):
    """Вентилятор: рамка из 4 планок + ротор с лопастями + резиновые уголки + ступица.
    axis — ось, куда "смотрит" вентилятор; side — +1/-1 (в какую сторону лицо);
    center — центр (x,y,z) лица; size — сторона квадрата; depth — толщина."""
    cx, cy, cz = center
    h = size / 2
    fr = size * 0.09            # ширина рамки
    face = {"x": ("east", "west"), "y": ("up", "down"), "z": ("south", "north")}[axis][0 if side > 0 else 1]

    def B(a0, a1, u0, u1, v0, v1, mat, tn=None, faces=None):
        # a — вдоль оси вентилятора; u, v — две другие оси в порядке xyz
        if axis == "x":
            f, t = (a0, u0, v0), (a1, u1, v1)
        elif axis == "y":
            f, t = (u0, a0, v0), (u1, a1, v1)
        else:
            f, t = (u0, v0, a0), (u1, v1, a1)
        m.box(f, t, mat, tn, faces=faces)

    ca = {"x": cx, "y": cy, "z": cz}[axis]
    a_face = ca
    a_back = ca - side * depth
    a0, a1 = min(a_face, a_back), max(a_face, a_back)
    cu, cv = [c for c, k in zip((cx, cy, cz), "xyz") if k != axis]
    # ротор (лопасти на лицевой грани, утоплен на 15% глубины)
    rec = depth * 0.15
    ra0, ra1 = (a0, a1 - rec) if side > 0 else (a0 + rec, a1)
    B(ra0, ra1, cu - h + fr, cu + h - fr, cv - h + fr, cv + h - fr, "plastic", None,
      faces={face: ("fan", None, "full")})
    # рамка
    fm = "shroud" if tint else "plastic"
    B(a0, a1, cu - h, cu + h, cv - h, cv - h + fr, fm, tint)
    B(a0, a1, cu - h, cu + h, cv + h - fr, cv + h, fm, tint)
    B(a0, a1, cu - h, cu - h + fr, cv - h + fr, cv + h - fr, fm, tint)
    B(a0, a1, cu + h - fr, cu + h, cv - h + fr, cv + h - fr, fm, tint)
    if corners:   # резиновые демпферы по углам
        e = 0.04
        ea0, ea1 = (a1, a1 + e) if side > 0 else (a0 - e, a0)
        for su in (-1, 1):
            for sv in (-1, 1):
                uu = cu + su * (h - fr / 2); vv = cv + sv * (h - fr / 2)
                B(ea0, ea1, uu - fr / 2, uu + fr / 2, vv - fr / 2, vv + fr / 2, "rubber")
    if hub:       # выпуклая ступица
        hs = size * 0.14
        ha0, ha1 = (ra1, ra1 + rec * 0.8) if side > 0 else (ra0 - rec * 0.8, ra0)
        B(ha0, ha1, cu - hs, cu + hs, cv - hs, cv + hs, "plastic", None,
          faces={face: ("fan_hub", None, "full")})


def octagon(m, axis, a0, a1, cu, cv, r, mat, tint=None, top_face=None):
    """Восьмигранная "шайба" (почти цилиндр) из трёх брусков."""
    k = r * 0.42
    for (du, dv) in ((r, k), (k, r), (r * 0.78, r * 0.78)):
        if axis == "x":
            f, t = (a0, cu - du, cv - dv), (a1, cu + du, cv + dv)
        elif axis == "y":
            f, t = (cu - du, a0, cv - dv), (cu + du, a1, cv + dv)
        else:
            f, t = (cu - du, cv - dv, a0), (cu + du, cv + dv, a1)
        m.box(f, t, mat, tint, faces=top_face)


# ---------------------------------------------------------------- процессор
def cpu(s):
    m = Model()
    sub = ["pcb_green", "pcb_green", "pcb_green", "pcb_black", "pcb_blue"][s]
    h = .725
    m.box((4.48, SY - h, SZ - h), (4.54, SY + h, SZ + h), sub)
    m.box((4.54, SY - h + .02, SZ + h - .14), (4.545, SY - h + .14, SZ + h - .02), "gold")   # треугольник-ключ
    if s == 0:   # открытый кристалл + SMD вокруг
        m.box((4.54, SY - .3, SZ - .3), (4.585, SY + .3, SZ + .3), "die")
        for dy in (-.5, .5):
            for dz in (-.45, -.15, .15, .45):
                m.box((4.54, SY + dy - .04, SZ + dz - .06), (4.56, SY + dy + .04, SZ + dz + .06), "smd")
        return m
    r = [0, .5, .58, .62, .66][s]
    # крышка: нижний фланец + приподнятое плато
    m.box((4.54, SY - r, SZ - r), (4.57, SY + r, SZ + r), "metal", 1)
    p = r - .1
    m.box((4.57, SY - p, SZ - p), (4.62, SY + p, SZ + p), "ihs", 1, faces={"east": ("ihs", 1, "full")})
    if s == 3:   # выступы-"ушки" по бокам
        m.box((4.54, SY - .25, SZ + r), (4.565, SY + .25, SZ + r + .06), "metal", 1)
        m.box((4.54, SY - .25, SZ - r - .06), (4.565, SY + .25, SZ - r), "metal", 1)
    if s == 4:   # конденсаторы по краям и логотип-вставка
        for dy in (-.6, .6):
            for dz in (-.3, 0, .3):
                m.box((4.54, SY + dy - .03, SZ + dz - .06), (4.555, SY + dy + .03, SZ + dz + .06), "smd")
        m.box((4.62, SY + .12, SZ - .25), (4.625, SY + .4, SZ + .25), "led", 2)
    return m


# ---------------------------------------------------------------- охлаждение
def heatpipes_out(m, x0, x1, spots, d=.17):
    for (dy, dz) in spots:
        m.box((x0, SY + dy - d / 2, SZ + dz - d / 2), (x1, SY + dy + d / 2, SZ + dz + d / 2), "copper")
        m.box((x1, SY + dy - d / 2 + .02, SZ + dz - d / 2 + .02), (x1 + .05, SY + dy + d / 2 - .02, SZ + dz + d / 2 - .02), "nickel")


def cooler(s):
    m = Model()
    top = 4.62
    if s == 0:   # боксовый: круглый радиатор + вентилятор с кожухом
        octagon(m, "x", top, 5.35, SY, SZ, 1.05, "fins_radial", None,
                top_face={"east": ("fins_radial", None, "full")})
        m.box((top, SY - .35, SZ - .35), (5.38, SY + .35, SZ + .35), "copper")       # медный сердечник
        m.box((5.35, SY - 1.1, SZ - 1.1), (5.42, SY + 1.1, SZ + 1.1), "shroud", 1)  # кожух
        fan_unit(m, "x", 1, (5.85, SY, SZ), 2.1, .43, tint=1, corners=False)
        return m

    # медное основание + никелированный блок
    m.box((top, SY - .62, SZ - .62), (4.72, SY + .62, SZ + .62), "copper")
    m.box((4.72, SY - .7, SZ - .7), (4.92, SY + .7, SZ + .7), "nickel")

    if s == 1:   # низкопрофильный: вертикальные пластины, вентилятор сверху
        heatpipes_out(m, 4.92, 5.12, [(-.35, -1.1), (.35, -1.1), (-.35, 1.1), (.35, 1.1)])
        m.box((4.92, SY - .5, SZ - 1.2), (5.05, SY + .5, SZ + 1.2), "copper")
        plates(m, "z", SZ - 1.7, SZ + 1.7, .26, .07, (5.1, SY - 1.7), (5.85, SY + 1.7))
        fan_unit(m, "x", 1, (6.3, SY, SZ), 3.4, .45)
        return m

    if s == 2:   # башня: 4 трубки, 14 пластин, вентилятор спереди, крышка
        heatpipes_out(m, 4.92, 9.65, [(-.55, -.3), (.55, -.3), (-.2, .3), (.2, .3)])
        plates(m, "x", 5.55, 9.5, .28, .08, (SY - 2.1, SZ - .9), (SY + 2.1, SZ + .9))
        m.box((9.5, SY - 2.15, SZ - .95), (9.62, SY + 2.15, SZ + .95), "metal", 1,
              faces={"east": ("cover", 1, "full")})
        fan_unit(m, "z", -1, (7.55, SY, SZ - 1.35), 4.2, .45)
        for x in (5.75, 9.35):   # проволочные клипсы
            m.box((x, SY - 2.2, SZ - 1.4), (x + .05, SY + 2.2, SZ - .85), "nickel")
        return m

    if s == 3:   # две башни, вентилятор между ними и спереди, крышка с подсветкой
        m.box((4.92, SY - .3, SZ - 1.66), (5.05, SY + .3, SZ + 1.66), "copper")
        heatpipes_out(m, 5.05, 10.1, [(dy, dz) for dy in (-.4, .4) for dz in (-1.55, -1.15, 1.15, 1.55)], d=.13)
        for (z0, z1) in ((SZ - 2.3, SZ - .6), (SZ + .6, SZ + 2.3)):
            plates(m, "x", 6.1, 9.95, .28, .08, (SY - 2.1, z0), (SY + 2.1, z1))
            m.box((9.95, SY - 2.15, z0 - .03), (10.07, SY + 2.15, z1 + .03), "metal", 1,
                  faces={"east": ("cover", 1, "full")})
        fan_unit(m, "z", 1, (8.0, SY, SZ + .45), 3.9, .9, corners=False)
        fan_unit(m, "z", -1, (8.07, SY, SZ - 2.3), 3.9, .4)            # второй вентилятор спереди
        m.box((10.07, SY - 2.0, SZ - 1.9), (10.1, SY + 2.0, SZ - 1.75), "led", 2)
        m.box((10.07, SY - 2.0, SZ + 1.75), (10.1, SY + 2.0, SZ + 1.9), "led", 2)
        return m

    # s == 4: водяное охлаждение
    m.elements.clear()
    octagon(m, "x", top, 5.45, SY, SZ, .82, "metal", 1)                     # корпус помпы
    octagon(m, "x", 5.45, 5.55, SY, SZ, .78, "plastic", None,
            top_face={"east": ("pump_cap", None, "full")})                   # крышка
    for (y0, y1, z0, z1) in ((SY + .52, SY + .62, SZ - .45, SZ + .45), (SY - .62, SY - .52, SZ - .45, SZ + .45),
                             (SY - .45, SY + .45, SZ - .62, SZ - .52), (SY - .45, SY + .45, SZ + .52, SZ + .62)):
        m.box((5.55, y0, z0), (5.57, y1, z1), "led", 2)                     # кольцо подсветки
    for dz in (-.3, .3):
        z0, z1 = SZ + dz - .14, SZ + dz + .14
        m.box((5.0, SY + .82, z0 - .03), (5.4, SY + 1.0, z1 + .03), "nickel")  # фитинг
        m.box((5.06, SY + 1.0, z0), (5.34, SY + 1.45, z1), "tube")             # шланг вверх
        m.box((5.06, SY + 1.17, z0), (6.95, SY + 1.45, z1), "tube")            # к стеклу
        m.box((6.67, SY + 1.45, z0), (6.95, 13.55, z1), "tube")                # вверх к радиатору
        m.box((6.6, 13.55, z0 - .04), (7.02, 13.62, z1 + .04), "nickel")
    # радиатор под потолком: рёбра, бачки, три вентилятора снизу
    m.box((6.2, 14.1, 3.55), (10.5, 14.85, 14.45), "rad_core", 1, faces={"down": ("rad_core", 1, "full")})
    m.box((6.2, 14.02, 3.15), (10.5, 14.88, 3.55), "shroud", 1)
    m.box((6.2, 14.02, 14.45), (10.5, 14.88, 14.85), "shroud", 1)
    for zc in (5.3, 9.0, 12.7):
        fan_unit(m, "y", -1, (8.35, 13.62, zc), 3.6, .5, corners=False)
    return m


# ---------------------------------------------------------------- память DDR5
def ram(s):
    m = Model()
    z0 = RAM_Z0
    pcb = "pcb_green" if s == 0 else "pcb_black"
    top = 5.6
    m.box((4.55, 9.15, z0 - .03), (top, 13.95, z0 + .03), pcb)
    if s == 0:   # голая планка: чипы с двух сторон + PMIC
        for side in (-1, 1):
            za, zb = (z0 - .07, z0 - .03) if side < 0 else (z0 + .03, z0 + .07)
            for i in range(5):
                y = 9.35 + i * .9
                if i == 2:
                    m.box((4.95, y + .2, za), (5.2, y + .5, zb), "chip")      # PMIC
                    continue
                m.box((4.85, y, za), (5.35, y + .7, zb), "chip")
        return m
    hs_top = {1: 5.5, 2: 5.72, 3: 5.72, 4: 5.85}[s]
    y0, y1 = 9.32, 13.78   # радиатор короче платы — по краям виден текстолит
    for (za, zb) in ((z0 - .095, z0 - .03), (z0 + .03, z0 + .095)):
        m.box((4.7, y0, za), (hs_top, y1, zb), "spreader", 1,
              faces={"north": ("spreader", 1, "full"), "south": ("spreader", 1, "full")})
    if s >= 2:   # гребень сверху между пластинами
        m.box((top, y0, z0 - .03), (hs_top, y1, z0 + .03), "metal", 1)
    if s == 2:
        for i in range(6):
            yy = y0 + .2 + i * .73
            m.box((hs_top, yy, z0 - .09), (hs_top + .05, yy + .4, z0 + .09), "metal", 1)
    if s == 3:   # светорассеиватель сверху
        m.box((hs_top, y0 + .1, z0 - .07), (5.92, y1 - .1, z0 + .07), "led", 2)
        m.box((hs_top, y0, z0 - .095), (5.95, y0 + .12, z0 + .095), "metal", 1)
        m.box((hs_top, y1 - .12, z0 - .095), (5.95, y1, z0 + .095), "metal", 1)
    if s == 4:   # высокий радиатор, широкий рассеиватель, "крылья"
        m.box((hs_top, y0 + .1, z0 - .085), (6.06, y1 - .1, z0 + .085), "led", 2)
        for (za, zb) in ((z0 - .11, z0 - .095), (z0 + .095, z0 + .11)):
            m.box((5.05, y0 + .5, za), (5.65, y1 - .5, zb), "metal", 1)
    return m


# ---------------------------------------------------------------- видеокарта
def gpu(s):
    m = Model()
    L = [6.0, 8.6, 9.3, 10.5, 11.0][s]
    W = [8.0, 8.5, 8.8, 9.0, 9.2][s]
    yb = [7.8, 7.2, 7.0, 6.6, 6.4][s]
    z0 = 14.85 - L
    pcb = "pcb_green" if s == 0 else "pcb_black"
    pcb_w = W if s == 0 else W - .25
    m.box((4.5, PCB_Y0, z0 + (0 if s == 0 else .25)), (pcb_w, PCB_Y1, 14.85), pcb)
    m.box((4.45, yb - .1, 14.85), (5.45, 8.95, 14.95), "bracket", faces={"south": ("bracket", None, "full")})
    m.box((4.5, PCB_Y0 - .02, 14.3), (4.62, PCB_Y1 + .02, 14.85), "gold")   # контакты у слота
    if s == 0:   # низкопрофильная пассивная
        plates(m, "z", 10.2, 13.2, .25, .07, (5.2, 7.8), (7.4, PCB_Y0), tint=1)
        m.box((5.0, PCB_Y1, 11.0), (5.6, 8.55, 11.6), "chip")
        m.box((6.0, PCB_Y1, 10.4), (6.5, 8.53, 12.4), "chip")
        return m
    if s == 1:   # турбина: закрытый кожух, вентилятор в конце
        m.box((4.85, yb, z0), (W, PCB_Y0, 14.85), "metal", 1,
              faces={"down": ("gpu_fans_1", None, [0, 0, 7.5, 16]), "east": ("shroud_side", 1, "full")})
        m.box((W - .35, PCB_Y1, z0 + 1.2), (W - .05, 8.75, z0 + 1.7), "plastic")    # разъём питания
        return m
    # открытая конструкция: радиатор из пластин, кожух снизу, вентиляторы, бэкплейт
    plates(m, "z", z0 + .35, 14.6, .24, .07, (4.95, yb + .28), (W - .12, PCB_Y0 - .03))
    m.box((4.95, PCB_Y0 - .18, z0 + .35), (W - .12, PCB_Y0 - .03, 14.6), "copper")   # основание/трубки
    m.box((4.85, yb, z0), (W, yb + .28, 14.85), "shroud", 1,
          faces={"down": ({2: "gpu_fans_2", 3: "gpu_fans_3", 4: "gpu_fans_3"}[s], None, [0, 0, 7.5, 16])})
    m.box((W - .06, yb + .28, z0), (W, yb + .95, 14.85), "metal", 1,
          faces={"east": ("shroud_side", 1, "full")})                               # боковая "юбка"
    m.box((4.85, yb + .28, z0), (W, PCB_Y0, z0 + .32), "metal", 1)                    # торцевая заглушка
    m.box((4.6, PCB_Y1, z0 + .3), (W - .2, 8.57, 14.8), "backplate",
          faces={"up": ("backplate", None, "full")})
    nconn = 1 if s == 2 else 2
    for i in range(nconn):                                                            # разъёмы питания
        zc = z0 + 2.0 + i * .7
        m.box((W - .45, 8.57, zc), (W - .1, 8.82, zc + .55), "plastic")
    if s == 4:
        m.box((W, yb + .5, z0 + .6), (W + .02, yb + .72, 14.2), "led", 2)
        m.box((W, PCB_Y0 - .1, z0 + 1.0), (W + .02, PCB_Y0 + .1, z0 + 4.0), "led", 2)
    return m


# ---------------------------------------------------------------- материнская плата
def motherboard(s):
    m = Model()
    edge = "pcb_green" if s == 0 else ("plastic_light" if s == 4 else "pcb_black")
    m.box((4.3, 4.5, 6.2), (BX, 14.8, 14.9), edge,
          faces={"east": (f"minepc:item/mobo/face_s{s}", None, "full")})
    # сокет с рамкой и рычагом
    m.box((BX, SY - .9, SZ - .9), (4.48, SY + .9, SZ + .9), "socket", faces={"east": ("socket", None, "full")})
    for (a, b) in (((SY - .9, SZ - .9), (SY - .82, SZ + .9)), ((SY + .82, SZ - .9), (SY + .9, SZ + .9))):
        m.box((4.48, a[0], a[1]), (4.52, b[0], b[1]), "silver")
    m.box((BX, SY - .9, SZ - 1.08), (4.54, SY + .95, SZ - .98), "nickel")
    # слоты памяти с защёлками
    nslots = 2 if s <= 1 else 4
    for i in range(nslots):
        z = RAM_Z0 - .35 * i
        m.box((BX, 9.1, z - .1), (4.65, 14.0, z + .1), "slot" if i % 2 == 0 else "slot_gray")
        m.box((BX, 14.0, z - .1), (4.72, 14.18, z + .1), "plastic_light")
    # PCIe
    if s >= 2:
        m.box((BX, 8.26, 11.38), (4.67, 8.58, 14.62), "silver")
    else:
        m.box((BX, 8.28, 11.4), (4.65, 8.56, 14.6), "slot")
    m.box((BX, 6.1, 13.0), (4.6, 6.3, 14.6), "slot")
    m.box((BX, 6.1, 12.98), (4.62, 6.3, 13.06), "plastic_light")
    # M.2: разъём и стойка для винта
    for i in range([0, 1, 2, 2, 2][s]):
        dy = -2.8 * i
        m.box((BX, M2_Y0 + .05 + dy, M2_Z1), (4.56, M2_Y1 - .05 + dy, M2_Z1 + .2), "slot")
        m.box((BX, (M2_Y0 + M2_Y1) / 2 - .08 + dy, M2_Z0 - .12), (4.5, (M2_Y0 + M2_Y1) / 2 + .08 + dy, M2_Z0 + .04), "gold")
    # цепи питания: дроссели + конденсаторы
    for z in (9.5, 9.95, 10.4, 10.85, 11.3, 11.75):
        m.box((BX, 12.75, z), (4.68, 13.05, z + .32), "choke")
    for y in (10.0, 10.45, 10.9, 11.35, 11.8):
        m.box((BX, y, 12.0), (4.68, y + .32, 12.32), "choke")
    for z in (9.55, 10.15, 10.75, 11.35):
        m.box((BX, 12.55, z), (4.62, 12.7, z + .15), "cap")
    if s >= 1:   # радиаторы VRM
        big = s >= 2
        top = (BX, 13.05 if not big else 12.95, 9.3), (4.95 if not big else 5.15, 13.75 if not big else 14.1, 12.2)
        rear = (BX, 9.8, 12.32), (4.95 if not big else 5.15, 12.6, 12.85 if not big else 13.05)
        for (f, t) in (top, rear):
            m.box(f, t, "fins_v", 1, faces={"east": ("vrm_top", 1, "full")})
        m.box((BX, 12.6, 12.32), (4.95 if not big else 5.15, 13.0, 12.85), "nickel")   # тепловая трубка-перемычка
    # кожух I/O
    if s >= 2:
        m.box((BX, 11.0, 13.2), (5.25, 14.8, 14.9), "metal", 1, faces={"east": ("io_cover", 1, "full")})
    if s >= 3:
        m.box((5.25, 11.3, 13.4), (5.27, 14.5, 13.58), "led", 2)
    # чипсет
    if s == 0:
        m.box((BX, 5.5, 8.0), (4.55, 6.3, 8.8), "chip")
    elif s == 4:
        m.box((BX, 4.8, 7.0), (4.82, 6.8, 10.2), "metal", 1, faces={"east": ("chipset_cover", 1, "full")})
        m.box((4.82, 5.0, 7.2), (4.84, 6.6, 7.35), "led", 2)
    else:
        m.box((BX, 5.2, 7.4), (4.78, 6.6, 9.0), "metal", 1, faces={"east": ("chipset_cover", 1, "full")})
        if s == 3:
            m.box((4.78, 5.3, 7.45), (4.8, 6.5, 7.6), "led", 2)
    # аудиозона: конденсаторы и экран кодека
    for (y, z) in ((5.1, 13.4), (5.1, 13.85), (5.55, 13.4), (5.55, 13.85), (6.0, 14.3)):
        m.box((BX, y, z), (4.65, y + .28, z + .28), "audio_cap")
    m.box((BX, 4.8, 12.3), (4.52, 5.6, 13.1), "metal_dark")
    # батарейка CMOS, разъёмы
    octagon(m, "x", BX, 4.5, 7.4, 11.5, .32, "silver")
    m.box((BX, 10.4, 6.25), (4.98, 12.4, 6.55), "plastic_light" if s == 4 else "plastic",
          faces={"west": ("plastic", None), "north": ("pin24", None, "full")})
    m.box((BX, 14.2, 12.5), (4.88, 14.6, 13.15), "plastic")
    for i in range(3 if s >= 2 else 2):                                       # SATA (угловые)
        yy = 5.0 + i * .38
        m.box((BX, yy, 6.22), (4.8, yy + .3, 6.5), "plastic")
    for z in (8.2, 9.6, 11.0):                                                # колодки вдоль нижнего края
        m.box((BX, 4.6, z), (4.62, 4.78, z + .9), "plastic")
    return m


# ---------------------------------------------------------------- блок питания
def psu(s):
    m = Model()
    x0, y0, z0, x1, y1, z1 = 4.3, 1.06, 10.0, 9.6, 4.06, 14.95   # на ножках над полом
    faces = {"down": ("psu_fan", None, "full"), "south": ("psu_back", None, "full"),
             "east": ("psu_side_mesh" if s == 2 else "psu_side", 1, "full")}
    if s >= 3:
        faces["north"] = ("connectors", None, "full")
    m.box((x0, y0, z0), (x1, y1, z1), "metal", 1, faces=faces)
    if s >= 1:
        m.box((x1, 2.0, 11.2), (x1 + .02, 3.25, 13.8), "label_psu", 2, faces={"east": ("label_psu", 2, "full")})
    if s == 4:
        m.box((x1, 3.78, 10.2), (x1 + .02, 3.92, 14.75), "led", 2)
        m.box((x1, 1.2, 10.2), (x1 + .02, 1.34, 14.75), "led", 2)
    for (x, z) in ((x0 + .2, z0 + .2), (x1 - .4, z0 + .2), (x0 + .2, z1 - .4), (x1 - .4, z1 - .4)):
        m.box((x, y0 - .06, z), (x + .2, y0, z + .2), "rubber")                  # ножки
    return m


# ---------------------------------------------------------------- жёсткий диск
def hdd(s):
    m = Model()
    if s == 0:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.6, 1.15, 6.95, 1.95, 4.75
    else:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.4, 1.15, 8.05, 2.3, 6.4
    # литое основание (бока с отверстиями) + плата снизу
    m.box((x0, y0, z0), (x1, y1 - .08, z1), "hdd_body",
          faces={"down": ("hdd_pcb", None, "full"), "east": ("hdd_side", None),
                 "west": ("hdd_side", None), "north": ("hdd_side", None)})
    # крышка (перекрашиваемая)
    m.box((x0 + .03, y1 - .08, z0 + .03), (x1 - .03, y1, z1 - .03), "metal", 1,
          faces={"up": ("hdd_top", 1, "full")})
    if s == 3:   # серверный: рёбра жёсткости
        for i in range(3):
            m.box((x0 + .6 + i * 1.0, y1, z0 + 2.4), (x0 + 1.0 + i * 1.0, y1 + .08, z1 - .4), "metal", 1)
        m.box((x0 + .5, y1, z0 + .5), (x1 - .5, y1 + .015, z0 + 2.0), "label", 2,
              faces={"up": ("label_hdd", 2, "full")})
    else:
        m.box((x0 + .4, y1, z0 + .7), (x1 - .4, y1 + .015, z1 - 1.0), "label", 2,
              faces={"up": ("label_hdd", 2, "full")})
        m.box(((x0 + x1) / 2 - .25, y1, z1 - .85), ((x0 + x1) / 2 + .25, y1 + .04, z1 - .35), "nickel")
    if s == 4:
        m.box((x1, y0 + .35, z0 + .15), (x1 + .02, y0 + .55, z1 - .15), "led", 2)
    # разъёмы SATA (данные + питание) на торце
    m.box((x0 + .35, y0 + .12, z1), (x0 + .9, y1 - .2, z1 + .08), "plastic")
    m.box((x0 + 1.05, y0 + .12, z1), (x0 + 2.1, y1 - .2, z1 + .08), "plastic")
    return m


# ---------------------------------------------------------------- SSD
def ssd(s):
    m = Model()
    if s <= 1:   # 2.5" SATA на поддоне перед платой
        body = "plastic" if s == 0 else "metal"
        m.box((4.05, 9.4, 1.4), (4.18, 11.9, 5.0), "plastic_dark2")
        m.box((4.18, 9.43, 1.43), (4.3, 11.87, 4.97), body, 1,
              faces={"east": ("ssd_shell", 1, "full")})
        if s == 0:
            m.box((4.3, 9.75, 1.9), (4.31, 11.55, 4.5), "label", 2, faces={"east": ("label_ssd", 2, "full")})
        else:
            m.box((4.3, 10.45, 1.43), (4.31, 10.75, 4.97), "led", 2)
            m.box((4.3, 9.7, 3.6), (4.31, 10.2, 4.7), "label", 2)
        m.box((4.07, 9.6, 5.0), (4.28, 10.6, 5.08), "plastic")    # разъём
        return m
    # NVMe M.2
    m.box((BX, M2_Y0, M2_Z0), (4.5, M2_Y1, M2_Z1), "pcb_black")
    m.box((BX, M2_Y0 + .05, M2_Z1 - .25), (4.505, M2_Y1 - .05, M2_Z1), "gold")
    if s == 2:
        m.box((4.5, 9.78, 9.45), (4.545, 10.42, 10.3), "chip")
        m.box((4.5, 9.78, 10.45), (4.545, 10.42, 11.3), "chip")
        m.box((4.5, 9.85, 11.42), (4.545, 10.35, 11.85), "chip")
        m.box((4.545, 9.76, 9.35), (4.55, 10.44, 11.1), "label", 2, faces={"east": ("label_nvme", 2, "full")})
    elif s == 3:
        m.box((4.5, 9.72, 9.25), (4.56, 10.48, 11.95), "metal", 1, faces={"east": ("nvme_plate", 1, "full")})
        m.box((4.56, 9.86, 9.6), (4.565, 10.34, 11.1), "label", 2)
    else:
        m.box((4.5, M2_Y0, M2_Z0), (4.62, M2_Y1, M2_Z1 - .3), "metal", 1)
        for i in range(8):                                                  # рёбра радиатора
            zz = M2_Z0 + .1 + i * .32
            m.box((4.62, M2_Y0 + .02, zz), (4.8, M2_Y1 - .02, zz + .16), "metal", 1)
        m.box((4.8, 10.03, M2_Z0 + .1), (4.82, 10.17, M2_Z1 - .35), "led", 2)
    return m


BUILDERS = {"cpu": cpu, "cooler": cooler, "motherboard": motherboard, "ram": ram, "gpu": gpu,
            "psu": psu, "hdd": hdd, "ssd": ssd}
