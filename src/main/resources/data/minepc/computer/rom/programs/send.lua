-- Передать файл по сети: send <кому> <файл> [порт]. На том компьютере должна работать программа recv
local target, path, port = ...
if not path then print("Использование: send <имя или адрес> <файл> [порт]") return end
port = tonumber(port or "") or 21
path = shell.resolve(path)
if not fs.exists(path) or fs.isDir(path) then printError("Нет такого файла") return end
if net.speed() == 0 then print("Нет сети: подключите кабель или Wi-Fi") return end

local f = fs.open(path, "rb")
local content = f.readAll()
f.close()
local id = math.random(1, 1000000000)
local name = fs.getName(path)
local opened = not net.isOpen(port)
net.open(port)

local function wait(kind, seconds)
  local timer = os.startTimer(seconds)
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "net_message" and ev[3] == port and ev[5] == kind and ev[6] == id then return ev end
    if ev[1] == "timer" and ev[2] == timer then return nil end
  end
end

local function finish()
  if opened then net.close(port) end
end

net.send(target, port, "file_begin", id, name, #content)
local ok = wait("file_ok", 3)
if not ok then
  printError("Нет ответа. На том компьютере запустите recv" .. (port ~= 21 and (" " .. port) or ""))
  finish()
  return
end
local who = ok[2]
print("Отправляю " .. name .. " (" .. #content .. " байт) → " .. who:sub(1, 8))

local CHUNK = 4096
local parts = math.max(1, math.ceil(#content / CHUNK))
local started = os.clock()
local _, y = term.getCursorPos()
for i = 1, parts do
  net.send(who, port, "file_part", id, i, content:sub((i - 1) * CHUNK + 1, i * CHUNK))
  if i % 4 == 0 or i == parts then
    term.setCursorPos(1, y)
    term.clearLine()
    write(string.format("%d%%", math.floor(i * 100 / parts)))
  end
end
net.send(who, port, "file_end", id, parts, data.crc32(content))
local done = wait("file_done", 5)
print()
if done and done[7] then
  local seconds = math.max(0.05, os.clock() - started)
  print(string.format("Готово: %d байт за %.1f с (%.1f КБ/с)", #content, seconds, #content / 1024 / seconds))
elseif done then
  printError("Файл дошёл повреждённым: " .. tostring(done[8]))
else
  printError("Нет подтверждения от получателя")
end
finish()
