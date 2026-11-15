-- Wired: холст окна (рисование в координатах окна) и поле ввода.
local G = wired.gfx
local T = wired.theme
local I = wired.icons
local U = {}

--- Холст для окна: всё в координатах тела окна (1, 1 — левый верхний угол), щелчки — по меткам hit.
function U.canvas(win)
  local c = { win = win }
  local function X(x) return win.bx + x - 1 end
  local function Y(y) return win.by + y - 1 end
  function c.fill(x, y, w, h, bg, ch, fg) G.fill(X(x), Y(y), w, h, bg, ch, fg) end
  function c.text(x, y, s, fg, bg) G.text(X(x), Y(y), s, fg, bg or c.bg) end
  function c.sprite(x, y, art, pal) G.sprite(X(x), Y(y), art, pal or I.PAL) end
  function c.half(x, y, w, top, bot) G.half(X(x), Y(y), w, top, bot) end
  --- Кадровый буфер видеокарты (gpu.canvas) в точке окна x, y — только в пределах обрезки.
  function c.canvas(cv, x, y)
    local a, b, cc, d = G.getClip()
    return cv:present(X(x), Y(y), a, b, cc, d)
  end
  function c.hit(x, y, w, h, id, data)
    win.hits[#win.hits + 1] = { x, y, x + w - 1, y + h - 1, id, data }
  end
  function c.hline(x, y, w, color) c.text(x, y, string.rep("─", w), color or T.line) end
  function c.button(x, y, label, id, kind, data)
    local s = " " .. label .. " "
    local fg, bg = T.fg, T.btn
    if kind == "primary" then fg, bg = T.ink, T.accent
    elseif kind == "ghost" then fg, bg = T.dim, c.bg
    elseif kind == "off" then fg, bg = T.faint, T.btn end
    c.text(x, y, s, fg, bg)
    local n = G.len(s)
    if id and kind ~= "off" then c.hit(x, y, n, 1, id, data) end
    return x + n
  end
  function c.check(x, y, label, on, id)
    c.text(x, y, on and "☑" or "☐", on and T.accent or T.dim)
    c.text(x + 2, y, label, T.fg)
    if id then c.hit(x, y, G.len(label) + 2, 1, id) end
  end
  function c.radio(x, y, label, on, id)
    c.text(x, y, on and "●" or "○", on and T.accent or T.dim)
    c.text(x + 2, y, label, T.fg)
    if id then c.hit(x, y, G.len(label) + 2, 1, id) end
  end
  function c.progress(x, y, w, t, color)
    local n = math.max(0, math.min(w, math.floor(w * t + 0.5)))
    c.text(x, y, string.rep("█", n), color or T.accent)
    c.text(x + n, y, string.rep("░", w - n), T.faint)
  end
  function c.field(x, y, w, f, placeholder)
    c.fill(x, y, w, 1, T.field)
    local text = f.mask and string.rep(f.mask, G.len(f.text)) or f.text
    local room = w - 2
    local start = math.max(0, f.pos - room + 1)
    if text == "" and placeholder and not f.focused then
      c.text(x + 1, y, G.cut(placeholder, room), T.faint, T.field)
    else
      c.text(x + 1, y, G.sub(text, start + 1, start + room), f.fresh and T.ink or T.fg, f.fresh and T.sel or T.field)
    end
    if f.focused then
      local cx = x + 1 + f.pos - start
      local ch = G.sub(text, f.pos + 1, f.pos + 1)
      c.text(cx, y, ch ~= "" and ch or " ", T.field, T.accent)
    end
    if f.id then c.hit(x, y, w, 1, f.id) end
  end
  --- Полоса прокрутки: pos — первая видимая строка (с 0), total — всего, shown — видно.
  function c.scrollbar(x, y, h, pos, total, shown)
    if total <= shown then return end
    c.fill(x, y, 1, h, c.bg, "│", T.line)
    local size = math.max(1, math.floor(h * shown / total))
    local at = math.floor((h - size) * pos / math.max(1, total - shown) + 0.5)
    c.fill(x, y + at, 1, size, c.bg, "┃", T.dim)
  end
  return c
end

--- Поле ввода: текст, позиция курсора, маска (для паролей).
function U.field(text, id, mask)
  return { text = text or "", pos = G.len(text or ""), id = id, mask = mask, focused = false }
end

--- Обработать клавишу или символ в поле. Возвращает "enter", "tab", true (изменилось) или nil.
function U.edit(f, ev, a)
  local n = G.len(f.text)
  if ev == "char" then
    f.text = G.sub(f.text, 1, f.pos) .. a .. G.sub(f.text, f.pos + 1)
    f.pos = f.pos + G.len(a)
    return true
  elseif ev == "paste" then
    a = tostring(a):gsub("[\r\n].*", "")
    f.text = G.sub(f.text, 1, f.pos) .. a .. G.sub(f.text, f.pos + 1)
    f.pos = f.pos + G.len(a)
    return true
  elseif ev == "key" then
    if a == "enter" then return "enter"
    elseif a == "tab" then return "tab"
    elseif a == "left" and f.pos > 0 then f.pos = f.pos - 1
    elseif a == "right" and f.pos < n then f.pos = f.pos + 1
    elseif a == "home" then f.pos = 0
    elseif a == "end" then f.pos = n
    elseif a == "backspace" and f.pos > 0 then
      f.text = G.sub(f.text, 1, f.pos - 1) .. G.sub(f.text, f.pos + 1)
      f.pos = f.pos - 1
    elseif a == "delete" and f.pos < n then
      f.text = G.sub(f.text, 1, f.pos) .. G.sub(f.text, f.pos + 2)
    else
      return nil
    end
    return true
  end
end

-- ======================= многострочное поле (письмо, заметка) =======================

function U.area(text)
  local a = { lines = {}, row = 1, col = 0, top = 0, focused = false }
  for l in ((text or "") .. "\n"):gmatch("(.-)\n") do a.lines[#a.lines + 1] = l end
  if #a.lines == 0 then a.lines[1] = "" end
  return a
end

function U.areaText(a) return table.concat(a.lines, "\n") end

--- Клавиша или символ в многострочном поле (w — ширина: длинная строка переносится по словам).
function U.areaEdit(a, ev, k, w)
  local line = a.lines[a.row]
  local n = G.len(line)
  local function insert(t)
    a.lines[a.row] = G.sub(line, 1, a.col) .. t .. G.sub(line, a.col + 1)
    a.col = a.col + G.len(t)
    line = a.lines[a.row]
    if w and G.len(line) > w then
      local cut = w
      for i = w, 1, -1 do if G.sub(line, i, i) == " " then cut = i break end end
      local rest = G.sub(line, cut + 1)
      a.lines[a.row] = G.sub(line, 1, cut)
      table.insert(a.lines, a.row + 1, rest)
      if a.col > cut then a.row, a.col = a.row + 1, a.col - cut end
    end
  end
  if ev == "char" then insert(k)
  elseif ev == "paste" then
    local first = true
    for part in (tostring(k) .. "\n"):gmatch("(.-)\n") do
      if not first then
        local l = a.lines[a.row]
        a.lines[a.row] = G.sub(l, 1, a.col)
        table.insert(a.lines, a.row + 1, G.sub(l, a.col + 1))
        a.row, a.col = a.row + 1, 0
        line = a.lines[a.row]
      end
      insert(part)
      first = false
    end
  elseif ev == "key" then
    if k == "enter" then
      a.lines[a.row] = G.sub(line, 1, a.col)
      table.insert(a.lines, a.row + 1, G.sub(line, a.col + 1))
      a.row, a.col = a.row + 1, 0
    elseif k == "backspace" then
      if a.col > 0 then
        a.lines[a.row] = G.sub(line, 1, a.col - 1) .. G.sub(line, a.col + 1)
        a.col = a.col - 1
      elseif a.row > 1 then
        local prev = a.lines[a.row - 1]
        a.col = G.len(prev)
        a.lines[a.row - 1] = prev .. line
        table.remove(a.lines, a.row)
        a.row = a.row - 1
      end
    elseif k == "delete" then
      if a.col < n then a.lines[a.row] = G.sub(line, 1, a.col) .. G.sub(line, a.col + 2)
      elseif a.row < #a.lines then a.lines[a.row] = line .. a.lines[a.row + 1] table.remove(a.lines, a.row + 1) end
    elseif k == "left" then
      if a.col > 0 then a.col = a.col - 1 elseif a.row > 1 then a.row = a.row - 1 a.col = G.len(a.lines[a.row]) end
    elseif k == "right" then
      if a.col < n then a.col = a.col + 1 elseif a.row < #a.lines then a.row = a.row + 1 a.col = 0 end
    elseif k == "up" and a.row > 1 then a.row = a.row - 1 a.col = math.min(a.col, G.len(a.lines[a.row]))
    elseif k == "down" and a.row < #a.lines then a.row = a.row + 1 a.col = math.min(a.col, G.len(a.lines[a.row]))
    elseif k == "home" then a.col = 0
    elseif k == "end" then a.col = n
    else return nil end
  else
    return nil
  end
  return true
end

--- Нарисовать многострочное поле на холсте c.
function U.drawArea(c, x, y, w, h, a, bg, fg)
  bg, fg = bg or T.field, fg or T.fg
  c.fill(x, y, w, h, bg)
  if a.row <= a.top then a.top = a.row - 1 end
  if a.row > a.top + h then a.top = a.row - h end
  for i = 1, h do
    local l = a.lines[a.top + i]
    if not l then break end
    c.text(x + 1, y + i - 1, G.sub(l, 1, w - 2), fg, bg)
  end
  if a.focused then
    local cy = y + a.row - a.top - 1
    local ch = G.sub(a.lines[a.row], a.col + 1, a.col + 1)
    c.text(x + 1 + a.col, cy, ch ~= "" and ch or " ", bg, T.accent)
  end
end

return U
