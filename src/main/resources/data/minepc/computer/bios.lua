-- BIOS MineComputers: первые функции системы и запуск оболочки.
-- Всё, что здесь объявлено глобально, доступно любой программе.

local native_yield = coroutine.yield

-- ===================== Цена встроенных функций =====================
-- string.rep, string.sub, table.concat и т. п. списывают команды по объёму работы прямо в Java.
-- Функции, которые могут вызывать Lua-код (format с __tostring, gsub с функцией, sort с comparator, load
-- с функцией-читателем), оборачиваем здесь: цена списывается заранее, а сам вызов — хвостовой.
do
  local charge = computer.__charge
  computer.__charge = nil
  local BPO = 16    -- байт на одну команду
  local format, gsub, sort, rawload = string.format, string.gsub, table.sort, load
  local floor, log = math.floor, math.log
  function string.format(fmt, ...)
    if type(fmt) == "string" then charge(floor(#fmt / BPO) + select("#", ...)) end
    return format(fmt, ...)
  end
  function string.gsub(s, pattern, repl, n)
    if type(s) == "string" then charge(floor(#s / BPO)) end
    return gsub(s, pattern, repl, n)
  end
  function table.sort(t, comp)
    if type(t) == "table" then
      local n = #t
      if n > 1 then charge(floor(n * log(n) / log(2) / 4)) end
    end
    return sort(t, comp)
  end
  function load(chunk, ...)
    if type(chunk) == "string" then charge(floor(#chunk / 4)) end
    return rawload(chunk, ...)
  end
end

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
local ulen, usub = unicode.len, unicode.sub
function write(text)
  text = tostring(text)
  local w, h = term.getSize()
  local lines = 0
  while #text > 0 do
    local ws = text:match("^[ \t]+")
    if ws then
      -- табуляция — пробелы до следующей позиции, кратной 8
      for ch in ws:gmatch(".") do
        local x = term.getCursorPos()
        if ch == "\t" then term.write(string.rep(" ", 8 - (x - 1) % 8)) else term.write(ch) end
      end
      text = text:sub(#ws + 1)
    end
    if text:match("^\n") then
      newline(); lines = lines + 1
      text = text:sub(2)
    else
      local word = text:match("^[^ \t\n]+")
      if word then
        text = text:sub(#word + 1)
        local x = term.getCursorPos()
        local n = ulen(word)
        if n > w then
          -- Слово длиннее строки: режем как есть
          while n > 0 do
            x = term.getCursorPos()
            local room = w - x + 1
            if room <= 0 then newline(); lines = lines + 1; room = w end
            term.write(usub(word, 1, room))
            word = usub(word, room + 1)
            n = ulen(word)
          end
        else
          if x + n - 1 > w then newline(); lines = lines + 1 end
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
  term.setTextColor(old or colors.white)
end

-- ===================== Ввод строки =====================
--- Прочитать строку с клавиатуры. replace — символ вместо букв (для паролей), history — список.
function read(replace, history, completeFn, default)
  term.setCursorBlink(true)
  local line = default or ""
  local pos = ulen(line)
  local histPos
  local w = term.getSize()
  local sx, sy = term.getCursorPos()

  local function redraw(clear)
    local scroll = math.max(0, sx + pos - w)
    term.setCursorPos(sx, sy)
    local shown = usub(line, scroll + 1, scroll + w - sx + 1)
    if replace then shown = string.rep(replace, ulen(shown)) end
    term.write(shown)
    if clear then term.write(string.rep(" ", math.max(0, w - sx - ulen(shown) + 2))) end
    term.setCursorPos(sx + pos - scroll, sy)
  end

  local function insert(text)
    line = usub(line, 1, pos) .. text .. usub(line, pos + 1)
    pos = pos + ulen(text)
    redraw()
  end

  while true do
    local ev, a = os.pullEvent()
    if ev == "char" then
      insert(a)
    elseif ev == "paste" then
      insert((a:gsub("[\r\n].*", "")))
    elseif ev == "key" then
      local n = ulen(line)
      if a == "enter" then
        break
      elseif a == "left" and pos > 0 then pos = pos - 1; redraw()
      elseif a == "right" and pos < n then pos = pos + 1; redraw()
      elseif a == "home" then pos = 0; redraw()
      elseif a == "end" then pos = n; redraw()
      elseif a == "backspace" and pos > 0 then
        line = usub(line, 1, pos - 1) .. usub(line, pos + 1); pos = pos - 1; redraw(true)
      elseif a == "delete" and pos < n then
        line = usub(line, 1, pos) .. usub(line, pos + 2); redraw(true)
      elseif (a == "up" or a == "down") and history and #history > 0 then
        if a == "up" then
          histPos = histPos and math.max(1, histPos - 1) or #history
        else
          histPos = histPos and histPos + 1 or nil
          if histPos and histPos > #history then histPos = nil end
        end
        line = histPos and history[histPos] or ""
        pos = ulen(line)
        redraw(true)
      elseif a == "tab" and completeFn then
        local options = completeFn(usub(line, 1, pos))
        if options and #options == 1 then insert(options[1]) end
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
function loadfile(path, mode, env)
  if type(mode) == "table" and env == nil then env, mode = mode, nil end
  local f, err = fs.open(path, "r")
  if not f then return nil, err end
  local src = f.readAll() or ""
  f.close()
  if src:sub(1, 1) == "#" then src = "--" .. src end   -- строка #!/... в начале файла
  return load(src, "@" .. fs.combine(path, ""), mode or "t", env or _G)
end

function dofile(path)
  local fn, err = loadfile(path)
  if not fn then error(err, 2) end
  return fn()
end

--- Запустить файл программы с аргументами. Возвращает true, если всё прошло без ошибок.
function os.run(env, path, ...)
  if getmetatable(env) == nil then setmetatable(env, { __index = _G }) end
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

-- ===================== Модули (require) и совместимость с OpenOS =====================
-- Программы для OpenComputers пишут require("component"), require("event") и т. п. —
-- такие модули лежат в /rom/openos и ведут себя как в OpenOS.
package = {
  loaded = { _G = _G, string = string, table = table, math = math, coroutine = coroutine,
             bit32 = bit32, utf8 = utf8, debug = debug, unicode = unicode, computer = computer },
  preload = {},
  path = "./?.lua;/lib/?.lua;/usr/lib/?.lua;/home/lib/?.lua;/rom/openos/?.lua",
  config = "/\n;\n?\n!\n-\n",
}

function package.searchpath(name, path, sep, rep)
  sep, rep = sep or ".", rep or "/"
  local fname = name:gsub("%" .. sep, rep)
  local tried = {}
  for pattern in path:gmatch("[^;]+") do
    local p = pattern:gsub("%?", fname)
    if p:sub(1, 2) == "./" then
      p = fs.combine(_NATIVE_SHELL and _NATIVE_SHELL.dir() or "", p:sub(3))
    end
    if fs.exists(p) and not fs.isDir(p) then return "/" .. fs.combine(p, "") end
    tried[#tried + 1] = "\n\tno file '" .. p .. "'"
  end
  return nil, table.concat(tried)
end

local loading = {}
function require(name)
  if package.loaded[name] ~= nil then return package.loaded[name] end
  if package.preload[name] then
    local r = package.preload[name](name)
    package.loaded[name] = package.loaded[name] or (r == nil and true or r)
    return package.loaded[name]
  end
  local path, err = package.searchpath(name, package.path)
  if not path then error("module '" .. name .. "' not found:" .. err, 2) end
  if loading[name] then error("loop or previous error loading module '" .. name .. "'", 2) end
  loading[name] = true
  local fn, lerr = loadfile(path)
  if not fn then loading[name] = nil; error("error loading module '" .. name .. "' from file '" .. path .. "':\n\t" .. lerr, 2) end
  local ok, r = pcall(fn, name, path)
  loading[name] = nil
  if not ok then error(r, 2) end
  if package.loaded[name] == nil then package.loaded[name] = (r == nil) and true or r end
  return package.loaded[name]
end

--- Проверка типа аргумента, как в OpenComputers: checkArg(1, x, "string", "nil")
function checkArg(n, have, ...)
  have = type(have)
  for i = 1, select("#", ...) do
    if have == select(i, ...) then return end
  end
  error(string.format("bad argument #%d (%s expected, got %s)", n, table.concat({ ... }, " or "), have), 3)
end

-- Дополнения os из OpenOS
local env = { HOME = "/home", PATH = "/bin:/usr/bin:/home/bin:.", SHELL = "/rom/shell.lua", TMPDIR = "/tmp" }
function os.getenv(name)
  if name == nil then return env end
  if name == "PWD" then return _NATIVE_SHELL and _NATIVE_SHELL.dir() or "/" end
  return env[name]
end
function os.setenv(name, value) env[name] = value; return value end
function os.remove(path)
  local ok, err = pcall(fs.delete, path)
  if not ok then return nil, err end
  return true
end
function os.rename(a, b)
  local ok, err = pcall(fs.move, a, b)
  if not ok then return nil, err end
  return true
end
function os.tmpname()
  pcall(fs.makeDir, "/tmp")
  return "/tmp/" .. string.format("%08x", math.random(0, 0x7fffffff))
end
function os.exit() error("exit", 0) end

io = require("io")
component = require("component")

-- ===================== Отдельный процесс (computer.spawn) =====================
-- Процесс на другом ядре: та же система, но вместо командной строки — одна программа.
if _PROCESS then
  local p = _PROCESS
  _PROCESS = nil
  local fn, err = loadfile(p.path)
  if not fn then error(err, 0) end
  return fn(table.unpack(p.args, 1, p.args.n or #p.args))
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
  _NATIVE_SHELL = nil
  local ok = os.run({}, "/rom/shell.lua")
  if ok then
    os.shutdown()       -- команда exit в оболочке выключает компьютер
    os.pullEventRaw()
  end
  print("Оболочка завершилась с ошибкой, перезапуск...")
  os.sleep(1)
end
