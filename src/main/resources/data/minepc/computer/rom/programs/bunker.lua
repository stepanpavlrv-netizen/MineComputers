-- bunker: 3D-стрелялка в духе Doom — показывает кадровый буфер видеокарты и ускорители отрисовки.
-- Управление: W/S — вперёд/назад, A/D — шаг вбок, ←/→ — поворот, пробел или Ctrl — выстрел, Q — выход.
--   bunker        — играть
--   bunker demo   — играет сам (автопилот), для показа
--   bunker bench  — замер: 90 кадров без ожидания, печатает кадры в секунду
local args = { ... }
local mode = args[1] or "play"
local gpu = component.gpu
if not gpu.canvas then
  print("Нужна видеокарта MineComputers с кадровым буфером")
  return
end

local floor, sqrt, abs, cos, sin, atan2, min, max = math.floor, math.sqrt, math.abs, math.cos, math.sin, math.atan2, math.min, math.max
local pi = math.pi
local uptime = computer.uptime

---------------------------------------------------------------- экран
local W, H = gpu.maxResolution()
local oldW, oldH = gpu.getResolution()
gpu.setResolution(W, H)
if gpu.maxDepth() >= 24 then pcall(gpu.setDepth, 24) end
local HUD = 2                                   -- строки текста внизу
local CW, CH = W, (H - HUD) * 2
local cv, err = gpu.canvas(CW, CH)
if not cv then
  gpu.setResolution(oldW, oldH)
  print("Не хватает видеопамяти для кадра " .. CW .. "×" .. CH .. ": " .. tostring(err))
  return
end
local HALF = CH / 2

---------------------------------------------------------------- звук (звуковой чип платы; без него игра идёт молча)
-- Каналы: 1 — выстрел, 2 — роботы, 3 — подбор и сообщения, 4 — шаги
local snd = (sound and sound.channels and sound.channels() >= 4) and sound or nil
local function sfx(ch, freq, sec, vol, wave, to)
  if snd then pcall(snd.play, ch, freq, sec, vol, wave, to) end
end
if snd then
  snd.setADSR(1, 1, 110, 0.15, 60)
  snd.setADSR(2, 3, 80, 0.5, 90)
  snd.setADSR(3, 2, 60, 0.7, 60)
  snd.setADSR(4, 1, 25, 0, 20)
  snd.process()
end

---------------------------------------------------------------- текстуры (рисуем сами, без файлов)
local TS = 32
local rnd = math.random
math.randomseed(7)

local function rgb(r, g, b)
  r = max(0, min(255, floor(r))); g = max(0, min(255, floor(g))); b = max(0, min(255, floor(b)))
  return r * 65536 + g * 256 + b
end

local function texture(w, h, f)
  local t = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do t[y * w + x + 1] = f(x, y) end
  end
  return assert(gpu.newTexture(w, h, t))
end

local brick = texture(TS, TS, function(x, y)
  local row = floor(y / 8)
  local off = (row % 2) * 8
  if y % 8 == 7 or (x + off) % 16 == 15 then return rgb(70 + rnd(0, 10), 64, 58) end
  local n = rnd(-14, 14) + ((x + off) % 16 < 2 and -10 or 0)
  return rgb(150 + n, 62 + n * 0.5, 44 + n * 0.3)
end)

local tech = texture(TS, TS, function(x, y)
  if x == 0 or y == 0 then return rgb(150, 156, 166) end
  if x == TS - 1 or y == TS - 1 then return rgb(46, 50, 58) end
  if (x == 3 or x == TS - 4) and (y == 3 or y == TS - 4) then return rgb(200, 204, 210) end
  if y >= 12 and y <= 15 and x >= 6 and x <= 25 then
    return (x % 4 < 2) and rgb(40, 220, 140) or rgb(20, 120, 80)
  end
  local n = rnd(-6, 6)
  return rgb(92 + n, 98 + n, 108 + n)
end)

