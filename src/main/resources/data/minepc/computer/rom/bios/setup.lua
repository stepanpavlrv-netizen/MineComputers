-- BIOS: самотест при включении (POST), экраны настройки и выбор устройства загрузки.
-- Две темы: классическая синяя (платы ур. 1–3, управление клавиатурой) и графический UEFI (ур. 4–5, есть мышь).
local M = {}

local ulen, usub = unicode.len, unicode.sub
local W, H

local DEVICE_NAMES = { nvme = "M.2 NVMe SSD", sata = "SATA SSD", hdd = "Жёсткий диск", usb = "USB-флешка", floppy = "Дискета" }
local POWER_LOSS = { [0] = "Как было", [1] = "Включаться", [2] = "Не включаться" }

-- ===================== Рисование =====================
local color = term.isColor()
local rgb = color and term.getDepth and term.getDepth() >= 8 and term.setTextColorRGB ~= nil

local function fg(c) if c then if type(c) == "number" and c > 15 then if rgb then term.setTextColorRGB(c) end else term.setTextColor(c) end end end
local function bg(c) if c then if type(c) == "number" and c > 15 then if rgb then term.setBackgroundColorRGB(c) end else term.setBackgroundColor(c) end end end

local function at(x, y, text, f, b)
  if y < 1 or y > H or x > W then return end
  if x < 1 then text = usub(text, 2 - x); x = 1 end
  local room = W - x + 1
  if ulen(text) > room then text = usub(text, 1, room) end
  fg(f); bg(b)
  term.setCursorPos(x, y)
  term.write(text)
end

local function fill(x, y, w, h, b, ch)
  bg(b)
  local line = string.rep(ch or " ", math.max(0, w))
  for yy = y, y + h - 1 do
    if yy >= 1 and yy <= H then term.setCursorPos(x, yy); term.write(line) end
  end
end

local function pad(s, n)
  s = tostring(s)
  local l = ulen(s)
  if l >= n then return usub(s, 1, n) end
  return s .. string.rep(" ", n - l)
end

local function center(y, text, f, b)
  at(math.floor((W - ulen(text)) / 2) + 1, y, text, f, b)
end

-- ===================== Сведения =====================
local function gatherInfo()
  local i = bios.info()
  local c = computer.info()
  i.cpu = c.cpu
  i.cores = c.cores
  i.clock = c.clock
  i.ram = c.ram
  i.gpu = c.gpu ~= "" and c.gpu or "нет (встроенный текстовый вывод)"
  return i
end

local function devices()
  local list, byId = {}, {}
  for _, d in ipairs(bios.devices()) do byId[d.id] = d end
  return byId
end

