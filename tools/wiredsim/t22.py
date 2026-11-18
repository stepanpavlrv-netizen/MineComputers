"""SWired в симуляторе: рабочий стол сервера и все окна (Сервер, Службы, Состояние, Журнал, Пользователи, Копии),
«Серверы» в обычном Wired. Снимки — swired_*.png в папке, переданной первым аргументом (по умолчанию — здесь)."""
import os, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sim import Sim, REPO

here = os.path.dirname(os.path.abspath(__file__))
out = sys.argv[1] if len(sys.argv) > 1 else here
root = here + "/root"

FAKE = r"""
bios = bios or {}
bios.server = function()
  return { power = "on", uptime = 273600, slot = 3, cpus = 2, cores = 4, temp = 48, fans = 6, rpm = 2100, watts = 410,
    psu = { { present = true, wattage = 2000 }, { present = true, wattage = 2000 } }, reserve = true,
    bays = { { state = "ok", name = "Жёсткий диск 1 ТБ", wear = 22, raid = true }, { state = "ok", name = "Жёсткий диск 1 ТБ", wear = 24, raid = true },
             { state = "ok", name = "Жёсткий диск 1 ТБ", wear = 31, raid = true }, { state = "warning", name = "Жёсткий диск 1 ТБ", wear = 91, raid = true },
             { state = "ok", name = "SATA SSD 512 ГБ", wear = 8 }, { state = "empty" }, { state = "empty" }, { state = "empty" } },
    raid = { level = "raid5", name = "data", state = "ok" },
    bmc = { present = true, address = "1f2e3d4c-0000", defaultPassword = true }, log = {} }
end
bios.raid = function() return nil end
computer.pid = function() return 1 end
local st0 = computer.stats
computer.stats = function()
  local s = st0()
  s.disks = { { path = "/", capacity = 3000000, used = 1200000 }, { path = "/usb", capacity = 4000, used = 100 } }
  s.net = { up = 400, down = 2100, speed = 125000 }
  return s
end
local S = require("swired")
S.log("Сервер", "ok", "включён · SWired 1.0")
S.log("Копии", "ok", "копия «Сайты и почта»: 214 КБ → /backups")
S.log("Вход", "err", "неверный пароль admin с 10.0.0.23")
S.log("Службы", "err", "«Мой бот» упала")
S.log("Службы", "warn", "«Мой бот» перезапущена (1/5)")
S.log("Диски", "warn", "диск в отсеке 4: износ 91 %")
S.saveBackup({ name = "Сайты и почта", dirs = { "/www", "/mail" }, to = { kind = "path", target = "/backups" }, every = "day", at = "06:00", keep = 7 })
"""


def fresh(server):
    shutil.rmtree(root, ignore_errors=True)
    os.makedirs(root + "/home")
    os.makedirs(root + "/apps")
    os.makedirs(root + "/www")
    with open(root + "/www/index.page", "w", encoding="utf-8") as f:
        f.write("# Сайт")
    shutil.copytree(REPO + "/disks/wired/system", root + "/wired")
    if server:
        with open(root + "/wired/edition", "w") as f:
            f.write("server\n")
    s = Sim(root)
    s.start("/wired/boot.lua")
    s.L.execute(FAKE)
    s.run(advance=0.5)
    return s


def shot(s, n):
    p = os.path.join(out, "swired_%s.png" % n)
    s.screen.png(p)
    print("снимок", p)


s = fresh(True)
shot(s, "desktop")
L = s.L
for app in ["srv_server", "srv_services", "srv_health", "srv_journal", "srv_users", "srv_backup"]:
    L.globals().wired.apps.launch(L.globals().wired.apps.get(app))
    s.push("sim_redraw")
    s.run(advance=1.5)
    shot(s, app)
    s.key("f4", True)
    s.run(advance=0.3)
errs = s.flags.get("errors") or []

# «Серверы» в обычном Wired: найденный сервер (подставной ответ)
s2 = fresh(False)
s2.L.execute(r"""
local S = require("swired")
S.discover = function() return { { address = "bbbbbbbb-2222", name = "srv-01", version = "1.0", level = "warn", count = 2,
  uptime = 273600, problems = { { level = "warn", text = "диск в отсеке 4: износ 91 % — пора менять" },
  { level = "warn", text = "у BMC пароль по умолчанию — смените" } } } } end
""")
s2.L.globals().wired.apps.launch(s2.L.globals().wired.apps.get("servers"))
s2.push("sim_redraw")
s2.run(advance=2.5)
shot(s2, "servers")
errs += s2.flags.get("errors") or []
print("errors", errs)
