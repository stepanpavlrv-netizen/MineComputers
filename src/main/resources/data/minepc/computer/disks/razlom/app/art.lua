-- Разлом: текстуры и 3D-модели. Всё рисуется кодом при запуске — файлов картинок нет.
-- Модели собраны из коробок: вперёд — ось +x, вправо — +z, вверх — +y, ноль — под ногами.
local gpu, lowVram = ...
local floor, max, min, abs, sqrt, sin, cos = math.floor, math.max, math.min, math.abs, math.sqrt, math.sin, math.cos
local rnd = math.random
math.randomseed(1977)

local A = { tex = {}, mesh = {} }
local TR = -1   -- прозрачная точка

local function rgb(r, g, b)
  r = r < 0 and 0 or r > 255 and 255 or floor(r)
  g = g < 0 and 0 or g > 255 and 255 or floor(g)
  b = b < 0 and 0 or b > 255 and 255 or floor(b)
  return r * 65536 + g * 256 + b
end
A.rgb = rgb

--- Текстура w×h из функции f(x, y) → цвет или -1. На слабой видеокарте — вдвое меньше (усреднение 2×2).
local function texture(name, w, h, f)
  local t = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do t[y * w + x + 1] = f(x, y) end
  end
  if lowVram and w >= 16 and h >= 16 then
    local s, hw, hh = {}, floor(w / 2), floor(h / 2)
    for y = 0, hh - 1 do
      for x = 0, hw - 1 do
        local r, g, b, n, tr = 0, 0, 0, 0, 0
        for dy = 0, 1 do for dx = 0, 1 do
          local c = t[(y * 2 + dy) * w + x * 2 + dx + 1]
          if c < 0 then tr = tr + 1 else
            r = r + floor(c / 65536) % 256; g = g + floor(c / 256) % 256; b = b + c % 256; n = n + 1
          end
        end end
        s[y * hw + x + 1] = (tr > 2 or n == 0) and TR or rgb(r / n, g / n, b / n)
      end
    end
    t, w, h = s, hw, hh
  end
  local id, err = gpu.newTexture(w, h, t)
  if not id then error("Не хватает видеопамяти для текстур (" .. tostring(err) .. ")", 0) end
  A.tex[name] = id
  return id
end

local function noise(a) return rnd(-a, a) end

-- ======================= стены, пол, потолок =======================

texture("wall", 32, 32, function(x, y)        -- металлические панели с заклёпками
  local px, py = x % 16, y % 32
  if px == 0 or py == 0 then return rgb(40, 44, 52) end
  if px == 15 or py == 31 then return rgb(118, 124, 136) end
  if (px == 2 or px == 13) and (py == 2 or py == 29) then return rgb(160, 166, 176) end
  if py >= 12 and py <= 13 then return rgb(62, 66, 76) end
  local n = noise(5)
  return rgb(86 + n, 92 + n, 104 + n)
end)

texture("rock", 32, 32, function(x, y)        -- тёмный камень (склад)
  local cell = ((floor(x / 8)) * 7 + (floor(y / 6)) * 13) % 5
  local edge = (y % 6 == 0) or ((x + (floor(y / 6)) * 4) % 8 == 0)
  if edge then return rgb(34, 28, 26) end
  local n = noise(9) + cell * 3
  return rgb(84 + n, 66 + n, 54 + n)
end)

texture("plate", 32, 32, function(x, y)       -- рифлёный металл пола
  if x % 32 == 0 or y % 32 == 0 then return rgb(36, 36, 38) end
  local d = ((x + y) % 8 == 0 and (floor(x / 4)) % 2 == 0) or ((x - y) % 8 == 0 and (floor(x / 4)) % 2 == 1)
  local n = noise(4)
  if d then return rgb(132 + n, 128 + n, 118 + n) end
  return rgb(88 + n, 86 + n, 80 + n)
end)

texture("concrete", 32, 32, function(x, y)
  local n = noise(10)
  if (x * 7 + y * 3) % 31 == 0 then n = n - 22 end
  if x % 32 == 0 or y % 32 == 0 then n = n - 18 end
  return rgb(112 + n, 108 + n, 100 + n)
end)

