-- Черновик новых моделей «Разлома» (макет): охранник, боевой робот, око, дробовик.
-- Получает gpu, lowVram, A (таблица art.lua) и дописывает текстуры и сетки с именами *2.
local gpu, lowVram, A = ...
local floor, max, min, abs, sqrt = math.floor, math.max, math.min, math.abs, math.sqrt
local rnd = math.random
math.randomseed(2077)
local rgb = A.rgb
local TR = -1
local T = {}

local function texture(name, w, h, f)
  local t = {}
  for y = 0, h - 1 do for x = 0, w - 1 do t[y * w + x + 1] = f(x, y) end end
  T[name] = gpu.newTexture(w, h, t)
end
local function noise(a) return rnd(-a, a) end
local function mix(c, k)  -- {r,g,b} * k → цвет
  return rgb(c[1] * k, c[2] * k, c[3] * k)
end

-- Крошечный трафаретный шрифт 3×5 (для надписей на броне)
local GLYPH = {
  ["Г"] = { "111", "100", "100", "100", "100" }, ["-"] = { "000", "000", "111", "000", "000" },
  ["7"] = { "111", "001", "010", "010", "010" }, ["0"] = { "111", "101", "101", "101", "111" },
}
local function stencil(text, x0, y0, x, y)
  local cx = x - x0
  local cy = y - y0
  if cy < 0 or cy > 4 or cx < 0 then return false end
  local i = floor(cx / 4) + 1
  local ch = text:sub(i * 2 - 1, i * 2)
  if #text < i * 2 then return false end
  local g = GLYPH[text:sub(i * 2 - 1, i * 2)] or GLYPH[text:sub(i, i)]
  return false
end
-- проще: надпись — список символов
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

-- ======================= сетки =======================
local function model(fn)
  local b = A.builder()
  fn(b)
  return b.mesh()
end

-- Охранник: синяя броня охраны, шлем с зелёным забралом; разлом прорастает через левое плечо и руку.
A.mesh.guard2Body = model(function(b)
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
A.mesh.guard2Leg = model(function(b)                                         -- нога: ноль — тазобедренный сустав
  b.box(-0.08, -0.36, -0.08, 0.08, 0.02, 0.08, T.g2Under)
  b.box(0.08, -0.32, -0.07, 0.1, -0.06, 0.07, T.g2Armor)
  b.box(0.03, -0.45, -0.09, 0.12, -0.32, 0.09, T.g2Armor)
  b.box(-0.07, -0.66, -0.07, 0.08, -0.36, 0.07, T.g2Armor)
  b.box(-0.09, -0.72, -0.09, 0.18, -0.62, 0.09, T.g2Boot)
end)

-- Боевой робот: жёлтый промышленный корпус «07», пушка на правой руке, клешня слева, реактор в ранце.
A.mesh.gunner2Body = model(function(b)
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
A.mesh.gunner2Leg = model(function(b)
  b.box(-0.1, -0.12, -0.12, 0.1, 0.06, 0.12, T.g2Metal)
  b.box(-0.12, -0.46, -0.12, 0.12, -0.1, 0.12, T.r2Hull)
  b.box(0.0, -0.55, -0.13, 0.16, -0.4, 0.13, T.r2Hazard)
  b.box(-0.11, -0.8, -0.11, 0.11, -0.46, 0.11, T.r2Hull)
  b.box(-0.16, -0.76, -0.04, -0.11, -0.36, 0.04, T.g2MetalLight)
  b.box(-0.16, -0.86, -0.15, 0.26, -0.78, 0.15, T.g2Metal)
  b.box(0.26, -0.86, -0.12, 0.33, -0.81, 0.12, T.r2Hull)
end)

-- Око: шар из трёх «крестом» коробок (так он круглее), радужка со зрачком-щелью, веки, наросты и шипы.
A.mesh.eye2 = model(function(b)
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
A.mesh.tentacle2 = model(function(b)                                         -- щупальце сужается книзу, с присосками
  b.box(-0.055, -0.18, -0.055, 0.055, 0, 0.055, T.e2Flesh)
  b.box(-0.045, -0.36, -0.045, 0.045, -0.18, 0.045, T.e2Flesh)
  b.box(-0.035, -0.52, -0.035, 0.035, -0.36, 0.035, T.e2Flesh)
  b.box(-0.022, -0.66, -0.022, 0.022, -0.52, 0.022, T.e2Flesh)
  b.box(0.045, -0.3, -0.025, 0.055, -0.25, 0.025, T.e2Sucker)
  b.box(0.035, -0.46, -0.02, 0.045, -0.42, 0.02, T.e2Sucker)
end)

-- Дробовик (в руках: ноль — у рукояти, ствол вперёд по +x): ствольная коробка, ствол и магазин, цевьё с насечкой,
-- приклад с затыльником, патроны на боку, мушка.
A.mesh.shotgun2 = model(function(b)
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
