import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
for d in ("cameras", "browser", "office", "mail", "site"): shutil.copytree(REPO + "/disks/%s/app" % d, root + "/apps/" + d)
for (w, h, n) in ((80, 25, "small"), (160, 50, "big")):
    s = Sim(root, w=w, h=h); s.start("/wired/boot.lua"); s.run()
    p = s.find("◆ WIRED"); s.click(p[0] + 1, p[1]); s.run()
    s.screen.png("menu_%s.png" % n)
    p = s.find("Часы"); print(n, "Часы at", p)
    if p: s.click(p[0] + 1, p[1]); s.run(advance=0.3)
    print(n, "clock window:", s.find("Будильник") is not None, s.flags.get("errors"))
