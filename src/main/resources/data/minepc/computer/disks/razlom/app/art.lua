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

--- Текстура w×h из функции f(x, y) → цвет или -1. На слабой видеокарте — в 2 или 4 раза меньше (усреднение k×k).
--- lowVram: false/nil — полные, true или 2 — вдвое меньше, 4 — вчетверо (самые слабые видеокарты).
local SHRINK = lowVram == 4 and 4 or (lowVram and 2 or 1)
local function texture(name, w, h, f)
  local t = {}
  for y = 0, h - 1 do
    for x = 0, w - 1 do t[y * w + x + 1] = f(x, y) end
  end
  local k = SHRINK
  while k > 1 and (w / k < 4 or h / k < 4) do k = k / 2 end
  if k > 1 then
    local s, hw, hh = {}, floor(w / k), floor(h / k)
    local half = k * k / 2
    for y = 0, hh - 1 do
      for x = 0, hw - 1 do
        local r, g, b, n, tr = 0, 0, 0, 0, 0
        for dy = 0, k - 1 do for dx = 0, k - 1 do
          local c = t[(y * k + dy) * w + x * k + dx + 1]
          if c < 0 then tr = tr + 1 else
            r = r + floor(c / 65536) % 256; g = g + floor(c / 256) % 256; b = b + c % 256; n = n + 1
          end
        end end
        s[y * hw + x + 1] = (tr > half or n == 0) and TR or rgb(r / n, g / n, b / n)
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

-- ======================= враги, оружие, предметы: текстуры =======================
-- (броня охраны «Г-7», робот «07», око, учёный, ползун, сердце разлома, оружие станции, предметы, ключ-карты)
local T = A.tex
local function mix(c, k)  -- {r,g,b} * k → цвет
  return rgb(c[1] * k, c[2] * k, c[3] * k)
end

-- Крошечный трафаретный шрифт 3×5 (для надписей на броне)
local GLYPH = {
  ["Г"] = { "111", "100", "100", "100", "100" }, ["-"] = { "000", "000", "111", "000", "000" },
  ["7"] = { "111", "001", "010", "010", "010" }, ["0"] = { "111", "101", "101", "101", "111" },
}
--- Надпись (список символов) трафаретом: true, если точка (x, y) закрашена.
local function label(chars, x0, y0, x, y)
  local cx, cy = x - x0, y - y0
  if cy < 0 or cy > 4 or cx < 0 then return false end
  local i = floor(cx / 4) + 1
  local col = cx % 4
  local g = GLYPH[chars[i] or ""]
  if not g or col > 2 then return false end
  return g[cy + 1]:sub(col + 1, col + 1) == "1"
end

--- Броневая пластина: панели size×size со скосом (светлый верх-лево, тёмный низ-право), заклёпки, царапины.
local function plate(base, size, opts)
  opts = opts or {}
  local scratches = {}
  for i = 1, (opts.scratches or 5) do scratches[i] = { rnd(0, 31), rnd(0, 31), rnd(3, 7), rnd(0, 1) } end
  return function(x, y)
    local px, py = x % size, y % size
    local k = 1 + noise(4) / 100
    if px == 0 or py == 0 then k = 1.28
    elseif px == size - 1 or py == size - 1 then k = 0.58
    elseif px == 1 or py == 1 then k = 1.1 end
    if opts.rivets and (px == 2 or px == size - 3) and (py == 2 or py == size - 3) then return mix(base, 1.55) end
    for _, s in ipairs(scratches) do
      local dx, dy = x - s[1], y - s[2]
      if (s[4] == 0 and dy == floor(dx / 2) and dx >= 0 and dx < s[3]) or (s[4] == 1 and dx == dy and dx >= 0 and dx < s[3]) then
        return mix(base, 1.45)
      end
    end
    if opts.dirt and py > size - 6 then k = k * (0.8 + py % 2 * 0.06) end
    return mix(base, k)
  end
end

-- ======================= охранник станции =======================
local ARMOR = { 72, 88, 112 }          -- стальная синяя броня охраны «Глубины-7»
texture("g2Armor", 32, 32, plate(ARMOR, 32, { rivets = true, scratches = 6, dirt = true }))
texture("g2Chest", 32, 32, function(x, y)
  if label({ "Г", "-", "7" }, 10, 13, x, y) then return rgb(232, 196, 70) end   -- трафарет «Г-7»
  if y >= 24 and y <= 26 then return (x % 6 < 3) and rgb(232, 196, 70) or rgb(30, 30, 30) end  -- полоса «опасно»
  return plate(ARMOR, 32, { rivets = true, scratches = 3 })(x, y)
end)
texture("g2Under", 32, 32, function(x, y)                 -- ткань комбинезона: плетение
  local k = ((x + y) % 4 < 2) and 1 or 0.9
  local n = noise(5)
  return rgb((38 + n) * k, (42 + n) * k, (48 + n) * k)
end)
texture("g2Belt", 32, 32, function(x, y)
  if y < 2 or y > 29 then return rgb(26, 24, 22) end
  if x % 16 >= 6 and x % 16 <= 9 and y >= 10 and y <= 21 then return rgb(170, 160, 120) end   -- пряжки
  local n = noise(6)
  return rgb(64 + n, 54 + n, 40 + n)
end)
texture("g2Pouch", 16, 16, function(x, y)
  if y < 4 then return (y == 3) and rgb(40, 42, 30) or rgb(86, 90, 64) end
  if x == 0 or x == 15 or y == 15 then return rgb(44, 46, 34) end
  local n = noise(6)
  return rgb(74 + n, 78 + n, 56 + n)
end)
texture("g2Visor", 16, 8, function(x, y)                  -- забрало: тёмное стекло, светится зелёным (разлом)
  if y == 3 or y == 4 then return rgb(140, 255, 150) end
  if y == 2 or y == 5 then return rgb(50, 160, 80) end
  return rgb(14, 30, 24)
end)
texture("g2Metal", 32, 32, function(x, y)
  local k = (y == 0) and 1.5 or (y == 31) and 0.6 or 1
  local n = noise(5)
  return rgb((62 + n) * k, (66 + n) * k, (74 + n) * k)
end)
texture("g2MetalLight", 16, 16, function(x, y)
  local k = (y == 0) and 1.25 or (y == 15) and 0.7 or 1
  local n = noise(6)
  return rgb((150 + n) * k, (156 + n) * k, (166 + n) * k)
end)
texture("g2Boot", 16, 16, function(x, y)
  if y >= 12 then return (x % 3 == 0) and rgb(14, 14, 14) or rgb(30, 30, 30) end   -- протектор
  local n = noise(4)
  return rgb(36 + n, 34 + n, 32 + n)
end)
texture("g2Flesh", 32, 32, function(x, y)                 -- плоть, изменённая разломом: прожилки, светящиеся точки
  local v = abs(math.sin(x * 0.28 + math.sin(y * 0.21) * 2.2) )          -- извилистые светящиеся жилы
  if v < 0.07 then return rgb(130, 255, 150) end
  if v < 0.16 then return rgb(70, 170, 90) end
  local n = noise(6)
  local k = 0.85 + 0.15 * math.sin(y * 0.4 + x * 0.1)
  return rgb((122 + n) * k, (42 + n * 0.4) * k, (56 + n * 0.4) * k)
end)
texture("g2Bone", 16, 16, function(x, y)
  local k = 1 - y / 40
  local n = noise(8)
  return rgb((214 + n) * k, (204 + n) * k, (178 + n) * k)
end)
texture("g2Glow", 4, 4, function() return rgb(120, 255, 140) end)
texture("g2Red", 4, 4, function() return rgb(255, 60, 40) end)

