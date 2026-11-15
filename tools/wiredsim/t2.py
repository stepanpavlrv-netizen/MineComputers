import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
root = os.path.dirname(os.path.abspath(__file__)) + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
open(root + "/home/заметки.txt", "w").write("привет")
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
s.dbl(10, 4)        # Проводник
s.screen.png("d2.png")
s.click(3, 49)      # Пуск
s.screen.png("d3.png")
s.click(80, 20)     # закрыть меню
s.dbl(10, 12)       # Терминал
s.type("ls /"); s.key("enter")
s.screen.png("d4.png")
print(s.status, s.flags.get("errors"))
