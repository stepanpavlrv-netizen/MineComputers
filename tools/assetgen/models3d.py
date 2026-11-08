"""3D-модели комплектующих.

Все координаты задаются в "координатах корпуса" (пиксели блока, корпус смотрит на север):
  x: поддон материнской платы = 4, стекло = 12.75
  y: пол = 1, потолок = 15
  z: передняя панель = 1, задняя = 15
В модели x сдвигается на +4, так что точка корпуса (4, 8, 8) = центр модели (8, 8, 8).
Рендерер ставит центр модели в точку (4, 8, 8) корпуса — и каждая деталь
оказывается на своём месте. Для второго/третьего слота добавляется смещение.

Масштаб: 1 пиксель ≈ 2.8 см.
"""
import math

MODEL_DX = 4.0

# --- опорные точки материнской платы ---
BX = 4.45                    # лицевая поверхность платы
SY, SZ = 11.6, 10.7          # центр сокета процессора
RAM_Z0 = 8.9                 # первый слот памяти
RAM_PITCH = 0.35
PCB_Y0, PCB_Y1 = 8.42, 8.48  # плата видеокарты (горизонтально)
M2_Y0, M2_Y1, M2_Z0, M2_Z1 = 9.7, 10.5, 9.2, 12.1
M2_PITCH = -2.8
HDD_PITCH = 1.5
SATA_SSD_PITCH = -3.0

TEX = lambda name: f"minepc:item/mat/{name}"
DIRS = ("north", "south", "east", "west", "up", "down")


def face_dims(d, size):
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy),
            "up": (sx, sz), "down": (sx, sz)}[d]


class Model:
    def __init__(self):
        self.elements = []
        self.textures = {}

    def _tex(self, mat):
        key = "".join(ch if ch.isalnum() else "_" for ch in mat.split(":")[-1])
        self.textures[key] = mat if ":" in mat else TEX(mat)
        return "#" + key

    def box(self, frm, to, mat, tint=None, faces=None, k=2.0, skip=()):
        """frm/to — координаты корпуса. faces: {dir: (mat, tint, uv)} для особых граней.
        uv: None = плитка по размеру, 'full' = вся текстура, или [u0,v0,u1,v1]."""
        faces = faces or {}
        f = [frm[0] + MODEL_DX, frm[1], frm[2]]
        t = [to[0] + MODEL_DX, to[1], to[2]]
        size = (t[0] - f[0], t[1] - f[1], t[2] - f[2])
        assert all(s > 0 for s in size), (frm, to)
        el = {"from": [round(v, 4) for v in f], "to": [round(v, 4) for v in t], "faces": {}}
        for d in DIRS:
            if d in skip:
                continue
            m, tn, uv = mat, tint, None
            if d in faces:
                spec = faces[d]
                m = spec[0]
                tn = spec[1] if len(spec) > 1 else None
                uv = spec[2] if len(spec) > 2 else None
            if uv is None:
                du, dv = face_dims(d, size)
                uv = [0, 0, round(min(16, max(0.05, du * k)), 3), round(min(16, max(0.05, dv * k)), 3)]
            elif uv == "full":
                uv = [0, 0, 16, 16]
            fd = {"texture": self._tex(m), "uv": uv}
            if tn is not None:
                fd["tintindex"] = tn
            el["faces"][d] = fd
        self.elements.append(el)
        return self

    def bounds(self):
        lo = [min(e["from"][i] for e in self.elements) for i in range(3)]
        hi = [max(e["to"][i] for e in self.elements) for i in range(3)]
        return lo, hi

    def to_json(self, particle):
        lo, hi = self.bounds()
        c = [(lo[i] + hi[i]) / 2 for i in range(3)]
        size = max(hi[i] - lo[i] for i in range(3))
        disp = {}
        for ctx, (target, rot, off) in {
            "ground": (7, (0, 0, 0), (0, 2, 0)),
            "fixed": (12, (0, 0, 0), (0, 0, 0)),
            "head": (10, (0, 180, 0), (0, 13, 7)),
            "thirdperson_righthand": (7, (75, 45, 0), (0, 2.5, 0)),
            "firstperson_righthand": (8, (0, 45, 0), (0, 0, 0)),
            "firstperson_lefthand": (8, (0, 225, 0), (0, 0, 0)),
        }.items():
            s = min(4.0, target / size)
            v = rotate([s * (c[i] - 8) for i in range(3)], rot)
            tr = [round(off[i] - v[i], 3) for i in range(3)]
            tr = [max(-80, min(80, x)) for x in tr]
            disp[ctx] = {"rotation": list(rot), "translation": tr, "scale": [round(s, 4)] * 3}
        tex = dict(self.textures)
        tex["particle"] = particle
        return {"textures": tex, "elements": self.elements, "display": disp}


