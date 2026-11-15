import os, shutil, sys, datetime
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO
here = os.path.dirname(os.path.abspath(__file__)); root = here + "/root"
shutil.rmtree(root, ignore_errors=True); os.makedirs(root + "/home"); os.makedirs(root + "/apps")
shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
os.makedirs(root + "/sites/shop.mine"); open(root + "/sites/shop.mine/index.page", "w").write("# Магазин\n" * 50)
os.makedirs(root + "/hosting/mail/shop.mine/info"); open(root + "/hosting/mail/shop.mine/info/1.txt", "w").write("От: a\n\nhi")
os.makedirs(root + "/var/httpd/stats")
today = datetime.date.today()
days = ", ".join('["%s"] = { views = %d, bytes = %d, uniq = { a = true, b = true } }' % ((today - datetime.timedelta(i)).isoformat(), v, v * 900) for i, v in enumerate([12, 30, 7, 0, 18, 25, 4]))
open(root + "/var/httpd/stats/shop.mine", "w").write("{ views = 96, bytes = 90000, days = { %s } }" % days)
s = Sim(root); s.start("/wired/boot.lua"); s.run()
L = s.L.globals()
L.wired.apps.launch(L.wired.apps.get("hosting")); s.push("x"); s.run(advance=0.5)
p = s.find(" + Клиент "); s.click(p[0] + 2, p[1]); s.run()
for v in ("vasya", "pw", "256"):
    s.type(v); s.key("enter"); s.run(advance=0.3)
# привязать сайт к клиенту
s.L.execute("local H = require('hostadm') local db = H.load() db.users.vasya.sites = { ['shop.mine'] = true } H.save(db)")
s.push("x"); s.run(advance=0.3)
p = s.find("vasya"); s.click(p[0], p[1]); s.run(advance=0.3)
s.screen.png("hostapp.png")
print(open(root + "/etc/hosting.db").read()[:200], s.flags.get("errors"))
