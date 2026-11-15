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
local function rootFor(host)
  host = (unicode and unicode.lower or string.lower)(tostring(host or "")):gsub("[/\\]", "")
  if host ~= "" and host ~= "." and host ~= ".." and fs.isDir("/sites/" .. host) then return "/sites/" .. host end
  return "/www"
end

local function handle(path, params, from, host)
  path = "/" .. tostring(path or "/"):gsub("%.%.", ""):gsub("^/+", "")
  local root = rootFor(host)
  local file = root .. path
  if fs.isDir(file) then
    file = fs.exists(file .. "/index.page") and (file .. "/index.page") or (file .. "/index.lua")
  end
  if not fs.exists(file) and fs.exists(file .. ".page") then file = file .. ".page" end
  if not fs.exists(file) or fs.isDir(file) then
    return 404, "page", "# 404\nНа этом сайте нет страницы {yellow}" .. path .. "{}\n[На главную](/)"
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

local function serve()
  net.open(http.PORT)
  log("сайт запущен")
  while true do
    local ev = { os.pullEvent("net_message") }
    local from, port = ev[2], ev[3]
    if port == http.PORT and ev[5] == "GET" then
      local id, reply, path, params, host = ev[6], ev[7], ev[8], ev[9], ev[10]
      local ok, status, ctype, body = pcall(handle, path, type(params) == "table" and params or {}, from, host)
      if not ok then status, ctype, body = 500, "page", "# Ошибка сервера\n" .. tostring(status) end
      http.reply(from, reply, id, status, ctype, body)
      log(string.format("%s %s%s %d %d Б", from:sub(1, 8), host and host ~= "" and (tostring(host) .. " ") or "", tostring(path), status, #body))
    end
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
