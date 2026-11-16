# Браузер: стартовая страница со списком сайтов майннета; не отвечающий сайт — серым, его можно убрать,
# а когда снова ответит — он возвращается в список.
import os, shutil, sys
sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps"); os.makedirs(root + "/www")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
shutil.copytree(REPO + "/disks/browser/app", root + "/apps/browser")
open(root + "/www/index.page", "w", encoding="utf-8").write("# Магазин Васи\nТовары\n")
s = Sim(root); s.start("/wired/boot.lua"); s.run()
L = s.L.globals()
names = ["shop.mine", "news.mine", "dead.mine"]
alive = {"dead.mine": False}
L.net.dns = lambda *a: (True, s.L.table_from({i + 1: s.L.table_from({"name": n, "address": "x"}) for i, n in enumerate(names)}))
orig_send = L.net.send
L.net.send = lambda host, *rest: True if (str(host) in alive and not alive[str(host)]) else orig_send(host, *rest)

fails = []
def check(what, ok):
    print(("OK  " if ok else "FAIL") + " " + what)
    if not ok: fails.append(what)

L.wired.apps.launch(L.wired.apps.get("browser")); s.push("x"); s.run(advance=4)
s.screen.png(here + "/home_sites.png")
check("заголовок «Сайты Майннета»", s.find("Сайты Майннета") is not None)
check("работающий сайт с заголовком страницы", s.find("shop.mine") is not None and s.find("Магазин Васи") is not None)
check("раздел «Не отвечают» с dead.mine", s.find("Не отвечают") is not None and s.find("dead.mine") is not None)
check("счётчик: видно 3, работают 2", s.find("видно в сети: 3, работают: 2") is not None)

p = s.find("✕ убрать"); check("у не отвечающего — «✕ убрать»", p is not None)
if p: s.click(p[0] + 1, p[1]); s.run()
check("после «убрать» dead.mine не видно", s.find("dead.mine") is None)
check("убранный записан в /home/.browser_hidden", "dead.mine" in open(root + "/home/.browser_hidden", encoding="utf-8").read())

alive["dead.mine"] = True
p = s.find("↻ обновить"); s.click(p[0] + 1, p[1]); s.run(advance=4)
check("снова отвечает — вернулся в список", s.find("dead.mine") is not None and s.find("Не отвечают") is None)
check("и убран из скрытых", "dead.mine" not in open(root + "/home/.browser_hidden", encoding="utf-8").read())

p = s.find("найти сайт"); s.click(p[0] + 1, p[1]); s.type("news")
check("поиск: остался только news.mine", s.find("● news.mine") is not None and s.find("● shop.mine") is None)
s.screen.png(here + "/home_search.png")
s.key("escape")
p = s.find("● shop.mine"); s.click(p[0] + 3, p[1]); s.run(advance=1)
check("клик по сайту открывает его", s.find("Товары") is not None)
s.screen.png(here + "/home_opened.png")
check("без ошибок Lua", not s.flags.get("errors"))
print("ОШИБОК:", len(fails))
sys.exit(1 if fails else 0)
