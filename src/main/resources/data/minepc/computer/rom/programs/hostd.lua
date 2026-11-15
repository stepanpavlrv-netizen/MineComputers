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

local H = require("hostadm")
local running, autostart, loadDb, saveDb, hash, size, used = H.proc, H.autostart, H.load, H.save, H.hash, H.size, H.used

-- Панель клиента: страница-программа на сайте сервера (httpd → /www/hosting.lua)
local PANEL = [==[
-- Панель хостинга (пишет hostd при запуске): вход клиента, его сайты, посещения, почта и место
local H = require("hostadm")
local p = request.params
local sz = textutils.formatSize
local form = "Логин [?login:логин]\nПароль [?password:пароль]\n[!Войти](/hosting.lua)"
if not p.login or p.login == "" then
  return "# Хостинг\nСайты и почта клиентов этого сервера.\n---\n" .. form ..
    "\n---\n> Аккаунт выдаёт владелец сервера. Загрузить сайт: {lightBlue}deploy <сервер> <сайт>{} или «На хостинг» в Сайтостроителе."
end
local u = H.load().users[p.login]
if not u or u.pass ~= H.hash(p.password or "") then
  return "# Хостинг\n{red}Неверный логин или пароль{}\n---\n" .. form
end
local out, total = {}, 0
local names = {}
for site in pairs(u.sites or {}) do names[#names + 1] = site end
table.sort(names)
for _, site in ipairs(names) do
  local s, m = H.size(H.SITES .. "/" .. site), H.size(H.MAIL .. "/" .. site)
  total = total + s + m
  local boxes = {}
  if fs.isDir(H.MAIL .. "/" .. site) then
    for _, b in ipairs(fs.list(H.MAIL .. "/" .. site)) do
      boxes[#boxes + 1] = b .. "@" .. site .. " (" .. #fs.list(H.MAIL .. "/" .. site .. "/" .. b) .. ")"
    end
  end
  local st = H.stats(site)
  local maxv = 1
  for _, d in ipairs(st.series) do if d.views > maxv then maxv = d.views end end
  local bars = {}
  for _, d in ipairs(st.series) do
    bars[#bars + 1] = "{gray}" .. d.date .. "{} {cyan}" .. string.rep("█", math.floor(d.views * 16 / maxv + 0.5)) .. "{} " .. d.views
  end
  out[#out + 1] = "## [" .. site .. "](mine://" .. site .. ")\nСайт: " .. sz(s) .. ", почта: " .. sz(m) ..
    "\nПосещения: сегодня {lime}" .. st.today .. "{} (гостей " .. st.todayUniq .. "), за неделю " .. st.week ..
    " (гостей " .. st.weekUniq .. "), трафик за неделю " .. sz(st.weekBytes) .. ", всего страниц " .. st.total ..
    "\n" .. table.concat(bars, "\n") ..
    "\nЯщики: " .. (#boxes > 0 and table.concat(boxes, ", ") or "писем пока нет")
end
local quota = u.quota or H.DEFAULT_QUOTA
local pct = math.min(100, math.floor(total * 100 / math.max(1, quota)))
local bar = string.rep("█", math.floor(pct / 5)) .. string.rep("░", 20 - math.floor(pct / 5))
return "# Хостинг: " .. p.login .. "\nМесто: {" .. (pct > 90 and "red" or "lime") .. "}" .. bar .. "{} " .. sz(total) ..
  " из " .. sz(quota) .. "\n---\n" ..
  (#out > 0 and table.concat(out, "\n---\n") or "Сайтов нет. Загрузите: {lightBlue}deploy <сервер> <сайт>{}") ..
  "\n---\n> Почта на ваши домены (имя@сайт.mine) приходит сюда; читать — Почта Wired → «Ящик на хостинге»."
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
      local st = H.stats(site)
      sites[#sites + 1] = { name = site, size = s, files = n, today = st.today, week = st.week, weekBytes = st.weekBytes }
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
    H.removeSite(site)
    u.sites[site] = nil
    return true, site
  end
  return false, "Неизвестная команда " .. tostring(c)
end

local function serve()
  -- панель клиентов на сайте сервера: каждый запуск — свежая (её пишет hostd)
  pcall(fs.makeDir, "/www")
  local pf = fs.open("/www/hosting.lua", "w")
  if pf then pf.write(PANEL) pf.close() end
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
  if H.stop() then print("Хостинг остановлен (сайты клиентов не отвечают, httpd работает)") else print("Хостинг не запущен") end
elseif cmd == "add" then
  if not a1 or not a2 then print("hostd add <логин> <пароль> [КБ]") return end
  local ok, err = H.add(a1, a2, a3)
  if not ok then printError(err) return end
  print("Клиент " .. a1 .. ": " .. kb(loadDb().users[a1].quota) .. ". Загрузить сайт: deploy " ..
    (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)) .. " <имя сайта>")
elseif cmd == "del" then
  local ok, err = H.del(a1)
  if ok then print("Клиент " .. a1 .. " и его сайты удалены") else printError(err) end
elseif cmd == "quota" then
  if not H.quota(a1, a2) then print("hostd quota <логин> <КБ>") return end
  print(a1 .. ": " .. kb(tonumber(a2) * 1024))
elseif cmd == "passwd" then
  if H.passwd(a1, a2) then print("Пароль клиента " .. a1 .. " сменён") else print("hostd passwd <логин> <пароль>") end
elseif cmd == "list" then
  local db = loadDb()
  local any = false
  for name, u in pairs(db.users) do
    any = true
    if term.isColor() then term.setTextColor(colors.yellow) end
    write(name)
    term.setTextColor(colors.white)
    print("  " .. kb(used(u)) .. " из " .. kb(u.quota or DEFAULT_QUOTA))
    for site in pairs(u.sites or {}) do
      local st = H.stats(site)
      print("   " .. site .. "  " .. kb((size(SITES .. "/" .. site))) .. "  заходов сегодня " .. st.today .. ", за неделю " .. st.week)
    end
  end
  if not any then print("Клиентов нет. Завести: hostd add <логин> <пароль> [КБ]") end
  print(H.running() and "Хостинг работает" or "Хостинг не запущен: hostd")
elseif cmd == nil or cmd == "start" then
  local pid, err = H.start()
  if not pid then printError("Не запустился: " .. tostring(err)) return end
  print("Хостинг работает (процесс " .. pid .. "), включается сам при запуске. Сайты клиентов — в " .. SITES .. ".")
  if next(loadDb().users) == nil then print("Заведите клиента: hostd add <логин> <пароль> [КБ]") end
  print("Панель клиентов: " .. H.url() .. "   В Wired — программа «Хостинг».")
else
  print("hostd [stop | add | del | quota | passwd | list]")
end
