-- Почта Майннета. Адрес: имя@компьютер (имя.mine, имя компьютера или адрес).
--   mail                     — входящие (письма этого компьютера)
--   mail read <номер>        — прочитать
--   mail send <кому> [тема]  — написать (текст — строками, точка в строке — конец)
--   mail rm <номер>          — удалить
-- Принимать письма: maild (в фоне).
local cmd, a, b = ...
local function color(c) if term.isColor() then term.setTextColor(c) end end

local function inbox()
  local list = {}
  if not fs.exists("/mail") then return list end
  for _, user in ipairs(fs.list("/mail")) do
    local dir = "/mail/" .. user
    if fs.isDir(dir) then
      for _, f in ipairs(fs.list(dir)) do
        local path = dir .. "/" .. f
        local h = fs.open(path, "r")
        local text = h and h.readAll() or ""
        if h then h.close() end
        list[#list + 1] = { path = path, file = f, user = user, from = text:match("От: ([^\n]*)") or "?",
          subject = text:match("Тема: ([^\n]*)") or "", new = text:find("Новое: да") ~= nil, text = text }
      end
    end
  end
  table.sort(list, function(x, y) return x.file > y.file end)
  return list
end

if not cmd then
  local list = inbox()
  if #list == 0 then
    print("Писем нет.")
    print("Ваш ящик работает? Запустите maild — тогда вам смогут писать на <имя>@" .. (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)))
    return
  end
  for i, m in ipairs(list) do
    color(m.new and colors.yellow or colors.lightGray)
    print(string.format("%2d %s %-12s %s", i, m.new and "*" or " ", m.from:sub(1, 12), m.subject))
  end
  color(colors.white)
  print("Прочитать: mail read <номер>")
elseif cmd == "read" or cmd == "rm" then
  local list = inbox()
  local m = list[tonumber(a or "")]
  if not m then printError("Нет письма с таким номером") return end
  if cmd == "rm" then fs.delete(m.path) print("Удалено") return end
  local text = m.text:gsub("Новое: да", "Новое: нет", 1)
  local f = fs.open(m.path, "w") f.write(text) f.close()
  local head, body = text:match("^(.-)\n\n(.*)$")
  color(colors.yellow) print(head or "")
  color(colors.white) print("")
  print(body or text)
elseif cmd == "send" then
  local to = a
  if not to or not to:find("@") then print("mail send <имя@компьютер> [тема]") return end
  local user, host = to:match("^([^@]+)@(.+)$")
  local subject = b
  if not subject then write("Тема: ") subject = read() end
  print("Текст (точка в отдельной строке — отправить):")
  local lines = {}
  while true do
    local l = read()
    if l == "." then break end
    lines[#lines + 1] = l
  end
  local me = (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8))
  local port = math.random(40000, 49999)
  net.open(port)
  local id = math.random(1, 1000000000)
  if not net.send(host, 25, "MAIL", id, port, user, me, subject, table.concat(lines, "\n")) then
    net.close(port) printError("Нет сети") return
  end
  local timer = os.startTimer(4)
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "MAILOK" and ev[6] == id then
      if ev[7] then print("Отправлено: " .. ev[8]) else printError(ev[8]) end
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      printError(host .. " не принимает почту: компьютер выключен, не запущен maild или нет сети")
      break
    end
  end
  net.close(port)
else
  print("mail [read N | send кому | rm N]")
end