texture("ceil", 32, 32, function(x, y)        -- решётка потолка
  if x % 8 == 0 or y % 8 == 0 then return rgb(58, 60, 66) end
  return rgb(22 + noise(3), 22, 26)
end)

texture("rust", 32, 32, function(x, y)        -- ржавый металл (бока платформ)
  local n = noise(12)
  local streak = (x * 5 + (floor(y / 3))) % 11 == 0 and -20 or 0
  if y % 16 == 0 then return rgb(50, 32, 22) end
  return rgb(122 + n + streak, 70 + n * 0.6 + streak, 42 + n * 0.3)
end)

texture("hazard", 32, 32, function(x, y)      -- жёлто-чёрные полосы (ступени, края)
  local s = (floor((x + y) / 6)) % 2 == 0
  local n = noise(6)
  if s then return rgb(214 + n, 170 + n, 40) end
  return rgb(30 + n, 28 + n, 26 + n)
end)

texture("acid", 32, 32, function(x, y)        -- кислота
  local b = sin(x * 0.4) * cos(y * 0.33) * 30
  local bubble = ((x - 10) ^ 2 + (y - 20) ^ 2 < 5) or ((x - 24) ^ 2 + (y - 7) ^ 2 < 3)
  if bubble then return rgb(200, 255, 140) end
  return rgb(60 + b, 190 + b + noise(10), 50 + b)
end)

texture("crate", 32, 32, function(x, y)       -- ящик
  if x < 3 or y < 3 or x > 28 or y > 28 then return rgb(70, 74, 60) end
  if abs(x - y) < 2 or abs(x - (31 - y)) < 2 then return rgb(96, 100, 80) end
  local n = noise(6)
  return rgb(126 + n, 112 + n, 70 + n)
end)

texture("lamp", 16, 16, function(x, y)        -- светильник (светится сам)
  if x == 0 or y == 0 or x == 15 or y == 15 then return rgb(80, 80, 84) end
  return rgb(255, 236, 190 - (x % 4 == 0 and 30 or 0))
end)

texture("grate", 32, 32, function(x, y)       -- мостик: решётка с просветами
  if x % 32 < 2 or x % 32 > 29 then return rgb(110, 104, 92) end
  if (floor(x / 4) + floor(y / 4)) % 2 == 0 and x % 4 ~= 0 and y % 4 ~= 0 then return TR end
  return rgb(92 + noise(6), 88, 78)
end)

texture("portal", 32, 32, function(x, y)
  local dx, dy = x - 15.5, y - 15.5
  local r = sqrt(dx * dx + dy * dy)
  local a = math.atan2(dy, dx)
  local swirl = sin(a * 3 + r * 0.6) * 0.5 + 0.5
  return rgb(90 + swirl * 140, 40 + swirl * 60, 200 + swirl * 55)
end)

texture("pad", 32, 32, function(x, y)         -- прыжковая площадка: светящиеся кольца
  local dx, dy = x - 15.5, y - 15.5
  local r = sqrt(dx * dx + dy * dy)
  if r > 15 then return rgb(50, 50, 56) end
  if floor(r / 3) % 2 == 0 then return rgb(255, 170, 40) end
  return rgb(90, 50, 20)
end)

texture("panel", 32, 32, function(x, y)       -- пульт с огоньками (декор)
  if x < 2 or y < 2 or x > 29 or y > 29 then return rgb(50, 52, 60) end
  if y > 6 and y < 12 and x % 6 < 3 then return (floor(x / 6)) % 2 == 0 and rgb(60, 230, 120) or rgb(240, 70, 50) end
  if y > 16 and y < 26 and x > 6 and x < 26 then return rgb(30, 70 + (y % 3) * 20, 90) end
  return rgb(74 + noise(3), 78, 88)
end)

-- ======================= враги =======================

