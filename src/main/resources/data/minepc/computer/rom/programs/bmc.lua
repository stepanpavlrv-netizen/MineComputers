-- BMC: управление серверами по сети. Работает, даже если сервер выключен или упал (нужен блок питания и кабель)
--   bmc                         — свой BMC (если это сервер): адрес, пароль
--   bmc password <новый>        — сменить пароль своего BMC
--   bmc <сервер> <команда> [...] — status, sensors, log, screen, console, power on|off|reset, password, help
-- Пароль спросит один раз (или задайте: bmc -p пароль <сервер> <команда>)
local args = { ... }
local color = term.isColor()
local function c(col) if color then term.setTextColor(col) end end
local function bg(col) if color then term.setBackgroundColor(col) end end

local password
if args[1] == "-p" then
  password = args[2]
  table.remove(args, 1); table.remove(args, 1)
end

local target, cmd, arg = args[1], args[2], args[3]

if target == nil then
  local info = net.bmcInfo()
  if not info then
    print("У этого компьютера нет BMC (нужна серверная плата и блок питания).")
    print("Управлять другими: bmc <сервер> status")
    return
  end
  c(colors.lightGray) write("Адрес BMC:   ") c(colors.cyan) print(info.address)
  c(colors.lightGray) write("Сервер:      ") c(colors.white) print(info.server)
  c(colors.lightGray) write("Пароль:      ")
  if info.defaultPassword then c(colors.yellow) print("по умолчанию (admin) — смените: bmc password <новый>")
  else c(colors.lime) print("задан") end
  c(colors.lightGray) write("Журнал:      ") c(colors.white) print(info.log .. " записей")
  c(colors.white)
  return
end

if target == "password" and cmd then
  if net.bmcPassword(cmd) then c(colors.lime) print("Пароль BMC изменён")
  else c(colors.red) print("Не вышло: нужна серверная плата, пароль — от 4 до 32 знаков") end
  c(colors.white)
  return
end

cmd = cmd or "status"
if cmd == "power" and not arg then print("bmc " .. target .. " power on|off|reset") return end
if not password then
  write("Пароль BMC " .. target .. ": ")
  password = read("*")
end

-- Консоль: экран сервера по сети (обновляется 4 раза в секунду) и клавиатура; Ctrl+T — выйти
if cmd == "console" then
  local ok, msg = net.bmc(target, password, "screen")
  if not ok and msg ~= "Сервер выключен — экрана нет" then c(colors.red) print(msg) c(colors.white) return end
  local w, h = term.getSize()
  local last = nil
  local timer = os.startTimer(0)
  while true do
    local ev = { os.pullEventRaw() }
    if ev[1] == "terminate" then break
    elseif ev[1] == "timer" and ev[2] == timer then
      local ok2, text, data = net.bmc(target, password, "screen")
      local key = (ok2 and text or "") .. (data and (data.cx .. "," .. data.cy) or "")
      if key ~= last then
        last = key
        bg(colors.black)
        term.clear()
        term.setCursorPos(1, 1)
        c(colors.black) bg(colors.lightGray)
        term.write((" BMC " .. target .. " — Ctrl+T: выйти") .. (" "):rep(w))   -- term.write обрезает по краю экрана
        bg(colors.black) c(colors.white)
        if ok2 and data then
          for i = 1, math.min(h - 1, #data.lines) do
            term.setCursorPos(1, i + 1)
            term.write(data.lines[i])
          end
          term.setCursorPos(math.min(w, data.cx + 1), math.min(h, data.cy + 2))
        else
          term.setCursorPos(1, 3)
          c(colors.yellow) print(text) c(colors.white)
          print("power on — включить: bmc " .. target .. " power on")
        end
      end
      timer = os.startTimer(0.25)
    elseif ev[1] == "char" then
      net.bmc(target, password, "input", "c:" .. ev[2])
      timer = os.startTimer(0.05)
    elseif ev[1] == "key" then
      net.bmc(target, password, "input", "k:" .. tostring(ev[2]) .. ":" .. (ev[3] and "1" or "0") .. ":" .. (ev[4] and "1" or "0"))
      timer = os.startTimer(0.05)
    end
  end
  bg(colors.black) c(colors.white)
  term.clear() term.setCursorPos(1, 1)
  print("Консоль BMC закрыта")
  return
end

local ok, msg, data = net.bmc(target, password, cmd, arg)
if not ok then
  c(colors.red) print(msg) c(colors.white)
  return
end
if cmd == "status" or cmd == "sensors" then
  for line in (msg .. "\n"):gmatch("(.-)\n") do
    if line:find("!") then c(colors.yellow) else c(colors.white) end
    print(line)
  end
elseif cmd == "screen" then
  c(colors.lightGray) print(("─"):rep(math.min(term.getSize(), (data and data.width) or 40)))
  c(colors.white) print(msg)
  c(colors.lightGray) print(("─"):rep(math.min(term.getSize(), (data and data.width) or 40)))
else
  print(msg)
end
c(colors.white)
