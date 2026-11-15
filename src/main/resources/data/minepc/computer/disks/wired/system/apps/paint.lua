-- Wired: Рисовалка — картинки для сайтов и документов (.pix) и значки программ (icon.pix, 9×12).
-- Пиксель холста — две клетки экрана (почти квадрат); справа — превью в настоящем размере полупикселями.
local G, T, I, U, wm = wired.gfx, wired.theme, wired.icons, wired.ui, wired.wm
local DIR = "/home/Картинки"

local COLORS = {
  0x000000, 0x3a3550, 0x8d87a3, 0xffffff, 0xff4f5e, 0xb8323f, 0xff8a3d, 0xffc857,
  0x5fd38a, 0x2f9a5a, 0x4fd1e8, 0x2a8ea3, 0x6b95ff, 0x3a5fc0, 0xc77dff, 0x8a4fc0,
  0xffe3a3, 0xc9932a, 0x8b5a2b, 0xff7fbf,
}
local TOOLS = {
  { "pen", "✎", "Карандаш" }, { "line", "╱", "Линия" }, { "rect", "▭", "Рамка" }, { "box", "■", "Прямоугольник" },
  { "fill", "▧", "Заливка" }, { "pick", "⊙", "Пипетка" }, { "erase", "⌫", "Ластик" },
}

local function newImage(w, h, bg)
  local img = { w = w, h = h, px = {} }
  if bg then for i = 1, w * h do img.px[i] = bg end end
  return img
end

