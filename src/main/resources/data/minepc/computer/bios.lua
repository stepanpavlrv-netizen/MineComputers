-- BIOS MineComputers: первые функции системы и запуск оболочки.
-- Всё, что здесь объявлено глобально, доступно любой программе.

local native_yield = coroutine.yield

-- ===================== Цвета (номера палитры экрана) =====================
colors = {
  white = 0, orange = 1, magenta = 2, lightBlue = 3, yellow = 4, lime = 5, pink = 6, gray = 7,
  lightGray = 8, cyan = 9, purple = 10, blue = 11, brown = 12, green = 13, red = 14, black = 15,
}
colours = colors
term.setTextColour = term.setTextColor
term.setBackgroundColour = term.setBackgroundColor
term.getTextColour = term.getTextColor
term.getBackgroundColour = term.getBackgroundColor
term.isColour = term.isColor

-- ===================== События =====================
function os.pullEventRaw(filter)
  return native_yield(filter)
end

function os.pullEvent(filter)
  local ev = table.pack(os.pullEventRaw(filter))
  if ev[1] == "terminate" then error("Прервано", 0) end
  return table.unpack(ev, 1, ev.n)
end

function os.sleep(seconds)
  local id = os.startTimer(seconds or 0)
  repeat local _, t = os.pullEvent("timer") until t == id
end
sleep = os.sleep

-- ===================== Вывод текста =====================
local function newline()
  local w, h = term.getSize()
  local _, y = term.getCursorPos()
  if y >= h then
    term.scroll(1)
    term.setCursorPos(1, h)
  else
    term.setCursorPos(1, y + 1)
  end
end

