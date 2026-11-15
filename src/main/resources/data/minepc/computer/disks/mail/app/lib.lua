-- Почта: ящики в /mail/<имя>/ (их пишет maild), отправленные — в /mail/.sent/<имя>/.
local M = {}
M.MAILD = "/rom/programs/maild.lua"

function M.me()
  local u = auth and auth.user()
  return (u and u ~= "") and u:lower() or "all"
end

function M.host()
  local ok, names = net.dns("names")
  if ok and names and names[1] then return names[1].name end
  local h = net.hostname()
  return h ~= "" and h or net.address():sub(1, 8)
end

function M.running()
  for _, p in ipairs(computer.processes()) do
    if p.path == M.MAILD then return p.pid end
  end
end

function M.start()
  if not M.running() then computer.spawn(M.MAILD, "serve") end
end

function M.parse(path)
  local f = fs.open(path, "r")
  if not f then return nil end
  local text = f.readAll() or ""
  f.close()
  local head, body = text:match("^(.-)\n\n(.*)$")
  head = head or text
  return {
    path = path, file = fs.getName(path), text = text, body = body or "",
    from = head:match("От: ([^\n]*)") or "?", to = head:match("Кому: ([^\n]*)"),
    subject = head:match("Тема: ([^\n]*)") or "", time = head:match("Время: ([^\n]*)") or "",
    new = head:find("Новое: да") ~= nil,
  }
end

function M.list(dir)
  local out = {}
  if not fs.isDir(dir) then return out end
  for _, f in ipairs(fs.list(dir)) do
    local m = not fs.isDir(fs.combine(dir, f)) and M.parse(fs.combine(dir, f))
    if m then out[#out + 1] = m end
  end
  table.sort(out, function(a, b) return a.file > b.file end)
  return out
end

function M.markRead(m)
  if not m.new then return end
  local text = m.text:gsub("Новое: да", "Новое: нет", 1)
  local f = fs.open(m.path, "w")
  if f then f.write(text) f.close() end
  m.new, m.text = false, text
end

--- Отправить письмо (внутри фоновой задачи: ждёт ответа). Возвращает ok, сообщение.
function M.send(to, subject, body)
  local user, host = tostring(to):match("^%s*([^@%s]+)@([^%s]+)%s*$")
  if not user then return false, "Адрес — имя@компьютер" end
  if net.speed() == 0 then return false, "Нет сети: подключите кабель или Wi-Fi" end
  local port = math.random(40000, 49999)
  net.open(port)
  local id = math.random(1, 1000000000)
  if not net.send(host, 25, "MAIL", id, port, user, M.me() .. "@" .. M.host(), subject, body) then
    net.close(port)
    return false, "Нет сети"
  end
  local timer = os.startTimer(5)
  local ok, msg = false, host .. " не принимает почту: компьютер выключен, ящик не включён или нет сети"
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "MAILOK" and ev[6] == id then
      ok, msg = ev[7] and true or false, tostring(ev[8] or "")
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      break
    end
  end
  net.close(port)
  if ok then
    local dir = "/mail/.sent/" .. M.me()
    pcall(fs.makeDir, dir)
    local name = string.format("%s/%d-%04d.txt", dir, math.floor(os.epoch and os.epoch() or 0), math.random(0, 9999))
    local f = fs.open(name, "w")
    if f then
      f.write("От: " .. M.me() .. "@" .. M.host() .. "\nКому: " .. to .. "\nТема: " .. subject .. "\nВремя: " ..
        os.date("%d.%m %H:%M") .. "\n\n" .. body)
      f.close()
    end
  end
  return ok, msg
end

return M
