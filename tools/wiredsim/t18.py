import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(REPO + "/disks/mail/app", root + "/apps/mail")
open(root + "/home/.mailhost", "w").write("srv|vasya|abc|info@shop.mine\n")
s = Sim(root); s.start("/wired/boot.lua"); s.run()
p = s.find("Почта"); s.dbl(p[0], p[1] - 2); s.run(advance=0.5)
p = s.find("info@shop.mine"); print("remote folder", p); s.click(p[0], p[1]); s.run(advance=6)
s.screen.png("mail_r.png")
p = s.find("Ящик на хостинге"); s.click(p[0] + 1, p[1]); s.run(advance=0.5)
s.screen.png("mail_r2.png")
print(s.flags.get("errors"))
