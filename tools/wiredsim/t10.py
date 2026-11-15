import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
for d in ("cameras",):
    shutil.copytree(REPO + "/disks/%s/app" % d, root + "/apps/" + d)
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
p = s.find("Камеры"); s.dbl(p[0], p[1] - 2)
s.run(advance=3)
s.screen.png("c1.png")
p = s.find(" Сеткой "); s.click(p[0] + 2, p[1]); s.run(advance=1)
s.screen.png("c2.png")
p = s.find(" 1 "); s.click(p[0] + 1, p[1]); s.run(advance=1)
p = s.find(" ▶ "); s.click(p[0] + 1, p[1]); s.run(advance=1)
s.screen.png("c3.png")
print(s.flags.get("errors"))
