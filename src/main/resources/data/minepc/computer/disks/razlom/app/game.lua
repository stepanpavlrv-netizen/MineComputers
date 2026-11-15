-- Разлом: 3D-шутер для видеокарт MineComputers (настоящее 3D: можно смотреть вверх и вниз,
-- ходить по мосту над залом и под ним, прыгать на площадки).
--   game            — играть
--   game demo [с]   — облёт уровня (сам), для показа
--   game bench [N]  — замер: N кадров облёта, печатает кадры в секунду
-- Управление: W/S/A/D — ходьба, ←/→ — поворот, ↑/↓ — взгляд, пробел — прыжок, Ctrl или F (или левая кнопка
-- мыши) — огонь, 1/2/3 — оружие, правая кнопка мыши с протяжкой — обзор, P — пауза, Q — выход.
local args = { ... }
local mode = args[1] or "play"
-- «wired» среди аргументов — запущено из окна Wired: экран потом перерисует сама система
local embedded = false
for _, a in ipairs(args) do if a == "wired" then embedded = true end end
local gpu = component and component.gpu
if not gpu or not gpu.newMesh or not gpu.canvas then
  print("Нужна видеокарта MineComputers с 3D (gpu.newMesh). Обновите мод.")
  return
end

local floor, sqrt, abs, cos, sin, atan2, min, max = math.floor, math.sqrt, math.abs, math.cos, math.sin, math.atan2, math.min, math.max
local pi, huge = math.pi, math.huge
local uptime = computer.uptime
local rnd = math.random
math.randomseed(floor(uptime() * 1000) % 100000)

-- Папка игры (рядом лежат art.lua и level.lua)
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

-- Самый большой кадр, на который хватает видеопамяти (с запасом под текстуры и сетки)
-- (текстуры и сетки занимают около 6 тыс. клеток в простом качестве и 17 тыс. — в обычном)
local W, H, cv
do
  local scale = 1
  while scale > 0.2 do
    local w, h = max(40, floor(MW * scale)), max(14, floor(MH * scale))
    gpu.setResolution(w, h)
    local c = gpu.canvas(w, (h - HUD) * 2)
    if c then
      local free = gpu.freeMemory and gpu.freeMemory() or 1e9
      if free >= 6500 or w <= 40 then W, H, cv = w, h, c break end
      c:free()
    end
    if w <= 40 and h <= 14 then break end
    scale = scale - 0.1
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
local lowVram = (gpu.freeMemory and gpu.freeMemory() or 1e9) < 20000

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

-- Экран загрузки
local function loading(text, frac)
  gpu.setBackground(0x0c0b10)
  gpu.fill(1, 1, W, H, " ")
  gpu.setForeground(0xff8a3d)
  local title = "Р А З Л О М"
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
  loading("Рисуем текстуры...", 0.05)
  art = need("art", gpu, lowVram)
end)
local Lv
local levelMeshes, acidMesh
if okInit then
  okInit, initErr = pcall(function()
    Lv = need("level")
    loading("Строим станцию и раскладываем свет...", 0.3)
    levelMeshes, acidMesh = Lv.build(art, function(f) loading("Строим станцию и раскладываем свет...", 0.3 + f * 0.65) end)
  end)
end
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

local T, M = art.tex, art.mesh
local STEP = Lv.STEP
local GRAV = 18
local FOG = 0x0c0b10

-- ======================= состояние =======================
local P                         -- игрок
local enemies, items, shots, parts, flashes
local stats, portalOpen, state, message, messageT
local t0, now

