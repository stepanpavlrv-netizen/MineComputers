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
local F = {}                    -- функции игры (у Cobalt не больше 200 локальных переменных на всю вложенность)

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
-- Кадр: картинкой (вдвое шире и вчетверо выше полублоков), если видеокарта и сервер позволяют, иначе полублоками.
-- Интерфейс рисуется прямо в кадр, поэтому кадр — на весь экран.
local IX, IY, imageMode = 1, 2, false
if gpu.imageScale and not opt.halfblock then
  local okS, sx, sy = pcall(gpu.imageScale)
  local probe = okS and sx and gpu.canvas(sx, sy)
  if probe then
    if probe:image(1, 1) then IX, IY, imageMode = sx, sy, true end
    probe:free()
    pcall(gpu.clearImage)
  end
end
-- качество текстур из настроек игры (авто — по видеопамяти)
local cfgQuality = "auto"
do
  local p = (fs.isDir("/home") and "/home/" or "/") .. ".razlom_cfg"
  if fs.exists(p) then
    local f = fs.open(p, "r")
    if f then cfgQuality = (f.readAll() or ""):match("quality=(%w+)") or "auto" f.close() end
  end
end

-- Кадр и качество текстур под видеопамять: модели и текстуры занимают около 31 тыс. клеток в полном качестве,
-- 10 тыс. — в простом и 5 тыс. — в самом простом, уровень — ещё до 3 тыс. Сначала ищем кадр покрупнее (не меньше
-- половины экрана) с полными текстурами, потом с простыми; на самых слабых картах — что влезет.
local W, H, cv, quality
do
  local TIERS = { { q = false, need = 34500, minScale = 0.5 }, { q = 2, need = 14000, minScale = 0.5 }, { q = 4, need = 8000, minScale = 0.2 } }
  if cfgQuality == "simple" then table.remove(TIERS, 1)
  elseif cfgQuality == "tiny" then TIERS = { TIERS[3] }
  elseif cfgQuality == "full" then TIERS[1].minScale = 0.2 end
  for _, tier in ipairs(TIERS) do
    local scale = 1
    while scale >= tier.minScale - 1e-6 do
      local w, h = max(40, floor(MW * scale)), max(14, floor(MH * scale))
      gpu.setResolution(w, h)
      local c = gpu.canvas(w * IX, h * IY)
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
function F.sfx(ch, freq, sec, vol, wave, to)
  if snd then pcall(snd.play, (ch - 1) % CHN + 1, freq, sec, vol, wave, to) end
end
function F.arpeggio(notes, len)
  if not snd then return end
  local c = (3 - 1) % CHN + 1
  for i, n in ipairs(notes) do
    snd.setWave(c, snd.modes.square) snd.setVolume(c, 0.5) snd.setFrequency(c, n)
    snd.open(c) snd.delay(i == #notes and len * 3 or len) snd.close(c) snd.delay(15)
  end
  pcall(snd.process)
end

function F.loading(text, frac, title)
  if gpu.clearImage then pcall(gpu.clearImage) end
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
  F.loading("Рисуем текстуры и модели...", 0.05)
  art = need("art", gpu, lowVram)
end)
local T, M = art and art.tex, art and art.mesh

-- ======================= уровень =======================
local Lv, levelNo
local levelMeshes, acidMesh, doorMeshes = {}, nil, {}

function F.freeLevel()
  for _, l in ipairs(levelMeshes) do pcall(gpu.freeMesh, l.id) end
  if acidMesh then pcall(gpu.freeMesh, acidMesh) end
  for _, id in pairs(doorMeshes) do pcall(gpu.freeMesh, id) end
  levelMeshes, acidMesh, doorMeshes = {}, nil, {}
end

function F.loadLevel(n)
  F.freeLevel()
  local fn, err = loadfile(DIR .. "/levels/" .. n .. ".lua")
  if not fn then error("Не найден уровень " .. n .. ": " .. tostring(err), 0) end
  local data = fn()
  local title = "Уровень " .. n .. " · " .. data.name
  F.loading("Строим станцию и раскладываем свет...", 0.3, title)
  Lv = need("level", data)
  levelMeshes, acidMesh, doorMeshes = Lv.build(art, function(f) F.loading("Строим станцию и раскладываем свет...", 0.3 + f * 0.65, title) end)
  levelNo = n
end

local startLevel = max(1, min(LEVELS, tonumber(opt.lvl) or 1))
if okInit then okInit, initErr = pcall(F.loadLevel, startLevel) end
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
local cfg, BIND, DIFF           -- настройки игрока, клавиши, сложность (заполняются ниже)
local quit = false
local feedList = {}             -- лента событий над здоровьем: «+12 патронов»

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

function F.say(text, sec)
  message = text
  messageT = now + (sec or 2.5)
