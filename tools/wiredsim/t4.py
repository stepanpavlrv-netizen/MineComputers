import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(here + "/disk1/app", root + "/apps/Привет")
s = Sim(root, w=320, h=100)
s.calls = 0
s.start("/wired/boot.lua"); s.run()
print("boot calls", s.calls)
s.screen.png("f1.png", cw=4, chh=8)
p = s.find("Параметры"); s.dbl(p[0], p[1] - 2)
s.calls = 0
p = s.find(" 200 % "); print("200 at", p)
s.click(p[0] + 1, p[1])
print("rescale calls", s.calls, s.screen.w, s.screen.h)
s.screen.png("f2.png", cw=8, chh=16)
# перетащить окно
win = s.find("Параметры —") or s.find("Параметры")
s.calls = 0
s.push("mouse_click", 1, win[0] + 5, win[1]); s.push("mouse_drag", 1, win[0] + 15, win[1] + 3); s.push("mouse_drag", 1, win[0] + 25, win[1] + 6); s.push("mouse_up", 1, win[0]+25, win[1]+6); s.run()
print("drag calls", s.calls)
p = s.find("Оформление"); s.click(p[0], p[1])
p = s.find("Светлая"); s.click(p[0], p[1])
s.screen.png("f3.png", cw=8, chh=16)
p = s.find("100 %") 
print(s.flags.get("errors"))
