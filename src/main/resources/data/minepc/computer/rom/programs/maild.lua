-- Почтовый ящик этого компьютера: принимает письма (порт 25) в фоне. Письма — в /mail/<имя>/.
-- На хостинге (hostd) письма на домены клиентов — в /hosting/mail/<домен>/<имя>/, их забирает Почта по сети.
--   maild — запустить, maild stop — остановить. Читать — mail.
local cmd = ...
local SELF = "/rom/programs/maild.lua"
local PORT = 25
local function running()
  for _, p in ipairs(computer.processes()) do
    if p.path == SELF and p.pid ~= computer.pid() then return p.pid end
  end
end
--- Запускать при включении: строка в /etc/services
local function autostart(on)
  local line = SELF .. " serve"
  local lines, have = {}, false
  local f = fs.exists("/etc/services") and fs.open("/etc/services", "r")
  if f then
    for l in (f.readAll() or ""):gmatch("[^\r\n]+") do
      if l == line then have = true end
      if l ~= line then lines[#lines + 1] = l end
    end
    f.close()
  end
  if on then lines[#lines + 1] = line elseif not have then return end
  pcall(fs.makeDir, "/etc")
  local w = fs.open("/etc/services", "w")
  if w then w.write(table.concat(lines, "\n") .. (#lines > 0 and "\n" or "")) w.close() end
end
if cmd == "serve" then
  net.open(PORT)
  while true do
    local ev = { os.pullEvent("net_message") }
    local from, port = ev[2], ev[3]
    if port == PORT and ev[5] == "MAIL" then
      local id, reply, user, sender, subject, body = ev[6], ev[7], tostring(ev[8] or ""), tostring(ev[9] or ""),
        tostring(ev[10] or ""), tostring(ev[11] or "")
      user = user:lower():gsub("[^%w%-%_а-яё]", "")
      if user == "" then user = "all" end
      -- Хостинг: письма на чужие домены этого сервера (user@shop.mine) — в ящики клиента, /hosting/mail/<домен>/<имя>
      local domain = (unicode and unicode.lower or string.lower)(tostring(ev[12] or "")):gsub("[/\\]", "")
      local dir = "/mail/" .. user
      if domain ~= "" and domain ~= "." and domain ~= ".." and fs.isDir("/sites/" .. domain) then
        dir = "/hosting/mail/" .. domain .. "/" .. user
      end
      pcall(fs.makeDir, dir)
      local name = string.format("%s/%d-%04d.txt", dir, math.floor(os.epoch and os.epoch("utc") or os.clock() * 1000), math.random(0, 9999))
      local f = fs.open(name, "w")
      local ok = f ~= nil
      if f then
        f.write("От: " .. sender .. "\nТема: " .. subject .. "\nВремя: " .. (os.date and os.date("%d.%m %H:%M") or "") ..
          "\nНовое: да\n\n" .. body)
        f.close()
      end
      net.send(from, reply, "MAILOK", id, ok, ok and "доставлено" or "ящик переполнен")
      if ok then pcall(computer.beep, 1200, 0.1) end
    end
  end
elseif cmd == "stop" then
  local pid = running()
  autostart(false)
  if pid and computer.kill(pid) then print("Почта остановлена") else print("Почта не запущена") end
else
  local pid = running()
  autostart(true)
  if pid then print("Почта уже работает (процесс " .. pid .. ")") return end
  pid = computer.spawn(SELF, "serve")
  local host = net.hostname()
  local ok, names = net.dns("names")
  if ok and names[1] then host = names[1].name end
  if host == "" then host = net.address():sub(1, 8) end
  print("Почта работает в фоне (процесс " .. pid .. "). Ваш адрес: <имя>@" .. host)
end
