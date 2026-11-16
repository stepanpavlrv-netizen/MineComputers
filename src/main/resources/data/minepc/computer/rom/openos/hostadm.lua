-- Хостинг на этом компьютере: клиенты, место, сайты, посещения. Общая часть для hostd и программы «Хостинг» в Wired.
--   local H = require("hostadm")
--   H.clients()               → { {login, quota, used, sites = { {name, size, files, mail, stats} } } }
--   H.add(login, пароль, КБ)  H.del(login)  H.quota(login, КБ)  H.passwd(login, пароль)  H.removeSite(site)
--   H.stats(ключ)             → посещения сайта (ключ — имя .mine или "www")
--   H.running()  H.start()  H.stop()  H.url()
--   H.setUntil(login, "30.11.2026" | "+30" | "-")  H.pause(сайт или login, причина)  H.resume(сайт или login)
--   H.state(сайт) → "ok" | "paused", причина | "expired", дата   H.stub(сайт) → заглушка для посетителей
local H = {
  SELF = "/rom/programs/hostd.lua", HTTPD = "/rom/programs/httpd.lua", MAILD = "/rom/programs/maild.lua",
  DB = "/etc/hosting.db", SITES = "/sites", MAIL = "/hosting/mail", STATS = "/var/httpd/stats",
  DEFAULT_QUOTA = 512 * 1024,
}

function H.proc(path)
  for _, p in ipairs(computer.processes()) do
    if p.path == path and p.pid ~= computer.pid() then return p.pid end
  end
end

function H.running() return H.proc(H.SELF) end

function H.autostart(line, on)
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

local function readTable(path)
  if not fs.exists(path) then return nil end
  local f = fs.open(path, "r")
  if not f then return nil end
  local fn = load("return " .. (f.readAll() or ""), "data", "t", {})
  f.close()
  local ok, t = pcall(fn or function() end)
  return ok and type(t) == "table" and t or nil
end

function H.load()
  local db = readTable(H.DB)
  if not db or type(db.users) ~= "table" then db = { users = {} } end
  return db
end

function H.save(db)
  pcall(fs.makeDir, "/etc")
  local f = fs.open(H.DB, "w")
  if f then f.write(textutils.serialize(db)) f.close() end
end

function H.hash(s) return data.toHex(data.sha256(s)) end

function H.size(path)
  if not fs.exists(path) then return 0, 0 end
  if not fs.isDir(path) then return fs.getSize(path) or 0, 1 end
  local total, n = 0, 0
  for _, name in ipairs(fs.list(path)) do
    local s, c = H.size(fs.combine(path, name))
    total, n = total + s, n + c
  end
  return total, n
end

function H.used(user)
  local total = 0
  for site in pairs(user.sites or {}) do
    total = total + H.size(H.SITES .. "/" .. site) + H.size(H.MAIL .. "/" .. site)
  end
  return total
end

