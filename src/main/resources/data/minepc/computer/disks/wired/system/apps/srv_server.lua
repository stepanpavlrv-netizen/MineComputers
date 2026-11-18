-- SWired: «Сервер» — сводка: всё ли в порядке, процессоры, память, питание, диски, сеть, копии, службы, замечания.
local G, T, U, wm, K = wired.gfx, wired.theme, wired.ui, wired.wm, wired.srvkit

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Сервер — " .. name, icon = "srv", w = 100, h = 34, minW = 78, minH = 26 }
  local st = {}
  local win

  local function open(id)
    wired.apps.launch(wired.apps.get(id), type(arg) == "table" and arg or nil)
  end

  function app.init(w)
    win = w
    K.poll(w, st, 3, function()
      local s, e = B.status()
      if not s then return nil, e end
      s.events = B.journal(5) or {}
      return s
    end)
  end

  local LEVEL = { ok = "Всё в порядке", warn = "Есть замечания", err = "Нужно вмешаться" }

  local function cards(c, s, y, h)
    local stats, hw = s.stats or {}, s.hw or {}
    local cw = math.floor((c.w - 4) / 3)
    local function at(i) return 2 + ((i - 1) % 3) * (cw + 1), y + math.floor((i - 1) / 3) * (h + 1) end
    -- 1. процессоры
    local x, yy = at(1)
    local cores = stats.cores or {}
    local load = 0
    for _, v in ipairs(cores) do load = load + v end
    load = #cores > 0 and load / #cores or (stats.cpu or 0)
    K.card(c, x, yy, cw, h, "Процессоры" .. (hw.cpus and hw.cpus > 0 and (" · " .. hw.cpus .. " × " .. math.floor((hw.cores or #cores) / hw.cpus) .. " ядер") or (" · " .. #cores .. " ядер")))
    c.text(x + 1, yy + 1, string.format("%d %%", math.floor(load + 0.5)), T.fg, T.side)
    K.meter(c, x + 1, yy + 2, cw - 2, load)
    local temp = hw.temp or stats.cpuTemp
    c.text(x + 1, yy + 3, G.cut((temp and (math.floor(temp) .. " °C") or "") .. ((hw.rpm and hw.rpm > 0) and (" · " .. hw.rpm .. " об/мин") or ""), cw - 2), T.dim, T.side)
    -- 2. память
    x, yy = at(2)
    local mem = stats.memory or {}
    K.card(c, x, yy, cw, h, "Память")
    c.text(x + 1, yy + 1, G.cut(K.size(mem.used) .. " из " .. K.size(mem.total), cw - 2), T.fg, T.side)
    K.meter(c, x + 1, yy + 2, cw - 2, (mem.total or 0) > 0 and 100 * (mem.used or 0) / mem.total or 0, T.link)
    -- 3. питание
    x, yy = at(3)
    K.card(c, x, yy, cw, h, "Питание")
    if s.hw then
      local n, line = 0, ""
      for i, p in ipairs(hw.psu or {}) do
        if p.present then n = n + 1 end
        line = line .. (p.present and "● " or "○ ") .. "БП " .. i .. "   "
      end
      c.text(x + 1, yy + 1, n .. " из 2" .. (hw.reserve and " · резерв есть" or (n > 0 and " · без резерва" or "")),
        hw.reserve and T.ok or (n == 0 and T.err or T.warn), T.side)
      c.text(x + 1, yy + 2, G.cut((hw.watts or 0) .. " Вт", cw - 2), T.dim, T.side)
      c.text(x + 1, yy + 3, G.cut(line, cw - 2), hw.reserve and T.ok or T.warn, T.side)
    else
      c.text(x + 1, yy + 1, G.cut((stats.power or 0) .. " из " .. (stats.psu or 0) .. " Вт", cw - 2), T.fg, T.side)
      c.text(x + 1, yy + 2, G.cut("не серверное шасси", cw - 2), T.faint, T.side)
    end
    -- 4. диски
    x, yy = at(4)
    local cap, used = 0, 0
    for _, d in ipairs(stats.disks or {}) do
      if d.path ~= "/disk" and not tostring(d.path):find("^/usb") and not tostring(d.path):find("^/disk%d") then
        cap, used = cap + (d.capacity or 0), used + (d.used or 0)
      end
    end
    local raid = hw.raid or s.raid
    K.card(c, x, yy, cw, h, "Диски" .. (type(raid) == "table" and raid.level and (" · RAID " .. tostring(raid.level)) or ""))
    c.text(x + 1, yy + 1, G.cut(K.size(used) .. " из " .. K.size(cap), cw - 2), T.fg, T.side)
    K.meter(c, x + 1, yy + 2, cw - 2, cap > 0 and 100 * used / cap or 0, 0xff8a3d)
    local worst
    for i, b in ipairs(hw.bays or {}) do
      if b.state == "failed" then worst = { T.err, "✕ отсек " .. i .. " отказал" } break
      elseif b.state == "warning" and not worst then worst = { T.warn, "▲ отсек " .. i .. ": износ " .. tostring(b.wear) .. " %" } end
    end
    c.text(x + 1, yy + 3, G.cut(worst and worst[2] or "диски в порядке", cw - 2), worst and worst[1] or T.ok, T.side)
    -- 5. сеть
    x, yy = at(5)
    local n = stats.net or {}
    K.card(c, x, yy, cw, h, "Сеть")
    c.text(x + 1, yy + 1, G.cut("↓ " .. K.size(n.down) .. "/с  ↑ " .. K.size(n.up) .. "/с", cw - 2), T.fg, T.side)
    c.text(x + 1, yy + 2, G.cut((s.address or ""):sub(1, 8) .. (n.speed and n.speed > 0 and (" · " .. K.size(n.speed) .. "/с") or " · нет сети"), cw - 2), T.dim, T.side)
    -- 6. копии
    x, yy = at(6)
    K.card(c, x, yy, cw, h, "Резервные копии")
    local last
    for _, b in ipairs(s.backups or {}) do if b.last and (not last or b.last.time > last.time) then last = b.last end end
    if last then
      c.text(x + 1, yy + 1, G.cut((last.ok and "✔ " or "✕ ") .. require("swired").fmtTime(last.time), cw - 2), last.ok and T.ok or T.err, T.side)
      c.text(x + 1, yy + 2, G.cut(last.ok and K.size(last.size) or tostring(last.err), cw - 2), T.dim, T.side)
    else
      c.text(x + 1, yy + 1, "копий ещё нет", T.warn, T.side)
      c.text(x + 1, yy + 2, G.cut("настройте в «Копиях»", cw - 2), T.dim, T.side)
    end
    c.hit(x, yy, cw, h, "open", "srv_backup")
    return y + 2 * (h + 1)
  end

  function app.draw(w, c)
    if K.waiting(c, st) then return end
    local s = st.data
    -- шапка
    local col = K.color(s.level)
    c.text(2, 2, "●", col)
    c.text(4, 2, LEVEL[s.level] or "?", T.fg)
    local sub = s.name .. " · " .. (s.address or ""):sub(1, 8) ..
      (s.hw and s.hw.slot and (" · место " .. s.hw.slot .. " в стойке") or "") .. " · работает " .. K.uptime(s.uptime)
    c.text(4, 3, G.cut(sub, c.w - 34), T.dim)
    local bx = c.w - 30
    bx = c.button(bx, 2, "↻ Перезагрузить", "reboot") + 1
    c.button(bx, 2, "⏻ Выключить", "off")
    local y = cards(c, s, 5, 4)
    -- службы
    K.card(c, 2, y, c.w - 3, 3, "Службы")
    c.text(c.w - 20, y, "открыть «Службы» →", T.link, T.side)
    c.hit(c.w - 20, y, 18, 1, "open", "srv_services")
    local x = 3
    for _, sv in ipairs(s.services or {}) do
      local label = (sv.running and "● " or "○ ") .. sv.name
      if x + G.len(label) > c.w - 3 then break end
      c.text(x, y + 1, sv.running and "●" or "○", sv.failed and T.err or (sv.running and T.ok or T.faint), T.side)
      c.text(x + 2, y + 1, sv.name, sv.running and T.fg or T.dim, T.side)
      x = x + G.len(label) + 3
    end
    y = y + 4
    -- замечания и события
    local rows = c.h - y - 1
    K.card(c, 2, y, c.w - 3, rows + 1, #s.problems > 0 and "Замечания" or "Последние события")
    c.text(c.w - 16, y, "весь журнал →", T.link, T.side)
    c.hit(c.w - 16, y, 14, 1, "open", "srv_journal")
    local yy = y + 1
    for _, p in ipairs(s.problems) do
      if yy > y + rows then break end
      c.text(3, yy, K.mark(p.level), K.color(p.level), T.side)
      c.text(5, yy, G.cut(p.text, c.w - 8), T.fg, T.side)
      yy = yy + 1
    end
    if #s.problems > 0 and yy <= y + rows then yy = yy + 1 end
    local S = require("swired")
    for _, e in ipairs(s.events or {}) do
      if yy > y + rows then break end
      c.text(3, yy, S.fmtTime(e.time), T.faint, T.side)
      c.text(15, yy, K.mark(e.level), K.color(e.level), T.side)
      c.text(17, yy, G.cut(e.src .. ": " .. e.text, c.w - 20), T.dim, T.side)
      yy = yy + 1
    end
    if st.err then c.text(2, c.h, G.cut("Связь: " .. st.err, c.w - 3), T.err) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    if a == "open" then open(b)
    elseif a == "reboot" or a == "off" then
      wm.confirm(a == "reboot" and "Перезагрузить сервер" or "Выключить сервер",
        (a == "reboot" and "Перезагрузить «" or "Выключить «") .. name .. "»? Сайты и почта на это время перестанут отвечать.",
        function(ok)
          if not ok then return end
          K.run(w, function() return (a == "reboot" and B.reboot or B.shutdown)() end, function() end)
        end, a == "reboot" and "Перезагрузить" or "Выключить")
    end
  end

  return app
end
