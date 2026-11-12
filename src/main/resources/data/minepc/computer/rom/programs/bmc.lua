-- BMC: управление серверами по сети. Работает, даже если сервер выключен или упал (нужен блок питания и кабель)
--   bmc                         — свой BMC (если это сервер): адрес, пароль
--   bmc password <новый>        — сменить пароль своего BMC
--   bmc <сервер> <команда> [...] — status, sensors, log, screen, power on|off|reset, password, help
-- Пароль спросит один раз (или задайте: bmc -p пароль <сервер> <команда>)
local args = { ... }
local color = term.isColor()
local function c(col) if color then term.setTextColor(col) end end

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
