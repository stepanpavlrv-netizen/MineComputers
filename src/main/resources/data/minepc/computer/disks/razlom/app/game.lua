-- Разлом: 3D-шутер для видеокарт MineComputers. Пять уровней станции «Глубина-7» над разломом.
--   game [lvl=N]       — играть (с уровня N)
--   game demo [с]      — облёт уровня (сам), для показа
--   game bench [N]     — замер: N кадров облёта, печатает кадры в секунду
--   game shot ... out=файл — один кадр в файл (для проверок без Minecraft: tools/razview/shot.py)
--   game check [out=файл] — самопроверка правил всех уровней (двери, пульты, босс, выходы)
-- Управление: мышь — обзор (захватывается на время боя, Esc отпускает), W/S/A/D — ходьба, ←/→ и ↑/↓ — поворот
-- и взгляд клавишами, пробел — прыжок, левая кнопка мыши, Ctrl или F — огонь, 1/2/3 — оружие, E — пульт,
-- P — пауза, Q — выход.
local args = { ... }
local mode = args[1] or "play"
if mode ~= "play" and mode ~= "demo" and mode ~= "bench" and mode ~= "shot" and mode ~= "check" then mode = "play" end
local embedded = false
for _, a in ipairs(args) do if a == "wired" then embedded = true end end
local gpu = component and component.gpu
if not gpu or not gpu.newMesh or not gpu.canvas then
  print("Нужна видеокарта MineComputers с 3D (gpu.newMesh). Обновите мод.")
  return
end

local floor, sqrt, abs, cos, sin, atan2, min, max = math.floor, math.sqrt, math.abs, math.cos, math.sin, math.atan2, math.min, math.max
local pi = math.pi
local uptime = computer.uptime
local rnd = math.random
math.randomseed(floor(uptime() * 1000) % 100000)
local LEVELS = 5

-- аргументы вида имя=значение
local opt = {}
for _, a in ipairs(args) do
  local k, v = a:match("^(%w+)=(.*)$")
  if k then opt[k] = v else opt[a] = true end
end

local DIR = ((debug and debug.getinfo and debug.getinfo(1, "S").source) or ""):match("^@(.*)/[^/]*$") or "disk/app"
if DIR:sub(1, 1) ~= "/" then DIR = "/" .. DIR end
local function need(name, ...)
  local fn, err = loadfile(DIR .. "/" .. name .. ".lua")
  if not fn then error("Не найден " .. name .. ".lua: " .. tostring(err), 0) end
  return fn(...)
end

-- ======================= экран =======================
local prevBuffer = gpu.getActiveBuffer and gpu.getActiveBuffer() or 0
if prevBuffer ~= 0 then gpu.setActiveBuffer(0) end
local oldW, oldH = gpu.getResolution()
local oldFg, oldBg = gpu.getForeground(), gpu.getBackground()
local MW, MH = gpu.maxResolution()
if gpu.maxDepth() >= 24 then pcall(gpu.setDepth, 24) end
local HUD = 2

-- Кадр и качество текстур под видеопамять: модели и текстуры занимают около 31 тыс. клеток в полном качестве,
-- 10 тыс. — в простом и 5 тыс. — в самом простом, уровень — ещё до 3 тыс. Сначала ищем кадр покрупнее (не меньше
-- половины экрана) с полными текстурами, потом с простыми; на самых слабых картах — что влезет.
local W, H, cv, quality
do
  local TIERS = { { q = false, need = 34500, minScale = 0.5 }, { q = 2, need = 14000, minScale = 0.5 }, { q = 4, need = 8000, minScale = 0.2 } }
  for _, tier in ipairs(TIERS) do
    local scale = 1
    while scale >= tier.minScale - 1e-6 do
      local w, h = max(40, floor(MW * scale)), max(14, floor(MH * scale))
      gpu.setResolution(w, h)
      local c = gpu.canvas(w, (h - HUD) * 2)
      if c then
        local free = gpu.freeMemory and gpu.freeMemory() or 1e9
        if free >= tier.need - 300 then W, H, cv, quality = w, h, c, tier.q break end
        c:free()
      end
      if w <= 40 and h <= 14 then break end
      scale = scale - 0.1
    end
    if cv then break end
  end
end
if not cv then
  gpu.setResolution(oldW, oldH)
  if prevBuffer ~= 0 then gpu.setActiveBuffer(prevBuffer) end
  local msg = "Не хватает видеопамяти даже для маленького кадра. Нужна видеокарта помощнее."
  if embedded then return false, msg end
  print(msg)
  return
end
local CW, CH = cv.width, cv.height
local lowVram = quality

-- Звук (звуковой чип платы; без него игра идёт молча). 1 — оружие, 2 — враги, 3 — предметы, 4 — игрок
local snd = (sound and sound.channels and sound.channels() >= 1) and sound or nil
local CHN = snd and snd.channels() or 1
local function sfx(ch, freq, sec, vol, wave, to)
  if snd then pcall(snd.play, (ch - 1) % CHN + 1, freq, sec, vol, wave, to) end
