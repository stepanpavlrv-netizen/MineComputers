"""3D-модели v6: геометрия зависит от варианта (v0, v1, v2), который вычисляет Java-спецификация.

Слои цвета: tintindex 1 — основной цвет, 2 — подсветка/наклейка, 3 — текстолит.
"""
from models3d import Model, BX, SY, SZ, RAM_Z0, PCB_Y0, PCB_Y1, M2_Y0, M2_Y1, M2_Z0, M2_Z1
from models5 import plates, fan_unit, octagon, heatpipes_out

PCB = "pcb"          # нейтральный текстолит, красится слоем 3


# ================================================================ процессор
def cpu(lid, size, _):
    m = Model()
    h = .725
    m.box((4.48, SY - h, SZ - h), (4.54, SY + h, SZ + h), PCB, 3)
    m.box((4.54, SY - h + .02, SZ + h - .14), (4.545, SY - h + .14, SZ + h - .02), "gold")
    scale = [0.82, 1.0, 1.1][size]
    if lid == 0:
        d = [.24, .3, .36][size]
        m.box((4.54, SY - d, SZ - d), (4.585, SY + d, SZ + d), "die")
        for dy in (-.5, .5):
            for dz in (-.45, -.15, .15, .45):
                m.box((4.54, SY + dy - .04, SZ + dz - .06), (4.56, SY + dy + .04, SZ + dz + .06), "smd")
        return m
    r = min(.70, [0, .5, .58, .62, .66][lid] * scale)
    m.box((4.54, SY - r, SZ - r), (4.57, SY + r, SZ + r), "metal", 1)
    p = r - .1
    m.box((4.57, SY - p, SZ - p), (4.62, SY + p, SZ + p), "ihs", 1, faces={"east": ("ihs", 1, "full")})
    if lid == 3 and r < .66:
        m.box((4.54, SY - .25, SZ + r), (4.565, SY + .25, SZ + r + .05), "metal", 1)
        m.box((4.54, SY - .25, SZ - r - .05), (4.565, SY + .25, SZ - r), "metal", 1)
    if lid == 4:
        for dy in (-.6, .6):
            if r < .58:
                for dz in (-.3, 0, .3):
                    m.box((4.54, SY + dy - .03, SZ + dz - .06), (4.555, SY + dy + .03, SZ + dz + .06), "smd")
        m.box((4.62, SY + .12, SZ - .25), (4.625, SY + .4, SZ + .25), "led", 2)
    return m


