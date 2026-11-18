-- SWired: «Резервные копии» — что копировать, куда (диск, дискета, флешка, другой сервер), когда, с паролем;
-- сделанные копии и восстановление.
local G, T, wm, K = wired.gfx, wired.theme, wired.wm, wired.srvkit

local EVERY = { day = "каждый день", week = "раз в неделю", hour = "каждый час", manual = "только вручную" }
local ORDER = { "day", "week", "hour", "manual" }

return function(arg)
  local B, name = K.backend(arg)
  local app = { title = "Резервные копии — " .. name, icon = "backup", w = 104, h = 30, minW = 84, minH = 22 }
  local st = { sel = 1, msg = nil, copies = {} }
  local win
  local LEFT = 34

  local function reload()
    K.run(win, function()
      local list, e = B.backups()
      if not list then return nil, e end
      local job = list[math.max(1, math.min(st.sel, #list))]
      local copies = job and B.copies(job.id) or {}
      return { list = list, copies = type(copies) == "table" and copies or {} }
    end, function(v, e) if v then st.data, st.err = v, nil else st.err = e end end)
  end

  function app.init(w)
    win = w
    local function tick()
      if w.closed then return end
      if not w.min then reload() end
      wm.after(10, tick)
    end
    tick()
  end

  local function act(fn, ok)
    st.msg = "…"
    wm.redraw(win)
    K.run(win, fn, function(v, e)
      st.msg = v and ok or ("Не вышло: " .. tostring(e))
      reload()
    end)
  end

  local function save(job, ok) act(function() return B.saveBackup(job) end, ok or "Сохранено") end

  local function copyOf(job)
    local j = {}
    for k, v in pairs(job) do j[k] = v end
    j.to = job.to and { kind = job.to.kind, target = job.to.target, user = job.to.user } or nil
    j.hasPassword, j.last, j.history = nil, nil, nil
    return j
  end

  local function where(job)
    local to = job.to or {}
    if to.kind == "server" then return "сервер " .. tostring(to.target) .. " (вход " .. tostring(to.user or "?") .. ")" end
    local t = tostring(to.target or "/backups")
    if t:find("^/?disk") then return "дискета " .. t end
    if t:find("^/?usb") then return "флешка " .. t end
    return "диск сервера " .. t
  end

  function app.draw(w, c)
    if K.waiting(c, st) then return end
    local list = st.data.list
    st.sel = math.max(1, math.min(st.sel, #list))
    local S = require("swired")
    c.fill(1, 1, LEFT, c.h, T.side)
    if #list == 0 then
      c.text(3, 2, "Копий пока нет.", T.fg, T.side)
      c.text(3, 3, G.cut("Сайты, почта, настройки —", LEFT - 4), T.dim, T.side)
      c.text(3, 4, G.cut("лучше копировать каждый день.", LEFT - 4), T.dim, T.side)
    end
    for i, job in ipairs(list) do
      local y = 1 + (i - 1) * 4
      if y > c.h - 4 then break end
      local sel = i == st.sel
      local bg = sel and T.sel or T.side
      c.fill(1, y, LEFT, 3, bg)
      if sel then c.text(1, y, "▌", T.accent, bg) c.text(1, y + 1, "▌", T.accent, bg) c.text(1, y + 2, "▌", T.accent, bg) end
      c.text(3, y, G.cut(job.name, LEFT - 4), T.fg, bg)
      c.text(3, y + 1, G.cut(table.concat(job.dirs or {}, ", ") .. " → " .. where(job), LEFT - 4), T.dim, bg)
      local last = job.last
      if last then
        c.text(3, y + 2, G.cut((last.ok and "✔ " or "✕ ") .. S.fmtTime(last.time) .. " · " .. (last.ok and K.size(last.size) or tostring(last.err)), LEFT - 4),
          last.ok and T.ok or T.err, bg)
      else
        c.text(3, y + 2, "ещё не делалась", T.warn, bg)
      end
      c.hit(1, y, LEFT, 3, "pick", i)
    end
    c.button(2, c.h, "+ Новая копия", "new", "primary")
    local job = list[st.sel]
    local x, cw = LEFT + 3, c.w - LEFT - 3
    if not job then return end
    c.text(x, 2, G.cut(job.name, cw - 20), T.fg)
    c.button(c.w - 17, 2, "Сделать сейчас", "run", "primary")
    K.card(c, x, 4, cw, 7, nil)
    local rows = {
      { "Что", table.concat(job.dirs or {}, "  "), "dirs" },
      { "Куда", where(job), "where" },
      { "Когда", (EVERY[job.every] or job.every) .. (job.every ~= "manual" and job.every ~= "hour" and (" в " .. tostring(job.at)) or "") ..
        (job.every ~= "manual" and "  (настоящее время)" or ""), "when" },
      { "Хранить", tostring(job.keep or 7) .. " последних копий", "keep" },
      { "Пароль", job.hasPassword and "зашифрована: без пароля не открыть" or "без пароля", "pw" },
    }
    for i, r in ipairs(rows) do
      local y = 4 + i
      c.text(x + 1, y, r[1], T.dim, T.side)
      c.text(x + 11, y, G.cut(r[2], cw - 22), T.fg, T.side)
      c.text(x + cw - 10, y, "изменить", T.link, T.side)
      c.hit(x, y, cw, 1, "edit", r[3])
    end
    local nr = job.every ~= "manual" and S.nextRun(job) or nil
    c.text(x, 12, G.cut(nr and ("Следующая — " .. S.fmtTime(nr)) or "Только по кнопке «Сделать сейчас»", cw), T.dim)
    c.text(x, 14, "Сделанные копии", T.faint)
    c.hline(x, 15, cw)
    local copies = st.data.copies or {}
    if #copies == 0 then c.text(x, 16, "Пока нет ни одной.", T.dim) end
    for i, cp in ipairs(copies) do
      local y = 15 + i
      if y > c.h - 3 then break end
      local nice = cp.name:gsub("%.swb$", ""):gsub("^(%d+)-(%d+)-(%d+)_(%d%d)(%d%d)(%d%d)$", "%3.%2.%1 %4:%5")
      c.text(x, y, G.cut(nice, 18), T.fg)
      c.text(x + 20, y, K.size(cp.size), T.dim)
      c.text(x + cw - 14, y, "Восстановить…", T.link)
      c.hit(x + cw - 14, y, 13, 1, "restore", cp.name)
    end
    c.text(x, c.h - 1, G.cut("«Восстановить» возвращает файлы копии; текущие откладываются в /restore-old.", cw), T.faint)
    local bx = c.button(x, c.h, "Удалить задание", "del")
    if st.msg then c.text(bx + 2, c.h, G.cut(st.msg, c.w - bx - 3), st.msg:find("^Не") and T.err or T.ok) end
    if st.err then c.text(x, c.h - 2, G.cut("Связь: " .. st.err, cw), T.err) end
  end

  function app.event(w, ev, a, b)
    if ev ~= "click" then return end
    local list = st.data and st.data.list or {}
    local job = list[st.sel]
    if a == "pick" then st.sel = b st.data.copies = {} reload()
    elseif a == "new" then
      wm.prompt("Новая копия", "Название (например, «Сайты и почта»):", "", function(nm)
        if not nm or nm == "" then return end
        local dirs = {}
        for _, d in ipairs({ "/www", "/sites", "/hosting", "/mail", "/etc", "/home" }) do
          if B.remote or fs.isDir(d) then dirs[#dirs + 1] = d end
          if B.remote and #dirs >= 3 then break end
        end
        if #dirs == 0 then dirs = { "/home" } end
        save({ name = nm, dirs = dirs, to = { kind = "path", target = "/backups" }, every = "day", at = "06:00", keep = 7 },
          "Создано: каждый день в 06:00 на диск сервера")
        st.sel = #list + 1
      end)
    elseif not job then return
    elseif a == "run" then act(function() return B.runBackup(job.id) end, "Копия «" .. job.name .. "» сделана")
    elseif a == "del" then
      wm.confirm("Удалить задание", "Удалить задание «" .. job.name .. "»? Уже сделанные копии останутся на месте.", function(ok)
        if ok then act(function() return B.removeBackup(job.id) end, "Удалено") end
      end, "Удалить")
    elseif a == "restore" then
      wm.confirm("Восстановить", "Вернуть файлы из копии " .. b .. "? Текущие отложатся в /restore-old.", function(ok)
        if ok then act(function() return B.restore(job.id, b) end, "Восстановлено") end
      end, "Восстановить")
    elseif a == "edit" then
      local j = copyOf(job)
      if b == "dirs" then
        wm.prompt("Что копировать", "Папки через пробел:", table.concat(j.dirs or {}, " "), function(v)
          if not v then return end
          local d = {}
          for p in v:gmatch("%S+") do d[#d + 1] = "/" .. fs.combine(p, "") end
          if #d == 0 then return end
          j.dirs = d
          save(j)
        end)
      elseif b == "where" then
        local cur = j.to and j.to.kind == "server" and 4 or (tostring(j.to and j.to.target):find("^/?usb") and 3) or
          (tostring(j.to and j.to.target):find("^/?disk") and 2) or 1
        local nxt = cur % 4 + 1
        if nxt == 1 then j.to = { kind = "path", target = "/backups" } save(j, "Куда: диск сервера /backups")
        elseif nxt == 2 then j.to = { kind = "path", target = "/disk" } save(j, "Куда: дискета (вставьте её в дисковод)")
        elseif nxt == 3 then j.to = { kind = "path", target = "/usb" } save(j, "Куда: флешка (вставьте её в USB)")
        else
          wm.prompt("Копия на другой сервер", "Сервер-хранилище с SWired (имя или адрес):", "", function(addr)
            if not addr or addr == "" then return end
            wm.prompt("Копия на другой сервер", "Администратор на " .. addr .. ":", "admin", function(u)
              if not u or u == "" then return end
              wm.prompt("Копия на другой сервер", "Его пароль:", "", function(p)
                if not p then return end
                j.to = { kind = "server", target = addr, user = u, pass = p }
                save(j, "Куда: сервер " .. addr)
              end, "•")
            end)
          end)
        end
      elseif b == "when" then
        local i = 1
        for k, v in ipairs(ORDER) do if v == j.every then i = k end end
        j.every = ORDER[i % #ORDER + 1]
        if j.every == "day" or j.every == "week" then
          wm.prompt("Когда", "Во сколько (ЧЧ:ММ, настоящее время):", j.at or "06:00", function(v)
            if v and v:match("^%d%d?:%d%d$") then j.at = v end
            save(j, "Когда: " .. EVERY[j.every] .. " в " .. tostring(j.at))
          end)
        else
          save(j, "Когда: " .. EVERY[j.every])
        end
      elseif b == "keep" then
        wm.prompt("Хранить", "Сколько последних копий хранить (1–30):", tostring(j.keep or 7), function(v)
          if tonumber(v) then j.keep = tonumber(v) save(j) end
        end)
      elseif b == "pw" then
        wm.prompt("Пароль копии", "Пароль (пусто — без пароля). Забудете — копию не открыть!", "", function(v)
          if v == nil then return end
          j.password = v
          save(j, v ~= "" and "Новые копии — под паролем" or "Новые копии — без пароля")
        end, "•")
      end
    end
    wm.redraw(w)
  end

  return app
end
