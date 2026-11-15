-- Wired: Игры — Сапёр, Змейка, Тетрис. Рекорды хранятся в /home/.games.
local G, T, U, wm = wired.gfx, wired.theme, wired.ui, wired.wm
local SCORES = "/home/.games"

local function loadScores()
  if not fs.exists(SCORES) then return {} end
  local f = fs.open(SCORES, "r")
  local fn = load("return " .. (f.readAll() or ""), "scores", "t", {})
  f.close()
  local ok, t = pcall(fn or function() end)
  return ok and type(t) == "table" and t or {}
end

local function saveScore(key, value, lowerBetter)
  local s = loadScores()
  local old = s[key]
  local better = not old or (lowerBetter and value < old) or (not lowerBetter and value > old)
  if better then
    s[key] = value
    local f = fs.open(SCORES, "w")
    if f then f.write(textutils.serialize(s)) f.close() end
  end
  return better, old
end

-- ======================= Сапёр =======================
local Mines = {}
Mines.LEVELS = { { "Новичок", 9, 9, 10 }, { "Любитель", 16, 12, 30 }, { "Профи", 24, 14, 60 } }

function Mines.new(level)
  local L = Mines.LEVELS[level]
  local g = { kind = "mines", level = level, w = L[2], h = L[3], mines = L[4], cells = {}, state = "ready",
    opened = 0, flags = 0, t0 = nil, t1 = nil }
  for i = 1, g.w * g.h do g.cells[i] = { mine = false, open = false, flag = false, n = 0 } end
  return g
end

local function cellAt(g, x, y)
  if x < 1 or y < 1 or x > g.w or y > g.h then return nil end
  return g.cells[(y - 1) * g.w + x]
end

function Mines.plant(g, sx, sy)
  local placed = 0
  while placed < g.mines do
    local x, y = math.random(1, g.w), math.random(1, g.h)
    local c = cellAt(g, x, y)
    if not c.mine and (math.abs(x - sx) > 1 or math.abs(y - sy) > 1) then c.mine = true placed = placed + 1 end
  end
  for y = 1, g.h do for x = 1, g.w do
    local n = 0
    for dy = -1, 1 do for dx = -1, 1 do
      local o = cellAt(g, x + dx, y + dy)
      if o and o.mine then n = n + 1 end
    end end
    cellAt(g, x, y).n = n
  end end
end

