import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
s = Sim(root, disk=here + "/disk1", users={"admin": ("pw", True)})
s.start("/wired/boot.lua"); s.run()
s.screen.png("e1.png")      # вход
s.type("admin"); s.key("enter"); s.type("pw"); s.key("enter")
s.push("disk", "/disk"); s.run()
s.screen.png("e2.png")      # уведомление
p = s.find("Установить"); print("btn", p)
s.click(p[0] + 1, p[1])
s.screen.png("e3.png")      # установщик
p = s.find(" Установить "); print("btn2", p)
s.click(p[0] + 2, p[1]); s.run(advance=2)
s.screen.png("e4.png")
p = s.find(" Открыть "); print("open", p)
if p: s.click(p[0] + 2, p[1])
s.screen.png("e5.png")
print(os.listdir(root + "/apps"), s.flags.get("errors"))
