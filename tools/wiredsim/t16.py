import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps"); os.makedirs(root + "/www")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(REPO + "/disks/browser/app", root + "/apps/browser")
open(root + "/www/index.page", "w").write("# Главная\n[Вторая страница](/two)\n")
open(root + "/www/two.page", "w").write("# Это вторая\n[Назад](index.page)\n")
s = Sim(root); s.start("/wired/boot.lua"); s.run()
L = s.L.globals()
L.wired.apps.launch(L.wired.apps.get("browser"), "/www/index.page"); s.push("x"); s.run(advance=1)
p = s.find("Вторая страница"); print("link", p); s.click(p[0] + 1, p[1]); s.run(advance=1)
print("page2", s.find("Это вторая"))
p = s.find("Назад"); s.click(p[0] + 1, p[1]); s.run(advance=1)
print("back", s.find("Главная"), s.flags.get("errors"))
s.screen.png("pv.png")