local concrete = texture(TS, TS, function(x, y)
  local n = rnd(-18, 18)
  if (x + y * 3) % 23 == 0 then n = n - 30 end
  if y > 26 then return rgb(60 + n, 56 + n, 40 + n) end          -- грязная полоса внизу
  return rgb(128 + n, 126 + n, 120 + n)
end)

local floorTex = texture(TS, TS, function(x, y)
  local tile = ((floor(x / 16) + floor(y / 16)) % 2 == 0)
  if x % 16 == 0 or y % 16 == 0 then return rgb(30, 30, 34) end
  local n = rnd(-8, 8)
  return tile and rgb(78 + n, 74 + n, 70 + n) or rgb(58 + n, 56 + n, 54 + n)
end)

local ceilTex = texture(TS, TS, function(x, y)
  if x >= 10 and x <= 21 and y >= 10 and y <= 21 then return rgb(230, 226, 190) end   -- лампа
  if x % 32 == 0 or y % 32 == 0 then return rgb(20, 20, 24) end
  local n = rnd(-5, 5)
  return rgb(44 + n, 46 + n, 52 + n)
end)

local T = -1   -- прозрачная точка

-- Робот: корпус, голова с красным глазом, ноги (2 кадра ходьбы), вспышка попадания, обломки
local function robot(frame, hit, dead)
  return texture(TS, TS, function(x, y)
    local cx = x - 15.5
    if dead then
      if y > 24 and abs(cx) < 12 - (y - 24) * 0.5 + (x % 3) then
        return rgb(90 + (x * 7 % 20), 40, 30)
      end
      if y > 28 and abs(cx) < 14 then return rgb(50, 50, 54) end
      return T
    end
    local body = hit and 1.8 or 1
    if y >= 4 and y <= 11 and abs(cx) <= 5 then                   -- голова
      if y >= 6 and y <= 8 and abs(cx) <= 3 then return hit and rgb(255, 255, 255) or rgb(255, 40, 30) end
      return rgb(70 * body, 74 * body, 84 * body)
    end
    if y >= 12 and y <= 22 and abs(cx) <= 8 - (y > 19 and 1 or 0) then -- корпус
      if abs(cx) <= 2 and y >= 14 and y <= 18 then return rgb(250, 160, 40) end
      return rgb(150 * body, 40 * body, 36 * body)
    end
    if y >= 13 and y <= 20 and abs(cx) >= 9 and abs(cx) <= 11 then  -- руки
      return rgb(90 * body, 92 * body, 100 * body)
    end
    if y >= 23 and y <= 31 then                                     -- ноги
      local step = frame == 1 and 2 or -2
      local lx = cx + 4 + (y > 27 and step or 0)
      local rx = cx - 4 - (y > 27 and step or 0)
      if abs(lx) <= 1.5 or abs(rx) <= 1.5 then return rgb(60 * body, 62 * body, 70 * body) end
    end
    return T
  end)
end
local robotTex = { robot(1), robot(2) }
local robotHit = robot(1, true)
local robotDead = robot(1, false, true)

local medkit = texture(16, 16, function(x, y)
  if y < 6 then return T end
  if (x >= 6 and x <= 9 and y >= 8) or (y >= 10 and y <= 13 and x >= 2 and x <= 13) then return rgb(220, 30, 30) end
  if x >= 1 and x <= 14 then return rgb(235, 235, 230) end
  return T
end)
local ammo = texture(16, 16, function(x, y)
  if y < 8 or x < 2 or x > 13 then return T end
  if y == 8 then return rgb(200, 170, 60) end
  return rgb(60, 110, 50 + (x % 3) * 10)
end)