-- ======================= боевой робот =======================
local HULL = { 196, 150, 52 }          -- промышленная жёлтая краска, протёртая до металла на кромках
local hullPlate = plate(HULL, 32, { rivets = true, scratches = 5, dirt = true })
texture("r2Hull", 32, 32, function(x, y)
  if (x <= 1 or y <= 1 or x >= 30 or y >= 30) and noise(10) > 3 then return rgb(130, 132, 136) end   -- облезлые кромки
  return hullPlate(x, y)
end)
texture("r2Chest", 32, 32, function(x, y)
  if label({ "0", "7" }, 12, 6, x, y) then return rgb(24, 24, 24) end
  return hullPlate(x, y)
end)
texture("r2Hazard", 32, 32, function(x, y)
  if y == 0 then return rgb(240, 200, 80) end
  return (((x + y) % 12) < 6) and rgb(210, 166, 50) or rgb(28, 28, 28)
end)
texture("r2Vent", 16, 16, function(x, y)
  if y % 3 == 0 then return rgb(20, 20, 22) end
  return rgb(80, 82, 88)
end)
texture("r2Core", 16, 16, function(x, y)                   -- реактор в ранце: зелёный свет разлома
  local dx, dy = x - 7.5, y - 7.5
  local d = sqrt(dx * dx + dy * dy)
  if x == 0 or y == 0 or x == 15 or y == 15 then return rgb(40, 44, 50) end
  return rgb(80 + 140 * max(0, 1 - d / 9), 255 - d * 6, 120)
end)
texture("r2Lens", 8, 4, function(x, y) return (y == 1 or y == 2) and rgb(255, 80, 50) or rgb(150, 20, 10) end)

-- ======================= око =======================
texture("e2Ball", 32, 32, function(x, y)                   -- белок с прожилками к краям
  local dx, dy = x - 15.5, y - 15.5
  local d = sqrt(dx * dx + dy * dy)
  local a = math.atan2(dy, dx)
  if d > 6 and abs(math.sin(a * 5 + d * 0.3)) < 0.08 then return rgb(180, 40, 50) end
  local n = noise(6)
  local k = 1 - d / 60
  return rgb((226 + n) * k, (214 + n) * k, (196 + n) * k)
end)
texture("e2Iris", 32, 32, function(x, y)                   -- радужка: жёлто-зелёная, зрачок-щель
  local dx, dy = x - 15.5, y - 15.5
  local d = sqrt(dx * dx + dy * dy)
  if abs(dx) < 2.2 - abs(dy) / 10 and abs(dy) < 11 then return rgb(6, 4, 4) end
  if d < 14 then
    local ring = (floor(d) % 3 == 0) and 0.8 or 1
    return rgb((210 - d * 6) * ring, (230 - d * 4) * ring, 60 * ring)
  end
  return rgb(150, 40, 46)
end)
texture("e2Flesh", 32, 32, function(x, y)
  local wet = ((x * 7 + y * 3) % 23 == 0) and 1.5 or 1
  local n = noise(12)
  return rgb((136 + n) * wet, (58 + n * 0.5) * wet, (84 + n * 0.5) * wet)
end)
texture("e2Sucker", 8, 8, function(x, y)
  local dx, dy = x - 3.5, y - 3.5
  return (dx * dx + dy * dy < 4) and rgb(90, 30, 50) or rgb(220, 160, 170)
end)

-- ======================= дробовик =======================
texture("w2Wood", 32, 32, function(x, y)                   -- орех: волокна вдоль, лак
  local grain = math.sin(y * 0.8 + math.sin(x * 0.15) * 2)
  local n = noise(5)
  local k = 0.85 + grain * 0.12
  return rgb((128 + n) * k, (78 + n) * k, (42 + n) * k)
end)
texture("w2Pump", 16, 16, function(x, y)                   -- цевьё с насечкой
  if x % 3 == 0 then return rgb(52, 32, 18) end
  local n = noise(5)
  return rgb(116 + n, 72 + n, 40 + n)
end)
texture("w2Blued", 32, 32, function(x, y)                  -- воронёная сталь
  local k = (y == 0) and 1.6 or (y == 1) and 1.25 or (y >= 30) and 0.6 or 1
  local n = noise(4)
  return rgb((44 + n) * k, (48 + n) * k, (60 + n) * k)
end)
texture("w2Shells", 16, 16, function(x, y)
  local col = x % 4
  if col == 3 then return rgb(20, 20, 20) end
  if y >= 11 then return rgb(210, 176, 80) end            -- латунь
  return rgb(176 - col * 10, 34, 30)
end)
texture("w2Rubber", 8, 8, function(x, y) return (y % 2 == 0) and rgb(24, 24, 24) or rgb(36, 36, 36) end)
texture("w2Sight", 4, 4, function() return rgb(255, 150, 40) end)


-- ======================= ещё текстуры: оружие станции, предметы, новые враги =======================
texture("w2Coil", 8, 8, function(x, y) return (y % 2 == 0) and rgb(110, 240, 255) or rgb(40, 150, 210) end)   -- техника людей — голубая
texture("w2Glass", 16, 16, function(x, y)                  -- окошко батареи: голубой заряд, блик
  if x == 0 or y == 0 or x == 15 or y == 15 then return rgb(50, 54, 62) end
  if x == y + 3 or x == y + 4 then return rgb(220, 250, 255) end
  return rgb(40 + noise(10), 170 + noise(20), 250)
end)
texture("i2Med", 32, 32, function(x, y)                    -- аптечка: белый пластик, красный крест, окантовка
  if x <= 1 or y <= 1 or x >= 30 or y >= 30 then return rgb(150, 154, 160) end
  if (x >= 13 and x <= 18 and y >= 6 and y <= 25) or (y >= 13 and y <= 18 and x >= 6 and x <= 25) then return rgb(214, 34, 40) end
  local n = noise(4)
  return rgb(232 + n, 234 + n, 230 + n)
end)
texture("i2Card", 32, 32, function(x, y)                   -- картон коробки патронов
  local n = noise(5)
  if y >= 10 and y <= 21 then
    if label({ "Г", "-", "7" }, 10, 13, x, y) then return rgb(240, 230, 210) end
    return rgb(150 + n, 30, 28)
  end
  return rgb(176 + n, 140 + n, 96 + n)
end)
texture("i2ShellTop", 16, 16, function(x, y)               -- донца гильз сверху
  local cx, cy = x % 4 - 1.5, y % 4 - 1.5
  local d = cx * cx + cy * cy
  if d < 1.2 then return rgb(120, 90, 40) end
  if d < 3 then return rgb(220, 184, 90) end
  return rgb(40, 30, 24)
end)
local function keycard(r, g, b)
  return function(x, y)
    if x <= 0 or y <= 0 or x >= 31 or y >= 31 then return rgb(r * 0.5, g * 0.5, b * 0.5) end
    if y >= 4 and y <= 9 then return rgb(r, g, b) end                              -- цветная полоса доступа
    if x >= 4 and x <= 10 and y >= 14 and y <= 21 then                             -- чип
      return ((x + y) % 3 == 0) and rgb(150, 120, 40) or rgb(220, 186, 80)
    end
    if label({ "Г", "-", "7" }, 16, 15, x, y) then return rgb(40, 40, 44) end
    if y >= 25 and y <= 26 and x >= 4 and x <= 27 then return rgb(r * 0.7, g * 0.7, b * 0.7) end
    local n = noise(3)
    return rgb(226 + n, 228 + n, 232 + n)
  end