local DEF = {
  guard = { hp = 45, speed = 4.0, r = 0.34, h = 1.6, melee = 9, meleeRate = 0.9, sight = 16, eyeY = 1.45,
            name = "Страж", body = "guardBody", leg = "guardLeg", hip = 0.72, hipW = 0.15 },
  gunner = { hp = 85, speed = 2.5, r = 0.4, h = 1.85, shot = "fire", rate = 1.8, dmg = 12, speedShot = 11, keep = 6,
             sight = 26, eyeY = 1.7, name = "Стрелок", body = "gunnerBody", leg = "gunnerLeg", hip = 0.86, hipW = 0.18,
             muzzle = { 0.7, 1.04, 0.47 } },
  eye = { hp = 35, speed = 3.2, r = 0.42, h = 0.84, fly = true, shot = "bolt", rate = 1.35, dmg = 8, speedShot = 15,
          sight = 22, eyeY = 0, name = "Око" },
}

local WEAPONS = {
  { name = "Бластер", mesh = "blaster", rate = 0.36, dmg = 15, pellets = 1, spread = 0.006, kick = 0.12,
    hold = { 0.11, -0.115, 0.24 }, scale = 0.75, tip = 0.27 },
  { name = "Дробовик", mesh = "shotgun", rate = 0.8, ammo = "shells", dmg = 8, pellets = 8, spread = 0.065, kick = 0.3,
    hold = { 0.11, -0.11, 0.2 }, scale = 0.6, tip = 0.52 },
  { name = "Плазмомёт", mesh = "plasmagun", rate = 0.7, ammo = "cells", proj = true, dmg = 95, splash = 3.3, kick = 0.22,
    hold = { 0.11, -0.12, 0.22 }, scale = 0.5, tip = 0.54 },
}

local ITEMS = {
  medkit = { mesh = "medkit", name = "Аптечка" }, armor = { mesh = "armor", name = "Бронежилет" },
  shells = { mesh = "shells", name = "Патроны" }, cells = { mesh = "cells", name = "Плазменные батареи" },
  shotgun = { mesh = "shotgun", name = "Дробовик", scale = 1.6 }, plasmagun = { mesh = "plasmagun", name = "Плазмомёт", scale = 1.6 },
}

local function say(text, sec)
  message = text
  messageT = now + (sec or 2.5)
end

-- ======================= движение тел (игрок и враги) =======================
--- Сдвинуть тело по горизонтали с заходом на ступеньки; потом гравитация. Возвращает true, если упёрлось.
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
  -- вертикаль
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

-- ======================= урон =======================
local function hurtPlayer(dmg, fromX, fromZ)
  if P.dead or state ~= "play" or P.god then return end
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
    say("Вы погибли. R — заново, Q — выход", 1e9)
    sfx(4, 300, 1.2, 1, "sawtooth", 40)
  end
end

local function aliveCount()
  local n = 0
  for _, e in ipairs(enemies) do if not e.dead then n = n + 1 end end
  return n
end

local function killEnemy(e)
  e.dead, e.deadT = true, now
  stats.kills = stats.kills + 1
  local cx, cy, cz = e.x, e.y + (e.def.fly and 0 or e.def.h * 0.6), e.z
  burst(T.oil, cx, cy, cz, 10, 4, 0.9, 0.16, 10)
  burst(T.spark, cx, cy, cz, 8, 6, 0.4, 0.1, 6, true)
  if e.def.fly then
    burst(T.fire, cx, cy, cz, 6, 3, 0.5, 0.5, -1, true)
    flashLight(cx, cy, cz, 5, 0xff9040, 1.2, 0.4)
  end
  sfx(2, e.def.fly and 900 or 480, 0.5, 0.9, "sawtooth", 40)
  local left = aliveCount()
  if left == 0 then
    portalOpen = true
    say("Все враги уничтожены! Портал открыт на северном балконе", 5)
    arpeggio({ 523, 659, 784, 1047 }, 110)
  elseif left <= 3 then
    say("Осталось врагов: " .. left)
  end
end

local function hurtEnemy(e, dmg, kx, kz)
  if e.dead then return end
  e.hp = e.hp - dmg
  e.flashT = now + 0.1
  e.alert = true
  if kx and not e.def.fly then e.push = (e.push or 0) + dmg * 0.02; e.pushX, e.pushZ = kx, kz end
  if e.hp <= 0 then killEnemy(e)
  elseif now - (e.painT or 0) > 0.3 then
    e.painT = now
    sfx(2, 240 + rnd(0, 60), 0.12, 0.6, "square", 120)
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
      local cy = e.def.fly and e.y or e.y + e.def.h * 0.5
      local d = dist3(x, y, z, e.x, cy, e.z)
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