texture("guard", 16, 16, function(x, y)       -- броня стража
  if x % 8 == 0 or y % 8 == 0 then return rgb(50, 16, 14) end
  local n = noise(10)
  return rgb(150 + n, 40 + n * 0.4, 32)
end)
texture("guardDark", 16, 16, function(x, y) local n = noise(8) return rgb(54 + n, 46 + n, 48 + n) end)
texture("eyeGlow", 4, 4, function() return rgb(255, 230, 80) end)
texture("gunner", 16, 16, function(x, y)      -- оливковый металл стрелка
  if y % 8 == 0 then return rgb(40, 46, 26) end
  if (x == 3 or x == 12) and (y % 8 == 4) then return rgb(170, 176, 150) end
  local n = noise(8)
  return rgb(96 + n, 108 + n, 62 + n)
end)
texture("visor", 4, 4, function() return rgb(255, 60, 40) end)
texture("eyeball", 16, 16, function(x, y)     -- око: белок с прожилками
  local n = noise(8)
  if (x * 3 + y * 5) % 17 == 0 then return rgb(200, 60, 60) end
  return rgb(220 + n, 210 + n, 196 + n)
end)
texture("iris", 16, 16, function(x, y)
  local dx, dy = x - 7.5, y - 7.5
  local r = sqrt(dx * dx + dy * dy)
  if r < 2.6 then return rgb(10, 0, 0) end
  if r < 6.5 then return rgb(230 - r * 15, 40, 30) end
  return rgb(220, 205, 190)
end)
texture("flesh", 16, 16, function(x, y) local n = noise(14) return rgb(130 + n, 60 + n * 0.5, 70 + n * 0.5) end)

-- ======================= оружие и предметы =======================

texture("gun", 16, 16, function(x, y)
  if y == 0 then return rgb(130, 136, 150) end
  local n = noise(6)
  return rgb(56 + n, 60 + n, 70 + n)
end)
texture("gunLight", 16, 16, function(x, y) local n = noise(8) return rgb(150 + n, 154 + n, 166 + n) end)
texture("wood", 16, 16, function(x, y) local n = noise(8) + (y % 4 == 0 and -20 or 0) return rgb(120 + n, 76 + n, 40 + n) end)
texture("coil", 8, 8, function(x, y) return (y % 2 == 0) and rgb(80, 240, 255) or rgb(30, 140, 200) end)
texture("medkit", 16, 16, function(x, y)
  if (x >= 6 and x <= 9 and y >= 3 and y <= 12) or (y >= 6 and y <= 9 and x >= 3 and x <= 12) then return rgb(230, 30, 40) end
  return rgb(236, 236, 230)
end)
texture("armor", 16, 16, function(x, y)
  if x % 5 == 0 then return rgb(30, 90, 40) end
  local n = noise(8)
  return rgb(60 + n, 170 + n, 80 + n)
end)
texture("shells", 16, 16, function(x, y)
  if y < 4 then return rgb(200, 170, 70) end
  return (x % 4 == 0) and rgb(90, 20, 20) or rgb(190, 40, 36)
end)
texture("cells", 16, 16, function(x, y)
  if y < 3 or y > 12 then return rgb(60, 64, 74) end
  return rgb(40 + noise(10), 120, 240)
end)

-- ======================= спрайты (вспышки, снаряды, искры) =======================

local function blob(name, size, r, g, b, hard)
  return texture(name, size, size, function(x, y)
    local dx, dy = (x + 0.5) / size * 2 - 1, (y + 0.5) / size * 2 - 1
    local d = sqrt(dx * dx + dy * dy)
    if d >= 1 then return TR end
    local k = (1 - d) ^ (hard or 1.2)
    return rgb(r * k + 255 * k * k * 0.6, g * k + 255 * k * k * 0.5, b * k + 255 * k * k * 0.4)
  end)
end
blob("plasma", 16, 60, 200, 255)
blob("fire", 16, 255, 120, 30, 0.8)
blob("bolt", 8, 255, 40, 30)
blob("spark", 8, 255, 220, 120, 2)
blob("glowGreen", 16, 80, 255, 120)
blob("glowPurple", 16, 180, 80, 255)
texture("smoke", 16, 16, function(x, y)
  local dx, dy = x - 7.5, y - 7.5
  if dx * dx + dy * dy > 56 then return TR end
  local n = noise(16)
  return rgb(90 + n, 88 + n, 86 + n)
end)
texture("oil", 8, 8, function(x, y)
  local dx, dy = x - 3.5, y - 3.5
  if dx * dx + dy * dy > 12 then return TR end
  return rgb(110 + noise(20), 20, 16)
end)
texture("flash", 16, 16, function(x, y)        -- вспышка выстрела: звезда
  local dx, dy = abs(x - 7.5), abs(y - 7.5)
  local d = min(dx, dy) * 1.8 + sqrt(dx * dx + dy * dy) * 0.35
  if d > 4 then return TR end
  local k = 1 - d / 4
  return rgb(255, 180 + 75 * k, 60 + 190 * k * k)
end)

