-- Процессы на ядрах процессора: программы работают параллельно и обмениваются сообщениями.
--
--   local cores = require("cores")
--   local pid = cores.spawn("worker.lua", 1, 1000)   -- запустить на свободном ядре
--   cores.send(pid, "привет", { a = 1 })             -- числа, строки, таблицы (копируются)
--   local from, msg = cores.receive(5)               -- ждать сообщение до 5 секунд
--   local ok, err = cores.wait(pid)                  -- дождаться завершения
--
-- У каждого процесса свои переменные (общих таблиц нет — только сообщения), но экран, файлы
-- и память компьютера общие. Процессов больше, чем ядер, — они делят ядра между собой.
local cores = {}

function cores.count() return computer.limits().cores end
cores.send = computer.send
cores.kill = computer.kill
cores.list = computer.processes
cores.pid = computer.pid
cores.parent = computer.parentPid

--- Запустить программу отдельным процессом. Путь — как в командной строке (можно без .lua).
function cores.spawn(path, ...)
  local p = path
  if p:sub(1, 1) ~= "/" and _NATIVE_SHELL then p = _NATIVE_SHELL.resolve(p) end
  if not fs.exists(p) and fs.exists(p .. ".lua") then p = p .. ".lua" end
  return computer.spawn(p, ...)
end

-- Сообщения и завершения процессов, пришедшие, пока ждали что-то другое, не теряются
local pending = {}

local function take(match)
  for i, ev in ipairs(pending) do
    if match(ev) then
      table.remove(pending, i)
      return ev
    end
  end
end

local function waitFor(match, timeout)
  local ev = take(match)
  if ev then return ev end
  local timer = timeout and os.startTimer(timeout)
  while true do
    ev = table.pack(os.pullEvent())
    if ev[1] == "timer" and ev[2] == timer then return nil end
    if ev[1] == "process_message" or ev[1] == "process_exit" then
      if match(ev) then
        if timer then os.cancelTimer(timer) end
        return ev
      end
      if #pending < 1000 then pending[#pending + 1] = ev end
    end
  end
end

--- Ждать сообщение (от процесса from или от любого). Возвращает номер отправителя и значения или nil.
function cores.receive(timeout, from)
  local ev = waitFor(function(e) return e[1] == "process_message" and (from == nil or e[2] == from) end, timeout)
  if ev then return table.unpack(ev, 2, ev.n) end
end

--- Дождаться завершения процесса (запущенного этим процессом). Возвращает ok и текст ошибки.
function cores.wait(pid, timeout)
  local ev = waitFor(function(e) return e[1] == "process_exit" and e[2] == pid end, timeout)
  if not ev then return nil, "timeout" end
  return ev[3], ev[4]
end

return cores