--- Посещения сайта: сегодня, за 7 дней, всего, по дням (7 последних, старые слева).
function H.stats(key)
  local s = readTable(H.STATS .. "/" .. key) or {}
  local days = s.days or {}
  local out = { total = s.views or 0, bytes = s.bytes or 0, today = 0, todayUniq = 0, week = 0, weekBytes = 0, weekUniq = 0, series = {} }
  local now = os.time and os.time() or 0
  local seen = {}
  for i = 6, 0, -1 do
    local d = os.date("%Y-%m-%d", now - i * 86400)
    local day = days[d] or {}
    local views = day.views or 0
    out.series[#out.series + 1] = { date = os.date("%d.%m", now - i * 86400), views = views }
    out.week = out.week + views
    out.weekBytes = out.weekBytes + (day.bytes or 0)
    for a in pairs(day.uniq or {}) do seen[a] = true end
    if i == 0 then
      out.today = views
      for _ in pairs(day.uniq or {}) do out.todayUniq = out.todayUniq + 1 end
    end
  end
  for _ in pairs(seen) do out.weekUniq = out.weekUniq + 1 end
  return out
end

--- Записать заход на сайт (вызывает httpd). page — это страница (а не картинка/файл).
function H.record(key, from, bytes, page)
  local path = H.STATS .. "/" .. key
  local s = readTable(path) or {}
  s.days = s.days or {}
  local d = os.date("%Y-%m-%d")
  local day = s.days[d] or { views = 0, bytes = 0, uniq = {} }
  day.bytes = (day.bytes or 0) + bytes
  s.bytes = (s.bytes or 0) + bytes
  if page then
    day.views = (day.views or 0) + 1
    s.views = (s.views or 0) + 1
    day.uniq = day.uniq or {}
    day.uniq[tostring(from):sub(1, 8)] = true
  end
  s.days[d] = day
  local keys = {}
  for k in pairs(s.days) do keys[#keys + 1] = k end
  table.sort(keys)
  while #keys > 14 do s.days[table.remove(keys, 1)] = nil end
  pcall(fs.makeDir, H.STATS)
  local f = fs.open(path, "w")
  if f then f.write(textutils.serialize(s)) f.close() end
end

-- ======================= срок работы и приостановка =======================
-- Срок — у клиента (u.till = "ГГГГ-ММ-ДД", nil — без срока), на все его сайты. Приостановить можно клиента
-- целиком (u.paused = причина) или один сайт (db.paused[сайт] = причина). Ничего не удаляется.

local function iso(y, m, d) return string.format("%04d-%02d-%02d", y, m, d) end

local function timeOf(isoDate)
  local y, m, d = tostring(isoDate or ""):match("^(%d+)-(%d+)-(%d+)$")
  if not y then return nil end
  return os.time({ year = tonumber(y), month = tonumber(m), day = tonumber(d), hour = 12 })
end

function H.today() return os.date("%Y-%m-%d") end

--- «30.11.2026», «30.11» (этот год) или «2026-11-30» → «2026-11-30»; nil — не дата.
function H.parseDate(s)
  s = tostring(s or "")
  local d, m, y = s:match("^(%d%d?)%.(%d%d?)%.(%d%d%d%d)$")
  if not d then d, m = s:match("^(%d%d?)%.(%d%d?)$") y = os.date("%Y") end
  if d then
    d, m, y = tonumber(d), tonumber(m), tonumber(y)
    if m < 1 or m > 12 or d < 1 or d > 31 then return nil end
    return iso(y, m, d)
  end
  local yy, mm, dd = s:match("^(%d%d%d%d)-(%d%d?)-(%d%d?)$")
  if yy then return iso(tonumber(yy), tonumber(mm), tonumber(dd)) end
  return nil
end

--- «2026-11-30» → «30.11.2026».
function H.fmtDate(isoDate)
  local y, m, d = tostring(isoDate or ""):match("^(%d+)-(%d+)-(%d+)$")
  return y and (d .. "." .. m .. "." .. y) or "—"
end

--- Сколько дней до конца срока (0 — сегодня последний день, меньше нуля — срок вышел); nil — без срока.
function H.daysLeft(isoDate)
  local t, now = timeOf(isoDate), timeOf(H.today())
  if not t or not now then return nil end
  return math.floor((t - now) / 86400 + 0.5)
end

--- Срок работы клиента: дата, «+N» дней (от текущего срока, а если он вышел или его нет — от сегодня) или «-».
function H.setUntil(login, spec)
  local db = H.load()
  local u = db.users[login]
  if not u then return false, "Нет клиента " .. tostring(login) end
  spec = tostring(spec or ""):gsub("%s", "")
  local value
  if spec == "-" or spec == "" or spec == "нет" then
    value = nil
  elseif spec:match("^%+%d+$") then
    local base = u.till
    if not base or (H.daysLeft(base) or -1) < 0 then base = H.today() end
    value = os.date("%Y-%m-%d", timeOf(base) + tonumber(spec:sub(2)) * 86400)
  else
    value = H.parseDate(spec)
    if not value then return false, "Не понимаю дату «" .. spec .. "»: нужно 30.11.2026, +30 или -" end
  end
  u.till = value
  u.reminded = nil
  H.save(db)
  return true, value
end

--- Чей сайт: логин клиента или nil.
function H.owner(site, db)
  db = db or H.load()
  for login, u in pairs(db.users) do if u.sites and u.sites[site] then return login, u end end
  return nil
end

--- Приостановить сайт (если target — сайт) или всех сайтов клиента (если target — логин).
function H.pause(target, reason)
  local db = H.load()
  reason = tostring(reason or "")
  if H.owner(target, db) then
    db.paused = db.paused or {}
    db.paused[target] = reason
    H.save(db)
    return true, "site"
  elseif db.users[target] then
    db.users[target].paused = reason
    H.save(db)
    return true, "client"
  end
  return false, "Нет сайта или клиента «" .. tostring(target) .. "»"
end

function H.resume(target)
  local db = H.load()
  if db.paused and db.paused[target] then
    db.paused[target] = nil
    H.save(db)
    return true, "site"
  elseif db.users[target] then
    db.users[target].paused = nil
    H.save(db)
    return true, "client"
  end
  return false, "«" .. tostring(target) .. "» не приостановлен"
end

--- Состояние сайта: "ok" | "paused", причина | "expired", дата.
function H.state(site, db)
  db = db or H.load()
  if db.paused and db.paused[site] then return "paused", db.paused[site] end
  local _, u = H.owner(site, db)
  if u then
    if u.paused then return "paused", u.paused end
    if u.till and (H.daysLeft(u.till) or 0) < 0 then return "expired", u.till end
  end
  return "ok"
end

--- Заглушка, которую видит посетитель приостановленного сайта.
function H.stub(site, st, info)
  local host = net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)
  local okN, names = net.dns("names")
  if okN and names and names[1] then host = names[1].name end
  local why
  if st == "expired" then
    why = "Срок работы сайта закончился " .. H.fmtDate(info) .. ".\n---\n> Сайт вернётся, когда срок продлят. Письма на " .. site .. " доходят."
  else
    why = "Его приостановил владелец хостинга." .. ((info and info ~= "") and ("\n---\n> Причина: «" .. info .. "»") or "") ..
      "\n> Письма на " .. site .. " доходят."
  end
  return "# Сайт " .. site .. " временно не работает\n" .. why .. "\n\n{gray}Хостинг " .. host .. "{}"
end

--- Напоминание за 3 дня до конца срока: письмо в ящик admin@ каждого сайта клиента (один раз на каждый срок).
function H.remind()
  local db = H.load()
  local changed = false
  for login, u in pairs(db.users) do
    local left = u.till and H.daysLeft(u.till)
    if left and left >= 0 and left <= 3 and u.reminded ~= u.till then
      local names = {}
      for site in pairs(u.sites or {}) do names[#names + 1] = site end
      table.sort(names)
      if #names > 0 then
        local host = net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)
        local body = "Срок работы ваших сайтов (" .. table.concat(names, ", ") .. ")\nзаканчивается " ..
          (left == 0 and "сегодня" or ("через " .. left .. " дн.")) .. " — " .. H.fmtDate(u.till) .. ".\n" ..
          "Чтобы продлить, свяжитесь с владельцем хостинга.\nФайлы и почта сохранятся в любом случае."
        for _, site in ipairs(names) do
          local dir = H.MAIL .. "/" .. site .. "/admin"
          pcall(fs.makeDir, dir)
          local f = fs.open(string.format("%s/%d-%04d.txt", dir, math.floor(os.epoch and os.epoch("utc") or 0), math.random(0, 9999)), "w")
          if f then
            f.write("От: hosting@" .. host .. "\nТема: Срок работы сайтов заканчивается " .. H.fmtDate(u.till) ..
              "\nВремя: " .. os.date("%d.%m %H:%M") .. "\nНовое: да\n\n" .. body)
            f.close()
          end
        end
      end
      u.reminded = u.till
      changed = true
    end
  end
  if changed then H.save(db) end
end

--- Все клиенты с сайтами, местом и посещениями.
function H.clients()
  local db = H.load()
  local out = {}
  for login, u in pairs(db.users) do
    local c = { login = login, quota = u.quota or H.DEFAULT_QUOTA, used = 0, sites = {},
      till = u.till, left = u.till and H.daysLeft(u.till), paused = u.paused }
    for site in pairs(u.sites or {}) do
      local sz, files = H.size(H.SITES .. "/" .. site)
      local mail = H.size(H.MAIL .. "/" .. site)
      local boxes = fs.isDir(H.MAIL .. "/" .. site) and fs.list(H.MAIL .. "/" .. site) or {}
      c.used = c.used + sz + mail
      local st, info = H.state(site, db)
      c.sites[#c.sites + 1] = { name = site, size = sz, files = files, mail = mail, boxes = boxes, stats = H.stats(site),
        state = st, reason = st == "paused" and info or nil, sitePaused = db.paused and db.paused[site] ~= nil }
    end
    table.sort(c.sites, function(a, b) return a.name < b.name end)
    out[#out + 1] = c
  end
  table.sort(out, function(a, b) return a.login < b.login end)
  return out
end

function H.add(login, password, kb)
  login = tostring(login or ""):gsub("%s", "")
  if login == "" or not password or password == "" then return false, "Нужны логин и пароль" end
  local db = H.load()
  if db.users[login] then return false, "Клиент " .. login .. " уже есть" end
  db.users[login] = { pass = H.hash(password), quota = math.floor((tonumber(kb) or H.DEFAULT_QUOTA / 1024) * 1024), sites = {} }
  H.save(db)
  return true
end

function H.removeSite(site)
  local db0 = H.load()
  if db0.paused and db0.paused[site] then db0.paused[site] = nil H.save(db0) end
  if fs.exists(H.SITES .. "/" .. site) then fs.delete(H.SITES .. "/" .. site) end
  if fs.exists(H.MAIL .. "/" .. site) then fs.delete(H.MAIL .. "/" .. site) end
  if fs.exists(H.STATS .. "/" .. site) then fs.delete(H.STATS .. "/" .. site) end
  pcall(net.dns, "unregister", site)
  local db = H.load()
  for _, u in pairs(db.users) do if u.sites then u.sites[site] = nil end end
  H.save(db)
  return true
end

function H.del(login)
  local db = H.load()
  local u = db.users[login]
  if not u then return false, "Нет клиента " .. tostring(login) end
  for site in pairs(u.sites or {}) do H.removeSite(site) end
  db = H.load()
  db.users[login] = nil
  H.save(db)
  return true
end

function H.quota(login, kb)
  local db = H.load()
  local u = db.users[login]
  if not u or not tonumber(kb) then return false, "Нет клиента или размера" end
  u.quota = math.floor(tonumber(kb) * 1024)
  H.save(db)
  return true
end

function H.passwd(login, password)
  local db = H.load()
  local u = db.users[login]
  if not u or not password or password == "" then return false, "Нет клиента или пароля" end
  u.pass = H.hash(password)
  H.save(db)
  return true
end

--- Включить хостинг (и сайты, и почту), включаться при запуске компьютера.
function H.start()
  H.autostart(H.SELF .. " serve", true)
  H.autostart(H.HTTPD .. " serve", true)
  H.autostart(H.MAILD .. " serve", true)
  if not H.proc(H.HTTPD) then pcall(computer.spawn, H.HTTPD, "serve") end
  if not H.proc(H.MAILD) then pcall(computer.spawn, H.MAILD, "serve") end
  pcall(fs.makeDir, H.SITES)
  local pid = H.running()
  if not pid then
    local ok, p = pcall(computer.spawn, H.SELF, "serve")
    if not ok then return nil, p end
    pid = p
  end
  return pid
end

function H.stop()
  H.autostart(H.SELF .. " serve", false)
  local pid = H.running()
  return pid and computer.kill(pid) or false
end

--- Адрес панели клиентов.
function H.url()
  local ok, names = net.dns("names")
  local host = (ok and names and names[1]) and names[1].name or (net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8))
  return host .. "/hosting.lua"
end

return H