end
local function arpeggio(notes, len)
  if not snd then return end
  local c = (3 - 1) % CHN + 1
  for i, n in ipairs(notes) do
    snd.setWave(c, snd.modes.square) snd.setVolume(c, 0.5) snd.setFrequency(c, n)
    snd.open(c) snd.delay(i == #notes and len * 3 or len) snd.close(c) snd.delay(15)
  end
  pcall(snd.process)
end

local function loading(text, frac, title)
  gpu.setBackground(0x0c0b10)
  gpu.fill(1, 1, W, H, " ")
  gpu.setForeground(0xff8a3d)
  title = title or "Р А З Л О М"
  gpu.set(floor((W - unicode.len(title)) / 2) + 1, floor(H / 2) - 2, title)
  gpu.setForeground(0x8d87a3)
  gpu.set(floor((W - unicode.len(text)) / 2) + 1, floor(H / 2), text)
  local bw = min(40, W - 4)
  local bx = floor((W - bw) / 2) + 1
  gpu.setBackground(0x2a2733)
  gpu.fill(bx, floor(H / 2) + 2, bw, 1, " ")
  gpu.setBackground(0xff8a3d)
  gpu.fill(bx, floor(H / 2) + 2, max(1, floor(bw * frac)), 1, " ")
  gpu.setBackground(0x0c0b10)
end

local art
local okInit, initErr = pcall(function()
  loading("Рисуем текстуры и модели...", 0.05)
  art = need("art", gpu, lowVram)
end)
local T, M = art and art.tex, art and art.mesh

-- ======================= уровень =======================
local Lv, levelNo
local levelMeshes, acidMesh, doorMeshes = {}, nil, {}

local function freeLevel()
  for _, l in ipairs(levelMeshes) do pcall(gpu.freeMesh, l.id) end
  if acidMesh then pcall(gpu.freeMesh, acidMesh) end
  for _, id in pairs(doorMeshes) do pcall(gpu.freeMesh, id) end
  levelMeshes, acidMesh, doorMeshes = {}, nil, {}
end

local function loadLevel(n)
  freeLevel()
  local fn, err = loadfile(DIR .. "/levels/" .. n .. ".lua")
  if not fn then error("Не найден уровень " .. n .. ": " .. tostring(err), 0) end
  local data = fn()
  local title = "Уровень " .. n .. " · " .. data.name
  loading("Строим станцию и раскладываем свет...", 0.3, title)
  Lv = need("level", data)
  levelMeshes, acidMesh, doorMeshes = Lv.build(art, function(f) loading("Строим станцию и раскладываем свет...", 0.3 + f * 0.65, title) end)
  levelNo = n
end

local startLevel = max(1, min(LEVELS, tonumber(opt.lvl) or 1))
if okInit then okInit, initErr = pcall(loadLevel, startLevel) end
if not okInit then
  if gpu.freeAllBuffers then pcall(gpu.freeAllBuffers) end
  pcall(function() cv:free() end)
  gpu.setBackground(oldBg) gpu.setForeground(oldFg)
  gpu.setResolution(oldW, oldH)
  if prevBuffer ~= 0 then gpu.setActiveBuffer(prevBuffer) end
  if embedded then return false, tostring(initErr) end
  print("Разлом не запустился: " .. tostring(initErr))
  return
end

local STEP = Lv.STEP
local GRAV = 18
local FOG = 0x0c0b10

-- ======================= состояние =======================
local P                         -- игрок
local enemies, items, shots, parts, flashes, consoles
local stats, portalOpen, state, message, messageT
local t0, now
local run = { time = 0, kills = 0, taken = 0, fromStart = startLevel == 1 }   -- весь забег (рекорд — с 1-го уровня)
local snapshot                  -- игрок в начале уровня (после смерти — отсюда)

-- Враги. hip/hipW — где ноги; muzzle — откуда летит выстрел (вперёд, вверх, вправо)
local DEF = {
  guard = { hp = 45, speed = 4.0, r = 0.34, h = 1.6, melee = 9, meleeRate = 0.9, sight = 16, eyeY = 1.45,
            name = "Охранник", body = "guardBody", leg = "guardLeg", hip = 0.72, hipW = 0.15 },
  gunner = { hp = 85, speed = 2.5, r = 0.4, h = 1.85, shot = "fire", rate = 1.8, dmg = 12, speedShot = 11, keep = 6,
             sight = 26, eyeY = 1.7, name = "Боевой робот", body = "gunnerBody", leg = "gunnerLeg", hip = 0.86, hipW = 0.2,
             muzzle = { 0.84, 1.12, 0.49 } },
  eye = { hp = 35, speed = 3.2, r = 0.42, h = 0.84, fly = true, shot = "bolt", rate = 1.35, dmg = 8, speedShot = 15,
          sight = 22, eyeY = 0, name = "Око" },
  scientist = { hp = 120, speed = 1.8, r = 0.36, h = 1.75, melee = 24, meleeRate = 1.5, sight = 14, eyeY = 1.42,
                name = "Заражённый учёный", body = "scientistBody", leg = "scientistLeg", hip = 0.7, hipW = 0.12 },
  crawler = { hp = 28, speed = 6.8, r = 0.32, h = 0.55, melee = 7, meleeRate = 0.55, sight = 18, eyeY = 0.4,
              name = "Ползун", mesh = "crawler" },
  turret = { hp = 70, speed = 0, r = 0.3, h = 1.05, shot = "bolt", rate = 0.6, dmg = 4, speedShot = 18, sight = 18,
             eyeY = 0.75, name = "Турель", mesh = "turret", static = true, machine = true, muzzle = { 0.64, 0.64, 0 } },
  boss = { hp = 1500, speed = 0.8, r = 1.05, h = 3.3, shot = "bolt", rate = 2.2, dmg = 9, speedShot = 13, sight = 60,
           eyeY = 2.1, name = "Сердце разлома", mesh = "boss", boss = true, muzzle = { 1.0, 2.1, 0 } },
}

local WEAPONS = {
  { name = "Бластер", mesh = "blaster", rate = 0.36, dmg = 15, pellets = 1, spread = 0.006, kick = 0.12,
    hold = { 0.11, -0.115, 0.24 }, scale = 0.7, tip = 0.3 },
  { name = "Дробовик", mesh = "shotgun", rate = 0.8, ammo = "shells", dmg = 8, pellets = 8, spread = 0.065, kick = 0.3,
    hold = { 0.11, -0.11, 0.24 }, scale = 0.5, tip = 0.63 },
  { name = "Плазмомёт", mesh = "plasmagun", rate = 0.7, ammo = "cells", proj = true, dmg = 95, splash = 3.3, kick = 0.22,
    hold = { 0.12, -0.12, 0.26 }, scale = 0.45, tip = 0.63 },
}

local KEYNAME = { red = "красная", yellow = "жёлтая", blue = "синяя" }
local KEYCOL = { red = 0xff5050, yellow = 0xffd040, blue = 0x5a8cff }
local ITEMS = {
  medkit = { mesh = "medkit", name = "Аптечка" }, armor = { mesh = "armor", name = "Бронежилет" },
  shells = { mesh = "shells", name = "Патроны" }, cells = { mesh = "cells", name = "Энергоячейка" },
  shotgun = { mesh = "shotgun", name = "Дробовик", scale = 1.4 }, plasmagun = { mesh = "plasmagun", name = "Плазмомёт", scale = 1.3 },
  keyRed = { mesh = "keyRed", name = "Красная ключ-карта", key = "red", noBob = true },
  keyYellow = { mesh = "keyYellow", name = "Жёлтая ключ-карта", key = "yellow", noBob = true },
  keyBlue = { mesh = "keyBlue", name = "Синяя ключ-карта", key = "blue", noBob = true },
}

local function say(text, sec)
  message = text
  messageT = now + (sec or 2.5)
end

-- ======================= движение тел (игрок и враги) =======================
local function moveBody(b, dx, dz, dt)
  local r, h = b.r, b.h
  local bumped = false
  local n = max(1, math.ceil(max(abs(dx), abs(dz)) / 0.25))
  dx, dz = dx / n, dz / n
  for _ = 1, n do
    if dx ~= 0 then
      local nx = b.x + dx
      if not Lv.blocked(nx, b.y, b.z, r, h) then b.x = nx
      elseif b.onGround and not Lv.blocked(nx, b.y + STEP, b.z, r, h) then
        b.x = nx
        b.y = max(b.y, Lv.ground(nx, b.z, r, b.y))
      else bumped = true end
    end
    if dz ~= 0 then
      local nz = b.z + dz
      if not Lv.blocked(b.x, b.y, nz, r, h) then b.z = nz
      elseif b.onGround and not Lv.blocked(b.x, b.y + STEP, nz, r, h) then
        b.z = nz
        b.y = max(b.y, Lv.ground(b.x, nz, r, b.y))
      else bumped = true end
    end
  end
  local g = Lv.ground(b.x, b.z, r, b.y)
  if b.onGround and b.vy <= 0 and b.y - g <= STEP + 0.02 then
    b.y = g
  else
    b.vy = b.vy - GRAV * dt
    local ny = b.y + b.vy * dt
    if ny <= g then
      b.landSpeed = -b.vy
      b.y, b.vy, b.onGround = g, 0, true
      return bumped
    end
    local c = Lv.ceiling(b.x, b.z, r, b.y + h)
    if ny + h > c then ny = c - h; if b.vy > 0 then b.vy = 0 end end
    b.y = ny
    b.onGround = false
  end
  return bumped
end

-- ======================= частицы, вспышки, снаряды =======================
local MAXP = 90
local function particle(tex, x, y, z, vx, vy, vz, life, size, grav, glow)
  if #parts >= MAXP then table.remove(parts, 1) end
  parts[#parts + 1] = { tex = tex, x = x, y = y, z = z, vx = vx, vy = vy, vz = vz, life = life, max = life,
                        size = size, grav = grav or 0, glow = glow }
end
local function burst(tex, x, y, z, n, speed, life, size, grav, glow)
  for _ = 1, n do
    local a, b = rnd() * pi * 2, (rnd() - 0.3) * pi
    local s = speed * (0.4 + rnd() * 0.6)
    particle(tex, x, y, z, cos(a) * cos(b) * s, sin(b) * s + speed * 0.2, sin(a) * cos(b) * s, life * (0.6 + rnd() * 0.6), size, grav, glow)
  end
end
local function flashLight(x, y, z, radius, color, power, life)
  flashes[#flashes + 1] = { x = x, y = y, z = z, r = radius, c = color, p = power, life = life, max = life }
end
local function dist3(ax, ay, az, bx, by, bz)
  local dx, dy, dz = ax - bx, ay - by, az - bz
  return sqrt(dx * dx + dy * dy + dz * dz)
end

--- Коробка тела врага по высоте.
local function bodyY(e)
  local d = e.def
  if d.fly then return e.y - 0.45, e.y + 0.45 end
  if d.boss then return e.y + 0.9, e.y + 3.3 end
  return e.y, e.y + d.h
end

-- ======================= урон =======================
local function hurtPlayer(dmg, fromX, fromZ)
  if P.dead or state ~= "play" or P.god or dmg <= 0 then return end
  local save = min(P.armor, floor(dmg * 2 / 3 + 0.5))
  P.armor = P.armor - save
  P.hp = P.hp - (dmg - save)
  P.hurtT = now
  stats.taken = stats.taken + dmg
  if fromX then P.hurtDir = atan2(fromZ - P.z, fromX - P.x) end
  sfx(4, 190, 0.18, 0.9, "sawtooth", 90)
  if P.hp <= 0 then
    P.hp, P.dead, P.deadT = 0, true, now
    state = "dead"
    say("Вы погибли. R — заново с начала уровня, Q — выход", 1e9)
    sfx(4, 300, 1.2, 1, "sawtooth", 40)
  end
end

local function aliveCount()
  local n = 0
  for _, e in ipairs(enemies) do if not e.dead and not e.def.machine then n = n + 1 end end
  return n
end

local function openExit(text)
  if portalOpen then return end
  portalOpen = true
  say(text, 5)
  arpeggio({ 523, 659, 784, 1047 }, 110)
end

local function killEnemy(e)
  e.dead, e.deadT = true, now
  if not e.def.machine then stats.kills = stats.kills + 1 end
  local y1, y2 = bodyY(e)
  local cx, cy, cz = e.x, (y1 + y2) / 2, e.z
  if e.def.machine then
    burst(T.spark, cx, cy, cz, 14, 7, 0.5, 0.1, 8, true)
    burst(T.smoke, cx, cy, cz, 4, 1.2, 1.2, 0.5, -1)
  else
    burst(T.oil, cx, cy, cz, 10, 4, 0.9, 0.16, 10)
    burst(T.spark, cx, cy, cz, 8, 6, 0.4, 0.1, 6, true)
  end
  if e.def.fly or e.def.boss then
    burst(T.fire, cx, cy, cz, e.def.boss and 20 or 6, e.def.boss and 6 or 3, 0.7, e.def.boss and 1.0 or 0.5, -1, true)
    burst(T.glowGreen, cx, cy, cz, e.def.boss and 24 or 0, 7, 1.2, 0.3, 2, true)
    flashLight(cx, cy, cz, e.def.boss and 14 or 5, 0xff9040, e.def.boss and 2 or 1.2, e.def.boss and 1.5 or 0.4)
  end
  sfx(2, e.def.fly and 900 or 480, e.def.boss and 1.6 or 0.5, 0.9, "sawtooth", 40)
  if e.def.machine then return end
  local need = Lv.exit and Lv.exit.need
  local left = aliveCount()
  if e.def.boss and need == "boss" then
    for _, o in ipairs(enemies) do if not o.dead and not o.def.machine then killEnemy(o) end end
    openExit("Сердце разлома уничтожено! Портал домой открыт")
  elseif need == "kills" and left == 0 then
    openExit("Все враги уничтожены! Портал открыт " .. (Lv.exit.where or ""))
  elseif need == "kills" and left <= 3 then
    say("Осталось врагов: " .. left)
  end
end

local function hurtEnemy(e, dmg, kx, kz)
  if e.dead then return end
  if e.def.boss and now >= (e.exposedT or 0) then
    dmg = dmg * 0.15                                 -- плоть гасит урон, пока обруч не вскрыл кристалл
    if now >= (e.hintT or 0) then
      e.hintT = now + 12
      say("Плоть гасит выстрелы! Включите излучатели обруча на пультах (E)", 4)
    end
  end
  e.hp = e.hp - dmg
  e.flashT = now + 0.1
  e.alert = true
  if kx and not e.def.fly and not e.def.static and not e.def.boss then
    e.push = (e.push or 0) + dmg * 0.02; e.pushX, e.pushZ = kx, kz
  end
  if e.hp <= 0 then killEnemy(e)
  elseif now - (e.painT or 0) > 0.3 then
    e.painT = now
    sfx(2, e.def.machine and 900 or (240 + rnd(0, 60)), 0.12, 0.6, "square", 120)
  end
end

--- Взрыв: урон по кругу, отбрасывание (в том числе себя — так прыгают на взрыве).
local function explode(x, y, z, radius, dmg, owner)
  burst(T.fire, x, y, z, 12, 5, 0.55, 0.55, -2, true)
  burst(T.spark, x, y, z, 10, 9, 0.45, 0.1, 8, true)
  burst(T.smoke, x, y, z, 5, 1.5, 1.2, 0.7, -1)
  flashLight(x, y, z, radius * 2.2, 0xff9a40, 1.6, 0.45)
  sfx(2, 380, 0.6, 1, "noise", 30)
  for _, e in ipairs(enemies) do
    if not e.dead then
      local y1, y2 = bodyY(e)
      local d = dist3(x, y, z, e.x, max(y1, min(y2, y)), e.z) - (e.def.boss and 1 or 0)
      d = max(0, d)
      if d < radius then hurtEnemy(e, dmg * (1 - d / radius * 0.7), (e.x - x) / max(0.1, d), (e.z - z) / max(0.1, d)) end
    end
  end
  if owner == "player" and not P.dead then
    local cy = P.y + 0.9
    local d = dist3(x, y, z, P.x, cy, P.z)
    if d < radius then
      local k = 1 - d / radius
      local ux, uy, uz = (P.x - x) / max(0.2, d), (cy - y) / max(0.2, d), (P.z - z) / max(0.2, d)
      P.vx, P.vy, P.vz = P.vx + ux * 11 * k, P.vy + max(uy, 0.35) * 13 * k, P.vz + uz * 11 * k
      P.onGround = false
      P.air = true
      hurtPlayer(floor(dmg * 0.35 * k))
    end
  end
end

-- ======================= стрельба игрока =======================
local function eyePos()
  return P.x, P.y + (P.dead and 0.35 or 1.55), P.z
end
local function forward(yaw, pitch)
  local cp = cos(pitch)
  return cp * cos(yaw), sin(pitch), cp * sin(yaw)
end

--- Луч против врагов (коробка вокруг тела): ближайший враг и расстояние. skip — кого не считать.
local function rayEnemy(ox, oy, oz, dx, dy, dz, maxd, skip)
  local best, bestT = nil, maxd
  for _, e in ipairs(enemies) do
    if not e.dead and e ~= skip then
      local r = e.def.r + 0.08
      local y1, y2 = bodyY(e)
      local tmin, tmax = 0, bestT
      local ok = true
      local bounds = { { ox, dx, e.x - r, e.x + r }, { oy, dy, y1, y2 }, { oz, dz, e.z - r, e.z + r } }
      for i = 1, 3 do
        local o, v, lo, hi = bounds[i][1], bounds[i][2], bounds[i][3], bounds[i][4]
        if abs(v) < 1e-9 then
          if o < lo or o > hi then ok = false break end
        else
          local a, b = (lo - o) / v, (hi - o) / v
          if a > b then a, b = b, a end
          if a > tmin then tmin = a end
          if b < tmax then tmax = b end
          if tmin > tmax then ok = false break end
        end
      end
      if ok and tmin < bestT then best, bestT = e, tmin end
    end
  end
  return best, bestT
end

local function fire()
  local w = WEAPONS[P.weapon]
  if now < P.nextFire or P.dead then return end
  if w.ammo and P.ammo[w.ammo] <= 0 then
    P.nextFire = now + 0.4
    sfx(1, 1400, 0.03, 0.4, "square")
    for i = #WEAPONS, 1, -1 do
      local o = WEAPONS[i]
      if P.have[i] and (not o.ammo or P.ammo[o.ammo] > 0) then P.weapon = i say(o.name) break end
    end
    return
  end
  if w.ammo then P.ammo[w.ammo] = P.ammo[w.ammo] - 1 end
  P.nextFire = now + w.rate
  P.kick = w.kick
  P.fireT = now
  local ex, ey, ez = eyePos()
  local fx, fy, fz = forward(P.yaw, P.pitch)
  local rx, rz = -sin(P.yaw), cos(P.yaw)
  local ux, uy, uz = -rz * fy, rz * fx - rx * fz, rx * fy
  local tipX = ex + fx * 0.5 + rx * 0.14 - ux * 0.12
  local tipY = ey + fy * 0.5 - uy * 0.12
  local tipZ = ez + fz * 0.5 + rz * 0.14 - uz * 0.12
  flashLight(tipX, tipY, tipZ, w.proj and 4 or 5, w.proj and 0x60d0ff or 0xffc070, 1.0, 0.07)
  for _, e in ipairs(enemies) do
    if not e.dead and not e.alert then
      local d = dist3(e.x, e.y, e.z, P.x, P.y, P.z)
      if d < 9 or (d < 20 and Lv.sees(e.x, e.y + 1, e.z, ex, ey, ez)) then e.alert = true end
    end
  end
  if w.proj then
    shots[#shots + 1] = { x = tipX, y = tipY, z = tipZ, vx = fx * 22, vy = fy * 22, vz = fz * 22, owner = "player",
                          tex = T.plasma, size = 0.42, dmg = w.dmg, splash = w.splash, life = 3, light = 0x40c8ff }
    sfx(1, 260, 0.35, 0.9, "sawtooth", 1100)
    return
  end
  if P.weapon == 2 then sfx(1, 700, 0.32, 1, "noise", 70) else sfx(1, 1700, 0.12, 0.7, "square", 380) end
  for _ = 1, w.pellets do
    local a, s = rnd() * pi * 2, w.spread * sqrt(rnd())
    local ox, oy = cos(a) * s, sin(a) * s
    local dx, dy, dz = fx + rx * ox + ux * oy, fy + uy * oy, fz + rz * ox + uz * oy
    local l = sqrt(dx * dx + dy * dy + dz * dz)
    dx, dy, dz = dx / l, dy / l, dz / l
    local wall = Lv.ray(ex, ey, ez, dx, dy, dz, 60)
    local e, t = rayEnemy(ex, ey, ez, dx, dy, dz, wall)
    local hx, hy, hz = ex + dx * (e and t or wall), ey + dy * (e and t or wall), ez + dz * (e and t or wall)
    if e then
      hurtEnemy(e, w.dmg, dx, dz)
      particle(e.def.machine and T.spark or T.oil, hx, hy, hz, dx * -2 + rnd() - 0.5, 1.5, dz * -2 + rnd() - 0.5, 0.5, 0.14, 9)
    else
      particle(T.spark, hx - dx * 0.05, hy - dy * 0.05, hz - dz * 0.05, -dx * 2, 1.5, -dz * 2, 0.25, 0.1, 6, true)
      if rnd() < 0.5 then particle(T.smoke, hx - dx * 0.1, hy - dy * 0.1, hz - dz * 0.1, 0, 0.4, 0, 0.6, 0.3, -0.3) end
    end
  end
end

-- ======================= враги =======================
local flowT = -1
local spawnEnemy

--- Выстрел врага в точку (tx, ty, tz); spread — разлёт по сторонам (рад).
local function enemyShoot(e, tx, ty, tz, tvx, tvz, spread)
  local d = e.def
  local sx, sy, sz
  if d.muzzle then
    local m = d.muzzle
    local c, s = cos(e.yaw), sin(e.yaw)
    sx, sy, sz = e.x + m[1] * c - m[3] * s, e.y + m[2], e.z + m[1] * s + m[3] * c
  else
    local fx, fy, fz = forward(e.yaw, e.pitch or 0)
    sx, sy, sz = e.x + fx * 0.5, e.y + fy * 0.5, e.z + fz * 0.5
  end
  local dd = dist3(sx, sy, sz, tx, ty, tz)
  local tt = dd / d.speedShot * 0.6
  tx, tz = tx + (tvx or 0) * tt + (rnd() - 0.5) * 0.6, tz + (tvz or 0) * tt + (rnd() - 0.5) * 0.6
  local dx, dy, dz = tx - sx, ty - sy, tz - sz
  if spread and spread ~= 0 then
    local c, s = cos(spread), sin(spread)
    dx, dz = dx * c - dz * s, dx * s + dz * c
  end
  local l = sqrt(dx * dx + dy * dy + dz * dz)
  local sp = d.speedShot
  local fireShot = d.shot == "fire"
  shots[#shots + 1] = { x = sx, y = sy, z = sz, vx = dx / l * sp, vy = dy / l * sp, vz = dz / l * sp, owner = e,
                        tex = fireShot and T.fire or T.bolt, size = fireShot and 0.4 or (d.boss and 0.45 or 0.26),
                        dmg = d.dmg, splash = fireShot and 1.3 or nil, life = 4,
                        light = fireShot and 0xff8030 or (d.boss and 0x60ff80 or 0xff3020) }
  local vol = max(0.1, 0.7 - dist3(sx, sy, sz, P.x, P.y, P.z) / 40)
  if fireShot then sfx(2, 420, 0.25, vol, "triangle", 160) else sfx(2, d.machine and 2200 or 1500, 0.08, vol, "square", 700) end
end

--- Турель: стреляет во всех подряд — в игрока и в тварей, кто ближе и на виду.
local function updateTurret(e, dt)
  local d = e.def
  if e.off then e.anim = e.anim + dt * 0.2 return end
  if now >= e.lookT then
    e.lookT = now + 0.25
    local best, bd = nil, d.sight
    local ey = e.y + d.eyeY
    if not P.dead then
      local pd = dist3(e.x, ey, e.z, P.x, P.y + 1.2, P.z)
      if pd < bd and Lv.sees(e.x, ey, e.z, P.x, P.y + 1.3, P.z) then best, bd = "player", pd end
    end
    for _, o in ipairs(enemies) do
      if o ~= e and not o.dead and not o.def.machine then
        local y1, y2 = bodyY(o)
        local od = dist3(e.x, ey, e.z, o.x, (y1 + y2) / 2, o.z)
        if od < bd and Lv.sees(e.x, ey, e.z, o.x, (y1 + y2) / 2, o.z) then best, bd = o, od end
      end
    end
    e.target = best
  end
  local t = e.target
  if t == nil or (t ~= "player" and t.dead) or (t == "player" and P.dead) then e.anim = e.anim + dt return end
  local tx, ty, tz, tvx, tvz
  if t == "player" then tx, ty, tz, tvx, tvz = P.x, P.y + 1.0, P.z, P.vx, P.vz
  else local y1, y2 = bodyY(t) tx, ty, tz = t.x, (y1 + y2) / 2, t.z end
  local want = atan2(tz - e.z, tx - e.x)
  local diff = (want - e.yaw + pi) % (2 * pi) - pi
  e.yaw = e.yaw + max(-dt * 3, min(dt * 3, diff))
  if abs(diff) < 0.25 and now >= e.atkT then
    e.atkT = now + d.rate
    e.swingT = now
    enemyShoot(e, tx, ty, tz, tvx, tvz)
  end
end

--- Босс: висит над серединой пещеры, залпы веером, удар щупальцами вблизи, зовёт тварей на 60% и 30%.
local function updateBoss(e, dt)
  local d = e.def
  local dx, dz = P.x - e.x, P.z - e.z
  local dxz = sqrt(dx * dx + dz * dz)
  e.alert = true
  e.anim = e.anim + dt
  local want = atan2(dz, dx)
  local diff = (want - e.yaw + pi) % (2 * pi) - pi
  e.yaw = e.yaw + max(-dt * 1.2, min(dt * 1.2, diff))
  -- медленно плывёт за игроком, не дальше 3 м от своего места
  local hx, hz = e.homeX + dx / max(1, dxz) * 3, e.homeZ + dz / max(1, dxz) * 3
  local mx, mz = hx - e.x, hz - e.z
  local ml = sqrt(mx * mx + mz * mz)
  if ml > 0.1 then e.x, e.z = e.x + mx / ml * min(ml, d.speed * dt), e.z + mz / ml * min(ml, d.speed * dt) end
  if state ~= "play" or P.dead then return end
  local frac = e.hp / d.hp
  local phase = frac > 0.6 and 1 or (frac > 0.3 and 2 or 3)
  if phase > (e.phase or 1) then
    e.phase = phase
    say(phase == 2 and "Сердце зовёт ползунов из разлома!" or "Сердце в ярости: летят очи!", 3)
    sfx(2, 90, 1.2, 1, "sawtooth", 50)
  end
  local exposed = now < (e.exposedT or 0)
  -- залп веером
  if now >= e.atkT and not exposed then
    e.atkT = now + d.rate * (phase == 3 and 0.65 or 1)
    e.swingT = now
    local n = phase == 1 and 5 or 7
    for i = 1, n do
      enemyShoot(e, P.x, P.y + 1.0, P.z, 0, 0, (i - (n + 1) / 2) * 0.13)
    end
  end
  -- удар щупальцами: волна по полу вокруг
  if dxz < 4.2 and abs(P.y - e.y) < 2 and now >= (e.slamT or 0) then
    e.slamT = now + 2.4
    burst(T.oil, e.x, e.y + 0.2, e.z, 16, 6, 0.6, 0.2, 8)
    flashLight(e.x, e.y + 0.5, e.z, 6, 0x80ff90, 1.2, 0.3)
    sfx(2, 70, 0.5, 1, "noise", 40)
    hurtPlayer(18, e.x, e.z)
    P.vx, P.vz, P.vy = P.vx + dx / max(0.5, dxz) * 9, P.vz + dz / max(0.5, dxz) * 9, 5
    P.onGround, P.air = false, true
  end
  -- твари из разлома
  if phase >= 2 and now >= (e.spawnT or 0) then
    e.spawnT = now + (phase == 3 and 9 or 12)
    local alive = 0
    for _, o in ipairs(enemies) do if not o.dead and o ~= e and not o.def.machine then alive = alive + 1 end end
    if alive < 6 then
      for i = 1, 2 do
        local a = rnd() * pi * 2
        local kind = (phase == 3 and i == 2) and "eye" or "crawler"
        local sx, sz = e.homeX + cos(a) * 2.5, e.homeZ + sin(a) * 2.5
        if Lv.cellAt(sx, sz) then
          local o = spawnEnemy(kind, sx, kind == "eye" and e.y + 3 or Lv.ground(sx, sz, 0.3, e.y + 1), sz)
          o.alert = true
          burst(T.glowGreen, sx, e.y + 0.5, sz, 8, 3, 0.6, 0.25, -1, true)
        end
      end
    end
  end
end

local function updateEnemy(e, dt)
  local d = e.def
  if e.dead then return end
  if d.static then return updateTurret(e, dt) end
  if d.boss then return updateBoss(e, dt) end
  local dx, dz = P.x - e.x, P.z - e.z
  local dxz = sqrt(dx * dx + dz * dz)
  local ey = d.fly and e.y or e.y + d.eyeY
  if now >= e.lookT then
    e.lookT = now + 0.3 + rnd() * 0.2
    local facing = e.alert or dxz < 4 or abs((atan2(dz, dx) - e.yaw + pi) % (2 * pi) - pi) < 1.1
    e.sees = not P.dead and facing and dxz < d.sight and Lv.sees(e.x, ey, e.z, P.x, P.y + 1.4, P.z)
    if e.sees then e.alert = true end
  end
  if not e.alert or state ~= "play" then
    e.anim = e.anim + dt * 0.5
    return
  end
  local want = atan2(dz, dx)
  local diff = (want - e.yaw + pi) % (2 * pi) - pi
  e.yaw = e.yaw + max(-dt * 5, min(dt * 5, diff))
  if e.push and e.push > 0 and not d.fly then
    moveBody(e, e.pushX * e.push * dt * 8, e.pushZ * e.push * dt * 8, 0)
    e.push = max(0, e.push - dt * 3)
  end

  if d.fly then
    e.orbit = e.orbit + dt * 0.6
    local tx = P.x - cos(e.orbit) * 5.5
    local tz = P.z - sin(e.orbit) * 5.5
    local ty = P.y + 2.6 + sin(now * 0.9 + e.orbit) * 0.8
    local mx, my, mz = tx - e.x, ty - e.y, tz - e.z
    local pdx, pdy, pdz = e.x - P.x, e.y - (P.y + 1.5), e.z - P.z
    local pd = sqrt(pdx * pdx + pdy * pdy + pdz * pdz)
    if pd < 3.5 and pd > 0.01 then mx, my, mz = pdx / pd * 3, pdy / pd * 3 + 1, pdz / pd * 3 end
    local ml = sqrt(mx * mx + my * my + mz * mz)
    if ml > 0.2 then
      local s = min(ml, d.speed * dt)
      local nx, ny, nz = e.x + mx / ml * s, e.y + my / ml * s, e.z + mz / ml * s
      local r = d.r
      if not (Lv.solidPoint(nx + r, ny, nz) or Lv.solidPoint(nx - r, ny, nz) or Lv.solidPoint(nx, ny, nz + r)
              or Lv.solidPoint(nx, ny, nz - r) or Lv.solidPoint(nx, ny + r, nz) or Lv.solidPoint(nx, ny - r, nz)) then
        e.x, e.y, e.z = nx, ny, nz
      elseif not Lv.solidPoint(e.x, e.y + r + 0.1, e.z) then
        e.y = e.y + s
      end
    end
    e.pitch = atan2(P.y + 1.2 - e.y, max(0.1, dxz))
    e.anim = e.anim + dt * 3
    if e.sees and now >= e.atkT then
      e.atkT = now + d.rate * (0.8 + rnd() * 0.4)
      enemyShoot(e, P.x, P.y + 1.0, P.z, P.vx, P.vz)
    end
    return
  end

  local goX, goZ
  local stand = false
  if d.shot and e.sees and dxz < 18 then
    stand = dxz > d.keep
    if dxz < d.keep - 1 then goX, goZ = -dx, -dz end
    if stand and rnd() < dt * 0.6 then e.strafe = (rnd() < 0.5) and -1 or 1 end
    if stand and e.strafe then goX, goZ = -dz * e.strafe, dx * e.strafe end
  elseif e.sees and (abs(P.y - e.y) < 1.2) then
    goX, goZ = dx, dz
  else
    local best, bx, bz = Lv.distAt(e.x, e.z), nil, nil
    if best then
      local cx, cz = floor(e.x), floor(e.z)
      for oz = -1, 1 do
        for ox = -1, 1 do
          if ox ~= 0 or oz ~= 0 then
            local v = Lv.distAt(cx + ox + 0.5, cz + oz + 0.5)
            if v and v < best and (ox == 0 or oz == 0 or (Lv.distAt(cx + ox + 0.5, cz + 0.5) and Lv.distAt(cx + 0.5, cz + oz + 0.5))) then
              best, bx, bz = v, cx + ox + 0.5, cz + oz + 0.5
            end
          end
        end
      end
    end
    if bx then goX, goZ = bx - e.x, bz - e.z else goX, goZ = dx, dz end
  end
  -- ползун петляет зигзагом — в него трудно попасть
  if d.mesh == "crawler" and goX then
    local zig = sin(now * 7 + e.orbit) * 0.7
    goX, goZ = goX - goZ * zig, goZ + goX * zig
  end
  local moving = false
  if goX and (d.melee and dxz > 1.0 or not d.melee) then
    local l = sqrt(goX * goX + goZ * goZ)
    if l > 0.05 then
      local sp = d.speed * (stand and 0.55 or 1)
      local bumped = moveBody(e, goX / l * sp * dt, goZ / l * sp * dt, dt)
      if bumped and e.onGround and e.sees and d.melee and P.y > e.y + STEP then e.vy = 6.5 e.onGround = false end
      moving = true
    else
      moveBody(e, 0, 0, dt)
    end
  else
    moveBody(e, 0, 0, dt)
  end
  if moving then e.anim = e.anim + dt * d.speed * 2.2 end
  if d.melee then
    if dxz < d.r + 0.35 + 0.75 and abs(P.y - e.y) < 1.3 and now >= e.atkT then
      e.atkT = now + d.meleeRate
      e.swingT = now
      hurtPlayer(d.melee, e.x, e.z)
      sfx(2, d.mesh == "crawler" and 600 or 160, 0.15, 0.8, "sawtooth", 60)
    end
  elseif e.sees and now >= e.atkT then
    e.atkT = now + d.rate * (0.8 + rnd() * 0.4)
    e.swingT = now
    enemyShoot(e, P.x, P.y + 1.0, P.z, P.vx, P.vz)
  end
  local c = Lv.cellAt(e.x, e.z)
  if c and c.acid and e.y <= c.f + 0.05 then
    if now >= (e.acidT or 0) then e.acidT = now + 0.5 hurtEnemy(e, 6) end
  end
end

-- ======================= снаряды и частицы =======================
local function updateShots(dt)
  local i = 1
  while i <= #shots do
    local s = shots[i]
    s.life = s.life - dt
    local hit = s.life <= 0
    local victim
    local steps = max(1, math.ceil(sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz) * dt / 0.25))
    local sdt = dt / steps
    local turretShot = s.owner ~= "player" and s.owner.def.machine
    for _ = 1, steps do
      if hit then break end
      local nx, ny, nz = s.x + s.vx * sdt, s.y + s.vy * sdt, s.z + s.vz * sdt
      if Lv.solidPoint(nx, ny, nz) then hit = true break end
      s.x, s.y, s.z = nx, ny, nz
      if s.owner == "player" or turretShot then
        for _, e in ipairs(enemies) do
          if not e.dead and e ~= s.owner then
            local y1, y2 = bodyY(e)
            local r = e.def.r + 0.2
            if abs(nx - e.x) < r and abs(nz - e.z) < r and ny > y1 - 0.2 and ny < y2 + 0.2 then hit = true victim = e break end
          end
        end
      end
      if not hit and s.owner ~= "player" and not P.dead then
        if abs(nx - P.x) < 0.5 and abs(nz - P.z) < 0.5 and ny > P.y - 0.1 and ny < P.y + 1.8 then hit = true end
      end
    end
    if hit then
      if s.splash and s.owner == "player" then
        explode(s.x, s.y, s.z, s.splash, s.dmg, "player")
      elseif s.owner ~= "player" then
        if victim then hurtEnemy(victim, s.dmg)
        elseif not P.dead and dist3(s.x, s.y, s.z, P.x, P.y + 0.9, P.z) < (s.splash or 0.9) + 0.4 then
          hurtPlayer(s.dmg, s.x, s.z)
        end
        burst(s.tex, s.x, s.y, s.z, 5, 3, 0.3, 0.2, 2, true)
        flashLight(s.x, s.y, s.z, 3, s.light, 1, 0.2)
      end
      table.remove(shots, i)
    else
      if s.owner == "player" and rnd() < 0.6 then particle(T.plasma, s.x, s.y, s.z, 0, 0, 0, 0.18, 0.18, 0, true) end
      i = i + 1
    end
  end
end

local function updateParts(dt)
  local i = 1
  while i <= #parts do
    local p = parts[i]
    p.life = p.life - dt
    if p.life <= 0 then
      table.remove(parts, i)
    else
      p.vy = p.vy - p.grav * dt
      local nx, ny, nz = p.x + p.vx * dt, p.y + p.vy * dt, p.z + p.vz * dt
      if p.grav > 0 and Lv.solidPoint(nx, ny, nz) then
        p.vx, p.vy, p.vz = p.vx * 0.3, 0, p.vz * 0.3
      else
        p.x, p.y, p.z = nx, ny, nz
      end
      i = i + 1
    end
  end
  i = 1
  while i <= #flashes do
    local f = flashes[i]
    f.life = f.life - dt
    if f.life <= 0 then table.remove(flashes, i) else i = i + 1 end
  end
end

-- ======================= предметы, двери, пульты =======================
local function pickup(it)
  local k = it.kind
  local def = ITEMS[k]
  if def.key then
    P.keys[def.key] = true
    return true
  elseif k == "medkit" then
    if P.hp >= 100 then return false end
    P.hp = min(100, P.hp + 25)
  elseif k == "armor" then
    if P.armor >= 100 then return false end
    P.armor = 100
  elseif k == "shells" then
    if P.ammo.shells >= 60 then return false end
    P.ammo.shells = min(60, P.ammo.shells + 12)
  elseif k == "cells" then
    if P.ammo.cells >= 50 then return false end
    P.ammo.cells = min(50, P.ammo.cells + 10)
  elseif k == "shotgun" then
    if P.have[2] and P.ammo.shells >= 60 then return false end
    P.have[2] = true
    P.ammo.shells = min(60, P.ammo.shells + 12)
    P.weapon = 2
  elseif k == "plasmagun" then
    if P.have[3] and P.ammo.cells >= 50 then return false end
    P.have[3] = true
    P.ammo.cells = min(50, P.ammo.cells + 15)
    P.weapon = 3
  end
  return true
end

local function updateItems()
  for _, it in ipairs(items) do
    if not it.taken and not P.dead then
      if abs(it.x - P.x) < 0.75 and abs(it.z - P.z) < 0.75 and P.y > it.y - 1 and P.y < it.y + 1.2 then
        if pickup(it) then
          it.taken = true
          P.pickT = now
          say(ITEMS[it.kind].name)
          if it.kind == "shotgun" or it.kind == "plasmagun" or ITEMS[it.kind].key then arpeggio({ 392, 523, 659 }, 70)
          else sfx(3, 620, 0.15, 0.6, "square", 1250) end
        end
      end
    end
  end
end

local function openDoor(d)
  if d.opening then return end
  d.opening = true
  sfx(3, 160, 0.9, 0.7, "sawtooth", 260)
end

local doorHintT = 0
local function updateDoors(dt)
  for _, d in pairs(Lv.doors) do
    if d.opening and d.open < 1 then d.open = min(1, d.open + dt * 1.1) end
    if not d.opening and not P.dead then
      for _, c in ipairs(d.cells) do
        local dx, dz = c[1] + 0.5 - P.x, c[2] + 0.5 - P.z
        if dx * dx + dz * dz < 1.8 * 1.8 and abs(P.y - d.f) < 1.5 then
          if d.key and P.keys[d.key] then openDoor(d)
          elseif now >= doorHintT then
            doorHintT = now + 2.5
            say(d.key and ("Нужна " .. KEYNAME[d.key] .. " ключ-карта") or "Эта дверь открывается с пульта")
          end
          break
        end
      end
    end
  end
end

--- Пульт: target = "door:знак" — открыть дверь, "turrets:группа" — выключить турели, "ring" — излучатель обруча.
local function useConsole(c)
  local kind, arg = c.target:match("^(%a+):?(.*)$")
  if kind == "ring" then
    if now < c.readyT then say(string.format("Излучатель заряжается: %d с", math.ceil(c.readyT - now))) return end
    local boss
    for _, e in ipairs(enemies) do if e.def.boss and not e.dead then boss = e end end
    if not boss then say("Излучатель молчит") return end
    c.readyT = now + 16
    c.beamT = now + 7
    boss.exposedT = now + 7
    say("Обруч вскрыл кристалл! Стреляйте в Сердце!", 3)
    sfx(3, 300, 1.2, 0.9, "sine", 1600)
    return
  end
  if c.used then say("Уже сделано") return end
  c.used = true
  if kind == "door" then
    local d = Lv.doors[arg]
    if d then openDoor(d) end
    say(c.label or "Дверь открыта")
  elseif kind == "turrets" then
    local n = 0
    for _, e in ipairs(enemies) do
      if e.def.static and e.group == arg and not e.dead then e.off = true n = n + 1 end
    end
    say(c.label or ("Турели отключены: " .. n))
  end
  sfx(3, 880, 0.2, 0.6, "square", 440)
end

local function nearConsole()
  for _, c in ipairs(consoles) do
    local dx, dz = c.x - P.x, c.z - P.z
    if dx * dx + dz * dz < 1.7 * 1.7 and abs(P.y - c.y) < 1.4 then return c end
  end
end

-- ======================= игрок =======================
local keys = {}
local mouseFire, dragX, dragY = false, nil, nil
local mouseLocked = nil
local MOUSE_RAD = pi / 180

local consoleHintT = 0
local function updatePlayer(dt)
  if P.dead then
    P.vx, P.vz = P.vx * 0.9, P.vz * 0.9
    moveBody(P, P.vx * dt, P.vz * dt, dt)
    return
  end
  local turn = (keys.right and 1 or 0) - (keys.left and 1 or 0)
  P.yaw = P.yaw + turn * 2.5 * dt
  local look = (keys.up and 1 or 0) - (keys.down and 1 or 0)
  P.pitch = max(-1.35, min(1.35, P.pitch + look * 1.7 * dt))
  local fwd = (keys.w and 1 or 0) - (keys.s and 1 or 0)
  local side = (keys.d and 1 or 0) - (keys.a and 1 or 0)
  local c, s = cos(P.yaw), sin(P.yaw)
  local wx, wz = c * fwd - s * side, s * fwd + c * side
  local wl = sqrt(wx * wx + wz * wz)
  if wl > 0 then wx, wz = wx / wl, wz / wl end
  local SPEED = 6.2
  if P.onGround then
    local k = min(1, 12 * dt)
    P.vx = P.vx + (wx * SPEED - P.vx) * k
    P.vz = P.vz + (wz * SPEED - P.vz) * k
    P.air = false
  elseif wl > 0 then
    local k = min(1, (P.air and 1.2 or 3) * dt)
    local tvx, tvz = P.vx + (wx * SPEED - P.vx) * k, P.vz + (wz * SPEED - P.vz) * k
    if P.air and tvx * tvx + tvz * tvz < P.vx * P.vx + P.vz * P.vz then
      P.vx, P.vz = P.vx + (tvx - P.vx) * 0.5, P.vz + (tvz - P.vz) * 0.5
    else
      P.vx, P.vz = tvx, tvz
    end
  end
  if keys.space and P.onGround and now >= P.jumpT then
    P.vy = 6.4
    P.onGround = false
    P.jumpT = now + 0.3
    sfx(4, 210, 0.12, 0.4, "triangle", 440)
  end
  local wasGround = P.onGround
  local bumped = moveBody(P, P.vx * dt, P.vz * dt, dt)
  if bumped and not P.onGround then P.vx, P.vz = P.vx * 0.7, P.vz * 0.7 end
  if P.onGround and not wasGround and (P.landSpeed or 0) > 7 then
    sfx(4, 120, 0.1, 0.6, "noise", 60)
    if P.landSpeed > 15 then hurtPlayer(floor((P.landSpeed - 15) * 3)) end
  end
  local hs = sqrt(P.vx * P.vx + P.vz * P.vz)
  if P.onGround and hs > 0.5 then
    local before = floor(P.bob / pi)
    P.bob = P.bob + dt * hs * 1.7
    if floor(P.bob / pi) ~= before then sfx(4, 140 + rnd(0, 30), 0.04, 0.35, "noise", 60) end
  end
  -- клетка под ногами: прыжковая площадка, кислота
  local cell = Lv.cellAt(P.x, P.z)
  if cell and P.onGround and P.y <= cell.f + 0.05 then
    if cell.pad then
      P.vy = cell.pad[1]
      P.vx, P.vz = cell.pad[2], cell.pad[3]
      P.onGround = false
      P.air = true
      sfx(3, 140, 0.45, 0.8, "sine", 900)
    elseif cell.acid and now >= P.acidT then
      P.acidT = now + 0.5
      P.acidHurtT = now
      hurtPlayer(7)
    end
  end
  -- пульт рядом: подсказка, E — включить
  local con = nearConsole()
  if con and now >= consoleHintT and not (message and now < messageT) then
    consoleHintT = now + 3
    say("E — " .. (con.hint or "пульт"), 2)
  end
  if keys.e and con and not P.eHeld then useConsole(con) end
  P.eHeld = keys.e
  if keys.ctrl or keys.f or mouseFire then fire() end
  P.kick = max(0, P.kick - dt * 1.6)
  -- выход
  local ex = Lv.exit
  if portalOpen and state == "play" and ex then
    if abs(P.x - ex.x) < 0.8 and abs(P.z - ex.z) < 0.8 and abs(P.y - ex.y) < 1.2 then
      stats.time = now - t0
      run.time = run.time + stats.time
      run.kills = run.kills + stats.kills
      run.taken = run.taken + stats.taken
      state = levelNo < LEVELS and "next" or "won"
      say("", 0)
      arpeggio({ 523, 659, 784, 1047, 1319 }, 120)
    end
  end
end

-- ======================= новый уровень =======================
local function newPlayer()
  return { x = 0, y = 0, z = 0, vx = 0, vy = 0, vz = 0, r = 0.32, h = 1.75, yaw = 0, pitch = 0,
           hp = 100, armor = 0, onGround = true, weapon = 1, have = { true, false, false }, ammo = { shells = 0, cells = 0 },
           nextFire = 0, kick = 0, bob = 0, jumpT = 0, acidT = 0, hurtT = -10, pickT = -10, fireT = -10, acidHurtT = -10 }
end

function spawnEnemy(kind, x, y, z, th)
  local d = DEF[kind]
  local e = { kind = kind, def = d, x = x, y = y, z = z, vy = 0, r = d.r, h = d.h, onGround = true,
              hp = d.hp, yaw = (th and th[5]) or rnd() * pi * 2, anim = rnd() * 6, lookT = rnd() * 0.5,
              atkT = now + 1 + rnd(), orbit = rnd() * pi * 2, light = 1, lightT = -1, alert = false,
              homeX = x, homeZ = z, group = th and th.group }
  enemies[#enemies + 1] = e
  return e
end

--- Начать уровень: игрок переносит здоровье, броню, оружие и патроны (ключи — нет).
local function startLevelState()
  local st = Lv.start
  if snapshot then
    for k, v in pairs(snapshot) do P[k] = type(v) == "table" and { table.unpack(v) } or v end
    P.ammo = { shells = snapshot.ammo.shells, cells = snapshot.ammo.cells }
    P.have = { snapshot.have[1], snapshot.have[2], snapshot.have[3] }
  end
  P.x, P.z, P.yaw, P.pitch = st.x, st.z, st.yaw or 0, 0
  P.vx, P.vy, P.vz, P.dead, P.kick = 0, 0, 0, false, 0
  local sc = Lv.cellAt(P.x, P.z)
  P.y = Lv.ground(P.x, P.z, P.r, st.y or (sc and sc.f) or 0)     -- старт может быть высоко (клеть лифта на 6 м)
  P.onGround = true
  P.keys = {}
  P.hurtT, P.pickT, P.fireT, P.acidHurtT = -10, -10, -10, -10
  now = uptime()
  for _, d in pairs(Lv.doors) do d.open, d.opening = 0, false end
  enemies, items, shots, parts, flashes, consoles = {}, {}, {}, {}, {}, {}
  for _, th in ipairs(Lv.things) do
    local kind, x, y, z = th[1], th[2], th[3], th[4]
    if DEF[kind] then
      spawnEnemy(kind, x, y, z, th)
    elseif kind == "console" then
      consoles[#consoles + 1] = { x = x, y = y, z = z, yaw = th[5] or 0, target = th.target, label = th.label, hint = th.hint,
                                  readyT = 0, beamT = 0, ex = th.ex, ey = th.ey, ez = th.ez }
    elseif ITEMS[kind] then
      items[#items + 1] = { kind = kind, x = x, y = y, z = z, phase = rnd() * 6 }
    end
  end
  local total = 0
  for _, e in ipairs(enemies) do if not e.def.machine then total = total + 1 end end
  stats = { kills = 0, total = total, taken = 0, time = 0 }
  portalOpen = (Lv.exit and Lv.exit.need == "open") or P.devPortal or false
  state = "play"
  t0 = now
  message, messageT = nil, 0
  flowT = -1
  say(Lv.data.intro or Lv.data.name, 5)
end

local function saveSnapshot()
  snapshot = {}
  for _, k in ipairs({ "hp", "armor", "weapon" }) do snapshot[k] = P[k] end
  snapshot.have = { P.have[1], P.have[2], P.have[3] }
  snapshot.ammo = { shells = P.ammo.shells, cells = P.ammo.cells }
  snapshot.hp = max(snapshot.hp, 50)                   -- после трудного уровня — хотя бы половина здоровья
end

local PROGRESS = fs.isDir("/home") and "/home/.razlom_lvl" or "/.razlom_lvl"
local function saveProgress(n)
  if mode ~= "play" then return end
  local best = 1
  if fs.exists(PROGRESS) then
    local f = fs.open(PROGRESS, "r")
    if f then best = tonumber(f.readAll()) or 1 f.close() end
  end
  if n > best then
    local f = fs.open(PROGRESS, "w")
    if f then f.write(tostring(n)) f.close() end
  end
end

local function newGame()
  P = newPlayer()
  -- с середины игры — оружие, которое к этому уровню уже было бы
  if startLevel >= 2 then P.have[2] = true P.ammo.shells = 24 P.weapon = 2 end
  if startLevel >= 3 then P.have[3] = true P.ammo.cells = 20 P.armor = 50 end
  for k in pairs(opt) do
    if k == "god" then P.god = true end
    if k == "give" then P.have = { true, true, true } P.ammo = { shells = 60, cells = 50 } P.weapon = 3 end
    if k == "portal" then P.devPortal = true end
  end
  saveSnapshot()
  startLevelState()
  -- для проверок: at=x,z[,угол] — начать в другом месте
  if opt.at then
    local x, z, yaw = opt.at:match("^([%-%d%.]+),([%-%d%.]+),?([%-%d%.]*)$")
    if x then
      P.x, P.z, P.yaw = tonumber(x), tonumber(z), tonumber(yaw) or P.yaw
      P.y = Lv.ground(P.x, P.z, P.r, 20)
    end
  end
  if opt.pitch then P.pitch = tonumber(opt.pitch) or 0 end
  if opt.weapon and P.have[tonumber(opt.weapon) or 0] then P.weapon = tonumber(opt.weapon) end
  if opt.keys then P.keys = { red = true, yellow = true, blue = true } end
end

local function nextLevel()
  saveSnapshot()
  local ok, err = pcall(loadLevel, levelNo + 1)
  if not ok then error(err, 0) end
  saveProgress(levelNo)
  startLevelState()
end

-- ======================= отрисовка =======================
local lampList = {}
local function collectLamps()
  lampList = {}
  for _, li in ipairs(Lv.lights) do
    if li.lamp then
      local c = Lv.cellAt(li[1], li[3])
      if c then lampList[#lampList + 1] = { li[1], c.c, li[3] } end
    end
  end
end
collectLamps()

local playerLight, playerLightT = 1, -1
local function lightNear(x, y, z)
  return Lv.lightAt and Lv.lightAt(x, y, z, 0, 1, 0) or 1
end

local function drawEnemy(e)
  local d = e.def
  local bright = e.light
  if now >= e.lightT then
    e.lightT = now + 0.4 + rnd() * 0.2
    e.light = max(0.35, min(1.4, lightNear(e.x, e.y + 1, e.z) * 1.15))
    bright = e.light
  end
  local flash = (e.flashT and now < e.flashT) and 0x902018 or 0
  if d.fly then
    local y = e.y
    if e.dead then
      local t = now - e.deadT
      if t > 1.5 then return end
      y = e.y - t * t * 4
      local g = Lv.ground(e.x, e.z, 0.1, e.y)
      if y < g + 0.3 then y = g + 0.3 end
    else
      y = y + sin(e.anim) * 0.12
    end
    cv:mesh(M.eye, e.x, y, e.z, e.yaw, e.pitch or 0, 1, bright, flash)
    if not e.dead then
      local c, s = cos(e.yaw), sin(e.yaw)
      for i = -1, 1 do
        local ox, oz = (i == 0) and 0.1 or -0.08, i * 0.14
        cv:mesh(M.tentacle, e.x + ox * c - oz * s, y - 0.3, e.z + ox * s + oz * c, e.yaw, sin(e.anim + i) * 0.5, 1, bright, flash)
      end
    end
    return
  end
  if d.static then                                    -- турель: выключенная — тусклая и опустила стволы
    if e.dead then
      if now - e.deadT > 10 then return end
      cv:mesh(M.turret, e.x, e.y, e.z, e.yaw, -0.5, 1, bright * 0.4, 0)
      return
    end
    local kick = (e.swingT and now - e.swingT < 0.08) and 0.04 or 0
    cv:mesh(M.turret, e.x, e.y, e.z, e.yaw, e.off and -0.35 or kick, 1, e.off and bright * 0.55 or bright, flash)
    return
  end
  if d.boss then
    local t = e.dead and (now - e.deadT) or 0
    if t > 3 then return end
    local exposed = now < (e.exposedT or 0)
    local y = e.y + sin(e.anim * 1.3) * 0.15 - t * t * 0.4
    local glow = exposed and floor(30 + sin(now * 12) * 25) * 256 or 0
    cv:mesh(M.boss, e.x, y, e.z, e.yaw, 0, 1, max(0.8, bright), flash ~= 0 and flash or glow)
    return
  end
  if d.mesh then                                      -- ползун: одна сетка, извивается
    local y, pitch = e.y, 0
    if e.dead then
      local t = now - e.deadT
      if t > 5 then return end
      cv:mesh(M[d.mesh], e.x, y, e.z, e.yaw, 0, 1, bright * 0.6, flash)
      return
    end
    local wig = sin(e.anim * 2.2) * 0.25
    if e.swingT and now - e.swingT < 0.2 then pitch = 0.3 end
    cv:mesh(M[d.mesh], e.x, y, e.z, e.yaw + wig, pitch, 1, bright, flash)
    return
  end
  local y, pitch = e.y, 0
  if e.dead then
    local t = now - e.deadT
    pitch = -min(1.45, t * 4)
    if t > 6 then y = y - (t - 6) * 0.5 end
    if t > 8 then return end
    cv:mesh(M[d.body], e.x, y + 0.1, e.z, e.yaw, pitch, 1, bright * 0.8, flash)
    return
  end
  if e.swingT and now - e.swingT < 0.25 then pitch = -0.25 * sin((now - e.swingT) / 0.25 * pi) end
  local bobY = abs(sin(e.anim)) * 0.05
  cv:mesh(M[d.body], e.x, y + bobY, e.z, e.yaw, pitch, 1, bright, flash)
  local c, s = cos(e.yaw), sin(e.yaw)
  for side = -1, 1, 2 do
    local hx, hz = -s * side * d.hipW, c * side * d.hipW
    cv:mesh(M[d.leg], e.x + hx, y + d.hip + bobY, e.z + hz, e.yaw, sin(e.anim) * 0.55 * side, 1, bright, flash)
  end
end

local function hud(fps)
  gpu.setBackground(0x0c0b10)
  gpu.fill(1, H - 1, W, 2, " ")
  local x = 2
  local function put(text, color)
    gpu.setForeground(color)
    gpu.set(x, H - 1, text)
    x = x + unicode.len(text) + 2
  end
  put(string.format("♥ %3d", P.hp), P.hp > 30 and 0x60e070 or 0xff5040)
  put(string.format("◈ %3d", P.armor), P.armor > 0 and 0x6fb8ff or 0x5d5775)
  local w = WEAPONS[P.weapon]
  put(w.name .. (w.ammo and (": " .. P.ammo[w.ammo]) or ": ∞"), 0xffc857)
  put(string.format("Враги %d/%d", stats.total - stats.kills, stats.total), 0xd0d4da)
  for _, k in ipairs({ "red", "yellow", "blue" }) do
    if P.keys[k] then gpu.setForeground(KEYCOL[k]) gpu.set(x, H - 1, "▮") x = x + 2 end
  end
  local tsec = ((state == "won" or state == "next") and stats.time or (now - t0))
  put(string.format("%d:%02d", floor(tsec / 60), floor(tsec % 60)), 0x8d87a3)
  x = 2
  gpu.setForeground(0x5d5775)
  local slots = ""
  for i, _ in ipairs(WEAPONS) do
    if P.have[i] then slots = slots .. (i == P.weapon and "[" .. i .. "]" or " " .. i .. " ") end
  end
  gpu.set(x, H, slots)
  local hint = "Ур. " .. levelNo .. " · Мышь обзор · WASD · пробел прыжок · ЛКМ огонь · E пульт · P пауза · Q выход"
  if unicode.len(hint) > W - 26 then hint = "Ур. " .. levelNo .. " · WASD · ЛКМ огонь · E пульт · Q выход" end
  gpu.set(14, H, hint)
  gpu.setForeground(0x5d5775)
  gpu.set(W - 9, H, string.format("%3d к/с", fps))
end

local function centerText(y, text, fg, bg)
  local n = unicode.len(text)
  gpu.setForeground(fg)
  gpu.setBackground(bg or 0x0c0b10)
  gpu.set(max(1, floor((W - n) / 2) + 1), y, text)
end

local function pathAt(t)
  local PATH = Lv.data.tour or { { Lv.start.x, 1.6, Lv.start.z, 0, 0 }, { Lv.start.x, 1.6, Lv.start.z, 6.28, 0 } }
  local n = #PATH - 1
  local seg = (t / 3.2) % n
  local i = floor(seg)
  local f = seg - i
  local a, b = PATH[i + 1], PATH[i + 2]
  f = f * f * (3 - 2 * f)
  local function lerpAng(x, y) local d = (y - x + pi) % (2 * pi) - pi return x + d * f end
  return a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f, lerpAng(a[4], b[4]), a[5] + (b[5] - a[5]) * f
end

--- Луч света от излучателя к боссу: цепочка светящихся точек.
local function beam(x1, y1, z1, x2, y2, z2)
  local d = dist3(x1, y1, z1, x2, y2, z2)
  local n = max(2, floor(d / 0.4))
  local wob = sin(now * 30) * 0.03
  for i = 0, n do
    local k = i / n
    cv:billboard(T.plasma, x1 + (x2 - x1) * k, y1 + (y2 - y1) * k + wob, z1 + (z2 - z1) * k, 0.3, 0.3, 0.9, 0, 1)
  end
end

local function render(fps, cam)
  cv:clear(FOG)
  local ex, ey, ez, yaw, pitch
  if cam then
    ex, ey, ez, yaw, pitch = cam[1], cam[2], cam[3], cam[4], cam[5]
  else
    ex, ey, ez = eyePos()
    if P.onGround and not P.dead then ey = ey + sin(P.bob * 2) * 0.035 end
    yaw, pitch = P.yaw, P.pitch + P.kick * 0.25
  end
  cv:view(ex, ey, ez, yaw, pitch, 95)
  cv:fog(Lv.data.fog or FOG, Lv.data.fogNear or 14, Lv.data.fogFar or 34)
  cv:clearLights()
  local nl = 0
  for _, g in ipairs(Lv.glows) do
    if nl < 12 then cv:light(g[1], g[2], g[3], g[4], g[5], g[6] + sin(now * 2.3 + g[1]) * 0.12) nl = nl + 1 end
  end
  local exit = Lv.exit
  if portalOpen and exit then cv:light(exit.x, exit.y + 1.2, exit.z, 6, 0xb060ff, 1.1) nl = nl + 1 end
  for i = #flashes, 1, -1 do
    local f = flashes[i]
    if nl < 16 then cv:light(f.x, f.y, f.z, f.r, f.c, f.p * f.life / f.max) nl = nl + 1 end
  end
  for i = 1, #shots do
    local s = shots[i]
    if nl < 16 then cv:light(s.x, s.y, s.z, 3.5, s.light, 0.9) nl = nl + 1 end
  end
  for i = 1, #levelMeshes do cv:mesh(levelMeshes[i].id, 0, 0, 0) end
  if acidMesh then
    local g = floor(20 + sin(now * 3) * 18)
    cv:mesh(acidMesh, 0, 0, 0, 0, 0, 1, 1, g * 256 + floor(g / 3))
  end
  for ch, id in pairs(doorMeshes) do
    local d = Lv.doors[ch]
    if d.open < 1 then cv:mesh(id, 0, d.open * (d.c - d.f - 0.05), 0) end
  end
  for i = 1, #lampList do
    local l = lampList[i]
    cv:mesh(M.lampBox, l[1], l[2], l[3], 0, 0, 1, 1.8)
  end
  for _, c in ipairs(consoles) do
    local ready = c.target == "ring" and now >= c.readyT or (c.target ~= "ring" and not c.used)
    cv:mesh(M.console, c.x, c.y, c.z, c.yaw, 0, 1, ready and 1.25 or 0.7, ready and 0x002810 or 0x200000)
    if c.ex and now < c.beamT then
      for _, e in ipairs(enemies) do
        if e.def.boss and not e.dead then beam(c.ex, c.ey, c.ez, e.x, e.y + 2.1, e.z) end
      end
    end
  end
  for _, it in ipairs(items) do
    if not it.taken then
      local d = ITEMS[it.kind]
      local y = it.y + (d.noBob and 0.02 or (0.3 + sin(now * 2 + it.phase) * 0.1))
      cv:mesh(M[d.mesh], it.x, y, it.z, now * 1.6 + it.phase, 0, d.scale or 1, 1.25)
    end
  end
  for _, e in ipairs(enemies) do drawEnemy(e) end
  if portalOpen and exit then
    local k = 0.85 + sin(now * 4) * 0.15
    cv:billboard(T.portal, exit.x, exit.y + 1.1, exit.z, 1.3 * k, 2.1 * k, 1.2, 0, 0)
    cv:billboard(T.glowPurple, exit.x, exit.y + 1.1, exit.z, 2.6, 3.0, 0.8, 0, 1)
    if rnd() < 0.5 then particle(T.glowPurple, exit.x + (rnd() - 0.5) * 1.2, exit.y + 0.2, exit.z + (rnd() - 0.5) * 1.2, 0, 1.5, 0, 0.8, 0.15, 0, true) end
  end
  for i = 1, #shots do
    local s = shots[i]
    cv:billboard(s.tex, s.x, s.y, s.z, s.size, s.size, 1, 0, 1)
  end
  for i = 1, #parts do
    local p = parts[i]
    local k = p.life / p.max
    local sz = p.size * (p.glow and (0.6 + k * 0.6) or (1.4 - k * 0.4))
    cv:billboard(p.tex, p.x, p.y, p.z, sz, sz, p.glow and k or 1, 0, p.glow and 1 or 0)
  end
  if not cam and not P.dead then
    local w = WEAPONS[P.weapon]
    if now - P.fireT < 0.06 then
      local fx, fy, fz = forward(P.yaw, P.pitch)
      local rx, rz = -sin(P.yaw), cos(P.yaw)
      local d = w.hold[3] + w.tip * w.scale + 0.05
      cv:billboard(w.proj and T.plasma or T.flash, ex + fx * d + rx * w.hold[1], ey + fy * d + w.hold[2] * 0.8, ez + fz * d + rz * w.hold[1],
        0.18, 0.18, 1, 0, 1)
    end
    cv:clearDepth()
    if now >= playerLightT then
      playerLightT = now + 0.25
      playerLight = max(0.45, min(1.5, lightNear(P.x, P.y + 1.4, P.z) * 1.2))
    end
    local bx = sin(P.bob) * 0.012
    local by = -abs(cos(P.bob)) * 0.01
    local h = w.hold
    local glow = (now - P.fireT < 0.08) and 0.5 or 0
    cv:held(M[w.mesh], h[1] + bx, h[2] + by - P.kick * 0.04, h[3] - P.kick * 0.06, -0.05, P.kick * 0.8, w.scale, playerLight + glow)
  end
  if not cam and not P.dead then
    local cx, cy = floor(CW / 2), floor(CH / 2)
    for _, o in ipairs({ { -2, 0 }, { 2, 0 }, { 0, -2 }, { 0, 2 } }) do cv:pixel(cx + o[1], cy + o[2], 0xd8ffd0) end
  end
  if not cam then
    if P.dead then cv:tint(0x600000, min(0.55, (now - P.deadT) * 0.6))
    elseif now - P.hurtT < 0.35 then cv:tint(0xff2010, 0.35 * (1 - (now - P.hurtT) / 0.35))
    elseif now - P.pickT < 0.25 then cv:tint(0xffd040, 0.22 * (1 - (now - P.pickT) / 0.25)) end
    if now - P.acidHurtT < 0.5 then cv:tint(0x30ff40, 0.15) end
  end
  cv:present(1, 1)
  if cam then
    gpu.setBackground(0x0c0b10)
    gpu.fill(1, H - 1, W, 2, " ")
    gpu.setForeground(0xff8a3d)
    gpu.set(2, H - 1, "Р А З Л О М")
    gpu.setForeground(0x8d87a3)
    gpu.set(16, H - 1, "Демо: облёт · уровень " .. levelNo .. " · Q — выход")
    gpu.setForeground(0x5d5775)
    gpu.set(W - 9, H - 1, string.format("%3d к/с", fps))
  else
    hud(fps)
  end
  -- полоса здоровья босса
  for _, e in ipairs(enemies) do
    if e.def.boss and not e.dead and e.alert then
      local bw = min(40, W - 10)
      local bx = floor((W - bw) / 2) + 1
      local f = max(0, e.hp / e.def.hp)
      centerText(4, " СЕРДЦЕ РАЗЛОМА ", 0xffffff, now < (e.exposedT or 0) and 0x208040 or 0x6a1830)
      gpu.setBackground(0x2a2733) gpu.fill(bx, 5, bw, 1, " ")
      gpu.setBackground(now < (e.exposedT or 0) and 0x60ff80 or 0xd03050) gpu.fill(bx, 5, max(1, floor(bw * f)), 1, " ")
    end
  end
  if message and now < messageT and message ~= "" then centerText(2, " " .. message .. " ", 0xffffff, 0x2a2733) end
  if state == "next" then
    local y = floor((H - HUD) / 2) - 2
    local tsec = stats.time
    centerText(y, "  Уровень " .. levelNo .. " пройден!  ", 0x14111d, 0xffc857)
    centerText(y + 2, string.format("  Время %d:%02d · враги %d/%d · урон получен %d  ", floor(tsec / 60), floor(tsec % 60),
      stats.kills, stats.total, stats.taken), 0xffffff, 0x2a2733)
    centerText(y + 4, "  Пробел или Enter — дальше · Q — выход  ", 0xb9b3cc, 0x2a2733)
  elseif state == "won" then
    local tsec = run.time
    local y = floor((H - HUD) / 2) - 3
    centerText(y, "  Станция «Глубина-7» спасена. Разлом закрыт!  ", 0x14111d, 0xffc857)
    centerText(y + 2, string.format("  Всё время %d:%02d · враги %d · урон получен %d  ", floor(tsec / 60), floor(tsec % 60),
      run.kills, run.taken), 0xffffff, 0x2a2733)
    if stats.record then centerText(y + 3, "  " .. stats.record .. "  ", 0xffc857, 0x2a2733) end
    centerText(y + 5, "  R — сыграть с начала · Q — выход  ", 0xb9b3cc, 0x2a2733)
  elseif state == "pause" then
    centerText(floor((H - HUD) / 2), "  Пауза · P — продолжить · Q — выход  ", 0xffffff, 0x2a2733)
  elseif state == "quit?" then
    centerText(floor((H - HUD) / 2), "  Выйти из игры? Q — да · любая клавиша — нет  ", 0xffffff, 0x8a2030)
  end
end

-- ======================= рекорд =======================
local RECORD = fs.isDir("/home") and "/home/.razlom" or "/.razlom"
local function saveRecord(sec)
  if not run.fromStart then return "Рекорд — только за игру с первого уровня" end
  local best
  if fs.exists(RECORD) then
    local f = fs.open(RECORD, "r")
    if f then best = tonumber(f.readAll()) f.close() end
  end
  if not best or sec < best then
    local f = fs.open(RECORD, "w")
    if f then f.write(tostring(sec)) f.close() end
    return best and string.format("Новый рекорд! Было %d:%02d", floor(best / 60), floor(best % 60)) or "Первый рекорд!"
  end
  return string.format("Рекорд: %d:%02d", floor(best / 60), floor(best % 60))
end

-- ======================= главный цикл =======================
local function setMouseLock(on)
  if term and term.setMouseLock and mouseLocked ~= on then pcall(term.setMouseLock, on) mouseLocked = on end
end

local function finish(text)
  setMouseLock(false)
  if snd then pcall(snd.stop) end
  pcall(function() cv:free() end)
  freeLevel()
  for _, id in pairs(M) do pcall(gpu.freeMesh, id) end
  for _, id in pairs(T) do pcall(gpu.freeTexture, id) end
  gpu.setBackground(oldBg)
  gpu.setForeground(oldFg)
  gpu.setResolution(oldW, oldH)
  if prevBuffer ~= 0 then gpu.setActiveBuffer(prevBuffer) end
  if embedded then return end
  gpu.setBackground(0x000000)
  gpu.setForeground(0xffffff)
  gpu.fill(1, 1, oldW, oldH, " ")
  if term and term.setCursorPos then pcall(term.setCursorPos, 1, 1) end
  if text and print then print(text) end
end

now = uptime()
newGame()
collectLamps()

-- замер: облёт без ожидания
if mode == "bench" then
  local n = tonumber(args[2]) or 90
  for i = 1, 5 do now = uptime() render(0, { pathAt(i * 0.1) }) end
  local tb = uptime()
  for i = 1, n do
    now = uptime()
    updateParts(1 / 30)
    render(0, { pathAt(i * 0.12) })
  end
  local dt = uptime() - tb
  local used = gpu.totalMemory and (gpu.totalMemory() - gpu.freeMemory()) or 0
  finish(string.format("RAZLOM %d×%d точек: %d кадров за %.2f с — %.1f кадров/с, %.1f мс на кадр; видеопамять %d клеток%s",
    CW, CH, n, dt, n / dt, dt / n * 1000, used, lowVram and " (простые текстуры)" or ""))
  return
end

-- самопроверка правил всех уровней (без экрана): game check → строки «CHECK ok/fail …» в out=файл или на экран
if mode == "check" then
  local lines, fails = {}, 0
  local function ok(what, cond)
    lines[#lines + 1] = (cond and "CHECK ok   " or "CHECK FAIL ") .. what
    if not cond then fails = fails + 1 end
  end
  local function tick(sec)
    for _ = 1, floor(sec * 30) do
      now = now + 1 / 30
      updateDoors(1 / 30) updateShots(1 / 30) updateParts(1 / 30)
    end
  end
  for n = 1, LEVELS do
    if n ~= levelNo then loadLevel(n) end
    snapshot = nil
    P = newPlayer() P.god = true
    saveSnapshot()
    startLevelState()
    local name = "ур." .. n .. ": "
    local used = gpu.totalMemory and (gpu.totalMemory() - gpu.freeMemory()) or 0
    local sc = Lv.cellAt(P.x, P.z)
    ok(name .. "игрок на старте стоит на полу и может ходить",
      sc ~= nil and abs(P.y - sc.f) < 0.01 and not Lv.blocked(P.x, P.y, P.z, P.r, P.h))
    ok(name .. "загрузился, врагов " .. stats.total .. ", видеопамять " .. used .. " клеток (кадр " .. CW .. "×" .. CH .. ")",
      stats.total > 0 and #levelMeshes > 0)
    -- двери под ключ-карту: без карты закрыта, с картой открывается и пропускает
    for _, d in pairs(Lv.doors) do
      if d.key then
        local c = d.cells[1]
        P.x, P.z, P.y = c[1] + 0.5, c[2] + 0.5, d.f
        P.keys = {}
        tick(0.5)
        ok(name .. "дверь «" .. d.key .. "» без карты закрыта", d.open == 0 and Lv.cell(c[1], c[2]) == nil)
        P.keys[d.key] = true
        tick(1.5)
        ok(name .. "дверь «" .. d.key .. "» с картой открылась", d.open >= 1 and Lv.cell(c[1], c[2]) ~= nil)
        local hasKey = false
        for _, it in ipairs(items) do if ITEMS[it.kind].key == d.key then hasKey = true end end
        ok(name .. "карта «" .. d.key .. "» лежит на уровне", hasKey)
      end
    end
    -- пульты: турели выключаются, излучатель вскрывает босса
    for _, c in ipairs(consoles) do
      local kind, arg = c.target:match("^(%a+):?(.*)$")
      if kind == "turrets" then
        useConsole(c)
        local all, off = 0, 0
        for _, e in ipairs(enemies) do if e.def.static and e.group == arg then all = all + 1 if e.off then off = off + 1 end end end
        ok(name .. "пульт выключил турели " .. off .. "/" .. all, all > 0 and off == all)
      end
    end
    local boss
    for _, e in ipairs(enemies) do if e.def.boss then boss = e end end
    if boss then
      local hp = boss.hp
      hurtEnemy(boss, 100)
      ok(name .. "без обруча босс почти не ранится", hp - boss.hp < 20)
      local ring
      for _, c in ipairs(consoles) do if c.target == "ring" then ring = c end end
      ok(name .. "на уровне есть излучатели обруча", ring ~= nil)
      if ring then useConsole(ring) end
      hp = boss.hp
      hurtEnemy(boss, 100)
      ok(name .. "вскрытый босс получает полный урон", hp - boss.hp >= 99)
      killEnemy(boss)
      ok(name .. "после босса открыт выход", portalOpen)
    elseif Lv.exit.need == "kills" then
      for _, e in ipairs(enemies) do if not e.dead then killEnemy(e) end end
      ok(name .. "после всех врагов открыт выход", portalOpen)
    else
      ok(name .. "выход открыт сразу", portalOpen)
    end
    -- выход ведёт дальше
    P.x, P.y, P.z = Lv.exit.x, Lv.exit.y, Lv.exit.z
    P.onGround = true
    state = "play"
    updatePlayer(1 / 30)
    ok(name .. "через выход — " .. (n < LEVELS and "на следующий уровень" or "победа"), state == (n < LEVELS and "next" or "won"))
  end
  local f = opt.out and fs.open(opt.out, "w")
  if f then
    f.write(table.concat(lines, "\n") .. "\n") f.close()
    local m = fs.open(opt.out .. ".ok", "w") m.write("ok") m.close()
  end
  finish()
  if not f then for _, l in ipairs(lines) do print(l) end end
  print(fails == 0 and "RAZLOM CHECK: всё в порядке" or ("RAZLOM CHECK: ошибок " .. fails))
  return fails == 0
end

-- кадр в файл: мир живёт t секунд (враги ходят, если встревожены — alert), потом снимок
if mode == "shot" then
  local live = tonumber(opt.t) or 0
  if opt.alert then for _, e in ipairs(enemies) do e.alert = true end end
  if opt.open then for _, d in pairs(Lv.doors) do d.open = 1 d.opening = true end end
  local steps = floor(live * 30)
  for _ = 1, steps do
    now = now + 1 / 30
    if now >= flowT then flowT = now + 0.6 Lv.flow(P.x, P.z) end
    for _, e in ipairs(enemies) do updateEnemy(e, 1 / 30) end
    updateShots(1 / 30) updateParts(1 / 30) updateDoors(1 / 30)
  end
  if opt.expose then for _, e in ipairs(enemies) do if e.def.boss then e.exposedT = now + 5 e.alert = true end end end
  if opt.beam then for _, c in ipairs(consoles) do c.beamT = now + 5 end end
  local cam
  if opt.cam then
    cam = {}
    for v in opt.cam:gmatch("[^,]+") do cam[#cam + 1] = tonumber(v) end
  end
  render(0, cam)
  local f = fs.open(opt.out or "/razlom_shot.txt", "w")
  f.write(CW .. " " .. CH .. "\n")
  for y = 0, CH - 1 do
    local row = {}
    for x = 0, CW - 1 do row[#row + 1] = string.format("%06x", cv:get(x, y) or 0) end
    f.write(table.concat(row) .. "\n")
  end
  f.close()
  local okf = fs.open((opt.out or "/razlom_shot.txt") .. ".ok", "w") okf.write("ok") okf.close()
  finish()
  return
end

local TARGET = 1 / 30
local last = uptime()
local timer = os.startTimer(0)
local quit = false
local endAt = tonumber(args[2])
local started = uptime()
local frames, fps, fpsT = 0, 0, uptime()
local foreign = {}
local demoT = 0

while not quit do
  local ev = { os.pullEventRaw() }
  local name = ev[1]
  if name == "terminate" then quit = true
  elseif name == "key" then
    local k = ev[2]
    if state == "quit?" then
      if k == "q" then quit = true else state = "play" end
    elseif k == "q" then
      if mode == "demo" or state == "won" or state == "dead" or state == "next" then quit = true else state = "quit?" end
    elseif k == "p" and (state == "play" or state == "pause") then
      state = state == "play" and "pause" or "play"
    elseif state == "next" and (k == "space" or k == "enter") then
      local ok, err = pcall(nextLevel)
      if not ok then finish("Разлом: не загрузился уровень: " .. tostring(err)) return false, tostring(err) end
      collectLamps()
      last = uptime()
    elseif k == "r" and state == "dead" then
      startLevelState()
    elseif k == "r" and state == "won" then
      run = { time = 0, kills = 0, taken = 0, fromStart = true }
      startLevel = 1
      snapshot = nil
      local ok, err = pcall(loadLevel, 1)
      if not ok then finish("Разлом: " .. tostring(err)) return false, tostring(err) end
      newGame()
      collectLamps()
    elseif k == "1" or k == "2" or k == "3" then
      local i = tonumber(k)
      if P.have[i] and P.weapon ~= i then P.weapon = i P.nextFire = max(P.nextFire, now + 0.25) say(WEAPONS[i].name, 1) end
    elseif k == "c" then
      P.pitch = 0
    end
    keys[k] = true
  elseif name == "key_up" then
    keys[ev[2]] = nil
  elseif name == "mouse_click" then
    if ev[2] == 1 then mouseFire = true else dragX, dragY = ev[3], ev[4] end
  elseif name == "mouse_drag" then
    if dragX and ev[2] ~= 1 and state == "play" then
      P.yaw = P.yaw + (ev[3] - dragX) * 0.05
      P.pitch = max(-1.35, min(1.35, P.pitch - (ev[4] - dragY) * 0.09))
      dragX, dragY = ev[3], ev[4]
    end
  elseif name == "mouse_up" then
    if ev[2] == 1 then mouseFire = false else dragX, dragY = nil, nil end
  elseif name == "mouse_move" then
    if state == "play" and mode == "play" then
      P.yaw = P.yaw + (tonumber(ev[2]) or 0) * MOUSE_RAD
      P.pitch = max(-1.35, min(1.35, P.pitch - (tonumber(ev[3]) or 0) * MOUSE_RAD))
    end
  elseif name == "timer" and ev[2] == timer then
    local t = uptime()
    local dt = min(0.1, t - last)
    last = t
    if state == "pause" or state == "quit?" or state == "next" then
      t0 = t0 + dt
      now = t
      render(fps)
    elseif mode == "demo" then
      now = t
      demoT = demoT + dt
      for _, e in ipairs(enemies) do e.anim = e.anim + dt * 2 end
      updateParts(dt)
      render(fps, { pathAt(demoT) })
    else
      now = t
      if state == "play" and now >= flowT then
        flowT = now + 0.6
        Lv.flow(P.x, P.z)
      end
      updatePlayer(dt)
      if state == "play" or state == "dead" then
        for _, e in ipairs(enemies) do updateEnemy(e, dt) end
      end
      updateDoors(dt)
      updateShots(dt)
      updateParts(dt)
      updateItems()
      if state == "won" and not stats.record then stats.record = saveRecord(run.time) saveProgress(LEVELS) end
      render(fps)
    end
    setMouseLock(mode == "play" and (state == "play" or state == "dead" or state == "won"))
    frames = frames + 1
    if t - fpsT >= 1 then fps = floor(frames / (t - fpsT) + 0.5) frames = 0 fpsT = t end
    if endAt and t - started >= endAt then quit = true end
    timer = os.startTimer(max(0, TARGET - (uptime() - t)))
  elseif name ~= "timer" and name ~= "char" and name ~= "mouse_scroll" and name ~= "screen_resized" then
    foreign[#foreign + 1] = ev
  elseif name == "timer" then
    foreign[#foreign + 1] = ev
  end
end

finish(mode == "play" and string.format("Разлом: уровень %d, врагов уничтожено %d", levelNo, run.kills + stats.kills) or nil)
for _, ev in ipairs(foreign) do pcall(os.queueEvent, table.unpack(ev)) end
return true
