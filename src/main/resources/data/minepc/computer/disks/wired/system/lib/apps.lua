-- Wired: программы — встроенные и установленные в /apps (каждая — папка с описанием app.lua).
local T = wired.theme
local I = wired.icons
local wm = wired.wm
local A = {}

local SYSTEM = {
  files = { id = "files", name = "Проводник", icon = "files", file = "files" },
  terminal = { id = "terminal", name = "Терминал", icon = "term", file = "terminal" },
  taskmgr = { id = "taskmgr", name = "Диспетчер задач", icon = "task", file = "taskmgr" },
  settings = { id = "settings", name = "Параметры", icon = "gear", file = "settings" },
  installer = { id = "installer", name = "Установка программы", icon = "disk", file = "installer" },
  chat = { id = "chat", name = "Чат", icon = "chat", file = "chat" },
  network = { id = "network", name = "Сеть", icon = "net", file = "network" },
  pult = { id = "pult", name = "Пульт", icon = "pult", file = "pult" },
  paint = { id = "paint", name = "Рисовалка", icon = "paint", file = "paint", opens = { "pix" } },
  music = { id = "music", name = "Музыка", icon = "music", file = "music", opens = { "mus" } },
  games = { id = "games", name = "Игры", icon = "games", file = "games" },
  archive = { id = "archive", name = "Архив", icon = "archive", file = "archive" },
  calc = { id = "calc", name = "Калькулятор", icon = "calc", file = "calc" },
  clock = { id = "clock", name = "Часы", icon = "clock", file = "clock" },
  hosting = { id = "hosting", name = "Хостинг", icon = "host", file = "hosting" },
  servers = { id = "servers", name = "Серверы", icon = "servers", file = "servers" },
  -- SWired: программы сервера
  srv_server = { id = "srv_server", name = "Сервер", icon = "srv", file = "srv_server" },
  srv_services = { id = "srv_services", name = "Службы", icon = "svc", file = "srv_services" },
  srv_health = { id = "srv_health", name = "Состояние", icon = "health", file = "srv_health" },
  srv_journal = { id = "srv_journal", name = "Журнал", icon = "journal", file = "srv_journal" },
  srv_users = { id = "srv_users", name = "Пользователи", icon = "users", file = "srv_users" },
  srv_backup = { id = "srv_backup", name = "Копии", icon = "backup", file = "srv_backup" },
}
-- Встроенные программы в «Пуске» (по порядку)
local BUILTIN = { "chat", "network", "pult", "paint", "music", "games", "archive", "calc", "clock", "hosting", "servers" }
-- SWired: свой набор — для сервера (без игр, музыки и рисовалки)
local SERVER = { "srv_server", "srv_services", "srv_health", "srv_journal", "srv_users", "srv_backup" }
local SERVER_MORE = { "hosting", "network", "servers", "archive", "chat", "calc", "clock" }
if wired.server then BUILTIN = SERVER_MORE end

--- Описание программы из папки dir (там app.lua: return { name = ..., main = ..., ... }).
function A.manifest(dir)
  local p = fs.combine(dir, "app.lua")
  if not fs.exists(p) then return nil, "нет app.lua" end
  local fn, err = loadfile(p, setmetatable({}, { __index = _G }))
  if not fn then return nil, err end
  local ok, m = pcall(fn)
  if not ok or type(m) ~= "table" then return nil, ok and "app.lua должен вернуть таблицу" or m end
  if type(m.name) ~= "string" or type(m.main) ~= "string" then return nil, "в app.lua нет name или main" end
  m.dir = "/" .. fs.combine(dir, "")
  local id = tostring(m.id or m.name):gsub("[/\\%s%.:]+", "_")
  m.id = id ~= "" and id or "app"
  m.version = tostring(m.version or "1.0")
  return m
end

