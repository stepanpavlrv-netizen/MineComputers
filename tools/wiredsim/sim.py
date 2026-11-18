"""Мини-эмулятор компьютера MineComputers для проверки Wired без Minecraft: экран (gpu с буферами), fs, события,
auth, сеть, камеры и т. п. — заглушками. Нужны Python 3, lupa и Pillow:  pip install lupa pillow
Тесты — t*.py рядом:  python tools/wiredsim/t12.py  (картинки экрана — PNG в этой папке, диск — папка root/)."""
import os, shutil, time, collections, datetime
from lupa import lua52
from PIL import Image, ImageDraw, ImageFont

REPO = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..",
                                    "src", "main", "resources", "data", "minepc", "computer"))


def _font():
    for name in ("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", "DejaVuSansMono.ttf", "consola.ttf", "cour.ttf"):
        try:
            return ImageFont.truetype(name, 11)
        except OSError:
            pass
    return ImageFont.load_default()


FONT = _font()

PALETTE = [0xF0F0F0, 0xF2B233, 0xE57FD8, 0x99B2F2, 0xDEDE6C, 0x7FCC19, 0xF2B2CC, 0x4C4C4C, 0x999999, 0x4C99B2,
           0xB266E5, 0x3366CC, 0x7F664C, 0x57A64E, 0xCC4C4C, 0x111111]