def rotate(v, rot):
    """Поворот как в ItemTransform: rotationXYZ → R = Rx*Ry*Rz."""
    ax, ay, az = [math.radians(a) for a in rot]
    x, y, z = v
    # Rz
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    # Ry
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    # Rx
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return [x, y, z]


# ============================ ПРОЦЕССОР ============================
def cpu(s):
    m = Model()
    sub = ["pcb_green", "pcb_green", "pcb_green", "pcb_black", "pcb_blue"][s]
    m.box((4.48, SY - .725, SZ - .725), (4.54, SY + .725, SZ + .725), sub)
    if s == 0:
        m.box((4.54, SY - .3, SZ - .3), (4.58, SY + .3, SZ + .3), "die")
    else:
        h = [0, .45, .55, .6, .65][s]
        m.box((4.54, SY - h, SZ - h), (4.62, SY + h, SZ + h), "metal", 1)
    if s == 4:
        m.box((4.62, SY + .15, SZ - .25), (4.625, SY + .45, SZ + .25), "led", 2)
    return m


# ============================ ОХЛАЖДЕНИЕ ============================
def fan_box(m, frm, to, face, tint=None):
    m.box(frm, to, "plastic", tint, faces={face: ("fan", None, "full")})


def cooler(s):
    m = Model()
    top = 4.62  # верх крышки процессора
    if s == 0:   # боксовый
        m.box((top, SY - 1.1, SZ - 1.1), (5.4, SY + 1.1, SZ + 1.1), "fins_v")
        fan_box(m, (5.4, SY - 1.1, SZ - 1.1), (5.9, SY + 1.1, SZ + 1.1), "east", 1)
        return m
    m.box((top, SY - .65, SZ - .65), (4.85, SY + .65, SZ + .65), "copper")  # медное основание
    if s == 1:   # низкопрофильный
        for dz in (-.45, -.15, .15, .45):
            m.box((4.85, SY - .5, SZ + dz - .07), (5.1, SY + .5, SZ + dz + .07), "copper")
        m.box((5.1, SY - 1.7, SZ - 1.7), (5.9, SY + 1.7, SZ + 1.7), "fins_v", 1,
              faces={"east": ("metal", 1)})
        fan_box(m, (5.9, SY - 1.7, SZ - 1.7), (6.35, SY + 1.7, SZ + 1.7), "east")
        return m
    if s == 2:   # башня
        for dy in (-.35, .35):
            for dz in (-.45, .45):
                m.box((4.85, SY + dy - .07, SZ + dz - .07), (9.75, SY + dy + .07, SZ + dz + .07), "copper")
        m.box((5.6, SY - 2.1, SZ - .9), (9.6, SY + 2.1, SZ + .9), "fins_v", 1, faces={"east": ("metal", 1)})
        fan_box(m, (5.5, SY - 2.1, SZ - 1.35), (9.7, SY + 2.1, SZ - .9), "north")
        return m
    if s == 3:   # две башни
        m.box((4.85, SY - .3, SZ - 1.66), (5.0, SY + .3, SZ + 1.66), "copper")
        for dy in (-.35, .35):
            for dz in (-1.6, -1.2, 1.2, 1.6):
                m.box((5.0, SY + dy - .06, SZ + dz - .06), (10.15, SY + dy + .06, SZ + dz + .06), "copper")
        for (z0, z1) in ((SZ - 2.3, SZ - .6), (SZ + .6, SZ + 2.3)):
            m.box((6.1, SY - 2.1, z0), (10.0, SY + 2.1, z1), "fins_v", 1, faces={"east": ("metal", 1)})
        m.box((6.0, SY - 2.1, SZ - .5), (9.9, SY + 2.1, SZ + .5), "plastic",
              faces={"north": ("fan", None, "full"), "south": ("fan", None, "full")})
        m.box((9.9, SY - 2.0, SZ - .45), (9.95, SY + 2.0, SZ + .45), "led", 2)
        return m
    # s == 4: водяное охлаждение (помпа на процессоре + радиатор под потолком)
    m.elements.clear()
    m.box((top, SY - .8, SZ - .8), (5.5, SY + .8, SZ + .8), "metal", 1)
    m.box((5.5, SY - .8, SZ - .8), (5.56, SY + .8, SZ + .8), "plastic")
    for (y0, y1, z0, z1) in ((SY + .5, SY + .62, SZ - .62, SZ + .62), (SY - .62, SY - .5, SZ - .62, SZ + .62),
                             (SY - .5, SY + .5, SZ - .62, SZ - .5), (SY - .5, SY + .5, SZ + .5, SZ + .62)):
        m.box((5.56, y0, z0), (5.58, y1, z1), "led", 2)
    for dz in (-.3, .3):
        z0, z1 = SZ + dz - .12, SZ + dz + .12
        m.box((5.1, SY + .8, z0), (5.34, SY + 1.4, z1), "plastic")       # шланг вверх из помпы
        m.box((5.1, SY + 1.16, z0), (6.9, SY + 1.4, z1), "plastic")      # к стеклу
        m.box((6.66, SY + 1.4, z0), (6.9, 13.6, z1), "plastic")          # вверх к радиатору
    for (z0, z1) in ((3.4, 7.13), (7.13, 10.87), (10.87, 14.6)):
        m.box((6.2, 13.6, z0), (10.5, 14.05, z1), "plastic", faces={"down": ("fan", None, "full")})
    m.box((6.2, 14.05, 3.6), (10.5, 14.85, 14.4), "fins_v", 1)
    m.box((6.2, 14.05, 3.2), (10.5, 14.85, 3.6), "plastic", 1)
    m.box((6.2, 14.05, 14.4), (10.5, 14.85, 14.8), "plastic", 1)
    return m


