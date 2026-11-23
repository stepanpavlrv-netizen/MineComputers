-- SWired: серверная часть — службы, состояние железа, журнал, резервные копии, управление по сети.
-- Ей пользуются программы SWired (окна), надзиратель swiredd (фон) и «Серверы» в обычном Wired.
--   local S = require("swired")
--   S.services()            → службы: { id, name, desc, running, autostart, restart, mail, crashes, custom }
--   S.start(id) / S.stop(id) / S.restart(id) / S.set(id, "autostart"|"restart"|"mail", значение)
--   S.status()              → сводка: имя, адрес, время работы, нагрузка, железо (bios.server), замечания
--   S.journal(n, filter)    → последние события: { time, src, level ("ok"|"warn"|"err"|"info"), text }
--   S.log(src, level, text) — записать событие в журнал
--   S.backups() / S.saveBackup(job) / S.removeBackup(id) / S.runBackup(id) / S.restore(id, stamp)
--   S.remote(адрес, пользователь, пароль) → тот же набор функций, но на другом сервере (по сети)
--   S.discover(секунд)      → серверы с SWired в сети
local S = { PORT = 2200, VERSION = "1.0", CHUNK = 6000 }

local CFG = "/etc/swired.cfg"
local STATE = "/var/run/swired.state"
local LOG = "/var/log/swired.log"
local LOG_MAX = 400
local BACKUP_DIR = "/backups"

-- ======================= файлы =======================

local function readFile(p)
  local f = fs.exists(p) and fs.open(p, "r")
  if not f then return nil end
  local d = f.readAll()
  f.close()
  return d
end

local function writeFile(p, d)
  local dir = fs.getDir(p)
  if dir ~= "" and not fs.exists(dir) then fs.makeDir(dir) end
  local f = fs.open(p, "w")
  if not f then return false end
  f.write(d)
  f.close()
  return true
end

local function loadTable(p)
  local t = readFile(p)
  if not t or t == "" then return {} end
  local fn = load("return " .. t, "data", "t", {})
  local ok, v = pcall(fn or function() end)
  return ok and type(v) == "table" and v or {}
end

local function saveTable(p, t) return writeFile(p, textutils.serialize(t)) end

function S.config()
  local c = loadTable(CFG)
  c.services = c.services or {}
  c.custom = c.custom or {}
  c.backups = c.backups or {}
  return c
end

function S.saveConfig(c) return saveTable(CFG, c) end

--- Когда включился компьютер (секунды по настоящим часам): по нему надзиратель отличает «включили заново» от
--- «перезапустили надзирателя».
function S.bootStamp()
  return math.floor(((os.epoch and os.epoch("utc") or 0) - os.clock() * 1000) / 1000)
end
function S.state() return loadTable(STATE) end
function S.saveState(st)
  st.boot = S.bootStamp()
  return saveTable(STATE, st)
end

-- ======================= журнал =======================

function S.log(src, level, text)
  local lines = {}
  local old = readFile(LOG)
  if old then for l in old:gmatch("[^\r\n]+") do lines[#lines + 1] = l end end
  lines[#lines + 1] = table.concat({ tostring(os.time()), tostring(src or "Сервер"), tostring(level or "info"),
    (tostring(text or ""):gsub("[\t\r\n]", " ")) }, "\t")
  while #lines > LOG_MAX do table.remove(lines, 1) end
  writeFile(LOG, table.concat(lines, "\n") .. "\n")
end

local FILTERS = {
  logins = { ["Вход по сети"] = true, ["Вход"] = true, ["Пользователи"] = true },
  services = { ["Службы"] = true },
  hardware = { ["Диски"] = true, ["Питание"] = true, ["Процессоры"] = true, ["BMC"] = true, ["RAID"] = true },
  sites = { ["Хостинг"] = true, ["Сайты"] = true, ["Почта"] = true },
  backups = { ["Копии"] = true },
}

