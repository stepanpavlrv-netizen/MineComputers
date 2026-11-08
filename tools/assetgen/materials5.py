"""Материалы v5 для детализированных моделей. Многие 32x32 — для крупных деталей."""
import math, random
from px import Canvas, noise, hx
from materials import brushed, solid, stripes, materials as materials_v4, gpu_bottom
from items import M_BASE, M_HI, M_LO, M_ENGR


def brushed_n(color, size=32, seed=0, amount=5):
    c = Canvas(size, size)
    rnd = random.Random(seed)
    b = hx(color)
    for y in range(size):
        d = rnd.randint(-amount, amount)
        for x in range(size):
            e = d + rnd.randint(-2, 2)
            c.px(x, y, tuple(max(0, min(255, b[i] + e)) for i in range(3)) + (255,))
    return c


def fan_disc(c, cx, cy, r, blades=9, dark="121316", blade="50535a", edge="6a6d75", ring="2a2b30",
             hub="34363c", cap="6c6f77"):
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for y in range(int(cy - r - 1), int(cy + r + 2)):
            dx, dy = x + .5 - cx, y + .5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            a = math.atan2(dy, dx)
            k = (a * blades / (2 * math.pi) + d / r * 0.9) % 1.0
            col = blade if k < 0.55 else dark
            if 0.5 < k < 0.58:
                col = edge
            if d > r - 1.0:
                col = ring
            c.px(x, y, col)
    hr = r * 0.34
    for x in range(int(cx - hr - 1), int(cx + hr + 2)):
        for y in range(int(cy - hr - 1), int(cy + hr + 2)):
            d = math.hypot(x + .5 - cx, y + .5 - cy)
            if d <= hr:
                c.px(x, y, cap if d < hr * 0.55 else hub)


def fan32():
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, "1b1c20", 2, 101)
    fan_disc(c, 16, 16, 15.2)
    return c


def lines(c, rows, x0, color):
    for (y, x1) in rows:
        c.hline(x0, x1, y, color)


def label(kind):
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, "f2f2ef", 2, 110 + len(kind))
    c.hline(0, 31, 0, "d8d8d4"); c.hline(0, 31, 31, "c8c8c4")
    if kind == "hdd":
        c.rect(2, 2, 13, 7, "3a3c42")                   # логотип-плашка
        lines(c, [(10, 24), (13, 18), (16, 26), (19, 14)], 2, "8a8a86")
        for x in range(3, 29):                         # штрихкод
            if (x * 7) % 3:
                c.vline(x, 23, 28, "2a2a2a")
    elif kind == "ssd":
        c.rect(2, 3, 29, 9, "2c2e34")
        lines(c, [(14, 22), (18, 16), (22, 25)], 3, "8a8a86")
    elif kind == "nvme":
        lines(c, [(5, 20), (9, 14), (13, 22)], 3, "8a8a86")
        c.rect(23, 4, 29, 10, "2a2a2a"); c.rect(24, 5, 28, 9, "f2f2ef"); c.rect(25, 6, 27, 8, "2a2a2a")
        for x in range(3, 29):
            if (x * 5) % 3:
                c.vline(x, 20, 27, "2a2a2a")
    elif kind == "psu":
        noise(c, 0, 0, 31, 31, "eeeeec", 2, 120)
        c.rect(2, 2, 18, 8, "2c2e34")                   # название
        c.rect(22, 2, 29, 9, "c8a040"); c.rect(23, 3, 28, 8, "e0c060")   # значок сертификата
        lines(c, [(13, 27), (16, 22), (19, 25)], 2, "8a8a86")
        for y in (24, 26, 28):
            c.hline(2, 29, y, "b0b0ac")
    return c