# ============================ ПАМЯТЬ ============================
def ram(s):
    m = Model()
    z0 = RAM_Z0
    pcb = "pcb_green" if s == 0 else "pcb_black"
    m.box((4.55, 9.15, z0 - .03), (5.6 if s == 0 else 5.55, 13.95, z0 + .03), pcb)
    if s == 0:
        for side in (-1, 1):
            za, zb = (z0 - .07, z0 - .03) if side < 0 else (z0 + .03, z0 + .07)
            for i in range(4):
                y = 9.4 + i * 1.15
                m.box((4.85, y, za), (5.3, y + .9, zb), "chip")
        return m
    top = {1: 5.55, 2: 5.75, 3: 5.75, 4: 5.85}[s]
    for (za, zb) in ((z0 - .09, z0 - .03), (z0 + .03, z0 + .09)):
        m.box((4.75, 9.25, za), (top, 13.85, zb), "metal", 1,
              faces={"east": ("fins_h", 1)} if s >= 2 else None)
    if s >= 2:
        m.box((5.55, 9.25, z0 - .03), (top, 13.85, z0 + .03), "metal", 1)
    if s == 3:
        m.box((5.75, 9.3, z0 - .06), (5.95, 13.8, z0 + .06), "led", 2)
    if s == 4:
        m.box((5.85, 9.3, z0 - .08), (6.08, 13.8, z0 + .08), "led", 2)
    return m


# ============================ ВИДЕОКАРТА ============================
def gpu(s):
    m = Model()
    L = [6.0, 8.6, 9.3, 10.5, 11.0][s]
    W = [8.0, 8.5, 8.8, 9.0, 9.2][s]
    yb = [7.8, 7.2, 7.0, 6.6, 6.4][s]
    z0 = 14.85 - L
    pcb = "pcb_green" if s == 0 else "pcb_black"
    m.box((4.5, PCB_Y0, z0), (W - (0 if s == 0 else .2), PCB_Y1, 14.85), pcb)
    m.box((4.45, yb - .1, 14.85), (5.45, 8.9, 14.95), "silver")          # планка-кронштейн
    if s == 0:
        m.box((5.2, 7.8, 10.2), (7.4, PCB_Y0, 13.2), "fins_v", 1)
        m.box((5.0, PCB_Y1, 11.0), (5.6, 8.55, 11.6), "chip")
        m.box((6.0, PCB_Y1, 10.4), (6.5, 8.53, 12.4), "chip")
        return m
    fans = {1: "gpu_fans_1", 2: "gpu_fans_2", 3: "gpu_fans_3", 4: "gpu_fans_3"}[s]
    m.box((4.85, yb, z0), (W, PCB_Y0, 14.85), "metal", 1,
          faces={"down": (fans, None, [0, 0, 7.5, 16])})
    if s >= 2:
        m.box((4.6, PCB_Y1, z0 + .2), (W - .2, 8.56, 14.8), "metal_dark")  # бэкплейт
    if s == 4:
        m.box((W, 8.1, z0 + .6), (W + .02, 8.3, 14.3), "led", 2)
        m.box((W, 6.9, z0 + 1.0), (W + .02, 7.5, z0 + 3.2), "led", 2)
    return m


