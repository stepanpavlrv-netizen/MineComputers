-- OpenOS: библиотека event. События MineComputers переводятся в сигналы OpenComputers:
-- char/key -> key_down, key_up; mouse_click/drag/up/scroll -> touch/drag/drop/scroll; paste -> clipboard.
local computer = require("computer")
local keyboard = require("keyboard")
local event = { handlers = {} }

local PLAYER = "player"
local function addr(kind) return computer.componentAddress(kind) end

-- Перевести событие MineComputers в сигнал OpenComputers (или nil, если его надо пропустить)
local function translate(ev)
  local name = ev[1]
  if name == "char" then
    local cp = unicode.char and utf8 and utf8.codepoint and utf8.codepoint(ev[2]) or ev[2]:byte()
    local code = keyboard.native[unicode.lower(ev[2])] or 0
    if ev[2] == " " then code = 57 end
    return { "key_down", addr("keyboard"), cp, code, PLAYER }
  elseif name == "key" then
    local key, ctrl = ev[2], ev[3]
    local code = keyboard.native[key] or 0
    keyboard.pressedCodes[code] = true
    -- Буквы, цифры и пробел без Ctrl придут следом как «char» с символом
    if not ctrl and (#key == 1 or key == "space") then return nil end
    local char = 0
    if key == "enter" then char = 13 elseif key == "backspace" then char = 8 elseif key == "tab" then char = 9
    elseif ctrl and #key == 1 and key:match("%a") then char = key:byte() - 96 end
    return { "key_down", addr("keyboard"), char, code, PLAYER }
  elseif name == "key_up" then
    local code = keyboard.native[ev[2]] or 0
    keyboard.pressedCodes[code] = nil
    return { "key_up", addr("keyboard"), 0, code, PLAYER }
  elseif name == "paste" then
    return { "clipboard", addr("keyboard"), ev[2], PLAYER }
  elseif name == "mouse_click" then
    return { "touch", addr("screen"), ev[3], ev[4], ev[2] - 1, PLAYER }
  elseif name == "mouse_drag" then
    return { "drag", addr("screen"), ev[3], ev[4], ev[2] - 1, PLAYER }
  elseif name == "mouse_up" then
    return { "drop", addr("screen"), ev[3], ev[4], ev[2] - 1, PLAYER }
  elseif name == "mouse_scroll" then
    return { "scroll", addr("screen"), ev[3], ev[4], -ev[2], PLAYER }
  elseif name == "disk" then
    return { "component_added", addr("fs:" .. ev[2]:gsub("^/", "")), "filesystem" }
  elseif name == "disk_eject" then
    return { "component_removed", addr("fs:" .. ev[2]:gsub("^/", "")), "filesystem" }
  elseif name == "terminate" then
    return { "interrupted", computer.uptime() }
  elseif name == "screen_resized" then
    return { "screen_resized", addr("screen"), ev[2], ev[3] }
  elseif name == "net_message" then
    -- Сеть MineComputers -> modem_message OpenComputers: получатель, отправитель, порт, «расстояние», данные
    local out = { "modem_message", addr("modem"), ev[2], ev[3], ev[4] }
    local n = ev.n or #ev
    for i = 5, n do out[i + 1] = ev[i] end
    out.n = math.max(5, n + 1)          -- на одно поле больше, чем у net_message (адрес получателя)
    return out
  end
  return ev
end

-- Таймеры event.timer работают на таймерах MineComputers
local timers = {}

local function dispatch(sig)
  local name = sig[1]
  if name == "timer" and timers[sig[2]] then
    local t = timers[sig[2]]
    timers[sig[2]] = nil
    t.times = t.times - 1
    if t.times > 0 then
      local id = os.startTimer(t.interval)
      timers[id] = t
      t.id = id
    end
    local ok, err = pcall(t.callback)
    if not ok then event.onError(err) end
    return true            -- служебный таймер, программе не показываем
  end
  local list = event.handlers[name]
  if list then
    for i = #list, 1, -1 do
      local ok, res = pcall(list[i], table.unpack(sig, 1, sig.n or #sig))
      if not ok then event.onError(res)
      elseif res == false then table.remove(list, i) end
    end
  end
  return false
end

function event.onError(msg)
  pcall(function()
    local f = io and io.open and io.open("/tmp/event.log", "a")
    if f then f:write(tostring(msg) .. "\n"); f:close() end
  end)
end

--- Следующий сигнал с таймаутом (в секундах). nil — ждать бесконечно.
function event.pullRaw(timeout)
  if timeout and timeout <= 0 then
    -- Как в OpenComputers: pull(0) отдаёт то, что уже пришло, и не ждёт ни такта
    while computer.pending() > 0 do
      local raw = table.pack(os.pullEventRaw())
      local sig = translate(raw)
      if sig then
        sig.n = sig.n or #sig
        if not dispatch(sig) then return sig end
      end
    end
    return nil
  end
  local tid
  if timeout then tid = os.startTimer(math.max(0, timeout)) end
  while true do
    local raw = table.pack(os.pullEventRaw())
    if raw[1] == "timer" and raw[2] == tid then return nil end
    local sig = translate(raw)
    if sig then
      sig.n = sig.n or #sig
      if not dispatch(sig) then
        if tid then os.cancelTimer(tid) end
        return sig
      end
    end
  end
end

local function matches(sig, name, ...)
  if name and not (type(sig[1]) == "string" and sig[1]:match("^" .. name .. "$")) then return false end
  local want = table.pack(...)
  for i = 1, want.n do
    if want[i] ~= nil and want[i] ~= sig[i + 1] then return false end
  end
  return true
end

function event.pull(...)
  local args = table.pack(...)
  local timeout
  if type(args[1]) == "number" then timeout = table.remove(args, 1); args.n = args.n - 1 end
  local deadline = timeout and computer.uptime() + timeout
  while true do
    local left = deadline and math.max(0, deadline - computer.uptime())
    local sig = event.pullRaw(left)
    if not sig then return nil end
    if sig[1] == "interrupted" then error("interrupted", 0) end
    if matches(sig, table.unpack(args, 1, args.n)) then return table.unpack(sig, 1, sig.n) end
  end
end

function event.pullFiltered(...)
  local args = table.pack(...)
  local timeout, filter = nil, args[1]
  if type(args[1]) == "number" then timeout, filter = args[1], args[2] end
  local deadline = timeout and computer.uptime() + timeout
  while true do
    local left = deadline and math.max(0, deadline - computer.uptime())
    local sig = event.pullRaw(left)
    if not sig then return nil end
    if not filter or filter(table.unpack(sig, 1, sig.n)) then return table.unpack(sig, 1, sig.n) end
  end
end

function event.pullMultiple(...)
  local names = table.pack(...)
  local timeout
  if type(names[1]) == "number" then timeout = table.remove(names, 1); names.n = names.n - 1 end
  return event.pullFiltered(timeout, function(name)
    for i = 1, names.n do if name:match("^" .. names[i] .. "$") then return true end end
    return false
  end)
end

function event.listen(name, callback)
  event.handlers[name] = event.handlers[name] or {}
  for _, f in ipairs(event.handlers[name]) do if f == callback then return false end end
  table.insert(event.handlers[name], callback)
  return true
end

function event.ignore(name, callback)
  local list = event.handlers[name]
  if not list then return false end
  for i, f in ipairs(list) do
    if f == callback then table.remove(list, i); return true end
  end
  return false
end

function event.timer(interval, callback, times)
  times = times or 1
  if times == math.huge then times = 1e9 end
  local id = os.startTimer(interval)
  timers[id] = { interval = interval, callback = callback, times = times, id = id }
  return id
end

function event.cancel(id)
  local t = timers[id]
  for k, v in pairs(timers) do
    if v.id == id or k == id then timers[k] = nil; os.cancelTimer(k); return true end
  end
  return t ~= nil
end

function event.push(name, ...)
  os.queueEvent(name, ...)
  return true
end

computer.pullSignal = function(timeout)
  local sig = event.pullRaw(timeout)
  if sig then return table.unpack(sig, 1, sig.n) end
end

return event