# ================================================================ охлаждение
def cooler(kind, fans, rad):
    m = Model()
    top = 4.62
    if kind == 0:
        octagon(m, "x", top, 5.35, SY, SZ, 1.05, "fins_radial", None, top_face={"east": ("fins_radial", None, "full")})
        m.box((top, SY - .35, SZ - .35), (5.38, SY + .35, SZ + .35), "copper")
        m.box((5.35, SY - 1.1, SZ - 1.1), (5.42, SY + 1.1, SZ + 1.1), "shroud", 1)
        fan_unit(m, "x", 1, (5.85, SY, SZ), 2.1, .43, tint=1, corners=False)
        return m
    if kind == 4:
        return aio(m, rad)
    m.box((top, SY - .62, SZ - .62), (4.72, SY + .62, SZ + .62), "copper")
    m.box((4.72, SY - .7, SZ - .7), (4.92, SY + .7, SZ + .7), "nickel")
    if kind == 1:
        heatpipes_out(m, 4.92, 5.12, [(-.35, -1.1), (.35, -1.1), (-.35, 1.1), (.35, 1.1)])
        m.box((4.92, SY - .5, SZ - 1.2), (5.05, SY + .5, SZ + 1.2), "copper")
        plates(m, "z", SZ - 1.7, SZ + 1.7, .26, .07, (5.1, SY - 1.7), (5.85, SY + 1.7))
        fan_unit(m, "x", 1, (6.3, SY, SZ), 3.4, .45)
        return m
    if kind == 2:
        heatpipes_out(m, 4.92, 9.65, [(-.55, -.3), (.55, -.3), (-.2, .3), (.2, .3)])
        plates(m, "x", 5.55, 9.5, .28, .08, (SY - 2.1, SZ - .9), (SY + 2.1, SZ + .9))
        m.box((9.5, SY - 2.15, SZ - .95), (9.62, SY + 2.15, SZ + .95), "metal", 1, faces={"east": ("cover", 1, "full")})
        fan_unit(m, "z", -1, (7.55, SY, SZ - 1.35), 4.2, .45)
        for x in (5.75, 9.35):
            m.box((x, SY - 2.2, SZ - 1.4), (x + .05, SY + 2.2, SZ - .85), "nickel")
        if fans == 1:   # второй вентилятор сзади (push-pull)
            fan_unit(m, "z", 1, (7.55, SY, SZ + 1.35), 4.2, .45)
        m.box((9.62, SY - 2.0, SZ - .9), (9.64, SY + 2.0, SZ - .78), "led", 2)
        return m
    # kind == 3: две башни
    m.box((4.92, SY - .3, SZ - 1.66), (5.05, SY + .3, SZ + 1.66), "copper")
    heatpipes_out(m, 5.05, 10.1, [(dy, dz) for dy in (-.4, .4) for dz in (-1.55, -1.15, 1.15, 1.55)], d=.13)
    for (z0, z1) in ((SZ - 2.3, SZ - .6), (SZ + .6, SZ + 2.3)):
        plates(m, "x", 6.1, 9.95, .28, .08, (SY - 2.1, z0), (SY + 2.1, z1))
        m.box((9.95, SY - 2.15, z0 - .03), (10.07, SY + 2.15, z1 + .03), "metal", 1, faces={"east": ("cover", 1, "full")})
    fan_unit(m, "z", 1, (8.0, SY, SZ + .45), 3.9, .9, corners=False)
    if fans == 1:
        fan_unit(m, "z", -1, (8.07, SY, SZ - 2.3), 3.9, .4)
    m.box((10.07, SY - 2.0, SZ - 1.9), (10.1, SY + 2.0, SZ - 1.75), "led", 2)
    m.box((10.07, SY - 2.0, SZ + 1.75), (10.1, SY + 2.0, SZ + 1.9), "led", 2)
    return m


def aio(m, rad):
    top = 4.62
    octagon(m, "x", top, 5.45, SY, SZ, .82, "metal", 1)
    octagon(m, "x", 5.45, 5.55, SY, SZ, .78, "plastic", None, top_face={"east": ("pump_cap", None, "full")})
    for (y0, y1, z0, z1) in ((SY + .52, SY + .62, SZ - .45, SZ + .45), (SY - .62, SY - .52, SZ - .45, SZ + .45),
                             (SY - .45, SY + .45, SZ - .62, SZ - .52), (SY - .45, SY + .45, SZ + .52, SZ + .62)):
        m.box((5.55, y0, z0), (5.57, y1, z1), "led", 2)
    for dz in (-.3, .3):
        z0, z1 = SZ + dz - .14, SZ + dz + .14
        m.box((5.0, SY + .82, z0 - .03), (5.4, SY + 1.0, z1 + .03), "nickel")
        m.box((5.06, SY + 1.0, z0), (5.34, SY + 1.45, z1), "tube")
        m.box((5.06, SY + 1.17, z0), (6.95, SY + 1.45, z1), "tube")
        m.box((6.67, SY + 1.45, z0), (6.95, 13.55, z1), "tube")
        m.box((6.6, 13.55, z0 - .04), (7.02, 13.62, z1 + .04), "nickel")
    n = rad + 1                       # 120 / 240 / 360 мм → 1 / 2 / 3 вентилятора
    fan_len = 3.73
    core0 = {1: 9.4, 2: 7.0, 3: 3.4}[n]
    core1 = core0 + n * fan_len
    m.box((6.2, 14.1, core0 + .15), (10.5, 14.85, core1 - .15), "rad_core", 1, faces={"down": ("rad_core", 1, "full")})
    m.box((6.2, 14.02, core0 - .25), (10.5, 14.88, core0 + .15), "shroud", 1)
    m.box((6.2, 14.02, core1 - .15), (10.5, 14.88, core1 + .25), "shroud", 1)
    for i in range(n):
        fan_unit(m, "y", -1, (8.35, 13.62, core0 + fan_len * (i + .5)), 3.6, .5, corners=False)
    return m