end
texture("k2Red", 32, 32, keycard(220, 50, 50))
texture("k2Yellow", 32, 32, keycard(230, 190, 50))
texture("k2Blue", 32, 32, keycard(60, 120, 230))
texture("k2Holo", 8, 8, function(x, y) return ((x + y) % 2 == 0) and rgb(120, 220, 255) or rgb(60, 140, 200) end)

-- учёный: халат в пятнах, бледная кожа
texture("s2Coat", 32, 32, function(x, y)
  local gx, gy = x - 22, y - 24
  if gx * gx * 0.6 + gy * gy < 26 + noise(6) then return rgb(96, 150, 84) end   -- зелёное пятно разлома
  local rx, ry = x - 7, y - 8
  if rx * rx + ry * ry * 0.5 < 10 + noise(4) then return rgb(132, 54, 50) end   -- бурое пятно
  if x == 16 then return rgb(170, 172, 168) end                   -- шов / застёжка
  local n = noise(5)
  return rgb(214 + n, 216 + n, 210 + n)
end)
texture("s2Skin", 16, 16, function() local n = noise(6) return rgb(150 + n, 160 + n, 140 + n) end)
texture("s2Pants", 16, 16, function() local n = noise(5) return rgb(46 + n, 50 + n, 60 + n) end)
texture("s2Badge", 8, 8, function(x, y)
  if y <= 1 then return rgb(60, 120, 230) end
  return (x >= 2 and x <= 5 and y >= 3 and y <= 6) and rgb(120, 110, 100) or rgb(236, 236, 236)
end)
-- ползун: хитин со светящимися швами
texture("c2Chitin", 32, 32, function(x, y)
  if y == 0 or y == 31 then return rgb(100, 255, 130) end
  if y == 1 or y == 30 then return rgb(40, 110, 60) end
  local k = 1 - abs(y - 15.5) / 40
  local n = noise(5)
  if (x * 5 + y * 3) % 29 == 0 then return rgb(130, 120, 160) end   -- блик
  return rgb((52 + n) * k, (36 + n) * k, (62 + n) * k)
end)
-- сердце разлома: кристалл
texture("b2Crystal", 16, 16, function(x, y)
  local k = ((x + y) % 8 < 2) and 1.25 or 1
  return rgb(90 * k, 255, 150 * k)
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

-- ======================= модели =======================
-- Охранник: синяя броня охраны, шлем с зелёным забралом; разлом прорастает через левое плечо и руку.
A.mesh.guardBody = model(function(b)
  b.box(-0.17, 0.66, -0.24, 0.17, 0.80, 0.24, T.g2Belt)                         -- пояс
  b.box(0.15, 0.68, -0.21, 0.21, 0.79, -0.1, T.g2Pouch)                        -- подсумки
  b.box(0.15, 0.68, 0.1, 0.21, 0.79, 0.21, T.g2Pouch)
  b.box(-0.21, 0.68, 0.12, -0.15, 0.78, 0.22, T.g2Pouch)
  b.box(-0.15, 0.80, -0.2, 0.15, 0.98, 0.2, T.g2Under)                         -- живот
  b.box(-0.2, 0.96, -0.27, 0.22, 1.30, 0.27, T.g2Armor)                        -- кираса
  b.box(0.22, 1.0, -0.2, 0.25, 1.27, 0.2, { side = T.g2Chest, top = T.g2Armor, bottom = T.g2Armor })   -- грудная плита «Г-7»
  b.box(-0.31, 1.0, -0.22, -0.2, 1.32, 0.2, T.g2Metal)                         -- ранец
  b.box(-0.29, 1.32, 0.1, -0.26, 1.56, 0.13, T.g2MetalLight)                   -- антенна
  b.box(-0.295, 1.56, 0.095, -0.255, 1.6, 0.135, T.g2Red, 2.2)
  b.box(-0.1, 1.30, -0.1, 0.1, 1.37, 0.1, T.g2Under)                           -- шея
  b.box(-0.15, 1.36, -0.15, 0.17, 1.62, 0.15, T.g2Armor)                       -- шлем
  b.box(0.17, 1.45, -0.13, 0.2, 1.53, 0.13, T.g2Visor, 1.9)                    -- забрало
  b.box(0.12, 1.36, -0.08, 0.21, 1.44, 0.08, T.g2Metal)                        -- дыхательная маска
  b.box(-0.12, 1.62, -0.03, 0.13, 1.66, 0.03, T.g2MetalLight)                  -- гребень шлема
  b.box(-0.16, 1.22, 0.27, 0.16, 1.37, 0.41, T.g2Armor)                        -- правый наплечник
  b.box(-0.17, 1.2, 0.27, 0.17, 1.23, 0.42, T.g2MetalLight)
  -- правая рука: броня, перчатка, когти-лезвия
  b.box(-0.07, 1.0, 0.29, 0.07, 1.23, 0.39, T.g2Under)
  b.box(0.0, 0.98, 0.29, 0.30, 1.10, 0.39, T.g2Armor)
  b.box(0.30, 0.97, 0.3, 0.37, 1.09, 0.38, T.g2Boot)
  b.box(0.37, 1.05, 0.31, 0.55, 1.07, 0.32, T.g2MetalLight)
  b.box(0.37, 1.02, 0.335, 0.58, 1.04, 0.345, T.g2MetalLight)
  b.box(0.37, 0.99, 0.36, 0.55, 1.01, 0.37, T.g2MetalLight)
  -- левая сторона: плоть разлома рвёт наплечник, рука стала костяной клешнёй
  b.box(-0.22, 1.12, -0.42, 0.12, 1.36, -0.24, T.g2Flesh)
  b.box(-0.18, 1.34, -0.36, -0.06, 1.46, -0.26, T.g2Flesh)
  b.box(-0.05, 1.36, -0.33, -0.02, 1.52, -0.3, T.g2Bone)
  b.box(-0.14, 1.44, -0.32, -0.11, 1.56, -0.29, T.g2Bone)
  b.box(0.0, 0.94, -0.44, 0.30, 1.13, -0.28, T.g2Flesh)
  b.box(0.12, 1.13, -0.38, 0.22, 1.135, -0.33, T.g2Glow, 2.2)
  b.box(0.30, 0.99, -0.43, 0.54, 1.04, -0.38, T.g2Bone)
  b.box(0.30, 1.06, -0.35, 0.50, 1.10, -0.30, T.g2Bone)
  b.box(0.12, 1.24, -0.425, 0.18, 1.3, -0.42, T.g2Glow, 2.2)
end)
A.mesh.guardLeg = model(function(b)                                         -- нога: ноль — тазобедренный сустав
  b.box(-0.08, -0.36, -0.08, 0.08, 0.02, 0.08, T.g2Under)
  b.box(0.08, -0.32, -0.07, 0.1, -0.06, 0.07, T.g2Armor)
  b.box(0.03, -0.45, -0.09, 0.12, -0.32, 0.09, T.g2Armor)
  b.box(-0.07, -0.66, -0.07, 0.08, -0.36, 0.07, T.g2Armor)
  b.box(-0.09, -0.72, -0.09, 0.18, -0.62, 0.09, T.g2Boot)
end)

-- Боевой робот: жёлтый промышленный корпус «07», пушка на правой руке, клешня слева, реактор в ранце.
A.mesh.gunnerBody = model(function(b)
  b.box(-0.2, 0.80, -0.26, 0.2, 0.95, 0.26, T.g2Metal)                         -- таз
  b.box(-0.30, 0.94, -0.36, 0.28, 1.56, 0.36, T.r2Hull)                        -- корпус
  b.box(0.28, 1.02, -0.26, 0.32, 1.46, 0.26, { side = T.r2Chest, top = T.r2Hull, bottom = T.r2Hull })
  b.box(0.32, 1.08, -0.2, 0.33, 1.2, -0.04, T.r2Vent)
  b.box(0.32, 1.08, 0.04, 0.33, 1.2, 0.2, T.r2Vent)
  b.box(-0.2, 1.46, 0.34, 0.2, 1.63, 0.53, T.r2Hazard)                         -- наплечники
  b.box(-0.2, 1.46, -0.53, 0.2, 1.63, -0.34, T.r2Hazard)
  b.box(-0.12, 1.56, -0.16, 0.2, 1.76, 0.16, T.r2Hull)                         -- голова-датчик
  b.box(0.2, 1.63, -0.13, 0.23, 1.69, 0.13, T.r2Lens, 2.2)
  b.box(-0.08, 1.76, 0.08, -0.05, 1.96, 0.11, T.g2MetalLight)
  b.box(-0.48, 1.0, -0.26, -0.30, 1.5, 0.26, T.g2Metal)                        -- реактор
  b.box(-0.49, 1.12, -0.12, -0.48, 1.38, 0.12, T.r2Core, 2.2)
  b.box(-0.44, 1.5, 0.13, -0.36, 1.7, 0.21, T.g2Metal)
  b.box(-0.44, 1.5, -0.21, -0.36, 1.7, -0.13, T.g2Metal)
  -- правая рука — плазменная пушка
  b.box(-0.12, 1.18, 0.36, 0.14, 1.44, 0.56, T.r2Hull)
  b.box(0.0, 1.0, 0.38, 0.62, 1.24, 0.6, T.g2Metal)
  b.box(0.62, 1.06, 0.43, 0.82, 1.18, 0.55, T.w2Blued)
  b.box(0.82, 1.08, 0.45, 0.84, 1.16, 0.53, T.r2Core, 2.2)
  for i = 0, 2 do b.box(0.1 + i * 0.14, 1.24, 0.4, 0.15 + i * 0.14, 1.33, 0.58, T.g2MetalLight) end
  b.box(0.15, 1.08, 0.6, 0.5, 1.16, 0.612, T.g2Glow, 2.0)
  -- левая рука — клешня на гидравлике
  b.box(-0.1, 1.18, -0.56, 0.14, 1.44, -0.36, T.r2Hull)
  b.box(0.0, 0.98, -0.56, 0.34, 1.18, -0.38, T.r2Hull)
  b.box(-0.02, 1.18, -0.58, 0.26, 1.22, -0.54, T.g2MetalLight)
  b.box(0.34, 1.02, -0.56, 0.52, 1.07, -0.5, T.g2Metal)
  b.box(0.34, 1.11, -0.44, 0.52, 1.16, -0.38, T.g2Metal)
end)
A.mesh.gunnerLeg = model(function(b)
  b.box(-0.1, -0.12, -0.12, 0.1, 0.06, 0.12, T.g2Metal)
  b.box(-0.12, -0.46, -0.12, 0.12, -0.1, 0.12, T.r2Hull)
  b.box(0.0, -0.55, -0.13, 0.16, -0.4, 0.13, T.r2Hazard)
  b.box(-0.11, -0.8, -0.11, 0.11, -0.46, 0.11, T.r2Hull)
  b.box(-0.16, -0.76, -0.04, -0.11, -0.36, 0.04, T.g2MetalLight)
  b.box(-0.16, -0.86, -0.15, 0.26, -0.78, 0.15, T.g2Metal)
  b.box(0.26, -0.86, -0.12, 0.33, -0.81, 0.12, T.r2Hull)
end)

-- Око: шар из трёх «крестом» коробок (так он круглее), радужка со зрачком-щелью, веки, наросты и шипы.
A.mesh.eye = model(function(b)
  b.box(-0.32, -0.32, -0.32, 0.32, 0.32, 0.32, T.e2Ball)
  b.box(-0.38, -0.22, -0.22, 0.38, 0.22, 0.22, T.e2Ball)
  b.box(-0.22, -0.38, -0.22, 0.22, 0.38, 0.22, T.e2Ball)
  b.box(-0.22, -0.22, -0.38, 0.22, 0.22, 0.38, T.e2Ball)
  b.box(0.38, -0.19, -0.19, 0.4, 0.19, 0.19, T.e2Iris, 1.4)
  b.box(0.16, 0.2, -0.3, 0.43, 0.3, 0.3, T.e2Flesh)                          -- верхнее веко
  b.box(0.16, -0.3, -0.3, 0.43, -0.21, 0.3, T.e2Flesh)                       -- нижнее
  b.box(-0.5, -0.2, -0.2, -0.32, 0.2, 0.2, T.e2Flesh)                         -- нарост сзади
  b.box(-0.6, -0.1, -0.1, -0.5, 0.1, 0.1, T.e2Flesh)
  b.box(-0.04, 0.38, -0.04, 0.04, 0.6, 0.04, T.g2Bone)                        -- шипы
  b.box(-0.22, 0.32, 0.12, -0.16, 0.5, 0.18, T.g2Bone)
  b.box(-0.22, 0.32, -0.18, -0.16, 0.5, -0.12, T.g2Bone)
  b.box(-0.02, 0.22, 0.36, 0.04, 0.26, 0.4, T.g2Glow, 2.2)                    -- светящиеся точки
  b.box(-0.1, -0.12, -0.4, -0.04, -0.08, -0.36, T.g2Glow, 2.2)
end)
A.mesh.tentacle = model(function(b)                                         -- щупальце сужается книзу, с присосками
  b.box(-0.055, -0.18, -0.055, 0.055, 0, 0.055, T.e2Flesh)
  b.box(-0.045, -0.36, -0.045, 0.045, -0.18, 0.045, T.e2Flesh)
  b.box(-0.035, -0.52, -0.035, 0.035, -0.36, 0.035, T.e2Flesh)
  b.box(-0.022, -0.66, -0.022, 0.022, -0.52, 0.022, T.e2Flesh)
  b.box(0.045, -0.3, -0.025, 0.055, -0.25, 0.025, T.e2Sucker)
  b.box(0.035, -0.46, -0.02, 0.045, -0.42, 0.02, T.e2Sucker)
end)

-- Дробовик (в руках: ноль — у рукояти, ствол вперёд по +x): ствольная коробка, ствол и магазин, цевьё с насечкой,
-- приклад с затыльником, патроны на боку, мушка.
A.mesh.shotgun = model(function(b)
  b.box(-0.08, -0.04, -0.045, 0.14, 0.05, 0.045, T.w2Blued)                   -- ствольная коробка
  b.box(0.0, 0.0, 0.045, 0.08, 0.03, 0.048, T.g2Metal)                         -- окно выброса
  b.box(0.14, 0.012, -0.022, 0.62, 0.046, 0.022, T.w2Blued)                   -- ствол
  b.box(0.14, -0.035, -0.02, 0.52, 0.006, 0.02, T.g2Metal)                    -- трубчатый магазин
  b.box(0.24, -0.058, -0.036, 0.42, 0.012, 0.036, T.w2Pump)                   -- цевьё
  b.box(0.6, 0.008, -0.026, 0.63, 0.05, 0.026, T.g2MetalLight)                -- дульное кольцо
  b.box(0.6, 0.05, -0.006, 0.615, 0.062, 0.006, T.w2Sight, 2.0)               -- мушка
  b.box(-0.2, -0.085, -0.032, -0.08, 0.025, 0.032, T.w2Wood)                  -- шейка приклада
  b.box(-0.38, -0.12, -0.036, -0.2, 0.012, 0.036, T.w2Wood)                   -- приклад
  b.box(-0.405, -0.13, -0.038, -0.38, 0.02, 0.038, T.w2Rubber)                -- затыльник
  b.box(-0.06, -0.09, -0.012, 0.04, -0.04, 0.012, T.g2Metal)                  -- спусковая скоба
  b.box(-0.005, -0.08, -0.004, 0.008, -0.045, 0.004, T.g2MetalLight)
  b.box(-0.06, -0.025, 0.045, 0.09, 0.035, 0.058, T.w2Shells)                 -- патроны на боку
  b.box(0.44, -0.075, -0.006, 0.46, -0.058, 0.006, T.g2MetalLight)            -- антабка
end)


-- ======================= оружие =======================
-- Бластер: пистолет охраны станции, голубой излучатель и индикатор заряда.
A.mesh.blaster = model(function(b)
  b.box(-0.05, -0.03, -0.034, 0.2, 0.05, 0.034, T.g2Metal)                    -- корпус
  b.box(-0.04, 0.05, -0.028, 0.18, 0.075, 0.028, T.w2Blued)                   -- верхняя крышка
  b.box(0.2, -0.002, -0.022, 0.29, 0.044, 0.022, T.g2MetalLight)              -- излучатель
  for i = 0, 2 do b.box(0.215 + i * 0.024, -0.008, -0.026, 0.225 + i * 0.024, 0.05, 0.026, T.w2Coil, 1.8) end
  b.box(0.29, 0.004, -0.016, 0.3, 0.038, 0.016, T.w2Coil, 2.2)
  b.box(-0.075, -0.15, -0.027, 0.02, -0.03, 0.027, T.w2Rubber)                -- рукоять
  b.box(0.0, -0.08, -0.008, 0.07, -0.03, 0.008, T.g2Metal)                    -- скоба
  b.box(0.02, 0.0, 0.034, 0.14, 0.03, 0.038, T.w2Glass, 1.6)                  -- индикатор заряда
  b.box(-0.04, 0.075, -0.012, -0.02, 0.09, 0.012, T.g2MetalLight)             -- целик
  b.box(0.16, 0.075, -0.004, 0.175, 0.09, 0.004, T.w2Sight, 2.0)              -- мушка
end)
-- Плазмомёт: тяжёлый, кольца-ускорители вокруг ствола, батарея на боку, ручка для переноски.
A.mesh.plasmagun = model(function(b)
  b.box(-0.2, -0.08, -0.075, 0.3, 0.09, 0.075, T.w2Blued)                     -- корпус
  b.box(-0.16, 0.09, -0.06, 0.14, 0.11, 0.06, T.g2Metal)
  b.box(-0.1, 0.11, -0.012, -0.08, 0.17, 0.012, T.g2Metal)                    -- ручка для переноски
  b.box(0.08, 0.11, -0.012, 0.1, 0.17, 0.012, T.g2Metal)
  b.box(-0.1, 0.17, -0.014, 0.1, 0.19, 0.014, T.g2Metal)
  b.box(0.3, -0.04, -0.04, 0.6, 0.04, 0.04, T.g2MetalLight)                   -- ствол
  for i = 0, 2 do b.box(0.33 + i * 0.08, -0.065, -0.065, 0.37 + i * 0.08, 0.065, 0.065, T.w2Coil, 1.9) end
  b.box(0.6, -0.055, -0.055, 0.63, 0.055, 0.055, T.g2Metal)                   -- дульный срез
  b.box(0.63, -0.03, -0.03, 0.635, 0.03, 0.03, T.w2Coil, 2.4)
  b.box(-0.12, -0.06, 0.075, 0.1, 0.06, 0.11, T.w2Glass, 1.7)                 -- батарея
  b.box(-0.13, -0.065, 0.074, -0.12, 0.065, 0.112, T.g2MetalLight)
  b.box(0.1, -0.065, 0.074, 0.11, 0.065, 0.112, T.g2MetalLight)
  b.box(0.12, -0.075, -0.08, 0.28, 0.06, -0.075, T.r2Vent)                    -- решётка охлаждения
  b.box(-0.06, -0.2, -0.03, 0.04, -0.08, 0.03, T.w2Rubber)                    -- рукоять
  b.box(-0.32, -0.1, -0.05, -0.2, 0.06, 0.05, T.g2Metal)                      -- упор
  b.box(-0.335, -0.11, -0.055, -0.32, 0.07, 0.055, T.w2Rubber)
end)

-- ======================= предметы =======================
A.mesh.medkit = model(function(b)
  b.box(-0.24, 0, -0.18, 0.24, 0.26, 0.18, { side = T.i2Med, top = T.i2Med, bottom = T.g2Metal })
  b.box(-0.245, 0.11, -0.185, 0.245, 0.14, 0.185, T.g2MetalLight)             -- стык крышки
  b.box(-0.06, 0.26, -0.03, -0.04, 0.32, 0.03, T.g2Metal)                     -- ручка
  b.box(0.04, 0.26, -0.03, 0.06, 0.32, 0.03, T.g2Metal)
  b.box(-0.06, 0.32, -0.03, 0.06, 0.34, 0.03, T.g2Metal)
  b.box(0.245, 0.1, -0.12, 0.255, 0.16, -0.08, T.g2MetalLight)                -- защёлки
  b.box(0.245, 0.1, 0.08, 0.255, 0.16, 0.12, T.g2MetalLight)
end)
A.mesh.armor = model(function(b)                                            -- бронежилет охраны на подставке
  b.box(-0.03, 0.1, -0.24, 0.07, 0.62, 0.24, { side = T.g2Chest, top = T.g2Armor, bottom = T.g2Armor })
  b.box(-0.13, 0.1, -0.24, -0.03, 0.6, 0.24, T.g2Armor)
  b.box(-0.12, 0.62, -0.24, 0.06, 0.68, -0.12, T.g2Belt)                      -- лямки
  b.box(-0.12, 0.62, 0.12, 0.06, 0.68, 0.24, T.g2Belt)
  b.box(0.07, 0.14, -0.2, 0.11, 0.26, -0.06, T.g2Pouch)
  b.box(0.07, 0.14, 0.06, 0.11, 0.26, 0.2, T.g2Pouch)
  b.box(-0.15, 0, -0.2, 0.09, 0.1, 0.2, T.g2Metal)
end)
A.mesh.shells = model(function(b)                                           -- коробка патронов, открытая
  b.box(-0.18, 0, -0.13, 0.18, 0.2, 0.13, { side = T.i2Card, top = T.i2ShellTop, bottom = T.i2Card })
  b.box(-0.2, 0.2, -0.13, -0.18, 0.34, 0.13, T.i2Card)                        -- откинутая крышка
end)
A.mesh.cells = model(function(b)                                            -- энергоячейка
  b.box(-0.1, 0.04, -0.1, 0.1, 0.36, 0.1, T.w2Glass, 1.7)
  b.box(-0.13, 0.08, -0.07, 0.13, 0.32, 0.07, T.w2Glass, 1.7)
  b.box(-0.07, 0.08, -0.13, 0.07, 0.32, 0.13, T.w2Glass, 1.7)
  b.box(-0.13, 0, -0.13, 0.13, 0.06, 0.13, T.g2Metal)
  b.box(-0.13, 0.34, -0.13, 0.13, 0.4, 0.13, T.g2Metal)
  b.box(-0.04, 0.4, -0.04, 0.04, 0.45, 0.04, T.g2MetalLight)                  -- контакт
end)
for _, k in ipairs({ { "keyRed", T.k2Red }, { "keyYellow", T.k2Yellow }, { "keyBlue", T.k2Blue } }) do
  A.mesh[k[1]] = model(function(b)                                           -- ключ-карта над голограммой
    b.box(-0.008, 0.22, -0.11, 0.008, 0.38, 0.11, k[2])
    b.box(-0.1, 0, -0.1, 0.1, 0.03, 0.1, T.g2Metal)
    b.box(-0.06, 0.03, -0.06, 0.06, 0.04, 0.06, T.k2Holo, 2.0)
  end)
end

-- ======================= новые враги =======================
-- Заражённый учёный: ссутулился, халат в пятнах, голову разорвал нарост, левая рука — огромная клешня почти до земли.
A.mesh.scientistBody = model(function(b)
  b.box(-0.14, 0.66, -0.2, 0.14, 1.1, 0.2, T.s2Coat)                          -- халат
  b.box(-0.18, 0.4, -0.2, -0.13, 0.8, 0.2, T.s2Coat)                          -- полы сзади
  b.box(-0.06, 1.05, -0.21, 0.22, 1.34, 0.21, T.s2Coat)                       -- плечи подались вперёд
  b.box(0.22, 1.12, 0.06, 0.226, 1.2, 0.13, T.s2Badge)                        -- пропуск
  b.box(0.1, 1.28, -0.07, 0.2, 1.36, 0.07, T.s2Skin)                          -- шея
  b.box(0.14, 1.3, -0.12, 0.38, 1.52, 0.12, T.s2Skin)                         -- голова, низко
  b.box(0.38, 1.4, -0.09, 0.39, 1.44, -0.03, T.g2Glow, 2.2)                   -- глаза
  b.box(0.38, 1.4, 0.03, 0.39, 1.44, 0.09, T.g2Glow, 2.2)
  b.box(0.12, 1.48, -0.1, 0.34, 1.62, 0.06, T.g2Flesh)                        -- нарост
  b.box(0.18, 1.6, -0.06, 0.28, 1.7, 0.02, T.g2Flesh)
  b.box(0.2, 1.68, -0.04, 0.23, 1.8, -0.01, T.g2Bone)
  b.box(0.0, 0.82, 0.21, 0.1, 1.28, 0.29, T.s2Coat)                           -- правая рука висит
  b.box(0.01, 0.72, 0.22, 0.09, 0.82, 0.28, T.s2Skin)
  b.box(-0.16, 0.98, -0.44, 0.16, 1.4, -0.2, T.g2Flesh)                       -- левое плечо — масса
  b.box(-0.1, 1.38, -0.4, 0.0, 1.5, -0.3, T.g2Bone)
  b.box(-0.02, 0.5, -0.5, 0.3, 0.98, -0.24, T.g2Flesh)                        -- ручища
  b.box(0.3, 0.5, -0.48, 0.5, 0.56, -0.43, T.g2Bone)                          -- когти
  b.box(0.3, 0.5, -0.4, 0.54, 0.56, -0.35, T.g2Bone)
  b.box(0.3, 0.5, -0.32, 0.48, 0.56, -0.27, T.g2Bone)
  b.box(0.1, 0.98, -0.47, 0.2, 0.985, -0.3, T.g2Glow, 2.2)
end)
A.mesh.scientistLeg = model(function(b)
  b.box(-0.07, -0.62, -0.07, 0.07, 0.02, 0.07, T.s2Pants)
  b.box(-0.08, -0.7, -0.08, 0.15, -0.62, 0.08, T.g2Boot)
end)

-- Турель охраны: тренога, поворотная голова с двумя стволами, красный датчик, короб с лентой.
A.mesh.turret = model(function(b)
  b.box(-0.3, 0, -0.06, -0.1, 0.05, 0.06, T.g2Metal)                          -- лапы треноги
  b.box(0.06, 0, -0.3, 0.16, 0.05, -0.1, T.g2Metal)
  b.box(0.06, 0, 0.1, 0.16, 0.05, 0.3, T.g2Metal)
  b.box(-0.14, 0.03, -0.14, 0.14, 0.14, 0.14, T.g2Metal)
  b.box(-0.08, 0.14, -0.08, 0.08, 0.52, 0.08, T.g2MetalLight)                 -- стойка
  b.box(-0.22, 0.52, -0.24, 0.22, 0.88, 0.24, T.g2Armor)                      -- голова
  b.box(-0.225, 0.58, -0.245, 0.225, 0.63, 0.245, T.r2Hazard)
  b.box(0.22, 0.74, -0.07, 0.24, 0.82, 0.07, T.r2Lens, 2.2)                   -- датчик
  b.box(0.22, 0.6, 0.07, 0.62, 0.67, 0.14, T.w2Blued)                         -- стволы
  b.box(0.22, 0.6, -0.14, 0.62, 0.67, -0.07, T.w2Blued)
  b.box(0.58, 0.59, 0.06, 0.64, 0.68, 0.15, T.g2Metal)
  b.box(0.58, 0.59, -0.15, 0.64, 0.68, -0.06, T.g2Metal)
  b.box(-0.14, 0.56, 0.24, 0.12, 0.8, 0.36, T.g2Metal)                        -- короб с лентой
  b.box(0.0, 0.62, 0.36, 0.08, 0.7, 0.37, T.w2Shells)
  b.box(-0.18, 0.88, -0.03, -0.15, 1.05, 0.0, T.g2MetalLight)                 -- антенна
end)

-- Ползун: длинная личинка из разлома — сегменты в хитине со светящимися швами, тонкие ноги, жвала.
A.mesh.crawler = model(function(b)
  local segs = { { 0.36, 0.62, 0.2, 0.17 }, { 0.08, 0.36, 0.24, 0.2 }, { -0.2, 0.08, 0.23, 0.19 }, { -0.46, -0.2, 0.2, 0.16 },
                 { -0.68, -0.46, 0.15, 0.12 }, { -0.84, -0.68, 0.1, 0.08 } }
  for i, s in ipairs(segs) do
    local x1, x2, hh, hw = s[1], s[2], s[3], s[4]
    b.box(x1, 0.1, -hw, x2 - 0.01, 0.1 + hh * 2, hw, T.c2Chitin)
    if i <= 4 then                                                             -- ноги парами
      local lx = (x1 + x2) / 2
      b.box(lx - 0.015, 0.17, hw, lx + 0.015, 0.2, hw + 0.12, T.g2Bone)
      b.box(lx - 0.015, 0.0, hw + 0.09, lx + 0.015, 0.2, hw + 0.12, T.g2Bone)
      b.box(lx - 0.015, 0.17, -hw - 0.12, lx + 0.015, 0.2, -hw, T.g2Bone)
      b.box(lx - 0.015, 0.0, -hw - 0.12, lx + 0.015, 0.2, -hw - 0.09, T.g2Bone)
    end
  end
  b.box(0.62, 0.16, -0.13, 0.74, 0.42, 0.13, T.c2Chitin)                      -- голова
  b.box(0.74, 0.32, -0.1, 0.75, 0.37, -0.04, T.g2Glow, 2.3)                   -- глаза
  b.box(0.74, 0.32, 0.04, 0.75, 0.37, 0.1, T.g2Glow, 2.3)
  b.box(0.74, 0.16, -0.12, 0.88, 0.2, -0.08, T.g2Bone)                        -- жвала
  b.box(0.74, 0.16, 0.08, 0.88, 0.2, 0.12, T.g2Bone)
  b.box(0.1, 0.58, -0.02, 0.16, 0.7, 0.02, T.g2Bone)                          -- шипы на спине
  b.box(-0.16, 0.56, -0.02, -0.1, 0.68, 0.02, T.g2Bone)
end)

-- Босс «Сердце разлома»: огромный ком плоти над полом, сквозь трещины — зелёный кристалл; люди пытались
-- удержать его стальными обручами — один лопнул; спереди глаз, вниз уходят толстые щупальца.
A.mesh.boss = model(function(b)
  local cy = 2.1
  b.box(-0.7, cy - 0.7, -0.7, 0.7, cy + 0.7, 0.7, T.g2Flesh)                  -- шар: куб и «крест» из трёх плит
  b.box(-0.95, cy - 0.45, -0.45, 0.95, cy + 0.45, 0.45, T.g2Flesh)
  b.box(-0.45, cy - 0.95, -0.45, 0.45, cy + 0.95, 0.45, T.g2Flesh)
  b.box(-0.45, cy - 0.45, -0.95, 0.45, cy + 0.45, 0.95, T.g2Flesh)
  b.box(-0.85, cy - 0.62, -0.62, 0.85, cy + 0.62, 0.62, T.g2Flesh)          -- промежуточные — сглаживают углы
  b.box(-0.62, cy - 0.85, -0.62, 0.62, cy + 0.85, 0.62, T.g2Flesh)
  b.box(-0.62, cy - 0.62, -0.85, 0.62, cy + 0.62, 0.85, T.g2Flesh)
  b.box(0.95, cy - 0.3, -0.3, 0.98, cy + 0.3, 0.3, T.e2Iris, 1.5)            -- глаз
  b.box(0.8, cy + 0.3, -0.45, 1.05, cy + 0.42, 0.45, T.g2Flesh)              -- веки
  b.box(0.8, cy - 0.42, -0.45, 1.05, cy - 0.3, 0.45, T.g2Flesh)
  b.box(-0.2, cy + 0.5, 0.8, 0.3, cy + 0.6, 0.96, T.b2Crystal, 2.4)          -- трещины с кристаллом
  b.box(-0.5, cy - 0.2, 0.9, -0.4, cy + 0.4, 0.96, T.b2Crystal, 2.4)
  b.box(-0.4, cy + 0.95, -0.3, 0.2, cy + 0.98, -0.1, T.b2Crystal, 2.4)
  b.box(-0.96, cy - 0.1, -0.4, -0.93, cy + 0.3, 0.1, T.b2Crystal, 2.4)
  b.box(-0.3, cy + 0.9, 0.1, -0.15, cy + 1.25, 0.25, T.b2Crystal, 2.4)        -- кристаллы наружу
  b.box(0.1, cy + 0.85, -0.5, 0.22, cy + 1.15, -0.38, T.b2Crystal, 2.4)
  local R, H = 1.08, 0.07                                                     -- обруч-рама вокруг пояса
  b.box(-R, cy - H, R, R, cy + H, R + 0.1, T.r2Hazard)
  b.box(-R, cy - H, -R - 0.1, R, cy + H, -R, T.r2Hazard)
  b.box(R, cy - H, -R - 0.1, R + 0.1, cy + H, R + 0.1, T.r2Hazard)
  b.box(-R - 0.1, cy - H, -R - 0.1, -R, cy + H, -0.2, T.r2Hazard)              -- эта сторона лопнула
  b.box(-R - 0.1, cy - H - 0.2, 0.25, -R, cy + H - 0.2, R + 0.1, T.r2Hazard)  -- обломок съехал вниз
  for _, z in ipairs({ -0.55, 0.55 }) do                                       -- крепления к телу
    b.box(0.9, cy - 0.04, z - 0.04, R, cy + 0.04, z + 0.04, T.g2Metal)
  end
  b.box(-0.5, cy + 0.6, -0.9, 0.5, cy + 0.7, -0.84, T.g2Metal)               -- скоба
  for i, p in ipairs({ { 0.4, 0.4 }, { -0.4, 0.5 }, { 0.3, -0.5 }, { -0.5, -0.3 }, { 0.0, 0.0 } }) do   -- щупальца
    local x, z = p[1], p[2]
    local ox = (i % 2 == 0) and 0.08 or -0.08                                 -- щупальца чуть изогнуты
    b.box(x - 0.15, cy - 1.25, z - 0.15, x + 0.15, cy - 0.6, z + 0.15, T.e2Flesh)
    b.box(x - 0.11 + ox, 0.55, z - 0.11, x + 0.11 + ox, cy - 1.25, z + 0.11, T.e2Flesh)
    b.box(x - 0.08 + ox * 2, 0.08, z - 0.08, x + 0.08 + ox * 2, 0.55, z + 0.08, T.e2Flesh)
    b.box(x - 0.13 + ox * 2, 0, z - 0.13, x + 0.13 + ox * 2, 0.1, z + 0.13, T.g2Flesh)
  end
end)


-- ======================= станция: светильник, ящик, двери, пульт =======================
A.mesh.lampBox = model(function(b)                                       -- светильник на потолке
  b.box(-0.4, -0.12, -0.4, 0.4, 0, 0.4, { side = T.g2Metal, bottom = tx.lamp, top = T.g2Metal }, 1)
end)
A.mesh.crate = model(function(b)
  b.box(-0.5, 0, -0.5, 0.5, 1, 0.5, tx.crate)
end)
-- Пульт (стойка с экраном): вперёд — +x. Зелёный экран — работает, красный — отключён (рисуется подсветкой).
A.mesh.console = model(function(b)
  b.box(-0.25, 0, -0.35, 0.15, 0.9, 0.35, T.g2Metal)
  b.box(0.15, 0.55, -0.32, 0.3, 0.95, 0.32, { side = tx.panel, top = T.g2Metal, bottom = T.g2Metal })
  b.box(-0.27, 0.9, -0.37, 0.17, 0.96, 0.37, T.r2Hazard)
  b.box(-0.05, 0.96, -0.02, -0.02, 1.25, 0.01, T.g2MetalLight)
  b.box(-0.06, 1.25, -0.03, -0.01, 1.3, 0.02, T.g2Red, 2.2)
end)
-- Двери под ключ-карту строит уровень (по размеру проёма); здесь — их текстуры.
for _, k in ipairs({ { "doorRed", 220, 50, 50 }, { "doorYellow", 230, 190, 50 }, { "doorBlue", 60, 120, 230 }, { "doorGrey", 140, 146, 156 } }) do
  local r, g, bb = k[2], k[3], k[4]
  texture(k[1], 32, 32, function(x, y)
    if y >= 13 and y <= 18 then                                             -- цветная полоса доступа
      return (x % 8 < 6) and rgb(r, g, bb) or rgb(r * 0.4, g * 0.4, bb * 0.4)
    end
    if x == 15 or x == 16 then return rgb(30, 32, 36) end                   -- шов створок
    if y <= 1 or y >= 30 then return rgb(40, 42, 48) end
    local n = noise(4)
    local base = ((x + floor(y / 2)) % 16 == 0) and 70 or 96
    return rgb(base + n, base + 4 + n, base + 14 + n)
  end)
end

-- ======================= текстуры уровней 2–5 =======================
texture("tile", 32, 32, function(x, y)          -- лаборатория: белая плитка
  if x % 8 == 0 or y % 8 == 0 then return rgb(150, 156, 160) end
  local n = noise(4)
  return rgb(212 + n, 218 + n, 220 + n)
end)
texture("lino", 32, 32, function(x, y)          -- пол лаборатории: шашечка
  local c = (floor(x / 8) + floor(y / 8)) % 2 == 0
  local n = noise(4)
  if c then return rgb(70 + n, 112 + n, 120 + n) end
  return rgb(188 + n, 196 + n, 196 + n)
end)
texture("labceil", 32, 32, function(x, y)       -- подвесной потолок
  if x % 16 == 0 or y % 16 == 0 then return rgb(110, 114, 118) end
  local n = noise(3)
  return rgb(176 + n, 180 + n, 182 + n)
end)
texture("pipes", 32, 32, function(x, y)         -- стена реактора: трубы
  local p = x % 8
  if p == 0 or p == 7 then return rgb(26, 28, 30) end
  if y % 16 == 3 then return rgb(196, 160, 60) end                         -- хомуты
  local k = 1 - abs(p - 3.5) / 5
  local n = noise(4)
  return rgb((96 + n) * k + 30, (102 + n) * k + 30, (110 + n) * k + 30)
end)
texture("core", 32, 32, function(x, y)          -- светящийся реактор (зелень разлома)
  local b = sin(x * 0.5) * cos(y * 0.4) * 30
  if x % 16 == 0 or y % 16 == 0 then return rgb(40, 60, 46) end
  return rgb(60 + b, 220 + b, 110 + b)
end)
texture("beam", 32, 32, function(x, y)          -- шахта: деревянная крепь
  local grain = sin(y * 0.6 + sin(x * 0.2) * 1.5)
  if x % 16 == 0 then return rgb(40, 26, 14) end
  local n = noise(5)
  return rgb(110 + n + grain * 12, 72 + n + grain * 8, 38 + n)
end)
texture("dirt", 32, 32, function(x, y)          -- шахта: утоптанная порода
  local n = noise(12)
  if (x * 13 + y * 7) % 37 == 0 then n = n + 30 end
  return rgb(78 + n, 64 + n, 52 + n)
end)
texture("rail", 32, 32, function(x, y)          -- рельсы на шпалах
  if x == 9 or x == 10 or x == 21 or x == 22 then return rgb(150, 150, 156) end
  if y % 8 < 3 then return rgb(84, 56, 30) end
  local n = noise(10)
  return rgb(70 + n, 58 + n, 48 + n)
end)
texture("fleshWall", 32, 32, function(x, y)     -- пещера: камень, проросший плотью и светящимися жилами
  local v = abs(sin(x * 0.21 + sin(y * 0.17) * 2.6))
  if v < 0.06 then return rgb(120, 255, 140) end
  if v < 0.2 then return rgb(110, 40, 56) end
  local n = noise(8)
  return rgb(60 + n, 48 + n, 52 + n)
end)
texture("crystal", 32, 32, function(x, y)       -- кристалл разлома (светится сам)
  local k = ((x + y) % 10 < 3) and 1.25 or (((x - y) % 12 == 0) and 0.8 or 1)
  return rgb(80 * k, 240 * k, 140 * k)
end)

return A