local GW, GH = 40, 32
local gunTex = texture(GW, GH, function(x, y)
  local cx = x - 19.5
  if y >= 2 and y <= 20 and abs(cx) <= 3 then return (abs(cx) <= 1) and rgb(30, 30, 34) or rgb(70, 72, 80) end   -- ствол
  if y >= 18 and abs(cx) <= 8 + (y - 18) * 0.5 then
    if y >= 26 and abs(cx) <= 4 then return rgb(90, 60, 40) end                                          -- рукоять
    return rgb(56 + (x % 4) * 4, 58, 66)
  end
  return T
end)
local flashTex = texture(24, 24, function(x, y)
  local d = sqrt((x - 11.5) ^ 2 + (y - 11.5) ^ 2)
  local ray = (abs(x - 11.5) < 1.5 or abs(y - 11.5) < 1.5) and 11 or 7
  if d > ray then return T end
  return d < 4 and rgb(255, 255, 220) or rgb(255, 180 - d * 8, 40)
end)

---------------------------------------------------------------- карта
local MAP = {
  "111111111111111111111111",
  "1.........2............1",
  "1.........2............1",
  "1..3..3...2..1111..33..1",
  "1.................1....1",
  "1222..........3...1....1",
  "1.....2222....3........1",
  "1.....2.......3333..2221",
  "1.....2...............21",
  "1..1..2....11111......21",
  "1..1.......1...........1",
  "1..1...33..1...3..3....1",
  "1......33......3..3....1",
  "1111..........22.......1",
  "1......................1",
  "111111111111111111111111",
}
local MW, MH = #MAP[1], #MAP
local grid = {}
for y = 1, MH do
  grid[y - 1] = {}
  for x = 1, MW do
    local c = MAP[y]:sub(x, x)
    grid[y - 1][x - 1] = c == "." and 0 or tonumber(c)
  end
end
local wallTex = { brick, tech, concrete }

local function solid(x, y)
  local row = grid[floor(y)]
  return not row or (row[floor(x)] or 1) ~= 0
end

---------------------------------------------------------------- игрок, враги, предметы
local px, py = 1.6, 4.5
local ang = 0
local FOV = 0.66
local hp, bullets, score = 100, 30, 0
local hurt, fireT, bob = 0, 0, 0

