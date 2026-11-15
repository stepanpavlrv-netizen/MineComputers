import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
for d in ("browser", "site"):
    shutil.copytree(REPO + "/disks/%s/app" % d, root + "/apps/" + d)
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
p = s.find("Сайтостроитель"); s.dbl(p[0], p[1] - 2)
s.screen.png("s1.png")
p = s.find("Поле и кнопка"); s.click(p[0], p[1])
p = s.find("Мой сайт"); s.click(p[0], p[1])
p = s.find("████"); 
s.screen.png("s2.png")
p = s.find(" Опубликовать "); s.click(p[0] + 2, p[1])
s.key("f4", True)
p = s.find("Браузер"); s.dbl(p[0], p[1] - 2)
s.type("shop.mine"); s.key("enter"); s.run()
s.screen.png("s3.png")
p = s.find("О нас»"); 
if p: s.click(p[0], p[1]); s.run()
s.screen.png("s4.png")
print(os.listdir(root + "/www"), s.flags.get("errors"))
