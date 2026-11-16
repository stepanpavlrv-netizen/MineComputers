# Хостинг: срок работы и приостановка (окно «Хостинг», диалоги, состояние, заглушка, напоминание)
import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
for site in ("shop.mine", "blog.mine", "lavka.mine"):
    os.makedirs(root + "/sites/" + site); open(root + "/sites/" + site + "/index.page", "w").write("# " + site + "\n")
s = Sim(root); s.start("/wired/boot.lua"); s.run()
ok = True
def check(cond, what):
    global ok
    print(("OK   " if cond else "FAIL ") + what)
    ok = ok and bool(cond)
def lua(code): return s.L.execute(code)
lua("""
local H = require('hostadm')
H.add('vasya', 'pw', 512) H.add('petya', 'pw', 512) H.add('lavka', 'pw', 256)
local db = H.load()
db.users.vasya.sites = { ['shop.mine'] = true, ['blog.mine'] = true }
db.users.lavka.sites = { ['lavka.mine'] = true }
H.save(db)
""")
# сегодня в симуляторе 15.11.2026
check(lua("local H = require('hostadm') return H.daysLeft('2026-11-30')") == 15, "daysLeft 30.11 = 15")
check(lua("local H = require('hostadm') local ok, v = H.setUntil('vasya', '30.11.2026') return v") == "2026-11-30", "setUntil дата")
check(lua("local H = require('hostadm') local ok, v = H.setUntil('vasya', '+7') return v") == "2026-12-07", "setUntil +7 от текущего срока")
lua("local H = require('hostadm') H.setUntil('vasya', '30.11.2026') H.setUntil('petya', '17.11.2026') H.setUntil('lavka', '12.11.2026')")
check(lua("local H = require('hostadm') local ok, v = H.setUntil('lavka', '+30') H.setUntil('lavka', '12.11.2026') return v") == "2026-12-15", "+30 при вышедшем сроке — от сегодня")
check(lua("local H = require('hostadm') return (H.state('lavka.mine'))") == "expired", "вышел срок → expired")
check(lua("local H = require('hostadm') return (H.state('shop.mine'))") == "ok", "shop работает")
check(lua("local H = require('hostadm') H.pause('blog.mine', 'реклама казино') local st, why = H.state('blog.mine') return st .. '|' .. why") == "paused|реклама казино", "приостановка сайта с причиной")
stub = lua("local H = require('hostadm') return H.stub('blog.mine', 'paused', 'реклама казино')")
check("временно не работает" in stub and "казино" in stub, "заглушка: " + stub.split("\n")[0])
check(lua("local ok = require('hostadm').setUntil('vasya', 'ерунда') return ok") is False, "плохая дата отклоняется")
# сериализация: ключ till и логин-ключевое слово
lua("require('hostadm').add('end', 'pw', 100)")
check(lua("return require('hostadm').load().users['end'] ~= nil and require('hostadm').load().users.vasya.till") == "2026-11-30", "база читается (логин end, поле till)")
lua("require('hostadm').del('end')")
# напоминание petya: 2 дня — но у petya нет сайтов, у vasya 15 дней → писем нет; ставим vasya на 17.11
lua("require('hostadm').setUntil('vasya', '17.11.2026') require('hostadm').remind()")
box = root + "/hosting/mail/shop.mine/admin"
check(os.path.isdir(box) and len(os.listdir(box)) == 1, "напоминание в admin@shop.mine")
lua("require('hostadm').remind()")
check(len(os.listdir(box)) == 1, "напоминание только одно")
print(open(box + "/" + os.listdir(box)[0]).read())
# окно
L = s.L.globals()
L.wired.apps.launch(L.wired.apps.get("hosting")); s.push("x"); s.run(advance=0.5)
txt = s.screen.text()
check("⌛ срок вышел 12.11" in txt and "⌛ через 2 дня" in txt, "список клиентов со сроками")
p = s.find("vasya"); s.click(p[0], p[1]); s.run(advance=0.3)
txt = s.screen.text()
check("⌛ через 2 дня" in txt, "в списке: через 2 дня")
check("⌛ срок вышел 12.11" in txt, "в списке: срок вышел")
check("⏸ приостановлен" in txt, "в таблице сайтов: приостановлен")
check("«реклама казино»" in txt, "причина под сайтом")
s.screen.png("host_term.png")
# диалог срока: продлить на 30 дней → 17.12
p = s.find("◷ Срок"); s.click(p[0] + 2, p[1]); s.run(advance=0.3)
s.screen.png("host_term_dlg.png")
p = s.find(" Сохранить "); s.click(p[0] + 2, p[1]); s.run(advance=0.3)
check(lua("return require('hostadm').load().users.vasya.till") == "2026-12-17", "диалог: продлено на 30 дней")
# приостановить сайт shop.mine
p = s.find("shop.mine"); s.click(p[0], p[1]); s.run(advance=0.3)
p = s.find(" ⏸ Приостановить сайт "); s.click(p[0] + 2, p[1]); s.run(advance=0.3)
s.type("ремонт"); s.run(advance=0.1)
s.screen.png("host_pause_dlg.png")
s.key("enter"); s.run(advance=0.3)
check(lua("local st, w = require('hostadm').state('shop.mine') return st .. '|' .. w") == "paused|ремонт", "диалог: shop.mine приостановлен")
p = s.find(" ▶ Включить сайт "); s.click(p[0] + 2, p[1]); s.run(advance=0.3)
check(lua("return (require('hostadm').state('shop.mine'))") == "ok", "кнопка: shop.mine снова работает")
s.screen.png("host_after.png")
print("errors:", s.flags.get("errors"))
print("ИТОГ:", "всё хорошо" if ok and not s.flags.get("errors") else "ЕСТЬ ОШИБКИ")
