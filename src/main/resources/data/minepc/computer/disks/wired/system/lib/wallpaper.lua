-- Wired: обои «Сеть» — закатное небо и чёрные силуэты столбов с проводами (present day, present time).
-- Считаются один раз на размер экрана и хранятся полосами: { x1, x2, верх, низ, ... } на каждую строку.
local floor, max = math.floor, math.max
local W = {}

local function lerp(a, b, t)
  local function ch(c, s) return floor(c / s) % 256 end
  local r = ch(a, 65536) + (ch(b, 65536) - ch(a, 65536)) * t
  local g = ch(a, 256) + (ch(b, 256) - ch(a, 256)) * t
  local bl = a % 256 + (b % 256 - a % 256) * t
  return floor(r + 0.5) * 65536 + floor(g + 0.5) * 256 + floor(bl + 0.5)
end

--- Обои для области w × rows клеток. style: "wires" или "plain"; dark — тёмная тема.
function W.build(w, rows, style, dark, plainColor)
  local H = rows * 2
  local out = {}
  if style == "plain" then
    for cy = 1, rows do out[cy] = { 1, w, plainColor, plainColor } end
    return out
  end
  local sky0, sky1, gnd, sil
  if dark then
    sky0, sky1, gnd, sil = 0x09080f, 0x4a1a36, 0x07060a, 0x060509
  else
    sky0, sky1, gnd, sil = 0x9fb4dc, 0xf2c9b8, 0x3a3246, 0x2a2433
  end
  local ground = H - max(3, floor(H / 16))
  local mark = {}
  local function put(x, y)
    if x >= 0 and x < w and y >= 0 and y < H then mark[y * w + x] = true end
  end
  local function rect(x, y, rw, rh)
    for yy = y, y + rh - 1 do for xx = x, x + rw - 1 do put(xx, yy) end end
  end
  local n = max(3, floor(w / 50))
  local xs = {}
  for i = 0, n - 1 do xs[#xs + 1] = floor(w * (0.18 + 0.74 * i / (n - 1)) + 0.5) end
  local top = floor(H * 0.30 + 0.5)
  for _, x in ipairs(xs) do
    rect(x, top, 1, ground - top)
    rect(x - 7, top + 1, 15, 1)
    rect(x - 4, top + 5, 9, 1)
    rect(x + 1, top + 9, 2, 3)
  end
  local wires = { { -6, 1 }, { 6, 1 }, { -3, 5 }, { 3, 5 } }
  local function span(x0, x1, y0, sag)
    local a, b = x0, x1
    if a > b then a, b = b, a end
    for x = a, b do
      local t = (x1 ~= x0) and (x - x0) / (x1 - x0) or 0
      put(x, y0 + floor(sag * 4 * t * (1 - t) + 0.5))
    end
  end
  local step = xs[2] - xs[1]
  for k, d in ipairs(wires) do
    local sag = 4 + (k - 1) % 2 * 2
    for i = 1, #xs - 1 do span(xs[i] + d[1], xs[i + 1] + d[1], top + d[2], sag) end
    span(xs[1] - step + d[1], xs[1] + d[1], top + d[2], sag)
    span(xs[#xs] + d[1], xs[#xs] + step + d[1], top + d[2], sag)
  end
  local function base(y)
    if y >= ground then return gnd end
    local t = y / ground
    return lerp(sky0, sky1, t * t * (dark and 1.2 or 1) > 1 and 1 or t * t * (dark and 1.2 or 1))
  end
  for cy = 0, rows - 1 do
    local yt, yb = cy * 2, cy * 2 + 1
    local ct, cb = base(yt), base(yb)
    local runs = {}
    local rx, rt, rb
    for x = 0, w - 1 do
      local t = mark[yt * w + x] and sil or ct
      local b = mark[yb * w + x] and sil or cb
      if rx and t == rt and b == rb then
        runs[#runs - 2] = x + 1
      else
        runs[#runs + 1] = x + 1
        runs[#runs + 1] = x + 1
        runs[#runs + 1] = t
        runs[#runs + 1] = b
        rx, rt, rb = x, t, b
      end
    end
    out[cy + 1] = runs
  end
  return out
end

return W
