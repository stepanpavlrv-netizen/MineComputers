import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(REPO + "/disks/site/app", root + "/apps/site")
s = Sim(root); s.start("/wired/boot.lua"); s.run()
p = s.find("Сайтостроитель"); s.dbl(p[0], p[1] - 2); s.run(advance=0.5)
p = s.find("На хостинг"); print("btn", p); s.click(p[0] + 1, p[1]); s.run(advance=2)
s.screen.png("host1.png")
print(s.flags.get("errors"))