class Term:
    def __init__(self, w, h, mw=None, mh=None):
        self.mw, self.mh = mw or w, mh or h
        self.resize(w, h)
        self.f, self.b = 0xFFFFFF, 0x000000
        self.cx, self.cy = 1, 1

    def resize(self, w, h):
        self.w, self.h = w, h
        self.ch = [[" "] * w for _ in range(h)]
        self.fg = [[0xFFFFFF] * w for _ in range(h)]
        self.bg = [[0] * w for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.ch[y][x], self.fg[y][x], self.bg[y][x] = c, self.f, self.b

    def png(self, path, cw=7, chh=14):
        im = Image.new("RGB", (self.w * cw, self.h * chh))
        d = ImageDraw.Draw(im)
        rgb = lambda c: ((c >> 16) & 255, (c >> 8) & 255, c & 255)
        for y in range(self.h):
            for x in range(self.w):
                c, f, b = self.ch[y][x], self.fg[y][x], self.bg[y][x]
                x0, y0 = x * cw, y * chh
                if c == "▀":
                    d.rectangle([x0, y0, x0 + cw - 1, y0 + chh // 2 - 1], fill=rgb(f))
                    d.rectangle([x0, y0 + chh // 2, x0 + cw - 1, y0 + chh - 1], fill=rgb(b))
                    continue
                d.rectangle([x0, y0, x0 + cw - 1, y0 + chh - 1], fill=rgb(b))
                if c != " ":
                    d.text((x0, y0 + 1), c, font=FONT, fill=rgb(f))
        im.save(path)

    def text(self):
        return "\n".join("".join(r) for r in self.ch)


class Sim:
    def __init__(self, root, w=160, h=50, disk=None, users=None):
        self.root = root
        self.mounts = {"rom": os.path.join(REPO, "rom")}
        if disk:
            self.mounts["disk"] = disk
        self.screen = Term(w, h)
        self.buffers = [None]
        self.active = 0
        self.queue = collections.deque()
        self.timers = []
        self.clock = 0.0
        self.tid = 0
        self.users = users or {}
        self.user = None
        self.flags = {}
        self.L = lua52.LuaRuntime(unpack_returned_tuples=True, encoding="utf-8")
        self.install_api()

    # ---------- файлы ----------
    def real(self, p):
        p = str(p).strip("/")
        parts = [x for x in p.split("/") if x and x != "."]
        if parts and parts[0] in self.mounts:
            return os.path.join(self.mounts[parts[0]], *parts[1:]), parts[0] == "rom"
        return os.path.join(self.root, *parts), False

    def install_api(self):
        L = self.L
        g = L.globals()
        sim = self

        def tbl(d):
            t = L.table()
            for k, v in d.items():
                t[k] = v
            return t

        # ---- gpu ----
        def target():
            return sim.screen if sim.active == 0 else sim.buffers[sim.active]

        def gpu_set(x, y, s, vert=None):
            sim.calls = getattr(sim, "calls", 0) + 1
            t = target()
            for i, c in enumerate(str(s)):
                t.put(int(x) - 1 + i, int(y) - 1, c)
            return True

        def gpu_fill(x, y, w, h, c):
            sim.calls = getattr(sim, "calls", 0) + 1
            t = target()
            c = str(c)[0] if c else " "
            for yy in range(int(y) - 1, int(y) - 1 + int(h)):
                for xx in range(int(x) - 1, int(x) - 1 + int(w)):
                    t.put(xx, yy, c)
            return True

        def gpu_get(x, y):
            sim.calls = getattr(sim, "calls", 0) + 1
            t = target()
            x, y = int(x) - 1, int(y) - 1
            if not (0 <= x < t.w and 0 <= y < t.h):
                raise Exception("index out of bounds")
            return t.ch[y][x], t.fg[y][x], t.bg[y][x]

        def setf(c, pal=None):
            t = target(); old = t.f; t.f = int(c); return old

        def setb(c, pal=None):
            t = target(); old = t.b; t.b = int(c); return old

        def alloc(w=None, h=None):
            b = Term(int(w or sim.screen.w), int(h or sim.screen.h))
            sim.buffers.append(b)
            return len(sim.buffers) - 1

        def free(i=None):
            i = int(i if i is not None else sim.active)
            if 0 < i < len(sim.buffers):
                sim.buffers[i] = None
            return True

        def setact(i):
            sim.active = int(i); return True

        def bitblt(dst=0, col=1, row=1, w=None, h=None, src=None, fc=1, fr=1):
            D = sim.screen if not dst else sim.buffers[int(dst)]
            si = int(src if src is not None else sim.active)
            S = sim.screen if si == 0 else sim.buffers[si]
            w = int(w or S.w); h = int(h or S.h)
            for yy in range(h):
                for xx in range(w):
                    sx, sy = int(fc) - 1 + xx, int(fr) - 1 + yy
                    dx, dy = int(col) - 1 + xx, int(row) - 1 + yy
                    if 0 <= sx < S.w and 0 <= sy < S.h and 0 <= dx < D.w and 0 <= dy < D.h:
                        D.ch[dy][dx], D.fg[dy][dx], D.bg[dy][dx] = S.ch[sy][sx], S.fg[sy][sx], S.bg[sy][sx]
            return True

        def setres(w, h):
            w, h = int(w), int(h)
            if w > sim.screen.mw or h > sim.screen.mh:
                raise Exception("unsupported resolution")
            ch = (w, h) != (sim.screen.w, sim.screen.h)
            sim.screen.resize(w, h)
            if ch:
                sim.queue.append(("screen_resized", w, h))
            return ch

        # ---- кадровый буфер (как gpu.canvas) ----
        def make_canvas(w=None, h=None):
            w = int(w or sim.screen.w); h = int(h or sim.screen.h * 2)
            px = [0] * (w * h)
            cvt = L.table()

            def cv_clear(self, color=0):
                for i in range(len(px)): px[i] = int(color)

            def cv_fill(self, x, y, fw, fh, color):
                for yy in range(int(y), int(y) + int(fh)):
                    for xx in range(int(x), int(x) + int(fw)):
                        if 0 <= xx < w and 0 <= yy < h: px[yy * w + xx] = int(color)

            def cv_camera(self, name, x=0, y=0, fw=None, fh=None):
                x, y = int(x), int(y); fw = int(fw or w - x); fh = int(fh or h - y)
                seed = sum(ord(c) for c in str(name))
                for yy in range(fh):
                    for xx in range(fw):
                        X, Y = x + xx, y + yy
                        if 0 <= X < w and 0 <= Y < h:
                            if yy < fh * 0.45:
                                c = 0x6f9fe8 + int(yy / fh * 40)
                            else:
                                c = 0x4f9431 if (xx * 7 + yy * 3 + seed) % 11 else 0x5fa83c
                            if abs(xx - fw // 2 - seed % 5) < 3 and yy > fh * 0.45: c = 0x8a6a3e
                            px[Y * w + X] = c
                return 10

            def cv_present(self, col=1, row=1, cx1=None, cy1=None, cx2=None, cy2=None):
                t = target()
                col, row = int(col) - 1, int(row) - 1
                lim = (int(cx1) - 1, int(cy1) - 1, int(cx2) - 1, int(cy2) - 1) if cx1 is not None else (0, 0, 10**6, 10**6)
                for r in range((h + 1) // 2):
                    ty = row + r
                    if not (0 <= ty < t.h and lim[1] <= ty <= lim[3]): continue
                    for c in range(w):
                        tx = col + c
                        if not (0 <= tx < t.w and lim[0] <= tx <= lim[2]): continue
                        top = px[2 * r * w + c]; bot = px[min(h - 1, 2 * r + 1) * w + c]
                        t.ch[ty][tx], t.fg[ty][tx], t.bg[ty][tx] = "▀", top, bot
                return 1

            cvt.clear, cvt.fill, cvt.camera, cvt.present = cv_clear, cv_fill, cv_camera, cv_present
            cvt.free = lambda self: None
            return cvt

        gpu = tbl({
            "canvas": make_canvas,
            "set": gpu_set, "fill": gpu_fill, "get": gpu_get, "setForeground": setf, "setBackground": setb,
            "getResolution": lambda: (target().w, target().h), "maxResolution": lambda: (sim.screen.mw, sim.screen.mh),
            "setResolution": setres, "allocateBuffer": alloc, "freeBuffer": free, "setActiveBuffer": setact,
            "getActiveBuffer": lambda: sim.active, "bitblt": bitblt, "getDepth": lambda: 24,
        })

        # ---- term (экран, для установщика и программ) ----
        S = sim.screen

        def t_write(s):
            for c in str(s):
                S.put(S.cx - 1, S.cy - 1, c)
                S.cx += 1

        def t_scroll(n):
            n = int(n)
            for _ in range(n):
                S.ch.pop(0); S.fg.pop(0); S.bg.pop(0)
                S.ch.append([" "] * S.w); S.fg.append([S.f] * S.w); S.bg.append([S.b] * S.w)

        def t_clear():
            for y in range(S.h):
                for x in range(S.w):
                    S.ch[y][x], S.fg[y][x], S.bg[y][x] = " ", S.f, S.b

        def t_setcur(x, y):
            S.cx, S.cy = int(x), int(y)

        term = tbl({
            "write": t_write, "clear": t_clear, "clearLine": lambda: None, "getCursorPos": lambda: (S.cx, S.cy),
            "setCursorPos": t_setcur, "setCursorBlink": lambda b: None, "getSize": lambda: (S.w, S.h),
            "scroll": t_scroll, "isColor": lambda: True, "getResolution": lambda: (S.w, S.h),
            "maxResolution": lambda: (S.mw, S.mh), "setResolution": setres,
            "setTextColor": lambda i: setattr(S, "f", PALETTE[int(i)]), "setBackgroundColor": lambda i: setattr(S, "b", PALETTE[int(i)]),
            "getTextColor": lambda: 0, "getBackgroundColor": lambda: 15,
            "setTextColorRGB": lambda c: setattr(S, "f", int(c)), "setBackgroundColorRGB": lambda c: setattr(S, "b", int(c)),
            "getTextColorRGB": lambda: S.f, "getBackgroundColorRGB": lambda: S.b,
            "getPaletteColor": lambda i: PALETTE[int(i)], "getDepth": lambda: 24,
        })
        g.term = term

        # ---- fs ----
        def f_list(p):
            r, _ = sim.real(p)
            names = sorted(os.listdir(r))
            if str(p).strip("/") == "":
                names = sorted(set(names) | set(sim.mounts))
            return tbl({i + 1: n for i, n in enumerate(names)})

        def f_exists(p):
            if str(p).strip("/") in sim.mounts:
                return True
            return os.path.exists(sim.real(p)[0])

        def f_isdir(p):
            if str(p).strip("/") in sim.mounts or str(p).strip("/") == "":
                return True
            return os.path.isdir(sim.real(p)[0])

        def f_size(p):
            r = sim.real(p)[0]
            return os.path.getsize(r) if os.path.isfile(r) else 0

        def f_mkdir(p):
            os.makedirs(sim.real(p)[0], exist_ok=True)

        def f_delete(p):
            r = sim.real(p)[0]
            if os.path.isdir(r):
                shutil.rmtree(r)
            elif os.path.exists(r):
                os.remove(r)

        def f_copy(a, b):
            ra, rb = sim.real(a)[0], sim.real(b)[0]
            if os.path.exists(rb):
                raise Exception("файл уже есть: " + str(b))
            if os.path.isdir(ra):
                shutil.copytree(ra, rb)
            else:
                shutil.copy(ra, rb)

        def f_move(a, b):
            shutil.move(sim.real(a)[0], sim.real(b)[0])

        def f_open(p, mode="r"):
            r, ro = sim.real(p)
            mode = str(mode)
            if "r" in mode:
                if not os.path.isfile(r):
                    return None, "нет файла " + str(p)
                data = open(r, "rb").read().decode("utf-8", "replace")
                st = {"pos": 0}

                def readAll():
                    s = data[st["pos"]:]; st["pos"] = len(data); return s

                def readLine():
                    if st["pos"] >= len(data):
                        return None
                    i = data.find("\n", st["pos"])
                    if i < 0:
                        s = data[st["pos"]:]; st["pos"] = len(data); return s
                    s = data[st["pos"]:i]; st["pos"] = i + 1; return s
                return tbl({"readAll": readAll, "readLine": readLine, "close": lambda: None})
            os.makedirs(os.path.dirname(r), exist_ok=True)
            fh = open(r, "a" if "a" in mode else "w", encoding="utf-8")
            return tbl({"write": lambda s: fh.write(str(s)), "writeLine": lambda s: fh.write(str(s) + "\n"),
                        "close": lambda: fh.close(), "flush": lambda: fh.flush()})

        g.fs = tbl({"list": f_list, "exists": f_exists, "isDir": f_isdir, "getSize": f_size, "makeDir": f_mkdir,
                    "delete": f_delete, "copy": f_copy, "move": f_move, "open": f_open,
                    "getCapacity": lambda p: 2 * 1024 * 1024, "getFreeSpace": lambda p: 1800 * 1024,
                    "isReadOnly": lambda p: sim.real(p)[1]})

        # ---- os / computer ----
        def start_timer(sec):
            sim.tid += 1
            sim.timers.append((sim.clock + float(sec or 0), sim.tid))
            return sim.tid

        NOW = datetime.datetime(2026, 11, 15, 12, 4)

        def os_date(fmt="%c", t=None):
            fmt = str(fmt)
            d = NOW if t is None else datetime.datetime(1970, 1, 1) + datetime.timedelta(seconds=float(t))
            if fmt.lstrip("!").startswith("*t"):
                return tbl({"year": d.year, "month": d.month, "day": d.day, "hour": d.hour, "min": d.minute, "sec": d.second,
                            "wday": d.isoweekday() % 7 + 1, "yday": d.timetuple().tm_yday, "isdst": False})
            return d.strftime(fmt.lstrip("!"))

        def os_time(t=None):
            if t is None:
                d = NOW
            else:
                h = t["hour"]
                d = datetime.datetime(int(t["year"]), int(t["month"]), int(t["day"]), 12 if h is None else int(h), int(t["min"] or 0))
            return int((d - datetime.datetime(1970, 1, 1)).total_seconds())

        osT = L.eval("os")
        osT.startTimer = start_timer
        osT.date = os_date
        osT.time = os_time
        osT.shutdown = lambda: sim.flags.__setitem__("shutdown", True)
        osT.reboot = lambda: sim.flags.__setitem__("reboot", True)
        osT.getComputerLabel = lambda: "shop"
        osT.queueEvent = lambda *a: sim.queue.append(tuple(a))
        osT.cancelTimer = lambda i: None

        def stats():
            return tbl({"cores": tbl({1: 12.0, 2: 40.0, 3: 5.0, 4: 70.0}), "gpu": 18.0,
                        "memory": tbl({"used": 900 * 1024, "total": 4096 * 1024}), "cpuTemp": 52,
                        "processes": tbl({1: tbl({"pid": 1, "path": "/bios.lua", "cpu": 30.0, "memory": 600000, "status": "running"}),
                                          2: tbl({"pid": 2, "path": "/rom/programs/httpd.lua", "cpu": 2.0, "memory": 40000, "status": "waiting"})})})

        g.computer = tbl({
            "uptime": lambda: sim.clock, "pending": lambda: len(sim.queue), "gpu": lambda: gpu, "stats": stats,
            "info": lambda: tbl({"cpu": "Процессор ур. 3, ядер: 8", "ram": 4096, "gpu": "Видеокарта ур. 4", "architecture": "Lua",
                                 "opsPerSecond": 6785600}),
            "processes": lambda: L.table(), "kill": lambda p: True, "__charge": lambda n: None,
        })
        g.unicode = tbl({"len": lambda s: len(str(s)), "sub": lambda s, i, j=None: lua_sub(str(s), i, j)})

        # ---- auth ----
        def a_login(name, pw):
            name, pw = str(name), str(pw)
            if sim.users.get(name, (None,))[0] == pw:
                sim.user = name
                return True
            return False, "bad", 0, 1

        def a_add(name, pw, admin=False):
            sim.users[str(name)] = (str(pw), bool(admin))
            return True

        g.auth = tbl({
            "open": lambda: len(sim.users) == 0, "user": lambda: sim.user, "isAdmin": lambda: (sim.user and sim.users[sim.user][1]) or not sim.users,
            "users": lambda: tbl({i + 1: tbl({"name": n, "admin": a}) for i, (n, (p, a)) in enumerate(sim.users.items())}),
            "login": a_login, "logout": lambda: setattr(sim, "user", None), "add": a_add,
        })
        g.sound = None
        # ---- камеры ----
        cams = [{"name": "двор", "address": "3368c5f8", "width": 192, "height": 108, "link": 1250000, "hops": 1},
                {"name": "склад", "address": "a1f07c22", "width": 128, "height": 72, "link": 1250000, "hops": 2},
                {"name": "поле", "address": "7c0e19b4", "width": 256, "height": 144, "link": 125000, "hops": 1}]
        cinfo = {c["name"]: {"name": c["name"], "width": c["width"], "height": c["height"], "fps": 10, "yaw": 0, "pitch": -12,
                             "fov": 70, "night": False, "patrol": False, "res": 4} for c in cams}

        def c_info(n):
            return tbl(cinfo[str(n)]) if str(n) in cinfo else (None, "нет камеры")

        def c_turn(n, dy, dp):
            cinfo[str(n)]["yaw"] += float(dy); cinfo[str(n)]["pitch"] += float(dp); return c_info(n)

        def c_zoom(n, fov):
            cinfo[str(n)]["fov"] = max(10, min(90, float(fov))); return c_info(n)

        def c_set(n, k, v):
            cinfo[str(n)][str(k)] = v; return c_info(n)

        g.camera = tbl({"list": lambda: tbl({i + 1: tbl(c) for i, c in enumerate(cams)}), "info": c_info, "turn": c_turn,
                        "zoom": c_zoom, "set": c_set})
        # ---- сеть (почта уходит «в никуда»: отвечаем MAILOK сразу) ----
        def n_send(host, port, kind, *rest):
            if str(kind) == "GET":
                rid, reply, path = rest[0], int(rest[1]), str(rest[2])
                f = os.path.join(sim.root, "www", path.strip("/") or "index.page")
                if os.path.isdir(f): f = os.path.join(f, "index.page")
                if os.path.isfile(f):
                    body = open(f, encoding="utf-8").read(); code = 200
                else:
                    body = "# 404\nНет страницы " + path; code = 404
                ct = "page" if f.endswith(".page") or code == 404 else "file"
                sim.queue.append(("net_message", "bbbbbbbb", reply, 1, "RESP", rid, 1, 1, code, ct, body))
                return True
            if str(kind) == "MAIL":
                sim.queue.append(("net_message", "bbbbbbbb", int(rest[1]), 1, "MAILOK", rest[0], True, "доставлено"))
            return True
        g.net = tbl({"dns": lambda *a: (True, tbl({1: tbl({"name": "shop.mine"})})), "hostname": lambda: "shop",
                     "address": lambda: "aaaaaaaa-1111", "speed": lambda: 125000, "send": n_send,
                     "open": lambda p: True, "close": lambda p: True})
        # ---- соседи, пинг, контроллеры, коммутаторы, Wi-Fi ----
        peers = [{"name": "склад", "address": "bbbbbbbb-2222", "hops": 1}, {"name": "ioc-ворота", "address": "cccccccc-3333", "hops": 2},
                 {"name": "", "address": "dddddddd-4444", "hops": 3}]
        sides = {s_: {"mode": "in", "level": 0} for s_ in ("north", "south", "west", "east", "up", "down")}
        sides["east"] = {"mode": "out", "level": 15}
        sides["north"]["block"] = "minecraft:lever"
        ioc = {"name": "ворота", "address": "cccccccc", "light": 12, "time": "день", "players": {"1": "Steve"}}
        sim.rid = 100
        def ioc_state():
            st = dict(ioc); st["players"] = tbl(ioc["players"])
            st["sides"] = tbl({k: tbl(v) for k, v in sides.items()})
            return tbl(st)
        def n_io(target, cmd, *a):
            sim.rid += 1
            rid = sim.rid
            if not str(target).startswith("cccccccc"):
                sim.queue.append(("io_error", str(target), rid, "не контроллер"))
                return rid
            cmd = str(cmd)
            if cmd == "set": sides[str(a[0])] = {"mode": "out", "level": int(a[1])}
            elif cmd == "mode": sides[str(a[0])]["mode"] = str(a[1])
            elif cmd == "name": ioc["name"] = str(a[0])
            sim.queue.append(("io_state", str(target), rid, ioc_state()))
            return rid
        def n_ping(target):
            sim.rid += 1
            sim.queue.append(("net_pong", str(target), sim.rid, 7, 1))
            return sim.rid
        def n_broadcast(port, *a):
            sim.flags.setdefault("broadcast", []).append((int(port),) + tuple(str(x) for x in a))
            return True
        g.net.peers = lambda: tbl({i + 1: tbl(p_) for i, p_ in enumerate(peers)})
        g.net.broadcast = n_broadcast
        g.net.ping = n_ping
        g.net.io = n_io
        g.net.stats = lambda: tbl({"received": 123456, "sent": 65432})
        g.net.wifi = tbl({"status": lambda: tbl({"adapter": "USB Wi-Fi", "ssid": "дом", "connected": True, "signal": 80, "speed": 54}),
                          "scan": lambda: tbl({1: tbl({"ssid": "дом", "signal": 80, "speed": 54, "secured": True}),
                                               2: tbl({"ssid": "кафе", "signal": 30, "speed": 11, "secured": False})}),
                          "connect": lambda *a: True, "disconnect": lambda: True})
        ports = [{"port": 1, "side": "front", "n": 1, "link": True, "enabled": True, "vlan": "", "device": "shop", "label": ""},
                 {"port": 2, "side": "front", "n": 2, "link": True, "enabled": True, "vlan": "red", "device": "склад", "label": "склад"},
                 {"port": 3, "side": "back", "n": 1, "link": False, "enabled": False, "vlan": "", "device": "", "label": ""}]
        def sw_set(i, port, en, vlan=None):
            for p_ in ports:
                if p_["port"] == int(port):
                    p_["enabled"] = bool(en)
                    if vlan is not None: p_["vlan"] = "" if str(vlan) == "none" else str(vlan)
            return True
        g.switch = tbl({"list": lambda: tbl({1: tbl({"id": "sw-a1", "kind": "desk", "ports": 3})}),
                        "ports": lambda i: tbl({k + 1: tbl(p_) for k, p_ in enumerate(ports)}), "set": sw_set})
        g.computer.beep = lambda *a: sim.flags.setdefault("beeps", []).append(a)
        import hashlib, zlib
        def _b(x): return x.encode("utf-8", "surrogateescape") if isinstance(x, str) else bytes(x)
        g.data = tbl({"sha256": lambda x: hashlib.sha256(_b(x)).hexdigest(), "toHex": lambda x: x,
                      "crc32": lambda x: "%08x" % zlib.crc32(_b(x))})
        procs = []
        g.computer.processes = lambda: tbl({i + 1: tbl(p) for i, p in enumerate(procs)})
        g.computer.spawn = lambda path, *a: (procs.append({"pid": len(procs) + 5, "path": str(path)}), len(procs) + 4)[1]
        osT.epoch = lambda *a: 1763200000000

        # BIOS до запуска оболочки
        src = open(os.path.join(REPO, "bios.lua"), encoding="utf-8").read()
        src = src[:src.index('io = require("io")')]
        L.execute(src)
        L.execute("""
          function _sim_start(path, ...)
            local args = table.pack(...)
            _sim_co = coroutine.create(function() return os.run({}, path, table.unpack(args, 1, args.n)) end)
          end
          function _sim_step(...)
            local ok, r = coroutine.resume(_sim_co, ...)
            if not ok then error(tostring(r) .. "\\n" .. debug.traceback(_sim_co), 0) end
            return coroutine.status(_sim_co)
          end
        """)
        g.printError = L.eval("function(...) local t = {...} for i=1,#t do t[i]=tostring(t[i]) end _sim_err(table.concat(t, ' ')) end")
        g._sim_err = lambda s: (sim.flags.setdefault("errors", []).append(str(s)), print("LUA ERROR:", s))

    # ---------- запуск ----------
    def start(self, path, *args):
        self.L.globals()._sim_start(path, *args)
        self.status = self.L.globals()._sim_step()

    def push(self, *ev):
        self.queue.append(tuple(ev))

    def run(self, max_events=2000, until_idle=True, advance=0.0):
        n = 0
        deadline = self.clock + advance
        while self.status != "dead" and n < max_events:
            if self.queue:
                ev = self.queue.popleft()
                self.status = self.L.globals()._sim_step(*ev)
                n += 1
                continue
            self.timers.sort()
            if self.timers and (self.timers[0][0] <= deadline):
                t, tid = self.timers.pop(0)
                self.clock = max(self.clock, t)
                self.queue.append(("timer", tid))
                continue
            break
        self.clock = max(self.clock, deadline)
        return n

    def click(self, x, y, btn=1):
        self.push("mouse_click", btn, x, y)
        self.push("mouse_up", btn, x, y)
        self.run()

    def dbl(self, x, y):
        self.click(x, y)
        self.clock += 0.1
        self.click(x, y)

    def type(self, s):
        for c in s:
            self.push("char", c)
        self.run()

    def key(self, k, ctrl=False):
        self.push("key", k, ctrl, False)
        self.run()

    def find(self, text):
        for y, row in enumerate(self.screen.ch):
            line = "".join(row)
            i = line.find(text)
            if i >= 0:
                return i + 1, y + 1
        return None


def lua_sub(s, i, j=None):
    n = len(s)
    i = int(i)
    j = n if j is None else int(j)
    if i < 0:
        i = max(1, n + i + 1)
    if j < 0:
        j = n + j + 1
    i = max(1, i)
    j = min(n, j)
    return s[i - 1:j] if i <= j else ""
