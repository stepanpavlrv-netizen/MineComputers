import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
open(root + "/wired/alarms.cfg", "w").write('{ { time = "12:04", label = "обед", on = true } }')
s = Sim(root); s.start("/wired/boot.lua"); s.run(advance=3)
print("beeps", len(s.flags.get("beeps", [])), s.find("Будильник 12:04"), s.find("обед"))
s.screen.png("alarm.png")
print(s.flags.get("errors"))
