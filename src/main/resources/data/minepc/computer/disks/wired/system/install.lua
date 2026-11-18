-- Установка Wired 1.4 / SWired 1.0 с дискеты или флешки: администратор, файлы системы в /wired, запуск при включении.
-- SWired (на флешке есть файл edition со словом server) ещё спрашивает имя сервера, пароль BMC и какие службы включить.
local src = ...
if not src then
  for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
    if fs.exists(d .. "/system/boot.lua") then src = d break end
  end
end
if not src then printError("Не нашёл файлы Wired на дисках") return end

local server = false
if fs.exists(src .. "/edition") then
  local f = fs.open(src .. "/edition", "r")
  server = f and (f.readAll() or ""):find("server") ~= nil
  if f then f.close() end
end
local TITLE = server and "◆ SWIRED 1.0" or "◆ WIRED 1.4"
local SUB = server and "Система для серверов MineComputers · установка" or "Графическая система MineComputers · установка"

local BG, PANEL, FG, DIM, ERR, OK = 0x0d0b14, 0x16131f, 0xece8f4, 0x8d87a3, 0xff4f5e, 0x5fd38a
local ACC = server and 0x4fd1e8 or 0xff4f5e
local W, H = term.getSize()
local function rgb(f, b)
  if term.setTextColorRGB then term.setTextColorRGB(f) else term.setTextColor(colors.white) end
  if term.setBackgroundColorRGB then term.setBackgroundColorRGB(b) else term.setBackgroundColor(colors.black) end
end
local bx, bw = math.max(1, math.floor((W - 60) / 2)), math.min(60, W)
local row
local function screen(title)
  rgb(FG, BG)
  term.clear()
  rgb(FG, ACC)
  term.setCursorPos(1, 1)
  term.write(string.rep(" ", W))
  rgb(ACC, BG)
  term.setCursorPos(bx, 3)
  term.write(TITLE)
  rgb(DIM, BG)
  term.setCursorPos(bx, 4)
  term.write(SUB)
  rgb(FG, BG)
  term.setCursorPos(bx, 6)
  term.write(title)
  row = 8
end
local function line(text, color)
  rgb(color or FG, BG)
  term.setCursorPos(bx, row)
  term.write(text)
  row = row + 1
end
local function ask(label, mask, default)
  rgb(DIM, BG)
  term.setCursorPos(bx, row)
  term.write(label)
  rgb(FG, PANEL)
  term.setCursorPos(bx + 22, row)
  term.write(string.rep(" ", math.min(30, W - bx - 22)))
  term.setCursorPos(bx + 23, row)
  local v = read(mask, nil, nil, default)
  row = row + 1
  return v
end

-- 1. приветствие
screen("Добро пожаловать")
if server then
  line("SWired — система для серверов: службы (сайты, почта, вход по сети),")
  line("состояние железа, журнал, пользователи и резервные копии — в окнах.")
  line("Управлять сервером можно и с другого компьютера: «Серверы» в Wired.")
else
  line("Wired — рабочий стол с окнами, «Пуск», Проводник и программы")
  line("с дискет. Командная строка остаётся: Терминал в системе.")
end
line("")
local update = fs.exists("/wired/boot.lua")
if update then line("Система уже установлена — файлы будут обновлены, настройки останутся.", 0xffc857) end
line("")
line("Enter — продолжить, Esc — отмена", DIM)
local _, k
repeat _, k = os.pullEvent("key") until k == "enter" or k == "escape"
if k == "escape" then rgb(FG, 0) term.clear() term.setCursorPos(1, 1) print("Установка отменена") return end

-- 2. администратор
local adminPw
if auth then
  if auth.open() then
    screen(server and "Администратор сервера" or "Администратор компьютера")
    line("Придумайте имя и пароль. При включении система спросит их.")
    if server then line("С ними же входят в «Серверы» с другого компьютера.", DIM) end
    line("")
    while true do
      local name = ask("Имя", nil, server and "admin" or nil)
      local p1 = ask("Пароль", "*")
      local p2 = ask("Ещё раз", "*")
      if name == "" then line("Имя не может быть пустым", ERR)
      elseif p1 ~= p2 then line("Пароли не совпали — ещё раз", ERR)
      elseif server and #p1 < 6 then line("Для сервера — хотя бы 6 знаков", ERR)
      else
        local ok, err = auth.add(name, p1, true)
        if ok == false or (ok == nil and err) then line(tostring(err), ERR)
        else
          pcall(auth.login, name, p1)
          adminPw = p1
          break
        end
      end
      line("")
    end
  elseif not auth.isAdmin() then
    screen("Нужен администратор")
    line("Ставить систему может только администратор этого компьютера.")
    line("")
    while true do
      local name = ask("Пользователь")
      local pw = ask("Пароль", "*")
      local ok = auth.login(name, pw)
      if ok and auth.isAdmin() then adminPw = pw break end
      line(ok and "Это не администратор" or "Неверный пароль", ERR)
      line("")
    end
  end
