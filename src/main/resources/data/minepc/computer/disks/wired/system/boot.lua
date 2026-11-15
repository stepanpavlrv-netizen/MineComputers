-- Wired 1.2 — графическая система MineComputers.
-- Запускается из /startup.lua. Удалить /startup.lua — компьютер снова загрузится в командную строку.
local ROOT = "/wired"
local CFG = ROOT .. "/settings.cfg"

if not term.isColor() then
  print("Wired нужна цветная видеокарта.")
  return
end

_G.wired = { VERSION = "1.2", root = ROOT }

local function loadSettings()
  local s = { scale = "auto", theme = "dark", accent = 0xff4f5e, wallpaper = "wires" }
  if fs.exists(CFG) then
    local f = fs.open(CFG, "r")
    local text = f and f.readAll() or ""
    if f then f.close() end
    local fn = load("return " .. text, "settings", "t", {})
    local ok, t = pcall(fn or function() end)
    if ok and type(t) == "table" then for k, v in pairs(t) do s[k] = v end end
    -- до Wired 1.2 масштаб по умолчанию был 100 %: теперь «авто» (под экран видеокарты)
    if (s.v or 1) < 2 then
      if s.scale == 100 then s.scale = "auto" end
      s.v = 2
    end
  end
  s.v = 2
  return s
end

wired.settings = loadSettings()

function wired.saveSettings()
  local f = fs.open(CFG, "w")
  if f then
    f.write(textutils.serialize(wired.settings))
    f.close()
  end
end

local function lib(name)
  local v = dofile(ROOT .. "/lib/" .. name .. ".lua")
  wired[name] = v
  return v
end

lib("theme").apply(wired.settings)
lib("icons")
lib("gfx")
lib("ui")
lib("wallpaper")
lib("wm")
lib("vterm")
lib("apps")
lib("page")

local gpu = wired.gfx.gpu
local maxW, maxH = gpu.maxResolution()
local wm = wired.wm

--- Масштаб в процентах; «авто» — чтобы рабочая область была около 160 столбцов (на больших видеокартах — крупнее).
function wired.scalePercent()
  local s = wired.settings.scale
  if type(s) == "number" then return s end
  local auto = math.floor(maxW / 160 * 100 / 25 + 0.5) * 25
  return math.max(100, math.min(300, auto))
end

--- Масштаб: при 200 % система рисует вдвое меньше клеток — и всё вдвое крупнее.
function wired.applyScale()
  local s = wired.scalePercent()
  local w = math.max(40, math.min(maxW, math.floor(maxW * 100 / s)))
  local h = math.max(12, math.min(maxH, math.floor(maxH * 100 / s)))
  local cw, ch = wired.gfx.screenSize()
  if cw ~= w or ch ~= h then gpu.setResolution(w, h) end
end

--- Поменяли оформление в Параметрах.
function wired.applyTheme()
  wired.theme.apply(wired.settings)
  wm.layout()
end

--- Текущий пользователь — администратор (по списку пользователей, без sudo).
function wired.isAdmin()
  if not auth or auth.open() then return true end
  local me = auth.user()
  for _, u in ipairs(auth.users() or {}) do
    if u.name == me then return u.admin and true or false end
  end
  return false
end

function wired.quit(kind)
  wired.exit = kind
  wm.running = false
end

function wired.logout()
  if not auth or auth.open() then
    wm.confirm("Выйти", "На компьютере нет пользователей. Выйти в командную строку?", function(ok)
      if ok then wired.quit("console") end
    end, "Выйти")
    return
  end
  for i = #wm.windows, 1, -1 do wm.close(wm.windows[i], true) end
  auth.logout()
  wm.startLogin()
end

-- Вставили дискету или флешку: установочный диск — предложить поставить программу
function wired.onEvent(ev)
  if ev[1] == "disk" and type(ev[2]) == "string" then
    local path = ev[2]
    local m = fs.exists(fs.combine(path, "app/app.lua")) and wired.apps.manifest(fs.combine(path, "app"))
    local kind = path:find("usb") and "флешка" or "дискета"
    if m then
      wm.notify({ title = "▣ Вставлена " .. kind, text = "«" .. m.name .. " " .. m.version .. "» — установочный диск",
        actions = {
          { "Установить", function() wired.apps.launch(wired.apps.get("installer"), fs.combine(path, "app")) end },
          { "Открыть", function() wired.apps.launch(wired.apps.get("files"), path) end },
        } })
    else
      wm.notify({ title = "▣ Вставлена " .. kind, text = path, actions = {
        { "Открыть", function() wired.apps.launch(wired.apps.get("files"), path) end } } })
    end
    wm.refreshDesktop()
  elseif ev[1] == "disk_eject" then
    wm.refreshDesktop()
  end
end

function wired.onLogin()
  pcall(wired.apps.startServices)
end

wired.applyScale()
wm.layout()
if auth and not auth.open() and not auth.user() then wm.startLogin() else wired.onLogin() end

-- часы на панели — раз в 20 секунд
local function clock()
  wm.invalidateBar()
  wm.after(20, clock)
end
wm.after(20, clock)

local ok, err = pcall(function()
  wm.render()
  while wm.running do
    local ev = table.pack(os.pullEventRaw())
    wm.handle(ev)
    if computer.pending() == 0 then wm.render() end
  end
end)

wired.gfx.release()
pcall(gpu.setResolution, math.min(160, maxW), math.min(50, maxH))   -- командная строка — как после включения
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)
if not ok then
  printError("Wired остановилась: " .. tostring(err))
  print("Командная строка. Снова запустить систему: /wired/boot.lua")
end
local exit = wired.exit
_G.wired = nil
if exit == "off" then
  os.shutdown()
  os.pullEventRaw()
elseif exit == "reboot" then
  os.reboot()
  os.pullEventRaw()
end
