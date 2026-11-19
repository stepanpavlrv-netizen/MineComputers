# Камеры в Wired: камера с паролем — в списке «закрыта паролем», при открытии — окошко пароля,
# неверный пароль — ошибка, верный — открывается и запоминается в /home/.cameras; второй запуск — без вопросов.
import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps"); os.makedirs(root + "/rom/openos")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(REPO + "/disks/cameras/app", root + "/apps/cameras")
shutil.copy(REPO + "/rom/openos/camkeys.lua", root + "/rom/openos/camkeys.lua")
fail = []
def check(cond, what):
    print(("OK   " if cond else "FAIL ") + what)
    if not cond: fail.append(what)

s = Sim(root, w=160, h=50); s.start("/wired/boot.lua"); s.run()
p = s.find("Камеры"); s.dbl(p[0], p[1] - 2); s.run(advance=0.5)
s.screen.png("cam_pw0.png")
check(s.find("закрыта паролем") is not None, "в списке «склад» — закрыта паролем")
p = s.find("склад"); s.dbl(p[0], p[1]); s.run(advance=0.3)
s.screen.png("cam_pw1.png")
check(s.find("Камера закрыта паролем.") is not None, "окошко пароля открылось")
s.type("1111"); s.key("enter"); s.run(advance=0.3)
s.screen.png("cam_pw2.png")
check(s.find("Неверный пароль") is not None, "неверный пароль — ошибка в окошке")
s.type("9999"); s.key("enter"); s.run(advance=0.5)
s.screen.png("cam_pw3.png")
check(s.find("Камера закрыта паролем.") is None, "верный пароль — окошко закрылось")
check(s.find("← Все камеры") is not None, "камера открылась")
saved = open(root + "/home/.cameras", encoding="utf-8").read() if os.path.exists(root + "/home/.cameras") else ""
check("a1f07c22\t9999" in saved, "пароль запомнен: " + repr(saved))
check(not s.flags.get("errors"), "без ошибок Lua: " + str(s.flags.get("errors")))

# второй запуск (новый компьютер с тем же диском): сохранённый пароль входит сам
s2 = Sim(root, w=160, h=50); s2.start("/wired/boot.lua"); s2.run()
p = s2.find("Камеры"); s2.dbl(p[0], p[1] - 2); s2.run(advance=0.5)
s2.screen.png("cam_pw4.png")
check(s2.find("закрыта паролем") is None and s2.find("пароль ✓") is not None, "второй запуск: вошли сохранённым паролем")
print("ВСЁ ХОРОШО" if not fail else "ОШИБКИ: " + ", ".join(fail))
