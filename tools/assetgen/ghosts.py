"""Силуэты деталей для пустых слотов окна корпуса (как силуэты брони в инвентаре Minecraft).

Белая маска 16×16: в игре красится цветом темы и рисуется полупрозрачно.
Запуск: python3 tools/assetgen/ghosts.py  (из корня проекта)
"""
import json, os, sys
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import icons2d

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
OUT = os.path.join(ROOT, "src/main/resources/assets/minepc/textures/gui/ghost")
PRESETS = json.load(open(os.path.join(os.path.dirname(__file__), "spec_dump.json")))["presets"]

def preset(t, tier):
    for p in PRESETS:
        if p["type"] == t and p["tier"] == tier and not p["name"].endswith("*"):
            return p
    raise KeyError((t, tier))

def mask(img):
    """Контур и внутренние границы цветов — плотно, заливка — бледно (как рисунок линиями)."""
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    a, b = img.load(), out.load()
    def lum(c):
        return c[0] * .3 + c[1] * .59 + c[2] * .11
    for x in range(16):
        for y in range(16):
            c = a[x, y]
            if c[3] == 0:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 16 and 0 <= ny < 16) or a[nx, ny][3] == 0:
                    edge = True
                elif lum(a[nx, ny]) - lum(c) > 28:      # тёмная сторона границы
                    edge = True
            b[x, y] = (255, 255, 255, 255 if edge else 96)
    return out

JOBS = {
    "motherboard": ("MOTHERBOARD", 3), "cpu": ("CPU", 3), "cooler": ("COOLER", 3), "ram": ("RAM", 3),
    "gpu": ("GPU", 3), "psu": ("PSU", 3), "hdd": ("HDD", 2), "sata": ("SSD", 1), "m2": ("SSD", 3),
}
os.makedirs(OUT, exist_ok=True)
for name, (t, tier) in JOBS.items():
    mask(icons2d.make(preset(t, tier))).save(os.path.join(OUT, name + ".png"))
flash = Image.open(os.path.join(ROOT, "src/main/resources/assets/minepc/textures/item/flash_drive.png")).convert("RGBA")
mask(flash).save(os.path.join(OUT, "usb.png"))
print("ok", len(JOBS) + 1)