--- Последние n событий, новые сверху. filter: nil | "errors" | "logins" | "services" | "hardware" | "sites" | "backups".
function S.journal(n, filter, find)
  local out = {}
  local d = readFile(LOG) or ""
  for l in d:gmatch("[^\r\n]+") do
    local t, src, lvl, text = l:match("^(%d+)\t([^\t]*)\t([^\t]*)\t(.*)$")
    if t then
      local e = { time = tonumber(t), src = src, level = lvl, text = text }
      local keep = true
      if filter == "errors" then keep = lvl == "err" or lvl == "warn"
      elseif filter and FILTERS[filter] then keep = FILTERS[filter][src] or false end
      if keep and find and find ~= "" then
        local low = unicode and unicode.lower or string.lower
        keep = (low(text .. " " .. src)):find(low(find), 1, true) ~= nil
      end
      if keep then out[#out + 1] = e end
    end
  end
  local total = #out
  local res = {}
  for i = #out, math.max(1, #out - (n or 50) + 1), -1 do res[#res + 1] = out[i] end
  return res, total
end

function S.fmtTime(t)
  if not t then return "—" end
  if os.date("%d.%m.%Y", t) == os.date("%d.%m.%Y") then return os.date("%H:%M", t) end
  return os.date("%d.%m %H:%M", t)
end

--- Вид строки журнала входов (auth.journal): источник и уровень.
local function authLevel(line)
  if line:find("неверн") or line:find("закрыт") or line:find("нет пользователя") or line:find("не администратор") then
    return "err"
  elseif line:find("файрвол") then return "warn" end
  return "ok"
end

--- Перенести в журнал SWired новые строки журнала входов и журнала BMC (их пишут другие части системы).
--- Журнал входов читает только администратор: без прав переносится только журнал BMC.
function S.importLogs(st)
  st.seen = st.seen or {}
  local function seen(kind, list, src, level)
    local old = st.seen[kind] or {}
    local was = {}
    for _, l in ipairs(old) do was[l] = (was[l] or 0) + 1 end
    for _, l in ipairs(list) do
      if (was[l] or 0) > 0 then was[l] = was[l] - 1
      else
        local s, lv = src, level
        if type(src) == "function" then s, lv = src(l) end
        S.log(s, lv, l)
      end
    end
    st.seen[kind] = list
  end
  if auth and auth.journal then
    local ok, lines = pcall(auth.journal, 40)
    if ok and type(lines) == "table" then
      seen("auth", lines, function(l) return "Вход", authLevel(l) end)
    end
  end
  if bios and bios.server then
    local ok, info = pcall(bios.server)
    if ok and type(info) == "table" and type(info.log) == "table" then
      local list = {}
      for _, l in ipairs(info.log) do list[#list + 1] = (l:gsub("^день %d+ %d+:%d+%s+", "")) end
      seen("bmc", list, function(l)
        local src = l:find("диск") and "Диски" or l:find("питани") and "Питание" or l:find("RAID") and "RAID" or "BMC"
        local lv = (l:find("выпал") or l:find("развалился") or l:find("отказал") or l:find("не хватило") or l:find("неверн"))
          and "err" or (l:find("вынут") or l:find("извлечён") or l:find("перегрев") or l:find("без резерва")) and "warn" or "info"
        return src, lv
      end)
    end
  end
end

--- Перенести журнал входов: пароль администратора (того, кто вошёл) повышает права на время чтения.
function S.importAuth(sudoPw)
  local up = false
  if sudoPw and auth and auth.sudo then
    local ok, err = auth.sudo(sudoPw)
    if not ok then return nil, tostring(err or "Неверный пароль") end
    up = true
  end
  local st = S.state()
  local ok, err = pcall(S.importLogs, st)
  S.saveState(st)
  if up and auth.unsudo then pcall(auth.unsudo) end
  if not ok then return nil, tostring(err) end
  return true
end

-- ======================= службы =======================

local KNOWN = {
  { id = "hostd", name = "Хостинг сайтов", path = "/rom/programs/hostd.lua", desc = "сайты и почта клиентов (порты 80 и 25)" },
  { id = "httpd", name = "Сайты", path = "/rom/programs/httpd.lua", desc = "раздаёт /www и сайты хостинга, порт 80" },
  { id = "maild", name = "Почта", path = "/rom/programs/maild.lua", desc = "принимает письма, порт 25" },
  { id = "sshd", name = "Вход по сети", path = "/rom/programs/sshd.lua", desc = "connect <сервер> <пользователь>, порт 22" },
}
S.SWIREDD = "/rom/programs/swiredd.lua"

local function procs()
  local ok, list = pcall(computer.processes)
  return ok and type(list) == "table" and list or {}
end

local function pidOf(path, list)
  for _, p in ipairs(list or procs()) do
    if p.path == path and p.pid ~= computer.pid() then return p.pid end
  end
end
S.pidOf = pidOf

local function autostartLines()
  local set = {}
  local d = readFile("/etc/services") or ""
  for l in d:gmatch("[^\r\n]+") do set[l] = true end
  return set
end

--- Строку «путь аргументы» включить в /etc/services или убрать оттуда.
function S.autostartLine(line, on)
  local lines, have = {}, false
  local d = readFile("/etc/services") or ""
  for l in d:gmatch("[^\r\n]+") do
    if l == line then have = true else lines[#lines + 1] = l end
  end
  if on then lines[#lines + 1] = line elseif not have then return true end
  return writeFile("/etc/services", table.concat(lines, "\n") .. (#lines > 0 and "\n" or ""))
end

local function defs(cfg)
  local out = {}
  for _, k in ipairs(KNOWN) do out[#out + 1] = { id = k.id, name = k.name, path = k.path, args = "serve", desc = k.desc } end
  for _, c in ipairs(cfg.custom) do
    out[#out + 1] = { id = c.id, name = c.name, path = c.path, args = c.args or "", desc = c.path, custom = true }
  end
  return out
end

local function lineOf(d) return d.args ~= "" and (d.path .. " " .. d.args) or d.path end

local function find(id, cfg)
  for _, d in ipairs(defs(cfg or S.config())) do if d.id == id then return d end end
end

function S.services()
  local cfg, st, list, auto = S.config(), S.state(), procs(), autostartLines()
  local out = {}
  for _, d in ipairs(defs(cfg)) do
    local o = cfg.services[d.id] or {}
    local pid = pidOf(d.path, list)
    out[#out + 1] = {
      id = d.id, name = d.name, desc = d.desc, path = d.path, custom = d.custom,
      running = pid ~= nil, pid = pid, autostart = auto[lineOf(d)] or false,
      restart = o.restart ~= false, mail = o.mail or "",
      since = st.started and st.started[d.id], crashes = st.crashes and st.crashes[d.id] or 0,
      failed = st.failed and st.failed[d.id] or false,
    }
  end
  return out
end

--- Служба должна работать (true), остановлена вручную (false); nil — как в автозапуске.
local function wanted(st, id, on)
  st.wanted = st.wanted or {}
  st.wanted[id] = on and true or false
end

--- Должна ли служба сейчас работать: запущена вручную или стоит в автозапуске (и не остановлена вручную).
function S.wants(st, d, auto)
  local w = st.wanted and st.wanted[d.id]
  if w ~= nil then return w end
  return auto[lineOf(d)] or false
end
S.defs = function() return defs(S.config()) end
S.lineOf = lineOf
S.autostartLines = autostartLines

function S.start(id)
  local cfg = S.config()
  local d = find(id, cfg)
  if not d then return nil, "Нет такой службы" end
  local st = S.state()
  if d.id == "hostd" then
    local okH, H = pcall(require, "hostadm")
    if okH then
      local ok, err = H.start()
      if not ok and err then return nil, err end
    end
  elseif not pidOf(d.path) then
    local args = {}
    for a in d.args:gmatch("%S+") do args[#args + 1] = a end
    local ok, err = pcall(computer.spawn, d.path, table.unpack(args))
    if not ok then return nil, tostring(err) end
  end
  wanted(st, d.id, true)
  st.started = st.started or {}
  st.started[d.id] = os.time()
  if st.failed then st.failed[d.id] = nil end
  if st.crashes then st.crashes[d.id] = nil end
  S.saveState(st)
  S.log("Службы", "ok", "«" .. d.name .. "» запущена")
  return true
end

function S.stop(id)
  local d = find(id)
  if not d then return nil, "Нет такой службы" end
  local st = S.state()
  wanted(st, d.id, false)
  if st.started then st.started[d.id] = nil end
  S.saveState(st)
  if d.id == "hostd" then
    local okH, H = pcall(require, "hostadm")
    if okH and H.stop then pcall(H.stop) end
  end
  local pid = pidOf(d.path)
  if pid then pcall(computer.kill, pid) end
  S.log("Службы", "info", "«" .. d.name .. "» остановлена")
  return true
end

function S.restart(id)
  local d = find(id)
  if not d then return nil, "Нет такой службы" end
  local pid = pidOf(d.path)
  if pid then pcall(computer.kill, pid) end
  return S.start(id)
end

--- Настройка службы: autostart (запуск при включении), restart (перезапуск при падении), mail (кому письмо).
function S.set(id, key, value)
  local cfg = S.config()
  local d = find(id, cfg)
  if not d then return nil, "Нет такой службы" end
  if key == "autostart" then
    S.autostartLine(lineOf(d), value and true or false)
    if d.id == "hostd" and value then
      S.autostartLine("/rom/programs/httpd.lua serve", true)
      S.autostartLine("/rom/programs/maild.lua serve", true)
    end
  else
    cfg.services[d.id] = cfg.services[d.id] or {}
    cfg.services[d.id][key] = value
    S.saveConfig(cfg)
  end
  return true
end

--- Своя служба: программа .lua, которая работает в фоне.
function S.addCustom(name, path, args)
  if type(name) ~= "string" or name == "" then return nil, "Нужно название" end
  path = "/" .. fs.combine(tostring(path or ""), "")
  if not fs.exists(path) or fs.isDir(path) then return nil, "Нет файла " .. path end
  local cfg = S.config()
  local id = "c" .. tostring(os.time()) .. tostring(math.random(100, 999))
  cfg.custom[#cfg.custom + 1] = { id = id, name = name, path = path, args = args or "" }
  S.saveConfig(cfg)
  S.log("Службы", "info", "добавлена своя служба «" .. name .. "» (" .. path .. ")")
  return id
end

function S.removeCustom(id)
  local cfg = S.config()
  for i, c in ipairs(cfg.custom) do
    if c.id == id then
      S.stop(id)
      S.autostartLine(lineOf({ path = c.path, args = c.args or "" }), false)
      table.remove(cfg.custom, i)
      cfg.services[id] = nil
      S.saveConfig(cfg)
      return true
    end
  end
  return nil, "Нет такой службы"
end

--- Последние строки работы службы (то, что она пишет в /var/log/<id>.log).
function S.serviceLog(id, n)
  local d = readFile("/var/log/" .. tostring(id):gsub("[^%w_%-]", "") .. ".log") or ""
  local lines = {}
  for l in d:gmatch("[^\r\n]+") do lines[#lines + 1] = l end
  local out = {}
  for i = math.max(1, #lines - (n or 8) + 1), #lines do out[#out + 1] = lines[i] end
  return out
end

-- ======================= письма =======================

--- Письмо (имя@компьютер): без ожидания ответа — надзиратель не должен замирать.
function S.mail(to, subject, body)
  if type(to) ~= "string" then return end
  local user, host = to:match("^([^@]+)@(.+)$")
  if not user or not net or not net.send then return end
  local me = (net.hostname and net.hostname() ~= "" and net.hostname()) or net.address():sub(1, 8)
  pcall(net.send, host, 25, "MAIL", math.random(1, 1000000000), math.random(40000, 49999), user, me, subject, body,
    (unicode and unicode.lower or string.lower)(host))
end

-- ======================= состояние =======================

local function pct(a, b) if not b or b <= 0 then return 0 end return math.floor(a * 100 / b + 0.5) end

function S.status()
  local s = {}
  s.version = S.VERSION
  s.name = net and net.hostname and net.hostname() or ""
  if s.name == "" then s.name = computer.getLabel and computer.getLabel() or "" end
  s.address = net and net.address and net.address() or ""
  if s.name == "" then s.name = s.address:sub(1, 8) end
  s.uptime = computer.uptime and computer.uptime() or 0
  local okS, st = pcall(computer.stats)
  s.stats = okS and st or {}
  local okH, hw = pcall(function() return bios and bios.server and bios.server() end)
  s.hw = okH and hw or nil
  local okR, raid = pcall(function() return bios and bios.raid and bios.raid() end)
  s.raid = okR and raid or nil
  s.services = S.services()
  s.backups = S.backups()
  -- замечания: что не так, по важности
  local p = {}
  local function add(level, text) p[#p + 1] = { level = level, text = text } end
  local hw2 = s.hw
  if hw2 then
    for i, b in ipairs(hw2.bays or {}) do
      if b.state == "failed" then add("err", "диск в отсеке " .. i .. " отказал — замените")
      elseif b.state == "warning" then add("warn", "диск в отсеке " .. i .. ": износ " .. tostring(b.wear) .. " % — пора менять")
      elseif b.state == "rebuilding" then add("warn", "диск в отсеке " .. i .. ": RAID переносит данные") end
    end
    local psu = hw2.psu or {}
    local n = 0
    for _, x in ipairs(psu) do if x.present then n = n + 1 end end
    if n == 0 then add("err", "нет блока питания")
    elseif n == 1 then add("warn", "один блок питания: если откажет — сервер выключится")
    elseif not hw2.reserve then add("warn", "нет резерва питания: один блок не тянет весь сервер") end
    if (hw2.heat or 0) > (hw2.cooling or math.huge) then
      add((hw2.temp or 0) >= 90 and "err" or "warn", "процессоры греются на " .. hw2.heat .. " Вт, а вентиляторы модуля отводят " ..
        hw2.cooling .. " Вт — поставьте «Вентиляторы стойки» помощнее" .. ((hw2.fanTier or 0) >= 3 and " (уже ур. 3: модуль 2U или процессоры слабее)" or ""))
    elseif (hw2.temp or 0) >= 90 then add("err", "перегрев процессоров: " .. hw2.temp .. " °C")
    elseif (hw2.temp or 0) >= 80 then add("warn", "процессоры горячие: " .. hw2.temp .. " °C") end
    if hw2.bmc and hw2.bmc.present and hw2.bmc.defaultPassword then add("warn", "у BMC пароль по умолчанию — смените") end
    local r = hw2.raid
    if type(r) == "table" then
      if r.state == "degraded" then add("err", "RAID работает без резерва")
      elseif r.state == "failed" then add("err", "RAID развалился") end
    end
  end
  for _, d in ipairs(s.stats.disks or {}) do
    if (d.capacity or 0) > 0 and pct(d.used or 0, d.capacity) >= 90 then
      add("warn", "мало места на " .. tostring(d.path) .. ": занято " .. pct(d.used, d.capacity) .. " %")
    end
  end
  for _, sv in ipairs(s.services) do
    if sv.failed then add("err", "служба «" .. sv.name .. "» упала и не поднимается")
    elseif sv.crashes > 0 and sv.running then add("warn", "служба «" .. sv.name .. "» падала: " .. sv.crashes .. " раз") end
  end
  for _, b in ipairs(s.backups) do
    if b.last and not b.last.ok then add("err", "копия «" .. b.name .. "» не сделана: " .. tostring(b.last.err)) end
  end
  if not pidOf(S.SWIREDD) then add("warn", "надзиратель SWired не запущен (службы не перезапускаются, копии не делаются)") end
  table.sort(p, function(a, b) return (a.level == "err" and 0 or 1) < (b.level == "err" and 0 or 1) end)
  s.problems = p
  s.level = #p == 0 and "ok" or (p[1].level == "err" and "err" or "warn")
  return s
end

-- ======================= пользователи =======================

function S.users()
  local out = {}
  local ok, list = pcall(function() return auth and auth.users and auth.users() end)
  local me = auth and auth.user and auth.user() or nil
  for _, u in ipairs(ok and list or {}) do
    out[#out + 1] = { name = u.name, admin = u.admin, me = u.name == me }
  end
  -- последний вход — из журнала
  local j = S.journal(LOG_MAX, "logins")
  for _, u in ipairs(out) do
    for _, e in ipairs(j) do
      if e.level == "ok" and e.text:find(u.name, 1, true) then u.last = e.time break end
    end
  end
  return out
end

--- Изменить пользователей: нужны права администратора — sudoPw (пароль того, кто вошёл) повышает их на время вызова.
local function asAdmin(sudoPw, fn, ...)
  local up = false
  if sudoPw and auth.sudo then
    local ok, err = auth.sudo(sudoPw)
    if not ok then return nil, tostring(err or "Неверный пароль") end
    up = true
  end
  local r = table.pack(pcall(fn, ...))
  if up and auth.unsudo then pcall(auth.unsudo) end
  if not r[1] then return nil, tostring(r[2]) end
  if r[2] == false or (r[2] == nil and r[3]) then return nil, tostring(r[3] or "Нет прав: нужен администратор") end
  return true
end

function S.addUser(name, pw, admin, sudoPw)
  if type(name) ~= "string" or name == "" then return nil, "Нужно имя" end
  if type(pw) ~= "string" or #pw < 4 then return nil, "Пароль — хотя бы 4 знака" end
  local ok, err = asAdmin(sudoPw, auth.add, name, pw, admin and true or false)
  if ok then S.log("Пользователи", "ok", "добавлен " .. name .. (admin and " (администратор)" or "")) end
  return ok, err
end
function S.removeUser(name, sudoPw)
  local ok, err = asAdmin(sudoPw, auth.remove, name)
  if ok then S.log("Пользователи", "warn", "удалён " .. tostring(name)) end
  return ok, err
end
function S.setAdmin(name, on, sudoPw)
  local ok, err = asAdmin(sudoPw, auth.setAdmin, name, on and true or false)
  if ok then S.log("Пользователи", "info", tostring(name) .. (on and " — теперь администратор" or " — больше не администратор")) end
  return ok, err
end
function S.passwd(name, pw, sudoPw)
  if type(pw) ~= "string" or #pw < 4 then return nil, "Пароль — хотя бы 4 знака" end
  local ok, err = asAdmin(sudoPw, auth.passwd, name, pw)
  if ok then S.log("Пользователи", "info", "сменён пароль " .. tostring(name)) end
  return ok, err
end

-- ======================= резервные копии =======================

local function listFiles(dir, prefix, out)
  out = out or {}
  prefix = prefix or ""
  if not fs.isDir(dir) then
    if fs.exists(dir) then out[#out + 1] = prefix end
    return out
  end
  for _, n in ipairs(fs.list(dir)) do
    local p = fs.combine(dir, n)
    if fs.isDir(p) then listFiles(p, prefix .. n .. "/", out) else out[#out + 1] = prefix .. n end
  end
  return out
end

local function readBin(p)
  local f = fs.open(p, "rb") or fs.open(p, "r")
  if not f then return nil end
  local d = f.readAll() or ""
  f.close()
  return d
end

local function writeBin(p, d)
  local dir = fs.getDir(p)
  if dir ~= "" and not fs.exists(dir) then fs.makeDir(dir) end
  local f = fs.open(p, "wb") or fs.open(p, "w")
  if not f then return false end
  f.write(d)
  f.close()
  return true
end

--- Задания копий (без паролей: этот список уходит и по сети).
function S.backups()
  local cfg = S.config()
  local out = {}
  for _, b in ipairs(cfg.backups) do
    local c = {}
    for k, v in pairs(b) do c[k] = v end
    c.hasPassword = (b.password or "") ~= ""
    c.password = nil
    if b.to then c.to = { kind = b.to.kind, target = b.to.target, user = b.to.user } end
    out[#out + 1] = c
  end
  return out
end

local function findJob(id)
  for _, b in ipairs(S.config().backups) do if b.id == id then return b end end
end

--- Добавить или поменять задание копии: { id?, name, dirs = {...}, to = { kind = "path"|"server", target },
--- every = "day"|"week"|"hour"|"manual", at = "06:00", keep = 7, password = "" }.
function S.saveBackup(job)
  if type(job) ~= "table" or type(job.name) ~= "string" or job.name == "" then return nil, "Нужно название" end
  if type(job.dirs) ~= "table" or #job.dirs == 0 then return nil, "Выберите, что копировать" end
  local cfg = S.config()
  job.id = job.id or ("b" .. tostring(os.time()) .. tostring(math.random(100, 999)))
  job.keep = math.max(1, math.min(30, tonumber(job.keep) or 7))
  job.every = job.every or "day"
  job.at = job.at or "06:00"
  local done = false
  for i, b in ipairs(cfg.backups) do
    if b.id == job.id then
      job.history, job.last = b.history, b.last
      if job.password == nil then job.password = b.password end
      if job.to and b.to and job.to.pass == nil and job.to.target == b.to.target then job.to.pass = b.to.pass end
      cfg.backups[i] = job
      done = true
    end
  end
  if not done then cfg.backups[#cfg.backups + 1] = job end
  S.saveConfig(cfg)
  return job.id
end

function S.removeBackup(id)
  local cfg = S.config()
  for i, b in ipairs(cfg.backups) do
    if b.id == id then table.remove(cfg.backups, i) S.saveConfig(cfg) return true end
  end
  return nil, "Нет такой копии"
end

--- Время следующей копии по расписанию (или nil — только вручную).
function S.nextRun(job, now)
  now = now or os.time()
  if job.every == "manual" then return nil end
  local h, m = tostring(job.at or "06:00"):match("^(%d+):(%d+)$")
  h, m = tonumber(h) or 6, tonumber(m) or 0
  local last = job.last and job.last.time or 0
  if job.every == "hour" then
    local t = os.date("*t", now)
    local cand = os.time({ year = t.year, month = t.month, day = t.day, hour = t.hour, min = m, sec = 0 })
    if cand <= last or cand < now - 3600 then cand = cand + 3600 end
    return cand
  end
  local step = job.every == "week" and 7 * 86400 or 86400
  local t = os.date("*t", now)
  local cand = os.time({ year = t.year, month = t.month, day = t.day, hour = h, min = m, sec = 0 })
  while cand <= last do cand = cand + step end
  if last == 0 and cand > now and cand - step > now - 86400 then return cand end
  return cand
end

local function stampName(t) return os.date("%Y-%m-%d_%H%M%S", t) end

--- Собрать копию в таблицу { [путь] = содержимое }.
local function collect(job)
  local files, size = {}, 0
  for _, d in ipairs(job.dirs) do
    local root = "/" .. fs.combine(d, "")
    for _, rel in ipairs(listFiles(root)) do
      local full = rel == "" and root or fs.combine(root, rel)
      local data = readBin(full)
      if data then
        files["/" .. fs.combine(full, "")] = data
        size = size + #data
      end
    end
  end
  return files, size
end

local function pack(files, password)
  local text = textutils.serialize(files)
  if password and password ~= "" and data and data.encrypt then
    return "SWB1E" .. data.encrypt(text, password)
  end
  return "SWB1P" .. text
end

local function unpack_(blob, password)
  local kind = blob:sub(1, 5)
  local body = blob:sub(6)
  if kind == "SWB1E" then
    if not password or password == "" then return nil, "Копия под паролем" end
    local ok, text = pcall(data.decrypt, body, password)
    if not ok or not text then return nil, "Неверный пароль копии" end
    body = text
  elseif kind ~= "SWB1P" then
    return nil, "Это не копия SWired"
  end
  local fn = load("return " .. body, "backup", "t", {})
  local okL, t = pcall(fn or function() end)
  if not okL or type(t) ~= "table" then return nil, "Копия повреждена" end
  return t
end

--- Куда кладутся копии этого задания на диске: <target>/swired/<сервер>/<id>/<время>.swb
local function localDir(job, target)
  local me = (net and net.hostname and net.hostname() ~= "" and net.hostname()) or "server"
  return fs.combine(target or job.to.target or BACKUP_DIR, "swired/" .. me .. "/" .. job.id)
end

local function trim(dir, keep)
  if not fs.isDir(dir) then return end
  local list = fs.list(dir)
  table.sort(list)
  for i = 1, #list - keep do pcall(fs.delete, fs.combine(dir, list[i])) end
end

local function finish(job, ok, err, size, t0)
  local cfg = S.config()
  for _, b in ipairs(cfg.backups) do
    if b.id == job.id then
      b.last = { time = t0, ok = ok, err = err, size = size, took = os.time() - t0 }
      b.history = b.history or {}
      table.insert(b.history, 1, b.last)
      while #b.history > 12 do table.remove(b.history) end
    end
  end
  S.saveConfig(cfg)
  if ok then S.log("Копии", "ok", "копия «" .. job.name .. "»: " .. textutils.formatSize(size) .. " → " .. S.whereText(job))
  else S.log("Копии", "err", "копия «" .. job.name .. "» не сделана: " .. tostring(err)) end
end

function S.whereText(job)
  if job.to and job.to.kind == "server" then return "сервер " .. tostring(job.to.target) end
  return tostring(job.to and job.to.target or BACKUP_DIR)
end

--- Сделать копию сейчас. Для копии на другой сервер нужен его логин администратора (job.to.user, job.to.password).
function S.runBackup(id)
  local job = findJob(id)
  if not job then return nil, "Нет такой копии" end
  local t0 = os.time()
  local files, size = collect(job)
  local blob = pack(files, job.password)
  local name = stampName(t0) .. ".swb"
  if job.to and job.to.kind == "server" then
    local r = S.remote(job.to.target, job.to.user, job.to.pass)
    local ok, err = r.storeBackup(job.id, job.name, name, blob, job.keep)
    if not ok then finish(job, false, err, 0, t0) return nil, err end
  else
    local target = job.to and job.to.target or BACKUP_DIR
    local removable = target:find("^/?disk") or target:find("^/?usb")
    if not fs.exists(target) then
      if removable then
        finish(job, false, "нет " .. target .. " (дискета или флешка вынута?)", 0, t0)
        return nil, "нет " .. target
      end
      fs.makeDir(target)
    end
    local dir = localDir(job, target)
    local okw, werr = pcall(writeBin, fs.combine(dir, name), blob)
    if not okw or werr == false then
      local e = okw and "не записалось (мало места?)" or tostring(werr)
      finish(job, false, e, 0, t0)
      return nil, e
    end
    trim(dir, job.keep)
  end
  finish(job, true, nil, size, t0)
  return true, size
end

--- Сделанные копии задания: { name, time, size }.
function S.copies(id)
  local job = findJob(id)
  if not job then return {} end
  if job.to and job.to.kind == "server" then
    local r = S.remote(job.to.target, job.to.user, job.to.pass)
    local list = r.storedBackups(job.id)
    return type(list) == "table" and list or {}
  end
  local dir = localDir(job)
  local out = {}
  if fs.isDir(dir) then
    for _, n in ipairs(fs.list(dir)) do out[#out + 1] = { name = n, size = fs.getSize(fs.combine(dir, n)) } end
  end
  table.sort(out, function(a, b) return a.name > b.name end)
  return out
end

--- Вернуть файлы из копии. Текущие файлы сначала откладываются в /restore-old/<время>.
function S.restore(id, copyName, password)
  local job = findJob(id)
  if not job then return nil, "Нет такой копии" end
  local blob, err
  if job.to and job.to.kind == "server" then
    local r = S.remote(job.to.target, job.to.user, job.to.pass)
    blob, err = r.fetchBackup(job.id, copyName)
  else
    blob = readBin(fs.combine(localDir(job), copyName))
    if not blob then err = "Нет файла копии" end
  end
  if not blob then return nil, err end
  local files, uerr = unpack_(blob, password or job.password)
  if not files then return nil, uerr end
  local aside = "/restore-old/" .. stampName(os.time())
  local n = 0
  for path, content in pairs(files) do
    if fs.exists(path) and not fs.isDir(path) then pcall(fs.copy, path, fs.combine(aside, path)) pcall(fs.delete, path) end
    writeBin(path, content)
    n = n + 1
  end
  S.log("Копии", "ok", "восстановлено из «" .. job.name .. "» (" .. copyName .. "): " .. n .. " файлов, старые — в " .. aside)
  return n
end

-- приём копий с других серверов (этот сервер — хранилище)
local INCOMING = "/backups/incoming"

local function inDir(from, jobId)
  return fs.combine(INCOMING, (tostring(from):gsub("[^%w%-_%.]", "_")) .. "/" .. (tostring(jobId):gsub("[^%w]", "")))
end
local function safeName(name) return (tostring(name):gsub("[^%w%-_%.]", "")) end

--- Кусок i из n копии с сервера from; на последнем куске копия готова.
function S.storePart(from, jobId, jobName, name, i, n, piece, keep)
  local dir = inDir(from, jobId)
  local part = fs.combine(dir, safeName(name) .. ".part")
  local old = i > 1 and readBin(part) or ""
  if not writeBin(part, old .. tostring(piece)) then
    return nil, "Не записалось на " .. ((net.hostname and net.hostname()) or "сервер") .. " (мало места?)"
  end
  if i >= n then
    local final = fs.combine(dir, safeName(name))
    if fs.exists(final) then fs.delete(final) end
    fs.move(part, final)
    local list = {}
    for _, f in ipairs(fs.list(dir)) do if not f:find("%.part$") then list[#list + 1] = f end end
    table.sort(list)
    for k = 1, #list - (tonumber(keep) or 7) do pcall(fs.delete, fs.combine(dir, list[k])) end
    S.log("Копии", "ok", "принята копия «" .. tostring(jobName) .. "» с " .. tostring(from) .. ": " ..
      textutils.formatSize(fs.getSize(final)))
  end
  return true
end

function S.storedBackups(from, jobId)
  local dir = inDir(from, jobId)
  local out = {}
  if fs.isDir(dir) then
    for _, n in ipairs(fs.list(dir)) do
      if not n:find("%.part$") then out[#out + 1] = { name = n, size = fs.getSize(fs.combine(dir, n)) } end
    end
  end
  table.sort(out, function(a, b) return a.name > b.name end)
  return out
end

--- Кусок i копии name (и сколько всего кусков).
function S.fetchPart(from, jobId, name, i)
  local d = readBin(fs.combine(inDir(from, jobId), safeName(name)))
  if not d then return nil, "Нет такой копии" end
  local n = math.max(1, math.ceil(#d / S.CHUNK))
  i = tonumber(i) or 1
  return d:sub((i - 1) * S.CHUNK + 1, i * S.CHUNK), n
end

-- ======================= управление по сети =======================

-- что можно вызвать по сети (после входа администратора)
S.REMOTE = {
  status = true, services = true, start = true, stop = true, restart = true, set = true, addCustom = true,
  removeCustom = true, serviceLog = true, journal = true, users = true, addUser = true, removeUser = true,
  setAdmin = true, passwd = true, backups = true, saveBackup = true, removeBackup = true, runBackup = true,
  copies = true, restore = true, reboot = true, shutdown = true, importAuth = true,
}

--- Ждать ответ SWIREDOK с номером id. quiet — не возвращать чужие события в очередь (для Wired).
local function wait(port, id, timeout, quiet)
  local timer = os.startTimer(timeout or 5)
  local skipped, res = {}, nil
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "SWIREDOK" and ev[6] == id then
      res = { ok = ev[7], value = ev[8], extra = ev[9] }
      break
    elseif ev[1] == "timer" and ev[2] == timer then break
    elseif ev[1] == "terminate" then res = { ok = false, value = "Прервано" } break
    elseif not quiet then skipped[#skipped + 1] = ev end
  end
  os.cancelTimer(timer)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  return res
end

local function request(server, cmd, session, args, opts)
  if not net or net.speed() == 0 then return nil, "Нет сети: подключите кабель или Wi-Fi" end
  local port = math.random(30000, 39999)
  net.open(port)
  local id = math.random(1, 1000000000)
  local sent = net.send(server, S.PORT, "SWIRED", id, port, cmd, session, args)
  if not sent then net.close(port) return nil, "Нет сети" end
  local r = wait(port, id, opts and opts.timeout or 6, opts and opts.quiet)
  net.close(port)
  if not r then return nil, "«" .. tostring(server) .. "» не отвечает: выключен, нет SWired или нет сети" end
  if not r.ok then return nil, tostring(r.value) end
  if r.value == nil then return true end
  return r.value, r.extra
end

--- Серверы с SWired в сети: { address, name, version, level, problems, uptime }.
function S.discover(seconds, quiet)
  local out = {}
  if not net or net.speed() == 0 then return out end
  local port = math.random(30000, 39999)
  net.open(port)
  local id = math.random(1, 1000000000)
  net.broadcast(S.PORT, "SWIRED", id, port, "hello")
  local timer = os.startTimer(seconds or 1.5)
  local skipped = {}
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "SWIREDOK" and ev[6] == id and type(ev[8]) == "table" then
      local v = ev[8]
      v.address = ev[2]
      out[#out + 1] = v
    elseif ev[1] == "timer" and ev[2] == timer then break
    elseif ev[1] == "terminate" then break
    elseif not quiet then skipped[#skipped + 1] = ev end
  end
  net.close(port)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  table.sort(out, function(a, b) return tostring(a.name) < tostring(b.name) end)
  return out
end

--- Тот же набор функций на другом сервере. Вход — при первом вызове (администратор того сервера).
function S.remote(server, user, password, opts)
  local R = { server = server, remote = true }
  local session
  local function login()
    local v, err = request(server, "login", nil, { user = user, password = password }, opts)
    if not v then return nil, err end
    session = v
    return true
  end
  R.login = login
  local function call(name, ...)
    if not session then
      local ok, err = login()
      if not ok then return nil, err end
    end
    local args = table.pack(...)
    local v, extra = request(server, name, session, { n = args.n, table.unpack(args, 1, args.n) }, opts)
    if not v and tostring(extra or ""):find("^Сеанс") then
      session = nil
      local ok, err = login()
      if not ok then return nil, err end
      v, extra = request(server, name, session, { n = args.n, table.unpack(args, 1, args.n) }, opts)
    end
    return v, extra
  end
  for name in pairs(S.REMOTE) do R[name] = function(...) return call(name, ...) end end
  -- копии на этом сервере-хранилище: по кускам (одно сообщение — не больше 8 КБ); «откуда» сервер подставляет сам
  R.storeBackup = function(jobId, jobName, name, blob, keep)
    local n = math.max(1, math.ceil(#blob / S.CHUNK))
    for i = 1, n do
      local ok, err = call("storePart", jobId, jobName, name, i, n, blob:sub((i - 1) * S.CHUNK + 1, i * S.CHUNK), keep)
      if not ok then return nil, err end
    end
    return true
  end
  R.storedBackups = function(jobId) return call("storedBackups", jobId) end
  R.fetchBackup = function(jobId, name)
    local parts, i, n = {}, 1, 1
    while i <= n do
      local piece, total = call("fetchPart", jobId, name, i)
      if not piece then return nil, total end
      parts[i] = piece
      n = tonumber(total) or 1
      i = i + 1
    end
    return table.concat(parts)
  end
  R.hello = function() return request(server, "hello", nil, {}, opts) end
  return R
end

--- Этот сервер — те же имена, что у S.remote (чтобы окна SWired работали одинаково здесь и по сети).
function S.here()
  local R = { remote = false }
  for name in pairs(S.REMOTE) do if S[name] then R[name] = S[name] end end
  R.reboot = function() os.reboot() end
  R.shutdown = function() os.shutdown() end
  R.journal = function(n, filter, find) return S.journal(n, filter, find) end
  return R
end

return S