local function loadImage(path)
  local f = fs.open(path, "r")
  if not f then return nil, "не открывается" end
  local data = f.readAll() or ""
  f.close()
  local w, h, rest = data:match("^PIX1%s+(%d+)%s+(%d+)%s*\n(.*)$")
  if w then
    w, h = tonumber(w), tonumber(h)
    if w < 1 or h < 1 or w > 320 or h > 400 then return nil, "слишком большая картинка" end
    local img = newImage(w, h)
    local y = 0
    for line in (rest .. "\n"):gmatch("(.-)\r?\n") do
      if y >= h then break end
      for x = 0, w - 1 do img.px[y * w + x + 1] = tonumber(line:sub(x * 6 + 1, x * 6 + 6), 16) end
      y = y + 1
    end
    return img
  end
  -- значок: строки букв палитры
  local rows = {}
  for line in data:gmatch("[^\r\n]+") do rows[#rows + 1] = line end
  if #rows == 0 then return nil, "пустой файл" end
  local img = newImage(9, 12)
  for y = 0, 11 do
    local r = rows[y + 1] or ""
    for x = 0, 8 do img.px[y * 9 + x + 1] = I.PAL[r:sub(x + 1, x + 1)] end
  end
  img.icon = true
  return img
end

local function nearestLetter(col)
  local best, bd = ".", math.huge
  local function rgb(v) return math.floor(v / 65536) % 256, math.floor(v / 256) % 256, v % 256 end
  local r, g, b = rgb(col)
  for k, v in pairs(I.PAL) do
    local r2, g2, b2 = rgb(v)
    local d = (r - r2) ^ 2 + (g - g2) ^ 2 + (b - b2) ^ 2
    if d < bd then best, bd = k, d end
  end
  return best
end

local function saveImage(img, path)
  local d = fs.getDir(path)
  if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
  local out = {}
  if fs.getName(path) == "icon.pix" then
    for y = 0, 11 do
      local row = {}
      for x = 0, 8 do
        local col = x < img.w and y < img.h and img.px[y * img.w + x + 1] or nil
        row[#row + 1] = col and nearestLetter(col) or "."
      end
      out[#out + 1] = table.concat(row)
    end
  else
    out[1] = "PIX1 " .. img.w .. " " .. img.h
    for y = 0, img.h - 1 do
      local row = {}
      for x = 0, img.w - 1 do
        local col = img.px[y * img.w + x + 1]
        row[#row + 1] = col and string.format("%06x", col) or "------"
      end
      out[#out + 1] = table.concat(row)
    end
  end
  local f = fs.open(path, "w")
  if not f then return false, "не записывается" end
  f.write(table.concat(out, "\n") .. "\n")
  f.close()
  return true
end

return function(arg)
  local app = { title = "Рисовалка", icon = "paint", w = 96, h = 32, minW = 60, minH = 20 }
  local st = { img = newImage(48, 32, 0xffffff), tool = "pen", c1 = 0x000000, c2 = nil, size = 1, ox = 0, oy = 0,
    path = nil, dirty = false, undo = {}, redo = {}, drag = nil }
  local win

  if type(arg) == "string" and fs.exists(arg) then
    local img, err = loadImage(arg)
    if img then st.img, st.path = img, arg else wm.notify({ title = "Рисовалка", text = tostring(err) }) end
  end

  local function retitle()
    if not win then return end
    wm.setTitle(win, "Рисовалка — " .. (st.path and fs.getName(st.path) or "без имени") .. (st.dirty and " •" or ""))
  end

  function app.init(w) win = w retitle() end

  -- геометрия: палитра слева (8), превью справа (PW), холст между
  local PAL_W = 9
  local function geom(c)
    local pw = math.min(26, math.max(14, st.img.w + 2))
    local x0, y0 = PAL_W + 2, 3
    local vw = math.floor((c.w - PAL_W - pw - 3) / 2)
    local vh = c.h - y0
    return x0, y0, math.max(1, vw), math.max(1, vh), pw
  end

  local function snapshot()
    local copy = {}
    for k, v in pairs(st.img.px) do copy[k] = v end
    st.undo[#st.undo + 1] = copy
    if #st.undo > 30 then table.remove(st.undo, 1) end
    st.redo = {}
  end

  local function set(x, y, col)
    local img = st.img
    local r = st.size - 1
    for dy = -math.floor(r / 2), math.ceil(r / 2) do
      for dx = -math.floor(r / 2), math.ceil(r / 2) do
        local px, py = x + dx, y + dy
        if px >= 0 and py >= 0 and px < img.w and py < img.h then img.px[py * img.w + px + 1] = col end
      end
    end
  end

  local function line(x1, y1, x2, y2, col)
    local dx, dy = math.abs(x2 - x1), -math.abs(y2 - y1)
    local sx, sy = x1 < x2 and 1 or -1, y1 < y2 and 1 or -1
    local err = dx + dy
    while true do
      set(x1, y1, col)
      if x1 == x2 and y1 == y2 then break end
      local e2 = 2 * err
      if e2 >= dy then err = err + dy x1 = x1 + sx end
      if e2 <= dx then err = err + dx y1 = y1 + sy end
    end
  end

  local function rect(x1, y1, x2, y2, col, filled)
    if x1 > x2 then x1, x2 = x2, x1 end
    if y1 > y2 then y1, y2 = y2, y1 end
    for y = y1, y2 do
      for x = x1, x2 do
        if filled or x == x1 or x == x2 or y == y1 or y == y2 then
          if x >= 0 and y >= 0 and x < st.img.w and y < st.img.h then st.img.px[y * st.img.w + x + 1] = col end
        end
      end
    end
  end

  local function flood(x, y, col)
    local img = st.img
    local target = img.px[y * img.w + x + 1]
    if target == col then return end
    local stack, n = { { x, y } }, 0
    while #stack > 0 and n < 100000 do
      local p = table.remove(stack)
      local px, py = p[1], p[2]
      if px >= 0 and py >= 0 and px < img.w and py < img.h and img.px[py * img.w + px + 1] == target then
        img.px[py * img.w + px + 1] = col
        n = n + 1
        stack[#stack + 1] = { px + 1, py }
        stack[#stack + 1] = { px - 1, py }
        stack[#stack + 1] = { px, py + 1 }
        stack[#stack + 1] = { px, py - 1 }
      end
    end
  end

  local function checker(x, y) return ((x + y) % 2 == 0) and 0x2a2638 or 0x1f1b2b end

  function app.draw(w, c)
    local img = st.img
    -- панель инструментов
    local bx = 2
    bx = c.button(bx, 1, "Новый", "new", "ghost")
    bx = c.button(bx, 1, "Открыть", "open", "ghost")
    bx = c.button(bx, 1, "Сохранить", "save", "ghost")
    bx = c.button(bx, 1, "Как…", "saveas", "ghost") + 1
    for _, t in ipairs(TOOLS) do
      local act = st.tool == t[1]
      c.text(bx, 1, " " .. t[2] .. " ", act and T.ink or T.fg, act and T.accent or T.btn)
      c.hit(bx, 1, 3, 1, "tool", t[1])
      bx = bx + 4
    end
    bx = bx + 1
    for s = 1, 3 do
      local act = st.size == s
      c.text(bx, 1, " " .. s .. " ", act and T.ink or T.dim, act and T.accent or T.btn)
      c.hit(bx, 1, 3, 1, "size", s)
      bx = bx + 3
    end
    bx = c.button(bx + 1, 1, "↶", "undo", #st.undo > 0 and "normal" or "off")
    c.button(bx + 1, 1, "↷", "redo", #st.redo > 0 and "normal" or "off")
    local tname = ""
    for _, t in ipairs(TOOLS) do if t[1] == st.tool then tname = t[3] end end
    c.text(2, 2, tname .. " · левая кнопка — основной цвет, правая — второй", T.faint)
    -- палитра
    for i, col in ipairs(COLORS) do
      local x = 2 + ((i - 1) % 2) * 4
      local y = 3 + math.floor((i - 1) / 2)
      c.fill(x, y, 3, 1, col)
      if col == st.c1 then c.text(x + 1, y, "●", (col == 0xffffff or col == 0xffe3a3 or col == 0xffc857) and 0x000000 or 0xffffff, col) end
      if col == st.c2 then c.text(x, y, "○", (col == 0xffffff) and 0x000000 or 0xffffff, col) end
      c.hit(x, y, 3, 1, "color", col)
    end
    local ty = 3 + math.ceil(#COLORS / 2)
    c.fill(2, ty, 3, 1, checker(0, 0))
    c.text(2, ty, st.c2 == nil and "○ " or "  ", 0xffffff, checker(0, 0))
    c.text(5, ty, "нет", T.dim)
    c.hit(2, ty, 6, 1, "color", false)
    c.text(2, ty + 2, "Цвета", T.dim)
    c.fill(2, ty + 3, 3, 2, st.c1 or checker(0, 0))
    c.fill(5, ty + 4, 3, 2, st.c2 or checker(1, 0))
    c.text(2, ty + 7, "свой", T.dim)
    c.hit(2, ty + 7, 4, 1, "custom")
    -- холст
    local x0, y0, vw, vh, pw = geom(c)
    st.ox = math.max(0, math.min(st.ox, img.w - vw))
    st.oy = math.max(0, math.min(st.oy, img.h - vh))
    for vy = 0, math.min(vh, img.h) - 1 do
      local py = st.oy + vy
      for vx = 0, math.min(vw, img.w) - 1 do
        local px = st.ox + vx
        local col = img.px[py * img.w + px + 1]
        c.fill(x0 + vx * 2, y0 + vy, 2, 1, col or checker(px, py))
      end
    end
    -- превью рамки/линии во время рисования
    if st.drag and (st.tool == "line" or st.tool == "rect" or st.tool == "box") and st.drag.x2 then
      local d = st.drag
      local function mark(px, py)
        local vx, vy = px - st.ox, py - st.oy
        if vx >= 0 and vy >= 0 and vx < vw and vy < vh and px < img.w and py < img.h then
          c.fill(x0 + vx * 2, y0 + vy, 2, 1, d.col or checker(px, py))
        end
      end
      if st.tool == "line" then
        local x1, y1, x2, y2 = d.x1, d.y1, d.x2, d.y2
        local dx, dy = math.abs(x2 - x1), -math.abs(y2 - y1)
        local sx, sy = x1 < x2 and 1 or -1, y1 < y2 and 1 or -1
        local err = dx + dy
        while true do
          mark(x1, y1)
          if x1 == x2 and y1 == y2 then break end
          local e2 = 2 * err
          if e2 >= dy then err = err + dy x1 = x1 + sx end
          if e2 <= dx then err = err + dx y1 = y1 + sy end
        end
      else
        local ax, bx2 = math.min(d.x1, d.x2), math.max(d.x1, d.x2)
        local ay, by2 = math.min(d.y1, d.y2), math.max(d.y1, d.y2)
        for y = ay, by2 do for x = ax, bx2 do
          if st.tool == "box" or x == ax or x == bx2 or y == ay or y == by2 then mark(x, y) end
        end end
      end
    end
    -- превью и сведения
    local rx = c.w - pw + 1
    c.text(rx, 3, "Превью", T.dim)
    local maxW, maxH = pw - 2, (c.h - 12) * 2
    local scale = math.max(1, math.ceil(math.max(img.w / maxW, img.h / maxH)))
    local cols, rows = math.ceil(img.w / scale), math.ceil(img.h / scale / 2)
    for r = 0, rows - 1 do
      for col = 0, cols - 1 do
        local px = col * scale
        local top = img.px[(r * 2 * scale) * img.w + px + 1]
        local yb = (r * 2 + 1) * scale
        local bot = yb < img.h and img.px[yb * img.w + px + 1] or nil
        c.half(rx + col, 4 + r, 1, top or T.field, bot or T.field)
      end
    end
    local iy = 5 + rows
    c.text(rx, iy, img.w .. "×" .. img.h .. " пикселей", T.fg)
    if scale > 1 then c.text(rx, iy + 1, "превью 1:" .. scale, T.faint) end
    if img.w > vw or img.h > vh then c.text(rx, iy + 2, "←→↑↓ — прокрутка", T.faint) end
    c.text(rx, c.h - 1, G.cut(st.path or "не сохранена", pw - 1), T.faint)
  end

  local function toPixel(lx, ly)
    local x0, y0, vw, vh = geom(win.c)
    local vx, vy = math.floor((lx - x0) / 2), ly - y0
    if lx < x0 or vx >= vw or vy < 0 or vy >= vh then return nil end
    local px, py = st.ox + vx, st.oy + vy
    if px >= st.img.w or py >= st.img.h then return nil end
    return px, py
  end

  local function setDirty()
    if not st.dirty then st.dirty = true retitle() end
  end

  local function stroke(kind, btn, lx, ly)
    local px, py = toPixel(lx, ly)
    local col
    if btn == 2 then col = st.c2 else col = st.c1 end
    if st.tool == "erase" then col = nil end
    if kind == "down" then
      if not px then return end
      if st.tool == "pick" then
        local v = st.img.px[py * st.img.w + px + 1]
        if btn == 2 then st.c2 = v else st.c1 = v or st.c1 end
        st.tool = "pen"
        return
      end
      snapshot()
      st.drag = { x1 = px, y1 = py, lx = px, ly = py, col = col }
      if st.tool == "pen" or st.tool == "erase" then set(px, py, col) setDirty()
      elseif st.tool == "fill" then flood(px, py, col) setDirty() st.drag = nil end
    elseif kind == "drag" and st.drag then
      if not px then return end
      local d = st.drag
      if st.tool == "pen" or st.tool == "erase" then line(d.lx, d.ly, px, py, d.col) setDirty()
      else d.x2, d.y2 = px, py end
      d.lx, d.ly = px, py
    elseif kind == "up" and st.drag then
      local d = st.drag
      if d.x2 then
        if st.tool == "line" then line(d.x1, d.y1, d.x2, d.y2, d.col)
        elseif st.tool == "rect" then rect(d.x1, d.y1, d.x2, d.y2, d.col, false)
        elseif st.tool == "box" then rect(d.x1, d.y1, d.x2, d.y2, d.col, true) end
        setDirty()
      elseif st.tool == "line" or st.tool == "rect" or st.tool == "box" then
        set(d.x1, d.y1, d.col)
        setDirty()
      end
      st.drag = nil
    end
  end

  local function saveTo(path)
    local ok, err = saveImage(st.img, path)
    if ok then
      st.path, st.dirty = path, false
      retitle()
      wm.notify({ title = "Рисовалка", text = "Сохранено: " .. path })
    else
      wm.alert("Не сохранилось", tostring(err))
    end
  end

  local function saveAs()
    local def = st.path or (DIR .. "/картинка.pix")
    wm.prompt("Сохранить как", "Путь (icon.pix — значок программы 9×12):", def, function(v)
      if not v or v == "" then return end
      if not v:match("%.pix$") then v = v .. ".pix" end
      if v:sub(1, 1) ~= "/" then v = fs.combine(DIR, v) end
      saveTo(v)
    end)
  end

  local function askNew()
    wm.prompt("Новая картинка", "Размер ШИРИНАxВЫСОТА (значок — 9x12):", "48x32", function(v)
      local w2, h2 = tostring(v or ""):match("(%d+)%s*[xх×*]%s*(%d+)")
      w2, h2 = tonumber(w2), tonumber(h2)
      if not w2 or w2 < 1 or h2 < 1 or w2 > 320 or h2 > 400 then return end
      local icon = w2 == 9 and h2 == 12
      st.img = newImage(w2, h2, not icon and 0xffffff or nil)
      st.path, st.dirty, st.undo, st.redo, st.ox, st.oy = nil, false, {}, {}, 0, 0
      retitle()
      wm.redraw(win)
    end)
  end

  local function guard(fn)
    if not st.dirty then return fn() end
    wm.confirm("Рисовалка", "Картинка не сохранена. Продолжить без сохранения?", function(ok) if ok then fn() end end, "Да")
  end

  function app.event(w, ev, a, b, lx, ly, btn)
    if ev == "mouse_click" then stroke("down", a, b, lx) wm.redraw(w) return
    elseif ev == "mouse_drag" then stroke("drag", a, b, lx) wm.redraw(w) return
    elseif ev == "mouse_up" then stroke("up", a, b, lx) wm.redraw(w) return
    elseif ev == "scroll" then st.oy = st.oy + a * 2 wm.redraw(w) return
    elseif ev == "key" then
      if a == "left" then st.ox = st.ox - 2 elseif a == "right" then st.ox = st.ox + 2
      elseif a == "up" then st.oy = st.oy - 2 elseif a == "down" then st.oy = st.oy + 2
      elseif a == "s" and b then if st.path then saveTo(st.path) else saveAs() end
      elseif a == "z" and b then ev, a = "click", "undo"
      elseif a == "y" and b then ev, a = "click", "redo" end
      if ev == "key" then wm.redraw(w) return end
    end
    if ev ~= "click" and ev ~= "dblclick" then return end
    if a == "tool" then st.tool = b
    elseif a == "size" then st.size = b
    elseif a == "color" then
      local col = b ~= false and b or nil
      -- правая кнопка — второй цвет
      if btn == 2 then st.c2 = col else if col then st.c1 = col else st.c2 = nil end end
    elseif a == "custom" then
      wm.prompt("Свой цвет", "Цвет в шестнадцатеричном виде (например ff8800):", string.format("%06x", st.c1 or 0), function(v)
        local n = tonumber(tostring(v or ""):gsub("^#", ""), 16)
        if n and n >= 0 and n <= 0xffffff then st.c1 = n wm.redraw(w) end
      end)
    elseif a == "undo" and #st.undo > 0 then
      local cur = {}
      for k, v in pairs(st.img.px) do cur[k] = v end
      st.redo[#st.redo + 1] = cur
      st.img.px = table.remove(st.undo)
      setDirty()
    elseif a == "redo" and #st.redo > 0 then
      local cur = {}
      for k, v in pairs(st.img.px) do cur[k] = v end
      st.undo[#st.undo + 1] = cur
      st.img.px = table.remove(st.redo)
      setDirty()
    elseif a == "new" then guard(askNew)
    elseif a == "open" then
      guard(function()
        wm.prompt("Открыть", "Путь к картинке .pix:", st.path or (DIR .. "/"), function(v)
          if not v or v == "" then return end
          if v:sub(1, 1) ~= "/" then v = fs.combine(DIR, v) end
          local img, err = loadImage(v)
          if not img then wm.alert("Не открылась", tostring(err)) return end
          st.img, st.path, st.dirty, st.undo, st.redo, st.ox, st.oy = img, v, false, {}, {}, 0, 0
          retitle()
          wm.redraw(w)
        end)
      end)
    elseif a == "save" then if st.path then saveTo(st.path) else saveAs() end
    elseif a == "saveas" then saveAs() end
    wm.redraw(w)
  end

  function app.close(w)
    if not st.dirty then return true end
    wm.confirm("Рисовалка", "Картинка не сохранена. Закрыть без сохранения?", function(ok)
      if ok then wm.close(w, true) end
    end, "Закрыть")
    return false
  end

  return app
end