# ================================================================ материнская плата
def motherboard(style, slots, m2):
    if style == SERVER_LOOK:
        return server_board(slots, m2)
    m = Model()
    m.box((4.3, 4.5, 6.2), (BX, 14.8, 14.9), PCB, 3, faces={"east": ("minepc:item/mobo/face", 3, "full")})
    m.box((BX, SY - .9, SZ - .9), (4.48, SY + .9, SZ + .9), "socket", faces={"east": ("socket", None, "full")})
    for (a, b) in (((SY - .9, SZ - .9), (SY - .82, SZ + .9)), ((SY + .82, SZ - .9), (SY + .9, SZ + .9))):
        m.box((4.48, a[0], a[1]), (4.52, b[0], b[1]), "silver")
    m.box((BX, SY - .9, SZ - 1.08), (4.54, SY + .95, SZ - .98), "nickel")
    for i in range(2 if slots == 0 else 4):
        z = RAM_Z0 - .35 * i
        m.box((BX, 9.1, z - .1), (4.65, 14.0, z + .1), "slot" if i % 2 == 0 else "slot_gray")
        m.box((BX, 14.0, z - .1), (4.72, 14.18, z + .1), "plastic_light")
    if style >= 2:
        m.box((BX, 8.26, 11.38), (4.67, 8.58, 14.62), "silver")
    else:
        m.box((BX, 8.28, 11.4), (4.65, 8.56, 14.6), "slot")
    m.box((BX, 6.1, 13.0), (4.6, 6.3, 14.6), "slot")
    m.box((BX, 6.1, 12.98), (4.62, 6.3, 13.06), "plastic_light")
    for i in range(m2):
        dy = -2.8 * i
        m.box((BX, M2_Y0 + .05 + dy, M2_Z1), (4.56, M2_Y1 - .05 + dy, M2_Z1 + .2), "slot")
        m.box((BX, (M2_Y0 + M2_Y1) / 2 - .08 + dy, M2_Z0 - .12), (4.5, (M2_Y0 + M2_Y1) / 2 + .08 + dy, M2_Z0 + .04), "gold")
    for z in (9.5, 9.95, 10.4, 10.85, 11.3, 11.75):
        m.box((BX, 12.75, z), (4.68, 13.05, z + .32), "choke")
    for y in (10.0, 10.45, 10.9, 11.35, 11.8):
        m.box((BX, y, 12.0), (4.68, y + .32, 12.32), "choke")
    for z in (9.55, 10.15, 10.75, 11.35):
        m.box((BX, 12.55, z), (4.62, 12.7, z + .15), "cap")
    if style >= 1:
        big = style >= 2
        top = (BX, 13.05 if not big else 12.95, 9.3), (4.95 if not big else 5.15, 13.75 if not big else 14.1, 12.2)
        rear = (BX, 9.8, 12.32), (4.95 if not big else 5.15, 12.6, 12.85 if not big else 13.05)
        for (f, t) in (top, rear):
            m.box(f, t, "fins_v", 1, faces={"east": ("vrm_top", 1, "full")})
        m.box((BX, 12.6, 12.32), (4.95 if not big else 5.15, 13.0, 12.85), "nickel")
    if style >= 2:
        m.box((BX, 11.0, 13.2), (5.25, 14.8, 14.9), "metal", 1, faces={"east": ("io_cover", 1, "full")})
    if style >= 3:
        m.box((5.25, 11.3, 13.4), (5.27, 14.5, 13.58), "led", 2)
    if style == 0:
        m.box((BX, 5.5, 8.0), (4.55, 6.3, 8.8), "chip")
    elif style == 4:
        m.box((BX, 4.8, 7.0), (4.82, 6.8, 10.2), "metal", 1, faces={"east": ("chipset_cover", 1, "full")})
        m.box((4.82, 5.0, 7.2), (4.84, 6.6, 7.35), "led", 2)
    else:
        m.box((BX, 5.2, 7.4), (4.78, 6.6, 9.0), "metal", 1, faces={"east": ("chipset_cover", 1, "full")})
        if style == 3:
            m.box((4.78, 5.3, 7.45), (4.8, 6.5, 7.6), "led", 2)
    for (y, z) in ((5.1, 13.4), (5.1, 13.85), (5.55, 13.4), (5.55, 13.85), (6.0, 14.3)):
        m.box((BX, y, z), (4.65, y + .28, z + .28), "audio_cap")
    m.box((BX, 4.8, 12.3), (4.52, 5.6, 13.1), "metal_dark")
    octagon(m, "x", BX, 4.5, 7.4, 11.5, .32, "silver")
    m.box((BX, 10.4, 6.25), (4.98, 12.4, 6.55), "plastic_light" if style == 4 else "plastic",
          faces={"west": ("plastic", None), "north": ("pin24", None, "full")})
    m.box((BX, 14.2, 12.5), (4.88, 14.6, 13.15), "plastic")
    for i in range(3 if style >= 2 else 2):
        yy = 5.0 + i * .38
        m.box((BX, yy, 6.22), (4.8, yy + .3, 6.5), "plastic")
    for z in (8.2, 9.6, 11.0):
        m.box((BX, 4.6, z), (4.62, 4.78, z + .9), "plastic")
    return m


