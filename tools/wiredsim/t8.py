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
os.makedirs(root + "/mail/all")
open(root + "/mail/all/1763-0001.txt", "w").write("От: steve@farm\nТема: Привет\nВремя: 15.11 12:01\nНовое: да\n\nКак дела? Пришли железа.")
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
s.screen.png("m0.png")
p = s.find("Почта"); s.dbl(p[0], p[1] - 2)
p = s.find("Привет"); 
if p: s.click(p[0], p[1])
s.screen.png("m1.png")
p = s.find("Написать"); s.click(p[0], p[1])
s.type("alex@farm.mine"); s.key("enter"); s.type("Заказ"); s.key("enter"); s.type("Нужно 64 железа")
s.screen.png("m2.png")
p = s.find(" Отправить "); s.click(p[0] + 2, p[1]); s.run(advance=1)
s.screen.png("m3.png")
print(os.listdir(root + "/mail"), s.flags.get("errors"))