--- Написать текст с переносом слов и прокруткой. Возвращает число перенесённых строк.
function write(text)
  text = tostring(text)
  local w, h = term.getSize()
  local lines = 0
  local x, y = term.getCursorPos()
  while #text > 0 do
    local ws = text:match("^[ \t]+")
    if ws then
      term.write(ws)
      x = x + #ws
      text = text:sub(#ws + 1)
    end
    local nl = text:match("^\n")
    if nl then
      newline(); lines = lines + 1
      text = text:sub(2)
    else
      local word = text:match("^[^ \t\n]+")
      if word then
        text = text:sub(#word + 1)
        x = select(1, term.getCursorPos())
        if #word > w then
          -- Слово длиннее строки: режем как есть
          while #word > 0 do
            x = select(1, term.getCursorPos())
            local room = w - x + 1
            if room <= 0 then newline(); lines = lines + 1; room = w end
            term.write(word:sub(1, room))
            word = word:sub(room + 1)
          end
        else
          if x + #word - 1 > w then newline(); lines = lines + 1 end
          term.write(word)
        end
      end
    end
  end
  return lines
end

function print(...)
  local parts = {}
  local n = select("#", ...)
  for i = 1, n do parts[i] = tostring((select(i, ...))) end
  local lines = write(table.concat(parts, "\t"))
  newline()
  return lines + 1
end

function printError(...)
  local old = term.getTextColor()
  if term.isColor() then term.setTextColor(colors.red) end
  print(...)
  term.setTextColor(old)
end

-- ===================== Ввод строки =====================
--- Прочитать строку с клавиатуры. replace — символ вместо букв (для паролей), history — список.
function read(replace, history, completeFn, default)
  term.setCursorBlink(true)
  local line = default or ""
  local pos = #line
  local histPos
  local w = term.getSize()
  local sx, sy = term.getCursorPos()

  local function redraw(clear)
    local scroll = math.max(0, sx + pos - w)
    term.setCursorPos(sx, sy)
    local shown = line:sub(scroll + 1, scroll + w - sx + 1)
    if replace then shown = string.rep(replace, #shown) end
    term.write(shown)
    if clear then term.write(string.rep(" ", w - sx - #shown + 2)) end
    term.setCursorPos(sx + pos - scroll, sy)
  end

  while true do
    local ev, a, ctrl = os.pullEvent()
    if ev == "char" then
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + #a
      redraw()
    elseif ev == "paste" then
      a = a:gsub("[\r\n].*", "")
      line = line:sub(1, pos) .. a .. line:sub(pos + 1)
      pos = pos + #a
      redraw()
    elseif ev == "key" then
      if a == "enter" then
        break
      elseif a == "left" and pos > 0 then pos = pos - 1; redraw()
      elseif a == "right" and pos < #line then pos = pos + 1; redraw()
      elseif a == "home" then pos = 0; redraw()
      elseif a == "end" then pos = #line; redraw()
      elseif a == "backspace" and pos > 0 then
        line = line:sub(1, pos - 1) .. line:sub(pos + 1); pos = pos - 1; redraw(true)
      elseif a == "delete" and pos < #line then
        line = line:sub(1, pos) .. line:sub(pos + 2); redraw(true)
      elseif (a == "up" or a == "down") and history and #history > 0 then
        if a == "up" then
          histPos = histPos and math.max(1, histPos - 1) or #history
        else
          histPos = histPos and histPos + 1 or nil
          if histPos and histPos > #history then histPos = nil end
        end
        line = histPos and history[histPos] or ""
        pos = #line
        redraw(true)
      elseif a == "tab" and completeFn then
        local options = completeFn(line:sub(1, pos))
        if options and #options == 1 then
          line = line:sub(1, pos) .. options[1] .. line:sub(pos + 1)
          pos = pos + #options[1]
          redraw()
        end
      end
    end
  end
  term.setCursorBlink(false)
  newline()
  return line
end

-- ===================== Пути =====================
function fs.combine(a, b)
  local path = (a or "") .. "/" .. (b or "")
  local parts = {}
  for part in path:gmatch("[^/]+") do
    if part == ".." then
      table.remove(parts)
    elseif part ~= "." then
      parts[#parts + 1] = part
    end
  end
  return table.concat(parts, "/")
end

function fs.getName(path)
  path = fs.combine(path, "")
  return path:match("([^/]+)$") or "root"
end

function fs.getDir(path)
  path = fs.combine(path, "")
  return path:match("^(.*)/[^/]+$") or ""
end

-- ===================== Загрузка программ =====================
function loadfile(path, env)
  local f, err = fs.open(path, "r")
  if not f then return nil, err end
  local src = f.readAll()
  f.close()
  return load(src, "@" .. fs.getName(path), "t", env or _G)
end

function dofile(path)
  local fn, err = loadfile(path)
  if not fn then error(err, 2) end
  return fn()
end

--- Запустить файл программы с аргументами. Возвращает true, если всё прошло без ошибок.
function os.run(env, path, ...)
  setmetatable(env, { __index = _G })
  local fn, err = loadfile(path, env)
  if not fn then
    printError(err)
    return false
  end
  local ok, e = pcall(fn, ...)
  if not ok then
    if e and e ~= "" then printError(e) end
    return false
  end
  return true
end

-- Простая сериализация таблиц (для сохранения настроек и вывода в lua)
textutils = {}
function textutils.serialize(v, indent)
  indent = indent or ""
  local t = type(v)
  if t == "table" then
    local out = { "{" }
    for k, val in pairs(v) do
      local key = type(k) == "string" and k:match("^[%a_][%w_]*$") and k or "[" .. textutils.serialize(k) .. "]"
      out[#out + 1] = indent .. "  " .. key .. " = " .. textutils.serialize(val, indent .. "  ") .. ","
    end
    out[#out + 1] = indent .. "}"
    return table.concat(out, "\n")
  elseif t == "string" then
    return string.format("%q", v)
  else
    return tostring(v)
  end
end

function textutils.formatSize(bytes)
  if bytes >= 1048576 then return string.format("%.1f МБ", bytes / 1048576) end
  if bytes >= 1024 then return string.format("%.1f КБ", bytes / 1024) end
  return bytes .. " Б"
end

-- ===================== Запуск =====================
term.clear()
term.setCursorPos(1, 1)
local info = computer.info()
if term.isColor() then term.setTextColor(colors.yellow) end
print("MineComputers BIOS")
term.setTextColor(colors.white)
print(info.cpu .. ", " .. info.architecture)
print("Память: " .. info.ram .. " КБ, скорость: " .. info.opsPerSecond .. " команд/с")

if not fs.exists("/rom/shell.lua") then
  printError("Нет оболочки /rom/shell.lua")
  while true do os.pullEvent() end
end

if fs.exists("/startup.lua") then
  os.run({}, "/startup.lua")
end

while true do
  local ok = os.run({}, "/rom/shell.lua")
  if ok then
    os.shutdown()       -- команда exit в оболочке выключает компьютер
    os.pullEventRaw()
  end
  print("Оболочка завершилась с ошибкой, перезапуск...")
  os.sleep(1)
end
