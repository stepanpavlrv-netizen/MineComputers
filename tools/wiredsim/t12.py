import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__))
root = here + "/root"
shutil.rmtree(root, ignore_errors=True)
os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
s = Sim(root)
s.start("/wired/boot.lua"); s.run()
L = s.L
def launch(i, arg=None):
    L.globals().wired.apps.launch(L.globals().wired.apps.get(i), arg); s.push("sim_redraw")
    s.run(advance=0.5)
def shot(n): s.screen.png("n_%s.png" % n)
which = sys.argv[1:] or ["calc", "clock", "archive", "chat", "network", "pult", "paint", "music", "games"]
for a in which:
    launch(a)
    if a == "calc":
        s.type("12*(3+4)"); s.key("enter"); s.type("/7")
    if a == "chat":
        s.type("привет всем"); s.key("enter")
        s.push("net_message", "bbbbbbbb-2222", 4001, 1, "склад", "здорово!"); s.run()
        print("broadcast", s.flags.get("broadcast"))
    if a == "network":
        shot("network1")
        p = s.find(" Компьютеры "); s.click(p[0] + 1, p[1]); s.run(advance=0.3)
        p = s.find(" Пинг "); s.click(p[0] + 1, p[1]); s.run(advance=0.3)
        shot(a + "2")
        p = s.find(" Коммутаторы "); s.click(p[0] + 1, p[1]); s.run(advance=0.3)
        shot(a + "3")
        p = s.find(" Wi-Fi "); s.click(p[0] + 1, p[1]); p = s.find(" Найти сети "); s.click(p[0] + 1, p[1])
    if a == "pult":
        s.run(advance=1.5)
        p = s.find(" Импульс ")
    if a == "paint":
        p = s.find("Превью")
        # нарисовать линию
        x0, y0 = None, None
        for y, row in enumerate(s.screen.ch):
            pass
    if a == "music":
        p = s.find(" ▶ Играть "); s.click(p[0] + 2, p[1]); s.run(advance=2)
    if a == "games":
        shot(a + "0")
        p = s.find(" Играть "); s.click(p[0] + 2, p[1]); s.run(advance=0.2)
        s.click(40, 12); s.run(advance=0.2)
    if a == "archive":
        pass
    s.run(advance=0.5)
    shot(a)
    s.key("f4", True)
    s.run(advance=0.3)
print("errors", s.flags.get("errors"))
