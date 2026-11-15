import os, shutil, sys
sys.path.insert(0, os.path.dirname(__file__))
from sim import Sim, REPO
root = "/tmp/claude-0/-home-claude/ba0f59d2-00a8-51c4-9252-ddf963d761ee/scratchpad/wiredsim/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
open(root + "/home/заметки.txt", "w").write("привет")
s = Sim(root)
s.start("/wired/boot.lua")
s.run()
s.screen.png("d1.png")
print(s.status, s.flags)