function Mines.open(g, x, y)
  local c = cellAt(g, x, y)
  if not c or c.open or c.flag then return end
  if g.state == "ready" then Mines.plant(g, x, y) g.state = "play" g.t0 = computer.uptime() end
  if g.state ~= "play" then return end
  if c.mine then
    c.open = true
    c.boom = true
    g.state = "lost"
    g.t1 = computer.uptime()
    for _, o in ipairs(g.cells) do if o.mine then o.open = true end end
    return
  end
  local stack = { { x, y } }
  while #stack > 0 do
    local p = table.remove(stack)
    local o = cellAt(g, p[1], p[2])
    if o and not o.open and not o.flag and not o.mine then
      o.open = true
      g.opened = g.opened + 1
      if o.n == 0 then
        for dy = -1, 1 do for dx = -1, 1 do stack[#stack + 1] = { p[1] + dx, p[2] + dy } end end
      end
    end
  end
  if g.opened == g.w * g.h - g.mines then
    g.state = "won"
    g.t1 = computer.uptime()
    g.record = saveScore("mines" .. g.level, math.floor(g.t1 - g.t0), true)
  end
end

--- Открытая цифра: если флажков вокруг столько же — открыть остальных соседей.
function Mines.chord(g, x, y)
  local c = cellAt(g, x, y)
  if not c or not c.open or c.n == 0 then return end
  local f = 0
  for dy = -1, 1 do for dx = -1, 1 do local o = cellAt(g, x + dx, y + dy) if o and o.flag then f = f + 1 end end end
  if f == c.n then
    for dy = -1, 1 do for dx = -1, 1 do Mines.open(g, x + dx, y + dy) end end
  end
end

local NUMC = { 0x6b95ff, 0x5fd38a, 0xff4f5e, 0xc77dff, 0xff8a3d, 0x4fd1e8, 0xece8f4, 0x8d87a3 }

function Mines.draw(g, c, x0, y0)
  for y = 1, g.h do
    for x = 1, g.w do
      local cell = cellAt(g, x, y)
      local cx, cy = x0 + (x - 1) * 3, y0 + y - 1
      if cell.open then
        local bg = cell.boom and 0xff4f5e or ((x + y) % 2 == 0 and 0x2a2638 or 0x25213a)
        if cell.mine then c.text(cx, cy, " ✹ ", cell.boom and 0x000000 or 0xff4f5e, bg)
        elseif cell.n > 0 then c.text(cx, cy, " " .. cell.n .. " ", NUMC[cell.n], bg)
        else c.fill(cx, cy, 3, 1, bg) end
      else
        local bg = (x + y) % 2 == 0 and 0x4d4763 or 0x5d5775
        if cell.flag then c.text(cx, cy, " ⚑ ", g.state == "lost" and not cell.mine and 0x000000 or 0xff4f5e, bg)
        else c.fill(cx, cy, 3, 1, bg) end
      end
    end
  end
end

function Mines.click(g, lx, ly, btn, x0, y0)
  local x, y = math.floor((lx - x0) / 3) + 1, ly - y0 + 1
  local c = cellAt(g, x, y)
  if not c or g.state == "won" or g.state == "lost" then return end
  if btn == 2 then
    if not c.open and g.state ~= "ready" then
      c.flag = not c.flag
      g.flags = g.flags + (c.flag and 1 or -1)
    end
  elseif c.open then Mines.chord(g, x, y)
  else Mines.open(g, x, y) end
end

-- ======================= Змейка =======================
local Snake = {}

function Snake.new(w, h)
  local g = { kind = "snake", w = w, h = h, body = {}, dir = { 1, 0 }, next = { 1, 0 }, state = "ready", score = 0, speed = 0.18 }
  local cx, cy = math.floor(w / 2), math.floor(h / 2)
  for i = 0, 3 do g.body[#g.body + 1] = { cx - i, cy } end
  Snake.food(g)
  return g
end

function Snake.food(g)
  while true do
    local x, y = math.random(1, g.w), math.random(1, g.h)
    local free = true
    for _, p in ipairs(g.body) do if p[1] == x and p[2] == y then free = false break end end
    if free then g.apple = { x, y } return end
  end
end

function Snake.step(g)
  if g.state ~= "play" then return end
  g.dir = g.next
  local head = g.body[1]
  local nx, ny = head[1] + g.dir[1], head[2] + g.dir[2]
  if nx < 1 or ny < 1 or nx > g.w or ny > g.h then g.state = "lost" end
  for i = 1, #g.body - 1 do
    local p = g.body[i]
    if p[1] == nx and p[2] == ny then g.state = "lost" end
  end
  if g.state == "lost" then
    g.record = saveScore("snake", g.score)
    return
  end
  table.insert(g.body, 1, { nx, ny })
  if nx == g.apple[1] and ny == g.apple[2] then
    g.score = g.score + 10
    g.speed = math.max(0.07, g.speed - 0.005)
    Snake.food(g)
  else
    table.remove(g.body)
  end
end

function Snake.key(g, k)
  local d = ({ up = { 0, -1 }, w = { 0, -1 }, down = { 0, 1 }, s = { 0, 1 }, left = { -1, 0 }, a = { -1, 0 }, right = { 1, 0 }, d = { 1, 0 } })[k]
  if d then
    if g.state == "ready" then g.state = "play" end
    if d[1] ~= -g.dir[1] or d[2] ~= -g.dir[2] then g.next = d end
    return true
  end
  if k == "space" and g.state == "play" then g.state = "pause" return true end
  if k == "space" and g.state == "pause" then g.state = "play" return true end
end

function Snake.draw(g, c, x0, y0)
  c.fill(x0, y0, g.w * 2, g.h, 0x0f0c17)
  c.text(x0 + (g.apple[1] - 1) * 2, y0 + g.apple[2] - 1, "██", 0xff4f5e, 0x0f0c17)
  for i, p in ipairs(g.body) do
    local col = i == 1 and 0x8dff9a or (i % 2 == 0 and 0x5fd38a or 0x4fbf7a)
    c.text(x0 + (p[1] - 1) * 2, y0 + p[2] - 1, "██", col, 0x0f0c17)
  end
end

-- ======================= Тетрис =======================
local Tet = {}
Tet.SHAPES = {
  { { 0, 1 }, { 1, 1 }, { 2, 1 }, { 3, 1 }, c = 0x4fd1e8, n = 4 },   -- I
  { { 0, 0 }, { 1, 0 }, { 0, 1 }, { 1, 1 }, c = 0xffc857, n = 2 },   -- O
  { { 1, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, c = 0xc77dff, n = 3 },   -- T
  { { 1, 0 }, { 2, 0 }, { 0, 1 }, { 1, 1 }, c = 0x5fd38a, n = 3 },   -- S
  { { 0, 0 }, { 1, 0 }, { 1, 1 }, { 2, 1 }, c = 0xff4f5e, n = 3 },   -- Z
  { { 0, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, c = 0x6b95ff, n = 3 },   -- J
  { { 2, 0 }, { 0, 1 }, { 1, 1 }, { 2, 1 }, c = 0xff8a3d, n = 3 },   -- L
}

function Tet.new()
  local g = { kind = "tetris", w = 10, h = 20, grid = {}, state = "ready", score = 0, lines = 0, level = 1 }
  for i = 1, g.w * g.h do g.grid[i] = false end
  g.nextShape = math.random(1, #Tet.SHAPES)
  Tet.spawn(g)
  return g
end

function Tet.cells(p)
  local s = Tet.SHAPES[p.s]
  local out = {}
  for i = 1, 4 do
    local x, y = s[i][1], s[i][2]
    for _ = 1, p.r % 4 do x, y = s.n - 1 - y, x end
    out[i] = { p.x + x, p.y + y }
  end
  return out
end

function Tet.fits(g, p)
  for _, q in ipairs(Tet.cells(p)) do
    if q[1] < 0 or q[1] >= g.w or q[2] >= g.h then return false end
    if q[2] >= 0 and g.grid[q[2] * g.w + q[1] + 1] then return false end
  end
  return true
end

function Tet.spawn(g)
  g.p = { s = g.nextShape, r = 0, x = 3, y = -1 }
  g.nextShape = math.random(1, #Tet.SHAPES)
  if not Tet.fits(g, g.p) then
    g.state = "lost"
    g.record = saveScore("tetris", g.score)
  end
end

function Tet.lock(g)
  local col = Tet.SHAPES[g.p.s].c
  for _, q in ipairs(Tet.cells(g.p)) do
    if q[2] >= 0 then g.grid[q[2] * g.w + q[1] + 1] = col end
  end
  local cleared = 0
  for y = g.h - 1, 0, -1 do
    local full = true
    for x = 0, g.w - 1 do if not g.grid[y * g.w + x + 1] then full = false break end end
    if full then
      cleared = cleared + 1
      for yy = y, 1, -1 do for x = 0, g.w - 1 do g.grid[yy * g.w + x + 1] = g.grid[(yy - 1) * g.w + x + 1] end end
      for x = 0, g.w - 1 do g.grid[x + 1] = false end
    end
  end
  -- если строки сдвинулись, проверяем ту же строку снова
  if cleared > 0 then
    local again = true
    while again do
      again = false
      for y = g.h - 1, 0, -1 do
        local full = true
        for x = 0, g.w - 1 do if not g.grid[y * g.w + x + 1] then full = false break end end
        if full then
          cleared = cleared + 1
          for yy = y, 1, -1 do for x = 0, g.w - 1 do g.grid[yy * g.w + x + 1] = g.grid[(yy - 1) * g.w + x + 1] end end
          for x = 0, g.w - 1 do g.grid[x + 1] = false end
          again = true
          break
        end
      end
    end
  end
  g.lines = g.lines + cleared
  g.score = g.score + ({ 0, 100, 300, 500, 800 })[math.min(cleared, 4) + 1] * g.level
  g.level = 1 + math.floor(g.lines / 10)
  Tet.spawn(g)
end

function Tet.move(g, dx, dy, dr)
  local p = { s = g.p.s, r = g.p.r + (dr or 0), x = g.p.x + dx, y = g.p.y + dy }
  if Tet.fits(g, p) then g.p = p return true end
  if dr then
    for _, kick in ipairs({ -1, 1, -2, 2 }) do
      p.x = g.p.x + kick
      if Tet.fits(g, p) then g.p = p return true end
    end
  end
  return false
end

function Tet.step(g)
  if g.state ~= "play" then return end
  if not Tet.move(g, 0, 1) then Tet.lock(g) end
end

function Tet.speed(g) return math.max(0.08, 0.6 - (g.level - 1) * 0.05) end

function Tet.key(g, k)
  if g.state == "ready" and (k == "space" or k == "enter" or k == "left" or k == "right" or k == "up" or k == "down") then
    g.state = "play" return true
  end
  if k == "p" or (k == "escape" and g.state == "play") then g.state = g.state == "pause" and "play" or "pause" return true end
  if g.state ~= "play" then return end
  if k == "left" or k == "a" then Tet.move(g, -1, 0)
  elseif k == "right" or k == "d" then Tet.move(g, 1, 0)
  elseif k == "down" or k == "s" then if Tet.move(g, 0, 1) then g.score = g.score + 1 end
  elseif k == "up" or k == "w" then Tet.move(g, 0, 0, 1)
  elseif k == "space" then
    local n = 0
    while Tet.move(g, 0, 1) do n = n + 1 end
    g.score = g.score + n * 2
    Tet.lock(g)
  else return end
  return true
end

function Tet.draw(g, c, x0, y0)
  c.fill(x0 - 1, y0, g.w * 2 + 2, g.h, 0x2e2842)
  c.fill(x0, y0, g.w * 2, g.h, 0x0f0c17)
  for y = 0, g.h - 1 do for x = 0, g.w - 1 do
    local col = g.grid[y * g.w + x + 1]
    if col then c.text(x0 + x * 2, y0 + y, "▓▓", col, 0x0f0c17) end
  end end
  if g.state ~= "lost" then
    -- тень
    local ghost = { s = g.p.s, r = g.p.r, x = g.p.x, y = g.p.y }
    while Tet.fits(g, { s = ghost.s, r = ghost.r, x = ghost.x, y = ghost.y + 1 }) do ghost.y = ghost.y + 1 end
    for _, q in ipairs(Tet.cells(ghost)) do
      if q[2] >= 0 then c.text(x0 + q[1] * 2, y0 + q[2], "░░", Tet.SHAPES[g.p.s].c, 0x0f0c17) end
    end
    for _, q in ipairs(Tet.cells(g.p)) do
      if q[2] >= 0 then c.text(x0 + q[1] * 2, y0 + q[2], "██", Tet.SHAPES[g.p.s].c, 0x0f0c17) end
    end
  end
end

-- ======================= окно =======================
return function()
  local app = { title = "Игры", icon = "games", w = 80, h = 28, minW = 64, minH = 24 }
  local st = { game = nil, mineLevel = 1 }
  local win, timer

  local function schedule()
    local g = st.game
    if not g or win.closed then return end
    local delay = g.kind == "snake" and g.speed or g.kind == "tetris" and Tet.speed(g) or 1
    timer = wm.after(delay, function()
      if win.closed or st.game ~= g then return end
      if g.kind == "snake" then Snake.step(g)
      elseif g.kind == "tetris" then Tet.step(g) end
      wm.redraw(win)
      schedule()
    end)
  end

  local function start(kind)
    if kind == "mines" then st.game = Mines.new(st.mineLevel)
    elseif kind == "snake" then st.game = Snake.new(math.min(30, math.floor((win.c.w - 22) / 2)), math.min(20, win.c.h - 4))
    elseif kind == "tetris" then st.game = Tet.new() end
    wm.setTitle(win, "Игры — " .. ({ mines = "Сапёр", snake = "Змейка", tetris = "Тетрис" })[kind])
    schedule()
  end

  function app.init(w) win = w end

  local function boardOrigin(c)
    local g = st.game
    if g.kind == "mines" then return 3, 3
    elseif g.kind == "snake" then return 3, 3
    else return math.floor((c.w - 20 - 22) / 2) + 2, 2 end
  end

  local function side(c, x, lines)
    for i, l in ipairs(lines) do c.text(x, 2 + i, l[1], l[2] or T.fg) end
  end

  function app.draw(w, c)
    local g = st.game
    if not g then
      c.text(3, 2, "Во что сыграем?", T.fg)
      local scores = loadScores()
      local cards = {
        { "mines", "✹  Сапёр", "Откройте все клетки без мин. Цифра — сколько мин рядом.",
          "Левая кнопка — открыть, правая — флажок",
          scores.mines1 and ("Рекорд новичка: " .. scores.mines1 .. " с") or "Рекордов пока нет" },
        { "snake", "██ Змейка", "Ешьте яблоки и растите, не врезаясь в стены и в себя.",
          "Стрелки или WASD, пробел — пауза",
          scores.snake and ("Рекорд: " .. scores.snake .. " очков") or "Рекордов пока нет" },
        { "tetris", "▓▓ Тетрис", "Собирайте сплошные линии из падающих фигур.",
          "←→ — двигать, ↑ — повернуть, ↓ — быстрее, пробел — сбросить",
          scores.tetris and ("Рекорд: " .. scores.tetris .. " очков") or "Рекордов пока нет" },
      }
      for i, card in ipairs(cards) do
        local y = 4 + (i - 1) * 6
        c.fill(3, y, c.w - 4, 5, T.sel)
        c.text(5, y + 1, card[2], T.accent, T.sel)
        c.text(5, y + 2, G.cut(card[3], c.w - 22), T.fg, T.sel)
        c.text(5, y + 3, G.cut(card[4], c.w - 22), T.dim, T.sel)
        c.text(c.w - 2 - G.len(card[5]), y + 1, card[5], T.faint, T.sel)
        c.button(c.w - 12, y + 3, "Играть", "start", "primary", card[1])
        c.hit(3, y, c.w - 18, 5, "start", card[1])
      end
      local lx = 5
      c.text(lx, 22, "Сапёр:", T.dim)
      lx = lx + 7
      for i, L in ipairs(Mines.LEVELS) do
        c.radio(lx, 22, L[1], st.mineLevel == i, "lvl" .. i)
        lx = lx + G.len(L[1]) + 4
      end
      return
    end
    local x0, y0 = boardOrigin(c)
    local sx
    if g.kind == "mines" then
      Mines.draw(g, c, x0, y0)
      sx = x0 + g.w * 3 + 3
      local t = g.t0 and math.floor((g.t1 or computer.uptime()) - g.t0) or 0
      side(c, sx, {
        { "Сапёр · " .. Mines.LEVELS[g.level][1], T.accent },
        { "Мин: " .. (g.mines - g.flags) },
        { "Время: " .. t .. " с" },
        { "" },
        { g.state == "won" and "Победа!" or g.state == "lost" and "Бум! Мина." or "", g.state == "won" and T.ok or T.err },
        { g.state == "won" and g.record and "Новый рекорд!" or "", T.warn },
      })
    elseif g.kind == "snake" then
      Snake.draw(g, c, x0, y0)
      sx = x0 + g.w * 2 + 3
      side(c, sx, {
        { "Змейка", T.accent }, { "Очки: " .. g.score }, { "Длина: " .. #g.body }, { "" },
        { g.state == "ready" and "Нажмите стрелку" or g.state == "pause" and "Пауза" or g.state == "lost" and "Конец игры" or "", g.state == "lost" and T.err or T.dim },
        { g.state == "lost" and g.record and "Новый рекорд!" or "", T.warn },
      })
    else
      Tet.draw(g, c, x0, y0)
      sx = x0 + g.w * 2 + 3
      side(c, sx, {
        { "Тетрис", T.accent }, { "Очки: " .. g.score }, { "Линии: " .. g.lines }, { "Уровень: " .. g.level }, { "" },
        { "Следующая:", T.dim },
      })
      local s = Tet.SHAPES[g.nextShape]
      for i = 1, 4 do c.text(sx + s[i][1] * 2, 10 + s[i][2], "██", s.c) end
      local msg = g.state == "ready" and "Нажмите пробел" or g.state == "pause" and "Пауза (P)" or g.state == "lost" and "Конец игры" or ""
      c.text(sx, 13, msg, g.state == "lost" and T.err or T.dim)
      if g.state == "lost" and g.record then c.text(sx, 14, "Новый рекорд!", T.warn) end
    end
    local by = c.h - 1
    local bx = c.button(sx, by, "Заново", "again", "primary") + 1
    c.button(bx, by, "Меню", "menu")
  end

  function app.event(w, ev, a, b, lx, ly, btn)
    local g = st.game
    if ev == "click" or ev == "dblclick" then
      if a == "start" then start(b)
      elseif a == "again" and g then start(g.kind)
      elseif a == "menu" then st.game = nil wm.setTitle(w, "Игры")
      elseif type(a) == "string" and a:match("^lvl%d$") then st.mineLevel = tonumber(a:sub(4)) end
      wm.redraw(w)
    elseif ev == "mouse_click" and g and g.kind == "mines" then
      local x0, y0 = boardOrigin(w.c)
      Mines.click(g, b, lx, a, x0, y0)
      wm.redraw(w)
    elseif ev == "key" and g then
      local used
      if g.kind == "snake" then
        if g.state == "ready" and not ({ up = 1, down = 1, left = 1, right = 1, w = 1, a = 1, s = 1, d = 1 })[a] then return end
        used = Snake.key(g, a)
      elseif g.kind == "tetris" then used = Tet.key(g, a) end
      if a == "enter" and g.state == "lost" then start(g.kind) used = true end
      if used then wm.redraw(w) end
    end
  end

  return app
end
