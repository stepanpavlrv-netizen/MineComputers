-- Хостинг: этот компьютер (лучше сервер в стойке) держит сайты других игроков. Сайты — в /sites/<имя.mine>,
-- раздаёт их httpd; имена .mine занимает сам сервер. Аккаунты клиентов — /etc/hosting.db.
--   hostd                          — запустить (в фоне, включается сам при запуске компьютера; заодно httpd)
--   hostd stop                     — остановить
--   hostd add <логин> <пароль> [КБ] — завести клиента (место по умолчанию 512 КБ)
--   hostd del <логин>              — удалить клиента вместе с его сайтами
--   hostd quota <логин> <КБ>       — сколько места клиенту
--   hostd passwd <логин> <пароль>  — сменить пароль
--   hostd list                     — клиенты, их сайты и место
-- Клиенты загружают сайт командой deploy или кнопкой «На хостинг» в Сайтостроителе Wired. Почта на домены
-- клиентов (имя@shop.mine) приходит сюда же (maild), клиент забирает её в Почте Wired («Ящик на хостинге»).
-- Панель клиента — страница hosting на сайте сервера: /www/hosting.lua (создаётся при запуске).
local cmd, a1, a2, a3 = ...
local SELF = "/rom/programs/hostd.lua"
local HTTPD = "/rom/programs/httpd.lua"
local MAILD = "/rom/programs/maild.lua"
local MAIL = "/hosting/mail"
local DB = "/etc/hosting.db"
local SITES = "/sites"
local PORT = 2121
local DEFAULT_QUOTA = 512 * 1024
local lower = unicode and unicode.lower or string.lower

local function running(path)
  for _, p in ipairs(computer.processes()) do
    if p.path == path and p.pid ~= computer.pid() then return p.pid end
  end
end