-- ===================== Настройки (общая модель для обеих тем) =====================
-- Пункт: {kind = "info"|"toggle"|"choice"|"action"|"order", label, key, help, ...}
local function buildPages(info, cfg, dev, state)
  local coreChoices = { "Все (" .. info.cores .. ")" }
  for n = 1, info.cores do coreChoices[#coreChoices + 1] = tostring(n) end
  local pages = {
    { id = "main", title = "Main", ru = "Система", items = {
      { kind = "info", label = "BIOS", value = info.vendor .. " " .. info.version },
      { kind = "info", label = "Материнская плата", value = info.board },
      { kind = "info", label = "Серийный номер", value = info.serial or "" },
      { kind = "info", label = "Процессор", value = info.cpu },
      { kind = "info", label = "Память", value = string.format("%d КБ", info.ram) },
      { kind = "info", label = "Видеокарта", value = info.gpu },
      { kind = "info", label = "День / время", value = "день " .. tostring(info.day or "?") .. ", " .. tostring(info.time or "") },
      { kind = "info", label = "Имя компьютера", value = os.getComputerLabel and os.getComputerLabel() or "" },
    } },
    { id = "advanced", title = "Advanced", ru = "Устройства", items = {
      { kind = "choice", label = "Ядра процессора", key = "cores", options = coreChoices, base = 0,
        help = "Сколько ядер включено. Меньше ядер — меньше программ работает одновременно." },
      { kind = "toggle", label = "Встроенная сеть (LAN)", key = "lan", help = "Сетевой порт на плате. Выключен — кабель не работает." },
      { kind = "toggle", label = "USB-порты", key = "usb", help = "Флешки и USB Wi-Fi адаптеры." },
      { kind = "toggle", label = "Wi-Fi адаптер", key = "wifi", help = "USB Wi-Fi адаптер (нужны включённые USB-порты)." },
      { kind = "toggle", label = "Быстрая загрузка", key = "fastBoot", help = "Без проверки памяти и паузы на экране самотеста." },
    } },
    { id = "boot", title = "Boot", ru = "Загрузка", items = {
      { kind = "order", label = "Порядок загрузки", key = "bootOrder",
        help = "Сверху вниз. Из первого устройства с файлом startup.lua запускается система. Первый внутренний диск — системный (/). +/− или Enter — сдвинуть." },
    } },
    { id = "security", title = "Security", ru = "Защита", items = {
      { kind = "action", label = "Пароль на вход в BIOS", key = "biosPassword", action = "password",
        help = "Спрашивается при входе в настройки. Пустой — снять пароль." },
      { kind = "action", label = "Пароль при включении", key = "bootPassword", action = "password",
        help = "Спрашивается при каждом включении. Пустой — снять пароль." },
    } },
    { id = "power", title = "Power", ru = "Питание", items = {
      { kind = "choice", label = "После пропажи питания", key = "powerLoss", options = { "Как было", "Включаться", "Не включаться" }, base = 0,
        help = "Что делать, когда мир загрузился (сервер перезапустили): включиться, остаться выключенным или как было." },
      { kind = "toggle", label = "Включение красным камнем", key = "redstone", help = "Компьютер включается сигналом красного камня рядом с корпусом." },
    } },
    { id = "monitor", title = "Monitor", ru = "Мониторинг", items = {
      { kind = "monitor" },
    } },
    { id = "exit", title = "Exit", ru = "Выход", items = {
      { kind = "action", label = "Сохранить и перезагрузить", action = "save", help = "F10. Новые ядра и порядок дисков — после перезагрузки." },
      { kind = "action", label = "Выйти без сохранения", action = "discard", help = "Изменения пропадут." },
      { kind = "action", label = "Настройки по умолчанию", action = "defaults", help = "Все настройки как с завода (пароли не трогаются)." },
    } },
  }
  return pages
end

local function valueText(item, cfg, dev)
  if item.kind == "toggle" then return cfg[item.key] and "Включено" or "Выключено"
  elseif item.kind == "choice" then return item.options[(cfg[item.key] or 0) - item.base + 1] or "?"
  elseif item.kind == "action" and item.action == "password" then return cfg[item.key] and "Установлен" or "Нет"
  elseif item.kind == "info" then return item.value
  end
  return ""
end

-- изменить значение пункта (dir = 1 / -1)
local function change(item, cfg, dir)
  if item.kind == "toggle" then cfg[item.key] = not cfg[item.key]; return true
  elseif item.kind == "choice" then
    local n = #item.options
    local v = ((cfg[item.key] or 0) - item.base + dir) % n
    cfg[item.key] = v + item.base
    return true
  end
end

local function readings()
  local s = computer.stats()
  local load = s.cpu / 100
  local psuLoad = s.psu > 0 and s.power / s.psu or 0
  local r = {
    { "Температура процессора", string.format("%.0f °C", s.cpuTemp), s.cpuTemp >= 85 and "hot" or s.cpuTemp >= 70 and "warm" or "ok", s.cpuTemp / 100 },
  }
  if s.gpuTemp then r[#r + 1] = { "Температура видеокарты", string.format("%.0f °C", s.gpuTemp), s.gpuTemp >= 85 and "hot" or s.gpuTemp >= 70 and "warm" or "ok", s.gpuTemp / 100 } end
  r[#r + 1] = { "Вентилятор процессора", s.fan > 0 and (s.fan .. " об/мин") or "нет (пассивное)", "ok", s.fan / 2400 }
  r[#r + 1] = { "Нагрузка процессора", string.format("%.0f %%", s.cpu), "ok", load }
  r[#r + 1] = { "Vcore", string.format("%.3f В", 0.95 + 0.30 * load), "ok", nil }
  r[#r + 1] = { "+12V", string.format("%.2f В", 12.12 - 0.30 * psuLoad), "ok", nil }
  r[#r + 1] = { "+5V", string.format("%.2f В", 5.04 - 0.10 * psuLoad), "ok", nil }
  r[#r + 1] = { "+3.3V", string.format("%.2f В", 3.32 - 0.05 * psuLoad), "ok", nil }
  r[#r + 1] = { "Потребление", string.format("%d Вт из %d Вт", s.power, s.psu), psuLoad > 0.9 and "hot" or "ok", psuLoad }
  return r
end

-- Ввести строку (пароль): рисует поле в окошке, возвращает текст или nil (отмена)
local function askLine(title, hidden, drawBox)
  local text = ""
  while true do
    drawBox(title, hidden and string.rep("*", ulen(text)) or text)
    local ev = { os.pullEvent() }
    if ev[1] == "char" then
      if ulen(text) < 32 then text = text .. ev[2] end
    elseif ev[1] == "paste" then text = usub(text .. ev[2], 1, 32)
    elseif ev[1] == "key" then
      if ev[2] == "enter" then return text
      elseif ev[2] == "backspace" then text = usub(text, 1, -2)
      elseif ev[2] == "tab" or ev[2] == "delete" then return nil end
    elseif ev[1] == "mouse_click" then return nil
    end
  end
end

-- ===================== Тема: классическая (синяя, текстовая) =====================
local C
if rgb then
  -- палитра старых BIOS (EGA): тёмно-синий фон, серые полосы, жёлтые значения
  C = { bg = 0x0000A8, text = 0xFCFCFC, value = 0xFCFC54, dim = 0xA8A8A8, title = 0xA8A8A8, titleText = 0x0000A8,
        sel = 0xA80000, selText = 0xFCFCFC, help = 0xA8A8A8, warn = 0xFC5454, ok = 0x54FC54 }
elseif color then
  C = { bg = colors.blue, text = colors.white, value = colors.yellow, dim = colors.lightGray, title = colors.lightGray,
        titleText = colors.blue, sel = colors.red, selText = colors.white, help = colors.lightGray, warn = colors.red, ok = colors.lime }
else
  C = { bg = colors.black, text = colors.white, value = colors.white, dim = colors.white, title = colors.white,
        titleText = colors.black, sel = colors.white, selText = colors.black, help = colors.white, warn = colors.white, ok = colors.white }
end

local function classicBox(title, value)
  local bw = math.min(W - 4, 44)
  local x, y = math.floor((W - bw) / 2) + 1, math.floor(H / 2) - 2
  fill(x, y, bw, 5, C.title)
  at(x + 2, y, pad(" " .. title .. " ", bw - 4), C.titleText, C.title)
  at(x + 2, y + 2, pad(value .. "_", bw - 4), C.text, C.bg)
  at(x + 2, y + 4, pad("Enter — готово, Tab — отмена", bw - 4), C.titleText, C.title)
end

local function drawClassic(st)
  W, H = term.getSize()
  fill(1, 1, W, H, C.bg)
  fill(1, 1, W, 1, C.title)
  center(1, st.info.vendor .. " Setup Utility", C.titleText, C.title)
  -- вкладки
  local x = 2
  fill(1, 2, W, 1, C.bg)
  for i, p in ipairs(st.pages) do
    local label = " " .. p.title .. " "
    if i == st.page then at(x, 2, label, C.selText, C.sel) else at(x, 2, label, C.text, C.bg) end
    st.tabX = st.tabX or {}
    st.tabX[i] = { x, x + ulen(label) - 1 }
    x = x + ulen(label) + 1
  end
  local helpW = W >= 70 and 26 or 0
  local listW = W - helpW - 2
  at(1, 3, string.rep("─", W), C.dim, C.bg)
  local page = st.pages[st.page]
  local y = 5
  local labelW = math.min(30, math.floor(listW * 0.5))
  local cur = page.items[st.row]
  if page.id == "monitor" then
    at(3, y, "PC Health Status — мониторинг", C.value, C.bg)
    y = y + 2
    for _, r in ipairs(readings()) do
      at(3, y, pad(r[1], labelW), C.text, C.bg)
      at(3 + labelW, y, r[2], r[3] == "hot" and C.warn or r[3] == "warm" and C.value or C.ok, C.bg)
      y = y + 1
    end
  elseif page.id == "boot" then
    at(3, y, "Порядок загрузки (сверху — первым):", C.text, C.bg)
    y = y + 2
    for i, id in ipairs(st.cfg.bootOrder) do
      local d = st.dev[id]
      local present = d and d.present
      local line = string.format("%d. %s", i, pad(DEVICE_NAMES[id] or id, 18)) .. (present and ("  " .. (d.path or "")) or "  (нет)")
      if i == st.orderSel then at(3, y, pad(line, labelW + 20), C.selText, C.sel)
      else at(3, y, line, present and C.value or C.dim, C.bg) end
      y = y + 1
    end
    y = y + 1
    at(3, y, "↑↓ — выбрать, + / − — сдвинуть", C.dim, C.bg)
  else
    for i, item in ipairs(page.items) do
      local label = item.label
      local value = valueText(item, st.cfg, st.dev)
      local selected = i == st.row
      if item.kind == "info" then
        at(3, y, pad(label, labelW), C.dim, C.bg)
        at(3 + labelW, y, value, C.text, C.bg)
      else
        local v = item.kind == "action" and item.action ~= "password" and "" or ("[" .. value .. "]")
        if selected then
          at(3, y, pad(label, labelW), C.selText, C.sel)
          at(3 + labelW, y, v, C.selText, C.sel)
        else
          at(3, y, pad(label, labelW), C.text, C.bg)
          at(3 + labelW, y, v, C.value, C.bg)
        end
      end
      y = y + 1
    end
  end
  -- справка
  if helpW > 0 then
    local hx = W - helpW
    for yy = 4, H - 2 do at(hx, yy, "│", C.dim, C.bg) end
    at(hx + 2, 4, "Item Help", C.value, C.bg)
    local help = page.id == "boot" and page.items[1].help or (cur and cur.help) or ""
    local line, yy = "", 6
    for word in help:gmatch("%S+") do
      if ulen(line) + ulen(word) + 1 > helpW - 3 then at(hx + 2, yy, line, C.help, C.bg); yy = yy + 1; line = word
      else line = line == "" and word or (line .. " " .. word) end
    end
    if line ~= "" then at(hx + 2, yy, line, C.help, C.bg) end
  end
  if st.dirty then at(W - 13, H - 1, "* изменено", C.value, C.bg) end
  fill(1, H, W, 1, C.title)
  at(2, H, "←→ Вкладки  ↑↓ Пункт  Enter/+/- Изменить  F10 Сохранить", C.titleText, C.title)
end

-- ===================== Тема: UEFI (графическая, мышь) =====================
local U = {
  bg = 0x12141A, panel = 0x1C1F27, panel2 = 0x252935, line = 0x343A48, text = 0xE6E9EF, dim = 0x8D95A3,
  accent = 0xE0442B, accent2 = 0xFF8A3D, ok = 0x5BD982, warn = 0xF2B13C, bad = 0xE5534B, sel = 0x2D3342,
}

local LOGO = {
  "█▀▄▀█ █ █▄ █ █▀▀ █▀▀ █▀█ █▀█ █▀▀",
  "█ ▀ █ █ █ ▀█ ██▄ █▄▄ █▄█ █▀▄ ██▄",
}

local function uefiBox(title, value)
  local bw = math.min(W - 4, 50)
  local x, y = math.floor((W - bw) / 2) + 1, math.floor(H / 2) - 3
  fill(x, y, bw, 7, U.panel2)
  at(x + 2, y + 1, title, U.text, U.panel2)
  fill(x + 2, y + 3, bw - 4, 1, U.bg)
  at(x + 3, y + 3, value .. "_", U.text, U.bg)
  at(x + 2, y + 5, "Enter — готово, Tab или клик — отмена", U.dim, U.panel2)
end

local function bar(x, y, w, frac, col)
  frac = math.max(0, math.min(1, frac or 0))
  local n = math.floor(frac * w + 0.5)
  at(x, y, string.rep("█", n), col, U.panel)
  at(x + n, y, string.rep("░", w - n), U.line, U.panel)
end

local function drawUefi(st)
  W, H = term.getSize()
  st.hits = {}
  local function hit(x, y, w, h, act) st.hits[#st.hits + 1] = { x, y, w, h, act } end
  fill(1, 1, W, H, U.bg)
  -- верхняя полоса
  fill(1, 1, W, 3, U.panel)
  at(3, 2, "▌", U.accent, U.panel)
  at(5, 2, st.info.vendor, U.text, U.panel)
  at(6 + ulen(st.info.vendor), 2, "Utility  —  " .. st.info.board, U.dim, U.panel)
  local clock = "день " .. tostring(st.info.day or "?") .. "  " .. tostring(st.info.time or "")
  at(W - ulen(clock) - 2, 2, clock, U.text, U.panel)
  -- левая колонка вкладок
  local navW = 22
  fill(1, 4, navW, H - 5, U.panel)
  for i, p in ipairs(st.pages) do
    local y = 5 + (i - 1) * 3
    local on = i == st.page
    if on then fill(1, y, navW, 2, U.sel); at(1, y, "▌", U.accent, U.sel); at(1, y + 1, "▌", U.accent, U.sel) end
    at(3, y, p.ru, on and U.text or U.dim, on and U.sel or U.panel)
    at(3, y + 1, p.title, on and U.accent2 or U.line, on and U.sel or U.panel)
    hit(1, y, navW, 2, { tab = i })
  end
  -- основная часть
  local x0, cw = navW + 3, W - navW - 4
  local page = st.pages[st.page]
  local y = 6
  at(x0, 5, page.ru, U.text, U.bg)
  if page.id == "main" then
    -- карточки: процессор, память, температура
    local s = computer.stats()
    local cards = {
      { "Процессор", st.info.cpu, string.format("%.0f %%", s.cpu), s.cpu / 100, U.accent2 },
      { "Память", string.format("%d КБ", st.info.ram), string.format("%.0f %%", s.memory.used / math.max(1, s.memory.total) * 100), s.memory.used / math.max(1, s.memory.total), 0x4EA1FF },
      { "Температура ЦП", string.format("%.0f °C", s.cpuTemp), s.fan > 0 and (s.fan .. " об/мин") or "пассивное", s.cpuTemp / 100, s.cpuTemp >= 80 and U.bad or U.ok },
    }
    local cardW = math.max(18, math.floor((cw - 4) / 3))
    for i, cd in ipairs(cards) do
      local cx = x0 + (i - 1) * (cardW + 2)
      if cx + cardW - 1 <= W then
        fill(cx, y, cardW, 6, U.panel)
        at(cx + 1, y, cd[1], U.dim, U.panel)
        at(cx + 1, y + 1, pad(cd[2], cardW - 2), U.text, U.panel)
        at(cx + 1, y + 3, cd[3], cd[5], U.panel)
        bar(cx + 1, y + 4, cardW - 2, cd[4], cd[5])
      end
    end
    y = y + 8
    for _, item in ipairs(page.items) do
      at(x0, y, pad(item.label, 22), U.dim, U.bg)
      at(x0 + 22, y, item.value, U.text, U.bg)
      y = y + 1
    end
  elseif page.id == "monitor" then
    for _, r in ipairs(readings()) do
      fill(x0, y, cw, 1, U.panel)
      at(x0 + 1, y, pad(r[1], 26), U.dim, U.panel)
      local col = r[3] == "hot" and U.bad or r[3] == "warm" and U.warn or U.ok
      at(x0 + 28, y, pad(r[2], 18), col, U.panel)
      if r[4] then bar(x0 + 47, y, math.max(4, cw - 49), r[4], col) end
      y = y + 2
    end
  elseif page.id == "boot" then
    at(x0, y, "Приоритет загрузки: нажмите стрелку, чтобы сдвинуть устройство", U.dim, U.bg)
    y = y + 2
    for i, id in ipairs(st.cfg.bootOrder) do
      local d = st.dev[id]
      local present = d and d.present
      local on = i == st.orderSel
      fill(x0, y, cw, 2, on and U.sel or U.panel)
      at(x0 + 1, y, tostring(i), U.accent2, on and U.sel or U.panel)
      at(x0 + 4, y, DEVICE_NAMES[id] or id, present and U.text or U.dim, on and U.sel or U.panel)
      at(x0 + 4, y + 1, present and ("подключено: " .. (d.path or "")) or "нет устройства", present and U.ok or U.dim, on and U.sel or U.panel)
      at(x0 + cw - 8, y, " ▲ ", U.text, U.panel2)
      at(x0 + cw - 4, y, " ▼ ", U.text, U.panel2)
      hit(x0, y, cw - 9, 2, { order = i })
      hit(x0 + cw - 8, y, 3, 1, { orderUp = i })
      hit(x0 + cw - 4, y, 3, 1, { orderDown = i })
      y = y + 3
    end
  else
    for i, item in ipairs(page.items) do
      local on = i == st.row
      fill(x0, y, cw, 2, on and U.sel or U.panel)
      at(x0 + 1, y, item.label, U.text, on and U.sel or U.panel)
      if item.help then at(x0 + 1, y + 1, usub(item.help, 1, cw - 26), U.dim, on and U.sel or U.panel) end
      local vx = x0 + cw - 22
      if item.kind == "toggle" then
        local v = st.cfg[item.key]
        at(vx + 10, y, v and " ВКЛ ●" or "○ ВЫКЛ ", v and 0xFFFFFF or U.dim, v and U.accent or U.line)
      elseif item.kind == "choice" then
        at(vx, y, "◄ " .. pad(valueText(item, st.cfg), 15) .. " ►", U.accent2, on and U.sel or U.panel)
        hit(vx, y, 2, 1, { row = i, dir = -1 })
        hit(vx + 18, y, 2, 1, { row = i, dir = 1 })
      elseif item.kind == "action" then
        local label = item.action == "password" and (st.cfg[item.key] and "Изменить" or "Задать") or "Выполнить"
        if item.action == "password" then at(vx, y + 1, st.cfg[item.key] and "установлен" or "не задан", U.dim, on and U.sel or U.panel) end
        at(vx + 8, y, " " .. pad(label, 11) .. " ", 0xFFFFFF, item.action == "save" and U.accent or U.line)
      end
      hit(x0, y, cw, 2, { row = i, activate = item.kind ~= "choice" })
      y = y + 3
    end
  end
  -- нижняя полоса
  fill(1, H - 1, W, 2, U.panel)
  local bx = 3
  for _, b in ipairs({ { "F10  Сохранить и выйти", "save", U.accent }, { "F5  По умолчанию", "defaults", U.line }, { "Выйти без сохранения", "discard", U.line } }) do
    local w = ulen(b[1]) + 4
    at(bx, H - 1, "  " .. b[1] .. "  ", 0xFFFFFF, b[3])
    hit(bx, H - 1, w, 1, { action = b[2] })
    bx = bx + w + 2
  end
  if st.dirty then at(W - 14, H - 1, "● изменено", U.warn, U.panel) end
  at(bx, H, "мышь или ←→↑↓ Enter", U.dim, U.panel)
end

-- ===================== Цикл настройки =====================
local function runSetup(theme)
  local st = { page = 1, row = 1, orderSel = 1, dirty = false }
  st.info = gatherInfo()
  st.dev = devices()
  local saved = bios.get()
  local function copy(t)
    local o = {}
    for k, v in pairs(t) do o[k] = type(v) == "table" and copy(v) or v end
    return o
  end
  st.cfg = copy(saved)
  st.pages = buildPages(st.info, st.cfg, st.dev, st)
  st.passwords = {}
  local draw = theme == "uefi" and drawUefi or drawClassic
  local box = theme == "uefi" and uefiBox or classicBox

  local function firstEditable(page)
    for i, it in ipairs(page.items) do if it.kind ~= "info" then return i end end
    return 1
  end
  st.row = firstEditable(st.pages[1])

  local function save()
    local changes = copy(st.cfg)
    changes.biosPassword, changes.bootPassword = nil, nil
    for k, v in pairs(st.passwords) do changes[k] = v end
    local ok, err = bios.set(changes)
    return ok, err
  end

  local function activate(item)
    if not item then return end
    if item.kind == "toggle" or item.kind == "choice" then change(item, st.cfg, 1); st.dirty = true
    elseif item.kind == "action" then
      if item.action == "password" then
        local p1 = askLine("Новый пароль (пусто — снять):", true, box)
        if p1 == nil then return end
        if p1 ~= "" then
          local p2 = askLine("Повторите пароль:", true, box)
          if p2 ~= p1 then askLine("Пароли не совпали — Enter", false, box); return end
        end
        st.passwords[item.key] = p1
        st.cfg[item.key] = p1 ~= ""
        st.dirty = true
      elseif item.action == "save" then return "save"
      elseif item.action == "discard" then return "discard"
      elseif item.action == "defaults" then
        st.cfg.bootOrder = { "nvme", "sata", "hdd", "usb", "floppy" }
        st.cfg.cores, st.cfg.lan, st.cfg.usb, st.cfg.wifi = 0, true, true, true
        st.cfg.powerLoss, st.cfg.redstone, st.cfg.fastBoot = 0, false, false
        st.dirty = true
      end
    end
  end

  local function moveOrder(i, dir)
    local o = st.cfg.bootOrder
    local j = i + dir
    if j < 1 or j > #o then return end
    o[i], o[j] = o[j], o[i]
    st.orderSel = j
    st.dirty = true
  end

  local timer = os.startTimer(1)
  while true do
    draw(st)
    local ev = { os.pullEvent() }
    local page = st.pages[st.page]
    local result
    if ev[1] == "timer" and ev[2] == timer then
      timer = os.startTimer(1)                       -- мониторинг и часы обновляются сами
    elseif ev[1] == "key" then
      local k = ev[2]
      if k == "left" then st.page = (st.page - 2) % #st.pages + 1; st.row = firstEditable(st.pages[st.page])
      elseif k == "right" then st.page = st.page % #st.pages + 1; st.row = firstEditable(st.pages[st.page])
      elseif k == "f10" then result = "save"
      elseif k == "f5" then result = activate({ kind = "action", action = "defaults" })
      elseif page.id == "boot" then
        if k == "up" then st.orderSel = math.max(1, st.orderSel - 1)
        elseif k == "down" then st.orderSel = math.min(#st.cfg.bootOrder, st.orderSel + 1)
        elseif k == "enter" then moveOrder(st.orderSel, -1) end
      elseif k == "up" or k == "down" then
        local n = #page.items
        local r = st.row
        for _ = 1, n do
          r = k == "up" and (r - 2) % n + 1 or r % n + 1
          if page.items[r].kind ~= "info" and page.items[r].kind ~= "monitor" then break end
        end
        st.row = r
      elseif k == "enter" or k == "space" then
        result = activate(page.items[st.row])
      end
    elseif ev[1] == "char" then
      local item = page.items[st.row]
      if page.id == "boot" then
        if ev[2] == "+" then moveOrder(st.orderSel, -1) elseif ev[2] == "-" then moveOrder(st.orderSel, 1) end
      elseif item and (ev[2] == "+" or ev[2] == "-") and (item.kind == "toggle" or item.kind == "choice") then
        change(item, st.cfg, ev[2] == "+" and 1 or -1)
        st.dirty = true
      end
    elseif ev[1] == "mouse_click" then
      local mx, my = ev[3], ev[4]
      if st.hits then
        for i = #st.hits, 1, -1 do
          local h = st.hits[i]
          if mx >= h[1] and mx < h[1] + h[3] and my >= h[2] and my < h[2] + h[4] then
            local a = h[5]
            if a.tab then st.page = a.tab; st.row = firstEditable(st.pages[a.tab])
            elseif a.orderUp then moveOrder(a.orderUp, -1)
            elseif a.orderDown then moveOrder(a.orderDown, 1)
            elseif a.order then st.orderSel = a.order
            elseif a.action then result = activate({ kind = "action", action = a.action })
            elseif a.row then
              st.row = a.row
              local item = page.items[a.row]
              if a.dir then change(item, st.cfg, a.dir); st.dirty = true
              elseif a.activate then result = activate(item) end
            end
            break
          end
        end
      elseif st.tabX and my == 2 then
        for i, r in ipairs(st.tabX) do if mx >= r[1] and mx <= r[2] then st.page = i; st.row = firstEditable(st.pages[i]) end end
      end
    end
    if result == "save" then
      local ok, err = save()
      if not ok then askLine("Не сохранилось: " .. tostring(err) .. " — Enter", false, box) else return "reboot" end
    elseif result == "discard" then
      return "continue"
    end
  end
end

-- ===================== Пароль =====================
local function askPassword(kind, theme)
  local box = theme == "uefi" and uefiBox or classicBox
  for attempt = 1, 3 do
    W, H = term.getSize()
    fill(1, 1, W, H, theme == "uefi" and U.bg or colors.black)
    local p = askLine(kind == "boot" and "Пароль для включения:" or "Пароль BIOS:", true, box)
    if p and bios.check(kind, p) then return true end
  end
  return false
end

-- ===================== Самотест (POST) =====================
-- Возвращает: что делать дальше ("setup" или "boot") и выбранное устройство (из меню F12) или nil
local function post(info, cfg, theme)
  W, H = term.getSize()
  local wantSetup, bootMenu = false, false
  local function poll(seconds)
    local t = os.startTimer(seconds)
    while true do
      local ev = { os.pullEvent() }
      if ev[1] == "timer" and ev[2] == t then return end
      if ev[1] == "key" and (ev[2] == "delete" or ev[2] == "f2") then wantSetup = true end
      if ev[1] == "key" and ev[2] == "f12" then bootMenu = true end
    end
  end
  term.setBackgroundColor(colors.black)
  term.clear()
  if theme == "uefi" then
    fill(1, 1, W, H, 0x010101)
    local ly = math.floor(H / 2) - 3
    for i, l in ipairs(LOGO) do center(ly + i - 1, l, U.accent, 0x010101) end
    center(ly + 3, info.board, U.dim, 0x010101)
    center(H - 2, "Del или F2 — настройки UEFI   ·   F12 — меню загрузки", U.dim, 0x010101)
    poll(cfg.fastBoot and 0.4 or 1.6)
  else
    local y = 1
    local function line(t, c) at(1, y, t, c or colors.white, colors.black); y = y + 1 end
    line(info.vendor .. " v" .. info.version .. ", An Energy Saving Friend", colors.white)
    line("Copyright (C) MineComputers", colors.lightGray)
    y = y + 1
    line(info.board, colors.yellow)
    y = y + 1
    line("Процессор : " .. info.cpu)
    if not cfg.fastBoot then
      local total = info.ram
      for k = 0, 8 do
        at(1, y, string.format("Проверка памяти : %d КБ", math.floor(total * k / 8)), colors.white, colors.black)
        poll(0.1)
      end
      at(1, y, string.format("Проверка памяти : %d КБ OK", total), colors.white, colors.black)
    end
    y = y + 2
    local devs = devices()
    for _, id in ipairs({ "nvme", "sata", "hdd", "usb", "floppy" }) do
      local d = devs[id]
      if d and d.present then line(string.format("  %-16s: %s", DEVICE_NAMES[id], d.path or ""), colors.lightGray) end
    end
    at(1, H, "Нажмите DEL — настройки, F12 — меню загрузки", colors.white, colors.black)
    poll(cfg.fastBoot and 0.3 or 1.0)
  end
  return wantSetup, bootMenu
end

-- Меню F12: выбрать устройство на один раз
local function bootMenu(theme)
  local devs = devices()
  local list = {}
  for _, id in ipairs({ "nvme", "sata", "hdd", "usb", "floppy" }) do
    if devs[id] and devs[id].present then list[#list + 1] = id end
  end
  if #list == 0 then return nil end
  local sel = 1
  while true do
    W, H = term.getSize()
    local bw, bh = 36, #list + 4
    local x, y = math.floor((W - bw) / 2) + 1, math.floor((H - bh) / 2) + 1
    local pbg, pfg = theme == "uefi" and U.panel2 or colors.lightGray, theme == "uefi" and U.text or colors.black
    fill(x, y, bw, bh, pbg)
    at(x + 2, y, "Загрузить с устройства:", pfg, pbg)
    for i, id in ipairs(list) do
      local on = i == sel
      at(x + 2, y + 1 + i, pad(DEVICE_NAMES[id], bw - 4), on and (theme == "uefi" and 0xFFFFFF or colors.white) or pfg,
        on and (theme == "uefi" and U.accent or colors.blue) or pbg)
    end
    local ev = { os.pullEvent() }
    if ev[1] == "key" then
      if ev[2] == "up" then sel = math.max(1, sel - 1)
      elseif ev[2] == "down" then sel = math.min(#list, sel + 1)
      elseif ev[2] == "enter" then return list[sel]
      elseif ev[2] == "tab" then return nil end
    elseif ev[1] == "mouse_click" then
      local i = ev[4] - y - 1
      if list[i] then return list[i] end
    end
  end
end

--- Включение: самотест, пароли, настройки, выбор устройства. Возвращает путь к startup.lua для запуска (или nil).
function M.start()
  local info = gatherInfo()
  local cfg = bios.get()
  local theme = info.theme == "uefi" and color and "uefi" or "classic"
  -- Классический BIOS — в «текстовом режиме» 80×25 (крупные буквы, как на старых ПК)
  local oldW, oldH = term.getSize()
  local function textMode(on)
    if theme ~= "classic" or not term.setResolution then return end
    local mw, mh = term.maxResolution()
    if on then pcall(term.setResolution, math.min(80, mw), math.min(25, mh))
    else pcall(term.setResolution, oldW, oldH) end
  end
  textMode(true)
  W, H = term.getSize()
  local setup, menu = post(info, cfg, theme)
  if cfg.bootPassword and not askPassword("boot", theme) then
    term.setBackgroundColor(colors.black)
    term.clear()
    os.shutdown()
    while true do os.pullEventRaw() end
  end
  if setup then
    if (not cfg.biosPassword) or askPassword("bios", theme) then
      local r = runSetup(theme)
      if r == "reboot" then
        term.setBackgroundColor(colors.black)
        term.clear()
        os.reboot()
        while true do os.pullEventRaw() end
      end
    end
    cfg = bios.get()
  end
  local chosen = menu and bootMenu(theme) or nil
  textMode(false)
  -- Устройство загрузки: выбранное в меню или первое по порядку, на котором есть startup.lua
  local devs = devices()
  local order = chosen and { chosen } or cfg.bootOrder
  local bootFrom, startup
  for _, id in ipairs(order) do
    local d = devs[id]
    if d and d.present then
      local path = (d.path == "/" and "" or d.path) .. "/startup.lua"
      if fs.exists(path) then bootFrom, startup = id, path; break end
      if not bootFrom then bootFrom = id end
    end
  end
  -- Плата не сообщает об устройствах (старый корпус или тест) — как раньше: /startup.lua на системном диске
  if not startup and not next(devs) and fs.exists("/startup.lua") then startup = "/startup.lua" end
  -- Загрузка ОС: время зависит от скорости диска
  local seconds = (computer.limits().bootSeconds or 0)
  term.setBackgroundColor(colors.black)
  term.setTextColor(colors.white)
  term.clear()
  W, H = term.getSize()
  if seconds > 0 then
    local name = DEVICE_NAMES[bootFrom or "nvme"] or "диска"
    local steps = math.max(1, math.floor(seconds * 10))
    for i = 1, steps do
      at(1, 1, "Загрузка системы с устройства: " .. name, colors.white, colors.black)
      local bw = math.max(4, math.min(40, W - 2))
      local n = math.floor(bw * i / steps)
      at(1, 2, string.rep("█", n) .. string.rep("░", bw - n) .. string.format(" %d%%", math.floor(i * 100 / steps)), colors.white, colors.black)
      os.sleep(seconds / steps)
    end
    term.clear()
  end
  term.setCursorPos(1, 1)
  return startup
end

return M
