-- Установка Wired 1.1 с дискеты: администратор, файлы системы в /wired, запуск при включении.
local src = ...
if not src then
  for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
    if fs.exists(d .. "/system/boot.lua") then src = d break end
  end
end
if not src then printError("Не нашёл файлы Wired на дисках") return end

local BG, PANEL, FG, DIM, ACC, ERR, OK = 0x0d0b14, 0x16131f, 0xece8f4, 0x8d87a3, 0xff4f5e, 0xff4f5e, 0x5fd38a
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
  term.write("◆ WIRED 1.1")
  rgb(DIM, BG)
  term.setCursorPos(bx, 4)
  term.write("Графическая система MineComputers · установка")
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
local function ask(label, mask)
  rgb(DIM, BG)
  term.setCursorPos(bx, row)
  term.write(label)
  rgb(FG, PANEL)
  term.setCursorPos(bx + 22, row)
  term.write(string.rep(" ", math.min(30, W - bx - 22)))
  term.setCursorPos(bx + 23, row)
  local v = read(mask)
  row = row + 1
  return v
end
local function wait(text)
  line("")
  line(text, DIM)
  repeat local _, k = os.pullEvent("key") until k == "enter" or k == "escape"
end

-- 1. приветствие
screen("Добро пожаловать")
line("Wired — рабочий стол с окнами, «Пуск», Проводник и программы")
line("с дискет. Командная строка остаётся: Терминал в системе.")
line("")
local update = fs.exists("/wired/boot.lua")
if update then line("Wired уже установлена — файлы системы будут обновлены.", 0xffc857) end
line("")
line("Enter — продолжить, Esc — отмена", DIM)
local _, k
repeat _, k = os.pullEvent("key") until k == "enter" or k == "escape"
if k == "escape" then rgb(FG, 0) term.clear() term.setCursorPos(1, 1) print("Установка отменена") return end

-- 2. администратор
if auth then
  if auth.open() then
    screen("Администратор компьютера")
    line("Придумайте имя и пароль. При включении Wired спросит их.")
    line("")
    while true do
      local name = ask("Имя")
      local p1 = ask("Пароль", "*")
      local p2 = ask("Ещё раз", "*")
      if name == "" then line("Имя не может быть пустым", ERR)
      elseif p1 ~= p2 then line("Пароли не совпали — ещё раз", ERR)
      else
        local ok, err = auth.add(name, p1, true)
        if ok == false or (ok == nil and err) then line(tostring(err), ERR)
        else
          pcall(auth.login, name, p1)
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
      if ok and auth.isAdmin() then break end
      line(ok and "Это не администратор" or "Неверный пароль", ERR)
      line("")
    end
  end
end

-- 3. файлы
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

-- 4. запуск при включении
local s = fs.open("/startup.lua", "w")
s.write([[
-- Wired: графическая система. Удалите этот файл — компьютер будет загружаться в командную строку.
if fs.exists("/wired/boot.lua") then os.run({}, "/wired/boot.lua") end
]])
s.close()

screen("Готово")
line("Wired установлена: " .. #files .. " файлов в /wired.", OK)
line("Компьютер перезагрузится. Дискету можно вынуть.")
line("")
line("Через 3 секунды — перезагрузка…", DIM)
os.sleep(3)
os.reboot()
os.pullEventRaw()
