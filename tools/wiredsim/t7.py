import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root)
disk = here + "/wdisk"
shutil.rmtree(disk, ignore_errors=True)
shutil.copytree(REPO + "/disks/wired", disk)
s = Sim(root, disk=disk)
s.start("/disk/startup.lua"); s.run()
s.screen.png("i1.png")
s.key("enter")
s.type("admin"); s.key("enter"); s.type("pw"); s.key("enter"); s.type("pw"); s.key("enter")
s.screen.png("i2.png")
s.run(advance=4)
s.screen.png("i3.png")
print(s.status, s.flags, os.listdir(root), open(root + "/startup.lua").read()[:80], s.users)