# ============================ МАТЕРИНСКАЯ ПЛАТА ============================
def motherboard(s):
    m = Model()
    edge = "pcb_green" if s == 0 else ("plastic_light" if s == 4 else "pcb_black")
    m.box((4.3, 4.5, 6.2), (BX, 14.8, 14.9), edge,
          faces={"east": (f"minepc:item/mobo/face_s{s}", None, "full")})
    m.box((BX, SY - .9, SZ - .9), (4.48, SY + .9, SZ + .9), "silver")                 # сокет
    m.box((BX, SY - .9, SZ - 1.05), (4.53, SY + .9, SZ - .95), "silver")              # рычаг
    for i in range(2 if s <= 1 else 4):                                                # слоты памяти
        z = RAM_Z0 - RAM_PITCH * i
        m.box((BX, 9.1, z - .1), (4.65, 14.0, z + .1), "slot" if i % 2 == 0 else "slot_gray")
    if s >= 2:
        m.box((BX, 8.26, 11.38), (4.67, 8.58, 14.62), "silver")                        # PCIe в "броне"
    else:
        m.box((BX, 8.28, 11.4), (4.65, 8.56, 14.6), "slot")
    m.box((BX, 6.1, 13.0), (4.6, 6.3, 14.6), "slot")                                   # PCIe x1
    m2 = [0, 1, 2, 2, 2][s]
    for i in range(m2):
        dy = M2_PITCH * i
        m.box((BX, M2_Y0 + .05 + dy, M2_Z1), (4.55, M2_Y1 - .05 + dy, M2_Z1 + .2), "slot")
    if s == 0:                                                                          # голые дроссели
        for z in (9.6, 10.3, 11.0, 11.7):
            m.box((BX, 13.1, z), (4.7, 13.5, z + .45), "metal_dark")
        for y in (10.4, 11.1, 11.8):
            m.box((BX, y, 12.2), (4.7, y + .45, 12.6), "metal_dark")
        m.box((BX, 5.5, 8.0), (4.55, 6.3, 8.8), "chip")
    else:
        if s == 1:
            m.box((BX, 12.9, 9.4), (4.85, 13.7, 12.0), "fins_v", 1, faces={"east": ("metal", 1)})
            m.box((BX, 10.0, 12.1), (4.85, 12.4, 12.7), "fins_v", 1, faces={"east": ("metal", 1)})
        else:
            m.box((BX, 12.8, 9.2), (5.05, 14.0, 12.4), "fins_v", 1, faces={"east": ("metal", 1)})
            m.box((BX, 9.8, 12.0), (5.05, 12.6, 13.0), "fins_v", 1, faces={"east": ("metal", 1)})
        if s == 4:
            m.box((BX, 4.8, 7.0), (4.8, 6.8, 10.2), "metal", 1)
            m.box((4.8, 5.0, 7.2), (4.82, 6.6, 7.35), "led", 2)
        else:
            m.box((BX, 5.2, 7.4), (4.75, 6.6, 9.0), "metal", 1)
        if s == 3:
            m.box((4.75, 5.3, 7.45), (4.77, 6.5, 7.6), "led", 2)
    if s >= 2:                                                                          # кожух I/O
        m.box((BX, 11.0, 13.2), (5.2, 14.8, 14.9), "metal", 1)
    if s >= 3:
        m.box((5.2, 11.3, 13.4), (5.22, 14.5, 13.6), "led", 2)
    m.box((BX, 10.4, 6.25), (4.95, 12.4, 6.55), "plastic_light" if s == 4 else "plastic")   # 24-pin
    m.box((BX, 14.2, 12.5), (4.85, 14.6, 13.15), "plastic")                                  # 8-pin
    m.box((BX, 5.0, 6.25), (4.75, 6.0, 6.5), "plastic")                                      # SATA
    return m


