import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
for d in ("mail", "office", "cameras"):
    shutil.copytree(REPO + "/disks/%s/app" % d, root + "/apps/" + d)
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
# Документы
p = s.find("Документы"); s.dbl(p[0], p[1] - 2)
for k in ["backspace"] * 20: s.key(k)
s.type("Отчёт за неделю"); s.key("enter")
s.type("Склад пополнили, шахта работает в две смены. Камеры на воротах включены, ночью — ночное видение."); s.key("enter")
p = s.find("• Список"); s.click(p[0], p[1]); s.type("провели провод"); s.key("enter"); s.type("запустили сайт")
# выделить слово «шахта» и покрасить
s.screen.png("o0.png"); p = s.find("шахта"); print("шахта", p); s.dbl(p[0] + 1, p[1])
p = s.find("▌выделить"); s.click(p[0] + 1, p[1])
s.key("s", True)
s.type("/home/отчёт"); s.key("enter")
s.screen.png("o1.png")
s.key("f4", True)
# Таблицы
p = s.find("Таблицы"); s.dbl(p[0], p[1] - 2)
for t in ["Ресурс", "Железо", "Медь", "Алмазы"]:
    s.type(t); s.key("enter")
s.key("up"); s.key("up"); s.key("up"); s.key("up"); s.key("right")
for t in ["Пн", "128", "64", "3", "=СУММ(B2:B4)"]:
    s.type(t); s.key("enter")
s.key("up"); s.key("up"); s.key("up"); s.key("up")
s.push("key", "down", False, True); s.push("key", "down", False, True); s.run()
p = s.find(" ▤ "); s.click(p[0] + 1, p[1])
s.screen.png("o2.png")
print(sorted(os.listdir(root + "/home")), s.flags.get("errors"))
