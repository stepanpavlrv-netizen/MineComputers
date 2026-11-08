-- Оболочка: командная строка компьютера.
local dir = ""
local history = {}
local running = true

shell = {}

function shell.dir() return "/" .. dir end
function shell.setDir(d) dir = fs.combine(d, "") end
function shell.resolve(path)
  if path:sub(1, 1) == "/" then return fs.combine(path, "") end
  return fs.combine(dir, path)
end
function shell.exit() running = false end

--- Найти программу по имени: в текущей папке, затем в /rom/programs и /programs.
function shell.resolveProgram(name)
  local candidates = {}
  if name:find("/") then
    local p = shell.resolve(name)
    candidates = { p, p .. ".lua" }
  else
    candidates = {
      fs.combine(dir, name), fs.combine(dir, name .. ".lua"),
      "programs/" .. name .. ".lua", "rom/programs/" .. name .. ".lua",
    }
  end
  for _, p in ipairs(candidates) do
    if fs.exists(p) and not fs.isDir(p) then return p end
  end
  return nil
end

--- Разобрать строку на слова, учитывая кавычки.
local function tokenize(line)
  local words, current, quote = {}, nil, nil
  for ch in line:gmatch(".") do
    if quote then
      if ch == quote then quote = nil else current = (current or "") .. ch end
    elseif ch == '"' or ch == "'" then
      quote = ch
      current = current or ""
    elseif ch:match("%s") then
      if current then words[#words + 1] = current; current = nil end
    else
      current = (current or "") .. ch
    end
  end
  if current then words[#words + 1] = current end
  return words
end

function shell.run(...)
  local words = tokenize(table.concat({ ... }, " "))
  local name = table.remove(words, 1)
  if not name then return true end
  if name == "cd" then
    local target = words[1] and shell.resolve(words[1]) or ""
    if fs.isDir(target) then dir = target else printError("Нет такой папки") end
    return true
  elseif name == "exit" then
    running = false
    return true
  end
  local path = shell.resolveProgram(name)
  if not path then
    printError("Нет такой команды: " .. name .. ". Список команд: help")
    return false
  end
  return os.run({ shell = shell }, path, table.unpack(words))
end

-- Дополнение имён файлов по Tab
local function complete(text)
  local word = text:match("(%S*)$")
  local base = word:match("^(.*/)") or ""
  local prefix = word:sub(#base + 1)
  local folder = shell.resolve(base)
  if not fs.isDir(folder) then return nil end
  local out = {}
  for _, f in ipairs(fs.list(folder)) do
    if f:sub(1, #prefix) == prefix then
      out[#out + 1] = f:sub(#prefix + 1) .. (fs.isDir(fs.combine(folder, f)) and "/" or "")
    end
  end
  return out
end

if term.isColor() then term.setTextColor(colors.lightGray) end
print("Введите help, чтобы увидеть список команд.")
term.setTextColor(colors.white)

while running do
  if term.isColor() then term.setTextColor(colors.yellow) end
  write(shell.dir() .. "> ")
  term.setTextColor(colors.white)
  local ok, line = pcall(read, nil, history, complete)
  if not ok then line = "" end      -- Ctrl+T в командной строке просто сбрасывает ввод
  if line:match("%S") then
    if history[#history] ~= line then history[#history + 1] = line end
    if #history > 50 then table.remove(history, 1) end
    shell.run(line)
  end
end
