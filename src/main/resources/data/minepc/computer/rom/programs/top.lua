-- top: нагрузка компьютера в реальном времени (как диспетчер задач). q или Ctrl+T — выход.
local color = term.isColor()
local function fg(c) if color then term.setTextColor(c) end end
local function bg(c) if color then term.setBackgroundColor(c) end end

local function rate(n)
  if n < 1024 then return string.format("%d Б/с", n) end
  if n < 1048576 then return string.format("%.1f КБ/с", n / 1024) end
  return string.format("%.1f МБ/с", n / 1048576)
end

local function uptime(s)
  s = math.floor(s)
  return string.format("%d:%02d:%02d", math.floor(s / 3600), math.floor(s / 60) % 60, s % 60)
end

-- Полоска нагрузки: зелёная, жёлтая после 60%, красная после 85%
local function bar(pct, width)
  local n = math.floor(pct / 100 * width + 0.5)
  fg(colors.lightGray) term.write("[")
  fg(pct >= 85 and colors.red or pct >= 60 and colors.yellow or colors.lime)
  term.write(string.rep("|", n))
  fg(colors.gray) term.write(string.rep(" ", width - n))
  fg(colors.lightGray) term.write("]")
end

-- string.format считает байты, а русские буквы — по два байта; выравниваем по символам
local function pad(t, n) t = tostring(t); return t .. string.rep(" ", math.max(0, n - unicode.len(t))) end
local function lpad(t, n) t = tostring(t); return string.rep(" ", math.max(0, n - unicode.len(t))) .. t end

local names = { running = "работает", waiting = "ждёт", starting = "запуск", finished = "завершён" }
local sortBy = "cpu"

local function draw()
  local s = computer.stats()
  local w, h = term.getSize()
  bg(colors.black) term.clear()
  local y = 1
  local function line() y = y + 1; term.setCursorPos(1, y) end
  term.setCursorPos(1, 1)
  bg(colors.blue) fg(colors.white)
  local title = string.format(" top — %s   работает %s", os.getComputerLabel and (os.getComputerLabel() or "компьютер") or "компьютер", uptime(s.uptime))
  term.write(title .. string.rep(" ", math.max(0, w - unicode.len(title))))
  bg(colors.black)
  -- Ядра в две колонки
  local half = math.max(18, math.floor(w / 2) - 1)
  local barW = math.max(6, half - 12)
  local n = #s.cores
  local rows = math.ceil(n / 2)
  for r = 1, rows do
    line()
    for col = 0, 1 do
      local i = r + col * rows
      if s.cores[i] then
        term.setCursorPos(1 + col * (half + 1), y)
        fg(colors.cyan) term.write(string.format("%2d", i))
        bar(s.cores[i], barW)
        fg(colors.white) term.write(string.format("%4d%%", math.floor(s.cores[i] + 0.5)))
      end
    end
  end
  line()
  fg(colors.lightGray) term.write("Пам ")
  local memPct = s.memory.used / math.max(1, s.memory.total) * 100
  bar(memPct, barW - 2)
  fg(colors.white) term.write(" " .. textutils.formatSize(s.memory.used) .. " / " .. textutils.formatSize(s.memory.total))
  line()
  fg(colors.lightGray) term.write("ЦП ") fg(colors.white) term.write(string.format("%d%%", math.floor(s.cpu + 0.5)))
  fg(colors.lightGray) term.write("  ГП ") fg(colors.white) term.write(string.format("%d%%", math.floor(s.gpu + 0.5)))
  fg(colors.lightGray) term.write("  t ЦП ")
  fg(s.cpuTemp >= 80 and colors.red or s.cpuTemp >= 65 and colors.yellow or colors.white) term.write(math.floor(s.cpuTemp) .. "°C")
  if s.gpuTemp then fg(colors.lightGray) term.write("  t ГП ") fg(colors.white) term.write(math.floor(s.gpuTemp) .. "°C") end
  fg(colors.lightGray) term.write("  вент. ") fg(colors.white) term.write(s.fan > 0 and (s.fan .. " об/мин") or "нет")
  line()
  fg(colors.lightGray) term.write("Питание ") fg(colors.white) term.write(s.power .. " / " .. s.psu .. " Вт")
  fg(colors.lightGray) term.write("  Сеть ") fg(colors.white) term.write("↑" .. rate(s.net.up) .. " ↓" .. rate(s.net.down))
  for _, d in ipairs(s.disks) do
    line()
    fg(colors.lightGray) term.write("Диск " .. pad(d.path, 7))
    fg(colors.white) term.write(string.format(" %3d%%  чтение %s  запись %s", math.floor(d.active + 0.5), rate(d.read), rate(d.write)))
    if y >= h - 4 then break end
  end
  -- Процессы
  line()
  line()
  bg(colors.gray) fg(colors.white)
  local head = pad("PID", 5) .. pad("Состояние", 10) .. lpad("ЦП", 6) .. lpad("Память", 10) .. "  Программа"
  term.write(head .. string.rep(" ", math.max(0, w - unicode.len(head))))
  bg(colors.black)
  local list = {}
  for _, p in ipairs(s.processes) do list[#list + 1] = p end
  table.sort(list, function(a, b)
    if sortBy == "mem" then return a.memory > b.memory end
    if sortBy == "pid" then return a.pid < b.pid end
    return a.cpu > b.cpu
  end)
  for _, p in ipairs(list) do
    if y >= h - 1 then break end
    line()
    fg(p.cpu >= 50 and colors.yellow or colors.white)
    local txt = pad(p.pid, 5) .. pad(names[p.status] or p.status, 10) .. lpad(string.format("%.1f%%", p.cpu), 6) ..
      lpad(textutils.formatSize(p.memory), 10) .. "  " .. p.path
    term.write(unicode.sub(txt, 1, w))
  end
  term.setCursorPos(1, h)
  fg(colors.lightGray)
  term.write(unicode.sub("q — выход  c/m/p — сортировка по ЦП/памяти/PID  k — снять процесс", 1, w))
  fg(colors.white)
end

local timer = os.startTimer(0)
while true do
  local ev = { os.pullEventRaw() }
  if ev[1] == "terminate" then break
  elseif ev[1] == "timer" and ev[2] == timer then
    draw()
    timer = os.startTimer(1)
  elseif ev[1] == "char" then
    local ch = ev[2]
    if ch == "q" or ch == "й" then break
    elseif ch == "c" or ch == "с" then sortBy = "cpu"; draw()
    elseif ch == "m" or ch == "ь" then sortBy = "mem"; draw()
    elseif ch == "p" or ch == "з" then sortBy = "pid"; draw()
    elseif ch == "k" or ch == "л" then
      local _, h = term.getSize()
      term.setCursorPos(1, h) term.clearLine()
      fg(colors.yellow) term.write("Номер процесса: ") fg(colors.white)
      local pid = tonumber(read())
      if pid then
        if not computer.kill(pid) then
          term.setCursorPos(1, h) term.clearLine() fg(colors.red) term.write("Этот процесс снять нельзя") sleep(1)
        end
      end
      draw()
      timer = os.startTimer(1) -- read() съел наши таймеры
    end
  end
end
bg(colors.black) fg(colors.white)
term.clear()
term.setCursorPos(1, 1)