--- Значок программы p (или всей папки m).
local function entry(m, p)
  p = p or m
  local icon = I.MINI[p.icon or ""] and p.icon or "app"
  local art = p.pix and I.load(fs.combine(m.dir, p.pix)) or (p == m and I.load(fs.combine(m.dir, "icon.pix"))) or nil
  return { id = p == m and m.id or (m.id .. "." .. tostring(p.id or p.name)), name = p.name, icon = icon, art = art,
           installed = true, m = m, main = p.main, type = p.type or m.type, w = p.w or m.w, h = p.h or m.h }
end

function A.installed()
  local out = {}
  if fs.isDir("/apps") then
    for _, name in ipairs(fs.list("/apps")) do
      local m = A.manifest("/apps/" .. name)
      if m then
        if type(m.programs) == "table" and #m.programs > 0 then
          -- на одной дискете несколько программ (например, Офис: Документы и Таблицы)
          for _, p in ipairs(m.programs) do out[#out + 1] = entry(m, p) end
        else
          out[#out + 1] = entry(m)
        end
      end
    end
  end
  table.sort(out, function(a, b) return a.name < b.name end)
  return out
end

function A.get(id)
  if SYSTEM[id] then return SYSTEM[id] end
  for _, e in ipairs(A.installed()) do if e.id == id then return e end end
end

--- Для «Пуска»: всё по порядку.
function A.list()
  if wired.server then
    local out = {}
    for _, id in ipairs(SERVER) do out[#out + 1] = SYSTEM[id] end
    out[#out + 1] = SYSTEM.files
    out[#out + 1] = SYSTEM.terminal
    for _, e in ipairs(A.installed()) do out[#out + 1] = e end
    for _, id in ipairs(BUILTIN) do out[#out + 1] = SYSTEM[id] end
    out[#out + 1] = SYSTEM.taskmgr
    out[#out + 1] = SYSTEM.settings
    return out
  end
  local out = { SYSTEM.files, SYSTEM.terminal }
  for _, e in ipairs(A.installed()) do out[#out + 1] = e end
  for _, id in ipairs(BUILTIN) do out[#out + 1] = SYSTEM[id] end
  out[#out + 1] = SYSTEM.taskmgr
  out[#out + 1] = SYSTEM.settings
  return out
end

--- Значки рабочего стола.
function A.desktop()
  if wired.server then
    local out = {}
    for _, id in ipairs(SERVER) do out[#out + 1] = SYSTEM[id] end
    out[#out + 1] = SYSTEM.hosting
    out[#out + 1] = SYSTEM.terminal
    out[#out + 1] = SYSTEM.files
    for _, e in ipairs(A.installed()) do out[#out + 1] = e end
    out[#out + 1] = SYSTEM.settings
    return out
  end
  local out = { SYSTEM.files, SYSTEM.terminal }
  for _, e in ipairs(A.installed()) do out[#out + 1] = e end
  out[#out + 1] = SYSTEM.settings
  return out
end

function A.places()
  local out = {
    { name = "Домой", path = "/home", mark = "⌂", color = T.fg },
    { name = "Диск", path = "/", mark = "▤", color = T.fg },
  }
  if fs.isDir("/disk") then out[#out + 1] = { name = "Дискета", path = "/disk", mark = "▣", color = 0xff4f5e } end
  if fs.isDir("/usb") then out[#out + 1] = { name = "Флешка", path = "/usb", mark = "▭", color = T.fg } end
  out[#out + 1] = { name = "Программы", path = "/apps", mark = "◆", color = 0xc77dff }
  return out
end

--- Запустить программу (arg — например, папка для Проводника).
function A.launch(e, arg)
  if not e then return end
  local ok, err = pcall(function()
    if e.file then
      local make = dofile("/wired/apps/" .. e.file .. ".lua")
      wm.open(make(arg))
      return
    end
    local m = e.m
    local main = fs.combine(m.dir, e.main or m.main)
    if e.type == "terminal" then
      wm.open(wired.vterm.app(main, arg and { arg } or {}, { title = e.name, icon = e.icon, w = e.w, h = e.h }))
      return
    end
    local env = setmetatable({ APP_DIR = m.dir, wired = wired }, { __index = _G })
    local fn, lerr = loadfile(main, env)
    if not fn then error(lerr, 0) end
    local r = fn(arg)
    if type(r) == "function" then r = r(arg) end
    if type(r) ~= "table" then error("программа не вернула окно", 0) end
    r.title = r.title or e.name
    r.icon = r.icon or e.icon
    wm.open(r)
  end)
  if not ok then wm.notify({ title = "Не запустилось: " .. tostring(e.name), text = tostring(err) }) end
end

--- Службы программ (service в app.lua): работают в фоне всё время, пока запущена система.
function A.startServices()
  A.running = A.running or {}
  -- службы встроенных программ: будильники, приём сообщений чата
  for _, id in ipairs({ "clock", "chat" }) do
    local key = "@" .. id
    if not A.running[key] then
      local ok, mod = pcall(dofile, "/wired/apps/" .. id .. ".lua")
      if ok and type(mod) == "table" and mod.service then
        A.running[key] = true
        wm.spawn(mod.service, function() A.running[key] = nil end)
      end
    end
  end
  for _, e in ipairs(A.installed()) do
    local m = e.m
    if m.service and not A.running then A.running = {} end
    if m.service and not A.running[m.id] then
      A.running[m.id] = true
      local path = fs.combine(m.dir, m.service)
      local env = setmetatable({ APP_DIR = m.dir, wired = wired }, { __index = _G })
      local fn, err = loadfile(path, env)
      if fn then wm.spawn(fn, function(ok, e2) A.running[m.id] = nil end)
      else wm.notify({ title = "Служба «" .. m.name .. "» не запустилась", text = tostring(err) }) end
    end
  end
end

--- Фоновая программа при каждом включении компьютера: строка «путь аргументы» в /etc/services (её читает BIOS).
function A.autostart(line, on)
  local lines, have = {}, false
  local f = fs.exists("/etc/services") and fs.open("/etc/services", "r")
  if f then
    for l in (f.readAll() or ""):gmatch("[^\r\n]+") do
      if l == line then have = true else lines[#lines + 1] = l end
    end
    f.close()
  end
  if on then lines[#lines + 1] = line elseif not have then return true end
  pcall(fs.makeDir, "/etc")
  local w = fs.open("/etc/services", "w")
  if not w then return false end
  w.write(table.concat(lines, "\n") .. (#lines > 0 and "\n" or ""))
  w.close()
  return true
end

--- Найти программу по полю (например, по расширению файла, которое она открывает).
function A.forExt(ext)
  for _, e in ipairs(A.installed()) do
    local p = e.m.programs and nil
    local exts = e.m.opens
    if e.m.programs then
      for _, pp in ipairs(e.m.programs) do
        if pp.name == e.name and pp.opens then exts = pp.opens end
      end
    end
    if type(exts) == "table" then
      for _, x in ipairs(exts) do if x == ext then return e end end
    end
  end
  for _, id in ipairs(BUILTIN) do
    for _, x in ipairs(SYSTEM[id].opens or {}) do if x == ext then return SYSTEM[id] end end
  end
end

-- ======================= установка =======================

--- Все файлы папки (пути относительно неё).
function A.files(dir, prefix, out)
  out = out or {}
  prefix = prefix or ""
  for _, name in ipairs(fs.list(dir)) do
    local p = fs.combine(dir, name)
    if fs.isDir(p) then A.files(p, prefix .. name .. "/", out) else out[#out + 1] = prefix .. name end
  end
  return out
end

local function copyFile(src, dst)
  local d = fs.getDir(dst)
  if d ~= "" and not fs.exists(d) then fs.makeDir(d) end
  if fs.exists(dst) then fs.delete(dst) end
  fs.copy(src, dst)
end

--- Скопировать папку по несколько файлов за раз. step(готово, всего, файл), done(ok, ошибка).
function A.copyTree(src, dst, step, done)
  local ok, list = pcall(A.files, src)
  if not ok then return done(false, list) end
  local i = 0
  local function tick()
    for _ = 1, 4 do
      i = i + 1
      if i > #list then return done(true) end
      local f = list[i]
      local okc, err = pcall(copyFile, fs.combine(src, f), fs.combine(dst, f))
      if not okc then return done(false, err) end
      if step then step(i, #list, f) end
    end
    wm.after(0.05, tick)
  end
  if not fs.exists(dst) then fs.makeDir(dst) end
  tick()
end

--- Установить программу из папки src (обычно /disk/app).
function A.install(src, step, done)
  local m, err = A.manifest(src)
  if not m then return done(false, err) end
  local dst = "/apps/" .. m.id
  if fs.exists(dst) then fs.delete(dst) end
  A.copyTree(src, dst, step, function(ok, e)
    if ok then
      wm.refreshDesktop()
      pcall(A.startServices)
    end
    done(ok, e, m)
  end)
end

function A.uninstall(id)
  local dst = "/apps/" .. id
  if fs.exists(dst) then fs.delete(dst) end
  wm.refreshDesktop()
end

--- Записать установленную программу на дискету или флешку (в папку app).
function A.writeTo(id, drive, step, done)
  local dst = fs.combine(drive, "app")
  if fs.exists(dst) then fs.delete(dst) end
  A.copyTree("/apps/" .. id, dst, step, done)
end

local WRAPPER = [[
-- Установка Wired / SWired: сам установщик — system/install.lua.
local src = ...
if not src then
  for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
    if fs.exists(d .. "/system/boot.lua") then src = d break end
  end
end
if not src then printError("Не нашёл файлы Wired на дисках") return end
os.run({}, src .. "/system/install.lua", src)
]]
local STARTUP = [[
-- Установочный диск Wired / SWired: при загрузке с него запускается установка.
for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
  if fs.exists(d .. "/install.lua") and fs.exists(d .. "/system/boot.lua") then
    os.run({}, d .. "/install.lua", d)
    return
  end
end
]]

--- Записать установочный диск системы (server — SWired) на дискету или флешку drive. Всё на диске стирается.
function A.makeInstaller(drive, server, step, done)
  if not fs.exists("/wired/install.lua") then
    return done(false, "В этой системе нет установщика — сначала обновите Wired с дискеты")
  end
  local need = A.size("/wired")
  local free = fs.getFreeSpace and fs.getFreeSpace(drive)
  for _, n in ipairs(fs.list(drive)) do
    local p = fs.combine(drive, n)
    if fs.isDir(p) then free = free and free + A.size(p) else free = free and free + (fs.getSize(p) or 0) end
  end
  if free and free < need + 2048 then
    return done(false, "Мало места: нужно " .. textutils.formatSize(need) .. ", на диске " .. textutils.formatSize(free))
  end
  for _, n in ipairs(fs.list(drive)) do pcall(fs.delete, fs.combine(drive, n)) end
  local list = A.files("/wired")
  local i = 0
  local function write(path, text)
    local f = fs.open(fs.combine(drive, path), "w")
    if f then f.write(text) f.close() end
  end
  local function tick()
    for _ = 1, 4 do
      i = i + 1
      if i > #list then
        write("install.lua", WRAPPER)
        write("startup.lua", STARTUP)
        write("readme.txt", (server and "SWired 1.0 — система для серверов MineComputers" or "Wired " .. wired.VERSION .. " — графическая система MineComputers") ..
          "\n\nВставьте в компьютер (сервер — в его USB) и включите: установка начнётся сама.\n")
        if server then write("edition", "server\n") end
        if fs.setLabel then pcall(fs.setLabel, drive, server and "SWired 1.0" or ("Wired " .. wired.VERSION)) end
        return done(true, nil, #list)
      end
      local f = list[i]
      if f ~= "settings.cfg" and f ~= "edition" then
        local okc, err = pcall(copyFile, fs.combine("/wired", f), fs.combine(drive, "system/" .. f))
        if not okc then return done(false, err) end
      end
      if step then step(i, #list) end
    end
    wm.after(0.05, tick)
  end
  tick()
end

function A.size(dir)
  local total = 0
  local ok, list = pcall(A.files, dir)
  if not ok then return 0 end
  for _, f in ipairs(list) do total = total + (fs.getSize(fs.combine(dir, f)) or 0) end
  return total
end

return A