# ============================ БЛОК ПИТАНИЯ ============================
def psu(s):
    m = Model()
    faces = {"down": ("fan", None, "full")}
    if s == 2:
        faces["east"] = ("mesh", None)
    if s >= 3:
        faces["north"] = ("connectors", None, "full")
    m.box((4.3, 1.0, 10.0), (9.6, 4.0, 14.95), "metal", 1, faces=faces)
    if s >= 1:
        m.box((9.6, 2.0, 11.2), (9.62, 3.2, 13.8), "label", 2)
    if s == 4:
        m.box((9.6, 3.7, 10.2), (9.62, 3.85, 14.75), "led", 2)
        m.box((9.6, 1.15, 10.2), (9.62, 1.3, 14.75), "led", 2)
    return m


# ============================ ЖЁСТКИЙ ДИСК ============================
def hdd(s):
    m = Model()
    if s == 0:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.6, 1.15, 6.95, 1.95, 4.75
    else:
        x0, y0, z0, x1, y1, z1 = 4.45, 1.4, 1.15, 8.05, 2.3, 6.4
    m.box((x0, y0, z0), (x1, y1, z1), "hdd_body",
          faces={"up": ("hdd_top", 1, "full"), "down": ("pcb_green", None)})
    if s == 3:
        for i in range(3):
            m.box((x0 + .6 + i * 1.0, y1, z0 + 2.4), (x0 + 1.0 + i * 1.0, y1 + .1, z1 - .4), "metal", 1)
        m.box((x0 + .5, y1, z0 + .5), (x1 - .5, y1 + .02, z0 + 2.0), "label", 2)
    else:
        m.box((x0 + .5, y1, z0 + .8), (x1 - .5, y1 + .02, z1 - .9), "label", 2)
    if s == 4:
        m.box((x1, y0 + .35, z0 + .15), (x1 + .02, y0 + .55, z1 - .15), "led", 2)
    m.box((x0 + .4, y0 + .1, z1), (x0 + 1.6, y1 - .1, z1 + .08), "plastic")              # разъём SATA
    return m


# ============================ SSD ============================
def ssd(s):
    m = Model()
    if s <= 1:   # 2.5" SATA на поддоне перед платой
        m.box((4.05, 9.4, 1.4), (4.3, 11.9, 5.0), "plastic" if s == 0 else "metal", 1)
        if s == 0:
            m.box((4.3, 9.8, 2.0), (4.31, 11.5, 4.4), "label", 2)
        else:
            m.box((4.3, 10.45, 1.4), (4.31, 10.75, 5.0), "led", 2)
        return m
    # NVMe в слоте M.2
    m.box((BX, M2_Y0, M2_Z0), (4.5, M2_Y1, M2_Z1), "pcb_black")
    if s == 2:
        m.box((4.5, 9.8, 9.5), (4.54, 10.4, 10.3), "chip")
        m.box((4.5, 9.8, 10.5), (4.54, 10.4, 11.3), "chip")
        m.box((4.5, 9.85, 11.45), (4.54, 10.35, 11.9), "chip")
        m.box((4.54, 9.78, 9.4), (4.545, 10.42, 11.0), "label", 2)
    elif s == 3:
        m.box((4.5, 9.72, 9.25), (4.56, 10.48, 12.0), "metal", 1)
        m.box((4.56, 9.85, 9.6), (4.565, 10.35, 11.2), "label", 2)
    else:
        m.box((4.5, M2_Y0, M2_Z0), (4.8, M2_Y1, M2_Z1), "fins_v", 1, faces={"east": ("metal", 1)})
        m.box((4.8, 10.0, 9.3), (4.82, 10.2, 12.0), "led", 2)
    return m


BUILDERS = {"cpu": cpu, "cooler": cooler, "motherboard": motherboard, "ram": ram, "gpu": gpu,
            "psu": psu, "hdd": hdd, "ssd": ssd}