--- Луч против врагов (коробка вокруг тела): ближайший враг и расстояние.
local function rayEnemy(ox, oy, oz, dx, dy, dz, maxd)
  local best, bestT = nil, maxd
  for _, e in ipairs(enemies) do
    if not e.dead then
      local d = e.def
      local r = d.r + 0.08
      local y1, y2
      if d.fly then y1, y2 = e.y - 0.45, e.y + 0.45 else y1, y2 = e.y, e.y + d.h end
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
    -- нет патронов — берём что есть
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
  -- вверх камеры = вправо × вперёд (у «вправо» нет вертикали)
  local ux, uy, uz = -rz * fy, rz * fx - rx * fz, rx * fy
  local tipX = ex + fx * 0.5 + rx * 0.14 - ux * 0.12
  local tipY = ey + fy * 0.5 - uy * 0.12
  local tipZ = ez + fz * 0.5 + rz * 0.14 - uz * 0.12
  flashLight(tipX, tipY, tipZ, w.proj and 4 or 5, w.proj and 0x60d0ff or 0xffc070, 1.0, 0.07)
  -- враги слышат выстрел: рядом — сквозь стены, подальше — если видят
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
    -- разброс в плоскости экрана
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
      particle(T.oil, hx, hy, hz, dx * -2 + rnd() - 0.5, 1.5, dz * -2 + rnd() - 0.5, 0.5, 0.14, 9)
    else
      particle(T.spark, hx - dx * 0.05, hy - dy * 0.05, hz - dz * 0.05, -dx * 2, 1.5, -dz * 2, 0.25, 0.1, 6, true)
      if rnd() < 0.5 then particle(T.smoke, hx - dx * 0.1, hy - dy * 0.1, hz - dz * 0.1, 0, 0.4, 0, 0.6, 0.3, -0.3) end
    end
  end
end

-- ======================= враги =======================
local flowT = -1
local function enemyShoot(e)
  local d = e.def
  local px, py, pz = P.x, P.y + 1.0, P.z
  local sx, sy, sz
  if d.muzzle then
    local m = d.muzzle
    local c, s = cos(e.yaw), sin(e.yaw)
    sx, sy, sz = e.x + m[1] * c - m[3] * s, e.y + m[2], e.z + m[1] * s + m[3] * c
  else
    local fx, fy, fz = forward(e.yaw, e.pitch or 0)
    sx, sy, sz = e.x + fx * 0.5, e.y + fy * 0.5, e.z + fz * 0.5
  end
  -- упреждение: туда, где игрок будет, плюс немного промаха
  local dd = dist3(sx, sy, sz, px, py, pz)
  local tt = dd / d.speedShot * 0.6
  px, pz = px + P.vx * tt + (rnd() - 0.5) * 0.6, pz + P.vz * tt + (rnd() - 0.5) * 0.6
  local dx, dy, dz = px - sx, py - sy, pz - sz
  local l = sqrt(dx * dx + dy * dy + dz * dz)
  local sp = d.speedShot
  shots[#shots + 1] = { x = sx, y = sy, z = sz, vx = dx / l * sp, vy = dy / l * sp, vz = dz / l * sp, owner = e,
                        tex = d.shot == "fire" and T.fire or T.bolt, size = d.shot == "fire" and 0.4 or 0.26,
                        dmg = d.dmg, splash = d.shot == "fire" and 1.3 or nil, life = 4,
                        light = d.shot == "fire" and 0xff8030 or 0xff3020 }
  local vol = max(0.1, 0.7 - dd / 40)
  if d.shot == "fire" then sfx(2, 420, 0.25, vol, "triangle", 160) else sfx(2, 1500, 0.12, vol, "square", 700) end
