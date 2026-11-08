"""Текстуры корпуса и материнской платы.

Корпус смотрит лицом на север (z=0). Координаты текстур на гранях считаются
автоматически из размеров элементов модели:
  север (перед):  u = 16 - x, v = 16 - y
  юг (зад):       u = x,      v = 16 - y
  восток (стекло, плата видна через него): u = 16 - z, v = 16 - y
  верх:           u = x,      v = z
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image
from px import Canvas, noise, fan, hx

out = sys.argv[1]
preview_path = sys.argv[2] if len(sys.argv) > 2 else None
os.makedirs(out, exist_ok=True)

BODY, BODY_HI, BODY_LO = "2b2d33", "3b3e46", "1c1d21"

# ---- боковая стенка (глухая) ----
c = Canvas()
noise(c, 0, 0, 15, 15, BODY, 3, seed=11)
c.hline(0, 15, 0, BODY_HI); c.vline(0, 0, 15, BODY_HI)
c.hline(0, 15, 15, BODY_LO); c.vline(15, 0, 15, BODY_LO)
for x in range(4, 12, 2):                   # вентиляционная прорезь
    c.vline(x, 11, 13, "17181b")
c.save(f"{out}/case_side.png")

# ---- рёбра / рамка ----
c = Canvas()
noise(c, 0, 0, 15, 15, "25272c", 2, seed=12)
c.hline(0, 15, 0, "34373e")
c.save(f"{out}/case_edge.png")

# ---- внутренняя поверхность ----
c = Canvas()
noise(c, 0, 0, 15, 15, "1b1c20", 2, seed=13)
c.save(f"{out}/case_interior.png")

# ---- передняя панель (видимая область u 3..12, v 1..15) ----
c = Canvas()
noise(c, 0, 0, 15, 15, "212328", 2, seed=14)
for u in range(4, 12):                      # сетка-мэш
    for v in range(5, 15):
        if (u + v) % 2 == 0:
            c.px(u, v, "141518")
c.rect(4, 2, 11, 3, "2b2e34")               # верхняя планка
c.px(10, 2, "9aa0a6"); c.px(10, 3, "6c7178")   # кнопка питания
c.px(9, 2, "40c8ff")                        # индикатор
c.rect(5, 2, 5, 3, "0d0e10"); c.rect(7, 2, 7, 3, "0d0e10")  # USB
c.px(5, 2, "3b6fd0"); c.px(7, 2, "3b6fd0")
c.vline(3, 1, 15, "34373e"); c.vline(12, 1, 15, BODY_LO)
c.save(f"{out}/case_front.png")

# ---- задняя панель (видимая область u 4..12, v 1..15) ----
c = Canvas()
noise(c, 0, 0, 15, 15, "26282d", 2, seed=15)
c.rect(5, 2, 6, 6, "8f949b")                # заглушка I/O
c.px(5, 3, "2a6fd6"); c.px(6, 3, "2a6fd6"); c.px(5, 5, "d6d6d6"); c.px(6, 4, "e0b040")
fan(c, 10, 4, 2.3, frame="34373e", blade="5a5d64", hub="7a7c84", dark="15161a")
for v in (8, 9, 10):                        # заглушки слотов расширения
    c.hline(5, 11, v, "4a4d55" if v % 2 else "3a3d44")
c.rect(4, 12, 12, 14, "1a1b1e")             # блок питания
fan(c, 9.5, 13.5, 1.3, frame="2a2b30", blade="44464c", hub="5a5c62", dark="111214")
c.rect(5, 12, 6, 13, "0d0e10"); c.px(5, 13, "6c7178")
c.save(f"{out}/case_back.png")

# ---- верх ----
c = Canvas()
noise(c, 0, 0, 15, 15, BODY, 3, seed=16)
for u in range(5, 12):
    for v in range(3, 13):
        if (u + v) % 2 == 0:
            c.px(u, v, "15161a")
c.save(f"{out}/case_top.png")

# ---- кожух блока питания ----
c = Canvas()
noise(c, 0, 0, 15, 15, "24262b", 2, seed=17)
c.hline(0, 15, 13, "34373e")
for u in range(5, 12, 2):
    c.px(u, 14, "141518")
c.save(f"{out}/case_shroud.png")

# ---- стекло (полупрозрачное) ----
c = Canvas()
for u in range(16):
    for v in range(16):
        c.px(u, v, (150, 175, 195, 40))
for k in range(-4, 6):                      # блик
    u, v = 3 + k, 12 - k
    for d in (0, 1):
        if 0 <= u + d < 16 and 0 <= v < 16:
            c.px(u + d, v, (225, 240, 255, 85))
for (u, v) in [(1, 1), (14, 1), (1, 14), (14, 14)]:   # винты-барашки
    c.px(u, v, (150, 155, 162, 255))
c.save(f"{out}/case_glass.png")

# ---- материнская плата (u = 16 - z, v = 16 - y; задняя сторона слева) ----
c = Canvas()
noise(c, 0, 0, 15, 15, "1d2422", 3, seed=18)
for (u, v) in [(3, 8), (8, 7), (13, 2), (12, 13), (2, 12), (9, 3)]:   # монтажные точки
    c.px(u, v, "56605b")
for u in range(4, 13):                      # дорожки
    if u % 3 == 0:
        c.px(u, 7, "2c3632")
c.rect(1, 1, 3, 6, "3a3d44"); c.hline(1, 3, 1, "565a62")     # кожух I/O
c.rect(4, 2, 8, 2, "5a5f68"); c.vline(4, 3, 6, "5a5f68")      # радиаторы VRM
c.px(5, 2, "777c85"); c.px(4, 3, "777c85")
c.rect(5, 4, 7, 6, "9aa0a6"); c.px(6, 5, "c9a243")           # сокет процессора
c.hline(5, 7, 4, "c3c8cd")
for u in (9, 10, 11, 12):                                     # 4 слота памяти (чередуются цвета)
    c.vline(u, 3, 8, "0f1012" if u % 2 else "3a3d44")
    c.px(u, 3, "8f949b"); c.px(u, 8, "8f949b")
c.rect(14, 4, 14, 7, "e4e4e4"); c.rect(13, 4, 13, 7, "b8b8b8")  # 24-pin питание
for v in (9, 11):                                             # слоты PCIe
    c.hline(3, 11, v, "0f1012"); c.px(3, v, "9aa0a6")
c.rect(4, 10, 8, 10, "2f3a35")                                # слот M.2
c.rect(10, 12, 12, 13, "5a5f68"); c.hline(10, 12, 12, "777c85")  # радиатор чипсета
c.px(6, 13, "c0c0c0"); c.px(7, 13, "8a8a8a")                  # батарейка CMOS
c.save(f"{out}/motherboard.png")

# превью развёрток (если указан второй аргумент)
if preview_path is None:
    sys.exit(0)
names = ["case_front", "case_back", "case_side", "case_top", "motherboard", "case_glass", "case_shroud", "case_interior"]
S, G = 10, 10
prev = Image.new("RGBA", (len(names) * (16 * S + G) + G, 16 * S + 2 * G), (70, 74, 80, 255))
for i, n in enumerate(names):
    im = Image.open(f"{out}/{n}.png").convert("RGBA").resize((16 * S, 16 * S), Image.NEAREST)
    prev.paste(im, (G + i * (16 * S + G), G), im)
prev.save(preview_path)
print("ok")
