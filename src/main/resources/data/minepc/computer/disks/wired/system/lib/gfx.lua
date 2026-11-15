-- Wired: рисование. Всё рисуется в задний буфер (если хватает видеопамяти) с обрезкой по прямоугольнику,
-- потом изменённый кусок одним bitblt уходит на экран. Полупиксели — символ «▀» (верх — цвет текста, низ — фон).
local gpu = computer.gpu()
local ulen, usub = unicode.len, unicode.sub
local floor, max, min = math.floor, math.max, math.min

local G = { W = 80, H = 25, back = 0, gpu = gpu }
local cx1, cy1, cx2, cy2 = 1, 1, 80, 25
local curF, curB

--- Подготовиться к рисованию в текущем разрешении (после смены масштаба — снова).
--- Разрешение экрана (не буфера: getResolution отвечает про активный буфер).
function G.screenSize()
  local cur = gpu.getActiveBuffer and gpu.getActiveBuffer() or 0
  if cur ~= 0 then gpu.setActiveBuffer(0) end
  local w, h = gpu.getResolution()
  if cur ~= 0 then gpu.setActiveBuffer(cur) end
  return w, h
end

function G.init()
  gpu.setActiveBuffer(0)
  G.W, G.H = gpu.getResolution()
  if G.back and G.back > 0 then pcall(gpu.freeBuffer, G.back) end
  G.back = 0
  local ok, id = pcall(gpu.allocateBuffer, G.W, G.H)
  if ok and type(id) == "number" and id > 0 then G.back = id end
  gpu.setActiveBuffer(G.back)
  curF, curB = nil, nil
  G.noclip()
end

function G.release()
  gpu.setActiveBuffer(0)
  if G.back and G.back > 0 then pcall(gpu.freeBuffer, G.back) end
  G.back = 0
end

-- ---------- обрезка ----------
function G.clip(x, y, w, h)
  cx1, cy1 = max(1, x), max(1, y)
  cx2, cy2 = min(G.W, x + w - 1), min(G.H, y + h - 1)
end
function G.noclip() cx1, cy1, cx2, cy2 = 1, 1, G.W, G.H end
function G.getClip() return cx1, cy1, cx2, cy2 end
function G.setClip(a, b, c, d) cx1, cy1, cx2, cy2 = a, b, c, d end
--- Сузить обрезку до пересечения с прямоугольником. false — пересечения нет.
function G.narrow(x, y, w, h)
  cx1, cy1 = max(cx1, x), max(cy1, y)
  cx2, cy2 = min(cx2, x + w - 1), min(cy2, y + h - 1)
  return cx2 >= cx1 and cy2 >= cy1
end

local function F(c) if c ~= curF then gpu.setForeground(c); curF = c end end
local function B(c) if c ~= curB then gpu.setBackground(c); curB = c end end

-- ---------- примитивы ----------
function G.fill(x, y, w, h, bg, ch, fg)
  local x1, y1 = max(x, cx1), max(y, cy1)
  local x2, y2 = min(x + w - 1, cx2), min(y + h - 1, cy2)
  if x2 < x1 or y2 < y1 then return end
  B(bg)
  if fg then F(fg) end
  gpu.fill(x1, y1, x2 - x1 + 1, y2 - y1 + 1, ch or " ")
end

function G.text(x, y, s, fg, bg)
  if y < cy1 or y > cy2 then return end
  s = tostring(s)
  local n = ulen(s)
  if n == 0 or x > cx2 or x + n - 1 < cx1 then return end
  local a, b = 1, n
  if x < cx1 then a = cx1 - x + 1 end
  if x + n - 1 > cx2 then b = cx2 - x + 1 end
  F(fg)
  B(bg)
  gpu.set(x + a - 1, y, (a == 1 and b == n) and s or usub(s, a, b))
end

--- Цвет «под» клеткой (фон; у полупиксельной клетки — низ и верх).
function G.under(x, y)
  local ok, ch, f, b = pcall(gpu.get, x, y)
  if not ok then return 0, 0 end
  if ch == "▀" then return b, f end
  return b, b
end

--- Текст поверх картинки: фон каждой буквы — нижний полупиксель под ней.
function G.textOver(x, y, s, fg)
  if y < cy1 or y > cy2 then return end
  s = tostring(s)
  F(fg)
  for i = 1, ulen(s) do
    local xx = x + i - 1
    if xx >= cx1 and xx <= cx2 then
      local b = G.under(xx, y)
      B(b)
      gpu.set(xx, y, usub(s, i, i))
    end
  end
end

--- Картинка из полупикселей: art — строки букв палитры pal (точка — прозрачно), x, y — клетка.
function G.sprite(x, y, art, pal)
  for r = 1, #art, 2 do
    local yy = y + floor((r - 1) / 2)
    if yy >= cy1 and yy <= cy2 then
      local top, bot = art[r], art[r + 1] or ""
      for i = 1, max(#top, #bot) do
        local xx = x + i - 1
        if xx >= cx1 and xx <= cx2 then
          local tc, bc = pal[top:sub(i, i)], pal[bot:sub(i, i)]
          if tc or bc then
            if not (tc and bc) then
              local ub, ut = G.under(xx, yy)
              tc, bc = tc or ut, bc or ub
            end
            if tc == bc then
              B(tc)
              gpu.set(xx, yy, " ")
            else
              F(tc)
              B(bc)
              gpu.set(xx, yy, "▀")
            end
          end
        end
      end
    end
  end
end

--- Полоса из полупикселей: верх клетки цвета top, низ — bot.
function G.half(x, y, w, top, bot)
  if top == bot then return G.fill(x, y, w, 1, top) end
  G.fill(x, y, w, 1, bot, "▀", top)
end

--- Отправить кусок заднего буфера на экран.
function G.present(x, y, w, h)
  if G.back > 0 then
    gpu.bitblt(0, x, y, w, h, G.back, x, y)
  end
end

-- ---------- обои: ряды полос (x1, x2, верх, низ) ----------
function G.drawRuns(rows, y0)
  for cy = max(cy1, y0), min(cy2, y0 + #rows - 1) do
    local runs = rows[cy - y0 + 1]
    for i = 1, #runs, 4 do
      local a, b = runs[i], runs[i + 1]
      if b >= cx1 and a <= cx2 then G.half(a, cy, b - a + 1, runs[i + 2], runs[i + 3]) end
    end
  end
end

-- ---------- строки ----------
G.len, G.sub = ulen, usub
function G.cut(s, n)
  s = tostring(s)
  if ulen(s) <= n then return s end
  if n <= 1 then return usub(s, 1, n) end
  return usub(s, 1, n - 1) .. "…"
end
--- Разбить текст на строки шириной w (по словам; \n — новая строка).
function G.wrap(text, w)
  local out = {}
  w = math.max(1, w)
  for para in (tostring(text) .. "\n"):gmatch("(.-)\n") do
    if para == "" then out[#out + 1] = "" else
      local line = ""
      for word in para:gmatch("%S+") do
        if line == "" then line = word
        elseif ulen(line) + 1 + ulen(word) <= w then line = line .. " " .. word
        else out[#out + 1] = line line = word end
        while ulen(line) > w do out[#out + 1] = usub(line, 1, w) line = usub(line, w + 1) end
      end
      out[#out + 1] = line
    end
  end
  return out
end

function G.pad(s, n)
  s = G.cut(s, n)
  return s .. string.rep(" ", n - ulen(s))
end

return G
