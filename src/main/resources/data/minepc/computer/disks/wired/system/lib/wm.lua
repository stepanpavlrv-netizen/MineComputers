-- Wired: оконная система — рабочий стол, окна, панель задач, «Пуск», уведомления, вход, масштаб.
local G = wired.gfx
local T = wired.theme
local I = wired.icons
local U = wired.ui
local floor, max, min = math.floor, math.max, math.min

local wm = { windows = {}, focus = nil, mode = "desk", running = true, notes = {} }
local TB, DH = 2, 23                 -- строк панели задач, высота рабочего стола
local dirty                          -- { x1, y1, x2, y2 } — что перерисовать
local wall                           -- обои полосами
local drag                           -- перетаскивание окна: { win, dx, dy, kind }
local press                          -- окно, в котором нажали кнопку мыши
local timers = {}                    -- номер таймера → функция
local menuOpen, menuHits, menuRect = false, {}, nil
local barHits, noteHits = {}, {}
local icons, selIcon = {}, nil
local lastClick = { t = 0 }
local nextId = 1

-- ======================= перерисовка =======================

function wm.invalidate(x, y, w, h)
  local x2, y2 = x + w - 1, y + h - 1
  if not dirty then dirty = { x, y, x2, y2 } return end
  dirty[1], dirty[2] = min(dirty[1], x), min(dirty[2], y)
  dirty[3], dirty[4] = max(dirty[3], x2), max(dirty[4], y2)
end
function wm.invalidateAll() wm.invalidate(1, 1, G.W, G.H) end
function wm.invalidateWin(win) wm.invalidate(win.x, win.y, win.w, win.h) end
function wm.redraw(win) if win and not win.min then wm.invalidateWin(win) end end
local function invalidateBar() wm.invalidate(1, DH + 1, G.W, TB) end
wm.invalidateBar = invalidateBar

-- ======================= фоновые задачи =======================
-- Сопрограмма, которой можно ждать событий (сеть, таймеры) и не держать систему: wm.spawn(fn, done).
local tasks = {}
local nullTerm = setmetatable({}, { __index = function() return function() return 0, 0 end end })

local function resumeTask(t, ...)
  local saved = term
  _G.term = nullTerm
  local r = table.pack(coroutine.resume(t.co, ...))
  _G.term = saved
  local ok, res = r[1], r[2]
  if not ok or coroutine.status(t.co) == "dead" then
    for i, x in ipairs(tasks) do if x == t then table.remove(tasks, i) break end end
    if t.done then
      local okd, err = pcall(t.done, table.unpack(r, 1, r.n))
      if not okd then wm.notify({ title = "Ошибка", text = tostring(err) }) end
    elseif not ok then
      wm.notify({ title = "Ошибка в фоне", text = tostring(res) })
    end
  else
    t.filter = res
  end
end

