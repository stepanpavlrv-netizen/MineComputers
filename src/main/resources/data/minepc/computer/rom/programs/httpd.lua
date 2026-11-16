-- Сайт этого компьютера: раздаёт папку /www по сети (порт 80) и работает в фоне.
--   httpd        — запустить (в фоне; дальше включается сам при запуске компьютера)
--   httpd stop   — остановить (и не запускать при включении)
--   httpd log    — последние запросы
-- Страницы: /www/index.page (разметка — см. help в browse), картинки и файлы — как есть. На хостинге (hostd)
-- у каждого имени своя папка: /sites/shop.mine.
-- /www/*.lua — страница-программа: получает request (path, params, from), возвращает текст страницы.
local cmd = ...
local SELF = "/rom/programs/httpd.lua"
local LOG = "/var/log/httpd.log"
local http = require("http")

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

local function sample()
  if fs.exists("/www") then return end
  fs.makeDir("/www")
  local name = net.hostname and net.hostname() or ""
  local okNames, owned = net.dns("names")
  if okNames and owned[1] then name = owned[1].name end
  local f = fs.open("/www/index.page", "w")
  f.write([[
# Добро пожаловать на мой сайт!
Этот сайт работает на компьютере {yellow}]] .. (name ~= "" and name or net.address():sub(1, 8)) .. [[{}.
Страницы лежат в папке {lightBlue}/www{}: откройте {lightBlue}edit /www/index.page{} и поменяйте этот текст.
---
## Что умеет разметка
Решётка и пробел в начале строки — заголовок, две решётки — подзаголовок,
три минуса — черта, знак «больше» — цитата.
> Это цитата.
{red}Цвет{} — имя в фигурных скобках: {green}green{}, {orange}orange{}, {lightBlue}lightBlue{}; пустые скобки — обычный.
Ссылка: [страница «Привет»](/hello.lua)
---
## Форма
Как вас зовут? [?name]
[!Поздороваться](/hello.lua)
]])
  f.close()
  f = fs.open("/www/hello.lua", "w")
  f.write([[
-- Страница-программа: request.params — поля формы, request.from — адрес гостя
local name = request.params.name
if not name or name == "" then name = "гость" end
return "# Привет, " .. name .. "!\nВаш адрес: " .. request.from .. "\n[Назад](/)"
]])
  f.close()
end

local function log(line)
  pcall(fs.makeDir, "/var/log")
  local size = fs.exists(LOG) and fs.getSize(LOG) or 0
  local f = fs.open(LOG, size > 16384 and "w" or "a")
  if f then f.write(os.date and os.date("%H:%M ") or "") f.write(line .. "\n") f.close() end
end

local function ctypeOf(path)
  if path:match("%.page$") then return "page" end
  if path:match("%.txt$") or path:match("%.md$") then return "text" end
  return "file"
end

local function read(path)
  local f = fs.open(path, "rb") or fs.open(path, "r")
  if not f then return nil end
  local data = f.readAll() or ""
  f.close()
  return data
end

--- Папка сайта: на хостинге у каждого имени своя (/sites/shop.mine), иначе — /www этого компьютера.
local stats = require("hostadm")

local function rootFor(host)
  host = (unicode and unicode.lower or string.lower)(tostring(host or "")):gsub("[/\\]", "")
  if host ~= "" and host ~= "." and host ~= ".." and fs.isDir("/sites/" .. host) then return "/sites/" .. host end
  return "/www"
end

--- Страница-программа сайта клиента: отдельный процесс в клетке (видит только папку сайта, живёт ≤ 3 с).
local busy = {}          -- сколько страниц каждого сайта работает сейчас
local pending = {}       -- запросы, на которые не хватило глубины: обработаем следом
local exits, fired = {}, {}   -- события, пришедшие, пока ждали другую страницу
local MAX_BUSY, MAX_DEPTH = 2, 3
local depth = 0
local serveOne           -- обработать запрос (ниже); пока ждём страницу, отвечаем на другие запросы

local function runCaged(root, file, request, path)
  if (busy[root] or 0) >= MAX_BUSY or not computer.spawnPage then
    return 503, "page", "# Сервер занят\nПопробуйте позже."
  end
  local pid = computer.spawnPage(root, file, request)
  if not pid then return 503, "page", "# Сервер занят\nПопробуйте позже." end
  busy[root] = (busy[root] or 0) + 1
  depth = depth + 1
  local timer = os.startTimer(3.5)
  local ok, perr, result, timedOut
  while true do
    local ev
    if exits[pid] then ev = exits[pid] exits[pid] = nil
    elseif fired[timer] then fired[timer] = nil ev = { "timer", timer }
    else ev = { os.pullEvent() } end
    if ev[1] == "process_exit" and ev[2] == pid then
      ok, perr, result = ev[3], ev[4], ev[5]
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      timedOut = true
      pcall(computer.kill, pid)
      break
    elseif ev[1] == "process_exit" then
      exits[ev[2]] = ev
    elseif ev[1] == "timer" then
      fired[ev[2]] = true
    elseif ev[1] == "net_message" then
      if depth < MAX_DEPTH then serveOne(ev)
      elseif #pending < 32 then pending[#pending + 1] = ev end
    end
  end
  depth = depth - 1
  os.cancelTimer(timer)
  busy[root] = busy[root] - 1
  if timedOut or perr == "timeout" then
    return 504, "page", "# Страница слишком долго отвечала\nСтраница {yellow}" .. path .. "{} работала дольше 3 секунд и была остановлена."
  end
  if not ok then return 500, "page", "# Ошибка в " .. path .. "\n" .. tostring(perr) end
  return 200, "page", type(result) == "string" and result or ""
end

local function handle(path, params, from, host)
  path = "/" .. tostring(path or "/"):gsub("%.%.", ""):gsub("^/+", "")
  local root = rootFor(host)
  -- сайт клиента приостановлен или у него вышел срок работы — вместо сайта заглушка (почта при этом работает)
  local site = root:match("^/sites/(.+)$")
  if site then
    local st, info = stats.state(site)
    if st ~= "ok" then return 423, "page", stats.stub(site, st, info) end
  end
  local file = root .. path
  if fs.isDir(file) then
    file = fs.exists(file .. "/index.page") and (file .. "/index.page") or (file .. "/index.lua")
  end
  if not fs.exists(file) and fs.exists(file .. ".page") then file = file .. ".page" end
  if not fs.exists(file) or fs.isDir(file) then
    return 404, "page", "# 404\nНа этом сайте нет страницы {yellow}" .. path .. "{}\n[На главную](/)"
  end
  if file:match("%.lua$") and root:match("^/sites/") then
    return runCaged(root, file:sub(#root + 1), { path = path, params = params or {}, from = from, host = host }, path)
  end
  if file:match("%.lua$") then
    local out = {}
    local env = setmetatable({ request = { path = path, params = params or {}, from = from, host = host },
      echo = function(...) for _, v in ipairs({ ... }) do out[#out + 1] = tostring(v) end out[#out + 1] = "\n" end },
      { __index = _G })
    local fn, err = loadfile(file, "t", env)
    if not fn then return 500, "page", "# Ошибка в " .. path .. "\n" .. tostring(err) end
    local ok, res = pcall(fn)
    if not ok then return 500, "page", "# Ошибка в " .. path .. "\n" .. tostring(res) end
    return 200, "page", type(res) == "string" and res or table.concat(out)
  end
  return 200, ctypeOf(file), read(file) or ""
end

function serveOne(ev)
  local from, port = ev[2], ev[3]
  if port == http.PORT and ev[5] == "GET" then
    local id, reply, path, params, host = ev[6], ev[7], ev[8], ev[9], ev[10]
    local ok, status, ctype, body = pcall(handle, path, type(params) == "table" and params or {}, from, host)
    if not ok then status, ctype, body = 500, "page", "# Ошибка сервера\n" .. tostring(status) end
    http.reply(from, reply, id, status, ctype, body)
    -- посещения: по сайту хостинга или «www» этого компьютера
    local key = rootFor(host):match("^/sites/(.+)$") or "www"
    pcall(stats.record, key, from, #body, ctype == "page" and status == 200)
    log(string.format("%s %s%s %d %d Б", from:sub(1, 8), host and host ~= "" and (tostring(host) .. " ") or "", tostring(path), status, #body))
  end
end

local function serve()
  net.open(http.PORT)
  log("сайт запущен")
  while true do
    serveOne(table.remove(pending, 1) or { os.pullEvent("net_message") })
  end
end

if cmd == "serve" then
  serve()
elseif cmd == "stop" then
  local pid = running()
  autostart(false)
  if pid and computer.kill(pid) then print("Сайт остановлен") else print("Сайт не запущен") end
elseif cmd == "log" then
  print(read(LOG) or "Журнал пуст")
elseif cmd == nil or cmd == "start" then
  local pid = running()
  autostart(true)
  if pid then print("Сайт уже работает (процесс " .. pid .. ")") return end
  sample()
  pid = computer.spawn(SELF, "serve")
  print("Сайт работает в фоне (процесс " .. pid .. "), включается сам при запуске компьютера. Страницы — в /www.")
  local ok, names = net.dns("names")
  local list = {}
  if ok then for _, n in ipairs(names) do list[#list + 1] = n.name end end
  if #list > 0 then
    print("Адрес: " .. table.concat(list, ", "))
  else
    print("Адрес: " .. net.address():sub(1, 8) .. " — займите имя: dns register <имя>")
  end
else
  print("httpd [stop | log]")
end