local function autostart(line, on)
  local lines, have = {}, false
  local f = fs.exists("/etc/services") and fs.open("/etc/services", "r")
  if f then
    for l in (f.readAll() or ""):gmatch("[^\r\n]+") do
      if l == line then have = true else lines[#lines + 1] = l end
    end
    f.close()
  end
  if on then lines[#lines + 1] = line elseif not have then return end
  pcall(fs.makeDir, "/etc")
  local w = fs.open("/etc/services", "w")
  if w then w.write(table.concat(lines, "\n") .. (#lines > 0 and "\n" or "")) w.close() end
end

local function loadDb()
  local db = { users = {} }
  if fs.exists(DB) then
    local f = fs.open(DB, "r")
    local fn = load("return " .. (f.readAll() or ""), "hosting", "t", {})
    f.close()
    local ok, t = pcall(fn or function() end)
    if ok and type(t) == "table" and type(t.users) == "table" then db = t end
  end
  return db
end

local function saveDb(db)
  pcall(fs.makeDir, "/etc")
  local f = fs.open(DB, "w")
  if f then f.write(textutils.serialize(db)) f.close() end
end

local function hash(s) return data.toHex(data.sha256(s)) end

local function size(path)
  if not fs.exists(path) then return 0, 0 end
  if not fs.isDir(path) then return fs.getSize(path) or 0, 1 end
  local total, n = 0, 0
  for _, name in ipairs(fs.list(path)) do
    local s, c = size(fs.combine(path, name))
    total, n = total + s, n + c
  end
  return total, n
end

local function used(user)
  local total = 0
  for site in pairs(user.sites or {}) do total = total + size(SITES .. "/" .. site) + size(MAIL .. "/" .. site) end
  return total
end

-- Панель клиента: страница-программа на сайте сервера (httpd → /www/hosting.lua)
local PANEL = [==[
-- Панель хостинга (создал hostd): вход клиента, его сайты, почта и место
local p = request.params
local function sz(n) return textutils.formatSize(n) end
local function size(path)
  if not fs.exists(path) then return 0 end
  if not fs.isDir(path) then return fs.getSize(path) or 0 end
  local t = 0
  for _, n in ipairs(fs.list(path)) do t = t + size(fs.combine(path, n)) end
  return t
end
local form = "Логин [?login:логин]
Пароль [?password:пароль]
[!Войти](/hosting.lua)"
if not p.login or p.login == "" then
  return "# Хостинг
Сайты и почта клиентов этого сервера.
---
" .. form ..
    "
---
> Аккаунт выдаёт владелец сервера. Загрузить сайт: {lightBlue}deploy <сервер> <сайт>{} или «На хостинг» в Сайтостроителе."
end
local f = fs.open("/etc/hosting.db", "r")
local fn = f and load("return " .. (f.readAll() or ""), "db", "t", {})
if f then f.close() end
local ok, db = pcall(fn or function() end)
local u = ok and type(db) == "table" and db.users and db.users[p.login]
if not u or u.pass ~= data.toHex(data.sha256(p.password or "")) then
  return "# Хостинг
{red}Неверный логин или пароль{}
---
" .. form
end
local out, total = {}, 0
for site in pairs(u.sites or {}) do
  local s, m = size("/sites/" .. site), size("/hosting/mail/" .. site)
  total = total + s + m
  local boxes = {}
  if fs.isDir("/hosting/mail/" .. site) then
    for _, b in ipairs(fs.list("/hosting/mail/" .. site)) do
      boxes[#boxes + 1] = b .. "@" .. site .. " (" .. #fs.list("/hosting/mail/" .. site .. "/" .. b) .. ")"
    end
  end
  out[#out + 1] = "## [" .. site .. "](mine://" .. site .. ")
Сайт: " .. sz(s) .. (#boxes > 0 and ("
Почта: " .. table.concat(boxes, ", ")) or "
Почта: писем пока нет")
end
local quota = u.quota or 524288
local pct = math.min(100, math.floor(total * 100 / math.max(1, quota)))
local bar = string.rep("█", math.floor(pct / 5)) .. string.rep("░", 20 - math.floor(pct / 5))
return "# Хостинг: " .. p.login .. "
Место: {" .. (pct > 90 and "red" or "lime") .. "}" .. bar .. "{} " .. sz(total) .. " из " .. sz(quota) ..
  "
---
" .. (#out > 0 and table.concat(out, "
---
") or "Сайтов нет. Загрузите: {lightBlue}deploy <сервер> <сайт>{}") ..
  "
---
> Почта на ваши домены (имя@сайт.mine) приходит сюда; читать — Почта Wired → «Ящик на хостинге»."
]==]

--- Имя сайта: «shop» → «shop.mine», маленькими буквами; nil — плохое имя.
local function siteName(raw)
  local n = lower(tostring(raw or "")):gsub("^%s+", ""):gsub("%s+$", "")
  if not n:match("%.mine$") then n = n .. ".mine" end
  if n:find("[/\\]") or n:find("%.%.") or n == ".mine" then return nil end
  return n
end

local function cleanPath(p)
  p = tostring(p or ""):gsub("\\", "/"):gsub("^/+", "")
  if p == "" or p:find("%.%.") then return nil end
  return p
end

local function kb(n) return textutils.formatSize and textutils.formatSize(n) or (math.floor(n / 1024) .. " КБ") end

-- ======================= служба =======================

local function handle(db, from, c, user, ph, x1, x2, x3, x4, x5)
  if c == "hello" then
    local n = 0
    for _, u in pairs(db.users) do for _ in pairs(u.sites or {}) do n = n + 1 end end
    local okF, free = pcall(fs.getFreeSpace, "/")
    return true, { name = net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8), sites = n, free = okF and free or nil }
  end
  local u = db.users[tostring(user or "")]
  if not u or u.pass ~= ph then return false, "Неверный логин или пароль хостинга" end
  if c == "info" then
    local sites = {}
    for site in pairs(u.sites or {}) do
      local s, n = size(SITES .. "/" .. site)
      sites[#sites + 1] = { name = site, size = s, files = n }
    end
    table.sort(sites, function(p, q) return p.name < q.name end)
    return true, { user = user, quota = u.quota, used = used(u), sites = sites }
  end
  local site = siteName(x1)
  if not site then return false, "Плохое имя сайта" end
  for name, other in pairs(db.users) do
    if name ~= user and other.sites and other.sites[site] then return false, "Сайт " .. site .. " принадлежит другому клиенту" end
  end
  local dir = SITES .. "/" .. site
  -- Почта клиента: ящики доменов его сайтов (письма кладёт maild этого сервера)
  if c == "mail_boxes" or c == "mail_list" or c == "mail_get" or c == "mail_del" then
    if not (u.sites or {})[site] then return false, "Домен " .. site .. " не ваш" end
    local base = MAIL .. "/" .. site
    if c == "mail_boxes" then
      local out = {}
      if fs.isDir(base) then for _, n in ipairs(fs.list(base)) do out[#out + 1] = n end end
      return true, out
    end
    local box = lower(tostring(x2 or "")):gsub("[^%w%-%_а-яё]", "")
    if box == "" then return false, "Нет имени ящика" end
    local dir = base .. "/" .. box
    if c == "mail_list" then
      local names = fs.isDir(dir) and fs.list(dir) or {}
      table.sort(names, function(p, q) return p > q end)
      local out = {}
      for i = 1, math.min(30, #names) do
        local f = fs.open(dir .. "/" .. names[i], "r")
        local head = f and (f.readAll() or ""):match("^(.-)\n\n") or ""
        if f then f.close() end
        out[#out + 1] = { id = names[i], from = head:match("От: ([^\n]*)") or "?",
          subject = (head:match("Тема: ([^\n]*)") or ""):sub(1, 80), time = head:match("Время: ([^\n]*)") or "",
          new = head:find("Новое: да") ~= nil }
      end
      return true, out
    end
    local id = tostring(x3 or "")
    if id == "" or id:find("[/\\]") or id:find("%.%.") then return false, "Плохой номер письма" end
    local path = dir .. "/" .. id
    if not fs.exists(path) then return false, "Письма нет" end
    if c == "mail_del" then fs.delete(path) return true end
    local f = fs.open(path, "r")
    local text = f.readAll() or ""
    f.close()
    if text:find("Новое: да") then
      local w = fs.open(path, "w")
      if w then w.write((text:gsub("Новое: да", "Новое: нет", 1))) w.close() end
    end
    return true, text:sub(1, 7000)
  end
  if c == "begin" then
    local okL, owner = net.dns("lookup", site)
    if okL and owner ~= net.address() then
      return false, "Имя " .. site .. " занято другим компьютером: пусть владелец освободит его (dns unregister)"
    end
    if not (u.sites or {})[site] then
      local count = 0
      for _ in pairs(u.sites or {}) do count = count + 1 end
      if count >= (u.maxSites or 8) then return false, "У клиента уже " .. count .. " сайтов" end
    end
    pcall(fs.makeDir, dir)
    u.sites = u.sites or {}
    u.sites[site] = true
    saveDb(db)
    -- что уже лежит на сервере: путь и crc32 — клиент пришлёт только изменённое
    local lines = {}
    local function walk(d, prefix)
      for _, name in ipairs(fs.list(d)) do
        local p = fs.combine(d, name)
        if fs.isDir(p) then walk(p, prefix .. name .. "/")
        else
          local f = fs.open(p, "rb") or fs.open(p, "r")
          local body = f and f.readAll() or ""
          if f then f.close() end
          lines[#lines + 1] = prefix .. name .. "\t" .. data.toHex(data.crc32(body))
        end
      end
    end
    walk(dir, "")
    local m = table.concat(lines, "\n")
    return true, #m <= 7000 and m or "*"
  end
  if not (u.sites or {})[site] then return false, "Нет такого сайта у клиента: сначала begin" end
  if c == "put" then
    local rel = cleanPath(x2)
    if not rel then return false, "Плохой путь файла" end
    local part, body = tonumber(x3) or 1, tostring(x5 or "")
    local path = dir .. "/" .. rel
    local old = (part == 1 and fs.exists(path)) and (fs.getSize(path) or 0) or 0
    if used(u) - old + #body > (u.quota or DEFAULT_QUOTA) then
      return false, "Не хватает места: " .. kb(used(u)) .. " из " .. kb(u.quota or DEFAULT_QUOTA)
    end
    local d = fs.getDir(path)
    if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
    local f = fs.open(path, part == 1 and "wb" or "ab")
    if not f then return false, "Не записывается " .. rel end
    f.write(body)
    f.close()
    return true
  elseif c == "finish" then
    local keep = {}
    for line in tostring(x2 or ""):gmatch("[^\n]+") do keep[line] = true end
    local function prune(d, prefix)
      for _, name in ipairs(fs.list(d)) do
        local p = fs.combine(d, name)
        if fs.isDir(p) then
          prune(p, prefix .. name .. "/")
          if #fs.list(p) == 0 then fs.delete(p) end
        elseif not keep[prefix .. name] then fs.delete(p) end
      end
    end
    prune(dir, "")
    local ok, res = net.dns("register", site)
    if not ok then return false, "Сайт загружен, но имя не занялось: " .. tostring(res) end
    if not running(HTTPD) then pcall(computer.spawn, HTTPD, "serve") end
    local s = size(dir)
    return true, { url = site, size = s, used = used(u), quota = u.quota }
  elseif c == "remove" then
    if fs.exists(dir) then fs.delete(dir) end
    if fs.exists(MAIL .. "/" .. site) then fs.delete(MAIL .. "/" .. site) end
    pcall(net.dns, "unregister", site)
    u.sites[site] = nil
    saveDb(db)
    return true, site
  end
  return false, "Неизвестная команда " .. tostring(c)
end

local function serve()
  net.open(PORT)
  while true do
    local ev = { os.pullEvent("net_message") }
    if ev[3] == PORT and ev[5] == "HOST" then
      local from, id, reply = ev[2], ev[6], ev[7]
      local db = loadDb()
      local ok, okR, res = pcall(handle, db, from, ev[8], ev[9], ev[10], ev[11], ev[12], ev[13], ev[14], ev[15])
      if not ok then okR, res = false, "Ошибка хостинга: " .. tostring(okR) end
      net.send(from, reply, "HOSTOK", id, okR, res)
    end
  end
end

-- ======================= команды владельца =======================

if cmd == "serve" then
  serve()
elseif cmd == "stop" then
  autostart(SELF .. " serve", false)
  local pid = running(SELF)
  if pid and computer.kill(pid) then print("Хостинг остановлен (сайты клиентов не отвечают, httpd работает)") else print("Хостинг не запущен") end
elseif cmd == "add" then
  if not a1 or not a2 then print("hostd add <логин> <пароль> [КБ]") return end
  local db = loadDb()
  if db.users[a1] then printError("Клиент " .. a1 .. " уже есть") return end
  db.users[a1] = { pass = hash(a2), quota = (tonumber(a3) or DEFAULT_QUOTA / 1024) * 1024, sites = {} }
  saveDb(db)
  print("Клиент " .. a1 .. ": " .. kb(db.users[a1].quota) .. ". Загрузить сайт: deploy " ..
    (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)) .. " <имя сайта>")
elseif cmd == "del" then
  local db = loadDb()
  local u = a1 and db.users[a1]
  if not u then printError("Нет клиента " .. tostring(a1)) return end
  for site in pairs(u.sites or {}) do
    if fs.exists(SITES .. "/" .. site) then fs.delete(SITES .. "/" .. site) end
    if fs.exists(MAIL .. "/" .. site) then fs.delete(MAIL .. "/" .. site) end
    pcall(net.dns, "unregister", site)
  end
  db.users[a1] = nil
  saveDb(db)
  print("Клиент " .. a1 .. " и его сайты удалены")
elseif cmd == "quota" then
  local db = loadDb()
  local u = a1 and db.users[a1]
  if not u or not tonumber(a2) then print("hostd quota <логин> <КБ>") return end
  u.quota = tonumber(a2) * 1024
  saveDb(db)
  print(a1 .. ": " .. kb(u.quota))
elseif cmd == "passwd" then
  local db = loadDb()
  local u = a1 and db.users[a1]
  if not u or not a2 then print("hostd passwd <логин> <пароль>") return end
  u.pass = hash(a2)
  saveDb(db)
  print("Пароль клиента " .. a1 .. " сменён")
elseif cmd == "list" then
  local db = loadDb()
  local any = false
  for name, u in pairs(db.users) do
    any = true
    if term.isColor() then term.setTextColor(colors.yellow) end
    write(name)
    term.setTextColor(colors.white)
    print("  " .. kb(used(u)) .. " из " .. kb(u.quota or DEFAULT_QUOTA))
    for site in pairs(u.sites or {}) do print("   " .. site .. "  " .. kb((size(SITES .. "/" .. site)))) end
  end
  if not any then print("Клиентов нет. Завести: hostd add <логин> <пароль> [КБ]") end
  print(running(SELF) and "Хостинг работает" or "Хостинг не запущен: hostd")
elseif cmd == nil or cmd == "start" then
  autostart(SELF .. " serve", true)
  autostart(HTTPD .. " serve", true)
  autostart(MAILD .. " serve", true)
  if not running(HTTPD) then computer.spawn(HTTPD, "serve") end
  if not running(MAILD) then computer.spawn(MAILD, "serve") end
  local pid = running(SELF)
  if not pid then pid = computer.spawn(SELF, "serve") end
  pcall(fs.makeDir, SITES)
  if not fs.exists("/www/hosting.lua") then
    pcall(fs.makeDir, "/www")
    local f = fs.open("/www/hosting.lua", "w")
    if f then f.write(PANEL) f.close() end
  end
  print("Хостинг работает (процесс " .. pid .. "), включается сам при запуске. Сайты клиентов — в " .. SITES .. ".")
  local db = loadDb()
  if next(db.users) == nil then print("Заведите клиента: hostd add <логин> <пароль> [КБ]") end
  local okN, names = net.dns("names")
  local host = (okN and names[1]) and names[1].name or (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8))
  print("Панель клиентов: " .. host .. "/hosting.lua")
else
  print("hostd [stop | add | del | quota | passwd | list]")
end
