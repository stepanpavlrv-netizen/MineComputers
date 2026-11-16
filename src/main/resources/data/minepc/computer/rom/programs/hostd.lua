-- Хостинг: этот компьютер (лучше сервер в стойке) держит сайты других игроков. Сайты — в /sites/<имя.mine>,
-- раздаёт их httpd; имена .mine занимает сам сервер. Аккаунты клиентов — /etc/hosting.db.
--   hostd                          — запустить (в фоне, включается сам при запуске компьютера; заодно httpd)
--   hostd stop                     — остановить
--   hostd add <логин> <пароль> [КБ] — завести клиента (место по умолчанию 512 КБ)
--   hostd del <логин>              — удалить клиента вместе с его сайтами
--   hostd quota <логин> <КБ>       — сколько места клиенту
--   hostd passwd <логин> <пароль>  — сменить пароль
--   hostd list                     — клиенты, их сайты, место и срок работы
--   hostd until <логин> <дата|+дней|-> — срок работы сайтов клиента: 30.11.2026, +30 (продлить) или - (без срока)
--   hostd pause <сайт|логин> [причина] — приостановить сайт или все сайты клиента (посетители видят заглушку)
--   hostd resume <сайт|логин>      — снова включить
-- Клиенты загружают сайт командой deploy или кнопкой «На хостинг» в Сайтостроителе Wired. Почта на домены
-- клиентов (имя@shop.mine) приходит сюда же (maild), клиент забирает её в Почте Wired («Ящик на хостинге»).
-- Панель клиента — страница hosting на сайте сервера: /www/hosting.lua (создаётся при запуске).
local args = { ... }
local cmd, a1, a2, a3 = args[1], args[2], args[3], args[4]
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
local db = H.load()
local u = db.users[p.login]
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
  local state, why = H.state(site, db)
  local note = ""
  if state == "paused" then
    note = "\n{orange}⏸ Сайт приостановлен владельцем хостинга{}" .. (why ~= "" and (": «" .. why .. "»") or "") ..
      "\n> Посетители видят заглушку. Загружать сайт можно, почта работает."
  elseif state == "expired" then
    note = "\n{red}Срок работы закончился " .. H.fmtDate(why) .. "{} — посетители видят заглушку."
  end
  out[#out + 1] = "## [" .. site .. "](mine://" .. site .. ")" .. note .. "\nСайт: " .. sz(s) .. ", почта: " .. sz(m) ..
    "\nПосещения: сегодня {lime}" .. st.today .. "{} (гостей " .. st.todayUniq .. "), за неделю " .. st.week ..
    " (гостей " .. st.weekUniq .. "), трафик за неделю " .. sz(st.weekBytes) .. ", всего страниц " .. st.total ..
    "\n" .. table.concat(bars, "\n") ..
    "\nЯщики: " .. (#boxes > 0 and table.concat(boxes, ", ") or "писем пока нет")
end
local quota = u.quota or H.DEFAULT_QUOTA
local pct = math.min(100, math.floor(total * 100 / math.max(1, quota)))
local bar = string.rep("█", math.floor(pct / 5)) .. string.rep("░", 20 - math.floor(pct / 5))
local left = u.till and H.daysLeft(u.till)
local termLine = "Срок работы: " .. (left == nil and "без срока"
  or left < 0 and ("{red}закончился " .. H.fmtDate(u.till) .. "{} — продлевает владелец хостинга")
  or (left <= 3 and "{yellow}" or "{lime}") .. "до " .. H.fmtDate(u.till) .. "{} · осталось " .. left .. " дн.")
return "# Хостинг: " .. p.login .. "\nМесто: {" .. (pct > 90 and "red" or "lime") .. "}" .. bar .. "{} " .. sz(total) ..
  " из " .. sz(quota) .. "\n" .. termLine ..
  (u.paused and ("\n{orange}⏸ Все ваши сайты приостановлены{}" .. (u.paused ~= "" and (": «" .. u.paused .. "»") or "")) or "") ..
  "\n---\n" ..
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
      local state, why = H.state(site, db)
      sites[#sites + 1] = { name = site, size = s, files = n, today = st.today, week = st.week, weekBytes = st.weekBytes,
        state = state, reason = state == "paused" and why or nil }
    end
    table.sort(sites, function(p, q) return p.name < q.name end)
    return true, { user = user, quota = u.quota, used = used(u), sites = sites, till = u.till,
      left = u.till and H.daysLeft(u.till), paused = u.paused }
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
    local state, why = H.state(site, db)
    return true, { url = site, size = s, used = used(u), quota = u.quota, state = state, reason = why,
      till = u.till, left = u.till and H.daysLeft(u.till) }
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
  -- напоминания о конце срока работы: при запуске и раз в час
  pcall(H.remind)
  local remindTimer = os.startTimer(3600)
  while true do
    local ev = { os.pullEvent() }
    if ev[1] == "timer" and ev[2] == remindTimer then
      pcall(H.remind)
      remindTimer = os.startTimer(3600)
    elseif ev[1] == "net_message" and ev[3] == PORT and ev[5] == "HOST" then
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
elseif cmd == "until" then
  if not a1 or not a2 then print("hostd until <логин> <30.11.2026 | +30 | ->") return end
  local ok, res = H.setUntil(a1, a2)
  if not ok then printError(res) return end
  if res then
    print("Срок работы сайтов " .. a1 .. ": до " .. H.fmtDate(res) .. " (осталось " .. H.daysLeft(res) .. " дн.)")
  else
    print("Срок работы сайтов " .. a1 .. ": без срока")
  end
elseif cmd == "pause" then
  if not a1 then print("hostd pause <сайт|логин> [причина]") return end
  local target = a1
  if not loadDb().users[target] and not target:match("%.mine$") then target = target .. ".mine" end
  local ok, res = H.pause(target, table.concat(args, " ", 3))
  if not ok then printError(res) return end
  print((res == "site" and ("Сайт " .. target) or ("Все сайты клиента " .. target)) ..
    " приостановлены: посетители видят заглушку, почта работает. Включить: hostd resume " .. a1)
elseif cmd == "resume" then
  if not a1 then print("hostd resume <сайт|логин>") return end
  local target = a1
  if not loadDb().users[target] and not target:match("%.mine$") then target = target .. ".mine" end
  local ok, res = H.resume(target)
  if not ok then printError(res) return end
  print((res == "site" and ("Сайт " .. target) or ("Сайты клиента " .. target)) .. " снова работают" ..
    ((res == "site" and H.state(target) ~= "ok") and " (но клиент приостановлен или срок вышел)" or ""))
elseif cmd == "list" then
  local db = loadDb()
  local any = false
  for name, u in pairs(db.users) do
    any = true
    if term.isColor() then term.setTextColor(colors.yellow) end
    write(name)
    term.setTextColor(colors.white)
    write("  " .. kb(used(u)) .. " из " .. kb(u.quota or DEFAULT_QUOTA) .. "  ")
    local left = u.till and H.daysLeft(u.till)
    if term.isColor() then term.setTextColor(left == nil and colors.lightGray or left < 0 and colors.red or left <= 3 and colors.yellow or colors.white) end
    print(left == nil and "без срока" or left < 0 and ("срок вышел " .. H.fmtDate(u.till)) or ("до " .. H.fmtDate(u.till) .. " (" .. left .. " дн.)"))
    term.setTextColor(colors.white)
    if u.paused then
      if term.isColor() then term.setTextColor(colors.yellow) end
      print("   ⏸ все сайты приостановлены" .. (u.paused ~= "" and (": " .. u.paused) or ""))
      term.setTextColor(colors.white)
    end
    for site in pairs(u.sites or {}) do
      local st = H.stats(site)
      local state, why = H.state(site, db)
      write("   " .. site .. "  " .. kb((size(SITES .. "/" .. site))) .. "  заходов сегодня " .. st.today .. ", за неделю " .. st.week)
      if state == "paused" and db.paused and db.paused[site] then
        if term.isColor() then term.setTextColor(colors.yellow) end
        write("  ⏸" .. (why ~= "" and (" " .. why) or ""))
        term.setTextColor(colors.white)
      end
      print()
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
  print("hostd [stop | add | del | quota | passwd | until | pause | resume | list]")
end