local things = {}
local function enemy(x, y) things[#things + 1] = { kind = "robot", x = x, y = y, hp = 3, hitT = 0, atk = 0, anim = 0 } end
local function item(kind, x, y) things[#things + 1] = { kind = kind, x = x, y = y } end
enemy(9.5, 4.5); enemy(13.5, 4.2); enemy(16.5, 4.8); enemy(11.5, 8.5); enemy(20.5, 2.5); enemy(17.5, 12.5)
enemy(8.5, 14.5); enemy(21.5, 10.5)
item("ammo", 6.5, 4.5); item("medkit", 12.5, 5.5); item("ammo", 9.5, 12.5); item("medkit", 20.5, 5.5)
local total = 8

---------------------------------------------------------------- лучи
-- Дистанция до стены по лучу (для прицела и видимости)
local function cast(ox, oy, dx, dy, limit)
  local mx, my = floor(ox), floor(oy)
  local ddx, ddy = dx == 0 and 1e30 or abs(1 / dx), dy == 0 and 1e30 or abs(1 / dy)
  local sx, sy, sdx, sdy
  if dx < 0 then sx = -1; sdx = (ox - mx) * ddx else sx = 1; sdx = (mx + 1 - ox) * ddx end
  if dy < 0 then sy = -1; sdy = (oy - my) * ddy else sy = 1; sdy = (my + 1 - oy) * ddy end
  for _ = 1, 64 do
    local side
    if sdx < sdy then sdx = sdx + ddx; mx = mx + sx; side = 0 else sdy = sdy + ddy; my = my + sy; side = 1 end
    local row = grid[my]
    local cell = row and row[mx] or 1
    if cell ~= 0 then
      local dist = side == 0 and (sdx - ddx) or (sdy - ddy)
      return dist, cell, side, mx, my
    end
    if limit and min(sdx, sdy) > limit then return limit end
  end
  return 64
end

local function visible(ax, ay, bx, by)
  local dx, dy = bx - ax, by - ay
  local d = sqrt(dx * dx + dy * dy)
  if d < 1e-6 then return true end
  return cast(ax, ay, dx / d, dy / d, d) >= d - 0.05
end

---------------------------------------------------------------- кадр
local centerDepth = 64
local frames, fps, fpsT = 0, 0, uptime()
local message, messageT = "", 0

local function say(text)
  message, messageT = text, uptime() + 2
end

local function render(t)
  local dirX, dirY = cos(ang), sin(ang)
  local plX, plY = -dirY * FOV, dirX * FOV
  cv:clear(0)
  -- Пол и потолок: строка экрана — одна полоса с текстурой (на одной строке расстояние одинаковое)
  local rx0, ry0 = dirX - plX, dirY - plY
  local rx1, ry1 = dirX + plX, dirY + plY
  for y = floor(HALF) + 1, CH - 1 do
    local rowDist = HALF / (y - HALF + 0.5)
    local fx0, fy0 = px + rowDist * rx0, py + rowDist * ry0
    local fx1, fy1 = px + rowDist * rx1, py + rowDist * ry1
    local k = max(0.15, 1 - rowDist / 12)
    cv:span(y, 0, CW, floorTex, fx0 * TS, fy0 * TS, fx1 * TS, fy1 * TS, k)
    cv:span(CH - 1 - y, 0, CW, ceilTex, fx0 * TS, fy0 * TS, fx1 * TS, fy1 * TS, k * 0.9)
  end
  -- Стены: столбец экрана — один луч
  for x = 0, CW - 1 do
    local camX = 2 * (x + 0.5) / CW - 1
    local dx, dy = dirX + plX * camX, dirY + plY * camX
    local dist, cell, side = cast(px, py, dx, dy)
    if dist < 0.02 then dist = 0.02 end
    local hgt = CH / dist
    local top = HALF - hgt / 2
    local wx
    if side == 0 then wx = py + dist * dy else wx = px + dist * dx end
    wx = wx - floor(wx)
    local tx = floor(wx * TS)
    if (side == 0 and dx > 0) or (side == 1 and dy < 0) then tx = TS - 1 - tx end
    local k = max(0.18, 1 - dist / 14) * (side == 1 and 0.78 or 1)
    cv:column(x, top, top + hgt, wallTex[cell] or brick, tx, 0, TS, k, dist)
    if x == floor(CW / 2) then centerDepth = dist end
  end
  -- Враги и предметы: от дальних к ближним, стены закрывают их по глубине
  local inv = 1 / (plX * dirY - dirX * plY)
  local list = {}
  for _, th in ipairs(things) do
    if not th.gone then
      local sx, sy = th.x - px, th.y - py
      th.tx = inv * (dirY * sx - dirX * sy)
      th.ty = inv * (-plY * sx + plX * sy)
      if th.ty > 0.15 then list[#list + 1] = th end
    end
  end
  table.sort(list, function(a, b) return a.ty > b.ty end)
  for _, th in ipairs(list) do
    local scrX = (CW / 2) * (1 + th.tx / th.ty)
    local size = CH / th.ty
    local k = max(0.2, 1 - th.ty / 14)
    if th.kind == "robot" then
      local tex = th.dead and robotDead or (th.hitT > t and robotHit) or robotTex[(floor(th.anim * 4) % 2) + 1]
      cv:sprite(tex, scrX - size / 2, HALF - size / 2, size, size, k, th.ty)
      th.scrX, th.size = scrX, size
    else
      local s = size * 0.45
      cv:sprite(th.kind == "medkit" and medkit or ammo, scrX - s / 2, HALF + size / 2 - s, s, s, k, th.ty)
    end
  end
  -- Оружие и вспышка выстрела
  local gw, gh = CW * 0.16, CH * 0.36
  local gy = CH - gh + 4 + abs(sin(bob)) * 6
  if fireT > t then
    local fs = gw * 0.9
    cv:sprite(flashTex, CW / 2 - fs / 2, gy - fs * 0.55, fs, fs, 1)
    gy = gy + 4
  end
  cv:sprite(gunTex, CW / 2 - gw / 2, gy, gw, gh, 1)
  -- Прицел
  cv:fill(floor(CW / 2) - 3, floor(HALF), 2, 1, 0xFFFFFF)
  cv:fill(floor(CW / 2) + 2, floor(HALF), 2, 1, 0xFFFFFF)
  -- Получили урон — красная рамка
  if hurt > t then
    local b = 3
    cv:fill(0, 0, CW, b, 0xC01010); cv:fill(0, CH - b, CW, b, 0xC01010)
    cv:fill(0, 0, b, CH, 0xC01010); cv:fill(CW - b, 0, b, CH, 0xC01010)
  end
  cv:present(1, 1)
  -- Строка состояния (текстом поверх двух нижних строк)
  gpu.setBackground(0x15171C)
  gpu.fill(1, H - 1, W, 2, " ")
  gpu.setForeground(hp > 30 and 0x60E070 or 0xFF5040)
  gpu.set(2, H - 1, string.format("♥ %3d", hp))
  gpu.setForeground(0xF0C040)
  gpu.set(12, H - 1, string.format("Патроны %2d", bullets))
  gpu.setForeground(0xD0D4DA)
  gpu.set(28, H - 1, string.format("Роботы %d/%d", score, total))
  gpu.setForeground(0x8A9099)
  gpu.set(W - 22, H - 1, string.format("%2d кадров/с", fps))
  local hint = mode == "demo" and "Демо: играет автопилот · Q — выход" or "W/S A/D ←/→ · пробел — огонь · Q — выход"
  if messageT > t then
    gpu.setForeground(0xFFFFFF)
    gpu.set(2, H, message)
  else
    gpu.setForeground(0x6A7079)
    gpu.set(2, H, hint)
  end
end

---------------------------------------------------------------- игра
local keys = {}
local function shoot(t)
  if fireT > t - 0.1 then return end
  if bullets <= 0 then
    say("Нет патронов!")
    fireT = t + 0.3
    sfx(1, 1500, 0.03, 0.4, "square")                 -- щелчок пустого магазина
    return
  end
  bullets = bullets - 1
  fireT = t + 0.12
  sfx(1, 2400, 0.16, 1.0, "noise", 250)               -- выстрел: шум, уходящий вниз
  -- Попали в ближайшего робота, который под прицелом и не за стеной
  local best
  for _, th in ipairs(things) do
    if th.kind == "robot" and not th.dead and th.scrX and th.ty and th.ty > 0.15 and th.ty < centerDepth
        and abs(th.scrX - CW / 2) < th.size * 0.3 then
      if not best or th.ty < best.ty then best = th end
    end
  end
  if best then
    best.hp = best.hp - 1
    best.hitT = t + 0.15
    if best.hp <= 0 then
      best.dead = true
      score = score + 1
      say(score == total and "Все роботы уничтожены!" or "Робот уничтожен")
      sfx(2, 520, 0.45, 0.9, "sawtooth", 45)          -- робот ломается
      if score == total and snd then                  -- победа: арпеджио по очереди
        for i, n in ipairs({ "C5", "E5", "G5", "C6" }) do
          snd.setWave(3, snd.modes.square) snd.setVolume(3, 0.6) snd.setFrequency(3, n)
          snd.open(3) snd.delay(i == 4 and 400 or 110) snd.close(3) snd.delay(20)
        end
        snd.process()
      end
    else
      sfx(2, 330, 0.08, 0.7, "square", 180)           -- попадание
    end
  end
end

local function move(dx, dy)
  local r = 0.22
  local nx, ny = px + dx, py + dy
  if not solid(nx + (dx > 0 and r or -r), py) then px = nx end
  if not solid(px, ny + (dy > 0 and r or -r)) then py = ny end
end

local function update(t, dt)
  local dirX, dirY = cos(ang), sin(ang)
  local fwd, side, turn = 0, 0, 0
  if keys.w or keys.up then fwd = fwd + 1 end
  if keys.s or keys.down then fwd = fwd - 1 end
  if keys.a then side = side - 1 end
  if keys.d then side = side + 1 end
  if keys.left then turn = turn - 1 end
  if keys.right then turn = turn + 1 end
  ang = ang + turn * 2.4 * dt
  local sp = 3.0 * dt
  if fwd ~= 0 or side ~= 0 then
    move((dirX * fwd - dirY * side) * sp, (dirY * fwd + dirX * side) * sp)
    local step = floor(bob / pi)
    bob = bob + dt * 9
    if floor(bob / pi) ~= step then sfx(4, 160, 0.05, 0.5, "noise", 70) end   -- шаг
  end
  if keys.space or keys.ctrl then shoot(t) end
  -- Враги идут к игроку, если видят, и бьют вблизи
  for _, th in ipairs(things) do
    if th.kind == "robot" and not th.dead then
      local dx, dy = px - th.x, py - th.y
      local d = sqrt(dx * dx + dy * dy)
      if d < 9 and visible(th.x, th.y, px, py) then
        if d > 1.1 then
          local s = 1.25 * dt
          local nx, ny = th.x + dx / d * s, th.y + dy / d * s
          if not solid(nx, th.y) then th.x = nx end
          if not solid(th.x, ny) then th.y = ny end
          th.anim = th.anim + dt
        elseif t > th.atk then
          th.atk = t + 0.9
          hp = max(0, hp - 8)
          hurt = t + 0.2
          sfx(2, 140, 0.18, 0.9, "sawtooth", 70)          -- робот бьёт
        end
      end
    elseif not th.gone and (th.kind == "medkit" or th.kind == "ammo") then
      if abs(th.x - px) < 0.6 and abs(th.y - py) < 0.6 then
        th.gone = true
        if th.kind == "medkit" then hp = min(100, hp + 30); say("Аптечка: +30"); sfx(3, 440, 0.25, 0.6, "triangle", 880)
        else bullets = bullets + 15; say("Патроны: +15"); sfx(3, 700, 0.12, 0.6, "square", 1400) end
      end
    end
  end
end

-- Автопилот для «demo»: ищет путь по карте (поиск в ширину), стреляет по видимым роботам
local function nextCell(sx, sy, tx, ty)
  sx, sy, tx, ty = floor(sx), floor(sy), floor(tx), floor(ty)
  if sx == tx and sy == ty then return tx, ty end
  local prev, queue, head = { [sy * MW + sx] = -1 }, { sy * MW + sx }, 1
  while queue[head] do
    local cur = queue[head]; head = head + 1
    local cx, cy = cur % MW, floor(cur / MW)
    if cx == tx and cy == ty then
      while prev[cur] ~= sy * MW + sx and prev[cur] ~= -1 do cur = prev[cur] end
      return cur % MW, floor(cur / MW)
    end
    for _, d in ipairs({ { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) do
      local nx, ny = cx + d[1], cy + d[2]
      local key = ny * MW + nx
      if not prev[key] and grid[ny] and grid[ny][nx] == 0 then
        prev[key] = cur
        queue[#queue + 1] = key
      end
    end
  end
  return nil
end

local route = { { 6.5, 4.5 }, { 12.5, 5.5 }, { 20.5, 2.5 }, { 21.5, 10.5 }, { 17.5, 13.5 }, { 9.5, 12.5 }, { 4.5, 10.5 }, { 2.5, 4.5 } }
local routeI = 1
local goalX, goalY, goalT = nil, nil, 0
local function autopilot(t)
  for k in pairs(keys) do keys[k] = nil end
  -- Ближайший живой робот: если виден — стреляем, иначе идём к нему по карте
  local target, td, seen
  for _, th in ipairs(things) do
    if th.kind == "robot" and not th.dead then
      local d = sqrt((th.x - px) ^ 2 + (th.y - py) ^ 2)
      local v = d < 9 and visible(px, py, th.x, th.y)
      if (v and not seen) or ((v or not seen) and (not td or d < td)) then target, td, seen = th, d, v end
    end
  end
  local gx, gy
  if target and seen then
    gx, gy = target.x, target.y
  else
    if t > goalT then
      local tx, ty
      if target then tx, ty = target.x, target.y else tx, ty = route[routeI][1], route[routeI][2] end
      local cx, cy = nextCell(px, py, tx, ty)
      if cx then goalX, goalY = cx + 0.5, cy + 0.5 else goalX, goalY = tx, ty end
      goalT = t + 0.25
      if not target and sqrt((tx - px) ^ 2 + (ty - py) ^ 2) < 0.8 then routeI = routeI % #route + 1 end
    end
    gx, gy = goalX, goalY
  end
  local want = atan2(gy - py, gx - px)
  local diff = (want - ang + pi) % (2 * pi) - pi
  if diff > 0.06 then keys.right = true elseif diff < -0.06 then keys.left = true end
  if target and seen then
    if abs(diff) < 0.1 then keys.space = true end
    if td > 3.5 and abs(diff) < 0.4 then keys.w = true end
    if td < 1.8 then keys.s = true end
  elseif abs(diff) < 0.5 then
    keys.w = true
  end
  if hp < 40 then hp = 100 end            -- в демо игрок не умирает
  if bullets < 5 then bullets = 30 end
end

local function finish(text)
  cv:free()
  gpu.setBackground(0x000000)
  gpu.setForeground(0xFFFFFF)
  gpu.setResolution(oldW, oldH)
  term.clear()
  term.setCursorPos(1, 1)
  if text then print(text) end
end

---------------------------------------------------------------- замер
if mode == "bench" then
  local n = tonumber(args[2]) or 90
  for _ = 1, 5 do render(uptime()) end                -- разогрев
  local t0 = uptime()
  for i = 1, n do
    ang = ang + 0.02
    render(uptime())
  end
  local dt = uptime() - t0
  finish(string.format("BUNKER %d×%d точек: %d кадров за %.2f с — %.1f кадров/с, %.1f мс на кадр",
    CW, CH, n, dt, n / dt, dt / n * 1000))
  return
end

---------------------------------------------------------------- главный цикл (30 кадров в секунду)
local TARGET = 1 / 30
local last = uptime()
local timer = os.startTimer(0)
local quit = false
local endAt = tonumber(args[2])                       -- «bunker demo 12» — 12 секунд и выход
local started = uptime()
say(mode == "demo" and "Демо-режим" or "Уничтожьте всех роботов!")

while not quit do
  local ev = { os.pullEventRaw() }
  local name = ev[1]
  if name == "terminate" then quit = true
  elseif name == "key" then
    keys[ev[2]] = true
    if ev[2] == "q" then quit = true end
  elseif name == "key_up" then keys[ev[2]] = nil
  elseif name == "timer" and ev[2] == timer then
    local t = uptime()
    local dt = min(0.1, t - last)
    last = t
    if mode == "demo" then autopilot(t) end
    update(t, dt)
    render(t)
    frames = frames + 1
    if t - fpsT >= 1 then fps = floor(frames / (t - fpsT) + 0.5); frames = 0; fpsT = t end
    if hp <= 0 then say("Вас победили роботы. Q — выход") end
    if endAt and t - started >= endAt then quit = true end
    -- Следующий кадр — ровно через 1/30 с от начала этого
    timer = os.startTimer(max(0, TARGET - (uptime() - t)))
  end
end
if snd then snd.stop() end
finish(string.format("Счёт: %d из %d роботов", score, total))