function wm.spawn(fn, done)
  local t = { co = coroutine.create(fn), done = done }
  tasks[#tasks + 1] = t
  resumeTask(t)
  return t
end

local function feedTasks(ev)
  if #tasks == 0 then return end
  local list = {}
  for i, t in ipairs(tasks) do list[i] = t end
  for _, t in ipairs(list) do
    if t.filter == nil or t.filter == ev[1] or ev[1] == "terminate" then resumeTask(t, table.unpack(ev, 1, ev.n)) end
  end
end

--- Таймер Wired: fn вызовется через sec секунд. Возвращает номер.
function wm.after(sec, fn)
  local id = os.startTimer(sec)
  timers[id] = fn
  return id
end

function wm.desktopHeight() return DH end

-- ======================= раскладка =======================

local function geom(win)
  win.bx, win.by, win.bw, win.bh = win.x, win.y + 1, win.w, win.h - 1
  win.c.w, win.c.h, win.c.bg = win.bw, win.bh, T.win
end

local function layoutIcons()
  icons = {}
  local list = wired.apps.desktop()
  local perCol = max(1, floor((DH - 1) / 8))
  for i, a in ipairs(list) do
    local col, row = floor((i - 1) / perCol), (i - 1) % perCol
    icons[i] = { app = a, x = 2 + col * 16, y = 1 + row * 8 }
  end
end

function wm.layout()
  G.init()
  TB = G.H >= 30 and 2 or 1
  DH = G.H - TB
  local s = wired.settings
  wall = wired.wallpaper.build(G.W, DH, s.wallpaper or "wires", T.dark, T.desk)
  for _, win in ipairs(wm.windows) do
    if win.max then win.x, win.y, win.w, win.h = 1, 1, G.W, DH end
    win.w, win.h = min(win.w, G.W), min(win.h, DH)
    if win.x + win.w - 1 > G.W then win.x = max(1, G.W - win.w + 1) end
    if win.y + win.h - 1 > DH then win.y = max(1, DH - win.h + 1) end
    geom(win)
    if win.app.event then win.app.event(win, "resize") end
  end
  layoutIcons()
  wm.invalidateAll()
end

--- Изменились программы (установили или удалили) — обновить значки.
function wm.refreshDesktop()
  layoutIcons()
  wm.invalidate(1, 1, G.W, DH)
end

-- ======================= окна =======================

function wm.open(app)
  local win = { id = nextId, app = app, title = app.title or "Окно", icon = app.icon or "app", hits = {},
                resizable = app.resizable ~= false, minW = app.minW or 24, minH = app.minH or 6 }
  nextId = nextId + 1
  win.w = min(app.w or 60, G.W)
  win.h = min(app.h or 20, DH)
  local n = #wm.windows % 6
  win.x = max(1, min(G.W - win.w + 1, floor((G.W - win.w) / 2) + 1 + n * 2 - 5))
  win.y = max(1, min(DH - win.h + 1, floor((DH - win.h) / 2) + 1 + n - 2))
  win.c = U.canvas(win)
  geom(win)
  wm.windows[#wm.windows + 1] = win
  wm.focusWin(win)
  if app.init then app.init(win) end
  wm.invalidateWin(win)
  invalidateBar()
  return win
end

function wm.focusWin(win)
  if wm.focus == win and win and wm.windows[#wm.windows] == win then return end
  local old = wm.focus
  for i, w in ipairs(wm.windows) do
    if w == win then table.remove(wm.windows, i) break end
  end
  if win then
    wm.windows[#wm.windows + 1] = win
    win.min = false
    wm.invalidateWin(win)
  end
  wm.focus = win
  if old and old ~= win then
    wm.redraw(old)
    if old.app.event then old.app.event(old, "blur") end
  end
  if win and win.app.event then win.app.event(win, "focus") end
  invalidateBar()
end

local function topVisible()
  for i = #wm.windows, 1, -1 do
    if not wm.windows[i].min then return wm.windows[i] end
  end
end

function wm.close(win, force)
  if not force and win.app.close and win.app.close(win) == false then return end
  for i, w in ipairs(wm.windows) do
    if w == win then table.remove(wm.windows, i) break end
  end
  win.closed = true
  wm.invalidateWin(win)
  if wm.focus == win then wm.focus = nil; wm.focusWin(topVisible()) end
  invalidateBar()
end

function wm.minimize(win)
  win.min = true
  wm.invalidateWin(win)
  if wm.focus == win then wm.focus = nil; wm.focusWin(topVisible()) end
  invalidateBar()
end

function wm.toggleMax(win)
  if not win.resizable then return end
  wm.invalidateWin(win)
  if win.max then
    win.x, win.y, win.w, win.h = table.unpack(win.restore)
    win.max = false
  else
    win.restore = { win.x, win.y, win.w, win.h }
    win.x, win.y, win.w, win.h = 1, 1, G.W, DH
    win.max = true
  end
  geom(win)
  if win.app.event then win.app.event(win, "resize") end
  wm.invalidateWin(win)
end

function wm.setTitle(win, title)
  win.title = title
  wm.invalidate(win.x, win.y, win.w, 1)
  invalidateBar()
end

function wm.find(pred)
  for _, w in ipairs(wm.windows) do if pred(w) then return w end end
end

-- ======================= рисование =======================

local function drawWindow(win, d1, d2, d3, d4)
  if win.x > d3 or win.y > d4 or win.x + win.w - 1 < d1 or win.y + win.h - 1 < d2 then return end
  G.setClip(d1, d2, d3, d4)
  if not G.narrow(win.x, win.y, win.w, win.h) then return end
  local active = win == wm.focus
  local tb = active and T.title or T.titleIn
  G.fill(win.x, win.y, win.w, 1, tb)
  if active then G.text(win.x, win.y, " ", T.fg, T.accent) end
  local mini = I.MINI[win.icon] or I.MINI.app
  G.text(win.x + 2, win.y, mini[1], mini[2], tb)
  G.text(win.x + 4, win.y, G.cut(win.title, max(1, win.w - 16)), active and T.fg or T.dim, tb)
  G.text(win.x + win.w - 10, win.y, "─  " .. (win.resizable and (win.max and "❐" or "□") or " ") .. "  ", T.dim, tb)
  G.text(win.x + win.w - 3, win.y, " ✕ ", active and T.white or T.dim, active and T.accentDim or tb)
  -- тело
  G.setClip(d1, d2, d3, d4)
  if not G.narrow(win.bx, win.by, win.bw, win.bh) then return end
  G.fill(win.bx, win.by, win.bw, win.bh, T.win)
  win.hits = {}
  win.c.bg = T.win
  if win.app.draw then
    local ok, err = pcall(win.app.draw, win, win.c)
    if not ok then
      G.text(win.bx + 1, win.by + 1, G.cut("Ошибка: " .. tostring(err), win.bw - 2), T.err, T.win)
    end
  end
  if win.resizable and not win.max then
    G.setClip(d1, d2, d3, d4)
    G.text(win.x + win.w - 1, win.y + win.h - 1, "◢", T.faint, T.win)
  end
end

local function drawDesktop()
  G.drawRuns(wall, 1)
  for i, ic in ipairs(icons) do
    local a = ic.app
    local sel = selIcon == i
    if sel then G.fill(ic.x, ic.y, 15, 7, T.sel) end
    G.sprite(ic.x + 3, ic.y, a.art or I.ART[a.icon] or I.ART.app, I.PAL)
    local lab = G.cut(a.name, 15)
    local lx = ic.x + floor((15 - G.len(lab)) / 2)
    if sel then G.text(lx, ic.y + 6, lab, T.fg, T.sel) else G.textOver(lx, ic.y + 6, lab, T.label) end
  end
  if wired.settings.wallpaper ~= "plain" and DH > 12 then
    local tag = "present day · present time"
    if wired.server then
      local name = (net and net.hostname and net.hostname()) or ""
      tag = (name ~= "" and (name .. " · ") or "") .. ((net and net.address and net.address():sub(1, 8)) or "") .. " · SWired"
    end
    G.textOver(G.W - G.len(tag) - 2, DH - 1, tag, wired.server and (T.dark and 0x2f5866 or 0x3f5a66) or (T.dark and 0x5a2c48 or 0x4b3f5a))
  end
end

local function drawBar()
  local y = DH + 1
  G.fill(1, y, G.W, TB, T.bar)
  barHits = {}
  local sb = menuOpen and T.accent or T.sel
  G.fill(1, y, 11, TB, sb)
  G.text(2, y, wired.server and "◆ SWIRED" or "◆ WIRED", menuOpen and T.ink or T.accent, sb)
  if TB > 1 then G.text(2, y + 1, "  пуск", menuOpen and T.ink or T.dim, sb) end
  barHits[#barHits + 1] = { 1, 11, "start" }
  local x = 13
  local trayW = 24
  for _, win in ipairs(wm.windowsInOrder()) do
    local title = G.cut(win.title, 16)
    local w = G.len(title) + 4
    if x + w > G.W - trayW then break end
    local act = win == wm.focus and not win.min
    local bg = act and T.hover or T.bar
    G.fill(x, y, w, TB, bg)
    local mini = I.MINI[win.icon] or I.MINI.app
    G.text(x + 1, y, mini[1], mini[2], bg)
    G.text(x + 3, y, title, (act and T.fg) or (win.min and T.faint) or T.dim, bg)
    if act then
      if TB > 1 then G.text(x + 1, y + 1, string.rep("▁", w - 2), T.accent, bg)
      else G.text(x, y, "▏", T.accent, bg) end
    end
    barHits[#barHits + 1] = { x, x + w - 1, win }
    x = x + w + 1
  end
  local user = (auth and auth.user()) or ""
  local tray = "⇅ сеть   " .. (user ~= "" and (user .. "   ") or "") .. os.date("%H:%M")
  G.text(G.W - G.len(tray) - 1, y, tray, T.dim, T.bar)
  if TB > 1 then
    local d = os.date("%d.%m.%Y")
    G.text(G.W - G.len(d) - 1, y + 1, d, T.faint, T.bar)
  end
end

--- Окна в порядке открытия (для панели задач).
function wm.windowsInOrder()
  local list = {}
  for _, w in ipairs(wm.windows) do list[#list + 1] = w end
  table.sort(list, function(a, b) return a.id < b.id end)
  return list
end

-- ---------- «Пуск» ----------
local function menuGeom()
  local mw = min(50, G.W - 2)
  local mh = min(DH, 30)
  return 1, DH - mh + 1, mw, mh
end

local function drawMenu()
  local x, y, w, h = menuGeom()
  menuRect = { x, y, w, h }
  menuHits = {}
  G.fill(x, y, w, h, T.menu)
  G.fill(x, y, w, 1, T.accent)
  local user = (auth and auth.user()) or "гость"
  G.half(x + 2, y + 2, 4, T.accent, T.accent)
  G.half(x + 2, y + 3, 4, T.accent, T.accentDim)
  G.text(x + 8, y + 2, user, T.fg, T.menu)
  local role = (auth and auth.open()) and "без пароля" or (wired.isAdmin() and "администратор" or "пользователь")
  G.text(x + 8, y + 3, G.cut(role .. " · " .. (os.getComputerLabel and os.getComputerLabel() or "компьютер"), w - 10), T.dim, T.menu)
  local col2 = x + floor(w * 0.56)
  local list = wired.apps.list()
  local places = wired.apps.places()
  local rows = h - 9                      -- строки y+6 … y+h-4
  local two = #list > rows                -- программ больше, чем строк: две колонки, папки — следом во второй
  local step = (not two and #list * 2 <= h - 10) and 2 or 1
  local cw = two and floor((w - 4) / 2) or (col2 - x - 2)
  G.text(x + 2, y + 5, "ПРОГРАММЫ", T.faint, T.menu)
  local function item(cx, cy, mark, color, name, hit)
    G.text(cx, cy, mark, color, T.menu)
    G.text(cx + 2, cy, G.cut(name, cw - 3), T.fg, T.menu)
    hit[1], hit[2], hit[3], hit[4] = cx - 1, cy, cx + cw - 2, cy
    menuHits[#menuHits + 1] = hit
  end
  for i, a in ipairs(list) do
    local cx, cy
    if two then
      if i > rows * 2 then break end
      cx, cy = i <= rows and x + 2 or x + 2 + cw, y + 6 + (i - 1) % rows
    else
      cx, cy = x + 2, y + 6 + (i - 1) * step
    end
    local mini = I.MINI[a.icon] or I.MINI.app
    item(cx, cy, mini[1], mini[2], a.name, { 0, 0, 0, 0, "app", a })
    if a.installed then G.text(cx + cw - 4, cy, "▣", T.faint, T.menu) end
  end
  local px, py0
  if two then
    px, py0 = x + 2 + cw, y + 6 + math.max(0, #list - rows) + 1
  else
    px, py0 = col2, y + 6
  end
  if py0 <= y + h - 5 then
    G.text(px, py0 - 1, "ПАПКИ", T.faint, T.menu)
    if two then cw = x + w - px - 1 else cw = x + w - col2 - 1 end
    for i, p in ipairs(places) do
      local py = py0 + (i - 1) * step
      if py > y + h - 4 then break end
      item(px, py, p.mark, p.color, p.name, { 0, 0, 0, 0, "place", p })
    end
  end
  G.text(x + 1, y + h - 3, string.rep("─", w - 2), T.line, T.menu)
  local bx = x + 2
  for _, b in ipairs({ { "⏻ Выключить", "off" }, { "↻ Перезагрузка", "reboot" }, { "⇥ Выйти", "logout" }, { "> Консоль", "console" } }) do
    if bx + G.len(b[1]) > x + w - 1 then break end
    G.text(bx, y + h - 2, b[1], T.dim, T.menu)
    menuHits[#menuHits + 1] = { bx, y + h - 2, bx + G.len(b[1]) - 1, y + h - 2, b[2] }
    bx = bx + G.len(b[1]) + 2
  end
end

function wm.toggleMenu(open)
  if open == nil then open = not menuOpen end
  if open == menuOpen then return end
  menuOpen = open
  local x, y, w, h = menuGeom()
  wm.invalidate(x, y, w, h)
  invalidateBar()
end

-- ---------- уведомления ----------
local NOTE_W = 38

function wm.notify(n)
  n.id = {}
  table.insert(wm.notes, 1, n)
  while #wm.notes > 3 do table.remove(wm.notes) end
  local timeout = n.timeout or (n.actions and 20 or 8)
  wm.after(timeout, function() wm.dismiss(n) end)
  wm.invalidate(G.W - NOTE_W - 1, 1, NOTE_W + 2, DH)
  if sound and sound.beep then pcall(sound.beep, 880, 0.05) end
end

function wm.dismiss(n)
  for i, m in ipairs(wm.notes) do
    if m == n then table.remove(wm.notes, i) break end
  end
  wm.invalidate(G.W - NOTE_W - 1, 1, NOTE_W + 2, DH)
end

local function drawNotes()
  noteHits = {}
  local y = DH
  for _, n in ipairs(wm.notes) do
    local h = n.actions and 6 or 4
    y = y - h - 1
    if y < 1 then break end
    local x = G.W - NOTE_W - 1
    G.fill(x, y, NOTE_W, h, T.title)
    G.fill(x, y, 1, h, T.accent)
    G.text(x + 2, y + 1, G.cut(n.title or "", NOTE_W - 6), T.fg, T.title)
    G.text(x + NOTE_W - 2, y + 1, "✕", T.faint, T.title)
    noteHits[#noteHits + 1] = { x + NOTE_W - 3, y + 1, x + NOTE_W - 1, y + 1, n, "close" }
    G.text(x + 2, y + 2, G.cut(n.text or "", NOTE_W - 3), T.dim, T.title)
    if n.actions then
      local bx = x + 2
      for i, a in ipairs(n.actions) do
        local label = " " .. a[1] .. " "
        G.text(bx, y + 4, label, i == 1 and T.ink or T.fg, i == 1 and T.accent or T.btn)
        noteHits[#noteHits + 1] = { bx, y + 4, bx + G.len(label) - 1, y + 4, n, a[2] }
        bx = bx + G.len(label) + 1
      end
    end
    noteHits[#noteHits + 1] = { x, y, x + NOTE_W - 1, y + h - 1, n, "body" }
  end
end

-- ---------- вход ----------
local login = {}

local function drawLogin()
  G.drawRuns(wall, 1)
  G.fill(1, DH + 1, G.W, TB, T.bar)
  local w, h = min(46, G.W - 4), 13
  local x, y = floor((G.W - w) / 2) + 1, max(1, floor((DH - h) / 2))
  G.fill(x, y, w, h, T.win)
  G.fill(x, y, w, 1, T.accent)
  G.text(x + 3, y + 2, wired.server and "◆ SWIRED" or "◆ WIRED", T.accent, T.win)
  local name = os.getComputerLabel and os.getComputerLabel() or ""
  G.text(x + 3, y + 3, G.cut("Вход" .. (name ~= "" and (" в " .. name) or ""), w - 6), T.dim, T.win)
  login.hits = {}
  local function field(fy, label, f)
    G.text(x + 3, fy, label, T.dim, T.win)
    G.fill(x + 16, fy, w - 19, 1, T.field)
    local text = f.mask and string.rep("•", G.len(f.text)) or f.text
    G.text(x + 17, fy, G.cut(text, w - 22), T.fg, T.field)
    if f.focused then G.text(x + 17 + min(G.len(text), w - 22), fy, " ", T.field, T.accent) end
    login.hits[#login.hits + 1] = { x + 16, fy, x + w - 4, fy, f }
  end
  field(y + 5, "Пользователь", login.user)
  field(y + 7, "Пароль", login.pass)
  if login.msg then G.text(x + 3, y + 9, G.cut(login.msg, w - 6), T.err, T.win) end
  local label = " Войти "
  G.text(x + w - 3 - G.len(label), y + 11, label, T.ink, T.accent)
  login.hits[#login.hits + 1] = { x + w - 3 - G.len(label), y + 11, x + w - 4, y + 11, "go" }
  local tray = os.date("%H:%M")
  G.text(G.W - G.len(tray) - 1, DH + 1, tray, T.dim, T.bar)
end

function wm.startLogin()
  wm.mode = "login"
  login.user = U.field("", "user")
  login.pass = U.field("", "pass", "•")
  login.user.focused = true
  login.msg = nil
  wm.invalidateAll()
end

local function tryLogin()
  local ok, why, wait, n = auth.login(login.user.text, login.pass.text)
  if ok then
    wm.mode = "desk"
    layoutIcons()
    wm.invalidateAll()
    if wired.onLogin then wired.onLogin() end
    return
  end
  login.pass.text, login.pass.pos = "", 0
  if why == "locked" then login.msg = "Много ошибок — вход закрыт на " .. math.ceil((wait or 600) / 60) .. " мин"
  elseif why == "wait" then login.msg = "Подождите " .. (wait or 2) .. " с"
  else login.msg = "Неверный пароль (" .. (n or 1) .. " из 5)" end
  wm.invalidateAll()
end

local function loginEvent(ev)
  local name = ev[1]
  if name == "mouse_click" then
    for _, h in ipairs(login.hits or {}) do
      if ev[3] >= h[1] and ev[3] <= h[3] and ev[4] == h[2] then
        if h[5] == "go" then tryLogin()
        else
          login.user.focused, login.pass.focused = h[5] == login.user, h[5] == login.pass
          wm.invalidateAll()
        end
      end
    end
  elseif name == "char" or name == "key" or name == "paste" then
    local f = login.user.focused and login.user or login.pass
    local r = U.edit(f, name, ev[2])
    if r == "tab" or (r == "enter" and f == login.user) then
      login.user.focused, login.pass.focused = not login.user.focused, login.user.focused
    elseif r == "enter" then
      tryLogin()
      return
    end
    wm.invalidateAll()
  end
end

-- ======================= отрисовка кадра =======================

function wm.render()
  if not dirty then return end
  local x1, y1 = max(1, dirty[1]), max(1, dirty[2])
  local x2, y2 = min(G.W, dirty[3]), min(G.H, dirty[4])
  dirty = nil
  if x2 < x1 or y2 < y1 then return end
  if wm.mode == "login" then
    G.setClip(1, 1, G.W, G.H)
    drawLogin()
    G.noclip()
    G.present(1, 1, G.W, G.H)
    return
  end
  G.setClip(x1, y1, x2, y2)
  drawDesktop()
  for _, win in ipairs(wm.windows) do
    if not win.min then drawWindow(win, x1, y1, x2, y2) end
  end
  G.setClip(x1, y1, x2, y2)
  drawBar()
  if menuOpen then G.setClip(x1, y1, x2, y2) drawMenu() end
  G.setClip(x1, y1, x2, y2)
  drawNotes()
  G.noclip()
  G.present(x1, y1, x2 - x1 + 1, y2 - y1 + 1)
end

-- ======================= события =======================

local function inside(win, x, y)
  return x >= win.x and x < win.x + win.w and y >= win.y and y < win.y + win.h
end

local function windowAt(x, y)
  for i = #wm.windows, 1, -1 do
    local w = wm.windows[i]
    if not w.min and inside(w, x, y) then return w end
  end
end

local function deliver(win, ...)
  if win.app.event then
    local ok, err = pcall(win.app.event, win, ...)
    if not ok then
      wm.notify({ title = "Ошибка в «" .. win.title .. "»", text = tostring(err) })
    end
  end
end
wm.deliver = deliver

local function isDouble(key)
  local now = computer.uptime()
  local d = lastClick.key == key and now - lastClick.t < 0.45
  lastClick.key, lastClick.t = key, d and 0 or now
  return d
end

local function menuAction(h)
  wm.toggleMenu(false)
  local kind, data = h[5], h[6]
  if kind == "app" then wired.apps.launch(data)
  elseif kind == "place" then wired.apps.launch(wired.apps.get("files"), data.path)
  elseif kind == "off" then wired.quit("off")
  elseif kind == "reboot" then wired.quit("reboot")
  elseif kind == "logout" then wired.logout()
  elseif kind == "console" then wired.quit("console") end
end

local function mouseDown(btn, x, y)
  if wm.mode == "login" then return end
  if menuOpen then
    local r = menuRect
    if r and x >= r[1] and x < r[1] + r[3] and y >= r[2] and y < r[2] + r[4] then
      for _, h in ipairs(menuHits) do
        if x >= h[1] and x <= h[3] and y >= h[2] and y <= h[4] then menuAction(h) return end
      end
      return
    end
    wm.toggleMenu(false)
    if y > DH and x <= 11 then return end
  end
  for _, h in ipairs(noteHits) do
    if x >= h[1] and x <= h[3] and y >= h[2] and y <= h[4] then
      local n, act = h[5], h[6]
      if act == "close" then wm.dismiss(n)
      elseif type(act) == "function" then wm.dismiss(n); act()
      elseif act == "body" and n.onClick then wm.dismiss(n); n.onClick() end
      return
    end
  end
  if y > DH then
    for _, h in ipairs(barHits) do
      if x >= h[1] and x <= h[2] then
        if h[3] == "start" then wm.toggleMenu()
        else
          local win = h[3]
          if win == wm.focus and not win.min then wm.minimize(win) else wm.focusWin(win) end
        end
        return
      end
    end
    return
  end
  local win = windowAt(x, y)
  if win then
    wm.focusWin(win)
    if y == win.y then
      if x >= win.x + win.w - 3 then wm.close(win)
      elseif x >= win.x + win.w - 7 and x <= win.x + win.w - 5 then wm.toggleMax(win)
      elseif x >= win.x + win.w - 10 and x <= win.x + win.w - 8 then wm.minimize(win)
      elseif isDouble(win) then wm.toggleMax(win)
      elseif not win.max then drag = { win = win, dx = x - win.x, dy = y - win.y, kind = "move" } end
      return
    end
    if win.resizable and not win.max and x == win.x + win.w - 1 and y == win.y + win.h - 1 then
      drag = { win = win, kind = "size" }
      return
    end
    local lx, ly = x - win.bx + 1, y - win.by + 1
    press = win
    for i = #win.hits, 1, -1 do
      local h = win.hits[i]
      if lx >= h[1] and lx <= h[3] and ly >= h[2] and ly <= h[4] then
        local dbl = isDouble(tostring(win.id) .. "/" .. tostring(h[5]) .. "/" .. tostring(h[6]))
        deliver(win, dbl and "dblclick" or "click", h[5], h[6], lx, ly, btn)
        return
      end
    end
    isDouble(nil)
    deliver(win, "mouse_click", btn, lx, ly)
    return
  end
  -- рабочий стол
  local hit
  for i, ic in ipairs(icons) do
    if x >= ic.x and x < ic.x + 15 and y >= ic.y and y < ic.y + 7 then hit = i break end
  end
  if hit ~= selIcon then
    for _, i in ipairs({ hit, selIcon }) do
      if icons[i] then wm.invalidate(icons[i].x, icons[i].y, 15, 7) end
    end
    selIcon = hit
  end
  if wm.focus then local f = wm.focus; wm.focus = nil; wm.redraw(f); invalidateBar() end
  if hit and isDouble("icon" .. hit) then wired.apps.launch(icons[hit].app) end
end

local function mouseDrag(btn, x, y)
  if drag then
    local win = drag.win
    wm.invalidateWin(win)
    if drag.kind == "move" then
      win.x = max(4 - win.w, min(G.W - 3, x - drag.dx))
      win.y = max(1, min(DH, y - drag.dy))
    else
      win.w = max(win.minW, min(G.W - win.x + 1, x - win.x + 1))
      win.h = max(win.minH, min(DH - win.y + 1, y - win.y + 1))
    end
    geom(win)
    if drag.kind == "size" then deliver(win, "resize") end
    wm.invalidateWin(win)
    return
  end
  if press and not press.closed then deliver(press, "mouse_drag", btn, x - press.bx + 1, y - press.by + 1) end
end

local function mouseUp(btn, x, y)
  if drag then drag = nil return end
  if press and not press.closed then deliver(press, "mouse_up", btn, x - press.bx + 1, y - press.by + 1) end
  press = nil
end

function wm.handle(ev)
  local name = ev[1]
  if name == "timer" and timers[ev[2]] then
    local fn = timers[ev[2]]
    timers[ev[2]] = nil
    local ok, err = pcall(fn)
    if not ok then wm.notify({ title = "Ошибка", text = tostring(err) }) end
    return
  end
  if name == "screen_resized" then
    local w, h = G.screenSize()
    if w ~= G.W or h ~= G.H then wm.layout() end
    return
  end
  if wm.mode == "login" then loginEvent(ev) return end
  if name == "mouse_click" then mouseDown(ev[2], ev[3], ev[4])
  elseif name == "mouse_drag" then mouseDrag(ev[2], ev[3], ev[4])
  elseif name == "mouse_up" then mouseUp(ev[2], ev[3], ev[4])
  elseif name == "mouse_scroll" then
    local win = windowAt(ev[3], ev[4])
    if win then deliver(win, "scroll", ev[2], ev[3] - win.bx + 1, ev[4] - win.by + 1) end
  elseif name == "key" or name == "char" or name == "paste" or name == "key_up" then
    if menuOpen and name == "key" and ev[2] == "escape" then wm.toggleMenu(false) return end
    if name == "key" and ev[2] == "f4" and ev[3] and wm.focus then wm.close(wm.focus) return end
    if wm.focus then deliver(wm.focus, name, ev[2], ev[3], ev[4]) end
  else
    if wired.onEvent then wired.onEvent(ev) end
    feedTasks(ev)
    for _, win in ipairs(wm.windows) do deliver(win, table.unpack(ev, 1, ev.n)) end
  end
end

-- ======================= диалоги =======================

local function dialog(title, w, h, draw, event)
  local app = { title = title, icon = "app", w = w, h = h, resizable = false, draw = draw, event = event }
  return wm.open(app)
end

--- Спросить строку: fn(текст) или fn(nil) при отмене. mask — знак вместо букв (для пароля).
function wm.prompt(title, label, default, fn, mask)
  local f = U.field(default or "", "f", mask)
  f.focused = true
  f.fresh = (default or "") ~= ""      -- первая буква заменяет подсказанный текст, стрелки — правят его
  local win
  local function done(v) wm.close(win, true) fn(v) end
  win = dialog(title, min(56, G.W - 2), 7, function(w, c)
    c.text(3, 2, G.cut(label, c.w - 4), T.fg)
    c.field(3, 3, c.w - 4, f)
    local bx = c.w - 22
    bx = c.button(bx, 5, "Отмена", "no") + 1
    c.button(bx, 5, "OK", "ok", "primary")
  end, function(w, ev, a)
    if ev == "click" then
      if a == "ok" then done(f.text) elseif a == "no" then done(nil) end
    elseif ev == "key" and a == "escape" then done(nil)
    elseif ev == "char" or ev == "key" or ev == "paste" then
      if f.fresh then
        if ev == "char" or ev == "paste" or (ev == "key" and (a == "backspace" or a == "delete")) then
          f.text, f.pos = "", 0
          if ev == "key" then f.fresh = false wm.redraw(w) return end
        end
        if ev ~= "key" or a ~= "enter" then f.fresh = false end
      end
      if U.edit(f, ev, a) == "enter" then done(f.text) return end
      wm.redraw(w)
    end
  end)
  win.app.close = function() fn(nil) end
  return win
end

--- Подтверждение: fn(true/false).
function wm.confirm(title, text, fn, yes)
  local win
  local function done(v) wm.close(win, true) fn(v) end
  win = dialog(title, min(56, G.W - 2), 7, function(w, c)
    c.text(3, 2, G.cut(text, c.w - 4), T.fg)
    local bx = c.w - 24
    bx = c.button(bx, 5, "Отмена", "no") + 1
    c.button(bx, 5, yes or "Да", "ok", "primary")
  end, function(w, ev, a)
    if ev == "click" then done(a == "ok")
    elseif ev == "key" and (a == "enter" or a == "escape") then done(a == "enter") end
  end)
  win.app.close = function() fn(false) end
  return win
end

function wm.alert(title, text)
  local win
  win = dialog(title, min(56, G.W - 2), 7, function(w, c)
    c.text(3, 2, G.cut(text, c.w - 4), T.fg)
    c.button(c.w - 7, 5, "OK", "ok", "primary")
  end, function(w, ev, a)
    if ev == "click" or (ev == "key" and (a == "enter" or a == "escape")) then wm.close(win, true) end
  end)
  return win
end

return wm