-- ======================= сборщик сеток =======================

--- Сборщик: квадраты и коробки → одна сетка. light — яркость вершин (1 — обычная, 2 — светится).
function A.builder()
  local V, T = {}, {}
  local nv = 0
  local b = {}
  --- Четырёхугольник: вершины против часовой стрелки, если смотреть на лицевую сторону.
  --- p = {x, y, z, u, v}, l1..l4 — свет вершин.
  function b.quad(p1, p2, p3, p4, tex, l1, l2, l3, l4)
    l2, l3, l4 = l2 or l1, l3 or l1, l4 or l1
    local n = #V
    V[n + 1], V[n + 2], V[n + 3], V[n + 4], V[n + 5], V[n + 6] = p1[1], p1[2], p1[3], p1[4], p1[5], l1
    V[n + 7], V[n + 8], V[n + 9], V[n + 10], V[n + 11], V[n + 12] = p2[1], p2[2], p2[3], p2[4], p2[5], l2
    V[n + 13], V[n + 14], V[n + 15], V[n + 16], V[n + 17], V[n + 18] = p3[1], p3[2], p3[3], p3[4], p3[5], l3
    V[n + 19], V[n + 20], V[n + 21], V[n + 22], V[n + 23], V[n + 24] = p4[1], p4[2], p4[3], p4[4], p4[5], l4
    local k = #T
    T[k + 1], T[k + 2], T[k + 3], T[k + 4] = nv + 1, nv + 2, nv + 3, tex
    T[k + 5], T[k + 6], T[k + 7], T[k + 8] = nv + 1, nv + 3, nv + 4, tex
    nv = nv + 4
  end
  --- Коробка: текстура tex (или таблица {сторона = tex} с полями top, bottom, side), свет l.
  --- uvs — сколько метров на повтор текстуры (nil — каждая грань целиком).
  function b.box(x1, y1, z1, x2, y2, z2, tex, l, uvs)
    l = l or 1
    local tt = type(tex) == "table" and tex or { top = tex, bottom = tex, side = tex }
    local side, top, bot = tt.side, tt.top or tt.side, tt.bottom or tt.side
    local function U(a, a1, a2) if uvs then return a / uvs end return (a - a1) / (a2 - a1) end
    local function Vv(y) if uvs then return -y / uvs end return (y2 - y) / (y2 - y1) end
    -- +x, -x (ярче спереди), +z, -z, верх, низ
    b.quad({ x2, y1, z2, U(-z2, -z2, -z1), Vv(y1) }, { x2, y1, z1, U(-z1, -z2, -z1), Vv(y1) },
           { x2, y2, z1, U(-z1, -z2, -z1), Vv(y2) }, { x2, y2, z2, U(-z2, -z2, -z1), Vv(y2) }, side, l * 0.92)
    b.quad({ x1, y1, z1, U(z1, z1, z2), Vv(y1) }, { x1, y1, z2, U(z2, z1, z2), Vv(y1) },
           { x1, y2, z2, U(z2, z1, z2), Vv(y2) }, { x1, y2, z1, U(z1, z1, z2), Vv(y2) }, side, l * 0.62)
    b.quad({ x1, y1, z2, U(x1, x1, x2), Vv(y1) }, { x2, y1, z2, U(x2, x1, x2), Vv(y1) },
           { x2, y2, z2, U(x2, x1, x2), Vv(y2) }, { x1, y2, z2, U(x1, x1, x2), Vv(y2) }, side, l * 0.78)
    b.quad({ x2, y1, z1, U(-x2, -x2, -x1), Vv(y1) }, { x1, y1, z1, U(-x1, -x2, -x1), Vv(y1) },
           { x1, y2, z1, U(-x1, -x2, -x1), Vv(y2) }, { x2, y2, z1, U(-x2, -x2, -x1), Vv(y2) }, side, l * 0.72)
    local function TU(x) if uvs then return x / uvs end return (x - x1) / (x2 - x1) end
    local function TV(z) if uvs then return z / uvs end return (z - z1) / (z2 - z1) end
    b.quad({ x1, y2, z2, TU(x1), TV(z2) }, { x2, y2, z2, TU(x2), TV(z2) },
           { x2, y2, z1, TU(x2), TV(z1) }, { x1, y2, z1, TU(x1), TV(z1) }, top, l)
    b.quad({ x1, y1, z1, TU(x1), TV(z1) }, { x2, y1, z1, TU(x2), TV(z1) },
           { x2, y1, z2, TU(x2), TV(z2) }, { x1, y1, z2, TU(x1), TV(z2) }, bot, l * 0.5)
  end
  function b.count() return nv, floor(#T / 4) end
  function b.empty() return nv == 0 end
  --- Отдать сетку видеокарте (таблицы после этого можно выбросить).
  function b.mesh(cull)
    if nv == 0 then return nil end
    local id, err = gpu.newMesh(V, T, cull ~= false)
    if not id then error("Не хватает видеопамяти для 3D-моделей (" .. tostring(err) .. ")", 0) end
    return id
  end
  return b
end

local tx = A.tex
local function model(fn)
  local b = A.builder()
  fn(b)
  return b.mesh()
end

-- ======================= модели врагов =======================

-- Страж: быстрый, бьёт когтями. Ноги рисуются отдельно (качаются при ходьбе).
A.mesh.guardBody = model(function(b)
  b.box(-0.25, 0.72, -0.3, 0.25, 1.32, 0.3, tx.guard)                    -- корпус
  b.box(-0.2, 0.62, -0.22, 0.2, 0.75, 0.22, tx.guardDark)                -- пояс
  b.box(-0.14, 1.32, -0.17, 0.26, 1.6, 0.17, tx.guard)                   -- голова
  b.box(0.26, 1.44, -0.13, 0.29, 1.51, -0.03, tx.eyeGlow, 2.2)           -- глаза
  b.box(0.26, 1.44, 0.03, 0.29, 1.51, 0.13, tx.eyeGlow, 2.2)
  b.box(-0.12, 0.98, 0.3, 0.42, 1.12, 0.42, tx.guardDark)                -- руки-когти
  b.box(-0.12, 0.98, -0.42, 0.42, 1.12, -0.3, tx.guardDark)
  b.box(0.42, 0.94, 0.32, 0.56, 1.0, 0.4, tx.gunLight)
  b.box(0.42, 0.94, -0.4, 0.56, 1.0, -0.32, tx.gunLight)
  b.box(-0.32, 1.1, -0.1, -0.25, 1.4, 0.1, tx.guardDark)                 -- гребень на спине
end)
A.mesh.guardLeg = model(function(b)                                      -- нога: ноль — тазобедренный сустав
  b.box(-0.09, -0.72, -0.08, 0.09, 0.02, 0.08, tx.guardDark)
  b.box(-0.08, -0.72, -0.09, 0.2, -0.64, 0.09, tx.guard)
end)

-- Стрелок: крупный, стреляет сгустками плазмы с правой руки.
A.mesh.gunnerBody = model(function(b)
  b.box(-0.3, 0.86, -0.36, 0.3, 1.58, 0.36, tx.gunner)
  b.box(-0.18, 1.58, -0.16, 0.2, 1.84, 0.16, tx.gunner)
  b.box(0.2, 1.64, -0.13, 0.23, 1.72, 0.13, tx.visor, 2.2)               -- забрало
  b.box(-0.12, 0.92, 0.36, 0.62, 1.16, 0.58, tx.gun)                     -- пушка на правой руке
  b.box(0.62, 0.98, 0.42, 0.68, 1.1, 0.52, tx.visor, 1.8)
  b.box(-0.12, 0.9, -0.56, 0.22, 1.42, -0.36, tx.gunner)                 -- левая рука
  b.box(-0.42, 1.1, -0.26, -0.3, 1.5, 0.26, tx.gun)                      -- ранец
end)
A.mesh.gunnerLeg = model(function(b)
  b.box(-0.11, -0.84, -0.1, 0.11, 0.02, 0.1, tx.gun)
  b.box(-0.1, -0.86, -0.11, 0.24, -0.76, 0.11, tx.gunner)
end)

-- Око: летает, стреляет красными разрядами.
A.mesh.eye = model(function(b)
  b.box(-0.36, -0.36, -0.36, 0.36, 0.36, 0.36, tx.eyeball)
  b.box(0.36, -0.2, -0.2, 0.42, 0.2, 0.2, tx.iris, 1.6)
  b.box(-0.06, 0.36, -0.04, 0.06, 0.58, 0.04, tx.flesh)                  -- шип
  b.box(-0.5, -0.06, -0.06, -0.36, 0.06, 0.06, tx.flesh)                 -- хвост
end)
A.mesh.tentacle = model(function(b)
  b.box(-0.04, -0.5, -0.04, 0.04, 0, 0.04, tx.flesh)
end)

-- ======================= предметы =======================

A.mesh.medkit = model(function(b)
  b.box(-0.24, 0, -0.18, 0.24, 0.28, 0.18, { top = tx.medkit, side = tx.medkit, bottom = tx.gun })
end)
A.mesh.armor = model(function(b)
  b.box(-0.1, 0, -0.28, 0.1, 0.5, 0.28, tx.armor)
  b.box(-0.08, 0.5, -0.28, 0.08, 0.6, -0.12, tx.armor)
  b.box(-0.08, 0.5, 0.12, 0.08, 0.6, 0.28, tx.armor)
end)
A.mesh.shells = model(function(b)
  b.box(-0.2, 0, -0.14, 0.2, 0.22, 0.14, tx.shells)
end)
A.mesh.cells = model(function(b)
  b.box(-0.14, 0, -0.14, 0.14, 0.34, 0.14, tx.cells)
  b.box(-0.06, 0.34, -0.06, 0.06, 0.4, 0.06, tx.gunLight)
end)
A.mesh.lampBox = model(function(b)                                       -- светильник на потолке
  b.box(-0.4, -0.12, -0.4, 0.4, 0, 0.4, { side = tx.gun, bottom = tx.lamp, top = tx.gun }, 1)
end)
A.mesh.crate = model(function(b)
  b.box(-0.5, 0, -0.5, 0.5, 1, 0.5, tx.crate)
end)

-- ======================= оружие (в руках и на полу) =======================
-- В руках: ноль — у рукояти, ствол вперёд по +x.

A.mesh.blaster = model(function(b)
  b.box(-0.02, -0.02, -0.035, 0.24, 0.05, 0.035, tx.gun)                 -- ствол
  b.box(-0.06, -0.12, -0.03, 0.03, 0.0, 0.03, tx.gunLight)               -- рукоять
  b.box(0.06, 0.05, -0.012, 0.18, 0.07, 0.012, tx.coil, 1.6)             -- прицельная полоска
  b.box(0.24, -0.005, -0.02, 0.26, 0.035, 0.02, tx.coil, 1.8)
end)
A.mesh.shotgun = model(function(b)
  b.box(-0.24, -0.06, -0.04, 0.02, 0.04, 0.04, tx.wood)                  -- приклад
  b.box(0.0, -0.02, -0.045, 0.5, 0.03, 0.0, tx.gun)                      -- два ствола
  b.box(0.0, -0.02, 0.0, 0.5, 0.03, 0.045, tx.gun)
  b.box(0.05, -0.07, -0.035, 0.24, -0.02, 0.035, tx.wood)                -- цевьё
  b.box(0.48, 0.03, -0.01, 0.5, 0.05, 0.01, tx.gunLight)
end)
A.mesh.plasmagun = model(function(b)
  b.box(-0.18, -0.08, -0.07, 0.3, 0.08, 0.07, tx.gun)                    -- корпус
  b.box(0.3, -0.04, -0.04, 0.52, 0.04, 0.04, tx.gunLight)                -- ствол
  b.box(0.02, 0.08, -0.05, 0.24, 0.12, 0.05, tx.coil, 1.8)               -- светящиеся катушки
  b.box(0.02, -0.12, -0.05, 0.24, -0.08, 0.05, tx.coil, 1.8)
  b.box(-0.06, -0.2, -0.03, 0.04, -0.08, 0.03, tx.gunLight)              -- рукоять
end)

return A
