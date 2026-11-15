-- Разлом: уровень «Станция над разломом». Карта — клетки 1×1 м, у каждой своя высота пола и потолка,
-- плюс отдельные коробки (мост над кислотой, ящики), под которыми можно пройти.
local floor, max, min, abs, sqrt = math.floor, math.max, math.min, math.abs, math.sqrt
local L = {}

L.STEP = 0.45          -- на такую высоту заходят без прыжка

-- Знаки карты: f — пол, c — потолок, ft/ct/wt — текстуры пола, потолка и стен, side — бок ступени (виден снизу)
local LEGEND = {
  ["."] = { f = 0, c = 4, ft = "concrete", ct = "ceil", wt = "wall", side = "rust" },
  [":"] = { f = 0, c = 5, ft = "concrete", ct = "ceil", wt = "rock", side = "rust" },
  [","] = { f = 0, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "rust" },
  ["="] = { f = 2.8, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "rust" },
  ["a"] = { f = 0.4, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["b"] = { f = 0.8, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["c"] = { f = 1.2, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["d"] = { f = 1.6, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["e"] = { f = 2.0, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["f"] = { f = 2.4, c = 9, ft = "plate", ct = "ceil", wt = "wall", side = "hazard" },
  ["~"] = { f = -0.8, c = 9, ft = "acid", ct = "ceil", wt = "wall", side = "rust", acid = true },
  ["j"] = { f = 0, c = 9, ft = "pad", ct = "ceil", wt = "wall", side = "rust", pad = true },
}

-- 36 × 22 клеток: x — столбец (восток), z — строка (юг)
local MAP = {
  "####################################",
  "#........###====================####",
  "#........###====================####",
  "#........###====================####",
  "#........###====================####",
  "#........###,,,,,,,,,,,,,,,,,,ff####",
  "#...........,,,,,,,,,,,,,,,,,,ee####",
  "#...........,,,,,,,,,,,,,,,,,,dd####",
  "#........###,,,,,,,,,,,,,,,,,,cc####",
  "############,,,,,,,,~~~~~~~~,,bb####",
  "############,,,,,,,,~~~~~~~~,,aa####",
  "############,,,,#,,,~~~~~~~~,,,,####",
  "############,,,,#,,,~~~~~~~~,,,,####",
  "############,,,,,,,,~~~~~~~~,,,,####",
  "############,,,,,,,,~~~~~~~~,,,,####",
  "#:::::::::..,,,,,,,,~~~~~~~~,,,,####",
  "#:::::::::..,,,,,,,,,,,,,,,,,,,,####",
  "#:::::::::##,,,,,,==========,,,,####",
  "#:::::::::##,,,,,,==========,,,,####",
  "#:::::::::##,,j,,,==========,,,,####",
  "#:::::::::##,,,,,,==========,,,,####",
  "####################################",
}
L.W, L.H = #MAP[1], #MAP

-- Коробки: мост над кислотой с балкона на южную площадку, ящики на складе, пульт в начальной комнате
L.brushes = {
  { 22, 2.5, 5, 24, 2.8, 17, top = "grate", side = "rust", bottom = "grate", uvs = 2, noCull = true },
  { 2, 0, 15, 3, 1, 16, tex = "crate" }, { 2, 0, 16, 3, 1, 17, tex = "crate" }, { 2, 1, 15.5, 3, 2, 16.5, tex = "crate" },
  { 6, 0, 19, 7, 1, 20, tex = "crate" }, { 7, 0, 19, 8, 1, 20, tex = "crate" }, { 6.5, 1, 19, 7.5, 2, 20, tex = "crate" },
  { 1, 0, 1, 1.6, 1.3, 3, side = "panel", top = "gun", bottom = "gun" },
  { 30, 0, 17, 31, 1, 18, tex = "crate" },
}

-- Лампы: x, y, z, радиус, сила; lamp — повесить светильник; glow — цвет живого свечения
L.lights = {
  { 4.5, 3.8, 4.5, 8, 1.0, lamp = true },
  { 10.5, 3.8, 6.5, 5, 0.7, lamp = true },
  { 16, 8.8, 8, 12, 0.85, lamp = true }, { 27, 8.8, 8, 12, 0.85, lamp = true },
  { 16, 8.8, 18, 12, 0.85, lamp = true }, { 27, 8.8, 18, 12, 0.8, lamp = true },
  { 22, 5.5, 2.5, 8, 0.55 },
  { 24, 0.4, 12, 7, 0.55 },
  { 5, 4.8, 17.5, 8, 0.75, lamp = true }, { 11, 3.8, 15.5, 5, 0.5, lamp = true },
}
L.AMBIENT = 0.2

-- Кто где: игрок, враги, предметы. y — пол, на котором стоит (у ока — высота полёта).
L.start = { x = 4.5, z = 4.5, yaw = 0 }
L.things = {
  { "guard", 18.5, 0, 7.5 }, { "guard", 14.5, 0, 16.5 },
  { "gunner", 20.5, 2.8, 2.5 }, { "gunner", 29.5, 2.8, 2.5 },
  { "eye", 24, 4.5, 12 }, { "eye", 17, 5.5, 18 },
  { "guard", 5.5, 0, 17.5 }, { "guard", 7.5, 0, 16.5 }, { "gunner", 4.5, 0, 19.5 },
  { "gunner", 25.5, 2.8, 19 },
  { "shotgun", 7.5, 0, 2.5 }, { "plasmagun", 22.5, 2.8, 19.5 },
  { "shells", 10.5, 0, 7 }, { "shells", 2.5, 0, 18.5 }, { "shells", 30.5, 0, 19.5 },
  { "medkit", 7.5, 0, 7.5 }, { "medkit", 13, 0, 20 }, { "medkit", 8.5, 0, 15.5 }, { "medkit", 31, 2.8, 1.5 },
  { "armor", 12.5, 2.8, 1.5 },
  { "cells", 27, 2.8, 20 }, { "cells", 29.5, 0, 6 },
}
L.portal = { x = 17, y = 2.8, z = 2.5 }
L.padPush = { up = 11.5, x = 6, z = 0 }   -- прыжковая площадка: вверх и на восток, на южную площадку

-- ======================= клетки =======================

local cells = {}
for r = 1, L.H do
  local row = MAP[r]
  for c = 1, L.W do
    local e = LEGEND[row:sub(c, c)]
    cells[(r - 1) * L.W + (c - 1)] = e or false
  end
end

--- Клетка с левым верхним углом (cx, cz) или nil — сплошная.
local function cell(cx, cz)
  if cx < 0 or cz < 0 or cx >= L.W or cz >= L.H then return nil end
  return cells[cz * L.W + cx] or nil
end
L.cell = cell
function L.cellAt(x, z) return cell(floor(x), floor(z)) end

for _, b in ipairs(L.brushes) do
  b.x1, b.y1, b.z1, b.x2, b.y2, b.z2 = b[1], b[2], b[3], b[4], b[5], b[6]
end
local brushes = L.brushes

-- ======================= столкновения =======================

--- Упирается ли тело (квадрат 2r по горизонтали, от y до y + h) во что-нибудь.
function L.blocked(x, y, z, r, h)
  local x1, x2 = floor(x - r), floor(x + r - 1e-6)
  local z1, z2 = floor(z - r), floor(z + r - 1e-6)
  local top = y + h
  for cz = z1, z2 do
    for cx = x1, x2 do
      local e = cell(cx, cz)
      if not e or e.f > y + 1e-3 or e.c < top then return true end
    end
  end
  for i = 1, #brushes do
    local b = brushes[i]
    if x + r > b.x1 and x - r < b.x2 and z + r > b.z1 and z - r < b.z2 and top > b.y1 and y + 1e-3 < b.y2 then return true end
  end
  return false
end

--- Высота опоры под телом: самый высокий пол или верх коробки не выше y + ступенька.
function L.ground(x, z, r, y)
  local x1, x2 = floor(x - r), floor(x + r - 1e-6)
  local z1, z2 = floor(z - r), floor(z + r - 1e-6)
  local g = -100
  local lim = y + L.STEP + 1e-3
  for cz = z1, z2 do
    for cx = x1, x2 do
      local e = cell(cx, cz)
      if e and e.f <= lim and e.f > g then g = e.f end
    end
  end
  for i = 1, #brushes do
    local b = brushes[i]
    if b.y2 <= lim and b.y2 > g and x + r > b.x1 and x - r < b.x2 and z + r > b.z1 and z - r < b.z2 then g = b.y2 end
  end
  return g
end

--- Потолок над телом (самый низкий потолок клетки или низ коробки выше головы).
function L.ceiling(x, z, r, head)
  local x1, x2 = floor(x - r), floor(x + r - 1e-6)
  local z1, z2 = floor(z - r), floor(z + r - 1e-6)
  local c = 100
  for cz = z1, z2 do
    for cx = x1, x2 do
      local e = cell(cx, cz)
      if e and e.c < c then c = e.c end
    end
  end
  for i = 1, #brushes do
    local b = brushes[i]
    if b.y1 >= head - 0.3 and b.y1 < c and x + r > b.x1 and x - r < b.x2 and z + r > b.z1 and z - r < b.z2 then c = b.y1 end
  end
  return c
end

--- Точка внутри стены, пола, потолка или коробки.
local function solidPoint(x, y, z)
  local e = cell(floor(x), floor(z))
  if not e or y < e.f or y > e.c then return true end
  for i = 1, #brushes do
    local b = brushes[i]
    if x > b.x1 and x < b.x2 and y > b.y1 and y < b.y2 and z > b.z1 and z < b.z2 then return true end
  end
  return false
end
L.solidPoint = solidPoint

--- Луч из (x, y, z) по единичному направлению: расстояние до стены (не дальше maxd).
--- Идём по клеткам (как в Wolfenstein), в каждой — точное пересечение с полом и потолком; коробки — отдельно.
function L.ray(x, y, z, dx, dy, dz, maxd)
  local best = maxd
  for i = 1, #brushes do
    local b = brushes[i]
    local tmin, tmax = 0, best
    local ok = true
    -- три пары плоскостей коробки
    if abs(dx) < 1e-9 then ok = x > b.x1 and x < b.x2 else
      local a, c = (b.x1 - x) / dx, (b.x2 - x) / dx
      if a > c then a, c = c, a end
      if a > tmin then tmin = a end
      if c < tmax then tmax = c end
    end
    if ok then
      if abs(dy) < 1e-9 then ok = y > b.y1 and y < b.y2 else
        local a, c = (b.y1 - y) / dy, (b.y2 - y) / dy
        if a > c then a, c = c, a end
        if a > tmin then tmin = a end
        if c < tmax then tmax = c end
      end
    end
    if ok then
      if abs(dz) < 1e-9 then ok = z > b.z1 and z < b.z2 else
        local a, c = (b.z1 - z) / dz, (b.z2 - z) / dz
        if a > c then a, c = c, a end
        if a > tmin then tmin = a end
        if c < tmax then tmax = c end
      end
    end
    if ok and tmin <= tmax and tmin < best then best = tmin end
  end
  local cx, cz = floor(x), floor(z)
  local sx, sz = dx > 0 and 1 or -1, dz > 0 and 1 or -1
  local tdx, tdz = abs(dx) > 1e-9 and abs(1 / dx) or math.huge, abs(dz) > 1e-9 and abs(1 / dz) or math.huge
  local tmx = dx > 1e-9 and (cx + 1 - x) / dx or (dx < -1e-9 and (cx - x) / dx or math.huge)
  local tmz = dz > 1e-9 and (cz + 1 - z) / dz or (dz < -1e-9 and (cz - z) / dz or math.huge)
  local t = 0
  while t < best do
    local e = cell(cx, cz)
    if not e then return t end
    local yt = y + dy * t
    if yt < e.f - 1e-6 or yt > e.c + 1e-6 then return t end      -- вход в клетку ниже её пола или выше потолка
    local tn = tmx < tmz and tmx or tmz
    if tn > best then tn = best end
    if dy < -1e-9 then
      local tf = (e.f - y) / dy
      if tf < tn then return tf > t and tf or t end
    elseif dy > 1e-9 then
      local tc = (e.c - y) / dy
      if tc < tn then return tc > t and tc or t end
    end
    if tmx < tmz then cx = cx + sx t = tmx tmx = tmx + tdx
    else cz = cz + sz t = tmz tmz = tmz + tdz end
  end
  return best
end

--- Видно ли точку b из точки a.
function L.sees(ax, ay, az, bx, by, bz)
  local dx, dy, dz = bx - ax, by - ay, bz - az
  local d = sqrt(dx * dx + dy * dy + dz * dz)
  if d < 0.01 then return true end
  return L.ray(ax, ay, az, dx / d, dy / d, dz / d, d) >= d - 0.05
end

-- ======================= путь для врагов =======================
-- Поле расстояний от игрока по клеткам: враг шагает в соседнюю клетку с меньшим числом.

local dist, queue = {}, {}
function L.flow(px, pz)
  local W, H = L.W, L.H
  for i = 0, W * H - 1 do dist[i] = nil end
  local sx, sz = floor(px), floor(pz)
  if not cell(sx, sz) then return dist end
  local head, tail = 1, 1
  queue[1] = sz * W + sx
  dist[sz * W + sx] = 0
  while head <= tail do
    local i = queue[head]
    head = head + 1
    local cx, cz = i % W, floor(i / W)
    local e = cells[i]
    local d = dist[i] + 1
    for k = 1, 4 do
      local nx, nz = cx, cz
      if k == 1 then nx = cx + 1 elseif k == 2 then nx = cx - 1 elseif k == 3 then nz = cz + 1 else nz = cz - 1 end
      local j = nz * W + nx
      local n = nx >= 0 and nz >= 0 and nx < W and nz < H and cells[j]
      -- враг пройдёт из n в e: e не выше ступеньки над n, хватает места, в кислоту не лезет
      if n and not dist[j] and not n.acid and e.f - n.f <= L.STEP + 0.05 and n.c - n.f >= 1.9 then
        dist[j] = d
        tail = tail + 1
        queue[tail] = j
      end
    end
  end
  return dist
end
function L.distAt(x, z)
  local cx, cz = floor(x), floor(z)
  if cx < 0 or cz < 0 or cx >= L.W or cz >= L.H then return nil end
  return dist[cz * L.W + cx]
end

-- ======================= свет =======================

--- Запечённый свет в точке p с нормалью n: окружающий + лампы с тенями (луч до лампы по клеткам).
local function lightAt(x, y, z, nx, ny, nz)
  local s = L.AMBIENT
  local ox, oy, oz = x + nx * 0.06, y + ny * 0.06, z + nz * 0.06
  for i = 1, #L.lights do
    local li = L.lights[i]
    local dx, dy, dz = li[1] - ox, li[2] - oy, li[3] - oz
    local d = sqrt(dx * dx + dy * dy + dz * dz)
    local R = li[4]
    if d < R and d > 0.01 then
      local ndl = (dx * nx + dy * ny + dz * nz) / d
      if ndl > -0.1 then
        local k = li[5] * (1 - d / R) * (0.35 + 0.65 * max(0, ndl))
        -- тень: идём к лампе шагами по 0.5 м
        local steps = floor(d / 0.5)
        local ux, uy, uz = dx / d * 0.5, dy / d * 0.5, dz / d * 0.5
        local px, py, pz = ox, oy, oz
        for _ = 1, steps - 1 do
          px, py, pz = px + ux, py + uy, pz + uz
          local e = cell(floor(px), floor(pz))
          if not e or py < e.f or py > e.c then k = k * 0.12 break end
        end
        s = s + k
      end
    end
  end
  return min(1.7, s)
end
L.lightAt = lightAt

-- ======================= сборка 3D-сеток =======================

--- Собрать уровень в сетки по кускам 8×8 м (то, что не видно, не рисуется целиком).
--- progress(доля) — для экрана загрузки. Возвращает { {id, x, z}, ... } и сетку кислоты.
function L.build(art, progress)
  local T = art.tex
  local CH = 8
  local chunks = {}
  local function chunkFor(x, z)
    local k = floor(x / CH) .. ":" .. floor(z / CH)
    local c = chunks[k]
    if not c then c = { b = art.builder() } chunks[k] = c end
    return c.b
  end
  local acid = art.builder()
  local SH = { floor = 1.0, ceil = 0.8, x = 0.9, z = 0.78 }    -- лёгкая разница граней, чтобы углы читались
  local UV = 2                                                 -- метров на повтор текстуры

  -- четырёхугольник с подсветкой каждой вершины, разбитый на куски не больше 2 м (свет по вершинам)
  local function face(b, corners, n, tex, shade)
    -- corners: 4 точки {x,y,z}; делим по сторонам 1→2 и 1→4
    local p1, p2, p4 = corners[1], corners[2], corners[4]
    local ax, ay, az = p2[1] - p1[1], p2[2] - p1[2], p2[3] - p1[3]
    local bx, by, bz = p4[1] - p1[1], p4[2] - p1[2], p4[3] - p1[3]
    local la, lb = sqrt(ax * ax + ay * ay + az * az), sqrt(bx * bx + by * by + bz * bz)
    local na, nb = max(1, math.ceil(la / 2 - 0.01)), max(1, math.ceil(lb / 2 - 0.01))
    local nx, ny, nz = n[1], n[2], n[3]
    -- координаты текстуры от положения в мире
    local function uv(x, y, z)
      if ny > 0.5 then return x / UV, z / UV
      elseif ny < -0.5 then return x / UV, -z / UV
      elseif nx > 0.5 then return -z / UV, -y / UV
      elseif nx < -0.5 then return z / UV, -y / UV
      elseif nz > 0.5 then return x / UV, -y / UV
      else return -x / UV, -y / UV end
    end
    local grid = {}
    for i = 0, na do
      for j = 0, nb do
        local x = p1[1] + ax * i / na + bx * j / nb
        local y = p1[2] + ay * i / na + by * j / nb
        local z = p1[3] + az * i / na + bz * j / nb
        local u, v = uv(x, y, z)
        grid[i * (nb + 1) + j] = { x, y, z, u, v, lightAt(x, y, z, nx, ny, nz) * shade }
      end
    end
    for i = 0, na - 1 do
      for j = 0, nb - 1 do
        local q1, q2 = grid[i * (nb + 1) + j], grid[(i + 1) * (nb + 1) + j]
        local q3, q4 = grid[(i + 1) * (nb + 1) + j + 1], grid[i * (nb + 1) + j + 1]
        b.quad(q1, q2, q3, q4, tex, q1[6], q2[6], q3[6], q4[6])
      end
    end
  end

  local W, H = L.W, L.H
  -- полы и потолки: жадно сливаем одинаковые клетки в прямоугольники
  local function slabs(key, emit)
    local used = {}
    for cz = 0, H - 1 do
      for cx = 0, W - 1 do
        local e = cell(cx, cz)
        local k = e and key(e)
        if k and not used[cz * W + cx] then
          local w = 1
          while cx + w < W and not used[cz * W + cx + w] and cell(cx + w, cz) and key(cell(cx + w, cz)) == k do w = w + 1 end
          local h = 1
          local ok = true
          while ok and cz + h < H do
            for i = 0, w - 1 do
              local n = cell(cx + i, cz + h)
              if not n or used[(cz + h) * W + cx + i] or key(n) ~= k then ok = false break end
            end
            if ok then h = h + 1 end
          end
          for j = 0, h - 1 do for i = 0, w - 1 do used[(cz + j) * W + cx + i] = true end end
          emit(e, cx, cz, cx + w, cz + h)
        end
      end
    end
  end
  slabs(function(e) return e.f .. e.ft end, function(e, x1, z1, x2, z2)
    local y = e.f
    local b = e.acid and acid or chunkFor((x1 + x2) / 2, (z1 + z2) / 2)
    face(b, { { x1, y, z2 }, { x2, y, z2 }, { x2, y, z1 }, { x1, y, z1 } }, { 0, 1, 0 }, T[e.ft], e.acid and 1.4 or (e.pad and 1.5 or SH.floor))
  end)
  if progress then progress(0.25) end
  slabs(function(e) return e.c .. e.ct end, function(e, x1, z1, x2, z2)
    local y = e.c
    face(chunkFor((x1 + x2) / 2, (z1 + z2) / 2), { { x1, y, z1 }, { x2, y, z1 }, { x2, y, z2 }, { x1, y, z2 } }, { 0, -1, 0 }, T[e.ct], SH.ceil)
  end)
  if progress then progress(0.45) end

  -- стены: для каждой клетки и стороны — куски от пола до потолка, которые не закрыты соседом
  -- dir: 1 восток (+x), 2 запад, 3 юг (+z), 4 север
  local function pieces(e, n)
    local out = {}
    if not n then out[1] = { e.f, e.c, e.wt } return out end
    if n.f > e.f then out[#out + 1] = { e.f, min(n.f, e.c), n.side } end
    if n.c < e.c then out[#out + 1] = { max(n.c, e.f), e.c, e.wt } end
    return out
  end
  local function wall(dir, a1, a2, line, y1, y2, tex)
    if y2 - y1 < 0.01 then return end
    local b, n, sh, c
    if dir == 1 then      -- стена на востоке клетки, смотрит на запад
      c = { { line, y1, a1 }, { line, y1, a2 }, { line, y2, a2 }, { line, y2, a1 } }; n = { -1, 0, 0 }; sh = SH.x
      b = chunkFor(line - 0.5, (a1 + a2) / 2)
    elseif dir == 2 then  -- на западе, смотрит на восток
      c = { { line, y1, a2 }, { line, y1, a1 }, { line, y2, a1 }, { line, y2, a2 } }; n = { 1, 0, 0 }; sh = SH.x
      b = chunkFor(line + 0.5, (a1 + a2) / 2)
    elseif dir == 3 then  -- на юге, смотрит на север
      c = { { a2, y1, line }, { a1, y1, line }, { a1, y2, line }, { a2, y2, line } }; n = { 0, 0, -1 }; sh = SH.z
      b = chunkFor((a1 + a2) / 2, line - 0.5)
    else                  -- на севере, смотрит на юг
      c = { { a1, y1, line }, { a2, y1, line }, { a2, y2, line }, { a1, y2, line } }; n = { 0, 0, 1 }; sh = SH.z
      b = chunkFor((a1 + a2) / 2, line + 0.5)
    end
    face(b, c, n, T[tex], sh)
  end
  -- проходим ряды и сливаем соседние одинаковые куски
  for dir = 1, 4 do
    local alongX = dir >= 3                       -- стена тянется вдоль x (юг/север) или вдоль z
    local outer = alongX and H or W
    local inner = alongX and W or H
    for o = 0, outer - 1 do
      local runs = {}                             -- ключ куска → начало
      local function flush(k, endAt)
        local r = runs[k]
        if r then
          local line
          if dir == 1 then line = o + 1 elseif dir == 2 then line = o elseif dir == 3 then line = o + 1 else line = o end
          wall(dir, r.start, endAt, line, r.y1, r.y2, r.tex)
          runs[k] = nil
        end
      end
      for i = 0, inner do
        local seen = {}
        if i < inner then
          local cx, cz = alongX and i or o, alongX and o or i
          local e = cell(cx, cz)
          if e then
            local nx, nz = cx, cz
            if dir == 1 then nx = cx + 1 elseif dir == 2 then nx = cx - 1 elseif dir == 3 then nz = cz + 1 else nz = cz - 1 end
            for _, p in ipairs(pieces(e, cell(nx, nz))) do
              local k = p[1] .. "/" .. p[2] .. "/" .. p[3]
              seen[k] = true
              if not runs[k] then runs[k] = { start = i, y1 = p[1], y2 = p[2], tex = p[3] } end
            end
          end
        end
        for k in pairs(runs) do if not seen[k] then flush(k, i) end end
      end
    end
    if progress then progress(0.45 + dir * 0.1) end
  end

  -- коробки (мост, ящики, пульт)
  for _, br in ipairs(L.brushes) do
    local tx = br.tex and T[br.tex]
    local tt = { top = T[br.top] or tx, side = T[br.side] or tx, bottom = T[br.bottom] or tx }
    local x1, y1, z1, x2, y2, z2 = br.x1, br.y1, br.z1, br.x2, br.y2, br.z2
    local b = chunkFor((x1 + x2) / 2, (z1 + z2) / 2)
    local function sideFace(c, n, tex, sh) face(b, c, n, tex, sh) end
    sideFace({ { x2, y1, z2 }, { x2, y1, z1 }, { x2, y2, z1 }, { x2, y2, z2 } }, { 1, 0, 0 }, tt.side, SH.x)
    sideFace({ { x1, y1, z1 }, { x1, y1, z2 }, { x1, y2, z2 }, { x1, y2, z1 } }, { -1, 0, 0 }, tt.side, SH.x)
    sideFace({ { x1, y1, z2 }, { x2, y1, z2 }, { x2, y2, z2 }, { x1, y2, z2 } }, { 0, 0, 1 }, tt.side, SH.z)
    sideFace({ { x2, y1, z1 }, { x1, y1, z1 }, { x1, y2, z1 }, { x2, y2, z1 } }, { 0, 0, -1 }, tt.side, SH.z)
    sideFace({ { x1, y2, z2 }, { x2, y2, z2 }, { x2, y2, z1 }, { x1, y2, z1 } }, { 0, 1, 0 }, tt.top, SH.floor)
    if y1 > 0.01 then
      sideFace({ { x1, y1, z1 }, { x2, y1, z1 }, { x2, y1, z2 }, { x1, y1, z2 } }, { 0, -1, 0 }, tt.bottom, SH.ceil)
    end
  end
  if progress then progress(0.9) end

  local out = {}
  for _, c in pairs(chunks) do
    -- середина куска — для сортировки «ближние раньше» (меньше перерисовки)
    local nv = c.b.count()
    if nv > 0 then out[#out + 1] = { id = c.b.mesh(), n = nv } end
  end
  local acidMesh = acid.mesh()
  if progress then progress(1) end
  return out, acidMesh
end

return L
