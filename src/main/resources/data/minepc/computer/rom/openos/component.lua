-- OpenOS: библиотека component. Устройства компьютера MineComputers видны как компоненты OpenComputers:
-- computer, gpu, screen, keyboard, eeprom и filesystem для каждого накопителя.
local computer = require("computer")
local component = {}

local proxies = {}           -- адрес -> прокси
local types = {}             -- адрес -> тип
local primaries = {}

local function fsProxy(mountPath, label)
  local p = { type = "filesystem", address = computer.componentAddress("fs:" .. mountPath) }
  local root = "/" .. mountPath
  local function full(path) return fs.combine(root, path or "") end
  local handles, nextHandle = {}, 1
  function p.spaceUsed() local m = fs.getCapacity(root) return m - fs.getFreeSpace(root) end
  function p.spaceTotal() return fs.getCapacity(root) end
  function p.isReadOnly() return fs.isReadOnly(root) end
  function p.getLabel() return fs.getLabel(root) end
  function p.setLabel(v) return fs.getLabel(root) end
  function p.exists(path) return fs.exists(full(path)) end
  function p.isDirectory(path) return fs.isDir(full(path)) end
  function p.size(path) return fs.exists(full(path)) and fs.getSize(full(path)) or 0 end
  function p.lastModified(path) return fs.lastModified(full(path)) end
  function p.makeDirectory(path) fs.makeDir(full(path)) return true end
  function p.remove(path) if not fs.exists(full(path)) then return false end fs.delete(full(path)) return true end
  function p.rename(a, b) fs.move(full(a), full(b)) return true end
  function p.list(path)
    local ok, names = pcall(fs.list, full(path))
    if not ok then return nil, "no such file or directory" end
    local out = {}
    for _, n in ipairs(names) do out[#out + 1] = fs.isDir(fs.combine(full(path), n)) and n .. "/" or n end
    out.n = #out
    return out
  end
  function p.open(path, mode)
    local f, err = io.open(full(path), mode or "r")
    if not f then return nil, err end
    local h = nextHandle
    nextHandle = nextHandle + 1
    handles[h] = f
    return h
  end
  function p.read(h, n) local f = handles[h] if not f then return nil, "bad file descriptor" end return f:read(math.min(n or 2048, 2048)) end
  function p.write(h, data) local f = handles[h] if not f then return nil, "bad file descriptor" end f:write(data) return true end
  function p.seek(h, whence, off) local f = handles[h] if not f then return nil, "bad file descriptor" end return f:seek(whence, off) end
  function p.close(h) local f = handles[h] if f then f:close() handles[h] = nil end end
  return p
end

local function add(proxy)
  proxies[proxy.address] = proxy
  types[proxy.address] = proxy.type
  if not primaries[proxy.type] then primaries[proxy.type] = proxy end
end

--- Пересобрать список компонентов (накопители могли появиться или исчезнуть).
local function refresh()
  for k in pairs(proxies) do proxies[k] = nil end
  for k in pairs(types) do types[k] = nil end
  local keepGpu = primaries.gpu
  for k in pairs(primaries) do primaries[k] = nil end

  add({ type = "computer", address = computer.address(),
        start = function() return false end, stop = function() computer.shutdown() end,
        isRunning = function() return true end, beep = function() end,
        getDeviceInfo = function() return {} end })
  local gpu = keepGpu or computer.gpu()
  add(gpu)
  add({ type = "screen", address = computer.componentAddress("screen"),
        isOn = function() return true end, turnOn = function() return false end, turnOff = function() return false end,
        getAspectRatio = function() return 1, 1 end,
        getKeyboards = function() return { computer.componentAddress("keyboard") } end,
        setPrecise = function() return false end, isPrecise = function() return false end,
        setTouchModeInverted = function() return false end, isTouchModeInverted = function() return false end })
  add({ type = "keyboard", address = computer.componentAddress("keyboard") })
  -- Карта данных (хеши, base64, сжатие) встроена в каждый компьютер
  if data then
    local d = { type = "data", address = computer.componentAddress("data") }
    for k, v in pairs(data) do d[k] = v end
    add(d)
  end
  add({ type = "eeprom", address = computer.componentAddress("eeprom"),
        get = function() return "" end, set = function() end, getLabel = function() return "MineComputers BIOS" end,
        setLabel = function(v) return v end, getSize = function() return 4096 end, getDataSize = function() return 256 end,
        getData = function() return "" end, setData = function() end, getChecksum = function() return "00000000" end,
        makeReadonly = function() return false end })
  for _, m in ipairs(fs.mounts()) do
    local mp = m.path:gsub("^/", "")
    add(fsProxy(mp, m.label))
  end
end
refresh()
component._refresh = refresh

local function listTable(filter, exact)
  local out = {}
  for a, t in pairs(types) do
    if not filter or (exact and t == filter) or (not exact and t:find(filter, 1, true)) then out[a] = t end
  end
  local key = nil
  return setmetatable(out, { __call = function()
    key = next(out, key)
    if key then return key, out[key] end
  end })
end

function component.list(filter, exact)
  if filter == nil then refresh() end
  return listTable(filter, exact)
end

function component.type(address) return types[address] end
function component.slot(address) return types[address] and -1 or nil, types[address] and nil or "no such component" end
function component.proxy(address)
  local p = proxies[address]
  if not p then return nil, "no such component" end
  return p
end
function component.invoke(address, method, ...)
  local p = proxies[address]
  if not p then error("no such component", 2) end
  if type(p[method]) ~= "function" then error("no such method", 2) end
  return p[method](...)
end
function component.methods(address)
  local p = proxies[address]
  if not p then return nil, "no such component" end
  local out = {}
  for k, v in pairs(p) do if type(v) == "function" then out[k] = true end end
  return out
end
function component.fields(address) return {} end
function component.doc(address, method) return nil end
function component.get(abbr, kind)
  for a, t in pairs(types) do
    if a:sub(1, #abbr) == abbr and (not kind or t == kind) then return a end
  end
  return nil, "no such component"
end
function component.isAvailable(kind) return primaries[kind] ~= nil end
function component.getPrimary(kind)
  local p = primaries[kind]
  if not p then error("no primary '" .. tostring(kind) .. "' available", 2) end
  return p
end
function component.setPrimary(kind, address)
  if address == nil then primaries[kind] = nil return end
  local p = proxies[address]
  if p then primaries[kind] = p end
end

-- component.gpu, component.screen и т. д. — основные устройства
return setmetatable(component, { __index = function(_, key)
  local p = primaries[key]
  if p then return p end
end })
