-- Контроллер ввода-вывода: редстоун и датчики по сети.
--   ioc.get(кто)                  → состояние (таблица) или nil, ошибка
--   ioc.set(кто, сторона, уровень) → выход 0..15 (сторона станет выходом)
--   ioc.pulse(кто, сторона, [уровень], [тиков]) → импульс (по умолчанию 15 на 10 тиков)
--   ioc.mode(кто, сторона, "in"|"out")
--   ioc.watch(кто) / ioc.unwatch(кто) → события io_change(адрес, сторона, уровень, было),
--                                       io_player(адрес, игрок, пришёл)
--   ioc.list()                     → контроллеры в сети {имя, адрес, переходов}
-- Стороны: north, south, east, west, up, down (или север, юг, восток, запад, верх, низ).
local ioc = {}

local function request(target, cmd, ...)
  if net.speed() == 0 then return nil, "Нет сети: подключите кабель или Wi-Fi" end
  local id = net.io(target, cmd, ...)
  if not id then return nil, "Нет сети" end
  local timer = os.startTimer(2)
  local skipped = {}
  local result, err
  while true do
    local ev = table.pack(os.pullEventRaw())
    if (ev[1] == "io_state" or ev[1] == "io_error") and ev[3] == id then
      if ev[1] == "io_error" then err = ev[4] else result = ev[4] end
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      err = "«" .. tostring(target) .. "» не отвечает: нет в сети или это не контроллер"
      break
    elseif ev[1] == "terminate" then
      err = "Прервано"
      break
    else
      skipped[#skipped + 1] = ev
    end
  end
  os.cancelTimer(timer)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  if result then return result end
  return nil, err
end

function ioc.get(target) return request(target, "get") end
function ioc.set(target, side, level) return request(target, "set", side, level or 15) end
function ioc.pulse(target, side, level, ticks) return request(target, "pulse", side, level or 15, ticks or 10) end
function ioc.mode(target, side, mode) return request(target, "mode", side, mode) end
function ioc.watch(target) return request(target, "watch") end
function ioc.unwatch(target) return request(target, "unwatch") end
function ioc.name(target, name) return request(target, "name", name) end

--- Контроллеры в сети: спрашивает всех соседей сразу и ждёт ответы секунду.
function ioc.list()
  local out, ids, hops = {}, {}, {}
  if net.speed() == 0 then return out end
  for _, p in ipairs(net.peers()) do
    local id = net.io(p.address, "get")
    if id then ids[id] = true; hops[id] = p.hops end
  end
  local timer = os.startTimer(1)
  local skipped = {}
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "io_state" and ids[ev[3]] then
      out[#out + 1] = { name = ev[4].name, address = ev[4].address, hops = hops[ev[3]] }
    elseif ev[1] == "timer" and ev[2] == timer then
      break
    elseif ev[1] == "terminate" then
      break
    elseif ev[1] ~= "io_error" then
      skipped[#skipped + 1] = ev
    end
  end
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  table.sort(out, function(x, y) return x.hops < y.hops end)
  return out
end

ioc.sides = { "north", "south", "west", "east", "up", "down" }
return ioc