end

-- 3. SWired: имя сервера, BMC, службы
local services = { { "hostd", "Хостинг сайтов (сайты и почта клиентов)", true }, { "maild", "Почта этого сервера", true },
  { "sshd", "Вход по сети (connect)", true }, { "httpd", "Свой сайт сервера (/www)", false } }
if server then
  screen("Имя сервера")
  line("Под этим именем сервер виден в сети и в «Серверах».")
  line("")
  local cur = net and net.hostname and net.hostname() or ""
  local nm = ask("Имя сервера", nil, cur ~= "" and cur or "srv-01")
  if nm ~= "" and net and net.hostname then pcall(net.hostname, nm) end
  local info = net and net.bmcInfo and net.bmcInfo()
  if info and info.defaultPassword and adminPw and net.bmcPassword then
    line("")
    line("У BMC сервера пароль по умолчанию (admin) — его знают все.")
    line("Поставить на BMC тот же пароль, что у администратора? Enter — да, Esc — нет", DIM)
    repeat _, k = os.pullEvent("key") until k == "enter" or k == "escape"
    if k == "enter" then pcall(net.bmcPassword, adminPw) end
  end
  screen("Какие службы включить")
  line("Цифра — включить или выключить, Enter — дальше. Потом — в окне «Службы».", DIM)
  line("")
  local top = row
  while true do
    row = top
    for i, s in ipairs(services) do line(i .. ". " .. (s[3] and "☑ " or "☐ ") .. s[2], s[3] and FG or DIM) end
    local ev, key = os.pullEvent()
    if ev == "char" and tonumber(key) and services[tonumber(key)] then
      local s = services[tonumber(key)]
      s[3] = not s[3]
    elseif ev == "key" and key == "enter" then break end
  end
end

-- 4. файлы
screen("Копирую файлы")
local files = {}
local function walk(dir, rel)
  for _, n in ipairs(fs.list(dir)) do
    local p = fs.combine(dir, n)
    if fs.isDir(p) then walk(p, rel .. n .. "/") else files[#files + 1] = rel .. n end
  end
end
walk(src .. "/system", "")
local keep = fs.exists("/wired/settings.cfg")
local saved
if keep then local f = fs.open("/wired/settings.cfg", "r") saved = f.readAll() f.close() end
if fs.exists("/wired") then fs.delete("/wired") end
local pw = math.min(50, W - bx - 8)
for i, f in ipairs(files) do
  local dst = "/wired/" .. f
  local d = fs.getDir(dst)
  if not fs.exists(d) then fs.makeDir(d) end
  fs.copy(fs.combine(src .. "/system", f), dst)
  local n = math.floor(pw * i / #files + 0.5)
  term.setCursorPos(bx, 8)
  rgb(ACC, BG) term.write(string.rep("█", n))
  rgb(0x4d4763, BG) term.write(string.rep("░", pw - n))
  rgb(FG, BG) term.write(string.format(" %3d%%", math.floor(100 * i / #files)))
  term.setCursorPos(bx, 9)
  rgb(DIM, BG) term.write(string.rep(" ", bw))
  term.setCursorPos(bx, 9)
  term.write(f)
end
if saved then local o = fs.open("/wired/settings.cfg", "w") o.write(saved) o.close() end
for _, d in ipairs({ "/home", "/apps" }) do if not fs.exists(d) then fs.makeDir(d) end end
if server then
  local e = fs.open("/wired/edition", "w")
  e.write("server\n")
  e.close()
end

-- 5. запуск при включении (и службы сервера)
local s = fs.open("/startup.lua", "w")
s.write([[
-- Wired / SWired: графическая система. Удалите этот файл — компьютер будет загружаться в командную строку.
if fs.exists("/wired/boot.lua") then os.run({}, "/wired/boot.lua") end
]])
s.close()
if server then
  local okS, S = pcall(require, "swired")
  if okS then
    S.autostartLine(S.SWIREDD .. " serve", true)
    for _, sv in ipairs(services) do
      if sv[3] then pcall(S.set, sv[1], "autostart", true) end
    end
    S.log("Сервер", "ok", "установлена SWired " .. S.VERSION)
  end
end

screen("Готово")
line((server and "SWired" or "Wired") .. " установлена: " .. #files .. " файлов в /wired.", OK)
line("Компьютер перезагрузится. " .. (src:find("usb") and "Флешку" or "Дискету") .. " можно вынуть.")
line("")
line("Через 3 секунды — перезагрузка…", DIM)
os.sleep(3)
os.reboot()
os.pullEventRaw()
