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

-- ======================= оружие =======================
-- Бластер: пистолет охраны станции, голубой излучатель и индикатор заряда.
A.mesh.blaster2 = model(function(b)
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
A.mesh.plasmagun2 = model(function(b)
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
A.mesh.medkit2 = model(function(b)
  b.box(-0.24, 0, -0.18, 0.24, 0.26, 0.18, { side = T.i2Med, top = T.i2Med, bottom = T.g2Metal })
  b.box(-0.245, 0.11, -0.185, 0.245, 0.14, 0.185, T.g2MetalLight)             -- стык крышки
  b.box(-0.06, 0.26, -0.03, -0.04, 0.32, 0.03, T.g2Metal)                     -- ручка
  b.box(0.04, 0.26, -0.03, 0.06, 0.32, 0.03, T.g2Metal)
  b.box(-0.06, 0.32, -0.03, 0.06, 0.34, 0.03, T.g2Metal)
  b.box(0.245, 0.1, -0.12, 0.255, 0.16, -0.08, T.g2MetalLight)                -- защёлки
  b.box(0.245, 0.1, 0.08, 0.255, 0.16, 0.12, T.g2MetalLight)
end)
A.mesh.armor2 = model(function(b)                                            -- бронежилет охраны на подставке
  b.box(-0.03, 0.1, -0.24, 0.07, 0.62, 0.24, { side = T.g2Chest, top = T.g2Armor, bottom = T.g2Armor })
  b.box(-0.13, 0.1, -0.24, -0.03, 0.6, 0.24, T.g2Armor)
  b.box(-0.12, 0.62, -0.24, 0.06, 0.68, -0.12, T.g2Belt)                      -- лямки
  b.box(-0.12, 0.62, 0.12, 0.06, 0.68, 0.24, T.g2Belt)
  b.box(0.07, 0.14, -0.2, 0.11, 0.26, -0.06, T.g2Pouch)
  b.box(0.07, 0.14, 0.06, 0.11, 0.26, 0.2, T.g2Pouch)
  b.box(-0.15, 0, -0.2, 0.09, 0.1, 0.2, T.g2Metal)
end)
A.mesh.shells2 = model(function(b)                                           -- коробка патронов, открытая
  b.box(-0.18, 0, -0.13, 0.18, 0.2, 0.13, { side = T.i2Card, top = T.i2ShellTop, bottom = T.i2Card })
  b.box(-0.2, 0.2, -0.13, -0.18, 0.34, 0.13, T.i2Card)                        -- откинутая крышка
end)
A.mesh.cells2 = model(function(b)                                            -- энергоячейка
  b.box(-0.1, 0.04, -0.1, 0.1, 0.36, 0.1, T.w2Glass, 1.7)
  b.box(-0.13, 0.08, -0.07, 0.13, 0.32, 0.07, T.w2Glass, 1.7)
  b.box(-0.07, 0.08, -0.13, 0.07, 0.32, 0.13, T.w2Glass, 1.7)
  b.box(-0.13, 0, -0.13, 0.13, 0.06, 0.13, T.g2Metal)
  b.box(-0.13, 0.34, -0.13, 0.13, 0.4, 0.13, T.g2Metal)
  b.box(-0.04, 0.4, -0.04, 0.04, 0.45, 0.04, T.g2MetalLight)                  -- контакт
end)
for _, k in ipairs({ { "keyRed2", T.k2Red }, { "keyYellow2", T.k2Yellow }, { "keyBlue2", T.k2Blue } }) do
  A.mesh[k[1]] = model(function(b)                                           -- ключ-карта над голограммой
    b.box(-0.008, 0.22, -0.11, 0.008, 0.38, 0.11, k[2])
    b.box(-0.1, 0, -0.1, 0.1, 0.03, 0.1, T.g2Metal)
    b.box(-0.06, 0.03, -0.06, 0.06, 0.04, 0.06, T.k2Holo, 2.0)
  end)
end

-- ======================= новые враги =======================
-- Заражённый учёный: ссутулился, халат в пятнах, голову разорвал нарост, левая рука — огромная клешня почти до земли.
A.mesh.scientist2Body = model(function(b)
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
A.mesh.scientist2Leg = model(function(b)
  b.box(-0.07, -0.62, -0.07, 0.07, 0.02, 0.07, T.s2Pants)
  b.box(-0.08, -0.7, -0.08, 0.15, -0.62, 0.08, T.g2Boot)
end)

-- Турель охраны: тренога, поворотная голова с двумя стволами, красный датчик, короб с лентой.
A.mesh.turret2 = model(function(b)
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
A.mesh.crawler2 = model(function(b)
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
A.mesh.boss2 = model(function(b)
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