end

local function updateEnemy(e, dt)
  local d = e.def
  if e.dead then return end
  local dx, dz = P.x - e.x, P.z - e.z
  local dxz = sqrt(dx * dx + dz * dz)
  local ey = d.fly and e.y or e.y + d.eyeY
  -- замечает игрока (проверка видимости не каждый кадр)
  if now >= e.lookT then
    e.lookT = now + 0.3 + rnd() * 0.2
    -- пока не встревожен, видит только перед собой (или совсем рядом)
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
  -- отдача от попаданий
  if e.push and e.push > 0 and not d.fly then
    moveBody(e, e.pushX * e.push * dt * 8, e.pushZ * e.push * dt * 8, 0)
    e.push = max(0, e.push - dt * 3)
  end

  if d.fly then
    -- око кружит над игроком и держит дистанцию
    e.orbit = e.orbit + dt * 0.6
    local tx = P.x - cos(e.orbit) * 5.5
    local tz = P.z - sin(e.orbit) * 5.5
    local ty = P.y + 2.6 + sin(now * 0.9 + e.orbit) * 0.8
    local mx, my, mz = tx - e.x, ty - e.y, tz - e.z
    -- слишком близко к игроку — отлетает
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
      enemyShoot(e)
    end
    return
  end

  -- куда идти: прямо к игроку, если видно и путь свободен, иначе по полю расстояний
  local goX, goZ
  local stand = false
  if d.shot and e.sees and dxz < 18 then
    stand = dxz > d.keep
    if dxz < d.keep - 1 then goX, goZ = -dx, -dz end         -- отступает
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
            -- по диагонали — только если обе соседние клетки проходимы
            if v and v < best and (ox == 0 or oz == 0 or (Lv.distAt(cx + ox + 0.5, cz + 0.5) and Lv.distAt(cx + 0.5, cz + oz + 0.5))) then
              best, bx, bz = v, cx + ox + 0.5, cz + oz + 0.5
            end
          end
        end
      end
    end
    if bx then goX, goZ = bx - e.x, bz - e.z else goX, goZ = dx, dz end
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
  -- атака
  if d.melee then
    if dxz < d.r + 0.35 + 0.75 and abs(P.y - e.y) < 1.3 and now >= e.atkT then
      e.atkT = now + d.meleeRate
      e.swingT = now
      hurtPlayer(d.melee, e.x, e.z)
      sfx(2, 160, 0.15, 0.8, "sawtooth", 60)
    end
  elseif e.sees and now >= e.atkT then
    e.atkT = now + d.rate * (0.8 + rnd() * 0.4)
    e.swingT = now
    enemyShoot(e)
  end
  -- упал в кислоту
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
    local steps = max(1, math.ceil(sqrt(s.vx * s.vx + s.vy * s.vy + s.vz * s.vz) * dt / 0.25))
    local sdt = dt / steps
    for _ = 1, steps do
      if hit then break end
      local nx, ny, nz = s.x + s.vx * sdt, s.y + s.vy * sdt, s.z + s.vz * sdt
      if Lv.solidPoint(nx, ny, nz) then hit = true break end
      s.x, s.y, s.z = nx, ny, nz
      if s.owner == "player" then
        for _, e in ipairs(enemies) do
          if not e.dead then
            local d = e.def
            local y1, y2 = d.fly and e.y - 0.45 or e.y, d.fly and e.y + 0.45 or e.y + d.h
            if abs(nx - e.x) < d.r + 0.2 and abs(nz - e.z) < d.r + 0.2 and ny > y1 - 0.2 and ny < y2 + 0.2 then hit = true break end
          end
        end
      elseif not P.dead then
        if abs(nx - P.x) < 0.5 and abs(nz - P.z) < 0.5 and ny > P.y - 0.1 and ny < P.y + 1.8 then hit = true end
      end
    end
    if hit then
      if s.splash and s.owner == "player" then
        explode(s.x, s.y, s.z, s.splash, s.dmg, "player")
      elseif s.owner ~= "player" then
        if not P.dead and dist3(s.x, s.y, s.z, P.x, P.y + 0.9, P.z) < (s.splash or 0.9) + 0.4 then
          hurtPlayer(s.dmg, s.x, s.z)
        end
        burst(s.tex, s.x, s.y, s.z, 5, 3, 0.3, 0.2, 2, true)
        flashLight(s.x, s.y, s.z, 3, s.light, 1, 0.2)
      end
      table.remove(shots, i)
    else
      -- след
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