end
function F.feed(text)
  feedList[#feedList + 1] = { text = text, t = now }
  if #feedList > 4 then table.remove(feedList, 1) end
end

-- ======================= движение тел (игрок и враги) =======================
function F.moveBody(b, dx, dz, dt)
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
function F.particle(tex, x, y, z, vx, vy, vz, life, size, grav, glow)
  if #parts >= MAXP then table.remove(parts, 1) end
  parts[#parts + 1] = { tex = tex, x = x, y = y, z = z, vx = vx, vy = vy, vz = vz, life = life, max = life,
                        size = size, grav = grav or 0, glow = glow }
end
function F.burst(tex, x, y, z, n, speed, life, size, grav, glow)
  for _ = 1, n do
    local a, b = rnd() * pi * 2, (rnd() - 0.3) * pi
    local s = speed * (0.4 + rnd() * 0.6)
    F.particle(tex, x, y, z, cos(a) * cos(b) * s, sin(b) * s + speed * 0.2, sin(a) * cos(b) * s, life * (0.6 + rnd() * 0.6), size, grav, glow)
  end
end
function F.flashLight(x, y, z, radius, color, power, life)
  flashes[#flashes + 1] = { x = x, y = y, z = z, r = radius, c = color, p = power, life = life, max = life }
end
function F.dist3(ax, ay, az, bx, by, bz)
  local dx, dy, dz = ax - bx, ay - by, az - bz
  return sqrt(dx * dx + dy * dy + dz * dz)
end

--- Коробка тела врага по высоте.
function F.bodyY(e)
  local d = e.def
  if d.fly then return e.y - 0.45, e.y + 0.45 end
  if d.boss then return e.y + 0.9, e.y + 3.3 end
  return e.y, e.y + d.h
end

-- ======================= урон =======================
function F.hurtPlayer(dmg, fromX, fromZ)
  if P.dead or state ~= "play" or P.god or dmg <= 0 then return end
  dmg = max(1, floor(dmg * (DIFF and DIFF.dmg or 1) + 0.5))
  local save = min(P.armor, floor(dmg * 2 / 3 + 0.5))
  P.armor = P.armor - save
  P.hp = P.hp - (dmg - save)
  P.hurtT = now
  stats.taken = stats.taken + dmg
  if fromX then P.hurtDir = atan2(fromZ - P.z, fromX - P.x) end
  F.sfx(4, 190, 0.18, 0.9, "sawtooth", 90)
  if P.hp <= 0 then
    P.hp, P.dead, P.deadT = 0, true, now
    state = "dead"
    F.say("Вы погибли. R — заново с начала уровня, Q — выход", 1e9)
    F.sfx(4, 300, 1.2, 1, "sawtooth", 40)
  end
end

function F.aliveCount()
  local n = 0
  for _, e in ipairs(enemies) do if not e.dead and not e.def.machine then n = n + 1 end end
  return n
end

function F.openExit(text)
  if portalOpen then return end
  portalOpen = true
  F.say(text, 5)
  F.arpeggio({ 523, 659, 784, 1047 }, 110)
end

function F.killEnemy(e)
  e.dead, e.deadT = true, now
  if not e.def.machine then stats.kills = stats.kills + 1 end
  local y1, y2 = F.bodyY(e)
  local cx, cy, cz = e.x, (y1 + y2) / 2, e.z
  if e.def.machine then
    F.burst(T.spark, cx, cy, cz, 14, 7, 0.5, 0.1, 8, true)
    F.burst(T.smoke, cx, cy, cz, 4, 1.2, 1.2, 0.5, -1)
  else
    F.burst(T.oil, cx, cy, cz, 10, 4, 0.9, 0.16, 10)
    F.burst(T.spark, cx, cy, cz, 8, 6, 0.4, 0.1, 6, true)
  end
  if e.def.fly or e.def.boss then
    F.burst(T.fire, cx, cy, cz, e.def.boss and 20 or 6, e.def.boss and 6 or 3, 0.7, e.def.boss and 1.0 or 0.5, -1, true)
    F.burst(T.glowGreen, cx, cy, cz, e.def.boss and 24 or 0, 7, 1.2, 0.3, 2, true)
    F.flashLight(cx, cy, cz, e.def.boss and 14 or 5, 0xff9040, e.def.boss and 2 or 1.2, e.def.boss and 1.5 or 0.4)
  end
  F.sfx(2, e.def.fly and 900 or 480, e.def.boss and 1.6 or 0.5, 0.9, "sawtooth", 40)
  if e.def.machine then return end
  local need = Lv.exit and Lv.exit.need
  local left = F.aliveCount()
  if e.def.boss and need == "boss" then
    for _, o in ipairs(enemies) do if not o.dead and not o.def.machine then F.killEnemy(o) end end
    F.openExit("Сердце разлома уничтожено! Портал домой открыт")
  elseif need == "kills" and left == 0 then
    F.openExit("Все враги уничтожены! Портал открыт " .. (Lv.exit.where or ""))
  elseif need == "kills" and left <= 3 then
    F.say("Осталось врагов: " .. left)
  end
end

function F.hurtEnemy(e, dmg, kx, kz)
  if e.dead then return end
  if e.def.boss and now >= (e.exposedT or 0) then
    dmg = dmg * 0.15                                 -- плоть гасит урон, пока обруч не вскрыл кристалл
    if now >= (e.hintT or 0) then
      e.hintT = now + 12
      F.say("Плоть гасит выстрелы! Включите излучатели обруча на пультах (E)", 4)
    end
  end
  e.hp = e.hp - dmg
  e.flashT = now + 0.1
  e.alert = true
  if kx and not e.def.fly and not e.def.static and not e.def.boss then
    e.push = (e.push or 0) + dmg * 0.02; e.pushX, e.pushZ = kx, kz
  end
  if e.hp <= 0 then F.killEnemy(e)
  elseif now - (e.painT or 0) > 0.3 then
    e.painT = now
    F.sfx(2, e.def.machine and 900 or (240 + rnd(0, 60)), 0.12, 0.6, "square", 120)
  end
end

--- Взрыв: урон по кругу, отбрасывание (в том числе себя — так прыгают на взрыве).
function F.explode(x, y, z, radius, dmg, owner)
  F.burst(T.fire, x, y, z, 12, 5, 0.55, 0.55, -2, true)
  F.burst(T.spark, x, y, z, 10, 9, 0.45, 0.1, 8, true)
  F.burst(T.smoke, x, y, z, 5, 1.5, 1.2, 0.7, -1)
  F.flashLight(x, y, z, radius * 2.2, 0xff9a40, 1.6, 0.45)
  F.sfx(2, 380, 0.6, 1, "noise", 30)
  for _, e in ipairs(enemies) do
    if not e.dead then
      local y1, y2 = F.bodyY(e)
      local d = F.dist3(x, y, z, e.x, max(y1, min(y2, y)), e.z) - (e.def.boss and 1 or 0)
      d = max(0, d)
      if d < radius then
        F.hurtEnemy(e, dmg * (1 - d / radius * 0.7), (e.x - x) / max(0.1, d), (e.z - z) / max(0.1, d))
        if owner == "player" then P.hitT = now end
      end
    end
  end
  if owner == "player" and not P.dead then
    local cy = P.y + 0.9
    local d = F.dist3(x, y, z, P.x, cy, P.z)
    if d < radius then
      local k = 1 - d / radius
      local ux, uy, uz = (P.x - x) / max(0.2, d), (cy - y) / max(0.2, d), (P.z - z) / max(0.2, d)
      P.vx, P.vy, P.vz = P.vx + ux * 11 * k, P.vy + max(uy, 0.35) * 13 * k, P.vz + uz * 11 * k
      P.onGround = false
      P.air = true
      F.hurtPlayer(floor(dmg * 0.35 * k))
    end
  end
end

-- ======================= стрельба игрока =======================
function F.eyePos()
  return P.x, P.y + (P.dead and 0.35 or (P.eye or 1.55)), P.z
end
function F.forward(yaw, pitch)
  local cp = cos(pitch)
  return cp * cos(yaw), sin(pitch), cp * sin(yaw)
end

--- Луч против врагов (коробка вокруг тела): ближайший враг и расстояние. skip — кого не считать.
function F.rayEnemy(ox, oy, oz, dx, dy, dz, maxd, skip)
  local best, bestT = nil, maxd
  for _, e in ipairs(enemies) do
    if not e.dead and e ~= skip then
      local r = e.def.r + 0.08
      local y1, y2 = F.bodyY(e)
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

function F.fire()
  local w = WEAPONS[P.weapon]
  if now < P.nextFire or P.dead then return end
  if w.ammo and P.ammo[w.ammo] <= 0 then
    P.nextFire = now + 0.4
    F.sfx(1, 1400, 0.03, 0.4, "square")
    for i = #WEAPONS, 1, -1 do
      local o = WEAPONS[i]
      if P.have[i] and (not o.ammo or P.ammo[o.ammo] > 0) then P.weapon = i F.say(o.name) break end
    end
    return
  end
  if w.ammo then P.ammo[w.ammo] = P.ammo[w.ammo] - 1 end
  P.nextFire = now + w.rate
  P.kick = w.kick
  P.fireT = now
  local ex, ey, ez = F.eyePos()
  local fx, fy, fz = F.forward(P.yaw, P.pitch)
  local rx, rz = -sin(P.yaw), cos(P.yaw)
  local ux, uy, uz = -rz * fy, rz * fx - rx * fz, rx * fy
  local tipX = ex + fx * 0.5 + rx * 0.14 - ux * 0.12
  local tipY = ey + fy * 0.5 - uy * 0.12
  local tipZ = ez + fz * 0.5 + rz * 0.14 - uz * 0.12
  F.flashLight(tipX, tipY, tipZ, w.proj and 4 or 5, w.proj and 0x60d0ff or 0xffc070, 1.0, 0.07)
  for _, e in ipairs(enemies) do
    if not e.dead and not e.alert then
      local d = F.dist3(e.x, e.y, e.z, P.x, P.y, P.z)
      if d < 9 or (d < 20 and Lv.sees(e.x, e.y + 1, e.z, ex, ey, ez)) then e.alert = true end
    end
  end
  if w.proj then
    shots[#shots + 1] = { x = tipX, y = tipY, z = tipZ, vx = fx * 22, vy = fy * 22, vz = fz * 22, owner = "player",
                          tex = T.plasma, size = 0.42, dmg = w.dmg, splash = w.splash, life = 3, light = 0x40c8ff }
    F.sfx(1, 260, 0.35, 0.9, "sawtooth", 1100)
    return
  end
  if P.weapon == 2 then F.sfx(1, 700, 0.32, 1, "noise", 70) else F.sfx(1, 1700, 0.12, 0.7, "square", 380) end
  for _ = 1, w.pellets do
    local a, s = rnd() * pi * 2, w.spread * sqrt(rnd()) * (1 - 0.65 * (P.ads or 0))
    local ox, oy = cos(a) * s, sin(a) * s
    local dx, dy, dz = fx + rx * ox + ux * oy, fy + uy * oy, fz + rz * ox + uz * oy
    local l = sqrt(dx * dx + dy * dy + dz * dz)
    dx, dy, dz = dx / l, dy / l, dz / l
    local wall = Lv.ray(ex, ey, ez, dx, dy, dz, 60)
    local e, t = F.rayEnemy(ex, ey, ez, dx, dy, dz, wall)
    local hx, hy, hz = ex + dx * (e and t or wall), ey + dy * (e and t or wall), ez + dz * (e and t or wall)
    if e then
      F.hurtEnemy(e, w.dmg, dx, dz)
      P.hitT = now
      F.particle(e.def.machine and T.spark or T.oil, hx, hy, hz, dx * -2 + rnd() - 0.5, 1.5, dz * -2 + rnd() - 0.5, 0.5, 0.14, 9)
    else
      F.particle(T.spark, hx - dx * 0.05, hy - dy * 0.05, hz - dz * 0.05, -dx * 2, 1.5, -dz * 2, 0.25, 0.1, 6, true)
      if rnd() < 0.5 then F.particle(T.smoke, hx - dx * 0.1, hy - dy * 0.1, hz - dz * 0.1, 0, 0.4, 0, 0.6, 0.3, -0.3) end
    end
  end
end

-- ======================= враги =======================
local flowT = -1
local spawnEnemy

--- Выстрел врага в точку (tx, ty, tz); spread — разлёт по сторонам (рад).
function F.enemyShoot(e, tx, ty, tz, tvx, tvz, spread)
  local d = e.def
  local sx, sy, sz
  if d.muzzle then
    local m = d.muzzle
    local c, s = cos(e.yaw), sin(e.yaw)
    sx, sy, sz = e.x + m[1] * c - m[3] * s, e.y + m[2], e.z + m[1] * s + m[3] * c
  else
    local fx, fy, fz = F.forward(e.yaw, e.pitch or 0)
    sx, sy, sz = e.x + fx * 0.5, e.y + fy * 0.5, e.z + fz * 0.5
  end
  local dd = F.dist3(sx, sy, sz, tx, ty, tz)
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
  local vol = max(0.1, 0.7 - F.dist3(sx, sy, sz, P.x, P.y, P.z) / 40)
  if fireShot then F.sfx(2, 420, 0.25, vol, "triangle", 160) else F.sfx(2, d.machine and 2200 or 1500, 0.08, vol, "square", 700) end
end

--- Турель: стреляет во всех подряд — в игрока и в тварей, кто ближе и на виду.
function F.updateTurret(e, dt)
  local d = e.def
  if e.off then e.anim = e.anim + dt * 0.2 return end
  if now >= e.lookT then
    e.lookT = now + 0.25
    local best, bd = nil, d.sight
    local ey = e.y + d.eyeY
    if not P.dead then
      local pd = F.dist3(e.x, ey, e.z, P.x, P.y + 1.2, P.z)
      if pd < bd and Lv.sees(e.x, ey, e.z, P.x, P.y + 1.3, P.z) then best, bd = "player", pd end
    end
    for _, o in ipairs(enemies) do
      if o ~= e and not o.dead and not o.def.machine then
        local y1, y2 = F.bodyY(o)
        local od = F.dist3(e.x, ey, e.z, o.x, (y1 + y2) / 2, o.z)
        if od < bd and Lv.sees(e.x, ey, e.z, o.x, (y1 + y2) / 2, o.z) then best, bd = o, od end
      end
    end
    e.target = best
  end
  local t = e.target
  if t == nil or (t ~= "player" and t.dead) or (t == "player" and P.dead) then e.anim = e.anim + dt return end
  local tx, ty, tz, tvx, tvz
  if t == "player" then tx, ty, tz, tvx, tvz = P.x, P.y + 1.0, P.z, P.vx, P.vz
  else local y1, y2 = F.bodyY(t) tx, ty, tz = t.x, (y1 + y2) / 2, t.z end
  local want = atan2(tz - e.z, tx - e.x)
  local diff = (want - e.yaw + pi) % (2 * pi) - pi
  e.yaw = e.yaw + max(-dt * 3, min(dt * 3, diff))
  if abs(diff) < 0.25 and now >= e.atkT then
    e.atkT = now + d.rate
    e.swingT = now
    F.enemyShoot(e, tx, ty, tz, tvx, tvz)
  end
end

--- Босс: висит над серединой пещеры, залпы веером, удар щупальцами вблизи, зовёт тварей на 60% и 30%.
function F.updateBoss(e, dt)
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
    F.say(phase == 2 and "Сердце зовёт ползунов из разлома!" or "Сердце в ярости: летят очи!", 3)
    F.sfx(2, 90, 1.2, 1, "sawtooth", 50)
  end
  local exposed = now < (e.exposedT or 0)
  -- залп веером
  if now >= e.atkT and not exposed then
    e.atkT = now + d.rate * (phase == 3 and 0.65 or 1)
    e.swingT = now
    local n = phase == 1 and 5 or 7
    for i = 1, n do
      F.enemyShoot(e, P.x, P.y + 1.0, P.z, 0, 0, (i - (n + 1) / 2) * 0.13)
    end
  end
  -- удар щупальцами: волна по полу вокруг
  if dxz < 4.2 and abs(P.y - e.y) < 2 and now >= (e.slamT or 0) then
    e.slamT = now + 2.4
    F.burst(T.oil, e.x, e.y + 0.2, e.z, 16, 6, 0.6, 0.2, 8)
    F.flashLight(e.x, e.y + 0.5, e.z, 6, 0x80ff90, 1.2, 0.3)
    F.sfx(2, 70, 0.5, 1, "noise", 40)
    F.hurtPlayer(18, e.x, e.z)
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
          F.burst(T.glowGreen, sx, e.y + 0.5, sz, 8, 3, 0.6, 0.25, -1, true)
        end
      end
    end
  end
end

function F.updateEnemy(e, dt)
  local d = e.def
  if e.dead then return end
  if d.static then return F.updateTurret(e, dt) end
  if d.boss then return F.updateBoss(e, dt) end
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
    F.moveBody(e, e.pushX * e.push * dt * 8, e.pushZ * e.push * dt * 8, 0)
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
      F.enemyShoot(e, P.x, P.y + 1.0, P.z, P.vx, P.vz)
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
      local bumped = F.moveBody(e, goX / l * sp * dt, goZ / l * sp * dt, dt)
      if bumped and e.onGround and e.sees and d.melee and P.y > e.y + STEP then e.vy = 6.5 e.onGround = false end
      moving = true
    else
      F.moveBody(e, 0, 0, dt)
    end
  else
    F.moveBody(e, 0, 0, dt)
  end
  if moving then e.anim = e.anim + dt * d.speed * 2.2 end
  if d.melee then
    if dxz < d.r + 0.35 + 0.75 and abs(P.y - e.y) < 1.3 and now >= e.atkT then
      e.atkT = now + d.meleeRate
      e.swingT = now
      F.hurtPlayer(d.melee, e.x, e.z)
      F.sfx(2, d.mesh == "crawler" and 600 or 160, 0.15, 0.8, "sawtooth", 60)
    end
  elseif e.sees and now >= e.atkT then
    e.atkT = now + d.rate * (0.8 + rnd() * 0.4)
    e.swingT = now
    F.enemyShoot(e, P.x, P.y + 1.0, P.z, P.vx, P.vz)
  end
  local c = Lv.cellAt(e.x, e.z)
  if c and c.acid and e.y <= c.f + 0.05 then
    if now >= (e.acidT or 0) then e.acidT = now + 0.5 F.hurtEnemy(e, 6) end
  end
end

-- ======================= снаряды и частицы =======================
function F.updateShots(dt)
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
            local y1, y2 = F.bodyY(e)
            local r = e.def.r + 0.2
            if abs(nx - e.x) < r and abs(nz - e.z) < r and ny > y1 - 0.2 and ny < y2 + 0.2 then hit = true victim = e break end
          end
        end
      end
      if not hit and s.owner ~= "player" and not P.dead then
        if abs(nx - P.x) < 0.5 and abs(nz - P.z) < 0.5 and ny > P.y - 0.1 and ny < P.y + P.h + 0.05 then hit = true end
      end
    end
    if hit then
      if s.splash and s.owner == "player" then
        F.explode(s.x, s.y, s.z, s.splash, s.dmg, "player")
      elseif s.owner ~= "player" then
        if victim then F.hurtEnemy(victim, s.dmg)
        elseif not P.dead and F.dist3(s.x, s.y, s.z, P.x, P.y + 0.9, P.z) < (s.splash or 0.9) + 0.4 then
          F.hurtPlayer(s.dmg, s.x, s.z)
        end
        F.burst(s.tex, s.x, s.y, s.z, 5, 3, 0.3, 0.2, 2, true)
        F.flashLight(s.x, s.y, s.z, 3, s.light, 1, 0.2)
      end
      table.remove(shots, i)
    else
      if s.owner == "player" and rnd() < 0.6 then F.particle(T.plasma, s.x, s.y, s.z, 0, 0, 0, 0.18, 0.18, 0, true) end
      i = i + 1
    end
  end
end

function F.updateParts(dt)
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
function F.pickup(it)
  local k = it.kind
  local def = ITEMS[k]
  if def.key then
    P.keys[def.key] = true
    return true
  elseif k == "medkit" then
    if P.hp >= 100 then return false end
    P.hp = min(100, P.hp + DIFF.heal)
  elseif k == "armor" then
    if P.armor >= 100 then return false end
    P.armor = 100
  elseif k == "shells" then
    if P.ammo.shells >= 60 then return false end
    P.ammo.shells = min(60, P.ammo.shells + floor(12 * DIFF.ammo))
  elseif k == "cells" then
    if P.ammo.cells >= 50 then return false end
    P.ammo.cells = min(50, P.ammo.cells + floor(10 * DIFF.ammo))
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

function F.updateItems()
  for _, it in ipairs(items) do
    if not it.taken and not P.dead then
      if abs(it.x - P.x) < 0.75 and abs(it.z - P.z) < 0.75 and P.y > it.y - 1 and P.y < it.y + 1.2 then
        if F.pickup(it) then
          it.taken = true
          P.pickT = now
          F.feed(ITEMS[it.kind].name)
          if it.kind == "shotgun" or it.kind == "plasmagun" or ITEMS[it.kind].key then F.arpeggio({ 392, 523, 659 }, 70)
          else F.sfx(3, 620, 0.15, 0.6, "square", 1250) end
        end
      end
    end
  end
end

function F.openDoor(d)
  if d.opening then return end
  d.opening = true
  F.sfx(3, 160, 0.9, 0.7, "sawtooth", 260)
end

function F.updateDoors(dt)
  for _, d in pairs(Lv.doors) do
    if d.opening and d.open < 1 then d.open = min(1, d.open + dt * 1.1) end
    if not d.opening and not P.dead then
      for _, c in ipairs(d.cells) do
        local dx, dz = c[1] + 0.5 - P.x, c[2] + 0.5 - P.z
        if dx * dx + dz * dz < 1.8 * 1.8 and abs(P.y - d.f) < 1.5 then
          if d.key and P.keys[d.key] then F.openDoor(d)
          end
          break
        end
      end
    end
  end
end

--- Пульт: target = "door:знак" — открыть дверь, "turrets:группа" — выключить турели, "ring" — излучатель обруча.
function F.useConsole(c)
  local kind, arg = c.target:match("^(%a+):?(.*)$")
  if kind == "ring" then
    if now < c.readyT then F.say(string.format("Излучатель заряжается: %d с", math.ceil(c.readyT - now))) return end
    local boss
    for _, e in ipairs(enemies) do if e.def.boss and not e.dead then boss = e end end
    if not boss then F.say("Излучатель молчит") return end
    c.readyT = now + 16
    c.beamT = now + 7
    boss.exposedT = now + 7
    F.say("Обруч вскрыл кристалл! Стреляйте в Сердце!", 3)
    F.sfx(3, 300, 1.2, 0.9, "sine", 1600)
    return
  end
  if c.used then F.say("Уже сделано") return end
  c.used = true
  if kind == "door" then
    local d = Lv.doors[arg]
    if d then F.openDoor(d) end
    F.say(c.label or "Дверь открыта")
  elseif kind == "turrets" then
    local n = 0
    for _, e in ipairs(enemies) do
      if e.def.static and e.group == arg and not e.dead then e.off = true n = n + 1 end
    end
    F.say(c.label or ("Турели отключены: " .. n))
  end
  F.sfx(3, 880, 0.2, 0.6, "square", 440)
end

function F.nearConsole()
  for _, c in ipairs(consoles) do
    local dx, dz = c.x - P.x, c.z - P.z
    if dx * dx + dz * dz < 1.7 * 1.7 and abs(P.y - c.y) < 1.4 then return c end
  end
end

-- ======================= лампы, свет у тел =======================
local lampList = {}
function F.collectLamps()
  lampList = {}
  for _, li in ipairs(Lv.lights) do
    if li.lamp then
      local c = Lv.cellAt(li[1], li[3])
      if c then lampList[#lampList + 1] = { li[1], c.c, li[3] } end
    end
  end
end
F.collectLamps()
function F.lightNear(x, y, z)
  return Lv.lightAt and Lv.lightAt(x, y, z, 0, 1, 0) or 1
end

-- ======================= настройки, клавиши, сохранение =======================
local HOME = fs.isDir("/home") and "/home/" or "/"
local CFG_FILE, SAVE_FILE, REC_FILE, PROGRESS = HOME .. ".razlom_cfg", HOME .. ".razlom_save", HOME .. ".razlom_rec", HOME .. ".razlom_lvl"

local ACTIONS = {   -- что можно переназначить: имя, подпись, клавиша по умолчанию
  { "forward", "Вперёд", "w" }, { "back", "Назад", "s" }, { "left", "Влево", "a" }, { "right", "Вправо", "d" },
  { "jump", "Прыжок", "space" }, { "sprint", "Бег", "shift" }, { "crouch", "Присесть", "ctrl" },
  { "use", "Пульт, дверь", "e" }, { "map", "Карта", "tab" }, { "lastWeapon", "Прошлое оружие", "q" },
}
function F.readKV(path)
  local t = {}
  if not fs.exists(path) then return t end
  local f = fs.open(path, "r")
  if not f then return t end
  for line in (f.readAll() or ""):gmatch("[^\n]+") do
    local k, v = line:match("^([%w_]+)=(.*)$")
    if k then t[k] = v end
  end
  f.close()
  return t
end
function F.writeKV(path, t)
  local f = fs.open(path, "w")
  if not f then return end
  local ks = {}
  for k in pairs(t) do ks[#ks + 1] = k end
  table.sort(ks)
  for _, k in ipairs(ks) do f.write(k .. "=" .. tostring(t[k]) .. "\n") end
  f.close()
end

cfg = { sens = 1.0, invert = false, fov = 95, volume = 100, quality = "auto", fps = false }
BIND = {}
for _, a in ipairs(ACTIONS) do BIND[a[1]] = a[3] end
do
  local t = F.readKV(CFG_FILE)
  cfg.sens = tonumber(t.sens) or cfg.sens
  cfg.invert = t.invert == "true"
  cfg.fov = tonumber(t.fov) or cfg.fov
  cfg.volume = tonumber(t.volume) or cfg.volume
  cfg.quality = t.quality or cfg.quality
  cfg.fps = t.fps == "true"
  for _, a in ipairs(ACTIONS) do if t["key_" .. a[1]] then BIND[a[1]] = t["key_" .. a[1]] end end
end
function F.saveCfg()
  if mode ~= "play" then return end
  local t = { sens = string.format("%.1f", cfg.sens), invert = cfg.invert, fov = cfg.fov, volume = cfg.volume, quality = cfg.quality, fps = cfg.fps }
  for _, a in ipairs(ACTIONS) do t["key_" .. a[1]] = BIND[a[1]] end
  F.writeKV(CFG_FILE, t)
end

-- сложность
local DIFFS = {
  easy = { name = "Лёгкая", dmg = 0.6, hp = 0.8, heal = 35, ammo = 1.4, info = "враги слабее, аптечки лечат больше" },
  normal = { name = "Обычная", dmg = 1, hp = 1, heal = 25, ammo = 1, info = "как задумано" },
  hard = { name = "Тяжёлая", dmg = 1.4, hp = 1.25, heal = 20, ammo = 0.75, info = "враги злее, патронов меньше" },
}
local diffKey = "normal"
DIFF = DIFFS.normal

function F.reached()
  return max(1, min(LEVELS, tonumber(F.readKV(PROGRESS).lvl) or 1))
end
function F.saveProgress(n)
  if mode ~= "play" then return end
  if n > F.reached() then F.writeKV(PROGRESS, { lvl = n }) end
end
function F.loadSave()
  local t = F.readKV(SAVE_FILE)
  if not tonumber(t.lvl) then return nil end
  return t
end

-- ======================= игрок =======================
local keys = {}
local mouseFire, adsHeld = false, false
local mouseLocked, escCaptured = nil, nil
local MOUSE_RAD = pi / 180
function F.key(action) return keys[BIND[action]] end

local consoleHintT = 0
function F.updatePlayer(dt)
  if P.dead then
    P.vx, P.vz = P.vx * 0.9, P.vz * 0.9
    F.moveBody(P, P.vx * dt, P.vz * dt, dt)
    return
  end
  -- поворот и взгляд стрелками
  local turn = (keys.right and 1 or 0) - (keys.left and 1 or 0)
  P.yaw = P.yaw + turn * 2.5 * dt
  local look = (keys.up and 1 or 0) - (keys.down and 1 or 0)
  P.pitch = max(-1.35, min(1.35, P.pitch + look * 1.7 * dt))
  -- присесть: ниже, медленнее; встать — если над головой есть место
  local wantCrouch = F.key("crouch")
  if wantCrouch then P.crouch = true
  elseif P.crouch and not Lv.blocked(P.x, P.y, P.z, P.r, 1.75) then P.crouch = false end
  P.h = P.crouch and 1.15 or 1.75
  local eyeTo = P.crouch and 0.95 or 1.55
  P.eye = P.eye + (eyeTo - P.eye) * min(1, dt * 12)
  -- прицеливание (правая кнопка)
  P.ads = P.ads + ((adsHeld and 1 or 0) - P.ads) * min(1, dt * 10)
  -- желаемое направление
  local fwd = (F.key("forward") and 1 or 0) - (F.key("back") and 1 or 0)
  local side = (F.key("right") and 1 or 0) - (F.key("left") and 1 or 0)
  local c, s = cos(P.yaw), sin(P.yaw)
  local wx, wz = c * fwd - s * side, s * fwd + c * side
  local wl = sqrt(wx * wx + wz * wz)
  if wl > 0 then wx, wz = wx / wl, wz / wl end
  local SPEED = 6.2
  P.sprint = F.key("sprint") and fwd > 0 and not P.crouch and P.ads < 0.3
  if P.sprint then SPEED = 8.8 end
  if P.crouch then SPEED = SPEED * 0.5 end
  SPEED = SPEED * (1 - 0.35 * P.ads)
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
  if F.key("jump") and P.onGround and now >= P.jumpT and not P.crouch then
    P.vy = 6.4
    P.onGround = false
    P.jumpT = now + 0.3
    F.sfx(4, 210, 0.12, 0.4, "triangle", 440)
  end
  local wasGround = P.onGround
  local bumped = F.moveBody(P, P.vx * dt, P.vz * dt, dt)
  if bumped and not P.onGround then P.vx, P.vz = P.vx * 0.7, P.vz * 0.7 end
  if P.onGround and not wasGround and (P.landSpeed or 0) > 7 then
    F.sfx(4, 120, 0.1, 0.6, "noise", 60)
    if P.landSpeed > 15 then F.hurtPlayer(floor((P.landSpeed - 15) * 3)) end
  end
  local hs = sqrt(P.vx * P.vx + P.vz * P.vz)
  if P.onGround and hs > 0.5 then
    local before = floor(P.bob / pi)
    P.bob = P.bob + dt * hs * 1.7
    if floor(P.bob / pi) ~= before then F.sfx(4, 140 + rnd(0, 30), 0.04, P.crouch and 0.15 or 0.35, "noise", 60) end
  end
  local cell = Lv.cellAt(P.x, P.z)
  if cell and P.onGround and P.y <= cell.f + 0.05 then
    if cell.pad then
      P.vy = cell.pad[1]
      P.vx, P.vz = cell.pad[2], cell.pad[3]
      P.onGround = false
      P.air = true
      F.sfx(3, 140, 0.45, 0.8, "sine", 900)
    elseif cell.acid and now >= P.acidT then
      P.acidT = now + 0.5
      P.acidHurtT = now
      F.hurtPlayer(7)
    end
  end
  -- пульт рядом: E (или F) — включить
  local con = F.nearConsole()
  local use = F.key("use") or keys.f
  if use and con and not P.eHeld then F.useConsole(con) end
  P.eHeld = use
  if mouseFire then F.fire() end
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
      F.say("", 0)
      F.arpeggio({ 523, 659, 784, 1047, 1319 }, 120)
    end
  end
end

function F.switchWeapon(i)
  if i and P.have[i] and P.weapon ~= i then
    P.lastWeapon = P.weapon
    P.weapon = i
    P.nextFire = max(P.nextFire, now + 0.25)
    P.switchT = now
  end
end
function F.cycleWeapon(dir)
  local i = P.weapon
  for _ = 1, #WEAPONS do
    i = (i - 1 + dir) % #WEAPONS + 1
    if P.have[i] then F.switchWeapon(i) return end
  end
end

-- ======================= новый уровень =======================
function F.newPlayer()
  return { x = 0, y = 0, z = 0, vx = 0, vy = 0, vz = 0, r = 0.32, h = 1.75, eye = 1.55, ads = 0, yaw = 0, pitch = 0,
           hp = 100, armor = 0, onGround = true, weapon = 1, lastWeapon = 1, have = { true, false, false }, ammo = { shells = 0, cells = 0 },
           nextFire = 0, kick = 0, bob = 0, jumpT = 0, acidT = 0, hurtT = -10, pickT = -10, fireT = -10, acidHurtT = -10,
           hitT = -10, switchT = -10, keys = {} }
end

function spawnEnemy(kind, x, y, z, th)
  local d = DEF[kind]
  local hp = floor(d.hp * (d.boss and 1 or DIFF.hp) + 0.5)
  local e = { kind = kind, def = d, x = x, y = y, z = z, vy = 0, r = d.r, h = d.h, onGround = true,
              hp = hp, maxHp = hp, yaw = (th and th[5]) or rnd() * pi * 2, anim = rnd() * 6, lookT = rnd() * 0.5,
              atkT = now + 1 + rnd(), orbit = rnd() * pi * 2, light = 1, lightT = -1, alert = false,
              homeX = x, homeZ = z, group = th and th.group }
  enemies[#enemies + 1] = e
  return e
end

local objective, objectiveT = "", -10

--- Начать уровень: игрок переносит здоровье, броню, оружие и патроны (ключи — нет).
function F.startLevelState()
  local st = Lv.start
  if snapshot then
    P.hp, P.armor, P.weapon = snapshot.hp, snapshot.armor, snapshot.weapon
    P.ammo = { shells = snapshot.ammo.shells, cells = snapshot.ammo.cells }
    P.have = { snapshot.have[1], snapshot.have[2], snapshot.have[3] }
  end
  P.x, P.z, P.yaw, P.pitch = st.x, st.z, st.yaw or 0, 0
  P.vx, P.vy, P.vz, P.dead, P.kick = 0, 0, 0, false, 0
  P.crouch, P.h, P.eye, P.ads = false, 1.75, 1.55, 0
  local sc = Lv.cellAt(P.x, P.z)
  P.y = Lv.ground(P.x, P.z, P.r, st.y or (sc and sc.f) or 0)     -- старт может быть высоко (клеть лифта на 6 м)
  P.onGround = true
  P.keys = {}
  P.hurtT, P.pickT, P.fireT, P.acidHurtT, P.hitT, P.switchT = -10, -10, -10, -10, -10, -10
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
  feedList = {}
  flowT = -1
  objective, objectiveT = "", -10
  F.say(Lv.data.intro or Lv.data.name, 5)
end

function F.saveSnapshot()
  snapshot = {}
  for _, k in ipairs({ "hp", "armor", "weapon" }) do snapshot[k] = P[k] end
  snapshot.have = { P.have[1], P.have[2], P.have[3] }
  snapshot.ammo = { shells = P.ammo.shells, cells = P.ammo.cells }
  snapshot.hp = max(snapshot.hp, 50)                   -- после трудного уровня — хотя бы половина здоровья
end

--- Сохранить начало уровня («Продолжить» в главном меню).
function F.saveGame()
  if mode ~= "play" or not snapshot then return end
  F.writeKV(SAVE_FILE, { lvl = levelNo, diff = diffKey, hp = snapshot.hp, armor = snapshot.armor, weapon = snapshot.weapon,
    have = (snapshot.have[1] and "1" or "0") .. (snapshot.have[2] and "1" or "0") .. (snapshot.have[3] and "1" or "0"),
    shells = snapshot.ammo.shells, cells = snapshot.ammo.cells, time = floor(run.time), kills = run.kills, taken = run.taken,
    full = run.fromStart and 1 or 0 })
end

--- Оружие, которое к этому уровню уже было бы (если начать с середины).
function F.loadoutFor(lvl)
  P = F.newPlayer()
  if lvl >= 2 then P.have[2] = true P.ammo.shells = 24 P.weapon = 2 end
  if lvl >= 3 then P.have[3] = true P.ammo.cells = 20 P.armor = 50 end
end

function F.applyDevOptions()
  for k in pairs(opt) do
    if k == "god" then P.god = true end
    if k == "give" then P.have = { true, true, true } P.ammo = { shells = 60, cells = 50 } P.weapon = 3 end
    if k == "portal" then P.devPortal = true end
  end
end

--- Новая игра (или выбранный уровень) с данной сложностью.
function F.newGame(lvl, dk)
  lvl = lvl or 1
  diffKey = dk or diffKey
  DIFF = DIFFS[diffKey] or DIFFS.normal
  run = { time = 0, kills = 0, taken = 0, fromStart = lvl == 1 }
  F.loadoutFor(lvl)
  F.applyDevOptions()
  F.saveSnapshot()
  if levelNo ~= lvl then F.loadLevel(lvl) F.collectLamps() end
  F.startLevelState()
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
  F.saveGame()
end

--- Продолжить с сохранения.
function F.continueGame()
  local sv = F.loadSave()
  if not sv then return F.newGame(1) end
  diffKey = DIFFS[sv.diff] and sv.diff or "normal"
  DIFF = DIFFS[diffKey]
  P = F.newPlayer()
  local hv = sv.have or "100"
  snapshot = { hp = tonumber(sv.hp) or 100, armor = tonumber(sv.armor) or 0, weapon = tonumber(sv.weapon) or 1,
               have = { hv:sub(1, 1) == "1", hv:sub(2, 2) == "1", hv:sub(3, 3) == "1" },
               ammo = { shells = tonumber(sv.shells) or 0, cells = tonumber(sv.cells) or 0 } }
  run = { time = tonumber(sv.time) or 0, kills = tonumber(sv.kills) or 0, taken = tonumber(sv.taken) or 0, fromStart = sv.full == "1" }
  local lvl = max(1, min(LEVELS, tonumber(sv.lvl) or 1))
  if levelNo ~= lvl then F.loadLevel(lvl) F.collectLamps() end
  F.startLevelState()
end

function F.nextLevel()
  F.saveSnapshot()
  local ok, err = pcall(F.loadLevel, levelNo + 1)
  if not ok then error(err, 0) end
  F.collectLamps()
  F.saveProgress(levelNo)
  F.startLevelState()
  F.saveGame()
end

-- ======================= отрисовка мира =======================
local playerLight, playerLightT = 1, -1

function F.drawEnemy(e)
  local d = e.def
  local bright = e.light
  if now >= e.lightT then
    e.lightT = now + 0.4 + rnd() * 0.2
    e.light = max(0.35, min(1.4, F.lightNear(e.x, e.y + 1, e.z) * 1.15))
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
  if d.static then
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
  if d.mesh then
    local y, pitch = e.y, 0
    if e.dead then
      if now - e.deadT > 5 then return end
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

function F.pathAt(t)
  local PATH = Lv.data.tour
  if not PATH then
    local st = Lv.start
    return st.x, 1.7, st.z, (st.yaw or 0) + sin(t * 0.15) * 1.2, -0.05
  end
  local n = #PATH - 1
  local seg = (t / 3.2) % n
  local i = floor(seg)
  local f = seg - i
  local a, b = PATH[i + 1], PATH[i + 2]
  f = f * f * (3 - 2 * f)
  local function lerpAng(x, y) local d = (y - x + pi) % (2 * pi) - pi return x + d * f end
  return a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f, lerpAng(a[4], b[4]), a[5] + (b[5] - a[5]) * f
end

function F.beam(x1, y1, z1, x2, y2, z2)
  local d = F.dist3(x1, y1, z1, x2, y2, z2)
  local n = max(2, floor(d / 0.4))
  local wob = sin(now * 30) * 0.03
  for i = 0, n do
    local k = i / n
    cv:billboard(T.plasma, x1 + (x2 - x1) * k, y1 + (y2 - y1) * k + wob, z1 + (z2 - z1) * k, 0.3, 0.3, 0.9, 0, 1)
  end
end

--- Мир в кадр (без интерфейса). cam — свободная камера (облёт, меню) или nil — глазами игрока.
function F.renderWorld(cam)
  cv:clear(FOG)
  local ex, ey, ez, yaw, pitch
  if cam then
    ex, ey, ez, yaw, pitch = cam[1], cam[2], cam[3], cam[4], cam[5]
  else
    ex, ey, ez = F.eyePos()
    if P.onGround and not P.dead then ey = ey + sin(P.bob * 2) * 0.035 end
    yaw, pitch = P.yaw, P.pitch + P.kick * 0.25
  end
  local fov = cfg.fov * (1 - 0.3 * (cam and 0 or P.ads))
  cv:view(ex, ey, ez, yaw, pitch, fov)
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
  -- куски уровня — от ближних к дальним: закрытые точки дальних не закрашиваются
  for i = 1, #levelMeshes do
    local l = levelMeshes[i]
    local dx, dz = (l.cx or 0) - ex, (l.cz or 0) - ez
    l.d = dx * dx + dz * dz
  end
  table.sort(levelMeshes, function(a, b) return a.d < b.d end)
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
    local ready = (c.target == "ring" and now >= c.readyT) or (c.target ~= "ring" and not c.used)
    cv:mesh(M.console, c.x, c.y, c.z, c.yaw, 0, 1, ready and 1.25 or 0.7, ready and 0x002810 or 0x200000)
    if c.ex and now < c.beamT then
      for _, e in ipairs(enemies) do
        if e.def.boss and not e.dead then F.beam(c.ex, c.ey, c.ez, e.x, e.y + 2.1, e.z) end
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
  for _, e in ipairs(enemies) do F.drawEnemy(e) end
  if portalOpen and exit then
    local k = 0.85 + sin(now * 4) * 0.15
    cv:billboard(T.portal, exit.x, exit.y + 1.1, exit.z, 1.3 * k, 2.1 * k, 1.2, 0, 0)
    cv:billboard(T.glowPurple, exit.x, exit.y + 1.1, exit.z, 2.6, 3.0, 0.8, 0, 1)
    if rnd() < 0.5 then F.particle(T.glowPurple, exit.x + (rnd() - 0.5) * 1.2, exit.y + 0.2, exit.z + (rnd() - 0.5) * 1.2, 0, 1.5, 0, 0.8, 0.15, 0, true) end
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
    local hx = w.hold[1] * (1 - P.ads)                 -- прицеливание: оружие к середине
    if now - P.fireT < 0.06 then
      local fx, fy, fz = F.forward(P.yaw, P.pitch)
      local rx, rz = -sin(P.yaw), cos(P.yaw)
      local d = w.hold[3] + w.tip * w.scale + 0.05
      cv:billboard(w.proj and T.plasma or T.flash, ex + fx * d + rx * hx, ey + fy * d + w.hold[2] * 0.8, ez + fz * d + rz * hx,
        0.18, 0.18, 1, 0, 1)
    end
    cv:clearDepth()
    if now >= playerLightT then
      playerLightT = now + 0.25
      playerLight = max(0.45, min(1.5, F.lightNear(P.x, P.y + 1.4, P.z) * 1.2))
    end
    local bx = sin(P.bob) * 0.012 * (P.sprint and 2 or 1)
    local by = -abs(cos(P.bob)) * 0.01 * (P.sprint and 2 or 1)
    local h = w.hold
    local glow = (now - P.fireT < 0.08) and 0.5 or 0
    cv:held(M[w.mesh], hx + bx, h[2] + by - P.kick * 0.04 + 0.02 * P.ads, h[3] - P.kick * 0.06, -0.05 * (1 - P.ads), P.kick * 0.8, w.scale, playerLight + glow)
  end
  if not cam then
    if P.dead then cv:tint(0x600000, min(0.55, (now - P.deadT) * 0.6))
    elseif now - P.hurtT < 0.35 then cv:tint(0xff2010, 0.3 * (1 - (now - P.hurtT) / 0.35))
    elseif now - P.pickT < 0.25 then cv:tint(0xffd040, 0.2 * (1 - (now - P.pickT) / 0.25)) end
    if now - P.acidHurtT < 0.5 then cv:tint(0x30ff40, 0.15) end
    if not P.dead and P.hp <= 25 then cv:tint(0xff1010, 0.1 + 0.07 * sin(now * 6)) end
  end
end

-- ======================= интерфейс в кадре =======================
-- (интерфейс и меню — в своей области видимости: у Lua не больше 200 локальных переменных на файл)
local render, show, menuKey, menuClick, openMenu, currentObjective, menu = (function()
-- Всё рисуется пиксельным шрифтом прямо в кадр. UI — масштаб: на 400 точек высоты буква 14 точек.
local UI = max(1, floor(CH / 140))
local GREEN, BLUE, YELLOW, ORANGE, RED, WHITE, GREY, DARK = 0x8cff9e, 0x8fc4ff, 0xe8c048, 0xff9a3d, 0xff6b5f, 0xf2f4f6, 0x9aa1ad, 0x000000
local function txt(x, y, s, col, sc, outline)
  if outline == false then return cv:text(floor(x), floor(y), s, col, sc or UI) end
  return cv:text(floor(x), floor(y), s, col, sc or UI, outline or DARK)
end
local function tw(s, sc) return cv:textWidth(s, sc or UI) end
local function ctext(y, s, col, sc, outline) txt((CW - tw(s, sc)) / 2, y, s, col, sc, outline) end
local function box(x, y, w, h, col) if w > 0 and h > 0 then cv:fill(floor(x), floor(y), floor(w), floor(h), col) end end

local KEYACC = { red = "красную", yellow = "жёлтую", blue = "синюю" }

--- Что делать на уровне сейчас (для всплывающей задачи и карты).
local function currentObjective()
  for _, e in ipairs(enemies) do
    if e.def.boss and not e.dead then return "Включайте излучатели (E) и бейте в кристалл" end
  end
  local need = Lv.exit and Lv.exit.need
  if need == "kills" and not portalOpen then return "Уничтожьте всех: осталось " .. F.aliveCount() end
  for _, d in pairs(Lv.doors) do
    if d.key and not d.opening then
      if P.keys[d.key] then return "Откройте " .. KEYACC[d.key] .. " дверь" end
      return "Найдите " .. KEYACC[d.key] .. " ключ-карту"
    end
  end
  if portalOpen then return "Идите к порталу" end
  return "Найдите выход"
end

local function nearLockedDoor()
  for _, d in pairs(Lv.doors) do
    if not d.opening then
      for _, c in ipairs(d.cells) do
        local dx, dz = c[1] + 0.5 - P.x, c[2] + 0.5 - P.z
        if dx * dx + dz * dz < 2.2 * 2.2 and abs(P.y - d.f) < 1.5 and not (d.key and P.keys[d.key]) then return d end
      end
    end
  end
end

local function drawCrosshair()
  local cx, cy = floor(CW / 2), floor(CH / 2)
  local g, l = 2 * UI, 2 * UI
  local col = 0xe8ffe0
  box(cx - g - l, cy, l, UI, col) box(cx + g + 1, cy, l, UI, col)
  box(cx, cy - g - l, UI, l, col) box(cx, cy + g + 1, UI, l, col)
  if now - P.hitT < 0.15 then                          -- попадание: крестик вокруг прицела
    for i = 0, 3 do
      local o = g + l + i * UI
      box(cx - o, cy - o, UI, UI, WHITE) box(cx + o, cy - o, UI, UI, WHITE)
      box(cx - o, cy + o, UI, UI, WHITE) box(cx + o, cy + o, UI, UI, WHITE)
    end
  end
  if P.hurtDir and now - P.hurtT < 0.7 then             -- откуда стреляли: дужка у прицела
    local rel = P.hurtDir - P.yaw
    local R = 18 * UI
    for i = -6, 6 do
      local a = rel + i * 0.06
      box(cx + sin(a) * R, cy - cos(a) * R, UI * 2, UI * 2, 0xff3c28)
    end
  end
end

local function drawHud(fps)
  local m = 5 * UI
  -- здоровье и броня
  local big = UI * 3
  local low = P.hp <= 25
  local hpCol = low and RED or GREEN
  local hpText = "♥ " .. P.hp
  local hy = CH - m - 7 * big - 2 * UI
  if not (low and floor(now * 3) % 2 == 1) then txt(m, hy, hpText, hpCol, big) end
  local aw = tw(hpText, big) + 3 * UI
  txt(m + aw, hy + 7 * big - 7 * UI * 2, "◈ " .. P.armor, P.armor > 0 and BLUE or GREY, UI * 2)
  local barW = aw + tw("◈ " .. P.armor, UI * 2)
  box(m, CH - m - UI, barW, UI, 0x101418)
  box(m, CH - m - UI, barW * max(0, P.hp) / 100, UI, hpCol)
  -- ключ-карты над здоровьем
  local kx = m
  for _, k in ipairs({ "red", "yellow", "blue" }) do
    if P.keys[k] then
      box(kx - UI, hy - 4 * UI - UI, 4 * UI + 2 * UI, 3 * UI + 2 * UI, DARK)
      box(kx, hy - 4 * UI, 4 * UI, 3 * UI, KEYCOL[k])
      kx = kx + 6 * UI
    end
  end
  -- лента событий над здоровьем
  local fy = hy - 10 * UI
  for i = #feedList, 1, -1 do
    local f = feedList[i]
    if now - f.t < 2.5 then
      txt(m, fy, f.text, WHITE, UI)
      fy = fy - 9 * UI
    else
      table.remove(feedList, i)
    end
  end
  -- патроны
  local w = WEAPONS[P.weapon]
  local ammo = w.ammo and P.ammo[w.ammo]
  local maxA = w.ammo == "shells" and 60 or 50
  local lowA = ammo and ammo <= floor(maxA * 0.2)
  local ab = UI * 4
  local ay = CH - m - 7 * ab
  local rx = CW - m
  if ammo then
    local tail = "/" .. maxA
    local tws = tw(tail, UI * 2)
    txt(rx - tws, ay + 7 * ab - 7 * UI * 2, tail, GREY, UI * 2)
    local num = tostring(ammo)
    txt(rx - tws - UI - tw(num, ab), ay, num, lowA and ORANGE or WHITE, ab)
  else
    txt(rx - tw("∞", UI * 3), ay + 7 * ab - 7 * UI * 3, "∞", WHITE, UI * 3)
  end
  local label = lowA and "мало патронов" or w.name
  txt(rx - tw(label), ay - 10 * UI, label, lowA and ORANGE or YELLOW)
  if now - P.switchT < 1.5 then                         -- слоты — только при смене оружия
    local sx = rx
    for i = #WEAPONS, 1, -1 do
      if P.have[i] then
        local s = tostring(i)
        local bw = tw(s) + 4 * UI
        sx = sx - bw
        box(sx, ay - 22 * UI, bw, 10 * UI, i == P.weapon and YELLOW or 0x3a3f48)
        box(sx + UI, ay - 21 * UI, bw - 2 * UI, 8 * UI, DARK)
        txt(sx + 2 * UI, ay - 20 * UI, s, i == P.weapon and YELLOW or GREY, UI, false)
        sx = sx - 2 * UI
      end
    end
  end
  drawCrosshair()
  -- подсказка под прицелом: пульт или запертая дверь
  local con = F.nearConsole()
  local py = floor(CH / 2) + 26 * UI
  if con then
    local s = con.hint or "Пульт"
    if con.target == "ring" and now < con.readyT then s = string.format("Излучатель заряжается: %d с", math.ceil(con.readyT - now)) end
    local kw = tw("E") + 4 * UI
    local total = kw + 3 * UI + tw(s)
    local x0 = (CW - total) / 2
    box(x0, py - 2 * UI, kw, 11 * UI, 0x6b5414)
    box(x0, py - 2 * UI, kw, 10 * UI, YELLOW)
    txt(x0 + 2 * UI, py, "E", 0x14110a, UI, false)
    txt(x0 + kw + 3 * UI, py, s, WHITE)
  else
    local d = nearLockedDoor()
    if d then ctext(py, d.key and ("Нужна " .. KEYNAME[d.key] .. " ключ-карта") or "Дверь открывается с пульта", d.key and KEYCOL[d.key] or WHITE) end
  end
  -- задача: всплывает слева сверху при смене
  local obj = currentObjective()
  if obj ~= objective then objective, objectiveT = obj, now end
  local bossUp = false
  for _, e in ipairs(enemies) do if e.def.boss and not e.dead and e.alert then bossUp = true end end
  if now - objectiveT < 5 then
    local ty = m + (bossUp and 28 * UI or 0)
    txt(m, ty, "задача", YELLOW)
    txt(m, ty + 10 * UI, objective, WHITE)
  end
  -- полоса босса
  for _, e in ipairs(enemies) do
    if e.def.boss and not e.dead and e.alert then
      local exposed = now < (e.exposedT or 0)
      local bw = floor(CW * 0.5)
      local bx = floor((CW - bw) / 2)
      ctext(m, "сердце разлома", WHITE)
      box(bx - UI, m + 9 * UI, bw + 2 * UI, 4 * UI, DARK)
      box(bx, m + 10 * UI, floor(bw * max(0, e.hp / e.maxHp)), 2 * UI, exposed and GREEN or 0xd03050)
      if exposed then ctext(m + 15 * UI, "кристалл вскрыт · " .. math.ceil(e.exposedT - now), GREEN) end
    end
  end
  -- сообщение посередине сверху (вступление уровня, события)
  if message and now < messageT and message ~= "" then
    local y = floor(CH * (bossUp and 0.32 or 0.18))
    local mw = tw(message)
    if mw > CW - 2 * m then
      -- длинное — в две строки по словам
      local a, b = message, ""
      local cut = #message
      while tw(a) > CW - 2 * m and cut > 1 do
        cut = message:sub(1, cut - 1):match("^.*() ") or 1
        a, b = message:sub(1, cut - 1), message:sub(cut + 1)
      end
      ctext(y, a, WHITE)
      ctext(y + 10 * UI, b, WHITE)
    else
      ctext(y, message, WHITE)
    end
  end
  if cfg.fps then txt(CW - m - tw(fps .. " к/с"), m, fps .. " к/с", GREY) end
end

--- Карта уровня (Tab): стены, двери, ключи, выход и игрок. Враги не показываются.
local function drawMap()
  cv:tint(0x06080a, 0.85)
  local m = 5 * UI
  local top = m + 22 * UI
  local s = max(1, floor(min((CW - 2 * m) / Lv.W, (CH - top - m - 20 * UI) / Lv.H)))
  local ox = floor((CW - Lv.W * s) / 2)
  local oy = top
  for cz = 0, Lv.H - 1 do
    for cx = 0, Lv.W - 1 do
      local e = Lv.cell(cx, cz)
      local d = Lv.doorAt(cx + 0.5, cz + 0.5)
      if e or d then
        local col
        if d and not (e and d.open >= 1) then col = d.key and KEYCOL[d.key] or 0x8a919c
        elseif e.acid then col = 0x2e8c3c
        else
          local k = max(0, min(1, (e.f + 1) / 6))
          col = floor(30 + 50 * k) * 65536 + floor(48 + 60 * k) * 256 + floor(44 + 40 * k)
        end
        box(ox + cx * s, oy + cz * s, s, s, col)
        -- светлая кромка у стен
        if not Lv.cell(cx - 1, cz) and not Lv.doorAt(cx - 0.5, cz + 0.5) then box(ox + cx * s, oy + cz * s, max(1, floor(s / 5)), s, GREEN) end
        if not Lv.cell(cx + 1, cz) and not Lv.doorAt(cx + 1.5, cz + 0.5) then box(ox + (cx + 1) * s - max(1, floor(s / 5)), oy + cz * s, max(1, floor(s / 5)), s, GREEN) end
        if not Lv.cell(cx, cz - 1) and not Lv.doorAt(cx + 0.5, cz - 0.5) then box(ox + cx * s, oy + cz * s, s, max(1, floor(s / 5)), GREEN) end
        if not Lv.cell(cx, cz + 1) and not Lv.doorAt(cx + 0.5, cz + 1.5) then box(ox + cx * s, oy + (cz + 1) * s - max(1, floor(s / 5)), s, max(1, floor(s / 5)), GREEN) end
      end
    end
  end
  for _, it in ipairs(items) do
    local k = ITEMS[it.kind].key
    if k and not it.taken then
      box(ox + it.x * s - s, oy + it.z * s - s * 0.7, 2 * s, 1.4 * s, WHITE)
      box(ox + it.x * s - s + 1, oy + it.z * s - s * 0.7 + 1, 2 * s - 2, 1.4 * s - 2, KEYCOL[k])
    end
  end
  local ex = Lv.exit
  if ex then
    local col = portalOpen and 0xc078ff or 0x5a4470
    box(ox + ex.x * s - s, oy + ex.z * s - s, 2 * s, 2 * s, col)
    box(ox + ex.x * s - s / 2, oy + ex.z * s - s / 2, s, s, DARK)
  end
  -- игрок: стрелка по направлению взгляда
  local px, pz = ox + P.x * s, oy + P.z * s
  for i = 0, 4 do
    local d = i * s * 0.45
    local r = max(1, floor((5 - i) * s * 0.25))
    box(px + cos(P.yaw) * d - r / 2, pz + sin(P.yaw) * d - r / 2, r, r, YELLOW)
  end
  txt(m, m, Lv.data.name, GREEN, UI * 2)
  local tsec = now - t0
  txt(m, m + 15 * UI, string.format("ур. %d · %d:%02d · враги %d из %d", levelNo, floor(tsec / 60), floor(tsec % 60),
    stats.total - stats.kills, stats.total), GREY)
  local obj = currentObjective()
  txt(CW - m - tw("задача"), CH - m - 19 * UI, "задача", YELLOW)
  txt(CW - m - tw(obj), CH - m - 9 * UI, obj, WHITE)
end

-- ======================= меню =======================
local menu = { stack = {}, sel = 1, rows = {}, wait = nil, from = nil }
local menuBuild = {}

local function push(name) menu.stack[#menu.stack + 1] = name menu.sel = 1 end
local function pop()
  table.remove(menu.stack)
  menu.sel = 1
  if #menu.stack == 0 then
    if menu.from == "pause" then state = "play" end
  end
end
local function openMenu(name, from)
  menu.stack = { name }
  menu.sel = 1
  menu.from = from
  state = "menu"
end

local function onOff(v) return v and "вкл" or "выкл" end
local QUAL = { "auto", "full", "simple", "tiny" }
local QUALNAME = { auto = "авто", full = "полное", simple = "простое", tiny = "самое простое" }
local KEYNAMES = { space = "пробел", shift = "Shift", ctrl = "Ctrl", tab = "Tab", alt = "Alt", enter = "Enter", up = "↑", down = "↓", left = "←", right = "→" }
local function keyLabel(k) return KEYNAMES[k] or tostring(k):upper() end

function menuBuild.main()
  local sv = F.loadSave()
  local items = {}
  if sv then
    local n = tonumber(sv.lvl) or 1
    items[#items + 1] = { "Продолжить", "ур. " .. n, function() F.continueGame() end }
  end
  items[#items + 1] = { "Новая игра", "выбор сложности", function() push("difficulty") menu.sel = 2 end }
  items[#items + 1] = { "Уровни", "открыто " .. F.reached() .. " из " .. LEVELS, function() push("levels") end }
  items[#items + 1] = { "Настройки", nil, function() push("settings") end }
  items[#items + 1] = { "Управление", nil, function() push("controls") end }
  items[#items + 1] = { "Рекорды", nil, function() push("records") end }
  items[#items + 1] = { "Выход", nil, function() quit = true end }
  return { title = nil, items = items, logo = true }
end
function menuBuild.difficulty()
  local items = {}
  for _, k in ipairs({ "easy", "normal", "hard" }) do
    local d = DIFFS[k]
    items[#items + 1] = { d.name, d.info, function() F.newGame(1, k) end }
  end
  return { title = "Новая игра", items = items, sel = 2 }
end
function menuBuild.levels()
  local items = {}
  local names = { "Станция над разломом", "Лаборатории", "Реакторный блок", "Шахта", "Сердце разлома" }
  local r = F.reached()
  for i = 1, LEVELS do
    if i <= r then items[#items + 1] = { i .. ". " .. names[i], nil, function() F.newGame(i, diffKey) end }
    else items[#items + 1] = { i .. ". ???", "закрыт", nil, off = true } end
  end
  return { title = "Уровни · сложность: " .. DIFF.name, items = items }
end
function menuBuild.settings()
  return { title = "Настройки", items = {
    { "Чувствительность мыши", string.format("%.1f", cfg.sens), nil, change = function(d) cfg.sens = max(0.2, min(3, cfg.sens + d * 0.1)) end, bar = (cfg.sens - 0.2) / 2.8 },
    { "Инверсия мыши", onOff(cfg.invert), function() cfg.invert = not cfg.invert end, change = function() cfg.invert = not cfg.invert end },
    { "Угол обзора", cfg.fov .. "°", nil, change = function(d) cfg.fov = max(70, min(110, cfg.fov + d * 5)) end },
    { "Громкость", cfg.volume .. "%", nil, change = function(d) cfg.volume = max(0, min(100, cfg.volume + d * 10)) end, bar = cfg.volume / 100 },
    { "Качество текстур", QUALNAME[cfg.quality] or "авто", nil, change = function(d)
        local i = 1
        for j, q in ipairs(QUAL) do if q == cfg.quality then i = j end end
        cfg.quality = QUAL[(i - 1 + d) % #QUAL + 1]
      end, note = "после перезапуска" },
    { "Кадры в секунду", onOff(cfg.fps), function() cfg.fps = not cfg.fps end, change = function() cfg.fps = not cfg.fps end },
    { "Клавиши", "›", function() push("keys") end },
  } }
end
function menuBuild.keys()
  local items = {}
  for _, a in ipairs(ACTIONS) do
    items[#items + 1] = { a[2], menu.wait == a[1] and "нажмите клавишу…" or keyLabel(BIND[a[1]]), function() menu.wait = a[1] end }
  end
  items[#items + 1] = { "Сбросить", nil, function() for _, a in ipairs(ACTIONS) do BIND[a[1]] = a[3] end F.saveCfg() end }
  return { title = "Клавиши", items = items }
end
function menuBuild.controls()
  local items = {
    { "Огонь", "ЛКМ" }, { "Прицелиться", "ПКМ" },
    { "Ходьба", keyLabel(BIND.forward) .. keyLabel(BIND.left) .. keyLabel(BIND.back) .. keyLabel(BIND.right) .. ", обзор — мышь" },
    { "Прыжок", keyLabel(BIND.jump) }, { "Бег", keyLabel(BIND.sprint) }, { "Присесть", keyLabel(BIND.crouch) },
    { "Пульт, дверь", keyLabel(BIND.use) .. " или F" }, { "Оружие", "1 2 3, колесо мыши, " .. keyLabel(BIND.lastWeapon) .. " — прошлое" },
    { "Карта", keyLabel(BIND.map) }, { "Пауза, меню", "Esc" },
  }
  for _, it in ipairs(items) do it.info = true end
  return { title = "Управление", items = items }
end
function menuBuild.records()
  local rec = F.readKV(REC_FILE)
  local items = {}
  for _, k in ipairs({ "easy", "normal", "hard" }) do
    local v = tonumber(rec[k])
    items[#items + 1] = { DIFFS[k].name, v and string.format("%d:%02d", floor(v / 60), floor(v % 60)) or "нет", info = true }
  end
  items[#items + 1] = { "Пройдено уровней", F.reached() .. " из " .. LEVELS, info = true }
  return { title = "Рекорды · все 5 уровней", items = items }
end
function menuBuild.pause()
  return { title = "Пауза", items = {
    { "Продолжить", "Esc", function() menu.stack = {} state = "play" end },
    { "Настройки", nil, function() push("settings") end },
    { "Начать уровень заново", nil, function() F.startLevelState() end },
    { "Выйти в главное меню", nil, function() F.saveGame() openMenu("main") end },
  }, footer = "Задача: " .. currentObjective() }
end

local function currentMenu()
  local name = menu.stack[#menu.stack]
  if not name then return nil end
  local mm = menuBuild[name]()
  mm.name = name
  return mm
end

--- Нарисовать меню поверх кадра.
local function drawMenu()
  local mm = currentMenu()
  if not mm then return end
  cv:tint(0x08090c, mm.logo and 0.55 or 0.72)
  local m = 8 * UI
  local x = m
  local y = m
  if mm.logo then
    txt(x, y, "РАЗЛОМ", 0xff8a3d, UI * 4, 0x3a1608)
    y = y + 7 * UI * 4 + 4 * UI
    txt(x + UI, y, "станция «глубина-7»", GREY)
    y = y + 18 * UI
  else
    txt(x, y, mm.title, 0xff8a3d, UI * 2)
    y = y + 7 * UI * 2 + 10 * UI
  end
  menu.rows = {}
  local n = #mm.items
  if menu.sel > n then menu.sel = n end
  if menu.sel < 1 then menu.sel = 1 end
  local rowH = 12 * UI
  local labelW = 0
  for _, it in ipairs(mm.items) do labelW = max(labelW, tw(it[1])) end
  for i, it in ipairs(mm.items) do
    local sel = i == menu.sel and not it.info
    local col = it.off and 0x5f6672 or (sel and 0x0d1410 or WHITE)
    local lw = tw(it[1])
    local rowW = it.info and (labelW + 8 * UI + tw(it[2] or "")) or (lw + (it[2] and (10 * UI + tw(it[2])) or 0))
    if mm.name == "settings" or mm.name == "keys" then rowW = labelW + 8 * UI + 60 * UI end
    if sel then box(x - 3 * UI, y - 2 * UI, rowW + 6 * UI, rowH - UI, GREEN) end
    txt(x, y, it[1], col, UI, not sel and DARK or false)
    if it[2] then
      local vx = (it.info or mm.name == "settings" or mm.name == "keys") and (x + labelW + 8 * UI) or (x + lw + 10 * UI)
      if it.bar then
        box(vx, y + 2 * UI, 40 * UI, 3 * UI, sel and 0x1f4a2a or 0x2a2f38)
        box(vx, y + 2 * UI, floor(40 * UI * max(0, min(1, it.bar))), 3 * UI, sel and 0x0d1410 or GREEN)
        txt(vx + 43 * UI, y, it[2], sel and 0x1f4a2a or GREEN, UI, not sel and DARK or false)
      else
        txt(vx, y, it[2], sel and 0x2e6b3c or (it.info and GREEN or 0x6f7682), UI, not sel and DARK or false)
      end
      if it.note and sel then txt(vx + 43 * UI + tw(it[2]) + 4 * UI, y, it.note, 0x1f4a2a, UI, false) end
    end
    menu.rows[i] = { y = y - 2 * UI, h = rowH, item = it }
    y = y + rowH
  end
  if mm.footer then txt(x, CH - m - 9 * UI, mm.footer, GREY) end
  -- карточка «продолжить» в главном меню
  if mm.logo then
    local sv = F.loadSave()
    if sv and menu.sel == 1 then
      local names = { "Станция над разломом", "Лаборатории", "Реакторный блок", "Шахта", "Сердце разлома" }
      local hv = sv.have or "100"
      local guns = {}
      if hv:sub(2, 2) == "1" then guns[#guns + 1] = "дробовик" end
      if hv:sub(3, 3) == "1" then guns[#guns + 1] = "плазмомёт" end
      local tsec = tonumber(sv.time) or 0
      local lines = {
        { names[tonumber(sv.lvl) or 1] or "", YELLOW },
        { "♥ " .. (sv.hp or "?") .. "  ◈ " .. (sv.armor or 0), WHITE },
        { #guns > 0 and table.concat(guns, ", ") or "бластер", WHITE },
        { "сложность: " .. (DIFFS[sv.diff] or DIFFS.normal).name, GREY },
        { string.format("время %d:%02d", floor(tsec / 60), floor(tsec % 60)), GREY },
      }
      local cw = 0
      for _, l in ipairs(lines) do cw = max(cw, tw(l[1])) end
      cw = cw + 8 * UI
      local chh = #lines * 10 * UI + 6 * UI
      local cx, cy = CW - cw - m, CH - m - chh - 12 * UI
      box(cx, cy, cw, chh, 0x101216)
      box(cx, cy, cw, UI, 0x2a2f38)
      for i, l in ipairs(lines) do txt(cx + 4 * UI, cy + 4 * UI + (i - 1) * 10 * UI, l[1], l[2], UI, false) end
    end
    txt(m, CH - m - 9 * UI, "↑↓ выбор · enter выбрать · esc назад", 0x7d8490)
  else
    txt(m, CH - m - 9 * UI - (mm.footer and 12 * UI or 0), "↑↓ выбор · ←→ изменить · enter · esc назад", 0x7d8490)
  end
end

local function menuActivate(i)
  local mm = currentMenu()
  local it = mm and mm.items[i]
  if not it or it.off or it.info then return end
  F.sfx(3, 900, 0.05, 0.4, "square")
  if it[3] then it[3]() elseif it.change then it.change(1) end
  if mm.name == "settings" then F.saveCfg() end
end

local function menuKey(k)
  local mm = currentMenu()
  if not mm then return end
  if menu.wait then                                     -- ждём клавишу для действия
    if k ~= "escape" then BIND[menu.wait] = k F.saveCfg() end
    menu.wait = nil
    return
  end
  local n = #mm.items
  local function step(d)
    for _ = 1, n do
      menu.sel = (menu.sel - 1 + d) % n + 1
      local it = mm.items[menu.sel]
      if not it.off and not it.info then break end
    end
    F.sfx(3, 600, 0.03, 0.3, "square")
  end
  if k == "up" or k == "w" then step(-1)
  elseif k == "down" or k == "s" then step(1)
  elseif k == "left" or k == "a" or k == "right" or k == "d" then
    local it = mm.items[menu.sel]
    if it and it.change then it.change((k == "left" or k == "a") and -1 or 1) F.saveCfg() F.sfx(3, 700, 0.03, 0.3, "square") end
  elseif k == "enter" or k == "space" or k == "e" then
    menuActivate(menu.sel)
  elseif k == "escape" or k == "backspace" then
    if #menu.stack > 1 then pop()
    elseif menu.from == "pause" then menu.stack = {} state = "play"
    elseif menu.stack[1] ~= "main" then openMenu("main") end
  end
end

local function menuClick(x, y)
  local py = (y - 0.5) * IY
  for i, r in pairs(menu.rows) do
    if py >= r.y and py < r.y + r.h then menu.sel = i menuActivate(i) return end
  end
end

-- ======================= экраны поверх кадра =======================
local function drawPanel(lines, y0)
  local m = 8 * UI
  local y = y0 or floor(CH * 0.3)
  for _, l in ipairs(lines) do
    local s, col, sc = l[1], l[2] or WHITE, l[3] or UI
    ctext(y, s, col, sc)
    y = y + 7 * sc + 6 * UI
  end
end

local function drawOverlay()
  if state == "dead" then
    drawPanel({ { "вы погибли", RED, UI * 3 }, { "r — начать уровень заново · esc — меню", WHITE } })
  elseif state == "next" then
    local tsec = stats.time
    drawPanel({ { "уровень " .. levelNo .. " пройден!", YELLOW, UI * 3 },
      { string.format("время %d:%02d · враги %d из %d · урон получен %d", floor(tsec / 60), floor(tsec % 60), stats.kills, stats.total, stats.taken) },
      { "пробел или enter — дальше", GREEN } })
  elseif state == "won" then
    local tsec = run.time
    local lines = { { "станция «глубина-7» спасена", YELLOW, UI * 3 }, { "разлом закрыт!", GREEN, UI * 2 },
      { string.format("всё время %d:%02d · враги %d · урон получен %d · %s", floor(tsec / 60), floor(tsec % 60), run.kills, run.taken, DIFF.name) } }
    if stats.record then lines[#lines + 1] = { stats.record, YELLOW } end
    lines[#lines + 1] = { "r — сыграть снова · esc — меню", WHITE }
    drawPanel(lines)
  end
end

local function show()
  if imageMode then cv:image(1, 1) else cv:present(1, 1) end
end

--- Кадр целиком: мир, интерфейс, меню.
local function render(fps, cam)
  if state == "menu" and (not menu.from) then
    F.renderWorld({ F.pathAt(now - t0) })
  else
    F.renderWorld(cam)
  end
  if state == "menu" then
    drawMenu()
  elseif cam then
    local m = 5 * UI
    txt(m, CH - m - 9 * UI, "р а з л о м · облёт · уровень " .. levelNo .. " · q — выход", GREY)
  else
    if keys[BIND.map] and state == "play" then drawMap()
    else
      if not P.dead then drawHud(fps) end
      drawOverlay()
    end
  end
  show()
end

return render, show, menuKey, menuClick, openMenu, currentObjective, menu
end)()

-- ======================= рекорд =======================
function F.saveRecord(sec)
  if not run.fromStart then return "рекорд — только за игру с первого уровня" end
  local rec = F.readKV(REC_FILE)
  local best = tonumber(rec[diffKey])
  if diffKey == "normal" and not best and fs.exists(HOME .. ".razlom") then   -- рекорд из «Разлома» 1.x
    local f = fs.open(HOME .. ".razlom", "r")
    if f then best = tonumber(f.readAll()) f.close() end
  end
  if not best or sec < best then
    rec[diffKey] = floor(sec)
    if mode == "play" then F.writeKV(REC_FILE, rec) end
    return best and string.format("новый рекорд! было %d:%02d", floor(best / 60), floor(best % 60)) or "первый рекорд!"
  end
  return string.format("рекорд: %d:%02d", floor(best / 60), floor(best % 60))
end

-- ======================= главный цикл =======================
function F.setMouseLock(on)
  if term and term.setMouseLock and mouseLocked ~= on then pcall(term.setMouseLock, on) mouseLocked = on end
end
function F.setEscape(on)
  if term and term.setEscapeCapture and escCaptured ~= on then pcall(term.setEscapeCapture, on) escCaptured = on end
end

function F.finish(text)
  F.setMouseLock(false)
  F.setEscape(false)
  if snd then pcall(snd.stop) end
  if gpu.clearImage then pcall(gpu.clearImage) end
  pcall(function() cv:free() end)
  F.freeLevel()
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
t0 = now
-- сразу в бой — для проверок (at=, lvl=, god, give) и замеров; иначе — главное меню
local direct = opt.at or opt.lvl or opt.god or opt.give or mode ~= "play"
if direct then
  F.newGame(startLevel, opt.diff and DIFFS[opt.diff] and opt.diff or "normal")
else
  -- фон меню: уровень из сохранения (если есть), камера облетает его; сохранение не трогаем
  local sv = F.loadSave()
  local lvl = sv and max(1, min(LEVELS, tonumber(sv.lvl) or 1)) or 1
  if lvl ~= levelNo then pcall(F.loadLevel, lvl) end
  F.collectLamps()
  DIFF = DIFFS.normal
  P = F.newPlayer()
  F.saveSnapshot()
  F.startLevelState()
  openMenu("main")
end
gpu.setBackground(0x000000)
gpu.fill(1, 1, W, H, " ")

-- замер: облёт без ожидания
if mode == "bench" then
  local n = tonumber(args[2]) or 90
  for i = 1, 5 do now = uptime() F.renderWorld({ F.pathAt(i * 0.1) }) show() end
  local tb = uptime()
  for i = 1, n do
    now = uptime()
    F.updateParts(1 / 30)
    F.renderWorld({ F.pathAt(i * 0.12) })
    show()
  end
  local dt = uptime() - tb
  local used = gpu.totalMemory and (gpu.totalMemory() - gpu.freeMemory()) or 0
  F.finish(string.format("RAZLOM %d×%d точек%s: %d кадров за %.2f с — %.1f кадров/с, %.1f мс на кадр; видеопамять %d клеток%s",
    CW, CH, imageMode and " картинкой" or "", n, dt, n / dt, dt / n * 1000, used, lowVram and " (простые текстуры)" or ""))
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
      F.updateDoors(1 / 30) F.updateShots(1 / 30) F.updateParts(1 / 30)
    end
  end
  for n = 1, LEVELS do
    if n ~= levelNo then F.loadLevel(n) end
    snapshot = nil
    P = F.newPlayer() P.god = true
    F.saveSnapshot()
    F.startLevelState()
    local name = "ур." .. n .. ": "
    local used = gpu.totalMemory and (gpu.totalMemory() - gpu.freeMemory()) or 0
    local sc = Lv.cellAt(P.x, P.z)
    ok(name .. "игрок на старте стоит на полу и может ходить",
      sc ~= nil and abs(P.y - sc.f) < 0.01 and not Lv.blocked(P.x, P.y, P.z, P.r, P.h))
    ok(name .. "загрузился, врагов " .. stats.total .. ", видеопамять " .. used .. " клеток (кадр " .. CW .. "×" .. CH ..
      (imageMode and " картинкой" or "") .. ")", stats.total > 0 and #levelMeshes > 0)
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
    for _, c in ipairs(consoles) do
      local kind, arg = c.target:match("^(%a+):?(.*)$")
      if kind == "turrets" then
        F.useConsole(c)
        local all, off = 0, 0
        for _, e in ipairs(enemies) do if e.def.static and e.group == arg then all = all + 1 if e.off then off = off + 1 end end end
        ok(name .. "пульт выключил турели " .. off .. "/" .. all, all > 0 and off == all)
      end
    end
    local boss
    for _, e in ipairs(enemies) do if e.def.boss then boss = e end end
    if boss then
      local hp = boss.hp
      F.hurtEnemy(boss, 100)
      ok(name .. "без обруча босс почти не ранится", hp - boss.hp < 20)
      local ring
      for _, c in ipairs(consoles) do if c.target == "ring" then ring = c end end
      ok(name .. "на уровне есть излучатели обруча", ring ~= nil)
      if ring then F.useConsole(ring) end
      hp = boss.hp
      F.hurtEnemy(boss, 100)
      ok(name .. "вскрытый босс получает полный урон", hp - boss.hp >= 99)
      F.killEnemy(boss)
      ok(name .. "после босса открыт выход", portalOpen)
    elseif Lv.exit.need == "kills" then
      for _, e in ipairs(enemies) do if not e.dead then F.killEnemy(e) end end
      ok(name .. "после всех врагов открыт выход", portalOpen)
    else
      ok(name .. "выход открыт сразу", portalOpen)
    end
    P.x, P.y, P.z = Lv.exit.x, Lv.exit.y, Lv.exit.z
    P.onGround = true
    state = "play"
    F.updatePlayer(1 / 30)
    ok(name .. "через выход — " .. (n < LEVELS and "на следующий уровень" or "победа"), state == (n < LEVELS and "next" or "won"))
  end
  -- присесть: ниже, под низким потолком не встать
  F.loadLevel(1) F.collectLamps()
  P = F.newPlayer() F.saveSnapshot() F.startLevelState()
  keys[BIND.crouch] = true F.updatePlayer(1 / 30)
  ok("присесть: игрок ниже", P.h < 1.5)
  keys[BIND.crouch] = nil F.updatePlayer(1 / 30)
  ok("встать: снова в полный рост", P.h > 1.7)
  local f = opt.out and fs.open(opt.out, "w")
  if f then
    f.write(table.concat(lines, "\n") .. "\n") f.close()
    local mk = fs.open(opt.out .. ".ok", "w") mk.write("ok") mk.close()
  end
  F.finish()
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
    for _, e in ipairs(enemies) do F.updateEnemy(e, 1 / 30) end
    F.updateShots(1 / 30) F.updateParts(1 / 30) F.updateDoors(1 / 30)
  end
  if opt.expose then for _, e in ipairs(enemies) do if e.def.boss then e.exposedT = now + 5 e.alert = true end end end
  if opt.beam then for _, c in ipairs(consoles) do c.beamT = now + 5 end end
  if opt.hp then P.hp = tonumber(opt.hp) or P.hp end
  if opt.hurt then P.hurtT, P.hurtDir = now - 0.1, P.yaw + (tonumber(opt.hurt) or 1) end
  if opt.feed then F.feed("+12 патронов") end
  if opt.fakesave then F.writeKV(SAVE_FILE, { lvl = 3, diff = "normal", hp = 82, armor = 50, weapon = 2, have = "111", shells = 30, cells = 20, time = 872 }) end
  if opt.menu then openMenu(opt.menu) t0 = now - 3 end
  if opt.pause then openMenu("pause", "pause") end
  if opt.map then keys[BIND.map] = true end
  if opt.toast then objective, objectiveT = currentObjective(), now end
  if opt.near then P.switchT = now end
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
  F.finish()
  return
end

local TARGET = 1 / 30
local last = uptime()
local timer = os.startTimer(0)
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
    if mode == "demo" then
      if k == "q" or k == "escape" then quit = true end
    elseif state == "menu" then
      menuKey(k)
    elseif state == "play" then
      if k == "escape" or k == "p" then
        keys, mouseFire, adsHeld = {}, false, false
        openMenu("pause", "pause")
      elseif k == "1" or k == "2" or k == "3" then F.switchWeapon(tonumber(k))
      elseif k == BIND.lastWeapon then F.switchWeapon(P.lastWeapon)
      elseif k == "c" then P.pitch = 0
      end
      if state == "play" then keys[k] = true end
    elseif state == "dead" then
      if k == "r" then F.startLevelState()
      elseif k == "escape" then openMenu("main") end
    elseif state == "next" then
      if k == "space" or k == "enter" then
        local ok, err = pcall(F.nextLevel)
        if not ok then F.finish("Разлом: не загрузился уровень: " .. tostring(err)) return false, tostring(err) end
        last = uptime()
      elseif k == "escape" then pcall(F.nextLevel) openMenu("main") end
    elseif state == "won" then
      if k == "r" then F.newGame(1, diffKey)
      elseif k == "escape" or k == "enter" then openMenu("main") end
    end
  elseif name == "key_up" then
    keys[ev[2]] = nil
  elseif name == "mouse_click" then
    if state == "menu" then
      if ev[2] == 1 then menuClick(ev[3], ev[4]) end
    elseif state == "play" then
      if ev[2] == 1 then mouseFire = true elseif ev[2] == 2 then adsHeld = true end
    end
  elseif name == "mouse_up" then
    if ev[2] == 1 then mouseFire = false elseif ev[2] == 2 then adsHeld = false end
  elseif name == "mouse_scroll" then
    if state == "play" then F.cycleWeapon(ev[2] > 0 and 1 or -1)
    elseif state == "menu" then menuKey(ev[2] > 0 and "down" or "up") end
  elseif name == "mouse_move" then
    if state == "play" and mode == "play" then
      local k = cfg.sens * MOUSE_RAD * (1 - 0.4 * P.ads)
      P.yaw = P.yaw + (tonumber(ev[2]) or 0) * k
      P.pitch = max(-1.35, min(1.35, P.pitch - (tonumber(ev[3]) or 0) * k * (cfg.invert and -1 or 1)))
    end
  elseif name == "timer" and ev[2] == timer then
    local t = uptime()
    local dt = min(0.1, t - last)
    last = t
    if mode == "demo" then
      now = t
      demoT = demoT + dt
      for _, e in ipairs(enemies) do e.anim = e.anim + dt * 2 end
      F.updateParts(dt)
      render(fps, { F.pathAt(demoT) })
    elseif state == "menu" or state == "next" then
      if menu.from == "pause" or state == "next" then t0 = t0 + dt end   -- время уровня стоит
      now = t
      F.updateParts(dt)
      render(fps)
    else
      now = t
      if state == "play" and now >= flowT then
        flowT = now + 0.6
        Lv.flow(P.x, P.z)
      end
      F.updatePlayer(dt)
      if state == "play" or state == "dead" then
        for _, e in ipairs(enemies) do F.updateEnemy(e, dt) end
      end
      F.updateDoors(dt)
      F.updateShots(dt)
      F.updateParts(dt)
      F.updateItems()
      if state == "won" and not stats.record then
        stats.record = F.saveRecord(run.time)
        F.saveProgress(LEVELS)
        if mode == "play" and fs.exists(SAVE_FILE) then fs.delete(SAVE_FILE) end
      end
      render(fps)
    end
    F.setMouseLock(mode == "play" and (state == "play" or state == "dead" or state == "next" or state == "won"))
    F.setEscape(mode == "play")
    frames = frames + 1
    if t - fpsT >= 1 then fps = floor(frames / (t - fpsT) + 0.5) frames = 0 fpsT = t end
    if endAt and t - started >= endAt then quit = true end
    timer = os.startTimer(max(0, TARGET - (uptime() - t)))
  elseif name ~= "timer" and name ~= "char" and name ~= "screen_resized" then
    foreign[#foreign + 1] = ev
  elseif name == "timer" then
    foreign[#foreign + 1] = ev
  end
end

if state ~= "menu" and mode == "play" and state ~= "won" then F.saveSnapshot() F.saveGame() end
F.finish(mode == "play" and string.format("Разлом: уровень %d, врагов уничтожено %d", levelNo, run.kills + (stats and stats.kills or 0)) or nil)
for _, ev in ipairs(foreign) do pcall(os.queueEvent, table.unpack(ev)) end
return true
