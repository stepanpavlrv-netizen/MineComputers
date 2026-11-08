-- OpenOS: библиотека io (файлы и ввод-вывод через экран)
local io = {}

local File = {}
File.__index = File

local function readNumber(self)
  local s = self.data:match("^%s*[-+]?%d*%.?%d+[eE]?[-+]?%d*", self.pos)
  if not s then return nil end
  self.pos = self.pos + #s
  return tonumber(s)
end

local function readOne(self, fmt)
  if type(fmt) == "number" then
    if self.pos > #self.data then return nil end
    local s = self.data:sub(self.pos, self.pos + fmt - 1)
    self.pos = self.pos + #s
    return s
  end
  fmt = tostring(fmt or "l"):gsub("^%*", "")
  local c = fmt:sub(1, 1)
  if c == "a" then
    local s = self.data:sub(self.pos)
    self.pos = #self.data + 1
    return s
  elseif c == "n" then
    return readNumber(self)
  elseif c == "l" or c == "L" then
    if self.pos > #self.data then return nil end
    local e = self.data:find("\n", self.pos, true)
    local line
    if e then
      line = self.data:sub(self.pos, c == "L" and e or e - 1)
      self.pos = e + 1
    else
      line = self.data:sub(self.pos)
      self.pos = #self.data + 1
    end
    if c == "l" then line = line:gsub("\r$", "") end
    return line
  end
  error("bad argument (invalid format)", 3)
end

function File:read(...)
  if self.closed then return nil, "file is closed" end
  if not self.readable then return nil, "bad file descriptor" end
  local n = select("#", ...)
  if n == 0 then return readOne(self, "l") end
  local out = {}
  for i = 1, n do out[i] = readOne(self, (select(i, ...))) end
  return table.unpack(out, 1, n)
end

function File:lines(fmt)
  return function() return self:read(fmt or "l") end
end

function File:write(...)
  if self.closed then return nil, "file is closed" end
  if not self.writable then return nil, "bad file descriptor" end
  for i = 1, select("#", ...) do
    local v = select(i, ...)
    self.buffer[#self.buffer + 1] = tostring(v)
  end
  return self
end

function File:flush()
  if self.writable and #self.buffer > 0 then
    local h, err = fs.open(self.path, self.flushedOnce and "a" or self.mode)
    if not h then return nil, err end
    h.write(table.concat(self.buffer))
    h.close()
    self.buffer = {}
    self.flushedOnce = true
  end
  return self
end

function File:seek(whence, offset)
  whence, offset = whence or "cur", offset or 0
  if self.readable then
    if whence == "set" then self.pos = offset + 1
    elseif whence == "cur" then self.pos = self.pos + offset
    elseif whence == "end" then self.pos = #self.data + 1 + offset end
    self.pos = math.max(1, math.min(self.pos, #self.data + 1))
    return self.pos - 1
  end
  local size = 0
  for _, s in ipairs(self.buffer) do size = size + #s end
  return size
end

function File:setvbuf() return true end

function File:close()
  if self.closed then return nil, "file is closed" end
  self:flush()
  self.closed = true
  return true
end

function io.open(path, mode)
  mode = (mode or "r"):gsub("b", "")
  path = fs.combine(path, "")
  local f = setmetatable({ path = path, mode = mode:sub(1, 1), pos = 1, buffer = {} }, File)
  if mode:sub(1, 1) == "r" then
    if not fs.exists(path) or fs.isDir(path) then return nil, path .. ": no such file or directory" end
    local h = fs.open(path, "r")
    f.data = h.readAll() or ""
    h.close()
    f.readable = true
    f.writable = mode:find("+", 1, true) ~= nil
    if f.writable then f.mode = "w"; f.buffer = { f.data } end
  else
    if fs.isDir(path) then return nil, path .. ": is a directory" end
    local ok, err = pcall(function()
      local h = fs.open(path, mode:sub(1, 1) == "a" and "a" or "w")
      if not h then error("cannot open") end
      h.close()
    end)
    if not ok then return nil, tostring(err) end
    f.mode = mode:sub(1, 1) == "a" and "a" or "w"
    f.flushedOnce = f.mode == "w"   -- файл уже очищен — дальше только дописываем
    f.mode = "a"
    f.writable = true
  end
  return f
end

-- Вывод на экран и ввод с клавиатуры
local Out = {}
Out.__index = Out
local nativeTerm = term     -- экран MineComputers (у модуля term из OpenOS другие функции)
function Out:write(...)
  local ocTerm = require("term")
  local old
  if self.err and nativeTerm.isColor() then
    old = nativeTerm.getTextColorRGB()
    nativeTerm.setTextColorRGB(0xFF5555)
  end
  for i = 1, select("#", ...) do ocTerm.write(tostring((select(i, ...))), true) end
  if old then nativeTerm.setTextColorRGB(old) end
  return self
end
function Out:flush() return self end
function Out:close() return true end
function Out:setvbuf() return true end

io.stdout = setmetatable({}, Out)
io.stderr = setmetatable({ err = true }, Out)

local In = {}
In.__index = In
function In:read(fmt)
  fmt = tostring(fmt or "l"):gsub("^%*", "")
  local line = read()
  if fmt:sub(1, 1) == "n" then return tonumber(line) end
  if fmt:sub(1, 1) == "L" then return line .. "\n" end
  return line
end
function In:lines() return function() return self:read("l") end end
function In:close() return true end
io.stdin = setmetatable({}, In)

function io.write(...) return io.stdout:write(...) end
function io.read(...) return io.stdin:read(...) end
function io.lines(path, fmt)
  if not path then return io.stdin:lines() end
  local f, err = io.open(path, "r")
  if not f then error(err, 2) end
  return function()
    local l = f:read(fmt or "l")
    if l == nil then f:close() end
    return l
  end
end
function io.input(f) if f then io.stdin = type(f) == "string" and io.open(f, "r") or f end return io.stdin end
function io.output(f) if f then io.stdout = type(f) == "string" and io.open(f, "w") or f end return io.stdout end
function io.close(f) return (f or io.stdout):close() end
function io.type(f)
  if type(f) ~= "table" then return nil end
  if getmetatable(f) == File then return f.closed and "closed file" or "file" end
  return nil
end

return io