-- ======================= предметы =======================
local function pickup(it)
  local k = it.kind
  if k == "medkit" then
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
    P.have[2] = true
    P.ammo.shells = min(60, P.ammo.shells + 12)
    P.weapon = 2
  elseif k == "plasmagun" then
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
          if it.kind == "shotgun" or it.kind == "plasmagun" then arpeggio({ 392, 523, 659 }, 70)
          else sfx(3, 620, 0.15, 0.6, "square", 1250) end
        end
      end
    end
  end
end

-- ======================= игрок =======================
local keys = {}
local mouseFire, dragX, dragY = false, nil, nil

local function updatePlayer(dt)
  if P.dead then
    P.vx, P.vz = P.vx * 0.9, P.vz * 0.9
    moveBody(P, P.vx * dt, P.vz * dt, dt)
    return
  end
  -- поворот и взгляд
  local turn = (keys.right and 1 or 0) - (keys.left and 1 or 0)
  P.yaw = P.yaw + turn * 2.5 * dt
  local look = (keys.up and 1 or 0) - (keys.down and 1 or 0)
  P.pitch = max(-1.35, min(1.35, P.pitch + look * 1.7 * dt))
  -- желаемое направление
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
    -- в воздухе управление слабее (полёт после взрыва не гасится)
    local k = min(1, (P.air and 1.2 or 3) * dt)
    local tvx, tvz = P.vx + (wx * SPEED - P.vx) * k, P.vz + (wz * SPEED - P.vz) * k
    if P.air and tvx * tvx + tvz * tvz < P.vx * P.vx + P.vz * P.vz then
      P.vx, P.vz = P.vx + (tvx - P.vx) * 0.5, P.vz + (tvz - P.vz) * 0.5
    else
      P.vx, P.vz = tvx, tvz
    end
  end
  -- прыжок
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
  -- покачивание при ходьбе
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
      P.vy = Lv.padPush.up
      P.vx, P.vz = Lv.padPush.x, Lv.padPush.z
      P.onGround = false
      P.air = true
      sfx(3, 140, 0.45, 0.8, "sine", 900)
    elseif cell.acid and now >= P.acidT then
      P.acidT = now + 0.5
      P.acidHurtT = now
      hurtPlayer(7)
    end
  end
  -- стрельба и смена оружия
  if keys.ctrl or keys.f or mouseFire then fire() end
  P.kick = max(0, P.kick - dt * 1.6)
  -- портал
  if portalOpen and state == "play" then
    local pp = Lv.portal
    if abs(P.x - pp.x) < 0.8 and abs(P.z - pp.z) < 0.8 and abs(P.y - pp.y) < 1.2 then
      state = "won"
      stats.time = now - t0
      say("", 0)
      arpeggio({ 523, 659, 784, 1047, 1319 }, 120)
    end
  end
end

