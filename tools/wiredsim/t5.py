import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
s = Sim(root, w=320, h=100)
s.start("/wired/boot.lua"); s.run()
p = s.find("Параметры"); s.dbl(p[0], p[1] - 2)
p = s.find(" 200 % "); s.click(p[0] + 1, p[1])
print(len(s.screen.ch), s.screen.w)
for y in (47, 48, 49): print(y, "".join(s.screen.ch[y])[:80])
print(s.L.eval("wired.wm.desktopHeight()"), s.L.eval("wired.gfx.H"), s.L.eval("wired.gfx.back"))
b = s.buffers[s.L.eval("wired.gfx.back")]
print(b.w, b.h, "".join(b.ch[48])[:60])
