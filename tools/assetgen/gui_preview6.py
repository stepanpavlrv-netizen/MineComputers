import sys, os, json
sys.path.insert(0, os.path.dirname(__file__))
import importlib.util
spec = importlib.util.spec_from_file_location("gp", os.path.join(os.path.dirname(__file__), "gui_preview.py"))
src = open(spec.origin).read()
src = src[:src.index("types = list(STOCK)")]           # берём только функции рендера
ns = {"__file__": spec.origin}
args = list(sys.argv)
sys.argv = [args[0], args[1], args[2], "72"]
exec(compile(src, "gp", "exec"), ns)
sys.argv = args
from PIL import Image
root, out, dump = sys.argv[1], sys.argv[2], sys.argv[3]
S = 72
A = f"{root}/src/main/resources/assets/minepc"
NAMES = {"MOTHERBOARD": "motherboard", "CPU": "cpu", "COOLER": "cooler", "RAM": "ram", "GPU": "gpu", "PSU": "psu", "HDD": "hdd", "SSD": "ssd"}
d = json.load(open(dump))
rows = {}
for p in d["presets"]:
    rows.setdefault(p["type"], []).append(p)
G = 8
order = ["MOTHERBOARD", "CPU", "COOLER", "RAM", "GPU", "PSU", "HDD", "SSD"]
cols = max(len(rows[t]) for t in order)
sheet = Image.new("RGBA", (cols * (S + G) + G, len(order) * (S + G) + G), (139, 139, 139, 255))
for r, t in enumerate(order):
    for c, p in enumerate(rows[t]):
        v = p["vis"]
        model = json.load(open(f"{A}/models/item/{NAMES[t]}/show_{v[0]}_{v[1]}_{v[2]}.json"))
        tints = p["tints"]
        # render_icon ждёт (цвет, акцент); добавим слой 3 — текстолит
        icon = ns["render_icon3"](model, tints, S) if "render_icon3" in ns else None
        sheet.alpha_composite(icon, (G + c * (S + G), G + r * (S + G)))
sheet.save(out)
print("ok")