def materials():
    m = {k: v for k, v in materials_v4().items()}
    m["fan"] = fan32()
    hub = Canvas()
    noise(hub, 0, 0, 15, 15, "2a2c31", 2, 130)
    for x in range(16):
        for y in range(16):
            d = math.hypot(x + .5 - 8, y + .5 - 8)
            if d < 7.5:
                hub.px(x, y, "3a3c42")
            if d < 4.5:
                hub.px(x, y, "6c6f77")
            if 2.2 < d < 3.0:
                hub.px(x, y, "50535a")
    m["fan_hub"] = hub
    m["fin"] = brushed(M_BASE, 31)
    m["shroud"] = solid("cacdd2", 3, 32)
    m["rubber"] = solid("45474d", 2, 33)
    m["nickel"] = brushed("dadde1", 34)
    m["smd"] = solid("b88a50", 2, 35)
    m["choke"] = solid("3c3e44", 3, 36)
    m["audio_cap"] = solid("c9a043", 3, 37)
    m["plastic_dark2"] = solid("1e1f23", 2, 38)
    m["tube"] = Canvas()
    for x in range(16):
        for y in range(16):
            m["tube"].px(x, y, "1c1d21" if (x + y) % 4 < 2 else "2a2c31")

    # радиальные рёбра боксового кулера
    fr = Canvas(32, 32)
    noise(fr, 0, 0, 31, 31, "8e939a", 3, 40)
    for x in range(32):
        for y in range(32):
            a = math.atan2(y + .5 - 16, x + .5 - 16)
            if int((a + math.pi) * 24 / (2 * math.pi)) % 2 == 0:
                fr.px(x, y, "c4c8ce")
    m["fins_radial"] = fr

    # крышка башни: шлифовка + фаска + рельефная эмблема
    cv = brushed_n(M_BASE, 32, 41)
    cv.hline(0, 31, 0, M_HI); cv.vline(0, 0, 31, M_HI); cv.hline(0, 31, 31, M_LO); cv.vline(31, 0, 31, M_LO)
    cv.rect(11, 11, 20, 20, "bfc3c9"); cv.rect(13, 13, 18, 18, M_BASE)
    for i in range(4):
        cv.px(13 + i, 18 - i, M_ENGR)
    m["cover"] = cv

    pc = Canvas(32, 32)
    noise(pc, 0, 0, 31, 31, "141518", 2, 42)
    for x in range(32):
        for y in range(32):
            d = math.hypot(x + .5 - 16, y + .5 - 16)
            if d < 12:
                pc.px(x, y, "1f2024")
            if 8.5 < d < 9.5:
                pc.px(x, y, "3a3c42")
            if d < 12 and x < y - 10:
                pc.px(x, y, "2a2c31")   # блик
    m["pump_cap"] = pc

    rc = Canvas(32, 32)
    for x in range(32):
        for y in range(32):
            rc.px(x, y, M_BASE if x % 2 == 0 else M_LO)
    for y in (0, 31):
        rc.hline(0, 31, y, M_ENGR)
    m["rad_core"] = rc

    # радиатор памяти: шлифовка + рельеф
    sp = brushed_n(M_BASE, 32, 43)
    sp.hline(0, 31, 0, M_HI); sp.hline(0, 31, 31, M_LO)
    for i in range(10):
        sp.px(4 + i, 20 - i, M_ENGR); sp.px(5 + i, 20 - i, M_HI)
    sp.rect(16, 12, 28, 14, "bec2c8"); sp.hline(16, 28, 15, M_HI)
    m["spreader"] = sp

    ih = brushed_n("c9cdd2", 32, 44, 3)
    lines(ih, [(9, 20), (12, 15), (15, 22), (18, 17)], 5, "aeb2b8")
    ih.rect(23, 21, 27, 25, "a3a7ad")
    m["ihs"] = ih

    sh = brushed_n(M_BASE, 32, 45)
    for y in (6, 7, 24, 25):
        sh.hline(0, 31, y, M_LO if y % 2 == 0 else M_HI)
    for i in range(8):
        sh.px(20 + i, 14 + i // 2, M_ENGR)
    m["shroud_side"] = sh

    bp = brushed_n("3a3d44", 32, 46, 3)
    for y in range(4, 28, 3):
        for x in range(20, 29):
            bp.px(x, y, "1b1c20")
    for (x, y) in [(2, 2), (29, 2), (2, 29), (29, 29), (12, 16)]:
        bp.px(x, y, "6a6e74")
    bp.rect(4, 8, 14, 12, "4a4e56")
    m["backplate"] = bp

    br = brushed_n("b9bec5", 32, 47, 3)
    for (y0, y1) in ((3, 7), (10, 14), (17, 21)):          # порты
        br.rect(6, y0, 16, y1, "1b1c20"); br.rect(7, y0 + 1, 15, y1 - 1, "2a2c31")
    for y in range(24, 31, 2):
        br.hline(4, 27, y, "8a8f97")
    m["bracket"] = br

    so = Canvas()
    noise(so, 0, 0, 15, 15, "aeb3ba", 3, 48)
    so.rect(2, 2, 13, 13, "8a6a2a")
    for x in range(3, 13):
        for y in range(3, 13):
            if (x + y) % 2 == 0:
                so.px(x, y, "d9b45a")
    m["socket"] = so

    cap = Canvas()
    noise(cap, 0, 0, 15, 15, "1b1c20", 2, 49)
    for x in range(16):
        for y in range(16):
            if math.hypot(x + .5 - 8, y + .5 - 8) < 5:
                cap.px(x, y, "5a5d64")
    m["cap"] = cap

    vt = brushed_n(M_BASE, 32, 50)
    for y in range(3, 30, 4):
        vt.hline(0, 31, y, M_LO)
    vt.rect(10, 13, 22, 18, "bec2c8")
    m["vrm_top"] = vt

    io = brushed_n(M_BASE, 32, 51)
    for i in range(14):
        io.px(4 + i, 26 - i, M_ENGR); io.px(5 + i, 26 - i, M_HI)
    io.rect(18, 5, 26, 8, "bec2c8")
    m["io_cover"] = io

    cc = brushed_n(M_BASE, 32, 52)
    for k in range(3):
        for i in range(8):
            cc.px(8 + i, 8 + k * 6 + abs(i - 4), M_ENGR)
    m["chipset_cover"] = cc

    p24 = Canvas()
    noise(p24, 0, 0, 15, 15, "e2e3e5", 2, 53)
    for x in range(2, 14, 2):
        for y in range(2, 14, 4):
            p24.rect(x, y, x, y + 1, "2a2c31")
    m["pin24"] = p24

    pf = Canvas(32, 32)
    noise(pf, 0, 0, 31, 31, "141518", 2, 54)
    fan_disc(pf, 16, 16, 14.2)
    for r in (5, 9, 13):                                     # проволочная решётка
        for x in range(32):
            for y in range(32):
                if abs(math.hypot(x + .5 - 16, y + .5 - 16) - r) < 0.5:
                    pf.px(x, y, "8a8f97")
    pf.vline(16, 2, 29, "8a8f97"); pf.hline(2, 29, 16, "8a8f97")
    for (x, y) in [(1, 1), (30, 1), (1, 30), (30, 30)]:
        pf.px(x, y, "6a6e74")
    m["psu_fan"] = pf

    pb = Canvas(32, 32)
    noise(pb, 0, 0, 31, 31, "1b1c20", 2, 55)
    for x in range(3, 30):
        for y in range(3, 16):
            if (x % 3 == 0 and y % 2 == 0) or (x % 3 == 1 and y % 2 == 1):
                pb.px(x, y, "0d0e10")
    pb.rect(4, 20, 13, 28, "0d0e10"); pb.rect(6, 22, 11, 26, "2a2c31")    # разъём питания
    pb.rect(17, 21, 21, 27, "2a2c31"); pb.rect(18, 22, 20, 24, "8a3030")  # выключатель
    m["psu_back"] = pb

    ps = brushed_n(M_BASE, 32, 56)
    ps.hline(1, 30, 1, M_HI); ps.hline(1, 30, 30, M_LO); ps.vline(1, 1, 30, M_HI); ps.vline(30, 1, 30, M_LO)
    m["psu_side"] = ps
    pm = Canvas(32, 32)
    noise(pm, 0, 0, 31, 31, M_BASE, 2, 57)
    for x in range(2, 30):
        for y in range(2, 30):
            if (x % 4 in (1, 2) and y % 3 == 1) or (x % 4 in (3, 0) and y % 3 == 2):
                pm.px(x, y, "7a7f87")
    m["psu_side_mesh"] = pm
    m["label_psu"] = label("psu")

    hs = Canvas()
    noise(hs, 0, 0, 15, 15, "4a4e56", 3, 58)
    for x in (2, 8, 13):
        hs.px(x, 7, "1b1c20"); hs.px(x, 8, "1b1c20")
    m["hdd_side"] = hs
    hp = Canvas(32, 32)
    noise(hp, 0, 0, 31, 31, "1f4a2c", 3, 59)
    hp.rect(6, 6, 12, 12, "1d1e22"); hp.rect(16, 8, 21, 13, "1d1e22"); hp.rect(8, 18, 15, 22, "1d1e22")
    for x in range(2, 30, 3):
        hp.px(x, 27, "c79a5b")
    m["hdd_pcb"] = hp
    m["label_hdd"] = label("hdd")
    m["label_ssd"] = label("ssd")
    m["label_nvme"] = label("nvme")

    ss = brushed_n("cacdd2", 32, 60, 3)
    ss.hline(0, 31, 0, "e4e6e9"); ss.vline(0, 0, 31, "e4e6e9"); ss.hline(0, 31, 31, "9a9fa6"); ss.vline(31, 0, 31, "9a9fa6")
    m["ssd_shell"] = ss

    nv = brushed_n(M_BASE, 32, 61)
    for k in range(4):
        for i in range(6):
            nv.px(4 + k * 7 + i // 2, 12 + abs(i - 3), M_ENGR)
    m["nvme_plate"] = nv

    m["gpu_fans_1"] = gpu_bottom_v5(1)
    m["gpu_fans_2"] = gpu_bottom_v5(2)
    m["gpu_fans_3"] = gpu_bottom_v5(3)
    return m


def gpu_bottom_v5(n):
    """Днище видеокарты 32x32 (используется область u 0..15): вентиляторы в рамках кожуха."""
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, "26272b", 2, 70 + n)
    if n == 1:
        for y in range(2, 17, 2):
            c.hline(2, 12, y, "141518")
        fan_disc(c, 7.5, 24.5, 6.4)
        return c
    step = 32 / n
    r = min(7.0, step / 2 - 0.4)
    for i in range(n):
        cy = step * i + step / 2
        for x in range(16):                      # тёмное кольцо-рамка
            for y in range(32):
                if r - .2 < math.hypot(x + .5 - 7.5, y + .5 - cy) < r + .9:
                    c.px(x, y, "15161a")
        fan_disc(c, 7.5, cy, r)
    return c


def mobo_face_v5(style):
    """Лицевая сторона платы 32x32: z = 14.9 - tu*8.7/32, y = 14.8 - tv*10.3/32."""
    pcb = ["2c5e3f", "26352e", "23262b", "1e2126", "d8dadc"][style]
    trace = ["357049", "30433a", "2c3036", "262a30", "c8cbcf"][style]
    silk = ["8fb59a", "77867e", "6f747c", "6a7078", "8d9196"][style]
    c = Canvas(32, 32)
    noise(c, 0, 0, 31, 31, pcb, 3, 40 + style)

    def T(z, y):
        return int(round((14.9 - z) * 32 / 8.7)), int(round((14.8 - y) * 32 / 10.3))

    rnd = random.Random(80 + style)
    for _ in range(18):
        y = rnd.randint(2, 29); x0 = rnd.randint(1, 22); c.hline(x0, min(30, x0 + rnd.randint(3, 9)), y, trace)
    for _ in range(12):
        x = rnd.randint(2, 29); y0 = rnd.randint(1, 22); c.vline(x, y0, min(30, y0 + rnd.randint(3, 7)), trace)
    # линия изоляции аудиозоны
    a0, b0 = T(14.9, 7.0); a1, b1 = T(12.1, 4.5)
    for x in range(a0, a1 + 1):
        c.px(x, b0, silk)
    for y in range(b0, b1 + 1):
        c.px(a1, y, silk)
    # контуры под M.2 и шелкография
    for dy in (0, -2.8):
        u0, v0 = T(12.1, 10.5 + dy); u1, v1 = T(9.2, 9.7 + dy)
        for x in range(u0, u1 + 1):
            c.px(x, v0, silk); c.px(x, v1, silk)
    for (z, y) in [(14.6, 14.5), (6.5, 14.5), (14.6, 4.8), (6.5, 4.8), (10.7, 8.0), (6.5, 9.5)]:
        tu, tv = T(z, y)
        c.rect(tu - 1, tv - 1, tu, tv, "b9bec5"); c.px(tu, tv, "6a6e74")
    for _ in range(46):
        x, y = rnd.randint(1, 30), rnd.randint(1, 30)
        c.px(x, y, rnd.choice(["8a6a3d", "c79a5b", "3a3c42", silk]))
    for (z, y, w) in [(12.0, 10.6, 5), (13.4, 7.6, 3), (8.6, 4.9, 6)]:
        tu, tv = T(z, y); c.hline(tu, tu + w, tv, silk)
    return c