# ================================================================ серверная плата
SERVER_LOOK = 5                      # MotherboardSpec.SERVER_LOOK
SRV_SOCKETS = (7.2, 12.2)            # центры сокетов по y (оба на одной высоте)
SRV_SZ = 9.75                        # центр сокетов по z
SRV_DIMM_Z = (7.3, 12.2)             # слоты памяти вдоль потока воздуха: спереди назад
SRV_PITCH = .3


def server_dimm_y(slots):
    """Центры слотов памяти по y: по ряду с каждой стороны от каждого сокета, от сокета наружу."""
    per = [2, 3, 4][slots]
    out = []
    for c in SRV_SOCKETS:
        for side in (-1, 1):
            edge = c + side * 1.05
            out += [edge + side * (.15 + .1 + SRV_PITCH * k) for k in range(per)]
    return out


def server_board(slots, m2):
    """Серверная плата (EEB): два больших сокета MS-S рядом, память рядами по бокам от каждого и вдоль потока
    воздуха, радиаторы питания за сокетами, сзади — две клетки SFP+, порт и чип BMC, VGA; спереди — M.2 и SATA.
    Без подсветки и кожухов: только текстолит и радиаторы (цвет слоя 1)."""
    m = Model()
    m.box((4.3, 4.5, 6.2), (BX, 14.8, 14.9), PCB, 3)
    z0, z1 = SRV_DIMM_Z
    # ----- память -----
    for k, y in enumerate(server_dimm_y(slots)):
        m.box((BX, y - .1, z0), (4.65, y + .1, z1), "slot" if (k // 2) % 2 == 0 else "slot_gray")
        for (a, b) in ((z0 - .16, z0), (z1, z1 + .16)):                       # защёлки
            m.box((BX, y - .1, a), (4.72, y + .1, b), "plastic_light")
    # ----- сокеты MS-S: большие, с рамкой и четырьмя винтами крепления радиатора -----
    for cy in SRV_SOCKETS:
        a0, a1, b0, b1 = cy - 1.0, cy + 1.0, SRV_SZ - 1.25, SRV_SZ + 1.25
        m.box((BX, a0, b0), (4.5, a1, b1), "socket", faces={"east": ("socket", None, "full")})
        for (f, t) in (((a0 - .08, b0 - .08), (a1 + .08, b0)), ((a0 - .08, b1), (a1 + .08, b1 + .08)),
                       ((a0 - .08, b0), (a0, b1)), ((a1, b0), (a1 + .08, b1))):
            m.box((BX, f[0], f[1]), (4.56, t[0], t[1]), "nickel")
        m.box((BX, a0 - .2, b0 + .3), (4.6, a0 - .1, b1 - .3), "silver")          # рычаг
        for (y, z) in ((a0 + .2, b0 - .22), (a1 - .2, b0 - .22), (a0 + .2, b1 + .22), (a1 - .2, b1 + .22)):
            octagon(m, "x", BX, 4.62, y, z, .1, "metal_dark")
        # радиатор питания за сокетом
        m.box((BX, cy - .95, 11.48), (5.0, cy + .95, z1), "fins_v", 1, faces={"east": ("vrm_top", 1, "full")})
        for i in range(4):
            m.box((BX, cy - .5 + i * .3, 11.34), (4.62, cy - .3 + i * .3, 11.46), "choke")
    # ----- сзади: SFP+, BMC, VGA, питание -----
    for y0 in (4.85, 6.15):                                                        # две клетки SFP+ (10 Гбит/с)
        m.box((BX, y0, 13.3), (5.05, y0 + 1.05, 14.9), "metal", faces={"south": ("metal_dark", None)})
        m.box((5.05, y0 + .15, 14.6), (5.07, y0 + .35, 14.8), "led", 2)
    m.box((BX, 7.5, 13.6), (4.95, 8.35, 14.9), "plastic", faces={"south": ("connectors", None, "full")})   # порт BMC
    m.box((BX, 8.7, 12.6), (4.58, 9.55, 13.45), "chip")                             # чип BMC
    m.box((4.58, 8.85, 12.75), (4.6, 9.4, 13.3), "label")
    m.box((BX, 9.85, 12.55), (4.95, 11.65, 13.8), "metal", 1, faces={"east": ("chipset_cover", 1, "full")})  # чипсет
    m.box((BX, 11.95, 14.25), (4.85, 13.15, 14.9), "connectors")                     # VGA
    m.box((BX, 13.35, 12.6), (4.98, 14.55, 13.55), "plastic", faces={"east": ("pin24", None, "full")})     # питание
    for y in (12.0, 12.55):                                                          # 8-pin процессоров
        m.box((BX, y, 12.55), (4.9, y + .45, 13.05), "plastic")
    # ----- спереди: M.2 поперёк платы, разъёмы SATA -----
    for k in range(m2):
        ya = 9.0 if k == 0 else 5.0
        m.box((BX, ya, 6.35), (4.56, ya + .2, 7.1), "slot")
        m.box((BX, ya + 2.75, 6.65), (4.52, ya + 2.9, 6.8), "gold")                 # стойка винта
    for k in range(4):
        y = 12.35 + k * .58
        m.box((BX, y, 6.3), (4.78, y + .42, 6.75), "plastic", faces={"north": ("pin24", None, "full")})
    return m


# ================================================================ память
def ram(heatsink, led, chips):
    m = Model()
    z0 = RAM_Z0
    top = 5.6
    m.box((4.55, 9.15, z0 - .03), (top, 13.95, z0 + .03), PCB, 3)
    if heatsink == 0:
        per_side = [2, 4, 8][chips]
        span = 4.4
        step = span / per_side
        h = min(.9, step * .78)
        for side in (-1, 1):
            za, zb = (z0 - .07, z0 - .03) if side < 0 else (z0 + .03, z0 + .07)
            for i in range(per_side):
                y = 9.35 + i * step + (step - h) / 2
                m.box((4.85, y, za), (5.35, y + h, zb), "chip")
        m.box((4.62, 11.4, z0 + .03), (4.78, 11.7, z0 + .06), "chip")     # контроллер питания
        return m
    hs_top = {1: 5.5, 2: 5.72, 3: 5.85}[heatsink]
    y0, y1 = 9.32, 13.78
    for (za, zb) in ((z0 - .095, z0 - .03), (z0 + .03, z0 + .095)):
        m.box((4.7, y0, za), (hs_top, y1, zb), "spreader", 1,
              faces={"north": ("spreader", 1, "full"), "south": ("spreader", 1, "full")})
    if heatsink >= 2:
        m.box((top, y0, z0 - .03), (hs_top, y1, z0 + .03), "metal", 1)
    if heatsink == 2 and not led:
        for i in range(6):
            yy = y0 + .2 + i * .73
            m.box((hs_top, yy, z0 - .09), (hs_top + .05, yy + .4, z0 + .09), "metal", 1)
    if heatsink == 3:
        for (za, zb) in ((z0 - .11, z0 - .095), (z0 + .095, z0 + .11)):
            m.box((5.05, y0 + .5, za), (5.65, y1 - .5, zb), "metal", 1)
    if led:
        bar = .2 if heatsink < 3 else .21
        m.box((hs_top, y0 + .1, z0 - .07), (hs_top + bar, y1 - .1, z0 + .07), "led", 2)
        m.box((hs_top, y0, z0 - .095), (hs_top + bar + .02, y0 + .12, z0 + .095), "metal", 1)
        m.box((hs_top, y1 - .12, z0 - .095), (hs_top + bar + .02, y1, z0 + .095), "metal", 1)
    return m


# ================================================================ видеокарта
VRAM_SPOTS = [(-1.2, -.45), (-1.2, .45), (1.2, -.45), (1.2, .45), (-.45, -1.3), (.45, -1.3),
              (-.45, 1.3), (.45, 1.3), (-1.2, -1.35), (1.2, -1.35), (-1.2, 1.35), (1.2, 1.35)]


def gpu(cooler_kind, length, v2):
    m = Model()
    L = [6.5, 9.0, 11.0][length]
    W = [8.2, 8.7, 9.2][length]
    z0 = 14.85 - L
    pcb_x1 = W - .15
    m.box((4.5, PCB_Y0, z0), (pcb_x1, PCB_Y1, 14.85), PCB, 3)
    m.box((4.5, PCB_Y0 - .02, 14.3), (4.62, PCB_Y1 + .02, 14.85), "gold")
    m.box((pcb_x1 - .4, PCB_Y1, z0 + 1.0), (pcb_x1 - .05, 8.74, z0 + 1.55), "plastic")        # разъём питания
    if length >= 1:
        m.box((pcb_x1 - .4, PCB_Y1, z0 + 1.7), (pcb_x1 - .05, 8.74, z0 + 2.25), "plastic")
    if cooler_kind == 0:   # голая плата: чип, видеопамять, питание
        yb = PCB_Y0 - 1.0
        m.box((4.45, yb, 14.85), (5.45, 8.95, 14.95), "bracket", faces={"south": ("bracket", None, "full")})
        cx, cz = (4.5 + pcb_x1) / 2, 14.85 - min(4.2, L * .45)
        m.box((cx - .7, PCB_Y0 - .04, cz - .7), (cx + .7, PCB_Y0, cz + .7), "pcb_green")
        m.box((cx - .38, PCB_Y0 - .08, cz - .38), (cx + .38, PCB_Y0 - .04, cz + .38), "die")
        for (dx, dz) in VRAM_SPOTS[:[2, 4, 6, 8, 12][v2]]:
            x, z = cx + dx, cz + dz
            m.box((x - .22, PCB_Y0 - .06, z - .27), (x + .22, PCB_Y0, z + .27), "chip")
        for i, x in enumerate([5.0 + j * .5 for j in range(int((pcb_x1 - 5.4) / .5))]):
            m.box((x, PCB_Y0 - .3, z0 + .7), (x + .38, PCB_Y0, z0 + 1.08), "choke")
            m.box((x + .08, PCB_Y0 - .16, z0 + 1.3), (x + .3, PCB_Y0, z0 + 1.52), "cap")
        return m
    if cooler_kind == 1:   # пассивный радиатор
        m.box((4.45, 7.7, 14.85), (5.45, 8.95, 14.95), "bracket", faces={"south": ("bracket", None, "full")})
        za, zb = max(z0 + .4, 14.85 - L * .8), 14.4
        plates(m, "z", za, zb, .25, .07, (5.0, 7.8), (pcb_x1 - .3, PCB_Y0 - .05), tint=1)
        m.box((5.0, PCB_Y0 - .12, za), (pcb_x1 - .3, PCB_Y0 - .05, zb), "metal", 1)
        return m
    T = 1.2 if cooler_kind == 2 else [1.2, 1.6, 2.0][v2]
    yb = PCB_Y0 - T
    m.box((4.45, yb - .1, 14.85), (5.45, 8.95, 14.95), "bracket", faces={"south": ("bracket", None, "full")})
    if cooler_kind == 2:   # турбина: закрытый кожух
        m.box((4.85, yb, z0), (W, PCB_Y0, 14.85), "metal", 1,
              faces={"down": ("gpu_fans_1", None, [0, 0, 7.5, 16]), "east": ("shroud_side", 1, "full")})
        m.box((W, yb + .3, z0 + .6), (W + .02, yb + .45, 14.3), "led", 2)
        return m
    plates(m, "z", z0 + .35, 14.6, .24, .07, (4.95, yb + .28), (W - .12, PCB_Y0 - .03))
    m.box((4.95, PCB_Y0 - .18, z0 + .35), (W - .12, PCB_Y0 - .03, 14.6), "copper")
    m.box((4.85, yb, z0), (W, yb + .28, 14.85), "shroud", 1,
          faces={"down": ("gpu_fans_2" if cooler_kind == 3 else "gpu_fans_3", None, [0, 0, 7.5, 16])})
    m.box((W - .06, yb + .28, z0), (W, yb + .95, 14.85), "metal", 1, faces={"east": ("shroud_side", 1, "full")})
    m.box((4.85, yb + .28, z0), (W, PCB_Y0, z0 + .32), "metal", 1)
    m.box((4.6, PCB_Y1, z0 + .3), (W - .2, 8.57, 14.8), "backplate", faces={"up": ("backplate", None, "full")})
    m.box((W, yb + .5, z0 + .6), (W + .02, yb + .72, 14.2), "led", 2)
    return m


# ================================================================ блок питания
def psu(side, modular, _):
    m = Model()
    x0, y0, z0, x1, y1, z1 = 4.3, 1.06, 10.0, 9.6, 4.06, 14.95
    faces = {"down": ("psu_fan", None, "full"), "south": ("psu_back", None, "full"),
             "east": ("psu_side_mesh" if side == 1 else "psu_side", 1, "full")}
    if modular == 2:
        faces["north"] = ("connectors", None, "full")
    m.box((x0, y0, z0), (x1, y1, z1), "metal", 1, faces=faces)
    m.box((x1, 2.0, 11.2), (x1 + .02, 3.25, 13.8), "label_psu", faces={"east": ("label_psu", None, "full")})
    if modular == 1:
        m.box((5.0, 1.5, z0 - .04), (7.4, 3.6, z0), "connectors", faces={"north": ("connectors", None, "full")})
    if modular < 2:   # пучок несъёмных кабелей
        m.box((7.7, 2.0, z0 - .6), (9.0, 3.4, z0), "tube")
    if side == 2:
        m.box((x1, 3.78, 10.2), (x1 + .02, 3.92, 14.75), "led", 2)
        m.box((x1, 1.2, 10.2), (x1 + .02, 1.34, 14.75), "led", 2)
    for (x, z) in ((x0 + .2, z0 + .2), (x1 - .4, z0 + .2), (x0 + .2, z1 - .4), (x1 - .4, z1 - .4)):
        m.box((x, y0 - .06, z), (x + .2, y0, z + .2), "rubber")
    return m


# ================================================================ жёсткий диск
def hdd(form, lid, _):
    m = Model()
    if form == 0:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.6, 1.15, 6.95, 1.95, 4.75
    else:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.4, 1.15, 8.05, 2.3, 6.4
    m.box((x0, y0, z0), (x1, y1 - .08, z1), "hdd_body",
          faces={"down": ("hdd_pcb", None, "full"), "east": ("hdd_side", None),
                 "west": ("hdd_side", None), "north": ("hdd_side", None)})
    m.box((x0 + .03, y1 - .08, z0 + .03), (x1 - .03, y1, z1 - .03), "metal", 1, faces={"up": ("hdd_top", 1, "full")})
    if lid == 1:
        for i in range(3 if form else 2):
            m.box((x0 + .6 + i * 1.0, y1, z0 + 2.4), (x0 + 1.0 + i * 1.0, y1 + .08, z1 - .4), "metal", 1)
        m.box((x0 + .5, y1, z0 + .5), (x1 - .5, y1 + .015, z0 + 2.0), "label", 2, faces={"up": ("label_hdd", 2, "full")})
    else:
        m.box((x0 + .4, y1, z0 + .7), (x1 - .4, y1 + .015, z1 - 1.0), "label", 2, faces={"up": ("label_hdd", 2, "full")})
        m.box(((x0 + x1) / 2 - .25, y1, z1 - .85), ((x0 + x1) / 2 + .25, y1 + .04, z1 - .35), "nickel")
    if lid == 2:
        m.box((x1, y0 + .35 * (y1 - y0), z0 + .15), (x1 + .02, y0 + .6 * (y1 - y0), z1 - .15), "led", 2)
    m.box((x0 + .35, y0 + .06, z1), (x0 + .9, y1 - .1, z1 + .08), "plastic")
    m.box((x0 + 1.05, y0 + .06, z1), (x0 + 2.1, y1 - .1, z1 + .08), "plastic")
    return m


# ================================================================ SSD
def ssd(kind, cooling, _):
    m = Model()
    if kind == 0:   # 2.5" SATA на поддоне
        m.box((4.05, 9.4, 1.4), (4.18, 11.9, 5.0), "plastic_dark2")
        shell = "shroud" if cooling == 0 else "ssd_shell"
        m.box((4.18, 9.43, 1.43), (4.3, 11.87, 4.97), shell, 1, faces={"east": (shell, 1, "full")})
        if cooling < 2:
            m.box((4.3, 9.75, 1.9), (4.31, 11.55, 4.5), "label", 2, faces={"east": ("label_ssd", 2, "full")})
        else:
            m.box((4.3, 10.45, 1.43), (4.31, 10.75, 4.97), "led", 2)
            m.box((4.3, 9.7, 3.6), (4.31, 10.2, 4.7), "label", 2)
        m.box((4.07, 9.6, 5.0), (4.28, 10.6, 5.08), "plastic")
        return m
    m.box((BX, M2_Y0, M2_Z0), (4.5, M2_Y1, M2_Z1), PCB, 3)
    m.box((BX, M2_Y0 + .05, M2_Z1 - .25), (4.505, M2_Y1 - .05, M2_Z1), "gold")
    if cooling == 0:
        m.box((4.5, 9.78, 9.45), (4.545, 10.42, 10.3), "chip")
        m.box((4.5, 9.78, 10.45), (4.545, 10.42, 11.3), "chip")
        m.box((4.5, 9.85, 11.42), (4.545, 10.35, 11.85), "chip")
        m.box((4.545, 9.76, 9.35), (4.55, 10.44, 11.1), "label", 2, faces={"east": ("label_nvme", 2, "full")})
    elif cooling == 1:
        m.box((4.5, 9.72, 9.25), (4.56, 10.48, 11.95), "metal", 1, faces={"east": ("nvme_plate", 1, "full")})
        m.box((4.56, 9.86, 9.6), (4.565, 10.34, 11.1), "label", 2)
    else:
        m.box((4.5, M2_Y0, M2_Z0), (4.62, M2_Y1, M2_Z1 - .3), "metal", 1)
        for i in range(8):
            zz = M2_Z0 + .1 + i * .32
            m.box((4.62, M2_Y0 + .02, zz), (4.8, M2_Y1 - .02, zz + .16), "metal", 1)
        m.box((4.8, 10.03, M2_Z0 + .1), (4.82, 10.17, M2_Z1 - .35), "led", 2)
    return m


BUILDERS = {"CPU": ("cpu", cpu), "COOLER": ("cooler", cooler), "MOTHERBOARD": ("motherboard", motherboard),
            "RAM": ("ram", ram), "GPU": ("gpu", gpu), "PSU": ("psu", psu), "HDD": ("hdd", hdd), "SSD": ("ssd", ssd)}