-- ======================= новая игра =======================
local function newGame()
  local st = Lv.start
  P = { x = st.x, y = 0, z = st.z, vx = 0, vy = 0, vz = 0, r = 0.32, h = 1.75, yaw = st.yaw, pitch = 0,
        hp = 100, armor = 0, onGround = true, weapon = 1, have = { true, false, false }, ammo = { shells = 0, cells = 0 },
        nextFire = 0, kick = 0, bob = 0, jumpT = 0, acidT = 0, hurtT = -10, pickT = -10, fireT = -10, acidHurtT = -10 }
  P.y = Lv.ground(P.x, P.z, P.r, 0)
  enemies, items, shots, parts, flashes = {}, {}, {}, {}, {}
  for _, th in ipairs(Lv.things) do
    local kind, x, y, z = th[1], th[2], th[3], th[4]
    if DEF[kind] then
      local d = DEF[kind]
      enemies[#enemies + 1] = { kind = kind, def = d, x = x, y = y, z = z, vy = 0, r = d.r, h = d.h, onGround = true,
                                hp = d.hp, yaw = th[5] or rnd() * pi * 2, anim = rnd() * 6, lookT = rnd() * 0.5,
                                atkT = 1 + rnd(), orbit = rnd() * pi * 2, light = 1, lightT = -1, alert = false }
    else
      items[#items + 1] = { kind = kind, x = x, y = y, z = z, phase = rnd() * 6 }
    end
  end
  stats = { kills = 0, total = #enemies, taken = 0, time = 0 }
  -- для проверок: at=x,z[,угол] — начать в другом месте; god — без урона; give — всё оружие
  for _, a in ipairs(args) do
    local x, z, yaw = a:match("^at=([%-%d%.]+),([%-%d%.]+),?([%-%d%.]*)$")
    if x then
      P.x, P.z, P.yaw = tonumber(x), tonumber(z), tonumber(yaw) or P.yaw
      P.y = Lv.ground(P.x, P.z, P.r, 10)
    end
    if a == "god" then P.god = true end
    if a == "give" then P.have = { true, true, true } P.ammo = { shells = 60, cells = 50 } P.weapon = 3 end
    if a == "portal" then P.devPortal = true end
  end
  portalOpen = P.devPortal or false
  state = "play"
  t0 = uptime()
  now = t0
  message, messageT = nil, 0
  flowT = -1
  say("Уничтожьте всех на станции. Ctrl — огонь, ↑/↓ — взгляд", 4)
end

-- ======================= отрисовка =======================
local lampList = {}
for _, li in ipairs(Lv.lights) do
  if li.lamp then
    local c = Lv.cellAt(li[1], li[3])
    if c then lampList[#lampList + 1] = { li[1], c.c, li[3] } end
  end
end

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
        local ox, oz = -0.15, i * 0.2
        cv:mesh(M.tentacle, e.x + ox * c - oz * s, y - 0.32, e.z + ox * s + oz * c, e.yaw, sin(e.anim + i) * 0.5, 1, bright, flash)
      end
    end
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
  -- наклон вперёд при атаке
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
  local tsec = (state == "won" and stats.time or (now - t0))
  put(string.format("%d:%02d", floor(tsec / 60), floor(tsec % 60)), 0x8d87a3)
  -- вторая строка: оружие в руках и подсказки
  x = 2
  gpu.setForeground(0x5d5775)
  local slots = ""
  for i, o in ipairs(WEAPONS) do
    if P.have[i] then slots = slots .. (i == P.weapon and "[" .. i .. "]" or " " .. i .. " ") end
  end
  gpu.set(x, H, slots)
  local hint = mode == "demo" and "Демо: облёт станции · Q — выход"
          or "WASD · ←→ поворот · ↑↓ взгляд · пробел прыжок · Ctrl огонь · P пауза · Q выход"
  if unicode.len(hint) > W - 26 then hint = "WASD ←→↑↓ · Ctrl огонь · Q выход" end
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

-- путь облёта для демо и замера: x, y, z, yaw, pitch
local PATH = {
  { 3, 1.6, 4.5, 0, 0 }, { 9, 1.6, 6.8, 0.1, 0 }, { 14, 2.2, 7, 0.4, 0.1 }, { 18, 4.5, 10, 0.9, -0.35 },
  { 23, 5.5, 13, 1.6, -0.6 }, { 24, 3.3, 18, 1.2, -0.1 }, { 26, 3.4, 20, -1.2, 0 }, { 22, 3.3, 15, -1.6, 0.1 },
  { 23, 3.4, 8, -1.7, 0.05 }, { 23, 3.3, 3, -2.7, -0.1 }, { 15, 3.3, 2.5, -3.0, -0.25 }, { 13, 1.6, 12, 1.9, 0.15 },
  { 11, 1.6, 15.8, 3.0, 0 }, { 6, 1.6, 16.3, 2.4, -0.1 }, { 4, 1.6, 9, -1.3, 0 }, { 3, 1.6, 4.5, 0, 0 },
}
local function pathAt(t)
  local n = #PATH - 1
  local seg = (t / 3.2) % n
  local i = floor(seg)
  local f = seg - i
  local a, b = PATH[i + 1], PATH[i + 2]
  f = f * f * (3 - 2 * f)
  local function lerpAng(x, y) local d = (y - x + pi) % (2 * pi) - pi return x + d * f end
  return a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f, a[3] + (b[3] - a[3]) * f, lerpAng(a[4], b[4]), a[5] + (b[5] - a[5]) * f
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
  cv:fog(FOG, 14, 34)
  -- лампы этого кадра (до 16): свечение кислоты, портал, вспышки, снаряды
  cv:clearLights()
  local acidPulse = 0.45 + sin(now * 2.3) * 0.12
  cv:light(24, 0.4, 12, 7.5, 0x50ff70, acidPulse)
  if portalOpen then cv:light(Lv.portal.x, Lv.portal.y + 1.2, Lv.portal.z, 6, 0xb060ff, 1.1) end
  local nl = portalOpen and 2 or 1
  for i = #flashes, 1, -1 do
    local f = flashes[i]
    if nl < 16 then cv:light(f.x, f.y, f.z, f.r, f.c, f.p * f.life / f.max) nl = nl + 1 end
  end
  for i = 1, #shots do
    local s = shots[i]
    if nl < 16 then cv:light(s.x, s.y, s.z, 3.5, s.light, 0.9) nl = nl + 1 end
  end
  -- уровень
  for i = 1, #levelMeshes do cv:mesh(levelMeshes[i].id, 0, 0, 0) end
  if acidMesh then
    local g = floor(20 + sin(now * 3) * 18)
    cv:mesh(acidMesh, 0, 0, 0, 0, 0, 1, 1, g * 256 + floor(g / 3))
  end
  for i = 1, #lampList do
    local l = lampList[i]
    cv:mesh(M.lampBox, l[1], l[2], l[3], 0, 0, 1, 1.8)
  end
  -- предметы: крутятся и покачиваются
  for _, it in ipairs(items) do
    if not it.taken then
      local d = ITEMS[it.kind]
      cv:mesh(M[d.mesh], it.x, it.y + 0.3 + sin(now * 2 + it.phase) * 0.1, it.z, now * 1.6 + it.phase, 0, d.scale or 1, 1.25)
    end
  end
  -- враги
  for _, e in ipairs(enemies) do drawEnemy(e) end
  -- портал
  if portalOpen then
    local pp = Lv.portal
    local k = 0.85 + sin(now * 4) * 0.15
    cv:billboard(T.portal, pp.x, pp.y + 1.1, pp.z, 1.3 * k, 2.1 * k, 1.2, 0, 0)
    cv:billboard(T.glowPurple, pp.x, pp.y + 1.1, pp.z, 2.6, 3.0, 0.8, 0, 1)
    if rnd() < 0.5 then particle(T.glowPurple, pp.x + (rnd() - 0.5) * 1.2, pp.y + 0.2, pp.z + (rnd() - 0.5) * 1.2, 0, 1.5, 0, 0.8, 0.15, 0, true) end
  end
  -- снаряды и частицы
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
    -- вспышка у ствола
    local w = WEAPONS[P.weapon]
    if now - P.fireT < 0.06 then
      local fx, fy, fz = forward(P.yaw, P.pitch)
      local rx, rz = -sin(P.yaw), cos(P.yaw)
      local d = w.hold[3] + w.tip * w.scale + 0.05
      cv:billboard(w.proj and T.plasma or T.flash, ex + fx * d + rx * w.hold[1], ey + fy * d + w.hold[2] * 0.8, ez + fz * d + rz * w.hold[1],
        0.18, 0.18, 1, 0, 1)
    end
    -- оружие в руках: поверх всего (глубину сбрасываем)
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
  -- прицел
  if not cam and not P.dead then
    local cx, cy = floor(CW / 2), floor(CH / 2)
    for _, o in ipairs({ { -2, 0 }, { 2, 0 }, { 0, -2 }, { 0, 2 } }) do cv:pixel(cx + o[1], cy + o[2], 0xd8ffd0) end
  end
  -- цветом по экрану: урон, подбор, кислота, смерть
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
    gpu.set(16, H - 1, "Демо: облёт станции · Q — выход")
    gpu.setForeground(0x5d5775)
    gpu.set(W - 9, H - 1, string.format("%3d к/с", fps))
  else
    hud(fps)
  end
  -- сообщения поверх кадра
  if message and now < messageT and message ~= "" then centerText(2, " " .. message .. " ", 0xffffff, 0x2a2733) end
  if state == "won" then
    local tsec = stats.time
    local y = floor((H - HUD) / 2) - 2
    centerText(y, "  Станция зачищена!  ", 0x14111d, 0xffc857)
    centerText(y + 2, string.format("  Время %d:%02d · враги %d/%d · урон получен %d  ", floor(tsec / 60), floor(tsec % 60),
      stats.kills, stats.total, stats.taken), 0xffffff, 0x2a2733)
    if stats.record then centerText(y + 3, "  " .. stats.record .. "  ", 0xffc857, 0x2a2733) end
    centerText(y + 5, "  R — ещё раз · Q — выход  ", 0xb9b3cc, 0x2a2733)
  elseif state == "pause" then
    centerText(floor((H - HUD) / 2), "  Пауза · P — продолжить · Q — выход  ", 0xffffff, 0x2a2733)
  elseif state == "quit?" then
    centerText(floor((H - HUD) / 2), "  Выйти из игры? Q — да · любая клавиша — нет  ", 0xffffff, 0x8a2030)
  end
end

-- ======================= рекорд =======================
local RECORD = fs.isDir("/home") and "/home/.razlom" or "/.razlom"
local function saveRecord(sec)
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
local function finish(text)
  if snd then pcall(snd.stop) end
  pcall(function() cv:free() end)
  for _, l in ipairs(levelMeshes or {}) do pcall(gpu.freeMesh, l.id) end
  if acidMesh then pcall(gpu.freeMesh, acidMesh) end
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

newGame()

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

local TARGET = 1 / 30
local last = uptime()
local timer = os.startTimer(0)
local quit = false
local endAt = tonumber(args[2])
local started = uptime()
local frames, fps, fpsT = 0, 0, uptime()
local foreign = {}     -- чужие события (сеть, таймеры системы) — вернём в очередь после игры
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
      if mode == "demo" or state == "won" or state == "dead" then quit = true else state = "quit?" end
    elseif k == "p" and (state == "play" or state == "pause") then
      state = state == "play" and "pause" or "play"
    elseif k == "r" and (state == "won" or state == "dead") then
      newGame()
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
  elseif name == "timer" and ev[2] == timer then
    local t = uptime()
    local dt = min(0.1, t - last)
    last = t
    if state == "pause" or state == "quit?" then
      t0 = t0 + dt                              -- время игры не идёт
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
      updateShots(dt)
      updateParts(dt)
      updateItems()
      if state == "won" and not stats.record then stats.record = saveRecord(stats.time) end
      render(fps)
    end
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

finish(mode == "play" and string.format("Разлом: врагов уничтожено %d из %d", stats.kills, stats.total) or nil)
for _, ev in ipairs(foreign) do pcall(os.queueEvent, table.unpack(ev)) end
return true
