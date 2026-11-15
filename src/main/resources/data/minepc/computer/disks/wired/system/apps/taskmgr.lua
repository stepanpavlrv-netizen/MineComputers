-- Wired: Диспетчер задач — окна, процессы и нагрузка (ядра, память, видеокарта, диски).
local G, T, I, U, wm = wired.gfx, wired.theme, wired.icons, wired.ui, wired.wm

local TABS = { "Окна", "Процессы", "Производительность" }
local NAMES = { running = "работает", waiting = "ждёт", starting = "запуск", finished = "завершён" }

return function()
  local app = { title = "Диспетчер задач", icon = "task", w = 80, h = 26, minW = 60, minH = 16 }
  local st = { tab = 1, sel = nil, hist = {}, timer = nil }
  local win

  local function tick()
    local ok, s = pcall(computer.stats)
    if ok and s then
      st.stats = s
      local total = 0
      for _, v in ipairs(s.cores or {}) do total = total + v end
      st.hist[#st.hist + 1] = total / math.max(1, #(s.cores or {}))
      while #st.hist > 60 do table.remove(st.hist, 1) end
    end
    if not win.closed then
      wm.redraw(win)
      st.timer = wm.after(1, tick)
    end
  end

  local function bar(c, x, y, w, pct, color)
    local n = math.max(0, math.min(w, math.floor(w * pct / 100 + 0.5)))
    c.text(x, y, string.rep("█", n), color or (pct >= 85 and T.err or pct >= 60 and T.warn or T.ok))
    c.text(x + n, y, string.rep("░", w - n), T.faint)
  end

  function app.init(w)
    win = w
    tick()
  end

  function app.draw(w, c)
    local x = 2
    for i, t in ipairs(TABS) do
      local act = i == st.tab
      c.text(x, 1, " " .. t .. " ", act and T.fg or T.dim, act and T.sel or T.win)
      if act then c.text(x, 2, string.rep("▔", G.len(t) + 2), T.accent) end
      c.hit(x, 1, G.len(t) + 2, 1, "tab", i)
      x = x + G.len(t) + 3
    end
    local s = st.stats or {}
    if st.tab == 1 then
      c.text(2, 4, "Окно", T.faint)
      c.text(c.w - 14, 4, "Состояние", T.faint)
      c.hline(2, 5, c.w - 3)
      local list = wm.windowsInOrder()
      for i, ww in ipairs(list) do
        local y = 5 + i
        if y > c.h - 3 then break end
        local sel = st.sel == ww
        local bg = sel and T.sel or T.win
        if sel then c.fill(2, y, c.w - 3, 1, T.sel) end
        local mini = I.MINI[ww.icon] or I.MINI.app
        c.text(3, y, mini[1], mini[2], bg)
        c.text(5, y, G.cut(ww.title, c.w - 22), T.fg, bg)
        c.text(c.w - 14, y, ww.min and "свёрнуто" or (ww == wm.focus and "активно" or "открыто"), T.dim, bg)
        c.hit(2, y, c.w - 3, 1, "win", ww)
      end
      c.button(c.w - 17, c.h - 1, "Снять задачу", "endwin", st.sel and "primary" or "off")
    elseif st.tab == 2 then
      c.text(2, 4, "PID", T.faint)
      c.text(8, 4, "Программа", T.faint)
      c.text(c.w - 30, 4, "ЦП", T.faint)
      c.text(c.w - 22, 4, "Память", T.faint)
      c.text(c.w - 12, 4, "Состояние", T.faint)
      c.hline(2, 5, c.w - 3)
      local procs = s.processes or {}
      for i, p in ipairs(procs) do
        local y = 5 + i
        if y > c.h - 3 then break end
        local sel = st.sel == p.pid
        local bg = sel and T.sel or T.win
        if sel then c.fill(2, y, c.w - 3, 1, T.sel) end
        c.text(2, y, tostring(p.pid), T.dim, bg)
        c.text(8, y, G.cut(tostring(p.path or "?"), c.w - 40), T.fg, bg)
        c.text(c.w - 30, y, string.format("%d%%", math.floor((p.cpu or 0) + 0.5)), T.fg, bg)
        c.text(c.w - 22, y, textutils.formatSize(p.memory or 0), T.dim, bg)
        c.text(c.w - 12, y, NAMES[p.status] or tostring(p.status or ""), T.dim, bg)
        c.hit(2, y, c.w - 3, 1, "proc", p.pid)
      end
      c.button(c.w - 21, c.h - 1, "Завершить процесс", "kill", st.sel and type(st.sel) == "number" and "primary" or "off")
    else
      local cores = s.cores or {}
      c.text(2, 4, "Процессор", T.fg)
      local half = math.floor((c.w - 4) / 2)
      for i, v in ipairs(cores) do
        local col, row = (i - 1) % 2, math.floor((i - 1) / 2)
        local x0, y = 2 + col * (half + 1), 5 + row
        if y > 12 then break end
        c.text(x0, y, string.format("%2d", i), T.dim)
        bar(c, x0 + 3, y, half - 10, v)
        c.text(x0 + half - 6, y, string.format("%3d%%", math.floor(v + 0.5)), T.fg)
      end
      -- график за минуту
      local gy, gh = 14, math.max(3, c.h - 20)
      local gw = math.min(60, c.w - 4)
      c.text(2, gy - 1, "Нагрузка за минуту", T.dim)
      for i = 1, gw do
        local v = st.hist[#st.hist - gw + i]
        if v then
          local hpx = math.floor(v / 100 * gh * 2 + 0.5)
          for k = 0, gh - 1 do
            local cellBottom = (gh - 1 - k) * 2
            local top, bot = T.win, T.win
            if hpx > cellBottom then bot = T.accent end
            if hpx > cellBottom + 1 then top = T.accent end
            if top ~= T.win or bot ~= T.win then c.half(1 + i, gy + k, 1, top, bot) end
          end
        end
      end
      local mem = s.memory or {}
      local y = c.h - 4
      if mem.total and mem.total > 0 then
        c.text(2, y, "Память", T.dim)
        bar(c, 12, y, c.w - 40, 100 * (mem.used or 0) / mem.total, T.link)
        c.text(c.w - 26, y, textutils.formatSize(mem.used or 0) .. " из " .. textutils.formatSize(mem.total), T.fg)
      end
      c.text(2, y + 1, "Видео", T.dim)
      bar(c, 12, y + 1, c.w - 40, tonumber(s.gpu) or 0, 0xc77dff)
      c.text(c.w - 26, y + 1, string.format("%d%%", math.floor((tonumber(s.gpu) or 0) + 0.5)), T.fg)
      if s.cpuTemp then c.text(2, y + 2, string.format("Температура ЦП %d °C", math.floor(s.cpuTemp)), T.dim) end
    end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    if a == "tab" then st.tab, st.sel = b, nil
    elseif a == "win" or a == "proc" then st.sel = b
    elseif a == "endwin" and type(st.sel) == "table" then
      if st.sel ~= w then wm.close(st.sel, true) end
      st.sel = nil
    elseif a == "kill" and type(st.sel) == "number" then
      pcall(computer.kill, st.sel)
      st.sel = nil
    end
    wm.redraw(w)
  end

  return app
end
