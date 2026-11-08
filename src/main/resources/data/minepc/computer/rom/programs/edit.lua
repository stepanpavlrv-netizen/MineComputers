-- Редактор текста с подсветкой Lua.
-- Стрелки, Home/End, PageUp/PageDown, мышь — перемещение; Ctrl+S — сохранить; Ctrl+Q — выйти.
local name = ...
if not name then print("Использование: edit <файл>"); return end
local path = shell.resolve(name)
if fs.isDir(path) then printError("Это папка"); return end
local readOnly = fs.isReadOnly(path)
local ulen, usub = unicode.len, unicode.sub

local lines = {}
if fs.exists(path) then
  local f = fs.open(path, "r")
  local text = f.readAll() or ""
  f.close()
  for line in (text .. "\n"):gmatch("(.-)\r?\n") do lines[#lines + 1] = line end
  if #lines > 1 and lines[#lines] == "" and not text:match("\n$") then table.remove(lines) end
end
if #lines == 0 then lines[1] = "" end

local w, h = term.getSize()
local cx, cy = 1, 1          -- курсор: столбец (в символах) и строка в тексте
local sx, sy = 0, 0          -- прокрутка
local modified = false
local status = readOnly and "Только чтение" or "Ctrl+S сохранить, Ctrl+Q выйти"
local color = term.isColor()

local keywords = {}
for k in ("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"):gmatch("%a+") do
  keywords[k] = true
end
local isLua = path:match("%.lua$") ~= nil
local dedent = { ["end"] = true, ["else"] = true, ["until"] = true }
local UTF8 = "^[%z\1-\127\194-\244][\128-\191]*"

-- Раскраска одной строки: список {текст, цвет}
local function highlight(line)
  if not (color and isLua) then return { { line, colors.white } } end
  local parts, i = {}, 1
  while i <= #line do
    local c = line:sub(i, i)
    local j
    if line:sub(i, i + 1) == "--" then
      parts[#parts + 1] = { line:sub(i), colors.green }
      break
    elseif c == '"' or c == "'" then
      j = i + 1
      while j <= #line and line:sub(j, j) ~= c do
        if line:sub(j, j) == "\\" then j = j + 1 end
        j = j + 1
      end
      parts[#parts + 1] = { line:sub(i, j), colors.red }
      i = j + 1
    elseif c:match("[%a_]") then
      j = i
      while j <= #line and line:sub(j, j):match("[%w_]") do j = j + 1 end
      local word = line:sub(i, j - 1)
      parts[#parts + 1] = { word, keywords[word] and colors.yellow or colors.white }
      i = j
    elseif c:match("%d") then
      j = i
      while j <= #line and line:sub(j, j):match("[%w%.]") do j = j + 1 end
      parts[#parts + 1] = { line:sub(i, j - 1), colors.lightBlue }
      i = j
    else
      local ch = line:match(UTF8, i) or c        -- целый символ UTF-8
      parts[#parts + 1] = { ch, colors.white }
      i = i + #ch
    end
  end
  return parts
end

local function drawLine(screenY)
  if screenY < 1 or screenY > h - 1 then return end
  local n = screenY + sy
  term.setCursorPos(1, screenY)
  term.setBackgroundColor(colors.black)
  term.clearLine()
  local line = lines[n]
  if not line then return end
  local x = 1 - sx
  for _, part in ipairs(highlight(line)) do
    local len = ulen(part[1])
    if x + len > 0 and x <= w then
      term.setTextColor(part[2])
      term.setCursorPos(x, screenY)
      term.write(part[1])
    end
    x = x + len
  end
end

local function drawStatus()
  term.setCursorPos(1, h)
  if color then
    term.setBackgroundColor(colors.gray)
    term.setTextColor(colors.yellow)
  end
  term.clearLine()
  local right = string.format("%d:%d%s", cy, cx, modified and " *" or "")
  term.write(" " .. status)
  term.setCursorPos(w - #right, h)
  term.write(right)
  term.setBackgroundColor(colors.black)
  term.setTextColor(colors.white)
end

local function redraw()
  for y = 1, h - 1 do drawLine(y) end
  drawStatus()
end

local function placeCursor()
  term.setCursorPos(cx - sx, cy - sy)
end

-- Прокрутить так, чтобы курсор был виден. Возвращает true, если пришлось прокрутить.
local function follow()
  local moved = false
  if cx - sx < 1 then sx = cx - 1; moved = true end
  if cx - sx > w then sx = cx - w; moved = true end
  if cy - sy < 1 then sy = cy - 1; moved = true end
  if cy - sy > h - 1 then sy = cy - (h - 1); moved = true end
  return moved
end

local function clampX()
  cx = math.max(1, math.min(cx, ulen(lines[cy]) + 1))
end

local function save()
  if readOnly then status = "Файл только для чтения"; return end
  local f, err = fs.open(path, "w")
  if not f then status = "Ошибка: " .. tostring(err); return end
  local ok, e = pcall(function()
    f.write(table.concat(lines, "\n"))
    f.close()
  end)
  if ok then
    modified = false
    status = "Сохранено: " .. fs.getName(path)
  else
    status = "Ошибка: " .. tostring(e)
  end
end

local function insert(text)
  if readOnly then return end
  local line = lines[cy]
  lines[cy] = usub(line, 1, cx - 1) .. text .. usub(line, cx)
  cx = cx + ulen(text)
  modified = true
end

term.clear()
term.setCursorBlink(true)
redraw()
placeCursor()

local running = true
while running do
  local ev, a, b, c = os.pullEventRaw()
  local full = false
  local before = cy
  if ev == "char" then
    insert(a)
    -- Набрали end / else / until в начале строки — сдвигаем её на уровень левее предыдущей
    local indent, word = lines[cy]:match("^(%s*)(%a+)$")
    if word and dedent[word] and cx == ulen(lines[cy]) + 1 and cy > 1 then
      local prev = lines[cy - 1]:match("^%s*")
      if #indent >= 2 and #indent >= #prev then
        lines[cy] = lines[cy]:sub(3)
        cx = cx - 2
      end
    end
  elseif ev == "paste" then
    local first = true
    for piece in (a .. "\n"):gmatch("(.-)\r?\n") do
      if not first and not readOnly then
        local line = lines[cy]
        lines[cy] = usub(line, 1, cx - 1)
        table.insert(lines, cy + 1, usub(line, cx))
        cy, cx = cy + 1, 1
      end
      insert(piece)
      first = false
    end
    full = true
  elseif ev == "terminate" then
    running = false
  elseif ev == "mouse_click" then
    -- Клик мышью ставит курсор
    if c < h then
      cy = math.max(1, math.min(#lines, c + sy))
      cx = b + sx
      clampX()
    end
  elseif ev == "mouse_scroll" then
    sy = math.max(0, math.min(math.max(0, #lines - (h - 1)), sy + a * 3))
    cy = math.max(sy + 1, math.min(cy, sy + h - 1))
    clampX()
    full = true
  elseif ev == "key" then
    local ctrl = b
    local len = ulen(lines[cy])
    if ctrl and a == "s" then save()
    elseif ctrl and a == "q" then running = false
    elseif a == "up" then cy = math.max(1, cy - 1); clampX()
    elseif a == "down" then cy = math.min(#lines, cy + 1); clampX()
    elseif a == "left" then
      if cx > 1 then cx = cx - 1 elseif cy > 1 then cy = cy - 1; cx = ulen(lines[cy]) + 1 end
    elseif a == "right" then
      if cx <= len then cx = cx + 1 elseif cy < #lines then cy = cy + 1; cx = 1 end
    elseif a == "home" then cx = 1
    elseif a == "end" then cx = len + 1
    elseif a == "pageUp" then cy = math.max(1, cy - (h - 2)); clampX()
    elseif a == "pageDown" then cy = math.min(#lines, cy + (h - 2)); clampX()
    elseif a == "tab" then insert("  ")
    elseif a == "enter" and not readOnly then
      local line = lines[cy]
      local indent = line:match("^%s*")
      lines[cy] = usub(line, 1, cx - 1)
      table.insert(lines, cy + 1, indent .. usub(line, cx))
      cy, cx = cy + 1, #indent + 1
      modified, full = true, true
    elseif a == "backspace" and not readOnly then
      if cx > 1 then
        local line = lines[cy]
        lines[cy] = usub(line, 1, cx - 2) .. usub(line, cx)
        cx = cx - 1
        modified = true
      elseif cy > 1 then
        cx = ulen(lines[cy - 1]) + 1
        lines[cy - 1] = lines[cy - 1] .. lines[cy]
        table.remove(lines, cy)
        cy = cy - 1
        modified, full = true, true
      end
    elseif a == "delete" and not readOnly then
      local line = lines[cy]
      if cx <= len then
        lines[cy] = usub(line, 1, cx - 1) .. usub(line, cx + 1)
        modified = true
      elseif cy < #lines then
        lines[cy] = line .. lines[cy + 1]
        table.remove(lines, cy + 1)
        modified, full = true, true
      end
    end
  end
  if follow() or full then
    redraw()
  else
    drawLine(before - sy)
    if before ~= cy then drawLine(cy - sy) end
    drawStatus()
  end
  placeCursor()
end

term.setCursorBlink(false)
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)
if modified then print("Изменения не сохранены.") end
