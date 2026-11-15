import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home/Документы"); os.makedirs(root + "/apps")
open(root + "/home/Документы/a.txt", "w").write("hello")
usb = here + "/usb"; shutil.rmtree(usb, ignore_errors=True); os.makedirs(usb)
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
s = Sim(root); s.mounts["usb"] = usb
s.start("/wired/boot.lua"); s.run()
p = s.find("◆ WIRED"); s.click(p[0] + 1, p[1]); s.run()
s.screen.png("m_menu.png")
p = s.find("Игры"); print("menu Игры at", p); s.click(p[0] + 1, p[1]); s.run(advance=0.3)
p = s.find(" Играть "); s.click(p[0] + 2, p[1]); s.run(advance=0.3)
p = s.find("Сапёр · Новичок"); print("side", p); s.click(p[0] - 18, p[1] + 4); s.run(advance=0.3)
s.screen.png("m_games.png")
s.key("f4", True)
# Архив
L = s.L.globals()
L.wired.apps.launch(L.wired.apps.get("archive")); s.push("x"); s.run(advance=0.3)
p = s.find(" Сохранить копию "); s.click(p[0] + 2, p[1]); s.run(advance=2)
s.screen.png("m_archive.png")
for dp, dn, fn in os.walk(usb): print(dp, fn)
s.key("f4", True)
# Рисовалка: линия
L.wired.apps.launch(L.wired.apps.get("paint")); s.push("x"); s.run(advance=0.3)
p = s.find("Превью")
s.push("mouse_click", 1, 40, 20); s.push("mouse_drag", 1, 60, 25); s.push("mouse_drag", 1, 80, 30); s.push("mouse_up", 1, 80, 30); s.run()
s.key("s", True); s.run()
p = s.find(" OK "); s.click(p[0] + 1, p[1]); s.run(advance=0.5)
s.screen.png("m_paint.png")
print(os.listdir(root + "/home"), os.path.exists(root + "/home/Картинки/картинка.pix"))
# Часы: будильник на текущее время
print("errors", s.flags.get("errors"))
