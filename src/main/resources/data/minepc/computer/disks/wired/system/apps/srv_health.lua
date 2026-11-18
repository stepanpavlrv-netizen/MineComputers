-- SWired: «Состояние» — железо сервера: ядра, температура, вентиляторы, блоки питания, память, BMC, отсеки дисков, RAID.
local G, T, wm, K = wired.gfx, wired.theme, wired.wm, wired.srvkit

local STATE = { ok = "исправен", warning = "износ", failed = "отказал", rebuilding = "перенос", empty = "пусто" }
local RAID = { ok = "массив исправен", degraded = "без резерва!", rebuilding = "восстанавливается", failed = "развалился!" }

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Состояние — " .. name, icon = "health", w = 104, h = 32, minW = 84, minH = 24 }
  local st = {}

  function app.init(w)
    K.poll(w, st, 2, function() return B.status() end)
  end

  local function cores(c, x, y, w, list, from, to, label)
    c.text(x, y, G.cut(label, w), T.dim, T.side)
    local n = to - from + 1
    if n <= 0 then return end
    local bw = math.max(1, math.floor((w - (n - 1)) / n))
    for i = from, to do
      local v = list[i] or 0
      local bx = x + (i - from) * (bw + 1)
      -- столбик в 3 строки из полублоков
      local hpx = math.floor(v / 100 * 6 + 0.5)
      for k = 0, 2 do
        local cellBottom = (2 - k) * 2
        local top, bot = T.field, T.field
        if hpx > cellBottom then bot = T.accent end
        if hpx > cellBottom + 1 then top = T.accent end
        for xx = 0, bw - 1 do c.half(bx + xx, y + 1 + k, 1, top, bot) end
      end
    end
  end

  function app.draw(w, c)
    if K.waiting(c, st) then return end
    local s = st.data
    local stats, hw = s.stats or {}, s.hw
    local half = math.floor((c.w - 4) / 2)
    -- процессоры
    K.card(c, 2, 1, half, 10, nil)
    local list = stats.cores or {}
    local cpus = hw and hw.cpus or 1
    if cpus >= 2 and #list >= 2 then
      local m = math.floor(#list / 2)
      cores(c, 3, 1, half - 2, list, 1, m, "Процессор 1 · " .. m .. " ядер")
      cores(c, 3, 5, half - 2, list, m + 1, #list, "Процессор 2 · " .. (#list - m) .. " ядер")
    else
      cores(c, 3, 1, half - 2, list, 1, #list, "Процессор · " .. #list .. " ядер")
    end
    local temp = hw and hw.temp or stats.cpuTemp
    local tcol = (temp or 0) >= 90 and T.err or (temp or 0) >= 80 and T.warn or T.dim
    c.text(3, 9, G.cut((temp and (math.floor(temp) .. " °C") or "—") ..
      (hw and (" · вентиляторы " .. ((hw.fanTier or 0) > 0 and ("ур. " .. hw.fanTier) or "встроенные") .. ", " ..
        (hw.rpm > 0 and (hw.rpm .. " об/мин") or "стоят")) or ""), half - 2), tcol, T.side)
    if hw and hw.cooling then
      local hot = (hw.heat or 0) > hw.cooling
      c.text(3, 10, G.cut("Тепло " .. (hw.heat or 0) .. " Вт · отвод до " .. hw.cooling .. " Вт" ..
        (hot and " — мало: «Вентиляторы стойки» помощнее" or ""), half - 2), hot and T.warn or T.dim, T.side)
    end
    -- питание, память, BMC
    local x = 3 + half
    K.card(c, x, 1, half, 10, "Питание")
    if hw then
      for i, p in ipairs(hw.psu or {}) do
        c.text(x + 1, 1 + i, p.present and "●" or "○", p.present and T.ok or T.err, T.side)
        c.text(x + 3, 1 + i, G.cut("БП " .. i .. (p.present and (" · " .. tostring(p.wattage or "?") .. " Вт") or " · вынут"), half - 4), T.fg, T.side)
      end
      c.text(x + 1, 4, G.cut("Сервер тянет " .. (hw.watts or 0) .. " Вт", half - 2), T.dim, T.side)
      c.text(x + 1, 5, G.cut(hw.reserve and "Резерв есть: каждый блок тянет сервер один" or "Резерва нет: откажет блок — сервер погаснет", half - 2),
        hw.reserve and T.ok or T.warn, T.side)
    else
      c.text(x + 1, 2, G.cut((stats.power or 0) .. " из " .. (stats.psu or 0) .. " Вт", half - 2), T.fg, T.side)
    end
    local mem = stats.memory or {}
    c.text(x + 1, 7, "Память", T.dim, T.side)
    K.meter(c, x + 9, 7, half - 30, (mem.total or 0) > 0 and 100 * (mem.used or 0) / mem.total or 0, T.link)
    c.text(x + half - 20, 7, G.cut(K.size(mem.used) .. " из " .. K.size(mem.total), 19), T.fg, T.side)
    if hw and hw.bmc then
      c.text(x + 1, 9, "BMC", T.dim, T.side)
      c.text(x + 6, 9, G.cut(tostring(hw.bmc.address):sub(1, 8) .. (hw.bmc.defaultPassword and " · пароль admin — смените" or " · пароль свой"), half - 7),
        hw.bmc.defaultPassword and T.warn or T.fg, T.side)
    end
    -- отсеки
    local y = 12
    if not hw then
      K.card(c, 2, y, c.w - 3, 4, "Диски")
      local yy = y + 1
      for _, d in ipairs(stats.disks or {}) do
        if yy > y + 3 then break end
        c.text(3, yy, G.cut(tostring(d.path) .. "  " .. K.size(d.used) .. " из " .. K.size(d.capacity), c.w - 6), T.fg, T.side)
        yy = yy + 1
      end
      c.text(3, y + 5, "Отсеков, RAID и BMC нет: это не серверное шасси (модуль сервера в стойке).", T.faint)
      return
    end
    local raid = hw.raid
    K.card(c, 2, y, c.w - 3, c.h - y, nil)
    c.text(3, y, "Диски", T.dim, T.side)
    if type(raid) == "table" then
      c.text(10, y, G.cut("· RAID " .. tostring(raid.level or "") .. " «" .. tostring(raid.name or "") .. "»: " ..
        (RAID[raid.state] or tostring(raid.state)) .. (raid.rebuild and (" " .. raid.rebuild .. " %") or ""), c.w - 14),
        (raid.state == "ok") and T.ok or T.err, T.side)
    else
      c.text(10, y, "· без RAID (собрать — команда raid в Терминале)", T.faint, T.side)
    end
    local bays = hw.bays or {}
    local n = math.max(1, #bays)
    local cols = math.max(1, math.min(n, math.floor((c.w - 4) / 19)))
    local bw = math.floor((c.w - 5 - (cols - 1)) / cols)
    local warn
    for i, b in ipairs(bays) do
      local bx = 3 + ((i - 1) % cols) * (bw + 1)
      local by = y + 2 + math.floor((i - 1) / cols) * 6
      local col = b.state == "failed" and T.err or b.state == "warning" and T.warn or b.state == "empty" and T.faint or T.fg
      c.fill(bx, by, bw, 5, T.field)
      c.text(bx + 1, by, "Отсек " .. i, col, T.field)
      if b.state ~= "empty" then
        c.text(bx + 1, by + 1, G.cut(b.name or "диск", bw - 2), T.dim, T.field)
        K.meter(c, bx + 1, by + 2, bw - 2, b.wear or 0, b.state == "ok" and T.ok or col)
        c.text(bx + 1, by + 3, G.cut((STATE[b.state] or b.state) .. " · " .. tostring(b.wear or 0) .. " %", bw - 2), col, T.field)
        if b.state == "warning" or b.state == "failed" then warn = warn or i end
      else
        c.text(bx + 1, by + 1, "пусто", T.faint, T.field)
      end
    end
    if warn then
      local rows = math.ceil(n / cols)
      c.text(3, y + 2 + rows * 6, G.cut("▲ Отсек " .. warn .. ": диск пора менять — вставьте новый в свободный отсек, RAID перенесёт данные сам", c.w - 5), T.warn, T.side)
    end
  end

  function app.event() end

  return app
end
