-- Хостинг на этом компьютере: клиенты, место, сайты, посещения. Общая часть для hostd и программы «Хостинг» в Wired.
--   local H = require("hostadm")
--   H.clients()               → { {login, quota, used, sites = { {name, size, files, mail, stats} } } }
--   H.add(login, пароль, КБ)  H.del(login)  H.quota(login, КБ)  H.passwd(login, пароль)  H.removeSite(site)
--   H.stats(ключ)             → посещения сайта (ключ — имя .mine или "www")
--   H.running()  H.start()  H.stop()  H.url()
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

--- Все клиенты с сайтами, местом и посещениями.
function H.clients()
  local db = H.load()
  local out = {}
  for login, u in pairs(db.users) do
    local c = { login = login, quota = u.quota or H.DEFAULT_QUOTA, used = 0, sites = {} }
    for site in pairs(u.sites or {}) do
      local sz, files = H.size(H.SITES .. "/" .. site)
      local mail = H.size(H.MAIL .. "/" .. site)
      local boxes = fs.isDir(H.MAIL .. "/" .. site) and fs.list(H.MAIL .. "/" .. site) or {}
      c.used = c.used + sz + mail
      c.sites[#c.sites + 1] = { name = site, size = sz, files = files, mail = mail, boxes = boxes, stats = H.stats(site) }
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
